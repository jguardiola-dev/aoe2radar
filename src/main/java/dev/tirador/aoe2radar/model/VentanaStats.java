package dev.tirador.aoe2radar.model;

import java.util.List;
import java.util.Map;

public record VentanaStats(String etiqueta, String desde, String hasta, int dias, String parche, List<String> tramos,
                    Map<String, Map<String, Integer>> modos, Map<String, String> nombresMapas,
                    Map<String, Map<String, Integer>> mapasPorModo, List<CivFila> civs, List<Matchup> matchups) { }
