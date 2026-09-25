package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.cache.CacheMemoria;
import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PerfilesCompanion (ProfileService, paso A: ficha y fichaConocida). Sin red: un Transporte falso responde el JSON de
 * /profiles/{pid} y cuenta las peticiones; la caché usa CacheService con un reloj falso (nada espera de verdad).
 */
class PerfilesCompanionTest {

    /** Responde siempre lo mismo (cuerpo/estado configurables) y apunta cada URL pedida. */
    static final class TransporteFalso implements Transporte {
        final List<String> pedidas = new ArrayList<>();
        String cuerpo = "{}";
        int estado = 200;
        @Override public Respuesta get(String url) {
            pedidas.add(url);
            return new Respuesta(estado, cuerpo);
        }
    }

    /** Sin freno de verdad: deja pasar todo, para no meter esperas en el test. */
    static final class ThrottleSinFreno implements Throttle {
        @Override public void adquirir(java.util.function.BooleanSupplier cancelar) { }
        @Override public long registrar429() { return 0; }
    }

    final TransporteFalso red = new TransporteFalso();
    final CompanionApi api = new CompanionApi(new ApiClient(new ThrottleSinFreno(), red, s -> { }, () -> false));
    final RelojFalso reloj = new RelojFalso();
    final CacheMemoria<Long, FichaPerfil> fichas = new CacheService(reloj).memoria(Caducidad.PERFIL);
    final Map<Long, Object> canalAprendido = new HashMap<>();
    final Map<Long, Object> paisAprendido = new HashMap<>();
    final PerfilesCompanion servicio = new PerfilesCompanion(api, fichas,
            (pid, v) -> canalAprendido.put(pid, v), (pid, v) -> paisAprendido.put(pid, v));

    /** Cuántas veces se pidió /profiles/{pid} a la red. */
    long peticiones(long pid) {
        String sufijo = "/profiles/" + pid;
        return red.pedidas.stream().filter(u -> u.endsWith(sufijo)).count();
    }

    // ----- 1. conversión de ladders, país, clan y partidas

    @Test void fichaMapeaLosIdsNumericosDeLosLaddersConocidos() {
        red.cuerpo = "{\"country\":\" es \",\"clan\":\" R1 \",\"games\":321,\"leaderboards\":["
                + "{\"leaderboard_id\":\"3\",\"rating\":1500.5,\"rank\":88,\"maxRating\":1600.6,\"wins\":10,\"losses\":2},"
                + "{\"leaderboard_id\":\"4\",\"rating\":1000},"
                + "{\"leaderboard_id\":\"13\",\"rating\":900},"
                + "{\"leaderboard_id\":\"14\",\"rating\":800}]}";
        FichaPerfil f = servicio.ficha(1L);
        assertNotNull(f);
        assertArrayEquals(new int[]{ 1501, 88, 1601, 10, 2 }, f.ladders().get("rm_1v1"), "id numérico 3 → rm_1v1 (el redondeo de 1500.5 es de CompanionApi, no de esta regla)");
        assertEquals(1000, f.ladders().get("rm_team")[0], "id numérico 4 → rm_team");
        assertEquals(900, f.ladders().get("ew_1v1")[0], "id numérico 13 → ew_1v1");
        assertEquals(800, f.ladders().get("ew_team")[0], "id numérico 14 → ew_team");
        assertEquals("es", f.pais(), "país con espacios: trim");
        assertEquals("R1", f.clan(), "clan con espacios: trim");
        assertEquals(321L, f.partidas(), "partidas viene de games");
    }

    @Test void unLadderConIdYaLiteralSeConservaTalCual() {
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"rm_1v1\",\"rating\":700}]}";
        FichaPerfil f = servicio.ficha(6L);
        assertEquals(700, f.ladders().get("rm_1v1")[0], "un id que ya llega como \"rm_1v1\" no pasa por el switch, y entra igual");
    }

    @Test void unLadderFueraDeLosConocidosNoEntraEnLaFicha() {
        red.cuerpo = "{\"leaderboards\":["
                + "{\"leaderboard_id\":\"rm_1v1_console\",\"rating\":999},"
                + "{\"leaderboard_id\":\"dm_1v1\",\"rating\":999}]}";
        FichaPerfil f = servicio.ficha(2L);
        assertTrue(f.ladders().isEmpty(), "ni rm_1v1_console ni dm_1v1 están en LADDER_IDS");
    }

    @Test void losCamposAusentesDeUnLadderQuedanEnCero() {
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"3\"}]}";
        FichaPerfil f = servicio.ficha(3L);
        assertArrayEquals(new int[]{ 0, 0, 0, 0, 0 }, f.ladders().get("rm_1v1"),
                "rating, rango, máximo, victorias y derrotas null → 0");
    }

    @Test void paisYClanAusentesQuedanVacios() {
        red.cuerpo = "{\"games\":5}";
        FichaPerfil f = servicio.ficha(4L);
        assertEquals("", f.pais());
        assertEquals("", f.clan());
    }

    @Test void laCadenaNullComoPaisOClanQuedaVacia() {
        red.cuerpo = "{\"country\":\"null\",\"clan\":\"null\"}";
        FichaPerfil f = servicio.ficha(5L);
        assertEquals("", f.pais(), "la 1.1 trataba el texto \"null\" como ausente");
        assertEquals("", f.clan());
    }

    // ----- 3. caché: fresco < 30 min, a los 30 min justos ya no

    @Test void unaSegundaFichaAntesDeLosTreintaMinutosNoVaALaRed() {
        red.cuerpo = "{\"games\":1}";
        servicio.ficha(10L);
        assertEquals(1, peticiones(10L));
        reloj.avanzar(Duration.ofMinutes(30).toMillis() - 1);
        servicio.ficha(10L);
        assertEquals(1, peticiones(10L), "a 30 min menos 1 ms sigue fresca: no se vuelve a pedir");
    }

    @Test void conLaFichaFrescaNoSeAprendeNadaDeNuevo() {
        red.cuerpo = "{\"country\":\"es\",\"socialTwitchChannel\":\"canal\"}";
        servicio.ficha(12L);
        canalAprendido.clear(); paisAprendido.clear();
        servicio.ficha(12L);
        assertTrue(canalAprendido.isEmpty() && paisAprendido.isEmpty(), "sin llamada no hay nada que aprender: la caché va antes");
    }

    @Test void alosTreintaMinutosJustosVaALaRed() {
        red.cuerpo = "{\"games\":1}";
        servicio.ficha(11L);
        reloj.avanzar(Duration.ofMinutes(30).toMillis());
        servicio.ficha(11L);
        assertEquals(2, peticiones(11L), "frontera estricta: a la edad justa, ya no vale");
    }

    // ----- 4. fallo: null y no guarda nada; lo último conocido sobrevive

    @Test void siLaLlamadaFallaFichaDevuelveNullYNoGuardaNada() {
        red.estado = 500;
        assertNull(servicio.ficha(20L));
        assertNull(servicio.fichaConocida(20L), "nunca hubo una ficha válida que guardar");
    }

    @Test void unJsonRotoTambienDevuelveNullSinGuardarNada() {
        red.cuerpo = "{esto no es json";
        assertNull(servicio.ficha(21L));
        assertNull(servicio.fichaConocida(21L));
    }

    @Test void unFalloTrasUnaFichaBuenaDejaSobrevivirLaUltimaConocida() {
        red.cuerpo = "{\"games\":7}";
        FichaPerfil buena = servicio.ficha(22L);
        assertNotNull(buena);
        reloj.avanzar(Duration.ofMinutes(30).toMillis());   // caduca, para que el siguiente ficha() intente ir a la red
        red.estado = 500;
        assertNull(servicio.ficha(22L), "la llamada que falla devuelve null");
        assertSame(buena, servicio.fichaConocida(22L), "pero lo último conocido no se pierde con un fallo posterior");
    }

    // ----- 5. fichaConocida: la caducada sin red, null si nunca se pidió

    @Test void fichaConocidaDevuelveLaCaducadaSinIrALaRed() {
        red.cuerpo = "{\"games\":9}";
        FichaPerfil f = servicio.ficha(30L);
        reloj.avanzar(Duration.ofMinutes(30).toMillis());
        assertEquals(1, peticiones(30L));
        assertSame(f, servicio.fichaConocida(30L), "caducada, pero es lo último que se supo");
        assertEquals(1, peticiones(30L), "fichaConocida no toca la red");
    }

    @Test void fichaConocidaEsNullSiNuncaSePidio() {
        assertNull(servicio.fichaConocida(999L));
    }

    // ----- 6. aprenderCanal y aprenderPais reciben lo crudo, como la 1.1

    @Test void aprenderCanalYAprenderPaisRecibenLosDatosCrudosSinRecortar() {
        red.cuerpo = "{\"country\":\" es \",\"socialTwitchChannel\":\"tira\"}";
        servicio.ficha(40L);
        assertEquals("tira", canalAprendido.get(40L), "el canal tal cual llegó (social_twitch_channel)");
        assertEquals(" es ", paisAprendido.get(40L), "el país CRUDO, sin trim: el trim es solo para la ficha");
    }
}
