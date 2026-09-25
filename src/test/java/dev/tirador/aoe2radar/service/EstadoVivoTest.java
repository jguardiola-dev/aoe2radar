package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.util.RelojFalso;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Quién está en partida ahora (service.EstadoVivo), con un reloj falso (util.RelojFalso): nunca el reloj real
 * (EstadoVivo.SISTEMA), para que el test sea determinista. No hay red ni disco: EstadoVivo es memoria pura.
 * Primero se fija lo que la clase hace hoy (caracterización, incluida alguna rareza heredada de la 1.1);
 * después las reglas de negocio (freno de «en curso de verdad», fantasmas fuera de aquí en cache.Vivos).
 */
class EstadoVivoTest {
    /** Una hora fija (nov. 2023): el reloj falso la marca y las fechas de las partidas se cuentan desde ella. */
    static final long HORA = 1_700_000_000_000L;
    static RelojFalso reloj() { RelojFalso r = new RelojFalso(); r.ahora = HORA; return r; }
    static Instant ahora() { return Instant.ofEpochMilli(HORA); }


    private static Match partidaConJugadores(Instant started, Instant finished, boolean fantasma, MatchPlayer... ps) {
        Match m = new Match();
        m.started = started;
        m.finished = finished;
        m.fantasma = fantasma;
        m.players = new ArrayList<>(Arrays.asList(ps));
        return m;
    }

    private static MatchPlayer jugador(long id, String name) {
        MatchPlayer p = new MatchPlayer();
        p.id = id;
        p.name = name;
        return p;
    }

    // ----- 1. marcarJugando -----

    @Test void marcarJugando_sinTexto_marcaJugandoYMatchDeYQuitaNadieJugando() {
        EstadoVivo e = new EstadoVivo(reloj());
        assertTrue(e.nadieJugando());

        e.marcarJugando(1, 100);

        assertTrue(e.jugando(1));
        assertEquals(100L, e.matchDe(1));
        assertFalse(e.nadieJugando());
    }

    @Test void marcarJugando_conTexto_guardaElTexto() {
        EstadoVivo e = new EstadoVivo(reloj());

        e.marcarJugando(1, 100, "Subiendo el ELO");

        assertEquals("Subiendo el ELO", e.info(1));
    }

    @Test void marcarJugando_conTextoNull_borraElTextoAnterior() {
        // En la 1.1 esto lanzaba NullPointerException (info.put(pid, null) con un Map que no lo admite).
        EstadoVivo e = new EstadoVivo(reloj());
        e.marcarJugando(1, 100, "algo");

        e.marcarJugando(1, 100, null);

        assertNull(e.info(1));
        assertTrue(e.jugando(1), "sigue jugando: null solo afecta al texto");
    }

    // ----- 2. ponerInfo -----

    @Test void ponerInfo_conTexto_loGuarda() {
        EstadoVivo e = new EstadoVivo(reloj());

        e.ponerInfo(1, "algo");

        assertEquals("algo", e.info(1));
    }

    @Test void ponerInfo_conNull_borraElTexto() {
        EstadoVivo e = new EstadoVivo(reloj());
        e.ponerInfo(1, "algo");

        e.ponerInfo(1, null);

        assertNull(e.info(1));
    }

    // ----- 3. marcarFuera -----

    @Test void marcarFuera_borraPuntoTextoYRival_dejaPartidaYVisto() {
        RelojFalso r = reloj();
        EstadoVivo e = new EstadoVivo(r);
        Match m = partidaConJugadores(ahora().minus(Duration.ofMinutes(5)), null, false,
                jugador(1, "Yo"), jugador(2, "Rival"));
        e.registrar(m, 1);                       // guarda partida + visto + rival
        e.marcarJugando(1, m.id, "jugando");
        assertNotNull(e.rival(1), "para que la prueba sea significativa, tiene que haber rival antes");
        Long visto = e.vistoMs(1);
        assertNotNull(visto);

        e.marcarFuera(1);

        assertFalse(e.jugando(1));
        assertNull(e.matchDe(1));
        assertNull(e.info(1));
        assertNull(e.rival(1));
        assertSame(m, e.partida(1), "la partida completa se queda, como en la 1.1");
        assertEquals(visto, e.vistoMs(1), "el 'visto' se queda, como en la 1.1");
    }

    // ----- 4. quitarPartida -----

    @Test void quitarPartida_sacaSoloALosDeEsaPartidaYDevuelveSusPids() {
        EstadoVivo e = new EstadoVivo(reloj());
        e.marcarJugando(1, 100);
        e.marcarJugando(2, 100);
        e.marcarJugando(3, 200);

        List<Long> fuera = e.quitarPartida(100);

        assertEquals(new HashSet<>(List.of(1L, 2L)), new HashSet<>(fuera));
        assertEquals(2, fuera.size(), "sin duplicados ni de más");
        assertFalse(e.jugando(1));
        assertFalse(e.jugando(2));
        assertTrue(e.jugando(3), "no estaba en esa partida");
    }

    @Test void quitarPartida_conMidQueNadieTiene_devuelveListaVacia() {
        EstadoVivo e = new EstadoVivo(reloj());
        e.marcarJugando(3, 200);

        List<Long> fuera = e.quitarPartida(999);

        assertTrue(fuera.isEmpty());
        assertTrue(e.jugando(3));
    }

    // ----- 5. guardarPartida / soltarPartida -----

    @Test void soltarPartida_devuelveLaPartidaYLaQuita() {
        EstadoVivo e = new EstadoVivo(reloj());
        Match m = new Match();
        e.guardarPartida(7, m);

        assertSame(m, e.soltarPartida(7));
        assertNull(e.soltarPartida(7), "ya se soltó: la segunda vez no hay nada");
    }

    @Test void soltarPartida_sinPartidaGuardada_devuelveNull() {
        EstadoVivo e = new EstadoVivo(reloj());

        assertNull(e.soltarPartida(99));
    }

    // ----- 6. registrar -----

    @Test void registrar_decideEnCursoConSuRelojNoConElReal() {
        RelojFalso r = reloj();
        EstadoVivo e = new EstadoVivo(r);
        Match m = partidaConJugadores(ahora().minus(Duration.ofMinutes(5)), null, false, jugador(1, "Yo"), jugador(2, "Rival"));
        r.ahora = HORA + Duration.ofHours(3).toMillis();   // para SU reloj ya han pasado más de 3 h desde el inicio
        e.registrar(m, 1);
        assertNull(e.partida(1), "con la hora real (2026) tampoco contaría, pero así se ve que manda el reloj inyectado");
        assertNull(e.vistoMs(1));
        r.ahora = HORA;
        e.registrar(m, 2);
        assertSame(m, e.partida(2), "con su reloj en la hora de la partida, sí");
    }

    @Test void registrar_enCursoDeVerdad_guardaPartidaYSellaVisto() {
        RelojFalso r = reloj();
        r.ahora = HORA + 1_000;   // el reloj avanza: el sello es el del reloj, no el de la partida
        EstadoVivo e = new EstadoVivo(r);
        Match m = partidaConJugadores(ahora().minus(Duration.ofMinutes(5)), null, false,
                jugador(1, "Yo"), jugador(2, "Rival"));

        e.registrar(m, 1);

        assertSame(m, e.partida(1));
        assertEquals(HORA + 1_000, e.vistoMs(1));
    }

    @Test void registrar_partidaTerminada_niPartidaNiVisto() {
        EstadoVivo e = new EstadoVivo(reloj());
        Match m = partidaConJugadores(ahora().minus(Duration.ofMinutes(5)), ahora(), false,
                jugador(1, "Yo"), jugador(2, "Rival"));

        e.registrar(m, 1);

        assertNull(e.partida(1));
        assertNull(e.vistoMs(1));
    }

    @Test void registrar_empezadaHaceMasDeTresHoras_niPartidaNiVisto() {
        EstadoVivo e = new EstadoVivo(reloj());
        Match m = partidaConJugadores(ahora().minus(Duration.ofHours(4)), null, false,
                jugador(1, "Yo"), jugador(2, "Rival"));

        e.registrar(m, 1);

        assertNull(e.partida(1));
        assertNull(e.vistoMs(1));
    }

    @Test void registrar_unoContraUno_guardaElRival() {
        EstadoVivo e = new EstadoVivo(reloj());
        Match m = partidaConJugadores(ahora(), null, false, jugador(1, "Yo"), jugador(2, "Rival"));

        e.registrar(m, 1);

        assertEquals(new EstadoVivo.Rival(2, "Rival"), e.rival(1));
    }

    @Test void registrar_enEquipos_sinRival() {
        EstadoVivo e = new EstadoVivo(reloj());
        Match m = partidaConJugadores(ahora(), null, false,
                jugador(1, "Yo"), jugador(2, "A"), jugador(3, "B"), jugador(4, "C"));

        e.registrar(m, 1);

        assertNull(e.rival(1));
    }

    @Test void registrar_unoContraUnoConElMismoPidEnLosDos_sinRival() {
        // Caracterización: datos raros (el mismo id en las dos plazas) no rompen nada, solo no hay rival.
        EstadoVivo e = new EstadoVivo(reloj());
        Match m = partidaConJugadores(ahora(), null, false, jugador(1, "Yo"), jugador(1, "Yo"));

        e.registrar(m, 1);

        assertNull(e.rival(1));
    }

    @Test void registrar_rivalSeGuardaAunqueLaPartidaYaTermino_esRaro() {
        // Caracterización: el guardado del rival no depende de si la partida sigue en curso, solo de que
        // haya 2 jugadores (el código lo hace fuera del "if enCursoReal"). Si esto cambia sin querer, rojo.
        EstadoVivo e = new EstadoVivo(reloj());
        Match m = partidaConJugadores(ahora().minus(Duration.ofMinutes(5)), ahora(), false,
                jugador(1, "Yo"), jugador(2, "Rival"));

        e.registrar(m, 1);

        assertNull(e.partida(1), "la partida ya terminó: no se guarda");
        assertEquals(new EstadoVivo.Rival(2, "Rival"), e.rival(1), "pero el rival sí se guarda igualmente");
    }

    // ----- 7. concurrencia -----

    @Test void marcarJugandoMarcarFueraYQuitarPartidaEnParalelo_sinExcepcionesYConEstadoCoherente() throws Exception {
        final EstadoVivo e = new EstadoVivo(reloj());
        final int hilos = 8;
        final int pids = 50;
        final int matches = 5;
        final int iteraciones = 3000;

        CountDownLatch arrancar = new CountDownLatch(1);
        CountDownLatch terminado = new CountDownLatch(hilos);
        List<Throwable> fallos = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(hilos);

        for (int h = 0; h < hilos; h++) {
            final long semilla = h;
            pool.submit(() -> {
                try {
                    arrancar.await();
                    Random rnd = new Random(semilla);
                    for (int i = 0; i < iteraciones; i++) {
                        long pid = rnd.nextInt(pids);
                        long mid = (i / 300) * matches + rnd.nextInt(matches);   // partidas nuevas cada 300 vueltas: con C4 una quitada ya no vuelve, y sin esto el test dejaría de escribir
                        switch (rnd.nextInt(4)) {
                            case 0 -> e.marcarJugando(pid, mid);
                            case 1 -> e.marcarFuera(pid);
                            case 2 -> e.quitarPartida(mid);
                            default -> {
                                // lecturas concurrentes: solo deben no romper nada
                                e.jugando(pid);
                                e.matchDe(pid);
                                e.info(pid);
                                e.rival(pid);
                                e.nadieJugando();
                            }
                        }
                    }
                } catch (Throwable t) {
                    fallos.add(t);
                } finally {
                    terminado.countDown();
                }
            });
        }

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            arrancar.countDown();               // todos a la vez
            assertTrue(terminado.await(15, TimeUnit.SECONDS), "no debería bloquearse nunca");
        });
        pool.shutdownNow();

        assertTrue(fallos.isEmpty(), "ninguna excepción concurrente: " + fallos);

        // Con marcarJugando/marcarFuera/quitarPartida como únicas escrituras, el último en tocar un pid deja
        // uno de dos estados posibles: "jugando" (con su punto) o "fuera del todo" (punto, texto y rival a
        // null). Este invariante debe cumplirse pase lo que pase con el orden de los hilos.
        for (long pid = 0; pid < pids; pid++) {
            boolean jugando = e.jugando(pid);
            assertEquals(jugando, e.matchDe(pid) != null, "pid " + pid);
            if (!jugando) {
                assertNull(e.info(pid), "pid " + pid + " no jugando debería tener info null");
                assertNull(e.rival(pid), "pid " + pid + " no jugando debería tener rival null");
            }
        }
    }

    // ===== una partida terminada no vuelve (LiveService C4: gana el dato más reciente) =====

    @Test void unaPartidaQuitadaNoVuelveAMarcarseJugando() {
        EstadoVivo e = new EstadoVivo(reloj());
        e.marcarJugando(1, 555);
        e.quitarPartida(555);                                         // el socket: matchRemoved
        assertFalse(e.marcarJugando(1, 555, "vs X"), "un barrido con la foto de antes no la resucita");
        assertFalse(e.jugando(1));
        assertNull(e.info(1));
        assertTrue(e.marcarJugando(1, 556), "otra partida sí");
        assertEquals(556L, e.matchDe(1));
    }

    @Test void apuntarTerminadaSinQuitarTambienBloquea() {
        EstadoVivo e = new EstadoVivo(reloj());
        e.apuntarTerminada(555);                                      // el socket: matchUpdated con finished
        assertFalse(e.marcarJugando(2, 555));
        assertFalse(e.jugando(2));
        assertTrue(e.terminada(555));
    }

    @Test void lasTerminadasSeOlvidanALasTresHoras() {
        RelojFalso r = reloj();
        EstadoVivo e = new EstadoVivo(r);
        e.apuntarTerminada(555);
        r.ahora = HORA + Duration.ofHours(3).toMillis() - 1;
        e.apuntarTerminada(1);                                        // limpia al apuntar: la 555 aún no ha cumplido 3 h
        assertTrue(e.terminada(555));
        r.ahora = HORA + Duration.ofHours(3).toMillis();
        e.apuntarTerminada(2);
        assertFalse(e.terminada(555), "a las 3 h justas ya no se recuerda (ninguna partida «en curso» dura más)");
    }

    // ===== fin de partida por jugador (LiveService D: la regla del ELO) =====

    @Test void marcarFueraSoloApuntaElFinSiEstabaJugando() {
        RelojFalso r = reloj();
        EstadoVivo e = new EstadoVivo(r);
        e.marcarFuera(1);                                             // un barrido: no estaba jugando
        assertNull(e.finMs(1), "no jugaba: no ha terminado nada");
        e.marcarJugando(1, 555);
        r.ahora = HORA + 5_000;
        e.marcarFuera(1);
        assertEquals(HORA + 5_000, e.finMs(1), "jugaba y ya no: su partida acaba de terminar");
        r.ahora = HORA + 9_000;
        e.marcarFuera(1);                                             // otro barrido después: no mueve el fin
        assertEquals(HORA + 5_000, e.finMs(1));
    }

    @Test void quitarPartidaApuntaElFinDeCadaJugador() {
        RelojFalso r = reloj();
        EstadoVivo e = new EstadoVivo(r);
        e.marcarJugando(1, 555);
        e.marcarJugando(2, 555);
        e.quitarPartida(555);
        assertEquals(HORA, e.finMs(1));
        assertEquals(HORA, e.finMs(2));
    }
}
