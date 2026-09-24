package dev.tirador.aoe2radar.util;

import java.util.Locale;

import static dev.tirador.aoe2radar.util.I18n.IDIOMA;

public final class Formato {
    private Formato() {}

    public static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String pct1(double p) { String s = String.format(Locale.ROOT, "%.1f", p); return ("en".equals(IDIOMA) ? s + "%" : s.replace('.', ',') + " %"); }
}
