package dev.tirador.aoe2radar.app;

import dev.tirador.aoe2radar.SpoilerFreeRecs;
import dev.tirador.aoe2radar.ui.AcercaDe;
import dev.tirador.aoe2radar.ui.TemaApp;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static dev.tirador.aoe2radar.cache.Catalogos.cargarCatalogos;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;
import static dev.tirador.aoe2radar.util.Identidad.VERSION;
import static dev.tirador.aoe2radar.ui.TemaApp.TEMA_SISTEMA;

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
        cargarCatalogos();
        IDIOMA = leerConfig("idioma",
                Locale.getDefault().getLanguage().equalsIgnoreCase("es") ? "es" : "en");
        SwingUtilities.invokeLater(() -> {
            String tema = TemaApp.temaValido(leerConfig("tema", TEMA_SISTEMA));
            TemaApp.flatLafDisponible = TemaApp.aplicarTema(tema, null);
            if (!TemaApp.flatLafDisponible) {
                try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                catch (Exception ignored) {}
            }
            try {
                new SpoilerFreeRecs(tema).setVisible(true);
            } catch (Throwable ex) {   // un fallo de arranque nunca más muere en silencio: queda escrito y avisa
                StringBuilder sb = new StringBuilder("Fallo al arrancar " + NOMBRE + " " + VERSION + "\n" + ex + "\n");
                for (StackTraceElement st : ex.getStackTrace()) sb.append("    at ").append(st).append("\n");
                try { Files.writeString(Path.of("arranque_error.log"), sb.toString()); } catch (Exception ignored) { }
                JOptionPane.showMessageDialog(null,
                        NOMBRE + " no ha podido arrancar.\nDetalle guardado en arranque_error.log (junto al exe).\n\n" + ex,
                        NOMBRE, JOptionPane.ERROR_MESSAGE);
                System.exit(2);
            }
        });
    }
}
