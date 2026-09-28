package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.util.RelojFalso;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * El freno de cortesía (docs/ARQUITECTURA.md, «Reglas de desarrollo»: «1 llamada/s con ráfaga de 5, cortacircuitos ante 429»), probado sin esperar:
 * el reloj es falso y solo avanza cuando el test o el propio freno «duermen».
 */
class ThrottleCuboTest {

    @Test void rafagaDeCincoSinEsperarYLaSextaEsperaUnSegundo() throws Exception {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        for (int i = 0; i < 5; i++) t.adquirir();
        assertEquals(0, r.dormido, "las 5 primeras van seguidas");
        t.adquirir();
        assertEquals(1000, r.dormido, "la sexta espera a que se recargue una ficha");
    }

    @Test void recargaUnaFichaPorSegundo() throws Exception {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        for (int i = 0; i < 5; i++) t.adquirir();
        r.avanzar(2000);
        t.adquirir(); t.adquirir();
        assertEquals(0, r.dormido, "tras 2 s hay 2 fichas");
        t.adquirir();
        assertEquals(1000, r.dormido);
    }

    @Test void pausaRestanteDiceSiElCortacircuitosEstaAbiertoSinEsperar() {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        assertEquals(0, t.pausaRestanteMs(), "sin 429: cerrado");
        t.registrar429();
        assertEquals(60_000, t.pausaRestanteMs());
        r.avanzar(59_000);
        assertEquals(1_000, t.pausaRestanteMs());
        r.avanzar(1_000);
        assertEquals(0, t.pausaRestanteMs(), "acabó la pausa: cerrado otra vez");
        assertEquals(0, r.dormido, "preguntar no duerme");
    }

    /** Deja pasar la pausa vigente entera (y 1 ms): el siguiente 429 es un episodio nuevo. */
    static void pasaLaPausa(RelojFalso r, long pausa) { r.avanzar(pausa + 1); }

    @Test void cada429TrasAcabarLaPausaEscalaHastaElTope() {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        long p;
        assertEquals(60_000, p = t.registrar429());  pasaLaPausa(r, p);
        assertEquals(120_000, p = t.registrar429()); pasaLaPausa(r, p);
        assertEquals(240_000, p = t.registrar429()); pasaLaPausa(r, p);
        assertEquals(300_000, p = t.registrar429()); pasaLaPausa(r, p);
        assertEquals(300_000, t.registrar429(), "tope: 300 s");
    }

    @Test void un429JustoAlAcabarLaPausaEsUnEpisodioNuevo() {
        // El caso real del reintento: el freno duerme la pausa entera y el 429 llega en el mismo milisegundo en que acaba.
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        r.avanzar(t.registrar429());                     // exactamente 60 s, sin el +1
        assertEquals(120_000, t.registrar429());
    }

    @Test void un429DuranteLaPausaNoEscalaNiLaAcorta() {
        // Decisión de Jorge (2026-09-24): un escalón por episodio. Los 429 que llegan con la pausa vigente son de la
        // misma ráfaga (esas peticiones salieron antes de la pausa).
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        assertEquals(60_000, t.registrar429());
        r.avanzar(10_000);
        assertEquals(50_000, t.registrar429(), "devuelve lo que queda; no sube el escalón");
        assertEquals(50_000, t.registrar429(), "ni con varios 429 seguidos");
        pasaLaPausa(r, 50_000);
        assertEquals(120_000, t.registrar429(), "acabada la pausa, el siguiente sí es un episodio nuevo");
    }

    @Test void trasUn429NadieLlamaHastaQuePaseLaPausa() throws Exception {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        t.registrar429();
        t.adquirir();
        assertEquals(60_000, r.dormido, "espera la pausa entera; después hay fichas de sobra");
    }

    @Test void diezMinutosDeCalmaOlvidanLaEscalada() {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        pasaLaPausa(r, t.registrar429());                // 60 s
        long p = t.registrar429();                       // 120 s
        pasaLaPausa(r, p);
        r.avanzar(5 * 60_000L);
        assertEquals(240_000, t.registrar429(), "a los 5 min de acabar la pausa sigue siendo el mismo episodio: escala");
        pasaLaPausa(r, 240_000);
        r.avanzar(11 * 60_000L);
        assertEquals(60_000, t.registrar429(), "a los 11 min de calma vuelve a empezar por 60 s");
    }

    @Test void detenerCortaLaEsperaDeLaPausaEnTramos() {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        t.registrar429();                                            // 60 s
        InterruptedException e = assertThrows(InterruptedException.class, () -> t.adquirir(() -> r.dormido >= 1_000));
        assertEquals("detenido", e.getMessage());
        assertEquals(1_000, r.dormido, "cuatro tramos de 250 ms y fuera");
    }

    @Test void detenerMientrasEsperaFichaNoLaConsume() throws Exception {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        for (int i = 0; i < 5; i++) t.adquirir();                    // cubo vacío
        assertThrows(InterruptedException.class, () -> t.adquirir(() -> r.dormido >= 250));
        long antes = r.dormido;                                      // 250 ms dormidos: hay 0,25 fichas
        t.adquirir();
        assertEquals(750, r.dormido - antes, "el cancelado no consumió: falta 0,75 de ficha, no 1,75");
    }

    @Test void detenerTrasDejarPasarTiempoNoRegalaFichas() throws Exception {
        // B1 del revisor: el tiempo se cuenta al recalcular; si Detener corta la espera, no se cuenta dos veces.
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        for (int i = 0; i < 5; i++) t.adquirir();                    // T0: cubo vacío
        r.avanzar(500);                                              // T0+500: 0,5 fichas
        assertThrows(InterruptedException.class, () -> t.adquirir(() -> r.dormido >= 250));   // espera 250 y se cancela
        long antes = r.dormido;                                      // T0+750: debe haber 0,75 fichas, no 1,25
        t.adquirir();
        assertEquals(250, r.dormido - antes, "falta 0,25 de ficha: espera 250 ms (antes del arreglo pasaba sin esperar)");
    }

    @Test void registrarEpisodioDiceSiEl429AbrioLaPausaOCayoEnUnaVigente() {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        assertEquals(new Throttle.Pausa429(60_000, true), t.registrarEpisodio429(), "el primero abre la pausa");
        r.avanzar(10_000);
        assertEquals(new Throttle.Pausa429(50_000, false), t.registrarEpisodio429(), "misma ráfaga: lo que queda, no es nueva");
        assertEquals(50_000, t.registrar429(), "registrar429 sigue dando solo los ms (misma escalada)");
        pasaLaPausa(r, 50_000);
        assertEquals(new Throttle.Pausa429(120_000, true), t.registrarEpisodio429(), "acabada la pausa: episodio nuevo, escala");
    }

    @Test void sinDetenerLaEsperaEsLaMismaQueAntes() throws Exception {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        t.registrar429();
        t.adquirir(() -> false);
        assertEquals(60_000, r.dormido);
    }

    @Test void lasPausasYaVencidasNoSeEsperan() throws Exception {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        t.registrar429();
        r.avanzar(61_000);
        t.adquirir();
        assertEquals(0, r.dormido);
    }

    @Test void fichaFraccionariaSeEsperaSoloLoQueFalta() throws Exception {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        for (int i = 0; i < 5; i++) t.adquirir();
        r.avanzar(500);
        t.adquirir();
        assertEquals(500, r.dormido, "media ficha recargada: falta medio segundo");
    }

    @Test void nuncaSeDuermeMasDe300sDeUnaVezAunqueElRelojRetroceda() throws Exception {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        for (int i = 0; i < 3; i++) pasaLaPausa(r, t.registrar429());
        t.registrar429();                                // cuarto episodio: pausa de 300 s
        r.avanzar(-100_000);                            // p. ej. Windows ajusta la hora hacia atrás
        t.adquirir();
        assertEquals(300_000, r.dormido, "tope de lo que se duerme de una vez");
    }

    @Test void unUsoContinuoNoImpideOlvidar() throws Exception {
        // El fallo de la regla anterior: con éxitos cada poco no se olvidaba nunca. Ahora solo cuentan los 429.
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        for (int i = 0; i < 3; i++) pasaLaPausa(r, t.registrar429());   // 60, 120, 240: la siguiente sería 300
        for (int i = 0; i < 700; i++) t.adquirir();                      // ~11 min de llamadas buenas, una por segundo
        assertEquals(60_000, t.registrar429());
    }

    @Test void conElRelojHaciaAtrasNuncaOlvida() {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        pasaLaPausa(r, t.registrar429());                // 60 s
        r.avanzar(-30 * 60_000L);                        // Windows ajusta la hora 30 min atrás
        assertEquals(300_000, t.registrar429(), "la pausa parece vigente: devuelve lo que queda, con tope, sin olvidar ni escalar");
        r.avanzar(30 * 60_000L + 1);
        assertEquals(120_000, t.registrar429(), "de vuelta a la hora real: sigue la escalada");
    }

    @Test void fronteraDeLosDiezMinutosEsEstricta() {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        r.avanzar(t.registrar429() + 600_000);           // pausa de 60 s y 10 min exactos de calma: no olvida
        assertEquals(120_000, t.registrar429());
        r.avanzar(120_000 + 600_001);                    // 10 min y 1 ms de calma: olvida
        assertEquals(60_000, t.registrar429());
    }
}
