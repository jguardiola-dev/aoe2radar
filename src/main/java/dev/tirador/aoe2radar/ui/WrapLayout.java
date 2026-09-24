package dev.tirador.aoe2radar.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** FlowLayout que envuelve a varias líneas y reporta la altura real, para
 *  que las filas de controles no se corten al estrechar la ventana. */
public class WrapLayout extends FlowLayout {
    public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

    @Override public Dimension preferredLayoutSize(Container target) { return layoutSize(target, true); }

    @Override public Dimension minimumLayoutSize(Container target) {
        Dimension d = layoutSize(target, false);
        d.width -= (getHgap() + 1);
        return d;
    }

    private Dimension layoutSize(Container target, boolean preferido) {
        synchronized (target.getTreeLock()) {
            int ancho = target.getSize().width;
            Container padre = target;
            while (ancho == 0 && padre.getParent() != null) {
                padre = padre.getParent();
                ancho = padre.getSize().width;
            }
            if (ancho < 120) ancho = Integer.MAX_VALUE;   // plegado o aún sin tamaño: una sola fila (si no, se quedaba la altura de 4 filas al volver: la watchlist «bajaba»)
            Insets in = target.getInsets();
            int maxAncho = ancho - (in.left + in.right + getHgap() * 2);
            Dimension dim = new Dimension(0, 0);
            int filaAncho = 0, filaAlto = 0;
            for (int i = 0; i < target.getComponentCount(); i++) {
                Component c = target.getComponent(i);
                if (!c.isVisible()) continue;
                Dimension d = preferido ? c.getPreferredSize() : c.getMinimumSize();
                if (filaAncho + d.width > maxAncho && filaAncho > 0) {
                    dim.width = Math.max(dim.width, filaAncho);
                    dim.height += filaAlto + getVgap();
                    filaAncho = 0;
                    filaAlto = 0;
                }
                filaAncho += d.width + getHgap();
                filaAlto = Math.max(filaAlto, d.height);
            }
            dim.width = Math.max(dim.width, filaAncho);
            dim.height += filaAlto + in.top + in.bottom + getVgap() * 2;
            return dim;
        }
    }
}
