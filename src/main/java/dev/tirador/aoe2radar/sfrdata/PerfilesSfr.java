package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.Sello;
import dev.tirador.aoe2radar.util.Json;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.leerGzJson;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Los perfiles precalculados de la release «perfiles» de sfr-data: el año completo de un jugador sin tocar la API.
 * <ul>
 * <li>indice(): index.json (hasta, desde, shards, deltas…). Vale 6 h desde que se bajó, en memoria y en disco (cambia
 *     cada noche): al reiniciar la app no se vuelve a pedir, y la memoria cuenta desde la fecha de la copia (si no, las
 *     dos caducidades se sumarían). Si falla, null y se reintenta en la siguiente llamada.</li>
 * <li>shard(pid): el paquete del jugador. Formato v2 = base semanal (shard-NNNN, en su propia release si el índice
 *     lo dice) + deltas diarios de su grupo, sin repetir partidas; formato v1 = shard-NNN.</li>
 * </ul>
 */
public final class PerfilesSfr {
    private final SfrDataClient sfr;
    private final Sello sello;
    private volatile Map<String, Object> indice;

    public PerfilesSfr(SfrDataClient sfr, CacheService cache) {
        this.sfr = sfr;
        this.sello = cache.sello(Caducidad.NOCTURNO);
    }

    /** index.json de la release (caché de 6 h): hasta, desde, shards, alcance. null si no hay release todavía. */
    public Map<String, Object> indice() {
        if (indice != null && sello.fresco()) return indice;
        try {
            byte[] raw = sfr.diario("index.json", 20, Caducidad.NOCTURNO);   // un fallo de red sale tal cual: no hay copia que borrar
            Object root;
            try { root = Json.parse(new String(raw, StandardCharsets.UTF_8).replace(",NaN", ",\"\"").replace("[NaN", "[\"\""));   // tolerancia: un NaN suelto no es JSON válido
            } catch (Exception ex) { sfr.olvidarDiario("index.json"); throw ex; }
            if (root instanceof Map<?, ?> m) { @SuppressWarnings("unchecked") Map<String, Object> mm = (Map<String, Object>) m; indice = mm; }
            sello.marcar(sfr.fechaDiario("index.json"));   // la edad cuenta desde que se bajó, no desde que se leyó del disco
        } catch (Exception ex) { log("perfiles: index: " + causa(ex)); indice = null; sello.marcar(); }
        return indice;
    }

    /** El paquete del jugador (ver la clase). Devuelve {"v", "j": {pid: [partidas]}, "civs", "mapas", "hasta"}; null sin índice. */
    public Map<String, Object> shard(long pid) throws Exception {
        Map<String, Object> idx = indice();
        if (idx == null) return null;
        int shards = idx.get("shards") instanceof Number n ? n.intValue() : 256;
        int i = (int) (pid % shards);
        boolean v2 = idx.get("v") instanceof Number vn && vn.intValue() >= 2;
        if (!v2) {   // formato antiguo (1.4.x): un paquete por jugador con nombres dentro
            String nombre = String.format("shard-%03d.json.gz", i);
            Map<String, Object> m = leerPaquete(nombre, sfr.versionado(nombre, String.valueOf(idx.get("hasta")), 120));
            m.put("v", 1L); return m;
        }
        String baseHasta = String.valueOf(idx.get("base_hasta"));
        String tagBase = idx.get("base_release") instanceof String tb && !tb.isBlank() ? tb : null;   // los paquetes base viven en su propia release (perfiles-base-…)
        String urlBase = tagBase == null ? sfr.baseRelease() : sfr.baseRelease().replaceAll("/[^/]+/?$", "/") + tagBase + "/";
        String nombreBase = String.format("shard-%04d.json.gz", i);
        Map<String, Object> base = leerPaquete(nombreBase, sfr.versionado(urlBase, nombreBase, tagBase == null ? baseHasta : tagBase, 120));
        Map<String, Object> out = new HashMap<>(); out.put("v", 2L); out.put("civs", idx.get("civs")); out.put("mapas", idx.get("mapas")); out.put("hasta", idx.get("hasta"));
        Map<String, List<Object>> j = new HashMap<>();
        if (base.get("j") instanceof Map<?, ?> bj) for (Map.Entry<?, ?> en : bj.entrySet()) j.put(String.valueOf(en.getKey()), new ArrayList<>(arr(en.getValue())));
        int grupos = idx.get("grupos") instanceof Number gn ? gn.intValue() : 16;
        String clave = String.valueOf(pid);
        for (Object d : arr(idx.get("deltas"))) {   // los días posteriores a la base: solo el grupo de este paquete
            String fecha = String.valueOf(d);
            String nombreDelta = "delta-" + fecha + "-g" + (i % grupos) + ".json.gz";
            try {
                Map<String, Object> dj = leerPaquete(nombreDelta, sfr.versionado(nombreDelta, fecha, 60));
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

    /**
     * leerGzJson(bytes) de un paquete de versionado(); si no se entiende (roto, truncado), borra la copia en disco
     * y su marca ".v" (sfr.olvidarVersionado): sin esto, el paquete roto valdría hasta que cambiara la versión y
     * cada intento releería la misma basura. Si se entiende, un paquete roto de verdad (JSON válido pero sin lo
     * que se espera) no se borra: eso no lo arregla volver a bajar la misma release (fila 66 de DEUDA).
     */
    private Map<String, Object> leerPaquete(String nombre, byte[] raw) throws Exception {
        try { return leerGzJson(raw); }
        catch (Exception ex) { sfr.olvidarVersionado(nombre); throw ex; }
    }
}
