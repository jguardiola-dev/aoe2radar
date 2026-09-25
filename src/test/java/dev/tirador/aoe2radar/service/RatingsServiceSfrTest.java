package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.LadderHist;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Rejilla;
import dev.tirador.aoe2radar.sfrdata.Ladder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RatingsServiceSfr sobre sfrdata.Ladder: pone valores directamente en los estáticos de Ladder (como haría
 * ladderAsegurar tras una carga real) y comprueba que el servicio los devuelve tal cual, sin red. Los estáticos
 * son compartidos con la UI y el harness de capturas: se guardan y se restauran en cada test para no contaminar
 * la suite (mismo motivo que StatsServiceTest con CivStats).
 */
class RatingsServiceSfrTest {

    final RatingsService ratings = RatingsServiceSfr.SISTEMA;

    // valores originales de los estáticos de Ladder, para restaurarlos en @AfterEach
    Map<String, LadderHist> histsOriginal, histsActivosOriginal;
    Map<String, Rejilla> dispersionTodosOriginal, dispersionActivosOriginal;
    Map<String, List<LadderRow>> clanesOriginal;
    String generadoOriginal;
    int minPartidasOriginal, diasOriginal;
    boolean cargandoOriginal;
    String progresoOriginal;

    @BeforeEach void guardar() {
        histsOriginal = Ladder.ladderHists;
        histsActivosOriginal = Ladder.ladderHistsActivos;
        dispersionTodosOriginal = Ladder.dispersionTodos;
        dispersionActivosOriginal = Ladder.dispersionActivos;
        clanesOriginal = Ladder.clanes;
        generadoOriginal = Ladder.ladderGenerado;
        minPartidasOriginal = Ladder.activosMinPartidas;
        diasOriginal = Ladder.activosDias;
        cargandoOriginal = Ladder.ladderCargando;
        progresoOriginal = Ladder.ladderProgreso;
    }

    @AfterEach void restaurar() {
        Ladder.ladderHists = histsOriginal;
        Ladder.ladderHistsActivos = histsActivosOriginal;
        Ladder.dispersionTodos = dispersionTodosOriginal;
        Ladder.dispersionActivos = dispersionActivosOriginal;
        Ladder.clanes = clanesOriginal;
        Ladder.ladderGenerado = generadoOriginal;
        Ladder.activosMinPartidas = minPartidasOriginal;
        Ladder.activosDias = diasOriginal;
        Ladder.ladderCargando = cargandoOriginal;
        Ladder.ladderProgreso = progresoOriginal;
    }

    static LadderHist hist(int total, int min, int mediana) {
        return new LadderHist(total, min, new int[]{ 1, 2, 3 }, mediana, Map.of());
    }

    @Test void histDevuelveActivosSiSePidenYExisten() {
        Ladder.ladderHists = Map.of("rm_1v1", hist(1000, 0, 1200));
        Ladder.ladderHistsActivos = Map.of("rm_1v1", hist(200, 0, 1500));
        assertEquals(1500, ratings.hist("rm_1v1", true).mediana());
        assertEquals(1200, ratings.hist("rm_1v1", false).mediana());
    }

    @Test void histCaeATodosSiNoHayActivosParaEseLadder() {
        Ladder.ladderHists = Map.of("rm_1v1", hist(1000, 0, 1200));
        Ladder.ladderHistsActivos = Map.of();   // sin publicar activos de este ladder
        assertEquals(1200, ratings.hist("rm_1v1", true).mediana(), "sin activos, cae a todos");
        assertNull(ratings.hist("desconocido", true));
    }

    @Test void tieneActivosMiraSoloElMapaDeActivos() {
        // "todos" trae ambas claves: si tieneActivos mirara ladderHists en vez de ladderHistsActivos, las dos darían true.
        Ladder.ladderHists = Map.of("rm_1v1", hist(1000, 0, 1200), "ew_1v1", hist(1000, 0, 1200));
        Ladder.ladderHistsActivos = Map.of("rm_1v1", hist(200, 0, 1500));
        assertTrue(ratings.tieneActivos("rm_1v1"));
        assertFalse(ratings.tieneActivos("ew_1v1"));
    }

    static Rejilla rejilla(int n) { return new Rejilla(n, 0, 0, new int[][]{ { 0, 0, n } }, 1.0, 0.0, 0.9); }

    @Test void dispersionDevuelveActivosSiSePidenYExisten() {
        Ladder.dispersionTodos = Map.of("rm", rejilla(1000));
        Ladder.dispersionActivos = Map.of("rm", rejilla(200));
        assertEquals(200, ratings.dispersion("rm", true).n());
        assertEquals(1000, ratings.dispersion("rm", false).n());
    }

    @Test void dispersionCaeATodosSiNoHayActivosParaEsaFamilia() {
        Ladder.dispersionTodos = Map.of("rm", rejilla(1000));
        Ladder.dispersionActivos = Map.of();
        assertEquals(1000, ratings.dispersion("rm", true).n(), "sin activos, cae a todos");
        assertNull(ratings.dispersion("desconocida", true));
    }

    @Test void dispersionTieneActivosMiraSoloElMapaDeActivos() {
        // "todos" trae ambas familias: si dispersionTieneActivos mirara dispersionTodos, "ew" también daría true.
        Ladder.dispersionTodos = Map.of("rm", rejilla(1000), "ew", rejilla(1000));
        Ladder.dispersionActivos = Map.of("rm", rejilla(200));
        assertTrue(ratings.dispersionTieneActivos("rm"));
        assertFalse(ratings.dispersionTieneActivos("ew"));
    }

    @Test void cargandoSeLeeYSeEscribeComoUnVolatilSimple() {
        Ladder.ladderCargando = false;
        assertFalse(ratings.cargando());
        ratings.cargando(true);
        assertTrue(ratings.cargando());
        assertTrue(Ladder.ladderCargando, "misma variable estática, no una copia");
        ratings.cargando(false);
        assertFalse(ratings.cargando());
    }

    @Test void progresoDevuelveElTextoDeLadder() {
        Ladder.ladderProgreso = "Descargando los resúmenes del ladder…";
        assertEquals("Descargando los resúmenes del ladder…", ratings.progreso());
    }

    @Test void clanesDevuelveElMapaDeLadder() {
        LadderRow fila = new LadderRow(1L, "Jugador", 2000, 1, "es", "R1", 100);
        Ladder.clanes = Map.of("R1", List.of(fila));
        assertEquals(Map.of("R1", List.of(fila)), ratings.clanes());
    }

    @Test void activosMinPartidasYDiasVienenDeLadder() {
        Ladder.activosMinPartidas = 15;
        Ladder.activosDias = 21;
        assertEquals(15, ratings.activosMinPartidas());
        assertEquals(21, ratings.activosDias());
    }

    @Test void generadoDevuelveLaFechaDeLadder() {
        Ladder.ladderGenerado = "2026-09-25T03:00:00Z";
        assertEquals("2026-09-25T03:00:00Z", ratings.generado());
    }
}
