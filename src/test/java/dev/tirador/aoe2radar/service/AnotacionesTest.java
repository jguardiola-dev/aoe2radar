package dev.tirador.aoe2radar.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Caracterización de service.Anotaciones (antes, la parte sin diálogo de pedirAlias/pedirNota/borrarNota de la
 * app): con mapas y config en memoria, sin disco.
 */
class AnotacionesTest {

    final Map<Long, String> alias = new HashMap<>();
    final Map<Long, String> notas = new HashMap<>();
    final List<String[]> guardado = new ArrayList<>();   // [clave, valor] de cada llamada a guardarConfig
    final Anotaciones anotaciones = new Anotaciones(alias, notas, (k, v) -> guardado.add(new String[]{ k, v }));

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
        assertEquals("", guardado.get(0)[1]);
    }

    @Test void ponerAliasIgualAlOriginalLoQuitaDelMapaPeroGuardaElTextoTalCual() {
        // Comportamiento de la 1.1, no se toca (ver DEUDA): al escribir el mismo nombre original, el mapa lo quita,
        // pero la config se guarda con ese texto (no con ""), así que al recargar reaparecería.
        alias.put(1L, "Apodo");
        anotaciones.ponerAlias(1L, "Original", "Original");
        assertNull(alias.get(1L), "el mapa en memoria lo trata como «sin alias»");
        assertEquals("alias_1", guardado.get(0)[0]);
        assertEquals("Original", guardado.get(0)[1], "la config guarda el texto tal cual, no vacío");
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
        assertEquals("", guardado.get(0)[1]);
    }

    @Test void notaDeSinNotaEsNull() {
        assertNull(anotaciones.notaDe(5L));
    }

    // ----- nombreVisible ----------------------------------------------------------

    @Test void nombreVisibleConAliasDevuelveElAlias() {
        alias.put(2L, "Capitán");
        assertEquals("Capitán", anotaciones.nombreVisible(2L, "NombreReal"));
    }

    @Test void nombreVisibleSinAliasDevuelveElOriginal() {
        assertEquals("NombreReal", anotaciones.nombreVisible(2L, "NombreReal"));
    }

    @Test void nombreVisibleConAliasEnBlancoDevuelveElOriginal() {
        alias.put(2L, "   ");
        assertEquals("NombreReal", anotaciones.nombreVisible(2L, "NombreReal"));
    }
}
