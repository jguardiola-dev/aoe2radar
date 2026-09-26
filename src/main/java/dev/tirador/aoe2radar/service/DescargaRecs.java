package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongConsumer;

import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * RecService sobre aoe.ms y el disco de la app (recs/): la descarga, la ruta de destino y la copia al savegame
 * entran por el constructor (en la app: api.Recs::descargarRec, cache.RecsDisco::destino,
 * service.Juego::copiarASavegame); en los tests, dobles sin red ni juego real (ver RecServiceTest).
 */
public final class DescargaRecs implements RecService {
    private final BiFunction<Long, Long, byte[]> descargador;
    private final Function<Match, Path> destino;
    private final BiPredicate<Match, Path> copiarAlJuego;
    private final LongConsumer pausa;
    private final long pausaMs;

    public DescargaRecs(BiFunction<Long, Long, byte[]> descargador, Function<Match, Path> destino,
                        BiPredicate<Match, Path> copiarAlJuego, LongConsumer pausa, long pausaMs) {
        this.descargador = descargador; this.destino = destino; this.copiarAlJuego = copiarAlJuego;
        this.pausa = pausa; this.pausaMs = pausaMs;
    }

    @Override public Resultado procesar(Match m, Set<Long> trackedIds, boolean enviarAlJuego, Path savegame, BooleanSupplier cancelado) {
        avisarSiUi("RecService.procesar");
        Path archivo = destino.apply(m);
        boolean guardada;
        String ultimaCausa;
        if (RecService.recSana(archivo)) {
            // Decisión de Jorge (DEUDA 94/95): ya hay una rec sana en disco, no se vuelve a descargar.
            guardada = true;
            ultimaCausa = null;
        } else {
            ultimaCausa = "sin candidatos con rec";
            guardada = false;
            for (long pid : candidatos(m, trackedIds)) {
                if (cancelado.getAsBoolean()) { ultimaCausa = "cancelada"; break; }
                byte[] datos = descargador.apply(m.id, pid);
                if (datos != null) {
                    try {
                        Files.write(archivo, datos);
                        guardada = true;
                        break;
                    } catch (IOException ex) {
                        ultimaCausa = causa(ex);
                        log("no se pudo escribir " + archivo + ": " + ultimaCausa);
                    }
                } else {
                    ultimaCausa = "sin rec válida";
                }
                pausa.accept(pausaMs);
            }
        }
        Estado estado = guardada ? Estado.DESCARGADA : Estado.FALLO;
        String causaFallo = guardada ? null : ultimaCausa;
        boolean enJuego = estado != Estado.FALLO && enviarAlJuego && savegame != null && copiarAlJuego.test(m, savegame);
        return new Resultado(estado, enJuego, causaFallo);
    }

    /** POVs a intentar, por orden: el de referencia, con rec confirmada
     *  (seguidos primero), seguidos, resto. */
    static List<Long> candidatos(Match m, Set<Long> tracked) {
        LinkedHashSet<Long> c = new LinkedHashSet<>();
        for (MatchPlayer p : m.players) if (p.id == m.refId && !Boolean.FALSE.equals(p.replay)) c.add(p.id);
        for (MatchPlayer p : m.players) if (Boolean.TRUE.equals(p.replay) && tracked.contains(p.id)) c.add(p.id);
        for (MatchPlayer p : m.players) if (Boolean.TRUE.equals(p.replay)) c.add(p.id);
        for (MatchPlayer p : m.players) if (tracked.contains(p.id)) c.add(p.id);
        for (MatchPlayer p : m.players) c.add(p.id);
        return new ArrayList<>(c).subList(0, Math.min(c.size(), 8));   // TGs de 8: todas las perspectivas caben
    }
}
