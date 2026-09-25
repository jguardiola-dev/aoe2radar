package dev.tirador.aoe2radar.util;

import java.util.Locale;

import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import java.time.Duration;

public final class Formato {
    private Formato() {}

    public static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

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
