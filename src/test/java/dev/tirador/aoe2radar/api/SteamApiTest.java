package dev.tirador.aoe2radar.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Caracterización de SteamApi (antes, dentro de nicksAnteriores de la app): sin red, con un transporte falso que
 * responde JSON. Steam no es un host del companion (ver Freno.aplicaA), así que un 429 suyo no debe contar al freno
 * del companion, aunque textoCon429 reintente igual que con cualquier host.
 */
class SteamApiTest {

    static final String STEAM_ID = "76561198000000000";
    static final String URL = "https://steamcommunity.com/profiles/" + STEAM_ID + "/ajaxaliases";

    /** Como el TransporteJson de CompanionApiTest: apunta la URL pedida y responde el JSON de turno; admite una cola
     *  de estados HTTP (para los reintentos ante 429). */
    static final class TransporteJson implements Transporte {
        final List<String> pedidas = new ArrayList<>();
        final Deque<Integer> estados = new ArrayDeque<>();
        String cuerpo = "[]";
        @Override public Respuesta get(String url) {
            pedidas.add(url);
            int e = estados.isEmpty() ? 200 : estados.poll();
            return new Respuesta(e, cuerpo);
        }
    }

    final ApiClientTest.ThrottleEspia throttle = new ApiClientTest.ThrottleEspia();
    final List<Long> avisos = new ArrayList<>();
    final TransporteJson red = new TransporteJson();
    final SteamApi steam = new SteamApi(new ApiClient(throttle, red, avisos::add, () -> false));

    @Test void aliasBuenosDevuelveNombreYFecha() throws Exception {
        red.cuerpo = "[{\"newname\":\"Uno\",\"timechanged\":\"1600000000\"},{\"newname\":\"Dos\",\"timechanged\":\"1600000001\"}]";
        List<String[]> out = steam.alias(STEAM_ID);
        assertEquals(2, out.size());
        assertArrayEquals(new String[]{ "Uno", "1600000000" }, out.get(0));
        assertArrayEquals(new String[]{ "Dos", "1600000001" }, out.get(1));
        assertEquals(URL, red.pedidas.get(0));
    }

    @Test void aliasConNombreVacioSeFiltra() throws Exception {
        red.cuerpo = "[{\"newname\":\"\",\"timechanged\":\"1\"},{\"newname\":\"Solo\",\"timechanged\":\"\"}]";
        List<String[]> out = steam.alias(STEAM_ID);
        assertEquals(1, out.size());
        assertArrayEquals(new String[]{ "Solo", "" }, out.get(0));
    }

    @Test void sinAliasElCuerpoNoEsListaYSaleVacio() throws Exception {
        // Steam responde «false» cuando la cuenta no tiene historial de nombres.
        red.cuerpo = "false";
        assertTrue(steam.alias(STEAM_ID).isEmpty());
    }

    @Test void listaVaciaEsVacia() throws Exception {
        red.cuerpo = "[]";
        assertTrue(steam.alias(STEAM_ID).isEmpty());
    }

    @Test void jsonRotoPropagaLaExcepcion() {
        red.cuerpo = "{esto no es json";
        assertThrows(RuntimeException.class, () -> steam.alias(STEAM_ID));
    }

    @Test void un429DeSteamNoCuentaAlFrenoDelCompanionPeroSeReintenta() throws Exception {
        red.estados.add(429);
        red.cuerpo = "[]";
        List<String[]> out = steam.alias(STEAM_ID);
        assertTrue(out.isEmpty());
        assertEquals(2, red.pedidas.size(), "textoCon429 reintenta igual, sea cual sea el host");
        assertEquals(0, throttle.cuatrocientosVeintinueve, "Steam no cuenta al freno del companion");
        assertTrue(avisos.isEmpty(), "sin aviso de pausa: la pausa es cosa del companion");
    }

    @Test void dosReintentosComoMaximoYLuegoPropagaHttp429() {
        red.estados.add(429); red.estados.add(429); red.estados.add(429);
        IOException e = assertThrows(IOException.class, () -> steam.alias(STEAM_ID));
        assertEquals("HTTP 429", e.getMessage());
        assertEquals(3, red.pedidas.size());
        assertEquals(0, throttle.cuatrocientosVeintinueve, "sigue sin contar al freno del companion");
    }
}
