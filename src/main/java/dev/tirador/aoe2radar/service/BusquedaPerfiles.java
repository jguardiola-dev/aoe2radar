package dev.tirador.aoe2radar.service;

import java.util.List;

/**
 * Buscar un perfil por nick, en un solo sitio: la usan los cinco buscadores de la interfaz (Ratings, Live, Watchlist,
 * Perfil…) y así ninguna vista de ui necesita hablar con la ventana ni con la API directamente. Las filas son
 * {id, nombre, texto a mostrar}, igual que en la 1.1.
 */
public interface BusquedaPerfiles {
    /** Sugerencias al teclear: el índice local si tiene algo (sin llamada a la red); si no, la API. */
    List<String[]> sugerir(String q);

    /** Búsqueda explícita (Enter): la API y el índice local, juntos y sin duplicados; primero lo local. */
    List<String[]> buscar(String q);

    /** Búsqueda solo en el índice local, sin red. La usan sugerir/buscar; addPlayerDialog (WatchlistView) ya no
     *  la llama aparte: desde la decisión 8 (DEUDA fila 112) usa buscar(q) como el resto de buscadores, en vez
     *  de combinar API + local con su propio orden. */
    List<String[]> local(String q);
}
