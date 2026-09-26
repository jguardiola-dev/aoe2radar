package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.service.StatsService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CivStatsPresenter sin red ni Swing: un {@link StatsService} de mentira que apunta lo que le piden, y
 * {@link Tareas#EN_LINEA} para que todo corra en el acto. Para «ya hay una carga en curso» se usa un Tareas
 * que aplaza el hilo de fondo (no lo ejecuta hasta que el test se lo pide), como una descarga real que tarda.
 */
class CivStatsPresenterTest {

    /** Registra lo que el presentador le pide a la vista, sin pintar nada. */
    static final class PantallaFalsa implements CivStatsPresenter.Pantalla {
        final List<String> estados = new ArrayList<>();
        int datosListos, repintados;
        @Override public void mostrarEstado(String texto) { estados.add(texto); }
        @Override public void datosListos() { datosListos++; }
        @Override public void repintarTendencias() { repintados++; }
    }

    /** Responde lo que se le diga, sin red; apunta qué ventana y con qué tendencias se le pidió. */
    static final class StatsFalso implements StatsService {
        String errorAsegurar;
        final List<String> ventanasPedidas = new ArrayList<>();
        final List<Boolean> conTendenciasPedido = new ArrayList<>();
        boolean tieneParche;
        @Override public String asegurar(String ventana, boolean conTendencias) {
            ventanasPedidas.add(ventana);
            conTendenciasPedido.add(conTendencias);
            return errorAsegurar;
        }
        @Override public VentanaStats ventana(String clave) { return null; }
        @Override public boolean tieneVentana(String clave) { return "parche".equals(clave) && tieneParche; }
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

    /** No ejecuta enFondo: lo deja pendiente (como una descarga real que aún no ha vuelto). enUi sí corre en el acto. */
    static final class TareasAplazada implements Tareas {
        final List<Runnable> pendientes = new ArrayList<>();
        @Override public void enFondo(String nombre, Runnable trabajo) { pendientes.add(trabajo); }
        @Override public void enFondoDemonio(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enFondoDemonioMinima(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enUi(Runnable trabajo) { trabajo.run(); }
    }

    @Test void cargarOkAvisaDatosListosTrasElHiloYVuelveAlEdt() {
        StatsFalso stats = new StatsFalso();
        PantallaFalsa pantalla = new PantallaFalsa();
        new CivStatsPresenter(stats, Tareas.EN_LINEA, pantalla).cargar("30");
        assertEquals(List.of("30"), stats.ventanasPedidas);
        assertEquals(List.of(true), stats.conTendenciasPedido, "civstats-datos siempre pide tendencias");
        assertEquals(1, pantalla.datosListos);
        assertEquals(0, pantalla.repintados);
        assertEquals(1, pantalla.estados.size(), "solo el aviso de «descargando», sin error");
    }

    @Test void cargarConErrorAvisaDelMotivoYNoLlamaADatosListos() {
        StatsFalso stats = new StatsFalso();
        stats.errorAsegurar = "sin conexión";
        PantallaFalsa pantalla = new PantallaFalsa();
        new CivStatsPresenter(stats, Tareas.EN_LINEA, pantalla).cargar("30");
        assertEquals(0, pantalla.datosListos);
        assertEquals(2, pantalla.estados.size(), "descargando… y luego el error");
        assertTrue(pantalla.estados.get(1).contains("sin conexión"));
    }

    @Test void mensajeDeDescargaNombraLaVentanaLegibleEnMinusculas() {
        StatsFalso stats = new StatsFalso();
        PantallaFalsa pantalla = new PantallaFalsa();
        new CivStatsPresenter(stats, Tareas.EN_LINEA, pantalla).cargar("30");
        String esperado = dev.tirador.aoe2radar.service.NombresStats.ventanaNombre("30").toLowerCase(Locale.ROOT);
        assertTrue(pantalla.estados.get(0).contains(esperado), "el mensaje debe nombrar la ventana en minúsculas: " + pantalla.estados.get(0));
    }

    @Test void siYaHayUnaCargaEnCursoNoRelanzaOtroHilo() {
        StatsFalso stats = new StatsFalso();
        PantallaFalsa pantalla = new PantallaFalsa();
        TareasAplazada tareas = new TareasAplazada();
        CivStatsPresenter p = new CivStatsPresenter(stats, tareas, pantalla);
        p.cargar("30");
        p.cargar("30");   // la primera aún no ha terminado (el hilo está aplazado): esta no debe encolar nada
        assertEquals(1, tareas.pendientes.size(), "solo un hilo civstats-datos en vuelo");
        tareas.pendientes.remove(0).run();   // al terminar, solo se pidió una vez a stats
        assertEquals(List.of("30"), stats.ventanasPedidas);
    }

    @Test void trasTerminarUnaCargaSePuedeVolverAPedir() {
        StatsFalso stats = new StatsFalso();
        PantallaFalsa pantalla = new PantallaFalsa();
        TareasAplazada tareas = new TareasAplazada();
        CivStatsPresenter p = new CivStatsPresenter(stats, tareas, pantalla);
        p.cargar("30");
        tareas.pendientes.remove(0).run();   // termina la primera carga (vuelve al EDT: cargando = false)
        p.cargar("90");
        tareas.pendientes.remove(0).run();
        assertEquals(List.of("30", "90"), stats.ventanasPedidas);
    }

    /** F2 (1.3): mientras baja «30 días» se elige «365 días». Al terminar la de 30 hay que pedir la de 365 (antes
     *  se perdía: el combo decía 365, se veían los datos de 30 y el estado seguía en «Descargando…»). */
    @Test void cambioDeVentanaDuranteUnaDescargaSePideAlTerminar() {
        StatsFalso stats = new StatsFalso();
        PantallaFalsa pantalla = new PantallaFalsa();
        TareasAplazada tareas = new TareasAplazada();
        CivStatsPresenter p = new CivStatsPresenter(stats, tareas, pantalla);
        p.cargar("30");
        p.cargar("365");   // la de 30 aún no ha vuelto
        assertEquals(1, tareas.pendientes.size(), "sigue habiendo un solo hilo en vuelo");
        tareas.pendientes.remove(0).run();   // vuelve la de 30: debe lanzar la de 365, no pintar la de 30
        assertEquals(0, pantalla.datosListos, "la ventana vieja no se pinta");
        assertEquals(1, tareas.pendientes.size(), "se lanzó la carga de la ventana vigente");
        tareas.pendientes.remove(0).run();
        assertEquals(List.of("30", "365"), stats.ventanasPedidas);
        assertEquals(1, pantalla.datosListos);
        assertTrue(pantalla.estados.get(pantalla.estados.size() - 1).contains(dev.tirador.aoe2radar.service.NombresStats.ventanaNombre("365").toLowerCase(Locale.ROOT)), "el último aviso es el de la ventana nueva");
    }

    /** F2: ir y volver (30 → 365 → 30) durante la descarga de 30 no descarga nada más: la vigente es la que llegó. */
    @Test void idaYVueltaALaMismaVentanaNoRelanza() {
        StatsFalso stats = new StatsFalso();
        PantallaFalsa pantalla = new PantallaFalsa();
        TareasAplazada tareas = new TareasAplazada();
        CivStatsPresenter p = new CivStatsPresenter(stats, tareas, pantalla);
        p.cargar("30");
        p.cargar("365");
        p.cargar("30");
        tareas.pendientes.remove(0).run();
        assertTrue(tareas.pendientes.isEmpty());
        assertEquals(List.of("30"), stats.ventanasPedidas);
        assertEquals(1, pantalla.datosListos);
    }

    @Test void elHiloDeCargaSeLlamaCivstatsDatos() {
        StatsFalso stats = new StatsFalso();
        PantallaFalsa pantalla = new PantallaFalsa();
        List<String> nombres = new ArrayList<>();
        Tareas tareas = new Tareas() {
            @Override public void enFondo(String nombre, Runnable trabajo) { nombres.add(nombre); trabajo.run(); }
            @Override public void enFondoDemonio(String nombre, Runnable trabajo) { trabajo.run(); }
            @Override public void enFondoDemonioMinima(String nombre, Runnable trabajo) { trabajo.run(); }
            @Override public void enUi(Runnable trabajo) { trabajo.run(); }
        };
        new CivStatsPresenter(stats, tareas, pantalla).cargar("30");
        assertEquals(List.of("civstats-datos"), nombres);
    }

    @Test void cargarParcheYaCargadoSoloRepintaSinPedirNada() {
        StatsFalso stats = new StatsFalso();
        stats.tieneParche = true;
        PantallaFalsa pantalla = new PantallaFalsa();
        new CivStatsPresenter(stats, Tareas.EN_LINEA, pantalla).cargarParcheSiHaceFalta();
        assertEquals(1, pantalla.repintados);
        assertTrue(stats.ventanasPedidas.isEmpty(), "ya estaba cargada: no se pide de nuevo");
    }

    @Test void cargarParcheSiNoEstaLaPideYLuegoRepinta() {
        StatsFalso stats = new StatsFalso();
        stats.tieneParche = false;
        PantallaFalsa pantalla = new PantallaFalsa();
        new CivStatsPresenter(stats, Tareas.EN_LINEA, pantalla).cargarParcheSiHaceFalta();
        assertEquals(List.of("parche"), stats.ventanasPedidas);
        assertEquals(List.of(false), stats.conTendenciasPedido, "el parche se pide sin recalcular tendencias");
        assertEquals(1, pantalla.repintados);
    }
}
