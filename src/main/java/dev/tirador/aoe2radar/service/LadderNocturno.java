package dev.tirador.aoe2radar.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * El ladder 1v1 de anoche, sacado del ELO nocturno de sfr-data (elo_ayer v2, 1.4: rango y última partida): la misma forma
 * que las páginas del leaderboard del companion ({pid, rating, última partida en ms}), en orden de rango. «Al azar por
 * ELO» y «Guess the ELO» lo usan en vez de bisecar el leaderboard en vivo: cero llamadas para saber quién está en el tramo.
 * <p>Pura y sin red. Solo entran los jugadores con rango y ELO 1v1 (el alcance de sfr-data: todos los activos de los
 * últimos 28 días más el top 40.000). null si el archivo es anterior a la 1.4 (sin rangos): quien llama sigue con el
 * leaderboard en vivo, como hasta ahora.
 */
public final class LadderNocturno {
    private LadderNocturno() {}

    /**
     * @param ayer        pid → {elo1v1, partidas1v1, eloEq, partidasEq} (EloNocturno.ayer)
     * @param rangoUltima pid → {rango 1v1, última partida 1v1 en epoch s} (EloNocturno.rangoUltima)
     * @return {pid, elo1v1, últimaMs (0 si no se sabe)} por rango ascendente; null si no hay nada que ordenar
     */
    public static List<long[]> de(Map<Long, int[]> ayer, Map<Long, long[]> rangoUltima) {
        if (ayer == null || rangoUltima == null || rangoUltima.isEmpty()) return null;
        List<long[]> con = new ArrayList<>();   // {pid, elo, últimaMs, rango}
        for (Map.Entry<Long, long[]> en : rangoUltima.entrySet()) {
            long[] ru = en.getValue();
            int[] a = ayer.get(en.getKey());
            if (a == null || a[0] <= 0 || ru == null || ru[0] <= 0) continue;
            con.add(new long[]{ en.getKey(), a[0], Math.max(0L, ru[1]) * 1000L, ru[0] });
        }
        if (con.isEmpty()) return null;
        con.sort(Comparator.<long[]>comparingLong(x -> x[3]).thenComparingLong(x -> -x[1]).thenComparingLong(x -> x[0]));
        List<long[]> out = new ArrayList<>(con.size());
        for (long[] x : con) out.add(new long[]{ x[0], x[1], x[2] });
        return out;
    }
}
