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
        final Map<String, Integer> codigos = new HashMap<>();
        void responder(String url, String json) { respuestas.put(url, json); }
        /** Como el companion real desde 2026-09-28 con /matches sin profile_ids: 422 y ese cuerpo. */
        void responder(String url, int codigo, String json) { respuestas.put(url, json); codigos.put(url, codigo); }
        /** Respuesta para cualquier /matches?profile_ids=… no registrada (lotes con ids al azar); null: revienta. */
        java.util.function.Function<String, String> lotesPorDefecto;
        int codigoLotes = 200;   // el estado con el que responden esos lotes (500: el companion falla)
        void fallar(String url, String mensaje) { fallos.put(url, new IOException(mensaje)); }
        @Override public Respuesta get(String url) throws IOException {
            llamadas.merge(url, 1, Integer::sum);
            IOException fallo = fallos.get(url);
            if (fallo != null) throw fallo;
            String json = respuestas.get(url);
            if (json == null && lotesPorDefecto != null && url.contains("/matches?profile_ids=")) {
                json = lotesPorDefecto.apply(url);
                if (codigoLotes != 200) return new Respuesta(codigoLotes, json);
            }
            if (json == null) throw new AssertionError("URL no esperada en el test: " + url);
            return new Respuesta(codigos.getOrDefault(url, 200), json);
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
        FakeTransporte red = new FakeTransporte();
        AzarServiceCompanion s = servicio(red, muestraEspia);   // sin ladder nocturno: la tirada lo dice y no llama
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        assertThrows(IOException.class, () -> s.buscarAleatorias(1000, 2000, null, null, 1, 1, cutoff, 1, m -> { }));   // hours=1 < 24
        assertEquals(0, red.totalLlamadas());
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
}
