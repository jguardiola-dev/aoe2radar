package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.TwitchService;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * La pestaña Directos (card "directos"): todos los canales de Twitch dando AoE2 ahora mismo, cruzados con tu
 * watchlist para el badge "en directo". Sale tal cual de SpoilerFreeRecs.construirPanelDirectos/poblarDirectos/
 * vigilarTwitch/cargarMiniaturas de la 1.1: la parte de Swing se queda aquí; el throttle, el anti-solape y la
 * descarga (red) viven en {@link DirectosPresenter}, del que esta vista es la Pantalla.
 * <p>Lo que la vista necesita de la ventana (la watchlist visible, su repintado, la barra de estado y si esta
 * pestaña está seleccionada) se lo pide a {@link Anfitrion}: así ui no importa java.net ni conoce la ventana.
 * {@code twitchLive} sigue siendo el mapa de la ventana (misma instancia): esta vista lo recibe por constructor y
 * lo escribe (a través del presentador), como las demás vistas lo leen para pintar sus propios badges.
 */
public final class DirectosView implements DirectosPresenter.Pantalla {

    /** Lo que la vista pide a la ventana: la watchlist visible, su repintado, el estado general y la pestaña activa. */
    public interface Anfitrion {
        /** Los jugadores visibles de la watchlist ahora mismo (playersModel de la 1.1): a quién cruzar contra Twitch. */
        List<Player> visibles();
        /** Los demás vigilados (todos los grupos de la watchlist y la fuente de Live now): el TW de Live now y «Solo con
         *  Twitch» no deben depender del grupo abierto (revisión 1.3, F10). Solo se cruzan con su canal conocido. */
        List<Player> otrosVigilados();
        /** Repinta la lista de la watchlist (para que se vea el badge de Twitch tras un barrido). */
        void repintarLista();
        /** Mensaje de la barra de estado general de la ventana. */
        void estado(String texto);
        /** Abre una URL en el navegador del sistema (abrirUrl de la 1.1). */
        void abrirUrl(String url);
        /** ¿Está esta pestaña seleccionada ahora mismo? (directosBtn.isSelected() de la 1.1). */
        boolean seleccionada();
    }

    private final Anfitrion anfitrion;
    private final DirectosPresenter presenter;

    private JPanel panelDirectos;
    public JLabel directosContador;   // visible para RegresionCapturas
    private JLabel directosHora;
    private final List<String[]> directosAoE2 = new ArrayList<>();   // { login, display, título, idioma, viewers }
    private final List<String[]> filasDir = new ArrayList<>();       // { login, nick visible, título visible }
    private final Map<String, ImageIcon> minis = new ConcurrentHashMap<>();   // login → miniatura en vivo
    private final Map<String, Long> minisTs = new ConcurrentHashMap<>();
    public JTable tablaDirectos;   // visible para RegresionCapturas y esFondoDeseleccionable
    private ImageIcon miniPlaceholder;
    private final List<String> codigosIdiomaDir = new ArrayList<>();
    private DefaultTableModel modeloDirectos;
    private JComboBox<String> idiomaDirCombo;
    private volatile boolean rearmandoIdiomas;

    public DirectosView(TwitchService twitchService, Map<Long, String[]> twitchLive, Tareas tareas, Anfitrion anfitrion) {
        this.anfitrion = anfitrion;
        this.presenter = new DirectosPresenter(twitchService, twitchLive, tareas, this);
        construirPanelDirectos();
    }

    /** El panel de la pestaña, para el CardLayout de la ventana. */
    public JPanel panel() { return panelDirectos; }

    // ===== DirectosPresenter.Pantalla =====================================================

    @Override public void actualizarDirectosAoE2(List<String[]> filas) {
        directosAoE2.clear();
        directosAoE2.addAll(filas);
    }

    @Override public void poblarDirectos() { hacerPoblarDirectos(); }

    @Override public void estado(String texto) { anfitrion.estado(texto); }

    @Override public void repintarLista() { anfitrion.repintarLista(); }

    @Override public boolean seleccionada() { return anfitrion.seleccionada(); }

    @Override public void miniaturaLista(String login, TwitchService.Miniatura m, long enMs) {
        BufferedImage img = new BufferedImage(m.ancho(), m.alto(), BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, m.ancho(), m.alto(), m.pixelesArgb(), 0, m.ancho());
        minis.put(login, new ImageIcon(img));
        minisTs.put(login, enMs);
        if (tablaDirectos != null) tablaDirectos.repaint();
    }

    // ===== Ciclo de vida (llamado desde mostrarDirectos, en el mismo orden que la 1.1) ====

    /** La parte de vista de mostrarDirectos(true): refresco de cortesía al abrir. */
    public void alAbrir() {
        hacerPoblarDirectos();
        vigilarTwitch();   // refresco de cortesía al abrir
    }

    /** Cruza el listado global de Twitch con la watchlist visible, respetando el ritmo de 170 s (al abrir, el río del top…). */
    public void vigilarTwitch() { presenter.vigilarTwitch(anfitrion::visibles, anfitrion::otrosVigilados); }

    /** El conjunto de jugadores vigilado cambió (nuevo top/país/clan): su barrido de Twitch se hace en el acto. */
    public void reiniciarThrottle() { presenter.reiniciarThrottle(); }

    /** F5 y el botón «Refrescar»: fuerzan el barrido saltándose el throttle (el botón, desde la 1.3: antes se
     *  ignoraba en silencio si el último barrido tenía menos de 170 s). */
    public void refrescarForzado() { presenter.reiniciarThrottle(); vigilarTwitch(); }

    // ===== Construcción del panel =========================================================

    private ImageIcon miniPlaceholder() {
        if (miniPlaceholder == null) {
            BufferedImage img = new BufferedImage(DirectosPresenter.MINI_W, DirectosPresenter.MINI_H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(temaOscuroActivo ? new Color(0x3a, 0x3a, 0x3a) : new Color(0xdd, 0xdd, 0xdd));
            g.fillRoundRect(0, 0, DirectosPresenter.MINI_W, DirectosPresenter.MINI_H, 8, 8);
            g.dispose();
            miniPlaceholder = new ImageIcon(img);
        }
        return miniPlaceholder;
    }

    private String nombreIdioma(String code) {
        try {
            String n = Locale.forLanguageTag(code).getDisplayLanguage(Locale.forLanguageTag(IDIOMA));
            if (n == null || n.isBlank()) return code.toUpperCase();
            return n.substring(0, 1).toUpperCase() + n.substring(1);
        } catch (Exception e) { return code.toUpperCase(); }
    }

    /** ¿El punto cae sobre el TEXTO del nick o del título (no el blanco)? El ancho se mide con la fuente real de cada línea. */
    private boolean sobreNombreCanal(JTable tabla, Point p) {
        int fila = tabla.rowAtPoint(p), col = tabla.columnAtPoint(p);
        if (fila < 0 || col < 0 || tabla.convertColumnIndexToModel(col) != 0) return false;
        int i = tabla.convertRowIndexToModel(fila);
        if (i >= filasDir.size()) return false;
        String[] d = filasDir.get(i);
        java.awt.Rectangle celda = tabla.getCellRect(fila, col, true);
        boolean lineaNick = p.y - celda.y <= tabla.getRowHeight() * 0.55;
        Font base = tabla.getFont();
        Font f = lineaNick ? base.deriveFont(Font.BOLD, 13f) : base;
        int ancho = tabla.getFontMetrics(f).stringWidth(lineaNick ? d[1] : d[2]) + 10;
        int dx = p.x - celda.x;
        return dx <= DirectosPresenter.MINI_OFFSET + ancho;   // la miniatura y el texto son clicables; el hueco a la derecha, no
    }

    private void abrirCanalEn(JTable tabla, Point p) {
        int fila = tabla.rowAtPoint(p);
        if (fila < 0) return;
        int i = tabla.convertRowIndexToModel(fila);
        if (i < filasDir.size()) anfitrion.abrirUrl("https://twitch.tv/" + filasDir.get(i)[0]);
    }

    private void construirPanelDirectos() {
        JPanel cont = new JPanel(new BorderLayout(0, 8));
        cont.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JPanel arriba = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        directosContador = new JLabel(t("Buscando canales\u2026", "Finding channels\u2026"));
        directosContador.setFont(directosContador.getFont().deriveFont(Font.BOLD));
        arriba.add(directosContador);
        directosHora = new JLabel();
        directosHora.setEnabled(false);
        arriba.add(directosHora);
        arriba.add(new JLabel(t("Idioma:", "Language:")));
        idiomaDirCombo = new JComboBox<>();
        idiomaDirCombo.addActionListener(e -> {
            if (rearmandoIdiomas) return;
            int i = idiomaDirCombo.getSelectedIndex();
            guardarConfig("twitch_idioma",
                    i <= 0 || i - 1 >= codigosIdiomaDir.size() ? "" : codigosIdiomaDir.get(i - 1));
            hacerPoblarDirectos();
        });
        arriba.add(idiomaDirCombo);
        JButton refrescarDir = new JButton(t("Refrescar", "Refresh"));
        refrescarDir.addActionListener(e -> refrescarForzado());   // como F5: el botón no respeta el ritmo de 170 s (sí el freno global y el anti-solape)
        arriba.add(refrescarDir);
        JLabel ayudaDir = new JLabel(t("Clic en el nombre de un canal = abrir su directo en Twitch.",
                "Click a channel name to open its Twitch stream."));
        ayudaDir.setEnabled(false);
        JPanel norte = new JPanel(new BorderLayout());
        norte.add(arriba, BorderLayout.NORTH);
        norte.add(ayudaDir, BorderLayout.SOUTH);
        cont.add(norte, BorderLayout.NORTH);
        modeloDirectos = new DefaultTableModel(
                new Object[]{ t("Canal", "Channel"), t("Idioma", "Language"),
                        t("Espectadores", "Viewers") }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return c == 2 ? Integer.class : String.class;
            }
        };
        JTable tabla = new JTable(modeloDirectos) {
            @Override public String getToolTipText(MouseEvent e) {
                int fila = rowAtPoint(e.getPoint());
                if (fila < 0) return null;
                int i = convertRowIndexToModel(fila);
                return i < filasDir.size() ? "twitch.tv/" + filasDir.get(i)[0] : null;
            }
        };
        tabla.setAutoCreateRowSorter(true);
        tablaDirectos = tabla;
        // Dos líneas (nick 13px + título) caben con cualquier tamaño de letra; la miniatura fija el mínimo
        tabla.setRowHeight(Math.max((int) (tabla.getFont().getSize2D() * 2f) + 22, DirectosPresenter.MINI_H + 8));
        tabla.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, column);
                int mr = tb.convertRowIndexToModel(row);
                ImageIcon ic = mr >= 0 && mr < filasDir.size() ? minis.get(filasDir.get(mr)[0]) : null;
                l.setIcon(ic != null ? ic : miniPlaceholder());
                l.setIconTextGap(10);
                return l;
            }
        });
        tabla.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                tabla.setCursor(Cursor.getPredefinedCursor(sobreNombreCanal(tabla, e.getPoint())
                        ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
        tabla.getColumnModel().getColumn(0).setPreferredWidth(430);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(120);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(100);
        DefaultTableCellRenderer centro = new DefaultTableCellRenderer();
        centro.setHorizontalAlignment(SwingConstants.CENTER);
        tabla.getColumnModel().getColumn(1).setCellRenderer(centro);
        DefaultTableCellRenderer miles = new DefaultTableCellRenderer() {
            @Override protected void setValue(Object v) {
                setHorizontalAlignment(CENTER);
                setText(v instanceof Integer n
                        ? String.format(Locale.forLanguageTag(IDIOMA), "%,d", n)
                        : String.valueOf(v));
            }
        };
        tabla.getColumnModel().getColumn(2).setCellRenderer(miles);
        tabla.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                // Un clic sobre el nombre del canal; doble clic, en cualquier parte
                boolean sobre = sobreNombreCanal(tabla, e.getPoint());
                if ((e.getClickCount() == 1 && sobre) || (e.getClickCount() == 2 && !sobre))
                    abrirCanalEn(tabla, e.getPoint());
            }
        });
        cont.add(new JScrollPane(tabla), BorderLayout.CENTER);
        panelDirectos = cont;
    }

    private void hacerPoblarDirectos() {
        if (modeloDirectos == null) return;
        String idiomaSel = leerConfig("twitch_idioma", "");
        Set<String> idiomas = new TreeSet<>();
        for (String[] d : directosAoE2) idiomas.add(d[3]);
        rearmandoIdiomas = true;
        idiomaDirCombo.removeAllItems();
        codigosIdiomaDir.clear();
        idiomaDirCombo.addItem(t("Todos", "All"));
        for (String c : idiomas) { codigosIdiomaDir.add(c); idiomaDirCombo.addItem(nombreIdioma(c)); }
        int sel = codigosIdiomaDir.indexOf(idiomaSel);
        if (sel < 0 && !idiomaSel.isBlank()) {   // el idioma guardado sin canales hoy: sigue elegido, con «(0)», y el combo explica la tabla vacía
            codigosIdiomaDir.add(idiomaSel);
            idiomaDirCombo.addItem(nombreIdioma(idiomaSel) + " (0)");
            sel = codigosIdiomaDir.size() - 1;
        }
        if (sel >= 0) idiomaDirCombo.setSelectedIndex(sel + 1);
        rearmandoIdiomas = false;
        modeloDirectos.setRowCount(0);
        filasDir.clear();
        List<String[]> filas = new ArrayList<>(directosAoE2);
        filas.sort((a, b) -> Integer.parseInt(b[4]) - Integer.parseInt(a[4]));
        String grisTit = temaOscuroActivo ? "#9a9a9a" : "#666666";
        for (String[] d : filas) {
            if (!idiomaSel.isBlank() && !d[3].equalsIgnoreCase(idiomaSel)) continue;
            String titulo = d[2].length() > 90 ? d[2].substring(0, 89) + "\u2026" : d[2];
            String celda = "<html><b style='font-size:13px'>" + escapeHtml(d[1]) + "</b><br>"
                    + "<span style='color:" + grisTit + "'>" + escapeHtml(titulo) + "</span></html>";
            modeloDirectos.addRow(new Object[]{ celda, nombreIdioma(d[3]), Integer.parseInt(d[4]) });
            filasDir.add(new String[]{ d[0], d[1], titulo });
        }
        pedirMiniaturas();
        long audiencia = 0;
        for (int i = 0; i < modeloDirectos.getRowCount(); i++)
            audiencia += ((Integer) modeloDirectos.getValueAt(i, 2));
        directosContador.setText(t("Top ", "Top ") + modeloDirectos.getRowCount() + t(" canales", " channels")
                + " \u00B7 " + String.format(t("es", "en").equals("es") ? Locale.of("es") : Locale.US, "%,d", audiencia)
                + " " + t("espectadores", "viewers"));
        directosHora.setText(t("Actualizado: ", "Updated: ")
                + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    /** Qué miniaturas hacen falta (nuevas o de más de 5 min) y se las pide al presentador. */
    private void pedirMiniaturas() {
        List<String> logins = new ArrayList<>();
        long ahora = System.currentTimeMillis();
        for (String[] d : filasDir)
            if (!minis.containsKey(d[0]) || ahora - minisTs.getOrDefault(d[0], 0L) > 300_000) logins.add(d[0]);
        presenter.cargarMiniaturas(logins);
    }
}
