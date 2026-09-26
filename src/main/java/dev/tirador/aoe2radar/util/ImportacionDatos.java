package dev.tirador.aoe2radar.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;

/**
 * Importar los datos de una aoe2radar 1.x en zip (config.properties, players.txt, cachés y recs) a las carpetas de
 * la app instalada. Solo disco, sin Swing: la pregunta y el selector de carpeta viven en ui.ImportarDatos. Nunca
 * mueve ni borra nada del origen: COPIA. Dos pasos:
 * <ol>
 * <li>{@link #preparar}: en un hilo de fondo, con la app funcionando. Copia los datos a {@code .importando} dentro
 *     de la carpeta de datos y las recs a la carpeta de recs (sin sobrescribir). Si falla, borra {@code .importando}
 *     y la carpeta de datos no ha cambiado (las recs ya copiadas se quedan, y se dice).</li>
 * <li>{@link #colocar}: justo antes de relanzar y salir. Con config.properties bloqueado (monitor de Config) y las
 *     escrituras de Archivos en pausa, guarda copia de lo que va a reemplazar en {@code .antes_de_importar/<fecha>}
 *     y mueve lo preparado a su sitio, con players.txt, config.properties y la marca los últimos. Si un movimiento
 *     falla, restaura desde esa copia.</li>
 * </ol>
 */
public final class ImportacionDatos {
    private ImportacionDatos() {}

    /** Clave de config.properties: la importación ya se ofreció (se contestara lo que se contestara). */
    public static final String CLAVE_OFRECIDA = "importar_ofrecido";
    /** Se deja en la carpeta de datos al terminar, con el origen y el resultado. */
    static final String MARCA = "importado_desde.txt";
    /** Carpeta de preparación, dentro de la de datos. */
    static final String PREPARACION = ".importando";
    /** Copia de lo que había antes de cada importación, dentro de la de datos (una subcarpeta por fecha). */
    public static final String COPIA_ANTERIOR = ".antes_de_importar";
    /** Terminación de una rec a medio copiar (la barre Archivos.limpiarTemporales al arrancar). */
    public static final String SUFIJO_REC_TEMPORAL = ".importando";
    /** Lista blanca de lo que se importa a la carpeta de datos, en el orden en que se coloca (disparadores al final).
     *  No: descargas.log (historial de otra instalación), techtree/ (caché que se rehace desde el jar), ni los
     *  recursos de la app (app/, runtime/, banderas/, el exe). Las recs van aparte, a la carpeta de recs. */
    static final List<String> DATOS = List.of("sfrdata", "top_cache.txt", "players.txt", "config.properties");

    /** ¿Se ofrece la importación al arrancar? Solo si la carpeta de datos estaba vacía de config y players al
     *  arrancar (Sistema.datosNuevos) y no se ofreció ya. */
    public static boolean debeOfrecer(boolean datosNuevos, String ofrecida) {
        return datosNuevos && !Boolean.parseBoolean(ofrecida);
    }

    /** ¿Hay que pedir confirmación antes de importar? Si ya hay datos del usuario que se sustituirían: players.txt o
     *  config.properties en la carpeta, o la carpeta ya tenía datos al arrancar. */
    public static boolean pideConfirmacion(boolean datosNuevos, Path datos) {
        return !datosNuevos || Files.exists(datos.resolve("players.txt")) || Files.exists(datos.resolve("config.properties"));
    }

    /** ¿Vale origen? Tiene config.properties o players.txt y no es la propia carpeta de datos (ni está dentro de
     *  ella, ni la contiene): elegir por error la carpeta nueva no debe copiarla sobre sí misma. */
    public static boolean valida(Path origen, Path datos) {
        if (origen == null || !Files.isDirectory(origen)) return false;
        Path o = origen.toAbsolutePath().normalize(), d = datos.toAbsolutePath().normalize();
        if (o.startsWith(d) || d.startsWith(o)) return false;
        return Files.exists(o.resolve("config.properties")) || Files.exists(o.resolve("players.txt"));
    }

    /** Lo preparado en el paso 1. {@code error} null si fue bien (entonces {@code prep} existe). */
    public record Preparado(Path origen, Path prep, String error, int recsCopiadas, int recsYaEstaban,
                            boolean autoarranque) {
        public boolean ok() { return error == null; }
    }

    /** Cómo acabó el paso 2. */
    public enum Estado {
        /** Todo colocado. */
        IMPORTADO,
        /** Falló antes de reemplazar nada: la carpeta de datos está como estaba. */
        SIN_CAMBIOS,
        /** Falló a mitad y se restauró lo reemplazado desde la copia: está como estaba. */
        RESTAURADO,
        /** Falló a mitad y tampoco se pudo restaurar todo: datos mezclados; lo anterior sigue en la copia. */
        A_MEDIAS
    }

    public record Resultado(Estado estado, String error, Path copiaAnterior) {
        public boolean ok() { return estado == Estado.IMPORTADO; }
    }

    /** Cómo se copia un archivo (el real; en los tests, uno que falla a mitad). */
    @FunctionalInterface
    interface Copiador { void copiar(Path origen, Path destino) throws IOException; }

    /** Cómo se mueve un archivo a su sitio (el real; en los tests, uno que falla a mitad). */
    @FunctionalInterface
    interface Movedor { void mover(Path origen, Path destino) throws IOException; }

    /** Copia sin COPY_ATTRIBUTES y quitando el «solo lectura» que Windows copia igualmente: un archivo del zip
     *  marcado así no debe quedar sin poder guardarse en la carpeta nueva. */
    static final Copiador COPIA_REAL = (o, d) -> {
        Files.copy(o, d, StandardCopyOption.REPLACE_EXISTING);
        try { Files.setAttribute(d, "dos:readonly", false); } catch (UnsupportedOperationException | IOException ignored) { }
    };

    static final Movedor MOVER_REAL = (o, d) -> Files.move(o, d, StandardCopyOption.REPLACE_EXISTING);

    /** Paso 1 (disco: nunca desde el EDT). */
    public static Preparado preparar(Path origen, Path datos, Path recs) { return preparar(origen, datos, recs, COPIA_REAL); }

    static Preparado preparar(Path origen, Path datos, Path recs, Copiador copiador) {
        if (!valida(origen, datos)) return new Preparado(origen, null, "sin config.properties ni players.txt", 0, 0, false);
        Path o = origen.toAbsolutePath().normalize();
        Path prep = datos.resolve(PREPARACION);
        int[] cuenta = { 0, 0 };
        boolean auto = false;
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
                    Path v = rutaAbsolutaONull(fijada);
                    if (v == null) {
                        p.remove(Sistema.CLAVE_CARPETA_RECS);   // relativa o inválida: no significa nada aquí
                    } else if (v.normalize().startsWith(o)) {   // dentro del zip viejo: a la nueva
                        recsViejas = v.normalize();
                        p.setProperty(Sistema.CLAVE_CARPETA_RECS, recs.toAbsolutePath().toString());
                    } else {
                        recsViejas = null;   // fuera del zip: la carpeta del usuario sigue donde está, no se copia
                    }
                }
                auto = Boolean.parseBoolean(p.getProperty("autoarranque", "false"));
                p.setProperty(CLAVE_OFRECIDA, "true");
                try (OutputStream out = Files.newOutputStream(prep.resolve("config.properties"))) { p.store(out, NOMBRE); }
            }
            if (recsViejas != null && Files.isDirectory(recsViejas)) copiarRecs(recsViejas, recs, copiador, cuenta);
            return new Preparado(o, prep, null, cuenta[0], cuenta[1], auto);
        } catch (Exception ex) {
            try { borrarArbol(prep); } catch (Exception ignored) { }
            return new Preparado(o, null, ex.toString(), cuenta[0], cuenta[1], auto);
        }
    }

    private static Path rutaAbsolutaONull(String s) {
        try {
            Path p = Path.of(s.strip());
            return p.isAbsolute() ? p : null;
        } catch (InvalidPathException ex) { return null; }
    }

    /** Recs: sin sobrescribir las que ya estén; cada una a un temporal y renombrada, para no dejar una rec a medias
     *  con el nombre bueno (que ya nunca se volvería a copiar). */
    private static void copiarRecs(Path desde, Path hacia, Copiador copiador, int[] cuenta) throws IOException {
        Files.createDirectories(hacia);
        try (Stream<Path> s = Files.list(desde)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                if (!Files.isRegularFile(f)) continue;
                Path d = hacia.resolve(f.getFileName().toString());
                if (Files.exists(d)) { cuenta[1]++; continue; }
                Path tmp = hacia.resolve(f.getFileName() + SUFIJO_REC_TEMPORAL);
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

    /**
     * Paso 2: pone lo preparado en su sitio. Orden de cerrojos: primero Config.class, después la pausa de Archivos
     * (el mismo que sigue guardarConfig), nunca al revés. Con el monitor de Config (el de leerConfig/guardarConfig: nadie lee ni
     * guarda config.properties a medias) y las escrituras de Archivos en pausa (el Timer de países, la caché del top,
     * sfr-data, players.txt). Si {@code seguirEnPausa} y sale bien, las escrituras siguen en pausa: la app va a
     * relanzarse y salir, y nada de lo que tiene en memoria debe caer encima de lo importado. En cualquier otro caso
     * se reanudan. Borra siempre la preparación; la copia de lo anterior se queda siempre (si hubo algo que copiar).
     */
    public static Resultado colocar(Preparado p, Path datos, boolean seguirEnPausa) {
        return colocar(p, datos, seguirEnPausa, COPIA_REAL, MOVER_REAL);
    }

    static Resultado colocar(Preparado p, Path datos, boolean seguirEnPausa, Copiador copiador, Movedor movedor) {
        if (!p.ok()) return new Resultado(Estado.SIN_CAMBIOS, p.error(), null);
        synchronized (Config.class) {
            Archivos.congelarEscrituras();
            Resultado r = null;
            try {
                r = colocarYa(p, datos, copiador, movedor);
                return r;
            } finally {
                if (r == null || !r.ok() || !seguirEnPausa) Archivos.descongelarEscrituras();
            }
        }
    }

    private static Resultado colocarYa(Preparado p, Path datos, Copiador copiador, Movedor movedor) {
        String sello = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path copia = datos.resolve(COPIA_ANTERIOR).resolve(sello);
        Path prep = p.prep();
        List<Path> relativos = new ArrayList<>();   // archivos a colocar, relativos a prep, en orden
        List<Path> movidos = new ArrayList<>();
        try {
            // Inventario y copia de lo que se va a reemplazar: si esto falla, aún no se ha tocado nada.
            try {
                for (String n : DATOS) listarArchivos(prep, prep.resolve(n), relativos);
                for (Path rel : relativos) {
                    Path actual = datos.resolve(rel);
                    if (Files.exists(actual)) {
                        Files.createDirectories(copia.resolve(rel).getParent());
                        copiador.copiar(actual, copia.resolve(rel));
                    }
                }
            } catch (Exception ex) {
                return new Resultado(Estado.SIN_CAMBIOS, ex.toString(), Files.exists(copia) ? copia : null);
            }
            Path copiaFinal = Files.exists(copia) ? copia : null;
            // Colocar; la marca, la última.
            try {
                for (Path rel : relativos) {
                    Path d = datos.resolve(rel);
                    Files.createDirectories(d.getParent());
                    movedor.mover(prep.resolve(rel), d);
                    movidos.add(rel);
                }
                Files.writeString(datos.resolve(MARCA), p.origen() + System.lineSeparator()
                        + "recs copiadas: " + p.recsCopiadas() + ", ya estaban: " + p.recsYaEstaban() + System.lineSeparator()
                        + "copia de lo anterior: " + (copiaFinal != null ? copiaFinal : "(nada que copiar)") + System.lineSeparator(),
                        StandardCharsets.UTF_8);
                return new Resultado(Estado.IMPORTADO, null, copiaFinal);
            } catch (Exception ex) {
                boolean restaurado = restaurar(datos, copia, movidos);
                return new Resultado(restaurado ? Estado.RESTAURADO : Estado.A_MEDIAS, ex.toString(), copiaFinal);
            }
        } finally {
            try { borrarArbol(prep); } catch (Exception ignored) { }
        }
    }

    /** Deshace lo movido: lo que existía vuelve de la copia; lo que no existía, se borra. */
    private static boolean restaurar(Path datos, Path copia, List<Path> movidos) {
        boolean ok = true;
        for (int i = movidos.size() - 1; i >= 0; i--) {
            Path rel = movidos.get(i), d = datos.resolve(rel), c = copia.resolve(rel);
            try {
                if (Files.exists(c)) Files.copy(c, d, StandardCopyOption.REPLACE_EXISTING);
                else Files.deleteIfExists(d);
            } catch (Exception ex) { ok = false; }
        }
        return ok;
    }

    private static void listarArchivos(Path base, Path p, List<Path> salida) throws IOException {
        if (!Files.exists(p)) return;
        if (Files.isDirectory(p)) {
            try (Stream<Path> s = Files.walk(p)) {
                for (Path f : s.filter(Files::isRegularFile).sorted().toList()) salida.add(base.relativize(f));
            }
        } else {
            salida.add(base.relativize(p));
        }
    }

    /** Pasos 1 y 2 seguidos, reanudando las escrituras al acabar (para los tests y usos sin relanzar). */
    static Resultado importar(Path origen, Path datos, Path recs, Copiador copiador, Movedor movedor) {
        Preparado p = preparar(origen, datos, recs, copiador);
        if (!p.ok()) return new Resultado(Estado.SIN_CAMBIOS, p.error(), null);
        return colocar(p, datos, false, copiador, movedor);
    }

    /** La respuesta a la oferta del arranque en su versión sin interfaz: null es «No, gracias» y no toca el disco. */
    static Resultado responder(Path elegida, Path datos, Path recs) {
        return elegida == null ? null : importar(elegida, datos, recs, COPIA_REAL, MOVER_REAL);
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

    private static void borrarArbol(Path p) throws IOException {
        if (p == null || !Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(x);
        }
    }
}
