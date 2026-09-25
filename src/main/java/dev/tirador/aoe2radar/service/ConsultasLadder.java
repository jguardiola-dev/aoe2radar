package dev.tirador.aoe2radar.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.tirador.aoe2radar.model.LadderHist;
import dev.tirador.aoe2radar.model.LadderRow;

import static dev.tirador.aoe2radar.sfrdata.Ladder.clanes;
import static dev.tirador.aoe2radar.sfrdata.Ladder.hist;
import static dev.tirador.aoe2radar.util.Formato.fmtTop;
import static dev.tirador.aoe2radar.util.I18n.t;

/** Consultas sobre los resúmenes del ladder ya cargados: percentiles de rango y rating, y clanes. */
public final class ConsultasLadder {
    private ConsultasLadder() {}

    public static final int BIN_LADDER = 25;

    /** Nombre legible de un ladder («rm_1v1» → «1v1 Random Map»): lo usan Ratings y la ficha de perfil. */
    public static String ladderNombre(String id) {
        return switch (id) {
            case "rm_1v1" -> "1v1 Random Map"; case "rm_team" -> t("Equipos Random Map", "Team Random Map");
            case "ew_1v1" -> "1v1 Empire Wars"; case "ew_team" -> t("Equipos Empire Wars", "Team Empire Wars");
            default -> id;
        };
    }

    /** Nombre de la familia de ladders («rm»/«ew») que elige el combo de Ratings. */
    public static String familiaNombre(String f) { return "ew".equals(f) ? "Empire Wars" : "Random Map"; }

    /** «Top 1,30 %» a partir del rango del companion (entre TODOS los jugadores del ladder). */
    public static String percentilRango(String lb, Integer rank) {
        LadderHist h = hist(lb);
        if (h == null || rank == null || rank <= 0 || h.total() <= 0) return null;
        return fmtTop(100.0 * rank / h.total());
    }

    /** Percentil por rating sobre la campana elegida (todos o activos): jugadores por encima / total. */
    public static String percentilRating(String lb, boolean activos, int rating) {
        LadderHist h = hist(lb, activos);
        if (h == null || rating <= 0 || h.total() <= 0) return null;
        int[] bins = h.bins();
        int idx = Math.floorDiv(rating - h.min(), BIN_LADDER);
        double encima;
        if (idx < 0) encima = h.total();
        else if (idx >= bins.length) encima = 0;
        else {
            encima = 0;
            for (int i = idx + 1; i < bins.length; i++) encima += bins[i];
            double dentro = (h.min() + (idx + 1) * BIN_LADDER - rating - 0.5) / BIN_LADDER;   // parte del bin por encima del rating
            encima += bins[idx] * Math.max(0, Math.min(1, dentro));
        }
        return fmtTop(100.0 * encima / h.total());
    }

    public static String clanLimpio(String s) { return s == null ? "" : s.trim().replaceAll("^[\\[\\(]|[\\]\\)]$", "").trim().toLowerCase(); }

    public static List<LadderRow> miembrosClan(String tag) {
        String t0 = clanLimpio(tag);
        if (t0.isEmpty()) return List.of();
        for (Map.Entry<String, List<LadderRow>> en : clanes.entrySet()) if (clanLimpio(en.getKey()).equals(t0)) return en.getValue();
        return List.of();
    }

    public static List<Map.Entry<String, Integer>> sugerirClanes(String q) {
        String q0 = clanLimpio(q);
        List<Map.Entry<String, Integer>> l = new ArrayList<>();
        for (Map.Entry<String, List<LadderRow>> en : clanes.entrySet())
            if (q0.isEmpty() || clanLimpio(en.getKey()).contains(q0)) l.add(Map.entry(en.getKey(), en.getValue().size()));
        l.sort((a, b) -> { int c = b.getValue() - a.getValue(); return c != 0 ? c : a.getKey().compareToIgnoreCase(b.getKey()); });
        return l.size() > 12 ? l.subList(0, 12) : l;
    }
}
