package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El manejador global de excepciones no capturadas (punto 10 de la 1.3): qué escribe, el tope por minuto, que no
 *  relanza ni vuelve a entrar, y que en Java 21 el global basta para el EDT. */
class ManejadorExcepcionesTest {

    private final List<String> log = new ArrayList<>();
    private final RelojFalso reloj = new RelojFalso();

    @Test void escribeHiloVersionYPilaConSuCausa() {
        ManejadorExcepciones m = new ManejadorExcepciones(log::add, reloj, 5);
        Thread hilo = new Thread(() -> { }, "sondeo-live");
        m.uncaughtException(hilo, new IllegalStateException("fallo raro", new java.io.IOException("la causa")));
        assertEquals(1, log.size());
        String e = log.get(0);
        assertTrue(e.contains("«sondeo-live»"), e);
        assertTrue(e.contains(Identidad.NOMBRE + " " + Identidad.VERSION), e);
        assertTrue(e.contains("java.lang.IllegalStateException: fallo raro"), e);
        assertTrue(e.contains("\tat dev.tirador.aoe2radar.util.ManejadorExcepcionesTest."), "con la pila: " + e);
        assertTrue(e.contains("Caused by: java.io.IOException: la causa"), e);
    }

    @Test void comoMuchoNPilasPorMinutoYLuegoUnResumen() {
        ManejadorExcepciones m = new ManejadorExcepciones(log::add, reloj, 3);
        for (int i = 0; i < 10; i++) { m.uncaughtException(Thread.currentThread(), new RuntimeException("n" + i)); reloj.avanzar(1_000); }
        assertEquals(3, log.size(), "tope de 3 en el minuto");
        reloj.avanzar(60_000);
        m.uncaughtException(Thread.currentThread(), new RuntimeException("otra"));
        assertEquals(5, log.size(), "minuto nuevo: el resumen de las omitidas y la pila nueva");
        assertTrue(log.get(3).contains("7 más en el minuto anterior"), log.get(3));
        assertTrue(log.get(4).contains("RuntimeException: otra"), log.get(4));
    }

    @Test void unaPilaEnormeSeRecorta() {
        ManejadorExcepciones m = new ManejadorExcepciones(log::add, reloj, 5);
        RuntimeException ex = new RuntimeException("profunda");
        StackTraceElement[] pila = new StackTraceElement[1000];
        java.util.Arrays.fill(pila, new StackTraceElement("C", "m", "C.java", 1));
        ex.setStackTrace(pila);
        m.uncaughtException(Thread.currentThread(), ex);
        String e = log.get(0);
        assertTrue(e.split("\\R").length <= ManejadorExcepciones.MAX_LINEAS_PILA + 2, "recortada");
        assertTrue(e.contains("líneas más"), e);
    }

    @Test void unFalloAlEscribirNoSeRelanzaNiVuelveAEntrar() {
        int[] llamadas = new int[1];
        ManejadorExcepciones[] m = new ManejadorExcepciones[1];
        m[0] = new ManejadorExcepciones(txt -> {
            llamadas[0]++;
            m[0].uncaughtException(Thread.currentThread(), new RuntimeException("dentro"));   // no vuelve a entrar
            throw new IllegalStateException("disco lleno");
        }, reloj, 5);
        assertDoesNotThrow(() -> m[0].uncaughtException(Thread.currentThread(), new RuntimeException("fuera")));
        assertEquals(1, llamadas[0], "ni bucle ni reentrada");
        assertDoesNotThrow(() -> m[0].uncaughtException(Thread.currentThread(), new RuntimeException("después")));
        assertEquals(2, llamadas[0], "tras el fallo sigue funcionando");
    }

    @Test void enJava21ElManejadorGlobalRecibeLasDelEdt() throws Exception {
        Thread.UncaughtExceptionHandler antes = Thread.getDefaultUncaughtExceptionHandler();
        AtomicReference<Throwable> recibida = new AtomicReference<>();
        AtomicReference<Thread> hilo = new AtomicReference<>();
        CountDownLatch llego = new CountDownLatch(1);
        RuntimeException lanzada = new RuntimeException("desde un evento de Swing");
        try {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> { hilo.set(t); recibida.set(e); llego.countDown(); });
            SwingUtilities.invokeLater(() -> { throw lanzada; });
            assertTrue(llego.await(10, TimeUnit.SECONDS), "la excepción del EDT llega al manejador global");
            assertSame(lanzada, recibida.get());
            assertTrue(hilo.get().getName().startsWith("AWT-EventQueue"), hilo.get().getName());
            boolean[] sigueVivo = new boolean[1];
            SwingUtilities.invokeAndWait(() -> sigueVivo[0] = SwingUtilities.isEventDispatchThread());
            assertTrue(sigueVivo[0], "y el EDT sigue atendiendo eventos");
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(antes);
        }
    }
}
