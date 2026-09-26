package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.service.ControlService;
import dev.tirador.aoe2radar.service.VistaInicial;

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
import java.util.ArrayList;
import java.util.List;

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
        /** «Abrir en»: los grupos del combo de la Watchlist (sin «Todos» ni las vistas ★). */
        List<String> gruposWatchlist();
        /** «Abrir en»: los clanes guardados en ★ Top clan (solo se puede abrir en uno de ellos). */
        List<String> clanesGuardados();
        /** «Abrir en»: todos los países, con su nombre en el idioma de la app. */
        List<PaisItem> paises();
    }

    private final Anfitrion anfitrion;
    private final ControlService controlService;
    private final JPopupMenu menu;
    private final JPanel esquina;
    // «Abrir en» (1.3): se rehacen al abrir el menú (refrescarAbrirEn), porque grupos y clanes cambian con la sesión
    private final JRadioButtonMenuItem abrirTop, abrirPais, abrirClan, abrirGrupo, abrirTodos;
    private final JCheckBoxMenuItem buscarAbrirItem;

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
        this.buscarAbrirItem = buscarAbrirItem;
        buscarAbrirItem.addActionListener(e ->
                guardarConfig("buscar_al_abrir", String.valueOf(buscarAbrirItem.isSelected())));

        // «Abrir en» (decisión de Jorge, 1.3): la vista de la Watchlist con la que abre la app; ver service.VistaInicial
        JMenu abrirMenu = new JMenu(t("Abrir en", "Open in"));
        abrirMenu.setToolTipText(t("La vista de la Watchlist con la que abre la app (por defecto, ★ Top ladder)",
                "The Watchlist view the app opens in (★ Top ladder by default)"));
        ButtonGroup gAbrir = new ButtonGroup();
        abrirTop = new JRadioButtonMenuItem();
        abrirPais = new JRadioButtonMenuItem();
        abrirClan = new JRadioButtonMenuItem();
        abrirGrupo = new JRadioButtonMenuItem();
        abrirTodos = new JRadioButtonMenuItem();
        abrirTop.addActionListener(e -> guardarAbrirEn(VistaInicial.Eleccion.TOP_LADDER));
        abrirTodos.addActionListener(e -> guardarAbrirEn(VistaInicial.Eleccion.TODOS_LOS_GRUPOS));
        abrirPais.addActionListener(e -> {
            List<PaisItem> ps = anfitrion.paises();
            PaisItem actual = null;
            VistaInicial.Eleccion ahora = eleccionAbrirEn();
            for (PaisItem pi : ps) if (ahora.tipo() == VistaInicial.Tipo.PAIS && pi.code().equals(ahora.valor())) actual = pi;
            Object elegido = JOptionPane.showInputDialog(anfitrion.padre(), t("¿El top de qué país?", "Which country's top?"),
                    t("Abrir en", "Open in"), JOptionPane.PLAIN_MESSAGE, null, ps.toArray(), actual);
            if (elegido instanceof PaisItem pi) guardarAbrirEn(new VistaInicial.Eleccion(VistaInicial.Tipo.PAIS, pi.code()));
            else refrescarAbrirEn();   // cancelado: la marca vuelve a lo guardado
        });
        abrirClan.addActionListener(e -> elegirYGuardar(VistaInicial.Tipo.CLAN, anfitrion.clanesGuardados(), t("¿Qué clan?", "Which clan?")));
        abrirGrupo.addActionListener(e -> elegirYGuardar(VistaInicial.Tipo.GRUPO, anfitrion.gruposWatchlist(), t("¿Qué grupo?", "Which group?")));
        for (JRadioButtonMenuItem it : new JRadioButtonMenuItem[]{ abrirTop, abrirPais, abrirClan, abrirGrupo, abrirTodos }) {
            gAbrir.add(it);
            abrirMenu.add(it);
        }
        pintarAbrirEn(VistaInicial.leer(leerConfig(VistaInicial.CLAVE, "top")), null, null, null);   // la ventana aún no tiene Watchlist: sin listas

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
        configMenu.add(abrirMenu);
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
        configMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {   // grupos y clanes de ahora mismo
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) { refrescarAbrirEn(); }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { }
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { }
        });
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

    // ----- «Abrir en» -----

    /** Lo guardado en «Abrir en», comprobado contra lo que existe hoy (si ya no existe, ★ Top ladder). */
    private VistaInicial.Eleccion eleccionAbrirEn() {
        List<String> codigos = new ArrayList<>();
        for (PaisItem pi : anfitrion.paises()) codigos.add(pi.code());
        return VistaInicial.resolver(VistaInicial.leer(leerConfig(VistaInicial.CLAVE, "top")), codigos,
                anfitrion.clanesGuardados(), anfitrion.gruposWatchlist());
    }

    private void guardarAbrirEn(VistaInicial.Eleccion e) {
        guardarConfig(VistaInicial.CLAVE, e.aConfig());
        refrescarAbrirEn();
    }

    /** Clan o grupo: se elige de la lista (con el actual preseleccionado); cancelar deja lo que había. */
    private void elegirYGuardar(VistaInicial.Tipo tipo, List<String> opciones, String pregunta) {
        VistaInicial.Eleccion ahora = eleccionAbrirEn();
        Object elegido = JOptionPane.showInputDialog(anfitrion.padre(), pregunta, t("Abrir en", "Open in"),
                JOptionPane.PLAIN_MESSAGE, null, opciones.toArray(), ahora.tipo() == tipo ? ahora.valor() : null);
        if (elegido instanceof String s) guardarAbrirEn(new VistaInicial.Eleccion(tipo, s));
        else refrescarAbrirEn();
    }

    /** Pone «Abrir en» y «Buscar al abrir» como diga la config, con los grupos, clanes y países de ahora. */
    void refrescarAbrirEn() {
        pintarAbrirEn(eleccionAbrirEn(), anfitrion.paises(), anfitrion.clanesGuardados(), anfitrion.gruposWatchlist());
    }

    /** Listas null: aún no se conocen (al construir el menú, antes que la Watchlist): no se deshabilita nada. */
    private void pintarAbrirEn(VistaInicial.Eleccion e, List<PaisItem> paises, List<String> clanes, List<String> grupos) {
        String topPais = t("\u2605 Top pa\u00eds", "\u2605 Country top"), topClan = t("\u2605 Top clan", "\u2605 Clan top");
        abrirTop.setText("\u2605 Top ladder");
        abrirPais.setText(topPais + (e.tipo() == VistaInicial.Tipo.PAIS ? ": " + nombrePais(paises, e.valor()) : "") + "…");
        abrirClan.setText(topClan + (e.tipo() == VistaInicial.Tipo.CLAN ? ": " + e.valor() : "") + "…");
        abrirGrupo.setText(t("Grupo", "Group") + (e.tipo() == VistaInicial.Tipo.GRUPO ? ": " + e.valor() : "") + "…");
        abrirTodos.setText(t("Todos", "All"));
        abrirClan.setEnabled(clanes == null || !clanes.isEmpty());
        abrirClan.setToolTipText(clanes != null && clanes.isEmpty()
                ? t("Guarda antes un clan en ★ Top clan («Guardar clan»)", "Save a clan first in ★ Clan top (“Save clan”)") : null);
        abrirGrupo.setEnabled(grupos == null || !grupos.isEmpty());
        JRadioButtonMenuItem marcado = switch (e.tipo()) {
            case TOP -> abrirTop;
            case PAIS -> abrirPais;
            case CLAN -> abrirClan;
            case GRUPO -> abrirGrupo;
            case TODOS -> abrirTodos;
        };
        marcado.setSelected(true);
        // «Buscar al abrir» solo tiene sentido si la app abre en tus seguidos (un grupo o «Todos»)
        buscarAbrirItem.setEnabled(e.buscaAlAbrir());
        buscarAbrirItem.setToolTipText(e.buscaAlAbrir()
                ? t("Al abrir la app, busca partidas de tus seguidos automáticamente", "Search your watchlist automatically when the app opens")
                : t("Solo se aplica si la app abre en un grupo o en «Todos» (Configuración → Abrir en): en los tops ★ no se busca al abrir.",
                    "Only applies when the app opens in a group or in “All” (Settings → Open in): the ★ tops don't search on startup."));
    }

    private static String nombrePais(List<PaisItem> paises, String code) {
        if (paises != null) for (PaisItem pi : paises) if (pi.code().equals(code)) return pi.nombre();
        return code.toUpperCase(java.util.Locale.ROOT);
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
