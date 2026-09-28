package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.Sello;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.leerGzJson;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Las «chispas» de sfr-data (1.4): los últimos ratings 1v1 RM de cada jugador activo, hasta anoche, para la mini gráfica
 * de la tarjeta del hover sin pedir el perfil ni sus partidas a la API. Formato (chispas.json.gz, release «perfiles»):
 * {"v":1, "fecha", "completo", "j": {pid: [rating más viejo, dif, dif…]}, "x": {pid: [pico, ganadas, perdidas]}} (-1 = no
 * se sabe). «completo» = la chispa sale del año entero del jugador: una corta es de verdad «pocos 1v1». Si es parcial (las
 * primeras noches), una chispa corta no dice nada y hay que preguntar a la API como antes.
 * <p>Se baja solo la primera vez que alguien la pide (cerca de 2,5 MB) y vale lo que su copia en disco, 12 h desde que se
 * bajó, como MuestraNocturna. A diferencia de la muestra (un botón), esto lo dispara el ratón: un fallo también caduca.
 * Si falla, null durante 5 min (Caducidad.REINTENTO) sin volver a la red, y una sola descarga a la vez (quien llega
 * mientras otro descarga recibe lo que haya, null la primera vez). Una copia que no se entiende se borra.
 * Sin Swing; puede ir a la red: nunca desde el EDT.
 */
public final class ChispasNocturnas {
    static final String ARCHIVO = "chispas.json.gz";

    /** La chispa de un jugador: serie cronológica (vieja → nueva, vacía si no está), extra {pico, ganadas, perdidas} o null.
     *  Los arrays son los de la caché compartida: de solo lectura (no se copian en cada hover). */
    public record Chispa(int[] serie, int[] extra, boolean completo) { }

    private record Datos(Map<Long, int[]> series, Map<Long, int[]> extra, boolean completo) { }

    private final SfrDataClient sfr;
    private final Sello exito, intento;
    private final AtomicBoolean cargando = new AtomicBoolean();
    private volatile Datos datos;

    public ChispasNocturnas(SfrDataClient sfr, CacheService cache) {
        this.sfr = sfr;
        this.exito = cache.sello(Caducidad.DESCARGA_DIARIA);   // la misma que su copia en disco (SfrDataClient.diario)
        this.intento = cache.sello(Caducidad.REINTENTO);       // tras un fallo, 5 min sin volver a la red
    }

    /** La chispa de pid; null si el archivo no está disponible (aún no se publica, falla la red o no se entiende). */
    public Chispa chispa(long pid) {
        Datos d = cargar();
        if (d == null) return null;
        int[] s = d.series().get(pid);
        return new Chispa(s == null ? new int[0] : s, d.extra().get(pid), d.completo());
    }

    private Datos cargar() {
        Datos d = datos;
        if (d != null && exito.fresco()) return d;
        if (intento.fresco()) return d;                        // falló hace menos de 5 min: nada de red
        if (!cargando.compareAndSet(false, true)) return d;   // otro hover ya está descargando
        try {
            byte[] raw = sfr.diario(ARCHIVO, 60);   // un fallo de red sale tal cual: no hay copia que borrar
            Map<String, Object> m;
            try {
                m = leerGzJson(raw);
                if (m == null || lng(m.get("v")) != 1 || !(m.get("j") instanceof Map<?, ?>)) throw new java.io.IOException("formato desconocido: " + ARCHIVO);
            } catch (Exception ex) { sfr.olvidarDiario(ARCHIVO); throw ex; }
            Map<Long, int[]> series = new HashMap<>();
            for (Map.Entry<?, ?> en : ((Map<?, ?>) m.get("j")).entrySet()) series.put(Long.parseLong(String.valueOf(en.getKey())), decodificar(arr(en.getValue())));
            Map<Long, int[]> extra = new HashMap<>();
            if (m.get("x") instanceof Map<?, ?> xm) for (Map.Entry<?, ?> en : xm.entrySet()) {
                List<Object> v = arr(en.getValue());
                if (v.size() >= 3) extra.put(Long.parseLong(String.valueOf(en.getKey())), new int[]{ (int) lng(v.get(0)), (int) lng(v.get(1)), (int) lng(v.get(2)) });
            }
            d = new Datos(series, extra, Boolean.TRUE.equals(m.get("completo")));
            datos = d;
            exito.marcar(sfr.fechaDiario(ARCHIVO));   // la edad, desde que se bajó
            return d;
        } catch (Exception ex) { log("perfiles: chispas: " + causa(ex)); datos = null; intento.marcar(); return null; }
        finally { cargando.set(false); }
    }

    /** [r0, d1, d2…] → [r0, r0+d1, r0+d1+d2…]. */
    static int[] decodificar(List<Object> cod) {
        int[] out = new int[cod.size()];
        for (int i = 0; i < out.length; i++) out[i] = (int) lng(cod.get(i)) + (i == 0 ? 0 : out[i - 1]);
        return out;
    }
}
