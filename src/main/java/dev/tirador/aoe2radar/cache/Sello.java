package dev.tirador.aoe2radar.cache;

import java.time.Duration;

/**
 * «Cuándo cargué esto por última vez», para datos que se guardan en campos propios (el ELO de ayer, la muestra, el
 * ladder…): fresco() dice si aún vale, marcar() apunta que se acaba de cargar. Sin marcar nunca, no es fresco.
 * Se crea con CacheService.sello(…). Seguro entre hilos (volatile): cada lectura ve la última marca.
 */
public final class Sello {
    private final CacheService cache;
    private final Duration caducidad;
    private volatile long marcaMs = Long.MIN_VALUE;

    Sello(CacheService cache, Duration caducidad) { this.cache = cache; this.caducidad = caducidad; }

    public boolean fresco() { return marcaMs != Long.MIN_VALUE && cache.fresco(marcaMs, caducidad); }

    public void marcar() { marcaMs = cache.reloj().ahoraMs(); }

    /** La marca actual (Long.MIN_VALUE si nunca se marcó): para tests que necesiten respaldarla y restaurarla. */
    public long marcaMs() { return marcaMs; }

    /**
     * Marca con una hora dada: la del dato, no la de ahora. Para lo que se guarda también en disco: si la memoria se
     * sellara al leer la copia, las dos caducidades se sumarían (una copia de 5 h valdría otras 6 h en memoria).
     */
    public void marcar(long marcaMs) { this.marcaMs = marcaMs; }
}
