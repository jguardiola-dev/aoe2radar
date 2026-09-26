package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.SocketVivo;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.util.Reloj;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.tirador.aoe2radar.cache.Vivos.candidatoSocket;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * El cableado del websocket «ongoing-matches» del companion (ver api.SocketVivo): decide qué significa cada
 * evento (partida nueva confirmada por la API, partida quitada) y avisa a las vistas (Live now, Watchlist,
 * Partidas) por la interfaz {@link Vistas}. Antes vivía en SpoilerFreeRecs
 * (procesarEventosSocket/sincronizarSocket/iniciarPing/tickMs, fase 3, tanda 4, Z4). Vive en {@code service} y
 * no en {@code ui} porque necesita el tipo api.SocketVivo: ui no puede importar api (ver tools/capas.py).
 * <p>Hilos: el socket llama a {@code conectado}/{@code eventos} en su propio hilo de fondo (nunca el EDT, ver
 * api.SocketVivo); {@code confirmarEventoSocket} abre otro hilo («socket-confirmar») para no bloquear al
 * socket con la llamada de red de LiveService; un matchRemoved programa con el Planificador una comprobación
 * diferida que corre en otro hilo («socket-quitada», ver comprobarQuitada). Ningún método de esta clase toca
 * Swing: la vuelta al EDT (SwingUtilities.invokeLater) la hace SIEMPRE la implementación de {@link Vistas} que pasa la ventana.
 */
public final class EnlaceVivo {

    /** Lo que EnlaceVivo pide o avisa a las vistas; implementada por la ventana (SpoilerFreeRecs). Los métodos
     *  que tocan Swing deben volver al EDT ellos mismos: EnlaceVivo los llama desde hilos de fondo. */
    public interface Vistas {
        /** Ids de la watchlist (playersModel), en su orden. */
        List<Long> idsWatchlist();
        /** Ids de todos los grupos, no solo el visible (así cambiar de pestaña no reconecta el socket). */
        List<Long> idsTodosJugadores();
        /** Ids del top del ladder cuando la vista está en modo ★ (watchlist.topLadderSnapshot). */
        List<Long> idsTopLadder();
        /** Ids extra que Live now (si está construido) también vigila; vacío si Live now no existe aún. */
        Set<Long> idsSocketExtra();
        /** Avisa a Live now de un evento de pid (no hace nada si Live now no existe aún). */
        void liveEvento(long pid, Match m, boolean terminada);
        /** Quiénes tiene Live now «en partida» en matchId (lo vio su barrido, aunque nadie los marcara en EstadoVivo);
         *  vacío si nadie o si Live now no existe aún. Sin red; seguro desde cualquier hilo (revisión 1.3, F1). */
        List<Long> jugadoresLiveNow(long matchId);
        /** Campanita: avisa si pid está en una vista marcada. */
        void avisarSiCampana(long pid, Match m);
        /** «Mi partida»: avisa si pid es el propio jugador vigilado. */
        void avisarMiPartida(long pid, Match m);
        /** Tras un cambio de verdad (alguien entra o sale de partida): refresca indicadores, alturas y repinta
         *  watchlist y tabla de partidas. Debe volver al EDT ella misma. */
        void avisarTrasCambio();
        /** Tras reconectar después de una caída, si Live now está abierta, un barrido para reparar su estado.
         *  Debe volver al EDT ella misma. */
        void refrescarLiveNowSiAbierta();
    }

    private final SocketVivo socketVivo;
    private final EstadoVivo vivo;
    private final LiveService live;
    private final Vistas vistas;
    private final SocketVivo.Planificador planificador;

    public EnlaceVivo(EstadoVivo vivo, LiveService live, Vistas vistas) {
        this(vivo, live, vistas, SocketVivo.HTTP, Reloj.SISTEMA, SocketVivo.planificadorSistema());
    }

    /** Visible para tests: permite pasar un Conector/Reloj/Planificador falsos, sin red ni hilos reales. */
    EnlaceVivo(EstadoVivo vivo, LiveService live, Vistas vistas,
               SocketVivo.Conector conector, Reloj reloj, SocketVivo.Planificador planificador) {
        this.vivo = vivo;
        this.live = live;
        this.vistas = vistas;
        this.planificador = planificador;
        this.socketVivo = new SocketVivo(conector, new SocketVivo.Oyente() {
            @Override public void conectado(boolean trasCaida) { if (trasCaida) vistas.refrescarLiveNowSiAbierta(); }   // tras una caída, un barrido para reparar el estado
            @Override public void eventos(List<SocketVivo.Evento> eventos, Set<Long> ids) { procesarEventosSocket(eventos, ids); }
        }, reloj, planificador);
    }

    public boolean conectado() { return socketVivo.conectado(); }
    public void iniciarPing() { socketVivo.iniciarPing(); }
    public void cerrar() { socketVivo.cerrar(); }

    /** Conecta (o reconecta) con los ids visibles; si no cambian y el socket está sano, no hace nada. La
     *  comprobación mira {@code conectado()}, no un {@code sano()} aparte: SocketVivo.revisarSalud (tarea de
     *  ping, cada 30 s) ya baja conectado a false en cuanto pasan 10 min sin mensajes ni pong, así que
     *  «conectado» puede tardar hasta esos 30 s en reflejar una conexión colgada. */
    public void sincronizarSocket() {
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(vistas.idsWatchlist());
        ids.addAll(vistas.idsTodosJugadores());      // todos los grupos, no solo la vista actual: así cambiar de pestaña no reconecta el socket (y no se pierden eventos en el hueco)
        ids.addAll(vistas.idsTopLadder());
        ids.addAll(vistas.idsSocketExtra());          // Live now abierto y vistas con campana: también se vigilan aunque no estén a la vista
        try { String mi = leerConfig("mi_pid", ""); if (!mi.isBlank()) ids.add(Long.parseLong(mi)); } catch (Exception ignored) { }   // «Mi partida»: mi propio id siempre vigilado
        socketVivo.sincronizar(ids);
    }

    /** Antes de marcar a alguien como jugando por un evento del socket, se comprueba en la API que la
     *  partida no esté ya terminada (el companion a veces anuncia partidas viejas como vivas). */
    private void confirmarEventoSocket(Match m, List<Long> pids) {
        new Thread(() -> {
            LiveService.Comprobacion c = live.comprobar(pids.get(0), m.id, 5);
            if (c.error() != null) log("socket: no se pudo confirmar la partida " + m.id + ": " + causa(c.error()));
            boolean viva = c.veredicto() != LiveService.Veredicto.TERMINADA;   // sin datos: el beneficio de la duda
            if (!viva) { log("socket: partida " + m.id + " ya terminada según la API: fantasma ignorado"); return; }
            for (long pid : pids) { if (vivo.terminada(m.id)) continue; String resumen = ReglasPartida.resumenVivo(m, pid); if (!vivo.marcarJugando(pid, m.id, resumen)) continue;   /* terminada entretanto: ni Live now ni avisos */ vivo.guardarPartida(pid, m); vistas.liveEvento(pid, m, false); vistas.avisarSiCampana(pid, m); vistas.avisarMiPartida(pid, m); }
            vistas.avisarTrasCambio();
        }, "socket-confirmar").start();
    }

    /** Tras un matchRemoved, la pregunta a la API espera 3 min: /matches marca finished unos 2 min después del
     *  final real (medido al calibrar EloSesion, ver DEUDA); antes la API diría casi siempre «sigue viva». */
    static final long ESPERA_QUITADA_MS = 3 * 60_000;

    /** Partidas quitadas con una comprobación pendiente: un matchRemoved repetido no lanza otra llamada. */
    private final Set<Long> quitadasEnVuelo = ConcurrentHashMap.newKeySet();

    private void programarQuitada(long matchId, int intento) {
        planificador.despues(ESPERA_QUITADA_MS, () -> new Thread(() -> comprobarQuitada(matchId, intento), "socket-quitada").start());
    }

    /**
     * ¿Terminó de verdad la partida quitada? (decisión de Jorge: solo cuenta «terminada»). TERMINADA: salen sus
     * jugadores y se apunta como terminada. VIVA: el aviso era falso; se vuelve a mirar cada 3 min (tras un
     * matchRemoved el companion ya no manda más de esa partida), con el tope natural de enCursoReal: pasadas 3 h
     * desde started, la API la da por TERMINADA. SIN_DATOS (la API no la tiene o falló): un reintento; si sigue sin
     * datos, salen SIN apuntarla como terminada (un fallo de red no puede bloquearla 3 h; un barrido puede volver a
     * marcarlos), para que nadie se quede «jugando» para siempre: los que solo vigila Live now no tienen otro
     * barrido que los saque. Va a la red: se llama en el hilo «socket-quitada».
     */
    void comprobarQuitada(long matchId, int intento) {
        boolean sigue = false;
        try {
            List<Long> pids = vivo.jugadoresDe(matchId);
            // los que solo vio el barrido de Live now (no están marcados en EstadoVivo): también salen (revisión 1.3, F1)
            List<Long> soloLive = new ArrayList<>(vistas.jugadoresLiveNow(matchId)); soloLive.removeAll(pids);
            if (pids.isEmpty() && soloLive.isEmpty()) return;   // ya salieron entretanto (un matchUpdated con finished o el barrido)
            LiveService.Comprobacion c = live.comprobar(!pids.isEmpty() ? pids.get(0) : soloLive.get(0), matchId, 5);
            if (c.error() != null) log("socket: no se pudo comprobar la partida quitada " + matchId + ": " + causa(c.error()));
            if (c.veredicto() == LiveService.Veredicto.VIVA) {
                log("socket: matchRemoved de la partida " + matchId + ", pero la API la ve en curso: se mantiene y se mira en 3 min");
                programarQuitada(matchId, intento);
                sigue = true;
                return;
            }
            List<Long> fuera;
            if (c.veredicto() == LiveService.Veredicto.SIN_DATOS) {
                if (intento < 2) {
                    log("socket: matchRemoved de la partida " + matchId + " sin datos de la API: se reintenta en 3 min");
                    programarQuitada(matchId, intento + 1);
                    sigue = true;
                    return;
                }
                log("socket: la partida " + matchId + " sigue sin datos de la API tras el reintento: salen sus jugadores, sin darla por terminada");
                fuera = vivo.sacarDePartida(matchId);
            } else {
                fuera = vivo.quitarPartida(matchId);
            }
            boolean cambio = false;
            // TERMINADA: la partida que devolvió la API, con su hora de fin (si no, Live now pintaba «hace 0 min» hasta
            // que caducaba a las 2 h). Sin datos: null, como antes (Live now usa la que guardó al empezar). Revisión 1.3, F5.
            Match fin = c.veredicto() == LiveService.Veredicto.TERMINADA ? c.partida() : null;
            for (long pid : fuera) { vistas.liveEvento(pid, fin, true); cambio = true; }
            for (long pid : soloLive) vistas.liveEvento(pid, fin, true);   // F1: solo Live now los tenía (sin repetir: se quitaron los marcados arriba)
            if (cambio) vistas.avisarTrasCambio();
        } finally {
            if (!sigue) quitadasEnVuelo.remove(matchId);
        }
    }

    /** Los eventos de un mensaje del socket (vacío si era un pong), ya traducidos por SocketVivo. */
    void procesarEventosSocket(List<SocketVivo.Evento> eventos, Set<Long> ids) {
        boolean cambio = false;
        for (SocketVivo.Evento ev : eventos) {
            if (ev instanceof SocketVivo.Quitada q) {
                // Decisión de Jorge (fase 4): «partida quitada» (matchRemoved) no basta para darla por terminada; el
                // companion lo manda también con partidas que siguen en juego. Solo cuenta «terminada»: la API decide.
                // Se pregunta más tarde, no al momento: la API tarda unos 2 min en marcar finished (ver ESPERA_QUITADA_MS).
                // También si solo la tiene Live now (la vio su barrido; nadie la marcó en EstadoVivo): si no, su tarjeta
                // se quedaba «en partida» para siempre con el socket vivo (revisión 1.3, F1).
                if ((!vivo.jugadoresDe(q.matchId()).isEmpty() || !vistas.jugadoresLiveNow(q.matchId()).isEmpty()) && quitadasEnVuelo.add(q.matchId())) {
                    try { programarQuitada(q.matchId(), 1); }
                    catch (RuntimeException ex) { quitadasEnVuelo.remove(q.matchId()); throw ex; }
                }
                continue;
            }
            String tipo = ((SocketVivo.Partida) ev).tipo();
            Match m = ((SocketVivo.Partida) ev).partida();
            int vigilados = 0; for (MatchPlayer mp : m.players) if (ids.contains(mp.id)) vigilados++;
            log("socket: " + tipo + " partida " + m.id + " started=" + m.started + " finished=" + m.finished + " · " + vigilados + " vigilados");
            if (m.id <= 0) continue;   // sin id de partida no hay nada que espectar
            List<Long> candidatos = new ArrayList<>();
            for (MatchPlayer mp : m.players) {
                if (!ids.contains(mp.id)) continue;
                // terminada: solo sale si esta era su partida (un final tardío de una vieja no le saca de la nueva, F8);
                // Live now la apunta en «Terminadas» igual y solo la quita de «en curso» si era esa
                if (m.finished != null) { vivo.apuntarTerminada(m.id); vivo.marcarFueraDe(mp.id, m.id); vistas.liveEvento(mp.id, m, true); cambio = true; }
                // en curso DE VERDAD: empezada (no un lobby), sin terminar y hace menos de 3 h
                else if (candidatoSocket(m, Instant.now()) && !vivo.terminada(m.id) && !Long.valueOf(m.id).equals(vivo.matchDe(mp.id))) candidatos.add(mp.id);
            }
            if (!candidatos.isEmpty()) confirmarEventoSocket(m, candidatos);   // la API tiene la última palabra (fantasmas fuera)
        }
        if (cambio) vistas.avisarTrasCambio();
    }

    /** Cada cuánto se sondea a los vivos (menú «Vigilancia de vivos»): tick_min en minutos, 1 por defecto. */
    public static int tickMs() {
        try { return Math.max(1, Integer.parseInt(leerConfig("tick_min", "1"))) * 60_000; }
        catch (Exception e) { return 60_000; }
    }
}
