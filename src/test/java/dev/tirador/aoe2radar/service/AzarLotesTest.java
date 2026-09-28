package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
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
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.API;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.FakeTransporte;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.SIN_PARTIDAS;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.ThrottleNoop;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.jsonUnaPartida1v1;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.urlRecientes;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.4.1: el companion ya no admite /matches sin profile_ids (422 «profile_ids must be specified», 2026-09-28) y el
 * «río» de «Al azar» murió. Ahora: jugadores del tramo desde el ladder de anoche y sus partidas por lotes de hasta 10
 * (/matches?profile_ids=<csv>&per_page=100), con tope de llamadas; sin el ladder de anoche, un mensaje y ninguna
 * llamada. El Transporte falso responde 422 a las URL del río: si alguien las pidiera, el test lo vería.
 */
class AzarLotesTest {

    @AfterEach void limpiarVistasDeGte() throws Exception { Files.deleteIfExists(Path.of("config.properties")); }

    static final String CUERPO_422 = "{\"error\":\"profile_ids must be specified\"}";

    static FakeTransporte red() {
        FakeTransporte red = new FakeTransporte();
        red.responder(urlRecientes("rm_1v1", 1), 422, CUERPO_422);
        red.responder(urlRecientes("3", 1), 422, CUERPO_422);
        red.responder(urlRecientes(null, 1), 422, CUERPO_422);
        return red;
    }

    static int llamadasAlRio(FakeTransporte red) {
        return red.llamadasA(urlRecientes("rm_1v1", 1)) + red.llamadasA(urlRecientes("3", 1)) + red.llamadasA(urlRecientes(null, 1));
    }

    static int llamadasA(FakeTransporte red, String trozo) {
        return red.llamadas.entrySet().stream().filter(e -> e.getKey().contains(trozo)).mapToInt(Map.Entry::getValue).sum();
    }

    static String urlLote(String csv) { return API + "/matches?profile_ids=" + csv + "&page=1&per_page=100"; }

    static AzarServiceCompanion servicio(FakeTransporte red, List<long[]> ladder) {
        CompanionApi companion = new CompanionApi(new ApiClient(new ThrottleNoop(), red, ms -> { }, () -> false));
        return new AzarServiceCompanion(companion, civ -> civ, () -> null, () -> null, () -> ladder, ms -> { }, 50, 1);
    }

    /** n jugadores de anoche desde el pid dado, ELO de «desde» hacia abajo, última partida hace 26 h (anterior a cualquier
     *  ventana corta: el volcado es de anoche). */
    static List<long[]> ladder(long pid0, int n, int desde) {
        long hace26h = System.currentTimeMillis() - 26 * 3_600_000L;
        List<long[]> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new long[]{ pid0 + i, desde - i, hace26h });
        return l;
    }

    // ----- «Al azar» -----------------------------------------------------------------------------------------

    @Test void unTramoPequenoEsUnSoloLoteConLosIdsOrdenadosYNuncaElRio() throws Exception {
        FakeTransporte red = red();
        red.responder(urlLote("1,2,3,4,5"), jsonUnaPartida1v1(777, 1, 2));   // sin rating en la partida: vale el de anoche
        AzarServiceCompanion s = servicio(red, ladder(1, 5, 1504));
        List<Match> res = s.buscarAleatorias(1000, 2000, null, null, 3, 1, Instant.now().minus(Duration.ofHours(3)), 1, m -> { });
        assertEquals(0, llamadasAlRio(red), "el río (422) no se pide nunca");
        assertEquals(0, llamadasA(red, "/leaderboards/"), "el tramo sale del ladder de anoche");
        assertEquals(1, red.llamadasA(urlLote("1,2,3,4,5")), "los 5 del tramo en una llamada");
        assertEquals(1, red.totalLlamadas());
        assertEquals(1, res.size());
        assertEquals(777L, res.get(0).id);
        assertTrue(s.tramoAgotado(), "los 5 ya consultados: la siguiente pasada no tiene a quién preguntar");
    }

    @Test void conMuchosCandidatosHayTopeDeSeisLlamadasPorTirada() throws Exception {
        FakeTransporte red = red();
        red.lotesPorDefecto = url -> SIN_PARTIDAS;
        AzarServiceCompanion s = servicio(red, ladder(10_000, 1000, 2500));
        s.buscarAleatorias(1500, 2500, null, "Franks", 3, 1, Instant.now().minus(Duration.ofHours(3)), 1, m -> { });   // con civ: 2 pasadas de 40 = 8 lotes
        assertEquals(AzarServiceCompanion.MAX_LOTES, llamadasA(red, "/matches?profile_ids="), "tope: 6 × intensidad (1)");
        assertEquals(0, llamadasAlRio(red));
        assertTrue(red.llamadas.keySet().stream().filter(u -> u.contains("profile_ids=")).allMatch(u -> {
            String csv = u.replaceAll(".*profile_ids=([0-9,]+)&.*", "$1");
            return csv.split(",").length <= AzarServiceCompanion.LOTE && u.endsWith("&page=1&per_page=100");
        }), "lotes de hasta 10 y per_page=100");
    }

    @Test void elTopeCreceConLaIntensidad() throws Exception {
        FakeTransporte red = red();
        red.lotesPorDefecto = url -> SIN_PARTIDAS;
        AzarServiceCompanion s = servicio(red, ladder(10_000, 1000, 2500));
        s.buscarAleatorias(1500, 2500, null, "Franks", 3, 3, Instant.now().minus(Duration.ofHours(3)), 1, m -> { });   // 6 pasadas de 4 lotes = 24
        assertEquals(AzarServiceCompanion.MAX_LOTES * 3, llamadasA(red, "/matches?profile_ids="));
    }

    @Test void sinElLadderDeAnocheUnMensajeYNingunaLlamada() {
        FakeTransporte red = red();
        AzarServiceCompanion s = servicio(red, null);
        IOException ex = assertThrows(IOException.class,
                () -> s.buscarAleatorias(1000, 2000, null, null, 3, 1, Instant.now().minus(Duration.ofHours(3)), 1, m -> { }));
        assertTrue(ex.getMessage().contains("sfr-data"), ex.getMessage());
        assertEquals(0, red.totalLlamadas());
    }

    @Test void unRangoFueraDelLadderDeAnocheSaleSinLlamadas() throws Exception {
        FakeTransporte red = red();
        AzarServiceCompanion s = servicio(red, ladder(1, 100, 2500));
        assertTrue(s.buscarAleatorias(3000, 3100, null, null, 3, 1, Instant.now().minus(Duration.ofHours(3)), 1, m -> { }).isEmpty());
        assertEquals(0, red.totalLlamadas());
    }

    @Test void alRepetirEnLaSesionNoSeVuelveAPreguntarPorLosMismos() throws Exception {
        FakeTransporte red = red();
        red.lotesPorDefecto = url -> SIN_PARTIDAS;
        AzarServiceCompanion s = servicio(red, ladder(1, 25, 1600));
        Instant cutoff = Instant.now().minus(Duration.ofHours(3));
        s.buscarAleatorias(1000, 2000, null, null, 3, 1, cutoff, 1, m -> { });
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (String u : red.llamadas.keySet())
            if (u.contains("profile_ids=")) for (String id : u.replaceAll(".*profile_ids=([0-9,]+)&.*", "$1").split(",")) assertTrue(vistos.add(id), "sin repetir en la tirada: " + id);
        assertEquals(25, vistos.size(), "los 25 del tramo, en lotes (10 + 10 + 5)");
        int antes = red.totalLlamadas();
        s.buscarAleatorias(1000, 2000, null, null, 3, 1, cutoff, 2, m -> { });
        assertEquals(antes, red.totalLlamadas(), "en 10 min no se vuelve a preguntar por los mismos");
        assertTrue(s.tramoAgotado());
    }

    @Test void unLoteQueFallaNoMarcaVistosYCuentaParaElTope() throws Exception {
        FakeTransporte red = red();
        red.fallar(urlLote("1,2,3,4,5"), "HTTP 500");
        AzarServiceCompanion s = servicio(red, ladder(1, 5, 1504));
        List<Match> res = s.buscarAleatorias(1000, 2000, null, null, 3, 1, Instant.now().minus(Duration.ofHours(3)), 1, m -> { });
        assertTrue(res.isEmpty());
        assertEquals(3, red.llamadasA(urlLote("1,2,3,4,5")), "no quedan vistos: cada pasada (3) lo reintenta, dentro del tope");
        assertFalse(s.tramoAgotado(), "nunca respondieron: el tramo no está agotado");
    }

    // ----- Guess the ELO ---------------------------------------------------------------------------------------

    @Test void gteCadaSlotSeQuedaConLaPartidaDeSuJugador() throws Exception {
        FakeTransporte red = red();
        red.lotesPorDefecto = AzarLotesTest::partidasDelLote;
        AzarServiceCompanion s = servicio(red, ladder(10_000, 1000, 2500));
        List<Match> res = s.buscarGte(Instant.now().minus(Duration.ofHours(48)), m -> { });
        assertEquals(5, res.size(), "5 slots, cada uno con partidas de sus jugadores: 5 partidas distintas");
        assertEquals(5, res.stream().map(m -> m.id).distinct().count());
    }

    /** Solo el de pid más bajo de cada lote tiene partidas (uno muy activo acapara las 100). */
    static String soloElPrimero(String url) {
        String csv = url.replaceAll(".*profile_ids=([0-9,]+)&.*", "$1");
        return partidasDelLote(url.replace(csv, csv.split(",")[0]));
    }

    @Test void gteConUnSlotAcaparadoHaceUnaSegundaLlamadaParaLosDemasYNoMas() throws Exception {
        FakeTransporte red = red();
        red.lotesPorDefecto = AzarLotesTest::soloElPrimero;
        AzarServiceCompanion s = servicio(red, ladder(10_000, 1000, 2500));
        s.buscarGte(Instant.now().minus(Duration.ofHours(48)), m -> { });
        assertEquals(2, red.totalLlamadas(), "como mucho 2");
    }

    @Test void gteSiFallaLaLlamadaSeDiceElErrorNoQueNoHayNada() {
        FakeTransporte red = red();
        red.lotesPorDefecto = url -> "{}";
        red.codigoLotes = 500;
        AzarServiceCompanion s = servicio(red, ladder(10_000, 1000, 2500));
        IOException ex = assertThrows(IOException.class, () -> s.buscarGte(Instant.now().minus(Duration.ofHours(48)), m -> { }));
        assertTrue(ex.getMessage().contains("500"), ex.getMessage());
    }

    @Test void gteElProgresoSaleEnElIdiomaDeLaApp() throws Exception {
        String previo = dev.tirador.aoe2radar.util.I18n.IDIOMA;
        dev.tirador.aoe2radar.util.I18n.IDIOMA = "en";
        try {
            FakeTransporte red = red();
            red.lotesPorDefecto = AzarLotesTest::partidasDelLote;
            List<String> progreso = new ArrayList<>();
            servicio(red, ladder(10_000, 1000, 2500)).buscarGte(Instant.now().minus(Duration.ofHours(48)), progreso::add);
            assertTrue(progreso.stream().anyMatch(p -> p.startsWith("Sampling the ladder")), "progreso: " + progreso);
            assertTrue(progreso.stream().anyMatch(p -> p.startsWith("Reading games of")), "progreso: " + progreso);
            assertTrue(progreso.stream().noneMatch(p -> p.startsWith("Muestreando") || p.startsWith("Leyendo")), "progreso: " + progreso);
        } finally {
            dev.tirador.aoe2radar.util.I18n.IDIOMA = previo;
        }
    }

    /** Una partida reciente por cada jugador del lote (contra un rival de fuera), para que cada slot tenga la suya. */
    static String partidasDelLote(String url) {
        String csv = url.replaceAll(".*profile_ids=([0-9,]+)&.*", "$1");
        long ahora = Instant.now().getEpochSecond();
        StringBuilder b = new StringBuilder("{\"matches\":[");
        String[] ids = csv.split(",");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) b.append(',');
            long pid = Long.parseLong(ids[i]);
            b.append("{\"match_id\":").append(900_000 + pid).append(",\"started\":").append(ahora - 900).append(",\"finished\":").append(ahora - 600)
             .append(",\"leaderboard_name\":\"1v1 Random Map\",\"map_name\":\"Arabia\",\"players\":[")
             .append("{\"profile_id\":").append(pid).append(",\"name\":\"A\",\"civ_name\":\"Franks\",\"team\":1},")
             .append("{\"profile_id\":").append(pid + 1_000_000).append(",\"name\":\"B\",\"civ_name\":\"Goths\",\"team\":2}]}");
        }
        return b.append("]}").toString();
    }

    @Test void gteSinMuestraEsUnaSolaLlamadaParaLosCincoSlots() throws Exception {
        FakeTransporte red = red();
        red.lotesPorDefecto = AzarLotesTest::partidasDelLote;
        AzarServiceCompanion s = servicio(red, ladder(10_000, 1000, 2500));
        List<Match> res = s.buscarGte(Instant.now().minus(Duration.ofHours(48)), m -> { });
        assertEquals(1, red.totalLlamadas(), "antes, una por perfil (hasta 10)");
        assertEquals(0, llamadasAlRio(red));
        assertFalse(res.isEmpty());
        for (Match m : res) {
            MatchPlayer delLadder = m.players.stream().filter(p -> p.id < 1_000_000).findFirst().orElseThrow();
            assertNotNull(delLadder.rating, "el revelado lleva el ELO de anoche");
        }
    }

    @Test void gteSinMuestraNiLadderUnMensajeYNingunaLlamada() {
        FakeTransporte red = red();
        AzarServiceCompanion s = servicio(red, null);
        assertThrows(IOException.class, () -> s.buscarGte(Instant.now().minus(Duration.ofHours(48)), m -> { }));
        assertEquals(0, red.totalLlamadas());
    }
}
