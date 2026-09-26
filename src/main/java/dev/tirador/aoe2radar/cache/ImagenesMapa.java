package dev.tirador.aoe2radar.cache;

import java.util.Map;

/** Imagen de cada mapa (nombre o clave → URL), aprendida de sfr-data (mapas.json) y de las partidas de la API. */
public final class ImagenesMapa {
    private ImagenesMapa() {}

    public static final Map<String, String> MAPA_IMG_URL = new java.util.concurrent.ConcurrentHashMap<>();   // nombre o clave de mapa → URL de imagen (aprendida de la API)
}
