package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.service.StatsService;
import dev.tirador.aoe2radar.service.TechTreeService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Todo lo del Tech tree que hoy es {@code new Thread(...)}: cargar el catálogo, el árbol de una civ, el
 * resumen de Civ Stats para la banda de winrate, la cola de iconos y la precarga al arrancar. Sin Swing ni
 * AWT: recibe un {@link Tareas} (en la app, hilos reales; en los tests, {@link Tareas#EN_LINEA}) y una
 * {@link Pantalla} con solo los datos que la vista necesita para pintar, nunca componentes de Swing.
 * <p>Movido tal cual desde SpoilerFreeRecs (ttPedirIcono, ttPrecargar, ttCargarStats y la parte de vista de
 * abrirTechTree/ttMostrarCiv que hacía new Thread): mismos nombres de hilo, mismo límite de 4 hilos de
 * iconos, misma comprobación de «la civ pedida cambió» antes de pintar el árbol que llega tarde.
 */
public final class TechTreePresenter {

    /** Lo que el presentador necesita pedirle a la vista: sin javax.swing ni java.awt, solo datos y decisiones. */
    public interface Pantalla {
        /** El catálogo ya está (o el motivo del fallo): reconstruye el combo de civs y elige "civPedida" (o la primera). */
        void datosListos(String error, String civPedida);

        /** El árbol de "civ" ya está descargado: si sigue siendo la civ pedida, pinta la ficha y el árbol. */
        void arbolListo(String civ, Map<String, Object> arbol);

        /** No se pudo descargar el árbol de "civ". */
        void errorArbol(String civ, String motivo);

        /** El tamaño de celda con el que hay que precargar los iconos antes de pintar (lee el visor, sin red);
         *  se llama en el EDT, ANTES de lanzar el hilo techtree-civ (ver DEUDA, fila 118: antes se leía fuera
         *  del EDT y podía chocar con un repintado real del visor). */
        int celdaPx();

        /** A memoria (o a la cola de descarga si falta) el icono tipo/id a ese tamaño: no se usa el resultado aquí, solo precalienta la caché;
         *  se llama fuera del EDT, en el hilo techtree-civ, como en la 1.1; lee Swing (ver DEUDA). */
        void precalentarIcono(String tipo, long id, int px);

        /** Ya bajaron más iconos de la cola: repinta lo que esté visible con ellos. */
        void iconosActualizados();

        /** Texto de estado de la banda de winrate mientras se carga el resumen de Civ Stats. */
        void estadoWr(String texto);

        /** El resumen de Civ Stats ya estaba (o ya está) cargado: recalcula y repinta la banda de winrate. */
        void actualizarWr();
    }

    private final TechTreeService tt;
    private final StatsService stats;
    private final FiltroStats filtroStats;
    private final Tareas tareas;
    private final Pantalla pantalla;
    private final TechTreeView.Anfitrion anfitrion;
    private final TechTreeView.EnlaceCivStats enlaceCivStats;

    // Cola de iconos compartida (ver ttPedirIcono en la 1.1): hasta 4 hilos "techtree-iconos" descargando en paralelo.
    private final Set<String> pedidos = ConcurrentHashMap.newKeySet();
    private final LinkedBlockingQueue<String> cola = new LinkedBlockingQueue<>();
    private final AtomicInteger hilos = new AtomicInteger();

    // La última civ pedida a pedirArbol(): si el árbol de una civ vieja llega tarde, no se pinta.
    private volatile String civEnCurso;

    /** La civ pedida ahora mismo (o null si aún no se pidió ninguna): única fuente de verdad (antes también vivía
     *  como ttCivPedida en TechTreeView, ver DEUDA, fila 117); la vista la lee de aquí. */
    public String civEnCurso() { return civEnCurso; }

    public TechTreePresenter(TechTreeService tt, StatsService stats, FiltroStats filtroStats, Tareas tareas,
                              Pantalla pantalla, TechTreeView.Anfitrion anfitrion, TechTreeView.EnlaceCivStats enlaceCivStats) {
        this.tt = tt;
        this.stats = stats;
        this.filtroStats = filtroStats;
        this.tareas = tareas;
        this.pantalla = pantalla;
        this.anfitrion = anfitrion;
        this.enlaceCivStats = enlaceCivStats;
    }

    /** «techtree-datos»: comprueba si hay catálogo nuevo y lo asegura; vuelve al EDT con el resultado. */
    public void cargarDatos(String civPedida) {
        tareas.enFondo("techtree-datos", () -> {
            tt.comprobarActualizacion();
            String err = tt.asegurarDatos();
            tareas.enUi(() -> pantalla.datosListos(err, civPedida));
        });
    }

    /** «techtree-civ»: descarga (o coge de caché) el árbol de "civ"; si mientras tanto se pidió otra, no pinta. */
    public void pedirArbol(String civ) {
        civEnCurso = civ;
        int px = pantalla.celdaPx();   // se lee AQUÍ, en el hilo que llama (el EDT): celdaPx mira el visor de Swing y no es
        // seguro leerlo desde el hilo de fondo (ver DEUDA, fila 118); ya calculado, se pasa al hilo sin volver a tocar Swing.
        tareas.enFondo("techtree-civ", () -> {
            Map<String, Object> arbol;
            try { arbol = tt.arbol(civ); }
            catch (Exception ex) { tareas.enUi(() -> pantalla.errorArbol(civ, causa(ex))); return; }
            // los iconos de esta civ, a memoria ANTES de pintar: el árbol sale entero de golpe. Los tamaños
            // (celda-6 para el nodo/la celda del edificio en la rejilla, 34 fijo para la cabecera del edificio)
            // son los MISMOS que pide el pintado (TechTreeView.ttPintarArbol): antes no coincidían (px-4 y 26)
            // y la caché nunca acertaba (revisor, fila 145 de DEUDA).
            for (Object o : arr(arbol.get("units_techs"))) { Map<String, Object> n = obj(o); pantalla.precalentarIcono(String.valueOf(n.get("use_type")), lng(n.get("picture_index")), TechTreeView.ttTamanoIconoCelda(px)); }
            for (Object o : arr(arbol.get("buildings"))) {
                Map<String, Object> n = obj(o);
                long pic = lng(n.get("picture_index"));
                pantalla.precalentarIcono("Building", pic, TechTreeView.ttTamanoIconoCelda(px));   // icono en su celda de la rejilla
                pantalla.precalentarIcono("Building", pic, TechTreeView.TT_ICONO_CABECERA_EDIFICIO);   // icono grande de la cabecera
            }
            Map<String, Object> arbolListo = arbol;
            tareas.enUi(() -> { if (civ.equals(civEnCurso)) pantalla.arbolListo(civ, arbolListo); });   // ya se pidió otra civ: esta llegó tarde
        });
    }

    /** «techtree-stats»: asegura la ventana de Civ Stats con la que se calcula la banda de winrate. */
    public void cargarStats() {
        if (stats.tieneVentana(filtroStats.ventana())) { pantalla.actualizarWr(); return; }
        pantalla.estadoWr(t("cargando…", "loading…"));
        String ventana = filtroStats.ventana();
        tareas.enFondo("techtree-stats", () -> {
            String err = stats.asegurar(ventana, false);
            tareas.enUi(() -> {
                if (err != null) { pantalla.estadoWr(t("sin datos (", "no data (") + err + ")"); return; }
                pantalla.estadoWr("");
                if (enlaceCivStats.construida()) enlaceCivStats.filtrosCambiados(true); else pantalla.actualizarWr();
            });
        });
    }

    /** Pide (si hace falta) el icono relativo "rel": si ya se pidió, no lo repite; si no hay hilo libre (máx. 4), espera turno. */
    public void pedirIcono(String rel) {
        if (!pedidos.add(rel)) return;
        cola.offer(rel);
        while (hilos.get() < 4 && !cola.isEmpty()) {
            hilos.incrementAndGet();
            tareas.enFondoDemonio("techtree-iconos", () -> {
                try {
                    String job;
                    int bajados = 0;
                    while ((job = cola.poll(2, TimeUnit.SECONDS)) != null) {
                        try { tt.descargar(job); bajados++; }
                        catch (Exception ex) { log("techtree " + job + ": " + causa(ex)); }
                        finally { pedidos.remove(job); }
                        if (bajados % 8 == 0) tareas.enUi(pantalla::iconosActualizados);
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    hilos.decrementAndGet();
                    tareas.enUi(pantalla::iconosActualizados);
                }
            });
        }
    }

    /** «techtree-precarga»: primero calienta los perfiles guardados (vía el Anfitrion), luego lo que falte del árbol de cada civ. */
    public void precargar() {
        anfitrion.precalentarPerfiles();
        tareas.enFondoDemonio("techtree-precarga", () -> {
            if (tt.asegurarDatos() != null) return;
            try {
                for (String civ : new ArrayList<>(obj(tt.datos().get("civs")).keySet())) {
                    if ("antiquity".equals(String.valueOf(obj(obj(tt.datos().get("civs")).get(civ)).get("era")))) continue;
                    Map<String, Object> arbol = tt.arbol(civ);
                    for (Object o : arr(arbol.get("units_techs"))) {
                        Map<String, Object> n = obj(o);
                        Path p = tt.rutaIcono(String.valueOf(n.get("use_type")), lng(n.get("picture_index")));
                        if (!Files.exists(p)) pedirIcono("img/" + n.get("use_type") + "/" + lng(n.get("picture_index")) + ".png");
                    }
                    for (Object o : arr(arbol.get("buildings"))) {
                        Map<String, Object> n = obj(o);
                        if (!Files.exists(tt.rutaIcono("Building", lng(n.get("picture_index"))))) pedirIcono("img/Building/" + lng(n.get("picture_index")) + ".png");
                    }
                    if (!Files.exists(tt.dir().resolve("img/Civs/" + civ.toLowerCase(Locale.ROOT) + ".png"))) pedirIcono("img/Civs/" + civ.toLowerCase(Locale.ROOT) + ".png");
                    Thread.sleep(50);
                }
                for (String rr : new String[]{ "food", "wood", "gold", "stone" })
                    if (!Files.exists(tt.dir().resolve("img/" + rr + ".png"))) pedirIcono("img/" + rr + ".png");
            } catch (Exception ex) { log("techtree precarga: " + causa(ex)); }
        });
    }
}
