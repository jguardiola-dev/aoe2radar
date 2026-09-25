package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.cache.CacheMemoria;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Perfil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static dev.tirador.aoe2radar.service.ReglasPartida.LADDER_IDS;
import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;
import static dev.tirador.aoe2radar.util.Json.firstNonNull;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * ProfileService sobre el companion. La caché de fichas es la de la app (CachePerfiles.PERFIL_CACHE) y la recibe
 * construida: así el harness y los tests la siembran y la leen. Lo que la ficha enseña de paso (canal de Twitch,
 * país) se entrega a quien aprende (en la app, cache.Canales y cache.Paises), como hacía perfilLadders.
 */
public final class PerfilesCompanion implements ProfileService {
    private final CompanionApi api;
    private final CacheMemoria<Long, FichaPerfil> fichas;
    private final BiConsumer<Long, Object> aprenderCanal, aprenderPais;

    public PerfilesCompanion(CompanionApi api, CacheMemoria<Long, FichaPerfil> fichas,
                             BiConsumer<Long, Object> aprenderCanal, BiConsumer<Long, Object> aprenderPais) {
        this.api = api; this.fichas = fichas; this.aprenderCanal = aprenderCanal; this.aprenderPais = aprenderPais;
    }

    @Override public FichaPerfil ficha(long pid) {
        FichaPerfil c = fichas.vigente(pid);
        if (c != null) return c;
        avisarSiUi("ProfileService.ficha");
        Map<String, int[]> m = new HashMap<>();
        String pais = "", clan = "";
        long games = 0;
        try {
            Perfil pf = api.perfil(pid);
            aprenderCanal.accept(pid, pf.canal());
            aprenderPais.accept(pid, pf.pais());
            pais = String.valueOf(firstNonNull(pf.pais(), "")).trim();
            if ("null".equals(pais)) pais = "";
            clan = String.valueOf(firstNonNull(pf.clan(), "")).trim();
            if ("null".equals(clan)) clan = "";
            games = pf.partidas();
            for (Perfil.Ladder l : pf.ladders()) {
                String lid = String.valueOf(l.id());
                String lb = switch (lid) { case "3" -> "rm_1v1"; case "4" -> "rm_team"; case "13" -> "ew_1v1"; case "14" -> "ew_team"; default -> lid; };
                if (!Arrays.asList(LADDER_IDS).contains(lb)) continue;
                int rating = l.rating() != null ? l.rating() : 0;
                int rank = l.rango() != null ? l.rango() : 0;
                int maxR = l.ratingMax() != null ? l.ratingMax() : 0;
                int wins = l.ganadas() != null ? l.ganadas() : 0;
                int losses = l.perdidas() != null ? l.perdidas() : 0;
                m.put(lb, new int[]{ rating, rank, maxR, wins, losses });
            }
        } catch (Exception ex) { log("ladder: perfil " + pid + ": " + causa(ex)); return null; }
        c = new FichaPerfil(m, pais, clan, games);
        fichas.poner(pid, c);
        return c;
    }

    @Override public FichaPerfil fichaConocida(long pid) { return fichas.ultimo(pid); }
}
