package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.ProfileService;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Lo que el diálogo de Cara a cara pide en segundo plano: sugerencias del buscador de rival, el año del rival
 * para «Cada uno por su lado» y la ficha compacta de un jugador cuando no se conoce. Sale de SpoilerFreeRecs
 * (h2hSugerirDialogo, la parte de red de h2hFijar, fichaJugadorH2h de la 1.1) tal cual, mismos nombres de hilo.
 */
public final class CaraACaraPresenter {

    private final ProfileService perfiles;
    private final BusquedaPerfiles busqueda;
    private final Tareas tareas;
    private final Map<Long, Actividad> actividadCache;

    public CaraACaraPresenter(ProfileService perfiles, BusquedaPerfiles busqueda, Tareas tareas, Map<Long, Actividad> actividadCache) {
        this.perfiles = perfiles; this.busqueda = busqueda; this.tareas = tareas; this.actividadCache = actividadCache;
    }

    /** Sugerencias del buscador de rival del diálogo. {@code actual} da el texto vigente al mirar la respuesta. Hilo "h2h-sugerir". */
    public void sugerir(String q, Supplier<String> actual, Consumer<List<String[]>> pintar) {
        tareas.enFondo("h2h-sugerir", () -> {
            List<String[]> res = busqueda.sugerir(q);
            tareas.enUi(() -> { if (q.equals(actual.get())) pintar.accept(res); });
        });
    }

    /**
     * El año del rival para «Cada uno por su lado» (sfr-data; si no está ahí, queda sin comparación, como la 1.1).
     * {@code sigueAbierto} se mira al volver, para no pintar sobre un cruce que ya se cambió. Hilo "h2h-comparar".
     */
    public void pedirAnioRival(long rivalPid, String nombreSiFalta, Supplier<Boolean> sigueAbierto, Consumer<Actividad> pintar) {
        tareas.enFondo("h2h-comparar", () -> {
            Actividad ar = actividadCache.get(rivalPid);
            if (ar == null) {
                try {
                    AnioSfr an = perfiles.anioSfr(rivalPid, nombreSiFalta);
                    if (an != null) { ar = an.actividad(); actividadCache.put(rivalPid, ar); }
                } catch (Exception ex) { log("h2h rival: " + causa(ex)); }
            }
            final Actividad arF = ar;
            tareas.enUi(() -> { if (Boolean.TRUE.equals(sigueAbierto.get())) pintar.accept(arF); });
        });
    }

    /** La ficha (ELO y puesto) de un jugador que no se conoce todavía: sin comprobación de caducidad, como la 1.1
     *  (pinta directamente en la etiqueta que se le pasó, aunque el diálogo haya seguido adelante). Hilo "h2h-ficha". */
    public void pedirFicha(long pid, Consumer<FichaPerfil> pintar) {
        tareas.enFondo("h2h-ficha", () -> {
            FichaPerfil p = perfiles.ficha(pid);
            tareas.enUi(() -> pintar.accept(p));
        });
    }
}
