package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PartidasPresenter es puro (sin Swing, sin hilos): estas pruebas llaman a sus métodos estáticos directamente,
 * sin Tareas.EN_LINEA ni dobles de servicio, porque no hay ninguna operación asíncrona que orquestar (fetchMatches/
 * download/buscarAleatorias/buscarGte siguen siendo SwingWorker en PartidasView, y son ellos —no este
 * presentador— quienes deciden qué hacer con el error o la cancelación). Cubre lo que el presentador SÍ hace:
 * vigente() (la comprobación de caducidad por opSerial que usan fetchMatches/download/azar/GTE al terminar),
 * los tres filtros de la tabla y el ojo de revelar.
 */
class PartidasPresenterTest {

    /** Hora fija (nunca Instant.now(): un test con la hora real es un test que a veces falla solo). */
    private static final Instant AHORA = Instant.ofEpochMilli(1_700_000_000_000L);

    // ----- vigente(): la comprobación de caducidad por opSerial que usan fetchMatches/download/azar/GTE -----

    @Test void download_vigente() {
        assertTrue(PartidasPresenter.vigente(3, 3));
    }

    @Test void download_caducada() {
        assertFalse(PartidasPresenter.vigente(3, 4));
    }

    // ----- revelar -----

    @Test void revelar_modoConsultaMuestraPartidaNormal() {
        assertTrue(PartidasPresenter.revelada(true, 0, false));
    }

    @Test void revelar_modoConsultaNoDestapaGte() {
        // el modo consulta global nunca revela Guess the ELO (haría trampa): gte > 0
        assertFalse(PartidasPresenter.revelada(true, 3, false));
    }

    @Test void revelar_manualEnReveladasSinModoConsulta() {
        assertTrue(PartidasPresenter.revelada(false, 0, true));
    }

    @Test void revelar_ninguna() {
        assertFalse(PartidasPresenter.revelada(false, 0, false));
    }

    // ----- filtros: modo -----

    @Test void filtroModo_todosPasaCualquierPartida() {
        assertTrue(PartidasPresenter.pasaFiltroModo("Todos los modos", "Todos los modos", "RM 1v1"));
    }

    @Test void filtroModo_sinSeleccionPasaCualquierPartida() {
        assertTrue(PartidasPresenter.pasaFiltroModo(null, "Todos los modos", "RM 1v1"));
    }

    @Test void filtroModo_coincideExacto() {
        assertTrue(PartidasPresenter.pasaFiltroModo("RM 1v1", "Todos los modos", "RM 1v1"));
    }

    @Test void filtroModo_noCoincide() {
        assertFalse(PartidasPresenter.pasaFiltroModo("RM Team", "Todos los modos", "RM 1v1"));
    }

    // ----- filtros: mapa -----

    @Test void filtroMapa_indiceCeroPasaCualquiera() {
        assertTrue(PartidasPresenter.pasaFiltroMapa(0, "Arabia", "Nómada"));
    }

    @Test void filtroMapa_coincide() {
        assertTrue(PartidasPresenter.pasaFiltroMapa(2, "Arabia", "Arabia"));
    }

    @Test void filtroMapa_noCoincide() {
        assertFalse(PartidasPresenter.pasaFiltroMapa(2, "Arabia", "Nómada"));
    }

    // ----- filtros: periodo -----

    @Test void filtroPeriodo_indiceCeroPasaAunSinFecha() {
        assertTrue(PartidasPresenter.pasaFiltroPeriodo(0, null, AHORA));
    }

    @Test void filtroPeriodo_dentroDeLaVentana() {
        Instant ahora = Instant.parse("2024-06-01T00:00:00Z");
        Instant inicio = ahora.minusSeconds(3600);   // hace 1 hora: dentro de "7 días" (índice 1)
        assertTrue(PartidasPresenter.pasaFiltroPeriodo(1, inicio, ahora));
    }

    @Test void filtroPeriodo_fueraDeLaVentana() {
        Instant ahora = Instant.parse("2024-06-01T00:00:00Z");
        Instant inicio = ahora.minusSeconds(30L * 24 * 3600);   // hace 30 días: fuera de "7 días" (índice 1)
        assertFalse(PartidasPresenter.pasaFiltroPeriodo(1, inicio, ahora));
    }

    @Test void filtroPeriodo_sinFechaDeInicioQuedaFuera() {
        assertFalse(PartidasPresenter.pasaFiltroPeriodo(1, null, AHORA));
    }
}
