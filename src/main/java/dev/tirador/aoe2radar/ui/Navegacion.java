package dev.tirador.aoe2radar.ui;

/**
 * Lo que una vista puede pedir a la ventana para ir a OTRA vista. La vista no conoce la ventana ni las demás vistas:
 * pide «abre el tech tree de esta civ» y la ventana (hoy SpoilerFreeRecs; al final de la fase 3, Main + AppState)
 * se encarga de botones, tarjetas del CardLayout e historial. Crece con cada vista que sale.
 */
public interface Navegacion {
    /** Abre el Tech tree en la civ dada (clave del tech tree o del companion; null = la última o la primera). */
    void abrirTechTree(String civ);

    /** Abre Civ Stats. */
    void abrirCivStats();

    /** Abre Ratings. */
    void abrirLadder();

    /** Abre el perfil de pid (nombre: el que se muestra mientras carga). */
    void abrirPerfil(long pid, String nombre);

    /** Abre el perfil de pid en una pestaña nueva del perfil. */
    void abrirPerfilEnPestana(long pid, String nombre);

    /** Abre Live now. */
    void abrirAhora();
}
