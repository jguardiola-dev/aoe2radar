package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.PaginaPartidas;

import java.io.IOException;

/**
 * De dónde salen las partidas recientes de uno o varios jugadores: lo que LiveService, BarridoVivos, TopLadderService y
 * HistorialPerfil piden a la red. Hoy la implementa {@link CompanionApi} (GET /matches?profile_ids=…); los servicios
 * reciben esta interfaz y no la clase, para que una fuente de respaldo (World's Edge, previsto para la 1.4) se pueda
 * enchufar sin tocarlos (plan B del inventario de la API). Todas van a la red: nunca en el EDT.
 */
public interface FuentePartidas {

    /** Las partidas recientes de los ids (uno o varios, separados por comas), ya como Match. Ver CompanionApi.partidas. */
    Iterable<Match> partidas(String ids, int pagina, int porPagina) throws IOException, InterruptedException;

    /** Como partidas(String…) para un solo jugador. */
    Iterable<Match> partidas(long pid, int pagina, int porPagina) throws IOException, InterruptedException;

    /** Una página de partidas de un jugador, convertida entera (con el número de brutas). Ver CompanionApi.pagina. */
    PaginaPartidas pagina(long pid, int pagina, int porPagina) throws IOException, InterruptedException;
}
