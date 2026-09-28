package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Perfil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.tirador.aoe2radar.util.Hilos.avisarSiUi;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Qué enseña la tarjeta flotante de la Watchlist y de dónde sale (1.4, encendida). Sin Swing:
 * <ul>
 * <li><b>Nocturno primero</b>: con las chispas de sfr-data (Fuentes.nocturna), cero llamadas.</li>
 * <li>Si no alcanza, <b>una sola llamada</b>: /profiles/{pid} (Fuentes.perfil, con la caché por URL de CompanionApi y
 *     detrás del freno de ApiClient). La gráfica sale de la serie de rating 1v1 del propio perfil; antes eran /profiles
 *     más una o dos páginas de /matches.</li>
 * <li><b>Una carga a la vez por pid</b> (reservar/liberar): pasar dos veces el ratón por la misma fila mientras la
 *     primera carga sigue en vuelo no lanza otra.</li>
 * </ul>
 * La tarjeta ya pintada se guarda 10 min en la caché de la ventana (Caducidad.TARJETA), como antes.
 * <p>Hilos: cargar va a la red y nunca se llama desde el EDT (aviso en el log si pasa); reservar/liberar valen desde
 * cualquier hilo; las decisiones (procede, pintarAlLlegar) son puras.
 */
public final class TarjetaService {

    /** Las series de /profiles pueden ser largas: como las chispas nocturnas, se miran las 25 más recientes. */
    public static final int MAX_PUNTOS = 25;

    public interface Fuentes {
        /** La tarjeta desde el nocturno, o null si no alcanza (ver TarjetaPerfil.desdeNocturno). Puede bajar las chispas. */
        TarjetaPerfil.Datos nocturna(long pid);
        /** /profiles/{pid}, con caché y detrás del freno. */
        Perfil perfil(long pid) throws Exception;
    }

    /**
     * html y gráfica para pintar; perfil, el de la API si se pidió y llegó (para aprender canal y país), si no null;
     * guardar: false si la API falló (una tarjeta vacía no se guarda 10 min: el siguiente paso del ratón lo reintenta).
     */
    public record Resultado(String html, int[] spark, Perfil perfil, boolean guardar) { }

    private final Fuentes fuentes;
    private final Set<Long> enVuelo = ConcurrentHashMap.newKeySet();

    public TarjetaService(Fuentes fuentes) { this.fuentes = fuentes; }

    /** true si nadie está cargando ya la tarjeta de pid (y desde ahora la carga quien llama: debe liberar al acabar). */
    public boolean reservar(long pid) { return enVuelo.add(pid); }

    public void liberar(long pid) { enVuelo.remove(pid); }

    /** Como cargar(pid, nombre, sigue) con una tarjeta que siempre interesa. */
    public Resultado cargar(long pid, String nombre) { return cargar(pid, nombre, () -> true); }

    /**
     * La tarjeta de pid: del nocturno o, si no alcanza, de una llamada a /profiles. Un fallo de red no lanza: sale sin
     * datos. sigue: justo antes de salir a la API se pregunta si la tarjeta todavía interesa (el ratón sigue en esa fila);
     * si no, null y ninguna llamada: pasear el ratón por la lista no gasta fichas del freno en tarjetas que nadie mira.
     */
    public Resultado cargar(long pid, String nombre, java.util.function.BooleanSupplier sigue) {
        avisarSiUi("TarjetaService.cargar");
        TarjetaPerfil.Datos d = null;
        try { d = fuentes.nocturna(pid); }
        catch (RuntimeException ex) { log("perfil card: nocturno de " + pid + ": " + causa(ex)); }
        if (d != null) return new Resultado(TarjetaPerfil.html(nombre, d), d.spark(), null, true);
        if (!sigue.getAsBoolean()) return null;
        Perfil pf = null;
        try { pf = fuentes.perfil(pid); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        catch (Exception ex) { log("perfil card: fallo con " + pid + ": " + causa(ex)); }
        TarjetaPerfil.Datos da = desdePerfil(pf);
        return new Resultado(TarjetaPerfil.html(nombre, da), da.spark(), pf, pf != null);
    }

    /** Los datos de la tarjeta desde /profiles (los mismos campos que antes; la gráfica, de su serie 1v1). null → vacíos. */
    public static TarjetaPerfil.Datos desdePerfil(Perfil pf) {
        if (pf == null) return new TarjetaPerfil.Datos("", "", 0, null, null, null, null, null, false);
        String pais = "", clan = "";
        String c = pf.pais();
        if (c != null && !"null".equals(c)) pais = c.toUpperCase();
        String cl = pf.clan();
        if (cl != null && !"null".equals(cl)) clan = cl;
        Integer rating = null, maxRating = null, wins = null, losses = null;
        for (Perfil.Ladder lb : pf.ladders()) {
            String lid = String.valueOf(lb.id());
            if (!"rm_1v1".equals(lid) && !"3".equals(lid)) continue;
            if (lb.rating() != null) rating = lb.rating();
            if (lb.ratingMax() != null) maxRating = lb.ratingMax();
            if (lb.ganadas() != null) wins = lb.ganadas();
            if (lb.perdidas() != null) losses = lb.perdidas();
            break;
        }
        List<Perfil.Punto> puntos = new ArrayList<>();
        for (Perfil.Serie s : pf.series()) {
            String lid = String.valueOf(s.id());
            if (!"rm_1v1".equals(lid) && !"3".equals(lid)) continue;
            for (Perfil.Punto p : s.puntos()) if (p.fecha() != null && p.rating() != null) puntos.add(p);
        }
        puntos.sort(Comparator.comparing(Perfil.Punto::fecha).reversed());   // de la más reciente a la más antigua
        List<Integer> serie = new ArrayList<>();
        for (Perfil.Punto p : puntos) { if (serie.size() >= MAX_PUNTOS) break; serie.add(p.rating()); }
        TarjetaPerfil.Chispa ch = TarjetaPerfil.chispa(serie);
        return new TarjetaPerfil.Datos(pais, clan, pf.partidas(), rating, maxRating, wins, losses, ch.spark(), ch.pocos1v1());
    }

    /** ¿Se puede enseñar ahora? La ventana es la activa, no hay un menú abierto y el ratón está sobre la lista visible. */
    public static boolean procede(boolean ventanaActiva, boolean menuAbierto, boolean listaVisible, boolean ratonEncima) {
        return ventanaActiva && !menuAbierto && listaVisible && ratonEncima;
    }

    /**
     * Al llegar una carga (done): se pinta si era una tarjeta fijada, o si el ratón sigue en esa fila y todavía procede.
     * Si ya está en otra fila, o la tarjeta se ocultó mientras tanto (hoverPid 0: clic, scroll, lista rehecha), no: la
     * fila bajo el ratón puede ser otra (en la 1.1, con hoverPid 0 también se pintaba).
     */
    public static boolean pintarAlLlegar(long pid, long hoverPid, boolean fijar, boolean procede) {
        return fijar || (hoverPid == pid && procede);
    }
}
