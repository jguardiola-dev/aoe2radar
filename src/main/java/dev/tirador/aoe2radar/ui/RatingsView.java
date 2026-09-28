package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Comparado;
import dev.tirador.aoe2radar.model.LadderHist;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.model.Rejilla;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.RatingsService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.tirador.aoe2radar.service.ConsultasLadder.BIN_LADDER;
import static dev.tirador.aoe2radar.service.ConsultasLadder.familiaNombre;
import static dev.tirador.aoe2radar.service.ConsultasLadder.ladderNombre;
import static dev.tirador.aoe2radar.ui.Componentes.PALETA_LADDER;
import static dev.tirador.aoe2radar.ui.Componentes.colorHex;
import static dev.tirador.aoe2radar.ui.Componentes.etiquetaK;
import static dev.tirador.aoe2radar.ui.Componentes.pasoBonito;
import static dev.tirador.aoe2radar.ui.Componentes.subirArriba;
import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.Formato.fmtTop;
import static dev.tirador.aoe2radar.util.Formato.miles;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * La pestaña Ratings (card "ladder"): dos campanas de ELO (1v1 y equipos), su dispersión, y un comparador de
 * hasta {@link RatingsPresenter#MAX_COMPARADOS} jugadores (buscados a mano o seleccionados en la watchlist).
 * Sale tal cual de SpoilerFreeRecs.construirPanelLadder/abrirLadder de la 1.1: la parte de Swing se queda aquí,
 * la lógica de carga/búsqueda/comparación vive en {@link RatingsPresenter}, del que esta vista es la Pantalla.
 * <p>Lo que la vista necesita de la ventana (selección de la watchlist, nombre visible con alias) se lo pide a
 * {@link Anfitrion}: así ui no importa cache.Anotaciones ni conoce la ventana principal.
 */
public final class RatingsView implements RatingsPresenter.Pantalla {

    /** Lo que la vista pide a la ventana: la selección de la watchlist y el nombre visible (con alias). */
    public interface Anfitrion {
        /** Los jugadores seleccionados ahora mismo en la watchlist (playersList.getSelectedValuesList() de la 1.1). */
        List<Player> seleccion();
        /** ¿Sigue seleccionado este jugador en la watchlist? */
        boolean seleccionado(long pid);
        /** Lo quita de la selección de la watchlist (al quitar su chip de la comparación). */
        void deseleccionar(long pid);
        /** Vacía la selección de la watchlist (botón «Quitar todos»). */
        void limpiarSeleccion();
        /** El nombre visible de un jugador (con su alias, si tiene uno guardado). */
        String nombreVisible(long pid, String nombre);
    }

    private static final String[] FAMILIAS = { "rm", "ew" };

    private final RatingsService ratingsService;
    private final Anfitrion anfitrion;
    private final RatingsPresenter presenter;

    private JPanel ladderPanel;
    public JComboBox<String> familiaCombo;   // visible para RegresionCapturas
    public JCheckBox activosCheck;           // visible para RegresionCapturas
    public JSplitPane ladderDivisor;         // visible para RegresionCapturas
    private JTextField ladderBusca;
    private JPopupMenu ladderPopup;
    private JLabel ladderEstado, ladderPista;
    /** Comparación en curso: se pinta en las campanas mientras Ratings esté abierto. RegresionCapturas la rellena directamente. */
    public final List<Comparado> ladderComparados = new ArrayList<>();   // visible para RegresionCapturas
    private HistogramaPanel histograma1, histograma2;
    public DispersionPanel dispersion;       // visible para RegresionCapturas
    private JPanel ladderChips, ladderGraficos;
    private JButton ladderQuitarTodos;
    private JTable ladderTabla;
    public DefaultTableModel ladderModelo;   // visible para RegresionCapturas
    private javax.swing.Timer ladderDebounce;
    /** El parpadeo de progreso mientras carga: es un campo (no una variable local) porque solo hay una carga a la
     *  vez (lo garantiza RatingsService.cargando()); lo arranca cargaIniciada() y lo para pararProgreso(), ambos
     *  llamados desde RatingsPresenter.cargar(). */
    private javax.swing.Timer ladderTick;
    private String familia = "ew".equals(leerConfig("ladder_familia", "rm")) ? "ew" : "rm";
    public boolean soloActivos = Boolean.parseBoolean(leerConfig("ladder_activos", "true"));   // visible para RegresionCapturas
    private double ratingsEscala = Math.max(0.6, Math.min(1.6, Double.parseDouble(leerConfig("ratings_escala", "1"))));   // tamaño de los tres gráficos (zoom de conjunto)

    public RatingsView(RatingsService ratingsService, BusquedaPerfiles busqueda, ProfileService perfiles, Tareas tareas, Anfitrion anfitrion) {
        this.ratingsService = ratingsService;
        this.anfitrion = anfitrion;
        this.presenter = new RatingsPresenter(ratingsService, busqueda, perfiles, tareas, this);
        construirPanelLadder();
    }

    /** El panel de la pestaña, para el CardLayout de la ventana y para esFondoDeseleccionable. */
    public JPanel panel() { return ladderPanel; }

    // ===== RatingsPresenter.Pantalla =====================================================

    @Override public List<Comparado> comparados() { return ladderComparados; }

    @Override public void refrescarComparados() { ladderRefrescarComparados(); }

    @Override public void estado(String texto) { ladderEstado.setText(texto); }

    @Override public boolean seleccionado(long pid) { return anfitrion.seleccionado(pid); }

    @Override public String nombreVisible(long pid, String nombre) { return anfitrion.nombreVisible(pid, nombre); }

    @Override public void cargaIniciada() {
        ladderEstado.setText(t("Cargando los resúmenes de ratings…", "Loading the ratings summaries…"));
        ladderTick = new javax.swing.Timer(500, e -> { if (ratingsService.cargando()) ladderEstado.setText(ratingsService.progreso()); });
        ladderTick.start();
    }

    @Override public void pararProgreso() { if (ladderTick != null) ladderTick.stop(); }

    @Override public void cargaTerminada(String error) {
        if (error != null) { ladderEstado.setText(t("No se pudieron cargar los ratings: ", "Couldn't load the ratings: ") + error); return; }
        ladderRefrescar();
        sincronizarSeleccion();
    }

    @Override public void mostrarSugerencias(List<String[]> res) {
        ladderPopup.removeAll();
        int n = 0;
        for (String[] r : res) {
            JMenuItem it = new JMenuItem(r[2]);
            long pid = Long.parseLong(r[0]); String nombre = r[1];
            it.addActionListener(a -> { presenter.anadir(pid, nombre, false); ladderBusca.setText(""); ladderPopup.setVisible(false); });
            ladderPopup.add(it);
            if (++n >= 8) break;
        }
        if (n > 0 && ladderBusca.isShowing()) ladderPopup.show(ladderBusca, 0, ladderBusca.getHeight());
    }

    @Override public void ocultarSugerencias() { ladderPopup.setVisible(false); ladderPopup.removeAll(); }

    // ===== Ciclo de vida (llamado desde abrirLadder, en el mismo orden que la 1.1) =======

    /** La parte de vista de abrirLadder que va ANTES del cromo (taparResultados/apagarForma/actualizarControlesTabla). */
    public void alAbrirAntes() {
        subirArriba(ladderPanel);
        SwingUtilities.invokeLater(this::aplicarAltoComparados);
    }

    /** Reparte el alto de la tabla de comparados (230 px por defecto, o lo que el usuario dejara) apenas el
     *  divisor tenga alto real. La primera vez que se abre Ratings, el divisor puede seguir midiendo 0 (aún no
     *  se ha mostrado ni una vez): antes el reparto se perdía sin más; ahora un ComponentListener de un solo uso
     *  lo aplica en cuanto llegue el primer componentResized (DEUDA, fila 13). */
    private void aplicarAltoComparados() {
        int alto = Math.max(60, Integer.parseInt(leerConfig("ratings_tabla_alto", "230")));
        if (ladderDivisor.getHeight() > alto + 100) {
            ladderDivisor.setDividerLocation(ladderDivisor.getHeight() - alto - ladderDivisor.getDividerSize());
            altoAplicado = true;
        } else if (ladderDivisor.getHeight() > 0) {
            altoAplicado = true;   // no cabe el alto guardado: se deja como está, pero lo que mueva el usuario ya se guarda
        } else if (ladderDivisor.getHeight() == 0) {
            ladderDivisor.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentResized(java.awt.event.ComponentEvent e) {
                    ladderDivisor.removeComponentListener(this);
                    aplicarAltoComparados();
                }
            });
        }
    }

    /** true cuando ya se aplicó el alto guardado. Antes, el primer reparto de Swing al mostrar Ratings (la tabla
     *  aplastada a su mínimo) se guardaba ANTES de aplicar el del usuario y lo pisaba: la barra volvía siempre a
     *  ~60 px (fallo de la 1.1, visto por Jorge al probar la 1.2). Solo el EDT lo lee y escribe. */
    private boolean altoAplicado;

    /** El divisor se movió: guarda el alto de la tabla de comparados, pero solo si se ve y ya se aplicó el guardado. */
    void alMoverDivisor(boolean visible) {
        if (altoAplicado && visible && ladderDivisor.getHeight() > 0)
            guardarConfig("ratings_tabla_alto", String.valueOf(Math.max(60, ladderDivisor.getHeight() - ladderDivisor.getDividerLocation() - ladderDivisor.getDividerSize())));
    }

    /** La parte de vista de abrirLadder que va DESPUÉS del cromo: arranca la carga si hace falta. */
    public void alAbrirDespues() { presenter.cargar(); }

    /** Con el Ladder abierto, la selección de la watchlist se refleja en las campanas. Llamado también desde el listener de playersList. */
    public void sincronizarSeleccion() {
        if (ladderPanel == null || !ladderPanel.isShowing()) return;
        presenter.sincronizarSeleccion(anfitrion.seleccion());
    }

    // ===== Construcción del panel =========================================================

    private JPanel construirPanelLadder() {
        ladderPanel = new JPanel(new BorderLayout(8, 6));
        ladderPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel norte = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        familiaCombo = new JComboBox<>(new String[]{ familiaNombre("rm"), familiaNombre("ew") });
        familiaCombo.setSelectedIndex("ew".equals(familia) ? 1 : 0);
        familiaCombo.setToolTipText(t("Random Map: campanas 1v1 y equipos RM. Empire Wars: las de EW.", "Random Map: 1v1 and team RM curves. Empire Wars: the EW ones."));
        familiaCombo.addActionListener(e -> { familia = FAMILIAS[Math.max(0, familiaCombo.getSelectedIndex())]; guardarConfig("ladder_familia", familia); ladderRefrescar(); });
        norte.add(familiaCombo);
        activosCheck = new JCheckBox(t("Solo activos", "Active only"), soloActivos);
        activosCheck.setFocusable(false);
        activosCheck.addActionListener(e -> { soloActivos = activosCheck.isSelected(); guardarConfig("ladder_activos", String.valueOf(soloActivos)); ladderRefrescar(); });
        norte.add(activosCheck);
        JPanel zoom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        JLabel zl = new JLabel(t("Zoom:", "Zoom:")); zl.setFont(zl.getFont().deriveFont(11f)); zoom.add(zl);
        for (String[] z : new String[][]{ { "\u2212", "out" }, { "+", "in" }, { "\u27F2", "reset" } }) {
            JButton zb = new JButton(z[0]);
            zb.setFocusable(false); zb.setMargin(new Insets(0, 6, 0, 6)); zb.putClientProperty("JButton.buttonType", "roundRect");
            zb.setToolTipText(t("Tamaño de los tres gráficos a la vez: aleja para verlos todos con la tabla, acerca para leer detalle. También Ctrl + rueda sobre un gráfico; doble clic para volver.", "Size of the three charts at once: zoom out to see them all with the table, zoom in for detail. Also Ctrl + wheel over a chart; double-click to reset."));
            zb.addActionListener(e -> { switch (z[1]) { case "in" -> escalarRatings(1.25); case "out" -> escalarRatings(1 / 1.25); default -> escalarRatingsReset(); } });
            zoom.add(zb);
        }
        ladderBusca = new JTextField(18);
        ladderBusca.putClientProperty("JTextField.placeholderText", t("Buscar jugador… o selecciona en la watchlist", "Search a player… or select in the watchlist"));
        ladderBusca.putClientProperty("JTextField.showClearButton", true);
        ladderPopup = TemaApp.registrarPopup(new JPopupMenu()); ladderPopup.setFocusable(false);
        ladderDebounce = new javax.swing.Timer(450, e -> ladderSugerir());
        ladderDebounce.setRepeats(false);
        ladderBusca.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() { ladderDebounce.restart(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        ladderBusca.addActionListener(e -> {   // Enter: la primera sugerencia (y así el Enter no llega al botón de buscar partidas)
            if (ladderPopup.isVisible() && ladderPopup.getComponentCount() > 0) ((JMenuItem) ladderPopup.getComponent(0)).doClick();
            else ladderSugerir();
        });
        ladderBusca.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && ladderPopup.isVisible() && ladderPopup.getComponentCount() > 0) { ((JMenuItem) ladderPopup.getComponent(0)).doClick(); e.consume(); }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) ladderPopup.setVisible(false);
            }
        });
        norte.add(ladderBusca, 0);   // el buscador, siempre lo primero de la barra (igual en Perfil)
        ladderEstado = new JLabel();
        ladderEstado.setFont(ladderEstado.getFont().deriveFont(Font.PLAIN, 11f));
        norte.add(ladderEstado);
        ladderPanel.add(norte, BorderLayout.NORTH);

        JPanel centro = new JPanel(new BorderLayout(0, 4));
        JPanel arriba = new JPanel(new BorderLayout(0, 2));
        ladderPista = new JLabel(t("Busca un jugador arriba o selecciónalo en la watchlist (Ctrl para varios): aparecerá en las campanas.",
                "Search a player above or select one in the watchlist (Ctrl for several): they'll show up on the curves."));
        ladderPista.setFont(ladderPista.getFont().deriveFont(Font.ITALIC, 11f));
        ladderPista.setForeground(Color.GRAY);
        arriba.add(ladderPista, BorderLayout.NORTH);
        ladderChips = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 2));
        arriba.add(ladderChips, BorderLayout.CENTER);
        ladderQuitarTodos = new JButton(t("\u00D7 Quitar todos", "\u00D7 Remove all"));
        ladderQuitarTodos.setFocusable(false); ladderQuitarTodos.setMargin(new Insets(1, 6, 1, 6)); ladderQuitarTodos.putClientProperty("JButton.buttonType", "roundRect");
        ladderQuitarTodos.setVisible(false);
        ladderQuitarTodos.addActionListener(e -> { anfitrion.limpiarSeleccion(); ladderComparados.clear(); ladderRefrescarComparados(); });
        arriba.add(ladderQuitarTodos, BorderLayout.EAST);
        centro.add(arriba, BorderLayout.NORTH);
        histograma1 = new HistogramaPanel(familia + "_1v1");
        histograma2 = new HistogramaPanel(familia + "_team");
        dispersion = new DispersionPanel();
        PanelScrollable graficos = new PanelScrollable();
        ladderGraficos = graficos;
        aplicarEscalaRatings();
        dispersion.addMouseWheelListener(e -> {   // el zoom de conjunto también desde la dispersión
            if (e.isControlDown()) { escalarRatings(e.getPreciseWheelRotation() < 0 ? 1.15 : 1 / 1.15); e.consume(); }
            else { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, dispersion); if (sc != null) sc.dispatchEvent(SwingUtilities.convertMouseEvent(dispersion, e, sc)); }
        });
        JScrollPane scroll = new JScrollPane(graficos, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        ladderModelo = new DefaultTableModel(new Object[]{ t("Jugador", "Player"), "ELO 1v1", t("Rango 1v1", "1v1 rank"), t("Top % 1v1", "1v1 top %"),
                t("ELO equipos", "Team ELO"), t("Rango eq.", "Team rank"), t("Top % eq.", "Team top %"), t("Clan", "Clan"), t("País", "Country") }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        ladderTabla = new JTable(ladderModelo);
        ladderTabla.setRowHeight(ladderTabla.getRowHeight() + 4);
        ladderTabla.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel lab = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, col);
                lab.setIcon(value == null ? null : iconoBandera(String.valueOf(value))); lab.setIconTextGap(5);
                return lab;
            }
        });
        JScrollPane sp = new JScrollPane(ladderTabla);
        sp.setPreferredSize(new Dimension(10, 150));
        JPanel abajo = new JPanel(new BorderLayout(0, 2));
        abajo.add(zoom, BorderLayout.NORTH);   // el zoom, abajo a la derecha, bajo las gráficas
        abajo.add(sp, BorderLayout.CENTER);
        abajo.setMinimumSize(new Dimension(10, 60));
        ladderDivisor = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, abajo);   // arrastra el borde: la tabla de comparados crece
        ladderDivisor.setResizeWeight(1.0); ladderDivisor.setContinuousLayout(true); ladderDivisor.setBorder(null); ladderDivisor.setDividerSize(7);
        ladderDivisor.setOneTouchExpandable(true);
        ladderDivisor.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, ev -> alMoverDivisor(ladderDivisor.isShowing()));
        centro.add(ladderDivisor, BorderLayout.CENTER);
        ladderPanel.add(centro, BorderLayout.CENTER);
        JLabel pie = new JLabel(t("Distribución de ELO por ladder (resumen diario de sfr-data sobre los volcados de aoe2companion). «Solo activos»: jugadores con partidas recientes; el Top % se calcula sobre la campana elegida. Pasa el ratón por una barra para ver cuántos jugadores hay en ese tramo. Los seleccionados en la watchlist se dibujan mientras Ratings esté abierto.",
                "ELO distribution per ladder (sfr-data daily summary of the aoe2companion dumps). \u201CActive only\u201D: players with recent games; the top % is computed on the chosen curve. Hover a bar to see how many players sit in that bracket. Watchlist selections are drawn while Ratings is open."));
        pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f));
        ladderPanel.add(pie, BorderLayout.SOUTH);
        return ladderPanel;
    }

    /** Sugerencias de jugadores por nombre (búsqueda del companion, con retardo para no disparar por cada tecla). */
    private void ladderSugerir() {
        String q = ladderBusca.getText().trim();
        presenter.sugerir(q, () -> ladderBusca.getText().trim());
    }

    private void ladderRefrescarComparados() {
        ladderChips.removeAll();
        ladderModelo.setRowCount(0);
        String lb1 = familia + "_1v1", lb2 = familia + "_team";
        int k = 0;
        for (Comparado c : ladderComparados) {
            Color col = PALETA_LADDER[k % PALETA_LADDER.length];
            k++;
            JButton chip = new JButton("<html><font color='" + colorHex(col) + "'>\u25A0</font> " + escapeHtml(c.name()) + "  \u00D7</html>");
            chip.setFocusable(false); chip.setMargin(new Insets(1, 6, 1, 6)); chip.putClientProperty("JButton.buttonType", "roundRect");
            chip.setToolTipText(c.deSeleccion() ? t("Seleccionado en la watchlist: deselecciónalo (o pulsa ×) para quitarlo", "Selected in the watchlist: deselect it (or press ×) to remove it")
                    : t("Buscado a mano: × para quitarlo", "Searched by hand: × to remove it"));
            chip.addActionListener(e -> {
                if (c.deSeleccion()) anfitrion.deseleccionar(c.pid());   // quitar = deseleccionar en la lista; la sincronización lo saca
                ladderComparados.remove(c);
                ladderRefrescarComparados();
            });
            ladderChips.add(chip);
            String clanDe = "";
            for (Map.Entry<String, List<LadderRow>> en : ratingsService.clanes().entrySet()) { for (LadderRow m : en.getValue()) if (m.pid() == c.pid()) { clanDe = en.getKey(); break; } if (!clanDe.isEmpty()) break; }
            int r1 = c.rating(lb1), r2 = c.rating(lb2), k1 = c.rank(lb1), k2 = c.rank(lb2);
            String p1 = r1 > 0 ? presenter.topDe(c, lb1, soloActivos) : null, p2 = r2 > 0 ? presenter.topDe(c, lb2, soloActivos) : null;
            ladderModelo.addRow(new Object[]{ c.name(), r1 > 0 ? r1 : "-", k1 > 0 ? "#" + miles(k1) : "-", p1 != null ? p1 : "-",
                    r2 > 0 ? r2 : "-", k2 > 0 ? "#" + miles(k2) : "-", p2 != null ? p2 : "-", clanDe, c.country() });
        }
        ladderPista.setVisible(ladderComparados.isEmpty());
        if (ladderQuitarTodos != null) ladderQuitarTodos.setVisible(ladderComparados.size() > 1);
        ladderChips.revalidate(); ladderChips.repaint();
        histograma1.repaint(); histograma2.repaint(); dispersion.repaint();
    }

    /** Refresca las campanas y la tabla tras cambiar de familia, de «solo activos», o al terminar de cargar. RegresionCapturas la usa por el nombre. */
    public void ladderRefrescar() {
        histograma1.lb = familia + "_1v1";
        histograma2.lb = familia + "_team";
        activosCheck.setToolTipText(t("Solo jugadores con ", "Only players with ") + RatingsService.criterioActivos(ratingsService.activosMinPartidas(), ratingsService.activosDias())
                + t(". Sin marcar: todos los que tienen rating.", ". Unticked: everyone with a rating."));
        ladderRefrescarComparados();
        LadderHist h = ratingsService.hist(histograma1.lb, soloActivos);
        ladderEstado.setText(h == null ? t("Sin datos de ese ladder.", "No data for that ladder.")
                : t("Resumen del ", "Summary of ") + ratingsService.generado().replace("T", " ").substring(0, Math.min(16, ratingsService.generado().length())) + " UTC");
    }

    /** Zoom de conjunto: los tres gráficos crecen o encogen a la vez (entre la mitad y el doble); se recuerda. RegresionCapturas la usa por el nombre. */
    public void escalarRatings(double factor) {
        ratingsEscala = Math.max(0.6, Math.min(1.6, ratingsEscala * factor));
        guardarConfig("ratings_escala", String.format(Locale.ROOT, "%.3f", ratingsEscala));
        aplicarEscalaRatings();
    }

    /** RegresionCapturas la usa por el nombre. */
    public void escalarRatingsReset() { ratingsEscala = 1; guardarConfig("ratings_escala", "1"); aplicarEscalaRatings(); }

    /** Tamaño y disposición de los gráficos según la escala: apilados a tamaño normal; al alejar, las campanas lado a lado y la dispersión a media anchura, cada uno con su proporción. */
    private void aplicarEscalaRatings() {
        if (histograma1 == null || ladderGraficos == null) return;
        for (JComponent c : new JComponent[]{ histograma1, histograma2, dispersion }) {
            int alto = (int) Math.round((c == dispersion ? 420 : 300) * ratingsEscala);
            c.setPreferredSize(new Dimension(10, alto)); c.setMinimumSize(new Dimension(10, alto)); c.setMaximumSize(new Dimension(Integer.MAX_VALUE, alto));
            c.setAlignmentX(0f);
        }
        ladderGraficos.removeAll();
        if (ratingsEscala >= 0.75) {
            ladderGraficos.add(histograma1); ladderGraficos.add(Box.createRigidArea(new Dimension(0, 16)));
            ladderGraficos.add(histograma2); ladderGraficos.add(Box.createRigidArea(new Dimension(0, 16)));
            ladderGraficos.add(dispersion);
        } else {
            JPanel fila1 = new JPanel(new GridLayout(1, 2, 16, 0)); fila1.setOpaque(false); fila1.setAlignmentX(0f);
            fila1.add(histograma1); fila1.add(histograma2);
            fila1.setMaximumSize(new Dimension(Integer.MAX_VALUE, histograma1.getPreferredSize().height));
            JPanel fila2 = new JPanel(new GridLayout(1, 2, 16, 0)); fila2.setOpaque(false); fila2.setAlignmentX(0f);
            JPanel hueco = new JPanel(); hueco.setOpaque(false);
            fila2.add(dispersion); fila2.add(hueco);
            fila2.setMaximumSize(new Dimension(Integer.MAX_VALUE, dispersion.getPreferredSize().height));
            ladderGraficos.add(fila1); ladderGraficos.add(Box.createRigidArea(new Dimension(0, 12))); ladderGraficos.add(fila2);
        }
        ladderGraficos.revalidate(); ladderGraficos.repaint();
    }

    // ===== Gráficos (pintados a mano) ======================================================

    private final class HistogramaPanel extends JPanel {
        String lb;
        int ml = 58, mb = 30, mt = 30, mr = 18;
        HistogramaPanel(String lb) {
            this.lb = lb; setOpaque(false);
            ToolTipManager.sharedInstance().registerComponent(this);
            addMouseWheelListener(e -> {
                if (e.isControlDown()) { escalarRatings(e.getPreciseWheelRotation() < 0 ? 1.15 : 1 / 1.15); e.consume(); }
                else { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this); if (sc != null) sc.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, sc)); }
            });
            addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) escalarRatingsReset(); } });
        }
        LadderHist datos() { return ratingsService.hist(lb, soloActivos); }
        /** Rango de ELO de esta campana. */
        double[] rango() {
            LadderHist h = datos();
            if (h == null) return new double[]{ 0, 3000 };
            return new double[]{ h.min(), h.min() + h.bins().length * (double) BIN_LADDER };
        }
        double sx() { double[] r = rango(); return (getWidth() - ml - mr) / Math.max(1, r[1] - r[0]); }
        double eloEn(int x) { return rango()[0] + (x - ml) / sx(); }
        int xDe(double elo) { return ml + (int) Math.round((elo - rango()[0]) * sx()); }
        @Override public String getToolTipText(MouseEvent e) {
            LadderHist h = datos();
            if (h == null || e.getX() < ml || e.getX() > getWidth() - mr) return null;
            int idx = Math.floorDiv((int) Math.floor(eloEn(e.getX())) - h.min(), BIN_LADDER);
            if (idx < 0 || idx >= h.bins().length) return null;
            long encima = 0; for (int i = idx + 1; i < h.bins().length; i++) encima += h.bins()[i];
            int lo = h.min() + idx * BIN_LADDER;
            return "<html><b>" + lo + "–" + (lo + BIN_LADDER - 1) + "</b>: " + miles(h.bins()[idx]) + t(" jugadores", " players") + "<br>"
                    + miles(encima) + t(" por encima", " above") + " (" + escapeHtml(fmtTop(100.0 * encima / h.total())) + ")</html>";
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), ht = getHeight();
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            Color gris = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 90 : 170), tenue = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 35);
            LadderHist h = datos();
            Font base = g2.getFont();
            g2.setFont(base.deriveFont(Font.BOLD, 12f));
            g2.setColor(fg);
            String titulo = ladderNombre(lb) + (h == null ? "" : "  ·  " + miles(h.total()) + (soloActivos && ratingsService.tieneActivos(lb) ? t(" activos", " active") : t(" jugadores", " players"))
                    + "  ·  " + t("mediana ", "median ") + h.mediana());
            g2.drawString(titulo, ml, 16);
            g2.setFont(base);
            if (h == null || h.bins().length == 0 || h.total() == 0) { g2.setColor(gris); g2.drawString(t("Sin datos", "No data"), ml, ht / 2); g2.dispose(); return; }
            int[] bins = h.bins(); int minR = h.min();
            double[] r = rango(); double sx = sx();
            int i0 = Math.max(0, (int) Math.floor((r[0] - minR) / BIN_LADDER)), i1 = Math.min(bins.length - 1, (int) Math.ceil((r[1] - minR) / BIN_LADDER));
            int max = 1; for (int i = i0; i <= i1; i++) max = Math.max(max, bins[i]);
            // eje Y: jugadores por tramo de 25, con marcas «redondas»
            double pasoY = pasoBonito(max / 4.0);
            g2.setFont(base.deriveFont(10f));
            for (double v = pasoY; v <= max; v += pasoY) {
                int y = ht - mb - (int) ((ht - mb - mt) * v / max);
                g2.setColor(tenue); g2.drawLine(ml, y, w - mr, y);
                g2.setColor(gris); String s = etiquetaK(v); g2.drawString(s, ml - 6 - g2.getFontMetrics().stringWidth(s), y + 4);
            }
            g2.setColor(gris);
            g2.drawLine(ml, ht - mb, w - mr, ht - mb);
            g2.drawLine(ml, mt, ml, ht - mb);
            // eje X: ELO, marcas según el zoom
            int pasoX = (int) pasoBonito((r[1] - r[0]) / 8.0);
            for (int v = (int) (Math.ceil(r[0] / pasoX) * pasoX); v <= r[1]; v += pasoX) {
                int x = xDe(v);
                g2.drawLine(x, ht - mb, x, ht - mb + 4);
                String s = String.valueOf(v);
                g2.drawString(s, x - g2.getFontMetrics().stringWidth(s) / 2, ht - mb + 16);
            }
            g2.drawString("ELO", w - mr - g2.getFontMetrics().stringWidth("ELO"), ht - mb + 27);
            g2.drawString(t("jugadores", "players"), 4, mt - 6);
            Color barra = temaOscuroActivo ? new Color(0x5a, 0x8f, 0xc7) : new Color(0x3b, 0x6e, 0xa8);
            int anchoBarra = Math.max(1, (int) Math.ceil(BIN_LADDER * sx) - 1);
            for (int i = i0; i <= i1; i++) {
                int bh = (int) ((ht - mb - mt) * bins[i] / (double) max);
                int x = xDe(minR + i * BIN_LADDER);
                if (x + anchoBarra < ml || x > w - mr) continue;
                g2.setColor(barra);
                g2.fillRect(Math.max(ml, x), ht - mb - bh, Math.min(anchoBarra, w - mr - Math.max(ml, x)), bh);
            }
            // mediana: línea gruesa y etiqueta con fondo
            if (h.mediana() >= r[0] && h.mediana() <= r[1]) {
                int xm = xDe(h.mediana());
                g2.setColor(temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00));
                g2.setStroke(new BasicStroke(2.2f));
                g2.drawLine(xm, mt, xm, ht - mb);
                String s = t("mediana ", "median ") + h.mediana();
                g2.setFont(base.deriveFont(Font.BOLD, 11f));
                int sw = g2.getFontMetrics().stringWidth(s);
                int lx = xm + 6 + sw > w - mr ? xm - 6 - sw : xm + 6;
                g2.setColor(temaOscuroActivo ? new Color(0, 0, 0, 150) : new Color(255, 255, 255, 190));
                g2.fillRoundRect(lx - 4, mt + 2, sw + 8, 16, 6, 6);
                g2.setColor(temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00));
                g2.drawString(s, lx, mt + 14);
            }
            g2.setFont(base);
            int k = 0;
            for (Comparado c : ladderComparados) {
                int rating = c.rating(lb);
                Color col = PALETA_LADDER[k % PALETA_LADDER.length];
                k++;
                if (rating <= 0 || rating < r[0] || rating > r[1]) continue;
                int x = xDe(rating);
                g2.setColor(col);
                g2.setStroke(new BasicStroke(2f));
                int fila = (k - 1) % 5;
                g2.drawLine(x, mt + 22 + fila * 13, x, ht - mb);
                String p = presenter.topDe(c, lb, soloActivos);
                String etiqueta = c.name() + " · " + rating + (p != null ? " · " + p : "");
                int ancho = g2.getFontMetrics().stringWidth(etiqueta);
                g2.drawString(etiqueta, x + 4 + ancho > w - mr ? x - 4 - ancho : x + 4, mt + 32 + fila * 13);   // junto al borde derecho, a la izquierda de la línea
            }
            g2.dispose();
        }
    }

    /** Dispersión rating 1v1 (x) × rating equipos (y): densidad por celdas de 25×25, recta de regresión y los comparados como puntos. */
    public final class DispersionPanel extends JPanel {
        DispersionPanel() { setOpaque(false); }
        Rejilla datos() { return ratingsService.dispersion(familia, soloActivos); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), ht = getHeight(), ml = 52, mb = 30, mt = 30, mr = 16;
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            Color gris = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 90 : 170);
            Font base = g2.getFont();
            Rejilla r = datos();
            String lbx = familia + "_1v1", lby = familia + "_team";
            g2.setFont(base.deriveFont(Font.BOLD, 12f));
            g2.setColor(fg);
            g2.drawString(t("Dispersión ", "Scatter ") + ladderNombre(lbx) + " × " + ladderNombre(lby)
                    + (r == null ? "" : "  ·  " + miles(r.n()) + (soloActivos && ratingsService.dispersionTieneActivos(familia) ? t(" activos", " active") : t(" jugadores", " players")) + t(" en ambos ladders", " on both ladders")
                    + "  ·  r = " + String.format(Locale.ROOT, "%.2f", r.r())), ml, 16);
            g2.setFont(base);
            if (r == null) { g2.setColor(gris); g2.drawString(t("Sin datos de dispersión (aún no publicados o menos de 100 jugadores).", "No scatter data (not published yet or fewer than 100 players)."), ml, ht / 2); g2.dispose(); return; }
            int maxIx = 0, maxIy = 0, maxN = 1;
            for (int[] c : r.celdas()) { maxIx = Math.max(maxIx, c[0]); maxIy = Math.max(maxIy, c[1]); maxN = Math.max(maxN, c[2]); }
            int x0 = r.minX(), x1 = r.minX() + (maxIx + 1) * BIN_LADDER, y0 = r.minY(), y1 = r.minY() + (maxIy + 1) * BIN_LADDER;
            double sx = (w - ml - mr) / (double) (x1 - x0), sy = (ht - mb - mt) / (double) (y1 - y0);
            Color base2 = temaOscuroActivo ? new Color(0x5a, 0x8f, 0xc7) : new Color(0x3b, 0x6e, 0xa8);
            int cw = Math.max(1, (int) Math.ceil(BIN_LADDER * sx)), ch = Math.max(1, (int) Math.ceil(BIN_LADDER * sy));
            for (int[] c : r.celdas()) {
                int alfa = 25 + (int) (225 * Math.sqrt(c[2] / (double) maxN));
                g2.setColor(new Color(base2.getRed(), base2.getGreen(), base2.getBlue(), Math.min(255, alfa)));
                int px = ml + (int) ((c[0] * BIN_LADDER) * sx);
                int py = ht - mb - (int) (((c[1] + 1) * BIN_LADDER) * sy);
                g2.fillRect(px, py, cw, ch);
            }
            g2.setColor(gris);
            g2.drawLine(ml, ht - mb, w - mr, ht - mb);
            g2.drawLine(ml, mt, ml, ht - mb);
            g2.setFont(base.deriveFont(10f));
            for (int v = (Math.floorDiv(x0, 500) + 1) * 500; v < x1; v += 500) { int x = ml + (int) ((v - x0) * sx); g2.drawLine(x, ht - mb, x, ht - mb + 4); g2.drawString(String.valueOf(v), x - 12, ht - mb + 16); }
            for (int v = (Math.floorDiv(y0, 500) + 1) * 500; v < y1; v += 500) { int y = ht - mb - (int) ((v - y0) * sy); g2.drawLine(ml - 4, y, ml, y); g2.drawString(String.valueOf(v), 6, y + 4); }
            g2.drawString(t("ELO 1v1 →", "1v1 ELO →"), w - mr - 60, ht - 4);
            g2.drawString(t("↑ ELO equipos", "↑ Team ELO"), ml + 4, mt + 12);
            // recta y = a·x + b, recortada al rango vertical visible
            g2.setColor(new Color(0xe0, 0x60, 0x40));
            g2.setStroke(new BasicStroke(1.6f));
            double ya = r.a() * x0 + r.b(), yb = r.a() * x1 + r.b();
            int px1 = ml, px2 = w - mr;
            int py1 = ht - mb - (int) ((ya - y0) * sy), py2 = ht - mb - (int) ((yb - y0) * sy);
            Shape clipPrevio = g2.getClip();   // nunca setClip(null): quitaría el recorte del visor y pintaría sobre la tabla
            g2.clipRect(ml, mt, w - ml - mr, ht - mb - mt);
            g2.drawLine(px1, py1, px2, py2);
            g2.setClip(clipPrevio);
            g2.drawString(String.format(Locale.ROOT, "y = %.2f·x + %.0f", r.a(), r.b()), w - mr - 120, mt + 12);
            int k = 0;
            for (Comparado c : ladderComparados) {
                Color col = PALETA_LADDER[k % PALETA_LADDER.length];
                k++;
                int rx = c.rating(lbx), ry = c.rating(lby);
                if (rx <= 0 || ry <= 0) continue;
                int px = ml + (int) ((rx - x0) * sx), py = ht - mb - (int) ((ry - y0) * sy);
                g2.setColor(col);
                g2.fillOval(px - 5, py - 5, 10, 10);
                g2.setColor(fg);
                g2.drawOval(px - 5, py - 5, 10, 10);
                g2.setColor(col);
                int ancho = g2.getFontMetrics().stringWidth(c.name());
                g2.drawString(c.name(), px + 8 + ancho > w - mr ? px - 8 - ancho : px + 8, py - 4);
            }
            g2.dispose();
        }
    }
}
