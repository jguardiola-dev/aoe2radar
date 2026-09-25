package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Los vínculos entre cuentas del mismo jugador (ver service.Familias): sin red, sin Swing. */
class FamiliasTest {

    final Familias svc = new Familias();

    // ----- marcarVinculo ---------------------------------------------------

    @Test void marcarVinculoFamiliaNueva() {
        List<Player> jugadores = new ArrayList<>(List.of(
                new Player(5, "Alfa", "g"),
                new Player(3, "Beta", "g")));
        boolean cambio = svc.marcarVinculo(jugadores, Set.of(5L, 3L));
        assertTrue(cambio);
        assertEquals(3L, jugadores.get(0).vinculo());
        assertEquals(3L, jugadores.get(1).vinculo());
    }

    @Test void marcarVinculoUneDosFamilias() {
        List<Player> jugadores = new ArrayList<>(List.of(
                new Player(3, "Alfa", "g", 3),
                new Player(5, "Beta", "g", 3),
                new Player(7, "Gamma", "g", 7),
                new Player(9, "Delta", "g", 7)));
        boolean cambio = svc.marcarVinculo(jugadores, Set.of(3L, 5L, 7L, 9L));
        assertTrue(cambio);
        for (Player p : jugadores) assertEquals(3L, p.vinculo());
    }

    @Test void marcarVinculoMismaFamiliaSinCambio() {
        List<Player> jugadores = new ArrayList<>(List.of(
                new Player(3, "Alfa", "g", 3),
                new Player(5, "Beta", "g", 3)));
        boolean cambio = svc.marcarVinculo(jugadores, Set.of(3L, 5L));
        assertFalse(cambio);
        assertEquals(3L, jugadores.get(0).vinculo());
        assertEquals(3L, jugadores.get(1).vinculo());
    }

    // ----- sanearVinculosHuerfanos ------------------------------------------

    @Test void sanearVinculosHuerfanosDeshaceFamiliaDeUno() {
        List<Player> jugadores = new ArrayList<>(List.of(
                new Player(3, "Alfa", "g", 3),
                new Player(5, "Beta", "g", 3),
                new Player(9, "Huerfano", "g", 9)));
        boolean cambio = svc.sanearVinculosHuerfanos(jugadores);
        assertTrue(cambio);
        assertEquals(3L, jugadores.get(0).vinculo());
        assertEquals(3L, jugadores.get(1).vinculo());
        assertEquals(0L, jugadores.get(2).vinculo());
    }

    @Test void sanearVinculosHuerfanosSinHuerfanosNoCambia() {
        List<Player> jugadores = new ArrayList<>(List.of(
                new Player(3, "Alfa", "g", 3),
                new Player(5, "Beta", "g", 3)));
        boolean cambio = svc.sanearVinculosHuerfanos(jugadores);
        assertFalse(cambio);
        assertEquals(3L, jugadores.get(0).vinculo());
        assertEquals(3L, jugadores.get(1).vinculo());
    }

    // ----- mejorAlt ----------------------------------------------------------

    @Test void mejorAltSinHermanas() {
        List<Player> jugadores = List.of(new Player(1, "Yo", "g"));
        Map<Long, Integer> eloWatch = new HashMap<>(Map.of(1L, 1000));
        assertNull(svc.mejorAlt(1L, jugadores, null, eloWatch));
    }

    @Test void mejorAltHermanaConMenosElo() {
        List<Player> jugadores = List.of(
                new Player(1, "Yo", "g", 5),
                new Player(2, "Hermana", "g", 5));
        Map<Long, Integer> eloWatch = new HashMap<>(Map.of(1L, 1200, 2L, 900));
        assertNull(svc.mejorAlt(1L, jugadores, null, eloWatch));
    }

    @Test void mejorAltHermanaConMasElo() {
        List<Player> jugadores = List.of(
                new Player(1, "Yo", "g", 5),
                new Player(2, "Hermana", "g", 5));
        Map<Long, Integer> eloWatch = new HashMap<>(Map.of(1L, 1000, 2L, 1200));
        String[] alt = svc.mejorAlt(1L, jugadores, null, eloWatch);
        assertNotNull(alt);
        assertArrayEquals(new String[]{ "Hermana", "1200", "2" }, alt);
    }

    @Test void mejorAltEmpate() {
        List<Player> jugadores = List.of(
                new Player(1, "Yo", "g", 5),
                new Player(2, "Hermana", "g", 5));
        Map<Long, Integer> eloWatch = new HashMap<>(Map.of(1L, 1000, 2L, 1000));
        assertNull(svc.mejorAlt(1L, jugadores, null, eloWatch));
    }

    @Test void mejorAltHermanasSoloEnFamiliaDeProfileService() {
        List<Player> jugadores = List.of(new Player(1, "Yo", "g"));   // sin vínculo local (vinculo() == 0)
        Map<Long, String> familiaConsultada = Map.of(3L, "Lejana");
        Map<Long, Integer> eloWatch = new HashMap<>(Map.of(1L, 1000, 3L, 1500));
        String[] alt = svc.mejorAlt(1L, jugadores, familiaConsultada, eloWatch);
        assertNotNull(alt);
        assertArrayEquals(new String[]{ "Lejana", "1500", "3" }, alt);
    }

    @Test void mejorAltEloDesconocido() {
        List<Player> jugadores = List.of(
                new Player(1, "Yo", "g", 5),
                new Player(2, "Hermana", "g", 5));
        Map<Long, Integer> eloWatch = new HashMap<>(Map.of(1L, 1000));   // sin entrada para la hermana
        assertNull(svc.mejorAlt(1L, jugadores, null, eloWatch));
    }

    // ----- vincularExistentes --------------------------------------------------

    @Test void vincularExistentesInterseccionConSeguidos() {
        List<Perfil.Vinculada> vinc = List.of(
                new Perfil.Vinculada(2, "A", "ES", 10),
                new Perfil.Vinculada(3, "B", "FR", 5));
        Set<Long> familia = svc.vincularExistentes(1L, vinc, id -> id == 2L);
        assertEquals(Set.of(1L, 2L), familia);
    }

    @Test void vincularExistentesSinNingunoSeguido() {
        List<Perfil.Vinculada> vinc = List.of(new Perfil.Vinculada(2, "A", "ES", 10));
        Set<Long> familia = svc.vincularExistentes(1L, vinc, id -> false);
        assertEquals(Set.of(1L), familia);
    }
}
