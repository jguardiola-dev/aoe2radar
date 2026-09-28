package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.Matchup;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.service.StatsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.util.Config.CONFIG_FILE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Civ Stats con componentes Swing reales dentro de invokeAndWait, nunca visibles (como RatingsViewTest): no hace
 * falta pantalla. Un StatsService de mentira con tres civs y dos mapas, sin red.
 */
class CivStatsViewTest {

    /** Tres civs en cualquier mapa/tramo; dos mapas con partidas de sobra. */
    static final class StatsFalso implements StatsService {
        final VentanaStats v;
        StatsFalso() { this(List.of()); }
        StatsFalso(List<Matchup> matchups) {
            v = new VentanaStats("30", "2026-08-25", "2026-09-23", 30, "", List.of("0-800", "800-1000"),
                Map.of("rm_1v1", Map.of("partidas", 3000)), Map.of("rm_arabia", "Arabia", "rm_arena", "Arena"),
                Map.of("rm_1v1", Map.of("rm_arabia", 2000, "rm_arena", 1000)), List.of(), matchups);
        }
        @Override public String asegurar(String ventana, boolean conTendencias) { return null; }
        @Override public VentanaStats ventana(String clave) { return v; }
        @Override public boolean tieneVentana(String clave) { return true; }
        @Override public String[] modos() { return new String[]{ "rm_1v1", "rm_2v2" }; }
        @Override public String[] clavesVentanas() { return new String[]{ "30" }; }
        @Override public Tendencias tendencias() { return null; }
        @Override public boolean tramoEnRango(String tramo, List<String> tramos, String rango) { return true; }
        @Override public double[] wilson(int w, int n) { return new double[]{ 0.4, 0.6 }; }
        @Override public Map<String, CivAgg> agregarCivs(VentanaStats v, String modo, String mapa, String tramo) {
            Map<String, CivAgg> m = new LinkedHashMap<>();
            m.put("aztecs", new CivAgg("aztecs", 500, 260, 500L * 1500));
            m.put("franks", new CivAgg("franks", 600, 290, 600L * 1500));
            m.put("mongols", new CivAgg("mongols", 400, 210, 400L * 1500));
            return m;
        }
        @Override public Map<String, Integer> partidasPorMapa(VentanaStats v, String modo, String tramo) { return Map.of("rm_arabia", 2000, "rm_arena", 1000); }
        @Override public Map<String, CivAgg> civPorMapa(VentanaStats v, String modo, String tramo, String civ) { return Map.of(); }
        @Override public String duracionMedia(long segundos, int n) { return "-"; }
    }

    static final class NavegacionFalsa implements Navegacion {
        @Override public void abrirTechTree(String civ) { }
        @Override public void abrirCivStats() { }
        @Override public void abrirLadder() { }
        @Override public void abrirPerfil(long pid, String nombre) { }
        @Override public void abrirPerfilEnPestana(long pid, String nombre) { }
        @Override public void abrirAhora() { }
    }

    @BeforeEach @AfterEach
    void limpiarConfig() throws Exception { Files.deleteIfExists(CONFIG_FILE); }

    private static CivStatsView vista(FiltroStats filtro) { return vista(filtro, new StatsFalso()); }

    private static CivStatsView vista(FiltroStats filtro, StatsFalso stats) {
        return new CivStatsView(stats, filtro, new Listas(null, b -> { }), new NavegacionFalsa(), Tareas.EN_LINEA, null, () -> { });
    }

    /** D2 (1.3): con «matchups_mapa» (sfr-data 1.5.4), la matriz de un mapa usa sus filas y el título no avisa; un
     *  mapa sin filas propias (arena aquí) vuelve al agregado de todos los mapas y el título lo dice. */
    @Test void laMatrizFiltraPorMapaSiHayFilasDeEseMapa() throws Exception {
        FiltroStats filtro = new FiltroStats("rm_1v1", "30", "*", "*");
        StatsFalso stats = new StatsFalso(List.of(
                new Matchup("rm_1v1", "*", "0-800", "aztecs", "franks", 100, 40),
                new Matchup("rm_1v1", "rm_arabia", "0-800", "aztecs", "franks", 30, 21)));
        String[] titulo = new String[3];
        int[][] celda = new int[3][];
        SwingUtilities.invokeAndWait(() -> {
            CivStatsView cs = vista(filtro, stats);
            cs.filtrosCambiados(false);
            titulo[0] = cs.stTituloMatriz.getText(); celda[0] = cs.stMatriz.celdas.get("aztecs|franks");
            cs.stMapaCombo.setSelectedIndex(1);   // Arabia: tiene filas propias
            titulo[1] = cs.stTituloMatriz.getText(); celda[1] = cs.stMatriz.celdas.get("aztecs|franks");
            cs.stMapaCombo.setSelectedIndex(2);   // Arena: sin filas, agregado + aviso
            titulo[2] = cs.stTituloMatriz.getText(); celda[2] = cs.stMatriz.celdas.get("aztecs|franks");
        });
        assertEquals("rm_arena", filtro.mapa());
        assertEquals("Matchups (civ de la fila contra civ de la columna)  ⓘ", titulo[0]);
        assertArrayEquals(new int[]{ 100, 40 }, celda[0], "sin mapa: el agregado");
        assertEquals("Matchups (civ de la fila contra civ de la columna)  ⓘ", titulo[1]);
        assertArrayEquals(new int[]{ 30, 21 }, celda[1], "Arabia: solo sus filas");
        assertEquals("Matchups (civ de la fila contra civ de la columna) · todos los mapas  ⓘ", titulo[2]);
        assertArrayEquals(new int[]{ 100, 40 }, celda[2], "Arena sin filas: el agregado");
    }

    /** D2 (1.3): datos sin matchups por mapa (sfr-data anterior a 1.5.4); con un mapa elegido, el título de la matriz
     *  dice que es de todos los mapas (antes la tabla era de Arabia y la matriz de todos, sin avisar). Sin mapa, el
     *  título de siempre. */
    @Test void conUnMapaElegidoLaMatrizAvisaDeQueEsDeTodosLosMapas() throws Exception {
        FiltroStats filtro = new FiltroStats("rm_1v1", "30", "*", "*");
        String[] titulo = new String[2];
        SwingUtilities.invokeAndWait(() -> {
            CivStatsView cs = vista(filtro);
            cs.filtrosCambiados(false);
            titulo[0] = cs.stTituloMatriz.getText();
            cs.stMapaCombo.setSelectedIndex(1);
            titulo[1] = cs.stTituloMatriz.getText();
        });
        assertEquals("Matchups (civ de la fila contra civ de la columna)  ⓘ", titulo[0]);
        assertEquals("Matchups (civ de la fila contra civ de la columna) · todos los mapas  ⓘ", titulo[1]);
    }

    /** B1 (revisión de F3): cambiar de modo borra la civ elegida a propósito; las civs de tendencias
     *  (stCivsSeleccionadas) también tienen que vaciarse, aunque el repintado ya no avise con fila -1. */
    @Test void cambiarDeModoVaciaLasCivsDeTendencias() throws Exception {
        FiltroStats filtro = new FiltroStats("rm_1v1", "30", "*", "*");
        List<List<String>> tras = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            CivStatsView cs = vista(filtro);
            cs.filtrosCambiados(false);
            for (int r = 0; r < cs.stModelo.getRowCount(); r++) if ("franks".equals(cs.stTabla.getClientProperty("civ" + r))) { int vr = cs.stTabla.convertRowIndexToView(r); cs.stTabla.setRowSelectionInterval(vr, vr); }
            tras.add(new java.util.ArrayList<>(cs.stCivsSeleccionadas));
            cs.stModoCombo.setSelectedIndex(1);
            tras.add(new java.util.ArrayList<>(cs.stCivsSeleccionadas));
        });
        assertEquals(List.of("franks"), tras.get(0));
        assertEquals(List.of(), tras.get(1), "tras cambiar de modo no queda ninguna civ en tendencias");
        assertEquals(null, filtro.civSeleccionada());
    }

    /** F3 (revisión 1.3): con una civ seleccionada en la tabla, cambiar el mapa (o el tramo) repinta la tabla
     *  (setRowCount(0) + filas nuevas). La civ debe seguir seleccionada: solo el cambio de modo la borra a propósito. */
    @Test void cambiarDeMapaConservaLaCivSeleccionada() throws Exception {
        FiltroStats filtro = new FiltroStats("rm_1v1", "30", "*", "*");
        String[] tras = new String[2];
        SwingUtilities.invokeAndWait(() -> {
            CivStatsView cs = vista(filtro);
            cs.filtrosCambiados(false);
            int fila = -1;
            for (int r = 0; r < cs.stModelo.getRowCount(); r++) if ("franks".equals(cs.stTabla.getClientProperty("civ" + r))) fila = cs.stTabla.convertRowIndexToView(r);
            cs.stTabla.setRowSelectionInterval(fila, fila);
            tras[0] = filtro.civSeleccionada();
            cs.stMapaCombo.setSelectedIndex(1);   // el primer mapa real: dispara filtrosCambiados como el usuario
            tras[1] = filtro.civSeleccionada();
        });
        assertEquals("franks", tras[0], "la selección de la tabla llega al filtro compartido");
        assertEquals("franks", tras[1], "tras cambiar de mapa la civ sigue elegida");
    }

    /** F3 con varias civs (1.4): con dos civs elegidas (Ctrl), cambiar el mapa conservaba solo la primera; las dos
     *  deben seguir en tendencias y en la tabla, y la civ del filtro no cambia. */
    @Test void cambiarDeMapaConservaVariasCivsSeleccionadas() throws Exception {
        FiltroStats filtro = new FiltroStats("rm_1v1", "30", "*", "*");
        List<Object> tras = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            CivStatsView cs = vista(filtro);
            cs.filtrosCambiados(false);
            for (String civ : new String[]{ "aztecs", "mongols" })
                for (int r = 0; r < cs.stModelo.getRowCount(); r++) if (civ.equals(cs.stTabla.getClientProperty("civ" + r))) { int vr = cs.stTabla.convertRowIndexToView(r); cs.stTabla.addRowSelectionInterval(vr, vr); }
            tras.add(new java.util.TreeSet<>(cs.stCivsSeleccionadas));
            tras.add(filtro.civSeleccionada());
            cs.stMapaCombo.setSelectedIndex(1);
            tras.add(new java.util.TreeSet<>(cs.stCivsSeleccionadas));
            tras.add(filtro.civSeleccionada());
            tras.add(cs.stTabla.getSelectedRowCount());
        });
        assertEquals(new java.util.TreeSet<>(List.of("aztecs", "mongols")), tras.get(0));
        assertEquals(new java.util.TreeSet<>(List.of("aztecs", "mongols")), tras.get(2), "las dos siguen en tendencias");
        assertEquals(tras.get(1), tras.get(3), "la civ del filtro es la misma");
        assertEquals(2, tras.get(4), "y las dos siguen marcadas en la tabla");
    }
}
