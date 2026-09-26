package dev.tirador.aoe2radar.cache;

import java.util.Map;

import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;

/** Canal de Twitch vinculado a cada jugador, aprendido de la API y guardado en la config. */
public final class Canales {
    private Canales() {}

    public static final Map<Long, String> CANAL_DE = new java.util.concurrent.ConcurrentHashMap<>();   // pid -> canal Twitch vinculado (persistido)

    public static void aprenderCanal(long pid, Object canal) {
        if (pid <= 0 || canal == null) return;
        String c = String.valueOf(canal).trim();
        if (c.isBlank() || "null".equals(c)) return;
        if (c.equals(CANAL_DE.put(pid, c))) return;   // ya lo sabíamos
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<Long, String> e : CANAL_DE.entrySet()) {
            if (n++ >= 400) break;   // cota del config
            sb.append(sb.isEmpty() ? "" : ",").append(e.getKey()).append(":").append(e.getValue());
        }
        guardarConfig("twitch_canales", sb.toString());
    }

    public static void cargarCanales() {
        for (String par : leerConfig("twitch_canales", "").split(",")) {
            int i = par.indexOf(':');
            if (i <= 0) continue;
            try { CANAL_DE.put(Long.parseLong(par.substring(0, i).trim()), par.substring(i + 1).trim()); }
            catch (Exception ignored) { }
        }
    }
}
