package dev.tirador.aoe2radar.cache;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caché en memoria por clave con caducidad. Dos lecturas, porque la app las necesita las dos:
 * vigente(k) da el valor solo si no ha caducado (para decidir si se vuelve a pedir); ultimo(k) da lo último que se
 * supo aunque haya caducado (para pintar un ELO conocido mientras llega el nuevo). Nada se borra al caducar, igual
 * que las cachés de la 1.1. Seguro entre hilos; dos hilos que piden lo mismo a la vez pueden cargarlo los dos (como
 * en la 1.1: la carga la hace el llamador, no la caché).
 */
public final class CacheMemoria<K, V> {
    private record Entrada<V>(V valor, long selloMs) { }

    private final CacheService cache;
    private final Duration caducidad;
    private final Map<K, Entrada<V>> mapa = new ConcurrentHashMap<>();

    CacheMemoria(CacheService cache, Duration caducidad) { this.cache = cache; this.caducidad = caducidad; }

    /** El valor de k si lo hay y no ha caducado; si no, null. */
    public V vigente(K k) {
        Entrada<V> e = mapa.get(k);
        return e != null && cache.fresco(e.selloMs(), caducidad) ? e.valor() : null;
    }

    /** Lo último que se guardó para k, haya caducado o no; null si nunca se guardó. */
    public V ultimo(K k) {
        Entrada<V> e = mapa.get(k);
        return e == null ? null : e.valor();
    }

    /** Guarda v para k con la hora de ahora (no admite null: «no hay» es no guardar). */
    public void poner(K k, V v) { mapa.put(k, new Entrada<>(Objects.requireNonNull(v), cache.reloj().ahoraMs())); }

    /** Da por caducado lo guardado para k: vigente(k) pasa a null, pero ultimo(k) lo sigue dando (para enseñar algo si
     *  la próxima lectura falla). Sin nada guardado, no hace nada. */
    public void caducar(K k) { mapa.computeIfPresent(k, (clave, e) -> new Entrada<>(e.valor(), Long.MIN_VALUE / 2)); }
}
