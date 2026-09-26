package dev.tirador.aoe2radar.model;

public final class MatchPlayer {
    public long id; public String name; public String civ; public int team; public Boolean replay;
    public Boolean won;             // solo para «Revelar resultado…»
    public Integer color, slot;     // color/slot del lobby: en ranked coinciden; sirven para pocket/flanco en 3v3 y 4v4
    public String social;           // canal de Twitch vinculado, si viene
    public Integer rating, ratingDiff;
}
