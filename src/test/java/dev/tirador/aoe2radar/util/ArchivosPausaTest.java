package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La pausa de escrituras de Archivos (la usa ImportacionDatos.colocar) y la limpieza de recs a medio importar. */
class ArchivosPausaTest {

    @TempDir Path tmp;

    @Test void enPausaOtroHiloNoPuedeEscribirYAlReanudarSi() throws Exception {
        Path f = tmp.resolve("paises.txt");
        Archivos.congelarEscrituras();
        try {
            Throwable t = CompletableFuture.supplyAsync(() -> {
                try { Archivos.escribirAtomico(f, new byte[] { 1 }); return null; }
                catch (Exception ex) { return ex; }
            }).get(5, TimeUnit.SECONDS);
            assertNotNull(t, "el Timer de países (otro hilo) no escribe mientras se colocan datos importados");
            assertFalse(Files.exists(f));
        } finally {
            Archivos.descongelarEscrituras();
        }
        CompletableFuture.runAsync(() -> {
            try { Archivos.escribirAtomico(f, new byte[] { 1 }); } catch (Exception ex) { throw new RuntimeException(ex); }
        }).get(5, TimeUnit.SECONDS);
        assertTrue(Files.exists(f));
    }

    @Test void descongelarDesdeOtroHiloNoHaceNada() throws Exception {
        CompletableFuture.runAsync(Archivos::descongelarEscrituras).get(5, TimeUnit.SECONDS);   // no lanza
        Archivos.escribirAtomico(tmp.resolve("x"), new byte[] { 1 });
    }

    @Test void limpiaLasRecsAMedioImportar() throws Exception {
        Path vieja = Files.writeString(tmp.resolve("a.aoe2record" + ImportacionDatos.SUFIJO_REC_TEMPORAL), "x");
        Files.setLastModifiedTime(vieja, FileTime.from(Instant.now().minus(Duration.ofHours(30))));
        Path rec = Files.writeString(tmp.resolve("b.aoe2record"), "x");
        Files.setLastModifiedTime(rec, FileTime.from(Instant.now().minus(Duration.ofHours(30))));
        Archivos.limpiarTemporales(tmp, "", ImportacionDatos.SUFIJO_REC_TEMPORAL, Duration.ofDays(1));
        assertFalse(Files.exists(vieja));
        assertTrue(Files.exists(rec), "una rec de verdad no se toca");
    }
}
