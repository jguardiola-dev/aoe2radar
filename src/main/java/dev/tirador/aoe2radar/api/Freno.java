package dev.tirador.aoe2radar.api;

import java.util.Map;

/** Reglas de cortesía con la API del companion: el Throttle único de la app y el mando a distancia (control.json). */
public final class Freno {
    private Freno() {}

    /** El único Throttle de la app: cubo de fichas (ráfaga de 5, luego 1 por segundo) y cortacircuitos ante 429. */
    public static final Throttle THROTTLE = new ThrottleCubo(ThrottleCubo.Reloj.SISTEMA);

    // ----- Buen vecino: mando a distancia (control.json en sfr-data) -----
    public static final Map<String, Object> CONTROL = new java.util.concurrent.ConcurrentHashMap<>();   // control.json: multiplicadores e interruptores
    public static final String CONTROL_URL = "https://raw.githubusercontent.com/jguardiola-dev/sfr-data/main/control.json";
    public static double ctrlMult(String clave) { Object v = CONTROL.get(clave); return v instanceof Number n ? Math.max(0.5, Math.min(20, n.doubleValue())) : 1.0; }
    public static boolean ctrlOn(String clave) { Object v = CONTROL.get(clave); return !(v instanceof Boolean b) || b; }

    /** Antes de cada llamada al companion (fachada para el código de la 1.1: delega en THROTTLE). */
    public static void freno() throws InterruptedException { THROTTLE.adquirir(); }
}
