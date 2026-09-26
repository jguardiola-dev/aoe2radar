package dev.tirador.aoe2radar.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;

/**
 * config.properties, leído y guardado por clave. Dos reglas de la fila 11 de DEUDA: {@code synchronized} en los
 * dos métodos (dos hilos guardando a la vez no se pisan: uno espera al otro y lee lo que el otro acaba de escribir)
 * y escritura atómica (a un ".tmp" y luego {@code Files.move} con ATOMIC_MOVE): si la app se cierra a mitad de un
 * guardado, el archivo real nunca queda a medio escribir, como mucho se pierde el último cambio.
 */
public final class Config {
    private Config() {}

    public static final Path CONFIG_FILE = Path.of("config.properties");

    public static synchronized String leerConfig(String clave, String porDefecto) {
        try (var in = Files.newInputStream(CONFIG_FILE)) {
            var p = new Properties();
            p.load(in);
            String v = p.getProperty(clave);
            return v != null ? v : porDefecto;
        } catch (IOException e) {
            return porDefecto;
        }
    }

    public static synchronized void guardarConfig(String clave, String valor) {
        try {
            var p = new Properties();
            if (Files.exists(CONFIG_FILE))
                try (var in = Files.newInputStream(CONFIG_FILE)) { p.load(in); }
            p.setProperty(clave, valor);
            Path tmp = CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".tmp");
            try (var out = Files.newOutputStream(tmp)) { p.store(out, NOMBRE); }
            Files.move(tmp, CONFIG_FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {}
    }
}
