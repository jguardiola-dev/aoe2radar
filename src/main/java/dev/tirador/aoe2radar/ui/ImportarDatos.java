package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.util.ImportacionDatos;
import dev.tirador.aoe2radar.util.Sistema;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * La cara de {@link ImportacionDatos}: la pregunta del primer arranque («¿Vienes de aoe2radar 1.x en zip?»), la
 * entrada «Importar datos de otra versión…» de Configuración y el aviso con el resultado al arrancar la app
 * relanzada. Solo con la app empaquetada (fuera del paquete la carpeta de datos es la de trabajo y no hay nada que
 * importar; así el harness no ve ningún cambio).
 * <p>Orden: selector y confirmación en el EDT; en un hilo de fondo, preparar (copiar a .importando y las recs) y,
 * sin nada por medio, colocar + relanzar + System.exit. Ningún diálogo entre colocar y salir: mientras está abierto,
 * la app vieja seguiría escribiendo. El resultado se deja en un archivo y lo enseña la app nueva al arrancar. Se sale
 * sin el cierre normal: guardaría la lista y los países que hay en memoria encima de lo importado.
 */
public final class ImportarDatos {
    private ImportarDatos() {}

    /** Aviso pendiente para la app relanzada, en la carpeta de datos. */
    static final String AVISO = ".aviso_importacion.txt";

    /** Al arrancar, en el EDT y con la ventana ya visible: enseña el resultado de una importación recién hecha y, si
     *  toca, ofrece importar una sola vez. No bloquea la carga, que sigue en sus hilos. */
    public static void alArrancar(Component padre) {
        if (!Sistema.empaquetada()) return;
        Path aviso = Sistema.enCarpetaBase(AVISO);
        new Thread(() -> {
            String texto = null;
            try {
                if (Files.exists(aviso)) { texto = Files.readString(aviso, StandardCharsets.UTF_8); Files.delete(aviso); }
            } catch (Exception ex) { log("importar: no se pudo leer el aviso: " + ex); }
            String txt = texto;
            SwingUtilities.invokeLater(() -> {
                if (txt != null) JOptionPane.showMessageDialog(padre, txt, NOMBRE, JOptionPane.INFORMATION_MESSAGE);
                else ofrecerSiToca(padre);
            });
        }, "importar-aviso").start();
    }

    /** Pregunta una sola vez si toca. La respuesta (cualquiera, también cerrar con la X) se recuerda. En el EDT. */
    static void ofrecerSiToca(Component padre) {
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

    /** Selector de carpeta; validación en segundo plano; confirmación si hay datos que se sustituirían. En el EDT. */
    public static void elegirEImportar(Component padre) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(t("Carpeta de la versión anterior de " + NOMBRE, "Folder of the previous " + NOMBRE + " version"));
        if (fc.showOpenDialog(padre) != JFileChooser.APPROVE_OPTION || fc.getSelectedFile() == null) return;
        Path origen = fc.getSelectedFile().toPath();
        Path datos = Sistema.carpetaBase(), recs = Sistema.carpetaRecs();
        new Thread(() -> {
            boolean vale = ImportacionDatos.valida(origen, datos);
            boolean confirmar = ImportacionDatos.pideConfirmacion(Sistema.datosNuevos(), datos);
            SwingUtilities.invokeLater(() -> {
                if (!vale) {
                    JOptionPane.showMessageDialog(padre,
                            t("Esa carpeta no tiene config.properties ni players.txt de " + NOMBRE + ".\nElige la carpeta donde estaba "
                                            + NOMBRE + ".exe.",
                                    "That folder has no " + NOMBRE + " config.properties or players.txt.\nChoose the folder where "
                                            + NOMBRE + ".exe was."),
                            NOMBRE, JOptionPane.WARNING_MESSAGE);
                    elegirEImportar(padre);
                    return;
                }
                Path copia = datos.resolve(ImportacionDatos.COPIA_ANTERIOR).toAbsolutePath();
                if (confirmar && JOptionPane.showConfirmDialog(padre,
                        t("Esto sustituirá tus grupos y ajustes actuales por los de la carpeta elegida.\n"
                                        + "Se guardará una copia de los actuales en:\n" + copia + "\n\n¿Importar?",
                                "This will replace your current groups and settings with those from the chosen folder.\n"
                                        + "A copy of the current ones will be saved in:\n" + copia + "\n\nImport?"),
                        NOMBRE, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
                new Thread(() -> importarYSalir(padre, origen, datos, recs), "importar-datos").start();
            });
        }, "importar-validar").start();
    }

    /** En el hilo de fondo: preparar, colocar y, si fue bien, dejar el aviso, relanzar y salir sin nada por medio. */
    private static void importarYSalir(Component padre, Path origen, Path datos, Path recs) {
        ImportacionDatos.Preparado p = ImportacionDatos.preparar(origen, datos, recs);
        String recsTxt = t(p.recsCopiadas() + " recs copiadas a " + recs.toAbsolutePath(),
                p.recsCopiadas() + " recs copied to " + recs.toAbsolutePath());
        if (!p.ok()) {
            log("importar: de " + origen + " falló al preparar: " + p.error() + " (" + p.recsCopiadas() + " recs copiadas)");
            String msg = t("No se pudieron importar los datos: tus grupos y ajustes no han cambiado.\n",
                    "Could not import the data: your groups and settings have not changed.\n") + p.error()
                    + (p.recsCopiadas() > 0 ? "\n" + t("Las recs ya copiadas se quedan: ", "Recs already copied stay: ") + recsTxt : "");
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(padre, msg, NOMBRE, JOptionPane.ERROR_MESSAGE));
            return;
        }
        ImportacionDatos.Resultado r = ImportacionDatos.colocar(p, datos, true);
        log("importar: de " + origen + " -> " + r.estado() + (r.error() != null ? " (" + r.error() + ")" : "")
                + ", recs copiadas " + p.recsCopiadas() + ", ya estaban " + p.recsYaEstaban() + ", copia de lo anterior: " + r.copiaAnterior());
        String copiaTxt = r.copiaAnterior() != null
                ? t("Copia de lo que tenías antes: ", "Copy of what you had before: ") + r.copiaAnterior().toAbsolutePath()
                : "";
        if (r.ok()) {
            String aviso = t("Datos importados de ", "Data imported from ") + origen.toAbsolutePath() + ".\n" + recsTxt + ".\n" + copiaTxt
                    + (p.autoarranque() ? "\n\n" + t("Si la versión en zip se abría con Windows, desactívalo en esa versión o bórrala.",
                            "If the zip version started with Windows, turn that off in that version or delete it.") : "");
            try {
                Files.writeString(datos.resolve(AVISO), aviso, StandardCharsets.UTF_8);   // directo: las escrituras de Archivos están en pausa
            } catch (Exception ex) { log("importar: no se pudo dejar el aviso: " + ex); }
            Sistema.relanzar();
            System.exit(0);
        }
        String cabecera = switch (r.estado()) {
            case SIN_CAMBIOS -> t("No se pudieron importar los datos: tus grupos y ajustes no han cambiado.",
                    "Could not import the data: your groups and settings have not changed.");
            case RESTAURADO -> t("La importación falló a mitad y se ha deshecho: tus grupos y ajustes están como antes.",
                    "The import failed halfway and was undone: your groups and settings are as before.");
            default -> t("La importación falló a mitad y NO se pudo deshacer del todo: puede haber datos mezclados.\n"
                            + "Cierra la app y copia a mano lo que haya en la copia de lo anterior.",
                    "The import failed halfway and could NOT be fully undone: data may be mixed.\n"
                            + "Close the app and copy back by hand what is in the copy of the previous data.");
        };
        String msg = cabecera + "\n" + r.error() + (copiaTxt.isEmpty() ? "" : "\n" + copiaTxt)
                + (p.recsCopiadas() > 0 ? "\n" + t("Las recs ya copiadas se quedan: ", "Recs already copied stay: ") + recsTxt : "");
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(padre, msg, NOMBRE, JOptionPane.ERROR_MESSAGE));
    }
}
