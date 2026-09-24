package dev.tirador.aoe2radar.model;

import java.util.List;

/**
 * Una página de /leaderboards: sus filas en el orden del JSON (también las ilegibles, con pid -1: cada pantalla las
 * filtra a su manera) y los campos de paginación tal cual (-1 si faltan).
 */
public record Clasificacion(List<FilaClasificacion> filas, long total, long porPagina) { }
