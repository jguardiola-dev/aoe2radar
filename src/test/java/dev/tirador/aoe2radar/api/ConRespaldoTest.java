package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.model.Clasificacion;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.PerfilEncontrado;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConRespaldo con las dos fuentes de verdad (CompanionApi y WorldsEdgeApi) sobre un transporte falso que responde
 * según el host: el companion como el test diga (sano, caído, 404…), World's Edge con las respuestas reales guardadas.
 * Sin red y sin esperas (RelojFalso). Cada test mira qué URL salió y en qué orden: nunca las dos fuentes a la vez.
 */
class ConRespaldoTest {

    static final String CUERPO_MATCHES = "{\"matches\":[{\"match_id\":7,\"started\":\"2026-09-27T10:00:00Z\","
            + "\"leaderboard_name\":\"1v1 Random Map\",\"map_name\":\"Arabia\",\"players\":[{\"profile_id\":271202,\"name\":\"Uno\",\"team\":1}]}]}";

    /** El companion responde lo que diga «companion» (un estado HTTP o una excepción); World's Edge, los fixtures. */
    static final class Red implements Transporte {
        Object companion = 200;
        final List<String> pedidas = new ArrayList<>();
        final WorldsEdgeApiTest.TransporteFixtures we = new WorldsEdgeApiTest.TransporteFixtures();
        Red() {
            we.respuestas.put("getAvailableLeaderboards", WorldsEdgeApiTest.fixture("available_leaderboards.json"));
            we.respuestas.put("getRecentMatchHistory", WorldsEdgeApiTest.fixture("recent_history.json.gz"));
            we.respuestas.put("getLeaderBoard2", WorldsEdgeApiTest.fixture("leaderboard_top.json"));
            we.respuestas.put("aliases=", WorldsEdgeApiTest.fixture("alias.json"));
            we.respuestas.put("getPersonalStat", WorldsEdgeApiTest.fixture("personal_stat.json"));
        }
        @Override public Respuesta get(String url) throws IOException, InterruptedException {
            pedidas.add(url);
            if (Freno.aplicaAWorldsEdge(url)) return we.get(url);
            if (companion instanceof IOException e) throw e;
            if (companion instanceof InterruptedException e) throw e;
            int estado = (Integer) companion;
            String cuerpo = url.contains("/matches") ? CUERPO_MATCHES
                    : url.contains("/leaderboards/") ? "{\"players\":[{\"profile_id\":1,\"name\":\"C\"}],\"total\":1}"
                    : url.contains("/profiles?search=") ? "{\"profiles\":[{\"profile_id\":5,\"name\":\"Nick\"}]}"
                    : "{\"country\":\"es\",\"leaderboards\":[]}";
            return new Respuesta(estado, cuerpo);
        }
        /** Las URL pedidas sin las del catálogo de civs de World's Edge (una vez por sesión, no es «la petición»). */
        List<String> sinCatalogo() {
            List<String> out = new ArrayList<>();
            for (String u : pedidas) if (!u.contains("getAvailableLeaderboards")) out.add(u);
            return out;
        }
        List<String> hosts() {
            List<String> out = new ArrayList<>();
            for (String u : sinCatalogo()) out.add(Freno.aplicaAWorldsEdge(u) ? "WE" : "C");
            return out;
        }
        void olvidar() { pedidas.clear(); }
    }

    final Red red = new Red();
    final RelojFalso reloj = new RelojFalso();
    final List<Boolean> avisos = new ArrayList<>();
    boolean cortacircuitos;
    final ConRespaldo.Estado estado = new ConRespaldo.Estado(reloj, 3, 5 * 60_000L, () -> cortacircuitos, avisos::add);
    final ApiClient api = new ApiClient(new ApiClientTest.ThrottleEspia(), red, s -> { }, () -> false);
    final CompanionApi companion = new CompanionApi(api);
    final WorldsEdgeApi we = new WorldsEdgeApi(api);
    final FuentePartidas partidas = ConRespaldo.partidas(estado, companion, we);
    final FuenteLadder ladder = ConRespaldo.ladder(estado, companion, we);
    final FuentePerfil perfil = ConRespaldo.perfil(estado, companion, we);
    final FuenteBusqueda busqueda = ConRespaldo.busqueda(estado, companion, we);

    static long primeraId(Iterable<Match> ms) { return ms.iterator().next().id; }

    @Test void conElCompanionSanoWorldsEdgeNoSeToca() throws Exception {
        assertEquals(7, primeraId(partidas.partidas(271202L, 1, 10)));
        assertEquals(List.of("C"), red.hosts());
        assertTrue(avisos.isEmpty());
    }

    @Test void companionCaidoLaMismaPeticionVaDespuesAlRespaldo() throws Exception {
        red.companion = new IOException("Connection refused");
        assertEquals(509637140L, primeraId(partidas.partidas(271202L, 1, 10)), "la de World's Edge");
        assertEquals(List.of("C", "WE"), red.hosts(), "primero el companion; tras su fallo, una vez World's Edge");
        assertEquals(List.of(true), avisos, "la barra: datos parciales");
    }

    @Test void unCincoCientosTambienEsCaida() throws Exception {
        red.companion = 503;
        perfil.perfil(271202);
        assertEquals(List.of("C", "WE"), red.hosts());
    }

    @Test void conNFallosSeguidosDejaDeProbarElCompanionHastaLaEspera() throws Exception {
        red.companion = new IOException("timeout");
        for (int i = 0; i < 3; i++) partidas.partidas(271202L, 1, 10);
        red.olvidar();
        partidas.partidas(271202L, 1, 10);
        ladder.clasificacion("rm_1v1", 1, 3, null);
        assertEquals(List.of("WE", "WE"), red.hosts(), "modo respaldo: el companion ni se intenta, tampoco para el ladder (estado compartido)");
        assertEquals(List.of(true), avisos, "un aviso por cambio, no por petición");

        red.companion = 200;   // el companion vuelve…
        red.olvidar();
        reloj.avanzar(5 * 60_000L - 1);
        partidas.partidas(271202L, 1, 10);
        assertEquals(List.of("WE"), red.hosts(), "…pero hasta que pase la espera no se prueba");

        red.olvidar();
        reloj.avanzar(1);
        assertEquals(7, primeraId(partidas.partidas(271202L, 1, 10)));
        assertEquals(List.of("C"), red.hosts(), "pasada la espera, se prueba el companion y responde");
        assertEquals(List.of(true, false), avisos, "la barra: fuera el aviso");

        red.olvidar();
        partidas.partidas(271202L, 1, 10);
        assertEquals(List.of("C"), red.hosts(), "y sigue siendo la fuente");
    }

    @Test void siLaPruebaTrasLaEsperaFallaVuelveAlRespaldoSinEsperarNFallos() throws Exception {
        red.companion = new IOException("timeout");
        for (int i = 0; i < 3; i++) partidas.partidas(271202L, 1, 10);
        reloj.avanzar(5 * 60_000L);
        partidas.partidas(271202L, 1, 10);   // la prueba: falla (y esa petición sale de World's Edge)
        red.olvidar();
        partidas.partidas(271202L, 1, 10);
        assertEquals(List.of("WE"), red.hosts());
    }

    @Test void unExitoEntreFallosReiniciaLaCuenta() throws Exception {
        red.companion = new IOException("timeout");
        partidas.partidas(271202L, 1, 10);
        partidas.partidas(271202L, 1, 10);
        red.companion = 200;
        partidas.partidas(271202L, 1, 10);
        red.companion = new IOException("timeout");
        partidas.partidas(271202L, 1, 10);
        partidas.partidas(271202L, 1, 10);
        red.olvidar();
        red.companion = 200;
        partidas.partidas(271202L, 1, 10);
        assertEquals(List.of("C"), red.hosts(), "2 + 2 fallos no son 3 seguidos");
    }

    @Test void conElCortacircuitosAbiertoNoSeEsperaLaPausaDelCompanion() throws Exception {
        cortacircuitos = true;   // pausa por 429 vigente en el cubo del companion
        Perfil p = perfil.perfilFresco(271202);
        assertEquals("ru", p.pais(), "el de World's Edge");
        assertEquals(List.of("WE"), red.hosts());
        cortacircuitos = false;
        red.olvidar();
        perfil.perfil(271202);
        assertEquals(List.of("C"), red.hosts());
        assertEquals(List.of(true, false), avisos);
    }

    @Test void unNoExisteDelCompanionNoSeRepiteEnWorldsEdge() {
        red.companion = 404;
        assertThrows(IOException.class, () -> perfil.perfil(99));
        assertEquals(List.of("C"), red.hosts());
        assertTrue(avisos.isEmpty());
    }

    @Test void detenerNoEsUnaCaida() {
        red.companion = new InterruptedException("detenido");
        assertThrows(InterruptedException.class, () -> partidas.partidas(271202L, 1, 10));
        assertEquals(List.of("C"), red.hosts());
        assertTrue(avisos.isEmpty());
    }

    @Test void siFallanLasDosSaleElErrorDelRespaldoConElDelCompanionDentro() {
        red.companion = new IOException("Connection refused");
        red.we.respuestas.clear();   // World's Edge: 503
        IOException e = assertThrows(IOException.class, () -> partidas.partidas(271202L, 1, 10));
        assertEquals("HTTP 503", e.getMessage());
        assertEquals("Connection refused", e.getSuppressed()[0].getMessage());
        assertTrue(avisos.isEmpty(), "nada se sirvió del respaldo");
    }

    @Test void elLadderConPaisNoSeSustituyePorElTopDeTodos() {
        red.companion = new IOException("timeout");
        assertThrows(IOException.class, () -> ladder.clasificacion("rm_1v1", 1, 100, "es"));
        assertEquals(List.of("C"), red.hosts(), "World's Edge no filtra por país: ni se le pide");
    }

    @Test void ladderYBusquedaDelRespaldo() throws Exception {
        red.companion = 502;
        Clasificacion c = ladder.clasificacionFresca("rm_1v1", 1, 3, null);
        assertEquals(271202, c.filas().get(0).pid());
        List<PerfilEncontrado> r = busqueda.buscarPerfiles("Oni.Vinchester");
        assertEquals(2, r.size());
        assertEquals(List.of("C", "WE", "C", "WE"), red.hosts());
    }

    @Test void conElCompanionSanoLaBusquedaEsLaSuya() throws Exception {
        assertEquals(List.of(new PerfilEncontrado(5, "Nick", null, -1)), busqueda.buscarPerfiles("nick"));
        assertEquals(List.of("C"), red.hosts());
    }

    @Test void laPruebaTrasLaEsperaNoSeFiaDeLaCacheDelCompanion() throws Exception {
        CompanionApi conCache = CompanionApi.conCache(api, reloj);
        FuentePerfil p = ConRespaldo.perfil(estado, conCache, we);
        p.perfil(1);                          // sano: a la caché
        red.companion = new IOException("timeout");
        for (int i = 0; i < 3; i++) p.perfil(2);   // cae: modo respaldo
        reloj.avanzar(5 * 60_000L);           // la caché (10 min) aún tiene el perfil 1
        red.olvidar();
        p.perfil(1);                          // la prueba: a la red, no a la caché → falla y sigue en respaldo
        assertEquals(List.of("C", "WE"), red.hosts());
        assertEquals(List.of(true), avisos, "sigue en respaldo: ningún «false»");
    }

    @Test void losAvisosLleganEnElOrdenDeLosCambiosConVariosHilos() throws Exception {
        List<Boolean> orden = java.util.Collections.synchronizedList(new ArrayList<>());
        ConRespaldo.Estado e = new ConRespaldo.Estado(reloj, 3, 60_000L, () -> false, orden::add);
        Thread[] hilos = new Thread[8];
        for (int h = 0; h < hilos.length; h++) {
            final boolean respaldo = h % 2 == 0;
            hilos[h] = new Thread(() -> { for (int i = 0; i < 2000; i++) { if (respaldo) e.servidoPorRespaldo(); else e.exitoPrimaria(); } });
        }
        for (Thread t : hilos) t.start();
        for (Thread t : hilos) t.join();
        for (int i = 0; i < orden.size(); i++) assertEquals(i % 2 == 0, orden.get(i), "alternan true/false, sin repetidos");
        assertEquals(e.enRespaldo(), !orden.isEmpty() && orden.get(orden.size() - 1), "el último aviso es el estado real");
    }

    @Test void queEsCaida() {
        assertTrue(ConRespaldo.esCaida(new IOException("HTTP 500")));
        assertTrue(ConRespaldo.esCaida(new IOException("HTTP 429")));
        assertTrue(ConRespaldo.esCaida(new IOException("HTTP 408")));
        assertTrue(ConRespaldo.esCaida(new IOException("Connection reset")));
        assertTrue(ConRespaldo.esCaida(new IOException((String) null)));
        assertFalse(ConRespaldo.esCaida(new IOException("HTTP 404")));
        assertFalse(ConRespaldo.esCaida(new IOException("HTTP 400")));
    }
}
