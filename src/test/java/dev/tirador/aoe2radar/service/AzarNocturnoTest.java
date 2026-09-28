package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.Match;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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

import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.FakeTransporte;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.SIN_PARTIDAS;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.ThrottleNoop;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.jsonUnaPartida1v1;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.ladderDeUnaPagina;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.partidaDeMuestra;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.rioVacio;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.urlLb;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.urlPartidas;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.4, «nocturno primero» en «Al azar» y «Guess the ELO»: el ladder de anoche (LadderNocturno, desde elo_ayer v2) en vez
 * de bisecar el leaderboard en vivo, y la muestra de anteayer. Sin red: el mismo Transporte falso que
 * AzarServiceCompanionTest (cualquier URL no registrada revienta el test).
 */
class AzarNocturnoTest {

    @AfterEach void limpiarVistasDeGte() throws Exception { Files.deleteIfExists(Path.of("config.properties")); }

    static AzarServiceCompanion servicio(FakeTransporte red, Supplier<Map<String, List<List<Object>>>> ayer,
                                         Supplier<Map<String, List<List<Object>>>> anteayer, Supplier<List<long[]>> ladder) {
        CompanionApi companion = new CompanionApi(new ApiClient(new ThrottleNoop(), red, ms -> { }, () -> false));
        return new AzarServiceCompanion(companion, civ -> civ, ayer, anteayer, ladder, ms -> { }, 50, 1);
    }

    static int llamadasAlLeaderboard(FakeTransporte red) {
        return red.llamadas.entrySet().stream().filter(e -> e.getKey().contains("/leaderboards/")).mapToInt(Map.Entry::getValue).sum();
    }

    /** 1.000 jugadores de anoche, 2500 → 1501 (10 páginas de 100), todos con partida hace una hora. */
    static List<long[]> ladderDeAnoche() {
        long hace1h = System.currentTimeMillis() - 3_600_000L;
        List<long[]> l = new ArrayList<>();
        for (int i = 0; i < 1000; i++) l.add(new long[]{ 10_000 + i, 2500 - i, hace1h });
        return l;
    }

    // ----- Al azar: el tramo sale del ladder de anoche -----------------------------------------------------

    @Test void conElLadderDeAnocheNoSePideNiUnaPaginaDelLeaderboard() throws Exception {
        List<long[]> ladder = ladderDeAnoche();
        FakeTransporte red = new FakeTransporte();
        for (long[] j : ladder) red.responder(urlPartidas(j[0], 1), SIN_PARTIDAS);
        // la única partida: dos jugadores del tramo 1920-1950 (pids 10550 = 1950 y 10560 = 1940), sin rating en la
        // respuesta del companion: el filtro usa el de anoche (como antes usaba el de las páginas del leaderboard)
        red.responder(urlPartidas(10_550, 1), jsonUnaPartida1v1(777, 10_550, 10_560));
        red.responder(urlPartidas(10_560, 1), jsonUnaPartida1v1(777, 10_550, 10_560));
        AtomicInteger pedidasAlLadder = new AtomicInteger();
        AzarServiceCompanion s = servicio(red, () -> null, () -> null, () -> { pedidasAlLadder.incrementAndGet(); return ladder; });
        Instant cutoff = Instant.now().minus(Duration.ofHours(3));
        List<Match> res = s.buscarAleatorias(1920, 1950, null, null, 3, 1, cutoff, 1, m -> { });
        assertEquals(0, llamadasAlLeaderboard(red), "ni bisección ni muestreo de páginas: el ladder es el de anoche");
        assertTrue(red.totalLlamadas() > 0, "las partidas de cada perfil siguen saliendo del companion");
        assertTrue(red.llamadas.keySet().stream().allMatch(u -> {
            long pid = Long.parseLong(u.replaceAll(".*profile_ids=(\\d+).*", "$1"));
            return pid >= 10_550 && pid <= 10_580;
        }), "solo se consultan perfiles del tramo 1920-1950 (pids 10550-10580)");
        assertEquals(1, res.size());
        assertEquals(777L, res.get(0).id);
        assertEquals(1, pedidasAlLadder.get(), "una sola lectura del ladder nocturno por contexto (10 min)");
    }

    /** Revisión: la última partida del volcado siempre es anterior a anoche. Con una ventana de 3 h, antes el tramo
     *  entero quedaba «inactivo» (0 perfiles consultados, tramo agotado); ahora cuenta como actividad desconocida. */
    @Test void unaVentanaCortaNoDejaElTramoSinPerfilesPorqueElVolcadoEsDeAnoche() throws Exception {
        long hace26h = System.currentTimeMillis() - 26 * 3_600_000L;
        List<long[]> ladder = new ArrayList<>();
        for (int i = 0; i < 1000; i++) ladder.add(new long[]{ 10_000 + i, 2500 - i, hace26h });
        FakeTransporte red = new FakeTransporte();
        for (long[] j : ladder) red.responder(urlPartidas(j[0], 1), SIN_PARTIDAS);
        AzarServiceCompanion s = servicio(red, () -> null, () -> null, () -> ladder);
        s.buscarAleatorias(1920, 1950, null, null, 3, 1, Instant.now().minus(Duration.ofHours(3)), 1, m -> { });
        assertTrue(red.totalLlamadas() > 0, "se consultan perfiles del tramo");
        assertEquals(0, llamadasAlLeaderboard(red));
    }

    @Test void unRangoFueraDelLadderDeAnocheSaleSinLlamadas() throws Exception {
        FakeTransporte red = new FakeTransporte();   // nada registrado
        AzarServiceCompanion s = servicio(red, () -> null, () -> null, AzarNocturnoTest::ladderDeAnoche);
        assertTrue(s.buscarAleatorias(3000, 3100, null, null, 3, 1, Instant.now().minus(Duration.ofHours(3)), 1, m -> { }).isEmpty());
        assertEquals(0, red.totalLlamadas(), "páginas fuera del ladder nocturno: vacías, nunca al companion");
    }

    @Test void sinDatosNuevosSigueConElLeaderboardEnVivo() throws Exception {
        long ahora = Instant.now().getEpochSecond();
        FakeTransporte red = new FakeTransporte();
        ladderDeUnaPagina(red, ahora);
        rioVacio(red);
        red.responder(urlPartidas(111, 1), SIN_PARTIDAS);
        red.responder(urlPartidas(222, 1), SIN_PARTIDAS);
        AzarServiceCompanion s = servicio(red, () -> null, () -> null, () -> null);   // elo_ayer de la 1.3: sin rangos
        s.buscarAleatorias(1000, 2000, null, null, 1, 1, Instant.now().minus(Duration.ofHours(1)), 1, m -> { });
        assertTrue(red.llamadasA(urlLb("rm_1v1", 1)) > 0, "como hasta ahora: la página 1 del leaderboard en vivo");
    }

    @Test void unFalloAlLeerElLadderDeAnocheCaeAlEnVivo() throws Exception {
        long ahora = Instant.now().getEpochSecond();
        FakeTransporte red = new FakeTransporte();
        ladderDeUnaPagina(red, ahora);
        rioVacio(red);
        red.responder(urlPartidas(111, 1), SIN_PARTIDAS);
        red.responder(urlPartidas(222, 1), SIN_PARTIDAS);
        AzarServiceCompanion s = servicio(red, () -> null, () -> null, () -> { throw new IllegalStateException("roto"); });
        s.buscarAleatorias(1000, 2000, null, null, 1, 1, Instant.now().minus(Duration.ofHours(1)), 1, m -> { });
        assertTrue(red.llamadasA(urlLb("rm_1v1", 1)) > 0);
    }

    // ----- Guess the ELO sin muestra: franjas del ladder de anoche -----------------------------------------

    @Test void gteSinMuestraUsaElLadderDeAnoche() throws Exception {
        List<long[]> ladder = ladderDeAnoche();
        FakeTransporte red = new FakeTransporte();
        for (long[] j : ladder) red.responder(urlPartidas(j[0], 1), jsonUnaPartida1v1(900_000 + j[0], j[0], j[0] + 1));
        AzarServiceCompanion s = servicio(red, () -> null, () -> null, () -> ladder);
        List<Match> res = s.buscarGte(Instant.now().minus(Duration.ofHours(48)), m -> { });
        assertEquals(0, llamadasAlLeaderboard(red));
        assertFalse(res.isEmpty());
        for (Match m : res) assertNotNull(m.players.get(0).rating, "el revelado lleva el ELO de anoche");
    }

    // ----- Muestra de anteayer -----------------------------------------------------------------------------

    static Map<String, List<List<Object>>> muestra(long idBase, int n) {
        List<List<Object>> l = new ArrayList<>();
        for (int i = 0; i < n; i++)
            l.add(partidaDeMuestra(idBase + i, 100, 200, "rm_arabia", idBase * 10 + 2 * i, "Uno", "franks", 1500, 1, idBase * 10 + 2 * i + 1, "Dos", "goths", 1500, 0));
        Map<String, List<List<Object>>> m = new HashMap<>();
        m.put("1400", l);
        return m;
    }

    @Test void conVentanaDe48HorasAnteayerCompletaLaDeAyer() throws Exception {
        FakeTransporte red = new FakeTransporte();   // nada registrado: cero llamadas
        AzarServiceCompanion s = servicio(red, () -> muestra(1000, 3), () -> muestra(2000, 20), () -> null);
        List<Match> res = s.buscarAleatorias(1000, 2000, null, null, 48, 1, Instant.now().minus(Duration.ofHours(48)), 1, m -> { });
        assertEquals(10, res.size());
        assertEquals(3, res.stream().filter(m -> m.id < 2000).count(), "primero todas las de ayer");
        assertTrue(s.deMuestra());
        assertEquals(0, red.totalLlamadas());
    }

    @Test void conVentanaDe24HorasAnteayerNoEntra() throws Exception {
        FakeTransporte red = new FakeTransporte();
        long ahora = Instant.now().getEpochSecond();
        ladderDeUnaPagina(red, ahora);
        rioVacio(red);
        red.responder(urlPartidas(111, 1), SIN_PARTIDAS);
        red.responder(urlPartidas(222, 1), SIN_PARTIDAS);
        AtomicInteger pedidasAnteayer = new AtomicInteger();
        AzarServiceCompanion s = servicio(red, () -> muestra(1000, 3), () -> { pedidasAnteayer.incrementAndGet(); return muestra(2000, 20); }, () -> null);
        s.buscarAleatorias(1000, 2000, null, null, 24, 1, Instant.now().minus(Duration.ofHours(24)), 1, m -> { });
        assertEquals(0, pedidasAnteayer.get(), "24 h: la ventana no llega a anteayer");
        assertFalse(s.deMuestra(), "3 de ayer no bastan: la tirada sigue como antes (API)");
    }

    @Test void conAyerSuficienteAnteayerNiSeMira() throws Exception {
        AtomicInteger pedidasAnteayer = new AtomicInteger();
        AzarServiceCompanion s = servicio(new FakeTransporte(), () -> muestra(1000, 20), () -> { pedidasAnteayer.incrementAndGet(); return muestra(2000, 20); }, () -> null);
        List<Match> res = s.buscarAleatorias(1000, 2000, null, null, 72, 1, Instant.now().minus(Duration.ofHours(72)), 1, m -> { });
        assertEquals(10, res.size());
        assertTrue(res.stream().allMatch(m -> m.id < 2000));
        assertEquals(0, pedidasAnteayer.get());
    }

    @Test void gteConAyerYaVistaSacaLaDeAnteayer() throws Exception {
        FakeTransporte red = new FakeTransporte();
        Map<String, List<List<Object>>> ayer = muestra(1000, 1), ant = muestra(2000, 1);
        AzarServiceCompanion s = servicio(red, () -> ayer, () -> ant, () -> null);
        List<Match> primera = s.buscarGte(Instant.now().minus(Duration.ofHours(48)), m -> { });
        assertEquals(1, primera.size());
        assertEquals(1000L, primera.get(0).id, "ayer primero");
        List<Match> segunda = s.buscarGte(Instant.now().minus(Duration.ofHours(48)), m -> { });
        assertEquals(1, segunda.size());
        assertEquals(2000L, segunda.get(0).id, "la de ayer ya se vio: sale la de anteayer, sin ir al ladder");
        assertEquals(0, red.totalLlamadas());
    }

    @Test void gteSoloConAnteayerTambienVale() throws Exception {
        AzarServiceCompanion s = servicio(new FakeTransporte(), () -> null, () -> muestra(2000, 1), () -> null);
        List<Match> res = s.buscarGte(Instant.now().minus(Duration.ofHours(48)), m -> { });
        assertEquals(1, res.size());
        assertEquals(2000L, res.get(0).id);
    }
}
