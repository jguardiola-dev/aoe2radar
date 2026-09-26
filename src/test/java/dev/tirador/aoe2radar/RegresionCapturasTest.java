package dev.tirador.aoe2radar;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La parte de RegresionCapturas.foto() que no necesita pantalla ni la app arrancada: todaNegra(), la señal de
 * "la pantalla está bloqueada" sobre una imagen ya capturada (nunca se crea un Robot aquí; las imágenes son de
 * memoria, como en ComparadorCapturasTest). El resto de foto()/asegurarTamanoRaiz() sí necesita la app y la
 * pantalla: lo prueba el harness completo (RegresionCapturas, -Dharness=si), fuera del alcance de esta tarea.
 */
class RegresionCapturasTest {

    static BufferedImage imagen(Color color) {
        BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(color); g.fillRect(0, 0, 50, 50);
        g.dispose();
        return img;
    }

    @Test void todaNegraConImagenNegraPuraDaTrue() {
        assertTrue(RegresionCapturas.todaNegra(imagen(Color.BLACK)));
    }

    @Test void todaNegraConImagenOscuraPeroNoNegraPuraDaFalse() {
        assertFalse(RegresionCapturas.todaNegra(imagen(new Color(2, 2, 2))));
    }

    @Test void todaNegraConUnSoloPixelNoNegroDaFalse() {
        BufferedImage img = imagen(Color.BLACK);
        img.setRGB(49, 49, Color.WHITE.getRGB());
        assertFalse(RegresionCapturas.todaNegra(img));
    }
}
