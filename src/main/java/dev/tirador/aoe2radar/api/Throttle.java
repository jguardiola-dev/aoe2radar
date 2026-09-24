package dev.tirador.aoe2radar.api;

/**
 * Freno de cortesía con la API del companion (CLAUDE.md): cubo de fichas (ráfaga de 5, luego 1 por segundo) y
 * cortacircuitos ante 429 (pausa global que se dobla si se repite). Toda llamada al companion pasa por aquí.
 */
public interface Throttle {
    /** Espera lo que haga falta (pausa por 429 pendiente y/o falta de fichas) y consume una ficha. */
    void adquirir() throws InterruptedException;

    /** Un 429: pausa global para todas las llamadas, más larga si se repite (60 s → 120 → 240 → 300 máx). Devuelve la pausa en ms. */
    long registrar429();

    /** Una respuesta buena: si hace más de 10 min del último éxito, se olvida la escalada de pausas. */
    void registrarExito();
}
