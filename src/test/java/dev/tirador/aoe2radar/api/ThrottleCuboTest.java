package dev.tirador.aoe2radar.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El freno de cortesía (CLAUDE.md: «1 llamada/s con ráfaga de 5, cortacircuitos ante 429»), probado sin esperar:
 * el reloj es falso y solo avanza cuando el test o el propio freno «duermen».
 */
class ThrottleCuboTest {

    /** Reloj de prueba: dormir no espera, solo apunta cuánto se durmió y adelanta la hora. */
    static final class RelojFalso implements ThrottleCubo.Reloj {
        long ahora = 1_000_000_000L;   // no empezar en 0: el freno real nunca ve la hora 0
        long dormido;
        @Override public long ahoraMs() { return ahora; }
        @Override public void dormir(long ms) { dormido += ms; ahora += ms; }
        void avanzar(long ms) { ahora += ms; }
    }

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

    @Test void el429EscalaLaPausaHastaElTope() {
        ThrottleCubo t = new ThrottleCubo(new RelojFalso());
        assertEquals(60_000, t.registrar429());
        assertEquals(120_000, t.registrar429());
        assertEquals(240_000, t.registrar429());
        assertEquals(300_000, t.registrar429());
        assertEquals(300_000, t.registrar429(), "tope: 300 s");
    }

    @Test void trasUn429NadieLlamaHastaQuePaseLaPausa() throws Exception {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        t.registrar429();
        t.adquirir();
        assertEquals(60_000, r.dormido, "espera la pausa entera; después hay fichas de sobra");
    }

    @Test void diezMinutosSinProblemasOlvidanLaEscalada() {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        t.registrarExito();
        t.registrar429(); t.registrar429();
        r.avanzar(5 * 60_000L);
        t.registrarExito();
        assertEquals(240_000, t.registrar429(), "a los 5 min del último éxito sigue la escalada");
        r.avanzar(11 * 60_000L);
        t.registrarExito();
        assertEquals(60_000, t.registrar429(), "a los 11 min del último éxito vuelve a empezar");
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
        for (int i = 0; i < 4; i++) t.registrar429();   // pausa de 300 s
        r.avanzar(-100_000);                            // p. ej. Windows ajusta la hora hacia atrás
        t.adquirir();
        assertEquals(300_000, r.dormido, "tope de lo que se duerme de una vez");
    }

    @Test void caracterizacion_elPrimerExitoSinExitoPrevioOlvidaLaEscalada() {
        // Rareza de la 1.1 que se conserva: ultimoExito empieza en 0, así que el primer éxito de la sesión borra la escalada.
        ThrottleCubo t = new ThrottleCubo(new RelojFalso());
        t.registrar429(); t.registrar429();
        t.registrarExito();
        assertEquals(60_000, t.registrar429());
    }

    @Test void fronteraDeLosDiezMinutosEsEstricta() {
        RelojFalso r = new RelojFalso();
        ThrottleCubo t = new ThrottleCubo(r);
        t.registrarExito();
        t.registrar429();
        r.avanzar(600_000);
        t.registrarExito();                              // 10 min exactos: no olvida
        assertEquals(120_000, t.registrar429());
        r.avanzar(600_001);
        t.registrarExito();                              // 10 min y 1 ms: olvida
        assertEquals(60_000, t.registrar429());
    }
}
