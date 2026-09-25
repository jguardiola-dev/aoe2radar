package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.Clasificacion;
import dev.tirador.aoe2radar.model.FilaClasificacion;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.PaginaLb;
import dev.tirador.aoe2radar.model.PaginaPartidas;
import dev.tirador.aoe2radar.util.Hilos;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import static dev.tirador.aoe2radar.api.Cancelacion.hiloOperacion;
import static dev.tirador.aoe2radar.api.Cancelacion.stopOperacion;
import static dev.tirador.aoe2radar.cache.RecsDisco.maxGteEnDisco;
import static dev.tirador.aoe2radar.service.Aleatorio.ProveedorPaginas;
import static dev.tirador.aoe2radar.service.Aleatorio.componerTanda;
import static dev.tirador.aoe2radar.service.Aleatorio.cumpleAzar;
import static dev.tirador.aoe2radar.service.Aleatorio.elegirPerfiles;
import static dev.tirador.aoe2radar.service.Aleatorio.filtrarAleatorias;
import static dev.tirador.aoe2radar.service.Aleatorio.filtrarGte;
import static dev.tirador.aoe2radar.service.Aleatorio.franjasAleatorias;
import static dev.tirador.aoe2radar.service.Aleatorio.ordenaYRecorta;
import static dev.tirador.aoe2radar.service.Aleatorio.primeraPaginaRango;
import static dev.tirador.aoe2radar.service.Aleatorio.tramosGte;
import static dev.tirador.aoe2radar.service.Aleatorio.ultimaPaginaRango;
import static dev.tirador.aoe2radar.service.NombresStats.nombreCivStats;
import static dev.tirador.aoe2radar.service.NombresStats.nombreMapaClave;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;
import static dev.tirador.aoe2radar.util.Texto.esCualquiera;

/**
 * Implementación de AzarService sobre el companion: la caché de sesión (páginas del leaderboard, partidas ya
 * descargadas, perfiles ya consultados) vive en los campos de la instancia, igual que antes vivía en campos
 * sueltos de la ventana. Una sola instancia por ventana (la crea SpoilerFreeRecs, con techTree.claveCivDeNombre,
 * Snapshots::muestraAyer y su dormir() ya existentes, para no duplicar el freno entre llamadas).
 * <p>muestraAyer se recibe como colaborador (no como import estático de sfrdata.Snapshots) para poder sustituirlo
 * por uno de prueba: Snapshots.muestraAyer() de verdad va a la red si no hay copia fresca en disco.
 */
public final class AzarServiceCompanion implements AzarService {

    private final CompanionApi companion;
    private final Function<String, String> claveCivDeNombre;
    private final Supplier<Map<String, List<List<Object>>>> muestraAyer;
    private final LongConsumer dormir;
    private final int perPage;
    private final long pausaMs;

    // --- Caché de sesión del «Al azar por ELO» (vive mientras la app está abierta) ---
    private LbCtx ctxAzar;                       // páginas del leaderboard reutilizadas entre tiradas
    private long ctxAzarNacido;
    private String firmaRangoAzar = "";
    private String firmaTotalAzar = "";
    private List<Integer> pagsAzar;
    private int cursorPagsAzar;
    private final Map<Long, Match> cacheAzar = new HashMap<>();      // partidas ya descargadas del API
    private final Map<Long, Long> perfilVistoAzar = new HashMap<>(); // perfil -> última consulta (TTL 10 min)
    private volatile boolean azarTramoAgotado;
    private final Set<Long> azarEnsenadas = new HashSet<>();

    public AzarServiceCompanion(CompanionApi companion, Function<String, String> claveCivDeNombre,
                                 Supplier<Map<String, List<List<Object>>>> muestraAyer,
                                 LongConsumer dormir, int perPage, long pausaMs) {
        this.companion = companion;
        this.claveCivDeNombre = claveCivDeNombre;
        this.muestraAyer = muestraAyer;
        this.dormir = dormir;
        this.perPage = perPage;
        this.pausaMs = pausaMs;
    }

    @Override
    public List<Match> buscarAleatorias(int lo, int hi, String mapaSel, String civSel, int hours, int multAzar,
                                         Instant cutoff, long serial, Consumer<String> progreso) throws Exception {
        Hilos.avisarSiUi("AzarService.buscarAleatorias");
        hiloOperacion = Thread.currentThread();   // Detener corta la espera del freno de ESTA operación, no la de todos
        Random rnd = new Random();
        if (hours >= 24) {   // la muestra nocturna cubre «ayer»: si da para una tanda, cero llamadas
            List<Match> deMuestra = azarDesdeMuestra(lo, hi, mapaSel, civSel, rnd);
            if (deMuestra.size() >= 5) { for (Match m : deMuestra) { m.azar = true; AzarService.ajustarRefAzar(m, civSel); } return deMuestra; }
        }
        long ahora = System.currentTimeMillis();
        String firmaRango = lo + "|" + hi;
        String firmaTotal = firmaRango + "|" + hours + "|" + mapaSel + "|" + civSel;
        azarTramoAgotado = false;

        // Caché de sesión: contexto del ladder reutilizable y TTL de perfiles.
        if (ctxAzar == null || !firmaRango.equals(firmaRangoAzar)
                || ahora - ctxAzarNacido > 10 * 60_000L) {
            ctxAzar = new LbCtx();
            ctxAzarNacido = ahora;
            firmaRangoAzar = firmaRango;
            pagsAzar = null;
        }
        perfilVistoAzar.values().removeIf(ts -> ahora - ts > 10 * 60_000L);
        boolean continua = firmaTotal.equals(firmaTotalAzar) && !perfilVistoAzar.isEmpty();
        firmaTotalAzar = firmaTotal;

        Set<Long> idsRes = new HashSet<>();
        List<Match> encontradas = new ArrayList<>();
        Map<Long, Integer> ratingsLb = new HashMap<>();
        for (PaginaLb pg : ctxAzar.cache.values())
            for (long[] p : pg.jugadores()) ratingsLb.put(p[0], (int) p[1]);

        // Pre-siembra: lo ya leído en esta sesión que cumpla los filtros de hoy.
        for (Match m : filtrarAleatorias(cacheAzar.values(), ratingsLb, lo, hi, cutoff, mapaSel, civSel, 10, rnd))
            if (idsRes.add(m.id)) encontradas.add(m);
        if (!encontradas.isEmpty())
            progreso.accept(t("De lo ya leído en esta sesión: ", "From this session's cache: ")
                    + encontradas.size() + "/10\u2026");

        try {
            // Tramo del rango en el ladder (bisección sobre páginas cacheadas).
            ProveedorPaginas prov = p -> lbPagina(ctxAzar, p);
            int ult = ultimaPaginaLadder(ctxAzar);
            int pIni = primeraPaginaRango(prov, hi, ult);
            int pFin = ultimaPaginaRango(prov, lo, ult);
            log("azar #" + serial + ": tramo páginas " + pIni + "-" + pFin + " de " + ult + " stop=" + stopOperacion);
            if (pIni > pFin) return ordenaYRecorta(encontradas);
            double fraccion = (pFin - pIni + 1) / (double) Math.max(1, ult);

            // ---- Río global: solo si el rango es una porción rentable del
            // ladder y no hay filtro de civ (la dilución lo vuelve un pozo).
            if (fraccion >= 0.12 && civSel == null && encontradas.size() < 10) {
                String[] variantesLb = { "rm_1v1", "3", null };   // null: sin filtro de ladder
                int varLb = 0;
                int maxPagRio = mapaSel != null ? 25 : 15;
                int pagLeidas = 0;
                for (int pag = 1; pag <= maxPagRio && encontradas.size() < 10 && !stopOperacion; pag++) {
                    progreso.accept(t("Leyendo partidas recientes del ladder\u2026 (p\u00e1g. ", "Reading recent ladder games\u2026 (page ")
                            + pag + ", " + encontradas.size() + "/10)");
                    PaginaPartidas ms;
                    try {
                        ms = companion.recientes(variantesLb[varLb], pag, 50);   // con reintento ante 429, como antes
                    } catch (Exception ex) {
                        log("al azar (r\u00edo): fallo en p\u00e1gina " + pag + " (variante " + varLb + "): " + causa(ex));
                        if (varLb < variantesLb.length - 1 && pagLeidas == 0) { varLb++; pag = 0; continue; }
                        break;
                    }
                    if (ms.brutas() == 0 && pagLeidas == 0 && varLb < variantesLb.length - 1) {
                        varLb++;
                        pag = 0;
                        continue;
                    }
                    if (ms.brutas() == 0) break;
                    pagLeidas++;
                    boolean algunaEnVentana = false;
                    for (Match m : ms.partidas()) {
                        if (m.finished == null) continue;
                        cacheAzar.putIfAbsent(m.id, m);
                        if (!m.finished.isBefore(cutoff)) algunaEnVentana = true;
                        if (cumpleAzar(m, lo, hi, cutoff, mapaSel, civSel) && idsRes.add(m.id))
                            encontradas.add(m);
                    }
                    if (!algunaEnVentana) break;      // el r\u00edo ya qued\u00f3 m\u00e1s viejo que la ventana
                    dormir.accept(pausaMs / 2);
                }
                log("al azar (r\u00edo): " + pagLeidas + " p\u00e1ginas le\u00eddas (variante " + varLb + "), "
                        + encontradas.size() + " v\u00e1lidas acumuladas");
            }
            if (encontradas.size() >= 10) return ordenaYRecorta(encontradas);

            // ---- Perfiles activos del tramo, sin repetir los ya consultados.
            if (pagsAzar == null) {
                pagsAzar = new ArrayList<>();
                for (int p = pIni; p <= pFin; p++) pagsAzar.add(p);
                Collections.shuffle(pagsAzar, rnd);
                cursorPagsAzar = 0;
            }
            int pasadas = ((mapaSel != null || civSel != null) ? 2 : 3) * multAzar;
            for (int intento = 1; intento <= pasadas && encontradas.size() < 10 && !stopOperacion; intento++) {
                int nPerfiles = civSel != null ? 40 : (intento == 1 ? 12 : 24);
                int nPags = Math.min(pagsAzar.size(), civSel != null ? 12 : (intento == 1 ? 6 : 10));
                if (intento > 1 || continua)
                    progreso.accept(t("A\u00fan ", "Still ") + encontradas.size()
                            + t(" de 10; muestreando jugadores nuevos\u2026 (pasada ", " of 10; sampling new players\u2026 (pass ")
                            + intento + "/" + pasadas + ")");
                Map<Long, Match> unicos = new LinkedHashMap<>();
                List<long[]> lb = new ArrayList<>();
                for (int i = 0; i < nPags; i++) {
                    int pag = pagsAzar.get((cursorPagsAzar + i) % pagsAzar.size());
                    progreso.accept(t("Muestreando el ladder\u2026 (", "Sampling the ladder\u2026 (") + (i + 1) + "/" + nPags + ")");
                    lb.addAll(lbPagina(ctxAzar, pag).jugadores());
                }
                cursorPagsAzar += nPags;
                ratingsLb.clear();
                for (PaginaLb pg : ctxAzar.cache.values())
                    for (long[] p : pg.jugadores()) ratingsLb.put(p[0], (int) p[1]);
                log("al azar por ELO " + lo + "-" + hi
                        + (mapaSel != null ? ", mapa=" + mapaSel : "")
                        + (civSel != null ? ", civ=" + civSel : "")
                        + ": pasada " + intento + "/" + pasadas
                        + ", tramo en p\u00e1ginas " + pIni + "\u2013" + pFin
                        + " (" + String.format("%.1f", fraccion * 100) + "% del ladder), "
                        + lb.size() + " jugadores a la vista, "
                        + perfilVistoAzar.size() + " consultados en la sesi\u00f3n");
                List<Long> perfiles = new ArrayList<>();
                for (long pid : elegirPerfiles(lb, lo, hi, cutoff.toEpochMilli(), nPerfiles * 4, rnd)) {
                    if (perfiles.size() >= nPerfiles) break;
                    if (!perfilVistoAzar.containsKey(pid)) perfiles.add(pid);
                }
                if (perfiles.isEmpty()) { azarTramoAgotado = true; break; }
                int i = 0;
                for (long pid : perfiles) {
                    if (stopOperacion) break;
                    progreso.accept(t("Perfil ", "Profile ") + (++i) + "/" + perfiles.size()
                            + " \u00b7 " + encontradas.size() + "/10\u2026");
                    try {
                        Iterable<Match> leidas = companion.partidas(pid, 1, perPage);
                        perfilVistoAzar.put(pid, System.currentTimeMillis());
                        for (Match m : leidas) {
                            if (m != null && m.finished != null) {
                                unicos.putIfAbsent(m.id, m);
                                cacheAzar.putIfAbsent(m.id, m);
                            }
                        }
                    } catch (Exception ex) {
                        log("al azar: fallo con perfil " + pid + ": " + causa(ex));
                    }
                    dormir.accept(pausaMs);
                }
                for (Match m : filtrarAleatorias(unicos.values(), ratingsLb, lo, hi, cutoff, mapaSel, civSel, 10, rnd))
                    if (idsRes.add(m.id)) encontradas.add(m);
            }
            log("al azar: total acumulado " + encontradas.size() + " partidas"
                    + (azarTramoAgotado ? " (tramo activo agotado en esta sesi\u00f3n)" : ""));
            return ordenaYRecorta(encontradas);
        } catch (InterruptedException ex) {   // Detener durante la bisección o el muestreo: se aplica lo encontrado, como en la 1.1
            if (stopOperacion) return ordenaYRecorta(encontradas);
            throw ex;
        }
    }

    @Override
    public List<Match> buscarGte(Instant cutoff, Consumer<String> progreso) throws Exception {
        Hilos.avisarSiUi("AzarService.buscarGte");
        hiloOperacion = Thread.currentThread();   // Detener corta la espera del freno de ESTA operación, no la de todos
        Random rnd = new Random();
        List<Match> deMuestra = gteDesdeMuestra(rnd);   // la muestra nocturna de sfr-data: cero llamadas y nunca una partida repetida
        if (!deMuestra.isEmpty()) return deMuestra;
        LbCtx ctx = new LbCtx();
        int ult = ultimaPaginaLadder(ctx);
        int[][] tramos = tramosGte(ult);
        int[] franjas = franjasAleatorias(5, tramos.length, rnd);   // dado independiente por partida
        List<List<Long>> perfilesPorSlot = new ArrayList<>();
        Set<Long> usados = new HashSet<>();
        int nPerfiles = 0;
        for (int s = 0; s < franjas.length; s++) {
            int[] tr = tramos[franjas[s]];
            int pag = tr[0] + rnd.nextInt(Math.max(1, tr[1] - tr[0] + 1));
            progreso.accept("Muestreando el ladder… (" + (s + 1) + "/" + franjas.length + ")");
            List<long[]> js = new ArrayList<>(lbPagina(ctx, Math.min(pag, ult)).jugadores());
            long cutMs = cutoff.toEpochMilli();
            List<long[]> activos = new ArrayList<>(), resto = new ArrayList<>();
            for (long[] j : js) ((j.length > 2 && j[2] >= cutMs) ? activos : resto).add(j);
            Collections.shuffle(activos, rnd);
            Collections.shuffle(resto, rnd);
            List<long[]> orden = new ArrayList<>(activos);
            orden.addAll(resto);
            List<Long> ids = new ArrayList<>();
            for (long[] j : orden) {
                if (ids.size() >= 2) break;
                if (usados.add(j[0])) ids.add(j[0]);
            }
            perfilesPorSlot.add(ids);
            nPerfiles += ids.size();
        }
        Map<Long, Integer> ratings = new HashMap<>();
        for (PaginaLb pg : ctx.cache.values())
            for (long[] p : pg.jugadores()) ratings.put(p[0], (int) p[1]);
        log("Guess the ELO: ladder de " + ult + " páginas, " + nPerfiles
                + " perfiles en 5 slots con franja sorteada al azar");
        if (nPerfiles == 0) return List.of();

        List<List<Match>> validasPorSlot = new ArrayList<>();
        int i = 0;
        for (List<Long> ids : perfilesPorSlot) {
            Map<Long, Match> unicos = new LinkedHashMap<>();
            for (long pid : ids) {
                if (stopOperacion) break;
                progreso.accept(t("Perfil ", "Profile ") + (++i) + "/" + nPerfiles + "…");
                try {
                    Iterable<Match> leidas = companion.partidas(pid, 1, perPage);
                    for (Match m : leidas) {
                        if (m != null && m.finished != null) unicos.putIfAbsent(m.id, m);
                    }
                } catch (Exception ex) {
                    log("Guess the ELO: fallo con perfil " + pid + ": " + causa(ex));
                }
                dormir.accept(pausaMs);
            }
            validasPorSlot.add(filtrarGte(unicos.values(), cutoff, 3, rnd));
        }
        List<Match> res = componerTanda(validasPorSlot, 5, rnd);
        int base = maxGteEnDisco();
        for (int k = 0; k < res.size(); k++) {
            Match m = res.get(k);
            m.gte = base + k + 1;
            for (MatchPlayer p : m.players)
                if (p.rating == null) p.rating = ratings.get(p.id);   // para el revelado
        }
        return res;
    }

    @Override
    public boolean tramoAgotado() {
        return azarTramoAgotado;
    }

    /** Partida de la muestra → Match (jugadores con nombre, civ, rating y resultado; sin revelar nada en pantalla hasta que se pida). */
    private Match matchDeMuestra(List<Object> f) {
        Match m = new Match();
        m.id = lng(f.get(0)); m.started = Instant.ofEpochSecond(lng(f.get(1))); m.finished = Instant.ofEpochSecond(lng(f.get(2)));
        m.mode = "1v1 Random Map"; m.mapaClave = String.valueOf(f.get(3)); m.map = nombreMapaClave(m.mapaClave);
        int team = 1;
        for (Object jo : arr(f.get(4))) { List<Object> j = arr(jo); MatchPlayer mp = new MatchPlayer(); mp.id = lng(j.get(0)); mp.name = String.valueOf(j.get(1)); String civK = String.valueOf(j.get(2)); mp.civ = civK.isBlank() ? null : nombreCivStats(civK); mp.rating = (int) lng(j.get(3)); mp.won = lng(j.get(4)) == 1; mp.team = team++; m.players.add(mp); }
        if (!m.players.isEmpty()) m.refId = m.players.get(0).id;
        return m;
    }

    /** Guess the ELO desde la muestra de ayer: 5 partidas 1v1 de tramos distintos, nunca una ya vista (los ids vistos se guardan en config). Vacío si no hay muestra. */
    private List<Match> gteDesdeMuestra(Random rnd) {
        Map<String, List<List<Object>>> muestra = muestraAyer.get();
        if (muestra == null || muestra.isEmpty()) return List.of();
        Set<String> vistas = new HashSet<>(Arrays.asList(leerConfig("gte_vistas", "").split(",")));
        List<String> tramos = new ArrayList<>(muestra.keySet()); Collections.shuffle(tramos, rnd);
        List<Match> res = new ArrayList<>();
        for (String tr : tramos) {
            List<List<Object>> l = new ArrayList<>(muestra.get(tr)); Collections.shuffle(l, rnd);
            for (List<Object> f : l) { if (vistas.contains(String.valueOf(lng(f.get(0))))) continue; res.add(matchDeMuestra(f)); break; }
            if (res.size() >= 5) break;
        }
        if (res.isEmpty()) return res;
        int base = maxGteEnDisco();
        for (int k = 0; k < res.size(); k++) { res.get(k).gte = base + k + 1; vistas.add(String.valueOf(res.get(k).id)); }
        List<String> lista = new ArrayList<>(vistas); lista.removeIf(String::isBlank); while (lista.size() > 3000) lista.remove(0);
        guardarConfig("gte_vistas", String.join(",", lista));
        return res;
    }

    /** Al azar por ELO desde la muestra de ayer: partidas cuyo ELO medio cae en [lo, hi], con filtros de mapa y civ, sin repetir las ya enseñadas. */
    private List<Match> azarDesdeMuestra(int lo, int hi, String mapaSel, String civSel, Random rnd) {
        Map<String, List<List<Object>>> muestra = muestraAyer.get();
        if (muestra == null) return List.of();
        List<Match> cand = new ArrayList<>();
        for (List<List<Object>> l : muestra.values()) for (List<Object> f : l) {
            Match m = matchDeMuestra(f);
            if (m.players.size() != 2) continue;
            double media = (m.players.get(0).rating + m.players.get(1).rating) / 2.0;
            if (media < lo || media > hi) continue;
            if (mapaSel != null && !esCualquiera(mapaSel) && !mapaSel.equalsIgnoreCase(m.map) && !mapaSel.equalsIgnoreCase(m.mapaClave)) continue;
            if (civSel != null && m.players.stream().noneMatch(pl -> civSel.equalsIgnoreCase(pl.civ) || civSel.equalsIgnoreCase(claveCivDeNombre.apply(pl.civ)))) continue;
            if (azarEnsenadas.contains(m.id)) continue;
            cand.add(m);
        }
        Collections.shuffle(cand, rnd);
        List<Match> out = new ArrayList<>(cand.subList(0, Math.min(10, cand.size())));
        for (Match m : out) azarEnsenadas.add(m.id);
        return out;
    }

    /** Baja y parsea una página del leaderboard, con caché. */
    private PaginaLb lbPagina(LbCtx ctx, int p) throws IOException, InterruptedException {
        PaginaLb enCache = ctx.cache.get(p);
        if (enCache != null) return enCache;
        Clasificacion root = null;
        List<FilaClasificacion> players = List.of();
        if (ctx.id == null) {
            for (String id : new String[]{ "rm_1v1", "3" }) {
                try {
                    root = companion.clasificacion(id, p, 100, null);   // con reintento ante 429 (Detener corta la espera)
                    players = root.filas();
                    if (!players.isEmpty()) { ctx.id = id; break; }
                } catch (InterruptedException ie) { throw ie; }   // Detener: no probar el otro id ni decir «no responde»
                catch (IOException io) { if (String.valueOf(io.getMessage()).contains("429")) throw io; }   // un 429 es «espera», no «prueba otro id» (con reintentos, probar el «3» alargaba ~12 min y subía la pausa al tope)
                catch (Exception ignored) {}
            }
            if (ctx.id == null) throw new IOException("el leaderboard no responde (ni rm_1v1 ni 3)");
        } else {
            root = companion.clasificacion(ctx.id, p, 100, null);   // con reintento ante 429 (Detener corta la espera)
            players = root.filas();
        }
        if (ctx.totalPaginas < 0 && root != null) {
            long total = root.total();
            long porPag = root.porPagina();
            if (porPag <= 0) porPag = 100;
            if (total > 0) ctx.totalPaginas = (int) ((total + porPag - 1) / porPag);
        }
        List<long[]> js = new ArrayList<>();
        int max = -1, min = -1;
        for (FilaClasificacion f : players) {
            long pid = f.pid();
            int rating = f.rating() != null ? f.rating() : -1;
            if (pid > 0 && rating > 0) {
                Instant lm = f.ultimaPartida();
                js.add(new long[]{ pid, rating, lm == null ? 0L : lm.toEpochMilli() });
                max = max < 0 ? rating : Math.max(max, rating);
                min = min < 0 ? rating : Math.min(min, rating);
            }
        }
        PaginaLb pag = new PaginaLb(js, max, min);
        ctx.cache.put(p, pag);
        log("leaderboard " + ctx.id + " página " + p + ": " + js.size() + " jugadores"
                + (js.isEmpty() ? "" : ", rating " + max + "–" + min));
        dormir.accept(pausaMs);
        return pag;
    }

    /** Número de páginas del ladder: del campo total si viene; si no, sondeo
     *  exponencial hasta encontrar una página vacía. */
    private int ultimaPaginaLadder(LbCtx ctx) throws IOException, InterruptedException {
        lbPagina(ctx, 1);
        if (ctx.totalPaginas > 0) return ctx.totalPaginas;
        int p = 1;
        while (p < 4096 && !lbPagina(ctx, p * 2).jugadores().isEmpty()) p *= 2;
        return p * 2;
    }

    /** Estado compartido de una consulta al leaderboard: id que responde
     *  (rm_1v1 o 3), total de páginas y caché por página para que la
     *  búsqueda binaria no repita peticiones. */
    private static final class LbCtx {
        String id;
        int totalPaginas = -1;
        final Map<Integer, PaginaLb> cache = new HashMap<>();
    }
}
