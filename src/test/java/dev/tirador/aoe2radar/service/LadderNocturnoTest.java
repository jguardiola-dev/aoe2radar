package dev.tirador.aoe2radar.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** El ladder de anoche desde elo_ayer v2: orden por rango, solo quien tiene 1v1, y null con datos de la 1.3. */
class LadderNocturnoTest {

    @Test void ordenaPorRangoYConvierteLaUltimaPartidaAMilisegundos() {
        Map<Long, int[]> ayer = new HashMap<>();
        ayer.put(1L, new int[]{ 1500, 10, 0, 0 });
        ayer.put(2L, new int[]{ 2100, 50, 0, 0 });
        ayer.put(3L, new int[]{ 0, 0, 1800, 30 });    // solo equipos
        ayer.put(4L, new int[]{ 1700, 20, 0, 0 });
        Map<Long, long[]> ru = new HashMap<>();
        ru.put(1L, new long[]{ 300, 1_790_000_000L });
        ru.put(2L, new long[]{ 1, 1_790_100_000L });
        ru.put(3L, new long[]{ 5, 1_790_000_000L });   // sin ELO 1v1: fuera
        ru.put(4L, new long[]{ 120, 0 });              // última desconocida: 0, como en el leaderboard sin fecha
        ru.put(5L, new long[]{ 7, 1_790_000_000L });   // sin fila en ayer: fuera
        List<long[]> l = LadderNocturno.de(ayer, ru);
        assertEquals(3, l.size());
        assertArrayEquals(new long[]{ 2, 2100, 1_790_100_000_000L }, l.get(0));
        assertArrayEquals(new long[]{ 4, 1700, 0 }, l.get(1));
        assertArrayEquals(new long[]{ 1, 1500, 1_790_000_000_000L }, l.get(2));
    }

    @Test void sinRangosEsNull() {
        Map<Long, int[]> ayer = Map.of(1L, new int[]{ 1500, 10, 0, 0 });
        assertNull(LadderNocturno.de(ayer, Map.of()), "elo_ayer de la 1.3: el llamador sigue con el leaderboard en vivo");
        assertNull(LadderNocturno.de(ayer, null));
        assertNull(LadderNocturno.de(ayer, Map.of(9L, new long[]{ 1, 1 })), "nadie con 1v1: null, no una lista vacía");
    }
}
