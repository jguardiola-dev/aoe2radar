package dev.tirador.aoe2radar.ui;

import javax.swing.JFrame;
import javax.swing.JSplitPane;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;

/**
 * Geometría de la ventana principal: tamaño, posición, maximizado y el ancho del divisor
 * (Watchlist | resto), con las mismas claves de config que la 1.1 ("ventana", "ventana_max",
 * "divisor"). Sale tal cual de SpoilerFreeRecs.aplicarVentanaGuardada/guardarVentana: aquí no
 * hay vistas, solo el JFrame y, para guardar, el JSplitPane principal.
 */
public final class VentanaGuardada {

    private VentanaGuardada() { }

    /** Restaura tamaño, posición y maximizado de la última sesión, con
     *  cordura: si la posición guardada cae fuera de la pantalla, se centra. */
    public static void aplicar(JFrame ventana) {
        ventana.setSize(1180, 680);
        ventana.setLocationRelativeTo(null);
        try {
            String v = leerConfig("ventana", null);
            if (v != null) {
                String[] p = v.split(",");
                int x = Integer.parseInt(p[0].trim()), y = Integer.parseInt(p[1].trim());
                int w = Math.max(700, Integer.parseInt(p[2].trim()));
                int h = Math.max(450, Integer.parseInt(p[3].trim()));
                Rectangle pant = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                w = Math.min(w, pant.width);
                h = Math.min(h, pant.height);
                if (x >= pant.x - 8 && y >= pant.y - 8
                        && x + 100 < pant.x + pant.width && y + 100 < pant.y + pant.height) {
                    ventana.setBounds(x, y, w, h);
                } else {
                    ventana.setSize(w, h);
                    ventana.setLocationRelativeTo(null);
                }
            }
            if (Boolean.parseBoolean(leerConfig("ventana_max", "false")))
                ventana.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } catch (Exception ignored) { }
    }

    /** Guarda el divisor (si existe), el estado maximizado y, si no está maximizada, los bounds. */
    public static void guardar(JFrame ventana, JSplitPane splitPrincipal) {
        if (splitPrincipal != null)
            guardarConfig("divisor", String.valueOf(splitPrincipal.getDividerLocation()));
        boolean max = (ventana.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        guardarConfig("ventana_max", String.valueOf(max));
        if (!max) {
            Rectangle b = ventana.getBounds();
            guardarConfig("ventana", b.x + "," + b.y + "," + b.width + "," + b.height);
        }
    }
}
