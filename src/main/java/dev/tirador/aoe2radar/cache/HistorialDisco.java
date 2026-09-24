package dev.tirador.aoe2radar.cache;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.util.Json;

import static dev.tirador.aoe2radar.sfrdata.Ladder.LADDER_DIR;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.firstNonNull;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/** Historial de partidas por jugador: caché en memoria y en disco (perfiles/…). */
public final class HistorialDisco {
    private HistorialDisco() {}

    public static final int ACT_DIAS = 365;
    public static final Map<Long, Actividad> ACTIVIDAD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Path PERFILES_DIR = LADDER_DIR.resolve("perfiles");

    static String jsonTxt(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\"); case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
                default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
        }
        return b.append('"').toString();
    }

    /** Guarda el historial en sfrdata/perfiles/<pid>.json (solo lo que usa el perfil). */
    public static void guardarActividad(Actividad a) {
        try {
            Files.createDirectories(PERFILES_DIR);
            StringBuilder b = new StringBuilder("{\"pid\":").append(a.pid()).append(",\"nombre\":").append(jsonTxt(a.nombre())).append(",\"completo\":").append(a.completo())
                    .append(",\"paginas\":").append(a.paginas()).append(",\"ms\":").append(a.ms()).append(",\"partidas\":[");
            boolean primera = true;
            for (Match m : a.partidas()) {
                if (m.started == null) continue;
                if (!primera) b.append(',');
                primera = false;
                b.append("{\"id\":").append(m.id).append(",\"s\":").append(m.started.toEpochMilli()).append(",\"f\":").append(m.finished == null ? "null" : String.valueOf(m.finished.toEpochMilli()))
                 .append(",\"mode\":").append(jsonTxt(m.mode)).append(",\"map\":").append(jsonTxt(m.map)).append(",\"players\":[");
                boolean pp = true;
                for (MatchPlayer p : m.players) {
                    if (!pp) b.append(',');
                    pp = false;
                    b.append("{\"id\":").append(p.id).append(",\"n\":").append(jsonTxt(p.name)).append(",\"c\":").append(jsonTxt(p.civ)).append(",\"t\":").append(p.team)
                     .append(",\"w\":").append(p.won == null ? "null" : String.valueOf(p.won)).append(",\"r\":").append(p.rating == null ? "null" : String.valueOf(p.rating))
                     .append(",\"d\":").append(p.ratingDiff == null ? "null" : String.valueOf(p.ratingDiff)).append('}');
                }
                b.append("]}");
            }
            b.append("]}");
            Files.writeString(PERFILES_DIR.resolve(a.pid() + ".json"), b.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) { log("perfil: no se pudo guardar " + a.pid() + ": " + causa(ex)); }
    }

    /** Lee el historial guardado, o null. Descarta lo de hace más de un año. */
    public static Actividad cargarActividad(long pid) {
        try {
            Path p = PERFILES_DIR.resolve(pid + ".json");
            if (!Files.exists(p)) return null;
            Map<String, Object> j = obj(Json.parse(Files.readString(p, StandardCharsets.UTF_8)));
            Instant limite = Instant.now().minus(Duration.ofDays(ACT_DIAS));
            List<Match> lista = new ArrayList<>();
            for (Object o : arr(j.get("partidas"))) {
                Map<String, Object> mj = obj(o);
                Match m = new Match();
                m.id = lng(mj.get("id"));
                m.started = Instant.ofEpochMilli(lng(mj.get("s")));
                if (m.started.isBefore(limite)) continue;
                m.finished = mj.get("f") == null ? null : Instant.ofEpochMilli(lng(mj.get("f")));
                m.mode = String.valueOf(firstNonNull(mj.get("mode"), "?"));
                m.map = String.valueOf(firstNonNull(mj.get("map"), "?"));
                for (Object po : arr(mj.get("players"))) {
                    Map<String, Object> pj = obj(po);
                    MatchPlayer mp = new MatchPlayer();
                    mp.id = lng(pj.get("id")); mp.name = pj.get("n") == null ? "" : String.valueOf(pj.get("n")); mp.civ = pj.get("c") == null ? null : String.valueOf(pj.get("c"));
                    mp.team = (int) lng(pj.get("t")); mp.won = pj.get("w") instanceof Boolean w ? w : null;
                    mp.rating = pj.get("r") instanceof Number n ? n.intValue() : null; mp.ratingDiff = pj.get("d") instanceof Number n ? n.intValue() : null;
                    m.players.add(mp);
                }
                lista.add(m);
            }
            return new Actividad(pid, String.valueOf(firstNonNull(j.get("nombre"), "")), lista, Boolean.TRUE.equals(j.get("completo")), (int) lng(j.get("paginas")), lng(j.get("ms")));
        } catch (Exception ex) { log("perfil: caché ilegible " + pid + ": " + causa(ex)); return null; }
    }
}
