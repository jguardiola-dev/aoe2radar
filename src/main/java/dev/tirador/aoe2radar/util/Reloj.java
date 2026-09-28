package dev.tirador.aoe2radar.util;

/**
 * El tiempo, inyectable: en producción el del sistema; en los tests, uno falso que avanza cuando el test quiere.
 * Lo usan el freno (api.ThrottleCubo) y las cachés (cache.CacheService).
 */
public interface Reloj {
    long ahoraMs();
    void dormir(long ms) throws InterruptedException;

    Reloj SISTEMA = new Reloj() {
        @Override public long ahoraMs() { return System.currentTimeMillis(); }
        @Override public void dormir(long ms) throws InterruptedException { Thread.sleep(ms); }
    };

    /** Solo para medir esperas (no es una fecha: su cero es arbitrario): System.nanoTime en ms, que no salta si
     *  Windows cambia la hora. Lo usa la cola de confirmaciones del socket (service.EnlaceVivo). */
    Reloj MONOTONO = new Reloj() {
        @Override public long ahoraMs() { return System.nanoTime() / 1_000_000L; }
        @Override public void dormir(long ms) throws InterruptedException { Thread.sleep(ms); }
    };
}
