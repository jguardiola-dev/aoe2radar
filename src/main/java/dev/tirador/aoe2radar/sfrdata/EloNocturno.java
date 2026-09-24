package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.Sello;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.leerGzJson;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * El ELO de anoche y el de hace 7 días, del volcado nocturno de sfr-data (forma por resta, índice de nombres).
 * cargar() lo baja si hace falta:
 * <ul>
 * <li>Si llegó bien, vale 6 h (Caducidad.NOCTURNO).</li>
 * <li>Si falló, se reintenta a los 5 min (Caducidad.REINTENTO), en segundo plano: quien llama sigue al momento con lo
 *     que haya y no se come la espera de la red. Mientras falte, la forma y el ELO salen del companion.</li>
 * <li>La primera carga y el refresco de 6 h van en el hilo de quien llama (como en la 1.1).</li>
 * <li>Si el archivo del disco no se entiende, se borra para que el siguiente intento vuelva a la red.</li>
 * </ul>
 * El de hace 7 días es opcional: si falta, se anota en el log y no cuenta como fallo.
 */
public final class EloNocturno {
    /** pid → {elo1v1, partidas1v1, eloEq, partidasEq}. */
    public final Map<Long, int[]> ayer = new ConcurrentHashMap<>();
    /** pid → {nombre, país}. */
    public final Map<Long, String[]> nombres = new ConcurrentHashMap<>();
    /** pid → {elo1v1, partidas1v1, eloEq, partidasEq}, de hace 7 días. */
    public final Map<Long, int[]> hace7 = new ConcurrentHashMap<>();

    private final SfrDataClient sfr;
    private final Executor segundoPlano;
    private final Supplier<LocalDate> hoyUtc;
    private final Sello exito, intento;
    private final AtomicBoolean cargando = new AtomicBoolean();
    private volatile boolean fallo;   // el último intento no trajo el ELO de ayer
    private volatile String fechaAyer, fechaHace7;

    public EloNocturno(SfrDataClient sfr, CacheService cache, Executor segundoPlano, Supplier<LocalDate> hoyUtc) {
        this.sfr = sfr; this.segundoPlano = segundoPlano; this.hoyUtc = hoyUtc;
        this.exito = cache.sello(Caducidad.NOCTURNO);
        this.intento = cache.sello(Caducidad.REINTENTO);
    }

    public String fechaAyer() { return fechaAyer; }
    public String fechaHace7() { return fechaHace7; }

    /** Carga si hace falta (ver la clase). Seguro entre hilos: si otro ya está cargando, vuelve sin hacer nada. */
    public void cargar() {
        if (exito.fresco() || intento.fresco()) return;     // atajo barato
        if (!cargando.compareAndSet(false, true)) return;   // atómico: dos llamadores a la vez no descargan dos veces
        if (exito.fresco() || intento.fresco()) { cargando.set(false); return; }   // se vuelve a mirar con el candado: otro pudo acabar justo ahora
        if (fallo) {
            try { segundoPlano.execute(this::descargar); }
            catch (Throwable t) { cargando.set(false); throw t; }   // si no arranca, no se queda «cargando» para siempre
            return;
        }
        descargar();
    }

    /** La descarga en sí (cargando ya está a true); en el finally se apunta si llegó, el intento y el fin, en ese orden. */
    private void descargar() {
        boolean llego = false;
        try {
            llego = leerAyer();
            String h7 = hoyUtc.get().minusDays(7).toString();
            try {
                if (leer("elo-" + h7 + ".json.gz", hace7, null)) fechaHace7 = h7;
            } catch (Exception ex) { log("perfiles: elo hace 7: " + causa(ex)); }
            log("perfiles: elo_ayer " + fechaAyer + ": " + ayer.size() + " jugadores; hace 7: " + hace7.size());
        } catch (Exception ex) { log("perfiles: elo_ayer: " + causa(ex)); }
        finally { fallo = !llego; intento.marcar(); cargando.set(false); }
    }

    private boolean leerAyer() throws Exception {
        String[] fecha = new String[1];
        if (!leer("elo_ayer.json.gz", ayer, fecha)) return false;
        fechaAyer = fecha[0];
        exito.marcar();   // solo si llegó: un fallo no bloquea 6 h
        return true;
    }

    /**
     * Baja (o lee del disco) un volcado de ELO y lo vuelca en destino (y los nombres, si fecha != null: es el de ayer).
     * false si no trae el mapa «j». Si el archivo está roto (no se descomprime o no trae «j»), borra la copia del
     * disco. Una fila rara en un archivo legible sale como excepción sin borrar nada (como en la 1.1): la release no va
     * a cambiar por volver a bajarla.
     */
    private boolean leer(String archivo, Map<Long, int[]> destino, String[] fecha) throws Exception {
        byte[] raw = sfr.diario(archivo, 60);   // un fallo de red sale tal cual: no hay copia que borrar
        Map<String, Object> m;
        try { m = leerGzJson(raw); }
        catch (Exception ex) { sfr.olvidarDiario(archivo); throw ex; }
        if (!(m.get("j") instanceof Map<?, ?> jm)) { sfr.olvidarDiario(archivo); return false; }
        destino.clear();
        if (fecha != null) nombres.clear();
        for (Map.Entry<?, ?> en : jm.entrySet()) {
            List<Object> v = arr(en.getValue());
            long pid = Long.parseLong(String.valueOf(en.getKey()));
            destino.put(pid, new int[]{ (int) lng(v.get(0)), (int) lng(v.get(1)), (int) lng(v.get(2)), (int) lng(v.get(3)) });
            if (fecha != null) nombres.put(pid, new String[]{ String.valueOf(v.get(4)), String.valueOf(v.get(5)) });
        }
        if (fecha != null) fecha[0] = String.valueOf(m.get("fecha"));
        return true;
    }
}
