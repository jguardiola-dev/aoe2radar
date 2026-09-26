package dev.tirador.aoe2radar.util;

public final class I18n {
    private I18n() {}

    public static String IDIOMA = "es";   // "es"/"en"; se fija en main antes de crear la UI

    /** Texto según el idioma activo. */
    public static String t(String es, String en) { return "en".equals(IDIOMA) ? en : es; }
}
