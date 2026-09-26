package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.util.ImportacionDatos;
import dev.tirador.aoe2radar.util.Sistema;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.nio.file.Path;

import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * La cara de {@link ImportacionDatos}: la pregunta del primer arranque («¿Vienes de aoe2radar 1.x en zip?») y la
 * entrada «Importar datos de otra versión…» de Configuración. Solo con la app empaquetada (fuera del paquete la
 * carpeta de datos es la de trabajo y no hay nada que importar; así el harness no ve ningún cambio).
 * <p>Hilos: diálogos y selector en el EDT; la copia, en un hilo de fondo; el resultado vuelve por invokeLater.
 * Tras importar, la app se cierra (y se vuelve a abrir si se puede) sin pasar por el cierre normal: en memoria
 * tiene la lista y los ajustes de antes, y guardarlos al cerrar pisaría lo recién importado.
 */
public final class ImportarDatos {
    private ImportarDatos() {}

    /** Al arrancar, en el EDT y con la ventana ya visible: pregunta una sola vez si toca. No bloquea la carga, que
     *  sigue en sus hilos. La respuesta (cualquiera, también cerrar con la X) se recuerda. */
    public static void ofrecerSiToca(Component padre) {
        if (!Sistema.empaquetada()) return;
        if (!ImportacionDatos.debeOfrecer(Sistema.datosNuevos(), leerConfig(ImportacionDatos.CLAVE_OFRECIDA, "false"))) return;
        Object[] opciones = { t("Elegir carpeta…", "Choose folder…"), t("No, gracias", "No, thanks") };
        int r = JOptionPane.showOptionDialog(padre,
                t("¿Vienes de " + NOMBRE + " 1.x en zip?\nElige su carpeta para importar grupos y ajustes.\n\n"
                                + "También lo tienes en Configuración → Importar datos de otra versión…",
                        "Coming from " + NOMBRE + " 1.x (zip)?\nChoose its folder to import your groups and settings.\n\n"
                                + "You can also do it later in Settings → Import data from another version…"),
                NOMBRE, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opciones, opciones[1]);
        guardarConfig(ImportacionDatos.CLAVE_OFRECIDA, "true");
        if (r == 0) elegirEImportar(padre);
    }

    /** Selector de carpeta y, si vale, la importación en segundo plano. En el EDT. */
    public static void elegirEImportar(Component padre) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(t("Carpeta de la versión anterior de " + NOMBRE, "Folder of the previous " + NOMBRE + " version"));
        if (fc.showOpenDialog(padre) != JFileChooser.APPROVE_OPTION || fc.getSelectedFile() == null) return;
        Path origen = fc.getSelectedFile().toPath();
        Path datos = Sistema.carpetaBase(), recs = Sistema.carpetaRecs();
        new Thread(() -> {
            if (!ImportacionDatos.valida(origen, datos)) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(padre,
                            t("Esa carpeta no tiene config.properties ni players.txt de " + NOMBRE + ".\nElige la carpeta donde estaba "
                                            + NOMBRE + ".exe.",
                                    "That folder has no " + NOMBRE + " config.properties or players.txt.\nChoose the folder where "
                                            + NOMBRE + ".exe was."),
                            NOMBRE, JOptionPane.WARNING_MESSAGE);
                    elegirEImportar(padre);
                });
                return;
            }
            ImportacionDatos.Resultado r = ImportacionDatos.importar(origen, datos, recs);
            log("importar: de " + origen + (r.ok() ? " bien" : " falló: " + r.error())
                    + " (recs copiadas " + r.recsCopiadas() + ", ya estaban " + r.recsYaEstaban() + ")");
            SwingUtilities.invokeLater(() -> mostrarResultado(padre, r));
        }, "importar-datos").start();
    }

    private static void mostrarResultado(Component padre, ImportacionDatos.Resultado r) {
        if (!r.ok()) {
            JOptionPane.showMessageDialog(padre,
                    t("No se pudieron importar los datos (no se ha cambiado nada):\n", "Could not import the data (nothing was changed):\n")
                            + r.error(), NOMBRE, JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(padre,
                t("Datos importados (" + r.recsCopiadas() + " recs copiadas).\n" + NOMBRE
                                + " se cerrará y se volverá a abrir para cargarlos. Si no se abre sola, ábrela tú.",
                        "Data imported (" + r.recsCopiadas() + " recs copied).\n" + NOMBRE
                                + " will close and reopen to load it. If it does not reopen, open it yourself."),
                NOMBRE, JOptionPane.INFORMATION_MESSAGE);
        Sistema.relanzar();
        System.exit(0);   // sin windowClosing: el cierre normal guardaría el estado viejo encima de lo importado
    }
}
