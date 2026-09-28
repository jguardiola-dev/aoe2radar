package dev.tirador.aoe2radar.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReglasPartida.mismoMapa (1.4, smoke test de la 1.3): el socket manda el nombre del mapa traducido y la API en
 * inglés. Con las imágenes que la API da con cada partida (aquí, un mapa a mano), «Islas» e «Islands» son el mismo
 * mapa. Sin red ni disco.
 */
class ReglasPartidaMapaTest {

    static final Map<String, String> IMAGENES = Map.of(
            "islas", "https://cdn.example/maps/islands.png",
            "islands", "https://cdn.example/maps/islands.png",
            "arabia", "https://cdn.example/maps/arabia.png");

    @Test void elMismoMapaConNombreTraducidoYEnInglesEsElMismo() {
        assertTrue(ReglasPartida.mismoMapa("Islas", "Islands", IMAGENES::get));
        assertTrue(ReglasPartida.mismoMapa("Islands", "Islas", IMAGENES::get));
    }

    @Test void mapasDistintosNoSonElMismo() {
        assertFalse(ReglasPartida.mismoMapa("Islas", "Arabia", IMAGENES::get));
        assertFalse(ReglasPartida.mismoMapa("Arena", "Nómada", IMAGENES::get), "sin imagen conocida, solo el nombre");
    }

    @Test void sinImagenSeComparaPorNombreSinMayusculas() {
        assertTrue(ReglasPartida.mismoMapa("Arena", "Arena", k -> null));
        assertTrue(ReglasPartida.mismoMapa("arena", "Arena", k -> null));
        assertFalse(ReglasPartida.mismoMapa("Arena", null, k -> null));
        assertFalse(ReglasPartida.mismoMapa(null, null, k -> null));
    }
}
