package dev.tirador.aoe2radar.model;

import java.util.List;

/**
 * Una página de /matches ya convertida: las partidas legibles, en orden, y cuántos elementos traía la página en bruto
 * (legibles o no). Las paginaciones deciden con «brutas» si es la última, como en la 1.1 (ms.isEmpty(), ms.size() < N).
 */
public record PaginaPartidas(List<Match> partidas, int brutas) { }
