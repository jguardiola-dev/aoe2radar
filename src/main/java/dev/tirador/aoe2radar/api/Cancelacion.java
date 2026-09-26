package dev.tirador.aoe2radar.api;

/** Banderas del botón Detener: la operación cancelable en curso y la orden de pararla (las lee httpText y el freno). */
public final class Cancelacion {
    private Cancelacion() {}

    public static volatile boolean stopOperacion;
    public static volatile boolean opEnCurso;   // solo dentro de una operación cancelable el freno corta pausas
    /** El hilo de la operación cancelable en curso (lo anota cada operación al empezar su trabajo de fondo). */
    public static volatile Thread hiloOperacion;

    /** ¿Detener va por ESTE hilo? Solo el de la operación: un barrido de fondo que espere en el freno no se entera. */
    public static boolean detieneEsteHilo() {
        return stopOperacion && opEnCurso && Thread.currentThread() == hiloOperacion;
    }
}
