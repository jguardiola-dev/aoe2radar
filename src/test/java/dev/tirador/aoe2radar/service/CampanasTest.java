package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.cache.Sello;
import dev.tirador.aoe2radar.model.LadderHist;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.sfrdata.Ladder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Campanas: el set de vistas con aviso, la deduplicación y la fuente de Live now, sin red (transporte falso) y sin
 * tocar Swing. El ladder (★ladder/★país/★clan) usa CompanionApi o sfrdata.Ladder, ya probados por su cuenta: aquí
 * solo se comprueba que Campanas los llama bien.
 */
class CampanasTest {

    static final class TransporteFalso implements Transporte {
        final List<String> pedidas = new ArrayList<>();
        String cuerpo = "{\"players\":[]}";
        int estado = 200;
        @Override public Respuesta get(String url) { pedidas.add(url); return new Respuesta(estado, cuerpo); }
    }
    static final class ThrottleSinFreno implements Throttle {
        @Override public void adquirir(java.util.function.BooleanSupplier cancelar) { }
        @Override public long registrar429() { return 0; }
    }

    final TransporteFalso red = new TransporteFalso();
    final CompanionApi api = new CompanionApi(new ApiClient(new ThrottleSinFreno(), red, s -> { }, () -> false));
    final Map<String, String> config = new HashMap<>();
    final BiFunction<String, String, String> leer = (clave, porDefecto) -> config.getOrDefault(clave, porDefecto);
    final BiConsumer<String, String> guardar = config::put;
    final Campanas campanas = new Campanas(api, leer, guardar);

    String idiomaPrevio;
    @BeforeEach void fijarIdioma() { idiomaPrevio = IDIOMA; IDIOMA = "es"; }
    @AfterEach void restaurarIdioma() { IDIOMA = idiomaPrevio; }

    /** Una fila de /leaderboards con lo mínimo que lee FilaClasificacion. */
    static String fila(long pid, String nombre, int rating, int rango, String pais) {
        return "{\"profile_id\":" + pid + ",\"name\":\"" + nombre + "\",\"rating\":" + rating + ",\"rank\":" + rango + ",\"country\":\"" + pais + "\"}";
    }
    /** Como fila(...), pero sin "rank": FilaClasificacion.rango() sale null, como cuando la API no lo manda. */
    static String filaSinRango(long pid, String nombre, int rating, String pais) {
        return "{\"profile_id\":" + pid + ",\"name\":\"" + nombre + "\",\"rating\":" + rating + ",\"country\":\"" + pais + "\"}";
    }
    void responder(String... filas) { red.cuerpo = "{\"players\":[" + String.join(",", filas) + "],\"total\":" + filas.length + ",\"per_page\":100}"; }

    // ----- campanas() / guardarCampanas(): el set en config, separado con el texto "\u0001" -----

    @Test void sinConfigElSetEstaVacio() {
        assertEquals(Set.of(), campanas.campanas());
    }

    @Test void guardaYLeeUnaCampana() {
        campanas.guardarCampanas(new LinkedHashSet<>(List.of("grupo|A")));
        assertEquals(Set.of("grupo|A"), campanas.campanas());
    }

    @Test void guardaYLeeVariasCampanas() {
        Set<String> tres = new LinkedHashSet<>(List.of("grupo|A", "grupo|B", "★ladder"));
        campanas.guardarCampanas(tres);
        assertEquals(tres, campanas.campanas());
    }

    /**
     * Arreglo (2026-09-25): antes, campanas() dividía con una expresión regular que interpretaba "\u0001" como
     * el carácter real U+0001; como lo guardado nunca contenía ese carácter (ver guardarCampanas/SEPARADOR),
     * con más de una campana activa quedaban pegadas en un único elemento. El FORMATO GUARDADO no cambia (sigue
     * siendo el texto literal de 6 caracteres "\u0001" entre cada campana, tal cual lo escribía la 1.1): esta
     * prueba simula una config ya existente, escrita con ese mismo texto literal, y comprueba que ahora se lee
     * bien, sin pasar por guardarCampanas.
     */
    @Test void leeUnaConfigVieja() {
        config.put("campanas", "grupo|A" + "\\u0001" + "grupo|B" + "\\u0001" + "★ladder");
        assertEquals(Set.of("grupo|A", "grupo|B", "★ladder"), campanas.campanas());
    }

    /**
     * El formato REAL en disco: util.Config guarda y lee con java.util.Properties, que escapa cada barra al
     * escribir («\» se guarda como «\\» en el .properties) y la restaura al leer. Aquí se hace ese mismo viaje
     * (sin fichero: un StringWriter/StringReader) para comprobar que el separador sobrevive tal cual y las
     * campanas salen separadas, no solo con el mapa en memoria de este test (que no escapa nada).
     */
    @Test void elSeparadorSobreviveAUnPropertiesStoreYLoad() throws IOException {
        Set<String> tres = new LinkedHashSet<>(List.of("grupo|A", "grupo|B", "★ladder"));
        Map<String, String> escrito = new HashMap<>();
        new Campanas(api, leer, escrito::put).guardarCampanas(tres);

        Properties paraGuardar = new Properties();
        paraGuardar.setProperty("campanas", escrito.get("campanas"));
        StringWriter sw = new StringWriter();
        paraGuardar.store(sw, null);

        Properties releidas = new Properties();
        releidas.load(new StringReader(sw.toString()));

        Campanas lector = new Campanas(api, (clave, porDefecto) -> releidas.getProperty(clave, porDefecto), guardar);
        assertEquals(tres, lector.campanas());
    }

    // ----- alternar -----

    @Test void alternarEnciendeYLuegoApaga() {
        assertTrue(campanas.alternar("grupo|A"), "no estaba: se enciende");
        assertEquals(Set.of("grupo|A"), campanas.campanas());
        assertFalse(campanas.alternar("grupo|A"), "ya estaba: se apaga");
        assertEquals(Set.of(), campanas.campanas());
    }

    // ----- campanaContiene -----

    @Test void campanaContieneMiraTodasLasVistas() {
        Map<String, Set<Long>> campanaIds = Map.of("grupo|A", Set.of(1L, 2L), "★ladder", Set.of(3L));
        assertTrue(campanas.campanaContiene(2L, campanaIds));
        assertTrue(campanas.campanaContiene(3L, campanaIds));
        assertFalse(campanas.campanaContiene(99L, campanaIds));
    }

    // ----- calcularCampanaIds: recalcula los ids de cada vista con campana -----

    @Test void calcularCampanaIdsDeGrupo() {
        List<Player> todos = List.of(new Player(101, "Ana", "A"), new Player(102, "Bea", "B"), new Player(103, "Cel", "A"));
        Map<String, Set<Long>> ids = campanas.calcularCampanaIds(Set.of("grupo|A"), todos);
        assertEquals(Set.of(101L, 103L), ids.get("grupo|A"));
    }

    @Test void calcularCampanaIdsDeGrupoTodosIncluyeCualquierGrupo() {
        List<Player> todos = List.of(new Player(101, "Ana", "A"), new Player(102, "Bea", "B"));
        Map<String, Set<Long>> ids = campanas.calcularCampanaIds(Set.of("grupo|Todos"), todos);
        assertEquals(Set.of(101L, 102L), ids.get("grupo|Todos"));
    }

    @Test void calcularCampanaIdsDeLadderPideElTopNYCorta() {
        config.put("top_n", "2");
        responder(fila(910001, "A", 2000, 1, "es"), fila(910002, "B", 1900, 2, "fr"), fila(910003, "C", 1800, 3, "de"));
        Map<String, Set<Long>> ids = campanas.calcularCampanaIds(Set.of("★ladder"), List.of());
        assertEquals(Set.of(910001L, 910002L), ids.get("★ladder"), "top_n=2: solo los dos primeros");
    }

    @Test void calcularCampanaIdsDePaisPideElLeaderboardDeEsePais() {
        config.put("top_n", "50");
        responder(fila(910011, "A", 2000, 1, "es"));
        Map<String, Set<Long>> ids = campanas.calcularCampanaIds(Set.of("★pais|es"), List.of());
        assertEquals(Set.of(910011L), ids.get("★pais|es"));
        assertTrue(red.pedidas.get(0).contains("country=es"), red.pedidas.get(0));
    }

    @Test void calcularCampanaIdsDeClanUsaElLadderDeSfrData() throws Exception {
        marcarLadderFresco(Map.of("miclan", List.of(new LadderRow(910021, "A", 1500, 10, "es", "miclan", 100))));
        try {
            Map<String, Set<Long>> ids = campanas.calcularCampanaIds(Set.of("★clan|miclan"), List.of());
            assertEquals(Set.of(910021L), ids.get("★clan|miclan"));
        } finally { limpiarLadder(); }
    }

    @Test void calcularCampanaIdsUnFalloEnUnaVistaNoParaLasDemas() {
        // "★pais|es" falla (red rota); "grupo|A" no depende de la red y debe calcularse igual.
        red.estado = 500;
        List<Player> todos = List.of(new Player(101, "Ana", "A"));
        Map<String, Set<Long>> ids = campanas.calcularCampanaIds(new LinkedHashSet<>(List.of("★pais|es", "grupo|A")), todos);
        assertEquals(Set.of(), ids.get("★pais|es"), "si falla, la vista queda con el set vacío, no null");
        assertEquals(Set.of(101L), ids.get("grupo|A"));
    }

    // ----- dedupe de avisos: la misma partida no avisa dos veces -----

    @Test void tocaAvisarMiPartidaSoloLaPrimeraVez() {
        config.put("mi_pid", "7");
        Match m = partida(555);
        assertTrue(campanas.tocaAvisarMiPartida(7, m), "primera vez: toca avisar");
        assertFalse(campanas.tocaAvisarMiPartida(7, m), "misma partida otra vez: no vuelve a avisar");
    }

    @Test void tocaAvisarMiPartidaSoloSiElPidEsElMio() {
        config.put("mi_pid", "7");
        assertFalse(campanas.tocaAvisarMiPartida(8, partida(555)), "8 no es mi_pid");
    }

    @Test void tocaAvisarMiPartidaSinConfigNoAvisa() {
        assertFalse(campanas.tocaAvisarMiPartida(7, partida(555)));
    }

    @Test void tocaAvisarMiPartidaSinPartidaNoAvisa() {
        config.put("mi_pid", "7");
        assertFalse(campanas.tocaAvisarMiPartida(7, null), "el pid es el mío, pero sin partida no hay nada que avisar");
    }

    @Test void tocaAvisarSoloSiEstaEnUnaVistaConCampana() {
        Map<String, Set<Long>> campanaIds = Map.of("grupo|A", Set.of(42L));
        assertFalse(campanas.tocaAvisar(99L, partida(1), campanaIds), "99 no está vigilado");
        assertTrue(campanas.tocaAvisar(42L, partida(1), campanaIds), "42 sí, primera vez");
    }

    @Test void tocaAvisarSinPartidaNoAvisa() {
        Map<String, Set<Long>> campanaIds = Map.of("grupo|A", Set.of(42L));
        assertFalse(campanas.tocaAvisar(42L, null, campanaIds), "42 está vigilado, pero sin partida no hay nada que avisar");
    }

    @Test void tocaAvisarNoRepiteParaLaMismaPartida() {
        Map<String, Set<Long>> campanaIds = Map.of("grupo|A", Set.of(42L));
        assertTrue(campanas.tocaAvisar(42L, partida(1), campanaIds));
        assertFalse(campanas.tocaAvisar(42L, partida(1), campanaIds), "misma partida: no repite");
        assertTrue(campanas.tocaAvisar(42L, partida(2), campanaIds), "partida distinta: sí avisa");
    }

    /**
     * tocaAvisar y tocaAvisarMiPartida comparten un único set (avisadosClave), con claves "pid|matchId" y
     * "mi|matchId": distinta forma de construir la clave, así que no se pisan entre sí para el mismo jugador y
     * la misma partida.
     */
    @Test void avisadosClaveNoMezclaMiPartidaConLaDeUnVigilado() {
        config.put("mi_pid", "7");
        Match m = partida(5);
        Map<String, Set<Long>> campanaIds = Map.of("grupo|A", Set.of(7L));
        assertTrue(campanas.tocaAvisar(7L, m, campanaIds), "clave \"7|5\": primera vez");
        assertTrue(campanas.tocaAvisarMiPartida(7L, m), "clave \"mi|5\": distinta de \"7|5\", no la consumió la anterior");
        assertFalse(campanas.tocaAvisar(7L, m, campanaIds), "\"7|5\" ya avisada");
        assertFalse(campanas.tocaAvisarMiPartida(7L, m), "\"mi|5\" ya avisada, por su cuenta");
    }

    static Match partida(long id) { Match m = new Match(); m.id = id; m.map = "Arabia"; m.mode = "rm_1v1"; return m; }

    // ----- fuente de Live now: {pid, nombre, rating, rango, país} según el tipo -----

    @Test void fuenteLiveTop() throws Exception {
        responder(fila(920001, "A", 2000, 1, "es"), fila(920002, "B", 1900, 2, "fr"), fila(920003, "C", 1800, 3, "de"));
        List<Object[]> filas = campanas.cargarFuenteLive("top", "", 2, List.of(), pid -> null);
        assertEquals(2, filas.size(), "limiteTop corta la lista");
        assertArrayEquals(new Object[]{ 920001L, "A", 2000, 1, "es" }, filas.get(0));
        assertArrayEquals(new Object[]{ 920002L, "B", 1900, 2, "fr" }, filas.get(1));
    }

    @Test void fuenteLivePais() throws Exception {
        responder(fila(920011, "A", 2000, 5, "es"));
        List<Object[]> filas = campanas.cargarFuenteLive("pais", "es", 250, List.of(), pid -> null);
        assertEquals(1, filas.size());
        assertArrayEquals(new Object[]{ 920011L, "A", 2000, 5, "es" }, filas.get(0));
        assertTrue(red.pedidas.get(0).contains("country=es"), red.pedidas.get(0));
    }

    @Test void fuenteLiveClan() throws Exception {
        marcarLadderFresco(Map.of("miclan", List.of(new LadderRow(920021, "A", 1500, 10, "es", "miclan", 100))));
        try {
            List<Object[]> filas = campanas.cargarFuenteLive("clan", "miclan", 250, List.of(), pid -> null);
            assertEquals(1, filas.size());
            assertArrayEquals(new Object[]{ 920021L, "A", 1500, 10, "es" }, filas.get(0));
        } finally { limpiarLadder(); }
    }

    @Test void fuenteLiveGrupoLeeElEloYaConocidoSinIrARed() throws Exception {
        List<Player> todos = List.of(new Player(920031, "Ana", "A"), new Player(920032, "Bea", "B"));
        LongFunction<Integer> eloDe = pid -> pid == 920031 ? 1700 : null;
        List<Object[]> filas = campanas.cargarFuenteLive("grupo", "A", 250, todos, eloDe);
        assertEquals(1, filas.size());
        assertArrayEquals(new Object[]{ 920031L, "Ana", 1700, 0, null }, filas.get(0));
        assertTrue(red.pedidas.isEmpty(), "grupo no va a la red");
    }

    @Test void fuenteLiveGrupoSinEloConocidoUsaCero() throws Exception {
        List<Player> todos = List.of(new Player(920041, "Ana", "A"));
        List<Object[]> filas = campanas.cargarFuenteLive("grupo", "A", 250, todos, pid -> null);
        assertEquals(0, filas.get(0)[2]);
    }

    /** Sin rango de la API: en «top» se usa la posición (1-based, top.size()+1 antes de añadir la fila). */
    @Test void fuenteLiveTopSinRangoUsaLaPosicion() throws Exception {
        responder(filaSinRango(930001, "A", 2000, "es"), filaSinRango(930002, "B", 1900, "fr"));
        List<Object[]> filas = campanas.cargarFuenteLive("top", "", 250, List.of(), pid -> null);
        assertEquals(1, filas.get(0)[3]);
        assertEquals(2, filas.get(1)[3]);
    }

    /** Sin rango de la API: en «pais» se usa 0 (no la posición, a diferencia de «top»). */
    @Test void fuenteLivePaisSinRangoUsaCero() throws Exception {
        responder(filaSinRango(930011, "A", 2000, "es"));
        List<Object[]> filas = campanas.cargarFuenteLive("pais", "es", 250, List.of(), pid -> null);
        assertEquals(0, filas.get(0)[3]);
    }

    // ----- utilidades para dejar sfrdata.Ladder listo sin red (LADDER_SELLO es privado: se marca por reflexión) -----

    static void marcarLadderFresco(Map<String, List<LadderRow>> clanes) throws Exception {
        Ladder.ladderHists = Map.of("rm_1v1", new LadderHist(1, 0, new int[0], 0, Map.of()));
        Ladder.clanes = clanes;
        Field f = Ladder.class.getDeclaredField("LADDER_SELLO");
        f.setAccessible(true);
        ((Sello) f.get(null)).marcar();
    }
    static void limpiarLadder() { Ladder.ladderHists = Map.of(); Ladder.clanes = Map.of(); }
}
