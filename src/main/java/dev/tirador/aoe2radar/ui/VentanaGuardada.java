package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.util.Config;

import javax.swing.JFrame;
import javax.swing.JSplitPane;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                Ubicacion u = ubicar(x, y, w, h, pantallasUtiles());
                if (!u.centrar()) {
                    ventana.setBounds(u.x(), u.y(), u.ancho(), u.alto());
                } else {
                    ventana.setSize(u.ancho(), u.alto());
                    ventana.setLocationRelativeTo(null);
                }
            }
            if (Boolean.parseBoolean(leerConfig("ventana_max", "false")))
                ventana.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } catch (Exception ignored) { }
    }

    /** «Iniciar minimizada» (arreglo F8 de la revisión 1.3): minimiza SIN borrar el maximizado que puso
     *  {@link #aplicar}. Con setExtendedState(ICONIFIED) a secas, la ventana volvía de la barra de tareas en tamaño
     *  normal y al cerrar se guardaba ventana_max=false. */
    public static void minimizar(JFrame ventana) {
        ventana.setExtendedState(ventana.getExtendedState() | JFrame.ICONIFIED);
    }

    /** Dónde va la ventana guardada: posición, tamaño y si hay que centrarla en la pantalla principal. */
    record Ubicacion(int x, int y, int ancho, int alto, boolean centrar) { }

    /** Arreglo F9 de la revisión 1.3: la ventana guardada vuelve a la pantalla en la que estaba si ese monitor sigue
     *  conectado, con el tamaño recortado a esa pantalla. Si no cae en ninguna, se recorta a la principal (la
     *  primera de la lista) y se centra. Con un solo monitor es la misma cuenta de siempre (mismos 8 px de margen
     *  arriba/izquierda y 100 px visibles). Antes solo se miraba la principal y una ventana del segundo monitor
     *  volvía siempre a ella. Paquete: lo prueba VentanaGuardadaTest sin pantallas reales. */
    static Ubicacion ubicar(int x, int y, int w, int h, List<Rectangle> pantallas) {
        for (Rectangle p : pantallas)
            if (x >= p.x - 8 && y >= p.y - 8 && x + 100 < p.x + p.width && y + 100 < p.y + p.height)
                return new Ubicacion(x, y, Math.min(w, p.width), Math.min(h, p.height), false);
        Rectangle principal = pantallas.get(0);
        return new Ubicacion(x, y, Math.min(w, principal.width), Math.min(h, principal.height), true);
    }

    /** El área útil (sin barra de tareas) de cada monitor conectado; la principal primero, calculada como siempre
     *  (getMaximumWindowBounds), para que con un solo monitor nada cambie. */
    static List<Rectangle> pantallasUtiles() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        List<Rectangle> out = new ArrayList<>();
        out.add(ge.getMaximumWindowBounds());
        GraphicsDevice principal = ge.getDefaultScreenDevice();
        for (GraphicsDevice d : ge.getScreenDevices()) {
            if (d.equals(principal)) continue;
            GraphicsConfiguration gc = d.getDefaultConfiguration();
            Rectangle b = gc.getBounds();
            Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            out.add(new Rectangle(b.x + in.left, b.y + in.top, b.width - in.left - in.right, b.height - in.top - in.bottom));
        }
        return out;
    }

    /** Guarda el divisor (si existe), el estado maximizado y, si no está maximizada, los bounds. */
    public static void guardar(JFrame ventana, JSplitPane splitPrincipal) {
        geometria(ventana, splitPrincipal).forEach(Config::guardarConfig);
    }

    /** Las claves que escribe {@link #guardar}, en el mismo orden: divisor (si hay split), ventana_max y, si no está
     *  maximizada ni minimizada, ventana (minimizada, Windows puede dar una posición fuera de pantalla: se conserva la
     *  última buena, igual en el cierre que en el guardado al cambiar). Se leen del JFrame: hay que llamarla en el EDT. */
    static Map<String, String> geometria(JFrame ventana, JSplitPane splitPrincipal) {
        Map<String, String> out = new LinkedHashMap<>();
        if (splitPrincipal != null)
            out.put("divisor", String.valueOf(splitPrincipal.getDividerLocation()));
        boolean max = (ventana.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        out.put("ventana_max", String.valueOf(max));
        if (!max && (ventana.getExtendedState() & JFrame.ICONIFIED) == 0) {
            Rectangle b = ventana.getBounds();
            out.put("ventana", b.x + "," + b.y + "," + b.width + "," + b.height);
        }
        return out;
    }

    /** Arreglo F11 de la revisión 1.3: al apagar Windows o cerrar sesión con la app abierta, Swing no recibe
     *  windowClosing y la geometría no se guardaba. En vez de fiarlo al cierre, se guarda 1,5 s después del último
     *  movimiento, cambio de tamaño, maximizado o arrastre del divisor. El cierre normal sigue guardando como
     *  siempre. Devuelve el Timer (paquete: VentanaGuardadaTest lo dispara sin esperar). */
    public static javax.swing.Timer guardarAlCambiar(JFrame ventana, JSplitPane splitPrincipal) {
        javax.swing.Timer t = new javax.swing.Timer(1500, e -> guardarEnSegundoPlano(ventana, splitPrincipal));
        t.setRepeats(false);
        ventana.addComponentListener(new ComponentAdapter() {
            @Override public void componentMoved(ComponentEvent e) { t.restart(); }
            @Override public void componentResized(ComponentEvent e) { t.restart(); }
        });
        ventana.addWindowStateListener(e -> t.restart());
        if (splitPrincipal != null)
            splitPrincipal.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> t.restart());
        return t;
    }

    /** Lee la geometría en el EDT y la escribe en un hilo aparte (disco fuera del EDT). Minimizada no guarda nada:
     *  Windows puede llevarla a -32000,-32000 y se perdería la posición buena. */
    static void guardarEnSegundoPlano(JFrame ventana, JSplitPane splitPrincipal) {
        if ((ventana.getExtendedState() & JFrame.ICONIFIED) != 0) return;
        Map<String, String> valores = geometria(ventana, splitPrincipal);
        Thread h = new Thread(() -> valores.forEach(Config::guardarConfig), "ventana-guarda");
        h.setDaemon(true);
        h.start();
    }
}
