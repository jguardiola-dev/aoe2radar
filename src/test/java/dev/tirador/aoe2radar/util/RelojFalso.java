package dev.tirador.aoe2radar.util;

/** Reloj de prueba: dormir no espera, solo apunta cuánto se durmió y adelanta la hora. */
public final class RelojFalso implements Reloj {
    public long ahora = 1_000_000_000L;   // no empezar en 0: el reloj real nunca ve la hora 0
    public long dormido;
    @Override public long ahoraMs() { return ahora; }
    @Override public void dormir(long ms) { dormido += ms; ahora += ms; }
    public void avanzar(long ms) { ahora += ms; }
}
