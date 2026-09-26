package dev.tirador.aoe2radar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Prueba el comparador sin pantalla: si la red de seguridad tuviera un agujero, aquí se vería. */
class ComparadorCapturasTest {
    @TempDir Path dir;

    static BufferedImage imagen(Color fondo) {
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(fondo); g.fillRect(0, 0, 200, 100);
        g.setColor(Color.BLACK); g.drawString("12Tirador 1905", 20, 50);
        g.dispose();
        return img;
    }

    ComparadorCapturas comparador() { return new ComparadorCapturas(dir.resolve("ref"), dir.resolve("out"), false, Map.of()); }

    @Test void primeraVezGrabaYPasa_despuesIdenticaPasa() throws Exception {
        var c = comparador();
        assertTrue(c.comparar("a", imagen(Color.WHITE)).ok());
        assertTrue(Files.exists(dir.resolve("ref/a.png")));
        var r = c.comparar("a", imagen(Color.WHITE));
        assertTrue(r.ok());
        assertEquals(0, r.fraccion());
    }

    @Test void ruidoLevePorDebajoDeLaToleranciaPasa() throws Exception {
        var c = comparador();
        c.comparar("a", imagen(Color.WHITE));
        var r = c.comparar("a", imagen(new Color(240, 240, 240)));   // 15 por canal: como un cambio de antialiasing
        assertTrue(r.ok(), r.detalle());
        assertFalse(Files.exists(dir.resolve("out/a.diff.png")));
    }

    @Test void bloqueCambiadoFallaYDejaDiferencias() throws Exception {
        var c = comparador();
        c.comparar("a", imagen(Color.WHITE));
        BufferedImage cambiada = imagen(Color.WHITE);
        Graphics2D g = cambiada.createGraphics();
        g.setColor(Color.BLUE); g.fillRect(150, 10, 20, 20);   // 400 píxeles de 20.000 = 2 %
        g.dispose();
        var r = c.comparar("a", cambiada);
        assertFalse(r.ok());
        assertEquals(0.02, r.fraccion(), 1e-9);
        assertTrue(Files.exists(dir.resolve("out/a.png")));
        assertTrue(Files.exists(dir.resolve("out/a.diff.png")));
    }

    @Test void cambioDentroDeZonaIgnoradaNoCuenta() throws Exception {
        var c = comparador();
        c.comparar("a", imagen(Color.WHITE));
        BufferedImage cambiada = imagen(Color.WHITE);
        Graphics2D g = cambiada.createGraphics();
        g.setColor(Color.BLUE); g.fillRect(150, 10, 20, 20);
        g.dispose();
        var r = c.comparar("a", cambiada, List.of(new Rectangle(140, 0, 60, 40)));
        assertTrue(r.ok(), r.detalle());
        assertEquals(0, r.fraccion());
    }

    @Test void tamanoDistintoFalla() throws Exception {
        var c = comparador();
        c.comparar("a", imagen(Color.WHITE));
        assertFalse(c.comparar("a", new BufferedImage(201, 100, BufferedImage.TYPE_INT_RGB)).ok());
    }
}
