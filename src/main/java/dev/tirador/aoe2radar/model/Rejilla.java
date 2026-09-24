package dev.tirador.aoe2radar.model;

/** Rejilla de dispersión: celdas [ix, iy, n] sobre bins de 25 desde (minX, minY); recta y = a·x + b y correlación r. */
public record Rejilla(int n, int minX, int minY, int[][] celdas, double a, double b, double r) { }
