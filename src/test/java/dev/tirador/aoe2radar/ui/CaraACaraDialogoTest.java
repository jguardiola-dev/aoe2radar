package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fila 80: pedirAnioRival tenía que buscar «cada uno por su lado» del RIVAL, no del perfil abierto (bug
 * heredado de la 1.1: el clic en fijar() pasaba nombreAbierto). CaraACaraDialogo está pegado a PerfilView (ver
 * su javadoc de clase) y necesita una ventana entera para instanciarse, así que este test cubre la parte que sí
 * se puede probar sin pantalla: nombreParaSfr(), el nombre exacto que ahora se pasa a
 * CaraACaraPresenter.pedirAnioRival. El cableado de la llamada (que usa rivalNombre y no nombreAbierto) se
 * comprobó leyendo el diff línea a línea: todos los sitios que llaman a fijar(pid, nombre) en este archivo pasan
 * un nombre no nulo (grep sobre "fijar(" en CaraACaraDialogo.java), así que en la práctica siempre se toma la
 * primera rama.
 */
class CaraACaraDialogoTest {

    @Test void nombreParaSfrUsaElNombreDelRivalCuandoLoHay() {
        assertEquals("Rival", CaraACaraDialogo.nombreParaSfr("Rival", 42L));
    }

    @Test void nombreParaSfrCaeAHashPidSiNoHayNombre() {
        assertEquals("#42", CaraACaraDialogo.nombreParaSfr(null, 42L));
        assertEquals("#42", CaraACaraDialogo.nombreParaSfr("   ", 42L));
    }
}
