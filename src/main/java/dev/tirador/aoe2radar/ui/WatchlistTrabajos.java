package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.service.TopLadderService;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Los trabajos en segundo plano de la Watchlist: forma reciente, top clan, top ladder/país (con su caché
 * de respaldo) y los barridos de vivos (refrescarWatchlist, vigilarVivos, vigilarTop). Sale de WatchlistView
 * tal cual en la 1.3: cada SwingWorker y cada Thread con su nombre, su publish/process y su done(). El
 * estado (topLadder, rankTop, topCargado, cargandoTop...) sigue en la fachada: aquí solo vive el código.
 * Hilos: doInBackground y "top-clan" hacen la red; process/done/invokeLater vuelven al EDT.
 */
final class WatchlistTrabajos {

    private final WatchlistView wv;

    WatchlistTrabajos(WatchlistView wv) { this.wv = wv; }

    /** Cambiar de vista apaga la columna Forma (vuelve solo si la pides). */
    public void apagarForma() {
        if (!wv.formaVisible) return;
        wv.formaVisible = false;
        if (wv.ocultarFormaBtn != null) wv.ocultarFormaBtn.setVisible(false);
        if (wv.formaBtn != null) actualizarTextoForma();
        if (leerConfig("orden_watch", "elo").startsWith("forma")) guardarConfig("orden_watch", "elo");
        wv.refrescarCabeceraOrden();
    }

    /** Consulta la forma de los jugadores dados (con caché de 10 min), con progreso y Detener. */
    void cargarForma(List<Player> objetivo, int horas, Runnable alTerminar) {
        Map<Long, Forma> cache = horas <= 24 ? wv.forma24 : wv.forma7d;
        Map<Long, Long> ts = horas <= 24 ? wv.formaTs24 : wv.formaTs7d;
        List<Player> pendientes = new ArrayList<>();
        long ahora = dev.tirador.aoe2radar.util.Reloj.SISTEMA.ahoraMs();   // una sola lectura del reloj para todo el lote, como la 1.1
        for (Player p : objetivo) if (wv.formaService.pendiente(ts.getOrDefault(p.id(), 0L), ahora)) pendientes.add(p);
        if (pendientes.isEmpty()) { if (alTerminar != null) alTerminar.run(); return; }
        if (wv.modoTop() && pendientes.size() > 20) {
            int seg = (int) Math.ceil(pendientes.size() * 0.6);
            int ok = JOptionPane.showConfirmDialog(wv.ventana,
                    (horas <= 24 ? t("Consultar la forma de las últimas 24 h de ", "Fetching the last 24 h form of ")
                                 : t("Consultar la forma de los últimos 7 días de ", "Fetching the last 7 days form of "))
                            + pendientes.size() + t(" jugadores tarda ~", " players takes ~") + seg + " s.\n"
                            + t("Se consulta jugador a jugador (con pausas) y queda guardado 10 minutos.", "It goes player by player (with pauses) and is cached for 10 minutes."),
                    t("Forma reciente", "Recent form"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
        }
        wv.anfitrion.trabajando(true);
        final long miSerial = wv.anfitrion.opSerial();
        new SwingWorker<Void, String>() {
            @Override protected Void doInBackground() {
                wv.anfitrion.marcarHiloOperacionActual();   // Detener corta la espera del freno de ESTA operación, no la de todos
                wv.anfitrion.cargarEloAyer();
                List<Player> porApi = new ArrayList<>();
                long ahoraTs = System.currentTimeMillis();
                for (Player p : pendientes) {   // 1) resta con el snapshot nocturno: sin llamadas
                    Forma[] f = wv.formaService.porResta(p.id(), pid -> WatchlistView.eloParaResta(pid, wv.eloWatch, wv.eloDelSnapshot), wv.gamesWatch::get);
                    if (f == null) { porApi.add(p); continue; }
                    wv.forma24.put(p.id(), f[0]); wv.formaTs24.put(p.id(), ahoraTs);
                    if (f[1] != null) { wv.forma7d.put(p.id(), f[1]); wv.formaTs7d.put(p.id(), ahoraTs); } else if (horas > 24) porApi.add(p);
                }
                publish(t("Forma: ", "Recent form: ") + (pendientes.size() - porApi.size()) + t(" del snapshot nocturno", " from the nightly snapshot") + (porApi.isEmpty() ? "" : " · " + porApi.size() + t(" consultas", " requests")));
                for (Player p : porApi) {   // 2) quien no está en el snapshot: su serie de rating, exacta (una llamada por jugador)
                    if (wv.anfitrion.detenerOperacion()) break;
                    try {
                        Forma[] ambas = wv.formaService.porSerie(p.id());
                        if (ambas != null) { wv.forma24.put(p.id(), ambas[0]); wv.formaTs24.put(p.id(), ahoraTs); wv.forma7d.put(p.id(), ambas[1]); wv.formaTs7d.put(p.id(), ahoraTs); }
                    } catch (Exception ex) { log("forma " + p.name() + ": " + causa(ex)); }
                }
                return null;
            }
            @Override protected void process(List<String> ch) { wv.status.setText(ch.get(ch.size() - 1)); }
            @Override protected void done() {
                if (miSerial != wv.anfitrion.opSerial()) return;
                wv.anfitrion.trabajando(false);
                wv.status.setText(t("Forma consultada.", "Recent form fetched."));
                if (alTerminar != null) alTerminar.run();
            }
        }.execute();
    }

    /** Objetivo de «Ver forma»: los seleccionados si los hay; si no, todos los visibles. */
    List<Player> objetivoForma() {
        List<Player> sel = wv.playersList.getSelectedValuesList();
        return sel.isEmpty() ? jugadoresVisibles() : new ArrayList<>(sel);
    }

    /** El botón dice lo que va a consultar: «Ver forma (todos · 50)» / «(3 seleccionados)» / «Ocultar forma». */
    public void actualizarTextoForma() {
        if (wv.formaBtn == null) return;
        int n = wv.playersList.getSelectedIndices().length;
        String quien = n > 0 ? n + t(" seleccionado" + (n > 1 ? "s" : ""), " selected")
                             : t("todos \u00B7 ", "all \u00B7 ") + wv.playersModel.size();
        wv.formaBtn.setText(t("Ver forma", "Recent form") + " (" + quien + ")");
    }

    private List<Player> jugadoresVisibles() {
        List<Player> v = new ArrayList<>();
        for (int i = 0; i < wv.playersModel.size(); i++) v.add(wv.playersModel.get(i));
        return v;
    }

    // ----- «★ Top clan»: una vista más de la watchlist -----
    void cargarTopClan() {
        String tag = wv.clanField == null ? "" : wv.clanField.getText().trim();
        if (tag.isEmpty()) { wv.status.setText(t("Escribe el tag del clan (p. ej. R1).", "Type the clan tag (e.g. R1).")); return; }
        wv.status.setText(wv.anfitrion.clanesVacios() ? t("Descargando la lista de clanes…", "Downloading the clan list…") : t("Cargando el clan…", "Loading the clan…"));
        new Thread(() -> {
            TopLadderService.ResultadoClan res = wv.topLadderService.topClan(tag);
            SwingUtilities.invokeLater(() -> aplicarTopClan(tag, res));
        }, "top-clan").start();
    }

    /** El resultado de cargarTopClan, ya en el EDT. F1 de la revisión 1.3: el clan comparte topLadder/rankTop con
     *  ★ Top ladder y ★ Top país, así que 1) deja su propia firma (antes la de «global» seguía puesta y, al volver al
     *  ladder antes de 10 min, topFresco daba por buena la lista del clan) y 2) no pinta si el usuario ya salió de
     *  ★ Top clan mientras cargaba (como cargarTopLadder, que ya lo miraba). */
    void aplicarTopClan(String tag, TopLadderService.ResultadoClan res) {
        if (res.error() != null) { wv.status.setText(t("No se pudo cargar la lista de clanes: ", "Couldn't load the clan list: ") + res.error()); return; }
        if (!wv.modoClan()) return;   // el usuario cambió de vista mientras cargaba: no pintar encima
        wv.topFirma = WatchlistView.firmaClan(tag);
        wv.topLadder.clear();
        wv.ultimoTopMs = 0; wv.anfitrion.reiniciarThrottleDirectos();   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
        wv.lastTop.clear(); wv.rankTop.clear();
        for (TopLadderService.FilaClan f : res.miembros()) {
            wv.topLadder.add(new Player(f.pid(), f.nombre(), wv.TOP_CLAN));
            wv.eloWatch.put(f.pid(), f.rating());
            wv.eloDelSnapshot.add(f.pid());   // resumen diario de sfr-data: ELO de anoche (F5)
            wv.rankTop.put(f.pid(), wv.topLadder.size());
        }
        guardarConfig("clan_tag", tag);
        wv.aplicarFiltroGrupo();
        wv.actualizarIndicadoresVivos();
        wv.status.setText(res.miembros().isEmpty() ? t("Ningún clan del ladder 1v1 se llama «", "No 1v1 ladder clan is called \u201C") + tag + t("» (elige uno de las sugerencias).", "\u201D (pick one from the suggestions).")
                : t("Clan ", "Clan ") + tag + ": " + res.miembros().size() + t(" jugadores en el ladder 1v1 (resumen diario).", " players on the 1v1 ladder (daily summary)."));
    }

    /** Barrido de la Watchlist al abrir: para cada seguido, una consulta ligera
     *  que detecta partida en curso (finished vacío) y su ELO actual (rating de
     *  su último 1v1 terminado). Solo toca la lista, nunca la tabla: los
     *  resultados de las partidas siguen sin verse. */
    /** Carga el top N del leaderboard (nick, ELO, última partida) sin tocar
     *  players.txt, con caché de 10 min, y dispara un barrido de vivos. */
    public void cargarTopLadder(boolean forzar) {
        if (wv.cargandoTop) return;
        String pais = wv.modoPais() ? wv.paisSel() : null;
        String firma = pais == null ? "global" : pais;
        if (wv.topLadderService.topFresco(forzar, firma, wv.topFirma, !wv.topLadder.isEmpty(), wv.topCargado)) {
            wv.aplicarFiltroGrupo();
            wv.actualizarIndicadoresVivos();
            vigilarTop();
            return;
        }
        wv.cargandoTop = true;
        int topN = Integer.parseInt(leerConfig("top_n", "50"));
        String nombrePais = null;
        if (pais != null) for (PaisItem pi : wv.PAISES) if (pi.code().equals(pais)) { nombrePais = pi.nombre(); break; }
        final String nombrePaisF = nombrePais;
        wv.status.setText(t("Cargando el top ", "Loading the top ") + topN
                + (nombrePais != null ? t(" de ", " of ") + nombrePais : t(" del ladder…", " of the ladder…")));
        new SwingWorker<TopLadderService.ResultadoTop, Void>() {
            @Override protected TopLadderService.ResultadoTop doInBackground() {
                return wv.topLadderService.cargarTop(pais, topN);
            }
            @Override protected void done() {
                wv.cargandoTop = false;
                try {
                    TopLadderService.ResultadoTop res = get();
                    WatchlistView.TOP_STREAK.putAll(res.racha());
                    WatchlistView.TOP_LAST10.putAll(res.ultimas10());
                    wv.gamesWatch.putAll(res.partidas());   // como en la 1.1: se aprenden siempre, aunque el usuario haya cambiado de vista
                    if (wv.modoClan() || !wv.modoTop()) return;   // mientras cargaba, el usuario cambió de vista: no pintar encima
                    if (res.filas().isEmpty()) {
                        if (cargarTopCache(firma)) {
                            wv.topFirma = firma;
                            wv.aplicarFiltroGrupo();
                            wv.actualizarIndicadoresVivos();
                            long horasCache = Math.max(1, (System.currentTimeMillis() - wv.topCargado) / 3600_000L);
                            wv.status.setText(t("El servicio de datos no responde (¿bloqueo de red? p. ej. LaLiga/Cloudflare). Mostrando el top de hace ~",
                                    "The data service isn't responding (network block? e.g. LaLiga/Cloudflare). Showing the top from ~")
                                    + horasCache + t(" h. Reintento automático cada 2 min.", " h ago. Auto-retrying every 2 min."));
                        } else {
                            if (!wv.avisoTopMostrado) {
                                wv.avisoTopMostrado = true;
                                JOptionPane.showMessageDialog(wv.ventana,
                                        t("No se pudo cargar el top del ladder.\n\nCausa probable: el servicio de datos está caído o bloqueado\n(p. ej. LaLiga/Cloudflare en días de fútbol en España).\n\nLa app reintenta sola cada 2 minutos — no hace falta hacer nada.",
                                          "Couldn't load the ladder top.\n\nLikely cause: the data service is down or blocked\n(e.g. LaLiga/Cloudflare on football days in Spain).\n\nThe app retries every 2 minutes on its own — nothing to do."),
                                        t("Servicio no disponible", "Service unavailable"),
                                        JOptionPane.WARNING_MESSAGE);
                            }
                            wv.status.setText(t("No se pudo cargar el top (¿servicio caído o bloqueado? p. ej. LaLiga/Cloudflare en días de fútbol). Reintento automático cada 2 min.",
                                    "Couldn't load the top (service down or blocked? e.g. LaLiga/Cloudflare on match days). Auto-retrying every 2 min."));
                        }
                        return;
                    }
                    wv.topLadder.clear();
                    wv.ultimoTopMs = 0; wv.anfitrion.reiniciarThrottleDirectos();   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
                    wv.lastTop.clear();
                    wv.rankTop.clear();
                    for (TopLadderService.FilaTop f : res.filas()) {
                        wv.topLadder.add(new Player(f.pid(), f.nombre(), WatchlistView.TOP_LADDER));
                        wv.eloWatch.put(f.pid(), f.rating());
                        wv.eloDelSnapshot.remove(f.pid());   // del leaderboard: fresco (F5)
                        wv.lastTop.put(f.pid(), f.ultimaPartidaMs());
                        wv.rankTop.put(f.pid(), wv.topLadder.size());
                    }
                    wv.topCargado = System.currentTimeMillis();
                    wv.topFirma = firma;
                    guardarTopCache(firma);
                    wv.aplicarFiltroGrupo();
                    wv.actualizarIndicadoresVivos();
                    wv.status.setText(t("Top ", "Top ") + wv.topLadder.size()
                            + (nombrePaisF != null ? t(" de ", " of ") + nombrePaisF : t(" del ladder", " of the ladder"))
                            + t(" cargado. Los puntos rojos llegan en segundos…",
                                " loaded. Red dots arriving in seconds…"));
                    vigilarTop();
                } catch (Exception ex) {
                    wv.status.setText(t("Error cargando el top: ", "Error loading the top: ") + causa(ex));
                }
            }
        }.execute();
    }

    private void guardarTopCache(String firma) {
        List<TopLadderService.FilaCache> filas = new ArrayList<>();
        for (Player p : wv.topLadder)
            filas.add(new TopLadderService.FilaCache(p.id(), p.name(), wv.eloWatch.getOrDefault(p.id(), 0), wv.lastTop.getOrDefault(p.id(), 0L)));
        wv.topLadderService.guardarCache(wv.topCache, firma, wv.topCargado, filas);
    }

    /** Restaura el último top guardado si es de la misma vista. Devuelve éxito. */
    private boolean cargarTopCache(String firma) {
        TopLadderService.TopCache cache = wv.topLadderService.cargarCache(wv.topCache, firma);
        if (cache == null) return false;
        wv.topLadder.clear();
        wv.ultimoTopMs = 0; wv.anfitrion.reiniciarThrottleDirectos();   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
        wv.lastTop.clear();
        wv.rankTop.clear();
        for (TopLadderService.FilaCache f : cache.filas()) {
            wv.topLadder.add(new Player(f.pid(), f.nombre(), WatchlistView.TOP_LADDER));
            if (f.elo() > 0) { wv.eloWatch.put(f.pid(), f.elo()); wv.eloDelSnapshot.remove(f.pid()); }   // del leaderboard, aunque de caché (F5)
            wv.lastTop.put(f.pid(), f.ultimaPartidaMs());
            wv.rankTop.put(f.pid(), wv.topLadder.size());
        }
        wv.topCargado = cache.cargadoMs();
        return !wv.topLadder.isEmpty();
    }

    /** Vivos del top: el río global de partidas en curso como motor, consulta a los «calientes» y verificación
     *  individual presupuestada de los que se apagan. */
    /** Vivos del top: el río global de partidas en curso como motor (1-2
     *  llamadas), consulta a los «calientes» si el río no trae en-curso, y
     *  verificación individual presupuestada de los que se apagan. */
    public void vigilarTop() {
        if (wv.vigilandoTop || wv.topLadder.isEmpty()) return;
        if (System.currentTimeMillis() - wv.ultimoTopMs < 45_000) return;   // anti-solape: un barrido por tick
        wv.ultimoTopMs = System.currentTimeMillis();
        wv.vigilandoTop = true;
        List<Player> top = new ArrayList<>(wv.topLadder);
        new SwingWorker<TopLadderService.ResultadoVigilancia, Void>() {
            @Override protected TopLadderService.ResultadoVigilancia doInBackground() {
                return wv.topLadderService.vigilarTop(top, WatchlistView.VIVO::jugando, WatchlistView.VIVO::matchDe,
                        (pid, m) -> {   // el lote: alguien aparece en curso. avisarSiCampana se llama desde este hilo de
                            // fondo, pero ya es segura (fila 106 de DEUDA): construye nombre/texto dentro de un invokeLater.
                            WatchlistView.VIVO.ponerInfo(pid, wv.enlacePartidas.resumenVivo(m, pid));
                            if (!WatchlistView.VIVO.jugando(pid)) wv.avisarSiCampana(pid, m);   // nuevo en partida desde el último barrido
                            WatchlistView.VIVO.guardarPartida(pid, m);
                        },
                        (pid, m) -> WatchlistView.VIVO.ponerInfo(pid, wv.enlacePartidas.resumenVivo(m, pid)));   // la confirmación individual
            }
            @Override protected void done() {
                wv.vigilandoTop = false;
                wv.anfitrion.vigilarTwitchDirectos();   // el río acaba de enseñar canales: ahora sí, el cruce
                try {
                    TopLadderService.ResultadoVigilancia r = get();
                    wv.topVerificados.clear();
                    wv.topVerificados.addAll(r.verificados());
                    Map<Long, Long> vivos = r.resultado();
                    for (Player p : top) {
                        if (!wv.topVerificados.contains(p.id())) continue;   // lote fallido: ni quitar ni poner
                        Long v = vivos.get(p.id());
                        if (v != null) WatchlistView.VIVO.marcarJugando(p.id(), v);
                        else { WatchlistView.VIVO.marcarFuera(p.id()); }   // el REST manda al quitar
                    }
                    // fila 109 de DEUDA: aquí había un "terminadasRio" que nunca se rellenaba (nada de este
                    // método añadía partidas a esa lista); el bloque que lo consumía no actuaba nunca, así que
                    // el resultado real siempre era el repintado simple. Se borra el muerto, no el comportamiento.
                    wv.enlacePartidas.repintarTabla();
                    wv.actualizarIndicadoresVivos();
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    public void refrescarWatchlist() {
        if (wv.modoTop()) return;   // el top se alimenta del leaderboard y del río
        List<Player> objetivo = new ArrayList<>();
        for (int i = 0; i < wv.playersModel.size(); i++) {
            Player p = wv.playersModel.get(i);
            if (wv.watchBarridos.add(p.id())) objetivo.add(p);
        }
        if (objetivo.isEmpty()) return;
        new SwingWorker<Void, BarridoVivos.Refresco>() {
            @Override protected Void doInBackground() {
                wv.anfitrion.cargarEloAyer();
                for (Player p : objetivo) {
                    try {
                        BarridoVivos.Refresco r = wv.barridoVivos.refrescar(p.id());
                        if (r.juegosNocturno() != null) {   // del snapshot nocturno: sin llamada (el socket dirá si está en partida)
                            wv.gamesWatch.put(p.id(), r.juegosNocturno());
                            publish(r);
                            continue;
                        }
                        publish(r);
                    } catch (Exception ex) {
                        log("watchlist: fallo con " + p.name() + ": " + causa(ex));
                    }
                    wv.anfitrion.dormir(wv.pausaMs / 2);
                }
                return null;
            }
            @Override protected void process(List<BarridoVivos.Refresco> chunks) {
                for (BarridoVivos.Refresco r : chunks) aplicarRefresco(r);
                wv.actualizarIndicadoresVivos();
            }
        }.execute();
    }

    /** Un refresco de refrescarWatchlist, ya en el EDT: vivo y ELO. Con el ELO del snapshot nocturno no se sabe si
     *  juega (sabeSiJuega() false): el estado en vivo no se toca, lo lleva el socket (F4 de la revisión 1.3). */
    void aplicarRefresco(BarridoVivos.Refresco r) {
        if (r.sabeSiJuega()) {
            if (r.vivo() != null) {
                if (WatchlistView.VIVO.marcarJugando(r.pid(), r.vivo()) && r.resumen() != null) WatchlistView.VIVO.ponerInfo(r.pid(), r.resumen());
            } else { WatchlistView.VIVO.marcarFuera(r.pid()); }
        }
        if (r.elo() != null) {
            wv.eloWatch.put(r.pid(), r.elo());
            if (r.sabeSiJuega()) wv.eloDelSnapshot.remove(r.pid()); else wv.eloDelSnapshot.add(r.pid());   // F5: ¿fresco o de anoche?
        }
    }

    /** Vigilancia periódica de TODA la watchlist: solo detecta quién está jugando ahora. Se salta el tick si
     *  hay otra tarea en marcha. */
    /** Vigilancia periódica de TODA la watchlist (todos los grupos): solo
     *  detecta quién está jugando ahora; el ELO no se toca (solo al abrir).
     *  Se salta el tick si hay otra tarea en marcha. */
    public void vigilarVivos() {
        if (wv.vigilando || wv.progreso.isVisible() || wv.todosJugadores.isEmpty()) return;
        wv.vigilando = true;
        List<Player> objetivo = new ArrayList<>(wv.todosJugadores);
        new SwingWorker<Void, BarridoVivos.Lote>() {
            @Override protected Void doInBackground() {
                final int LOTE = BarridoVivos.LOTE_VIVOS;   // 2 llamadas para un top 50, 4 para el top 100
                for (int d = 0; d < objetivo.size(); d += LOTE) {
                    List<Player> lote = objetivo.subList(d, Math.min(d + LOTE, objetivo.size()));
                    List<Long> idsLote = new ArrayList<>();
                    for (Player p : lote) idsLote.add(p.id());
                    try {
                        publish(wv.barridoVivos.lote(idsLote));
                    } catch (Exception ex) {
                        log("vigilante: fallo con el lote " + (d / LOTE + 1) + ": " + causa(ex));
                    }
                    wv.anfitrion.dormir(wv.pausaMs / 2);
                }
                return null;
            }
            @Override protected void process(List<BarridoVivos.Lote> chunks) {
                boolean tablaTocada = false;
                for (BarridoVivos.Lote c : chunks) {
                    for (Long id : c.idsLote()) {
                        Long vm = c.vivos().get(id);
                        if (vm != null) { WatchlistView.VIVO.marcarJugando(id, vm, c.infos().get(id)); }
                        else { WatchlistView.VIVO.marcarFuera(id); }
                    }
                    for (Match fresco : c.terminadas())
                        for (Match m : wv.all)
                            if (m.id == fresco.id && m.finished == null) {
                                m.finished = fresco.finished;   // la EN DIRECTO de la tabla acabó:
                                m.players = fresco.players;     // fecha real y resultado disponibles
                                tablaTocada = true;
                            }
                }
                if (tablaTocada) wv.enlacePartidas.applyFilters();
                else wv.enlacePartidas.repintarTabla();   // p. ej. una viva que cruza el umbral de fantasma
                wv.actualizarIndicadoresVivos();
            }
            @Override protected void done() { wv.vigilando = false; }
        }.execute();
    }
}
