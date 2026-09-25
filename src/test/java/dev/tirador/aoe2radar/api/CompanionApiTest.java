package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.cache.Canales;
import dev.tirador.aoe2radar.cache.Paises;
import dev.tirador.aoe2radar.model.Clasificacion;
import dev.tirador.aoe2radar.model.Directo;
import dev.tirador.aoe2radar.model.FilaClasificacion;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.PaginaPartidas;
import dev.tirador.aoe2radar.model.PerfilEncontrado;
import dev.tirador.aoe2radar.model.Perfil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

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

    @Test void buscarPerfilesConvierteSinFiltrar() throws Exception {
        red.cuerpo = "{\"profiles\":[{\"profile_id\":9000001,\"name\":\"Hera\",\"country\":\"ca\",\"games\":10},"
                + "{\"profileId\":9000002},\"ilegible\"]}";
        List<PerfilEncontrado> r = companion.buscarPerfiles("hera");
        assertEquals(3, r.size(), "sin filtrar: el pid <= 0 lo filtra la pantalla");
        assertEquals(new PerfilEncontrado(9000001, "Hera", "ca", 10), r.get(0));
        assertEquals(new PerfilEncontrado(9000002, null, null, -1), r.get(1), "clave alternativa profileId");
        assertEquals(-1, r.get(2).pid());
        assertNull(Paises.PAIS_DE.get(9000001L), "aprender el país es de la pantalla");
    }

    @Test void twitchDirectosEnLasDosFormas() throws Exception {
        red.cuerpo = "[{\"user_login\":\"hera\",\"user_name\":\"Hera\",\"title\":\"t\",\"language\":\"en\",\"viewer_count\":900,\"type\":\"live\"}]";
        assertEquals(List.of(new Directo("hera", "Hera", "t", "en", 900, "live")), companion.twitchDirectos());
        red.cuerpo = "{\"data\":[{\"userLogin\":\"viper\",\"userName\":\"Viper\",\"viewerCount\":5}]}";
        assertEquals(List.of(new Directo("viper", "Viper", null, null, 5, null)), companion.twitchDirectos(), "forma {data:[…]} y claves alternativas");
        red.cuerpo = "{}";
        assertTrue(companion.twitchDirectos().isEmpty());
    }

    @Test void twitchCanalEnMinusculasSinDependerDelIdiomaDelSistema() throws Exception {
        // Con la Locale turca, "I".toLowerCase() da «ı» (i sin punto) y el canal no existiría (DEUDA, fase 4).
        java.util.Locale antes = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            companion.twitchCanal("TIRADOR");
            assertEquals("https://api.aoe2companion.com/twitch/live?channel=tirador", ultima());
        } finally {
            java.util.Locale.setDefault(antes);
        }
    }

    @Test void twitchCanalPrimeroObjetoONull() throws Exception {
        red.cuerpo = "[]";
        assertNull(companion.twitchCanal("x"), "lista vacía: no hay directo");
        red.cuerpo = "[{\"user_login\":\"a\",\"type\":\"live\"},{\"user_login\":\"b\"}]";
        assertEquals("a", companion.twitchCanal("x").login(), "el primero de la lista");
        red.cuerpo = "{\"user_login\":\"c\",\"type\":\"offline\"}";
        assertEquals("offline", companion.twitchCanal("x").tipo(), "si no llega lista, el propio objeto");
    }

    @Test void recientesYPaginaReintentanEl429() throws Exception {
        red.estados.add(429);
        companion.recientes(null, 1, 50);
        assertEquals(2, red.pedidas.size(), "río de «Al azar»: con reintento, como su httpText429 de la 1.1");
        red.estados.add(429);
        companion.pagina(1L, 1, 50);
        assertEquals(4, red.pedidas.size(), "paginaciones: con reintento");
        assertEquals(2, throttle.cuatrocientosVeintinueve);
    }

    @Test void twitchCanalConCuerpoNullEsNull() throws Exception {
        red.cuerpo = "null";
        assertNull(companion.twitchCanal("x"));
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

    @Test void paginaCuentaLasBrutasYDaLasLegibles() throws Exception {
        red.cuerpo = TRES_PARTIDAS;
        PaginaPartidas p = companion.pagina(1L, 2, 50);
        assertEquals(4, p.brutas(), "las brutas incluyen la ilegible: con ellas se decide si es la última página");
        assertEquals(List.of(11L, 12L, 13L), p.partidas().stream().map(m -> m.id).toList());
        assertEquals("https://data.aoe2companion.com/api/matches?profile_ids=1&page=2&per_page=50", ultima());
        red.cuerpo = "{}";
        assertEquals(0, companion.pagina(1L, 3, 50).brutas());
    }

    @Test void recientesConYSinFiltroDeLadder() throws Exception {
        red.cuerpo = TRES_PARTIDAS;
        assertEquals(4, companion.recientes("rm_1v1", 1, 50).brutas());
        assertEquals("https://data.aoe2companion.com/api/matches?leaderboard_ids=rm_1v1&page=1&per_page=50", ultima());
        companion.recientes("3", 2, 50);
        assertEquals("https://data.aoe2companion.com/api/matches?leaderboard_ids=3&page=2&per_page=50", ultima());
        companion.recientes(null, 1, 50);
        assertEquals("https://data.aoe2companion.com/api/matches?page=1&per_page=50", ultima(), "las tres variantes del río, como la 1.1");
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

    @Test void perfilConvierteCadaBloque() throws Exception {
        red.cuerpo = "{\"country\":\"es\",\"clan\":\"R1\",\"games\":321,\"socialTwitchChannel\":\"tira\",\"steam_id\":\" 7656 \","
                + "\"leaderboards\":[{\"leaderboard_id\":3,\"rating\":1500.5,\"rank\":88,\"maxRating\":1600.6,\"wins\":10,\"losses\":\"x\"},{}],"
                + "\"ratings\":[{\"leaderboardId\":\"rm_1v1\",\"ratings\":[{\"date\":\"2024-01-02T03:04:05Z\",\"rating\":1499.9,\"ratingDiff\":-12},"
                + "{\"date\":-1e30,\"rating\":\"x\"}]}],"
                + "\"linkedProfiles\":[{\"profileId\":42,\"name\":\"alt\",\"country\":\"fr\",\"games\":7},\"ilegible\"]}";
        Perfil p = companion.perfil(199325L);
        assertEquals("es", p.pais());
        assertEquals("R1", p.clan());
        assertEquals(321, p.partidas());
        assertEquals("tira", p.canal(), "clave alternativa socialTwitchChannel");
        assertEquals(" 7656 ", p.steamId(), "sin recortar: recortar es de la pantalla");

        Perfil.Ladder l = p.ladders().get(0);
        assertEquals("3.0", l.id(), "un id numérico llega como Double: «3.0», no «3» (como en la 1.1; ver DEUDA)");
        assertEquals(1501, l.rating(), "ladders: redondeo");
        assertEquals(88, l.rango());
        assertEquals(1601, l.ratingMax(), "clave alternativa maxRating, redondeada (truncar daría 1600)");
        assertEquals(10, l.ganadas());
        assertNull(l.perdidas());
        Perfil.Ladder vacio = p.ladders().get(1);
        assertNull(vacio.id());
        assertNull(vacio.rating());

        Perfil.Serie s = p.series().get(0);
        assertEquals("rm_1v1", s.id(), "clave alternativa leaderboardId");
        assertEquals(2, s.puntos().size(), "sin filtrar: filtrar es de la pantalla");
        assertEquals(1499, s.puntos().get(0).rating(), "serie: TRUNCADO, como en la 1.1");
        assertEquals(-12, s.puntos().get(0).diff(), "clave alternativa ratingDiff");
        assertEquals(Instant.parse("2024-01-02T03:04:05Z"), s.puntos().get(0).fecha());
        assertNull(s.puntos().get(1).fecha(), "fecha absurda: null, no excepción");
        assertNull(s.puntos().get(1).rating());

        assertEquals(2, p.vinculadas().size());
        Perfil.Vinculada v = p.vinculadas().get(0);
        assertEquals(42, v.pid());
        assertEquals("alt", v.nombre());
        assertEquals("fr", v.pais());
        assertEquals(7, v.partidas());
        assertEquals(-1, p.vinculadas().get(1).pid(), "ilegible: pid -1, que la pantalla filtra");
    }

    @Test void perfilConLasDosClavesGanaLaPrimera() throws Exception {
        red.cuerpo = "{\"social_twitch_channel\":\"uno\",\"socialTwitchChannel\":\"dos\","
                + "\"leaderboards\":[{\"leaderboard_id\":\"rm_1v1\",\"leaderboardId\":\"3\"}],"
                + "\"linked_profiles\":[{\"profile_id\":1,\"profileId\":2}],\"linkedProfiles\":[]}";
        Perfil p = companion.perfil(1L);
        assertEquals("uno", p.canal());
        assertEquals("rm_1v1", p.ladders().get(0).id());
        assertEquals(1, p.vinculadas().size(), "linked_profiles antes que linkedProfiles");
        assertEquals(1, p.vinculadas().get(0).pid());
    }

    @Test void perfilVacioYSinEfectos() throws Exception {
        red.cuerpo = "[]";   // raíz que no es objeto: como antes, un perfil vacío
        Perfil p = companion.perfil(9000001L);
        assertNull(p.pais());
        assertNull(p.canal());
        assertEquals(-1, p.partidas());
        assertTrue(p.ladders().isEmpty() && p.series().isEmpty() && p.vinculadas().isEmpty());
        red.cuerpo = "{\"country\":\"es\",\"social_twitch_channel\":\"canalx\"}";
        companion.perfil(9000002L);
        assertNull(Paises.PAIS_DE.get(9000002L), "aprender es de cada pantalla");
        assertNull(Canales.CANAL_DE.get(9000002L));
    }

    @Test void pasaPorElFrenoYReintentaEl429() throws Exception {
        red.estados.add(429);
        companion.matches(1L, 1, 5);
        assertEquals(2, red.pedidas.size(), "textoCon429: reintento tras el 429");
        assertEquals(1, throttle.cuatrocientosVeintinueve);
        assertEquals(2, throttle.adquiridas);
    }
}
