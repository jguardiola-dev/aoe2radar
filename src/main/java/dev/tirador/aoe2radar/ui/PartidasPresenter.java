package dev.tirador.aoe2radar.ui;

import java.time.Duration;
import java.time.Instant;

/**
 * Las decisiones de Partidas que no tocan Swing. fetchMatches, download, buscarAleatorias y buscarGte siguen
 * siendo {@code SwingWorker} dentro de {@link PartidasView} (la tanda 3 lo permite explícitamente para no
 * reescribir su publish/process/done, que ya funcionan hoy): lo que este presentador aísla es {@link #vigente}
 * — la comprobación de «¿sigo siendo la operación vigente, o ya me superó otra?» que cada {@code done()} hace
 * antes de pintar nada — y los tres filtros de la tabla (modo, mapa, periodo), que antes eran condiciones
 * sueltas dentro de {@code applyFilters}. Al ser métodos puros (mismas entradas, misma salida, sin Swing), se
 * prueban con JUnit normal, sin arrancar ninguna ventana.
 */
public final class PartidasPresenter {

    private PartidasPresenter() { }

    /** true si la operación que acaba de terminar (miSerial) sigue siendo la vigente: nadie la ha superado
     *  mientras corría (equivalente a {@code miSerial == opSerial} en el código de la 1.1). */
    public static boolean vigente(long miSerial, long serialActual) {
        return miSerial == serialActual;
    }

    /** El ojo de una fila: abierto si el modo consulta global está activo (y la fila no es de Guess the ELO,
     *  que nunca se destapa así) o si esta fila se reveló a mano. Mismo cálculo que {@code revelada(Match)}. */
    public static boolean revelada(boolean mostrarResultados, int gte, boolean enReveladas) {
        return (mostrarResultados && gte == 0) || enReveladas;
    }

    /** Filtro de modo: sin filtro, o coincide exactamente con el modo de la partida. */
    public static boolean pasaFiltroModo(String modoElegido, String todosLosModos, String modoPartida) {
        return modoElegido == null || todosLosModos.equals(modoElegido) || modoElegido.equals(modoPartida);
    }

    /** Filtro de mapa: índice 0 del combo = todos; si no, coincide exactamente con el mapa de la partida. */
    public static boolean pasaFiltroMapa(int indiceElegido, Object mapaElegido, String mapaPartida) {
        return indiceElegido <= 0 || String.valueOf(mapaElegido).equals(mapaPartida);
    }

    /** Filtro de periodo: índice 0 = todo; si no, la partida debe haber EMPEZADO dentro de los últimos N días
     *  (0/7/30/90/365, en el orden del combo). Sin fecha de inicio, la partida queda fuera. */
    public static boolean pasaFiltroPeriodo(int indicePeriodo, Instant inicioPartida, Instant ahora) {
        if (indicePeriodo <= 0) return true;
        int[] dias = { 0, 7, 30, 90, 365 };
        int d = dias[indicePeriodo];
        return inicioPartida != null && !inicioPartida.isBefore(ahora.minus(Duration.ofDays(d)));
    }
}
