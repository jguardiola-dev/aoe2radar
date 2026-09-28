package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.model.Clasificacion;
import dev.tirador.aoe2radar.model.Directo;
import dev.tirador.aoe2radar.model.FilaClasificacion;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.PaginaPartidas;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.PerfilEncontrado;
import dev.tirador.aoe2radar.util.Json;
import dev.tirador.aoe2radar.util.Reloj;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static dev.tirador.aoe2radar.api.Parseo.parseMatch;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Json.str;
import static dev.tirador.aoe2radar.util.Json.val;
import static dev.tirador.aoe2radar.util.Json.when;

/**
 * Los endpoints del companion que usa la app, con su URL construida en un solo sitio (la misma que la 1.1 construía a
 * mano). Todos pasan por ApiClient (freno, cancelación); todos reintentan ante 429 (textoCon429) salvo buscarPerfiles
 * (texto). Construida con conCache(…), las fichas (/profiles/{pid}) y las páginas del ladder (/leaderboards) pasan
 * antes por una caché por URL (CacheRespuestas: 10 y 14 min, 500 entradas, solo 2xx); /matches, la búsqueda y Twitch
 * nunca: son tiempo real. La app la construye así desde la 1.3 (app.Servicios.COMPANION); el constructor de siempre,
 * sin caché, queda para los tests.
 * Devuelven tipos del modelo: los conversores son puros (no aprenden país/canal, salvo parseMatch, ver DEUDA)
 * y no inventan valores: lo que falta queda null (textos, Integer) o -1 (ids y contadores long); cada pantalla decide
 * lo suyo. Fuera de aquí solo lee JSON del companion el socket (LiveService).
 */
public final class CompanionApi implements FuentePartidas, FuenteLadder, FuentePerfil, FuenteBusqueda {
    public static final String TWITCH_LIVE = "https://api.aoe2companion.com/twitch/live";

    /** Cuánto vale una ficha /profiles/{pid} guardada: ELO, vinculadas y steamId no cambian en minutos. */
    static final long TTL_PERFIL_MS = 10 * 60_000L;
    /** Cuánto vale una página de /leaderboards: 14 min, uno menos que el ritmo de la recarga del top y de las campanas
     *  (15 min). Con 15 justos, la campana podía leer a los 15 min su propia página de hace 15 (plan API de la 1.3). */
    static final long TTL_LADDER_MS = 14 * 60_000L;
    /** Máximo de respuestas guardadas (fichas y páginas del ladder juntas); al pasarlo sale la menos usada. */
    static final int MAX_CACHE = 500;

    private final ApiClient api;
    private final CacheRespuestas cache;   // null: sin caché. Si no: /profiles/{pid} y /leaderboards/…, por URL, solo 2xx

    /** SIN caché: cada lectura sale a la red, como hasta la 1.3 (hoy, solo los tests; la app usa conCache). */
    public CompanionApi(ApiClient api) { this(api, (CacheRespuestas) null); }

    private CompanionApi(ApiClient api, CacheRespuestas cache) { this.api = api; this.cache = cache; }

    /**
     * CON caché de fichas y páginas del ladder (10 y 14 min, MAX_CACHE entradas). Reloj: Reloj.SISTEMA en la app, uno
     * falso en los tests. Quien pida datos que deben ser de ahora (el ELO 1v1, la tarjeta del hover, una recarga que
     * pide el usuario) tiene que ir por perfilFresco / leaderboardFresca / clasificacionFresca. Un acierto respeta
     * Detener (ApiClient.comprobarDetenida).
     */
    public static CompanionApi conCache(ApiClient api, Reloj reloj) {
        return new CompanionApi(api, new CacheRespuestas(reloj, MAX_CACHE));
    }

    /**
     * Json.parse(textoCon429(url)) pasando antes por la caché (si la hay): si url está y no ha caducado, no sale a la red
     * (ni gasta ficha del freno). Cada lectura parsea de nuevo: quien llama recibe su propio objeto.
     */
    private Object jsonConCache(String url, long ttlMs) throws IOException, InterruptedException {
        String guardado = cache == null ? null : cache.vigente(url);
        if (guardado != null) { api.comprobarDetenida(); return Json.parse(guardado); }   // Detener también corta los aciertos
        return jsonFresco(url, ttlMs);
    }

    /**
     * Siempre a la red; lo que llegue sustituye lo guardado (para quien pide «forzar» o «actualizar»). Solo se guarda
     * una respuesta 2xx que además se deja leer: un error (no 2xx, red) o un cuerpo ilegible salen como excepción, igual
     * que sin caché, y no se guardan.
     */
    private Object jsonFresco(String url, long ttlMs) throws IOException, InterruptedException {
        String cuerpo = api.textoCon429(url);
        Object json = Json.parse(cuerpo);
        if (cache != null) cache.poner(url, cuerpo, ttlMs);
        return json;
    }

    /** Olvida la respuesta guardada de esta URL exacta (la de perfil(…) o leaderboard(…)); la siguiente irá a la red. */
    public void invalidar(String url) { if (cache != null) cache.invalidar(url); }

    /** Olvida la ficha guardada de pid (p. ej. tras «Actualizar hoy», para que el ELO de la ficha no sea el de antes). */
    @Override public void invalidarPerfil(long pid) { invalidar(urlPerfil(pid)); }

    /** Lecturas que la caché ha ahorrado a la red desde el arranque. */
    public long aciertosCache() { return cache == null ? 0 : cache.aciertos(); }

    /** GET /matches?profile_ids=…&page=…&per_page=… (ids: uno o varios separados por comas). Raíz del JSON (interno). */
    Object matches(String ids, int pagina, int porPagina) throws IOException, InterruptedException {
        return Json.parse(api.textoCon429(Http.API + "/matches?profile_ids=" + ids + "&page=" + pagina + "&per_page=" + porPagina));
    }

    /**
     * Las partidas de matches(…) ya convertidas a Match, saltando las ilegibles. PEREZOSO a propósito: la descarga es
     * inmediata, pero cada partida se lee (parseMatch, que de paso aprende país, canal e imagen de mapa) solo cuando el
     * bucle la pide; si el bucle corta con break, las siguientes no se leen, igual que el bucle a mano de la 1.1.
     * Se recorre UNA sola vez: recorrerlo de nuevo volvería a leer (y a aprender) las partidas.
     */
    @Override public Iterable<Match> partidas(String ids, int pagina, int porPagina) throws IOException, InterruptedException {
        List<Object> brutas = arr(val(obj(matches(ids, pagina, porPagina)), "matches"));
        return () -> new Iterator<>() {
            int i;
            Match siguiente;
            @Override public boolean hasNext() {
                while (siguiente == null && i < brutas.size()) siguiente = parseMatch(obj(brutas.get(i++)));
                return siguiente != null;
            }
            @Override public Match next() {
                if (!hasNext()) throw new NoSuchElementException();
                Match m = siguiente; siguiente = null; return m;
            }
        };
    }

    /** Como partidas(String…) para un solo jugador. */
    @Override public Iterable<Match> partidas(long pid, int pagina, int porPagina) throws IOException, InterruptedException {
        return partidas(String.valueOf(pid), pagina, porPagina);
    }

    /** Como matches(String…) para un solo jugador. */
    Object matches(long pid, int pagina, int porPagina) throws IOException, InterruptedException {
        return matches(String.valueOf(pid), pagina, porPagina);
    }

    /**
     * GET /matches?[leaderboard_ids=…&]page=…&per_page=…: las partidas recientes de todo el ladder (el «río» de «Al
     * azar»), convertida entera. leaderboard null: sin filtro de ladder.
     */
    public PaginaPartidas recientes(String leaderboard, int pagina, int porPagina) throws IOException, InterruptedException {
        String filtro = leaderboard == null ? "" : "leaderboard_ids=" + leaderboard + "&";
        return aPagina(Json.parse(api.textoCon429(Http.API + "/matches?" + filtro + "page=" + pagina + "&per_page=" + porPagina)));
    }

    /**
     * Una página de partidas de un jugador, convertida entera (para paginaciones que recorren la página completa y
     * deciden con «brutas» si es la última). Si el bucle puede cortar a media página, usar partidas(…), que es perezoso.
     */
    @Override public PaginaPartidas pagina(long pid, int pagina, int porPagina) throws IOException, InterruptedException {
        return aPagina(matches(pid, pagina, porPagina));
    }

    /** Raíz de /matches → PaginaPartidas: todas las partidas en orden (parseMatch, que aprende; ver DEUDA). */
    static PaginaPartidas aPagina(Object root) {
        List<Object> brutas = arr(val(obj(root), "matches"));
        List<Match> legibles = new ArrayList<>();
        for (Object o : brutas) { Match m = parseMatch(obj(o)); if (m != null) legibles.add(m); }
        return new PaginaPartidas(List.copyOf(legibles), brutas.size());
    }

    /** GET /profiles/{pid}, ya convertido (ver aPerfil). Si hay caché (conCache), 10 min (TTL_PERFIL_MS). */
    @Override public Perfil perfil(long pid) throws IOException, InterruptedException {
        return aPerfil(obj(jsonConCache(urlPerfil(pid), TTL_PERFIL_MS)));
    }

    /** Como perfil(pid), pero siempre a la red (y renueva la caché): para lo que el usuario pide «forzar». */
    @Override public Perfil perfilFresco(long pid) throws IOException, InterruptedException {
        return aPerfil(obj(jsonFresco(urlPerfil(pid), TTL_PERFIL_MS)));
    }

    static String urlPerfil(long pid) { return Http.API + "/profiles/" + pid; }

    /**
     * Conversión pura de /profiles/{pid}: sin filtrar ni aprender nada. Cada campo se lee con las mismas claves y la
     * misma aritmética que usaban las pantallas de la 1.1 (ojo: el rating de la serie se trunca y el de los ladders
     * se redondea, como entonces).
     */
    static Perfil aPerfil(Map<String, Object> root) {
        List<Perfil.Ladder> ladders = new ArrayList<>();
        for (Object o : arr(val(root, "leaderboards"))) {
            Map<String, Object> l = obj(o);
            ladders.add(new Perfil.Ladder(
                    idLadder(val(l, "leaderboard_id", "leaderboardId")),
                    val(l, "rating") instanceof Number n ? (int) Math.round(n.doubleValue()) : null,
                    val(l, "rank") instanceof Number n ? n.intValue() : null,
                    val(l, "max_rating", "maxRating") instanceof Number n ? (int) Math.round(n.doubleValue()) : null,
                    val(l, "wins") instanceof Number n ? n.intValue() : null,
                    val(l, "losses") instanceof Number n ? n.intValue() : null));
        }
        List<Perfil.Serie> series = new ArrayList<>();
        for (Object o : arr(val(root, "ratings"))) {
            Map<String, Object> lb = obj(o);
            List<Perfil.Punto> puntos = new ArrayList<>();
            for (Object p : arr(val(lb, "ratings"))) {
                Map<String, Object> pt = obj(p);
                puntos.add(new Perfil.Punto(
                        when(val(pt, "date")),
                        val(pt, "rating") instanceof Number n ? n.intValue() : null,
                        val(pt, "rating_diff", "ratingDiff") instanceof Number n ? n.intValue() : null));
            }
            series.add(new Perfil.Serie(idLadder(val(lb, "leaderboard_id", "leaderboardId")), List.copyOf(puntos)));
        }
        List<Perfil.Vinculada> vinculadas = new ArrayList<>();
        for (Object o : arr(val(root, "linked_profiles", "linkedProfiles"))) {
            Map<String, Object> lp = obj(o);
            vinculadas.add(new Perfil.Vinculada(lng(val(lp, "profile_id", "profileId")), str(val(lp, "name")),
                    str(val(lp, "country")), lng(val(lp, "games"))));
        }
        return new Perfil(str(val(root, "country")), str(val(root, "clan")), lng(val(root, "games")),
                str(val(root, "social_twitch_channel", "socialTwitchChannel")), str(val(root, "steam_id", "steamId")),
                List.copyOf(ladders), List.copyOf(series), List.copyOf(vinculadas));
    }

    /**
     * Id de ladder como texto. Json.parse lee todo número como Double: un id numérico (3) daría «3.0» y no casaría con
     * "3" en las pantallas. Si es un número entero, se escribe sin decimales; lo demás, como str() (null si falta).
     */
    static String idLadder(Object o) {
        if (o instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && !Double.isInfinite(n.doubleValue()))
            return String.valueOf(n.longValue());
        return str(o);
    }

    /**
     * GET /leaderboards/{id}?page=…&per_page=… y, si pais no es null, &country=pais (también si es ""). Si hay caché
     * (conCache), 14 min (TTL_LADDER_MS) por URL completa: la misma página del mismo ladder y país, pedida por dos pantallas, sale
     * una vez a la red. Cada llamada recibe su propio mapa (se guarda el texto, no el objeto).
     */
    public Map<String, Object> leaderboard(String id, int pagina, int porPagina, String pais) throws IOException, InterruptedException {
        return obj(jsonConCache(urlLeaderboard(id, pagina, porPagina, pais), TTL_LADDER_MS));
    }

    /** Como leaderboard(…), pero siempre a la red (y renueva la caché): para lo que el usuario pide «forzar». */
    public Map<String, Object> leaderboardFresca(String id, int pagina, int porPagina, String pais) throws IOException, InterruptedException {
        return obj(jsonFresco(urlLeaderboard(id, pagina, porPagina, pais), TTL_LADDER_MS));
    }

    static String urlLeaderboard(String id, int pagina, int porPagina, String pais) {
        return Http.API + "/leaderboards/" + id + "?page=" + pagina + "&per_page=" + porPagina
                + (pais != null ? "&country=" + pais : "");
    }

    /** Como leaderboard(…), ya convertida (ver aClasificacion). */
    @Override public Clasificacion clasificacion(String id, int pagina, int porPagina, String pais) throws IOException, InterruptedException {
        return aClasificacion(leaderboard(id, pagina, porPagina, pais));
    }

    /** Como leaderboardFresca(…), ya convertida: para la recarga del top que pide el usuario. */
    @Override public Clasificacion clasificacionFresca(String id, int pagina, int porPagina, String pais) throws IOException, InterruptedException {
        return aClasificacion(leaderboardFresca(id, pagina, porPagina, pais));
    }

    /**
     * Conversión pura de la raíz de /leaderboards: una fila por elemento de «players», en orden, sin filtrar ni
     * aprender nada. Cada campo se lee con las mismas claves y la misma aritmética que usaban las pantallas de la 1.1.
     */
    static Clasificacion aClasificacion(Map<String, Object> root) {
        List<FilaClasificacion> filas = new ArrayList<>();
        for (Object o : arr(val(root, "players"))) {
            Map<String, Object> pl = obj(o);
            int ganadas10 = 0, jugadas10 = 0;
            if (val(pl, "last10MatchesWon", "last_10_matches_won") instanceof List<?> l10)
                for (Object b : l10) if (b instanceof Boolean bb) { jugadas10++; if (bb) ganadas10++; }
            filas.add(new FilaClasificacion(
                    lng(val(pl, "profile_id", "profileId")),
                    str(val(pl, "name")),
                    val(pl, "rating") instanceof Number n ? (int) Math.round(n.doubleValue()) : null,
                    val(pl, "rank") instanceof Number n ? n.intValue() : null,
                    str(val(pl, "country")),
                    str(val(pl, "social_twitch_channel", "socialTwitchChannel")),
                    when(val(pl, "last_match_time", "lastMatchTime")),
                    val(pl, "streak") instanceof Number n ? n.intValue() : null,
                    val(pl, "games") instanceof Number n ? n.intValue() : null,
                    ganadas10, jugadas10));
        }
        return new Clasificacion(List.copyOf(filas), lng(val(root, "total")), lng(val(root, "per_page", "perPage")));
    }

    /**
     * GET /profiles?search=…&page=1 (q sin espacios en los extremos, codificado en UTF-8). SIN reintento ante 429 (por
     * texto(), que sí lo cuenta al freno): es la búsqueda de nicks; si falla, la siguiente tecla o búsqueda lo repite.
     */
    @Override public List<PerfilEncontrado> buscarPerfiles(String q) throws IOException, InterruptedException {
        Object root = Json.parse(api.texto(Http.API + "/profiles?search=" + java.net.URLEncoder.encode(q.trim(), java.nio.charset.StandardCharsets.UTF_8) + "&page=1"));
        List<PerfilEncontrado> out = new ArrayList<>();
        for (Object o : arr(val(obj(root), "profiles"))) {
            Map<String, Object> p = obj(o);
            out.add(new PerfilEncontrado(lng(val(p, "profile_id", "profileId")), str(val(p, "name")),
                    str(val(p, "country")), lng(val(p, "games"))));
        }
        return List.copyOf(out);
    }

    /** Directos de AoE2 en Twitch según el companion (game=13389). La respuesta llega como lista o como {data: […]}. */
    public List<Directo> twitchDirectos() throws IOException, InterruptedException {
        Object root = Json.parse(api.textoCon429(TWITCH_LIVE + "?game=13389"));
        List<Directo> out = new ArrayList<>();
        for (Object o : root instanceof List<?> l ? l : arr(val(obj(root), "data"))) out.add(aDirecto(o));
        return List.copyOf(out);
    }

    /**
     * El directo de un canal concreto (el nombre va en minúsculas, como en la 1.1, pero con Locale.ROOT: con la Locale
     * turca la «I» daría «ı»): el primero de la lista, o el objeto si no llega lista. null si no hay ninguno. Que esté
     * en directo lo dice tipo() («live»): lo mira la pantalla.
     */
    public Directo twitchCanal(String canal) throws IOException, InterruptedException {
        Object root = Json.parse(api.textoCon429(TWITCH_LIVE + "?channel=" + canal.toLowerCase(java.util.Locale.ROOT)));
        Object primero = root instanceof List<?> l ? (l.isEmpty() ? null : l.get(0)) : root;
        return primero == null ? null : aDirecto(primero);
    }

    /** Conversión pura de un directo: textos con str (null si faltan), viewers con lng (-1 si faltan). */
    static Directo aDirecto(Object o) {
        Map<String, Object> s = obj(o);
        return new Directo(str(val(s, "user_login", "userLogin")), str(val(s, "user_name", "userName")),
                str(val(s, "title")), str(val(s, "language")), lng(val(s, "viewer_count", "viewerCount")), str(val(s, "type")));
    }
}
