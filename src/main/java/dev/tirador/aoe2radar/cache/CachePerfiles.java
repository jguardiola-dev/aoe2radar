package dev.tirador.aoe2radar.cache;

import dev.tirador.aoe2radar.model.FichaPerfil;

/**
 * Caché en memoria de las fichas de perfil. PERFIL_CACHE caduca (Caducidad.PERFIL) y la usa service.ProfileService:
 * vigente() para decidir si se vuelve a pedir /profiles, ultimo() para pintar lo que se sabe. Las vinculadas, sus ELO
 * y las familias son estado de la sesión y viven en ProfileService.
 */
public final class CachePerfiles {
    private CachePerfiles() {}

    public static final CacheMemoria<Long, FichaPerfil> PERFIL_CACHE = CacheService.SISTEMA.memoria(Caducidad.PERFIL);
}
