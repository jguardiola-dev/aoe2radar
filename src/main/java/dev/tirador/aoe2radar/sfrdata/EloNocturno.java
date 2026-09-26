package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.Sello;

import java.time.LocalDate;
import java.util.HashMap;
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
 * <li>Si llegó bien, vale lo que su copia en disco: 12 h desde que se bajó (Caducidad.DESCARGA_DIARIA). Un solo reloj:
 *     si la memoria contara desde la lectura, al reiniciar la app se sumarían las dos caducidades (hasta 18 h en la 1.1,
 *     que recargaba cada 6 h de la misma copia).</li>
 * <li>Si falló, se reintenta a los 5 min (Caducidad.REINTENTO), en segundo plano: quien llama sigue al momento con lo
 *     que haya y no se come la espera de la red. Mientras falte, la forma y el ELO salen del companion.</li>
 * <li>La primera carga y el refresco tras un éxito van en el hilo de quien llama (como en la 1.1).</li>
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
    private volatile LocalDate diaCarga;   // día UTC de la última carga buena: el de hace 7 días depende de él

    public EloNocturno(SfrDataClient sfr, CacheService cache, Executor segundoPlano, Supplier<LocalDate> hoyUtc) {
        this.sfr = sfr; this.segundoPlano = segundoPlano; this.hoyUtc = hoyUtc;
        this.exito = cache.sello(Caducidad.DESCARGA_DIARIA);   // la misma que su copia en disco (SfrDataClient.diario)
        this.intento = cache.sello(Caducidad.REINTENTO);
    }

    public String fechaAyer() { return fechaAyer; }
    public String fechaHace7() { return fechaHace7; }

    /**
     * ¿Vale lo que hay? Sí mientras la copia no caduque Y siga siendo el mismo día UTC: el archivo de hace 7 días lleva
     * la fecha en el nombre, y al cambiar el día hay que pedir el nuevo (el de ayer se relee del disco, sin red).
     * O si hubo un intento hace menos de 5 min.
     */
    private boolean vigente() {
        return (exito.fresco() && hoyUtc.get().equals(diaCarga)) || intento.fresco();
    }

    /** Carga si hace falta (ver la clase). Seguro entre hilos: si otro ya está cargando, vuelve sin hacer nada. */
    public void cargar() {
        if (vigente()) return;                              // atajo barato
        if (!cargando.compareAndSet(false, true)) return;   // atómico: dos llamadores a la vez no descargan dos veces
        if (vigente()) { cargando.set(false); return; }     // se vuelve a mirar con el candado: otro pudo acabar justo ahora
        if (fallo) {
            try { segundoPlano.execute(this::descargar); }
            catch (Throwable t) { cargando.set(false); throw t; }   // si no arranca, no se queda «cargando» para siempre
            return;
        }
        descargar();
    }

    /**
     * La descarga en sí (cargando ya está a true); en el finally se apunta si llegó, el intento y el fin, en ese orden.
     * Una interrupción (cancelar la operación) no es un fallo del servidor: el hilo conserva su marca para que quien
     * llama se detenga, y si el corte llega antes de terminar de leer el de ayer no se apunta intento (la siguiente llamada lo pide en línea,
     * sin 5 min de espera en los que la forma saldría de la API).
     */
    private void descargar() {
        boolean llego = false, ayerLeido = false, cortado = false;
        LocalDate hoy = hoyUtc.get();
        try {
            llego = leerAyer();
            ayerLeido = true;   // llegara o no (sin «j»), el de ayer ya se intentó: un corte después no lo borra
            if (llego) diaCarga = hoy;
            String h7 = hoy.minusDays(7).toString();
            try {
                if (leer("elo-" + h7 + ".json.gz", hace7, null)) fechaHace7 = h7;
            } catch (InterruptedException ex) { throw ex; }
            catch (Exception ex) { log("perfiles: elo hace 7: " + causa(ex)); }
            log("perfiles: elo_ayer " + fechaAyer + ": " + ayer.size() + " jugadores; hace 7: " + hace7.size());
        } catch (InterruptedException ex) {
            cortado = true;
            Thread.currentThread().interrupt();
            log(ayerLeido ? "perfiles: elo hace 7: detenido" : "perfiles: elo_ayer: detenido");
        } catch (Exception ex) { log("perfiles: elo_ayer: " + causa(ex)); }
        finally {
            if (ayerLeido || !cortado) { fallo = !llego; intento.marcar(); }
            cargando.set(false);
        }
    }

    private boolean leerAyer() throws Exception {
        String[] fecha = new String[1];
        if (!leer("elo_ayer.json.gz", ayer, fecha)) return false;
        fechaAyer = fecha[0];
        exito.marcar(sfr.fechaDiario("elo_ayer.json.gz"));   // solo si llegó (un fallo no bloquea); la edad, desde que se bajó
        return true;
    }

    /**
     * Baja (o lee del disco) un volcado de ELO y lo vuelca en destino (y los nombres, si fecha != null: es el de ayer).
     * false si no trae el mapa «j». Si el archivo está roto (no se descomprime o no trae «j»), borra la copia del
     * disco. Una fila rara en un archivo legible sale como excepción sin borrar nada (como en la 1.1): la release no va
     * a cambiar por volver a bajarla.
     * <p>El parseo se hace en un mapa aparte y solo al final se vuelca en destino con {@code retainAll} + {@code
     * putAll} (destino y nombres son públicos y compartidos: no se puede cambiar la referencia por una nueva). Así
     * quien lee ayer/hace7 mientras se refresca nunca los ve vacíos a medias, como pasaba con destino.clear() antes
     * del bucle (fila 73 de DEUDA); y si el parseo falla a mitad, destino conserva lo que ya tenía.
     */
    private boolean leer(String archivo, Map<Long, int[]> destino, String[] fecha) throws Exception {
        byte[] raw = sfr.diario(archivo, 60);   // un fallo de red sale tal cual: no hay copia que borrar
        Map<String, Object> m;
        try { m = leerGzJson(raw); if (m == null) throw new java.io.IOException("vacío (null): " + archivo); }
        catch (Exception ex) { sfr.olvidarDiario(archivo); throw ex; }
        if (!(m.get("j") instanceof Map<?, ?> jm)) { sfr.olvidarDiario(archivo); return false; }
        Map<Long, int[]> nuevo = new HashMap<>();
        Map<Long, String[]> nuevosNombres = fecha != null ? new HashMap<>() : null;
        for (Map.Entry<?, ?> en : jm.entrySet()) {
            List<Object> v = arr(en.getValue());
            long pid = Long.parseLong(String.valueOf(en.getKey()));
            nuevo.put(pid, new int[]{ (int) lng(v.get(0)), (int) lng(v.get(1)), (int) lng(v.get(2)), (int) lng(v.get(3)) });
            if (fecha != null) nuevosNombres.put(pid, new String[]{ String.valueOf(v.get(4)), String.valueOf(v.get(5)) });
        }
        destino.keySet().retainAll(nuevo.keySet());
        destino.putAll(nuevo);
        if (fecha != null) {
            nombres.keySet().retainAll(nuevosNombres.keySet());
            nombres.putAll(nuevosNombres);
            fecha[0] = String.valueOf(m.get("fecha"));
        }
        return true;
    }
}
