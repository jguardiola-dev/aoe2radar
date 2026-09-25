package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.techtree.TechTreeDatos;

import java.nio.file.Path;
import java.util.Map;

/**
 * TechTreeService sobre techtree.TechTreeDatos: cada método delega tal cual en su miembro tt* (mismo cálculo,
 * mismo estado estático). TechTreeDatos guarda ttData/ttStrings/ttTrees como estáticos porque los comparten la
 * UI y el harness de capturas (igual que CivStats para Civ Stats): este servicio no los duplica, solo evita que
 * la vista Tech tree tenga que importar el paquete techtree.
 * <p>Sin dependencias que inyectar (todo lo resuelve TechTreeDatos por sí solo), así que hay una única
 * instancia para toda la app, como CacheService.SISTEMA.
 */
public final class TechTreeServiceDatos implements TechTreeService {
    /** El de la app. */
    public static final TechTreeServiceDatos SISTEMA = new TechTreeServiceDatos();

    @Override public Map<String, Object> datos() { return TechTreeDatos.ttData; }

    @Override public boolean arbolEnCache(String civ) { return TechTreeDatos.ttTrees.containsKey(civ); }

    @Override public Map<String, Object> arbolCacheado(String civ) { return TechTreeDatos.ttTrees.get(civ); }

    @Override public Path dir() { return TechTreeDatos.TT_DIR; }

    @Override public Path rutaIcono(String tipo, long id) { return TechTreeDatos.ttRutaIcono(tipo, id); }

    @Override public Map<String, Object> arbol(String civ) throws Exception { return TechTreeDatos.ttArbol(civ); }

    @Override public String asegurarDatos() { return TechTreeDatos.ttAsegurarDatos(); }

    @Override public void comprobarActualizacion() { TechTreeDatos.ttComprobarActualizacion(); }

    @Override public String clase(int id) { return TechTreeDatos.ttClase(id); }

    @Override public void descargar(String rel) throws Exception { TechTreeDatos.ttDescargar(rel); }

    @Override public String nombre(Object id) { return TechTreeDatos.ttNombre(id); }

    @Override public String nombreCiv(String civ) { return TechTreeDatos.ttNombreCiv(civ); }

    @Override public String str(Object id) { return TechTreeDatos.ttStr(id); }
}
