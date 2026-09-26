package dev.tirador.aoe2radar.service;

/**
 * Los nombres visibles de mapas y civilizaciones a partir de sus claves del companion («arabia», «burmese»). En la app
 * salen de Civ Stats y del tech tree; en los tests, de lo que el test decida.
 */
public interface NombresJuego {
    /** Nombre del mapa para su clave (la clave nunca es vacía ni «unknown»: eso lo resuelve quien llama). */
    String mapa(String clave);

    /** Nombre de la civilización para su clave (no vacía). */
    String civ(String clave);
}
