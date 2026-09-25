package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.ConsultasLadder;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.RatingsService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

import static dev.tirador.aoe2radar.service.ProfileService.ACT_MAX_PAGINAS;
import static dev.tirador.aoe2radar.service.ProfileService.ACT_PAGINAS_RAPIDAS;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Lo que la pestaña Perfil pide en segundo plano: abrir un perfil (primero sfr-data, si no la API página a
 * página), «Actualizar hoy», «Cargar más», las sugerencias del buscador de nick y del filtro cara a cara de la
 * cabecera, las cuentas vinculadas y el conjunto «clan» del filtro. Sale de SpoilerFreeRecs (abrirPerfil,
 * perfilActualizarHoy, perfilCargarMas, perfilSugerir, h2hSugerir, actPintarVinculadas, aplicarH2hConjunto de la
 * 1.1) tal cual, con los mismos nombres de hilo y las mismas comprobaciones de «respuesta caducada»
 * ({@code pantalla.pidAbierto() != pid}: si mientras tanto se abrió otro perfil, la respuesta tardía no pinta).
 * <p>Sin Swing: la vista (PerfilView) implementa {@link Pantalla} y hace la pintura, que no es red.
 */
public final class PerfilPresenter {

    /** Lo que el presentador necesita pintar en la vista, sin conocer Swing. */
    public interface Pantalla {
        /** El pid actualmente abierto (para descartar respuestas de un perfil que ya no se mira). */
        long pidAbierto();
        /** ¿Hay una carga en curso? Campo de la vista (actCargando de la 1.1), leído/escrito tal cual. */
        boolean cargando();
        void cargando(boolean v);

        /** Arranca el aviso de «consultando el perfil…» con el progreso indeterminado. */
        void cargaIniciada();
        /** Ya se sabe la cabecera (ficha): la pinta si el pid sigue siendo el abierto. */
        void cabecera(FichaPerfil ficha);
        /** Todo el año vino de sfr-data: pinta cuerpo completo y marca el origen (para «Actualizar hoy»). */
        void desdeSfr(Actividad a, String hastaSfr);
        /** Una página más del historial por la API: progreso determinado + repintado con lo que hay hasta ahora. */
        void progresoParcial(Actividad parcialA, int maxPaginas);
        /** Historial por la API completo (dentro del límite de páginas de esta carga). */
        void cargaCompletada(Actividad a);
        /** La carga falló: oculta el progreso y lo dice en el estado del perfil. */
        void errorCarga(String mensaje);

        /** «Actualizar hoy»: deshabilita el botón y cambia su texto mientras dura. */
        void hoyIniciado();
        /** Terminó bien: repinta cabecera y cuerpo si hay partidas nuevas, y el botón según cuántas. */
        void hoyTerminado(FichaPerfil ficha, int nuevas);
        void hoyError(String mensaje);

        /** «Cargar más»: igual que progresoParcial pero con el máximo de páginas de esta tanda. */
        void masProgreso(Actividad parcialA, int maxPaginas);
        void masCompletado(Actividad a);
        void masError(String mensaje);

        /** Sugerencias del buscador de nick de la barra del perfil. */
        void sugerenciasBuscador(List<String[]> resultados, String query);
        /** Sugerencias del filtro «cara a cara con…» de la cabecera. */
        void sugerenciasCaraACara(List<String[]> resultados, String query);
        /** Las cuentas vinculadas ya están en ProfileService: repinta la línea de la cabecera. */
        void vinculadasListas();
        /** El conjunto de ids del filtro «clan» del cara a cara ya está listo. */
        void conjuntoClanListo(Set<Long> ids, String nombre);
    }

    private final ProfileService perfiles;
    private final RatingsService ratings;
    private final BusquedaPerfiles busqueda;
    private final Tareas tareas;
    private final Map<Long, Integer> eloWatch;
    private final Pantalla pantalla;

    public PerfilPresenter(ProfileService perfiles, RatingsService ratings, BusquedaPerfiles busqueda, Tareas tareas,
                            Map<Long, Integer> eloWatch, Pantalla pantalla) {
        this.perfiles = perfiles; this.ratings = ratings; this.busqueda = busqueda; this.tareas = tareas;
        this.eloWatch = eloWatch; this.pantalla = pantalla;
    }

    /** Ficha de cabecera sin llamada: el ELO más reciente que conocemos (watchlist, o el de su última partida por ladder). Igual que perfilSintetico de la 1.1. */
    public FichaPerfil sintetico(long pid, Actividad a, String pais) {
        Map<String, int[]> m = new HashMap<>();
        for (Match x : a.partidas()) {
            String lb = x.mode == null ? null : x.mode.contains("Empire") ? (x.mode.contains("1v1") ? "ew_1v1" : "ew_team") : x.mode.contains("Death") ? (x.mode.contains("1v1") ? "dm_1v1" : "dm_team") : x.mode.contains("1v1") ? "rm_1v1" : "rm_team";
            if (lb == null || m.containsKey(lb)) continue;
            for (var mp : x.players) if (mp.id == pid && mp.rating != null) m.put(lb, new int[]{ mp.rating + (mp.ratingDiff == null ? 0 : mp.ratingDiff), 0, 0, 0, 0 });
        }
        Integer eloW = eloWatch.get(pid);
        if (eloW != null && eloW > 0) { int[] v = m.computeIfAbsent("rm_1v1", k -> new int[5]); v[0] = eloW; }
        return new FichaPerfil(m, pais == null ? "" : pais, "", (long) a.partidas().size());
    }

    /** Abre pid: primero sfr-data (el año completo sin tocar la API); si no está en su alcance, la API página a página. Hilo "perfil-" + pid. */
    public void cargar(long pid, String nombre, Actividad base, boolean fresco) {
        boolean actualizar = base != null && !base.partidas().isEmpty();
        pantalla.cargando(true);
        pantalla.cargaIniciada();
        tareas.enFondo("perfil-" + pid, () -> {
            try {
                ratings.asegurar(false);   // para el Top % (si ya está cargado, no cuesta nada)
                Actividad desdeSfr = null; String hastaSfr = null; String paisSfr = null;
                try {
                    AnioSfr anio = perfiles.anioSfr(pid, nombre);
                    if (anio != null) { desdeSfr = anio.actividad(); hastaSfr = anio.hasta(); paisSfr = anio.pais(); }
                } catch (Exception ex) { log("perfiles: " + causa(ex)); }
                if (desdeSfr != null) {
                    Actividad a = desdeSfr; String hastaF = hastaSfr; String paisF = paisSfr;
                    FichaPerfil perfilConocido = perfiles.fichaConocida(pid);
                    FichaPerfil ficha = perfilConocido != null ? perfilConocido : sintetico(pid, a, paisF);
                    tareas.enUi(() -> {
                        pantalla.cargando(false);
                        if (pantalla.pidAbierto() != pid) return;
                        pantalla.cabecera(ficha);
                        pantalla.desdeSfr(a, hastaF);
                    });
                    return;
                }
                FichaPerfil ficha = perfiles.ficha(pid);
                tareas.enUi(() -> { if (pantalla.pidAbierto() == pid) pantalla.cabecera(ficha); });
                int max = actualizar ? ACT_MAX_PAGINAS : ACT_PAGINAS_RAPIDAS;   // primero 250 partidas; el resto solo si te quedas
                Actividad a = (fresco && base.completo()) ? base : perfiles.historial(pid, nombre, base, false, max, parcialA -> tareas.enUi(() -> {
                    if (pantalla.pidAbierto() != pid) return;
                    pantalla.progresoParcial(parcialA, ACT_MAX_PAGINAS);
                }), () -> pantalla.pidAbierto() != pid);
                tareas.enUi(() -> {
                    pantalla.cargando(false);
                    if (pantalla.pidAbierto() != pid) return;
                    pantalla.cargaCompletada(a);
                });
            } catch (Exception ex) {
                tareas.enUi(() -> { pantalla.cargando(false); if (pantalla.pidAbierto() == pid) pantalla.errorCarga(causa(ex)); });
            }
        });
    }

    /** «Actualizar hoy»: ficha + las 50 partidas más recientes, fundidas con lo que ya había. Hilo "perfil-hoy". */
    public void actualizarHoy(long pid) {
        pantalla.hoyIniciado();
        tareas.enFondo("perfil-hoy", () -> {
            try {
                FichaPerfil ficha = perfiles.ficha(pid);
                int nuevas = perfiles.traerHoy(pid);
                tareas.enUi(() -> { if (pantalla.pidAbierto() == pid) pantalla.hoyTerminado(ficha, nuevas); });
            } catch (Exception ex) {
                tareas.enUi(() -> { if (pantalla.pidAbierto() == pid) pantalla.hoyError(causa(ex)); });
            }
        });
    }

    /** «Cargar más» páginas del historial por la API. Hilo "perfil-mas-" + pid. */
    public void cargarMas(long pid, String nombre, Actividad base, int paginas) {
        pantalla.cargando(true);
        int maxTotal = base.paginas() + paginas;
        tareas.enFondo("perfil-mas-" + pid, () -> {
            try {
                Actividad a = perfiles.historial(pid, nombre, base, true, paginas, parcialA -> tareas.enUi(() -> {
                    if (pantalla.pidAbierto() != pid) return;
                    pantalla.masProgreso(parcialA, maxTotal);
                }), () -> pantalla.pidAbierto() != pid);
                tareas.enUi(() -> { pantalla.cargando(false); if (pantalla.pidAbierto() == pid) pantalla.masCompletado(a); });
            } catch (Exception ex) {
                tareas.enUi(() -> { pantalla.cargando(false); if (pantalla.pidAbierto() == pid) pantalla.masError(causa(ex)); });
            }
        });
    }

    /** Sugerencias del buscador de nick de la barra del perfil. Hilo "perfil-sugerir". */
    public void sugerirBuscador(String q) {
        tareas.enFondo("perfil-sugerir", () -> {
            List<String[]> res = busqueda.sugerir(q);
            tareas.enUi(() -> pantalla.sugerenciasBuscador(res, q));
        });
    }

    /** Sugerencias del filtro «cara a cara con…» de la cabecera (rivales del historial ya se pintan sin red, aparte). Hilo "h2h-sugerir". */
    public void sugerirCaraACara(String q) {
        tareas.enFondo("h2h-sugerir", () -> {
            List<String[]> res = busqueda.sugerir(q);
            tareas.enUi(() -> pantalla.sugerenciasCaraACara(res, q));
        });
    }

    /** Cuentas vinculadas (con su ELO) del pid actual. Hilo "perfil-vinculadas". */
    public void pedirVinculadas(long pid) {
        tareas.enFondo("perfil-vinculadas", () -> {
            perfiles.vinculadasConElo(pid);   // las recuerda para la sesión, con el ELO de cada una
            tareas.enUi(() -> { if (pantalla.pidAbierto() == pid) pantalla.vinculadasListas(); });
        });
    }

    /** El filtro «clan» del cara a cara: asegura el ladder y saca los miembros del tag. Hilo "h2h-clan". */
    public void resolverConjuntoClan(String tag, String etiqueta) {
        tareas.enFondo("h2h-clan", () -> {
            Set<Long> ids = new HashSet<>();
            if (ratings.asegurar(false) == null) for (LadderRow r : ConsultasLadder.miembrosClan(tag)) ids.add(r.pid());
            tareas.enUi(() -> pantalla.conjuntoClanListo(ids, etiqueta));
        });
    }

    /**
     * Calienta en segundo plano los perfiles ya guardados en disco (solo páginas nuevas, despacio): así los tuyos
     * abren al instante. Sin Pantalla: no pinta nada, solo deja la actividad en {@code actividadCache} y en disco.
     * Igual que precalentarPerfiles de la 1.1; hilo "perfiles-precarga", demonio, prioridad mínima.
     */
    public void precalentar(Map<Long, Actividad> actividadCache, Path perfilesDir, LongFunction<Actividad> cargarDeDisco) {
        tareas.enFondoDemonio("perfiles-precarga", () -> {
            try {
                Thread.sleep(90_000);
                if (!Files.isDirectory(perfilesDir)) return;
                List<Path> ficheros;
                try (var st = Files.list(perfilesDir)) { ficheros = st.filter(p -> p.toString().endsWith(".json")).toList(); } catch (IOException ex) { return; }
                ficheros = new ArrayList<>(ficheros);
                ficheros.removeIf(p -> { try { return System.currentTimeMillis() - Files.getLastModifiedTime(p).toMillis() > 7L * 24 * 3_600_000L; } catch (IOException ex) { return true; } });
                ficheros.sort((x, y) -> { try { return Files.getLastModifiedTime(y).compareTo(Files.getLastModifiedTime(x)); } catch (IOException ex) { return 0; } });
                if (ficheros.size() > 10) ficheros = ficheros.subList(0, 10);
                for (Path p : ficheros) {
                    long pid;
                    try { pid = Long.parseLong(p.getFileName().toString().replace(".json", "")); } catch (NumberFormatException ex) { continue; }
                    while (pantalla.cargando()) Thread.sleep(5000);   // nunca competir con una carga pedida por el usuario
                    Actividad base = actividadCache.get(pid);
                    if (base == null) base = cargarDeDisco.apply(pid);
                    if (base == null || System.currentTimeMillis() - base.ms() < 6 * 3_600_000L) continue;
                    try { perfiles.historial(pid, base.nombre(), base, false, 3, a -> { }, () -> false); } catch (Exception ex) { log("precarga perfil " + pid + ": " + causa(ex)); }
                    Thread.sleep(4000);
                }
            } catch (InterruptedException ignored) { }
        });
    }
}
