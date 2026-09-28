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

    /** Una copia en dir: exe, datos y, si version != null, el .cfg de jpackage (o el jar si porJar). */
    private Path copia(Path dir, String exe, String version, boolean porJar, String players, long haceSegundos) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(exe), "exe");
        Path dato = players != null ? dir.resolve("players.txt") : dir.resolve("config.properties");
        Files.writeString(dato, players != null ? players : "idioma=es\n");
        Files.setLastModifiedTime(dato, FileTime.from(Instant.now().minusSeconds(haceSegundos)));
        if (version != null) {
            Path app = Files.createDirectories(dir.resolve("app"));
            if (porJar) Files.writeString(app.resolve("aoe2radar-" + version + ".jar"), "jar");
            else Files.writeString(app.resolve("aoe2radar.cfg"), "[JavaOptions]\njava-options=-Djpackage.app-version=" + version + "\n");
        }
        return dir;
    }

    @Test void comparaVersionesPorNumero() {
        assertTrue(BusquedaDatos.compararVersiones("1.10", "1.9") > 0);
        assertTrue(BusquedaDatos.compararVersiones("1.3", "1.2.5") > 0);
        assertEquals(0, BusquedaDatos.compararVersiones("1.3", "1.3.0"));
        assertTrue(BusquedaDatos.compararVersiones("1.0", "1.1") < 0);
    }

    @Test void leeLaVersionDelCfgODelJarYSiEsSpoilerFreeRecs() throws IOException {
        BusquedaDatos.Candidata cfg = BusquedaDatos.leer(copia(tmp.resolve("a"), "aoe2radar.exe", "1.3", false, "1;Ana\n", 1));
        assertEquals("1.3", cfg.version());
        assertTrue(cfg.aoe2radar());
        assertTrue(cfg.conJugadores());
        assertEquals("aoe2radar 1.3", cfg.nombre());
        BusquedaDatos.Candidata jar = BusquedaDatos.leer(copia(tmp.resolve("b"), "aoe2radar.exe", "1.2", true, "# vacío\n\n", 1));
        assertEquals("1.2", jar.version());
        assertFalse(jar.conJugadores(), "sin jugadores: solo comentarios y líneas vacías");
        Path sfr = copia(tmp.resolve("c"), "SpoilerFreeRecs.exe", null, false, null, 1);
        Files.createDirectories(sfr.resolve("app"));
        Files.writeString(sfr.resolve("app").resolve("SpoilerFreeRecs.jar"), "jar");
        BusquedaDatos.Candidata s = BusquedaDatos.leer(sfr);
        assertFalse(s.aoe2radar());
        assertNull(s.version());
    }

    @Test void comoEnElPcDeJorgeGanaLaVersionMayorNoLaConfigMasReciente() throws IOException {
        Path esc = Files.createDirectories(tmp.resolve("Escritorio"));
        Path sfr4 = copia(esc.resolve("SFR4").resolve("app").resolve("SpoilerFreeRecs"), "SpoilerFreeRecs.exe", null, false, null, 10);
        Path v2 = copia(esc.resolve("aoe2radar v2").resolve("app").resolve("aoe2radar"), "aoe2radar.exe", "1.1", false, "1;x\n", 100);
        Path v9 = copia(esc.resolve("aoe2radar v9").resolve("app").resolve("aoe2radar"), "aoe2radar.exe", "1.3", false, "1;x\n", 86_400);
        Path v10 = copia(esc.resolve("aoe2radar v10").resolve("app").resolve("aoe2radar"), "aoe2radar.exe", "1.10", true, "1;x\n", 90_000);
        Path prueba = copia(esc.resolve("prueba").resolve("aoe2radar"), "aoe2radar.exe", "1.10", false, "", 5);   // sin jugadores
        List<Path> orden = BusquedaDatos.buscar(List.of(esc), 4, 1000, limite(), List.of())
                .stream().map(BusquedaDatos.Candidata::carpeta).toList();
        assertEquals(List.of(v10, prueba, v9, v2, sfr4), orden,
                "1.10 > 1.3 (numérico); a igual versión, con jugadores primero; SFR 1.0 sin versión, la última");
        assertEquals(v10, BusquedaDatos.resolverElegida(esc, List.of(), limite(), null));
    }

    @Test void aIgualVersionAoe2radarAntesQueSpoilerFreeRecs() throws IOException {
        Path esc = Files.createDirectories(tmp.resolve("E"));
        Path sfr = copia(esc.resolve("sfr"), "SpoilerFreeRecs.exe", null, false, "1;x\n", 1);
        Path aoe = copia(esc.resolve("aoe"), "aoe2radar.exe", null, false, "1;x\n", 1000);
        assertEquals(aoe, BusquedaDatos.buscar(List.of(esc), 4, 1000, limite(), List.of()).get(0).carpeta());
        assertTrue(Files.exists(sfr));
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
