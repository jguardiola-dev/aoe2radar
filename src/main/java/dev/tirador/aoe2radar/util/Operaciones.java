package dev.tirador.aoe2radar.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Un freno por operación (decisión de Jorge, 1.3). Antes había un solo freno para todo (api.Cancelacion:
 * stopOperacion/hiloOperacion/opEnCurso): el «Detener» de una descarga empezada durante una búsqueda paraba
 * también la búsqueda, y la operación siguiente soltaba el freno que la anterior aún no había visto.
 * <p>
 * Ahora cada operación cancelable (buscar, descargar, al azar, Guess the ELO, «Ver forma», cuentas vinculadas)
 * recibe su propio {@link Freno} al empezar, con el número de operación de la barra de estado. El «Detener» de la
 * barra para SOLO la más reciente que sigue viva; cuando esa termina, pasa a la anterior viva, y si no queda
 * ninguna, se oculta ({@link #estadoDetener()}). Los botones propios (la × de «Partidas de:», «Buscar partidas»
 * convertido en «Detener») paran su propia operación por número ({@link #detener(long)}).
 * <p>
 * El freno de la red (api.Cancelacion.detieneEsteHilo, que miran el ApiClient y la pausa cooperativa dormir())
 * pregunta por el HILO: el trabajo de fondo de cada operación se apunta al empezar ({@link #anotarHilo(long)}) y
 * se borra al acabar ({@link #soltarHilo()}), así que un barrido de Live now o de Twitch que espere en el freno
 * nunca se entera de un Detener que no es suyo, aunque reutilice un hilo del pool.
 * <p>
 * Hilos: la lista de vivas y el historial se protegen con el monitor de este objeto (la barra los toca en el EDT;
 * los trabajos de fondo leen). {@link Freno#detenido()} es volatile (lo escribe el EDT, lo lee el hilo de fondo).
 * El mapa hilo→freno es concurrente: cada hilo de fondo escribe solo su propia entrada.
 * Sin Swing ni red: se prueba con JUnit normal.
 */
public final class Operaciones {

    /** El de la app: lo comparten la barra de estado (ui.BarraEstado), el freno de la red (api.Cancelacion) y el
     *  cableado de las vistas. Los tests crean el suyo. */
    public static final Operaciones GLOBAL = new Operaciones();

    /** Cuántas operaciones terminadas se recuerdan para {@link #detenido(long)} (un done() que pregunta tarde). */
    private static final int HISTORIAL = 64;

    /** El freno de una operación: su número y si le han pedido parar. */
    public static final class Freno {
        private final long id;
        private volatile boolean detenido;

        Freno(long id) { this.id = id; }

        public long id() { return id; }
        public boolean detenido() { return detenido; }
    }

    /** Lo que pinta la barra: si «Detener» (y el progreso) se ven, si se puede pulsar y a qué operación para. */
    public record EstadoDetener(boolean visible, boolean habilitado, long objetivo) { }

    private final List<Freno> vivas = new ArrayList<>();                 // en orden de inicio; monitor: this
    private final Map<Long, Freno> recientes = new LinkedHashMap<>() {   // vivas y terminadas hace poco; monitor: this
        // nunca expulsa una viva: una descarga larga que ve empezar 64 operaciones sigue sabiendo si la paran
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Freno> e) { return size() > HISTORIAL && !vivas.contains(e.getValue()); }
    };
    private final Map<Thread, Freno> porHilo = new ConcurrentHashMap<>();

    /** Empieza la operación {@code id}: freno suelto, la más reciente de las vivas. */
    public synchronized void empezar(long id) {
        Freno f = new Freno(id);
        vivas.add(f);
        recientes.put(id, f);
    }

    /** La operación {@code id} terminó: deja de contar para «Detener». Devuelve true si seguía viva (repetirlo no
     *  hace nada). Su freno se recuerda un rato: {@link #detenido(long)} sigue respondiendo. */
    public synchronized boolean terminar(long id) {
        return vivas.removeIf(f -> f.id == id);
    }

    /** ¿Queda alguna operación viva? */
    public synchronized boolean hayVivas() {
        return !vivas.isEmpty();
    }

    /** El «Detener» de la barra: para la más reciente que sigue viva. Devuelve su número, o -1 si no hay ninguna. */
    public synchronized long detenerUltima() {
        if (vivas.isEmpty()) return -1;
        Freno f = vivas.get(vivas.size() - 1);
        f.detenido = true;
        return f.id;
    }

    /** Para la operación {@code id} (sus propios botones: la × de «Partidas de:», «Buscar partidas»/«Detener»),
     *  sea o no la más reciente. Devuelve false si no se conoce. */
    public synchronized boolean detener(long id) {
        Freno f = recientes.get(id);
        if (f == null) return false;
        f.detenido = true;
        return true;
    }

    /** ¿Le han pedido parar a la operación {@code id}? (También después de terminar, si fue hace poco.) */
    public synchronized boolean detenido(long id) {
        Freno f = recientes.get(id);
        return f != null && f.detenido;
    }

    /** Qué pinta la barra: sin vivas, nada; con vivas, «Detener» apunta a la más reciente y se puede pulsar si a
     *  esa aún no le han pedido parar (si ya se pidió, espera a que termine y entonces pasa a la anterior). */
    public synchronized EstadoDetener estadoDetener() {
        if (vivas.isEmpty()) return new EstadoDetener(false, false, -1);
        Freno f = vivas.get(vivas.size() - 1);
        return new EstadoDetener(true, !f.detenido, f.id);
    }

    /** El trabajo de fondo de la operación {@code id} corre en ESTE hilo (al empezar su doInBackground). */
    public void anotarHilo(long id) {
        Freno f;
        synchronized (this) { f = recientes.get(id); }
        if (f != null) porHilo.put(Thread.currentThread(), f);
    }

    /** Este hilo ya no trabaja para ninguna operación (al acabar su doInBackground, en un finally). */
    public void soltarHilo() {
        porHilo.remove(Thread.currentThread());
    }

    /** ¿Detener va por ESTE hilo? Solo si es el trabajo de fondo de una operación a la que han pedido parar. */
    public boolean detieneEsteHilo() {
        Freno f = porHilo.get(Thread.currentThread());
        return f != null && f.detenido;
    }
}
