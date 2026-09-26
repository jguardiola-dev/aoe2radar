package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.FuentePartidas;
import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.PaginaPartidas;
import dev.tirador.aoe2radar.util.Reloj;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

import static dev.tirador.aoe2radar.cache.HistorialDisco.ACT_DIAS;

/**
 * El historial de un jugador por la API (cuando no está en sfr-data o para «Cargar más»): páginas de partidas con
 * pausa de cortesía entre una y otra, fundidas con lo que ya se sabe, y guardado en memoria y en disco. La memoria es
 * la de la app (HistorialDisco.ACTIVIDAD_CACHE: la pintan las pantallas y la siembra el harness) y la recibe
 * construida, igual que el disco y la pausa (en la app, dormir: se corta con Detener).
 */
public final class HistorialPerfil {
    private final FuentePartidas api;
    private final Map<Long, Actividad> actividades;
    private final LongFunction<Actividad> cargar;
    private final Consumer<Actividad> guardar;
    private final LongConsumer pausa;
    private final int porPagina;
    private final long pausaMs;
    private final Reloj reloj;

    public HistorialPerfil(FuentePartidas api, Map<Long, Actividad> actividades, LongFunction<Actividad> cargar, Consumer<Actividad> guardar,
                           LongConsumer pausa, int porPagina, long pausaMs, Reloj reloj) {
        this.api = api; this.actividades = actividades; this.cargar = cargar; this.guardar = guardar;
        this.pausa = pausa; this.porPagina = porPagina; this.pausaMs = pausaMs; this.reloj = reloj;
    }

    /** La actividad que se sabe: de memoria o, si no está, del disco (y entonces se sube a memoria). null si ninguna. Sin red. */
    public Actividad actividad(long pid) {
        Actividad base = actividades.get(pid);
        if (base == null) { base = cargar.apply(pid); if (base != null) actividades.put(pid, base); }
        return base;
    }

    /**
     * Descarga historial página a página y avisa tras cada página con el estado parcial.
     * base == null: partidas nuevas desde la página 1 hasta maxPaginas (o el año). base con partidas: actualización —
     * baja páginas nuevas hasta encontrar una partida ya conocida y las funde con la base. mas == true: continúa
     * desde la última página de la base (botón «cargar más»).
     */
    public Actividad descargar(long pid, String nombre, Actividad base, boolean mas, int maxPaginas,
                               Consumer<Actividad> parcial, BooleanSupplier cancelar) throws IOException, InterruptedException {
        Objects.requireNonNull(parcial, "parcial: si no hace falta avisar, a -> { }");   // antes de gastar una llamada
        Instant limite = Instant.ofEpochMilli(reloj.ahoraMs()).minus(Duration.ofDays(ACT_DIAS));
        Set<Long> conocidos = new HashSet<>();
        List<Match> previas = new ArrayList<>();
        if (base != null) for (Match m : base.partidas()) { if (m.started != null && !m.started.isBefore(limite)) { previas.add(m); conocidos.add(m.id); } }
        boolean actualizar = base != null && !mas && !previas.isEmpty();
        int desde = mas && base != null ? base.paginas() + 1 : 1;
        int hasta = mas ? desde + maxPaginas - 1 : maxPaginas;
        List<Match> nuevas = new ArrayList<>();
        boolean completo = base != null && mas ? false : (base != null && actualizar ? base.completo() : false);
        int ultimaPagina = base == null || !mas ? 0 : base.paginas();
        boolean cancelado = false;
        for (int pag = desde; pag <= hasta; pag++) {
            if (cancelar.getAsBoolean()) { cancelado = true; break; }   // el usuario ya está mirando a otro: ni una llamada más
            PaginaPartidas ms = api.pagina(pid, pag, porPagina);
            ultimaPagina = pag;
            if (ms.brutas() == 0) { completo = true; break; }
            boolean parar = false;
            for (Match m : ms.partidas()) {
                if (m.started == null) continue;
                if (m.started.isBefore(limite)) { parar = true; completo = true; continue; }
                if (conocidos.contains(m.id)) { if (actualizar) parar = true; continue; }
                nuevas.add(m);
                conocidos.add(m.id);
            }
            if (ms.brutas() < porPagina) { completo = true; parar = true; }
            List<Match> fusion = new ArrayList<>(mas ? previas : nuevas);
            if (mas) fusion.addAll(nuevas); else fusion.addAll(previas);
            Actividad parcialA = new Actividad(pid, nombre, fusion, completo && !(actualizar && !parar), actualizar ? Math.max(base.paginas(), pag) : ultimaPagina, reloj.ahoraMs());
            parcial.accept(parcialA);
            if (parar) break;
            pausa.accept(pausaMs);
        }
        List<Match> fusion = new ArrayList<>(mas ? previas : nuevas);
        if (mas) fusion.addAll(nuevas); else fusion.addAll(previas);
        boolean fin = !cancelado && (completo || (actualizar && base.completo()));
        int paginas = actualizar ? Math.max(base.paginas(), ultimaPagina) : ultimaPagina;
        Actividad a = new Actividad(pid, nombre, fusion, fin, paginas, reloj.ahoraMs());
        actividades.put(pid, a);
        guardar.accept(a);
        return a;
    }

    /**
     * «Actualizar hoy»: las 50 partidas más recientes del companion (una llamada); las terminadas que no se
     * conocían se funden con la actividad en memoria (de más reciente a más antigua) y la dejan completa. Solo en
     * memoria, no en disco, como la 1.1. Si no había actividad, no guarda nada. Devuelve cuántas son nuevas.
     */
    public int traerHoy(long pid) throws IOException, InterruptedException {
        Actividad base = actividades.get(pid);
        Set<Long> vistos = new HashSet<>(); if (base != null) for (Match x : base.partidas()) vistos.add(x.id);
        List<Match> extra = new ArrayList<>();
        Iterable<Match> leidas = api.partidas(pid, 1, 50);
        for (Match x : leidas) { if (x != null && x.finished != null && !vistos.contains(x.id)) { x.refId = pid; extra.add(x); } }
        int nuevas = extra.size();
        if (base != null && !extra.isEmpty()) {
            List<Match> todas = new ArrayList<>(extra); todas.addAll(base.partidas());
            todas.sort((a, b) -> { Instant x = a.started == null ? Instant.EPOCH : a.started, y = b.started == null ? Instant.EPOCH : b.started; return y.compareTo(x); });
            actividades.put(pid, new Actividad(pid, base.nombre(), todas, true, base.paginas(), reloj.ahoraMs()));
        }
        return nuevas;
    }
}
