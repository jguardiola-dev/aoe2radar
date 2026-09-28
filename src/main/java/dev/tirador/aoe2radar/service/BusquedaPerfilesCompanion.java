package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.FuenteBusqueda;
import dev.tirador.aoe2radar.model.PerfilEncontrado;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * BusquedaPerfiles sobre el companion. El índice local (nombresAyer/eloAyer) es el volcado nocturno de nicks de
 * sfr-data (en la app, Snapshots.NOMBRES_AYER/ELO_AYER): se recibe por constructor para no atar este servicio a ese
 * estático. Lo que la API enseña de paso (el país) se entrega a quien aprende (aprenderPais), como en
 * PerfilesCompanion.
 */
public final class BusquedaPerfilesCompanion implements BusquedaPerfiles {
    private final FuenteBusqueda api;   // en la app, companion con respaldo de World's Edge (alias exacto)
    private final Map<Long, String[]> nombresAyer;   // pid → {nombre, país}
    private final Map<Long, int[]> eloAyer;          // pid → {elo1v1, partidas1v1, eloEq, partidasEq}
    private final BiConsumer<Long, String> aprenderPais;

    public BusquedaPerfilesCompanion(FuenteBusqueda api, Map<Long, String[]> nombresAyer, Map<Long, int[]> eloAyer,
                                      BiConsumer<Long, String> aprenderPais) {
        this.api = api; this.nombresAyer = nombresAyer; this.eloAyer = eloAyer; this.aprenderPais = aprenderPais;
    }

    /** Búsqueda solo en el índice local (top 40.000 del volcado nocturno): {pid, nombre, «nombre · país · ELO»}, hasta 8, las de más ELO primero. */
    @Override public List<String[]> local(String q) {
        List<String[]> out = new ArrayList<>();
        if (q == null || q.length() < 2 || nombresAyer.isEmpty()) return out;
        String ql = q.toLowerCase(Locale.ROOT);
        List<Map.Entry<Long, String[]>> l = new ArrayList<>();
        for (Map.Entry<Long, String[]> en : nombresAyer.entrySet()) if (en.getValue()[0].toLowerCase(Locale.ROOT).contains(ql)) l.add(en);
        l.sort((a, b) -> { int[] ea = eloAyer.get(a.getKey()), eb = eloAyer.get(b.getKey()); return Integer.compare(eb == null ? 0 : eb[0], ea == null ? 0 : ea[0]); });
        for (Map.Entry<Long, String[]> en : l) { int[] e = eloAyer.get(en.getKey()); out.add(new String[]{ String.valueOf(en.getKey()), en.getValue()[0], en.getValue()[0] + (en.getValue()[1].isBlank() ? "" : " · " + en.getValue()[1].toUpperCase(Locale.ROOT)) + (e != null && e[0] > 0 ? " · " + e[0] : "") }); if (out.size() >= 8) break; }
        return out;
    }

    /** Sugerencias al teclear: el índice local si tiene algo (sin llamada); si no, la API. */
    @Override public List<String[]> sugerir(String q) {
        List<String[]> local = local(q);
        return local.isEmpty() ? buscar(q) : local;
    }

    /** Búsqueda explícita (Enter): la API y el índice local, juntos y sin duplicados; primero lo local. Así un nick cambiado hace poco (que el volcado aún no conoce) también aparece. */
    @Override public List<String[]> buscar(String q) {
        List<String[]> local = local(q), out = new ArrayList<>(local);
        Set<String> vistos = new HashSet<>(); for (String[] r : local) vistos.add(r[0]);
        for (String[] r : buscarPerfilesApi(q)) if (vistos.add(r[0])) out.add(r);
        return out;
    }

    private List<String[]> buscarPerfilesApi(String q) {
        avisarSiUi("BusquedaPerfiles.buscar");
        List<String[]> out = new ArrayList<>();
        try {
            for (PerfilEncontrado p : api.buscarPerfiles(q)) {   // sin reintento, como antes
                long id = p.pid();
                if (id <= 0) continue;
                String name = p.nombre();
                String pais = p.pais();
                aprenderPais.accept(id, pais);
                long games = p.partidas();
                out.add(new String[]{ String.valueOf(id), name, name + (pais != null ? "  [" + pais + "]" : "") + "  ·  " + id + (games > 0 ? "  ·  " + games + t(" partidas", " games") : "") });
            }
        } catch (Exception ex) { log("buscarPerfiles: " + causa(ex)); }
        return out;
    }
}
