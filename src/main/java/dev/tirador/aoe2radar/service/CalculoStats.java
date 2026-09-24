package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.CivFila;
import dev.tirador.aoe2radar.model.VentanaStats;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Cálculos de Civ Stats sobre los datos ya cargados: agregados por civ y mapa, intervalos de Wilson y umbrales de muestra. */
public final class CalculoStats {
    private CalculoStats() {}

    public static final int MIN_PARTIDAS_CIV = 20;       // por debajo, la civ no se lista; por debajo de MUESTRA_FIABLE se pinta en gris
    public static final int MUESTRA_FIABLE = 100;
    public static final int POCAS_PARTIDAS = 2000;       // por debajo de este total, aviso discreto: prueba 90 o 365 días
    public static final int MIN_PARTIDAS_MAPA = 200;

    /** ¿La fila de tramo «tramo» entra en el rango elegido? Acepta «*», una clave simple o «desde|hasta» (extremos con * = sin límite). */
    public static boolean tramoEnRango(String tramo, List<String> tramos, String rango) {
        if (rango == null || "*".equals(rango) || "*|*".equals(rango)) return true;
        if (!rango.contains("|")) return tramo.equals(rango);
        String[] p = rango.split("\\|", -1);
        int i = tramos.indexOf(tramo); if (i < 0) return false;
        int a = "*".equals(p[0]) ? 0 : tramos.indexOf(p[0]), b = "*".equals(p[1]) ? tramos.size() - 1 : tramos.indexOf(p[1]);
        if (a < 0) a = 0; if (b < 0) b = tramos.size() - 1;
        if (a > b) { int x = a; a = b; b = x; }
        return i >= a && i <= b;
    }

    /** Intervalo de Wilson al 95 % en porcentaje: {inferior, superior}. */
    public static double[] wilson(int w, int n) {
        if (n <= 0) return new double[]{ 0, 100 };
        double z = 1.96, p = (double) w / n, den = 1 + z * z / n;
        double centro = p + z * z / (2 * n), marg = z * Math.sqrt((p * (1 - p) + z * z / (4 * n)) / n);
        return new double[]{ 100 * (centro - marg) / den, 100 * (centro + marg) / den };
    }

    /** Suma por civ con los filtros («*» = todos). */
    public static Map<String, CivAgg> agregarCivs(VentanaStats v, String modo, String mapa, String tramo) {
        Map<String, int[]> acc = new HashMap<>();
        Map<String, long[]> dur = new HashMap<>();
        for (CivFila f : v.civs()) {
            if (!f.modo().equals(modo)) continue;
            if (!"*".equals(mapa) && !f.mapa().equals(mapa)) continue;
            if (!tramoEnRango(f.tramo(), v.tramos(), tramo)) continue;
            int[] a = acc.computeIfAbsent(f.civ(), k -> new int[2]); a[0] += f.n(); a[1] += f.w();
            dur.computeIfAbsent(f.civ(), k -> new long[1])[0] += f.d();
        }
        Map<String, CivAgg> out = new HashMap<>();
        for (Map.Entry<String, int[]> en : acc.entrySet()) out.put(en.getKey(), new CivAgg(en.getKey(), en.getValue()[0], en.getValue()[1], dur.get(en.getKey())[0]));
        return out;
    }

    /** Partidas por mapa en el modo elegido: totales o, con tramo, solo las de ese tramo (filas civ / jugadores por partida). */
    public static Map<String, Integer> partidasPorMapa(VentanaStats v, String modo, String tramo) {
        if (tramo == null || "*".equals(tramo) || "*|*".equals(tramo)) return new LinkedHashMap<>(v.mapasPorModo().getOrDefault(modo, Map.of()));
        Map<String, Integer> porTramo = new HashMap<>();
        for (CivFila f : v.civs()) if (f.modo().equals(modo) && tramoEnRango(f.tramo(), v.tramos(), tramo)) porTramo.merge(f.mapa(), f.n(), Integer::sum);
        int jpp = modo.endsWith("_1v1") ? 2 : modo.endsWith("_2v2") ? 4 : modo.endsWith("_3v3") ? 6 : modo.endsWith("_4v4") ? 8 : 6;
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> en : porTramo.entrySet()) out.put(en.getKey(), en.getValue() / jpp);
        return out;
    }

    /** Winrate de una civ por mapa (para la ficha del tech tree): mapa → agg. */
    public static Map<String, CivAgg> civPorMapa(VentanaStats v, String modo, String tramo, String civ) {
        Map<String, int[]> acc = new HashMap<>();
        for (CivFila f : v.civs()) {
            if (!f.modo().equals(modo) || !f.civ().equals(civ)) continue;
            if (!tramoEnRango(f.tramo(), v.tramos(), tramo)) continue;
            int[] a = acc.computeIfAbsent(f.mapa(), k -> new int[2]); a[0] += f.n(); a[1] += f.w();
        }
        Map<String, CivAgg> out = new HashMap<>();
        for (Map.Entry<String, int[]> en : acc.entrySet()) out.put(en.getKey(), new CivAgg(en.getKey(), en.getValue()[0], en.getValue()[1], 0));
        return out;
    }

    public static String duracionMedia(long segundos, int n) {
        if (n == 0) return "-";
        long s = segundos / n;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60) + " min";
    }
}
