package dev.tirador.aoe2radar.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.tirador.aoe2radar.api.Http.HTTP;
import static dev.tirador.aoe2radar.api.Http.UA;
import static dev.tirador.aoe2radar.api.Parseo.MAPA_IMG_PATRON;
import static dev.tirador.aoe2radar.cache.Directorios.LADDER_DIR;
import static dev.tirador.aoe2radar.cache.ImagenesMapa.MAPA_IMG_URL;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.TT_DIR;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;
import static dev.tirador.aoe2radar.util.Sistema.enCarpetaApp;

/**
 * Todo lo que, para pintar un icono de bandera, civ o mapa, toca disco o red: rutas fijas y la descarga de
 * miniaturas de mapa al companion. Vive en service para que ui.Iconos (las cachés de ImageIcon, que sí son de
 * interfaz) pinte sin conocer carpetas, patrones de URL ni el hilo de descarga.
 */
public final class ImagenesJuego {
    private ImagenesJuego() { }

    /** Carpeta de banderas (20×15, dominio público, Wikimedia vía hampusborgos/country-flags); el bat las deja junto al exe.
     *  Es un recurso de la app (solo lectura): cuelga de la carpeta del exe, no de la de datos. */
    public static final Path BANDERAS_DIR = enCarpetaApp("banderas");

    /** Mapas cuya miniatura ya se está pidiendo al companion, para no duplicar la descarga. */
    private static final Set<String> MAPAS_PIDIENDO = ConcurrentHashMap.newKeySet();

    /** Ruta del png de la bandera (código ISO ya en minúsculas), o null si no está en ninguna de las dos ubicaciones. */
    public static Path rutaBandera(String k) {
        Path p = BANDERAS_DIR.resolve(k + ".png");
        if (!Files.exists(p)) p = BANDERAS_DIR.resolve("banderas").resolve(k + ".png");   // «Extraer todo» de Windows anida la carpeta
        if (!Files.exists(p)) return null;
        return p;
    }

    /** Dentro del jar van las mismas banderas (src/main/resources/banderas): el exe de jpackage no deja la carpeta
     *  «banderas» junto al exe como hacía el bat de la 1.1, y sin esto no salía ninguna (visto al probar la 1.2). */
    private static final String BANDERAS_JAR = "/banderas/";

    /** El png de la bandera: de la carpeta banderas si está (como en la 1.1), si no el que va dentro del jar; null si no hay. */
    public static byte[] bytesBandera(String k) {
        try {
            Path p = rutaBandera(k);
            return p != null ? Files.readAllBytes(p) : bytesBanderaJar(k);
        } catch (IOException ex) {
            return null;
        }
    }

    /** Solo la del jar (visible para tests: en el directorio de los tests el harness deja la carpeta banderas). */
    static byte[] bytesBanderaJar(String k) throws IOException {
        try (InputStream in = ImagenesJuego.class.getResourceAsStream(BANDERAS_JAR + k + ".png")) {
            return in == null ? null : in.readAllBytes();
        }
    }

    /** La misma bandera como dirección en texto, para un <img src> en HTML (file: o jar:); null si no hay. */
    public static String urlBandera(String k) {
        Path p = rutaBandera(k);
        return p != null ? p.toUri().toString() : urlBanderaJar(k);
    }

    /** Solo la del jar (visible para tests). */
    static String urlBanderaJar(String k) {
        URL u = ImagenesJuego.class.getResource(BANDERAS_JAR + k + ".png");
        return u == null ? null : u.toString();
    }

    /** Ruta del icono de civ en el tech tree (clave ya en minúsculas); no comprueba si existe, como hoy. */
    public static Path rutaIconoCiv(String k) {
        return TT_DIR.resolve("img/Civs/" + k + ".png");
    }

    /** ¿Hay un icono del tech tree en disco que no esté vacío? (1.4) Un PNG de 0 bytes (un corte de las versiones sin
     *  escritura atómica) contaba como presente con Files.exists y ya no se volvía a pedir. */
    public static boolean iconoEnDisco(Path p) {
        try { return Files.size(p) > 0; } catch (IOException ex) { return false; }
    }

    /**
     * Lee un icono PNG de disco (fuera del EDT). Si está vacío o no se decodifica (truncado por un corte), lo borra y
     * devuelve null: al faltar, quien lo pidió lo vuelve a traer (del jar o de aoe2techtree), en vez de quedarse sin
     * icono para siempre (1.4). Un fallo al LEER (el antivirus con el archivo abierto…) sale como IOException sin
     * borrar nada, como leerJson en techtree.TechTreeDatos.
     */
    public static java.awt.image.BufferedImage leerIcono(Path p) throws IOException {
        byte[] raw = Files.readAllBytes(p);   // fuera del try: un fallo de lectura no es un archivo dañado
        java.awt.image.BufferedImage img = null;
        if (raw.length > 0) {
            try { img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(raw)); }
            catch (IOException decodificar) { img = null; }
        }
        if (img == null) {
            log("icono dañado, se borra para volver a traerlo: " + p.getFileName());
            Files.deleteIfExists(p);
        }
        return img;
    }

    /** Clave de mapa a usar para buscar imagen: la del volcado o la del nombre, la que tenga URL conocida. */
    public static String claveMapa(String kc, String kn) {
        return kc != null && MAPA_IMG_URL.containsKey(kc) ? kc : kn != null && MAPA_IMG_URL.containsKey(kn) ? kn : kc != null ? kc : kn;
    }

    /** Ruta donde se guarda (o se busca) la miniatura de un mapa ya descargada. */
    public static Path rutaMapa(String k) {
        return LADDER_DIR.resolve("mapas").resolve(k.replaceAll("[^a-z0-9_-]", "_") + ".png");
    }

    /** Pide al companion la miniatura del mapa k (o por el patrón si aún no se ha visto) y la guarda en p; no bloquea. */
    public static void pedirMapa(String k, String kc, Path p) {
        String url = MAPA_IMG_URL.get(k);
        if (url == null && kc != null && MAPA_IMG_PATRON != null) url = MAPA_IMG_PATRON.replace("{clave}", kc);   // mapa no visto todavía: por el patrón
        if (url == null || !MAPAS_PIDIENDO.add(k)) return;
        String urlMini = url.contains("cdn.aoe2companion.com") && !url.contains("?") ? url + "?width=200" : url;   // miniatura de 200 px: el tamaño que el companion quiere servir
        new Thread(() -> {
            try {
                Files.createDirectories(p.getParent());
                HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(urlMini)).timeout(Duration.ofSeconds(20)).header("User-Agent", UA).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() / 100 == 2 && r.body().length > 0) Files.write(p, r.body());
            } catch (Exception ex) { log("mapa " + k + ": " + causa(ex)); }
            finally { MAPAS_PIDIENDO.remove(k); }
        }, "mapa-icono").start();
    }
}
