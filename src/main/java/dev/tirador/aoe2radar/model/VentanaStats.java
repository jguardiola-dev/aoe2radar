package dev.tirador.aoe2radar.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Una ventana de Civ Stats de sfr-data. matchupsPorMapa: los mismos matchups agrupados por mapa ("*" = todos los
 * mapas), hecho una vez al crearla: la matriz filtra por mapa sin recorrer las ~460.000 filas de v365 cada vez.
 */
public record VentanaStats(String etiqueta, String desde, String hasta, int dias, String parche, List<String> tramos,
                    Map<String, Map<String, Integer>> modos, Map<String, String> nombresMapas,
                    Map<String, Map<String, Integer>> mapasPorModo, List<CivFila> civs, List<Matchup> matchups,
                    Map<String, List<Matchup>> matchupsPorMapa) {

    /** La de siempre: agrupa los matchups por mapa al crearla. */
    public VentanaStats(String etiqueta, String desde, String hasta, int dias, String parche, List<String> tramos,
                        Map<String, Map<String, Integer>> modos, Map<String, String> nombresMapas,
                        Map<String, Map<String, Integer>> mapasPorModo, List<CivFila> civs, List<Matchup> matchups) {
        this(etiqueta, desde, hasta, dias, parche, tramos, modos, nombresMapas, mapasPorModo, civs, matchups, porMapa(matchups));
    }

    /** Los matchups agrupados por su mapa, en el mismo orden relativo. */
    public static Map<String, List<Matchup>> porMapa(List<Matchup> matchups) {
        Map<String, List<Matchup>> m = new HashMap<>();
        for (Matchup mu : matchups) m.computeIfAbsent(mu.mapa(), k -> new ArrayList<>()).add(mu);
        return m;
    }
}
