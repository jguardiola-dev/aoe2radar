package dev.tirador.aoe2radar.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;

/**
 * Integración con Windows: ruta del propio exe, las carpetas de la app y el arranque con el sistema (registro Run).
 * <ul>
 * <li><b>Carpeta de la app</b> ({@link #carpetaApp()}): donde está instalada. Solo recursos de lectura que el
 *     empaquetado deja junto al exe (banderas). Nunca se escribe en ella: con un instalador es de solo lectura
 *     (Program Files, WindowsApps).</li>
 * <li><b>Carpeta de datos</b> ({@link #carpetaBase()}): lo que la app escribe (config.properties, players.txt,
 *     top_cache.txt, descargas.log, sfrdata/, techtree/, arranque_error.log). Empaquetada, %APPDATA%\aoe2radar,
 *     salvo en modo portátil (un archivo «portable» o «portable.txt» en la carpeta de la app), que la deja junto
 *     al exe como en la 1.3.</li>
 * <li><b>Carpeta de recs</b> ({@link #carpetaRecs()}): la clave «carpeta_recs» de config.properties si el usuario
 *     la fijó; si no, empaquetada, Documentos\aoe2radar\recs (la carpeta Documentos real, aunque OneDrive la
 *     redirija); portátil, recs/ junto al exe.</li>
 * </ul>
 * Fuera del paquete (mvn, tests, harness) todo es relativo al directorio de trabajo, byte a byte como antes:
 * {@code Path.of("")}, {@code Path.of("recs")}.
 */
public final class Sistema {
    private Sistema() {}

    /** Ruta del exe real cuando corremos empaquetados con jpackage; null si no. */
    public static String rutaExePropia() {
        String p = System.getProperty("jpackage.app-path");
        return p == null || p.isBlank() ? null : p;
    }

    // ---------------------------------------------------------------- ¿empaquetada? y carpeta de la app

    /** Propiedad de sistema que fija el lanzador de jpackage: la ruta del exe. */
    static final String PROP_JPACKAGE = "jpackage.app-path";
    /** Propiedad de sistema que fija el lanzador de Conveyor (Hydraulic): la carpeta de instalación. */
    static final String PROP_CONVEYOR = "app.dir";

    /**
     * LA señal de «app empaquetada», en un solo sitio: con jpackage, la carpeta del exe ({@code jpackage.app-path});
     * con Conveyor, la de instalación ({@code app.dir}); si no hay ninguna, null (mvn, tests, harness).
     */
    static Path carpetaInstalacion(String jpackageAppPath, String conveyorAppDir) {
        try {
            if (jpackageAppPath != null && !jpackageAppPath.isBlank()) return carpetaExe(jpackageAppPath);
            if (conveyorAppDir != null && !conveyorAppDir.isBlank()) return Path.of(conveyorAppDir).toAbsolutePath();
        } catch (RuntimeException ex) { }   // ruta inválida (InvalidPathException, IOError…): como sin paquete; nunca lanza
        return null;
    }

    private static Path carpetaInstalacion() {
        return carpetaInstalacion(System.getProperty(PROP_JPACKAGE), System.getProperty(PROP_CONVEYOR));
    }

    /** ¿Corremos como app instalada (jpackage o Conveyor)? Fuera del paquete, false. */
    public static boolean empaquetada() { return carpetaInstalacion() != null; }

    /** Carpeta de instalación cuando corremos empaquetados; si no, el directorio de trabajo ({@code Path.of("")}). */
    public static Path carpetaApp() {
        Path p = carpetaInstalacion();
        return p != null ? p : Path.of("");
    }

    /** Un recurso de la app (solo lectura) dentro de {@link #carpetaApp()}. */
    public static Path enCarpetaApp(String nombre) { return carpetaApp().resolve(nombre); }

    /** Pura: la carpeta del exe si hay ruta de exe; si no, {@code Path.of("")}. Arreglo F1 de la revisión 1.3: el
     *  autoarranque de Windows (clave Run) lanza el exe con otro directorio de trabajo (normalmente
     *  C:\Windows\System32), y por eso las carpetas se calculan desde la ruta del exe y no desde el directorio actual. */
    static Path carpetaExe(String rutaExe) {
        if (rutaExe == null || rutaExe.isBlank()) return Path.of("");
        Path padre = Path.of(rutaExe).toAbsolutePath().getParent();
        return padre != null ? padre : Path.of("");
    }

    // ---------------------------------------------------------------- carpeta de datos (escritura)

    /** Nombre de la carpeta de datos dentro de %APPDATA% (y de la de recs dentro de Documentos). */
    static final String CARPETA_DATOS = "aoe2radar";
    /** Cualquiera de estos archivos en la carpeta de la app activa el modo portátil (datos junto al exe). */
    static final List<String> MARCAS_PORTATIL = List.of("portable", "portable.txt");
    /** Si la carpeta de datos no tiene ninguno al arrancar, es un usuario nuevo en esta carpeta: se le ofrece importar. */
    static final List<String> DISPARADORES = List.of("config.properties", "players.txt");

    /**
     * Resultado de elegir la carpeta de datos en este arranque. {@code documentos}: la carpeta Documentos del usuario
     * (null fuera del paquete o en modo portátil: ahí las recs van a la carpeta de datos). {@code datosNuevos}: la
     * carpeta de datos no tenía config.properties ni players.txt al arrancar (se decide aquí, antes de que nadie
     * escriba config.properties). {@code aviso}: línea para el log, o null.
     */
    record Resolucion(Path carpeta, Path documentos, boolean datosNuevos, String aviso) { }

    /** Se calcula una sola vez, la primera vez que alguien pide una ruta de datos (las constantes CONFIG_FILE,
     *  LOG_FILE, PLAYERS_FILE…, desde Main.main en el hilo main, antes de abrir la ventana). Aquí NO se llama a Log
     *  ni a Config: sus constantes salen de esta misma clase y, si el primero en llegar es uno de ellos, su
     *  inicialización aún no ha terminado. El aviso lo escribe Log al inicializarse ({@link #avisoCarpetaDatos()}). */
    private static final class Datos {
        static final Resolucion R = resolver(System.getProperty(PROP_JPACKAGE), System.getProperty(PROP_CONVEYOR),
                System.getenv("APPDATA"), System.getProperty("user.home"), Sistema::documentosDelUsuario);
    }

    /** Carpeta de los datos de la app (ver el comentario de la clase). */
    public static Path carpetaBase() { return Datos.R.carpeta(); }

    /** Un archivo o carpeta de datos de la app, dentro de {@link #carpetaBase()}. */
    public static Path enCarpetaBase(String nombre) { return carpetaBase().resolve(nombre); }

    /** Lo que pasó al preparar la carpeta de datos en este arranque (fallo y respaldo…), o null. Lo escribe Log. */
    public static String avisoCarpetaDatos() { return Datos.R.aviso(); }

    /** ¿La carpeta de datos estaba sin config.properties ni players.txt al arrancar? Solo puede ser true empaquetada
     *  y fuera del modo portátil. La UI lo usa para ofrecer una vez la importación desde una 1.x en zip. */
    public static boolean datosNuevos() { return Datos.R.datosNuevos(); }

    /** Elige la carpeta de datos y la crea; solo toca el disco de las rutas que recibe. Nunca lanza (corre en un
     *  inicializador estático): si algo falla, vuelve a la carpeta de la app, como en la 1.3. */
    static Resolucion resolver(String jpackageAppPath, String conveyorAppDir, String appdata, String userHome,
                               Supplier<Path> documentos) {
        Path instalacion = null;
        try {
            instalacion = carpetaInstalacion(jpackageAppPath, conveyorAppDir);
            if (instalacion == null) return new Resolucion(Path.of(""), null, false, null);   // fuera del paquete: como siempre
            Path datos = carpetaDatos(instalacion, appdata, userHome);
            if (datos.equals(instalacion)) return new Resolucion(datos, null, false, null);   // portátil
            boolean nuevos = true;
            for (String n : DISPARADORES) if (Files.exists(datos.resolve(n))) nuevos = false;
            Files.createDirectories(datos);
            return new Resolucion(datos, documentos.get(), nuevos, null);
        } catch (Exception ex) {
            Path app = instalacion != null ? instalacion : Path.of("");
            return new Resolucion(app, null, false, "datos: no se pudo preparar la carpeta de datos (" + ex
                    + "); se usa la de la app: " + app.toAbsolutePath());
        }
    }

    /** Pura (solo mira si existen las marcas de portátil): con «portable» o «portable.txt» en la carpeta de la
     *  app, esa carpeta; si no, %APPDATA%\aoe2radar (o, sin APPDATA, user.home\AppData\Roaming\aoe2radar). Con
     *  Conveyor (MSIX), Windows redirige %APPDATA% a la copia privada del paquete sin que la app lo note. */
    static Path carpetaDatos(Path instalacion, String appdata, String userHome) {
        for (String m : MARCAS_PORTATIL) if (Files.exists(instalacion.resolve(m))) return instalacion;
        Path raiz = appdata != null && !appdata.isBlank() ? Path.of(appdata)
                : Path.of(userHome == null ? "" : userHome, "AppData", "Roaming");
        return raiz.resolve(CARPETA_DATOS);
    }

    // ---------------------------------------------------------------- carpeta de recs

    /** Clave de config.properties con una carpeta de recs fijada por el usuario (vacía o ausente: la de siempre). */
    public static final String CLAVE_CARPETA_RECS = "carpeta_recs";

    /** Holder aparte de Datos: este sí lee config.properties, y Config necesita Datos ya resuelto. */
    private static final class Recs {
        static final Path DIR = carpetaRecs(carpetaBase(), Datos.R.documentos(),
                Config.leerConfig(CLAVE_CARPETA_RECS, null));
    }

    /** Carpeta de las recs descargadas (ver el comentario de la clase). */
    public static Path carpetaRecs() { return Recs.DIR; }

    /** Pura: la carpeta fijada en config si la hay y es una ruta absoluta válida; si no, Documentos\aoe2radar\recs con
     *  Documentos conocida (empaquetada); si no, recs/ en la carpeta de datos (portátil, o {@code Path.of("recs")}
     *  fuera del paquete). */
    static Path carpetaRecs(Path datos, Path documentos, String configurada) {
        if (configurada != null && !configurada.isBlank()) {
            try {
                Path p = Path.of(configurada.strip());
                if (p.isAbsolute()) return p;   // relativa: dependería del directorio de trabajo; se ignora
            } catch (InvalidPathException ignored) { }
        }
        if (documentos != null) return documentos.resolve(CARPETA_DATOS).resolve("recs");
        return datos.resolve("recs");
    }

    /** Clave del registro con las carpetas del shell del usuario ya redirigidas (OneDrive, GPO…). */
    static final String CLAVE_SHELL = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders";

    /** La carpeta Documentos real del usuario: el valor «Personal» de User Shell Folders (que sigue a OneDrive si la
     *  redirige), si existe; si no, user.home\Documents. Una llamada a reg.exe (con la consola en UTF-8, para que
     *  un nombre de usuario con tilde no salga en la página OEM) desde el holder Datos, una vez por arranque. */
    static Path documentosDelUsuario() {
        String salida = ejecutarConLimite(5, "cmd", "/c", "chcp 65001 >nul & reg query \"" + CLAVE_SHELL + "\" /v Personal");
        return documentos(salida, System::getenv, System.getProperty("user.home"), Files::isDirectory);
    }

    /** Ejecuta y devuelve la salida (UTF-8), o null si falla o no acaba en el plazo. La salida se lee en otro hilo:
     *  si el proceso se cuelga, el plazo protege de verdad (se mata con destroyForcibly) y el arranque sigue. */
    static String ejecutarConLimite(long segundos, String... comando) {
        Process pr = null;
        try {
            pr = new ProcessBuilder(comando).redirectErrorStream(true).start();
            Process p = pr;
            CompletableFuture<byte[]> lectura = CompletableFuture.supplyAsync(() -> {
                try { return p.getInputStream().readAllBytes(); } catch (IOException ex) { return null; }
            });
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(segundos);   // un solo plazo para todo
            if (!pr.waitFor(segundos, TimeUnit.SECONDS)) { matar(pr); return null; }
            byte[] b = lectura.get(Math.max(0, limite - System.nanoTime()), TimeUnit.NANOSECONDS);
            return b == null ? null : new String(b, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            if (pr != null) matar(pr);
            return null;
        }
    }

    /** Mata el proceso y sus hijos (cmd lanza reg.exe: matando solo cmd, el hijo seguiría con la tubería abierta). */
    private static void matar(Process pr) {
        pr.descendants().forEach(ProcessHandle::destroyForcibly);
        pr.destroyForcibly();
    }

    /** Pura: Documentos a partir de la salida de «reg query … /v Personal», con las %VARIABLES% expandidas; si no
     *  hay valor, no se puede expandir o la carpeta no existe, user.home\Documents. */
    static Path documentos(String salidaReg, Function<String, String> entorno, String userHome, Predicate<Path> existe) {
        return carpetaShell(salidaReg, "Personal", "Documents", entorno, userHome, existe);
    }

    /** Pura: una carpeta del shell (valor «nombre» de User Shell Folders, expandido) si es absoluta y existe; si no,
     *  user.home\respaldo. */
    static Path carpetaShell(String salidaReg, String nombre, String respaldo, Function<String, String> entorno,
                             String userHome, Predicate<Path> existe) {
        String valor = valorShell(salidaReg, nombre, entorno);
        if (valor != null) {
            try {
                Path p = Path.of(valor);
                if (p.isAbsolute() && existe.test(p)) return p;
            } catch (InvalidPathException ignored) { }
        }
        return Path.of(userHome == null ? "" : userHome, respaldo);
    }

    /** Valor de User Shell Folders con la carpeta Descargas (no tiene nombre legible, solo este GUID). */
    static final String VALOR_DESCARGAS = "{374DE290-123F-4565-9164-39C4925E467B}";

    /** Dónde buscar una 1.x en zip al primer arranque: Escritorio (el real, que OneDrive puede mover), Descargas y
     *  Documentos, sin repetir y solo las que existen. Una llamada a reg.exe con plazo; nunca desde el EDT. */
    public static List<Path> carpetasDeBusqueda() {
        String salida = ejecutarConLimite(5, "cmd", "/c", "chcp 65001 >nul & reg query \"" + CLAVE_SHELL + "\"");
        return carpetasDeBusqueda(salida, System::getenv, System.getProperty("user.home"), Files::isDirectory);
    }

    /** Pura, para el test: las tres carpetas a partir de la salida de «reg query» de la clave entera. */
    static List<Path> carpetasDeBusqueda(String salidaReg, Function<String, String> entorno, String userHome,
                                         Predicate<Path> existe) {
        java.util.LinkedHashSet<Path> r = new java.util.LinkedHashSet<>();
        r.add(carpetaShell(salidaReg, "Desktop", "Desktop", entorno, userHome, existe));
        r.add(carpetaShell(salidaReg, VALOR_DESCARGAS, "Downloads", entorno, userHome, existe));
        r.add(carpetaShell(salidaReg, "Personal", "Documents", entorno, userHome, existe));
        return r.stream().filter(existe).toList();
    }

    private static final Pattern VARIABLE = Pattern.compile("%([^%]+)%");

    /** El valor de «Personal» con las variables expandidas, o null si no está o alguna variable no existe. */
    static String valorPersonal(String salidaReg, Function<String, String> entorno) {
        return valorShell(salidaReg, "Personal", entorno);
    }

    /** El valor «nombre» de la salida de reg query, con las variables expandidas; null si no está o alguna variable
     *  no existe. */
    static String valorShell(String salidaReg, String nombre, Function<String, String> entorno) {
        if (salidaReg == null) return null;
        Matcher m = Pattern.compile("^\\s*" + Pattern.quote(nombre) + "\\s+REG_(?:EXPAND_)?SZ\\s+(.+?)\\s*$",
                Pattern.MULTILINE).matcher(salidaReg);
        if (!m.find()) return null;
        Matcher v = VARIABLE.matcher(m.group(1));
        StringBuilder sb = new StringBuilder();
        while (v.find()) {
            String x = entorno.apply(v.group(1));
            if (x == null) return null;
            v.appendReplacement(sb, Matcher.quoteReplacement(x));
        }
        v.appendTail(sb);
        return sb.toString();
    }

    // ---------------------------------------------------------------- relanzar (tras importar datos)

    /** Pura: ¿es este ejecutable el lanzador de la app (un .exe que no es java/javaw)? */
    static boolean esLanzadorRelanzable(String comando) {
        if (comando == null || comando.isBlank()) return false;
        String n = Path.of(comando).getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".exe") && !n.equals("java.exe") && !n.equals("javaw.exe");
    }

    /** Vuelve a abrir la app (el mismo exe de este proceso, empaquetada). Devuelve si lo lanzó; no cierra esta. */
    public static boolean relanzar() {
        if (!empaquetada()) return false;
        String cmd = ProcessHandle.current().info().command().orElse(null);
        if (!esLanzadorRelanzable(cmd)) return false;
        try {
            new ProcessBuilder(cmd).start();
            return true;
        } catch (Exception ex) { return false; }
    }

    // ---------------------------------------------------------------- autoarranque

    static final String CLAVE_RUN = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    /** Escribe o borra la entrada de autoarranque en el registro del usuario
     *  con reg.exe (sin permisos de administrador). Devuelve si fue bien. Apunta al exe (rutaExePropia), nunca a
     *  la carpeta de datos. */
    public static boolean fijarAutoArranque(boolean activar) {
        String exe = rutaExePropia();
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return false;
        if (activar && exe == null) return false;
        try {
            ProcessBuilder pb = activar
                    ? new ProcessBuilder("reg", "add", CLAVE_RUN, "/v", NOMBRE,
                            "/t", "REG_SZ", "/d", "\"" + exe + "\"", "/f")
                    : new ProcessBuilder("reg", "delete", CLAVE_RUN, "/v", NOMBRE, "/f");
            Process pr = pb.redirectErrorStream(true).start();
            pr.getInputStream().readAllBytes();
            return pr.waitFor() == 0 || !activar;   // borrar lo inexistente también vale
        } catch (Exception e) { return false; }
    }
}
