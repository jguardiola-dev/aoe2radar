package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.Campanas;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.Familias;
import dev.tirador.aoe2radar.service.FiltroLista;
import dev.tirador.aoe2radar.service.FormService;
import dev.tirador.aoe2radar.service.ListaSeguidos;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.VistaInicial;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * La lógica de la Watchlist sin Swing (fase 5): qué vista está elegida (grupo, ★ ladder, ★ país, ★ clan), los
 * grupos y la lista de seguidos. Sale de WatchlistView tal cual: mismas comparaciones, mismos textos, mismo orden
 * de llamadas. La vista (WatchlistView, la fachada) implementa {@link Pantalla}: le dice al presentador qué hay
 * elegido en sus controles y hace lo que toca Swing (combo, lista, barra de estado).
 * <p>El modo de vista NO se guarda aquí: cada consulta vuelve a leer {@code pantalla.grupoSeleccionado()}, como
 * hacía la vista con el combo (así nunca hay dos verdades). Hilos: todo en el hilo que llama (el EDT en la app).
 */
public final class WatchlistPresenter {

    /** Lo que el presentador necesita de la vista: qué hay elegido en sus controles y los repintados. */
    public interface Pantalla {
        /** El item elegido en el combo de vistas (null si no hay ninguno). Se lee en cada consulta: nunca se guarda. */
        String grupoSeleccionado();
        /** Los items del combo de vistas, en su orden. */
        List<String> itemsCombo();
        /** El texto del campo de clan tal cual (sin recortar); null si el campo aún no existe. */
        String clanEscrito();
        /** Enseña/oculta los controles de cada modo (país, clan, Top N, campana, «Añadir», «Quitar»). */
        void actualizarBotonesModo();
        /** ★ Top clan con el campo vacío: lo rellena con el último clan cargado (config clan_tag). */
        void rellenarClanSiVacio();
        void cargarTopClan();
        void cargarTopLadder(boolean forzar);
        /** Reconstruye la lista visible (aplicarFiltroGrupo). */
        void refrescarFiltro();
        /** Barrido de vivos/ELO de los visibles que aún no se han mirado (refrescarWatchlist). */
        void refrescarWatchlist();
        /** Conteos, título y chip «Jugando» (actualizarIndicadoresVivos). */
        void indicadoresVivos();
        /** Mensaje en la barra de estado. */
        void estado(String texto);
        /** Rehace el combo de vistas con los grupos de ahora (rebuildGrupos). */
        void reconstruirGrupos();
        /** Tras fichar a alguien: ofrece añadir sus cuentas vinculadas (consulta y diálogo, en la vista). */
        void ofrecerVinculadasTrasAlta(long pid, String nombre, String grupo);
        /** ¿Está pulsado el chip «● Jugando» (solo los que juegan)? false si aún no existe. */
        boolean soloVivosMarcado();
        /** ¿Se enseña el ELO en la lista? (Configuración; la vista guarda la preferencia). */
        boolean mostrarEloWatch();
        /** El enlace con Partidas, leído de la vista en CADA uso (los tests lo cambian después de construir). */
        WatchlistView.EnlacePartidas enlace();
        /** Las campanas (service.Campanas), leídas de la vista en CADA uso, por el mismo motivo que enlace(). */
        Campanas campanas();
        /** Pinta el botón de la campana según esté encendida o no la de la vista actual. */
        void refrescarCampanaBtn();
    }

    private final Pantalla pantalla;
    private final String topPais, topClan;
    private final PaisItem[] paises;
    private final BiFunction<String, String, String> leerCfg;
    private final BiConsumer<String, String> guardarCfg;
    /** La lista de seguidos: la MISMA instancia que la ventana (y Ratings, Live now, Perfil...). */
    final List<Player> todosJugadores;
    /** Persistencia, grupos y altas/bajas/movimientos de la watchlist: ver service.ListaSeguidos. */
    final ListaSeguidos listaSeguidos;

    PaisItem paisActual;
    List<PaisItem> catalogoOrdenado = List.of();

    // ----- forma reciente -----
    private final FormService formaService;
    /** El «partidas jugadas» que conoce la app (la MISMA instancia que la ventana): lo lee FormService.porResta. */
    private final Map<Long, Integer> gamesWatch;
    static final Map<Long, Integer> TOP_STREAK = new java.util.concurrent.ConcurrentHashMap<>();   // racha del ladder (+3 / -2)
    static final Map<Long, int[]> TOP_LAST10 = new java.util.concurrent.ConcurrentHashMap<>();   // {ganadas, perdidas} de las últimas 10
    final Map<Long, Forma> forma24 = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<Long, Forma> forma7d = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<Long, Long> formaTs24 = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<Long, Long> formaTs7d = new java.util.concurrent.ConcurrentHashMap<>();
    volatile boolean formaVisible;   // el chip: nace apagado, no se recuerda
    volatile int ventanaForma = 24;   // 24 h o 7 d (selector junto al chip)

    // ----- lista visible, familias y ELO -----
    private final ProfileService perfiles;
    private final EstadoVivo vivo;
    /** El ELO conocido de cada jugador: la MISMA instancia que la ventana (Ratings, Live now, Perfil la leen). */
    private final Map<Long, Integer> eloWatch;
    /** El top cargado (ladder, país o clan): lo crea la vista; misma instancia y mismo monitor (topLadderSnapshot). */
    final List<Player> topLadder;
    private final FiltroLista filtroLista;   // qué fila se ve y en qué orden (sin Swing)
    final Set<Long> vinculosExpandidos = new HashSet<>();
    final Map<Long, Character> marcaFila = new HashMap<>();   // pid -> P(rincipal) / E(xpandida) / H(ija)
    final Map<Long, Boolean> vivoFamilia = new HashMap<>();   // principal -> alguna cuenta viva
    /** Familias: vínculos entre cuentas del mismo jugador (ver service.Familias). Un servicio sin red. */
    final Familias familiaSvc = new Familias();
    /** Jugadores cuyo ELO en eloWatch es el del snapshot nocturno (el barrido de un grupo sin red, o el resumen
     *  diario de ★ Top clan), no uno fresco. Lo escriben el EDT y lo lee el hilo de «Ver forma»: concurrente. */
    final Set<Long> eloDelSnapshot = java.util.concurrent.ConcurrentHashMap.newKeySet();
    final Set<Long> watchBarridos = new HashSet<>();   // seguidos ya consultados en este arranque

    // ----- campanas y avisos -----
    private final Tareas tareas;
    private final WatchlistView.Anfitrion anfitrion;
    private final MenusJugador menus;
    private final Navegacion navegacion;
    /** id de vista → jugadores vigilados: la MISMA instancia que la vista (la lee el hilo del socket y Live now). */
    private final Map<String, Set<Long>> campanaIds;

    /** topPais/topClan/paises: los de la vista (calculados al construirla, con el idioma ya fijado), no otros. */
    WatchlistPresenter(Pantalla pantalla, List<Player> todosJugadores, Path playersFile,
                       String topPais, String topClan, PaisItem[] paises,
                       BiFunction<String, String, String> leerCfg, BiConsumer<String, String> guardarCfg,
                       FormService formaService, Map<Long, Integer> gamesWatch,
                       ProfileService perfiles, EstadoVivo vivo, Map<Long, Integer> eloWatch, List<Player> topLadder,
                       Tareas tareas, WatchlistView.Anfitrion anfitrion, MenusJugador menus, Navegacion navegacion,
                       Map<String, Set<Long>> campanaIds) {
        this.pantalla = pantalla;
        this.tareas = tareas;
        this.anfitrion = anfitrion;
        this.menus = menus;
        this.navegacion = navegacion;
        this.campanaIds = campanaIds;
        this.todosJugadores = todosJugadores;
        this.perfiles = perfiles;
        this.vivo = vivo;
        this.filtroLista = new FiltroLista(vivo);
        this.eloWatch = eloWatch;
        this.topLadder = topLadder;
        this.formaService = formaService;
        this.gamesWatch = gamesWatch;
        this.topPais = topPais; this.topClan = topClan; this.paises = paises;
        this.leerCfg = leerCfg; this.guardarCfg = guardarCfg;
        this.listaSeguidos = new ListaSeguidos(playersFile, WatchlistView.GRUPO_GENERAL, leerCfg, guardarCfg);
    }

    // ===== Modo de vista (se lee SIEMPRE del combo, vía la Pantalla) ======================================

    public boolean modoTop() {
        String s = String.valueOf(pantalla.grupoSeleccionado());
        return WatchlistView.TOP_LADDER.equals(s) || topPais.equals(s) || topClan.equals(s);
    }
    public boolean modoClan() { return topClan.equals(String.valueOf(pantalla.grupoSeleccionado())); }
    public boolean modoPais() { return topPais.equals(String.valueOf(pantalla.grupoSeleccionado())); }
    public String paisSel() { return paisActual != null ? paisActual.code() : leerConfig("top_pais", "es"); }

    public String vistaActualId() {
        String g = pantalla.grupoSeleccionado();
        String pais = modoPais() ? paisSel() : "";
        return g + "|" + pais;
    }

    /** El grupo de usuario elegido; null = «Todos» o una vista ★ (ladder, país o clan: ninguna es un grupo donde
     *  fichar). Antes de la 1.3 se colaba «★ Top clan»: grupoDestino() lo daba como grupo y el invitado de
     *  «Ver sus partidas» nacía con ese grupo. */
    public String grupoActivo() {
        String s = pantalla.grupoSeleccionado();
        if (s == null) return null;
        return s.equals(t("Todos", "All")) || s.equals(WatchlistView.TOP_LADDER) || s.equals(topPais) || s.equals(topClan)
                || s.equals(t("+ Nuevo grupo…", "+ New group…"))
                || s.equals(t("Gestionar grupos…", "Manage groups…")) ? null : s;
    }

    public String grupoDestino() {
        String g = grupoActivo();
        return g != null ? g : WatchlistView.GRUPO_GENERAL;
    }

    /** El tag de clan escrito ahora mismo en el campo (Top clan); "" si no hay campo o está vacío. */
    public String clanBuscado() { String c = pantalla.clanEscrito(); return c == null ? "" : c.trim(); }

    public List<String> clanesGuardados() { List<String> l = new ArrayList<>(); for (String x : leerCfg.apply("clanes_guardados", "").split(",")) if (!x.isBlank()) l.add(x.trim()); return l; }

    /** El id interno de la vista para las campanas (service.Campanas): «★clan|r1», «★pais|es», «★ladder», «grupo|Amigos». */
    String idVistaCampana() {
        if (modoClan()) { String c = pantalla.clanEscrito(); return "\u2605clan|" + (c == null ? "" : c.trim().toLowerCase(Locale.ROOT)); }
        if (modoPais()) return "\u2605pais|" + paisSel();
        if (modoTop()) return "\u2605ladder";
        return "grupo|" + String.valueOf(pantalla.grupoSeleccionado());
    }

    /** El nombre de la vista actual tal como lo ve el usuario, para el texto de la campana: «★ Top país · España»,
     *  «★ Top clan · R1», «★ Top ladder», «Todos» o el nombre del grupo. Antes se enseñaba el id interno de
     *  idVistaCampana («★pais es», «grupo Amigos»: «grupo» en español también con la app en inglés). */
    String nombreVistaCampana() {
        if (modoClan()) return topClan + " \u00B7 " + clanBuscado();
        if (modoPais()) return topPais + " \u00B7 " + (paisActual != null ? paisActual.nombre() : paisSel().toUpperCase(Locale.ROOT));
        return String.valueOf(pantalla.grupoSeleccionado());
    }

    /** La firma de la lista cargada cuando es la de un clan: nunca coincide con la de ★ Top ladder («global») ni
     *  con la de ★ Top país (el código ISO), así que volver a esas vistas recarga (F1 de la revisión 1.3). */
    static String firmaClan(String tag) { return "clan|" + tag.toLowerCase(Locale.ROOT); }

    /** El combo de vistas cambió de item (listener de la vista): guarda la elección y carga lo que toca. */
    void grupoElegido() {
        String sel = String.valueOf(pantalla.grupoSeleccionado());
        if (sel.equals(topClan)) {
            guardarCfg.accept("grupo_activo", sel);
            pantalla.actualizarBotonesModo();
            pantalla.rellenarClanSiVacio();
            pantalla.cargarTopClan();
            return;
        }
        if (sel.equals(WatchlistView.TOP_LADDER) || sel.equals(topPais)) {
            guardarCfg.accept("grupo_activo", sel);
            pantalla.actualizarBotonesModo();
            pantalla.cargarTopLadder(false);
            return;
        }
        guardarCfg.accept("grupo_activo", sel);
        pantalla.actualizarBotonesModo();
        pantalla.refrescarFiltro();
        pantalla.refrescarWatchlist();
        pantalla.indicadoresVivos();
    }

    // ===== Campanas: aviso cuando alguien de una vista marcada entra en partida =========================

    public boolean campanaContiene(long pid) { return pantalla.campanas().campanaContiene(pid, campanaIds); }

    /** Recalcula (en segundo plano, service.Campanas) los jugadores de cada vista con campana y los mete en el socket. Cada 15 min para tops, pais y clan. */
    public void refrescarCampanas() {
        Set<String> s = pantalla.campanas().campanas();
        if (s.isEmpty()) {
            campanaIds.clear();
            // F11 de la 1.3: al apagar la última campana, el socket también deja de vigilar esa lista (antes solo
            // se vaciaba campanaIds y el socket seguía con los ids viejos). Conjunto MUTABLE: el anfitrión le suma
            // el top de Live now, si está abierto.
            anfitrion.actualizarSocketExtra(new HashSet<>());
            tareas.enUi(pantalla.enlace()::sincronizarSocket);
            return;
        }
        // Copia de todosJugadores AQUÍ, en el EDT (refrescarCampanas siempre se llama desde él: botón de campana,
        // Timer de Swing, arranque): mismo riesgo que la fila 106 si el hilo de fondo recorriera la lista de
        // verdad mientras el EDT la muta (altas, bajas, rebuildGrupos...).
        List<Player> jugadoresAhora = new ArrayList<>(todosJugadores);
        tareas.enFondo("campanas", () -> {   // con Tareas.SWING: new Thread(..., "campanas").start(), como antes
            Map<String, Set<Long>> nuevo = pantalla.campanas().calcularCampanaIds(s, jugadoresAhora);
            // fila 107 de DEUDA: retainAll+putAll en vez de clear+putAll, para que campanaIds nunca quede vacio
            // a medias mientras otro hilo (el socket) lo lee con tocaAvisar/campanaContiene.
            campanaIds.keySet().retainAll(nuevo.keySet());
            campanaIds.putAll(nuevo);
            Set<Long> todos = new HashSet<>(); for (Set<Long> x : nuevo.values()) todos.addAll(x);
            anfitrion.actualizarSocketExtra(todos);
            tareas.enUi(pantalla.enlace()::sincronizarSocket);
        });
    }

    /** «Mi partida»: si el que entra en partida soy yo (mi_pid), aviso con el rival (bandera, ELO, civ) y accesos a su perfil y al cara a cara. */
    public void avisarMiPartida(long pid, Match m) {
        if (!pantalla.campanas().tocaAvisarMiPartida(pid, m)) return;
        log("mi partida: el socket dice que mi partida " + m.id + " ha empezado (" + m.map + ", " + m.mode + ")");
        MatchPlayer yo0 = null; for (MatchPlayer p : m.players) if (p.id == pid) yo0 = p;
        if (yo0 == null) return;
        final MatchPlayer yo = yo0;
        List<MatchPlayer> rivales = new ArrayList<>(); for (MatchPlayer p : m.players) if (p.team != yo.team) rivales.add(p);
        StringBuilder txt = new StringBuilder("\u25CF " + t("Tu partida ha empezado", "Your game has started") + " \u00B7 " + (m.map == null ? "" : m.map) + " \u00B7 " + dev.tirador.aoe2radar.service.ReglasPartida.modoCorto(m) + " \u00B7 " + t("vs ", "vs "));
        for (int i = 0; i < rivales.size(); i++) { MatchPlayer r = rivales.get(i); if (i > 0) txt.append(", "); txt.append(anfitrion.nombreVisible(r.id, r.name)); Integer e1 = menus.elo1v1Conocido(r.id); if (e1 == null && r.rating != null) e1 = r.rating; if (e1 != null) txt.append(" (").append(e1).append(")"); if (r.civ != null) txt.append(" ").append(r.civ); }
        final MatchPlayer rival = rivales.isEmpty() ? null : rivales.get(0);
        List<Object[]> fichas = new ArrayList<>(); for (MatchPlayer r : rivales) { Integer e1 = menus.elo1v1Conocido(r.id); if (e1 == null) e1 = r.rating; fichas.add(new Object[]{ r.id, anfitrion.nombreVisible(r.id, r.name) + (r.civ != null ? "  ·  " + r.civ : ""), e1 }); }
        final String txtSup = "● " + t("Tu partida empieza", "Your game starts") + " · " + (m.map == null ? "" : m.map) + " · " + dev.tirador.aoe2radar.service.ReglasPartida.modoCorto(m);
        tareas.enUi(() -> {
            anfitrion.mostrarSuperposicion(txtSup, fichas, 60_000);   // sobre el juego, mientras carga
            anfitrion.mostrarToast(txt.toString(), m.id);
            if (rival != null && rival.id > 0) {   // accesos rápidos en el propio aviso
                anfitrion.agregarAccionesToast(
                        () -> navegacion.abrirPerfilEnPestana(rival.id, anfitrion.nombreVisible(rival.id, rival.name)),
                        () -> anfitrion.abrirPerfilYCaraACara(pid, leerConfig("mi_nombre", anfitrion.nombreVisible(pid, yo.name)), rival.id, anfitrion.nombreVisible(rival.id, rival.name)));
            }
        });
    }

    /** Alguien vigilado entra en partida: si está en una lista con campana, aviso (toast dentro de la app; Windows si está minimizada).
     *  El nombre y el texto se construyen DENTRO del invokeLater (fila 106 de DEUDA): nombreDe recorre
     *  todosJugadores/topLadder, que se leen y escriben desde el EDT (rebuildGrupos, altas, bajas...); llamarlo
     *  desde un hilo de fondo (vigilarTop/vigilarVivos) podía toparse con un ConcurrentModificationException. */
    public void avisarSiCampana(long pid, Match m) {
        if (!pantalla.campanas().tocaAvisar(pid, m, campanaIds)) return;
        tareas.enUi(() -> {
            String nombre = anfitrion.nombreVisible(pid, anfitrion.ahoraNombre(pid).equals(String.valueOf(pid)) ? nombreDe(pid) : anfitrion.ahoraNombre(pid));
            String resumen = pantalla.enlace().resumenVivo(m, pid);
            String texto = "\u25CF " + nombre + t(" ha empezado una partida", " started a game") + (resumen != null ? " \u00B7 " + resumen : "");
            anfitrion.mostrarToast(texto, m.id);   // solo dentro de la app: nada de notificaciones de Windows
        });
    }

    /** ¿Está encendida la campana de la vista que se mira ahora? */
    boolean campanaActiva() { return pantalla.campanas().campanas().contains(idVistaCampana()); }

    /** El botón de la campana: la enciende o la apaga para la vista actual, recalcula y lo dice en el estado. */
    void alternarCampana() {
        String id = idVistaCampana();
        boolean activo = pantalla.campanas().alternar(id);
        pantalla.refrescarCampanaBtn();
        refrescarCampanas();
        pantalla.estado(activo ? t("Avisos activados para «", "Alerts on for \u201C") + nombreVistaCampana() + t("»: te avisaré cuando alguien entre en partida.", "\u201D: you'll get a notice when someone starts a game.") : t("Avisos apagados para esta lista.", "Alerts off for this list."));
    }

    private String nombreDe(long pid) { for (Player p : todosJugadores) if (p.id() == pid) return p.name(); for (Player p : topLadder) if (p.id() == pid) return p.name(); return String.valueOf(pid); }

    // ===== Lista visible: filtro, familias y conteos ======================================================

    /** El cálculo de aplicarFiltroGrupo: qué filas se ven y en qué orden (grupo, «Jugando», orden por ELO/forma/
     *  nombre, familias plegadas o no). Deja marcaFila y vivoFamilia al día; el invitado, los sujetos, el modelo y
     *  la selección los resuelve la vista con la lista que devuelve (una lista nueva, suya). */
    List<Player> filasVisibles() {
        podarEloDelSnapshot();   // tras cambiar el top o quitar a alguien, la marca de «ELO de anoche» no se acumula
        String g = grupoActivo();
        boolean soloVivos = pantalla.soloVivosMarcado();
        boolean porElo = pantalla.mostrarEloWatch() && "elo".equals(leerConfig("orden_watch", "elo"));
        String ordenCfg = leerConfig("orden_watch", "elo");
        boolean porForma = formaVisible && ordenCfg.startsWith("forma");
        boolean formaAsc = "forma_asc".equals(ordenCfg);   // ascendente = los que más bajan, arriba
        final Map<Long, Forma> fa = formaActiva();
        FiltroLista.Resultado res = filtroLista.filtrar(todosJugadores, topLadder, modoTop(), g, soloVivos,
                porForma, formaAsc, porElo, fa, eloWatch, vinculosExpandidos);
        marcaFila.clear();
        marcaFila.putAll(res.marcaFila());
        vivoFamilia.clear();
        vivoFamilia.putAll(res.vivoFamilia());
        return res.filas();
    }

    /** Suma a la lista todas las cuentas vinculadas de cada jugador — salvo
     *  las hijas seleccionadas explícitamente, que van solas (control fino). */
    public List<Player> conFamilias(List<Player> base) {
        Map<Long, Player> out = new java.util.LinkedHashMap<>();
        for (Player p : base) out.putIfAbsent(p.id(), p);
        for (Player p : base)
            if (p.vinculo() != 0 && marcaFila.getOrDefault(p.id(), ' ') != 'H')
                for (Player x : todosJugadores)
                    if (x.vinculo() == p.vinculo()) out.putIfAbsent(x.id(), x);
        return new ArrayList<>(out.values());
    }

    /** Tooltip de la columna Jugador: si la cuenta es hermana de una familia,
     *  dice de quién. Null si no aplica. */
    public String tipCuentaVinculada(Match m) {
        String ref = pantalla.enlace().refNombre(m);
        for (Player x : todosJugadores)
            if (x.name().equals(ref) && x.vinculo() != 0) {
                Player matriz = null;
                Integer mejor = null;
                for (Player y : todosJugadores)
                    if (y.vinculo() == x.vinculo()) {
                        Integer e = eloWatch.get(y.id());
                        if (matriz == null || (e != null && (mejor == null || e > mejor))) { matriz = y; mejor = e; }
                    }
                if (matriz != null && matriz.id() != x.id())
                    return t("Cuenta vinculada de ", "Linked account of ") + matriz.name();
                return null;
            }
        return null;
    }

    /** La cuenta hermana con MÁS ELO conocido que la propia, o null.
     *  Bebe de los vínculos guardados y de las familias consultadas. */
    String[] mejorAlt(long pid) {
        return familiaSvc.mejorAlt(pid, todosJugadores, perfiles.familia(pid), eloWatch);
    }

    /** Un vinculo que agrupa a UNA sola cuenta es un fantasma (p. ej. de
     *  cuando el companion devolvía al propio jugador como vinculada). */
    public void sanearVinculosHuerfanos() { if (familiaSvc.sanearVinculosHuerfanos(todosJugadores)) guardarJugadores(); }

    /** Casa un conjunto de cuentas bajo la misma familia (clave = menor id). */
    public void marcarVinculo(Set<Long> ids) { if (familiaSvc.marcarVinculo(todosJugadores, ids)) { guardarJugadores(); pantalla.refrescarFiltro(); } }   // el vínculo sobrevive al cierre

    /** ¿Hay alguien jugando ahora en el grupo? (null = en cualquiera). */
    boolean grupoTieneVivo(String grupo) {
        for (Player p : todosJugadores)
            if ((grupo == null || p.grupo().equalsIgnoreCase(grupo)) && vivo.jugando(p.id()))
                return true;
        return false;
    }

    /** Cuántos de toda la Watchlist juegan ahora (el título «Watchlist — N jugando»). */
    int vivosEnLista() {
        int nVivos = 0;
        for (Player p : todosJugadores) if (vivo.jugando(p.id())) nVivos++;
        return nVivos;
    }

    /** Cuántos juegan ahora en lo que se mira: el top cargado en los modos ★, o el grupo elegido (todos si «Todos»). */
    int vivosEnAmbito() {
        String g = grupoActivo();
        int nAmbito = 0;
        for (Player p : (modoTop() ? topLadder : todosJugadores))
            if ((modoTop() || g == null || p.grupo().equalsIgnoreCase(g)) && vivo.jugando(p.id())) nAmbito++;
        return nAmbito;
    }

    // ===== ELO de anoche frente a ELO fresco (F5 de la revisión 1.3) ======================================

    /** El «ELO actual» que «Ver forma» resta al de anoche (FormService.porResta): null si el que se conoce ES el de
     *  anoche, porque la resta daría 0 y «sin partidas» (F5 de la revisión 1.3). Con null, porResta no sirve y la
     *  forma va por la vía exacta (porSerie, una llamada). */
    static Integer eloParaResta(long pid, Map<Long, Integer> eloWatch, Set<Long> delSnapshot) {
        return delSnapshot.contains(pid) ? null : eloWatch.get(pid);
    }

    /** eloParaResta con el ELO y las marcas de esta Watchlist (lo que «Ver forma» le pasa a la resta). */
    Integer eloParaResta(long pid) { return eloParaResta(pid, eloWatch, eloDelSnapshot); }

    // Orden de escritura (revisor 1.3): el hilo de «Ver forma» lee primero la marca y luego el ELO (eloParaResta). Para
    // que nunca vea «sin marca + ELO de anoche» (resta 0, el fallo F5), todo cambio pasa por «marcado»: un ELO de anoche
    // se marca ANTES de escribirlo; uno fresco se escribe ANTES de quitar la marca. En la ventana intermedia, como mucho,
    // la forma va por la vía exacta (una llamada de más), nunca por una resta falsa.

    /** Un ELO fresco (API, leaderboard): primero el valor, después se quita la marca de «de anoche». */
    public void ponerEloFresco(long pid, int elo) {
        eloWatch.put(pid, elo);
        eloDelSnapshot.remove(pid);
    }

    /** Un ELO de anoche (snapshot nocturno, resumen diario del clan): primero la marca, después el valor. */
    void ponerEloDeAnoche(long pid, int elo) {
        eloDelSnapshot.add(pid);
        eloWatch.put(pid, elo);
    }

    /** Poda de eloDelSnapshot (revisor 1.3): quien ya no está ni en la Watchlist ni en el top/clan cargado pierde la
     *  marca, y también su «ya barrido» (watchBarridos): su ELO de anoche sigue en eloWatch (lo comparten otras
     *  vistas), así que si vuelve a la Watchlist se barre otra vez y la marca se pone de nuevo. En el EDT. */
    void podarEloDelSnapshot() {
        if (eloDelSnapshot.isEmpty()) return;
        Set<Long> presentes = new HashSet<>();
        for (Player p : todosJugadores) presentes.add(p.id());
        for (Player p : topLadder) presentes.add(p.id());
        for (Long pid : new ArrayList<>(eloDelSnapshot))
            if (!presentes.contains(pid)) { eloDelSnapshot.remove(pid); watchBarridos.remove(pid); }
    }

    // ===== Forma reciente (±ELO en una ventana de horas; 1v1 ranked) ======================================

    static String formaLarga(Forma f) {
        if (f.partidas() == 0) return t("sin partidas 1v1 en la ventana", "no 1v1 games in the window");
        String r = f.racha() >= 2 ? " \u00B7 " + t("racha ", "streak ") + f.racha() + (f.rachaGana() ? t("V", "W") : t("D", "L")) : "";
        return f.w() + "-" + f.l() + " \u00B7 " + (f.diff() >= 0 ? "+" : "") + f.diff() + r;
    }

    static String tipVerForma() { return t("Consulta el ±ELO reciente (según el selector) y lo muestra como columna ordenable junto al ELO. Con jugadores seleccionados consulta solo esos; sin selección, todos. No se recuerda entre sesiones.",
            "Fetches the recent ±ELO (per the selector) and shows it as a sortable column next to the ELO. With players selected it checks only those; with none, everyone. Not remembered between sessions."); }
    static String tipOcultarForma() { return t("Oculta la columna Forma (los datos siguen en caché 10 min).", "Hides the Recent form column (data stays cached for 10 min)."); }

    Map<Long, Forma> formaActiva() { return ventanaForma <= 24 ? forma24 : forma7d; }

    /** Los de {@code objetivo} cuya forma de esa ventana hay que (re)consultar: caducada (10 min) o nunca pedida. */
    List<Player> pendientesForma(List<Player> objetivo, int horas) {
        Map<Long, Long> ts = horas <= 24 ? formaTs24 : formaTs7d;
        List<Player> pendientes = new ArrayList<>();
        long ahora = dev.tirador.aoe2radar.util.Reloj.SISTEMA.ahoraMs();   // una sola lectura del reloj para todo el lote, como la 1.1
        for (Player p : objetivo) if (formaService.pendiente(ts.getOrDefault(p.id(), 0L), ahora)) pendientes.add(p);
        return pendientes;
    }

    /** ¿Hay que avisar antes de consultar? Solo en los tops y con más de 20 jugadores pendientes (tarda). */
    boolean confirmarForma(int pendientes) { return modoTop() && pendientes > 20; }

    /** El texto del aviso: cuántos jugadores y cuánto tarda (~0,6 s por jugador). */
    static String textoConfirmarForma(int pendientes, int horas) {
        int seg = (int) Math.ceil(pendientes * 0.6);
        return (horas <= 24 ? t("Consultar la forma de las últimas 24 h de ", "Fetching the last 24 h form of ")
                            : t("Consultar la forma de los últimos 7 días de ", "Fetching the last 7 days form of "))
                + pendientes + t(" jugadores tarda ~", " players takes ~") + seg + " s.\n"
                + t("Se consulta jugador a jugador (con pausas) y queda guardado 10 minutos.", "It goes player by player (with pauses) and is cached for 10 minutes.");
    }

    /**
     * El trabajo de «Ver forma», en el hilo de fondo del SwingWorker de la vista (que sigue siendo el transporte: su
     * hilo, su progreso y su done()). 1) Resta con el snapshot nocturno, sin llamadas; 2) quien no está en el
     * snapshot: su serie de rating, exacta (una llamada por jugador), hasta que se pida Detener. {@code eloActual}:
     * el «ELO actual» para la resta (null si el conocido es el de anoche); {@code publicar}: el texto de progreso.
     */
    void consultarForma(List<Player> pendientes, int horas, LongFunction<Integer> eloActual,
                        Consumer<String> publicar, BooleanSupplier detener) {
        List<Player> porApi = new ArrayList<>();
        long ahoraTs = System.currentTimeMillis();
        for (Player p : pendientes) {   // 1) resta con el snapshot nocturno: sin llamadas
            Forma[] f = formaService.porResta(p.id(), eloActual, gamesWatch::get);
            if (f == null) { porApi.add(p); continue; }
            forma24.put(p.id(), f[0]); formaTs24.put(p.id(), ahoraTs);
            if (f[1] != null) { forma7d.put(p.id(), f[1]); formaTs7d.put(p.id(), ahoraTs); } else if (horas > 24) porApi.add(p);
        }
        publicar.accept(t("Forma: ", "Recent form: ") + (pendientes.size() - porApi.size()) + t(" del snapshot nocturno", " from the nightly snapshot") + (porApi.isEmpty() ? "" : " · " + porApi.size() + t(" consultas", " requests")));
        for (Player p : porApi) {   // 2) quien no está en el snapshot: su serie de rating, exacta (una llamada por jugador)
            if (detener.getAsBoolean()) break;
            try {
                Forma[] ambas = formaService.porSerie(p.id());
                if (ambas != null) { forma24.put(p.id(), ambas[0]); formaTs24.put(p.id(), ahoraTs); forma7d.put(p.id(), ambas[1]); formaTs7d.put(p.id(), ahoraTs); }
            } catch (Exception ex) { log("forma " + p.name() + ": " + causa(ex)); }
        }
    }

    /** Al apagar la columna Forma: si la lista iba ordenada por forma, vuelve a ordenarse por ELO (util.Config). */
    void quitarOrdenPorForma() {
        if (leerConfig("orden_watch", "elo").startsWith("forma")) dev.tirador.aoe2radar.util.Config.guardarConfig("orden_watch", "elo");
    }

    private String rachaTexto(long pid, Forma f) {
        int n = 0; boolean gana = true;
        if (f != null && f.racha() >= 2) { n = f.racha(); gana = f.rachaGana(); }
        else if (modoTop() && TOP_STREAK.containsKey(pid) && Math.abs(TOP_STREAK.get(pid)) >= 2) { n = Math.abs(TOP_STREAK.get(pid)); gana = TOP_STREAK.get(pid) > 0; }
        if (n == 0) return "";
        return " \u00B7 " + n + (gana ? t(" victorias seguidas", " wins in a row") : t(" derrotas seguidas", " losses in a row"));
    }

    /** Tooltip de la celda Forma (lo pide playersList.getToolTipText, en la ventana). */
    public String tipForma(long pid) {
        Forma f = formaActiva().get(pid);
        StringBuilder sb = new StringBuilder(ventanaForma <= 24 ? t("Últimas 24 h: ", "Last 24 h: ") : t("Últimos 7 días: ", "Last 7 days: "));
        if (f == null) sb.append(t("sin consultar (selecciónalo y pulsa Ver forma)", "not fetched (select them and press Recent form)"));
        else if (f.partidas() == 0) sb.append(t("sin partidas 1v1", "no 1v1 games"));
        else sb.append(f.w()).append("-").append(f.l()).append(" \u00B7 ").append(f.diff() >= 0 ? "+" : "").append(f.diff()).append(rachaTexto(pid, f));
        int[] l10 = TOP_LAST10.get(pid);
        if (modoTop() && l10 != null) sb.append(" \u00B7 ").append(t("últimas 10: ", "last 10: ")).append(l10[0]).append("-").append(l10[1]);
        return sb.toString();
    }

    // ===== País (★ Top país) ==============================================================================

    /** El catálogo en el orden del selector: el país guardado primero y el resto por nombre. Deja ese orden en
     *  catalogoOrdenado y el primero (el favorito) como paisActual. */
    List<PaisItem> ordenarPaises(String paisGuardado) {
        List<PaisItem> ordenPais = new ArrayList<>();
        for (PaisItem pi : paises) if (pi.code().equals(paisGuardado)) ordenPais.add(pi);
        List<PaisItem> resto = new ArrayList<>();
        for (PaisItem pi : paises) if (!pi.code().equals(paisGuardado)) resto.add(pi);
        resto.sort(Comparator.comparing(PaisItem::nombre, String.CASE_INSENSITIVE_ORDER));
        ordenPais.addAll(resto);
        catalogoOrdenado = ordenPais;
        paisActual = ordenPais.get(0);   // el favorito guardado
        return ordenPais;
    }

    /** Los países del catálogo que casan con lo tecleado en el buscador (sin tildes; por nombre o por código). */
    List<PaisItem> paisesQueCoinciden(String texto) {
        String q = dev.tirador.aoe2radar.util.Texto.sinTildes(texto.trim());
        List<PaisItem> out = new ArrayList<>();
        for (PaisItem pi : catalogoOrdenado)
            if (q.isEmpty() || dev.tirador.aoe2radar.util.Texto.sinTildes(pi.nombre()).contains(q) || pi.code().startsWith(q))
                out.add(pi);
        return out;
    }

    void fijarPais(PaisItem pi) {
        paisActual = pi;
        dev.tirador.aoe2radar.util.Config.guardarConfig("top_pais", pi.code());
        if (modoPais()) pantalla.cargarTopLadder(true);
    }

    // ===== Grupos (lectura) ================================================================================

    /** Grupos donde se puede fichar o mover a alguien: «General», los grupos con gente ahora mismo y los guardados
     *  en config (service.ListaSeguidos.gruposDisponibles). Es el único sitio que los calcula: menú contextual,
     *  diálogos, esGrupoDeUsuario y, vía la ventana, DialogosJugador y MenusJugadorSwing (DEUDA fila 134). */
    public Set<String> gruposDisponibles() { return listaSeguidos.gruposDisponibles(todosJugadores); }

    /** Grupos creados por el usuario (existen aunque estén vacíos). */
    public Set<String> gruposConfig() { return listaSeguidos.gruposConfig(); }

    /** ¿"nombre" es un grupo de verdad (General, uno con jugadores dentro o uno registrado en config)? */
    public boolean esGrupoDeUsuario(String nombre) { return gruposDisponibles().contains(nombre); }

    public String grupoDeJugador(long id) { return listaSeguidos.grupoDeJugador(todosJugadores, id); }

    public boolean containsPlayerId(long id) { return listaSeguidos.contiene(todosJugadores, id); }

    static String limpiarGrupo(String nombre) { return nombre.trim().replace(";", " ").replace(",", " "); }

    // ===== Operaciones de la lista (mismo orden que la vista: guardar, combo, filtro, indicadores, estado) =====

    // Delegado a service.ListaSeguidos (cargar/guardar players.txt); la vista muestra el error, si lo hay, en el status.
    // OJO (deuda ya existente en la 1.1, no se cambia aquí): esta E/S de disco es síncrona y se llama desde el EDT
    // (arranque, botones, menús): el disco se toca en el EDT.
    void cargarJugadores() {
        String error = listaSeguidos.cargar(todosJugadores);
        if (error != null) pantalla.estado(error);
        pantalla.reconstruirGrupos();       // SIEMPRE: sin esto, el combo quedaba vacío en instalaciones nuevas
        pantalla.refrescarFiltro();
    }

    void guardarJugadores() {
        String error = listaSeguidos.guardar(todosJugadores);
        if (error != null) pantalla.estado(error);
    }

    void registrarGrupo(String g) { listaSeguidos.registrarGrupo(g); pantalla.reconstruirGrupos(); }

    void moverJugador(Player p, String grupo) {
        listaSeguidos.moverJugador(todosJugadores, p, grupo);
        guardarJugadores();
        pantalla.reconstruirGrupos();
        pantalla.refrescarFiltro();
        pantalla.indicadoresVivos();
        pantalla.estado(p.name() + t(" movido al grupo «", " moved to group “") + grupo + t("».", "”."));
    }

    void renombrarGrupo(String viejo, String nuevo) {
        listaSeguidos.renombrarGrupo(todosJugadores, viejo, nuevo);
        guardarJugadores();
        pantalla.reconstruirGrupos();
        pantalla.refrescarFiltro();
    }

    void borrarGrupo(String g) {
        listaSeguidos.borrarGrupo(todosJugadores, g);
        guardarJugadores();
        pantalla.reconstruirGrupos();
        pantalla.refrescarFiltro();
    }

    /** Ficha a uno desde un top y, como el buscador, ofrece sus cuentas vinculadas. */
    void ficharDesdeTop(Player p, String g) {
        if (!listaSeguidos.ficharDesdeTop(todosJugadores, p, g)) return;
        guardarJugadores();
        pantalla.reconstruirGrupos();
        pantalla.refrescarFiltro();
        pantalla.estado(p.name() + t(" añadido a «", " added to \u201C") + g + t("» de tu watchlist.", "\u201D in your watchlist."));
        pantalla.ofrecerVinculadasTrasAlta(p.id(), p.name(), g);
    }

    void moverVarios(List<Player> lista, String g) {
        int n = listaSeguidos.moverVarios(todosJugadores, lista, g);
        guardarJugadores(); pantalla.reconstruirGrupos(); pantalla.refrescarFiltro(); pantalla.refrescarWatchlist();
        pantalla.estado(n + t(" jugadores movidos a «", " players moved to \u201C") + g + t("\u00bb.", "\u201D."));
    }

    void quitarDeWatchlist(long id) {
        listaSeguidos.quitar(todosJugadores, id);
        guardarJugadores();
        pantalla.refrescarFiltro();
        pantalla.estado(t("Quitado de tu watchlist.", "Removed from your watchlist."));
    }

    /** «Quitar los N seleccionados de la Watchlist» (menú contextual con varios elegidos). */
    void quitarVarios(List<Player> sel) {
        Set<Long> ids = new HashSet<>(); for (Player x : sel) ids.add(x.id());
        todosJugadores.removeIf(x -> ids.contains(x.id()));
        guardarJugadores(); pantalla.reconstruirGrupos(); pantalla.refrescarFiltro();
        pantalla.estado(ids.size() + t(" jugadores quitados.", " players removed."));
    }

    /** Los items fijos del combo, delante de los grupos: «Todos» y las tres vistas ★. */
    List<String> itemsFijos() { return List.of(t("Todos", "All"), WatchlistView.TOP_LADDER, topPais, topClan); }

    /** El grupo_activo guardado (el que rebuildGrupos intenta volver a elegir). */
    String grupoGuardado() { return leerCfg.apply("grupo_activo", t("Todos", "All")); }

    /** Los grupos que van en el combo tras los fijos: un grupo ya nunca se esfuma al vaciarse. */
    Set<String> calcularGrupos() { return listaSeguidos.calcularGrupos(todosJugadores); }

    /**
     * Qué item del combo corresponde al grupo guardado, o -1. Dos pasadas, EN ESTE ORDEN: 1) coincidencia exacta,
     * como hacía la 1.1 (equalsIgnoreCase a secas). 2) solo si no hubo, la equivalencia bilingüe
     * (coincideGrupoGuardado). Si se hiciera al revés, un grupo de usuario que se llame igual que un texto
     * bilingüe del combo (p.ej. un grupo llamado "All") nunca se podría seleccionar: siempre ganaría la
     * traducción a "Todos" antes de llegar a él en la lista.
     */
    int indiceGuardado(List<String> items, String guardado) {
        for (int i = 0; i < items.size(); i++)
            if (items.get(i).equalsIgnoreCase(guardado)) return i;
        for (int i = 0; i < items.size(); i++)
            if (coincideGrupoGuardado(items.get(i), guardado)) return i;
        return -1;
    }

    /**
     * ¿Es "itemActual" (un item del combo, en el idioma activo ahora mismo) la variante bilingüe de "guardado"
     * (el valor de config grupo_activo, escrito quizá en una sesión con otro idioma)? Solo se mira cuando no hay
     * ninguna coincidencia EXACTA con ningún item (ver indiceGuardado): así, un grupo de usuario con ese mismo
     * nombre por casualidad siempre gana a la traducción, que es solo una red de seguridad. Los grupos del usuario
     * son texto libre, no bilingüe: aquí solo se traducen "Todos"/"All", TOP_PAIS y TOP_CLAN, los tres textos fijos
     * del combo que sí cambian con el idioma. Sin esto, cambiar el idioma de la app "perdía" el grupo activo
     * guardado (bug resuelto en fase 4, ver docs/DEUDA.md).
     */
    private boolean coincideGrupoGuardado(String itemActual, String guardado) {
        if (itemActual.equals(t("Todos", "All"))) return guardado.equalsIgnoreCase("Todos") || guardado.equalsIgnoreCase("All");
        if (itemActual.equals(topPais)) return guardado.equalsIgnoreCase("\u2605 Top pa\u00eds") || guardado.equalsIgnoreCase("\u2605 Country top");
        if (itemActual.equals(topClan)) return guardado.equalsIgnoreCase("\u2605 Top clan") || guardado.equalsIgnoreCase("\u2605 Clan top");
        return false;
    }

    // ===== «Abrir en» (Configuración): la vista con la que abre la app =================================

    /** Los grupos de usuario del combo (lo que hay detrás de «Todos» y las tres vistas ★), en su orden. */
    public List<String> gruposDelCombo() {
        List<String> gs = new ArrayList<>();
        for (String s : pantalla.itemsCombo())
            if (!s.equals(t("Todos", "All")) && !s.equals(WatchlistView.TOP_LADDER) && !s.equals(topPais) && !s.equals(topClan)) gs.add(s);
        return gs;
    }

    /** La elección de «Abrir en» guardada, ya comprobada contra lo que existe hoy (service.VistaInicial): si el país,
     *  el clan (entre los guardados) o el grupo ya no existen, ★ Top ladder. No toca nada. */
    public VistaInicial.Eleccion eleccionInicial() {
        List<String> codigos = new ArrayList<>();
        for (PaisItem pi : paises) codigos.add(pi.code());
        return VistaInicial.resolver(VistaInicial.leer(leerCfg.apply(VistaInicial.CLAVE, "top")), codigos, clanesGuardados(), gruposDelCombo());
    }
}
