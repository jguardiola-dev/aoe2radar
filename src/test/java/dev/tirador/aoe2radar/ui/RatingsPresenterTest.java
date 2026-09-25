package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Comparado;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.LadderHist;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.model.Rejilla;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.RatingsService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RatingsPresenter con Tareas.EN_LINEA (sin hilos) y dobles de los tres servicios y de la Pantalla: comprueba la
 * carga de los resúmenes, las sugerencias del buscador (con y sin cambio de query) y el comparador (añadir, tope
 * de {@link RatingsPresenter#MAX_COMPARADOS}, y la sincronización con la selección de la watchlist). No usa Swing.
 */
class RatingsPresenterTest {

    /** Un RatingsService de mentira: solo lo que usa el presentador (asegurar/cargando/progreso). */
    static final class ServicioFalso implements RatingsService {
        boolean cargando;
        String progreso = "";
        String error;      // lo que devuelve asegurar()
        int asegurarLlamadas;

        @Override public String asegurar(boolean forzar) { asegurarLlamadas++; return error; }
        @Override public boolean cargando() { return cargando; }
        @Override public void cargando(boolean v) { cargando = v; }
        @Override public String progreso() { return progreso; }
        @Override public LadderHist hist(String lb, boolean activos) { return null; }
        @Override public boolean tieneActivos(String lb) { return false; }
        @Override public Rejilla dispersion(String familia, boolean activos) { return null; }
        @Override public boolean dispersionTieneActivos(String familia) { return false; }
        @Override public Map<String, List<LadderRow>> clanes() { return Map.of(); }
        @Override public int activosMinPartidas() { return 10; }
        @Override public int activosDias() { return 28; }
        @Override public String generado() { return ""; }
    }

    static final class BusquedaFalsa implements BusquedaPerfiles {
        List<String[]> resultado = List.of();
        List<String> queries = new ArrayList<>();
        @Override public List<String[]> sugerir(String q) { queries.add(q); return resultado; }
        @Override public List<String[]> buscar(String q) { return resultado; }
        @Override public List<String[]> local(String q) { return resultado; }
    }

    static final class PerfilesFalso implements ProfileService {
        final Map<Long, FichaPerfil> fichas = new java.util.HashMap<>();
        int fichaLlamadas;
        @Override public FichaPerfil ficha(long pid) { fichaLlamadas++; return fichas.get(pid); }
        @Override public FichaPerfil fichaConocida(long pid) { return null; }
        @Override public Integer elo1v1(long pid) { return null; }
        @Override public List<dev.tirador.aoe2radar.model.Perfil.Vinculada> vinculadas(long pid) { return List.of(); }
        @Override public List<dev.tirador.aoe2radar.model.Perfil.Vinculada> vinculadasConElo(long pid) { return List.of(); }
        @Override public List<dev.tirador.aoe2radar.model.Perfil.Vinculada> vinculadasConocidas(long pid) { return null; }
        @Override public Integer eloVinculada(long vid) { return null; }
        @Override public Map<Long, String> familia(long pid) { return null; }
        @Override public dev.tirador.aoe2radar.model.AnioSfr anioSfr(long pid, String nombreSiFalta) { return null; }
        @Override public dev.tirador.aoe2radar.model.Actividad actividad(long pid) { return null; }
        @Override public dev.tirador.aoe2radar.model.Actividad historial(long pid, String nombre, dev.tirador.aoe2radar.model.Actividad base, boolean mas, int maxPaginas,
                java.util.function.Consumer<dev.tirador.aoe2radar.model.Actividad> parcial, java.util.function.BooleanSupplier cancelar) { return null; }
        @Override public int traerHoy(long pid) { return 0; }
    }

    /** La Pantalla de mentira: guarda lo que el presentador le pide, como haría RatingsView. */
    static final class PantallaFalsa implements RatingsPresenter.Pantalla {
        final List<Comparado> comparados = new ArrayList<>();
        final java.util.Set<Long> seleccionEnWatchlist = new java.util.HashSet<>();
        String estado = "";
        int cargaIniciadaVeces, ocultarVeces;
        String errorCarga = "sin-llamar";
        boolean cargaTerminadaLlamada;
        List<String[]> sugerenciasMostradas;
        int refrescarVeces;

        @Override public List<Comparado> comparados() { return comparados; }
        @Override public void refrescarComparados() { refrescarVeces++; }
        @Override public void estado(String texto) { estado = texto; }
        @Override public boolean seleccionado(long pid) { return seleccionEnWatchlist.contains(pid); }
        @Override public String nombreVisible(long pid, String nombre) { return nombre; }
        @Override public void cargaIniciada() { cargaIniciadaVeces++; }
        @Override public void cargaTerminada(String error) { cargaTerminadaLlamada = true; errorCarga = error; }
        @Override public void mostrarSugerencias(List<String[]> resultados) { sugerenciasMostradas = resultados; }
        @Override public void ocultarSugerencias() { ocultarVeces++; }
    }

    final ServicioFalso servicio = new ServicioFalso();
    final BusquedaFalsa busqueda = new BusquedaFalsa();
    final PerfilesFalso perfiles = new PerfilesFalso();
    final PantallaFalsa pantalla = new PantallaFalsa();
    final RatingsPresenter presenter = new RatingsPresenter(servicio, busqueda, perfiles, Tareas.EN_LINEA, pantalla);

    // ----- carga -----------------------------------------------------------------

    @Test void carga_ok_avisa_inicio_y_fin_sin_error() {
        servicio.error = null;
        presenter.cargar();
        assertEquals(1, pantalla.cargaIniciadaVeces);
        assertTrue(pantalla.cargaTerminadaLlamada);
        assertNull(pantalla.errorCarga);
        assertFalse(servicio.cargando());   // se desmarca al terminar
    }

    @Test void carga_con_error_lo_pasa_a_la_pantalla() {
        servicio.error = "sin red";
        presenter.cargar();
        assertEquals("sin red", pantalla.errorCarga);
    }

    @Test void segunda_apertura_mientras_carga_no_relanza() {
        servicio.cargando = true;   // ya hay una carga en curso (marcada por otra apertura)
        presenter.cargar();
        assertEquals(0, servicio.asegurarLlamadas);
        assertEquals(0, pantalla.cargaIniciadaVeces);
    }

    // ----- sugerencias -------------------------------------------------------------

    @Test void sugerencias_se_descartan_si_la_query_cambio() {
        busqueda.resultado = List.<String[]>of(new String[]{ "1", "Fulano", "Fulano" });
        presenter.sugerir("fu", () -> "fulanote");   // el usuario siguió escribiendo antes de que volviera la respuesta
        assertNull(pantalla.sugerenciasMostradas);
        assertEquals(1, pantalla.ocultarVeces);
    }

    @Test void sugerencias_se_muestran_si_la_query_no_cambio() {
        busqueda.resultado = List.<String[]>of(new String[]{ "1", "Fulano", "Fulano" });
        presenter.sugerir("fu", () -> "fu");
        assertEquals(busqueda.resultado, pantalla.sugerenciasMostradas);
    }

    // ----- añadir comparado ---------------------------------------------------------

    @Test void anadir_ok_lo_deja_en_comparados() {
        perfiles.fichas.put(1L, new FichaPerfil(Map.of("rm_1v1", new int[]{ 1500, 100 }), "es", "", 200));
        presenter.anadir(1L, "Fulano", false);
        assertEquals(1, pantalla.comparados.size());
        assertEquals(1L, pantalla.comparados.get(0).pid());
        assertEquals(1, pantalla.refrescarVeces);
    }

    @Test void anadir_ya_presente_no_repite_ni_consulta() {
        pantalla.comparados.add(new Comparado(1L, "Fulano", Map.of(), "es", false));
        presenter.anadir(1L, "Fulano", false);
        assertEquals(1, pantalla.comparados.size());
        assertEquals(0, perfiles.fichaLlamadas);
    }

    @Test void anadir_deseleccionado_mientras_cargaba_no_se_agrega() {
        perfiles.fichas.put(1L, new FichaPerfil(Map.of("rm_1v1", new int[]{ 1500, 100 }), "es", "", 200));
        // deSeleccion=true y NO está en seleccionEnWatchlist: se deseleccionó mientras se consultaba la ficha
        presenter.anadir(1L, "Fulano", true);
        assertTrue(pantalla.comparados.isEmpty());
    }

    @Test void limite_de_comparados_rechaza_uno_nuevo_buscado_a_mano_si_esta_lleno_de_seleccionados() {
        for (long i = 1; i <= RatingsPresenter.MAX_COMPARADOS; i++)
            pantalla.comparados.add(new Comparado(i, "J" + i, Map.of("rm_1v1", new int[]{ 1000, 1 }), "es", true));
        perfiles.fichas.put(99L, new FichaPerfil(Map.of("rm_1v1", new int[]{ 1500, 100 }), "es", "", 200));
        presenter.anadir(99L, "Nuevo", false);
        assertEquals(RatingsPresenter.MAX_COMPARADOS, pantalla.comparados.size());
        assertTrue(pantalla.estado.contains(String.valueOf(RatingsPresenter.MAX_COMPARADOS)));
    }

    @Test void limite_de_comparados_quita_el_buscado_a_mano_mas_antiguo_para_dejar_sitio() {
        pantalla.comparados.add(new Comparado(1L, "Manual", Map.of("rm_1v1", new int[]{ 1000, 1 }), "es", false));
        for (long i = 2; i <= RatingsPresenter.MAX_COMPARADOS; i++)
            pantalla.comparados.add(new Comparado(i, "J" + i, Map.of("rm_1v1", new int[]{ 1000, 1 }), "es", true));
        perfiles.fichas.put(99L, new FichaPerfil(Map.of("rm_1v1", new int[]{ 1500, 100 }), "es", "", 200));
        presenter.anadir(99L, "Nuevo", false);
        assertEquals(RatingsPresenter.MAX_COMPARADOS, pantalla.comparados.size());
        assertTrue(pantalla.comparados.stream().noneMatch(c -> c.pid() == 1L));
        assertTrue(pantalla.comparados.stream().anyMatch(c -> c.pid() == 99L));
    }

    // ----- sincronización con la watchlist -------------------------------------------

    @Test void sincronizarSeleccion_anade_los_nuevos_seleccionados() {
        perfiles.fichas.put(5L, new FichaPerfil(Map.of("rm_1v1", new int[]{ 1200, 10 }), "es", "", 5));
        pantalla.seleccionEnWatchlist.add(5L);
        presenter.sincronizarSeleccion(List.of(new Player(5L, "Cinco", "")));
        assertEquals(1, pantalla.comparados.size());
        assertTrue(pantalla.comparados.get(0).deSeleccion());
    }

    @Test void sincronizarSeleccion_quita_los_deseleccionados() {
        pantalla.comparados.add(new Comparado(5L, "Cinco", Map.of(), "es", true));
        presenter.sincronizarSeleccion(List.of());   // ya no hay nadie seleccionado
        assertTrue(pantalla.comparados.isEmpty());
        assertEquals(1, pantalla.refrescarVeces);
    }

    @Test void topDe_usa_percentil_por_rango_solo_si_no_es_solo_activos() {
        Comparado c = new Comparado(1L, "Uno", Map.of("rm_1v1", new int[]{ 1500, 0 }), "es", false);
        // rank 0 -> percentilRango no aplica; cae al percentil por rating, que sin datos de hist es null
        assertNull(presenter.topDe(c, "rm_1v1", false));
    }
}
