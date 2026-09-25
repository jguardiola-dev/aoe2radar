package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.model.LadderHist;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.sfrdata.Ladder;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los tops de la watchlist (TopLadderService), copiados literales de cargarTopLadder/guardarTopCache/
 * cargarTopCache/cargarTopClan/vigilarTop (ver {@code git show HEAD~1:.../SpoilerFreeRecs.java}): tests de
 * caracterización, sin red (Transporte falso) y con reloj fijo (util.RelojFalso, HORA = 1.700.000.000.000 ms, nov.
 * 2023: Json.when lee como milisegundos los números por encima de 1e11).
 *
 * <p>topClan necesita que sfrdata.Ladder.ladderAsegurar no vaya a la red: como esa clase es un singleton estático sin
 * puerto de inyección (queda fuera de este encargo), se fuerza su «sello» a fresco por reflexión, solo en este test,
 * y se limpia después (ver marcarLadderFresco/limpiarLadder). Es la única concesión: ver DEUDA.
 */
class TopLadderServiceTest {
    static final long HORA = 1_700_000_000_000L;   // nov. 2023, en ms: por encima de 1e11 (Json.when las lee en ms)

    // ----- red falsa: responde por subcadena de la URL; sin coincidencia, una respuesta vacía -----
    static final class TransporteFalso implements Transporte {
        final Map<String, String> cuerpos = new HashMap<>();
        final Map<String, Integer> estados = new HashMap<>();
        final List<String> pedidas = new ArrayList<>();
        @Override public Respuesta get(String url) {
            pedidas.add(url);
            for (Map.Entry<String, String> e : cuerpos.entrySet())
                if (url.contains(e.getKey())) return new Respuesta(estados.getOrDefault(e.getKey(), 200), e.getValue());
            return new Respuesta(200, "{\"matches\":[],\"players\":[]}");
        }
        void responder(String contiene, String cuerpo) { cuerpos.put(contiene, cuerpo); }
        void fallar(String contiene) { estados.put(contiene, 500); cuerpos.putIfAbsent(contiene, "{}"); }
    }

    static final class ThrottleSinFreno implements Throttle {
        @Override public void adquirir(BooleanSupplier cancelar) { }
        @Override public long registrar429() { return 0; }
    }

    final TransporteFalso red = new TransporteFalso();
    final CompanionApi api = new CompanionApi(new ApiClient(new ThrottleSinFreno(), red, s -> { }, () -> false));
    final RelojFalso reloj = new RelojFalso();
    { reloj.ahora = HORA; }
    final List<Long> pausas = new ArrayList<>();
    final LongConsumer pausa = pausas::add;
    static final long PAUSA_MS = 300;

    TopLadderService nuevo() { return new TopLadderService(api, reloj, pausa, PAUSA_MS); }

    // ===================== helpers de JSON =====================

    String jugadorLb(long pid, String nombre, int rating, long ultimaMs) {
        return "{\"profile_id\":" + pid + ",\"name\":\"" + nombre + "\",\"rating\":" + rating + ",\"last_match_time\":" + ultimaMs + "}";
    }
    String jugadorLbConRachaYUltimas(long pid, String nombre, int rating, long ultimaMs, int racha, int ganadas10, int jugadas10) {
        StringBuilder l10 = new StringBuilder("[");
        for (int i = 0; i < jugadas10; i++) { if (i > 0) l10.append(','); l10.append(i < ganadas10 ? "true" : "false"); }
        l10.append(']');
        return "{\"profile_id\":" + pid + ",\"name\":\"" + nombre + "\",\"rating\":" + rating + ",\"last_match_time\":" + ultimaMs
                + ",\"streak\":" + racha + ",\"last10MatchesWon\":" + l10 + "}";
    }
    String leaderboard(String... jugadores) { return "{\"players\":[" + String.join(",", jugadores) + "]}"; }

    String partidaEnCurso(long matchId, long haceMs, long... pids) {
        StringBuilder ps = new StringBuilder();
        int team = 1;
        for (long pid : pids) { if (ps.length() > 0) ps.append(','); ps.append("{\"profile_id\":").append(pid).append(",\"name\":\"J").append(pid).append("\",\"team\":").append(team++).append('}'); }
        return "{\"match_id\":" + matchId + ",\"started\":" + (HORA - haceMs) + ",\"players\":[" + ps + "]}";
    }
    String partidaTerminada(long matchId, long haceMs, long pid) {
        long ini = HORA - haceMs;
        return "{\"match_id\":" + matchId + ",\"started\":" + ini + ",\"finished\":" + (ini + 60_000)
                + ",\"players\":[{\"profile_id\":" + pid + ",\"name\":\"J" + pid + "\",\"team\":1}]}";
    }
    String matches(String... partidas) { return "{\"matches\":[" + String.join(",", partidas) + "]}"; }

    // ===================== cargarTop: top nuevo =====================

    @Test void topNuevo_construyeFilasYAprendeRachaYUltimas10() {
        red.responder("/leaderboards/rm_1v1", leaderboard(
                jugadorLbConRachaYUltimas(1L, "Ana", 2000, HORA, 3, 5, 7),
                jugadorLbConRachaYUltimas(2L, "Beto", 1800, HORA - 60_000, -2, 1, 4)));
        TopLadderService.ResultadoTop res = nuevo().cargarTop(null, 50);
        assertEquals(List.of(new TopLadderService.FilaTop(1L, "Ana", 2000, HORA), new TopLadderService.FilaTop(2L, "Beto", 1800, HORA - 60_000)), res.filas());
        assertEquals(3, res.racha().get(1L));
        assertEquals(-2, res.racha().get(2L));
        assertArrayEquals(new int[]{ 5, 2 }, res.ultimas10().get(1L), "5 ganadas de 7: {ganadas, perdidas}");
        assertArrayEquals(new int[]{ 1, 3 }, res.ultimas10().get(2L), "1 ganada de 4: {ganadas, perdidas}");
    }

    @Test void siRm1v1NoTraeNadaPruebaConElLadderDeEquipos() {
        red.responder("/leaderboards/rm_1v1", leaderboard());
        red.responder("/leaderboards/3", leaderboard(jugadorLb(9L, "Caro", 1500, HORA)));
        TopLadderService.ResultadoTop res = nuevo().cargarTop(null, 50);
        assertEquals(1, res.filas().size());
        assertEquals(9L, res.filas().get(0).pid());
    }

    @Test void siRm1v1FallaPruebaConElLadderDeEquipos() {
        red.fallar("/leaderboards/rm_1v1");
        red.responder("/leaderboards/3", leaderboard(jugadorLb(9L, "Caro", 1500, HORA)));
        TopLadderService.ResultadoTop res = nuevo().cargarTop(null, 50);
        assertEquals(1, res.filas().size(), "el fallo de rm_1v1 no impide probar con el ladder de equipos");
    }

    @Test void seParaAlLlegarATopN() {
        red.responder("/leaderboards/rm_1v1", leaderboard(
                jugadorLb(1L, "A", 2000, HORA), jugadorLb(2L, "B", 1900, HORA), jugadorLb(3L, "C", 1800, HORA)));
        TopLadderService.ResultadoTop res = nuevo().cargarTop(null, 2);
        assertEquals(2, res.filas().size());
    }

    @Test void siNoHayResultadosNiEnRmNiEnEquiposLasFilasQuedanVacias() {
        red.responder("/leaderboards/rm_1v1", leaderboard());
        red.responder("/leaderboards/3", leaderboard());
        TopLadderService.ResultadoTop res = nuevo().cargarTop(null, 50);
        assertTrue(res.filas().isEmpty());
    }

    // ===================== topFresco: caché de 10 minutos =====================

    @Test void topFresco_justoAntesDeLaFronteraEsFresco() {
        long cargado = HORA - (10 * 60_000L - 1);
        assertTrue(nuevo().topFresco(false, "global", "global", true, cargado));
    }

    @Test void topFresco_enLaFronteraExactaYaNoEsFresco() {
        long cargado = HORA - 10 * 60_000L;   // exactamente 10 minutos: la 1.1 usa "<", no "<="
        assertFalse(nuevo().topFresco(false, "global", "global", true, cargado));
    }

    @Test void topFresco_forzarSiempreDaFalso() {
        assertFalse(nuevo().topFresco(true, "global", "global", true, HORA));
    }

    @Test void topFresco_firmaDistintaDaFalso() {
        assertFalse(nuevo().topFresco(false, "es", "global", true, HORA));
    }

    @Test void topFresco_sinDatosDaFalso() {
        assertFalse(nuevo().topFresco(false, "global", "global", false, HORA));
    }

    // ===================== caché de disco =====================

    @Test void guardarYLeerLaCache(@TempDir Path dir) {
        Path cache = dir.resolve("top_cache.txt");
        TopLadderService s = nuevo();
        List<TopLadderService.FilaCache> filas = List.of(
                new TopLadderService.FilaCache(1L, "Ana", 2000, HORA),
                new TopLadderService.FilaCache(2L, "Beto", 0, HORA - 1000));   // elo 0: sin ELO conocido
        s.guardarCache(cache, "global", HORA, filas);
        TopLadderService.TopCache leida = s.cargarCache(cache, "global");
        assertEquals(HORA, leida.cargadoMs());
        assertEquals(filas, leida.filas());
    }

    @Test void cargarCache_conFirmaDistintaDaNull(@TempDir Path dir) {
        Path cache = dir.resolve("top_cache.txt");
        TopLadderService s = nuevo();
        s.guardarCache(cache, "global", HORA, List.of(new TopLadderService.FilaCache(1L, "Ana", 2000, HORA)));
        assertNull(s.cargarCache(cache, "es"));
    }

    @Test void cargarCache_sinArchivoDaNull(@TempDir Path dir) {
        assertNull(nuevo().cargarCache(dir.resolve("no_existe.txt"), "global"));
    }

    @Test void fallaElServicio_sirveLaCacheDeDisco(@TempDir Path dir) {
        Path cache = dir.resolve("top_cache.txt");
        TopLadderService s = nuevo();
        s.guardarCache(cache, "global", HORA - 3600_000L, List.of(new TopLadderService.FilaCache(5L, "Duna", 1700, HORA - 3600_000L)));
        red.fallar("/leaderboards/rm_1v1");
        red.fallar("/leaderboards/3");
        TopLadderService.ResultadoTop res = s.cargarTop(null, 50);
        assertTrue(res.filas().isEmpty(), "los dos ladders fallaron: sin filas nuevas");
        TopLadderService.TopCache leida = s.cargarCache(cache, "global");
        assertEquals(1, leida.filas().size(), "la app cae a la caché de disco cuando el servicio no responde");
        assertEquals(5L, leida.filas().get(0).pid());
    }

    // ===================== ★ Top clan =====================

    @AfterEach void limpiarLadder() throws Exception {
        Ladder.clanes = Map.of();
        Ladder.ladderHists = Map.of();
        Field f = Ladder.class.getDeclaredField("LADDER_SELLO");
        f.setAccessible(true);
        Object sello = f.get(null);
        sello.getClass().getMethod("marcar", long.class).invoke(sello, 0L);   // muy viejo: vuelve a "no fresco"
    }

    /** sfrdata.Ladder es un singleton estático sin puerto de inyección (fuera de este encargo): se fuerza su
     *  «sello» a fresco por reflexión para que ladderAsegurar no vaya a la red en el test (ver DEUDA). */
    static void marcarLadderFresco() throws Exception {
        Ladder.ladderHists = Map.of("rm_1v1", new LadderHist(1, 0, new int[]{ 1 }, 0, Map.of()));
        Field f = Ladder.class.getDeclaredField("LADDER_SELLO");
        f.setAccessible(true);
        Object sello = f.get(null);
        sello.getClass().getMethod("marcar").invoke(sello);
    }

    @Test void topClan_construyeLaListaDesdeLosMiembros() throws Exception {
        marcarLadderFresco();
        Ladder.clanes = Map.of("R1", List.of(
                new LadderRow(1L, "Ana", 2000, 10, "es", "R1", 500),
                new LadderRow(2L, "Beto", 1800, 20, "fr", "R1", 300)));
        TopLadderService.ResultadoClan res = nuevo().topClan("R1");
        assertNull(res.error());
        assertEquals(List.of(new TopLadderService.FilaClan(1L, "Ana", 2000), new TopLadderService.FilaClan(2L, "Beto", 1800)), res.miembros());
    }

    @Test void topClan_tagQueNoExisteDaListaVaciaSinError() throws Exception {
        marcarLadderFresco();
        Ladder.clanes = Map.of("R1", List.of(new LadderRow(1L, "Ana", 2000, 10, "es", "R1", 500)));
        TopLadderService.ResultadoClan res = nuevo().topClan("NoExiste");
        assertNull(res.error());
        assertTrue(res.miembros().isEmpty());
    }

    // ===================== vigilarTop =====================

    List<Player> jugadores(long... ids) {
        List<Player> l = new ArrayList<>();
        for (long id : ids) l.add(new Player(id, "J" + id, "★ Top ladder"));
        return l;
    }

    /** Callback del río: apunta lo recibido, como haría la app (ponerInfo/avisarSiCampana/guardarPartida). */
    static final class Avisos {
        final Map<Long, Match> rio = new HashMap<>();
        final Map<Long, Match> confirmados = new HashMap<>();
    }

    @Test void vigilarTop_vivoEnElLote() {
        List<Player> top = jugadores(1L, 2L);
        red.responder("per_page=100", matches(partidaEnCurso(500L, 60_000, 1L, 99L)));
        Avisos av = new Avisos();
        TopLadderService.ResultadoVigilancia r = nuevo().vigilarTop(top, pid -> false, pid -> null,
                (pid, m) -> av.rio.put(pid, m), (pid, m) -> av.confirmados.put(pid, m));
        assertEquals(Map.of(1L, 500L), r.resultado());
        assertEquals(Set.of(1L, 2L), r.verificados());
        assertEquals(500L, av.rio.get(1L).id);
        assertTrue(av.confirmados.isEmpty());
    }

    @Test void vigilarTop_vivoConfirmadoIndividualmente() {
        List<Player> top = jugadores(77L);
        red.responder("per_page=100", matches());                                    // el río no lo trae
        red.responder("per_page=3", matches(partidaEnCurso(600L, 30_000, 77L)));      // pero sigue en curso
        Avisos av = new Avisos();
        TopLadderService.ResultadoVigilancia r = nuevo().vigilarTop(top, pid -> true, pid -> null,
                (pid, m) -> av.rio.put(pid, m), (pid, m) -> av.confirmados.put(pid, m));
        assertEquals(Map.of(77L, 600L), r.resultado());
        assertEquals(Set.of(77L), r.verificados());
        assertEquals(600L, av.confirmados.get(77L).id);
        assertTrue(av.rio.isEmpty());
    }

    @Test void vigilarTop_terminadoDeVerdad() {
        List<Player> top = jugadores(77L);
        red.responder("per_page=100", matches());                                     // el río no lo trae
        red.responder("per_page=3", matches(partidaTerminada(601L, 30_000, 77L)));     // y ya terminó
        Avisos av = new Avisos();
        TopLadderService.ResultadoVigilancia r = nuevo().vigilarTop(top, pid -> true, pid -> null,
                (pid, m) -> av.rio.put(pid, m), (pid, m) -> av.confirmados.put(pid, m));
        assertTrue(r.resultado().isEmpty(), "sin punto: la app marcará fuera");
        assertEquals(Set.of(77L), r.verificados());
        assertTrue(av.confirmados.isEmpty());
    }

    @Test void vigilarTop_loteFallidoNoTocaALosNoVerificados() {
        List<Player> top = jugadores(1L, 2L);
        red.fallar("per_page=100");   // el único lote falla
        Avisos av = new Avisos();
        TopLadderService.ResultadoVigilancia r = nuevo().vigilarTop(top, pid -> true, pid -> null,
                (pid, m) -> av.rio.put(pid, m), (pid, m) -> av.confirmados.put(pid, m));
        assertTrue(r.resultado().isEmpty());
        assertTrue(r.verificados().isEmpty(), "lote fallido: nadie queda verificado");
        assertTrue(av.rio.isEmpty());
        assertTrue(av.confirmados.isEmpty(), "sin verificar, la confirmación individual tampoco se intenta");
    }
}
