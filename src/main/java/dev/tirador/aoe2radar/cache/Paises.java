package dev.tirador.aoe2radar.cache;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import static dev.tirador.aoe2radar.cache.Directorios.LADDER_DIR;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/** País de cada jugador, aprendido de la API y guardado en sfrdata/paises.txt. */
public final class Paises {
    private Paises() {}

    public static final Map<Long, String> PAIS_DE = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Path PAISES_FILE = LADDER_DIR.resolve("paises.txt");
    public static volatile boolean paisesSucios;

    public static void aprenderPais(long pid, Object pais) {
        if (pid <= 0 || pais == null) return;
        String c = String.valueOf(pais).trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty() || "null".equals(c) || c.length() > 6) return;
        if (!c.equals(PAIS_DE.put(pid, c))) paisesSucios = true;
    }
    public static String paisDe(long pid) { return PAIS_DE.get(pid); }

    public static void cargarPaises() {
        try {
            if (!Files.exists(PAISES_FILE)) return;
            for (String linea : Files.readAllLines(PAISES_FILE, StandardCharsets.UTF_8)) {
                int i = linea.indexOf('=');
                if (i <= 0) continue;
                try { PAIS_DE.put(Long.parseLong(linea.substring(0, i).trim()), linea.substring(i + 1).trim()); } catch (NumberFormatException ignored) { }
            }
        } catch (Exception ex) { log("paises: " + causa(ex)); }
    }
    public static void guardarPaises() {
        if (!paisesSucios) return;
        paisesSucios = false;
        try {
            Files.createDirectories(LADDER_DIR);
            StringBuilder b = new StringBuilder();
            for (Map.Entry<Long, String> en : PAIS_DE.entrySet()) b.append(en.getKey()).append('=').append(en.getValue()).append('\n');
            Files.writeString(PAISES_FILE, b.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) { log("paises: no se pudo guardar: " + causa(ex)); }
    }
}
