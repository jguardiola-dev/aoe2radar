package dev.tirador.aoe2radar;

import dev.tirador.aoe2radar.app.Servicios;
import dev.tirador.aoe2radar.cache.Paises;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.EnlaceVivo;
import dev.tirador.aoe2radar.service.Juego;
import dev.tirador.aoe2radar.service.MiPartidaServiceJuego;
import dev.tirador.aoe2radar.ui.AutoScroll;
import dev.tirador.aoe2radar.ui.BarraEstado;
import dev.tirador.aoe2radar.ui.ClicEnFondo;
import dev.tirador.aoe2radar.ui.MenuConfiguracion;
import dev.tirador.aoe2radar.ui.MiPartidaPanel;
import dev.tirador.aoe2radar.ui.Tareas;
import dev.tirador.aoe2radar.ui.TemaApp;
import dev.tirador.aoe2radar.ui.VentanaGuardada;
import dev.tirador.aoe2radar.ui.VentanaPrincipalAjustes;
import dev.tirador.aoe2radar.util.Config;
import dev.tirador.aoe2radar.util.Reloj;

import static dev.tirador.aoe2radar.api.Cancelacion.hiloOperacion;
import static dev.tirador.aoe2radar.api.Cancelacion.opEnCurso;
import static dev.tirador.aoe2radar.api.Cancelacion.stopOperacion;
import static dev.tirador.aoe2radar.api.Http.HTTP;
import static dev.tirador.aoe2radar.api.Http.nuevoHttp;
import static dev.tirador.aoe2radar.app.Servicios.CONTROL_SERVICE;
import static dev.tirador.aoe2radar.app.Servicios.LIVE;
import static dev.tirador.aoe2radar.app.Servicios.VIVO;
import static dev.tirador.aoe2radar.cache.Anotaciones.cargarAliases;
import static dev.tirador.aoe2radar.cache.Anotaciones.cargarNotas;
import static dev.tirador.aoe2radar.service.EnlaceVivo.tickMs;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** El «cromo» de la ventana principal: ajustes del JFrame, barra superior (pestañas de vistas aparte, en
 *  ui.Navegador; aquí el resto: ventana de horas, el menú «Configuración ▾» y su esquina «Mi perfil»), barra
 *  inferior, el montaje final (split Watchlist/resto y el filtro global de clics) y cuatro piezas de cableado
 *  que se inyectan como inicializadores de campo (barraEstadoAnfitrion, autoScroll, enlaceVivo, miPartida).
 *  Métodos static que reciben la ventana ({@code v}): esta clase no tiene estado propio, solo construye piezas
 *  de Swing que leen y escriben campos de SpoilerFreeRecs. Vive en el paquete raíz (no en ui) para poder leer
 *  esos campos sin volverlos public. Fase 3, tanda 4, oleada B, zona B3. */
final class CableadoCromo {

    private CableadoCromo() { }

    // Cierre, ventana recordada, foco perdido e iconos: ajustes del JFrame antes
    // de construir ningun panel. Se separa del resto del constructor porque es
    // configuración de ventana, no construcción de paneles.
    static void configurarVentana(SpoilerFreeRecs v) {
        VentanaPrincipalAjustes.configurar(v, v.logo, new VentanaPrincipalAjustes.Anfitrion() {
            @Override public void alCerrar() {
                VentanaGuardada.guardar(v, v.splitPrincipal); v.enlaceVivo.cerrar();
                Paises.guardarAlCerrar(2000);   // F13 (1.3): los países del último minuto; retiene el cierre 2 s como mucho
            }
            @Override public void alPerderFoco() { v.watchlist.ocultarHoverCard(true); }
        });
        // Camino explícito para el aviso de pausa por 429 (limpieza 1, fase 4): se fija aquí, en el EDT y con
        // barraEstado ya construido (es un inicializador de campo, corre antes que el cuerpo del constructor).
        Servicios.avisoPausa429 = v.barraEstado::mostrarPausaApi;
        // El mensaje de control.json (arreglo F10 de la revisión 1.3): la barra lo enseña cuando la ventana está a
        // la vista y solo entonces se marca como visto.
        Servicios.avisoControl = v.barraEstado::mostrarAvisoCuandoSeVea;
    }

    // La barra de arriba: ventana de horas/buscar, filtros, pestañas de vistas,
    // flechas de historial y el menú Configuración (idioma, tema, letra...). Recibe
    // temaInicial porque el menú de Tema marca la opción ya activa al abrir.
    static JPanel construirBarraSuperior(SpoilerFreeRecs v, String temaInicial) {
        // Barra superior: ventana de horas + buscar + filtro de modo + acerca de
        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));   // fija: nada salta de sitio
        v.unidadCombo.setSelectedIndex(Math.max(0, Math.min(2, Integer.parseInt(leerConfig("ventana_unidad", "0")))));
        v.unidadCombo.setFocusable(false);
        v.unidadCombo.setToolTipText(t("Unidad de la ventana de búsqueda (hasta 1 semana)", "Unit of the search window (up to 1 week)"));
        v.hoursSpinner.setToolTipText(t("Ventana de búsqueda: hasta 1 semana. Las ventanas largas piden más páginas por jugador (tope: 300 partidas/jugador, se avisa).",
                "Search window: up to 1 week. Long windows fetch more pages per player (cap: 300 games/player, you get a warning)."));
        v.unidadCombo.addActionListener(e -> {
            guardarConfig("ventana_unidad", String.valueOf(v.unidadCombo.getSelectedIndex()));
            int u = v.unidadCombo.getSelectedIndex(), max = u == 0 ? 24 : u == 1 ? 7 : 1;   // el techo, en la unidad elegida: 24 h, 7 días o 1 semana
            SpinnerNumberModel sm = (SpinnerNumberModel) v.hoursSpinner.getModel();
            sm.setMaximum(max);
            if ((int) v.hoursSpinner.getValue() > max) v.hoursSpinner.setValue(max);
        });
        { int u0 = v.unidadCombo.getSelectedIndex(); int max0 = u0 == 0 ? 24 : u0 == 1 ? 7 : 1; ((SpinnerNumberModel) v.hoursSpinner.getModel()).setMaximum(max0); if ((int) v.hoursSpinner.getValue() > max0) v.hoursSpinner.setValue(max0); }
        fila1.add(SpoilerFreeRecs.par(new JLabel(t("Últimas", "Last")), v.hoursSpinner, v.unidadCombo));
        v.partidas.agregarFilaConsulta(fila1);   // modo, rival (sugerencias), mapa, periodo, Buscar partidas, Al azar por ELO, Guess the ELO: ver ui.PartidasView
        // Pestañas de vistas y flechas de atrás/adelante: ver ui.Navegador.construirFilaVistas (fase 3, tanda 4, T4-Z1).
        JPanel filaVistas = v.navegador.construirFilaVistas();

        // El botón «Configuración ▾», todos sus ítems y la esquina «Mi perfil»: ver ui.MenuConfiguracion. Lo
        // que sus ítems necesitan de otras zonas (tema, watchlist, vigilancia, CaptureAge, mi perfil, acerca
        // de, carpeta savegame…) llega por MenuConfiguracion.Anfitrion, implementado aquí con lambdas.
        v.menuConfiguracion = new MenuConfiguracion(temaInicial, v.autoSgItem, CONTROL_SERVICE, new MenuConfiguracion.Anfitrion() {
            @Override public Component padre() { return v; }
            @Override public void estado(String texto) { v.status.setText(texto); }
            @Override public void aplicarTema(String tema) { TemaApp.aplicarTema(tema, v); }
            @Override public void abrirUrl(String url) { AccionesVentana.abrirUrl(v, url); }
            @Override public void mostrarNuevaVersion(String etiqueta) {
                if (v.actualizarBtn != null) { v.actualizarBtn.setText(etiqueta); v.actualizarBtn.setVisible(true); }
            }
            @Override public boolean mostrarEloWatch() { return v.watchlist.mostrarEloWatch; }
            @Override public void fijarMostrarEloWatch(boolean mostrar) { v.watchlist.mostrarEloWatch = mostrar; }
            @Override public void repintarListaJugadores() { v.playersList.repaint(); }
            @Override public void reiniciarVigilancia() {
                if (v.vigilante != null) { v.vigilante.setDelay(tickMs()); v.vigilante.setInitialDelay(tickMs()); v.vigilante.restart(); }
            }
            @Override public void cargarAliasesYNotas() { cargarAliases(); cargarNotas(); }
            @Override public boolean modoTop() { return v.watchlist.modoTop(); }
            @Override public void forzarRecargaTop() { v.watchlist.forzarRecargaTop(); }
            @Override public Path elegirCarpetaSavegame() { return v.partidas.elegirSavegameManual(); }
            @Override public void refiltrarPartidas() { v.partidas.applyFilters(); }
            @Override public boolean hayCarpetaSavegame() { return v.partidas.obtenerSavegame(true) != null; }
            @Override public void mostrarMiPerfil() { v.miPartida.abrirMiPerfil(); }
            @Override public void cambiarCuentaPropia() { v.miPartida.preguntarMiNick(); }
            @Override public void mostrarAcercaDe() { v.showAbout(); }
        });
        JPanel esquina = v.menuConfiguracion.esquina();

        JPanel fila2 = v.partidas.construirFilaNota();   // nota sin-spoilers + «Mostrar resultados»: ver ui.PartidasView

        JPanel filasIzq = new JPanel();
        filasIzq.setLayout(new BoxLayout(filasIzq, BoxLayout.Y_AXIS));
        fila1.setAlignmentX(Component.LEFT_ALIGNMENT); filaVistas.setAlignmentX(Component.LEFT_ALIGNMENT);
        filasIzq.add(fila1);
        filasIzq.add(filaVistas);
        JPanel barraSuperior = new JPanel(new BorderLayout());
        barraSuperior.add(filasIzq, BorderLayout.CENTER);
        JPanel esquinaArriba = new JPanel(new BorderLayout());
        esquinaArriba.add(esquina, BorderLayout.NORTH);
        barraSuperior.add(esquinaArriba, BorderLayout.EAST);
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        barraSuperior.setAlignmentX(Component.LEFT_ALIGNMENT); fila2.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(barraSuperior);
        top.add(fila2);
        v.setMinimumSize(new Dimension(1100, 640));
        return top;
    }

    // La tabla de partidas: construirTablaPartidas() movida a ui.PartidasView.construirTabla().
    // La franja inferior: los botones de descargar/enviar al juego, el menú de
    // carpetas, la firma y donacion, y la barra de estado (progreso, detener,
    // continuar buscando). Devuelve el panel para el centro de la ventana.
    static JPanel construirBarraInferior(SpoilerFreeRecs v) {
        JPanel filasBtns = v.partidas.construirBotonesInferiores();   // dlSel/dlAll/carpetas/enviarSg/todasPerfilBtn: ver ui.PartidasView
        JPanel filaEstado = v.barraEstado.construirFila();   // progreso/detener/continuar, status, firma/café/actualizar: ver ui.BarraEstado
        v.firma = v.barraEstado.firma;
        v.actualizarBtn = v.barraEstado.actualizarBtn;
        v.detenerDescBtn = v.barraEstado.detenerDescBtn;
        v.continuarBtn = v.barraEstado.continuarBtn;

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(filasBtns, BorderLayout.NORTH);
        bottom.add(filaEstado, BorderLayout.SOUTH);
        return bottom;
    }

    // El split principal (Watchlist | resto) y el filtro global de clics: en
    // cualquier fondo sin control, clic izquierdo = quitar la selección de la
    // Watchlist (como el Explorador de Windows).
    static void montarVentana(SpoilerFreeRecs v, JPanel left, JPanel center) {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, center);
        split.setContinuousLayout(true);
        split.setDividerSize(7);
        split.setResizeWeight(0);   // al agrandar la ventana crece la tabla
        left.setMinimumSize(new Dimension(230, 100));
        center.setMinimumSize(new Dimension(420, 100));
        try { split.setDividerLocation(Integer.parseInt(leerConfig("divisor", "355"))); }
        catch (Exception ignored) { split.setDividerLocation(355); }
        if (split.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI bui)
            bui.getDivider().addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) split.setDividerLocation(355);   // vuelta al ancho por defecto
                }
            });
        v.splitPrincipal = split;
        VentanaGuardada.guardarAlCambiar(v, split);   // F11 (1.3): la geometría se guarda al cambiar, no solo al cerrar
        // Como el Explorador, en toda la ventana: clic en cualquier fondo que no sea un control = sin selección.
        // Excepciones: la cabecera de columnas (ordena) y la cabecera «Partidas de:» (sus nombres son clicables).
        // Ver ui.ClicEnFondo: mezcla excepciones de varias vistas, así que se las pedimos por interfaz.
        ClicEnFondo.instalarGlobal(v, new ClicEnFondo.Anfitrion() {
            @Override public JLabel cabeceraWatchlist() { return v.watchlist.cabLabel; }
            @Override public JPanel sujetosPanel() { return v.sujetosPanel; }
            @Override public JTable tablaPartidas() { return v.partidas.table; }
            @Override public JTable tablaDirectos() { return v.directos == null ? null : v.directos.tablaDirectos; }
            @Override public JComponent panelRatings() { return v.ratings == null ? null : v.ratings.panel(); }
            @Override public void ocultarDetalleTechTree() { v.techTree.ocultarDetalle(); }
            @Override public boolean haySeleccionWatchlist() { return !v.playersList.isSelectionEmpty(); }
            @Override public void limpiarSeleccionWatchlist() { v.playersList.clearSelection(); v.partidas.actualizarTextoBuscar(); }
        });
        v.add(split, BorderLayout.CENTER);
    }

    /** Lo que ui.BarraEstado necesita del resto de la ventana: el freno de cancelación real (api.Cancelacion)
     *  y la red (api.Http, java.net) viven aquí porque ui no puede importarlos; «operacionTerminada» reactiva
     *  los botones de ui.PartidasView, que tampoco son suyos. */
    static BarraEstado.Anfitrion barraEstadoAnfitrion(SpoilerFreeRecs v) {
        return new BarraEstado.Anfitrion() {
            @Override public void iniciarOperacion() { stopOperacion = false; hiloOperacion = null; }
            @Override public void marcarOperacionEnCurso(boolean on) { opEnCurso = on; }
            @Override public void operacionTerminada() {
                v.partidas.fetchBtn.setEnabled(true); v.partidas.azarBtn.setEnabled(true); v.partidas.gteBtn.setEnabled(true);
                if (v.partidas.dlSel != null) v.partidas.dlSel.setEnabled(true);
                if (v.partidas.dlAll != null) v.partidas.dlAll.setEnabled(true);
            }
            @Override public void pararOperacion() { stopOperacion = true; }
            @Override public void renovarHttp() { HTTP = nuevoHttp(); }
            @Override public void continuarBuscando() { v.partidas.buscarAleatorias(true); }
            @Override public void abrirTwitch() { AccionesVentana.abrirTwitch(v); }
            @Override public void abrirDonacion() { AccionesVentana.abrirDonacion(v); }
            @Override public void abrirUrl(String url) { AccionesVentana.abrirUrl(v, url); }
            @Override public void espectarPartida(long matchId) { AccionesVentana.espectarPartida(v, matchId); }
        };
    }

    /** Ver el javadoc del campo {@code autoScroll} en la ventana (desplazamiento con la rueda pulsada y vuelta
     *  arriba al cambiar de vista). */
    static AutoScroll autoScroll(SpoilerFreeRecs v) {
        return new AutoScroll(new AutoScroll.Anfitrion() {
            @Override public void atras() { if (!v.perfil.h2hAtrasSiProcede()) v.navegador.volverAtras(); }
            @Override public void adelante() { v.navegador.irAdelante(); }
        });
    }

    /** Ver el javadoc del campo {@code enlaceVivo} en la ventana: el cableado del socket (service.EnlaceVivo);
     *  aquí solo se traducen sus avisos a las vistas concretas. */
    static EnlaceVivo enlaceVivo(SpoilerFreeRecs v) {
        return new EnlaceVivo(VIVO, LIVE, new EnlaceVivo.Vistas() {
            @Override public List<Long> idsWatchlist() {
                List<Long> ids = new ArrayList<>();
                for (int i = 0; i < v.playersModel.size(); i++) ids.add(v.playersModel.get(i).id());
                return ids;
            }
            @Override public List<Long> idsTodosJugadores() {
                List<Long> ids = new ArrayList<>();
                for (Player p : v.todosJugadores) ids.add(p.id());
                return ids;
            }
            @Override public List<Long> idsTopLadder() {
                List<Long> ids = new ArrayList<>();
                for (Player p : v.watchlist.topLadderSnapshot()) ids.add(p.id());
                return ids;
            }
            @Override public Set<Long> idsSocketExtra() { return v.liveNow != null ? v.liveNow.socketExtra : Set.of(); }
            @Override public void liveEvento(long pid, Match m, boolean terminada) { if (v.liveNow != null) v.liveNow.liveEvento(pid, m, terminada); }
            @Override public List<Long> jugadoresLiveNow(long matchId) { return v.liveNow != null ? v.liveNow.jugadoresEnPartida(matchId) : List.of(); }
            @Override public void avisarSiCampana(long pid, Match m) { v.watchlist.avisarSiCampana(pid, m); }
            @Override public void avisarMiPartida(long pid, Match m) { v.watchlist.avisarMiPartida(pid, m); }
            @Override public void avisarTrasCambio() {
                SwingUtilities.invokeLater(() -> { v.watchlist.actualizarIndicadoresVivos(); v.watchlist.refrescarAlturasWatch(); v.playersList.repaint(); v.partidas.table.repaint(); });
            }
            @Override public void refrescarLiveNowSiAbierta() {
                if (v.liveNow != null && v.liveNow.ahoraAbierta) SwingUtilities.invokeLater(() -> v.liveNow.refrescar(true));
            }
        });
    }

    /** Ver el javadoc del campo {@code miPartida} en la ventana: ui.MiPartidaPanel + ui.MiPartidaPresenter +
     *  service.MiPartidaServiceJuego (fase 3, tanda 3), mismos textos, mismo Timer de 2s, mismos nombres de
     *  hilo ("log-juego", "lobby-oficial", "mi-perfil"). */
    static MiPartidaPanel miPartida(SpoilerFreeRecs v) {
        return new MiPartidaPanel(
                new MiPartidaServiceJuego(v.menus::elo1v1Conocido, Config::leerConfig, Config::guardarConfig, Reloj.SISTEMA, Juego::carpetaLogsJuego),
                Tareas.SWING, v, v, new MiPartidaPanel.Anfitrion() {
                    @Override public List<String[]> buscarPerfiles(String nick) { return Servicios.buscarPerfiles(nick); }
                    @Override public void mostrarEstado(String texto) { v.status.setText(texto); }
                    @Override public void sincronizarSocket() { v.enlaceVivo.sincronizarSocket(); }
                });
    }
}
