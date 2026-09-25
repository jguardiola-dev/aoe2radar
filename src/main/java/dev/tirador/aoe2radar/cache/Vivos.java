package dev.tirador.aoe2radar.cache;

import dev.tirador.aoe2radar.model.Match;

import java.time.Duration;
import java.time.Instant;

/** La regla de «en curso de verdad» (el estado de quién está en partida vive en service.EstadoVivo). */
public final class Vivos {
    private Vivos() {}

    /** Partida realmente en curso: sin terminar y empezada hace menos de 3 h.
     *  Las «en curso» de hace días son fantasmas de partidas que crashearon. */
    public static boolean enCursoReal(Match m) {
        return m != null && !m.fantasma && m.finished == null && m.started != null
                && m.started.isAfter(Instant.now().minus(Duration.ofHours(3)));
    }
}
