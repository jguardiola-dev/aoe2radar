package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.service.Campanas;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.LiveService;
import dev.tirador.aoe2radar.util.Reloj;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caracteriza el cromo de navegación (ui.Navegador + AppState) con las vistas de verdad, construidas con los dobles de
 * sus propios tests y sin red: qué pestaña queda subrayada, qué carta se ve y qué guarda el historial.
 *
 * <p>Fija el bug A5 de la revisión de pestañas (1.4.1): al salir del Tech tree hacia Live now, un perfil, Ratings o
 * Civ Stats, {@code cerrarTechTree()} terminaba en {@code mostrarDirectos(false)}, que desmarcaba la pestaña de destino
 * (se subrayaba «Partidas») y metía un «recs» falso en el historial. La regla que fijan estos tests: la pestaña marcada
 * es la vista visible, y el historial solo registra navegaciones reales.
 */
class NavegadorTest {

    @TempDir Path tmp;

    final PartidasViewTest partidasTest = new PartidasViewTest();
    final WatchlistViewTest watchlistTest = new WatchlistViewTest();
    Navegador nav;
    JPanel centro;
    JFrame marco;

    /** El anfitrión del perfil registra el destino en el MISMO historial, como hace la ventana
     *  (CableadoCentro: registrarDestino → Navegador.estado), y al final de abrirPerfil, como en la app. */
    final class AnfitrionPerfil extends PerfilViewTest.AnfitrionFalso {
        @Override public void registrarDestino(long pid, String nombre) {
            nav.estado.registrarDestino(new AppState.Destino("perfil", pid, nombre, null));
        }
    }

    @BeforeEach void montar() throws Exception {
        partidasTest.recs = tmp;
        partidasTest.crear();
        watchlistTest.crear();
        CompanionApi companion = new CompanionApi(new ApiClient(new WatchlistViewTest.ThrottleSinFreno(),
                new WatchlistViewTest.TransporteNuncaLlamado(), s -> { }, () -> false));
        SwingUtilities.invokeAndWait(() -> {
            marco = new JFrame();
            FiltroStats filtro = new FiltroStats("rm_1v1", "30", "*", "*");
            TechTreeView techTree = new TechTreeView(marco, new TechTreeViewTest.TechTreeServiceFake(), new TechTreeViewTest.StatsServiceFake(),
                    filtro, new Listas(marco, b -> { }), null, new TechTreeViewTest.TareasAplazadas(),
                    new TechTreeViewTest.AnfitrionFake(), new TechTreeViewTest.EnlaceCivStatsFake());
            RatingsView ratings = new RatingsView(new RatingsViewTest.RatingsServiceFalso(), new RatingsViewTest.BusquedaFalsa(),
                    new RatingsViewTest.PerfilesFalso(), Tareas.EN_LINEA, new RatingsViewTest.AnfitrionFalso());
            CivStatsView civStats = new CivStatsView(new CivStatsViewTest.StatsFalso(), filtro, new Listas(null, b -> { }),
                    new CivStatsViewTest.NavegacionFalsa(), Tareas.EN_LINEA, null, () -> { });
            PerfilView perfil = new PerfilView(new PerfilViewTest.PerfilesContados(), new RatingsViewTest.RatingsServiceFalso(),
                    new RatingsViewTest.BusquedaFalsa(), new CivStatsViewTest.StatsFalso(), new EstadoVivo(Reloj.SISTEMA), null,
                    new CivStatsViewTest.NavegacionFalsa(), null, new Listas(null, b -> { }), new PerfilPresenterTest.TareasAplazadas(),
                    new AnfitrionPerfil(), new ConcurrentHashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                    new HashMap<>(), new HashMap<>(), pid -> null, Path.of("perfiles-test-inexistente"), pid -> null, 365);
            LiveNowView liveNow = new LiveNowView(new Campanas(companion, (k, def) -> def, (k, v) -> { }, n -> false),
                    new LiveService(companion, Reloj.SISTEMA), List.of(), new ArrayList<>(), new HashMap<>(), new HashMap<>(),
                    civ -> civ, new LiveNowPresenterTest.TareasAplazadas(), new CivStatsViewTest.NavegacionFalsa(),
                    new WatchlistViewTest.MenusFalso(), null, new AnfitrionLive());
            DirectosView directos = new DirectosViewTest().vista;

            centro = new JPanel(new CardLayout());
            for (String c : List.of("recs", "directos", "ahora", "perfil", "ladder", "civstats", "techtree")) {
                JPanel p = new JPanel(); p.setName(c); centro.add(p, c);
            }
            nav = new Navegador(new JLabel(), partidasTest.vista, () -> nav.abrirPerfil(0, ""));
            nav.construirFilaVistas();
            nav.conectarVistas(watchlistTest.watchlist, perfil, liveNow, techTree, ratings, civStats, directos, centro, null);
        });
    }

    @AfterEach void desmontar() throws Exception {
        SwingUtilities.invokeAndWait(() -> { if (marco != null) marco.dispose(); });
        watchlistTest.restaurar();
        partidasTest.cerrar();
    }

    // ----- utilidades ------------------------------------------------------------------------

    /** Ejecuta en el EDT y después vacía la cola: los abrir* dejan actualizarControlesTabla (que es quien
     *  resincroniza «Partidas») en un invokeLater. */
    void enEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** Las pestañas subrayadas ahora mismo (debería ser siempre exactamente una). */
    List<String> marcadas() {
        List<String> out = new ArrayList<>();
        Map<String, JToggleButton> b = new java.util.LinkedHashMap<>();
        b.put("recs", nav.recsBtn); b.put("directos", nav.directosBtn); b.put("ahora", nav.ahoraBtn); b.put("perfil", nav.perfilBtn);
        b.put("ladder", nav.ladderBtn); b.put("civstats", nav.civStatsBtn); b.put("techtree", nav.techTreeBtn);
        b.forEach((k, v) -> { if (v.isSelected()) out.add(k); });
        return out;
    }

    /** La carta del CardLayout que se ve. */
    String cartaVisible() {
        for (Component c : centro.getComponents()) if (c.isVisible()) return c.getName();
        return null;
    }

    /** El historial entero (vista de cada entrada), sin moverlo: se reconstruye con las primitivas públicas
     *  de AppState recorriéndolo atrás y volviendo adelante. */
    List<String> historial() throws Exception {
        List<String> out = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {   // AppState, como todo el cromo, se toca en el EDT
            AppState e = nav.estado;
            int pos = e.historialPos();
            while (e.prepararAtras() != null) { }
            if (e.actual() != null) out.add(e.actual().vista());
            AppState.Destino d;
            while ((d = e.prepararAdelante()) != null) out.add(d.vista());
            for (int k = e.historialPos(); k > pos; k--) e.prepararAtras();
            e.dejarDeNavegar();
            assertEquals(pos, e.historialPos(), "historial() deja la posición donde estaba");
        });
        return out;
    }

    void comprobar(String vista, List<String> historialEsperado) throws Exception {
        Object[] visto = new Object[2];
        SwingUtilities.invokeAndWait(() -> { visto[0] = cartaVisible(); visto[1] = marcadas(); });   // Swing se lee en el EDT
        assertEquals(vista, visto[0], "carta visible");
        assertEquals(List.of(vista), visto[1], "la pestaña subrayada es la vista que se ve");
        assertEquals(historialEsperado, historial(), "historial");
    }

    // ----- salir del Tech tree hacia otra pestaña ------------------------------------------------

    @Test void techTree_aLiveNow_marcaLiveNowYNoApuntaPartidas() throws Exception {
        enEdt(() -> nav.abrirTechTree(null));
        comprobar("techtree", List.of("techtree"));
        enEdt(() -> nav.abrirAhora());
        comprobar("ahora", List.of("techtree", "ahora"));
    }

    @Test void techTree_aRatings_marcaRatingsYNoApuntaPartidas() throws Exception {
        enEdt(() -> nav.abrirTechTree(null));
        enEdt(() -> nav.abrirLadder());
        comprobar("ladder", List.of("techtree", "ladder"));
    }

    @Test void techTree_aCivStats_marcaCivStatsYNoApuntaPartidas() throws Exception {
        enEdt(() -> nav.abrirTechTree(null));
        enEdt(() -> nav.abrirCivStats());
        comprobar("civstats", List.of("techtree", "civstats"));
    }

    @Test void techTree_aPerfil_marcaPerfilYNoApuntaPartidas() throws Exception {
        enEdt(() -> nav.abrirTechTree(null));
        enEdt(() -> nav.abrirPerfil(1L, "Uno"));
        comprobar("perfil", List.of("techtree", "perfil"));
    }

    /** Perfil vacío (pid 0, la pestaña «Perfil» sin nadie seleccionado): no se registra (como desde cualquier otra
     *  pestaña), pero se subraya «Perfil». Es la secuencia de shot_perfil_vacio. */
    @Test void techTree_aPerfilVacio_marcaPerfilYNoTocaElHistorial() throws Exception {
        enEdt(() -> nav.abrirTechTree(null));
        enEdt(() -> nav.perfilBtn.doClick());
        comprobar("perfil", List.of("techtree"));
        assertFalse(nav.atrasBtn.isEnabled(), "no hay a dónde volver: solo se ha pasado por el Tech tree");
    }

    // ----- «←» después de salir del Tech tree ------------------------------------------------

    @Test void techTree_liveNow_atras_vuelveAlTechTreeALaPrimera() throws Exception {
        enEdt(() -> nav.abrirTechTree(null));
        enEdt(() -> nav.abrirAhora());
        enEdt(() -> nav.volverAtras());
        comprobar("techtree", List.of("techtree", "ahora"));
        assertTrue(nav.adelanteBtn.isEnabled());
        enEdt(() -> nav.irAdelante());
        comprobar("ahora", List.of("techtree", "ahora"));
    }

    @Test void techTree_perfil_atras_vuelveAlTechTreeYNoAPartidas() throws Exception {
        enEdt(() -> nav.abrirTechTree(null));
        enEdt(() -> nav.abrirPerfil(1L, "Uno"));
        enEdt(() -> nav.volverAtras());
        comprobar("techtree", List.of("techtree", "perfil"));
    }

    /** La secuencia de shot_perfil_atras: perfil → Tech tree → «←» vuelve al perfil con «Perfil» subrayado. */
    @Test void perfil_techTree_atras_vuelveAlPerfilConSuPestana() throws Exception {
        enEdt(() -> nav.abrirPerfil(1L, "Uno"));
        enEdt(() -> nav.abrirTechTree("aztecs"));
        enEdt(() -> nav.volverAtras());
        comprobar("perfil", List.of("perfil", "techtree"));
        enEdt(() -> nav.irAdelante());
        comprobar("techtree", List.of("perfil", "techtree"));
    }

    // ----- lo que no cambia: cerrar el Tech tree con su «×» lleva a Partidas --------------------

    @Test void cerrarTechTree_llevaAPartidasYLoApunta() throws Exception {
        enEdt(() -> nav.abrirTechTree(null));
        enEdt(() -> nav.cerrarTechTree());
        comprobar("recs", List.of("techtree", "recs"));
    }

    @Test void liveNow_atras_vuelveAPartidas() throws Exception {
        enEdt(() -> nav.mostrarDirectos(false));
        enEdt(() -> nav.abrirAhora());
        comprobar("ahora", List.of("recs", "ahora"));
        enEdt(() -> nav.volverAtras());
        comprobar("recs", List.of("recs", "ahora"));
    }

    @Test void perfil_atras_vuelveALaVistaAnterior() throws Exception {
        enEdt(() -> nav.abrirLadder());
        enEdt(() -> nav.abrirPerfil(1L, "Uno"));
        comprobar("perfil", List.of("ladder", "perfil"));
        enEdt(() -> nav.volverAtras());
        comprobar("ladder", List.of("ladder", "perfil"));
    }

    /** El Live now que usa la ventana tiene más anfitrión que el resto: aquí, todo sin efecto. */
    static final class AnfitrionLive implements LiveNowView.Anfitrion {
        @Override public List<String> clanesGuardados() { return List.of(); }
        @Override public String paisSel() { return null; }
        @Override public String clanBuscado() { return ""; }
        @Override public boolean campanaContiene(long pid) { return false; }
        @Override public void sincronizarSocket() { }
        @Override public boolean socketConectado() { return false; }
        @Override public String nombreVisible(long pid, String nombre) { return nombre; }
        @Override public String paisDe(long pid) { return null; }
        @Override public void ocultarHoverCard(boolean forzar) { }
        @Override public boolean confirmarEspectar(String quien) { return false; }
        @Override public void espectarPartida(long matchId) { }
        @Override public void abrirUrl(String url) { }
        @Override public void descargar(List<Match> partidas, boolean enviarAlJuego, Runnable alTerminar) { }
        @Override public void estadoGlobal(String texto) { }
    }
}
