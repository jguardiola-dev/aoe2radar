package dev.tirador.aoe2radar.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.stream.Stream;

import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Escritura atómica de un archivo compartido con quien lo lee (config.properties, las cachés de sfr-data): se
 * escribe entero a un temporal y se mueve con {@code ATOMIC_MOVE} sobre destino, así un corte a mitad de guardado
 * nunca deja el archivo real a medio escribir (o sigue con la copia de antes, o ya tiene la nueva entera).
 * <p>En Windows el {@code move} puede fallar con {@code AccessDeniedException} si alguien tiene destino abierto
 * para lectura (o el antivirus lo está mirando en ese instante): en ese caso, en vez de perder el guardado (como
 * pasaba antes de este arreglo), se escribe directamente sobre destino, igual que en la 1.1 —sin la atomicidad,
 * pero sin perder el dato—. El temporal se borra siempre, se haya movido o no.
 */
public final class Archivos {
    private Archivos() {}

    /** Como Consumer, pero admite IOException (Files.write, Properties.store…). */
    @FunctionalInterface
    public interface IOConsumer<T> { void accept(T t) throws IOException; }

    public static void escribirAtomico(Path destino, IOConsumer<OutputStream> escritor) throws IOException {
        // config.properties es un nombre suelto (sin carpeta): destino.getParent() daría null. Con toAbsolutePath()
        // siempre hay carpeta (la de trabajo), tanto para ese caso como para las rutas con carpeta de sfr-data.
        Path dirPadre = destino.toAbsolutePath().getParent();
        Files.createDirectories(dirPadre);
        Path tmp = Files.createTempFile(dirPadre, destino.getFileName().toString(), ".tmp");
        try {
            try (var out = Files.newOutputStream(tmp)) { escritor.accept(out); }
            try {
                Files.move(tmp, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                log("archivos: move atómico de " + destino + " falló (" + causa(ex) + "): se escribe directo, como en la 1.1");
                try (var out = Files.newOutputStream(destino)) { escritor.accept(out); }
            }
        } finally {
            // si esto lanzara sin capturar, taparía el éxito o la excepción de arriba (el finally manda): un .tmp
            // huérfano que no se pudo borrar es basura tolerable, no motivo para perder ese resultado (el que se
            // acumulen si la app se corta a media escritura queda anotado en DEUDA, no se arregla aquí).
            try { Files.deleteIfExists(tmp); } catch (IOException ex) { log("archivos: no se pudo borrar " + tmp + ": " + causa(ex)); }
        }
    }

    /** Como escribirAtomico(Path, IOConsumer) con los bytes ya en memoria. */
    public static void escribirAtomico(Path destino, byte[] datos) throws IOException {
        escribirAtomico(destino, out -> out.write(datos));
    }

    /**
     * Limpieza de los ".tmp" huérfanos que puede dejar escribirAtomico (fila 140 de DEUDA): si la app se corta a
     * media escritura, ese temporal (nombre único, {@code Files.createTempFile}) se queda en disco para siempre,
     * porque nadie vuelve a mirarlo. Se llama UNA vez al arrancar, en un hilo de fondo, no en cada guardado.
     * <p>Solo borra, en "dir" (sin bajar a subcarpetas), los archivos cuyo nombre empiece por "prefijo" y acabe
     * en ".tmp", y solo si su última modificación es más antigua que "antiguedad": un temporal recién creado por
     * una escritura EN CURSO (otro hilo, ahora mismo) no se toca. Con prefijo "" vale cualquier nombre, para una
     * carpeta donde solo escribirAtomico escribe archivos sueltos (p. ej. sfrdata/perfiles_shards).
     */
    public static void limpiarTemporales(Path dir, String prefijo, Duration antiguedad) {
        if (dir == null || !Files.isDirectory(dir)) return;
        long limite = System.currentTimeMillis() - antiguedad.toMillis();
        try (Stream<Path> listado = Files.list(dir)) {
            listado.filter(p -> {
                String nombre = p.getFileName().toString();
                return nombre.startsWith(prefijo) && nombre.endsWith(".tmp");
            }).forEach(p -> {
                try {
                    if (Files.getLastModifiedTime(p).toMillis() < limite) Files.deleteIfExists(p);
                } catch (IOException ex) {
                    log("archivos: limpiar temporales: no se pudo revisar/borrar " + p + ": " + causa(ex));
                }
            });
        } catch (IOException | UncheckedIOException ex) {
            // UncheckedIOException: no solo Files.list() puede fallar al abrir "dir" (esa parte ya es IOException),
            // el propio recorrido del Stream (el iterador del directorio, dentro de forEach) también puede toparse
            // con un error de E/S a media lectura (p. ej. si algo borra "dir" mientras se recorre) y lo envuelve así,
            // sin que el try/catch de cada archivo (arriba) llegue a verlo (revisor, fila 145: limpieza de arranque
            // en un hilo de fondo, un fallo aquí no debe tirar el hilo ni el arranque).
            log("archivos: limpiar temporales: no se pudo listar " + dir + ": " + causa(ex instanceof UncheckedIOException u ? u.getCause() : ex));
        }
    }
}
