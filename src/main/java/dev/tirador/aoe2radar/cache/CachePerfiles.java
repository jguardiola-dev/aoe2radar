package dev.tirador.aoe2radar.cache;

import dev.tirador.aoe2radar.model.FichaPerfil;

import java.util.List;
import java.util.Map;

/**
 * Cachés en memoria de perfiles, cuentas vinculadas y familias. PERFIL_CACHE caduca (Caducidad.PERFIL) y la usa
 * service.ProfileService: vigente() para decidir si se vuelve a pedir /profiles, ultimo() para pintar lo que se sabe. Las vinculadas y familias son
 * estado de la sesión, sin caducidad (irán con ProfileService).
 */
public final class CachePerfiles {
    private CachePerfiles() {}

    public static final CacheMemoria<Long, FichaPerfil> PERFIL_CACHE = CacheService.SISTEMA.memoria(Caducidad.PERFIL);

    public static final Map<Long, List<Object[]>> VINCULADAS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();   // pid → {vid, nombre, país, partidas} (sesión)
    public static final Map<Long, Integer> ELO_VINC = new java.util.concurrent.ConcurrentHashMap<>();                  // vid → ELO 1v1 (sesión)

    /** Familias conocidas en la sesión (de consultar vinculadas): id -> {altId -> nombre}. */
    public static final Map<Long, Map<Long, String>> FAMILIA_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
}
