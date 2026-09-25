package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.model.Clasificacion;
import dev.tirador.aoe2radar.model.Directo;
import dev.tirador.aoe2radar.model.FilaClasificacion;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.PaginaPartidas;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.PerfilEncontrado;
import dev.tirador.aoe2radar.util.Json;

import java.io.IOException;
import java.time.Instant;
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
 * (texto). Devuelven tipos del modelo: los conversores son puros (no aprenden país/canal, salvo parseMatch, ver DEUDA)
 * y no inventan valores: lo que falta queda null (textos, Integer) o -1 (ids y contadores long); cada pantalla decide
 * lo suyo. Fuera de aquí solo lee JSON del companion el socket (LiveService).
 */
public final class CompanionApi {
    public static final String TWITCH_LIVE = "https://api.aoe2companion.com/twitch/live";

    private final ApiClient api;

    public CompanionApi(ApiClient api) { this.api = api; }

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
    public Iterable<Match> partidas(String ids, int pagina, int porPagina) throws IOException, InterruptedException {
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
    public Iterable<Match> partidas(long pid, int pagina, int porPagina) throws IOException, InterruptedException {
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
    public PaginaPartidas pagina(long pid, int pagina, int porPagina) throws IOException, InterruptedException {
        return aPagina(matches(pid, pagina, porPagina));
    }

    /** Raíz de /matches → PaginaPartidas: todas las partidas en orden (parseMatch, que aprende; ver DEUDA). */
    static PaginaPartidas aPagina(Object root) {
        List<Object> brutas = arr(val(obj(root), "matches"));
        List<Match> legibles = new ArrayList<>();
        for (Object o : brutas) { Match m = parseMatch(obj(o)); if (m != null) legibles.add(m); }
        return new PaginaPartidas(List.copyOf(legibles), brutas.size());
    }

    /** GET /profiles/{pid}, ya convertido (ver aPerfil). */
    public Perfil perfil(long pid) throws IOException, InterruptedException {
        return aPerfil(obj(Json.parse(api.textoCon429(Http.API + "/profiles/" + pid))));
    }

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
                    str(val(l, "leaderboard_id", "leaderboardId")),
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
                        fecha(val(pt, "date")),
                        val(pt, "rating") instanceof Number n ? n.intValue() : null,
                        val(pt, "rating_diff", "ratingDiff") instanceof Number n ? n.intValue() : null));
            }
            series.add(new Perfil.Serie(str(val(lb, "leaderboard_id", "leaderboardId")), List.copyOf(puntos)));
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

    /** GET /leaderboards/{id}?page=…&per_page=… y, si pais no es null, &country=pais (también si es ""). */
    public Map<String, Object> leaderboard(String id, int pagina, int porPagina, String pais) throws IOException, InterruptedException {
        return obj(Json.parse(api.textoCon429(Http.API + "/leaderboards/" + id + "?page=" + pagina + "&per_page=" + porPagina
                + (pais != null ? "&country=" + pais : ""))));
    }

    /** Como leaderboard(…), ya convertida (ver aClasificacion). */
    public Clasificacion clasificacion(String id, int pagina, int porPagina, String pais) throws IOException, InterruptedException {
        return aClasificacion(leaderboard(id, pagina, porPagina, pais));
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
                    fecha(val(pl, "last_match_time", "lastMatchTime")),
                    val(pl, "streak") instanceof Number n ? n.intValue() : null,
                    val(pl, "games") instanceof Number n ? n.intValue() : null,
                    ganadas10, jugadas10));
        }
        return new Clasificacion(List.copyOf(filas), lng(val(root, "total")), lng(val(root, "per_page", "perPage")));
    }

    /**
     * when() sin excepciones: una fecha absurda (p. ej. -1e30) queda null. El conversor lee la fecha de todas las filas,
     * también en pantallas que antes no la miraban; así no les añade un fallo nuevo (ver DEUDA).
     */
    static Instant fecha(Object o) {
        try { return when(o); } catch (RuntimeException e) { return null; }
    }

    /**
     * GET /profiles?search=…&page=1 (q sin espacios en los extremos, codificado en UTF-8). SIN reintento ante 429 (por
     * texto(), que sí lo cuenta al freno): es la búsqueda de nicks; si falla, la siguiente tecla o búsqueda lo repite.
     */
    public List<PerfilEncontrado> buscarPerfiles(String q) throws IOException, InterruptedException {
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
     * El directo de un canal concreto (el nombre va en minúsculas, como en la 1.1): el primero de la lista, o el objeto
     * si no llega lista. null si no hay ninguno. Que esté en directo lo dice tipo() («live»): lo mira la pantalla.
     */
    public Directo twitchCanal(String canal) throws IOException, InterruptedException {
        Object root = Json.parse(api.textoCon429(TWITCH_LIVE + "?channel=" + canal.toLowerCase()));
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
