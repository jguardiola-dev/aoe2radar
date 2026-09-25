package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.service.Campanas;
import dev.tirador.aoe2radar.service.EloSesion;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.Familias;
import dev.tirador.aoe2radar.service.FiltroLista;
import dev.tirador.aoe2radar.service.FormService;
import dev.tirador.aoe2radar.service.ListaSeguidos;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.TopLadderService;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolTip;
import javax.swing.JViewport;
import javax.swing.JWindow;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static dev.tirador.aoe2radar.ui.Componentes.colorVivoHex;
import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
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
public final class WatchlistView {

    /** Un grupo especial vale para el resto de la app; General es de service.ListaSeguidos. */
    public static final String GRUPO_GENERAL = "General";

    /** Lo que la Watchlist pide a Partidas (todavía dentro de SpoilerFreeRecs en esta rama). */
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
        /** api.CompanionApi, que ui no puede importar: búsqueda de perfiles por nick (addPlayerDialog). */
        List<dev.tirador.aoe2radar.model.PerfilEncontrado> buscarPerfilesApi(String q) throws Exception;
        /** api.CompanionApi.perfil/pagina, solo para la tarjeta de hover (hoy inerte). */
        Perfil perfilApi(long pid) throws Exception;
        dev.tirador.aoe2radar.model.PaginaPartidas paginaApi(long pid, int pagina, int porPagina) throws Exception;
        /** ui.DirectosView y ui.MiPartidaPanel se construyen DESPUÉS que la Watchlist (construirCentro): no se
         *  pueden inyectar por constructor sin capturar null. La ventana los llama por su nombre cuando ya existen. */
        void reiniciarThrottleDirectos();
        void vigilarTwitchDirectos();
        void mostrarSuperposicion(String texto, List<Object[]> fichas, int ms);
        /** liveNow.socketExtra: además de las campanas, Live now (si está abierto) vigila su propio top 250. */
        void actualizarSocketExtra(Set<Long> ids);
        /** liveNow.ahoraNombre(pid): el nombre que Live now ya conoce de un jugador en curso, o el propio pid si no
         *  lo conoce (sin guarda de null, como la base: liveNow siempre existe cuando se llama). */
        String ahoraNombre(long pid);
    }

    // ----- colaboradores inyectados (mismas instancias que la ventana) -----
    private final Window ventana;
    private final ProfileService perfiles;
    private final dev.tirador.aoe2radar.service.BusquedaPerfiles busqueda;
    private final TopLadderService topLadderService;
    private final FormService formaService;
    private final Campanas campanas;
    private final BarridoVivos barridoVivos;
    private final EloSesion eloSesion;
    private final MenusJugador menus;
    private final DialogosJugador dialogos;
    private final Navegacion navegacion;
    private final Tareas tareas;
    private final EnlacePartidas enlacePartidas;
    private final Anfitrion anfitrion;

    // ----- estado compartido con la ventana (creado allí, inyectado aquí: misma instancia) -----
    private final List<Player> todosJugadores;
    private final DefaultListModel<Player> playersModel;
    private final JList<Player> playersList;
    private final Map<Long, Integer> eloWatch;
    private final Map<Long, Integer> gamesWatch;
    private final Map<Long, String[]> twitchLive;
    private final Map<Long, String> aliases;
    private final JLabel status;
    private final JProgressBar progreso;
    private final List<Match> all;
    public JToggleButton soloVivosBtn;   // visible: fetchMatches (Partidas) lo consulta; se crea en construirPanel(), como la 1.1
    private final JPanel sujetosPanel;   // de Partidas (refrescarSujetos vive en la ventana); solo lo insertamos en el layout

    private static final EstadoVivo VIVO = EstadoVivo.SISTEMA;

    // ----- propios -----
    /** Persistencia, grupos y altas/bajas/movimientos de la watchlist: ver service.ListaSeguidos. */
    private final ListaSeguidos listaSeguidos;
    private final Set<Long> watchBarridos = new HashSet<>();   // seguidos ya consultados en este arranque
    public final JComboBox<String> grupoCombo = new JComboBox<>();   // visible para RegresionCapturas
    public boolean mostrarEloWatch = Boolean.parseBoolean(leerConfig("elo_watchlist", "true"));   // visible: el ítem «Mostrar ELO en la Watchlist» del menú Configuración (cromo) lo lee/escribe
    private boolean vigilando = false;
    private JPanel watchPanel;
    public JPanel norteWatchRef;   // visible: la ventana repinta al restaurar el ancho del split (mostrarDirectos/cerrarTechTree)
    public javax.swing.border.TitledBorder tituloWatch;   // visible para RegresionCapturas
    private JButton delBtn;
    public JLabel cabLabel;   // visible: lo consulta esFondoDeseleccionable (cromo)
    public JLabel resumenWatch;   // visible para RegresionCapturas

    public static final String TOP_LADDER = "\u2605 Top ladder";
    private static final String TOP_PAIS = t("\u2605 Top pa\u00eds", "\u2605 Country top");
    private static final String TOP_CLAN = t("\u2605 Top clan", "\u2605 Clan top");

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
    public static final PaisItem[] PAISES = catalogoPaises();
    private PaisItem paisActual;
    private JComboBox<PaisItem> paisCombo;
    private JTextField buscaPais;
    private JPanel parPais;
    private boolean rearmandoPais;
    private List<PaisItem> catalogoOrdenado = List.of();

    private String topFirma = "";
    private final Map<Long, Integer> rankTop = new HashMap<>();
    private JComboBox<String> topNCombo; private boolean rellenandoTopN; private JButton addJugBtn;
    private JPanel parClan, parClanGuardados; private JTextField clanField; private JPopupMenu clanPopup;
    private JComboBox<String> clanesGuardadosCombo; private JButton clanEstrella; private boolean rellenandoClanes;

    private final FiltroLista filtroLista = new FiltroLista(VIVO);
    private final Set<Long> vinculosExpandidos = new HashSet<>();
    private final Map<Long, Character> marcaFila = new HashMap<>();
    private final Map<Long, Boolean> vivoFamilia = new HashMap<>();
    private final List<Player> topLadder = new ArrayList<>();
    private final Map<Long, Long> lastTop = new HashMap<>();
    private long topCargado;
    private volatile boolean cargandoTop, vigilandoTop;
    private final Path topCache;
    private final long pausaMs;
    private final int perPage;
    /** El «río» de Top ladder usa este throttle propio (independiente del de Twitch, ver ui.DirectosPresenter). */
    private long ultimoTopMs;
    private boolean avisoTopMostrado;   // el popup del top caído: solo la primera vez por sesión
    private final Set<Long> topVerificados = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ----- forma reciente -----
    private static final Map<Long, Integer> TOP_STREAK = new java.util.concurrent.ConcurrentHashMap<>();   // racha del ladder (+3 / -2)
    private static final Map<Long, int[]> TOP_LAST10 = new java.util.concurrent.ConcurrentHashMap<>();   // {ganadas, perdidas} de las últimas 10
    private final Map<Long, Forma> forma24 = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Forma> forma7d = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Long> formaTs24 = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Long> formaTs7d = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean formaVisible;   // el chip: nace apagado, no se recuerda
    /** ¿Se ve la columna Forma? La consulta playersList.getToolTipText (queda en la ventana: playersList es suyo). */
    public boolean formaVisible() { return formaVisible; }
    private volatile int ventanaForma = 24;   // 24 h o 7 d (selector junto al chip)
    private JButton formaBtn, ocultarFormaBtn;

    // ----- campanas -----
    public JToggleButton campanaBtn;   // visible para RegresionCapturas
    private final Map<String, Set<Long>> campanaIds = new java.util.concurrent.ConcurrentHashMap<>();   // id de vista → jugadores vigilados

    // ----- hover card (hoy inerte: se conserva igual) -----
    private JWindow hoverCard;
    private boolean cardFijada;
    private javax.swing.Timer hoverTimer;
    private long hoverPid;
    private Point hoverPantalla;
    /** ¿Es buen momento para enseñar la hover-card? Nunca sobre diálogos,
     *  menús abiertos o con el ratón ya fuera de la lista. */
    private Component hoverAncla;   // componente sobre el que vive la tarjeta flotante (la watchlist por defecto; en Live now, el nick)
    private JTextField buscaNick;

    /** Ctrl+F de la ventana: foco en el buscador de nick y selecciona lo que hubiera (mismo comportamiento que
     *  la 1.1, con la misma guarda por si aún no se ha construido el panel). */
    public void enfocarBuscador() {
        if (buscaNick != null) { buscaNick.requestFocusInWindow(); buscaNick.selectAll(); }
    }
    private JLabel watchPista1, watchPista2, watchPista3;   // explicación visible de la Watchlist
    /** Las tres etiquetas de pista de la Watchlist: TemaApp las repinta al cambiar de tema (ui.ComponentesTema). */
    public JLabel watchPista1() { return watchPista1; }
    public JLabel watchPista2() { return watchPista2; }
    public JLabel watchPista3() { return watchPista3; }

    /** Familias: vínculos entre cuentas del mismo jugador (ver service.Familias). Campo de instancia, junto al
     *  código que lo usa (no junto a COMPANION/LIVE/SERVICIO_PERFIL: es un servicio sin red). */
    private final Familias familiaSvc = new Familias();

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
        this.ventana = ventana; this.perfiles = perfiles; this.busqueda = busqueda; this.topLadderService = topLadderService;
        this.formaService = formaService; this.campanas = campanas; this.barridoVivos = barridoVivos; this.eloSesion = eloSesion;
        this.menus = menus; this.dialogos = dialogos; this.navegacion = navegacion;
        this.tareas = tareas; this.enlacePartidas = enlacePartidas; this.anfitrion = anfitrion;
        this.todosJugadores = todosJugadores; this.playersModel = playersModel; this.playersList = playersList;
        this.eloWatch = eloWatch; this.gamesWatch = gamesWatch; this.twitchLive = twitchLive; this.aliases = aliases;
        this.status = status; this.progreso = progreso; this.all = all;
        this.sujetosPanel = sujetosPanel;
        this.pausaMs = pausaMs; this.perPage = perPage;
        this.listaSeguidos = new ListaSeguidos(playersFile, GRUPO_GENERAL, dev.tirador.aoe2radar.util.Config::leerConfig, dev.tirador.aoe2radar.util.Config::guardarConfig);
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
        // Panel izquierdo: jugadores seguidos (la selección filtra la tabla)
        playersList.setVisibleRowCount(12);
        playersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { enlacePartidas.applyFilters(); anfitrion.seleccionCambiada(); }   // con Ratings o Perfil abiertos, la selección se refleja allí
        });
        delBtn = new JButton(t("Quitar del grupo", "Remove from group"));

        playersList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isMiddleMouseButton(e)) return;
                int idx = playersList.locationToIndex(e.getPoint());
                if (idx < 0 || !playersList.getCellBounds(idx, idx).contains(e.getPoint())) return;
                Player p = playersModel.get(idx);
                navegacion.abrirPerfilEnPestana(p.id(), anfitrion.nombreVisible(p.id(), p.name()));
                e.consume();
            }
        });
        playersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Player p) {
                    char marca = marcaFila.getOrDefault(p.id(), ' ');
                    boolean vivo = VIVO.jugando(p.id())
                            || (marca != 'H' && vivoFamilia.getOrDefault(p.id(), false));
                    Integer elo = eloWatch.get(p.id());
                    Integer rank = modoTop() ? rankTop.get(p.id()) : null;
                    String punto = vivo ? "<font color='#" + colorVivoHex() + "'>\u25CF</font>" : "";
                    String col1 = rank != null ? "<font color='gray'>" + rank + ".</font>"
                            : marca == 'P' ? "\u25B8" : marca == 'E' ? "\u25BE" : "";
                    String eloTxt = (elo != null && mostrarEloWatch) ? String.valueOf(elo) : "";
                    int w0 = Math.max(150, list.getWidth() - 22);
                    FontMetrics fmSub = l.getFontMetrics(l.getFont());   // para truncar sublíneas en píxeles reales
                    int maxChars = Math.max(10, (w0 - 76 - anchoCeldaElo(w0) - (formaVisible ? 72 : 0)) / 7);
                    String nombreVis = anfitrion.nombreVisible(p.id(), p.name());
                    String tip = aliases.containsKey(p.id())
                            ? t("Nick real: ", "Real nick: ") + p.name() : null;
                    String notaP = dialogos.notaDe(p.id());
                    if (notaP != null) tip = (tip == null ? "" : tip + " \u2014 ") + t("Nota: ", "Note: ") + notaP + t(" (clic en \u270E para editar)", " (click \u270E to edit)");

                    if (nombreVis.length() > maxChars) {
                        nombreVis = nombreVis.substring(0, maxChars - 1) + "\u2026";
                        tip = p.name();
                    }
                    String[] st = twitchLive.get(p.id());   // solo para pintar el badge: sin tooltip del título (tapaba la tarjeta)
                    l.setToolTipText(tip);   // ni «jugando ahora» ni el título del stream: el punto, la sublínea y Directos ya lo cuentan
                    Match enCurso = vivo ? VIVO.partida(p.id()) : null;   // una sola lectura: el socket puede soltarla entre dos
                    String mapaVivo = enCurso != null && enCurso.map != null ? enCurso.map : null;
                    if (mapaVivo != null) {   // el mapa cabe en lo que sobra tras el nick; si no cabe, se recorta con «…» y, si ni siquiera hay sitio, no se pinta (el ELO nunca se tapa)
                        int sitio = maxChars - nombreVis.length() - 3;
                        if (sitio < 6) mapaVivo = null;
                        else if (mapaVivo.length() > sitio) mapaVivo = mapaVivo.substring(0, sitio - 1) + "\u2026";
                    }
                    String nick = (vivo ? "<font color='" + (temaOscuroActivo ? "#ffd56a" : "#b06a00") + "'>" + escapeHtml(nombreVis) + "</font>" + (mapaVivo != null ? " <font color='#8a8a8a'>\u00B7</font> <font color='" + (temaOscuroActivo ? "#ffd56a" : "#b06a00") + "'>" + escapeHtml(mapaVivo) + "</font>" : "") : escapeHtml(nombreVis))   // en partida: nick en ámbar y el mapa al lado
                            + (dialogos.notaDe(p.id()) != null ? " <font color='#8a8a8a'>\u270E</font>" : "")
                            + (modoTop() && containsPlayerId(p.id()) ? " <font color='#8a8a8a'>\u2605</font>" : "");   // ya fichado
                    if (marca == 'H')   // hija: sangría fija y un punto menos, legible
                        nick = "&nbsp;&nbsp;&nbsp;<span style='font-size:0.92em'>" + nick + "</span>";
                    l.setBorder(null);   // renderer compartido: se fija SIEMPRE
                    String[] alt = marca == 'H' ? null : mejorAlt(p.id());
                    if (alt != null) {
                        eloTxt = eloTxt + " <font color='#8a8a8a'>\u21A5" + alt[1] + "</font>";
                        tip = (tip == null ? "" : tip + " \u2014 ")
                                + t("Su cuenta ", "Their account ") + alt[0]
                                + t(" está a ", " sits at ") + alt[1]
                                + t(" (clic para ver su perfil)", " (click to view their profile)");
                    }
                    int w = Math.max(150, list.getWidth() - 22);   // el HTML no refluye: ancho explícito
                    l.setText("<html><table width='" + w + "' cellpadding='0' cellspacing='0'><tr>"
                            + "<td width='24' align='right'>" + col1 + "</td>"
                            + "<td width='14' align='center'>" + punto + "</td>"
                            + "<td width='20' align='center'>" + (modoPais() ? "" : Iconos.banderaHtml(anfitrion.paisDe(p.id()))) + "</td>"   // en «Top país» todas serían la misma: solo en el selector
                            + "<td nowrap align='left'>" + nick + "</td>"
                            + "<td width='26' align='center'>" + (st != null
                                    ? "<font color='#9146FF'><b>TW</b></font>" : "") + "</td>"
                            + (formaVisible ? "<td width='72' align='center'>" + celdaForma(p.id()) + "</td>" : "")
                            + "<td width='" + anchoCeldaElo(w0) + "' align='right'>" + eloTxt + "</td></tr>"
                            + "</table>"
                            // Las sublíneas van FUERA de la tabla, como bloques propios: un colspan dentro
                            // cambia el reparto de anchos de las columnas y descoloca puesto/punto/nick
                            + (vivo
                                ? "<div style='margin-left:38px'>" + subtextoVivo(p.id(), fmSub, w0 - 44) + "</div>"
                                : "")
                            + (dialogos.notaDe(p.id()) != null
                                ? "<div style='margin-left:38px'><font color='#b08d57'>\u270E "
                                  + escapeHtml(dev.tirador.aoe2radar.util.Formato.truncarPx(dialogos.notaDe(p.id()), fmSub, w0 - 60)) + "</font></div>"
                                : "")
                            + "</html>");
                }
                return l;
            }
        });
        playersList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  {
                ocultarHoverCard(true);
                if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
                    int idxH = playersList.locationToIndex(e.getPoint());
                    if (idxH >= 0 && playersList.getCellBounds(idxH, idxH).contains(e.getPoint())) {
                        String bajo = textoBajo(idxH, e.getPoint());   // lo que hay pintado bajo el ratón, de verdad
                        Player ph = playersModel.get(idxH);
                        if (enlacePartidas.invitado() != null) {
                            enlacePartidas.limpiarInvitado();
                            enlacePartidas.limpiarSujetos();
                            enlacePartidas.refrescarSujetos(List.of(), false);
                            enlacePartidas.actualizarTextoBuscar();
                        }
                        if (bajo.contains("\u270E") || sobreNota(idxH, e.getPoint())) { dialogos.pedirNota(ph.id(), ph.name()); return; }
                        int wL = playersList.getWidth() - 22, eloW = anchoCeldaElo(wL);
                        int formaW = formaVisible ? 72 : 0;
                        boolean zonaTwFormula = e.getX() >= wL - eloW - formaW - 40 && e.getX() <= wL - eloW - formaW + 8;   // respaldo (la columna de forma desplaza el badge)
                        if (bajo.contains("TW") || (bajo.isEmpty() && zonaTwFormula)) {
                            String[] stT = twitchLive.get(ph.id());
                            if (stT != null) { anfitrion.abrirUrl("https://twitch.tv/" + stT[0]); return; }
                        }
                        if (bajo.contains("\u21A5") && marcaFila.getOrDefault(ph.id(), ' ') != 'H') {
                            String[] altA = mejorAlt(ph.id());
                            if (altA != null && altA.length > 2) {
                                try { navegacion.abrirPerfil(Long.parseLong(altA[2]), altA[0]); return; }
                                catch (NumberFormatException ignored) { }
                            }
                        }
                        if (twitchLive.containsKey(ph.id()) && e.getX() > wL / 2)   // rastro: si un TW no reacciona, el log dice qué vio
                            log("clic sin acción en zona derecha (x=" + e.getX() + " de " + wL + ", bajo='" + bajo.trim() + "')");
                    }
                }
                if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1 && e.getX() < 26) {
                    int idx = playersList.locationToIndex(e.getPoint());
                    if (idx >= 0 && playersList.getCellBounds(idx, idx).contains(e.getPoint())) {
                        Player p = playersModel.get(idx);
                        char m = marcaFila.getOrDefault(p.id(), ' ');
                        if (m == 'P' || m == 'E') {
                            if (!vinculosExpandidos.remove(p.vinculo())) vinculosExpandidos.add(p.vinculo());
                            aplicarFiltroGrupo();
                            return;
                        }
                    }
                }
                maybePopup(e);
            }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int idx = playersList.locationToIndex(e.getPoint());
                if (idx < 0) return;
                Rectangle celda = playersList.getCellBounds(idx, idx);
                if (celda == null || !celda.contains(e.getPoint())) return;   // clic fuera de los nicks
                if (!playersList.isSelectedIndex(idx)) playersList.setSelectedIndex(idx);   // si ya está en la selección, se conserva la múltiple
                Player p = playersModel.get(idx);
                menuContextualWatchlist(p, e);
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown()) {
                    int idx1 = playersList.locationToIndex(e.getPoint());
                    boolean sobreFila = idx1 >= 0 && playersList.getCellBounds(idx1, idx1).contains(e.getPoint());
                    if (!sobreFila) { playersList.clearSelection(); enlacePartidas.actualizarTextoBuscar(); }   // como el Explorador: clic en el vacío = sin selección
                    return;
                }
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown()) {
                    int idx = playersList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        Player p = playersModel.get(idx);
                        String bajo = textoBajo(idx, e.getPoint());
                        if (bajo.contains("TW") || bajo.contains("\u21A5") || bajo.contains("\u270E") || sobreNota(idx, e.getPoint())) return;   // el clic simple ya actuó
                        playersList.setSelectedIndex(idx);
                        enlacePartidas.fetchMatches();
                    }
                }
            }
        });
        delBtn.addActionListener(e -> {
            List<Player> sel = playersList.getSelectedValuesList();
            if (sel.isEmpty()) return;
            StringBuilder nombres = new StringBuilder();
            for (Player p : sel) nombres.append(nombres.isEmpty() ? "" : ", ").append(p.name());
            int r = JOptionPane.showConfirmDialog(ventana,
                    t("Se quitará de la Watchlist a: ", "This will remove from the Watchlist: ") + nombres
                            + t(". ¿Continuar?", ". Continue?"),
                    t("Quitar jugador", "Remove player"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return;
            for (Player p : sel) {
                playersModel.removeElement(p);
                todosJugadores.removeIf(x -> x.id() == p.id());
            }
            savePlayers();
            rebuildGrupos();
            enlacePartidas.applyFilters();
        });
        JPanel row2 = new JPanel(new GridLayout(1, 1, 4, 0));
        row2.add(delBtn);   // «Quitar filtro» sobra: la selección se suelta clicando en el fondo
        JPanel leftButtons = new JPanel(new GridLayout(1, 1, 0, 4));
        leftButtons.add(row2);   // «Buscar/Añadir jugador…» viven ahora en el buscador de arriba
        watchPanel = new JPanel(new BorderLayout(0, 6));
        JPanel left = watchPanel;
        tituloWatch = BorderFactory.createTitledBorder("Watchlist");
        tituloWatch.setTitleFont(tituloWatch.getTitleFont().deriveFont(Font.BOLD, 13f));
        left.setBorder(tituloWatch);
        watchPista1 = new JLabel(t("Amigos, pros o gente del clan.", "Friends, pros or clan mates."));
        watchPista2 = new JLabel(t("Selecciona para filtrar; sin selección, todos.", "Select to filter; none selected = everyone."));
        watchPista3 = new JLabel("\u21A5 " + t("= cuenta vinculada con más ELO", "= linked account with higher ELO"));
        watchPista1.setFont(watchPista1.getFont().deriveFont(Font.PLAIN, 11f));
        watchPista2.setFont(watchPista2.getFont().deriveFont(Font.PLAIN, 11f));
        watchPista3.setFont(watchPista3.getFont().deriveFont(Font.PLAIN, 11f));
        JPanel watchPistas = new JPanel();
        watchPistas.setLayout(new BoxLayout(watchPistas, BoxLayout.Y_AXIS));
        watchPistas.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
        watchPista1.setVisible(false);   // la explicación larga vive en el «?»
        watchPista3.setVisible(false);
        watchPista2.setText(t("Selecciona para filtrar \u00B7 \u21A5 = cuenta más fuerte", "Select to filter \u00B7 \u21A5 = stronger account"));
        JButton ayudaBtn = new JButton("?");
        ayudaBtn.setFocusable(false);
        ayudaBtn.setMargin(new Insets(0, 5, 0, 5));
        ayudaBtn.putClientProperty("JButton.buttonType", "roundRect");
        ayudaBtn.setToolTipText("<html>" + t("<b>Tu watchlist</b>: amigos, pros o gente del clan.<br>Selecciona jugadores para buscar solo sus partidas; sin selección, todo el grupo.<br>\u25CF rojo = jugando ahora (clic derecho \u2192 Espectar) \u00B7 TW = en Twitch (clic = su canal)<br>\u21A5 = cuenta vinculada con más ELO (clic = su perfil) \u00B7 \u270E = tiene nota (clic = editar)<br>Escribe un nick arriba y pulsa Enter para ver las partidas de cualquiera.<br>Clic en un jugador = seleccionarlo; clic en el hueco de la lista = quitar la selección; Ctrl+clic = varios.",
                "<b>Your watchlist</b>: friends, pros or clan mates.<br>Select players to search only their games; with none selected, the whole group.<br>Red \u25CF = playing now (right-click \u2192 Spectate) \u00B7 TW = on Twitch (click = channel)<br>\u21A5 = linked account with higher ELO (click = profile) \u00B7 \u270E = has a note (click = edit)<br>Type a nick above and press Enter to see anyone's games.<br>Click a player to select; click the empty space to deselect; Ctrl+click for several.") + "</html>");
        ayudaBtn.addActionListener(e -> {
            JToolTip tt = ayudaBtn.createToolTip();
            tt.setTipText(ayudaBtn.getToolTipText());
            JPopupMenu pm = new JPopupMenu();
            pm.add(tt);
            pm.show(ayudaBtn, 0, ayudaBtn.getHeight());
        });
        resumenWatch = new JLabel();   // se crea aquí: la línea del «?» lo necesita ya
        resumenWatch.setFont(resumenWatch.getFont().deriveFont(Font.PLAIN, 11f));
        JPanel pistaFila = new JPanel(new BorderLayout(6, 0));
        pistaFila.setOpaque(false);
        watchPista2.setVisible(false);   // su texto vive en el tooltip del «?»
        pistaFila.add(resumenWatch, BorderLayout.CENTER);   // «50 jugadores · 4 en directo»
        pistaFila.add(ayudaBtn, BorderLayout.EAST);
        pistaFila.setAlignmentX(Component.LEFT_ALIGNMENT);
        watchPistas.add(pistaFila);
        JPanel grupoFila = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        grupoFila.setAlignmentX(Component.LEFT_ALIGNMENT);
        grupoCombo.setToolTipText(t("Agrupa tu Watchlist (Amigos, Pros, Clan…). Se busca y se muestra el grupo activo.",
                "Group your Watchlist (Friends, Pros, Clan…). The active group is what gets searched and shown."));
        grupoCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String s = String.valueOf(value);
                boolean esNuevo = s.equals(t("+ Nuevo grupo…", "+ New group…"));
                boolean esTodos = s.equals(t("Todos", "All"));
                boolean esGestion = s.equals(t("Gestionar grupos…", "Manage groups…"));
                if (!esNuevo && !esGestion && grupoTieneVivo(esTodos ? null : s)) {
                    l.setText("<html><font color='#" + colorVivoHex() + "'>\u25CF</font> "
                            + escapeHtml(s) + "</html>");
                }
                return l;
            }
        });
        grupoCombo.addActionListener(e -> onGrupoElegido());
        grupoCombo.setFont(grupoCombo.getFont().deriveFont(Font.BOLD));
        grupoFila.add(grupoCombo);
        soloVivosBtn = new JToggleButton("● " + t("Jugando", "Playing"));
        soloVivosBtn.setToolTipText(t(
                "Detecta partidas EN CURSO de cualquier modo (1v1, TG, lo que sea). Límites: partidas de más de 3 h se consideran colgadas, y las salas personalizadas a veces no aparecen hasta terminar.",
                "Detects games IN PROGRESS of any mode (1v1, TG, anything). Limits: games over 3 h are treated as hung, and custom lobbies sometimes only show up once finished."));
        soloVivosBtn.setToolTipText(t("Muestra solo a los que están jugando ahora, dentro del grupo elegido (con «Todos», de toda la lista)",
                "Show only who is playing right now, within the selected group (with “All”, across your whole list)"));
        soloVivosBtn.setFocusable(false);
        soloVivosBtn.addActionListener(e -> {
            playersList.setFixedCellHeight(0);
            playersList.setFixedCellHeight(-1);
            aplicarFiltroGrupo();
        });
        String paisGuardado = leerConfig("top_pais", "es");
        List<PaisItem> ordenPais = new ArrayList<>();
        for (PaisItem pi : PAISES) if (pi.code().equals(paisGuardado)) ordenPais.add(pi);
        List<PaisItem> resto = new ArrayList<>();
        for (PaisItem pi : PAISES) if (!pi.code().equals(paisGuardado)) resto.add(pi);
        resto.sort(Comparator.comparing(PaisItem::nombre, String.CASE_INSENSITIVE_ORDER));
        ordenPais.addAll(resto);
        catalogoOrdenado = ordenPais;
        paisActual = ordenPais.get(0);   // el favorito guardado
        buscaPais = new JTextField(6);
        buscaPais.putClientProperty("JTextField.placeholderText", t("Buscar\u2026", "Search\u2026"));
        buscaPais.setToolTipText(t("Filtra países al teclear: «bul» → Bulgaria; Enter elige el primero",
                "Filters countries as you type: \u201Cbul\u201D \u2192 Bulgaria; Enter picks the first"));
        paisCombo = new JComboBox<>(ordenPais.toArray(PaisItem[]::new));
        paisCombo.setRenderer(new DefaultListCellRenderer() {   // con su bandera
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean foc) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, value, index, sel, foc);
                if (value instanceof PaisItem pi) { lab.setIcon(iconoBandera(pi.code())); lab.setIconTextGap(6); }
                return lab;
            }
        });
        paisCombo.setSelectedIndex(0);
        paisCombo.setMaximumRowCount(14);
        paisCombo.setToolTipText(t("País del top a mostrar (se recuerda como predeterminado)",
                "Country whose top to show (remembered as default)"));
        paisCombo.addActionListener(e -> {
            if (!rearmandoPais && paisCombo.getSelectedItem() instanceof PaisItem p) fijarPais(p);
        });
        instalarFiltroPais();
        paisCombo.setPrototypeDisplayValue(null);
        paisCombo.setPreferredSize(new Dimension(150, paisCombo.getPreferredSize().height));   // más compacto: el nombre se ve, no manda
        buscaPais.setColumns(8);
        parPais = new JPanel(new BorderLayout(6, 0));
        parPais.setOpaque(false);
        parPais.add(new JLabel(t("País:", "Country:")), BorderLayout.WEST);
        parPais.add(buscaPais, BorderLayout.CENTER);
        parPais.add(paisCombo, BorderLayout.EAST);
        parPais.setAlignmentX(Component.LEFT_ALIGNMENT);
        parPais.setVisible(false);
        JButton nuevoGrupoBtn = new JButton(t("Crear Grupo", "Create Group"));
        nuevoGrupoBtn.setToolTipText(t("Crear un grupo nuevo", "Create a new group"));
        nuevoGrupoBtn.addActionListener(e -> crearGrupoDialog());
        JButton gestGruposBtn = new JButton(t("Gestionar…", "Manage…"));
        gestGruposBtn.setToolTipText(t("Renombrar o borrar grupos", "Rename or delete groups"));
        gestGruposBtn.addActionListener(e -> gestionarGrupos());
        JButton gruposBtn = new JButton("\u2699");
        gruposBtn.setFocusable(false);
        gruposBtn.setMargin(new Insets(1, 7, 1, 7));
        gruposBtn.putClientProperty("JButton.buttonType", "roundRect");
        gruposBtn.setToolTipText(t("Grupos: crear, renombrar o borrar", "Groups: create, rename or delete"));
        gruposBtn.addActionListener(e -> {
            JPopupMenu pm = new JPopupMenu();
            JMenuItem crear = new JMenuItem(t("Crear grupo\u2026", "Create group\u2026"));
            crear.addActionListener(a -> crearGrupoDialog());
            JMenuItem gest = new JMenuItem(t("Gestionar grupos\u2026", "Manage groups\u2026"));
            gest.addActionListener(a -> gestionarGrupos());
            pm.add(crear); pm.add(gest);
            pm.show(gruposBtn, 0, gruposBtn.getHeight());
        });
        grupoFila.add(gruposBtn);
        campanaBtn = new JToggleButton(anfitrion.iconoVista("campana"));
        campanaBtn.setFocusable(false); campanaBtn.setMargin(new Insets(2, 5, 2, 5)); campanaBtn.putClientProperty("JButton.buttonType", "roundRect");
        campanaBtn.addActionListener(e -> alternarCampana());
        grupoFila.add(campanaBtn);
        topNCombo = new JComboBox<>(new String[]{ "Top 25", "Top 50", "Top 100" });
        topNCombo.setToolTipText(t("Cuántos jugadores enseñan Top ladder y Top país", "How many players Top ladder and Top country show"));
        topNCombo.setSelectedIndex(Math.max(0, Arrays.asList("25", "50", "100").indexOf(leerConfig("top_n", "50"))));
        topNCombo.addActionListener(e -> { if (rellenandoTopN) return; String n = new String[]{ "25", "50", "100" }[Math.max(0, topNCombo.getSelectedIndex())]; if (n.equals(leerConfig("top_n", "50"))) return; guardarConfig("top_n", n); if (modoTop() && !modoClan()) { topCargado = 0; cargarTopLadder(true); } });
        topNCombo.setVisible(false);
        grupoFila.add(topNCombo);
        addJugBtn = new JButton(t("+ Añadir jugador", "+ Add player"));
        addJugBtn.setFocusable(false);
        addJugBtn.setMargin(new Insets(1, 7, 1, 7));
        addJugBtn.putClientProperty("JButton.buttonType", "roundRect");
        addJugBtn.setToolTipText(t("Busca un jugador por nick y añádelo a este grupo", "Find a player by nick and add them to this group"));
        addJugBtn.addActionListener(e -> addPlayerDialog(false, buscaNick != null ? buscaNick.getText().trim() : null));
        grupoFila.add(addJugBtn);
        // Fila 3: el chip «● En directo» y el resumen en gris
        soloVivosBtn.putClientProperty("JButton.buttonType", "roundRect");
        soloVivosBtn.setFocusable(false);
        JPanel filtroFila = new JPanel(new BorderLayout(8, 0));
        filtroFila.setOpaque(false);
        filtroFila.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        formaBtn = new JButton(t("Ver forma", "Recent form"));
        formaBtn.putClientProperty("JButton.buttonType", "roundRect");
        formaBtn.setFocusable(false);
        formaBtn.setToolTipText(tipVerForma());
        formaBtn.addActionListener(e -> cargarForma(objetivoForma(), ventanaForma, () -> {   // siempre consulta (y suma a lo ya consultado)
            formaVisible = true; actualizarTextoForma();
            if (ocultarFormaBtn != null) ocultarFormaBtn.setVisible(true);
            refrescarCabeceraOrden(); aplicarFiltroGrupo(); playersList.repaint();
        }));
        ocultarFormaBtn = new JButton(t("Ocultar forma", "Hide recent form"));
        ocultarFormaBtn.setFocusable(false);
        ocultarFormaBtn.setMargin(new Insets(1, 6, 1, 6));
        ocultarFormaBtn.putClientProperty("JButton.buttonType", "roundRect");
        ocultarFormaBtn.setToolTipText(tipOcultarForma());
        ocultarFormaBtn.setVisible(false);
        ocultarFormaBtn.addActionListener(e -> apagarForma());
        JComboBox<String> ventanaCb = new JComboBox<>(new String[]{ t("últimas 24 h", "last 24 h"), t("últimos 7 días", "last 7 days") });
        ventanaCb.setFocusable(false);
        ventanaCb.setToolTipText(t("Ventana de la forma que consulta «Ver forma»", "Window used by \u201CRecent form\u201D"));
        ventanaCb.addActionListener(e -> {
            ventanaForma = ventanaCb.getSelectedIndex() == 1 ? 24 * 7 : 24;
            if (formaVisible) { refrescarCabeceraOrden(); aplicarFiltroGrupo(); playersList.repaint(); }   // la columna cambia de ventana al instante
        });
        JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));   // envuelve a otra línea, nunca se trunca
        chips.setOpaque(false);
        chips.add(soloVivosBtn); chips.add(formaBtn); chips.add(ventanaCb); chips.add(ocultarFormaBtn);
        filtroFila.add(chips, BorderLayout.CENTER);
        filtroFila.setAlignmentX(Component.LEFT_ALIGNMENT);
        buscaNick = new JTextField();
        buscaNick.putClientProperty("JTextField.placeholderText",
                t("\uD83D\uDD0D Buscar jugador por nick  (Enter = ver sus partidas)", "\uD83D\uDD0D Find a player by nick  (Enter = see their games)"));
        buscaNick.setToolTipText(t("Escribe un nick y pulsa Enter: verás sus partidas sin añadirlo; desde su nombre podrás ficharlo a un grupo.",
                "Type a nick and press Enter: you'll see their games without adding them; from their name you can add them to a group."));
        JPopupMenu nickPopup = new JPopupMenu(); nickPopup.setFocusable(false);
        javax.swing.Timer nickDebounce = new javax.swing.Timer(450, ev -> {
            String q = buscaNick.getText().trim();
            nickPopup.setVisible(false); nickPopup.removeAll();
            if (q.length() < 2) return;
            new Thread(() -> {
                List<String[]> res = busqueda.sugerir(q);
                SwingUtilities.invokeLater(() -> {
                    if (sugerenciaCaducada(q, buscaNick.getText())) return;
                    nickPopup.removeAll();
                    int n = 0;
                    for (String[] r : res) {
                        JMenuItem it = new JMenuItem(r[2]);
                        String nombre = r[1];
                        long pidSug = Long.parseLong(r[0]);
                        it.addActionListener(a -> { nickPopup.setVisible(false); buscaNick.setText(nombre); jugadorElegido(pidSug, nombre); });   // el elegido es ESTE jugador (por id): nada de volver a buscar por nombre
                        nickPopup.add(it);
                        if (++n >= 8) break;
                    }
                    if (n > 0 && buscaNick.isShowing() && buscaNick.hasFocus()) nickPopup.show(buscaNick, 0, buscaNick.getHeight());
                });
            }, "nick-sugerir").start();
        });
        nickDebounce.setRepeats(false);
        buscaNick.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { nickDebounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { nickDebounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { nickDebounce.restart(); }
        });
        buscaNick.addKeyListener(new KeyAdapter() { @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ESCAPE) nickPopup.setVisible(false); } });
        buscaNick.addActionListener(e -> {
            nickPopup.setVisible(false);
            String q = buscaNick.getText().trim();
            if (!q.isEmpty()) { addPlayerDialog(true, q); buscaNick.setText(""); }
        });
        JPanel buscaFila = new JPanel(new BorderLayout());
        buscaFila.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        buscaFila.add(buscaNick, BorderLayout.CENTER);
        JPanel norteWatch = new JPanel();
        norteWatchRef = norteWatch;
        norteWatch.setLayout(new BoxLayout(norteWatch, BoxLayout.Y_AXIS));
        norteWatch.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
        buscaFila.setAlignmentX(Component.LEFT_ALIGNMENT);
        norteWatch.add(buscaFila);   // 1. el buscador
        norteWatch.add(grupoFila);   // 2. la vista (grupo · ⚙ · + Añadir jugador)
        norteWatch.add(parPais);   //    el país, en su propia línea, solo en Top país
        clanField = new JTextField(10);
        clanField.putClientProperty("JTextField.placeholderText", t("Tag del clan (R1, DK, TdB…)", "Clan tag (R1, DK, TdB…)"));
        clanField.setText(leerConfig("clan_tag", ""));
        clanPopup = new JPopupMenu(); clanPopup.setFocusable(false);
        clanField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() {
                clanPopup.setVisible(false); clanPopup.removeAll();
                if (clanField.getText().trim().length() < 1) return;
                if (anfitrion.clanesVacios()) { Thread th = new Thread(anfitrion::asegurarLadderEnFondo, "clanes"); th.setDaemon(true); th.start(); return; }
                for (Map.Entry<String, Integer> en : anfitrion.sugerirClanes(clanField.getText())) {
                    JMenuItem it = new JMenuItem(en.getKey() + "  (" + en.getValue() + ")");
                    it.addActionListener(a -> { clanField.setText(en.getKey()); clanPopup.setVisible(false); cargarTopClan(); });
                    clanPopup.add(it);
                }
                if (clanPopup.getComponentCount() > 0) clanPopup.show(clanField, 0, clanField.getHeight());
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        clanField.addActionListener(e -> { clanPopup.setVisible(false); cargarTopClan(); });
        parClan = new JPanel(new BorderLayout(6, 0));
        parClan.setOpaque(false);
        parClan.add(new JLabel(t("Clan:", "Clan:")), BorderLayout.WEST);
        parClan.add(clanField, BorderLayout.CENTER);
        clanesGuardadosCombo = new JComboBox<>();
        clanesGuardadosCombo.setToolTipText(t("Tus clanes guardados: elige uno para cargar su top", "Your saved clans: pick one to load its top"));
        clanesGuardadosCombo.addActionListener(e -> { if (rellenandoClanes) return; Object v = clanesGuardadosCombo.getSelectedItem(); if (v instanceof String tag && !tag.isBlank() && !tag.startsWith("(")) { clanField.setText(tag); clanPopup.setVisible(false); cargarTopClan(); } });
        clanEstrella = new JButton(t("Guardar clan", "Save clan"));
        clanEstrella.setFocusable(false); clanEstrella.setMargin(new Insets(1, 8, 1, 8));
        clanEstrella.addActionListener(e -> {
            String tag = clanField.getText().trim();
            if (tag.isEmpty()) return;
            List<String> l = clanesGuardados();
            if (l.removeIf(x -> x.equalsIgnoreCase(tag))) status.setText(t("Clan quitado de guardados: ", "Clan removed from saved: ") + tag); else { l.add(tag); status.setText(t("Clan guardado: ", "Clan saved: ") + tag); }
            guardarConfig("clanes_guardados", String.join(",", l));
            refrescarClanesGuardados();
        });
        parClan.add(clanEstrella, BorderLayout.EAST);
        parClanGuardados = new JPanel(new BorderLayout(6, 0));
        parClanGuardados.setOpaque(false);
        parClanGuardados.add(new JLabel(t("Clanes guardados:", "Saved clans:")), BorderLayout.WEST);
        parClanGuardados.add(clanesGuardadosCombo, BorderLayout.CENTER);
        parClanGuardados.setAlignmentX(Component.LEFT_ALIGNMENT);
        parClanGuardados.setVisible(false);
        clanField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() { refrescarClanesGuardados(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        refrescarClanesGuardados();
        parClan.setAlignmentX(Component.LEFT_ALIGNMENT);
        parClan.setVisible(false);
        norteWatch.add(parClan);   //    el clan, en su propia línea, solo en Top clan
        norteWatch.add(parClanGuardados);   //    y debajo, los guardados
        norteWatch.add(filtroFila);   // 3. chip En directo + resumen
        norteWatch.add(watchPistas);   //    una línea de pista + «?»
        left.add(norteWatch, BorderLayout.NORTH);
        JLabel cabLabelLocal = new JLabel();
        cabLabelLocal.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Component.borderColor") != null
                        ? UIManager.getColor("Component.borderColor") : Color.GRAY));
        cabLabelLocal.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cabLabelLocal.setToolTipText(t("Clic en Nick o en ELO para ordenar por esa columna",
                "Click Nick or ELO to sort by that column"));
        this.cabLabel = cabLabelLocal;
        cabLabelLocal.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int wC = cabLabel.getWidth();
                String actual = leerConfig("orden_watch", "elo");
                String nuevo = e.getX() >= wC - 60 ? "elo"
                        : (formaVisible && e.getX() >= wC - 60 - 72 - 30) ? ("forma".equals(actual) ? "forma_asc" : "forma")   // 2.º clic: invierte
                        : "alfa";
                guardarConfig("orden_watch", nuevo);
                aplicarFiltroGrupo();
                refrescarCabeceraOrden();
            }
        });
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.add(cabLabel, BorderLayout.CENTER);
        hoverTimer = new javax.swing.Timer(600, ev -> {
            if (hoverPid != 0 && hoverPantalla != null && hoverProcede()) {
                for (int i = 0; i < playersModel.size(); i++)   // el pid ya quedó fijado al mover: mostramos directamente
                    if (playersModel.get(i).id() == hoverPid) {
                        mostrarPerfilCard(hoverPid, playersModel.get(i).name(), hoverPantalla);
                        return;
                    }
            }
        });
        hoverTimer.setRepeats(false);
        playersList.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int idx = playersList.locationToIndex(e.getPoint());
                long pid = 0;
                if (idx >= 0 && playersList.getCellBounds(idx, idx).contains(e.getPoint()))
                    pid = playersModel.get(idx).id();
                if (pid != 0 && enZonaForma(e.getPoint())) pid = 0;   // sobre la celda Forma no sale la tarjeta: sale su tooltip
                String bajoM = pid != 0 ? textoBajo(idx, e.getPoint()) : "";   // mano sobre TW, \u21A5 y \u270E
                boolean clicable = bajoM.contains("TW") || bajoM.contains("\u21A5") || bajoM.contains("\u270E")
                        || (pid != 0 && sobreNota(idx, e.getPoint()));
                playersList.setCursor(Cursor.getPredefinedCursor(clicable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                if (pid != hoverPid) {
                    if (hoverCard != null) ocultarHoverCard();
                    hoverTimer.stop();
                    hoverPid = pid;
                    if (false && pid != 0) {   // tarjeta flotante desactivada: ver comentario de la 1.1
                        hoverPantalla = e.getLocationOnScreen();
                        hoverTimer.restart();
                    }
                }
            }
        });
        playersList.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { ocultarHoverCard(); }
        });
        ToolTipManager.sharedInstance().registerComponent(playersList);
        playersList.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { refrescarCabeceraOrden(); }
        });
        JPanel listWrap = new JPanel(new BorderLayout());

        JScrollPane listScroll = new JScrollPane(playersList);
        listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.getViewport().addMouseListener(new MouseAdapter() {   // el hueco bajo la última fila es del visor
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown()) {
                    playersList.clearSelection();
                    enlacePartidas.actualizarTextoBuscar();
                }
            }
        });
        listScroll.getViewport().addChangeListener(e -> {
            ocultarHoverCard();
            int vw = listScroll.getViewport().getWidth();
            if (vw > 0 && playersList.getFixedCellWidth() != vw)
                playersList.setFixedCellWidth(vw);   // orden, no sugerencia: invalida el caché de medidas
            refrescarCabeceraOrden();
        });
        sujetosPanel.setLayout(new BoxLayout(sujetosPanel, BoxLayout.Y_AXIS));
        sujetosPanel.setVisible(false);
        JPanel norteLista = new JPanel(new BorderLayout());
        norteLista.add(sujetosPanel, BorderLayout.NORTH);
        norteLista.add(cabecera, BorderLayout.SOUTH);   // la cabecera, pegada a su lista
        listWrap.add(norteLista, BorderLayout.NORTH);
        listWrap.add(listScroll, BorderLayout.CENTER);
        left.add(listWrap, BorderLayout.CENTER);
        refrescarCabeceraOrden();
        left.add(leftButtons, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(280, 0));
    }

    /** Grupos donde se puede fichar a alguien: General, los que ya tienen gente y los guardados en config. */
    /** Grupos donde se puede fichar a alguien: «General», los grupos con gente ahora mismo y los guardados en
     *  config (idéntico al bloque que arma menuContextualWatchlist/menuDeJugador/mostrarVinculadas). */
    public Set<String> gruposParaFichar() {
        Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        gs.add(GRUPO_GENERAL);
        for (Player x : todosJugadores) gs.add(x.grupo());
        gs.addAll(gruposConfig());
        return gs;
    }

    /** Subtexto de quien está en partida, con el estilo de Live now. Se recorta a la anchura disponible. */
    private String subtextoVivo(long pid, FontMetrics fm, int px) {
        Match m = VIVO.partida(pid);
        String amb = temaOscuroActivo ? "#ffd56a" : "#b06a00";
        if (m == null || !anfitrion.enCursoReal(m)) {
            String info = VIVO.info(pid);
            return "<font color='#8a8a8a'>" + escapeHtml(dev.tirador.aoe2radar.util.Formato.truncarPx(info != null ? info : t("partida en curso — detalle en el próximo tick", "game in progress — details next tick"), fm, px)) + "</font>";
        }
        MatchPlayer yo = null; for (MatchPlayer mp : m.players) if (mp.id == pid) yo = mp;
        List<String> rivales = new ArrayList<>();
        MatchPlayer rivalUnico = null;
        for (MatchPlayer mp : m.players) { if (mp.id == pid) continue; if (yo != null && mp.team == yo.team) continue; rivales.add(anfitrion.nombreVisible(mp.id, mp.name) + (mp.rating != null ? " (" + mp.rating + ")" : "")); rivalUnico = mp; }
        boolean unoContraUno = m.players.size() == 2 && rivales.size() == 1;
        String prefijo = unoContraUno ? "vs " : "TG " + (m.players.size() / 2) + "v" + (m.players.size() / 2);
        String rival = unoContraUno ? String.join(", ", rivales) : "";
        String civs = unoContraUno && yo != null && yo.civ != null && rivalUnico != null && rivalUnico.civ != null ? yo.civ + "–" + rivalUnico.civ : "";
        String mapa = "";
        String reloj = "";
        java.util.function.Function<String[], String> plano = partes -> partes[0] + partes[1] + (partes[2].isEmpty() ? "" : " " + partes[2]) + (mapa.isEmpty() ? "" : " · " + mapa) + (partes[3].isEmpty() ? "" : " · " + partes[3]);
        String[] partes = { prefijo, rival, civs, reloj };
        if (fm.stringWidth(plano.apply(partes)) > px) partes[3] = "";
        if (fm.stringWidth(plano.apply(partes)) > px) partes[2] = "";
        while (fm.stringWidth(plano.apply(partes)) > px && partes[1].length() > 4) partes[1] = partes[1].substring(0, partes[1].length() - 2).trim() + "…";
        StringBuilder h = new StringBuilder();
        h.append("<font color='#8a8a8a'>").append(escapeHtml(partes[0])).append("</font>").append(escapeHtml(partes[1]));
        if (!partes[2].isEmpty()) h.append(" <font color='#8a8a8a'>").append(escapeHtml(partes[2])).append("</font>");
        if (!mapa.isEmpty()) h.append(" <font color='#8a8a8a'>·</font> <font color='").append(amb).append("'>").append(escapeHtml(mapa)).append("</font>");
        if (!partes[3].isEmpty()) h.append(" <font color='#8a8a8a'>·</font> <font color='").append(amb).append("'>").append(partes[3]).append("</font>");
        return h.toString();
    }

    public boolean enZonaForma(Point p) {
        int wL = playersList.getWidth() - 22, elo = anchoCeldaElo(wL);
        return formaVisible && p.x >= wL - elo - 72 && p.x <= wL - elo;
    }

    /** El campo filtra el combo de al lado; nadie más escribe en él, así que no puede realimentarse. */
    /** El campo filtra el combo de al lado; nadie más escribe en él, así que
     *  no puede realimentarse (la lección del cuelgue de la 4.41). */
    private void instalarFiltroPais() {
        buscaPais.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void filtrar() {
                String q = dev.tirador.aoe2radar.util.Texto.sinTildes(buscaPais.getText().trim());
                PaisItem prev = paisActual;
                rearmandoPais = true;
                try {
                    paisCombo.removeAllItems();
                    for (PaisItem pi : catalogoOrdenado)
                        if (q.isEmpty() || dev.tirador.aoe2radar.util.Texto.sinTildes(pi.nombre()).contains(q) || pi.code().startsWith(q))
                            paisCombo.addItem(pi);
                    if (prev != null)
                        for (int i = 0; i < paisCombo.getItemCount(); i++)
                            if (paisCombo.getItemAt(i).code().equals(prev.code())) { paisCombo.setSelectedIndex(i); break; }
                } finally { rearmandoPais = false; }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { filtrar(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { filtrar(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        buscaPais.addActionListener(e -> {   // Enter: el primero filtrado (y carga)
            if (paisCombo.getItemCount() > 0) paisCombo.setSelectedIndex(0);
        });
    }

    private void fijarPais(PaisItem pi) {
        paisActual = pi;
        guardarConfig("top_pais", pi.code());
        if (modoPais()) cargarTopLadder(true);
    }

    public String vistaActualId() {
        Object g = grupoCombo == null ? null : grupoCombo.getSelectedItem();
        String pais = modoPais() ? paisSel() : "";
        return g + "|" + pais;
    }

    /** Celda de ELO fija: siempre con sitio para «elo \u21A5altElo». */
    static int anchoCeldaElo(int wPanel) { return 86; }

    public boolean modoTop() {
        String s = String.valueOf(grupoCombo.getSelectedItem());
        return TOP_LADDER.equals(s) || TOP_PAIS.equals(s) || TOP_CLAN.equals(s);
    }
    public boolean modoClan() { return TOP_CLAN.equals(String.valueOf(grupoCombo.getSelectedItem())); }
    public boolean modoPais() { return TOP_PAIS.equals(String.valueOf(grupoCombo.getSelectedItem())); }
    public String paisSel() { return paisActual != null ? paisActual.code() : leerConfig("top_pais", "es"); }

    /** ¿El top ladder/país/clan está vacío? Lo consulta el temporizador de vigilancia (cromo, arrancar()). */
    public boolean topLadderVacio() { return topLadder.isEmpty(); }
    /** Cuándo se cargó el top por última vez (epoch ms); 0 = nunca. */
    public long topCargadoMs() { return topCargado; }
    /** Fuerza la próxima carga del top a ir a la red (config Top N cambiado desde el menú). */
    public void forzarRecargaTop() { topCargado = 0; cargarTopLadder(true); }
    /** Copia del top actual (ladder/país/clan): la consulta sincronizarSocket (cromo) para vigilar también el top. */
    public List<Player> topLadderSnapshot() { synchronized (topLadder) { return new ArrayList<>(topLadder); } }

    /** ¿Qué texto hay pintado bajo el punto p en la fila idx? Pregunta a la vista HTML real del renderer. */
    /** ¿Qué texto hay pintado bajo el punto p en la fila idx? Pregunta a la vista HTML
     *  real del renderer (exacto sea cual sea el ancho), o "" si no hay texto ahí. */
    private String textoBajo(int idx, Point p) {
        try {
            Rectangle b = playersList.getCellBounds(idx, idx);
            if (b == null || !b.contains(p)) return "";
            Component c = playersList.getCellRenderer().getListCellRendererComponent(
                    playersList, playersModel.get(idx), idx, false, false);
            if (!(c instanceof JLabel l)) return "";
            l.setBounds(0, 0, b.width, b.height);
            Object vo = l.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
            if (!(vo instanceof javax.swing.text.View v)) return "";
            Insets in = l.getInsets();
            Rectangle alloc = new Rectangle(in.left, in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
            v.setSize(alloc.width, alloc.height);
            int x = p.x - b.x, y = p.y - b.y;
            javax.swing.text.Position.Bias[] bias = new javax.swing.text.Position.Bias[1];
            int pos = v.viewToModel(x, y, alloc, bias);
            if (pos < 0) return "";
            java.awt.Shape s = v.modelToView(Math.max(0, pos - 1), alloc, javax.swing.text.Position.Bias.Forward);
            Rectangle r = s.getBounds();
            r.grow(6, 2);   // tolerancia de unos píxeles alrededor del glifo
            if (!r.contains(x, y)) return "";
            javax.swing.text.Document d = v.getDocument();
            int ini = Math.max(0, pos - 3), fin = Math.min(d.getLength(), pos + 3);
            return d.getText(ini, fin - ini);
        } catch (Exception ex) {
            return "";
        }
    }

    /** ¿El punto p cae sobre la sublínea «\u270E nota» de la fila idx? */
    /** ¿El punto p cae sobre la sublínea «\u270E nota» de la fila idx (toda la línea, no solo el lápiz)? */
    private boolean sobreNota(int idx, Point p) {
        try {
            if (idx < 0 || dialogos.notaDe(playersModel.get(idx).id()) == null) return false;
            Rectangle b = playersList.getCellBounds(idx, idx);
            if (b == null || !b.contains(p)) return false;
            Component c = playersList.getCellRenderer().getListCellRendererComponent(
                    playersList, playersModel.get(idx), idx, false, false);
            if (!(c instanceof JLabel l)) return false;
            l.setBounds(0, 0, b.width, b.height);
            Object vo = l.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
            if (!(vo instanceof javax.swing.text.View v)) return false;
            Insets in = l.getInsets();
            Rectangle alloc = new Rectangle(in.left, in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
            v.setSize(alloc.width, alloc.height);
            int x = p.x - b.x, y = p.y - b.y;
            javax.swing.text.Document d = v.getDocument();
            String todo = d.getText(0, d.getLength());
            int lapiz = todo.lastIndexOf('\u270E');   // el último \u270E es el de la sublínea
            if (lapiz < 0) return false;
            Rectangle rNota = v.modelToView(lapiz, alloc, javax.swing.text.Position.Bias.Forward).getBounds();
            return y >= rNota.y - 2 && y <= rNota.y + rNota.height + 2 && x >= rNota.x - 6;   // la línea entera, desde el lápiz
        } catch (Exception ex) {
            return false;
        }
    }

    // ===== Forma reciente (±ELO en una ventana de horas; 1v1 ranked) ====================================

    static String formaLarga(Forma f) {
        if (f.partidas() == 0) return t("sin partidas 1v1 en la ventana", "no 1v1 games in the window");
        String r = f.racha() >= 2 ? " \u00B7 " + t("racha ", "streak ") + f.racha() + (f.rachaGana() ? "V" : "D") : "";
        return f.w() + "-" + f.l() + " \u00B7 " + (f.diff() >= 0 ? "+" : "") + f.diff() + r;
    }

    static String tipVerForma() { return t("Consulta el ±ELO reciente (según el selector) y lo muestra como columna ordenable junto al ELO. Con jugadores seleccionados consulta solo esos; sin selección, todos. No se recuerda entre sesiones.",
            "Fetches the recent ±ELO (per the selector) and shows it as a sortable column next to the ELO. With players selected it checks only those; with none, everyone. Not remembered between sessions."); }
    static String tipOcultarForma() { return t("Oculta la columna Forma (los datos siguen en caché 10 min).", "Hides the Recent form column (data stays cached for 10 min)."); }

    private Map<Long, Forma> formaActiva() { return ventanaForma <= 24 ? forma24 : forma7d; }

    /** Cambiar de vista apaga la columna Forma (vuelve solo si la pides). */
    public void apagarForma() {
        if (!formaVisible) return;
        formaVisible = false;
        if (ocultarFormaBtn != null) ocultarFormaBtn.setVisible(false);
        if (formaBtn != null) actualizarTextoForma();
        if (leerConfig("orden_watch", "elo").startsWith("forma")) guardarConfig("orden_watch", "elo");
        refrescarCabeceraOrden();
    }

    /** Consulta la forma de los jugadores dados (con caché de 10 min), con progreso y Detener. */
    private void cargarForma(List<Player> objetivo, int horas, Runnable alTerminar) {
        Map<Long, Forma> cache = horas <= 24 ? forma24 : forma7d;
        Map<Long, Long> ts = horas <= 24 ? formaTs24 : formaTs7d;
        List<Player> pendientes = new ArrayList<>();
        long ahora = dev.tirador.aoe2radar.util.Reloj.SISTEMA.ahoraMs();   // una sola lectura del reloj para todo el lote, como la 1.1
        for (Player p : objetivo) if (formaService.pendiente(ts.getOrDefault(p.id(), 0L), ahora)) pendientes.add(p);
        if (pendientes.isEmpty()) { if (alTerminar != null) alTerminar.run(); return; }
        if (modoTop() && pendientes.size() > 20) {
            int seg = (int) Math.ceil(pendientes.size() * 0.6);
            int ok = JOptionPane.showConfirmDialog(ventana,
                    (horas <= 24 ? t("Consultar la forma de las últimas 24 h de ", "Fetching the last 24 h form of ")
                                 : t("Consultar la forma de los últimos 7 días de ", "Fetching the last 7 days form of "))
                            + pendientes.size() + t(" jugadores tarda ~", " players takes ~") + seg + " s.\n"
                            + t("Se consulta jugador a jugador (con pausas) y queda guardado 10 minutos.", "It goes player by player (with pauses) and is cached for 10 minutes."),
                    t("Forma reciente", "Recent form"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
        }
        anfitrion.trabajando(true);
        final long miSerial = anfitrion.opSerial();
        new SwingWorker<Void, String>() {
            @Override protected Void doInBackground() {
                anfitrion.marcarHiloOperacionActual();
                anfitrion.cargarEloAyer();
                List<Player> porApi = new ArrayList<>();
                long ahoraTs = System.currentTimeMillis();
                for (Player p : pendientes) {   // 1) resta con el snapshot nocturno: sin llamadas
                    Forma[] f = formaService.porResta(p.id(), eloWatch::get, gamesWatch::get);
                    if (f == null) { porApi.add(p); continue; }
                    forma24.put(p.id(), f[0]); formaTs24.put(p.id(), ahoraTs);
                    if (f[1] != null) { forma7d.put(p.id(), f[1]); formaTs7d.put(p.id(), ahoraTs); } else if (horas > 24) porApi.add(p);
                }
                publish(t("Forma: ", "Recent form: ") + (pendientes.size() - porApi.size()) + t(" del snapshot nocturno", " from the nightly snapshot") + (porApi.isEmpty() ? "" : " · " + porApi.size() + t(" consultas", " requests")));
                for (Player p : porApi) {   // 2) quien no está en el snapshot: su serie de rating, exacta (una llamada por jugador)
                    if (anfitrion.detenerOperacion()) break;
                    try {
                        Forma[] ambas = formaService.porSerie(p.id());
                        if (ambas != null) { forma24.put(p.id(), ambas[0]); formaTs24.put(p.id(), ahoraTs); forma7d.put(p.id(), ambas[1]); formaTs7d.put(p.id(), ahoraTs); }
                    } catch (Exception ex) { log("forma " + p.name() + ": " + causa(ex)); }
                }
                return null;
            }
            @Override protected void process(List<String> ch) { status.setText(ch.get(ch.size() - 1)); }
            @Override protected void done() {
                if (miSerial != anfitrion.opSerial()) return;
                anfitrion.trabajando(false);
                status.setText(t("Forma consultada.", "Recent form fetched."));
                if (alTerminar != null) alTerminar.run();
            }
        }.execute();
    }

    /** Objetivo de «Ver forma»: los seleccionados si los hay; si no, todos los visibles. */
    private List<Player> objetivoForma() {
        List<Player> sel = playersList.getSelectedValuesList();
        return sel.isEmpty() ? jugadoresVisibles() : new ArrayList<>(sel);
    }

    /** El botón dice lo que va a consultar: «Ver forma (todos · 50)» / «(3 seleccionados)» / «Ocultar forma». */
    public void actualizarTextoForma() {
        if (formaBtn == null) return;
        int n = playersList.getSelectedIndices().length;
        String quien = n > 0 ? n + t(" seleccionado" + (n > 1 ? "s" : ""), " selected")
                             : t("todos · ", "all · ") + playersModel.size();
        formaBtn.setText(t("Ver forma", "Recent form") + " (" + quien + ")");
    }

    private List<Player> jugadoresVisibles() {
        List<Player> v = new ArrayList<>();
        for (int i = 0; i < playersModel.size(); i++) v.add(playersModel.get(i));
        return v;
    }

    private String celdaForma(long pid) {
        Forma f = formaActiva().get(pid);
        if (f == null) return "<font color='#8a8a8a'>\u2014</font>";   // sin consultar
        String col = f.diff() > 0 ? (temaOscuroActivo ? "#6abf69" : "#2e7d32") : f.diff() < 0 ? (temaOscuroActivo ? "#e57373" : "#c62828") : "#8a8a8a";
        return "<font color='" + col + "'>" + escapeHtml(f.corta()) + "</font>";
    }

    private String rachaTexto(long pid, Forma f) {
        int n = 0; boolean gana = true;
        if (f != null && f.racha() >= 2) { n = f.racha(); gana = f.rachaGana(); }
        else if (modoTop() && TOP_STREAK.containsKey(pid) && Math.abs(TOP_STREAK.get(pid)) >= 2) { n = Math.abs(TOP_STREAK.get(pid)); gana = TOP_STREAK.get(pid) > 0; }
        if (n == 0) return "";
        return " · " + n + (gana ? t(" victorias seguidas", " wins in a row") : t(" derrotas seguidas", " losses in a row"));
    }

    public String tipForma(long pid) {
        Forma f = formaActiva().get(pid);
        StringBuilder sb = new StringBuilder(ventanaForma <= 24 ? t("Últimas 24 h: ", "Last 24 h: ") : t("Últimos 7 días: ", "Last 7 days: "));
        if (f == null) sb.append(t("sin consultar (selecciónalo y pulsa Ver forma)", "not fetched (select them and press Recent form)"));
        else if (f.partidas() == 0) sb.append(t("sin partidas 1v1", "no 1v1 games"));
        else sb.append(f.w()).append("-").append(f.l()).append(" · ").append(f.diff() >= 0 ? "+" : "").append(f.diff()).append(rachaTexto(pid, f));
        int[] l10 = TOP_LAST10.get(pid);
        if (modoTop() && l10 != null) sb.append(" · ").append(t("últimas 10: ", "last 10: ")).append(l10[0]).append("-").append(l10[1]);
        return sb.toString();
    }

    // ===== Campanas: aviso cuando alguien de una vista marcada entra en partida =========================

    private String idVistaCampana() {
        if (modoClan()) return "★clan|" + (clanField == null ? "" : clanField.getText().trim().toLowerCase(Locale.ROOT));
        if (modoPais()) return "★pais|" + paisSel();
        if (modoTop()) return "★ladder";
        return "grupo|" + String.valueOf(grupoCombo.getSelectedItem());
    }

    public boolean campanaContiene(long pid) { return campanas.campanaContiene(pid, campanaIds); }

    private void refrescarCampanaBtn() {
        if (campanaBtn == null) return;
        boolean on = campanas.campanas().contains(idVistaCampana());
        campanaBtn.setSelected(on);
        campanaBtn.setForeground(on ? (temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00)) : UIManager.getColor("Button.foreground"));
        campanaBtn.setToolTipText(on ? t("Avisos activados para esta lista: te avisa cuando alguien de aquí entre en partida (clic para apagar)", "Alerts on for this list: you get a notice when someone here starts a game (click to turn off)")
                : t("Activar para recibir un aviso cuando alguien de esta lista entre en partida", "Turn on to get a notice when someone in this list starts a game"));
    }

    private void alternarCampana() {
        String id = idVistaCampana();
        boolean activo = campanas.alternar(id);
        refrescarCampanaBtn();
        refrescarCampanas();
        status.setText(activo ? t("Avisos activados para «", "Alerts on for “") + id.replace("★", "★ ").replace("|", " ") + t("»: te avisaré cuando alguien entre en partida.", "”: you'll get a notice when someone starts a game.") : t("Avisos apagados para esta lista.", "Alerts off for this list."));
    }

    /** Recalcula (en segundo plano, service.Campanas) los jugadores de cada vista con campana y los mete en el socket. */
    /** Recalcula (en segundo plano, service.Campanas) los jugadores de cada vista con campana y los mete en el socket. Cada 15 min para tops, pais y clan. */
    public void refrescarCampanas() {
        Set<String> s = campanas.campanas();
        if (s.isEmpty()) { campanaIds.clear(); SwingUtilities.invokeLater(enlacePartidas::sincronizarSocket); return; }
        new Thread(() -> {
            Map<String, Set<Long>> nuevo = campanas.calcularCampanaIds(s, todosJugadores);
            campanaIds.clear(); campanaIds.putAll(nuevo);   // no atomico entre el clear y el putAll: ver DEUDA
            Set<Long> todos = new HashSet<>(); for (Set<Long> x : nuevo.values()) todos.addAll(x);
            anfitrion.actualizarSocketExtra(todos);
            SwingUtilities.invokeLater(enlacePartidas::sincronizarSocket);
        }, "campanas").start();
    }

    /** «Mi partida»: si el que entra en partida soy yo, aviso con el rival y accesos a su perfil y al cara a cara. */
    /** «Mi partida»: si el que entra en partida soy yo (mi_pid), aviso con el rival (bandera, ELO, civ) y accesos a su perfil y al cara a cara. */
    public void avisarMiPartida(long pid, Match m) {
        if (!campanas.tocaAvisarMiPartida(pid, m)) return;
        log("mi partida: el socket dice que mi partida " + m.id + " ha empezado (" + m.map + ", " + m.mode + ")");
        MatchPlayer yo0 = null; for (MatchPlayer p : m.players) if (p.id == pid) yo0 = p;
        if (yo0 == null) return;
        final MatchPlayer yo = yo0;
        List<MatchPlayer> rivales = new ArrayList<>(); for (MatchPlayer p : m.players) if (p.team != yo.team) rivales.add(p);
        StringBuilder txt = new StringBuilder("● " + t("Tu partida ha empezado", "Your game has started") + " · " + (m.map == null ? "" : m.map) + " · " + dev.tirador.aoe2radar.service.ReglasPartida.modoCorto(m) + " · " + t("vs ", "vs "));
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

    /** Alguien vigilado entra en partida: si está en una lista con campana, aviso (toast dentro de la app; Windows si está minimizada). */
    public void avisarSiCampana(long pid, Match m) {
        if (!campanas.tocaAvisar(pid, m, campanaIds)) return;
        String nombre = anfitrion.nombreVisible(pid, anfitrion.ahoraNombre(pid).equals(String.valueOf(pid)) ? nombreDe(pid) : anfitrion.ahoraNombre(pid));
        String resumen = enlacePartidas.resumenVivo(m, pid);
        String texto = "● " + nombre + t(" ha empezado una partida", " started a game") + (resumen != null ? " · " + resumen : "");
        SwingUtilities.invokeLater(() -> anfitrion.mostrarToast(texto, m.id));   // solo dentro de la app: nada de notificaciones de Windows
    }

    private String nombreDe(long pid) { for (Player p : todosJugadores) if (p.id() == pid) return p.name(); for (Player p : topLadder) if (p.id() == pid) return p.name(); return String.valueOf(pid); }

    // ===== «★ Top clan» =================================================================================

    public List<String> clanesGuardados() { List<String> l = new ArrayList<>(); for (String x : leerConfig("clanes_guardados", "").split(",")) if (!x.isBlank()) l.add(x.trim()); return l; }
    /** El tag de clan escrito ahora mismo en el campo (Top clan); "" si no hay campo o está vacío. */
    public String clanBuscado() { return clanField == null ? "" : clanField.getText().trim(); }

    private void refrescarClanesGuardados() {
        if (clanesGuardadosCombo == null) return;
        rellenandoClanes = true;
        try {
            List<String> l = clanesGuardados();
            clanesGuardadosCombo.removeAllItems();
            clanesGuardadosCombo.addItem(l.isEmpty() ? t("(sin clanes guardados)", "(no saved clans)") : t("Guardados…", "Saved…"));
            for (String x : l) clanesGuardadosCombo.addItem(x);
            String actual = clanField == null ? "" : clanField.getText().trim();
            boolean guardado = l.stream().anyMatch(x -> x.equalsIgnoreCase(actual));
            clanEstrella.setText(guardado ? t("Quitar de guardados", "Remove from saved") : t("Guardar clan", "Save clan"));
            clanEstrella.setEnabled(!actual.isEmpty());
            if (parClanGuardados != null) parClanGuardados.setVisible(parClan != null && parClan.isVisible() && !l.isEmpty());
        } finally { rellenandoClanes = false; }
    }

    // ----- «★ Top clan»: una vista más de la watchlist -----
    private void cargarTopClan() {
        String tag = clanField == null ? "" : clanField.getText().trim();
        if (tag.isEmpty()) { status.setText(t("Escribe el tag del clan (p. ej. R1).", "Type the clan tag (e.g. R1).")); return; }
        status.setText(anfitrion.clanesVacios() ? t("Descargando la lista de clanes…", "Downloading the clan list…") : t("Cargando el clan…", "Loading the clan…"));
        new Thread(() -> {
            TopLadderService.ResultadoClan res = topLadderService.topClan(tag);
            SwingUtilities.invokeLater(() -> {
                if (res.error() != null) { status.setText(t("No se pudo cargar la lista de clanes: ", "Couldn't load the clan list: ") + res.error()); return; }
                topLadder.clear();
                ultimoTopMs = 0; anfitrion.reiniciarThrottleDirectos();   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
                lastTop.clear(); rankTop.clear();
                for (TopLadderService.FilaClan f : res.miembros()) {
                    topLadder.add(new Player(f.pid(), f.nombre(), TOP_CLAN));
                    eloWatch.put(f.pid(), f.rating());
                    rankTop.put(f.pid(), topLadder.size());
                }
                guardarConfig("clan_tag", tag);
                aplicarFiltroGrupo();
                actualizarIndicadoresVivos();
                status.setText(res.miembros().isEmpty() ? t("Ningún clan del ladder 1v1 se llama «", "No 1v1 ladder clan is called “") + tag + t("» (elige uno de las sugerencias).", "” (pick one from the suggestions).")
                        : t("Clan ", "Clan ") + tag + ": " + res.miembros().size() + t(" jugadores en el ladder 1v1 (resumen diario).", " players on the 1v1 ladder (daily summary)."));
            });
        }, "top-clan").start();
    }

    /** Carga el top N del leaderboard (nick, ELO, última partida) con caché de 10 min, y dispara un barrido de vivos. */
    /** Barrido de la Watchlist al abrir: para cada seguido, una consulta ligera
     *  que detecta partida en curso (finished vacío) y su ELO actual (rating de
     *  su último 1v1 terminado). Solo toca la lista, nunca la tabla: los
     *  resultados de las partidas siguen sin verse. */
    /** Carga el top N del leaderboard (nick, ELO, última partida) sin tocar
     *  players.txt, con caché de 10 min, y dispara un barrido de vivos. */
    public void cargarTopLadder(boolean forzar) {
        if (cargandoTop) return;
        String pais = modoPais() ? paisSel() : null;
        String firma = pais == null ? "global" : pais;
        if (topLadderService.topFresco(forzar, firma, topFirma, !topLadder.isEmpty(), topCargado)) {
            aplicarFiltroGrupo();
            actualizarIndicadoresVivos();
            vigilarTop();
            return;
        }
        cargandoTop = true;
        int topN = Integer.parseInt(leerConfig("top_n", "50"));
        String nombrePais = null;
        if (pais != null) for (PaisItem pi : PAISES) if (pi.code().equals(pais)) { nombrePais = pi.nombre(); break; }
        final String nombrePaisF = nombrePais;
        status.setText(t("Cargando el top ", "Loading the top ") + topN
                + (nombrePais != null ? t(" de ", " of ") + nombrePais : t(" del ladder…", " of the ladder…")));
        new SwingWorker<TopLadderService.ResultadoTop, Void>() {
            @Override protected TopLadderService.ResultadoTop doInBackground() {
                return topLadderService.cargarTop(pais, topN);
            }
            @Override protected void done() {
                cargandoTop = false;
                try {
                    TopLadderService.ResultadoTop res = get();
                    TOP_STREAK.putAll(res.racha());
                    TOP_LAST10.putAll(res.ultimas10());
                    gamesWatch.putAll(res.partidas());   // como en la 1.1: se aprenden siempre, aunque el usuario haya cambiado de vista
                    if (modoClan() || !modoTop()) return;   // mientras cargaba, el usuario cambió de vista: no pintar encima
                    if (res.filas().isEmpty()) {
                        if (cargarTopCache(firma)) {
                            topFirma = firma;
                            aplicarFiltroGrupo();
                            actualizarIndicadoresVivos();
                            long horasCache = Math.max(1, (System.currentTimeMillis() - topCargado) / 3600_000L);
                            status.setText(t("El servicio de datos no responde (¿bloqueo de red? p. ej. LaLiga/Cloudflare). Mostrando el top de hace ~",
                                    "The data service isn't responding (network block? e.g. LaLiga/Cloudflare). Showing the top from ~")
                                    + horasCache + t(" h. Reintento automático cada 2 min.", " h ago. Auto-retrying every 2 min."));
                        } else {
                            if (!avisoTopMostrado) {
                                avisoTopMostrado = true;
                                JOptionPane.showMessageDialog(ventana,
                                        t("No se pudo cargar el top del ladder.\n\nCausa probable: el servicio de datos está caído o bloqueado\n(p. ej. LaLiga/Cloudflare en días de fútbol en España).\n\nLa app reintenta sola cada 2 minutos — no hace falta hacer nada.",
                                          "Couldn't load the ladder top.\n\nLikely cause: the data service is down or blocked\n(e.g. LaLiga/Cloudflare on football days in Spain).\n\nThe app retries every 2 minutes on its own — nothing to do."),
                                        t("Servicio no disponible", "Service unavailable"),
                                        JOptionPane.WARNING_MESSAGE);
                            }
                            status.setText(t("No se pudo cargar el top (¿servicio caído o bloqueado? p. ej. LaLiga/Cloudflare en días de fútbol). Reintento automático cada 2 min.",
                                    "Couldn't load the top (service down or blocked? e.g. LaLiga/Cloudflare on match days). Auto-retrying every 2 min."));
                        }
                        return;
                    }
                    topLadder.clear();
                    ultimoTopMs = 0; anfitrion.reiniciarThrottleDirectos();   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
                    lastTop.clear();
                    rankTop.clear();
                    for (TopLadderService.FilaTop f : res.filas()) {
                        topLadder.add(new Player(f.pid(), f.nombre(), TOP_LADDER));
                        eloWatch.put(f.pid(), f.rating());
                        lastTop.put(f.pid(), f.ultimaPartidaMs());
                        rankTop.put(f.pid(), topLadder.size());
                    }
                    topCargado = System.currentTimeMillis();
                    topFirma = firma;
                    guardarTopCache(firma);
                    aplicarFiltroGrupo();
                    actualizarIndicadoresVivos();
                    status.setText(t("Top ", "Top ") + topLadder.size()
                            + (nombrePaisF != null ? t(" de ", " of ") + nombrePaisF : t(" del ladder", " of the ladder"))
                            + t(" cargado. Los puntos rojos llegan en segundos…",
                                " loaded. Red dots arriving in seconds…"));
                    vigilarTop();
                } catch (Exception ex) {
                    status.setText(t("Error cargando el top: ", "Error loading the top: ") + causa(ex));
                }
            }
        }.execute();
    }

    private void guardarTopCache(String firma) {
        List<TopLadderService.FilaCache> filas = new ArrayList<>();
        for (Player p : topLadder)
            filas.add(new TopLadderService.FilaCache(p.id(), p.name(), eloWatch.getOrDefault(p.id(), 0), lastTop.getOrDefault(p.id(), 0L)));
        topLadderService.guardarCache(topCache, firma, topCargado, filas);
    }

    /** Restaura el último top guardado si es de la misma vista. Devuelve éxito. */
    private boolean cargarTopCache(String firma) {
        TopLadderService.TopCache cache = topLadderService.cargarCache(topCache, firma);
        if (cache == null) return false;
        topLadder.clear();
        ultimoTopMs = 0; anfitrion.reiniciarThrottleDirectos();   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
        lastTop.clear();
        rankTop.clear();
        for (TopLadderService.FilaCache f : cache.filas()) {
            topLadder.add(new Player(f.pid(), f.nombre(), TOP_LADDER));
            if (f.elo() > 0) eloWatch.put(f.pid(), f.elo());
            lastTop.put(f.pid(), f.ultimaPartidaMs());
            rankTop.put(f.pid(), topLadder.size());
        }
        topCargado = cache.cargadoMs();
        return !topLadder.isEmpty();
    }

    /** Vivos del top: el río global de partidas en curso como motor, consulta a los «calientes» y verificación
     *  individual presupuestada de los que se apagan. */
    /** Vivos del top: el río global de partidas en curso como motor (1-2
     *  llamadas), consulta a los «calientes» si el río no trae en-curso, y
     *  verificación individual presupuestada de los que se apagan. */
    public void vigilarTop() {
        if (vigilandoTop || topLadder.isEmpty()) return;
        if (System.currentTimeMillis() - ultimoTopMs < 45_000) return;   // anti-solape: un barrido por tick
        ultimoTopMs = System.currentTimeMillis();
        vigilandoTop = true;
        List<Player> top = new ArrayList<>(topLadder);
        new SwingWorker<TopLadderService.ResultadoVigilancia, Void>() {
            final List<Match> terminadasRio = new ArrayList<>();
            @Override protected TopLadderService.ResultadoVigilancia doInBackground() {
                return topLadderService.vigilarTop(top, VIVO::jugando, VIVO::matchDe,
                        (pid, m) -> {   // el lote: alguien aparece en curso (ver DEUDA: avisarSiCampana sigue en el hilo de fondo, como en la 1.1)
                            VIVO.ponerInfo(pid, enlacePartidas.resumenVivo(m, pid));
                            if (!VIVO.jugando(pid)) avisarSiCampana(pid, m);   // nuevo en partida desde el último barrido
                            VIVO.guardarPartida(pid, m);
                        },
                        (pid, m) -> VIVO.ponerInfo(pid, enlacePartidas.resumenVivo(m, pid)));   // la confirmación individual
            }
            @Override protected void done() {
                vigilandoTop = false;
                anfitrion.vigilarTwitchDirectos();
                try {
                    TopLadderService.ResultadoVigilancia r = get();
                    topVerificados.clear();
                    topVerificados.addAll(r.verificados());
                    Map<Long, Long> vivos = r.resultado();
                    for (Player p : top) {
                        if (!topVerificados.contains(p.id())) continue;   // lote fallido: ni quitar ni poner
                        Long v = vivos.get(p.id());
                        if (v != null) VIVO.marcarJugando(p.id(), v);
                        else { VIVO.marcarFuera(p.id()); }   // el REST manda al quitar
                    }
                    boolean tablaTocada = false;
                    for (Match fresco : terminadasRio)
                        for (Match m : all)
                            if (m.id == fresco.id && m.finished == null) {
                                m.finished = fresco.finished;
                                m.players = fresco.players;
                                tablaTocada = true;
                            }
                    if (tablaTocada) enlacePartidas.applyFilters();
                    else enlacePartidas.repintarTabla();
                    actualizarIndicadoresVivos();
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    /** La cuenta hermana con MÁS ELO conocido que la propia, o null. */
    /** La cuenta hermana con MÁS ELO conocido que la propia, o null.
     *  Bebe de los vínculos guardados y de las familias consultadas. */
    private String[] mejorAlt(long pid) {
        return familiaSvc.mejorAlt(pid, todosJugadores, perfiles.familia(pid), eloWatch);
    }

    /** Consulta las vinculadas de un seguido y casa las que YA sigues. */
    private void vincularExistentes(Player p) {
        status.setText(t("Buscando cuentas vinculadas de ", "Looking up linked accounts of ") + p.name() + "…");
        new SwingWorker<List<Perfil.Vinculada>, Void>() {
            @Override protected List<Perfil.Vinculada> doInBackground() { return perfiles.vinculadas(p.id()); }
            @Override protected void done() {
                List<Perfil.Vinculada> vinc;
                try { vinc = get(); } catch (Exception e) { vinc = List.of(); }
                Set<Long> familia = familiaSvc.vincularExistentes(p.id(), vinc, WatchlistView.this::containsPlayerId);
                if (familia.size() < 2) {
                    status.setText(t("No sigues ninguna otra cuenta vinculada de ", "You don't follow any other linked account of ")
                            + p.name() + t(". Usa «Cuentas vinculadas…» para añadirlas.", ". Use “Linked accounts…” to add them."));
                    return;
                }
                marcarVinculo(familia);
                savePlayers();
                aplicarFiltroGrupo();
                status.setText(t("Vinculadas ", "Linked ") + familia.size()
                        + t(" cuentas: ahora comparten fila en la lista.", " accounts: they now share a row in the list."));
            }
        }.execute();
    }

    // ===== Grupos ========================================================================================

    public String grupoActivo() {   // null = «Todos»
        Object sel = grupoCombo.getSelectedItem();
        if (sel == null) return null;
        String s = String.valueOf(sel);
        return s.equals(t("Todos", "All")) || s.equals(TOP_LADDER) || s.equals(TOP_PAIS)
                || s.equals(t("+ Nuevo grupo…", "+ New group…"))
                || s.equals(t("Gestionar grupos…", "Manage groups…")) ? null : s;
    }

    private static String limpiarGrupo(String nombre) { return nombre.trim().replace(";", " ").replace(",", " "); }

    /** Grupos creados por el usuario (existen aunque estén vacíos). */
    public Set<String> gruposConfig() { return listaSeguidos.gruposConfig(); }

    private void registrarGrupo(String g) { listaSeguidos.registrarGrupo(g); rebuildGrupos(); }

    public void moverJugador(Player p, String grupo) {
        listaSeguidos.moverJugador(todosJugadores, p, grupo);
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
        actualizarIndicadoresVivos();
        status.setText(p.name() + t(" movido al grupo «", " moved to group “") + grupo + t("».", "”."));
    }

    private void renombrarGrupo(String viejo, String nuevo) {
        listaSeguidos.renombrarGrupo(todosJugadores, viejo, nuevo);
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
    }

    private void borrarGrupo(String g) {
        listaSeguidos.borrarGrupo(todosJugadores, g);
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
    }

    /** Diálogo de gestión: crear, renombrar y borrar grupos sin tocar jugadores. */
    private void gestionarGrupos() {
        DefaultListModel<String> modelo = new DefaultListModel<>();
        Runnable recargar = () -> {
            modelo.clear();
            Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            gs.add(GRUPO_GENERAL);
            gs.addAll(gruposConfig());
            for (Player p : todosJugadores) gs.add(p.grupo());
            for (String g : gs) modelo.addElement(g);
        };
        recargar.run();
        JList<String> lista = new JList<>(modelo);
        lista.setVisibleRowCount(8);
        JButton nuevo = new JButton(t("Nuevo…", "New…"));
        JButton renombrar = new JButton(t("Renombrar…", "Rename…"));
        JButton borrar = new JButton(t("Borrar", "Delete"));
        nuevo.addActionListener(a -> {
            String n = JOptionPane.showInputDialog(ventana, t("Nombre del grupo nuevo:", "New group name:"),
                    t("Nuevo grupo", "New group"), JOptionPane.PLAIN_MESSAGE);
            if (n != null && !n.isBlank()) { registrarGrupo(limpiarGrupo(n)); recargar.run(); }
        });
        renombrar.addActionListener(a -> {
            String sel = lista.getSelectedValue();
            if (sel == null || sel.equalsIgnoreCase(GRUPO_GENERAL)) return;
            String n = JOptionPane.showInputDialog(ventana, t("Nuevo nombre para «", "New name for “") + sel + "»:",
                    t("Renombrar grupo", "Rename group"), JOptionPane.PLAIN_MESSAGE);
            if (n != null && !n.isBlank()) { renombrarGrupo(sel, limpiarGrupo(n)); recargar.run(); }
        });
        borrar.addActionListener(a -> {
            String sel = lista.getSelectedValue();
            if (sel == null || sel.equalsIgnoreCase(GRUPO_GENERAL)) return;
            int r = JOptionPane.showConfirmDialog(ventana,
                    t("Se borrará el grupo «", "Group “") + sel
                            + t("». Sus jugadores pasarán a General. ¿Continuar?",
                                "” will be deleted. Its players move to General. Continue?"),
                    t("Borrar grupo", "Delete group"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r == JOptionPane.YES_OPTION) { borrarGrupo(sel); recargar.run(); }
        });
        lista.addListSelectionListener(a -> {
            String sel = lista.getSelectedValue();
            boolean editable = sel != null && !sel.equalsIgnoreCase(GRUPO_GENERAL);
            renombrar.setEnabled(editable);
            borrar.setEnabled(editable);
        });
        renombrar.setEnabled(false);
        borrar.setEnabled(false);
        JPanel botones = new JPanel(new GridLayout(3, 1, 0, 6));
        botones.add(nuevo); botones.add(renombrar); botones.add(borrar);
        JPanel cont = new JPanel(new BorderLayout(8, 0));
        cont.add(new JScrollPane(lista), BorderLayout.CENTER);
        cont.add(botones, BorderLayout.EAST);
        JOptionPane.showMessageDialog(ventana, cont, t("Gestionar grupos", "Manage groups"), JOptionPane.PLAIN_MESSAGE);
    }

    public String grupoDestino() {
        String g = grupoActivo();
        return g != null ? g : GRUPO_GENERAL;
    }

    /** Ficha a uno desde un top y, como el buscador, ofrece sus cuentas vinculadas. */
    public void ficharDesdeTop(Player p, String g) {
        if (!listaSeguidos.ficharDesdeTop(todosJugadores, p, g)) return;
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
        status.setText(p.name() + t(" añadido a «", " added to “") + g + t("» de tu watchlist.", "” in your watchlist."));
        ofrecerVinculadasTrasAlta(p.id(), p.name(), g);
    }

    /**
     * Ficha a varios de golpe; pregunta UNA vez si buscar sus cuentas vinculadas y lo hace en segundo plano.
     * BUG corregido (fase 2, service.ListaSeguidos): la 1.1 mutaba todosJugadores y llamaba a marcarVinculo
     * desde doInBackground (hilo de fondo) mientras el EDT recorre esa misma lista. Ahora, en el mismo punto
     * donde la 1.1 mutaba, se aplica con SwingUtilities.invokeAndWait: la mutación ocurre en el EDT y
     * doInBackground espera a que termine antes de seguir con el siguiente jugador; así, al acabar el
     * bucle, ya está todo aplicado (done() no puede adelantarse al último hallazgo). Los textos de
     * estado siguen yendo por publish/process, como antes.
     */
    public void ficharVarios(List<Player> lista, String g) {
        List<Player> nuevos = listaSeguidos.ficharVarios(todosJugadores, lista, g);
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
        refrescarWatchlist();
        status.setText(nuevos.size() + t(" jugadores añadidos a «", " players added to “") + g + "».");
        if (nuevos.isEmpty()) return;
        int r = JOptionPane.showConfirmDialog(ventana,
                t("¿Buscar las cuentas vinculadas de los ", "Look up the linked accounts of the ") + nuevos.size()
                        + t(" jugadores y añadirlas al grupo? (una consulta por jugador, en segundo plano)", " players and add them to the group? (one lookup per player, in the background)"),
                t("Cuentas vinculadas", "Linked accounts"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        anfitrion.trabajando(true);
        final long miSerial = anfitrion.opSerial();
        final int[] anadidas = { 0 };
        new SwingWorker<Void, String>() {
            @Override protected Void doInBackground() {
                anfitrion.marcarHiloOperacionActual();
                for (Player p : nuevos) {
                    if (anfitrion.detenerOperacion()) break;
                    publish(t("Vinculadas de ", "Linked accounts of ") + p.name() + "…");
                    try {
                        List<Perfil.Vinculada> vinc = perfiles.vinculadas(p.id());
                        SwingUtilities.invokeAndWait(() -> {
                            try {
                                Set<Long> familia = new HashSet<>(); familia.add(p.id());
                                for (Perfil.Vinculada v : vinc) {
                                    long vid = v.pid();
                                    if (listaSeguidos.ficharSiNuevo(todosJugadores, vid, v.nombre(), g)) anadidas[0]++;
                                    familia.add(vid);
                                }
                                if (familia.size() > 1) marcarVinculo(familia);
                            } catch (Exception ex) { log("vinculadas " + p.name() + ": " + causa(ex)); }
                        });
                    } catch (Exception ex) { log("vinculadas " + p.name() + ": " + causa(ex)); }
                    anfitrion.dormir(pausaMs / 2);
                }
                return null;
            }
            @Override protected void process(List<String> ch) { status.setText(ch.get(ch.size() - 1)); }
            @Override protected void done() {
                if (miSerial != anfitrion.opSerial()) return;
                anfitrion.trabajando(false);
                savePlayers(); rebuildGrupos(); aplicarFiltroGrupo(); refrescarWatchlist();
                status.setText(anadidas[0] + t(" cuentas vinculadas añadidas a «", " linked accounts added to “") + g + "».");
            }
        }.execute();
    }

    private Set<String> gruposExistentes() {
        Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        gs.add(GRUPO_GENERAL);
        for (Player x : todosJugadores) gs.add(x.grupo());
        gs.addAll(gruposConfig());
        return gs;
    }

    private void moverVarios(List<Player> lista, String g) {
        int n = listaSeguidos.moverVarios(todosJugadores, lista, g);
        savePlayers(); rebuildGrupos(); aplicarFiltroGrupo(); refrescarWatchlist();
        status.setText(n + t(" jugadores movidos a «", " players moved to “") + g + "».");
    }

    /** Pregunta a qué grupo fichar (preseleccionado el activo), con «Nuevo grupo…». null = cancelado. */
    public String elegirGrupoDialog(String nombreJugador) {
        Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        gs.add(GRUPO_GENERAL);
        for (Player x : todosJugadores) gs.add(x.grupo());
        gs.addAll(gruposConfig());
        String nuevoO = t("+ Nuevo grupo…", "+ New group…");
        List<String> ops = new ArrayList<>(gs);
        ops.add(nuevoO);
        JComboBox<String> cb = new JComboBox<>(ops.toArray(String[]::new));
        String pre = grupoActivo();
        if (pre != null && gs.contains(pre)) cb.setSelectedItem(pre);
        JPanel pnl = new JPanel(new BorderLayout(0, 6));
        pnl.add(new JLabel(t("¿A qué grupo añadir a ", "Which group should ") + nombreJugador + (t("?", " join?"))), BorderLayout.NORTH);
        pnl.add(cb, BorderLayout.CENTER);
        int r = JOptionPane.showConfirmDialog(ventana, pnl, t("Añadir al grupo", "Add to group"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return null;
        String sel = String.valueOf(cb.getSelectedItem());
        if (sel.equals(nuevoO)) {
            String nombre = JOptionPane.showInputDialog(ventana, t("Nombre del nuevo grupo:", "New group name:"), "");
            if (nombre == null || nombre.trim().isEmpty()) return null;
            nombre = nombre.trim();
            Set<String> cfg = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            cfg.addAll(gruposConfig()); cfg.add(nombre);
            guardarConfig("grupos", String.join(";", cfg));
            return nombre;
        }
        return sel;
    }

    /** Rellena el combo con «Todos», los grupos existentes y «+ Nuevo grupo…», conservando la selección guardada. */
    /** Rellena el combo con «Todos», los grupos existentes y «+ Nuevo grupo…»,
     *  conservando la selección guardada. */
    public void rebuildGrupos() {
        var listener = grupoCombo.getActionListeners();
        for (var l : listener) grupoCombo.removeActionListener(l);
        String guardado = leerConfig("grupo_activo", t("Todos", "All"));
        Set<String> grupos = listaSeguidos.calcularGrupos(todosJugadores);   // un grupo ya nunca se esfuma al vaciarse
        grupoCombo.removeAllItems();
        grupoCombo.addItem(t("Todos", "All"));
        grupoCombo.addItem(TOP_LADDER);
        grupoCombo.addItem(TOP_PAIS);
        grupoCombo.addItem(TOP_CLAN);
        for (String g : grupos) grupoCombo.addItem(g);
        grupoCombo.setSelectedItem(t("Todos", "All"));
        for (int i = 0; i < grupoCombo.getItemCount(); i++)
            if (grupoCombo.getItemAt(i).equalsIgnoreCase(guardado)) { grupoCombo.setSelectedIndex(i); break; }
        for (var l : listener) grupoCombo.addActionListener(l);
    }

    private void onGrupoElegido() {
        String sel = String.valueOf(grupoCombo.getSelectedItem());
        if (sel.equals(TOP_CLAN)) {
            guardarConfig("grupo_activo", sel);
            actualizarBotonesModo();
            if (clanField != null && clanField.getText().isBlank()) clanField.setText(leerConfig("clan_tag", ""));
            cargarTopClan();
            return;
        }
        if (sel.equals(TOP_LADDER) || sel.equals(TOP_PAIS)) {
            guardarConfig("grupo_activo", sel);
            actualizarBotonesModo();
            cargarTopLadder(false);
            return;
        }
        guardarConfig("grupo_activo", sel);
        actualizarBotonesModo();
        aplicarFiltroGrupo();
        refrescarWatchlist();
        actualizarIndicadoresVivos();
    }

    private void crearGrupoDialog() {
        String nombre = JOptionPane.showInputDialog(ventana,
                t("Nombre del grupo nuevo:", "New group name:"),
                t("Nuevo grupo", "New group"), JOptionPane.PLAIN_MESSAGE);
        if (nombre == null || nombre.isBlank()) return;
        String limpio = limpiarGrupo(nombre);
        guardarConfig("grupo_activo", limpio);
        registrarGrupo(limpio);
        grupoCombo.setSelectedItem(limpio);
        aplicarFiltroGrupo();
        actualizarIndicadoresVivos();
        status.setText(t("Grupo «", "Group “") + limpio
                + t("» activo: los próximos jugadores que añadas caerán ahí.",
                    "” active: players you add next will go there."));
    }

    private void refrescarCabeceraOrden() {
        if (cabLabel == null) return;
        String ordenCfg = leerConfig("orden_watch", "elo");
        boolean porForma = formaVisible && ordenCfg.startsWith("forma");
        boolean formaAsc = "forma_asc".equals(ordenCfg);
        boolean porElo = !porForma && mostrarEloWatch && "elo".equals(ordenCfg);
        String ordenable = " <font color='#8a8a8a'>\u21C5</font>";   // «⇅»: aquí también se puede ordenar
        cabLabel.setToolTipText(t("Clic en una cabecera para ordenar por Nick, ELO o Forma", "Click a header to sort by Nick, ELO or Recent form"));
        cabLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        int w = Math.max(150, playersList.getWidth() > 0 ? playersList.getWidth() - 22 : 250);
        cabLabel.setText("<html><table width='" + w + "' cellpadding='0' cellspacing='0'><tr>"
                + "<td width='24'></td><td width='14'></td>"
                + "<td><b>Nick" + (porElo || porForma ? ordenable : " \u2193") + "</b></td>"
                + (formaVisible ? "<td width='72' align='center'><b>" + t("Forma ", "Form ") + (ventanaForma <= 24 ? "24h" : "7d")
                        + (porForma ? (formaAsc ? " \u2191" : " \u2193") : ordenable) + "</b></td>" : "")
                + "<td width='" + anchoCeldaElo(Math.max(150, playersList.getWidth() - 22)) + "' align='right'><b>" + (mostrarEloWatch ? "ELO" + (porElo ? " \u2193" : ordenable) : "") + "</b></td>"
                + "</tr></table></html>");
    }

    /** En los modos ★ la lista es de solo lectura; el país solo se ve en ★ país. */
    private void actualizarBotonesModo() {
        boolean editable = !modoTop();
        if (delBtn != null) delBtn.setEnabled(editable);
        if (parPais != null) parPais.setVisible(modoPais());
        if (parClan != null) parClan.setVisible(modoClan());
        if (parClanGuardados != null) parClanGuardados.setVisible(modoClan() && !clanesGuardados().isEmpty());
        if (topNCombo != null) { topNCombo.setVisible(modoTop() && !modoClan()); rellenandoTopN = true; try { topNCombo.setSelectedIndex(Math.max(0, Arrays.asList("25", "50", "100").indexOf(leerConfig("top_n", "50")))); } finally { rellenandoTopN = false; } }
        refrescarCampanaBtn();
        if (addJugBtn != null) addJugBtn.setVisible(!modoTop());   // en los tops se ficha desde la fila
        if (delBtn != null) delBtn.setVisible(!modoTop() && grupoActivo() != null);   // solo en grupos personalizados
        if (buscaNick != null) buscaNick.putClientProperty("JTextField.placeholderText", modoTop()
                ? t("\uD83D\uDD0D Buscar jugador por nick  (Enter = ver sus partidas)", "\uD83D\uDD0D Find a player by nick  (Enter = view their games)")
                : t("\uD83D\uDD0D Buscar o añadir jugador por nick  (Enter = ver sus partidas)", "\uD83D\uDD0D Find or add a player by nick  (Enter = view their games)"));
    }

    /** Reconstruye la lista visible con el grupo activo («Todos» = todos). */
    public void aplicarFiltroGrupo() {
        String g = grupoActivo();
        boolean soloVivos = soloVivosBtn != null && soloVivosBtn.isSelected();
        boolean porElo = mostrarEloWatch && "elo".equals(leerConfig("orden_watch", "elo"));
        String ordenCfg = leerConfig("orden_watch", "elo");
        boolean porForma = formaVisible && ordenCfg.startsWith("forma");
        boolean formaAsc = "forma_asc".equals(ordenCfg);
        final Map<Long, Forma> fa = formaActiva();
        FiltroLista.Resultado res = filtroLista.filtrar(todosJugadores, topLadder, modoTop(), g, soloVivos,
                porForma, formaAsc, porElo, fa, eloWatch, vinculosExpandidos);
        marcaFila.clear();
        marcaFila.putAll(res.marcaFila());
        vivoFamilia.clear();
        vivoFamilia.putAll(res.vivoFamilia());
        List<Player> vis = res.filas();
        Player invitadoActual = enlacePartidas.invitado();
        if (invitadoActual != null && containsPlayerId(invitadoActual.id())) { enlacePartidas.limpiarInvitado(); invitadoActual = null; }
        if (invitadoActual != null && !vistaActualId().equals(enlacePartidas.vistaDelInvitado())) { enlacePartidas.limpiarInvitado(); invitadoActual = null; }
        if (enlacePartidas.sujetosPanelVisible() && !vistaActualId().equals(enlacePartidas.vistaDeSujetos())) {
            enlacePartidas.limpiarSujetos();
            enlacePartidas.refrescarSujetos(List.of(), false);
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
        enlacePartidas.sincronizarSocket();
    }

    // ===== Ficha / grupos: infraestructura de vigilancia y refresco =====================================

    void quitarDeWatchlist(long id) {
        listaSeguidos.quitar(todosJugadores, id);
        savePlayers();
        aplicarFiltroGrupo();
        status.setText(t("Quitado de tu watchlist.", "Removed from your watchlist."));
    }

    public String grupoDeJugador(long id) { return listaSeguidos.grupoDeJugador(todosJugadores, id); }

    public boolean containsPlayerId(long id) { return listaSeguidos.contiene(todosJugadores, id); }

    /** Amplía la lista con todas las cuentas vinculadas de cada jugador (salvo hijas seleccionadas explícitamente). */
    /** Amplía la lista con todas las cuentas de cada familia presente. */
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

    /** Tooltip de la columna Jugador: si la cuenta es hermana de una familia, dice de quién. Null si no aplica. */
    /** Tooltip de la columna Jugador: si la cuenta es hermana de una familia,
     *  dice de quién. Null si no aplica. */
    public String tipCuentaVinculada(Match m) {
        String ref = enlacePartidas.refNombre(m);
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

    /** Casa un conjunto de cuentas bajo la misma familia (clave = menor id). */
    /** Un vinculo que agrupa a UNA sola cuenta es un fantasma (p. ej. de
     *  cuando el companion devolvía al propio jugador como vinculada). */
    public void sanearVinculosHuerfanos() { if (familiaSvc.sanearVinculosHuerfanos(todosJugadores)) savePlayers(); }

    public void marcarVinculo(Set<Long> ids) { if (familiaSvc.marcarVinculo(todosJugadores, ids)) { savePlayers(); aplicarFiltroGrupo(); } }   // el vínculo sobrevive al cierre

    public void refrescarWatchlist() {
        if (modoTop()) return;   // el top se alimenta del leaderboard y del río
        List<Player> objetivo = new ArrayList<>();
        for (int i = 0; i < playersModel.size(); i++) {
            Player p = playersModel.get(i);
            if (watchBarridos.add(p.id())) objetivo.add(p);
        }
        if (objetivo.isEmpty()) return;
        new SwingWorker<Void, BarridoVivos.Refresco>() {
            @Override protected Void doInBackground() {
                anfitrion.cargarEloAyer();
                for (Player p : objetivo) {
                    try {
                        BarridoVivos.Refresco r = barridoVivos.refrescar(p.id());
                        if (r.juegosNocturno() != null) {   // del snapshot nocturno: sin llamada (el socket dirá si está en partida)
                            gamesWatch.put(p.id(), r.juegosNocturno());
                            publish(r);
                            continue;
                        }
                        publish(r);
                    } catch (Exception ex) {
                        log("watchlist: fallo con " + p.name() + ": " + causa(ex));
                    }
                    anfitrion.dormir(pausaMs / 2);
                }
                return null;
            }
            @Override protected void process(List<BarridoVivos.Refresco> chunks) {
                for (BarridoVivos.Refresco r : chunks) {
                    if (r.vivo() != null) {
                        if (VIVO.marcarJugando(r.pid(), r.vivo()) && r.resumen() != null) VIVO.ponerInfo(r.pid(), r.resumen());
                    } else { VIVO.marcarFuera(r.pid()); }
                    if (r.elo() != null) eloWatch.put(r.pid(), r.elo());
                }
                actualizarIndicadoresVivos();
            }
        }.execute();
    }

    /** Vigilancia periódica de TODA la watchlist: solo detecta quién está jugando ahora. Se salta el tick si
     *  hay otra tarea en marcha. */
    /** Vigilancia periódica de TODA la watchlist (todos los grupos): solo
     *  detecta quién está jugando ahora; el ELO no se toca (solo al abrir).
     *  Se salta el tick si hay otra tarea en marcha. */
    public void vigilarVivos() {
        if (vigilando || progreso.isVisible() || todosJugadores.isEmpty()) return;
        vigilando = true;
        List<Player> objetivo = new ArrayList<>(todosJugadores);
        new SwingWorker<Void, BarridoVivos.Lote>() {
            @Override protected Void doInBackground() {
                final int LOTE = 25;   // 2 llamadas para un top 50, 4 para el top 100
                for (int d = 0; d < objetivo.size(); d += LOTE) {
                    List<Player> lote = objetivo.subList(d, Math.min(d + LOTE, objetivo.size()));
                    List<Long> idsLote = new ArrayList<>();
                    for (Player p : lote) idsLote.add(p.id());
                    try {
                        publish(barridoVivos.lote(idsLote));
                    } catch (Exception ex) {
                        log("vigilante: fallo con el lote " + (d / LOTE + 1) + ": " + causa(ex));
                    }
                    anfitrion.dormir(pausaMs / 2);
                }
                return null;
            }
            @Override protected void process(List<BarridoVivos.Lote> chunks) {
                boolean tablaTocada = false;
                for (BarridoVivos.Lote c : chunks) {
                    for (Long id : c.idsLote()) {
                        Long vm = c.vivos().get(id);
                        if (vm != null) { VIVO.marcarJugando(id, vm, c.infos().get(id)); }
                        else { VIVO.marcarFuera(id); }
                    }
                    for (Match fresco : c.terminadas())
                        for (Match m : all)
                            if (m.id == fresco.id && m.finished == null) {
                                m.finished = fresco.finished;
                                m.players = fresco.players;
                                tablaTocada = true;
                            }
                }
                if (tablaTocada) enlacePartidas.applyFilters();
                else enlacePartidas.repintarTabla();   // p. ej. una viva que cruza el umbral de fantasma
                actualizarIndicadoresVivos();
            }
            @Override protected void done() { vigilando = false; }
        }.execute();
    }

    /** ¿Hay alguien jugando ahora en el grupo? (null = en cualquiera). */
    private boolean grupoTieneVivo(String grupo) {
        for (Player p : todosJugadores)
            if ((grupo == null || p.grupo().equalsIgnoreCase(grupo)) && VIVO.jugando(p.id()))
                return true;
        return false;
    }

    /** Repinta lista, combo de grupos y el título («Watchlist — N en directo»). */
    public void refrescarAlturasWatch() {
        playersList.setFixedCellHeight(0);
        playersList.setFixedCellHeight(-1);
    }

    public void actualizarIndicadoresVivos() {
        refrescarAlturasWatch();   // las sublíneas nacen y mueren con los vivos, en toda vista
        playersList.repaint();
        grupoCombo.repaint();
        int nVivos = 0;
        for (Player p : todosJugadores) if (VIVO.jugando(p.id())) nVivos++;
        String g = grupoActivo();
        int nAmbito = 0;
        for (Player p : (modoTop() ? topLadder : todosJugadores))
            if ((modoTop() || g == null || p.grupo().equalsIgnoreCase(g)) && VIVO.jugando(p.id())) nAmbito++;
        if (soloVivosBtn != null)
            soloVivosBtn.setText("● " + t("Jugando", "Playing") + (nAmbito > 0 ? " (" + nAmbito + ")" : ""));
            soloVivosBtn.setToolTipText(null);   // sin tooltip: el chip se explica solo
            if (resumenWatch != null) {
                int totalAmbito = 0;
                for (int i = 0; i < playersModel.size(); i++) totalAmbito++;
                resumenWatch.setText(totalAmbito + t(" jugadores", " players") + " · " + nAmbito + t(" jugando", " playing"));
            }
        tituloWatch.setTitle(nVivos == 0 ? "Watchlist"
                : "Watchlist — " + nVivos + t(" jugando", " playing"));
        if (soloVivosBtn != null && soloVivosBtn.isSelected()) aplicarFiltroGrupo();
        if (watchPanel != null) watchPanel.repaint();
    }

    // ===== Alta de jugadores =============================================================================

    /** Tras fichar a alguien: si tiene cuentas vinculadas, ofrecer añadirlas todas al mismo grupo de una vez. */
    /** Tras fichar a alguien: si tiene cuentas vinculadas, ofrecer añadirlas
     *  todas al mismo grupo de una vez. */
    public void ofrecerVinculadasTrasAlta(long profileId, String nombre, String grupo) {
        new SwingWorker<List<Perfil.Vinculada>, Void>() {
            final Map<Long, Integer> elosV = new HashMap<>();
            @Override protected List<Perfil.Vinculada> doInBackground() {
                List<Perfil.Vinculada> vinc = perfiles.vinculadas(profileId);
                for (Perfil.Vinculada v : vinc) {
                    Integer e = perfiles.elo1v1(v.pid());
                    if (e != null) elosV.put(v.pid(), e);
                    anfitrion.dormir(pausaMs / 2);
                }
                return vinc;
            }
            @Override protected void done() {
                List<Perfil.Vinculada> vinc;
                try { vinc = get(); } catch (Exception e) { vinc = List.of(); }
                List<Perfil.Vinculada> nuevas = new ArrayList<>();
                for (Perfil.Vinculada v : vinc) if (!containsPlayerId(v.pid())) nuevas.add(v);
                if (nuevas.isEmpty()) return;
                StringBuilder sb = new StringBuilder();
                for (Perfil.Vinculada v : nuevas) sb.append(sb.isEmpty() ? "" : ", ").append(v.nombre());
                int r = JOptionPane.showConfirmDialog(ventana,
                        nombre + t(" tiene ", " has ") + nuevas.size()
                                + t(" cuentas vinculadas: ", " linked accounts: ") + sb
                                + t(".\n¿Añadirlas también al grupo «", ".\nAdd them to group “") + grupo
                                + t("»?", "” too?"),
                        t("Cuentas vinculadas", "Linked accounts"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (r != JOptionPane.YES_OPTION) return;
                Set<Long> familia = new HashSet<>();
                familia.add(profileId);
                for (Perfil.Vinculada v : nuevas) {
                    todosJugadores.add(new Player(v.pid(), v.nombre(), grupo));
                    familia.add(v.pid());
                }
                for (Perfil.Vinculada v : vinc) if (containsPlayerId(v.pid())) familia.add(v.pid());
                eloWatch.putAll(elosV);
                marcarVinculo(familia);
                savePlayers();
                rebuildGrupos();
                aplicarFiltroGrupo();
                refrescarWatchlist();
                status.setText(nuevas.size() + t(" cuentas vinculadas añadidas a «", " linked accounts added to “")
                        + grupo + "\u00bb.");
            }
        }.execute();
    }

    /** Un jugador concreto elegido de una sugerencia: las mismas tres opciones del buscador, sin repetir la búsqueda. */
    /** Un jugador concreto elegido de una sugerencia: en Perfil se abre directamente; en el resto, las mismas tres opciones del buscador, sin repetir la búsqueda. */
    public void jugadorElegido(long pid, String nombre) {
        if (anfitrion.perfilAbierto()) { navegacion.abrirPerfil(pid, nombre); return; }
        String verO = t("Ver sus partidas", "View their games"), perfO = t("Ver perfil", "View profile"), addO = t("Añadir al grupo…", "Add to group…"), canO = t("Cancelar", "Cancel");
        int r0 = JOptionPane.showOptionDialog(ventana, nombre + "  ·  " + pid, t("Resultados", "Results"), JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[]{ perfO, verO, addO, canO }, perfO);
        if (r0 == 0) navegacion.abrirPerfil(pid, nombre);
        else if (r0 == 1) {
            Player pl = new Player(pid, nombre, grupoDestino());
            enlacePartidas.fijarObjetivo(pl, vistaActualId());
            playersList.clearSelection(); aplicarFiltroGrupo(); enlacePartidas.mostrarDirectos(false); enlacePartidas.fetchMatches();
        } else if (r0 == 2) {
            Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); gs.add(GRUPO_GENERAL); for (Player x : todosJugadores) gs.add(x.grupo()); gs.addAll(gruposConfig());
            Object g = JOptionPane.showInputDialog(ventana, t("Grupo:", "Group:"), t("Añadir a la watchlist", "Add to the watchlist"), JOptionPane.PLAIN_MESSAGE, null, gs.toArray(), grupoDestino());
            if (g != null) ficharDesdeTop(new Player(pid, nombre, String.valueOf(g)), String.valueOf(g));
        }
    }

    /** ¿La respuesta a la búsqueda «q» ya no sirve porque el usuario siguió escribiendo? (el texto actual del
     *  campo ya no es «q»). Mismo criterio que el buscador de nick de la 1.1: comparar contra el texto YA
     *  recortado (trim), para que espacios al final no cuenten como «ha cambiado». */
    static boolean sugerenciaCaducada(String q, String textoActualDelCampo) {
        return !q.equals(textoActualDelCampo.trim());
    }

    /** Añade a {@code out} los resultados locales que no estuvieran ya (por id, out[i][0]): la API manda primero
     *  (conoce el nick actual), el índice local completa sin repetir. Mismo criterio que la 1.1 (addPlayerDialog). */
    static void agregarLocalesSinRepetir(List<String[]> out, List<String[]> locales) {
        Set<String> vistos = new HashSet<>();
        for (String[] r : out) vistos.add(r[0]);
        for (String[] r : locales) if (vistos.add(r[0])) out.add(new String[]{ r[0], r[1], r[2] + "  ·  " + r[0] });
    }

    public void addPlayerDialog(boolean soloVer) { addPlayerDialog(soloVer, null); }

    public void addPlayerDialog(boolean soloVer, String nickInicial) {
        String q = nickInicial != null && !nickInicial.isBlank() ? nickInicial
                : JOptionPane.showInputDialog(ventana, t("Nick del jugador:", "Player nick:"),
                        t("Buscar jugador", "Find player"), JOptionPane.PLAIN_MESSAGE);
        if (q == null || q.isBlank()) return;
        status.setText(t("Buscando \"", "Searching \"") + q.trim() + "\"…");
        new SwingWorker<List<String[]>, Void>() {
            @Override protected List<String[]> doInBackground() throws Exception {
                List<String[]> out = new ArrayList<>();
                for (dev.tirador.aoe2radar.model.PerfilEncontrado p : anfitrion.buscarPerfilesApi(q)) {
                    long id = p.pid();
                    if (id <= 0) continue;
                    String name = p.nombre();
                    String pais = p.pais();
                    anfitrion.aprenderPais(id, pais);
                    long games  = p.partidas();
                    out.add(new String[]{ String.valueOf(id), name,
                            name + (pais != null ? "  [" + pais + "]" : "") + "  ·  " + id
                                 + (games > 0 ? "  ·  " + games + " partidas" : "") });
                }
                agregarLocalesSinRepetir(out, busqueda.local(q.trim()));
                return out;
            }
            @Override protected void done() {
                try {
                    List<String[]> res = get();
                    if (res.isEmpty()) { status.setText(t("Sin resultados para \"", "No results for \"") + q.trim() + "\"."); return; }
                    String[] opciones = res.stream().map(r -> r[2]).toArray(String[]::new);
                    JComboBox<String> cbSel = new JComboBox<>(opciones);
                    JPanel pnl = new JPanel(new BorderLayout(0, 6));
                    pnl.add(new JLabel(t("Elige el jugador:", "Pick the player:")), BorderLayout.NORTH);
                    pnl.add(cbSel, BorderLayout.CENTER);
                    String verO = t("Ver sus partidas", "View their games");
                    String perfO = t("Ver perfil", "View profile");
                    String addO = t("Añadir al grupo…", "Add to group…");
                    String canO = t("Cancelar", "Cancel");
                    boolean perfilAbierto = anfitrion.perfilAbierto();
                    int r0;
                    if (perfilAbierto && res.size() == 1) r0 = 0;
                    else r0 = JOptionPane.showOptionDialog(ventana, pnl, t("Resultados", "Results"),
                            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                            perfilAbierto ? new Object[]{ perfO, canO } : new Object[]{ perfO, verO, addO, canO }, perfO);
                    if (perfilAbierto && r0 != 0) { status.setText(t("Listo.", "Ready.")); return; }
                    if (r0 != 0 && r0 != 1 && r0 != 2) { status.setText(t("Listo.", "Ready.")); return; }
                    String sel = (String) cbSel.getSelectedItem();
                    final boolean verPerfil = r0 == 0, verAhora = r0 == 1;
                    for (String[] r : res) if (r[2].equals(sel)) {
                        Player p = new Player(Long.parseLong(r[0]), r[1], grupoDestino());
                        if (verPerfil) { status.setText(t("Listo.", "Ready.")); navegacion.abrirPerfil(p.id(), p.name()); return; }
                        if (verAhora) {
                            enlacePartidas.fijarObjetivo(p, vistaActualId());
                            playersList.clearSelection();
                            aplicarFiltroGrupo();
                            new Thread(() -> {
                                Integer ei = perfiles.elo1v1(p.id());
                                if (ei != null) SwingUtilities.invokeLater(() -> {
                                    eloWatch.put(p.id(), ei);
                                    playersList.repaint();
                                    enlacePartidas.refrescarSujetos(enlacePartidas.ultimosSujetos(), enlacePartidas.invitado() != null);
                                });
                            }).start();
                            enlacePartidas.fetchMatches();
                            return;
                        }
                        String gElegido = elegirGrupoDialog(p.name());
                        if (gElegido == null) { status.setText(t("Listo.", "Ready.")); return; }
                        final Player pAdd = new Player(p.id(), p.name(), gElegido);
                        if (!containsPlayerId(pAdd.id())) {
                            todosJugadores.add(pAdd);
                            savePlayers();
                            rebuildGrupos();
                            aplicarFiltroGrupo();
                            refrescarWatchlist();
                        }
                        ofrecerVinculadasTrasAlta(pAdd.id(), pAdd.name(), pAdd.grupo());
                    }
                    status.setText(t("Listo.", "Ready."));
                } catch (Exception ex) {
                    status.setText(t("Error buscando: ", "Search error: ") + causa(ex));
                }
            }
        }.execute();
    }

    // ===== Persistencia ===================================================================================

    // Delegado a service.ListaSeguidos (cargar/guardar players.txt); la ventana conserva el disparo de
    // rebuildGrupos()/aplicarFiltroGrupo() (Swing) y muestra el error, si lo hay, en el status.
    // OJO (deuda ya existente en la 1.1, no se cambia aquí): esta E/S de disco es síncrona y loadPlayers/savePlayers
    // se llaman directamente desde el EDT (arranque, botones, menús): el disco se toca en el EDT.
    public void loadPlayers() {
        String error = listaSeguidos.cargar(todosJugadores);
        if (error != null) status.setText(error);
        rebuildGrupos();
        aplicarFiltroGrupo();
    }

    public void savePlayers() {
        String error = listaSeguidos.guardar(todosJugadores);
        if (error != null) status.setText(error);
    }

    // ===== Menú contextual =================================================================================

    /** Menú contextual de la watchlist y los tops, en cuatro bloques: en partida · perfil · watchlist · edición. */
    // visible para RegresionCapturas (abre el menú contextual para fotografiarlo)
    public void menuContextualWatchlist(Player p, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        long pid = p.id(); String nombre = anfitrion.nombreVisible(pid, p.name());
        // ---- 1. en partida ahora
        if (VIVO.jugando(pid)) {
            Match m = VIVO.partida(pid);
            JMenuItem cab = new JMenuItem(t("En partida ahora", "In a game now") + (m != null && m.map != null ? " · " + m.map : "") + (m != null && m.started != null ? " · " + dev.tirador.aoe2radar.util.Formato.reloj(Duration.between(m.started, Instant.now())) : ""));
            cab.setEnabled(false); cab.setFont(cab.getFont().deriveFont(Font.BOLD));
            menu.add(cab);
            JMenuItem esp = new JMenuItem(t("Espectar en directo", "Spectate live"));
            esp.addActionListener(a -> anfitrion.espectar(p));
            menu.add(esp);
            if (anfitrion.rutaCaptureAge() != null) {
                JMenuItem espCa = new JMenuItem(t("Espectar con CaptureAge", "Spectate with CaptureAge"));
                espCa.addActionListener(a -> { anfitrion.lanzarCaptureAge(null); anfitrion.espectar(p); });
                menu.add(espCa);
            }
            MatchPlayer yo = null; if (m != null) for (MatchPlayer mp : m.players) if (mp.id == pid) yo = mp;
            if (m != null && yo != null) {
                List<MatchPlayer> aliados = new ArrayList<>(), rivales = new ArrayList<>();
                for (MatchPlayer mp : m.players) { if (mp.id == pid) continue; if (mp.team == yo.team) aliados.add(mp); else rivales.add(mp); }
                if (m.players.size() == 2 && rivales.size() == 1) menu.add(submenuJugadorPartida(rivales.get(0), t("Rival: ", "Opponent: ")));
                else {
                    if (!aliados.isEmpty()) { JMenu al = new JMenu(t("Aliados", "Allies")); for (MatchPlayer mp : aliados) al.add(submenuJugadorPartida(mp, "")); menu.add(al); }
                    JMenu rv = new JMenu(t("Rivales", "Opponents")); for (MatchPlayer mp : rivales) rv.add(submenuJugadorPartida(mp, "")); menu.add(rv);
                }
            } else {
                EstadoVivo.Rival riv = VIVO.rival(pid);
                if (riv != null) { JMenu rivalMenu = menus.deJugador(riv.pid(), riv.nombre()); rivalMenu.setText(t("Rival: ", "Opponent: ") + riv.nombre()); menu.add(rivalMenu); }
            }
            menu.addSeparator();
        }
        // ---- 2. perfil
        JMenuItem perf = new JMenuItem(t("Perfil", "Profile"));
        perf.addActionListener(a -> navegacion.abrirPerfil(pid, nombre));
        menu.add(perf);
        JMenuItem perfN = new JMenuItem(t("Perfil en pestaña nueva", "Profile in a new tab"));
        perfN.addActionListener(a -> navegacion.abrirPerfilEnPestana(pid, nombre));
        menu.add(perfN);
        menu.add(menus.perfilNavegador(pid));
        if (twitchLive.containsKey(pid)) {
            JMenuItem tw = new JMenuItem(t("Ver directo en Twitch", "Watch live on Twitch"));
            tw.addActionListener(a -> anfitrion.abrirUrl("https://twitch.tv/" + twitchLive.get(pid)[0]));
            menu.add(tw);
        }
        menu.addSeparator();
        // ---- 3. watchlist
        if (modoTop()) {
            boolean yaSeguido = todosJugadores.stream().anyMatch(x -> x.id() == pid);
            if (yaSeguido) {
                JMenuItem quitarW = new JMenuItem(t("Quitar de mi watchlist", "Remove from my watchlist"));
                quitarW.addActionListener(a -> quitarDeWatchlist(pid));
                menu.add(quitarW);
            } else {
                JMenu anadir = new JMenu(t("Añadir a mi watchlist", "Add to my watchlist"));
                Set<String> gsTop = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                gsTop.add(GRUPO_GENERAL);
                for (Player x : todosJugadores) gsTop.add(x.grupo());
                gsTop.addAll(gruposConfig());
                for (String g : gsTop) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> ficharDesdeTop(p, g)); anadir.add(it); }
                anadir.addSeparator();
                JMenuItem nuevoGT = new JMenuItem(t("+ Nuevo grupo…", "+ New group…"));
                nuevoGT.addActionListener(a -> { String g = elegirGrupoDialog(p.name()); if (g != null) ficharDesdeTop(p, g); });
                anadir.add(nuevoGT);
                menu.add(anadir);
                List<Player> selTop = playersList.getSelectedValuesList();
                if (selTop.size() > 1 && selTop.contains(p)) {   // varios seleccionados: ficharlos todos de golpe
                    JMenu anadirVarios = new JMenu(t("Añadir los ", "Add the ") + selTop.size() + t(" seleccionados a", " selected to"));
                    for (String g : gsTop) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> ficharVarios(selTop, g)); anadirVarios.add(it); }
                    anadirVarios.addSeparator();
                    JMenuItem nuevoGV = new JMenuItem(t("+ Nuevo grupo…", "+ New group…"));
                    nuevoGV.addActionListener(a -> { String g = elegirGrupoDialog(selTop.size() + t(" jugadores", " players")); if (g != null) ficharVarios(selTop, g); });
                    anadirVarios.add(nuevoGV);
                    menu.add(anadirVarios);
                }
            }
        } else {
            JMenu mover = new JMenu(t("Mover a grupo", "Move to group"));
            Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            gs.add(GRUPO_GENERAL);
            gs.addAll(gruposConfig());
            for (Player x : todosJugadores) gs.add(x.grupo());
            for (String g : gs) { if (g.equalsIgnoreCase(p.grupo())) continue; JMenuItem it = new JMenuItem(g); it.addActionListener(a -> moverJugador(p, g)); mover.add(it); }
            JMenuItem nuevoG = new JMenuItem(t("Nuevo grupo…", "New group…"));
            nuevoG.addActionListener(a -> {
                String nombreG = JOptionPane.showInputDialog(ventana, t("Nombre del grupo nuevo:", "New group name:"), t("Nuevo grupo", "New group"), JOptionPane.PLAIN_MESSAGE);
                if (nombreG != null && !nombreG.isBlank()) { String limpio = limpiarGrupo(nombreG); registrarGrupo(limpio); moverJugador(p, limpio); }
            });
            if (mover.getItemCount() > 0) mover.addSeparator();
            mover.add(nuevoG);
            menu.add(mover);
            JMenuItem quitar = new JMenuItem(t("Quitar de la Watchlist", "Remove from Watchlist"));
            quitar.addActionListener(a -> { playersModel.removeElement(p); todosJugadores.removeIf(x -> x.id() == pid); savePlayers(); rebuildGrupos(); actualizarIndicadoresVivos(); });
            menu.add(quitar);
            List<Player> selW = playersList.getSelectedValuesList();
            if (selW.size() > 1 && selW.contains(p)) {   // varios seleccionados: mover o quitar de golpe
                JMenu moverVarios = new JMenu(t("Mover los ", "Move the ") + selW.size() + t(" seleccionados a", " selected to"));
                for (String g : gruposExistentes()) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> moverVarios(selW, g)); moverVarios.add(it); }
                moverVarios.addSeparator();
                JMenuItem nuevoGM = new JMenuItem(t("+ Nuevo grupo…", "+ New group…"));
                nuevoGM.addActionListener(a -> { String g = elegirGrupoDialog(selW.size() + t(" jugadores", " players")); if (g != null) moverVarios(selW, g); });
                moverVarios.add(nuevoGM);
                menu.add(moverVarios);
                JMenuItem quitarVarios = new JMenuItem(t("Quitar los ", "Remove the ") + selW.size() + t(" seleccionados del grupo", " selected from the group"));
                quitarVarios.addActionListener(a -> {
                    Set<Long> ids = new HashSet<>(); for (Player x : selW) ids.add(x.id());
                    todosJugadores.removeIf(x -> ids.contains(x.id()));
                    savePlayers(); rebuildGrupos(); aplicarFiltroGrupo();
                    status.setText(ids.size() + t(" jugadores quitados.", " players removed."));
                });
                menu.add(quitarVarios);
            }
        }
        JMenuItem vinc = new JMenuItem(t("Cuentas vinculadas…", "Linked accounts…"));
        vinc.addActionListener(a -> dialogos.mostrarVinculadas(pid, p.name()));
        menu.add(vinc);
        menu.addSeparator();
        // ---- 4. edición
        JMenuItem alias = new JMenuItem(t("Mostrar como…", "Show as…"));
        alias.addActionListener(a -> dialogos.pedirAlias(pid, p.name()));
        menu.add(alias);
        JMenuItem nota = new JMenuItem(t("Nota…", "Note…"));
        nota.addActionListener(a -> dialogos.pedirNota(pid, p.name()));
        menu.add(nota);
        if (dialogos.notaDe(pid) != null) { JMenuItem bn = new JMenuItem(t("Borrar nota", "Delete note")); bn.addActionListener(a -> dialogos.borrarNota(pid, p.name())); menu.add(bn); }
        JMenuItem nicks = new JMenuItem(t("Nicks anteriores…", "Previous names…"));
        nicks.addActionListener(a -> dialogos.nicksAnteriores(pid, p.name()));
        menu.add(nicks);
        menu.show(playersList, e.getX(), e.getY());
    }

    /** Un jugador de la partida en curso, como submenú: perfil, pestaña nueva, añadir a la watchlist. */
    private JMenu submenuJugadorPartida(MatchPlayer mp, String prefijo) {
        String nombre = anfitrion.nombreVisible(mp.id, mp.name);
        Integer e1 = menus.elo1v1Conocido(mp.id);
        JMenu sub = new JMenu(prefijo + nombre + (e1 != null ? "  1v1 " + e1 : "") + (mp.civ != null && !mp.civ.isBlank() ? "  ·  " + mp.civ : ""));
        sub.setIcon(iconoBandera(anfitrion.paisDe(mp.id)));
        if (((e1 == null && eloSesion.conocido(mp.id) == null) || eloSesion.caducado(mp.id)) && eloSesion.reservar(mp.id)) new Thread(() -> { long pedido = eloSesion.ahora(); Integer e = perfiles.elo1v1(mp.id); eloSesion.apuntar(mp.id, e, pedido); if (e != null && e > 0) SwingUtilities.invokeLater(() -> sub.setText(prefijo + nombre + "  1v1 " + e + (mp.civ != null && !mp.civ.isBlank() ? "  ·  " + mp.civ : ""))); }, "elo-1v1").start();
        JMenuItem perf = new JMenuItem(t("Perfil", "Profile")); perf.addActionListener(a -> navegacion.abrirPerfil(mp.id, nombre)); sub.add(perf);
        JMenuItem perfN = new JMenuItem(t("Perfil en pestaña nueva", "Profile in a new tab")); perfN.addActionListener(a -> navegacion.abrirPerfilEnPestana(mp.id, nombre)); sub.add(perfN);
        if (!containsPlayerId(mp.id)) {
            JMenu anadir = new JMenu(t("Añadir a mi watchlist", "Add to my watchlist"));
            Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); gs.add(GRUPO_GENERAL); for (Player x : todosJugadores) gs.add(x.grupo()); gs.addAll(gruposConfig());
            for (String g : gs) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> ficharDesdeTop(new Player(mp.id, mp.name, g), g)); anadir.add(it); }
            sub.add(anadir);
        }
        return sub;
    }

    // ===== Tarjeta de perfil flotante (hoy inerte: el hover-timer nunca dispara) ==========================

    private boolean hoverProcede() {
        Component c = hoverAncla != null ? hoverAncla : playersList;
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == ventana
                && MenuSelectionManager.defaultManager().getSelectedPath().length == 0
                && c.isShowing() && c.getMousePosition() != null;
    }

    public void ocultarHoverCard() { ocultarHoverCard(false); }

    public void ocultarHoverCard(boolean forzar) {
        if (!forzar && cardFijada) return;   // la card del clic derecho no la mata pasear el ratón
        if (hoverTimer != null) hoverTimer.stop();
        if (hoverCard != null) { hoverCard.dispose(); hoverCard = null; }
        cardFijada = false;
        hoverPid = 0;
    }

    /** Muestra la tarjeta de perfil de un jugador en la posición dada. */
    private void mostrarPerfilCard(long pid, String nombre, Point enPantalla) { mostrarPerfilCard(pid, nombre, enPantalla, false); }

    private void mostrarPerfilCard(long pid, String nombre, Point enPantalla, boolean fijar) {
        Object[] cache = anfitrion.tarjetaPerfilCache(pid);
        if (cache != null) {
            pintarCard((String) cache[0], (int[]) cache[1], enPantalla, pid, fijar);
            return;
        }
        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() {
                String pais = "", clan = "";
                long games = 0;
                Integer rating = null, maxRating = null, wins = null, losses = null;
                try {
                    Perfil pf = anfitrion.perfilApi(pid);
                    anfitrion.aprenderCanal(pid, pf.canal());
                    String c = pf.pais();
                    anfitrion.aprenderPais(pid, c);
                    if (c != null && !"null".equals(c)) pais = c.toUpperCase();
                    String cl = pf.clan();
                    if (cl != null && !"null".equals(cl)) clan = cl;
                    games = pf.partidas();
                    for (Perfil.Ladder lb : pf.ladders()) {
                        String lid = String.valueOf(lb.id());
                        if (!"rm_1v1".equals(lid) && !"3".equals(lid)) continue;
                        if (lb.rating() != null) rating = lb.rating();
                        if (lb.ratingMax() != null) maxRating = lb.ratingMax();
                        if (lb.ganadas() != null) wins = lb.ganadas();
                        if (lb.perdidas() != null) losses = lb.perdidas();
                        break;
                    }
                } catch (Exception ex) {
                    log("perfil card: fallo con " + pid + ": " + causa(ex));
                }
                int[] spark = null;
                boolean pocos1v1 = false;
                try {
                    List<Integer> serie = new ArrayList<>();
                    for (int pag = 1; pag <= 2 && serie.size() <= 15; pag++) {
                        anfitrion.dormir(pausaMs / 2);
                        dev.tirador.aoe2radar.model.PaginaPartidas ms = anfitrion.paginaApi(pid, pag, perPage);
                        if (ms.brutas() == 0) break;
                        for (Match m : ms.partidas()) {
                            if (m.finished == null || m.players.size() != 2
                                    || m.mode == null || !m.mode.startsWith("1v1 Random")) continue;
                            for (MatchPlayer mp : m.players)
                                if (mp.id == pid && mp.rating != null) serie.add(mp.rating);
                        }
                    }
                    if (serie.size() > 10) serie = serie.subList(10, serie.size());   // sin la forma fresca
                    if (serie.size() >= 4) {
                        Collections.reverse(serie);   // cronológico
                        spark = serie.stream().mapToInt(Integer::intValue).toArray();
                    } else pocos1v1 = true;
                } catch (Exception ex) {
                    log("perfil card: sparkline falló con " + pid + ": " + causa(ex));
                }
                StringBuilder h = new StringBuilder("<html><b>").append(escapeHtml(nombre)).append("</b>");
                if (!pais.isBlank()) h.append("  · ").append(pais);
                if (!clan.isBlank()) h.append("  · ").append(escapeHtml(clan));
                h.append("<br>");
                if (rating != null) h.append(t("ELO 1v1: <b>", "1v1 ELO: <b>")).append(rating).append("</b>");
                if (maxRating != null) h.append(t("  · máx ", "  · peak ")).append(maxRating);
                h.append("<br>");
                if (wins != null && losses != null && wins + losses > 0)
                    h.append(t("Winrate 1v1: ", "1v1 winrate: "))
                     .append(Math.round(wins * 100.0 / (wins + losses))).append("% (")
                     .append(wins + losses).append(t(" partidas)", " games)"));
                else if (games > 0) h.append(games).append(t(" partidas jugadas", " games played"));
                if (spark != null)
                    h.append("<br><font size='2' color='gray'>")
                     .append(t("Rating (hasta hace ~10 partidas):", "Rating (up to ~10 games ago):"))
                     .append("</font>");
                else if (pocos1v1)
                    h.append("<br><font size='2' color='gray'>")
                     .append(t("(pocos 1v1 recientes para la gráfica)", "(too few recent 1v1s for the chart)"))
                     .append("</font>");
                return new Object[]{ h.append("</html>").toString(), spark };
            }
            @Override protected void done() {
                try {
                    Object[] r = get();
                    anfitrion.tarjetaPerfilGuardar(pid, new Object[]{ r[0], r[1] });
                    if (fijar || ((hoverPid == pid || hoverPid == 0) && hoverProcede()))
                        pintarCard((String) r[0], (int[]) r[1], enPantalla, pid, fijar);
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    private void pintarCard(String html, int[] spark, Point enPantalla, long pid, boolean fijar) {
        ocultarHoverCard(true);
        cardFijada = fijar;
        hoverPid = pid;
        hoverCard = new JWindow(ventana);
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") != null
                        ? UIManager.getColor("Component.borderColor") : Color.GRAY),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        p.add(new JLabel(html), BorderLayout.CENTER);
        if (spark != null) p.add(new SparkPanel(spark), BorderLayout.SOUTH);
        hoverCard.add(p);
        hoverCard.pack();
        hoverCard.setLocation(enPantalla.x + 14, enPantalla.y + 10);
        hoverCard.setVisible(true);
    }

    /** Panel con la mini gráfica del rating (termina 10 partidas atrás). */
    static class SparkPanel extends JPanel {
        final int[] datos;
        SparkPanel(int[] datos) { this(datos, 200, 44); }
        SparkPanel(int[] datos, int w, int h) {
            this.datos = datos;
            setPreferredSize(new Dimension(w, h));
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (datos == null || datos.length < 2) return;
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight() - 14;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int v : datos) { min = Math.min(min, v); max = Math.max(max, v); }
            if (max == min) max = min + 1;
            g.setColor(temaOscuroActivo ? new Color(0xFF, 0xC9, 0x4D) : new Color(0xB0, 0x78, 0x00));
            int n = datos.length;
            int px = -1, py = -1;
            for (int i = 0; i < n; i++) {
                int x = (int) Math.round(i * (w - 4) / (double) (n - 1)) + 2;
                int y = 4 + (int) Math.round((max - datos[i]) * (h - 8) / (double) (max - min));
                if (px >= 0) g.drawLine(px, py, x, y);
                px = x; py = y;
            }
            g.setFont(getFont().deriveFont(Font.PLAIN, 10f));
            g.setColor(UIManager.getColor("Label.disabledForeground") != null
                    ? UIManager.getColor("Label.disabledForeground") : Color.GRAY);
            g.drawString(String.valueOf(min), 2, getHeight() - 2);
            String sMax = String.valueOf(max);
            g.drawString(sMax, w - g.getFontMetrics().stringWidth(sMax) - 2, getHeight() - 2);
        }
    }
}
