package dev.tirador.aoe2radar.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static dev.tirador.aoe2radar.cache.Directorios.LADDER_DIR;
import static dev.tirador.aoe2radar.cache.ImagenesMapa.MAPA_IMG_URL;
import static dev.tirador.aoe2radar.service.ImagenesJuego.BANDERAS_DIR;
import static dev.tirador.aoe2radar.service.ImagenesJuego.claveMapa;
import static dev.tirador.aoe2radar.service.ImagenesJuego.rutaBandera;
import static dev.tirador.aoe2radar.service.ImagenesJuego.rutaIconoCiv;
import static dev.tirador.aoe2radar.service.ImagenesJuego.rutaMapa;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.TT_DIR;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Rutas de disco de banderas, civs y mapas (ver service.ImagenesJuego): sin red, sin Swing. BANDERAS_DIR es una
 * ruta relativa fija («banderas»), así que estos tests crean sus archivos bajo el directorio de trabajo del
 * harness (target/harness, ver surefire) y los borran al terminar.
 */
class ImagenesJuegoTest {

    // ----- claveMapa ---------------------------------------------------

    @Test void claveMapaPreficreClaveConUrlConocida() {
        MAPA_IMG_URL.put("rm_arabia_test", "http://x/arabia.png");
        try {
            assertEquals("rm_arabia_test", claveMapa("rm_arabia_test", "arabia_test"));
        } finally { MAPA_IMG_URL.remove("rm_arabia_test"); }
    }

    @Test void claveMapaUsaNombreSiSoloEseTieneUrl() {
        MAPA_IMG_URL.put("arabia_test", "http://x/arabia.png");
        try {
            assertEquals("arabia_test", claveMapa("rm_arabia_test", "arabia_test"));
        } finally { MAPA_IMG_URL.remove("arabia_test"); }
    }

    @Test void claveMapaSinUrlConocidaCaeEnLaClave() {
        // ni "rm_desconocido_test" ni "desconocido_test" están en MAPA_IMG_URL
        assertEquals("rm_desconocido_test", claveMapa("rm_desconocido_test", "desconocido_test"));
    }

    @Test void claveMapaSinClaveCaeEnElNombre() {
        assertEquals("desconocido_test", claveMapa(null, "desconocido_test"));
    }

    // ----- rutaBandera ---------------------------------------------------

    @AfterEach void limpiarBanderas() throws IOException {
        if (Files.exists(BANDERAS_DIR)) {
            try (var s = Files.walk(BANDERAS_DIR)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } });
            }
        }
    }

    @Test void rutaBanderaDirectaEnBanderasDir() throws IOException {
        Files.createDirectories(BANDERAS_DIR);
        Path esperado = BANDERAS_DIR.resolve("zz.png");
        Files.writeString(esperado, "png");
        assertEquals(esperado, rutaBandera("zz"));
    }

    @Test void rutaBanderaAnidadaComoElExtraerTodoDeWindows() throws IOException {
        Files.createDirectories(BANDERAS_DIR.resolve("banderas"));
        Path esperado = BANDERAS_DIR.resolve("banderas").resolve("yy.png");
        Files.writeString(esperado, "png");
        assertEquals(esperado, rutaBandera("yy"));
    }

    @Test void rutaBanderaNullSiNoHayArchivoEnNinguno() {
        assertNull(rutaBandera("no_existe_test"));
    }

    // ----- rutaIconoCiv / rutaMapa ---------------------------------------------------

    @Test void rutaIconoCivNoCompruebaExistencia() {
        assertEquals(TT_DIR.resolve("img/Civs/aztecs.png"), rutaIconoCiv("aztecs"));
    }

    @Test void rutaMapaSaneaCaracteresRaros() {
        assertEquals(LADDER_DIR.resolve("mapas").resolve("rm_arabia.png"), rutaMapa("rm_arabia"));
        assertEquals(LADDER_DIR.resolve("mapas").resolve("black_forest.png"), rutaMapa("black forest"));
    }
}
