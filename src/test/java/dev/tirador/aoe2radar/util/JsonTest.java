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

    /** NaN/Infinity/-Infinity sueltos (pandas en sfr-data) se leen como el valor ausente, en cualquier posición. */
    @Test void toleraNaNEInfinitySueltosComoValorAusente() {
        assertEquals(java.util.List.of(1.0, "", 2.0, "", "", 3.0), Json.parse("[1,NaN,2,Infinity, -Infinity,3]"));
        java.util.Map<String, Object> m = Json.obj(Json.parse("{\"a\":NaN,\"b\": Infinity,\"c\":-Infinity,\"d\":-1.5}"));
        assertEquals("", m.get("a"));
        assertEquals("", m.get("b"));
        assertEquals("", m.get("c"));
        assertEquals(-1.5, m.get("d"), "un negativo normal se sigue leyendo como número");
        assertEquals(Json.NO_NUMERO, Json.parse("NaN"));
        assertEquals(java.util.List.of(""), Json.parse("[NaN]"), "el mismo caso que ya toleraba PerfilesSfr");
    }

    @Test void losTextosQueContienenNaNNoSeTocan() {
        // el arreglo es del parser, no un replace sobre el texto: «,NaN» dentro de un nombre queda como estaba
        assertEquals(java.util.List.of("a,NaN", "Infinity", "[NaN"), Json.parse("[\"a,NaN\",\"Infinity\",\"[NaN\"]"));
    }

    @Test void otraPalabraQueEmpiezaPorNoIEsError() {
        assertThrows(RuntimeException.class, () -> Json.parse("[Nope]"));
        assertThrows(RuntimeException.class, () -> Json.parse("[Inf]"));
    }

    @Test void whenConUnNumeroAbsurdoEsNullNoExcepcion() {
        assertNull(Json.when(-1e30), "Long.MIN_VALUE segundos: fuera del rango de Instant");
        assertNull(Json.when(-1e17), "segundos fuera del rango de Instant");
        assertEquals(Instant.ofEpochMilli(Long.MAX_VALUE), Json.when(1e30), "positivo enorme: milisegundos, que sí caben (como antes)");
        assertNull(Json.when(Json.parse("-1e30")), "tal como llega del parser");
    }
}
