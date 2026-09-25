package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.CivFila;
import dev.tirador.aoe2radar.model.Matchup;
import dev.tirador.aoe2radar.model.VentanaStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de caracterización de CalculoStats (Fase 2, StatsService): un caso normal y uno límite por cálculo
 * público, con datos hechos a mano (sin red, sin sfr-data real).
 */
class CalculoStatsTest {

    static VentanaStats ventana(List<String> tramos, Map<String, Map<String, Integer>> mapasPorModo, List<CivFila> civs, List<Matchup> mu) {
        return new VentanaStats("30", "2026-08-26", "2026-09-25", 30, "", tramos, Map.of(), Map.of(), mapasPorModo, civs, mu);
    }

    // ----- tramoEnRango -----

    @Test void tramoEnRangoSinRangoOConAsteriscoAceptaCualquierTramo() {
        List<String> tramos = List.of("0-1000", "1000-1200", "1200+");
        assertTrue(CalculoStats.tramoEnRango("1000-1200", tramos, null));
        assertTrue(CalculoStats.tramoEnRango("1000-1200", tramos, "*"));
        assertTrue(CalculoStats.tramoEnRango("1000-1200", tramos, "*|*"));
    }

    @Test void tramoEnRangoConDosExtremosIncluyeLosDeEnmedio() {
        List<String> tramos = List.of("0-1000", "1000-1200", "1200-1400", "1400+");
        assertTrue(CalculoStats.tramoEnRango("1200-1400", tramos, "1000-1200|1400+"));
        assertFalse(CalculoStats.tramoEnRango("0-1000", tramos, "1000-1200|1400+"), "fuera del rango pedido");
    }

    @Test void tramoEnRangoDeUnTramoQueNoExisteDaFalso() {
        List<String> tramos = List.of("0-1000", "1000+");
        assertFalse(CalculoStats.tramoEnRango("2000+", tramos, "0-1000"), "el tramo de la fila no está en la lista de la ventana");
    }

    // ----- wilson -----

    @Test void wilsonConMuestraNormalDaUnIntervaloCentradoEnLaMedia() {
        double[] iv = CalculoStats.wilson(60, 100);   // 60 %
        assertTrue(iv[0] < 60 && iv[1] > 60, "el intervalo rodea la media: " + iv[0] + "-" + iv[1]);
        assertTrue(iv[0] > 45 && iv[1] < 75, "95 % de Wilson con n=100 no es enorme: " + iv[0] + "-" + iv[1]);
    }

    @Test void wilsonSinPartidasDaElRangoCompleto() {
        assertArrayEquals(new double[]{ 0, 100 }, CalculoStats.wilson(0, 0), "sin muestra, sin información: 0-100 (evita la división por cero)");
    }

    // ----- agregarCivs -----

    @Test void agregarCivsSumaLasFilasQuePasanLosFiltros() {
        VentanaStats v = ventana(List.of("0-1000", "1000+"), Map.of(), List.of(
                new CivFila("rm_1v1", "arabia", "0-1000", "aztecs", 10, 6, 6000),
                new CivFila("rm_1v1", "arabia", "1000+", "aztecs", 5, 2, 3000),
                new CivFila("rm_1v1", "arena", "0-1000", "aztecs", 20, 8, 12000),     // otro mapa: fuera con mapa="arabia"
                new CivFila("rm_2v2", "arabia", "0-1000", "aztecs", 99, 90, 90000)),  // otro modo: siempre fuera
                List.of());
        Map<String, CivAgg> agg = CalculoStats.agregarCivs(v, "rm_1v1", "arabia", "*");
        CivAgg a = agg.get("aztecs");
        assertNotNull(a);
        assertEquals(15, a.n(), "10 + 5, sin el de arena ni el de rm_2v2");
        assertEquals(8, a.w());
        assertEquals(9000, a.d());
    }

    @Test void agregarCivsSinFilasQueCumplanDaMapaVacio() {
        VentanaStats v = ventana(List.of("0-1000"), Map.of(), List.of(new CivFila("rm_1v1", "arabia", "0-1000", "aztecs", 10, 6, 6000)), List.of());
        assertTrue(CalculoStats.agregarCivs(v, "rm_2v2", "arabia", "*").isEmpty(), "ningún modo rm_2v2 en los datos");
    }

    // ----- partidasPorMapa -----

    @Test void partidasPorMapaSinTramoUsaElTotalDeLaVentana() {
        VentanaStats v = ventana(List.of("0-1000"), Map.of("rm_1v1", Map.of("arabia", 100, "arena", 40)), List.of(), List.of());
        assertEquals(Map.of("arabia", 100, "arena", 40), CalculoStats.partidasPorMapa(v, "rm_1v1", "*"));
    }

    @Test void partidasPorMapaConTramoCuentaFilasYDivideEntreJugadoresPorPartida() {
        VentanaStats v = ventana(List.of("0-1000", "1000+"), Map.of(), List.of(
                new CivFila("rm_1v1", "arabia", "0-1000", "aztecs", 10, 6, 6000),
                new CivFila("rm_1v1", "arabia", "0-1000", "britons", 10, 4, 6000),
                new CivFila("rm_1v1", "arabia", "1000+", "aztecs", 8, 5, 4000)),   // fuera del tramo pedido
                List.of());
        Map<String, Integer> mapas = CalculoStats.partidasPorMapa(v, "rm_1v1", "0-1000");
        assertEquals(10, mapas.get("arabia"), "20 filas de civ / 2 jugadores por partida en 1v1");
    }

    // ----- civPorMapa -----

    @Test void civPorMapaAgrupaPorMapaLaCivPedida() {
        VentanaStats v = ventana(List.of("0-1000"), Map.of(), List.of(
                new CivFila("rm_1v1", "arabia", "0-1000", "aztecs", 10, 6, 0),
                new CivFila("rm_1v1", "arena", "0-1000", "aztecs", 5, 1, 0),
                new CivFila("rm_1v1", "arabia", "0-1000", "britons", 20, 11, 0)),   // otra civ: fuera
                List.of());
        Map<String, CivAgg> porMapa = CalculoStats.civPorMapa(v, "rm_1v1", "*", "aztecs");
        assertEquals(2, porMapa.size());
        assertEquals(10, porMapa.get("arabia").n());
        assertEquals(5, porMapa.get("arena").n());
    }

    @Test void civPorMapaSinDatosDeEsaCivDaMapaVacio() {
        VentanaStats v = ventana(List.of("0-1000"), Map.of(), List.of(new CivFila("rm_1v1", "arabia", "0-1000", "aztecs", 10, 6, 0)), List.of());
        assertTrue(CalculoStats.civPorMapa(v, "rm_1v1", "*", "britons").isEmpty());
    }

    // ----- duracionMedia -----

    @Test void duracionMediaFormateaMinutosYSegundos() {
        assertEquals("21:40 min", CalculoStats.duracionMedia(1300, 1), "1300 s = 21:40");
        assertEquals("25:00 min", CalculoStats.duracionMedia(3000, 2), "3000 s / 2 partidas = 1500 s = 25:00");
    }

    @Test void duracionMediaSinPartidasDaGuion() {
        assertEquals("-", CalculoStats.duracionMedia(1000, 0), "n=0: evita la división por cero");
    }
}
