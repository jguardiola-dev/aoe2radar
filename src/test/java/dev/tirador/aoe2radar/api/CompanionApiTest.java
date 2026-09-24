package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.cache.Canales;
import dev.tirador.aoe2radar.cache.Paises;
import dev.tirador.aoe2radar.model.Clasificacion;
import dev.tirador.aoe2radar.model.FilaClasificacion;
import dev.tirador.aoe2radar.model.Match;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.time.Instant;
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
        for (long pid = 9000001; pid <= 9000003; pid++) { Paises.PAIS_DE.remove(pid); Canales.CANAL_DE.remove(pid); }
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

    @Test void clasificacionConvierteCadaCampo() throws Exception {
        red.cuerpo = "{\"total\":250,\"per_page\":100,\"players\":["
                + "{\"profile_id\":9000001,\"name\":\"Hera\",\"rating\":2890.6,\"rank\":1,\"country\":\"CA\","
                + "\"social_twitch_channel\":\"hera\",\"last_match_time\":1700000000,\"streak\":-2,\"games\":4000,"
                + "\"last10MatchesWon\":[true,false,true,null,\"x\"]},"
                + "{\"profileId\":9000002,\"socialTwitchChannel\":\"otro\",\"rating\":\"alto\",\"lastMatchTime\":\"2024-01-02T03:04:05Z\",\"last_10_matches_won\":[false]},"
                + "\"ilegible\"]}";
        Clasificacion c = companion.clasificacion("rm_1v1", 1, 100, null);
        assertEquals(250, c.total());
        assertEquals(100, c.porPagina());
        assertEquals(3, c.filas().size(), "una fila por elemento, también la ilegible");

        FilaClasificacion hera = c.filas().get(0);
        assertEquals(9000001, hera.pid());
        assertEquals("Hera", hera.nombre());
        assertEquals(2891, hera.rating(), "redondeo, no truncado");
        assertEquals(1, hera.rango());
        assertEquals("CA", hera.pais(), "tal cual: normalizar es cosa de aprenderPais");
        assertEquals("hera", hera.canal());
        assertEquals(Instant.ofEpochSecond(1700000000), hera.ultimaPartida());
        assertEquals(-2, hera.racha());
        assertEquals(4000, hera.partidas());
        assertEquals(2, hera.ganadas10());
        assertEquals(3, hera.jugadas10(), "solo cuentan los booleanos");

        FilaClasificacion otra = c.filas().get(1);
        assertEquals(9000002, otra.pid(), "clave alternativa profileId");
        assertNull(otra.nombre());
        assertEquals("null", String.valueOf(otra.nombre()), "las pantallas lo siguen viendo como «null», igual que antes");
        assertNull(otra.rating(), "un rating que no es número queda null: cada pantalla pone su valor por defecto");
        assertNull(otra.rango());
        assertEquals(Instant.parse("2024-01-02T03:04:05Z"), otra.ultimaPartida());
        assertEquals(0, otra.ganadas10());
        assertEquals(1, otra.jugadas10());
        assertEquals("otro", otra.canal(), "clave alternativa socialTwitchChannel");
        assertNull(otra.racha());
        assertNull(otra.partidas());

        assertEquals(-1, c.filas().get(2).pid(), "ilegible: pid -1, que todas las pantallas filtran");
    }

    @Test void clasificacionAliasesYPrecedencia() throws Exception {
        // Con las dos claves presentes gana la primera, como en la 1.1 (val recorre las claves en orden).
        red.cuerpo = "{\"perPage\":50,\"players\":[{\"profile_id\":9000001,\"profileId\":7,\"last_match_time\":-1e30}]}";
        Clasificacion c = companion.clasificacion("rm_1v1", 1, 50, null);
        assertEquals(50, c.porPagina(), "clave alternativa perPage");
        assertEquals(9000001, c.filas().get(0).pid(), "profile_id antes que profileId");
        assertNull(c.filas().get(0).ultimaPartida(), "fecha absurda: null, no excepción");
    }

    @Test void clasificacionNoAprendeNada() throws Exception {
        red.cuerpo = "{\"players\":[{\"profile_id\":9000003,\"country\":\"es\",\"social_twitch_channel\":\"canalx\"}]}";
        companion.clasificacion("rm_1v1", 1, 100, null);
        assertNull(Paises.PAIS_DE.get(9000003L), "aprender es de cada pantalla (lbPagina no aprende)");
        assertNull(Canales.CANAL_DE.get(9000003L));
    }

    @Test void clasificacionSinCamposDePaginacion() throws Exception {
        red.cuerpo = "{\"players\":[]}";
        Clasificacion c = companion.clasificacion("3", 2, 100, "es");
        assertEquals(-1, c.total());
        assertEquals(-1, c.porPagina());
        assertTrue(c.filas().isEmpty());
        assertEquals("https://data.aoe2companion.com/api/leaderboards/3?page=2&per_page=100&country=es", ultima());
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
