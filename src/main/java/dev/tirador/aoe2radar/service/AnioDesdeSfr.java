package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.sfrdata.EloNocturno;
import dev.tirador.aoe2radar.sfrdata.PerfilesSfr;
import dev.tirador.aoe2radar.util.Reloj;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.service.ProfileService.ACT_MAX_PAGINAS;
import static dev.tirador.aoe2radar.service.ReglasPartida.LADDERS_IDX;
import static dev.tirador.aoe2radar.service.ReglasPartida.modoDeLadder;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.lng;

/**
 * El año de un jugador desde el paquete nocturno de sfr-data (release «perfiles»), sin tocar la API: «nocturno
 * primero». Entiende los dos formatos de paquete (v1, con nombres dentro; v2, con índices de civs y mapas y los nombres
 * del ELO nocturno). Lo que aprende de paso (el país del paquete v1) lo apunta en el mapa de países de la app.
 */
public final class AnioDesdeSfr {
    private final EloNocturno elo;
    private final PerfilesSfr perfiles;
    private final Map<Long, String> paisDe;
    private final NombresJuego nombres;
    private final Reloj reloj;

    public AnioDesdeSfr(EloNocturno elo, PerfilesSfr perfiles, Map<Long, String> paisDe, NombresJuego nombres, Reloj reloj) {
        this.elo = elo; this.perfiles = perfiles; this.paisDe = paisDe; this.nombres = nombres; this.reloj = reloj;
    }

    /**
     * Carga el ELO nocturno (nombres y países del formato v2) y el paquete del jugador. null si no hay paquete o si el
     * jugador no está en él. nombreSiFalta: el nombre de la Actividad si el paquete no lo trae. Puede ir a la red
     * (sfr-data, no la API); un fallo sale como excepción.
     */
    public AnioSfr leer(long pid, String nombreSiFalta) throws Exception {
        elo.cargar();   // nombres y países (formato v2 los toma del índice nocturno)
        Map<String, Object> shard = perfiles.shard(pid);
        if (shard == null) return null;
        Actividad a = convertir(pid, shard, nombreSiFalta);
        String hasta = String.valueOf(shard.getOrDefault("hasta", perfiles.indice() == null ? "" : perfiles.indice().get("hasta")));
        String pais = paisDe.get(pid);
        return a == null ? null : new AnioSfr(a, hasta, pais);
    }

    /** Construye la Actividad (año completo) de un jugador a partir de su entrada en el paquete. null si no está en el alcance. */
    Actividad convertir(long pid, Map<String, Object> shard, String nombreSiFalta) {
        boolean v2 = shard.get("v") instanceof Number vn && vn.intValue() >= 2;
        List<Object> partidasRaw; String nombre = "", pais = "";
        List<Object> civsDic = v2 ? arr(shard.get("civs")) : List.of(), mapasDic = v2 ? arr(shard.get("mapas")) : List.of();
        if (v2) {
            Object j = shard.get("j");
            if (!(j instanceof Map<?, ?> jm) || jm.get(String.valueOf(pid)) == null) return null;
            partidasRaw = arr(jm.get(String.valueOf(pid)));
            String[] nn = elo.nombres.get(pid); if (nn != null) { nombre = nn[0]; pais = nn[1]; }
        } else {
            Object jugadores = shard.get("jugadores");
            if (!(jugadores instanceof Map<?, ?> jm)) return null;
            Object entrada = jm.get(String.valueOf(pid));
            if (!(entrada instanceof Map<?, ?> em)) return null;
            nombre = em.get("n") == null ? "" : String.valueOf(em.get("n")); pais = em.get("c") == null ? "" : String.valueOf(em.get("c"));
            partidasRaw = arr(em.get("m"));
        }
        if (!pais.isBlank()) paisDe.put(pid, pais);
        List<Match> partidas = new ArrayList<>();
        for (Object o : partidasRaw) {
            List<Object> f = arr(o);
            Match m = new Match();
            m.id = lng(f.get(0));
            long ini = lng(f.get(1)), fin = lng(f.get(2));
            m.started = ini > 0 ? Instant.ofEpochSecond(ini) : null;
            m.finished = fin > 0 ? Instant.ofEpochSecond(fin) : null;
            String lb = v2 ? (f.get(3) instanceof Number ln && ln.intValue() >= 0 && ln.intValue() < LADDERS_IDX.length ? LADDERS_IDX[ln.intValue()] : "?") : String.valueOf(f.get(3));
            m.mode = modoDeLadder(lb);
            String mapaClave = v2 ? (f.get(4) instanceof Number mn && mn.intValue() >= 0 && mn.intValue() < mapasDic.size() ? String.valueOf(mapasDic.get(mn.intValue())) : "") : String.valueOf(f.get(4));
            m.map = mapaClave.isBlank() || "unknown".equalsIgnoreCase(mapaClave) ? t("Mapa desconocido", "Unknown map") : nombres.mapa(mapaClave);
            m.mapaClave = mapaClave;
            for (Object jo : arr(f.get(5))) {
                List<Object> j = arr(jo);
                MatchPlayer mp = new MatchPlayer();
                mp.id = lng(j.get(0));
                String civK;
                if (v2) {   // [pid, civIdx, equipo, rating, diff, won]: nombres del índice nocturno
                    String[] nj = elo.nombres.get(mp.id); mp.name = nj != null ? nj[0] : "#" + mp.id;
                    civK = j.get(1) instanceof Number cn && cn.intValue() >= 0 && cn.intValue() < civsDic.size() ? String.valueOf(civsDic.get(cn.intValue())) : "";
                    mp.team = (int) lng(j.get(2)); mp.rating = j.get(3) instanceof Number n && n.intValue() > 0 ? n.intValue() : null; mp.ratingDiff = j.get(4) instanceof Number n2 ? n2.intValue() : null;
                    long won = lng(j.get(5)); mp.won = won < 0 ? null : won == 1;
                    if (j.size() > 6 && j.get(6) instanceof Number sl && sl.intValue() > 0) mp.slot = sl.intValue();
                } else {   // [pid, nombre, civ, equipo, rating, diff, won]
                    mp.name = String.valueOf(j.get(1)); civK = String.valueOf(j.get(2));
                    mp.team = (int) lng(j.get(3)); mp.rating = j.get(4) instanceof Number n ? n.intValue() : null; mp.ratingDiff = j.get(5) instanceof Number n2 ? n2.intValue() : null;
                    long won = lng(j.get(6)); mp.won = won < 0 ? null : won == 1;
                }
                mp.civ = civK.isBlank() ? null : nombres.civ(civK);
                if (mp.id == pid && (mp.name.isBlank() || mp.name.startsWith("#")) && !nombre.isBlank()) mp.name = nombre;
                m.players.add(mp);
            }
            m.refId = pid;
            partidas.add(m);
        }
        partidas.sort((a, b) -> { Instant x = a.started == null ? Instant.EPOCH : a.started, y = b.started == null ? Instant.EPOCH : b.started; return y.compareTo(x); });
        return new Actividad(pid, nombre.isBlank() ? nombreSiFalta : nombre, partidas, true, ACT_MAX_PAGINAS, reloj.ahoraMs());
    }
}
