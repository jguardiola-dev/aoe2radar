package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La caché por URL de CompanionApi (conCache): acierto, caducidad con reloj falso, errores sin guardar, límite de
 * tamaño, lecturas «frescas» e invalidación. Sin red: el transporte de CompanionApiTest cuenta las peticiones.
 */
class CompanionApiCacheTest {

    final RelojFalso reloj = new RelojFalso();
    final CompanionApiTest.TransporteJson red = new CompanionApiTest.TransporteJson();
    final ApiClient cliente = new ApiClient(new ApiClientTest.ThrottleEspia(), red, s -> { }, () -> false);
    final CompanionApi companion = CompanionApi.conCache(cliente, reloj);

    { red.cuerpo = "{\"matches\":[],\"players\":[],\"games\":7}"; }

    int peticiones() { return red.pedidas.size(); }

    @Test void sinCacheComoSiempre() throws Exception {
        CompanionApi sin = new CompanionApi(cliente);
        sin.perfil(1L); sin.perfil(1L);
        sin.clasificacion("rm_1v1", 1, 100, null); sin.clasificacion("rm_1v1", 1, 100, null);
        assertEquals(4, peticiones(), "el constructor de siempre no guarda nada");
        assertEquals(0, sin.aciertosCache());
    }

    @Test void aciertoDePerfil() throws Exception {
        assertEquals(7, companion.perfil(199325L).partidas());
        assertEquals(7, companion.perfil(199325L).partidas());
        assertEquals(1, peticiones());
        assertEquals(1, companion.aciertosCache());
        companion.perfil(2L);
        assertEquals(2, peticiones(), "otro pid, otra URL");
    }

    @Test void aciertoDeLadderPorUrlCompleta() throws Exception {
        companion.clasificacion("rm_1v1", 1, 100, null);
        companion.leaderboard("rm_1v1", 1, 100, null);   // la misma URL por el otro método (top ★ y campana ★ladder)
        assertEquals(1, peticiones());
        companion.clasificacion("rm_1v1", 2, 100, null);
        companion.clasificacion("rm_1v1", 1, 100, "es");
        companion.clasificacion("rm_1v1", 1, 100, "");
        companion.clasificacion("rm_1v1", 1, 50, null);
        assertEquals(5, peticiones(), "página, país (también \"\") y tamaño cambian la URL");
    }

    @Test void cadaLecturaRecibeSuPropioMapa() throws Exception {
        Map<String, Object> a = companion.leaderboard("rm_1v1", 1, 100, null);
        a.put("players", "estropeado");
        Map<String, Object> b = companion.leaderboard("rm_1v1", 1, 100, null);
        assertEquals(1, peticiones());
        assertNotEquals("estropeado", b.get("players"), "lo guardado es el texto: nadie toca lo que ve otro");
    }

    @Test void elPerfilCaducaALosDiezMinutos() throws Exception {
        companion.perfil(1L);
        reloj.avanzar(CompanionApi.TTL_PERFIL_MS - 1);
        companion.perfil(1L);
        assertEquals(1, peticiones(), "un milisegundo antes, aún vale");
        reloj.avanzar(1);
        companion.perfil(1L);
        assertEquals(2, peticiones(), "a los 10 min justos, a la red");
        companion.perfil(1L);
        assertEquals(2, peticiones(), "y lo nuevo vuelve a valer");
        assertEquals(10 * 60_000L, CompanionApi.TTL_PERFIL_MS);
    }

    @Test void elLadderCaducaALosCatorceMinutos() throws Exception {
        companion.clasificacion("rm_1v1", 1, 100, null);
        reloj.avanzar(CompanionApi.TTL_LADDER_MS - 1);
        companion.clasificacion("rm_1v1", 1, 100, null);
        assertEquals(1, peticiones());
        reloj.avanzar(1);
        companion.clasificacion("rm_1v1", 1, 100, null);
        assertEquals(2, peticiones());
        assertEquals(14 * 60_000L, CompanionApi.TTL_LADDER_MS);   // uno menos que el ritmo de top y campanas (15)
    }

    @Test void noGuardaErrores() throws Exception {
        red.estados.add(500);
        assertThrows(IOException.class, () -> companion.perfil(1L));
        companion.perfil(1L);
        assertEquals(2, peticiones(), "el 500 no se guardó: la siguiente va a la red");
        companion.perfil(1L);
        assertEquals(2, peticiones(), "el 200 sí");
        red.estados.add(404);
        assertThrows(IOException.class, () -> companion.clasificacion("rm_1v1", 9, 100, null));
        companion.clasificacion("rm_1v1", 9, 100, null);
        assertEquals(4, peticiones());
    }

    @Test void unCuerpoIlegibleNoSeGuarda() throws Exception {
        String bueno = red.cuerpo;
        red.cuerpo = "{\"games\":";   // 2xx, pero cortado a medias
        assertThrows(RuntimeException.class, () -> companion.perfil(1L), "falla igual que sin caché");
        red.cuerpo = bueno;
        assertEquals(7, companion.perfil(1L).partidas());
        assertEquals(2, peticiones(), "lo ilegible no se guardó: la siguiente fue a la red");
        companion.perfil(1L);
        assertEquals(2, peticiones());
    }

    @Test void limiteDeTamanoSacaLaMenosUsada() throws Exception {
        for (long pid = 1; pid <= CompanionApi.MAX_CACHE; pid++) companion.perfil(pid);
        assertEquals(CompanionApi.MAX_CACHE, peticiones());
        companion.perfil(1L);                               // la 1 pasa a ser la más reciente
        assertEquals(CompanionApi.MAX_CACHE, peticiones());
        companion.perfil(CompanionApi.MAX_CACHE + 1L);      // entra una más: sale la menos usada (la 2)
        companion.perfil(1L);
        assertEquals(CompanionApi.MAX_CACHE + 1, peticiones(), "la 1 se usó hace poco: sigue");
        companion.perfil(2L);
        assertEquals(CompanionApi.MAX_CACHE + 2, peticiones(), "la 2 era la menos usada: salió");
        assertEquals(500, CompanionApi.MAX_CACHE);
    }

    @Test void cacheRespuestasNoPasaDelMaximo() {
        CacheRespuestas c = new CacheRespuestas(reloj, 3);
        for (int i = 0; i < 10; i++) c.poner("u" + i, "c" + i, 60_000);
        assertEquals(3, c.tamano());
        assertNull(c.vigente("u0"));
        assertEquals("c9", c.vigente("u9"));
        assertThrows(IllegalArgumentException.class, () -> new CacheRespuestas(reloj, 0));
    }

    @Test void lasFrescasSiempreVanALaRedYRenuevanLaCache() throws Exception {
        companion.perfil(1L);
        companion.perfilFresco(1L);
        assertEquals(2, peticiones(), "forzar no mira la caché");
        reloj.avanzar(CompanionApi.TTL_PERFIL_MS - 1);
        companion.perfil(1L);
        assertEquals(2, peticiones(), "la fresca renovó la entrada: cuenta desde ella");
        companion.clasificacion("rm_1v1", 1, 100, "es");
        companion.clasificacionFresca("rm_1v1", 1, 100, "es");
        companion.leaderboardFresca("rm_1v1", 1, 100, "es");
        companion.clasificacion("rm_1v1", 1, 100, "es");
        assertEquals(5, peticiones());
    }

    @Test void invalidarOlvidaUnaClave() throws Exception {
        companion.perfil(1L);
        companion.perfil(2L);
        companion.invalidarPerfil(1L);
        companion.perfil(1L);
        companion.perfil(2L);
        assertEquals(3, peticiones(), "solo la 1 vuelve a la red");
        companion.clasificacion("rm_1v1", 1, 100, null);
        companion.invalidar(CompanionApi.urlLeaderboard("rm_1v1", 1, 100, null));
        companion.clasificacion("rm_1v1", 1, 100, null);
        assertEquals(5, peticiones());
    }

    @Test void matchesBusquedaYTwitchNuncaSeGuardan() throws Exception {
        companion.matches("1,2", 1, 50); companion.matches("1,2", 1, 50);
        companion.recientes("rm_1v1", 1, 50); companion.recientes("rm_1v1", 1, 50);
        red.cuerpo = "{\"profiles\":[],\"data\":[]}";
        companion.buscarPerfiles("tirador"); companion.buscarPerfiles("tirador");
        companion.twitchDirectos(); companion.twitchDirectos();
        red.cuerpo = "[]";
        companion.twitchCanal("x"); companion.twitchCanal("x");
        assertEquals(10, peticiones(), "tiempo real: siempre a la red");
        assertEquals(0, companion.aciertosCache());
    }

    /** Un acierto no pasa por el freno (que es donde se atiende a Detener): tiene que mirar él la bandera. */
    @Test void unAciertoRespetaDetener() throws Exception {
        boolean[] detenida = { false };
        CompanionApi c = CompanionApi.conCache(new ApiClient(new ApiClientTest.ThrottleEspia(), red, s -> { }, () -> detenida[0]), reloj);
        c.perfil(1L);
        c.clasificacion("rm_1v1", 1, 100, null);
        detenida[0] = true;
        InterruptedException e = assertThrows(InterruptedException.class, () -> c.perfil(1L));
        assertEquals("detenido", e.getMessage());
        assertThrows(InterruptedException.class, () -> c.clasificacion("rm_1v1", 1, 100, null));
        assertEquals(2, peticiones(), "no se fue a la red: se cortó antes de devolver lo guardado");
        detenida[0] = false;
        c.perfil(1L);
        assertEquals(2, peticiones(), "sin Detener, el acierto vuelve a servir");
    }
}
