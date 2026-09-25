package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Forma;

import java.time.Duration;
import java.util.function.LongFunction;

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
     * de hace 7 días; partidas = diferencia de partidas jugadas. eloActual/partidasActual son lecturas PEREZOSAS de lo
     * que la app ya sabe del jugador (eloWatch/gamesWatch, escritos en el EDT): solo se invocan si hace falta, en los
     * mismos puntos que la 1.1 (eloActual, solo si hay snapshot de ayer; partidasActual, solo si eloActual > 0), para
     * no leer ese estado compartido más veces de las necesarias. Devuelve {forma24, forma7d} (forma7d null si falta el
     * snapshot de hace 7 días); null entero si falta el snapshot de ayer o el ELO actual. Sin red.
     */
    Forma[] porResta(long pid, LongFunction<Integer> eloActual, LongFunction<Integer> partidasActual);

    /**
     * Una llamada a /profiles: la serie de rating 1v1 con fechas → 24 h y 7 d a la vez. Devuelve {forma24, forma7d};
     * null si el perfil no trae serie de rating 1v1. Va a la red.
     */
    Forma[] porSerie(long pid) throws Exception;

    /**
     * ¿Ya pasaron los 10 min (CADUCIDAD) desde ultimaConsultaTs (0 si nunca se consultó)? ahoraMs: la hora de una sola
     * lectura del reloj para todo el lote (como cargarForma en la 1.1: una hora para toda la lista, no una por jugador).
     */
    boolean pendiente(long ultimaConsultaTs, long ahoraMs);
}
