package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.util.Reloj;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * que se le vio en partida. De quien ya no juega, el texto y el «visto» se podan pasado un tiempo (ver podar).
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
    /** Cuándo terminó la última partida de cada jugador (lo pone marcarFuera si estaba jugando): ver EloSesion. */
    private final Map<Long, Long> finMs = new HashMap<>();
    /** Partidas que se sabe que terminaron (id → cuándo se supo), 3 h: una partida terminada no vuelve a estar en curso. */
    private final Map<Long, Long> terminadas = new LinkedHashMap<>();
    /** Cuándo se puso el texto de cada jugador (para podar los textos viejos de quien ya no juega: ver podar). */
    private final Map<Long, Long> infoMs = new HashMap<>();
    /** Rechazos de marcarJugando por partida terminada (id → cuántos). Solo el primero va al log: ver anotarRechazo. */
    private final Map<Long, Integer> rechazos = new HashMap<>();
    /** Última poda (ms): se poda como mucho una vez cada PODA_CADA, desde las escrituras. */
    private long ultimaPodaMs;
    private final java.util.function.Consumer<String> log;

    /** Cada cuánto se poda como mucho, y cuánto duran «visto» y el texto de quien ya no está en partida (1.4). */
    static final long PODA_CADA_MS = Duration.ofMinutes(10).toMillis();
    static final long VISTO_DURA_MS = Duration.ofHours(48).toMillis();
    static final long INFO_DURA_MS = Duration.ofHours(3).toMillis();

    public EstadoVivo(Reloj reloj) { this(reloj, dev.tirador.aoe2radar.util.Log::log); }

    /** Con otro destino para el log (los tests, para no escribir en descargas.log). */
    EstadoVivo(Reloj reloj, java.util.function.Consumer<String> log) { this.reloj = reloj; this.log = log; }

    // ----- escrituras -----

    /**
     * Está en la partida matchId (sin tocar su texto). Si ya se sabe que esa partida terminó, no hace nada: un barrido
     * con una foto anterior no la resucita (gana el dato más reciente, decisión de Jorge). Devuelve si la apuntó.
     */
    public synchronized boolean marcarJugando(long pid, long matchId) {
        if (terminada(matchId)) { anotarRechazo(pid, matchId); return false; }
        matchDe.put(pid, matchId);
        return true;
    }

    /** Como marcarJugando(pid, matchId), con este texto (null: sin texto; en la 1.1, NullPointerException). */
    public synchronized boolean marcarJugando(long pid, long matchId, String texto) {
        if (terminada(matchId)) { anotarRechazo(pid, matchId); return false; }
        matchDe.put(pid, matchId);
        ponerInfoSinCandado(pid, texto);
        return true;
    }

    /**
     * Para medir en uso real si el companion manda «terminada» de partidas vivas (el riesgo de esta regla, ver DEUDA).
     * Solo el primer rechazo de cada partida va al log (antes, uno por jugador y por barrido: mucho ruido en
     * descargas.log); los demás se cuentan y el total sale en una línea cuando la partida se olvida (apuntarTerminada).
     * Esa línea necesita otra apuntarTerminada 3 h después: si la app se cierra antes, no sale (el primer aviso, sí).
     * Con el candado cogido.
     */
    private void anotarRechazo(long pid, long matchId) {
        if (rechazos.merge(matchId, 1, Integer::sum) == 1)
            log.accept("vivo: la partida " + matchId + " ya se dio por terminada: no se marca a " + pid + " como jugando");
    }

    /** Se sabe que la partida matchId ha terminado (y no vuelve): la recuerda 3 h, lo que dura una partida «en curso». */
    public synchronized void apuntarTerminada(long matchId) {
        long ahora = reloj.ahoraMs();
        terminadas.put(matchId, ahora);
        terminadas.entrySet().removeIf(e -> {
            if (ahora - e.getValue() < Duration.ofHours(3).toMillis()) return false;
            Integer n = rechazos.remove(e.getKey());
            if (n != null && n > 1) log.accept("vivo: la partida " + e.getKey() + " se rechazó " + n + " veces en total (ya se olvida)");
            return true;
        });
        podar(ahora);
    }

    /**
     * Poda lo que ya no sirve de quien NO está en partida (1.4, antes duraba toda la sesión): el «visto» de hace más de
     * VISTO_DURA_MS (48 h: el perfil lo compara con el volcado nocturno, que va un día por detrás) y el texto de la
     * sublínea de hace más de INFO_DURA_MS (el texto solo se enseña con el jugador en partida; puede llegar un momento
     * antes de marcarlo, por eso no se borra en cuanto no juega). Se queda: la partida completa (la leen el menú y la
     * regla de fantasmas; en la 1.1 no se borraba) y finMs (EloSesion.caducado lo necesita para saber que el ELO
     * recordado quedó viejo; sin él, un ELO de antes de esa partida no se renovaría). Como mucho una vez cada
     * PODA_CADA_MS. Con el candado cogido.
     */
    private void podar(long ahora) {
        if (ahora - ultimaPodaMs < PODA_CADA_MS) return;
        ultimaPodaMs = ahora;
        vistoMs.entrySet().removeIf(e -> !matchDe.containsKey(e.getKey()) && ahora - e.getValue() >= VISTO_DURA_MS);
        infoMs.entrySet().removeIf(e -> {
            if (matchDe.containsKey(e.getKey()) || ahora - e.getValue() < INFO_DURA_MS) return false;
            info.remove(e.getKey());
            return true;
        });
    }

    /** ¿Se sabe que la partida matchId terminó? */
    public synchronized boolean terminada(long matchId) { return terminadas.containsKey(matchId); }

    /** Solo el texto de la sublínea (null: sin texto). */
    public synchronized void ponerInfo(long pid, String texto) { ponerInfoSinCandado(pid, texto); }

    private void ponerInfoSinCandado(long pid, String texto) {
        if (texto == null) { info.remove(pid); infoMs.remove(pid); }
        else { info.put(pid, texto); infoMs.put(pid, reloj.ahoraMs()); }
    }

    /** Ya no está en partida: se van su punto, su texto y su rival (la partida completa y el «visto» se quedan; el
     *  «visto», hasta la poda). */
    public synchronized void marcarFuera(long pid) {
        long ahora = reloj.ahoraMs();
        if (matchDe.remove(pid) != null) finMs.put(pid, ahora);   // estaba jugando: su partida acaba de terminar
        info.remove(pid);
        infoMs.remove(pid);
        rival.remove(pid);
        podar(ahora);
    }

    /**
     * Como marcarFuera, pero solo si pid está en la partida matchId o en ninguna: el final tardío de una partida vieja
     * no le saca de la nueva (revisión 1.3, F8). Una sola operación bajo el candado. Devuelve si le sacó (o no estaba).
     */
    public synchronized boolean marcarFueraDe(long pid, long matchId) {
        Long actual = matchDe.get(pid);
        if (actual != null && actual != matchId) return false;
        marcarFuera(pid);
        return true;
    }

    /** Quiénes están ahora marcados como jugando la partida matchId (vacío si nadie). No cambia nada. */
    public synchronized List<Long> jugadoresDe(long matchId) {
        List<Long> en = new ArrayList<>();
        for (Map.Entry<Long, Long> e : matchDe.entrySet()) if (e.getValue() == matchId) en.add(e.getKey());
        return en;
    }

    /** La partida matchId ha terminado: fuera todos los que estaban en ella (y se apunta como terminada). Devuelve quiénes eran. */
    public synchronized List<Long> quitarPartida(long matchId) {
        apuntarTerminada(matchId);
        return sacarDePartida(matchId);
    }

    /** Como quitarPartida pero SIN apuntarla como terminada (no se sabe si terminó): fuera todos los que estaban en
     *  ella, y un barrido posterior puede volver a marcarlos. Devuelve quiénes eran. */
    public synchronized List<Long> sacarDePartida(long matchId) {
        List<Long> fuera = jugadoresDe(matchId);
        for (long pid : fuera) marcarFuera(pid);
        return fuera;
    }

    /** Guarda la partida completa en curso de pid (nunca null: «tiene partida» es partida(pid) != null). */
    public synchronized void guardarPartida(long pid, Match m) { partida.put(pid, Objects.requireNonNull(m)); }

    /** Suelta la partida completa de pid y la devuelve (null si no había). */
    public synchronized Match soltarPartida(long pid) { return partida.remove(pid); }

    /** Como soltarPartida, pero solo si la guardada es la partida matchId (o no hay ninguna): el final de una partida
     *  vieja no suelta la nueva (revisión 1.3, F8). Devuelve la que soltó, o null. */
    public synchronized Match soltarPartidaDe(long pid, long matchId) {
        Match m = partida.get(pid);
        return m != null && m.id != matchId ? null : partida.remove(pid);
    }

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
        podar(ahora);
    }

    // ----- lecturas (sin red, desde cualquier hilo) -----

    public synchronized boolean jugando(long pid) { return matchDe.containsKey(pid); }
    /** El id de la partida en la que está pid, o null. */
    public synchronized Long matchDe(long pid) { return matchDe.get(pid); }
    public synchronized boolean nadieJugando() { return matchDe.isEmpty(); }
    public synchronized String info(long pid) { return info.get(pid); }
    public synchronized Match partida(long pid) { return partida.get(pid); }
    public synchronized Rival rival(long pid) { return rival.get(pid); }
    /** Cuándo (ms) se supo que terminó su última partida, o null si no se ha visto terminar ninguna. */
    public synchronized Long finMs(long pid) { return finMs.get(pid); }
    /** Última vez (ms) que se le vio en partida, o null. */
    public synchronized Long vistoMs(long pid) { return vistoMs.get(pid); }
}
