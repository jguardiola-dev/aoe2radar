package dev.tirador.aoe2radar.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Buscar la carpeta de datos de una aoe2radar 1.x (o SpoilerFreeRecs 1.0) dentro de otra. Las carpetas reales salen
 * anidadas al descomprimir («…\aoe2radar\app\aoe2radar\» tiene el exe y config.properties): el usuario elige la de
 * arriba y antes la validación decía que no valía. Solo disco, sin Swing; nunca desde el EDT.
 * <p>Candidata: una carpeta con config.properties o players.txt junto a aoe2radar.exe o SpoilerFreeRecs.exe, o
 * junto a app/ o runtime/. Se baja hasta {@link #PROFUNDIDAD} niveles, sin seguir enlaces simbólicos ni uniones
 * (con NOFOLLOW_LINKS una unión de Windows no es «directorio»), con tope de carpetas visitadas y de tiempo.
 */
public final class BusquedaDatos {
    private BusquedaDatos() {}

    public static final int PROFUNDIDAD = 4;
    /** Tope de carpetas visitadas por búsqueda: un Escritorio con miles de carpetas no la alarga sin fin. */
    public static final int TOPE_CARPETAS = 20_000;

    static final List<String> DATOS = List.of("config.properties", "players.txt");
    static final List<String> EJECUTABLES = List.of("aoe2radar.exe", "SpoilerFreeRecs.exe");
    static final List<String> CARPETAS_APP = List.of("app", "runtime");

    /**
     * Una carpeta de datos encontrada. {@code version}: la de la app que la escribió (del .cfg de jpackage o del
     * nombre del jar), o null si no se sabe. {@code aoe2radar}: es aoe2radar (no SpoilerFreeRecs 1.0).
     * {@code conJugadores}: players.txt tiene algún jugador. {@code modificado}: lo más reciente de config.properties
     * y players.txt.
     */
    public record Candidata(Path carpeta, FileTime modificado, String version, boolean aoe2radar, boolean conJugadores) {
        /** Para la oferta: «aoe2radar 1.3», «SpoilerFreeRecs», «aoe2radar». */
        public String nombre() {
            return (aoe2radar ? "aoe2radar" : "SpoilerFreeRecs") + (version != null ? " " + version : "");
        }
    }

    /** Orden de preferencia: versión mayor; a igualdad, aoe2radar antes que SpoilerFreeRecs; luego con jugadores;
     *  luego lo más reciente. Una copia vieja con la config tocada hace poco no gana a la 1.3. */
    public static final Comparator<Candidata> PREFERENCIA = Comparator
            .comparing(Candidata::version, Comparator.nullsLast(BusquedaDatos::compararVersionesDesc))
            .thenComparing(Candidata::aoe2radar, Comparator.reverseOrder())
            .thenComparing(Candidata::conJugadores, Comparator.reverseOrder())
            .thenComparing(Candidata::modificado, Comparator.reverseOrder());

    private static int compararVersionesDesc(String a, String b) { return compararVersiones(b, a); }

    /** Comparación numérica de versiones por tramos («1.10» > «1.9», «1.3» = «1.3.0»). */
    public static int compararVersiones(String a, String b) {
        String[] x = a.split("[^0-9]+"), y = b.split("[^0-9]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long u = i < x.length && !x[i].isEmpty() ? Long.parseLong(x[i]) : 0;
            long v = i < y.length && !y[i].isEmpty() ? Long.parseLong(y[i]) : 0;
            if (u != v) return Long.compare(u, v);
        }
        return 0;
    }

    private static final Pattern VERSION_CFG = Pattern.compile("-Djpackage\\.app-version=([0-9]+(?:\\.[0-9]+)*)");
    private static final Pattern VERSION_JAR = Pattern.compile("^(aoe2radar|SpoilerFreeRecs)[-_ ]?([0-9]+(?:\\.[0-9]+)*)?.*\\.jar$",
            Pattern.CASE_INSENSITIVE);

    /** Lee una candidata: versión (app/*.cfg de jpackage; si no, el nombre del jar en app/ o junto al exe), si es
     *  aoe2radar o SpoilerFreeRecs, si players.txt tiene jugadores y la fecha. */
    static Candidata leer(Path dir) {
        String version = null;
        Boolean aoe = null;
        Path app = dir.resolve("app");
        for (Path carpeta : List.of(app, dir)) {
            if (!Files.isDirectory(carpeta, LinkOption.NOFOLLOW_LINKS)) continue;
            try (Stream<Path> s = Files.list(carpeta)) {
                for (Path f : (Iterable<Path>) s::iterator) {
                    String n = f.getFileName().toString();
                    if (version == null && n.toLowerCase(Locale.ROOT).endsWith(".cfg") && Files.isRegularFile(f)) {
                        try {
                            Matcher m = VERSION_CFG.matcher(Files.readString(f, StandardCharsets.UTF_8));
                            if (m.find()) {
                                version = m.group(1);
                                if (aoe == null) aoe = !n.toLowerCase(Locale.ROOT).startsWith("spoilerfreerecs");
                            }
                        } catch (IOException | RuntimeException ignored) { }
                    }
                    Matcher j = VERSION_JAR.matcher(n);
                    if (j.matches()) {
                        if (aoe == null) aoe = j.group(1).equalsIgnoreCase("aoe2radar");
                        if (version == null && j.group(2) != null) version = j.group(2);
                    }
                }
            } catch (IOException | RuntimeException ignored) { }
        }
        if (Files.isRegularFile(dir.resolve("aoe2radar.exe"))) aoe = true;
        else if (aoe == null && Files.isRegularFile(dir.resolve("SpoilerFreeRecs.exe"))) aoe = false;
        return new Candidata(dir, modificado(dir), version, aoe == null || aoe, conJugadores(dir.resolve("players.txt")));
    }

    /** players.txt con al menos una línea que no esté vacía ni sea un comentario. */
    static boolean conJugadores(Path players) {
        try (Stream<String> l = Files.lines(players, StandardCharsets.UTF_8)) {
            return l.map(String::strip).anyMatch(x -> !x.isEmpty() && !x.startsWith("#"));
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private record Nodo(Path carpeta, int nivel) { }

    /** ¿Es dir una carpeta de datos de una 1.x (datos junto al exe o a app/ runtime/)? */
    public static boolean esCandidata(Path dir) {
        if (!tieneDatos(dir)) return false;
        for (String e : EJECUTABLES) if (Files.isRegularFile(dir.resolve(e))) return true;
        for (String c : CARPETAS_APP) if (Files.isDirectory(dir.resolve(c), LinkOption.NOFOLLOW_LINKS)) return true;
        return false;
    }

    static boolean tieneDatos(Path dir) {
        for (String d : DATOS) if (Files.isRegularFile(dir.resolve(d))) return true;
        return false;
    }

    /** Fecha más reciente de config.properties / players.txt en dir (EPOCH si no hay ninguno o no se puede leer). */
    static FileTime modificado(Path dir) {
        FileTime max = FileTime.fromMillis(0);
        for (String d : DATOS) {
            try {
                Path f = dir.resolve(d);
                if (Files.isRegularFile(f)) {
                    FileTime t = Files.getLastModifiedTime(f);
                    if (t.compareTo(max) > 0) max = t;
                }
            } catch (IOException ignored) { }
        }
        return max;
    }

    /**
     * Candidatas dentro de las raíces (cada raíz incluida, y sus subcarpetas hasta profundidad niveles), la preferida
     * primero ({@link #PREFERENCIA}), sin repetir y sin las que estén dentro de excluir (la carpeta de datos, la de la app).
     * Para al pasar tope carpetas visitadas o el instante limiteNanos (System.nanoTime): devuelve lo encontrado.
     */
    public static List<Candidata> buscar(List<Path> raices, int profundidad, int tope, long limiteNanos, List<Path> excluir) {
        Set<Path> vistas = new LinkedHashSet<>();
        List<Candidata> salida = new ArrayList<>();
        int[] visitadas = { 0 };
        for (Path raiz : raices) {
            if (raiz == null) continue;
            Deque<Nodo> cola = new ArrayDeque<>();   // en anchura: las más cercanas a la raíz, primero
            cola.add(new Nodo(raiz.toAbsolutePath().normalize(), 0));
            while (!cola.isEmpty()) {
                if (visitadas[0] >= tope || System.nanoTime() - limiteNanos > 0) return ordenar(salida);
                Nodo n = cola.poll();
                Path dir = n.carpeta();
                int nivel = n.nivel();
                if (!vistas.add(dir) || !esDirectorioReal(dir)) continue;
                visitadas[0]++;
                if (esCandidata(dir) && !excluida(dir, excluir)) salida.add(leer(dir));
                if (nivel >= profundidad) continue;
                try (Stream<Path> hijos = Files.list(dir)) {
                    for (Path h : (Iterable<Path>) hijos::iterator) {
                        if (esDirectorioReal(h)) cola.add(new Nodo(h, nivel + 1));
                    }
                } catch (IOException | RuntimeException ignored) { }   // sin permiso, desaparecida…: se sigue con el resto
            }
        }
        return ordenar(salida);
    }

    /** Directorio de verdad: ni enlace simbólico ni unión (con NOFOLLOW_LINKS una unión sale como «other»). */
    static boolean esDirectorioReal(Path p) {
        try {
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return a.isDirectory() && !a.isSymbolicLink() && !a.isOther();
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private static boolean excluida(Path dir, List<Path> excluir) {
        if (excluir == null) return false;
        for (Path e : excluir) {
            if (e == null || e.toString().isEmpty()) continue;
            Path x = e.toAbsolutePath().normalize();
            if (dir.startsWith(x) || x.startsWith(dir)) return true;
        }
        return false;
    }

    private static List<Candidata> ordenar(List<Candidata> l) {
        l.sort(PREFERENCIA);
        return l;
    }

    /**
     * La carpeta de la que importar, a partir de la que eligió el usuario: la propia si ya tiene datos (aunque no
     * tenga exe al lado: una copia suelta de config/players vale), si no la candidata preferida de dentro. null si
     * no hay ninguna. {@code encontradas} recibe cuántas candidatas había (para enseñar la ruta exacta si hay varias).
     */
    public static Path resolverElegida(Path elegida, List<Path> excluir, long limiteNanos, int[] encontradas) {
        if (elegida == null) return null;
        Path e = elegida.toAbsolutePath().normalize();
        List<Candidata> c = buscar(List.of(e), PROFUNDIDAD, TOPE_CARPETAS, limiteNanos, excluir);
        boolean propia = tieneDatos(e) && !excluida(e, excluir);
        if (encontradas != null) encontradas[0] = c.size() + (propia && c.stream().noneMatch(x -> x.carpeta().equals(e)) ? 1 : 0);
        if (propia) return e;
        return c.isEmpty() ? null : c.get(0).carpeta();
    }
}
