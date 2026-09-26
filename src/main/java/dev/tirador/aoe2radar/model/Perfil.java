package dev.tirador.aoe2radar.model;

import java.time.Instant;
import java.util.List;

/**
 * Un perfil de /profiles/{pid}, tal como viene: sin valores por defecto ni efectos (no aprende país ni canal). Lo que
 * falta en el JSON queda null (partidas: -1), y cada pantalla decide qué hacer con ello, como en la 1.1.
 *
 * @param pais        country tal cual (sin normalizar); null si falta
 * @param clan        clan; null si falta
 * @param partidas    games; -1 si falta
 * @param canal       social_twitch_channel (o socialTwitchChannel); null si falta
 * @param steamId     steam_id (o steamId), sin recortar; null si falta
 * @param ladders     «leaderboards»: la situación actual en cada ladder, en el orden del JSON
 * @param series      «ratings»: la serie histórica de cada ladder, en el orden del JSON
 * @param vinculadas  linked_profiles (o linkedProfiles), en el orden del JSON y sin filtrar
 */
public record Perfil(String pais, String clan, long partidas, String canal, String steamId,
                     List<Ladder> ladders, List<Serie> series, List<Vinculada> vinculadas) {

    /**
     * @param id       leaderboard_id (o leaderboardId) como texto («3», «rm_1v1»…); null si falta
     * @param rating   redondeado; null si no es un número
     * @param ratingMax max_rating (o maxRating), redondeado; null si no es un número
     */
    public record Ladder(String id, Integer rating, Integer rango, Integer ratingMax, Integer ganadas, Integer perdidas) { }

    /** @param id leaderboard_id (o leaderboardId) como texto; null si falta */
    public record Serie(String id, List<Punto> puntos) { }

    /**
     * @param fecha  date; null si falta o no se entiende
     * @param rating TRUNCADO (intValue), no redondeado: así lo leía la 1.1 en la serie (ver DEUDA)
     * @param diff   rating_diff (o ratingDiff); null si no es un número
     */
    public record Punto(Instant fecha, Integer rating, Integer diff) { }

    /**
     * @param pid      profile_id (o profileId); -1 si falta
     * @param partidas games; -1 si falta
     */
    public record Vinculada(long pid, String nombre, String pais, long partidas) { }
}
