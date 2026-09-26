package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.Recs;
import dev.tirador.aoe2radar.model.Match;

import java.io.IOException;
import java.nio.file.Files;
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
     * Procesa una partida: si en {@code destino.apply(m)} ya hay una rec sana (ver {@link #recSana}), la
     * reutiliza sin tocar la red (decisión de Jorge, DEUDA 94/95: «Enviar al juego» no debe gastar una llamada
     * si el archivo ya sirve). Si no, prueba sus candidatos a POV (el de referencia, seguidos con rec, seguidos,
     * resto — ver DescargaRecs.candidatos) hasta guardar una rec válida (DESCARGADA) o agotarlos (FALLO, con la
     * causa del último intento), igual que la 1.1.
     * <p>Si enviarAlJuego es true y savegame no es null, copia la rec (la reutilizada o la recién descargada) al
     * savegame y lo marca en el resultado; si copiar falla, enJuego queda false (sin que eso sea un FALLO: la rec
     * sigue en disco).
     * <p>cancelado se mira entre un candidato y el siguiente (no se llega a mirar si la rec ya era sana): si ya
     * está a true no se prueban más y el resultado es FALLO.
     */
    Resultado procesar(Match m, Set<Long> trackedIds, boolean enviarAlJuego, Path savegame, BooleanSupplier cancelado);

    /**
     * Decisión de Jorge (DEUDA 94/95): ¿la rec que ya está en {@code archivo} parece sana, como para
     * reutilizarla en vez de volver a descargarla? Se lee solo su cabecera (hasta 5000 B, sin cargar el archivo
     * entero) y se aplica la MISMA regla que ya usa {@code api.Recs} para aceptar una rec recién descargada
     * ({@link Recs#esRecValida}): al menos 5000 B y que no empiece por «&lt;» o «{» (páginas de error HTML/JSON).
     * Si el archivo no existe, pesa menos o está vacío, no parece sana: se descarga como hasta ahora.
     * <p>Nota: la triaje original hablaba de «se abre como zip», pero {@code api.Recs.normalizar} ya desenvuelve
     * el zip que sirve aoe.ms antes de escribir el archivo (ver Recs.java, extraerZip): la rec en disco casi
     * nunca es un zip, así que esa comprobación no detectaría ninguna rec sana real. Se usa en su lugar la regla
     * que el propio código ya usa para decidir «esto parece una rec» (Recs.esRecValida), sobre la MISMA
     * cabecera. La única llamadora (PartidasView) puede usar directamente esta regla, en vez de repetir su
     * propio Files.exists.
     */
    static boolean recSana(Path archivo) {
        try (var in = Files.newInputStream(archivo)) {
            return Recs.esRecValida(in.readNBytes(5000));
        } catch (IOException ex) {
            return false;
        }
    }
}
