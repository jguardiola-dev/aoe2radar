package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.util.Reloj;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Caché en memoria de respuestas de la API por URL completa, con caducidad por entrada y tamaño acotado (LRU: al
 * pasar del máximo sale la menos usada). Guarda el TEXTO de la respuesta, no el objeto leído: cada lectura vuelve a
 * hacer Json.parse y recibe su propio mapa, así ningún llamador puede tocar lo que ve otro.
 *
 * Solo se guarda lo que el llamador le da (CompanionApi solo le da respuestas 2xx: los errores salen como excepción
 * antes de llegar aquí). Seguro entre hilos con un monitor (las operaciones son de microsegundos). Dos hilos que
 * piden a la vez una URL que no está la piden los dos a la red: la caché no bloquea a nadie mientras se descarga.
 */
public final class CacheRespuestas {
    private record Entrada(String cuerpo, long caducaMs) { }

    private final Reloj reloj;
    private final Map<String, Entrada> mapa;
    private final LongAdder aciertos = new LongAdder();

    public CacheRespuestas(Reloj reloj, int maximo) {
        if (maximo < 1) throw new IllegalArgumentException("maximo < 1");
        this.reloj = reloj;
        this.mapa = new LinkedHashMap<>(16, 0.75f, true) {   // true: orden de acceso, para que la eldest sea la menos usada
            @Override protected boolean removeEldestEntry(Map.Entry<String, Entrada> e) { return size() > maximo; }
        };
    }

    /** El cuerpo guardado para url si no ha caducado; si caducó, lo quita y da null. */
    public synchronized String vigente(String url) {
        Entrada e = mapa.get(url);
        if (e == null) return null;
        if (reloj.ahoraMs() >= e.caducaMs()) { mapa.remove(url); return null; }
        aciertos.increment();
        return e.cuerpo();
    }

    /** Guarda el cuerpo de url durante ttlMs a partir de ahora (sustituye lo que hubiera). */
    public synchronized void poner(String url, String cuerpo, long ttlMs) {
        mapa.put(url, new Entrada(Objects.requireNonNull(cuerpo), reloj.ahoraMs() + ttlMs));
    }

    /** Olvida url (la próxima lectura irá a la red). */
    public synchronized void invalidar(String url) { mapa.remove(url); }

    /** Cuántas entradas hay (vigentes o caducadas aún no leídas). */
    public synchronized int tamano() { return mapa.size(); }

    /** Cuántas lecturas se sirvieron desde la caché desde que se creó (llamadas que no salieron a la red). */
    public long aciertos() { return aciertos.sum(); }
}
