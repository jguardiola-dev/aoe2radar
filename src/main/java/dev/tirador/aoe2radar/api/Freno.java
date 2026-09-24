package dev.tirador.aoe2radar.api;

import java.util.Map;

/** Reglas de cortesía con la API del companion: cubo de fichas, estado del cortacircuitos ante 429 y control.json. */
public final class Freno {
    private Freno() {}

    public static final Object FRENO = new Object();
    public static long frenoUltimaMs;
    /** Freno de cortesía: nunca más de 3 llamadas por segundo a la API del companion, pase lo que pase. */
    // ----- Buen vecino: cortacircuitos ante 429 y mando a distancia (control.json en sfr-data) -----
    public static volatile long PAUSA_HASTA;          // hasta cuándo están pausadas TODAS las llamadas tras un 429
    public static volatile int PAUSAS_SEGUIDAS;       // 429 encadenados: la pausa se dobla (60 s → 120 → 240 → 300 máx)
    public static volatile long ULTIMO_EXITO_MS;
    public static final Map<String, Object> CONTROL = new java.util.concurrent.ConcurrentHashMap<>();   // control.json: multiplicadores e interruptores
    public static final String CONTROL_URL = "https://raw.githubusercontent.com/jguardiola-dev/sfr-data/main/control.json";
    public static double ctrlMult(String clave) { Object v = CONTROL.get(clave); return v instanceof Number n ? Math.max(0.5, Math.min(20, n.doubleValue())) : 1.0; }
    public static boolean ctrlOn(String clave) { Object v = CONTROL.get(clave); return !(v instanceof Boolean b) || b; }
    public static double frenoCreditos = 5;   // cubo de fichas: hasta 5 llamadas seguidas sin esperar, después 1 por segundo (se recarga a 1/s)
    public static void freno() throws InterruptedException {
        long pausa = PAUSA_HASTA - System.currentTimeMillis();
        if (pausa > 0) Thread.sleep(Math.min(pausa, 300_000L));   // cortacircuitos: nadie llama hasta que pase la pausa
        synchronized (FRENO) {
            long ahora = System.currentTimeMillis();
            frenoCreditos = Math.min(5, frenoCreditos + (ahora - frenoUltimaMs) / 1000.0);
            if (frenoCreditos >= 1) frenoCreditos -= 1;
            else { long espera = (long) ((1 - frenoCreditos) * 1000); Thread.sleep(espera); frenoCreditos = 0; }
            frenoUltimaMs = System.currentTimeMillis();
        }
    }
}
