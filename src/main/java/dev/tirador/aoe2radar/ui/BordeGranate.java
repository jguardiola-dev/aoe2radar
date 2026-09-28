package dev.tirador.aoe2radar.ui;

import javax.swing.border.AbstractBorder;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

/** Borde fino granate con una barra algo más gruesa a la izquierda; el color se lee al pintar (Tema.granate()),
 *  así sigue al cambio de tema sin que nadie tenga que avisarle. */
final class BordeGranate extends AbstractBorder {
    @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
        g.setColor(Tema.granate());
        g.drawRect(x, y, w - 1, h - 1);
        g.fillRect(x, y, 4, h);
    }
    @Override public Insets getBorderInsets(Component c) { return new Insets(1, 4, 1, 1); }
    @Override public Insets getBorderInsets(Component c, Insets i) { i.set(1, 4, 1, 1); return i; }
}
