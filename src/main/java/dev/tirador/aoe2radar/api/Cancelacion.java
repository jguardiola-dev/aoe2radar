package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.util.Operaciones;

/** El freno de la red para el botón Detener: lo leen el ApiClient (app.Servicios lo cablea) y la pausa cooperativa
 *  dormir(). Desde la 1.3 hay un freno por operación (util.Operaciones): aquí solo se pregunta por el de este hilo. */
public final class Cancelacion {
    private Cancelacion() {}

    /** ¿Detener va por ESTE hilo? Solo el trabajo de fondo de una operación a la que han pedido parar: un barrido de
     *  fondo que espere en el freno, u otra operación, no se entera. */
    public static boolean detieneEsteHilo() {
        return Operaciones.GLOBAL.detieneEsteHilo();
    }
}
