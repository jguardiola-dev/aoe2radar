package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.util.BusquedaDatos;
import dev.tirador.aoe2radar.util.ImportacionDatos;
import dev.tirador.aoe2radar.util.Sistema;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * <p>Al primer arranque busca sola una 1.x (Escritorio, Descargas y Documentos, en segundo plano y con plazo) y la
 * ofrece con su ruta; al elegir a mano, busca la carpeta de datos dentro de la elegida (util.BusquedaDatos). Cada
 * paso queda en el log: nada termina en silencio.
 * <p>Orden: selector y confirmación en el EDT; en un hilo de fondo, preparar (copiar a .importando y las recs) y,
 * sin nada por medio, colocar + relanzar + System.exit. Ningún diálogo entre colocar y salir: mientras está abierto,
 * la app vieja seguiría escribiendo. El resultado se deja en un archivo y lo enseña la app nueva al arrancar. Se sale
 * sin el cierre normal: guardaría la lista y los países que hay en memoria encima de lo importado.
 */
public final class ImportarDatos {
    private ImportarDatos() {}

    /** Aviso pendiente para la app relanzada, en la carpeta de datos. */
    static final String AVISO = ".aviso_importacion.txt";

    /** Una sola importación a la vez (el menú de Configuración sigue disponible mientras se prepara la primera). */
    private static final AtomicBoolean IMPORTANDO = new AtomicBoolean();

    /** Reserva la importación; false si ya hay una en marcha. */
    static boolean reservar() { return IMPORTANDO.compareAndSet(false, true); }

    static void liberar() { IMPORTANDO.set(false); }

    private static void avisarEnMarcha(Component padre) {
        JOptionPane.showMessageDialog(padre, t("Ya hay una importación en marcha: espera a que termine.",
                "An import is already running: wait for it to finish."), NOMBRE, JOptionPane.INFORMATION_MESSAGE);
    }

    /** Plazo de la búsqueda de una 1.x en Escritorio, Descargas y Documentos al primer arranque. */
    static final long PLAZO_DETECCION_S = 8;

    /** Al arrancar, en el EDT y con la ventana ya visible: enseña el resultado de una importación recién hecha y, si
     *  toca, ofrece importar una sola vez. Todo el disco (el aviso, config, la búsqueda) en un hilo de fondo. */
    public static void alArrancar(Component padre) {
        if (!Sistema.empaquetada()) return;
        Path aviso = Sistema.enCarpetaBase(AVISO);
        new Thread(() -> {
            String texto = null;
            try {
                if (Files.exists(aviso)) { texto = Files.readString(aviso, StandardCharsets.UTF_8); Files.delete(aviso); }
            } catch (Exception ex) { log("importar: no se pudo leer el aviso: " + ex); }
            if (texto != null) {
                String txt = texto;
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(padre, txt, NOMBRE, JOptionPane.INFORMATION_MESSAGE));
                return;
            }
            if (!ImportacionDatos.debeOfrecer(Sistema.datosNuevos(), leerConfig(ImportacionDatos.CLAVE_OFRECIDA, "false"))) return;
            BusquedaDatos.Candidata c = detectar(Sistema.carpetasDeBusqueda(), excluidas(),
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(PLAZO_DETECCION_S));
            SwingUtilities.invokeLater(() -> ofrecer(padre, c));
        }, "importar-oferta").start();
    }

    /** Carpetas que nunca son origen: la de datos y la de la app. */
    static List<Path> excluidas() { return List.of(Sistema.carpetaBase(), Sistema.carpetaApp()); }

    /** La candidata más reciente en las raíces (Escritorio, Descargas, Documentos), o null; lo encontrado, al log. */
    static BusquedaDatos.Candidata detectar(List<Path> raices, List<Path> excluir, long limiteNanos) {
        List<BusquedaDatos.Candidata> cs = BusquedaDatos.buscar(raices, BusquedaDatos.PROFUNDIDAD, BusquedaDatos.TOPE_CARPETAS,
                limiteNanos, excluir);
        log("importar: búsqueda de 1.x en " + raices + ": " + (cs.isEmpty() ? "ninguna"
                : cs.size() + " candidata(s): " + cs.stream().map(x -> x.carpeta().toString()).toList()));
        return cs.isEmpty() ? null : cs.get(0);
    }

    /** Texto de la oferta con una 1.x encontrada. */
    static String textoEncontrada(BusquedaDatos.Candidata c) {
        String fecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())
                .format(c.modificado().toInstant());
        return t("He encontrado " + NOMBRE + " 1.x en\n" + c.carpeta() + "\n(modificado " + fecha + ").\n¿Importar sus grupos y ajustes?",
                "Found " + NOMBRE + " 1.x in\n" + c.carpeta() + "\n(modified " + fecha + ").\nImport its groups and settings?");
    }

    /** La oferta, una sola vez: con la 1.x encontrada ([Importar] [Elegir otra carpeta…] [No, gracias]) o sin ella
     *  ([Elegir carpeta…] [No, gracias]). La respuesta (también cerrar con la X) se recuerda. En el EDT. */
    static void ofrecer(Component padre, BusquedaDatos.Candidata c) {
        int r;
        if (c != null) {
            Object[] op = { t("Importar", "Import"), t("Elegir otra carpeta…", "Choose another folder…"), t("No, gracias", "No, thanks") };
            r = JOptionPane.showOptionDialog(padre, textoEncontrada(c), NOMBRE, JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, op, op[0]);
        } else {
            Object[] op = { t("Elegir carpeta…", "Choose folder…"), t("No, gracias", "No, thanks") };
            int x = JOptionPane.showOptionDialog(padre,
                    t("¿Vienes de " + NOMBRE + " 1.x en zip?\nElige su carpeta para importar grupos y ajustes.\n\n"
                                    + "También lo tienes en Configuración → Importar datos de otra versión…",
                            "Coming from " + NOMBRE + " 1.x (zip)?\nChoose its folder to import your groups and settings.\n\n"
                                    + "You can also do it later in Settings → Import data from another version…"),
                    NOMBRE, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, op, op[1]);
            r = x == 0 ? 1 : x == 1 ? 2 : -1;   // mismas respuestas que la de arriba: 1 elegir, 2 no
        }
        String resp = r == 0 ? "importar la encontrada" : r == 1 ? "elegir carpeta" : r == 2 ? "no, gracias" : "cerrada sin contestar";
        log("importar: oferta del primer arranque mostrada (" + (c != null ? "encontrada " + c.carpeta() : "sin candidata")
                + "); respuesta: " + resp);
        guardarConfig(ImportacionDatos.CLAVE_OFRECIDA, "true");
        if (r == 0) validarYConfirmar(padre, c.carpeta(), true);
        else if (r == 1) elegirEImportar(padre);
    }

    /** Selector de carpeta y, con lo elegido, validar y confirmar. En el EDT. */
    public static void elegirEImportar(Component padre) {
        if (IMPORTANDO.get()) { log("importar: ya hay una en marcha; no se lanza otra"); avisarEnMarcha(padre); return; }
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(t("Carpeta de la versión anterior de " + NOMBRE, "Folder of the previous " + NOMBRE + " version"));
        if (fc.showOpenDialog(padre) != JFileChooser.APPROVE_OPTION || fc.getSelectedFile() == null) {
            log("importar: selector de carpeta cancelado");
            return;
        }
        Path elegida = fc.getSelectedFile().toPath();
        log("importar: carpeta elegida " + elegida);
        validarYConfirmar(padre, elegida, false);
    }

    /** En segundo plano, busca la carpeta de datos dentro de la elegida; de vuelta en el EDT, avisa si no hay o pide
     *  confirmación con la ruta exacta cuando hace falta (datos que se sustituirían, o la de datos no es la elegida o
     *  había varias). {@code yaVista}: el usuario ya vio esa ruta exacta en la oferta. */
    static void validarYConfirmar(Component padre, Path elegida, boolean yaVista) {
        Path datos = Sistema.carpetaBase(), recs = Sistema.carpetaRecs();
        new Thread(() -> {
            int[] n = { 0 };
            Path origen = BusquedaDatos.resolverElegida(elegida, excluidas(),
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(PLAZO_DETECCION_S), n);
            boolean vale = origen != null && ImportacionDatos.valida(origen, datos);
            if (!vale) log("importar: " + elegida + " no vale: " + (origen == null
                    ? "no hay config.properties ni players.txt junto a aoe2radar.exe/SpoilerFreeRecs.exe (ni en ella ni hasta "
                            + BusquedaDatos.PROFUNDIDAD + " niveles dentro)"
                    : origen + " es (o contiene) la carpeta de datos o la de la app"));
            else log("importar: " + elegida + " -> se importaría de " + origen + " (" + n[0] + " candidata(s))");
            boolean confirmar = ImportacionDatos.pideConfirmacion(Sistema.datosNuevos(), datos);
            boolean ensenarRuta = vale && !yaVista && (n[0] > 1 || !origen.equals(elegida.toAbsolutePath().normalize()));
            SwingUtilities.invokeLater(() -> {
                if (!vale) {
                    JOptionPane.showMessageDialog(padre,
                            t("No encuentro los datos de " + NOMBRE + " en\n" + elegida + "\n\nBusca la carpeta que contiene "
                                            + NOMBRE + ".exe (o SpoilerFreeRecs.exe) junto a config.properties o players.txt.",
                                    "Cannot find " + NOMBRE + " data in\n" + elegida + "\n\nLook for the folder that contains "
                                            + NOMBRE + ".exe (or SpoilerFreeRecs.exe) next to config.properties or players.txt."),
                            NOMBRE, JOptionPane.WARNING_MESSAGE);
                    elegirEImportar(padre);
                    return;
                }
                if (confirmar || ensenarRuta) {
                    Path copia = datos.resolve(ImportacionDatos.COPIA_ANTERIOR).toAbsolutePath();
                    String texto = t("Importar desde:\n", "Import from:\n") + origen
                            + (confirmar ? t("\n\nEsto sustituirá tus grupos y ajustes actuales por los de esa carpeta.\n"
                                            + "Se guardará una copia de los actuales en:\n" + copia,
                                    "\n\nThis will replace your current groups and settings with those from that folder.\n"
                                            + "A copy of the current ones will be saved in:\n" + copia) : "")
                            + t("\n\n¿Importar?", "\n\nImport?");
                    if (JOptionPane.showConfirmDialog(padre, texto, NOMBRE, JOptionPane.OK_CANCEL_OPTION,
                            confirmar ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
                        log("importar: confirmación rechazada (" + origen + ")");
                        return;
                    }
                }
                if (!reservar()) { log("importar: ya hay una en marcha; no se lanza otra"); avisarEnMarcha(padre); return; }
                log("importar: empieza desde " + origen);
                new Thread(() -> {
                    try { importarYSalir(padre, origen, datos, recs); } finally { liberar(); }   // si sale bien, no vuelve: exit
                }, "importar-datos").start();
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
            if (!Sistema.relanzar()) {
                log("importar: no se pudo relanzar la app; hay que abrirla a mano");
                // Este diálogo SÍ puede ir entre colocar y salir: las escrituras de Archivos siguen en pausa (colocar
                // con seguirEnPausa=true), así que mientras está abierto nada de lo que hay en memoria llega al disco,
                // y al cerrarlo se sale con System.exit sin el cierre normal. invokeAndWait: el EDT lo enseña y este
                // hilo espera a que se cierre.
                try {
                    SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(padre,
                            t("Datos importados. Vuelve a abrir " + NOMBRE + ".", "Data imported. Open " + NOMBRE + " again."),
                            NOMBRE, JOptionPane.INFORMATION_MESSAGE));
                } catch (Exception ex) { log("importar: aviso de reabrir: " + ex); }
            }
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
