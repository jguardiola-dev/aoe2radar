package dev.tirador.aoe2radar.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Properties;

import static dev.tirador.aoe2radar.util.Config.CONFIG_FILE;

/** Alias y notas que el usuario pone a los jugadores (en la config). */
public final class Anotaciones {
    private Anotaciones() {}

    // ----- Alias: el nombre que TÚ decides para una cuenta ---------------------
    public static final Map<Long, String> ALIASES = new java.util.concurrent.ConcurrentHashMap<>();

    public static void cargarAliases() {
        try (var in = Files.newInputStream(CONFIG_FILE)) {
            var p = new Properties();
            p.load(in);
            for (String k : p.stringPropertyNames())
                if (k.startsWith("alias_")) {
                    String v = p.getProperty(k, "").trim();
                    try { long id = Long.parseLong(k.substring(6)); if (!v.isEmpty()) ALIASES.put(id, v); }
                    catch (NumberFormatException ignored) { }
                }
        } catch (IOException ignored) { }
    }

    public static final Map<Long, String> NOTAS = new java.util.concurrent.ConcurrentHashMap<>();   // id → nota propia

    public static void cargarNotas() {
        try (var in = Files.newInputStream(CONFIG_FILE)) {
            var p = new Properties();
            p.load(in);
            for (String k : p.stringPropertyNames())
                if (k.startsWith("nota_")) {
                    String v = p.getProperty(k, "").trim();
                    try { long id = Long.parseLong(k.substring(5)); if (!v.isEmpty()) NOTAS.put(id, v); }
                    catch (NumberFormatException ignored) { }
                }
        } catch (IOException ignored) { }
    }

    public static String notaDe(long id) { return NOTAS.get(id); }

    public static String nombreVisible(long id, String original) {
        String a = ALIASES.get(id);
        return a != null && !a.isBlank() ? a : original;
    }
}
