package dev.tirador.aoe2radar.ui;

/**
 * El tema aplicado (claro u oscuro). Lo escribe la ventana al aplicar el tema (en el EDT) y lo leen las vistas al pintar
 * (también en el EDT): los paneles pintados a mano eligen sus colores con él en cada paintComponent, así que basta un
 * repintado tras cambiar de tema. Vive en ui para que las vistas lo lean sin conocer la ventana.
 */
public final class Tema {
    private Tema() { }

    /** Último tema aplicado: true si es oscuro. */
    public static boolean temaOscuroActivo;
}
