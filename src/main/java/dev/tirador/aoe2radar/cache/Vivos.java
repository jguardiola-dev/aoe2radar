package dev.tirador.aoe2radar.cache;

import dev.tirador.aoe2radar.model.Match;

import java.time.Duration;
import java.time.Instant;

/** La regla de «en curso de verdad» (el estado de quién está en partida vive en service.EstadoVivo). */
public final class Vivos {
    private Vivos() {}

    /** Partida realmente en curso: sin terminar y empezada hace menos de 3 h.
     *  Las «en curso» de hace días son fantasmas de partidas que crashearon. */
    public static boolean enCursoReal(Match m) { return enCursoReal(m, Instant.now()); }

    /** enCursoReal con la hora que se le dé (estricta: empezada hace 3 h justas ya no cuenta). */
    public static boolean enCursoReal(Match m, Instant ahora) {
        return m != null && !m.fantasma && m.finished == null && m.started != null
                && m.started.isAfter(ahora.minus(Duration.ofHours(3)));
    }

    /**
     * ¿Un matchAdded/matchUpdated del socket puede ser alguien entrando en partida? Empezada (no un lobby: se admite
     * hasta 60 s en el futuro por relojes desajustados) y hace menos de 3 h. Que no esté terminada lo mira quien llama.
     */
    public static boolean candidatoSocket(Match m, Instant ahora) {
        return m.started != null && !m.started.isAfter(ahora.plusSeconds(60)) && m.started.isAfter(ahora.minus(Duration.ofHours(3)));
    }
}
