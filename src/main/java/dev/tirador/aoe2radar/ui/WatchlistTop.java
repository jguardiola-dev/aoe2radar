package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.TopLadderService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;

/**
 * El top de la Watchlist (★ Top ladder, ★ Top país, ★ Top clan) sin Swing: su estado (la lista cargada, su firma,
 * puestos, última partida, hora de carga, los «cargando/vigilando»), qué pedir, cómo aplicar lo que llega y la
 * caché de respaldo en disco. Es una pieza de {@link WatchlistPresenter} (el presentador la crea y ella usa su
 * Pantalla, sus Tareas y su modo de vista), separada solo por tamaño. Sale de WatchlistTrabajos y de la fachada tal
 * cual: mismos textos, mismo orden. Los SwingWorker de la carga y de la vigilancia siguen en WatchlistTrabajos como
 * transporte (su hilo, su get() y su done()); aquí van las decisiones que los rodean.
 * <p>Hilos: todo en el EDT salvo {@link #cargar} y {@link #vigilar} (el doInBackground de esos workers) y el hilo
 * "top-clan". topLadder no cambia de objeto nunca: topLadderSnapshot sincroniza sobre él (el hilo del socket).
 */
final class WatchlistTop {

    /** Lo que se va a pedir al leaderboard: país (null = mundial), firma de la vista, cuántos y el nombre del país. */
    record Carga(String pais, String firma, int topN, String nombrePais) { }

    private final WatchlistPresenter p;
    final TopLadderService servicio;
    private final Path topCache;

    final List<Player> topLadder = new ArrayList<>();
    String topFirma = "";
    final Map<Long, Integer> rankTop = new HashMap<>();
    final Map<Long, Long> lastTop = new HashMap<>();   // pid -> última partida (ms), del leaderboard
    long topCargado;
    volatile boolean cargandoTop, vigilandoTop;
    /** El «río» de Top ladder usa este throttle propio (independiente del de Twitch, ver ui.DirectosPresenter). */
    long ultimoTopMs;
    boolean avisoTopMostrado;   // el popup del top caído: solo la primera vez por sesión
    final Set<Long> topVerificados = java.util.concurrent.ConcurrentHashMap.newKeySet();

    WatchlistTop(WatchlistPresenter p, TopLadderService servicio, Path topCache) {
        this.p = p;
        this.servicio = servicio;
        this.topCache = topCache;
    }

    /** Copia del top actual (ladder/país/clan): la consulta sincronizarSocket (cromo) para vigilar también el top. */
    List<Player> snapshot() { synchronized (topLadder) { return new ArrayList<>(topLadder); } }

    // ----- «★ Top clan»: una vista más de la watchlist -----

    void cargarTopClan() {
        String tag = p.clanBuscado();
        if (tag.isEmpty()) { p.pantalla.estado(t("Escribe el tag del clan (p. ej. R1).", "Type the clan tag (e.g. R1).")); return; }
        p.pantalla.estado(p.anfitrion.clanesVacios() ? t("Descargando la lista de clanes…", "Downloading the clan list…") : t("Cargando el clan…", "Loading the clan…"));
        p.tareas.enFondo("top-clan", () -> {   // con Tareas.SWING: new Thread(..., "top-clan").start(), como antes
            TopLadderService.ResultadoClan res = servicio.topClan(tag);
            p.tareas.enUi(() -> aplicarTopClan(tag, res));
        });
    }

    /** El resultado de cargarTopClan, ya en el EDT. F1 de la revisión 1.3: el clan comparte topLadder/rankTop con
     *  ★ Top ladder y ★ Top país, así que 1) deja su propia firma (antes la de «global» seguía puesta y, al volver al
     *  ladder antes de 10 min, topFresco daba por buena la lista del clan) y 2) no pinta si el usuario ya salió de
     *  ★ Top clan mientras cargaba (como cargarTopLadder, que ya lo miraba). */
    void aplicarTopClan(String tag, TopLadderService.ResultadoClan res) {
        if (res.error() != null) { p.pantalla.estado(t("No se pudo cargar la lista de clanes: ", "Couldn't load the clan list: ") + res.error()); return; }
        if (!p.modoClan()) return;   // el usuario cambió de vista mientras cargaba: no pintar encima
        topFirma = WatchlistPresenter.firmaClan(tag);
        topLadder.clear();
        ultimoTopMs = 0; p.anfitrion.reiniciarThrottleDirectos();   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
        lastTop.clear(); rankTop.clear();
        for (TopLadderService.FilaClan f : res.miembros()) {
            topLadder.add(new Player(f.pid(), f.nombre(), p.topClan));
            p.ponerEloDeAnoche(f.pid(), f.rating());   // resumen diario de sfr-data: ELO de anoche (F5)
            rankTop.put(f.pid(), topLadder.size());
        }
        guardarConfig("clan_tag", tag);
        p.pantalla.refrescarFiltro();
        p.pantalla.indicadoresVivos();
        p.pantalla.estado(res.miembros().isEmpty() ? t("Ningún clan del ladder 1v1 se llama «", "No 1v1 ladder clan is called \u201C") + tag + t("» (elige uno de las sugerencias).", "\u201D (pick one from the suggestions).")
                : t("Clan ", "Clan ") + tag + ": " + res.miembros().size() + t(" jugadores en el ladder 1v1 (resumen diario).", " players on the 1v1 ladder (daily summary)."));
    }

    // ----- ★ Top ladder / ★ Top país: el top N del leaderboard (sin tocar players.txt), con caché de 10 min -----

    /** Antes de cargar el top: si ya hay una carga en marcha, nada (null); si el que hay está fresco (misma vista,
     *  menos de 10 min), lo pinta, intenta vigilar y nada más (null); si no, marca «cargando», dice qué se carga y
     *  devuelve lo que hay que pedir (lo pide el SwingWorker de la vista con {@link #cargar}). */
    Carga prepararCarga(boolean forzar) {
        if (cargandoTop) return null;
        String pais = p.modoPais() ? p.paisSel() : null;
        String firma = pais == null ? "global" : pais;
        if (servicio.topFresco(forzar, firma, topFirma, !topLadder.isEmpty(), topCargado)) {
            p.pantalla.refrescarFiltro();
            p.pantalla.indicadoresVivos();
            p.pantalla.vigilarTop();
            return null;
        }
        cargandoTop = true;
        int topN = Integer.parseInt(leerConfig("top_n", "50"));
        String nombrePais = null;
        if (pais != null) for (PaisItem pi : p.paises) if (pi.code().equals(pais)) { nombrePais = pi.nombre(); break; }
        p.pantalla.estado(t("Cargando el top ", "Loading the top ") + topN
                + (nombrePais != null ? t(" de ", " of ") + nombrePais : t(" del ladder…", " of the ladder…")));
        return new Carga(pais, firma, topN, nombrePais);
    }

    /** En el hilo de fondo del SwingWorker: siempre de ahora, sin la caché por URL (B1). */
    TopLadderService.ResultadoTop cargar(Carga c) { return servicio.cargarTop(c.pais(), c.topN()); }

    /** Lo primero del done() del SwingWorker, pase lo que pase. */
    void cargaTerminada() { cargandoTop = false; }

    /** El resultado de la carga, ya en el EDT (dentro del try del done(): un fallo aquí acaba en errorCarga). */
    void aplicarCarga(TopLadderService.ResultadoTop res, Carga c) {
        WatchlistPresenter.TOP_STREAK.putAll(res.racha());
        WatchlistPresenter.TOP_LAST10.putAll(res.ultimas10());
        p.gamesWatch.putAll(res.partidas());   // como en la 1.1: se aprenden siempre, aunque el usuario haya cambiado de vista
        if (p.modoClan() || !p.modoTop()) return;   // mientras cargaba, el usuario cambió de vista: no pintar encima
        String firma = c.firma();
        if (res.filas().isEmpty()) {
            if (cargarTopCache(firma)) {
                topFirma = firma;
                p.pantalla.refrescarFiltro();
                p.pantalla.indicadoresVivos();
                long horasCache = Math.max(1, (System.currentTimeMillis() - topCargado) / 3600_000L);
                p.pantalla.estado(t("El servicio de datos no responde (¿bloqueo de red? p. ej. LaLiga/Cloudflare). Mostrando el top de hace ~",
                        "The data service isn't responding (network block? e.g. LaLiga/Cloudflare). Showing the top from ~")
                        + horasCache + t(" h. Reintento automático cada ", " h ago. Auto-retrying every ") + minutosReintento() + " min.");
            } else {
                if (!avisoTopMostrado) {
                    avisoTopMostrado = true;
                    p.pantalla.avisarTopCaido(t("No se pudo cargar el top del ladder.\n\nCausa probable: el servicio de datos está caído o bloqueado\n(p. ej. LaLiga/Cloudflare en días de fútbol en España).\n\nLa app reintenta sola cada ",
                              "Couldn't load the ladder top.\n\nLikely cause: the data service is down or blocked\n(e.g. LaLiga/Cloudflare on football days in Spain).\n\nThe app retries on its own every ")
                              + minutosReintento() + t(" min — no hace falta hacer nada.", " min — nothing to do."));
                }
                p.pantalla.estado(t("No se pudo cargar el top (¿servicio caído o bloqueado? p. ej. LaLiga/Cloudflare en días de fútbol). Reintento automático cada ",
                        "Couldn't load the top (service down or blocked? e.g. LaLiga/Cloudflare on match days). Auto-retrying every ") + minutosReintento() + " min.");
            }
            return;
        }
        topLadder.clear();
        ultimoTopMs = 0; p.anfitrion.reiniciarThrottleDirectos();   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
        lastTop.clear();
        rankTop.clear();
        for (TopLadderService.FilaTop f : res.filas()) {
            topLadder.add(new Player(f.pid(), f.nombre(), WatchlistView.TOP_LADDER));
            p.ponerEloFresco(f.pid(), f.rating());   // del leaderboard: fresco (F5)
            lastTop.put(f.pid(), f.ultimaPartidaMs());
            rankTop.put(f.pid(), topLadder.size());
        }
        topCargado = System.currentTimeMillis();
        topFirma = firma;
        guardarTopCache(firma);
        p.pantalla.refrescarFiltro();
        p.pantalla.indicadoresVivos();
        p.pantalla.estado(t("Top ", "Top ") + topLadder.size()
                + (c.nombrePais() != null ? t(" de ", " of ") + c.nombrePais() : t(" del ladder", " of the ladder"))
                + t(" cargado. Los puntos rojos llegan en segundos…",
                    " loaded. Red dots arriving in seconds…"));
        p.pantalla.vigilarTop();
    }

    /** El catch del done(): la carga (o su aplicación) falló. */
    void errorCarga(Exception ex) {
        p.pantalla.estado(t("Error cargando el top: ", "Error loading the top: ") + causa(ex));
    }

    /** Cada cuánto reintenta el vigilante un top caído: en cada ronda de vigilancia (Configuración → Vigilancia de
     *  vivos, 1 min por defecto), no «cada 2 min» como decían los textos de la 1.1 (F12 de la revisión 1.3). */
    private static int minutosReintento() { return dev.tirador.aoe2radar.service.EnlaceVivo.tickMs() / 60_000; }

    private void guardarTopCache(String firma) {
        List<TopLadderService.FilaCache> filas = new ArrayList<>();
        for (Player x : topLadder)
            filas.add(new TopLadderService.FilaCache(x.id(), x.name(), p.eloWatch.getOrDefault(x.id(), 0), lastTop.getOrDefault(x.id(), 0L)));
        servicio.guardarCache(topCache, firma, topCargado, filas);
    }

    /** Restaura el último top guardado si es de la misma vista. Devuelve éxito. */
    private boolean cargarTopCache(String firma) {
        TopLadderService.TopCache cache = servicio.cargarCache(topCache, firma);
        if (cache == null) return false;
        topLadder.clear();
        ultimoTopMs = 0; p.anfitrion.reiniciarThrottleDirectos();   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
        lastTop.clear();
        rankTop.clear();
        for (TopLadderService.FilaCache f : cache.filas()) {
            topLadder.add(new Player(f.pid(), f.nombre(), WatchlistView.TOP_LADDER));
            if (f.elo() > 0) p.ponerEloFresco(f.pid(), f.elo());   // del leaderboard, aunque de caché (F5)
            lastTop.put(f.pid(), f.ultimaPartidaMs());
            rankTop.put(f.pid(), topLadder.size());
        }
        topCargado = cache.cargadoMs();
        return !topLadder.isEmpty();
    }

    // ----- Vivos del top: el río global de partidas en curso como motor (1-2 llamadas), consulta a los «calientes»
    // si el río no trae en-curso, y verificación individual presupuestada de los que se apagan -----

    /** Antes de vigilar: nada (null) si ya se vigila, si no hay top o si no han pasado 45 s (anti-solape: un
     *  barrido por tick); si toca, marca «vigilando» y devuelve la copia del top que se va a vigilar. */
    List<Player> prepararVigilancia() {
        if (vigilandoTop || topLadder.isEmpty()) return null;
        if (System.currentTimeMillis() - ultimoTopMs < 45_000) return null;   // anti-solape: un barrido por tick
        ultimoTopMs = System.currentTimeMillis();
        vigilandoTop = true;
        return new ArrayList<>(topLadder);
    }

    /** En el hilo de fondo del SwingWorker de vigilarTop. */
    TopLadderService.ResultadoVigilancia vigilar(List<Player> top) {
        return servicio.vigilarTop(top, p.vivo::jugando, p.vivo::matchDe,
                (pid, m) -> {   // el lote: alguien aparece en curso. avisarSiCampana se llama desde este hilo de
                    // fondo, pero ya es segura (fila 106 de DEUDA): construye nombre/texto dentro de un invokeLater.
                    p.vivo.ponerInfo(pid, p.pantalla.enlace().resumenVivo(m, pid));
                    if (!p.vivo.jugando(pid)) p.avisarSiCampana(pid, m);   // nuevo en partida desde el último barrido
                    p.vivo.guardarPartida(pid, m);
                },
                (pid, m) -> p.vivo.ponerInfo(pid, p.pantalla.enlace().resumenVivo(m, pid)));   // la confirmación individual
    }

    /** Lo primero del done() de la vigilancia, pase lo que pase. */
    void vigilanciaTerminada() {
        vigilandoTop = false;
        p.anfitrion.vigilarTwitchDirectos();   // el río acaba de enseñar canales: ahora sí, el cruce
    }

    /** El resultado de la vigilancia, ya en el EDT (dentro del try del done(): un fallo aquí se ignora, como antes). */
    void aplicarVigilancia(List<Player> top, TopLadderService.ResultadoVigilancia r) {
        topVerificados.clear();
        topVerificados.addAll(r.verificados());
        Map<Long, Long> vivos = r.resultado();
        for (Player x : top) {
            if (!topVerificados.contains(x.id())) continue;   // lote fallido: ni quitar ni poner
            Long v = vivos.get(x.id());
            if (v != null) p.vivo.marcarJugando(x.id(), v);
            else { p.vivo.marcarFuera(x.id()); }   // el REST manda al quitar
        }
        // fila 109 de DEUDA: aquí había un "terminadasRio" que nunca se rellenaba (nada de este
        // método añadía partidas a esa lista); el bloque que lo consumía no actuaba nunca, así que
        // el resultado real siempre era el repintado simple. Se borra el muerto, no el comportamiento.
        p.pantalla.enlace().repintarTabla();
        p.pantalla.indicadoresVivos();
    }
}
