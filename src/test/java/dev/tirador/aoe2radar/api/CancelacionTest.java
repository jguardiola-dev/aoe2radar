package dev.tirador.aoe2radar.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Detener es de UNA operación: solo afecta al hilo que la hace, no a los barridos de fondo. */
class CancelacionTest {

    @AfterEach void limpiar() { Cancelacion.stopOperacion = false; Cancelacion.opEnCurso = false; Cancelacion.hiloOperacion = null; }

    @Test void detenerAfectaAlHiloDeLaOperacion() {
        Cancelacion.hiloOperacion = Thread.currentThread();
        Cancelacion.opEnCurso = true;
        Cancelacion.stopOperacion = true;
        assertTrue(Cancelacion.detieneEsteHilo());
    }

    @Test void detenerNoAfectaAOtrosHilos() throws Exception {
        Cancelacion.hiloOperacion = new Thread(() -> { });   // la operación corre en otro hilo
        Cancelacion.opEnCurso = true;
        Cancelacion.stopOperacion = true;
        assertFalse(Cancelacion.detieneEsteHilo(), "un barrido de Live que espere en el freno no se entera");
    }

    @Test void sinDetenerONiOperacionNoHayCancelacion() {
        Cancelacion.hiloOperacion = Thread.currentThread();
        Cancelacion.opEnCurso = true;
        assertFalse(Cancelacion.detieneEsteHilo(), "sin pulsar Detener");
        Cancelacion.stopOperacion = true;
        Cancelacion.opEnCurso = false;
        assertFalse(Cancelacion.detieneEsteHilo(), "la operación ya terminó");
    }
}
