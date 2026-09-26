package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.Campanas;
import dev.tirador.aoe2radar.service.ConsultasLadder;
import dev.tirador.aoe2radar.service.ControlService;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.LiveService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import static dev.tirador.aoe2radar.service.NombresStats.nombreCivStats;
import static dev.tirador.aoe2radar.service.ReglasPartida.modoCorto;
import static dev.tirador.aoe2radar.ui.Componentes.colorSecundario;
import static dev.tirador.aoe2radar.ui.Componentes.colorWr;
import static dev.tirador.aoe2radar.ui.Componentes.estiloCab;
import static dev.tirador.aoe2radar.ui.Componentes.subirArriba;
import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.ui.Iconos.iconoCiv;
import static dev.tirador.aoe2radar.ui.Iconos.iconoMapa;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.Formato.reloj;
import static dev.tirador.aoe2radar.util.Formato.truncarPx;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * La pestaña Live now (card "ahora"): partidas en curso de los 250 mejores del ladder 1v1 (u otra fuente: país,
 * clan, grupo) en tarjetas, la lista completa y las terminadas en las últimas 2 horas (sin resultado, con la rec
 * a un clic). Sale tal cual de SpoilerFreeRecs (construirPanelAhora/abrirAhora/ahoraPintar… de la 1.1): la parte
 * de Swing se queda aquí, el barrido periódico y el evento del socket viven en {@link LiveNowPresenter}, del que
 * esta vista es la Pantalla.
 * <p>Lo que se queda en la ventana (tanda 2, oleada B): los seis métodos de {@link MenusJugador} y el ELO 1v1 de
 * sesión, el toast y la campanita (Campanas UI), el bloque del socket «Partidas en curso en tiempo real» (llama a
 * {@link #liveEvento} desde cualquier hilo) y todo lo de la watchlist que esta vista solo necesita leer: por eso
 * pide a {@link Anfitrion} el país/clan de la fuente, el ELO y la lista de todos los jugadores, espectar, la
 * descarga de recs y demás acciones compartidas.
 */
public final class LiveNowView implements LiveNowPresenter.Pantalla {

    /** Lo que la vista pide a la ventana: nada de esto es navegación (eso va por {@link Navegacion}) ni menús de
     *  jugador (eso va por {@link MenusJugador}). Métodos con nombre de negocio, como en el resto de vistas. */
    public interface Anfitrion {
        /** Los tags de clan guardados (desplegable de la fuente «Clan»). */
        List<String> clanesGuardados();
        /** El país recordado de la watchlist (paisSel), o null: valor por defecto de la fuente «País». */
        String paisSel();
        /** El texto del buscador de clan de la watchlist, para recordar la última fuente «Clan» elegida. */
        String clanBuscado();
        /** ¿pid está en alguna vista con campana? (no se suelta del socket aunque salga del top). */
        boolean campanaContiene(long pid);
        /** Tras cambiar quién se vigila, hay que resuscribir el socket con los ids nuevos. */
        void sincronizarSocket();
        /** ¿El socket está conectado ahora mismo? Si lo está, no hacen falta barridos periódicos. */
        boolean socketConectado();
        /** El nombre visible de un jugador (con su alias, si tiene uno guardado). */
        String nombreVisible(long pid, String nombre);
        /** El país de un jugador que no está en la ficha de Live now (cache.Paises, vía la ventana). */
        String paisDe(long pid);
        /** Cierra la tarjeta flotante de ELO si estuviera abierta (antes de navegar a un perfil). */
        void ocultarHoverCard(boolean forzar);
        /** El aviso de siempre antes de espectar («va a abrir el juego…»). */
        boolean confirmarEspectar(String quien);
        /** Espectar esta partida (abre el juego). */
        void espectarPartida(long matchId);
        /** Abre una URL en el navegador (los enlaces de Twitch). */
        void abrirUrl(String url);
        /** Descarga (o envía al juego) estas partidas; alTerminar repinta la tarjeta cuando acaba. */
        void descargar(List<Match> partidas, boolean enviarAlJuego, Runnable alTerminar);
        /** La barra de estado global de la ventana (mostrarLista250 la usa si la lista aún no se ha cargado). */
        void estadoGlobal(String texto);
    }

    private static final int AHORA_TOP = 250;

    private final Campanas campanas;
    private final LiveService liveService;
    private final List<PaisItem> paises;
    private final List<Player> todosJugadores;
    private final Map<Long, Integer> eloWatch;
    private final Map<Long, String[]> twitchLive;
    private final Function<String, String> claveCivDeNombre;
    private final Tareas tareas;
    private final Navegacion navegacion;
    private final MenusJugador menus;
    private final Anfitrion anfitrion;
    private final Window ventana;
    private final LiveNowPresenter presenter;

    // ----- campos de construcción / configuración de la vista (ver construirPanelAhora) -----
    private JPanel ahoraPanel;
    private JLabel ahoraEstado, liveSub;
    private PanelScrollable ahoraCuerpo;
    private JComboBox<String> liveTipo, liveMapa, liveOrden, liveFuente;
    private JCheckBox liveTwitch, liveTopTop;
    private final List<String[]> liveFuentes = new ArrayList<>();   // {tipo, valor, etiqueta}: top · pais · clan · grupo
    private String liveFuenteTipo = "top", liveFuenteValor = "";
    private boolean liveRellenandoFuente;
    private JComboBox<String> liveValor;
    private JPanel liveValorCaja;
    private JLabel liveTitulo;
    private JPopupMenu liveClanPopup;
    private JScrollPane liveScroll;
    private int liveAnchoPintado;
    /** ¿La pestaña está abierta? La tocan también los abrir* de otras vistas (por eso es público). */
    public boolean ahoraAbierta;   // visible para RegresionCapturas y para el cromo de la ventana
    private boolean liveRellenando;
    private javax.swing.Timer ahoraTimer, liveReloj;
    private String liveMapaSel = "*";
    private final List<JLabel> liveRelojes = new ArrayList<>();
    /** Ids extra para el socket (Live now abierto, vistas con campana): el MISMO Set que usa refrescarCampanas y
     *  el bloque del socket en la ventana (Campanas se queda allí, ver la javadoc de la clase). */
    public final Set<Long> socketExtra = java.util.concurrent.ConcurrentHashMap.newKeySet();   // visible para RegresionCapturas

    public LiveNowView(Campanas campanas, LiveService liveService, List<PaisItem> paises, List<Player> todosJugadores,
                        Map<Long, Integer> eloWatch, Map<Long, String[]> twitchLive, Function<String, String> claveCivDeNombre,
                        Tareas tareas, Navegacion navegacion, MenusJugador menus, Window ventana, Anfitrion anfitrion) {
        this.campanas = campanas;
        this.liveService = liveService;
        this.paises = paises;
        this.todosJugadores = todosJugadores;
        this.eloWatch = eloWatch;
        this.twitchLive = twitchLive;
        this.claveCivDeNombre = claveCivDeNombre;
        this.tareas = tareas;
        this.navegacion = navegacion;
        this.menus = menus;
        this.ventana = ventana;
        this.anfitrion = anfitrion;
        this.presenter = new LiveNowPresenter(liveService::partidas, socketExtra, tareas, this, EstadoVivo.SISTEMA);
        construirPanelAhora();
    }

    /** El panel de la pestaña, para el CardLayout de la ventana y para esFondoDeseleccionable. */
    public JPanel panel() { return ahoraPanel; }

    // visible para RegresionCapturas
    public JPanel ahoraCuerpoPanel() { return ahoraCuerpo; }
    // visible para RegresionCapturas
    public JLabel ahoraEstadoLabel() { return ahoraEstado; }

    // ===== LiveNowPresenter.Pantalla =======================================================

    @Override public void estado(String texto) { ahoraEstado.setText(texto); }

    @Override public void pintar() { ahoraPintar(); }

    @Override public List<Object[]> cargarFuenteLive() throws Exception {
        return campanas.cargarFuenteLive(liveFuenteTipo, liveFuenteValor, AHORA_TOP, todosJugadores, eloWatch::get);
    }

    @Override public boolean campanaContiene(long pid) { return anfitrion.campanaContiene(pid); }

    @Override public void sincronizarSocket() { anfitrion.sincronizarSocket(); }

    @Override public boolean abierta() { return ahoraAbierta; }

    @Override public boolean puedeRepintar() { return ahoraAbierta && ahoraPanel != null && ahoraPanel.isShowing(); }

    // ===== Ciclo de vida (llamado desde abrirAhora, en el mismo orden que la 1.1) =========

    /** Marca la pestaña como abierta ANTES del CardLayout.show: el hilo del socket (puedeRepintar) puede leer
     *  esta bandera en cuanto se decide abrir, igual que la 1.1 (`ahoraAbierta = true;` antes del show). */
    public void marcarAbierta() { ahoraAbierta = true; }

    /** La parte de vista de abrirAhora que va justo tras mostrar la tarjeta "ahora" del CardLayout. */
    public void alAbrirAntes() {
        subirArriba(ahoraPanel);
    }

    /** La parte de vista de abrirAhora que va al final: arranca el timer, comprueba el mando a distancia y
     *  lanza el primer barrido, igual que la 1.1. */
    public void alAbrirDespues() {
        if (ahoraTimer == null) { ahoraTimer = new javax.swing.Timer(1_800_000, e -> { if (ahoraAbierta && ahoraPanel.isShowing() && !anfitrion.socketConectado()) presenter.refrescar(false); }); ahoraTimer.start(); }   // con el socket vivo no hay barridos periódicos: los eventos mantienen el estado
        if (!ControlService.activo("live_now")) { ahoraEstado.setText(t("Live now está pausado temporalmente por mantenimiento de la fuente de datos.", "Live now is paused temporarily for data-source maintenance.")); ahoraCuerpo.removeAll(); ahoraCuerpo.revalidate(); ahoraCuerpo.repaint(); return; }
        liveReloj.start();
        rellenarFuentesLive();
        presenter.refrescar(false);
    }

    /** Puente del socket (bloque «Partidas en curso en tiempo real», en la ventana) hacia el presentador: seguro
     *  de llamar desde CUALQUIER hilo, ver la javadoc de {@link LiveNowPresenter#liveEvento}. */
    public void liveEvento(long pid, Match m, boolean terminada) { presenter.liveEvento(pid, m, terminada); }

    /** Puente del socket: la partida matchId de pid terminó (fin: la de la API, o null). Ver
     *  {@link LiveNowPresenter#liveTerminada}. Seguro desde cualquier hilo. */
    public void liveTerminada(long pid, long matchId, Match fin) { presenter.liveTerminada(pid, matchId, fin); }

    /** Quiénes tiene Live now «en partida» en matchId: el socket lo pregunta ante un matchRemoved (revisión 1.3, F1).
     *  Seguro desde cualquier hilo. */
    public List<Long> jugadoresEnPartida(long matchId) { return presenter.jugadoresEn(matchId); }

    /** El barrido manual (botón «Actualizar», el timer y, tras una caída del socket, la reconexión): lo llama
     *  la ventana desde el bloque del socket. */
    public void refrescar(boolean forzar) { presenter.refrescar(forzar); }

    /** La ficha {pid, nombre, rating, rango, país} de pid en la fuente actual, o null: la usa elo1v1Conocido
     *  (que se queda en la ventana) con la guarda {@code ahora != null}. */
    public Object[] liveFicha(long pid) { return presenter.ficha(pid); }

    /** El nombre visible de pid según la fuente actual, o el pid como texto si no está: la usa avisarSiCampana
     *  (que se queda en la ventana) con la guarda {@code ahora != null}. */
    public String ahoraNombre(long pid) {
        Object[] f = presenter.ficha(pid);
        return f != null ? anfitrion.nombreVisible(pid, (String) f[1]) : String.valueOf(pid);
    }

    /** Copia de la fuente actual {pid, nombre, rating, rango, país}: la usa el «Cara a cara» de la watchlist
     *  (conjunto «Top 250 mundial»), que se queda en la ventana. */
    public List<Object[]> topSnapshot() { return presenter.topSnapshot(); }

    // ----- visible para RegresionCapturas: inyecta datos de Live now sin pasar por la red -----

    public void conTop(java.util.function.Consumer<List<Object[]>> accion) { presenter.conTop(accion); }
    public void conEnCurso(java.util.function.Consumer<Map<Long, Match>> accion) { presenter.conEnCurso(accion); }
    public void conTerminadas(java.util.function.Consumer<Map<Long, Object[]>> accion) { presenter.conTerminadas(accion); }
    public void fijarTopMs(long ms) { presenter.fijarTopMs(ms); }
    public void fijarUltimaMs(long ms) { presenter.fijarUltimaMs(ms); }

    // ===== Construcción del panel ===========================================================

    private void construirPanelAhora() {
        ahoraPanel = new JPanel(new BorderLayout(8, 6));
        ahoraPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel norte = new JPanel(new BorderLayout(8, 2));
        JPanel fila1 = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        liveTitulo = new JLabel("Live now");
        liveTitulo.setFont(liveTitulo.getFont().deriveFont(Font.BOLD, 15f));
        fila1.add(liveTitulo);
        ahoraEstado = new JLabel();
        ahoraEstado.setFont(ahoraEstado.getFont().deriveFont(Font.PLAIN, 11f));
        fila1.add(ahoraEstado);

        JButton refrescar = new JButton(t("Actualizar", "Refresh"));
        refrescar.setFocusable(false); refrescar.setMargin(new Insets(1, 8, 1, 8)); refrescar.putClientProperty("JButton.buttonType", "roundRect");
        refrescar.setToolTipText(t("Barrido completo ahora (entre barridos, las novedades llegan por el socket)", "Full sweep now (between sweeps, updates arrive through the socket)"));
        refrescar.addActionListener(e -> presenter.refrescar(true));
        fila1.add(refrescar);
        liveSub = new JLabel();
        liveSub.setFont(liveSub.getFont().deriveFont(Font.PLAIN, 13f));
        JPanel cabecera = new JPanel(new BorderLayout(0, 2)); cabecera.add(fila1, BorderLayout.NORTH); cabecera.add(liveSub, BorderLayout.SOUTH);
        norte.add(cabecera, BorderLayout.NORTH);
        JPanel filtrosFila = new JPanel(new BorderLayout(8, 0));
        JPanel filtros = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 2));
        liveFuente = new JComboBox<>(new String[]{ t("Top 250 mundial", "World top 250"), t("Top 100 país", "Country top 100"), t("Clan", "Clan"), t("Grupo", "Group") });
        liveFuente.setToolTipText(t("De quién quieres ver las partidas: los 250 mejores del mundo, el top 100 de un país, un clan o uno de tus grupos", "Whose games to show: the world's top 250, a country's top 100, a clan or one of your groups"));
        liveFuente.addActionListener(e -> { if (liveRellenandoFuente) return; String tipo = new String[]{ "top", "pais", "clan", "grupo" }[Math.max(0, liveFuente.getSelectedIndex())]; guardarConfig("live_fuente_tipo", tipo); rellenarValorLive(tipo); aplicarFuenteLive(); });
        filtros.add(new JLabel(t("Fuente:", "Source:"))); filtros.add(liveFuente);
        liveValor = new JComboBox<>(); liveValor.setMaximumRowCount(14);
        liveValor.addActionListener(e -> { if (liveRellenandoFuente) return; aplicarFuenteLive(); });
        liveValorCaja = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0)); liveValorCaja.setOpaque(false); liveValorCaja.add(liveValor);
        JButton irFuente = new JButton("\u2192"); irFuente.setFocusable(false); irFuente.setMargin(new Insets(1, 6, 1, 6)); irFuente.putClientProperty("JButton.buttonType", "roundRect");
        irFuente.setToolTipText(t("Cargar esta fuente (también con Enter)", "Load this source (Enter works too)"));
        irFuente.addActionListener(e -> aplicarFuenteLive());
        liveValorCaja.add(irFuente);
        ((JTextField) liveValor.getEditor().getEditorComponent()).addActionListener(e -> aplicarFuenteLive());   // Enter en el desplegable editable aplica
        filtros.add(liveValorCaja);
        liveTipo = new JComboBox<>(new String[]{ t("Todas", "All"), "1v1", t("Equipos", "Team"), "Empire Wars" });
        liveTipo.addActionListener(e -> ahoraPintar());
        filtros.add(new JLabel(t("Tipo:", "Type:"))); filtros.add(liveTipo);
        liveMapa = new JComboBox<>();
        liveMapa.addActionListener(e -> { if (liveRellenando) return; Object cl = liveMapa.getClientProperty("claves"); if (cl instanceof List<?> l && liveMapa.getSelectedIndex() >= 0 && liveMapa.getSelectedIndex() < l.size()) { liveMapaSel = String.valueOf(l.get(liveMapa.getSelectedIndex())); ahoraPintar(); } });
        filtros.add(new JLabel(t("Mapa:", "Map:"))); filtros.add(liveMapa);
        liveOrden = new JComboBox<>(new String[]{ t("Ordenar: ELO", "Sort: ELO"), t("Ordenar: tiempo de partida", "Sort: game time") });
        liveOrden.addActionListener(e -> ahoraPintar());
        filtros.add(liveOrden);
        liveTwitch = new JCheckBox(t("Solo con Twitch", "Twitch only")); liveTwitch.setFocusable(false); liveTwitch.addActionListener(e -> ahoraPintar());
        liveTopTop = new JCheckBox(t("Solo top contra top", "Top vs top only")); liveTopTop.setFocusable(false); liveTopTop.setToolTipText(t("Partidas en las que ambos bandos tienen a alguien del top 50", "Games where both sides have someone from the top 50")); liveTopTop.addActionListener(e -> ahoraPintar());
        filtros.add(liveTwitch); filtros.add(liveTopTop);
        filtrosFila.add(filtros, BorderLayout.CENTER);
        JButton verTop = new JButton(t("Ver la lista", "See the list"));
        verTop.setFocusable(false); verTop.setMargin(new Insets(1, 8, 1, 8)); verTop.putClientProperty("JButton.buttonType", "roundRect");
        verTop.setToolTipText(t("La lista completa de la fuente elegida, con filtros por país y nick, y espectar a quien esté jugando", "The full list of the chosen source, with country and nick filters, and spectate whoever is playing"));
        verTop.addActionListener(e -> mostrarLista250());
        JPanel der = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2)); der.add(verTop);
        filtrosFila.add(der, BorderLayout.EAST);
        norte.add(filtrosFila, BorderLayout.SOUTH);
        ahoraPanel.addComponentListener(new java.awt.event.ComponentAdapter() { @Override public void componentResized(java.awt.event.ComponentEvent e) { filtros.revalidate(); norte.revalidate(); } });   // la fila de filtros se reajusta al ancho real
        rellenarFuentesLive();
        ahoraPanel.add(norte, BorderLayout.NORTH);
        ahoraCuerpo = new PanelScrollable();
        ahoraCuerpo.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 6));
        JScrollPane sp = new JScrollPane(ahoraCuerpo, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {   // al cambiar el ancho, se recalcula el reparto (sin scroll horizontal nunca)
            javax.swing.Timer t;
            @Override public void componentResized(java.awt.event.ComponentEvent e) { if (t != null) t.stop(); t = new javax.swing.Timer(150, ev -> { if (ahoraAbierta && ahoraPanel.isShowing() && liveAnchoPintado != sp.getViewport().getWidth()) ahoraPintar(); }); t.setRepeats(false); t.start(); }
        });
        liveScroll = sp;
        sp.setBorder(null);
        ahoraPanel.add(sp, BorderLayout.CENTER);
        JLabel pie = new JLabel(t("Barrido al abrir y al pulsar Actualizar (con el socket caído, también cada 30 min); el socket del companion trae las novedades al instante. Doble clic en un nick: perfil · botón central: pestaña nueva · clic derecho: más. «Buscando partida» no lo publica ninguna API: solo se ven partidas ya empezadas.",
                "Sweep on open and on Refresh (also every 30 min while the socket is down); the companion's socket brings updates instantly. Double-click a nick: profile · middle button: new tab · right-click: more. \u201CIn queue\u201D is not published by any API: only started games are shown."));
        pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f));
        ahoraPanel.add(pie, BorderLayout.SOUTH);
        liveReloj = new javax.swing.Timer(1000, e -> { if (!ahoraAbierta) { liveReloj.stop(); return; } for (JLabel l : liveRelojes) { Object m = l.getClientProperty("match"); if (m instanceof Match mm && mm.started != null) l.setText(reloj(Duration.between(mm.started, Instant.now()))); } });
    }

    /** Título de sección que se pliega y despliega con un clic (estado recordado en config). */
    private JLabel tituloPlegable(String texto, String claveConfig) {
        JLabel l = Componentes.tituloSeccion(texto);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 15f));
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.setToolTipText(t("Clic para plegar o desplegar", "Click to collapse or expand"));
        l.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { guardarConfig(claveConfig, String.valueOf(!Boolean.parseBoolean(leerConfig(claveConfig, "false")))); ahoraPintar(); } });
        return l;
    }

    /** Ventana con la lista completa de la fuente: puesto, bandera, nick, ELO, país, en partida; filtros por país (desplegable con escritura) y por nick; espectar desde dentro; doble clic = perfil.
     *  Pública: la usa el botón «Ver la lista» y RegresionCapturas. */
    public void mostrarLista250() {
        List<Object[]> top = presenter.topSnapshot();
        if (top.isEmpty()) { anfitrion.estadoGlobal(t("Todavía no se ha cargado la lista: abre Live now un momento.", "The list isn't loaded yet: open Live now for a moment.")); return; }
        JDialog d = new JDialog(ventana, "Live now · " + etiquetaFuenteLive(), JDialog.ModalityType.MODELESS);
        DefaultTableModel modelo = new DefaultTableModel(new Object[]{ "#", t("Jugador", "Player"), "ELO", t("País", "Country"), t("Modo", "Mode"), t("Ahora", "Now"), "" }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 || c == 2 ? Integer.class : String.class; }
        };
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(tabla.getRowHeight() + 8);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(50); tabla.getColumnModel().getColumn(1).setPreferredWidth(220); tabla.getColumnModel().getColumn(2).setPreferredWidth(60); tabla.getColumnModel().getColumn(3).setPreferredWidth(60); tabla.getColumnModel().getColumn(4).setPreferredWidth(90); tabla.getColumnModel().getColumn(5).setPreferredWidth(200); tabla.getColumnModel().getColumn(6).setPreferredWidth(96);
        tabla.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel lab = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, col);
                lab.setIcon(value == null ? null : iconoBandera(String.valueOf(value))); lab.setIconTextGap(5); return lab;
            }
        });
        tabla.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {   // «Espectar» como botón pintado
            @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel lab = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, col);
                boolean hay = value != null && !String.valueOf(value).isEmpty();
                lab.setHorizontalAlignment(SwingConstants.CENTER);
                lab.setText(hay ? "\u25B6 " + t("Espectar", "Spectate") : "");
                lab.setForeground(hay ? (temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00)) : lab.getForeground());
                lab.setFont(lab.getFont().deriveFont(Font.BOLD));
                return lab;
            }
        });
        Map<Long, Match> vivos = presenter.enCursoSnapshot();
        List<Object[]> filas = new ArrayList<>();   // {pid, nombre, matchId}
        for (Object[] f : top) {
            Match m = vivos.get((Long) f[0]);
            String ahora = m == null ? "" : ("\u25CF " + (m.map == null ? "" : m.map) + (m.started != null ? " · " + reloj(Duration.between(m.started, Instant.now())) : ""));
            modelo.addRow(new Object[]{ LiveNowPresenter.conPuesto(f) ? f[3] : null, anfitrion.nombreVisible((Long) f[0], (String) f[1]), f[2], f[4] == null ? "" : String.valueOf(f[4]).toUpperCase(Locale.ROOT), m == null ? "" : modoCorto(m), ahora, m == null || m.id <= 0 ? "" : String.valueOf(m.id) });
            filas.add(new Object[]{ f[0], anfitrion.nombreVisible((Long) f[0], (String) f[1]), m == null ? 0L : m.id });
        }
        javax.swing.table.TableRowSorter<DefaultTableModel> sorter = new javax.swing.table.TableRowSorter<>(modelo);
        tabla.setRowSorter(sorter);
        // filtros: país (desplegable editable con scroll) y nick
        Set<String> paisesSet = new TreeSet<>(); for (Object[] f : top) if (f[4] != null && !String.valueOf(f[4]).isBlank()) paisesSet.add(String.valueOf(f[4]).toUpperCase(Locale.ROOT));
        JComboBox<String> paisCb = new JComboBox<>();
        paisCb.addItem(t("Todos los países", "All countries"));
        for (String cc : paisesSet) { String nombreP = cc; for (PaisItem pi : paises) if (pi.code().equalsIgnoreCase(cc)) nombreP = pi.nombre() + " (" + cc + ")"; paisCb.addItem(nombreP); }
        paisCb.setEditable(true); paisCb.setMaximumRowCount(16);
        paisCb.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean foc) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, value, index, sel, foc);
                String v = String.valueOf(value); int i = v.lastIndexOf('('); String cc = i > 0 ? v.substring(i + 1, v.length() - 1) : null;
                lab.setIcon(cc == null ? null : iconoBandera(cc)); lab.setIconTextGap(5); return lab;
            }
        });
        JTextField busca = new JTextField(16);
        busca.putClientProperty("JTextField.placeholderText", t("Filtrar por nick", "Filter by nick"));
        JCheckBox soloVivos = new JCheckBox(t("Solo en partida", "In a game only")); soloVivos.setFocusable(false);
        Runnable filtrar = () -> {
            List<RowFilter<Object, Object>> fs = new ArrayList<>();
            String q = busca.getText().trim();
            if (!q.isEmpty()) fs.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(q), 1));
            Object pv = paisCb.getEditor().getItem(); String ptxt = pv == null ? "" : String.valueOf(pv).trim();
            if (paisCb.getSelectedIndex() > 0 || (!ptxt.isEmpty() && !ptxt.equals(paisCb.getItemAt(0)))) {
                int i = ptxt.lastIndexOf('('); String cc = i > 0 && ptxt.endsWith(")") ? ptxt.substring(i + 1, ptxt.length() - 1) : ptxt;
                final String ccF = cc.toUpperCase(Locale.ROOT);
                fs.add(RowFilter.regexFilter("(?i)^" + java.util.regex.Pattern.quote(ccF), 3));
            }
            if (soloVivos.isSelected()) fs.add(RowFilter.regexFilter("\\u25CF", 5));
            sorter.setRowFilter(fs.isEmpty() ? null : RowFilter.andFilter(fs));
        };
        busca.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { filtrar.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { filtrar.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrar.run(); }
        });
        paisCb.addActionListener(e -> filtrar.run());
        ((JTextField) paisCb.getEditor().getEditorComponent()).getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {   // al escribir, filtra el desplegable y la tabla
            void cambio() { String q = String.valueOf(paisCb.getEditor().getItem()).trim().toLowerCase(Locale.ROOT); if (q.length() >= 2 && !paisCb.isPopupVisible()) { for (int i = 1; i < paisCb.getItemCount(); i++) if (paisCb.getItemAt(i).toLowerCase(Locale.ROOT).startsWith(q)) { paisCb.setPopupVisible(true); break; } } filtrar.run(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { filtrar.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrar.run(); }
        });
        soloVivos.addActionListener(e -> filtrar.run());
        tabla.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int vr = tabla.rowAtPoint(e.getPoint()); if (vr < 0) return;
                int mr = tabla.convertRowIndexToModel(vr);
                Object[] f = filas.get(mr);
                int vc = tabla.columnAtPoint(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1 && tabla.convertColumnIndexToModel(vc) == 6 && (Long) f[2] > 0) {   // «Espectar» con el aviso de siempre
                    if (anfitrion.confirmarEspectar((String) f[1])) anfitrion.espectarPartida((Long) f[2]);
                    return;
                }
                if (e.getClickCount() != 2) return;
                if (SwingUtilities.isMiddleMouseButton(e)) navegacion.abrirPerfilEnPestana((Long) f[0], (String) f[1]); else navegacion.abrirPerfil((Long) f[0], (String) f[1]);
            }
        });
        JPanel arriba = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        arriba.add(new JLabel(t("País:", "Country:"))); arriba.add(paisCb); arriba.add(busca); arriba.add(soloVivos);
        JLabel pie = new JLabel(t("Doble clic: perfil · botón central: pestaña nueva · «▶ Espectar» en quien está jugando · ordena clicando las cabeceras", "Double-click: profile · middle button: new tab · \u201C\u25B6 Spectate\u201D on whoever is playing · click headers to sort")); pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f)); pie.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        d.add(arriba, BorderLayout.NORTH); d.add(new JScrollPane(tabla), BorderLayout.CENTER); d.add(pie, BorderLayout.SOUTH);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setSize(860, 660); d.setLocationRelativeTo(ventana); d.setVisible(true);
    }

    private void rellenarFuentesLive() {
        liveRellenandoFuente = true;
        try {
            String tipo = leerConfig("live_fuente_tipo", "top");
            int idx = switch (tipo) { case "pais" -> 1; case "clan" -> 2; case "grupo" -> 3; default -> 0; };
            liveFuente.setSelectedIndex(idx);
            rellenarValorLive(tipo);
        } finally { liveRellenandoFuente = false; }
        aplicarFuenteLive();
    }

    /** Segundo desplegable según el tipo: países (con banderas, se puede escribir), clanes (guardados + escribir), grupos. Recuerda la última elección de cada tipo. */
    private void rellenarValorLive(String tipo) {
        boolean antes = liveRellenandoFuente; liveRellenandoFuente = true;
        try {
            liveValor.removeAllItems(); liveValor.setEditable(false); liveValor.setRenderer(new DefaultListCellRenderer());
            liveValorCaja.setVisible(!tipo.equals("top"));
            List<String> claves = new ArrayList<>();
            switch (tipo) {
                case "pais" -> {
                    for (PaisItem pi : paises) { claves.add(pi.code()); liveValor.addItem(pi.nombre() + " (" + pi.code().toUpperCase(Locale.ROOT) + ")"); }
                    liveValor.setRenderer(new DefaultListCellRenderer() {
                        @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean foc) {
                            JLabel lab = (JLabel) super.getListCellRendererComponent(l, value, index, sel, foc);
                            String v = String.valueOf(value); int i = v.lastIndexOf('('); lab.setIcon(i > 0 ? iconoBandera(v.substring(i + 1, v.length() - 1).toLowerCase(Locale.ROOT)) : null); lab.setIconTextGap(5); return lab;
                        }
                    });
                    liveValor.setEditable(true);
                    String cc = leerConfig("live_pais", anfitrion.paisSel() == null ? "es" : anfitrion.paisSel());
                    int i = claves.indexOf(cc); liveValor.setSelectedIndex(Math.max(0, i));
                }
                case "clan" -> {
                    for (String tag : anfitrion.clanesGuardados()) { claves.add(tag); liveValor.addItem(tag); }
                    liveValor.setEditable(true);
                    String tag = leerConfig("live_clan", anfitrion.clanBuscado());
                    if (!tag.isEmpty() && !claves.contains(tag)) { claves.add(tag); liveValor.addItem(tag); }
                    liveValor.setSelectedItem(tag.isEmpty() && liveValor.getItemCount() > 0 ? liveValor.getItemAt(0) : tag);
                    instalarPrediccionClanes();   // al escribir, sugiere clanes del ladder (como en Top clan)
                }
                case "grupo" -> {
                    Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); for (Player x : todosJugadores) gs.add(x.grupo());
                    for (String g : gs) { claves.add(g); liveValor.addItem(g); }
                    String g = leerConfig("live_grupo", ""); int i = claves.indexOf(g); liveValor.setSelectedIndex(Math.max(0, i));
                }
                default -> { }
            }
            liveValor.putClientProperty("claves", claves);
            liveValor.setPreferredSize(new Dimension(tipo.equals("pais") ? 220 : 160, liveValor.getPreferredSize().height));
            liveValorCaja.revalidate();
        } finally { liveRellenandoFuente = antes; }
    }

    /** Sugerencias de clan al escribir en el desplegable de Live now (misma lista que Top clan); Enter o clic aplican. */
    private void instalarPrediccionClanes() {
        JTextField ed = (JTextField) liveValor.getEditor().getEditorComponent();
        if (Boolean.TRUE.equals(ed.getClientProperty("prediccionClanes"))) return;
        ed.putClientProperty("prediccionClanes", true);
        liveClanPopup = new JPopupMenu(); liveClanPopup.setFocusable(false);
        ed.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() {
                if (!"clan".equals(new String[]{ "top", "pais", "clan", "grupo" }[Math.max(0, liveFuente.getSelectedIndex())])) return;
                String q = ed.getText().trim();
                liveClanPopup.setVisible(false); liveClanPopup.removeAll();
                if (q.length() < 1) return;
                if (!ConsultasLadder.clanesCargados()) { presenter.pedirClanes(); return; }
                for (Map.Entry<String, Integer> en : ConsultasLadder.sugerirClanes(q)) {
                    JMenuItem it = new JMenuItem(en.getKey() + "  (" + en.getValue() + ")");
                    it.addActionListener(a -> { liveClanPopup.setVisible(false); liveValor.setSelectedItem(en.getKey()); aplicarFuenteLive(); });
                    liveClanPopup.add(it);
                }
                if (liveClanPopup.getComponentCount() > 0) liveClanPopup.show(ed, 0, ed.getHeight());
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        ed.addActionListener(e -> { liveClanPopup.setVisible(false); aplicarFuenteLive(); });
    }

    /** Lee tipo y valor de los desplegables, los recuerda y, si cambian, recarga la fuente. */
    private void aplicarFuenteLive() {
        String tipo = new String[]{ "top", "pais", "clan", "grupo" }[Math.max(0, liveFuente.getSelectedIndex())];
        String valor = "";
        if (!tipo.equals("top")) {
            Object cl = liveValor.getClientProperty("claves");
            int i = liveValor.getSelectedIndex();
            if (cl instanceof List<?> l && i >= 0 && i < l.size()) valor = String.valueOf(l.get(i));
            else { Object ed = liveValor.isEditable() ? liveValor.getEditor().getItem() : liveValor.getSelectedItem(); valor = ed == null ? "" : String.valueOf(ed).trim(); if (tipo.equals("pais")) { int k = valor.lastIndexOf('('); if (k > 0 && valor.endsWith(")")) valor = valor.substring(k + 1, valor.length() - 1); valor = valor.toLowerCase(Locale.ROOT); } }
            if (valor.isEmpty()) return;
            guardarConfig(tipo.equals("pais") ? "live_pais" : tipo.equals("clan") ? "live_clan" : "live_grupo", valor);
        }
        if (tipo.equals(liveFuenteTipo) && valor.equals(liveFuenteValor)) { actualizarSubtituloLive(); return; }
        liveFuenteTipo = tipo; liveFuenteValor = valor;
        liveFuentes.clear();
        String etiqueta = switch (tipo) { case "pais" -> { String n = valor.toUpperCase(Locale.ROOT); for (PaisItem pi : paises) if (pi.code().equalsIgnoreCase(valor)) n = pi.nombre(); yield t("Top 100 · ", "Top 100 · ") + n; } case "clan" -> t("Clan ", "Clan ") + valor; case "grupo" -> t("Grupo ", "Group ") + valor; default -> t("Top 250 mundial", "World top 250"); };
        liveFuentes.add(new String[]{ tipo, valor, etiqueta });
        cambiarFuenteLive();
    }

    private String etiquetaFuenteLive() { for (String[] f : liveFuentes) if (f[0].equals(liveFuenteTipo) && f[1].equals(liveFuenteValor)) return f[2]; return t("Top 250 mundial", "World top 250"); }

    private void actualizarSubtituloLive() {
        if (liveSub == null) return;
        String quien = switch (liveFuenteTipo) {
            case "pais" -> t("los 100 mejores de ", "the top 100 of ") + etiquetaFuenteLive().replaceFirst("^Top 100 \u00B7 ", "");
            case "clan" -> t("los jugadores del clan ", "the players of clan ") + liveFuenteValor;
            case "grupo" -> t("los jugadores de tu grupo «", "the players of your group \u201C") + liveFuenteValor + (("es".equals(IDIOMA)) ? "»" : "\u201D");
            default -> t("los 250 mejores del mundo (ladder 1v1)", "the world's top 250 (1v1 ladder)");
        };
        liveSub.setText(t("Qué están jugando ahora mismo ", "What ") + quien + t(", en tiempo real; y sus partidas terminadas en las últimas dos horas, sin resultado.", " are playing right now, in real time; and their games finished in the last two hours, no result shown."));
        if (liveTitulo != null) liveTitulo.setText("Live now \u00B7 " + etiquetaFuenteLive());
    }

    private void cambiarFuenteLive() {
        presenter.reiniciarFuente();
        actualizarSubtituloLive();
        presenter.refrescar(true);
    }

    private void ahoraPintar() {
        List<Object[]> top = presenter.topSnapshot();
        Map<Long, Match> vivos = presenter.enCursoSnapshot();
        Map<Long, Object[]> fichas = new HashMap<>(); for (Object[] f : top) fichas.put((Long) f[0], f);
        // partidas únicas en curso (una tarjeta por partida, aunque haya varios del top dentro)
        Map<Long, Match> partidas = new java.util.LinkedHashMap<>();
        for (Match m : vivos.values()) partidas.putIfAbsent(m.id, m);
        List<Match> lista = new ArrayList<>(partidas.values());
        // mapas para el filtro
        liveRellenando = true;
        try {
            Map<String, Integer> mapas = new TreeMap<>();
            for (Match m : lista) if (m.map != null && !m.map.isBlank()) mapas.merge(m.map, 1, Integer::sum);
            List<String> claves = new ArrayList<>(); claves.add("*");
            liveMapa.removeAllItems(); liveMapa.addItem(t("Todos", "All"));
            for (Map.Entry<String, Integer> en : mapas.entrySet()) { claves.add(en.getKey()); liveMapa.addItem(en.getKey() + " (" + en.getValue() + ")"); }
            liveMapa.putClientProperty("claves", claves);
            if (!claves.contains(liveMapaSel)) liveMapaSel = "*";
            liveMapa.setSelectedIndex(claves.indexOf(liveMapaSel));
        } finally { liveRellenando = false; }
        // filtros
        lista.removeIf(m -> !liveTipoOk(m));
        if (!"*".equals(liveMapaSel)) lista.removeIf(m -> !liveMapaSel.equals(m.map));
        if (liveTwitch.isSelected()) lista.removeIf(m -> m.players.stream().noneMatch(mp -> twitchLive.containsKey(mp.id)));
        if (liveTopTop.isSelected()) lista.removeIf(m -> { Set<Integer> equipos = new HashSet<>(); for (MatchPlayer mp : m.players) { Object[] f = fichas.get(mp.id); if (LiveNowPresenter.enTop(f, 50)) equipos.add(mp.team); } return equipos.size() < 2; });
        java.util.function.ToIntFunction<Match> eloMax = m -> { int mx = 0; for (MatchPlayer mp : m.players) if (mp.rating != null) mx = Math.max(mx, mp.rating); return mx; };
        if (liveOrden.getSelectedIndex() == 1) lista.sort((a, b) -> { Instant x = a.started == null ? Instant.EPOCH : a.started, y = b.started == null ? Instant.EPOCH : b.started; return x.compareTo(y); });
        else lista.sort((a, b) -> eloMax.applyAsInt(b) - eloMax.applyAsInt(a));
        ahoraCuerpo.removeAll(); liveRelojes.clear();
        medirColumnasLive(ahoraCuerpo);
        liveAnchoPintado = liveScroll == null ? 0 : liveScroll.getViewport().getWidth();
        ajustarAnchosLive(liveAnchoPintado - 12);   // el reparto se calcula para el ancho real: todo cabe siempre
        boolean plegCurso = Boolean.parseBoolean(leerConfig("live_plegado_curso", "false")), plegFin = Boolean.parseBoolean(leerConfig("live_plegado_fin", "false"));
        ahoraCuerpo.add(tituloPlegable((plegCurso ? "\u25B8 " : "\u25BE ") + t("En partida ahora", "In a game now") + "  ·  " + lista.size() + (lista.size() == 1 ? t(" partida", " game") : t(" partidas", " games")), "live_plegado_curso"));
        if (!plegCurso) {
            RejillaLive rej = new RejillaLive();
            cabeceraLive(rej);
            int i = 1;
            for (Match m : lista) tarjetaLive(rej, i++, m, fichas, false);
            ahoraCuerpo.add(rej);
            if (lista.isEmpty()) { JLabel vac = new JLabel(presenter.cargando() ? t("Consultando…", "Checking…") : t("Nadie de esta fuente en partida con estos filtros.", "Nobody from this source in a game with these filters.")); vac.setForeground(Color.GRAY); vac.setAlignmentX(0f); ahoraCuerpo.add(vac); }
        }
        List<Object[]> terminadas = presenter.terminadasVigentes();
        terminadas.removeIf(x -> !liveTipoOk((Match) x[0]) || (!"*".equals(liveMapaSel) && !liveMapaSel.equals(((Match) x[0]).map)));
        if (liveOrden.getSelectedIndex() == 0) terminadas.sort((a, b) -> eloMax.applyAsInt((Match) b[0]) - eloMax.applyAsInt((Match) a[0]));   // por el jugador de mayor ELO de la partida
        else terminadas.sort((a, b) -> Long.compare((Long) b[1], (Long) a[1]));
        ahoraCuerpo.add(Box.createVerticalStrut(10));
        ahoraCuerpo.add(tituloPlegable((plegFin ? "\u25B8 " : "\u25BE ") + t("Terminadas en las últimas 2 horas", "Finished in the last 2 hours") + "  ·  " + terminadas.size() + "   " + t("(sin resultado: la rec, a un clic)", "(no result shown: the rec, one click away)"), "live_plegado_fin"));
        if (!plegFin) {
            RejillaLive rej = new RejillaLive();
            cabeceraLive(rej);
            int i = 1;
            for (Object[] x : terminadas) tarjetaLive(rej, i++, (Match) x[0], fichas, true);
            ahoraCuerpo.add(rej);
            if (terminadas.isEmpty()) { JLabel vac = new JLabel(t("Todavía ninguna (se van acumulando mientras la vista esté abierta).", "None yet (they accumulate while this view is open).")); vac.setForeground(Color.GRAY); vac.setAlignmentX(0f); ahoraCuerpo.add(vac); }
        }
        ahoraCuerpo.revalidate(); ahoraCuerpo.repaint();
        String hace = presenter.ultimaMs() == 0 ? "" : t(" · barrido hace ", " · sweep ") + Math.max(0, (System.currentTimeMillis() - presenter.ultimaMs()) / 60_000) + t(" min", " min ago");
        ahoraEstado.setText(vivos.size() + t(" de ", " of ") + top.size() + t(" en partida", " in a game") + hace + (anfitrion.socketConectado() ? t(" · socket en vivo", " · live socket") : ""));
    }

    private boolean liveTipoOk(Match m) {
        int sel = liveTipo == null ? 0 : liveTipo.getSelectedIndex();
        String modo = m.mode == null ? "" : m.mode.toLowerCase(Locale.ROOT);
        return switch (sel) {
            case 1 -> m.players.size() == 2 && !modo.contains("empire") && !modo.contains("ew");
            case 2 -> m.players.size() > 2;
            case 3 -> modo.contains("empire") || modo.contains(" ew") || modo.startsWith("ew");
            default -> true;
        };
    }

    /** Cabecera de un bando, con las mismas celdas que filaJugadorLive. */
    private JPanel cabeceraBando() {
        JPanel b = new JPanel(new GridBagLayout()); b.setOpaque(false);
        medirColumnasLive(b);
        GridBagConstraints gc = new GridBagConstraints(); gc.gridy = 0; gc.anchor = GridBagConstraints.WEST;
        String[] textos = { "#", "", t("Jugador", "Player"), "", "ELO", "1v1", t("Civ", "Civ") };
        for (int i = 0; i < textos.length; i++) {
            JLabel l = new JLabel(textos[i], i == 0 ? SwingConstants.RIGHT : SwingConstants.LEFT); estiloCab(l);
            l.setPreferredSize(new Dimension(LIVE_COLS[i], 16)); l.setMinimumSize(new Dimension(LIVE_COLS_MIN[i], 16)); l.setMaximumSize(new Dimension(LIVE_COLS[i], 16));
            gc.gridx = i; gc.insets = new Insets(0, 0, 0, i == 1 || i == 2 ? 4 : 6); b.add(l, gc);
        }
        JPanel relleno = new JPanel(); relleno.setOpaque(false); gc.gridx = textos.length; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(0, 0, 0, 0); b.add(relleno, gc);
        return b;
    }

    /** Cabecera de columnas de Live now: la fila 0 de la rejilla compartida de la sección. */
    private void cabeceraLive(RejillaLive cab) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0; gc.anchor = GridBagConstraints.WEST; gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(2, 0, 4, 10);
        JLabel lMapa = new JLabel(t("Mapa · modo", "Map · mode")); estiloCab(lMapa); lMapa.setPreferredSize(new Dimension(liveMapaW, 16)); lMapa.setMinimumSize(new Dimension(liveMapaW, 16));
        gc.gridx = 0; gc.weightx = 0; gc.insets = new Insets(2, 8, 4, liveGapMapa); cab.add(lMapa, gc);
        cab.cabMapa = lMapa;
        gc.insets = new Insets(2, 0, 4, 10);
        gc.gridx = 1; gc.weightx = 1; cab.add(cabeceraBando(), gc);
        JLabel vs = new JLabel(""); vs.setPreferredSize(new Dimension(30, 16)); gc.gridx = 2; gc.weightx = 0; cab.add(vs, gc);
        gc.gridx = 3; gc.weightx = 1; cab.add(cabeceraBando(), gc);
        JLabel lT = new JLabel(t("Tiempo", "Time"), SwingConstants.RIGHT); estiloCab(lT); lT.setPreferredSize(new Dimension(liveTiempoW, 16)); lT.setMinimumSize(new Dimension(liveTiempoW, 16));
        gc.gridx = 4; gc.weightx = 0; cab.add(lT, gc);
        cab.cabTiempo = lT;
        JLabel lB = new JLabel(""); lB.setPreferredSize(new Dimension(liveBotonesW, 16)); lB.setMinimumSize(new Dimension(liveBotonesW, 16)); gc.gridx = 5; gc.insets = new Insets(2, 0, 4, 8); cab.add(lB, gc);
    }

    /** Una tarjeta de partida en rejilla fija: mapa grande · bando A · vs · bando B · reloj · botones. Las subcolumnas de cada jugador van alineadas en todas las tarjetas.
     *  Sección de Live now: UNA rejilla para la cabecera y todas las tarjetas, así las columnas coinciden siempre, en cualquier anchura. Pinta filas alternas y separadores. */
    private static final class RejillaLive extends JPanel {
        final List<Component> filas = new ArrayList<>();            // el bloque de mapa de cada fila
        final List<List<Component>> celdasFila = new ArrayList<>();  // todas las celdas de cada fila: el fondo alterno cubre la altura real (en TG el bando es más alto que el mapa)
        Component cabMapa, cabTiempo;
        RejillaLive() { super(new GridBagLayout()); setOpaque(false); setAlignmentX(0f); }
        void celda(int fila, Component c) { while (celdasFila.size() < fila) celdasFila.add(new ArrayList<>()); celdasFila.get(fila - 1).add(c); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int i = 0, yPrimero = -1, yUltimo = -1;
            for (List<Component> cs : celdasFila) {
                if (cs.isEmpty()) continue;
                int y0 = Integer.MAX_VALUE, y1 = 0;
                for (Component c : cs) { y0 = Math.min(y0, c.getY()); y1 = Math.max(y1, c.getY() + c.getHeight()); }
                if (yPrimero < 0) yPrimero = y0 - 4;
                yUltimo = y1 + 4;
                if (i++ % 2 == 1) { g.setColor(new Color(128, 128, 128, temaOscuroActivo ? 22 : 16)); g.fillRoundRect(0, y0 - 4, getWidth(), y1 - y0 + 8, 8, 8); }
            }
            g.setColor(new Color(128, 128, 128, 50));   // separadores verticales tenues: tras el mapa y antes del reloj
            if (cabMapa != null && yPrimero >= 0) { int x = cabMapa.getX() + cabMapa.getWidth() + 20; g.drawLine(x, yPrimero, x, yUltimo); if (cabTiempo != null) { int x2 = cabTiempo.getX() - 6; g.drawLine(x2, yPrimero, x2, yUltimo); } }
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }   // nunca por debajo de lo preferido: si no cabe, se recorta por la derecha, pero no se aplasta
    }

    private void tarjetaLive(RejillaLive card, int fila, Match m, Map<Long, Object[]> fichas, boolean terminada) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = fila; gc.anchor = GridBagConstraints.WEST; gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(6, 0, 6, 10);
        // mapa: miniatura grande + nombre + modo
        JPanel mapa = new JPanel(new BorderLayout(8, 0)); mapa.setOpaque(false);
        JLabel mini = new JLabel(iconoMapa(m.map, 56));
        mini.setPreferredSize(new Dimension(56, 56));
        mapa.add(mini, BorderLayout.WEST);
        JPanel mapaTxt = new JPanel(); mapaTxt.setLayout(new BoxLayout(mapaTxt, BoxLayout.Y_AXIS)); mapaTxt.setOpaque(false);
        JLabel mapaNombre = new JLabel(m.map == null || m.map.isBlank() ? "?" : m.map);
        mapaNombre.setFont(mapaNombre.getFont().deriveFont(Font.BOLD, 14f));
        String modoTxt = m.mode == null ? "" : m.mode;
        boolean ranked = !modoTxt.toLowerCase(Locale.ROOT).contains("unranked") && !modoTxt.toLowerCase(Locale.ROOT).contains("custom") && !modoTxt.isBlank();
        JLabel modo = new JLabel(modoTxt);
        modo.setFont(modo.getFont().deriveFont(ranked ? Font.PLAIN : Font.ITALIC, 12f)); modo.setForeground(ranked ? (temaOscuroActivo ? new Color(0x9f, 0xc5, 0xe8) : new Color(0x2f, 0x5f, 0x8f)) : Color.GRAY);
        mapaTxt.add(mapaNombre); mapaTxt.add(modo);
        { Set<Integer> con50 = new HashSet<>(), con25 = new HashSet<>();
          for (MatchPlayer mp : m.players) { Object[] f = fichas.get(mp.id); if (LiveNowPresenter.enTop(f, 50)) con50.add(mp.team); if (LiveNowPresenter.enTop(f, 25)) con25.add(mp.team); }
          String etq = m.players.size() == 2 && con25.size() >= 2 ? t("élite 25 vs 25", "elite 25 vs 25") : con50.size() >= 2 ? "top 50 vs top 50" : null;
          if (etq != null) { JLabel tt = new JLabel(etq); tt.setFont(tt.getFont().deriveFont(Font.BOLD, 10.5f)); tt.setForeground(temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00)); tt.setToolTipText(m.players.size() == 2 && con25.size() >= 2 ? t("Los dos jugadores están en el top 25 del ladder 1v1", "Both players are in the 1v1 ladder's top 25") : t("Los dos bandos tienen a alguien del top 50 del ladder 1v1", "Both sides have someone from the 1v1 ladder's top 50")); mapaTxt.add(tt); } }
        mapa.add(mapaTxt, BorderLayout.CENTER);
        mapa.setPreferredSize(new Dimension(liveMapaW, 58)); mapa.setMinimumSize(new Dimension(liveMapaW, 58));
        gc.gridx = 0; gc.weightx = 0; gc.insets = new Insets(6, 8, 6, liveGapMapa); card.add(mapa, gc);   // aire tras el mapa: se lee solo
        card.filas.add(mapa); card.celda(fila, mapa);
        gc.insets = new Insets(6, 0, 6, 10);
        // bandos
        Map<Integer, List<MatchPlayer>> equipos = new TreeMap<>();
        for (MatchPlayer mp : m.players) equipos.computeIfAbsent(mp.team, k -> new ArrayList<>()).add(mp);
        List<List<MatchPlayer>> bandos = new ArrayList<>(equipos.values());
        boolean ffa = bandos.size() > 2;
        if (ffa) {   // FFA / custom con más de dos equipos: dos columnas (mitad y mitad); si no, la rejilla necesitaba un ancho por equipo y lo aplastaba todo
            List<MatchPlayer> todos = new ArrayList<>(); for (List<MatchPlayer> b : bandos) todos.addAll(b);
            int mitad = (todos.size() + 1) / 2;
            bandos = new ArrayList<>(List.of(new ArrayList<>(todos.subList(0, mitad)), new ArrayList<>(todos.subList(mitad, todos.size()))));
        }
        int col = 1;
        for (int b = 0; b < 2; b++) {
            if (b > 0) {
                JLabel vs = new JLabel(ffa ? "ffa" : "vs", SwingConstants.CENTER); vs.setForeground(Color.GRAY); vs.setFont(vs.getFont().deriveFont(Font.BOLD, ffa ? 11f : 13f));
                vs.setPreferredSize(new Dimension(30, 20));
                gc.gridx = col++; gc.weightx = 0; card.add(vs, gc);
            }
            JPanel bando = new JPanel(); bando.setLayout(new BoxLayout(bando, BoxLayout.Y_AXIS)); bando.setOpaque(false);
            boolean unoContraUno = m.players.size() == 2;
            if (b < bandos.size()) for (MatchPlayer mp : bandos.get(b)) bando.add(filaJugadorLive(mp, fichas.get(mp.id), unoContraUno));
            bando.setMinimumSize(bando.getPreferredSize());   // el bando nunca encoge (ni en ancho ni en alto)
            gc.gridx = col++; gc.weightx = 1; card.add(bando, gc); card.celda(fila, bando);
        }
        // reloj / estado
        JLabel tiempo = new JLabel("", SwingConstants.RIGHT);
        tiempo.setPreferredSize(new Dimension(liveTiempoW, 24)); tiempo.setMinimumSize(new Dimension(liveTiempoW, 24));
        if (terminada) {
            long hace = m.finished == null ? 0 : Duration.between(m.finished, Instant.now()).toMinutes();
            tiempo.setText(t("hace ", "") + hace + t(" min", " min ago"));
            tiempo.setFont(tiempo.getFont().deriveFont(Font.PLAIN, 13f)); tiempo.setForeground(Color.GRAY);
            tiempo.setToolTipText(t("Terminó hace ", "Finished ") + hace + t(" minutos", " minutes ago"));
        } else {
            tiempo.putClientProperty("match", m);
            tiempo.setText(m.started == null ? "" : reloj(Duration.between(m.started, Instant.now())));
            tiempo.setFont(tiempo.getFont().deriveFont(Font.BOLD, 16f));
            tiempo.setForeground(temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00));
            tiempo.setToolTipText(t("Tiempo de partida (reloj de pared desde el inicio)", "Game time (wall clock since start)"));
            liveRelojes.add(tiempo);
        }
        gc.gridx = col++; gc.weightx = 0; card.add(tiempo, gc); card.celda(fila, tiempo);
        // botones
        JPanel botones = new JPanel(); botones.setLayout(new BoxLayout(botones, BoxLayout.Y_AXIS)); botones.setOpaque(false);
        botones.setPreferredSize(new Dimension(liveBotonesW, 44)); botones.setMinimumSize(new Dimension(liveBotonesW, 44));
        if (terminada) {
            if (m.enJuego) {
                JLabel ok = new JLabel("\u2713 " + t("Enviada al juego", "Sent to the game")); ok.setForeground(colorWr(60, 100)); ok.setFont(ok.getFont().deriveFont(Font.BOLD, 12.5f)); ok.setAlignmentX(1f);
                botones.add(Box.createVerticalGlue()); botones.add(ok); botones.add(Box.createVerticalGlue());
            } else if (m.enDisco) {
                JLabel ok = new JLabel("\u2713 " + t("Descargada", "Downloaded")); ok.setForeground(colorWr(60, 100)); ok.setFont(ok.getFont().deriveFont(Font.BOLD, 12.5f)); ok.setAlignmentX(1f);
                botones.add(ok);
                JButton env = new JButton(t("Enviar al juego", "Send to game"));
                env.setFocusable(false); env.setMargin(new Insets(1, 8, 1, 8)); env.putClientProperty("JButton.buttonType", "roundRect"); env.setAlignmentX(1f);
                env.addActionListener(e -> anfitrion.descargar(List.of(m), true, this::ahoraPintar));
                botones.add(env);
            } else {
                JButton rec = new JButton(t("Descargar rec", "Download rec"));
                rec.setFocusable(false); rec.setMargin(new Insets(1, 8, 1, 8)); rec.putClientProperty("JButton.buttonType", "roundRect");
                rec.setToolTipText(t("Descarga la grabación de esta partida (sin resultado); te quedas en esta pantalla", "Download this game's recording (no result shown); you stay on this screen"));
                rec.addActionListener(e -> { if (m.refId == 0 && !m.players.isEmpty()) m.refId = m.players.get(0).id; rec.setEnabled(false); rec.setText(t("Descargando…", "Downloading…")); anfitrion.descargar(List.of(m), false, this::ahoraPintar); });
                rec.setAlignmentX(1f);
                JLabel res = new JLabel("<html><u>" + t("Resultado", "Result") + "</u></html>"); res.setFont(res.getFont().deriveFont(Font.PLAIN, 11f)); res.setForeground(colorSecundario()); res.setAlignmentX(1f);
                res.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); res.setToolTipText(t("Revela quién ganó esta partida (solo aquí)", "Reveals who won this game (only here)"));
                res.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) {
                    Runnable pintar = () -> {
                        List<String> ganadores = new ArrayList<>(); for (MatchPlayer mp : m.players) if (Boolean.TRUE.equals(mp.won)) ganadores.add(anfitrion.nombreVisible(mp.id, mp.name));
                        res.setText("<html><b>" + (ganadores.isEmpty() ? t("Aún sin resultado (unos minutos)", "No result yet (a few minutes)") : t("Ganó ", "Won: ") + escapeHtml(String.join(", ", ganadores))) + "</b></html>"); res.setForeground(ganadores.isEmpty() ? colorSecundario() : colorWr(100, 100)); res.setCursor(Cursor.getDefaultCursor());
                    };
                    boolean hay = false; for (MatchPlayer mp : m.players) if (mp.won != null) hay = true;
                    if (hay || m.players.isEmpty()) { pintar.run(); return; }
                    res.setText(t("consultando…", "checking…"));
                    presenter.pedirResultado(m, pintar);
                } });
                botones.add(Box.createVerticalGlue()); botones.add(rec); botones.add(Box.createVerticalStrut(2)); botones.add(res); botones.add(Box.createVerticalGlue());
            }
        } else if (m.id > 0) {
            JButton esp = new JButton(t("Espectar", "Spectate"));
            esp.setFocusable(false); esp.setMargin(new Insets(1, 8, 1, 8)); esp.putClientProperty("JButton.buttonType", "roundRect"); esp.setAlignmentX(1f);
            esp.addActionListener(e -> { if (anfitrion.confirmarEspectar(m.map == null ? "" : m.map)) anfitrion.espectarPartida(m.id); });   // el mismo aviso «va a abrir el juego» que en el resto de la app
            botones.add(Box.createVerticalGlue()); botones.add(esp); botones.add(Box.createVerticalGlue());
        }
        gc.gridx = col; gc.insets = new Insets(6, 0, 6, 8); card.add(botones, gc); card.celda(fila, botones);
    }

    private static final int[] LIVE_COLS_BASE = { 44, 26, 180, 26, 56, 66, 150 };   // puesto · bandera · nick · TW · ELO · 1v1 · civ: anchos base
    private static int[] LIVE_COLS = LIVE_COLS_BASE.clone(), LIVE_COLS_MIN = LIVE_COLS_BASE.clone();   // las celdas numéricas se miden con la fuente real (DPI, Segoe…); ninguna celda encoge nunca
    private static boolean liveColsMedidas;

    /** Mide con la fuente de verdad lo que ocupan «#250», «(2999)» y «1v1 2999» y ajusta las celdas: así nunca se recortan, sea cual sea el escalado. */
    private static void medirColumnasLive(JComponent c) {
        if (liveColsMedidas) return;
        FontMetrics fmPuesto = c.getFontMetrics(c.getFont().deriveFont(Font.BOLD, 11.5f)), fmElo = c.getFontMetrics(c.getFont().deriveFont(Font.PLAIN, 12.5f)), fmUno = c.getFontMetrics(c.getFont().deriveFont(Font.PLAIN, 11f));
        int puesto = Math.max(LIVE_COLS_BASE[0], fmPuesto.stringWidth("#250") + 8), elo = Math.max(LIVE_COLS_BASE[4], fmElo.stringWidth("(2999)") + 8), uno = Math.max(LIVE_COLS_BASE[5], fmUno.stringWidth("1v1 2999") + 10);
        LIVE_COLS_MEDIDAS = new int[]{ puesto, LIVE_COLS_BASE[1], LIVE_COLS_BASE[2], LIVE_COLS_BASE[3], elo, uno, LIVE_COLS_BASE[6] };
        LIVE_COLS = LIVE_COLS_MEDIDAS.clone();
        LIVE_COLS_MIN = LIVE_COLS.clone();
        liveColsMedidas = true;
    }
    private static int[] LIVE_COLS_MEDIDAS = LIVE_COLS_BASE.clone();
    private static int liveMapaW = 210, liveGapMapa = 40, liveTiempoW = 110, liveBotonesW = 170;

    /** Reparte el ancho disponible: si la rejilla completa no cabe, ceden por este orden el aire tras el mapa, el bloque de mapa, los botones, el reloj y por último nick y civ (con «…»). Nunca hay scroll horizontal. */
    private static void ajustarAnchosLive(int disponible) {
        int[] c = LIVE_COLS_MEDIDAS.clone();
        int mapa = 210, gap = 40, tiempo = 110, botones = 170;
        java.util.function.IntSupplier total = () -> { int bando = 10; for (int i = 0; i < c.length; i++) bando += c[i] + (i == 1 || i == 2 ? 4 : 6); return 8 + mapaHolder[0] + gapHolder[0] + 2 * bando + 10 + 30 + 10 + tiempoHolder[0] + 10 + botonesHolder[0] + 8; };
        mapaHolder[0] = mapa; gapHolder[0] = gap; tiempoHolder[0] = tiempo; botonesHolder[0] = botones;
        if (disponible > 100) {
            if (total.getAsInt() > disponible) gapHolder[0] = 16;
            if (total.getAsInt() > disponible) mapaHolder[0] = 160;
            if (total.getAsInt() > disponible) botonesHolder[0] = 130;
            if (total.getAsInt() > disponible) tiempoHolder[0] = 80;
            if (total.getAsInt() > disponible) {   // nick y civ ceden a partes iguales, hasta un mínimo legible
                int sobra = total.getAsInt() - disponible;
                int quitaNick = Math.min(c[2] - 90, (sobra + 3) / 4), quitaCiv = Math.min(c[6] - 70, (sobra + 3) / 4);
                c[2] -= Math.max(0, quitaNick); c[6] -= Math.max(0, quitaCiv);
                sobra = total.getAsInt() - disponible;
                if (sobra > 0) { int q = Math.min(c[2] - 90, (sobra + 3) / 2); c[2] -= Math.max(0, q); sobra = total.getAsInt() - disponible; if (sobra > 0) c[6] -= Math.max(0, Math.min(c[6] - 70, (sobra + 3) / 2)); }
            }
        }
        LIVE_COLS = c; LIVE_COLS_MIN = c.clone();
        liveMapaW = mapaHolder[0]; liveGapMapa = gapHolder[0]; liveTiempoW = tiempoHolder[0]; liveBotonesW = botonesHolder[0];
    }
    private static final int[] mapaHolder = { 210 }, gapHolder = { 40 }, tiempoHolder = { 110 }, botonesHolder = { 170 };

    /** Etiqueta que recorta su texto con «…» al ancho que tenga en cada momento (nunca se parte en dos líneas ni se sale). */
    private static final class EtiquetaRecorte extends JLabel {
        String completo = "";
        EtiquetaRecorte() { super(); }
        void texto(String s) { completo = s == null ? "" : s; super.setText(completo); }
        @Override protected void paintComponent(Graphics g) {
            int disponible = getWidth() - getInsets().left - getInsets().right - (getIcon() != null ? getIcon().getIconWidth() + getIconTextGap() : 0);
            String vis = truncarPx(completo, getFontMetrics(getFont()), Math.max(10, disponible));
            if (!vis.equals(getText())) super.setText(vis);
            super.paintComponent(g);
        }
    }

    /** Un jugador dentro de un bando, en texto plano y celdas fijas: nada se parte ni se desplaza, sea cual sea el nick. */
    private JPanel filaJugadorLive(MatchPlayer mp, Object[] ficha, boolean grande) {
        JPanel fila = new JPanel(new GridBagLayout()); fila.setOpaque(false); fila.setAlignmentX(0f);
        medirColumnasLive(fila);
        GridBagConstraints gc = new GridBagConstraints(); gc.gridy = 0; gc.anchor = GridBagConstraints.WEST; gc.insets = new Insets(grande ? 3 : 1, 0, grande ? 3 : 1, 6);
        int altoFila = grande ? 26 : 22;
        Color ambar = temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00);
        JLabel puesto = new JLabel(LiveNowPresenter.conPuesto(ficha) ? "#" + ficha[3] : "", SwingConstants.RIGHT);   // rango 0 (fuente «Grupo»): sin puesto
        puesto.setFont(puesto.getFont().deriveFont(Font.BOLD, 11.5f));
        puesto.setForeground(LiveNowPresenter.enTop(ficha, 50) ? ambar : Color.GRAY);
        if (LiveNowPresenter.conPuesto(ficha)) puesto.setToolTipText(t("Puesto ", "Rank ") + ficha[3] + t(" del ladder 1v1 · ELO 1v1 ", " on the 1v1 ladder · 1v1 ELO ") + ficha[2]);
        String cc = ficha != null ? (String) ficha[4] : anfitrion.paisDe(mp.id);
        JLabel bandera = new JLabel(iconoBandera(cc, 18));
        String nick = anfitrion.nombreVisible(mp.id, mp.name);
        EtiquetaRecorte nombre = new EtiquetaRecorte();
        nombre.setFont(nombre.getFont().deriveFont(Font.BOLD, 15f));
        nombre.texto(nick);   // recorte con «…» al ancho real: nunca dos líneas
        nombre.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nombre.addMouseListener(new MouseAdapter() {   // sin tooltip de texto: solo la tarjeta de ELO al posar el ratón
            @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isMiddleMouseButton(e)) navegacion.abrirPerfilEnPestana(mp.id, nick); else if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) { anfitrion.ocultarHoverCard(true); navegacion.abrirPerfil(mp.id, nick); } }
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) menus.menuContextual(mp.id, nick, e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) menus.menuContextual(mp.id, nick, e); }

        });
        JLabel elo = new JLabel(mp.rating == null ? "" : "(" + mp.rating + ")");
        elo.setFont(elo.getFont().deriveFont(Font.PLAIN, 12.5f)); elo.setForeground(Color.GRAY);
        JLabel uno = new JLabel(!grande && ficha != null && (Integer) ficha[2] > 0 ? "1v1 " + ficha[2] : "");   // en equipos: el 1v1 de los del top, en ámbar
        uno.setFont(uno.getFont().deriveFont(Font.PLAIN, 11f)); uno.setForeground(ambar);
        String claveCiv = claveCivDeNombre.apply(mp.civ);
        EtiquetaRecorte civ = new EtiquetaRecorte(); civ.setIcon(claveCiv == null ? null : iconoCiv(claveCiv, 20)); civ.texto(mp.civ == null ? "" : claveCiv != null ? nombreCivStats(claveCiv) : mp.civ);   // siempre el mismo nombre e icono, venga en el idioma que venga
        civ.setIconTextGap(5); civ.setFont(civ.getFont().deriveFont(Font.PLAIN, 13.5f)); civ.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
        String[] tw = twitchLive.get(mp.id);
        JLabel twl = new JLabel(tw != null ? "TW" : "");
        twl.setFont(twl.getFont().deriveFont(Font.BOLD, 12f)); twl.setForeground(new Color(0x91, 0x46, 0xFF));
        if (tw != null) { twl.setToolTipText(t("En directo en twitch.tv/", "Live on twitch.tv/") + tw[0]); twl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); twl.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { anfitrion.abrirUrl("https://twitch.tv/" + tw[0]); } }); }
        JComponent[] celdas = { puesto, bandera, nombre, twl, elo, uno, civ };   // el TW, pegado al nick
        for (int i = 0; i < celdas.length; i++) {
            celdas[i].setPreferredSize(new Dimension(LIVE_COLS[i], altoFila)); celdas[i].setMinimumSize(new Dimension(LIVE_COLS_MIN[i], altoFila)); celdas[i].setMaximumSize(new Dimension(LIVE_COLS[i], altoFila));
            gc.gridx = i; gc.insets = new Insets(grande ? 3 : 1, 0, grande ? 3 : 1, i == 1 || i == 2 ? 4 : 6);   // bandera y TW, pegados al nick
            fila.add(celdas[i], gc);
        }
        JPanel relleno = new JPanel(); relleno.setOpaque(false);
        gc.gridx = celdas.length; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(0, 0, 0, 0); fila.add(relleno, gc);
        return fila;
    }
}
