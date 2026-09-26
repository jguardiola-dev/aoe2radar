package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
