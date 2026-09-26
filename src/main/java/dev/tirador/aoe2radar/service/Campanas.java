package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.FilaClasificacion;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static dev.tirador.aoe2radar.cache.Canales.aprenderCanal;
import static dev.tirador.aoe2radar.cache.Paises.aprenderPais;
import static dev.tirador.aoe2radar.cache.Paises.paisDe;
import static dev.tirador.aoe2radar.service.ConsultasLadder.miembrosClan;
import static dev.tirador.aoe2radar.sfrdata.Ladder.ladderAsegurar;
import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Campanas (aviso cuando alguien de una vista marcada entra en partida) y la fuente de datos de Live now: la
 * red, la config y la deduplicación, tal como los calculaba SpoilerFreeRecs. El toast y el resto de Swing se
 * quedan en la app; aquí solo la decisión de «¿toca avisar?» y la lista de jugadores de cada fuente.
 * <p>eloDe/todosJugadores llegan como parámetro en vez de por el constructor porque son estado de la app que
 * cambia con cada llamada (ver DEUDA: eloDe lee un HashMap escrito en el EDT desde un hilo de fondo, igual que
 * en la 1.1: no se ha tocado esa carrera de datos, solo se ha envuelto en una función, como en FormService).
 */
public final class Campanas {
    private final CompanionApi api;
    private final BiFunction<String, String, String> leerConfig;
    private final BiConsumer<String, String> guardarConfig;
    /** ¿Existe un grupo del usuario con ese nombre? Lo consulta normalizarVistaTodos, en cada lectura (no una
     *  copia): decide WatchlistView (sus grupos pueden cambiar en cualquier momento), Campanas solo pregunta. */
    private final Predicate<String> esGrupoDeUsuario;

    /** pid|matchId (o «mi|matchId») ya avisados: una campana solo avisa una vez por partida. */
    private final Set<String> avisadosClave = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public Campanas(CompanionApi api, BiFunction<String, String, String> leerConfig, BiConsumer<String, String> guardarConfig,
                     Predicate<String> esGrupoDeUsuario) {
        this.api = api;
        this.leerConfig = leerConfig;
        this.guardarConfig = guardarConfig;
        this.esGrupoDeUsuario = esGrupoDeUsuario;
    }

    // ----- el set de campanas activas (config «campanas») -----

    /**
     * El separador guardado en config, tal cual lo escribía la 1.1: el texto literal de 6 caracteres «\u0001»
     * (la barra doble en el código fuente escapa la barra, así que NO es el carácter de control U+0001, es
     * texto). No se puede cambiar sin invalidar las configs ya guardadas.
     */
    private static final String SEPARADOR = "\\u0001";

    public Set<String> campanas() {
        Set<String> s = new LinkedHashSet<>();
        // Pattern.quote: partir por el texto literal SEPARADOR, no por la expresión regular \u0001 (que Pattern
        // interpreta como el carácter real U+0001 y que, como tal, nunca aparece en lo guardado: antes del
        // arreglo esto dejaba pegadas todas las campanas en un único elemento cuando había más de una).
        for (String x : leerConfig.apply("campanas", "").split(Pattern.quote(SEPARADOR))) if (!x.isBlank()) s.add(normalizarVistaTodos(x));
        return s;
    }

    /**
     * El id de la campana del grupo especial «Todos»/«All» (WatchlistView.idVistaCampana: "grupo|" + el texto
     * de ese item del combo) se guardó con el texto del idioma activo en su día; si la app cambió de idioma
     * desde entonces, tradúcelo al idioma actual para que siga casando con las campanas recién calculadas.
     * Bug resuelto en fase 4 (ver docs/DEUDA.md): antes, cambiar de idioma apagaba esta campana sin avisar.
     * Los demás ids (grupo de usuario, ★ladder/★país/★clan) ya son estables: usan nombres propios o el
     * código ISO/tag del clan, nunca un texto traducido, así que no necesitan este arreglo.
     * <p>Decisión (fase 4): esta traducción solo vale para el pseudogrupo «Todos»/«All», no para un grupo de
     * usuario que se llame igual por casualidad (p.ej. uno literalmente llamado «All»). Por eso se pregunta a
     * esGrupoDeUsuario antes de traducir: si ese nombre es un grupo de verdad, el id se deja tal cual, porque
     * esa campana es la de ESE grupo y no la de «todos los jugadores».
     */
    private String normalizarVistaTodos(String id) {
        if (!id.startsWith("grupo|")) return id;
        String g = id.substring(6);
        if (!(g.equalsIgnoreCase("Todos") || g.equalsIgnoreCase("All"))) return id;
        if (esGrupoDeUsuario.test(g)) return id;
        return "grupo|" + t("Todos", "All");
    }

    public void guardarCampanas(Set<String> s) { guardarConfig.accept("campanas", String.join(SEPARADOR, s)); }

    /** Enciende o apaga la campana de una vista; devuelve si quedó encendida. */
    public boolean alternar(String id) {
        Set<String> s = campanas();
        boolean activo = !s.remove(id);
        if (activo) s.add(id);
        guardarCampanas(s);
        return activo;
    }

    /** ¿pid está en alguna vista con campana? campanaIds: id de vista → jugadores vigilados (estado de la app). */
    public boolean campanaContiene(long pid, Map<String, Set<Long>> campanaIds) {
        for (Set<Long> ids : campanaIds.values()) if (ids.contains(pid)) return true;
        return false;
    }

    /**
     * Recalcula los jugadores de cada vista con campana: grupo → todosJugadores; ★ladder/★país → el top del
     * ladder 1v1 (idsLeaderboard, vía CompanionApi); ★clan → miembrosClan (ConsultasLadder, ladder de sfr-data).
     * Un fallo en una vista no interrumpe las demás (se anota en el log, como en la 1.1). Va a la red.
     */
    public Map<String, Set<Long>> calcularCampanaIds(Set<String> vistas, List<Player> todosJugadores) {
        avisarSiUi("Campanas.calcularCampanaIds");
        Map<String, Set<Long>> nuevo = new HashMap<>();
        for (String id : vistas) {
            Set<Long> ids = new HashSet<>();
            try {
                if (id.startsWith("grupo|")) {
                    String g = id.substring(6);
                    for (Player p : todosJugadores) if (g.equalsIgnoreCase(t("Todos", "All")) || p.grupo().equalsIgnoreCase(g)) ids.add(p.id());
                } else if (id.equals("\u2605ladder")) {
                    for (long pid : idsLeaderboard(null, Integer.parseInt(leerConfig.apply("top_n", "50")))) ids.add(pid);
                } else if (id.startsWith("\u2605pais|")) {
                    for (long pid : idsLeaderboard(id.substring(6), Integer.parseInt(leerConfig.apply("top_n", "50")))) ids.add(pid);
                } else if (id.startsWith("\u2605clan|")) {
                    if (ladderAsegurar(false) == null) for (LadderRow r : miembrosClan(id.substring(6))) ids.add(r.pid());
                }
            } catch (Exception ex) { log("campanas " + id + ": " + causa(ex)); }
            nuevo.put(id, ids);
        }
        return nuevo;
    }

    /** Ids del top del ladder 1v1 (global o de un país), sin tocar la vista. */
    private List<Long> idsLeaderboard(String pais, int n) throws Exception {
        List<Long> out = new ArrayList<>();
        for (FilaClasificacion f : api.clasificacion("rm_1v1", 1, Math.min(100, Math.max(25, n)), pais).filas()) {
            long pid = f.pid(); if (pid > 0) out.add(pid); if (out.size() >= n) break;
        }
        return out;
    }

    // ----- deduplicación de avisos: «¿toca avisar?» (el toast lo pinta la app) -----

    /** «Mi partida»: solo si pid es mi_pid (config), hay partida y no se había avisado ya de este m.id. */
    public boolean tocaAvisarMiPartida(long pid, Match m) {
        String mi = leerConfig.apply("mi_pid", "");
        if (mi.isBlank() || pid != Long.parseLong(mi) || m == null) return false;
        return avisadosClave.add("mi|" + m.id);
    }

    /** Alguien vigilado entra en partida: solo si pid está en una vista con campana y no se había avisado ya. */
    public boolean tocaAvisar(long pid, Match m, Map<String, Set<Long>> campanaIds) {
        if (m == null || !campanaContiene(pid, campanaIds)) return false;
        return avisadosClave.add(pid + "|" + m.id);
    }

    // ----- la fuente de Live now: {pid, nombre, rating, rango, país} según el tipo elegido -----

    /**
     * top: los primeros limiteTop del ladder 1v1 mundial; pais: los 100 mejores de ese país; clan: sus
     * miembros en el ladder 1v1 (si el ladder está cargado); grupo: los de ese grupo, con su ELO ya conocido
     * (eloDe, ver la nota de la clase). Va a la red salvo en «grupo».
     */
    public List<Object[]> cargarFuenteLive(String tipo, String valor, int limiteTop, List<Player> todosJugadores, LongFunction<Integer> eloDe) throws Exception {
        avisarSiUi("Campanas.cargarFuenteLive");
        List<Object[]> top = new ArrayList<>();
        switch (tipo) {
            case "pais" -> {
                for (FilaClasificacion f : api.clasificacion("rm_1v1", 1, 100, valor).filas()) {
                    long pid = f.pid(); if (pid <= 0) continue;
                    int rating = f.rating() != null ? f.rating() : 0;
                    int rango = f.rango() != null ? f.rango() : 0;
                    aprenderPais(pid, f.pais()); aprenderCanal(pid, f.canal());
                    top.add(new Object[]{ pid, String.valueOf(f.nombre()), rating, rango, paisDe(pid) });
                }
            }
            case "clan" -> {
                if (ladderAsegurar(false) == null) for (LadderRow r : miembrosClan(valor)) { aprenderPais(r.pid(), r.country()); top.add(new Object[]{ r.pid(), r.name(), r.rating(), r.rank(), paisDe(r.pid()) }); }
                if (top.isEmpty()) log("live: clan «" + valor + "» sin miembros en el ladder 1v1");
            }
            case "grupo" -> {
                for (Player pl : todosJugadores) if (pl.grupo().equalsIgnoreCase(valor)) { Integer elo = eloDe.apply(pl.id()); top.add(new Object[]{ pl.id(), pl.name(), elo == null ? 0 : elo, 0, paisDe(pl.id()) }); }
            }
            default -> {
                for (int pag = 1; pag <= 3 && top.size() < limiteTop; pag++) {
                    for (FilaClasificacion f : api.clasificacion("rm_1v1", pag, 100, null).filas()) {
                        long pid = f.pid(); if (pid <= 0) continue;
                        int rating = f.rating() != null ? f.rating() : 0;
                        int rango = f.rango() != null ? f.rango() : top.size() + 1;
                        aprenderPais(pid, f.pais()); aprenderCanal(pid, f.canal());
                        top.add(new Object[]{ pid, String.valueOf(f.nombre()), rating, rango, paisDe(pid) });
                        if (top.size() >= limiteTop) break;
                    }
                }
            }
        }
        return top;
    }
}
