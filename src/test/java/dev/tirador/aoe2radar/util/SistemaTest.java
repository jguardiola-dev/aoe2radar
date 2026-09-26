package dev.tirador.aoe2radar.util;

import dev.tirador.aoe2radar.cache.Directorios;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Carpeta de datos de la app (arreglo F1 de la revisión 1.3). Empaquetada, todo cuelga de la carpeta del exe,
 * venga de donde venga el directorio de trabajo (el autoarranque de Windows lanza el exe desde System32). Sin
 * exe (mvn, tests, harness), las rutas siguen siendo relativas, idénticas a las de antes del arreglo.
 */
class SistemaTest {

    @Test void empaquetadaLaCarpetaBaseEsLaDelExe() {
        Path base = Sistema.carpetaBase("C:\\Apps\\aoe2radar\\aoe2radar.exe");
        assertEquals(Path.of("C:\\Apps\\aoe2radar"), base);
        assertEquals(Path.of("C:\\Apps\\aoe2radar\\config.properties"), base.resolve("config.properties"));
        assertEquals(Path.of("C:\\Apps\\aoe2radar\\sfrdata\\paises.txt"), base.resolve("sfrdata").resolve("paises.txt"));
    }

    @Test void sinExeLasRutasSiguenSiendoRelativasComoAntes() {
        assertEquals(Path.of(""), Sistema.carpetaBase(null));
        assertEquals(Path.of(""), Sistema.carpetaBase("  "));
        assertEquals(Path.of("config.properties"), Sistema.carpetaBase(null).resolve("config.properties"));
    }

    @Test void enLosTestsNoHayExeYLasConstantesNoCambian() {
        // Surefire no fija jpackage.app-path: las constantes deben ser exactamente las rutas relativas de siempre.
        assertEquals(Path.of("config.properties"), Config.CONFIG_FILE);
        assertEquals(Path.of("descargas.log"), Log.LOG_FILE);
        assertEquals(Path.of("sfrdata"), Directorios.LADDER_DIR);
        assertEquals(Path.of("banderas"), dev.tirador.aoe2radar.service.ImagenesJuego.BANDERAS_DIR);
        assertEquals(Path.of(""), Sistema.carpetaApp());
    }
}
