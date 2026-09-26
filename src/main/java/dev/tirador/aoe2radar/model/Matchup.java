package dev.tirador.aoe2radar.model;

/** Un cruce 1v1 de sfr-data: mapa "*" = todos los mapas (campo «matchups»); si no, la clave del mapa («matchups_mapa»). */
public record Matchup(String modo, String mapa, String tramo, String ca, String cb, int n, int wa) { }
