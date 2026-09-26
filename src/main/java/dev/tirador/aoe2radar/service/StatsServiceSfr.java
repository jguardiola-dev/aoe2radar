package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.sfrdata.CivStats;
import dev.tirador.aoe2radar.sfrdata.SfrDataClient;

import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;

/**
 * StatsService sobre sfr-data: el cliente que baja los archivos (SfrDataClient) entra por el constructor, como
 * en EloNocturno y PerfilesSfr, para poder probarlo con una release falsa y sin red (ver StatsServiceTest).
 * La ventana y las tendencias quedan en CivStats (estático, compartido con la UI y el harness de capturas: ver
 * la clase); este servicio delega en CivStats.statsAsegurar, que ya sabe si hace falta bajarlas o no.
 */
public final class StatsServiceSfr implements StatsService {
    private final SfrDataClient sfr;

    public StatsServiceSfr(SfrDataClient sfr) { this.sfr = sfr; }

    @Override public String asegurar(String ventana, boolean conTendencias) {
        avisarSiUi("StatsService.asegurar");
        return CivStats.statsAsegurar(sfr, ventana, conTendencias);
    }

    @Override public VentanaStats ventana(String clave) { return CivStats.VENTANAS_STATS.get(clave); }

    @Override public boolean tieneVentana(String clave) { return CivStats.VENTANAS_STATS.containsKey(clave); }

    // clone(): copia defensiva (fila 114 de DEUDA). Antes se entregaba el array estático de CivStats tal cual;
    // quien lo recibiera podía mutarlo y corromper el estado compartido con la UI y con otras sesiones de test.
    @Override public String[] modos() { return CivStats.MODOS_STATS.clone(); }

    @Override public String[] clavesVentanas() { return CivStats.VENTANAS_STATS_KEYS.clone(); }

    @Override public Tendencias tendencias() { return CivStats.tendenciasStats; }

    @Override public boolean tramoEnRango(String tramo, List<String> tramos, String rango) {
        return CalculoStats.tramoEnRango(tramo, tramos, rango);
    }

    @Override public double[] wilson(int w, int n) { return CalculoStats.wilson(w, n); }

    @Override public Map<String, CivAgg> agregarCivs(VentanaStats v, String modo, String mapa, String tramo) {
        return CalculoStats.agregarCivs(v, modo, mapa, tramo);
    }

    @Override public Map<String, Integer> partidasPorMapa(VentanaStats v, String modo, String tramo) {
        return CalculoStats.partidasPorMapa(v, modo, tramo);
    }

    @Override public Map<String, CivAgg> civPorMapa(VentanaStats v, String modo, String tramo, String civ) {
        return CalculoStats.civPorMapa(v, modo, tramo, civ);
    }

    @Override public String duracionMedia(long segundos, int n) { return CalculoStats.duracionMedia(segundos, n); }
}
