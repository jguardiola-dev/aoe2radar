package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Player;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * «Buscar partidas» por lotes (1.4) contra el CompanionApi de verdad, con un transporte falso que hace de /matches
 * (varios profile_ids juntos, de la más reciente a la más antigua por inicio, sin repetir la compartida, página a
 * página: lo que devolvió la API real al comprobarlo el 2026-09-28). Cuenta las llamadas del recorrido de la 1.3
 * (jugador a jugador, 50 por página) y del de ahora (lotes de 10, 100 por página) y comprueba que las partidas
 * encontradas son las mismas. Sin red.
 */
class BuscarPorLotesTest {

    /** Una partida del servidor falso: id, inicio y fin (segundos) y sus dos jugadores. */
    record P(long id, long inicio, long fin, long a, long b) { }

    /** /matches?profile_ids=…&page=…&per_page=… sobre una lista fija de partidas. Cuenta las llamadas. */
    static final class ServidorFalso implements Transporte {
        final List<P> partidas = new ArrayList<>();
        final AtomicInteger llamadas = new AtomicInteger();
        @Override public Respuesta get(String url) {
            llamadas.incrementAndGet();
            Map<String, String> q = new HashMap<>();
            for (String par : url.substring(url.indexOf('?') + 1).split("&")) q.put(par.substring(0, par.indexOf('=')), par.substring(par.indexOf('=') + 1));
            Set<Long> ids = new TreeSet<>();
            for (String s : q.get("profile_ids").split(",")) ids.add(Long.parseLong(s));
            int pagina = Integer.parseInt(q.get("page")), porPagina = Integer.parseInt(q.get("per_page"));
            List<P> suyas = new ArrayList<>();
            for (P p : partidas) if (ids.contains(p.a()) || ids.contains(p.b())) suyas.add(p);
            suyas.sort(Comparator.comparingLong(P::inicio).reversed());
            StringBuilder json = new StringBuilder("{\"page\":" + pagina + ",\"perPage\":" + porPagina + ",\"matches\":[");
            int desde = (pagina - 1) * porPagina, hasta = Math.min(suyas.size(), desde + porPagina);
            for (int i = desde; i < hasta; i++) {
                P p = suyas.get(i);
                if (i > desde) json.append(',');
                json.append("{\"matchId\":").append(p.id()).append(",\"started\":").append(p.inicio()).append(",\"finished\":").append(p.fin() == 0 ? "null" : String.valueOf(p.fin()))
                    .append(",\"leaderboardName\":\"1v1 Random Map\",\"mapName\":\"Arabia\",\"teams\":[")
                    .append("{\"players\":[{\"profileId\":").append(p.a()).append(",\"name\":\"j").append(p.a()).append("\"}]},")
                    .append("{\"players\":[{\"profileId\":").append(p.b()).append(",\"name\":\"j").append(p.b()).append("\"}]}]}");
            }
            return new Respuesta(200, json.append("]}").toString());
        }
    }

    static final Instant AHORA = Instant.parse("2026-09-28T12:00:00Z");

    /** 30 jugadores (ids 1..30) con 8 partidas al día durante 5 días; 1 de cada 4, contra otro jugador de la lista
     *  (una partida compartida: sale una vez). El resto, contra rivales de fuera. */
    static ServidorFalso servidor() {
        ServidorFalso s = new ServidorFalso();
        long id = 1000;
        for (int j = 1; j <= 30; j++)
            for (int k = 0; k < 40; k++) {
                long fin = AHORA.getEpochSecond() - (k * 3 * 3600L) - j * 60L;   // una cada 3 h, escalonadas por jugador
                long rival = (k % 4 == 0 && j < 30) ? j + 1 : 90_000 + id;
                s.partidas.add(new P(++id, fin - 1800, fin, j, rival));
            }
        return s;
    }

    static List<Player> treinta() {
        List<Player> l = new ArrayList<>();
        for (int i = 1; i <= 30; i++) l.add(new Player(i, "j" + i, "G"));
        return l;
    }

    /** Lo que hace CableadoPartidas.paginaDePartidas: los ids en CSV, una llamada a CompanionApi.partidas. */
    static PartidasPresenter.Recorrido buscar(ServidorFalso red, int horas, int tamLote, int porPagina) {
        return buscar(red, horas, tamLote, porPagina, m -> false);
    }

    static PartidasPresenter.Recorrido buscar(ServidorFalso red, int horas, int tamLote, int porPagina, java.util.function.Predicate<Match> enCurso) {
        CompanionApi companion = new CompanionApi(new ApiClient(new WatchlistViewTest.ThrottleSinFreno(), red, s -> { }, () -> false));
        return PartidasPresenter.recorrer(treinta(), AHORA.minusSeconds(horas * 3600L), tamLote, porPagina, 0,
                (pids, pag, pp) -> {
                    StringBuilder csv = new StringBuilder();
                    for (Long pid : pids) { if (csv.length() > 0) csv.append(','); csv.append(pid); }
                    return companion.partidas(csv.toString(), pag, pp);
                },
                enCurso, () -> false, s -> { });
    }

    static List<Long> ids(PartidasPresenter.Recorrido r) { return r.lista().stream().map(m -> m.id).sorted().toList(); }

    @Test void treintaJugadores24h_de30llamadasA3_yLasMismasPartidas() {
        ServidorFalso antes = servidor(), ahora = servidor();
        PartidasPresenter.Recorrido r13 = buscar(antes, 24, 1, 50);
        PartidasPresenter.Recorrido r14 = buscar(ahora, 24, PartidasPresenter.JUGADORES_POR_LOTE, PartidasPresenter.PARTIDAS_POR_LOTE);
        assertEquals(30, antes.llamadas.get(), "1.3: una llamada por jugador");
        assertEquals(3, ahora.llamadas.get(), "1.4: una por lote de 10");
        assertEquals(ids(r13), ids(r14), "las mismas partidas");
        assertEquals(r13.exitosos(), r14.exitosos());
        assertFalse(r14.topeAlcanzado());
        assertEquals(0, r14.fallos());
    }

    @Test void treintaJugadores48h_paginaPorLote_yLasMismasPartidas() {
        ServidorFalso antes = servidor(), ahora = servidor();
        PartidasPresenter.Recorrido r13 = buscar(antes, 48, 1, 50);
        PartidasPresenter.Recorrido r14 = buscar(ahora, 48, PartidasPresenter.JUGADORES_POR_LOTE, PartidasPresenter.PARTIDAS_POR_LOTE);
        assertEquals(ids(r13), ids(r14), "las mismas partidas");
        assertTrue(ahora.llamadas.get() < antes.llamadas.get(), "menos llamadas: " + ahora.llamadas.get() + " frente a " + antes.llamadas.get());
        assertTrue(ahora.llamadas.get() >= 6, "cada lote necesita más de una página: " + ahora.llamadas.get());
        System.out.println("Buscar partidas, 30 jugadores, 48 h: " + antes.llamadas.get() + " llamadas (1.3) -> " + ahora.llamadas.get() + " (1.4)");
    }

    @Test void losEstadosVivosSeDecidenIgual_unaEnCursoDeUnJugadorDelLote() {
        // Una partida sin fin (en curso) de j5: sale en la página 1 de su lote como en la suya propia.
        ServidorFalso antes = servidor(), ahora = servidor();
        antes.partidas.add(new P(1, AHORA.getEpochSecond() - 600, 0, 5, 99_999));
        ahora.partidas.add(new P(1, AHORA.getEpochSecond() - 600, 0, 5, 99_999));
        Map<Long, Match> vivas13 = new LinkedHashMap<>(), vivas14 = new LinkedHashMap<>();
        for (Match m : buscar(antes, 24, 1, 50, m -> m.finished == null).lista()) if (m.finished == null) vivas13.put(m.id, m);
        for (Match m : buscar(ahora, 24, 10, 100, m -> m.finished == null).lista()) if (m.finished == null) vivas14.put(m.id, m);
        assertEquals(Set.of(1L), vivas13.keySet());
        assertEquals(vivas13.keySet(), vivas14.keySet());
    }
}
