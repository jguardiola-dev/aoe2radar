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
        /** Esperas pedidas con despues(), en orden, y sus tareas (no se ejecutan solas: el test decide). */
        final List<Long> esperas = new ArrayList<>();
        final List<Runnable> tareas = new ArrayList<>();
        @Override public Tarea despues(long ms, Runnable r) { synchronized (esperas) { esperas.add(ms); tareas.add(r); } return () -> false; }
        @Override public void cada(long ms, Runnable r) { periodicas++; }
    }
    static final class TransporteFalso implements Transporte {
        String cuerpo = "{\"matches\":[]}";
        int estado = 200;
        volatile int llamadas;
        @Override public Respuesta get(String url) { llamadas++; return new Respuesta(estado, cuerpo); }
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
        final List<Match> partidasLive = new ArrayList<>();   // la partida que llegó con cada liveEvento (null si ninguna)
        @Override public void liveEvento(long pid, Match m, boolean terminada) { avisos.add("liveEvento:" + pid + ":" + terminada); partidasLive.add(m); }
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

    // Decisión de Jorge (fase 4): matchRemoved solo saca a los jugadores si la API confirma que la partida terminó,
    // y se le pregunta 3 min después (antes la API aún no lo sabe). comprobarQuitada se llama a mano: es lo que hace
    // el hilo «socket-quitada» cuando vence la espera.

    List<Long> esperasQuitada() {
        synchronized (planificador.esperas) { return planificador.esperas.stream().filter(ms -> ms == EnlaceVivo.ESPERA_QUITADA_MS).toList(); }
    }

    @Test void eventoQuitadaNoSacaANadieAlMomentoNiVaALaRedYProgramaLaComprobacion() throws InterruptedException {
        vivo.marcarJugando(7, 555);
        enlace.procesarEventosSocket(List.of(new SocketVivo.Quitada(555)), Set.of(7L));
        Thread.sleep(50);
        assertTrue(vistas.avisos.isEmpty(), "sin avisos hasta que la API confirme");
        assertEquals(555L, vivo.matchDe(7));
        assertEquals(0, red.llamadas, "no se pregunta al momento");
        assertEquals(List.of(EnlaceVivo.ESPERA_QUITADA_MS), esperasQuitada());
    }

    @Test void dosQuitadasDeLaMismaPartidaProgramanUnaSolaComprobacion() {
        vivo.marcarJugando(7, 555);
        enlace.procesarEventosSocket(List.of(new SocketVivo.Quitada(555), new SocketVivo.Quitada(555)), Set.of(7L));
        enlace.procesarEventosSocket(List.of(new SocketVivo.Quitada(555)), Set.of(7L));
        assertEquals(1, esperasQuitada().size());
    }

    @Test void quitadaConfirmadaPorLaApiSacaDeEstadoVivoYAvisa() {
        red.cuerpo = "{\"matches\":[" + partidaJson(555, Duration.ofMinutes(30), true) + "]}";
        vivo.marcarJugando(7, 555);
        enlace.comprobarQuitada(555, 1);
        assertEquals(List.of("liveEvento:7:true", "avisarTrasCambio"), vistas.avisos);
        assertFalse(vivo.jugando(7));
        assertTrue(vivo.terminada(555));
    }

    /** Revisión 1.3, F5: la terminada llega a Live now con la partida de la API (con su hora de fin), no con null. */
    @Test void quitadaConfirmadaPasaALiveNowLaPartidaDeLaApiConSuFin() {
        red.cuerpo = "{\"matches\":[" + partidaJson(555, Duration.ofMinutes(30), true) + "]}";
        vivo.marcarJugando(7, 555);
        enlace.comprobarQuitada(555, 1);
        Match fin = vistas.partidasLive.get(0);
        assertNotNull(fin, "antes llegaba null y Live now pintaba «hace 0 min»");
        assertEquals(555L, fin.id);
        assertNotNull(fin.finished);
    }

    @Test void quitadaSinDatosSigueSinPartidaParaLiveNow() {
        red.cuerpo = "{\"matches\":[]}";
        vivo.marcarJugando(7, 555);
        enlace.comprobarQuitada(555, 2);   // último intento sin datos: sale sin partida (no hay otra)
        assertEquals(1, vistas.partidasLive.size());
        assertNull(vistas.partidasLive.get(0));
    }

    @Test void quitadaDeUnaPartidaQueLaApiVeVivaNoSacaANadie() {
        red.cuerpo = "{\"matches\":[" + partidaJson(555, Duration.ofMinutes(10), false) + "]}";
        vivo.marcarJugando(7, 555);
        enlace.comprobarQuitada(555, 1);
        assertEquals(1, red.llamadas);
        assertTrue(vistas.avisos.isEmpty(), "sigue en juego: ni avisos ni cambios");
        assertEquals(555L, vivo.matchDe(7));
        assertFalse(vivo.terminada(555), "no se apunta como terminada: podría volver a marcarse");
        assertEquals(List.of(EnlaceVivo.ESPERA_QUITADA_MS), esperasQuitada(), "viva: se vuelve a mirar en 3 min (tope: 3 h de enCursoReal)");
    }

    @Test void laTareaProgramadaComprueba_yUnaQuitadaResueltaLiberaLaPartida() throws InterruptedException {
        red.cuerpo = "{\"matches\":[" + partidaJson(555, Duration.ofMinutes(30), true) + "]}";
        vivo.marcarJugando(7, 555);
        enlace.procesarEventosSocket(List.of(new SocketVivo.Quitada(555)), Set.of(7L));
        Runnable tarea; synchronized (planificador.esperas) { tarea = planificador.tareas.get(planificador.esperas.indexOf(EnlaceVivo.ESPERA_QUITADA_MS)); }
        tarea.run();   // lo que hace el planificador al vencer: arranca el hilo «socket-quitada»
        esperarAvisos(vistas, 2);
        assertEquals(List.of("liveEvento:7:true", "avisarTrasCambio"), vistas.avisos);
        assertTrue(vivo.terminada(555));
        vivo.marcarJugando(8, 556);   // otra partida quitada después: se programa sin que la anterior la bloquee
        enlace.procesarEventosSocket(List.of(new SocketVivo.Quitada(556)), Set.of(8L));
        assertEquals(2, esperasQuitada().size());
    }

    @Test void unaQuitadaResueltaSinNadieDentroLiberaLaPartidaParaOtraQuitada() {
        vivo.marcarJugando(7, 555);
        enlace.procesarEventosSocket(List.of(new SocketVivo.Quitada(555)), Set.of(7L));
        vivo.marcarFuera(7);                 // salió por otro camino (barrido, matchUpdated) antes de la comprobación
        enlace.comprobarQuitada(555, 1);     // no queda nadie: no va a la red y libera la partida
        assertEquals(0, red.llamadas);
        vivo.marcarJugando(7, 555);          // vuelve a marcarse (la partida no se apuntó como terminada)
        enlace.procesarEventosSocket(List.of(new SocketVivo.Quitada(555)), Set.of(7L));
        assertEquals(2, esperasQuitada().size(), "la segunda quitada se programa: la primera no la bloquea");
    }

    @Test void quitadaSinDatosReintentaUnaVezYLuegoSaca() {
        red.cuerpo = "{\"matches\":[]}";   // la API no la tiene: SIN_DATOS
        vivo.marcarJugando(7, 555);
        enlace.comprobarQuitada(555, 1);
        assertTrue(vistas.avisos.isEmpty());
        assertEquals(555L, vivo.matchDe(7));
        assertEquals(List.of(EnlaceVivo.ESPERA_QUITADA_MS), esperasQuitada(), "un reintento programado");
        enlace.comprobarQuitada(555, 2);
        assertEquals(List.of("liveEvento:7:true", "avisarTrasCambio"), vistas.avisos, "sin datos tras el reintento: fuera, para no dejarlo colgado");
        assertFalse(vivo.jugando(7));
        assertFalse(vivo.terminada(555), "sin datos no es «terminada»: un barrido puede volver a marcarlo");
        assertEquals(1, esperasQuitada().size(), "no hay un tercer intento");
    }

    @Test void quitadaConLaApiCaidaTrasElReintentoNoLaBloqueaTresHoras() {
        red.estado = 500;   // la API falla: SIN_DATOS con error
        vivo.marcarJugando(7, 555);
        enlace.comprobarQuitada(555, 1);
        enlace.comprobarQuitada(555, 2);
        assertFalse(vivo.jugando(7));
        assertFalse(vivo.terminada(555));
        assertTrue(vivo.marcarJugando(7, 555), "el barrido puede volver a marcarlo cuando la API vuelva");
    }

    @Test void quitadaSinJugadoresMarcadosNoVaALaRedNiProgramaNada() {
        enlace.procesarEventosSocket(List.of(new SocketVivo.Quitada(555)), Set.of(7L));
        enlace.comprobarQuitada(555, 1);   // y si vence una espera cuando ya no queda nadie, tampoco
        assertEquals(0, red.llamadas);
        assertTrue(vistas.avisos.isEmpty());
        assertTrue(esperasQuitada().isEmpty());
        assertFalse(vivo.terminada(555));
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
