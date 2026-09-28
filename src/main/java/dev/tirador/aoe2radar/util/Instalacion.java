package dev.tirador.aoe2radar.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.function.LongPredicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;
import static dev.tirador.aoe2radar.util.Texto.versionMayor;

/**
 * El lado de disco del actualizador propio (1.4): sin red y sin Swing. Tres momentos:
 * <ol>
 * <li><b>Al cerrar</b> ({@link #aplicar}): si hay un jar descargado y verificado ({@link Lista}), se copia a
 *     {@code app/} con su nombre versionado y se reescribe {@code app/aoe2radar.cfg} para que el lanzador arranque
 *     con él la próxima vez. El jar en uso NO se toca (Windows lo tiene bloqueado). Antes de cambiar el .cfg se deja
 *     en la carpeta de datos una copia del .cfg de antes y una <i>marca</i> («se aplicó la X, venía de la Y»).</li>
 * <li><b>Al arrancar</b> ({@link #alArrancar}, lo primero de {@code app.Main}): si hay marca y el jar en uso es el
 *     nuevo, es un arranque tras actualizar y se apunta (con el pid del proceso). Si ya hay {@link #MAX_ARRANQUES}
 *     arranques anteriores del jar nuevo que murieron sin llegar a abrir la ventana, se vuelve al .cfg anterior
 *     ({@link #revertir}).</li>
 * <li><b>Con la ventana abierta</b> ({@link #confirmar}): la actualización ha ido bien: se borra la marca y se
 *     barren los jars viejos de {@code app/} (ya no están bloqueados) y lo descargado que ya no hace falta.</li>
 * </ol>
 * Si {@code Main} captura un fallo de arranque con la marca puesta, llama a {@link #revertir} directamente.
 * <p>Dos procesos de la app a la vez (dos ventanas, o una que se cierra mientras otra arranca) comparten estos
 * archivos: todo lo que los cambia va con un cerrojo entre procesos ({@code actualizacion/.cerrojo}, más un monitor
 * dentro de este proceso). Lo que no coge el cerrojo en unos segundos no se hace (se reintenta en el siguiente
 * cierre o arranque).
 * <p>Límites (documentados en README_TECNICO): el código que cuenta arranques y revierte es el del jar NUEVO. Si ese
 * jar no llega a ejecutar {@code Main} (el lanzador no puede cargarlo, la JVM cae antes), nada revierte solo; por eso
 * el jar se verifica entero (tamaño, SHA-256, zip, clases) antes de ponerlo en {@code app/}, y solo se aplica si el
 * runtime y el resto del .cfg son los mismos para los que se publicó. Un cuelgue sin excepción no se detecta en ese
 * arranque: lo recoge el recuento en el siguiente.
 */
public final class Instalacion {
    private Instalacion() {}

    /** Carpeta de trabajo del actualizador, dentro de la de datos. */
    public static final String CARPETA = "actualizacion";
    /** Descargado y verificado, pendiente de aplicar al cerrar. */
    static final String LISTA = "lista.properties";
    /** Aplicada, pendiente de un arranque bueno. */
    static final String MARCA = "aplicada.properties";
    /** El .cfg de antes de aplicar, tal cual, para volver atrás. */
    static final String CFG_ANTERIOR = "cfg.anterior";
    /** La versión que no arrancó: no se vuelve a aplicar como jar (se ofrece el instalador). */
    static final String FALLIDA = "fallida.properties";
    /** Cerrojo entre procesos de la app. */
    static final String CERROJO = ".cerrojo";
    /** Sufijo de las descargas a medias (service.ActualizadorService). */
    public static final String SUFIJO_DESCARGA = ".descargando";
    /** Arranques del jar nuevo que murieron sin llegar a abrir la ventana que se toleran; con uno más, se revierte. */
    static final int MAX_ARRANQUES = 2;
    /** Un jar de app/ más reciente que esto no se barre: puede ser el que otra instancia está colocando ahora. */
    static final Duration EDAD_BARRIDO = Duration.ofMinutes(10);
    /** Lo que se espera como mucho al cerrojo antes de dejarlo para otra vez. */
    static final long ESPERA_CERROJO_MS = 5_000;

    /** Versión publicable: hasta cuatro tramos numéricos («1.5», «1.5.1»). Sin sufijos: «1.5-rc1» ordenaría mal. */
    public static final Pattern VERSION = Pattern.compile("\\d{1,4}(\\.\\d{1,4}){0,3}");

    /** El nombre del jar de una versión: siempre «aoe2radar-X.Y.jar». */
    public static String nombreJar(String version) { return "aoe2radar-" + version + ".jar"; }

    /** Dónde está todo. app: la carpeta {@code app/} del paquete (jars y .cfg). cfg: el .cfg del lanzador. dir: la
     *  carpeta de trabajo del actualizador en los datos. jarEnUso: el nombre del jar con el que corre este proceso. */
    public record Rutas(Path app, Path cfg, Path dir, String jarEnUso) { }

    /** Lo descargado y verificado, y para qué paquete se publicó: el runtime (java.version), el resto del classpath,
     *  las opciones de Java y la clase principal. Al aplicar se comprueba que la instalación sigue siendo esa. */
    public record Lista(String version, String jar, String sha256, long size, String runtime, List<String> classpath,
                       List<String> opciones, String mainclass) { }

    // ------------------------------------------------------------------ rutas y detección

    /** Las rutas de este proceso: null fuera del paquete o si el jar en uso no está en {@code app/} (desarrollo,
     *  tests, harness). No escribe nada. Nunca lanza. */
    public static Rutas actuales() {
        try {
            if (!Sistema.empaquetada()) return null;
            Path app = Sistema.carpetaApp().resolve("app").toAbsolutePath().normalize();
            String exe = Sistema.rutaExePropia();
            String base = exe == null ? Identidad.NOMBRE : Path.of(exe).getFileName().toString().replaceFirst("(?i)\\.exe$", "");
            Path jar = Path.of(Instalacion.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(jar) || jar.getParent() == null || !Files.isSameFile(jar.getParent(), app)) return null;
            return new Rutas(app, app.resolve(base + ".cfg"), Sistema.enCarpetaBase(CARPETA), jar.getFileName().toString());
        } catch (Exception | Error ex) {
            return null;
        }
    }

    /** ¿Puede actualizarse sola? El .cfg tiene el formato esperado, arranca el jar en uso y la carpeta app/ admite
     *  escritura (se prueba creando y borrando un archivo: Files.isWritable no es fiable en Windows). Toca el disco:
     *  nunca en el EDT. Nunca lanza. */
    public static boolean activa(Rutas r) {
        if (r == null) return false;
        CfgLanzador.Datos d = CfgLanzador.leer(r.cfg());
        if (d == null || !d.jarApp().equalsIgnoreCase(r.jarEnUso())) return false;
        return escribible(r.app());
    }

    static boolean escribible(Path dir) {
        try {
            Path p = Files.createTempFile(dir, "escritura", ".tmp");
            Files.delete(p);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** La versión de un nombre de jar de la app («aoe2radar-1.3.0.jar» → «1.3.0»), o null. */
    static String versionDeJar(String nombre) {
        if (nombre == null || !CfgLanzador.JAR_APP.matcher(nombre).matches()) return null;
        String v = nombre.substring("aoe2radar-".length(), nombre.length() - ".jar".length());
        return v.matches("\\d+(\\.\\d+)*.*") ? v : null;
    }

    // ------------------------------------------------------------------ cerrojo entre procesos

    private static final Object MONITOR = new Object();

    /** Ejecuta el trabajo con el cerrojo entre procesos de dir; si no se consigue en {@link #ESPERA_CERROJO_MS}, o
     *  falla, devuelve siNo. El monitor evita que dos hilos de este proceso pidan el mismo FileLock (lanzaría). */
    static <T> T conCerrojo(Path dir, T siNo, Callable<T> trabajo) {
        synchronized (MONITOR) {
            try {
                Files.createDirectories(dir);
                try (FileChannel ch = FileChannel.open(dir.resolve(CERROJO), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                    long fin = System.currentTimeMillis() + ESPERA_CERROJO_MS;
                    FileLock l;
                    while ((l = ch.tryLock()) == null) {
                        if (System.currentTimeMillis() > fin) { log("actualizar: otra ventana de la app está actualizando; se deja para otra vez"); return siNo; }
                        Thread.sleep(50);
                    }
                    try { return trabajo.call(); } finally { l.release(); }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return siNo;
            } catch (Exception ex) {
                log("actualizar: " + causa(ex));
                return siNo;
            }
        }
    }

    // ------------------------------------------------------------------ lista (descargado, pendiente)

    static boolean guardarLista(Path dir, Lista l) {
        Properties p = new Properties();
        p.setProperty("version", l.version());
        p.setProperty("jar", l.jar());
        p.setProperty("sha256", l.sha256());
        p.setProperty("size", String.valueOf(l.size()));
        p.setProperty("runtime", l.runtime());
        p.setProperty("mainclass", l.mainclass());
        ponerLista(p, "classpath", l.classpath());
        ponerLista(p, "opciones", l.opciones());
        return escribirProps(dir.resolve(LISTA), p);
    }

    private static void ponerLista(Properties p, String clave, List<String> valores) {
        p.setProperty(clave + ".n", String.valueOf(valores.size()));
        for (int i = 0; i < valores.size(); i++) p.setProperty(clave + "." + i, valores.get(i));
    }

    private static List<String> leerLista(Properties p, String clave) {
        int n = Integer.parseInt(p.getProperty(clave + ".n", "x"));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String v = p.getProperty(clave + "." + i);
            if (v == null) throw new NumberFormatException(clave);
            out.add(v);
        }
        return List.copyOf(out);
    }

    /** Lo pendiente de aplicar, o null si no hay o está mal (versión o jar raros, sin sha, sin los datos del paquete…). */
    public static Lista leerLista(Path dir) {
        Properties p = leerProps(dir.resolve(LISTA));
        if (p == null) return null;
        try {
            String v = p.getProperty("version", ""), jar = p.getProperty("jar"), sha = p.getProperty("sha256");
            String runtime = p.getProperty("runtime"), mainclass = p.getProperty("mainclass");
            long size = Long.parseLong(p.getProperty("size", "-1"));
            if (!VERSION.matcher(v).matches() || !nombreJar(v).equals(jar) || !VerificacionJar.shaValido(sha) || size <= 0
                    || runtime == null || mainclass == null) return null;
            return new Lista(v, jar, sha, size, runtime, leerLista(p, "classpath"), leerLista(p, "opciones"), mainclass);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Olvida lo pendiente: la lista y el jar descargado que nombra. */
    public static void borrarLista(Path dir) {
        Lista l = leerLista(dir);
        borrar(dir.resolve(LISTA));
        if (l != null) borrar(dir.resolve(l.jar()));
    }

    /** El final de una descarga ya comprobada (service.ActualizadorService): con el cerrojo, fuerza el temporal a
     *  disco, lo mueve con ATOMIC_MOVE a {@code dir/<jar>} y lo apunta como lista (olvidando la anterior). Si no se
     *  puede apuntar, borra el jar: nunca queda uno con el nombre bueno sin lista, ni una lista sin su jar. */
    public static boolean registrarDescarga(Path dir, Path temporal, Lista l) {
        return conCerrojo(dir, false, () -> {
            try (FileChannel ch = FileChannel.open(temporal, StandardOpenOption.WRITE)) { ch.force(true); }
            Lista vieja = leerLista(dir);
            Path destino = dir.resolve(l.jar());
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            if (!guardarLista(dir, l)) { borrar(destino); return false; }
            if (vieja != null && !vieja.jar().equalsIgnoreCase(l.jar())) borrar(dir.resolve(vieja.jar()));
            return true;
        });
    }

    // ------------------------------------------------------------------ aplicar (al cerrar)

    public enum Aplicacion { NADA, APLICADA, YA_ESTABA, FALLO }

    /** Paquete, solo para los tests: corre justo antes de escribir el .cfg (para simular que otro lo cambia entonces). */
    static volatile Runnable antesDeEscribirCfg = () -> { };

    /** Al cerrar: aplica lo pendiente si lo hay (ver el comentario de la clase). Si algo falla, el .cfg queda como
     *  estaba y la app sigue en su versión; queda en el log. Idempotente: con el .cfg ya apuntando al jar nuevo (otra
     *  instancia lo aplicó), no hace nada. Nunca a una versión igual o más vieja que la del .cfg, ni a la fallida, ni
     *  si el runtime o el resto del .cfg ya no son aquellos para los que se publicó (un instalador lo cambió): eso
     *  se olvida y se vuelve a decidir en la siguiente comprobación. runtimeEnUso: la java.version de este proceso.
     *  Nunca lanza. */
    public static Aplicacion aplicar(Rutas r, String versionEnUso, String runtimeEnUso) {
        if (r == null) return Aplicacion.NADA;
        return conCerrojo(r.dir(), Aplicacion.FALLO, () -> {
            try {
                return aplicarYa(r, versionEnUso, runtimeEnUso);
            } catch (Exception ex) {
                log("actualizar: fallo al aplicar (" + causa(ex) + "); la app sigue en " + versionEnUso);
                return Aplicacion.FALLO;
            }
        });
    }

    private static Aplicacion aplicarYa(Rutas r, String versionEnUso, String runtimeEnUso) throws IOException {
        Lista l = leerLista(r.dir());
        if (l == null) return Aplicacion.NADA;
        if (!versionMayor(l.version(), versionEnUso)) return Aplicacion.NADA;
        if (l.version().equals(versionFallida(r.dir()))) return Aplicacion.NADA;
        byte[] cfgAntes = Files.readAllBytes(r.cfg());
        String textoAntes = new String(cfgAntes, StandardCharsets.ISO_8859_1);
        CfgLanzador.Datos d = CfgLanzador.leer(textoAntes);
        if (d == null) { log("actualizar: " + r.cfg().getFileName() + " sin el formato esperado; no se aplica " + l.version()); return Aplicacion.FALLO; }
        if (d.jarApp().equalsIgnoreCase(l.jar())) return Aplicacion.YA_ESTABA;
        String vCfg = versionDeJar(d.jarApp());
        if (!versionMayor(l.version(), vCfg != null ? vCfg : versionEnUso)) return Aplicacion.NADA;   // otra instancia ya puso algo igual o más nuevo
        if (!l.runtime().equals(runtimeEnUso) || !l.classpath().equals(d.resto()) || !l.opciones().equals(d.opciones())
                || !l.mainclass().equals(d.mainclass())) {
            log("actualizar: la " + l.version() + " descargada era para otro paquete (runtime o .cfg distintos); se olvida");
            borrarLista(r.dir());
            return Aplicacion.NADA;
        }
        if (l.jar().equalsIgnoreCase(r.jarEnUso())) { log("actualizar: el jar nuevo se llama como el que está en uso; no se aplica"); return Aplicacion.FALLO; }
        Path descargado = r.dir().resolve(l.jar());
        VerificacionJar.Resultado ver = VerificacionJar.verificar(descargado, l.size(), l.sha256(), d.mainclass());
        if (!ver.ok()) {
            log("actualizar: el jar descargado de " + l.version() + " no vale (" + ver.motivo() + "); se descarta");
            borrarLista(r.dir());
            return Aplicacion.FALLO;
        }
        Path destino = r.app().resolve(l.jar());
        if (!colocarJar(descargado, destino, l, d.mainclass())) return Aplicacion.FALLO;
        String textoNuevo = CfgLanzador.reescribir(textoAntes, l.jar(), l.version());
        if (textoNuevo == null) { log("actualizar: no se pudo reescribir el .cfg para " + l.jar()); return Aplicacion.FALLO; }
        Properties marca = new Properties();
        marca.setProperty("anterior", d.jarApp());
        marca.setProperty("nueva", l.jar());
        marca.setProperty("version", l.version());
        marca.setProperty("version_anterior", versionEnUso);
        marca.setProperty("pids", "");
        if (!CfgLanzador.escribirAtomico(r.dir().resolve(CFG_ANTERIOR), cfgAntes)
                || !escribirProps(r.dir().resolve(MARCA), marca)) {
            log("actualizar: no se pudo dejar la copia del .cfg o la marca; no se aplica " + l.version());
            borrar(r.dir().resolve(MARCA));
            return Aplicacion.FALLO;
        }
        antesDeEscribirCfg.run();
        // Justo antes de escribir, el .cfg tiene que seguir siendo el que se leyó (el instalador u otra instancia no
        // lo han cambiado mientras se copiaba el jar): si no, no se pisa.
        if (!Arrays.equals(cfgAntes, Files.readAllBytes(r.cfg()))) {
            borrar(r.dir().resolve(MARCA));
            log("actualizar: el .cfg cambió mientras se aplicaba la " + l.version() + "; no se toca");
            return Aplicacion.FALLO;
        }
        if (!CfgLanzador.escribirAtomico(r.cfg(), textoNuevo.getBytes(StandardCharsets.ISO_8859_1))) {
            borrar(r.dir().resolve(MARCA));
            return Aplicacion.FALLO;
        }
        if (!Files.isRegularFile(destino)) {   // alguien lo borró entre medias: se vuelve al .cfg de antes
            CfgLanzador.escribirAtomico(r.cfg(), cfgAntes);
            borrar(r.dir().resolve(MARCA));
            log("actualizar: " + l.jar() + " desapareció al aplicar; .cfg restaurado");
            return Aplicacion.FALLO;
        }
        log("actualizar: aplicada la " + l.version() + " (" + d.jarApp() + " -> " + l.jar() + "); se usará al volver a abrir");
        return Aplicacion.APLICADA;
    }

    /** Pone el jar en app/ con su nombre: si ya está y es el bueno (otra instancia lo puso), no se toca; si no, copia a
     *  un temporal, le pone la fecha de ahora (Files.copy en Windows conserva la de la descarga, y el barrido lo
     *  tomaría por viejo), lo fuerza a disco, lo comprueba y lo mueve con ATOMIC_MOVE. Nunca queda a medias con el
     *  nombre bueno. */
    private static boolean colocarJar(Path descargado, Path destino, Lista l, String mainclass) throws IOException {
        if (Files.exists(destino) && VerificacionJar.verificar(destino, l.size(), l.sha256(), mainclass).ok()) {
            Files.setLastModifiedTime(destino, FileTime.fromMillis(System.currentTimeMillis()));
            return true;
        }
        Path tmp = Files.createTempFile(destino.getParent(), l.jar(), ".parcial");
        try {
            Files.copy(descargado, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(tmp, FileTime.fromMillis(System.currentTimeMillis()));
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE)) { ch.force(true); }
            VerificacionJar.Resultado v = VerificacionJar.verificar(tmp, l.size(), l.sha256(), mainclass);
            if (!v.ok()) { log("actualizar: la copia en app/ no vale (" + v.motivo() + ")"); return false; }
            CfgLanzador.MOVER_ATOMICO.mover(tmp, destino);
            return true;
        } catch (IOException ex) {
            log("actualizar: no se pudo copiar " + l.jar() + " a app/ (" + causa(ex) + ")");
            return false;
        } finally {
            borrar(tmp);
        }
    }

    // ------------------------------------------------------------------ arranque, recuperación y confirmación

    public enum Arranque { NORMAL, TRAS_ACTUALIZAR, REVERTIDO }

    /** Lo primero del arranque, con el pid de este proceso. */
    public static Arranque alArrancar(Rutas r) {
        return alArrancar(r, ProcessHandle.current().pid(), pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }

    /** NORMAL: no hay actualización recién aplicada (una marca de otro jar se borra: el instalador u otra instancia
     *  cambiaron las cosas). TRAS_ACTUALIZAR: es el jar nuevo y aún no se ha confirmado; se apunta este pid.
     *  REVERTIDO: ya hay {@link #MAX_ARRANQUES} arranques anteriores del jar nuevo cuyo proceso murió sin confirmar (los
     *  que siguen vivos no cuentan: un doble clic no es un fallo) y se ha vuelto al .cfg anterior; quien llama relanza
     *  la app para que arranque la versión de antes. Nunca lanza. */
    static Arranque alArrancar(Rutas r, long pid, LongPredicate vivo) {
        if (r == null) return Arranque.NORMAL;
        return conCerrojo(r.dir(), Arranque.NORMAL, () -> {
            Properties m = leerProps(r.dir().resolve(MARCA));
            if (m == null) return Arranque.NORMAL;
            if (!r.jarEnUso().equalsIgnoreCase(m.getProperty("nueva", ""))) {
                log("actualizar: marca de " + m.getProperty("nueva") + " pero corre " + r.jarEnUso() + "; se descarta");
                borrar(r.dir().resolve(MARCA));
                borrar(r.dir().resolve(CFG_ANTERIOR));
                return Arranque.NORMAL;
            }
            List<Long> pids = new ArrayList<>();
            int muertos = 0;
            for (String s : m.getProperty("pids", "").split(",")) {
                if (s.isBlank()) continue;
                try {
                    long p = Long.parseLong(s.strip());
                    pids.add(p);
                    if (!vivo.test(p)) muertos++;
                } catch (NumberFormatException ex) { muertos++; }
            }
            if (muertos >= MAX_ARRANQUES) {
                if (revertirYa(r, muertos + " arranques sin llegar a abrir la ventana")) return Arranque.REVERTIDO;
                log("actualizar: no se puede volver atrás; se sigue con la " + m.getProperty("version") + " sin vigilarla más");
                borrar(r.dir().resolve(MARCA));
                return Arranque.NORMAL;
            }
            pids.add(pid);
            StringBuilder sb = new StringBuilder();
            for (Long p : pids) sb.append(sb.length() > 0 ? "," : "").append(p);
            m.setProperty("pids", sb.toString());
            if (!escribirProps(r.dir().resolve(MARCA), m)) log("actualizar: no se pudo apuntar este arranque en la marca");
            log("actualizar: arranque de la " + m.getProperty("version") + " recién aplicada (" + pids.size() + "º, "
                    + muertos + " anteriores sin ventana)");
            return Arranque.TRAS_ACTUALIZAR;
        });
    }

    /** Vuelve al .cfg de antes de la última actualización: solo si hay marca del jar en uso, el jar anterior sigue en
     *  app/, la copia del .cfg lo nombra y el .cfg actual sigue apuntando al nuevo (si alguien lo cambió, no se toca).
     *  Apunta la versión como fallida (no se vuelve a aplicar como jar) y olvida lo descargado. Nunca lanza. */
    public static boolean revertir(Rutas r, String motivo) {
        if (r == null) return false;
        return conCerrojo(r.dir(), false, () -> revertirYa(r, motivo));
    }

    private static boolean revertirYa(Rutas r, String motivo) {
        try {
            Properties m = leerProps(r.dir().resolve(MARCA));
            if (m == null || !r.jarEnUso().equalsIgnoreCase(m.getProperty("nueva", ""))) return false;
            String anterior = m.getProperty("anterior", "");
            if (!CfgLanzador.JAR_APP.matcher(anterior).matches() || !Files.isRegularFile(r.app().resolve(anterior))) {
                log("actualizar: no se puede volver atrás: falta " + anterior + " en app/");
                return false;
            }
            Path copiaP = r.dir().resolve(CFG_ANTERIOR);
            if (!Files.isRegularFile(copiaP)) { log("actualizar: no se puede volver atrás: falta la copia del .cfg"); return false; }
            byte[] copia = Files.readAllBytes(copiaP);
            CfgLanzador.Datos dCopia = CfgLanzador.leer(new String(copia, StandardCharsets.ISO_8859_1));
            CfgLanzador.Datos dAhora = CfgLanzador.leer(r.cfg());
            if (dCopia == null || !dCopia.jarApp().equalsIgnoreCase(anterior)
                    || dAhora == null || !dAhora.jarApp().equalsIgnoreCase(r.jarEnUso())) {
                log("actualizar: no se puede volver atrás: la copia del .cfg o el .cfg actual no son los esperados");
                return false;
            }
            if (!CfgLanzador.escribirAtomico(r.cfg(), copia)) return false;
            Properties f = new Properties();
            f.setProperty("version", m.getProperty("version", ""));
            f.setProperty("version_anterior", m.getProperty("version_anterior", ""));
            f.setProperty("motivo", motivo == null ? "" : motivo);
            f.setProperty("avisado", "false");
            escribirProps(r.dir().resolve(FALLIDA), f);
            borrar(r.dir().resolve(MARCA));
            borrar(copiaP);
            borrarLista(r.dir());
            log("actualizar: la " + m.getProperty("version") + " no arrancó (" + motivo + "); vuelta a " + anterior);
            return true;
        } catch (Exception ex) {
            log("actualizar: no se pudo volver atrás: " + causa(ex));
            return false;
        }
    }

    /** Con la ventana ya abierta (en un hilo de fondo): la actualización ha ido bien (se borran la marca y la copia del
     *  .cfg), se barren los jars de la app que ya no usa nadie y lo descargado que ya no hace falta. Nunca lanza. */
    public static void confirmar(Rutas r, String versionEnUso) {
        if (r == null) return;
        conCerrojo(r.dir(), null, () -> {
            Properties m = leerProps(r.dir().resolve(MARCA));
            if (m != null && r.jarEnUso().equalsIgnoreCase(m.getProperty("nueva", ""))) {
                borrar(r.dir().resolve(MARCA));
                borrar(r.dir().resolve(CFG_ANTERIOR));
                log("actualizar: la " + m.getProperty("version") + " arrancó bien");
            }
            barrerJarsViejos(r);
            Lista l = leerLista(r.dir());
            if (l != null && !versionMayor(l.version(), versionEnUso)) borrarLista(r.dir());
            Properties f = leerProps(r.dir().resolve(FALLIDA));
            if (f != null && !versionMayor(f.getProperty("version", "0"), versionEnUso)) borrar(r.dir().resolve(FALLIDA));   // ya estás en esa o en otra más nueva
            Archivos.limpiarTemporales(r.dir(), "", SUFIJO_DESCARGA, Duration.ofDays(1));
            Archivos.limpiarTemporales(r.dir(), "", ".tmp", Duration.ofDays(1));
            Archivos.limpiarTemporales(r.app(), "", ".parcial", Duration.ofDays(1));
            Archivos.limpiarTemporales(r.app(), r.cfg().getFileName().toString(), ".tmp", Duration.ofDays(1));
            return null;
        });
    }

    /** Borra de app/ los jars de la app (aoe2radar-*.jar) que no son el que corre, ni el que nombra el .cfg, ni los de
     *  una marca pendiente, si tienen más de {@link #EDAD_BARRIDO}. Si el .cfg no se puede leer, no borra nada. Uno
     *  bloqueado (otra instancia aún abierta con él) se queda para el siguiente arranque. Devuelve los borrados. Se
     *  llama con el cerrojo tomado. */
    static List<String> barrerJarsViejos(Rutas r) {
        List<String> borrados = new ArrayList<>();
        CfgLanzador.Datos d = CfgLanzador.leer(r.cfg());
        if (d == null) return borrados;
        Properties m = leerProps(r.dir().resolve(MARCA));
        String nuevaMarca = m == null ? "" : m.getProperty("nueva", ""), anteriorMarca = m == null ? "" : m.getProperty("anterior", "");
        long limite = System.currentTimeMillis() - EDAD_BARRIDO.toMillis();
        try (Stream<Path> s = Files.list(r.app())) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                if (!CfgLanzador.JAR_APP.matcher(n).matches() || n.equalsIgnoreCase(r.jarEnUso()) || n.equalsIgnoreCase(d.jarApp())
                        || n.equalsIgnoreCase(nuevaMarca) || n.equalsIgnoreCase(anteriorMarca)) continue;
                try {
                    if (Files.getLastModifiedTime(p).toMillis() >= limite) continue;
                    Files.delete(p);
                    borrados.add(n);
                    log("actualizar: borrado el jar viejo " + n);
                } catch (IOException ex) {
                    log("actualizar: " + n + " no se pudo borrar aún (" + causa(ex) + "); en el próximo arranque");
                }
            }
        } catch (Exception ex) {
            log("actualizar: no se pudo revisar app/: " + causa(ex));
        }
        return borrados;
    }

    // ------------------------------------------------------------------ versión fallida

    /** La versión que se aplicó y no arrancó, o null. */
    public static String versionFallida(Path dir) {
        Properties f = leerProps(dir.resolve(FALLIDA));
        String v = f == null ? null : f.getProperty("version");
        return v == null || v.isBlank() ? null : v.strip();
    }

    /** {versión fallida, versión a la que se volvió} si aún no se ha avisado al usuario; si no, null. */
    public static String[] fallidaSinAvisar(Path dir) {
        Properties f = leerProps(dir.resolve(FALLIDA));
        if (f == null || Boolean.parseBoolean(f.getProperty("avisado", "false"))) return null;
        String v = f.getProperty("version", "");
        return v.isBlank() ? null : new String[]{ v, f.getProperty("version_anterior", "") };
    }

    public static void marcarFallidaAvisada(Path dir) {
        Properties f = leerProps(dir.resolve(FALLIDA));
        if (f == null) return;
        f.setProperty("avisado", "true");
        escribirProps(dir.resolve(FALLIDA), f);
    }

    // ------------------------------------------------------------------ disco

    static Properties leerProps(Path p) {
        if (!Files.isRegularFile(p)) return null;
        try (InputStream in = Files.newInputStream(p)) {
            Properties pr = new Properties();
            pr.load(in);
            return pr;
        } catch (Exception ex) {
            return null;
        }
    }

    static boolean escribirProps(Path p, Properties pr) {
        try {
            Files.createDirectories(p.toAbsolutePath().getParent());
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            pr.store(b, Identidad.NOMBRE + " actualizador");
            return CfgLanzador.escribirAtomico(p, b.toByteArray());
        } catch (Exception ex) {
            log("actualizar: no se pudo guardar " + p.getFileName() + ": " + causa(ex));
            return false;
        }
    }

    private static void borrar(Path p) {
        try { Files.deleteIfExists(p); } catch (IOException ex) { log("actualizar: no se pudo borrar " + p.getFileName() + ": " + causa(ex)); }
    }
}
