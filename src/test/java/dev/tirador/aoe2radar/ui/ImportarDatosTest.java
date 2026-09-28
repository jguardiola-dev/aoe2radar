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
        Files.createDirectories(d.resolve("app"));
        Files.writeString(d.resolve("app").resolve("aoe2radar.cfg"), "[JavaOptions]\njava-options=-Djpackage.app-version=1.3\n");
        Files.setLastModifiedTime(d.resolve("config.properties"), FileTime.from(Instant.parse("2026-05-01T10:00:00Z")));

        List<BusquedaDatos.Candidata> cs = ImportarDatos.detectar(List.of(escritorio, descargas), List.of(), limite());
        assertEquals(d, cs.get(0).carpeta());
        String texto = ImportarDatos.textoEncontrada(cs);
        assertTrue(texto.contains(d.toString()), texto);
        assertTrue(texto.contains("2026"), texto);
        assertTrue(texto.contains("aoe2radar 1.3"), texto);
        assertEquals(escritorio, ImportarDatos.carpetaInicial(cs.get(0), List.of(descargas, escritorio)),
                "«Elegir otra carpeta…» abre en el Escritorio, no en la carpeta por defecto");
    }

    @Test void conVariasLaOfertaDiceCuantasYLaVersionPropuesta() throws IOException {
        Path esc = Files.createDirectories(tmp.resolve("Escritorio"));
        Path v13 = Files.createDirectories(esc.resolve("aoe2radar v9").resolve("app").resolve("aoe2radar"));
        Files.writeString(v13.resolve("aoe2radar.exe"), "exe");
        Files.writeString(v13.resolve("players.txt"), "1;Ana\n");
        Files.createDirectories(v13.resolve("app"));
        Files.writeString(v13.resolve("app").resolve("aoe2radar-1.3.jar"), "jar");
        Path sfr = Files.createDirectories(esc.resolve("SFR4").resolve("app").resolve("SpoilerFreeRecs"));
        Files.writeString(sfr.resolve("SpoilerFreeRecs.exe"), "exe");
        Files.writeString(sfr.resolve("config.properties"), "x");   // la más reciente, pero una 1.0
        List<BusquedaDatos.Candidata> cs = ImportarDatos.detectar(List.of(esc), List.of(), limite());
        String texto = ImportarDatos.textoEncontrada(cs);
        assertTrue(texto.contains("2 copias") || texto.contains("2 copies"), texto);
        assertTrue(texto.contains("1.3"), texto);
        assertTrue(texto.contains(v13.toString()), texto);
    }

    @Test void sinNadaQueEncontrarLaOfertaEsLaDeSiempre() throws IOException {
        Path vacio = Files.createDirectories(tmp.resolve("Escritorio"));
        assertTrue(ImportarDatos.detectar(List.of(vacio), List.of(), limite()).isEmpty());
    }
}
