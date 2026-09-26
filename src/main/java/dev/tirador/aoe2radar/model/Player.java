package dev.tirador.aoe2radar.model;

public record Player(long id, String name, String grupo, long vinculo) {
    public Player(long id, String name, String grupo) { this(id, name, grupo, 0L); }
    @Override public String toString() { return name + "  ·  " + id; }
}
