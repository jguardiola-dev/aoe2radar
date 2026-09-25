package dev.tirador.aoe2radar.cache;

import dev.tirador.aoe2radar.model.Match;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las dos reglas de «en curso», con la hora fija: fronteras exactas. */
class VivosTest {
    static final Instant AHORA = Instant.ofEpochMilli(1_700_000_000_000L);

    static Match empezada(Duration hace) {
        Match m = new Match();
        m.started = AHORA.minus(hace);
        return m;
    }

    @Test void enCursoReal_haceMenosDeTresHorasSiTresHorasJustasNo() {
        assertTrue(Vivos.enCursoReal(empezada(Duration.ofHours(3).minusMillis(1)), AHORA));
        assertFalse(Vivos.enCursoReal(empezada(Duration.ofHours(3)), AHORA), "a las 3 h justas ya es un fantasma");
    }

    @Test void enCursoReal_niTerminadaNiFantasmaNiSinEmpezar() {
        Match t = empezada(Duration.ofMinutes(5)); t.finished = AHORA;
        assertFalse(Vivos.enCursoReal(t, AHORA), "terminada");
        Match f = empezada(Duration.ofMinutes(5)); f.fantasma = true;
        assertFalse(Vivos.enCursoReal(f, AHORA), "marcada como fantasma");
        assertFalse(Vivos.enCursoReal(new Match(), AHORA), "sin started");
        assertFalse(Vivos.enCursoReal(null, AHORA));
    }

    @Test void candidatoSocket_admiteHasta60sEnElFuturo() {
        assertTrue(Vivos.candidatoSocket(empezada(Duration.ofSeconds(-60)), AHORA), "60 s en el futuro: relojes desajustados");
        assertFalse(Vivos.candidatoSocket(empezada(Duration.ofSeconds(-61)), AHORA), "más: es un lobby, aún no ha empezado");
    }

    @Test void candidatoSocket_menosDeTresHorasYConStarted() {
        assertTrue(Vivos.candidatoSocket(empezada(Duration.ofHours(3).minusMillis(1)), AHORA));
        assertFalse(Vivos.candidatoSocket(empezada(Duration.ofHours(3)), AHORA));
        assertFalse(Vivos.candidatoSocket(new Match(), AHORA), "sin started");
    }
}
