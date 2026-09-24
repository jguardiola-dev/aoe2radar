package dev.tirador.aoe2radar.api;

/** Banderas del botón Detener: la operación cancelable en curso y la orden de pararla (las lee httpText y el freno). */
public final class Cancelacion {
    private Cancelacion() {}

    public static volatile boolean stopOperacion;
    public static volatile boolean opEnCurso;   // solo dentro de una operación cancelable el freno corta pausas
}
