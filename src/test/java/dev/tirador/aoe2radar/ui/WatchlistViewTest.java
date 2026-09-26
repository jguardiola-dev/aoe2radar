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
        final List<String> espectarYCa = new ArrayList<>();   // el orden de «lanzar CaptureAge» y «espectar»
        @Override public void espectar(Player p) { espectarYCa.add("espectar"); }
        @Override public java.nio.file.Path rutaCaptureAge() { return null; }
        @Override public void lanzarCaptureAge(java.nio.file.Path rec) { espectarYCa.add("ca"); }
        final List<String> avisos = new ArrayList<>();   // toasts, superposiciones y botones del toast, en orden
        @Override public void mostrarToast(String texto, long matchId) { avisos.add("toast:" + texto + "#" + matchId); }
        @Override public void agregarAccionesToast(Runnable accionPerfil, Runnable accionCaraACara) { avisos.add("acciones"); }
        @Override public void abrirPerfilYCaraACara(long pid, String miNombre, long rivalId, String rivalNombre) { }
        @Override public Object[] tarjetaPerfilCache(long pid) { return null; }
        @Override public void tarjetaPerfilGuardar(long pid, Object[] valor) { }
        @Override public boolean perfilAbierto() { return perfilAbierto; }
        @Override public javax.swing.Icon iconoVista(String tipo) { return null; }
        @Override public void seleccionCambiada() { }
        @Override public boolean enCursoReal(Match m) { return false; }
        @Override public Perfil perfilApi(long pid) { return null; }
        @Override public PaginaPartidas paginaApi(long pid, int pagina, int porPagina) { return null; }
        @Override public void reiniciarThrottleDirectos() { }
        @Override public void vigilarTwitchDirectos() { }
        @Override public void mostrarSuperposicion(String texto, List<Object[]> fichas, int ms) {
            StringBuilder f = new StringBuilder();
            for (Object[] x : fichas) f.append(" [").append(x[0]).append("|").append(x[1]).append("|").append(x[2]).append("]");
            avisos.add("superposicion:" + texto + f + " " + ms);
        }
        final List<Set<Long>> socketExtra = new ArrayList<>();
        @Override public void actualizarSocketExtra(Set<Long> ids) { ids.add(-1L); socketExtra.add(ids); }   // como el real: le suma el top de Live now (el set debe ser mutable)
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
    /** La config de las WatchlistView del test, en memoria (grupo_activo, abrir_en, grupos...): antes los tests
     *  escribían config.properties de verdad y en Windows un guardado fallido en silencio daba el flaky de la fila 146. */
    Map<String, String> cfg;

    /** TOP_PAIS/TOP_CLAN/PAISES se calculan en el constructor de WatchlistView (arreglo de fase 4: antes eran
     *  "static final" y salían siempre en español, ver docs/DEUDA.md), así que hay que fijar I18n.IDIOMA
     *  ANTES de construir. Por eso "es" se fija aquí mismo, no en un @BeforeEach aparte (el orden entre dos
     *  @BeforeEach de una misma clase no está garantizado). */
    String idiomaPrevio;

    @BeforeEach void crear() {
        idiomaPrevio = IDIOMA;
        IDIOMA = "es";
        cfg = new HashMap<>();
        Transporte redNunca = new TransporteNuncaLlamado();
        CompanionApi companion = new CompanionApi(new ApiClient(new ThrottleSinFreno(), redNunca, s -> { }, () -> false));
        TopLadderService topLadderService = new TopLadderService(companion, companion, new RelojFalso(), ms -> { }, 300);
        campanas = new Campanas(companion, (k, def) -> def, (k, v) -> { }, nombre -> false);
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
                java.nio.file.Path.of("players_test.txt"), java.nio.file.Path.of("top_cache_test.txt"), 300L, 50,
                (k, def) -> cfg.getOrDefault(k, def), cfg::put);
    }

    @AfterEach void restaurar() {
        IDIOMA = idiomaPrevio;
        // config.properties: grupo_activo/abrir_en/grupos ya van al mapa «cfg», pero las piezas de la vista aún escriben
        // otras claves con util.Config (p. ej. clan_tag en aplicarTopClan): se borra para no dejar rastro entre tests.
        try { Files.deleteIfExists(Config.CONFIG_FILE); } catch (Exception ignored) { }
    }

    /**
     * Una WatchlistView nueva e independiente de "watchlist" (el @BeforeEach), para los tests que necesitan
     * fijar I18n.IDIOMA justo antes de construir: TOP_PAIS, TOP_CLAN y PAISES se calculan en el constructor
     * (arreglo de fase 4, ver docs/DEUDA.md), así que reutilizar "watchlist" no serviría para probar el otro
     * idioma. Dobles nuevos y ficheros de test distintos: no comparte estado con "watchlist".
     */
    private WatchlistView nuevaInstancia() { return nuevaInstancia(new ArrayList<>()); }

    private WatchlistView nuevaInstancia(List<Player> jugadores) { return nuevaInstancia(jugadores, java.nio.file.Path.of("players_test2.txt")); }

    /** Con el players.txt en una carpeta temporal: para los tests que guardan la lista (savePlayers). */
    private WatchlistView nuevaInstancia(List<Player> jugadores, java.nio.file.Path playersFile) {
        Transporte redNunca = new TransporteNuncaLlamado();
        CompanionApi companion = new CompanionApi(new ApiClient(new ThrottleSinFreno(), redNunca, s -> { }, () -> false));
        TopLadderService topLadderService = new TopLadderService(companion, companion, new RelojFalso(), ms -> { }, 300);
        Campanas camp = new Campanas(companion, (k, def) -> def, (k, v) -> { }, nombre -> false);
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
                jugadores, new DefaultListModel<>(), new JList<>(new DefaultListModel<>()),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new JLabel(), new JProgressBar(), new ArrayList<>(), new javax.swing.JPanel(),
                playersFile, java.nio.file.Path.of("top_cache_test2.txt"), 300L, 50,
                (k, def) -> cfg.getOrDefault(k, def), cfg::put);
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
        cfg.put("grupo_activo", "★ Top país");
        IDIOMA = "en";
        WatchlistView w = nuevaInstancia();
        w.rebuildGrupos();
        assertTrue(w.modoPais(), "el \"★ Top país\" guardado en español debe reconocerse con la app en inglés");
    }

    @Test void grupoActivoGuardadoEnIngles_appEnEspanol_seleccionaTopClan() {
        cfg.put("grupo_activo", "★ Clan top");
        IDIOMA = "es";
        WatchlistView w = nuevaInstancia();
        w.rebuildGrupos();
        assertTrue(w.modoClan(), "el \"★ Clan top\" guardado en inglés debe reconocerse con la app en español");
    }

    /**
     * El test anterior guardaba grupo_activo="All" con IDIOMA=es y comprobaba grupoActivo()==null. Eso no
     * probaba nada de verdad: "Todos" YA es la selección por defecto del combo (rebuildGrupos la pone antes de
     * buscar ninguna coincidencia), así que el resultado habría sido el mismo aunque coincideGrupoGuardado no
     * existiera. Aquí, en cambio, el valor por defecto ("Todos") NO es el esperado (modoClan()==false por
     * defecto): solo pasa si la equivalencia bilingüe encontró de verdad el TOP_CLAN.
     */
    @Test void grupoActivoBilingue_cambiaLaSeleccionDeVerdad() {
        cfg.put("grupo_activo", "★ Clan top");
        IDIOMA = "es";
        WatchlistView w = nuevaInstancia();
        assertFalse(w.modoClan(), "antes de rebuildGrupos, el combo está en blanco: de fábrica no es modoClan");
        w.rebuildGrupos();
        assertTrue(w.modoClan(), "\"★ Clan top\" (en) guardado debe cambiar la selección, no quedarse en el \"Todos\" por defecto");
    }

    /**
     * BLOQUEANTE resuelto: rebuildGrupos() probaba primero la equivalencia bilingüe, así que un grupo de
     * usuario llamado igual que "Todos"/"All" (coincidencia por casualidad, en el idioma que no es el activo)
     * nunca se podía seleccionar: "Todos"/TOP_LADDER/TOP_PAIS/TOP_CLAN van antes que los grupos de usuario en
     * el combo, y "Todos" ya "ganaba" la comparación bilingüe antes de llegar al grupo real. Ahora la
     * coincidencia EXACTA (como en la 1.1) se prueba primero, así que el grupo real gana siempre.
     */
    @Test void grupoDeUsuarioLlamadoAll_ganaALaEquivalenciaBilingue() {
        cfg.put("grupo_activo", "All");
        IDIOMA = "es";
        WatchlistView w = nuevaInstancia(new ArrayList<>(List.of(new Player(1L, "Uno", "All"))));
        w.rebuildGrupos();
        assertEquals("All", w.grupoActivo(), "hay un grupo de verdad llamado \"All\": debe ganar a la traducción de \"Todos\"");
        assertFalse(Files.exists(Config.CONFIG_FILE), "fila 146 de DEUDA: la config de los grupos va al mapa del test, no al fichero real");
    }

    @Test void grupoDeUsuarioLlamadoTodos_ganaALaEquivalenciaBilingueEnIngles() {
        cfg.put("grupo_activo", "Todos");
        IDIOMA = "en";
        WatchlistView w = nuevaInstancia(new ArrayList<>(List.of(new Player(1L, "Uno", "Todos"))));
        w.rebuildGrupos();
        assertEquals("Todos", w.grupoActivo(), "hay un grupo de verdad llamado \"Todos\": debe ganar a la traducción de \"All\"");
    }

    /** Caracterización (DEUDA fila 134, antes de unificar los TreeSet de grupos): el conjunto de grupos para fichar es
     *  General + los de los jugadores + los de config, sin distinguir mayúsculas; cuando el mismo nombre llega con
     *  mayúsculas distintas, sobrevive el primero que entra: «General» siempre, y el del jugador antes que el de config. */
    @Test void gruposDisponibles_caracterizacionDeMayusculas() {
        cfg.put("grupos", "amigos,Torneo");
        todosJugadores.add(new Player(1L, "Uno", "Amigos"));
        todosJugadores.add(new Player(2L, "Dos", "general"));
        todosJugadores.add(new Player(3L, "Tres", "PROS"));
        todosJugadores.add(new Player(4L, "Cuatro", "pros"));
        assertEquals(List.of("Amigos", "General", "PROS", "Torneo"), new ArrayList<>(watchlist.gruposDisponibles()));
    }

    // ===== «Abrir en» (Configuración, 1.3): qué vista se elige al arrancar =====================================

    /** Aplica «Abrir en» con los listeners del combo quitados (así no se lanza ninguna carga con red). */
    private dev.tirador.aoe2radar.service.VistaInicial.Eleccion abrirSinCargas(WatchlistView w) {
        w.rebuildGrupos();
        var res = new Object() { dev.tirador.aoe2radar.service.VistaInicial.Eleccion e; };
        sinListeners(() -> res.e = w.abrirVistaInicial(), w.grupoCombo);
        return res.e;
    }

    @Test void abrirEn_sinConfigAbreEnTopLadder() {
        var e = abrirSinCargas(watchlist);
        assertEquals(WatchlistView.TOP_LADDER, watchlist.grupoCombo.getSelectedItem());
        assertFalse(e.buscaAlAbrir());
    }

    @Test void abrirEn_grupoAbreEnEseGrupoYBuscaAlAbrir() {
        todosJugadores.add(new Player(1L, "Uno", "Amigos"));
        cfg.put("abrir_en", "grupo:amigos");
        var e = abrirSinCargas(watchlist);
        assertEquals("Amigos", watchlist.grupoActivo());
        assertTrue(e.buscaAlAbrir(), "en un grupo, «Buscar al abrir» sí aplica");
    }

    @Test void abrirEn_paisYClan() {
        cfg.put("abrir_en", "pais:fr");
        abrirSinCargas(watchlist);
        assertTrue(watchlist.modoPais());
        assertEquals("fr", watchlist.paisSel());

        cfg.put("clanes_guardados", "R1,TdB");
        cfg.put("abrir_en", "clan:tdb");
        abrirSinCargas(watchlist);
        assertTrue(watchlist.modoClan());
        assertEquals("TdB", watchlist.clanBuscado(), "el clan guardado, con sus mayúsculas");
    }

    @Test void abrirEn_todos() {
        cfg.put("abrir_en", "todos");
        var e = abrirSinCargas(watchlist);
        assertEquals("Todos", watchlist.grupoCombo.getSelectedItem());
        assertTrue(e.buscaAlAbrir());
    }

    @Test void abrirEn_loGuardadoYaNoExiste_caeATopLadder() {
        cfg.put("abrir_en", "grupo:Borrado");
        abrirSinCargas(watchlist);
        assertEquals(WatchlistView.TOP_LADDER, watchlist.grupoCombo.getSelectedItem());
        cfg.put("abrir_en", "clan:DK");   // no está entre los guardados
        abrirSinCargas(watchlist);
        assertEquals(WatchlistView.TOP_LADDER, watchlist.grupoCombo.getSelectedItem());
    }

    /** F3 de la revisión 1.3: el arranque ya no pisa grupo_activo con ★ salvo que «Abrir en» sea ★. Con los listeners
     *  puestos (como en la app): elegir el grupo pasa por onGrupoElegido, que guarda ESE grupo. Grupo vacío: sin barrido. */
    @Test void abrirEn_grupoGuardaEseGrupoComoActivo() {
        cfg.put("grupos", "Vacio");
        cfg.put("grupo_activo", "Otro");
        cfg.put("abrir_en", "grupo:Vacio");
        watchlist.rebuildGrupos();
        watchlist.abrirVistaInicial();
        assertEquals("Vacio", cfg.get("grupo_activo"));
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

    /** Dudoso de la revisión 1.3, confirmado: «★ Top clan» salía como grupo activo y grupoDestino() lo daba como
     *  grupo donde fichar. Las tres vistas ★ son iguales: ninguna es un grupo. */
    @Test void grupoActivo_topClanEsNullYFichaEnGeneral() {
        watchlist.grupoCombo.addItem("★ Top clan");
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem("★ Top clan"), watchlist.grupoCombo);
        assertNull(watchlist.grupoActivo());
        assertEquals(WatchlistView.GRUPO_GENERAL, watchlist.grupoDestino());
    }

    @Test void vistaActualId_combinaGrupoYPais() {
        watchlist.grupoCombo.addItem("Amigos");
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem("Amigos"), watchlist.grupoCombo);
        // fuera de ★ país, el país no entra en el id: solo grupo + «|»
        assertEquals("Amigos|", watchlist.vistaActualId());
    }

    /** Caracterización (antes de pasar el modo de vista a WatchlistPresenter): el id interno de la campana de cada
     *  vista, el país elegido y el clan escrito (recortado y en minúsculas). */
    @Test void idVistaCampana_yPaisSel_porVista() {
        watchlist.rebuildGrupos();
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem(t("Todos", "All")), watchlist.grupoCombo);
        assertEquals("grupo|Todos", watchlist.presenter.idVistaCampana());
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem(WatchlistView.TOP_LADDER), watchlist.grupoCombo);
        assertEquals("★ladder", watchlist.presenter.idVistaCampana());
        watchlist.presenter.paisActual = new PaisItem("Francia", "fr");
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem("★ Top país"), watchlist.grupoCombo);
        assertEquals("★pais|fr", watchlist.presenter.idVistaCampana());
        assertEquals("fr", watchlist.paisSel());
        assertEquals("★ Top país|fr", watchlist.vistaActualId());
        watchlist.clanField.setText("  R1 ");
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem("★ Top clan"), watchlist.grupoCombo);
        assertEquals("★clan|r1", watchlist.presenter.idVistaCampana());
        assertEquals("R1", watchlist.clanBuscado());
        assertEquals("★ Top clan · R1", watchlist.presenter.nombreVistaCampana());
    }

    /** Caracterización: elegir un grupo de usuario guarda grupo_activo y filtra la lista a ese grupo; los grupos del
     *  combo son los de usuario, sin «Todos» ni las tres vistas ★. */
    @Test void onGrupoElegido_grupoDeUsuarioGuardaYFiltra() {
        todosJugadores.add(new Player(1L, "Uno", "Amigos"));
        todosJugadores.add(new Player(2L, "Dos", "Pros"));
        watchlist.rebuildGrupos();
        assertEquals(List.of("Amigos", "Pros"), watchlist.gruposDelCombo());
        watchlist.presenter.watchBarridos.add(2L);   // ya barrido: refrescarWatchlist no lanza nada (el test no tiene red)
        watchlist.grupoCombo.setSelectedItem("Pros");   // con listeners: onGrupoElegido
        assertEquals("Pros", cfg.get("grupo_activo"));
        assertEquals(1, playersModel.size());
        assertEquals(2L, playersModel.get(0).id());
    }

    // ===== operaciones de la lista (caracterización antes de pasarlas a WatchlistPresenter) ==========================

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp;

    /** Una Watchlist con Uno y Dos en «Amigos» y Tres en «Pros», ya barridos (sin red), en «Todos», guardando en tmp. */
    private WatchlistView conTresJugadores() {
        List<Player> js = new ArrayList<>(List.of(new Player(1L, "Uno", "Amigos"), new Player(2L, "Dos", "Amigos"), new Player(3L, "Tres", "Pros")));
        WatchlistView w = nuevaInstancia(js, tmp.resolve("players.txt"));
        w.presenter.watchBarridos.addAll(List.of(1L, 2L, 3L));
        w.rebuildGrupos();
        sinListeners(() -> w.grupoCombo.setSelectedItem("Todos"), w.grupoCombo);
        w.aplicarFiltroGrupo();
        return w;
    }

    @Test void moverJugador_mueveGuardaYAvisa() throws Exception {
        WatchlistView w = conTresJugadores();
        w.moverJugador(new Player(1L, "Uno", "Amigos"), "Nuevo");
        assertEquals("Nuevo", w.grupoDeJugador(1L));
        assertTrue(w.gruposDelCombo().contains("Nuevo"), "el combo se reconstruye");
        assertTrue(Files.readString(tmp.resolve("players.txt")).contains("Nuevo"), "se guarda en players.txt");
        assertEquals("Uno movido al grupo «Nuevo».", w.status.getText());
    }

    @Test void renombrarYBorrarGrupo() {
        WatchlistView w = conTresJugadores();
        w.presenter.renombrarGrupo("Amigos", "Colegas");
        assertEquals("Colegas", w.grupoDeJugador(2L));
        assertEquals(List.of("Colegas", "Pros"), w.gruposDelCombo());
        w.presenter.borrarGrupo("Pros");
        assertEquals(WatchlistView.GRUPO_GENERAL, w.grupoDeJugador(3L));
        assertFalse(w.gruposDelCombo().contains("Pros"));
    }

    @Test void moverVarios_yQuitar() {
        WatchlistView w = conTresJugadores();
        w.presenter.moverVarios(List.of(new Player(1L, "Uno", "Amigos"), new Player(2L, "Dos", "Amigos"), new Player(1L, "Uno", "Amigos")), "Pros");
        assertEquals("Pros", w.grupoDeJugador(2L));
        assertEquals("2 jugadores movidos a «Pros».", w.status.getText(), "cuenta ids distintos");
        w.presenter.quitarDeWatchlist(3L);
        assertFalse(w.containsPlayerId(3L));
        assertEquals(2, w.playersModel.size(), "la lista visible se rehace");
        assertEquals("Quitado de tu watchlist.", w.status.getText());
    }

    @Test void ficharDesdeTop_soloSiNoEstaba() {
        WatchlistView w = conTresJugadores();
        w.status.setText("antes");
        w.ficharDesdeTop(new Player(1L, "Uno", "Amigos"), "Pros");
        assertEquals("antes", w.status.getText(), "ya seguido: no hace nada");
        w.ficharDesdeTop(new Player(9L, "Nueve", WatchlistView.TOP_LADDER), "Pros");
        assertEquals("Pros", w.grupoDeJugador(9L));
        assertEquals("Nueve añadido a «Pros» de tu watchlist.", w.status.getText());
    }

    /** Caracterización (antes de pasar filtro y conteos a WatchlistPresenter): título, resumen y chip cuentan a los que
     *  juegan en toda la lista y en el grupo elegido; el combo marca los grupos con alguien jugando. */
    @Test void indicadoresVivos_cuentanEnLaListaYEnElGrupo() {
        WatchlistView w = conTresJugadores();
        sinListeners(() -> w.grupoCombo.setSelectedItem("Amigos"), w.grupoCombo);
        w.aplicarFiltroGrupo();
        try {
            EstadoVivo.SISTEMA.marcarJugando(1L, 111L);
            EstadoVivo.SISTEMA.marcarJugando(3L, 333L);
            w.actualizarIndicadoresVivos();
            assertEquals("Watchlist — 2 jugando", w.tituloWatch.getTitle());
            assertEquals("2 jugadores · 1 jugando", w.resumenWatch.getText());
            assertEquals("● Jugando (1)", w.soloVivosBtn.getText());
            assertTrue(w.presenter.grupoTieneVivo("amigos"));
            assertTrue(w.presenter.grupoTieneVivo(null));
            EstadoVivo.SISTEMA.marcarFuera(1L);
            assertFalse(w.presenter.grupoTieneVivo("Amigos"));
            w.actualizarIndicadoresVivos();
            assertEquals("● Jugando", w.soloVivosBtn.getText());
        } finally { EstadoVivo.SISTEMA.marcarFuera(1L); EstadoVivo.SISTEMA.marcarFuera(3L); }
    }

    /** Caracterización: la cuenta hermana de una familia dice de quién es (la de más ELO); vincular guarda y refiltra. */
    @Test void familias_tipCuentaVinculadaYMarcarVinculo() {
        todosJugadores.add(new Player(1L, "Uno", "G", 42L));
        todosJugadores.add(new Player(2L, "Otro", "G", 42L));
        todosJugadores.add(new Player(3L, "Tres", "G"));
        eloWatch.put(1L, 1500); eloWatch.put(2L, 1800);
        Match m = new Match();
        m.refId = 1L;   // EnlaceFalso.refNombre: «Uno»
        assertEquals("Cuenta vinculada de Otro", watchlist.tipCuentaVinculada(m));
        m.refId = 2L;   // «Otro»: la matriz es ella misma
        assertNull(watchlist.tipCuentaVinculada(m));
        watchlist.marcarVinculo(Set.of(2L, 3L));
        assertEquals(todosJugadores.get(1).vinculo(), todosJugadores.get(2).vinculo(), "Tres entra en la familia");
        assertEquals((Character) 'P', watchlist.presenter.marcaFila.get(2L), "marcarVinculo refiltra: Otro encabeza (más ELO)");
        assertEquals(2, watchlist.conFamilias(List.of(todosJugadores.get(1))).size(), "la cabeza arrastra a su familia");
        watchlist.presenter.vinculosExpandidos.add(2L);
        watchlist.aplicarFiltroGrupo();
        assertEquals((Character) 'H', watchlist.presenter.marcaFila.get(3L));
        assertEquals(1, watchlist.conFamilias(List.of(todosJugadores.get(2))).size(), "una hija elegida va sola");
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

    // ===== avisarSiCampana: nombre y texto se construyen en el EDT (fila 106 de DEUDA) ==========================

    /**
     * avisarSiCampana llega desde un hilo de fondo (vigilarTop/vigilarVivos). nombreDe recorre todosJugadores,
     * que el EDT lee y escribe (rebuildGrupos, altas, bajas...): antes del arreglo, ese recorrido se hacía en el
     * propio hilo de fondo, ANTES del invokeLater, así que podía toparse con la lista a medio modificar. El
     * hook de más abajo detecta desde qué hilo se recorre todosJugadores.
     */
    @Test void avisarSiCampana_leeTodosJugadoresSoloEnElEdt() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean invocado = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean fueraDelEdt = new java.util.concurrent.atomic.AtomicBoolean(false);
        List<Player> jugadoresVigilados = new ArrayList<>() {
            @Override public java.util.Iterator<Player> iterator() {
                invocado.set(true);
                if (!javax.swing.SwingUtilities.isEventDispatchThread()) fueraDelEdt.set(true);
                return super.iterator();
            }
        };
        jugadoresVigilados.add(new Player(1L, "Ana", "G"));
        WatchlistView w = nuevaInstancia(jugadoresVigilados);

        // campanaIds es privado: se rellena por reflexión con una campana ya calculada para el grupo "G",
        // sin pasar por refrescarCampanas (que lanza un hilo de red que aquí no hace falta).
        java.lang.reflect.Field campo = WatchlistView.class.getDeclaredField("campanaIds");
        campo.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Set<Long>> campanaIds = (Map<String, Set<Long>>) campo.get(w);
        campanaIds.put("grupo|G", Set.of(1L));

        Match m = new Match();
        m.id = 555L;

        Thread hiloDeFondo = new Thread(() -> w.avisarSiCampana(1L, m), "vigilar-test");
        hiloDeFondo.start();
        hiloDeFondo.join(2000);

        assertFalse(fueraDelEdt.get(), "todosJugadores no debe recorrerse fuera del EDT");

        // deja correr en el EDT lo que avisarSiCampana haya encolado con invokeLater
        javax.swing.SwingUtilities.invokeAndWait(() -> { });

        assertTrue(invocado.get(), "el hook debía dispararse: avisarSiCampana sí llega a leer todosJugadores");
        assertFalse(fueraDelEdt.get(), "todosJugadores no debe recorrerse fuera del EDT (tampoco tras el invokeLater)");
    }

    /** Caracterización (antes de pasar las campanas a WatchlistPresenter): el texto del aviso de campana (una vez por
     *  partida) y el de «Tu partida ha empezado» (superposición, toast y sus dos accesos), ya en el EDT. */
    @Test void avisos_campanaYMiPartida() throws Exception {
        todosJugadores.add(new Player(1L, "Ana", "G"));
        java.lang.reflect.Field campo = WatchlistView.class.getDeclaredField("campanaIds");
        campo.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Set<Long>> campanaIds = (Map<String, Set<Long>>) campo.get(watchlist);
        campanaIds.put("grupo|G", Set.of(1L));
        assertTrue(watchlist.campanaContiene(1L));
        assertFalse(watchlist.campanaContiene(2L));
        Match m = new Match();
        m.id = 77L;
        watchlist.avisarSiCampana(1L, m);
        watchlist.avisarSiCampana(1L, m);   // la misma partida: un solo aviso
        watchlist.avisarSiCampana(2L, m);   // sin campana
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        assertEquals(List.of("toast:● Ana ha empezado una partida · resumen#77"), anfitrion.avisos);

        anfitrion.avisos.clear();
        Map<String, String> cfgC = new HashMap<>(Map.of("mi_pid", "5"));
        Campanas conMiPid = new Campanas(new CompanionApi(new ApiClient(new ThrottleSinFreno(), new TransporteNuncaLlamado(), s -> { }, () -> false)),
                (k, def) -> cfgC.getOrDefault(k, def), cfgC::put, nombre -> false);
        java.lang.reflect.Field campoCampanas = WatchlistView.class.getDeclaredField("campanas");
        campoCampanas.setAccessible(true);
        campoCampanas.set(watchlist, conMiPid);
        Match mm = new Match();
        mm.id = 88L; mm.map = "Arena";
        MatchPlayer yo = new MatchPlayer(); yo.id = 5L; yo.name = "Yo"; yo.team = 1;
        MatchPlayer rival = new MatchPlayer(); rival.id = 6L; rival.name = "Riv"; rival.team = 2; rival.rating = 1500; rival.civ = "Franks";
        mm.players.add(yo); mm.players.add(rival);
        watchlist.avisarMiPartida(6L, mm);   // no soy yo
        watchlist.avisarMiPartida(5L, mm);
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        String modo = dev.tirador.aoe2radar.service.ReglasPartida.modoCorto(mm);
        assertEquals(List.of("superposicion:● Tu partida empieza · Arena · " + modo + " [6|Riv  ·  Franks|1500] 60000",
                "toast:● Tu partida ha empezado · Arena · " + modo + " · vs Riv (1500) Franks#88", "acciones"), anfitrion.avisos);
    }

    // ===== refrescarCampanas: campanaIds nunca se ve vacío a medias (fila 107 de DEUDA) =========================

    /**
     * Mismo patrón que EloNocturnoTest.elMapaDeAyerNuncaSeVeVacioMientrasSeRefresca: muchas claves (20.000, para
     * que el retainAll+putAll tarde lo bastante) y un hilo lector espiando campanaIds mientras se refresca dos
     * veces con las MISMAS claves. Con clear()+putAll() el mapa se ve vacío entre medias; con retainAll+putAll,
     * las claves que siguen existiendo nunca desaparecen.
     */
    /** EnlacePartidas mínimo que solo avisa (por el latch) cuando refrescarCampanas termina su hilo de fondo. */
    static final class EnlaceConLatch implements WatchlistView.EnlacePartidas {
        final java.util.concurrent.CountDownLatch listo;
        EnlaceConLatch(java.util.concurrent.CountDownLatch listo) { this.listo = listo; }
        @Override public void fetchMatches() { }
        @Override public void mostrarDirectos(boolean mostrar) { }
        @Override public void refrescarSujetos(List<Player> tracked, boolean esInvitado) { }
        @Override public List<Player> ultimosSujetos() { return List.of(); }
        @Override public void taparResultados() { }
        @Override public void applyFilters() { }
        @Override public void actualizarTextoBuscar() { }
        @Override public void limpiarSujetos() { }
        @Override public void sincronizarSocket() { listo.countDown(); }
        @Override public String resumenVivo(Match m, long pid) { return null; }
        @Override public String refNombre(Match m) { return ""; }
        @Override public void repintarTabla() { }
        @Override public void fijarObjetivo(Player p, String vistaId) { }
        @Override public Player invitado() { return null; }
        @Override public void limpiarInvitado() { }
        @Override public String vistaDelInvitado() { return ""; }
        @Override public boolean sujetosPanelVisible() { return false; }
        @Override public String vistaDeSujetos() { return ""; }
    }

    @Test void refrescarCampanas_noDejaCampanaIdsVacioAMedias() throws Exception {
        int n = 20_000;
        Set<String> vistas = new java.util.LinkedHashSet<>();
        for (int i = 0; i < n; i++) vistas.add("grupo|G" + i);
        Map<String, String> cfg = new HashMap<>();
        Campanas campanasGrande = new Campanas(
                new CompanionApi(new ApiClient(new ThrottleSinFreno(), new TransporteNuncaLlamado(), s -> { }, () -> false)),
                (k, def) -> cfg.getOrDefault(k, def), cfg::put, nombre -> false);
        campanasGrande.guardarCampanas(vistas);   // config "campanas" con las 20.000 vistas, en un solo guardado

        WatchlistView w = nuevaInstancia(new ArrayList<>());
        java.lang.reflect.Field campoCampanas = WatchlistView.class.getDeclaredField("campanas");
        campoCampanas.setAccessible(true);
        campoCampanas.set(w, campanasGrande);
        java.lang.reflect.Field campoEnlace = WatchlistView.class.getDeclaredField("enlacePartidas");
        campoEnlace.setAccessible(true);
        java.lang.reflect.Field campoCampanaIds = WatchlistView.class.getDeclaredField("campanaIds");
        campoCampanaIds.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Set<Long>> campanaIds = (Map<String, Set<Long>>) campoCampanaIds.get(w);

        // primer refresco: parte de vacío, no es lo que se prueba (solo deja las 20.000 claves puestas)
        java.util.concurrent.CountDownLatch listo1 = new java.util.concurrent.CountDownLatch(1);
        campoEnlace.set(w, new EnlaceConLatch(listo1));
        w.refrescarCampanas();
        assertTrue(listo1.await(20, java.util.concurrent.TimeUnit.SECONDS), "el primer refresco no terminó a tiempo");
        assertEquals(n, campanaIds.size());

        // segundo refresco: MISMAS 20.000 claves. Aquí es donde clear()+putAll() dejaría el mapa vacío a medias
        // mientras otro hilo lo lee (p. ej. avisarSiCampana/campanaContiene en el hilo del socket).
        java.util.concurrent.CountDownLatch listo2 = new java.util.concurrent.CountDownLatch(1);
        campoEnlace.set(w, new EnlaceConLatch(listo2));
        java.util.concurrent.atomic.AtomicBoolean vistoVacio = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean sigueLeyendo = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread lector = new Thread(() -> {
            while (sigueLeyendo.get()) if (campanaIds.isEmpty()) { vistoVacio.set(true); break; }
        });
        lector.setDaemon(true);
        lector.start();
        try {
            w.refrescarCampanas();
            assertTrue(listo2.await(20, java.util.concurrent.TimeUnit.SECONDS), "el segundo refresco no terminó a tiempo");
        } finally {
            sigueLeyendo.set(false);
            lector.join(5000);
        }

        assertFalse(vistoVacio.get(), "quien lee campanaIds mientras se refresca (mismas claves) nunca debe verlo vacío");
        assertEquals(n, campanaIds.size());
    }

    // ===== alta de jugador: solo lo que no abre diálogo =========================================================

    /** F11 de la revisión 1.3: al apagar la última campana, el socket debe dejar de vigilar sus jugadores (antes
     *  campanaIds se vaciaba pero actualizarSocketExtra no se llamaba y el socket seguía con los ids viejos). */
    @Test void refrescarCampanas_sinCampanasLimpiaElExtraDelSocket() {
        // la config de "campanas" en este test es (k, def) -> def: sin campanas
        watchlist.refrescarCampanas();
        assertEquals(1, anfitrion.socketExtra.size(), "sin campanas también hay que avisar al socket");
        assertEquals(Set.of(-1L), anfitrion.socketExtra.get(0), "solo lo que añade el anfitrión (Live now), nada de las campanas");
    }

    /** F4 de la revisión 1.3 (= vivo F4): el primer barrido de un grupo, con el ELO del snapshot nocturno, trataba
     *  su vivo null como «no juega»: apagaba el punto que ya había puesto el socket y apuntaba un fin falso. */
    @Test void aplicarRefresco_delSnapshotNoApagaAlQueJuega() {
        long pid = 987_654_321L;
        EstadoVivo.SISTEMA.marcarJugando(pid, 555L);   // el socket ya lo vio en partida
        try {
            watchlist.trabajos.aplicarRefresco(new BarridoVivos.Refresco(pid, null, null, 1500, 42));
            assertTrue(EstadoVivo.SISTEMA.jugando(pid), "el snapshot no sabe si juega: el punto del socket se queda");
            assertNull(EstadoVivo.SISTEMA.finMs(pid), "ni fin de partida falso");
            assertEquals(1500, eloWatch.get(pid), "el ELO del snapshot sí se aplica");

            watchlist.trabajos.aplicarRefresco(new BarridoVivos.Refresco(pid, null, null, 1510, null));
            assertFalse(EstadoVivo.SISTEMA.jugando(pid), "el de la API sí manda: vivo null = fuera");
        } finally { EstadoVivo.SISTEMA.marcarFuera(pid); }
    }

    /** F5 de la revisión 1.3: en un grupo, el barrido pone en eloWatch el ELO de anoche (snapshot) y «Ver forma» se lo
     *  restaba al de anoche: diff 0, «sin partidas». Con el ELO del snapshot, la resta no tiene «ELO actual» y la forma
     *  va por la vía exacta (porSerie). Un ELO fresco (API, leaderboard) vuelve a valer para la resta. */
    @Test void eloParaResta_elDelSnapshotNoValeComoEloActual() {
        long pid = 42L;
        watchlist.trabajos.aplicarRefresco(new BarridoVivos.Refresco(pid, null, null, 1500, 30));   // del snapshot
        assertEquals(1500, eloWatch.get(pid), "la lista sigue enseñando el ELO del snapshot");
        assertNull(WatchlistPresenter.eloParaResta(pid, eloWatch, watchlist.presenter.eloDelSnapshot), "restar anoche de anoche da 0: no vale");

        watchlist.trabajos.aplicarRefresco(new BarridoVivos.Refresco(pid, null, null, 1523, null));   // de la API
        assertEquals(1523, WatchlistPresenter.eloParaResta(pid, eloWatch, watchlist.presenter.eloDelSnapshot), "un ELO fresco sí vale para la resta");
        EstadoVivo.SISTEMA.marcarFuera(pid);
    }

    /** F1 de la revisión 1.3: ★ Top clan cargaba sus miembros en topLadder sin cambiar la firma («global»): volver a
     *  ★ Top ladder antes de 10 min daba por fresca la lista del clan y enseñaba a sus miembros como el top. */
    @Test void aplicarTopClan_dejaSuFirmaYElLadderSeRecarga() {
        watchlist.rebuildGrupos();
        watchlist.presenter.top.topLadder.add(new Player(1L, "Top1", WatchlistView.TOP_LADDER));
        watchlist.presenter.top.topFirma = "global";
        watchlist.presenter.top.topCargado = new RelojFalso().ahora;   // el ladder se cargó ahora mismo (reloj del TopLadderService del test)
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem("★ Top clan"), watchlist.grupoCombo);

        watchlist.presenter.top.aplicarTopClan("R1", new TopLadderService.ResultadoClan(null, List.of(new TopLadderService.FilaClan(9L, "Nueve", 1800))));

        assertEquals(List.of(9L), watchlist.presenter.top.topLadder.stream().map(Player::id).toList());
        assertFalse(watchlist.presenter.top.servicio.topFresco(false, "global", watchlist.presenter.top.topFirma, !watchlist.presenter.top.topLadder.isEmpty(), watchlist.presenter.top.topCargado),
                "con la lista del clan puesta, volver a ★ Top ladder tiene que recargar");
    }

    /** Caracterización (antes de pasar el top a WatchlistPresenter): con el top fresco (misma firma, menos de 10 min),
     *  cargarTopLadder no recarga: pinta lo que hay y solo intenta vigilar (aquí, dentro del anti-solape de 45 s). */
    @Test void cargarTopLadder_topFrescoNoRecarga() {
        watchlist.rebuildGrupos();
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem(WatchlistView.TOP_LADDER), watchlist.grupoCombo);
        watchlist.presenter.top.topLadder.add(new Player(1L, "Top1", WatchlistView.TOP_LADDER));
        watchlist.presenter.top.topFirma = "global";
        watchlist.presenter.top.topCargado = new RelojFalso().ahora;
        watchlist.presenter.top.ultimoTopMs = System.currentTimeMillis();   // vigilarTop: dentro de los 45 s, no sale a la red
        assertFalse(watchlist.topLadderVacio());
        assertEquals(new RelojFalso().ahora, watchlist.topCargadoMs());
        watchlist.cargarTopLadder(false);
        assertFalse(watchlist.presenter.top.cargandoTop, "fresco: sin carga");
        assertFalse(watchlist.presenter.top.vigilandoTop, "anti-solape: sin vigilancia");
        assertEquals(List.of(1L), watchlist.topLadderSnapshot().stream().map(Player::id).toList());
        assertEquals(1, playersModel.size(), "pinta el top que ya había");
    }

    @Test void cargarTopClan_sinTagLoPide() {
        watchlist.clanField.setText("  ");
        watchlist.cargarTopClan();
        assertEquals("Escribe el tag del clan (p. ej. R1).", watchlist.status.getText());
    }

    @Test void aplicarTopClan_siElUsuarioYaSalioDelClanNoPinta() {
        watchlist.rebuildGrupos();
        watchlist.presenter.top.topLadder.add(new Player(1L, "Top1", WatchlistView.TOP_LADDER));
        watchlist.presenter.top.topFirma = "global";
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem(WatchlistView.TOP_LADDER), watchlist.grupoCombo);

        watchlist.presenter.top.aplicarTopClan("R1", new TopLadderService.ResultadoClan(null, List.of(new TopLadderService.FilaClan(9L, "Nueve", 1800))));

        assertEquals(List.of(1L), watchlist.presenter.top.topLadder.stream().map(Player::id).toList(), "el top ladder ya pintado se queda");
        assertEquals("global", watchlist.presenter.top.topFirma);
    }

    /** F12 de la revisión 1.3: la racha salía con «V»/«D» también con la app en inglés. */
    @Test void formaLarga_rachaTraducida() {
        Forma f = new Forma(25, 3, 0, 3, true, 3);
        IDIOMA = "en";
        assertTrue(WatchlistPresenter.formaLarga(f).endsWith("3W"), WatchlistPresenter.formaLarga(f));
        IDIOMA = "es";
        assertTrue(WatchlistPresenter.formaLarga(f).endsWith("3V"), WatchlistPresenter.formaLarga(f));
    }

    /** Caracterización (antes de pasar la forma a WatchlistPresenter): el tooltip de la celda Forma según lo
     *  consultado, la ventana (24 h / 7 d) y, solo en los tops, la racha del ladder y las últimas 10. */
    @Test void tipForma_textosSegunVentanaYModo() {
        watchlist.rebuildGrupos();
        sinListeners(() -> watchlist.grupoCombo.setSelectedItem("Todos"), watchlist.grupoCombo);
        long pid = 424_242L;
        assertEquals("Últimas 24 h: sin consultar (selecciónalo y pulsa Ver forma)", watchlist.tipForma(pid));
        watchlist.presenter.forma24.put(pid, new Forma(0, 0, 0, 0, true, 0));
        assertEquals("Últimas 24 h: sin partidas 1v1", watchlist.tipForma(pid));
        watchlist.presenter.forma24.put(pid, new Forma(-12, 1, 3, 3, false, 4));
        assertEquals("Últimas 24 h: 1-3 · -12 · 3 derrotas seguidas", watchlist.tipForma(pid));
        watchlist.presenter.forma7d.put(pid, new Forma(30, 5, 2, 1, true, 7));
        watchlist.presenter.ventanaForma = 24 * 7;
        assertEquals("Últimos 7 días: 5-2 · +30", watchlist.tipForma(pid), "racha 1: no se cuenta; fuera de los tops no mira la del ladder");
        WatchlistPresenter.TOP_STREAK.put(pid, 4);
        WatchlistPresenter.TOP_LAST10.put(pid, new int[]{ 7, 3 });
        try {
            assertEquals("Últimos 7 días: 5-2 · +30", watchlist.tipForma(pid), "fuera de los tops, ni racha ni últimas 10 del ladder");
            sinListeners(() -> watchlist.grupoCombo.setSelectedItem(WatchlistView.TOP_LADDER), watchlist.grupoCombo);
            assertEquals("Últimos 7 días: 5-2 · +30 · 4 victorias seguidas · últimas 10: 7-3", watchlist.tipForma(pid));
        } finally { WatchlistPresenter.TOP_STREAK.remove(pid); WatchlistPresenter.TOP_LAST10.remove(pid); }
    }

    /** F12 de la revisión 1.3: el aviso de campana enseñaba el id interno («★pais es», «grupo Amigos»). */
    @Test void nombreVistaCampana_esElNombreQueVeElUsuario() {
        IDIOMA = "en";
        WatchlistView w = nuevaInstancia();
        w.grupoCombo.addItem("Amigos");
        w.grupoCombo.addItem("★ Country top");
        sinListeners(() -> w.grupoCombo.setSelectedItem("Amigos"), w.grupoCombo);
        assertEquals("Amigos", w.presenter.nombreVistaCampana(), "ni «grupo» ni «|»: el nombre del grupo, tal cual");
        w.presenter.paisActual = new PaisItem("Spain", "es");
        sinListeners(() -> w.grupoCombo.setSelectedItem("★ Country top"), w.grupoCombo);
        assertEquals("★ Country top · Spain", w.presenter.nombreVistaCampana());
    }

    /** Revisión 1.3 (dudoso confirmado): con «Usar CaptureAge», espectar ya lanza CA; el menú no debe lanzarlo otra vez. */
    @Test void espectarConCaptureAge_unSoloLanzamiento() {
        Player p = new Player(5L, "Cinco", WatchlistView.GRUPO_GENERAL);
        watchlist.menuLista.espectarConCaptureAge(p);
        assertEquals(List.of("ca", "espectar"), anfitrion.espectarYCa, "sin la casilla, lo lanza el menú, como siempre");

        anfitrion.espectarYCa.clear();
        cfg.put("usar_ca", "true");
        watchlist.menuLista.espectarConCaptureAge(p);
        assertEquals(List.of("espectar"), anfitrion.espectarYCa, "con la casilla, solo lo lanza espectar (tras verificar)");
    }

    /** Revisor 1.3: «Guardar clan» escribía clanes_guardados con util.Config y la vista lo lee de la config inyectada. */
    @Test void guardarClan_escribeEnLaMismaConfigQueLee() {
        watchlist.clanField.setText("R1");
        watchlist.clanEstrella.doClick();
        assertEquals("R1", cfg.get("clanes_guardados"));
        assertEquals(List.of("R1"), watchlist.clanesGuardados());
    }

    /** Revisor 1.3: un ELO fresco escrito desde fuera (ponerEloWatch de las vinculadas) quita la marca de «de anoche». */
    @Test void ponerEloFresco_quitaLaMarcaDeAnoche() {
        watchlist.presenter.ponerEloDeAnoche(8L, 1400);
        assertNull(WatchlistPresenter.eloParaResta(8L, eloWatch, watchlist.presenter.eloDelSnapshot));
        watchlist.ponerEloFresco(8L, 1450);
        assertEquals(1450, WatchlistPresenter.eloParaResta(8L, eloWatch, watchlist.presenter.eloDelSnapshot));
    }

    /** Revisor 1.3: la marca no se acumula: quien sale de la Watchlist y del top la pierde (y se volverá a barrer si
     *  vuelve); quien sigue en la lista la conserva. */
    @Test void podarEloDelSnapshot_soloQuedanLosPresentes() {
        todosJugadores.add(new Player(1L, "Uno", "Amigos"));
        watchlist.presenter.ponerEloDeAnoche(1L, 1500);
        watchlist.presenter.ponerEloDeAnoche(2L, 1600);   // ya no está en ninguna lista
        watchlist.presenter.watchBarridos.add(1L); watchlist.presenter.watchBarridos.add(2L);

        watchlist.aplicarFiltroGrupo();

        assertEquals(Set.of(1L), watchlist.presenter.eloDelSnapshot);
        assertTrue(watchlist.presenter.watchBarridos.contains(1L));
        assertFalse(watchlist.presenter.watchBarridos.contains(2L), "si vuelve a la Watchlist, se barre y se vuelve a marcar");
    }

    @Test void sugerenciaCaducada_siElTextoCambio() {
        assertTrue(WatchlistView.sugerenciaCaducada("tirador", "otra cosa"));
        assertFalse(WatchlistView.sugerenciaCaducada("tirador", "tirador "));   // el trim es parte del criterio
    }

    /**
     * Hallazgo del revisor sobre la decisión 8 (fila 112): dos jugadores distintos pueden tener el mismo texto
     * de fila (nombre + país + ELO iguales; el índice local, a diferencia de la API, no lleva el id pegado al
     * texto). Elegir por texto (r[2].equals(sel)) se quedaba con el primero que empatara, y en «Añadir al
     * grupo» los añadía a los dos. jugadorDeFila elige por ÍNDICE del combo, que sí es unívoco.
     */
    @Test void jugadorDeFila_eligePorIndiceAunqueDosFilasCompartanElMismoTexto() {
        List<String[]> res = List.of(
                new String[]{ "10", "Ana", "Ana · es · 1500" },
                new String[]{ "20", "Ana", "Ana · es · 1500" });   // mismo texto, id distinto (como en el índice local)

        assertEquals(10L, WatchlistView.jugadorDeFila(res, 0, "G").id());
        assertEquals(20L, WatchlistView.jugadorDeFila(res, 1, "G").id());
        assertEquals("G", WatchlistView.jugadorDeFila(res, 0, "G").grupo());
    }

    @Test void jugadorDeFila_indiceFueraDeRangoEsNull() {
        List<String[]> res = List.<String[]>of(new String[]{ "1", "Ana", "Ana · es" });
        assertNull(WatchlistView.jugadorDeFila(res, -1, "G"));   // nada seleccionado (combo vacío, JComboBox.getSelectedIndex() == -1)
        assertNull(WatchlistView.jugadorDeFila(res, 1, "G"));
        assertNull(WatchlistView.jugadorDeFila(List.of(), 0, "G"));
    }

    // agregarLocalesSinRepetir (y su test) se borraron con la decisión 8 (DEUDA fila 112): addPlayerDialog ya no
    // fusiona API+local a mano, delega en busqueda.buscar (BusquedaPerfilesCompanionTest cubre esa fusión).

    private static String t(String es, String en) { return dev.tirador.aoe2radar.util.I18n.t(es, en); }
}
