package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Caracteriza el historial de navegación que antes eran cinco campos sueltos de SpoilerFreeRecs
 *  ({@code historial}, {@code historialPos}, {@code navegandoAtras} y el record {@code Destino}). */
class AppStateTest {

    private static AppState.Destino d(String vista) { return new AppState.Destino(vista, 0, null, null); }

    @Test
    void alPrincipioNoHayVistaActivaNiHistorial() {
        AppState e = new AppState();
        assertNull(e.actual());
        assertEquals(0, e.tamanoHistorial());
        assertFalse(e.puedeVolver());
        assertFalse(e.puedeAvanzar());
    }

    @Test
    void registrarDestinoLoDejaComoActivo() {
        AppState e = new AppState();
        e.registrarDestino(d("ladder"));
        assertEquals(d("ladder"), e.actual());
        assertEquals(1, e.tamanoHistorial());
        assertEquals(0, e.historialPos());
    }

    @Test
    void registrarElMismoDestinoActivoNoDuplica() {
        AppState e = new AppState();
        e.registrarDestino(d("ladder"));
        e.registrarDestino(d("ladder"));
        assertEquals(1, e.tamanoHistorial());
    }

    @Test
    void registrarDestinosDistintosAcumulaYHabilitaVolver() {
        AppState e = new AppState();
        e.registrarDestino(d("ladder"));
        e.registrarDestino(d("civstats"));
        assertEquals(2, e.tamanoHistorial());
        assertTrue(e.puedeVolver());
        assertFalse(e.puedeAvanzar());
    }

    @Test
    void prepararAtrasSinHistorialDevuelveNull() {
        AppState e = new AppState();
        assertNull(e.prepararAtras());
        e.registrarDestino(d("ladder"));   // un único destino: tampoco hay a dónde volver
        assertNull(e.prepararAtras());
    }

    @Test
    void prepararAtrasYAdelanteMuevenLaPosicion() {
        AppState e = new AppState();
        e.registrarDestino(d("ladder"));
        e.registrarDestino(d("civstats"));
        AppState.Destino atras = e.prepararAtras();
        assertEquals(d("ladder"), atras);
        assertEquals(0, e.historialPos());
        e.dejarDeNavegar();
        AppState.Destino adelante = e.prepararAdelante();
        assertEquals(d("civstats"), adelante);
        assertEquals(1, e.historialPos());
    }

    @Test
    void navegandoAtrasImpideQueRegistrarDestinoAnadaUnaEntradaNueva() {
        AppState e = new AppState();
        e.registrarDestino(d("ladder"));
        e.registrarDestino(d("civstats"));
        e.prepararAtras();   // dentro de un volverAtras(): navegandoAtras queda a true
        assertTrue(e.navegandoAtras());
        e.registrarDestino(d("perfil"));   // como si la vista, al abrirse, intentara registrarse sola
        assertEquals(2, e.tamanoHistorial());   // no se añade: seguimos «navegando»
        e.dejarDeNavegar();
        assertFalse(e.navegandoAtras());
    }

    @Test
    void unDestinoNuevoCortaElAdelante() {
        AppState e = new AppState();
        e.registrarDestino(d("ladder"));
        e.registrarDestino(d("civstats"));
        e.registrarDestino(d("ahora"));
        e.prepararAtras(); e.dejarDeNavegar();   // pos: civstats
        e.prepararAtras(); e.dejarDeNavegar();   // pos: ladder
        assertTrue(e.puedeAvanzar());
        e.registrarDestino(d("techtree"));   // una ruta nueva desde "ladder": borra el "adelante" (civstats, ahora)
        assertFalse(e.puedeAvanzar());
        assertEquals(2, e.tamanoHistorial());   // ladder, techtree
        assertEquals(d("techtree"), e.actual());
    }

    @Test
    void elHistorialNuncaSupera60Entradas() {
        AppState e = new AppState();
        for (int i = 0; i < 65; i++) e.registrarDestino(new AppState.Destino("v" + i, 0, null, null));
        assertEquals(60, e.tamanoHistorial());
        assertEquals(d("v64"), e.actual());   // las 5 más antiguas (v0..v4) se han caído
    }

    @Test
    void losOyentesSeAvisanAlRegistrarUnDestinoNuevo() {
        AppState e = new AppState();
        List<Integer> avisos = new ArrayList<>();
        e.agregarOyente(() -> avisos.add(1));
        e.registrarDestino(d("ladder"));
        e.registrarDestino(d("ladder"));   // duplicado: no avisa
        e.registrarDestino(d("civstats"));
        assertEquals(2, avisos.size());
    }

    @Test
    void dejarDeNavegarNoAvisaALosOyentes() {
        // prepararAtras/dejarDeNavegar son responsabilidad de quien orquesta la navegación (ui.Navegador);
        // AppState no decide por su cuenta cuándo repintar los botones de esa secuencia (ver su javadoc).
        AppState e = new AppState();
        List<Integer> avisos = new ArrayList<>();
        e.registrarDestino(d("ladder"));
        e.registrarDestino(d("civstats"));
        e.agregarOyente(() -> avisos.add(1));
        e.prepararAtras();
        e.dejarDeNavegar();
        assertEquals(0, avisos.size());
    }
}
