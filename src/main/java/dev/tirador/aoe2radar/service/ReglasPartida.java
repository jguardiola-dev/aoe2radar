package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.tirador.aoe2radar.api.Freno.CONTROL;
import static dev.tirador.aoe2radar.cache.Anotaciones.nombreVisible;
import static dev.tirador.aoe2radar.cache.Vivos.VIVO_PARTIDA;
import static dev.tirador.aoe2radar.util.Texto.normalizarNick;

/** Reglas del dominio sobre partidas y jugadores: coincidencia de rival, modo de ladder, posiciones, tramos y franjas. */
public final class ReglasPartida {
    private ReglasPartida() {}

    /** ¿Alguien del bando contrario al sujeto coincide con el filtro? (contiene, sin acentos; respeta alias) */
    public static boolean rivalCoincide(Match m, String filtro) {
        if (filtro.isEmpty()) return true;
        MatchPlayer yo = null;
        for (MatchPlayer p : m.players) if (p.id == m.refId) yo = p;
        for (MatchPlayer p : m.players) {
            if (p.id == m.refId) continue;
            if (yo != null && m.players.size() > 2 && p.team == yo.team) continue;   // aliado: no cuenta
            if (normalizarNick(nombreVisible(p.id, p.name)).contains(filtro) || normalizarNick(p.name).contains(filtro)) return true;
        }
        return false;
    }

    public static String modoDeLadder(String lb) {
        return switch (lb) { case "rm_1v1" -> "1v1 Random Map"; case "rm_team" -> "Team Random Map"; case "ew_1v1" -> "1v1 Empire Wars"; case "ew_team" -> "Team Empire Wars"; case "dm_1v1" -> "1v1 Death Match"; case "dm_team" -> "Team Death Match"; default -> lb; };
    }
    public static final String[] LADDERS_IDX = { "rm_1v1", "rm_team", "ew_1v1", "ew_team", "dm_1v1", "dm_team" };
    public static final String[] MAPAS_SIN_POSICION_DEF = { "nomad", "pilgrims", "african_clearing", "african clearing", "claro africano", "coastal_forest", "coastal forest", "land_nomad", "nómada", "nomada" };
    /** ¿Mapa con inicio nómada o posiciones aleatorias? Lista por defecto ampliable desde control.json («mapas_sin_posicion»). */
    public static boolean mapaSinPosicion(Match m) {
        String k = ((m.mapaClave == null ? "" : m.mapaClave) + " " + (m.map == null ? "" : m.map)).toLowerCase(Locale.ROOT);
        for (String p : MAPAS_SIN_POSICION_DEF) if (k.contains(p)) return true;
        if (CONTROL.get("mapas_sin_posicion") instanceof List<?> l) for (Object o : l) if (k.contains(String.valueOf(o).toLowerCase(Locale.ROOT))) return true;
        return false;
    }
    /** «pocket», «flanco» o null. Regla (Jorge): en 3v3 y 4v4 ranked, dentro de cada equipo ordenados por color, los extremos son flancos y los de en medio pockets. No aplica a 1v1/2v2 ni a mapas sin posición. */
    public static String posicionEnEquipo(Match m, MatchPlayer yo) {
        if (m == null || yo == null || m.players.size() != 6 && m.players.size() != 8) return null;
        if (mapaSinPosicion(m)) return null;
        List<Integer> equipo = new ArrayList<>();
        Integer mio = null;
        for (MatchPlayer p : m.players) {
            if (p.team != yo.team) continue;
            Integer c = p.color != null ? p.color : p.slot;
            if (c == null || c <= 0) return null;
            equipo.add(c); if (p == yo) mio = c;
        }
        if (mio == null || equipo.size() < 3) return null;
        Collections.sort(equipo);
        return mio.equals(equipo.get(0)) || mio.equals(equipo.get(equipo.size() - 1)) ? "flanco" : "pocket";
    }
    /** Tramo de duración de una partida (minutos): <15, 15–25, 25–35, 35–45, 45–60, 60+. */
    public static final String[] DURACION_TRAMOS = { "< 5 min", "5 \u2013 <15 min", "15 \u2013 <25 min", "25 \u2013 <40 min", "> 40 min" };   // tramos de aoe2insights
    public static int tramoDuracion(Match m) {
        if (m.started == null || m.finished == null) return -1;
        long seg = Duration.between(m.started, m.finished).getSeconds();
        if (seg <= 0) return -1;
        double min = seg / 60.0;
        return min < 5 ? 0 : min < 15 ? 1 : min < 25 ? 2 : min < 40 ? 3 : 4;
    }
    /** Franjas de ELO del rival de 200 puntos centradas en el ELO del jugador (redondeado a la centena): cinco franjas, la suya en medio; extremos abiertos. */
    public static String[] franjasCentradas(int elo) {
        int c = (int) Math.round(elo / 100.0) * 100;
        return new String[]{ "<" + (c - 300), (c - 300) + "-" + (c - 100), (c - 100) + "-" + (c + 100), (c + 100) + "-" + (c + 300), (c + 300) + "+" };
    }
    public static String franjaDe(double rating, String[] franjas, int elo) {
        int c = (int) Math.round(elo / 100.0) * 100;
        if (rating < c - 300) return franjas[0]; if (rating < c - 100) return franjas[1]; if (rating < c + 100) return franjas[2]; if (rating < c + 300) return franjas[3]; return franjas[4];
    }
    /** El ladder «principal» de un historial para la gráfica: el modo 1v1 más jugado, o el modo más jugado. */
    public static String modoPrincipal(Actividad a) {
        Map<String, Integer> cuenta = new HashMap<>();
        for (Match m : a.partidas()) if (m.mode != null) cuenta.merge(m.mode, 1, Integer::sum);
        String mejor = null; int mejorN = -1;
        for (Map.Entry<String, Integer> en : cuenta.entrySet()) if (en.getKey().contains("1v1") && en.getValue() > mejorN) { mejor = en.getKey(); mejorN = en.getValue(); }
        if (mejor != null) return mejor;
        for (Map.Entry<String, Integer> en : cuenta.entrySet()) if (en.getValue() > mejorN) { mejor = en.getKey(); mejorN = en.getValue(); }
        return mejor;
    }
    public static String tramoDeRating(double rating) {
        int[][] tr = { { 0, 800 }, { 800, 1000 }, { 1000, 1200 }, { 1200, 1400 }, { 1400, 1600 }, { 1600, 1800 }, { 1800, 2000 } };
        for (int[] t0 : tr) if (rating >= t0[0] && rating < t0[1]) return t0[0] + "-" + t0[1];
        return rating >= 2000 ? "2000+" : "?";
    }

    /** Nadie juega dos partidas a la vez: una «en curso» cuyo jugador tiene otra partida empezada después es un fantasma (la API no registró el final). También lo es si el socket/API dice que ese jugador está en OTRA partida. */
    public static void marcarFantasmas(List<Match> lista) {
        Map<Long, Instant> ultimaPorJugador = new HashMap<>();
        for (Match m : lista) { if (m.started == null) continue; for (MatchPlayer p : m.players) { Instant u = ultimaPorJugador.get(p.id); if (u == null || m.started.isAfter(u)) ultimaPorJugador.put(p.id, m.started); } }
        for (Match m : lista) {
            if (m.finished != null || m.started == null) continue;
            boolean f = false;
            for (MatchPlayer p : m.players) {
                Instant u = ultimaPorJugador.get(p.id);
                if (u != null && u.isAfter(m.started)) { f = true; break; }
                Match viva = VIVO_PARTIDA.get(p.id);
                if (viva != null && viva.id > 0 && viva.id != m.id && viva.started != null && viva.started.isAfter(m.started)) { f = true; break; }
            }
            m.fantasma = f;
        }
    }
}
