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
    private volatile Map<String, List<List<Object>>> muestra;
    private volatile String fecha;

    public MuestraNocturna(SfrDataClient sfr, CacheService cache) {
        this.sfr = sfr;
        this.sello = cache.sello(Caducidad.DESCARGA_DIARIA);   // la misma que su copia en disco (SfrDataClient.diario)
    }

    public String fecha() { return fecha; }

    /** tramo → partidas (cada una, una lista). null si no está disponible. */
    public Map<String, List<List<Object>>> muestra() {
        if (muestra != null && sello.fresco()) return muestra;
        try {
            byte[] raw = sfr.diario(ARCHIVO, 60);   // un fallo de red sale tal cual: no hay copia que borrar
            Map<String, Object> m;
            try { m = leerGzJson(raw); if (m == null) throw new java.io.IOException("vacío (null): " + ARCHIVO); }
            catch (Exception ex) { sfr.olvidarDiario(ARCHIVO); throw ex; }
            Map<String, List<List<Object>>> out = new HashMap<>();
            if (m.get("tramos") instanceof Map<?, ?> tm) for (Map.Entry<?, ?> en : tm.entrySet()) { List<List<Object>> l = new ArrayList<>(); for (Object o : arr(en.getValue())) l.add(arr(o)); out.put(String.valueOf(en.getKey()), l); }
            muestra = out; fecha = String.valueOf(m.get("fecha"));
            sello.marcar(sfr.fechaDiario(ARCHIVO));   // la edad, desde que se bajó
        } catch (Exception ex) { log("perfiles: muestra: " + causa(ex)); muestra = null; sello.marcar(); }
        return muestra;
    }
}
