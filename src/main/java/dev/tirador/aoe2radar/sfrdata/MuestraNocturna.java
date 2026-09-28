package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.Sello;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.leerGzJson;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * La muestra de ayer de sfr-data («Al azar por ELO» y «Guess the ELO» sin tocar la API): partidas por tramo de ELO.
 * Vale lo que su copia en disco, 12 h desde que se bajó (un solo reloj, como EloNocturno). Si falla, null y se
 * reintenta en la siguiente llamada; si el archivo guardado no se entiende, se borra para volver a la red.
 */
public final class MuestraNocturna {
    static final String ARCHIVO = "muestra_ayer.json.gz";

    private final SfrDataClient sfr;
    private final Sello sello;
    /** Lo leído de una descarga: ayer y anteayer se publican juntos (una sola escritura volatile), nunca de dos cargas. */
    private record Carga(Map<String, List<List<Object>>> muestra, Map<String, List<List<Object>>> anteayer) { }
    private volatile Carga carga;
    private volatile String fecha;

    public MuestraNocturna(SfrDataClient sfr, CacheService cache) {
        this.sfr = sfr;
        this.sello = cache.sello(Caducidad.DESCARGA_DIARIA);   // la misma que su copia en disco (SfrDataClient.diario)
    }

    public String fecha() { return fecha; }

    /**
     * La muestra de anteayer (misma forma que muestra()), que sfr-data publica desde la 1.4 en el mismo archivo. null si el
     * archivo es anterior o faltó una noche: quien la use sigue solo con la de ayer, como hasta ahora. Carga como muestra().
     */
    public Map<String, List<List<Object>>> anteayer() {
        Carga c = cargar();
        return c == null ? null : c.anteayer();
    }

    /** tramo → partidas (cada una, una lista). null si no está disponible. */
    public Map<String, List<List<Object>>> muestra() {
        Carga c = cargar();
        return c == null ? null : c.muestra();
    }

    private Carga cargar() {
        Carga c = carga;
        if (c != null && sello.fresco()) return c;
        try {
            byte[] raw = sfr.diario(ARCHIVO, 60);   // un fallo de red sale tal cual: no hay copia que borrar
            Map<String, Object> m;
            try { m = leerGzJson(raw); if (m == null) throw new java.io.IOException("vacío (null): " + ARCHIVO); }
            catch (Exception ex) { sfr.olvidarDiario(ARCHIVO); throw ex; }
            c = new Carga(tramos(m.get("tramos")), m.get("tramos_anteayer") instanceof Map<?, ?> ? tramos(m.get("tramos_anteayer")) : null);
            carga = c; fecha = String.valueOf(m.get("fecha"));
            sello.marcar(sfr.fechaDiario(ARCHIVO));   // la edad, desde que se bajó
        } catch (Exception ex) { log("perfiles: muestra: " + causa(ex)); carga = null; c = null; sello.marcar(); }
        return c;
    }

    private static Map<String, List<List<Object>>> tramos(Object t) {
        Map<String, List<List<Object>>> out = new HashMap<>();
        if (t instanceof Map<?, ?> tm) for (Map.Entry<?, ?> en : tm.entrySet()) { List<List<Object>> l = new ArrayList<>(); for (Object o : arr(en.getValue())) l.add(arr(o)); out.put(String.valueOf(en.getKey()), l); }
        return out;
    }
}
