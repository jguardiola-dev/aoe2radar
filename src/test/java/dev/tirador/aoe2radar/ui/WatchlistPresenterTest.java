package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.service.Campanas;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.TopLadderService;
import dev.tirador.aoe2radar.service.VistaInicial;
import dev.tirador.aoe2radar.util.Config;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * WatchlistPresenter sin Swing: una Pantalla de mentira que dice qué hay elegido en los controles y apunta, en orden,
 * lo que el presentador le pide (repintados, cargas, barridos). Config en un mapa en memoria. Tareas.EN_LINEA donde
 * hay hilos (todo en el acto y en orden).
 */
class WatchlistPresenterTest {

    static final String TOP_PAIS = "★ Top país", TOP_CLAN = "★ Top clan";

    /** La vista de mentira: el combo es un String, los controles son campos; cada petición deja su rastro en «llamadas». */
    static class PantallaFalsa implements WatchlistPresenter.Pantalla {
        String grupo;
        List<String> items = new ArrayList<>();
        String clan;
        boolean soloVivos, mostrarElo = true;
        WatchlistView.EnlacePartidas enlace = new WatchlistViewTest.EnlaceFalso();
        Campanas campanas;
        @Override public Campanas campanas() { return campanas; }
        @Override public void refrescarCampanaBtn() { llamadas.add("campanaBtn"); }
        final List<String> llamadas = new ArrayList<>();
        @Override public boolean soloVivosMarcado() { return soloVivos; }
        @Override public boolean mostrarEloWatch() { return mostrarElo; }
        @Override public WatchlistView.EnlacePartidas enlace() { return enlace; }
        @Override public String grupoSeleccionado() { return grupo; }
        @Override public List<String> itemsCombo() { return items; }
        @Override public String clanEscrito() { return clan; }
        @Override public void actualizarBotonesModo() { llamadas.add("botones"); }
        @Override public void rellenarClanSiVacio() { llamadas.add("rellenarClan"); }
        @Override public void cargarTopLadder(boolean forzar) { llamadas.add("topLadder:" + forzar); }
        @Override public void vigilarTop() { llamadas.add("vigilarTop"); }
        @Override public void avisarTopCaido(String texto) { llamadas.add("avisoTop:" + texto); }
        @Override public void refrescarFiltro() { llamadas.add("filtro"); }
        @Override public void refrescarWatchlist() { llamadas.add("barrido"); }
        @Override public void indicadoresVivos() { llamadas.add("indicadores"); }
        @Override public void estado(String texto) { llamadas.add("estado:" + texto); }
        @Override public void reconstruirGrupos() { llamadas.add("grupos"); }
        @Override public void ofrecerVinculadasTrasAlta(long pid, String nombre, String grupo) { llamadas.add("vinculadas:" + pid + ":" + nombre + ":" + grupo); }
    }

    /** FormService de mentira: resta y serie por pid (null = no hay); pendiente = nunca consultado (ts 0). Apunta
     *  qué ELO/partidas le pasaron para la resta y a quién pidió la serie. */
    static final class FormaFalsa implements dev.tirador.aoe2radar.service.FormService {
        final Map<Long, Forma[]> resta = new HashMap<>(), serie = new HashMap<>();
        final List<String> pedidos = new ArrayList<>();
        @Override public Forma[] porResta(long pid, java.util.function.LongFunction<Integer> eloActual, java.util.function.LongFunction<Integer> partidasActual) {
            pedidos.add("resta:" + pid + ":" + eloActual.apply(pid) + ":" + partidasActual.apply(pid));
            return resta.get(pid);
        }
        @Override public Forma[] porSerie(long pid) throws Exception {
            pedidos.add("serie:" + pid);
            if (pid == 13L) throw new java.io.IOException("sin red");
            return serie.get(pid);
        }
        @Override public boolean pendiente(long ultimaConsultaTs, long ahoraMs) { return ultimaConsultaTs == 0; }
    }

    FormaFalsa forma;
    Map<Long, Integer> gamesWatch;
    Map<Long, Integer> eloWatch;
    List<Player> topLadder;
    /** Un EstadoVivo propio (no el del sistema): quién juega, sin compartir estado con otros tests. */
    EstadoVivo vivo;
    WatchlistViewTest.AnfitrionFalso anfitrion;
    Map<String, Set<Long>> campanaIds;
    /** La config de las campanas (la de Campanas: campanas, mi_pid...). */
    Map<String, String> cfgCampanas;
    /** Lo que el presentador manda al EDT (tareas.enUi) se guarda aquí y se ejecuta a mano: así se ve qué va en el EDT. */
    final List<Runnable> enUi = new ArrayList<>();
    final List<String> hilos = new ArrayList<>();
    /** false: enFondo solo apunta el hilo (para lo que en la app iría a la red, p. ej. «top-clan»). */
    boolean correrFondo = true;
    final Tareas tareas = new Tareas() {
        @Override public void enFondo(String nombre, Runnable trabajo) { hilos.add(nombre); if (correrFondo) trabajo.run(); }
        @Override public void enFondoDemonio(String nombre, Runnable trabajo) { hilos.add(nombre + "(demonio)"); trabajo.run(); }
        @Override public void enFondoDemonioMinima(String nombre, Runnable trabajo) { hilos.add(nombre + "(minima)"); trabajo.run(); }
        @Override public void enUi(Runnable trabajo) { enUi.add(trabajo); }
    };
    private void correrEdt() { List<Runnable> r = new ArrayList<>(enUi); enUi.clear(); r.forEach(Runnable::run); }
    PantallaFalsa pantalla;
    List<Player> jugadores;
    Map<String, String> cfg;
    WatchlistPresenter p;
    String idiomaPrevio;
    final PaisItem[] paises = { new PaisItem("Alemania", "de"), new PaisItem("España", "es"), new PaisItem("Francia", "fr"), new PaisItem("Bélgica", "be") };

    @TempDir Path tmp;

    /** config.properties de util.Config (fijarPais guarda top_pais ahí): se deja como estaba, sin borrar lo de otros. */
    byte[] configPrevia;

    @BeforeEach void crear() throws Exception {
        configPrevia = Files.exists(Config.CONFIG_FILE) ? Files.readAllBytes(Config.CONFIG_FILE) : null;
        idiomaPrevio = IDIOMA;
        IDIOMA = "es";
        pantalla = new PantallaFalsa();
        jugadores = new ArrayList<>();
        cfg = new HashMap<>();
        forma = new FormaFalsa();
        gamesWatch = new HashMap<>();
        eloWatch = new HashMap<>();
        vivo = new EstadoVivo(new RelojFalso());
        anfitrion = new WatchlistViewTest.AnfitrionFalso();
        campanaIds = new java.util.concurrent.ConcurrentHashMap<>();
        cfgCampanas = new HashMap<>();
        pantalla.campanas = new Campanas(new CompanionApi(new ApiClient(new WatchlistViewTest.ThrottleSinFreno(), new WatchlistViewTest.TransporteNuncaLlamado(), s -> { }, () -> false)),
                (k, def) -> cfgCampanas.getOrDefault(k, def), cfgCampanas::put, nombre -> false);
        p = nuevo(tmp.resolve("players.txt"));
        topLadder = p.top.topLadder;
    }

    /** El reloj del TopLadderService (topFresco): la hora de «ahora» para los 10 min del top. */
    final RelojFalso reloj = new RelojFalso();
    private static CompanionApi companion() {
        return new CompanionApi(new ApiClient(new WatchlistViewTest.ThrottleSinFreno(), new WatchlistViewTest.TransporteNuncaLlamado(), s -> { }, () -> false));
    }

    private WatchlistPresenter nuevo(Path playersFile) {
        return new WatchlistPresenter(pantalla, jugadores, playersFile, TOP_PAIS, TOP_CLAN, paises,
                (k, def) -> cfg.getOrDefault(k, def), cfg::put, forma, gamesWatch,
                new WatchlistViewTest.ProfileServiceFalso(), vivo, eloWatch,
                new TopLadderService(companion(), companion(), reloj, ms -> { }, 300), tmp.resolve("top_cache.txt"),
                tareas, anfitrion, new WatchlistViewTest.MenusFalso(), new WatchlistViewTest.NavegacionFalsa(), campanaIds);
    }

    @AfterEach void restaurar() throws Exception {
        IDIOMA = idiomaPrevio;
        if (configPrevia != null) Files.write(Config.CONFIG_FILE, configPrevia); else Files.deleteIfExists(Config.CONFIG_FILE);
    }

    // ===== modo de vista: se lee SIEMPRE de la Pantalla ===============================================================

    @Test void modos_seLeenDelComboEnCadaConsulta() {
        pantalla.grupo = WatchlistView.TOP_LADDER;
        assertTrue(p.modoTop()); assertFalse(p.modoClan()); assertFalse(p.modoPais());
        pantalla.grupo = TOP_PAIS;   // sin avisar al presentador: no guarda el modo
        assertTrue(p.modoTop()); assertTrue(p.modoPais());
        pantalla.grupo = TOP_CLAN;
        assertTrue(p.modoTop()); assertTrue(p.modoClan());
        pantalla.grupo = "Amigos";
        assertFalse(p.modoTop());
        pantalla.grupo = null;
        assertFalse(p.modoTop());
    }

    @Test void grupoActivo_soloGruposDeUsuario() {
        for (String especial : List.of("Todos", WatchlistView.TOP_LADDER, TOP_PAIS, TOP_CLAN, "+ Nuevo grupo…", "Gestionar grupos…")) {
            pantalla.grupo = especial;
            assertNull(p.grupoActivo(), especial);
            assertEquals(WatchlistView.GRUPO_GENERAL, p.grupoDestino(), especial);
        }
        pantalla.grupo = null;
        assertNull(p.grupoActivo());
        pantalla.grupo = "Pros";
        assertEquals("Pros", p.grupoActivo());
        assertEquals("Pros", p.grupoDestino());
    }

    @Test void vistaActualId_grupoYPaisSoloEnTopPais() {
        pantalla.grupo = "Amigos";
        assertEquals("Amigos|", p.vistaActualId());
        pantalla.grupo = null;
        assertEquals("null|", p.vistaActualId(), "como la vista: el item null se concatena tal cual");
        p.paisActual = new PaisItem("Francia", "fr");
        pantalla.grupo = TOP_PAIS;
        assertEquals(TOP_PAIS + "|fr", p.vistaActualId());
    }

    @Test void paisSel_elElegidoOElDeConfig() {
        assertEquals("es", p.paisSel(), "sin país elegido: top_pais de util.Config, por defecto es");
        p.paisActual = new PaisItem("Alemania", "de");
        assertEquals("de", p.paisSel());
    }

    @Test void idVistaCampana_yNombreVistaCampana() {
        pantalla.grupo = "Todos";
        assertEquals("grupo|Todos", p.idVistaCampana());
        assertEquals("Todos", p.nombreVistaCampana());
        pantalla.grupo = WatchlistView.TOP_LADDER;
        assertEquals("★ladder", p.idVistaCampana());
        pantalla.grupo = TOP_PAIS;
        assertEquals("★pais|es", p.idVistaCampana());
        assertEquals(TOP_PAIS + " · ES", p.nombreVistaCampana(), "sin país elegido: el código en mayúsculas");
        p.paisActual = new PaisItem("Francia", "fr");
        assertEquals("★pais|fr", p.idVistaCampana());
        assertEquals(TOP_PAIS + " · Francia", p.nombreVistaCampana());
        pantalla.grupo = TOP_CLAN;
        assertEquals("★clan|", p.idVistaCampana(), "sin campo de clan todavía");
        assertEquals("", p.clanBuscado());
        pantalla.clan = "  TdB ";
        assertEquals("★clan|tdb", p.idVistaCampana());
        assertEquals("TdB", p.clanBuscado());
        assertEquals(TOP_CLAN + " · TdB", p.nombreVistaCampana());
    }

    @Test void clanesGuardados_deLaConfigInyectada() {
        cfg.put("clanes_guardados", " R1 ,, TdB,");
        assertEquals(List.of("R1", "TdB"), p.clanesGuardados());
    }

    @Test void firmaClan_nuncaEsLaDeLadderNiPais() {
        assertEquals("clan|r1", WatchlistPresenter.firmaClan("R1"));
    }

    @Test void limpiarGrupo_sinSeparadoresDeConfig() {
        assertEquals("a b c", WatchlistPresenter.limpiarGrupo("  a;b,c "));
    }

    // ===== el combo cambia de item =====================================================================================

    @Test void grupoElegido_topClan() {
        pantalla.grupo = TOP_CLAN;
        pantalla.clan = "R1";
        correrFondo = false;
        p.grupoElegido();
        assertEquals(TOP_CLAN, cfg.get("grupo_activo"));
        assertEquals(List.of("botones", "rellenarClan", "estado:Descargando la lista de clanes…"), pantalla.llamadas);
        assertEquals(List.of("top-clan"), hilos, "el clan se pide en el hilo «top-clan»");
    }

    @Test void grupoElegido_topLadderYPais() {
        pantalla.grupo = WatchlistView.TOP_LADDER;
        p.grupoElegido();
        assertEquals(List.of("botones", "topLadder:false"), pantalla.llamadas);
        pantalla.llamadas.clear();
        pantalla.grupo = TOP_PAIS;
        p.grupoElegido();
        assertEquals(TOP_PAIS, cfg.get("grupo_activo"));
        assertEquals(List.of("botones", "topLadder:false"), pantalla.llamadas);
    }

    @Test void grupoElegido_grupoDeUsuario() {
        pantalla.grupo = "Amigos";
        p.grupoElegido();
        assertEquals("Amigos", cfg.get("grupo_activo"));
        assertEquals(List.of("botones", "filtro", "barrido", "indicadores"), pantalla.llamadas);
    }

    // ===== país =========================================================================================================

    @Test void ordenarPaises_elGuardadoPrimeroYElRestoPorNombre() {
        List<PaisItem> orden = p.ordenarPaises("fr");
        assertEquals(List.of("fr", "de", "be", "es"), orden.stream().map(PaisItem::code).toList());
        assertEquals("fr", p.paisActual.code());
        assertSame(orden, p.catalogoOrdenado);
    }

    @Test void paisesQueCoinciden_sinTildesPorNombreOCodigo() {
        p.ordenarPaises("es");
        assertEquals(List.of("be"), p.paisesQueCoinciden(" belg ").stream().map(PaisItem::code).toList(), "«belg» casa con «Bélgica»");
        assertEquals(List.of("de"), p.paisesQueCoinciden("de").stream().map(PaisItem::code).toList(), "por código");
        assertEquals(4, p.paisesQueCoinciden("").size(), "vacío: todos");
    }

    @Test void fijarPais_soloRecargaEnTopPais() {
        pantalla.grupo = "Amigos";
        p.fijarPais(new PaisItem("Alemania", "de"));
        assertEquals("de", p.paisSel());
        assertEquals(List.of(), pantalla.llamadas);
        pantalla.grupo = TOP_PAIS;
        p.fijarPais(new PaisItem("Francia", "fr"));
        assertEquals(List.of("topLadder:true"), pantalla.llamadas);
    }

    // ===== grupos =======================================================================================================

    @Test void indiceGuardado_exactoAntesQueBilingue() {
        List<String> items = List.of("Todos", WatchlistView.TOP_LADDER, TOP_PAIS, TOP_CLAN, "All");
        assertEquals(4, p.indiceGuardado(items, "all"), "hay un grupo de usuario «All»: gana a la traducción de «Todos»");
        assertEquals(0, p.indiceGuardado(List.of("Todos", "Pros"), "All"), "sin grupo «All»: «All» guardado es «Todos»");
        assertEquals(2, p.indiceGuardado(items, "★ Country top"));
        assertEquals(3, p.indiceGuardado(items, "★ Clan top"));
        assertEquals(-1, p.indiceGuardado(items, "Borrado"));
    }

    @Test void itemsFijosYGruposDelCombo() {
        assertEquals(List.of("Todos", WatchlistView.TOP_LADDER, TOP_PAIS, TOP_CLAN), p.itemsFijos());
        pantalla.items = List.of("Todos", WatchlistView.TOP_LADDER, TOP_PAIS, TOP_CLAN, "Amigos", "Pros");
        assertEquals(List.of("Amigos", "Pros"), p.gruposDelCombo());
    }

    @Test void grupoGuardado_porDefectoTodos() {
        assertEquals("Todos", p.grupoGuardado());
        cfg.put("grupo_activo", "Pros");
        assertEquals("Pros", p.grupoGuardado());
    }

    @Test void gruposYPertenencia() {
        jugadores.add(new Player(1L, "Uno", "Amigos"));
        cfg.put("grupos", "Torneo");
        assertTrue(p.containsPlayerId(1L));
        assertFalse(p.containsPlayerId(2L));
        assertEquals("Amigos", p.grupoDeJugador(1L));
        assertEquals(List.of("Amigos", "General", "Torneo"), new ArrayList<>(p.gruposDisponibles()));
        assertTrue(p.esGrupoDeUsuario("Torneo"));
        assertFalse(p.esGrupoDeUsuario("Todos"));
        assertEquals(List.of("Amigos", "Torneo"), new ArrayList<>(p.calcularGrupos()));
    }

    // ===== operaciones de la lista: guardar, combo, filtro, indicadores y estado, en el orden de la vista ==============

    private void tresJugadores() {
        jugadores.addAll(List.of(new Player(1L, "Uno", "Amigos"), new Player(2L, "Dos", "Amigos"), new Player(3L, "Tres", "Pros")));
    }

    @Test void moverJugador_ordenYTexto() throws Exception {
        tresJugadores();
        p.moverJugador(new Player(1L, "Uno", "Amigos"), "Pros");
        assertEquals("Pros", p.grupoDeJugador(1L));
        assertTrue(Files.readString(tmp.resolve("players.txt")).contains("1;Uno;Pros"), "guardado antes de repintar");
        assertEquals(List.of("grupos", "filtro", "indicadores", "estado:Uno movido al grupo «Pros»."), pantalla.llamadas);
    }

    @Test void renombrarYBorrar_orden() {
        tresJugadores();
        p.renombrarGrupo("Amigos", "Colegas");
        assertEquals("Colegas", p.grupoDeJugador(2L));
        p.borrarGrupo("Pros");
        assertEquals(WatchlistView.GRUPO_GENERAL, p.grupoDeJugador(3L));
        assertEquals(List.of("grupos", "filtro", "grupos", "filtro"), pantalla.llamadas);
    }

    @Test void registrarGrupo_reconstruyeElCombo() {
        p.registrarGrupo("Torneo");
        assertTrue(p.gruposConfig().contains("Torneo"));
        assertEquals(List.of("grupos"), pantalla.llamadas);
    }

    @Test void ficharDesdeTop_soloSiNoEstabaYOfreceVinculadas() {
        tresJugadores();
        p.ficharDesdeTop(new Player(1L, "Uno", "Amigos"), "Pros");
        assertEquals(List.of(), pantalla.llamadas, "ya seguido: nada");
        p.ficharDesdeTop(new Player(9L, "Nueve", WatchlistView.TOP_LADDER), "Pros");
        assertEquals("Pros", p.grupoDeJugador(9L));
        assertEquals(List.of("grupos", "filtro", "estado:Nueve añadido a «Pros» de tu watchlist.", "vinculadas:9:Nueve:Pros"), pantalla.llamadas);
    }

    @Test void moverVarios_cuentaIdsDistintosYBarre() {
        tresJugadores();
        p.moverVarios(List.of(new Player(1L, "Uno", "Amigos"), new Player(2L, "Dos", "Amigos"), new Player(2L, "Dos", "Amigos")), "Pros");
        assertEquals(List.of("grupos", "filtro", "barrido", "estado:2 jugadores movidos a «Pros»."), pantalla.llamadas);
    }

    @Test void quitarUnoYVarios() {
        tresJugadores();
        p.quitarDeWatchlist(3L);
        assertFalse(p.containsPlayerId(3L));
        assertEquals(List.of("filtro", "estado:Quitado de tu watchlist."), pantalla.llamadas);
        pantalla.llamadas.clear();
        p.quitarVarios(List.of(new Player(1L, "Uno", "Amigos"), new Player(2L, "Dos", "Amigos")));
        assertTrue(jugadores.isEmpty());
        assertEquals(List.of("grupos", "filtro", "estado:2 jugadores quitados."), pantalla.llamadas);
    }

    @Test void cargarYGuardar_elErrorVaAlEstado() {
        WatchlistPresenter conCarpeta = nuevo(tmp);   // una carpeta no se puede leer ni escribir como players.txt
        conCarpeta.cargarJugadores();
        assertTrue(pantalla.llamadas.get(0).startsWith("estado:No se pudo leer players.txt"), pantalla.llamadas.toString());
        assertEquals(List.of("grupos", "filtro"), pantalla.llamadas.subList(1, 3), "combo y filtro SIEMPRE, aunque falle");
        pantalla.llamadas.clear();
        conCarpeta.guardarJugadores();
        assertTrue(pantalla.llamadas.get(0).startsWith("estado:No se pudo guardar players.txt"), pantalla.llamadas.toString());
        pantalla.llamadas.clear();
        p.guardarJugadores();
        assertEquals(List.of(), pantalla.llamadas, "guardado bien: sin mensaje");
    }

    // ===== lista visible, familias y conteos ============================================================================

    private List<Long> ids(List<Player> l) { return l.stream().map(Player::id).toList(); }

    @Test void filasVisibles_grupoOrdenYSoloJugando() {
        Config.guardarConfig("orden_watch", "elo");
        jugadores.addAll(List.of(new Player(1L, "Ana", "Amigos"), new Player(2L, "Bea", "Amigos"), new Player(3L, "Cris", "Pros")));
        eloWatch.put(1L, 1400); eloWatch.put(2L, 1900);
        pantalla.grupo = "Amigos";
        assertEquals(List.of(2L, 1L), ids(p.filasVisibles()), "por ELO, de más a menos");
        pantalla.mostrarElo = false;
        assertEquals(List.of(1L, 2L), ids(p.filasVisibles()), "sin ELO a la vista: por nombre");
        pantalla.grupo = "Todos";
        vivo.marcarJugando(3L, 30L);
        pantalla.soloVivos = true;
        assertEquals(List.of(3L), ids(p.filasVisibles()));
        topLadder.add(new Player(9L, "Top", WatchlistView.TOP_LADDER));
        pantalla.soloVivos = false;
        pantalla.grupo = WatchlistView.TOP_LADDER;
        assertEquals(List.of(9L), ids(p.filasVisibles()), "en los tops, la lista es el top cargado");
    }

    @Test void filasVisibles_familiaPlegadaYDesplegada() {
        jugadores.addAll(List.of(new Player(1L, "Uno", "G", 7L), new Player(2L, "Dos", "G", 7L)));
        eloWatch.put(2L, 2000);
        vivo.marcarJugando(1L, 10L);
        pantalla.grupo = "Todos";
        assertEquals(List.of(2L), ids(p.filasVisibles()), "plegada: solo la cabeza (la de más ELO)");
        assertEquals((Character) 'P', p.marcaFila.get(2L));
        assertEquals(Boolean.TRUE, p.vivoFamilia.get(2L), "una hija juega: la cabeza lo enseña");
        p.vinculosExpandidos.add(7L);
        assertEquals(List.of(2L, 1L), ids(p.filasVisibles()));
        assertEquals((Character) 'H', p.marcaFila.get(1L));
        assertEquals(1, p.conFamilias(List.of(jugadores.get(0))).size(), "la hija elegida va sola");
        assertEquals(2, p.conFamilias(List.of(jugadores.get(1))).size());
        assertArrayEquals(new String[]{ "Dos", "2000", "2" }, p.mejorAlt(1L));
        assertNull(p.mejorAlt(2L), "la cabeza ya es la de más ELO");
    }

    @Test void conteosDeVivos_listaAmbitoYGrupos() {
        jugadores.addAll(List.of(new Player(1L, "Uno", "Amigos"), new Player(2L, "Dos", "Pros")));
        topLadder.addAll(List.of(new Player(8L, "T8", WatchlistView.TOP_LADDER), new Player(9L, "T9", WatchlistView.TOP_LADDER)));
        vivo.marcarJugando(1L, 1L); vivo.marcarJugando(8L, 8L); vivo.marcarJugando(9L, 9L);
        pantalla.grupo = "Pros";
        assertEquals(1, p.vivosEnLista(), "el título solo cuenta la Watchlist");
        assertEquals(0, p.vivosEnAmbito());
        pantalla.grupo = "Todos";
        assertEquals(1, p.vivosEnAmbito());
        pantalla.grupo = WatchlistView.TOP_LADDER;
        assertEquals(2, p.vivosEnAmbito(), "en los tops, el ámbito es el top");
        assertTrue(p.grupoTieneVivo("AMIGOS"));
        assertFalse(p.grupoTieneVivo("Pros"));
        assertTrue(p.grupoTieneVivo(null));
    }

    @Test void tipCuentaVinculada_porElNombreDeLaTabla() {
        jugadores.addAll(List.of(new Player(1L, "Uno", "G", 5L), new Player(2L, "Otro", "G", 5L)));
        eloWatch.put(2L, 1700);
        Match m = new Match();
        m.refId = 1L;   // EnlaceFalso: «Uno»
        assertEquals("Cuenta vinculada de Otro", p.tipCuentaVinculada(m));
        m.refId = 2L;
        assertNull(p.tipCuentaVinculada(m));
    }

    @Test void marcarVinculoYSanear_guardanSoloSiCambia() throws Exception {
        jugadores.addAll(List.of(new Player(1L, "Uno", "G"), new Player(2L, "Dos", "G"), new Player(3L, "Tres", "G", 99L)));
        p.marcarVinculo(Set.of(1L));
        assertEquals(List.of(), pantalla.llamadas, "una sola cuenta: nada que vincular");
        p.marcarVinculo(Set.of(1L, 2L));
        assertEquals(List.of("filtro"), pantalla.llamadas);
        assertTrue(Files.readString(tmp.resolve("players.txt")).contains("2;Dos;G;1"), "guardado con la clave = menor id");
        p.sanearVinculosHuerfanos();
        assertEquals(0L, jugadores.get(2).vinculo(), "familia de una sola cuenta: fantasma fuera");
        assertTrue(Files.readString(tmp.resolve("players.txt")).contains("3;Tres;G" + System.lineSeparator()));
    }

    @Test void eloDeAnoche_marcasYPoda() {
        jugadores.add(new Player(1L, "Uno", "G"));
        p.ponerEloDeAnoche(1L, 1500);
        p.ponerEloDeAnoche(2L, 1600);
        assertNull(p.eloParaResta(1L), "el de anoche no vale para restar");
        p.watchBarridos.addAll(List.of(1L, 2L));
        p.podarEloDelSnapshot();
        assertEquals(Set.of(1L), p.eloDelSnapshot);
        assertEquals(Set.of(1L), p.watchBarridos);
        p.ponerEloFresco(1L, 1520);
        assertEquals(1520, p.eloParaResta(1L));
        assertTrue(p.eloDelSnapshot.isEmpty());
    }

    // ===== el top (WatchlistTop) ========================================================================================

    private static TopLadderService.ResultadoTop resultado(TopLadderService.FilaTop... filas) {
        return new TopLadderService.ResultadoTop(List.of(filas), Map.of(1L, 3), Map.of(1L, new int[]{ 6, 4 }), Map.of(1L, 250));
    }

    @Test void top_cargaYCache() {
        pantalla.grupo = TOP_PAIS;
        p.paisActual = new PaisItem("Francia", "fr");
        cfg.clear();
        WatchlistTop.Carga c = p.top.prepararCarga(false);
        assertNotNull(c);
        assertEquals("fr", c.pais()); assertEquals("fr", c.firma()); assertEquals("Francia", c.nombrePais());
        assertTrue(p.top.cargandoTop);
        assertNull(p.top.prepararCarga(false), "ya cargando: nada");
        assertTrue(pantalla.llamadas.get(0).startsWith("estado:Cargando el top "), pantalla.llamadas.toString());
        pantalla.llamadas.clear();
        p.top.cargaTerminada();
        eloDeAnocheDe(1L);
        p.top.aplicarCarga(resultado(new TopLadderService.FilaTop(1L, "Uno", 2100, 555L), new TopLadderService.FilaTop(2L, "Dos", 2000, 0L)), c);
        assertEquals(List.of(1L, 2L), ids(topLadder));
        assertEquals(2, p.top.rankTop.get(2L));
        assertEquals(2100, eloWatch.get(1L));
        assertTrue(p.eloDelSnapshot.isEmpty(), "el ELO del leaderboard es fresco");
        assertEquals(250, gamesWatch.get(1L));
        assertEquals(3, WatchlistPresenter.TOP_STREAK.get(1L));
        assertEquals("fr", p.top.topFirma);
        assertEquals(List.of("filtro", "indicadores", "estado:Top 2 de Francia cargado. Los puntos rojos llegan en segundos…", "vigilarTop"), pantalla.llamadas);
        assertTrue(Files.exists(tmp.resolve("top_cache.txt")), "guarda la caché de respaldo");

        // el siguiente viene vacío (servicio caído): tira de la caché de esa misma vista
        pantalla.llamadas.clear();
        topLadder.clear();
        p.top.aplicarCarga(resultado(), c);
        assertEquals(List.of(1L, 2L), ids(topLadder), "de la caché");
        assertEquals(List.of("filtro", "indicadores"), pantalla.llamadas.subList(0, 2));
        assertTrue(pantalla.llamadas.get(2).startsWith("estado:El servicio de datos no responde"), pantalla.llamadas.toString());
        WatchlistPresenter.TOP_STREAK.remove(1L); WatchlistPresenter.TOP_LAST10.remove(1L);
    }

    private void eloDeAnocheDe(long pid) { p.ponerEloDeAnoche(pid, 1); }

    @Test void top_caidoSinCache_avisaUnaSolaVez() {
        pantalla.grupo = WatchlistView.TOP_LADDER;
        WatchlistTop.Carga c = new WatchlistTop.Carga(null, "global", 50, null);
        p.top.aplicarCarga(resultado(), c);
        p.top.aplicarCarga(resultado(), c);
        long avisos = pantalla.llamadas.stream().filter(x -> x.startsWith("avisoTop:")).count();
        assertEquals(1, avisos, "el aviso modal, solo la primera vez por sesión");
        assertTrue(pantalla.llamadas.get(pantalla.llamadas.size() - 1).startsWith("estado:No se pudo cargar el top"));
        WatchlistPresenter.TOP_STREAK.remove(1L); WatchlistPresenter.TOP_LAST10.remove(1L);
    }

    @Test void top_siElUsuarioCambioDeVistaNoPinta() {
        pantalla.grupo = TOP_CLAN;   // la carga era del ladder; mientras, pasó a ★ Top clan
        p.top.aplicarCarga(resultado(new TopLadderService.FilaTop(1L, "Uno", 2100, 555L)), new WatchlistTop.Carga(null, "global", 50, null));
        assertTrue(topLadder.isEmpty());
        assertEquals(250, gamesWatch.get(1L), "las partidas se aprenden igual");
        assertEquals(List.of(), pantalla.llamadas);
        WatchlistPresenter.TOP_STREAK.remove(1L); WatchlistPresenter.TOP_LAST10.remove(1L);
    }

    @Test void top_frescoNoRecargaYErrorAlEstado() {
        pantalla.grupo = WatchlistView.TOP_LADDER;
        topLadder.add(new Player(1L, "Uno", WatchlistView.TOP_LADDER));
        p.top.topFirma = "global";
        p.top.topCargado = reloj.ahora;
        assertNull(p.top.prepararCarga(false));
        assertFalse(p.top.cargandoTop);
        assertEquals(List.of("filtro", "indicadores", "vigilarTop"), pantalla.llamadas);
        pantalla.llamadas.clear();
        p.top.errorCarga(new java.io.IOException("x"));
        assertTrue(pantalla.llamadas.get(0).startsWith("estado:Error cargando el top: "));
    }

    @Test void top_clan() {
        pantalla.grupo = TOP_CLAN;
        pantalla.clan = " ";
        p.top.cargarTopClan();
        assertEquals(List.of("estado:Escribe el tag del clan (p. ej. R1)."), pantalla.llamadas);
        assertEquals(List.of(), hilos);
        pantalla.llamadas.clear();
        p.top.aplicarTopClan("R1", new TopLadderService.ResultadoClan(null, List.of(new TopLadderService.FilaClan(9L, "Nueve", 1800))));
        assertEquals(List.of(9L), ids(topLadder));
        assertEquals(TOP_CLAN, topLadder.get(0).grupo());
        assertNull(p.eloParaResta(9L), "el resumen diario del clan es ELO de anoche");
        assertEquals("clan|r1", p.top.topFirma);
        assertEquals(List.of("filtro", "indicadores", "estado:Clan R1: 1 jugadores en el ladder 1v1 (resumen diario)."), pantalla.llamadas);
        pantalla.llamadas.clear();
        p.top.aplicarTopClan("R1", new TopLadderService.ResultadoClan("sin red", List.of()));
        assertEquals(List.of("estado:No se pudo cargar la lista de clanes: sin red"), pantalla.llamadas);
    }

    @Test void top_vigilancia() {
        assertNull(p.top.prepararVigilancia(), "sin top: nada");
        topLadder.addAll(List.of(new Player(1L, "Uno", "T"), new Player(2L, "Dos", "T"), new Player(3L, "Tres", "T")));
        List<Player> top = p.top.prepararVigilancia();
        assertEquals(3, top.size());
        assertNotSame(topLadder, top, "una copia");
        assertTrue(p.top.vigilandoTop);
        assertNull(p.top.prepararVigilancia(), "ya vigilando");
        p.top.vigilanciaTerminada();
        assertNull(p.top.prepararVigilancia(), "anti-solape de 45 s");
        vivo.marcarJugando(2L, 20L);
        vivo.marcarJugando(3L, 30L);
        p.top.aplicarVigilancia(top, new TopLadderService.ResultadoVigilancia(Map.of(1L, 10L), Set.of(1L, 2L)));
        assertTrue(vivo.jugando(1L));
        assertFalse(vivo.jugando(2L), "verificado y sin partida: fuera");
        assertTrue(vivo.jugando(3L), "no verificado (lote fallido): ni quitar ni poner");
        assertEquals(List.of("indicadores"), pantalla.llamadas);
        assertEquals(Set.of(1L, 2L), p.top.topVerificados);
    }

    @Test void top_snapshotEsUnaCopia() {
        topLadder.add(new Player(1L, "Uno", "T"));
        List<Player> s = p.top.snapshot();
        topLadder.clear();
        assertEquals(1, s.size());
    }

    // ===== barridos de vivos (el process de los SwingWorker) ============================================================

    @Test void objetivoBarrido_unaVezPorArranqueYNuncaEnLosTops() {
        List<Player> visibles = List.of(new Player(1L, "Uno", "G"), new Player(2L, "Dos", "G"));
        pantalla.grupo = WatchlistView.TOP_LADDER;
        assertEquals(List.of(), p.objetivoBarrido(visibles));
        assertTrue(p.watchBarridos.isEmpty(), "en los tops ni se marcan");
        pantalla.grupo = "G";
        p.watchBarridos.add(1L);
        assertEquals(List.of(2L), ids(p.objetivoBarrido(visibles)));
        assertEquals(List.of(), p.objetivoBarrido(visibles), "ya barridos");
    }

    @Test void procesarRefrescos_vivoYEloSegunDeDondeVengan() {
        vivo.marcarJugando(1L, 11L);
        p.procesarRefrescos(List.of(new dev.tirador.aoe2radar.service.BarridoVivos.Refresco(1L, null, null, 1500, 40),   // snapshot
                new dev.tirador.aoe2radar.service.BarridoVivos.Refresco(2L, 22L, "vs X", 1600, null)));                   // API, jugando
        assertTrue(vivo.jugando(1L), "el snapshot no sabe si juega: no apaga");
        assertNull(p.eloParaResta(1L), "ELO de anoche");
        assertTrue(vivo.jugando(2L));
        assertEquals("vs X", vivo.info(2L));
        assertEquals(1600, p.eloParaResta(2L), "ELO fresco");
        assertEquals(List.of("indicadores"), pantalla.llamadas);
    }

    /** EnlacePartidas sin efectos (para sobrescribir solo lo que se quiere mirar). */
    static class EnlaceMudo implements WatchlistView.EnlacePartidas {
        @Override public void fetchMatches() { }
        @Override public void mostrarDirectos(boolean mostrar) { }
        @Override public void refrescarSujetos(List<Player> tracked, boolean esInvitado) { }
        @Override public List<Player> ultimosSujetos() { return List.of(); }
        @Override public void taparResultados() { }
        @Override public void applyFilters() { }
        @Override public void actualizarTextoBuscar() { }
        @Override public void limpiarSujetos() { }
        @Override public void sincronizarSocket() { }
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

    @Test void procesarLotes_marcaVivosYCierraLasTerminadas() {
        List<String> tabla = new ArrayList<>();
        pantalla.enlace = new EnlaceMudo() {
            @Override public void applyFilters() { tabla.add("filtrar"); }
            @Override public void repintarTabla() { tabla.add("repintar"); }
        };
        vivo.marcarJugando(3L, 33L);
        Match enTabla = new Match(); enTabla.id = 500L;
        Match otra = new Match(); otra.id = 600L;
        List<Match> all = new ArrayList<>(List.of(enTabla, otra));
        Match fresco = new Match(); fresco.id = 500L; fresco.finished = java.time.Instant.ofEpochSecond(1000);
        var lote = new dev.tirador.aoe2radar.service.BarridoVivos.Lote(Set.of(1L, 3L), Map.of(1L, 10L), Map.of(1L, "info"), List.of(fresco));
        p.procesarLotes(List.of(lote), all);
        assertTrue(vivo.jugando(1L));
        assertEquals("info", vivo.info(1L));
        assertFalse(vivo.jugando(3L), "en el lote y sin partida: fuera");
        assertEquals(fresco.finished, enTabla.finished, "la EN DIRECTO de la tabla acabó: fecha real");
        assertNull(otra.finished);
        assertEquals(List.of("filtrar"), tabla);
        assertEquals(List.of("indicadores"), pantalla.llamadas);
        tabla.clear();
        p.procesarLotes(List.of(new dev.tirador.aoe2radar.service.BarridoVivos.Lote(Set.of(), Map.of(), Map.of(), List.of(fresco))), all);
        assertEquals(List.of("repintar"), tabla, "ya estaba terminada: solo repintar");
    }

    // ===== campanas y avisos ============================================================================================

    @Test void refrescarCampanas_calculaEnFondoYSincronizaEnElEdt() {
        jugadores.addAll(List.of(new Player(1L, "Uno", "Amigos"), new Player(2L, "Dos", "Pros")));
        pantalla.campanas.guardarCampanas(new java.util.LinkedHashSet<>(List.of("grupo|Amigos")));
        campanaIds.put("grupo|Viejo", Set.of(9L));
        p.refrescarCampanas();
        assertEquals(List.of("campanas"), hilos, "el cálculo va en el hilo «campanas»");
        assertEquals(Map.of("grupo|Amigos", Set.of(1L)), campanaIds, "retainAll+putAll: la vista sin campana sale");
        assertEquals(List.of(Set.of(1L, -1L)), anfitrion.socketExtra, "al socket, los vigilados (más lo de Live now)");
        assertEquals(1, enUi.size(), "sincronizarSocket, en el EDT");
        assertTrue(p.campanaContiene(1L));
        assertFalse(p.campanaContiene(2L));
    }

    @Test void refrescarCampanas_sinCampanasVaciaYAvisaAlSocket() {
        campanaIds.put("grupo|Viejo", Set.of(9L));
        p.refrescarCampanas();
        assertTrue(campanaIds.isEmpty());
        assertEquals(List.of(), hilos, "sin campanas no hay hilo");
        assertEquals(List.of(Set.of(-1L)), anfitrion.socketExtra);
        assertEquals(1, enUi.size());
    }

    @Test void alternarCampana_enciendeApagaYLoDice() {
        pantalla.grupo = "Amigos";
        assertFalse(p.campanaActiva());
        p.alternarCampana();
        assertTrue(p.campanaActiva());
        assertEquals(List.of("campanaBtn", "estado:Avisos activados para «Amigos»: te avisaré cuando alguien entre en partida."), pantalla.llamadas);
        pantalla.llamadas.clear();
        p.alternarCampana();
        assertFalse(p.campanaActiva());
        assertEquals(List.of("campanaBtn", "estado:Avisos apagados para esta lista."), pantalla.llamadas);
    }

    @Test void avisarSiCampana_textoEnElEdtYUnaVezPorPartida() {
        jugadores.add(new Player(1L, "Ana", "G"));
        topLadder.add(new Player(4L, "Cuatro", WatchlistView.TOP_LADDER));
        campanaIds.put("grupo|G", Set.of(1L, 4L));
        Match m = new Match();
        m.id = 5L;
        p.avisarSiCampana(1L, m);
        p.avisarSiCampana(1L, m);
        p.avisarSiCampana(4L, m);
        p.avisarSiCampana(2L, m);
        assertEquals(List.of(), anfitrion.avisos, "nada fuera del EDT");
        correrEdt();
        assertEquals(List.of("toast:● Ana ha empezado una partida · resumen#5", "toast:● Cuatro ha empezado una partida · resumen#5"), anfitrion.avisos,
                "el nombre sale de la Watchlist o del top cargado");
    }

    @Test void avisarMiPartida_rivalesFichasYAccesos() {
        cfgCampanas.put("mi_pid", "5");
        Match mm = new Match();
        mm.id = 88L; mm.map = "Arena";
        MatchPlayer yo = new MatchPlayer(); yo.id = 5L; yo.name = "Yo"; yo.team = 1;
        MatchPlayer r1 = new MatchPlayer(); r1.id = 6L; r1.name = "Riv"; r1.team = 2; r1.rating = 1500; r1.civ = "Franks";
        MatchPlayer r2 = new MatchPlayer(); r2.id = 7L; r2.name = "Dos"; r2.team = 2;
        mm.players.addAll(List.of(yo, r1, r2));
        p.avisarMiPartida(6L, mm);
        assertTrue(enUi.isEmpty(), "no soy yo: nada");
        p.avisarMiPartida(5L, mm);
        p.avisarMiPartida(5L, mm);   // la misma partida: una vez
        correrEdt();
        String modo = dev.tirador.aoe2radar.service.ReglasPartida.modoCorto(mm);
        assertEquals(List.of("superposicion:● Tu partida empieza · Arena · " + modo + " [6|Riv  ·  Franks|1500] [7|Dos|null] 60000",
                "toast:● Tu partida ha empezado · Arena · " + modo + " · vs Riv (1500) Franks, Dos#88", "acciones"), anfitrion.avisos);
    }

    // ===== forma reciente ===============================================================================================

    @Test void pendientesForma_losNuncaConsultadosDeEsaVentana() {
        List<Player> objetivo = List.of(new Player(1L, "Uno", "G"), new Player(2L, "Dos", "G"));
        p.formaTs24.put(2L, 123L);
        assertEquals(List.of(1L), p.pendientesForma(objetivo, 24).stream().map(Player::id).toList());
        assertEquals(List.of(1L, 2L), p.pendientesForma(objetivo, 24 * 7).stream().map(Player::id).toList(), "7 d mira su propia hora");
    }

    @Test void confirmarForma_soloEnLosTopsYConMasDe20() {
        pantalla.grupo = "Amigos";
        assertFalse(p.confirmarForma(50));
        pantalla.grupo = WatchlistView.TOP_LADDER;
        assertFalse(p.confirmarForma(20));
        assertTrue(p.confirmarForma(21));
        assertEquals("Consultar la forma de las últimas 24 h de 25 jugadores tarda ~15 s.\nSe consulta jugador a jugador (con pausas) y queda guardado 10 minutos.",
                WatchlistPresenter.textoConfirmarForma(25, 24));
        assertTrue(WatchlistPresenter.textoConfirmarForma(21, 168).startsWith("Consultar la forma de los últimos 7 días de 21 jugadores tarda ~13 s."));
    }

    @Test void consultarForma_primeroLaRestaLuegoLaSerie() {
        Forma a = new Forma(10, 2, 1, 0, true, 3), b = new Forma(-5, 1, 2, 0, true, 3), c = new Forma(1, 1, 0, 0, true, 1);
        forma.resta.put(1L, new Forma[]{ a, b });       // resta completa: sin llamadas
        forma.resta.put(2L, new Forma[]{ a, null });    // sin 7 d: con 7 d elegido, también por la serie
        forma.serie.put(2L, new Forma[]{ c, c });
        gamesWatch.put(1L, 40);
        List<Player> pend = List.of(new Player(1L, "Uno", "G"), new Player(2L, "Dos", "G"), new Player(13L, "Trece", "G"));
        List<String> publicado = new ArrayList<>();
        p.consultarForma(pend, 24 * 7, pid -> pid == 1L ? 1500 : null, publicado::add, () -> false);
        assertEquals(List.of("Forma: 1 del snapshot nocturno · 2 consultas"), publicado);
        assertEquals(List.of("resta:1:1500:40", "resta:2:null:null", "resta:13:null:null", "serie:2", "serie:13"), forma.pedidos);
        assertSame(a, p.forma24.get(1L)); assertSame(b, p.forma7d.get(1L));
        assertSame(c, p.forma24.get(2L), "la serie pisa la resta"); assertSame(c, p.forma7d.get(2L));
        assertNull(p.forma24.get(13L), "la serie falló: se apunta en el log y sigue");
        assertNotNull(p.formaTs24.get(1L)); assertNotNull(p.formaTs7d.get(2L)); assertNull(p.formaTs24.get(13L));
    }

    @Test void consultarForma_detenerCortaLasLlamadas() {
        List<String> publicado = new ArrayList<>();
        p.consultarForma(List.of(new Player(5L, "Cinco", "G")), 24, pid -> null, publicado::add, () -> true);
        assertEquals(List.of("Forma: 0 del snapshot nocturno · 1 consultas"), publicado);
        assertEquals(List.of("resta:5:null:null"), forma.pedidos, "con Detener pulsado, ni una serie");
    }

    @Test void tipForma_yFormaActivaSiguenLaVentana() {
        pantalla.grupo = "Amigos";
        p.forma24.put(7L, new Forma(3, 1, 0, 0, true, 1));
        assertEquals("Últimas 24 h: 1-0 · +3", p.tipForma(7L));
        p.ventanaForma = 24 * 7;
        assertSame(p.forma7d, p.formaActiva());
        assertEquals("Últimos 7 días: sin consultar (selecciónalo y pulsa Ver forma)", p.tipForma(7L));
    }

    @Test void quitarOrdenPorForma_soloSiIbaPorForma() {
        Config.guardarConfig("orden_watch", "forma_asc");
        p.quitarOrdenPorForma();
        assertEquals("elo", Config.leerConfig("orden_watch", "?"));
        Config.guardarConfig("orden_watch", "alfa");
        p.quitarOrdenPorForma();
        assertEquals("alfa", Config.leerConfig("orden_watch", "?"));
    }

    @Test void eleccionInicial_compruebaLoQueExiste() {
        cfg.put("abrir_en", "pais:fr");
        assertEquals(VistaInicial.Tipo.PAIS, p.eleccionInicial().tipo());
        cfg.put("abrir_en", "pais:zz");
        assertEquals(VistaInicial.Tipo.TOP, p.eleccionInicial().tipo(), "un país que no está en el catálogo: ★ Top ladder");
        pantalla.items = List.of("Todos", "Amigos");
        cfg.put("abrir_en", "grupo:amigos");
        assertEquals("Amigos", p.eleccionInicial().valor());
    }
}
