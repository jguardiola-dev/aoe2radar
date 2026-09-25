package dev.tirador.aoe2radar.ui;

import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static dev.tirador.aoe2radar.ui.Componentes.pasoBonito;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * ELO tras cada partida de UN ladder (las últimas 100): línea con escala, máximo, mínimo y actual; tooltip por
 * partida con fecha y ±diff. Sale de la clase anidada del mismo nombre en SpoilerFreeRecs (la 1.1) tal cual, ahora
 * de nivel superior en {@code ui} porque la usan tanto PerfilView como el diálogo ampliado de la gráfica.
 */
public final class GraficaElo extends JPanel {
    List<long[]> puntos = List.of();   // {epochMs, rating, diff} de más antigua a más nueva
    String etiqueta = "";
    int ml = 40, mr = 44, mt = 18, mb = 16;

    GraficaElo() { setOpaque(false); ToolTipManager.sharedInstance().registerComponent(this); }

    void datos(List<long[]> p, String et) { puntos = p; etiqueta = et; repaint(); }

    int indiceEn(int x) {
        if (puntos.size() < 2) return -1;
        double sx = (double) (getWidth() - ml - mr) / (puntos.size() - 1);
        int i = (int) Math.round((x - ml) / sx);
        return i < 0 || i >= puntos.size() ? -1 : i;
    }

    @Override public String getToolTipText(MouseEvent e) {
        int i = indiceEn(e.getX());
        if (i < 0) return null;
        long[] p = puntos.get(i);
        String fecha = Instant.ofEpochMilli(p[0]).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES")));
        return "<html>" + fecha + "<br><b>" + p[1] + "</b>" + (p[2] != 0 ? " <span style='color:" + (p[2] > 0 ? "#3a9d5d" : "#c0392b") + "'>" + (p[2] > 0 ? "+" : "") + p[2] + "</span>" : "") + "<br><span style='color:gray'>" + t("partida ", "game ") + (i + 1) + t(" de ", " of ") + puntos.size() + "</span></html>";
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
        Color gris = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 120 : 190);
        int w = getWidth(), h = getHeight();
        Font base = g2.getFont();
        g2.setFont(base.deriveFont(11f)); g2.setColor(gris);
        g2.drawString(etiqueta, ml, 12);
        if (puntos.size() < 2) { g2.drawString(t("Sin partidas suficientes en este ladder", "Not enough games on this ladder"), ml, h / 2); g2.dispose(); return; }
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (long[] p : puntos) { min = Math.min(min, p[1]); max = Math.max(max, p[1]); }
        if (max - min < 40) { long c = (min + max) / 2; min = c - 20; max = c + 20; }
        long paso = (long) pasoBonito((max - min) / 3.0);
        double sy = (double) (h - mt - mb) / (max - min), sx = (double) (w - ml - mr) / (puntos.size() - 1);
        g2.setFont(base.deriveFont(9f));
        for (long v = (long) Math.ceil(min / (double) paso) * paso; v <= max; v += paso) {
            int y = (int) (h - mb - (v - min) * sy);
            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 35)); g2.drawLine(ml, y, w - mr, y);
            g2.setColor(gris); String sv = String.valueOf(v); g2.drawString(sv, ml - 4 - g2.getFontMetrics().stringWidth(sv), y + 4);
        }
        Color linea = temaOscuroActivo ? new Color(0xff, 0xc9, 0x4d) : new Color(0xb0, 0x78, 0x00);
        g2.setColor(linea); g2.setStroke(new BasicStroke(1.8f));
        int px = -1, py = -1;
        for (int i = 0; i < puntos.size(); i++) {
            int x = ml + (int) Math.round(i * sx), y = (int) (h - mb - (puntos.get(i)[1] - min) * sy);
            if (px >= 0) g2.drawLine(px, py, x, y);
            px = x; py = y;
        }
        long ultimo = puntos.get(puntos.size() - 1)[1];
        g2.fillOval(px - 3, py - 3, 6, 6);
        g2.setFont(base.deriveFont(Font.BOLD, 11f));
        g2.drawString(String.valueOf(ultimo), Math.min(px + 6, w - mr + 2), py + 4);
        g2.setFont(base.deriveFont(9f)); g2.setColor(gris);
        String etMax = t("máx ", "max ") + max, etMin = t("mín ", "min ") + min;
        g2.drawString(etMax, w - mr + 2, mt + 4); g2.drawString(etMin, w - mr + 2, h - mb);
        String fIni = Instant.ofEpochMilli(puntos.get(0)[0]).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES")));
        String fFin = Instant.ofEpochMilli(puntos.get(puntos.size() - 1)[0]).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES")));
        g2.drawString(fIni, ml, h - 3); g2.drawString(fFin, w - mr - g2.getFontMetrics().stringWidth(fFin), h - 3);
        g2.dispose();
    }
}
