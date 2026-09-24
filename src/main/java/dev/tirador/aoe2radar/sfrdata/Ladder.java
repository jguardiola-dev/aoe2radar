package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Sello;
import dev.tirador.aoe2radar.model.LadderHist;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Rejilla;
import dev.tirador.aoe2radar.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.tirador.aoe2radar.api.Http.HTTP;
import static dev.tirador.aoe2radar.api.Http.UA;
import static dev.tirador.aoe2radar.cache.Directorios.LADDER_DIR;
import static dev.tirador.aoe2radar.cache.ImagenesMapa.MAPA_IMG_URL;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.firstNonNull;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/** Resúmenes del ladder publicados por sfr-data (rama data): campanas, dispersión, clanes; con caché en disco por ETag. */
public final class Ladder {
    private Ladder() {}

    // =====================================================================================
    // LADDER — resúmenes ligeros publicados por sfr-data (rama «data»; GitHub Actions procesa
    // los volcados diarios de aoe2companion): campanas por ladder (todos y activos), dispersión
    // rating 1v1 × equipos, y clanes con sus miembros
    // =====================================================================================
    public static final String SFR_DATA = "https://raw.githubusercontent.com/jguardiola-dev/sfr-data/data/";
    public static volatile Map<String, LadderHist> ladderHists = Map.of();          // todos los jugadores del ladder
    public static volatile Map<String, LadderHist> ladderHistsActivos = Map.of();   // activos: 10+ partidas y una en 28 días
    public static volatile Map<String, Rejilla> dispersionTodos = Map.of();         // «rm» / «ew» → rejilla 1v1 × equipos
    public static volatile Map<String, Rejilla> dispersionActivos = Map.of();
    public static volatile Map<String, List<LadderRow>> clanes = Map.of();          // tag → miembros (1v1 RM), ordenados por rating
    public static volatile String ladderGenerado = "";
    public static volatile int activosMinPartidas = 10, activosDias = 28;
    private static final Sello LADDER_SELLO = CacheService.SISTEMA.sello(Caducidad.NOCTURNO);   // cuándo se cargaron bien los resúmenes
    public static volatile boolean ladderCargando;
    public static volatile String ladderProgreso = "";
    public static final Object LADDER_LOCK = new Object();

    /** Baja un archivo de sfr-data si cambió (ETag) y devuelve su contenido (gzip transparente). */
    public static byte[] sfrDataArchivo(String nombre) throws Exception {
        Path local = LADDER_DIR.resolve(nombre);
        Files.createDirectories(local.getParent());
        if (!CacheService.SISTEMA.archivoFresco(local, Caducidad.NOCTURNO)) {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(SFR_DATA + nombre)).timeout(Duration.ofSeconds(60)).header("User-Agent", UA).GET();
            String etag = leerConfig("sfrdata_etag_" + nombre, "");
            if (!etag.isEmpty() && Files.exists(local)) rb.header("If-None-Match", etag);
            HttpResponse<byte[]> r = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() == 304) Files.setLastModifiedTime(local, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
            else if (r.statusCode() / 100 == 2) { Files.write(local, r.body()); r.headers().firstValue("etag").ifPresent(e -> guardarConfig("sfrdata_etag_" + nombre, e)); }
            else if (!Files.exists(local)) throw new IOException("HTTP " + r.statusCode() + " (" + nombre + ")");
        }
        byte[] b = Files.readAllBytes(local);
        if (nombre.endsWith(".gz")) try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(b))) { b = in.readAllBytes(); }
        return b;
    }

    public static double dblNum(Object o) { return o instanceof Number n ? n.doubleValue() : 0; }

    public static Map<String, LadderHist> parsearHists(Object o) {
        Map<String, LadderHist> h = new HashMap<>();
        for (Map.Entry<String, Object> en : obj(o).entrySet()) {
            Map<String, Object> d = obj(en.getValue());
            List<Object> bl = arr(d.get("bins"));
            int[] bins = new int[bl.size()];
            for (int i = 0; i < bins.length; i++) bins[i] = (int) lng(bl.get(i));
            Map<String, Integer> pct = new HashMap<>();
            for (Map.Entry<String, Object> p : obj(d.get("percentiles")).entrySet()) pct.put(p.getKey(), (int) lng(p.getValue()));
            h.put(en.getKey(), new LadderHist((int) lng(d.get("total")), (int) lng(d.get("min")), bins, (int) lng(d.get("mediana")), pct));
        }
        return h;
    }

    public static Rejilla parsearRejilla(Object o) {
        Map<String, Object> d = obj(o);
        if (d.isEmpty()) return null;
        List<Object> cl = arr(d.get("celdas"));
        int[][] celdas = new int[cl.size()][];
        for (int i = 0; i < celdas.length; i++) {
            List<Object> c = arr(cl.get(i));
            celdas[i] = new int[]{ (int) lng(c.get(0)), (int) lng(c.get(1)), (int) lng(c.get(2)) };
        }
        Map<String, Object> rec = obj(d.get("recta"));
        return new Rejilla((int) lng(d.get("n")), (int) lng(d.get("min_x")), (int) lng(d.get("min_y")), celdas, dblNum(rec.get("a")), dblNum(rec.get("b")), dblNum(rec.get("r")));
    }

    /** Carga (o refresca) campanas, dispersión y clanes. null si va bien; si no, el motivo. */
    public static String ladderAsegurar(boolean forzar) {
        synchronized (LADDER_LOCK) {
            if (!forzar && !ladderHists.isEmpty() && LADDER_SELLO.fresco()) return null;
            try {
                ladderProgreso = t("Descargando los resúmenes del ladder…", "Downloading the ladder summaries…");
                Map<String, Object> lj = obj(Json.parse(new String(sfrDataArchivo("ladder.json"), StandardCharsets.UTF_8)));
                Map<String, LadderHist> h = parsearHists(lj.get("ladders"));
                Map<String, LadderHist> ha = parsearHists(lj.get("activos"));
                Map<String, Object> def = obj(lj.get("activos_def"));
                if (!def.isEmpty()) { activosMinPartidas = (int) lng(def.get("min_partidas")); activosDias = (int) lng(def.get("dias")); }
                ladderGenerado = String.valueOf(firstNonNull(lj.get("generado"), ""));
                Map<String, Rejilla> dt = new HashMap<>(), da = new HashMap<>();
                try {
                    ladderProgreso = t("Descargando la dispersión…", "Downloading the scatter data…");
                    Map<String, Object> dj = obj(Json.parse(new String(sfrDataArchivo("dispersion.json.gz"), StandardCharsets.UTF_8)));
                    for (Map.Entry<String, Object> en : obj(dj.get("parejas")).entrySet()) {
                        Map<String, Object> p = obj(en.getValue());
                        Rejilla r1 = parsearRejilla(p.get("todos")), r2 = parsearRejilla(p.get("activos"));
                        if (r1 != null) dt.put(en.getKey(), r1);
                        if (r2 != null) da.put(en.getKey(), r2);
                    }
                } catch (Exception ex) { log("ladder: dispersión no disponible: " + causa(ex)); }
                try {   // mapas.json: imagen de cada mapa del pool (URL del CDN del companion), aprendida cada noche por sfr-data
                    Map<String, Object> mj = obj(Json.parse(new String(sfrDataArchivo("mapas.json"), StandardCharsets.UTF_8)));
                    for (Map.Entry<String, Object> en : obj(mj.get("mapas")).entrySet()) if (String.valueOf(en.getValue()).startsWith("http")) MAPA_IMG_URL.putIfAbsent(en.getKey().toLowerCase(Locale.ROOT).trim(), String.valueOf(en.getValue()));
                } catch (Exception ex) { log("ladder: mapas.json no disponible: " + causa(ex)); }
                ladderProgreso = t("Descargando los clanes…", "Downloading the clans…");
                Map<String, Object> cj = obj(Json.parse(new String(sfrDataArchivo("clans.json.gz"), StandardCharsets.UTF_8)));
                Map<String, List<LadderRow>> cl = new HashMap<>();
                for (Map.Entry<String, Object> en : obj(cj.get("clans")).entrySet()) {
                    List<LadderRow> mi = new ArrayList<>();
                    for (Object o : arr(en.getValue())) {
                        List<Object> m = arr(o);
                        if (m.size() < 3) continue;
                        mi.add(new LadderRow(lng(m.get(0)), String.valueOf(m.get(1)), (int) lng(m.get(2)), m.size() > 3 ? (int) lng(m.get(3)) : 0,
                                m.size() > 4 ? String.valueOf(m.get(4)) : "", en.getKey(), 0));
                    }
                    cl.put(en.getKey(), mi);
                }
                ladderHists = h; ladderHistsActivos = ha; dispersionTodos = dt; dispersionActivos = da; clanes = cl; LADDER_SELLO.marcar();
                log("ladder: resúmenes de sfr-data cargados (" + h.size() + " ladders, " + ha.size() + " con activos, " + dt.size() + " dispersiones, " + cl.size() + " clanes, generado " + ladderGenerado + ")");
                return null;
            } catch (Exception ex) {
                log("ladder: " + causa(ex));
                return causa(ex);
            }
        }
    }

    public static LadderHist hist(String id) { return ladderHists.get(id); }
    /** La campana elegida: activos si se pide y existe; si no, todos. */
    public static LadderHist hist(String id, boolean activos) { LadderHist h = activos ? ladderHistsActivos.get(id) : null; return h != null ? h : ladderHists.get(id); }
}
