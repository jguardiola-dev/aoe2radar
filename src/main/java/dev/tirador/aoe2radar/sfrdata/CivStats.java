package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.model.CivFila;
import dev.tirador.aoe2radar.model.Matchup;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.util.Json;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.firstNonNull;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/** Estadísticas de civilizaciones publicadas por sfr-data: ventanas por periodo y tendencias mensuales, con caché. */
public final class CivStats {
    private CivStats() {}

    public static final Map<String, VentanaStats> VENTANAS_STATS = new java.util.concurrent.ConcurrentHashMap<>();
    public static volatile Tendencias tendenciasStats;
    public static final String[] MODOS_STATS = { "rm_1v1", "rm_2v2", "rm_3v3", "rm_4v4", "ew_1v1", "ew_team", "dm_1v1", "dm_team" };
    public static final String[] VENTANAS_STATS_KEYS = { "7", "30", "90", "365", "parche" };

    public static VentanaStats parsearVentana(Map<String, Object> j) {
        List<String> tramos = new ArrayList<>();
        for (Object o : arr(j.get("tramos"))) tramos.add(String.valueOf(o));
        Map<String, Map<String, Integer>> modos = new LinkedHashMap<>();
        for (Map.Entry<String, Object> en : obj(j.get("modos")).entrySet()) {
            Map<String, Integer> c = new HashMap<>();
            for (Map.Entry<String, Object> x : obj(en.getValue()).entrySet()) c.put(x.getKey(), (int) lng(x.getValue()));
            modos.put(en.getKey(), c);
        }
        Map<String, String> nombres = new HashMap<>();
        for (Map.Entry<String, Object> en : obj(j.get("nombres_mapas")).entrySet()) nombres.put(en.getKey(), String.valueOf(en.getValue()));
        Map<String, Map<String, Integer>> mapas = new HashMap<>();
        for (Object o : arr(j.get("mapas"))) { List<Object> f = arr(o); mapas.computeIfAbsent(String.valueOf(f.get(0)), k -> new LinkedHashMap<>()).put(String.valueOf(f.get(1)), (int) lng(f.get(2))); }
        List<CivFila> civs = new ArrayList<>();
        for (Object o : arr(j.get("civs"))) {
            List<Object> f = arr(o);
            if (f.size() < 7) continue;
            String civ = String.valueOf(f.get(3));
            if ("unknown".equals(civ)) continue;
            civs.add(new CivFila(String.valueOf(f.get(0)), String.valueOf(f.get(1)), String.valueOf(f.get(2)), civ, (int) lng(f.get(4)), (int) lng(f.get(5)), lng(f.get(6))));
        }
        List<Matchup> mu = new ArrayList<>();
        for (Object o : arr(j.get("matchups"))) {
            List<Object> f = arr(o);
            if (f.size() < 6) continue;
            if ("unknown".equals(String.valueOf(f.get(2))) || "unknown".equals(String.valueOf(f.get(3)))) continue;
            mu.add(new Matchup(String.valueOf(f.get(0)), String.valueOf(f.get(1)), String.valueOf(f.get(2)), String.valueOf(f.get(3)), (int) lng(f.get(4)), (int) lng(f.get(5))));
        }
        Object parche = j.get("parche");
        return new VentanaStats(String.valueOf(j.get("ventana")), String.valueOf(firstNonNull(j.get("desde"), "")), String.valueOf(firstNonNull(j.get("hasta"), "")), (int) lng(j.get("dias")),
                parche == null ? "" : String.valueOf(parche instanceof Number n ? (Object) n.longValue() : parche), tramos, modos, nombres, mapas, civs, mu);
    }

    /**
     * Carga (con caché en memoria, para toda la sesión) la ventana pedida. null si va bien; si no, el motivo.
     * sfr: el cliente de sfr-data, inyectado (service.StatsService es quien decide cuándo llamar a esto; ver esa
     * clase para el contrato de hilos). Antes tomaba el archivo del Ladder.sfrDataArchivo estático: mismo dato,
     * ahora con el cliente que quien llama decide (para poder probarlo sin red).
     */
    public static String statsAsegurar(SfrDataClient sfr, String ventana, boolean conTendencias) {
        try {
            if (!VENTANAS_STATS.containsKey(ventana)) {
                // se parsea fuera del mapa y se entra con putIfAbsent: si dos hilos ven el mismo hueco vacío a la
                // vez, el segundo en llegar no pisa el valor que el primero ya dejó puesto (fila 38 de DEUDA)
                VentanaStats v = parsearVentana(obj(Json.parse(new String(sfr.datos("civstats/ventanas/v" + ventana + ".json.gz"), StandardCharsets.UTF_8))));
                VENTANAS_STATS.putIfAbsent(ventana, v);
            }
            if (conTendencias && tendenciasStats == null) {
                Map<String, Object> j = obj(Json.parse(new String(sfr.datos("civstats/tendencias.json.gz"), StandardCharsets.UTF_8)));
                List<String> meses = new ArrayList<>();
                for (Object o : arr(j.get("meses"))) meses.add(String.valueOf(o));
                Map<String, Map<String, Integer>> partidas = new HashMap<>();
                for (Object o : arr(j.get("partidas"))) { List<Object> f = arr(o); partidas.computeIfAbsent(String.valueOf(f.get(0)), k -> new HashMap<>()).put(String.valueOf(f.get(1)), (int) lng(f.get(2))); }
                Map<String, Map<String, int[]>> filas = new HashMap<>();
                for (Object o : arr(j.get("filas"))) { List<Object> f = arr(o); filas.computeIfAbsent(f.get(0) + "|" + f.get(1), k -> new HashMap<>()).put(String.valueOf(f.get(2)), new int[]{ (int) lng(f.get(3)), (int) lng(f.get(4)) }); }
                Map<String, Map<String, int[]>> filasMapa = new HashMap<>();
                for (Object o : arr(j.get("filas_mapa"))) { List<Object> f = arr(o); filasMapa.computeIfAbsent(f.get(0) + "|" + f.get(1) + "|" + f.get(2), k -> new HashMap<>()).put(String.valueOf(f.get(3)), new int[]{ (int) lng(f.get(4)), (int) lng(f.get(5)) }); }
                Map<String, Map<String, int[]>> filasTramo = new HashMap<>();
                for (Object o : arr(j.get("filas_tramo"))) { List<Object> f = arr(o); filasTramo.computeIfAbsent(f.get(0) + "|" + f.get(1) + "|" + f.get(2), k -> new HashMap<>()).put(String.valueOf(f.get(3)), new int[]{ (int) lng(f.get(4)), (int) lng(f.get(5)) }); }
                Map<String, Map<String, int[]>> filasMT = new HashMap<>();
                for (Object o : arr(j.get("filas_mt"))) { List<Object> f = arr(o); filasMT.computeIfAbsent(f.get(0) + "|" + f.get(1) + "|" + f.get(2) + "|" + f.get(3), k -> new HashMap<>()).put(String.valueOf(f.get(4)), new int[]{ (int) lng(f.get(5)), (int) lng(f.get(6)) }); }
                tendenciasStats = new Tendencias(meses, partidas, filas, filasMapa, filasTramo, filasMT);
            }
            return null;
        } catch (Exception ex) { log("civstats: " + causa(ex)); return causa(ex); }
    }
}
