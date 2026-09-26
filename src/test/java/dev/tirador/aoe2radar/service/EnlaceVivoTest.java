package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.SocketVivo;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.util.RelojFalso;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El cableado del socket de vivos (fase 3, tanda 4, Z4): un EstadoVivo y un LiveService propios (con un
 * transporte falso, sin red), y un SocketVivo con un Conector/Planificador falsos (como en
 * api.SocketVivoTest) para disparar sus eventos a mano. Comprueba que EnlaceVivo marca en EstadoVivo y avisa
 * a las vistas en el mismo orden que tenía SpoilerFreeRecs.confirmarEventoSocket/procesarEventosSocket.
 */
class EnlaceVivoTest {
    static final long HORA = 1_700_000_000_000L;

    // ----- dobles -----

    static final class ConectorFalso implements SocketVivo.Conector {
        SocketVivo.Receptor receptor;
        String ultimaUrl;
        @Override public CompletableFuture<SocketVivo.Canal> conectar(String url, SocketVivo.Receptor r) {
            this.receptor = r; this.ultimaUrl = url;
            return new CompletableFuture<>();   // esta suite no necesita que llegue a "conectado" de verdad
        }
    }
    static final class CanalFalso implements SocketVivo.Canal {
        @Override public void ping() { }
        @Override public void cerrar(String motivo) { }
    }
    static final class PlanificadorFalso implements SocketVivo.Planificador {
        int periodicas;
        @Override public Tarea despues(long ms, Runnable r) { return () -> false; }
        @Override public void cada(long ms, Runnable r) { periodicas++; }
    }
    static final class TransporteFalso implements Transporte {
        String cuerpo = "{\"matches\":[]}";
        int estado = 200;
        @Override public Respuesta get(String url) { return new Respuesta(estado, cuerpo); }
    }
    static final class ThrottleSinFreno implements Throttle {
        @Override public void adquirir(BooleanSupplier cancelar) { }
        @Override public long registrar429() { return 0; }
    }

    /** Anota cada aviso, en el orden en que llega, para comprobar la secuencia de la base. */
    static final class VistasFalsas implements EnlaceVivo.Vistas {
        final List<Long> watchlist = new ArrayList<>(), todos = new ArrayList<>(), topLadder = new ArrayList<>();
        Set<Long> extra = Set.of();
        final List<String> avisos = new ArrayList<>();
        @Override public List<Long> idsWatchlist() { return watchlist; }
        @Override public List<Long> idsTodosJugadores() { return todos; }
        @Override public List<Long> idsTopLadder() { return topLadder; }
        @Override public Set<Long> idsSocketExtra() { return extra; }
        @Override public void liveEvento(long pid, Match m, boolean terminada) { avisos.add("liveEvento:" + pid + ":" + terminada); }
        @Override public void avisarSiCampana(long pid, Match m) { avisos.add("campana:" + pid); }
        @Override public void avisarMiPartida(long pid, Match m) { avisos.add("miPartida:" + pid); }
        @Override public void avisarTrasCambio() { avisos.add("avisarTrasCambio"); }
        @Override public void refrescarLiveNowSiAbierta() { avisos.add("refrescarLiveNowSiAbierta"); }
    }

    static Match partida(long id, long... pids) {
        Match m = new Match();
        m.id = id;
        m.started = Instant.now();   // candidatoSocket usa Instant.now(), como en la 1.1
        for (long pid : pids) { MatchPlayer p = new MatchPlayer(); p.id = pid; m.players.add(p); }
        return m;
    }

    static String partidaJson(long id, Duration hace, boolean terminada) {
        long ini = HORA - hace.toMillis();
        return "{\"match_id\":" + id + ",\"started\":" + ini + (terminada ? ",\"finished\":" + (ini + 60_000) : "") + "}";
    }

    /** Espera (con tope) a que el hilo "socket-confirmar" haya avisado n veces: el transporte falso responde
     *  al instante, así que en la práctica es cuestión de milisegundos. */
    static void esperarAvisos(VistasFalsas vistas, int n) throws InterruptedException {
        long limite = System.currentTimeMillis() + 2000;
        while (vistas.avisos.size() < n && System.currentTimeMillis() < limite) Thread.sleep(5);
    }

    final TransporteFalso red = new TransporteFalso();
    final RelojFalso reloj = new RelojFalso();
    { reloj.ahora = HORA; }
    final LiveService live = new LiveService(new CompanionApi(new ApiClient(new ThrottleSinFreno(), red, s -> { }, () -> false)), reloj);
    final EstadoVivo vivo = new EstadoVivo(reloj);
    final VistasFalsas vistas = new VistasFalsas();
    final ConectorFalso conector = new ConectorFalso();
    final PlanificadorFalso planificador = new PlanificadorFalso();
    final EnlaceVivo enlace = new EnlaceVivo(vivo, live, vistas, conector, reloj, planificador);

    // ===== evento de partida: marca y avisa en el orden de la base =====

    @Test void eventoDePartidaConfirmadaMarcaEnEstadoVivoYAvisaEnElOrdenDeLaBase() throws InterruptedException {
        red.cuerpo = "{\"matches\":[]}";   // la API no la tiene entre las últimas: SIN_DATOS, pero no TERMINADA -> se confirma
        Match m = partida(555, 7);
        enlace.procesarEventosSocket(List.of(new SocketVivo.Partida("matchAdded", m)), Set.of(7L));
        esperarAvisos(vistas, 4);
        assertEquals(List.of("liveEvento:7:false", "campana:7", "miPartida:7", "avisarTrasCambio"), vistas.avisos);
        assertEquals(555L, vivo.matchDe(7));
    }

    @Test void siLaApiDiceQueYaTerminoEsFantasmaYNoMarcaNiAvisa() throws InterruptedException {
        red.cuerpo = "{\"matches\":[" + partidaJson(555, Duration.ofMinutes(10), true) + "]}";
        Match m = partida(555, 7);
        enlace.procesarEventosSocket(List.of(new SocketVivo.Partida("matchAdded", m)), Set.of(7L));
        Thread.sleep(150);   // el hilo de confirmación corre igual: se le da tiempo aunque no deba avisar
        assertTrue(vistas.avisos.isEmpty(), "fantasma ignorado, sin avisos");
        assertFalse(vivo.jugando(7));
    }

    @Test void eventoSinJugadoresVigiladosNoAvisaNiConfirmaPorRed() throws InterruptedException {
        Match m = partida(555, 99);   // 99 no está en ids
        enlace.procesarEventosSocket(List.of(new SocketVivo.Partida("matchAdded", m)), Set.of(7L));
        Thread.sleep(100);
        assertTrue(vistas.avisos.isEmpty());
        assertTrue(red.cuerpo.equals("{\"matches\":[]}"));
    }

    @Test void eventoDePartidaTerminadaMarcaFueraYAvisaSinPasarPorLaRed() {
        Match m = partida(555, 7);
        m.finished = Instant.now();
        vivo.marcarJugando(7, 555);
        enlace.procesarEventosSocket(List.of(new SocketVivo.Partida("matchUpdated", m)), Set.of(7L));
        assertEquals(List.of("liveEvento:7:true", "avisarTrasCambio"), vistas.avisos, "terminada: se sabe sin red, no hace falta confirmarEventoSocket");
        assertFalse(vivo.jugando(7));
        assertTrue(vivo.terminada(555));
    }

    @Test void eventoQuitadaSacaDeEstadoVivoYAvisaSinRed() {
        vivo.marcarJugando(7, 555);
        enlace.procesarEventosSocket(List.of(new SocketVivo.Quitada(555)), Set.of(7L));
        assertEquals(List.of("liveEvento:7:true", "avisarTrasCambio"), vistas.avisos);
        assertFalse(vivo.jugando(7));
        assertTrue(vivo.terminada(555));
    }

    // ===== sincronizarSocket: mismo orden de ids que la base =====

    @Test void sincronizarSocketPideLosIdsEnElOrdenDeLaBase() {
        vistas.watchlist.addAll(List.of(30L, 10L));
        vistas.todos.addAll(List.of(10L, 20L));      // 10 duplicado: no se repite en el Set final
        vistas.topLadder.add(40L);
        vistas.extra = new LinkedHashSet<>(List.of(50L));
        enlace.sincronizarSocket();
        assertNotNull(conector.ultimaUrl);
        assertTrue(conector.ultimaUrl.contains("profile_ids=30,10,20,40,50"), conector.ultimaUrl);
    }

    // ===== iniciarPing/conectado/cerrar: delegados de una línea =====

    @Test void iniciarPingProgramaLaTareaPeriodicaDelSocket() {
        enlace.iniciarPing();
        assertEquals(1, planificador.periodicas);
    }

    @Test void conectadoEmpiezaEnFalsoYCerrarNoLanza() {
        assertFalse(enlace.conectado());
        assertDoesNotThrow(enlace::cerrar);
    }

    // ===== tras una caída y reconexión, avisa a Live now por la interfaz (nunca toca Swing aquí) =====

    @Test void trasCaidaYReconexionAvisaARefrescarLiveNow() {
        enlace.sincronizarSocket();                         // vacío: sin ids, sincronizar(ids) no abre nada todavía
        vistas.watchlist.add(1L);
        enlace.sincronizarSocket();                         // ahora sí abre
        CanalFalso c1 = new CanalFalso();
        conector.receptor.abierto(c1);
        assertTrue(vistas.avisos.isEmpty(), "primera vez, sin caída previa: no avisa");
        conector.receptor.cerrado(c1, 1006, "red");         // se cae
        conector.receptor = null;
        enlace.sincronizarSocket();                         // la app reintenta con los mismos ids (mismo camino que el vigilante)
        assertNotNull(conector.receptor, "abre una segunda conexión");
        conector.receptor.abierto(new CanalFalso());
        assertEquals(List.of("refrescarLiveNowSiAbierta"), vistas.avisos, "tras una caída, un barrido para reparar el estado");
    }
}
