package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.LadderHist;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.Rejilla;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.RatingsService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PerfilPresenter con Tareas.EN_LINEA (sin hilos) y dobles de ProfileService/RatingsService/BusquedaPerfiles y de
 * la Pantalla: comprueba las dos rutas de abrirPerfil (sfr-data y API con progreso parcial), «Actualizar hoy»,
 * «Cargar más», las sugerencias de los dos buscadores, las vinculadas y el filtro «clan» del cara a cara — todas
 * con su comprobación de «respuesta caducada» (el pid abierto cambió mientras se esperaba).
 */
class PerfilPresenterTest {

    /** Un ProfileService de mentira: solo lo que usa el presentador. */
    static class PerfilesFalso implements ProfileService {
        FichaPerfil ficha;
        AnioSfr anioSfr;
        Exception anioSfrFalla;
        Actividad historialResultado;
        Exception historialFalla;
        int traerHoyResultado;
        Exception traerHoyFalla;
        Consumer<Actividad> ultimoParcial;

        @Override public FichaPerfil ficha(long pid) { return ficha; }
        @Override public FichaPerfil fichaConocida(long pid) { return ficha; }
        @Override public Integer elo1v1(long pid) { return null; }
        @Override public List<Perfil.Vinculada> vinculadas(long pid) { return List.of(); }
        @Override public List<Perfil.Vinculada> vinculadasConElo(long pid) { return List.of(); }
        @Override public List<Perfil.Vinculada> vinculadasConocidas(long pid) { return null; }
        @Override public Integer eloVinculada(long vid) { return null; }
        @Override public Map<Long, String> familia(long pid) { return null; }
        @Override public AnioSfr anioSfr(long pid, String nombreSiFalta) throws Exception { if (anioSfrFalla != null) throw anioSfrFalla; return anioSfr; }
        @Override public Actividad actividad(long pid) { return null; }
        @Override public Actividad historial(long pid, String nombre, Actividad base, boolean mas, int maxPaginas, Consumer<Actividad> parcial, BooleanSupplier cancelar) throws java.io.IOException {
            ultimoParcial = parcial;
            if (historialFalla != null) { if (historialFalla instanceof java.io.IOException io) throw io; throw new RuntimeException(historialFalla); }
            return historialResultado;
        }
        @Override public int traerHoy(long pid) throws java.io.IOException { if (traerHoyFalla != null) { if (traerHoyFalla instanceof java.io.IOException io) throw io; throw new RuntimeException(traerHoyFalla); } return traerHoyResultado; }
    }

    static class RatingsFalso implements RatingsService {
        int asegurarLlamadas;
        String asegurarError;
        @Override public String asegurar(boolean forzar) { asegurarLlamadas++; return asegurarError; }
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
        List<String[]> resultado = List.of();
        @Override public List<String[]> sugerir(String q) { return resultado; }
        @Override public List<String[]> buscar(String q) { return resultado; }
        @Override public List<String[]> local(String q) { return resultado; }
    }

    /** La Pantalla de mentira: guarda lo que el presentador le pide, como haría PerfilView. */
    static class PantallaFalsa implements PerfilPresenter.Pantalla {
        long pidAbierto;
        boolean cargando;
        int cargaIniciadaVeces;
        FichaPerfil cabeceraPintada;
        Actividad desdeSfrPintada; String desdeSfrHasta;
        Actividad parcialPintada; int parcialMax;
        Actividad completadaPintada;
        String errorCarga;
        int hoyIniciadoVeces;
        FichaPerfil hoyFicha; int hoyNuevas;
        String hoyError;
        Actividad masParcial; int masMax;
        Actividad masCompletado;
        String masError;
        List<String[]> sugerenciasBuscador; String sugerenciasBuscadorQuery;
        List<String[]> sugerenciasH2h; String sugerenciasH2hQuery;
        int vinculadasListasVeces;
        Set<Long> conjuntoIds; String conjuntoNombre;

        @Override public long pidAbierto() { return pidAbierto; }
        @Override public boolean cargando() { return cargando; }
        @Override public void cargando(boolean v) { cargando = v; }
        @Override public void cargaIniciada() { cargaIniciadaVeces++; }
        @Override public void cabecera(FichaPerfil ficha) { cabeceraPintada = ficha; }
        @Override public void desdeSfr(Actividad a, String hastaSfr) { desdeSfrPintada = a; desdeSfrHasta = hastaSfr; }
        @Override public void progresoParcial(Actividad parcialA, int maxPaginas) { parcialPintada = parcialA; parcialMax = maxPaginas; }
        @Override public void cargaCompletada(Actividad a) { completadaPintada = a; }
        @Override public void errorCarga(String mensaje) { errorCarga = mensaje; }
        @Override public void hoyIniciado() { hoyIniciadoVeces++; }
        @Override public void hoyTerminado(FichaPerfil ficha, int nuevas) { hoyFicha = ficha; hoyNuevas = nuevas; }
        @Override public void hoyError(String mensaje) { hoyError = mensaje; }
        @Override public void masProgreso(Actividad parcialA, int maxPaginas) { masParcial = parcialA; masMax = maxPaginas; }
        @Override public void masCompletado(Actividad a) { masCompletado = a; }
        @Override public void masError(String mensaje) { masError = mensaje; }
        @Override public void sugerenciasBuscador(List<String[]> resultados, String query) { sugerenciasBuscador = resultados; sugerenciasBuscadorQuery = query; }
        @Override public void sugerenciasCaraACara(List<String[]> resultados, String query) { sugerenciasH2h = resultados; sugerenciasH2hQuery = query; }
        @Override public void vinculadasListas() { vinculadasListasVeces++; }
        @Override public void conjuntoClanListo(Set<Long> ids, String nombre) { conjuntoIds = ids; conjuntoNombre = nombre; }
    }

    final PerfilesFalso perfiles = new PerfilesFalso();
    final RatingsFalso ratings = new RatingsFalso();
    final BusquedaFalsa busqueda = new BusquedaFalsa();
    final PantallaFalsa pantalla = new PantallaFalsa();
    final Map<Long, Integer> eloWatch = new HashMap<>();
    final PerfilPresenter presenter = new PerfilPresenter(perfiles, ratings, busqueda, Tareas.EN_LINEA, eloWatch, pantalla);

    private Actividad actividad(long pid, String nombre, List<Match> partidas) { return new Actividad(pid, nombre, partidas, true, 1, 1_700_000_000_000L); }

    // ----- cargar: sfr-data ----------------------------------------------------------------

    @Test void cargar_desde_sfr_data_pinta_cabecera_y_cuerpo_completo() {
        pantalla.pidAbierto = 5L;
        perfiles.ficha = new FichaPerfil(Map.of("rm_1v1", new int[]{ 1500, 10 }), "es", "", 20);
        perfiles.anioSfr = new AnioSfr(actividad(5L, "Fulano", List.of()), "2026-09-24", "es");
        presenter.cargar(5L, "Fulano", null, false);
        assertEquals(1, ratings.asegurarLlamadas);   // se asegura el ladder para el Top %
        assertSame(perfiles.ficha, pantalla.cabeceraPintada);
        assertNotNull(pantalla.desdeSfrPintada);
        assertEquals("2026-09-24", pantalla.desdeSfrHasta);
        assertFalse(pantalla.cargando);   // se desmarca antes de pintar, como el resto de presentadores
    }

    @Test void cargar_desde_sfr_data_descartada_si_el_pid_abierto_cambio() {
        pantalla.pidAbierto = 999L;   // mientras tanto se abrió otro perfil
        perfiles.anioSfr = new AnioSfr(actividad(5L, "Fulano", List.of()), "2026-09-24", "es");
        presenter.cargar(5L, "Fulano", null, false);
        assertNull(pantalla.desdeSfrPintada);
        assertNull(pantalla.cabeceraPintada);   // ni la cabecera se pinta: la comprobación es antes de cabecera()
    }

    // ----- cargar: API con progreso parcial -------------------------------------------------

    @Test void cargar_por_api_pinta_progreso_parcial_y_termina() {
        pantalla.pidAbierto = 7L;
        perfiles.anioSfr = null;   // fuera del alcance de sfr-data
        perfiles.ficha = new FichaPerfil(Map.of(), "es", "", 0);
        Actividad parcial = actividad(7L, "Zutano", List.of());
        Actividad completa = actividad(7L, "Zutano", List.of());
        perfiles.historialResultado = completa;
        presenter.cargar(7L, "Zutano", null, false);
        // el presentador guardó el consumer de progreso: lo disparamos a mano, como haría ProfileService.historial de verdad
        perfiles.ultimoParcial.accept(parcial);
        assertSame(parcial, pantalla.parcialPintada);
        assertSame(completa, pantalla.completadaPintada);
    }

    @Test void cargar_por_api_progreso_parcial_se_descarta_si_el_pid_cambio() {
        pantalla.pidAbierto = 7L;
        perfiles.anioSfr = null;
        perfiles.ficha = null;
        perfiles.historialResultado = actividad(7L, "Zutano", List.of());
        presenter.cargar(7L, "Zutano", null, false);
        pantalla.pidAbierto = 8L;   // se navegó a otro perfil antes de que llegara el progreso
        perfiles.ultimoParcial.accept(actividad(7L, "Zutano", List.of()));
        assertNull(pantalla.parcialPintada);
    }

    @Test void cargar_error_pasa_el_mensaje_a_la_pantalla_y_desmarca_cargando() {
        pantalla.pidAbierto = 9L;
        perfiles.anioSfrFalla = null;
        perfiles.anioSfr = null;
        perfiles.historialFalla = new java.io.IOException("sin red");
        presenter.cargar(9L, "Mengano", null, false);
        assertFalse(pantalla.cargando);
        assertNotNull(pantalla.errorCarga);
        assertTrue(pantalla.errorCarga.contains("sin red"));
    }

    // ----- actualizar hoy --------------------------------------------------------------------

    @Test void actualizar_hoy_ok_avisa_inicio_y_pinta_lo_nuevo() {
        pantalla.pidAbierto = 5L;
        perfiles.ficha = new FichaPerfil(Map.of(), "es", "", 0);
        perfiles.traerHoyResultado = 3;
        presenter.actualizarHoy(5L);
        assertEquals(1, pantalla.hoyIniciadoVeces);
        assertEquals(3, pantalla.hoyNuevas);
        assertSame(perfiles.ficha, pantalla.hoyFicha);
    }

    @Test void actualizar_hoy_descartado_si_el_pid_abierto_ya_no_es_ese() {
        pantalla.pidAbierto = 6L;   // se navegó a otro perfil antes de que volviera la respuesta
        perfiles.ficha = new FichaPerfil(Map.of(), "es", "", 0);
        perfiles.traerHoyResultado = 3;
        presenter.actualizarHoy(5L);
        assertEquals(1, pantalla.hoyIniciadoVeces);   // el aviso de «actualizando…» sí se ve (es inmediato, en el hilo que llama)
        assertNull(pantalla.hoyFicha);                // pero el resultado no se pinta
        assertEquals(0, pantalla.hoyNuevas);
    }

    @Test void actualizar_hoy_error_pasa_el_mensaje() {
        pantalla.pidAbierto = 5L;
        perfiles.traerHoyFalla = new java.io.IOException("caído");
        presenter.actualizarHoy(5L);
        assertNotNull(pantalla.hoyError);
        assertTrue(pantalla.hoyError.contains("caído"));
    }

    // ----- cargar más ------------------------------------------------------------------------

    @Test void cargar_mas_ok_pinta_progreso_y_termina() {
        pantalla.pidAbierto = 5L;
        Actividad base = actividad(5L, "Fulano", List.of());
        Actividad completo = actividad(5L, "Fulano", List.of());
        perfiles.historialResultado = completo;
        presenter.cargarMas(5L, "Fulano", base, 4);
        perfiles.ultimoParcial.accept(actividad(5L, "Fulano", List.of()));
        assertNotNull(pantalla.masParcial);
        assertSame(completo, pantalla.masCompletado);
        assertFalse(pantalla.cargando);
    }

    @Test void cargar_mas_error_pasa_el_mensaje() {
        pantalla.pidAbierto = 5L;
        perfiles.historialFalla = new java.io.IOException("timeout");
        presenter.cargarMas(5L, "Fulano", actividad(5L, "Fulano", List.of()), 4);
        assertNotNull(pantalla.masError);
        assertTrue(pantalla.masError.contains("timeout"));
    }

    // ----- sugerencias -------------------------------------------------------------------------

    @Test void sugerir_buscador_pasa_los_resultados_y_la_query() {
        busqueda.resultado = List.<String[]>of(new String[]{ "1", "Fulano", "Fulano" });
        presenter.sugerirBuscador("ful");
        assertEquals(busqueda.resultado, pantalla.sugerenciasBuscador);
        assertEquals("ful", pantalla.sugerenciasBuscadorQuery);
    }

    @Test void sugerir_cara_a_cara_pasa_los_resultados_y_la_query() {
        busqueda.resultado = List.<String[]>of(new String[]{ "2", "Zutano", "Zutano" });
        presenter.sugerirCaraACara("zu");
        assertEquals(busqueda.resultado, pantalla.sugerenciasH2h);
        assertEquals("zu", pantalla.sugerenciasH2hQuery);
    }

    // ----- vinculadas y conjunto clan ------------------------------------------------------------

    @Test void pedir_vinculadas_avisa_a_la_pantalla_si_el_pid_sigue_abierto() {
        pantalla.pidAbierto = 5L;
        presenter.pedirVinculadas(5L);
        assertEquals(1, pantalla.vinculadasListasVeces);
    }

    @Test void pedir_vinculadas_no_avisa_si_el_pid_ya_no_es_el_abierto() {
        pantalla.pidAbierto = 6L;
        presenter.pedirVinculadas(5L);
        assertEquals(0, pantalla.vinculadasListasVeces);
    }

    @Test void resolver_conjunto_clan_ok_devuelve_los_miembros() {
        ratings.asegurarError = null;   // asegurar() va bien
        presenter.resolverConjuntoClan("TSK", "Clan TSK");
        assertNotNull(pantalla.conjuntoIds);   // vacío o no: lo importante es que se avisó a la pantalla
        assertEquals("Clan TSK", pantalla.conjuntoNombre);
    }

    @Test void resolver_conjunto_clan_con_ladder_caido_devuelve_vacio() {
        ratings.asegurarError = "sin red";   // asegurar() falla: no se listan miembros (ver ConsultasLadder.miembrosClan)
        presenter.resolverConjuntoClan("TSK", "Clan TSK");
        assertTrue(pantalla.conjuntoIds.isEmpty());
    }

    /** Ficha de cabecera sin llamada (perfilSintetico de la 1.1): usa el ELO de la watchlist si no hay ninguno en las partidas. */
    @Test void sintetico_usa_el_elo_de_la_watchlist_cuando_no_hay_partidas_1v1() {
        eloWatch.put(5L, 1234);
        FichaPerfil f = presenter.sintetico(5L, actividad(5L, "Fulano", List.of()), "es");
        assertEquals(1234, f.ladders().get("rm_1v1")[0]);
    }
}
