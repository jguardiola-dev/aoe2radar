package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.service.ConsultasLadder;
import dev.tirador.aoe2radar.service.EstadoVivo;

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
    private boolean ahoraCargando;

    /** La de la app: quien construye el presentador (LiveNowView) pasa EstadoVivo.SISTEMA explícitamente. */
    public LiveNowPresenter(Buscador buscador, Set<Long> socketExtra, Tareas tareas, Pantalla pantalla) {
        this(buscador, socketExtra, tareas, pantalla, EstadoVivo.SISTEMA);
    }

    /** Con el EstadoVivo inyectado (DEUDA, fila 125): la usan los tests para no compartir el singleton de toda
     *  la app entre pruebas. */
    public LiveNowPresenter(Buscador buscador, Set<Long> socketExtra, Tareas tareas, Pantalla pantalla, EstadoVivo estadoVivo) {
        this.buscador = buscador;
        this.socketExtra = socketExtra;
        this.tareas = tareas;
        this.pantalla = pantalla;
        this.estadoVivo = estadoVivo;
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

    public long ultimaMs() { return ahoraUltimaMs; }

    public boolean cargando() { return ahoraCargando; }

    /** Copia de la fuente actual (para pintar sin tener el candado cogido mientras se construyen las tarjetas). */
    public List<Object[]> topSnapshot() { synchronized (ahoraTop) { return new ArrayList<>(ahoraTop); } }

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
     *  que el próximo {@link #refrescar} pida la lista entera y el barrido completo. */
    public void reiniciarFuente() {
        synchronized (ahoraTop) { ahoraTop.clear(); }
        synchronized (ahoraEnCurso) { ahoraEnCurso.clear(); }
        synchronized (liveTerminadas) { liveTerminadas.clear(); }
        ahoraTopMs = 0; ahoraUltimaMs = 0;
    }

    /**
     * Carga la fuente (si tiene más de 30 min), comprueba por lotes de 15 quién está en partida y suscribe esos
     * ids al socket. Igual que ahoraRefrescar de la 1.1: el hilo se llama "live-now", y el circuito se corta a
     * los tres lotes fallidos seguidos.
     */
    public void refrescar(boolean forzar) {
        if (ahoraCargando) return;
        if (!forzar && System.currentTimeMillis() - ahoraUltimaMs < 60_000 && !ahoraTop.isEmpty()) { pantalla.pintar(); return; }
        ahoraCargando = true;
        pantalla.estado(t("Consultando…", "Checking…"));
        tareas.enFondo("live-now", () -> {
            try {
                if (ahoraTop.isEmpty() || System.currentTimeMillis() - ahoraTopMs > 30 * 60_000L) {
                    List<Object[]> top = pantalla.cargarFuenteLive();
                    synchronized (ahoraTop) { ahoraTop.clear(); ahoraTop.addAll(top); }
                    ahoraTopMs = System.currentTimeMillis();
                    Set<Long> ids = new HashSet<>(); for (Object[] f : top) ids.add((Long) f[0]);
                    socketExtra.removeIf(id -> !ids.contains(id) && !pantalla.campanaContiene(id));
                    socketExtra.addAll(ids);
                    tareas.enUi(pantalla::sincronizarSocket);   // el socket vigila también a los 250
                }
                Map<Long, Match> vivos = new HashMap<>();
                List<Object[]> top; synchronized (ahoraTop) { top = new ArrayList<>(ahoraTop); }
                final int LOTE = 15; int fallosSeguidos = 0;
                for (int d = 0; d < top.size(); d += LOTE) {
                    if (!pantalla.abierta() || fallosSeguidos >= 3) { if (fallosSeguidos >= 3) log("live: tres lotes fallidos seguidos: barrido abortado hasta el próximo"); break; }
                    List<Object[]> lote = top.subList(d, Math.min(d + LOTE, top.size()));
                    StringBuilder csv = new StringBuilder(); Set<Long> ids = new HashSet<>();
                    for (Object[] f : lote) { if (csv.length() > 0) csv.append(','); csv.append((Long) f[0]); ids.add((Long) f[0]); }
                    try {
                        Iterable<Match> leidas = buscador.partidas(csv.toString(), 1, 100);
                        for (Match m : leidas) {
                            if (m == null) continue;
                            if (enCursoReal(m)) { for (MatchPlayer mp : m.players) if (ids.contains(mp.id) && !vivos.containsKey(mp.id)) vivos.put(mp.id, m); }
                            else if (m.finished != null && m.finished.isAfter(Instant.now().minus(Duration.ofHours(2)))) { synchronized (liveTerminadas) { liveTerminadas.putIfAbsent(m.id, new Object[]{ m, m.finished.toEpochMilli() }); } }
                        }
                        fallosSeguidos = 0;
                    } catch (Exception ex) { fallosSeguidos++; log("live: lote " + (d / LOTE + 1) + ": " + causa(ex)); }
                    final int hechos = Math.min(d + LOTE, top.size());
                    final int totalTop = top.size();
                    tareas.enUi(() -> pantalla.estado(t("Consultando… ", "Checking… ") + hechos + " / " + totalTop));
                }
                // una partida que se sabe terminada (socket, espectar) no vuelve con la foto del barrido. Candados
                // anidados en este orden (liveTerminadas → ahoraEnCurso → EstadoVivo, que es hoja): nadie los coge al revés
                synchronized (liveTerminadas) {
                    synchronized (ahoraEnCurso) {
                        vivos.values().removeIf(m -> liveTerminadas.containsKey(m.id) || estadoVivo.terminada(m.id));
                        ahoraEnCurso.clear(); ahoraEnCurso.putAll(vivos);
                    }
                }
                for (Map.Entry<Long, Match> en : vivos.entrySet()) estadoVivo.guardarPartida(en.getKey(), en.getValue());
                ahoraUltimaMs = System.currentTimeMillis();
                tareas.enUi(pantalla::pintar);
            } catch (Exception ex) {
                tareas.enUi(() -> pantalla.estado(t("No se pudo consultar: ", "Couldn't check: ") + causa(ex)));
            } finally { ahoraCargando = false; }
        });
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
            Match viva = estadoVivo.soltarPartida(pid);
            Match fin = m != null ? m : viva;
            if (fin != null && fin.id > 0) synchronized (liveTerminadas) { liveTerminadas.putIfAbsent(fin.id, new Object[]{ fin, System.currentTimeMillis() }); }
            synchronized (ahoraEnCurso) { ahoraEnCurso.remove(pid); }
        } else if (m != null) {
            estadoVivo.guardarPartida(pid, m);
            synchronized (ahoraEnCurso) { ahoraEnCurso.put(pid, m); }
        }
        if (pantalla.puedeRepintar()) tareas.enUi(pantalla::pintar);
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
