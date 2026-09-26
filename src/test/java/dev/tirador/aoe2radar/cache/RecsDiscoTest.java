package dev.tirador.aoe2radar.cache;

import dev.tirador.aoe2radar.model.Match;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RecsDisco: el nombre de archivo de una partida de «Guess the ELO» y la numeración que continúa entre tandas
 * (revisión 1.3, Watchlist/Partidas F9).
 */
class RecsDiscoTest {

    @Test void guessTheElo_elArchivoLlevaElNumeroDeLaTandaNoElIdDePartida() {
        Match m = new Match();
        m.id = 431_234_568L;
        m.gte = 3;
        assertEquals(RecsDisco.RECS_DIR.resolve("Guess the ELO 3.aoe2record"), RecsDisco.destino(m));
    }

    @Test void maxGte_continuaDesdeElMayorNumeroDeTanda(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("Guess the ELO 4.aoe2record"));
        Files.createFile(dir.resolve("Guess the ELO 7.aoe2record"));
        Files.createFile(dir.resolve("Otra partida.aoe2record"));
        assertEquals(7, RecsDisco.maxGteEnDisco(dir));
    }

    @Test void maxGte_cuentaTambienLasCopiasDelSavegame(@TempDir Path dir) throws Exception {
        // «Vaciar recs» deja ./recs vacía, pero la tanda vieja sigue en el savegame del juego: no se repite su número
        Path recs = Files.createDirectories(dir.resolve("recs"));
        Path sg = Files.createDirectories(dir.resolve("savegame"));
        Files.createFile(recs.resolve("Guess the ELO 2.aoe2record"));
        Files.createFile(sg.resolve("Guess the ELO 9.aoe2record"));
        assertEquals(9, RecsDisco.maxGteEnDisco(recs, sg));
        assertEquals(2, RecsDisco.maxGteEnDisco(recs, null), "sin savegame configurado, solo ./recs");
        assertEquals(2, RecsDisco.maxGteEnDisco(recs, dir.resolve("no-existe")), "savegame que ya no existe: solo ./recs");
    }

    @Test void maxGte_ignoraLosArchivosViejosConElIdDePartida(@TempDir Path dir) throws Exception {
        // hasta la 1.2 el archivo llevaba el id de la partida: no debe disparar la numeración de las tandas nuevas
        Files.createFile(dir.resolve("Guess the ELO 431234568.aoe2record"));
        Files.createFile(dir.resolve("Guess the ELO 5.aoe2record"));
        assertEquals(5, RecsDisco.maxGteEnDisco(dir));
    }
}
