package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.util.Json;

import java.io.IOException;
import java.util.Map;

import static dev.tirador.aoe2radar.util.Json.obj;

/**
 * Los endpoints del companion que usa la app, con su URL construida en un solo sitio. Paso 1 de la migración: cada
 * método construye exactamente la URL que la app construía a mano y devuelve el JSON leído, igual que antes. Todos
 * pasan por ApiClient (freno, cancelación); todos reintentan ante 429 (textoCon429) salvo buscarPerfiles (texto).
 * Paso 2: devolver objetos del modelo.
 */
public final class CompanionApi {
    public static final String TWITCH_LIVE = "https://api.aoe2companion.com/twitch/live";

    private final ApiClient api;

    public CompanionApi(ApiClient api) { this.api = api; }

    /** GET /matches?profile_ids=…&page=…&per_page=… (ids: uno o varios separados por comas). Raíz del JSON. */
    public Object matches(String ids, int pagina, int porPagina) throws IOException, InterruptedException {
        return Json.parse(api.textoCon429(Http.API + "/matches?profile_ids=" + ids + "&page=" + pagina + "&per_page=" + porPagina));
    }

    /** Como matches(String…) para un solo jugador. */
    public Object matches(long pid, int pagina, int porPagina) throws IOException, InterruptedException {
        return matches(String.valueOf(pid), pagina, porPagina);
    }

    /** GET /profiles/{pid}. */
    public Map<String, Object> perfil(long pid) throws IOException, InterruptedException {
        return obj(Json.parse(api.textoCon429(Http.API + "/profiles/" + pid)));
    }

    /** GET /leaderboards/{id}?page=…&per_page=… y, si pais no es null, &country=pais (también si es ""). */
    public Map<String, Object> leaderboard(String id, int pagina, int porPagina, String pais) throws IOException, InterruptedException {
        return obj(Json.parse(api.textoCon429(Http.API + "/leaderboards/" + id + "?page=" + pagina + "&per_page=" + porPagina
                + (pais != null ? "&country=" + pais : ""))));
    }

    /**
     * GET /profiles?search=…&page=1 (q sin espacios en los extremos, codificado en UTF-8). SIN reintento ante 429 (por
     * texto(), que sí lo cuenta al freno): es la búsqueda de nicks; si falla, la siguiente tecla o búsqueda lo repite.
     */
    public Object buscarPerfiles(String q) throws IOException, InterruptedException {
        return Json.parse(api.texto(Http.API + "/profiles?search=" + java.net.URLEncoder.encode(q.trim(), java.nio.charset.StandardCharsets.UTF_8) + "&page=1"));
    }

    /** Directos de AoE2 en Twitch según el companion (game=13389). */
    public Object twitchDirectos() throws IOException, InterruptedException {
        return Json.parse(api.textoCon429(TWITCH_LIVE + "?game=13389"));
    }

    /** El directo de un canal concreto (el nombre va en minúsculas, como en la 1.1). */
    public Object twitchCanal(String canal) throws IOException, InterruptedException {
        return Json.parse(api.textoCon429(TWITCH_LIVE + "?channel=" + canal.toLowerCase()));
    }
}
