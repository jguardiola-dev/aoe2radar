package dev.tirador.aoe2radar.model;

/**
 * Un resultado de la búsqueda de nicks (/profiles?search=), tal como viene, sin filtrar ni aprender nada.
 *
 * @param pid      profile_id (o profileId); -1 si falta: cada pantalla lo filtra
 * @param nombre   name; null si falta
 * @param pais     country tal cual; null si falta
 * @param partidas games; -1 si falta
 */
public record PerfilEncontrado(long pid, String nombre, String pais, long partidas) { }
