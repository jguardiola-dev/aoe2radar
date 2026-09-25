package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Match;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * La rec de una partida: descargarla, guardarla en disco y, si toca, copiarla al savegame del juego. El
 * SwingWorker de la app hace el bucle sobre las partidas, pinta el progreso y decide los textos; este servicio
 * solo dice qué pasó con UNA.
 * <p>Hilos: descargar y escribir en disco van a la red y a la E/S, nunca en el EDT (ver util.Hilos.avisarSiUi).
 */
public interface RecService {

    /** Qué pasó con la rec de la partida. */
    enum Estado { DESCARGADA, FALLO }

    /**
     * estado: ver Estado. enJuego: si en ESTA llamada la rec quedó copiada al savegame (solo puede darse si
     * estado no es FALLO). causa: el motivo del fallo (null salvo en FALLO).
     */
    record Resultado(Estado estado, boolean enJuego, String causa) {}

    /**
     * Procesa una partida: prueba sus candidatos a POV (el de referencia, seguidos con rec, seguidos, resto — ver
     * DescargaRecs.candidatos) hasta guardar una rec válida (DESCARGADA) o agotarlos (FALLO, con la causa del
     * último intento). Igual que la 1.1: si la rec ya estaba en disco, se vuelve a descargar y se sobrescribe (no
     * hay comprobación de «ya en disco»; ver DEUDA sobre la mejora propuesta y no aplicada).
     * <p>Si enviarAlJuego es true y savegame no es null, copia la rec recién descargada al savegame y lo marca en
     * el resultado; si copiar falla, enJuego queda false (sin que eso sea un FALLO: la rec sigue en disco).
     * <p>cancelado se mira entre un candidato y el siguiente: si ya está a true no se prueban más y el resultado
     * es FALLO.
     */
    Resultado procesar(Match m, Set<Long> trackedIds, boolean enviarAlJuego, Path savegame, BooleanSupplier cancelado);
}
