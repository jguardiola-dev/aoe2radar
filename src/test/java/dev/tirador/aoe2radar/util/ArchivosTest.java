package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fila 140 de DEUDA: Archivos.limpiarTemporales barre los ".tmp" huérfanos que deja escribirAtomico cuando la
 * app se corta a media escritura. Con una carpeta temporal y archivos con la fecha mutada (Files.setLastModifiedTime,
 * sin esperar de verdad): solo se borra lo viejo, con el prefijo pedido y la extensión ".tmp"; lo reciente, lo de
 * otro prefijo y lo que no acaba en ".tmp" se conservan.
 */
class ArchivosTest {

    private static Path crear(Path dir, String nombre, Duration antiguedad) throws IOException {
        Path p = dir.resolve(nombre);
        Files.writeString(p, "x");
        Files.setLastModifiedTime(p, java.nio.file.attribute.FileTime.from(Instant.now().minus(antiguedad)));
        return p;
    }

    @Test void borraSoloLosTmpViejosDelPrefijoPedido() throws Exception {
        Path dir = Files.createTempDirectory("archivos-test");
        try {
            Path viejo = crear(dir, "config.properties9182736451.tmp", Duration.ofHours(30));
            Path reciente = crear(dir, "config.properties1122334455.tmp", Duration.ofMinutes(1));
            Path otroPrefijo = crear(dir, "paises.txt9988776655.tmp", Duration.ofHours(30));
            Path sinExtensionTmp = crear(dir, "config.properties.bak", Duration.ofHours(30));

            Archivos.limpiarTemporales(dir, "config.properties", Duration.ofHours(24));

            assertFalse(Files.exists(viejo), "el .tmp viejo del prefijo pedido se borra");
            assertTrue(Files.exists(reciente), "el .tmp reciente (una escritura en curso) no se toca");
            assertTrue(Files.exists(otroPrefijo), "otro prefijo no se toca, aunque sea viejo y .tmp");
            assertTrue(Files.exists(sinExtensionTmp), "lo que no acaba en .tmp no se toca");
        } finally {
            borrarRecursivo(dir);
        }
    }

    @Test void prefijoVacioBarreCualquierTmpViejoDeLaCarpeta() throws Exception {
        Path dir = Files.createTempDirectory("archivos-test-shards");
        try {
            Path a = crear(dir, "12345.tmp", Duration.ofHours(30));
            Path b = crear(dir, "index.json6789.tmp", Duration.ofHours(30));
            Path reciente = crear(dir, "99999.tmp", Duration.ofSeconds(5));

            Archivos.limpiarTemporales(dir, "", Duration.ofHours(24));

            assertFalse(Files.exists(a));
            assertFalse(Files.exists(b));
            assertTrue(Files.exists(reciente), "recién escrito: no se toca aunque el prefijo esté vacío");
        } finally {
            borrarRecursivo(dir);
        }
    }

    @Test void carpetaInexistenteNoFalla() {
        Archivos.limpiarTemporales(Path.of("no-existe-de-verdad-" + System.nanoTime()), "x", Duration.ofHours(1));
        // no lanza: es limpieza de arranque en un hilo de fondo, una carpeta que no llegó a crearse no es un error
    }

    private static void borrarRecursivo(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } });
        }
    }
}
