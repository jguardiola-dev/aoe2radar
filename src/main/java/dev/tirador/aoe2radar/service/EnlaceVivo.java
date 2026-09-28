package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.SocketVivo;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.util.Reloj;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * socket con la llamada de red de LiveService; un matchRemoved apunta la partida y programa con el Planificador una
 * ronda diferida que las comprueba en lote en otro hilo («socket-quitada», ver rondaQuitadas). Ningún método de esta clase toca
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
        /** Avisa a Live now de que la partida matchId de pid terminó (fin: la que dio la API, o null si no hubo datos).
         *  Con el id, Live now solo la quita si sigue siendo la partida de pid (no hace nada si Live now no existe). */
        void liveTerminada(long pid, long matchId, Match fin);
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
    /** El reloj de la cola de quitadas (esperas y tope por minuto): en la app, Reloj.MONOTONO, que no salta si
     *  Windows cambia la hora; en los tests, el RelojFalso. El socket sigue con el suyo (hora del sistema). */
    private final Reloj relojCola;

    public EnlaceVivo(EstadoVivo vivo, LiveService live, Vistas vistas) {
        this(vivo, live, vistas, SocketVivo.HTTP, Reloj.SISTEMA, Reloj.MONOTONO, SocketVivo.planificadorSistema());
    }

    /** Visible para tests: permite pasar un Conector/Reloj/Planificador falsos, sin red ni hilos reales (el mismo
     *  reloj para el socket y para la cola). */
    EnlaceVivo(EstadoVivo vivo, LiveService live, Vistas vistas,
               SocketVivo.Conector conector, Reloj reloj, SocketVivo.Planificador planificador) {
        this(vivo, live, vistas, conector, reloj, reloj, planificador);
    }

    private EnlaceVivo(EstadoVivo vivo, LiveService live, Vistas vistas,
                       SocketVivo.Conector conector, Reloj reloj, Reloj relojCola, SocketVivo.Planificador planificador) {
        this.vivo = vivo;
        this.live = live;
        this.vistas = vistas;
        this.planificador = planificador;
        this.relojCola = relojCola;
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
    /** Jugadores por llamada del lote (como el barrido de la watchlist, BarridoVivos.lote). */
    static final int PIDS_POR_LOTE = 25;
    /** Partidas por llamada del lote: con 25 jugadores, unas 4 por cabeza (con uno solo, las 5 de siempre). */
    static final int POR_PAGINA_LOTE = 100;
    /** Tope de llamadas de las quitadas por minuto (lotes y preguntas sueltas), nuevo en la 1.4: lo que sobra espera su
     *  turno. Es un presupuesto de esta función, no el freno global (ese sigue solo en api.Throttle/ApiClient); con
     *  6 lotes de 25, más de 150 jugadores quitados en un minuto se confirman al ritmo de 150 por minuto. */
    static final int TOPE_POR_MINUTO = 6;

    /** Partidas quitadas con una comprobación pendiente: un matchRemoved repetido no lanza otra llamada. */
    private final Set<Long> quitadasEnVuelo = ConcurrentHashMap.newKeySet();

    /** Una quitada esperando su comprobación: el intento (1 o 2, ver resolverQuitada) y cuándo vence la espera. */
    private record Pendiente(int intento, long venceMs) { }

    // Estado del lote, bajo el monitor de pendientes: las quitadas que esperan (en orden de llegada), si hay una
    // ronda programada en el Planificador O EN CURSO (una sola a la vez: se apaga al final de la ronda, en reprogramar,
    // para que un matchRemoved que llega mientras la ronda está en la red no programe otra a +3 min y retrase las
    // pendientes) y la hora de las llamadas del último minuto (tope).
    private final Map<Long, Pendiente> pendientes = new LinkedHashMap<>();
    private boolean rondaProgramada;
    private final ArrayDeque<Long> llamadasRecientes = new ArrayDeque<>();

    /** Apunta la partida para que se compruebe dentro de 3 min (sin programar nada: lo hace quien llama). */
    private void apuntarQuitada(long matchId, int intento) {
        synchronized (pendientes) { pendientes.put(matchId, new Pendiente(intento, relojCola.ahoraMs() + ESPERA_QUITADA_MS)); }
    }

    /** Un matchRemoved nuevo: la apunta y programa una ronda si no la hay ya. Las quitadas que llegan juntas se
     *  comprueban juntas, en una llamada por lote de hasta 25 jugadores. */
    private void programarQuitada(long matchId, int intento) {
        boolean programar;
        synchronized (pendientes) {
            apuntarQuitada(matchId, intento);
            programar = !rondaProgramada;
            rondaProgramada = true;
        }
        if (!programar) return;
        try { programarRonda(ESPERA_QUITADA_MS); }
        catch (RuntimeException ex) { synchronized (pendientes) { pendientes.remove(matchId); } throw ex; }
    }

    /** La ronda corre en su propio hilo («socket-quitada»): va a la red. Quien llama ya puso rondaProgramada. */
    private void programarRonda(long ms) {
        try { planificador.despues(ms, () -> new Thread(this::rondaQuitadas, "socket-quitada").start()); }
        catch (RuntimeException ex) { synchronized (pendientes) { rondaProgramada = false; } throw ex; }
    }

    /**
     * Lo que hace el Planificador al vencer la espera: comprueba en lote las quitadas cuya espera de 3 min ya pasó
     * (ninguna antes de su hora: la API tarda unos 2 min en marcar finished) y deja las demás para otra ronda, que
     * programa al terminar. Va a la red: corre en el hilo «socket-quitada».
     */
    void rondaQuitadas() {
        Map<Long, Pendiente> tanda = new LinkedHashMap<>();
        synchronized (pendientes) {
            long ahora = relojCola.ahoraMs();
            for (Iterator<Map.Entry<Long, Pendiente>> it = pendientes.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Long, Pendiente> e = it.next();
                if (e.getValue().venceMs() <= ahora) { tanda.put(e.getKey(), e.getValue()); it.remove(); }
            }
        }
        try { comprobarQuitadas(tanda); }
        finally { reprogramar(true); }
    }

    /** Si quedan quitadas esperando y no hay ronda programada ni en curso, otra para cuando venza la primera (o
     *  cuando el tope del minuto deje otra llamada). finRonda: la llama la ronda al terminar, y en el mismo bloque
     *  sincronizado apaga su marca (así nadie programa otra en el hueco). */
    private void reprogramar(boolean finRonda) {
        long ms;
        synchronized (pendientes) {
            if (finRonda) rondaProgramada = false;
            if (pendientes.isEmpty() || rondaProgramada) return;
            long ahora = relojCola.ahoraMs(), vence = Long.MAX_VALUE;
            for (Pendiente p : pendientes.values()) vence = Math.min(vence, p.venceMs());
            ms = Math.max(Math.max(vence - ahora, esperaTope(ahora)), 1_000);
            rondaProgramada = true;
        }
        try { programarRonda(ms); }
        catch (RuntimeException ex) {   // Planificador parado (la app se cierra): nada quedará esperando en vuelo
            log("socket: no se pudo programar la comprobación de las quitadas: " + causa(ex));
            synchronized (pendientes) { quitadasEnVuelo.removeAll(pendientes.keySet()); pendientes.clear(); }
        }
    }

    /** Bajo el monitor de pendientes: ms hasta que el tope del minuto deje otra llamada (0 si ya la deja). */
    private long esperaTope(long ahora) {
        while (!llamadasRecientes.isEmpty() && llamadasRecientes.peekFirst() <= ahora - 60_000) llamadasRecientes.pollFirst();
        return llamadasRecientes.size() < TOPE_POR_MINUTO ? 0 : llamadasRecientes.peekFirst() + 60_000 - ahora;
    }

    /** Reserva una llamada dentro del tope del minuto; false si ya se hicieron TOPE_POR_MINUTO. */
    private boolean reservarLlamada() {
        synchronized (pendientes) {
            long ahora = relojCola.ahoraMs();
            if (esperaTope(ahora) > 0) return false;
            llamadasRecientes.addLast(ahora);
            return true;
        }
    }

    /** Vuelve a la cola, ya vencida, una quitada que el tope del minuto no dejó comprobar: va en la próxima ronda. */
    private void devolver(Caso caso) {
        synchronized (pendientes) { pendientes.put(caso.matchId(), new Pendiente(caso.intento(), relojCola.ahoraMs())); }
    }

    /** Una quitada lista para preguntar: los que solo tiene Live now (los marcados se leen al resolver) y por quién se pregunta. */
    private record Caso(long matchId, int intento, List<Long> soloLive, long pid) { }

    /** Una sola quitada, sin esperar a la ronda. Solo la usan los tests: el mismo camino que un lote de un solo jugador. */
    void comprobarQuitada(long matchId, int intento) {
        synchronized (pendientes) { pendientes.remove(matchId); }
        Map<Long, Pendiente> una = new LinkedHashMap<>();
        una.put(matchId, new Pendiente(intento, relojCola.ahoraMs()));
        try { comprobarQuitadas(una); }
        finally { reprogramar(false); }
    }

    /**
     * Comprueba una tanda de quitadas con el menor número de llamadas: una por lote de hasta 25 jugadores distintos
     * (se pregunta por uno de cada partida; dos partidas del mismo jugador comparten pregunta). Con un solo jugador
     * pide sus 5 últimas, como antes; con varios, sus 100 últimas juntas y, si una partida no vino (pudieron taparla
     * las de los otros), se pregunta por ella sola, como antes: el veredicto es el mismo que con una llamada por
     * partida. Lo que el tope del minuto no deja, vuelve a la cola. Va a la red.
     */
    private void comprobarQuitadas(Map<Long, Pendiente> tanda) {
        // las de la tanda que aún no se resolvieron ni volvieron a la cola: si algo lanza, se liberan al salir (como antes)
        Set<Long> abiertas = new HashSet<>(tanda.keySet());
        boolean cambio = false;
        try {
            Map<Long, List<Caso>> porPid = new LinkedHashMap<>();
            for (Map.Entry<Long, Pendiente> e : tanda.entrySet()) {
                long matchId = e.getKey();
                try {   // un fallo con una partida no tumba la tanda: esa se libera al salir, como cuando tenía su hilo
                    List<Long> pids = vivo.jugadoresDe(matchId);
                    // los que solo vio el barrido de Live now (no están marcados en EstadoVivo): también salen (revisión 1.3, F1)
                    List<Long> soloLive = new ArrayList<>(vistas.jugadoresLiveNow(matchId)); soloLive.removeAll(pids);
                    if (pids.isEmpty() && soloLive.isEmpty()) continue;   // ya salieron entretanto (un matchUpdated con finished o el barrido): se libera al salir, sin red
                    long pid = !pids.isEmpty() ? pids.get(0) : soloLive.get(0);
                    porPid.computeIfAbsent(pid, k -> new ArrayList<>()).add(new Caso(matchId, e.getValue().intento(), soloLive, pid));
                } catch (RuntimeException ex) { log("socket: fallo al preparar la partida quitada " + matchId + ": " + causa(ex)); }
            }
            List<Long> todos = new ArrayList<>(porPid.keySet());
            for (int desde = 0; desde < todos.size(); desde += PIDS_POR_LOTE) {
                List<Long> lotePids = todos.subList(desde, Math.min(todos.size(), desde + PIDS_POR_LOTE));
                List<Caso> casos = new ArrayList<>();
                for (long pid : lotePids) casos.addAll(porPid.get(pid));
                if (!reservarLlamada()) {   // tope del minuto: este lote y los siguientes esperan a la próxima ronda
                    for (int i = desde; i < todos.size(); i++) for (Caso caso : porPid.get(todos.get(i))) { devolver(caso); abiertas.remove(caso.matchId()); }
                    log("socket: tope de " + TOPE_POR_MINUTO + " comprobaciones por minuto: " + (todos.size() - desde) + " jugadores esperan a la próxima ronda");
                    break;
                }
                int porPagina = lotePids.size() == 1 ? 5 : POR_PAGINA_LOTE;
                if (casos.size() > 1) log("socket: " + casos.size() + " partidas quitadas comprobadas en una llamada (" + lotePids.size() + " jugadores)");
                Map<Long, LiveService.Comprobacion> veredictos = live.comprobarVarias(lotePids, casos.stream().map(Caso::matchId).toList(), porPagina);
                for (Caso caso : casos) {
                    LiveService.Comprobacion comp = veredictos.get(caso.matchId());
                    if (comp.veredicto() == LiveService.Veredicto.SIN_DATOS && comp.error() == null && lotePids.size() > 1) {
                        // no vino en el lote: pudieron taparla las partidas de los otros; se pregunta por ella sola (sus 5
                        // últimas, como antes), así el veredicto nunca es peor que con una llamada por partida
                        if (!reservarLlamada()) { devolver(caso); abiertas.remove(caso.matchId()); continue; }
                        comp = live.comprobar(caso.pid(), caso.matchId(), 5);
                    }
                    abiertas.remove(caso.matchId());   // resolverQuitada la libera o la vuelve a apuntar ella misma
                    try { cambio |= resolverQuitada(caso, comp); }
                    catch (RuntimeException ex) { log("socket: fallo al resolver la partida quitada " + caso.matchId() + ": " + causa(ex)); }
                }
            }
        } finally {
            quitadasEnVuelo.removeAll(abiertas);
        }
        if (cambio) vistas.avisarTrasCambio();
    }

    /**
     * ¿Terminó de verdad la partida quitada? (decisión de Jorge: solo cuenta «terminada»). TERMINADA: salen sus
     * jugadores y se apunta como terminada. VIVA: el aviso era falso; se vuelve a mirar cada 3 min (tras un
     * matchRemoved el companion ya no manda más de esa partida), con el tope natural de enCursoReal: pasadas 3 h
     * desde started, la API la da por TERMINADA. SIN_DATOS (la API no la tiene o falló): un reintento; si sigue sin
     * datos, salen SIN apuntarla como terminada (un fallo de red no puede bloquearla 3 h; un barrido puede volver a
     * marcarlos), para que nadie se quede «jugando» para siempre: los que solo vigila Live now no tienen otro
     * barrido que los saque. Sin red (el veredicto ya llegó); true si salió alguien marcado (hay que avisar).
     */
    private boolean resolverQuitada(Caso caso, LiveService.Comprobacion comp) {
        long matchId = caso.matchId();
        int intento = caso.intento();
        boolean sigue = false;
        try {
            if (comp.error() != null) log("socket: no se pudo comprobar la partida quitada " + matchId + ": " + causa(comp.error()));
            if (comp.veredicto() == LiveService.Veredicto.VIVA) {
                log("socket: matchRemoved de la partida " + matchId + ", pero la API la ve en curso: se mantiene y se mira en 3 min");
                apuntarQuitada(matchId, intento);   // la ronda la vuelve a programar al terminar
                sigue = true;
                return false;
            }
            List<Long> fuera;
            if (comp.veredicto() == LiveService.Veredicto.SIN_DATOS) {
                if (intento < 2) {
                    log("socket: matchRemoved de la partida " + matchId + " sin datos de la API: se reintenta en 3 min");
                    apuntarQuitada(matchId, intento + 1);
                    sigue = true;
                    return false;
                }
                log("socket: la partida " + matchId + " sigue sin datos de la API tras el reintento: salen sus jugadores, sin darla por terminada");
                fuera = vivo.sacarDePartida(matchId);
            } else {
                fuera = vivo.quitarPartida(matchId);
            }
            boolean cambio = false;
            // TERMINADA: la partida que devolvió la API, con su hora de fin (si no, Live now pintaba «hace 0 min» hasta
            // que caducaba a las 2 h). Sin datos: null, como antes (Live now usa la que guardó al empezar). Revisión 1.3, F5.
            Match fin = comp.veredicto() == LiveService.Veredicto.TERMINADA ? comp.partida() : null;
            // con el matchId: si en el hueco el socket lo metió en otra partida, Live now no la suelta (menor del revisor)
            for (long pid : fuera) { vistas.liveTerminada(pid, matchId, fin); cambio = true; }
            for (long pid : caso.soloLive()) vistas.liveTerminada(pid, matchId, fin);   // F1: solo Live now los tenía (sin repetir: se quitaron los marcados arriba)
            return cambio;
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
