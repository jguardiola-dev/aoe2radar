package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.util.RelojFalso;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Protocolo del websocket «ongoing-matches» (CLAUDE.md: cortacircuitos y reconexión viven en un solo sitio),
 * sin red: un Conector falso que el test completa a mano, un Canal falso que cuenta pings y cierres, y un
 * Planificador falso cuyas tareas el test ejecuta cuando quiere. Primero se fija lo que el código hace hoy
 * (caracterización); después, las reglas del enunciado, una a una.
 */
class SocketVivoTest {

    // ----- dobles -----

    /** Guarda cada conectar(url, receptor) y devuelve un futuro que el test completa (o falla) cuando quiere. */
    static final class ConectorFalso implements SocketVivo.Conector {
        record Llamada(String url, SocketVivo.Receptor receptor, CompletableFuture<SocketVivo.Canal> futuro) { }
        final List<Llamada> llamadas = new ArrayList<>();
        boolean fallarAlMomento;   // como buildAsync cuando falla al construir: el futuro llega ya fallido, en el mismo hilo
        @Override public CompletableFuture<SocketVivo.Canal> conectar(String url, SocketVivo.Receptor r) {
            CompletableFuture<SocketVivo.Canal> f = fallarAlMomento ? CompletableFuture.failedFuture(new RuntimeException("boom al momento")) : new CompletableFuture<>();
            llamadas.add(new Llamada(url, r, f));
            return f;
        }
        Llamada ultima() { return llamadas.get(llamadas.size() - 1); }
    }

    /** Cuenta pings y cierres (con motivo). */
    static final class CanalFalso implements SocketVivo.Canal {
        int pings;
        final List<String> cierres = new ArrayList<>();
        @Override public void ping() { pings++; }
        @Override public void cerrar(String motivo) { cierres.add(motivo); }
        int abortos;
        @Override public void abortar() { abortos++; }
    }

    /** Guarda las tareas diferidas y periódicas; el test las ejecuta a mano. */
    static final class PlanificadorFalso implements SocketVivo.Planificador {
        static final class TareaFalsa implements Tarea {
            final long ms; final Runnable r; boolean ejecutada;
            TareaFalsa(long ms, Runnable r) { this.ms = ms; this.r = r; }
            @Override public boolean pendiente() { return !ejecutada; }
            void ejecutar() { r.run(); ejecutada = true; }   // como ScheduledFuture: pendiente hasta que el run TERMINA
        }
        record Periodica(long ms, Runnable r) { }
        final List<TareaFalsa> diferidas = new ArrayList<>();
        final List<Periodica> periodicas = new ArrayList<>();
        @Override public Tarea despues(long ms, Runnable r) { TareaFalsa t = new TareaFalsa(ms, r); diferidas.add(t); return t; }
        @Override public void cada(long ms, Runnable r) { periodicas.add(new Periodica(ms, r)); }
    }

    /** Apunta conectado(trasCaida) y cada eventos(lista, ids). */
    static final class OyenteFalso implements SocketVivo.Oyente {
        record Mensaje(List<SocketVivo.Evento> eventos, Set<Long> ids) { }
        final List<Boolean> conectados = new ArrayList<>();
        final List<Mensaje> mensajes = new ArrayList<>();
        @Override public void conectado(boolean trasCaida) { conectados.add(trasCaida); }
        @Override public void eventos(List<SocketVivo.Evento> eventos, Set<Long> ids) { mensajes.add(new Mensaje(eventos, ids)); }
    }

    final ConectorFalso conector = new ConectorFalso();
    final OyenteFalso oyente = new OyenteFalso();
    final RelojFalso reloj = new RelojFalso();
    final PlanificadorFalso planificador = new PlanificadorFalso();
    final SocketVivo socket = new SocketVivo(conector, oyente, reloj, planificador);

    // ----- helpers -----

    /** Simula que el transporte abre y adopta un canal para la última llamada pendiente. */
    CanalFalso completar(ConectorFalso.Llamada l) {
        CanalFalso c = new CanalFalso();
        l.receptor().abierto(c);
        l.futuro().complete(c);
        return c;
    }

    /** sincronizar(ids) + conectar con éxito de un tirón: el caso feliz que usan la mayoría de los tests. */
    CanalFalso conectarNuevo(Set<Long> ids) {
        socket.sincronizar(ids);
        return completar(conector.ultima());
    }

    void fallar(ConectorFalso.Llamada l) { l.futuro().completeExceptionally(new RuntimeException("boom")); }

    // ===== 1. URL =====

    @Test void urlLlevaElIdiomaYLosIdsEnElOrdenDelSet() {
        String antes = IDIOMA;
        try {
            IDIOMA = "es";   // fijado: no depender de lo que dejen otros tests
            Set<Long> ids = new LinkedHashSet<>(List.of(30L, 10L, 20L));
            socket.sincronizar(ids);
            assertEquals(SocketVivo.URL + "&language=es&profile_ids=30,10,20", conector.ultima().url());
        } finally { IDIOMA = antes; }
    }

    @Test void urlUsaEnCuandoElIdiomaEsIngles() {
        String antes = IDIOMA;
        try {
            IDIOMA = "en";
            socket.sincronizar(Set.of(1L));
            assertEquals(SocketVivo.URL + "&language=en&profile_ids=1", conector.ultima().url());
        } finally { IDIOMA = antes; }
    }

    @Test void unIdiomaDesconocidoCaeAIngles() {
        // El original: solo "es" da "es"; cualquier otra cosa (incluida basura) da "en".
        String antes = IDIOMA;
        try {
            IDIOMA = "fr";
            socket.sincronizar(Set.of(1L));
            assertTrue(conector.ultima().url().contains("&language=en&"));
        } finally { IDIOMA = antes; }
    }

    // ===== 2. sincronizar =====

    @Test void sincronizarConVacioCierra() {
        CanalFalso canal = conectarNuevo(Set.of(1L));
        socket.sincronizar(Set.of());
        assertEquals(List.of("bye"), canal.cierres);
        assertFalse(socket.conectado());
    }

    @Test void mismosIdsConLaConexionAbiertaNoReconecta() {
        Set<Long> ids = Set.of(1L, 2L);
        conectarNuevo(ids);
        int antes = conector.llamadas.size();
        socket.sincronizar(Set.of(2L, 1L));   // mismos elementos, otra instancia
        assertEquals(antes, conector.llamadas.size(), "no abre otra conexión");
    }

    @Test void mismosIdsSinConexionAbiertaCierraLaAnteriorYAbreOtra() {
        Set<Long> ids = Set.of(1L, 2L);
        CanalFalso canal = conectarNuevo(ids);
        conector.ultima().receptor().cerrado(canal, 1006, "red");   // se cae: conectado=false, pero el canal sigue siendo el actual
        socket.sincronizar(ids);   // mismos ids, sin conexión: cierra y reabre
        assertEquals(List.of("bye"), canal.cierres, "el canal caído se cierra otra vez (bye) antes de abrir otro");
        assertEquals(2, conector.llamadas.size(), "abre una segunda conexión");
    }

    @Test void idsDistintosCierraLaAnteriorYAbreOtra() {
        CanalFalso canal = conectarNuevo(Set.of(1L));
        socket.sincronizar(Set.of(2L));
        assertEquals(List.of("bye"), canal.cierres);
        assertEquals(2, conector.llamadas.size());
        assertEquals(SocketVivo.URL + "&language=es&profile_ids=2", conector.ultima().url());
    }

    // ===== 3. whenComplete =====

    @Test void siAlCompletarseLosIdsYaNoSonLosSuyosCierraStaleYNoAdopta() {
        socket.sincronizar(Set.of(1L));
        ConectorFalso.Llamada primera = conector.ultima();
        primera.receptor().abierto(new CanalFalso());   // llega a abrirse...
        socket.sincronizar(Set.of(2L));                 // ...pero mientras tanto los ids vigilados cambian
        CanalFalso tardio = new CanalFalso();
        primera.futuro().complete(tardio);              // el futuro de la conexión vieja completa tarde
        assertEquals(List.of("stale"), tardio.cierres, "no se adopta: se cierra con motivo stale");
    }

    @Test void siLaConexionFallaProgramaUnaReconexion() {
        socket.sincronizar(Set.of(1L));
        fallar(conector.ultima());
        assertFalse(socket.conectado());
        assertEquals(1, planificador.diferidas.size());
        assertEquals(5000, planificador.diferidas.get(0).ms);
    }

    // ===== 4. abierto =====

    @Test void abiertoMarcaConectadoYAvisaSinCaidaPreviaLaPrimeraVez() {
        conectarNuevo(Set.of(1L));
        assertTrue(socket.conectado());
        assertEquals(List.of(false), oyente.conectados);
    }

    @Test void trasUnCerradoElSiguienteAbiertoAvisaConTrasCaidaVerdadero() {
        CanalFalso c1 = conectarNuevo(Set.of(1L));
        conector.ultima().receptor().cerrado(c1, 1006, "red");
        planificador.diferidas.get(0).ejecutar();   // reabre
        completar(conector.ultima());
        assertEquals(List.of(false, true), oyente.conectados);
    }

    @Test void unErrorTambienCuentaComoCaida() {
        // En la 1.1 solo cerrado() marcaba caída: tras un error, al reconectar no se reparaba el estado (DEUDA, paso C2).
        socket.sincronizar(Set.of(1L));
        conector.ultima().receptor().error(new RuntimeException("boom"));
        planificador.diferidas.get(0).ejecutar();
        completar(conector.ultima());
        assertEquals(List.of(true), oyente.conectados, "tras un error, al reconectar se avisa de la caída (barrido de reparación)");
    }

    // ===== intentos numerados: solo cuenta el más reciente (paso C2) =====

    @Test void dosAbrirALaVez_ganaElUltimoYElViejoSeCierraSinMandarNada() {
        Set<Long> ids = Set.of(1L);
        socket.sincronizar(ids);
        ConectorFalso.Llamada vieja = conector.ultima();
        socket.abrir(ids);                                  // p. ej. la reconexión y la app a la vez
        ConectorFalso.Llamada nueva = conector.ultima();
        CanalFalso cNueva = completar(nueva);
        CanalFalso cVieja = completar(vieja);               // la vieja conecta DESPUÉS
        assertEquals(List.of("stale"), cVieja.cierres, "el intento viejo se cierra en cuanto conecta");
        assertTrue(cNueva.cierres.isEmpty(), "el nuevo sigue");
        vieja.receptor().texto("[{\"type\":\"matchRemoved\",\"data\":{\"match_id\":5}}]", true);
        assertTrue(oyente.mensajes.isEmpty(), "sin eventos duplicados de la conexión vieja");
        assertEquals(List.of(false), oyente.conectados, "solo el abierto del vigente cuenta");
    }

    @Test void unAbiertoTardioDeUnIntentoViejoNoMarcaConectadoYLaReconexionSigue() {
        // protección secuencial: un abierto viejo que llega tarde no marca conectado ni frena la reconexión. (La carrera
        // real que vio el revisor, un hilo colándose entre comprobar y escribir, la evita el candado; un test de un solo
        // hilo no la reproduce.)
        socket.sincronizar(Set.of(1L));
        ConectorFalso.Llamada vieja = conector.ultima();
        socket.sincronizar(Set.of(1L, 2L));
        fallar(conector.ultima());                           // el vigente falla: reconexión pendiente
        vieja.receptor().abierto(new CanalFalso());          // el viejo abre tarde
        assertFalse(socket.conectado(), "un intento viejo no puede marcar conectado");
        int antes = conector.llamadas.size();
        planificador.diferidas.get(0).ejecutar();
        assertEquals(antes + 1, conector.llamadas.size(), "la reconexión abre: el socket no se queda muerto");
    }

    @Test void unaReconexionPendienteSirveAlUltimoIntentoQueFallo() {
        // lo vio el revisor en C2: la pendiente del intento 1 ocupaba el sitio y, al dispararse, no abría: socket muerto
        socket.sincronizar(Set.of(1L));
        fallar(conector.ultima());                           // reconexión pendiente (intento 1)
        socket.sincronizar(Set.of(1L, 2L));
        fallar(conector.ultima());                           // el nuevo también falla: la pendiente pasa a servirle a él
        assertEquals(1, planificador.diferidas.size(), "sigue habiendo una sola pendiente");
        int antes = conector.llamadas.size();
        planificador.diferidas.get(0).ejecutar();
        assertEquals(antes + 1, conector.llamadas.size(), "abre: el socket no se queda muerto");
        assertTrue(conector.ultima().url().endsWith("profile_ids=1,2") || conector.ultima().url().endsWith("profile_ids=2,1"), "con los ids nuevos");
    }

    @Test void unTrozoDeLaConexionAnteriorNoSePegaAlPrimerMensaje() {
        socket.sincronizar(Set.of(1L));
        ConectorFalso.Llamada primera = conector.ultima();
        completar(primera);
        primera.receptor().texto("[{\"type\":\"matchRem", false);   // a medias...
        primera.receptor().cerrado(null, 1006, "red");                  // ...y se cae
        planificador.diferidas.get(0).ejecutar();
        ConectorFalso.Llamada segunda = conector.ultima();
        completar(segunda);
        segunda.receptor().texto("[{\"type\":\"matchRemoved\",\"data\":{\"match_id\":7}}]", true);
        assertEquals(1, oyente.mensajes.size());
        assertEquals(List.of(new SocketVivo.Quitada(7)), oyente.mensajes.get(0).eventos(), "el mensaje nuevo, entero y limpio");
    }

    @Test void laReconexionNoReabreSiOtroIntentoEstaEnVuelo() {
        socket.sincronizar(Set.of(1L));
        fallar(conector.ultima());                           // reconexión pendiente por el intento 1
        socket.sincronizar(Set.of(1L, 2L));                  // la app abre otro (aún sin conectar)
        int antes = conector.llamadas.size();
        planificador.diferidas.get(0).ejecutar();
        assertEquals(antes, conector.llamadas.size(), "ya hay un intento más nuevo: la reconexión vieja no abre otro");
    }

    @Test void unCierrePedidoPorNosotrosNoEsCaidaNiReconecta() {
        socket.sincronizar(Set.of(1L));
        ConectorFalso.Llamada l = conector.ultima();
        CanalFalso c = completar(l);
        socket.sincronizar(Set.of());                       // se vacía la lista
        assertEquals(List.of("bye"), c.cierres);
        l.receptor().cerrado(c, 1000, "bye");               // el servidor confirma el cierre
        assertTrue(planificador.diferidas.isEmpty(), "en la 1.1 esto reconectaba con los ids antiguos");
        assertFalse(socket.conectado());
    }

    @Test void vaciarLaListaAnulaUnaReconexionPendiente() {
        socket.sincronizar(Set.of(1L));
        fallar(conector.ultima());                          // reconexión pendiente
        int antes = conector.llamadas.size();
        socket.sincronizar(Set.of());
        planificador.diferidas.get(0).ejecutar();
        assertEquals(antes, conector.llamadas.size(), "sin ids no se vuelve a abrir");
    }

    @Test void laReconexionNoAbreOtraSiYaHayConexion() {
        socket.sincronizar(Set.of(1L));
        fallar(conector.ultima());                          // reconexión pendiente
        socket.sincronizar(Set.of(1L, 2L));                 // la app reconecta por su cuenta
        completar(conector.ultima());
        int antes = conector.llamadas.size();
        planificador.diferidas.get(0).ejecutar();
        assertEquals(antes, conector.llamadas.size(), "ya conectado: la tarea no abre una segunda conexión");
        assertTrue(socket.conectado());
    }

    @Test void alAbrirseLosReintentosVuelvenACeroYLaSiguienteEsperaVuelveA5s() {
        socket.sincronizar(Set.of(1L));
        fallar(conector.ultima());                                    // 5 s
        planificador.diferidas.get(0).ejecutar();
        fallar(conector.ultima());                                    // 10 s
        assertEquals(10_000, planificador.diferidas.get(1).ms);
        planificador.diferidas.get(1).ejecutar();
        ConectorFalso.Llamada exitosa = conector.ultima();
        completar(exitosa);                                           // éxito: reintentos vuelve a 0
        exitosa.receptor().error(new RuntimeException("otra vez"));
        assertEquals(5_000, planificador.diferidas.get(2).ms, "tras conectar, la escalada vuelve a empezar en 5 s");
    }

    // ===== 5. mensajes troceados =====

    @Test void variosTrozosMasElUltimoProducenUnSoloEventos() {
        conectarNuevo(Set.of(1L));
        SocketVivo.Receptor r = conector.ultima().receptor();
        String json = "[{\"type\":\"matchAdded\",\"data\":{\"match_id\":10,\"players\":[]}}]";
        r.texto(json.substring(0, 6), false);
        r.texto(json.substring(6, 20), false);
        r.texto(json.substring(20), true);
        assertEquals(1, oyente.mensajes.size(), "un solo eventos() aunque llegara en tres trozos");
        assertEquals(1, oyente.mensajes.get(0).eventos().size());
    }

    @Test void unPongProduceEventosVacio() {
        conectarNuevo(Set.of(1L));
        conector.ultima().receptor().texto("{\"type\":\"pong\"}", true);
        assertEquals(List.of(), oyente.mensajes.get(0).eventos());
    }

    @Test void unJsonRotoNoLanzaYElSiguienteMensajeFunciona() {
        conectarNuevo(Set.of(1L));
        SocketVivo.Receptor r = conector.ultima().receptor();
        assertDoesNotThrow(() -> r.texto("{roto", true));
        assertEquals(0, oyente.mensajes.size(), "el mensaje roto no llega al oyente");
        r.texto("{\"type\":\"pong\"}", true);
        assertEquals(1, oyente.mensajes.size(), "el siguiente mensaje sí llega");
    }

    // ===== 6. leer() =====

    @Test void leerTraduceMatchRemovedConMatchIdOConMatchIdCamelCase() {
        assertEquals(List.of(new SocketVivo.Quitada(5)), SocketVivo.leer("[{\"type\":\"matchRemoved\",\"data\":{\"match_id\":5}}]"));
        assertEquals(List.of(new SocketVivo.Quitada(7)), SocketVivo.leer("[{\"type\":\"matchRemoved\",\"data\":{\"matchId\":7}}]"));
    }

    @Test void leerTraduceMatchAddedYMatchUpdatedConSuTipoYLaPartida() {
        List<SocketVivo.Evento> a = SocketVivo.leer("[{\"type\":\"matchAdded\",\"data\":{\"match_id\":1}}]");
        SocketVivo.Partida pa = (SocketVivo.Partida) a.get(0);
        assertEquals("matchAdded", pa.tipo());
        assertEquals(1L, pa.partida().id);

        List<SocketVivo.Evento> b = SocketVivo.leer("[{\"type\":\"matchUpdated\",\"data\":{\"match_id\":2}}]");
        assertEquals("matchUpdated", ((SocketVivo.Partida) b.get(0)).tipo());
    }

    @Test void leerIgnoraOtrosTipos() {
        assertEquals(List.of(), SocketVivo.leer("[{\"type\":\"otroTipo\",\"data\":{\"match_id\":1}}]"));
    }

    @Test void leerSaltaUnaPartidaIlegibleYSigueConLasDemas() {
        // sin match_id, parseMatch devuelve null: Parseo.parseMatch descarta id<=0
        List<SocketVivo.Evento> a = SocketVivo.leer(
                "[{\"type\":\"matchAdded\",\"data\":{}},{\"type\":\"matchAdded\",\"data\":{\"match_id\":9}}]");
        assertEquals(1, a.size());
        assertEquals(9L, ((SocketVivo.Partida) a.get(0)).partida().id);
    }

    // ===== 7. cerrado =====

    @Test void cerradoDelCanalActualMarcaCaidaYProgramaReconexion() {
        CanalFalso c = conectarNuevo(Set.of(1L));
        conector.ultima().receptor().cerrado(c, 1006, "red");
        assertFalse(socket.conectado());
        assertEquals(1, planificador.diferidas.size());
    }

    @Test void cerradoAntesDeAdoptarElCanalCuentaPorqueEsDelIntentoVigente() {
        socket.sincronizar(Set.of(1L));
        ConectorFalso.Llamada l = conector.ultima();
        CanalFalso c = new CanalFalso();
        l.receptor().abierto(c);
        l.receptor().cerrado(c, 1006, "rapido");   // se cierra antes de que el futuro complete y se adopte
        assertFalse(socket.conectado());
        assertEquals(1, planificador.diferidas.size(), "cuenta: todavía no había canal actual");
    }

    @Test void cerradoDeUnCanalViejoNoCuentaSiYaHayOtroCanalActual() {
        CanalFalso canal1 = conectarNuevo(Set.of(1L));
        ConectorFalso.Llamada llamada1 = conector.ultima();
        socket.sincronizar(Set.of(2L));         // cierra canal1 (bye) y abre otra conexión
        completar(conector.ultima());           // canal2 adoptado: ya hay un canal actual distinto de canal1
        llamada1.receptor().cerrado(canal1, 999, "tarde");
        assertEquals(0, planificador.diferidas.size(), "el cierre de canal1, ya sustituido, no cuenta");
        assertTrue(socket.conectado(), "el canal actual sigue conectado");
    }

    // ===== 8. reconexión =====

    @Test void lasEsperasDeReconexionEscalanHastaElTopeDe120s() {
        socket.sincronizar(Set.of(1L));
        long[] esperadas = { 5000, 10000, 20000, 40000, 80000, 120000, 120000 };
        for (long esperada : esperadas) {
            fallar(conector.ultima());
            PlanificadorFalso.TareaFalsa t = planificador.diferidas.get(planificador.diferidas.size() - 1);
            assertEquals(esperada, t.ms);
            t.ejecutar();
        }
    }

    @Test void siLaReconexionFallaAlMomentoProgramaOtra() {
        socket.sincronizar(new LinkedHashSet<>(List.of(1L)));
        fallar(conector.ultima());                                   // primera conexión: falla → reconexión a 5 s
        assertEquals(1, planificador.diferidas.size());
        conector.fallarAlMomento = true;                             // al reconectar, buildAsync falla en el mismo hilo
        planificador.diferidas.get(0).ejecutar();
        assertEquals(2, planificador.diferidas.size(), "la tarea en marcha ya no cuenta como pendiente: se programa la siguiente");
        assertEquals(10_000, planificador.diferidas.get(1).ms, "con su espera doblada");
    }

    @Test void unSegundoFalloAntesDeEjecutarseNoProgramaOtraReconexion() {
        socket.sincronizar(Set.of(1L));
        ConectorFalso.Llamada l = conector.ultima();
        fallar(l);
        assertEquals(1, planificador.diferidas.size());
        l.receptor().error(new RuntimeException("boom2"));   // un segundo fallo del mismo receptor, antes de que la tarea corra
        assertEquals(1, planificador.diferidas.size(), "sigue pendiente la primera: no se programa una segunda");
    }

    @Test void alEjecutarseLaReconexionReabreConLosIdsActuales() {
        socket.sincronizar(Set.of(1L, 2L));
        fallar(conector.ultima());
        planificador.diferidas.get(0).ejecutar();
        assertEquals(2, conector.llamadas.size());
        assertTrue(conector.ultima().url().contains("profile_ids=1,2") || conector.ultima().url().contains("profile_ids=2,1"));
    }

    // ===== 9. ping =====

    @Test void iniciarPingProgramaUnaTareaCada30000ms() {
        socket.iniciarPing();
        assertEquals(1, planificador.periodicas.size());
        assertEquals(30_000, planificador.periodicas.get(0).ms());
    }

    @Test void elPingSoloSuenaSiHayCanalYEstaConectado() {
        socket.iniciarPing();
        Runnable tarea = planificador.periodicas.get(0).r();
        assertDoesNotThrow(tarea::run);   // sin canal todavía: no hace nada, no lanza
        CanalFalso c = conectarNuevo(Set.of(1L));
        tarea.run();
        assertEquals(1, c.pings);
    }

    @Test void noHacePingSiElCanalActualNoEstaConectado() {
        socket.iniciarPing();
        Runnable tarea = planificador.periodicas.get(0).r();
        CanalFalso c = conectarNuevo(Set.of(1L));
        conector.ultima().receptor().cerrado(c, 1006, "red");   // el canal sigue siendo el actual, pero conectado=false
        tarea.run();
        assertEquals(0, c.pings, "hay canal pero no está conectado: no hace ping");
    }

    // ===== 10. sano() =====

    @Test void sanoEsFalsoSinHaberseConectadoNunca() {
        assertFalse(socket.sano());
    }

    @Test void sanoEsVerdaderoJustoTrasConectar() {
        conectarNuevo(Set.of(1L));
        assertTrue(socket.sano());
    }

    @Test void sanoSigueSiendoloUnMilisegundoAntesDeLosDiezMinutos() {
        conectarNuevo(Set.of(1L));
        reloj.avanzar(10 * 60_000L - 1);
        assertTrue(socket.sano());
    }

    // ===== 11. conexión colgada (fila 88) =====

    @Test void unPongMantieneSanaLaConexionAunqueNoLleguenMensajes() {
        conectarNuevo(Set.of(1L));
        reloj.avanzar(9 * 60_000L);
        conector.ultima().receptor().pong();
        reloj.avanzar(9 * 60_000L);   // 18 min sin textos, pero con un pong hace 9
        assertTrue(socket.sano());
    }

    @Test void elPongDeUnaConexionViejaNoCuenta() {
        conectarNuevo(Set.of(1L));
        ConectorFalso.Llamada vieja = conector.ultima();
        socket.sincronizar(Set.of(2L));
        completar(conector.ultima());
        reloj.avanzar(9 * 60_000L);
        vieja.receptor().pong();
        reloj.avanzar(2 * 60_000L);
        assertFalse(socket.sano(), "solo cuenta el pong de la conexión vigente");
    }

    @Test void colgadaDiezMinutosSeCierraYSeProgramaLaReconexion() {
        socket.iniciarPing();
        CanalFalso c = conectarNuevo(Set.of(1L));
        reloj.avanzar(10 * 60_000L);
        planificador.periodicas.get(0).r().run();
        assertFalse(socket.conectado());
        assertEquals(1, c.abortos, "colgada: se corta con abort, sin esperar la despedida");
        assertTrue(c.cierres.isEmpty(), "sin cierre educado: nadie contestaría");
        assertEquals(0, c.pings, "a una conexión colgada ya no se le hace ping");
        assertEquals(1, planificador.diferidas.size(), "reconexión programada");
        planificador.diferidas.get(0).ejecutar();
        completar(conector.ultima());
        assertEquals(List.of(false, true), oyente.conectados, "al volver, avisa de la caída para que la app repare");
        assertTrue(socket.sano(), "al reabrir, la salud se cuenta desde ahora");
    }

    @Test void otroTickConLaReconexionPendienteNoCierraNiReprogramaOtraVez() {
        socket.iniciarPing();
        CanalFalso c = conectarNuevo(Set.of(1L));
        reloj.avanzar(10 * 60_000L);
        planificador.periodicas.get(0).r().run();
        reloj.avanzar(30_000L);
        planificador.periodicas.get(0).r().run();
        assertEquals(1, c.abortos, "colgada: se corta con abort, sin esperar la despedida");
        assertTrue(c.cierres.isEmpty(), "sin cierre educado: nadie contestaría");
        assertEquals(1, planificador.diferidas.size());
    }

    @Test void sanaNoSeTocaYElPingSigue() {
        socket.iniciarPing();
        CanalFalso c = conectarNuevo(Set.of(1L));
        reloj.avanzar(10 * 60_000L - 1);
        planificador.periodicas.get(0).r().run();
        assertTrue(socket.conectado());
        assertTrue(c.cierres.isEmpty());
        assertEquals(1, c.pings);
        assertTrue(planificador.diferidas.isEmpty());
    }

    @Test void elCierreQueLlegaTrasDarlaPorColgadaNoProgramaOtraReconexion() {
        socket.iniciarPing();
        CanalFalso c = conectarNuevo(Set.of(1L));
        reloj.avanzar(10 * 60_000L);
        planificador.periodicas.get(0).r().run();
        conector.ultima().receptor().cerrado(c, 1000, "colgado");   // el transporte confirma el cierre después
        assertEquals(1, planificador.diferidas.stream().filter(PlanificadorFalso.TareaFalsa::pendiente).count());
    }

    @Test void sanoDejaDeSerloALosDiezMinutosJustos() {
        // frontera estricta: reloj.ahoraMs() - ultimoMsgMs < 10*60_000, no <=
        conectarNuevo(Set.of(1L));
        reloj.avanzar(10 * 60_000L);
        assertFalse(socket.sano());
    }
}
