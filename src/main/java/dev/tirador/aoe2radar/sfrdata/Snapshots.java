package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Sello;
import dev.tirador.aoe2radar.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static dev.tirador.aoe2radar.cache.Directorios.LADDER_DIR;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.leerGzJson;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/** Snapshots nocturnos de sfr-data: ELO de anoche y de hace 7 días, muestra de ayer y release «perfiles» (índice y archivos con caché en disco). */
public final class Snapshots {
    private Snapshots() {}

    // ----- Snapshots nocturnos de sfr-data: ELO de anoche (forma por resta, índice de nombres), de hace 7 días y la muestra de ayer -----
    public static final Map<Long, int[]> ELO_AYER = new java.util.concurrent.ConcurrentHashMap<>();     // pid → {elo1v1, partidas1v1, eloEq, partidasEq}
    public static final Map<Long, String[]> NOMBRES_AYER = new java.util.concurrent.ConcurrentHashMap<>();   // pid → {nombre, país}
    public static final Map<Long, int[]> ELO_HACE7 = new java.util.concurrent.ConcurrentHashMap<>();
    public static volatile String eloAyerFecha, eloHace7Fecha; public static volatile boolean eloAyerCargando;
    private static final Sello ELO_AYER_SELLO = CacheService.SISTEMA.sello(Caducidad.NOCTURNO), MUESTRA_SELLO = CacheService.SISTEMA.sello(Caducidad.NOCTURNO);
    public static volatile Map<String, List<List<Object>>> MUESTRA_AYER; public static volatile String muestraFecha;
    public static byte[] descargarCacheDiaria(String nombre, int timeoutS) throws Exception {   // un archivo de la release, guardado en disco y reutilizado 12 h
        Path f = PERFILES_SHARDS_DIR.resolve(nombre);
        try { if (CacheService.SISTEMA.archivoFresco(f, Caducidad.DESCARGA_DIARIA)) return Files.readAllBytes(f); } catch (IOException ignored) { }
        byte[] raw = descargarBytes(perfilesBase() + nombre, timeoutS);
        try { Files.createDirectories(PERFILES_SHARDS_DIR); Files.write(f, raw); } catch (IOException ignored) { }
        return raw;
    }
    /** Carga (o refresca cada 6 h) ELO_AYER/NOMBRES_AYER y ELO_HACE7. Silencioso si la release no existe todavía. */
    public static void cargarEloAyer() {
        if (eloAyerCargando || ELO_AYER_SELLO.fresco()) return;
        eloAyerCargando = true;
        try {
            Map<String, Object> m = leerGzJson(descargarCacheDiaria("elo_ayer.json.gz", 60));
            Object j = m.get("j");
            if (j instanceof Map<?, ?> jm) {
                ELO_AYER.clear(); NOMBRES_AYER.clear();
                for (Map.Entry<?, ?> en : jm.entrySet()) { List<Object> v = arr(en.getValue()); long pid = Long.parseLong(String.valueOf(en.getKey())); ELO_AYER.put(pid, new int[]{ (int) lng(v.get(0)), (int) lng(v.get(1)), (int) lng(v.get(2)), (int) lng(v.get(3)) }); NOMBRES_AYER.put(pid, new String[]{ String.valueOf(v.get(4)), String.valueOf(v.get(5)) }); }
                eloAyerFecha = String.valueOf(m.get("fecha"));
            }
            String hace7 = LocalDate.now(ZoneId.of("UTC")).minusDays(7).toString();
            try {
                Map<String, Object> m7 = leerGzJson(descargarCacheDiaria("elo-" + hace7 + ".json.gz", 60));
                Object j7 = m7.get("j");
                if (j7 instanceof Map<?, ?> jm) { ELO_HACE7.clear(); for (Map.Entry<?, ?> en : jm.entrySet()) { List<Object> v = arr(en.getValue()); ELO_HACE7.put(Long.parseLong(String.valueOf(en.getKey())), new int[]{ (int) lng(v.get(0)), (int) lng(v.get(1)), (int) lng(v.get(2)), (int) lng(v.get(3)) }); } eloHace7Fecha = hace7; }
            } catch (Exception ex) { log("perfiles: elo hace 7: " + causa(ex)); }
            log("perfiles: elo_ayer " + eloAyerFecha + ": " + ELO_AYER.size() + " jugadores; hace 7: " + ELO_HACE7.size());
        } catch (Exception ex) { log("perfiles: elo_ayer: " + causa(ex)); }
        finally { ELO_AYER_SELLO.marcar(); eloAyerCargando = false; }
    }
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
    public static final Path PERFILES_SHARDS_DIR = LADDER_DIR.resolve("perfiles_shards");

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
    /** Un archivo de la release con caché en disco por «versión» (la fecha que lo hace válido): si la marca coincide, no se baja. */
    public static byte[] archivoRelease(String nombre, String version, int timeoutS) throws Exception { return archivoRelease(perfilesBase(), nombre, version, timeoutS); }
    public static byte[] archivoRelease(String base, String nombre, String version, int timeoutS) throws Exception {
        Path f = PERFILES_SHARDS_DIR.resolve(nombre), marca = PERFILES_SHARDS_DIR.resolve(nombre + ".v");
        try { if (Files.exists(f) && Files.exists(marca) && version.equals(Files.readString(marca).trim())) return Files.readAllBytes(f); } catch (IOException ignored) { }
        byte[] raw = descargarBytes(base + nombre, timeoutS);
        try { Files.createDirectories(PERFILES_SHARDS_DIR); Files.write(f, raw); Files.writeString(marca, version); } catch (IOException ex) { log("perfiles: caché: " + causa(ex)); }
        return raw;
    }
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
