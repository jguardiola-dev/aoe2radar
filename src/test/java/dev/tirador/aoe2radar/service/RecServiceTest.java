package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecService: descarga la rec de una partida, la guarda en disco y, si toca, la copia al savegame. Sin red
 * (descarga falsa) y con disco real en un @TempDir (el nombre de archivo y el contenido importan).
 */
class RecServiceTest {

    /** Descarga falsa: cuenta a qué (gameId, profileId) se llamó y responde según lo programado. */
    static final class DescargaFalsa implements java.util.function.BiFunction<Long, Long, byte[]> {
        final List<Long> pids = new ArrayList<>();
        byte[] datos = "rec de prueba".getBytes(StandardCharsets.UTF_8);
        @Override public byte[] apply(Long gameId, Long pid) { pids.add(pid); return datos; }
    }

    /** Juego falso: apunta cada (partida, savegame) que le llegó; devuelve lo programado. */
    static final class JuegoFalso implements java.util.function.BiPredicate<Match, Path> {
        final List<Match> recibidas = new ArrayList<>();
        boolean copia = true;
        @Override public boolean test(Match m, Path sg) { recibidas.add(m); return copia; }
    }

    static Match partida(long id, long refId) {
        Match m = new Match();
        m.id = id; m.refId = refId;
        MatchPlayer p1 = new MatchPlayer(); p1.id = refId; p1.name = "Ref"; p1.team = 1;
        MatchPlayer p2 = new MatchPlayer(); p2.id = refId + 1; p2.name = "Rival"; p2.team = 2;
        m.players.add(p1); m.players.add(p2);
        return m;
    }

    static MatchPlayer jugador(long id, Boolean replay) {
        MatchPlayer p = new MatchPlayer(); p.id = id; p.replay = replay; return p;
    }

    @TempDir Path tempDir;

    DescargaFalsa descarga = new DescargaFalsa();
    JuegoFalso juego = new JuegoFalso();
    List<Long> pausas = new ArrayList<>();
    RecService service = new DescargaRecs(descarga, m -> tempDir.resolve("rec-" + m.id + ".aoe2record"),
            juego, pausas::add, 5L);
    BooleanSupplier sinCancelar = () -> false;

    @Test void recNuevaSeDescargaYSeGuarda() throws IOException {
        Match m = partida(1, 100);
        RecService.Resultado r = service.procesar(m, Set.of(), false, null, sinCancelar);
        assertEquals(RecService.Estado.DESCARGADA, r.estado());
        assertFalse(r.enJuego());
        assertNull(r.causa());
        Path archivo = tempDir.resolve("rec-1.aoe2record");
        assertTrue(Files.exists(archivo));
        assertArrayEquals(descarga.datos, Files.readAllBytes(archivo));
        assertEquals(List.of(100L), descarga.pids, "un solo candidato, el de referencia: acierta a la primera");
    }

    /** Test de caracterización: así es la 1.1 (download(), ~12036), no un contrato deseado. Si su rec ya está en
     *  disco, procesar() la vuelve a descargar y la sobrescribe; no hay comprobación de «ya en disco» (ver
     *  DEUDA: RecService, propuesta de no reintentar si ya está en disco, pendiente de decidir con Jorge). */
    @Test void siYaHabiaUnaRecEnDiscoSeVuelveADescargarYSeSobrescribe() throws IOException {
        Match m = partida(2, 100);
        Path archivo = tempDir.resolve("rec-2.aoe2record");
        Files.writeString(archivo, "ya estaba");
        RecService.Resultado r = service.procesar(m, Set.of(), false, null, sinCancelar);
        assertEquals(RecService.Estado.DESCARGADA, r.estado());
        assertFalse(descarga.pids.isEmpty(), "se vuelve a llamar a la descarga aunque ya hubiera archivo");
        assertArrayEquals(descarga.datos, Files.readAllBytes(archivo), "el archivo se sobrescribe");
    }

    @Test void falloDeDescargaNoDejaNadaAMedias() {
        descarga.datos = null;   // ningún candidato trae rec válida
        Match m = partida(3, 100);
        RecService.Resultado r = service.procesar(m, Set.of(), false, null, sinCancelar);
        assertEquals(RecService.Estado.FALLO, r.estado());
        assertNotNull(r.causa());
        assertFalse(r.enJuego());
        assertFalse(Files.exists(tempDir.resolve("rec-3.aoe2record")), "sin escritura parcial");
        assertEquals(List.of(100L, 101L), descarga.pids, "los dos candidatos de la partida, en orden");
        assertEquals(List.of(5L, 5L), pausas, "una pausa (el valor inyectado) por cada candidato fallido");
    }

    @Test void enviarAlJuegoConEnviarSiempre() {
        Match m = partida(4, 100);
        RecService.Resultado r = service.procesar(m, Set.of(), true, tempDir.resolve("savegame"), sinCancelar);
        assertEquals(RecService.Estado.DESCARGADA, r.estado());
        assertTrue(r.enJuego());
        assertEquals(List.of(m), juego.recibidas);
    }

    @Test void sinEnviarSiempreNoTocaElJuego() {
        Match m = partida(5, 100);
        RecService.Resultado r = service.procesar(m, Set.of(), false, tempDir.resolve("savegame"), sinCancelar);
        assertFalse(r.enJuego());
        assertTrue(juego.recibidas.isEmpty());
    }

    @Test void sinSavegameNoTocaElJuegoAunqueSePida() {
        Match m = partida(6, 100);
        RecService.Resultado r = service.procesar(m, Set.of(), true, null, sinCancelar);
        assertTrue(r.estado() == RecService.Estado.DESCARGADA);
        assertFalse(r.enJuego());
        assertTrue(juego.recibidas.isEmpty());
    }

    @Test void canceladaDesdeElPrincipioNoIntentaNingunCandidato() {
        Match m = partida(7, 100);
        RecService.Resultado r = service.procesar(m, Set.of(), false, null, () -> true);
        assertEquals(RecService.Estado.FALLO, r.estado());
        assertTrue(descarga.pids.isEmpty());
    }

    @Test void canceladaAMitadNoIntentaElSegundoCandidato() {
        descarga.datos = null;   // el primer candidato falla
        int[] llamadas = {0};
        BooleanSupplier cancelaTrasElPrimero = () -> llamadas[0]++ > 0;   // false la 1ª vez (antes del candidato 100), true la 2ª (antes del 101)
        Match m = partida(9, 100);   // candidatos: 100 (ref), 101 (rival)
        RecService.Resultado r = service.procesar(m, Set.of(), false, null, cancelaTrasElPrimero);
        assertEquals(RecService.Estado.FALLO, r.estado());
        assertEquals(List.of(100L), descarga.pids, "el segundo candidato (101) no se intenta: se canceló antes");
    }

    @Test void siFallaCopiarAlJuegoNoEsFallo() {
        juego.copia = false;
        Match m = partida(8, 100);
        RecService.Resultado r = service.procesar(m, Set.of(), true, tempDir.resolve("savegame"), sinCancelar);
        assertEquals(RecService.Estado.DESCARGADA, r.estado(), "la rec quedó en disco: no es un fallo");
        assertFalse(r.enJuego());
    }

    // ----- DescargaRecs.candidatos: el orden de los POV a probar --------------------------------------------

    /** 10 jugadores (TG de 8 con dos suplentes), con replay TRUE, FALSE y null, y seguidos variados: fija el
     *  orden exacto (referencia, seguidos con rec, con rec, seguidos, resto) y el límite de 8. */
    @Test void candidatosSigueElOrdenReferenciaSeguidosConRecConRecSeguidosResto() {
        Match m = new Match();
        m.refId = 1;
        m.players.add(jugador(1, Boolean.TRUE));    // referencia, con rec: grupo 1
        m.players.add(jugador(2, Boolean.TRUE));    // seguido con rec: grupo 2
        m.players.add(jugador(3, Boolean.TRUE));    // con rec, no seguido: grupo 3
        m.players.add(jugador(4, Boolean.FALSE));   // seguido, sin rec: grupo 4
        m.players.add(jugador(5, null));            // seguido, rec desconocida: grupo 4
        m.players.add(jugador(6, Boolean.TRUE));    // con rec, no seguido: grupo 3
        m.players.add(jugador(7, Boolean.FALSE));   // resto: grupo 5
        m.players.add(jugador(8, null));            // resto: grupo 5
        m.players.add(jugador(9, Boolean.TRUE));    // con rec, no seguido: grupo 3
        m.players.add(jugador(10, null));           // resto: grupo 5 (queda fuera del límite de 8)
        Set<Long> seguidos = Set.of(2L, 4L, 5L);

        List<Long> c = DescargaRecs.candidatos(m, seguidos);

        assertEquals(List.of(1L, 2L, 3L, 6L, 9L, 4L, 5L, 7L), c,
                "referencia; seguidos con rec; con rec; seguidos; resto — hasta 8, en el orden de la lista de jugadores");
        assertEquals(8, c.size(), "límite de 8 (TGs): el jugador 10 (resto) se queda fuera");
    }

    /** Sin referencia con rec ni seguidos ni nadie con rec confirmada: candidatos() cae en el «resto», en el
     *  mismo orden que la lista de jugadores, y sigue recortando a 8. */
    @Test void sinGruposPreferentesElOrdenEsElDeLaListaYSigueElLimiteDe8() {
        Match m = new Match();
        m.refId = 0;   // no coincide con ningún jugador
        for (long id = 1; id <= 10; id++) m.players.add(jugador(id, null));

        List<Long> c = DescargaRecs.candidatos(m, Set.of());

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), c);
    }
}
