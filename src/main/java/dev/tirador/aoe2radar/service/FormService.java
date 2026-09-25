package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Forma;

import java.time.Duration;

/**
 * La forma reciente de un jugador (±ELO 1v1 ranked en una ventana de 24 h o 7 días). «Nocturno primero»: porResta no
 * va a la red (resta el ELO nocturno del ELO que la app ya conoce); porSerie es la vía exacta, con una llamada, para
 * quien no está en el snapshot de sfr-data. La app decide con pendiente() si ya toca volver a consultar (10 min).
 * <p>Hilos: porSerie puede ir a la red y no se llama en el EDT (si se hace, queda aviso en el log, ver util.Hilos).
 * porResta y pendiente son sin red: se pueden llamar desde cualquier hilo.
 */
public interface FormService {

    /** Cuánto vale la forma ya consultada antes de volver a pedirla (ver cargarForma en la app). */
    Duration CADUCIDAD = Duration.ofMinutes(10);

    /**
     * Forma por resta con el ELO nocturno (EloNocturno.ayer / .hace7): ELO actual menos el de anoche (24 h) y menos el
     * de hace 7 días; partidas = diferencia de partidas jugadas. eloActual/partidasActual es lo que la app ya sabe del
     * jugador (su ELO vigilado); ninguno de los dos sale de aquí. Devuelve {forma24, forma7d} (forma7d null si falta
     * el snapshot de hace 7 días); null entero si falta el snapshot de ayer o el ELO actual. Sin red.
     */
    Forma[] porResta(long pid, Integer eloActual, Integer partidasActual);

    /**
     * Una llamada a /profiles: la serie de rating 1v1 con fechas → 24 h y 7 d a la vez. Devuelve {forma24, forma7d};
     * null si el perfil no trae serie de rating 1v1. Va a la red.
     */
    Forma[] porSerie(long pid) throws Exception;

    /** ¿Ya pasaron los 10 min (CADUCIDAD) desde ultimaConsultaTs (0 si nunca se consultó)? Con el reloj inyectado. */
    boolean pendiente(long ultimaConsultaTs);
}
