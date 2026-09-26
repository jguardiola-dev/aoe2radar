package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AzarServiceCompanion sin red: un Transporte falso responde JSON fijo por URL exacta (igual que CompanionApiTest),
 * así se prueba el muestreo real (bisección del ladder, río, perfiles, TTL de sesión) sin tocar la API de verdad.
 * La «muestra nocturna» (Snapshots.muestraAyer, que SÍ iría a la red) se sustituye por un Supplier de prueba: por
 * eso AzarServiceCompanion la recibe por constructor en vez de leerla con un import estático.
 */
class AzarServiceCompanionTest {

    /** gteDesdeMuestra guarda «gte_vistas» en config.properties (gitignored, ver TopLadderServiceTest): sin
     *  limpiarlo, una tirada anterior (de este test o de una ejecución previa) deja ids marcados como vistos y
     *  el siguiente buscarGte con muestra cae al ladder por sorpresa. */
    @AfterEach void limpiarVistasDeGte() throws Exception {
        Files.deleteIfExists(Path.of("config.properties"));
    }

    static final String API = "https://data.aoe2companion.com/api";

    // ----- fontanería de prueba (sin red) -----------------------------------------------------------------

    /** Un Transporte falso: respuestas fijas por URL exacta; cualquier URL no registrada revienta el test
     *  (para detectar llamadas de red que no esperábamos, p. ej. cuando la muestra nocturna debería bastar). */
    static final class FakeTransporte implements Transporte {
        final Map<String, String> respuestas = new HashMap<>();
        final Map<String, IOException> fallos = new HashMap<>();
        final Map<String, Integer> llamadas = new HashMap<>();
        void responder(String url, String json) { respuestas.put(url, json); }
        void fallar(String url, String mensaje) { fallos.put(url, new IOException(mensaje)); }
        @Override public Respuesta get(String url) throws IOException {
            llamadas.merge(url, 1, Integer::sum);
            IOException fallo = fallos.get(url);
            if (fallo != null) throw fallo;
            String json = respuestas.get(url);
            if (json == null) throw new AssertionError("URL no esperada en el test: " + url);
            return new Respuesta(200, json);
        }
        int llamadasA(String url) { return llamadas.getOrDefault(url, 0); }
        int totalLlamadas() { return llamadas.values().stream().mapToInt(Integer::intValue).sum(); }
    }

    static final class ThrottleNoop implements Throttle {
        @Override public void adquirir(java.util.function.BooleanSupplier cancelar) { }
        @Override public long registrar429() { return 0; }
    }

    static AzarServiceCompanion servicio(FakeTransporte red, Supplier<Map<String, List<List<Object>>>> muestra) {
        CompanionApi companion = new CompanionApi(new ApiClient(new ThrottleNoop(), red, ms -> { }, () -> false));
        return new AzarServiceCompanion(companion, civ -> civ, muestra, ms -> { }, 50, 1);
    }

    static Supplier<Map<String, List<List<Object>>>> sinMuestra() { return () -> null; }

    // ----- URLs exactas que construye CompanionApi (ver CompanionApiTest) --------------------------------

    static String urlLb(String id, int page) { return API + "/leaderboards/" + id + "?page=" + page + "&per_page=100"; }
    static String urlRecientes(String leaderboard, int page) {
        String filtro = leaderboard == null ? "" : "leaderboard_ids=" + leaderboard + "&";
        return API + "/matches?" + filtro + "page=" + page + "&per_page=50";
    }
    static String urlPartidas(long pid, int page) { return API + "/matches?profile_ids=" + pid + "&page=" + page + "&per_page=50"; }

    // ----- JSON de prueba (a mano, como CompanionApiTest) --------------------------------------------------

    static String jsonLadder(int total, int perPage, long[]... jugadores) {
        StringBuilder sb = new StringBuilder("{\"total\":").append(total).append(",\"per_page\":").append(perPage).append(",\"players\":[");
        for (int i = 0; i < jugadores.length; i++) {
            long[] j = jugadores[i];
            if (i > 0) sb.append(',');
            sb.append("{\"profile_id\":").append(j[0]).append(",\"rating\":").append(j[1])
              .append(",\"last_match_time\":").append(j[2]).append('}');
        }
        return sb.append("]}").toString();
    }

    static final String SIN_PARTIDAS = "{\"matches\":[]}";

    /** Una sola partida 1v1 Random Map, terminada hace poco, entre dos perfiles conocidos. */
    static String jsonUnaPartida1v1(long id, long p1, long p2) {
        long ahora = Instant.now().getEpochSecond();
        return "{\"matches\":[{\"match_id\":" + id + ",\"started\":" + (ahora - 900) + ",\"finished\":" + (ahora - 600)
                + ",\"leaderboard_name\":\"1v1 Random Map\",\"map_name\":\"Arabia\",\"players\":["
                + "{\"profile_id\":" + p1 + ",\"name\":\"Uno\",\"civ_name\":\"Franks\",\"team\":1},"
                + "{\"profile_id\":" + p2 + ",\"name\":\"Dos\",\"civ_name\":\"Goths\",\"team\":2}]}]}";
    }

    /** Ladder de una sola página (total=1, per_page=100 → ultimaPaginaLadder=1) con dos perfiles en rango. */
    static void ladderDeUnaPagina(FakeTransporte red, long ahora) {
        red.responder(urlLb("rm_1v1", 1), jsonLadder(1, 100, new long[]{111, 1500, ahora}, new long[]{222, 1550, ahora}));
    }

    /** El «río» (companion.recientes) sin nada nuevo: las 3 variantes de leaderboard, todas vacías. */
    static void rioVacio(FakeTransporte red) {
        red.responder(urlRecientes("rm_1v1", 1), SIN_PARTIDAS);
        red.responder(urlRecientes("3", 1), SIN_PARTIDAS);
        red.responder(urlRecientes(null, 1), SIN_PARTIDAS);
    }

    // ================================================================================================
    // ajustarRefAzar (estático, sin estado): el titular de una partida del azar
    // ================================================================================================

    private static MatchPlayer jugador(long id, String civ, Integer rating) {
        MatchPlayer p = new MatchPlayer(); p.id = id; p.civ = civ; p.rating = rating; return p;
    }

    @Test void ajustarRefAzar_sinFiltroDeCiv_eligeElDeMasElo() {
        Match m = new Match();
        m.players.add(jugador(1, "Franks", 1500));
        m.players.add(jugador(2, "Goths", 1800));
        AzarService.ajustarRefAzar(m, null);
        assertEquals(2, m.refId);
    }

    @Test void ajustarRefAzar_conFiltroDeCiv_eligeElQueLaJugo() {
        Match m = new Match();
        m.players.add(jugador(1, "Franks", 1900));   // más ELO, pero no jugó la civ filtrada
        m.players.add(jugador(2, "Goths", 1200));
        AzarService.ajustarRefAzar(m, "Goths");
        assertEquals(2, m.refId);
    }

    @Test void ajustarRefAzar_ningunoCumpleElFiltro_dejaAlPrimeroComoRespaldo() {
        Match m = new Match();
        m.players.add(jugador(1, "Franks", 1500));
        m.players.add(jugador(2, "Goths", 1800));
        AzarService.ajustarRefAzar(m, "Aztecs");   // ninguno jugó Aztecs
        assertEquals(1, m.refId);
    }

    // ================================================================================================
    // Estado inicial
    // ================================================================================================

    @Test void tramoAgotado_es_falso_antes_de_cualquier_busqueda() {
        AzarServiceCompanion s = servicio(new FakeTransporte(), sinMuestra());
        assertFalse(s.tramoAgotado());
    }

    // ================================================================================================
    // buscarAleatorias
    // ================================================================================================

    @Test void buscarAleatorias_sinCandidatosEnElLadder_noConsultaRioNiPerfiles() throws Exception {
        // Ladder de 2 páginas: página 1 con ELO alto (2000), página 2 con ELO bajo (900). Pedimos un rango
        // (1400-1600) que no cae en NINGUNA página: la bisección da pIni=2 > pFin=1 y sale sin más llamadas.
        FakeTransporte red = new FakeTransporte();
        red.responder(urlLb("rm_1v1", 1), jsonLadder(2, 1, new long[]{1, 2000, 0}));
        red.responder(urlLb("rm_1v1", 2), jsonLadder(2, 1, new long[]{2, 900, 0}));
        AzarServiceCompanion s = servicio(red, sinMuestra());
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        List<Match> res = s.buscarAleatorias(1400, 1600, null, null, 1, 1, cutoff, 1, m -> { });
        assertTrue(res.isEmpty());
        assertEquals(0, red.totalLlamadas() - red.llamadasA(urlLb("rm_1v1", 1)) - red.llamadasA(urlLb("rm_1v1", 2)),
                "no debería haber llamado a nada más que a las dos páginas del ladder");
    }

    @Test void buscarAleatorias_caminoFeliz_encuentraPartidaPorMuestreoDePerfiles() throws Exception {
        long ahora = Instant.now().getEpochSecond();
        FakeTransporte red = new FakeTransporte();
        ladderDeUnaPagina(red, ahora);
        rioVacio(red);
        red.responder(urlPartidas(111, 1), jsonUnaPartida1v1(999, 111, 222));
        red.responder(urlPartidas(222, 1), jsonUnaPartida1v1(999, 111, 222));
        AzarServiceCompanion s = servicio(red, sinMuestra());
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        List<Match> res = s.buscarAleatorias(1000, 2000, null, null, 1, 1, cutoff, 1, m -> { });
        assertEquals(1, res.size());
        assertEquals(999L, res.get(0).id);
        assertEquals(1, red.llamadasA(urlPartidas(111, 1)));
        assertEquals(1, red.llamadasA(urlPartidas(222, 1)));
        // los dos únicos perfiles del tramo quedaron consultados: no hay más candidatos → tramo agotado
        assertTrue(s.tramoAgotado());
    }

    @Test void buscarAleatorias_alContinuarEnLaMismaSesion_noRepitePerfilesYaConsultados() throws Exception {
        long ahora = Instant.now().getEpochSecond();
        FakeTransporte red = new FakeTransporte();
        ladderDeUnaPagina(red, ahora);
        rioVacio(red);
        red.responder(urlPartidas(111, 1), jsonUnaPartida1v1(999, 111, 222));
        red.responder(urlPartidas(222, 1), jsonUnaPartida1v1(999, 111, 222));
        AzarServiceCompanion s = servicio(red, sinMuestra());
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));

        List<Match> primera = s.buscarAleatorias(1000, 2000, null, null, 1, 1, cutoff, 1, m -> { });
        assertEquals(1, primera.size());
        assertEquals(1, red.llamadasA(urlPartidas(111, 1)));
        assertEquals(1, red.llamadasA(urlPartidas(222, 1)));

        // Misma sesión, mismos filtros: los dos perfiles siguen dentro del TTL de 10 min → no se vuelven a pedir.
        List<Match> segunda = s.buscarAleatorias(1000, 2000, null, null, 1, 1, cutoff, 2, m -> { });
        assertEquals(1, segunda.size(), "la partida ya en caché de sesión se sigue sirviendo");
        assertEquals(1, red.llamadasA(urlPartidas(111, 1)), "no se repite la consulta al mismo perfil");
        assertEquals(1, red.llamadasA(urlPartidas(222, 1)), "no se repite la consulta al mismo perfil");
        assertEquals(1, red.llamadasA(urlLb("rm_1v1", 1)), "la página del ladder ya cacheada no se repide");
    }

    @Test void buscarAleatorias_errorDeRedAlLeerUnPerfil_noRompeYDevuelveLoEncontrado() throws Exception {
        long ahora = Instant.now().getEpochSecond();
        FakeTransporte red = new FakeTransporte();
        // Un único perfil en rango, y su lectura de partidas siempre falla.
        red.responder(urlLb("rm_1v1", 1), jsonLadder(1, 100, new long[]{111, 1500, ahora}));
        rioVacio(red);
        red.fallar(urlPartidas(111, 1), "500 de prueba");
        AzarServiceCompanion s = servicio(red, sinMuestra());
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        List<Match> res = s.buscarAleatorias(1000, 2000, null, null, 1, 1, cutoff, 1, m -> { });
        assertTrue(res.isEmpty(), "el perfil nunca respondió: no hay partidas que devolver");
        // como el fallo pasa ANTES de marcar el perfil como visto, se reintenta en cada pasada (3, sin filtros)
        assertEquals(3, red.llamadasA(urlPartidas(111, 1)));
        assertFalse(s.tramoAgotado(), "el perfil nunca llegó a marcarse como visto: el tramo no está agotado");
    }

    @Test void buscarAleatorias_errorDeRedEnElLeaderboard_propagaLaExcepcion() {
        FakeTransporte red = new FakeTransporte();
        red.fallar(urlLb("rm_1v1", 1), "HTTP 500");
        red.fallar(urlLb("3", 1), "HTTP 500");
        AzarServiceCompanion s = servicio(red, sinMuestra());
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        assertThrows(IOException.class,
                () -> s.buscarAleatorias(1000, 2000, null, null, 1, 1, cutoff, 1, m -> { }));
    }

    @Test void buscarAleatorias_conMuestraNocturnaSuficiente_noTocaLaRed() throws Exception {
        List<List<Object>> partidas = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            partidas.add(partidaDeMuestra(1000 + i, 100 + i, 200 + i, "rm_arabia", 1, "Uno", "franks", 1500, 1, 2, "Dos", "goths", 1500, 0));
        Map<String, List<List<Object>>> muestra = Map.of("1500", partidas);
        FakeTransporte red = new FakeTransporte();   // no se registra NADA: cualquier llamada revienta el test
        AzarServiceCompanion s = servicio(red, () -> muestra);
        Instant cutoff = Instant.now().minus(Duration.ofHours(48));
        List<Match> res = s.buscarAleatorias(1000, 2000, null, null, 48, 1, cutoff, 1, m -> { });
        assertEquals(5, res.size());
        assertEquals(0, red.totalLlamadas());
        assertTrue(s.deMuestra(), "la tirada salió de la muestra nocturna: la vista lo avisa");
    }

    @Test void buscarAleatorias_conMenosDe24Horas_ignoraLaMuestraAunqueHayaDeSobra() throws Exception {
        AtomicInteger llamadasMuestra = new AtomicInteger();
        Supplier<Map<String, List<List<Object>>>> muestraEspia = () -> {
            llamadasMuestra.incrementAndGet();
            return Map.of();
        };
        long ahora = Instant.now().getEpochSecond();
        FakeTransporte red = new FakeTransporte();
        ladderDeUnaPagina(red, ahora);
        rioVacio(red);
        red.responder(urlPartidas(111, 1), SIN_PARTIDAS);
        red.responder(urlPartidas(222, 1), SIN_PARTIDAS);
        AzarServiceCompanion s = servicio(red, muestraEspia);
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        s.buscarAleatorias(1000, 2000, null, null, 1, 1, cutoff, 1, m -> { });   // hours=1 < 24
        assertEquals(0, llamadasMuestra.get(), "con menos de 24 h no se consulta la muestra nocturna");
        assertFalse(s.deMuestra(), "sin muestra, la tirada no se anuncia como de ayer");
    }

    // ================================================================================================
    // buscarGte
    // ================================================================================================

    static List<Object> partidaDeMuestra(long id, long startedEpoch, long finishedEpoch, String mapaClave,
                                          long p1, String n1, String civ1, long r1, long won1,
                                          long p2, String n2, String civ2, long r2, long won2) {
        return List.of((Object) id, (Object) startedEpoch, (Object) finishedEpoch, mapaClave,
                List.of(List.of((Object) p1, n1, civ1, r1, won1), List.of((Object) p2, n2, civ2, r2, won2)));
    }

    @Test void buscarGte_conMuestraDisponible_noConsultaElLadder() throws Exception {
        Map<String, List<List<Object>>> muestra = new HashMap<>();
        muestra.put("1500-2000", List.of(partidaDeMuestra(1, 10, 20, "rm_arabia", 1, "Uno", "franks", 1500, 1, 2, "Dos", "goths", 1600, 0)));
        muestra.put("0-1000", List.of(partidaDeMuestra(2, 30, 40, "rm_arena", 3, "Tres", "aztecs", 700, 1, 4, "Cuatro", "mayans", 650, 0)));
        FakeTransporte red = new FakeTransporte();
        AzarServiceCompanion s = servicio(red, () -> muestra);
        int base = dev.tirador.aoe2radar.cache.RecsDisco.maxGteEnDisco();
        List<Match> res = s.buscarGte(Instant.now().minus(Duration.ofHours(48)), m -> { });
        assertEquals(2, res.size());
        assertEquals(0, red.totalLlamadas());
        List<Integer> gtes = res.stream().map(m -> m.gte).sorted().toList();
        assertEquals(List.of(base + 1, base + 2), gtes);
    }

    @Test void buscarGte_sinMuestra_construyeUnaTandaDesdeElLadder() throws Exception {
        long ahora = Instant.now().getEpochSecond();
        FakeTransporte red = new FakeTransporte();
        // Único par de perfiles del ladder (una sola página: ultimaPaginaLadder = 1, así que las 5 franjas del
        // GTE, aunque sorteen páginas distintas, quedan todas pinzadas a la página 1 — ver AzarServiceCompanion).
        ladderDeUnaPagina(red, ahora);
        red.responder(urlPartidas(111, 1), jsonUnaPartida1v1(999, 111, 222));
        red.responder(urlPartidas(222, 1), jsonUnaPartida1v1(999, 111, 222));
        AzarServiceCompanion s = servicio(red, sinMuestra());
        int base = dev.tirador.aoe2radar.cache.RecsDisco.maxGteEnDisco();
        List<Match> res = s.buscarGte(Instant.now().minus(Duration.ofHours(48)), m -> { });
        assertEquals(1, res.size(), "los dos únicos perfiles del ladder solo dan para una partida (deduplicada)");
        assertEquals(999L, res.get(0).id);
        assertEquals(base + 1, res.get(0).gte);
    }

    /** Revisión 1.3 (textos): el «Muestreando el ladder… (n/5)» del GTE era el único progreso sin t(). */
    @Test void buscarGte_sinMuestra_elProgresoSaleEnElIdiomaDeLaApp() throws Exception {
        String idiomaPrevio = dev.tirador.aoe2radar.util.I18n.IDIOMA;
        dev.tirador.aoe2radar.util.I18n.IDIOMA = "en";
        try {
            long ahora = Instant.now().getEpochSecond();
            FakeTransporte red = new FakeTransporte();
            ladderDeUnaPagina(red, ahora);
            red.responder(urlPartidas(111, 1), jsonUnaPartida1v1(999, 111, 222));
            red.responder(urlPartidas(222, 1), jsonUnaPartida1v1(999, 111, 222));
            AzarServiceCompanion s = servicio(red, sinMuestra());
            List<String> progreso = new java.util.ArrayList<>();
            s.buscarGte(Instant.now().minus(Duration.ofHours(48)), progreso::add);
            assertTrue(progreso.stream().anyMatch(p -> p.startsWith("Sampling the ladder")), "progreso: " + progreso);
            assertTrue(progreso.stream().noneMatch(p -> p.startsWith("Muestreando")), "progreso: " + progreso);
        } finally {
            dev.tirador.aoe2radar.util.I18n.IDIOMA = idiomaPrevio;
        }
    }
}
