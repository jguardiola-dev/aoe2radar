package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Comparado;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.ConsultasLadder;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.RatingsService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * La lógica de la pestaña Ratings sin Swing: cargar los resúmenes del ladder, buscar y añadir jugadores a la
 * comparación, y mantenerla sincronizada con la selección de la watchlist. Sale de SpoilerFreeRecs (métodos
 * ladderSugerir/ladderAnadir/ladderSincronizarSeleccion de la 1.1) tal cual, salvo que ya no toca los campos de
 * Swing directamente: se los pide a {@link Pantalla}, que implementa ui.RatingsView.
 * <p>Hilos: aquí se decide "cargando" y se marca SIEMPRE en el hilo que llama (el EDT en la app real, ver el
 * aviso del revisor de la oleada A: no es atómico, pero es seguro porque solo se toca desde el EDT). El trabajo
 * lento va por {@code tareas.enFondo}, con los mismos nombres de hilo que tenía la 1.1.
 */
public final class RatingsPresenter {

    /** Máximo de jugadores en el comparador del ladder (buscados a mano + seleccionados en la watchlist). */
    public static final int MAX_COMPARADOS = 10;

    /** Lo que el presentador necesita de la vista: estado de la comparación y aviso de cada resultado. */
    public interface Pantalla {
        /** La lista mutable de comparados de la vista (la misma que pinta y que toca RegresionCapturas). */
        List<Comparado> comparados();
        /** Repinta chips, tabla y gráficos a partir de comparados(). */
        void refrescarComparados();
        /** Mensaje de la barra de estado del panel (vacío para borrarlo). */
        void estado(String texto);
        /** ¿Sigue seleccionado en la watchlist? (se re-comprueba al volver de una consulta lenta). */
        boolean seleccionado(long pid);
        /** El nombre visible (alias si lo tiene) de un jugador de la watchlist. */
        String nombreVisible(long pid, String nombre);
        /** Arranca el aviso de carga (texto + parpadeo de progreso). */
        void cargaIniciada();
        /** Para el parpadeo de progreso: SIEMPRE antes de marcar cargando(false) (mismo orden que la 1.1: tick.stop(); ladderCargando = false;). */
        void pararProgreso();
        /** Ya puede pintar el resultado de la carga; error null si fue bien. */
        void cargaTerminada(String error);
        /** Sustituye las sugerencias del buscador por estas (vacío las oculta). */
        void mostrarSugerencias(List<String[]> resultados);
        /** Oculta las sugerencias del buscador (se llama antes de lanzar una búsqueda nueva). */
        void ocultarSugerencias();
    }

    private final RatingsService servicio;
    private final BusquedaPerfiles busqueda;
    private final ProfileService perfiles;
    private final Tareas tareas;
    private final Pantalla pantalla;

    public RatingsPresenter(RatingsService servicio, BusquedaPerfiles busqueda, ProfileService perfiles, Tareas tareas, Pantalla pantalla) {
        this.servicio = servicio;
        this.busqueda = busqueda;
        this.perfiles = perfiles;
        this.tareas = tareas;
        this.pantalla = pantalla;
    }

    /** El «Top %» que se muestra: con todos y rango conocido, el exacto por rango; si no, por rating sobre la campana elegida. */
    public String topDe(Comparado c, String lb, boolean soloActivos) {
        String p = !soloActivos && c.rank(lb) > 0 ? ConsultasLadder.percentilRango(lb, c.rank(lb)) : null;
        return p != null ? p : ConsultasLadder.percentilRating(lb, soloActivos, c.rating(lb));
    }

    /**
     * Al abrir Ratings: si ya hay una carga en curso, no relanza (el chequeo y el marcado ocurren aquí mismo,
     * en el hilo que llama, que en la app es siempre el EDT). Si no, pide los resúmenes en "ladder-datos".
     */
    public void cargar() {
        if (servicio.cargando()) return;
        servicio.cargando(true);
        pantalla.cargaIniciada();
        tareas.enFondo("ladder-datos", () -> {
            String err = servicio.asegurar(false);
            tareas.enUi(() -> {
                pantalla.pararProgreso();   // primero para el tick, igual que la 1.1 (tick.stop(); ladderCargando = false; …)
                servicio.cargando(false);
                pantalla.cargaTerminada(err);
            });
        });
    }

    /**
     * Sugerencias de jugadores al teclear en el buscador. {@code actual} da el texto vigente del buscador en el
     * momento de mirar la respuesta: si ya no coincide con lo pedido, se descarta (el usuario siguió escribiendo).
     */
    public void sugerir(String q, Supplier<String> actual) {
        pantalla.ocultarSugerencias();
        if (q.length() < 2) return;
        tareas.enFondo("ladder-sugerir", () -> {
            List<String[]> res = busqueda.sugerir(q);
            tareas.enUi(() -> {
                if (!q.equals(actual.get())) return;
                pantalla.mostrarSugerencias(res);
            });
        });
    }

    /** Añade un jugador a la comparación (buscado a mano o seleccionado en la watchlist). */
    public void anadir(long pid, String nombre, boolean deSeleccion) {
        for (Comparado x : pantalla.comparados()) if (x.pid() == pid) return;
        pantalla.estado(t("Consultando a ", "Looking up ") + nombre + "…");
        tareas.enFondo("ladder-perfil", () -> {
            FichaPerfil perfil = perfiles.ficha(pid);
            tareas.enUi(() -> {
                pantalla.estado("");
                if (perfil == null) { pantalla.estado(t("No se pudo consultar a ", "Couldn't look up ") + nombre + "."); return; }
                List<Comparado> comparados = pantalla.comparados();
                for (Comparado x : comparados) if (x.pid() == pid) return;
                if (deSeleccion && !pantalla.seleccionado(pid)) return;   // se deseleccionó mientras se consultaba
                Map<String, int[]> m = perfil.ladders();
                if (m.isEmpty()) { pantalla.estado(nombre + t(" no tiene rating en ningún ladder.", " has no rating on any ladder.")); return; }
                if (comparados.size() >= MAX_COMPARADOS) {
                    int i = -1;
                    for (int j = 0; j < comparados.size(); j++) if (!comparados.get(j).deSeleccion()) { i = j; break; }
                    if (deSeleccion || i < 0) { pantalla.estado(t("Máximo ", "Max ") + MAX_COMPARADOS + t(" jugadores en la comparación: quita alguno (×).", " players compared: remove one (×).")); return; }
                    comparados.remove(i);   // cae el buscado a mano más antiguo
                }
                comparados.add(new Comparado(pid, nombre, m, String.valueOf(perfil.pais()), deSeleccion));
                pantalla.refrescarComparados();
            });
        });
    }

    /** Con el Ladder abierto, la selección de la watchlist se refleja en las campanas: entra lo seleccionado, sale lo deseleccionado. */
    public void sincronizarSeleccion(List<Player> sel) {
        List<Comparado> comparados = pantalla.comparados();
        Set<Long> ids = new HashSet<>();
        for (Player p : sel) ids.add(p.id());
        boolean cambio = comparados.removeIf(c -> c.deSeleccion() && !ids.contains(c.pid()));
        if (cambio) pantalla.refrescarComparados();
        List<Player> nuevos = new ArrayList<>();
        int hueco = MAX_COMPARADOS - comparados.size(), omitidos = 0;
        for (Player p : sel) {
            boolean ya = false;
            for (Comparado c : comparados) if (c.pid() == p.id()) { ya = true; break; }
            if (ya) continue;
            if (nuevos.size() >= hueco) { omitidos++; continue; }
            nuevos.add(p);
        }
        if (omitidos > 0) pantalla.estado(t("Máximo ", "Max ") + MAX_COMPARADOS + t(" jugadores en la comparación: quedan fuera ", " players compared; left out: ") + omitidos + ".");
        for (Player p : nuevos) anadir(p.id(), pantalla.nombreVisible(p.id(), p.name()), true);
    }
}
