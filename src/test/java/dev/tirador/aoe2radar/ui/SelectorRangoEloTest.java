package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Solo la lógica que no necesita pantalla: rellenar tramos, leer/escribir el rango y
 * que alCambiar no se dispare por los cambios de selección que el propio componente hace
 * mientras está «rellenando» (para no confundirlos con una elección del usuario).
 */
class SelectorRangoEloTest {

    private SelectorRangoElo nuevo() {
        return new SelectorRangoElo(null, tr -> tr);
    }

    @Test
    void sinTramosElRangoEsTodos() {
        SelectorRangoElo s = nuevo();
        assertEquals("*", s.rango());
    }

    @Test
    void tramosRellenaYFijaElRangoActual() {
        SelectorRangoElo s = nuevo();
        s.tramos(List.of("0-800", "800-1200", "1200+"), "800-1200");
        assertEquals("800-1200", s.rango());
    }

    @Test
    void rangoPersonalizadoSeConserva() {
        SelectorRangoElo s = nuevo();
        s.tramos(List.of("0-800", "800-1200", "1200+"), "*");
        s.rango("800-1200|*");
        assertEquals("800-1200|*", s.rango());
    }

    @Test
    void rangoNuloOAsteriscoEquivaleATodos() {
        SelectorRangoElo s = nuevo();
        s.tramos(List.of("0-800", "800-1200"), "*");
        s.rango("0-800");
        assertEquals("0-800", s.rango());
        s.rango((String) null);
        assertEquals("*", s.rango());
    }

    @Test
    void alCambiarNoSeDisparaMientrasRellenaProgramaticamente() {
        SelectorRangoElo s = nuevo();
        AtomicInteger llamadas = new AtomicInteger();
        s.alCambiar = r -> llamadas.incrementAndGet();
        s.tramos(List.of("0-800", "800-1200", "1200+"), "*");
        s.rango("800-1200");
        s.rango("1200+");
        assertEquals(0, llamadas.get());
    }

    @Test
    void sinRangoPersonalizadoElCampoQuedaVacio() {
        SelectorRangoElo s = nuevo();
        assertNull(s.personalizado);
    }
}
