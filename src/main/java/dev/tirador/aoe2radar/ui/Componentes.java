package dev.tirador.aoe2radar.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;

import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Piezas de interfaz sueltas que usan varias vistas (títulos de sección, colores del tema, la paleta del
 * ladder, redondeos de escala): sin estado propio, cada llamada es independiente. Vive en ui porque toca
 * Swing/AWT, pero ninguna vista concreta es su dueña.
 */
public final class Componentes {
    private Componentes() { }

    /** Colores fijos del ladder, en el mismo orden para todas las vistas que comparan varios jugadores. */
    public static final Color[] PALETA_LADDER = { new Color(0xe5, 0x73, 0x73), new Color(0x81, 0xc7, 0x84), new Color(0xff, 0xb7, 0x4d), new Color(0xba, 0x68, 0xc8), new Color(0x4d, 0xd0, 0xe1),
            new Color(0xff, 0xf1, 0x76), new Color(0xf0, 0x62, 0x92), new Color(0xa1, 0x88, 0x7f), new Color(0x90, 0xa4, 0xae), new Color(0x7c, 0xb3, 0x42) };

    public static JLabel tituloSeccion(String texto) { return tituloSeccion(texto, null); }

    public static JLabel tituloSeccion(String texto, String tooltip) {
        JLabel l = new JLabel(tooltip == null ? texto : texto + "  \u24D8");
        l.setAlignmentX(0f);
        if (tooltip != null) l.setToolTipText("<html><div style='width:320px'>" + escapeHtml(tooltip) + "</div></html>");
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        l.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        return l;
    }

    /** Gris «secundario» legible en los dos temas: claro en oscuro, oscuro en claro (títulos de recuadro, leyendas, subtítulos). */
    public static Color colorSecundario() { return temaOscuroActivo ? new Color(0x9a, 0x9a, 0x9a) : new Color(0x5a, 0x5a, 0x5a); }
    public static String colorSecundarioHex() { return colorHex(colorSecundario()); }

    public static String colorHex(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }

    /** Color de un winrate, igual en toda la app: verde de 52 % en adelante, rojo de 48 % para abajo, el resto en el color del texto. */
    public static Color colorWr(int w, int n) {
        if (n <= 0) return Color.GRAY;
        double wr = 100.0 * w / n;
        if (wr >= 52) return temaOscuroActivo ? new Color(0x7c, 0xc9, 0x7f) : new Color(0x2e, 0x7d, 0x32);
        if (wr <= 48) return temaOscuroActivo ? new Color(0xe5, 0x73, 0x73) : new Color(0xc6, 0x28, 0x28);
        Color fg = javax.swing.UIManager.getColor("Label.foreground");
        return fg == null ? Color.GRAY : fg;
    }

    public static JPanel listaVertical() { JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setAlignmentX(0f); return p; }

    /** Todos los scroll de un panel, arriba del todo (al abrir una vista). */
    public static void subirArriba(Container c) {
        if (c == null) return;
        for (Component x : c.getComponents()) {
            if (x instanceof JScrollPane sp) { sp.getVerticalScrollBar().setValue(0); sp.getHorizontalScrollBar().setValue(0); }
            if (x instanceof Container cc) subirArriba(cc);
        }
    }

    public static double pasoBonito(double bruto) {
        if (bruto <= 0) return 1;
        double p = Math.pow(10, Math.floor(Math.log10(bruto)));
        double f = bruto / p;
        return (f < 1.5 ? 1 : f < 3.5 ? 2 : f < 7.5 ? 5 : 10) * p;
    }

    public static String etiquetaK(double v) { return v >= 1000 ? (v % 1000 == 0 ? (long) (v / 1000) + "k" : String.format(java.util.Locale.ROOT, "%.1fk", v / 1000)) : String.valueOf((long) v); }

    public static String colorVivoHex() {
        return temaOscuroActivo ? "ff6b5e" : "d32f2f";
    }

    /** Dorado legible para la fila EN DIRECTO de la tabla, según el tema. */
    public static Color colorVivoTabla() {
        return temaOscuroActivo ? new Color(0xFF, 0xC9, 0x4D) : new Color(0xB0, 0x78, 0x00);
    }

    /** Icono cuadrado de lado {@code lado}: el logo entero, centrado y a escala
     *  proporcional sobre fondo transparente. Nunca se estira ni se recorta. */
    public static Image iconoCuadrado(Image src, int lado) {
        int w = src.getWidth(null), h = src.getHeight(null);
        if (w <= 0 || h <= 0) return src;
        double f = Math.min(lado / (double) w, lado / (double) h);
        int nw = Math.max(1, (int) Math.round(w * f));
        int nh = Math.max(1, (int) Math.round(h * f));
        BufferedImage out = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, (lado - nw) / 2, (lado - nh) / 2, nw, nh, null);
        g.dispose();
        return out;
    }
}
