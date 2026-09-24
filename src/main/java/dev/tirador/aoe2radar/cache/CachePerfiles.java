package dev.tirador.aoe2radar.cache;

import java.util.List;
import java.util.Map;

/** Cachés en memoria de perfiles, cuentas vinculadas y familias (sin TTL propio: cada uso decide). */
public final class CachePerfiles {
    private CachePerfiles() {}

    public static final Map<Long, Object[]> PERFIL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();   // pid → {ms, ladders, país}

    public static final Map<Long, List<Object[]>> VINCULADAS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();   // pid → {vid, nombre, país, partidas} (sesión)
    public static final Map<Long, Integer> ELO_VINC = new java.util.concurrent.ConcurrentHashMap<>();                  // vid → ELO 1v1 (sesión)

    /** Familias conocidas en la sesión (de consultar vinculadas): id -> {altId -> nombre}. */
    public static final Map<Long, Map<Long, String>> FAMILIA_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
}
