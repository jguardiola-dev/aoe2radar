package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.FuentePartidas;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.util.Reloj;

import java.io.IOException;
import java.time.Instant;

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

    private final FuentePartidas api;       // comprobar: «¿ya terminó?» (en la app, con respaldo de World's Edge)
    private final FuentePartidas barrido;   // partidas(…): el barrido de Live now busca partidas EN CURSO (solo companion)
    private final Reloj reloj;

    public LiveService(FuentePartidas api, Reloj reloj) { this(api, api, reloj); }

    /**
     * comprobacion para comprobar(…) y barrido para partidas(…). Separadas desde la 1.4: World's Edge solo conoce
     * partidas terminadas, así que sirve de respaldo para «¿ya terminó?» (una partida que no aparece queda SIN_DATOS),
     * pero no para el barrido, que busca las que están en curso: ahí, sin companion, mejor un error que «nadie juega».
     */
    public LiveService(FuentePartidas comprobacion, FuentePartidas barrido, Reloj reloj) {
        this.api = comprobacion; this.barrido = barrido; this.reloj = reloj;
    }

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

    /** Las últimas partidas de un jugador, tal cual las da la API (sin decidir nada): el barrido de Live now
     *  hace su propio enCursoReal por lote. ui no puede importar api directamente, de ahí este paso. Va a la red. */
    public Iterable<Match> partidas(long pid, int pagina, int porPagina) throws IOException, InterruptedException { return barrido.partidas(pid, pagina, porPagina); }

    /** Igual que {@link #partidas(long, int, int)} pero para varios pids a la vez (CSV), como hace el barrido
     *  por lotes de Live now (una llamada por lote de hasta 15 pids). Va a la red. */
    public Iterable<Match> partidas(String pidsCsv, int pagina, int porPagina) throws IOException, InterruptedException { return barrido.partidas(pidsCsv, pagina, porPagina); }
}
