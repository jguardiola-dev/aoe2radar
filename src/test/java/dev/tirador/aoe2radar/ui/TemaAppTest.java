package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TemaApp sin pantalla: solo lo que no depende de tener una ventana en marcha. Con ventana=null,
 * aplicarTema salta toda la rama que toca componentes (ComponentesTema), así que se puede comprobar su efecto
 * sobre Tema.temaOscuroActivo sin crear ni un JFrame. FlatLaf está en el classpath de test (dependencia normal
 * de Maven), así que aplicarTema(TEMA_OSCURO/TEMA_CLARO, null) tiene éxito de verdad, no solo "no falla".
 */
class TemaAppTest {

    @AfterEach void restaurarTema() { Tema.temaOscuroActivo = false; }

    @Test void aplicarTemaOscuroActivaTemaOscuroActivo() {
        assertTrue(TemaApp.aplicarTema(TemaApp.TEMA_OSCURO, null));
        assertTrue(Tema.temaOscuroActivo);
    }

    @Test void aplicarTemaClaroDesactivaTemaOscuroActivo() {
        Tema.temaOscuroActivo = true;
        assertTrue(TemaApp.aplicarTema(TemaApp.TEMA_CLARO, null));
        assertFalse(Tema.temaOscuroActivo);
    }

    @Test void temaValidoDevuelveClaroYOscuroTalCual() {
        assertEquals(TemaApp.TEMA_CLARO, TemaApp.temaValido(TemaApp.TEMA_CLARO));
        assertEquals(TemaApp.TEMA_OSCURO, TemaApp.temaValido(TemaApp.TEMA_OSCURO));
    }

    @Test void temaValidoCaeASistemaConCualquierOtraCosa() {
        assertEquals(TemaApp.TEMA_SISTEMA, TemaApp.temaValido("lo-que-sea"));
        assertEquals(TemaApp.TEMA_SISTEMA, TemaApp.temaValido(TemaApp.TEMA_SISTEMA));
    }

    // ----- F12 de la revisión general (1.4): el cambio en caliente llega a los popups guardados en campos -----

    /** Un popup guardado en un campo y oculto no cuelga de ninguna ventana: sin registrarlo, se quedaba con el tema
     *  viejo. Con ventana (cambio desde el menú) le llega el nuevo; sin ventana (el arranque, el harness) no se toca. */
    @Test void elCambioDeTemaEnCalienteLlegaAUnPopupRegistrado() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                assertTrue(TemaApp.aplicarTema(TemaApp.TEMA_CLARO, null));
                javax.swing.JPopupMenu pm = TemaApp.registrarPopup(new javax.swing.JPopupMenu());
                java.awt.Color claro = pm.getBackground();
                assertTrue(TemaApp.aplicarTema(TemaApp.TEMA_OSCURO, null));
                assertEquals(claro, pm.getBackground(), "sin ventana, aplicarTema no recorre nada");
                assertTrue(TemaApp.aplicarTema(TemaApp.TEMA_OSCURO, new VentanaFalsa()));
                assertNotEquals(claro, pm.getBackground(), "con ventana, el popup registrado recibe el tema oscuro");
            } finally {
                TemaApp.aplicarTema(TemaApp.TEMA_CLARO, null);
            }
        });
    }

    /** Lo mínimo de ComponentesTema para la rama «con ventana», sin crear ninguna ventana de verdad. */
    private static final class VentanaFalsa implements ComponentesTema {
        final javax.swing.JPanel raiz = new javax.swing.JPanel();
        final javax.swing.JTable tabla = new javax.swing.JTable();
        final javax.swing.JButton azar = new javax.swing.JButton(), gte = new javax.swing.JButton(), cafe = new javax.swing.JButton();
        @Override public java.awt.Component raiz() { return raiz; }
        @Override public javax.swing.JPopupMenu configMenu() { return null; }
        @Override public javax.swing.JLabel nota() { return null; }
        @Override public javax.swing.JLabel firma() { return null; }
        @Override public javax.swing.JLabel watchPista1() { return null; }
        @Override public javax.swing.JLabel watchPista2() { return null; }
        @Override public javax.swing.JLabel watchPista3() { return null; }
        @Override public javax.swing.border.TitledBorder tituloWatch() { return null; }
        @Override public javax.swing.JTable table() { return tabla; }
        @Override public javax.swing.JButton azarBtn() { return azar; }
        @Override public javax.swing.JButton gteBtn() { return gte; }
        @Override public javax.swing.JToggleButton resultadosBtn() { return null; }
        @Override public javax.swing.JButton cafeBtn() { return cafe; }
        @Override public boolean mostrarResultados() { return false; }
    }
}
