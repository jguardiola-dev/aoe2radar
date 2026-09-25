package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.util.Reloj;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.tirador.aoe2radar.cache.Vivos.enCursoReal;

/**
 * Quién está en partida AHORA, con un solo dueño. Lo escriben el socket, la confirmación de fantasmas y los barridos
 * (varios hilos); lo lee la UI. En la 1.1 eran cinco mapas sueltos (vivoWatch, un HashMap sin protección; vivoInfo,
 * VIVO_PARTIDA, VIVO_RIVAL, VISTO_VIVO_MS) y un jugador podía quedar a medias entre ellos. Aquí todo va bajo un mismo
 * candado y cada cambio que la app hacía en varios mapas seguidos es UNA operación.
 * <p>Por jugador: el id de la partida en la que está (el «punto» de la lista), el texto de la sublínea, la partida
 * completa (para el menú «En partida ahora»; no se borra al salir, como en la 1.1), el rival de un 1v1 y la última vez
 * que se le vio en partida.
 */
public final class EstadoVivo {
    /** El de la app. */
    public static final EstadoVivo SISTEMA = new EstadoVivo(Reloj.SISTEMA);

    /** El rival de un 1v1 en curso (para el contextual «Rival: X»). */
    public record Rival(long pid, String nombre) { }

    private final Reloj reloj;
    private final Map<Long, Long> matchDe = new HashMap<>();
    private final Map<Long, String> info = new HashMap<>();
    private final Map<Long, Match> partida = new HashMap<>();
    private final Map<Long, Rival> rival = new HashMap<>();
    private final Map<Long, Long> vistoMs = new HashMap<>();

    public EstadoVivo(Reloj reloj) { this.reloj = reloj; }

    // ----- escrituras -----

    /** Está en la partida matchId (sin tocar su texto). */
    public synchronized void marcarJugando(long pid, long matchId) { matchDe.put(pid, matchId); }

    /** Está en la partida matchId con este texto (null: sin texto; en la 1.1, NullPointerException). */
    public synchronized void marcarJugando(long pid, long matchId, String texto) {
        matchDe.put(pid, matchId);
        ponerInfoSinCandado(pid, texto);
    }

    /** Solo el texto de la sublínea (null: sin texto). */
    public synchronized void ponerInfo(long pid, String texto) { ponerInfoSinCandado(pid, texto); }

    private void ponerInfoSinCandado(long pid, String texto) {
        if (texto == null) info.remove(pid); else info.put(pid, texto);
    }

    /** Ya no está en partida: se van su punto, su texto y su rival (la partida completa y el «visto» se quedan). */
    public synchronized void marcarFuera(long pid) {
        matchDe.remove(pid);
        info.remove(pid);
        rival.remove(pid);
    }

    /** La partida matchId ha terminado: fuera todos los que estaban en ella. Devuelve quiénes eran. */
    public synchronized List<Long> quitarPartida(long matchId) {
        List<Long> fuera = new ArrayList<>();
        for (Map.Entry<Long, Long> e : matchDe.entrySet()) if (e.getValue() == matchId) fuera.add(e.getKey());
        for (long pid : fuera) marcarFuera(pid);
        return fuera;
    }

    /** Guarda la partida completa en curso de pid (nunca null: «tiene partida» es partida(pid) != null). */
    public synchronized void guardarPartida(long pid, Match m) { partida.put(pid, Objects.requireNonNull(m)); }

    /** Suelta la partida completa de pid y la devuelve (null si no había). */
    public synchronized Match soltarPartida(long pid) { return partida.remove(pid); }

    /**
     * Lo que la 1.1 hacía de paso en resumenVivo: si la partida está en curso de verdad, la guarda y apunta «visto
     * ahora»; si es un 1v1, apunta el rival de pid. Todos los caminos que detectan a alguien en partida pasan por aquí.
     */
    public synchronized void registrar(Match m, long pid) {
        long ahora = reloj.ahoraMs();   // un solo reloj para «en curso» y para el sello de «visto»
        if (enCursoReal(m, Instant.ofEpochMilli(ahora))) { partida.put(pid, m); vistoMs.put(pid, ahora); }
        try {
            if (m.players.size() == 2) {
                MatchPlayer riv = null;
                for (MatchPlayer p : m.players) if (p.id != pid) riv = p;
                if (riv != null) rival.put(pid, new Rival(riv.id, riv.name));
            }
        } catch (Exception ignored) { }   // como en la 1.1: una partida rara no rompe nada
    }

    // ----- lecturas (sin red, desde cualquier hilo) -----

    public synchronized boolean jugando(long pid) { return matchDe.containsKey(pid); }
    /** El id de la partida en la que está pid, o null. */
    public synchronized Long matchDe(long pid) { return matchDe.get(pid); }
    public synchronized boolean nadieJugando() { return matchDe.isEmpty(); }
    public synchronized String info(long pid) { return info.get(pid); }
    public synchronized Match partida(long pid) { return partida.get(pid); }
    public synchronized Rival rival(long pid) { return rival.get(pid); }
    /** Última vez (ms) que se le vio en partida, o null. */
    public synchronized Long vistoMs(long pid) { return vistoMs.get(pid); }
}
