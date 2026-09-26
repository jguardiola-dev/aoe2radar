package dev.tirador.aoe2radar.model;

/**
 * El año de un jugador sacado del paquete nocturno de sfr-data, sin tocar la API.
 * hasta: fecha de los datos («2026-09-24…») tal como la da el paquete o su índice ("" si ninguno la trae).
 * pais: el que se conoce del jugador tras leer el paquete; null si ninguno.
 */
public record AnioSfr(Actividad actividad, String hasta, String pais) { }
