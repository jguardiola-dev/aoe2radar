package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** El .cfg del lanzador de jpackage: lectura, reescritura byte a byte y escritura atómica sin respaldo. */
class CfgLanzadorTest {

    /** Copia literal del .cfg que deja jpackage 21 (target/dist/aoe2radar/app/aoe2radar.cfg): CRLF, y una línea en
     *  blanco con LF sola antes de [JavaOptions]. */
    static final String CFG_REAL = "[Application]\r\n"
            + "app.classpath=$APPDIR\\aoe2radar-1.3.0.jar\r\n"
            + "app.mainclass=dev.tirador.aoe2radar.SpoilerFreeRecs\r\n"
            + "app.classpath=$APPDIR\\flatlaf-3.7.2.jar\r\n"
            + "\n"
            + "[JavaOptions]\r\n"
            + "java-options=-Djpackage.app-version=1.3\r\n"
            + "java-options=-Dfile.encoding=UTF-8\r\n";

    @TempDir Path tmp;

    @Test void leeElFormatoReal() {
        CfgLanzador.Datos d = CfgLanzador.leer(CFG_REAL);
        assertNotNull(d);
        assertEquals("aoe2radar-1.3.0.jar", d.jarApp());
        assertEquals(List.of("flatlaf-3.7.2.jar"), d.resto());
        assertEquals("dev.tirador.aoe2radar.SpoilerFreeRecs", d.mainclass());
        assertEquals(List.of("-Dfile.encoding=UTF-8"), d.opciones(), "sin la opción de versión, que cambia en cada una");
    }

    @Test void loQueNoTieneElFormatoEsperadoNoSeLee() {
        assertNull(CfgLanzador.leer((String) null));
        assertNull(CfgLanzador.leer("basura"));
        assertNull(CfgLanzador.leer(CFG_REAL.replace("app.mainclass=dev.tirador.aoe2radar.SpoilerFreeRecs\r\n", "")), "sin mainclass");
        assertNull(CfgLanzador.leer(CFG_REAL.replace("aoe2radar-1.3.0.jar", "otra.jar")), "sin jar de la app");
        // la 1.3 de Jorge arrastraba un segundo jar de la app (1.2.0-SNAPSHOT): dos jars de la app = no se toca
        assertNull(CfgLanzador.leer(CFG_REAL.replace("app.classpath=$APPDIR\\flatlaf",
                "app.classpath=$APPDIR\\aoe2radar-1.2.0-SNAPSHOT.jar\r\napp.classpath=$APPDIR\\flatlaf")));
    }

    @Test void reescribirSoloCambiaElJarYLaVersionByteAByte() {
        String nuevo = CfgLanzador.reescribir(CFG_REAL, "aoe2radar-1.5.jar", "1.5");
        String esperado = CFG_REAL.replace("aoe2radar-1.3.0.jar", "aoe2radar-1.5.jar")
                .replace("app-version=1.3\r\n", "app-version=1.5\r\n");
        assertEquals(esperado, nuevo, "el resto (fines de línea incluidos, también la LF sola) intacto");
        CfgLanzador.Datos d = CfgLanzador.leer(nuevo);
        assertEquals("aoe2radar-1.5.jar", d.jarApp());
        assertEquals(List.of("flatlaf-3.7.2.jar"), d.resto());
    }

    @Test void reescribirEsIdempotente() {
        String una = CfgLanzador.reescribir(CFG_REAL, "aoe2radar-1.5.jar", "1.5");
        assertEquals(una, CfgLanzador.reescribir(una, "aoe2radar-1.5.jar", "1.5"));
        assertEquals(CFG_REAL, CfgLanzador.reescribir(CFG_REAL, "aoe2radar-1.3.0.jar", "1.3"), "al mismo jar: el mismo archivo");
    }

    @Test void reescribirRechazaNombresQueNoSonUnJarDeLaApp() {
        assertNull(CfgLanzador.reescribir(CFG_REAL, "..\\..\\malo.jar", "1.5"));
        assertNull(CfgLanzador.reescribir(CFG_REAL, "aoe2radar-1.5.jar\r\napp.classpath=x", "1.5"));
        assertNull(CfgLanzador.reescribir(CFG_REAL, "aoe2radar-1.5.jar", "1.5\r\njava-options=-Dx"));
        assertNull(CfgLanzador.reescribir("basura", "aoe2radar-1.5.jar", "1.5"));
    }

    @Test void escribirAtomicoSustituyeSinDejarTemporales() throws IOException {
        Path cfg = tmp.resolve("aoe2radar.cfg");
        Files.writeString(cfg, CFG_REAL, StandardCharsets.ISO_8859_1);
        assertTrue(CfgLanzador.escribirAtomico(cfg, "nuevo".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals("nuevo", Files.readString(cfg));
        assertEquals(List.of(cfg), listar());
    }

    @Test void siElMovimientoFallaElCfgQuedaIntacto() throws IOException {
        Path cfg = tmp.resolve("aoe2radar.cfg");
        Files.writeString(cfg, CFG_REAL, StandardCharsets.ISO_8859_1);
        boolean ok = CfgLanzador.escribirAtomico(cfg, "a medias".getBytes(StandardCharsets.ISO_8859_1),
                (o, d) -> { throw new IOException("simulado"); });
        assertFalse(ok);
        assertEquals(CFG_REAL, Files.readString(cfg, StandardCharsets.ISO_8859_1), "sin respaldo de escritura directa");
        assertEquals(List.of(cfg), listar(), "el temporal se borra");
    }

    /** Windows real: con el .cfg abierto por otro sin compartir el borrado (el lanzador leyéndolo, un antivirus), el
     *  move atómico falla y el archivo sigue entero. */
    @EnabledOnOs(OS.WINDOWS)
    @Test void conElCfgBloqueadoPorOtroQuedaIntacto() throws IOException {
        Path cfg = tmp.resolve("aoe2radar.cfg");
        Files.writeString(cfg, CFG_REAL, StandardCharsets.ISO_8859_1);
        try (RandomAccessFile bloqueo = new RandomAccessFile(cfg.toFile(), "r")) {
            assertFalse(CfgLanzador.escribirAtomico(cfg, "nuevo".getBytes(StandardCharsets.ISO_8859_1)));
        }
        assertEquals(CFG_REAL, Files.readString(cfg, StandardCharsets.ISO_8859_1));
        assertEquals(List.of(cfg), listar());
    }

    private List<Path> listar() throws IOException {
        try (Stream<Path> s = Files.list(tmp)) { return s.toList(); }
    }
}
