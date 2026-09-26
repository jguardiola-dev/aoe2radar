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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * Calendario de un año tipo GitHub: una celda por día, más oscura cuantas más partidas. Sale de la clase anidada
 * del mismo nombre en SpoilerFreeRecs (la 1.1) tal cual, ahora de nivel superior en {@code ui}. Público porque
 * RegresionCapturas lo usa (como JComponent) para calcular la zona a ignorar en las capturas (depende de la
 * fecha de hoy, ver docs/DEUDA.md).
 */
public final class CalendarioPanel extends JPanel {
    Map<LocalDate, Integer> porDia = Map.of(); LocalDate inicio, fin; int max = 1;
    int celda = 11, hueco = 2, ml = 30, mt = 34;

    CalendarioPanel() { setOpaque(false); ToolTipManager.sharedInstance().registerComponent(this); }

    void datos(Map<LocalDate, Integer> d, LocalDate ini, LocalDate f) {
        porDia = d; fin = f;
        inicio = ini.minusDays(ini.getDayOfWeek().getValue() - 1);   // arranca en lunes
        max = 1; for (int v : d.values()) max = Math.max(max, v);
        repaint();
    }

    LocalDate diaEn(Point p) {
        if (inicio == null || p.x < ml || p.y < mt) return null;
        int col = (p.x - ml) / (celda + hueco), fila = (p.y - mt) / (celda + hueco);
        if (fila < 0 || fila > 6) return null;
        LocalDate d = inicio.plusDays(col * 7L + fila);
        return d.isAfter(fin) ? null : d;
    }

    @Override public String getToolTipText(MouseEvent e) {
        LocalDate d = diaEn(e.getPoint());
        if (d == null) return null;
        int n = porDia.getOrDefault(d, 0);
        return d.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES"))) + ": " + n + t(" partidas", " games");
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (inicio == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
        int w = getWidth();
        long dias = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1;
        int columnas = (int) ((dias + 6) / 7);
        celda = Math.max(8, Math.min(13, (w - ml - 8) / columnas - hueco));
        Font base = g2.getFont();
        g2.setFont(base.deriveFont(Font.BOLD, 13f)); g2.setColor(fg);
        g2.drawString(t("Actividad: partidas por día, último año", "Activity: games per day, last year") + "  ·  " + t("máximo ", "max ") + max + t(" en un día", " in a day"), 4, 14);
        g2.setFont(base.deriveFont(9f));
        String[] etq = "en".equals(IDIOMA) ? new String[]{ "Mon", "", "Wed", "", "Fri", "", "Sun" } : new String[]{ "Lun", "", "Mié", "", "Vie", "", "Dom" };
        g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 160 : 205));
        for (int f = 0; f < 7; f++) if (!etq[f].isEmpty()) g2.drawString(etq[f], 4, mt + f * (celda + hueco) + celda - 2);
        Color verde = temaOscuroActivo ? new Color(0x4c, 0xaf, 0x50) : new Color(0x2e, 0x7d, 0x32);
        int mesPrevio = -1;
        for (int c = 0; c < columnas; c++) {
            for (int f = 0; f < 7; f++) {
                LocalDate d = inicio.plusDays(c * 7L + f);
                if (d.isAfter(fin)) break;
                int x = ml + c * (celda + hueco), y = mt + f * (celda + hueco);
                if (f == 0 || c == 0) {
                    if (d.getMonthValue() != mesPrevio && d.getDayOfMonth() <= 7) {
                        mesPrevio = d.getMonthValue();
                        g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 160 : 205));
                        g2.drawString(d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES")), x, mt - 4);
                    }
                }
                int n = porDia.getOrDefault(d, 0);
                if (n == 0) g2.setColor(new Color(128, 128, 128, temaOscuroActivo ? 45 : 30));
                else { double k = 0.25 + 0.75 * Math.min(1, Math.sqrt(n / (double) max)); g2.setColor(new Color(verde.getRed(), verde.getGreen(), verde.getBlue(), (int) (255 * k))); }
                g2.fillRoundRect(x, y, celda, celda, 3, 3);
            }
        }
        g2.dispose();
    }
}
