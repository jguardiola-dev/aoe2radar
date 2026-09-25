package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** La regla de Jorge: el ELO guardado vale hasta que termina una partida, y el nuevo se pide pasada la espera. */
class EloSesionTest {
    static final long HORA = 1_700_000_000_000L;
    static final Duration ESPERA = Duration.ofMinutes(2);

    final RelojFalso reloj = new RelojFalso();
    { reloj.ahora = HORA; }
    final EstadoVivo estado = new EstadoVivo(reloj);
    final EloSesion elo = new EloSesion(estado, reloj, ESPERA);

    /** pid juega la partida mid y termina ahora. */
    void terminaPartida(long pid, long mid) { estado.marcarJugando(pid, mid); estado.marcarFuera(pid); }

    @Test void sinPartidasTerminadasSeRecuerdaComoEnLa11() {
        assertNull(elo.conocido(1), "nunca preguntado");
        elo.apuntar(1, 1900);
        reloj.ahora += Duration.ofHours(5).toMillis();
        assertEquals(1900, elo.conocido(1));
        assertFalse(elo.caducado(1), "sin partidas terminadas no se vuelve a pedir: ni una llamada más que antes");
    }

    @Test void siNoTieneSeGuardaCero() {
        elo.apuntar(1, null);
        assertEquals(0, elo.conocido(1));
    }

    @Test void alTerminarUnaPartidaCaducaPeroNoAntesDeLaEspera() {
        elo.apuntar(1, 1900);
        reloj.ahora += 60_000;
        terminaPartida(1, 555);                                  // fin a HORA + 1 min
        reloj.ahora += ESPERA.toMillis() - 1;
        assertFalse(elo.caducado(1), "aún no: el companion no habría recalculado; se sigue mostrando el de antes");
        assertEquals(1900, elo.conocido(1));
        reloj.ahora += 1;
        assertTrue(elo.caducado(1), "a la espera justa, a pedir el nuevo");
    }

    @Test void pedidoDespuesDeLaEsperaYaEsElNuevo() {
        elo.apuntar(1, 1900);
        terminaPartida(1, 555);
        reloj.ahora += ESPERA.toMillis();
        elo.apuntar(1, 1912);                                    // el de después de la partida
        reloj.ahora += Duration.ofHours(1).toMillis();
        assertFalse(elo.caducado(1));
        assertEquals(1912, elo.conocido(1));
    }

    @Test void pedidoDuranteLaEsperaSigueCaducandoAlAcabarla() {
        elo.apuntar(1, 1900);
        terminaPartida(1, 555);
        reloj.ahora += 30_000;
        elo.apuntar(1, 1900);                                    // alguien lo pidió enseguida: puede ser el de antes
        reloj.ahora += ESPERA.toMillis();
        assertTrue(elo.caducado(1), "pedido antes de que el companion lo recalculara: hay que volver a pedirlo");
    }

    @Test void otroJugadorNoSeVeAfectado() {
        elo.apuntar(1, 1900);
        elo.apuntar(2, 1500);
        terminaPartida(1, 555);
        reloj.ahora += ESPERA.toMillis();
        assertTrue(elo.caducado(1));
        assertFalse(elo.caducado(2));
    }

    @Test void unaPeticionQueSalioAntesDeLaEsperaNoPasaPorNuevaAunqueRespondaDespues() {
        elo.apuntar(1, 1900);
        terminaPartida(1, 555);
        long pedido = elo.ahora() + 30_000;                        // sale 30 s después del final (dentro de la espera)...
        reloj.ahora += ESPERA.toMillis() + 60_000;                 // ...y la respuesta llega tarde (freno, 429)
        elo.apuntar(1, 1900, pedido);
        assertTrue(elo.caducado(1), "cuenta la hora de la petición: ese ELO puede ser el de antes");
    }

    @Test void soloUnaPeticionEnVueloPorJugador() {
        assertTrue(elo.reservar(1));
        assertFalse(elo.reservar(1), "el menú abierto otra vez antes de la respuesta no lanza otra llamada");
        assertTrue(elo.reservar(2), "otro jugador, sí");
        elo.apuntar(1, 1900, elo.ahora());
        assertTrue(elo.reservar(1), "tras la respuesta se puede volver a pedir cuando toque");
    }
}
