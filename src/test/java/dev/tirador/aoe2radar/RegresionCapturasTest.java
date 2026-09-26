package dev.tirador.aoe2radar;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** El harness no enseña el mensaje real de control.json: el aviso nulo se encola detrás de la construcción de la
     *  ventana (que conecta la franja) y gana a la primera lectura de control.json. Se simula el orden de la app
     *  sin pantalla: construcción encolada, silenciado desde el hilo del test, y el aviso como lo encola cargarControl. */
    @Test void elHarnessSilenciaElMensajeDeControlDespuesDeConstruirLaVentana() throws Exception {
        var antes = dev.tirador.aoe2radar.app.Servicios.avisoControl;
        int[] mostrados = new int[1];
        try {
            javax.swing.SwingUtilities.invokeLater(() ->   // lo que hace CableadoCromo.montarVentana al construir la ventana
                    dev.tirador.aoe2radar.app.Servicios.avisoControl = (texto, alCerrar) -> mostrados[0]++);
            RegresionCapturas.silenciarMensajeControl();
            javax.swing.SwingUtilities.invokeLater(() ->   // lo que encola Servicios.cargarControl al llegar un mensaje
                    dev.tirador.aoe2radar.app.Servicios.avisoControl.accept("Mensaje real de sfr-data", () -> { }));
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            assertEquals(0, mostrados[0], "la franja no recibe el mensaje durante el harness");
        } finally {
            javax.swing.SwingUtilities.invokeAndWait(() -> dev.tirador.aoe2radar.app.Servicios.avisoControl = antes);
        }
    }

    @Test void todaNegraConImagenNegraPuraDaTrue() {
        assertTrue(RegresionCapturas.todaNegra(imagen(Color.BLACK)));
    }

    @Test void todaNegraConImagenOscuraPeroNoNegraPuraDaFalse() {
        assertFalse(RegresionCapturas.todaNegra(imagen(new Color(2, 2, 2))));
    }

    @Test void pareceBloqueadaSoloConLogonUiYUnaPrimeraFotoMuyDistinta() {
        assertTrue(RegresionCapturas.pareceBloqueada(0.88, true), "fondo de la pantalla de bloqueo + LogonUI");
        assertFalse(RegresionCapturas.pareceBloqueada(0.88, false), "sin LogonUI, un fallo grande es un fallo de verdad");
        assertFalse(RegresionCapturas.pareceBloqueada(0.02, true), "LogonUI de otra sesión y la foto casi igual: no");
    }

    @Test void todaNegraConUnSoloPixelNoNegroDaFalse() {
        BufferedImage img = imagen(Color.BLACK);
        img.setRGB(49, 49, Color.WHITE.getRGB());
        assertFalse(RegresionCapturas.todaNegra(img));
    }
}
