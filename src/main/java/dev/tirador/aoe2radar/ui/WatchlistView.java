package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.service.Campanas;
import dev.tirador.aoe2radar.service.EloSesion;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.FormService;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.TopLadderService;
import dev.tirador.aoe2radar.service.VistaInicial;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * La Watchlist (panel izquierdo): jugadores seguidos, grupos, tops (ladder/país/clan), forma reciente,
 * campanas de aviso y alta de jugadores. Sale de SpoilerFreeRecs (construirWatchlist y todo lo que colgaba
 * de ella en la 1.1) tal cual: mismo texto, mismos colores, mismo orden. La ventana conserva
 * {@code todosJugadores}/{@code playersModel}/{@code playersList}/{@code eloWatch}/{@code gamesWatch} (los
 * crea ella, en el mismo punto de siempre) porque otras piezas ya extraídas (Ratings, Live now, Directos,
 * Perfil) y la propia tabla de Partidas leen esas MISMAS instancias; esta vista las recibe por constructor,
 * igual que {@code twitchLive} en Directos/Live now.
 * <p>Lo que la Watchlist necesita de Partidas (aún dentro de SpoilerFreeRecs) llega por {@link EnlacePartidas};
 * lo que necesita del resto de la ventana (estado global, red que no es de servicio, componentes compartidos),
 * por {@link Anfitrion}.
 */
public final class WatchlistView implements WatchlistPresenter.Pantalla {

    /** Un grupo especial vale para el resto de la app; General es de service.ListaSeguidos. */
    public static final String GRUPO_GENERAL = "General";

    /** Lo que la Watchlist pide a Partidas (ui.PartidasView; la ventana lo cablea). */
    public interface EnlacePartidas {
        void fetchMatches();
        void mostrarDirectos(boolean mostrar);
        void refrescarSujetos(List<Player> tracked, boolean esInvitado);
        List<Player> ultimosSujetos();
        void taparResultados();
        void applyFilters();
        void actualizarTextoBuscar();
        void limpiarSujetos();
        void sincronizarSocket();
        String resumenVivo(Match m, long pid);
        /** Presentación de la columna Jugador de la tabla de Partidas (refId → nombre, o «GTE N»). */
        String refNombre(Match m);
        /** table.repaint(): la tabla se crea DESPUÉS que la Watchlist (construirTablaPartidas), no se inyecta. */
        void repintarTabla();
        /** objetivoForzado = p; invitado = p; vistaDelInvitado = vistaId (mismo orden que la 1.1). */
        void fijarObjetivo(Player p, String vistaId);
        Player invitado();
        void limpiarInvitado();
        String vistaDelInvitado();
        boolean sujetosPanelVisible();
        String vistaDeSujetos();
    }

    /** Lo que la Watchlist necesita del resto de la ventana (cromo, red que no es de servicio, semáforo de
     *  operación) que no encaja en las interfaces de servicio inyectadas. */
    public interface Anfitrion {
        String nombreVisible(long pid, String nombre);
        String paisDe(long pid);
        void aprenderPais(long pid, String pais);
        void aprenderCanal(long pid, String canal);
        void cargarEloAyer();
        boolean clanesVacios();
        void asegurarLadderEnFondo();
        List<Map.Entry<String, Integer>> sugerirClanes(String texto);
        void trabajando(boolean on);
        long opSerial();
        void marcarHiloOperacionActual();
        boolean detenerOperacion();
        void dormir(long ms);
        void abrirUrl(String url);
        void espectar(Player p);
        Path rutaCaptureAge();
        void lanzarCaptureAge(Path rec);
        void mostrarToast(String texto, long matchId);
        /** Botones «Su perfil»/«Cara a cara» que avisarMiPartida añade al toast (el JPanel toast es de la ventana). */
        void agregarAccionesToast(Runnable accionPerfil, Runnable accionCaraACara);
        /** abrirPerfil(pid, miNombre) y, pasados 1200 ms, si sigue abierto ese perfil, el cara a cara con el rival. */
        void abrirPerfilYCaraACara(long pid, String miNombre, long rivalId, String rivalNombre);
        /** Tarjeta de perfil del hover (hoy inerte): la caché vive en cache.CacheMemoria, que ui no puede importar. */
        Object[] tarjetaPerfilCache(long pid);
        void tarjetaPerfilGuardar(long pid, Object[] valor);
        /** ¿La pestaña Perfil está abierta? (perfil.abierto()): cambia el flujo de jugadorElegido/addPlayerDialog. */
        boolean perfilAbierto();
        /** Icono vectorial de pestaña (campana incluida); sigue viviendo en la ventana, la comparten las pestañas. */
        javax.swing.Icon iconoVista(String tipo);
        /** playersList cambia de selección: la ventana avisa a Ratings/Perfil si están abiertos (mismo orden que la 1.1). */
        void seleccionCambiada();
        /** ¿La partida sigue realmente en curso? (cache.Vivos.enCursoReal, ui no puede importar cache). */
        boolean enCursoReal(Match m);
        /** api.CompanionApi.perfil/pagina, solo para la tarjeta de hover (hoy inerte). */
        Perfil perfilApi(long pid) throws Exception;
        dev.tirador.aoe2radar.model.PaginaPartidas paginaApi(long pid, int pagina, int porPagina) throws Exception;
        /** ui.DirectosView y ui.MiPartidaPanel se construyen DESPUÉS que la Watchlist (construirCentro): no se
         *  pueden inyectar por constructor sin capturar null. La ventana los llama por su nombre cuando ya existen. */
        void reiniciarThrottleDirectos();
        void vigilarTwitchDirectos();
        void mostrarSuperposicion(String texto, List<Object[]> fichas, int ms);
        /** liveNow.socketExtra: además de las campanas, Live now (si está abierto) vigila su propio top 250.
         *  ids tiene que ser MUTABLE: la ventana le añade el top de Live now antes de usarlo (nunca Set.of()). */
        void actualizarSocketExtra(Set<Long> ids);
        /** liveNow.ahoraNombre(pid): el nombre que Live now ya conoce de un jugador en curso, o el propio pid si no
         *  lo conoce (sin guarda de null, como la base: liveNow siempre existe cuando se llama). */
        String ahoraNombre(long pid);
    }

    // ----- colaboradores inyectados (mismas instancias que la ventana) -----
    final Window ventana;
    final ProfileService perfiles;
    final dev.tirador.aoe2radar.service.BusquedaPerfiles busqueda;
    final TopLadderService topLadderService;
    final FormService formaService;
    final Campanas campanas;
    final BarridoVivos barridoVivos;
    final EloSesion eloSesion;
    final MenusJugador menus;
    final DialogosJugador dialogos;
    final Navegacion navegacion;
    private final Tareas tareas;
    final EnlacePartidas enlacePartidas;
    final Anfitrion anfitrion;

    // ----- estado compartido con la ventana (creado allí, inyectado aquí: misma instancia) -----
    final List<Player> todosJugadores;
    final DefaultListModel<Player> playersModel;
    final JList<Player> playersList;
    final Map<Long, Integer> eloWatch;
    final Map<Long, Integer> gamesWatch;
    final Map<Long, String[]> twitchLive;
    final Map<Long, String> aliases;
    final JLabel status;
    final JProgressBar progreso;
    final List<Match> all;
    public JToggleButton soloVivosBtn;   // visible: fetchMatches (Partidas) lo consulta; se crea en construirPanel(), como la 1.1
    final JPanel sujetosPanel;   // de Partidas (lo rellena PartidasView.refrescarSujetos); solo lo insertamos en el layout

    static final EstadoVivo VIVO = EstadoVivo.SISTEMA;

    // ----- propios -----
    /** config.properties (util.Config en la app). Inyectable para que los tests de grupos usen un mapa en memoria y no
     *  el fichero real: en Windows, un guardado que falla en silencio (Config traga la IOException del move atómico
     *  si el fichero está bloqueado un instante) dejaba grupo_activo sin escribir y el test leía «Todos» (el flaky de
     *  la fila 146 de DEUDA). Pasan por aquí grupo_activo, abrir_en, clanes_guardados y lo de ListaSeguidos; el resto
     *  de claves (orden, país, clan...) sigue con util.Config directamente, en esta clase y en sus piezas. */
    final BiFunction<String, String, String> leerCfg;
    final BiConsumer<String, String> guardarCfg;
    /** La lógica sin Swing (modo de vista, grupos, lista de seguidos...): ver ui.WatchlistPresenter. */
    final WatchlistPresenter presenter;
    public final JComboBox<String> grupoCombo = new JComboBox<>();   // visible para RegresionCapturas
    public boolean mostrarEloWatch = Boolean.parseBoolean(leerConfig("elo_watchlist", "true"));   // visible: el ítem «Mostrar ELO en la Watchlist» del menú Configuración (cromo) lo lee/escribe
    boolean vigilando = false;
    private JPanel watchPanel;
    public JPanel norteWatchRef;   // visible: la ventana repinta al restaurar el ancho del split (mostrarDirectos/cerrarTechTree)
    public javax.swing.border.TitledBorder tituloWatch;   // visible para RegresionCapturas
    JButton delBtn;
    public JLabel cabLabel;   // visible: lo consulta esFondoDeseleccionable (cromo)
    public JLabel resumenWatch;   // visible para RegresionCapturas; ejemplo: «50 jugadores · 6 en directo»

    public static final String TOP_LADDER = "\u2605 Top ladder";
    /** No son "static final": si lo fueran (como en la 1.1), se evaluarían al cargar la clase WatchlistView,
     *  que puede pasar ANTES de que main() fije I18n.IDIOMA, y saldrían siempre en español aunque la app esté
     *  en inglés. Al ser de instancia, se calculan al construir la Watchlist (new WatchlistView(...) en
     *  SpoilerFreeRecs), que ya ocurre con el idioma fijado. Bug resuelto en fase 4: ver docs/DEUDA.md. */
    private final String TOP_PAIS = textoTopPais();
    final String TOP_CLAN = textoTopClan();
    /** Los textos de ★ Top país / ★ Top clan en el idioma activo AHORA (se evalúan al llamar, no al cargar la clase):
     *  los comparten el combo y el menú «Abrir en» (MenuConfiguracion), para no repetirlos. */
    public static String textoTopPais() { return t("\u2605 Top pa\u00eds", "\u2605 Country top"); }
    public static String textoTopClan() { return t("\u2605 Top clan", "\u2605 Clan top"); }

    /** TODOS los países ISO con su nombre en el idioma de la app — Bulgaria
     *  incluida y sin listas que mantener a mano. */
    private static PaisItem[] catalogoPaises() {
        Locale loc = Locale.forLanguageTag(IDIOMA);
        List<PaisItem> out = new ArrayList<>();
        for (String code : Locale.getISOCountries()) {
            String nombre = Locale.of("", code).getDisplayCountry(loc);
            if (nombre == null || nombre.isBlank() || nombre.equals(code)) nombre = code;
            out.add(new PaisItem(nombre, code.toLowerCase()));
        }
        java.text.Collator col = java.text.Collator.getInstance(loc);
        out.sort((a, b) -> col.compare(a.nombre(), b.nombre()));
        return out.toArray(PaisItem[]::new);
    }
    /** De instancia por el mismo motivo que TOP_PAIS/TOP_CLAN (comentario de arriba): así sale en el idioma
     *  activo de verdad, y no en el que estuviera fijado (o sin fijar) cuando se cargó la clase. */
    public final PaisItem[] PAISES = catalogoPaises();
    JComboBox<PaisItem> paisCombo;
    JTextField buscaPais;
    JPanel parPais;
    boolean rearmandoPais;

    String topFirma = "";
    final Map<Long, Integer> rankTop = new HashMap<>();
    JComboBox<String> topNCombo; boolean rellenandoTopN; JButton addJugBtn;
    JPanel parClan, parClanGuardados; JTextField clanField; JPopupMenu clanPopup;
    JComboBox<String> clanesGuardadosCombo; JButton clanEstrella; boolean rellenandoClanes;

    final List<Player> topLadder = new ArrayList<>();
    final Map<Long, Long> lastTop = new HashMap<>();   // pid -> última partida (ms), del leaderboard
    long topCargado;
    volatile boolean cargandoTop, vigilandoTop;
    final Path topCache;
    final long pausaMs;
    final int perPage;
    /** El «río» de Top ladder usa este throttle propio (independiente del de Twitch, ver ui.DirectosPresenter). */
    long ultimoTopMs;
    boolean avisoTopMostrado;   // el popup del top caído: solo la primera vez por sesión
    final Set<Long> topVerificados = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ----- forma reciente (el estado vive en WatchlistPresenter) -----
    /** ¿Se ve la columna Forma? La consulta playersList.getToolTipText (queda en la ventana: playersList es suyo). */
    public boolean formaVisible() { return presenter.formaVisible; }
    JButton formaBtn, ocultarFormaBtn;

    // ----- campanas -----
    public JToggleButton campanaBtn;   // visible para RegresionCapturas
    private final Map<String, Set<Long>> campanaIds = new java.util.concurrent.ConcurrentHashMap<>();   // id de vista → jugadores vigilados

    // ----- hover card (hoy inerte: se conserva igual) -----
    JWindow hoverCard;
    boolean cardFijada;
    javax.swing.Timer hoverTimer;
    long hoverPid;
    Point hoverPantalla;
    /** ¿Es buen momento para enseñar la hover-card? Nunca sobre diálogos,
     *  menús abiertos o con el ratón ya fuera de la lista. */
    Component hoverAncla;   // componente sobre el que vive la tarjeta flotante (la watchlist por defecto; en Live now, el nick)
    JTextField buscaNick;

    /** Ctrl+F de la ventana: foco en el buscador de nick y selecciona lo que hubiera (mismo comportamiento que
     *  la 1.1, con la misma guarda por si aún no se ha construido el panel). */
    public void enfocarBuscador() {
        if (buscaNick != null) { buscaNick.requestFocusInWindow(); buscaNick.selectAll(); }
    }
    JLabel watchPista1, watchPista2, watchPista3;   // explicación visible de la Watchlist
    /** Las tres etiquetas de pista de la Watchlist: TemaApp las repinta al cambiar de tema (ui.ComponentesTema). */
    public JLabel watchPista1() { return watchPista1; }
    public JLabel watchPista2() { return watchPista2; }
    public JLabel watchPista3() { return watchPista3; }


    // ----- piezas de la vista (1.3): reciben esta fachada y leen/escriben su estado a través de ella -----
    final WatchlistHoverCard tarjetaHover = new WatchlistHoverCard(this);
    final WatchlistMenu menuLista = new WatchlistMenu(this);
    final WatchlistTrabajos trabajos = new WatchlistTrabajos(this);
    final WatchlistDialogos dialogosLista = new WatchlistDialogos(this);
    final WatchlistControles controles = new WatchlistControles(this);
    final WatchlistLista listaVista = new WatchlistLista(this);

    /** Ver ui.WatchlistView.EnlacePartidas y ui.WatchlistView.Anfitrion para el contrato completo. */
    public WatchlistView(Window ventana, ProfileService perfiles,
                          dev.tirador.aoe2radar.service.BusquedaPerfiles busqueda, TopLadderService topLadderService,
                          FormService formaService, Campanas campanas, BarridoVivos barridoVivos, EloSesion eloSesion,
                          MenusJugador menus, DialogosJugador dialogos, Navegacion navegacion,
                          Tareas tareas, EnlacePartidas enlacePartidas, Anfitrion anfitrion,
                          List<Player> todosJugadores, DefaultListModel<Player> playersModel, JList<Player> playersList,
                          Map<Long, Integer> eloWatch, Map<Long, Integer> gamesWatch, Map<Long, String[]> twitchLive,
                          Map<Long, String> aliases, JLabel status, JProgressBar progreso, List<Match> all,
                          JPanel sujetosPanel, Path playersFile, Path topCache, long pausaMs, int perPage) {
        this(ventana, perfiles, busqueda, topLadderService, formaService, campanas, barridoVivos, eloSesion, menus, dialogos,
                navegacion, tareas, enlacePartidas, anfitrion, todosJugadores, playersModel, playersList, eloWatch, gamesWatch,
                twitchLive, aliases, status, progreso, all, sujetosPanel, playersFile, topCache, pausaMs, perPage,
                dev.tirador.aoe2radar.util.Config::leerConfig, dev.tirador.aoe2radar.util.Config::guardarConfig);
    }

    /** Como el público, con la config inyectada (los tests: un mapa en memoria; ver leerCfg). */
    WatchlistView(Window ventana, ProfileService perfiles,
                  dev.tirador.aoe2radar.service.BusquedaPerfiles busqueda, TopLadderService topLadderService,
                  FormService formaService, Campanas campanas, BarridoVivos barridoVivos, EloSesion eloSesion,
                  MenusJugador menus, DialogosJugador dialogos, Navegacion navegacion,
                  Tareas tareas, EnlacePartidas enlacePartidas, Anfitrion anfitrion,
                  List<Player> todosJugadores, DefaultListModel<Player> playersModel, JList<Player> playersList,
                  Map<Long, Integer> eloWatch, Map<Long, Integer> gamesWatch, Map<Long, String[]> twitchLive,
                  Map<Long, String> aliases, JLabel status, JProgressBar progreso, List<Match> all,
                  JPanel sujetosPanel, Path playersFile, Path topCache, long pausaMs, int perPage,
                  BiFunction<String, String, String> leerCfg, BiConsumer<String, String> guardarCfg) {
        this.leerCfg = leerCfg; this.guardarCfg = guardarCfg;
        this.ventana = ventana; this.perfiles = perfiles; this.busqueda = busqueda; this.topLadderService = topLadderService;
        this.formaService = formaService; this.campanas = campanas; this.barridoVivos = barridoVivos; this.eloSesion = eloSesion;
        this.menus = menus; this.dialogos = dialogos; this.navegacion = navegacion;
        this.tareas = tareas; this.enlacePartidas = enlacePartidas; this.anfitrion = anfitrion;
        this.todosJugadores = todosJugadores; this.playersModel = playersModel; this.playersList = playersList;
        this.eloWatch = eloWatch; this.gamesWatch = gamesWatch; this.twitchLive = twitchLive; this.aliases = aliases;
        this.status = status; this.progreso = progreso; this.all = all;
        this.sujetosPanel = sujetosPanel;
        this.pausaMs = pausaMs; this.perPage = perPage;
        this.presenter = new WatchlistPresenter(this, todosJugadores, playersFile, TOP_PAIS, TOP_CLAN, PAISES, leerCfg, guardarCfg,
                formaService, gamesWatch, perfiles, VIVO, eloWatch, topLadder);
        this.topCache = topCache;
        construirPanel();
    }

    /** El panel de la pestaña, para el split principal de la ventana. */
    public JPanel panel() { return watchPanel; }

    // ===== Construcción del panel (idéntica a construirWatchlist de la 1.1) ============================

    // El panel izquierdo: la Watchlist (buscador, grupos, pais/clan, la lista de
    // jugadores seguidos con su ELO y su «en vivo», y los botones para quitarlos).
    // Devuelve el panel para que el constructor lo pase al split principal.
    private void construirPanel() {
        listaVista.construirLista();
        JPanel row2 = new JPanel(new GridLayout(1, 1, 4, 0));
        row2.add(delBtn);   // «Quitar filtro» sobra: la selección se suelta clicando en el fondo
        JPanel leftButtons = new JPanel(new GridLayout(1, 1, 0, 4));
        leftButtons.add(row2);   // «Buscar/Añadir jugador…» viven ahora en el buscador de arriba
        watchPanel = new JPanel(new BorderLayout(0, 6));
        JPanel left = watchPanel;
        tituloWatch = BorderFactory.createTitledBorder("Watchlist");
        tituloWatch.setTitleFont(tituloWatch.getTitleFont().deriveFont(Font.BOLD, 13f));
        left.setBorder(tituloWatch);
        controles.construir(left);
        listaVista.construirCentro(left);
        left.add(leftButtons, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(280, 0));
    }

    /** Grupos donde se puede fichar o mover a alguien (ver WatchlistPresenter.gruposDisponibles). */
    public Set<String> gruposDisponibles() { return presenter.gruposDisponibles(); }

    public boolean enZonaForma(Point p) {
        int wL = playersList.getWidth() - 22, elo = anchoCeldaElo(wL);
        return presenter.formaVisible && p.x >= wL - elo - 72 && p.x <= wL - elo;
    }

    public String vistaActualId() { return presenter.vistaActualId(); }

    /** Celda de ELO fija: siempre con sitio para «elo \u21A5altElo». */
    static int anchoCeldaElo(int wPanel) { return 86; }

    public boolean modoTop() { return presenter.modoTop(); }
    public boolean modoClan() { return presenter.modoClan(); }
    public boolean modoPais() { return presenter.modoPais(); }
    public String paisSel() { return presenter.paisSel(); }

    /** ¿El top ladder/país/clan está vacío? Lo consulta el temporizador de vigilancia (cromo, arrancar()). */
    public boolean topLadderVacio() { return topLadder.isEmpty(); }
    /** Cuándo se cargó el top por última vez (epoch ms); 0 = nunca. */
    public long topCargadoMs() { return topCargado; }
    /** Fuerza la próxima carga del top a ir a la red (config Top N cambiado desde el menú). */
    public void forzarRecargaTop() { topCargado = 0; cargarTopLadder(true); }
    /** Copia del top actual (ladder/país/clan): la consulta sincronizarSocket (cromo) para vigilar también el top. */
    public List<Player> topLadderSnapshot() { synchronized (topLadder) { return new ArrayList<>(topLadder); } }

    // ----- Forma reciente (±ELO en una ventana de horas; 1v1 ranked) --------------

    /** Un ELO fresco (API, leaderboard): ver WatchlistPresenter.ponerEloFresco (orden de escritura del F5). */
    public void ponerEloFresco(long pid, int elo) { presenter.ponerEloFresco(pid, elo); }

    public void apagarForma() { trabajos.apagarForma(); }
    void cargarForma(List<Player> objetivo, int horas, Runnable alTerminar) { trabajos.cargarForma(objetivo, horas, alTerminar); }
    List<Player> objetivoForma() { return trabajos.objetivoForma(); }
    public void actualizarTextoForma() { trabajos.actualizarTextoForma(); }

    public String tipForma(long pid) { return presenter.tipForma(pid); }

    // ===== Campanas: aviso cuando alguien de una vista marcada entra en partida =========================

    public boolean campanaContiene(long pid) { return campanas.campanaContiene(pid, campanaIds); }

    /** Recalcula (en segundo plano, service.Campanas) los jugadores de cada vista con campana y los mete en el socket. Cada 15 min para tops, pais y clan. */
    public void refrescarCampanas() {
        Set<String> s = campanas.campanas();
        if (s.isEmpty()) {
            campanaIds.clear();
            // F11 de la 1.3: al apagar la última campana, el socket también deja de vigilar esa lista (antes solo
            // se vaciaba campanaIds y el socket seguía con los ids viejos). Conjunto MUTABLE: el anfitrión le suma
            // el top de Live now, si está abierto.
            anfitrion.actualizarSocketExtra(new HashSet<>());
            SwingUtilities.invokeLater(enlacePartidas::sincronizarSocket);
            return;
        }
        // Copia de todosJugadores AQUÍ, en el EDT (refrescarCampanas siempre se llama desde él: botón de campana,
        // Timer de Swing, arranque): mismo riesgo que la fila 106 si el hilo de fondo recorriera la lista de
        // verdad mientras el EDT la muta (altas, bajas, rebuildGrupos...).
        List<Player> jugadoresAhora = new ArrayList<>(todosJugadores);
        new Thread(() -> {
            Map<String, Set<Long>> nuevo = campanas.calcularCampanaIds(s, jugadoresAhora);
            // fila 107 de DEUDA: retainAll+putAll en vez de clear+putAll, para que campanaIds nunca quede vacio
            // a medias mientras otro hilo (el socket) lo lee con tocaAvisar/campanaContiene.
            campanaIds.keySet().retainAll(nuevo.keySet());
            campanaIds.putAll(nuevo);
            Set<Long> todos = new HashSet<>(); for (Set<Long> x : nuevo.values()) todos.addAll(x);
            anfitrion.actualizarSocketExtra(todos);
            SwingUtilities.invokeLater(enlacePartidas::sincronizarSocket);
        }, "campanas").start();
    }

    /** «Mi partida»: si el que entra en partida soy yo (mi_pid), aviso con el rival (bandera, ELO, civ) y accesos a su perfil y al cara a cara. */
    public void avisarMiPartida(long pid, Match m) {
        if (!campanas.tocaAvisarMiPartida(pid, m)) return;
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
        SwingUtilities.invokeLater(() -> {
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
        if (!campanas.tocaAvisar(pid, m, campanaIds)) return;
        SwingUtilities.invokeLater(() -> {
            String nombre = anfitrion.nombreVisible(pid, anfitrion.ahoraNombre(pid).equals(String.valueOf(pid)) ? nombreDe(pid) : anfitrion.ahoraNombre(pid));
            String resumen = enlacePartidas.resumenVivo(m, pid);
            String texto = "\u25CF " + nombre + t(" ha empezado una partida", " started a game") + (resumen != null ? " \u00B7 " + resumen : "");
            anfitrion.mostrarToast(texto, m.id);   // solo dentro de la app: nada de notificaciones de Windows
        });
    }

    private String nombreDe(long pid) { for (Player p : todosJugadores) if (p.id() == pid) return p.name(); for (Player p : topLadder) if (p.id() == pid) return p.name(); return String.valueOf(pid); }

    // ===== «★ Top clan» =================================================================================

    public List<String> clanesGuardados() { return presenter.clanesGuardados(); }
    /** El tag de clan escrito ahora mismo en el campo (Top clan); "" si no hay campo o está vacío. */
    public String clanBuscado() { return presenter.clanBuscado(); }

    @Override public void cargarTopClan() { trabajos.cargarTopClan(); }
    public void cargarTopLadder(boolean forzar) { trabajos.cargarTopLadder(forzar); }
    public void vigilarTop() { trabajos.vigilarTop(); }


    // ===== Grupos ========================================================================================

    /** El grupo de usuario elegido; null = «Todos» o una vista ★ (ver WatchlistPresenter.grupoActivo). */
    public String grupoActivo() { return presenter.grupoActivo(); }

    /** Grupos creados por el usuario (existen aunque estén vacíos). */
    public Set<String> gruposConfig() { return presenter.gruposConfig(); }

    public void moverJugador(Player p, String grupo) { presenter.moverJugador(p, grupo); }

    void gestionarGrupos() { dialogosLista.gestionarGrupos(); }

    public String grupoDestino() { return presenter.grupoDestino(); }

    /** Ficha a uno desde un top y, como el buscador, ofrece sus cuentas vinculadas. */
    public void ficharDesdeTop(Player p, String g) { presenter.ficharDesdeTop(p, g); }

    public void ficharVarios(List<Player> lista, String g) { dialogosLista.ficharVarios(lista, g); }

    /**
     * ¿"nombre" es un grupo de verdad (General, uno con jugadores dentro o uno registrado en config), no un
     * texto que solo coincide por casualidad con "Todos"/"All"? Lo usa Campanas (inyectado desde
     * SpoilerFreeRecs) para no traducir la campana de un grupo que el usuario llamó, por ejemplo, "All": esa
     * campana es la de ESE grupo, no la del pseudogrupo "todos los jugadores".
     */
    public boolean esGrupoDeUsuario(String nombre) { return presenter.esGrupoDeUsuario(nombre); }


    public String elegirGrupoDialog(String nombreJugador) { return dialogosLista.elegirGrupoDialog(nombreJugador); }

    /** Rellena el combo con «Todos», los grupos existentes y «+ Nuevo grupo…»,
     *  conservando la selección guardada (qué item es: WatchlistPresenter.indiceGuardado). */
    public void rebuildGrupos() {
        var listener = grupoCombo.getActionListeners();
        for (var l : listener) grupoCombo.removeActionListener(l);
        String guardado = presenter.grupoGuardado();
        Set<String> grupos = presenter.calcularGrupos();   // un grupo ya nunca se esfuma al vaciarse
        grupoCombo.removeAllItems();
        for (String fijo : presenter.itemsFijos()) grupoCombo.addItem(fijo);
        for (String g : grupos) grupoCombo.addItem(g);
        grupoCombo.setSelectedItem(t("Todos", "All"));
        int idx = presenter.indiceGuardado(itemsCombo(), guardado);
        if (idx >= 0) grupoCombo.setSelectedIndex(idx);
        for (var l : listener) grupoCombo.addActionListener(l);
    }

    /** El listener del combo de vistas (el que rebuildGrupos quita y vuelve a poner): lo decide el presentador. */
    void onGrupoElegido() { presenter.grupoElegido(); }

    // ===== «Abrir en» (Configuración): la vista con la que abre la app =================================

    public List<String> gruposDelCombo() { return presenter.gruposDelCombo(); }
    public VistaInicial.Eleccion eleccionInicial() { return presenter.eleccionInicial(); }

    /** Al arrancar: abre la Watchlist en la vista de «Abrir en» (por defecto ★ Top ladder, como siempre). Elige el
     *  item del combo CON sus listeners: onGrupoElegido carga el top, el país o el clan, o barre el grupo, y guarda
     *  grupo_activo. Antes el arranque ponía siempre ★ Top ladder y pisaba grupo_activo (F3 de la revisión 1.3).
     *  Devuelve la elección aplicada: el arranque decide con ella si toca «Buscar al abrir». En el EDT. */
    public VistaInicial.Eleccion abrirVistaInicial() {
        VistaInicial.Eleccion e = eleccionInicial();
        switch (e.tipo()) {
            case PAIS -> {
                for (PaisItem pi : PAISES) if (pi.code().equals(e.valor())) presenter.paisActual = pi;
                if (paisCombo != null) {   // que el selector enseñe ese país, sin volver a cargar (rearmandoPais)
                    rearmandoPais = true;
                    try { paisCombo.setSelectedItem(presenter.paisActual); } finally { rearmandoPais = false; }
                }
                grupoCombo.setSelectedItem(TOP_PAIS);
            }
            case CLAN -> {
                if (clanField != null) { clanField.setText(e.valor()); clanPopup.setVisible(false); }
                grupoCombo.setSelectedItem(TOP_CLAN);
            }
            case GRUPO -> grupoCombo.setSelectedItem(e.valor());
            case TODOS -> grupoCombo.setSelectedItem(t("Todos", "All"));
            default -> grupoCombo.setSelectedItem(TOP_LADDER);
        }
        return e;
    }

    // ===== WatchlistPresenter.Pantalla ===================================================================

    @Override public String grupoSeleccionado() { Object g = grupoCombo.getSelectedItem(); return g == null ? null : String.valueOf(g); }
    @Override public List<String> itemsCombo() {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < grupoCombo.getItemCount(); i++) items.add(grupoCombo.getItemAt(i));
        return items;
    }
    @Override public String clanEscrito() { return clanField == null ? null : clanField.getText(); }
    @Override public void rellenarClanSiVacio() { if (clanField != null && clanField.getText().isBlank()) clanField.setText(leerConfig("clan_tag", "")); }
    @Override public void refrescarFiltro() { aplicarFiltroGrupo(); }
    @Override public void indicadoresVivos() { actualizarIndicadoresVivos(); }
    @Override public void actualizarBotonesModo() { controles.actualizarBotonesModo(); }
    @Override public void estado(String texto) { status.setText(texto); }
    @Override public void reconstruirGrupos() { rebuildGrupos(); }
    @Override public boolean soloVivosMarcado() { return soloVivosBtn != null && soloVivosBtn.isSelected(); }
    @Override public boolean mostrarEloWatch() { return mostrarEloWatch; }
    @Override public EnlacePartidas enlace() { return enlacePartidas; }

    void crearGrupoDialog() { dialogosLista.crearGrupoDialog(); }

    void refrescarCabeceraOrden() { listaVista.refrescarCabeceraOrden(); }

    /** Reconstruye la lista visible con el grupo activo («Todos» = todos). */
    public void aplicarFiltroGrupo() {
        List<Player> vis = presenter.filasVisibles();   // qué filas y en qué orden: el presentador
        Player invitadoActual = enlacePartidas.invitado();
        if (invitadoActual != null && containsPlayerId(invitadoActual.id())) { enlacePartidas.limpiarInvitado(); invitadoActual = null; }   // fichado por cualquier vía: deja de flotar
        if (invitadoActual != null && !vistaActualId().equals(enlacePartidas.vistaDelInvitado())) { enlacePartidas.limpiarInvitado(); invitadoActual = null; }   // cambiar de vista (grupo o país) despide al invitado
        if (enlacePartidas.sujetosPanelVisible() && !vistaActualId().equals(enlacePartidas.vistaDeSujetos())) {
            enlacePartidas.limpiarSujetos();
            enlacePartidas.refrescarSujetos(List.of(), false);   // la cabecera refleja la tabla; otra vista, otra historia
            enlacePartidas.taparResultados();
            apagarForma();
        }
        if (invitadoActual != null) {
            final long invId = invitadoActual.id();
            vis.removeIf(px -> px.id() == invId);   // el invitado vive en la cabecera fija, no como fila
        }
        boolean igual = vis.size() == playersModel.size();
        if (igual)
            for (int i = 0; i < vis.size(); i++)
                if (vis.get(i).id() != playersModel.get(i).id()) { igual = false; break; }
        if (igual) return;   // nada que redibujar: la selección del usuario se conserva
        Set<Long> selPrevia = new HashSet<>();
        for (Player p : playersList.getSelectedValuesList()) selPrevia.add(p.id());
        playersModel.clear();
        for (Player p : vis) playersModel.addElement(p);
        enlacePartidas.actualizarTextoBuscar();
        if (!selPrevia.isEmpty()) {   // la selección sobrevive al reordenado (ELOs frescos, vivos…)
            List<Integer> idxs = new ArrayList<>();
            for (int i = 0; i < playersModel.size(); i++)
                if (selPrevia.contains(playersModel.get(i).id())) idxs.add(i);
            int[] arr = idxs.stream().mapToInt(Integer::intValue).toArray();
            if (arr.length > 0) playersList.setSelectedIndices(arr);
        }
        enlacePartidas.sincronizarSocket();   // la vista manda: el socket vigila exactamente lo que se ve
    }

    // ===== Ficha / grupos: infraestructura de vigilancia y refresco =====================================

    public String grupoDeJugador(long id) { return presenter.grupoDeJugador(id); }

    public boolean containsPlayerId(long id) { return presenter.containsPlayerId(id); }

    /** Cuentas vinculadas y familias: WatchlistPresenter (conFamilias, tipCuentaVinculada, sanear, marcarVinculo). */
    public List<Player> conFamilias(List<Player> base) { return presenter.conFamilias(base); }
    public String tipCuentaVinculada(Match m) { return presenter.tipCuentaVinculada(m); }
    public void sanearVinculosHuerfanos() { presenter.sanearVinculosHuerfanos(); }
    public void marcarVinculo(Set<Long> ids) { presenter.marcarVinculo(ids); }

    public void refrescarWatchlist() { trabajos.refrescarWatchlist(); }
    public void vigilarVivos() { trabajos.vigilarVivos(); }


    /** Repinta lista, combo de grupos y el título («Watchlist — N en directo»). */
    public void refrescarAlturasWatch() {
        playersList.setFixedCellHeight(0);
        playersList.setFixedCellHeight(-1);   // fuerza re-medir alturas (sublíneas que aparecen o mueren)
    }

    public void actualizarIndicadoresVivos() {
        refrescarAlturasWatch();   // las sublíneas nacen y mueren con los vivos, en toda vista
        playersList.repaint();
        grupoCombo.repaint();
        int nVivos = presenter.vivosEnLista();
        int nAmbito = presenter.vivosEnAmbito();
        if (soloVivosBtn != null) {
            soloVivosBtn.setText("\u25CF " + t("Jugando", "Playing") + (nAmbito > 0 ? " (" + nAmbito + ")" : ""));
            soloVivosBtn.setToolTipText(null);   // sin tooltip: el chip se explica solo
            if (resumenWatch != null) {
                int totalAmbito = 0;
                for (int i = 0; i < playersModel.size(); i++) totalAmbito++;
                resumenWatch.setText(totalAmbito + t(" jugadores", " players") + " \u00B7 " + nAmbito + t(" jugando", " playing"));
            }
        }
        tituloWatch.setTitle(nVivos == 0 ? "Watchlist"
                : "Watchlist \u2014 " + nVivos + t(" jugando", " playing"));
        if (soloVivosBtn != null && soloVivosBtn.isSelected()) aplicarFiltroGrupo();
        if (watchPanel != null) watchPanel.repaint();
    }

    // ===== Alta de jugadores =============================================================================

    @Override public void ofrecerVinculadasTrasAlta(long profileId, String nombre, String grupo) { dialogosLista.ofrecerVinculadasTrasAlta(profileId, nombre, grupo); }
    public void jugadorElegido(long pid, String nombre) { dialogosLista.jugadorElegido(pid, nombre); }

    /** ¿La respuesta a la búsqueda «q» ya no sirve porque el usuario siguió escribiendo? (el texto actual del
     *  campo ya no es «q»). Mismo criterio que el buscador de nick de la 1.1: comparar contra el texto YA
     *  recortado (trim), para que espacios al final no cuenten como «ha cambiado». */
    static boolean sugerenciaCaducada(String q, String textoActualDelCampo) {
        return !q.equals(textoActualDelCampo.trim());
    }

    /** El jugador de la fila elegida en el combo de resultados de addPlayerDialog, por ÍNDICE, no por texto:
     *  dos jugadores distintos pueden compartir el mismo texto de fila (nombre + país + ELO iguales, sobre todo
     *  en las filas que vienen del índice local, que no llevan el id pegado al texto como sí hacen las de la
     *  API). Buscar "cuál r[2] es igual al texto seleccionado" se queda siempre con el primero que empate y,
     *  peor aún, en la rama de «Añadir al grupo» los añadía a TODOS los que empataran. null si el índice no
     *  corresponde a ninguna fila (nada elegido). */
    static Player jugadorDeFila(List<String[]> res, int indiceSeleccionado, String grupo) {
        if (indiceSeleccionado < 0 || indiceSeleccionado >= res.size()) return null;
        String[] r = res.get(indiceSeleccionado);
        return new Player(Long.parseLong(r[0]), r[1], grupo);
    }

    public void addPlayerDialog(boolean soloVer, String nickInicial) { dialogosLista.addPlayerDialog(soloVer, nickInicial); }

    // ===== Persistencia ===================================================================================

    // players.txt: WatchlistPresenter.cargarJugadores/guardarJugadores (E/S de disco en el EDT, deuda de la 1.1).
    public void loadPlayers() { presenter.cargarJugadores(); }

    public void savePlayers() { presenter.guardarJugadores(); }

    // ===== Menú contextual =================================================================================

    public void menuContextualWatchlist(Player p, MouseEvent e) { menuLista.menuContextualWatchlist(p, e); }

    // ===== Tarjeta de perfil flotante (hoy inerte: el hover-timer nunca dispara) ==========================

    boolean hoverProcede() { return tarjetaHover.hoverProcede(); }
    public void ocultarHoverCard() { tarjetaHover.ocultarHoverCard(); }
    public void ocultarHoverCard(boolean forzar) { tarjetaHover.ocultarHoverCard(forzar); }
    void mostrarPerfilCard(long pid, String nombre, Point enPantalla) { tarjetaHover.mostrarPerfilCard(pid, nombre, enPantalla); }
}
