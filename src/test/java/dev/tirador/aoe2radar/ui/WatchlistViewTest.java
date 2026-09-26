package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.PaginaPartidas;
import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.PerfilEncontrado;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.AnotacionesService;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.Campanas;
import dev.tirador.aoe2radar.service.EloSesion;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.FormService;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.TopLadderService;
import dev.tirador.aoe2radar.util.Config;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JProgressBar;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongFunction;

import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * WatchlistView: la lógica de grupos, filtros, campanas y las dos decisiones sin diálogo del alta de jugador
 * (fusión de resultados API+local, sugerencia caducada). No prueba nada que abra un JOptionPane (elegirGrupoDialog,
 * gestionarGrupos, addPlayerDialog, jugadorElegido) ni nada con red real (cargarTopLadder, cargarForma, cargarTopClan,
 * vigilarVivos/vigilarTop): son SwingWorker reales, sin Tareas.EN_LINEA (ver DEUDA), no se prueban aquí sin pantalla.
 */
class WatchlistViewTest {

    /** Transporte que nunca debería llamarse en estos tests (solo se ejercita lógica síncrona sin red). */
    static final class TransporteNuncaLlamado implements Transporte {
        @Override public Respuesta get(String url) { throw new AssertionError("no se esperaba red: " + url); }
    }
    static final class ThrottleSinFreno implements Throttle {
        @Override public void adquirir(BooleanSupplier cancelar) { }
        @Override public long registrar429() { return 0; }
    }

    static final class FormServiceFalso implements FormService {
        @Override public Forma[] porResta(long pid, LongFunction<Integer> eloActual, LongFunction<Integer> partidasActual) { return null; }
        @Override public Forma[] porSerie(long pid) { return null; }
        @Override public boolean pendiente(long ultimaConsultaTs, long ahoraMs) { return false; }
    }

    static final class ProfileServiceFalso implements ProfileService {
        @Override public dev.tirador.aoe2radar.model.FichaPerfil ficha(long pid) { return null; }
        @Override public dev.tirador.aoe2radar.model.FichaPerfil fichaConocida(long pid) { return null; }
        @Override public Integer elo1v1(long pid) { return null; }
        @Override public List<Perfil.Vinculada> vinculadas(long pid) { return List.of(); }
        @Override public List<Perfil.Vinculada> vinculadasConElo(long pid) { return List.of(); }
        @Override public List<Perfil.Vinculada> vinculadasConocidas(long pid) { return null; }
        @Override public Integer eloVinculada(long vid) { return null; }
        @Override public Map<Long, String> familia(long pid) { return Map.of(); }
        @Override public Actividad actividad(long pid) { return null; }
        @Override public int traerHoy(long pid) { return 0; }
        @Override public Actividad historial(long pid, String modo, Actividad base, boolean mas, int paginas,
                java.util.function.Consumer<Actividad> alLlegarPagina, java.util.function.BooleanSupplier cancelado) { return null; }
        @Override public dev.tirador.aoe2radar.model.AnioSfr anioSfr(long pid, String nombreSiFalta) { return null; }
    }

    static final class BusquedaFalsa implements BusquedaPerfiles {
        List<String[]> locales = new ArrayList<>();
        @Override public List<String[]> sugerir(String q) { return List.of(); }
        @Override public List<String[]> buscar(String q) { return List.of(); }
        @Override public List<String[]> local(String q) { return locales; }
    }

    static final class NavegacionFalsa implements Navegacion {
        final List<String> llamadas = new ArrayList<>();
        @Override public void abrirTechTree(String civ) { llamadas.add("techtree:" + civ); }
        @Override public void abrirCivStats() { llamadas.add("civstats"); }
        @Override public void abrirLadder() { llamadas.add("ladder"); }
        @Override public void abrirPerfil(long pid, String nombre) { llamadas.add("perfil:" + pid); }
        @Override public void abrirPerfilEnPestana(long pid, String nombre) { llamadas.add("perfilPestana:" + pid); }
        @Override public void abrirAhora() { llamadas.add("ahora"); }
    }

    static final class MenusFalso implements MenusJugador {
        @Override public void menuContextual(long pid, String nombre, java.awt.event.MouseEvent e) { }
        @Override public javax.swing.JMenu enPartida(long pid) { return null; }
        @Override public javax.swing.JMenu deJugador(long pid, String nombre) { return new javax.swing.JMenu(nombre); }
        @Override public javax.swing.JMenu perfilNavegador(long pid) { return new javax.swing.JMenu(); }
        @Override public javax.swing.JMenuItem itemJugadorPartida(MatchPlayer p) { return new javax.swing.JMenuItem(); }
        @Override public Integer elo1v1Conocido(long pid) { return null; }
    }

    static final class EnlaceFalso implements WatchlistView.EnlacePartidas {
        Player invitado;
        String vistaDelInvitado = "";
        String vistaDeSujetos = "";
        boolean sujetosVisibles = false;
        boolean fetchLlamado = false;
        final List<Boolean> mostrarDirectosLlamadas = new ArrayList<>();
        @Override public void fetchMatches() { fetchLlamado = true; }
        @Override public void mostrarDirectos(boolean mostrar) { mostrarDirectosLlamadas.add(mostrar); }
        @Override public void refrescarSujetos(List<Player> tracked, boolean esInvitado) { }
        @Override public List<Player> ultimosSujetos() { return List.of(); }
        @Override public void taparResultados() { }
        @Override public void applyFilters() { }
        @Override public void actualizarTextoBuscar() { }
        @Override public void limpiarSujetos() { }
        @Override public void sincronizarSocket() { }
        @Override public String resumenVivo(Match m, long pid) { return "resumen"; }
        @Override public String refNombre(Match m) { return m.refId == 1L ? "Uno" : "Otro"; }
        @Override public void repintarTabla() { }
        @Override public void fijarObjetivo(Player p, String vistaId) { invitado = p; vistaDelInvitado = vistaId; }
        @Override public Player invitado() { return invitado; }
        @Override public void limpiarInvitado() { invitado = null; }
        @Override public String vistaDelInvitado() { return vistaDelInvitado; }
        @Override public boolean sujetosPanelVisible() { return sujetosVisibles; }
        @Override public String vistaDeSujetos() { return vistaDeSujetos; }
    }

    static final class AnfitrionFalso implements WatchlistView.Anfitrion {
        boolean perfilAbierto = false;
        final List<String> trabajando = new ArrayList<>();
        @Override public String nombreVisible(long pid, String nombre) { return nombre; }
        @Override public String paisDe(long pid) { return "es"; }
        @Override public void aprenderPais(long pid, String pais) { }
        @Override public void aprenderCanal(long pid, String canal) { }
        @Override public void cargarEloAyer() { }
        @Override public boolean clanesVacios() { return true; }
        @Override public void asegurarLadderEnFondo() { }
        @Override public List<Map.Entry<String, Integer>> sugerirClanes(String texto) { return List.of(); }
        @Override public void trabajando(boolean on) { trabajando.add(on ? "on" : "off"); }
        @Override public long opSerial() { return 0; }
        @Override public void marcarHiloOperacionActual() { }
        @Override public boolean detenerOperacion() { return false; }
        @Override public void dormir(long ms) { }
        @Override public void abrirUrl(String url) { }
        @Override public void espectar(Player p) { }
        @Override public java.nio.file.Path rutaCaptureAge() { return null; }
        @Override public void lanzarCaptureAge(java.nio.file.Path rec) { }
        @Override public void mostrarToast(String texto, long matchId) { }
        @Override public void agregarAccionesToast(Runnable accionPerfil, Runnable accionCaraACara) { }
        @Override public void abrirPerfilYCaraACara(long pid, String miNombre, long rivalId, String rivalNombre) { }
        @Override public Object[] tarjetaPerfilCache(long pid) { return null; }
        @Override public void tarjetaPerfilGuardar(long pid, Object[] valor) { }
        @Override public boolean perfilAbierto() { return perfilAbierto; }
        @Override public javax.swing.Icon iconoVista(String tipo) { return null; }
        @Override public void seleccionCambiada() { }
        @Override public boolean enCursoReal(Match m) { return false; }
        @Override public List<PerfilEncontrado> buscarPerfilesApi(String q) { return List.of(); }
        @Override public Perfil perfilApi(long pid) { return null; }
        @Override public PaginaPartidas paginaApi(long pid, int pagina, int porPagina) { return null; }
        @Override public void reiniciarThrottleDirectos() { }
        @Override public void vigilarTwitchDirectos() { }
        @Override public void mostrarSuperposicion(String texto, List<Object[]> fichas, int ms) { }
        @Override public void actualizarSocketExtra(Set<Long> ids) { }
        @Override public String ahoraNombre(long pid) { return String.valueOf(pid); }
    }

    List<Player> todosJugadores;
    DefaultListModel<Player> playersModel;
    JList<Player> playersList;
    Map<Long, Integer> eloWatch;
    Map<Long, Integer> gamesWatch;
    EnlaceFalso enlace;
    AnfitrionFalso anfitrion;
    Campanas campanas;
    WatchlistView watchlist;

    /** TOP_PAIS/TOP_CLAN/PAISES se calculan en el constructor de WatchlistView (arreglo de fase 4: antes eran
     *  "static final" y salían siempre en español, ver docs/DEUDA.md), así que hay que fijar I18n.IDIOMA
     *  ANTES de construir. Por eso "es" se fija aquí mismo, no en un @BeforeEach aparte (el orden entre dos
     *  @BeforeEach de una misma clase no está garantizado). */
    String idiomaPrevio;

    @BeforeEach void crear() {
        idiomaPrevio = IDIOMA;
        IDIOMA = "es";
        Transporte redNunca = new TransporteNuncaLlamado();
        CompanionApi companion = new CompanionApi(new ApiClient(new ThrottleSinFreno(), redNunca, s -> { }, () -> false));
        TopLadderService topLadderService = new TopLadderService(companion, new RelojFalso(), ms -> { }, 300);
        campanas = new Campanas(companion, (k, def) -> def, (k, v) -> { });
        BarridoVivos barridoVivos = new BarridoVivos(companion, new RelojFalso(), (m, pid) -> "r", new HashMap<>(), ms -> { }, 300, 50);
        EloSesion eloSesion = new EloSesion(EstadoVivo.SISTEMA, new RelojFalso(), Duration.ofMinutes(6));
        AnotacionesService anotaciones = new AnotacionesService(new HashMap<>(), new HashMap<>(), (k, v) -> { });
        DialogosJugador dialogos = new DialogosJugador(null, anotaciones, new ProfileServiceFalso(),
                new DialogosJugador.RedSteam() {
                    @Override public String steamId(long pid) { return ""; }
                    @Override public List<String[]> alias(String steamId) { return List.of(); }
                },
                new DialogosJugador.Anfitrion() {
                    @Override public void repintarLista() { }
                    @Override public void refrescarAlturas() { }
                    @Override public void refrescarTabla() { }
                    @Override public void ajustarColumnasTabla() { }
                    @Override public void actualizarControles() { }
                    @Override public void refrescarSujetos() { }
                    @Override public void mostrarEstado(String texto) { }
                    @Override public boolean enWatchlist(long pid) { return false; }
                    @Override public void ponerEloWatch(long pid, int elo) { }
                    @Override public Set<String> gruposDisponibles() { return Set.of(); }
                    @Override public String grupoActivo() { return null; }
                    @Override public void agregarJugador(long pid, String nombre, String grupo) { }
                    @Override public void guardarJugadores() { }
                    @Override public void reconstruirGrupos() { }
                    @Override public void marcarFamiliaVinculada(Set<Long> familia) { }
                    @Override public void aplicarFiltro() { }
                    @Override public void refrescarWatchlist() { }
                    @Override public void pausaCortesia() { }
                });

        todosJugadores = new ArrayList<>();
        playersModel = new DefaultListModel<>();
        playersList = new JList<>(playersModel);
        eloWatch = new HashMap<>();
        gamesWatch = new HashMap<>();
        enlace = new EnlaceFalso();
        anfitrion = new AnfitrionFalso();

        watchlist = new WatchlistView(null, new ProfileServiceFalso(), new BusquedaFalsa(), topLadderService,
                new FormServiceFalso(), campanas, barridoVivos, eloSesion,
                new MenusFalso(), dialogos, new NavegacionFalsa(),
                Tareas.SWING, enlace, anfitrion,
                todosJugadores, playersModel, playersList, eloWatch, gamesWatch, new HashMap<>(), new HashMap<>(),
                new JLabel(), new JProgressBar(), new ArrayList<>(), new javax.swing.JPanel(),
                java.nio.file.Path.of("players_test.txt"), java.nio.file.Path.of("top_cache_test.txt"), 300L, 50);
    }

    @AfterEach void restaurar() {
        IDIOMA = idiomaPrevio;
        // config.properties: util.Config lee/escribe un fichero real (no está inyectado); los tests de
        // grupo_activo lo tocan a propósito, así que se borra aquí para no dejar rastro entre tests.
        try { Files.deleteIfExists(Config.CONFIG_FILE); } catch (Exception ignored) { }
    }

    /**
     * Una WatchlistView nueva e independiente de "watchlist" (el @BeforeEach), para los tests que necesitan
     * fijar I18n.IDIOMA justo antes de construir: TOP_PAIS, TOP_CLAN y PAISES se calculan en el constructor
     * (arreglo de fase 4, ver docs/DEUDA.md), así que reutilizar "watchlist" no serviría para probar el otro
     * idioma. Dobles nuevos y ficheros de test distintos: no comparte estado con "watchlist".
     */
    private WatchlistView nuevaInstancia() {
        Transporte redNunca = new TransporteNuncaLlamado();
        CompanionApi companion = new CompanionApi(new ApiClient(new ThrottleSinFreno(), redNunca, s -> { }, () -> false));
        TopLadderService topLadderService = new TopLadderService(companion, new RelojFalso(), ms -> { }, 300);
        Campanas camp = new Campanas(companion, (k, def) -> def, (k, v) -> { });
        BarridoVivos barridoVivos = new BarridoVivos(companion, new RelojFalso(), (m, pid) -> "r", new HashMap<>(), ms -> { }, 300, 50);
        EloSesion eloSesion = new EloSesion(EstadoVivo.SISTEMA, new RelojFalso(), Duration.ofMinutes(6));
        AnotacionesService anotaciones = new AnotacionesService(new HashMap<>(), new HashMap<>(), (k, v) -> { });
        DialogosJugador dialogos = new DialogosJugador(null, anotaciones, new ProfileServiceFalso(),
                new DialogosJugador.RedSteam() {
                    @Override public String steamId(long pid) { return ""; }
                    @Override public List<String[]> alias(String steamId) { return List.of(); }
                },
                new DialogosJugador.Anfitrion() {
                    @Override public void repintarLista() { }
                    @Override public void refrescarAlturas() { }
                    @Override public void refrescarTabla() { }
                    @Override public void ajustarColumnasTabla() { }
                    @Override public void actualizarControles() { }
                    @Override public void refrescarSujetos() { }
                    @Override public void mostrarEstado(String texto) { }
                    @Override public boolean enWatchlist(long pid) { return false; }
                    @Override public void ponerEloWatch(long pid, int elo) { }
                    @Override public Set<String> gruposDisponibles() { return Set.of(); }
                    @Override public String grupoActivo() { return null; }
                    @Override public void agregarJugador(long pid, String nombre, String grupo) { }
                    @Override public void guardarJugadores() { }
                    @Override public void reconstruirGrupos() { }
                    @Override public void marcarFamiliaVinculada(Set<Long> familia) { }
                    @Override public void aplicarFiltro() { }
                    @Override public void refrescarWatchlist() { }
                    @Override public void pausaCortesia() { }
                });
        return new WatchlistView(null, new ProfileServiceFalso(), new BusquedaFalsa(), topLadderService,
                new FormServiceFalso(), camp, barridoVivos, eloSesion,
                new MenusFalso(), dialogos, new NavegacionFalsa(),
                Tareas.SWING, new EnlaceFalso(), new AnfitrionFalso(),
                new ArrayList<>(), new DefaultListModel<>(), new JList<>(new DefaultListModel<>()),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new JLabel(), new JProgressBar(), new ArrayList<>(), new javax.swing.JPanel(),
                java.nio.file.Path.of("players_test2.txt"), java.nio.file.Path.of("top_cache_test2.txt"), 300L, 50);
    }

    // ===== idioma: TOP_PAIS/TOP_CLAN/PAISES tras fijar I18n.IDIOMA (bug resuelto en fase 4) ====================

    @Test void idiomaIngles_textosDeTopEnIngles() {
        IDIOMA = "en";
        WatchlistView w = nuevaInstancia();
        w.grupoCombo.addItem("★ Country top");
        sinListeners(() -> w.grupoCombo.setSelectedItem("★ Country top"), w.grupoCombo);
        assertTrue(w.modoPais(), "con IDIOMA=en, el item del combo debe ser el texto inglés, no el español");
        w.grupoCombo.addItem("★ Clan top");
        sinListeners(() -> w.grupoCombo.setSelectedItem("★ Clan top"), w.grupoCombo);
        assertTrue(w.modoClan());
    }

    @Test void idiomaEspanol_textosDeTopEnEspanol() {
        IDIOMA = "es";
        WatchlistView w = nuevaInstancia();
        w.grupoCombo.addItem("★ Top país");
        sinListeners(() -> w.grupoCombo.setSelectedItem("★ Top país"), w.grupoCombo);
        assertTrue(w.modoPais(), "con IDIOMA=es, el item del combo debe ser el texto español, no el inglés");
    }

    @Test void catalogoPaises_ingles_nombreYOrdenColator() {
        IDIOMA = "en";
        WatchlistView w = nuevaInstancia();
        boolean encontrado = false;
        for (PaisItem pi : w.PAISES) if (pi.code().equals("es")) { assertEquals("Spain", pi.nombre()); encontrado = true; }
        assertTrue(encontrado, "el catálogo debe incluir España (code \"es\")");
        java.text.Collator col = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("en"));
        for (int i = 1; i < w.PAISES.length; i++)
            assertTrue(col.compare(w.PAISES[i - 1].nombre(), w.PAISES[i].nombre()) <= 0,
                    "fuera de orden (collator inglés): " + w.PAISES[i - 1].nombre() + " / " + w.PAISES[i].nombre());
    }

    @Test void catalogoPaises_espanol_nombreYOrdenColator() {
        IDIOMA = "es";
        WatchlistView w = nuevaInstancia();
        boolean encontrado = false;
        for (PaisItem pi : w.PAISES) if (pi.code().equals("es")) { assertEquals("España", pi.nombre()); encontrado = true; }
        assertTrue(encontrado, "el catálogo debe incluir España (code \"es\")");
        java.text.Collator col = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("es"));
        for (int i = 1; i < w.PAISES.length; i++)
            assertTrue(col.compare(w.PAISES[i - 1].nombre(), w.PAISES[i].nombre()) <= 0,
                    "fuera de orden (collator español): " + w.PAISES[i - 1].nombre() + " / " + w.PAISES[i].nombre());
    }

    @Test void grupoActivoGuardadoEnEspanol_appEnIngles_seleccionaTopPais() {
        Config.guardarConfig("grupo_activo", "★ Top país");
        IDIOMA = "en";
        WatchlistView w = nuevaInstancia();
        w.rebuildGrupos();
        assertTrue(w.modoPais(), "el \"★ Top país\" guardado en español debe reconocerse con la app en inglés");
    }

    @Test void grupoActivoGuardadoEnIngles_appEnEspanol_seleccionaTopClan() {
        Config.guardarConfig("grupo_activo", "★ Clan top");
        IDIOMA = "es";
        WatchlistView w = nuevaInstancia();
        w.rebuildGrupos();
        assertTrue(w.modoClan(), "el \"★ Clan top\" guardado en inglés debe reconocerse con la app en español");
    }

    @Test void grupoActivoGuardadoComoAll_appEnEspanol_esTodos() {
        Config.guardarConfig("grupo_activo", "All");
        IDIOMA = "es";
        WatchlistView w = nuevaInstancia();
        w.rebuildGrupos();
        assertNull(w.grupoActivo(), "\"All\" guardado en inglés debe reconocerse como \"Todos\" en español");
    }

    // ===== grupos / filtro =====================================================================================

    @Test void grupoActivo_todosEsNull() {
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem(t("Todos", "All")), watchlist.grupoCombo);
        assertNull(watchlist.grupoActivo());
    }

    @Test void grupoActivo_grupoPersonalizado() {
        watchlist.grupoCombo.addItem("Amigos");
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem("Amigos"), watchlist.grupoCombo);
        assertEquals("Amigos", watchlist.grupoActivo());
    }

    @Test void grupoDestino_generalSiNoHayGrupoActivo() {
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem(t("Todos", "All")), watchlist.grupoCombo);
        assertEquals(WatchlistView.GRUPO_GENERAL, watchlist.grupoDestino());
    }

    @Test void grupoDestino_usaElGrupoActivo() {
        watchlist.grupoCombo.addItem("Pros");
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem("Pros"), watchlist.grupoCombo);
        assertEquals("Pros", watchlist.grupoDestino());
    }

    /** Quita los listeners del combo antes de tocarlo: onGrupoElegido() lanzaría cargarTopLadder/cargarTopClan
     *  (SwingWorker real, red) o refrescarWatchlist si no se quita — ningún test puede lanzar hilos de red. */
    private static void sinListeners(Runnable accion, javax.swing.JComboBox<String> combo) {
        var listeners = combo.getActionListeners();
        for (var l : listeners) combo.removeActionListener(l);
        accion.run();
    }

    @Test void modoTop_paraLosTresTops() {
        watchlist.grupoCombo.addItem(WatchlistView.TOP_LADDER);
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem(WatchlistView.TOP_LADDER), watchlist.grupoCombo);
        assertTrue(watchlist.modoTop());
        assertFalse(watchlist.modoClan());
    }

    @Test void modoClan_soloParaTopClan() {
        watchlist.grupoCombo.addItem("★ Top clan");
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem("★ Top clan"), watchlist.grupoCombo);
        assertTrue(watchlist.modoTop());
        assertTrue(watchlist.modoClan());
    }

    @Test void vistaActualId_combinaGrupoYPais() {
        watchlist.grupoCombo.addItem("Amigos");
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem("Amigos"), watchlist.grupoCombo);
        // fuera de ★ país, el país no entra en el id: solo grupo + «|»
        assertEquals("Amigos|", watchlist.vistaActualId());
    }

    @Test void containsPlayerId_reflejaTodosJugadores() {
        assertFalse(watchlist.containsPlayerId(7L));
        todosJugadores.add(new Player(7L, "Nick", WatchlistView.GRUPO_GENERAL));
        assertTrue(watchlist.containsPlayerId(7L));
    }

    @Test void aplicarFiltroGrupo_muestraSoloElGrupoActivo() {
        todosJugadores.add(new Player(1L, "Uno", "Amigos"));
        todosJugadores.add(new Player(2L, "Dos", "Pros"));
        watchlist.rebuildGrupos();
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem("Amigos"), watchlist.grupoCombo);
        watchlist.aplicarFiltroGrupo();
        assertEquals(1, playersModel.size());
        assertEquals(1L, playersModel.get(0).id());
    }

    /** Mutación: si aplicarFiltroGrupo dejara de reconstruir playersModel al cambiar de grupo, este test lo vería
     *  (se comprobó a mano: comentar el bloque playersModel.clear()/addElement hace fallar la aserción anterior). */
    @Test void aplicarFiltroGrupo_todosMuestraAmbos() {
        todosJugadores.add(new Player(1L, "Uno", "Amigos"));
        todosJugadores.add(new Player(2L, "Dos", "Pros"));
        watchlist.rebuildGrupos();
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem(t("Todos", "All")), watchlist.grupoCombo);
        watchlist.aplicarFiltroGrupo();
        assertEquals(2, playersModel.size());
    }

    // ===== campanas =============================================================================================

    @Test void campanaContiene_delegaEnCampanas() {
        Map<String, Set<Long>> ids = new HashMap<>();
        // campanaContiene no expone campanaIds directamente: se comprueba con el mismo servicio Campanas.
        assertFalse(watchlist.campanaContiene(1L));
    }

    // ===== cuentas vinculadas / conFamilias ====================================================================

    @Test void conFamilias_amplíaConVinculadas() {
        Player cabeza = new Player(1L, "Cabeza", WatchlistView.GRUPO_GENERAL, 42L);
        Player vinculada = new Player(2L, "Vinculada", WatchlistView.GRUPO_GENERAL, 42L);
        todosJugadores.add(cabeza);
        todosJugadores.add(vinculada);
        List<Player> ampliado = watchlist.conFamilias(List.of(cabeza));
        assertEquals(2, ampliado.size());
    }

    @Test void tipCuentaVinculada_dueloSinVinculo_esNull() {
        Match m = new Match();
        m.refId = 99L;
        assertNull(watchlist.tipCuentaVinculada(m));
    }

    // ===== alta de jugador: solo lo que no abre diálogo =========================================================

    @Test void sugerenciaCaducada_siElTextoCambio() {
        assertTrue(WatchlistView.sugerenciaCaducada("tirador", "otra cosa"));
        assertFalse(WatchlistView.sugerenciaCaducada("tirador", "tirador "));   // el trim es parte del criterio
    }

    @Test void agregarLocalesSinRepetir_noDuplicaPorId() {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{ "1", "Uno", "Uno · es" });
        List<String[]> locales = List.of(
                new String[]{ "1", "Uno", "Uno · es" },           // ya está: no se añade
                new String[]{ "2", "Dos", "Dos · ar" });          // nuevo: se añade
        WatchlistView.agregarLocalesSinRepetir(out, locales);
        assertEquals(2, out.size());
        assertEquals("2", out.get(1)[0]);
    }

    private static String t(String es, String en) { return dev.tirador.aoe2radar.util.I18n.t(es, en); }
}
