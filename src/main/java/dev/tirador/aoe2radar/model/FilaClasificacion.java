package dev.tirador.aoe2radar.model;

import java.time.Instant;

/**
 * Un jugador de una página de /leaderboards, tal como viene: sin valores por defecto ni efectos (no aprende país ni
 * canal). Lo que falta en el JSON queda null (pid, -1), y cada pantalla decide qué hacer con ello, como en la 1.1.
 *
 * @param pid          profile_id (o profileId); -1 si falta
 * @param nombre       name; null si falta
 * @param rating       rating redondeado; null si no es un número
 * @param rango        rank; null si no es un número
 * @param pais         country tal cual (sin normalizar); null si falta
 * @param canal        social_twitch_channel (o socialTwitchChannel); null si falta
 * @param ultimaPartida last_match_time (o lastMatchTime); null si falta o no se entiende
 * @param racha        streak; null si no es un número
 * @param partidas     games; null si no es un número
 * @param ganadas10    de last10MatchesWon (o last_10_matches_won): cuántas true
 * @param jugadas10    de la misma lista: cuántas son booleanos (0 si no hay lista)
 */
public record FilaClasificacion(long pid, String nombre, Integer rating, Integer rango, String pais, String canal,
                                Instant ultimaPartida, Integer racha, Integer partidas, int ganadas10, int jugadas10) { }
