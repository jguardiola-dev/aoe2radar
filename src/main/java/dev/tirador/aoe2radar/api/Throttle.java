package dev.tirador.aoe2radar.api;

/**
 * Freno de cortesía con la API del companion (docs/ARQUITECTURA.md, «Reglas de desarrollo»): cubo de fichas (ráfaga de 5, luego 1 por segundo) y
 * cortacircuitos ante 429 (pausa global que se dobla si se repite). Toda llamada al companion pasa por aquí.
 */
public interface Throttle {
    /** Espera lo que haga falta (pausa por 429 pendiente y/o falta de fichas) y consume una ficha. */
    default void adquirir() throws InterruptedException { adquirir(() -> false); }

    /**
     * Como adquirir(), pero la espera se puede cortar: si cancelar pasa a true mientras espera (botón Detener), sale
     * con InterruptedException("detenido") sin consumir ficha.
     */
    void adquirir(java.util.function.BooleanSupplier cancelar) throws InterruptedException;

    /**
     * Un 429: pausa global para todas las llamadas, más larga si se repite tras acabar la anterior (60 s → 120 → 240 →
     * 300 máx). Un 429 durante una pausa vigente no escala (misma ráfaga). Tras 10 min de calma sin 429 desde que
     * acabó la última pausa, se olvida la escalada. Devuelve la pausa que queda, en ms.
     */
    long registrar429();

    /**
     * Lo que deja un 429: la pausa que queda (ms) y si este 429 la ABRIÓ (nueva = episodio nuevo) o cayó en una ya
     * vigente (misma ráfaga). Con eso ApiClient avisa una vez por pausa y no una por respuesta.
     */
    record Pausa429(long ms, boolean nueva) {}

    /**
     * Como registrar429() (misma escalada, misma pausa), diciendo además si la pausa es nueva. Por defecto toda pausa
     * cuenta como nueva: un Throttle que no lo sabe (los dobles de prueba) avisa en cada 429, como antes.
     * <p>Ojo: quien implemente registrar429() delegando en este método (como ThrottleCubo) debe sobrescribir también
     * este; si no, los dos se llaman entre sí sin fin.
     */
    default Pausa429 registrarEpisodio429() { return new Pausa429(registrar429(), true); }

    /**
     * ¿Está abierto el cortacircuitos? Los ms que quedan de la pausa por 429 (0 si no hay pausa). Sin esperar ni
     * consumir ficha: para quien quiere decidir ANTES de llamar (la fuente de respaldo, ConRespaldo, pasa a World's Edge
     * en vez de dormirse la pausa del companion). Por defecto 0: un Throttle que no lo sabe nunca está abierto.
     */
    default long pausaRestanteMs() { return 0; }
}
