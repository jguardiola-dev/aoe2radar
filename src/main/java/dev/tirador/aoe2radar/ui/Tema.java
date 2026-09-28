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

    /** Granate de la franja de avisos del mando a distancia (ui.FranjaAviso): el oscuro, algo más claro, para que el
     *  borde y el icono contrasten igual sobre el fondo gris del tema oscuro que el #8B1E2D sobre el claro. */
    public static final java.awt.Color GRANATE_CLARO = new java.awt.Color(0x8B, 0x1E, 0x2D), GRANATE_OSCURO = new java.awt.Color(0xD0, 0x5A, 0x6E);

    /** El granate que toca con el tema aplicado ahora mismo (se lee al pintar: sigue al cambio de tema). */
    public static java.awt.Color granate() { return temaOscuroActivo ? GRANATE_OSCURO : GRANATE_CLARO; }
}
