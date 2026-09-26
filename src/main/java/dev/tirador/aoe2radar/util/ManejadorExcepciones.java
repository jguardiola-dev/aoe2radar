package dev.tirador.aoe2radar.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

/**
 * El manejador global de excepciones no capturadas (punto 10 de la 1.3): lo que antes solo salía por la consola
 * (que en el exe nadie ve) queda escrito en descargas.log con el hilo, la versión de la app y la pila entera. Sin
 * diálogos: no es intrusivo. El fallo de arranque sigue yendo por su camino propio (arranque_error.log y el diálogo
 * de app.Main), que lo captura antes de que llegue aquí.
 * <p>
 * Vale también para el EDT: en Java 21, una excepción que se escapa de un evento de Swing va al manejador del propio
 * hilo del EDT, que sin uno propio delega en su grupo y este en el global (el EDT sigue vivo después). Lo comprueba
 * ManejadorExcepcionesTest.
 * <p>
 * Dos defensas: una excepción dentro del manejador no se relanza (ni vuelve a entrar en él), y como mucho
 * {@code maxPorMinuto} pilas por minuto; las que pasen del tope se cuentan y se resumen en una línea al abrirse el
 * minuto siguiente, para que un fallo que se repite en cada repintado no llene el log.
 */
public final class ManejadorExcepciones implements Thread.UncaughtExceptionHandler {

    /** Pilas completas por minuto como mucho, en producción. */
    public static final int MAX_POR_MINUTO = 5;
    static final long MINUTO_MS = 60_000;
    /** Líneas de pila como mucho por excepción (un StackOverflowError trae ~1.000). */
    static final int MAX_LINEAS_PILA = 120;

    private final Consumer<String> escribir;
    private final Reloj reloj;
    private final int maxPorMinuto;

    private long inicioMinuto = Long.MIN_VALUE;
    private int enEsteMinuto, omitidas;
    /** Hilo que está dentro del manejador: si algo en él vuelve a lanzar hacia aquí, no se entra otra vez. */
    private final ThreadLocal<Boolean> dentro = ThreadLocal.withInitial(() -> false);

    public ManejadorExcepciones(Consumer<String> escribir, Reloj reloj, int maxPorMinuto) {
        this.escribir = escribir;
        this.reloj = reloj;
        this.maxPorMinuto = maxPorMinuto;
    }

    /** Lo instala como manejador global (descargas.log, reloj del sistema): una vez, al principio de app.Main. */
    public static void instalar() {
        Thread.setDefaultUncaughtExceptionHandler(new ManejadorExcepciones(Log::log, Reloj.SISTEMA, MAX_POR_MINUTO));
    }

    @Override public void uncaughtException(Thread hilo, Throwable ex) {
        if (dentro.get()) return;
        dentro.set(true);
        try {
            String resumen = null, pila = null;
            synchronized (this) {
                long ahora = reloj.ahoraMs();
                if (inicioMinuto == Long.MIN_VALUE || ahora - inicioMinuto >= MINUTO_MS || ahora < inicioMinuto) {
                    // El resumen de las omitidas se escribe aquí, al abrirse un minuto nuevo, así que solo sale si
                    // llega otra excepción después: si no llega ninguna más, esas omitidas no se anotan nunca.
                    if (omitidas > 0)
                        resumen = "excepción no capturada: " + omitidas + " más en el minuto anterior, sin escribir (tope de " + maxPorMinuto + ")";
                    inicioMinuto = ahora;
                    enEsteMinuto = 0;
                    omitidas = 0;
                }
                if (enEsteMinuto < maxPorMinuto) {
                    enEsteMinuto++;
                    pila = texto(hilo, ex);
                } else {
                    omitidas++;
                }
            }
            if (resumen != null) escribir.accept(resumen);
            if (pila != null) escribir.accept(pila);
        } catch (Throwable ignorada) {
            // nunca se relanza desde aquí: sería otra excepción no capturada, y de nuevo aquí
        } finally {
            dentro.set(false);
        }
    }

    /** La entrada del log: versión, hilo y pila (con sus causas), recortada a MAX_LINEAS_PILA líneas. */
    static String texto(Thread hilo, Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String[] lineas = sw.toString().split("\\R");
        StringBuilder sb = new StringBuilder("excepción no capturada en el hilo «")
                .append(hilo == null ? "?" : hilo.getName()).append("» (")
                .append(Identidad.NOMBRE).append(' ').append(Identidad.VERSION).append(")");
        int n = Math.min(lineas.length, MAX_LINEAS_PILA);
        for (int i = 0; i < n; i++) sb.append(System.lineSeparator()).append(lineas[i]);
        if (lineas.length > n) sb.append(System.lineSeparator()).append("\t... ").append(lineas.length - n).append(" líneas más");
        return sb.toString();
    }
}
