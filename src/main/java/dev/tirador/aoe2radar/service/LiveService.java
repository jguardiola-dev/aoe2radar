package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.FuentePartidas;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.util.Reloj;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.tirador.aoe2radar.cache.Vivos.enCursoReal;
import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;

/**
 * Live now: las reglas que necesitan la API. De momento, la de los fantasmas: el companion a veces anuncia como viva
 * una partida que ya terminó (por el socket, o en la lista de «En partida ahora»), y la API tiene la última palabra.
 * Crece en la fase 2 (paso C) con el resto de reglas del directo.
 */
public final class LiveService {

    /** VIVA / TERMINADA: la API encontró la partida entre las últimas del jugador. SIN_DATOS: no apareció o la API falló. */
    public enum Veredicto { VIVA, TERMINADA, SIN_DATOS }

    /** El veredicto, con la partida tal como la da la API (si apareció) o el error (si falló). */
    public record Comprobacion(Veredicto veredicto, Match partida, Exception error) { }

    private final FuentePartidas api;
    private final Reloj reloj;

    public LiveService(FuentePartidas api, Reloj reloj) { this.api = api; this.reloj = reloj; }

    /**
     * ¿Sigue en curso la partida matchId? Mira las `ultimas` partidas de pid (una llamada): si aparece, decide
     * enCursoReal con la hora del reloj; si no aparece o la API falla, SIN_DATOS (cada llamador decide qué hacer con la
     * duda). No lanza. Va a la red.
     */
    public Comprobacion comprobar(long pid, long matchId, int ultimas) {
        avisarSiUi("LiveService.comprobar");
        try {
            for (Match r : api.partidas(pid, 1, ultimas)) {
                if (r != null && r.id == matchId)
                    return new Comprobacion(enCursoReal(r, Instant.ofEpochMilli(reloj.ahoraMs())) ? Veredicto.VIVA : Veredicto.TERMINADA, r, null);
            }
            return new Comprobacion(Veredicto.SIN_DATOS, null, null);
        } catch (Exception ex) {   // también InterruptedException, como en la 1.1 (ver DEUDA)
            return new Comprobacion(Veredicto.SIN_DATOS, null, ex);
        }
    }

    /** Lo que da {@link #comprobarVarias}: un veredicto por partida y el started más antiguo de lo que devolvió la API
     *  (null si no devolvió nada legible). La API da las partidas de más nueva a más vieja, mezclando las de todos los
     *  pids (comprobado con una respuesta real de 4 pids, guardada en src/test/resources/api: ver LiveServiceTest): si la más antigua de la
     *  página empezó bastante antes que una partida buscada y esta no vino, es que la API no la tiene (no que la
     *  taparan las de los otros jugadores). Ver {@link #ausenteSegura}. */
    public record Lote(Map<Long, Comprobacion> veredictos, Instant masAntigua) { }

    /** Margen para {@link #ausenteSegura}: el started del socket y el de la API pueden no coincidir al segundo. */
    static final long MARGEN_AUSENTE_MS = 5 * 60_000;

    /** Un started anterior a esto es un dato roto (la API da 0 cuando no lo sabe: Json.when lo lee como 1970) y no
     *  cuenta para masAntigua: si no, cualquier ausencia parecería segura. */
    static final Instant STARTED_MINIMO = Instant.parse("2020-01-01T00:00:00Z");

    /** ¿Se puede fiar el SIN_DATOS de una partida que empezó en `started` y no vino en el lote? Sí si la página llega
     *  hasta partidas que empezaron (con margen) antes que ella: habría salido. Si no se sabe (sin started o página
     *  sin partidas), no: el llamador pregunta por ella sola, como antes. */
    public static boolean ausenteSegura(Lote lote, Instant started) {
        return lote.masAntigua() != null && started != null && lote.masAntigua().toEpochMilli() < started.toEpochMilli() - MARGEN_AUSENTE_MS;
    }

    /**
     * Varias partidas en UNA llamada: pide las últimas `porPagina` partidas de todos los pids a la vez (CSV, como el
     * barrido de Live now) y decide cada matchId como {@link #comprobar}: si aparece, enCursoReal con la hora del reloj;
     * si no, SIN_DATOS (con varios pids, una ausente pudo quedar tapada por las partidas de los otros: el llamador
     * decide con {@link #ausenteSegura} si pregunta por ella sola). Con un solo pid pide lo mismo que comprobar(pid, …).
     * Si la API falla, todas SIN_DATOS con el error. No lanza. Va a la red: lo usa la confirmación en lote del socket
     * (EnlaceVivo: partidas nuevas y partidas quitadas).
     */
    public Lote comprobarVarias(Collection<Long> pids, Collection<Long> matchIds, int porPagina) {
        avisarSiUi("LiveService.comprobarVarias");
        Map<Long, Comprobacion> veredictos = new LinkedHashMap<>();
        try {
            Set<Long> buscadas = new HashSet<>(matchIds);
            Iterable<Match> partidas = pids.size() == 1 ? api.partidas(pids.iterator().next(), 1, porPagina)
                    : api.partidas(pids.stream().map(String::valueOf).collect(Collectors.joining(",")), 1, porPagina);
            Instant ahora = Instant.ofEpochMilli(reloj.ahoraMs());
            Instant masAntigua = null;
            for (Match r : partidas) {
                if (r != null && r.started != null && !r.started.isBefore(STARTED_MINIMO) && (masAntigua == null || r.started.isBefore(masAntigua))) masAntigua = r.started;
                if (r != null && buscadas.contains(r.id) && !veredictos.containsKey(r.id))
                    veredictos.put(r.id, new Comprobacion(enCursoReal(r, ahora) ? Veredicto.VIVA : Veredicto.TERMINADA, r, null));
            }
            for (long id : matchIds) veredictos.putIfAbsent(id, new Comprobacion(Veredicto.SIN_DATOS, null, null));
            return new Lote(veredictos, masAntigua);
        } catch (Exception ex) {   // también InterruptedException, como comprobar
            for (long id : matchIds) veredictos.put(id, new Comprobacion(Veredicto.SIN_DATOS, null, ex));
            return new Lote(veredictos, null);
        }
    }

    /** Las últimas partidas de un jugador, tal cual las da la API (sin decidir nada): el barrido de Live now
     *  hace su propio enCursoReal por lote. ui no puede importar api directamente, de ahí este paso. Va a la red. */
    public Iterable<Match> partidas(long pid, int pagina, int porPagina) throws IOException, InterruptedException { return api.partidas(pid, pagina, porPagina); }

    /** Igual que {@link #partidas(long, int, int)} pero para varios pids a la vez (CSV), como hace el barrido
     *  por lotes de Live now (una llamada por lote de hasta 15 pids). Va a la red. */
    public Iterable<Match> partidas(String pidsCsv, int pagina, int porPagina) throws IOException, InterruptedException { return api.partidas(pidsCsv, pagina, porPagina); }
}
