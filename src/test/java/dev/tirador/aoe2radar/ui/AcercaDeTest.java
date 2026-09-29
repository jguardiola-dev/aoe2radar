package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.tirador.aoe2radar.util.Identidad;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AcercaDe sin pantalla: cargarLogo() solo decodifica una imagen (no crea ningún componente Swing), e
 * icoBytes() es la generación real de bytes .ico que usa generarIco(); probarla escribiendo en una carpeta
 * temporal ejercita la misma lógica sin que el test escriba logo.ico en el directorio de trabajo real del
 * proceso (que es lo que haría generarIco() con su ruta relativa fija).
 */
class AcercaDeTest {

    @Test void cargarLogoDevuelveUnaImagenNoNula() {
        Image logo = AcercaDe.cargarLogo();
        assertNotNull(logo, "cargarLogo debe encontrar al menos el logo embebido (LOGO_B64)");
    }

    @Test void icoBytesGeneraUnIcoValidoConSeisTamanos(@TempDir Path tmp) throws Exception {
        Image logo = AcercaDe.cargarLogo();
        assertNotNull(logo);
        byte[] datos = AcercaDe.icoBytes(logo);

        Path ico = tmp.resolve("logo.ico");
        Files.write(ico, datos);
        byte[] leido = Files.readAllBytes(ico);

        assertTrue(leido.length > 6, "el .ico debe tener al menos la cabecera ICONDIR");
        // Cabecera ICONDIR (little-endian): reservado=0, tipo=1 (icono), 6 imágenes (16/24/32/48/64/256).
        assertEquals(0, shortLE(leido, 0));
        assertEquals(1, shortLE(leido, 2));
        assertEquals(6, shortLE(leido, 4));
    }

    /** 1.4.1: las Game Content Usage Rules exigen un enlace a ellas junto al aviso de Microsoft. La etiqueta lo
     *  enseña y, al hacer clic, pide a la ventana que abra esa URL (y ninguna otra). */
    @Test void elEnlaceALasReglasDeMicrosoftAbreSuUrl() throws Exception {
        List<String> abiertas = new ArrayList<>();
        JLabel[] l = new JLabel[1];
        SwingUtilities.invokeAndWait(() -> l[0] = AcercaDe.enlaceReglas(abiertas::add));
        assertEquals("https://www.xbox.com/en-US/developers/rules", Identidad.REGLAS_MICROSOFT_URL);
        assertTrue(l[0].getText().contains("www.xbox.com/en-US/developers/rules"), l[0].getText());
        assertTrue(l[0].getText().contains("Game Content Usage Rules"), l[0].getText());
        assertEquals(Cursor.HAND_CURSOR, l[0].getCursor().getType());
        assertTrue(abiertas.isEmpty());
        SwingUtilities.invokeAndWait(() -> l[0].dispatchEvent(
                new MouseEvent(l[0], MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 2, 2, 1, false, MouseEvent.BUTTON1)));
        assertEquals(List.of(Identidad.REGLAS_MICROSOFT_URL), abiertas);
    }

    private static int shortLE(byte[] b, int offset) {
        return (b[offset] & 0xFF) | ((b[offset + 1] & 0xFF) << 8);
    }
}
