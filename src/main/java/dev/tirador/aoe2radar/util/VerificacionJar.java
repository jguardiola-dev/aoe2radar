package dev.tirador.aoe2radar.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.zip.ZipFile;

/**
 * Comprobación de un jar de la app antes de ponerlo en uso (actualizador 1.4): tamaño exacto, SHA-256 (el de
 * update.json), que es un zip legible y que trae la clase de arranque ({@code app/Main.class}) y la clase principal
 * que nombra el .cfg (hoy {@code SpoilerFreeRecs}; si una versión la cambiara, el .cfg tendría que cambiar y la
 * actualización sería «completa», con instalador). Un jar que no pasa esto nunca llega a {@code app/}.
 */
public final class VerificacionJar {
    private VerificacionJar() {}

    /** La clase que el lanzador acaba ejecutando siempre (SpoilerFreeRecs.main delega en ella). */
    public static final String CLASE_ARRANQUE = "dev/tirador/aoe2radar/app/Main.class";

    /** Resultado: ok o el motivo del rechazo (para el log). */
    public record Resultado(boolean ok, String motivo) {
        static final Resultado OK = new Resultado(true, null);
        static Resultado no(String motivo) { return new Resultado(false, motivo); }
    }

    /** SHA-256 del archivo en hexadecimal en minúsculas. */
    public static String sha256(Path archivo) throws IOException {
        MessageDigest md = nuevoSha256();
        try (InputStream in = Files.newInputStream(archivo)) {
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    public static MessageDigest nuevoSha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }   // todo JDK lo trae
    }

    /** ¿Es un sha256 bien formado (64 cifras hexadecimales)? */
    public static boolean shaValido(String sha) { return sha != null && sha.matches("[0-9a-fA-F]{64}"); }

    /** Comprueba el jar entero. mainclass: la clase principal del .cfg (con puntos), o null para no mirarla. Nunca lanza. */
    public static Resultado verificar(Path jar, long tamano, String sha256, String mainclass) {
        try {
            if (!Files.isRegularFile(jar)) return Resultado.no("no existe " + jar.getFileName());
            long real = Files.size(jar);
            if (real != tamano) return Resultado.no("tamaño " + real + " en vez de " + tamano);
            if (!shaValido(sha256)) return Resultado.no("sha256 mal formado");
            String calculado = sha256(jar);
            if (!calculado.equals(sha256.toLowerCase(Locale.ROOT))) return Resultado.no("sha256 no coincide");
            return verificarContenido(jar, mainclass);
        } catch (Exception ex) {
            return Resultado.no(Log.causa(ex));
        }
    }

    /** Solo el contenido: zip legible con la clase de arranque y la principal. Nunca lanza. */
    public static Resultado verificarContenido(Path jar, String mainclass) {
        try (ZipFile z = new ZipFile(jar.toFile())) {
            if (z.getEntry(CLASE_ARRANQUE) == null) return Resultado.no("el jar no trae " + CLASE_ARRANQUE);
            if (mainclass != null && z.getEntry(mainclass.replace('.', '/') + ".class") == null)
                return Resultado.no("el jar no trae la clase principal " + mainclass);
            return Resultado.OK;
        } catch (Exception ex) {
            return Resultado.no("no es un jar válido: " + Log.causa(ex));
        }
    }
}
