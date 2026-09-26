package dev.tirador.aoe2radar.model;

import java.util.Map;

/**
 * La ficha de un perfil tal como la pintan las pantallas (cabecera del perfil, comparador del ladder, ficha del H2H).
 * ladders: ladder («rm_1v1», «rm_team», «ew_1v1», «ew_team») → {rating, rango, máximo, victorias, derrotas}, 0 si no
 * se sabe; solo están los ladders con fila. pais y clan: "" si no hay. partidas: totales del companion.
 * El mapa no se copia (lo rellena quien la construye, como en la 1.1); la fase 3 lo cambiará por un tipo.
 */
public record FichaPerfil(Map<String, int[]> ladders, String pais, String clan, long partidas) { }
