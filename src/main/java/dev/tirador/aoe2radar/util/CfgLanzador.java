package dev.tirador.aoe2radar.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * El {@code app/aoe2radar.cfg} que deja jpackage junto al jar: el lanzador (aoe2radar.exe) lo lee al arrancar y de
 * ahí saca el classpath, la clase principal y las opciones de Java. El actualizador (1.4) solo cambia la línea del jar
 * de la app; el runtime y el resto del .cfg no se tocan. Formato real (jpackage 21, fin de línea CRLF salvo una línea
 * en blanco con LF sola, que se respeta byte a byte):
 * <pre>
 * [Application]
 * app.classpath=$APPDIR\aoe2radar-1.3.0.jar
 * app.mainclass=dev.tirador.aoe2radar.SpoilerFreeRecs
 * app.classpath=$APPDIR\flatlaf-3.7.2.jar
 *
 * [JavaOptions]
 * java-options=-Djpackage.app-version=1.3
 * java-options=-Dfile.encoding=UTF-8
 * </pre>
 * Todo lo que no sea el jar de la app ni la opción {@code -Djpackage.app-version} se copia tal cual. Se lee y escribe
 * en ISO-8859-1 para no alterar ningún byte (el archivo es ASCII; un byte raro pasa intacto).
 * <p>La escritura es atómica y SIN respaldo: temporal en la misma carpeta y {@code ATOMIC_MOVE}. Si el movimiento
 * falla, el .cfg de antes queda intacto (a diferencia de {@link Archivos#escribirAtomico}, que en ese caso escribe
 * directo sobre el destino y, con el .cfg, podría dejar la app sin arrancar).
 */
public final class CfgLanzador {
    private CfgLanzador() {}

    /** Nombre del jar de la app en el classpath (el de jpackage, «aoe2radar-1.3.0.jar», o el de una actualización,
     *  «aoe2radar-1.5.jar»). Solo letras, cifras, punto, guion y guion bajo: nada de rutas. */
    public static final Pattern JAR_APP = Pattern.compile("aoe2radar-[0-9A-Za-z._-]+\\.jar", Pattern.CASE_INSENSITIVE);

    static final String CLAVE_CLASSPATH = "app.classpath=";
    static final String CLAVE_MAINCLASS = "app.mainclass=";
    static final String CLAVE_OPCION = "java-options=";
    static final String PREFIJO_APPDIR = "$APPDIR\\";
    static final String OPCION_VERSION = "-Djpackage.app-version=";

    /** Lo que importa del .cfg. jarApp: el nombre del jar de la app (sin $APPDIR\). resto: los demás jars del classpath,
     *  en orden. opciones: las java-options sin la de la versión (esa cambia en cada versión y la reescribe el
     *  actualizador). */
    public record Datos(String jarApp, List<String> resto, String mainclass, List<String> opciones) { }

    /** Una línea y su fin de línea tal cual ("\r\n", "\n", "\r" o "" en la última). */
    private record Linea(String texto, String fin) { }

    private static List<Linea> lineas(String contenido) {
        List<Linea> out = new ArrayList<>();
        int i = 0, n = contenido.length();
        while (i < n) {
            int j = i;
            while (j < n && contenido.charAt(j) != '\r' && contenido.charAt(j) != '\n') j++;
            String fin;
            if (j >= n) fin = "";
            else if (contenido.charAt(j) == '\r' && j + 1 < n && contenido.charAt(j + 1) == '\n') fin = "\r\n";
            else fin = String.valueOf(contenido.charAt(j));
            out.add(new Linea(contenido.substring(i, j), fin));
            i = j + fin.length();
        }
        return out;
    }

    /** Nombre del jar de una entrada del classpath («$APPDIR\x.jar» → «x.jar»); null si no va en $APPDIR. */
    private static String nombreEnAppdir(String valor) {
        String v = valor.strip();
        if (!v.regionMatches(true, 0, PREFIJO_APPDIR, 0, PREFIJO_APPDIR.length())) return null;
        return v.substring(PREFIJO_APPDIR.length());
    }

    private static boolean esJarApp(String nombre) { return nombre != null && JAR_APP.matcher(nombre).matches(); }

    /** Lee el contenido de un .cfg. Null si no tiene exactamente el formato esperado: un [Application] con un único
     *  jar de la app en $APPDIR y una app.mainclass. */
    public static Datos leer(String contenido) {
        if (contenido == null) return null;
        String seccion = "", jarApp = null, mainclass = null;
        int jarsApp = 0;
        List<String> resto = new ArrayList<>(), opciones = new ArrayList<>();
        for (Linea l : lineas(contenido)) {
            String t = l.texto().strip();
            if (t.startsWith("[") && t.endsWith("]")) { seccion = t; continue; }
            if (seccion.equals("[Application]") && t.startsWith(CLAVE_CLASSPATH)) {
                String nombre = nombreEnAppdir(t.substring(CLAVE_CLASSPATH.length()));
                if (esJarApp(nombre)) { jarsApp++; jarApp = nombre; }
                else resto.add(nombre != null ? nombre : t.substring(CLAVE_CLASSPATH.length()).strip());
            } else if (seccion.equals("[Application]") && t.startsWith(CLAVE_MAINCLASS)) {
                mainclass = t.substring(CLAVE_MAINCLASS.length()).strip();
            } else if (seccion.equals("[JavaOptions]") && t.startsWith(CLAVE_OPCION)) {
                String o = t.substring(CLAVE_OPCION.length()).strip();
                if (!o.startsWith(OPCION_VERSION)) opciones.add(o);
            }
        }
        if (jarsApp != 1 || mainclass == null || mainclass.isEmpty()) return null;
        return new Datos(jarApp, List.copyOf(resto), mainclass, List.copyOf(opciones));
    }

    /** Lee el .cfg del disco (null si no existe o no tiene el formato esperado). */
    public static Datos leer(Path cfg) {
        try {
            return leer(Files.readString(cfg, StandardCharsets.ISO_8859_1));
        } catch (IOException ex) {
            return null;
        }
    }

    /** El contenido con el jar de la app cambiado por jarNuevo y la opción de versión por versionNueva; el resto, byte a
     *  byte igual. Null si el contenido no tiene el formato esperado o jarNuevo no es un nombre de jar de la app. */
    public static String reescribir(String contenido, String jarNuevo, String versionNueva) {
        if (leer(contenido) == null || !esJarApp(jarNuevo)) return null;
        if (versionNueva == null || !versionNueva.matches("[0-9A-Za-z._-]+")) return null;
        StringBuilder sb = new StringBuilder(contenido.length() + 16);
        String seccion = "";
        for (Linea l : lineas(contenido)) {
            String t = l.texto().strip();
            if (t.startsWith("[") && t.endsWith("]")) seccion = t;
            String texto = l.texto();
            if (seccion.equals("[Application]") && t.startsWith(CLAVE_CLASSPATH)
                    && esJarApp(nombreEnAppdir(t.substring(CLAVE_CLASSPATH.length())))) {
                texto = CLAVE_CLASSPATH + PREFIJO_APPDIR + jarNuevo;
            } else if (seccion.equals("[JavaOptions]") && t.startsWith(CLAVE_OPCION + OPCION_VERSION)) {
                texto = CLAVE_OPCION + OPCION_VERSION + versionNueva;
            }
            sb.append(texto).append(l.fin());
        }
        return sb.toString();
    }

    /** Mueve un archivo sobre otro. Paquete: los tests lo sustituyen para simular un fallo. */
    @FunctionalInterface
    interface Movedor { void mover(Path origen, Path destino) throws IOException; }

    static final Movedor MOVER_ATOMICO = (o, d) -> Files.move(o, d, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

    /** Escribe destino de forma atómica y sin respaldo: temporal en la misma carpeta, forzado a disco y ATOMIC_MOVE. Si algo falla,
     *  destino queda como estaba, el temporal se borra y se devuelve false (con una línea en el log). Nunca lanza. */
    public static boolean escribirAtomico(Path destino, byte[] datos) {
        return escribirAtomico(destino, datos, MOVER_ATOMICO);
    }

    static boolean escribirAtomico(Path destino, byte[] datos, Movedor movedor) {
        Path tmp = null;
        try {
            Path dir = destino.toAbsolutePath().getParent();
            tmp = Files.createTempFile(dir, destino.getFileName().toString(), ".tmp");
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer b = ByteBuffer.wrap(datos);
                while (b.hasRemaining()) ch.write(b);
                ch.force(true);   // los datos en disco ANTES del renombrado: un corte de luz no deja el nombre bueno vacío
            }
            movedor.mover(tmp, destino);
            return true;
        } catch (Exception ex) {
            log("actualizar: no se pudo escribir " + destino + " (" + causa(ex) + "); queda como estaba");
            return false;
        } finally {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
        }
    }
}
