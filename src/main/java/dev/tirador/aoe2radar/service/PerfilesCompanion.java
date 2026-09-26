package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.cache.CacheMemoria;
import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Perfil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
    private final AnioDesdeSfr anio;
    private final HistorialPerfil historial;
    private final EloSesion elosVinculadas;   // vid → ELO 1v1 (0: no tiene), con la regla del ELO actual

    public PerfilesCompanion(CompanionApi api, CacheMemoria<Long, FichaPerfil> fichas,
                             BiConsumer<Long, Object> aprenderCanal, BiConsumer<Long, Object> aprenderPais,
                             AnioDesdeSfr anio, HistorialPerfil historial, EloSesion elosVinculadas) {
        this.api = api; this.fichas = fichas; this.aprenderCanal = aprenderCanal; this.aprenderPais = aprenderPais;
        this.anio = anio; this.historial = historial; this.elosVinculadas = elosVinculadas;
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

    @Override public Integer elo1v1(long pid) { return elo1v1Leido(pid).elo(); }   // null si no tiene o si falla, como la 1.1

    @Override public Elo1v1 elo1v1Leido(long pid) {
        avisarSiUi("ProfileService.elo1v1");
        try {   // la vía del perfil: la misma que usa el hover, probada
            Perfil pf = api.perfil(pid);
            aprenderCanal.accept(pid, pf.canal());
            for (Perfil.Ladder lb : pf.ladders()) {
                String lid = String.valueOf(lb.id());
                if (!"rm_1v1".equals(lid) && !"3".equals(lid)) continue;
                if (lb.rating() != null) return new Elo1v1(lb.rating(), false);
            }
        } catch (Exception ignored) { return new Elo1v1(null, true); }   // sin log, como la 1.1; pero no es «no tiene» (F11)
        return new Elo1v1(null, false);
    }

    // ----- Vinculadas y familias: estado de la sesión, sin caducidad (en la 1.1, mapas estáticos de CachePerfiles) -----
    private final Map<Long, List<Perfil.Vinculada>> vinculadasSesion = new ConcurrentHashMap<>();   // pid → las de la cabecera
    private final Map<Long, Map<Long, String>> familias = new ConcurrentHashMap<>();                 // id → {altId → nombre}

    @Override public List<Perfil.Vinculada> vinculadas(long profileId) {
        avisarSiUi("ProfileService.vinculadas");
        List<Perfil.Vinculada> out = new ArrayList<>();
        try {
            Perfil pf = api.perfil(profileId);
            aprenderCanal.accept(profileId, pf.canal());
            Set<Long> vistos = new HashSet<>();
            for (Perfil.Vinculada lp : pf.vinculadas()) {
                long id = lp.pid();
                if (id <= 0 || id == profileId || !vistos.add(id)) continue;   // el propio no es su vinculada
                String name = String.valueOf(lp.nombre());
                String c = lp.pais();
                String pais = c == null ? "" : c.toUpperCase();
                long games = lp.partidas();
                if (id > 0 && !"null".equals(name)) out.add(new Perfil.Vinculada(id, name, pais, games));
            }
            if (!out.isEmpty()) {   // la familia consultada alimenta la señal ↥ de la sesión
                // Los mapas por id son ConcurrentHashMap, no HashMap: familia(pid) los entrega tal cual (sin copia)
                // y un hilo puede leerlos (mejorAlt) mientras otro los escribe aquí (ver DEUDA fila 79).
                Map<Long, String> deEste = familias.computeIfAbsent(profileId, k -> new ConcurrentHashMap<>());
                for (Perfil.Vinculada v : out) {
                    deEste.put(v.pid(), v.nombre());
                    familias.computeIfAbsent(v.pid(), k -> new ConcurrentHashMap<>()).put(profileId, "—");
                }
            }
        } catch (Exception ex) {
            log("vinculadas: fallo con perfil " + profileId + ": " + causa(ex));
        }
        return out;
    }

    @Override public List<Perfil.Vinculada> vinculadasConElo(long pid) {
        List<Perfil.Vinculada> res = vinculadas(pid);
        vinculadasSesion.put(pid, res);   // antes que los ELO, como la 1.1: un repintado entretanto las ve sin ELO
        for (Perfil.Vinculada x : res) { long vid = x.pid(); if (elosVinculadas.conocido(vid) == null || elosVinculadas.caducado(vid)) { long pedido = elosVinculadas.ahora(); Elo1v1 r = elo1v1Leido(vid); if (!r.fallo()) elosVinculadas.apuntar(vid, r.elo(), pedido); } }   // un fallo no se apunta como «sin ELO» (F11)
        return res;
    }

    @Override public List<Perfil.Vinculada> vinculadasConocidas(long pid) { return vinculadasSesion.get(pid); }

    @Override public Integer eloVinculada(long vid) { return elosVinculadas.conocido(vid); }

    @Override public Map<Long, String> familia(long pid) { return familias.get(pid); }

    @Override public AnioSfr anioSfr(long pid, String nombreSiFalta) throws Exception {
        avisarSiUi("ProfileService.anioSfr");
        return anio.leer(pid, nombreSiFalta);
    }

    @Override public Actividad actividad(long pid) { return historial.actividad(pid); }

    @Override public Actividad historial(long pid, String nombre, Actividad base, boolean mas, int maxPaginas,
                                         Consumer<Actividad> parcial, BooleanSupplier cancelar) throws IOException, InterruptedException {
        avisarSiUi("ProfileService.historial");
        return historial.descargar(pid, nombre, base, mas, maxPaginas, parcial, cancelar);
    }

    @Override public int traerHoy(long pid) throws IOException, InterruptedException {
        avisarSiUi("ProfileService.traerHoy");
        return historial.traerHoy(pid);
    }
}
