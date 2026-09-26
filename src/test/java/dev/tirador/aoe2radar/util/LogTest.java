package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Rotación de descargas.log (F15 de la revisión general): al arrancar, si pasa del tope, se aparta a «.1». */
class LogTest {

    @TempDir Path dir;

    @Test void siPasaDelTopeSeApartaAPuntoUnoSustituyendoElAnterior() throws IOException {
        Path log = dir.resolve("descargas.log"), viejo = dir.resolve("descargas.log.1");
        Files.writeString(log, "0123456789A");   // 11 bytes: pasa del tope de 10
        Files.writeString(viejo, "rotado la vez anterior");
        Log.rotar(log, 10);
        assertFalse(Files.exists(log), "se empieza un log nuevo (lo crea la siguiente línea)");
        assertEquals("0123456789A", Files.readString(viejo), "el .1 anterior se sustituye: nunca hay más de dos");
    }

    @Test void hastaElTopeNoSeToca() throws IOException {
        Path log = dir.resolve("descargas.log");
        Files.writeString(log, "0123456789");   // 10 bytes: justo el tope
        Log.rotar(log, 10);
        assertEquals("0123456789", Files.readString(log));
        assertFalse(Files.exists(dir.resolve("descargas.log.1")));
    }

    @Test void sinLogNoFallaNiCreaNada() throws IOException {
        Log.rotar(dir.resolve("descargas.log"), 10);   // primer arranque: no hay log todavía
        try (var s = Files.list(dir)) { assertEquals(0, s.count()); }
    }

    @Test void elTopeEsDeCincoMegas() {
        assertEquals(5L * 1024 * 1024, Log.LOG_MAX_BYTES);
    }
}
