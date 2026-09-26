package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BarraEstado: el semáforo de la operación en curso (trabajando/opSerial, el vigilante del botón «Detener»)
 * y el toast flotante. Con componentes Swing reales (dentro de invokeAndWait, como en la app), pero sin red
 * ni pantalla visible: el Anfitrion es un doble que solo cuenta llamadas.
 */
class BarraEstadoTest {

    /** Cuenta cada llamada: así los tests comprueban «se llamó» y «con qué», sin tocar api.Cancelacion/api.Http de verdad. */
    static final class AnfitrionFalso implements BarraEstado.Anfitrion {
        int iniciarOperacion, operacionTerminada, pararOperacion, renovarHttp, continuarBuscando, abrirTwitch, abrirDonacion;
        boolean operacionEnCurso;
        String ultimaUrl;
        long ultimoMatchIdEspectado = -1;

        @Override public void iniciarOperacion() { iniciarOperacion++; }
        @Override public void marcarOperacionEnCurso(boolean on) { operacionEnCurso = on; }
        @Override public void operacionTerminada() { operacionTerminada++; }
        @Override public void pararOperacion() { pararOperacion++; }
        @Override public void renovarHttp() { renovarHttp++; }
        @Override public void continuarBuscando() { continuarBuscando++; }
        @Override public void abrirTwitch() { abrirTwitch++; }
        @Override public void abrirDonacion() { abrirDonacion++; }
        @Override public void abrirUrl(String url) { ultimaUrl = url; }
        @Override public void espectarPartida(long matchId) { ultimoMatchIdEspectado = matchId; }
    }

    private BarraEstado nuevo(AnfitrionFalso anfitrion, JFrame ventana) throws Exception {
        BarraEstado[] out = new BarraEstado[1];
        SwingUtilities.invokeAndWait(() -> {
            out[0] = new BarraEstado(ventana, anfitrion, "https://paypal.me/12Tirador/5EUR");
            out[0].construirFila();
        });
        return out[0];
    }

    @Test void trabajandoTrueMuestraProgresoYArmaElSemaforo() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        BarraEstado b = nuevo(anf, new JFrame());
        SwingUtilities.invokeAndWait(() -> {
            b.trabajando(true);
            assertTrue(b.progreso.isVisible(), "la barra de progreso se muestra al empezar");
            assertEquals(1, anf.iniciarOperacion, "una operación nueva limpia el freno de cancelación");
            assertTrue(anf.operacionEnCurso, "opEnCurso pasa a true");
            assertFalse(b.continuarBtn.isVisible(), "\"Continuar buscando\" se oculta al empezar una operación");
            assertTrue(b.detenerDescBtn.isVisible(), "\"Detener\" aparece mientras hay una operación en curso");
        });
    }

    @Test void trabajandoFalseOcultaProgresoYReactivaBotones() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        BarraEstado b = nuevo(anf, new JFrame());
        SwingUtilities.invokeAndWait(() -> {
            b.trabajando(true);
            b.detenerDescBtn.setEnabled(false);   // como si el usuario hubiera pulsado "Detener"
            b.trabajando(false);
            assertFalse(b.progreso.isVisible(), "la barra de progreso se oculta al terminar");
            assertEquals(1, anf.operacionTerminada, "al terminar, las otras vistas reactivan sus propios botones");
            assertFalse(anf.operacionEnCurso, "opEnCurso vuelve a false");
            assertFalse(b.detenerDescBtn.isVisible(), "\"Detener\" se oculta al terminar");
            assertTrue(b.detenerDescBtn.isEnabled(), "\"Detener\" queda listo para la próxima operación");
        });
    }

    @Test void opSerialCreceSoloConCadaOperacionNueva() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        BarraEstado b = nuevo(anf, new JFrame());
        SwingUtilities.invokeAndWait(() -> {
            long inicial = b.opSerial();
            b.trabajando(true);
            assertEquals(inicial + 1, b.opSerial(), "empezar una operación aumenta opSerial");
            b.trabajando(false);
            assertEquals(inicial + 1, b.opSerial(), "terminarla no lo toca");
            b.trabajando(true);
            assertEquals(inicial + 2, b.opSerial(), "la siguiente operación tiene un número distinto");
        });
    }

    @Test void elVigilanteDeDetenerSoloActuaSobreLaMismaOperacion() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        BarraEstado b = nuevo(anf, new JFrame());
        SwingUtilities.invokeAndWait(() -> {
            b.trabajando(true);   // operación #1
            b.detenerDescBtn.doClick();   // arma el vigilante para la operación #1 (serialDetenido = 1)
            assertEquals(1, anf.pararOperacion, "\"Detener\" pide parar la operación en curso");
            assertEquals(1, anf.renovarHttp, "\"Detener\" renueva el cliente HTTP para las peticiones siguientes");
            b.trabajando(true);   // una operación NUEVA empieza antes de que el vigilante dispare (op #2)
            dispararVigilante(b);
            assertTrue(b.progreso.isVisible(), "el vigilante de la operación #1 no debe apagar la #2");
        });
    }

    @Test void elVigilanteDeDetenerActuaSiSigueSiendoLaMismaOperacion() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        BarraEstado b = nuevo(anf, new JFrame());
        SwingUtilities.invokeAndWait(() -> {
            b.trabajando(true);
            b.detenerDescBtn.doClick();
            dispararVigilante(b);   // nadie empezó una operación nueva mientras tanto
            assertFalse(b.progreso.isVisible(), "sin operación nueva, el vigilante cierra la que se detuvo");
            assertEquals(1, anf.operacionTerminada, "el vigilante llama a trabajando(false), que avisa de fin de operación");
        });
    }

    /** Simula que pasaron los 5 s del Timer de "Detener" sin esperarlos de verdad: dispara su ActionListener a mano. */
    private static void dispararVigilante(BarraEstado b) {
        List<ActionListener> oyentes = new ArrayList<>(List.of(b.watchdogDetener.getActionListeners()));
        for (ActionListener al : oyentes) al.actionPerformed(null);
    }

    @Test void mostrarToastLoAnadeALaVentanaYOcultarToastLoQuita() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            assertNull(b.toast(), "sin toast al principio");
            int base = ventana.getLayeredPane().getComponentCount();   // el JRootPane ya trae su contentPane
            b.mostrarToast("Hera ha empezado una partida", 0);
            assertEquals(base + 1, ventana.getLayeredPane().getComponentCount(), "el toast se añade a la capa emergente");
            b.ocultarToast();
            assertNull(b.toast(), "ocultarToast lo quita");
            assertEquals(base, ventana.getLayeredPane().getComponentCount());
        });
    }

    @Test void mostrarToastConMatchIdOfreceEspectarYLoCierra() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            b.mostrarToast("Hera ha empezado una partida", 555);
            var botones = botonesDe(b.toast());
            assertTrue(botones.size() >= 2, "hay botón de espectar y de cerrar");
            botones.get(0).doClick();   // "Espectar"
            assertEquals(555L, anf.ultimoMatchIdEspectado, "el botón espectar pasa el matchId al Anfitrion");
            assertNull(b.toast(), "espectar también cierra el toast");
        });
    }

    private static List<javax.swing.JButton> botonesDe(javax.swing.JPanel toast) {
        List<javax.swing.JButton> out = new ArrayList<>();
        buscar(toast, out);
        return out;
    }

    private static void buscar(java.awt.Container c, List<javax.swing.JButton> out) {
        for (java.awt.Component comp : c.getComponents()) {
            if (comp instanceof javax.swing.JButton jb) out.add(jb);
            else if (comp instanceof java.awt.Container cc) buscar(cc, out);
        }
    }
}
