package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.util.CfgLanzador;
import dev.tirador.aoe2radar.util.Instalacion;
import dev.tirador.aoe2radar.util.VerificacionJar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;
import static dev.tirador.aoe2radar.util.Identidad.REPO_URL;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;
import static dev.tirador.aoe2radar.util.Texto.versionMayor;

/**
 * El actualizador propio (1.4): lee {@code update.json} de la última release de GitHub (por la CDN de descargas, sin
 * el límite de la API), decide si basta con cambiar el jar o hace falta el instalador, y descarga el jar a la carpeta
 * de datos ({@code actualizacion/}) con todas las comprobaciones; la parte de disco que lo pone en uso vive en
 * {@link Instalacion} (sin red). Red y disco: nunca en el EDT (se avisa en el log si alguien lo hace).
 * <p>update.json (lo genera release.yml):
 * <pre>{ "version": "1.5", "jar": "aoe2radar-1.5.jar", "sha256": "…", "size": 1234567,
 *   "runtime": "21.0.9", "instalador": "aoe2radar-1.5-setup.exe",
 *   "classpath": ["flatlaf-3.7.2.jar"], "opciones": ["-Dfile.encoding=UTF-8"],
 *   "mainclass": "dev.tirador.aoe2radar.SpoilerFreeRecs" }</pre>
 * version (solo cifras y puntos), jar (exactamente «aoe2radar-&lt;version&gt;.jar»), sha256 y size son obligatorios
 * (sin ellos, el archivo no vale). runtime, classpath, opciones y
 * mainclass describen el resto del paquete: si alguno falta o no coincide con esta instalación (su java.version y su
 * .cfg), la actualización es «completa» (instalador). Así una versión con otro runtime u otra dependencia nunca se
 * pone cambiando solo el jar.
 */
public final class ActualizadorService implements Actualizador {

    /** update.json de la última release (redirige a la CDN de GitHub; el cliente sigue redirecciones). */
    public static final String URL_UPDATE = REPO_URL + "/releases/latest/download/update.json";

    /** El jar de una versión concreta: por su tag, no por «latest» (entre leer update.json y bajar el jar podría
     *  publicarse otra). */
    static String urlJar(Info i) { return REPO_URL + "/releases/download/v" + i.version() + "/" + i.jar(); }

    /** Descarga binaria: abre el cuerpo de una respuesta 200 o lanza (con estado distinto, IOException). En producción
     *  (app.Servicios) con timeouts de conexión y de lectura; en los tests, bytes en memoria. */
    @FunctionalInterface
    public interface Descarga { InputStream abrir(String url) throws IOException, InterruptedException; }

    /** Lo que dice update.json. Los opcionales que falten quedan null. */
    public record Info(String version, String jar, String sha256, long size, String runtime, String instalador,
                       List<String> classpath, List<String> opciones, String mainclass) { }

    enum Tipo { JAR, COMPLETA }

    private final Transporte texto;
    private final Descarga binaria;
    private final Supplier<Instalacion.Rutas> rutas;
    private final String versionActual, runtimeActual;
    private final AtomicBoolean descargando = new AtomicBoolean();
    private volatile Info ultima;
    private volatile Boolean activo;

    /** rutas: se piden en cada uso (Instalacion::actuales en producción). versionActual: Identidad.VERSION.
     *  runtimeActual: la java.version del runtime con el que corre la app. Sin E/S en el constructor. */
    public ActualizadorService(Transporte texto, Descarga binaria, Supplier<Instalacion.Rutas> rutas,
                               String versionActual, String runtimeActual) {
        this.texto = texto;
        this.binaria = binaria;
        this.rutas = rutas;
        this.versionActual = versionActual;
        this.runtimeActual = runtimeActual;
    }

    @Override public boolean activo() {
        Boolean a = activo;
        if (a == null) {
            avisarSiUi("ActualizadorService.activo");
            a = Instalacion.activa(rutas.get());
            activo = a;
            log("actualizar: " + (a ? "actualizador propio activo" : "sin actualizador propio (no instalada o carpeta no escribible): aviso por tags"));
        }
        return a;
    }

    // ------------------------------------------------------------------ update.json

    /** Lee update.json. Null si no es un objeto o falta (o está mal) alguno de los obligatorios. */
    static Info leer(String json) {
        Object raiz;
        try { raiz = dev.tirador.aoe2radar.util.Json.parse(json); } catch (RuntimeException ex) { return null; }
        if (!(raiz instanceof Map<?, ?> m)) return null;
        String version = texto(m.get("version")), jar = texto(m.get("jar")), sha = texto(m.get("sha256"));
        if (version == null || !Instalacion.VERSION.matcher(version).matches()) return null;
        if (!Instalacion.nombreJar(version).equals(jar)) return null;   // «aoe2radar-X.Y.jar» de esa misma versión, sin rutas
        if (!VerificacionJar.shaValido(sha)) return null;
        if (!(m.get("size") instanceof Number n) || n.doubleValue() <= 0 || n.doubleValue() != Math.rint(n.doubleValue())
                || n.doubleValue() > 1L << 40) return null;
        return new Info(version, jar, sha.toLowerCase(Locale.ROOT), n.longValue(), texto(m.get("runtime")),
                texto(m.get("instalador")), lista(m.get("classpath")), lista(m.get("opciones")), texto(m.get("mainclass")));
    }

    private static String texto(Object o) {
        return o instanceof String s && !s.isBlank() ? s.strip() : null;
    }

    private static List<String> lista(Object o) {
        if (!(o instanceof List<?> l)) return null;
        List<String> out = new ArrayList<>();
        for (Object x : l) {
            if (!(x instanceof String s)) return null;
            out.add(s.strip());
        }
        return List.copyOf(out);
    }

    /** Jar o instalador. Completa si esa versión ya falló como jar, si no hay .cfg legible, o si el runtime, el resto
     *  del classpath, las opciones de Java o la clase principal no son los de esta instalación (o update.json no los
     *  dice). */
    static Tipo decidir(Info i, String runtimeActual, CfgLanzador.Datos cfg, String versionFallida) {
        if (i.version().equals(versionFallida)) return Tipo.COMPLETA;
        if (cfg == null) return Tipo.COMPLETA;
        if (i.runtime() == null || !i.runtime().equals(runtimeActual)) return Tipo.COMPLETA;
        if (i.classpath() == null || !i.classpath().equals(cfg.resto())) return Tipo.COMPLETA;
        if (i.opciones() == null || !i.opciones().equals(cfg.opciones())) return Tipo.COMPLETA;
        if (i.mainclass() == null || !i.mainclass().equals(cfg.mainclass())) return Tipo.COMPLETA;
        return Tipo.JAR;
    }

    // ------------------------------------------------------------------ comprobar y descargar

    @Override public Resultado comprobar(boolean auto) {
        avisarSiUi("ActualizadorService.comprobar");
        try {
            return comprobarYa(auto);
        } catch (RuntimeException ex) {   // nada de lo de aquí debería lanzar; si lo hace, no tumba el hilo que llama
            log("actualizar: al comprobar: " + causa(ex));
            return new Resultado(Estado.SIN_DATOS, null);
        }
    }

    private Resultado comprobarYa(boolean auto) {
        Instalacion.Rutas r = rutas.get();
        if (!activo() || r == null) return new Resultado(Estado.SIN_DATOS, null);
        Info info;
        try {
            Transporte.Respuesta resp = texto.get(URL_UPDATE);
            if (resp.estado() != 200) { log("actualizar: update.json HTTP " + resp.estado()); return new Resultado(Estado.SIN_DATOS, null); }
            info = leer(resp.cuerpo());
            if (info == null) { log("actualizar: update.json no válido"); return new Resultado(Estado.SIN_DATOS, null); }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Resultado(Estado.SIN_DATOS, null);
        } catch (Exception ex) {
            log("actualizar: update.json: " + causa(ex));
            return new Resultado(Estado.SIN_DATOS, null);
        }
        if (!versionMayor(info.version(), versionActual)) { ultima = null; return new Resultado(Estado.AL_DIA, null); }
        Tipo tipo = decidir(info, runtimeActual, CfgLanzador.leer(r.cfg()), Instalacion.versionFallida(r.dir()));
        if (tipo == Tipo.COMPLETA) {
            ultima = null;   // descargar() nunca baja una versión que pide el instalador
            log("actualizar: la " + info.version() + " necesita el instalador");
            return new Resultado(Estado.COMPLETA, info.version());
        }
        ultima = info;
        if (listaVale(r, info)) return new Resultado(Estado.LISTA, info.version());
        if (!auto) return new Resultado(Estado.DISPONIBLE, info.version());
        return descargar();
    }

    /** ¿Ya está descargada y comprobada esta misma versión? (de una sesión anterior, o de otra instancia). */
    private static boolean listaVale(Instalacion.Rutas r, Info i) {
        Instalacion.Lista l = Instalacion.leerLista(r.dir());
        if (l == null || !l.version().equals(i.version()) || !l.jar().equals(i.jar()) || !l.sha256().equalsIgnoreCase(i.sha256())
                || l.size() != i.size() || !l.runtime().equals(i.runtime())) return false;
        return VerificacionJar.verificar(r.dir().resolve(l.jar()), l.size(), l.sha256(), null).ok();
    }

    /** Una sola descarga a la vez; a un temporal «.descargando» con el SHA-256 calculado mientras llega, cortando si
     *  pasa del tamaño anunciado; se comprueba tamaño, sha y contenido (zip con Main y la clase principal del .cfg) y
     *  solo entonces se mueve al nombre bueno y se apunta como lista. Si falla, no queda nada con el nombre bueno. */
    @Override public Resultado descargar() {
        avisarSiUi("ActualizadorService.descargar");
        Info info = ultima;
        Instalacion.Rutas r = rutas.get();
        if (info == null || r == null) return new Resultado(Estado.SIN_DATOS, null);
        if (!descargando.compareAndSet(false, true)) return new Resultado(Estado.EN_CURSO, info.version());
        Path tmp = null;
        try {
            if (listaVale(r, info)) return new Resultado(Estado.LISTA, info.version());
            CfgLanzador.Datos cfg = CfgLanzador.leer(r.cfg());
            Files.createDirectories(r.dir());
            tmp = Files.createTempFile(r.dir(), info.jar(), Instalacion.SUFIJO_DESCARGA);
            MessageDigest md = VerificacionJar.nuevoSha256();
            long total = 0;
            try (InputStream in = binaria.abrir(urlJar(info)); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) {
                    total += n;
                    if (total > info.size()) throw new IOException("más grande de lo anunciado (" + info.size() + " bytes)");
                    md.update(buf, 0, n);
                    out.write(buf, 0, n);
                }
            }
            if (total != info.size()) throw new IOException("llegaron " + total + " bytes de " + info.size());
            if (!HexFormat.of().formatHex(md.digest()).equals(info.sha256())) throw new IOException("el sha256 no coincide");
            VerificacionJar.Resultado v = VerificacionJar.verificarContenido(tmp, cfg == null ? null : cfg.mainclass());
            if (!v.ok()) throw new IOException(v.motivo());
            Instalacion.Lista lista = new Instalacion.Lista(info.version(), info.jar(), info.sha256(), info.size(),
                    info.runtime(), info.classpath(), info.opciones(), info.mainclass());
            if (!Instalacion.registrarDescarga(r.dir(), tmp, lista)) throw new IOException("no se pudo apuntar la descarga");
            log("actualizar: descargada y comprobada la " + info.version() + " (" + total + " bytes)");
            return new Resultado(Estado.LISTA, info.version());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log("actualizar: descarga interrumpida");
            return new Resultado(Estado.FALLO_DESCARGA, info.version());
        } catch (Exception ex) {
            log("actualizar: descarga de la " + info.version() + " fallida (" + causa(ex) + "); se reintenta en la próxima comprobación");
            return new Resultado(Estado.FALLO_DESCARGA, info.version());
        } finally {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            descargando.set(false);
        }
    }

    @Override public boolean aplicarAlCerrar() {
        avisarSiUi("ActualizadorService.aplicarAlCerrar");
        try {
            if (!activo()) return false;
            Instalacion.Aplicacion a = Instalacion.aplicar(rutas.get(), versionActual, runtimeActual);
            return a == Instalacion.Aplicacion.APLICADA || a == Instalacion.Aplicacion.YA_ESTABA;
        } catch (RuntimeException ex) {
            log("actualizar: al cerrar: " + causa(ex));
            return false;
        }
    }

    @Override public String[] avisoFallida() {
        avisarSiUi("ActualizadorService.avisoFallida");
        Instalacion.Rutas r = rutas.get();
        if (r == null || !activo()) return null;
        String[] f = Instalacion.fallidaSinAvisar(r.dir());
        if (f != null) Instalacion.marcarFallidaAvisada(r.dir());
        return f;
    }
}
