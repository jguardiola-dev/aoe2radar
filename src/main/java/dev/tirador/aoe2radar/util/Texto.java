package dev.tirador.aoe2radar.util;

import java.util.ArrayList;
import java.util.List;

/** Utilidades de texto: tildes, nicks, nombres de fichero seguros, recortes y versiones. */
public final class Texto {
    private Texto() {}

    public static String sinTildes(String s) {
        return java.text.Normalizer.normalize(s.toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    /** minúsculas y sin acentos, para comparar nicks con tolerancia */
    public static String normalizarNick(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase().trim();
    }

    /** El comodín del desplegable («(cualquiera)» / «(any)», con o sin paréntesis, en cualquier idioma). */
    public static boolean esCualquiera(String s) {
        if (s == null) return true;
        String n = s.trim().toLowerCase().replace("(", "").replace(")", "");
        return n.isEmpty() || n.equals("cualquiera") || n.equals("any") || n.equals("null");
    }

    /** ¿«1.2.3» es mayor que «1.0»? Comparación numérica por tramos. */
    public static boolean versionMayor(String nueva, String actual) {
        String[] a = nueva.replaceAll("[^0-9.]", "").split("\\."), b = actual.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length && !b[i].isEmpty() ? Integer.parseInt(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    /** Variantes de un nick para casarlo con un canal: entero, sin tag de
     *  clan (última parte tras . | _ o espacio) y sin guiones bajos sueltos. */
    public static List<String> variantesNick(String nombre) {
        List<String> out = new ArrayList<>();
        String base = nombre.toLowerCase().replaceAll("\\[.*?\\]", "").trim();
        out.add(base.replaceAll("\\s+", ""));
        String[] partes = base.split("[.|_\\s]+");
        if (partes.length > 1) {
            String ultima = partes[partes.length - 1];
            if (ultima.length() >= 3) out.add(ultima);
        }
        String sinBajos = base.replaceAll("\\s+", "").replaceAll("_+$", "").replaceAll("^_+", "");
        if (!out.contains(sinBajos)) out.add(sinBajos);
        return out;
    }

    public static String recorta(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Sanea un nick para el nombre de archivo y quita los '_' sobrantes de
     *  los tags de clan ("[R1] 12Tirador" -> "R1_12Tirador"). */
    public static String limpiaNombre(String s) {
        String t = sanea(s).replaceAll("^_+|_+$", "");
        return t.isBlank() ? dev.tirador.aoe2radar.util.I18n.t("jugador", "player") : t;
    }

    public static String sanea(String s) {
        return s.replaceAll("[^A-Za-z0-9._+-]", "_").replaceAll("_{2,}", "_");
    }
}
