package dev.tirador.aoe2radar.model;

public record CivAgg(String civ, int n, int w, long d) {
    public double wr() { return n == 0 ? 0 : 100.0 * w / n; }
}
