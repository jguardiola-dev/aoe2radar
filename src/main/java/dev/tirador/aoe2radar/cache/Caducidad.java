package dev.tirador.aoe2radar.cache;

import java.time.Duration;

/**
 * Cuánto vale cada dato guardado, en un solo sitio. La regla para aplicarlas vive en CacheService (edad < caducidad).
 * Cambiar una cifra aquí cambia cada cuánto la app vuelve a pedir ese dato: ojo con el companion («nocturno primero»).
 */
public final class Caducidad {
    private Caducidad() {}

    /** Resumen de /profiles (ladders, país, clan, partidas): el hover y la cabecera del perfil. */
    public static final Duration PERFIL = Duration.ofMinutes(30);
    /** La tarjeta de perfil ya pintada (datos + sparkline). */
    public static final Duration TARJETA = Duration.ofMinutes(10);
    /** Resúmenes nocturnos de sfr-data: ladder y civ stats (rama «data», en disco y memoria) e índice de perfiles. */
    public static final Duration NOCTURNO = Duration.ofHours(6);
    /** Archivos de la release «perfiles» de sfr-data guardados en disco; el ELO de ayer y la muestra valen lo mismo en memoria. */
    public static final Duration DESCARGA_DIARIA = Duration.ofHours(12);
    /**
     * Tras una descarga fallida de sfr-data, cuánto esperar para reintentar. Corto a propósito: mientras falte el
     * volcado, esos datos salen del companion; por eso se reintenta pronto (en segundo plano, sin hacer esperar a nadie).
     */
    public static final Duration REINTENTO = Duration.ofMinutes(5);
    /** Comprobar si el tech tree cambió. */
    public static final Duration TECHTREE = Duration.ofHours(24);
}
