package dev.tirador.aoe2radar.ui;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.BorderFactory;
import javax.swing.GrayFilter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.AlphaComposite;

import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;

/**
 * Las piezas de pintura del árbol del Tech tree que no dependen de la vista (sin estado propio, solo dibujan):
 * la placa de cada edad, el borde-placa del edificio, la rejilla que une unidades con sus mejoras y el icono
 * apagado de lo que la civ no tiene. Separadas de TechTreeView (que ya pasaba de las ~1000 líneas orientativas
 * de la fase 3) sin cambiar ni un píxel de cómo pintan.
 */
final class TechTreeArbol {
    private TechTreeArbol() { }

    /** Versión apagada de un icono (gris y translúcida) para lo que la civ no tiene. */
    static Image imagenApagada(Image img) {
        Image gris = GrayFilter.createDisabledImage(img);
        int w = img.getWidth(null), h = img.getHeight(null);
        if (w <= 0 || h <= 0) return gris;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g2.drawImage(gris, 0, 0, null);
        g2.dispose();
        return out;
    }

    /** Placa de edad: degradado oscuro y borde dorado fino, como la tarjeta de cada edificio. */
    static class PlacaTT extends JPanel {
        final int edad;
        PlacaTT(int edad) { this.edad = edad; setOpaque(false); setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 4)); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 6, h = getHeight() - 6;
            g2.setPaint(new GradientPaint(0, 3, temaOscuroActivo ? new Color(0x33, 0x2f, 0x27) : new Color(0xf6, 0xef, 0xdd), 0, 3 + h, temaOscuroActivo ? new Color(0x24, 0x22, 0x1e) : new Color(0xe8, 0xdd, 0xc2)));
            g2.fillRoundRect(2, 3, w, h, 10, 10);
            g2.setColor(new Color(0xc9, 0x8a, 0x3b, temaOscuroActivo ? 120 : 150));
            g2.drawRoundRect(2, 3, w, h, 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Borde-placa para la cabecera de edificio: degradado oscuro, borde dorado fino y esquinas redondeadas. */
    static class PlacaBorde extends javax.swing.border.AbstractBorder {
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {   // solo el contorno: el fondo lo pinta el propio componente, debajo de su contenido
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0xc9, 0x8a, 0x3b, temaOscuroActivo ? 150 : 170));
            g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 12, 12);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(4, 6, 4, 6); }
        @Override public boolean isBorderOpaque() { return false; }
    }

    /** La rejilla de un edificio: GridLayout con hueco vertical y, por debajo de los iconos, las líneas que unen cada unidad con su mejora (misma columna, fila siguiente con contenido, a como mucho una edad). */
    static class RejillaTT extends JPanel {
        final int filas, cols;
        RejillaTT(int filas, int cols) { super(new GridLayout(filas, cols, 2, TechTreeView.TT_VGAP)); this.filas = filas; this.cols = cols; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Component[] cs = getComponents();
            if (cs.length < filas * cols) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(2f));
            // de la placa del edificio a su icono: una línea dorada por la columna 0 hasta la fila del edificio
            for (int r = 0; r < filas; r++) {
                Component b0 = cs[r * cols];
                if (b0 instanceof JLabel lb0 && lb0.getIcon() != null) {
                    g2.setColor(new Color(0xc9, 0x8a, 0x3b, temaOscuroActivo ? 110 : 130));
                    int x = b0.getX() + b0.getWidth() / 2;
                    if (b0.getY() > 2) g2.drawLine(x, 0, x, b0.getY() + 1);
                    break;
                }
            }
            for (int c = 1; c < cols; c++) {   // la columna 0 es el propio edificio
                int prev = -1;
                for (int r = 0; r < filas; r++) {
                    Component celda = cs[r * cols + c];
                    boolean lleno = celda instanceof JLabel l && (l.getIcon() != null || (l.getText() != null && !l.getText().isEmpty()));
                    if (!lleno) continue;
                    if (prev >= 0 && r - prev <= 2) {
                        Component arriba = cs[prev * cols + c];
                        boolean disp = celda.getForeground() == null || !(celda instanceof JLabel lb && "\u00D7".equals(lb.getText()));
                        Color marcoAbajo = celda instanceof JComponent jc && jc.getBorder() instanceof javax.swing.border.LineBorder lbrd ? lbrd.getLineColor() : Color.GRAY;
                        boolean noDisp = marcoAbajo.getAlpha() < 255;   // las no disponibles llevan marco rojo translúcido
                        g2.setColor(noDisp ? new Color(0xe5, 0x73, 0x73, 60) : (temaOscuroActivo ? new Color(0xc9, 0x8a, 0x3b, 120) : new Color(0x8a, 0x5e, 0x1e, 140)));
                        int x = arriba.getX() + arriba.getWidth() / 2;
                        g2.drawLine(x, arriba.getY() + arriba.getHeight() - 1, x, celda.getY() + 1);
                    }
                    prev = r;
                }
            }
            g2.dispose();
        }
    }
}
