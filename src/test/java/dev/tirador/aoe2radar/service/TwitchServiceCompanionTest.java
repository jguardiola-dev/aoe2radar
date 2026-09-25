package dev.tirador.aoe2radar.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TwitchServiceCompanion.decodificarYEscalar sin red: genera un JPEG pequeño en memoria y comprueba que el
 * PixelGrabber (solo java.awt.image, sin Component/MediaTracker/Graphics2D) da los mismos ARGB que dibujar a mano
 * el getScaledInstance sobre un BufferedImage TYPE_INT_ARGB (la forma "de libro" de leer esos píxeles).
 */
class TwitchServiceCompanionTest {

    /** Un JPEG de 8×8 con un patrón de colores (no un color plano: para que un fallo de escalado sea detectable). */
    private static byte[] jpegDePrueba() throws Exception {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                img.setRGB(x, y, new Color(x * 30, y * 30, 128).getRGB());
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, "jpg", out));
        return out.toByteArray();
    }

    /** La misma decodificación + escalado, pero leyendo los píxeles dibujando a mano (Graphics2D.drawImage). */
    private static int[] escalarADibujando(byte[] jpeg, int ancho, int alto) throws Exception {
        BufferedImage decodificada = ImageIO.read(new ByteArrayInputStream(jpeg));
        Image esc = decodificada.getScaledInstance(ancho, alto, Image.SCALE_SMOOTH);
        BufferedImage lienzo = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = lienzo.createGraphics();
        // MediaTracker no hace falta aquí: se usa solo para tener un punto de comparación independiente del
        // PixelGrabber; si getScaledInstance no estuviera listo, este test también lo notaría (drawImage no pintaría).
        java.awt.MediaTracker mt = new java.awt.MediaTracker(new java.awt.Component() { });
        mt.addImage(esc, 0);
        mt.waitForID(0);
        g.drawImage(esc, 0, 0, null);
        g.dispose();
        return lienzo.getRGB(0, 0, ancho, alto, null, 0, ancho);
    }

    @Test void decodificarYEscalar_da_los_mismos_pixeles_que_dibujar_a_mano() throws Exception {
        byte[] jpeg = jpegDePrueba();
        TwitchService.Miniatura m = TwitchServiceCompanion.decodificarYEscalar(jpeg, 96, 54);
        assertNotNull(m);
        assertEquals(96, m.ancho());
        assertEquals(54, m.alto());
        int[] esperado = escalarADibujando(jpeg, 96, 54);
        assertArrayEquals(esperado, m.pixelesArgb());
    }

    @Test void decodificarYEscalar_respeta_el_tamano_pedido_aunque_la_fuente_sea_otro() throws Exception {
        byte[] jpeg = jpegDePrueba();   // 8×8
        TwitchService.Miniatura m = TwitchServiceCompanion.decodificarYEscalar(jpeg, 10, 5);
        assertNotNull(m);
        assertEquals(10, m.ancho());
        assertEquals(5, m.alto());
        assertEquals(50, m.pixelesArgb().length);
    }

    @Test void decodificarYEscalar_con_bytes_invalidos_devuelve_null() {
        assertNull(TwitchServiceCompanion.decodificarYEscalar(new byte[]{ 1, 2, 3 }, 96, 54));
    }
}
