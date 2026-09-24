package dev.tirador.aoe2radar.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;

public final class Config {
    private Config() {}

    public static final Path CONFIG_FILE = Path.of("config.properties");

    public static String leerConfig(String clave, String porDefecto) {
        try (var in = Files.newInputStream(CONFIG_FILE)) {
            var p = new Properties();
            p.load(in);
            String v = p.getProperty(clave);
            return v != null ? v : porDefecto;
        } catch (IOException e) {
            return porDefecto;
        }
    }

    public static void guardarConfig(String clave, String valor) {
        try {
            var p = new Properties();
            if (Files.exists(CONFIG_FILE))
                try (var in = Files.newInputStream(CONFIG_FILE)) { p.load(in); }
            p.setProperty(clave, valor);
            try (var out = Files.newOutputStream(CONFIG_FILE)) { p.store(out, NOMBRE); }
        } catch (IOException ignored) {}
    }
}
