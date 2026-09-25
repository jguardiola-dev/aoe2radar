package dev.tirador.aoe2radar.ui;

import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.Locale;

import static dev.tirador.aoe2radar.service.ProfileService.ACT_MIN;
import static dev.tirador.aoe2radar.ui.Componentes.colorWr;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Formato.pct1;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * Barras verticales con etiqueta y, si hay partidas suficientes, el winrate encima (semana/horas/meses de la
 * pestaña Perfil). Sale de la clase anidada del mismo nombre en SpoilerFreeRecs (la 1.1) tal cual, ahora de nivel
 * superior en {@code ui}. Público porque RegresionCapturas lo usa (como JComponent) para la zona a ignorar de la
 * gráfica por meses/semana, que depende de la fecha de hoy (ver docs/DEUDA.md).
 */
public final class BarrasActividad extends JPanel {
    final String titulo; String[] etiquetas = new String[0], tips = null; int[] valores = new int[0], ganadas = new int[0], conRes = new int[0];

    BarrasActividad(String titulo) { this.titulo = titulo; setOpaque(false); ToolTipManager.sharedInstance().registerComponent(this); }

    void datos(String[] e, int[] v, int[] w, int[] n) { datos(e, v, w, n, null); }
    void datos(String[] e, int[] v, int[] w, int[] n, String[] t) { etiquetas = e; valores = v; ganadas = w; conRes = n; tips = t; repaint(); }

    int barraEn(Point p) {
        if (valores.length == 0) return -1;
        int ml = 8, mr = 8, w = getWidth();
        double sx = (double) (w - ml - mr) / valores.length;
        int i = (int) ((p.x - ml) / sx);
        return i < 0 || i >= valores.length ? -1 : i;
    }

    @Override public String getToolTipText(MouseEvent e) {
        int i = barraEn(e.getPoint());
        if (i < 0) return null;
        String base = tips != null ? tips[i] : etiquetas[i].isEmpty() ? String.valueOf(i) : etiquetas[i];
        return base + ": " + valores[i] + t(" partidas", " games") + (conRes[i] >= ACT_MIN ? " · WR " + pct1(100.0 * ganadas[i] / conRes[i]) : "");
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
        int w = getWidth(), h = getHeight(), ml = 8, mr = 8, mt = 42, mb = 18;
        Font base = g2.getFont();
        g2.setFont(base.deriveFont(Font.BOLD, 12f)); g2.setColor(fg);
        g2.drawString(titulo, 4, 14);
        g2.setFont(base.deriveFont(10.5f)); g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 160 : 205));
        g2.drawString(t("barras: partidas · número: % de victorias", "bars: games · number: win rate %"), 4, 27);
        if (valores.length == 0) { g2.dispose(); return; }
        int max = 1; for (int v : valores) max = Math.max(max, v);
        double sx = (double) (w - ml - mr) / valores.length;
        Color barra = temaOscuroActivo ? new Color(0x5a, 0x8f, 0xc7) : new Color(0x3b, 0x6e, 0xa8);
        g2.setFont(base.deriveFont(9f));
        for (int i = 0; i < valores.length; i++) {
            int x = ml + (int) (i * sx), bw = Math.max(2, (int) sx - 2);
            int bh = (int) ((h - mt - mb) * valores[i] / (double) max);
            g2.setColor(barra);
            g2.fillRoundRect(x, h - mb - bh, bw, bh, 3, 3);
            if (conRes[i] >= ACT_MIN && sx >= 22) {
                double wr = 100.0 * ganadas[i] / conRes[i];
                g2.setColor(colorWr(ganadas[i], conRes[i]));
                String s = String.format(Locale.ROOT, "%.0f", wr) + "%";
                g2.drawString(s, x + bw / 2 - g2.getFontMetrics().stringWidth(s) / 2, h - mb - bh - 3);
            }
            if (!etiquetas[i].isEmpty()) { g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 170 : 210)); g2.drawString(etiquetas[i], x + bw / 2 - g2.getFontMetrics().stringWidth(etiquetas[i]) / 2, h - 5); }
        }
        g2.dispose();
    }
}
