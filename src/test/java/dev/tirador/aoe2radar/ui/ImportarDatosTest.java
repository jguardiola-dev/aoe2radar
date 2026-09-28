package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.util.BusquedaDatos;
import dev.tirador.aoe2radar.util.ImportacionDatos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fuera del paquete (tests, harness) la oferta de importar no aparece ni escribe nada: si llegara a abrir el
 *  diálogo, este test se quedaría esperando a un clic. La detección se prueba con carpetas temporales como
 *  «Escritorio», «Descargas» y «Documentos»: nunca las reales. */
class ImportarDatosTest {

    @TempDir Path tmp;

    private long limite() { return System.nanoTime() + TimeUnit.SECONDS.toNanos(30); }

    @Test void fueraDelPaqueteNoSeOfreceNiSeRecuerda() {
        String antes = leerConfig(ImportacionDatos.CLAVE_OFRECIDA, "ausente");
        ImportarDatos.alArrancar(null);   // ni aviso ni oferta: vuelve sin lanzar hilos ni diálogos
        assertEquals(antes, leerConfig(ImportacionDatos.CLAVE_OFRECIDA, "ausente"));
    }

    @Test void soloUnaImportacionALaVez() {
        assertTrue(ImportarDatos.reservar());
        try {
            assertFalse(ImportarDatos.reservar(), "con una en marcha no se lanza otra");
        } finally {
            ImportarDatos.liberar();
        }
        assertTrue(ImportarDatos.reservar(), "al terminar, se puede volver a importar");
        ImportarDatos.liberar();
    }

    @Test void detectaUna1xEnElEscritorioYLaOfertaDiceRutaYFecha() throws IOException {
        Path escritorio = Files.createDirectories(tmp.resolve("OneDrive").resolve("Escritorio"));
        Path descargas = Files.createDirectories(tmp.resolve("Downloads"));
        Path d = Files.createDirectories(escritorio.resolve("aoe2radar version publicacion pre migracino")
                .resolve("aoe2radar").resolve("app").resolve("aoe2radar"));
        Files.writeString(d.resolve("aoe2radar.exe"), "exe");
        Files.writeString(d.resolve("config.properties"), "idioma=es\n");
        Files.setLastModifiedTime(d.resolve("config.properties"), FileTime.from(Instant.parse("2026-05-01T10:00:00Z")));

        BusquedaDatos.Candidata c = ImportarDatos.detectar(List.of(escritorio, descargas), List.of(), limite());
        assertEquals(d, c.carpeta());
        String texto = ImportarDatos.textoEncontrada(c);
        assertTrue(texto.contains(d.toString()), texto);
        assertTrue(texto.contains("2026"), texto);
    }

    @Test void sinNadaQueEncontrarLaOfertaEsLaDeSiempre() throws IOException {
        Path vacio = Files.createDirectories(tmp.resolve("Escritorio"));
        assertNull(ImportarDatos.detectar(List.of(vacio), List.of(), limite()));
    }
}
