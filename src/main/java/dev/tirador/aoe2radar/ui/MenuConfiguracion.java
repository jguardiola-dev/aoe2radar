package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.service.ControlService;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;

import static dev.tirador.aoe2radar.service.Juego.rutaCaptureAge;
import static dev.tirador.aoe2radar.ui.TemaApp.TEMA_CLARO;
import static dev.tirador.aoe2radar.ui.TemaApp.TEMA_OSCURO;
import static dev.tirador.aoe2radar.ui.TemaApp.TEMA_SISTEMA;
import static dev.tirador.aoe2radar.ui.TemaApp.flatLafDisponible;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;
import static dev.tirador.aoe2radar.util.Identidad.RELEASES_API;
import static dev.tirador.aoe2radar.util.Identidad.RELEASES_URL;
import static dev.tirador.aoe2radar.util.Identidad.VERSION;
import static dev.tirador.aoe2radar.util.Sistema.fijarAutoArranque;
import static dev.tirador.aoe2radar.util.Sistema.rutaExePropia;
import static dev.tirador.aoe2radar.util.Texto.versionMayor;

/**
 * El botón «Configuración ▾» de la barra superior: idioma, tema, letra, arranque, ELO en la watchlist,
 * CaptureAge, vigilancia de vivos, top ladder, carpeta savegame, actualizaciones y «Acerca de»; y, si va en el
 * mismo bloque, la esquina «Mi perfil». Vive en {@code ui} porque es cromo puro (construcción de menú, ningún
 * dato de negocio propio); lo que cada ítem necesita de otras zonas de la ventana (watchlist, partidas,
 * vigilancia, mi perfil…) llega por {@link Anfitrion}, implementado por la ventana con lambdas a lo que ya
 * existe, para no acoplar este menú a la clase concreta de cada zona.
 */
public final class MenuConfiguracion {

    /** Lo que este menú necesita de la ventana y de las otras zonas, con nombres de negocio. */
    public interface Anfitrion {
        /** Componente padre para los diálogos (JOptionPane/JFileChooser) de este menú. */
        Component padre();
        void estado(String texto);
        void aplicarTema(String tema);
        void abrirUrl(String url);
        /** Enseña el botón «Nueva versión…» con la etiqueta dada (o no hace nada si el botón no existe aún). */
        void mostrarNuevaVersion(String etiqueta);
        boolean mostrarEloWatch();
        void fijarMostrarEloWatch(boolean mostrar);
        void repintarListaJugadores();
        /** Reinicia el temporizador de vigilancia de vivos con el intervalo recién guardado en config. */
        void reiniciarVigilancia();
        void cargarAliasesYNotas();
        boolean modoTop();
        void forzarRecargaTop();
        Path elegirCarpetaSavegame();
        void refiltrarPartidas();
        boolean hayCarpetaSavegame();
        void mostrarMiPerfil();
        void cambiarCuentaPropia();
        void mostrarAcercaDe();
    }

    private final Anfitrion anfitrion;
    private final ControlService controlService;
    private final JPopupMenu menu;
    private final JPanel esquina;

    /** Construye el menú completo y la esquina «Mi perfil», en el mismo orden que antes en
     *  construirBarraSuperior (hoy CableadoCromo.construirBarraSuperior). {@code autoSgItem} es un campo YA
     *  existente de la ventana (lo usa también Partidas para saber si copia la rec al savegame), no uno nuevo:
     *  se recibe ya construido. abrirDonacion() vive en AccionesVentana.abrirDonacion (usa java.net.URI, que
     *  ui no puede importar). */
    public MenuConfiguracion(String temaInicial, JCheckBoxMenuItem autoSgItem, ControlService controlService,
                              Anfitrion anfitrion) {
        this.anfitrion = anfitrion;
        this.controlService = controlService;

        JButton configBtn = new JButton(t("Configuración ▾", "Settings ▾"));
        JPopupMenu configMenu = new JPopupMenu();

        JMenu idiomaMenu = new JMenu(t("Idioma", "Language"));
        ButtonGroup gIdioma = new ButtonGroup();
        for (String[] par : new String[][]{ { "Español", "es" }, { "English", "en" } }) {
            String valor = par[1];
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(par[0], valor.equals(IDIOMA));
            it.addActionListener(e -> {
                guardarConfig("idioma", valor);
                JOptionPane.showMessageDialog(anfitrion.padre(),
                        valor.equals("es") ? "El idioma se aplicará la próxima vez que abras la aplicación."
                                           : "The language will apply the next time you open the app.",
                        valor.equals("es") ? "Idioma" : "Language", JOptionPane.INFORMATION_MESSAGE);
            });
            gIdioma.add(it);
            idiomaMenu.add(it);
        }

        JMenu temaMenu = new JMenu(t("Tema", "Theme"));
        ButtonGroup gTema = new ButtonGroup();
        for (String[] par : new String[][]{ { t("Sistema", "System"), TEMA_SISTEMA }, { t("Claro", "Light"), TEMA_CLARO }, { t("Oscuro", "Dark"), TEMA_OSCURO } }) {
            String valor = par[1];
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(par[0], valor.equals(temaInicial));
            it.addActionListener(e -> { guardarConfig("tema", valor); anfitrion.aplicarTema(valor); });
            gTema.add(it);
            temaMenu.add(it);
        }
        temaMenu.setEnabled(flatLafDisponible);

        JMenu letraMenu = new JMenu(t("Letra", "Font size"));
        ButtonGroup gLetra = new ButtonGroup();
        String letraSel = leerConfig("letra", "grande");
        for (String[] par : new String[][]{ { t("Pequeño", "Small"), "normal" }, { t("Normal", "Normal"), "grande" }, { t("Grande", "Large"), "muygrande" } }) {
            String valor = par[1];
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(par[0], valor.equals(letraSel));
            it.addActionListener(e -> {
                guardarConfig("letra", valor);
                anfitrion.aplicarTema(TemaApp.temaValido(leerConfig("tema", TEMA_SISTEMA)));
            });
            gLetra.add(it);
            letraMenu.add(it);
        }
        letraMenu.setEnabled(flatLafDisponible);

        JCheckBoxMenuItem buscarAbrirItem = new JCheckBoxMenuItem(t("Buscar al abrir", "Search on startup"),
                Boolean.parseBoolean(leerConfig("buscar_al_abrir", "true")));
        buscarAbrirItem.setToolTipText(t("Al abrir la app, busca partidas de tus seguidos automáticamente", "Search your watchlist automatically when the app opens"));
        buscarAbrirItem.addActionListener(e ->
                guardarConfig("buscar_al_abrir", String.valueOf(buscarAbrirItem.isSelected())));

        JCheckBoxMenuItem autoWinItem = new JCheckBoxMenuItem(t("Ejecutar al iniciar Windows", "Run at Windows startup"),
                Boolean.parseBoolean(leerConfig("autoarranque", "false")));
        if (rutaExePropia() == null) {
            autoWinItem.setEnabled(false);
            autoWinItem.setSelected(false);
            autoWinItem.setToolTipText(t("Disponible solo ejecutando el " + NOMBRE + ".exe empaquetado",
                    "Only available when running the packaged " + NOMBRE + ".exe"));
        } else {
            autoWinItem.setToolTipText(t("Añade la app al arranque de tu usuario de Windows (sin permisos especiales)",
                    "Adds the app to your Windows user startup (no special permissions)"));
            autoWinItem.addActionListener(e -> {
                boolean deseado = autoWinItem.isSelected();   // leído en el EDT antes de salir de él (fila 37)
                autoWinItem.setEnabled(false);   // hallazgo del revisor: sin esto, dos clics rápidos lanzan dos hilos
                new Thread(() -> {
                    boolean ok = fijarAutoArranque(deseado);
                    SwingUtilities.invokeLater(() -> {
                        if (!ok) {
                            autoWinItem.setSelected(false);
                            anfitrion.estado(t("No se pudo cambiar el autoarranque (¿antivirus?).",
                                    "Could not change startup entry (antivirus?)."));
                        }
                        guardarConfig("autoarranque", String.valueOf(autoWinItem.isSelected()));
                        autoWinItem.setEnabled(true);
                    });
                }, "autoarranque").start();
            });
        }

        JCheckBoxMenuItem iniMinItem = new JCheckBoxMenuItem(t("Iniciar minimizada", "Start minimized"),
                Boolean.parseBoolean(leerConfig("inicio_min", "false")));
        iniMinItem.setToolTipText(t("La ventana arranca minimizada en la barra de tareas",
                "The window starts minimized to the taskbar"));
        iniMinItem.addActionListener(e ->
                guardarConfig("inicio_min", String.valueOf(iniMinItem.isSelected())));

        autoSgItem.setToolTipText(t("Tras cada descarga, copia también la rec a la carpeta savegame del juego", "After each download, also copy the rec to the game savegame folder"));
        autoSgItem.addActionListener(e -> {
            if (autoSgItem.isSelected() && !anfitrion.hayCarpetaSavegame()) {
                autoSgItem.setSelected(false);
                return;
            }
            guardarConfig("autosavegame", String.valueOf(autoSgItem.isSelected()));
        });

        JMenuItem carpetaItem = new JMenuItem(t("Cambiar carpeta savegame…", "Change savegame folder…"));
        carpetaItem.addActionListener(e -> {
            Path p = anfitrion.elegirCarpetaSavegame();
            if (p != null) { anfitrion.estado(t("Carpeta savegame: ", "Savegame folder: ") + p); anfitrion.refiltrarPartidas(); }
        });

        JMenuItem aboutItem = new JMenuItem(t("Acerca de…", "About…"));
        aboutItem.addActionListener(e -> anfitrion.mostrarAcercaDe());

        configMenu.add(idiomaMenu);
        configMenu.add(temaMenu);
        configMenu.add(letraMenu);
        configMenu.addSeparator();
        JCheckBoxMenuItem eloWatchItem = new JCheckBoxMenuItem(
                t("Mostrar ELO en la Watchlist", "Show ELO in the Watchlist"), anfitrion.mostrarEloWatch());
        eloWatchItem.setToolTipText(t("El ELO se actualiza solo al abrir la app, nunca al buscar, para no chivar resultados",
                "ELO refreshes only when the app opens, never on search, so results are never given away"));
        eloWatchItem.addActionListener(e -> {
            anfitrion.fijarMostrarEloWatch(eloWatchItem.isSelected());
            guardarConfig("elo_watchlist", String.valueOf(eloWatchItem.isSelected()));
            anfitrion.repintarListaJugadores();
        });
        configMenu.add(buscarAbrirItem);
        configMenu.add(autoWinItem);
        configMenu.add(iniMinItem);
        configMenu.add(eloWatchItem);
        configMenu.add(autoSgItem);
        JCheckBoxMenuItem usarCaItem = new JCheckBoxMenuItem(t("Usar CaptureAge", "Use CaptureAge"),
                Boolean.parseBoolean(leerConfig("usar_ca", "false")));
        usarCaItem.setToolTipText(t("Al espectar un directo, lanza también CaptureAge (se engancha solo al juego).",
                "When spectating, also launches CaptureAge (it hooks onto the game by itself)."));
        usarCaItem.addActionListener(e -> {
            guardarConfig("usar_ca", String.valueOf(usarCaItem.isSelected()));
            if (usarCaItem.isSelected() && rutaCaptureAge() == null)
                anfitrion.estado(t("CaptureAge no aparece en la ruta estándar: usa «Cambiar ruta de CaptureAge…».",
                        "CaptureAge isn't at the standard path: use \u201CChange CaptureAge path\u2026\u201D."));
        });
        configMenu.add(usarCaItem);
        JMenuItem rutaCaItem = new JMenuItem(t("Cambiar ruta de CaptureAge…", "Change CaptureAge path…"));
        rutaCaItem.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            Path actual = rutaCaptureAge();
            if (actual != null) fc.setSelectedFile(actual.toFile());
            fc.setDialogTitle(t("Elegir CaptureAge.exe", "Pick CaptureAge.exe"));
            if (fc.showOpenDialog(anfitrion.padre()) == JFileChooser.APPROVE_OPTION) {
                guardarConfig("ca_ruta", fc.getSelectedFile().getAbsolutePath());
                anfitrion.estado(t("Ruta de CaptureAge guardada: ", "CaptureAge path saved: ")
                        + fc.getSelectedFile().getAbsolutePath());
            }
        });
        configMenu.add(rutaCaItem);
        JMenu vigMenu = new JMenu(t("Vigilancia de vivos", "Live watch interval"));
        ButtonGroup vg = new ButtonGroup();
        String tickActual = leerConfig("tick_min", "1");
        for (String mins : new String[]{"1", "2", "5"}) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(t("Cada ", "Every ") + mins + " min", mins.equals(tickActual));
            it.addActionListener(e -> {
                guardarConfig("tick_min", mins);
                anfitrion.reiniciarVigilancia();
            });
            vg.add(it);
            vigMenu.add(it);
        }
        vigMenu.setToolTipText(t("Cada cuánto se refrescan los puntos rojos (1 min por defecto; sube si el servicio anda flojo).",
                "How often live dots refresh (1 min by default; raise it if the service is struggling)."));
        configMenu.add(vigMenu);
        anfitrion.cargarAliasesYNotas();
        JMenu topMenu = new JMenu(t("Top ladder", "Top ladder"));
        ButtonGroup topGrp = new ButtonGroup();
        for (int n : new int[]{ 25, 50, 100 }) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem("Top " + n,
                    String.valueOf(n).equals(leerConfig("top_n", "50")));
            it.addActionListener(e -> {
                guardarConfig("top_n", String.valueOf(n));
                if (anfitrion.modoTop()) anfitrion.forzarRecargaTop();
            });
            topGrp.add(it);
            topMenu.add(it);
        }
        configMenu.add(topMenu);
        configMenu.add(carpetaItem);
        configMenu.addSeparator();
        JMenuItem updItem = new JMenuItem(t("Buscar actualizaciones…", "Check for updates…"));
        updItem.addActionListener(e -> comprobarActualizacion(true));
        configMenu.add(updItem);
        configMenu.add(aboutItem);
        configBtn.addActionListener(e -> configMenu.show(configBtn, 0, configBtn.getHeight()));
        this.menu = configMenu;

        JPanel esquinaPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));   // Configuración: arriba a la derecha, siempre
        JButton miPerfilBtn = new JButton(t("Mi perfil", "My profile"));
        miPerfilBtn.setFocusable(false); miPerfilBtn.putClientProperty("JButton.buttonType", "roundRect");
        miPerfilBtn.setToolTipText(t("Tu propio perfil (la primera vez te pide tu nick y lo recuerda; clic derecho para cambiar de cuenta). Con tu nick guardado, la app te avisa de tus partidas.", "Your own profile (asks your nick the first time and remembers it; right-click to change account). With your nick saved, the app notifies you about your games."));
        miPerfilBtn.addActionListener(e -> anfitrion.mostrarMiPerfil());
        miPerfilBtn.addMouseListener(new MouseAdapter() {   // clic derecho: cambiar de cuenta
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) menu(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) menu(e); }
            void menu(MouseEvent e) {
                JPopupMenu pm = new JPopupMenu();
                JMenuItem otra = new JMenuItem(t("Cambiar de cuenta…", "Change account…")); otra.addActionListener(a -> { guardarConfig("mi_pid", ""); anfitrion.cambiarCuentaPropia(); }); pm.add(otra);
                pm.show(miPerfilBtn, e.getX(), e.getY());
            }
        });
        esquinaPanel.add(miPerfilBtn);
        esquinaPanel.add(configBtn);
        this.esquina = esquinaPanel;
    }

    /** El popup del botón «Configuración ▾» (lo lee ComponentesTema para repintarlo al cambiar de tema). */
    public JPopupMenu menu() { return menu; }

    /** La esquina superior derecha: «Mi perfil» + «Configuración ▾». */
    public JPanel esquina() { return esquina; }

    /** Consulta GitHub Releases (una llamada, sin claves) y enseña el botón si hay versión nueva. La red y la
     *  lectura del JSON viven en service.ControlService#ultimaVersion; aquí solo quedan la UI y los diálogos. */
    public void comprobarActualizacion(boolean manual) {
        new Thread(() -> {
            final String tagF = controlService.ultimaVersion(RELEASES_API);
            SwingUtilities.invokeLater(() -> {
                if (tagF != null && versionMayor(tagF, VERSION)) {
                    String limpia = tagF.replaceFirst("^[vV]", "");
                    anfitrion.mostrarNuevaVersion(t("Nueva versión ", "New version ") + limpia + t(" \u2014 Descargar", " \u2014 Download"));
                    if (manual) JOptionPane.showMessageDialog(anfitrion.padre(),
                            t("Hay una versión nueva: ", "There is a new version: ") + limpia
                                    + t("\nSe abrirá la página de descarga.", "\nThe download page will open."),
                            t("Actualización", "Update"), JOptionPane.INFORMATION_MESSAGE);
                    if (manual) anfitrion.abrirUrl(RELEASES_URL);
                } else if (manual) {
                    JOptionPane.showMessageDialog(anfitrion.padre(),
                            tagF == null ? t("No se pudo comprobar (¿sin red?).", "Couldn't check (no network?).")
                                         : t("Tienes la última versión (", "You have the latest version (") + VERSION + ").",
                            t("Actualización", "Update"), JOptionPane.INFORMATION_MESSAGE);
                }
            });
        }, "actualizaciones").start();
    }
}
