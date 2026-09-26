package dev.tirador.aoe2radar.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;

/**
 * Importar los datos de una aoe2radar 1.x en zip (config.properties, players.txt, cachés y recs) a las carpetas de
 * la app instalada. Solo disco, sin Swing: la pregunta y el selector de carpeta viven en ui.ImportarDatos, que llama
 * aquí desde un hilo de fondo. Nunca mueve ni borra nada del origen: COPIA.
 * <p>Sin carpetas a medias: los datos se copian primero a {@code .importando} dentro de la carpeta de datos, y solo
 * cuando todo está copiado se mueven a su sitio (renombrados en el mismo disco), con players.txt, config.properties
 * y la marca {@code importado_desde.txt} los últimos. Si algo falla antes, se borra {@code .importando} y la
 * carpeta de datos queda como estaba.
 */
public final class ImportacionDatos {
    private ImportacionDatos() {}

    /** Clave de config.properties: la importación ya se ofreció (se contestara lo que se contestara). */
    public static final String CLAVE_OFRECIDA = "importar_ofrecido";
    /** Se deja en la carpeta de datos al terminar, con la carpeta de origen. */
    static final String MARCA = "importado_desde.txt";
    /** Lista blanca de lo que se importa a la carpeta de datos, en el orden en que se coloca (disparadores al final).
     *  No: descargas.log (historial de otra instalación), techtree/ (caché que se rehace desde el jar), ni los
     *  recursos de la app (app/, runtime/, banderas/, el exe). Las recs van aparte, a la carpeta de recs. */
    static final List<String> DATOS = List.of("sfrdata", "top_cache.txt", "players.txt", "config.properties");

    /** ¿Se ofrece la importación al arrancar? Solo si la carpeta de datos estaba vacía de config y players al
     *  arrancar (Sistema.datosNuevos) y no se ofreció ya. */
    public static boolean debeOfrecer(boolean datosNuevos, String ofrecida) {
        return datosNuevos && !Boolean.parseBoolean(ofrecida);
    }

    /** ¿Vale origen? Tiene config.properties o players.txt y no es la propia carpeta de datos (ni está dentro de
     *  ella, ni la contiene): elegir por error la carpeta nueva no debe copiarla sobre sí misma. */
    public static boolean valida(Path origen, Path datos) {
        if (origen == null || !Files.isDirectory(origen)) return false;
        Path o = origen.toAbsolutePath().normalize(), d = datos.toAbsolutePath().normalize();
        if (o.startsWith(d) || d.startsWith(o)) return false;
        return Files.exists(o.resolve("config.properties")) || Files.exists(o.resolve("players.txt"));
    }

    /** Qué pasó: {@code error} null si fue bien. */
    public record Resultado(String error, int recsCopiadas, int recsYaEstaban) {
        public boolean ok() { return error == null; }
    }

    /** Cómo se copia un archivo (el real; en los tests, uno que falla a mitad). */
    @FunctionalInterface
    interface Copiador { void copiar(Path origen, Path destino) throws IOException; }

    static final Copiador COPIA_REAL = (o, d) ->
            Files.copy(o, d, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

    /** La respuesta a la oferta del arranque: null es «No, gracias» y no toca el disco. */
    public static Resultado responder(Path elegida, Path datos, Path recs) {
        return elegida == null ? null : importar(elegida, datos, recs);
    }

    /** Importa de origen a datos (y sus recs a recs). Disco: nunca desde el EDT. */
    public static Resultado importar(Path origen, Path datos, Path recs) { return importar(origen, datos, recs, COPIA_REAL); }

    static Resultado importar(Path origen, Path datos, Path recs, Copiador copiador) {
        if (!valida(origen, datos)) return new Resultado("sin config.properties ni players.txt", 0, 0);
        Path o = origen.toAbsolutePath().normalize();
        Path prep = datos.resolve(".importando");
        int[] cuenta = { 0, 0 };
        try {
            borrarArbol(prep);   // restos de un intento anterior cortado
            Files.createDirectories(prep);
            for (String n : DATOS) {
                if (n.equals("config.properties")) continue;   // se reescribe abajo
                Path s = o.resolve(n);
                if (Files.exists(s)) copiarArbol(s, prep.resolve(n), copiador);
            }
            Path recsViejas = o.resolve("recs");
            Path cfg = o.resolve("config.properties");
            if (Files.exists(cfg)) {
                Properties p = new Properties();
                try (InputStream in = Files.newInputStream(cfg)) { p.load(in); }
                String fijada = p.getProperty(Sistema.CLAVE_CARPETA_RECS);
                if (fijada != null && !fijada.isBlank()) {
                    Path v = rutaONull(fijada);
                    if (v != null && v.toAbsolutePath().normalize().startsWith(o)) {   // dentro del zip viejo: a la nueva
                        recsViejas = v.toAbsolutePath().normalize();
                        p.setProperty(Sistema.CLAVE_CARPETA_RECS, recs.toAbsolutePath().toString());
                    } else {
                        recsViejas = null;   // fuera del zip: la carpeta del usuario sigue donde está, no se copia
                    }
                }
                p.setProperty(CLAVE_OFRECIDA, "true");
                try (OutputStream out = Files.newOutputStream(prep.resolve("config.properties"))) { p.store(out, NOMBRE); }
            }
            if (recsViejas != null && Files.isDirectory(recsViejas)) copiarRecs(recsViejas, recs, copiador, cuenta);
            Files.writeString(prep.resolve(MARCA), o + System.lineSeparator(), StandardCharsets.UTF_8);
            colocar(prep, datos);
            return new Resultado(null, cuenta[0], cuenta[1]);
        } catch (Exception ex) {
            try { borrarArbol(prep); } catch (Exception ignored) { }
            return new Resultado(ex.toString(), cuenta[0], cuenta[1]);
        }
    }

    private static Path rutaONull(String s) {
        try { return Path.of(s.strip()); } catch (InvalidPathException ex) { return null; }
    }

    /** Recs: sin sobrescribir las que ya estén; cada una a un temporal y renombrada, para no dejar una rec a medias
     *  con el nombre bueno (que ya nunca se volvería a copiar). Van antes de colocar los datos: si fallan, no se
     *  importa nada más y las ya copiadas se quedan (reintentar las salta). */
    private static void copiarRecs(Path desde, Path hacia, Copiador copiador, int[] cuenta) throws IOException {
        Files.createDirectories(hacia);
        try (Stream<Path> s = Files.list(desde)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                if (!Files.isRegularFile(f)) continue;
                Path d = hacia.resolve(f.getFileName().toString());
                if (Files.exists(d)) { cuenta[1]++; continue; }
                Path tmp = hacia.resolve(f.getFileName() + ".importando");
                try {
                    copiador.copiar(f, tmp);
                    Files.move(tmp, d);
                } finally {
                    Files.deleteIfExists(tmp);
                }
                cuenta[0]++;
            }
        }
    }

    /** Mueve lo preparado a la carpeta de datos, en el orden de DATOS, y la marca la última. */
    private static void colocar(Path prep, Path datos) throws IOException {
        for (String n : DATOS) moverArbol(prep.resolve(n), datos.resolve(n));
        moverArbol(prep.resolve(MARCA), datos.resolve(MARCA));
        borrarArbol(prep);
    }

    private static void copiarArbol(Path s, Path d, Copiador copiador) throws IOException {
        if (Files.isDirectory(s)) {
            Files.createDirectories(d);
            try (Stream<Path> hijos = Files.list(s)) {
                for (Path h : (Iterable<Path>) hijos::iterator) copiarArbol(h, d.resolve(h.getFileName().toString()), copiador);
            }
        } else {
            copiador.copiar(s, d);
        }
    }

    private static void moverArbol(Path s, Path d) throws IOException {
        if (!Files.exists(s)) return;
        if (Files.isDirectory(s)) {
            Files.createDirectories(d);
            try (Stream<Path> hijos = Files.list(s)) {
                for (Path h : (Iterable<Path>) hijos::iterator) moverArbol(h, d.resolve(h.getFileName().toString()));
            }
        } else {
            Files.move(s, d, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void borrarArbol(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(x);
        }
    }
}
