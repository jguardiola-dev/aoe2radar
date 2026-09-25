package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.event.MouseAdapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ClicEnFondo.esFondoDeseleccionable con dobles de las vistas (Anfitrion falso: cada excepción es
 * un componente de prueba, no una vista real). Sin pantalla: los componentes se crean en el EDT
 * (invokeAndWait) pero nunca se muestran.
 */
class ClicEnFondoTest {

    /** Doble de Anfitrion: cada método devuelve el componente de prueba que le demos (o null). */
    private static final class AnfitrionFalso implements ClicEnFondo.Anfitrion {
        JLabel cabLabel; JPanel sujetosPanel; JTable tablaPartidas; JTable tablaDirectos; JComponent panelRatings;
        boolean seleccion; boolean limpiada; boolean fichaOcultada;

        @Override public JLabel cabeceraWatchlist() { return cabLabel; }
        @Override public JPanel sujetosPanel() { return sujetosPanel; }
        @Override public JTable tablaPartidas() { return tablaPartidas; }
        @Override public JTable tablaDirectos() { return tablaDirectos; }
        @Override public JComponent panelRatings() { return panelRatings; }
        @Override public void ocultarDetalleTechTree() { fichaOcultada = true; }
        @Override public boolean haySeleccionWatchlist() { return seleccion; }
        @Override public void limpiarSeleccionWatchlist() { limpiada = true; }
    }

    @Test
    void controlesNuncaSonFondoDeseleccionable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AnfitrionFalso a = new AnfitrionFalso();
            assertFalse(ClicEnFondo.esFondoDeseleccionable(new JButton("x"), a));
            assertFalse(ClicEnFondo.esFondoDeseleccionable(new JTable(), a));
            assertFalse(ClicEnFondo.esFondoDeseleccionable(new JSplitPane(), a));
            assertFalse(ClicEnFondo.esFondoDeseleccionable(new JProgressBar(), a));
        });
    }

    @Test
    void etiquetaConListenerDeRatonNoEsFondo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AnfitrionFalso a = new AnfitrionFalso();
            JLabel clicable = new JLabel("firma");
            clicable.addMouseListener(new MouseAdapter() { });
            assertFalse(ClicEnFondo.esFondoDeseleccionable(clicable, a));
        });
    }

    @Test
    void descendienteDeLaCabeceraDeWatchlistNoEsFondo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AnfitrionFalso a = new AnfitrionFalso();
            a.cabLabel = new JLabel("Nick / ELO / Forma");
            JPanel hijo = new JPanel();
            a.cabLabel.add(hijo);
            assertFalse(ClicEnFondo.esFondoDeseleccionable(hijo, a));
        });
    }

    @Test
    void descendienteDeSujetosPanelNoEsFondo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AnfitrionFalso a = new AnfitrionFalso();
            a.sujetosPanel = new JPanel();
            JLabel nombreSujeto = new JLabel("Partidas de: fulano");
            a.sujetosPanel.add(nombreSujeto);
            assertFalse(ClicEnFondo.esFondoDeseleccionable(nombreSujeto, a));
        });
    }

    @Test
    void descendienteDeTablaDirectosNoEsFondoYNullNoRompe() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AnfitrionFalso a = new AnfitrionFalso();
            a.tablaDirectos = new JTable();
            JPanel hijo = new JPanel();
            a.tablaDirectos.add(hijo);
            assertFalse(ClicEnFondo.esFondoDeseleccionable(hijo, a));

            AnfitrionFalso sinDirectos = new AnfitrionFalso();   // directos == null en la ventana real
            assertTrue(ClicEnFondo.esFondoDeseleccionable(new JPanel(), sinDirectos));
        });
    }

    @Test
    void descendienteDelPanelDeRatingsNoEsFondoYNullNoRompe() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AnfitrionFalso a = new AnfitrionFalso();
            a.panelRatings = new JPanel();
            JLabel campana = new JLabel();
            a.panelRatings.add(campana);
            assertFalse(ClicEnFondo.esFondoDeseleccionable(campana, a));

            AnfitrionFalso sinRatings = new AnfitrionFalso();   // ratings == null en la ventana real
            assertTrue(ClicEnFondo.esFondoDeseleccionable(new JLabel(), sinRatings));
        });
    }

    @Test
    void elHuecoBajoLasFilasDeLaTablaDePartidasNoEsFondo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AnfitrionFalso a = new AnfitrionFalso();
            a.tablaPartidas = new JTable();
            JScrollPane visor = new JScrollPane(a.tablaPartidas);
            JPanel esquina = new JPanel();
            visor.setCorner(JScrollPane.LOWER_RIGHT_CORNER, esquina);   // no es la tabla, pero cuelga del mismo JScrollPane
            assertFalse(ClicEnFondo.esFondoDeseleccionable(esquina, a));
        });
    }

    @Test
    void unPanelSinRelacionConNingunaVistaSiEsFondo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AnfitrionFalso a = new AnfitrionFalso();
            assertTrue(ClicEnFondo.esFondoDeseleccionable(new JPanel(), a));
        });
    }
}
