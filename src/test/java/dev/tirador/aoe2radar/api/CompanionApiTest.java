package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.cache.Paises;
import dev.tirador.aoe2radar.model.Match;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cada endpoint construye EXACTAMENTE la URL que la 1.1 construía a mano (se compara carácter a carácter) y devuelve
 * el JSON leído. Sin red: el transporte falso apunta la URL y responde JSON.
 */
class CompanionApiTest {

    static final class TransporteJson implements Transporte {
        final List<String> pedidas = new ArrayList<>();
        final Deque<Integer> estados = new ArrayDeque<>();
        String cuerpo = "{\"matches\":[],\"players\":[]}";
        @Override public Respuesta get(String url) {
            pedidas.add(url);
            int e = estados.isEmpty() ? 200 : estados.poll();
            return new Respuesta(e, cuerpo);
        }
    }

    final TransporteJson red = new TransporteJson();
    final ApiClientTest.ThrottleEspia throttle = new ApiClientTest.ThrottleEspia();
    final CompanionApi companion = new CompanionApi(new ApiClient(throttle, red, s -> { }, () -> false));

    String ultima() { return red.pedidas.get(red.pedidas.size() - 1); }

    @Test void matchesDeVariosJugadores() throws Exception {
        companion.matches("1,2,3", 1, 50);
        assertEquals("https://data.aoe2companion.com/api/matches?profile_ids=1,2,3&page=1&per_page=50", ultima());
    }

    @Test void matchesDeUnJugador() throws Exception {
        companion.matches(199325L, 3, 20);
        assertEquals("https://data.aoe2companion.com/api/matches?profile_ids=199325&page=3&per_page=20", ultima());
    }

    @Test void perfil() throws Exception {
        companion.perfil(199325L);
        assertEquals("https://data.aoe2companion.com/api/profiles/199325", ultima());
    }

    @Test void leaderboardSinPais() throws Exception {
        companion.leaderboard("rm_1v1", 1, 100, null);
        assertEquals("https://data.aoe2companion.com/api/leaderboards/rm_1v1?page=1&per_page=100", ultima());
    }

    @Test void leaderboardConPais() throws Exception {
        companion.leaderboard("3", 2, 25, "es");
        assertEquals("https://data.aoe2companion.com/api/leaderboards/3?page=2&per_page=25&country=es", ultima());
    }

    @Test void leaderboardConPaisVacioLoAnadeIgual() throws Exception {
        // Caso de Live now «por país»: la 1.1 añadía siempre &country=, aunque fuera vacío.
        companion.leaderboard("rm_1v1", 1, 100, "");
        assertEquals("https://data.aoe2companion.com/api/leaderboards/rm_1v1?page=1&per_page=100&country=", ultima());
    }

    @Test void buscarPerfilesCodificaYNoReintenta() throws Exception {
        companion.buscarPerfiles("  Mr_Yo ñ ");
        assertEquals("https://data.aoe2companion.com/api/profiles?search=Mr_Yo+%C3%B1&page=1", ultima(), "trim y URLEncoder UTF-8, como la 1.1");
        red.estados.add(429);
        assertThrows(java.io.IOException.class, () -> companion.buscarPerfiles("hera"));
        assertEquals(2, red.pedidas.size(), "un solo intento: no reintenta");
        assertEquals(1, throttle.cuatrocientosVeintinueve, "pero el 429 sí cuenta al freno");
    }

    @Test void twitch() throws Exception {
        companion.twitchDirectos();
        assertEquals("https://api.aoe2companion.com/twitch/live?game=13389", ultima());
        companion.twitchCanal("12Tirador");
        assertEquals("https://api.aoe2companion.com/twitch/live?channel=12tirador", ultima());
    }

    static final String TRES_PARTIDAS = "{\"matches\":["
            + "{\"match_id\":11,\"players\":[{\"profile_id\":9000001,\"name\":\"a\",\"country\":\"es\",\"team\":1}]},"
            + "{\"match_id\":0},"                                   // ilegible: parseMatch devuelve null
            + "{\"match_id\":12,\"players\":[{\"profile_id\":9000002,\"name\":\"b\",\"country\":\"fr\",\"team\":1}]},"
            + "{\"match_id\":13,\"players\":[{\"profile_id\":9000003,\"name\":\"c\",\"country\":\"de\",\"team\":1}]}]}";

    @AfterEach void limpiarPaises() {
        for (long pid = 9000001; pid <= 9000003; pid++) Paises.PAIS_DE.remove(pid);
    }

    @Test void partidasConvierteASaltandoLasIlegibles() throws Exception {
        red.cuerpo = TRES_PARTIDAS;
        List<Long> ids = new ArrayList<>();
        for (Match m : companion.partidas(1L, 1, 50)) ids.add(m.id);
        assertEquals(List.of(11L, 12L, 13L), ids);
        assertEquals("https://data.aoe2companion.com/api/matches?profile_ids=1&page=1&per_page=50", ultima());
    }

    @Test void partidasEsPerezosa_unBreakNoLeeLasSiguientes() throws Exception {
        // Mismo comportamiento que el bucle a mano de la 1.1: parseMatch aprende países; si se corta tras la primera,
        // no se aprende nada de las siguientes.
        red.cuerpo = TRES_PARTIDAS;
        for (Match m : companion.partidas(1L, 1, 50)) { if (m.id == 11) break; }
        assertEquals("es", Paises.PAIS_DE.get(9000001L));
        assertNull(Paises.PAIS_DE.get(9000002L), "la segunda no se leyó");
        assertNull(Paises.PAIS_DE.get(9000003L));
    }

    @Test void elIteradorNoAvanzaConHasNextRepetidoYNextFuncionaSolo() throws Exception {
        red.cuerpo = TRES_PARTIDAS;
        Iterator<Match> it = companion.partidas(1L, 1, 50).iterator();
        assertTrue(it.hasNext()); assertTrue(it.hasNext());
        assertEquals(11, it.next().id, "hasNext repetido no se come ninguna");
        assertEquals(12, it.next().id, "next sin hasNext: salta la ilegible y da la siguiente");
        assertEquals(13, it.next().id);
        assertFalse(it.hasNext());
        assertThrows(java.util.NoSuchElementException.class, it::next);
    }

    @Test void partidasSinPartidasEsVacia() throws Exception {
        red.cuerpo = "{\"matches\":[]}";
        assertFalse(companion.partidas(1L, 1, 50).iterator().hasNext());
    }

    @Test void devuelveElJsonLeido() throws Exception {
        red.cuerpo = "{\"profile_id\":199325,\"name\":\"Hera\"}";
        Map<String, Object> p = companion.perfil(199325L);
        assertEquals("Hera", p.get("name"));
    }

    @Test void pasaPorElFrenoYReintentaEl429() throws Exception {
        red.estados.add(429);
        companion.matches(1L, 1, 5);
        assertEquals(2, red.pedidas.size(), "textoCon429: reintento tras el 429");
        assertEquals(1, throttle.cuatrocientosVeintinueve);
        assertEquals(2, throttle.adquiridas);
    }
}
