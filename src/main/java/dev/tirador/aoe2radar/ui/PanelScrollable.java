package dev.tirador.aoe2radar.ui;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/** Un panel apilado que ocupa el ancho del visor y solo hace scroll vertical. */
public class PanelScrollable extends JPanel implements Scrollable {
    public PanelScrollable() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(16, r.height - 16); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
