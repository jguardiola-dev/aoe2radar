package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.LadderHist;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.model.Rejilla;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.RatingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static dev.tirador.aoe2radar.util.Config.CONFIG_FILE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DEUDA fila 13: al abrir Ratings por primera vez, el divisor de la tabla de comparados puede seguir midiendo
 * 0 (todavía no se mostró ni una vez): el reparto de 230 px no se debe perder, sino aplicarse en cuanto el
 * divisor tenga alto real. Componentes Swing reales dentro de invokeAndWait, nunca visibles (como
 * VentanaGuardadaTest/BarraEstadoTest): no hace falta pantalla.
 */
class RatingsViewTest {

    /** Doble mínimo de RatingsService: construirPanelLadder() no llama a ninguno de sus métodos, así que basta
     *  con que compile; alAbrirDespues() (que sí los usaría) no se llama en estos tests. */
    static class RatingsServiceFalso implements RatingsService {
        @Override public String asegurar(boolean forzar) { return "sin datos"; }
        @Override public boolean cargando() { return false; }
        @Override public void cargando(boolean v) { }
        @Override public String progreso() { return ""; }
        @Override public LadderHist hist(String lb, boolean activos) { return null; }
        @Override public boolean tieneActivos(String lb) { return false; }
        @Override public Rejilla dispersion(String familia, boolean activos) { return null; }
        @Override public boolean dispersionTieneActivos(String familia) { return false; }
        @Override public Map<String, List<LadderRow>> clanes() { return Map.of(); }
        @Override public int activosMinPartidas() { return 10; }
        @Override public int activosDias() { return 28; }
        @Override public String generado() { return ""; }
    }

    static class BusquedaFalsa implements BusquedaPerfiles {
        @Override public List<String[]> sugerir(String q) { return List.of(); }
        @Override public List<String[]> buscar(String q) { return List.of(); }
        @Override public List<String[]> local(String q) { return List.of(); }
    }

    static class PerfilesFalso implements ProfileService {
        @Override public FichaPerfil ficha(long pid) { return null; }
        @Override public FichaPerfil fichaConocida(long pid) { return null; }
        @Override public Integer elo1v1(long pid) { return null; }
        @Override public List<Perfil.Vinculada> vinculadas(long pid) { return List.of(); }
        @Override public List<Perfil.Vinculada> vinculadasConElo(long pid) { return List.of(); }
        @Override public List<Perfil.Vinculada> vinculadasConocidas(long pid) { return null; }
        @Override public Integer eloVinculada(long vid) { return null; }
        @Override public Map<Long, String> familia(long pid) { return null; }
        @Override public AnioSfr anioSfr(long pid, String nombreSiFalta) { return null; }
        @Override public Actividad actividad(long pid) { return null; }
        @Override public Actividad historial(long pid, String nombre, Actividad base, boolean mas, int maxPaginas, Consumer<Actividad> parcial, BooleanSupplier cancelar) { return null; }
        @Override public int traerHoy(long pid) { return 0; }
    }

    static class AnfitrionFalso implements RatingsView.Anfitrion {
        @Override public List<Player> seleccion() { return List.of(); }
        @Override public boolean seleccionado(long pid) { return false; }
        @Override public void deseleccionar(long pid) { }
        @Override public void limpiarSeleccion() { }
        @Override public String nombreVisible(long pid, String nombre) { return nombre; }
    }

    @BeforeEach
    @AfterEach
    void limpiarConfig() throws Exception { Files.deleteIfExists(CONFIG_FILE); }

    private RatingsView nueva() throws Exception {
        RatingsView[] out = new RatingsView[1];
        SwingUtilities.invokeAndWait(() -> out[0] = new RatingsView(new RatingsServiceFalso(), new BusquedaFalsa(), new PerfilesFalso(), Tareas.EN_LINEA, new AnfitrionFalso()));
        return out[0];
    }

    /** invokeLater necesita un pase por el EDT para ejecutarse; un invokeAndWait vacío, encolado detrás, sirve
     *  de barrera: cuando vuelve, lo que había antes en la cola ya corrió. */
    private static void pumpEdt() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }

    @Test void conAltoYaConocidoRepartaLosPxDeInmediato() throws Exception {
        Files.writeString(CONFIG_FILE, "ratings_tabla_alto=250\n");   // 250, no 230 (el valor por defecto): así la prueba exige leer la config, no solo caer en el fallback
        RatingsView v = nueva();
        SwingUtilities.invokeAndWait(() -> {
            v.ladderDivisor.setSize(400, 600);   // el divisor ya tiene alto: no hace falta esperar a nada
            v.alAbrirAntes();
        });
        pumpEdt();
        SwingUtilities.invokeAndWait(() ->
                assertEquals(600 - 250 - v.ladderDivisor.getDividerSize(), v.ladderDivisor.getDividerLocation()));
    }

    /** La mutación que demuestra la fila 13: sin el ComponentListener de reserva, cuando el divisor mide 0 al
     *  abrir, el reparto de 250 px no llega nunca aunque el divisor reciba su alto real un instante después
     *  (dividerLocation se queda en el valor por defecto de JSplitPane, muy lejos de 600 - 250 - grosor). */
    @Test void siElDivisorMide0AlAbrirElRepartoLlegaEnCuantoTengaAlto() throws Exception {
        Files.writeString(CONFIG_FILE, "ratings_tabla_alto=250\n");   // 250, no 230 (el valor por defecto): así la prueba exige leer la config, no solo caer en el fallback
        RatingsView v = nueva();
        SwingUtilities.invokeAndWait(v::alAbrirAntes);   // ladderDivisor.getHeight() == 0 en este momento
        pumpEdt();
        SwingUtilities.invokeAndWait(() -> v.ladderDivisor.setSize(400, 600));   // ahora sí: dispara componentResized
        pumpEdt();
        SwingUtilities.invokeAndWait(() ->
                assertEquals(600 - 250 - v.ladderDivisor.getDividerSize(), v.ladderDivisor.getDividerLocation()));
    }

    /** Lo que vio Jorge: al mostrarse Ratings, Swing reparte primero con la tabla aplastada y ese movimiento
     *  del divisor se guardaba antes de aplicar el alto del usuario, pisándolo. Ahora no se guarda nada hasta
     *  haber aplicado el guardado; después, lo que mueva el usuario sí. */
    @Test void elPrimerRepartoDeSwingNoPisaElAltoGuardado() throws Exception {
        Files.writeString(CONFIG_FILE, "ratings_tabla_alto=250\n");
        RatingsView v = nueva();
        SwingUtilities.invokeAndWait(() -> {
            v.ladderDivisor.setSize(400, 600);
            v.ladderDivisor.setDividerLocation(600 - 62 - v.ladderDivisor.getDividerSize());   // Swing: tabla aplastada
            v.alMoverDivisor(true);                                                          // antes: guardaba 62
        });
        assertEquals("250", dev.tirador.aoe2radar.util.Config.leerConfig("ratings_tabla_alto", ""), "el alto del usuario sigue intacto");
        SwingUtilities.invokeAndWait(v::alAbrirAntes);
        pumpEdt();
        SwingUtilities.invokeAndWait(() -> assertEquals(600 - 250 - v.ladderDivisor.getDividerSize(), v.ladderDivisor.getDividerLocation()));
        SwingUtilities.invokeAndWait(() -> {
            v.ladderDivisor.setDividerLocation(600 - 300 - v.ladderDivisor.getDividerSize());   // el usuario arrastra
            v.alMoverDivisor(true);
        });
        assertEquals("300", dev.tirador.aoe2radar.util.Config.leerConfig("ratings_tabla_alto", ""), "ya aplicado: lo que mueve el usuario se guarda");
    }
}
