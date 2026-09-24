package dev.tirador.aoe2radar.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;

import static dev.tirador.aoe2radar.cache.Canales.aprenderCanal;
import static dev.tirador.aoe2radar.cache.ImagenesMapa.MAPA_IMG_URL;
import static dev.tirador.aoe2radar.cache.Paises.aprenderPais;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.firstNonNull;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Json.str;
import static dev.tirador.aoe2radar.util.Json.val;
import static dev.tirador.aoe2radar.util.Json.when;
import static dev.tirador.aoe2radar.util.Log.log;

/** Convierte el JSON de partidas de la API del companion en Match/MatchPlayer (y aprende de paso país, canal e imagen de mapa). */
public final class Parseo {
    private Parseo() {}

    public static volatile String MAPA_IMG_PATRON;   // p. ej. https://…/maps/{clave}.png, deducido de la primera URL que contiene la clave del mapa
    static final java.util.concurrent.atomic.AtomicInteger COLOR_SLOT_DISTINTOS = new java.util.concurrent.atomic.AtomicInteger();

    /** Convierte el JSON de una partida. El resultado (won, rating, rating_diff)
     *  se guarda SOLO en memoria, para «Revelar resultado…». */
    public static Match parseMatch(Map<String, Object> j) {
        Match m = new Match();
        m.id = lng(val(j, "match_id", "matchId"));
        if (m.id <= 0) return null;
        m.started  = when(val(j, "started"));
        m.finished = when(val(j, "finished"));
        Object map = val(j, "map_name", "mapName");
        if (map == null) map = val(j, "map");
        if (map != null) m.map = str(map);
        Object img = val(j, "mapImageUrl", "map_image_url", "mapImage");   // la API da la imagen de cada mapa (CDN del companion): la recordamos por nombre y por clave
        if (img != null && String.valueOf(img).startsWith("http")) {
            String url = String.valueOf(img);
            if (m.map != null && MAPA_IMG_URL.putIfAbsent(m.map.toLowerCase(Locale.ROOT).trim(), url) == null) log("mapa: " + m.map + " → " + url);
            Object clave = val(j, "map");
            if (clave != null) {
                String k = String.valueOf(clave).toLowerCase(Locale.ROOT).trim();
                MAPA_IMG_URL.putIfAbsent(k, url);
                if (MAPA_IMG_PATRON == null && k.length() > 3 && url.toLowerCase(Locale.ROOT).contains(k)) {   // la URL lleva la clave: sirve de patrón para los mapas aún no vistos
                    int i = url.toLowerCase(Locale.ROOT).indexOf(k);
                    MAPA_IMG_PATRON = url.substring(0, i) + "{clave}" + url.substring(i + k.length());
                    log("mapa: patrón de imagen aprendido: " + MAPA_IMG_PATRON);
                }
            }
        }
        Object mode = val(j, "leaderboard_name", "leaderboardName");
        if (mode == null) mode = val(j, "game_mode_name", "gameModeName");
        if (mode != null) m.mode = str(mode);

        Object teams = val(j, "teams");
        if (teams instanceof List<?> ts && !ts.isEmpty()) {
            int t = 0;
            for (Object to : ts) {
                t++;
                for (Object po : arr(val(obj(to), "players"))) addPlayer(m, obj(po), t);
            }
        } else {
            for (Object po : arr(val(j, "players"))) {
                Map<String, Object> p = obj(po);
                int t = (int) lng(val(p, "team"));
                addPlayer(m, p, t > 0 ? t : 1);
            }
        }
        return m;
    }

    public static void addPlayer(Match m, Map<String, Object> p, int team) {
        MatchPlayer mp = new MatchPlayer();
        mp.id   = lng(val(p, "profile_id", "profileId"));
        mp.name = String.valueOf(firstNonNull(str(val(p, "name")), "?"));
        if (val(p, "color") instanceof Number cn) mp.color = cn.intValue();
        if (val(p, "slot") instanceof Number sn) mp.slot = sn.intValue();
        if (mp.color != null && mp.slot != null && !mp.color.equals(mp.slot)) COLOR_SLOT_DISTINTOS.incrementAndGet();   // contador de control: en ranked deberían coincidir
        aprenderPais(mp.id, val(p, "country"));
        Object civ = val(p, "civ_name", "civName");
        if (civ == null) civ = val(p, "civ");
        mp.civ  = civ == null ? null : str(civ);
        mp.team = team;
        Object rp = val(p, "replay");
        mp.replay = (rp instanceof Boolean b) ? b : null;
        // Resultado: SOLO en memoria, para «Revelar resultado…». Jamás en la
        // tabla ni en nombres de archivo.
        Object w = val(p, "won");
        mp.won = (w instanceof Boolean g) ? g : null;
        Object rt = val(p, "rating");
        mp.rating = (rt instanceof Number n1) ? (int) Math.round(n1.doubleValue()) : null;
        Object rd = val(p, "rating_diff", "ratingDiff");
        mp.ratingDiff = (rd instanceof Number n2) ? (int) Math.round(n2.doubleValue()) : null;
        Object tw = val(p, "social_twitch_channel", "socialTwitchChannel");
        if (tw != null && !"null".equals(String.valueOf(tw)) && !String.valueOf(tw).isBlank()) {
            mp.social = String.valueOf(tw).trim();
            aprenderCanal(mp.id, mp.social);
        }
        m.players.add(mp);
    }
}
