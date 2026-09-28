package dev.tirador.aoe2radar.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** La ⓘ dibujada (círculo con una «i»), en granate: no depende de que la fuente tenga el carácter U+24D8. */
final class IconoInfo implements Icon {
    private static final int LADO = 16;
    @Override public int getIconWidth() { return LADO; }
    @Override public int getIconHeight() { return LADO; }
    @Override public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(Tema.granate());
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(x + 1, y + 1, LADO - 3, LADO - 3);
            g2.setFont(c.getFont().deriveFont(Font.BOLD, 11f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("i", x + (LADO - fm.stringWidth("i")) / 2f, y + (LADO + fm.getAscent() - fm.getDescent()) / 2f - 0.5f);
        } finally {
            g2.dispose();
        }
    }
}
