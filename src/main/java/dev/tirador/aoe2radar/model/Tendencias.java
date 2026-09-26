package dev.tirador.aoe2radar.model;

import java.util.List;
import java.util.Map;

/** Tendencias: meses ordenados; partidas modo → mes → n; filas «modo|civ» → mes → {n, w}. */
public record Tendencias(List<String> meses, Map<String, Map<String, Integer>> partidas, Map<String, Map<String, int[]>> filas,
                  Map<String, Map<String, int[]>> filasMapa, Map<String, Map<String, int[]>> filasTramo, Map<String, Map<String, int[]>> filasMT) { }   // claves: modo|civ · modo|mapa|civ · modo|tramo|civ · modo|mapa|tramo|civ → mes → {n,w}
