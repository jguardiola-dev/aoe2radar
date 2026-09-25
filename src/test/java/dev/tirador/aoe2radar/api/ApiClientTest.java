package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.util.RelojFalso;
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
        int adquiridas, cuatrocientosVeintinueve;
        @Override public void adquirir(java.util.function.BooleanSupplier cancelar) { adquiridas++; }
        @Override public long registrar429() { cuatrocientosVeintinueve++; return 60_000; }
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
        assertEquals(0, throttle.cuatrocientosVeintinueve, "un 500 no es un 429");
        assertTrue(avisos.isEmpty());
    }

    @Test void textoCuentaEl429DelCompanionAunqueNoReintente() {
        // Cambio a propósito (fase 2, paso B): en la 1.1 httpText a secas dejaba pasar el 429 sin contarlo al freno.
        red.responde(429);
        IOException e = assertThrows(IOException.class, () -> api.texto(COMPANION));
        assertEquals("HTTP 429", e.getMessage(), "quien llama recibe lo mismo que antes");
        assertEquals(1, throttle.cuatrocientosVeintinueve);
        assertEquals(List.of(60L), avisos);
        assertEquals(1, red.pedidas.size(), "texto no reintenta");
    }

    @Test void textoCuentaEl429DeCualquierHostDelCompanion() {
        red.responde(429);
        assertThrows(IOException.class, () -> api.texto("https://api.aoe2companion.com/twitch/live?game=13389"));
        assertEquals(1, throttle.cuatrocientosVeintinueve);
    }

    @Test void textoDetectaEl429PorElEstadoNoPorElMensaje() {
        // Igual que textoCon429 desde la fase 4 (antes este lo miraba en el texto del mensaje).
        red.responde(new IOException("proxy: 429 conexiones"));
        assertThrows(IOException.class, () -> api.texto(COMPANION));
        assertEquals(0, throttle.cuatrocientosVeintinueve);
    }

    @Test void conElFrenoRealUn429EnTextoHaceEsperarALaLlamadaSiguiente() throws Exception {
        RelojFalso reloj = new RelojFalso();
        ApiClient conFrenoReal = new ApiClient(new ThrottleCubo(reloj), red, avisos::add, () -> false);
        red.responde(429, 200);
        assertThrows(IOException.class, () -> conFrenoReal.texto(COMPANION));
        long antes = reloj.dormido;
        conFrenoReal.texto(COMPANION);
        assertEquals(60_000, reloj.dormido - antes, "el efecto buscado: tras el 429, todos esperan la pausa");
    }

    @Test void conElFrenoRealElReintentoTrasLaPausaEsUnEpisodioNuevo() throws Exception {
        RelojFalso reloj = new RelojFalso();
        ApiClient conFrenoReal = new ApiClient(new ThrottleCubo(reloj), red, avisos::add, () -> false);
        red.responde(429, 429, 200);
        assertEquals("cuerpo 200", conFrenoReal.textoCon429(COMPANION));
        assertEquals(List.of(60L, 120L), avisos, "el segundo 429 llega tras dormir la pausa: escala");
    }

    @Test void conElFrenoRealTres429SeguidosCuentanLosTres() {
        // Fase 4 (DEUDA, asimetría): el tercero salía sin registrar. Cada reintento duerme la pausa anterior, así que
        // cada 429 es un episodio nuevo y escala, igual que si la llamada siguiente fuera por texto().
        RelojFalso reloj = new RelojFalso();
        ApiClient conFrenoReal = new ApiClient(new ThrottleCubo(reloj), red, avisos::add, () -> false);
        red.responde(429, 429, 429, 200);
        IOException e = assertThrows(IOException.class, () -> conFrenoReal.textoCon429(COMPANION));
        assertEquals("HTTP 429", e.getMessage());
        assertEquals(List.of(60L, 120L, 240L), avisos, "el tercero también pausa: nadie llama durante 240 s");
        long antes = reloj.dormido;
        assertDoesNotThrow(() -> conFrenoReal.texto(COMPANION));
        assertEquals(240_000, reloj.dormido - antes, "la llamada siguiente respeta esa pausa");
    }

    @Test void conElFrenoRealDetenerCortaLaEsperaDeLaPausa() {
        RelojFalso reloj = new RelojFalso();
        ApiClient conFrenoReal = new ApiClient(new ThrottleCubo(reloj), red, avisos::add, () -> detenida);
        red.responde(429);
        assertThrows(IOException.class, () -> conFrenoReal.texto(COMPANION));   // pausa de 60 s
        detenida = true;                                                      // el usuario pulsa Detener
        InterruptedException e = assertThrows(InterruptedException.class, () -> conFrenoReal.texto(COMPANION));
        assertEquals("detenido", e.getMessage());
        assertTrue(reloj.dormido < 1_000, "sale enseguida, no espera la pausa (durmió " + reloj.dormido + " ms)");
        assertEquals(1, red.pedidas.size(), "la segunda llamada no llega a la red");
    }

    @Test void cableadoReal_detenerSoloCortaElHiloDeLaOperacion() throws Exception {
        // ApiClient + freno real + Cancelacion.detieneEsteHilo, como en la app: la operación corre en OTRO hilo.
        RelojFalso reloj = new RelojFalso();
        ApiClient app = new ApiClient(new ThrottleCubo(reloj), red, avisos::add, Cancelacion::detieneEsteHilo);
        try {
            red.responde(429, 200);
            assertThrows(IOException.class, () -> app.texto(COMPANION));   // pausa de 60 s
            Cancelacion.hiloOperacion = new Thread(() -> { });              // la operación es otro hilo
            Cancelacion.opEnCurso = true;
            Cancelacion.stopOperacion = true;                              // el usuario pulsa Detener
            assertEquals("cuerpo 200", app.texto(COMPANION), "este hilo (un barrido de fondo) no se corta");
            assertEquals(60_000, reloj.dormido, "espera la pausa entera, como siempre");
        } finally {
            Cancelacion.stopOperacion = false; Cancelacion.opEnCurso = false; Cancelacion.hiloOperacion = null;
        }
    }

    @Test void textoNoCuentaEl429DeOtroHost() {
        red.responde(429);
        assertThrows(IOException.class, () -> api.texto(STEAM));
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

    @Test void un429DelCompanionPausaAvisaYReintenta() throws Exception {
        red.responde(429, 200);
        assertEquals("cuerpo 200", api.textoCon429(COMPANION));
        assertEquals(1, throttle.cuatrocientosVeintinueve);
        assertEquals(List.of(60L), avisos, "aviso en segundos para la barra de estado");
        assertEquals(2, red.pedidas.size());
    }

    @Test void dosReintentosComoMaximo() {
        red.responde(429, 429, 429);
        IOException e = assertThrows(IOException.class, () -> api.textoCon429(COMPANION));
        assertEquals("HTTP 429", e.getMessage());
        assertEquals(3, red.pedidas.size());
        assertEquals(3, throttle.cuatrocientosVeintinueve, "los tres cuentan al freno, como en texto()");
        assertEquals(List.of(60L, 60L, 60L), avisos);
    }

    @Test void un429DeSteamNoSeReintentaNiTocaElFreno() {
        // Fase 4 (DEUDA): antes se reintentaba dos veces al instante (sin pausa: tres peticiones en ráfaga).
        red.responde(429, 200);
        IOException e = assertThrows(IOException.class, () -> api.textoCon429(STEAM));
        assertEquals("HTTP 429", e.getMessage());
        assertEquals(1, red.pedidas.size());
        assertEquals(0, throttle.cuatrocientosVeintinueve);
        assertTrue(avisos.isEmpty());
    }

    @Test void otroErrorDeSteamTampocoSeReintenta() {
        red.responde(500);
        assertThrows(IOException.class, () -> api.textoCon429(STEAM));
        assertEquals(1, red.pedidas.size());
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

    @Test void unErrorDeRedCuyoMensajeContiene429NoEsUn429() {
        // Fase 4 (DEUDA, asimetría): en la 1.1 el 429 se detectaba por el texto del mensaje y esto se reintentaba.
        // Ahora solo cuenta el estado HTTP, como en texto().
        IOException proxy = new IOException("proxy: 429 conexiones");
        red.responde(proxy, 200);
        assertSame(proxy, assertThrows(IOException.class, () -> api.textoCon429(COMPANION)));
        assertEquals(1, red.pedidas.size());
        assertEquals(0, throttle.cuatrocientosVeintinueve);
    }

    @Test void unErrorInesperadoEnElReintentoTambienSaleComoIOException() {
        red.responde(429, new IllegalArgumentException("URI mala"));
        IOException e = assertThrows(IOException.class, () -> api.textoCon429(COMPANION));
        assertEquals("URI mala", e.getMessage());
        assertEquals(2, red.pedidas.size());
    }

    @Test void otrosErroresNoSeReintentan() {
        red.responde(500);
        assertThrows(IOException.class, () -> api.textoCon429(COMPANION));
        assertEquals(1, red.pedidas.size());
    }
}
