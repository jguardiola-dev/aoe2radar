package dev.tirador.aoe2radar.ui;

import javax.swing.JFrame;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Ajustes de la ventana principal al construirla: cierre, foco perdido e iconos. La geometría en
 * sí (tamaño, posición, divisor) vive en {@link VentanaGuardada}; lo que toca a otras vistas al
 * cerrar (el socket en vivo) o al perder el foco (la hover-card de la watchlist) se lo pide a
 * {@link Anfitrion} en vez de conocerlas por nombre. Sale tal cual de lo que era
 * SpoilerFreeRecs.configurarVentana (hoy CableadoCromo.configurarVentana).
 */
public final class VentanaPrincipalAjustes {

    /** Lo que la ventana hace al cerrarse o perder el foco, además de guardar su geometría. */
    public interface Anfitrion {
        /** Al cerrar la ventana (guardarVentana() + socketVivo.cerrar() de Watchlist/Live en la 1.1). */
        void alCerrar();
        /** Al perder el foco (ocultarHoverCard(true) de Watchlist). */
        void alPerderFoco();
    }

    private VentanaPrincipalAjustes() { }

    /** Cierre, ventana recordada, foco perdido e iconos: ajustes del JFrame antes
     *  de construir ningún panel. */
    public static void configurar(JFrame ventana, Image logo, Anfitrion anfitrion) {
        ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        VentanaGuardada.aplicar(ventana);
        ventana.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { anfitrion.alCerrar(); }
        });
        ventana.addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent e) { anfitrion.alPerderFoco(); }
        });
        if (logo != null) {
            // Varias escalas: Windows elige la adecuada para ventana y barra de
            // tareas. El logo es vertical: se centra en un lienzo cuadrado
            // transparente en vez de estirarlo (getScaledInstance lo deformaba).
            List<Image> iconos = new ArrayList<>();
            for (int s : new int[]{ 16, 24, 32, 48, 64, 128, 256 })
                iconos.add(Componentes.iconoCuadrado(logo, s));
            ventana.setIconImages(iconos);
        }
    }
}
