package dev.tirador.aoe2radar.model;

import java.util.List;

/** Página del leaderboard ya parseada: jugadores {profileId, rating} y
 *  extremos de rating de la página. */
public record PaginaLb(List<long[]> jugadores, int ratingMax, int ratingMin) {}
