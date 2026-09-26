package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.RatingsService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

import static dev.tirador.aoe2radar.service.ProfileService.ACT_MAX_PAGINAS;
import static dev.tirador.aoe2radar.service.ProfileService.ACT_PAGINAS_RAPIDAS;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Lo que la pestaña Perfil pide en segundo plano: abrir un perfil (primero sfr-data, si no la API página a
 * página), «Actualizar hoy», «Cargar más», las sugerencias del buscador de nick y las cuentas vinculadas. Sale
 * de SpoilerFreeRecs (abrirPerfil, perfilActualizarHoy, perfilCargarMas, perfilSugerir, actPintarVinculadas de
 * la 1.1) tal cual, con los mismos nombres de hilo y las mismas comprobaciones de «respuesta caducada»
 * ({@code pantalla.pidAbierto() != pid}: si mientras tanto se abrió otro perfil, la respuesta tardía no pinta).
 * <p>Sin Swing: la vista (PerfilView) implementa {@link Pantalla} y hace la pintura, que no es red.
 */
public final class PerfilPresenter {

    /** Lo que el presentador necesita pintar en la vista, sin conocer Swing. */
    public interface Pantalla {
        /** El pid actualmente abierto (para descartar respuestas de un perfil que ya no se mira). */
        long pidAbierto();
        /** Fila 28 (C1+C2): sube cada vez que alAbrir procesa una apertura de verdad. abrirBase la captura al
         *  lanzar el hilo de fondo y la vuelve a leer al terminar: si cambió mientras tanto (el mismo pid se
         *  reabrió, o A→B→A antes de que la lectura volviera), la respuesta se descarta aunque pidAbierto()
         *  coincida (subsume esa comprobación: cada apertura de verdad tiene su propia generación). */
        long generacion();
        /** ¿Hay una carga en curso? Campo de la vista (actCargando de la 1.1), leído/escrito tal cual. */
        boolean cargando();
        void cargando(boolean v);

        /** Fila 28: lo que había en disco/memoria al abrir (perfiles.actividad + fichaConocida, leídos en un hilo
         *  de fondo). Pinta el cuerpo y la cabecera si hay algo, y decide si hace falta seguir cargando. */
        void baseLista(Actividad base, FichaPerfil perfilCache);

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
        /** Ya se pidió la ficha con éxito: se recuerda para no preguntar solo por vinculadas en la sesión (mismo sitio que la 1.1: tras ficha(pid), antes de traerHoy).
         *  Fila 129: se llama FUERA del EDT, en el hilo de fondo "perfil-hoy" (no pasa por tareas.enUi); quien la implemente debe usar una colección segura entre hilos. */
        void marcarVinculadasPedidas(long pid);
        /** Terminó bien: repinta cabecera y cuerpo si hay partidas nuevas, y el botón según cuántas. */
        void hoyTerminado(FichaPerfil ficha, int nuevas);
        void hoyError(String mensaje);

        /** «Cargar más»: igual que progresoParcial pero con el máximo de páginas de esta tanda. */
        void masProgreso(Actividad parcialA, int maxPaginas);
        void masCompletado(Actividad a);
        void masError(String mensaje);

        /** Sugerencias del buscador de nick de la barra del perfil. */
        void sugerenciasBuscador(List<String[]> resultados, String query);
        /** Las cuentas vinculadas ya están en ProfileService: repinta la línea de la cabecera. */
        void vinculadasListas();
    }

    private final ProfileService perfiles;
    private final RatingsService ratings;
    private final BusquedaPerfiles busqueda;
    private final Tareas tareas;
    private final Map<Long, Integer> eloWatch;
    private final Map<Long, Actividad> actividadCache;
    private final Pantalla pantalla;
    /** F5 (1.3): testigo de la última carga lanzada (cargar o cargarMas). Solo la carga cuyo testigo sigue siendo el
     *  vigente apaga cargando(): la respuesta tardía de un perfil que ya se dejó no apaga la del que se está mirando.
     *  Se lee y escribe solo en el EDT (al lanzar y en tareas.enUi), así que no necesita volatile. */
    private long cargaToken;
    /** F4 (1.3): pids con «Actualizar hoy» en marcha (puede haber varios: A, luego B antes de que A vuelva). */
    private final java.util.Set<Long> hoyEnMarcha = new java.util.HashSet<>();
    /** F4 (3): pid → partidas nuevas del último «Actualizar hoy» terminado bien en esta sesión, aunque al terminar
     *  ese perfil ya no estuviera abierto. Las dos colecciones se leen y escriben solo en el EDT. */
    private final Map<Long, Integer> hoyHechos = new HashMap<>();

    public PerfilPresenter(ProfileService perfiles, RatingsService ratings, BusquedaPerfiles busqueda, Tareas tareas,
                            Map<Long, Integer> eloWatch, Map<Long, Actividad> actividadCache, Pantalla pantalla) {
        this.perfiles = perfiles; this.ratings = ratings; this.busqueda = busqueda; this.tareas = tareas;
        this.eloWatch = eloWatch; this.actividadCache = actividadCache; this.pantalla = pantalla;
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

    /**
     * Fila 28 (C1+C2, revisión del revisor): la vista solo llega aquí cuando el perfil NO está ya en
     * {@code actividadCache} (si lo estuviera, pintaría en el acto ella misma, sin hilo ni parpadeo); así que
     * perfiles.actividad(pid) sí va a tener que leer disco (HistorialDisco.cargarActividad), y eso sale del EDT.
     * El resultado vuelve por baseLista(), pero solo si {@code generacion} (la de ESTA apertura, capturada al
     * llamar) sigue siendo la actual: evita que un A→B→A rápido, o abrir dos veces el mismo pid antes de que la
     * primera lectura vuelva, disparen dos cargas a la vez. Hilo "perfil-abrir-" + pid.
     */
    public void abrirBase(long pid, long generacion) {
        tareas.enFondo("perfil-abrir-" + pid, () -> {
            Actividad base = perfiles.actividad(pid);
            FichaPerfil perfilCache = perfiles.fichaConocida(pid);
            tareas.enUi(() -> { if (pantalla.generacion() == generacion) pantalla.baseLista(base, perfilCache); });
        });
    }

    /** Abre pid: primero sfr-data (el año completo sin tocar la API); si no está en su alcance, la API página a página. Hilo "perfil-" + pid. */
    public void cargar(long pid, String nombre, Actividad base, boolean fresco) {
        boolean actualizar = base != null && !base.partidas().isEmpty();
        long token = ++cargaToken;
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
                    actividadCache.put(pid, a);
                    FichaPerfil perfilConocido = perfiles.fichaConocida(pid);
                    FichaPerfil ficha = perfilConocido != null ? perfilConocido : sintetico(pid, a, paisF);
                    tareas.enUi(() -> {
                        terminar(token);
                        if (pantalla.pidAbierto() != pid) return;
                        pantalla.cabecera(ficha);
                        pantalla.desdeSfr(a, hastaF);
                    });
                    return;
                }
                FichaPerfil ficha = fichaOConocida(pid);
                tareas.enUi(() -> { if (pantalla.pidAbierto() == pid) pantalla.cabecera(ficha); });
                int max = actualizar ? ACT_MAX_PAGINAS : ACT_PAGINAS_RAPIDAS;   // primero 100 partidas (2 páginas); hasta 1.000 al actualizar; el resto, con «Cargar más»
                Actividad a = (fresco && base.completo()) ? base : perfiles.historial(pid, nombre, base, false, max, parcialA -> tareas.enUi(() -> {
                    if (pantalla.pidAbierto() != pid) return;
                    pantalla.progresoParcial(parcialA, max);   // F8: «página 1 de 2» en la carga rápida (antes decía «de 20»)
                }), () -> pantalla.pidAbierto() != pid);
                tareas.enUi(() -> {
                    terminar(token);
                    if (pantalla.pidAbierto() != pid) return;
                    pantalla.cargaCompletada(a);
                });
            } catch (Exception ex) {
                tareas.enUi(() -> { terminar(token); if (pantalla.pidAbierto() == pid) pantalla.errorCarga(causa(ex)); });
            }
        });
    }

    /** F9 (1.3): la ficha por la API; si esa llamada falla (null), la última conocida, para no borrar la cabecera
     *  con «Sin datos de perfil» cuando lo que falló fue solo la ficha. Fuera del EDT (hace red). */
    private FichaPerfil fichaOConocida(long pid) {
        FichaPerfil f = perfiles.ficha(pid);
        return f != null ? f : perfiles.fichaConocida(pid);
    }

    /** F5: fin de una carga (en el EDT). Solo apaga cargando() si ninguna carga posterior la ha relevado. */
    private void terminar(long token) {
        if (token == cargaToken) pantalla.cargando(false);
    }

    /** «Actualizar hoy»: ficha + las 50 partidas más recientes, fundidas con lo que ya había. Hilo "perfil-hoy". */
    public void actualizarHoy(long pid) {
        if (pid <= 0) return;
        hoyEnMarcha.add(pid); hoyHechos.remove(pid);
        pantalla.hoyIniciado();
        tareas.enFondo("perfil-hoy", () -> {
            try {
                FichaPerfil ficha = fichaOConocida(pid);
                pantalla.marcarVinculadasPedidas(pid);
                int nuevas = perfiles.traerHoy(pid);
                tareas.enUi(() -> { hoyEnMarcha.remove(pid); hoyHechos.put(pid, nuevas); if (pantalla.pidAbierto() == pid) pantalla.hoyTerminado(ficha, nuevas); });
            } catch (Exception ex) {
                tareas.enUi(() -> { hoyEnMarcha.remove(pid); if (pantalla.pidAbierto() == pid) pantalla.hoyError(causa(ex)); });
            }
        });
    }

    /** F4: ¿está en marcha el «Actualizar hoy» de este pid? (para pintar su botón al volver a él). Solo en el EDT. */
    public boolean hoyEnCurso(long pid) { return hoyEnMarcha.contains(pid); }

    /** F4 (3): las partidas nuevas del «Actualizar hoy» ya terminado de este pid en la sesión; null si no hubo. Solo en el EDT. */
    public Integer hoyNuevas(long pid) { return hoyHechos.get(pid); }

    /** F4 (3): el perfil volvió a llegar de sfr-data: su «Actualizar hoy» anterior deja de contar. Solo en el EDT. */
    public void olvidarHoy(long pid) { hoyHechos.remove(pid); }

    /** «Cargar más» páginas del historial por la API. Hilo "perfil-mas-" + pid. */
    public void cargarMas(long pid, String nombre, Actividad base, int paginas) {
        long token = ++cargaToken;
        pantalla.cargando(true);
        int maxTotal = base.paginas() + paginas;
        tareas.enFondo("perfil-mas-" + pid, () -> {
            try {
                Actividad a = perfiles.historial(pid, nombre, base, true, paginas, parcialA -> tareas.enUi(() -> {
                    if (pantalla.pidAbierto() != pid) return;
                    pantalla.masProgreso(parcialA, maxTotal);
                }), () -> pantalla.pidAbierto() != pid);
                tareas.enUi(() -> { terminar(token); if (pantalla.pidAbierto() == pid) pantalla.masCompletado(a); });
            } catch (Exception ex) {
                tareas.enUi(() -> { terminar(token); if (pantalla.pidAbierto() == pid) pantalla.masError(causa(ex)); });
            }
        });
    }

    /**
     * Fila 128: «Cargar 50 más» del diálogo «Todas las partidas» (antes vivía como hilo suelto dentro de
     * PerfilView.mostrarHistorialPerfil). Mismo nombre de hilo y misma caché que la 1.1: "historial-mas",
     * fundido en actividadCache para que cualquier otra vista que mire este pid vea el resultado.
     */
    public void cargarMasHistorialCompleto(long pid, String nombre, Runnable alTerminar) {
        tareas.enFondo("historial-mas", () -> {
            try {
                Actividad base = actividadCache.get(pid);
                Actividad a2 = perfiles.historial(pid, nombre, base, true, 1, a -> { }, () -> false);
                actividadCache.put(pid, a2);
            } catch (Exception ex) { log("historial: " + causa(ex)); }
            tareas.enUi(alTerminar);
        });
    }

    /** Sugerencias del buscador de nick de la barra del perfil. Hilo "perfil-sugerir". */
    public void sugerirBuscador(String q) {
        tareas.enFondo("perfil-sugerir", () -> {
            List<String[]> res = busqueda.sugerir(q);
            tareas.enUi(() -> pantalla.sugerenciasBuscador(res, q));
        });
    }

    /** Cuentas vinculadas (con su ELO) del pid actual. Hilo "perfil-vinculadas". */
    public void pedirVinculadas(long pid) {
        tareas.enFondo("perfil-vinculadas", () -> {
            perfiles.vinculadasConElo(pid);   // las recuerda para la sesión, con el ELO de cada una
            tareas.enUi(() -> { if (pantalla.pidAbierto() == pid) pantalla.vinculadasListas(); });
        });
    }

    /**
     * Calienta en segundo plano los perfiles ya guardados en disco (solo páginas nuevas, despacio): así los tuyos
     * abren al instante. Sin Pantalla: no pinta nada, solo deja la actividad en {@code actividadCache} y en disco.
     * Igual que precalentarPerfiles de la 1.1; hilo "perfiles-precarga", demonio, prioridad mínima.
     */
    public void precalentar(Path perfilesDir, LongFunction<Actividad> cargarDeDisco) {
        tareas.enFondoDemonioMinima("perfiles-precarga", () -> {
            try {
                Thread.sleep(90_000);
                precalentarAhora(perfilesDir, cargarDeDisco);
            } catch (InterruptedException ignored) { }
        });
    }

    /** Pausa entre perfiles precalentados por la API (4 s, como en la 1.1). El test la baja. */
    long pausaPrecargaMs = 4000;

    /**
     * El trabajo de precalentar, sin la espera inicial (el test lo llama directo). «Nocturno primero» (1.3): antes de
     * ir a la API mira sfr-data; si tiene el año del jugador, abrir su perfil ya no llama a la API (cargar lo saca de
     * ahí), así que no se gastan hasta 3 páginas en él; de paso el paquete queda bajado. Solo los que sfr-data no
     * cubre (o si leerlo falla) van a la API, como antes. Fuera del EDT.
     */
    void precalentarAhora(Path perfilesDir, LongFunction<Actividad> cargarDeDisco) throws InterruptedException {
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
            try {
                if (perfiles.anioSfr(pid, base.nombre()) != null) continue;   // en el alcance de sfr-data: sin API
            } catch (Exception ex) { log("precarga perfil " + pid + " (sfr-data): " + causa(ex)); }
            try { perfiles.historial(pid, base.nombre(), base, false, 3, a -> { }, () -> false); } catch (Exception ex) { log("precarga perfil " + pid + ": " + causa(ex)); }
            Thread.sleep(pausaPrecargaMs);
        }
    }
}
