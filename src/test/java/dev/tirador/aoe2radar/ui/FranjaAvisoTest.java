package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La franja del mando a distancia (decisión de Jorge, 1.3): se queda hasta la ×, solo entonces se marca como visto,
 * no se repite y un mensaje distinto sustituye al anterior. Componentes reales dentro de invokeAndWait, sin pantalla.
 */
class FranjaAvisoTest {

    private static void enEdt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }

    @Test void naceOcultaYSinMensajeNoOcupaSitio() throws Exception {
        enEdt(() -> {
            JPanel ventana = new JPanel(new BorderLayout());
            FranjaAviso f = new FranjaAviso();
            ventana.add(f, BorderLayout.NORTH);
            ventana.add(new JPanel(), BorderLayout.CENTER);
            ventana.setSize(800, 600);
            ventana.doLayout();
            assertFalse(f.isVisible(), "sin mensaje no se ve (el harness no tiene mensaje: capturas intactas)");
            assertEquals(0, f.getHeight());
            assertNull(f.textoActual());
            assertFalse(f.cerrarBtn.isFocusable(), "la × no roba el foco");
        });
    }

    @Test void mostrarLaEnsenaSinMarcarlaYLaXLaMarcaUnaVez() throws Exception {
        int[] marcado = new int[1];
        enEdt(() -> {
            FranjaAviso f = new FranjaAviso();
            f.mostrar("Mantenimiento esta noche", () -> marcado[0]++);
            assertTrue(f.isVisible());
            assertEquals("Mantenimiento esta noche", f.texto.getText());
            assertEquals("Mantenimiento esta noche", f.texto.getToolTipText());
            assertEquals(0, marcado[0], "enseñarla no la marca como vista: solo la ×");
            f.cerrarBtn.doClick();
            assertFalse(f.isVisible());
            assertNull(f.textoActual());
            assertEquals(1, marcado[0], "la × la marca como vista");
            f.cerrarBtn.doClick();
            assertEquals(1, marcado[0], "una sola vez");
        });
    }

    @Test void laRecargaDeCadaHoraNoLaDuplicaNiLaResucita() throws Exception {
        int[] marcado = new int[1];
        enEdt(() -> {
            FranjaAviso f = new FranjaAviso();
            f.mostrar("Aviso", () -> marcado[0]++);
            f.mostrar("Aviso", () -> marcado[0] += 10);   // la hora siguiente, aún sin cerrar
            assertTrue(f.isVisible());
            f.cerrarBtn.doClick();
            assertEquals(1, marcado[0], "se marca con el primero, una vez");
            f.mostrar("Aviso", () -> marcado[0] += 100);   // llega antes de que se guarde control_msg_visto
            assertFalse(f.isVisible(), "cerrado: no vuelve a salir");
            assertEquals(1, marcado[0]);
        });
    }

    @Test void otroMensajeSustituyeAlAnteriorSinMarcarlo() throws Exception {
        int[] marcadoA = new int[1], marcadoB = new int[1];
        enEdt(() -> {
            FranjaAviso f = new FranjaAviso();
            f.mostrar("Viejo", () -> marcadoA[0]++);
            f.mostrar("Nuevo", () -> marcadoB[0]++);
            assertTrue(f.isVisible());
            assertEquals("Nuevo", f.texto.getText());
            f.cerrarBtn.doClick();
            assertEquals(0, marcadoA[0], "el sustituido no se llegó a cerrar: no se marca");
            assertEquals(1, marcadoB[0]);
        });
    }

    @Test void mensajeVacioNoHaceNada() throws Exception {
        enEdt(() -> {
            FranjaAviso f = new FranjaAviso();
            f.mostrar("  ", () -> { });
            assertFalse(f.isVisible());
        });
    }

    @Test void largoSeRecortaADosLineasConElEnteroEnElTooltip() {
        String larga = "x".repeat(FranjaAviso.MAX_CARACTERES + 40);
        String una = FranjaAviso.textoFranja(larga);
        assertEquals(FranjaAviso.MAX_CARACTERES, una.length());
        assertTrue(una.endsWith("…"));
        assertEquals(larga, FranjaAviso.tooltip(larga));

        String tres = "uno <b>\ndos & más\ntres";
        String html = FranjaAviso.textoFranja(tres);
        assertEquals("<html>uno &lt;b&gt;<br>dos &amp; más …</html>", html, "dos líneas, escapadas, y «…» por la que falta");
        assertEquals("<html>uno &lt;b&gt;<br>dos &amp; más<br>tres</html>", FranjaAviso.tooltip(tres));
        assertEquals("corto", FranjaAviso.textoFranja("corto"));
    }

    @Test void elGranateCambiaConElTema() {
        boolean antes = Tema.temaOscuroActivo;
        try {
            Tema.temaOscuroActivo = false;
            assertEquals(0x8B1E2D, Tema.granate().getRGB() & 0xFFFFFF);
            Tema.temaOscuroActivo = true;
            assertNotEquals(Tema.GRANATE_CLARO, Tema.granate(), "en oscuro, un granate más claro");
        } finally {
            Tema.temaOscuroActivo = antes;
        }
    }
}
