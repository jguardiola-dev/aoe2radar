package dev.tirador.aoe2radar.util;

import static dev.tirador.aoe2radar.util.Log.log;

/**
 * La regla de hilos, explícita: lo que va a la red nunca en el hilo de la UI (EDT). Los servicios la comprueban al
 * entrar y, si se incumple, lo AVISAN en el log sin lanzar nada (decisión de Jorge, 2026-09-25): hoy no pasa, y si
 * alguien lo rompe la app sigue funcionando (más lenta) y el log dice dónde.
 */
public final class Hilos {
    private Hilos() {}

    /** Anota en el log si quien llama está en el EDT. quien: el método que va a bloquear (p. ej. «ProfileService.ficha»). */
    public static void avisarSiUi(String quien) {
        if (!java.awt.EventQueue.isDispatchThread()) return;
        String desde = StackWalker.getInstance().walk(s -> s.skip(2).findFirst().map(f -> f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber()).orElse("?"));
        log("AVISO hilos: " + quien + " se llamó en el hilo de la UI (desde " + desde + ") y puede ir a la red");
    }
}
