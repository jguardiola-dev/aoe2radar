package dev.tirador.aoe2radar.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Caracterización de service.AnotacionesService (antes, la parte sin diálogo de pedirAlias/pedirNota/borrarNota de
 * la app): con mapas y config en memoria, sin disco.
 */
class AnotacionesServiceTest {

    final Map<Long, String> alias = new HashMap<>();
    final Map<Long, String> notas = new HashMap<>();
    final List<String[]> guardado = new ArrayList<>();   // [clave, valor] de cada llamada a guardarConfig
    final AnotacionesService anotaciones = new AnotacionesService(alias, notas, (k, v) -> guardado.add(new String[]{ k, v }));

    // ----- alias --------------------------------------------------------------

    @Test void ponerAliasLoGuardaYPersiste() {
        anotaciones.ponerAlias(1L, "Original", "Apodo");
        assertEquals("Apodo", alias.get(1L));
        assertEquals(List.of("alias_1", "Apodo"), List.of(guardado.get(0)));
    }

    @Test void ponerAliasVacioLoQuita() {
        alias.put(1L, "Apodo");
        anotaciones.ponerAlias(1L, "Original", "");
        assertNull(alias.get(1L));
        assertEquals(1, guardado.size(), "se guarda una sola vez");
        assertEquals("alias_1", guardado.get(0)[0]);
        assertEquals("", guardado.get(0)[1]);
    }

    @Test void ponerAliasIgualAlOriginalLoQuitaDelMapaYBorraLaClaveDeConfig() {
        // Arreglo de DEUDA fila 101 (fase 4): antes, al escribir el mismo nombre original, el mapa lo quitaba pero
        // la config se guardaba con ese texto (no con ""), así que al recargar la app el alias reaparecía.
        alias.put(1L, "Apodo");
        anotaciones.ponerAlias(1L, "Original", "Original");
        assertNull(alias.get(1L), "el mapa en memoria lo trata como «sin alias»");
        assertEquals(1, guardado.size(), "se guarda una sola vez");
        assertEquals("alias_1", guardado.get(0)[0]);
        assertEquals("", guardado.get(0)[1], "la config también queda vacía: no debe reaparecer al recargar");
    }

    @Test void aliasDeSinAliasEsNull() {
        assertNull(anotaciones.aliasDe(9L));
    }

    // ----- notas ----------------------------------------------------------------

    @Test void ponerNotaLaGuardaYPersiste() {
        anotaciones.ponerNota(5L, "cuidado con el rush");
        assertEquals("cuidado con el rush", notas.get(5L));
        assertEquals("nota_5", guardado.get(0)[0]);
        assertEquals("cuidado con el rush", guardado.get(0)[1]);
    }

    @Test void ponerNotaVaciaLaQuita() {
        notas.put(5L, "algo");
        anotaciones.ponerNota(5L, "");
        assertNull(notas.get(5L));
        assertEquals(1, guardado.size(), "se guarda una sola vez");
        assertEquals("nota_5", guardado.get(0)[0]);
        assertEquals("", guardado.get(0)[1]);
    }

    @Test void notaDeSinNotaEsNull() {
        assertNull(anotaciones.notaDe(5L));
    }
}
