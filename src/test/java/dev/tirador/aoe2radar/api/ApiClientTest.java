package dev.tirador.aoe2radar.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Caracterización de ApiClient (antes httpText / httpText429 de la app): sin red, con un transporte falso que
 * responde lo que el test quiera y un Throttle espía que apunta qué le llega.
 */
class ApiClientTest {

    static final String COMPANION = Http.API + "/matches?profile_ids=1&page=1";
    static final String STEAM = "https://steamcommunity.com/profiles/1/ajaxaliases";

    /** Responde en orden lo que se le dé (un estado HTTP o una excepción que lanzar) y apunta cada URL pedida. */
    static final class TransporteFalso implements Transporte {
        final Deque<Object> respuestas = new ArrayDeque<>();
        final List<String> pedidas = new ArrayList<>();
        Runnable trasCadaPeticion = () -> { };
        TransporteFalso responde(Object... r) { respuestas.addAll(List.of(r)); return this; }
        @Override public Respuesta get(String url) throws IOException, InterruptedException {
            pedidas.add(url);
            trasCadaPeticion.run();
            Object r = respuestas.isEmpty() ? 200 : respuestas.poll();
            if (r instanceof IOException e) throw e;
            if (r instanceof InterruptedException e) throw e;
            if (r instanceof RuntimeException e) throw e;
            int e = (Integer) r;
            return new Respuesta(e, "cuerpo " + e);
        }
    }

    /** Apunta qué le piden; el 429 siempre «pausa» 60 s. */
    static final class ThrottleEspia implements Throttle {
        int adquiridas, cuatrocientosVeintinueve, exitos;
        @Override public void adquirir() { adquiridas++; }
        @Override public long registrar429() { cuatrocientosVeintinueve++; return 60_000; }
        @Override public void registrarExito() { exitos++; }
    }

    final ThrottleEspia throttle = new ThrottleEspia();
    final TransporteFalso red = new TransporteFalso();
    final List<Long> avisos = new ArrayList<>();
    boolean detenida;
    final ApiClient api = new ApiClient(throttle, red, avisos::add, () -> detenida);

    @AfterEach void limpiarInterrupcion() { Thread.interrupted(); }

    // ----- texto (antes httpText)

    @Test void textoDevuelveElCuerpoYFrenaSoloAlCompanion() throws Exception {
        assertEquals("cuerpo 200", api.texto(COMPANION));
        assertEquals(1, throttle.adquiridas);
        api.texto(STEAM);
        assertEquals(1, throttle.adquiridas, "Steam no pasa por el freno");
    }

    @Test void textoLanzaHttpNnnSiNoEs2xx() {
        red.responde(500);
        IOException e = assertThrows(IOException.class, () -> api.texto(COMPANION));
        assertEquals("HTTP 500", e.getMessage());
    }

    @Test void caracterizacion_textoNoRegistraEl429() {
        // Rareza de la 1.1: httpText a secas deja pasar el 429 sin contarlo al freno (paso B lo cambia).
        red.responde(429);
        assertThrows(IOException.class, () -> api.texto(COMPANION));
        assertEquals(0, throttle.cuatrocientosVeintinueve);
        assertTrue(avisos.isEmpty());
    }

    @Test void unDetenerRealCancelaAntesDePedirNada() {
        detenida = true;
        Thread.currentThread().interrupt();
        assertThrows(InterruptedException.class, () -> api.texto(COMPANION));
        assertTrue(red.pedidas.isEmpty());
        assertEquals(0, throttle.adquiridas, "la cancelación va antes que el freno");
    }

    @Test void unaInterrupcionResidualSinDetenerSeLimpiaYSigue() throws Exception {
        detenida = false;
        Thread.currentThread().interrupt();
        assertEquals("cuerpo 200", api.texto(COMPANION));
        assertFalse(Thread.currentThread().isInterrupted(), "la interrupción residual se consume");
    }

    // ----- textoCon429 (antes httpText429)

    @Test void exitoDelCompanionSeCuenta() throws Exception {
        api.textoCon429(COMPANION);
        assertEquals(1, throttle.exitos);
    }

    @Test void un429DelCompanionPausaAvisaYReintenta() throws Exception {
        red.responde(429, 200);
        assertEquals("cuerpo 200", api.textoCon429(COMPANION));
        assertEquals(1, throttle.cuatrocientosVeintinueve);
        assertEquals(List.of(60L), avisos, "aviso en segundos para la barra de estado");
        assertEquals(2, red.pedidas.size());
        assertEquals(0, throttle.exitos, "caracterización: el éxito en un reintento no se cuenta");
    }

    @Test void dosReintentosComoMaximo() {
        red.responde(429, 429, 429);
        IOException e = assertThrows(IOException.class, () -> api.textoCon429(COMPANION));
        assertEquals("HTTP 429", e.getMessage());
        assertEquals(3, red.pedidas.size());
        assertEquals(2, throttle.cuatrocientosVeintinueve);
    }

    @Test void un429DeSteamNoTocaElFrenoDelCompanion() throws Exception {
        red.responde(429, 200);
        assertEquals("cuerpo 200", api.textoCon429(STEAM));
        assertEquals(0, throttle.cuatrocientosVeintinueve);
        assertTrue(avisos.isEmpty());
        assertEquals(0, throttle.exitos, "el éxito de Steam tampoco cuenta");
    }

    @Test void un429YLuegoOtroErrorSaleConEseError() {
        red.responde(429, 500);
        IOException e = assertThrows(IOException.class, () -> api.textoCon429(COMPANION));
        assertEquals("HTTP 500", e.getMessage());
        assertEquals(2, red.pedidas.size());
        assertEquals(1, throttle.cuatrocientosVeintinueve);
    }

    @Test void unTimeoutDelTransporteNoSeReintentaYSaleTalCual() {
        IOException timeout = new IOException("timed out");
        red.responde(timeout);
        assertSame(timeout, assertThrows(IOException.class, () -> api.textoCon429(COMPANION)));
        assertEquals(1, red.pedidas.size());
    }

    @Test void unaInterrupcionDelTransporteSaleSinEnvolver() {
        InterruptedException i = new InterruptedException("cortado");
        red.responde(i);
        assertSame(i, assertThrows(InterruptedException.class, () -> api.textoCon429(COMPANION)));
    }

    @Test void unErrorInesperadoSaleComoIOException() {
        red.responde(new IllegalArgumentException("URI mala"));
        IOException e = assertThrows(IOException.class, () -> api.textoCon429(COMPANION));
        assertEquals("URI mala", e.getMessage());
    }

    @Test void unDetenerDuranteElReintentoCortaSinMasPeticiones() {
        red.responde(429);
        red.trasCadaPeticion = () -> { detenida = true; Thread.currentThread().interrupt(); };
        InterruptedException e = assertThrows(InterruptedException.class, () -> api.textoCon429(COMPANION));
        assertEquals("detenido", e.getMessage());
        assertEquals(1, red.pedidas.size());
    }

    @Test void caracterizacion_unErrorCuyoMensajeContiene429SeReintenta() {
        // Rareza de la 1.1: el 429 se detecta por el texto del mensaje, no por el estado.
        red.responde(new IOException("proxy: 429 conexiones"), 200);
        assertDoesNotThrow(() -> api.textoCon429(COMPANION));
        assertEquals(2, red.pedidas.size());
    }

    @Test void otrosErroresNoSeReintentan() {
        red.responde(500);
        assertThrows(IOException.class, () -> api.textoCon429(COMPANION));
        assertEquals(1, red.pedidas.size());
    }
}
