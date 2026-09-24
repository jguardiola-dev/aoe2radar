package dev.tirador.aoe2radar.model;

import java.util.List;

public record Actividad(long pid, String nombre, List<Match> partidas, boolean completo, int paginas, long ms) { }
