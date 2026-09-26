package dev.tirador.aoe2radar.model;

import java.util.Map;

/** Un jugador en la comparación del Ladder: rating y rango en cada ladder (una sola llamada al perfil). */
public record Comparado(long pid, String name, Map<String, int[]> ladders, String country, boolean deSeleccion) {
    public int rating(String lb) { int[] v = ladders.get(lb); return v == null ? 0 : v[0]; }
    public int rank(String lb) { int[] v = ladders.get(lb); return v == null ? 0 : v[1]; }
}
