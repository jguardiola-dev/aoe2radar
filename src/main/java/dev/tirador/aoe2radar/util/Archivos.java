package dev.tirador.aoe2radar.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
            Files.deleteIfExists(tmp);
        }
    }

    /** Como escribirAtomico(Path, IOConsumer) con los bytes ya en memoria. */
    public static void escribirAtomico(Path destino, byte[] datos) throws IOException {
        escribirAtomico(destino, out -> out.write(datos));
    }
}
