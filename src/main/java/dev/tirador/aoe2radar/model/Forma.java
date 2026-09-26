package dev.tirador.aoe2radar.model;

public record Forma(int diff, int w, int l, int racha, boolean rachaGana, int partidas) {
    public String corta() { return partidas == 0 ? "\u00B7" : (diff > 0 ? "\u25B2+" + diff : diff < 0 ? "\u25BC" + diff : "\u25AC 0"); }
}
