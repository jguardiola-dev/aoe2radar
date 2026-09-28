package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.util.Operaciones;
import org.junit.jupiter.api.Test;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BarraEstado: el semáforo de las operaciones en curso (empezar/terminar, opSerial, un freno por operación con
 * util.Operaciones, el vigilante del botón «Detener») y el toast flotante. Con componentes Swing reales (dentro de
 * invokeAndWait, como en la app), pero sin red ni pantalla visible: el Anfitrion es un doble que registra, en
 * orden, cada llamada que recibe, y los frenos son un Operaciones propio de cada test (no el de la app).
 */
class BarraEstadoTest {

    /** Registra cada llamada, en el orden en que llega: así los tests comprueban no solo «se llamó» sino
     *  «en qué orden», sin tocar api.Http de verdad. */
    static final class AnfitrionFalso implements BarraEstado.Anfitrion {
        final List<String> secuencia = new ArrayList<>();
        String ultimaUrl;
        long ultimoMatchIdEspectado = -1;

        @Override public void operacionTerminada() { secuencia.add("terminada"); }
        @Override public void renovarHttp() { secuencia.add("renovarHttp"); }
        @Override public void continuarBuscando() { secuencia.add("continuarBuscando"); }
        @Override public void abrirTwitch() { secuencia.add("abrirTwitch"); }
        @Override public void abrirDonacion() { secuencia.add("abrirDonacion"); }
        @Override public void abrirUrl(String url) { ultimaUrl = url; secuencia.add("abrirUrl"); }
        @Override public void espectarPartida(long matchId) { ultimoMatchIdEspectado = matchId; secuencia.add("espectarPartida"); }

        long veces(String evento) { return secuencia.stream().filter(evento::equals).count(); }
    }

    /** Los frenos de este test (cada test crea su BarraEstado con uno nuevo). */
    private Operaciones ops;

    private BarraEstado nuevo(AnfitrionFalso anfitrion, JFrame ventana) throws Exception {
        ops = new Operaciones();
        BarraEstado[] out = new BarraEstado[1];
        SwingUtilities.invokeAndWait(() -> {
            out[0] = new BarraEstado(ventana, anfitrion, "https://paypal.me/12Tirador/5EUR", ops);
            out[0].construirFila();
        });
        return out[0];
    }

    /** Detiene los Timer que el propio test pudiera haber armado (watchdog de "Detener", toast) y cierra la
     *  ventana de prueba: cada test crea la suya, y ninguna llega a mostrarse (sin pantalla). */
    private static void cerrar(BarraEstado b, JFrame ventana) {
        if (b.watchdogDetener != null) b.watchdogDetener.stop();
        if (b.toastTimer != null) b.toastTimer.stop();
        if (b.timerPausaApi != null) b.timerPausaApi.stop();
        ventana.dispose();
    }

    @Test void empezarOperacionMuestraProgresoYArmaSuFreno() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            long op = b.empezarOperacion();
            assertEquals(b.opSerial(), op, "devuelve su número");
            assertTrue(b.progreso.isVisible(), "la barra de progreso se muestra al empezar");
            assertTrue(ops.hayVivas(), "la operación queda viva, con su freno");
            assertFalse(ops.detenido(op), "freno suelto");
            assertTrue(anf.secuencia.isEmpty(), "empezar no avisa a nadie");
            assertFalse(b.continuarBtn.isVisible(), "\"Continuar buscando\" se oculta al empezar una operación");
            assertTrue(b.detenerDescBtn.isVisible(), "\"Detener\" aparece mientras hay una operación en curso");
            assertTrue(b.detenerDescBtn.isEnabled());
        });
        cerrar(b, ventana);
    }

    @Test void terminarLaUltimaOcultaProgresoYReactivaBotones() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            long op = b.empezarOperacion();
            b.detenerDescBtn.doClick();   // el usuario pulsa "Detener": queda deshabilitado
            assertFalse(b.detenerDescBtn.isEnabled());
            anf.secuencia.clear();   // solo interesa lo que dispara ESTE terminar
            b.terminarOperacion(op);
            assertEquals(List.of("terminada"), anf.secuencia, "cada vista reactiva sus propios botones");
            assertFalse(b.progreso.isVisible(), "la barra de progreso se oculta al terminar");
            assertFalse(ops.hayVivas());
            assertFalse(b.detenerDescBtn.isVisible(), "\"Detener\" se oculta al terminar");
            assertTrue(b.detenerDescBtn.isEnabled(), "\"Detener\" queda listo para la próxima operación");
            b.terminarOperacion(op);
            assertEquals(List.of("terminada"), anf.secuencia, "terminar dos veces la misma no avisa dos veces");
        });
        cerrar(b, ventana);
    }

    @Test void opSerialCreceSoloConCadaOperacionNueva() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            long inicial = b.opSerial();
            long op = b.empezarOperacion();
            assertEquals(inicial + 1, b.opSerial(), "empezar una operación aumenta opSerial");
            b.terminarOperacion(op);
            assertEquals(inicial + 1, b.opSerial(), "terminarla no lo toca");
            b.empezarOperacion();
            assertEquals(inicial + 2, b.opSerial(), "la siguiente operación tiene un número distinto");
        });
        cerrar(b, ventana);
    }

    // ----- un freno por operación (decisión de Jorge, 1.3) -----

    @Test void detener_conDosOperacionesVivas_paraSoloLaMasReciente_yLuegoPasaALaAnterior() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            long busqueda = b.empezarOperacion();
            long descarga = b.empezarOperacion();   // una descarga empezada durante la búsqueda
            b.detenerDescBtn.doClick();
            assertTrue(ops.detenido(descarga), "Detener para la más reciente");
            assertFalse(ops.detenido(busqueda), "y SOLO esa: la búsqueda sigue");
            assertFalse(b.detenerDescBtn.isEnabled(), "mientras la detenida siga viva, Detener espera");
            b.terminarOperacion(descarga);
            assertTrue(b.progreso.isVisible(), "la búsqueda sigue viva: el progreso no se apaga");
            assertTrue(b.detenerDescBtn.isVisible() && b.detenerDescBtn.isEnabled(), "Detener pasa a la búsqueda");
            assertEquals(0, anf.veces("terminada"), "con la búsqueda viva, las vistas no reactivan sus botones");
            b.detenerDescBtn.doClick();
            assertTrue(ops.detenido(busqueda), "ahora sí para la búsqueda");
            b.terminarOperacion(busqueda);
            assertFalse(b.progreso.isVisible());
            assertFalse(b.detenerDescBtn.isVisible(), "sin operaciones vivas, Detener se oculta");
            assertEquals(1, anf.veces("terminada"));
        });
        cerrar(b, ventana);
    }

    @Test void detenerPorSuBotonPropio_repintaElDetenerDeLaBarra() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            long anterior = b.empezarOperacion();
            long reciente = b.empezarOperacion();
            b.detener(anterior);   // p. ej. la × de «Partidas de:» sobre una búsqueda que ya no es la última
            assertTrue(ops.detenido(anterior));
            assertTrue(b.detenerDescBtn.isEnabled(), "Detener sigue apuntando a la reciente, que no está detenida");
            b.detener(reciente);   // «Buscar partidas» → «Detener» sobre la última viva
            assertFalse(b.detenerDescBtn.isEnabled(), "no queda habilitado apuntando a una operación ya detenida");
            assertTrue(b.detenerDescBtn.isVisible() && b.progreso.isVisible());
        });
        cerrar(b, ventana);
    }

    @Test void terminarLaAnteriorConOtraViva_noApagaNadaYDetenerSigueEnLaReciente() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            long primera = b.empezarOperacion();
            long segunda = b.empezarOperacion();
            b.terminarOperacion(primera);
            assertTrue(b.progreso.isVisible() && b.detenerDescBtn.isVisible() && b.detenerDescBtn.isEnabled());
            assertEquals(0, anf.veces("terminada"));
            b.detenerDescBtn.doClick();
            assertTrue(ops.detenido(segunda));
        });
        cerrar(b, ventana);
    }

    @Test void elVigilanteDeDetenerSoloActuaSobreLaMismaOperacion() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            long primera = b.empezarOperacion();   // operación #1
            b.detenerDescBtn.doClick();   // arma el vigilante para la operación #1
            assertTrue(ops.detenido(primera), "\"Detener\" pide parar la operación en curso");
            assertEquals(1, anf.veces("renovarHttp"), "\"Detener\" renueva el cliente HTTP para las peticiones siguientes");
            long segunda = b.empezarOperacion();   // una operación NUEVA empieza antes de que el vigilante dispare (op #2)
            assertTrue(b.detenerDescBtn.isEnabled(), "Detener apunta ya a la nueva, que no está detenida");
            dispararVigilante(b);
            assertTrue(b.progreso.isVisible(), "el vigilante de la operación #1 no debe apagar la #2");
            assertFalse(ops.detenido(segunda), "ni pararla");
            assertEquals(0, anf.veces("terminada"));
            assertFalse(List.of("Detenido.", "Stopped.").contains(b.status.getText()), "ni pisar el mensaje de la operación viva");
        });
        cerrar(b, ventana);
    }

    @Test void elVigilanteDeDetenerActuaSiSigueSiendoLaMismaOperacion() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            long op = b.empezarOperacion();
            b.detenerDescBtn.doClick();
            dispararVigilante(b);   // la detenida no terminó en 5 s y nadie empezó otra
            assertFalse(b.progreso.isVisible(), "sin operación nueva, el vigilante cierra la que se detuvo");
            assertEquals(1, anf.veces("terminada"), "y avisa de fin de operación");
            assertEquals("Detenido.", b.status.getText());
            assertTrue(ops.detenido(op), "su freno sigue puesto: si su hilo aún espera en el freno, lo sigue viendo");
            b.terminarOperacion(op);   // el done() de la operación llega por fin
            assertEquals(1, anf.veces("terminada"), "no avisa dos veces");
        });
        cerrar(b, ventana);
    }

    /** Simula que pasaron los 5 s del Timer de "Detener" sin esperarlos de verdad: dispara su ActionListener a
     *  mano. Cada clic en "Detener" SUSTITUYE la referencia {@code watchdogDetener} por un Timer nuevo, pero
     *  eso no cambia nada en producción: el Timer anterior (si lo hubiera) sigue vivo en la cola de Swing con
     *  su propio número de operación capturado por el lambda, y solo actúa sobre esa. */
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
        cerrar(b, ventana);
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
        cerrar(b, ventana);
    }

    // ----- Aviso de pausa por 429 (decisión de Jorge, DEUDA 45) --------------------------------------------

    @Test void mostrarPausaApiPintaLaCuentaAtrasEnStatusInmediatamente() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            b.mostrarPausaApi(5);
            assertTrue(b.status.getText().contains("5"),
                    "la cuenta atrás se pinta ya, sin esperar al primer tick del Timer");
        });
        cerrar(b, ventana);
    }

    @Test void tickPausaBajaLaCuentaCadaSegundoYAlLlegarACeroLimpiaSuPropioTexto() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            b.mostrarPausaApi(2);
            assertTrue(b.status.getText().contains("2"));
            b.tickPausa();
            assertTrue(b.status.getText().contains("1"), "un tick: baja a 1");
            b.tickPausa();
            assertEquals("", b.status.getText(), "al llegar a 0 se limpia: nadie ha pintado nada encima");
        });
        cerrar(b, ventana);
    }

    @Test void siOtroMensajeTapaLaCuentaAtrasAlLlegarACeroNoLoBorra() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            b.mostrarPausaApi(1);
            b.status.setText("Buscando a Fulanito…");   // otra vista pinta encima mientras corre la cuenta
            b.tickPausa();   // llega a 0
            assertEquals("Buscando a Fulanito…", b.status.getText(),
                    "no se borra un mensaje que no es el de la cuenta atrás");
        });
        cerrar(b, ventana);
    }

    /** Hallazgo del revisor: en un tick INTERMEDIO (no el que llega a 0), si otro mensaje ya tapó la cuenta
     *  atrás, no hay que repintar encima — se deja de tocar `status` hasta el siguiente aviso. */
    @Test void tickPausaIntermedioNoRepintaSiOtroMensajeYaTapoLaCuenta() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            b.mostrarPausaApi(5);
            b.status.setText("otro");   // una vista pinta encima antes del siguiente tick
            b.tickPausa();   // 5 -> 4, intermedio
            assertEquals("otro", b.status.getText(), "un tick intermedio no repinta sobre un mensaje ajeno");
        });
        cerrar(b, ventana);
    }

    /** Mismo hallazgo: si lo que tapa la cuenta es un «Consultando…»/«Checking…» (PartidasView, LiveNow), el
     *  tick SÍ debe reclamar `status`: es justo el mensaje al que la cuenta atrás tiene que ganar. */
    @Test void tickPausaIntermedioSiRepintaSobreUnConsultando() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            b.mostrarPausaApi(5);
            // El prefijo exacto según el idioma activo (I18n.IDIOMA): "Consultando"/"Checking", igual que
            // PartidasView.download y LiveNowPresenter.
            b.status.setText(dev.tirador.aoe2radar.util.I18n.t("Consultando", "Checking") + " a Fulanito…");
            b.tickPausa();   // 5 -> 4, intermedio
            assertTrue(b.status.getText().contains("4"), "un \"Consultando...\" sí se tapa con la cuenta atrás");
        });
        cerrar(b, ventana);
    }

    @Test void unSegundoAvisoMientrasCorreReiniciaLaCuentaConElMismoTimer() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            b.mostrarPausaApi(5);
            b.tickPausa();   // 4
            javax.swing.Timer primero = b.timerPausaApi;
            b.mostrarPausaApi(10);   // segundo aviso: reinicia con el nuevo valor
            assertSame(primero, b.timerPausaApi, "sigue siendo el mismo Timer, nunca uno nuevo");
            assertTrue(b.status.getText().contains("10"), "la cuenta se reinicia con el nuevo valor");
        });
        cerrar(b, ventana);
    }

    @Test void tooltipDeDetenerDiceElTimeoutReal() throws Exception {
        // F14 (revisión 1.3): el tooltip decía 25 s; el timeout de api.Http es 15 s, como dice el propio estado.
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            String tip = b.detenerDescBtn.getToolTipText();
            assertTrue(tip.contains("15 s"), tip);
            assertFalse(tip.contains("25 s"), tip);
        });
        cerrar(b, ventana);
    }

    @Test void mostrarPausaApiConSegundosCeroNoHaceNada() throws Exception {
        AnfitrionFalso anf = new AnfitrionFalso();
        JFrame ventana = new JFrame();
        BarraEstado b = nuevo(anf, ventana);
        SwingUtilities.invokeAndWait(() -> {
            String textoPrevio = b.status.getText();
            b.mostrarPausaApi(0);
            assertEquals(textoPrevio, b.status.getText(), "sin segundos que contar, no toca status");
            assertNull(b.timerPausaApi, "tampoco arma el Timer");
        });
        cerrar(b, ventana);
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
