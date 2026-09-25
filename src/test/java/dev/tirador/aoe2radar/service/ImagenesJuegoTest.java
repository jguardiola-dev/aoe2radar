package dev.tirador.aoe2radar.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
 * ruta relativa fija («banderas»): NO se puede borrar entera al terminar (fuera del harness sería la carpeta de
 * banderas de verdad), así que cada test apunta en {@link #creados} solo los archivos que crea y el
 * {@code @AfterEach} borra uno a uno esos, igual que ui.IconosTest.
 */
class ImagenesJuegoTest {

    final List<Path> creados = new ArrayList<>();

    @AfterEach void limpiar() throws IOException {
        for (Path p : creados) Files.deleteIfExists(p);
        creados.clear();
    }

    private Path archivo(Path destino) throws IOException {
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, "png");
        creados.add(destino);
        return destino;
    }

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

    @Test void rutaBanderaDirectaEnBanderasDir() throws IOException {
        Path esperado = archivo(BANDERAS_DIR.resolve("rutadirectatest.png"));
        assertEquals(esperado, rutaBandera("rutadirectatest"));
    }

    @Test void rutaBanderaAnidadaComoElExtraerTodoDeWindows() throws IOException {
        Path esperado = archivo(BANDERAS_DIR.resolve("banderas").resolve("rutaanidadatest.png"));
        assertEquals(esperado, rutaBandera("rutaanidadatest"));
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
