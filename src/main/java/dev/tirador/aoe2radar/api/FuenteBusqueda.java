package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.model.PerfilEncontrado;

import java.io.IOException;
import java.util.List;

/**
 * De dónde sale la búsqueda de jugadores por nick (BusquedaPerfilesCompanion). La implementan {@link CompanionApi}
 * (/profiles?search=, por subcadena) y {@link WorldsEdgeApi} (alias exacto); ConRespaldo.busqueda las junta (1.4).
 * Va a la red: nunca en el EDT.
 */
@FunctionalInterface
public interface FuenteBusqueda {
    List<PerfilEncontrado> buscarPerfiles(String q) throws IOException, InterruptedException;
}
