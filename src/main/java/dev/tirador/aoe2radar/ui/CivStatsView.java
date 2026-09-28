package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.Matchup;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.service.NombresStats;
import dev.tirador.aoe2radar.service.StatsService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ImageIcon;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.ScrollPaneConstants;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BasicStroke;
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
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.tirador.aoe2radar.service.CalculoStats.MIN_PARTIDAS_CIV;
import static dev.tirador.aoe2radar.service.CalculoStats.MUESTRA_FIABLE;
import static dev.tirador.aoe2radar.service.CalculoStats.POCAS_PARTIDAS;
import static dev.tirador.aoe2radar.service.NombresStats.modoNombre;
import static dev.tirador.aoe2radar.service.NombresStats.nombreCivStats;
import static dev.tirador.aoe2radar.service.NombresStats.nombreMapaStats;
import static dev.tirador.aoe2radar.service.NombresStats.tramoNombre;
import static dev.tirador.aoe2radar.service.NombresStats.ventanaNombre;
import static dev.tirador.aoe2radar.ui.Componentes.PALETA_LADDER;
import static dev.tirador.aoe2radar.ui.Componentes.colorWr;
import static dev.tirador.aoe2radar.ui.Componentes.pasoBonito;
import static dev.tirador.aoe2radar.ui.Componentes.tituloSeccion;
import static dev.tirador.aoe2radar.ui.Iconos.iconoCiv;
import static dev.tirador.aoe2radar.ui.Iconos.iconoMapa;
import static dev.tirador.aoe2radar.ui.Listas.Celda;
import static dev.tirador.aoe2radar.ui.Listas.Pct;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.Formato.miles;
import static dev.tirador.aoe2radar.util.Formato.pct1;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * La pestaña «Civ Stats»: winrate y pick rate por civilización, con mapa y tramo de ELO, más tendencias
 * mensuales y matchups 1v1. Movida tal cual desde SpoilerFreeRecs; el trabajo de red (bajar el resumen de la
 * ventana elegida) lo pide a {@link CivStatsPresenter}, y el cálculo sobre los datos ya en memoria
 * (agregarCivs, wilson…) lo hace aquí porque no es red. Comparte {@link FiltroStats} con la banda del tech
 * tree (misma ventana, modo, mapa y tramo); avisa de sus cambios al tech tree por {@link EnlaceTechTree},
 * sin conocer esa vista.
 */
public final class CivStatsView {

    /** Lo que Civ Stats necesita del tech tree (ui.TechTreeView; la ventana lo cablea). */
    public interface EnlaceTechTree {
        /** Repinta la banda de winrate del tech tree con los filtros compartidos, si esa vista está abierta. */
        void repintarBandaTechTree();
    }

    private final StatsService stats;
    private final FiltroStats filtroStats;
    private final Listas listas;
    private final Navegacion navegacion;
    private final Window ventana;
    private final EnlaceTechTree enlace;
    private final CivStatsPresenter presentador;

    JPanel civStatsPanel;
    public JComboBox<String> stModoCombo, stVentanaCombo, stMapaCombo;
    public JLabel stEstado;
    JPanel stTarjetas, stMasJugadas, stMejorPorMapa, stPool;
    JTable stTabla;
    public DefaultTableModel stModelo;
    TableRowSorter<DefaultTableModel> stSorter;
    public TendenciasPanel stTendencias;
    MatrizPanel stMatriz;
    JLabel stTituloTabla, stTituloMatriz;
    final List<String> stCivsSeleccionadas = new ArrayList<>();
    JCheckBox stMasJugadasCheck; JComboBox<String> stTendVentana, stTendMapa; JToggleButton stTendPick, stTendWr; boolean stRellenandoTend; String tendMapa = "*", tendTramo = "*";
    JPanel stFilaMedio, stFilaTend;
    public SelectorRangoElo stRango;
    SelectorRangoElo tendRango;
    boolean stRellenandoMapas;
    /** F3 (1.3): true mientras statsPintar vacía y rellena la tabla; el oyente de selección no toca la civ elegida. */
    boolean stRepintandoTabla;

    public CivStatsView(StatsService stats, FiltroStats filtroStats, Listas listas, Navegacion navegacion, Tareas tareas, Window ventana, EnlaceTechTree enlace) {
        this.stats = stats;
        this.filtroStats = filtroStats;
        this.listas = listas;
        this.navegacion = navegacion;
        this.ventana = ventana;
        this.enlace = enlace;
        this.presentador = new CivStatsPresenter(stats, tareas, new CivStatsPresenter.Pantalla() {
            @Override public void mostrarEstado(String texto) { stEstado.setText(texto); }
            @Override public void datosListos() { filtrosCambiados(true); }
            @Override public void repintarTendencias() { stTendencias.repaint(); }
        });
        construirPanelCivStats();
    }

    /** El panel de la pestaña, para el CardLayout de la ventana. */
    public JPanel panel() { return civStatsPanel; }

    /** Las dos filas miden lo que mide su contenido (listas enteras a la vista), en cualquier pantalla. */
    void ajustarFilasCivStats() {
        for (JPanel f : new JPanel[]{ stFilaMedio, stFilaTend }) {
            if (f == null) continue;
            f.setPreferredSize(null);
            for (Component c : f.getComponents()) { c.setPreferredSize(null); if (c instanceof JPanel jp && jp != stPool) jp.setPreferredSize(new Dimension(10, jp.getPreferredSize().height)); }
            Dimension pref = f.getPreferredSize();
            f.setPreferredSize(new Dimension(10, pref.height));
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        }
        if (stPool != null) stPool.setPreferredSize(new Dimension(10, stPool.getPreferredSize().height));
    }

    // ----- Panel «Civ Stats» -----
    JPanel construirPanelCivStats() {
        civStatsPanel = new JPanel(new BorderLayout(8, 6));
        civStatsPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel norte = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        stModoCombo = new JComboBox<>();
        for (String m : stats.modos()) stModoCombo.addItem(modoNombre(m));
        stModoCombo.setSelectedIndex(Math.max(0, Arrays.asList(stats.modos()).indexOf(filtroStats.modo())));
        stModoCombo.addActionListener(e -> { filtroStats.modo(stats.modos()[Math.max(0, stModoCombo.getSelectedIndex())]); guardarConfig("stats_modo", filtroStats.modo()); filtroStats.civSeleccionada(null); filtrosCambiados(true); });
        norte.add(stModoCombo);
        stVentanaCombo = new JComboBox<>();
        for (String v : stats.clavesVentanas()) stVentanaCombo.addItem(ventanaNombre(v));
        stVentanaCombo.setSelectedIndex(Math.max(0, Arrays.asList(stats.clavesVentanas()).indexOf(filtroStats.ventana())));
        stVentanaCombo.addActionListener(e -> { if (stRellenandoMapas) return; filtroStats.ventana(stats.clavesVentanas()[Math.max(0, stVentanaCombo.getSelectedIndex())]); guardarConfig("stats_ventana", filtroStats.ventana()); navegacion.abrirCivStats(); });
        norte.add(stVentanaCombo);
        stMapaCombo = new JComboBox<>();
        stMapaCombo.setPrototypeDisplayValue("African Clearing (99.999)   ");
        stMapaCombo.addActionListener(e -> { if (stRellenandoMapas) return; Object id = stMapaCombo.getClientProperty("claves"); if (id instanceof List<?> l && stMapaCombo.getSelectedIndex() >= 0 && stMapaCombo.getSelectedIndex() < l.size()) { filtroStats.mapa(String.valueOf(l.get(stMapaCombo.getSelectedIndex()))); guardarConfig("stats_mapa", filtroStats.mapa()); filtrosCambiados(true); } });
        norte.add(stMapaCombo);
        stRango = new SelectorRangoElo(ventana, NombresStats::tramoNombre);
        stRango.alCambiar = r -> { if (stRellenandoMapas) return; filtroStats.tramo(r); guardarConfig("stats_tramo", filtroStats.tramo()); filtrosCambiados(true); };
        norte.add(stRango);
        stEstado = new JLabel();
        stEstado.setFont(stEstado.getFont().deriveFont(Font.PLAIN, 11f));
        norte.add(stEstado);
        civStatsPanel.add(norte, BorderLayout.NORTH);

        PanelScrollable cuerpo = new PanelScrollable();
        cuerpo.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 4));
        stTarjetas = new JPanel(new GridLayout(1, 5, 8, 0));
        stTarjetas.setAlignmentX(0f);
        stTarjetas.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        cuerpo.add(stTarjetas);
        cuerpo.add(Box.createVerticalStrut(8));

        JPanel medio = new JPanel(new GridBagLayout());
        medio.setAlignmentX(0f);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH; gc.gridy = 0; gc.insets = new Insets(0, 0, 0, 12);
        JPanel izq = new JPanel(new BorderLayout(0, 4));
        stTituloTabla = tituloSeccion(t("Winrate por civilización", "Win rate by civilization"), t("Porcentaje de partidas ganadas por cada civ con los filtros elegidos. La barra sale del 50 % hacia la derecha (gana más de lo que pierde) o hacia la izquierda; la banda gris es el intervalo de confianza al 95 %: si no cruza el 50 %, la diferencia es real. Pick: cuánto se elige esa civ. Clic en una fila para verla en tendencias y matchups.",
                "Share of games won by each civ with the chosen filters. The bar grows from 50% to the right (wins more than it loses) or to the left; the grey band is the 95% confidence interval: if it doesn't cross 50%, the difference is real. Pick: how often that civ is chosen. Click a row to highlight it in trends and matchups."));
        izq.add(stTituloTabla, BorderLayout.NORTH);
        stModelo = new DefaultTableModel(new Object[]{ t("Civ", "Civ"), "WR", t("Pick", "Pick"), t("Partidas", "Games"), t("Duración", "Length") }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? String.class : c == 3 ? Integer.class : c == 4 ? String.class : Double.class; }
        };
        stTabla = new JTable(stModelo);
        stTabla.setRowHeight(22);
        stTabla.setAutoCreateColumnsFromModel(true);
        stSorter = new TableRowSorter<>(stModelo);
        stTabla.setRowSorter(stSorter);
        stSorter.setSortKeys(List.of(new RowSorter.SortKey(1, SortOrder.DESCENDING)));
        stTabla.getColumnModel().getColumn(0).setPreferredWidth(150);
        stTabla.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel lab = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, col);
                int m = tb.convertRowIndexToModel(row);
                Object k = stTabla.getClientProperty("civ" + m);
                lab.setIcon(k == null ? null : iconoCiv(String.valueOf(k), 18));
                lab.setIconTextGap(6);
                Object n = stModelo.getValueAt(m, 3);
                boolean poca = n instanceof Integer i && i < MUESTRA_FIABLE;
                lab.setForeground(sel ? lab.getForeground() : poca ? Color.GRAY : UIManager.getColor("Table.foreground"));
                lab.setToolTipText(poca ? t("Menos de 100 partidas: orientativo", "Under 100 games: indicative only") : null);
                return lab;
            }
        });
        stTabla.getColumnModel().getColumn(1).setPreferredWidth(240);
        stTabla.getColumnModel().getColumn(1).setCellRenderer(new BarraWrRenderer());
        stTabla.getColumnModel().getColumn(2).setCellRenderer(new PctRenderer());
        stTabla.getColumnModel().getColumn(3).setCellRenderer(new MilesRenderer());
        stTabla.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        stTabla.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || stRepintandoTabla) return;   // F3: vaciar la tabla al repintar no es «deseleccionar»
            int r = stTabla.getSelectedRow();
            filtroStats.civSeleccionada(r < 0 ? null : (String) stTabla.getClientProperty("civ" + stTabla.convertRowIndexToModel(r)));
            stCivsSeleccionadas.clear();
            for (int vr : stTabla.getSelectedRows()) { Object k = stTabla.getClientProperty("civ" + stTabla.convertRowIndexToModel(vr)); if (k != null) stCivsSeleccionadas.add(String.valueOf(k)); }
            if (!stCivsSeleccionadas.isEmpty() && stMasJugadasCheck != null && stMasJugadasCheck.isSelected()) stMasJugadasCheck.setSelected(false);   // seleccionas → solo las tuyas
            if (stTendencias != null) stTendencias.repaint();
            if (stMatriz != null) stMatriz.repaint();
        });
        stTabla.addMouseListener(new MouseAdapter() {   // doble clic o clic derecho en una civ → su tech tree
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                int vr = stTabla.rowAtPoint(e.getPoint());
                if (vr < 0) return;
                Object k = stTabla.getClientProperty("civ" + stTabla.convertRowIndexToModel(vr));
                if (k != null) navegacion.abrirTechTree(String.valueOf(k));
            }
            @Override public void mousePressed(MouseEvent e) { menu(e); }
            @Override public void mouseReleased(MouseEvent e) { menu(e); }
            void menu(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int vr = stTabla.rowAtPoint(e.getPoint());
                if (vr < 0) return;
                Object k = stTabla.getClientProperty("civ" + stTabla.convertRowIndexToModel(vr));
                if (k == null) return;
                JPopupMenu pm = new JPopupMenu();
                JMenuItem tt = new JMenuItem(t("Ver en el tech tree: ", "See in the tech tree: ") + nombreCivStats(String.valueOf(k)));
                tt.addActionListener(a -> navegacion.abrirTechTree(String.valueOf(k)));
                pm.add(tt);
                pm.show(stTabla, e.getX(), e.getY());
            }
        });
        stTabla.getTableHeader().setToolTipText(t("Doble clic o clic derecho en una civ: su tech tree. Clic: seleccionarla para tendencias y matchups (Ctrl para varias).", "Double-click or right-click a civ: its tech tree. Click: select it for trends and matchups (Ctrl for several)."));
        JScrollPane sp = new JScrollPane(stTabla);
        sp.setPreferredSize(new Dimension(10, 470));
        izq.add(sp, BorderLayout.CENTER);
        izq.setPreferredSize(new Dimension(10, 500)); izq.setMinimumSize(new Dimension(10, 100));   // ancho base 10 en los cuatro bloques: solo el reparto 0,6/0,4 decide, en cualquier pantalla
        gc.gridx = 0; gc.weightx = 0.6; gc.weighty = 1;
        medio.add(izq, gc);
        PanelScrollable der = new PanelScrollable();
        stMasJugadas = new JPanel(); stMasJugadas.setLayout(new BoxLayout(stMasJugadas, BoxLayout.Y_AXIS)); stMasJugadas.setAlignmentX(0f);
        stMejorPorMapa = new JPanel(); stMejorPorMapa.setLayout(new BoxLayout(stMejorPorMapa, BoxLayout.Y_AXIS)); stMejorPorMapa.setAlignmentX(0f);
        stPool = new PanelScrollable();
        for (JPanel p : new JPanel[]{ stMasJugadas, stMejorPorMapa }) { der.add(p); der.add(Box.createVerticalStrut(6)); }
        gc.gridx = 1; gc.weightx = 0.40; gc.insets = new Insets(0, 0, 0, 0);
        der.setMinimumSize(new Dimension(10, 100)); der.setPreferredSize(new Dimension(10, 100));   // sin scroll propio: la fila crece con el contenido y todo queda a la vista
        medio.add(der, gc);
        stFilaMedio = medio;
        cuerpo.add(medio);
        cuerpo.add(Box.createVerticalStrut(10));

        stTendencias = new TendenciasPanel();
        JPanel filaTend = new JPanel(new GridBagLayout());
        filaTend.setAlignmentX(0f);
        filaTend.setPreferredSize(new Dimension(10, 360));
        GridBagConstraints gt = new GridBagConstraints();
        gt.fill = GridBagConstraints.BOTH; gt.gridy = 0; gt.weighty = 1;
        JPanel tendCaja = new JPanel(new BorderLayout(0, 2));
        tendCaja.setPreferredSize(new Dimension(10, 360)); tendCaja.setMinimumSize(new Dimension(10, 100));
        JPanel tendBarra = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 1));
        stMasJugadasCheck = new JCheckBox(t("Las 5 más jugadas", "5 most played"), true);
        stMasJugadasCheck.setFocusable(false);
        stMasJugadasCheck.setToolTipText(t("Marcado: las cinco civs más jugadas. Sin marcar: solo las civs que selecciones en la tabla (Ctrl o Shift para varias).", "Ticked: the five most played civs. Unticked: only the civs you select in the table (Ctrl or Shift for several)."));
        stMasJugadasCheck.addActionListener(e -> stTendencias.repaint());
        tendBarra.add(stMasJugadasCheck);
        JButton vaciar = new JButton(t("Vaciar", "Clear"));
        vaciar.setFocusable(false); vaciar.setMargin(new Insets(0, 6, 0, 6)); vaciar.putClientProperty("JButton.buttonType", "roundRect");
        vaciar.addActionListener(e -> { stTabla.clearSelection(); stCivsSeleccionadas.clear(); stMasJugadasCheck.setSelected(false); stTendencias.repaint(); });
        tendBarra.add(vaciar);
        stTendMapa = new JComboBox<>();
        stTendMapa.setToolTipText(t("Mapa de la tendencia: los 12 más jugados de cada modo (el resto no tiene serie por mapa)", "Map for the trend: the 12 most played of each mode (others have no per-map series)"));
        stTendMapa.addActionListener(e -> { if (stRellenandoTend) return; Object cl = stTendMapa.getClientProperty("claves"); if (cl instanceof List<?> l && stTendMapa.getSelectedIndex() >= 0 && stTendMapa.getSelectedIndex() < l.size()) { tendMapa = String.valueOf(l.get(stTendMapa.getSelectedIndex())); stTendencias.repaint(); } });
        tendBarra.add(new JLabel(t("Mapa:", "Map:"))); tendBarra.add(stTendMapa);
        tendRango = new SelectorRangoElo(ventana, NombresStats::tramoNombre);
        tendRango.alCambiar = r -> { if (stRellenandoTend) return; tendTramo = r; totalesTendencia.clear(); stTendencias.repaint(); };
        tendBarra.add(tendRango);
        stTendWr = new JToggleButton(t("Winrate", "Win rate"), true);
        stTendPick = new JToggleButton(t("Pick rate", "Pick rate"), false);
        ButtonGroup gTend = new ButtonGroup(); gTend.add(stTendWr); gTend.add(stTendPick);
        for (JToggleButton tb : new JToggleButton[]{ stTendWr, stTendPick }) { tb.setFocusable(false); tb.setMargin(new Insets(0, 8, 0, 8)); tb.putClientProperty("JButton.buttonType", "roundRect"); tb.addActionListener(e -> stTendencias.repaint()); }
        stTendWr.setToolTipText(t("La gráfica muestra el winrate de cada civ", "The chart shows each civ's win rate"));
        stTendPick.setToolTipText(t("La gráfica muestra cuánto se juega cada civ (pick rate)", "The chart shows how much each civ is played (pick rate)"));
        JPanel conmut = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); conmut.setOpaque(false); conmut.add(stTendWr); conmut.add(stTendPick);
        tendBarra.add(conmut);
        stTendVentana = new JComboBox<>(new String[]{ t("3 meses", "3 months"), t("6 meses", "6 months"), t("12 meses", "12 months"), t("Parche actual", "Current patch") });
        stTendVentana.setSelectedIndex(Math.max(0, Math.min(3, Integer.parseInt(leerConfig("stats_tend_ventana", "2")))));
        stTendVentana.addActionListener(e -> { guardarConfig("stats_tend_ventana", String.valueOf(stTendVentana.getSelectedIndex())); if (stTendVentana.getSelectedIndex() == 3) presentador.cargarParcheSiHaceFalta(); else stTendencias.repaint(); });
        tendBarra.add(stTendVentana);
        tendCaja.add(tendBarra, BorderLayout.NORTH);
        tendCaja.add(stTendencias, BorderLayout.CENTER);
        gt.gridx = 0; gt.weightx = 0.6; gt.insets = new Insets(0, 0, 0, 12);
        filaTend.add(tendCaja, gt);
        stPool.setPreferredSize(new Dimension(10, 100)); stPool.setMinimumSize(new Dimension(10, 100));
        gt.gridx = 1; gt.weightx = 0.4; gt.insets = new Insets(0, 0, 0, 0);
        filaTend.add(stPool, gt);
        stFilaTend = filaTend;
        cuerpo.add(filaTend);
        cuerpo.add(Box.createVerticalStrut(10));
        stTituloMatriz = tituloSeccion(tituloMatriz("*", "*"),
                t("Solo en modos 1v1. Cada celda es el winrate de la civ de la fila cuando se enfrenta a la de la columna: verde si gana, rojo si pierde; más intenso cuanto más lejos del 50 %. Gris: menos de 20 partidas. Pasa el ratón para ver la cifra y clica el nombre de una fila para seleccionar esa civ. Con un mapa elegido, la matriz es de ese mapa; si no hay matchups de ese mapa (datos antiguos o pocas partidas), es de todos los mapas y el título lo dice.",
                "1v1 modes only. Each cell is the row civ's win rate when facing the column civ: green if it wins, red if it loses; stronger the further from 50%. Grey: fewer than 20 games. Hover for the figure and click a row name to select that civ. With a map chosen, the matrix is for that map; if there are no matchups for that map (old data or few games), it is for all maps and the title says so."));
        stTituloMatriz.setAlignmentX(0f);
        JPanel filaMatriz = new JPanel(new BorderLayout(8, 0));
        filaMatriz.setAlignmentX(0f); filaMatriz.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        filaMatriz.add(stTituloMatriz, BorderLayout.WEST);
        JPanel botonesMatriz = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (String[] z : new String[][]{ { "\u2212", "menos" }, { "+", "mas" }, { "\u26F6 " + t("Ampliar", "Enlarge"), "grande" } }) {
            JButton zb = new JButton(z[0]);
            zb.setFocusable(false); zb.setMargin(new Insets(0, 6, 0, 6)); zb.putClientProperty("JButton.buttonType", "roundRect");
            zb.setToolTipText(z[1].equals("grande") ? t("La matriz en una ventana a pantalla completa (Esc para cerrar)", "The matrix in a full-screen window (Esc to close)") : t("Tamaño de las celdas (también Ctrl + rueda sobre la matriz)", "Cell size (also Ctrl + wheel over the matrix)"));
            zb.addActionListener(e -> { switch (z[1]) { case "menos" -> stMatriz.setCelda(stMatriz.celda - 2); case "mas" -> stMatriz.setCelda(stMatriz.celda + 2); default -> mostrarMatrizGrande(); } });
            botonesMatriz.add(zb);
        }
        filaMatriz.add(botonesMatriz, BorderLayout.CENTER);
        cuerpo.add(filaMatriz);
        stMatriz = new MatrizPanel();
        stMatriz.setAlignmentX(0f);
        cuerpo.add(stMatriz);

        JScrollPane scroll = new JScrollPane(cuerpo, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        civStatsPanel.add(scroll, BorderLayout.CENTER);
        JLabel pie = new JLabel(t("Datos: volcados diarios de aoe2companion.com (Dennis Keil), resumidos por sfr-data cada noche · Age of Empires II © Microsoft. Fuera espejos, partidas de menos de 2 minutos y sin resultado; el tramo de ELO es la media de la partida; las bandas son intervalos de Wilson al 95 %.",
                "Data: aoe2companion.com daily dumps (Dennis Keil), summarized nightly by sfr-data · Age of Empires II © Microsoft. Mirrors, games under 2 minutes and unfinished games excluded; the ELO bracket is the match average; bands are 95% Wilson intervals."));
        pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f));
        civStatsPanel.add(pie, BorderLayout.SOUTH);
        return civStatsPanel;
    }

    /** Fila «Arabia · [icono] Romanos · 55,1 %» para «Mejor civ por mapa». */
    JPanel filaMejorCiv(String mapa, String claveMapa, CivAgg mejor, int partidasMapa) {
        JPanel f = new JPanel(new BorderLayout(6, 0));
        f.setOpaque(false); f.setAlignmentX(0f);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22)); f.setPreferredSize(new Dimension(10, 22));
        JLabel m = new JLabel(mapa, iconoMapa(mapa, claveMapa, 18), SwingConstants.LEFT); m.setIconTextGap(5); m.setPreferredSize(new Dimension(126, 20));
        JLabel c = new JLabel("<html>" + escapeHtml(nombreCivStats(mejor.civ())) + " <span style='color:gray;font-size:9px'>" + miles(mejor.n()) + "</span></html>", iconoCiv(mejor.civ(), 18), SwingConstants.LEFT); c.setIconTextGap(5);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) navegacion.abrirTechTree(mejor.civ()); } });
        JLabel v = new JLabel(pct1(mejor.wr()), SwingConstants.RIGHT); v.setPreferredSize(new Dimension(58, 18)); v.setFont(v.getFont().deriveFont(Font.BOLD)); v.setForeground(colorWr(mejor.w(), mejor.n()));
        f.add(m, BorderLayout.WEST); f.add(c, BorderLayout.CENTER); f.add(v, BorderLayout.EAST);
        String tip = mapa + ": " + miles(partidasMapa) + t(" partidas · ", " games · ") + nombreCivStats(mejor.civ()) + " " + pct1(mejor.wr()) + " (" + miles(mejor.n()) + t(" partidas)", " games)");
        for (JComponent x : new JComponent[]{ f, m, c, v }) x.setToolTipText(tip);
        return f;
    }

    class BarraWrRenderer extends DefaultTableCellRenderer {
        double wr; int n, w;
        @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, "", sel, foc, row, col);
            int m = t.convertRowIndexToModel(row);
            Object oN = stModelo.getValueAt(m, 3);
            n = oN instanceof Integer i ? i : 0;
            wr = value instanceof Double d ? d : 0;
            w = (int) Math.round(wr * n / 100.0);
            return this;
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight(), wd = getWidth(), tx = 52, x0 = tx + 4, x1 = wd - 6;
            double lo = 42, hi = 58;
            java.util.function.DoubleUnaryOperator px = v -> x0 + (x1 - x0) * (Math.max(lo, Math.min(hi, v)) - lo) / (hi - lo);
            double[] iv = stats.wilson(w, n);
            g2.setColor(new Color(128, 128, 128, 45));
            g2.fillRoundRect((int) px.applyAsDouble(iv[0]), h / 2 - 5, Math.max(2, (int) (px.applyAsDouble(iv[1]) - px.applyAsDouble(iv[0]))), 10, 4, 4);
            Color c = colorWr(w, n);
            g2.setColor(c);
            int xm = (int) px.applyAsDouble(50), xv = (int) px.applyAsDouble(wr);
            g2.fillRect(Math.min(xm, xv), h / 2 - 3, Math.max(2, Math.abs(xv - xm)), 6);
            g2.setColor(new Color(128, 128, 128, 120));
            g2.drawLine(xm, 3, xm, h - 3);
            g2.setColor(c);
            g2.setFont(getFont().deriveFont(Font.BOLD));
            String s = pct1(wr);
            g2.drawString(s, tx - g2.getFontMetrics().stringWidth(s), h / 2 + 4);
            g2.dispose();
        }
    }
    static class MilesRenderer extends DefaultTableCellRenderer {
        MilesRenderer() { setHorizontalAlignment(RIGHT); }
        @Override protected void setValue(Object v) { setText(v instanceof Integer i ? miles(i) : ""); }
    }

    /** Todos los scrolls arriba del todo (al abrir la pestaña); parte de vista de abrirCivStats. */
    public void subirArriba() { Componentes.subirArriba(civStatsPanel); }

    /** La parte de vista de abrirCivStats: pide el resumen si hace falta (la ventana ya se sabe por filtroStats). */
    public void alAbrir() { presentador.cargar(filtroStats.ventana()); }

    /** Rellena mapas y tramos disponibles para el modo actual y repinta todo el cuadro de mando. */
    public void filtrosCambiados(boolean repintarTechTree) {
        VentanaStats v = stats.ventana(filtroStats.ventana());
        if (v == null || civStatsPanel == null) return;
        stRellenandoMapas = true;
        try {
            Map<String, Integer> mapas = stats.partidasPorMapa(v, filtroStats.modo(), filtroStats.tramo());   // el contador es el del tramo elegido
            List<Map.Entry<String, Integer>> lm = new ArrayList<>(mapas.entrySet());
            lm.sort((a, b) -> b.getValue() - a.getValue());
            List<String> claves = new ArrayList<>(); claves.add("*");
            stMapaCombo.removeAllItems();
            stMapaCombo.addItem(t("Todos los mapas", "All maps"));
            for (Map.Entry<String, Integer> en : lm) { if (en.getValue() < MIN_PARTIDAS_CIV) continue; claves.add(en.getKey()); stMapaCombo.addItem(nombreMapaStats(v, en.getKey()) + " (" + miles(en.getValue()) + ")"); }
            stMapaCombo.putClientProperty("claves", claves);
            if (!claves.contains(filtroStats.mapa())) { filtroStats.mapa("*"); guardarConfig("stats_mapa", "*"); }
            if (stTendMapa != null) {   // la fila de tendencias tiene sus propios mapa y ELO
                stRellenandoTend = true;
                try {
                    List<String> clTM = new ArrayList<>(); clTM.add("*");
                    stTendMapa.removeAllItems(); stTendMapa.addItem(t("Todos", "All"));
                    List<Map.Entry<String, Integer>> lmT = new ArrayList<>(v.mapasPorModo().getOrDefault(filtroStats.modo(), Map.of()).entrySet());
                    lmT.sort((a, b) -> b.getValue() - a.getValue());
                    for (Map.Entry<String, Integer> en : lmT.subList(0, Math.min(12, lmT.size()))) { clTM.add(en.getKey()); stTendMapa.addItem(nombreMapaStats(v, en.getKey())); }
                    stTendMapa.putClientProperty("claves", clTM);
                    if (!clTM.contains(tendMapa)) tendMapa = "*";
                    stTendMapa.setSelectedIndex(clTM.indexOf(tendMapa));
                    tendRango.tramos(v.tramos(), tendTramo);
                    tendTramo = tendRango.rango();
                    totalesTendencia.clear();
                } finally { stRellenandoTend = false; }
            }
            stMapaCombo.setSelectedIndex(claves.indexOf(filtroStats.mapa()));
            stRango.tramos(v.tramos(), filtroStats.tramo());
            filtroStats.tramo(stRango.rango());
        } finally { stRellenandoMapas = false; }
        statsPintar(v);
        if (repintarTechTree) enlace.repintarBandaTechTree();
    }

    /** Igual que filtrosCambiados, pero sin disparar sus propios listeners: lo usa el tech tree al cambiar la ventana compartida. */
    public void ponerVentanaSinDisparar(String ventana) {
        stRellenandoMapas = true;
        try { stVentanaCombo.setSelectedIndex(Math.max(0, Arrays.asList(stats.clavesVentanas()).indexOf(ventana))); }
        finally { stRellenandoMapas = false; }
    }

    /** F3 con varias civs (Ctrl, 1.4): tras repintar la tabla se restauraba solo la primera y las demás se perdían de
     *  tendencias y matriz. La primera ya está elegida (como antes, por el oyente); aquí se añaden las otras que sigan
     *  en la tabla, sin avisar al oyente, y se rehace stCivsSeleccionadas como lo haría él. La civ del filtro no cambia. */
    private void restaurarOtrasCivs(List<String> previas) {
        stRepintandoTabla = true;
        try {
            for (int r = 0; r < stModelo.getRowCount(); r++) {
                Object k = stTabla.getClientProperty("civ" + r);
                if (k != null && previas.contains(String.valueOf(k))) { int vr = stTabla.convertRowIndexToView(r); stTabla.addRowSelectionInterval(vr, vr); }
            }
        } finally { stRepintandoTabla = false; }
        stCivsSeleccionadas.clear();
        for (int vr : stTabla.getSelectedRows()) { Object k = stTabla.getClientProperty("civ" + stTabla.convertRowIndexToModel(vr)); if (k != null) stCivsSeleccionadas.add(String.valueOf(k)); }
        if (stTendencias != null) stTendencias.repaint();
        if (stMatriz != null) stMatriz.repaint();
    }

    void statsPintar(VentanaStats v) {
        Map<String, CivAgg> agg = stats.agregarCivs(v, filtroStats.modo(), filtroStats.mapa(), filtroStats.tramo());
        long totalN = 0, totalD = 0; int totalW = 0;
        for (CivAgg a : agg.values()) { totalN += a.n(); totalD += a.d(); totalW += a.w(); }
        Map<String, Integer> modo = v.modos().getOrDefault(filtroStats.modo(), Map.of());
        int jugadoresPorPartida = filtroStats.modo().endsWith("_1v1") ? 2 : filtroStats.modo().endsWith("_2v2") ? 4 : filtroStats.modo().endsWith("_3v3") ? 6 : filtroStats.modo().endsWith("_4v4") ? 8 : 6;
        long partidas = "*".equals(filtroStats.mapa()) && ("*".equals(filtroStats.tramo()) || "*|*".equals(filtroStats.tramo())) ? modo.getOrDefault("partidas", 0) : totalN / jugadoresPorPartida;
        int abandonos = modo.getOrDefault("abandonos", 0), espejos = modo.getOrDefault("espejos", 0), sinRes = modo.getOrDefault("sin_resultado", 0);
        int brutas = modo.getOrDefault("partidas", 0) + abandonos + espejos + sinRes;
        String ambito = ventanaNombre(filtroStats.ventana()) + " · " + v.desde() + " → " + v.hasta() + (v.parche().isEmpty() ? "" : " · " + t("parche ", "patch ") + v.parche());
        stTarjetas.removeAll();
        stTarjetas.add(listas.tarjeta(t("Partidas", "Games"), miles(partidas), ambito));
        stTarjetas.add(listas.tarjeta(t("Duración media", "Avg. length"), stats.duracionMedia(totalD, (int) Math.min(Integer.MAX_VALUE, totalN)), t("tiempo de juego, sin abandonos", "in-game time, dodges excluded")));
        stTarjetas.add(listas.tarjeta(t("Civs con datos", "Civs with data"), String.valueOf(agg.values().stream().filter(a -> a.n() >= MIN_PARTIDAS_CIV).count()), t("con ", "with ") + MIN_PARTIDAS_CIV + t("+ partidas (gris: menos de 100)", "+ games (grey: under 100)")));
        stTarjetas.add(listas.tarjeta(t("Espejos", "Mirrors"), brutas == 0 ? "-" : pct1(100.0 * espejos / brutas), t("misma civ en ambos lados (fuera)", "same civ on both sides (excluded)")));
        stTarjetas.add(listas.tarjeta(t("Abandonos", "Dodges"), brutas == 0 ? "-" : pct1(100.0 * abandonos / brutas), t("menos de 2 minutos (fuera)", "under 2 minutes (excluded)")));
        stTarjetas.revalidate(); stTarjetas.repaint();

        List<CivAgg> lista = new ArrayList<>(agg.values());
        lista.removeIf(a -> a.n() < MIN_PARTIDAS_CIV);
        stRepintandoTabla = true;   // F3: setRowCount(0) quita la selección y avisaría con fila -1: la civ elegida se perdería
        try {
            stModelo.setRowCount(0);
            int i = 0;
            for (CivAgg a : lista) {
                stModelo.addRow(new Object[]{ nombreCivStats(a.civ()), a.wr(), totalN == 0 ? 0.0 : 100.0 * a.n() / totalN, a.n(), stats.duracionMedia(a.d(), a.n()) });
                stTabla.putClientProperty("civ" + i, a.civ());
                i++;
            }
        } finally { stRepintandoTabla = false; }
        stTituloTabla.setText(t("Winrate por civilización", "Win rate by civilization") + "  ·  " + modoNombre(filtroStats.modo()) + " · " + nombreMapaStats(v, filtroStats.mapa()) + " · " + tramoNombre(filtroStats.tramo()));
        List<String> previas = new ArrayList<>(stCivsSeleccionadas);   // antes de que el oyente las rehaga con la primera
        boolean esta = false;
        if (filtroStats.civSeleccionada() != null) {
            for (int r = 0; r < stModelo.getRowCount(); r++) if (filtroStats.civSeleccionada().equals(stTabla.getClientProperty("civ" + r))) { int vr = stTabla.convertRowIndexToView(r); stTabla.setRowSelectionInterval(vr, vr); esta = true; break; }
            if (!esta) filtroStats.civSeleccionada(null);   // con estos filtros ya no sale en la tabla: como antes, sin civ elegida
            else if (previas.size() > 1) restaurarOtrasCivs(previas);
        }
        if (!esta) stCivsSeleccionadas.clear();   // B1: ninguna fila restaurada (tampoco tras el cambio de modo, que borra la civ antes): sin civs en tendencias, como hacía el aviso de fila -1
        Color barra = temaOscuroActivo ? new Color(0x5a, 0x8f, 0xc7) : new Color(0x3b, 0x6e, 0xa8);
        stMasJugadas.removeAll();
        stMasJugadas.add(tituloSeccion(t("Más jugadas", "Most played"), t("Las civs más elegidas con estos filtros: porcentaje de todas las partidas en las que aparece cada una (pick rate).", "The most chosen civs with these filters: share of all games in which each one appears (pick rate).")));
        List<CivAgg> porPick = new ArrayList<>(lista); porPick.sort((a, b) -> b.n() - a.n());
        double maxPick = porPick.isEmpty() ? 1 : porPick.get(0).n();
        for (CivAgg a : porPick.subList(0, Math.min(8, porPick.size())))
            stMasJugadas.add(listas.filaBarra(iconoCiv(a.civ(), 18), nombreCivStats(a.civ()), a.n() / maxPick, pct1(totalN == 0 ? 0 : 100.0 * a.n() / totalN), barra, miles(a.n()) + t(" partidas · WR ", " games · WR ") + pct1(a.wr()) + t(" · clic: tech tree", " · click: tech tree"), () -> navegacion.abrirTechTree(a.civ())));
        if (porPick.size() > 8) { long totalNF = totalN; stMasJugadas.add(listas.enlaceVerTodo(porPick.size(), () -> {
            List<Object[]> filas = new ArrayList<>();
            for (CivAgg a : porPick) filas.add(new Object[]{ new Celda(iconoCiv(a.civ(), 18), nombreCivStats(a.civ()), () -> navegacion.abrirTechTree(a.civ())), new Pct(totalNF == 0 ? 0 : 100.0 * a.n() / totalNF, 0, 0, false), (long) a.n(), new Pct(a.wr(), a.w(), a.n(), true) });
            listas.mostrarTablaCompleta(t("Más jugadas", "Most played") + " · " + modoNombre(filtroStats.modo()), new String[]{ t("Civ", "Civ"), "Pick", t("Partidas", "Games"), "WR" }, filas, 1);
        })); }
        stMejorPorMapa.removeAll();
        stMejorPorMapa.add(tituloSeccion(t("Mejor civ por mapa", "Best civ per map"), t("En cada mapa del pool, la civ con mejor winrate con el modo y el tramo elegidos: mínimo 20 partidas de esa civ en ese mapa, y gana la que tiene mejor límite inferior del intervalo de confianza (no la muestra pequeña con suerte). El número gris son sus partidas: por debajo de 100, orientativo. Responde a «qué me cojo en este mapa».", "For each map in the pool, the civ with the best win rate with the chosen mode and bracket: at least 20 games of that civ on that map, and the winner is the best lower bound of the confidence interval (not a lucky small sample). The grey number is its games: under 100, indicative only. Answers \u201Cwhat should I pick on this map\u201D.")));
        Map<String, Integer> mapas = stats.partidasPorMapa(v, filtroStats.modo(), filtroStats.tramo());   // con tramo elegido, solo las partidas de ese tramo
        List<Map.Entry<String, Integer>> lm = new ArrayList<>(mapas.entrySet());
        lm.sort((a, b) -> b.getValue() - a.getValue());
        int filasMejor = 0;
        List<Object[]> todasMejor = new ArrayList<>();   // {clave mapa, mejor, partidas del mapa}
        for (Map.Entry<String, Integer> en : lm) {
            if (en.getValue() < MIN_PARTIDAS_CIV) break;
            CivAgg mejor = null; double mejorLb = -1;
            for (CivAgg a : stats.agregarCivs(v, filtroStats.modo(), en.getKey(), filtroStats.tramo()).values()) {   // 20+ partidas; gana el límite inferior de Wilson (no la muestra pequeña con suerte)
                if (a.n() < MIN_PARTIDAS_CIV) continue;
                double lb = stats.wilson(a.w(), a.n())[0];
                if (lb > mejorLb) { mejorLb = lb; mejor = a; }
            }
            if (mejor == null) continue;
            todasMejor.add(new Object[]{ en.getKey(), mejor, en.getValue() });
            if (filasMejor < 12) { stMejorPorMapa.add(filaMejorCiv(nombreMapaStats(v, en.getKey()), en.getKey(), mejor, en.getValue())); filasMejor++; }
        }
        if (todasMejor.size() > 12) stMejorPorMapa.add(listas.enlaceVerTodo(todasMejor.size(), () -> {
            List<Object[]> filas = new ArrayList<>();
            for (Object[] x : todasMejor) { CivAgg mejor = (CivAgg) x[1]; filas.add(new Object[]{ new Celda(iconoMapa(nombreMapaStats(v, (String) x[0]), (String) x[0], 18), nombreMapaStats(v, (String) x[0]), null), new Celda(iconoCiv(mejor.civ(), 18), nombreCivStats(mejor.civ()), () -> navegacion.abrirTechTree(mejor.civ())), (long) mejor.n(), new Pct(mejor.wr(), mejor.w(), mejor.n(), true), (long) (Integer) x[2] }); }
            listas.mostrarTablaCompleta(t("Mejor civ por mapa", "Best civ per map") + " · " + modoNombre(filtroStats.modo()) + " · " + tramoNombre(filtroStats.tramo()), new String[]{ t("Mapa", "Map"), t("Mejor civ", "Best civ"), t("Partidas civ", "Civ games"), "WR", t("Partidas mapa", "Map games") }, filas, 4);
        }));
        if (filasMejor == 0) { JLabel vac = new JLabel(t("Sin mapas con 20+ partidas por civ.", "No maps with 20+ games per civ.")); vac.setForeground(Color.GRAY); vac.setFont(vac.getFont().deriveFont(Font.PLAIN, 11f)); stMejorPorMapa.add(vac); }
        stPool.removeAll();
        stPool.add(tituloSeccion(t("Pool de mapas", "Map pool"), t("Cuánto se juega cada mapa en este modo (porcentaje de partidas). Con un tramo de ELO elegido, la cuota es la de ese tramo.", "How much each map is played in this mode (share of games). With an ELO bracket chosen, the share is that bracket's.")));
        long totalMapas = 0; for (Map.Entry<String, Integer> en : lm) totalMapas += en.getValue();
        double maxMapa = lm.isEmpty() ? 1 : lm.get(0).getValue();
        Color oliva = temaOscuroActivo ? new Color(0xa3, 0xb8, 0x6c) : new Color(0x5d, 0x6b, 0x1e);
        long mostradas = 0;
        for (Map.Entry<String, Integer> en : lm.subList(0, Math.min(12, lm.size()))) {
            mostradas += en.getValue();
            stPool.add(listas.filaBarra(iconoMapa(nombreMapaStats(v, en.getKey()), en.getKey(), 18), nombreMapaStats(v, en.getKey()), en.getValue() / maxMapa, pct1(totalMapas == 0 ? 0 : 100.0 * en.getValue() / totalMapas), oliva, miles(en.getValue()) + t(" partidas", " games"), null));
        }
        if (lm.size() > 12 && totalMapas > mostradas) {
            stPool.add(listas.filaBarra(null, t("Otros (", "Others (") + (lm.size() - 12) + t(" mapas)", " maps)"), (totalMapas - mostradas) / maxMapa, pct1(100.0 * (totalMapas - mostradas) / totalMapas), new Color(128, 128, 128, 120), miles(totalMapas - mostradas) + t(" partidas en el resto del pool", " games in the rest of the pool"), null));
            long totalMapasF = totalMapas; double maxMapaF = maxMapa;
            stPool.add(listas.enlaceVerTodo(lm.size(), () -> {
                List<Object[]> filas = new ArrayList<>();
                for (Map.Entry<String, Integer> en : lm) filas.add(new Object[]{ new Celda(iconoMapa(nombreMapaStats(v, en.getKey()), en.getKey(), 18), nombreMapaStats(v, en.getKey()), null), (long) en.getValue(), new Pct(totalMapasF == 0 ? 0 : 100.0 * en.getValue() / totalMapasF, 0, 0, false) });
                listas.mostrarTablaCompleta(t("Pool de mapas", "Map pool") + " · " + modoNombre(filtroStats.modo()) + " · " + tramoNombre(filtroStats.tramo()), new String[]{ t("Mapa", "Map"), t("Partidas", "Games"), "%" }, filas, 1);
            }));
        }
        for (JPanel p : new JPanel[]{ stMasJugadas, stMejorPorMapa, stPool }) { p.revalidate(); p.repaint(); }
        ajustarFilasCivStats();
        stTendencias.datos(porPick.subList(0, Math.min(5, porPick.size())));
        boolean unoContraUno = filtroStats.modo().endsWith("_1v1");
        stTituloMatriz.setVisible(unoContraUno); stMatriz.setVisible(unoContraUno);
        if (unoContraUno) stMatriz.datos(v, lista);
        stTituloMatriz.setText(tituloMatriz(filtroStats.mapa(), unoContraUno ? stMatriz.mapaUsado : "*") + "  \u24D8");   // D2: si el mapa elegido no tiene matchups propios, avisa de que la matriz es de todos
        stEstado.setText(t("Resumen del ", "Summary of ") + v.hasta() + (partidas < POCAS_PARTIDAS ? "  ·  " + t("pocas partidas con estos filtros: prueba 90 o 365 días", "few games with these filters: try 90 or 365 days") : ""));
        civStatsPanel.revalidate(); civStatsPanel.repaint();
    }

    /**
     * D2 (1.3, decisión de Jorge: la matriz filtra por mapa). sfr-data publica «matchups» (todos los mapas, mapa
     * "*") y, desde la 1.5.4, «matchups_mapa» (por mapa, solo 1v1 y mapas con MIN_PARTIDAS_CIV partidas). La matriz
     * usa las del mapa elegido si las hay (ver mapaMatriz); si no, el agregado, y entonces el título avisa de que
     * es de todos los mapas. Pura: la prueba CivStatsViewTest.
     */
    static String tituloMatriz(String mapaPedido, String mapaUsado) {
        String base = t("Matchups (civ de la fila contra civ de la columna)", "Matchups (row civ against column civ)");
        return avisoTodosLosMapas(mapaPedido, mapaUsado) ? base + " · " + t("todos los mapas", "all maps") : base;
    }

    /** Hay un mapa elegido pero la matriz es del agregado (no había matchups de ese mapa). */
    static boolean avisoTodosLosMapas(String mapaPedido, String mapaUsado) {
        return mapaPedido != null && !"*".equals(mapaPedido) && "*".equals(mapaUsado);
    }

    /**
     * Qué filas de matchups pinta la matriz: las del mapa pedido si hay alguna de ese modo y tramo (datos de
     * sfr-data 1.5.4 o posterior y mapa con partidas suficientes); si no, "*" (el agregado de todos los mapas).
     */
    static String mapaMatriz(Map<String, List<Matchup>> porMapa, String modo, String mapa, java.util.function.Predicate<String> tramoOk) {
        if (mapa == null || "*".equals(mapa)) return "*";
        for (Matchup mu : porMapa.getOrDefault(mapa, List.of())) if (mu.modo().equals(modo) && tramoOk.test(mu.tramo())) return mapa;
        return "*";
    }

    /** La matriz de matchups en una ventana a pantalla completa, con celdas grandes y scroll. */
    public void mostrarMatrizGrande() {
        if (stMatriz == null || stMatriz.civs.isEmpty()) return;
        JDialog d = new JDialog(ventana, t("Matchups · ", "Matchups · ") + modoNombre(filtroStats.modo()) + " · " + tramoNombre(filtroStats.tramo()) + " · " + ventanaNombre(filtroStats.ventana()) + (avisoTodosLosMapas(filtroStats.mapa(), stMatriz.mapaUsado) ? " · " + t("todos los mapas", "all maps") : "*".equals(stMatriz.mapaUsado) ? "" : " · " + nombreMapaStats(stats.ventana(filtroStats.ventana()), stMatriz.mapaUsado)), JDialog.ModalityType.MODELESS);
        MatrizPanel grande = new MatrizPanel();
        grande.copiarDe(stMatriz);
        Rectangle pantalla = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int disponible = Math.min(pantalla.width - 180, pantalla.height - 200);
        grande.setCelda(Math.max(12, Math.min(30, disponible / Math.max(1, grande.civs.size()))));
        JScrollPane sp = new JScrollPane(grande);
        sp.setBorder(null);
        d.add(sp);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setBounds(pantalla);
        d.setVisible(true);
    }

    /** Prefijos de clave de una fuente de tendencia: uno por tramo del rango (o uno solo si la fuente no lleva tramo). */
    List<String> prefijosTendencia(String fuente, List<String> tramosRango) {
        List<String> out = new ArrayList<>();
        switch (fuente) {
            case "mt" -> { for (String tr : tramosRango) out.add(filtroStats.modo() + "|" + tendMapa + "|" + tr + "|"); }
            case "tramo" -> { for (String tr : tramosRango) out.add(filtroStats.modo() + "|" + tr + "|"); }
            case "mapa" -> out.add(filtroStats.modo() + "|" + tendMapa + "|");
            default -> out.add(filtroStats.modo() + "|");
        }
        return out;
    }
    /** Serie mes → {n,w} de una civ, sumando los tramos del rango cuando la fuente va por tramo. */
    Map<String, int[]> serieTendencia(Tendencias tn, String fuente, String civ, List<String> tramosRango) {
        Map<String, Map<String, int[]>> tabla = switch (fuente) { case "mt" -> tn.filasMT(); case "mapa" -> tn.filasMapa(); case "tramo" -> tn.filasTramo(); default -> tn.filas(); };
        if (tabla == null) return null;
        Map<String, int[]> suma = null;
        for (String prefijo : prefijosTendencia(fuente, tramosRango)) {
            Map<String, int[]> m = tabla.get(prefijo + civ);
            if (m == null) continue;
            if (suma == null) suma = new HashMap<>();
            for (Map.Entry<String, int[]> en : m.entrySet()) { int[] acc = suma.computeIfAbsent(en.getKey(), k -> new int[2]); acc[0] += en.getValue()[0]; acc[1] += en.getValue()[1]; }
        }
        return suma;
    }
    /** Total de «slots» de civ en un mes con el filtro de la tendencia (para el pick rate): suma de n de todas las civs (y de los tramos del rango). */
    final Map<String, Long> totalesTendencia = new HashMap<>();
    long totalTendencia(Tendencias tn, String fuente, String mes) {
        String clave = fuente + "|" + filtroStats.modo() + "|" + tendMapa + "|" + tendTramo + "|" + mes;
        Long c = totalesTendencia.get(clave);
        if (c != null) return c;
        VentanaStats vTr = stats.ventana(filtroStats.ventana());
        List<String> tramosRango = new ArrayList<>();
        if (vTr != null) for (String tr : vTr.tramos()) if (stats.tramoEnRango(tr, vTr.tramos(), tendTramo)) tramosRango.add(tr);
        Map<String, Map<String, int[]>> tabla = switch (fuente) { case "mt" -> tn.filasMT(); case "mapa" -> tn.filasMapa(); case "tramo" -> tn.filasTramo(); default -> tn.filas(); };
        long total = 0;
        for (String prefijo : prefijosTendencia(fuente, tramosRango)) if (tabla != null) for (Map.Entry<String, Map<String, int[]>> en : tabla.entrySet()) {
            if (!en.getKey().startsWith(prefijo) || en.getKey().substring(prefijo.length()).contains("|")) continue;
            int[] nw = en.getValue().get(mes); if (nw != null) total += nw[0];
        }
        totalesTendencia.put(clave, total);
        return total;
    }

    /** Líneas de winrate por mes de las civs más jugadas (y la seleccionada en la tabla). */
    class TendenciasPanel extends JPanel {
        List<CivAgg> civs = List.of();
        TendenciasPanel() { setOpaque(false); }
        void datos(List<CivAgg> c) { civs = c; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), ht = getHeight(), ml = 48, mb = 26, mt = 24, mr = 150;
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            Color gris = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 90 : 170);
            Font base = g2.getFont();
            g2.setFont(base.deriveFont(Font.BOLD, 13f)); g2.setColor(fg);
            Tendencias tn = stats.tendencias();
            int ventSel = stTendVentana == null ? 2 : stTendVentana.getSelectedIndex();
            boolean pick = stTendPick != null && stTendPick.isSelected();
            boolean conMapa = !"*".equals(tendMapa), conTramo = !"*".equals(tendTramo);
            String fuente = conMapa && conTramo ? "mt" : conMapa ? "mapa" : conTramo ? "tramo" : "todo";
            VentanaStats vTr = stats.ventana(filtroStats.ventana());
            List<String> tramosRango = new ArrayList<>();
            if (vTr != null) for (String tr : vTr.tramos()) if (stats.tramoEnRango(tr, vTr.tramos(), tendTramo)) tramosRango.add(tr);
            boolean hayFuente = tn != null && switch (fuente) {
                case "mt" -> tn.filasMT() != null && tramosRango.stream().anyMatch(tr -> tn.filasMT().keySet().stream().anyMatch(k -> k.startsWith(filtroStats.modo() + "|" + tendMapa + "|" + tr + "|")));
                case "mapa" -> tn.filasMapa() != null && tn.filasMapa().keySet().stream().anyMatch(k -> k.startsWith(filtroStats.modo() + "|" + tendMapa + "|"));
                case "tramo" -> tn.filasTramo() != null && tramosRango.stream().anyMatch(tr -> tn.filasTramo().keySet().stream().anyMatch(k -> k.startsWith(filtroStats.modo() + "|" + tr + "|")));
                default -> true;
            };
            VentanaStats vTit = stats.ventana(filtroStats.ventana());
            String titulo = (pick ? t("Tendencia del pick rate por mes", "Monthly pick rate trend") : t("Tendencia del winrate por mes", "Monthly win rate trend")) + "  ·  " + modoNombre(filtroStats.modo())
                    + " · " + (conMapa ? nombreMapaStats(vTit, tendMapa) : t("todos los mapas", "all maps")) + " · " + (conTramo ? tramoNombre(tendTramo) : t("todos los ELO", "all ELO"));
            g2.drawString(titulo, 4, 16);
            if (tn != null && !hayFuente) {
                g2.setFont(base.deriveFont(10f)); g2.setColor(gris);
                g2.drawString(fuente.equals("mt") ? t("Mapa y ELO a la vez: solo en modos 1v1 y para los 12 mapas más jugados (con el motor 1.3.1 publicado en sfr-data).", "Map and ELO together: only in 1v1 modes and for the 12 most played maps (with engine 1.3.1 published in sfr-data).")
                        : t("Sin serie para este filtro todavía: hace falta el motor 1.3.1 publicado en sfr-data.", "No series for this filter yet: engine 1.3.1 must be published in sfr-data."), ml, ht / 2);
                g2.dispose(); return;
            }
            g2.setFont(base.deriveFont(10f));
            if (tn == null || tn.meses().isEmpty()) { g2.setColor(gris); g2.drawString(t("Sin tendencias.", "No trend data."), ml, ht / 2); g2.dispose(); return; }
            List<String> claves = new ArrayList<>();
            List<String> mesesTop = new ArrayList<>(tn.meses());
            { int vSel = stTendVentana == null ? 2 : stTendVentana.getSelectedIndex(); if (vSel == 0 || vSel == 1) { int n = vSel == 0 ? 3 : 6; if (mesesTop.size() > n) mesesTop = new ArrayList<>(mesesTop.subList(mesesTop.size() - n, mesesTop.size())); } }
            if (stMasJugadasCheck == null || stMasJugadasCheck.isSelected()) {   // las 5 más jugadas CON LOS FILTROS DE ESTA FILA (mapa, ELO y periodo), no con los de la tabla
                Map<String, Map<String, int[]>> tabla = switch (fuente) { case "mt" -> tn.filasMT(); case "mapa" -> tn.filasMapa(); case "tramo" -> tn.filasTramo(); default -> tn.filas(); };
                Map<String, Long> porCiv = new HashMap<>();
                for (String prefijo : prefijosTendencia(fuente, tramosRango)) if (tabla != null) for (Map.Entry<String, Map<String, int[]>> en : tabla.entrySet()) {
                    if (!en.getKey().startsWith(prefijo) || en.getKey().substring(prefijo.length()).contains("|")) continue;
                    long n = 0; for (String mm : mesesTop) { int[] nw = en.getValue().get(mm); if (nw != null) n += nw[0]; }
                    if (n > 0) porCiv.merge(en.getKey().substring(prefijo.length()), n, Long::sum);
                }
                porCiv.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).limit(5).forEach(en -> claves.add(en.getKey()));
            }
            for (String c : stCivsSeleccionadas) if (!claves.contains(c)) claves.add(c);
            if (claves.isEmpty()) { g2.setColor(gris); g2.drawString(t("Selecciona civs en la tabla (Ctrl para varias) o marca «Las 5 más jugadas».", "Select civs in the table (Ctrl for several) or tick \u201C5 most played\u201D."), ml, ht / 2); g2.dispose(); return; }
            List<String> meses = new ArrayList<>(tn.meses());
            if (ventSel == 0 || ventSel == 1) { int n = ventSel == 0 ? 3 : 6; if (meses.size() > n) meses = new ArrayList<>(meses.subList(meses.size() - n, meses.size())); }
            else if (ventSel == 3) { VentanaStats vp = stats.ventana("parche"); if (vp != null && vp.desde().length() >= 7) { String desde = vp.desde().substring(0, 7); meses.removeIf(mm -> mm.compareTo(desde) < 0); } }
            if (meses.size() < 2) { g2.setColor(gris); g2.drawString(t("Aún no hay dos meses en esta ventana.", "Not two months in this window yet."), ml, ht / 2); g2.dispose(); return; }
            double lo = 100, hi = 0;
            Map<String, double[]> series = new LinkedHashMap<>();
            for (String c : claves) {
                Map<String, int[]> m = serieTendencia(tn, fuente, c, tramosRango);
                double[] s = new double[meses.size()];
                int minimo = fuente.equals("todo") ? 200 : fuente.equals("mt") ? 30 : 60;   // con más filtro hay menos partidas: umbral más bajo
                for (int i = 0; i < meses.size(); i++) {
                    int[] nw = m == null ? null : m.get(meses.get(i));
                    if (pick) {   // pick rate: partidas de la civ entre todas las de ese filtro y mes
                        long total = totalTendencia(tn, fuente, meses.get(i));
                        s[i] = nw == null || total == 0 ? Double.NaN : 100.0 * nw[0] / total;
                    } else s[i] = nw == null || nw[0] < minimo ? Double.NaN : 100.0 * nw[1] / nw[0];
                    if (!Double.isNaN(s[i])) { lo = Math.min(lo, s[i]); hi = Math.max(hi, s[i]); }
                }
                series.put(c, s);
            }
            if (pick) { lo = 0; hi = Math.max(1, hi * 1.15); }
            else { if (hi <= lo) { lo = 45; hi = 55; } lo = Math.floor(lo - 1); hi = Math.ceil(hi + 1); }
            double sx = (double) (w - ml - mr) / Math.max(1, meses.size() - 1), sy = (ht - mb - mt) / (hi - lo);
            g2.setColor(gris);
            double pasoY = pick ? pasoBonito((hi - lo) / 4.0) : (hi - lo > 12 ? 5 : 2);
            for (double y = pick ? 0 : Math.ceil(lo); y <= hi; y += pasoY) {
                int py = ht - mb - (int) ((y - lo) * sy);
                g2.drawLine(ml, py, w - mr, py);
                g2.drawString(pct1(y), 4, py + 4);
            }
            for (int i = 0; i < meses.size(); i++) {
                int px = ml + (int) (i * sx);
                g2.drawString(meses.get(i).substring(2), px - 12, ht - mb + 14);
            }
            if (!pick) { int py50 = ht - mb - (int) ((50 - lo) * sy); if (py50 >= mt && py50 <= ht - mb) { g2.setColor(new Color(128, 128, 128, 160)); g2.drawLine(ml, py50, w - mr, py50); } }
            int k = 0;
            for (Map.Entry<String, double[]> en : series.entrySet()) {
                Color c = PALETA_LADDER[k % PALETA_LADDER.length];
                boolean sel = stCivsSeleccionadas.contains(en.getKey());
                g2.setColor(c);
                g2.setStroke(new BasicStroke(sel ? 3f : 1.8f));
                double[] s = en.getValue();
                int prevX = -1, prevY = -1;
                for (int i = 0; i < s.length; i++) {
                    if (Double.isNaN(s[i])) { prevX = -1; continue; }
                    int px = ml + (int) (i * sx), py = ht - mb - (int) ((s[i] - lo) * sy);
                    if (prevX >= 0) g2.drawLine(prevX, prevY, px, py);
                    g2.fillOval(px - 2, py - 2, 5, 5);
                    prevX = px; prevY = py;
                }
                g2.setFont(base.deriveFont(sel ? Font.BOLD : Font.PLAIN, 11f));
                g2.drawString(nombreCivStats(en.getKey()), w - mr + 12, mt + 12 + k * 15);
                g2.fillRect(w - mr + 2, mt + 5 + k * 15, 7, 7);
                k++;
            }
            g2.dispose();
        }
    }

    /** Matriz de matchups 1v1: color por winrate de la civ de la fila contra la de la columna; tooltip con la cifra. */
    class MatrizPanel extends JPanel {
        List<String> civs = List.of();
        Map<String, int[]> celdas = Map.of();   // "a|b" → {n, wins de a}
        String mapaUsado = "*";                  // D2: mapa de las filas pintadas ("*" = todos los mapas)
        int celda = 13, margenIzq = 126, margenSup = 112;
        MatrizPanel() {
            setOpaque(false);
            ToolTipManager.sharedInstance().registerComponent(this);
            addMouseWheelListener(e -> {
                if (e.isControlDown()) { setCelda(celda + (e.getPreciseWheelRotation() < 0 ? 2 : -2)); e.consume(); }
                else { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this); if (sc != null) sc.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, sc)); }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int f = (e.getY() - margenSup) / celda;
                    if (e.getX() < margenIzq && f >= 0 && f < civs.size() && e.getClickCount() == 2) { navegacion.abrirTechTree(civs.get(f)); return; }   // doble clic en el nombre → tech tree
                    if (e.getX() < margenIzq && f >= 0 && f < civs.size()) {
                        filtroStats.civSeleccionada(civs.get(f));
                        for (int r = 0; r < stModelo.getRowCount(); r++) if (filtroStats.civSeleccionada().equals(stTabla.getClientProperty("civ" + r))) { int vr = stTabla.convertRowIndexToView(r); stTabla.setRowSelectionInterval(vr, vr); stTabla.scrollRectToVisible(stTabla.getCellRect(vr, 0, true)); break; }
                        repaint();
                    }
                }
            });
        }
        void setCelda(int px) { celda = Math.max(10, Math.min(30, px)); margenIzq = 96 + celda * 2 + 4; margenSup = 100 + celda; redimensionar(); }
        void redimensionar() {
            int lado = margenIzq + celda * civs.size() + 8;
            setPreferredSize(new Dimension(lado, margenSup + celda * civs.size() + 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, margenSup + celda * civs.size() + 8));
            revalidate(); repaint();
        }
        void datos(VentanaStats v, List<CivAgg> lista) {
            List<CivAgg> orden = new ArrayList<>(lista);
            orden.sort((a, b) -> Double.compare(b.wr(), a.wr()));
            List<String> cs = new ArrayList<>();
            for (CivAgg a : orden) cs.add(a.civ());
            Map<String, int[]> m = new HashMap<>();
            String mapa = mapaMatriz(v.matchupsPorMapa(), filtroStats.modo(), filtroStats.mapa(), tr -> stats.tramoEnRango(tr, v.tramos(), filtroStats.tramo()));
            for (Matchup mu : v.matchupsPorMapa().getOrDefault(mapa, List.of())) {   // solo las filas de ese mapa ("*" = el agregado)
                if (!mu.modo().equals(filtroStats.modo())) continue;
                if (!stats.tramoEnRango(mu.tramo(), v.tramos(), filtroStats.tramo())) continue;
                int[] ab = m.computeIfAbsent(mu.ca() + "|" + mu.cb(), k -> new int[2]); ab[0] += mu.n(); ab[1] += mu.wa();
                int[] ba = m.computeIfAbsent(mu.cb() + "|" + mu.ca(), k -> new int[2]); ba[0] += mu.n(); ba[1] += mu.n() - mu.wa();
            }
            civs = cs; celdas = m; mapaUsado = mapa;
            redimensionar();
        }
        void copiarDe(MatrizPanel otro) { civs = otro.civs; celdas = otro.celdas; mapaUsado = otro.mapaUsado; redimensionar(); }
        int[] celdaEn(Point p) {
            int c = (p.x - margenIzq) / celda, f = (p.y - margenSup) / celda;
            if (p.x < margenIzq || p.y < margenSup || c < 0 || f < 0 || c >= civs.size() || f >= civs.size()) return null;
            return new int[]{ f, c };
        }
        @Override public String getToolTipText(MouseEvent e) {
            int[] fc = celdaEn(e.getPoint());
            if (fc == null) return null;
            String a = civs.get(fc[0]), b = civs.get(fc[1]);
            if (a.equals(b)) return nombreCivStats(a) + t(" (espejo: fuera)", " (mirror: excluded)");
            int[] nw = celdas.get(a + "|" + b);
            if (nw == null || nw[0] == 0) return nombreCivStats(a) + " vs " + nombreCivStats(b) + t(": sin partidas", ": no games");
            double[] iv = stats.wilson(nw[1], nw[0]);
            return nombreCivStats(a) + " vs " + nombreCivStats(b) + ": " + pct1(100.0 * nw[1] / nw[0]) + " (" + miles(nw[0]) + t(" partidas, ", " games, ") + pct1(iv[0]) + "–" + pct1(iv[1]) + ")";
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (civs.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            Font peq = g2.getFont().deriveFont(9.5f);
            g2.setFont(peq);
            FontMetrics fm = g2.getFontMetrics();
            int n = civs.size();
            for (int f = 0; f < n; f++) {
                boolean sel = civs.get(f).equals(filtroStats.civSeleccionada());
                g2.setColor(sel ? fg : new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 200));
                String nombre = nombreCivStats(civs.get(f));
                g2.setFont(sel ? peq.deriveFont(Font.BOLD) : peq);
                g2.drawString(nombre, margenIzq - 18 - g2.getFontMetrics().stringWidth(nombre), margenSup + f * celda + celda - 3);
                ImageIcon ic = iconoCiv(civs.get(f), celda - 1);
                if (ic != null) g2.drawImage(ic.getImage(), margenIzq - 15, margenSup + f * celda, null);
            }
            for (int c = 0; c < n; c++) {   // emblemas sobre las columnas, bajo los nombres girados
                ImageIcon ic = iconoCiv(civs.get(c), celda - 1);
                if (ic != null) g2.drawImage(ic.getImage(), margenIzq + c * celda, margenSup - 15, null);
            }
            g2.setFont(peq);
            for (int c = 0; c < n; c++) {
                String nombre = nombreCivStats(civs.get(c));
                Graphics2D gr = (Graphics2D) g2.create();
                gr.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 200));
                int px = margenIzq + c * celda + celda - 2, py = margenSup - 4;
                gr.rotate(-Math.PI / 2, px, py - 16);      // el texto sube desde encima del emblema, con los glifos dentro de la columna
                gr.drawString(nombre, px, py - 16);
                gr.dispose();
            }
            for (int f = 0; f < n; f++) {
                for (int c = 0; c < n; c++) {
                    int x = margenIzq + c * celda, y = margenSup + f * celda;
                    if (f == c) { g2.setColor(new Color(128, 128, 128, 40)); g2.fillRect(x, y, celda - 1, celda - 1); continue; }
                    int[] nw = celdas.get(civs.get(f) + "|" + civs.get(c));
                    if (nw == null || nw[0] < 20) { g2.setColor(new Color(128, 128, 128, 18)); g2.fillRect(x, y, celda - 1, celda - 1); continue; }
                    double wr = 100.0 * nw[1] / nw[0];
                    double tint = Math.max(-1, Math.min(1, (wr - 50) / 12));   // ±12 puntos = color pleno
                    Color col = tint >= 0 ? mezcla(new Color(0x2e, 0x7d, 0x32), tint) : mezcla(new Color(0xc6, 0x28, 0x28), -tint);
                    g2.setColor(col);
                    g2.fillRect(x, y, celda - 1, celda - 1);
                }
            }
            if (filtroStats.civSeleccionada() != null) {
                int f = civs.indexOf(filtroStats.civSeleccionada());
                if (f >= 0) { g2.setColor(fg); g2.setStroke(new BasicStroke(1.5f)); g2.drawRect(margenIzq - 1, margenSup + f * celda - 1, celda * n + 1, celda + 1); }
            }
            g2.dispose();
        }
        Color mezcla(Color c, double k) {   // del gris neutro al color, según la intensidad
            int base = temaOscuroActivo ? 70 : 225;
            return new Color((int) (base + (c.getRed() - base) * k), (int) (base + (c.getGreen() - base) * k), (int) (base + (c.getBlue() - base) * k));
        }
    }
}
