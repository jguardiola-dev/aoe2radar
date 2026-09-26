package dev.tirador.aoe2radar.util;

import java.awt.FontMetrics;
import java.util.Locale;

import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import java.time.Duration;

public final class Formato {
    private Formato() {}

    /** Recorta s hasta que quepa en px píxeles con la fuente dada (con «…»). Compartido por Live now y la
     *  watchlist: ninguna vista es su dueña, y no toca ningún estado. */
    public static String truncarPx(String s, FontMetrics fm, int px) {
        if (s == null) return "";
        if (fm == null || fm.stringWidth(s) <= px) return s;
        String puntos = "…";
        int lo = 0, hi = s.length();
        while (lo < hi) {   // bisección sobre la longitud
            int mid = (lo + hi + 1) / 2;
            if (fm.stringWidth(s.substring(0, mid) + puntos) <= px) lo = mid; else hi = mid - 1;
        }
        return lo <= 0 ? puntos : s.substring(0, lo) + puntos;
    }

    public static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Un decimal con la coma o el punto del idioma («3,5» / «3.5»), sin unidad. */
    public static String dec1(double x) { String s = String.format(Locale.ROOT, "%.1f", x); return "en".equals(IDIOMA) ? s : s.replace('.', ','); }
    public static String pct1(double p) { String s = String.format(Locale.ROOT, "%.1f", p); return ("en".equals(IDIOMA) ? s + "%" : s.replace('.', ',') + " %"); }

    /** «Top 0,75 %» / «Top 0.75%» según el idioma; por debajo de 0,01 %, «< 0,01 %». */
    public static String fmtTop(double p) {
        boolean es = !"en".equals(IDIOMA);
        String s = p < 0.005 ? "< 0.01" : String.format(Locale.ROOT, "%.2f", p);
        if (es) s = s.replace('.', ',');
        return "Top " + s + (es ? " %" : "%");
    }

    public static String miles(long n) { return java.text.NumberFormat.getIntegerInstance("en".equals(IDIOMA) ? Locale.US : Locale.forLanguageTag("es-ES")).format(n); }

    public static String reloj(Duration d) { long s = Math.max(0, d.getSeconds()); return String.format("%d:%02d", s / 60, s % 60); }
}
