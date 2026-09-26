package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.util.Operaciones;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Detener es de UNA operación: solo afecta al hilo que la hace, no a los barridos de fondo ni a otras
 *  operaciones (desde la 1.3, un freno por operación: util.Operaciones; aquí, el de la app). */
class CancelacionTest {

    /** Números lejos de los de la barra: Operaciones.GLOBAL es el de la app. */
    static final long OP = 910_001, OTRA = 910_002;

    @AfterEach void limpiar() {
        Operaciones.GLOBAL.soltarHilo();
        Operaciones.GLOBAL.terminar(OP);
        Operaciones.GLOBAL.terminar(OTRA);
    }

    @Test void detenerAfectaAlHiloDeLaOperacion() {
        Operaciones.GLOBAL.empezar(OP);
        Operaciones.GLOBAL.anotarHilo(OP);
        Operaciones.GLOBAL.detener(OP);
        assertTrue(Cancelacion.detieneEsteHilo());
    }

    @Test void detenerNoAfectaAOtrosHilos() throws Exception {
        Operaciones.GLOBAL.empezar(OP);
        Operaciones.GLOBAL.detener(OP);   // la operación corre en otro hilo: este no la ha anotado
        assertFalse(Cancelacion.detieneEsteHilo(), "un barrido de Live que espere en el freno no se entera");
    }

    @Test void detenerOtraOperacionNoAfectaAEsta() throws Exception {
        Operaciones.GLOBAL.empezar(OP);
        Operaciones.GLOBAL.empezar(OTRA);
        Operaciones.GLOBAL.anotarHilo(OP);
        Operaciones.GLOBAL.detener(OTRA);   // p. ej. la × de otra búsqueda
        assertFalse(Cancelacion.detieneEsteHilo(), "el freno de otra operación no para a esta");
    }

    @Test void sinDetenerOConElHiloSueltoNoHayCancelacion() throws Exception {
        Operaciones.GLOBAL.empezar(OP);
        Operaciones.GLOBAL.anotarHilo(OP);
        assertFalse(Cancelacion.detieneEsteHilo(), "sin pulsar Detener");
        Operaciones.GLOBAL.detener(OP);
        Operaciones.GLOBAL.soltarHilo();
        assertFalse(Cancelacion.detieneEsteHilo(), "el hilo ya acabó el trabajo de la operación (vuelve al pool limpio)");
        AtomicBoolean enOtro = new AtomicBoolean(true);
        Thread t = new Thread(() -> enOtro.set(Cancelacion.detieneEsteHilo()));
        t.start();
        t.join();
        assertFalse(enOtro.get(), "otro hilo tampoco");
    }
}
