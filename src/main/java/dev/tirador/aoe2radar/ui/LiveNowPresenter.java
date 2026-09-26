package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.service.ConsultasLadder;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.util.Reloj;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.tirador.aoe2radar.service.ReglasPartida.enCursoReal;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * La lógica de Live now sin Swing: el barrido periódico (ahoraRefrescar de la 1.1) y el evento del socket
 * (liveEvento). Sale tal cual de SpoilerFreeRecs, salvo que ya no toca los campos de Swing directamente: se los
 * pide a {@link Pantalla}, que implementa ui.LiveNowView.
 * <p>Estado delicado: {@code ahoraTop}, {@code ahoraEnCurso} y {@code liveTerminadas} viven aquí (no son Swing) con
 * los MISMOS candados que en la 1.1 y el MISMO orden: {@code liveTerminadas} → {@code ahoraEnCurso} → EstadoVivo
 * (que es una hoja, con su propio candado interno). Nadie los coge al revés. {@link #liveEvento} se puede llamar
 * desde cualquier hilo (el socket lo hace desde uno de fondo): sus escrituras van con {@code synchronized} y, si
 * toca repintar, lo hace con {@code tareas.enUi(...)} (en la app, {@code SwingUtilities.invokeLater}), nunca en el
 * hilo que llama.
 */
public final class LiveNowPresenter {

    /** Trae hasta porPagina partidas recientes de los pids del CSV, tal cual las da la API (LiveService.partidas):
     *  un puerto mínimo para poder probar el presentador con un doble, sin tocar la red. */
    public interface Buscador {
        Iterable<Match> partidas(String pidsCsv, int pagina, int porPagina) throws Exception;
    }

    /** Lo que el presentador necesita de la vista. */
    public interface Pantalla {
        /** Mensaje de estado de Live now (incluido el progreso por lotes: «Consultando… 15 / 250»). */
        void estado(String texto);
        /** Repinta las tarjetas con el estado actual (ahoraPintar de la vista). */
        void pintar();
        /** La fuente elegida (top/país/clan/grupo): ver LiveNowView.cargarFuenteLive (delega en service.Campanas). */
        List<Object[]> cargarFuenteLive() throws Exception;
        /** ¿pid está en alguna vista con campana? (no se suelta del socket aunque salga del top). */
        boolean campanaContiene(long pid);
        /** Tras cambiar la lista vigilada, hay que resuscribir el socket con los ids nuevos. */
        void sincronizarSocket();
        /** ¿La pestaña sigue abierta? El barrido por lotes se corta antes de la siguiente llamada si no. */
        boolean abierta();
        /** ¿Toca repintar ahora mismo? (abierta y con el panel visible). Se mira ANTES de programar el repintado,
         *  en el mismo hilo que llama a liveEvento, igual que hacía la 1.1. */
        boolean puedeRepintar();
    }

    private final Buscador buscador;
    private final Tareas tareas;
    private final Pantalla pantalla;
    /** Quién está en partida ahora: antes se leía siempre del singleton EstadoVivo.SISTEMA (DEUDA, fila 125);
     *  se inyecta para poder probar el presentador con un EstadoVivo propio, sin tocar el de toda la app. */
    private final EstadoVivo estadoVivo;

    /** {pid, nombre, rating, rango, país} de la fuente elegida (ver Campanas.cargarFuenteLive). */
    private final List<Object[]> ahoraTop = new ArrayList<>();
    /** pid → partida en curso (vista). */
    private final Map<Long, Match> ahoraEnCurso = new HashMap<>();
    /** matchId → {Match, fin ms}. */
    private final Map<Long, Object[]> liveTerminadas = new LinkedHashMap<>();
    /** Ids extra para el socket (vistas con campana que ya no están en el top pero se siguen vigilando): el MISMO
     *  Set que la ventana usa en refrescarCampanas y en el bloque del socket (Campanas se queda allí). */
    private final Set<Long> socketExtra;
    private long ahoraTopMs, ahoraUltimaMs;
    /** ¿Hay un barrido en marcha? y ¿alguien pidió uno forzado mientras tanto? Los dos, bajo {@code candadoCarga}:
     *  así el barrido que acaba y la petición que llega no se cruzan (revisión 1.3, F7). */
    private volatile boolean ahoraCargando;
    private boolean repetir;
    private final Object candadoCarga = new Object();
    /** Sube con cada cambio de fuente (reiniciarFuente). Un barrido que empezó con otra generación no escribe nada:
     *  era de la fuente anterior (revisión 1.3, F7). */
    private volatile int generacion;

    /** El EstadoVivo se inyecta (DEUDA, fila 125): en la app, LiveNowView pasa EstadoVivo.SISTEMA explícitamente;
     *  en los tests, cada uno puede traer el suyo y no compartir el singleton de toda la app entre pruebas. */
    public LiveNowPresenter(Buscador buscador, Set<Long> socketExtra, Tareas tareas, Pantalla pantalla, EstadoVivo estadoVivo) {
        this(buscador, socketExtra, tareas, pantalla, estadoVivo, Reloj.SISTEMA);
    }

    /** Como el otro, con el reloj de la gracia del socket (los tests traen uno falso). */
    public LiveNowPresenter(Buscador buscador, Set<Long> socketExtra, Tareas tareas, Pantalla pantalla, EstadoVivo estadoVivo, Reloj reloj) {
        this.buscador = buscador;
        this.socketExtra = socketExtra;
        this.tareas = tareas;
        this.pantalla = pantalla;
        this.estadoVivo = estadoVivo;
        this.reloj = reloj;
    }

    // ----- suscripción del top al socket: se suelta con la pestaña cerrada (plan API de la 1.3) -----

    /** Cuánto sigue el top de Live now en el socket tras cerrar la pestaña. SocketVivo reconecta cada vez que cambia el
     *  conjunto de ids: sin esta gracia, entrar y salir de la pestaña costaría una reconexión (y su hueco) por vez. */
    public static final long GRACIA_SOCKET_MS = 5 * 60_000L;

    private final Reloj reloj;
    /** Cuándo se cerró la pestaña; -1 si está abierta o nunca se cerró. Lo escribe el EDT y lo lee el hilo del socket
     *  (idsSocket), por eso es volatile. */
    private volatile long cerradaMs = -1;
    /** ¿Ya se soltó el top del socket (y se olvidó «en curso»)? Solo lo tocan alAbrir y soltarSiToca, en el EDT. */
    private boolean soltado;

    /** ¿El top de la fuente sigue suscrito al socket? Sí con la pestaña abierta y durante GRACIA_SOCKET_MS tras cerrarla. */
    public boolean topSuscrito() {
        long c = cerradaMs;
        return c < 0 || reloj.ahoraMs() - c < GRACIA_SOCKET_MS;
    }

    /**
     * Los ids extra que el socket debe vigilar ahora (los pide EnlaceVivo.sincronizarSocket, vía la ventana): todo
     * socketExtra mientras el top siga suscrito; si no, solo los de alguna vista con campana, que no se sueltan nunca.
     * «Mi partida» (mi_pid) no pasa por aquí: lo añade EnlaceVivo aparte. Se filtra al leer, no al escribir: así quien
     * llena socketExtra (el barrido, refrescarCampanas) no tiene que saber si la pestaña está abierta. Seguro desde
     * cualquier hilo: socketExtra es concurrente y cerradaMs, volatile.
     */
    public Set<Long> idsSocket() {
        if (topSuscrito()) return socketExtra;
        Set<Long> conCampana = new HashSet<>();
        for (Long id : socketExtra) if (pantalla.campanaContiene(id)) conCampana.add(id);
        return conCampana;
    }

    /** La pestaña se cierra: empieza la gracia (la cuenta el primer cierre). Desde el EDT. */
    public void alCerrar() { if (cerradaMs < 0) cerradaMs = reloj.ahoraMs(); }

    /**
     * Pasada la gracia con la pestaña cerrada: el socket se resincroniza sin el top (idsSocket ya no lo da) y se olvida
     * «en curso». Motivo: sin socket nadie mantiene ese mapa, y el barrido de reapertura conserva lo que EstadoVivo aún
     * da por vivo (F2), pero EstadoVivo tampoco se entera ya de los finales de quien solo vigilaba Live now: sin
     * olvidarlo, una partida terminada con la pestaña cerrada seguiría «en curso» al volver y taparía la nueva. El
     * barrido de apertura (forzado: se olvida también su hora) lo rehace entero. No hace nada si la pestaña se reabrió
     * o la gracia no ha vencido. Desde el EDT (el Timer de LiveNowView).
     */
    public void soltarSiToca() {
        if (soltado || topSuscrito()) return;
        soltado = true;
        olvidarEnCurso();
        pantalla.sincronizarSocket();
    }

    /**
     * La pestaña se abre, antes del barrido de apertura: si el top estaba suelto, vuelve al socket (resincroniza con él).
     * Dentro de la gracia no cambia nada y el socket no se toca. Si la gracia venció pero el Timer aún no pasó, se hace
     * aquí lo que él habría hecho (olvidar «en curso»). Desde el EDT.
     */
    public void alAbrir() {
        boolean volver = soltado || !topSuscrito();
        if (volver && !soltado) olvidarEnCurso();
        soltado = false;
        cerradaMs = -1;
        if (volver) pantalla.sincronizarSocket();
    }

    private void olvidarEnCurso() {
        synchronized (ahoraEnCurso) { ahoraEnCurso.clear(); }
        ahoraUltimaMs = 0;   // el próximo refrescar(false) no se queda en «solo pintar»: barrido completo
    }

    /** La ficha {pid, nombre, rating, rango, país} de pid en la fuente actual, o null. Bajo el mismo candado que
     *  topSnapshot/conTop (DEUDA, fila 123: antes se recorría ahoraTop sin candado, con riesgo de
     *  ConcurrentModificationException si el barrido lo estaba reescribiendo a la vez). */
    public Object[] ficha(long pid) {
        synchronized (ahoraTop) {
            for (Object[] f : ahoraTop) if ((Long) f[0] == pid) return f;
        }
        return null;
    }

    /** ¿La ficha {pid, nombre, rating, rango, país} tiene puesto en el ladder 1v1? Rango 0 = sin puesto: la fuente
     *  «Grupo» no lo sabe (Campanas.cargarFuenteLive pone 0). Sin puesto no se pinta «#0» (revisión 1.3, F3). */
    public static boolean conPuesto(Object[] ficha) { return ficha != null && (Integer) ficha[3] > 0; }

    /** ¿La ficha está entre los «hasta» primeros del ladder 1v1? Sin puesto (rango 0) nunca: si no, un grupo entero
     *  pasaba por «top 50 vs top 50» y por el filtro «Solo top contra top» (revisión 1.3, F3). */
    public static boolean enTop(Object[] ficha, int hasta) { return conPuesto(ficha) && (Integer) ficha[3] <= hasta; }

    public long ultimaMs() { return ahoraUltimaMs; }

    public boolean cargando() { return ahoraCargando; }

    /** Copia de la fuente actual (para pintar sin tener el candado cogido mientras se construyen las tarjetas). */
    public List<Object[]> topSnapshot() { synchronized (ahoraTop) { return new ArrayList<>(ahoraTop); } }

    /** Quiénes están «en partida» en matchId según Live now (vacío si nadie). Bajo el candado de ahoraEnCurso: seguro
     *  desde cualquier hilo (lo llama el socket, revisión 1.3, F1). */
    public List<Long> jugadoresEn(long matchId) {
        List<Long> en = new ArrayList<>();
        synchronized (ahoraEnCurso) { for (Map.Entry<Long, Match> e : ahoraEnCurso.entrySet()) if (e.getValue() != null && e.getValue().id == matchId) en.add(e.getKey()); }
        return en;
    }

    /** Copia de las partidas en curso ahora mismo. */
    public Map<Long, Match> enCursoSnapshot() { synchronized (ahoraEnCurso) { return new HashMap<>(ahoraEnCurso); } }

    /** Las terminadas de las últimas 2 horas, tras limpiar las más viejas (mismo criterio que ahoraPintar en la 1.1). */
    public List<Object[]> terminadasVigentes() {
        synchronized (liveTerminadas) {
            liveTerminadas.values().removeIf(x -> System.currentTimeMillis() - (Long) x[1] > 2 * 3_600_000L);
            return new ArrayList<>(liveTerminadas.values());
        }
    }

    // ----- acceso directo con el MISMO candado, para RegresionCapturas (inyecta datos sin pasar por la red) -----

    /** Toca la fuente actual bajo su candado (el mismo que usa el barrido): visible para RegresionCapturas. */
    public void conTop(java.util.function.Consumer<List<Object[]>> accion) { synchronized (ahoraTop) { accion.accept(ahoraTop); } }

    /** Toca las partidas en curso bajo su candado: visible para RegresionCapturas. */
    public void conEnCurso(java.util.function.Consumer<Map<Long, Match>> accion) { synchronized (ahoraEnCurso) { accion.accept(ahoraEnCurso); } }

    /** Toca las terminadas bajo su candado: visible para RegresionCapturas. */
    public void conTerminadas(java.util.function.Consumer<Map<Long, Object[]>> accion) { synchronized (liveTerminadas) { accion.accept(liveTerminadas); } }

    /** Fuerza el instante del último barrido de la fuente (sin red): visible para RegresionCapturas. */
    public void fijarTopMs(long ms) { ahoraTopMs = ms; }

    /** Fuerza el instante del último barrido completo (sin red): visible para RegresionCapturas. */
    public void fijarUltimaMs(long ms) { ahoraUltimaMs = ms; }

    /** Al cambiar de fuente (top/país/clan/grupo, cambiarFuenteLive de la 1.1): se olvida todo lo cargado, para
     *  que el próximo {@link #refrescar} pida la lista entera y el barrido completo. La generación sube ANTES de
     *  vaciar: un barrido de la fuente anterior que aún esté en marcha ya no escribirá encima (F7). */
    public void reiniciarFuente() {
        generacion++;   // solo se llama desde el EDT (cambiarFuenteLive): no hay dos escritores
        synchronized (ahoraTop) { ahoraTop.clear(); }
        synchronized (ahoraEnCurso) { ahoraEnCurso.clear(); }
        synchronized (liveTerminadas) { liveTerminadas.clear(); }
        ahoraTopMs = 0; ahoraUltimaMs = 0;
    }

    /**
     * Carga la fuente (si tiene más de 30 min), comprueba por lotes de 15 quién está en partida y suscribe esos
     * ids al socket. Igual que ahoraRefrescar de la 1.1: el hilo se llama "live-now", y el circuito se corta a
     * los tres lotes fallidos seguidos.
     * <p>Revisión 1.3, F7: (1) un refrescar forzado que llega con otro barrido en marcha (cambio de fuente,
     * «Actualizar», reconexión del socket) no se pierde: se repite al acabar, desde el EDT. (2) Si la fuente cambió
     * durante el barrido, su resultado se descarta entero. (3) Un barrido cortado (pestaña cerrada, tres lotes
     * fallidos) o con algún lote fallido no cuenta como hecho: no se apunta su hora, y las partidas de los jugadores
     * que no se pudieron consultar se conservan como estaban (igual que vigilarTop ante un lote fallido).
     */
    public void refrescar(boolean forzar) {
        boolean soloPintar;
        synchronized (candadoCarga) {
            if (ahoraCargando) { if (forzar) repetir = true; return; }
            soloPintar = !forzar && System.currentTimeMillis() - ahoraUltimaMs < 60_000 && !ahoraTop.isEmpty();
            if (!soloPintar) ahoraCargando = true;
        }
        if (soloPintar) { pantalla.pintar(); return; }
        pantalla.estado(t("Consultando…", "Checking…"));
        final int gen = generacion;
        tareas.enFondo("live-now", () -> {
            try {
                if (ahoraTop.isEmpty() || System.currentTimeMillis() - ahoraTopMs > 30 * 60_000L) {
                    List<Object[]> top = pantalla.cargarFuenteLive();
                    synchronized (ahoraTop) { if (gen != generacion) return; ahoraTop.clear(); ahoraTop.addAll(top); }   // la fuente cambió mientras se cargaba: no se escribe
                    ahoraTopMs = System.currentTimeMillis();
                    Set<Long> ids = new HashSet<>(); for (Object[] f : top) ids.add((Long) f[0]);
                    socketExtra.removeIf(id -> !ids.contains(id) && !pantalla.campanaContiene(id));
                    socketExtra.addAll(ids);
                    tareas.enUi(pantalla::sincronizarSocket);   // el socket vigila también a los 250
                }
                Map<Long, Match> vivos = new HashMap<>();
                List<Object[]> top; synchronized (ahoraTop) { top = new ArrayList<>(ahoraTop); }
                final int LOTE = 15; int fallosSeguidos = 0;
                Set<Long> sinDatos = new HashSet<>();   // pids de lotes fallidos o que no se llegaron a consultar (F7)
                boolean cortadoPorFallos = false;
                for (int d = 0; d < top.size(); d += LOTE) {
                    if (gen != generacion) return;   // otra fuente: este barrido ya no sirve (el forzado que la trajo se repite al acabar)
                    if (!pantalla.abierta() || fallosSeguidos >= 3) {
                        if (fallosSeguidos >= 3) { log("live: tres lotes fallidos seguidos: barrido abortado hasta el próximo"); cortadoPorFallos = true; }
                        for (Object[] f : top.subList(d, top.size())) sinDatos.add((Long) f[0]);
                        break;
                    }
                    List<Object[]> lote = top.subList(d, Math.min(d + LOTE, top.size()));
                    StringBuilder csv = new StringBuilder(); Set<Long> ids = new HashSet<>();
                    for (Object[] f : lote) { if (csv.length() > 0) csv.append(','); csv.append((Long) f[0]); ids.add((Long) f[0]); }
                    try {
                        Iterable<Match> leidas = buscador.partidas(csv.toString(), 1, 100);
                        for (Match m : leidas) {
                            if (m == null) continue;
                            if (enCursoReal(m)) { for (MatchPlayer mp : m.players) if (ids.contains(mp.id) && !vivos.containsKey(mp.id)) vivos.put(mp.id, m); }
                            else if (m.finished != null && m.finished.isAfter(Instant.now().minus(Duration.ofHours(2)))) { synchronized (liveTerminadas) { if (gen == generacion) liveTerminadas.putIfAbsent(m.id, new Object[]{ m, m.finished.toEpochMilli() }); } }
                        }
                        fallosSeguidos = 0;
                    } catch (Exception ex) { fallosSeguidos++; sinDatos.addAll(ids); log("live: lote " + (d / LOTE + 1) + ": " + causa(ex)); }
                    final int hechos = Math.min(d + LOTE, top.size());
                    final int totalTop = top.size();
                    tareas.enUi(() -> pantalla.estado(t("Consultando… ", "Checking… ") + hechos + " / " + totalTop));
                }
                // una partida que se sabe terminada (socket, espectar) no vuelve con la foto del barrido. Candados
                // anidados en este orden (liveTerminadas → ahoraEnCurso → EstadoVivo, que es hoja): nadie los coge al revés
                synchronized (liveTerminadas) {
                    synchronized (ahoraEnCurso) {
                        if (gen != generacion) return;   // la fuente cambió en el último momento: nada de esto es suyo (F7)
                        vivos.values().removeIf(m -> liveTerminadas.containsKey(m.id) || estadoVivo.terminada(m.id));
                        // se fusiona, no se sustituye (revisión 1.3, F2): la foto del barrido es de antes; una partida
                        // que el socket confirmó entretanto (EstadoVivo la tiene como la de ese jugador y no terminó)
                        // se queda, aunque su lote ya se hubiera consultado. El socket no la volvería a mandar. Y los
                        // que no se pudieron consultar (F7) se quedan como estaban: no hay dato nuevo que los quite.
                        ahoraEnCurso.entrySet().removeIf(en -> yaTerminada(en.getValue()) || (!sinDatos.contains(en.getKey()) && !sigueSegunSocket(en.getKey(), en.getValue())));   // sin consultar no resucita una terminada
                        vivos.keySet().removeAll(ahoraEnCurso.keySet());   // para esos jugadores, el socket es más reciente que la foto
                        ahoraEnCurso.putAll(vivos);
                        if (sinDatos.isEmpty()) ahoraUltimaMs = System.currentTimeMillis();   // F7: cortado o con huecos no cuenta como hecho
                    }
                }
                for (Map.Entry<Long, Match> en : vivos.entrySet()) estadoVivo.guardarPartida(en.getKey(), en.getValue());
                tareas.enUi(pantalla::pintar);
                if (cortadoPorFallos) tareas.enUi(() -> pantalla.estado(t("Barrido incompleto: la API no respondió tres veces seguidas. Pulsa «Actualizar» para reintentar.",
                        "Incomplete sweep: the API didn't answer three times in a row. Press “Refresh” to retry.")));
            } catch (Exception ex) {
                tareas.enUi(() -> pantalla.estado(t("No se pudo consultar: ", "Couldn't check: ") + causa(ex)));
            } finally {
                boolean otra;
                synchronized (candadoCarga) { ahoraCargando = false; otra = repetir; repetir = false; }
                if (otra) tareas.enUi(() -> refrescar(true));   // F7: lo que se pidió durante el barrido, desde el EDT
            }
        });
    }

    /** ¿Se sabe ya que la partida m terminó (Live now o EstadoVivo)? Entonces no se conserva en «en curso» por ningún
     *  motivo, tampoco por no haber podido consultar a su jugador (revisión 1.3, menor del revisor). Con los candados
     *  de liveTerminadas y ahoraEnCurso cogidos (EstadoVivo es hoja). */
    private boolean yaTerminada(Match m) {
        return m == null || liveTerminadas.containsKey(m.id) || estadoVivo.terminada(m.id);
    }

    /** ¿La partida m de pid (de ahoraEnCurso) sigue en curso según el socket? Sí si EstadoVivo tiene a pid en esa misma
     *  partida y nadie la dio por terminada. Se llama con liveTerminadas y ahoraEnCurso cogidos (EstadoVivo es hoja). */
    private boolean sigueSegunSocket(long pid, Match m) {
        return m != null && Long.valueOf(m.id).equals(estadoVivo.matchDe(pid))
                && !liveTerminadas.containsKey(m.id) && !estadoVivo.terminada(m.id);
    }

    /**
     * Evento del socket sobre alguien del top actual: entra en partida o la termina. Seguro de llamar desde
     * CUALQUIER hilo (el socket lo hace desde uno de fondo): las escrituras van con synchronized y, si toca
     * repintar, se comprueba en el hilo que llama (igual que la 1.1) y el repintado se programa con
     * {@code tareas.enUi(...)}, nunca se ejecuta aquí mismo.
     */
    public void liveEvento(long pid, Match m, boolean terminada) {
        if (ficha(pid) == null) return;
        if (terminada) {
            if (m != null) { apuntarTerminada(pid, m.id, m); }
            else {   // sin partida: no se sabe cuál terminó; como antes (el socket ya pasa por liveTerminada con su id)
                Match viva = estadoVivo.soltarPartida(pid);
                if (viva != null && viva.id > 0) synchronized (liveTerminadas) { liveTerminadas.putIfAbsent(viva.id, new Object[]{ viva, viva.finished != null ? viva.finished.toEpochMilli() : System.currentTimeMillis() }); }
                synchronized (ahoraEnCurso) { ahoraEnCurso.remove(pid); }
            }
        } else if (m != null) {
            estadoVivo.guardarPartida(pid, m);
            synchronized (ahoraEnCurso) { ahoraEnCurso.put(pid, m); }
        }
        if (pantalla.puedeRepintar()) tareas.enUi(pantalla::pintar);
    }

    /**
     * La partida matchId de pid terminó (fin: la partida que dio la API, o null si no la dio). Solo se suelta y se quita
     * de «en curso» si es la partida actual de pid: si en el hueco el socket lo metió en otra, esa se queda (revisión
     * 1.3, F8 y menor del revisor). Sin fin, se usa la guardada al empezar. Seguro desde cualquier hilo, como liveEvento.
     */
    public void liveTerminada(long pid, long matchId, Match fin) {
        if (ficha(pid) == null) return;
        apuntarTerminada(pid, matchId, fin);
        if (pantalla.puedeRepintar()) tareas.enUi(pantalla::pintar);
    }

    private void apuntarTerminada(long pid, long matchId, Match fin) {
        Match viva = estadoVivo.soltarPartidaDe(pid, matchId);
        Match m = fin != null ? fin : viva;
        // con la hora de fin real si se sabe (la da la API, revisión 1.3, F5), como el barrido; si no, la de ahora
        if (m != null && m.id > 0) synchronized (liveTerminadas) { liveTerminadas.putIfAbsent(m.id, new Object[]{ m, m.finished != null ? m.finished.toEpochMilli() : System.currentTimeMillis() }); }
        synchronized (ahoraEnCurso) { Match actual = ahoraEnCurso.get(pid); if (actual == null || actual.id == matchId) ahoraEnCurso.remove(pid); }
    }

    /**
     * «resultado»: pide a la API el resultado real de la partida m (el socket avisa del final antes de que la
     * API lo tenga listo, así que solo se pide si el usuario lo pulsa). Actualiza el {@code won} de cada
     * MatchPlayer de m y llama a alListo en el EDT cuando termine. Movido tal cual desde LiveNowView (DEUDA,
     * fila 124): mismo nombre de hilo, misma llamada (CSV de un solo pid, igual que LiveService.partidas(long,…)),
     * mismo orden de pintado (alListo solo se llama al final, en el EDT).
     */
    public void pedirResultado(Match m, Runnable alListo) {
        long pid0 = m.players.get(0).id;
        tareas.enFondo("resultado", () -> {
            try {
                for (Match x : buscador.partidas(String.valueOf(pid0), 1, 5))
                    if (x != null && x.id == m.id) { for (MatchPlayer mp : m.players) for (MatchPlayer xp : x.players) if (xp.id == mp.id) mp.won = xp.won; break; }
            } catch (Exception ex) { log("resultado: " + causa(ex)); }
            tareas.enUi(alListo);
        });
    }

    /** «clanes»: si el catálogo de clanes del ladder aún no está en memoria, lo trae en un hilo demonio (nadie
     *  espera el resultado: la próxima vez que el usuario escriba ya estará). Movido tal cual desde LiveNowView
     *  (DEUDA, fila 124): mismo nombre de hilo; la vista sigue decidiendo cuándo hace falta (clanesCargados()). */
    public void pedirClanes() { tareas.enFondoDemonio("clanes", () -> ConsultasLadder.asegurarLadder(false)); }
}
