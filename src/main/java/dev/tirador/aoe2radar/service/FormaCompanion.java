package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.sfrdata.EloNocturno;
import dev.tirador.aoe2radar.util.Reloj;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;
import static dev.tirador.aoe2radar.util.Json.firstNonNull;

/** FormService con la API del companion y el ELO nocturno de sfr-data, tal como calculaba la 1.1 (calcFormaSerie,
 * formaPorResta y la regla de los 10 min de cargarForma). */
public final class FormaCompanion implements FormService {
    private final CompanionApi api;
    private final EloNocturno eloNocturno;
    private final Reloj reloj;

    public FormaCompanion(CompanionApi api, EloNocturno eloNocturno, Reloj reloj) {
        this.api = api; this.eloNocturno = eloNocturno; this.reloj = reloj;
    }

    @Override
    public Forma[] porResta(long pid, LongFunction<Integer> eloActual, LongFunction<Integer> partidasActual) {
        int[] ayer = eloNocturno.ayer.get(pid);
        if (ayer == null) return null;   // sin snapshot de ayer no hace falta leer eloActual (perezoso, como la 1.1)
        Integer ahora = null, partidasAhora = null;
        Integer e = eloActual.apply(pid); if (e != null && e > 0) { ahora = e; partidasAhora = partidasActual.apply(pid); }
        if (ahora == null) return null;
        Forma f24 = new Forma(ahora - ayer[0], 0, 0, 0, false,
                partidasAhora == null || ayer[1] <= 0 ? (ahora != ayer[0] ? 1 : 0) : Math.max(0, partidasAhora - ayer[1]));
        int[] h7 = eloNocturno.hace7.get(pid);
        Forma f7 = h7 == null ? null : new Forma(ahora - h7[0], 0, 0, 0, false,
                partidasAhora == null || h7[1] <= 0 ? (ahora != h7[0] ? 1 : 0) : Math.max(0, partidasAhora - h7[1]));
        return new Forma[]{ f24, f7 };
    }

    @Override
    public Forma[] porSerie(long pid) throws Exception {
        avisarSiUi("FormService.porSerie");
        Perfil pf = api.perfil(pid);
        List<Object[]> serie = new ArrayList<>();   // {epochMs, rating, ratingDiff|null}
        for (Perfil.Serie lb : pf.series()) {
            String lbId = String.valueOf(firstNonNull(lb.id(), ""));
            if (!lbId.equals("rm_1v1") && !lbId.equals("3")) continue;
            for (Perfil.Punto pt : lb.puntos()) {
                Instant d = pt.fecha();
                if (d == null || pt.rating() == null) continue;
                serie.add(new Object[]{ d.toEpochMilli(), pt.rating(), pt.diff() });
            }
        }
        if (serie.isEmpty()) return null;
        serie.sort((a, b) -> Long.compare((Long) b[0], (Long) a[0]));   // de la más reciente a la más antigua
        Forma[] out = new Forma[2];
        int[] horasV = { 24, 24 * 7 };
        for (int k = 0; k < 2; k++) {
            long desde = reloj.ahoraMs() - horasV[k] * 3_600_000L;
            int w = 0, l = 0, partidas = 0, racha = 0; Boolean rachaGana = null; boolean rachaViva = true;
            Integer ratingAhora = (Integer) serie.get(0)[1], ratingAntes = null;
            for (Object[] pt : serie) {
                if ((Long) pt[0] < desde) { ratingAntes = (Integer) pt[1]; break; }
                partidas++;
                Integer df = (Integer) pt[2];
                if (df != null) {
                    boolean gano = df > 0;
                    if (gano) w++; else l++;
                    if (rachaViva) {
                        if (rachaGana == null) { rachaGana = gano; racha = 1; }
                        else if (rachaGana == gano) racha++;
                        else rachaViva = false;
                    }
                }
            }
            int diff = partidas == 0 ? 0 : ratingAhora - (ratingAntes != null ? ratingAntes : (Integer) serie.get(serie.size() - 1)[1]);
            if (partidas == serie.size() && ratingAntes == null) {   // toda la serie cae en la ventana: suma de diffs
                int s = 0; for (Object[] pt : serie) if (pt[2] != null) s += (Integer) pt[2];
                diff = s;
            }
            out[k] = new Forma(diff, w, l, racha, Boolean.TRUE.equals(rachaGana), partidas);
        }
        return out;
    }

    @Override
    public boolean pendiente(long ultimaConsultaTs, long ahoraMs) {
        return ahoraMs - ultimaConsultaTs > CADUCIDAD.toMillis();
    }
}
