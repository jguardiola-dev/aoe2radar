package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.ProfileService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CaraACaraPresenter con Tareas.EN_LINEA: sugerencias del buscador de rival (se descartan si la query cambió,
 * igual que en Ratings), el año del rival para «Cada uno por su lado» (se descarta si el cruce ya no está
 * abierto) y la ficha de un jugador desconocido (sin comprobación de caducidad: pinta siempre, como la 1.1).
 */
class CaraACaraPresenterTest {

    static class PerfilesFalso implements ProfileService {
        AnioSfr anioSfr;
        FichaPerfil ficha;
        int anioSfrLlamadas;
        @Override public FichaPerfil ficha(long pid) { return ficha; }
        @Override public FichaPerfil fichaConocida(long pid) { return ficha; }
        @Override public Integer elo1v1(long pid) { return null; }
        @Override public List<Perfil.Vinculada> vinculadas(long pid) { return List.of(); }
        @Override public List<Perfil.Vinculada> vinculadasConElo(long pid) { return List.of(); }
        @Override public List<Perfil.Vinculada> vinculadasConocidas(long pid) { return null; }
        @Override public Integer eloVinculada(long vid) { return null; }
        @Override public Map<Long, String> familia(long pid) { return null; }
        @Override public AnioSfr anioSfr(long pid, String nombreSiFalta) { anioSfrLlamadas++; return anioSfr; }
        @Override public Actividad actividad(long pid) { return null; }
        @Override public Actividad historial(long pid, String nombre, Actividad base, boolean mas, int maxPaginas, Consumer<Actividad> parcial, BooleanSupplier cancelar) { return null; }
        @Override public int traerHoy(long pid) { return 0; }
    }

    static class BusquedaFalsa implements BusquedaPerfiles {
        List<String[]> resultado = List.of();
        @Override public List<String[]> sugerir(String q) { return resultado; }
        @Override public List<String[]> buscar(String q) { return resultado; }
        @Override public List<String[]> local(String q) { return resultado; }
    }

    final PerfilesFalso perfiles = new PerfilesFalso();
    final BusquedaFalsa busqueda = new BusquedaFalsa();
    final Map<Long, Actividad> actividadCache = new HashMap<>();
    final CaraACaraPresenter presenter = new CaraACaraPresenter(perfiles, busqueda, Tareas.EN_LINEA, actividadCache);

    @Test void sugerir_pinta_si_la_query_no_cambio() {
        busqueda.resultado = List.<String[]>of(new String[]{ "1", "Fulano", "Fulano" });
        List<String[]>[] pintado = new List[1];
        presenter.sugerir("fu", () -> "fu", res -> pintado[0] = res);
        assertEquals(busqueda.resultado, pintado[0]);
    }

    @Test void sugerir_se_descarta_si_la_query_cambio() {
        busqueda.resultado = List.<String[]>of(new String[]{ "1", "Fulano", "Fulano" });
        List<String[]>[] pintado = new List[1];
        presenter.sugerir("fu", () -> "fulanote", res -> pintado[0] = res);
        assertNull(pintado[0]);
    }

    @Test void pedir_anio_rival_pinta_si_el_cruce_sigue_abierto() {
        Actividad a = new Actividad(9L, "Rival", List.of(), true, 1, 1L);
        perfiles.anioSfr = new AnioSfr(a, "2026-09-24", "es");
        Actividad[] pintado = new Actividad[1];
        presenter.pedirAnioRival(9L, "Yo", () -> true, arF -> pintado[0] = arF);
        assertSame(a, pintado[0]);
    }

    @Test void pedir_anio_rival_no_pinta_si_el_cruce_ya_no_es_ese() {
        perfiles.anioSfr = new AnioSfr(new Actividad(9L, "Rival", List.of(), true, 1, 1L), "2026-09-24", "es");
        boolean[] llamado = { false };
        presenter.pedirAnioRival(9L, "Yo", () -> false, arF -> llamado[0] = true);
        assertFalse(llamado[0]);
    }

    @Test void pedir_anio_rival_sin_sfr_data_pinta_null() {
        perfiles.anioSfr = null;
        Actividad[] pintado = { new Actividad(0, "", List.of(), false, 0, 0) };
        presenter.pedirAnioRival(9L, "Yo", () -> true, arF -> pintado[0] = arF);
        assertNull(pintado[0]);
    }

    /** B4: si el rival ya tiene actividad en la caché (p. ej. porque tiene su propio perfil abierto), se usa
     *  esa y no se llama a anioSfr (misma condición exacta que la 1.1: {@code if (ar == null) { ... }}). */
    @Test void pedir_anio_rival_usa_la_cache_si_ya_esta_y_no_llama_a_anioSfr() {
        Actividad enCache = new Actividad(9L, "Rival", List.of(), true, 1, 1L);
        actividadCache.put(9L, enCache);
        perfiles.anioSfr = new AnioSfr(new Actividad(9L, "Rival", List.of(), true, 2, 2L), "2026-09-24", "es");   // si se llamara, pintaría OTRA actividad
        Actividad[] pintado = new Actividad[1];
        presenter.pedirAnioRival(9L, "Yo", () -> true, arF -> pintado[0] = arF);
        assertSame(enCache, pintado[0]);
        assertEquals(0, perfiles.anioSfrLlamadas);
    }

    /** B4: si no está en caché, se pide a sfr-data y el resultado se deja en la caché (para la próxima vez). */
    @Test void pedir_anio_rival_sin_cache_pide_a_sfr_data_y_la_rellena() {
        Actividad a = new Actividad(9L, "Rival", List.of(), true, 1, 1L);
        perfiles.anioSfr = new AnioSfr(a, "2026-09-24", "es");
        Actividad[] pintado = new Actividad[1];
        presenter.pedirAnioRival(9L, "Yo", () -> true, arF -> pintado[0] = arF);
        assertSame(a, pintado[0]);
        assertEquals(1, perfiles.anioSfrLlamadas);
        assertSame(a, actividadCache.get(9L));
    }

    @Test void pedir_ficha_pinta_siempre_sin_comprobar_caducidad() {
        FichaPerfil f = new FichaPerfil(Map.of("rm_1v1", new int[]{ 1500, 10 }), "es", "", 5);
        perfiles.ficha = f;
        FichaPerfil[] pintado = new FichaPerfil[1];
        presenter.pedirFicha(3L, p -> pintado[0] = p);
        assertSame(f, pintado[0]);
    }
}
