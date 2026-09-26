package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.util.Reloj;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.LongConsumer;

import static dev.tirador.aoe2radar.cache.Vivos.enCursoReal;
import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;

/**
 * Los barridos por API de «quién de la lista está en partida» (Watchlist): la parte de red y de decisión de
 * vigilarVivos (toda la lista, por lotes) y refrescarWatchlist (jugador a jugador, con el ELO nocturno primero), y
 * la decisión de «Buscar partidas» sobre quién sigue vivo y quién queda fuera. APLICAR esas decisiones al estado
 * compartido (EstadoVivo, eloWatch, gamesWatch, la tabla) sigue en process()/done() de la app, en el EDT.
 * <p>resumen: la 1.1 tenía un solo método (resumenVivo) que, de paso, registraba la partida en EstadoVivo (rival,
 * «visto»): se recibe tal cual, sin tocarlo, para no duplicar esa regla (ver DEUDA).
 */
public final class BarridoVivos {

    /** El resultado de un lote de vigilarVivos: quién de idsLote está vivo (pid→matchId), su texto, y las
     *  partidas del lote que ya se ven terminadas (para refrescar filas EN DIRECTO de la tabla). */
    public record Lote(Set<Long> idsLote, Map<Long, Long> vivos, Map<Long, String> infos, List<Match> terminadas) { }

    /** El resultado de refrescar a UN jugador: vivo (matchId o null), su ELO 1v1 (o null) y el resumen de su
     *  partida en curso (o null). juegosNocturno solo viene puesto cuando salió del snapshot de ayer (sin red). */
    public record Refresco(long pid, Long vivo, String resumen, Integer elo, Integer juegosNocturno) {
        /** ¿Este refresco miró de verdad si el jugador está en partida? Solo el que fue a la API. El del snapshot
         *  nocturno (juegosNocturno puesto) no mira nada: su vivo null significa «sin dato», no «no juega», y quien
         *  lo aplica no debe tocar el estado en vivo (F4 de la revisión 1.3: apagaba el punto que ya había puesto
         *  el socket y apuntaba un fin de partida falso). */
        public boolean sabeSiJuega() { return juegosNocturno == null; }
    }

    /** La decisión de «Buscar partidas»: quién de `res` está vivo (pid→matchId, con su texto) y, de los seguidos
     *  consultados con éxito, quién no apareció vivo (por tanto, fuera). */
    public record DecisionBuscar(Map<Long, Long> vivos, Map<Long, String> infos, Set<Long> fuera) { }

    private final CompanionApi api;
    private final Reloj reloj;
    private final BiFunction<Match, Long, String> resumen;
    private final Map<Long, int[]> eloAyer;
    private final LongConsumer pausa;
    private final long pausaMs;
    private final int porPagina;

    public BarridoVivos(CompanionApi api, Reloj reloj, BiFunction<Match, Long, String> resumen, Map<Long, int[]> eloAyer,
                        LongConsumer pausa, long pausaMs, int porPagina) {
        this.api = api; this.reloj = reloj; this.resumen = resumen; this.eloAyer = eloAyer;
        this.pausa = pausa; this.pausaMs = pausaMs; this.porPagina = porPagina;
    }

    private Instant ahora() { return Instant.ofEpochMilli(reloj.ahoraMs()); }

    /**
     * vigilarVivos: un lote (hasta 25 ids), una sola llamada a /matches con todos a la vez. De las partidas que
     * salen: las en curso de verdad marcan vivo a cada jugador del lote (la primera que se le encuentra); las
     * demás que ya están terminadas se devuelven aparte (para refrescar una fila EN DIRECTO de la tabla). Va a la
     * red.
     */
    public Lote lote(List<Long> idsLote) throws IOException, InterruptedException {
        avisarSiUi("BarridoVivos.lote");
        Set<Long> ids = new LinkedHashSet<>(idsLote);
        StringBuilder csv = new StringBuilder();
        for (Long id : idsLote) { if (csv.length() > 0) csv.append(','); csv.append(id); }
        Iterable<Match> leidas = api.partidas(csv.toString(), 1, 50);
        Map<Long, Long> vivos = new HashMap<>();
        Map<Long, String> infos = new HashMap<>();
        List<Match> terminadas = new ArrayList<>();
        Instant ahora = ahora();
        for (Match m : leidas) {
            if (m == null) continue;
            if (enCursoReal(m, ahora)) {
                for (MatchPlayer mp : m.players)
                    if (ids.contains(mp.id) && !vivos.containsKey(mp.id)) {
                        vivos.put(mp.id, m.id);
                        infos.put(mp.id, resumen.apply(m, mp.id));
                    }
            } else if (m.finished != null) terminadas.add(m);
        }
        return new Lote(ids, vivos, infos, terminadas);
    }

    /**
     * refrescarWatchlist para UN jugador: primero el ELO de ayer (sin red); si ya lo hay (snap[0] > 0), ni una
     * llamada (el socket dirá si está en partida) y trae de paso las partidas jugadas (para gamesWatch). Si no,
     * sus últimas 10 partidas (vivo + ELO 1v1 ranked reciente); si esas 10 no traen ELO, una segunda vuelta con
     * porPagina (con la pausa de cortesía entre medias). Va a la red salvo con snapshot.
     */
    public Refresco refrescar(long pid) throws IOException, InterruptedException {
        int[] snap = eloAyer.get(pid);
        if (snap != null && snap[0] > 0) return new Refresco(pid, null, null, snap[0], snap[1]);
        avisarSiUi("BarridoVivos.refrescar");
        Iterable<Match> leidas = api.partidas(pid, 1, 10);
        Long vivo = null;
        String resV = null;
        Integer elo = null;
        Instant ahora = ahora();
        for (Match m : leidas) {
            if (m == null) continue;
            if (enCursoReal(m, ahora) && vivo == null) { vivo = m.id; resV = resumen.apply(m, pid); }
            if (elo == null && m.finished != null && m.players.size() == 2
                    && m.mode != null && m.mode.startsWith("1v1 Random"))
                for (MatchPlayer mp : m.players)
                    if (mp.id == pid && mp.rating != null) { elo = mp.rating; break; }
            if (vivo != null && elo != null) break;
        }
        if (elo == null) {   // sin 1v1 ranked entre sus últimas 10: mirar más atrás
            pausa.accept(pausaMs);
            Iterable<Match> leidasApi = api.partidas(pid, 1, porPagina);
            for (Match m : leidasApi) {
                if (m == null || m.finished == null || m.players.size() != 2
                        || m.mode == null || !m.mode.startsWith("1v1 Random")) continue;
                for (MatchPlayer mp : m.players)
                    if (mp.id == pid && mp.rating != null) { elo = mp.rating; break; }
                if (elo != null) break;
            }
        }
        return new Refresco(pid, vivo, resV, elo, null);
    }

    /**
     * «Buscar partidas»: de las partidas encontradas (res), quién de ellas está en curso de verdad (pid→matchId,
     * con su texto). De los seguidos (tracked) consultados con éxito (consultados: excluye a quienes fallaron o
     * quedaron fuera por el tope MAX_TOTAL de la búsqueda), los que no salieron vivos van a `fuera`: antes se
     * marcaba fuera a CUALQUIER seguido ausente de res, aunque su petición hubiera fallado (falso «fuera» y fin de
     * partida falso en EstadoVivo); con `consultados` ya no. Sin red.
     */
    public DecisionBuscar decidirVivos(List<Match> res, List<Long> tracked, Set<Long> consultados) {
        Map<Long, Long> vivos = new HashMap<>();
        Map<Long, String> infos = new HashMap<>();
        Instant ahora = ahora();
        for (Match m : res)
            if (enCursoReal(m, ahora))
                for (MatchPlayer p : m.players) {
                    vivos.put(p.id, m.id);
                    infos.put(p.id, resumen.apply(m, p.id));
                }
        Set<Long> fuera = new LinkedHashSet<>();
        for (long pid : tracked)
            if (!vivos.containsKey(pid) && consultados.contains(pid)) fuera.add(pid);
        return new DecisionBuscar(vivos, infos, fuera);
    }
}
