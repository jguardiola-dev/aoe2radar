package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.ProfileService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
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

    /** F4 (3): se avisa (en el EDT) cuando el año de un rival llega de sfr-data: pid y fecha «hasta» del paquete. */
    private final BiConsumer<Long, String> alLlegarDeSfr;
    /** F7 (2): rivales que sfr-data no trae y fichas que no se pudieron pedir, recordados para la sesión: repintar
     *  el cruce (p. ej. al reordenar una lista) no vuelve a leer ni a llamar por ellos. Se escriben en los hilos de
     *  fondo h2h-comparar y h2h-ficha: conjuntos seguros entre hilos. */
    private final Set<Long> fueraDeSfr = ConcurrentHashMap.newKeySet();
    private final Set<Long> fichaSinRespuesta = ConcurrentHashMap.newKeySet();

    public CaraACaraPresenter(ProfileService perfiles, BusquedaPerfiles busqueda, Tareas tareas, Map<Long, Actividad> actividadCache) {
        this(perfiles, busqueda, tareas, actividadCache, (pid, hasta) -> { });
    }

    public CaraACaraPresenter(ProfileService perfiles, BusquedaPerfiles busqueda, Tareas tareas, Map<Long, Actividad> actividadCache, BiConsumer<Long, String> alLlegarDeSfr) {
        this.perfiles = perfiles; this.busqueda = busqueda; this.tareas = tareas; this.actividadCache = actividadCache; this.alLlegarDeSfr = alLlegarDeSfr;
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
     * {@code rivalNombre} NO sirve para buscar nada en sfr-data: es el nombre con el que se GUARDA la Actividad
     * del rival si el paquete no trae uno propio (ver ProfileService.anioSfr / AnioDesdeSfr.convertir, que hace
     * {@code nombre.isBlank() ? nombreSiFalta : nombre}). El bug de la fila 80 era que CaraACaraDialogo pasaba
     * aquí el nombre del perfil que ya estaba ABIERTO en vez del rival, así que la Actividad del rival podía
     * quedar guardada con el nombre de otro jugador. Si {@code rivalNombre} viene vacío, se usa "#pid" para que
     * al menos quede identificado. {@code sigueAbierto} se mira al volver, para no pintar sobre un cruce que ya
     * se cambió. Hilo "h2h-comparar".
     */
    public void pedirAnioRival(long rivalPid, String rivalNombre, Supplier<Boolean> sigueAbierto, Consumer<Actividad> pintar) {
        String nombreSiFalta = rivalNombre != null && !rivalNombre.isBlank() ? rivalNombre : "#" + rivalPid;
        tareas.enFondo("h2h-comparar", () -> {
            Actividad ar = actividadCache.get(rivalPid);
            String hastaSfr = null; boolean deSfr = false;
            if (ar == null && !fueraDeSfr.contains(rivalPid)) {
                try {
                    AnioSfr an = perfiles.anioSfr(rivalPid, nombreSiFalta);
                    if (an != null) { ar = an.actividad(); actividadCache.put(rivalPid, ar); hastaSfr = an.hasta(); deSfr = true; }
                    else fueraDeSfr.add(rivalPid);   // F7 (2): no está en el paquete; un fallo de lectura (excepción) sí se reintenta
                } catch (Exception ex) { log("h2h rival: " + causa(ex)); }
            }
            final Actividad arF = ar; final String hastaF = hastaSfr; final boolean deSfrF = deSfr;
            tareas.enUi(() -> {
                if (deSfrF) alLlegarDeSfr.accept(rivalPid, hastaF);   // F4 (3): aunque el diálogo ya mire otro cruce
                if (Boolean.TRUE.equals(sigueAbierto.get())) pintar.accept(arF);
            });
        });
    }

    /** La ficha (ELO y puesto) de un jugador que no se conoce todavía: sin comprobación de caducidad, como la 1.1
     *  (pinta directamente en la etiqueta que se le pasó, aunque el diálogo haya seguido adelante). Hilo "h2h-ficha". */
    public void pedirFicha(long pid, Consumer<FichaPerfil> pintar) {
        tareas.enFondo("h2h-ficha", () -> {
            FichaPerfil p = fichaSinRespuesta.contains(pid) ? null : perfiles.ficha(pid);
            if (p == null) fichaSinRespuesta.add(pid);   // F7 (2): no se vuelve a pedir en la sesión
            tareas.enUi(() -> pintar.accept(p));
        });
    }
}
