package dev.tirador.aoe2radar.ui;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.RowSorter;
import javax.swing.ScrollPaneConstants;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static dev.tirador.aoe2radar.ui.Componentes.colorSecundario;
import static dev.tirador.aoe2radar.ui.Componentes.colorWr;
import static dev.tirador.aoe2radar.ui.Componentes.tituloSeccion;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Formato.miles;
import static dev.tirador.aoe2radar.util.Formato.pct1;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * Tarjetas, filas de barra y ventanas de lista/tabla completa que usan varias vistas (Ratings, Civ Stats,
 * Actividad…). No es una clase de solo estáticos como Iconos o Componentes porque necesita al dueño de sus
 * diálogos (la ventana principal, igual que hoy) y forma de avisar del clic con Ctrl, que la ventana sigue
 * leyendo desde otras vistas.
 */
public final class Listas {

    private final JFrame propietaria;
    private final Consumer<Boolean> marcarUltimoClicCtrl;

    public Listas(JFrame propietaria, Consumer<Boolean> marcarUltimoClicCtrl) {
        this.propietaria = propietaria;
        this.marcarUltimoClicCtrl = marcarUltimoClicCtrl;
    }

    /** La fila que se está construyendo es de un jugador (rival/aliado): clic derecho ofrece pestaña nueva. Lo escriben otras vistas antes de pintar la lista. */
    public boolean esFilaJugador;

    public JPanel tarjeta(String titulo, String valor, String pie) {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(128, 128, 128, 70), 1, true), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JLabel t1 = new JLabel(titulo.toUpperCase(Locale.ROOT)); t1.setFont(t1.getFont().deriveFont(Font.BOLD, 10.5f)); t1.setForeground(colorSecundario());
        JLabel v = new JLabel(valor); v.setFont(v.getFont().deriveFont(Font.BOLD, 18f));
        JLabel t2 = new JLabel(pie); t2.setFont(t2.getFont().deriveFont(Font.PLAIN, 10.5f)); t2.setForeground(colorSecundario());
        p.add(t1, BorderLayout.NORTH); p.add(v, BorderLayout.CENTER); p.add(t2, BorderLayout.SOUTH);
        return p;
    }

    /** Una fila «nombre ····· barra ····· valor» para las listas laterales. */
    public JPanel filaBarra(String nombre, double fraccion, String valor, Color color, String tooltip) { return filaBarra(nombre, fraccion, valor, color, tooltip, null); }

    public JPanel filaBarra(String nombre, double fraccion, String valor, Color color, String tooltip, Runnable alClicar) { return filaBarra(null, nombre, fraccion, valor, color, tooltip, alClicar); }

    public JPanel filaBarra(Icon icono, String nombre, double fraccion, String valor, Color color, String tooltip, Runnable alClicar) {
        // Fila 111 / decisión 7: esFilaJugador es un campo COMPARTIDO de Listas (una sola instancia pinta todas
        // las listas de la ventana), así que hay que fijar su valor AQUÍ, al construir esta fila, y no leerlo
        // dentro del clic derecho: ese clic llega mucho después (cuando el usuario lo pide), y para entonces
        // quien pintó la lista ya devolvió esFilaJugador a false en su finally. Leído en el momento del clic,
        // el menú «Abrir perfil en pestaña nueva» casi nunca salía (bug de la 1.1).
        boolean filaJugador = esFilaJugador;
        JPanel f = new JPanel(new BorderLayout(6, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int x0 = 130, x1 = getWidth() - 62;
                if (x1 <= x0) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(128, 128, 128, 40));
                g2.fillRoundRect(x0, getHeight() / 2 - 4, x1 - x0, 8, 6, 6);
                g2.setColor(color);
                g2.fillRoundRect(x0, getHeight() / 2 - 4, (int) ((x1 - x0) * Math.max(0, Math.min(1, fraccion))), 8, 6, 6);
                g2.dispose();
            }
        };
        f.setOpaque(false);
        f.setAlignmentX(0f);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        f.setPreferredSize(new Dimension(10, 22));
        JLabel n = new JLabel(nombre, icono, SwingConstants.LEFT); n.setIconTextGap(5); n.setPreferredSize(new Dimension(126, 20));
        JLabel v = new JLabel(valor, SwingConstants.RIGHT); v.setPreferredSize(new Dimension(58, 18)); v.setFont(v.getFont().deriveFont(Font.BOLD));
        f.add(n, BorderLayout.WEST); f.add(v, BorderLayout.EAST);
        if (tooltip != null) { f.setToolTipText(tooltip); n.setToolTipText(tooltip); v.setToolTipText(tooltip); }
        if (alClicar != null) {
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) { marcarUltimoClicCtrl.accept(e.isControlDown() || SwingUtilities.isMiddleMouseButton(e)); alClicar.run(); marcarUltimoClicCtrl.accept(false); }   // botón central = pestaña nueva
                }
                @Override public void mousePressed(MouseEvent e) { menu(e); }
                @Override public void mouseReleased(MouseEvent e) { menu(e); }
                void menu(MouseEvent e) {
                    if (!e.isPopupTrigger() || !filaJugador) return;
                    JPopupMenu pm = new JPopupMenu();
                    JMenuItem it = new JMenuItem(t("Abrir perfil en pestaña nueva", "Open profile in a new tab"));
                    it.addActionListener(a -> { marcarUltimoClicCtrl.accept(true); alClicar.run(); marcarUltimoClicCtrl.accept(false); });
                    pm.add(it);
                    pm.show(e.getComponent(), e.getX(), e.getY());
                }
            };
            for (JComponent c : new JComponent[]{ f, n, v }) { c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); c.addMouseListener(ma); }
            n.setForeground(temaOscuroActivo ? new Color(0x7f, 0xb3, 0xe0) : new Color(0x2f, 0x5f, 0x8f));
        }
        return f;
    }

    /** Enlace «ver los N…» al pie de una lista recortada. */
    public JButton enlaceVerTodo(int total, Runnable abrir) {
        JButton b = new JButton(t("ver los ", "see all ") + total + "\u2026");
        b.setFocusable(false); b.setMargin(new Insets(0, 4, 0, 4)); b.putClientProperty("JButton.buttonType", "borderless");
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 11f));
        b.setForeground(temaOscuroActivo ? new Color(0x7f, 0xb3, 0xe0) : new Color(0x2f, 0x5f, 0x8f));
        b.setAlignmentX(0f);
        b.addActionListener(e -> abrir.run());
        return b;
    }

    /** Celda con icono y texto (tablas ordenables). */
    public record Celda(Icon icono, String texto, Runnable alClicar) implements Comparable<Celda> { @Override public String toString() { return texto; } @Override public int compareTo(Celda o) { return texto.compareToIgnoreCase(o.texto); } }
    /** Porcentaje con color (tablas ordenables): se ordena por valor y se pinta con el color del winrate. */
    public record Pct(double valor, int w, int n, boolean colorear) implements Comparable<Pct> { @Override public String toString() { return pct1(valor); } @Override public int compareTo(Pct o) { return Double.compare(valor, o.valor); } }

    /** Ventana con una tabla ordenable por cualquier columna: iconos, porcentajes coloreados, números a la derecha; doble clic en una fila ejecuta su acción si la tiene. */
    public void mostrarTablaCompleta(String titulo, String[] columnas, List<Object[]> filas, int ordenInicial) {
        JDialog d = new JDialog(propietaria, titulo, false);
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { for (Object[] f : filas) if (f[c] != null) return f[c].getClass(); return Object.class; }
        };
        for (Object[] f : filas) modelo.addRow(f);
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(tabla.getRowHeight() + 8);
        tabla.setAutoCreateRowSorter(true);
        tabla.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer render = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel lab = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, col);
                lab.setIcon(null); lab.setForeground(sel ? tb.getSelectionForeground() : tb.getForeground()); lab.setFont(tb.getFont());
                if (value instanceof Celda c) { lab.setText(c.texto()); lab.setIcon(c.icono()); lab.setIconTextGap(6); lab.setHorizontalAlignment(SwingConstants.LEFT); if (c.alClicar() != null) lab.setFont(tb.getFont().deriveFont(Font.BOLD)); }
                else if (value instanceof Pct pc) { lab.setText(pct1(pc.valor())); lab.setHorizontalAlignment(SwingConstants.RIGHT); if (pc.colorear() && !sel) lab.setForeground(colorWr(pc.w(), pc.n())); lab.setFont(tb.getFont().deriveFont(Font.BOLD)); }
                else if (value instanceof Number nn) { lab.setText(nn instanceof Double db ? pct1(db) : miles(nn.longValue())); lab.setHorizontalAlignment(SwingConstants.RIGHT); }
                else { lab.setText(value == null ? "" : String.valueOf(value)); lab.setHorizontalAlignment(SwingConstants.LEFT); }
                return lab;
            }
        };
        for (int c = 0; c < columnas.length; c++) tabla.getColumnModel().getColumn(c).setCellRenderer(render);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(220);
        for (int c = 1; c < columnas.length; c++) tabla.getColumnModel().getColumn(c).setPreferredWidth(90);
        if (ordenInicial >= 0 && ordenInicial < columnas.length) tabla.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(ordenInicial, SortOrder.DESCENDING)));
        tabla.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                int vr = tabla.rowAtPoint(e.getPoint()); if (vr < 0) return;
                Object v0 = modelo.getValueAt(tabla.convertRowIndexToModel(vr), 0);
                if (v0 instanceof Celda c && c.alClicar() != null) { c.alClicar().run(); }
            }
        });
        JScrollPane sp = new JScrollPane(tabla);
        sp.setBorder(null);
        JLabel pie = new JLabel(t("Clic en una cabecera: ordenar · doble clic en una fila con negrita: abrir", "Click a header: sort · double-click a bold row: open"));
        pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f)); pie.setForeground(colorSecundario()); pie.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        d.add(sp, BorderLayout.CENTER); d.add(pie, BorderLayout.SOUTH);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setSize(Math.min(760, 260 + 100 * columnas.length), Math.min(720, 90 + (tabla.getRowHeight() + 1) * (filas.size() + 1)));
        d.setLocationRelativeTo(propietaria);
        d.setVisible(true);
    }

    /** Ventana aparte con una lista entera (misma pintura que la recortada), con scroll. */
    public void mostrarListaCompleta(String titulo, java.util.function.Consumer<JPanel> rellenar) {
        JDialog d = new JDialog(propietaria, titulo, false);
        PanelScrollable cuerpo = new PanelScrollable();
        cuerpo.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        cuerpo.add(tituloSeccion(titulo));
        rellenar.accept(cuerpo);
        JScrollPane sp = new JScrollPane(cuerpo, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        d.add(sp);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setSize(460, Math.min(720, 60 + 24 * cuerpo.getComponentCount()));
        d.setLocationRelativeTo(propietaria);
        d.setVisible(true);
    }
}
