package dev.tirador.aoe2radar.cache;

import dev.tirador.aoe2radar.model.Match;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Lo que está en directo ahora: partida en curso, rival y última vez visto de cada jugador (lo llenan socket y barridos, lo lee la UI). */
public final class Vivos {
    private Vivos() {}

    public static final Map<Long, Match> VIVO_PARTIDA = new java.util.concurrent.ConcurrentHashMap<>();   // pid → partida en curso (socket y barridos)
    public static final Map<Long, Object[]> VIVO_RIVAL = new java.util.concurrent.ConcurrentHashMap<>();   // pid → {rivalId, rivalNombre}
    public static final Map<Long, Long> VISTO_VIVO_MS = new java.util.concurrent.ConcurrentHashMap<>();   // pid → última vez que lo vimos en partida (socket/barridos)

    /** Partida realmente en curso: sin terminar y empezada hace menos de 3 h.
     *  Las «en curso» de hace días son fantasmas de partidas que crashearon. */
    public static boolean enCursoReal(Match m) {
        return m != null && !m.fantasma && m.finished == null && m.started != null
                && m.started.isAfter(Instant.now().minus(Duration.ofHours(3)));
    }
}
