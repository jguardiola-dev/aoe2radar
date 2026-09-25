package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.service.NombresStats;
import dev.tirador.aoe2radar.service.StatsService;

import java.util.Locale;

import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * Lo que Civ Stats pide en segundo plano: el resumen de la ventana elegida (hilo "civstats-datos") y, si hace
 * falta, la ventana «parche» para la tendencia (hilo "civstats-parche"). Sin Swing: recibe un {@link Tareas}
 * para no crear hilos él mismo y una {@link Pantalla} pequeña para avisar a la vista de lo que pasó. La vista
 * (CivStatsView) hace el resto: pintar tablas, gráficas y combos con los datos ya cargados (eso no es red).
 */
public final class CivStatsPresenter {

    private final StatsService stats;
    private final Tareas tareas;
    private final Pantalla pantalla;
    private boolean cargando;

    public CivStatsPresenter(StatsService stats, Tareas tareas, Pantalla pantalla) {
        this.stats = stats;
        this.tareas = tareas;
        this.pantalla = pantalla;
    }

    /** Pide (si no hay ya una carga en curso) el resumen de la ventana y vuelve al EDT con el resultado. */
    public void cargar(String ventana) {
        if (cargando) return;
        cargando = true;
        pantalla.mostrarEstado(t("Descargando el resumen de ", "Downloading the summary for ") + NombresStats.ventanaNombre(ventana).toLowerCase(Locale.ROOT) + "…");
        tareas.enFondo("civstats-datos", () -> {
            String err = stats.asegurar(ventana, true);
            tareas.enUi(() -> {
                cargando = false;
                if (err != null) { pantalla.mostrarEstado(t("No se pudieron cargar las estadísticas: ", "Couldn't load the statistics: ") + err); return; }
                pantalla.datosListos();
            });
        });
    }

    /** La ventana «parche» hace falta para «Parche actual» en la tendencia: se pide una vez, sin bloquear. */
    public void cargarParcheSiHaceFalta() {
        if (stats.tieneVentana("parche")) { pantalla.repintarTendencias(); return; }
        tareas.enFondo("civstats-parche", () -> {
            stats.asegurar("parche", false);
            tareas.enUi(pantalla::repintarTendencias);
        });
    }

    /** Lo que el presentador necesita pintar en la vista, sin conocer Swing. */
    public interface Pantalla {
        /** Mensaje de la barra de estado de Civ Stats (descargando, error). */
        void mostrarEstado(String texto);
        /** El resumen ya está: repinta tabla, tarjetas, tendencias y matriz con los filtros actuales. */
        void datosListos();
        /** Repinta la gráfica de tendencias (tras cargar el parche, o si ya estaba cargado). */
        void repintarTendencias();
    }
}
