package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static dev.tirador.aoe2radar.ui.Componentes.colorHex;
import static dev.tirador.aoe2radar.ui.Componentes.colorWr;
import static dev.tirador.aoe2radar.ui.Componentes.etiquetaK;
import static dev.tirador.aoe2radar.ui.Componentes.pasoBonito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lo puro de ui.Componentes (sin crear ningún JComponent): colorHex, colorWr con los dos temas, pasoBonito y
 * etiquetaK. temaOscuroActivo es estado compartido con el resto de la app: se restaura tras cada test.
 */
class ComponentesTest {

    @AfterEach void restaurarTema() { Tema.temaOscuroActivo = false; }

    @Test void colorHexFormateaEnMinusculasConAlmohadilla() {
        assertEquals("#ff0080", colorHex(new Color(0xff, 0x00, 0x80)));
        assertEquals("#000000", colorHex(Color.BLACK));
    }

    @Test void colorWrGrisSinPartidas() {
        assertEquals(Color.GRAY, colorWr(0, 0));
    }

    @Test void colorWrVerdeDesde52PorCientoTemaClaro() {
        Tema.temaOscuroActivo = false;
        assertEquals(new Color(0x2e, 0x7d, 0x32), colorWr(52, 100));
    }

    @Test void colorWrVerdeDesde52PorCientoTemaOscuro() {
        Tema.temaOscuroActivo = true;
        assertEquals(new Color(0x7c, 0xc9, 0x7f), colorWr(52, 100));
    }

    @Test void colorWrRojoHasta48PorCientoTemaClaro() {
        Tema.temaOscuroActivo = false;
        assertEquals(new Color(0xc6, 0x28, 0x28), colorWr(48, 100));
    }

    @Test void colorWrRojoHasta48PorCientoTemaOscuro() {
        Tema.temaOscuroActivo = true;
        assertEquals(new Color(0xe5, 0x73, 0x73), colorWr(48, 100));
    }

    @Test void pasoBonitoRedondeaAUnDosCincoODiez() {
        assertEquals(1, pasoBonito(0));
        assertEquals(50, pasoBonito(42));
        assertEquals(100, pasoBonito(85));
        assertEquals(200, pasoBonito(150));
        assertEquals(500, pasoBonito(450));
        assertEquals(1000, pasoBonito(800));
    }

    @Test void etiquetaKAbreviaMiles() {
        assertEquals("999", etiquetaK(999));
        assertEquals("1k", etiquetaK(1000));
        assertEquals("1.5k", etiquetaK(1500));
    }
}
