package dev.tirador.aoe2radar.service;

import java.nio.file.Path;
import java.util.Map;

/**
 * Contrato de la vista Tech tree hacia techtree.TechTreeDatos: la vista y su presentador (fase 3) no importan
 * TechTreeDatos directamente, solo este servicio. Un método por cada miembro estático que usa hoy la sección
 * «TECH TREE» de SpoilerFreeRecs (icono, tooltip, panel, ficha de civ, banda de winrate…): mismos nombres sin
 * el prefijo «tt» y mismas firmas que en TechTreeDatos, para que mover la vista no cambie ningún resultado.
 * <p>Hilos: asegurarDatos(), comprobarActualizacion(), arbol(civ) y descargar(rel) pueden ir a la red (bajan
 * o comprueban archivos de aoe2techtree) y no deben llamarse en el EDT (ver util.Hilos). El resto son lecturas
 * u operaciones puras sobre lo ya cargado.
 */
public interface TechTreeService {

    /** El catálogo completo (data.json) ya cargado, o null si aún no se llamó a asegurarDatos(). Sin red. */
    Map<String, Object> datos();

    /** ¿El árbol de civ ya está en caché de memoria (ttTrees)? Sin red: no lo descarga si falta. */
    boolean arbolEnCache(String civ);

    /** El árbol de civ tal como está en caché de memoria ahora mismo, o null si aún no se pidió. Sin red. */
    Map<String, Object> arbolCacheado(String civ);

    /** La carpeta base de los datos de techtree (para construir rutas de iconos que no encajan en rutaIcono). */
    Path dir();

    /** Ruta en disco del icono tipo/id.png dentro de la carpeta de techtree. Sin red: no comprueba si existe. */
    Path rutaIcono(String tipo, long id);

    /** El árbol de la civ: de la caché de memoria si ya está, si no lo descarga y lo guarda en caché. Puede ir a la red. */
    Map<String, Object> arbol(String civ) throws Exception;

    /** Carga (o descarga si falta) el catálogo y las cadenas. null si todo va bien; si no, el motivo. Puede ir a la red. */
    String asegurarDatos();

    /** Una vez al día: comprueba si hay una versión nueva del catálogo y la sustituye si la hay. Puede ir a la red. */
    void comprobarActualizacion();

    /** Nombre legible de una clase de bonus (id de TT_CLASES), en el idioma de la app. Sin red. */
    String clase(int id);

    /** Descarga un archivo relativo (bajo TT_RAW) a la carpeta de techtree. Va a la red. */
    void descargar(String rel) throws Exception;

    /** Cadena de un id de LanguageHelpId/LanguageNameId, limpia de saltos de línea, o "?" si no hay. Sin red. */
    String nombre(Object id);

    /** Nombre de la civ en el idioma de la app (name_string_id), o la clave si no hay traducción. Sin red. */
    String nombreCiv(String civ);

    /** Cadena cruda de un id de las strings del idioma actual, o null si no hay. Sin red. */
    String str(Object id);
}
