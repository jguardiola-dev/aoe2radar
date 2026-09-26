package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.RatingsService;
import dev.tirador.aoe2radar.service.StatsService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;

import static dev.tirador.aoe2radar.service.NombresStats.nombreCivStats;
import static dev.tirador.aoe2radar.service.NombresStats.nombreMapaClave;
import static dev.tirador.aoe2radar.service.NombresStats.tramoNombre;
import static dev.tirador.aoe2radar.service.ProfileService.ACT_MAS_PAGINAS;
import static dev.tirador.aoe2radar.service.ProfileService.ACT_MAX_PAGINAS;
import static dev.tirador.aoe2radar.service.ProfileService.ACT_MIN;
import static dev.tirador.aoe2radar.service.ProfileService.ACT_PAGINAS_RAPIDAS;
import static dev.tirador.aoe2radar.service.ReglasPartida.LADDER_IDS;
import static dev.tirador.aoe2radar.service.ReglasPartida.franjaDe;
import static dev.tirador.aoe2radar.service.ReglasPartida.franjasCentradas;
import static dev.tirador.aoe2radar.service.ReglasPartida.modoPrincipal;
import static dev.tirador.aoe2radar.service.ReglasPartida.posicionEnEquipo;
import static dev.tirador.aoe2radar.service.ReglasPartida.tramoDuracion;
import static dev.tirador.aoe2radar.service.ConsultasLadder.ladderNombre;
import static dev.tirador.aoe2radar.service.ConsultasLadder.percentilRango;
import static dev.tirador.aoe2radar.service.ConsultasLadder.percentilRating;
import static dev.tirador.aoe2radar.ui.Componentes.colorHex;
import static dev.tirador.aoe2radar.ui.Componentes.colorSecundario;
import static dev.tirador.aoe2radar.ui.Componentes.colorSecundarioHex;
import static dev.tirador.aoe2radar.ui.Componentes.colorWr;
import static dev.tirador.aoe2radar.ui.Componentes.listaVertical;
import static dev.tirador.aoe2radar.ui.Componentes.tituloSeccion;
import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.ui.Iconos.iconoCiv;
import static dev.tirador.aoe2radar.ui.Iconos.iconoMapa;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.Formato.miles;
import static dev.tirador.aoe2radar.util.Formato.pct1;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.lng;

/**
 * La pestaña Perfil (card "perfil"): la página de un jugador — cabecera con ELO/rango/Top % por ladder, forma y
 * gráfica del año, cuentas vinculadas; y su actividad del último año (calendario, horas, meses, civs, mapas,
 * rivales, aliados, tramos de duración y de ELO rival), con pestañas para tener varios perfiles abiertos. El
 * cuadro de diálogo «Cara a cara» vive aparte, en {@link CaraACaraDialogo}. Sale de SpoilerFreeRecs
 * (construirPanelPerfil, abrirPerfil y compañía de la 1.1) tal cual: la parte de red está en
 * {@link PerfilPresenter}, del que esta vista es la {@link PerfilPresenter.Pantalla}.
 * <p>Lo que la vista necesita de la ventana (selección de la watchlist, notas/alias, navegación general, la
 * pestaña Partidas…) se lo pide a {@link Anfitrion}. Los mapas compartidos con otras vistas/hilos de fondo
 * (actividad en caché, snapshot de ayer, ELO de la watchlist, Twitch en directo) llegan por constructor: son la
 * MISMA instancia que usa el resto de la app (igual que {@code twitchLive} en Directos/Live now).
 */
public final class PerfilView implements PerfilPresenter.Pantalla {

    /** Lo que esta vista pide a la ventana: watchlist, notas/alias/vínculos, navegación general y la pestaña Partidas. */
    public interface Anfitrion {
        List<Player> seleccionWatchlist();
        boolean estaEnWatchlist(long pid);
        String paisDe(long pid);
        String nombreVisible(long pid, String nombre);
        void ficharDesdeTop(long pid, String nombre, String grupo);
        List<String> gruposDeJugadores();
        List<String> gruposGuardados();
        String grupoGeneral();
        /** ¿El último clic en una lista de jugadores llevaba Ctrl? (lo marca Listas, campo compartido de la ventana). */
        boolean ultimoClicFueCtrl();

        void pedirAlias(long pid, String nombreOriginal);
        void pedirNota(long pid, String nombre);
        void borrarNota(long pid, String nombre);
        void mostrarVinculadas(long pid, String nombre);
        void nicksAnteriores(long pid, String nombre);

        void abrirUrl(String url);
        void registrarDestino(long pid, String nombre);
        /** Repinta el texto de «Buscar partidas (X)» (fetchBtn/guiaBtn) y objetivoEtiqueta según quién está seleccionado o abierto. */
        void actualizarTextoBuscar();
        JToggleButton crearBotonPestana(String texto, Icon icono);
        void traerAlFrente();
        void mostrarEstadoGlobal(String texto);
        /** Cierra Perfil y vuelve a Directos (el botón «×» de la cabecera). */
        void cerrarPerfil();

        boolean enCursoReal(Match m);
        boolean confirmarEspectar(String nombre);
        void espectarPartida(long matchId);

        /** Vuelca estas partidas en la pestaña Partidas, con este jugador como sujeto (sin resultado hasta pedirlo). */
        void cargarPartidasEnTabla(List<Match> partidas, Player sujeto);
        /** «Ver sus partidas»: fuerza este jugador como objetivo de la búsqueda normal. */
        void buscarPartidasDe(long pid, String nombre);
        void descargarSinCambiarVista(List<Match> partidas, boolean enviar, Runnable alTerminar);
    }

    // ----- colaboradores inyectados (mismos que en la ventana, por constructor) -----
    final ProfileService perfiles;
    final RatingsService ratings;
    final BusquedaPerfiles busqueda;
    final StatsService stats;
    final EstadoVivo vivo;
    final MenusJugador menus;
    final Navegacion navegacion;
    final TechTreeView techTree;
    final Listas listas;
    final Tareas tareas;
    final Anfitrion anfitrion;
    final Map<Long, Actividad> actividadCache;
    final Map<Long, Integer> eloWatch;
    final Map<Long, String> aliases;
    final Map<Long, String> canalDe;
    final Map<Long, int[]> eloAyer;
    final Map<Long, String[]> nombresAyer;
    final Map<Long, String[]> twitchLive;
    final LongFunction<String> notaDeFn;
    final Path perfilesDir;
    final LongFunction<Actividad> cargarDeDisco;
    final int actDias;

    private final PerfilPresenter presenter;
    private CaraACaraDialogo caraACara;

    // ----- estado y componentes propios (mismos nombres que la 1.1) -----
    private JPanel actividadPanel, actCabecera, actChips;
    private JLabel actTitulo; public JLabel actEstado;   // visible para RegresionCapturas
    JLabel actSubtitulo;   // de paquete: lo mira PerfilViewTest
    private JLabel actPista;
    private JComboBox<String> actModoCombo;
    private PanelScrollable actCuerpo;
    private JTextField perfilBusca;
    private JPopupMenu perfilPopup;
    private javax.swing.Timer perfilDebounce;
    private JProgressBar actProgreso;
    private JButton actMasBtn;
    boolean actividadAbierta, actRellenandoModos;
    /** F5 (1.3): volatile porque PerfilPresenter.precalentar lo lee desde su hilo de fondo ("perfiles-precarga")
     *  para no competir con una carga pedida por el usuario; se escribe en el EDT. */
    volatile boolean actCargando;
    long actPid; String actNombre = "", actModo = "*";
    /** Fila 28 (C1+C2): sube en cada apertura de verdad (alAbrir); ver generacion() y PerfilPresenter.abrirBase. */
    private long aperturaGeneracion;
    public CalendarioPanel actCalendario;   // visible para RegresionCapturas
    public BarrasActividad actSemana;       // visible para RegresionCapturas
    private BarrasActividad actHoras;
    public BarrasActividad actMeses;        // visible para RegresionCapturas
    private JPanel actSpark, actPosicion, actUltimos30Civs, actUltimos30Mapas, actDuracionFila;
    private GraficaElo actGrafica;
    private JComboBox<String> actPeriodoCombo;
    private LocalDate actDesde, actHasta;
    private int actPeriodoIdx;
    private JPanel actTarjetas, actCivs, actCivsRival, actMapas, actRivales, actAliados, actTramos;
    private boolean actOrigenSfr;
    private String actHastaSfr;
    private JButton actHoyBtn;
    private JLabel actHastaLabel;
    private JPanel actVinculadasPanel;
    private JLabel actNotaLinea;
    private String actNombreReal = "";
    // Fila 129: se escribe desde el hilo de fondo "perfil-hoy" (PerfilPresenter.actualizarHoy llama a
    // marcarVinculadasPedidas antes de traerHoy, directamente en el hilo, sin pasar por enUi) y se lee en el
    // EDT (actPintarVinculadas, el clic de "comprobar"): con un HashSet normal ese cruce de hilos no es seguro.
    private final Set<Long> vinculadasPedidas = ConcurrentHashMap.newKeySet();
    private JDialog histDialogo; private int histPagina; private String histModo = "*";
    private long historialEnTabla;   // pid cuyo histórico está en la tabla (para que la pestaña Partidas no vuelva a buscar)
    private javax.swing.Timer perfilSeleccionTimer;
    final Map<String, Integer> ordenListas = new HashMap<>();   // título → 0 partidas, 1 winrate, 2 A-Z

    // ----- pestañas de perfil -----
    private static final int PERFIL_MAX_PESTANAS = 6;
    public final List<Object[]> perfilPestanas = new ArrayList<>();   // visible para RegresionCapturas: {pid Long, nombre String}
    public int perfilPestanaActiva = -1;                              // visible para RegresionCapturas
    private JPanel perfilTira;

    public PerfilView(ProfileService perfiles, RatingsService ratings, BusquedaPerfiles busqueda, StatsService stats,
                       EstadoVivo vivo, MenusJugador menus, Navegacion navegacion, TechTreeView techTree, Listas listas,
                       Tareas tareas, Anfitrion anfitrion,
                       Map<Long, Actividad> actividadCache, Map<Long, Integer> eloWatch, Map<Long, String> aliases,
                       Map<Long, String> canalDe, Map<Long, int[]> eloAyer, Map<Long, String[]> nombresAyer,
                       Map<Long, String[]> twitchLive, LongFunction<String> notaDeFn,
                       Path perfilesDir, LongFunction<Actividad> cargarDeDisco, int actDias) {
        this.perfiles = perfiles; this.ratings = ratings; this.busqueda = busqueda; this.stats = stats; this.vivo = vivo;
        this.menus = menus; this.navegacion = navegacion; this.techTree = techTree; this.listas = listas; this.tareas = tareas;
        this.anfitrion = anfitrion;
        this.actividadCache = actividadCache; this.eloWatch = eloWatch; this.aliases = aliases; this.canalDe = canalDe;
        this.eloAyer = eloAyer; this.nombresAyer = nombresAyer; this.twitchLive = twitchLive; this.notaDeFn = notaDeFn;
        this.perfilesDir = perfilesDir; this.cargarDeDisco = cargarDeDisco; this.actDias = actDias;
        this.presenter = new PerfilPresenter(perfiles, ratings, busqueda, tareas, eloWatch, actividadCache, this);
        construirPanelPerfil();
    }

    /** El panel de la pestaña, para el CardLayout de la ventana y para esFondoDeseleccionable. */
    public JPanel panel() { return actividadPanel; }

    /** ¿Perfil está abierto? Lo leen otras vistas/menús de la ventana para «apagarse» entre sí. */
    public boolean abierto() { return actividadAbierta; }
    /** El pid actualmente mostrado (0 si no hay ninguno). */
    public long pidAbierto() { return actPid; }
    /** Fila 28 (C1+C2): la generación de la apertura en curso; PerfilPresenter.abrirBase la compara al volver del hilo de fondo. */
    public long generacion() { return aperturaGeneracion; }
    /** El nombre del perfil actualmente mostrado. */
    public String nombreAbierto() { return actNombre; }
    /** El pid cuyo histórico está volcado en la pestaña Partidas (0 si ninguno todavía). */
    public long historialEnTabla() { return historialEnTabla; }
    /** Otra vista se abre: Perfil se cierra (mismo efecto que el botón «×», sin tocar el CardLayout). */
    public void cerrar() { actividadAbierta = false; }

    /** Calienta en segundo plano los perfiles ya guardados: llamar una vez al arrancar la ventana. */
    public void precalentar() { presenter.precalentar(perfilesDir, cargarDeDisco); }

    // ===== cromo: lo llama la ventana desde su abrirPerfil/abrirPerfilEnPestana =====================

    /** Marca Perfil como abierto: lo llama la ventana ANTES del CardLayout.show, en el mismo punto que la 1.1
     *  (actividadAbierta = true;), antes de subirArriba/taparResultados/apagarForma. */
    public void marcarAbierta() { actividadAbierta = true; }

    /** La parte de vista de abrir un perfil (pid 0 = página vacía con el buscador); el cromo (botones, CardLayout…) ya lo hizo la ventana. */
    public void alAbrir(long pid, String nombre) {
        if (pid <= 0) { aperturaGeneracion++; /* la página vacía también invalida una lectura de fondo pendiente (fila 28) */ actTitulo.setText(t("Perfil", "Profile")); actEstado.setText(""); actProgreso.setVisible(false); actMostrarCuerpo(false); perfilBusca.requestFocusInWindow(); refrescarTiraPerfil(); return; }
        perfilContabilizarPestana(pid, nombre);
        anfitrion.registrarDestino(pid, nombre);
        if (actCargando && pid == actPid) return;
        // Fila 28 (C1+C2, revisión): esta apertura pasa a ser LA de verdad; una generación nueva invalida
        // cualquier lectura de fondo que estuviera pendiente de una apertura anterior (aunque fuera del mismo
        // pid: A→B→A rápido, o abrir dos veces seguidas el mismo perfil antes de que la primera lectura vuelva).
        aperturaGeneracion++;
        actPid = pid; actNombre = nombre;
        actTitulo.setText(t("Perfil de ", "Profile of ") + nombre);
        actNombreReal = nombre;
        anfitrion.actualizarTextoBuscar();
        actMasBtn.setVisible(false);
        // C1: si ya está en memoria (actividadCache es la MISMA ConcurrentHashMap que usa perfiles.actividad(pid)
        // por debajo, ver HistorialPerfil.actividad), no hace falta ir a un hilo: se pinta en el acto, como la 1.1,
        // sin el parpadeo de «Descargando…». Solo cuando no está en memoria perfiles.actividad(pid) tendría que
        // leer disco (HistorialDisco.cargarActividad), y eso sí sale del EDT vía PerfilPresenter.abrirBase.
        Actividad base = actividadCache.get(pid);
        if (base != null) { baseLista(base, perfiles.fichaConocida(pid)); return; }
        actMostrarCuerpo(false);
        actPista.setText(t("Descargando el historial… la página se irá rellenando sola.", "Downloading the history… the page will fill itself in."));
        presenter.abrirBase(pid, aperturaGeneracion);
    }

    /** Con Perfil abierto, seleccionar a alguien en la watchlist abre su perfil (lo llama el listener de playersList). */
    public void sincronizarSeleccion() {
        if (!actividadAbierta || actividadPanel == null || !actividadPanel.isShowing()) return;
        if (perfilSeleccionTimer == null) {
            perfilSeleccionTimer = new javax.swing.Timer(500, ev -> {   // medio segundo de respiro: pasar por diez filas con las flechas no dispara diez cargas
                if (!actividadAbierta) return;
                List<Player> sel = anfitrion.seleccionWatchlist();
                if (sel.isEmpty()) return;
                Player p = sel.get(0);
                if (p.id() != actPid) navegacion.abrirPerfil(p.id(), anfitrion.nombreVisible(p.id(), p.name()));
            });
            perfilSeleccionTimer.setRepeats(false);
        }
        perfilSeleccionTimer.restart();
    }

    /** Abre a un jugador en una pestaña NUEVA (Ctrl+clic, «+»); si ya está abierto, se activa la suya. Lo llama la ventana desde su override de Navegacion. */
    public void abrirEnPestanaNueva(long pid, String nombre) {
        int i = indicePestana(pid);
        if (i >= 0) { perfilPestanaActiva = i; navegacion.abrirPerfil(pid, nombre); return; }
        if (perfilPestanas.size() >= PERFIL_MAX_PESTANAS) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(actividadPanel), t("Ya hay " + PERFIL_MAX_PESTANAS + " perfiles abiertos, el máximo. Cierra alguno con su × para abrir otro.\n(Sin Ctrl, el clic abre al jugador en la pestaña actual.)",
                    "There are already " + PERFIL_MAX_PESTANAS + " profiles open, the maximum. Close one with its × to open another.\n(Without Ctrl, a click opens the player in the current tab.)"),
                    t("Pestañas de perfil", "Profile tabs"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        perfilPestanaActiva = -1;   // la siguiente apertura crea pestaña
        navegacion.abrirPerfil(pid, nombre);
    }

    private int indicePestana(long pid) { for (int i = 0; i < perfilPestanas.size(); i++) if ((Long) perfilPestanas.get(i)[0] == pid) return i; return -1; }

    private void perfilContabilizarPestana(long pid, String nombre) {
        int i = indicePestana(pid);
        if (i >= 0) perfilPestanaActiva = i;
        else if (perfilPestanaActiva >= 0 && perfilPestanaActiva < perfilPestanas.size()) perfilPestanas.set(perfilPestanaActiva, new Object[]{ pid, nombre });
        else { perfilPestanas.add(new Object[]{ pid, nombre }); perfilPestanaActiva = perfilPestanas.size() - 1; }
        refrescarTiraPerfil();
    }

    private void cerrarPestana(int i) {
        if (i < 0 || i >= perfilPestanas.size()) return;
        perfilPestanas.remove(i);
        if (perfilPestanas.isEmpty()) { perfilPestanaActiva = -1; refrescarTiraPerfil(); navegacion.abrirPerfil(0, ""); return; }
        if (perfilPestanaActiva >= perfilPestanas.size()) perfilPestanaActiva = perfilPestanas.size() - 1;
        else if (i < perfilPestanaActiva) perfilPestanaActiva--;
        Object[] p = perfilPestanas.get(Math.max(0, perfilPestanaActiva));
        perfilPestanaActiva = Math.max(0, perfilPestanaActiva);
        refrescarTiraPerfil();
        navegacion.abrirPerfil((Long) p[0], (String) p[1]);
    }

    private void refrescarTiraPerfil() {
        if (perfilTira == null) return;
        perfilTira.removeAll();
        for (int i = 0; i < perfilPestanas.size(); i++) {
            final int idx = i;
            Object[] p = perfilPestanas.get(i);
            JPanel caja = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); caja.setOpaque(false);
            JToggleButton tb = anfitrion.crearBotonPestana((String) p[1], iconoBandera(anfitrion.paisDe((Long) p[0])));
            tb.setSelected(i == perfilPestanaActiva);
            tb.addActionListener(e -> { if (!tb.isSelected()) { tb.setSelected(true); return; } perfilPestanaActiva = idx; navegacion.abrirPerfil((Long) p[0], (String) p[1]); });
            JButton x = new JButton("\u00D7");
            x.setFocusable(false); x.setMargin(new Insets(0, 4, 0, 4)); x.putClientProperty("JButton.buttonType", "borderless");
            x.setToolTipText(t("Cerrar esta pestaña", "Close this tab"));
            x.addActionListener(e -> cerrarPestana(idx));
            caja.add(tb); caja.add(x);
            perfilTira.add(caja);
        }
        JButton mas = new JButton("+");
        mas.setFocusable(false); mas.setMargin(new Insets(2, 8, 2, 8)); mas.putClientProperty("JButton.buttonType", "roundRect");
        mas.setToolTipText(t("Nueva pestaña: busca a otro jugador (también Ctrl+clic en un rival o aliado)", "New tab: search another player (also Ctrl+click on a rival or ally)"));
        mas.addActionListener(e -> {
            if (perfilPestanas.size() >= PERFIL_MAX_PESTANAS) { abrirEnPestanaNueva(-1, ""); return; }
            perfilPestanaActiva = -1; navegacion.abrirPerfil(0, "");
        });
        perfilTira.add(mas);
        perfilTira.setVisible(!perfilPestanas.isEmpty());
        perfilTira.revalidate(); perfilTira.repaint();
    }

    // ===== construcción del panel (idéntica a construirPanelPerfil de la 1.1) ======================

    private void construirPanelPerfil() {
        actividadPanel = new JPanel(new BorderLayout(8, 6));
        actividadPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel norte = new JPanel(new BorderLayout(8, 0));
        JPanel izq = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        actTitulo = new JLabel();
        actTitulo.setFont(actTitulo.getFont().deriveFont(Font.BOLD, 14f));
        actTitulo.setToolTipText(t("Clic derecho: nota, alias, nicks anteriores, cuentas vinculadas, watchlist…", "Right-click: note, alias, previous names, linked accounts, watchlist…"));
        actTitulo.addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) actMenuNombre(e, actTitulo); } @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) actMenuNombre(e, actTitulo); } });
        actModoCombo = new JComboBox<>();
        actModoCombo.addActionListener(e -> { if (actRellenandoModos) return; Object cl = actModoCombo.getClientProperty("claves"); if (cl instanceof List<?> l && actModoCombo.getSelectedIndex() >= 0 && actModoCombo.getSelectedIndex() < l.size()) { actModo = String.valueOf(l.get(actModoCombo.getSelectedIndex())); actPintar(); } });
        izq.add(actModoCombo);
        actPeriodoCombo = new JComboBox<>(new String[]{ t("Todo el historial", "Whole history"), t("7 días", "7 days"), t("30 días", "30 days"), t("90 días", "90 days"), t("365 días", "365 days"), t("Rango de fechas…", "Date range…") });
        actPeriodoCombo.setToolTipText(t("Periodo: todas las tarjetas y listas del perfil se calculan solo con las partidas de ese periodo", "Period: every card and list of the profile is computed with the games in that period only"));
        actPeriodoCombo.addActionListener(e -> {
            if (actRellenandoModos) return;
            int i = actPeriodoCombo.getSelectedIndex();
            if (i == 5) { if (!pedirRangoFechas()) { actRellenandoModos = true; actPeriodoCombo.setSelectedIndex(actPeriodoIdx); actRellenandoModos = false; return; } }
            else { actDesde = i == 0 ? null : LocalDate.now().minusDays(new int[]{ 0, 6, 29, 89, 364 }[i]); actHasta = null; }
            actPeriodoIdx = i; actPintar();
        });
        izq.add(actPeriodoCombo);
        actHastaLabel = new JLabel(); actHastaLabel.setFont(actHastaLabel.getFont().deriveFont(Font.PLAIN, 11f)); actHastaLabel.setForeground(colorSecundario()); actHastaLabel.setVisible(false);
        actHoyBtn = new JButton(t("Actualizar hoy", "Update today"));
        actHoyBtn.setFocusable(false); actHoyBtn.setMargin(new Insets(1, 8, 1, 8)); actHoyBtn.putClientProperty("JButton.buttonType", "roundRect"); actHoyBtn.setVisible(false);
        actHoyBtn.addActionListener(e -> presenter.actualizarHoy(actPid));
        izq.add(actHastaLabel); izq.add(actHoyBtn);
        JButton histBtn = new JButton(t("Todas las partidas…", "All games…"));
        histBtn.setFocusable(false); histBtn.setMargin(new Insets(1, 8, 1, 8)); histBtn.putClientProperty("JButton.buttonType", "roundRect");
        histBtn.setToolTipText(t("El histórico del perfil en páginas, con filtro de modo y rec a un clic", "The profile's history in pages, with a mode filter and recs one click away"));
        histBtn.addActionListener(e -> { if (actPid > 0) verHistorialEnTabla(actPid, actNombre); });
        izq.add(histBtn);
        JButton h2hBtn = new JButton(t("Cara a cara…", "Head-to-head…"));
        h2hBtn.setFocusable(false); h2hBtn.setMargin(new Insets(1, 8, 1, 8)); h2hBtn.putClientProperty("JButton.buttonType", "roundRect");
        h2hBtn.setToolTipText(t("El cruce con un rival: balance, mapas, civ contra civ y últimas partidas entre ambos", "The pairing with an opponent: record, maps, civ vs civ and latest games between them"));
        h2hBtn.addActionListener(e -> mostrarCaraACara());
        izq.add(h2hBtn);
        JButton masBtn = new JButton("\u22EF");
        masBtn.setFocusable(false); masBtn.setMargin(new Insets(1, 6, 1, 6)); masBtn.putClientProperty("JButton.buttonType", "roundRect");
        masBtn.setToolTipText(t("Nota, alias, nicks anteriores, cuentas vinculadas, watchlist…", "Note, alias, previous names, linked accounts, watchlist…"));
        masBtn.addActionListener(e -> actMenuNombre(new MouseEvent(masBtn, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 0, masBtn.getHeight(), 1, true), masBtn));
        izq.add(masBtn);
        perfilBusca = new JTextField(18);
        perfilBusca.putClientProperty("JTextField.placeholderText", t("Buscar jugador… o selecciona en la watchlist", "Search a player… or select in the watchlist"));
        perfilBusca.putClientProperty("JTextField.showClearButton", true);
        perfilPopup = new JPopupMenu(); perfilPopup.setFocusable(false);
        perfilDebounce = new javax.swing.Timer(450, e -> perfilSugerir());
        perfilDebounce.setRepeats(false);
        perfilBusca.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() { perfilDebounce.restart(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        perfilBusca.addActionListener(e -> { if (perfilPopup.isVisible() && perfilPopup.getComponentCount() > 0) ((JMenuItem) perfilPopup.getComponent(0)).doClick(); else perfilSugerir(); });
        perfilBusca.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && perfilPopup.isVisible() && perfilPopup.getComponentCount() > 0) { ((JMenuItem) perfilPopup.getComponent(0)).doClick(); e.consume(); }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) perfilPopup.setVisible(false);
            }
        });
        izq.add(perfilBusca, 0);   // el buscador, siempre lo primero de la barra (igual en Ratings)
        izq.add(actTitulo, 1);
        actEstado = new JLabel();
        actEstado.setFont(actEstado.getFont().deriveFont(Font.PLAIN, 11f));
        izq.add(actEstado);
        norte.add(izq, BorderLayout.CENTER);
        JButton cerrar = new JButton("\u00D7");
        cerrar.setFocusable(false); cerrar.setMargin(new Insets(0, 7, 0, 7)); cerrar.putClientProperty("JButton.buttonType", "roundRect");
        cerrar.setToolTipText(t("Cerrar", "Close"));
        cerrar.addActionListener(e -> { actividadAbierta = false; anfitrion.cerrarPerfil(); });
        norte.add(cerrar, BorderLayout.EAST);
        actProgreso = new JProgressBar(0, ACT_MAX_PAGINAS);
        actProgreso.setStringPainted(true);
        actProgreso.setVisible(false);
        actProgreso.setPreferredSize(new Dimension(10, 16));
        norte.add(actProgreso, BorderLayout.SOUTH);
        actMasBtn = new JButton(t("Cargar más partidas", "Load more games"));
        actMasBtn.setFocusable(false); actMasBtn.setMargin(new Insets(1, 8, 1, 8)); actMasBtn.putClientProperty("JButton.buttonType", "roundRect");
        actMasBtn.setToolTipText(t("Este jugador tiene más de 1.000 partidas en el último año: baja las 500 siguientes", "This player has over 1,000 games in the last year: fetch the next 500"));
        actMasBtn.setVisible(false);
        actMasBtn.addActionListener(e -> perfilCargarMas());
        izq.add(actMasBtn);
        perfilTira = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 0));
        perfilTira.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(128, 128, 128, 70)));
        perfilTira.setVisible(false);
        JPanel norteTodo = new JPanel(new BorderLayout(0, 4));
        norteTodo.add(perfilTira, BorderLayout.NORTH);
        norteTodo.add(norte, BorderLayout.CENTER);
        actividadPanel.add(norteTodo, BorderLayout.NORTH);

        actCuerpo = new PanelScrollable();
        actCuerpo.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 4));
        actPista = new JLabel(t("Selecciona un jugador en la watchlist, clic derecho → «Perfil completo…», o escribe un nick arriba.", "Select a player in the watchlist, right-click → \u201CFull profile…\u201D, or type a nick above."));
        actPista.setFont(actPista.getFont().deriveFont(Font.ITALIC, 12f)); actPista.setForeground(Color.GRAY); actPista.setAlignmentX(0f);
        actCuerpo.add(actPista);
        actCabecera = new JPanel(new BorderLayout(10, 2));
        actCabecera.setAlignmentX(0f); actCabecera.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170)); actCabecera.setPreferredSize(new Dimension(10, 170));
        actCabecera.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(128, 128, 128, 70), 1, true), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JPanel identidad = new JPanel(new BorderLayout(0, 2));
        identidad.setOpaque(false);
        actSubtitulo = new JLabel();
        actSubtitulo.setFont(actSubtitulo.getFont().deriveFont(Font.PLAIN, 11.5f)); actSubtitulo.setForeground(colorSecundario());
        actChips = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 2));
        actChips.setOpaque(false);
        JPanel lineas = new JPanel(); lineas.setLayout(new BoxLayout(lineas, BoxLayout.Y_AXIS)); lineas.setOpaque(false);
        actSubtitulo.setAlignmentX(0f); lineas.add(actSubtitulo);
        actVinculadasPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 0)); actVinculadasPanel.setOpaque(false); actVinculadasPanel.setAlignmentX(0f);
        lineas.add(actVinculadasPanel);
        actNotaLinea = new JLabel(); actNotaLinea.setFont(actNotaLinea.getFont().deriveFont(Font.PLAIN, 11.5f)); actNotaLinea.setForeground(new Color(0xb0, 0x8d, 0x57)); actNotaLinea.setAlignmentX(0f); actNotaLinea.setVisible(false);
        lineas.add(actNotaLinea);
        identidad.add(lineas, BorderLayout.NORTH);
        identidad.add(actChips, BorderLayout.CENTER);
        actCabecera.add(identidad, BorderLayout.CENTER);
        actSpark = new JPanel(new BorderLayout());
        actSpark.setOpaque(false); actSpark.setPreferredSize(new Dimension(480, 150));
        actGrafica = new GraficaElo();
        actSpark.add(actGrafica, BorderLayout.CENTER);
        JButton ampliar = new JButton("\u26F6");
        ampliar.setFocusable(false); ampliar.setMargin(new Insets(0, 4, 0, 4)); ampliar.putClientProperty("JButton.buttonType", "roundRect");
        ampliar.setToolTipText(t("Ampliar la gráfica de ELO", "Enlarge the ELO chart"));
        ampliar.addActionListener(e -> {
            Window owner = SwingUtilities.getWindowAncestor(actividadPanel);
            JDialog d = new JDialog(owner, t("ELO de ", "ELO of ") + actNombre + " · " + actGrafica.etiqueta, java.awt.Dialog.ModalityType.MODELESS);
            GraficaElo g = new GraficaElo(); g.datos(actGrafica.puntos, actGrafica.etiqueta);
            d.add(g);
            d.getRootPane().registerKeyboardAction(ev -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            d.setSize(1000, 480); d.setLocationRelativeTo(owner); d.setVisible(true);
        });
        JPanel esq = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); esq.setOpaque(false); esq.add(ampliar);
        actSpark.add(esq, BorderLayout.NORTH);
        actCabecera.add(actSpark, BorderLayout.EAST);
        actCuerpo.add(actCabecera);
        actCuerpo.add(Box.createVerticalStrut(8));
        actTarjetas = new JPanel(new GridLayout(1, 5, 8, 0));
        actTarjetas.setAlignmentX(0f); actTarjetas.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        actCuerpo.add(actTarjetas);
        actCuerpo.add(Box.createVerticalStrut(8));
        actCalendario = new CalendarioPanel();
        actCalendario.setAlignmentX(0f);
        actCalendario.setPreferredSize(new Dimension(10, 150)); actCalendario.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        actCuerpo.add(actCalendario);
        JPanel fila2 = new JPanel(new GridLayout(1, 3, 8, 0));
        fila2.setAlignmentX(0f); fila2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170)); fila2.setPreferredSize(new Dimension(10, 170));
        actSemana = new BarrasActividad(t("Por día de la semana", "By day of week"));
        actHoras = new BarrasActividad(t("Por hora (hora local)", "By hour (local time)"));
        actMeses = new BarrasActividad(t("Por mes", "By month"));
        fila2.add(actSemana); fila2.add(actHoras); fila2.add(actMeses);
        actCuerpo.add(fila2);
        actCuerpo.add(Box.createVerticalStrut(8));
        JLabel tDur = tituloSeccion(t("Winrate por duración de la partida", "Win rate by match duration"), t("Tramos como en aoe2insights; solo partidas con resultado", "Buckets as in aoe2insights; games with a result only")); tDur.setAlignmentX(0f); actCuerpo.add(tDur);
        actDuracionFila = new JPanel(new GridLayout(1, 5, 8, 0)); actDuracionFila.setAlignmentX(0f); actDuracionFila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        actCuerpo.add(actDuracionFila);
        actCuerpo.add(Box.createVerticalStrut(8));
        JPanel fila3 = new JPanel(new GridLayout(1, 3, 8, 0));
        fila3.setAlignmentX(0f);
        actCivs = listaVertical(); actCivsRival = listaVertical(); actMapas = listaVertical();
        fila3.add(actCivs); fila3.add(actCivsRival); fila3.add(actMapas);
        actCuerpo.add(fila3);
        actCuerpo.add(Box.createVerticalStrut(8));
        JPanel fila4 = new JPanel(new GridLayout(1, 3, 8, 0));
        fila4.setAlignmentX(0f);
        actRivales = listaVertical(); actAliados = listaVertical(); actTramos = listaVertical();
        fila4.add(actRivales); fila4.add(actAliados); fila4.add(actTramos);
        actCuerpo.add(fila4);
        actCuerpo.add(Box.createVerticalStrut(8));
        JPanel fila5 = new JPanel(new GridLayout(1, 3, 8, 0));
        fila5.setAlignmentX(0f);
        actPosicion = listaVertical(); actUltimos30Civs = listaVertical(); actUltimos30Mapas = listaVertical();
        fila5.add(actPosicion); fila5.add(actUltimos30Civs); fila5.add(actUltimos30Mapas);
        actCuerpo.add(fila5);
        JScrollPane scroll = new JScrollPane(actCuerpo, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        actividadPanel.add(scroll, BorderLayout.CENTER);
        JLabel pie = new JLabel(t("Perfil e historial de aoe2companion.com (hasta 1 año o 1.500 partidas). Solo agregados: cada línea exige al menos 3 partidas; el calendario cuenta partidas, no resultados. Clic en un rival o aliado abre su perfil.",
                "Profile and history from aoe2companion.com (up to 1 year or 1,500 games). Aggregates only: every line needs at least 3 games; the calendar counts games, not results. Click a rival or ally to open their profile."));
        pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f));
        actividadPanel.add(pie, BorderLayout.SOUTH);
        actMostrarCuerpo(false);
    }

    private void actMostrarCuerpo(boolean hay) {
        if (hay) actPista.setText(t("Selecciona un jugador en la watchlist, clic derecho → «Perfil completo…», o escribe un nick arriba.", "Select a player in the watchlist, right-click → \u201CFull profile…\u201D, or type a nick above."));
        actPista.setVisible(!hay);
        for (Component c : actCuerpo.getComponents()) if (c != actPista) c.setVisible(hay);
        actModoCombo.setVisible(hay);
        actCuerpo.revalidate(); actCuerpo.repaint();
    }

    private boolean pedirRangoFechas() {
        JTextField d1 = new JTextField(actDesde == null ? LocalDate.now().minusDays(29).toString() : actDesde.toString(), 10), d2 = new JTextField(actHasta == null ? LocalDate.now().toString() : actHasta.toString(), 10);
        JPanel pnl = new JPanel(new GridLayout(2, 2, 6, 6));
        pnl.add(new JLabel(t("Desde (AAAA-MM-DD):", "From (YYYY-MM-DD):"))); pnl.add(d1); pnl.add(new JLabel(t("Hasta:", "To:"))); pnl.add(d2);
        int r = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(actividadPanel), pnl, t("Rango de fechas", "Date range"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return false;
        try { actDesde = LocalDate.parse(d1.getText().trim()); actHasta = LocalDate.parse(d2.getText().trim()); if (actHasta.isBefore(actDesde)) { LocalDate x = actDesde; actDesde = actHasta; actHasta = x; } return true; }
        catch (Exception ex) { anfitrion.mostrarEstadoGlobal(t("Fecha no válida: usa AAAA-MM-DD.", "Invalid date: use YYYY-MM-DD.")); return false; }
    }

    private boolean enPeriodo(Match m) {
        if (m.started == null) return actDesde == null;
        LocalDate d = m.started.atZone(ZoneId.systemDefault()).toLocalDate();
        if (actDesde != null && d.isBefore(actDesde)) return false;
        return actHasta == null || !d.isAfter(actHasta);
    }

    // ===== buscador de nick de la barra del perfil =================================================

    /** Sugerencias de nick para el buscador del perfil (búsqueda del companion, con retardo). */
    private void perfilSugerir() {
        String q = perfilBusca.getText().trim();
        perfilPopup.setVisible(false); perfilPopup.removeAll();
        if (q.length() < 2) return;
        presenter.sugerirBuscador(q);
    }

    // ===== PerfilPresenter.Pantalla ==================================================================

    @Override public boolean cargando() { return actCargando; }
    @Override public void cargando(boolean v) { actCargando = v; }

    /** Fila 28: lo que alAbrir hacía en el acto con perfiles.actividad(pid)/fichaConocida(pid) (ambas sin red, pero
     *  la primera puede leer disco): ahora llega desde el hilo de PerfilPresenter.abrirBase. Mismas decisiones que
     *  antes, en el mismo orden: pintar cuerpo y cabecera si hay algo, y si ya está fresco y completo no hace
     *  falta seguir cargando; si no, sigue como siempre por sfr-data o la API (presenter.cargar). */
    @Override public void baseLista(Actividad base, FichaPerfil perfilCache) {
        boolean fresco = base != null && System.currentTimeMillis() - base.ms() < 30 * 60_000L;
        if (base != null) { actMostrarCuerpo(true); actRellenarModos(base); actPintar(); }
        if (perfilCache != null && base != null) actPintarCabecera(perfilCache);
        if (fresco && base.completo() && perfilCache != null) { actEstado.setText(miles(base.partidas().size()) + t(" partidas", " games")); actProgreso.setVisible(false); return; }
        presenter.cargar(actPid, actNombre, base, fresco);
    }

    @Override public void cargaIniciada() {
        actEstado.setText(t("Consultando el perfil…", "Fetching the profile…"));
        actProgreso.setVisible(true); actProgreso.setIndeterminate(true); actProgreso.setString(t("Perfil…", "Profile…"));
        actOrigenSfr = false; actHastaSfr = null; perfilEstadoHoy();
    }

    @Override public void cabecera(FichaPerfil ficha) { actPintarCabecera(ficha); }

    @Override public void desdeSfr(Actividad a, String hastaSfr) {
        actOrigenSfr = true; actHastaSfr = hastaSfr;
        actProgreso.setVisible(false); actMostrarCuerpo(true); actRellenarModos(a); actPintar();
        actEstado.setText(miles(a.partidas().size()) + t(" partidas · último año · de sfr-data", " games · last year · from sfr-data"));
        actMasBtn.setVisible(false);
        perfilEstadoHoy();
    }

    @Override public void progresoParcial(Actividad parcialA, int maxPaginas) {
        actividadCache.put(actPid, parcialA);
        actProgreso.setIndeterminate(false); actProgreso.setMaximum(maxPaginas); actProgreso.setValue(Math.min(maxPaginas, parcialA.paginas()));
        actProgreso.setString(t("Historial: página ", "History: page ") + parcialA.paginas() + t(" de ", " of ") + maxPaginas + " · " + miles(parcialA.partidas().size()) + t(" partidas", " games"));
        actEstado.setText(t("datos parciales…", "partial data…"));
        actMostrarCuerpo(true); actRellenarModos(parcialA); actPintar();
    }

    @Override public void cargaCompletada(Actividad a) {
        actProgreso.setVisible(false); actMostrarCuerpo(true); actRellenarModos(a); actPintar();
        // nada se descarga solo: «Cargar más» es una decisión tuya (cada página son 50 partidas y una llamada)
    }

    @Override public void errorCarga(String mensaje) {
        actProgreso.setVisible(false); actEstado.setText(t("No se pudo cargar el perfil: ", "Couldn't load the profile: ") + mensaje);
    }

    @Override public void hoyIniciado() {
        actHoyBtn.setEnabled(false); actHoyBtn.setText(t("Actualizando…", "Updating…"));
    }

    @Override public void marcarVinculadasPedidas(long pid) { vinculadasPedidas.add(pid); }

    @Override public void hoyTerminado(FichaPerfil ficha, int nuevas) {
        if (ficha != null) actPintarCabecera(ficha);   // F9: sin ficha nueva ni conocida, se deja la cabecera que había (p. ej. la sintética de sfr-data)
        Actividad a = actividadCache.get(actPid);
        if (a != null) { actRellenarModos(a); actPintar(); }
        actHoyBtn.setText(nuevas == 0 ? t("Al día · sin partidas nuevas", "Up to date · no new games") : t("Actualizado · +", "Updated · +") + nuevas + t(" partidas", " games"));
        actHoyBtn.setForeground(UIManager.getColor("Button.foreground"));
        actHoyBtn.setEnabled(false);
        if (actHastaLabel != null) actHastaLabel.setText(t("Datos hasta hoy", "Data up to today"));
    }

    @Override public void hoyError(String mensaje) {
        actHoyBtn.setEnabled(true); actHoyBtn.setText(t("Actualizar hoy", "Update today"));
        anfitrion.mostrarEstadoGlobal(t("No se pudo actualizar: ", "Couldn't update: ") + mensaje);
    }

    @Override public void masProgreso(Actividad parcialA, int maxPaginas) {
        actividadCache.put(actPid, parcialA);
        actProgreso.setIndeterminate(false); actProgreso.setMaximum(maxPaginas); actProgreso.setValue(parcialA.paginas());
        actProgreso.setString(t("Historial: página ", "History: page ") + parcialA.paginas() + " · " + miles(parcialA.partidas().size()) + t(" partidas", " games"));
        actRellenarModos(parcialA); actPintar();
    }

    @Override public void masCompletado(Actividad a) { actProgreso.setVisible(false); actRellenarModos(a); actPintar(); }

    @Override public void masError(String mensaje) { actProgreso.setVisible(false); actEstado.setText(t("No se pudo cargar más: ", "Couldn't load more: ") + mensaje); }

    @Override public void sugerenciasBuscador(List<String[]> res, String query) {
        if (!query.equals(perfilBusca.getText().trim())) return;
        perfilPopup.removeAll();
        int n = 0;
        for (String[] r : res) {
            JMenuItem it = new JMenuItem(r[2]);
            long pid = Long.parseLong(r[0]); String nombre = r[1];
            it.addActionListener(a -> { perfilBusca.setText(""); perfilPopup.setVisible(false); navegacion.abrirPerfil(pid, nombre); });
            perfilPopup.add(it);
            if (++n >= 8) break;
        }
        if (n > 0 && perfilBusca.isShowing()) perfilPopup.show(perfilBusca, 0, perfilBusca.getHeight());
    }

    @Override public void vinculadasListas() { actPintarVinculadas(); }

    // ===== «Actualizar hoy» / «Cargar más» / historial en diálogo aparte =============================

    private void perfilEstadoHoy() {
        if (actHoyBtn == null) return;
        boolean visible = actOrigenSfr && actPid > 0;
        actHoyBtn.setVisible(visible); if (actHastaLabel != null) actHastaLabel.setVisible(visible);
        if (!visible) return;
        long hastaMs = 0;
        try { if (actHastaSfr != null && actHastaSfr.length() >= 10) hastaMs = LocalDate.parse(actHastaSfr.substring(0, 10)).plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(); } catch (Exception ignored) { }
        Long visto = vivo.vistoMs(actPid);
        boolean hayNuevas = (visto != null && visto > hastaMs) || vivo.partida(actPid) != null;
        actHoyBtn.setEnabled(true);
        actHoyBtn.setText(t("Actualizar hoy", "Update today"));
        actHoyBtn.setForeground(hayNuevas ? (Tema.temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00)) : UIManager.getColor("Button.foreground"));
        actHoyBtn.setToolTipText(hayNuevas ? t("Le hemos visto en partida después del volcado: hay partidas de hoy que traer (2 llamadas)", "Seen in a game after the dump: there are today's games to fetch (2 requests)")
                : t("No consta ninguna partida nueva desde el volcado; actualizar solo si crees que ha jugado hoy (2 llamadas)", "No new game is known since the dump; update only if you think they played today (2 requests)"));
        if (actHastaLabel != null) actHastaLabel.setText(t("Datos hasta el ", "Data up to ") + (actHastaSfr == null ? "?" : actHastaSfr) + (hayNuevas ? "" : t(" · sin partidas nuevas conocidas", " · no new games known")));
    }

    private void perfilCargarMas() { perfilCargarMas(ACT_MAS_PAGINAS); }
    private void perfilCargarMas(int paginas) {
        Actividad base = actividadCache.get(actPid);
        if (base == null || actCargando || paginas <= 0) return;
        actMasBtn.setVisible(false);
        actProgreso.setVisible(true); actProgreso.setIndeterminate(true); actProgreso.setString(t("Cargando más…", "Loading more…"));
        presenter.cargarMas(actPid, actNombre, base, paginas);
    }

    private void actRellenarModos(Actividad a) {
        Map<String, Integer> cuenta = new LinkedHashMap<>();
        for (Match m : a.partidas()) cuenta.merge(m.mode == null ? "?" : m.mode, 1, Integer::sum);
        List<Map.Entry<String, Integer>> l = new ArrayList<>(cuenta.entrySet());
        l.sort((x, y) -> y.getValue() - x.getValue());
        List<String> claves = new ArrayList<>(); claves.add("*");
        actRellenandoModos = true;
        try {
            actModoCombo.removeAllItems();
            actModoCombo.addItem(t("Todos los modos", "All modes") + " (" + a.partidas().size() + ")");
            for (Map.Entry<String, Integer> en : l) { claves.add(en.getKey()); actModoCombo.addItem(en.getKey() + " (" + en.getValue() + ")"); }
            actModoCombo.putClientProperty("claves", claves);
            if (!claves.contains(actModo)) actModo = "*";
            actModoCombo.setSelectedIndex(claves.indexOf(actModo));
        } finally { actRellenandoModos = false; }
    }

    private JLabel chipPerfil(String titulo, String valor, String tooltip) {
        JLabel l = new JLabel("<html><span style='color:" + colorSecundarioHex() + ";font-size:9.5px;font-weight:bold'>" + escapeHtml(titulo).toUpperCase(Locale.ROOT) + "</span><br><b>" + valor + "</b></html>");
        l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(128, 128, 128, 60), 1, true), BorderFactory.createEmptyBorder(1, 6, 1, 6)));
        if (tooltip != null) l.setToolTipText(tooltip);
        return l;
    }

    /** «Todas las partidas del perfil»: el histórico en páginas de 25, con filtro de modo; del paquete de sfr-data si está o de la API, solo al pedir más. */
    public void mostrarHistorialPerfil(long pid, String nombre) {
        if (histDialogo != null) { histDialogo.dispose(); histDialogo = null; }
        Window owner = SwingUtilities.getWindowAncestor(actividadPanel);
        histDialogo = new JDialog(owner, t("Todas las partidas de ", "All games of ") + nombre, java.awt.Dialog.ModalityType.MODELESS);
        JPanel norte = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JComboBox<String> modo = new JComboBox<>(new String[]{ t("Todos los modos", "All modes"), "1v1 Random Map", "Team Random Map", "1v1 Empire Wars", "Team Empire Wars", "1v1 Death Match", "Team Death Match" });
        norte.add(new JLabel(t("Modo:", "Mode:"))); norte.add(modo);
        JLabel info = new JLabel(); info.setForeground(colorSecundario()); norte.add(info);
        JButton ant = new JButton("\u2190"), sig = new JButton("\u2192"); for (JButton b : new JButton[]{ ant, sig }) { b.setFocusable(false); b.setMargin(new Insets(1, 8, 1, 8)); b.putClientProperty("JButton.buttonType", "roundRect"); }
        JButton mas = new JButton(t("Cargar 50 más (1 llamada)", "Load 50 more (1 request)")); mas.setFocusable(false); mas.setMargin(new Insets(1, 8, 1, 8)); mas.putClientProperty("JButton.buttonType", "roundRect"); mas.setVisible(false);
        JLabel pag = new JLabel();
        norte.add(ant); norte.add(pag); norte.add(sig); norte.add(mas);
        String[] cols = { t("Fecha", "Date"), t("Mapa", "Map"), t("Modo", "Mode"), t("Partida", "Game"), t("Rec", "Rec") };
        DefaultTableModel modelo = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        javax.swing.JTable tabla = new javax.swing.JTable(modelo); tabla.setRowHeight(tabla.getRowHeight() + 6); tabla.getTableHeader().setReorderingAllowed(false);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(120); tabla.getColumnModel().getColumn(1).setPreferredWidth(120); tabla.getColumnModel().getColumn(2).setPreferredWidth(120); tabla.getColumnModel().getColumn(3).setPreferredWidth(360); tabla.getColumnModel().getColumn(4).setPreferredWidth(60);
        List<Match> visibles = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES"));
        histPagina = 0; histModo = "*";
        Runnable pintar = () -> {
            Actividad a = actividadCache.get(pid);
            List<Match> todas = new ArrayList<>();
            if (a != null) for (Match m : a.partidas()) if ("*".equals(histModo) || histModo.equals(m.mode)) todas.add(m);
            int porPag = 25, paginas = Math.max(1, (todas.size() + porPag - 1) / porPag);
            histPagina = Math.max(0, Math.min(histPagina, paginas - 1));
            modelo.setRowCount(0); visibles.clear();
            for (int i = histPagina * porPag; i < Math.min(todas.size(), (histPagina + 1) * porPag); i++) {
                Match m = todas.get(i); visibles.add(m);
                StringBuilder j = new StringBuilder(); int team = -1;
                for (MatchPlayer mp : m.players) { if (team != -1 && mp.team != team) j.append("  vs  "); else if (j.length() > 0) j.append(", "); j.append(anfitrion.nombreVisible(mp.id, mp.name)).append(mp.civ == null ? "" : " (" + mp.civ + ")"); team = mp.team; }
                modelo.addRow(new Object[]{ m.started == null ? "" : m.started.atZone(ZoneId.systemDefault()).format(fmt), m.map, m.mode, j.toString(), m.enDisco ? "\u2713" : "" });
            }
            pag.setText((histPagina + 1) + " / " + paginas);
            ant.setEnabled(histPagina > 0); sig.setEnabled(histPagina < paginas - 1);
            boolean deSfr = a != null && a.completo() && actOrigenSfr && pid == actPid;
            info.setText(a == null ? t("sin datos", "no data") : miles(todas.size()) + t(" partidas", " games") + (deSfr ? t(" · último año · de sfr-data", " · last year · from sfr-data") : t(" · cargadas hasta ahora", " · loaded so far")));
            mas.setVisible(a != null && !deSfr && !a.completo());
        };
        modo.addActionListener(e -> { histModo = modo.getSelectedIndex() == 0 ? "*" : String.valueOf(modo.getSelectedItem()); histPagina = 0; pintar.run(); });
        ant.addActionListener(e -> { histPagina--; pintar.run(); });
        sig.addActionListener(e -> { histPagina++; pintar.run(); });
        mas.addActionListener(e -> { mas.setEnabled(false); presenter.cargarMasHistorialCompleto(pid, nombre, () -> { mas.setEnabled(true); pintar.run(); }); });
        JPanel sur = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton desc = new JButton(t("Descargar rec", "Download rec")), env = new JButton(t("Enviar al juego", "Send to game"));
        for (JButton b : new JButton[]{ desc, env }) { b.setFocusable(false); b.setMargin(new Insets(2, 10, 2, 10)); b.putClientProperty("JButton.buttonType", "roundRect"); }
        java.util.function.Supplier<List<Match>> sel = () -> { List<Match> l = new ArrayList<>(); for (int r : tabla.getSelectedRows()) l.add(visibles.get(r)); return l; };
        desc.addActionListener(e -> { List<Match> l = sel.get(); if (l.isEmpty()) return; anfitrion.descargarSinCambiarVista(l, false, pintar); });
        env.addActionListener(e -> { List<Match> l = sel.get(); if (l.isEmpty()) return; anfitrion.descargarSinCambiarVista(l, true, pintar); });
        JLabel nota = new JLabel(t("Sin resultado: como en Live now. Selecciona filas y descarga o envía al juego.", "No result shown, like Live now. Select rows and download or send to game.")); nota.setForeground(colorSecundario()); nota.setFont(nota.getFont().deriveFont(Font.PLAIN, 11f));
        sur.add(desc); sur.add(env); sur.add(nota);
        histDialogo.add(norte, BorderLayout.NORTH); histDialogo.add(new JScrollPane(tabla), BorderLayout.CENTER); histDialogo.add(sur, BorderLayout.SOUTH);
        histDialogo.getRootPane().registerKeyboardAction(e -> histDialogo.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pintar.run();
        histDialogo.setSize(900, 640); histDialogo.setLocationRelativeTo(owner); histDialogo.setVisible(true);
    }

    /** «Todas las partidas»: vuelca el año completo del perfil en la pestaña Partidas. */
    private void verHistorialEnTabla(long pid, String nombre) {
        Actividad a = actividadCache.get(pid);
        if (a == null || a.partidas().isEmpty()) { anfitrion.mostrarEstadoGlobal(t("Sin historial cargado para este perfil.", "No history loaded for this profile.")); return; }
        List<Match> todas = new ArrayList<>(a.partidas());
        for (Match m : todas) if (m.refId == 0) m.refId = pid;
        anfitrion.cargarPartidasEnTabla(todas, new Player(pid, nombre, "", 0));
        historialEnTabla = pid;
        anfitrion.mostrarEstadoGlobal(miles(todas.size()) + t(" partidas · histórico de ", " games · history of ") + nombre + (a.completo() ? t(" · último año · de sfr-data", " · last year · from sfr-data") : t(" · cargadas hasta ahora", " · loaded so far")) + t(" · sin resultado hasta que lo pidas", " · no result until you ask"));
    }

    // ===== cabecera: vinculadas y menú contextual del nombre =========================================

    private void actPintarVinculadas() {
        if (actVinculadasPanel == null) return;
        long pid = actPid;
        List<Perfil.Vinculada> v = perfiles.vinculadasConocidas(pid);
        actVinculadasPanel.removeAll();
        JLabel etq = new JLabel(t("Cuentas vinculadas:", "Linked accounts:")); etq.setFont(etq.getFont().deriveFont(Font.BOLD, 13f));
        actVinculadasPanel.add(etq);
        if (v == null && !vinculadasPedidas.contains(pid)) {
            JLabel b = new JLabel("<html><u>" + t("comprobar", "check") + "</u></html>"); b.setForeground(colorSecundario()); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setToolTipText(t("Busca cuentas vinculadas (una llamada, más una por cuenta para su ELO)", "Looks up linked accounts (one request, plus one per account for its ELO)"));
            b.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { vinculadasPedidas.add(pid); actPintarVinculadas(); } });
            actVinculadasPanel.add(b);
        } else if (v == null) {
            JLabel b = new JLabel(t("buscando…", "looking up…")); b.setForeground(Color.GRAY); actVinculadasPanel.add(b);
            presenter.pedirVinculadas(pid);
        } else if (v.isEmpty()) {
            JLabel b = new JLabel(t("ninguna conocida", "none known")); b.setForeground(Color.GRAY); actVinculadasPanel.add(b);
        } else {
            for (Perfil.Vinculada x : v) {
                long vid = x.pid(); String nombre = anfitrion.nombreVisible(vid, String.valueOf(x.nombre()));
                Integer elo = perfiles.eloVinculada(vid);
                JLabel l = new JLabel("<html><u>" + escapeHtml(nombre) + "</u>" + (elo != null && elo > 0 ? " <span style='color:gray'>(" + elo + ")</span>" : "") + (anfitrion.estaEnWatchlist(vid) ? " <span style='color:gray'>\u2605</span>" : "") + "</html>", iconoBandera(x.pais() != null ? String.valueOf(x.pais()) : anfitrion.paisDe(vid)), javax.swing.SwingConstants.LEFT);
                l.setIconTextGap(4); l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
                l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                l.setToolTipText(t("Clic: abrir su perfil · botón central o clic derecho: en pestaña nueva", "Click: open their profile · middle or right button: in a new tab"));
                l.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) navegacion.abrirPerfil(vid, nombre); else navegacion.abrirPerfilEnPestana(vid, nombre); } });
                actVinculadasPanel.add(l);
            }
        }
        actVinculadasPanel.revalidate(); actVinculadasPanel.repaint();
    }

    private void actMenuNombre(MouseEvent e, Component sobre) {
        long pid = actPid; String nombre = actNombre;
        if (pid <= 0) return;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem perfN = new JMenuItem(t("Perfil en pestaña nueva", "Profile in a new tab")); perfN.addActionListener(a -> navegacion.abrirPerfilEnPestana(pid, nombre)); menu.add(perfN);
        menu.add(menus.perfilNavegador(pid));
        String canalTwitch = twitchLive.containsKey(pid) ? twitchLive.get(pid)[0] : null;
        if (canalTwitch != null) { JMenuItem tw = new JMenuItem(t("Ver directo en Twitch", "Watch live on Twitch")); tw.addActionListener(a -> anfitrion.abrirUrl("https://twitch.tv/" + canalTwitch)); menu.add(tw); }
        JMenu enPartida = menus.enPartida(pid); if (enPartida != null) menu.add(enPartida);
        menu.addSeparator();
        if (!anfitrion.estaEnWatchlist(pid)) {
            JMenu anadir = new JMenu(t("Añadir a mi watchlist", "Add to my watchlist"));
            Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); gs.add(anfitrion.grupoGeneral()); gs.addAll(anfitrion.gruposDeJugadores()); gs.addAll(anfitrion.gruposGuardados());
            for (String g : gs) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> anfitrion.ficharDesdeTop(pid, nombre, g)); anadir.add(it); }
            menu.add(anadir);
        } else { JMenuItem ya = new JMenuItem(t("(ya está en tu watchlist)", "(already in your watchlist)")); ya.setEnabled(false); menu.add(ya); }
        JMenuItem vinc = new JMenuItem(t("Cuentas vinculadas…", "Linked accounts…")); vinc.addActionListener(a -> anfitrion.mostrarVinculadas(pid, nombre)); menu.add(vinc);
        menu.addSeparator();
        JMenuItem alias = new JMenuItem(t("Mostrar como…", "Show as…")); alias.addActionListener(a -> { anfitrion.pedirAlias(pid, nombre); actPintarCabecera(perfiles.fichaConocida(pid)); }); menu.add(alias);
        JMenuItem nota = new JMenuItem(t("Nota…", "Note…")); nota.addActionListener(a -> { anfitrion.pedirNota(pid, nombre); actPintarCabecera(perfiles.fichaConocida(pid)); }); menu.add(nota);
        if (notaDeFn.apply(pid) != null) { JMenuItem bn = new JMenuItem(t("Borrar nota", "Delete note")); bn.addActionListener(a -> { anfitrion.borrarNota(pid, nombre); actPintarCabecera(perfiles.fichaConocida(pid)); }); menu.add(bn); }
        JMenuItem nicks = new JMenuItem(t("Nicks anteriores…", "Previous names…")); nicks.addActionListener(a -> anfitrion.nicksAnteriores(pid, nombre)); menu.add(nicks);
        menu.show(sobre, e.getX(), e.getY());
    }

    /** Cabecera: país y clan, un chip por ladder con ELO · rango · Top %, máximo y totales, y la forma reciente. */
    private void actPintarCabecera(FichaPerfil perfil) {
        actChips.removeAll();
        actPintarVinculadas();
        String nota = notaDeFn.apply(actPid);
        actNotaLinea.setText(nota != null ? "\u270E " + nota : (aliases.get(actPid) != null && !aliases.get(actPid).isBlank() ? t("Mostrado como ", "Shown as ") + aliases.get(actPid) + t(" · nick real ", " · real nick ") + actNombreReal : ""));
        actNotaLinea.setVisible(!actNotaLinea.getText().isEmpty());
        String pais = perfil == null ? "" : String.valueOf(perfil.pais()), clan = perfil == null ? "" : String.valueOf(perfil.clan());
        long games = perfil == null ? 0 : perfil.partidas();
        StringBuilder sub = new StringBuilder();
        if (!pais.isBlank()) sub.append(pais.toUpperCase(Locale.ROOT));
        if (!clan.isBlank()) sub.append(sub.length() > 0 ? "  ·  " : "").append(t("clan ", "clan ")).append(clan);
        if (games > 0) sub.append(sub.length() > 0 ? "  ·  " : "").append(miles(games)).append(t(" partidas en total", " games in total"));
        String canal = canalDe.get(actPid);
        if (canal != null && !canal.isBlank()) sub.append(sub.length() > 0 ? "  ·  " : "").append("twitch.tv/").append(canal);
        actSubtitulo.setText(sub.length() == 0 ? t("Sin datos de perfil", "No profile data") : sub.toString());
        actSubtitulo.setIcon(iconoBandera(pais)); actSubtitulo.setIconTextGap(6);
        if (perfil != null) {
            Map<String, int[]> m = perfil.ladders();
            for (String lb : LADDER_IDS) {
                int[] v = m.get(lb);
                if (v == null || v[0] <= 0) continue;
                String top = percentilRating(lb, true, v[0]);
                if (top == null && v[1] > 0) top = percentilRango(lb, v[1]);
                StringBuilder val = new StringBuilder("<span style='font-size:13px'>" + v[0] + "</span>");
                if (v[1] > 0) val.append(" <span style='font-weight:normal'>#").append(miles(v[1])).append("</span>");
                if (top != null) val.append(" <span style='color:gray;font-weight:normal'>").append(escapeHtml(top)).append("</span>");
                if (v.length > 2 && v[2] > 0) val.append("<br><span style='font-weight:normal;font-size:10px;color:gray'>").append(t("máx ", "peak ")).append("</span><span style='font-weight:normal;font-size:10px'>").append(v[2]).append("</span>");
                String tip = (v.length > 2 && v[2] > 0 ? t("Máximo ", "Peak ") + v[2] : "") + (v.length > 4 && v[3] + v[4] > 0 ? (v.length > 2 && v[2] > 0 ? " · " : "") + v[3] + "-" + v[4] + t(" en total (", " in total (") + pct1(100.0 * v[3] / (v[3] + v[4])) + ")" : "");
                if (top != null) tip = (tip.isBlank() ? "" : tip + " · ") + t("Top % entre los jugadores activos (al menos una partida en los últimos 28 días); el # es el puesto en el ladder completo", "Top % among active players (at least one game in the last 28 days); # is the rank in the full ladder");
                actChips.add(chipPerfil(ladderNombre(lb), val.toString(), tip.isBlank() ? null : tip));
            }
            for (String lbTot : new String[]{ "rm_1v1", "rm_team" }) {
                int[] tot = m.get(lbTot);
                if (tot == null || tot.length < 5 || tot[3] + tot[4] == 0) continue;
                actChips.add(chipPerfil(ladderNombre(lbTot) + t(" en total", " overall"), "<b>" + miles(tot[3]) + "</b> " + t("victorias", "wins") + " - <b>" + miles(tot[4]) + "</b> " + t("derrotas", "losses") + " · <b>" + escapeHtml(pct1(100.0 * tot[3] / (tot[3] + tot[4]))) + "</b> · " + miles(tot[3] + tot[4]) + t(" partidas", " games"), null));
            }
        }
        actChips.revalidate(); actChips.repaint();
    }

    private void actPintar() {
        Actividad a = actividadCache.get(actPid);
        if (a == null) return;
        ZoneId zona = ZoneId.systemDefault();
        LocalDate hoy = LocalDate.now(zona), inicio = hoy.minusDays(actDias - 1);
        Map<LocalDate, Integer> porDia = new HashMap<>();
        int[] semana = new int[7], semanaW = new int[7], semanaN = new int[7];
        int[] horas = new int[24], horasW = new int[24], horasN = new int[24];
        Map<String, int[]> meses = new TreeMap<>();
        Map<String, int[]> civs = new HashMap<>(), civsRival = new HashMap<>(), mapas = new HashMap<>(), tramos = new HashMap<>();
        int[] durN = new int[5], durW = new int[5];
        Map<String, int[]> posicion = new LinkedHashMap<>(), posicionMapa = new LinkedHashMap<>();
        Map<String, int[]> civs30 = new HashMap<>(), mapas30 = new HashMap<>();
        Instant hace30 = Instant.now().minus(Duration.ofDays(30));
        int eloActualJugador = 0;
        { FichaPerfil pf = perfiles.fichaConocida(actPid); if (pf != null && pf.ladders().get("rm_1v1") instanceof int[] v && v[0] > 0) eloActualJugador = v[0]; if (eloActualJugador == 0) { Integer e = eloWatch.get(actPid); if (e != null && e > 0) eloActualJugador = e; } }
        String[] franjas = null;
        Map<Long, Object[]> rivales = new HashMap<>(), aliados = new HashMap<>();
        String modoGrafica = "*".equals(actModo) ? modoPrincipal(a) : actModo;
        List<long[]> serie = new ArrayList<>();
        int partidas = 0, conResultado = 0, victorias = 0; long duracion = 0; int conDuracion = 0;
        int f10 = 0, fW = 0, fL = 0, fDiff = 0, fRacha = 0; Boolean fRachaGana = null; boolean fRachaViva = true;
        for (Match m : a.partidas()) {
            if (!"*".equals(actModo) && !actModo.equals(m.mode)) continue;
            if (!enPeriodo(m)) continue;
            MatchPlayer yo = null;
            for (MatchPlayer p : m.players) if (p.id == actPid) { yo = p; break; }
            if (yo == null) continue;
            partidas++;
            ZonedDateTime z = m.started.atZone(zona);
            porDia.merge(z.toLocalDate(), 1, Integer::sum);
            int dow = z.getDayOfWeek().getValue() - 1;
            int h = z.getHour();
            String mes = z.getYear() + "-" + String.format("%02d", z.getMonthValue());
            int[] mm = meses.computeIfAbsent(mes, k -> new int[2]);
            semana[dow]++; horas[h]++; mm[0]++;
            if (yo.ratingDiff != null && m.finished != null && modoGrafica != null && modoGrafica.equals(m.mode) && serie.size() < 100) serie.add(new long[]{ m.started.toEpochMilli(), yo.rating == null ? 0 : yo.rating, yo.ratingDiff });
            Boolean gano = yo.won;
            if (gano != null && m.finished != null) {
                conResultado++; if (gano) victorias++;
                if (f10 < 10) {
                    f10++;
                    if (gano) fW++; else fL++;
                    if (yo.ratingDiff != null) fDiff += yo.ratingDiff;
                    if (fRachaViva) {
                        if (fRachaGana == null) { fRachaGana = gano; fRacha = 1; }
                        else if (fRachaGana == gano) fRacha++;
                        else fRachaViva = false;
                    }
                }
                semanaN[dow]++; horasN[h]++; if (gano) { semanaW[dow]++; horasW[h]++; mm[1]++; }
                if (m.finished.isAfter(m.started)) { duracion += Duration.between(m.started, m.finished).getSeconds(); conDuracion++; }
                String civ = yo.civ == null || yo.civ.isBlank() ? "?" : yo.civ;
                int[] c = civs.computeIfAbsent(civ, k -> new int[2]); c[0]++; if (gano) c[1]++;
                if (m.map != null && !m.map.isBlank()) { int[] x = mapas.computeIfAbsent(m.map, k -> new int[2]); x[0]++; if (gano) x[1]++; }
                if (m.started != null && m.started.isAfter(hace30)) { int[] c30 = civs30.computeIfAbsent(civ, k -> new int[2]); c30[0]++; if (gano) c30[1]++; if (m.map != null && !m.map.isBlank()) { int[] x30 = mapas30.computeIfAbsent(m.map, k -> new int[2]); x30[0]++; if (gano) x30[1]++; } }
                { int td = tramoDuracion(m); if (td >= 0) { durN[td]++; if (gano) durW[td]++; } }
                if ("Team Random Map".equals(m.mode)) { String pos = posicionEnEquipo(m, yo); if (pos != null) { int[] px = posicion.computeIfAbsent(pos, k -> new int[2]); px[0]++; if (gano) px[1]++; if (m.map != null) { int[] pm = posicionMapa.computeIfAbsent(m.map + "\u0000" + pos, k -> new int[2]); pm[0]++; if (gano) pm[1]++; } } }
                if (eloActualJugador == 0 && yo.rating != null && yo.rating > 0 && m.mode != null && m.mode.contains("1v1")) eloActualJugador = yo.rating + (yo.ratingDiff == null ? 0 : yo.ratingDiff);
                int rivalRating = 0, nRivales = 0;
                for (MatchPlayer p : m.players) {
                    if (p.id == actPid) continue;
                    if (p.team != yo.team) {
                        Object[] r = rivales.computeIfAbsent(p.id, k -> new Object[]{ p.name, 0, 0 });
                        r[1] = (int) r[1] + 1; if (gano) r[2] = (int) r[2] + 1;
                        if (p.rating != null) { rivalRating += p.rating; nRivales++; }
                        if (m.players.size() == 2 && p.civ != null && !p.civ.isBlank()) { int[] x = civsRival.computeIfAbsent(p.civ, k -> new int[2]); x[0]++; if (gano) x[1]++; }
                    } else {
                        Object[] r = aliados.computeIfAbsent(p.id, k -> new Object[]{ p.name, 0, 0 });
                        r[1] = (int) r[1] + 1; if (gano) r[2] = (int) r[2] + 1;
                    }
                }
                if (nRivales > 0) {
                    if (franjas == null) franjas = franjasCentradas(eloActualJugador > 0 ? eloActualJugador : (int) Math.round(rivalRating / (double) nRivales));
                    String tr = franjaDe(rivalRating / (double) nRivales, franjas, eloActualJugador > 0 ? eloActualJugador : (int) Math.round(rivalRating / (double) nRivales));
                    int[] x = tramos.computeIfAbsent(tr, k -> new int[2]); x[0]++; if (gano) x[1]++;
                }
            }
        }
        int diasActivos = porDia.size();
        for (Component c : actChips.getComponents()) if (c instanceof JLabel l && "forma".equals(l.getName())) actChips.remove(l);
        if (f10 > 0) {
            String forma = fW + "-" + fL + " · " + (fDiff >= 0 ? "+" : "") + fDiff + (fDiff > 0 ? " \u25B2" : fDiff < 0 ? " \u25BC" : "")
                    + (fRacha >= 2 ? " · " + t("racha ", "streak ") + fRacha + (Boolean.TRUE.equals(fRachaGana) ? "V" : "D") : "");
            JLabel lf = chipPerfil(t("Últimas ", "Last ") + f10 + ("*".equals(actModo) ? "" : " · " + actModo), escapeHtml(forma), t("Las partidas más recientes con resultado, en el modo elegido", "The most recent games with a result, in the chosen mode"));
            lf.setName("forma");
            actChips.add(lf);
        }
        actChips.revalidate(); actChips.repaint();
        if (!serie.isEmpty()) {
            long acum = eloActualLadder(modoGrafica, serie.get(0));
            for (long[] pt : serie) { pt[1] = acum; acum -= pt[2]; }
        }
        Collections.reverse(serie);
        actGrafica.datos(serie, (modoGrafica == null ? "" : modoGrafica + " · ") + t("últimas ", "last ") + serie.size() + t(" partidas", " games"));
        actTarjetas.removeAll();
        actTarjetas.add(listas.tarjeta(t("Partidas", "Games"), miles(partidas), (a.completo() ? t("último año", "last year") : t("últimas ", "last ") + miles(a.partidas().size()) + t(" (tope)", " (cap)")) + " · " + ("*".equals(actModo) ? t("todos los modos", "all modes") : actModo) + (actDesde != null ? " · " + (actHasta == null ? t("desde ", "since ") + actDesde : actDesde + " → " + actHasta) : "")));
        actTarjetas.add(listas.tarjeta(t("Winrate", "Win rate"), conResultado == 0 ? "-" : pct1(100.0 * victorias / conResultado), victorias + "-" + (conResultado - victorias) + t(" con resultado", " with a result")));
        actTarjetas.add(listas.tarjeta(t("Duración media", "Avg. length"), stats.duracionMedia(duracion, conDuracion), t("de reloj, inicio a fin", "wall clock, start to end")));
        actTarjetas.add(listas.tarjeta(t("Días con partidas", "Days with games"), String.valueOf(diasActivos), t("de los últimos ", "of the last ") + actDias));
        actTarjetas.add(listas.tarjeta(t("Por día activo", "Per active day"), diasActivos == 0 ? "-" : String.format(Locale.ROOT, "%.1f", partidas / (double) diasActivos).replace('.', "en".equals(IDIOMA) ? '.' : ','), t("partidas de media", "games on average")));
        actTarjetas.revalidate(); actTarjetas.repaint();
        actCalendario.datos(porDia, inicio, hoy);
        String[] dias = "en".equals(IDIOMA) ? new String[]{ "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" } : new String[]{ "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom" };
        actSemana.datos(dias, semana, semanaW, semanaN);
        String[] hl = new String[24], ht = new String[24]; int[] hv = new int[24], hw = new int[24], hn = new int[24];
        for (int i = 0; i < 24; i++) { hl[i] = i % 3 == 0 ? String.valueOf(i) : ""; ht[i] = String.format("%02d:00–%02d:59", i, i); hv[i] = horas[i]; hw[i] = horasW[i]; hn[i] = horasN[i]; }
        actHoras.datos(hl, hv, hw, hn, ht);
        List<String> mk = new ArrayList<>(meses.keySet());
        String[] ml = new String[mk.size()], mt2 = new String[mk.size()]; int[] mv = new int[mk.size()], mw = new int[mk.size()], mn = new int[mk.size()];
        for (int i = 0; i < mk.size(); i++) { ml[i] = String.valueOf(Integer.parseInt(mk.get(i).substring(5))); mt2[i] = mk.get(i); mv[i] = meses.get(mk.get(i))[0]; mw[i] = meses.get(mk.get(i))[1]; mn[i] = mv[i]; }
        actMeses.datos(ml, mv, mw, mn, mt2);
        Map<String, Runnable> irTechTree = new HashMap<>();
        for (String k : civs.keySet()) { String cl = techTree.claveCivDeNombre(k); if (cl != null) irTechTree.put(k, () -> navegacion.abrirTechTree(cl)); }
        for (String k : civsRival.keySet()) { String cl = techTree.claveCivDeNombre(k); if (cl != null) irTechTree.put(k, () -> navegacion.abrirTechTree(cl)); }
        pintarListaAgg(actCivs, t("Winrate por civ propia", "Win rate by own civ"), civs, 5, k -> k, irTechTree, k -> iconoCiv(techTree.claveCivDeNombre(k), 18));
        pintarListaAgg(actCivsRival, t("Winrate contra civ rival (1v1)", "Win rate vs opponent civ (1v1)"), civsRival, 5, k -> k, irTechTree, k -> iconoCiv(techTree.claveCivDeNombre(k), 18));
        pintarListaAgg(actMapas, t("Winrate por mapa", "Win rate by map"), mapas, 5, k -> k, null, k -> iconoMapa(k, 18));
        pintarListaJugadores(actRivales, t("Rivales más frecuentes", "Most frequent opponents"), rivales);
        pintarListaJugadores(actAliados, t("Aliados más frecuentes", "Most frequent allies"), aliados);
        Map<String, int[]> trOrd = new LinkedHashMap<>();
        if (franjas != null) for (String tr : franjas) if (tramos.containsKey(tr)) trOrd.put(tr, tramos.get(tr));
        pintarListaAgg(actTramos, t("Winrate por ELO del rival", "Win rate by opponent ELO") + (eloActualJugador > 0 ? " \u00B7 " + t("franjas centradas en ", "brackets centred on ") + eloActualJugador : ""), trOrd, 9, dev.tirador.aoe2radar.service.NombresStats::tramoNombre, null);
        actDuracionFila.removeAll();
        for (int i = 0; i < 5; i++) actDuracionFila.add(listas.tarjeta(dev.tirador.aoe2radar.service.ReglasPartida.DURACION_TRAMOS[i], durN[i] == 0 ? "\u2013" : pct1(100.0 * durW[i] / durN[i]), durN[i] == 0 ? t("sin partidas", "no games") : miles(durN[i]) + t(" partidas, ", " games, ") + miles(durW[i]) + t(" victorias", " wins")));
        actDuracionFila.revalidate(); actDuracionFila.repaint();
        Map<String, int[]> posOrd = new LinkedHashMap<>(); if (posicion.containsKey("pocket")) posOrd.put("pocket", posicion.get("pocket")); if (posicion.containsKey("flanco")) posOrd.put("flanco", posicion.get("flanco"));
        pintarListaAgg(actPosicion, t("Pocket o flanco (Team RM, 3v3 y 4v4)", "Pocket or flank (Team RM, 3v3 & 4v4)"), posOrd, 2, k -> dev.tirador.aoe2radar.service.NombresStats.posicionNombre(k), null);
        if (!posicionMapa.isEmpty()) {
            Map<String, int[][]> porMapa = new TreeMap<>();
            for (Map.Entry<String, int[]> en : posicionMapa.entrySet()) { String[] kv = en.getKey().split("\u0000"); int[][] f = porMapa.computeIfAbsent(kv[0], k -> new int[][]{ new int[2], new int[2] }); f["pocket".equals(kv[1]) ? 0 : 1] = en.getValue(); }
            List<Object[]> filasPos = new ArrayList<>();
            for (Map.Entry<String, int[][]> en : porMapa.entrySet()) { int[] pk = en.getValue()[0], fl = en.getValue()[1]; filasPos.add(new Object[]{ new Listas.Celda(iconoMapa(en.getKey(), 18), en.getKey(), null), (long) pk[0], pk[0] == 0 ? null : new Listas.Pct(100.0 * pk[1] / pk[0], pk[1], pk[0], true), (long) fl[0], fl[0] == 0 ? null : new Listas.Pct(100.0 * fl[1] / fl[0], fl[1], fl[0], true) }); }
            actPosicion.add(listas.enlaceVerTodo(porMapa.size(), () -> listas.mostrarTablaCompleta(t("Pocket o flanco por mapa", "Pocket or flank by map") + " \u00B7 " + actNombre, new String[]{ t("Mapa", "Map"), t("Partidas pocket", "Pocket games"), t("WR pocket", "Pocket WR"), t("Partidas flanco", "Flank games"), t("WR flanco", "Flank WR") }, filasPos, 1)));
        }
        pintarListaAgg(actUltimos30Civs, t("Últimos 30 días · civs", "Last 30 days · civs"), civs30, 5, k -> k, null, k -> iconoCiv(techTree.claveCivDeNombre(k), 18));
        pintarListaAgg(actUltimos30Mapas, t("Últimos 30 días · mapas", "Last 30 days · maps"), mapas30, 5, k -> k, null, k -> iconoMapa(k, 18));
        if (!actCargando) actEstado.setText(miles(a.partidas().size()) + t(" partidas", " games") + (a.completo() ? t(" · último año completo", " · full last year") : t(" · las más recientes", " · the most recent")));
        actMasBtn.setVisible(!a.completo() && !actCargando);
        actCuerpo.revalidate(); actCuerpo.repaint();
    }

    /** ELO actual del ladder que corresponde a un modo (de la ficha del perfil); si no hay ficha, el de la partida más reciente más su diferencia. */
    private long eloActualLadder(String modo, long[] masReciente) {
        String lb = modo == null ? null : (modo.toLowerCase(Locale.ROOT).contains("empire") || modo.toLowerCase(Locale.ROOT).startsWith("ew")) ? (modo.contains("1v1") ? "ew_1v1" : "ew_team") : modo.contains("1v1") ? "rm_1v1" : "rm_team";
        FichaPerfil perfil = perfiles.fichaConocida(actPid);
        if (perfil != null && lb != null && perfil.ladders().get(lb) instanceof int[] v && v[0] > 0) return v[0];
        return masReciente[1] + masReciente[2];
    }

    /** Lista «nombre · barra por partidas · WR» ordenada por partidas; solo entradas con ACT_MIN partidas. Package-private: la usa también CaraACaraDialogo. */
    void pintarListaAgg(JPanel panel, String titulo, Map<String, int[]> datos, int tope, java.util.function.Function<String, String> nombre, Map<String, Runnable> alClicar) { pintarListaAgg(panel, titulo, datos, tope, nombre, alClicar, null); }

    void pintarListaAgg(JPanel panel, String titulo, Map<String, int[]> datos, int tope, java.util.function.Function<String, String> nombre, Map<String, Runnable> alClicar, java.util.function.Function<String, Icon> icono) {
        panel.removeAll();
        boolean ordenado = datos instanceof LinkedHashMap;
        int modo = ordenado ? -1 : ordenListas.getOrDefault(titulo, 0);
        JLabel cab = tituloSeccion(titulo + (modo == 1 ? t("  · por winrate", "  · by win rate") : modo == 2 ? "  · A-Z" : ""),
                ordenado ? null : t("Clic para cambiar el orden: partidas → winrate → A-Z", "Click to change the order: games → win rate → A-Z"));
        if (!ordenado) {
            cab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cab.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { ordenListas.put(titulo, (ordenListas.getOrDefault(titulo, 0) + 1) % 3); actPintar(); } });
        }
        panel.add(cab);
        List<Map.Entry<String, int[]>> l = new ArrayList<>();
        for (Map.Entry<String, int[]> en : datos.entrySet()) if (en.getValue()[0] >= ACT_MIN) l.add(en);
        if (!ordenado) {
            if (modo == 1) l.sort((x, y) -> { double aa = 100.0 * x.getValue()[1] / x.getValue()[0], b = 100.0 * y.getValue()[1] / y.getValue()[0]; int c = Double.compare(b, aa); return c != 0 ? c : y.getValue()[0] - x.getValue()[0]; });
            else if (modo == 2) l.sort((x, y) -> nombre.apply(x.getKey()).compareToIgnoreCase(nombre.apply(y.getKey())));
            else l.sort((x, y) -> y.getValue()[0] - x.getValue()[0]);
        }
        double max = l.isEmpty() ? 1 : l.stream().mapToInt(en -> en.getValue()[0]).max().orElse(1);
        if (l.isEmpty()) { JLabel v = new JLabel(t("Nada con 3+ partidas.", "Nothing with 3+ games.")); v.setForeground(Color.GRAY); v.setFont(v.getFont().deriveFont(Font.PLAIN, 11f)); panel.add(v); }
        for (Map.Entry<String, int[]> en : l.subList(0, Math.min(tope, l.size()))) {
            int n = en.getValue()[0], w = en.getValue()[1];
            Runnable r = alClicar == null ? null : alClicar.get(en.getKey());
            panel.add(listas.filaBarra(icono == null ? null : icono.apply(en.getKey()), nombre.apply(en.getKey()), n / max, pct1(100.0 * w / n), colorWr(w, n), w + "-" + (n - w) + " · " + n + t(" partidas", " games") + (r != null ? (icono != null ? t(" · clic: tech tree", " · click: tech tree") : t(" · clic: abrir su perfil", " · click: open their profile")) : ""), r));
        }
        if (l.size() > tope) {
            panel.add(listas.enlaceVerTodo(l.size(), () -> {
                List<Object[]> filas = new ArrayList<>();
                for (Map.Entry<String, int[]> en : l) {
                    int n = en.getValue()[0], w = en.getValue()[1];
                    Runnable r = alClicar == null ? null : alClicar.get(en.getKey());
                    filas.add(new Object[]{ new Listas.Celda(icono == null ? null : icono.apply(en.getKey()), nombre.apply(en.getKey()), r), (long) n, (long) w, (long) (n - w), new Listas.Pct(100.0 * w / n, w, n, true) });
                }
                listas.mostrarTablaCompleta(titulo, new String[]{ t("Nombre", "Name"), t("Partidas", "Games"), t("V", "W"), t("D", "L"), "WR" }, filas, 1);
            }));
        }
        panel.revalidate(); panel.repaint();
    }

    /** Rivales o aliados: como la lista de agregados, con clic para abrir el perfil de cada uno. */
    private void pintarListaJugadores(JPanel panel, String titulo, Map<Long, Object[]> jugadores) {
        listas.esFilaJugador = true;
        try { pintarListaJugadoresImpl(panel, titulo, jugadores); } finally { listas.esFilaJugador = false; }
    }

    private boolean h2hDialogoAbierto() { return caraACara != null && caraACara.mostrado(); }

    private void pintarListaJugadoresImpl(JPanel panel, String titulo, Map<Long, Object[]> jugadores) {
        Map<String, int[]> datos = new HashMap<>();
        Map<String, Runnable> clics = new HashMap<>();
        for (Map.Entry<Long, Object[]> en : jugadores.entrySet()) {
            long pid = en.getKey(); String nombre = String.valueOf(en.getValue()[0]);
            String clave = nombre + "\u0000" + pid;
            datos.put(clave, new int[]{ (int) en.getValue()[1], (int) en.getValue()[2] });
            clics.put(clave, () -> { if (anfitrion.ultimoClicFueCtrl()) navegacion.abrirPerfilEnPestana(pid, anfitrion.nombreVisible(pid, nombre)); else navegacion.abrirPerfil(pid, anfitrion.nombreVisible(pid, nombre)); });
        }
        pintarListaAgg(panel, titulo, datos, listas.esFilaJugador && h2hDialogoAbierto() ? 10 : 5, k -> anfitrion.nombreVisible(Long.parseLong(k.substring(k.indexOf('\u0000') + 1)), k.substring(0, k.indexOf('\u0000'))), clics);
    }

    /** Abre (o reabre) el diálogo de cara a cara con el perfil actualmente abierto. */
    private void mostrarCaraACara() {
        if (caraACara == null) caraACara = new CaraACaraDialogo(SwingUtilities.getWindowAncestor(actividadPanel), this);
        caraACara.mostrar();
    }

    /** Abre el diálogo de cara a cara y fija de una vez el cruce con este rival (lo usa el aviso «Mi partida»).
     *  Si mostrarCaraACara() no llegó a crear el diálogo (sin historial cargado todavía), no fija nada, igual
     *  que la 1.1 (que protegía la llamada a h2hFijar con {@code if (h2hDialogo != null)}). */
    public void abrirCaraACaraCon(long rivalPid, String rivalNombre) {
        mostrarCaraACara();
        if (caraACara.creado()) caraACara.fijarRival(rivalPid, rivalNombre);
    }

    /** El botón lateral del ratón: si el diálogo de cara a cara está mostrado y con foco, es su «atrás»; si no, nada (lo decide la ventana). */
    public boolean h2hAtrasSiProcede() {
        if (caraACara != null && caraACara.mostradoConFoco()) { caraACara.irAtras(); return true; }
        return false;
    }
}
