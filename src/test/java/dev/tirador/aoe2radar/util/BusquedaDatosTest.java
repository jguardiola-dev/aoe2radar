package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Buscar la carpeta de datos de una 1.x dentro de la elegida (las reales salen anidadas al descomprimir). Solo
 *  carpetas temporales: nunca el Escritorio ni los Documentos reales. */
class BusquedaDatosTest {

    @TempDir Path tmp;

    private long limite() { return System.nanoTime() + TimeUnit.SECONDS.toNanos(30); }

    /** Como la de Jorge: «…\aoe2radar version publicacion pre migracino\aoe2radar\app\aoe2radar\» con exe y config. */
    private Path anidada(Path raiz, String exe, String dato, long hace) throws IOException {
        Path d = Files.createDirectories(raiz.resolve("aoe2radar").resolve("app").resolve("aoe2radar"));
        Files.writeString(d.resolve(exe), "exe");
        Files.writeString(d.resolve(dato), "x");
        Files.setLastModifiedTime(d.resolve(dato), FileTime.from(Instant.now().minusSeconds(hace)));
        return d;
    }

    @Test void encuentraLaCarpetaAnidadaDeLaVersionZip() throws IOException {
        Path elegida = Files.createDirectories(tmp.resolve("aoe2radar version publicacion pre migracino"));
        Path datos = anidada(elegida, "aoe2radar.exe", "config.properties", 60);   // sin players.txt: también vale
        int[] n = { 0 };
        assertEquals(datos, BusquedaDatos.resolverElegida(elegida, List.of(), limite(), n));
        assertEquals(1, n[0]);
    }

    @Test void encuentraSpoilerFreeRecs10() throws IOException {
        Path sfr = Files.createDirectories(tmp.resolve("SFR").resolve("app").resolve("SpoilerFreeRecs"));
        Files.writeString(sfr.resolve("players.txt"), "1;x\n");
        Files.createDirectories(sfr.resolve("runtime"));   // sin exe con ese nombre, pero con runtime/ al lado
        assertEquals(sfr, BusquedaDatos.resolverElegida(tmp.resolve("SFR"), List.of(), limite(), null));
    }

    @Test void laPropiaCarpetaConDatosVale() throws IOException {
        Path suelta = Files.createDirectories(tmp.resolve("copia"));
        Files.writeString(suelta.resolve("players.txt"), "1;x\n");   // sin exe: una copia suelta de los datos
        assertEquals(suelta, BusquedaDatos.resolverElegida(suelta, List.of(), limite(), null));
    }

    @Test void conVariasGanaLaMasRecienteYSeCuentan() throws IOException {
        Path raiz = Files.createDirectories(tmp.resolve("escritorio"));
        anidada(raiz.resolve("vieja"), "aoe2radar.exe", "players.txt", 86_400);
        Path nueva = anidada(raiz.resolve("nueva"), "aoe2radar.exe", "players.txt", 60);
        int[] n = { 0 };
        assertEquals(nueva, BusquedaDatos.resolverElegida(raiz, List.of(), limite(), n));
        assertEquals(2, n[0]);
        List<BusquedaDatos.Candidata> c = BusquedaDatos.buscar(List.of(raiz), 4, 1000, limite(), List.of());
        assertEquals(nueva, c.get(0).carpeta());
    }

    @Test void sinCandidataNiDatosDevuelveNull() throws IOException {
        Path raiz = Files.createDirectories(tmp.resolve("nada").resolve("a").resolve("b"));
        Files.writeString(raiz.resolve("config.properties"), "x");   // datos sin exe ni app/runtime, y no es la elegida
        assertNull(BusquedaDatos.resolverElegida(tmp.resolve("nada"), List.of(), limite(), null));
    }

    @Test void noBajaMasDeCuatroNiveles() throws IOException {
        Path raiz = Files.createDirectories(tmp.resolve("hondo"));
        Path cuatro = anidada(raiz.resolve("n1"), "aoe2radar.exe", "players.txt", 1);         // n1/aoe2radar/app/aoe2radar: nivel 4
        Path cinco = anidada(raiz.resolve("m1").resolve("m2"), "aoe2radar.exe", "players.txt", 1);   // nivel 5
        List<Path> c = BusquedaDatos.buscar(List.of(raiz), 4, 1000, limite(), List.of())
                .stream().map(BusquedaDatos.Candidata::carpeta).toList();
        assertTrue(c.contains(cuatro));
        assertFalse(c.contains(cinco));
    }

    @Test void noSigueUniones() throws Exception {
        Path fuera = anidada(tmp.resolve("fuera"), "aoe2radar.exe", "players.txt", 1);
        Path raiz = Files.createDirectories(tmp.resolve("raiz"));
        Process p = new ProcessBuilder("cmd", "/c", "mklink /J \"" + raiz.resolve("union") + "\" \"" + tmp.resolve("fuera") + "\"")
                .redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assertEquals(0, p.waitFor(), "mklink /J (no pide permisos)");
        assertTrue(Files.exists(raiz.resolve("union").resolve("aoe2radar").resolve("app").resolve("aoe2radar").resolve("players.txt")));
        try {
            assertTrue(BusquedaDatos.buscar(List.of(raiz), 4, 1000, limite(), List.of()).isEmpty(),
                    "una unión puede llevar a cualquier parte (o a un bucle): no se sigue");
            assertFalse(BusquedaDatos.esDirectorioReal(raiz.resolve("union")));
        } finally {
            Files.delete(raiz.resolve("union"));   // borra la unión, no el destino
        }
        assertTrue(Files.exists(fuera.resolve("players.txt")));
    }

    @Test void respetaElTopeElPlazoYLasExcluidas() throws IOException {
        Path raiz = Files.createDirectories(tmp.resolve("r"));
        Path d = anidada(raiz, "aoe2radar.exe", "players.txt", 1);
        assertTrue(BusquedaDatos.buscar(List.of(raiz), 4, 2, limite(), List.of()).isEmpty(), "tope de carpetas");
        assertTrue(BusquedaDatos.buscar(List.of(raiz), 4, 1000, System.nanoTime() - 1, List.of()).isEmpty(), "plazo vencido");
        assertTrue(BusquedaDatos.buscar(List.of(raiz), 4, 1000, limite(), List.of(d)).isEmpty(), "la carpeta de datos no cuenta");
        assertEquals(1, BusquedaDatos.buscar(List.of(raiz, raiz), 4, 1000, limite(), List.of()).size(), "sin repetir");
    }
}
