package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static dev.tirador.aoe2radar.service.ReglasPartida.rivalCoincide;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Texto.normalizarNick;

/**
 * Las decisiones de Partidas que no tocan Swing. La vista ({@link PartidasView} y sus piezas) lee sus controles,
 * llama aquí y pinta lo que vuelve; este presentador decide. Al ser funciones puras (mismas entradas, misma salida,
 * sin Swing ni hilos), se prueban con JUnit normal, sin arrancar ninguna ventana.
 * <p>
 * Sigue siendo ESTÁTICO (no una instancia con una interfaz Pantalla, como PerfilPresenter o RatingsPresenter): la
 * pestaña no tiene un flujo asíncrono propio que orquestar aquí —fetchMatches, download, buscarAleatorias y buscarGte
 * siguen siendo {@code SwingWorker} en BusquedasPartidas/DescargasPartidas, cuyo publish/process/done ya funciona—,
 * y el estado que se decide (all, view, SUJETOS, filtroSujetos) lo leen la ventana, WatchlistView y los tests por la
 * fachada, así que se pasa por referencia a cada función en vez de copiarlo a un objeto nuevo.
 * <p>
 * Contiene: {@link #vigente} (¿sigo siendo la operación vigente?), el ojo de revelar, los filtros de la tabla y a
 * quién se atribuye cada partida ({@link #asignarRef}), con las sugerencias de rival.
 */
public final class PartidasPresenter {

    private PartidasPresenter() { }

    /** Lo que las decisiones de Partidas leen de la watchlist. {@link PartidasView.EnlaceWatchlist} lo amplía, así
     *  que la vista pasa su enlace tal cual (mismos métodos, mismas llamadas que antes). */
    public interface Watchlist {
        List<Player> seleccion();
        int seleccionSize();
        boolean modoTop();
        int totalJugadores();
        Player jugador(int indice);
        List<Player> todosJugadores();
        Player invitado();
    }

    /** Lo que las decisiones de Partidas leen del resto de la ventana. {@link PartidasView.Anfitrion} lo amplía (mismos
     *  métodos), así que la vista pasa su anfitrión tal cual. */
    public interface Entorno {
        String nombreVisible(long pid, String nombre);
        long perfilAbiertoPid();
        boolean perfilAbierto();
        String perfilNombreAbierto();
    }

    /** true si la operación que acaba de terminar (miSerial) sigue siendo la vigente: nadie la ha superado
     *  mientras corría (equivalente a {@code miSerial == opSerial} en el código de la 1.1). */
    public static boolean vigente(long miSerial, long serialActual) {
        return miSerial == serialActual;
    }

    /** El ojo de una fila: abierto si el modo consulta global está activo (y la fila no es de Guess the ELO,
     *  que nunca se destapa así) o si esta fila se reveló a mano. Mismo cálculo que {@code revelada(Match)}. */
    public static boolean revelada(boolean mostrarResultados, int gte, boolean enReveladas) {
        return (mostrarResultados && gte == 0) || enReveladas;
    }

    // ======================================================================
    // Filtros de la tabla
    // ======================================================================

    /** Filtro de modo: sin filtro, o coincide exactamente con el modo de la partida. */
    public static boolean pasaFiltroModo(String modoElegido, String todosLosModos, String modoPartida) {
        return modoElegido == null || todosLosModos.equals(modoElegido) || modoElegido.equals(modoPartida);
    }

    /** Filtro de mapa: índice 0 del combo = todos; si no, coincide exactamente con el mapa de la partida. */
    public static boolean pasaFiltroMapa(int indiceElegido, Object mapaElegido, String mapaPartida) {
        return indiceElegido <= 0 || String.valueOf(mapaElegido).equals(mapaPartida);
    }

    /** Filtro de periodo: índice 0 = todo; si no, la partida debe haber EMPEZADO dentro de los últimos N días
     *  (0/7/30/90/365, en el orden del combo). Sin fecha de inicio, la partida queda fuera. */
    public static boolean pasaFiltroPeriodo(int indicePeriodo, Instant inicioPartida, Instant ahora) {
        if (indicePeriodo <= 0) return true;
        int[] dias = { 0, 7, 30, 90, 365 };
        int d = dias[indicePeriodo];
        return inicioPartida != null && !inicioPartida.isBefore(ahora.minus(Duration.ofDays(d)));
    }

    /** Lo elegido en los controles de filtro de la barra superior (la vista lo lee de sus combos y del campo
     *  Rival; {@code rival} ya viene normalizado). */
    public record Filtros(String modo, String todosLosModos, int indiceMapa, Object mapa, int indicePeriodo, String rival) { }

    /** Los ids que deja ver el filtro de sujetos de la cabecera «Partidas de:»: los sujetos marcados y, si alguno
     *  tiene cuenta vinculada, todas las cuentas de la watchlist con el mismo vínculo. Vacío = sin filtro. */
    public static Set<Long> idsFiltroSujetos(List<Player> ultimosSujetos, Set<Long> filtroSujetos, Watchlist watchlist) {
        Set<Long> selIds = new HashSet<>();
        List<Player> baseFiltro = new ArrayList<>();
        for (Player s : ultimosSujetos) if (filtroSujetos.contains(s.id())) baseFiltro.add(s);
        for (Player p : baseFiltro) {
            selIds.add(p.id());
            if (p.vinculo() != 0)
                for (Player x : watchlist.todosJugadores())
                    if (x.vinculo() == p.vinculo()) selIds.add(x.id());
        }
        return selIds;
    }

    /** El corazón de applyFilters: vacía {@code view} (la MISMA lista que pinta la tabla) y la rellena con las
     *  partidas de {@code all} que pasan modo, mapa, periodo, rival y sujetos. A cada una le fija el jugador de
     *  referencia; a las que no han terminado, su estado («▶» en directo, «—» colgada) y «no en disco». Devuelve las
     *  terminadas que pasan, para que la vista mire en el disco (fuera del EDT) si están bajadas o en el juego. */
    public static List<Match> filtrar(List<Match> all, Filtros f, Set<Long> selIds, Consumer<Match> asignarRef,
                                      Predicate<Match> enCursoReal, Instant ahora, List<Match> view) {
        List<Match> terminadas = new ArrayList<>();
        view.clear();
        for (Match m : all) {
            if (!pasaFiltroModo(f.modo(), f.todosLosModos(), m.mode)) continue;
            if (!pasaFiltroMapa(f.indiceMapa(), f.mapa(), m.map)) continue;
            if (!pasaFiltroPeriodo(f.indicePeriodo(), m.started, ahora)) continue;
            if (!f.rival().isEmpty()) { asignarRef.accept(m); if (!rivalCoincide(m, f.rival())) continue; }
            if (!selIds.isEmpty()) {
                boolean alguno = false;
                for (long id : selIds) if (m.tieneJugador(id)) { alguno = true; break; }
                if (!alguno) continue;
            }
            asignarRef.accept(m);
            if (m.finished == null) {
                m.estado = enCursoReal.test(m) ? "\u25B6" : "\u2014";
                m.enDisco = false;
                m.enJuego = false;
            } else {
                terminadas.add(m);   // «en disco»/«en juego»: se miran en el disco, fuera del EDT
            }
            view.add(m);
        }
        return terminadas;
    }

    /** La línea de estado tras filtrar: «N de M partidas (según filtros).». */
    public static String mensajeFiltros(int visibles, int total) {
        return visibles + t(" de ", " of ") + total + t(" partidas (según filtros).", " games (per filters).");
    }

    /** Los modos de las partidas cargadas, en orden alfabético (el combo «Modo:» los pone tras «Todos los modos»). */
    public static TreeSet<String> modosDe(List<Match> all) {
        TreeSet<String> modos = new TreeSet<>();
        for (Match m : all) modos.add(m.mode);
        return modos;
    }

    /** Los mapas de las partidas cargadas (sin vacíos), en orden alfabético, para el combo «Mapa:». */
    public static TreeSet<String> mapasDe(List<Match> all) {
        TreeSet<String> mapas = new TreeSet<>();
        for (Match m : all) if (m.map != null && !m.map.isBlank()) mapas.add(m.map);
        return mapas;
    }

    // ======================================================================
    // Jugador de referencia y sugerencias de rival
    // ======================================================================

    /** Fija el jugador de referencia de la partida (la columna «Jugador»): el sujeto de la búsqueda de más rating;
     *  si no hay, el primero de la selección de la watchlist que la jugó; si no, el primero de la lista; y si
     *  ninguno, el de más rating de la partida (en empate, el primero). */
    public static void asignarRef(Match m, Set<Long> sujetos, Watchlist watchlist) {
        MatchPlayer suj = null;
        for (MatchPlayer mp : m.players)
            if (sujetos.contains(mp.id) && (suj == null || (mp.rating != null && (suj.rating == null || mp.rating > suj.rating)))) suj = mp;
        if (suj != null) { m.refId = suj.id; return; }
        for (Player p : watchlist.seleccion())
            if (m.tieneJugador(p.id())) { m.refId = p.id(); return; }
        for (int i = 0; i < watchlist.totalJugadores(); i++) {
            Player p = watchlist.jugador(i);
            if (m.tieneJugador(p.id())) { m.refId = p.id(); return; }
        }
        if (!m.players.isEmpty()) {
            MatchPlayer mejor = m.players.get(0);
            for (MatchPlayer p : m.players)
                if (p.rating != null && (mejor.rating == null || p.rating > mejor.rating)) mejor = p;
            m.refId = mejor.id;
        }
    }

    /** Las sugerencias del campo Rival: los rivales (en equipos, solo el equipo contrario) de las partidas cargadas
     *  cuyo nombre contiene lo tecleado, con su número de partidas, de más a menos y hasta 8. Vacía si no hay nada
     *  que sugerir o si lo tecleado ya es exactamente el único rival posible (no se muestra el popup). Las de Guess
     *  the ELO no cuentan. Como antes, fija el jugador de referencia de cada partida al recorrerla. */
    public static List<Map.Entry<String, Integer>> sugerenciasRival(List<Match> all, String filtroRival, Consumer<Match> asignarRef,
                                                                   BiFunction<Long, String, String> nombreVisible) {
        if (filtroRival.length() < 1 || all.isEmpty()) return List.of();
        Map<String, Integer> cuenta = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Match m : all) {
            if (m.gte > 0) continue;
            asignarRef.accept(m);
            MatchPlayer yo = null;
            for (MatchPlayer p : m.players) if (p.id == m.refId) yo = p;
            for (MatchPlayer p : m.players) {
                if (p.id == m.refId) continue;
                if (yo != null && m.players.size() > 2 && p.team == yo.team) continue;
                String vis = nombreVisible.apply(p.id, p.name);
                if (normalizarNick(vis).contains(filtroRival) || normalizarNick(p.name).contains(filtroRival)) cuenta.merge(vis, 1, Integer::sum);
            }
        }
        if (cuenta.isEmpty()) return List.of();
        List<Map.Entry<String, Integer>> lista = new ArrayList<>(cuenta.entrySet());
        lista.sort((a, b) -> b.getValue() - a.getValue());
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        int n = 0;
        for (Map.Entry<String, Integer> en : lista) {
            if (n++ >= 8) break;
            if (normalizarNick(en.getKey()).equals(filtroRival) && lista.size() == 1) return List.of();
            out.add(en);
        }
        return out;
    }

    // ======================================================================
    // A quién busca «Buscar partidas»
    // ======================================================================

    /** El texto del botón «Buscar partidas (X)» y, si X es el jugador del perfil, ese jugador (objetivoEtiqueta:
     *  fetchMatches lo busca a él; si no, null). */
    public record QuienBusca(String textoBoton, Player objetivo) { }

    /** A quién va a buscar el botón principal: el invitado; si no, el del perfil (abierto, o con la watchlist en ★)
     *  cuando no hay nadie seleccionado; si no, los seleccionados, «selecciona a alguien» en ★ o todo el grupo. */
    public static QuienBusca quienBusca(Watchlist watchlist, Entorno entorno) {
        String quien;
        Player objetivo = null;
        if (watchlist.invitado() != null) quien = entorno.nombreVisible(watchlist.invitado().id(), watchlist.invitado().name());
        else if (entorno.perfilAbiertoPid() > 0 && watchlist.seleccionSize() == 0 && (entorno.perfilAbierto() || watchlist.modoTop())) { quien = entorno.perfilNombreAbierto(); objetivo = new Player(entorno.perfilAbiertoPid(), entorno.perfilNombreAbierto(), ""); }
        else {
            int n = watchlist.seleccionSize();
            if (n > 0) quien = n + t(" seleccionado" + (n > 1 ? "s" : ""), " selected");
            else if (watchlist.modoTop()) quien = t("selecciona a alguien", "select someone");
            else quien = t("todo el grupo", "whole group");
        }
        return new QuienBusca(t("Buscar partidas", "Search games") + " (" + quien + ")", objetivo);
    }

    // ======================================================================
    // Mensajes de estado de buscar, azar, Guess the ELO, descargar y enviar al juego
    // ======================================================================

    /** «36 h», «3 días», «2 semanas»: la ventana en la unidad que se lee mejor. */
    public static String textoVentana(int horas) {
        if (horas % (24 * 7) == 0 && horas >= 24 * 7) { int w = horas / (24 * 7); return w + (w == 1 ? t(" semana", " week") : t(" semanas", " weeks")); }
        if (horas % 24 == 0 && horas >= 48) return (horas / 24) + t(" días", " days");
        return horas + " h";
    }

    /** Al acabar «Buscar partidas»: cuántas se ven de cuántas, en qué ventana, y los avisos de tope y de jugadores
     *  sin respuesta del servicio. */
    public static String mensajeBusqueda(int visibles, int total, int horas, boolean topeAlcanzado, int fallos) {
        return visibles + t(" de ", " of ") + total + t(" partidas en las últimas ", " games in the last ") + textoVentana(horas) + "."
                + (topeAlcanzado
                    ? "  \u26A0 " + t("Tope de la búsqueda alcanzado: puede faltar historial antiguo — acorta la ventana o filtra por modo.",
                                      "Search cap reached: older history may be missing — shorten the window or filter by mode.")
                    : "")
                + (fallos > 0
                    ? "  \u26A0 " + fallos + t(" jugador(es) SIN RESPUESTA del servicio (¿429/caído?): sus partidas faltan — reintenta en un minuto.",
                                                  " player(s) got NO RESPONSE from the service (429/down?): their games are missing — retry in a minute.")
                    : "");
    }

    /** «Al azar por ELO» detenido: lo encontrado hasta el corte (si algo) se aplica igual. */
    public static String mensajeAzarDetenido(int encontradas) {
        return t("Búsqueda detenida.", "Search stopped.")
                + (encontradas == 0 ? "" : "  " + encontradas
                   + t(" encontradas hasta el corte, aplicadas.", " found before the cut, applied."));
    }

    /** «Al azar por ELO» sin nada en el rango. */
    public static String mensajeAzarNada(int lo, int hi, int horas) {
        return t("Nada en ", "Nothing in ") + lo + "–" + hi
                + t(" en las últimas ", " in the last ") + horas
                + t(" h. Detalle del muestreo en descargas.log.", " h. Sampling details in descargas.log.");
    }

    /** «Al azar por ELO» con resultado. Con menos de 10, dice si el tramo se agotó (entonces {@code tramoAgotado} se
     *  consulta, y solo entonces, como antes) o si repetirla continúa donde lo dejó. */
    public static String mensajeAzar(int n, int lo, int hi, int horas, java.util.function.BooleanSupplier tramoAgotado) {
        String extra = "";
        if (n < 10 && tramoAgotado.getAsBoolean())
            extra = t(" No hay más con esos filtros: tramo entero revisado (amplía horas o rango).",
                      " Nothing else with those filters: whole bracket checked (widen hours or range).");
        else if (n < 10)
            extra = t(" Repite la búsqueda: continúa donde lo dejó.",
                      " Run it again: it picks up where it left off.");
        return n + t(" partidas 1v1 al azar, ELO ", " random 1v1s, ELO ") + lo + "–" + hi
                + t(", últimas ", ", last ") + horas + " h." + extra;
    }

    /** «Guess the ELO» con resultado: cuántas y el rango de nombres de archivo (el de la primera y el de la última). */
    public static String mensajeGte(List<Match> res) {
        return res.size() + t(" partidas Guess the ELO (archivos: «Guess the ELO ", " Guess the ELO games (files: “Guess the ELO ")
                + res.get(0).gte + t("»–«", "”–“") + res.get(res.size() - 1).gte
                + t("»). Adivina y comprueba con «Revelar resultado…».", "”). Guess, then check with “Reveal result…”.");
    }

    /** «Guess the ELO» sin partidas en la ventana. */
    public static String mensajeGteNada(int horas) {
        return t("Sin partidas para Guess the ELO en las últimas ", "No games for Guess the ELO in the last ") + horas
                + t(" h. Detalle en descargas.log.", " h. Details in descargas.log.");
    }

    /** Al acabar una descarga: guardadas de cuántas, dónde, cuántas fueron al juego y si hay detalle en el log. */
    public static String mensajeDescarga(boolean parada, int guardadas, int total, java.nio.file.Path carpeta, int alJuego) {
        return (parada ? t("Detenido. ", "Stopped. ") : "")
                + guardadas + "/" + total + t(" recs guardadas en ", " recs saved to ") + carpeta
                + (alJuego > 0 ? "  ·  " + alJuego + t(" al juego", " to the game") : "")
                + (guardadas < total ? t("  ·  detalle en descargas.log", "  ·  details in descargas.log") : "");
    }

    /** Al acabar «Enviar al juego»: cuántas se copiaron y cuántas ya estaban (se actualizan). */
    public static String mensajeEnvio(int copiadas, int yaEstaban) {
        return copiadas + t(" recs enviadas al juego", " recs sent to the game") +
                (yaEstaban > 0 ? " (" + yaEstaban + t(" ya estaban, actualizadas)", " were already there, refreshed)") : "") + ".";
    }
}
