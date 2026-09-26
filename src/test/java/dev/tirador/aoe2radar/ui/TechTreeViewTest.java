package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.service.StatsService;
import dev.tirador.aoe2radar.service.TechTreeService;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fila 145 de DEUDA: si ttIcono falla la caché durante el pintado (primera apertura sin tamaño de visor
 * todavía, o precarga que llega tarde), leía el icono de disco ahí mismo, en el EDT. Componentes Swing reales
 * dentro de invokeAndWait, nunca visibles (mismo patrón que RatingsViewTest): no hace falta pantalla.
 */
class TechTreeViewTest {

    /** Como el de TechTreePresenterTest: lo justo para que TechTreeView pinte sin red ni catálogo real. */
    static class TechTreeServiceFake implements TechTreeService {
        Map<String, Object> datos = Map.of();
        Path iconoFijo;
        @Override public Map<String, Object> datos() { return datos; }
        @Override public boolean arbolEnCache(String civ) { return false; }
        @Override public Map<String, Object> arbolCacheado(String civ) { return null; }
        @Override public Path dir() { return Path.of("techtree-test-no-existe"); }
        @Override public Path rutaIcono(String tipo, long id) { return iconoFijo; }
        @Override public Map<String, Object> arbol(String civ) { return Map.of(); }
        @Override public String asegurarDatos() { return null; }
        @Override public void comprobarActualizacion() { }
        @Override public String clase(int id) { return "clase" + id; }
        @Override public void descargar(String rel) { }
        @Override public String nombre(Object id) { return String.valueOf(id); }
        @Override public String nombreCiv(String civ) { return civ; }
        @Override public String str(Object id) { return String.valueOf(id); }
    }

    static class StatsServiceFake implements StatsService {
        @Override public String asegurar(String ventana, boolean conTendencias) { return "sin datos"; }
        @Override public VentanaStats ventana(String clave) { return null; }
        @Override public boolean tieneVentana(String clave) { return false; }
        @Override public String[] modos() { return new String[0]; }
        @Override public String[] clavesVentanas() { return new String[0]; }
        @Override public Tendencias tendencias() { return null; }
        @Override public boolean tramoEnRango(String tramo, List<String> tramos, String rango) { return false; }
        @Override public double[] wilson(int w, int n) { return new double[]{ 0, 0 }; }
        @Override public Map<String, CivAgg> agregarCivs(VentanaStats v, String modo, String mapa, String tramo) { return Map.of(); }
        @Override public Map<String, Integer> partidasPorMapa(VentanaStats v, String modo, String tramo) { return Map.of(); }
        @Override public Map<String, CivAgg> civPorMapa(VentanaStats v, String modo, String tramo, String civ) { return Map.of(); }
        @Override public String duracionMedia(long segundos, int n) { return "-"; }
    }

    static class AnfitrionFake implements TechTreeView.Anfitrion {
        @Override public void precalentarPerfiles() { }
        @Override public void cerrar() { }
    }

    static class EnlaceCivStatsFake implements TechTreeView.EnlaceCivStats {
        @Override public boolean construida() { return false; }
        @Override public void filtrosCambiados(boolean repintarTechTree) { }
        @Override public void sincronizarVentana(String ventana) { }
    }

    /** Como TareasAplazadas de TechTreePresenterTest: enFondo encola en vez de ejecutar, así se ve qué pasó
     *  ANTES de que el hilo de fondo llegara a correr; enUi corre en el acto (aquí no hay EDT real de por medio). */
    static final class TareasAplazadas implements Tareas {
        final List<Runnable> pendientesFondo = new ArrayList<>();
        @Override public void enFondo(String nombre, Runnable trabajo) { pendientesFondo.add(trabajo); }
        @Override public void enFondoDemonio(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enFondoDemonioMinima(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enUi(Runnable trabajo) { trabajo.run(); }
    }

    @Test void elPintadoNoLeeDiscoEnElEdtYCargaEnFondoAlFallarLaCache() throws Exception {
        // Un icono de verdad en disco, para que la carga en fondo pueda encontrarlo y cachearlo.
        Path dir = Files.createTempDirectory("techtree-icono-test");
        Path icono = dir.resolve("42.png");
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(img, "png", icono.toFile());

        TechTreeServiceFake tt = new TechTreeServiceFake();
        tt.iconoFijo = icono;
        TareasAplazadas tareas = new TareasAplazadas();

        JFrame marco = new JFrame();
        TechTreeView[] out = new TechTreeView[1];
        SwingUtilities.invokeAndWait(() -> out[0] = new TechTreeView(marco, tt, new StatsServiceFake(),
                new FiltroStats("rm_1v1", "30", "*", "*"), new Listas(marco, b -> { }), null, tareas,
                new AnfitrionFake(), new EnlaceCivStatsFake()));
        TechTreeView v = out[0];

        // Un edificio con un hueco de su rejilla ocupado por un nodo (Unit, picture_index 42): así ttPintarArbol
        // pinta tanto el icono del edificio como el del nodo, los dos caminos que antes leían disco en el EDT.
        Map<String, Object> nodo = Map.of("id", "1", "use_type", "Unit", "node_id", 1L, "picture_index", 42L,
                "node_status", "Available", "name_string_id", "nodo");
        Map<String, Object> edificio = Map.of("picture_index", 42L, "building_id", 5L, "name_string_id", "edificio",
                "node_status", "Available", "age_id", 1L, "grid", List.of(List.of("1")));
        Map<String, Object> arbol = Map.of("units_techs", List.of(nodo), "buildings", List.of(edificio));

        SwingUtilities.invokeAndWait(() -> v.arbolListo("aztecs", arbol));

        assertTrue(v.ttIconos.isEmpty(), "el pintado no debe cachear nada leyendo disco en el EDT: debe quedar pendiente");
        assertFalse(tareas.pendientesFondo.isEmpty(), "debe pedir la carga del icono en un hilo de fondo");

        // Se ejecuta el trabajo de fondo (como si terminara el hilo real): ahora sí puede leer disco y cachear.
        for (Runnable trabajo : new ArrayList<>(tareas.pendientesFondo)) trabajo.run();

        assertFalse(v.ttIconos.isEmpty(), "tras la carga en fondo, el icono ya está en caché");
    }
}
