package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Caracteriza el estado de filtros que hoy son campos sueltos de SpoilerFreeRecs. */
class FiltroStatsTest {

    @Test
    void valoresIniciales() {
        FiltroStats f = new FiltroStats("rm_1v1", "30", "*", "*");
        assertEquals("rm_1v1", f.modo());
        assertEquals("30", f.ventana());
        assertEquals("*", f.mapa());
        assertEquals("*", f.tramo());
    }

    @Test
    void civSeleccionadaEmpiezaEnNull() {
        FiltroStats f = new FiltroStats("rm_1v1", "30", "*", "*");
        assertNull(f.civSeleccionada());
    }

    @Test
    void escritoresYLectoresIndependientes() {
        FiltroStats f = new FiltroStats("rm_1v1", "30", "*", "*");
        f.modo("ew_1v1");
        assertEquals("ew_1v1", f.modo());
        f.ventana("7");
        assertEquals("7", f.ventana());
        f.mapa("arabia");
        assertEquals("arabia", f.mapa());
        f.tramo("1600-1800");
        assertEquals("1600-1800", f.tramo());
        f.civSeleccionada("franks");
        assertEquals("franks", f.civSeleccionada());
        f.civSeleccionada(null);
        assertNull(f.civSeleccionada());
    }
}
