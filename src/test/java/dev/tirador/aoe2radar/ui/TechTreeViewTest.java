package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.service.StatsService;
import dev.tirador.aoe2radar.service.TechTreeService;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
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
        Map<String, Object> arbolCiv = Map.of();
        Path iconoFijo;
        @Override public Map<String, Object> datos() { return datos; }
        @Override public boolean arbolEnCache(String civ) { return false; }
        @Override public Map<String, Object> arbolCacheado(String civ) { return null; }
        @Override public Path dir() { return Path.of("techtree-test-no-existe"); }
        @Override public Path rutaIcono(String tipo, long id) { return iconoFijo; }
        @Override public Map<String, Object> arbol(String civ) { return arbolCiv; }
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
        final List<String> urls = new ArrayList<>();
        @Override public void precalentarPerfiles() { }
        @Override public void cerrar() { }
        @Override public void abrirUrl(String url) { urls.add(url); }
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

    /** Tareas que ejecuta enFondo EN EL ACTO (como techtree-icono-disco de verdad, en un hilo que ya terminó) y
     *  cuenta cuántas veces se pidió cada nombre de hilo: sirve para ver si ttIconoPintado reencola sin fin. */
    static final class TareasContadas implements Tareas {
        final List<String> lanzados = new ArrayList<>();
        @Override public void enFondo(String nombre, Runnable trabajo) { lanzados.add(nombre); trabajo.run(); }
        @Override public void enFondoDemonio(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enFondoDemonioMinima(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enUi(Runnable trabajo) { trabajo.run(); }
        long veces(String nombre) { return lanzados.stream().filter(nombre::equals).count(); }
    }

    /**
     * Hallazgo del revisor sobre 5ca5f49: si el icono no está NI en disco, ttIcono devuelve null, pero el código
     * repintaba siempre (ttPintarDeNuevo reconstruye el árbol entero), lo que volvía a llamar a ttIconoPintado
     * con la misma clave, que volvía a pedir OTRO hilo de fondo, sin fin, mientras la pestaña estuviera visible.
     * Aquí se simula ese "repintado siguiente" llamando a ttIconoPintado varias veces seguidas para la misma
     * clave (cada llamada real a ttPintarArbol vuelve a pedir el mismo tipo/id/px): con el arreglo, de las tres
     * llamadas solo la primera pide un hilo de fondo.
     */
    @Test void iconoAusenteEnDiscoNoSeReencolaEnCadaPintado() throws Exception {
        TechTreeServiceFake tt = new TechTreeServiceFake();
        tt.iconoFijo = Path.of(System.getProperty("java.io.tmpdir"), "techtree-nunca-existe-" + System.nanoTime() + ".png");
        assertFalse(Files.exists(tt.iconoFijo));
        TareasContadas tareas = new TareasContadas();

        JFrame marco = new JFrame();
        TechTreeView[] out = new TechTreeView[1];
        SwingUtilities.invokeAndWait(() -> out[0] = new TechTreeView(marco, tt, new StatsServiceFake(),
                new FiltroStats("rm_1v1", "30", "*", "*"), new Listas(marco, b -> { }), null, tareas,
                new AnfitrionFake(), new EnlaceCivStatsFake()));
        TechTreeView v = out[0];

        // tres "pintados" seguidos pidiendo el mismo icono, ausente de verdad en disco: como el árbol repintándose
        // solo una y otra vez porque el anterior repintado dejó el icono sin cachear.
        for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> v.ttIconoPintado("Unit", 42, 40));

        assertEquals(1, tareas.veces("techtree-icono-disco"),
                "un icono confirmado ausente en disco no debe pedir un hilo nuevo en cada pintado (bucle sin fin)");
    }

    /** Cuando la cola de red SÍ trae algo nuevo (iconosActualizados), sí merece la pena reintentar los que se
     *  habían marcado ausentes: si no, un icono que tardó en descargarse se quedaría sin dibujar para siempre. */
    @Test void iconosActualizadosPermiteReintentarUnIconoQueEstabaAusente() throws Exception {
        TechTreeServiceFake tt = new TechTreeServiceFake();
        tt.iconoFijo = Path.of(System.getProperty("java.io.tmpdir"), "techtree-nunca-existe-" + System.nanoTime() + ".png");
        TareasContadas tareas = new TareasContadas();

        JFrame marco = new JFrame();
        TechTreeView[] out = new TechTreeView[1];
        SwingUtilities.invokeAndWait(() -> out[0] = new TechTreeView(marco, tt, new StatsServiceFake(),
                new FiltroStats("rm_1v1", "30", "*", "*"), new Listas(marco, b -> { }), null, tareas,
                new AnfitrionFake(), new EnlaceCivStatsFake()));
        TechTreeView v = out[0];

        SwingUtilities.invokeAndWait(() -> v.ttIconoPintado("Unit", 42, 40));
        SwingUtilities.invokeAndWait(() -> v.ttIconoPintado("Unit", 42, 40));
        assertEquals(1, tareas.veces("techtree-icono-disco"), "todavía sin novedades: no se reintenta solo");

        SwingUtilities.invokeAndWait(v::iconosActualizados);   // la cola de red avisó (aunque no trajera este icono)
        SwingUtilities.invokeAndWait(() -> v.ttIconoPintado("Unit", 42, 40));

        assertEquals(2, tareas.veces("techtree-icono-disco"), "tras iconosActualizados sí se reintenta una vez más");
    }

    /**
     * Revisor, bullet 2: la precarga (TechTreePresenter.pedirArbol, precalentarIcono) y el pintado
     * (TechTreeView.ttPintarArbol) pedían tamaños distintos (px-4/26 la precarga, celda-6/34 el pintado), así
     * que la caché de precalentarIcono nunca acertaba y el respaldo de disco (ttIconoPintado, con su hilo de
     * fondo) era el camino normal en vez de la excepción. De punta a punta, con el icono de verdad en disco:
     * tras pedirArbol (que precalienta y pinta), no debe hacer falta NINGÚN hilo "techtree-icono-disco".
     */
    @Test void laPrecargaYElPintadoPidenElMismoTamanoDeIconoYNoHaceFaltaElRespaldoDeDisco() throws Exception {
        Path dir = Files.createTempDirectory("techtree-icono-alineado");
        Path icono = dir.resolve("42.png");
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", icono.toFile());

        TechTreeServiceFake tt = new TechTreeServiceFake();
        tt.iconoFijo = icono;
        Map<String, Object> nodo = Map.of("id", "1", "use_type", "Unit", "node_id", 1L, "picture_index", 42L,
                "node_status", "Available", "name_string_id", "nodo");
        Map<String, Object> edificio = Map.of("picture_index", 42L, "building_id", 5L, "name_string_id", "edificio",
                "node_status", "Available", "age_id", 1L, "grid", List.of(List.of("1")));
        tt.arbolCiv = Map.of("units_techs", List.of(nodo), "buildings", List.of(edificio));

        TareasContadas tareas = new TareasContadas();
        JFrame marco = new JFrame();
        TechTreeView[] out = new TechTreeView[1];
        SwingUtilities.invokeAndWait(() -> out[0] = new TechTreeView(marco, tt, new StatsServiceFake(),
                new FiltroStats("rm_1v1", "30", "*", "*"), new Listas(marco, b -> { }), null, tareas,
                new AnfitrionFake(), new EnlaceCivStatsFake()));
        TechTreeView v = out[0];

        // ttMostrarCiv encadena presenter.pedirArbol: con TareasContadas (todo en el acto) precalienta y pinta
        // en la misma llamada, como si el hilo techtree-civ ya hubiera terminado.
        SwingUtilities.invokeAndWait(() -> v.ttMostrarCiv("aztecs"));

        assertEquals(0, tareas.veces("techtree-icono-disco"),
                "con los tamaños alineados, la precarga ya deja el icono en caché: el pintado no debe pedir el respaldo de disco");
        assertFalse(v.ttIconos.isEmpty(), "y el árbol sí pinta con icono: la precarga cacheó algo de verdad");
    }

    /** 1.4.1: el pie ya no dice «Datos e iconos: … MIT» (los iconos no son MIT: son de Microsoft, bajo sus Game
     *  Content Usage Rules) y enlaza esas reglas: el clic pide a la ventana abrir su URL. */
    @Test void elPieSeparaLaMitDeLosIconosDeMicrosoftYEnlazaSusReglas() throws Exception {
        AnfitrionFake anfitrion = new AnfitrionFake();
        JFrame marco = new JFrame();
        TechTreeView[] out = new TechTreeView[1];
        SwingUtilities.invokeAndWait(() -> out[0] = new TechTreeView(marco, new TechTreeServiceFake(), new StatsServiceFake(),
                new FiltroStats("rm_1v1", "30", "*", "*"), new Listas(marco, b -> { }), null, new TareasAplazadas(),
                anfitrion, new EnlaceCivStatsFake()));
        JPanel[] pie = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> pie[0] = out[0].piePagina());

        List<JLabel> etiquetas = new ArrayList<>();
        etiquetasDe(pie[0], etiquetas);
        StringBuilder texto = new StringBuilder();
        JLabel enlace = null;
        for (JLabel l : etiquetas) {
            texto.append(l.getText());
            if (l.getCursor().getType() == Cursor.HAND_CURSOR) enlace = l;
        }
        String s = texto.toString();
        // El enlace va en la parte que nunca se recorta (WEST); la ayuda de uso, en la que sí (CENTER).
        JPanel oeste = (JPanel) ((BorderLayout) pie[0].getLayout()).getLayoutComponent(BorderLayout.WEST);
        List<JLabel> fijas = new ArrayList<>();
        etiquetasDe(oeste, fijas);
        assertTrue(fijas.contains(enlace), "el enlace no debe poder quedar fuera en una ventana estrecha");
        System.out.println("pie del Tech tree: ancho preferido " + pie[0].getPreferredSize().width + " px, atribución "
                + oeste.getPreferredSize().width + " px");
        assertFalse(s.contains("iconos: aoe2techtree") || s.contains("icons: aoe2techtree"), "los iconos no son MIT: " + s);
        assertTrue(s.contains("aoe2techtree.net (HSZemi, MIT)"), s);
        assertTrue(s.contains("Age of Empires II © Microsoft"), s);
        assertTrue(s.contains("Game Content Usage Rules"), s);
        assertTrue(enlace != null, "falta la etiqueta clicable de las reglas");

        JLabel e = enlace;
        SwingUtilities.invokeAndWait(() -> e.dispatchEvent(
                new MouseEvent(e, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 2, 2, 1, false, MouseEvent.BUTTON1)));
        assertEquals(List.of("https://www.xbox.com/en-US/developers/rules"), anfitrion.urls);
    }

    private static void etiquetasDe(java.awt.Container c, List<JLabel> out) {
        for (Component h : c.getComponents()) {
            if (h instanceof JLabel l) out.add(l);
            else if (h instanceof java.awt.Container sub) etiquetasDe(sub, out);
        }
    }
}
