package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.service.EstadoVivo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static dev.tirador.aoe2radar.service.AzarService.ajustarRefAzar;
import static dev.tirador.aoe2radar.service.ReglasPartida.rivalCoincide;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
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
 * quién se atribuye cada partida ({@link #asignarRef}), con las sugerencias de rival; a quién busca el botón
 * ({@link #quienBusca}) y a quién se piden las partidas ({@link #elegirBuscados}); el recorrido paginado de
 * «Buscar partidas» ({@link #recorrer}, lo que corre en el hilo del SwingWorker); lo que se hace con los resultados
 * (azar, vivos, estados conservados, vivas al descargar) y los mensajes de estado.
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
        Player objetivoForzado();
        boolean soloVivosMarcado();
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

    // ======================================================================
    // Lo que se hace con los resultados: azar, buscar y descargar
    // ======================================================================

    /** Los sujetos de una tirada de «Al azar por ELO»: el titular de cada partida (sin repetir, en el orden de las
     *  partidas) y sus ids, para la cabecera «Partidas de:» y la negrita de la tabla. */
    public record SujetosAzar(List<Player> refs, Set<Long> refIds) { }

    /** Marca cada partida como del azar, le fija el titular (AzarService.ajustarRefAzar: con filtro de civ, quien la
     *  jugó) y reúne los titulares, cada uno una vez, con el nombre que enseña la columna «Jugador». */
    public static SujetosAzar sujetosAzar(List<Match> res, String civSel, Function<Match, String> refNombre) {
        List<Player> refs = new ArrayList<>();
        Set<Long> refIds = new HashSet<>();
        for (Match m : res) {
            m.azar = true;
            ajustarRefAzar(m, civSel);
            if (refIds.add(m.refId)) {
                String nom = refNombre.apply(m);
                refs.add(new Player(m.refId, nom, "", 0));
            }
        }
        return new SujetosAzar(refs, refIds);
    }

    /** Los ids de unos jugadores, en el mismo orden. */
    public static List<Long> ids(List<Player> jugadores) {
        List<Long> ids = new ArrayList<>();
        for (Player pl : jugadores) ids.add(pl.id());
        return ids;
    }

    /** Los ids de toda la lista de la watchlist (lo que la descarga manda a RecService como «seguidos»). */
    public static Set<Long> idsDeLaLista(Watchlist watchlist) {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < watchlist.totalJugadores(); i++) ids.add(watchlist.jugador(i).id());
        return ids;
    }

    /** Tras «Buscar partidas»: cada buscado que BarridoVivos da por jugando pasa a jugando (con su partida y su
     *  texto) y el que da por fuera, a fuera; del resto no se sabe nada nuevo y no se toca. */
    public static void aplicarVivos(BarridoVivos.DecisionBuscar dec, List<Player> tracked, EstadoVivo estado) {
        for (Player pl : tracked) {
            Long v = dec.vivos().get(pl.id());
            if (v != null) { estado.marcarJugando(pl.id(), v, dec.infos().get(pl.id())); }
            else if (dec.fuera().contains(pl.id())) { estado.marcarFuera(pl.id()); }
        }
    }

    /** Una búsqueda que vuelve con partidas ya en la tabla trae OTROS Match de las mismas partidas: cada uno sin
     *  estado toma el que tenía la fila vieja («descargando…», «✓ guardada»…), por id de partida. */
    public static void conservarEstados(List<Match> viejos, List<Match> nuevos) {
        Map<Long, String> estados = new java.util.HashMap<>();
        for (Match viejo : viejos) if (!viejo.estado.isBlank()) estados.put(viejo.id, viejo.estado);
        for (Match nuevo : nuevos) { String e = estados.get(nuevo.id); if (e != null && nuevo.estado.isBlank()) nuevo.estado = e; }
    }

    /** Lo que se pidió descargar, repartido: las terminadas (se descargan) y las que siguen en directo (no). */
    public record Separadas(List<Match> terminadas, List<Match> vivas) { }

    public static Separadas separarVivas(List<Match> pedidas) {
        List<Match> terminadas = new ArrayList<>();
        List<Match> vivas = new ArrayList<>();
        for (Match m : pedidas) (m.finished == null ? vivas : terminadas).add(m);
        return new Separadas(terminadas, vivas);
    }

    // ======================================================================
    // A quién pide las partidas fetchMatches, y el filtro de la cabecera
    // ======================================================================

    /** «Buscar partidas» con el perfil abierto, sin selección ni nadie ya elegido: se busca al del perfil (la vista
     *  lo fija como objetivo forzado antes de elegir). */
    public static boolean buscaAlDelPerfil(Watchlist watchlist, Entorno entorno) {
        return watchlist.objetivoForzado() == null && watchlist.invitado() == null && entorno.perfilAbierto() && entorno.perfilAbiertoPid() > 0 && watchlist.seleccionSize() == 0;
    }

    /** No hay a quién buscar: lista vacía, sin invitado ni objetivo forzado. */
    public static boolean nadieABuscar(Watchlist watchlist) {
        return watchlist.totalJugadores() == 0 && watchlist.invitado() == null && watchlist.objetivoForzado() == null;
    }

    /** A quién se piden las partidas y qué debe hacer la vista con lo elegido: limpiar el objetivo forzado si se usó,
     *  avisar si en ★ se recortó a 15, o pedir una selección en ★ (entonces {@code buscados} va vacía y no se busca). */
    public record Eleccion(List<Player> buscados, boolean deForzado, boolean recortadaA15, boolean faltaSeleccionTop) { }

    /** El orden de siempre: el objetivo forzado (perfil, «Ver sus partidas»…); el invitado; el jugador que nombra el
     *  botón si no hay selección; en ★, los seleccionados (15 como mucho) o, con «● Jugando», toda la lista; y fuera
     *  de ★, la selección o toda la lista. */
    public static Eleccion elegirBuscados(Watchlist watchlist, Player objetivoEtiqueta) {
        List<Player> sel = new ArrayList<>();
        boolean deForzado = false, recortada = false;
        if (watchlist.objetivoForzado() != null) {
            sel.add(watchlist.objetivoForzado());
            deForzado = true;
        } else if (watchlist.invitado() != null) {
            sel.add(watchlist.invitado());
        } else if (objetivoEtiqueta != null && watchlist.seleccionSize() == 0) {
            sel.add(objetivoEtiqueta);
        } else if (watchlist.modoTop()) {
            List<Player> selTop = watchlist.seleccion();
            if (selTop.size() > 15) {
                selTop = selTop.subList(0, 15);
                recortada = true;
            }
            if (!selTop.isEmpty()) sel.addAll(selTop);
            else if (watchlist.soloVivosMarcado())
                for (int i = 0; i < watchlist.totalJugadores(); i++) sel.add(watchlist.jugador(i));
            else return new Eleccion(sel, false, false, true);
        } else {
            List<Player> selNorm = watchlist.seleccion();
            if (!selNorm.isEmpty()) sel.addAll(selNorm);
            else for (int i = 0; i < watchlist.totalJugadores(); i++) sel.add(watchlist.jugador(i));
        }
        return new Eleccion(sel, deForzado, recortada, false);
    }

    /** Clic en un sujeto de «Partidas de:»: con Ctrl, lo suma o lo quita; sin Ctrl, deja solo a ese, y si ya era el
     *  único marcado, quita el filtro. Cambia {@code filtroSujetos} en su sitio (el mismo conjunto de la vista). */
    public static void alternarFiltroSujeto(Set<Long> filtroSujetos, long pid, boolean ctrl) {
        if (ctrl) { if (!filtroSujetos.remove(pid)) filtroSujetos.add(pid); }
        else if (filtroSujetos.size() == 1 && filtroSujetos.contains(pid)) filtroSujetos.clear();
        else { filtroSujetos.clear(); filtroSujetos.add(pid); }
    }

    /** Al acabar «Enviar al juego»: cuántas se copiaron y cuántas ya estaban (se actualizan). */
    public static String mensajeEnvio(int copiadas, int yaEstaban) {
        return copiadas + t(" recs enviadas al juego", " recs sent to the game") +
                (yaEstaban > 0 ? " (" + yaEstaban + t(" ya estaban, actualizadas)", " were already there, refreshed)") : "") + ".";
    }

    /** Qué hace Enter en la tabla de Partidas. */
    public enum AccionEnter { DESCARGAR, AVISAR_SIN_SELECCION, NADA }

    /** Enter en la tabla (decisión de Jorge, 1.3): con una descarga en curso no hace nada (no lanza otra); sin
     *  descarga en curso, descarga la selección o, si no hay ninguna, avisa «No hay partidas seleccionadas.». */
    public static AccionEnter accionEnter(boolean descargaEnCurso, int seleccionadas) {
        if (descargaEnCurso) return AccionEnter.NADA;
        return seleccionadas > 0 ? AccionEnter.DESCARGAR : AccionEnter.AVISAR_SIN_SELECCION;
    }

    /** El aviso de Enter (y de «Descargar seleccionadas») sin nada seleccionado. */
    public static String mensajeSinSeleccion() {
        return t("No hay partidas seleccionadas.", "No games selected.");
    }

    // ======================================================================
    // El recorrido de «Buscar partidas» (el doInBackground de fetchMatches, antes en BusquedasPartidas)
    // ======================================================================

    /** Una página de partidas de un jugador (en la app, Anfitrion.paginaDePartidas: la red). */
    interface Paginador { Iterable<Match> pagina(long pid, int pagina, int porPagina) throws Exception; }

    /** Lo que deja el recorrido de fetchMatches: las partidas (la más reciente primero), si se tocó el tope, cuántos
     *  jugadores fallaron, quiénes respondieron entero y si se detuvo a medias (entonces la lista está incompleta). */
    record Recorrido(List<Match> lista, boolean topeAlcanzado, int fallos, Set<Long> exitosos, boolean detenida) { }

    /** El doInBackground de fetchMatches, sin Swing (para poder probarlo): páginas por jugador hasta la ventana,
     *  tope 6 páginas / 600 partidas. {@code parar} se mira antes de cada jugador, antes de cada página y al fallar
     *  una: si dice que sí, el recorrido acaba entero y vuelve con {@code detenida}. */
    static Recorrido recorrer(List<Player> tracked, Instant cutoff, int perPage, long pausaMs, Paginador paginas,
                              java.util.function.Predicate<Match> enCursoReal, java.util.function.BooleanSupplier parar,
                              java.util.function.Consumer<String> progreso) {
        Map<Long, Match> unicos = new LinkedHashMap<>();
        Set<Long> exitosos = new HashSet<>();
        boolean topeAlcanzado = false, detenida = false;
        int fallos = 0;
        final int MAX_PAGINAS = 6, MAX_TOTAL = 600;
        for (Player pl : tracked) {
            if (parar.getAsBoolean()) { detenida = true; break; }
            boolean fallo = false;
            for (int pagina = 1; pagina <= MAX_PAGINAS; pagina++) {
                if (parar.getAsBoolean()) { detenida = true; break; }
                progreso.accept(t("Consultando ", "Checking ") + pl.name() + (pagina > 1 ? " (" + t("pág. ", "p. ") + pagina + ")" : "") + "…");
                boolean seguir = false;
                try {
                    Iterable<Match> leidas = paginas.pagina(pl.id(), pagina, perPage);
                    int n = 0; Instant masAntigua = null;
                    for (Match m : leidas) {
                        if (m == null) continue;
                        n++;
                        Instant ref = m.finished != null ? m.finished : m.started;
                        if (ref != null && (masAntigua == null || ref.isBefore(masAntigua))) masAntigua = ref;
                        if (m.finished == null && !enCursoReal.test(m)) continue;
                        if (m.finished != null && m.finished.isBefore(cutoff)) continue;
                        unicos.putIfAbsent(m.id, m);
                    }
                    seguir = n >= perPage && masAntigua != null && masAntigua.isAfter(cutoff);
                    if (seguir && pagina == MAX_PAGINAS) topeAlcanzado = true;
                    if (unicos.size() >= MAX_TOTAL) { topeAlcanzado = true; seguir = false; }
                    Thread.sleep(pausaMs);
                } catch (Exception ex) {
                    fallo = true;
                    if (parar.getAsBoolean()) { detenida = true; break; }   // el freno cortó la espera: se para todo
                    if (ex instanceof InterruptedException) break;
                    fallos++;
                    progreso.accept(t("Aviso: fallo con ", "Heads-up: failed with ") + pl.name() + " (" + causa(ex) + ")");
                }
                if (!seguir) break;
            }
            if (detenida) break;
            if (!fallo) exitosos.add(pl.id());
            if (unicos.size() >= MAX_TOTAL) break;
        }
        List<Match> lista = new ArrayList<>(unicos.values());
        lista.sort(Comparator.comparing((Match m) -> m.finished == null ? Instant.MAX : m.finished).reversed());
        return new Recorrido(lista, topeAlcanzado, fallos, exitosos, detenida);
    }
}
