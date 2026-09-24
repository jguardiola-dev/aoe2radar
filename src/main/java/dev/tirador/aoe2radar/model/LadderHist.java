package dev.tirador.aoe2radar.model;

import java.util.Map;

public record LadderHist(int total, int min, int[] bins, int mediana, Map<String, Integer> percentiles) { }
