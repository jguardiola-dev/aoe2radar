package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.service.StatsService;
import dev.tirador.aoe2radar.service.TechTreeService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caracteriza TechTreePresenter con Tareas.EN_LINEA (sin hilos reales) y dobles de los servicios: mismo
 * comportamiento que hoy (comprobación de "la civ pedida cambió", "civStatsPanel != null" antes de repintar
 * la banda, cola de iconos con máximo 4 hilos y sin pedidos duplicados, precarga que calienta perfiles antes
 * que nada).
 */
class TechTreePresenterTest {

    /** Doble mínimo de TechTreeService: cada método hace lo justo para que el presentador se comporte igual. */
    static class TechTreeServiceFake implements TechTreeService {
        Map<String, Object> datos = Map.of();
        String errorAsegurar = null;
        Map<String, Object> arbolCiv = Map.of();
        RuntimeException fallaArbol;
        List<String> descargados = new ArrayList<>();
        RuntimeException fallaDescarga;

        @Override public Map<String, Object> datos() { return datos; }
        @Override public boolean arbolEnCache(String civ) { return false; }
        @Override public Map<String, Object> arbolCacheado(String civ) { return null; }
        @Override public Path dir() { return Path.of("techtree"); }
        @Override public Path rutaIcono(String tipo, long id) { return Path.of("techtree", tipo, id + ".png"); }
        @Override public Map<String, Object> arbol(String civ) throws Exception { if (fallaArbol != null) throw fallaArbol; return arbolCiv; }
        @Override public String asegurarDatos() { return errorAsegurar; }
        @Override public void comprobarActualizacion() { }
        @Override public String clase(int id) { return "clase" + id; }
        @Override public void descargar(String rel) throws Exception { if (fallaDescarga != null) throw fallaDescarga; descargados.add(rel); }
        @Override public String nombre(Object id) { return String.valueOf(id); }
        @Override public String nombreCiv(String civ) { return civ; }
        @Override public String str(Object id) { return String.valueOf(id); }
    }

    /** Doble mínimo de StatsService: solo asegurar()/tieneVentana() importan a este presentador. */
    static class StatsServiceFake implements StatsService {
        boolean tieneVentana;
        String errorAsegurar;

        @Override public String asegurar(String ventana, boolean conTendencias) { tieneVentana = errorAsegurar == null; return errorAsegurar; }
        @Override public VentanaStats ventana(String clave) { return null; }
        @Override public boolean tieneVentana(String clave) { return tieneVentana; }
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

    /** Doble de Pantalla: apunta lo último que le llamó el presentador, sin Swing. */
    static class PantallaFake implements TechTreePresenter.Pantalla {
        String ultimoError, ultimaCivPedida, ultimoEstadoWr;
        String civArbolListo; Map<String, Object> arbolListo;
        String civError, motivoError;
        int celdaPx = 40;
        int iconosPrecalentados;
        int actualizarWrLlamadas;
        int iconosActualizadosLlamadas;
        boolean datosListosLlamado;

        @Override public void datosListos(String error, String civPedida) { datosListosLlamado = true; ultimoError = error; ultimaCivPedida = civPedida; }
        @Override public void arbolListo(String civ, Map<String, Object> arbol) { civArbolListo = civ; this.arbolListo = arbol; }
        @Override public void errorArbol(String civ, String motivo) { civError = civ; motivoError = motivo; }
        @Override public int celdaPx() { return celdaPx; }
        @Override public void precalentarIcono(String tipo, long id, int px) { iconosPrecalentados++; }
        @Override public void iconosActualizados() { iconosActualizadosLlamadas++; }
        @Override public void estadoWr(String texto) { ultimoEstadoWr = texto; }
        @Override public void actualizarWr() { actualizarWrLlamadas++; }
    }

    static class AnfitrionFake implements TechTreeView.Anfitrion {
        boolean precalentado; boolean cerrado;
        @Override public void precalentarPerfiles() { precalentado = true; }
        @Override public void cerrar() { cerrado = true; }
    }

    static class EnlaceCivStatsFake implements TechTreeView.EnlaceCivStats {
        boolean construida;
        boolean filtrosCambiadosLlamado; boolean ultimoRepintarTechTree;
        String ultimaVentanaSincronizada;
        @Override public boolean construida() { return construida; }
        @Override public void filtrosCambiados(boolean repintarTechTree) { filtrosCambiadosLlamado = true; ultimoRepintarTechTree = repintarTechTree; }
        @Override public void sincronizarVentana(String ventana) { ultimaVentanaSincronizada = ventana; }
    }

    private TechTreeServiceFake tt;
    private StatsServiceFake stats;
    private FiltroStats filtroStats;
    private PantallaFake pantalla;
    private AnfitrionFake anfitrion;
    private EnlaceCivStatsFake enlaceCivStats;

    private TechTreePresenter crear(Tareas tareas) {
        tt = new TechTreeServiceFake();
        stats = new StatsServiceFake();
        filtroStats = new FiltroStats("rm_1v1", "30", "*", "*");
        pantalla = new PantallaFake();
        anfitrion = new AnfitrionFake();
        enlaceCivStats = new EnlaceCivStatsFake();
        return new TechTreePresenter(tt, stats, filtroStats, tareas, pantalla, anfitrion, enlaceCivStats);
    }

    @Test void cargarDatosOk() {
        TechTreePresenter p = crear(Tareas.EN_LINEA);
        tt.errorAsegurar = null;
        p.cargarDatos("aztecs");
        assertTrue(pantalla.datosListosLlamado);
        assertNull(pantalla.ultimoError);
        assertEquals("aztecs", pantalla.ultimaCivPedida);
    }

    @Test void cargarDatosError() {
        TechTreePresenter p = crear(Tareas.EN_LINEA);
        tt.errorAsegurar = "sin conexión";
        p.cargarDatos(null);
        assertEquals("sin conexión", pantalla.ultimoError);
    }

    @Test void pedirArbolOk() {
        TechTreePresenter p = crear(Tareas.EN_LINEA);
        tt.arbolCiv = Map.of("units_techs", List.of());
        p.pedirArbol("britons");
        assertEquals("britons", pantalla.civArbolListo);
        assertEquals(tt.arbolCiv, pantalla.arbolListo);
    }

    @Test void pedirArbolError() {
        TechTreePresenter p = crear(Tareas.EN_LINEA);
        tt.fallaArbol = new RuntimeException("HTTP 500");
        p.pedirArbol("mongols");
        assertEquals("mongols", pantalla.civError);
        assertEquals("HTTP 500", pantalla.motivoError);
        assertNull(pantalla.civArbolListo);
    }

    /** Si mientras se descargaba el árbol se pidió otra civ, el que llega tarde no se pinta: la única forma de
     *  observarlo sin reloj es haciendo que el propio TechTreeService "cambie de idea" a media descarga. */
    @Test void civPedidaCaducadaNoPinta() {
        crear(Tareas.EN_LINEA);   // deja listos stats/filtroStats/pantalla/anfitrion/enlaceCivStats
        final TechTreePresenter[] ref = new TechTreePresenter[1];
        tt = new TechTreeServiceFake() {
            @Override public Map<String, Object> arbol(String civ) {
                if ("vieja".equals(civ)) ref[0].pedirArbol("nueva");   // llega una petición más reciente antes de que "vieja" termine
                return Map.of("civ", civ);
            }
        };
        ref[0] = new TechTreePresenter(tt, stats, filtroStats, Tareas.EN_LINEA, pantalla, anfitrion, enlaceCivStats);
        ref[0].pedirArbol("vieja");
        assertEquals("nueva", pantalla.civArbolListo);   // solo se pintó la última, nunca "vieja"
    }

    @Test void civEnCursoEsLaMismaFuenteQuePedirArbol() {
        TechTreePresenter p = crear(Tareas.EN_LINEA);
        assertNull(p.civEnCurso());
        tt.arbolCiv = Map.of("units_techs", List.of());
        p.pedirArbol("britons");
        assertEquals("britons", p.civEnCurso());
    }

    @Test void cargarStatsVentanaYaCargadaNoVaARed() {
        TechTreePresenter p = crear(Tareas.EN_LINEA);
        stats.tieneVentana = true;
        p.cargarStats();
        assertEquals(1, pantalla.actualizarWrLlamadas);
        assertNull(pantalla.ultimoEstadoWr);
    }

    @Test void cargarStatsOkConCivStatsConstruida() {
        TechTreePresenter p = crear(Tareas.EN_LINEA);
        stats.tieneVentana = false;
        stats.errorAsegurar = null;
        enlaceCivStats.construida = true;
        p.cargarStats();
        assertTrue(enlaceCivStats.filtrosCambiadosLlamado);
        assertTrue(enlaceCivStats.ultimoRepintarTechTree);
        assertEquals(0, pantalla.actualizarWrLlamadas);   // lo repinta Civ Stats, no directamente la banda
    }

    @Test void cargarStatsOkSinCivStatsConstruida() {
        TechTreePresenter p = crear(Tareas.EN_LINEA);
        stats.tieneVentana = false;
        stats.errorAsegurar = null;
        enlaceCivStats.construida = false;
        p.cargarStats();
        assertFalse(enlaceCivStats.filtrosCambiadosLlamado);
        assertEquals(1, pantalla.actualizarWrLlamadas);
    }

    @Test void cargarStatsError() {
        TechTreePresenter p = crear(Tareas.EN_LINEA);
        stats.tieneVentana = false;
        stats.errorAsegurar = "HTTP 404";
        p.cargarStats();
        assertEquals("sin datos (HTTP 404)", pantalla.ultimoEstadoWr);
        assertFalse(enlaceCivStats.filtrosCambiadosLlamado);
    }

    @Test void pedirIconoNoDuplicaPedidos() {
        AtomicInteger hilosLanzados = new AtomicInteger();
        Tareas cuentaHilos = new Tareas() {
            @Override public void enFondo(String nombre, Runnable trabajo) { trabajo.run(); }
            @Override public void enFondoDemonio(String nombre, Runnable trabajo) { hilosLanzados.incrementAndGet(); }   // no lo ejecuta: solo cuenta cuántos hilos pediría
            @Override public void enFondoDemonioMinima(String nombre, Runnable trabajo) { trabajo.run(); }
            @Override public void enUi(Runnable trabajo) { trabajo.run(); }
        };
        TechTreePresenter p = crear(cuentaHilos);
        p.pedirIcono("img/Unit/1.png");
        int trasPrimeraVez = hilosLanzados.get();
        p.pedirIcono("img/Unit/1.png");   // el mismo icono otra vez: pedidos.add() lo rechaza, no debe pedir ni un hilo más
        assertEquals(trasPrimeraVez, hilosLanzados.get());
    }

    @Test void pedirIconoLimitaAHilos() {
        AtomicInteger hilosLanzados = new AtomicInteger();
        Tareas cuentaHilos = new Tareas() {
            @Override public void enFondo(String nombre, Runnable trabajo) { trabajo.run(); }
            @Override public void enFondoDemonio(String nombre, Runnable trabajo) { hilosLanzados.incrementAndGet(); }
            @Override public void enFondoDemonioMinima(String nombre, Runnable trabajo) { trabajo.run(); }
            @Override public void enUi(Runnable trabajo) { trabajo.run(); }
        };
        TechTreePresenter p = crear(cuentaHilos);
        for (int i = 0; i < 10; i++) p.pedirIcono("img/Unit/" + i + ".png");   // 10 iconos distintos, máximo 4 hilos
        assertEquals(4, hilosLanzados.get());
    }

    @Test void precargarCalientaPerfilesAntesQueNada() {
        List<String> orden = new ArrayList<>();
        TechTreePresenter p = crear(Tareas.EN_LINEA);
        anfitrion = new AnfitrionFake() {
            @Override public void precalentarPerfiles() { orden.add("precalentar"); super.precalentarPerfiles(); }
        };
        Tareas tareas = new Tareas() {
            @Override public void enFondo(String nombre, Runnable trabajo) { trabajo.run(); }
            @Override public void enFondoDemonio(String nombre, Runnable trabajo) { orden.add("demonio:" + nombre); trabajo.run(); }
            @Override public void enFondoDemonioMinima(String nombre, Runnable trabajo) { orden.add("demoniominima:" + nombre); trabajo.run(); }
            @Override public void enUi(Runnable trabajo) { trabajo.run(); }
        };
        tt.errorAsegurar = "sin datos";   // corta pronto: no hace falta simular el catálogo entero
        p = new TechTreePresenter(tt, stats, filtroStats, tareas, pantalla, anfitrion, enlaceCivStats);
        p.precargar();
        assertTrue(anfitrion.precalentado);
        assertEquals(List.of("precalentar", "demonio:techtree-precarga"), orden);
    }
}
