package dev.tirador.aoe2radar.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
