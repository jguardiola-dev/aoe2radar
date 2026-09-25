package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ListaSeguidos: persistencia de players.txt, grupos y altas/bajas/movimientos de la watchlist. Sin red y sin
 * Swing; la config va contra un mapa en memoria (como haría util.Config, pero sin tocar disco de verdad) y
 * players.txt contra un @TempDir.
 */
class ListaSeguidosTest {

    static final String GENERAL = "General";

    @TempDir Path tempDir;
    Path playersFile;
    Map<String, String> cfg;
    ListaSeguidos svc;

    @BeforeEach
    void setUp() {
        playersFile = tempDir.resolve("players.txt");
        cfg = new HashMap<>();
        svc = new ListaSeguidos(playersFile, GENERAL, (k, def) -> cfg.getOrDefault(k, def), cfg::put);
    }

    // ----- Persistencia --------------------------------------------------------------

    @Test void cargar_leeLineasConVinculoYSinEl() throws IOException {
        Files.write(playersFile, List.of(
                "111;Ana;Amigos;555",
                "222;Bob;Amigos",
                "333;Cid"
        ), StandardCharsets.UTF_8);
        List<Player> destino = new ArrayList<>();

        String error = svc.cargar(destino);

        assertNull(error);
        assertEquals(3, destino.size());
        assertEquals(new Player(111L, "Ana", "Amigos", 555L), destino.get(0));
        assertEquals(new Player(222L, "Bob", "Amigos", 0L), destino.get(1));
        assertEquals(new Player(333L, "Cid", GENERAL, 0L), destino.get(2));
    }

    @Test void cargar_omiteLineasCortasYVacias() throws IOException {
        Files.write(playersFile, List.of(
                "444",
                "",
                "555;Dan;"
        ), StandardCharsets.UTF_8);
        List<Player> destino = new ArrayList<>();

        String error = svc.cargar(destino);

        assertNull(error);
        assertEquals(1, destino.size());
        assertEquals(new Player(555L, "Dan", GENERAL, 0L), destino.get(0));
    }

    @Test void cargar_primerArranque_sinArchivoYSinGrupos_siembraAmigos() {
        List<Player> destino = new ArrayList<>();

        String error = svc.cargar(destino);

        assertNull(error);
        assertTrue(destino.isEmpty());
        assertEquals("Amigos", cfg.get("grupos"));
    }

    @Test void cargar_sinArchivo_conGruposYaConfigurados_noResiembra() {
        cfg.put("grupos", "Otro");
        List<Player> destino = new ArrayList<>();

        svc.cargar(destino);

        assertEquals("Otro", cfg.get("grupos"));
    }

    @Test void cargar_limpiaProsVacio() throws IOException {
        Files.write(playersFile, List.of("1;Ana;Amigos"), StandardCharsets.UTF_8);
        cfg.put("grupos", "Pros,Amigos");
        List<Player> destino = new ArrayList<>();

        svc.cargar(destino);

        assertEquals("Amigos", cfg.get("grupos"));
    }

    @Test void cargar_conservaProsConMiembros() throws IOException {
        Files.write(playersFile, List.of("10;Pete;Pros"), StandardCharsets.UTF_8);
        cfg.put("grupos", "Pros");
        List<Player> destino = new ArrayList<>();

        svc.cargar(destino);

        assertEquals("Pros", cfg.get("grupos"));
        assertEquals(1, destino.size());
        assertEquals(new Player(10L, "Pete", "Pros", 0L), destino.get(0));
    }

    @Test void guardar_escribeIdNombreGrupoYVinculoSoloSiNoEsCero() throws IOException {
        List<Player> jugadores = List.of(
                new Player(1L, "Ana", GENERAL, 0L),
                new Player(2L, "Bob", "Amigos", 5L)
        );

        String error = svc.guardar(jugadores);

        assertNull(error);
        List<String> lineas = Files.readAllLines(playersFile, StandardCharsets.UTF_8);
        assertEquals(List.of("1;Ana;General", "2;Bob;Amigos;5"), lineas);
    }

    // ----- Grupos --------------------------------------------------------------

    @Test void registrarGrupo_loAnadeALaConfig() {
        cfg.put("grupos", "Amigos");

        svc.registrarGrupo("Torneo");

        assertEquals(Set.of("Amigos", "Torneo"), svc.gruposConfig());
    }

    @Test void renombrarGrupo_cambiaJugadoresYConfig() {
        cfg.put("grupos", "Amigos");
        cfg.put("grupo_activo", "Amigos");
        List<Player> jugadores = new ArrayList<>(List.of(
                new Player(1L, "Ana", "Amigos", 0L),
                new Player(2L, "Bob", GENERAL, 0L)
        ));

        svc.renombrarGrupo(jugadores, "Amigos", "Colegas");

        assertEquals(new Player(1L, "Ana", "Colegas", 0L), jugadores.get(0));
        assertEquals(new Player(2L, "Bob", GENERAL, 0L), jugadores.get(1));
        assertEquals(Set.of("Colegas"), svc.gruposConfig());
        assertEquals("Colegas", cfg.get("grupo_activo"));
    }

    @Test void borrarGrupo_susJugadoresPasanAGeneral() {
        cfg.put("grupos", "Amigos,Otro");
        cfg.put("grupo_activo", "Amigos");
        List<Player> jugadores = new ArrayList<>(List.of(
                new Player(1L, "Ana", "Amigos", 0L),
                new Player(2L, "Bob", "Otro", 0L)
        ));

        svc.borrarGrupo(jugadores, "Amigos");

        assertEquals(new Player(1L, "Ana", GENERAL, 0L), jugadores.get(0));
        assertEquals(new Player(2L, "Bob", "Otro", 0L), jugadores.get(1));
        assertEquals(Set.of("Otro"), svc.gruposConfig());
        assertEquals("Todos", cfg.get("grupo_activo"));
    }

    // ----- Altas, bajas y movimientos --------------------------------------------------------------

    @Test void ficharDesdeTop_anadeSiNoEstaba() {
        List<Player> jugadores = new ArrayList<>();

        boolean anadido = svc.ficharDesdeTop(jugadores, new Player(1L, "Ana", "x"), "Amigos");
        boolean repetido = svc.ficharDesdeTop(jugadores, new Player(1L, "Ana otra vez", "y"), "Otro");

        assertTrue(anadido);
        assertFalse(repetido);
        assertEquals(1, jugadores.size());
        assertEquals(new Player(1L, "Ana", "Amigos", 0L), jugadores.get(0));
    }

    @Test void ficharVarios_soloAnadeLosQueFaltan() {
        List<Player> jugadores = new ArrayList<>(List.of(new Player(1L, "Ana", GENERAL, 0L)));
        List<Player> candidatos = List.of(
                new Player(1L, "Ana", "x"),
                new Player(2L, "Bob", "x")
        );

        List<Player> nuevos = svc.ficharVarios(jugadores, candidatos, "Amigos");

        assertEquals(List.of(new Player(2L, "Bob", "x")), nuevos);
        assertEquals(2, jugadores.size());
        assertEquals(new Player(2L, "Bob", "Amigos", 0L), jugadores.get(1));
    }

    @Test void quitar_loBorraDeLaLista() {
        List<Player> jugadores = new ArrayList<>(List.of(
                new Player(1L, "Ana", GENERAL, 0L),
                new Player(2L, "Bob", GENERAL, 0L)
        ));

        svc.quitar(jugadores, 1L);

        assertEquals(1, jugadores.size());
        assertEquals(new Player(2L, "Bob", GENERAL, 0L), jugadores.get(0));
    }

    @Test void moverJugador_cambiaSuGrupoYPierdeElVinculo() {
        List<Player> jugadores = new ArrayList<>(List.of(new Player(1L, "Ana", GENERAL, 99L)));

        svc.moverJugador(jugadores, new Player(1L, "Ana", GENERAL, 99L), "Amigos");

        // Igual que en la 1.1: a diferencia de moverVarios, moverJugador no conserva el vínculo (ver DEUDA).
        assertEquals(new Player(1L, "Ana", "Amigos", 0L), jugadores.get(0));
    }

    @Test void moverVarios_cambiaSuGrupoYConservaElVinculo() {
        List<Player> jugadores = new ArrayList<>(List.of(
                new Player(1L, "Ana", GENERAL, 7L),
                new Player(2L, "Bob", GENERAL, 0L),
                new Player(3L, "Cid", GENERAL, 0L)
        ));
        List<Player> aMover = List.of(new Player(1L, "x", "y"), new Player(2L, "x", "y"));

        int n = svc.moverVarios(jugadores, aMover, "Amigos");

        assertEquals(2, n);
        assertEquals(new Player(1L, "Ana", "Amigos", 7L), jugadores.get(0));
        assertEquals(new Player(2L, "Bob", "Amigos", 0L), jugadores.get(1));
        assertEquals(new Player(3L, "Cid", GENERAL, 0L), jugadores.get(2));
    }

    // ----- Consulta --------------------------------------------------------------

    @Test void contiene_yGrupoDeJugador() {
        List<Player> jugadores = List.of(new Player(1L, "Ana", "Amigos", 0L));

        assertTrue(svc.contiene(jugadores, 1L));
        assertFalse(svc.contiene(jugadores, 2L));
        assertEquals("Amigos", svc.grupoDeJugador(jugadores, 1L));
        assertNull(svc.grupoDeJugador(jugadores, 2L));
    }
}
