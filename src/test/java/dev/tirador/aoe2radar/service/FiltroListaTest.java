package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.util.RelojFalso;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Qué fila de la Watchlist se ve y en qué orden (service.FiltroLista): subconjunto por grupo,
 * filtro «solo vivos», los tres órdenes (nombre, ELO, forma) y la agrupación por familia.
 * Caracterización: fija el comportamiento actual de aplicarFiltroGrupo, sin red ni Swing.
 * EstadoVivo real con RelojFalso (no el reloj del sistema): «jugando» no depende de caducidades aquí.
 */
class FiltroListaTest {

    static EstadoVivo vivo() { return new EstadoVivo(new RelojFalso()); }

    /** Marca a pid como jugando ahora mismo (matchId cualquiera, no usado en las comparaciones). */
    static void marcarJugando(EstadoVivo v, long pid) { v.marcarJugando(pid, pid * 1000); }

    static List<Long> ids(List<Player> ps) { return ps.stream().map(Player::id).toList(); }

    // ----- por grupo -----

    @Test void filtraPorGrupoActivo() {
        FiltroLista fl = new FiltroLista(vivo());
        List<Player> todos = List.of(
                new Player(1, "Ana", "A"),
                new Player(2, "Beto", "B"),
                new Player(3, "Cleo", "A"));
        FiltroLista.Resultado res = fl.filtrar(todos, List.of(), false, "A", false,
                false, false, false, Map.of(), Map.of(), Set.of());
        assertEquals(List.of(1L, 3L), ids(res.filas()));
    }

    @Test void grupoNuloEsTodos() {
        FiltroLista fl = new FiltroLista(vivo());
        List<Player> todos = List.of(
                new Player(1, "Ana", "A"),
                new Player(2, "Beto", "B"));
        FiltroLista.Resultado res = fl.filtrar(todos, List.of(), false, null, false,
                false, false, false, Map.of(), Map.of(), Set.of());
        assertEquals(List.of(1L, 2L), ids(res.filas()));
    }

    @Test void modoTopUsaElTopLadderIgnorandoElGrupo() {
        FiltroLista fl = new FiltroLista(vivo());
        List<Player> todos = List.of(new Player(1, "Ana", "A"));
        List<Player> top = List.of(new Player(9, "Top1", "TOP_LADDER"), new Player(8, "Top2", "TOP_LADDER"));
        FiltroLista.Resultado res = fl.filtrar(todos, top, true, "A", false,
                false, false, false, Map.of(), Map.of(), Set.of());
        assertEquals(List.of(9L, 8L), ids(res.filas()));   // orden de llegada: sin orden aplicado (nombre, sin empates raros)
    }

    // ----- solo vivos -----

    @Test void soloVivosDejaSoloAQuienJuegaOAFamiliaViva() {
        EstadoVivo v = vivo();
        marcarJugando(v, 1);   // Ana juega
        marcarJugando(v, 4);   // Dara juega (vinculada a Cleo, vinculo 100); Cleo no juega
        FiltroLista fl = new FiltroLista(v);
        List<Player> lista = List.of(
                new Player(1, "Ana", "G"),
                new Player(2, "Beto", "G"),         // no juega, sin familia: fuera
                new Player(3, "Cleo", "G", 100),    // no juega, pero su familia (Dara) sí: se queda
                new Player(4, "Dara", "G", 100));
        FiltroLista.Resultado res = fl.filtrar(lista, List.of(), false, "G", true,
                false, false, false, Map.of(), Map.of(), Set.of());
        // la familia sobrevive al filtro (Dara juega) pero, sin expandir, cuelga detrás de su cabeza (Cleo,
        // la primera por nombre al empatar sin ELO): Dara queda oculta aunque sea ella quien juega.
        assertEquals(List.of(1L, 3L), ids(res.filas()));
        assertEquals(Boolean.TRUE, res.vivoFamilia().get(3L));
    }

    // ----- orden -----

    @Test void ordenPorNombreConEmpateEsEstable() {
        FiltroLista fl = new FiltroLista(vivo());
        // dos "ana" (mismo nombre en minúsculas): el orden de empate debe conservar el orden de llegada (sort estable)
        List<Player> todos = List.of(
                new Player(2, "ana", "G"),
                new Player(1, "Ana", "G"),
                new Player(3, "Beto", "G"));
        FiltroLista.Resultado res = fl.filtrar(todos, List.of(), false, "G", false,
                false, false, false, Map.of(), Map.of(), Set.of());
        assertEquals(List.of(2L, 1L, 3L), ids(res.filas()));
    }

    @Test void ordenPorEloDescendenteConNullsAlFinalYEmpatePorNombre() {
        FiltroLista fl = new FiltroLista(vivo());
        List<Player> todos = List.of(
                new Player(1, "Zeta", "G"),
                new Player(2, "Beto", "G"),
                new Player(3, "Ana", "G"),   // mismo elo que Beto -> empate por nombre
                new Player(4, "SinElo", "G"));
        Map<Long, Integer> elo = new HashMap<>();
        elo.put(1L, 1000);
        elo.put(2L, 1500);
        elo.put(3L, 1500);
        // 4 sin entrada en elo: null, va al final
        FiltroLista.Resultado res = fl.filtrar(todos, List.of(), false, "G", false,
                false, false, true, Map.of(), elo, Set.of());
        assertEquals(List.of(3L, 2L, 1L, 4L), ids(res.filas()));
    }

    @Test void ordenPorFormaDescendenteSinPartidasVaAlFinal() {
        FiltroLista fl = new FiltroLista(vivo());
        List<Player> todos = List.of(
                new Player(1, "Ana", "G"),
                new Player(2, "Beto", "G"),
                new Player(3, "Cleo", "G"));   // sin forma
        Map<Long, Forma> forma = new HashMap<>();
        forma.put(1L, new Forma(-50, 1, 2, 1, false, 3));   // baja
        forma.put(2L, new Forma(80, 3, 0, 3, true, 3));     // sube
        FiltroLista.Resultado res = fl.filtrar(todos, List.of(), false, "G", false,
                true, false, false, forma, Map.of(), Set.of());
        assertEquals(List.of(2L, 1L, 3L), ids(res.filas()));   // desc: el que más sube primero, sin forma al final
    }

    @Test void ordenPorFormaAscendenteLosQueMasBajanPrimero() {
        FiltroLista fl = new FiltroLista(vivo());
        List<Player> todos = List.of(
                new Player(1, "Ana", "G"),
                new Player(2, "Beto", "G"));
        Map<Long, Forma> forma = new HashMap<>();
        forma.put(1L, new Forma(-50, 1, 2, 1, false, 3));
        forma.put(2L, new Forma(80, 3, 0, 3, true, 3));
        FiltroLista.Resultado res = fl.filtrar(todos, List.of(), false, "G", false,
                true, true, false, forma, Map.of(), Set.of());
        assertEquals(List.of(1L, 2L), ids(res.filas()));   // asc: el que más baja, arriba
    }

    // ----- familias -----

    @Test void familiaSinExpandirSoloMuestraLaCabezaMarcadaP() {
        FiltroLista fl = new FiltroLista(vivo());
        Map<Long, Integer> elo = new HashMap<>();
        elo.put(10L, 1200);
        elo.put(11L, 1800);   // 11 tiene más elo: es la cabeza
        List<Player> todos = List.of(
                new Player(10, "Hija", "G", 500),
                new Player(11, "Cabeza", "G", 500));
        FiltroLista.Resultado res = fl.filtrar(todos, List.of(), false, "G", false,
                false, false, false, Map.of(), elo, Set.of());
        assertEquals(List.of(11L), ids(res.filas()));   // la hija no se ve: familia colapsada
        assertEquals(Character.valueOf('P'), res.marcaFila().get(11L));
        assertNull(res.marcaFila().get(10L));
    }

    @Test void familiaExpandidaMuestraCabezaYHijaMarcadasEYH() {
        FiltroLista fl = new FiltroLista(vivo());
        Map<Long, Integer> elo = new HashMap<>();
        elo.put(10L, 1200);
        elo.put(11L, 1800);
        List<Player> todos = List.of(
                new Player(10, "Hija", "G", 500),
                new Player(11, "Cabeza", "G", 500));
        FiltroLista.Resultado res = fl.filtrar(todos, List.of(), false, "G", false,
                false, false, false, Map.of(), elo, Set.of(500L));
        assertEquals(List.of(11L, 10L), ids(res.filas()));   // cabeza y, justo detrás, la hija
        assertEquals(Character.valueOf('E'), res.marcaFila().get(11L));
        assertEquals(Character.valueOf('H'), res.marcaFila().get(10L));
    }

    @Test void familiaConUnMiembroVivoQuedaMarcadaComoViva() {
        EstadoVivo v = vivo();
        marcarJugando(v, 10);   // la hija juega; la cabeza no
        FiltroLista fl = new FiltroLista(v);
        Map<Long, Integer> elo = new HashMap<>();
        elo.put(10L, 1200);
        elo.put(11L, 1800);
        List<Player> todos = List.of(
                new Player(10, "Hija", "G", 500),
                new Player(11, "Cabeza", "G", 500));
        FiltroLista.Resultado res = fl.filtrar(todos, List.of(), false, "G", false,
                false, false, false, Map.of(), elo, Set.of());
        assertEquals(Boolean.TRUE, res.vivoFamilia().get(11L));   // la cabeza hereda «familia viva»
    }

    // ----- vacío -----

    @Test void listaVaciaDaResultadoVacio() {
        FiltroLista fl = new FiltroLista(vivo());
        FiltroLista.Resultado res = fl.filtrar(List.of(), List.of(), false, null, false,
                false, false, false, Map.of(), Map.of(), Set.of());
        assertTrue(res.filas().isEmpty());
        assertTrue(res.marcaFila().isEmpty());
        assertTrue(res.vivoFamilia().isEmpty());
    }
}
