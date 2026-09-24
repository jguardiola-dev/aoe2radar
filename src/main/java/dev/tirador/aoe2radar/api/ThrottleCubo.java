package dev.tirador.aoe2radar.api;

/**
 * Throttle de cubo de fichas con cortacircuitos. Misma aritmética que el freno de la 1.1 (antes estado público en
 * Freno y en la app); ahora el estado es privado y el reloj entra por el constructor para poder probarlo sin esperar.
 */
public final class ThrottleCubo implements Throttle {

    /** El tiempo, inyectable: en producción el del sistema; en los tests, uno falso que avanza cuando el test quiere. */
    public interface Reloj {
        long ahoraMs();
        void dormir(long ms) throws InterruptedException;

        Reloj SISTEMA = new Reloj() {
            @Override public long ahoraMs() { return System.currentTimeMillis(); }
            @Override public void dormir(long ms) throws InterruptedException { Thread.sleep(ms); }
        };
    }

    static final int FICHAS = 5;                    // ráfaga máxima sin esperar
    static final long PAUSA_BASE_MS = 60_000L;      // primer 429: 60 s
    static final long PAUSA_MAX_MS = 300_000L;      // tope de la pausa (y de lo que se duerme de una vez)
    static final int ESCALONES = 4;                 // 60 → 120 → 240 → 300 (tope)
    static final long OLVIDO_MS = 10 * 60_000L;     // más de 10 min desde el último éxito contado: se olvida la escalada (ver DEUDA)

    private final Reloj reloj;
    private final Object cubo = new Object();
    private final Object escalada = new Object();   // candado privado: nadie de fuera puede compartirlo (THROTTLE es público)
    private double fichas = FICHAS;                  // bajo «cubo»
    private long ultimaMs;                          // bajo «cubo»
    private volatile long pausaHastaMs;             // se lee sin candado al entrar en adquirir()
    private int pausasSeguidas;                     // bajo «escalada»
    private long ultimoExitoMs;                     // bajo «escalada»

    public ThrottleCubo(Reloj reloj) { this.reloj = reloj; }

    static final long TRAMO_MS = 250;              // la espera se hace en tramos para poder atender a Detener

    @Override public void adquirir(java.util.function.BooleanSupplier cancelar) throws InterruptedException {
        long pausa = pausaHastaMs - reloj.ahoraMs();
        if (pausa > 0) dormir(Math.min(pausa, PAUSA_MAX_MS), cancelar);   // cortacircuitos: nadie llama hasta que pase la pausa
        synchronized (cubo) {
            long ahora = reloj.ahoraMs();
            fichas = Math.min(FICHAS, fichas + (ahora - ultimaMs) / 1000.0);
            ultimaMs = ahora;   // el tiempo ya está contado: si Detener corta la espera, el siguiente no lo cuenta otra vez
            if (fichas >= 1) fichas -= 1;
            else { long espera = (long) ((1 - fichas) * 1000); dormir(espera, cancelar); fichas = 0; }   // si se cancela, no consume ficha
            ultimaMs = reloj.ahoraMs();
        }
    }

    /**
     * Duerme ms en tramos de hasta TRAMO_MS; entre tramos, si cancelar da true, sale con InterruptedException("detenido").
     * Se duerme hasta una hora de fin (no sumando tramos): el retraso de cada sleep de Windows no se acumula.
     */
    private void dormir(long ms, java.util.function.BooleanSupplier cancelar) throws InterruptedException {
        long fin = reloj.ahoraMs() + ms;
        for (long queda = ms; queda > 0; queda = fin - reloj.ahoraMs()) {
            if (cancelar.getAsBoolean()) throw new InterruptedException("detenido");
            reloj.dormir(Math.min(TRAMO_MS, queda));
        }
    }

    /**
     * Un escalón por EPISODIO, no por respuesta: si ya hay una pausa vigente, un 429 más son respuestas de la misma
     * ráfaga (salieron antes de la pausa) y no escalan ni acortan la pausa. Si el 429 llega después de que acabe,
     * sí escala. Para el servidor es igual (durante la pausa no le llega nada); para el usuario, 60 s y no 300 s por
     * una sola queja. Decisión de Jorge (2026-09-24). Bajo candado: la escalada es atómica.
     */
    @Override public long registrar429() {
        synchronized (escalada) {
            long vigente = pausaHastaMs - reloj.ahoraMs();
            if (vigente > 0) return Math.min(PAUSA_MAX_MS, vigente);   // lo que queda (con tope: si el reloj retrocede no se duerme más de 300 s)
            pausasSeguidas = Math.min(pausasSeguidas + 1, ESCALONES);
            long pausa = Math.min(PAUSA_MAX_MS, PAUSA_BASE_MS * (1L << (pausasSeguidas - 1)));
            pausaHastaMs = reloj.ahoraMs() + pausa;
            return pausa;
        }
    }

    /** Bajo el mismo candado que registrar429: el olvido y un 429 simultáneo ya no se cruzan. */
    @Override public void registrarExito() {
        synchronized (escalada) {
            if (reloj.ahoraMs() - ultimoExitoMs > OLVIDO_MS) pausasSeguidas = 0;
            ultimoExitoMs = reloj.ahoraMs();
        }
    }
}
