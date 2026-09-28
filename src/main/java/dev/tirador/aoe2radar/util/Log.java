package dev.tirador.aoe2radar.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Log {
    private Log() {}

    public static final Path LOG_FILE = Sistema.enCarpetaBase("descargas.log");

    /** Tope de descargas.log: al arrancar, si lo pasa, se aparta a descargas.log.1 (sustituye al anterior) y se empieza otro. */
    static final long LOG_MAX_BYTES = 5L * 1024 * 1024;

    static { rotar(LOG_FILE, LOG_MAX_BYTES); }   // una vez por arranque, antes de la primera línea

    /**
     * Si log pasa de maxBytes, lo renombra a «log.1» (sustituyendo el que hubiera). Nunca lanza: se llama desde el
     * inicializador estático y una excepción ahí sería un ExceptionInInitializerError que tumbaría la app; si no se
     * puede rotar (archivo bloqueado…), se sigue escribiendo en el mismo, como antes.
     */
    static void rotar(Path log, long maxBytes) {
        try {
            if (Files.size(log) <= maxBytes) return;
            Files.move(log, log.resolveSibling(log.getFileName() + ".1"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) { }   // no existe, bloqueado o sin permiso: se sigue sin rotar
    }

    public static final DateTimeFormatter LOG_F =
            DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Lo que pasó al preparar la carpeta de datos (respaldo a la carpeta de la app si %APPDATA% falla…): Sistema no puede llamar a Log mientras
     *  calcula LOG_FILE, así que lo deja apuntado y se escribe aquí, ya con LOG_FILE y LOG_F listos. */
    static { String aviso = Sistema.avisoCarpetaDatos(); if (aviso != null) log(aviso); }

    /** Escribe en consola y en descargas.log (junto a players.txt). */
    public static synchronized void log(String linea) {
        String l = LOG_F.format(Instant.now()) + "  " + linea;
        System.out.println(l);
        try {
            Files.writeString(LOG_FILE, l + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    public static String causa(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }
}
