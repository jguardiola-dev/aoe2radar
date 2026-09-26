package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Json.when: lee fechas de la API (segundos, milisegundos o texto ISO). Lo que no se puede leer queda null, nunca una
 * excepción: una fecha absurda en una fila no debe tumbar la página entera (DEUDA, fase 4).
 */
class JsonTest {

    @Test void whenLeeSegundosMilisegundosYTexto() {
        assertEquals(Instant.ofEpochSecond(1_700_000_000L), Json.when(1.7e9));
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L), Json.when(1.7e12), "más de 1e11: milisegundos");
        assertEquals(Instant.parse("2024-01-02T03:04:05Z"), Json.when("2024-01-02T03:04:05Z"));
        assertEquals(Instant.parse("2024-01-02T01:04:05Z"), Json.when("2024-01-02T03:04:05+02:00"));
        assertNull(Json.when(null));
        assertNull(Json.when("ayer"));
    }

    @Test void whenConUnNumeroAbsurdoEsNullNoExcepcion() {
        assertNull(Json.when(-1e30), "Long.MIN_VALUE segundos: fuera del rango de Instant");
        assertNull(Json.when(-1e17), "segundos fuera del rango de Instant");
        assertEquals(Instant.ofEpochMilli(Long.MAX_VALUE), Json.when(1e30), "positivo enorme: milisegundos, que sí caben (como antes)");
        assertNull(Json.when(Json.parse("-1e30")), "tal como llega del parser");
    }
}
