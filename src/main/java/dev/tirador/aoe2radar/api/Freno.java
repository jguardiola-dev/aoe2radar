package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.util.Reloj;

import java.util.Map;

/** Reglas de cortesía con la API del companion: el Throttle único de la app y el mando a distancia (control.json). */
public final class Freno {
    private Freno() {}

    /** El único Throttle de la app: cubo de fichas (ráfaga de 5, luego 1 por segundo) y cortacircuitos ante 429. */
    public static final Throttle THROTTLE = new ThrottleCubo(Reloj.SISTEMA);

    // ----- Buen vecino: mando a distancia (control.json en sfr-data) -----
    public static final Map<String, Object> CONTROL = new java.util.concurrent.ConcurrentHashMap<>();   // control.json: multiplicadores e interruptores
    public static final String CONTROL_URL = "https://raw.githubusercontent.com/jguardiola-dev/sfr-data/main/control.json";
    public static double ctrlMult(String clave) { Object v = CONTROL.get(clave); return v instanceof Number n ? Math.max(0.5, Math.min(20, n.doubleValue())) : 1.0; }
    public static boolean ctrlOn(String clave) { Object v = CONTROL.get(clave); return !(v instanceof Boolean b) || b; }

    /**
     * ¿Esta URL es del companion y debe pasar por el freno? Cualquier host de aoe2companion.com (data., api., …).
     * En la 1.1 el criterio era «empieza por API» (data.aoe2companion.com/api) y las llamadas de Twitch
     * (api.aoe2companion.com/twitch/…) se saltaban el freno global. Quedan fuera por no usar httpText, a propósito:
     * las imágenes de cdn.aoe2companion.com (una por mapa, con caché) y el socket (conexión persistente con su propio
     * escalonado de reconexión). Si algún día pasaran por httpText, se frenarían.
     */
    public static boolean aplicaA(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(java.util.Locale.ROOT);
            return host.equals("aoe2companion.com") || host.endsWith(".aoe2companion.com");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
