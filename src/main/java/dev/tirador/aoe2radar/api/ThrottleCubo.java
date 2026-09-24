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
    static final long OLVIDO_MS = 10 * 60_000L;     // diez minutos sin 429: se olvida la escalada

    private final Reloj reloj;
    private final Object cubo = new Object();
    private final Object escalada = new Object();   // candado privado: nadie de fuera puede compartirlo (THROTTLE es público)
    private double fichas = FICHAS;                  // bajo «cubo»
    private long ultimaMs;                          // bajo «cubo»
    private volatile long pausaHastaMs;             // se lee sin candado al entrar en adquirir()
    private int pausasSeguidas;                     // bajo «escalada»
    private long ultimoExitoMs;                     // bajo «escalada»

    public ThrottleCubo(Reloj reloj) { this.reloj = reloj; }

    @Override public void adquirir() throws InterruptedException {
        long pausa = pausaHastaMs - reloj.ahoraMs();
        if (pausa > 0) reloj.dormir(Math.min(pausa, PAUSA_MAX_MS));   // cortacircuitos: nadie llama hasta que pase la pausa
        synchronized (cubo) {
            long ahora = reloj.ahoraMs();
            fichas = Math.min(FICHAS, fichas + (ahora - ultimaMs) / 1000.0);
            if (fichas >= 1) fichas -= 1;
            else { long espera = (long) ((1 - fichas) * 1000); reloj.dormir(espera); fichas = 0; }
            ultimaMs = reloj.ahoraMs();
        }
    }

    /** Bajo candado: en la 1.1 la escalada no era atómica y dos 429 a la vez podían subir un escalón en vez de dos. */
    @Override public long registrar429() {
        synchronized (escalada) {
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
