package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.LadderHist;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Rejilla;
import dev.tirador.aoe2radar.sfrdata.Ladder;

import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;

/**
 * RatingsService sobre sfr-data: delega en sfrdata.Ladder, que guarda las campanas, la dispersión y los clanes en
 * estáticos compartidos con la UI y el harness de capturas (por eso no se duplican aquí, igual que StatsServiceSfr
 * con CivStats). Sin dependencias que inyectar: SISTEMA es la única instancia que hace falta.
 */
public final class RatingsServiceSfr implements RatingsService {
    public static final RatingsServiceSfr SISTEMA = new RatingsServiceSfr();

    public RatingsServiceSfr() {}

    @Override public String asegurar(boolean forzar) {
        avisarSiUi("RatingsService.asegurar");
        return Ladder.ladderAsegurar(forzar);
    }

    @Override public boolean cargando() { return Ladder.ladderCargando; }

    @Override public void cargando(boolean v) { Ladder.ladderCargando = v; }

    @Override public String progreso() { return Ladder.ladderProgreso; }

    @Override public LadderHist hist(String lb, boolean activos) { return Ladder.hist(lb, activos); }

    @Override public boolean tieneActivos(String lb) { return Ladder.ladderHistsActivos.containsKey(lb); }

    @Override public Rejilla dispersion(String familia, boolean activos) {
        Rejilla r = activos ? Ladder.dispersionActivos.get(familia) : null;
        return r != null ? r : Ladder.dispersionTodos.get(familia);
    }

    @Override public boolean dispersionTieneActivos(String familia) { return Ladder.dispersionActivos.containsKey(familia); }

    @Override public Map<String, List<LadderRow>> clanes() { return Ladder.clanes; }

    @Override public int activosMinPartidas() { return Ladder.activosMinPartidas; }

    @Override public int activosDias() { return Ladder.activosDias; }

    @Override public String generado() { return Ladder.ladderGenerado; }
}
