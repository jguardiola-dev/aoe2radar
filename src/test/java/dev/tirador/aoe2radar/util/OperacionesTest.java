package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un freno por operación (decisión de Jorge, 1.3): «Detener» para solo la operación viva más reciente; cuando
 * esa termina, pasa a la anterior viva; el freno de la red solo corta el hilo de la operación detenida. Sin Swing
 * ni red; la concurrencia, con latches (sin esperas a ciegas).
 */
class OperacionesTest {

    private final Operaciones ops = new Operaciones();

    @Test void sinOperaciones_detenerNoHaceNadaYNoSeVe() {
        assertEquals(new Operaciones.EstadoDetener(false, false, -1), ops.estadoDetener());
        assertEquals(-1, ops.detenerUltima());
        assertFalse(ops.hayVivas());
    }

    @Test void detener_paraSoloLaMasReciente_yAlTerminarPasaALaAnterior() {
        ops.empezar(1);   // una búsqueda
        ops.empezar(2);   // una descarga empezada durante la búsqueda
        assertEquals(new Operaciones.EstadoDetener(true, true, 2), ops.estadoDetener());
        assertEquals(2, ops.detenerUltima());
        assertTrue(ops.detenido(2));
        assertFalse(ops.detenido(1), "la búsqueda no se entera");
        assertEquals(new Operaciones.EstadoDetener(true, false, 2), ops.estadoDetener(), "espera a que la detenida termine");
        assertTrue(ops.terminar(2));
        assertEquals(new Operaciones.EstadoDetener(true, true, 1), ops.estadoDetener(), "Detener pasa a la anterior viva");
        assertEquals(1, ops.detenerUltima());
        assertTrue(ops.terminar(1));
        assertEquals(new Operaciones.EstadoDetener(false, false, -1), ops.estadoDetener(), "sin vivas, se oculta");
    }

    @Test void terminarLaAnterior_dejaDetenerEnLaReciente() {
        ops.empezar(1);
        ops.empezar(2);
        assertTrue(ops.terminar(1));
        assertEquals(new Operaciones.EstadoDetener(true, true, 2), ops.estadoDetener());
        assertFalse(ops.terminar(1), "terminar dos veces no hace nada");
    }

    @Test void unaOperacionNuevaNoSueltaElFrenoDeLaAnterior() {
        // Antes (un solo freno), empezar otra operación ponía stopOperacion = false: la anterior, detenida, dejaba
        // de verlo si aún no lo había mirado.
        ops.empezar(1);
        ops.detenerUltima();
        ops.empezar(2);
        assertTrue(ops.detenido(1));
        assertFalse(ops.detenido(2));
    }

    @Test void detenerPorNumero_paraEsaAunqueNoSeaLaUltima() {
        ops.empezar(1);
        ops.empezar(2);
        assertTrue(ops.detener(1));   // la × de «Partidas de:» sobre su búsqueda
        assertTrue(ops.detenido(1));
        assertFalse(ops.detenido(2));
        assertFalse(ops.detener(99), "una operación desconocida no se para");
    }

    @Test void detenido_sigueRespondiendoTrasTerminar() {
        ops.empezar(1);
        ops.detenerUltima();
        ops.terminar(1);
        assertTrue(ops.detenido(1), "un done() que pregunta después de terminar sigue sabiendo que se detuvo");
    }

    @Test void unaOperacionLarga_noSeOlvidaAunqueEmpiecenYTerminenMuchasOtras() {
        ops.empezar(1);   // una descarga grande
        for (long i = 2; i <= 200; i++) { ops.empezar(i); ops.terminar(i); }
        assertTrue(ops.detener(1), "sigue siendo conocida");
        assertTrue(ops.detenido(1), "y su bucle ve el freno");
    }

    @Test void frenoDeLaRed_soloCortaElHiloDeLaOperacionDetenida() throws Exception {
        ops.empezar(1);
        ops.empezar(2);
        CountDownLatch anotados = new CountDownLatch(2), mirar = new CountDownLatch(1);
        AtomicBoolean corta1 = new AtomicBoolean(), corta2 = new AtomicBoolean();
        Thread h1 = new Thread(() -> trabajar(1, anotados, mirar, corta1));
        Thread h2 = new Thread(() -> trabajar(2, anotados, mirar, corta2));
        h1.start(); h2.start();
        assertTrue(anotados.await(5, TimeUnit.SECONDS));
        assertEquals(2, ops.detenerUltima());   // Detener de la barra
        mirar.countDown();
        h1.join(5000); h2.join(5000);
        assertFalse(corta1.get(), "el hilo de la operación 1 sigue esperando en el freno como siempre");
        assertTrue(corta2.get(), "el de la operación 2 sale enseguida");
        assertFalse(ops.detieneEsteHilo(), "ni este hilo (un barrido de fondo) se entera");
    }

    private void trabajar(long op, CountDownLatch anotados, CountDownLatch mirar, AtomicBoolean corta) {
        ops.anotarHilo(op);
        try {
            anotados.countDown();
            mirar.await(5, TimeUnit.SECONDS);
            corta.set(ops.detieneEsteHilo());
        } catch (InterruptedException ignored) {
        } finally {
            ops.soltarHilo();
        }
    }

    @Test void unHiloDelPoolReutilizado_noHeredaElFrenoDeLaOperacionAnterior() throws Exception {
        // Los SwingWorker corren en un pool: el hilo que hizo una operación detenida puede hacer después un barrido
        // de Live now. Al acabar, la operación suelta su hilo (soltarHilo en un finally) y el barrido no se entera.
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            ops.empezar(1);
            ops.detenerUltima();
            Future<Boolean> operacion = pool.submit(() -> {
                ops.anotarHilo(1);
                try { return ops.detieneEsteHilo(); } finally { ops.soltarHilo(); }
            });
            assertTrue(operacion.get(5, TimeUnit.SECONDS), "su propio trabajo sí lo ve");
            Future<Boolean> barrido = pool.submit(ops::detieneEsteHilo);   // mismo hilo, otro trabajo
            assertFalse(barrido.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void concurrencia_empezarYTerminarDesdeVariosHilosNoPierdeNinguna() throws Exception {
        int n = 200;
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int i = 1; i <= n; i++) {
                final long id = i;
                pool.submit(() -> { salida.await(); ops.empezar(id); return null; });
            }
            salida.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        int vivas = 0;
        while (ops.detenerUltima() >= 0) {
            long ultima = ops.estadoDetener().objetivo();
            assertTrue(ops.terminar(ultima));
            vivas++;
        }
        assertEquals(n, vivas, "las 200 quedaron registradas y se terminan de una en una");
    }
}
