package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los barridos de la Watchlist (vigilarVivos, refrescarWatchlist y la decisión de «Buscar partidas»), sin red
 * (transporte falso) y con la hora fija (RelojFalso). resumen es un doble simple (no EstadoVivo real): solo
 * interesa que BarridoVivos lo llame con la partida y el pid correctos.
 */
class BarridoVivosTest {
    static final long HORA = 1_700_000_000_000L;   // nov. 2023, en ms (util.RelojFalso: ver instrucciones del encargo)

    /** Responde /matches según el per_page pedido (10: primera pasada de refrescar; 50: lote y segunda pasada). */
    static final class TransporteFalso implements Transporte {
        final Map<Integer, String> porPerPage = new HashMap<>();
        final List<String> urls = new ArrayList<>();
        String porDefecto = "{\"matches\":[]}";
        int estado = 200;
        @Override public Transporte.Respuesta get(String url) {
            urls.add(url);
            Matcher m = Pattern.compile("per_page=(\\d+)").matcher(url);
            int pp = m.find() ? Integer.parseInt(m.group(1)) : -1;
            return new Transporte.Respuesta(estado, porPerPage.getOrDefault(pp, porDefecto));
        }
    }

    static final class ThrottleSinFreno implements Throttle {
        @Override public void adquirir(BooleanSupplier cancelar) { }
        @Override public long registrar429() { return 0; }
    }

    final TransporteFalso red = new TransporteFalso();
    final CompanionApi api = new CompanionApi(new ApiClient(new ThrottleSinFreno(), red, s -> { }, () -> false));
    final RelojFalso reloj = new RelojFalso();
    { reloj.ahora = HORA; }
    final Map<Long, int[]> eloAyer = new HashMap<>();
    final List<Long> pausas = new ArrayList<>();
    final List<Object[]> resumenLlamadas = new ArrayList<>();
    static final int PER_PAGE = 50;
    static final long PAUSA_MS = 300;

    BarridoVivos nuevo() {
        return new BarridoVivos(api, reloj, (m, pid) -> { resumenLlamadas.add(new Object[]{ m.id, pid }); return "R:" + m.id + ":" + pid; },
                eloAyer, pausas::add, PAUSA_MS, PER_PAGE);
    }

    // ---------- helpers para construir el JSON de /matches ----------

    String jugador(long pid) { return "{\"profile_id\":" + pid + ",\"name\":\"P" + pid + "\"}"; }

    String jugadorRanked(long pid, int rating) {
        return "{\"profile_id\":" + pid + ",\"name\":\"P" + pid + "\",\"rating\":" + rating + "}";
    }

    /** Partida en curso de verdad: empezada hace `haceMs`, sin terminar. */
    String viva(long matchId, long haceMs, String... jugadores) {
        return "{\"match_id\":" + matchId + ",\"started\":" + (HORA - haceMs) + ",\"players\":[" + String.join(",", jugadores) + "]}";
    }

    /** Partida terminada: empezada y terminada hace `haceMs`. */
    String terminada(long matchId, long haceMs, String... jugadores) {
        long ini = HORA - haceMs;
        return "{\"match_id\":" + matchId + ",\"started\":" + ini + ",\"finished\":" + (ini + 60_000)
                + ",\"players\":[" + String.join(",", jugadores) + "]}";
    }

    /** Partida 1v1 ranked terminada, con ELO para ambos: para las pasadas de refrescar(). */
    String ranked1v1(long matchId, long haceMs, long pidA, int ratingA, long pidB, int ratingB) {
        long ini = HORA - haceMs;
        return "{\"match_id\":" + matchId + ",\"started\":" + ini + ",\"finished\":" + (ini + 60_000)
                + ",\"leaderboard_name\":\"1v1 Random Map\",\"players\":["
                + jugadorRanked(pidA, ratingA) + "," + jugadorRanked(pidB, ratingB) + "]}";
    }

    void responderDefault(String... matches) { red.porDefecto = cuerpo(matches); }
    void responder(int perPage, String... matches) { red.porPerPage.put(perPage, cuerpo(matches)); }
    String cuerpo(String... matches) { return "{\"matches\":[" + String.join(",", matches) + "]}"; }

    // ===================== lote (vigilarVivos) =====================

    @Test void lote_conVivosYTerminadas() throws Exception {
        responderDefault(
                viva(100, Duration.ofMinutes(10).toMillis(), jugador(1), jugador(50)),      // pid 1 vivo en 100
                terminada(101, Duration.ofMinutes(5).toMillis(), jugador(2)),                // terminada: entra en `terminadas`
                viva(103, Duration.ofMinutes(1).toMillis(), jugador(1)));                    // pid 1 YA vivo por 100: no se pisa
        BarridoVivos.Lote r = nuevo().lote(List.of(1L, 2L, 3L, 4L));
        assertEquals(Set.of(1L, 2L, 3L, 4L), r.idsLote());
        assertEquals(Map.of(1L, 100L), r.vivos(), "solo el 1 está vivo, y con la PRIMERA partida en la que se le ve (100, no 103)");
        assertEquals("R:100:1", r.infos().get(1L));
        assertEquals(1, r.terminadas().size());
        assertEquals(101L, r.terminadas().get(0).id);
        assertTrue(red.urls.get(0).contains("profile_ids=1,2,3,4") && red.urls.get(0).contains("per_page=50"),
                "una sola llamada, con los 4 ids y per_page=50");
    }

    @Test void lote_partidaViejaDeMasDeTresHorasNiVivaNiTerminada() throws Exception {
        responderDefault(viva(200, Duration.ofHours(3).plusMinutes(1).toMillis(), jugador(1)));   // fantasma: sin finished, empezada hace >3h
        BarridoVivos.Lote r = nuevo().lote(List.of(1L));
        assertTrue(r.vivos().isEmpty(), "más de 3 h sin terminar: no es una partida real (fantasma de crash)");
        assertTrue(r.terminadas().isEmpty(), "sin finished: tampoco es una terminada");
    }

    @Test void lote_fallDeLaLlamadaNoDevuelveNada() {
        red.estado = 500;
        BarridoVivos b = nuevo();
        assertThrows(Exception.class, () -> b.lote(List.of(1L, 2L)), "quien llama (vigilarVivos) decide no publicar nada de este lote");
    }

    // ===================== refrescar (refrescarWatchlist) =====================

    @Test void refrescar_conEloNocturnoNoVaALaRed() throws Exception {
        eloAyer.put(7L, new int[]{ 1500, 42, 1600, 10 });   // {elo1v1, partidas1v1, eloEq, partidasEq}
        BarridoVivos.Refresco r = nuevo().refrescar(7L);
        assertEquals(new BarridoVivos.Refresco(7L, null, null, 1500, 42), r);
        assertFalse(r.sabeSiJuega(), "F4 1.3: del snapshot no se sabe si juega: vivo null es «sin dato», no «fuera»");
        assertTrue(red.urls.isEmpty(), "el snapshot de ayer ya lo dice: ni una llamada (el socket avisará si juega)");
        assertTrue(pausas.isEmpty(), "sin llamada de red, tampoco la pausa de la segunda vuelta (esa pausa es del servicio; "
                + "la pausa de cortesía ENTRE jugadores de refrescarWatchlist es de la app: dormir(PAUSA_MS/2) en su doInBackground, "
                + "que con este camino se salta con un `continue`, como en la 1.1)");
    }

    @Test void refrescar_conEloDeAyerEnCeroVaALaRed() throws Exception {
        eloAyer.put(7L, new int[]{ 0, 5, 1600, 3 });   // snap[0] (elo1v1 de ayer) == 0: sin ELO 1v1 que restar, no vale como snapshot
        responder(10, viva(305, Duration.ofMinutes(1).toMillis(), jugador(7)));
        BarridoVivos.Refresco r = nuevo().refrescar(7L);
        assertEquals(305L, r.vivo(), "snap[0] == 0 no vale como snapshot: se va a la red igual que sin snapshot");
        assertNull(r.juegosNocturno(), "el snapshot no se usó: gamesWatch no se toca");
        assertTrue(r.sabeSiJuega(), "fue a la API: su vivo sí dice si juega");
        assertFalse(red.urls.isEmpty());
    }

    @Test void refrescar_sinSnapshotYSinEloEnLasUltimas10VaAUnaSegundaVuelta() throws Exception {
        // primera pasada (per_page=10): nada de 1v1 ranked reciente, y nadie en curso
        responder(10, terminada(300, Duration.ofDays(1).toMillis(), jugadorRanked(7, 1400)));   // sin leaderboard_name: no cuenta como 1v1 ranked
        // segunda pasada (per_page=50): sí aparece un 1v1 ranked con su elo
        responder(50, ranked1v1(301, Duration.ofDays(3).toMillis(), 7, 1550, 8, 1600));
        BarridoVivos.Refresco r = nuevo().refrescar(7L);
        assertEquals(7L, r.pid());
        assertNull(r.vivo());
        assertEquals(1550, r.elo(), "el ELO viene de la segunda vuelta (per_page=50)");
        assertNull(r.juegosNocturno(), "sin snapshot: gamesWatch no se toca");
        assertEquals(2, red.urls.size(), "dos llamadas: la de 10 y, al no traer ELO, la de 50");
        assertEquals(List.of(PAUSA_MS), pausas, "una pausa de cortesía entre la primera y la segunda vuelta");
    }

    @Test void refrescar_cuentaLaPrimeraPartidaVivaEncontrada() throws Exception {
        responder(10,
                viva(306, Duration.ofMinutes(3).toMillis(), jugador(7)),   // la primera: esta es la que cuenta
                viva(307, Duration.ofMinutes(1).toMillis(), jugador(7)));  // una segunda partida viva: se ignora
        BarridoVivos.Refresco r = nuevo().refrescar(7L);
        assertEquals(306L, r.vivo(), "la PRIMERA partida en curso que aparece, no la más reciente ni la última");
        assertEquals("R:306:7", r.resumen());
    }

    @Test void refrescar_vivoYEloEnLaPrimeraVueltaNoHaceSegundaLlamada() throws Exception {
        // en la primera pasada (per_page=10) ya salen las dos cosas: una partida en curso Y (en otra fila) un
        // 1v1 ranked terminado con elo
        responder(10,
                viva(302, Duration.ofMinutes(5).toMillis(), jugadorRanked(7, 1500)),
                ranked1v1(303, Duration.ofDays(1).toMillis(), 7, 1520, 9, 1400));
        BarridoVivos.Refresco r = nuevo().refrescar(7L);
        assertEquals(302L, r.vivo());
        assertEquals("R:302:7", r.resumen());
        assertEquals(1520, r.elo());
        assertEquals(1, red.urls.size(), "vivo Y elo ya en la primera vuelta: no hace falta la segunda");
        assertTrue(pausas.isEmpty());
    }

    // ===================== decidirVivos («Buscar partidas») =====================

    @Test void decidirVivos_vivosYFueraSoloDeLosConsultadosConExito() {
        Match vivaA = matchDe(400, Duration.ofMinutes(2).toMillis(), 1);
        List<Match> res = List.of(vivaA);
        // 1: vivo en res. 2: consultado con éxito, no sale vivo -> fuera. 3: NO consultado con éxito (falló o se
        // cortó por MAX_TOTAL), no sale vivo -> NO se marca fuera (bug arreglado: sin ese falso «fuera»).
        BarridoVivos.DecisionBuscar dec = nuevo().decidirVivos(res, List.of(1L, 2L, 3L), Set.of(1L, 2L));
        assertEquals(Map.of(1L, 400L), dec.vivos());
        assertEquals("R:400:1", dec.infos().get(1L));
        assertEquals(Set.of(2L), dec.fuera(), "solo el 2 (consultado con éxito y sin partida viva); el 3 no se toca");
    }

    @Test void decidirVivos_vivoEnResAunqueNoEsteEnConsultados() {
        // «consultados» solo decide a quién se le puede marcar FUERA; a quien SÍ sale vivo en res se le marca
        // vivo pase lo que pase (p. ej. lo vio el socket entretanto, o su página sí llegó pese al fallo de otra).
        Match vivaA = matchDe(401, Duration.ofMinutes(1).toMillis(), 9);
        BarridoVivos.DecisionBuscar dec = nuevo().decidirVivos(List.of(vivaA), List.of(9L), Set.of());
        assertEquals(Map.of(9L, 401L), dec.vivos());
        assertTrue(dec.fuera().isEmpty());
    }

    /** Un Match en curso (a mano, sin pasar por el parser de JSON: aquí solo interesa la decisión). */
    Match matchDe(long id, long haceMs, long... pids) {
        Match m = new Match();
        m.id = id;
        m.started = java.time.Instant.ofEpochMilli(HORA - haceMs);
        for (long pid : pids) { MatchPlayer p = new MatchPlayer(); p.id = pid; p.name = "P" + pid; m.players.add(p); }
        return m;
    }
}
