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
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.ThrottleNoop;
import static dev.tirador.aoe2radar.service.AzarServiceCompanionTest.partidaDeMuestra;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.4, «nocturno primero» en «Al azar» y «Guess the ELO»: la muestra de anteayer (el ladder de anoche y los lotes,
 * en AzarLotesTest). Sin red: el mismo Transporte falso que
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
        assertTrue(s.conAnteayer(), "el aviso dirá «de ayer y anteayer»");
        assertEquals(0, red.totalLlamadas());
    }

    @Test void conVentanaDe24HorasAnteayerNoEntra() throws Exception {
        FakeTransporte red = new FakeTransporte();
        AtomicInteger pedidasAnteayer = new AtomicInteger();
        AzarServiceCompanion s = servicio(red, () -> muestra(1000, 3), () -> { pedidasAnteayer.incrementAndGet(); return muestra(2000, 20); }, () -> null);
        assertThrows(java.io.IOException.class,   // sin ladder nocturno, lo que no da la muestra ya no se busca (1.4.1)
                () -> s.buscarAleatorias(1000, 2000, null, null, 24, 1, Instant.now().minus(Duration.ofHours(24)), 1, m -> { }));
        assertEquals(0, pedidasAnteayer.get(), "24 h: la ventana no llega a anteayer");
        assertFalse(s.deMuestra(), "3 de ayer no bastan y sin ladder de anoche no se busca más: la tirada no sale de la muestra");
        assertFalse(s.conAnteayer());
    }

    @Test void conAyerSuficienteAnteayerNiSeMira() throws Exception {
        AtomicInteger pedidasAnteayer = new AtomicInteger();
        AzarServiceCompanion s = servicio(new FakeTransporte(), () -> muestra(1000, 20), () -> { pedidasAnteayer.incrementAndGet(); return muestra(2000, 20); }, () -> null);
        List<Match> res = s.buscarAleatorias(1000, 2000, null, null, 72, 1, Instant.now().minus(Duration.ofHours(72)), 1, m -> { });
        assertEquals(10, res.size());
        assertTrue(res.stream().allMatch(m -> m.id < 2000));
        assertEquals(0, pedidasAnteayer.get());
        assertFalse(s.conAnteayer(), "todas de ayer: el aviso de siempre");
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
