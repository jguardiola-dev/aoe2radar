package dev.tirador.aoe2radar.model;

/**
 * Un directo de Twitch según el companion, tal como viene: los textos quedan null si faltan (las pantallas los
 * leen con String.valueOf, que da «null», y lo filtran como en la 1.1).
 *
 * @param login    user_login (o userLogin): el canal
 * @param nombre   user_name (o userName): el nombre visible
 * @param titulo   title
 * @param idioma   language
 * @param viewers  viewer_count (o viewerCount); -1 si falta
 * @param tipo     type («live» si está en directo)
 */
public record Directo(String login, String nombre, String titulo, String idioma, long viewers, String tipo) { }
