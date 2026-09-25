package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Solo la lógica que no necesita pantalla: rellenar tramos, leer/escribir el rango y
 * que alCambiar no se dispare por los cambios de selección que el propio componente hace
 * mientras está «rellenando» (para no confundirlos con una elección del usuario). El
 * componente es Swing: cada cuerpo de test corre en el EDT, igual que en la app.
 */
class SelectorRangoEloTest {

    private SelectorRangoElo nuevo() {
        return new SelectorRangoElo(null, tr -> tr);
    }

    @Test
    void sinTramosElRangoEsTodos() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SelectorRangoElo s = nuevo();
            assertEquals("*", s.rango());
        });
    }

    @Test
    void tramosRellenaYFijaElRangoActual() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SelectorRangoElo s = nuevo();
            s.tramos(List.of("0-800", "800-1200", "1200+"), "800-1200");
            assertEquals("800-1200", s.rango());
        });
    }

    @Test
    void rangoPersonalizadoSeConserva() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SelectorRangoElo s = nuevo();
            s.tramos(List.of("0-800", "800-1200", "1200+"), "*");
            s.rango("800-1200|*");
            assertEquals("800-1200|*", s.rango());
        });
    }

    @Test
    void rangoNuloOAsteriscoEquivaleATodos() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SelectorRangoElo s = nuevo();
            s.tramos(List.of("0-800", "800-1200"), "*");
            s.rango("0-800");
            assertEquals("0-800", s.rango());
            s.rango((String) null);
            assertEquals("*", s.rango());
        });
    }

    @Test
    void alCambiarNoSeDisparaMientrasRellenaProgramaticamente() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SelectorRangoElo s = nuevo();
            AtomicInteger llamadas = new AtomicInteger();
            s.alCambiar = r -> llamadas.incrementAndGet();
            s.tramos(List.of("0-800", "800-1200", "1200+"), "*");
            s.rango("800-1200");
            s.rango("1200+");
            assertEquals(0, llamadas.get());
        });
    }
}
