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
 * socket con la llamada de red de LiveService. Ningún método de esta clase toca Swing: la vuelta al EDT
 * (SwingUtilities.invokeLater) la hace SIEMPRE la implementación de {@link Vistas} que pasa la ventana.
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

    public EnlaceVivo(EstadoVivo vivo, LiveService live, Vistas vistas) {
        this(vivo, live, vistas, SocketVivo.HTTP, Reloj.SISTEMA, SocketVivo.planificadorSistema());
    }

    /** Visible para tests: permite pasar un Conector/Reloj/Planificador falsos, sin red ni hilos reales. */
    EnlaceVivo(EstadoVivo vivo, LiveService live, Vistas vistas,
               SocketVivo.Conector conector, Reloj reloj, SocketVivo.Planificador planificador) {
        this.vivo = vivo;
        this.live = live;
        this.vistas = vistas;
        this.socketVivo = new SocketVivo(conector, new SocketVivo.Oyente() {
            @Override public void conectado(boolean trasCaida) { if (trasCaida) vistas.refrescarLiveNowSiAbierta(); }   // tras una caída, un barrido para reparar el estado
            @Override public void eventos(List<SocketVivo.Evento> eventos, Set<Long> ids) { procesarEventosSocket(eventos, ids); }
        }, reloj, planificador);
    }

    public boolean conectado() { return socketVivo.conectado(); }
    public void iniciarPing() { socketVivo.iniciarPing(); }
    public void cerrar() { socketVivo.cerrar(); }

    /** Conecta (o reconecta) con los ids visibles; si no cambian y el socket está sano, no hace nada. */
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

    /** Los eventos de un mensaje del socket (vacío si era un pong), ya traducidos por SocketVivo. */
    void procesarEventosSocket(List<SocketVivo.Evento> eventos, Set<Long> ids) {
        boolean cambio = false;
        for (SocketVivo.Evento ev : eventos) {
            if (ev instanceof SocketVivo.Quitada q) {
                long mid = q.matchId();
                for (long pid : vivo.quitarPartida(mid)) { vistas.liveEvento(pid, null, true); cambio = true; }
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
                if (m.finished != null) { vivo.apuntarTerminada(m.id); vivo.marcarFuera(mp.id); vistas.liveEvento(mp.id, m, true); cambio = true; }
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
