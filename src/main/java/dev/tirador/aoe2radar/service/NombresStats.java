package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.VentanaStats;

import java.util.Locale;

import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttData;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttNombreCiv;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Json.obj;

/**
 * Nombres legibles de Civ Stats y Tech tree (modo, ventana, tramo de ELO, civ, mapa): traducen las claves que
 * usa el companion («rm_1v1», «burmese», «0-1000») a texto para la UI. Movido tal cual desde SpoilerFreeRecs
 * para que las vistas de Civ Stats y Tech tree no tengan que importar sfrdata ni techtree directamente: les
 * basta con service. `claveCivDeNombre` no está aquí porque usa un mapa que solo existe en la vista del tech tree.
 */
public final class NombresStats {
    private NombresStats() {}

    /** Nombre del modo («rm_1v1» → «1v1 Random Map»), igual en toda la app. */
    public static String modoNombre(String m) {
        return switch (m) {
            case "rm_1v1" -> "1v1 Random Map"; case "rm_2v2" -> "2v2 Random Map"; case "rm_3v3" -> "3v3 Random Map"; case "rm_4v4" -> "4v4 Random Map";
            case "ew_1v1" -> "1v1 Empire Wars"; case "ew_team" -> t("Equipos Empire Wars", "Team Empire Wars");
            case "dm_1v1" -> "1v1 Deathmatch"; case "dm_team" -> t("Equipos Deathmatch", "Team Deathmatch");
            default -> m;
        };
    }

    /** Nombre de la ventana de tiempo («30» → «30 días»). */
    public static String ventanaNombre(String v) {
        return switch (v) {
            case "7" -> t("7 días", "7 days"); case "30" -> t("30 días", "30 days"); case "90" -> t("90 días", "90 days"); case "365" -> t("365 días", "365 days");
            case "parche" -> t("Parche actual", "Current patch"); default -> v;
        };
    }

    /** Nombre del tramo de ELO: «*» = Todos, «desde|hasta» = rango, o el tramo simple con guion en vez de rayita. */
    public static String tramoNombre(String tr) {
        if (tr == null || "*".equals(tr) || "*|*".equals(tr)) return t("Todos los ELO", "All ELO");
        if ("?".equals(tr)) return t("Sin rating", "Unrated");
        if (tr.contains("|")) {   // rango «desde|hasta» (cada extremo: clave de tramo o * = sin límite)
            String[] p = tr.split("\\|", -1);
            String lo = "*".equals(p[0]) ? null : p[0].contains("-") ? p[0].substring(0, p[0].indexOf('-')) : p[0].replace("+", "");
            String hi = "*".equals(p[1]) ? null : p[1].endsWith("+") ? null : p[1].contains("-") ? p[1].substring(p[1].indexOf('-') + 1) : p[1];
            if (lo != null && lo.equals("0")) lo = null;
            if (lo == null && hi == null) return t("Todos los ELO", "All ELO");
            if (lo == null) return "<" + hi;
            if (hi == null) return lo + "+";
            return lo + "\u2013" + hi;
        }
        return tr.endsWith("+") ? tr : tr.replace("-", "\u2013");
    }

    /** Nombre de civ a partir de la clave del companion («burmese»): el del tech tree si está cargado, si no capitalizado. */
    public static String nombreCivStats(String clave) {
        if (ttData != null) {
            for (String k : obj(ttData.get("civs")).keySet()) if (k.equalsIgnoreCase(clave)) return ttNombreCiv(k);
        }
        return clave.isEmpty() ? clave : Character.toUpperCase(clave.charAt(0)) + clave.substring(1);
    }

    /** Clave del tech tree («Burmese») para una clave del companion («burmese»), o null. */
    public static String claveTechTree(String claveStats) {
        if (ttData == null) return null;
        for (String k : obj(ttData.get("civs")).keySet()) if (k.equalsIgnoreCase(claveStats)) return k;
        return null;
    }

    /** Nombre de mapa legible a partir de la ventana (si trae el nombre) o de la clave («rm_arabia» → «Arabia»). */
    public static String nombreMapaStats(VentanaStats v, String clave) {
        if ("*".equals(clave)) return t("Todos los mapas", "All maps");
        String n = v == null ? null : v.nombresMapas().get(clave);
        if (n == null) { n = clave; for (String p : new String[]{ "rm_", "cm_", "ew_", "dm_" }) if (n.startsWith(p)) n = n.substring(p.length()); n = n.replace('_', ' ').replace('-', ' '); }
        if (n.startsWith("Cm ")) n = n.substring(3);
        return n;
    }

    /** Raíz de un nombre de civ sin acentos ni plural («Aztecas»/«Aztecs» → «azteca»/«aztec»), para comparar nombres en distinto idioma. */
    public static String raizCiv(String s) {
        String x = java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim();
        if (x.endsWith("es") && x.length() > 5) x = x.substring(0, x.length() - 2); else if (x.endsWith("s") && x.length() > 4) x = x.substring(0, x.length() - 1);
        return x;
    }
}
