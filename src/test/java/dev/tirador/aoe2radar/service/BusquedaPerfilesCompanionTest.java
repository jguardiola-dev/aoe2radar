package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BusquedaPerfilesCompanion (service.BusquedaPerfiles) sin red: un Transporte falso responde el JSON de
 * /profiles?search=… y un mapa en memoria hace de índice local (NOMBRES_AYER/ELO_AYER de sfr-data en la app).
 */
class BusquedaPerfilesCompanionTest {

    /** Responde lo mismo a cualquier URL (cuerpo/estado configurables) y apunta cada URL pedida. */
    static final class TransporteFalso implements Transporte {
        final List<String> pedidas = new ArrayList<>();
        String cuerpo = "{\"profiles\":[]}";
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
    final Map<Long, String[]> nombresAyer = new HashMap<>();   // pid → {nombre, país}
    final Map<Long, int[]> eloAyer = new HashMap<>();          // pid → {elo1v1, ...}
    final Map<Long, String> paisAprendido = new HashMap<>();
    final BusquedaPerfilesCompanion servicio = new BusquedaPerfilesCompanion(api, nombresAyer, eloAyer,
            (pid, pais) -> paisAprendido.put(pid, pais));

    // ----- sugerir(): local si tiene algo, si no la API -----

    @Test void sugerirConLocalNoLlamaALaApi() {
        nombresAyer.put(1L, new String[]{ "Hera", "es" });
        eloAyer.put(1L, new int[]{ 2000 });
        List<String[]> res = servicio.sugerir("her");
        assertEquals(1, res.size());
        assertEquals("1", res.get(0)[0]);
        assertEquals("Hera", res.get(0)[1]);
        assertTrue(res.get(0)[2].contains("2000"), "el texto local lleva el ELO");
        assertTrue(red.pedidas.isEmpty(), "con resultado local no se llama a la API");
    }

    @Test void sugerirSinLocalVaALaApi() {
        red.cuerpo = "{\"profiles\":[{\"profile_id\":9000001,\"name\":\"Hera\",\"country\":\"ca\",\"games\":10}]}";
        List<String[]> res = servicio.sugerir("hera");
        assertEquals(1, res.size());
        assertEquals("9000001", res.get(0)[0]);
        assertFalse(red.pedidas.isEmpty(), "sin local, pregunta a la API");
    }

    // ----- buscar(): local + API sin duplicados, local primero -----

    @Test void buscarJuntaLocalYApiSinDuplicadosLocalPrimero() {
        nombresAyer.put(1L, new String[]{ "Hera", "es" });
        eloAyer.put(1L, new int[]{ 2000 });
        red.cuerpo = "{\"profiles\":[{\"profile_id\":1,\"name\":\"Hera\",\"country\":\"es\",\"games\":5},"
                + "{\"profile_id\":2,\"name\":\"HeraTwo\",\"country\":\"fr\",\"games\":7}]}";
        List<String[]> res = servicio.buscar("hera");
        assertEquals(2, res.size(), "el 1 de la API no se repite: ya estaba en local");
        assertEquals("1", res.get(0)[0], "local primero");
        assertEquals("2", res.get(1)[0]);
    }

    // MUTACION: si en buscar() se quitara el filtro de duplicados (vistos.add), este test lo detecta:
    // aparecerían 2 filas del id 1 (una de local, otra de la API) en vez de una.
    @Test void buscarNoDuplicaElMismoIdEntreLocalYApi() {
        nombresAyer.put(1L, new String[]{ "Hera", "es" });
        eloAyer.put(1L, new int[]{ 2000 });
        red.cuerpo = "{\"profiles\":[{\"profile_id\":1,\"name\":\"Hera\",\"country\":\"es\",\"games\":5}]}";
        List<String[]> res = servicio.buscar("hera");
        assertEquals(1, res.size(), "mismo id en local y API: una sola fila");
        assertEquals("1", res.get(0)[0]);
    }

    // ----- límite de 8 en el local -----

    @Test void buscarLocalLimitaA8() {
        for (long pid = 1; pid <= 12; pid++) {
            nombresAyer.put(pid, new String[]{ "Hera" + pid, "" });
            eloAyer.put(pid, new int[]{ (int) (100 - pid) });   // orden decreciente de ELO
        }
        List<String[]> res = servicio.sugerir("hera");
        assertEquals(8, res.size(), "el índice local corta en 8");
        assertEquals("1", res.get(0)[0], "el de más ELO primero");
    }

    // ----- la fila de la API con país aprende el país y el texto lleva [pais] y partidas -----

    @Test void filaDeLaApiConPaisAprendeElPaisYElTextoLlevaPaisYPartidas() {
        red.cuerpo = "{\"profiles\":[{\"profile_id\":9000001,\"name\":\"Hera\",\"country\":\"ca\",\"games\":10}]}";
        List<String[]> res = servicio.buscar("hera");
        assertEquals(1, res.size());
        assertEquals("ca", paisAprendido.get(9000001L), "el BiConsumer recibe (pid, pais)");
        assertTrue(res.get(0)[2].contains("[ca]"), "el texto lleva el país entre corchetes");
        assertTrue(res.get(0)[2].contains("10"), "el texto lleva las partidas");
    }

    // ----- error de la API: no revienta -----

    @Test void errorDeLaApiDevuelveSoloLoLocalYNoRevienta() {
        nombresAyer.put(1L, new String[]{ "Hera", "es" });
        eloAyer.put(1L, new int[]{ 2000 });
        red.estado = 500;
        List<String[]> res = assertDoesNotThrow(() -> servicio.buscar("hera"));
        assertEquals(1, res.size(), "solo lo local: la API falló y buscarPerfilesApi devuelve vacío");
        assertEquals("1", res.get(0)[0]);
    }

    @Test void errorDeLaApiSinLocalDevuelveListaVacia() {
        red.estado = 500;
        List<String[]> res = assertDoesNotThrow(() -> servicio.buscar("hera"));
        assertTrue(res.isEmpty());
    }
}
