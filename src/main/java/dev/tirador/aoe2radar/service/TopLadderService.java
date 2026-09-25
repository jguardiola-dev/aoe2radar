package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.FilaClasificacion;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.util.Reloj;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

import static dev.tirador.aoe2radar.api.Cancelacion.stopOperacion;
import static dev.tirador.aoe2radar.cache.Canales.aprenderCanal;
import static dev.tirador.aoe2radar.cache.Paises.aprenderPais;
import static dev.tirador.aoe2radar.cache.Vivos.enCursoReal;
import static dev.tirador.aoe2radar.sfrdata.Ladder.ladderAsegurar;
import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Los tops de la watchlist (★ ladder, ★ país, ★ clan): la parte de red, de decisión y de disco de la 1.1
 * (cargarTopLadder, guardarTopCache/cargarTopCache, cargarTopClan y vigilarTop). La lógica de decisión es la misma;
 * la única diferencia deliberada es que enCursoReal se evalúa con el reloj inyectado en vez de Instant.now() directo
 * (mismo resultado en producción, ver DEUDA). La aplicación a Swing (topLadder, eloWatch, lastTop, rankTop, status…)
 * y a EstadoVivo sigue en la app, en el mismo orden y en el mismo hilo que hoy: por eso vigilarTop recibe callbacks
 * en vez de escribir en EstadoVivo por su cuenta (ver DEUDA: avisarSiCampana/resumenVivo son de la app y siguen
 * llamándose desde el hilo de fondo, como en la 1.1).
 */
public final class TopLadderService {
    /** Cuánto vale un top ya cargado antes de volver a pedirlo (cargarTopLadder, 1.1). */
    public static final long DIEZ_MINUTOS_MS = 10 * 60_000L;

    private final CompanionApi api;
    private final Reloj reloj;
    private final LongConsumer pausa;
    private final long pausaMs;

    public TopLadderService(CompanionApi api, Reloj reloj, LongConsumer pausa, long pausaMs) {
        this.api = api; this.reloj = reloj; this.pausa = pausa; this.pausaMs = pausaMs;
    }

    // ===================== cargarTopLadder =====================

    /** Una fila del top ya construida (pid, nombre, ELO, última partida en ms; 0 si no había). */
    public record FilaTop(long pid, String nombre, int rating, long ultimaPartidaMs) { }

    /** Lo que aprende una carga del top: las filas y lo que puebla TOP_STREAK/TOP_LAST10/gamesWatch hoy. */
    public record ResultadoTop(List<FilaTop> filas, Map<Long, Integer> racha, Map<Long, int[]> ultimas10, Map<Long, Integer> partidas) { }

    /**
     * ¿El top ya cargado sigue sirviendo? Misma regla que la 1.1: sin forzar, misma firma (país o "global"), con
     * datos y una carga de menos de 10 minutos (según el reloj inyectado).
     */
    public boolean topFresco(boolean forzar, String firma, String firmaCargada, boolean hayDatos, long cargadoMs) {
        return !forzar && firma.equals(firmaCargada) && hayDatos && reloj.ahoraMs() - cargadoMs < DIEZ_MINUTOS_MS;
    }

    /**
     * Como cargarTopLadder: prueba rm_1v1 y, si no trae nada, el ladder de equipos (id "3"), hasta topN filas. Aprende
     * país y canal de paso (cache.Paises/Canales), como la 1.1. Va a la red.
     */
    public ResultadoTop cargarTop(String pais, int topN) {
        avisarSiUi("TopLadderService.cargarTop");
        List<FilaTop> out = new ArrayList<>();
        Map<Long, Integer> racha = new HashMap<>();
        Map<Long, int[]> ultimas10 = new HashMap<>();
        Map<Long, Integer> partidas = new HashMap<>();
        for (String id : new String[]{ "rm_1v1", "3" }) {
            try {
                for (FilaClasificacion f : api.clasificacion(id, 1, 100, pais).filas()) {
                    long pid = f.pid();
                    int rating = f.rating() != null ? f.rating() : -1;
                    String name = String.valueOf(f.nombre());
                    aprenderCanal(pid, f.canal());
                    aprenderPais(pid, f.pais());
                    Instant lm = f.ultimaPartida();
                    if (f.racha() != null) racha.put(pid, f.racha());
                    if (f.jugadas10() > 0) ultimas10.put(pid, new int[]{ f.ganadas10(), f.jugadas10() - f.ganadas10() });
                    if (pid > 0 && rating > 0 && !"null".equals(name)) {
                        out.add(new FilaTop(pid, name, rating, lm == null ? 0L : lm.toEpochMilli()));
                        if (f.partidas() != null) partidas.put(pid, f.partidas());
                    }
                    if (out.size() >= topN) break;
                }
                if (!out.isEmpty()) break;
            } catch (Exception ex) {
                log("top ladder: fallo con id " + id + ": " + causa(ex));
            }
        }
        return new ResultadoTop(List.copyOf(out), racha, ultimas10, partidas);
    }

    // ===================== caché de disco (top_cache.txt) =====================

    /** Una fila tal como se guarda en disco: pid;nombre;elo;lastTop. */
    public record FilaCache(long pid, String nombre, int elo, long ultimaPartidaMs) { }

    /** Lo leído de la caché: la firma ya se comprobó, quedan las filas y cuándo se cargaron. */
    public record TopCache(long cargadoMs, List<FilaCache> filas) { }

    /** Formato «firma|topCargado» en la primera línea y «pid;nombre;elo;lastTop» en las demás, con el Path inyectado. */
    public void guardarCache(Path cache, String firma, long cargadoMs, List<FilaCache> filas) {
        try {
            List<String> lines = new ArrayList<>();
            lines.add(firma + "|" + cargadoMs);
            for (FilaCache f : filas)
                lines.add(f.pid() + ";" + f.nombre() + ";" + f.elo() + ";" + f.ultimaPartidaMs());
            Files.write(cache, lines, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log("top cache: no se pudo guardar: " + causa(ex));
        }
    }

    /** Restaura el último top guardado si es de la misma firma. null si no hay archivo, formato inválido o firma distinta. */
    public TopCache cargarCache(Path cache, String firma) {
        try {
            if (!Files.exists(cache)) return null;
            List<String> lines = Files.readAllLines(cache, StandardCharsets.UTF_8);
            if (lines.size() < 2) return null;
            String[] cab = lines.get(0).split("\\|", 2);
            if (!cab[0].equals(firma)) return null;
            List<FilaCache> filas = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String[] c = lines.get(i).split(";", 4);
                if (c.length < 4) continue;
                filas.add(new FilaCache(Long.parseLong(c[0]), c[1], Integer.parseInt(c[2]), Long.parseLong(c[3])));
            }
            return new TopCache(Long.parseLong(cab[1]), List.copyOf(filas));
        } catch (Exception ex) {
            log("top cache: no se pudo leer: " + causa(ex));
            return null;
        }
    }

    // ===================== ★ Top clan =====================

    /** Un miembro del clan tal como lo usa la lista del top. */
    public record FilaClan(long pid, String nombre, int rating) { }

    /** null en error: sin miembros si el ladder no se pudo asegurar. */
    public record ResultadoClan(String error, List<FilaClan> miembros) { }

    /** Como cargarTopClan: asegura los resúmenes del ladder (sfrdata.Ladder) y construye la lista del clan. Va a la red. */
    public ResultadoClan topClan(String tag) {
        avisarSiUi("TopLadderService.topClan");
        String err = ladderAsegurar(false);
        if (err != null) return new ResultadoClan(err, List.of());
        List<LadderRow> mi = ConsultasLadder.miembrosClan(tag);
        List<FilaClan> out = new ArrayList<>();
        for (LadderRow r : mi) out.add(new FilaClan(r.pid(), r.name(), r.rating()));
        return new ResultadoClan(null, out);
    }

    // ===================== vigilarTop =====================

    /** pid → id de la partida en la que se le vio; verificados: a quién se le pudo confirmar algo este barrido. */
    public record ResultadoVigilancia(Map<Long, Long> resultado, Set<Long> verificados) { }

    /**
     * El río en lotes de 15 y, para quien estaba jugando, se verificó en su lote y el río no lo trajo, una
     * confirmación individual (una llamada por jugador, partidas(pid, 1, 3): las 3 últimas). La decisión (quién sigue
     * en curso) es de aquí; escribir en EstadoVivo (ponerInfo/avisarSiCampana/guardarPartida)
     * sigue siendo cosa de la app -por eso los dos callbacks-, en el mismo momento y el mismo hilo (de fondo) que hoy.
     * jugando/matchDe leen EstadoVivo (solo lectura, sin escribir desde aquí). Va a la red.
     *
     * @param top                la lista vigilada, en orden (copia inmutable de topLadder en el momento de empezar)
     * @param jugando            EstadoVivo.jugando: ¿pid está en partida según el barrido ANTERIOR?
     * @param matchDe            EstadoVivo.matchDe: la partida de pid según el barrido ANTERIOR (puede ser null)
     * @param rioEncontrado      alguien del lote apareció en curso: aplica ponerInfo, avisarSiCampana y guardarPartida
     * @param confirmadoEncontrado la confirmación individual encontró que sigue en curso: aplica ponerInfo
     */
    public ResultadoVigilancia vigilarTop(List<Player> top, LongPredicate jugando, LongFunction<Long> matchDe,
                                          BiConsumer<Long, Match> rioEncontrado, BiConsumer<Long, Match> confirmadoEncontrado) {
        avisarSiUi("TopLadderService.vigilarTop");
        Map<Long, Long> resultado = new HashMap<>();
        Set<Long> verificados = new HashSet<>();
        final int LOTE = 15;
        for (int d = 0; d < top.size(); d += LOTE) {
            List<Player> lote = top.subList(d, Math.min(d + LOTE, top.size()));
            Set<Long> idsLote = new HashSet<>();
            StringBuilder csv = new StringBuilder();
            for (Player p : lote) {
                idsLote.add(p.id());
                if (csv.length() > 0) csv.append(',');
                csv.append(p.id());
            }
            try {
                Iterable<Match> leidas = api.partidas(csv.toString(), 1, 100);
                int nPart = 0, nCurso = 0; Instant masAntigua = null;
                for (Match m : leidas) {
                    if (m == null) continue;
                    nPart++;
                    if (m.started != null && (masAntigua == null || m.started.isBefore(masAntigua))) masAntigua = m.started;
                    if (!enCursoReal(m, Instant.ofEpochMilli(reloj.ahoraMs()))) continue;
                    nCurso++;
                    for (MatchPlayer mp : m.players)
                        if (idsLote.contains(mp.id) && !resultado.containsKey(mp.id)) {
                            resultado.put(mp.id, m.id);
                            rioEncontrado.accept(mp.id, m);
                        }
                }
                long minAnt = masAntigua == null ? -1 : Duration.between(masAntigua, Instant.ofEpochMilli(reloj.ahoraMs())).toMinutes();
                log("top vivos: lote " + (d / LOTE + 1) + " → " + nPart + " partidas, " + nCurso + " en curso, la más antigua hace " + minAnt + " min");
                verificados.addAll(idsLote);   // solo lo verificado se actualiza
            } catch (Exception ex) {
                log("top vivos: fallo con el lote " + (d / LOTE + 1) + " (se conserva el estado anterior): " + causa(ex));
            }
            pausa.accept(pausaMs / 2);
        }
        // Confirmación individual: quien estaba en partida y ya no aparece en el lote, se consulta solo
        for (Player p : top) {
            if (stopOperacion) break;
            if (!jugando.test(p.id()) || resultado.containsKey(p.id()) || !verificados.contains(p.id())) continue;
            try {
                Iterable<Match> leidas = api.partidas(p.id(), 1, 3);
                Match ultima = null;
                for (Match m : leidas) { if (m != null) { ultima = m; break; } }
                if (ultima != null && enCursoReal(ultima, Instant.ofEpochMilli(reloj.ahoraMs()))) {
                    resultado.put(p.id(), ultima.id);
                    confirmadoEncontrado.accept(p.id(), ultima);
                    log("top vivos: " + p.name() + " seguía en partida (" + ultima.id + ") aunque el lote no la traía");
                } else {
                    log("top vivos: " + p.name() + " terminó de verdad (última " + (ultima == null ? "?" : ultima.id + ", finished=" + ultima.finished) + ")");
                }
            } catch (Exception ex) {
                resultado.put(p.id(), matchDe.apply(p.id()));   // sin respuesta: se conserva el punto
                log("top vivos: confirmación de " + p.name() + " falló, punto conservado: " + causa(ex));
            }
            pausa.accept(pausaMs / 3);
        }
        int sinVerificar = top.size() - verificados.size();
        log("top vivos (lote): " + resultado.size() + " en partida de " + verificados.size() + " verificados"
                + (sinVerificar > 0 ? " · " + sinVerificar + " sin verificar (lote fallido), estado conservado" : ""));
        return new ResultadoVigilancia(resultado, verificados);
    }
}
