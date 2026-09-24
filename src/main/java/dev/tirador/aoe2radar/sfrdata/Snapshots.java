package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Sello;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.api.Freno.CONTROL;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.leerGzJson;
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
    /** Índice y paquetes de la release «perfiles» (ver PerfilesSfr). */
    public static final PerfilesSfr PERFILES = new PerfilesSfr(SfrDataClient.SISTEMA, CacheService.SISTEMA);

    public static String perfilesBase() {
        Object v = CONTROL.get("perfiles_base");
        String base = v instanceof String s && !s.isBlank() ? s : "https://github.com/jguardiola-dev/sfr-data/releases/download/perfiles/";
        return base.endsWith("/") ? base : base + "/";
    }
    /** index.json de la release (caché de 6 h). Ver PerfilesSfr.indice. */
    public static Map<String, Object> perfilesIndex() { return PERFILES.indice(); }
    /** El paquete del jugador. Ver PerfilesSfr.shard. */
    public static Map<String, Object> perfilesShard(long pid) throws Exception { return PERFILES.shard(pid); }
}
