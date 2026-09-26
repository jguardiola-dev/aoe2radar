package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.tirador.aoe2radar.service.ImagenesJuego.BANDERAS_DIR;
import static dev.tirador.aoe2radar.service.ImagenesJuego.rutaIconoCiv;
import static dev.tirador.aoe2radar.service.ImagenesJuego.rutaMapa;
import static dev.tirador.aoe2radar.ui.Iconos.banderaHtml;
import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.ui.Iconos.iconoCiv;
import static dev.tirador.aoe2radar.ui.Iconos.iconoMapa;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Iconos de bandera, civ y mapa (ver ui.Iconos): un ImageIcon de un PNG temporal se decodifica sin pantalla, así
 * que estos tests no necesitan @TempDir (las rutas de service.ImagenesJuego son fijas y relativas) ni red: solo
 * el caso de "sin archivo en disco" para mapa, que devuelve null en el acto sin lanzar ningún hilo.
 */
class IconosTest {

    final List<Path> creados = new ArrayList<>();

    @AfterEach void limpiar() throws IOException {
        for (Path p : creados) Files.deleteIfExists(p);
        creados.clear();
    }

    private Path png(Path destino) throws IOException {
        Files.createDirectories(destino.getParent());
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(img, "png", destino.toFile());
        creados.add(destino);
        return destino;
    }

    // ----- bandera ---------------------------------------------------

    @Test void iconoBanderaLeeElArchivoDeDisco() throws IOException {
        png(BANDERAS_DIR.resolve("zztest.png"));
        ImageIcon ic = iconoBandera("ZZTEST");   // se normaliza a minúsculas
        assertNotNull(ic);
        assertTrue(ic.getIconWidth() > 0);
    }

    @Test void iconoBanderaNullSinArchivo() {
        assertNull(iconoBandera("no_existe_test"));
    }

    @Test void banderaHtmlConArchivoIncluyeLaImagen() throws IOException {
        Path p = png(BANDERAS_DIR.resolve("htmltest.png"));
        String html = banderaHtml("htmltest");
        assertTrue(html.contains(p.toUri().toString()));
    }

    @Test void banderaHtmlVacioSinArchivo() {
        assertEquals("", banderaHtml("no_existe_test2"));
    }

    // ----- civ ---------------------------------------------------

    @Test void iconoCivLeeElArchivoDeDiscoYEscala() throws IOException {
        png(rutaIconoCiv("aztecs_test"));
        ImageIcon ic = iconoCiv("aztecs_test", 18);
        assertNotNull(ic);
        assertEquals(18, ic.getIconWidth());
    }

    @Test void iconoCivNullSinArchivo() {
        assertNull(iconoCiv("civ_que_no_existe_test", 18));
    }

    // ----- mapa ---------------------------------------------------

    @Test void iconoMapaLeeLaMiniaturaYaDescargada() throws IOException {
        png(rutaMapa("mitest_map"));
        ImageIcon ic = iconoMapa("MiTest_Map", 20);
        assertNotNull(ic);
        assertEquals(20, ic.getIconWidth());
    }

    @Test void iconoMapaNullSinArchivoNiUrlConocidaYSinLanzarRed() {
        // ni archivo en disco ni URL conocida ni patrón: pedirMapa vuelve enseguida sin arrancar el hilo de descarga
        assertNull(iconoMapa("mapa_totalmente_desconocido_test", null, 18));
    }
}
