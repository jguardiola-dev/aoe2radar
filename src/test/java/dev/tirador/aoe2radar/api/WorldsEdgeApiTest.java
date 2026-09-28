package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.model.Clasificacion;
import dev.tirador.aoe2radar.model.FilaClasificacion;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.PaginaPartidas;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.PerfilEncontrado;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Caracterización de WorldsEdgeApi sobre respuestas REALES guardadas (src/test/resources/fixture/worldsedge, grabadas
 * una vez con tools/grabar_worldsedge.py el 2026-09-28). Sin red: un transporte falso responde el archivo según la URL.
 * Si World's Edge cambia de formato, se vuelve a grabar y estos tests dicen qué se rompió.
 */
class WorldsEdgeApiTest {

    static String fixture(String nombre) {
        try (InputStream in = WorldsEdgeApiTest.class.getResourceAsStream("/fixture/worldsedge/" + nombre)) {
            assertNotNull(in, nombre);
            byte[] b = nombre.endsWith(".gz") ? new GZIPInputStream(in).readAllBytes() : in.readAllBytes();
            return new String(b, StandardCharsets.UTF_8);
        } catch (IOException e) { throw new AssertionError(e); }
    }

    /** Responde por el trozo de URL que contenga (el primero que encaje); apunta todas las URL pedidas. */
    static final class TransporteFixtures implements Transporte {
        final Map<String, String> respuestas = new LinkedHashMap<>();
        final List<String> pedidas = new ArrayList<>();
        @Override public Respuesta get(String url) {
            pedidas.add(url);
            for (Map.Entry<String, String> e : respuestas.entrySet())
                if (url.contains(e.getKey())) return new Respuesta(200, e.getValue());
            return new Respuesta(503, "");
        }
        long cuantas(String trozo) { return pedidas.stream().filter(u -> u.contains(trozo)).count(); }
    }

    final TransporteFixtures red = new TransporteFixtures();
    final ApiClientTest.ThrottleEspia throttle = new ApiClientTest.ThrottleEspia();
    final WorldsEdgeApi we = new WorldsEdgeApi(new ApiClient(throttle, red, s -> { }, () -> false));

    WorldsEdgeApiTest() {
        red.respuestas.put("getAvailableLeaderboards", fixture("available_leaderboards.json"));
        red.respuestas.put("getRecentMatchHistory?title=age2&profile_ids=%5B271202,199325%5D", fixture("recent_history_varios.json.gz"));
        red.respuestas.put("getRecentMatchHistory?title=age2&profile_ids=%5B271202%5D", fixture("recent_history.json.gz"));
        red.respuestas.put("getMatchHistory", fixture("match_history.json"));
        red.respuestas.put("getLeaderBoard2", fixture("leaderboard_top.json"));
        red.respuestas.put("aliases=", fixture("alias.json"));
        red.respuestas.put("getPersonalStat", fixture("personal_stat.json"));
    }

    static MatchPlayer jugador(Match m, long pid) {
        for (MatchPlayer p : m.players) if (p.id == pid) return p;
        throw new AssertionError("sin jugador " + pid);
    }

    // ----- Partidas

    @Test void recientesOrdenadasDeMasNuevaAMasViejaYCortadasAPorPagina() throws Exception {
        PaginaPartidas p = we.pagina(271202, 1, 20);
        assertEquals(20, p.partidas().size());
        assertEquals(20, p.brutas());
        assertEquals(509637140L, p.partidas().get(0).id, "la más reciente, aunque la API la dé en otro orden");
        for (int i = 1; i < p.partidas().size(); i++)
            assertFalse(p.partidas().get(i).started.isAfter(p.partidas().get(i - 1).started));
        assertTrue(red.pedidas.contains("https://aoe-api.worldsedgelink.com/community/leaderboard/getRecentMatchHistory?title=age2&profile_ids=%5B271202%5D"));
    }

    @Test void todasLasRecientesSiSePidenMas() throws Exception {
        PaginaPartidas p = we.pagina(271202, 1, 500);
        assertEquals(118, p.partidas().size(), "~10 por tipo de partida, no más");
    }

    @Test void unaPartidaClasificatoriaComoLaDaElCompanion() throws Exception {
        Match m = null;
        for (Match x : we.partidas(271202L, 1, 50)) if (x.id == 508450267L) m = x;
        assertNotNull(m);
        assertEquals(Instant.ofEpochSecond(1790093050L), m.started);
        assertEquals(Instant.ofEpochSecond(1790094664L), m.finished);
        assertEquals("Arena", m.map);
        assertEquals("1v1 Random Map", m.mode);
        assertEquals(2, m.players.size());
        MatchPlayer yo = jugador(m, 271202), rival = jugador(m, 1531083);
        assertEquals("Oni.Vinchester", yo.name);
        assertEquals("Koreans", yo.civ);
        assertEquals(1, yo.team);
        assertEquals(2997, yo.rating, "el de antes de la partida");
        assertEquals(5, yo.ratingDiff);
        assertEquals(Boolean.TRUE, yo.won);
        assertEquals(Boolean.TRUE, yo.replay);
        assertEquals("Oni.FreakinAndy", rival.name);
        assertEquals("Aztecs", rival.civ);
        assertEquals(2, rival.team);
        assertEquals(-5, rival.ratingDiff);
        assertEquals(Boolean.FALSE, rival.won);
        assertNull(yo.color); assertNull(yo.slot); assertNull(yo.social);
        assertSame(yo, m.players.get(0), "por equipos");
    }

    @Test void variosJugadoresEnUnaLlamada() throws Exception {
        List<Match> ms = new ArrayList<>();
        for (Match m : we.partidas("271202,199325", 1, 10)) ms.add(m);
        assertEquals(10, ms.size());
        assertEquals(509735210L, ms.get(0).id);
        assertEquals(1, red.cuantas("getRecentMatchHistory"));
    }

    @Test void laPaginaDosNoExisteYNoVaALaRed() throws Exception {
        PaginaPartidas p = we.pagina(271202, 2, 50);
        assertTrue(p.partidas().isEmpty());
        assertEquals(0, p.brutas());
        assertFalse(we.partidas(271202L, 3, 50).iterator().hasNext());
        assertTrue(red.pedidas.isEmpty());
    }

    @Test void elCatalogoDeCivsSePideUnaVezPorSesion() throws Exception {
        we.pagina(271202, 1, 5);
        we.pagina(271202, 1, 5);
        we.partida(508450267L);
        assertEquals(1, red.cuantas("getAvailableLeaderboards"));
    }

    @Test void sinCatalogoDeCivsLaPartidaSigueSirviendoYSeReintenta() throws Exception {
        red.respuestas.remove("getAvailableLeaderboards");   // 503
        Match m = we.pagina(271202, 1, 1).partidas().get(0);
        assertEquals(509637140L, m.id);
        for (MatchPlayer p : m.players) assertNull(p.civ);
        we.pagina(271202, 1, 1);
        assertEquals(2, red.cuantas("getAvailableLeaderboards"), "un fallo no se guarda");
    }

    @Test void partidaPorId() throws Exception {
        Match m = we.partida(508450267L);
        assertNotNull(m);
        assertEquals("Arena", m.map);
        assertEquals("1v1 Random Map", m.mode);
        assertEquals("Oni.FreakinAndy", jugador(m, 1531083).name);
        assertTrue(red.pedidas.contains("https://aoe-api.worldsedgelink.com/community/leaderboard/getMatchHistory?title=age2&matchIDs=%5B508450267%5D"));
        assertNull(we.partida(1L), "otra partida (la respuesta no la trae): null");
    }

    @Test void mapaPersonalizadoQuedaSinNombre() throws Exception {
        Match m = we.pagina(271202, 1, 1).partidas().get(0);   // «my map», sin clasificar
        assertEquals("?", m.map);
        assertEquals("Unranked", m.mode);
    }

    @Test void nombresDeMapa() {
        assertEquals("Arabia", WorldsEdgeApi.nombreMapa("Arabia.rms"));
        assertEquals("Black Forest", WorldsEdgeApi.nombreMapa("Black_Forest.rms"));
        assertEquals("African Clearing", WorldsEdgeApi.nombreMapa("AfricanClearing.rms"));
        assertEquals("Fortified Clearing", WorldsEdgeApi.nombreMapa("fortified_clearing.rms"));
        assertEquals("Acropolis", WorldsEdgeApi.nombreMapa("acropolis.rms2"));
        assertEquals("Loch Ness", WorldsEdgeApi.nombreMapa("Loch Ness.rms"));
        assertNull(WorldsEdgeApi.nombreMapa("my map"));
        assertNull(WorldsEdgeApi.nombreMapa(null));
        assertNull(WorldsEdgeApi.nombreMapa(" "));
    }

    @Test void modos() {
        assertEquals("1v1 Random Map", WorldsEdgeApi.modo(6));
        assertEquals("Team Random Map", WorldsEdgeApi.modo(8));
        assertEquals("1v1 Empire Wars", WorldsEdgeApi.modo(26));
        assertEquals("Team Empire Wars", WorldsEdgeApi.modo(29));
        assertEquals("1v1 Deathmatch", WorldsEdgeApi.modo(60));
        assertEquals("Team Deathmatch", WorldsEdgeApi.modo(61));
        assertNull(WorldsEdgeApi.modo(122), "El Reinado: sin equivalente");
    }

    // ----- Sobre

    @Test void unCodigoDistintoDeCeroEsUnError() {
        IOException e = assertThrows(IOException.class, () -> WorldsEdgeApi.sobre(fixture("alias_desconocido.json")));
        assertTrue(e.getMessage().contains("UNKNOWN_ALIASES"), e.getMessage());
        assertThrows(IOException.class, () -> WorldsEdgeApi.sobre("{\"otra\":1}"), "sin sobre: formato cambiado");
    }

    @Test void unHttpDeErrorEsUnError() {
        red.respuestas.clear();   // 503
        assertThrows(IOException.class, () -> we.perfil(271202));
    }

    // ----- Perfil

    @Test void perfil() throws Exception {
        Perfil p = we.perfil(271202);
        assertTrue(red.pedidas.contains("https://aoe-api.worldsedgelink.com/community/leaderboard/getPersonalStat?title=age2&profile_ids=%5B271202%5D"));
        assertEquals("ru", p.pais());
        assertNull(p.clan(), "clan vacío");
        assertEquals("76561198000635167", p.steamId());
        assertNull(p.canal());
        assertEquals(4482, p.partidas(), "suma de victorias y derrotas de sus ladders");
        assertEquals(8, p.ladders().size());
        Perfil.Ladder rm = p.ladders().get(0);
        assertEquals(new Perfil.Ladder("3", 3002, 1, 3026, 1862, 724), rm);
        assertTrue(p.series().isEmpty());
        assertTrue(p.vinculadas().isEmpty());
        for (Perfil.Ladder l : p.ladders()) assertTrue(l.rango() == null || l.rango() > 0, "rank -1 → null");
    }

    @Test void steamIds() {
        assertEquals("1", WorldsEdgeApi.steamId("/steam/1"));
        assertNull(WorldsEdgeApi.steamId("/xboxlive/123"));
        assertNull(WorldsEdgeApi.steamId("/steam/"));
        assertNull(WorldsEdgeApi.steamId(null));
    }

    // ----- Ladder

    @Test void clasificacion() throws Exception {
        Clasificacion c = we.clasificacion("rm_1v1", 1, 3, null);
        assertEquals("https://aoe-api.worldsedgelink.com/community/leaderboard/getLeaderBoard2?title=age2&leaderboard_id=3&sortBy=1&start=1&count=3", red.pedidas.get(0));
        assertEquals(46376, c.total());
        assertEquals(3, c.porPagina());
        assertEquals(3, c.filas().size());
        FilaClasificacion f = c.filas().get(0);
        assertEquals(new FilaClasificacion(271202, "Oni.Vinchester", 3002, 1, "ru", null, Instant.ofEpochSecond(1790094664L), 10, 2586, 0, 0), f);
        assertEquals(199325, c.filas().get(2).pid());
        assertEquals("VIT | Hera", c.filas().get(2).nombre());
        assertEquals(-1, c.filas().get(2).racha());
    }

    @Test void paginasDelLadderEIdsDeLadder() throws Exception {
        we.clasificacion("ew_team", 3, 100, "");
        assertEquals("https://aoe-api.worldsedgelink.com/community/leaderboard/getLeaderBoard2?title=age2&leaderboard_id=14&sortBy=1&start=201&count=100", red.pedidas.get(0));
        we.clasificacionFresca("4", 1, 500, null);
        assertTrue(red.pedidas.get(1).endsWith("leaderboard_id=4&sortBy=1&start=1&count=200"), "tope de 200 filas");
        assertThrows(IOException.class, () -> we.clasificacion("rm_2v2", 1, 100, null));
        assertEquals(2, red.pedidas.size(), "un ladder desconocido no va a la red");
    }

    @Test void sinFiltroDePaisNoSeDaElTopDeTodos() {
        assertThrows(IOException.class, () -> we.clasificacion("rm_1v1", 1, 100, "es"));
        assertTrue(red.pedidas.isEmpty());
    }

    // ----- Búsqueda

    @Test void busquedaPorAliasExacto() throws Exception {
        List<PerfilEncontrado> r = we.buscarPerfiles(" Oni.Vinchester ");
        assertEquals("https://aoe-api.worldsedgelink.com/community/leaderboard/getPersonalStat?title=age2&aliases=%5B%22Oni.Vinchester%22%5D", red.pedidas.get(0));
        assertEquals(List.of(new PerfilEncontrado(19438274, "Oni.Vinchester", "by", 3239),
                new PerfilEncontrado(271202, "Oni.Vinchester", "ru", 4482)), r);
    }

    @Test void aliasDesconocidoEsListaVacia() throws Exception {
        red.respuestas.put("aliases=", fixture("alias_desconocido.json"));
        assertEquals(List.of(), we.buscarPerfiles("oni.vinchester"));
    }
}
