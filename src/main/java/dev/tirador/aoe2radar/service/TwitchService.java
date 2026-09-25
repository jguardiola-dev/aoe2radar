package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Player;

import java.util.List;
import java.util.Map;

/**
 * Lo que necesita el área de Directos (Twitch) sin tocar la red directamente: el barrido de canales AoE2 en directo
 * cruzado con la watchlist visible, y la miniatura en vivo de un canal. Sale de SpoilerFreeRecs.vigilarTwitch/
 * cargarMiniaturas (la parte de red y de cache.Canales); la decisión de CUÁNDO barrer (throttle, anti-solape) y la
 * actualización de Swing se quedan en ui.DirectosPresenter/ui.DirectosView. Ver TwitchServiceCompanion para la
 * implementación (companion + cache.Canales).
 * <p>El presentador de Directos no puede importar java.awt (regla de la fase 3): por eso miniatura() ya decodifica
 * y escala la imagen aquí (igual que hacía la 1.1 en su hilo "miniaturas-twitch") y entrega los píxeles en crudo;
 * la vista los vuelve a montar en un ImageIcon, que sí es cosa suya.
 * <p>Hilos: barrer() y miniatura() van a la red (bloqueantes): no llamar en el EDT.
 */
public interface TwitchService {

    /**
     * Un barrido: quién de la watchlist visible retransmite (pid → {canal, título, viewers}), la lista completa de
     * canales AoE2 en directo (para la tabla de Directos) y si la llamada al listado global no respondió. Con
     * fallo=true, el llamador debe conservar el estado anterior (no vaciar twitchLive/directosAoE2), igual que la 1.1.
     */
    record Resultado(Map<Long, String[]> enVivo, List<String[]> directosAoE2, boolean fallo) { }

    /** Cruza el listado global de directos de Twitch con los jugadores visibles. Va a la red. */
    Resultado barrer(List<Player> visibles);

    /** Una miniatura ya decodificada y escalada a (ancho × alto), en ARGB fila a fila (formato de BufferedImage.getRGB). */
    record Miniatura(int ancho, int alto, int[] pixelesArgb) { }

    /** La miniatura en vivo de un canal ya al tamaño pedido, o null si no se pudo descargar o decodificar. Va a la red. */
    Miniatura miniatura(String login, int ancho, int alto);

    /** El «mando a distancia» sobre el intervalo de refresco de Twitch (api.Freno.ctrlMult("twitch_mult")). Sin red. */
    double multiplicador();
}
