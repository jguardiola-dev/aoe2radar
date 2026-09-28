package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.model.Clasificacion;
import dev.tirador.aoe2radar.model.FilaClasificacion;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.PaginaPartidas;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.PerfilEncontrado;
import dev.tirador.aoe2radar.util.Json;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Json.str;
import static dev.tirador.aoe2radar.util.Json.val;

/**
 * La API «community» de World's Edge (el backend del propio juego), hermana de {@link CompanionApi}: la fuente de
 * RESPALDO cuando el companion falla (ver ConRespaldo). Mismos ids de jugador y de partida que el companion (los de
 * Relic), así que las recs siguen saliendo de aoe.ms sin traducir nada. Sin clave ni sesión: solo GET públicos.
 * <p>Todo pasa por un ApiClient (freno propio para *.worldsedgelink.com, ver Freno.aplicaAWorldsEdge). Un error
 * lógico llega con HTTP 200 y {@code result.code != 0}: sale como IOException, igual que un HTTP de error.
 * <p>Conversiones PURAS al mismo modelo que el companion (Match, Perfil, Clasificacion, PerfilEncontrado): no aprenden
 * país ni canal (a diferencia de Parseo.parseMatch) y lo que World's Edge no tiene queda null o vacío. Lo que falta
 * respecto al companion (por eso la UI dice «datos parciales»): serie histórica de ELO, cuentas vinculadas, canal de
 * Twitch, racha de las últimas 10, filtro de país del ladder, paginación del historial (solo ~10 partidas por tipo),
 * partidas en curso (solo aparecen al terminar), búsqueda por subcadena (solo alias exacto) y el mapa de los mapas
 * personalizados («my map»). API no documentada (ingeniería inversa): si cambia, lo dirán WorldsEdgeApiTest y
 * tools/grabar_worldsedge.py. Va a la red: nunca en el EDT.
 */
public final class WorldsEdgeApi implements FuentePartidas, FuenteLadder, FuentePerfil, FuenteBusqueda {
    /** El host, en un solo sitio (ya cambió una vez: aoe-api.reliclink.com). También lo usa service.Juego (lobbies). */
    public static final String HOST = "https://aoe-api.worldsedgelink.com";
    public static final String COMMUNITY = HOST + "/community";
    static final String LEADERBOARD = COMMUNITY + "/leaderboard";

    /** Máximo de filas por página de getLeaderBoard2 (documentación comunitaria). */
    static final int MAX_LADDER = 200;

    private final ApiClient api;
    /** civilization_id → nombre en inglés (getAvailableLeaderboards, una vez por sesión y solo si hace falta). */
    private volatile Map<Integer, String> civs;

    public WorldsEdgeApi(ApiClient api) { this.api = api; }

    // ----- Sobre -----

    /** Lee el cuerpo y comprueba el sobre: result.code 0 o IOException («World's Edge: código N MENSAJE»). */
    static Map<String, Object> sobre(String cuerpo) throws IOException {
        Map<String, Object> root = obj(Json.parse(cuerpo));
        Map<String, Object> r = obj(val(root, "result"));
        long codigo = lng(val(r, "code"));
        if (codigo != 0) throw new ErrorWorldsEdge(codigo, str(val(r, "message")));
        return root;
    }

    /** Un error lógico de World's Edge (HTTP 200 con result.code distinto de 0). 9 = UNKNOWN_ALIASES. */
    static final class ErrorWorldsEdge extends IOException {
        final long codigo;
        ErrorWorldsEdge(long codigo, String mensaje) { super("World's Edge: código " + codigo + " " + mensaje); this.codigo = codigo; }
    }

    private Map<String, Object> pedir(String url) throws IOException, InterruptedException {
        return sobre(api.textoCon429(url));
    }

    /** Un array JSON en la query ([a,b] con los corchetes codificados), como lo pide la API. */
    static String lista(String csv) { return "%5B" + csv + "%5D"; }

    // ----- Partidas -----

    /**
     * Las partidas recientes de los ids (CSV) de getRecentMatchHistory: ~10 por tipo de partida y jugador, sin
     * paginación. Se ordenan de la más reciente a la más antigua (la API no las da en orden) y se devuelven las
     * porPagina primeras; la página 2 en adelante sale vacía (el historial largo no existe aquí).
     */
    @Override public Iterable<Match> partidas(String ids, int pagina, int porPagina) throws IOException, InterruptedException {
        return pagina(ids, pagina, porPagina).partidas();
    }

    @Override public Iterable<Match> partidas(long pid, int pagina, int porPagina) throws IOException, InterruptedException {
        return partidas(String.valueOf(pid), pagina, porPagina);
    }

    @Override public PaginaPartidas pagina(long pid, int pagina, int porPagina) throws IOException, InterruptedException {
        return pagina(String.valueOf(pid), pagina, porPagina);
    }

    private PaginaPartidas pagina(String ids, int pagina, int porPagina) throws IOException, InterruptedException {
        if (pagina > 1) return new PaginaPartidas(List.of(), 0);   // sin paginación: no se pide nada
        Map<String, Object> root = pedir(urlRecientes(ids));
        return aPagina(root, civs(), porPagina);
    }

    static String urlRecientes(String ids) {
        return LEADERBOARD + "/getRecentMatchHistory?title=age2&profile_ids=" + lista(ids.replace(" ", ""));
    }

    /** La partida matchId (solo si ya terminó: las que están en curso no aparecen), o null si no está. */
    public Match partida(long matchId) throws IOException, InterruptedException {
        Map<String, Object> root = pedir(LEADERBOARD + "/getMatchHistory?title=age2&matchIDs=" + lista(String.valueOf(matchId)));
        Map<Long, Map<String, Object>> perfiles = perfilesPorPid(root);
        Map<Integer, String> cat = civs();
        for (Object o : arr(val(root, "matchHistory"))) {
            Match m = aMatch(obj(o), perfiles, cat);
            if (m != null && m.id == matchId) return m;
        }
        return null;
    }

    /** Raíz de getRecentMatchHistory → las porPagina más recientes (por inicio, descendente). Pura. */
    static PaginaPartidas aPagina(Map<String, Object> root, Map<Integer, String> civs, int porPagina) {
        Map<Long, Map<String, Object>> perfiles = perfilesPorPid(root);
        List<Match> todas = new ArrayList<>();
        for (Object o : arr(val(root, "matchHistoryStats"))) {
            Match m = aMatch(obj(o), perfiles, civs);
            if (m != null) todas.add(m);
        }
        todas.sort(Comparator.comparing((Match m) -> m.started, Comparator.nullsLast(Comparator.reverseOrder())));
        List<Match> pagina = List.copyOf(todas.subList(0, Math.min(Math.max(porPagina, 0), todas.size())));
        return new PaginaPartidas(pagina, pagina.size());
    }

    /** «profiles» del historial: profile_id → su objeto (alias, país…). */
    static Map<Long, Map<String, Object>> perfilesPorPid(Map<String, Object> root) {
        Map<Long, Map<String, Object>> out = new HashMap<>();
        for (Object o : arr(val(root, "profiles"))) { Map<String, Object> p = obj(o); out.put(lng(val(p, "profile_id")), p); }
        return out;
    }

    /**
     * Una partida del historial → Match, con los mismos textos que da el companion donde se puede: mapa legible
     * (nombreMapa), modo como leaderboard_name (modo), civ por nombre (catálogo civs). Jugadores de matchhistorymember:
     * equipo = teamid + 1 (el companion cuenta desde 1), rating = oldrating (el de antes de la partida, como el
     * companion) y ratingDiff = newrating − oldrating, won = outcome (1 gana, 0 pierde). replay = true si hay URL de
     * rec de ese jugador (matchurls); si no, null (no se sabe). Sin país, color, slot ni canal. Pura.
     */
    static Match aMatch(Map<String, Object> j, Map<Long, Map<String, Object>> perfiles, Map<Integer, String> civs) {
        Match m = new Match();
        m.id = lng(val(j, "id"));
        if (m.id <= 0) return null;
        m.deRespaldo = true;   // para quien decide o guarda con estas partidas (vivos, catálogos): fuente parcial
        m.started = segundos(val(j, "startgametime"));
        m.finished = segundos(val(j, "completiontime"));
        String mapa = nombreMapa(str(val(j, "mapname")));
        if (mapa != null) m.map = mapa;
        String modo = modo(lng(val(j, "matchtype_id")));
        if (modo != null) m.mode = modo;
        Set<Long> conRec = new HashSet<>();
        for (Object o : arr(val(j, "matchurls"))) conRec.add(lng(val(obj(o), "profile_id")));
        List<MatchPlayer> jugadores = new ArrayList<>();
        for (Object o : arr(val(j, "matchhistorymember"))) {
            Map<String, Object> p = obj(o);
            MatchPlayer mp = new MatchPlayer();
            mp.id = lng(val(p, "profile_id"));
            String alias = str(val(perfiles.getOrDefault(mp.id, Map.of()), "alias"));
            mp.name = alias != null ? alias : "?";
            long civ = lng(val(p, "civilization_id"));
            mp.civ = civs.get((int) civ);
            long equipo = lng(val(p, "teamid"));
            mp.team = equipo >= 0 ? (int) equipo + 1 : 1;
            Object antes = val(p, "oldrating"), despues = val(p, "newrating");
            mp.rating = antes instanceof Number a ? (int) Math.round(a.doubleValue()) : null;
            mp.ratingDiff = antes instanceof Number a && despues instanceof Number d ? (int) Math.round(d.doubleValue() - a.doubleValue()) : null;
            long resultado = lng(val(p, "outcome"));
            mp.won = resultado == 1 ? Boolean.TRUE : resultado == 0 ? Boolean.FALSE : null;
            mp.replay = conRec.contains(mp.id) ? Boolean.TRUE : null;
            jugadores.add(mp);
        }
        jugadores.sort(Comparator.comparingInt(p -> p.team));   // por equipos, como el companion (estable: dentro, el orden de la API)
        m.players.addAll(jugadores);
        return m;
    }

    private static Instant segundos(Object o) {
        long s = lng(o);
        return s > 0 ? Instant.ofEpochSecond(s) : null;
    }

    /**
     * mapname → el nombre que da el companion: sin la extensión (.rms, .rms2), «_» como espacio, espacio entre
     * minúscula y mayúscula (AfricanClearing → African Clearing) y cada palabra con mayúscula inicial. «my map» (un
     * mapa personalizado: el nombre real va dentro del blob «options») y lo vacío → null (la partida queda con «?»).
     */
    static String nombreMapa(String mapname) {
        if (mapname == null || mapname.isBlank() || mapname.trim().equalsIgnoreCase("my map")) return null;
        String s = mapname.trim();
        int punto = s.lastIndexOf('.');
        if (punto > 0 && s.substring(punto).toLowerCase(Locale.ROOT).startsWith(".rms")) s = s.substring(0, punto);
        s = s.replace('_', ' ').replaceAll("(?<=\\p{Ll})(?=\\p{Lu})", " ");
        StringBuilder out = new StringBuilder();
        for (String palabra : s.trim().split("\\s+")) {
            if (palabra.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(palabra.charAt(0))).append(palabra.substring(1));
        }
        return out.length() == 0 ? null : out.toString();
    }

    /**
     * matchtype_id → leaderboard_name del companion (lo que miran esRankedRM, ReglasPartida y los filtros de modo).
     * Tabla de getAvailableLeaderboards: 6 RM 1v1 (ladder 3), 7-9 RM equipos (4), 2 y 60 DM 1v1 (1), 3-5 y 61 DM
     * equipos (2), 26 EW 1v1 (13), 27-29 EW equipos (14), 0 sin clasificar. El resto (Battle Royale, mando, POM,
     * RBW…) → null: la partida queda con «?», como cuando el companion no trae el campo.
     */
    static String modo(long matchtype) {
        return switch ((int) matchtype) {
            case 0 -> "Unranked";
            case 6 -> "1v1 Random Map";
            case 7, 8, 9 -> "Team Random Map";
            case 2, 60 -> "1v1 Deathmatch";
            case 3, 4, 5, 61 -> "Team Deathmatch";
            case 26 -> "1v1 Empire Wars";
            case 27, 28, 29 -> "Team Empire Wars";
            default -> null;
        };
    }

    /**
     * El catálogo de civs (getAvailableLeaderboards, «races»), pedido la primera vez que hace falta y guardado para la
     * sesión. Si falla, sin nombres de civ (vacío) y se volverá a intentar en la siguiente petición: la partida sigue
     * sirviendo para las recs, que es lo esencial.
     */
    Map<Integer, String> civs() throws InterruptedException {
        Map<Integer, String> c = civs;
        if (c != null) return c;
        try {
            c = aCivs(pedir(LEADERBOARD + "/getAvailableLeaderboards?title=age2"));
            civs = c;
            return c;
        } catch (IOException | RuntimeException ex) {
            dev.tirador.aoe2radar.util.Log.log("World's Edge: sin catálogo de civs (" + dev.tirador.aoe2radar.util.Log.causa(ex) + ")");
            return Map.of();
        }
    }

    /** «races» de getAvailableLeaderboards → id → nombre en inglés. Pura. */
    static Map<Integer, String> aCivs(Map<String, Object> root) {
        Map<Integer, String> out = new HashMap<>();
        for (Object o : arr(val(root, "races"))) {
            Map<String, Object> r = obj(o);
            long id = lng(val(r, "id"));
            String nombre = str(val(r, "name"));
            if (id >= 0 && nombre != null) out.put((int) id, nombre);
        }
        return Map.copyOf(out);
    }

    // ----- Perfil -----

    /** getPersonalStat de un jugador, ya convertido (ver aPerfil). */
    @Override public Perfil perfil(long pid) throws IOException, InterruptedException {
        return aPerfil(pedir(LEADERBOARD + "/getPersonalStat?title=age2&profile_ids=" + lista(String.valueOf(pid))), pid);
    }

    /**
     * Raíz de getPersonalStat → Perfil del jugador pid: país, clan (vacío → null), steamId (de «/steam/NNN»), y una
     * entrada por ladder con su id numérico como texto («3», «4»…: PerfilesCompanion y FormaCompanion ya aceptan esa
     * forma), rating, puesto (−1 → null), máximo, victorias y derrotas. partidas = suma de victorias y derrotas de sus
     * ladders (el companion cuenta también las no clasificatorias). Sin serie, vinculadas ni canal. Pura.
     */
    static Perfil aPerfil(Map<String, Object> root, long pid) {
        Map<String, Object> miembro = Map.of();
        for (Object g : arr(val(root, "statGroups")))
            for (Object mo : arr(val(obj(g), "members")))
                if (lng(val(obj(mo), "profile_id")) == pid) miembro = obj(mo);
        long grupo = lng(val(miembro, "personal_statgroup_id"));
        List<Perfil.Ladder> ladders = new ArrayList<>();
        long partidas = 0;
        boolean alguna = false;
        for (Object o : arr(val(root, "leaderboardStats"))) {
            Map<String, Object> l = obj(o);
            if (grupo < 0 || lng(val(l, "statgroup_id")) != grupo) continue;
            Integer gan = entero(val(l, "wins")), per = entero(val(l, "losses"));
            ladders.add(new Perfil.Ladder(CompanionApi.idLadder(val(l, "leaderboard_id")), entero(val(l, "rating")),
                    puesto(val(l, "rank")), entero(val(l, "highestrating")), gan, per));
            if (gan != null) { partidas += gan; alguna = true; }
            if (per != null) { partidas += per; alguna = true; }
        }
        return new Perfil(str(val(miembro, "country")), vacioANull(str(val(miembro, "clanlist_name"))), alguna ? partidas : -1,
                null, steamId(str(val(miembro, "name"))), List.copyOf(ladders), List.of(), List.of());
    }

    private static Integer entero(Object o) { return o instanceof Number n ? (int) Math.round(n.doubleValue()) : null; }
    private static Integer puesto(Object o) { Integer r = entero(o); return r == null || r < 1 ? null : r; }
    private static String vacioANull(String s) { return s == null || s.isBlank() ? null : s; }

    /** «/steam/76561198000635167» → «76561198000635167»; otra cosa (Xbox, vacío) → null. */
    static String steamId(String nombre) {
        if (nombre == null || !nombre.startsWith("/steam/")) return null;
        String id = nombre.substring("/steam/".length());
        return id.isEmpty() ? null : id;
    }

    // ----- Ladder -----

    /**
     * Una página del ladder con getLeaderBoard2 (start = (pagina−1)·porPagina + 1, hasta MAX_LADDER filas). Ids del
     * companion traducidos (rm_1v1 → 3, rm_team → 4, ew_1v1 → 13, ew_team → 14; un número pasa tal cual). World's Edge
     * no filtra por país: con pais (no vacío) sale IOException antes de ir a la red, mejor sin datos que el top de todos
     * con la etiqueta de un país. Tampoco sabe de Twitch ni de las últimas 10.
     */
    @Override public Clasificacion clasificacion(String id, int pagina, int porPagina, String pais) throws IOException, InterruptedException {
        if (pais != null && !pais.isBlank()) throw new IOException("World's Edge: el ladder no se puede filtrar por país");
        int lb = idLadderWE(id);
        int n = Math.max(1, Math.min(porPagina, MAX_LADDER));
        int inicio = (Math.max(pagina, 1) - 1) * porPagina + 1;
        return aClasificacion(pedir(LEADERBOARD + "/getLeaderBoard2?title=age2&leaderboard_id=" + lb + "&sortBy=1&start=" + inicio + "&count=" + n), porPagina);
    }

    /** Ladder del companion → id numérico de World's Edge; uno desconocido, IOException (sin ir a la red). */
    static int idLadderWE(String id) throws IOException {
        if (id != null) {
            switch (id) {
                case "rm_1v1": return 3;
                case "rm_team": return 4;
                case "ew_1v1": return 13;
                case "ew_team": return 14;
                default:
                    try { return Integer.parseInt(id.trim()); } catch (NumberFormatException ignored) { }
            }
        }
        throw new IOException("World's Edge: ladder desconocido " + id);
    }

    /**
     * Raíz de getLeaderBoard2 → Clasificacion: una fila por elemento de leaderboardStats, en su orden, unida a su
     * statGroup por statgroup_id (pid, alias, país). partidas = victorias + derrotas; total = rankTotal. Sin canal
     * ni últimas 10 (0 de 0). Pura.
     */
    static Clasificacion aClasificacion(Map<String, Object> root, int porPagina) {
        Map<Long, Map<String, Object>> miembros = new HashMap<>();
        for (Object g : arr(val(root, "statGroups"))) {
            Map<String, Object> grupo = obj(g);
            List<Object> ms = arr(val(grupo, "members"));
            if (!ms.isEmpty()) miembros.put(lng(val(grupo, "id")), obj(ms.get(0)));
        }
        List<FilaClasificacion> filas = new ArrayList<>();
        for (Object o : arr(val(root, "leaderboardStats"))) {
            Map<String, Object> l = obj(o);
            Map<String, Object> mi = miembros.getOrDefault(lng(val(l, "statgroup_id")), Map.of());
            Integer gan = entero(val(l, "wins")), per = entero(val(l, "losses"));
            filas.add(new FilaClasificacion(lng(val(mi, "profile_id")), str(val(mi, "alias")), entero(val(l, "rating")),
                    puesto(val(l, "rank")), str(val(mi, "country")), null, segundos(val(l, "lastmatchdate")),
                    entero(val(l, "streak")), gan != null && per != null ? gan + per : null, 0, 0));
        }
        return new Clasificacion(List.copyOf(filas), lng(val(root, "rankTotal")), porPagina);
    }

    // ----- Búsqueda -----

    /**
     * Búsqueda por alias EXACTO (getPersonalStat?aliases=["…"]): distingue mayúsculas y no busca subcadenas. Un alias
     * desconocido (código 9, UNKNOWN_ALIASES) es una lista vacía, no un error. partidas = victorias + derrotas.
     */
    @Override public List<PerfilEncontrado> buscarPerfiles(String q) throws IOException, InterruptedException {
        String alias = q.trim().replace("\\", "\\\\").replace("\"", "\\\"");
        String url = LEADERBOARD + "/getPersonalStat?title=age2&aliases="
                + URLEncoder.encode("[\"" + alias + "\"]", StandardCharsets.UTF_8);
        try {
            return aEncontrados(pedir(url));
        } catch (ErrorWorldsEdge e) {
            if (e.codigo == 9) return List.of();
            throw e;
        }
    }

    /** Raíz de getPersonalStat por alias → un PerfilEncontrado por jugador (statGroups personales). Pura. */
    static List<PerfilEncontrado> aEncontrados(Map<String, Object> root) {
        Map<Long, Long> partidas = new HashMap<>();
        for (Object o : arr(val(root, "leaderboardStats"))) {
            Map<String, Object> l = obj(o);
            long g = lng(val(l, "statgroup_id"));
            long n = Math.max(0, lng(val(l, "wins"))) + Math.max(0, lng(val(l, "losses")));
            partidas.merge(g, n, Long::sum);
        }
        List<PerfilEncontrado> out = new ArrayList<>();
        for (Object g : arr(val(root, "statGroups")))
            for (Object mo : arr(val(obj(g), "members"))) {
                Map<String, Object> p = obj(mo);
                out.add(new PerfilEncontrado(lng(val(p, "profile_id")), str(val(p, "alias")), str(val(p, "country")),
                        partidas.getOrDefault(lng(val(p, "personal_statgroup_id")), -1L)));
            }
        return List.copyOf(out);
    }
}
