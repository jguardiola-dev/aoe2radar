package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.PaginaLb;
import dev.tirador.aoe2radar.util.Hilos;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import static dev.tirador.aoe2radar.api.Cancelacion.detieneEsteHilo;
import static dev.tirador.aoe2radar.cache.RecsDisco.maxGteEnDisco;
import static dev.tirador.aoe2radar.service.Aleatorio.ProveedorPaginas;
import static dev.tirador.aoe2radar.service.Aleatorio.componerTanda;
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
    private final Supplier<Map<String, List<List<Object>>>> muestraAnteayer;   // null dentro si el archivo no la trae (anterior a la 1.4)
    private final Supplier<List<long[]>> ladderNocturno;                       // LadderNocturno.de(...); null → mensaje y ninguna llamada (1.4.1)
    private final LongConsumer dormir;
    private final int perPage;
    private final long pausaMs;

    /** Jugadores por llamada: /matches?profile_ids=<csv de hasta 10>&per_page=100 (el companion ya no admite /matches sin
     *  profile_ids: 422 «profile_ids must be specified», 2026-09-28; el «río» de partidas recientes del ladder murió). */
    static final int LOTE = 10, POR_LOTE = 100;
    /** Tope de llamadas por tirada de «Al azar» con intensidad normal; se multiplica por la intensidad (1, 3, 10). */
    static final int MAX_LOTES = 6;

    /** Sin el ELO de anoche de sfr-data no hay de dónde sacar jugadores del tramo (ni río al que volver): se dice y no se llama. */
    static String sinNocturno() {
        return t("faltan los datos nocturnos de sfr-data (ELO de anoche), que son los que dicen qué jugadores hay en cada tramo; "
                        + "inténtalo más tarde (con una ventana de 24 h o más se usa la muestra nocturna si está)",
                "sfr-data's nightly data (last night's ELO) is missing, and it is what tells which players are in each bracket; "
                        + "try again later (with a window of 24 h or more the nightly sample is used if available)");
    }

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
    private volatile boolean azarConAnteayer;   // ...y la muestra de anteayer aportó alguna (ver conAnteayer())
    private volatile boolean azarDeMuestra;   // la última tirada salió de la muestra nocturna (ver deMuestra())
    private final Set<Long> azarEnsenadas = new HashSet<>();

    public AzarServiceCompanion(CompanionApi companion, Function<String, String> claveCivDeNombre,
                                 Supplier<Map<String, List<List<Object>>>> muestraAyer,
                                 LongConsumer dormir, int perPage, long pausaMs) {
        this(companion, claveCivDeNombre, muestraAyer, () -> null, () -> null, dormir, perPage, pausaMs);
    }

    /**
     * 1.4, «nocturno primero» también para el tramo: muestraAnteayer (MuestraNocturna.anteayer) completa la muestra de ayer
     * (en «Al azar», solo con una ventana de 48 h o más y si ayer no da 10; en «Guess the ELO», cuando ayer ya está vista) y
     * ladderNocturno (LadderNocturno.de sobre el ELO de anoche) sustituye a las páginas del leaderboard en vivo: la
     * bisección y el muestreo de perfiles corren igual, pero sobre páginas de 100 ya en memoria (cero llamadas al
     * leaderboard). Si la muestra de anteayer es null, solo ayer. Sin ladder nocturno (1.4.1), lo que no dé la muestra no se
     * busca: IOException con el mensaje de sinNocturno() y ninguna llamada (el leaderboard en vivo y el río ya no se usan).
     */
    public AzarServiceCompanion(CompanionApi companion, Function<String, String> claveCivDeNombre,
                                 Supplier<Map<String, List<List<Object>>>> muestraAyer,
                                 Supplier<Map<String, List<List<Object>>>> muestraAnteayer,
                                 Supplier<List<long[]>> ladderNocturno,
                                 LongConsumer dormir, int perPage, long pausaMs) {
        this.companion = companion;
        this.claveCivDeNombre = claveCivDeNombre;
        this.muestraAyer = muestraAyer;
        this.muestraAnteayer = muestraAnteayer;
        this.ladderNocturno = ladderNocturno;
        this.dormir = dormir;
        this.perPage = perPage;
        this.pausaMs = pausaMs;
    }

    @Override
    public List<Match> buscarAleatorias(int lo, int hi, String mapaSel, String civSel, int hours, int multAzar,
                                         Instant cutoff, long serial, Consumer<String> progreso) throws Exception {
        Hilos.avisarSiUi("AzarService.buscarAleatorias");
        Random rnd = new Random();
        azarDeMuestra = false;
        azarConAnteayer = false;
        if (hours >= 24) {   // la muestra nocturna cubre «ayer»: si da para una tanda, cero llamadas
            List<Match> deMuestra = azarDesdeMuestra(muestraAyer.get(), lo, hi, mapaSel, civSel, rnd, 10);
            int deAyer = deMuestra.size();
            if (hours >= 48 && deMuestra.size() < 10)   // la ventana cubre anteayer: se completa con su muestra (1.4)
                deMuestra.addAll(azarDesdeMuestra(muestraAnteayer.get(), lo, hi, mapaSel, civSel, rnd, 10 - deMuestra.size()));
            if (deMuestra.size() >= 5) { for (Match m : deMuestra) { m.azar = true; AzarService.ajustarRefAzar(m, civSel); } azarDeMuestra = true; azarConAnteayer = deMuestra.size() > deAyer; return deMuestra; }
        }
        long ahora = System.currentTimeMillis();
        String firmaRango = lo + "|" + hi;
        String firmaTotal = firmaRango + "|" + hours + "|" + mapaSel + "|" + civSel;
        azarTramoAgotado = false;

        // Caché de sesión: contexto del ladder reutilizable y TTL de perfiles.
        if (ctxAzar == null || !ctxAzar.nocturno || !firmaRango.equals(firmaRangoAzar)
                || ahora - ctxAzarNacido > 10 * 60_000L) {
            ctxAzar = nuevoCtx();
            ctxAzarNacido = ahora;
            firmaRangoAzar = firmaRango;
            pagsAzar = null;
        }
        if (!ctxAzar.nocturno) throw new IOException(sinNocturno());   // ninguna llamada: sin tramo no hay a quién preguntar
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
            log("azar #" + serial + ": tramo páginas " + pIni + "-" + pFin + " de " + ult + " stop=" + detieneEsteHilo());
            if (pIni > pFin) return ordenaYRecorta(encontradas);
            double fraccion = (pFin - pIni + 1) / (double) Math.max(1, ult);

            if (encontradas.size() >= 10) return ordenaYRecorta(encontradas);

            // ---- Perfiles activos del tramo, sin repetir los ya consultados.
            if (pagsAzar == null) {
                pagsAzar = new ArrayList<>();
                for (int p = pIni; p <= pFin; p++) pagsAzar.add(p);
                Collections.shuffle(pagsAzar, rnd);
                cursorPagsAzar = 0;
            }
            int pasadas = ((mapaSel != null || civSel != null) ? 2 : 3) * multAzar;
            int maxLlamadas = MAX_LOTES * multAzar, llamadas = 0;   // cuenta todo intento, falle o salga de la caché por URL
            for (int intento = 1; intento <= pasadas && encontradas.size() < 10 && !detieneEsteHilo(); intento++) {
                int nPerfiles = civSel != null ? 40 : (intento == 1 ? 10 : 20);   // múltiplos de LOTE: ninguna llamada a medio llenar
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
                    for (long[] j : lbPagina(ctxAzar, pag).jugadores())
                        // ladder de anoche: su «última partida» es anterior al volcado, así que «antes del corte» no quiere
                        // decir inactivo (pudo jugar hoy): cuenta como desconocida (0), igual que sin fecha. Solo aquí, nunca
                        // en las páginas en caché, que se comparten entre tiradas con otra ventana de horas.
                        lb.add(ctxAzar.nocturno && j.length > 2 && j[2] < cutoff.toEpochMilli() ? new long[]{ j[0], j[1], 0L } : j);
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
                for (int k = 0; k < perfiles.size() && llamadas < maxLlamadas && !detieneEsteHilo(); k += LOTE) {
                    List<Long> lote = new ArrayList<>(perfiles.subList(k, Math.min(perfiles.size(), k + LOTE)));
                    Collections.sort(lote);   // URL estable (y la caché del freno no ve dos órdenes del mismo lote)
                    progreso.accept(t("Jugadores ", "Players ") + (k + 1) + "–" + (k + lote.size()) + "/" + perfiles.size()
                            + " \u00b7 " + encontradas.size() + "/10\u2026");
                    llamadas++;
                    try {
                        Iterable<Match> leidas = companion.partidas(csv(lote), 1, POR_LOTE);
                        long vistoTs = System.currentTimeMillis();
                        for (long pid : lote) perfilVistoAzar.put(pid, vistoTs);
                        for (Match m : leidas) {
                            if (m != null && m.finished != null) {
                                unicos.putIfAbsent(m.id, m);
                                cacheAzar.putIfAbsent(m.id, m);
                            }
                        }
                    } catch (InterruptedException ex) {
                        throw ex;   // Detener: al catch de abajo, que devuelve lo encontrado
                    } catch (Exception ex) {
                        log("al azar: fallo con el lote " + csv(lote) + ": " + causa(ex));   // no quedan como vistos: otra pasada los reintenta
                    }
                    dormir.accept(pausaMs);
                }
                for (Match m : filtrarAleatorias(unicos.values(), ratingsLb, lo, hi, cutoff, mapaSel, civSel, 10, rnd))
                    if (idsRes.add(m.id)) encontradas.add(m);
                if (llamadas >= maxLlamadas) { log("al azar: tope de " + maxLlamadas + " llamadas en esta tirada"); break; }
            }
            log("al azar: total acumulado " + encontradas.size() + " partidas en " + llamadas + " llamadas"
                    + (azarTramoAgotado ? " (tramo activo agotado en esta sesi\u00f3n)" : ""));
            return ordenaYRecorta(encontradas);
        } catch (InterruptedException ex) {   // Detener durante la bisección o el muestreo: se aplica lo encontrado, como en la 1.1
            if (detieneEsteHilo()) return ordenaYRecorta(encontradas);
            throw ex;
        }
    }

    @Override
    public List<Match> buscarGte(Instant cutoff, Consumer<String> progreso) throws Exception {
        Hilos.avisarSiUi("AzarService.buscarGte");
        Random rnd = new Random();
        List<Match> deMuestra = gteDesdeMuestra(rnd);   // la muestra nocturna de sfr-data: cero llamadas y nunca una partida repetida
        if (!deMuestra.isEmpty()) return deMuestra;
        LbCtx ctx = nuevoCtx();
        if (!ctx.nocturno) throw new IOException(sinNocturno());   // ninguna llamada
        int ult = ultimaPaginaLadder(ctx);
        int[][] tramos = tramosGte(ult);
        int[] franjas = franjasAleatorias(5, tramos.length, rnd);   // dado independiente por partida
        List<List<Long>> perfilesPorSlot = new ArrayList<>();
        Set<Long> usados = new HashSet<>();
        int nPerfiles = 0;
        for (int s = 0; s < franjas.length; s++) {
            int[] tr = tramos[franjas[s]];
            int pag = tr[0] + rnd.nextInt(Math.max(1, tr[1] - tr[0] + 1));
            progreso.accept(t("Muestreando el ladder… (", "Sampling the ladder… (") + (s + 1) + "/" + franjas.length + ")");
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

        // Los (hasta 10) jugadores de los 5 slots en UNA llamada; luego cada partida va al slot de quien la jugó. Las 100
        // partidas se reparten entre los 10: si uno muy activo deja algún slot sin nada, una segunda llamada solo con los
        // jugadores de esos slots (como mucho 2). Si la primera falla (y no es Detener), el error se dice, no «no hay nada».
        List<Long> todos = new ArrayList<>();
        for (List<Long> ids : perfilesPorSlot) todos.addAll(ids);
        Collections.sort(todos);
        Map<Long, Match> leidas = new LinkedHashMap<>();
        if (!detieneEsteHilo()) {
            progreso.accept(t("Leyendo partidas de ", "Reading games of ") + todos.size() + t(" jugadores\u2026", " players\u2026"));
            try { leerLote(todos, leidas); }
            catch (InterruptedException ex) { if (!detieneEsteHilo()) throw ex; }
            catch (IOException ex) {
                log("Guess the ELO: fallo con el lote " + csv(todos) + ": " + causa(ex));
                if (!detieneEsteHilo()) throw ex;
            }
        }
        List<Long> sinNada = new ArrayList<>();
        for (List<Long> ids : perfilesPorSlot) if (delSlot(leidas.values(), ids).isEmpty()) sinNada.addAll(ids);
        if (!leidas.isEmpty() && !sinNada.isEmpty() && sinNada.size() < todos.size() && !detieneEsteHilo()) {
            Collections.sort(sinNada);
            try { leerLote(sinNada, leidas); }
            catch (Exception ex) { log("Guess the ELO: fallo con el segundo lote " + csv(sinNada) + ": " + causa(ex)); }
        }
        List<List<Match>> validasPorSlot = new ArrayList<>();
        for (List<Long> ids : perfilesPorSlot) validasPorSlot.add(filtrarGte(delSlot(leidas.values(), ids), cutoff, 3, rnd));
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

    @Override
    public boolean deMuestra() {
        return azarDeMuestra;
    }

    @Override
    public boolean conAnteayer() {
        return azarConAnteayer;
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
        Map<String, List<List<Object>>> ant = muestraAnteayer.get();   // 1.4: si la de ayer ya está vista, la de anteayer
        boolean hayAyer = muestra != null && !muestra.isEmpty(), hayAnt = ant != null && !ant.isEmpty();
        if (!hayAyer && !hayAnt) return List.of();
        if (!hayAyer) muestra = Map.of();
        Set<String> vistas = new HashSet<>(Arrays.asList(leerConfig("gte_vistas", "").split(",")));
        Set<String> claves = new LinkedHashSet<>(muestra.keySet());
        if (hayAnt) claves.addAll(ant.keySet());
        List<String> tramos = new ArrayList<>(claves); Collections.shuffle(tramos, rnd);
        List<Match> res = new ArrayList<>();
        for (String tr : tramos) {
            List<List<Object>> l = new ArrayList<>(muestra.getOrDefault(tr, List.of())); Collections.shuffle(l, rnd);
            if (hayAnt && ant.containsKey(tr)) { List<List<Object>> la = new ArrayList<>(ant.get(tr)); Collections.shuffle(la, rnd); l.addAll(la); }   // ayer primero
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
    private List<Match> azarDesdeMuestra(Map<String, List<List<Object>>> muestra, int lo, int hi, String mapaSel, String civSel, Random rnd, int max) {
        if (muestra == null) return new ArrayList<>();
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
        List<Match> out = new ArrayList<>(cand.subList(0, Math.min(max, cand.size())));
        for (Match m : out) azarEnsenadas.add(m.id);
        return out;
    }

    /** Una llamada: las partidas terminadas de ids (ordenados, hasta LOTE) a leidas, sin repetir. */
    private void leerLote(List<Long> ids, Map<Long, Match> leidas) throws IOException, InterruptedException {
        for (Match m : companion.partidas(csv(ids), 1, POR_LOTE))
            if (m != null && m.finished != null) leidas.putIfAbsent(m.id, m);
    }

    /** Las partidas en las que juega alguno de ids. */
    private static List<Match> delSlot(Iterable<Match> partidas, List<Long> ids) {
        List<Match> out = new ArrayList<>();
        for (Match m : partidas) if (m.players.stream().anyMatch(p -> ids.contains(p.id))) out.add(m);
        return out;
    }

    /** Una página del ladder nocturno (todas en caché desde nuevoCtx); fuera de rango, vacía. Nunca va a la red. */
    private PaginaLb lbPagina(LbCtx ctx, int p) {
        PaginaLb enCache = ctx.cache.get(p);
        return enCache != null ? enCache : new PaginaLb(List.of(), -1, -1);
    }

    /** «, »-separado para /matches?profile_ids=. */
    static String csv(List<Long> ids) {
        StringBuilder b = new StringBuilder();
        for (long id : ids) { if (b.length() > 0) b.append(','); b.append(id); }
        return b.toString();
    }

    /**
     * Contexto nuevo del leaderboard: con el ladder nocturno (1.4), todas sus páginas de 100 ya en caché, sin llamadas;
     * sin él (sfr-data caído o datos anteriores a la 1.4), vacío y no nocturno: quien llama lo dice y no llama a nadie
     * (1.4.1: ya no se cae al leaderboard en vivo).
     */
    private LbCtx nuevoCtx() {
        LbCtx ctx = new LbCtx();
        List<long[]> noct;
        try { noct = ladderNocturno.get(); }
        catch (RuntimeException ex) { log("azar: ladder nocturno: " + causa(ex)); noct = null; }
        if (noct == null || noct.isEmpty()) return ctx;
        ctx.nocturno = true;
        ctx.id = "nocturno";
        int n = noct.size();
        ctx.totalPaginas = (n + 99) / 100;
        for (int p = 1; p <= ctx.totalPaginas; p++) {
            List<long[]> js = new ArrayList<>(noct.subList((p - 1) * 100, Math.min(n, p * 100)));
            int max = -1, min = -1;
            for (long[] j : js) { int r = (int) j[1]; max = max < 0 ? r : Math.max(max, r); min = min < 0 ? r : Math.min(min, r); }
            ctx.cache.put(p, new PaginaLb(js, max, min));
        }
        log("azar: ladder nocturno de sfr-data: " + n + " jugadores en " + ctx.totalPaginas + " páginas (sin llamadas al leaderboard)");
        return ctx;
    }

    /** Número de páginas del ladder nocturno. */
    private int ultimaPaginaLadder(LbCtx ctx) {
        return ctx.totalPaginas;
    }

    /** Estado compartido de una consulta al leaderboard: id que responde
     *  (rm_1v1 o 3), total de páginas y caché por página para que la
     *  búsqueda binaria no repita peticiones. */
    private static final class LbCtx {
        boolean nocturno;   // páginas del ladder de anoche (sfr-data), ya todas en cache
        String id;
        int totalPaginas = -1;
        final Map<Integer, PaginaLb> cache = new HashMap<>();
    }
}
