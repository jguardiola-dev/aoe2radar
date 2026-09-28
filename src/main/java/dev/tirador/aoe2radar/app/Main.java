package dev.tirador.aoe2radar.app;

import dev.tirador.aoe2radar.SpoilerFreeRecs;
import dev.tirador.aoe2radar.ui.AcercaDe;
import dev.tirador.aoe2radar.ui.TemaApp;
import dev.tirador.aoe2radar.util.Instalacion;
import dev.tirador.aoe2radar.util.ManejadorExcepciones;
import dev.tirador.aoe2radar.util.Sistema;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Files;
import java.util.Locale;

import static dev.tirador.aoe2radar.cache.Catalogos.cargarCatalogos;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;
import static dev.tirador.aoe2radar.util.Identidad.VERSION;
import static dev.tirador.aoe2radar.ui.TemaApp.TEMA_SISTEMA;
import static dev.tirador.aoe2radar.util.Sistema.enCarpetaBase;

/**
 * El arranque de la app (fase 3, tanda 4, Z6): {@code --make-ico}, catálogos, idioma, tema y la creación de la
 * ventana en el EDT. Es el mismo main() que vivía en SpoilerFreeRecs.java, movido tal cual; SpoilerFreeRecs
 * conserva {@code public static void main} como delegado de una línea porque el pom y el harness lo llaman por
 * su nombre de clase.
 */
public class Main {

    private Main() { }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--make-ico")) {
            System.exit(AcercaDe.generarIco() ? 0 : 1);
        }
        // Punto 10 (1.3): lo que se escape de cualquier hilo (EDT incluido) queda en descargas.log, sin diálogos.
        // El fallo de arranque de abajo lo sigue capturando su propio try/catch (arranque_error.log y diálogo).
        ManejadorExcepciones.instalar();
        // 1.4, actualizador propio: ¿es el primer arranque tras aplicar una versión nueva? Lo primero, antes que nada
        // que pueda fallar (util.Instalacion). Si el jar nuevo ya murió dos veces sin abrir la ventana, se ha vuelto al
        // .cfg anterior: se abre la versión de antes y esta sale. Fuera del paquete (mvn, harness), rutas null: NORMAL.
        final Instalacion.Rutas rutas = Instalacion.actuales();
        final Instalacion.Arranque arranque = Instalacion.alArrancar(rutas);
        if (arranque == Instalacion.Arranque.REVERTIDO) {
            if (!Sistema.relanzar()) dev.tirador.aoe2radar.util.Log.log("actualizar: vuelta atrás hecha, pero no se pudo relanzar: hay que abrir la app a mano");
            System.exit(3);
        }
        try {
            cargarCatalogos();
            IDIOMA = leerConfig("idioma",
                    Locale.getDefault().getLanguage().equalsIgnoreCase("es") ? "es" : "en");
        } catch (Throwable ex) {   // 1.4: también esto cuenta como fallo de arranque (antes caía sin aviso ni log)
            falloDeArranque(ex, rutas, arranque);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                String tema = TemaApp.temaValido(leerConfig("tema", TEMA_SISTEMA));
                TemaApp.flatLafDisponible = TemaApp.aplicarTema(tema, null);
                if (!TemaApp.flatLafDisponible) {
                    try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                    catch (Exception ignored) {}
                }
                new SpoilerFreeRecs(tema).setVisible(true);
            } catch (Throwable ex) {   // un fallo de arranque nunca más muere en silencio: queda escrito y avisa
                falloDeArranque(ex, rutas, arranque);
                return;
            }
            // La ventana está abierta: la actualización (si la hubo) ha ido bien. Se confirma y se barren los jars viejos
            // en segundo plano (disco, nunca en el EDT).
            Thread confirmar = new Thread(() -> Instalacion.confirmar(rutas, VERSION), "actualizar-confirmar");
            confirmar.setDaemon(true);
            confirmar.start();
        });
    }

    /** arranque_error.log, vuelta a la versión anterior si era el primer arranque tras actualizar, el aviso (en el EDT)
     *  y salida. Si se volvió atrás, la app se abre de nuevo (con la versión de antes) al cerrar el aviso. */
    private static void falloDeArranque(Throwable ex, Instalacion.Rutas rutas, Instalacion.Arranque arranque) {
        StringBuilder sb = new StringBuilder("Fallo al arrancar " + NOMBRE + " " + VERSION + "\n" + ex + "\n");
        for (StackTraceElement st : ex.getStackTrace()) sb.append("    at ").append(st).append("\n");
        try { Files.writeString(enCarpetaBase("arranque_error.log"), sb.toString()); } catch (Exception ignored) { }
        boolean revertido = arranque == Instalacion.Arranque.TRAS_ACTUALIZAR
                && Instalacion.revertir(rutas, "fallo al arrancar: " + ex);
        String vuelta = revertido ? t("\n\nSe ha vuelto a la versión anterior: " + NOMBRE + " se abrirá de nuevo.",
                "\n\nReverted to the previous version: " + NOMBRE + " will open again.") : "";
        Runnable aviso = () -> JOptionPane.showMessageDialog(null,
                NOMBRE + t(" no ha podido arrancar.\nDetalle guardado en " + enCarpetaBase("arranque_error.log").toAbsolutePath() + "\n\n",
                        " could not start.\nDetails saved to " + enCarpetaBase("arranque_error.log").toAbsolutePath() + "\n\n") + ex + vuelta,
                NOMBRE, JOptionPane.ERROR_MESSAGE);
        try {
            if (SwingUtilities.isEventDispatchThread()) aviso.run();
            else SwingUtilities.invokeAndWait(aviso);
        } catch (Throwable ignored) { }
        if (revertido) Sistema.relanzar();
        System.exit(2);
    }
}
