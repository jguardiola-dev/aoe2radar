package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** El contador de llamadas de ApiClient: plantillas, 429, volcado horario y al cerrar. Sin red: transporte falso. */
class ContadorLlamadasTest {

    final RelojFalso reloj = new RelojFalso();
    final List<String> lineas = new ArrayList<>();
    final ContadorLlamadas contador = new ContadorLlamadas(reloj, lineas::add);
    final ApiClientTest.TransporteFalso red = new ApiClientTest.TransporteFalso();
    final ApiClientTest.ThrottleEspia throttle = new ApiClientTest.ThrottleEspia();
    final ApiClient api = new ApiClient(throttle, red, s -> { }, () -> false, contador);

    @AfterEach void limpiarInterrupcion() { Thread.interrupted(); }

    @Test void plantillasSinIdsNiValores() {
        assertEquals("data.aoe2companion.com /api/profiles/{id}", ContadorLlamadas.clave(Http.API + "/profiles/199325"));
        assertEquals("data.aoe2companion.com /api/matches?page&per_page&profile_ids",
                ContadorLlamadas.clave(Http.API + "/matches?profile_ids=1,2,3&page=1&per_page=50"));
        assertEquals("data.aoe2companion.com /api/matches?leaderboard_ids&page&per_page",
                ContadorLlamadas.clave(Http.API + "/matches?leaderboard_ids=rm_1v1&page=4&per_page=50"), "el río sale aparte");
        assertEquals("data.aoe2companion.com /api/leaderboards/{lb}?country&page&per_page",
                ContadorLlamadas.clave(Http.API + "/leaderboards/rm_1v1?page=1&per_page=100&country=es"));
        assertEquals("data.aoe2companion.com /api/leaderboards/{lb}?page&per_page",
                ContadorLlamadas.clave(Http.API + "/leaderboards/3?page=2&per_page=100"));
        assertEquals("data.aoe2companion.com /api/profiles?page&search",
                ContadorLlamadas.clave(Http.API + "/profiles?search=tirador&page=1"));
        assertEquals("api.aoe2companion.com /twitch/live?game", ContadorLlamadas.clave(CompanionApi.TWITCH_LIVE + "?game=13389"));
        assertEquals("api.aoe2companion.com /twitch/live?channel", ContadorLlamadas.clave(CompanionApi.TWITCH_LIVE + "?channel=x"));
        assertEquals("steamcommunity.com /profiles/{id}/ajaxaliases",
                ContadorLlamadas.clave("https://steamcommunity.com/profiles/76561198000000000/ajaxaliases"));
        assertEquals("? (url ilegible)", ContadorLlamadas.clave("no es una url con espacios"));
    }

    @Test void cuentaCadaPeticionYSus429ReintentosIncluidos() throws Exception {
        red.responde(429, 429, 200);
        api.textoCon429(Http.API + "/profiles/1");
        api.texto(Http.API + "/profiles/2");
        api.texto("https://steamcommunity.com/profiles/9/ajaxaliases");
        api.volcarLlamadas();
        assertEquals(List.of("API: llamadas en 0 min: 5 (429: 2)"
                + " · data.aoe2companion.com /api/profiles/{id} 4 (429: 2)"
                + " · steamcommunity.com /profiles/{id}/ajaxaliases 1"), lineas);
    }

    @Test void unaPeticionQueFallaEnLaRedTambienCuenta() {
        red.responde(new IOException("timeout"));
        assertThrows(IOException.class, () -> api.texto(Http.API + "/profiles/1"));
        contador.volcar();
        assertEquals(List.of("API: llamadas en 0 min: 1 (429: 0) · data.aoe2companion.com /api/profiles/{id} 1"), lineas);
    }

    @Test void volcarPoneACeroYSinLlamadasNoEscribe() throws Exception {
        contador.volcar();
        assertTrue(lineas.isEmpty(), "sin llamadas, nada que decir");
        api.texto(Http.API + "/profiles/1");
        contador.volcar();
        contador.volcar();
        assertEquals(1, lineas.size(), "el segundo volcado ya no tiene nada");
    }

    @Test void vuelcaSoloCadaHoraYAbreTramoNuevo() throws Exception {
        api.texto(Http.API + "/profiles/1");
        reloj.avanzar(ContadorLlamadas.HORA_MS - 1);
        api.texto(Http.API + "/profiles/1");
        assertTrue(lineas.isEmpty(), "aún no ha pasado la hora");
        reloj.avanzar(1);
        api.texto(Http.API + "/matches?profile_ids=1&page=1&per_page=5");
        assertEquals(List.of("API: llamadas en 60 min: 2 (429: 0) · data.aoe2companion.com /api/profiles/{id} 2"), lineas,
                "la que dispara el volcado ya cuenta en el tramo nuevo");
        reloj.avanzar(10 * 60_000L);
        api.volcarLlamadas();
        assertEquals("API: llamadas en 10 min: 1 (429: 0) · data.aoe2companion.com /api/matches?page&per_page&profile_ids 1",
                lineas.get(1));
    }

    @Test void ordenaPorNumeroDeLlamadas() throws Exception {
        api.texto(Http.API + "/profiles/1");
        for (int i = 0; i < 3; i++) api.texto(Http.API + "/leaderboards/rm_1v1?page=" + i + "&per_page=100");
        contador.volcar();
        assertEquals("API: llamadas en 0 min: 4 (429: 0) · data.aoe2companion.com /api/leaderboards/{lb}?page&per_page 3"
                + " · data.aoe2companion.com /api/profiles/{id} 1", lineas.get(0));
    }

    @Test void seguroEntreHilos() throws Exception {
        ContadorLlamadas c = new ContadorLlamadas(reloj, lineas::add);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch salida = new CountDownLatch(1);
        for (int h = 0; h < 8; h++) pool.submit(() -> {
            salida.await();
            for (int i = 0; i < 1000; i++) { c.peticion(Http.API + "/profiles/" + i); c.respuesta(Http.API + "/profiles/" + i, i % 10 == 0 ? 429 : 200); }
            return null;
        });
        salida.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        c.volcar();
        assertEquals(List.of("API: llamadas en 0 min: 8000 (429: 800) · data.aoe2companion.com /api/profiles/{id} 8000 (429: 800)"), lineas);
    }
}
