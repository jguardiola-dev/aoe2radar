package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.AzarService;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.RecService;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static dev.tirador.aoe2radar.service.AzarService.ajustarRefAzar;
import static dev.tirador.aoe2radar.service.NombresStats.posicionNombre;
import static dev.tirador.aoe2radar.service.ReglasPartida.marcarFantasmas;
import static dev.tirador.aoe2radar.service.ReglasPartida.posicionEnEquipo;
import static dev.tirador.aoe2radar.service.ReglasPartida.rivalCoincide;
import static dev.tirador.aoe2radar.ui.Componentes.colorVivoTabla;
import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;
import static dev.tirador.aoe2radar.util.Texto.esCualquiera;
import static dev.tirador.aoe2radar.util.Texto.normalizarNick;

/**
 * La pestaña «Partidas»: la tabla de recs sin spoilers, la cabecera «Partidas de:», los filtros (modo, mapa,
 * periodo, rival), la descarga a disco/savegame, y «Al azar por ELO…»/«Guess the ELO!» (cuyo muestreo ya vive
 * en {@link AzarService}: aquí solo queda escribir el resultado en la tabla, igual que fetchMatches/download).
 * Movida tal cual desde SpoilerFreeRecs (fase 3, tanda 3, oleada B).
 * <p>
 * fetchMatches/download/buscarAleatorias/buscarGte SIGUEN siendo {@code SwingWorker} aquí (la tanda lo permite
 * explícitamente: reescribirlos con {@code Tareas} cambiaría cuándo se pinta cada trozo de publish/process).
 * Lo que sí se aisló en {@link PartidasPresenter}, sin Swing, es {@code vigente()} (la comprobación de caducidad
 * por opSerial que cada uno hace al terminar, antes de decidir qué pintar) y los tres filtros de la tabla.
 * <p>
 * La Watchlist (ui.WatchlistView) se pide por {@link EnlaceWatchlist}, que cablea la ventana. El resto de la ventana
 * (navegación, semáforo de operación en curso, perfil abierto, red) llega por {@link Anfitrion}.
 */
public final class PartidasView {

    /** Lo que Partidas necesita de la Watchlist (ui.WatchlistView; la ventana lo cablea). Nombres de negocio: la
     *  vista no conoce playersList/playersModel/eloWatch, solo lo que puede hacer con ellos. */
    public interface EnlaceWatchlist {
        List<Player> seleccion();
        int seleccionSize();
        boolean soloVivosMarcado();
        boolean modoTop();
        String grupoDestino();
        List<Player> conFamilias(List<Player> base);
        void limpiarSeleccion();
        int totalJugadores();
        Player jugador(int indice);
        Integer eloDe(long pid);
        String grupoDeJugador(long pid);
        List<Player> todosJugadores();
        void actualizarIndicadoresVivos();
        void aplicarFiltroGrupo();
        /** Tooltip de «cuenta hermana» de la columna Jugador (delegado en service.Familias, vía Watchlist). */
        String tipCuentaVinculada(Match m);
        Player objetivoForzado();
        void fijarObjetivoForzado(Player p);
        void limpiarObjetivoForzado();
        Player invitado();
        void limpiarInvitado();
        /** Identidad de la vista actual de la watchlist (grupo|país): para saber si la cabecera «Partidas de:»
         *  sigue hablando de la misma vista. */
        String vistaActualId();
        int horasVentana();
        void guardarVentanaHoras();
        /** El chip «Forma» (±ELO reciente) actualiza su texto cuando cambia a quién se busca. */
        void actualizarTextoForma();
        /** El panel fijo sobre la watchlist donde vive la cabecera «Partidas de:» (se construye dentro de
         *  construirWatchlist, antes de que esta vista exista; se pide por aquí para no duplicarlo). */
        JPanel sujetosPanel();
    }

    /** Lo que Partidas necesita del resto de la ventana (cromo, perfil, red) que no es la Watchlist. */
    public interface Anfitrion {
        void estado(String texto);
        void mostrarDirectos(boolean mostrar);
        void refrescarDirectos();
        void enfocarBuscador();
        boolean confirmarEspectar(String nombre);
        void espectarVerificando(long profileId, long matchId);
        void espectarPartida(long matchId);
        void lanzarCaptureAge(Path rec);
        void abrirUrl(String url);
        /** cache.Anotaciones.nombreVisible: ui no puede importar cache, así que llega envuelto (mismo patrón
         *  que ya usan PerfilView/LiveNowView/RatingsView). */
        String nombreVisible(long pid, String nombre);
        String paisDe(long pid);
        boolean enCursoReal(Match m);
        Path destino(Match m);
        Path recsDir();
        void trabajando(boolean on);
        long operacionActual();
        boolean detenido();
        void pararOperacion();
        void anotarHiloOperacion();
        void aprenderCatalogos(List<Match> res);
        List<String> mapasConocidos();
        List<String> civsConocidas();
        void dormir(long ms);
        long perfilAbiertoPid();
        boolean perfilAbierto();
        String perfilNombreAbierto();
        void mostrarHistorialSiSigueAbierto(long pid, String nombre);
        /** La única llamada de red que queda aquí (CompanionApi.partidas): ui no puede importar api. */
        Iterable<Match> paginaDePartidas(long pid, int pagina, int porPagina) throws IOException, InterruptedException;
        boolean autoCopiarAlDescargar();
        void continuarDisponible(boolean visible);
        /** La nota sin-spoilers vuelve a su gris de siempre (delegado en ui.TemaApp, que conoce toda la ventana). */
        void ajustarGrisesNota(boolean oscuro);
        /** Visibilidad de nota/botones/parModo/parRival según la pestaña activa (cromo, toca varias vistas). */
        void actualizarControlesTabla();
    }

    private final JFrame ventana;
    private final MenusJugador menus;
    private final DialogosJugador dialogos;
    private final Navegacion navegacion;
    private final AzarService azarService;
    private final RecService recService;
    private final BarridoVivos barridoVivos;
    private final int perPage;
    private final long pausaMs;
    private final EnlaceWatchlist enlaceWatchlist;
    private final Anfitrion anfitrion;
    private final PartidasTexto texto;

    /** Los buscados actuales: negrita en la tabla y cabecera «Partidas de:». Estático porque azar/GTE y
     *  {@code enfrentamiento()} lo comparten, igual que en la 1.1 (antes vivía en SpoilerFreeRecs). */
    public static final Set<Long> SUJETOS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static final String ORDEN_COLUMNAS_DEFECTO = "0,1,2,5,6,4,3,7,8";

    // ----- Campos de la tabla y su estado -----
    public final List<Match> all = new ArrayList<>();    // todo lo consultado
    public final List<Match> view = new ArrayList<>();   // lo que pasa los filtros (lo que ve la tabla)
    public final MatchesTableModel tableModel = new MatchesTableModel();   // visible para RegresionCapturas
    public JTable table;   // se asigna en construirTabla()

    public final JButton dlSel = new JButton(t("Descargar seleccionadas", "Download selected"));
    public final JButton dlAll = new JButton(t("Descargar todas", "Download all"));
    public final JButton fetchBtn = new JButton(t("Buscar partidas", "Search games"));
    public final JButton azarBtn = new JButton(t("Al azar por ELO…", "Random by ELO…"));
    public final JButton gteBtn = new JButton("Guess the ELO!");
    final JComboBox<String> modeCombo = new JComboBox<>(new String[]{ todosModos() });
    final JComboBox<String> mapaCombo = new JComboBox<>(new String[]{ t("Todos los mapas", "All maps") });
    final JComboBox<String> periodoCombo = new JComboBox<>(new String[]{ t("Todo", "All"), t("7 días", "7 days"), t("30 días", "30 days"), t("90 días", "90 days"), t("365 días", "365 days") });
    boolean actualizandoCombos = false;
    javax.swing.JTextField rivalField;
    public JPopupMenu rivalPopup;
    public String filtroRival = "";
    public JPanel parModo, parRival;

    public JPanel recsCards;   // «tabla» o «guia»
    public JButton guiaBtn;

    public JPanel filaNota, filaBotonesInferiores;
    public JLabel nota;
    public JToggleButton resultadosBtn;
    public boolean mostrarResultados;   // modo consulta: siempre renace apagado
    public final Set<Long> reveladas = new HashSet<>();   // ojos abiertos fila a fila

    public JButton todasPerfilBtn;
    public List<Player> ultimosSujetos = List.of();
    public final Set<Long> filtroSujetos = new HashSet<>();
    public String vistaDeSujetos = "";   // visible para WatchlistView.aplicarFiltroGrupo, vía su EnlacePartidas
    public Player objetivoEtiqueta;   // el jugador que nombra el botón «Buscar partidas (X)»

    volatile boolean topeAlcanzado;
    volatile int fallosFetch;
    public volatile SwingWorker<?, ?> fetchWorker;

    public boolean descargaSinCambiarVista;
    public Runnable alTerminarDescarga;

    public PartidasView(JFrame ventana, MenusJugador menus, DialogosJugador dialogos, Navegacion navegacion,
                         AzarService azarService, RecService recService, BarridoVivos barridoVivos,
                         int perPage, long pausaMs, EnlaceWatchlist enlaceWatchlist, Anfitrion anfitrion) {
        this.ventana = ventana;
        this.menus = menus;
        this.dialogos = dialogos;
        this.navegacion = navegacion;
        this.azarService = azarService;
        this.recService = recService;
        this.barridoVivos = barridoVivos;
        this.perPage = perPage;
        this.pausaMs = pausaMs;
        this.enlaceWatchlist = enlaceWatchlist;
        this.anfitrion = anfitrion;
        this.texto = new PartidasTexto(anfitrion::nombreVisible);
    }

    static String todosModos() { return t("Todos los modos", "All modes"); }

    /** «36 h», «3 días», «2 semanas»: la ventana en la unidad que se lee mejor. */
    static String textoVentana(int horas) {
        if (horas % (24 * 7) == 0 && horas >= 24 * 7) { int w = horas / (24 * 7); return w + (w == 1 ? t(" semana", " week") : t(" semanas", " weeks")); }
        if (horas % 24 == 0 && horas >= 48) return (horas / 24) + t(" días", " days");
        return horas + " h";
    }

    /** El panel de la pestaña, para el CardLayout de la ventana (card "recs": tabla + guía). */
    public JPanel panel() { return recsCards; }

    // ======================================================================
    // Construcción (llamada por la ventana en el mismo orden que hoy)
    // ======================================================================

    /** La parte de consulta de la fila 1 de la barra superior: modo, rival (con sugerencias), mapa, periodo,
     *  el botón «Buscar partidas» y, a continuación, «Al azar por ELO…»/«Guess the ELO!». La ventana la llama
     *  justo donde antes seguía construyendo fila1 a mano (después de «Últimas N horas»). */
    public void agregarFilaConsulta(JPanel fila1) {
        fetchBtn.addActionListener(e -> fetchMatches(fetchBtn));
        modeCombo.setPrototypeDisplayValue("RM Team MegaRandom XL");
        modeCombo.addActionListener(e -> { if (!actualizandoCombos) applyFilters(); });
        parModo = par(new JLabel(t("Modo:", "Mode:")), modeCombo);
        fila1.add(parModo);
        rivalField = new javax.swing.JTextField(11);
        rivalField.putClientProperty("JTextField.placeholderText", t("Rival…", "Opponent…"));
        rivalField.putClientProperty("JTextField.showClearButton", true);
        rivalField.setToolTipText(t("Filtra la tabla por el rival (en equipos, cualquiera del equipo contrario). Escribe para ver sugerencias.",
                "Filters the table by opponent (in team games, anyone on the other team). Type to see suggestions."));
        rivalPopup = new JPopupMenu();
        rivalPopup.setFocusable(false);
        rivalField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() { filtroRival = normalizarNick(rivalField.getText()); applyFilters(); sugerirRivales(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        rivalField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (!rivalPopup.isVisible() || rivalPopup.getComponentCount() == 0) return;
                if (e.getKeyCode() == KeyEvent.VK_DOWN) { ((JMenuItem) rivalPopup.getComponent(0)).doClick(); e.consume(); }
                else if (e.getKeyCode() == KeyEvent.VK_ENTER) { ((JMenuItem) rivalPopup.getComponent(0)).doClick(); e.consume(); }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) rivalPopup.setVisible(false);
            }
        });
        rivalField.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { rivalPopup.setVisible(false); } });
        parRival = par(new JLabel(t("Rival:", "Opponent:")), rivalField);
        fila1.add(parRival);
        mapaCombo.setToolTipText(t("Filtra la tabla por mapa (sobre las partidas cargadas, sin llamadas)", "Filters the table by map (over the loaded games, no requests)"));
        periodoCombo.setToolTipText(t("Filtra la tabla por fecha de la partida (sobre las partidas cargadas)", "Filters the table by game date (over the loaded games)"));
        mapaCombo.addActionListener(e -> { if (!actualizandoCombos) applyFilters(); });
        periodoCombo.addActionListener(e -> { if (!actualizandoCombos) applyFilters(); });
        fila1.add(par(new JLabel(t("Mapa:", "Map:")), mapaCombo));
        fila1.add(par(new JLabel(t("Periodo:", "Period:")), periodoCombo));
        fila1.add(fetchBtn);

        azarBtn.setToolTipText(t("Hasta 10 partidas 1v1 recientes del ladder con ambos jugadores en el rango de ELO elegido", "Up to 10 recent 1v1s from the ladder with both players inside your ELO range"));
        azarBtn.addActionListener(e -> buscarAleatorias());
        fila1.add(azarBtn);
        gteBtn.setToolTipText(t("5 partidas 1v1 recientes de cualquier ELO, anónimas: adivina el ELO y compruébalo con «Revelar resultado…»", "5 recent anonymous 1v1s from any ELO: guess the ELO, then check with “Reveal result…”"));
        gteBtn.addActionListener(e -> buscarGte());
        fila1.add(gteBtn);
    }

    /** Panel de una fila con las etiquetas y componentes dados (copia local del helper de la ventana: es una
     *  pieza trivial de layout usada por varias áreas y esta oleada no reordena utilidades compartidas). */
    private static JPanel par(Component... cs) {
        JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        for (Component c : cs) p.add(c);
        return p;
    }

    /** «Mostrar resultados», con la nota sin-spoilers: la fila 2 de la barra superior. La ventana la añade al
     *  panel «top» justo donde antes construía fila2 a mano. */
    public JPanel construirFilaNota() {
        JPanel fila2 = new JPanel(new BorderLayout(8, 0));
        fila2.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        nota = new JLabel(t("Sin spoilers: nunca se muestra ganador, ±ELO ni duración. Doble clic en una fila = descargar.", "Spoiler-free: winner, ±ELO and duration are never shown. Double-click a row = download."));
        resultadosBtn = new JToggleButton(t("Mostrar resultados", "Show results"));
        resultadosBtn.setFocusable(false);
        resultadosBtn.setToolTipText(t("Modo consulta: el ganador de cada partida en dorado (±ELO y duración en el tooltip). Siempre arranca apagado.",
                "Lookup mode: each game's winner in gold (±ELO and duration in the tooltip). Always starts off."));
        resultadosBtn.addActionListener(e -> {
            mostrarResultados = resultadosBtn.isSelected();
            actualizarNotaSpoilers();
            tableModel.fireTableDataChanged();
        });
        fila2.add(nota, BorderLayout.CENTER);
        fila2.add(resultadosBtn, BorderLayout.EAST);
        filaNota = fila2;
        return fila2;
    }

    /** El JTable completo: renderers, anchos, orden de columnas, atajos y menú contextual. La ventana la llama
     *  donde antes llamaba a construirTablaPartidas(). */
    public void construirTabla() {
        table = new JTable(tableModel) {
            @Override public String getToolTipText(MouseEvent ev) {
                int r = rowAtPoint(ev.getPoint());
                if (r < 0) return null;
                int mr = convertRowIndexToModel(r);
                if (mr < 0 || mr >= view.size()) return null;
                Match m = view.get(mr);
                int mc = convertColumnIndexToModel(columnAtPoint(ev.getPoint()));
                if (mc == 1 && dialogos.notaDe(m.refId) != null) return t("Nota: ", "Note: ") + dialogos.notaDe(m.refId);
                if (mc == 5 && m.players.size() == 2)
                    for (MatchPlayer mp : m.players)
                        if (mp.id != m.refId && dialogos.notaDe(mp.id) != null) return t("Nota: ", "Note: ") + dialogos.notaDe(mp.id);
                if (anfitrion.enCursoReal(m))
                    return t("EN DIRECTO — doble clic para espectar", "LIVE — double-click to spectate");
                String enf = texto.enfrentamiento(m, revelada(m));
                if (revelada(m)) {
                    String tr = PartidasTexto.tipResultado(m);
                    if (tr != null) return "<html>" + (enf.startsWith("<html>") ? enf.substring(6, enf.length() - 7) : escapeHtml(enf))
                            + "<br>" + escapeHtml(tr) + "</html>";
                }
                return enf;
            }
            @Override public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                int mr = convertRowIndexToModel(row);
                boolean viva = mr >= 0 && mr < view.size() && anfitrion.enCursoReal(view.get(mr));
                c.setFont(viva ? getFont().deriveFont(Font.BOLD) : getFont());
                if (!isRowSelected(row))
                    c.setForeground(viva ? colorVivoTabla() : getForeground());
                if (c instanceof JLabel jl && convertColumnIndexToModel(column) == 1
                        && mr >= 0 && mr < view.size())
                    jl.setToolTipText(enlaceWatchlist.tipCuentaVinculada(view.get(mr)));
                return c;
            }
        };
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setPreferredWidth(95);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);
        table.getColumnModel().getColumn(5).setPreferredWidth(230);
        table.getColumnModel().getColumn(6).setPreferredWidth(95);
        table.getColumnModel().getColumn(7).setPreferredWidth(100);
        table.getColumnModel().getColumn(8).setPreferredWidth(80);
        {
            String[] anchos = leerConfig("tabla_anchos", "").split(",");
            if (anchos.length == table.getColumnCount()) for (int i = 0; i < anchos.length; i++) { try { int a = Integer.parseInt(anchos[i].trim()); if (a >= 20) table.getColumnModel().getColumn(i).setPreferredWidth(a); } catch (NumberFormatException ignored) { } }
        }
        DefaultTableCellRenderer ojoR = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foc, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, sel, foc, row, col);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                boolean abierto = "\u25C9".equals(String.valueOf(v));
                l.setFont(l.getFont().deriveFont(abierto ? Font.BOLD : Font.PLAIN, 14f));
                if (!sel) l.setForeground(abierto ? (temaOscuroActivo ? new Color(0xd9, 0xa5, 0x5b) : new Color(0x9a, 0x6b, 0x1f))
                                                  : new Color(0x8a, 0x8a, 0x8a));
                l.setToolTipText(v == null || String.valueOf(v).isBlank() ? null
                        : (abierto ? t("Clic: volver a tapar el resultado", "Click: hide the result again")
                                   : t("Clic: revelar el resultado de esta partida", "Click: reveal this game's result")));
                return l;
            }
        };
        table.getColumnModel().getColumn(8).setCellRenderer(ojoR);
        aplicarOrdenColumnas(leerConfig("tabla_orden", ORDEN_COLUMNAS_DEFECTO));
        javax.swing.Timer guardaCols = new javax.swing.Timer(800, ev -> guardarColumnas());
        guardaCols.setRepeats(false);
        table.getColumnModel().addColumnModelListener(new javax.swing.event.TableColumnModelListener() {
            @Override public void columnMoved(javax.swing.event.TableColumnModelEvent e) { if (e.getFromIndex() != e.getToIndex()) guardaCols.restart(); }
            @Override public void columnMarginChanged(javax.swing.event.ChangeEvent e) { guardaCols.restart(); }
            @Override public void columnAdded(javax.swing.event.TableColumnModelEvent e) { }
            @Override public void columnRemoved(javax.swing.event.TableColumnModelEvent e) { }
            @Override public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) { }
        });
        DefaultTableCellRenderer centrado = new DefaultTableCellRenderer();
        centrado.setHorizontalAlignment(SwingConstants.CENTER);
        for (int ci : new int[]{ 0, 2, 3, 4, 6 }) table.getColumnModel().getColumn(ci).setCellRenderer(centrado);
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                int x = 0;
                for (int c = 0; c < table.getColumnCount(); c++) {
                    x += table.getColumnModel().getColumn(c).getWidth();
                    if (Math.abs(e.getX() - x) <= 4) { ajustarColumna(c); return; }
                }
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sfrDescargar");
        table.getActionMap().put("sfrDescargar", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (dlSel.isEnabled() && !selectedRows().isEmpty()) download(selectedRows());
            }
        });
        ventana.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK), "sfrBuscar");
        ventana.getRootPane().getActionMap().put("sfrBuscar", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { anfitrion.enfocarBuscador(); }
        });
        ventana.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "sfrRefrescar");
        ventana.getRootPane().getActionMap().put("sfrRefrescar", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { anfitrion.refrescarDirectos(); }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybePopupTabla(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopupTabla(e); }
            void maybePopupTabla(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int r = table.rowAtPoint(e.getPoint());
                if (r < 0) return;
                if (!table.isRowSelected(r)) table.setRowSelectionInterval(r, r);
                int mr = table.convertRowIndexToModel(r);
                if (mr < 0 || mr >= view.size()) return;
                Match m = view.get(mr);
                JPopupMenu menu = new JPopupMenu();
                if (m.gte == 0) {
                    List<MatchPlayer> conId = new ArrayList<>();
                    for (MatchPlayer mp : m.players) if (mp.id > 0) conId.add(mp);
                    if (!conId.isEmpty()) {
                        MatchPlayer titular = null; for (MatchPlayer mp : conId) if (mp.id == m.refId) titular = mp;
                        if (conId.size() <= 2)
                            for (MatchPlayer mp : conId) { Integer e1 = menus.elo1v1Conocido(mp.id); if (e1 == null) e1 = mp.rating; JMenu mj = menus.deJugador(mp.id, mp.name + (e1 != null ? "  " + e1 : "")); mj.setIcon(iconoBandera(anfitrion.paisDe(mp.id))); menu.add(mj); }
                        else if (titular != null) {
                            for (MatchPlayer mp : conId) if (mp.id == titular.id) menu.add(menus.deJugador(mp.id, mp.name));
                            JMenu al = new JMenu(t("Aliados", "Allies")), ri = new JMenu(t("Rivales", "Opponents"));
                            for (MatchPlayer mp : conId) { if (mp.id == titular.id) continue; String pos = posicionEnEquipo(m, mp); JMenu dst = mp.team == titular.team ? al : ri; Integer e1 = menus.elo1v1Conocido(mp.id); JMenu sub = menus.deJugador(mp.id, mp.name + (e1 != null ? "  " + e1 : "") + (pos == null ? "" : "  \u00B7 " + posicionNombre(pos))); sub.setIcon(iconoBandera(anfitrion.paisDe(mp.id))); dst.add(sub); }
                            if (al.getItemCount() > 0) menu.add(al); if (ri.getItemCount() > 0) menu.add(ri);
                        } else {
                            JMenu js = new JMenu(t("Jugadores de la partida", "Match players"));
                            for (MatchPlayer mp : conId) js.add(menus.deJugador(mp.id, mp.name));
                            menu.add(js);
                        }
                        menu.addSeparator();
                    }
                }
                if (anfitrion.enCursoReal(m)) {
                    JMenuItem esp = new JMenuItem(t("Espectar en directo", "Spectate live"));
                    esp.addActionListener(a -> anfitrion.espectarVerificando(m.players.isEmpty() ? 0 : m.players.get(0).id, m.id));
                    menu.add(esp);
                    if (dev.tirador.aoe2radar.service.Juego.rutaCaptureAge() != null) {
                        JMenuItem espCa = new JMenuItem(t("Espectar con CaptureAge", "Spectate with CaptureAge"));
                        espCa.addActionListener(a -> {
                            anfitrion.lanzarCaptureAge(null);
                            anfitrion.espectarVerificando(m.players.isEmpty() ? 0 : m.players.get(0).id, m.id);
                        });
                        menu.add(espCa);
                    }
                } else {
                    if (m.enDisco) {
                        JMenuItem env = new JMenuItem(t("Enviar al juego", "Send to game"));
                        // enviarInteligente (no enviarASavegame a secas): decisión 94/95, "si no [parece sana],
                        // descarga como hoy" — con un archivo sano se comporta igual que antes (enviarASavegame).
                        env.addActionListener(a -> enviarInteligente(List.of(m)));
                        menu.add(env);
                    } else {
                        JMenuItem dl = new JMenuItem(t("Descargar", "Download"));
                        dl.addActionListener(a -> download(List.of(m)));
                        menu.add(dl);
                    }
                    menu.addSeparator();
                    JMenuItem rev = new JMenuItem(t("Revelar resultado\u2026", "Reveal result\u2026"));
                    rev.addActionListener(a -> revelarResultado());
                    menu.add(rev);
                    if (m.gte == 0) {
                        java.util.LinkedHashSet<String> civsP = new java.util.LinkedHashSet<>();
                        for (MatchPlayer mp : m.players) if (mp.civ != null && !mp.civ.isBlank()) civsP.add(mp.civ);
                        if (civsP.size() <= 2) {
                            for (String cv : civsP) { JMenuItem it = new JMenuItem("Tech tree: " + cv); it.addActionListener(a -> navegacion.abrirTechTree(cv)); menu.add(it); }
                        } else {
                            JMenu sub = new JMenu("Tech tree");
                            for (String cv : civsP) { JMenuItem it = new JMenuItem(cv); it.addActionListener(a -> navegacion.abrirTechTree(cv)); sub.add(it); }
                            menu.add(sub);
                        }
                    }
                    if (m.gte == 0) {
                        JMenuItem ana = new JMenuItem(t("Análisis de la partida (¡spoilers!)\u2026",
                                "Match analysis (spoilers!)\u2026"));
                        ana.addActionListener(a -> {
                            int ok = JOptionPane.showConfirmDialog(ventana,
                                    t("Se abrirá el análisis completo en aoe2insights: resultado, estrategias y minimapa.\n¿Seguro?",
                                      "This opens the full analysis on aoe2insights: result, strategies and minimap.\nSure?"),
                                    t("Análisis con spoilers", "Analysis with spoilers"),
                                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                            if (ok == JOptionPane.YES_OPTION)
                                anfitrion.abrirUrl("https://www.aoe2insights.com/match/" + m.id + "/");
                        });
                        menu.add(ana);
                    }
                }
                menu.show(table, e.getX(), e.getY());
            }
            @Override public void mouseClicked(MouseEvent e) {
                int r0 = table.rowAtPoint(e.getPoint()), c0 = table.columnAtPoint(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e) && r0 >= 0 && c0 >= 0 && table.convertColumnIndexToModel(c0) == 8) {
                    if (e.getClickCount() != 1) return;
                    int mr0 = table.convertRowIndexToModel(r0);
                    if (mr0 < 0 || mr0 >= view.size()) return;
                    Match m0 = view.get(mr0);
                    if (m0.finished == null) return;
                    if (!reveladas.remove(m0.id)) reveladas.add(m0.id);
                    tableModel.fireTableRowsUpdated(mr0, mr0);
                    return;
                }
                if (e.getClickCount() != 2 || !dlSel.isEnabled() || e.isControlDown() || e.isShiftDown()) return;
                int r = table.rowAtPoint(e.getPoint());
                if (r < 0) return;
                int mr = table.convertRowIndexToModel(r);
                if (mr < 0 || mr >= view.size()) return;
                Match m = view.get(mr);
                if (anfitrion.enCursoReal(m)) {
                    if (anfitrion.confirmarEspectar(texto.refNombre(m))) anfitrion.espectarVerificando(m.players.isEmpty() ? 0 : m.players.get(0).id, m.id);
                    return;
                }
                else if (m.finished == null)
                    anfitrion.estado(t("Esa partida quedó colgada en el servidor (crash): no hay rec que bajar.",
                            "That game hung on the server (crash): there's no rec to download."));
                else download(List.of(m));
            }
        });
    }

    /** Las cards «tabla»/«guia» de la zona central de Partidas. CableadoCentro la llama donde antes montaba
     *  recsCards dentro de construirCentro, justo antes de crear Directos. */
    public void construirCards() {
        recsCards = new JPanel(new java.awt.CardLayout());
        recsCards.add(new JScrollPane(table), "tabla");
        JPanel guia = new JPanel(new GridBagLayout());
        JLabel guiaTxt = new JLabel("<html><div style='text-align:center'>"
                + "<b>" + t("Así funciona", "How it works") + "</b><br><br>"
                + t("1. Elige jugadores en la lista de la izquierda, o escribe un nick en el buscador.", "1. Pick players in the list on the left, or type a nick in the search box.") + "<br>"
                + t("2. Pulsa <b>Buscar partidas</b>.", "2. Press <b>Search games</b>.") + "<br>"
                + t("3. Doble clic en una partida para descargarla — sin spoilers.", "3. Double-click a game to download it — spoiler-free.")
                + "</div></html>");
        guiaTxt.setHorizontalAlignment(SwingConstants.CENTER);
        guiaBtn = new JButton(t("Buscar partidas", "Search games"));
        guiaBtn.addActionListener(e -> fetchBtn.doClick());
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0; gc.insets = new Insets(0, 0, 14, 0);
        guia.add(guiaTxt, gc);
        gc.gridy = 1; gc.insets = new Insets(0, 0, 0, 0);
        guia.add(guiaBtn, gc);
        recsCards.add(guia, "guia");
        mostrarGuiaVacia(true);
    }

    /** Los botones de descarga/carpeta/perfil de la franja inferior. CableadoCromo.construirBarraInferior la
     *  llama donde antes montaba btns1/btns2, y coloca lo que devuelve en el NORTE de «bottom». */
    public JPanel construirBotonesInferiores() {
        JButton abrir  = new JButton(t("Abrir carpeta recs descargadas", "Open downloaded recs folder"));
        JButton vaciar = new JButton(t("Vaciar recs", "Empty recs folder"));
        dlSel.addActionListener(e -> download(selectedRows()));
        dlAll.addActionListener(e -> download(allRows()));
        abrir.addActionListener(e -> abrirCarpeta());
        vaciar.addActionListener(e -> vaciarRecs());
        JPanel btns1 = new JPanel(new WrapLayout(java.awt.FlowLayout.LEFT, 8, 2));
        JButton carpetasBtn = new JButton(t("Carpetas \u25BE", "Folders \u25BE"));
        carpetasBtn.setFocusable(false);
        carpetasBtn.addActionListener(e -> {
            JPopupMenu pm = new JPopupMenu();
            JMenuItem i1 = new JMenuItem(abrir.getText());  i1.addActionListener(a -> abrir.doClick());
            JMenuItem i2 = new JMenuItem(abrirSgTxt());     i2.addActionListener(a -> abrirSavegame());
            JMenuItem i3 = new JMenuItem(vaciar.getText()); i3.addActionListener(a -> vaciar.doClick());
            pm.add(i1); pm.add(i2); pm.addSeparator(); pm.add(i3);
            pm.show(carpetasBtn, 0, carpetasBtn.getHeight());
        });
        btns1.add(dlSel); btns1.add(dlAll);

        JButton enviarSg = new JButton(t("Enviar al juego", "Send to game"));
        enviarSg.setToolTipText(t("Envía las recs seleccionadas al juego: las ya descargadas se copian; las que falten se descargan y se envían en la misma acción", "Sends the selected recs to the game: downloaded ones are copied; missing ones are downloaded and sent in one go"));
        enviarSg.addActionListener(e -> enviarInteligente(selectedRows()));
        JPanel btns2 = new JPanel(new WrapLayout(java.awt.FlowLayout.LEFT, 8, 2));
        btns1.add(enviarSg);
        todasPerfilBtn = new JButton(t("Todas las partidas del perfil", "All games of the profile"));
        todasPerfilBtn.setFocusable(false); todasPerfilBtn.putClientProperty("JButton.buttonType", "roundRect");
        todasPerfilBtn.setToolTipText(t("Abre el perfil del jugador buscado con su histórico completo en páginas (del último año, sin llamadas si está en sfr-data)", "Opens the searched player's profile with their full history in pages (last year, no requests when in sfr-data)"));
        todasPerfilBtn.addActionListener(e -> { if (ultimosSujetos.size() == 1) { Player p = ultimosSujetos.get(0); navegacion.abrirPerfil(p.id(), anfitrion.nombreVisible(p.id(), p.name())); anfitrion.mostrarHistorialSiSigueAbierto(p.id(), anfitrion.nombreVisible(p.id(), p.name())); } });
        todasPerfilBtn.setVisible(false);
        btns1.add(todasPerfilBtn);
        btns2.add(carpetasBtn);

        JPanel filasBtns = new JPanel();
        filasBtns.setLayout(new javax.swing.BoxLayout(filasBtns, javax.swing.BoxLayout.Y_AXIS));
        filasBtns.add(btns1);
        filasBtns.add(btns2);
        filaBotonesInferiores = filasBtns;
        return filasBtns;
    }

    // ======================================================================
    // Cabecera «Partidas de:» y sugerencias de rival
    // ======================================================================

    /** Cabecera fija sobre la watchlist: los sujetos de la búsqueda, fuera del scroll. Solo con ≤4 sujetos
     *  (más de 10, un resumen con «Filtrar ▾»). */
    public void refrescarSujetos(List<Player> tracked, boolean esInvitadoIn) {
        JPanel sujetosPanel = enlaceWatchlist.sujetosPanel();
        ultimosSujetos = tracked == null ? List.of() : List.copyOf(tracked);
        if (todasPerfilBtn != null) todasPerfilBtn.setVisible(ultimosSujetos.size() == 1 && ultimosSujetos.get(0).id() > 0);
        final boolean esInvitado = esInvitadoIn
                || (enlaceWatchlist.invitado() != null && ultimosSujetos.size() == 1 && ultimosSujetos.get(0).id() == enlaceWatchlist.invitado().id());
        sujetosPanel.removeAll();
        filtroSujetos.retainAll(ultimosSujetos.stream().map(Player::id).collect(java.util.stream.Collectors.toSet()));
        if (ultimosSujetos.isEmpty()) {
            sujetosPanel.setVisible(false);
            sujetosPanel.revalidate(); sujetosPanel.repaint();
            return;
        }
        final boolean muchos = ultimosSujetos.size() > 10;
        Color sep = temaOscuroActivo ? new Color(0x5a, 0x5a, 0x5a) : new Color(0xc0, 0xc0, 0xc0);
        Color gris = temaOscuroActivo ? new Color(0x9a, 0x9a, 0x9a) : new Color(0x66, 0x66, 0x66);
        log("cabecera sujetos: " + ultimosSujetos.size() + " · invitado=" + esInvitado);
        JLabel tit = new JLabel(t("Partidas de:", "Games of:"));
        tit.setFont(tit.getFont().deriveFont(Font.PLAIN, 11f));
        tit.setForeground(gris);
        tit.setBorder(BorderFactory.createEmptyBorder(3, 6, 1, 0));
        JButton cerrarBusq = new JButton("\u00D7");
        cerrarBusq.setFocusable(false);
        cerrarBusq.setMargin(new Insets(0, 5, 0, 5));
        cerrarBusq.putClientProperty("JButton.buttonType", "roundRect");
        cerrarBusq.setToolTipText(t("Cerrar esta búsqueda: vacía la tabla", "Close this search: clears the table"));
        cerrarBusq.addActionListener(e -> cerrarBusqueda());
        JPanel filaTit = new JPanel(new BorderLayout(6, 0));
        filaTit.setOpaque(false);
        filaTit.add(tit, BorderLayout.WEST);
        filaTit.add(cerrarBusq, BorderLayout.EAST);
        filaTit.setAlignmentX(Component.LEFT_ALIGNMENT);
        sujetosPanel.add(filaTit);
        JPanel chipsSujetos = new JPanel(new WrapLayout(java.awt.FlowLayout.LEFT, 4, 2));
        chipsSujetos.setOpaque(false);
        chipsSujetos.setAlignmentX(Component.LEFT_ALIGNMENT);
        chipsSujetos.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && !filtroSujetos.isEmpty()) { filtroSujetos.clear(); refrescarSujetos(ultimosSujetos, esInvitadoIn); applyFilters(); }
            }
        });
        if (muchos) {
            JLabel todos = new JLabel("<html><i>" + escapeHtml(enlaceWatchlist.vistaActualId().split("\\|")[0]) + "</i> <font color='#8a8a8a'>(" + ultimosSujetos.size() + ")</font></html>");
            todos.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            JButton filtrarBtn = new JButton(t("Filtrar \u25BE", "Filter \u25BE"));
            filtrarBtn.setFocusable(false); filtrarBtn.setMargin(new Insets(1, 6, 1, 6));
            filtrarBtn.putClientProperty("JButton.buttonType", "roundRect");
            filtrarBtn.addActionListener(e -> {
                JPopupMenu pm = new JPopupMenu();
                JMenuItem todosIt = new JMenuItem(t("Todas las partidas", "All games"));
                todosIt.addActionListener(a -> { filtroSujetos.clear(); refrescarSujetos(ultimosSujetos, esInvitadoIn); applyFilters(); });
                pm.add(todosIt); pm.addSeparator();
                for (Player s : ultimosSujetos) {
                    javax.swing.JCheckBoxMenuItem it = new javax.swing.JCheckBoxMenuItem(anfitrion.nombreVisible(s.id(), s.name()), filtroSujetos.contains(s.id()));
                    it.addActionListener(a -> { if (it.isSelected()) filtroSujetos.add(s.id()); else filtroSujetos.remove(s.id()); refrescarSujetos(ultimosSujetos, esInvitadoIn); applyFilters(); });
                    pm.add(it);
                }
                pm.show(filtrarBtn, 0, filtrarBtn.getHeight());
            });
            chipsSujetos.add(todos); chipsSujetos.add(filtrarBtn);
            if (!filtroSujetos.isEmpty()) {
                JLabel act = new JLabel("<font color='#8a8a8a'>" + filtroSujetos.size() + t(" filtrados", " filtered") + "</font>");
                act.setText("<html>" + act.getText() + "</html>");
                chipsSujetos.add(act);
            }
        }
        for (Player s : muchos ? List.<Player>of() : ultimosSujetos) {
            Integer elo = enlaceWatchlist.eloDe(s.id());
            JLabel l = new JLabel("<html><i>" + escapeHtml(anfitrion.nombreVisible(s.id(), s.name())) + "</i>"
                    + (elo != null ? " <font color='#8a8a8a'>\u00B7 " + elo + "</font>" : "") + "</html>");
            final boolean activo = filtroSujetos.contains(s.id());
            l.setOpaque(activo);
            if (activo) l.setBackground(temaOscuroActivo ? new Color(0x4a, 0x3a, 0x1e) : new Color(0xf3, 0xe3, 0xc0));
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(activo ? (temaOscuroActivo ? new Color(0xd9, 0xa5, 0x5b) : new Color(0x9a, 0x6b, 0x1f)) : new Color(0, 0, 0, 0), 1, true),
                    BorderFactory.createEmptyBorder(1, 6, 1, 6)));
            l.setToolTipText(t("Clic: ver solo sus partidas (Ctrl+clic: varios; otro clic: todas) — clic derecho: perfil, nicks, grupos\u2026",
                    "Click: only their games (Ctrl+click: several; click again: all) — right-click: profile, names, groups\u2026"));
            l.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            final long pid = s.id(); final String nom = s.name();
            l.addMouseListener(new MouseAdapter() {
                void popup(MouseEvent e) {
                    JPopupMenu pm = new JPopupMenu();
                    JMenu jm = menus.deJugador(pid, nom);
                    for (Component c : jm.getMenuComponents()) pm.add(c);
                    if (esInvitado) {
                        pm.addSeparator();
                        JMenuItem quitar = new JMenuItem(t("Quitar filtro (dejar de ver sus partidas)",
                                "Remove filter (stop viewing their games)"));
                        quitar.addActionListener(a -> {
                            enlaceWatchlist.limpiarInvitado();
                            refrescarSujetos(List.of(), false);
                            enlaceWatchlist.aplicarFiltroGrupo();
                        });
                        pm.add(quitar);
                    }
                    pm.show(l, e.getX(), e.getY());
                }
                @Override public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) popup(e); }
                @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) popup(e); }
                @Override public void mouseClicked(MouseEvent e)  {
                    if (e.getButton() != MouseEvent.BUTTON1) return;
                    if (e.getClickCount() == 2) { navegacion.abrirPerfil(pid, nom); return; }
                    if (e.getClickCount() != 1) return;
                    if (e.isControlDown()) { if (!filtroSujetos.remove(pid)) filtroSujetos.add(pid); }
                    else if (filtroSujetos.size() == 1 && filtroSujetos.contains(pid)) filtroSujetos.clear();
                    else { filtroSujetos.clear(); filtroSujetos.add(pid); }
                    refrescarSujetos(ultimosSujetos, esInvitadoIn);
                    applyFilters();
                }
            });
            String grupoYa = ultimosSujetos.size() == 1 ? enlaceWatchlist.grupoDeJugador(pid) : null;
            if (ultimosSujetos.size() == 1 && grupoYa != null && !esInvitado) {
                JLabel ya = new JLabel("\u2605 " + t("en ", "in ") + (grupoYa.isBlank() ? t("tu watchlist", "your watchlist") : grupoYa));
                ya.setFont(ya.getFont().deriveFont(Font.PLAIN, 11f));
                ya.setForeground(gris);
                JPanel filaYa = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
                filaYa.setOpaque(false);
                filaYa.add(l);
                filaYa.add(ya);
                chipsSujetos.add(filaYa);
            } else if (ultimosSujetos.size() == 1 && grupoYa == null) {
                JButton addInv = new JButton(t("+ Añadir al grupo", "+ Add to group"));
                addInv.setFont(addInv.getFont().deriveFont(11f));
                addInv.setMargin(new Insets(1, 6, 1, 6));
                addInv.setFocusable(false);
                addInv.setToolTipText(t("Ficha a este jugador en uno de tus grupos", "Add this player to one of your groups"));
                addInv.addActionListener(e -> {
                    JPopupMenu pm = new JPopupMenu();
                    JMenu jm = menus.deJugador(pid, nom);
                    for (Component c : jm.getMenuComponents())
                        if (c instanceof JMenu sub && sub.getText() != null && sub.getText().toLowerCase().startsWith(t("añadir", "add")))
                            for (Component cc : sub.getMenuComponents()) pm.add(cc);
                    if (pm.getComponentCount() == 0) for (Component c : jm.getMenuComponents()) pm.add(c);
                    pm.show(addInv, 0, addInv.getHeight());
                });
                JPanel fila = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
                fila.setOpaque(false);
                fila.add(l);
                fila.add(addInv);
                chipsSujetos.add(fila);
            } else chipsSujetos.add(l);
        }
        sujetosPanel.add(chipsSujetos);
        JPanel raya = new JPanel();
        raya.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 5));
        raya.setPreferredSize(new java.awt.Dimension(10, 5));
        raya.setOpaque(false);
        raya.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, sep));
        sujetosPanel.add(raya);
        sujetosPanel.setVisible(true);
        sujetosPanel.revalidate(); sujetosPanel.repaint();
    }

    /** Sugerencias: rivales de la búsqueda actual que contienen lo tecleado, con su número de partidas. */
    void sugerirRivales() {
        rivalPopup.setVisible(false);
        rivalPopup.removeAll();
        if (filtroRival.length() < 1 || all.isEmpty()) return;
        Map<String, Integer> cuenta = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Match m : all) {
            if (m.gte > 0) continue;
            asignarRef(m);
            MatchPlayer yo = null;
            for (MatchPlayer p : m.players) if (p.id == m.refId) yo = p;
            for (MatchPlayer p : m.players) {
                if (p.id == m.refId) continue;
                if (yo != null && m.players.size() > 2 && p.team == yo.team) continue;
                String vis = anfitrion.nombreVisible(p.id, p.name);
                if (normalizarNick(vis).contains(filtroRival) || normalizarNick(p.name).contains(filtroRival)) cuenta.merge(vis, 1, Integer::sum);
            }
        }
        if (cuenta.isEmpty()) return;
        List<Map.Entry<String, Integer>> lista = new ArrayList<>(cuenta.entrySet());
        lista.sort((a, b) -> b.getValue() - a.getValue());
        int n = 0;
        for (Map.Entry<String, Integer> en : lista) {
            if (n++ >= 8) break;
            if (normalizarNick(en.getKey()).equals(filtroRival) && lista.size() == 1) return;
            JMenuItem it = new JMenuItem(en.getKey() + "  (" + en.getValue() + ")");
            it.addActionListener(a -> { rivalField.setText(en.getKey()); rivalPopup.setVisible(false); });
            rivalPopup.add(it);
        }
        rivalPopup.show(rivalField, 0, rivalField.getHeight());
    }

    // ======================================================================
    // Revelar / tapar resultados
    // ======================================================================

    boolean revelada(Match m) { return PartidasPresenter.revelada(mostrarResultados, m.gte, reveladas.contains(m.id)); }

    /** Cada tabla nueva nace tapada: se apaga el modo consulta y se cierran los ojos. */
    public void taparResultados() {
        boolean cambia = mostrarResultados || !reveladas.isEmpty();
        mostrarResultados = false;
        reveladas.clear();
        if (resultadosBtn != null && resultadosBtn.isSelected()) resultadosBtn.setSelected(false);
        if (cambia) { actualizarNotaSpoilers(); if (tableModel != null) tableModel.fireTableDataChanged(); }
    }

    void actualizarNotaSpoilers() {
        boolean oscuro = temaOscuroActivo;
        if (mostrarResultados) {
            nota.setText(t("\u2726 Mostrando resultados: Jugador en verde si ganó y rojo si perdió, con su ±ELO; enfrentamiento completo y duración, en el tooltip de la fila.",
                           "\u2726 Showing results: Player in green if they won, red if they lost, with their ±ELO; full matchup and duration in the row tooltip."));
            nota.setForeground(oscuro ? new Color(0xd9, 0xa5, 0x5b) : new Color(0x9a, 0x6b, 0x1f));
        } else {
            nota.setText(t("Sin spoilers: nunca se muestra ganador, ±ELO ni duración. Doble clic en una fila = descargar.",
                           "Spoiler-free: winner, ±ELO and duration are never shown. Double-click a row = download."));
            anfitrion.ajustarGrisesNota(oscuro);
        }
    }

    void revelarResultado() {
        List<Match> sel = selectedRows();
        if (sel.size() != 1) { anfitrion.estado(t("Selecciona una sola partida para revelar.", "Select a single game to reveal.")); return; }
        Match m = sel.get(0);
        if (m.finished == null) {
            anfitrion.estado(anfitrion.enCursoReal(m)
                    ? t("Esa partida sigue EN DIRECTO: no hay resultado que revelar todavía.",
                        "That game is still LIVE: no result to reveal yet.")
                    : t("Esa partida quedó colgada en el servidor (crash): no tiene resultado.",
                        "That game hung on the server (crash): it has no result."));
            return;
        }
        int r = JOptionPane.showConfirmDialog(ventana,
                t("Vas a ver el resultado de esta partida (ganador, ±ELO y duración).\n¿Seguro?",
                  "You are about to see this game's result (winner, ±ELO and duration).\nSure?"),
                t("Revelar resultado", "Reveal result"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        if (m.gte > 0) {
            JOptionPane.showMessageDialog(ventana, PartidasTexto.textoResultado(m), t("Resultado", "Result"), JOptionPane.PLAIN_MESSAGE);
            return;
        }
        String verAn = t("Ver análisis en aoe2insights\u2026", "View analysis on aoe2insights\u2026");
        String cerrar = t("Cerrar", "Close");
        int ra = JOptionPane.showOptionDialog(ventana, PartidasTexto.textoResultado(m), t("Resultado", "Result"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new Object[]{ cerrar, verAn }, cerrar);
        if (ra == 1) anfitrion.abrirUrl("https://www.aoe2insights.com/match/" + m.id + "/");
    }

    // ======================================================================
    // Carpeta de recs, savegame
    // ======================================================================

    public void abrirCarpeta() {
        try {
            Files.createDirectories(anfitrion.recsDir());
            java.awt.Desktop.getDesktop().open(anfitrion.recsDir().toFile());
        } catch (Exception ex) {
            anfitrion.estado(t("No se pudo abrir la carpeta: ", "Couldn't open the folder: ") + causa(ex));
        }
    }

    public void vaciarRecs() {
        List<Path> files = new ArrayList<>();
        try {
            if (Files.isDirectory(anfitrion.recsDir()))
                try (var st = Files.list(anfitrion.recsDir())) {
                    st.filter(f -> f.getFileName().toString().endsWith(".aoe2record")).forEach(files::add);
                }
        } catch (IOException ex) {
            anfitrion.estado(t("Error leyendo la carpeta: ", "Error reading the folder: ") + causa(ex));
            return;
        }
        if (files.isEmpty()) { anfitrion.estado(t("La carpeta recs ya está vacía.", "The recs folder is already empty.")); return; }
        int r = JOptionPane.showConfirmDialog(ventana,
                t("Se borrarán ", "This will delete ") + files.size()
                        + t(" recs de la carpeta recs.\n¿Continuar?", " recs from the recs folder.\nContinue?"),
                t("Vaciar recs", "Empty recs"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        int ok = 0;
        for (Path f : files) { try { Files.delete(f); ok++; } catch (IOException ignored) {} }
        for (Match m : all) { m.enDisco = false; if (m.estado.startsWith("✓")) m.estado = ""; }
        tableModel.fireTableDataChanged();
        anfitrion.estado(ok + " recs borradas.");
    }

    /** Carpeta savegame activa: la de config si sigue existiendo; si no, la detectada. */
    public Path obtenerSavegame(boolean interactivo) {
        String cfg = leerConfig("savegame", null);
        if (cfg != null && Files.isDirectory(Path.of(cfg))) return Path.of(cfg);
        List<Path> dets = dev.tirador.aoe2radar.service.Juego.detectarSavegames();
        if (dets.size() == 1) {
            guardarConfig("savegame", dets.get(0).toString());
            return dets.get(0);
        }
        if (!interactivo) return null;
        if (dets.size() > 1) {
            Object sel = JOptionPane.showInputDialog(ventana,
                    t("Hay varios perfiles del juego. Elige tu carpeta savegame:",
                      "There are several game profiles. Pick your savegame folder:"),
                    t("Carpeta savegame", "Savegame folder"), JOptionPane.PLAIN_MESSAGE, null, dets.toArray(), dets.get(0));
            if (sel == null) return null;
            guardarConfig("savegame", sel.toString());
            return (Path) sel;
        }
        JOptionPane.showMessageDialog(ventana,
                t("No encuentro la carpeta savegame del juego.\nElígela a mano:\n…\\Games\\Age of Empires 2 DE\\<perfil>\\savegame",
                  "Couldn't find the game's savegame folder.\nPick it manually:\n…\\Games\\Age of Empires 2 DE\\<profile>\\savegame"));
        return elegirSavegameManual();
    }

    public Path elegirSavegameManual() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(t("Elige la carpeta savegame de AoE2 DE", "Pick the AoE2 DE savegame folder"));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String actual = leerConfig("savegame", null);
        Path base = actual != null ? Path.of(actual)
                : Path.of(System.getProperty("user.home", "."), "Games", "Age of Empires 2 DE");
        if (Files.isDirectory(base)) fc.setCurrentDirectory(base.toFile());
        if (fc.showOpenDialog(ventana) != JFileChooser.APPROVE_OPTION) return null;
        Path p = fc.getSelectedFile().toPath();
        guardarConfig("savegame", p.toString());
        return p;
    }

    /** Enviar al juego: copia lo que ya está sano en disco y descarga+envía lo que falte (decisión de Jorge,
     *  DEUDA 94/95: «sana» es la misma regla que usa RecService.procesar, no un Files.exists propio). */
    public void enviarInteligente(List<Match> objetivo) {
        if (objetivo.isEmpty()) { anfitrion.estado(t("No hay partidas seleccionadas.", "No games selected.")); return; }
        List<Match> enDisco = new ArrayList<>(), faltan = new ArrayList<>();
        for (Match m : objetivo) (RecService.recSana(anfitrion.destino(m)) ? enDisco : faltan).add(m);
        if (!enDisco.isEmpty()) enviarASavegame(enDisco);
        if (!faltan.isEmpty()) download(faltan, true);
    }

    /** Copia al savegame lo que le llegue en `objetivo`: NO vuelve a comprobar RecService.recSana (su único
     *  llamador, enviarInteligente, ya leyó la cabecera de cada archivo para armar esta lista); así es una sola
     *  lectura de cabecera por partida, no dos. */
    public void enviarASavegame(List<Match> objetivo) {
        if (objetivo.isEmpty()) { anfitrion.estado(t("No hay partidas seleccionadas.", "No games selected.")); return; }
        Path sg = obtenerSavegame(true);
        if (sg == null) { anfitrion.estado(t("Sin carpeta savegame configurada.", "No savegame folder configured.")); return; }
        int ok = 0, yaEstaban = 0;
        for (Match m : objetivo) {
            boolean ya = Files.exists(sg.resolve(anfitrion.destino(m).getFileName().toString()));
            if (dev.tirador.aoe2radar.service.Juego.copiarASavegame(m, sg)) {
                ok++;
                if (ya) yaEstaban++;
                try {
                    Files.setLastModifiedTime(sg.resolve(anfitrion.destino(m).getFileName().toString()),
                            java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
                } catch (Exception ignored) {}
                m.enJuego = true;
                setEstado(m, t("✓✓ en juego", "✓✓ in game"));
            }
        }
        anfitrion.estado(ok + t(" recs enviadas al juego", " recs sent to the game") +
                (yaEstaban > 0 ? " (" + yaEstaban + t(" ya estaban, actualizadas)", " were already there, refreshed)") : "") + ".");
    }

    String abrirSgTxt() { return t("Abrir carpeta savegame del juego", "Open game savegame folder"); }

    public void abrirSavegame() {
        Path sg = obtenerSavegame(true);
        if (sg == null) { anfitrion.estado(t("Sin carpeta savegame configurada.", "No savegame folder configured.")); return; }
        try { java.awt.Desktop.getDesktop().open(sg.toFile()); }
        catch (Exception ex) { anfitrion.estado(t("No se pudo abrir la carpeta: ", "Couldn't open the folder: ") + causa(ex)); }
    }

    // ======================================================================
    // Cierre de búsqueda, filtros
    // ======================================================================

    public void cerrarBusqueda() {
        if (fetchWorker != null) { anfitrion.pararOperacion(); }
        all.clear();
        view.clear();
        tableModel.fireTableDataChanged();
        SUJETOS.clear();
        filtroSujetos.clear();
        enlaceWatchlist.limpiarInvitado();
        refrescarSujetos(List.of(), false);
        taparResultados();
        mostrarGuiaVacia(true);
        actualizarTextoBuscar();
        anfitrion.estado(t("Búsqueda cerrada.", "Search closed."));
    }

    public void refreshModeCombo() {
        actualizandoCombos = true;
        Object sel = modeCombo.getSelectedItem();
        TreeSet<String> modos = new TreeSet<>();
        for (Match m : all) modos.add(m.mode);
        modeCombo.removeAllItems();
        modeCombo.addItem(todosModos());
        for (String s : modos) modeCombo.addItem(s);
        if (sel != null && modos.contains(String.valueOf(sel))) modeCombo.setSelectedItem(sel);
        Object selMapa = mapaCombo.getSelectedItem();
        TreeSet<String> mapas = new TreeSet<>();
        for (Match m : all) if (m.map != null && !m.map.isBlank()) mapas.add(m.map);
        mapaCombo.removeAllItems(); mapaCombo.addItem(t("Todos los mapas", "All maps"));
        for (String s : mapas) mapaCombo.addItem(s);
        if (selMapa != null && mapas.contains(String.valueOf(selMapa))) mapaCombo.setSelectedItem(selMapa);
        actualizandoCombos = false;
    }

    /** Fija el jugador seguido de referencia de la partida. */
    void asignarRef(Match m) {
        MatchPlayer suj = null;
        for (MatchPlayer mp : m.players)
            if (SUJETOS.contains(mp.id) && (suj == null || (mp.rating != null && (suj.rating == null || mp.rating > suj.rating)))) suj = mp;
        if (suj != null) { m.refId = suj.id; return; }
        for (Player p : enlaceWatchlist.seleccion())
            if (m.tieneJugador(p.id())) { m.refId = p.id(); return; }
        for (int i = 0; i < enlaceWatchlist.totalJugadores(); i++) {
            Player p = enlaceWatchlist.jugador(i);
            if (m.tieneJugador(p.id())) { m.refId = p.id(); return; }
        }
        if (!m.players.isEmpty()) {
            MatchPlayer mejor = m.players.get(0);
            for (MatchPlayer p : m.players)
                if (p.rating != null && (mejor.rating == null || p.rating > mejor.rating)) mejor = p;
            m.refId = mejor.id;
        }
    }

    void ajustarColumna(int c) {
        TableColumn col = table.getColumnModel().getColumn(c);
        int mc = table.convertColumnIndexToModel(c);
        TableCellRenderer hr = col.getHeaderRenderer() != null ? col.getHeaderRenderer() : table.getTableHeader().getDefaultRenderer();
        int w = hr.getTableCellRendererComponent(table, col.getHeaderValue(), false, false, -1, c).getPreferredSize().width + 16;
        int filas = Math.min(table.getRowCount(), 300);
        for (int r = 0; r < filas; r++) {
            Component comp = table.prepareRenderer(table.getCellRenderer(r, c), r, c);
            w = Math.max(w, comp.getPreferredSize().width + 14);
        }
        int max = mc == 1 || mc == 5 ? 420 : 260;
        col.setPreferredWidth(Math.max(56, Math.min(max, w)));
    }

    public void ajustarColumnas() {
        if (table == null || table.getColumnCount() == 0) return;
        for (int c = 0; c < table.getColumnCount(); c++) ajustarColumna(c);
    }

    public void applyFilters() {
        marcarFantasmas(all);
        String modo = (String) modeCombo.getSelectedItem();
        Set<Long> selIds = new HashSet<>();
        List<Player> baseFiltro = new ArrayList<>();
        for (Player s : ultimosSujetos) if (filtroSujetos.contains(s.id())) baseFiltro.add(s);
        for (Player p : baseFiltro) {
            selIds.add(p.id());
            if (p.vinculo() != 0)
                for (Player x : enlaceWatchlist.todosJugadores())
                    if (x.vinculo() == p.vinculo()) selIds.add(x.id());
        }

        String sgCfg = leerConfig("savegame", null);
        Path sgConocida = (sgCfg != null && Files.isDirectory(Path.of(sgCfg))) ? Path.of(sgCfg) : null;
        Instant ahora = Instant.now();

        view.clear();
        for (Match m : all) {
            if (!PartidasPresenter.pasaFiltroModo(modo, todosModos(), m.mode)) continue;
            if (!PartidasPresenter.pasaFiltroMapa(mapaCombo.getSelectedIndex(), mapaCombo.getSelectedItem(), m.map)) continue;
            if (!PartidasPresenter.pasaFiltroPeriodo(periodoCombo.getSelectedIndex(), m.started, ahora)) continue;
            if (!filtroRival.isEmpty()) { asignarRef(m); if (!rivalCoincide(m, filtroRival)) continue; }
            if (!selIds.isEmpty()) {
                boolean alguno = false;
                for (long id : selIds) if (m.tieneJugador(id)) { alguno = true; break; }
                if (!alguno) continue;
            }
            asignarRef(m);
            if (m.finished == null) {
                m.estado = anfitrion.enCursoReal(m) ? "\u25B6" : "\u2014";
                m.enDisco = false;
                m.enJuego = false;
            } else {
                m.enDisco = Files.exists(anfitrion.destino(m));
                m.enJuego = sgConocida != null
                        && Files.exists(sgConocida.resolve(anfitrion.destino(m).getFileName().toString()));
            }
            view.add(m);
        }
        tableModel.fireTableDataChanged();
        mostrarGuiaVacia(all.isEmpty());
        actualizarTextoBuscar();
        if (!all.isEmpty())
            anfitrion.estado(view.size() + t(" de ", " of ") + all.size() + t(" partidas (según filtros).", " games (per filters)."));
    }

    // ======================================================================
    // Cards / controles / texto del botón
    // ======================================================================

    public void mostrarGuiaVacia(boolean guia) {
        if (recsCards != null) ((java.awt.CardLayout) recsCards.getLayout()).show(recsCards, guia ? "guia" : "tabla");
        SwingUtilities.invokeLater(anfitrion::actualizarControlesTabla);
    }

    /** El botón principal dice a quién va a buscar. */
    public void actualizarTextoBuscar() {
        if (fetchWorker != null) return;
        String quien;
        objetivoEtiqueta = null;
        if (enlaceWatchlist.invitado() != null) quien = anfitrion.nombreVisible(enlaceWatchlist.invitado().id(), enlaceWatchlist.invitado().name());
        else if (anfitrion.perfilAbiertoPid() > 0 && enlaceWatchlist.seleccionSize() == 0 && (anfitrion.perfilAbierto() || enlaceWatchlist.modoTop())) { quien = anfitrion.perfilNombreAbierto(); objetivoEtiqueta = new Player(anfitrion.perfilAbiertoPid(), anfitrion.perfilNombreAbierto(), ""); }
        else {
            int n = enlaceWatchlist.seleccionSize();
            if (n > 0) quien = n + t(" seleccionado" + (n > 1 ? "s" : ""), " selected");
            else if (enlaceWatchlist.modoTop()) quien = t("selecciona a alguien", "select someone");
            else quien = t("todo el grupo", "whole group");
        }
        fetchBtn.setText(t("Buscar partidas", "Search games") + " (" + quien + ")");
        if (guiaBtn != null) guiaBtn.setText(fetchBtn.getText());
        enlaceWatchlist.actualizarTextoForma();
    }

    public boolean hayPartidas() { return !all.isEmpty(); }

    /** Repinta la tabla sin recalcular filtros (usado por DialogosJugador tras editar nota/alias). */
    public void refrescarTabla() { tableModel.fireTableDataChanged(); }

    // ======================================================================
    // Azar por ELO / Guess the ELO
    // ======================================================================

    public void buscarAleatorias() { buscarAleatorias(false); }

    public void buscarAleatorias(boolean continuar) {
        if (!continuar) {
            JSpinner minSp = new JSpinner(new SpinnerNumberModel(Integer.parseInt(leerConfig("elo_min", "2100")), 0, 4000, 50));
            JSpinner maxSp = new JSpinner(new SpinnerNumberModel(Integer.parseInt(leerConfig("elo_max", "2300")), 0, 4000, 50));
            JSpinner horasSp = new JSpinner(new SpinnerNumberModel(Integer.parseInt(leerConfig("elo_horas", "48")), 1, 336, 6));
            String cualquiera = t("(cualquiera)", "(any)");
            JComboBox<String> mapaCb = new JComboBox<>();
            mapaCb.addItem(cualquiera);
            for (String m : anfitrion.mapasConocidos()) mapaCb.addItem(m);
            mapaCb.setSelectedIndex(0);
            mapaCb.setSelectedItem(leerConfig("elo_mapa", cualquiera));
            JComboBox<String> civCb = new JComboBox<>();
            civCb.addItem(cualquiera);
            for (String c : anfitrion.civsConocidas()) civCb.addItem(c);
            civCb.setSelectedIndex(0);
            civCb.setSelectedItem(leerConfig("elo_civ", cualquiera));
            JComboBox<String> intCb = new JComboBox<>(new String[]{ t("Rápida", "Quick"), t("Amplia", "Broad"), t("Exhaustiva", "Exhaustive") });
            try { intCb.setSelectedIndex(Integer.parseInt(leerConfig("elo_intensidad", "0"))); } catch (Exception ignored) { }
            JPanel form = new JPanel(new GridLayout(6, 2, 8, 4));
            form.add(new JLabel(t("ELO mínimo:", "Min ELO:"))); form.add(minSp);
            form.add(new JLabel(t("ELO máximo:", "Max ELO:"))); form.add(maxSp);
            form.add(new JLabel(t("Últimas horas:", "Last hours:"))); form.add(horasSp);
            form.add(new JLabel(t("Mapa:", "Map:"))); form.add(mapaCb);
            form.add(new JLabel(t("Civilización (uno de los dos):", "Civilization (either player):"))); form.add(civCb);
            form.add(new JLabel(t("Intensidad:", "Intensity:"))); form.add(intCb);
            JLabel avisoCiv = new JLabel(t("Con civ o mapa, la primera tirada tarda ~1 min; repetirla continúa donde quedó y reutiliza lo ya leído.",
                    "With civ or map filters the first run takes ~1 min; running it again picks up where it left off."));
            avisoCiv.setFont(avisoCiv.getFont().deriveFont(Font.PLAIN, 11f));
            JPanel formWrap = new JPanel(new BorderLayout(0, 8));
            formWrap.add(form, BorderLayout.CENTER);
            formWrap.add(avisoCiv, BorderLayout.SOUTH);
            if (JOptionPane.showConfirmDialog(ventana, formWrap, t("Partidas 1v1 al azar por ELO", "Random 1v1s by ELO"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            int a = (int) minSp.getValue(), b = (int) maxSp.getValue();
            guardarConfig("elo_min", String.valueOf(Math.min(a, b)));
            guardarConfig("elo_max", String.valueOf(Math.max(a, b)));
            guardarConfig("elo_horas", String.valueOf((int) horasSp.getValue()));
            guardarConfig("elo_mapa", String.valueOf(mapaCb.getSelectedItem()));
            guardarConfig("elo_civ", String.valueOf(civCb.getSelectedItem()));
            guardarConfig("elo_intensidad", String.valueOf(intCb.getSelectedIndex()));
        }
        final int lo = Integer.parseInt(leerConfig("elo_min", "2100"));
        final int hi = Integer.parseInt(leerConfig("elo_max", "2300"));
        String mSel = leerConfig("elo_mapa", "");
        String cSel = leerConfig("elo_civ", "");
        final String mapaSel = esCualquiera(mSel) ? null : mSel;
        final String civSel  = esCualquiera(cSel) ? null : cSel;
        final int multAzar = switch (Integer.parseInt(leerConfig("elo_intensidad", "0"))) {
            case 1 -> 3; case 2 -> 10; default -> 1; };

        anfitrion.mostrarDirectos(false);
        fetchBtn.setEnabled(false);
        azarBtn.setEnabled(false);
        gteBtn.setEnabled(false);
        SUJETOS.clear();
        refrescarSujetos(List.of(), false);
        taparResultados();
        anfitrion.trabajando(true);
        final long miSerial = anfitrion.operacionActual();
        final int hours = Integer.parseInt(leerConfig("elo_horas", "48"));
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
        anfitrion.estado(t("Buscando partidas al azar ", "Searching random games ") + lo + "–" + hi + "…");
        log("azar #" + miSerial + ": inicio " + lo + "-" + hi + " h=" + hours + " mapa=" + mapaSel + " civ=" + civSel
                + " x" + multAzar + " continuar=" + continuar + " stop=" + anfitrion.detenido());
        new SwingWorker<List<Match>, String>() {
            @Override protected List<Match> doInBackground() throws Exception {
                return azarService.buscarAleatorias(lo, hi, mapaSel, civSel, hours, multAzar, cutoff, miSerial, this::publish);
            }
            @Override protected void process(List<String> msgs) { anfitrion.estado(msgs.get(msgs.size() - 1)); }
            @Override protected void done() {
                if (!PartidasPresenter.vigente(miSerial, anfitrion.operacionActual())) {
                    log("azar #" + miSerial + ": terminó superada por la op #" + anfitrion.operacionActual() + "; resultado ignorado");
                    return;
                }
                fetchBtn.setEnabled(true);
                azarBtn.setEnabled(true);
                gteBtn.setEnabled(true);
                anfitrion.trabajando(false);
                anfitrion.continuarDisponible(true);
                try {
                    List<Match> res = get();
                    log("azar #" + miSerial + ": done, " + res.size() + " partidas, stop=" + anfitrion.detenido());
                    anfitrion.aprenderCatalogos(res);
                    if (anfitrion.detenido()) {
                        anfitrion.estado(t("Búsqueda detenida.", "Search stopped.")
                                + (res.isEmpty() ? "" : "  " + res.size()
                                   + t(" encontradas hasta el corte, aplicadas.", " found before the cut, applied.")));
                        if (res.isEmpty()) return;
                    } else if (res.isEmpty()) {
                        anfitrion.estado(t("Nada en ", "Nothing in ") + lo + "–" + hi
                                + t(" en las últimas ", " in the last ") + hours
                                + t(" h. Detalle del muestreo en descargas.log.", " h. Sampling details in descargas.log."));
                        return;
                    }
                    List<Player> refs = new ArrayList<>();
                    Set<Long> refIds = new HashSet<>();
                    for (Match m : res) {
                        m.azar = true;
                        ajustarRefAzar(m, civSel);
                        if (refIds.add(m.refId)) {
                            String nom = texto.refNombre(m);
                            refs.add(new Player(m.refId, nom, "", 0));
                        }
                    }
                    SUJETOS.clear();
                    SUJETOS.addAll(refIds);
                    vistaDeSujetos = enlaceWatchlist.vistaActualId();
                    refrescarSujetos(refs, false);
                    all.clear();
                    all.addAll(res);
                    enlaceWatchlist.limpiarSeleccion();
                    refreshModeCombo();
                    applyFilters();
                    String extra = "";
                    if (res.size() < 10 && azarService.tramoAgotado())
                        extra = t(" No hay más con esos filtros: tramo entero revisado (amplía horas o rango).",
                                  " Nothing else with those filters: whole bracket checked (widen hours or range).");
                    else if (res.size() < 10)
                        extra = t(" Repite la búsqueda: continúa donde lo dejó.",
                                  " Run it again: it picks up where it left off.");
                    anfitrion.estado(res.size() + t(" partidas 1v1 al azar, ELO ", " random 1v1s, ELO ") + lo + "–" + hi
                            + t(", últimas ", ", last ") + hours + " h." + extra);
                } catch (Exception ex) {
                    anfitrion.estado(anfitrion.detenido() ? t("Detenido.", "Stopped.") : "Error: " + causa(ex));
                    log("al azar por ELO: ERROR " + causa(ex));
                }
            }
        }.execute();
    }

    public void buscarGte() {
        anfitrion.mostrarDirectos(false);
        fetchBtn.setEnabled(false);
        azarBtn.setEnabled(false);
        gteBtn.setEnabled(false);
        SUJETOS.clear();
        refrescarSujetos(List.of(), false);
        taparResultados();
        anfitrion.trabajando(true);
        final long miSerial = anfitrion.operacionActual();
        int hours = Integer.parseInt(leerConfig("elo_horas", "48"));
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
        anfitrion.estado(t("Preparando Guess the ELO…", "Preparing Guess the ELO…"));
        new SwingWorker<List<Match>, String>() {
            @Override protected List<Match> doInBackground() throws Exception {
                return azarService.buscarGte(cutoff, this::publish);
            }
            @Override protected void process(List<String> msgs) { anfitrion.estado(msgs.get(msgs.size() - 1)); }
            @Override protected void done() {
                if (!PartidasPresenter.vigente(miSerial, anfitrion.operacionActual())) { log("gte #" + miSerial + ": terminó superada por la op #" + anfitrion.operacionActual()); return; }
                fetchBtn.setEnabled(true);
                azarBtn.setEnabled(true);
                gteBtn.setEnabled(true);
                anfitrion.trabajando(false);
                try {
                    List<Match> res = get();
                    anfitrion.aprenderCatalogos(res);
                    if (res.isEmpty()) {
                        anfitrion.estado(t("Sin partidas para Guess the ELO en las últimas ", "No games for Guess the ELO in the last ") + hours
                                + t(" h. Detalle en descargas.log.", " h. Details in descargas.log."));
                        return;
                    }
                    all.clear();
                    all.addAll(res);
                    enlaceWatchlist.limpiarSeleccion();
                    refreshModeCombo();
                    applyFilters();
                    anfitrion.estado(res.size() + t(" partidas Guess the ELO (archivos: «Guess the ELO ", " Guess the ELO games (files: “Guess the ELO ")
                            + res.get(0).gte + t("»–«", "”–“") + res.get(res.size() - 1).gte
                            + t("»). Adivina y comprueba con «Revelar resultado…».", "”). Guess, then check with “Reveal result…”."));
                } catch (Exception ex) {
                    anfitrion.estado(anfitrion.detenido() ? t("Detenido.", "Stopped.") : "Error: " + causa(ex));
                    log("Guess the ELO: ERROR " + causa(ex));
                }
            }
        }.execute();
    }

    // ======================================================================
    // Consulta de partidas y descarga
    // ======================================================================

    public void fetchMatches(JButton btn) {
        if (fetchWorker != null) {
            fetchWorker.cancel(true);
            return;
        }
        if (enlaceWatchlist.objetivoForzado() == null && enlaceWatchlist.invitado() == null && anfitrion.perfilAbierto() && anfitrion.perfilAbiertoPid() > 0 && enlaceWatchlist.seleccionSize() == 0) {
            enlaceWatchlist.fijarObjetivoForzado(new Player(anfitrion.perfilAbiertoPid(), anfitrion.perfilNombreAbierto(), enlaceWatchlist.grupoDestino()));
            anfitrion.mostrarDirectos(false);
        }
        if (enlaceWatchlist.totalJugadores() == 0 && enlaceWatchlist.invitado() == null && enlaceWatchlist.objetivoForzado() == null) {
            JOptionPane.showMessageDialog(ventana, t("Añade antes algún jugador a la lista.",
                    "Add a player to the list first."));
            return;
        }
        List<Player> sel = new ArrayList<>();
        if (enlaceWatchlist.objetivoForzado() != null) {
            sel.add(enlaceWatchlist.objetivoForzado());
            enlaceWatchlist.limpiarObjetivoForzado();
        } else if (enlaceWatchlist.invitado() != null) {
            sel.add(enlaceWatchlist.invitado());
        } else if (objetivoEtiqueta != null && enlaceWatchlist.seleccionSize() == 0) {
            sel.add(objetivoEtiqueta);
        } else if (enlaceWatchlist.modoTop()) {
            List<Player> selTop = enlaceWatchlist.seleccion();
            if (selTop.size() > 15) {
                selTop = selTop.subList(0, 15);
                anfitrion.estado(t("En ★ el máximo son 15 perfiles por tanda: busco los 15 primeros seleccionados.",
                        "In ★ the cap is 15 profiles per run: searching the first 15 selected."));
            }
            if (!selTop.isEmpty()) sel.addAll(selTop);
            else if (enlaceWatchlist.soloVivosMarcado())
                for (int i = 0; i < enlaceWatchlist.totalJugadores(); i++) sel.add(enlaceWatchlist.jugador(i));
            else {
                JOptionPane.showMessageDialog(ventana,
                        t("En ★ (Top ladder o Top país), selecciona jugadores concretos (clic o Ctrl+clic en la lista)\no activa el chip «● Jugando» antes de buscar.",
                          "In ★ (Top ladder or Country top), select specific players (click or Ctrl+click the list)\nor turn on the “● Live” checkbox before searching."),
                        t("Buscar en el top", "Search the top"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        } else {
            List<Player> selNorm = enlaceWatchlist.seleccion();
            if (!selNorm.isEmpty()) sel.addAll(selNorm);
            else for (int i = 0; i < enlaceWatchlist.totalJugadores(); i++) sel.add(enlaceWatchlist.jugador(i));
        }
        final List<Player> tracked = (enlaceWatchlist.invitado() == null && !enlaceWatchlist.modoTop()) ? enlaceWatchlist.conFamilias(sel) : sel;
        SUJETOS.clear();
        filtroSujetos.clear();
        if (rivalField != null && !rivalField.getText().isEmpty()) rivalField.setText("");
        fallosFetch = 0;
        mostrarGuiaVacia(false);
        taparResultados();
        for (Player px : tracked) SUJETOS.add(px.id());
        vistaDeSujetos = enlaceWatchlist.vistaActualId();
        refrescarSujetos(tracked, enlaceWatchlist.invitado() != null);

        anfitrion.mostrarDirectos(false);
        enlaceWatchlist.guardarVentanaHoras();
        btn.setText(t("Detener", "Stop"));
        btn.setToolTipText(t("Detiene la búsqueda en curso", "Stops the current search"));
        azarBtn.setEnabled(false);
        gteBtn.setEnabled(false);
        anfitrion.trabajando(true);
        final long miSerial = anfitrion.operacionActual();
        int hours = enlaceWatchlist.horasVentana();
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
        SwingWorker<List<Match>, String> fw = new SwingWorker<>() {
            final Set<Long> exitosos = new HashSet<>();
            @Override protected List<Match> doInBackground() {
                anfitrion.anotarHiloOperacion();
                Map<Long, Match> unicos = new LinkedHashMap<>();
                topeAlcanzado = false;
                final int MAX_PAGINAS = 6, MAX_TOTAL = 600;
                for (Player pl : tracked) {
                    if (isCancelled()) break;
                    boolean fallo = false;
                    for (int pagina = 1; pagina <= MAX_PAGINAS; pagina++) {
                        if (isCancelled()) break;
                        publish(t("Consultando ", "Checking ") + pl.name() + (pagina > 1 ? " (" + t("pág. ", "p. ") + pagina + ")" : "") + "…");
                        boolean seguir = false;
                        try {
                            Iterable<Match> leidas = anfitrion.paginaDePartidas(pl.id(), pagina, perPage);
                            int n = 0; Instant masAntigua = null;
                            for (Match m : leidas) {
                                if (m == null) continue;
                                n++;
                                Instant ref = m.finished != null ? m.finished : m.started;
                                if (ref != null && (masAntigua == null || ref.isBefore(masAntigua))) masAntigua = ref;
                                if (m.finished == null && !anfitrion.enCursoReal(m)) continue;
                                if (m.finished != null && m.finished.isBefore(cutoff)) continue;
                                unicos.putIfAbsent(m.id, m);
                            }
                            seguir = n >= perPage && masAntigua != null && masAntigua.isAfter(cutoff);
                            if (seguir && pagina == MAX_PAGINAS) topeAlcanzado = true;
                            if (unicos.size() >= MAX_TOTAL) { topeAlcanzado = true; seguir = false; }
                            Thread.sleep(pausaMs);
                        } catch (Exception ex) {
                            fallo = true;
                            if (isCancelled() || ex instanceof InterruptedException) break;
                            fallosFetch++;
                            publish(t("Aviso: fallo con ", "Heads-up: failed with ") + pl.name() + " (" + causa(ex) + ")");
                        }
                        if (!seguir) break;
                    }
                    if (!isCancelled() && !fallo) exitosos.add(pl.id());
                    if (unicos.size() >= MAX_TOTAL) break;
                }
                List<Match> lista = new ArrayList<>(unicos.values());
                lista.sort(Comparator.comparing((Match m) -> m.finished == null ? Instant.MAX : m.finished).reversed());
                return lista;
            }
            @Override protected void process(List<String> msgs) { anfitrion.estado(msgs.get(msgs.size() - 1)); }
            @Override protected void done() {
                if (!PartidasPresenter.vigente(miSerial, anfitrion.operacionActual())) { log("buscar #" + miSerial + ": terminó superada por la op #" + anfitrion.operacionActual()); fetchWorker = null; return; }
                fetchWorker = null;
                actualizarTextoBuscar();
                btn.setToolTipText(null);
                btn.setEnabled(true);
                azarBtn.setEnabled(true);
                gteBtn.setEnabled(true);
                anfitrion.trabajando(false);
                if (isCancelled()) {
                    anfitrion.estado(t("Búsqueda detenida.", "Search stopped."));
                    return;
                }
                try {
                    List<Match> res = get();
                    anfitrion.aprenderCatalogos(res);
                    List<Long> idsTracked = new ArrayList<>();
                    for (Player pl : tracked) idsTracked.add(pl.id());
                    BarridoVivos.DecisionBuscar dec = barridoVivos.decidirVivos(res, idsTracked, exitosos);
                    for (Player pl : tracked) {
                        Long v = dec.vivos().get(pl.id());
                        if (v != null) { EstadoVivo.SISTEMA.marcarJugando(pl.id(), v, dec.infos().get(pl.id())); }
                        else if (dec.fuera().contains(pl.id())) { EstadoVivo.SISTEMA.marcarFuera(pl.id()); }
                    }
                    enlaceWatchlist.actualizarIndicadoresVivos();
                    all.clear();
                    all.addAll(res);
                    refreshModeCombo();
                    applyFilters();
                    anfitrion.estado(view.size() + t(" de ", " of ") + all.size() + t(" partidas en las últimas ", " games in the last ") + textoVentana(hours) + "."
                            + (topeAlcanzado
                                ? "  \u26A0 " + t("Tope de la búsqueda alcanzado: puede faltar historial antiguo — acorta la ventana o filtra por modo.",
                                                  "Search cap reached: older history may be missing — shorten the window or filter by mode.")
                                : "")
                            + (fallosFetch > 0
                                ? "  \u26A0 " + fallosFetch + t(" jugador(es) SIN RESPUESTA del servicio (¿429/caído?): sus partidas faltan — reintenta en un minuto.",
                                                              " player(s) got NO RESPONSE from the service (429/down?): their games are missing — retry in a minute.")
                                : ""));
                } catch (Exception ex) {
                    anfitrion.estado("Error: " + causa(ex));
                }
            }
        };
        fetchWorker = fw;
        fw.execute();
    }

    public List<Match> selectedRows() {
        List<Match> out = new ArrayList<>();
        for (int r : table.getSelectedRows()) out.add(view.get(table.convertRowIndexToModel(r)));
        return out;
    }

    public List<Match> allRows() { return new ArrayList<>(view); }

    /** Nombre del jugador de referencia de la partida (columna «Jugador»); lo usa Watchlist para
     *  «tipCuentaVinculada» (tooltip de cuenta hermana). */
    public String refNombre(Match m) { return texto.refNombre(m); }

    public void download(List<Match> objetivoIn) { download(objetivoIn, false); }

    public void download(List<Match> objetivoIn, boolean enviarSiempre) {
        List<Match> objetivo = new ArrayList<>();
        List<Match> vivas = new ArrayList<>();
        for (Match m : objetivoIn) (m.finished == null ? vivas : objetivo).add(m);
        if (!vivas.isEmpty() && objetivo.isEmpty()) {
            if (vivas.size() == 1) anfitrion.espectarPartida(vivas.get(0).id);
            else anfitrion.estado(t("Esas partidas están EN DIRECTO: doble clic en una para espectarla.",
                    "Those games are LIVE: double-click one to spectate."));
            return;
        }
        if (!vivas.isEmpty())
            anfitrion.estado(t("Las partidas EN DIRECTO no se descargan; se saltan.",
                    "LIVE games can't be downloaded; skipping them."));
        if (objetivo.isEmpty()) { anfitrion.estado(t("No hay partidas seleccionadas.", "No games selected.")); return; }
        if (!descargaSinCambiarVista) anfitrion.mostrarDirectos(false);
        descargaSinCambiarVista = false;
        dlSel.setEnabled(false); dlAll.setEnabled(false);
        anfitrion.trabajando(true);
        final long miSerial = anfitrion.operacionActual();
        Set<Long> trackedIds = new HashSet<>();
        for (int i = 0; i < enlaceWatchlist.totalJugadores(); i++) trackedIds.add(enlaceWatchlist.jugador(i).id());
        final boolean autoCopiar = anfitrion.autoCopiarAlDescargar();
        final boolean autoCopiarFinal = autoCopiar || enviarSiempre;
        final Path sgAuto = autoCopiarFinal ? obtenerSavegame(enviarSiempre) : null;
        if (autoCopiarFinal && sgAuto == null)
            log("copia automática a savegame activada pero sin carpeta resuelta: no se copiará");

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                anfitrion.anotarHiloOperacion();
                try { Files.createDirectories(anfitrion.recsDir()); } catch (IOException ignored) {}
                int ok = 0, copiadas = 0;
                for (Match m : objetivo) {
                    if (anfitrion.detenido()) break;
                    setEstado(m, "descargando…");
                    RecService.Resultado r = recService.procesar(m, trackedIds, autoCopiarFinal, sgAuto, anfitrion::detenido);
                    boolean hecho = r.estado() != RecService.Estado.FALLO;
                    if (hecho) {
                        m.enDisco = true;
                        ok++;
                        if (r.enJuego()) {
                            copiadas++;
                            m.enJuego = true;
                        }
                    }
                    setEstado(m, hecho ? (r.enJuego() ? t("✓✓ en juego", "✓✓ in game") : "✓ guardada") : "✗ no disponible");
                    // La pausa de cortesía es para espaciar peticiones a la API: si la rec se reutilizó del
                    // disco (RecService.Resultado.reutilizada), no hubo ninguna que espaciar.
                    if (!r.reutilizada()) anfitrion.dormir(pausaMs);
                }
                final int n = ok, tot = objetivo.size(), cop = copiadas;
                final boolean parada = anfitrion.detenido();
                SwingUtilities.invokeLater(() ->
                        anfitrion.estado((parada ? t("Detenido. ", "Stopped. ") : "")
                                + n + "/" + tot + t(" recs guardadas en ./", " recs saved to ./") + anfitrion.recsDir()
                                + (cop > 0 ? "  ·  " + cop + t(" al juego", " to the game") : "")
                                + (n < tot ? t("  ·  detalle en descargas.log", "  ·  details in descargas.log") : "")));
                return null;
            }
            @Override protected void done() {
                if (!PartidasPresenter.vigente(miSerial, anfitrion.operacionActual())) { log("descargas #" + miSerial + ": terminó superada por la op #" + anfitrion.operacionActual()); return; }
                dlSel.setEnabled(true);
                dlAll.setEnabled(true);
                anfitrion.trabajando(false);
                if (alTerminarDescarga != null) { Runnable r = alTerminarDescarga; alTerminarDescarga = null; r.run(); }
            }
        }.execute();
    }

    void setEstado(Match m, String txt) {
        SwingUtilities.invokeLater(() -> {
            m.estado = txt;
            int idx = view.indexOf(m);
            if (idx >= 0) tableModel.fireTableRowsUpdated(idx, idx);
        });
    }

    /** Llamado por Perfil (vía el Anfitrion de la ventana) cuando trae partidas ya cargadas a la tabla: mismo
     *  orden que hoy (limpiar la selección de la watchlist va justo después de volcar en `all`, antes de
     *  refrescar el combo de modos); la ventana sigue ocultando Directos alrededor de esta llamada. */
    public void cargarPartidasEnTabla(List<Match> partidas, Player sujeto, String vistaId) {
        SUJETOS.clear(); SUJETOS.add(sujeto.id());
        vistaDeSujetos = vistaId;
        refrescarSujetos(List.of(sujeto), false);
        all.clear(); all.addAll(partidas);
        enlaceWatchlist.limpiarSeleccion();
        refreshModeCombo();
        applyFilters();
        mostrarGuiaVacia(false);
    }

    /** Descarga sin cambiar de pestaña (Perfil/Live now la piden desde su propia vista). */
    public void descargarSinCambiarVista(List<Match> partidas, boolean enviarAlJuego, Runnable alTerminar) {
        descargaSinCambiarVista = true;
        alTerminarDescarga = alTerminar;
        download(partidas, enviarAlJuego);
    }

    // ======================================================================
    // Tabla: persistencia de orden/anchos y modelo
    // ======================================================================

    void aplicarOrdenColumnas(String orden) {
        try {
            String[] partes = orden.split(",");
            if (partes.length != table.getColumnCount()) return;
            for (int destino = 0; destino < partes.length; destino++) {
                int modelo = Integer.parseInt(partes[destino].trim());
                int actual = table.convertColumnIndexToView(modelo);
                if (actual >= 0 && actual != destino) table.getColumnModel().moveColumn(actual, destino);
            }
        } catch (RuntimeException ex) { log("columnas: orden ilegible: " + orden); }
    }

    void guardarColumnas() {
        StringBuilder orden = new StringBuilder();
        for (int v = 0; v < table.getColumnCount(); v++) { if (v > 0) orden.append(','); orden.append(table.convertColumnIndexToModel(v)); }
        StringBuilder anchos = new StringBuilder();
        for (int m = 0; m < table.getModel().getColumnCount(); m++) { if (m > 0) anchos.append(','); anchos.append(table.getColumnModel().getColumn(table.convertColumnIndexToView(m)).getWidth()); }
        guardarConfig("tabla_orden", orden.toString());
        guardarConfig("tabla_anchos", anchos.toString());
    }

    public final class MatchesTableModel extends AbstractTableModel {
        final String[] cols = { t("Fecha", "Date"), t("Jugador", "Player"), "Civ", t("Modo", "Mode"), t("Mapa", "Map"),
                t("Rival", "Opponent"), t("Civ rival", "Opp. civ"), "Rec", t("Resultado", "Result") };
        @Override public int getRowCount() { return view.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) {
            return switch (c) { case 0 -> PartidasTexto.FechaCell.class; default -> String.class; };
        }
        @Override public Object getValueAt(int r, int c) {
            Match m = view.get(r);
            return switch (c) {
                case 0 -> m.finished == null && !anfitrion.enCursoReal(m)
                        ? new PartidasTexto.FechaCell(null, m.started)
                        : new PartidasTexto.FechaCell(m.finished);
                case 1 -> revelada(m) ? texto.refConVeredicto(m) : texto.refNombre(m);
                case 2 -> m.civDe(m.refId);
                case 3 -> m.mode;
                case 4 -> m.map;
                case 5 -> texto.rivalTexto(m, revelada(m));
                case 6 -> m.civRival();
                case 8 -> m.finished == null ? "" : (revelada(m) ? "\u25C9" : "\u25CE");
                case 7 -> !m.estado.isBlank() ? m.estado
                          : m.enJuego ? t("✓✓ en juego", "✓✓ in game")
                          : m.enDisco ? t("✓ en disco", "✓ on disk")
                          : (m.povsConRec() > 0 ? m.povsConRec() + " POV" : "¿?");
                default -> "";
            };
        }
    }
}
