package dev.tirador.aoe2radar.ui;

import javax.swing.SwingUtilities;

/**
 * Las dos reglas de hilos de la app, como puerto: el trabajo lento (red, disco) va EN SEGUNDO PLANO y lo que toca
 * Swing vuelve AL HILO DE LA UI (EDT). Los presentadores reciben un Tareas en vez de crear hilos o llamar a
 * SwingUtilities: en la app es {@link #SWING}; en los tests, {@link #EN_LINEA}, que lo ejecuta todo en el acto y en
 * orden, para probar el presentador sin hilos ni esperas.
 */
public interface Tareas {
    /** Lanza trabajo fuera del EDT, en un hilo con ese nombre (sale en los volcados de hilos y en el log). */
    void enFondo(String nombre, Runnable trabajo);

    /** Igual, en un hilo demonio: no impide cerrar la app (precargas que nadie espera). */
    void enFondoDemonio(String nombre, Runnable trabajo);

    /** Devuelve al EDT (SwingUtilities.invokeLater). */
    void enUi(Runnable trabajo);

    /** La de la app: un hilo nuevo por tarea (como hacía la 1.1 con new Thread) e invokeLater para volver. */
    Tareas SWING = new Tareas() {
        @Override public void enFondo(String nombre, Runnable trabajo) { new Thread(trabajo, nombre).start(); }
        @Override public void enFondoDemonio(String nombre, Runnable trabajo) {
            Thread h = new Thread(trabajo, nombre); h.setDaemon(true); h.start();
        }
        @Override public void enUi(Runnable trabajo) { SwingUtilities.invokeLater(trabajo); }
    };

    /** La de los tests: todo en el hilo que llama, en el acto. */
    Tareas EN_LINEA = new Tareas() {
        @Override public void enFondo(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enFondoDemonio(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enUi(Runnable trabajo) { trabajo.run(); }
    };
}
