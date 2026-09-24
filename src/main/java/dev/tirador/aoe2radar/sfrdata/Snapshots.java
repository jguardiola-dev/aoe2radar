package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Sello;
import dev.tirador.aoe2radar.util.Json;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.tirador.aoe2radar.api.Freno.CONTROL;
import static dev.tirador.aoe2radar.api.Http.descargarBytes;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.leerGzJson;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/** Snapshots nocturnos de sfr-data: ELO de anoche y de hace 7 días, muestra de ayer y release «perfiles» (índice y archivos con caché en disco). */
public final class Snapshots {
    private Snapshots() {}

    // ----- Snapshots nocturnos de sfr-data: ELO de anoche (forma por resta, índice de nombres), de hace 7 días y la muestra de ayer -----
    /** El ELO de anoche y de hace 7 días (ver EloNocturno). Los mapas de abajo son los suyos: mismos objetos. */
    public static final EloNocturno ELO = new EloNocturno(SfrDataClient.SISTEMA, CacheService.SISTEMA,
            tarea -> { Thread h = new Thread(tarea, "elo-ayer-reintento"); h.setDaemon(true); h.start(); },
            () -> LocalDate.now(ZoneId.of("UTC")));
    public static final Map<Long, int[]> ELO_AYER = ELO.ayer;              // pid → {elo1v1, partidas1v1, eloEq, partidasEq}
    public static final Map<Long, String[]> NOMBRES_AYER = ELO.nombres;    // pid → {nombre, país}
    public static final Map<Long, int[]> ELO_HACE7 = ELO.hace7;
    private static final Sello MUESTRA_SELLO = CacheService.SISTEMA.sello(Caducidad.NOCTURNO);
    public static volatile Map<String, List<List<Object>>> MUESTRA_AYER; public static volatile String muestraFecha;
    /** Un archivo de la release, guardado en disco y reutilizado 12 h. Ver SfrDataClient.diario. */
    public static byte[] descargarCacheDiaria(String nombre, int timeoutS) throws Exception { return SfrDataClient.SISTEMA.diario(nombre, timeoutS); }
    /** Carga (o refresca) el ELO de anoche y de hace 7 días. Ver EloNocturno.cargar. */
    public static void cargarEloAyer() { ELO.cargar(); }
    /** La muestra de ayer (Al azar por ELO y Guess the ELO sin API). null si no está disponible. */
    public static Map<String, List<List<Object>>> muestraAyer() {
        if (MUESTRA_AYER != null && MUESTRA_SELLO.fresco()) return MUESTRA_AYER;
        try {
            Map<String, Object> m = leerGzJson(descargarCacheDiaria("muestra_ayer.json.gz", 60));
            Map<String, List<List<Object>>> out = new HashMap<>();
            if (m.get("tramos") instanceof Map<?, ?> tm) for (Map.Entry<?, ?> en : tm.entrySet()) { List<List<Object>> l = new ArrayList<>(); for (Object o : arr(en.getValue())) l.add(arr(o)); out.put(String.valueOf(en.getKey()), l); }
            MUESTRA_AYER = out; muestraFecha = String.valueOf(m.get("fecha"));
        } catch (Exception ex) { log("perfiles: muestra: " + causa(ex)); MUESTRA_AYER = null; }
        MUESTRA_SELLO.marcar();
        return MUESTRA_AYER;
    }

    // ----- Perfiles precalculados (release «perfiles» de sfr-data): el año completo sin tocar la API -----
    public static volatile Map<String, Object> PERFILES_INDEX;
    private static final Sello INDEX_SELLO = CacheService.SISTEMA.sello(Caducidad.NOCTURNO);

    public static String perfilesBase() {
        Object v = CONTROL.get("perfiles_base");
        String base = v instanceof String s && !s.isBlank() ? s : "https://github.com/jguardiola-dev/sfr-data/releases/download/perfiles/";
        return base.endsWith("/") ? base : base + "/";
    }
    /** index.json de la release (caché de 6 h): hasta, desde, shards, alcance. null si no hay release todavía. */
    public static Map<String, Object> perfilesIndex() {
        if (PERFILES_INDEX != null && INDEX_SELLO.fresco()) return PERFILES_INDEX;
        try {
            Object root = Json.parse(new String(descargarBytes(perfilesBase() + "index.json", 20), StandardCharsets.UTF_8).replace(",NaN", ",\"\"").replace("[NaN", "[\"\""));   // tolerancia: un NaN suelto no es JSON válido
            if (root instanceof Map<?, ?> m) { @SuppressWarnings("unchecked") Map<String, Object> mm = (Map<String, Object>) m; PERFILES_INDEX = mm; }
        } catch (Exception ex) { log("perfiles: index: " + causa(ex)); PERFILES_INDEX = null; }
        INDEX_SELLO.marcar();
        return PERFILES_INDEX;
    }
    /** Un archivo de la release con caché en disco por «versión» (la fecha que lo hace válido). Ver SfrDataClient.versionado. */
    public static byte[] archivoRelease(String nombre, String version, int timeoutS) throws Exception { return SfrDataClient.SISTEMA.versionado(nombre, version, timeoutS); }
    public static byte[] archivoRelease(String base, String nombre, String version, int timeoutS) throws Exception { return SfrDataClient.SISTEMA.versionado(base, nombre, version, timeoutS); }
    /** El paquete del jugador: formato v2 = base semanal (shard-NNNN) + deltas diarios (delta-fecha-gG) del índice; formato v1 = shard-NNN. Devuelve {"v", "j": {pid: [partidas]}, "civs", "mapas"}. */
    public static Map<String, Object> perfilesShard(long pid) throws Exception {
        Map<String, Object> idx = perfilesIndex();
        if (idx == null) return null;
        int shards = idx.get("shards") instanceof Number n ? n.intValue() : 256;
        int i = (int) (pid % shards);
        boolean v2 = idx.get("v") instanceof Number vn && vn.intValue() >= 2;
        if (!v2) {   // formato antiguo (1.4.x): un paquete por jugador con nombres dentro
            Map<String, Object> m = leerGzJson(archivoRelease(String.format("shard-%03d.json.gz", i), String.valueOf(idx.get("hasta")), 120));
            m.put("v", 1L); return m;
        }
        String baseHasta = String.valueOf(idx.get("base_hasta"));
        String tagBase = idx.get("base_release") instanceof String tb && !tb.isBlank() ? tb : null;   // los paquetes base viven en su propia release (perfiles-base-…)
        String urlBase = tagBase == null ? perfilesBase() : perfilesBase().replaceAll("/[^/]+/?$", "/") + tagBase + "/";
        Map<String, Object> base = leerGzJson(archivoRelease(urlBase, String.format("shard-%04d.json.gz", i), tagBase == null ? baseHasta : tagBase, 120));
        Map<String, Object> out = new HashMap<>(); out.put("v", 2L); out.put("civs", idx.get("civs")); out.put("mapas", idx.get("mapas")); out.put("hasta", idx.get("hasta"));
        Map<String, List<Object>> j = new HashMap<>();
        if (base.get("j") instanceof Map<?, ?> bj) for (Map.Entry<?, ?> en : bj.entrySet()) j.put(String.valueOf(en.getKey()), new ArrayList<>(arr(en.getValue())));
        int grupos = idx.get("grupos") instanceof Number gn ? gn.intValue() : 16;
        String clave = String.valueOf(pid);
        for (Object d : arr(idx.get("deltas"))) {   // los días posteriores a la base: solo el grupo de este paquete
            String fecha = String.valueOf(d);
            try {
                Map<String, Object> dj = leerGzJson(archivoRelease("delta-" + fecha + "-g" + (i % grupos) + ".json.gz", fecha, 60));
                if (dj.get("j") instanceof Map<?, ?> djm && djm.get(clave) != null) {
                    List<Object> lista = j.computeIfAbsent(clave, k -> new ArrayList<>());
                    Set<Long> ids = new HashSet<>(); for (Object o : lista) ids.add(lng(arr(o).get(0)));
                    for (Object o : arr(djm.get(clave))) if (ids.add(lng(arr(o).get(0)))) lista.add(o);   // sin repetir partidas ya presentes en la base
                }
            } catch (Exception ex) { log("perfiles: delta " + fecha + ": " + causa(ex)); }
        }
        out.put("j", j);
        return out;
    }
}
