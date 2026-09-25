package dev.tirador.aoe2radar.model;

/**
 * Un país del catálogo ISO con su nombre ya traducido al idioma de la app («España», "es"). Sale de
 * SpoilerFreeRecs (era un record anidado) porque lo usan varias vistas (el selector de país de la watchlist y
 * Live now): un dato, sin lógica ni Swing, así que vive en model.
 */
public record PaisItem(String nombre, String code) {
    @Override public String toString() { return nombre; }
}
