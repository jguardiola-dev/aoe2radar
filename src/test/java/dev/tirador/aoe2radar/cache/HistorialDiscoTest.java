package dev.tirador.aoe2radar.cache;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * General F5: el historial de perfil (sfrdata/perfiles/&lt;pid&gt;.json) se guarda de forma atómica. PERFILES_DIR es
 * relativo a la carpeta de trabajo (target/harness en los tests): se usa un pid que no existe y se borra al acabar.
 */
class HistorialDiscoTest {

    static final long PID = 987_654_321_012L;
    final Path archivo = HistorialDisco.PERFILES_DIR.resolve(PID + ".json");
    final Path espejo = HistorialDisco.PERFILES_DIR.resolve(PID + ".espejo");

    @AfterEach void limpiar() throws IOException {
        Files.deleteIfExists(archivo);
        Files.deleteIfExists(espejo);
    }

    static Actividad actividad(String nombre) {
        Match m = new Match();
        m.id = 42; m.started = Instant.now().minusSeconds(3600); m.finished = Instant.now(); m.mode = "RM"; m.map = "Arabia";
        MatchPlayer p = new MatchPlayer();
        p.id = PID; p.name = nombre; p.civ = "Aztecs"; p.team = 1; p.won = true; p.rating = 1500; p.ratingDiff = 12;
        m.players.add(p);
        return new Actividad(PID, nombre, List.of(m), true, 1, 1234L);
    }

    @Test void guardaDeFormaAtomicaYSeVuelveALeer() throws IOException {
        Files.createDirectories(HistorialDisco.PERFILES_DIR);
        Files.writeString(archivo, "viejo");
        Files.createLink(espejo, archivo);

        HistorialDisco.guardarActividad(actividad("Ána"));

        assertEquals("viejo", Files.readString(espejo), "no se escribió encima del archivo viejo: se sustituyó entero");
        Actividad leida = HistorialDisco.cargarActividad(PID);
        assertNotNull(leida);
        assertEquals("Ána", leida.nombre());
        assertEquals(1, leida.partidas().size());
        assertEquals(1500, leida.partidas().get(0).players.get(0).rating);
    }
}
