package dev.tirador.aoe2radar.service;

import java.net.URI;
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

/**
 * Todo lo que, para pintar un icono de bandera, civ o mapa, toca disco o red: rutas fijas y la descarga de
 * miniaturas de mapa al companion. Vive en service para que ui.Iconos (las cachés de ImageIcon, que sí son de
 * interfaz) pinte sin conocer carpetas, patrones de URL ni el hilo de descarga.
 */
public final class ImagenesJuego {
    private ImagenesJuego() { }

    /** Carpeta de banderas (20×15, dominio público, Wikimedia vía hampusborgos/country-flags); el bat las deja junto al exe. */
    public static final Path BANDERAS_DIR = Path.of("banderas");

    /** Mapas cuya miniatura ya se está pidiendo al companion, para no duplicar la descarga. */
    private static final Set<String> MAPAS_PIDIENDO = ConcurrentHashMap.newKeySet();

    /** Ruta del png de la bandera (código ISO ya en minúsculas), o null si no está en ninguna de las dos ubicaciones. */
    public static Path rutaBandera(String k) {
        Path p = BANDERAS_DIR.resolve(k + ".png");
        if (!Files.exists(p)) p = BANDERAS_DIR.resolve("banderas").resolve(k + ".png");   // «Extraer todo» de Windows anida la carpeta
        if (!Files.exists(p)) return null;
        return p;
    }

    /** Ruta del icono de civ en el tech tree (clave ya en minúsculas); no comprueba si existe, como hoy. */
    public static Path rutaIconoCiv(String k) {
        return TT_DIR.resolve("img/Civs/" + k + ".png");
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
