package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.model.Clasificacion;

import java.io.IOException;

/**
 * De dónde sale una página de la clasificación de un ladder: lo que TopLadderService y Campanas piden a la red. Hoy la
 * implementa {@link CompanionApi} (GET /leaderboards/{id}); los servicios reciben esta interfaz y no la clase, para que
 * una fuente de respaldo (World's Edge, previsto para la 1.4) se pueda enchufar sin tocarlos (plan B del inventario de
 * la API). Va a la red: nunca en el EDT.
 */
public interface FuenteLadder {

    /** Una página del ladder id (pais null: sin filtro de país), ya convertida. Ver CompanionApi.clasificacion. */
    Clasificacion clasificacion(String id, int pagina, int porPagina, String pais) throws IOException, InterruptedException;

    /** Como clasificacion(…), pero de ahora mismo: si la fuente guarda respuestas (CompanionApi.conCache), esta se la
     *  salta. Para lo que debe ser de ahora (el top ★ de la watchlist). Por defecto (una fuente sin caché), lo mismo que clasificacion. */
    default Clasificacion clasificacionFresca(String id, int pagina, int porPagina, String pais) throws IOException, InterruptedException {
        return clasificacion(id, pagina, porPagina, pais);
    }
}
