// ============================================================================
// SpoilerFreeRecs 1.0 — descarga recs recientes de AoE2 DE sin ver el resultado.
// Creado por Jorge «12Tirador» Guardiola · twitch.tv/12tirador · Licencia MIT
//
// Un solo archivo A PROPÓSITO: compila con un javac, sin Maven ni Gradle, con
// FlatLaf como única dependencia. El historial de desarrollo está en CHANGELOG.md.
//
// Índice de secciones (buscar la marca ---- en el código):
//   1. Constantes, versión, i18n (t) y configuración
//   2. Modelo: Player, Match, MatchPlayer, familias y aliases
//   3. Ventana principal: barra superior, watchlist, tabla, directos, estado
//   4. Watchlist: grupos, tops (ladder/país), invitado, sujetos, contextuales
//   5. Búsqueda de partidas, azar por ELO, Guess the ELO, modo consulta
//   6. Vigilantes: vivos en lote, Twitch, cierre de directos
//   7. Descargas, savegame, CaptureAge, autoarranque
//   8. Perfiles, hover-card, nicks (Steam), vinculadas
//   9. HTTP, JSON, utilidades
//
// Créditos: datos de aoe2companion (Dennis Keil); análisis enlazados a
// aoe2insights; directos vía Twitch; look & feel FlatLaf (Apache 2.0); tech tree de aoe2techtree (HSZemi, MIT);
// espectación con CaptureAge. Age of Empires II es marca de Microsoft /
// World's Edge: esta es una herramienta no oficial hecha por un fan.
//
// Fuentes de datos:
//   - Metadatos:  https://data.aoe2companion.com/api  (API pública documentada)
//   - Recs:       https://aoe.ms/replay/?gameId=...&profileId=...
//     (servidores oficiales; las recs solo están disponibles un tiempo limitado
//      y alguna partida puede no tener rec subida)
// ============================================================================

package dev.tirador.aoe2radar;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.CivFila;
import dev.tirador.aoe2radar.model.Clasificacion;
import dev.tirador.aoe2radar.model.Comparado;
import dev.tirador.aoe2radar.model.FilaClasificacion;
import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.LadderHist;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Matchup;
import dev.tirador.aoe2radar.model.PaginaLb;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.model.Rejilla;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.service.Aleatorio.ProveedorPaginas;
import dev.tirador.aoe2radar.util.Json;
import dev.tirador.aoe2radar.ui.PanelScrollable;
import dev.tirador.aoe2radar.ui.PctRenderer;
import dev.tirador.aoe2radar.ui.WrapLayout;

import static dev.tirador.aoe2radar.api.Cancelacion.detieneEsteHilo;
import static dev.tirador.aoe2radar.api.Cancelacion.hiloOperacion;
import static dev.tirador.aoe2radar.api.Cancelacion.opEnCurso;
import static dev.tirador.aoe2radar.api.Cancelacion.stopOperacion;
import static dev.tirador.aoe2radar.api.Freno.CONTROL;
import static dev.tirador.aoe2radar.api.Freno.CONTROL_URL;
import static dev.tirador.aoe2radar.api.Freno.THROTTLE;
import static dev.tirador.aoe2radar.api.Freno.ctrlMult;
import static dev.tirador.aoe2radar.api.Freno.ctrlOn;
import static dev.tirador.aoe2radar.api.Http.API;
import static dev.tirador.aoe2radar.api.Http.HTTP;
import static dev.tirador.aoe2radar.api.Http.TRANSPORTE;
import static dev.tirador.aoe2radar.api.Http.UA;
import static dev.tirador.aoe2radar.api.Http.descargarBytes;
import static dev.tirador.aoe2radar.api.Http.nuevoHttp;
import static dev.tirador.aoe2radar.api.Http.req;
import static dev.tirador.aoe2radar.api.Parseo.MAPA_IMG_PATRON;
import static dev.tirador.aoe2radar.api.Parseo.parseMatch;
import static dev.tirador.aoe2radar.api.Recs.descargarRec;
import static dev.tirador.aoe2radar.cache.Anotaciones.ALIASES;
import static dev.tirador.aoe2radar.cache.Anotaciones.NOTAS;
import static dev.tirador.aoe2radar.cache.Anotaciones.cargarAliases;
import static dev.tirador.aoe2radar.cache.Anotaciones.cargarNotas;
import static dev.tirador.aoe2radar.cache.Anotaciones.nombreVisible;
import static dev.tirador.aoe2radar.cache.Anotaciones.notaDe;
import static dev.tirador.aoe2radar.cache.CachePerfiles.ELO_VINC;
import static dev.tirador.aoe2radar.cache.CachePerfiles.FAMILIA_CACHE;
import static dev.tirador.aoe2radar.cache.CachePerfiles.PERFIL_CACHE;
import static dev.tirador.aoe2radar.cache.CachePerfiles.VINCULADAS_CACHE;
import static dev.tirador.aoe2radar.cache.Canales.CANAL_DE;
import static dev.tirador.aoe2radar.cache.Canales.aprenderCanal;
import static dev.tirador.aoe2radar.cache.Canales.cargarCanales;
import static dev.tirador.aoe2radar.cache.Catalogos.CIVS_CAT;
import static dev.tirador.aoe2radar.cache.Catalogos.MAPAS_CAT;
import static dev.tirador.aoe2radar.cache.Catalogos.cargarCatalogos;
import static dev.tirador.aoe2radar.cache.Directorios.LADDER_DIR;
import static dev.tirador.aoe2radar.cache.HistorialDisco.ACTIVIDAD_CACHE;
import static dev.tirador.aoe2radar.cache.HistorialDisco.ACT_DIAS;
import static dev.tirador.aoe2radar.cache.HistorialDisco.PERFILES_DIR;
import static dev.tirador.aoe2radar.cache.HistorialDisco.cargarActividad;
import static dev.tirador.aoe2radar.cache.HistorialDisco.guardarActividad;
import static dev.tirador.aoe2radar.cache.ImagenesMapa.MAPA_IMG_URL;
import static dev.tirador.aoe2radar.cache.Paises.PAIS_DE;
import static dev.tirador.aoe2radar.cache.Paises.aprenderPais;
import static dev.tirador.aoe2radar.cache.Paises.cargarPaises;
import static dev.tirador.aoe2radar.cache.Paises.guardarPaises;
import static dev.tirador.aoe2radar.cache.Paises.paisDe;
import static dev.tirador.aoe2radar.cache.RecsDisco.RECS_DIR;
import static dev.tirador.aoe2radar.cache.RecsDisco.destino;
import static dev.tirador.aoe2radar.cache.RecsDisco.maxGteEnDisco;
import static dev.tirador.aoe2radar.cache.Vivos.VISTO_VIVO_MS;
import static dev.tirador.aoe2radar.cache.Vivos.VIVO_PARTIDA;
import static dev.tirador.aoe2radar.cache.Vivos.VIVO_RIVAL;
import static dev.tirador.aoe2radar.cache.Vivos.enCursoReal;
import static dev.tirador.aoe2radar.service.Aleatorio.componerTanda;
import static dev.tirador.aoe2radar.service.Aleatorio.cumpleAzar;
import static dev.tirador.aoe2radar.service.Aleatorio.elegirPerfiles;
import static dev.tirador.aoe2radar.service.Aleatorio.esRankedRM;
import static dev.tirador.aoe2radar.service.Aleatorio.filtrarAleatorias;
import static dev.tirador.aoe2radar.service.Aleatorio.filtrarGte;
import static dev.tirador.aoe2radar.service.Aleatorio.franjasAleatorias;
import static dev.tirador.aoe2radar.service.Aleatorio.ordenaYRecorta;
import static dev.tirador.aoe2radar.service.Aleatorio.primeraPaginaRango;
import static dev.tirador.aoe2radar.service.Aleatorio.tramosGte;
import static dev.tirador.aoe2radar.service.Aleatorio.ultimaPaginaRango;
import static dev.tirador.aoe2radar.service.CalculoStats.MIN_PARTIDAS_CIV;
import static dev.tirador.aoe2radar.service.CalculoStats.MUESTRA_FIABLE;
import static dev.tirador.aoe2radar.service.CalculoStats.POCAS_PARTIDAS;
import static dev.tirador.aoe2radar.service.CalculoStats.agregarCivs;
import static dev.tirador.aoe2radar.service.CalculoStats.civPorMapa;
import static dev.tirador.aoe2radar.service.CalculoStats.duracionMedia;
import static dev.tirador.aoe2radar.service.CalculoStats.partidasPorMapa;
import static dev.tirador.aoe2radar.service.CalculoStats.tramoEnRango;
import static dev.tirador.aoe2radar.service.CalculoStats.wilson;
import static dev.tirador.aoe2radar.service.ConsultasLadder.BIN_LADDER;
import static dev.tirador.aoe2radar.service.ConsultasLadder.miembrosClan;
import static dev.tirador.aoe2radar.service.ConsultasLadder.percentilRango;
import static dev.tirador.aoe2radar.service.ConsultasLadder.percentilRating;
import static dev.tirador.aoe2radar.service.ConsultasLadder.sugerirClanes;
import static dev.tirador.aoe2radar.service.Juego.OFICIAL_LOBBIES;
import static dev.tirador.aoe2radar.service.Juego.buscarLobbyConPid;
import static dev.tirador.aoe2radar.service.Juego.carpetaLogsJuego;
import static dev.tirador.aoe2radar.service.Juego.copiarASavegame;
import static dev.tirador.aoe2radar.service.Juego.detectarSavegames;
import static dev.tirador.aoe2radar.service.Juego.rutaCaptureAge;
import static dev.tirador.aoe2radar.service.ReglasPartida.DURACION_TRAMOS;
import static dev.tirador.aoe2radar.service.ReglasPartida.LADDERS_IDX;
import static dev.tirador.aoe2radar.service.ReglasPartida.franjaDe;
import static dev.tirador.aoe2radar.service.ReglasPartida.franjasCentradas;
import static dev.tirador.aoe2radar.service.ReglasPartida.marcarFantasmas;
import static dev.tirador.aoe2radar.service.ReglasPartida.modoDeLadder;
import static dev.tirador.aoe2radar.service.ReglasPartida.modoPrincipal;
import static dev.tirador.aoe2radar.service.ReglasPartida.posicionEnEquipo;
import static dev.tirador.aoe2radar.service.ReglasPartida.rivalCoincide;
import static dev.tirador.aoe2radar.service.ReglasPartida.tramoDuracion;
import static dev.tirador.aoe2radar.sfrdata.CivStats.MODOS_STATS;
import static dev.tirador.aoe2radar.sfrdata.CivStats.VENTANAS_STATS;
import static dev.tirador.aoe2radar.sfrdata.CivStats.VENTANAS_STATS_KEYS;
import static dev.tirador.aoe2radar.sfrdata.CivStats.statsAsegurar;
import static dev.tirador.aoe2radar.sfrdata.CivStats.tendenciasStats;
import static dev.tirador.aoe2radar.sfrdata.Ladder.activosDias;
import static dev.tirador.aoe2radar.sfrdata.Ladder.activosMinPartidas;
import static dev.tirador.aoe2radar.sfrdata.Ladder.clanes;
import static dev.tirador.aoe2radar.sfrdata.Ladder.dispersionActivos;
import static dev.tirador.aoe2radar.sfrdata.Ladder.dispersionTodos;
import static dev.tirador.aoe2radar.sfrdata.Ladder.hist;
import static dev.tirador.aoe2radar.sfrdata.Ladder.ladderAsegurar;
import static dev.tirador.aoe2radar.sfrdata.Ladder.ladderCargando;
import static dev.tirador.aoe2radar.sfrdata.Ladder.ladderGenerado;
import static dev.tirador.aoe2radar.sfrdata.Ladder.ladderHistsActivos;
import static dev.tirador.aoe2radar.sfrdata.Ladder.ladderProgreso;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.ELO_AYER;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.ELO_HACE7;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.NOMBRES_AYER;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.cargarEloAyer;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.muestraAyer;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.perfilesIndex;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.perfilesShard;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.TT_DIR;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttArbol;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttAsegurarDatos;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttClase;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttComprobarActualizacion;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttData;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttDescargar;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttNombre;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttNombreCiv;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttRutaIcono;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttStr;
import static dev.tirador.aoe2radar.techtree.TechTreeDatos.ttTrees;
import static dev.tirador.aoe2radar.util.Config.CONFIG_FILE;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.Formato.fmtTop;
import static dev.tirador.aoe2radar.util.Formato.miles;
import static dev.tirador.aoe2radar.util.Formato.pct1;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Identidad.AUTOR;
import static dev.tirador.aoe2radar.util.Identidad.AUTOR_COMPLETO;
import static dev.tirador.aoe2radar.util.Identidad.CLAN;
import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;
import static dev.tirador.aoe2radar.util.Identidad.RELEASES_API;
import static dev.tirador.aoe2radar.util.Identidad.RELEASES_URL;
import static dev.tirador.aoe2radar.util.Identidad.TWITCH;
import static dev.tirador.aoe2radar.util.Identidad.VERSION;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.firstNonNull;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Json.str;
import static dev.tirador.aoe2radar.util.Json.val;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;
import static dev.tirador.aoe2radar.util.Sistema.fijarAutoArranque;
import static dev.tirador.aoe2radar.util.Sistema.rutaExePropia;
import static dev.tirador.aoe2radar.util.Texto.esCualquiera;
import static dev.tirador.aoe2radar.util.Texto.normalizarNick;
import static dev.tirador.aoe2radar.util.Texto.recorta;
import static dev.tirador.aoe2radar.util.Texto.sinTildes;
import static dev.tirador.aoe2radar.util.Texto.variantesNick;
import static dev.tirador.aoe2radar.util.Texto.versionMayor;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class SpoilerFreeRecs extends JFrame {

    // ----- Configuración -----------------------------------------------------
    static final Path   PLAYERS_FILE = Path.of("players.txt");
    static final String FLATLAF_JAR  = "flatlaf-3.7.2.jar";
    static final String DONAR_URL    = "https://paypal.me/12Tirador/5EUR";
    static final String TEMA_CLARO = "claro", TEMA_OSCURO = "oscuro", TEMA_SISTEMA = "sistema";
    static final int    PER_PAGE     = 50;   // partidas consultadas por jugador
    static final long   PAUSA_MS     = 300;  // cortesía entre llamadas

    // --- Caché de sesión del «Al azar por ELO» (vive mientras la app está abierta) ---
    LbCtx ctxAzar;                       // páginas del leaderboard reutilizadas entre tiradas
    long ctxAzarNacido;
    String firmaRangoAzar = "";
    String firmaTotalAzar = "";
    List<Integer> pagsAzar;
    int cursorPagsAzar;
    final Map<Long, Match> cacheAzar = new HashMap<>();      // partidas ya descargadas del API
    final Map<Long, Long> perfilVistoAzar = new HashMap<>(); // perfil -> última consulta (TTL 10 min)
    volatile boolean azarTramoAgotado;

    static String todosModos() { return t("Todos los modos", "All modes"); }

    // ----- Modelo ------------------------------------------------------------

    static final String GRUPO_GENERAL = "General";

    /** Añade a los catálogos los mapas y civs de las partidas recibidas y
     *  persiste las novedades. */
    static void aprenderCatalogos(Collection<Match> ms) {
        boolean nm = false, nc = false;
        for (Match m : ms) {
            if (esRankedRM(m.mode)
                    && m.map != null && !m.map.isBlank() && MAPAS_CAT.add(m.map.trim())) nm = true;
            for (MatchPlayer p : m.players)
                if (p.civ != null && !p.civ.isBlank() && CIVS_CAT.add(p.civ.trim())) nc = true;
        }
        if (nm) guardarConfig("mapas_ranked", String.join(",", MAPAS_CAT));
        if (nc) guardarConfig("civs_vistas", String.join(",", CIVS_CAT));
    }

    // ----- Presentación de Match (texto/HTML de las columnas; los datos viven en Match) -----
    static String enfrentamiento(Match m) { return enfrentamiento(m, false); }

    static String enfrentamiento(Match m, boolean conResultados) {
        boolean revelar = conResultados && m.finished != null;   // en GTE solo con su ojo: nombres, ELO y ganador
        Map<Integer, List<String>> porEquipo = new TreeMap<>();
        Map<Integer, Boolean> equipoGano = new TreeMap<>();
        int anon = 0;
        for (MatchPlayer p : m.players) {
            String nombre = m.gte > 0 && !conResultados ? t("Jugador ", "Player ") + (++anon) : nombreVisible(p.id, p.name);
            if (m.gte > 0 && conResultados && p.rating != null) nombre += " " + p.rating;   // la solución del GTE
            String extra = p.civ != null && !p.civ.isBlank() ? p.civ : "";
            if (m.azar && m.gte == 0 && p.rating != null)
                extra = extra.isEmpty() ? String.valueOf(p.rating) : extra + ", " + p.rating;
            String pieza = nombre + (extra.isEmpty() ? "" : " (" + extra + ")");
            if (revelar) {
                pieza = escapeHtml(pieza);
                if (SUJETOS.contains(p.id)) pieza = "<b>" + pieza + "</b>";   // la negrita dice la autoría
                if (p.ratingDiff != null) {
                    String col = p.ratingDiff >= 0
                            ? (temaOscuroActivo ? "#6abf69" : "#2e7d32")
                            : (temaOscuroActivo ? "#e57373" : "#c62828");
                    pieza += " <font color='" + col + "'>" + (p.ratingDiff >= 0 ? "+" : "")
                            + p.ratingDiff + "</font>";
                }
            }
            porEquipo.computeIfAbsent(p.team, k -> new ArrayList<>()).add(pieza);
            if (Boolean.TRUE.equals(p.won)) equipoGano.put(p.team, true);
        }
        List<String> lados = new ArrayList<>();
        boolean algunGanador = equipoGano.containsValue(true);
        for (Map.Entry<Integer, List<String>> e : porEquipo.entrySet()) {
            String lado = String.join(", ", e.getValue());
            if (revelar && algunGanador && Boolean.TRUE.equals(equipoGano.get(e.getKey())))
                lado = "\u2726 " + lado;   // corona discreta: el color vive en la columna Jugador
            lados.add(lado);
        }
        String s = String.join("  vs  ", lados);
        return revelar ? "<html>" + s + "</html>" : s;
    }

    /** Nombre del jugador seguido de referencia (columna «Jugador»). */
    static String refNombre(Match m) { return refNombre(m, false); }

    static String refNombre(Match m, boolean revelado) {
        if (m.gte > 0 && !revelado) return "GTE " + m.gte;
        for (MatchPlayer p : m.players) if (p.id == m.refId) return nombreVisible(p.id, p.name);
        return "?";
    }

    /** «2364 \u2192 2378 (+14)»: el rating con el que entró y con el que salió. */
    static String eloAntesDespues(MatchPlayer p) {   // devuelve HTML (sin envoltorio): gris + diff en verde/rojo
        if (p == null || p.rating == null) return "";
        if (p.ratingDiff == null) return " <font color='#8a8a8a'>" + p.rating + "</font>";
        String colD = p.ratingDiff > 0 ? (temaOscuroActivo ? "#6abf69" : "#2e7d32") : p.ratingDiff < 0 ? (temaOscuroActivo ? "#e57373" : "#c62828") : "#8a8a8a";
        return " <font color='" + colD + "'>(" + (p.ratingDiff >= 0 ? "+" : "") + p.ratingDiff + ")</font> <font color='#8a8a8a'>"
                + p.rating + " \u2192 " + (p.rating + p.ratingDiff) + "</font>";
    }

    static String rivalTexto(Match m) { return rivalTexto(m, false); }

    /** Columna Rival: en 1v1 el otro (con su ELO antes → después si la fila está revelada); en equipos «2v2 \u00B7 vs A, B». */
    static String rivalTexto(Match m, boolean revelado) {
        if (m.gte > 0 && !revelado) return "";
        MatchPlayer yo = null;
        for (MatchPlayer p : m.players) if (p.id == m.refId) yo = p;
        if (yo == null) return "";
        if (m.players.size() == 2) {
            for (MatchPlayer p : m.players) if (p.id != m.refId) {
                if (!revelado) return nombreVisible(p.id, p.name);
                String colR = Boolean.TRUE.equals(p.won) ? (temaOscuroActivo ? "#6abf69" : "#2e7d32") : (temaOscuroActivo ? "#e57373" : "#c62828");
                return "<html><font color='" + colR + "'>" + (Boolean.TRUE.equals(p.won) ? "\u2726 " : "") + escapeHtml(nombreVisible(p.id, p.name))
                        + eloAntesDespues(p) + "</font></html>";
            }
            return "";
        }
        Map<Integer, Integer> porEquipo = new TreeMap<>();
        for (MatchPlayer p : m.players) porEquipo.merge(p.team, 1, Integer::sum);
        StringBuilder tam = new StringBuilder();
        for (int n : porEquipo.values()) { if (tam.length() > 0) tam.append('v'); tam.append(n); }
        List<String> riv = new ArrayList<>();
        for (MatchPlayer p : m.players) if (p.team != yo.team) riv.add(nombreVisible(p.id, p.name));
        return tam + " \u00B7 vs " + String.join(", ", riv);
    }

    /** El veredicto del buscado, en su columna: verde con \u2726 si ganó,
     *  rojo si perdió; a pelo en vivas, colgadas y GTE. */
    static String refConVeredicto(Match m) {
        String nombre = refNombre(m, true);
        if (m.finished == null) return nombre;
        Boolean gano = null;
        for (MatchPlayer p : m.players) if (p.id == m.refId) { gano = p.won; break; }
        if (gano == null) return nombre;
        String col = gano ? (temaOscuroActivo ? "#6abf69" : "#2e7d32")
                          : (temaOscuroActivo ? "#e57373" : "#c62828");
        MatchPlayer yo = null;
        for (MatchPlayer p : m.players) if (p.id == m.refId) yo = p;
        return "<html><font color='" + col + "'>" + (gano ? "\u2726 " : "")
                + escapeHtml(nombre)
                + eloAntesDespues(yo)
                + "</font></html>";
    }

    /** Celdas ordenables que se pintan como texto. En curso real = «EN DIRECTO»
     *  arriba del todo; fantasmas de crash = «—» en su sitio cronológico. */
    record FechaCell(Instant t, Instant orden) implements Comparable<FechaCell> {
        static final DateTimeFormatter F =
                DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
        FechaCell(Instant t) { this(t, null); }
        Instant clave() { return t != null ? t : (orden != null ? orden : Instant.MAX); }
        @Override public int compareTo(FechaCell o) { return clave().compareTo(o.clave()); }
        @Override public String toString() {
            if (t != null) return F.format(t);
            return orden != null ? "\u2014" : dev.tirador.aoe2radar.util.I18n.t("EN DIRECTO", "LIVE");
        }
    }

    static String colorVivoHex() {
        return temaOscuroActivo ? "ff6b5e" : "d32f2f";
    }

    /** Dorado legible para la fila EN DIRECTO de la tabla, según el tema. */
    static Color colorVivoTabla() {
        return temaOscuroActivo ? new Color(0xFF, 0xC9, 0x4D) : new Color(0xB0, 0x78, 0x00);
    }

    // ----- Estado UI ---------------------------------------------------------
    final List<Player> todosJugadores = new ArrayList<>();          // fuente de verdad (todos los grupos)
    final Set<Long> watchBarridos = new HashSet<>();                // seguidos ya consultados en este arranque
    final JComboBox<String> grupoCombo = new JComboBox<>();
    final DefaultListModel<Player> playersModel = new DefaultListModel<>();
    final JList<Player> playersList = new JList<>(playersModel) {
        @Override public String getToolTipText(MouseEvent e) {   // sobre la celda Forma: su tooltip; en el resto, el de la fila
            if (formaVisible && enZonaForma(e.getPoint())) {
                int i = locationToIndex(e.getPoint());
                if (i >= 0 && getCellBounds(i, i).contains(e.getPoint())) return tipForma(playersModel.get(i).id());
            }
            return super.getToolTipText(e);
        }
    };

    boolean enZonaForma(Point p) {
        int wL = playersList.getWidth() - 22, elo = anchoCeldaElo(wL);
        return formaVisible && p.x >= wL - elo - 72 && p.x <= wL - elo;
    }
    final Map<Long, Integer> eloWatch  = new HashMap<>();   // ELO actual por seguido (escrito en el EDT)
    final Map<Long, Long>    vivoWatch = new HashMap<>();   // matchId en curso por seguido
    boolean mostrarEloWatch = Boolean.parseBoolean(leerConfig("elo_watchlist", "true"));
    javax.swing.Timer vigilante;      // barrido periódico del «en directo» (nunca del ELO)
    boolean vigilando = false;
    JPanel watchPanel;
    final JSpinner hoursSpinner = new JSpinner(new SpinnerNumberModel(
            Math.min(24, Integer.parseInt(leerConfig("ventana_n", leerConfig("horas", "24")))), 1, 24, 1));
    final JComboBox<String> unidadCombo = new JComboBox<>(new String[]{ t("horas", "hours"), t("días", "days"), t("semanas", "weeks") });

    /** «36 h», «3 días», «2 semanas»: la ventana en la unidad que se lee mejor. */
    static String textoVentana(int horas) {
        if (horas % (24 * 7) == 0 && horas >= 24 * 7) { int w = horas / (24 * 7); return w + (w == 1 ? t(" semana", " week") : t(" semanas", " weeks")); }
        if (horas % 24 == 0 && horas >= 48) return (horas / 24) + t(" días", " days");
        return horas + " h";
    }

    /** La ventana de búsqueda en horas: número × unidad (recordados entre sesiones). */
    int horasVentana() {
        int n = (int) hoursSpinner.getValue();
        int u = unidadCombo.getSelectedIndex();
        return Math.min(24 * 7, n * (u == 1 ? 24 : u == 2 ? 24 * 7 : 1));   // techo: una semana; el histórico completo vive en el perfil («Todas las partidas del perfil»)
    }
    final JComboBox<String> modeCombo = new JComboBox<>(new String[]{ todosModos() });
    final JComboBox<String> mapaCombo = new JComboBox<>(new String[]{ t("Todos los mapas", "All maps") });   // filtro de mapa sobre las partidas cargadas
    final JComboBox<String> periodoCombo = new JComboBox<>(new String[]{ t("Todo", "All"), t("7 días", "7 days"), t("30 días", "30 days"), t("90 días", "90 days"), t("365 días", "365 days") });   // filtro de periodo
    boolean actualizandoMapas;
    final MatchesTableModel tableModel = new MatchesTableModel();
    final JTable table;
    final JLabel status = new JLabel(t("Listo.", "Ready.")) {
        @Override public void setText(String texto) {   // si no cabe, el tooltip lo enseña entero
            super.setText(texto);
            setToolTipText(texto == null || texto.isBlank() ? null : texto);
        }
    };
    final JButton dlSel = new JButton(t("Descargar seleccionadas", "Download selected"));
    final JButton dlAll = new JButton(t("Descargar todas", "Download all"));
    final JButton fetchBtn = new JButton(t("Buscar partidas", "Search games"));
    final JButton azarBtn  = new JButton(t("Al azar por ELO…", "Random by ELO…"));
    final JButton gteBtn   = new JButton("Guess the ELO!");
    final JButton cafeBtn  = new JButton("\u2615 " + t("Invítame a un café", "Buy me a coffee"));
    final JProgressBar progreso = new JProgressBar();
    final List<Match> all  = new ArrayList<>();   // todo lo consultado en la ventana
    final List<Match> view = new ArrayList<>();   // lo que pasa los filtros (lo que ve la tabla)
    final Image logo = cargarLogo();
    final JCheckBoxMenuItem autoSgItem = new JCheckBoxMenuItem(t("Enviar al juego al descargar", "Send to game after download"),
            Boolean.parseBoolean(leerConfig("autosavegame", "false")));
    JPopupMenu configMenu;
    TitledBorder tituloWatch;
    JToggleButton soloVivosBtn;
    JLabel resumenWatch;   // «50 jugadores · 6 en directo»
    JButton delBtn;
    JLabel cabLabel;
    volatile SwingWorker<?, ?> fetchWorker;
    Player invitado;
    String vistaDelInvitado = "";

    String vistaActualId() {
        Object g = grupoCombo == null ? null : grupoCombo.getSelectedItem();
        String pais = modoPais() ? paisSel() : "";
        return g + "|" + pais;
    }
    JWindow hoverCard;
    boolean cardFijada;
    javax.swing.Timer hoverTimer;
    long hoverPid;
    Point hoverPantalla;
    final Map<Long, Object[]> perfilCardCache = new HashMap<>();   // pid -> { tsMs, htmlDatos, int[] spark }
    static final String TOP_LADDER = "\u2605 Top ladder";
    static final String TOP_PAIS = t("\u2605 Top pa\u00eds", "\u2605 Country top");
    record PaisItem(String nombre, String code) {
        @Override public String toString() { return nombre; }
    }
    /** TODOS los países ISO con su nombre en el idioma de la app — Bulgaria
     *  incluida y sin listas que mantener a mano. */
    static PaisItem[] catalogoPaises() {
        java.util.Locale loc = java.util.Locale.forLanguageTag(IDIOMA);
        List<PaisItem> out = new ArrayList<>();
        for (String code : java.util.Locale.getISOCountries()) {
            String nombre = java.util.Locale.of("", code).getDisplayCountry(loc);
            if (nombre == null || nombre.isBlank() || nombre.equals(code)) nombre = code;
            out.add(new PaisItem(nombre, code.toLowerCase()));
        }
        java.text.Collator col = java.text.Collator.getInstance(loc);
        out.sort((a, b) -> col.compare(a.nombre(), b.nombre()));
        return out.toArray(PaisItem[]::new);
    }
    static final PaisItem[] PAISES = catalogoPaises();
    PaisItem paisActual;
    JComboBox<PaisItem> paisCombo;
    javax.swing.JTextField buscaPais;
    JPanel parPais, sujetosPanel;
    List<Player> ultimosSujetos = List.of();
    final Set<Long> filtroSujetos = new HashSet<>();   // chips activos de la cabecera; vacío = todas las partidas de los buscados
    String vistaDeSujetos = "";

    /** Cabecera fija sobre la watchlist: los sujetos de la búsqueda, fuera
     *  del scroll e imposibles de perder de vista. Solo con ≤4 sujetos. */
    void refrescarSujetos(List<Player> tracked, boolean esInvitadoIn) {
        ultimosSujetos = tracked == null ? List.of() : List.copyOf(tracked);
        if (todasPerfilBtn != null) todasPerfilBtn.setVisible(ultimosSujetos.size() == 1 && ultimosSujetos.get(0).id() > 0);   // «Todas las partidas del perfil» solo cuando la búsqueda es de un jugador
        final boolean esInvitado = esInvitadoIn
                || (invitado != null && ultimosSujetos.size() == 1 && ultimosSujetos.get(0).id() == invitado.id());
        sujetosPanel.removeAll();
        filtroSujetos.retainAll(ultimosSujetos.stream().map(Player::id).collect(java.util.stream.Collectors.toSet()));
        if (ultimosSujetos.isEmpty()) {
            sujetosPanel.setVisible(false);
            sujetosPanel.revalidate(); sujetosPanel.repaint();
            return;
        }
        final boolean muchos = ultimosSujetos.size() > 10;
        Color sep = temaOscuroActivo ? new Color(0x5a, 0x5a, 0x5a) : new Color(0xc0, 0xc0, 0xc0);
        Color gris = temaOscuroActivo ? new Color(0x9a, 0x9a, 0x9a) : new Color(0x66, 0x66, 0x66);
        log("cabecera sujetos: " + ultimosSujetos.size() + " · invitado=" + esInvitado);
        JLabel tit = new JLabel(t("Partidas de:", "Games of:"));
        tit.setFont(tit.getFont().deriveFont(Font.PLAIN, 11f));
        tit.setForeground(gris);
        tit.setBorder(BorderFactory.createEmptyBorder(3, 6, 1, 0));
        JButton cerrarBusq = new JButton("\u00D7");
        cerrarBusq.setFocusable(false);
        cerrarBusq.setMargin(new Insets(0, 5, 0, 5));
        cerrarBusq.putClientProperty("JButton.buttonType", "roundRect");
        cerrarBusq.setToolTipText(t("Cerrar esta búsqueda: vacía la tabla", "Close this search: clears the table"));
        cerrarBusq.addActionListener(e -> cerrarBusqueda());
        JPanel filaTit = new JPanel(new BorderLayout(6, 0));
        filaTit.setOpaque(false);
        filaTit.add(tit, BorderLayout.WEST);
        filaTit.add(cerrarBusq, BorderLayout.EAST);
        filaTit.setAlignmentX(Component.LEFT_ALIGNMENT);
        sujetosPanel.add(filaTit);
        JPanel chipsSujetos = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        chipsSujetos.setOpaque(false);
        chipsSujetos.setAlignmentX(Component.LEFT_ALIGNMENT);
        chipsSujetos.addMouseListener(new MouseAdapter() {   // clic en el hueco entre chips = todas las partidas
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && !filtroSujetos.isEmpty()) { filtroSujetos.clear(); refrescarSujetos(ultimosSujetos, esInvitadoIn); applyFilters(); }
            }
        });
        if (muchos) {   // más de 10: «todo el grupo (N)» y un «Filtrar ▾» con casillas
            JLabel todos = new JLabel("<html><i>" + escapeHtml(vistaActualId().split("\\|")[0]) + "</i> <font color='#8a8a8a'>(" + ultimosSujetos.size() + ")</font></html>");
            todos.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            JButton filtrarBtn = new JButton(t("Filtrar \u25BE", "Filter \u25BE"));
            filtrarBtn.setFocusable(false); filtrarBtn.setMargin(new Insets(1, 6, 1, 6));
            filtrarBtn.putClientProperty("JButton.buttonType", "roundRect");
            filtrarBtn.addActionListener(e -> {
                JPopupMenu pm = new JPopupMenu();
                JMenuItem todosIt = new JMenuItem(t("Todas las partidas", "All games"));
                todosIt.addActionListener(a -> { filtroSujetos.clear(); refrescarSujetos(ultimosSujetos, esInvitadoIn); applyFilters(); });
                pm.add(todosIt); pm.addSeparator();
                for (Player s : ultimosSujetos) {
                    JCheckBoxMenuItem it = new JCheckBoxMenuItem(nombreVisible(s.id(), s.name()), filtroSujetos.contains(s.id()));
                    it.addActionListener(a -> { if (it.isSelected()) filtroSujetos.add(s.id()); else filtroSujetos.remove(s.id()); refrescarSujetos(ultimosSujetos, esInvitadoIn); applyFilters(); });
                    pm.add(it);
                }
                pm.show(filtrarBtn, 0, filtrarBtn.getHeight());
            });
            chipsSujetos.add(todos); chipsSujetos.add(filtrarBtn);
            if (!filtroSujetos.isEmpty()) {
                JLabel act = new JLabel("<font color='#8a8a8a'>" + filtroSujetos.size() + t(" filtrados", " filtered") + "</font>");
                act.setText("<html>" + act.getText() + "</html>");
                chipsSujetos.add(act);
            }
        }
        for (Player s : muchos ? List.<Player>of() : ultimosSujetos) {
            Integer elo = eloWatch.get(s.id());
            JLabel l = new JLabel("<html><i>" + escapeHtml(nombreVisible(s.id(), s.name())) + "</i>"
                    + (elo != null ? " <font color='#8a8a8a'>\u00B7 " + elo + "</font>" : "") + "</html>");
            final boolean activo = filtroSujetos.contains(s.id());
            l.setOpaque(activo);
            if (activo) l.setBackground(temaOscuroActivo ? new Color(0x4a, 0x3a, 0x1e) : new Color(0xf3, 0xe3, 0xc0));   // chip seleccionado
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(activo ? (temaOscuroActivo ? new Color(0xd9, 0xa5, 0x5b) : new Color(0x9a, 0x6b, 0x1f)) : new Color(0, 0, 0, 0), 1, true),
                    BorderFactory.createEmptyBorder(1, 6, 1, 6)));
            l.setToolTipText(t("Clic: ver solo sus partidas (Ctrl+clic: varios; otro clic: todas) — clic derecho: perfil, nicks, grupos\u2026",
                    "Click: only their games (Ctrl+click: several; click again: all) — right-click: profile, names, groups\u2026"));
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final long pid = s.id(); final String nom = s.name();
            l.addMouseListener(new MouseAdapter() {
                void popup(MouseEvent e) {
                    JPopupMenu pm = new JPopupMenu();
                    JMenu jm = menuDeJugador(pid, nom);
                    for (Component c : jm.getMenuComponents()) pm.add(c);
                    if (esInvitado) {
                        pm.addSeparator();
                        JMenuItem quitar = new JMenuItem(t("Quitar filtro (dejar de ver sus partidas)",
                                "Remove filter (stop viewing their games)"));
                        quitar.addActionListener(a -> {
                            invitado = null;
                            refrescarSujetos(List.of(), false);
                            aplicarFiltroGrupo();
                        });
                        pm.add(quitar);
                    }
                    pm.show(l, e.getX(), e.getY());
                }
                @Override public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) popup(e); }
                @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) popup(e); }
                @Override public void mouseClicked(MouseEvent e)  {
                    if (e.getButton() != MouseEvent.BUTTON1) return;
                    if (e.getClickCount() == 2) { abrirPerfil(pid, nom); return; }
                    if (e.getClickCount() != 1) return;
                    if (e.isControlDown()) { if (!filtroSujetos.remove(pid)) filtroSujetos.add(pid); }   // varios
                    else if (filtroSujetos.size() == 1 && filtroSujetos.contains(pid)) filtroSujetos.clear();   // otro clic = todas
                    else { filtroSujetos.clear(); filtroSujetos.add(pid); }
                    refrescarSujetos(ultimosSujetos, esInvitadoIn);
                    applyFilters();
                }
            });
            String grupoYa = ultimosSujetos.size() == 1 ? grupoDeJugador(pid) : null;
            if (ultimosSujetos.size() == 1 && grupoYa != null && !esInvitado) {   // ya fichado: se dice, no se esconde
                JLabel ya = new JLabel("\u2605 " + t("en ", "in ") + (grupoYa.isBlank() ? t("tu watchlist", "your watchlist") : grupoYa));
                ya.setFont(ya.getFont().deriveFont(Font.PLAIN, 11f));
                ya.setForeground(gris);
                JPanel filaYa = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                filaYa.setOpaque(false);
                filaYa.add(l);
                filaYa.add(ya);
                chipsSujetos.add(filaYa);
            } else if (ultimosSujetos.size() == 1 && grupoYa == null) {   // único y sin fichar: invitado o alguien del top
                JButton addInv = new JButton(t("+ Añadir al grupo", "+ Add to group"));
                addInv.setFont(addInv.getFont().deriveFont(11f));
                addInv.setMargin(new Insets(1, 6, 1, 6));
                addInv.setFocusable(false);
                addInv.setToolTipText(t("Ficha a este jugador en uno de tus grupos", "Add this player to one of your groups"));
                addInv.addActionListener(e -> {
                    JPopupMenu pm = new JPopupMenu();
                    JMenu jm = menuDeJugador(pid, nom);
                    for (Component c : jm.getMenuComponents())
                        if (c instanceof JMenu sub && sub.getText() != null && sub.getText().toLowerCase().startsWith(t("añadir", "add")))
                            for (Component cc : sub.getMenuComponents()) pm.add(cc);
                    if (pm.getComponentCount() == 0) for (Component c : jm.getMenuComponents()) pm.add(c);
                    pm.show(addInv, 0, addInv.getHeight());
                });
                JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                fila.setOpaque(false);
                fila.add(l);
                fila.add(addInv);
                chipsSujetos.add(fila);
            } else chipsSujetos.add(l);
        }
        sujetosPanel.add(chipsSujetos);
        JPanel raya = new JPanel();
        raya.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
        raya.setPreferredSize(new Dimension(10, 5));
        raya.setOpaque(false);
        raya.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, sep));
        sujetosPanel.add(raya);
        sujetosPanel.setVisible(true);
        sujetosPanel.revalidate(); sujetosPanel.repaint();
    }
    volatile boolean rearmandoPais;
    List<PaisItem> catalogoOrdenado = List.of();

    /** El campo filtra el combo de al lado; nadie más escribe en él, así que
     *  no puede realimentarse (la lección del cuelgue de la 4.41). */
    void instalarFiltroPais() {
        buscaPais.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void filtrar() {
                String q = sinTildes(buscaPais.getText().trim());
                PaisItem prev = paisActual;
                rearmandoPais = true;
                try {
                    paisCombo.removeAllItems();
                    for (PaisItem pi : catalogoOrdenado)
                        if (q.isEmpty() || sinTildes(pi.nombre()).contains(q) || pi.code().startsWith(q))
                            paisCombo.addItem(pi);
                    if (prev != null)
                        for (int i = 0; i < paisCombo.getItemCount(); i++)
                            if (paisCombo.getItemAt(i).code().equals(prev.code())) { paisCombo.setSelectedIndex(i); break; }
                } finally { rearmandoPais = false; }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { filtrar(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { filtrar(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        buscaPais.addActionListener(e -> {   // Enter: el primero filtrado (y carga)
            if (paisCombo.getItemCount() > 0) paisCombo.setSelectedIndex(0);
        });
    }

    void fijarPais(PaisItem pi) {
        paisActual = pi;
        guardarConfig("top_pais", pi.code());
        if (modoPais()) cargarTopLadder(true);
    }
    String topFirma = "";
    final Map<Long, Integer> rankTop = new HashMap<>();
    final Map<Long, String[]> twitchLive = new HashMap<>();   // pid -> { canal, título, viewers }
    final List<String[]> directosAoE2 = new ArrayList<>();    // { login, display, título, idioma, viewers }
    JToggleButton directosBtn, resultadosBtn;
    volatile boolean topeAlcanzado;   // la búsqueda tocó el tope de páginas/partidas: se avisa en el status
    JTextField rivalField;            // filtro por rival (sobre las partidas cargadas, sin llamadas)
    Player objetivoForzado;           // jugador concreto pedido con «Ver sus partidas» (una sola búsqueda)
    JPanel parModo, parRival;         // solo visibles cuando hay partidas que filtrar

    /** «Mostrar resultados», Modo y Rival solo cuando la tabla tiene partidas a la vista. */
    JPanel filaNota, filaBotonesInferiores;   // solo tienen sentido con la tabla de partidas a la vista

    /** Una pestaña de la barra de vistas: estilo «tab» de FlatLaf (subrayado en la activa), icono y sin foco. */
    static JToggleButton pestana(String texto, Icon icono) {
        JToggleButton b = new JToggleButton(texto, icono);
        b.setFocusable(false);
        b.setIconTextGap(6);
        b.putClientProperty("JButton.buttonType", "tab");
        b.setMargin(new Insets(4, 10, 4, 10));
        return b;
    }

    /** «Partidas» está marcada cuando ninguna otra vista lo está. */
    void sincronizarPestanas(boolean tablaVisible) {
        if (recsBtn == null) return;
        boolean otra = (directosBtn != null && directosBtn.isSelected()) || (techTreeBtn != null && techTreeBtn.isSelected())
                || (ladderBtn != null && ladderBtn.isSelected()) || (civStatsBtn != null && civStatsBtn.isSelected()) || (perfilBtn != null && perfilBtn.isSelected()) || (ahoraBtn != null && ahoraBtn.isSelected());
        if (recsBtn.isSelected() == otra) recsBtn.setSelected(!otra);
    }

    /** Iconos vectoriales de las pestañas (16 px, en el color del texto): lista, punto en directo, persona, campana, barras. */
    static Icon iconoVista(String tipo) {
        return new Icon() {
            @Override public int getIconWidth() { return 16; }
            @Override public int getIconHeight() { return 16; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color col = c.getForeground() != null ? c.getForeground() : Color.GRAY;
                g2.setColor(col);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (tipo) {
                    case "partidas" -> { for (int i = 0; i < 3; i++) g2.drawLine(x + 2, y + 3 + i * 5, x + 14, y + 3 + i * 5); }
                    case "directos" -> { g2.fillOval(x + 5, y + 5, 6, 6); g2.drawOval(x + 1, y + 1, 14, 14); }
                    case "ahora" -> { g2.drawOval(x + 1, y + 1, 14, 14); g2.drawLine(x + 8, y + 4, x + 8, y + 8); g2.drawLine(x + 8, y + 8, x + 11, y + 10); }
                    case "campana" -> { g2.drawArc(x + 3, y + 2, 10, 12, 0, 180); g2.drawLine(x + 3, y + 8, x + 3, y + 12); g2.drawLine(x + 13, y + 8, x + 13, y + 12); g2.drawLine(x + 1, y + 12, x + 15, y + 12); g2.fillOval(x + 6, y + 13, 4, 3); }
                    case "perfil" -> { g2.drawOval(x + 5, y + 1, 6, 6); g2.drawArc(x + 2, y + 8, 12, 12, 0, 180); }
                    case "ratings" -> { java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double(); p.moveTo(x + 1, y + 14); p.curveTo(x + 6, y + 14, x + 6, y + 2, x + 8, y + 2); p.curveTo(x + 10, y + 2, x + 10, y + 14, x + 15, y + 14); g2.draw(p); }
                    case "civstats" -> { g2.fillRect(x + 2, y + 9, 3, 6); g2.fillRect(x + 7, y + 4, 3, 11); g2.fillRect(x + 12, y + 7, 3, 8); }
                    default -> g2.drawRect(x + 2, y + 2, 12, 12);
                }
                g2.dispose();
            }
        };
    }
    JToggleButton recsBtn;                    // pestaña «Partidas»
    void actualizarControlesTabla() {
        boolean tablaVisible = recsCards != null && recsCards.isShowing() && !(directosBtn != null && directosBtn.isSelected()) && !(techTreeBtn != null && techTreeBtn.isSelected()) && !(ladderBtn != null && ladderBtn.isSelected()) && !(civStatsBtn != null && civStatsBtn.isSelected()) && !actividadAbierta && !ahoraAbierta;
        boolean hay = tablaVisible && !all.isEmpty();
        if (filaNota != null) filaNota.setVisible(tablaVisible);
        if (filaBotonesInferiores != null) filaBotonesInferiores.setVisible(tablaVisible);
        status.setVisible(tablaVisible || (directosBtn != null && directosBtn.isSelected()));   // los mensajes de estado, solo donde se usan
        sincronizarPestanas(tablaVisible);
        if (resultadosBtn != null) resultadosBtn.setVisible(hay);
        if (parModo != null) parModo.setVisible(hay);
        if (parRival != null) parRival.setVisible(hay);
    }
    JPopupMenu rivalPopup;
    String filtroRival = "";

    /** Sugerencias: rivales de la búsqueda actual que contienen lo tecleado, con su número de partidas. */
    void sugerirRivales() {
        rivalPopup.setVisible(false);
        rivalPopup.removeAll();
        if (filtroRival.length() < 1 || all.isEmpty()) return;
        Map<String, Integer> cuenta = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Match m : all) {
            if (m.gte > 0) continue;
            asignarRef(m);
            MatchPlayer yo = null;
            for (MatchPlayer p : m.players) if (p.id == m.refId) yo = p;
            for (MatchPlayer p : m.players) {
                if (p.id == m.refId) continue;
                if (yo != null && m.players.size() > 2 && p.team == yo.team) continue;
                String vis = nombreVisible(p.id, p.name);
                if (normalizarNick(vis).contains(filtroRival) || normalizarNick(p.name).contains(filtroRival)) cuenta.merge(vis, 1, Integer::sum);
            }
        }
        if (cuenta.isEmpty()) return;
        List<Map.Entry<String, Integer>> lista = new ArrayList<>(cuenta.entrySet());
        lista.sort((a, b) -> b.getValue() - a.getValue());
        int n = 0;
        for (Map.Entry<String, Integer> en : lista) {
            if (n++ >= 8) break;
            if (normalizarNick(en.getKey()).equals(filtroRival) && lista.size() == 1) return;   // ya es exacto: sin popup
            JMenuItem it = new JMenuItem(en.getKey() + "  (" + en.getValue() + ")");
            it.addActionListener(a -> { rivalField.setText(en.getKey()); rivalPopup.setVisible(false); });
            rivalPopup.add(it);
        }
        rivalPopup.show(rivalField, 0, rivalField.getHeight());
    }
    boolean mostrarResultados;   // modo consulta: SIEMPRE renace apagado

    // ----- Forma reciente (±ELO en una ventana de horas; 1v1 ranked) --------------

    static String formaLarga(Forma f) {
        if (f.partidas() == 0) return t("sin partidas 1v1 en la ventana", "no 1v1 games in the window");
        String r = f.racha() >= 2 ? " \u00B7 " + t("racha ", "streak ") + f.racha() + (f.rachaGana() ? "V" : "D") : "";
        return f.w() + "-" + f.l() + " \u00B7 " + (f.diff() >= 0 ? "+" : "") + f.diff() + r;
    }
    static final Map<Long, Integer> TOP_STREAK = new java.util.concurrent.ConcurrentHashMap<>();   // racha del ladder (+3 / -2)
    static final Map<Long, int[]> TOP_LAST10 = new java.util.concurrent.ConcurrentHashMap<>();    // {ganadas, perdidas} de las últimas 10
    final Map<Long, Forma> forma24 = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<Long, Forma> forma7d = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<Long, Long> formaTs24 = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<Long, Long> formaTs7d = new java.util.concurrent.ConcurrentHashMap<>();
    volatile boolean formaVisible;   // el chip: nace apagado, no se recuerda
    static String tipVerForma() { return t("Consulta el ±ELO reciente (según el selector) y lo muestra como columna ordenable junto al ELO. Con jugadores seleccionados consulta solo esos; sin selección, todos. No se recuerda entre sesiones.",
            "Fetches the recent ±ELO (per the selector) and shows it as a sortable column next to the ELO. With players selected it checks only those; with none, everyone. Not remembered between sessions."); }
    static String tipOcultarForma() { return t("Oculta la columna Forma (los datos siguen en caché 10 min).", "Hides the Recent form column (data stays cached for 10 min)."); }
    volatile int ventanaForma = 24;  // 24 h o 7 d (selector junto al chip)
    Map<Long, Forma> formaActiva() { return ventanaForma <= 24 ? forma24 : forma7d; }

    /** Cambiar de vista apaga la columna Forma (vuelve solo si la pides). */
    void apagarForma() {
        if (!formaVisible) return;
        formaVisible = false;
        if (ocultarFormaBtn != null) ocultarFormaBtn.setVisible(false);
        if (formaBtn != null) actualizarTextoForma();
        if (leerConfig("orden_watch", "elo").startsWith("forma")) guardarConfig("orden_watch", "elo");
        refrescarCabeceraOrden();
    }
    JButton formaBtn, ocultarFormaBtn;

    /** Una llamada por jugador: sus últimas partidas, filtradas a 1v1 ranked dentro de la ventana. */
    Forma calcForma(long pid, int horas) throws Exception {
        Iterable<Match> leidas = COMPANION.partidas(pid, 1, 40);
        Instant desde = Instant.now().minus(Duration.ofHours(horas));
        int diff = 0, w = 0, l = 0, partidas = 0, racha = 0; Boolean rachaGana = null; boolean rachaViva = true;
        for (Match m : leidas) {
            if (m == null || m.finished == null || m.finished.isBefore(desde) || m.players.size() != 2
                    || m.mode == null || !m.mode.startsWith("1v1 Random")) continue;
            for (MatchPlayer mp : m.players) {
                if (mp.id != pid) continue;
                partidas++;
                if (mp.ratingDiff != null) diff += mp.ratingDiff;
                boolean gano = Boolean.TRUE.equals(mp.won);
                if (gano) w++; else l++;
                if (rachaViva) {   // las partidas vienen de la más reciente a la más antigua
                    if (rachaGana == null) { rachaGana = gano; racha = 1; }
                    else if (rachaGana == gano) racha++;
                    else rachaViva = false;
                }
            }
        }
        return new Forma(diff, w, l, racha, Boolean.TRUE.equals(rachaGana), partidas);
    }

    /** Una llamada a /profiles: la serie de rating 1v1 con fechas → 24 h y 7 d a la vez.
     *  Devuelve {forma24, forma7d}; null si el perfil no trae serie (entonces se usa calcForma). */
    Forma[] calcFormaSerie(long pid) throws Exception {
        Perfil pf = COMPANION.perfil(pid);
        List<Object[]> serie = new ArrayList<>();   // {epochMs, rating, ratingDiff|null}
        for (Perfil.Serie lb : pf.series()) {
            String lbId = String.valueOf(firstNonNull(lb.id(), ""));
            if (!lbId.equals("rm_1v1") && !lbId.equals("3")) continue;
            for (Perfil.Punto pt : lb.puntos()) {
                Instant d = pt.fecha();
                if (d == null || pt.rating() == null) continue;
                serie.add(new Object[]{ d.toEpochMilli(), pt.rating(), pt.diff() });
            }
        }
        if (serie.isEmpty()) return null;
        serie.sort((a, b) -> Long.compare((Long) b[0], (Long) a[0]));   // de la más reciente a la más antigua
        Forma[] out = new Forma[2];
        int[] horasV = { 24, 24 * 7 };
        for (int k = 0; k < 2; k++) {
            long desde = System.currentTimeMillis() - horasV[k] * 3_600_000L;
            int w = 0, l = 0, partidas = 0, racha = 0; Boolean rachaGana = null; boolean rachaViva = true;
            Integer ratingAhora = (Integer) serie.get(0)[1], ratingAntes = null;
            for (Object[] pt : serie) {
                if ((Long) pt[0] < desde) { ratingAntes = (Integer) pt[1]; break; }
                partidas++;
                Integer df = (Integer) pt[2];
                if (df != null) {
                    boolean gano = df > 0;
                    if (gano) w++; else l++;
                    if (rachaViva) {
                        if (rachaGana == null) { rachaGana = gano; racha = 1; }
                        else if (rachaGana == gano) racha++;
                        else rachaViva = false;
                    }
                }
            }
            int diff = partidas == 0 ? 0 : ratingAhora - (ratingAntes != null ? ratingAntes : (Integer) serie.get(serie.size() - 1)[1]);
            if (partidas == serie.size() && ratingAntes == null) {   // toda la serie cae en la ventana: suma de diffs
                int s = 0; for (Object[] pt : serie) if (pt[2] != null) s += (Integer) pt[2];
                diff = s;
            }
            out[k] = new Forma(diff, w, l, racha, Boolean.TRUE.equals(rachaGana), partidas);
        }
        return out;
    }

    /** Forma de hasta cinco jugadores con UNA llamada (/matches?profile_ids=…): partidas 1v1 RM con resultado y ±ELO; 24 h y 7 días. */
    Map<Long, Forma[]> calcFormaLote(List<Player> lote) throws Exception {
        StringBuilder csv = new StringBuilder(); for (Player p : lote) { if (csv.length() > 0) csv.append(','); csv.append(p.id()); }
        Iterable<Match> leidas = COMPANION.partidas(csv.toString(), 1, 50);
        Map<Long, List<Object[]>> series = new HashMap<>();   // pid → {epochMs, ratingDespués, diff} de más nueva a más vieja
        for (Match m : leidas) {
            if (m == null || m.finished == null || m.started == null || m.mode == null || !m.mode.contains("1v1")) continue;
            for (Player p : lote) for (MatchPlayer mp : m.players) if (mp.id == p.id() && mp.rating != null && mp.ratingDiff != null)
                series.computeIfAbsent(p.id(), k -> new ArrayList<>()).add(new Object[]{ m.finished.toEpochMilli(), mp.rating + mp.ratingDiff, mp.ratingDiff });
        }
        Map<Long, Forma[]> out = new HashMap<>();
        for (Player p : lote) {
            List<Object[]> serie = series.getOrDefault(p.id(), List.of());
            Forma[] f = new Forma[2];
            int[] horasV = { 24, 24 * 7 };
            for (int k = 0; k < 2; k++) {
                long desde = System.currentTimeMillis() - horasV[k] * 3_600_000L;
                int w = 0, l = 0, partidas = 0, racha = 0, sumaDiff = 0; Boolean rachaGana = null; boolean rachaViva = true;
                for (Object[] pt : serie) {
                    if ((Long) pt[0] < desde) break;
                    partidas++;
                    int df = (Integer) pt[2]; sumaDiff += df;
                    boolean gano = df > 0;
                    if (gano) w++; else l++;
                    if (rachaViva) { if (rachaGana == null) { rachaGana = gano; racha = 1; } else if (rachaGana == gano) racha++; else rachaViva = false; }
                }
                f[k] = new Forma(sumaDiff, w, l, racha, Boolean.TRUE.equals(rachaGana), partidas);
            }
            out.put(p.id(), f);
        }
        return out;
    }

    /** Consulta la forma de los jugadores dados (con caché de 10 min), con progreso y Detener. */
    void cargarForma(List<Player> objetivo, int horas, Runnable alTerminar) {
        Map<Long, Forma> cache = horas <= 24 ? forma24 : forma7d;
        Map<Long, Long> ts = horas <= 24 ? formaTs24 : formaTs7d;
        List<Player> pendientes = new ArrayList<>();
        long ahora = System.currentTimeMillis();
        for (Player p : objetivo) if (ahora - ts.getOrDefault(p.id(), 0L) > 600_000) pendientes.add(p);
        if (pendientes.isEmpty()) { if (alTerminar != null) alTerminar.run(); return; }
        if (modoTop() && pendientes.size() > 20) {
            int seg = (int) Math.ceil(pendientes.size() * 0.6);
            int ok = JOptionPane.showConfirmDialog(this,
                    (horas <= 24 ? t("Consultar la forma de las últimas 24 h de ", "Fetching the last 24 h form of ")
                                 : t("Consultar la forma de los últimos 7 días de ", "Fetching the last 7 days form of "))
                            + pendientes.size() + t(" jugadores tarda ~", " players takes ~") + seg + " s.\n"
                            + t("Se consulta jugador a jugador (con pausas) y queda guardado 10 minutos.", "It goes player by player (with pauses) and is cached for 10 minutes."),
                    t("Forma reciente", "Recent form"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
        }
        trabajando(true);
        final long miSerial = opSerial;
        new SwingWorker<Void, String>() {
            @Override protected Void doInBackground() {
                hiloOperacion = Thread.currentThread();   // Detener corta la espera del freno de ESTA operación, no la de todos
                int i = 0;
                cargarEloAyer();
                List<Player> porApi = new ArrayList<>();
                long ahoraTs = System.currentTimeMillis();
                for (Player p : pendientes) {   // 1) resta con el snapshot nocturno: sin llamadas
                    Forma[] f = formaPorResta(p.id());
                    if (f == null) { porApi.add(p); continue; }
                    forma24.put(p.id(), f[0]); formaTs24.put(p.id(), ahoraTs);
                    if (f[1] != null) { forma7d.put(p.id(), f[1]); formaTs7d.put(p.id(), ahoraTs); } else if (horas > 24) porApi.add(p);
                }
                publish(t("Forma: ", "Recent form: ") + (pendientes.size() - porApi.size()) + t(" del snapshot nocturno", " from the nightly snapshot") + (porApi.isEmpty() ? "" : " · " + porApi.size() + t(" consultas", " requests")));
                for (Player p : porApi) {   // 2) quien no está en el snapshot: su serie de rating, exacta (una llamada por jugador)
                    if (stopOperacion) break;
                    try {
                        Forma[] ambas = calcFormaSerie(p.id());
                        if (ambas != null) { forma24.put(p.id(), ambas[0]); formaTs24.put(p.id(), ahoraTs); forma7d.put(p.id(), ambas[1]); formaTs7d.put(p.id(), ahoraTs); }
                    } catch (Exception ex) { log("forma " + p.name() + ": " + causa(ex)); }
                }
                return null;
            }
            @Override protected void process(List<String> ch) { status.setText(ch.get(ch.size() - 1)); }
            @Override protected void done() {
                if (miSerial != opSerial) return;
                trabajando(false);
                status.setText(t("Forma consultada.", "Recent form fetched."));
                if (alTerminar != null) alTerminar.run();
            }
        }.execute();
    }

    /** Objetivo de «Ver forma»: los seleccionados si los hay; si no, todos los visibles. */
    List<Player> objetivoForma() {
        List<Player> sel = playersList.getSelectedValuesList();
        return sel.isEmpty() ? jugadoresVisibles() : new ArrayList<>(sel);
    }

    /** El botón dice lo que va a consultar: «Ver forma (todos · 50)» / «(3 seleccionados)» / «Ocultar forma». */
    void actualizarTextoForma() {
        if (formaBtn == null) return;
        int n = playersList.getSelectedIndices().length;
        String quien = n > 0 ? n + t(" seleccionado" + (n > 1 ? "s" : ""), " selected")
                             : t("todos \u00B7 ", "all \u00B7 ") + playersModel.size();
        formaBtn.setText(t("Ver forma", "Recent form") + " (" + quien + ")");
    }

    List<Player> jugadoresVisibles() {
        List<Player> v = new ArrayList<>();
        for (int i = 0; i < playersModel.size(); i++) v.add(playersModel.get(i));
        return v;
    }

    final Set<Long> reveladas = new HashSet<>();   // ojos abiertos fila a fila; se olvidan con cada tabla nueva

    boolean revelada(Match m) { return (mostrarResultados && m.gte == 0) || reveladas.contains(m.id); }   // el global no destapa GTE

    /** Cada tabla nueva nace tapada: se apaga el modo consulta y se cierran los ojos. */
    void taparResultados() {
        boolean cambia = mostrarResultados || !reveladas.isEmpty();
        mostrarResultados = false;
        reveladas.clear();
        if (resultadosBtn != null && resultadosBtn.isSelected()) resultadosBtn.setSelected(false);
        if (cambia) { actualizarNotaSpoilers(); if (tableModel != null) tableModel.fireTableDataChanged(); }
    }
    final Set<Long> topVerificados = java.util.concurrent.ConcurrentHashMap.newKeySet();
    volatile int fallosFetch;   // jugadores sin respuesta en la última búsqueda
    final Map<Long, String> vivoInfo = new java.util.concurrent.ConcurrentHashMap<>();

    // ----- Ladder (vista): constantes y estado de pantalla; los resúmenes de sfr-data viven en sfrdata.Ladder -----
    static final int MAX_COMPARADOS = 10;
    static final String[] LADDER_IDS = { "rm_1v1", "rm_team", "ew_1v1", "ew_team" };
    static final String[] FAMILIAS = { "rm", "ew" };
    static String ladderNombre(String id) {
        return switch (id) {
            case "rm_1v1" -> "1v1 Random Map"; case "rm_team" -> t("Equipos Random Map", "Team Random Map");
            case "ew_1v1" -> "1v1 Empire Wars"; case "ew_team" -> t("Equipos Empire Wars", "Team Empire Wars");
            default -> id;
        };
    }
    static String familiaNombre(String f) { return "ew".equals(f) ? "Empire Wars" : "Random Map"; }
    static final String TOP_CLAN = t("\u2605 Top clan", "\u2605 Clan top");
    JComboBox<String> topNCombo; boolean rellenandoTopN; JPanel norteWatchRef; JButton todasPerfilBtn;
    JPanel parClan, parClanGuardados; JTextField clanField; JPopupMenu clanPopup; JComboBox<String> clanesGuardadosCombo; JButton clanEstrella; boolean rellenandoClanes;
    List<String> clanesGuardados() { List<String> l = new ArrayList<>(); for (String x : leerConfig("clanes_guardados", "").split(",")) if (!x.isBlank()) l.add(x.trim()); return l; }
    void refrescarClanesGuardados() {
        if (clanesGuardadosCombo == null) return;
        rellenandoClanes = true;
        try {
            List<String> l = clanesGuardados();
            clanesGuardadosCombo.removeAllItems();
            clanesGuardadosCombo.addItem(l.isEmpty() ? t("(sin clanes guardados)", "(no saved clans)") : t("Guardados…", "Saved…"));
            for (String x : l) clanesGuardadosCombo.addItem(x);
            String actual = clanField == null ? "" : clanField.getText().trim();
            boolean guardado = l.stream().anyMatch(x -> x.equalsIgnoreCase(actual));
            clanEstrella.setText(guardado ? t("Quitar de guardados", "Remove from saved") : t("Guardar clan", "Save clan"));
            clanEstrella.setEnabled(!actual.isEmpty());
            if (parClanGuardados != null) parClanGuardados.setVisible(parClan != null && parClan.isVisible() && !l.isEmpty());
        } finally { rellenandoClanes = false; }
    }
    JToggleButton ladderBtn;
    JPanel ladderPanel; JComboBox<String> familiaCombo; JCheckBox activosCheck, dispersionCheck; JSplitPane ladderDivisor;
    JTextField ladderBusca; JPopupMenu ladderPopup; JLabel ladderEstado, ladderPista;
    final List<Comparado> ladderComparados = new ArrayList<>();
    HistogramaPanel histograma1, histograma2; DispersionPanel dispersion; JPanel ladderChips, ladderGraficos; JButton ladderQuitarTodos; JTable ladderTabla; DefaultTableModel ladderModelo;
    javax.swing.Timer ladderDebounce;
    String familia = "ew".equals(leerConfig("ladder_familia", "rm")) ? "ew" : "rm";
    boolean soloActivos = Boolean.parseBoolean(leerConfig("ladder_activos", "true"));
    static final Color[] PALETA_LADDER = { new Color(0xe5, 0x73, 0x73), new Color(0x81, 0xc7, 0x84), new Color(0xff, 0xb7, 0x4d), new Color(0xba, 0x68, 0xc8), new Color(0x4d, 0xd0, 0xe1),
            new Color(0xff, 0xf1, 0x76), new Color(0xf0, 0x62, 0x92), new Color(0xa1, 0x88, 0x7f), new Color(0x90, 0xa4, 0xae), new Color(0x7c, 0xb3, 0x42) };

    /** El «Top %» que se muestra: con todos y rango conocido, el exacto por rango; si no, por rating sobre la campana elegida. */
    String topDe(Comparado c, String lb) {
        String p = !soloActivos && c.rank(lb) > 0 ? percentilRango(lb, c.rank(lb)) : null;
        return p != null ? p : percentilRating(lb, soloActivos, c.rating(lb));
    }

    /** Perfil del jugador (una llamada), con caché de 30 min: {ms, Map ladder→{rating, rango, máximo, victorias, derrotas}, país, clan, partidas totales}. */
    static Object[] perfilLadders(long pid) {
        Object[] c = PERFIL_CACHE.get(pid);
        if (c != null && System.currentTimeMillis() - (long) c[0] < 30 * 60_000L) return c;
        Map<String, int[]> m = new HashMap<>();
        String pais = "", clan = "";
        long games = 0;
        try {
            Perfil pf = COMPANION.perfil(pid);
            aprenderCanal(pid, pf.canal());
            aprenderPais(pid, pf.pais());
            pais = String.valueOf(firstNonNull(pf.pais(), "")).trim();
            if ("null".equals(pais)) pais = "";
            clan = String.valueOf(firstNonNull(pf.clan(), "")).trim();
            if ("null".equals(clan)) clan = "";
            games = pf.partidas();
            for (Perfil.Ladder l : pf.ladders()) {
                String lid = String.valueOf(l.id());
                String lb = switch (lid) { case "3" -> "rm_1v1"; case "4" -> "rm_team"; case "13" -> "ew_1v1"; case "14" -> "ew_team"; default -> lid; };
                if (!Arrays.asList(LADDER_IDS).contains(lb)) continue;
                int rating = l.rating() != null ? l.rating() : 0;
                int rank = l.rango() != null ? l.rango() : 0;
                int maxR = l.ratingMax() != null ? l.ratingMax() : 0;
                int wins = l.ganadas() != null ? l.ganadas() : 0;
                int losses = l.perdidas() != null ? l.perdidas() : 0;
                m.put(lb, new int[]{ rating, rank, maxR, wins, losses });
            }
        } catch (Exception ex) { log("ladder: perfil " + pid + ": " + causa(ex)); return null; }
        c = new Object[]{ System.currentTimeMillis(), m, pais, clan, games };
        PERFIL_CACHE.put(pid, c);
        return c;
    }
    // ----- «★ Top clan»: una vista más de la watchlist -----
    void cargarTopClan() {
        String tag = clanField == null ? "" : clanField.getText().trim();
        if (tag.isEmpty()) { status.setText(t("Escribe el tag del clan (p. ej. R1).", "Type the clan tag (e.g. R1).")); return; }
        status.setText(clanes.isEmpty() ? t("Descargando la lista de clanes…", "Downloading the clan list…") : t("Cargando el clan…", "Loading the clan…"));
        new Thread(() -> {
            String err = ladderAsegurar(false);
            List<LadderRow> mi = err == null ? miembrosClan(tag) : List.of();
            SwingUtilities.invokeLater(() -> {
                if (err != null) { status.setText(t("No se pudo cargar la lista de clanes: ", "Couldn't load the clan list: ") + err); return; }
                topLadder.clear();
                ultimoTopMs = 0; ultimoTwitchMs = 0;
                lastTop.clear(); rankTop.clear();
                for (LadderRow r : mi) {
                    topLadder.add(new Player(r.pid(), r.name(), TOP_CLAN));
                    eloWatch.put(r.pid(), r.rating());
                    rankTop.put(r.pid(), topLadder.size());
                }
                guardarConfig("clan_tag", tag);
                aplicarFiltroGrupo();
                actualizarIndicadoresVivos();
                status.setText(mi.isEmpty() ? t("Ningún clan del ladder 1v1 se llama «", "No 1v1 ladder clan is called \u201C") + tag + t("» (elige uno de las sugerencias).", "\u201D (pick one from the suggestions).")
                        : t("Clan ", "Clan ") + tag + ": " + mi.size() + t(" jugadores en el ladder 1v1 (resumen diario).", " players on the 1v1 ladder (daily summary)."));
            });
        }, "top-clan").start();
    }

    // ----- Panel «Ladder»: dos campanas (1v1 arriba, equipos debajo), dispersión, percentiles y comparador -----
    double ratingsEscala = Math.max(0.6, Math.min(1.6, Double.parseDouble(leerConfig("ratings_escala", "1"))));   // tamaño de los tres gráficos (zoom de conjunto)

    class HistogramaPanel extends JPanel {
        String lb;
        int ml = 58, mb = 30, mt = 30, mr = 18;
        HistogramaPanel(String lb) {
            this.lb = lb; setOpaque(false);
            ToolTipManager.sharedInstance().registerComponent(this);
            addMouseWheelListener(e -> {
                if (e.isControlDown()) { escalarRatings(e.getPreciseWheelRotation() < 0 ? 1.15 : 1 / 1.15); e.consume(); }
                else { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this); if (sc != null) sc.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, sc)); }
            });
            addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) escalarRatingsReset(); } });
        }
        LadderHist datos() { return hist(lb, soloActivos); }
        /** Rango de ELO de esta campana. */
        double[] rango() {
            LadderHist h = datos();
            if (h == null) return new double[]{ 0, 3000 };
            return new double[]{ h.min(), h.min() + h.bins().length * (double) BIN_LADDER };
        }
        double sx() { double[] r = rango(); return (getWidth() - ml - mr) / Math.max(1, r[1] - r[0]); }
        double eloEn(int x) { return rango()[0] + (x - ml) / sx(); }
        int xDe(double elo) { return ml + (int) Math.round((elo - rango()[0]) * sx()); }
        @Override public String getToolTipText(MouseEvent e) {
            LadderHist h = datos();
            if (h == null || e.getX() < ml || e.getX() > getWidth() - mr) return null;
            int idx = Math.floorDiv((int) Math.floor(eloEn(e.getX())) - h.min(), BIN_LADDER);
            if (idx < 0 || idx >= h.bins().length) return null;
            long encima = 0; for (int i = idx + 1; i < h.bins().length; i++) encima += h.bins()[i];
            int lo = h.min() + idx * BIN_LADDER;
            return "<html><b>" + lo + "–" + (lo + BIN_LADDER - 1) + "</b>: " + miles(h.bins()[idx]) + t(" jugadores", " players") + "<br>"
                    + miles(encima) + t(" por encima", " above") + " (" + escapeHtml(fmtTop(100.0 * encima / h.total())) + ")</html>";
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), ht = getHeight();
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            Color gris = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 90 : 170), tenue = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 35);
            LadderHist h = datos();
            Font base = g2.getFont();
            g2.setFont(base.deriveFont(Font.BOLD, 12f));
            g2.setColor(fg);
            String titulo = ladderNombre(lb) + (h == null ? "" : "  ·  " + miles(h.total()) + (soloActivos && ladderHistsActivos.containsKey(lb) ? t(" activos", " active") : t(" jugadores", " players"))
                    + "  ·  " + t("mediana ", "median ") + h.mediana());
            g2.drawString(titulo, ml, 16);
            g2.setFont(base);
            if (h == null || h.bins().length == 0 || h.total() == 0) { g2.setColor(gris); g2.drawString(t("Sin datos", "No data"), ml, ht / 2); g2.dispose(); return; }
            int[] bins = h.bins(); int minR = h.min();
            double[] r = rango(); double sx = sx();
            int i0 = Math.max(0, (int) Math.floor((r[0] - minR) / BIN_LADDER)), i1 = Math.min(bins.length - 1, (int) Math.ceil((r[1] - minR) / BIN_LADDER));
            int max = 1; for (int i = i0; i <= i1; i++) max = Math.max(max, bins[i]);
            // eje Y: jugadores por tramo de 25, con marcas «redondas»
            double pasoY = pasoBonito(max / 4.0);
            g2.setFont(base.deriveFont(10f));
            for (double v = pasoY; v <= max; v += pasoY) {
                int y = ht - mb - (int) ((ht - mb - mt) * v / max);
                g2.setColor(tenue); g2.drawLine(ml, y, w - mr, y);
                g2.setColor(gris); String s = etiquetaK(v); g2.drawString(s, ml - 6 - g2.getFontMetrics().stringWidth(s), y + 4);
            }
            g2.setColor(gris);
            g2.drawLine(ml, ht - mb, w - mr, ht - mb);
            g2.drawLine(ml, mt, ml, ht - mb);
            // eje X: ELO, marcas según el zoom
            int pasoX = (int) pasoBonito((r[1] - r[0]) / 8.0);
            for (int v = (int) (Math.ceil(r[0] / pasoX) * pasoX); v <= r[1]; v += pasoX) {
                int x = xDe(v);
                g2.drawLine(x, ht - mb, x, ht - mb + 4);
                String s = String.valueOf(v);
                g2.drawString(s, x - g2.getFontMetrics().stringWidth(s) / 2, ht - mb + 16);
            }
            g2.drawString("ELO", w - mr - g2.getFontMetrics().stringWidth("ELO"), ht - mb + 27);
            g2.drawString(t("jugadores", "players"), 4, mt - 6);
            Color barra = temaOscuroActivo ? new Color(0x5a, 0x8f, 0xc7) : new Color(0x3b, 0x6e, 0xa8);
            int anchoBarra = Math.max(1, (int) Math.ceil(BIN_LADDER * sx) - 1);
            for (int i = i0; i <= i1; i++) {
                int bh = (int) ((ht - mb - mt) * bins[i] / (double) max);
                int x = xDe(minR + i * BIN_LADDER);
                if (x + anchoBarra < ml || x > w - mr) continue;
                g2.setColor(barra);
                g2.fillRect(Math.max(ml, x), ht - mb - bh, Math.min(anchoBarra, w - mr - Math.max(ml, x)), bh);
            }
            // mediana: línea gruesa y etiqueta con fondo
            if (h.mediana() >= r[0] && h.mediana() <= r[1]) {
                int xm = xDe(h.mediana());
                g2.setColor(temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00));
                g2.setStroke(new BasicStroke(2.2f));
                g2.drawLine(xm, mt, xm, ht - mb);
                String s = t("mediana ", "median ") + h.mediana();
                g2.setFont(base.deriveFont(Font.BOLD, 11f));
                int sw = g2.getFontMetrics().stringWidth(s);
                int lx = xm + 6 + sw > w - mr ? xm - 6 - sw : xm + 6;
                g2.setColor(temaOscuroActivo ? new Color(0, 0, 0, 150) : new Color(255, 255, 255, 190));
                g2.fillRoundRect(lx - 4, mt + 2, sw + 8, 16, 6, 6);
                g2.setColor(temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00));
                g2.drawString(s, lx, mt + 14);
            }
            g2.setFont(base);
            int k = 0;
            for (Comparado c : ladderComparados) {
                int rating = c.rating(lb);
                Color col = PALETA_LADDER[k % PALETA_LADDER.length];
                k++;
                if (rating <= 0 || rating < r[0] || rating > r[1]) continue;
                int x = xDe(rating);
                g2.setColor(col);
                g2.setStroke(new BasicStroke(2f));
                int fila = (k - 1) % 5;
                g2.drawLine(x, mt + 22 + fila * 13, x, ht - mb);
                String p = topDe(c, lb);
                String etiqueta = c.name() + " · " + rating + (p != null ? " · " + p : "");
                int ancho = g2.getFontMetrics().stringWidth(etiqueta);
                g2.drawString(etiqueta, x + 4 + ancho > w - mr ? x - 4 - ancho : x + 4, mt + 32 + fila * 13);   // junto al borde derecho, a la izquierda de la línea
            }
            g2.dispose();
        }
    }

    static double pasoBonito(double bruto) {
        if (bruto <= 0) return 1;
        double p = Math.pow(10, Math.floor(Math.log10(bruto)));
        double f = bruto / p;
        return (f < 1.5 ? 1 : f < 3.5 ? 2 : f < 7.5 ? 5 : 10) * p;
    }
    static String etiquetaK(double v) { return v >= 1000 ? (v % 1000 == 0 ? (long) (v / 1000) + "k" : String.format(Locale.ROOT, "%.1fk", v / 1000)) : String.valueOf((long) v); }

    /** Zoom de conjunto: los tres gráficos crecen o encogen a la vez (entre la mitad y el doble); se recuerda. */
    void escalarRatings(double factor) {
        ratingsEscala = Math.max(0.6, Math.min(1.6, ratingsEscala * factor));
        guardarConfig("ratings_escala", String.format(Locale.ROOT, "%.3f", ratingsEscala));
        aplicarEscalaRatings();
    }
    void escalarRatingsReset() { ratingsEscala = 1; guardarConfig("ratings_escala", "1"); aplicarEscalaRatings(); }
    /** Tamaño y disposición de los gráficos según la escala: apilados a tamaño normal; al alejar, las campanas lado a lado y la dispersión a media anchura, cada uno con su proporción. */
    void aplicarEscalaRatings() {
        if (histograma1 == null || ladderGraficos == null) return;
        for (JComponent c : new JComponent[]{ histograma1, histograma2, dispersion }) {
            int alto = (int) Math.round((c == dispersion ? 420 : 300) * ratingsEscala);
            c.setPreferredSize(new Dimension(10, alto)); c.setMinimumSize(new Dimension(10, alto)); c.setMaximumSize(new Dimension(Integer.MAX_VALUE, alto));
            c.setAlignmentX(0f);
        }
        ladderGraficos.removeAll();
        if (ratingsEscala >= 0.75) {
            ladderGraficos.add(histograma1); ladderGraficos.add(Box.createRigidArea(new Dimension(0, 16)));
            ladderGraficos.add(histograma2); ladderGraficos.add(Box.createRigidArea(new Dimension(0, 16)));
            ladderGraficos.add(dispersion);
        } else {
            JPanel fila1 = new JPanel(new GridLayout(1, 2, 16, 0)); fila1.setOpaque(false); fila1.setAlignmentX(0f);
            fila1.add(histograma1); fila1.add(histograma2);
            fila1.setMaximumSize(new Dimension(Integer.MAX_VALUE, histograma1.getPreferredSize().height));
            JPanel fila2 = new JPanel(new GridLayout(1, 2, 16, 0)); fila2.setOpaque(false); fila2.setAlignmentX(0f);
            JPanel hueco = new JPanel(); hueco.setOpaque(false);
            fila2.add(dispersion); fila2.add(hueco);
            fila2.setMaximumSize(new Dimension(Integer.MAX_VALUE, dispersion.getPreferredSize().height));
            ladderGraficos.add(fila1); ladderGraficos.add(Box.createRigidArea(new Dimension(0, 12))); ladderGraficos.add(fila2);
        }
        ladderGraficos.revalidate(); ladderGraficos.repaint();
    }

    /** Dispersión rating 1v1 (x) × rating equipos (y): densidad por celdas de 25×25, recta de regresión y los comparados como puntos. */
    class DispersionPanel extends JPanel {
        DispersionPanel() { setOpaque(false); }
        Rejilla datos() {
            Rejilla r = soloActivos ? dispersionActivos.get(familia) : null;
            return r != null ? r : dispersionTodos.get(familia);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), ht = getHeight(), ml = 52, mb = 30, mt = 30, mr = 16;
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            Color gris = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 90 : 170);
            Font base = g2.getFont();
            Rejilla r = datos();
            String lbx = familia + "_1v1", lby = familia + "_team";
            g2.setFont(base.deriveFont(Font.BOLD, 12f));
            g2.setColor(fg);
            g2.drawString(t("Dispersión ", "Scatter ") + ladderNombre(lbx) + " × " + ladderNombre(lby)
                    + (r == null ? "" : "  ·  " + miles(r.n()) + (soloActivos && dispersionActivos.containsKey(familia) ? t(" activos", " active") : t(" jugadores", " players")) + t(" en ambos ladders", " on both ladders")
                    + "  ·  r = " + String.format(Locale.ROOT, "%.2f", r.r())), ml, 16);
            g2.setFont(base);
            if (r == null) { g2.setColor(gris); g2.drawString(t("Sin datos de dispersión (aún no publicados o menos de 100 jugadores).", "No scatter data (not published yet or fewer than 100 players)."), ml, ht / 2); g2.dispose(); return; }
            int maxIx = 0, maxIy = 0, maxN = 1;
            for (int[] c : r.celdas()) { maxIx = Math.max(maxIx, c[0]); maxIy = Math.max(maxIy, c[1]); maxN = Math.max(maxN, c[2]); }
            int x0 = r.minX(), x1 = r.minX() + (maxIx + 1) * BIN_LADDER, y0 = r.minY(), y1 = r.minY() + (maxIy + 1) * BIN_LADDER;
            double sx = (w - ml - mr) / (double) (x1 - x0), sy = (ht - mb - mt) / (double) (y1 - y0);
            Color base2 = temaOscuroActivo ? new Color(0x5a, 0x8f, 0xc7) : new Color(0x3b, 0x6e, 0xa8);
            int cw = Math.max(1, (int) Math.ceil(BIN_LADDER * sx)), ch = Math.max(1, (int) Math.ceil(BIN_LADDER * sy));
            for (int[] c : r.celdas()) {
                int alfa = 25 + (int) (225 * Math.sqrt(c[2] / (double) maxN));
                g2.setColor(new Color(base2.getRed(), base2.getGreen(), base2.getBlue(), Math.min(255, alfa)));
                int px = ml + (int) ((c[0] * BIN_LADDER) * sx);
                int py = ht - mb - (int) (((c[1] + 1) * BIN_LADDER) * sy);
                g2.fillRect(px, py, cw, ch);
            }
            g2.setColor(gris);
            g2.drawLine(ml, ht - mb, w - mr, ht - mb);
            g2.drawLine(ml, mt, ml, ht - mb);
            g2.setFont(base.deriveFont(10f));
            for (int v = (Math.floorDiv(x0, 500) + 1) * 500; v < x1; v += 500) { int x = ml + (int) ((v - x0) * sx); g2.drawLine(x, ht - mb, x, ht - mb + 4); g2.drawString(String.valueOf(v), x - 12, ht - mb + 16); }
            for (int v = (Math.floorDiv(y0, 500) + 1) * 500; v < y1; v += 500) { int y = ht - mb - (int) ((v - y0) * sy); g2.drawLine(ml - 4, y, ml, y); g2.drawString(String.valueOf(v), 6, y + 4); }
            g2.drawString(t("ELO 1v1 →", "1v1 ELO →"), w - mr - 60, ht - 4);
            g2.drawString(t("↑ ELO equipos", "↑ Team ELO"), ml + 4, mt + 12);
            // recta y = a·x + b, recortada al rango vertical visible
            g2.setColor(new Color(0xe0, 0x60, 0x40));
            g2.setStroke(new BasicStroke(1.6f));
            double ya = r.a() * x0 + r.b(), yb = r.a() * x1 + r.b();
            int px1 = ml, px2 = w - mr;
            int py1 = ht - mb - (int) ((ya - y0) * sy), py2 = ht - mb - (int) ((yb - y0) * sy);
            Shape clipPrevio = g2.getClip();   // nunca setClip(null): quitaría el recorte del visor y pintaría sobre la tabla
            g2.clipRect(ml, mt, w - ml - mr, ht - mb - mt);
            g2.drawLine(px1, py1, px2, py2);
            g2.setClip(clipPrevio);
            g2.drawString(String.format(Locale.ROOT, "y = %.2f·x + %.0f", r.a(), r.b()), w - mr - 120, mt + 12);
            int k = 0;
            for (Comparado c : ladderComparados) {
                Color col = PALETA_LADDER[k % PALETA_LADDER.length];
                k++;
                int rx = c.rating(lbx), ry = c.rating(lby);
                if (rx <= 0 || ry <= 0) continue;
                int px = ml + (int) ((rx - x0) * sx), py = ht - mb - (int) ((ry - y0) * sy);
                g2.setColor(col);
                g2.fillOval(px - 5, py - 5, 10, 10);
                g2.setColor(fg);
                g2.drawOval(px - 5, py - 5, 10, 10);
                g2.setColor(col);
                int ancho = g2.getFontMetrics().stringWidth(c.name());
                g2.drawString(c.name(), px + 8 + ancho > w - mr ? px - 8 - ancho : px + 8, py - 4);
            }
            g2.dispose();
        }
    }

    JPanel construirPanelLadder() {
        ladderPanel = new JPanel(new BorderLayout(8, 6));
        ladderPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel norte = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        familiaCombo = new JComboBox<>(new String[]{ familiaNombre("rm"), familiaNombre("ew") });
        familiaCombo.setSelectedIndex("ew".equals(familia) ? 1 : 0);
        familiaCombo.setToolTipText(t("Random Map: campanas 1v1 y equipos RM. Empire Wars: las de EW.", "Random Map: 1v1 and team RM curves. Empire Wars: the EW ones."));
        familiaCombo.addActionListener(e -> { familia = FAMILIAS[Math.max(0, familiaCombo.getSelectedIndex())]; guardarConfig("ladder_familia", familia); ladderRefrescar(); });
        norte.add(familiaCombo);
        activosCheck = new JCheckBox(t("Solo activos", "Active only"), soloActivos);
        activosCheck.setFocusable(false);
        activosCheck.addActionListener(e -> { soloActivos = activosCheck.isSelected(); guardarConfig("ladder_activos", String.valueOf(soloActivos)); ladderRefrescar(); });
        norte.add(activosCheck);
        JPanel zoom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        JLabel zl = new JLabel(t("Zoom:", "Zoom:")); zl.setFont(zl.getFont().deriveFont(11f)); zoom.add(zl);
        for (String[] z : new String[][]{ { "\u2212", "out" }, { "+", "in" }, { "\u27F2", "reset" } }) {
            JButton zb = new JButton(z[0]);
            zb.setFocusable(false); zb.setMargin(new Insets(0, 6, 0, 6)); zb.putClientProperty("JButton.buttonType", "roundRect");
            zb.setToolTipText(t("Tamaño de los tres gráficos a la vez: aleja para verlos todos con la tabla, acerca para leer detalle. También Ctrl + rueda sobre un gráfico; doble clic para volver.", "Size of the three charts at once: zoom out to see them all with the table, zoom in for detail. Also Ctrl + wheel over a chart; double-click to reset."));
            zb.addActionListener(e -> { switch (z[1]) { case "in" -> escalarRatings(1.25); case "out" -> escalarRatings(1 / 1.25); default -> escalarRatingsReset(); } });
            zoom.add(zb);
        }
        ladderBusca = new JTextField(18);
        ladderBusca.putClientProperty("JTextField.placeholderText", t("Buscar jugador… o selecciona en la watchlist", "Search a player… or select in the watchlist"));
        ladderBusca.putClientProperty("JTextField.showClearButton", true);
        ladderPopup = new JPopupMenu(); ladderPopup.setFocusable(false);
        ladderDebounce = new javax.swing.Timer(450, e -> ladderSugerir());
        ladderDebounce.setRepeats(false);
        ladderBusca.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() { ladderDebounce.restart(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        ladderBusca.addActionListener(e -> {   // Enter: la primera sugerencia (y así el Enter no llega al botón de buscar partidas)
            if (ladderPopup.isVisible() && ladderPopup.getComponentCount() > 0) ((JMenuItem) ladderPopup.getComponent(0)).doClick();
            else ladderSugerir();
        });
        ladderBusca.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && ladderPopup.isVisible() && ladderPopup.getComponentCount() > 0) { ((JMenuItem) ladderPopup.getComponent(0)).doClick(); e.consume(); }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) ladderPopup.setVisible(false);
            }
        });
        norte.add(ladderBusca, 0);   // el buscador, siempre lo primero de la barra (igual en Perfil)
        ladderEstado = new JLabel();
        ladderEstado.setFont(ladderEstado.getFont().deriveFont(Font.PLAIN, 11f));
        norte.add(ladderEstado);
        ladderPanel.add(norte, BorderLayout.NORTH);

        JPanel centro = new JPanel(new BorderLayout(0, 4));
        JPanel arriba = new JPanel(new BorderLayout(0, 2));
        ladderPista = new JLabel(t("Busca un jugador arriba o selecciónalo en la watchlist (Ctrl para varios): aparecerá en las campanas.",
                "Search a player above or select one in the watchlist (Ctrl for several): they'll show up on the curves."));
        ladderPista.setFont(ladderPista.getFont().deriveFont(Font.ITALIC, 11f));
        ladderPista.setForeground(Color.GRAY);
        arriba.add(ladderPista, BorderLayout.NORTH);
        ladderChips = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 2));
        arriba.add(ladderChips, BorderLayout.CENTER);
        ladderQuitarTodos = new JButton(t("\u00D7 Quitar todos", "\u00D7 Remove all"));
        ladderQuitarTodos.setFocusable(false); ladderQuitarTodos.setMargin(new Insets(1, 6, 1, 6)); ladderQuitarTodos.putClientProperty("JButton.buttonType", "roundRect");
        ladderQuitarTodos.setVisible(false);
        ladderQuitarTodos.addActionListener(e -> { playersList.clearSelection(); ladderComparados.clear(); ladderRefrescarComparados(); });
        arriba.add(ladderQuitarTodos, BorderLayout.EAST);
        centro.add(arriba, BorderLayout.NORTH);
        histograma1 = new HistogramaPanel(familia + "_1v1");
        histograma2 = new HistogramaPanel(familia + "_team");
        dispersion = new DispersionPanel();
        PanelScrollable graficos = new PanelScrollable();
        ladderGraficos = graficos;
        aplicarEscalaRatings();
        dispersion.addMouseWheelListener(e -> {   // el zoom de conjunto también desde la dispersión
            if (e.isControlDown()) { escalarRatings(e.getPreciseWheelRotation() < 0 ? 1.15 : 1 / 1.15); e.consume(); }
            else { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, dispersion); if (sc != null) sc.dispatchEvent(SwingUtilities.convertMouseEvent(dispersion, e, sc)); }
        });
        JScrollPane scroll = new JScrollPane(graficos, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        ladderModelo = new DefaultTableModel(new Object[]{ t("Jugador", "Player"), "ELO 1v1", t("Rango 1v1", "1v1 rank"), t("Top % 1v1", "1v1 top %"),
                t("ELO equipos", "Team ELO"), t("Rango eq.", "Team rank"), t("Top % eq.", "Team top %"), t("Clan", "Clan"), t("País", "Country") }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        ladderTabla = new JTable(ladderModelo);
        ladderTabla.setRowHeight(ladderTabla.getRowHeight() + 4);
        ladderTabla.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel lab = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, col);
                lab.setIcon(value == null ? null : iconoBandera(String.valueOf(value))); lab.setIconTextGap(5);
                return lab;
            }
        });
        JScrollPane sp = new JScrollPane(ladderTabla);
        sp.setPreferredSize(new Dimension(10, 150));
        JPanel abajo = new JPanel(new BorderLayout(0, 2));
        abajo.add(zoom, BorderLayout.NORTH);   // el zoom, abajo a la derecha, bajo las gráficas
        abajo.add(sp, BorderLayout.CENTER);
        abajo.setMinimumSize(new Dimension(10, 60));
        ladderDivisor = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, abajo);   // arrastra el borde: la tabla de comparados crece
        ladderDivisor.setResizeWeight(1.0); ladderDivisor.setContinuousLayout(true); ladderDivisor.setBorder(null); ladderDivisor.setDividerSize(7);
        ladderDivisor.setOneTouchExpandable(true);
        ladderDivisor.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, ev -> {
            if (ladderDivisor.isShowing() && ladderDivisor.getHeight() > 0) guardarConfig("ratings_tabla_alto", String.valueOf(Math.max(60, ladderDivisor.getHeight() - ladderDivisor.getDividerLocation() - ladderDivisor.getDividerSize())));
        });
        centro.add(ladderDivisor, BorderLayout.CENTER);
        ladderPanel.add(centro, BorderLayout.CENTER);
        JLabel pie = new JLabel(t("Distribución de ELO por ladder (resumen diario de sfr-data sobre los volcados de aoe2companion). «Solo activos»: jugadores con partidas recientes; el Top % se calcula sobre la campana elegida. Pasa el ratón por una barra para ver cuántos jugadores hay en ese tramo. Los seleccionados en la watchlist se dibujan mientras Ratings esté abierto.",
                "ELO distribution per ladder (sfr-data daily summary of the aoe2companion dumps). \u201CActive only\u201D: players with recent games; the top % is computed on the chosen curve. Hover a bar to see how many players sit in that bracket. Watchlist selections are drawn while Ratings is open."));
        pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f));
        ladderPanel.add(pie, BorderLayout.SOUTH);
        return ladderPanel;
    }
    /** Sugerencias de jugadores por nombre (búsqueda del companion, con retardo para no disparar por cada tecla). */
    void ladderSugerir() {
        String q = ladderBusca.getText().trim();
        ladderPopup.setVisible(false); ladderPopup.removeAll();
        if (q.length() < 2) return;
        new Thread(() -> {
            List<String[]> res = sugerirPerfiles(q);
            SwingUtilities.invokeLater(() -> {
                if (!q.equals(ladderBusca.getText().trim())) return;
                ladderPopup.removeAll();
                int n = 0;
                for (String[] r : res) {
                    JMenuItem it = new JMenuItem(r[2]);
                    long pid = Long.parseLong(r[0]); String nombre = r[1];
                    it.addActionListener(a -> { ladderAnadirPorId(pid, nombre); ladderBusca.setText(""); ladderPopup.setVisible(false); });
                    ladderPopup.add(it);
                    if (++n >= 8) break;
                }
                if (n > 0 && ladderBusca.isShowing()) ladderPopup.show(ladderBusca, 0, ladderBusca.getHeight());
            });
        }, "ladder-sugerir").start();
    }

    /** Añade un jugador a la comparación (buscado a mano o seleccionado en la watchlist). */
    void ladderAnadir(long pid, String nombre, boolean deSeleccion) {
        for (Comparado x : ladderComparados) if (x.pid() == pid) return;
        ladderEstado.setText(t("Consultando a ", "Looking up ") + nombre + "…");
        new Thread(() -> {
            Object[] perfil = perfilLadders(pid);
            SwingUtilities.invokeLater(() -> {
                ladderEstado.setText("");
                if (perfil == null) { ladderEstado.setText(t("No se pudo consultar a ", "Couldn't look up ") + nombre + "."); return; }
                for (Comparado x : ladderComparados) if (x.pid() == pid) return;
                if (deSeleccion && !estaSeleccionado(pid)) return;   // se deseleccionó mientras se consultaba
                @SuppressWarnings("unchecked") Map<String, int[]> m = (Map<String, int[]>) perfil[1];
                if (m.isEmpty()) { ladderEstado.setText(nombre + t(" no tiene rating en ningún ladder.", " has no rating on any ladder.")); return; }
                if (ladderComparados.size() >= MAX_COMPARADOS) {
                    int i = -1;
                    for (int j = 0; j < ladderComparados.size(); j++) if (!ladderComparados.get(j).deSeleccion()) { i = j; break; }
                    if (deSeleccion || i < 0) { ladderEstado.setText(t("Máximo ", "Max ") + MAX_COMPARADOS + t(" jugadores en la comparación: quita alguno (×).", " players compared: remove one (×).")); return; }
                    ladderComparados.remove(i);   // cae el buscado a mano más antiguo
                }
                ladderComparados.add(new Comparado(pid, nombre, m, String.valueOf(perfil[2]), deSeleccion));
                ladderRefrescarComparados();
            });
        }, "ladder-perfil").start();
    }

    void ladderAnadirPorId(long pid, String nombre) { ladderAnadir(pid, nombre, false); }

    boolean estaSeleccionado(long pid) {
        for (Player p : playersList.getSelectedValuesList()) if (p.id() == pid) return true;
        return false;
    }

    /** Con el Ladder abierto, la selección de la watchlist se refleja en las campanas: entra lo seleccionado, sale lo deseleccionado. */
    void ladderSincronizarSeleccion() {
        if (ladderPanel == null || !ladderPanel.isShowing()) return;
        List<Player> sel = playersList.getSelectedValuesList();
        Set<Long> ids = new HashSet<>();
        for (Player p : sel) ids.add(p.id());
        boolean cambio = ladderComparados.removeIf(c -> c.deSeleccion() && !ids.contains(c.pid()));
        if (cambio) ladderRefrescarComparados();
        List<Player> nuevos = new ArrayList<>();
        int hueco = MAX_COMPARADOS - ladderComparados.size(), omitidos = 0;
        for (Player p : sel) {
            boolean ya = false;
            for (Comparado c : ladderComparados) if (c.pid() == p.id()) { ya = true; break; }
            if (ya) continue;
            if (nuevos.size() >= hueco) { omitidos++; continue; }
            nuevos.add(p);
        }
        if (omitidos > 0) ladderEstado.setText(t("Máximo ", "Max ") + MAX_COMPARADOS + t(" jugadores en la comparación: quedan fuera ", " players compared; left out: ") + omitidos + ".");
        for (Player p : nuevos) ladderAnadir(p.id(), nombreVisible(p.id(), p.name()), true);
    }

    static String colorHex(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }

    void ladderRefrescarComparados() {
        ladderChips.removeAll();
        ladderModelo.setRowCount(0);
        String lb1 = familia + "_1v1", lb2 = familia + "_team";
        int k = 0;
        for (Comparado c : ladderComparados) {
            Color col = PALETA_LADDER[k % PALETA_LADDER.length];
            k++;
            JButton chip = new JButton("<html><font color='" + colorHex(col) + "'>\u25A0</font> " + escapeHtml(c.name()) + "  \u00D7</html>");
            chip.setFocusable(false); chip.setMargin(new Insets(1, 6, 1, 6)); chip.putClientProperty("JButton.buttonType", "roundRect");
            chip.setToolTipText(c.deSeleccion() ? t("Seleccionado en la watchlist: deselecciónalo (o pulsa ×) para quitarlo", "Selected in the watchlist: deselect it (or press ×) to remove it")
                    : t("Buscado a mano: × para quitarlo", "Searched by hand: × to remove it"));
            chip.addActionListener(e -> {
                if (c.deSeleccion()) {   // quitar = deseleccionar en la lista; la sincronización lo saca
                    for (int i = 0; i < playersModel.getSize(); i++) if (playersModel.getElementAt(i).id() == c.pid()) playersList.removeSelectionInterval(i, i);
                }
                ladderComparados.remove(c);
                ladderRefrescarComparados();
            });
            ladderChips.add(chip);
            String clanDe = "";
            for (Map.Entry<String, List<LadderRow>> en : clanes.entrySet()) { for (LadderRow m : en.getValue()) if (m.pid() == c.pid()) { clanDe = en.getKey(); break; } if (!clanDe.isEmpty()) break; }
            int r1 = c.rating(lb1), r2 = c.rating(lb2), k1 = c.rank(lb1), k2 = c.rank(lb2);
            String p1 = r1 > 0 ? topDe(c, lb1) : null, p2 = r2 > 0 ? topDe(c, lb2) : null;
            ladderModelo.addRow(new Object[]{ c.name(), r1 > 0 ? r1 : "-", k1 > 0 ? "#" + miles(k1) : "-", p1 != null ? p1 : "-",
                    r2 > 0 ? r2 : "-", k2 > 0 ? "#" + miles(k2) : "-", p2 != null ? p2 : "-", clanDe, c.country() });
        }
        ladderPista.setVisible(ladderComparados.isEmpty());
        if (ladderQuitarTodos != null) ladderQuitarTodos.setVisible(ladderComparados.size() > 1);
        ladderChips.revalidate(); ladderChips.repaint();
        histograma1.repaint(); histograma2.repaint(); dispersion.repaint();
    }

    void ladderRefrescar() {
        histograma1.lb = familia + "_1v1";
        histograma2.lb = familia + "_team";
        activosCheck.setToolTipText(t("Solo jugadores con ", "Only players with ") + activosMinPartidas + t(" o más partidas en ese ladder y una en los últimos ", " games or more on that ladder and one in the last ")
                + activosDias + t(" días. Sin marcar: todos los que tienen rating.", " days. Unticked: everyone with a rating."));
        ladderRefrescarComparados();
        LadderHist h = hist(histograma1.lb, soloActivos);
        ladderEstado.setText(h == null ? t("Sin datos de ese ladder.", "No data for that ladder.")
                : t("Resumen del ", "Summary of ") + ladderGenerado.replace("T", " ").substring(0, Math.min(16, ladderGenerado.length())) + " UTC");
    }

    void abrirLadder() {
        registrarDestino(new Destino("ladder", 0, null, null));
        if (ladderBtn != null && !ladderBtn.isSelected()) ladderBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        actividadAbierta = false;
        if (perfilBtn != null) perfilBtn.setSelected(false);
        ahoraAbierta = false; if (ahoraBtn != null) ahoraBtn.setSelected(false);
        if (techTreeBtn != null && techTreeBtn.isSelected()) cerrarTechTree();
        ((CardLayout) centroCards.getLayout()).show(centroCards, "ladder");
        subirArriba(ladderPanel);
        SwingUtilities.invokeLater(() -> {   // la tabla de comparados, con el alto que dejaste (230 px la primera vez)
            int alto = Math.max(60, Integer.parseInt(leerConfig("ratings_tabla_alto", "230")));
            if (ladderDivisor.getHeight() > alto + 100) ladderDivisor.setDividerLocation(ladderDivisor.getHeight() - alto - ladderDivisor.getDividerSize());
        });
        taparResultados(); apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        if (ladderCargando) return;
        ladderCargando = true;
        ladderEstado.setText(t("Cargando los resúmenes de ratings…", "Loading the ratings summaries…"));
        javax.swing.Timer tick = new javax.swing.Timer(500, e -> { if (ladderCargando) ladderEstado.setText(ladderProgreso); });
        tick.start();
        new Thread(() -> {
            String err = ladderAsegurar(false);
            SwingUtilities.invokeLater(() -> {
                tick.stop();
                ladderCargando = false;
                if (err != null) { ladderEstado.setText(t("No se pudieron cargar los ratings: ", "Couldn't load the ratings: ") + err); return; }
                ladderRefrescar();
                ladderSincronizarSeleccion();
            });
        }, "ladder-datos").start();
    }

    // =====================================================================================
    // CIV STATS — resúmenes de sfr-data (civstats/ventanas/vN.json.gz y tendencias.json.gz):
    // winrate y pick rate por civ, modo, mapa y tramo de ELO; matchups 1v1; tendencias por mes
    // datos y cálculos: sfrdata.CivStats y service.CalculoStats
    // =====================================================================================
    String statsModo = leerConfig("stats_modo", "rm_1v1"), statsVentana = leerConfig("stats_ventana", "30"), statsMapa = leerConfig("stats_mapa", "*"), statsTramo = leerConfig("stats_tramo", "*");
    JToggleButton civStatsBtn;
    JPanel civStatsPanel; JComboBox<String> stModoCombo, stVentanaCombo, stMapaCombo, stTramoCombo; JLabel stEstado;
    JPanel stTarjetas, stMasJugadas, stMejorPorMapa, stPool; JTable stTabla; DefaultTableModel stModelo; TableRowSorter<DefaultTableModel> stSorter;
    TendenciasPanel stTendencias; MatrizPanel stMatriz; JLabel stTituloTabla, stTituloMatriz;
    String stCivSeleccionada;
    final List<String> stCivsSeleccionadas = new ArrayList<>();
    JCheckBox stMasJugadasCheck; JComboBox<String> stTendVentana, stTendMapa, stTendTramo; JToggleButton stTendPick, stTendWr; boolean stRellenandoTend; String tendMapa = "*", tendTramo = "*";
    JPanel stFilaMedio, stFilaTend;
    /** Las dos filas miden lo que mide su contenido (listas enteras a la vista), en cualquier pantalla. */
    void ajustarFilasCivStats() {
        for (JPanel f : new JPanel[]{ stFilaMedio, stFilaTend }) {
            if (f == null) continue;
            f.setPreferredSize(null);
            for (Component c : f.getComponents()) { c.setPreferredSize(null); if (c instanceof JPanel jp && jp != stPool) jp.setPreferredSize(new Dimension(10, jp.getPreferredSize().height)); }
            Dimension pref = f.getPreferredSize();
            f.setPreferredSize(new Dimension(10, pref.height));
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        }
        if (stPool != null) stPool.setPreferredSize(new Dimension(10, stPool.getPreferredSize().height));
    }
    boolean stCargando, stRellenandoMapas;

    static String modoNombre(String m) {
        return switch (m) {
            case "rm_1v1" -> "1v1 Random Map"; case "rm_2v2" -> "2v2 Random Map"; case "rm_3v3" -> "3v3 Random Map"; case "rm_4v4" -> "4v4 Random Map";
            case "ew_1v1" -> "1v1 Empire Wars"; case "ew_team" -> t("Equipos Empire Wars", "Team Empire Wars");
            case "dm_1v1" -> "1v1 Deathmatch"; case "dm_team" -> t("Equipos Deathmatch", "Team Deathmatch");
            default -> m;
        };
    }
    static String ventanaNombre(String v) {
        return switch (v) {
            case "7" -> t("7 días", "7 days"); case "30" -> t("30 días", "30 days"); case "90" -> t("90 días", "90 days"); case "365" -> t("365 días", "365 days");
            case "parche" -> t("Parche actual", "Current patch"); default -> v;
        };
    }
    static String tramoNombre(String tr) {
        if (tr == null || "*".equals(tr) || "*|*".equals(tr)) return t("Todos los ELO", "All ELO");
        if ("?".equals(tr)) return t("Sin rating", "Unrated");
        if (tr.contains("|")) {   // rango «desde|hasta» (cada extremo: clave de tramo o * = sin límite)
            String[] p = tr.split("\\|", -1);
            String lo = "*".equals(p[0]) ? null : p[0].contains("-") ? p[0].substring(0, p[0].indexOf('-')) : p[0].replace("+", "");
            String hi = "*".equals(p[1]) ? null : p[1].endsWith("+") ? null : p[1].contains("-") ? p[1].substring(p[1].indexOf('-') + 1) : p[1];
            if (lo != null && lo.equals("0")) lo = null;
            if (lo == null && hi == null) return t("Todos los ELO", "All ELO");
            if (lo == null) return "<" + hi;
            if (hi == null) return lo + "+";
            return lo + "\u2013" + hi;
        }
        return tr.endsWith("+") ? tr : tr.replace("-", "\u2013");
    }
    /** Selector de ELO: presets (Todos, <800, 800–1000, …, 2000+) y «Personalizado…», que pregunta el rango «de … a …» en un diálogo y lo deja como opción activa. El valor es «*», un tramo o «desde|hasta». */
    class SelectorRangoElo extends JPanel {
        final JComboBox<String> preset = new JComboBox<>();
        List<String> tramos = List.of(); String personalizado;   // rango «desde|hasta» activo, si lo hay
        boolean rellenando; java.util.function.Consumer<String> alCambiar;
        SelectorRangoElo() {
            super(new FlowLayout(FlowLayout.LEFT, 3, 0)); setOpaque(false);
            add(preset);
            preset.setToolTipText(t("Tramo de ELO (media de ELO de la partida). «Personalizado…» pide un rango de tramo a tramo, con «sin límite» en cualquier extremo", "ELO bracket (match ELO average). \u201CCustom…\u201D asks for a range from bracket to bracket, with \u201Cno limit\u201D at either end"));
            preset.addActionListener(e -> {
                if (rellenando) return;
                int i = preset.getSelectedIndex();
                if (i == preset.getItemCount() - 1) {   // «Personalizado…»: preguntar
                    String r = pedirRango();
                    if (r == null) { rango(personalizado != null ? personalizado : "*"); return; }
                    personalizado = r; rellenar(); rango(r);
                }
                if (alCambiar != null) alCambiar.accept(rango());
            });
        }
        void tramos(List<String> t, String rangoActual) {
            tramos = new ArrayList<>(t);
            if (rangoActual != null && rangoActual.contains("|") && !"*|*".equals(rangoActual)) personalizado = rangoActual;
            rellenar();
            rango(rangoActual);
        }
        void rellenar() {
            boolean antes = rellenando; rellenando = true;
            try {
                preset.removeAllItems();
                preset.addItem(t("Todos los ELO", "All ELO"));
                for (String tr : tramos) preset.addItem(tramoNombre(tr));
                if (personalizado != null) preset.addItem(tramoNombre(personalizado) + t(" (personalizado)", " (custom)"));
                preset.addItem(t("Personalizado…", "Custom…"));
            } finally { rellenando = antes; }
        }
        String pedirRango() {
            JComboBox<String> desde = new JComboBox<>(), hasta = new JComboBox<>();
            desde.addItem(t("sin límite", "no limit")); hasta.addItem(t("sin límite", "no limit"));
            for (String tr : tramos) { String lo = tr.contains("-") ? tr.substring(0, tr.indexOf('-')) : tr.replace("+", ""), hi = tr.endsWith("+") ? tr : tr.contains("-") ? tr.substring(tr.indexOf('-') + 1) : tr; desde.addItem(lo.equals("0") ? "<" + hi : lo); hasta.addItem(hi); }
            if (personalizado != null) { String[] p = personalizado.split("\\|", -1); desde.setSelectedIndex("*".equals(p[0]) ? 0 : tramos.indexOf(p[0]) + 1); hasta.setSelectedIndex("*".equals(p[1]) ? 0 : tramos.indexOf(p[1]) + 1); }
            JPanel pnl = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            pnl.add(new JLabel(t("ELO de", "ELO from"))); pnl.add(desde); pnl.add(new JLabel(t("a", "to"))); pnl.add(hasta);
            int r = JOptionPane.showConfirmDialog(SpoilerFreeRecs.this, pnl, t("Rango de ELO personalizado", "Custom ELO range"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return null;
            int a = desde.getSelectedIndex() - 1, b = hasta.getSelectedIndex() - 1;
            if (a >= 0 && b >= 0 && a > b) { int x = a; a = b; b = x; }
            String lo = a < 0 ? "*" : tramos.get(a), hi = b < 0 ? "*" : tramos.get(b);
            return "*".equals(lo) && "*".equals(hi) ? "*" : lo.equals(hi) ? lo : lo + "|" + hi;
        }
        String rango() {
            int i = preset.getSelectedIndex(), n = preset.getItemCount();
            if (i <= 0) return "*";
            if (i <= tramos.size()) return tramos.get(i - 1);
            if (personalizado != null && i == n - 2) return personalizado;
            return personalizado != null ? personalizado : "*";
        }
        void rango(String r) {
            boolean antes = rellenando; rellenando = true;
            try {
                if (r == null || "*".equals(r) || "*|*".equals(r)) { preset.setSelectedIndex(0); return; }
                if (!r.contains("|")) { int i = tramos.indexOf(r); preset.setSelectedIndex(i >= 0 ? i + 1 : 0); return; }
                if (!r.equals(personalizado)) { personalizado = r; rellenar(); }
                preset.setSelectedIndex(preset.getItemCount() - 2);
            } finally { rellenando = antes; }
        }
    }

    /** Nombre de civ a partir de la clave del companion («burmese»): el del tech tree si está cargado, si no capitalizado. */
    static String nombreCivStats(String clave) {
        if (ttData != null) {
            for (String k : obj(ttData.get("civs")).keySet()) if (k.equalsIgnoreCase(clave)) return ttNombreCiv(k);
        }
        return clave.isEmpty() ? clave : Character.toUpperCase(clave.charAt(0)) + clave.substring(1);
    }
    /** Clave del tech tree («Burmese») para una clave del companion («burmese»), o null. */
    static String claveTechTree(String claveStats) {
        if (ttData == null) return null;
        for (String k : obj(ttData.get("civs")).keySet()) if (k.equalsIgnoreCase(claveStats)) return k;
        return null;
    }
    static String nombreMapaStats(VentanaStats v, String clave) {
        if ("*".equals(clave)) return t("Todos los mapas", "All maps");
        String n = v == null ? null : v.nombresMapas().get(clave);
        if (n == null) { n = clave; for (String p : new String[]{ "rm_", "cm_", "ew_", "dm_" }) if (n.startsWith(p)) n = n.substring(p.length()); n = n.replace('_', ' ').replace('-', ' '); }
        if (n.startsWith("Cm ")) n = n.substring(3);
        return n;
    }

    /** Color del winrate: verde si el intervalo queda por encima de 50, rojo si por debajo, gris si no se sabe. */
    /** Color de un winrate, igual en toda la app: verde de 52 % en adelante, rojo de 48 % para abajo, el resto en el color del texto. */
    static Color colorWr(int w, int n) {
        if (n <= 0) return Color.GRAY;
        double wr = 100.0 * w / n;
        if (wr >= 52) return temaOscuroActivo ? new Color(0x7c, 0xc9, 0x7f) : new Color(0x2e, 0x7d, 0x32);
        if (wr <= 48) return temaOscuroActivo ? new Color(0xe5, 0x73, 0x73) : new Color(0xc6, 0x28, 0x28);
        Color fg = UIManager.getColor("Label.foreground");
        return fg == null ? Color.GRAY : fg;
    }
    // ----- Panel «Civ Stats» -----
    JPanel construirPanelCivStats() {
        civStatsPanel = new JPanel(new BorderLayout(8, 6));
        civStatsPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel norte = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        stModoCombo = new JComboBox<>();
        for (String m : MODOS_STATS) stModoCombo.addItem(modoNombre(m));
        stModoCombo.setSelectedIndex(Math.max(0, Arrays.asList(MODOS_STATS).indexOf(statsModo)));
        stModoCombo.addActionListener(e -> { statsModo = MODOS_STATS[Math.max(0, stModoCombo.getSelectedIndex())]; guardarConfig("stats_modo", statsModo); stCivSeleccionada = null; statsFiltrosCambiados(true); });
        norte.add(stModoCombo);
        stVentanaCombo = new JComboBox<>();
        for (String v : VENTANAS_STATS_KEYS) stVentanaCombo.addItem(ventanaNombre(v));
        stVentanaCombo.setSelectedIndex(Math.max(0, Arrays.asList(VENTANAS_STATS_KEYS).indexOf(statsVentana)));
        stVentanaCombo.addActionListener(e -> { if (stRellenandoMapas) return; statsVentana = VENTANAS_STATS_KEYS[Math.max(0, stVentanaCombo.getSelectedIndex())]; guardarConfig("stats_ventana", statsVentana); abrirCivStats(); });
        norte.add(stVentanaCombo);
        stMapaCombo = new JComboBox<>();
        stMapaCombo.setPrototypeDisplayValue("African Clearing (99.999)   ");
        stMapaCombo.addActionListener(e -> { if (stRellenandoMapas) return; Object id = stMapaCombo.getClientProperty("claves"); if (id instanceof List<?> l && stMapaCombo.getSelectedIndex() >= 0 && stMapaCombo.getSelectedIndex() < l.size()) { statsMapa = String.valueOf(l.get(stMapaCombo.getSelectedIndex())); guardarConfig("stats_mapa", statsMapa); statsFiltrosCambiados(true); } });
        norte.add(stMapaCombo);
        stRango = new SelectorRangoElo();
        stRango.alCambiar = r -> { if (stRellenandoMapas) return; statsTramo = r; guardarConfig("stats_tramo", statsTramo); statsFiltrosCambiados(true); };
        norte.add(stRango);
        stEstado = new JLabel();
        stEstado.setFont(stEstado.getFont().deriveFont(Font.PLAIN, 11f));
        norte.add(stEstado);
        civStatsPanel.add(norte, BorderLayout.NORTH);

        PanelScrollable cuerpo = new PanelScrollable();
        cuerpo.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 4));
        stTarjetas = new JPanel(new GridLayout(1, 5, 8, 0));
        stTarjetas.setAlignmentX(0f);
        stTarjetas.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        cuerpo.add(stTarjetas);
        cuerpo.add(Box.createVerticalStrut(8));

        JPanel medio = new JPanel(new GridBagLayout());
        medio.setAlignmentX(0f);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH; gc.gridy = 0; gc.insets = new Insets(0, 0, 0, 12);
        JPanel izq = new JPanel(new BorderLayout(0, 4));
        stTituloTabla = tituloSeccion(t("Winrate por civilización", "Win rate by civilization"), t("Porcentaje de partidas ganadas por cada civ con los filtros elegidos. La barra sale del 50 % hacia la derecha (gana más de lo que pierde) o hacia la izquierda; la banda gris es el intervalo de confianza al 95 %: si no cruza el 50 %, la diferencia es real. Pick: cuánto se elige esa civ. Clic en una fila para verla en tendencias y matchups.",
                "Share of games won by each civ with the chosen filters. The bar grows from 50% to the right (wins more than it loses) or to the left; the grey band is the 95% confidence interval: if it doesn't cross 50%, the difference is real. Pick: how often that civ is chosen. Click a row to highlight it in trends and matchups."));
        izq.add(stTituloTabla, BorderLayout.NORTH);
        stModelo = new DefaultTableModel(new Object[]{ t("Civ", "Civ"), "WR", t("Pick", "Pick"), t("Partidas", "Games"), t("Duración", "Length") }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? String.class : c == 3 ? Integer.class : c == 4 ? String.class : Double.class; }
        };
        stTabla = new JTable(stModelo);
        stTabla.setRowHeight(22);
        stTabla.setAutoCreateColumnsFromModel(true);
        stSorter = new TableRowSorter<>(stModelo);
        stTabla.setRowSorter(stSorter);
        stSorter.setSortKeys(List.of(new RowSorter.SortKey(1, SortOrder.DESCENDING)));
        stTabla.getColumnModel().getColumn(0).setPreferredWidth(150);
        stTabla.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel lab = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, col);
                int m = tb.convertRowIndexToModel(row);
                Object k = stTabla.getClientProperty("civ" + m);
                lab.setIcon(k == null ? null : iconoCiv(String.valueOf(k), 18));
                lab.setIconTextGap(6);
                Object n = stModelo.getValueAt(m, 3);
                boolean poca = n instanceof Integer i && i < MUESTRA_FIABLE;
                lab.setForeground(sel ? lab.getForeground() : poca ? Color.GRAY : UIManager.getColor("Table.foreground"));
                lab.setToolTipText(poca ? t("Menos de 100 partidas: orientativo", "Under 100 games: indicative only") : null);
                return lab;
            }
        });
        stTabla.getColumnModel().getColumn(1).setPreferredWidth(240);
        stTabla.getColumnModel().getColumn(1).setCellRenderer(new BarraWrRenderer());
        stTabla.getColumnModel().getColumn(2).setCellRenderer(new PctRenderer());
        stTabla.getColumnModel().getColumn(3).setCellRenderer(new MilesRenderer());
        stTabla.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        stTabla.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int r = stTabla.getSelectedRow();
            stCivSeleccionada = r < 0 ? null : (String) stTabla.getClientProperty("civ" + stTabla.convertRowIndexToModel(r));
            stCivsSeleccionadas.clear();
            for (int vr : stTabla.getSelectedRows()) { Object k = stTabla.getClientProperty("civ" + stTabla.convertRowIndexToModel(vr)); if (k != null) stCivsSeleccionadas.add(String.valueOf(k)); }
            if (!stCivsSeleccionadas.isEmpty() && stMasJugadasCheck != null && stMasJugadasCheck.isSelected()) stMasJugadasCheck.setSelected(false);   // seleccionas → solo las tuyas
            if (stTendencias != null) stTendencias.repaint();
            if (stMatriz != null) stMatriz.repaint();
        });
        stTabla.addMouseListener(new MouseAdapter() {   // doble clic o clic derecho en una civ → su tech tree
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                int vr = stTabla.rowAtPoint(e.getPoint());
                if (vr < 0) return;
                Object k = stTabla.getClientProperty("civ" + stTabla.convertRowIndexToModel(vr));
                if (k != null) abrirTechTree(String.valueOf(k));
            }
            @Override public void mousePressed(MouseEvent e) { menu(e); }
            @Override public void mouseReleased(MouseEvent e) { menu(e); }
            void menu(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int vr = stTabla.rowAtPoint(e.getPoint());
                if (vr < 0) return;
                Object k = stTabla.getClientProperty("civ" + stTabla.convertRowIndexToModel(vr));
                if (k == null) return;
                JPopupMenu pm = new JPopupMenu();
                JMenuItem tt = new JMenuItem(t("Ver en el tech tree: ", "See in the tech tree: ") + nombreCivStats(String.valueOf(k)));
                tt.addActionListener(a -> abrirTechTree(String.valueOf(k)));
                pm.add(tt);
                pm.show(stTabla, e.getX(), e.getY());
            }
        });
        stTabla.getTableHeader().setToolTipText(t("Doble clic o clic derecho en una civ: su tech tree. Clic: seleccionarla para tendencias y matchups (Ctrl para varias).", "Double-click or right-click a civ: its tech tree. Click: select it for trends and matchups (Ctrl for several)."));
        JScrollPane sp = new JScrollPane(stTabla);
        sp.setPreferredSize(new Dimension(10, 470));
        izq.add(sp, BorderLayout.CENTER);
        izq.setPreferredSize(new Dimension(10, 500)); izq.setMinimumSize(new Dimension(10, 100));   // ancho base 10 en los cuatro bloques: solo el reparto 0,6/0,4 decide, en cualquier pantalla
        gc.gridx = 0; gc.weightx = 0.6; gc.weighty = 1;
        medio.add(izq, gc);
        PanelScrollable der = new PanelScrollable();
        stMasJugadas = new JPanel(); stMasJugadas.setLayout(new BoxLayout(stMasJugadas, BoxLayout.Y_AXIS)); stMasJugadas.setAlignmentX(0f);
        stMejorPorMapa = new JPanel(); stMejorPorMapa.setLayout(new BoxLayout(stMejorPorMapa, BoxLayout.Y_AXIS)); stMejorPorMapa.setAlignmentX(0f);
        stPool = new PanelScrollable();
        for (JPanel p : new JPanel[]{ stMasJugadas, stMejorPorMapa }) { der.add(p); der.add(Box.createVerticalStrut(6)); }
        gc.gridx = 1; gc.weightx = 0.40; gc.insets = new Insets(0, 0, 0, 0);
        der.setMinimumSize(new Dimension(10, 100)); der.setPreferredSize(new Dimension(10, 100));   // sin scroll propio: la fila crece con el contenido y todo queda a la vista
        medio.add(der, gc);
        stFilaMedio = medio;
        cuerpo.add(medio);
        cuerpo.add(Box.createVerticalStrut(10));

        stTendencias = new TendenciasPanel();
        JPanel filaTend = new JPanel(new GridBagLayout());
        filaTend.setAlignmentX(0f);
        filaTend.setPreferredSize(new Dimension(10, 360));
        GridBagConstraints gt = new GridBagConstraints();
        gt.fill = GridBagConstraints.BOTH; gt.gridy = 0; gt.weighty = 1;
        JPanel tendCaja = new JPanel(new BorderLayout(0, 2));
        tendCaja.setPreferredSize(new Dimension(10, 360)); tendCaja.setMinimumSize(new Dimension(10, 100));
        JPanel tendBarra = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 1));
        stMasJugadasCheck = new JCheckBox(t("Las 5 más jugadas", "5 most played"), true);
        stMasJugadasCheck.setFocusable(false);
        stMasJugadasCheck.setToolTipText(t("Marcado: las cinco civs más jugadas. Sin marcar: solo las civs que selecciones en la tabla (Ctrl o Shift para varias).", "Ticked: the five most played civs. Unticked: only the civs you select in the table (Ctrl or Shift for several)."));
        stMasJugadasCheck.addActionListener(e -> stTendencias.repaint());
        tendBarra.add(stMasJugadasCheck);
        JButton vaciar = new JButton(t("Vaciar", "Clear"));
        vaciar.setFocusable(false); vaciar.setMargin(new Insets(0, 6, 0, 6)); vaciar.putClientProperty("JButton.buttonType", "roundRect");
        vaciar.addActionListener(e -> { stTabla.clearSelection(); stCivsSeleccionadas.clear(); stMasJugadasCheck.setSelected(false); stTendencias.repaint(); });
        tendBarra.add(vaciar);
        stTendMapa = new JComboBox<>();
        stTendMapa.setToolTipText(t("Mapa de la tendencia: los 12 más jugados de cada modo (el resto no tiene serie por mapa)", "Map for the trend: the 12 most played of each mode (others have no per-map series)"));
        stTendMapa.addActionListener(e -> { if (stRellenandoTend) return; Object cl = stTendMapa.getClientProperty("claves"); if (cl instanceof List<?> l && stTendMapa.getSelectedIndex() >= 0 && stTendMapa.getSelectedIndex() < l.size()) { tendMapa = String.valueOf(l.get(stTendMapa.getSelectedIndex())); stTendencias.repaint(); } });
        tendBarra.add(new JLabel(t("Mapa:", "Map:"))); tendBarra.add(stTendMapa);
        tendRango = new SelectorRangoElo();
        tendRango.alCambiar = r -> { if (stRellenandoTend) return; tendTramo = r; totalesTendencia.clear(); stTendencias.repaint(); };
        tendBarra.add(tendRango);
        stTendWr = new JToggleButton(t("Winrate", "Win rate"), true);
        stTendPick = new JToggleButton(t("Pick rate", "Pick rate"), false);
        ButtonGroup gTend = new ButtonGroup(); gTend.add(stTendWr); gTend.add(stTendPick);
        for (JToggleButton tb : new JToggleButton[]{ stTendWr, stTendPick }) { tb.setFocusable(false); tb.setMargin(new Insets(0, 8, 0, 8)); tb.putClientProperty("JButton.buttonType", "roundRect"); tb.addActionListener(e -> stTendencias.repaint()); }
        stTendWr.setToolTipText(t("La gráfica muestra el winrate de cada civ", "The chart shows each civ's win rate"));
        stTendPick.setToolTipText(t("La gráfica muestra cuánto se juega cada civ (pick rate)", "The chart shows how much each civ is played (pick rate)"));
        JPanel conmut = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); conmut.setOpaque(false); conmut.add(stTendWr); conmut.add(stTendPick);
        tendBarra.add(conmut);
        stTendVentana = new JComboBox<>(new String[]{ t("3 meses", "3 months"), t("6 meses", "6 months"), t("12 meses", "12 months"), t("Parche actual", "Current patch") });
        stTendVentana.setSelectedIndex(Math.max(0, Math.min(3, Integer.parseInt(leerConfig("stats_tend_ventana", "2")))));
        stTendVentana.addActionListener(e -> { guardarConfig("stats_tend_ventana", String.valueOf(stTendVentana.getSelectedIndex())); if (stTendVentana.getSelectedIndex() == 3 && !VENTANAS_STATS.containsKey("parche")) new Thread(() -> { statsAsegurar("parche", false); SwingUtilities.invokeLater(stTendencias::repaint); }, "civstats-parche").start(); else stTendencias.repaint(); });
        tendBarra.add(stTendVentana);
        tendCaja.add(tendBarra, BorderLayout.NORTH);
        tendCaja.add(stTendencias, BorderLayout.CENTER);
        gt.gridx = 0; gt.weightx = 0.6; gt.insets = new Insets(0, 0, 0, 12);
        filaTend.add(tendCaja, gt);
        stPool.setPreferredSize(new Dimension(10, 100)); stPool.setMinimumSize(new Dimension(10, 100));
        gt.gridx = 1; gt.weightx = 0.4; gt.insets = new Insets(0, 0, 0, 0);
        filaTend.add(stPool, gt);
        stFilaTend = filaTend;
        cuerpo.add(filaTend);
        cuerpo.add(Box.createVerticalStrut(10));
        stTituloMatriz = tituloSeccion(t("Matchups (civ de la fila contra civ de la columna)", "Matchups (row civ against column civ)"),
                t("Solo en modos 1v1. Cada celda es el winrate de la civ de la fila cuando se enfrenta a la de la columna: verde si gana, rojo si pierde; más intenso cuanto más lejos del 50 %. Gris: menos de 20 partidas. Pasa el ratón para ver la cifra y clica el nombre de una fila para seleccionar esa civ.",
                "1v1 modes only. Each cell is the row civ's win rate when facing the column civ: green if it wins, red if it loses; stronger the further from 50%. Grey: fewer than 20 games. Hover for the figure and click a row name to select that civ."));
        stTituloMatriz.setAlignmentX(0f);
        JPanel filaMatriz = new JPanel(new BorderLayout(8, 0));
        filaMatriz.setAlignmentX(0f); filaMatriz.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        filaMatriz.add(stTituloMatriz, BorderLayout.WEST);
        JPanel botonesMatriz = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (String[] z : new String[][]{ { "\u2212", "menos" }, { "+", "mas" }, { "\u26F6 " + t("Ampliar", "Enlarge"), "grande" } }) {
            JButton zb = new JButton(z[0]);
            zb.setFocusable(false); zb.setMargin(new Insets(0, 6, 0, 6)); zb.putClientProperty("JButton.buttonType", "roundRect");
            zb.setToolTipText(z[1].equals("grande") ? t("La matriz en una ventana a pantalla completa (Esc para cerrar)", "The matrix in a full-screen window (Esc to close)") : t("Tamaño de las celdas (también Ctrl + rueda sobre la matriz)", "Cell size (also Ctrl + wheel over the matrix)"));
            zb.addActionListener(e -> { switch (z[1]) { case "menos" -> stMatriz.setCelda(stMatriz.celda - 2); case "mas" -> stMatriz.setCelda(stMatriz.celda + 2); default -> mostrarMatrizGrande(); } });
            botonesMatriz.add(zb);
        }
        filaMatriz.add(botonesMatriz, BorderLayout.CENTER);
        cuerpo.add(filaMatriz);
        stMatriz = new MatrizPanel();
        stMatriz.setAlignmentX(0f);
        cuerpo.add(stMatriz);

        JScrollPane scroll = new JScrollPane(cuerpo, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        civStatsPanel.add(scroll, BorderLayout.CENTER);
        JLabel pie = new JLabel(t("Datos: volcados diarios de aoe2companion.com (Dennis Keil), resumidos por sfr-data cada noche · Age of Empires II © Microsoft. Fuera espejos, partidas de menos de 2 minutos y sin resultado; el tramo de ELO es la media de la partida; las bandas son intervalos de Wilson al 95 %.",
                "Data: aoe2companion.com daily dumps (Dennis Keil), summarized nightly by sfr-data · Age of Empires II © Microsoft. Mirrors, games under 2 minutes and unfinished games excluded; the ELO bracket is the match average; bands are 95% Wilson intervals."));
        pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f));
        civStatsPanel.add(pie, BorderLayout.SOUTH);
        return civStatsPanel;
    }

    static JLabel tituloSeccion(String texto) { return tituloSeccion(texto, null); }
    /** Gris «secundario» legible en los dos temas: claro en oscuro, oscuro en claro (títulos de recuadro, leyendas, subtítulos). */
    static Color colorSecundario() { return temaOscuroActivo ? new Color(0x9a, 0x9a, 0x9a) : new Color(0x5a, 0x5a, 0x5a); }
    static String colorSecundarioHex() { return colorHex(colorSecundario()); }
    static JLabel tituloSeccion(String texto, String tooltip) {
        JLabel l = new JLabel(tooltip == null ? texto : texto + "  \u24D8");
        l.setAlignmentX(0f);
        if (tooltip != null) l.setToolTipText("<html><div style='width:320px'>" + escapeHtml(tooltip) + "</div></html>");
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        l.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        return l;
    }

    JPanel tarjeta(String titulo, String valor, String pie) {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(128, 128, 128, 70), 1, true), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JLabel t1 = new JLabel(titulo.toUpperCase(Locale.ROOT)); t1.setFont(t1.getFont().deriveFont(Font.BOLD, 10.5f)); t1.setForeground(colorSecundario());
        JLabel v = new JLabel(valor); v.setFont(v.getFont().deriveFont(Font.BOLD, 18f));
        JLabel t2 = new JLabel(pie); t2.setFont(t2.getFont().deriveFont(Font.PLAIN, 10.5f)); t2.setForeground(colorSecundario());
        p.add(t1, BorderLayout.NORTH); p.add(v, BorderLayout.CENTER); p.add(t2, BorderLayout.SOUTH);
        return p;
    }

    /** Una fila «nombre ····· barra ····· valor» para las listas laterales. */
    JPanel filaBarra(String nombre, double fraccion, String valor, Color color, String tooltip) { return filaBarra(nombre, fraccion, valor, color, tooltip, null); }

    JPanel filaBarra(String nombre, double fraccion, String valor, Color color, String tooltip, Runnable alClicar) { return filaBarra(null, nombre, fraccion, valor, color, tooltip, alClicar); }

    boolean esFilaJugador;   // la fila que se está construyendo es de un jugador (rival/aliado): clic derecho ofrece pestaña nueva
    JPanel filaBarra(Icon icono, String nombre, double fraccion, String valor, Color color, String tooltip, Runnable alClicar) {
        JPanel f = new JPanel(new BorderLayout(6, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int x0 = 130, x1 = getWidth() - 62;
                if (x1 <= x0) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(128, 128, 128, 40));
                g2.fillRoundRect(x0, getHeight() / 2 - 4, x1 - x0, 8, 6, 6);
                g2.setColor(color);
                g2.fillRoundRect(x0, getHeight() / 2 - 4, (int) ((x1 - x0) * Math.max(0, Math.min(1, fraccion))), 8, 6, 6);
                g2.dispose();
            }
        };
        f.setOpaque(false);
        f.setAlignmentX(0f);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        f.setPreferredSize(new Dimension(10, 22));
        JLabel n = new JLabel(nombre, icono, SwingConstants.LEFT); n.setIconTextGap(5); n.setPreferredSize(new Dimension(126, 20));
        JLabel v = new JLabel(valor, SwingConstants.RIGHT); v.setPreferredSize(new Dimension(58, 18)); v.setFont(v.getFont().deriveFont(Font.BOLD));
        f.add(n, BorderLayout.WEST); f.add(v, BorderLayout.EAST);
        if (tooltip != null) { f.setToolTipText(tooltip); n.setToolTipText(tooltip); v.setToolTipText(tooltip); }
        if (alClicar != null) {
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) { ultimoClicCtrl = e.isControlDown() || SwingUtilities.isMiddleMouseButton(e); alClicar.run(); ultimoClicCtrl = false; }   // botón central = pestaña nueva
                }
                @Override public void mousePressed(MouseEvent e) { menu(e); }
                @Override public void mouseReleased(MouseEvent e) { menu(e); }
                void menu(MouseEvent e) {
                    if (!e.isPopupTrigger() || !esFilaJugador) return;
                    JPopupMenu pm = new JPopupMenu();
                    JMenuItem it = new JMenuItem(t("Abrir perfil en pestaña nueva", "Open profile in a new tab"));
                    it.addActionListener(a -> { ultimoClicCtrl = true; alClicar.run(); ultimoClicCtrl = false; });
                    pm.add(it);
                    pm.show(e.getComponent(), e.getX(), e.getY());
                }
            };
            for (JComponent c : new JComponent[]{ f, n, v }) { c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); c.addMouseListener(ma); }
            n.setForeground(temaOscuroActivo ? new Color(0x7f, 0xb3, 0xe0) : new Color(0x2f, 0x5f, 0x8f));
        }
        return f;
    }

    /** Fila «Arabia · [icono] Romanos · 55,1 %» para «Mejor civ por mapa». */
    JPanel filaMejorCiv(String mapa, String claveMapa, CivAgg mejor, int partidasMapa) {
        JPanel f = new JPanel(new BorderLayout(6, 0));
        f.setOpaque(false); f.setAlignmentX(0f);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22)); f.setPreferredSize(new Dimension(10, 22));
        JLabel m = new JLabel(mapa, iconoMapa(mapa, claveMapa, 18), SwingConstants.LEFT); m.setIconTextGap(5); m.setPreferredSize(new Dimension(126, 20));
        JLabel c = new JLabel("<html>" + escapeHtml(nombreCivStats(mejor.civ())) + " <span style='color:gray;font-size:9px'>" + miles(mejor.n()) + "</span></html>", iconoCiv(mejor.civ(), 18), SwingConstants.LEFT); c.setIconTextGap(5);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) abrirTechTree(mejor.civ()); } });
        JLabel v = new JLabel(pct1(mejor.wr()), SwingConstants.RIGHT); v.setPreferredSize(new Dimension(58, 18)); v.setFont(v.getFont().deriveFont(Font.BOLD)); v.setForeground(colorWr(mejor.w(), mejor.n()));
        f.add(m, BorderLayout.WEST); f.add(c, BorderLayout.CENTER); f.add(v, BorderLayout.EAST);
        String tip = mapa + ": " + miles(partidasMapa) + t(" partidas · ", " games · ") + nombreCivStats(mejor.civ()) + " " + pct1(mejor.wr()) + " (" + miles(mejor.n()) + t(" partidas)", " games)");
        for (JComponent x : new JComponent[]{ f, m, c, v }) x.setToolTipText(tip);
        return f;
    }

    class BarraWrRenderer extends DefaultTableCellRenderer {
        double wr; int n, w;
        @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, "", sel, foc, row, col);
            int m = t.convertRowIndexToModel(row);
            Object oN = stModelo.getValueAt(m, 3);
            n = oN instanceof Integer i ? i : 0;
            wr = value instanceof Double d ? d : 0;
            w = (int) Math.round(wr * n / 100.0);
            return this;
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight(), wd = getWidth(), tx = 52, x0 = tx + 4, x1 = wd - 6;
            double lo = 42, hi = 58;
            java.util.function.DoubleUnaryOperator px = v -> x0 + (x1 - x0) * (Math.max(lo, Math.min(hi, v)) - lo) / (hi - lo);
            double[] iv = wilson(w, n);
            g2.setColor(new Color(128, 128, 128, 45));
            g2.fillRoundRect((int) px.applyAsDouble(iv[0]), h / 2 - 5, Math.max(2, (int) (px.applyAsDouble(iv[1]) - px.applyAsDouble(iv[0]))), 10, 4, 4);
            Color c = colorWr(w, n);
            g2.setColor(c);
            int xm = (int) px.applyAsDouble(50), xv = (int) px.applyAsDouble(wr);
            g2.fillRect(Math.min(xm, xv), h / 2 - 3, Math.max(2, Math.abs(xv - xm)), 6);
            g2.setColor(new Color(128, 128, 128, 120));
            g2.drawLine(xm, 3, xm, h - 3);
            g2.setColor(c);
            g2.setFont(getFont().deriveFont(Font.BOLD));
            String s = pct1(wr);
            g2.drawString(s, tx - g2.getFontMetrics().stringWidth(s), h / 2 + 4);
            g2.dispose();
        }
    }
    static class MilesRenderer extends DefaultTableCellRenderer {
        MilesRenderer() { setHorizontalAlignment(RIGHT); }
        @Override protected void setValue(Object v) { setText(v instanceof Integer i ? miles(i) : ""); }
    }

    void abrirCivStats() {
        registrarDestino(new Destino("civstats", 0, null, null));
        if (civStatsBtn != null && !civStatsBtn.isSelected()) civStatsBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (ladderBtn != null) ladderBtn.setSelected(false);
        actividadAbierta = false;
        if (perfilBtn != null) perfilBtn.setSelected(false);
        ahoraAbierta = false; if (ahoraBtn != null) ahoraBtn.setSelected(false);
        if (techTreeBtn != null && techTreeBtn.isSelected()) cerrarTechTree();
        ((CardLayout) centroCards.getLayout()).show(centroCards, "civstats");
        subirArriba(civStatsPanel);
        taparResultados(); apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        if (stCargando) return;
        stCargando = true;
        String ventana = statsVentana;
        stEstado.setText(t("Descargando el resumen de ", "Downloading the summary for ") + ventanaNombre(ventana).toLowerCase(Locale.ROOT) + "…");
        new Thread(() -> {
            String err = statsAsegurar(ventana, true);
            SwingUtilities.invokeLater(() -> {
                stCargando = false;
                if (err != null) { stEstado.setText(t("No se pudieron cargar las estadísticas: ", "Couldn't load the statistics: ") + err); return; }
                statsFiltrosCambiados(true);
            });
        }, "civstats-datos").start();
    }

    /** Rellena mapas y tramos disponibles para el modo actual y repinta todo el cuadro de mando. */
    void statsFiltrosCambiados(boolean repintarTechTree) {
        VentanaStats v = VENTANAS_STATS.get(statsVentana);
        if (v == null || civStatsPanel == null) return;
        stRellenandoMapas = true;
        try {
            Map<String, Integer> mapas = partidasPorMapa(v, statsModo, statsTramo);   // el contador es el del tramo elegido
            List<Map.Entry<String, Integer>> lm = new ArrayList<>(mapas.entrySet());
            lm.sort((a, b) -> b.getValue() - a.getValue());
            List<String> claves = new ArrayList<>(); claves.add("*");
            stMapaCombo.removeAllItems();
            stMapaCombo.addItem(t("Todos los mapas", "All maps"));
            for (Map.Entry<String, Integer> en : lm) { if (en.getValue() < MIN_PARTIDAS_CIV) continue; claves.add(en.getKey()); stMapaCombo.addItem(nombreMapaStats(v, en.getKey()) + " (" + miles(en.getValue()) + ")"); }
            stMapaCombo.putClientProperty("claves", claves);
            if (!claves.contains(statsMapa)) { statsMapa = "*"; guardarConfig("stats_mapa", "*"); }
            if (stTendMapa != null) {   // la fila de tendencias tiene sus propios mapa y ELO
                stRellenandoTend = true;
                try {
                    List<String> clTM = new ArrayList<>(); clTM.add("*");
                    stTendMapa.removeAllItems(); stTendMapa.addItem(t("Todos", "All"));
                    List<Map.Entry<String, Integer>> lmT = new ArrayList<>(v.mapasPorModo().getOrDefault(statsModo, Map.of()).entrySet());
                    lmT.sort((a, b) -> b.getValue() - a.getValue());
                    for (Map.Entry<String, Integer> en : lmT.subList(0, Math.min(12, lmT.size()))) { clTM.add(en.getKey()); stTendMapa.addItem(nombreMapaStats(v, en.getKey())); }
                    stTendMapa.putClientProperty("claves", clTM);
                    if (!clTM.contains(tendMapa)) tendMapa = "*";
                    stTendMapa.setSelectedIndex(clTM.indexOf(tendMapa));
                    tendRango.tramos(v.tramos(), tendTramo);
                    tendTramo = tendRango.rango();
                    totalesTendencia.clear();
                } finally { stRellenandoTend = false; }
            }
            stMapaCombo.setSelectedIndex(claves.indexOf(statsMapa));
            stRango.tramos(v.tramos(), statsTramo);
            statsTramo = stRango.rango();
        } finally { stRellenandoMapas = false; }
        statsPintar(v);
        if (repintarTechTree && techTreePanel != null) ttActualizarWr();
    }

    void statsPintar(VentanaStats v) {
        Map<String, CivAgg> agg = agregarCivs(v, statsModo, statsMapa, statsTramo);
        long totalN = 0, totalD = 0; int totalW = 0;
        for (CivAgg a : agg.values()) { totalN += a.n(); totalD += a.d(); totalW += a.w(); }
        Map<String, Integer> modo = v.modos().getOrDefault(statsModo, Map.of());
        int jugadoresPorPartida = statsModo.endsWith("_1v1") ? 2 : statsModo.endsWith("_2v2") ? 4 : statsModo.endsWith("_3v3") ? 6 : statsModo.endsWith("_4v4") ? 8 : 6;
        long partidas = "*".equals(statsMapa) && ("*".equals(statsTramo) || "*|*".equals(statsTramo)) ? modo.getOrDefault("partidas", 0) : totalN / jugadoresPorPartida;
        int abandonos = modo.getOrDefault("abandonos", 0), espejos = modo.getOrDefault("espejos", 0), sinRes = modo.getOrDefault("sin_resultado", 0);
        int brutas = modo.getOrDefault("partidas", 0) + abandonos + espejos + sinRes;
        String ambito = ventanaNombre(statsVentana) + " · " + v.desde() + " → " + v.hasta() + (v.parche().isEmpty() ? "" : " · " + t("parche ", "patch ") + v.parche());
        stTarjetas.removeAll();
        stTarjetas.add(tarjeta(t("Partidas", "Games"), miles(partidas), ambito));
        stTarjetas.add(tarjeta(t("Duración media", "Avg. length"), duracionMedia(totalD, (int) Math.min(Integer.MAX_VALUE, totalN)), t("tiempo de juego, sin abandonos", "in-game time, dodges excluded")));
        stTarjetas.add(tarjeta(t("Civs con datos", "Civs with data"), String.valueOf(agg.values().stream().filter(a -> a.n() >= MIN_PARTIDAS_CIV).count()), t("con ", "with ") + MIN_PARTIDAS_CIV + t("+ partidas (gris: menos de 100)", "+ games (grey: under 100)")));
        stTarjetas.add(tarjeta(t("Espejos", "Mirrors"), brutas == 0 ? "-" : pct1(100.0 * espejos / brutas), t("misma civ en ambos lados (fuera)", "same civ on both sides (excluded)")));
        stTarjetas.add(tarjeta(t("Abandonos", "Dodges"), brutas == 0 ? "-" : pct1(100.0 * abandonos / brutas), t("menos de 2 minutos (fuera)", "under 2 minutes (excluded)")));
        stTarjetas.revalidate(); stTarjetas.repaint();

        List<CivAgg> lista = new ArrayList<>(agg.values());
        lista.removeIf(a -> a.n() < MIN_PARTIDAS_CIV);
        stModelo.setRowCount(0);
        int i = 0;
        for (CivAgg a : lista) {
            stModelo.addRow(new Object[]{ nombreCivStats(a.civ()), a.wr(), totalN == 0 ? 0.0 : 100.0 * a.n() / totalN, a.n(), duracionMedia(a.d(), a.n()) });
            stTabla.putClientProperty("civ" + i, a.civ());
            i++;
        }
        stTituloTabla.setText(t("Winrate por civilización", "Win rate by civilization") + "  ·  " + modoNombre(statsModo) + " · " + nombreMapaStats(v, statsMapa) + " · " + tramoNombre(statsTramo));
        if (stCivSeleccionada != null) {
            for (int r = 0; r < stModelo.getRowCount(); r++) if (stCivSeleccionada.equals(stTabla.getClientProperty("civ" + r))) { int vr = stTabla.convertRowIndexToView(r); stTabla.setRowSelectionInterval(vr, vr); break; }
        }
        Color barra = temaOscuroActivo ? new Color(0x5a, 0x8f, 0xc7) : new Color(0x3b, 0x6e, 0xa8);
        stMasJugadas.removeAll();
        stMasJugadas.add(tituloSeccion(t("Más jugadas", "Most played"), t("Las civs más elegidas con estos filtros: porcentaje de todas las partidas en las que aparece cada una (pick rate).", "The most chosen civs with these filters: share of all games in which each one appears (pick rate).")));
        List<CivAgg> porPick = new ArrayList<>(lista); porPick.sort((a, b) -> b.n() - a.n());
        double maxPick = porPick.isEmpty() ? 1 : porPick.get(0).n();
        for (CivAgg a : porPick.subList(0, Math.min(8, porPick.size())))
            stMasJugadas.add(filaBarra(iconoCiv(a.civ(), 18), nombreCivStats(a.civ()), a.n() / maxPick, pct1(totalN == 0 ? 0 : 100.0 * a.n() / totalN), barra, miles(a.n()) + t(" partidas · WR ", " games · WR ") + pct1(a.wr()) + t(" · clic: tech tree", " · click: tech tree"), () -> abrirTechTree(a.civ())));
        if (porPick.size() > 8) { long totalNF = totalN; stMasJugadas.add(enlaceVerTodo(porPick.size(), () -> {
            List<Object[]> filas = new ArrayList<>();
            for (CivAgg a : porPick) filas.add(new Object[]{ new Celda(iconoCiv(a.civ(), 18), nombreCivStats(a.civ()), () -> abrirTechTree(a.civ())), new Pct(totalNF == 0 ? 0 : 100.0 * a.n() / totalNF, 0, 0, false), (long) a.n(), new Pct(a.wr(), a.w(), a.n(), true) });
            mostrarTablaCompleta(t("Más jugadas", "Most played") + " · " + modoNombre(statsModo), new String[]{ t("Civ", "Civ"), "Pick", t("Partidas", "Games"), "WR" }, filas, 1);
        })); }
        stMejorPorMapa.removeAll();
        stMejorPorMapa.add(tituloSeccion(t("Mejor civ por mapa", "Best civ per map"), t("En cada mapa del pool, la civ con mejor winrate con el modo y el tramo elegidos: mínimo 20 partidas de esa civ en ese mapa, y gana la que tiene mejor límite inferior del intervalo de confianza (no la muestra pequeña con suerte). El número gris son sus partidas: por debajo de 100, orientativo. Responde a «qué me cojo en este mapa».", "For each map in the pool, the civ with the best win rate with the chosen mode and bracket: at least 20 games of that civ on that map, and the winner is the best lower bound of the confidence interval (not a lucky small sample). The grey number is its games: under 100, indicative only. Answers \u201Cwhat should I pick on this map\u201D.")));
        Map<String, Integer> mapas = partidasPorMapa(v, statsModo, statsTramo);   // con tramo elegido, solo las partidas de ese tramo
        List<Map.Entry<String, Integer>> lm = new ArrayList<>(mapas.entrySet());
        lm.sort((a, b) -> b.getValue() - a.getValue());
        int filasMejor = 0;
        List<Object[]> todasMejor = new ArrayList<>();   // {clave mapa, mejor, partidas del mapa}
        for (Map.Entry<String, Integer> en : lm) {
            if (en.getValue() < MIN_PARTIDAS_CIV) break;
            CivAgg mejor = null; double mejorLb = -1;
            for (CivAgg a : agregarCivs(v, statsModo, en.getKey(), statsTramo).values()) {   // 20+ partidas; gana el límite inferior de Wilson (no la muestra pequeña con suerte)
                if (a.n() < MIN_PARTIDAS_CIV) continue;
                double lb = wilson(a.w(), a.n())[0];
                if (lb > mejorLb) { mejorLb = lb; mejor = a; }
            }
            if (mejor == null) continue;
            todasMejor.add(new Object[]{ en.getKey(), mejor, en.getValue() });
            if (filasMejor < 12) { stMejorPorMapa.add(filaMejorCiv(nombreMapaStats(v, en.getKey()), en.getKey(), mejor, en.getValue())); filasMejor++; }
        }
        if (todasMejor.size() > 12) stMejorPorMapa.add(enlaceVerTodo(todasMejor.size(), () -> {
            List<Object[]> filas = new ArrayList<>();
            for (Object[] x : todasMejor) { CivAgg mejor = (CivAgg) x[1]; filas.add(new Object[]{ new Celda(iconoMapa(nombreMapaStats(v, (String) x[0]), (String) x[0], 18), nombreMapaStats(v, (String) x[0]), null), new Celda(iconoCiv(mejor.civ(), 18), nombreCivStats(mejor.civ()), () -> abrirTechTree(mejor.civ())), (long) mejor.n(), new Pct(mejor.wr(), mejor.w(), mejor.n(), true), (long) (Integer) x[2] }); }
            mostrarTablaCompleta(t("Mejor civ por mapa", "Best civ per map") + " · " + modoNombre(statsModo) + " · " + tramoNombre(statsTramo), new String[]{ t("Mapa", "Map"), t("Mejor civ", "Best civ"), t("Partidas civ", "Civ games"), "WR", t("Partidas mapa", "Map games") }, filas, 4);
        }));
        if (filasMejor == 0) { JLabel vac = new JLabel(t("Sin mapas con 20+ partidas por civ.", "No maps with 20+ games per civ.")); vac.setForeground(Color.GRAY); vac.setFont(vac.getFont().deriveFont(Font.PLAIN, 11f)); stMejorPorMapa.add(vac); }
        stPool.removeAll();
        stPool.add(tituloSeccion(t("Pool de mapas", "Map pool"), t("Cuánto se juega cada mapa en este modo (porcentaje de partidas). Con un tramo de ELO elegido, la cuota es la de ese tramo.", "How much each map is played in this mode (share of games). With an ELO bracket chosen, the share is that bracket's.")));
        long totalMapas = 0; for (Map.Entry<String, Integer> en : lm) totalMapas += en.getValue();
        double maxMapa = lm.isEmpty() ? 1 : lm.get(0).getValue();
        Color oliva = temaOscuroActivo ? new Color(0xa3, 0xb8, 0x6c) : new Color(0x5d, 0x6b, 0x1e);
        long mostradas = 0;
        for (Map.Entry<String, Integer> en : lm.subList(0, Math.min(12, lm.size()))) {
            mostradas += en.getValue();
            stPool.add(filaBarra(iconoMapa(nombreMapaStats(v, en.getKey()), en.getKey(), 18), nombreMapaStats(v, en.getKey()), en.getValue() / maxMapa, pct1(totalMapas == 0 ? 0 : 100.0 * en.getValue() / totalMapas), oliva, miles(en.getValue()) + t(" partidas", " games"), null));
        }
        if (lm.size() > 12 && totalMapas > mostradas) {
            stPool.add(filaBarra(null, t("Otros (", "Others (") + (lm.size() - 12) + t(" mapas)", " maps)"), (totalMapas - mostradas) / maxMapa, pct1(100.0 * (totalMapas - mostradas) / totalMapas), new Color(128, 128, 128, 120), miles(totalMapas - mostradas) + t(" partidas en el resto del pool", " games in the rest of the pool"), null));
            long totalMapasF = totalMapas; double maxMapaF = maxMapa;
            stPool.add(enlaceVerTodo(lm.size(), () -> {
                List<Object[]> filas = new ArrayList<>();
                for (Map.Entry<String, Integer> en : lm) filas.add(new Object[]{ new Celda(iconoMapa(nombreMapaStats(v, en.getKey()), en.getKey(), 18), nombreMapaStats(v, en.getKey()), null), (long) en.getValue(), new Pct(totalMapasF == 0 ? 0 : 100.0 * en.getValue() / totalMapasF, 0, 0, false) });
                mostrarTablaCompleta(t("Pool de mapas", "Map pool") + " · " + modoNombre(statsModo) + " · " + tramoNombre(statsTramo), new String[]{ t("Mapa", "Map"), t("Partidas", "Games"), "%" }, filas, 1);
            }));
        }
        for (JPanel p : new JPanel[]{ stMasJugadas, stMejorPorMapa, stPool }) { p.revalidate(); p.repaint(); }
        ajustarFilasCivStats();
        stTendencias.datos(porPick.subList(0, Math.min(5, porPick.size())));
        boolean unoContraUno = statsModo.endsWith("_1v1");
        stTituloMatriz.setVisible(unoContraUno); stMatriz.setVisible(unoContraUno);
        if (unoContraUno) stMatriz.datos(v, lista);
        stEstado.setText(t("Resumen del ", "Summary of ") + v.hasta() + (partidas < POCAS_PARTIDAS ? "  ·  " + t("pocas partidas con estos filtros: prueba 90 o 365 días", "few games with these filters: try 90 or 365 days") : ""));
        civStatsPanel.revalidate(); civStatsPanel.repaint();
    }

    /** La matriz de matchups en una ventana a pantalla completa, con celdas grandes y scroll. */
    void mostrarMatrizGrande() {
        if (stMatriz == null || stMatriz.civs.isEmpty()) return;
        JDialog d = new JDialog(this, t("Matchups · ", "Matchups · ") + modoNombre(statsModo) + " · " + tramoNombre(statsTramo) + " · " + ventanaNombre(statsVentana), false);
        MatrizPanel grande = new MatrizPanel();
        grande.copiarDe(stMatriz);
        Rectangle pantalla = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int disponible = Math.min(pantalla.width - 180, pantalla.height - 200);
        grande.setCelda(Math.max(12, Math.min(30, disponible / Math.max(1, grande.civs.size()))));
        JScrollPane sp = new JScrollPane(grande);
        sp.setBorder(null);
        d.add(sp);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setBounds(pantalla);
        d.setVisible(true);
    }

    /** Prefijos de clave de una fuente de tendencia: uno por tramo del rango (o uno solo si la fuente no lleva tramo). */
    List<String> prefijosTendencia(String fuente, List<String> tramosRango) {
        List<String> out = new ArrayList<>();
        switch (fuente) {
            case "mt" -> { for (String tr : tramosRango) out.add(statsModo + "|" + tendMapa + "|" + tr + "|"); }
            case "tramo" -> { for (String tr : tramosRango) out.add(statsModo + "|" + tr + "|"); }
            case "mapa" -> out.add(statsModo + "|" + tendMapa + "|");
            default -> out.add(statsModo + "|");
        }
        return out;
    }
    /** Serie mes → {n,w} de una civ, sumando los tramos del rango cuando la fuente va por tramo. */
    Map<String, int[]> serieTendencia(Tendencias tn, String fuente, String civ, List<String> tramosRango) {
        Map<String, Map<String, int[]>> tabla = switch (fuente) { case "mt" -> tn.filasMT(); case "mapa" -> tn.filasMapa(); case "tramo" -> tn.filasTramo(); default -> tn.filas(); };
        if (tabla == null) return null;
        Map<String, int[]> suma = null;
        for (String prefijo : prefijosTendencia(fuente, tramosRango)) {
            Map<String, int[]> m = tabla.get(prefijo + civ);
            if (m == null) continue;
            if (suma == null) suma = new HashMap<>();
            for (Map.Entry<String, int[]> en : m.entrySet()) { int[] acc = suma.computeIfAbsent(en.getKey(), k -> new int[2]); acc[0] += en.getValue()[0]; acc[1] += en.getValue()[1]; }
        }
        return suma;
    }
    /** Total de «slots» de civ en un mes con el filtro de la tendencia (para el pick rate): suma de n de todas las civs (y de los tramos del rango). */
    final Map<String, Long> totalesTendencia = new HashMap<>();
    long totalTendencia(Tendencias tn, String fuente, String mes) {
        String clave = fuente + "|" + statsModo + "|" + tendMapa + "|" + tendTramo + "|" + mes;
        Long c = totalesTendencia.get(clave);
        if (c != null) return c;
        VentanaStats vTr = VENTANAS_STATS.get(statsVentana);
        List<String> tramosRango = new ArrayList<>();
        if (vTr != null) for (String tr : vTr.tramos()) if (tramoEnRango(tr, vTr.tramos(), tendTramo)) tramosRango.add(tr);
        Map<String, Map<String, int[]>> tabla = switch (fuente) { case "mt" -> tn.filasMT(); case "mapa" -> tn.filasMapa(); case "tramo" -> tn.filasTramo(); default -> tn.filas(); };
        long total = 0;
        for (String prefijo : prefijosTendencia(fuente, tramosRango)) if (tabla != null) for (Map.Entry<String, Map<String, int[]>> en : tabla.entrySet()) {
            if (!en.getKey().startsWith(prefijo) || en.getKey().substring(prefijo.length()).contains("|")) continue;
            int[] nw = en.getValue().get(mes); if (nw != null) total += nw[0];
        }
        totalesTendencia.put(clave, total);
        return total;
    }

    /** Líneas de winrate por mes de las civs más jugadas (y la seleccionada en la tabla). */
    class TendenciasPanel extends JPanel {
        List<CivAgg> civs = List.of();
        TendenciasPanel() { setOpaque(false); }
        void datos(List<CivAgg> c) { civs = c; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), ht = getHeight(), ml = 48, mb = 26, mt = 24, mr = 150;
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            Color gris = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 90 : 170);
            Font base = g2.getFont();
            g2.setFont(base.deriveFont(Font.BOLD, 13f)); g2.setColor(fg);
            Tendencias tn = tendenciasStats;
            int ventSel = stTendVentana == null ? 2 : stTendVentana.getSelectedIndex();
            boolean pick = stTendPick != null && stTendPick.isSelected();
            boolean conMapa = !"*".equals(tendMapa), conTramo = !"*".equals(tendTramo);
            String fuente = conMapa && conTramo ? "mt" : conMapa ? "mapa" : conTramo ? "tramo" : "todo";
            VentanaStats vTr = VENTANAS_STATS.get(statsVentana);
            List<String> tramosRango = new ArrayList<>();
            if (vTr != null) for (String tr : vTr.tramos()) if (tramoEnRango(tr, vTr.tramos(), tendTramo)) tramosRango.add(tr);
            boolean hayFuente = tn != null && switch (fuente) {
                case "mt" -> tn.filasMT() != null && tramosRango.stream().anyMatch(tr -> tn.filasMT().keySet().stream().anyMatch(k -> k.startsWith(statsModo + "|" + tendMapa + "|" + tr + "|")));
                case "mapa" -> tn.filasMapa() != null && tn.filasMapa().keySet().stream().anyMatch(k -> k.startsWith(statsModo + "|" + tendMapa + "|"));
                case "tramo" -> tn.filasTramo() != null && tramosRango.stream().anyMatch(tr -> tn.filasTramo().keySet().stream().anyMatch(k -> k.startsWith(statsModo + "|" + tr + "|")));
                default -> true;
            };
            VentanaStats vTit = VENTANAS_STATS.get(statsVentana);
            String titulo = (pick ? t("Tendencia del pick rate por mes", "Monthly pick rate trend") : t("Tendencia del winrate por mes", "Monthly win rate trend")) + "  ·  " + modoNombre(statsModo)
                    + " · " + (conMapa ? nombreMapaStats(vTit, tendMapa) : t("todos los mapas", "all maps")) + " · " + (conTramo ? tramoNombre(tendTramo) : t("todos los ELO", "all ELO"));
            g2.drawString(titulo, 4, 16);
            if (tn != null && !hayFuente) {
                g2.setFont(base.deriveFont(10f)); g2.setColor(gris);
                g2.drawString(fuente.equals("mt") ? t("Mapa y ELO a la vez: solo en modos 1v1 y para los 12 mapas más jugados (con el motor 1.3.1 publicado en sfr-data).", "Map and ELO together: only in 1v1 modes and for the 12 most played maps (with engine 1.3.1 published in sfr-data).")
                        : t("Sin serie para este filtro todavía: hace falta el motor 1.3.1 publicado en sfr-data.", "No series for this filter yet: engine 1.3.1 must be published in sfr-data."), ml, ht / 2);
                g2.dispose(); return;
            }
            g2.setFont(base.deriveFont(10f));
            if (tn == null || tn.meses().isEmpty()) { g2.setColor(gris); g2.drawString(t("Sin tendencias.", "No trend data."), ml, ht / 2); g2.dispose(); return; }
            List<String> claves = new ArrayList<>();
            List<String> mesesTop = new ArrayList<>(tn.meses());
            { int vSel = stTendVentana == null ? 2 : stTendVentana.getSelectedIndex(); if (vSel == 0 || vSel == 1) { int n = vSel == 0 ? 3 : 6; if (mesesTop.size() > n) mesesTop = new ArrayList<>(mesesTop.subList(mesesTop.size() - n, mesesTop.size())); } }
            if (stMasJugadasCheck == null || stMasJugadasCheck.isSelected()) {   // las 5 más jugadas CON LOS FILTROS DE ESTA FILA (mapa, ELO y periodo), no con los de la tabla
                Map<String, Map<String, int[]>> tabla = switch (fuente) { case "mt" -> tn.filasMT(); case "mapa" -> tn.filasMapa(); case "tramo" -> tn.filasTramo(); default -> tn.filas(); };
                Map<String, Long> porCiv = new HashMap<>();
                for (String prefijo : prefijosTendencia(fuente, tramosRango)) if (tabla != null) for (Map.Entry<String, Map<String, int[]>> en : tabla.entrySet()) {
                    if (!en.getKey().startsWith(prefijo) || en.getKey().substring(prefijo.length()).contains("|")) continue;
                    long n = 0; for (String mm : mesesTop) { int[] nw = en.getValue().get(mm); if (nw != null) n += nw[0]; }
                    if (n > 0) porCiv.merge(en.getKey().substring(prefijo.length()), n, Long::sum);
                }
                porCiv.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).limit(5).forEach(en -> claves.add(en.getKey()));
            }
            for (String c : stCivsSeleccionadas) if (!claves.contains(c)) claves.add(c);
            if (claves.isEmpty()) { g2.setColor(gris); g2.drawString(t("Selecciona civs en la tabla (Ctrl para varias) o marca «Las 5 más jugadas».", "Select civs in the table (Ctrl for several) or tick \u201C5 most played\u201D."), ml, ht / 2); g2.dispose(); return; }
            List<String> meses = new ArrayList<>(tn.meses());
            if (ventSel == 0 || ventSel == 1) { int n = ventSel == 0 ? 3 : 6; if (meses.size() > n) meses = new ArrayList<>(meses.subList(meses.size() - n, meses.size())); }
            else if (ventSel == 3) { VentanaStats vp = VENTANAS_STATS.get("parche"); if (vp != null && vp.desde().length() >= 7) { String desde = vp.desde().substring(0, 7); meses.removeIf(mm -> mm.compareTo(desde) < 0); } }
            if (meses.size() < 2) { g2.setColor(gris); g2.drawString(t("Aún no hay dos meses en esta ventana.", "Not two months in this window yet."), ml, ht / 2); g2.dispose(); return; }
            double lo = 100, hi = 0;
            Map<String, double[]> series = new LinkedHashMap<>();
            for (String c : claves) {
                Map<String, int[]> m = serieTendencia(tn, fuente, c, tramosRango);
                double[] s = new double[meses.size()];
                int minimo = fuente.equals("todo") ? 200 : fuente.equals("mt") ? 30 : 60;   // con más filtro hay menos partidas: umbral más bajo
                for (int i = 0; i < meses.size(); i++) {
                    int[] nw = m == null ? null : m.get(meses.get(i));
                    if (pick) {   // pick rate: partidas de la civ entre todas las de ese filtro y mes
                        long total = totalTendencia(tn, fuente, meses.get(i));
                        s[i] = nw == null || total == 0 ? Double.NaN : 100.0 * nw[0] / total;
                    } else s[i] = nw == null || nw[0] < minimo ? Double.NaN : 100.0 * nw[1] / nw[0];
                    if (!Double.isNaN(s[i])) { lo = Math.min(lo, s[i]); hi = Math.max(hi, s[i]); }
                }
                series.put(c, s);
            }
            if (pick) { lo = 0; hi = Math.max(1, hi * 1.15); }
            else { if (hi <= lo) { lo = 45; hi = 55; } lo = Math.floor(lo - 1); hi = Math.ceil(hi + 1); }
            double sx = (double) (w - ml - mr) / Math.max(1, meses.size() - 1), sy = (ht - mb - mt) / (hi - lo);
            g2.setColor(gris);
            double pasoY = pick ? pasoBonito((hi - lo) / 4.0) : (hi - lo > 12 ? 5 : 2);
            for (double y = pick ? 0 : Math.ceil(lo); y <= hi; y += pasoY) {
                int py = ht - mb - (int) ((y - lo) * sy);
                g2.drawLine(ml, py, w - mr, py);
                g2.drawString(pct1(y), 4, py + 4);
            }
            for (int i = 0; i < meses.size(); i++) {
                int px = ml + (int) (i * sx);
                g2.drawString(meses.get(i).substring(2), px - 12, ht - mb + 14);
            }
            if (!pick) { int py50 = ht - mb - (int) ((50 - lo) * sy); if (py50 >= mt && py50 <= ht - mb) { g2.setColor(new Color(128, 128, 128, 160)); g2.drawLine(ml, py50, w - mr, py50); } }
            int k = 0;
            for (Map.Entry<String, double[]> en : series.entrySet()) {
                Color c = PALETA_LADDER[k % PALETA_LADDER.length];
                boolean sel = stCivsSeleccionadas.contains(en.getKey());
                g2.setColor(c);
                g2.setStroke(new BasicStroke(sel ? 3f : 1.8f));
                double[] s = en.getValue();
                int prevX = -1, prevY = -1;
                for (int i = 0; i < s.length; i++) {
                    if (Double.isNaN(s[i])) { prevX = -1; continue; }
                    int px = ml + (int) (i * sx), py = ht - mb - (int) ((s[i] - lo) * sy);
                    if (prevX >= 0) g2.drawLine(prevX, prevY, px, py);
                    g2.fillOval(px - 2, py - 2, 5, 5);
                    prevX = px; prevY = py;
                }
                g2.setFont(base.deriveFont(sel ? Font.BOLD : Font.PLAIN, 11f));
                g2.drawString(nombreCivStats(en.getKey()), w - mr + 12, mt + 12 + k * 15);
                g2.fillRect(w - mr + 2, mt + 5 + k * 15, 7, 7);
                k++;
            }
            g2.dispose();
        }
    }

    /** Matriz de matchups 1v1: color por winrate de la civ de la fila contra la de la columna; tooltip con la cifra. */
    class MatrizPanel extends JPanel {
        List<String> civs = List.of();
        Map<String, int[]> celdas = Map.of();   // "a|b" → {n, wins de a}
        int celda = 13, margenIzq = 126, margenSup = 112;
        MatrizPanel() {
            setOpaque(false);
            ToolTipManager.sharedInstance().registerComponent(this);
            addMouseWheelListener(e -> {
                if (e.isControlDown()) { setCelda(celda + (e.getPreciseWheelRotation() < 0 ? 2 : -2)); e.consume(); }
                else { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this); if (sc != null) sc.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, sc)); }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int f = (e.getY() - margenSup) / celda;
                    if (e.getX() < margenIzq && f >= 0 && f < civs.size() && e.getClickCount() == 2) { abrirTechTree(civs.get(f)); return; }   // doble clic en el nombre → tech tree
                    if (e.getX() < margenIzq && f >= 0 && f < civs.size()) {
                        stCivSeleccionada = civs.get(f);
                        for (int r = 0; r < stModelo.getRowCount(); r++) if (stCivSeleccionada.equals(stTabla.getClientProperty("civ" + r))) { int vr = stTabla.convertRowIndexToView(r); stTabla.setRowSelectionInterval(vr, vr); stTabla.scrollRectToVisible(stTabla.getCellRect(vr, 0, true)); break; }
                        repaint();
                    }
                }
            });
        }
        void setCelda(int px) { celda = Math.max(10, Math.min(30, px)); margenIzq = 96 + celda * 2 + 4; margenSup = 100 + celda; redimensionar(); }
        void redimensionar() {
            int lado = margenIzq + celda * civs.size() + 8;
            setPreferredSize(new Dimension(lado, margenSup + celda * civs.size() + 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, margenSup + celda * civs.size() + 8));
            revalidate(); repaint();
        }
        void datos(VentanaStats v, List<CivAgg> lista) {
            List<CivAgg> orden = new ArrayList<>(lista);
            orden.sort((a, b) -> Double.compare(b.wr(), a.wr()));
            List<String> cs = new ArrayList<>();
            for (CivAgg a : orden) cs.add(a.civ());
            Map<String, int[]> m = new HashMap<>();
            for (Matchup mu : v.matchups()) {
                if (!mu.modo().equals(statsModo)) continue;
                if (!tramoEnRango(mu.tramo(), v.tramos(), statsTramo)) continue;
                int[] ab = m.computeIfAbsent(mu.ca() + "|" + mu.cb(), k -> new int[2]); ab[0] += mu.n(); ab[1] += mu.wa();
                int[] ba = m.computeIfAbsent(mu.cb() + "|" + mu.ca(), k -> new int[2]); ba[0] += mu.n(); ba[1] += mu.n() - mu.wa();
            }
            civs = cs; celdas = m;
            redimensionar();
        }
        void copiarDe(MatrizPanel otro) { civs = otro.civs; celdas = otro.celdas; redimensionar(); }
        int[] celdaEn(Point p) {
            int c = (p.x - margenIzq) / celda, f = (p.y - margenSup) / celda;
            if (p.x < margenIzq || p.y < margenSup || c < 0 || f < 0 || c >= civs.size() || f >= civs.size()) return null;
            return new int[]{ f, c };
        }
        @Override public String getToolTipText(MouseEvent e) {
            int[] fc = celdaEn(e.getPoint());
            if (fc == null) return null;
            String a = civs.get(fc[0]), b = civs.get(fc[1]);
            if (a.equals(b)) return nombreCivStats(a) + t(" (espejo: fuera)", " (mirror: excluded)");
            int[] nw = celdas.get(a + "|" + b);
            if (nw == null || nw[0] == 0) return nombreCivStats(a) + " vs " + nombreCivStats(b) + t(": sin partidas", ": no games");
            double[] iv = wilson(nw[1], nw[0]);
            return nombreCivStats(a) + " vs " + nombreCivStats(b) + ": " + pct1(100.0 * nw[1] / nw[0]) + " (" + miles(nw[0]) + t(" partidas, ", " games, ") + pct1(iv[0]) + "–" + pct1(iv[1]) + ")";
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (civs.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            Font peq = g2.getFont().deriveFont(9.5f);
            g2.setFont(peq);
            FontMetrics fm = g2.getFontMetrics();
            int n = civs.size();
            for (int f = 0; f < n; f++) {
                boolean sel = civs.get(f).equals(stCivSeleccionada);
                g2.setColor(sel ? fg : new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 200));
                String nombre = nombreCivStats(civs.get(f));
                g2.setFont(sel ? peq.deriveFont(Font.BOLD) : peq);
                g2.drawString(nombre, margenIzq - 18 - g2.getFontMetrics().stringWidth(nombre), margenSup + f * celda + celda - 3);
                ImageIcon ic = iconoCiv(civs.get(f), celda - 1);
                if (ic != null) g2.drawImage(ic.getImage(), margenIzq - 15, margenSup + f * celda, null);
            }
            for (int c = 0; c < n; c++) {   // emblemas sobre las columnas, bajo los nombres girados
                ImageIcon ic = iconoCiv(civs.get(c), celda - 1);
                if (ic != null) g2.drawImage(ic.getImage(), margenIzq + c * celda, margenSup - 15, null);
            }
            g2.setFont(peq);
            for (int c = 0; c < n; c++) {
                String nombre = nombreCivStats(civs.get(c));
                Graphics2D gr = (Graphics2D) g2.create();
                gr.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 200));
                int px = margenIzq + c * celda + celda - 2, py = margenSup - 4;
                gr.rotate(-Math.PI / 2, px, py - 16);      // el texto sube desde encima del emblema, con los glifos dentro de la columna
                gr.drawString(nombre, px, py - 16);
                gr.dispose();
            }
            for (int f = 0; f < n; f++) {
                for (int c = 0; c < n; c++) {
                    int x = margenIzq + c * celda, y = margenSup + f * celda;
                    if (f == c) { g2.setColor(new Color(128, 128, 128, 40)); g2.fillRect(x, y, celda - 1, celda - 1); continue; }
                    int[] nw = celdas.get(civs.get(f) + "|" + civs.get(c));
                    if (nw == null || nw[0] < 20) { g2.setColor(new Color(128, 128, 128, 18)); g2.fillRect(x, y, celda - 1, celda - 1); continue; }
                    double wr = 100.0 * nw[1] / nw[0];
                    double tint = Math.max(-1, Math.min(1, (wr - 50) / 12));   // ±12 puntos = color pleno
                    Color col = tint >= 0 ? mezcla(new Color(0x2e, 0x7d, 0x32), tint) : mezcla(new Color(0xc6, 0x28, 0x28), -tint);
                    g2.setColor(col);
                    g2.fillRect(x, y, celda - 1, celda - 1);
                }
            }
            if (stCivSeleccionada != null) {
                int f = civs.indexOf(stCivSeleccionada);
                if (f >= 0) { g2.setColor(fg); g2.setStroke(new BasicStroke(1.5f)); g2.drawRect(margenIzq - 1, margenSup + f * celda - 1, celda * n + 1, celda + 1); }
            }
            g2.dispose();
        }
        Color mezcla(Color c, double k) {   // del gris neutro al color, según la intensidad
            int base = temaOscuroActivo ? 70 : 225;
            return new Color((int) (base + (c.getRed() - base) * k), (int) (base + (c.getGreen() - base) * k), (int) (base + (c.getBlue() - base) * k));
        }
    }

    // ----- Tech tree × Civ Stats: banda inferior con los filtros compartidos, el winrate de la civ y su puesto -----
    JPanel ttBanda, ttMapasPanel; JComboBox<String> ttModoCombo, ttMapaCombo, ttVentanaCombo; SelectorRangoElo ttRango, stRango, tendRango; JLabel ttWrEstado, ttWrLabel, ttEmblema; JButton ttPuestoBtn; BarrasWrTramo ttTramosPanel;

    /** Winrate por tramo: barra desde la línea del 50 % (verde arriba si gana, roja abajo si pierde), el % encima y el pick rate del tramo en gris debajo. */
    class BarrasWrTramo extends JPanel {
        String[] etiquetas = new String[0]; int[] n = new int[0], w = new int[0]; double[] pick = new double[0];
        BarrasWrTramo() { setOpaque(false); ToolTipManager.sharedInstance().registerComponent(this); }
        void datos(String[] e, int[] nn, int[] ww, double[] p) { etiquetas = e; n = nn; w = ww; pick = p; repaint(); }
        int barraEn(Point pt) {
            if (n.length == 0) return -1;
            int ml = 8, mr = 8; double sx = (double) (getWidth() - ml - mr) / n.length;
            int i = (int) ((pt.x - ml) / sx);
            return i < 0 || i >= n.length ? -1 : i;
        }
        @Override public String getToolTipText(MouseEvent e) {
            int i = barraEn(e.getPoint());
            if (i < 0) return null;
            if (n[i] < MIN_PARTIDAS_CIV) return etiquetas[i] + ": " + n[i] + t(" partidas (pocas)", " games (few)");
            double[] iv = wilson(w[i], n[i]);
            return etiquetas[i] + ": " + pct1(100.0 * w[i] / n[i]) + " (" + pct1(iv[0]) + "–" + pct1(iv[1]) + ") · " + miles(n[i]) + t(" partidas · pick ", " games · pick ") + pct1(pick[i]);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            int wd = getWidth(), h = getHeight(), ml = 8, mr = 8, mt = 42, mb = 30;
            Font base = g2.getFont();
            g2.setFont(base.deriveFont(Font.BOLD, 13f)); g2.setColor(fg);
            g2.drawString(t("Winrate por tramo de ELO", "Win rate by ELO bracket"), 4, 15);
            g2.setFont(base.deriveFont(10.5f)); g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 160 : 205));
            g2.drawString(t("barra: % de victorias respecto al 50 · debajo: pick rate del tramo", "bar: win rate vs 50% · below: pick rate in that bracket"), 4, 28);
            if (n.length == 0) { g2.dispose(); return; }
            int y50 = mt + (h - mt - mb) / 2, medio = (h - mt - mb) / 2 - 12;
            g2.setColor(new Color(128, 128, 128, 120));
            g2.drawLine(ml, y50, wd - mr, y50);
            double sx = (double) (wd - ml - mr) / n.length;
            for (int i = 0; i < n.length; i++) {
                int x = ml + (int) (i * sx), bw = Math.max(2, (int) sx - 4);
                if (n[i] >= MIN_PARTIDAS_CIV) {
                    double wr = 100.0 * w[i] / n[i], d = Math.max(-8, Math.min(8, wr - 50));   // ±8 puntos = barra completa
                    int bh = (int) Math.round(Math.abs(d) / 8.0 * medio);
                    Color c = colorWr(w[i], n[i]);
                    g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 200));
                    if (d >= 0) g2.fillRoundRect(x, y50 - bh, bw, Math.max(2, bh), 3, 3); else g2.fillRoundRect(x, y50, bw, Math.max(2, bh), 3, 3);
                    g2.setColor(c);
                    g2.setFont(base.deriveFont(Font.BOLD, 12f));
                    String s = String.format(Locale.ROOT, "%.0f", wr) + "%";
                    int sw = g2.getFontMetrics().stringWidth(s);
                    g2.drawString(s, x + bw / 2 - sw / 2, d >= 0 ? y50 - bh - 4 : y50 + bh + 12);
                } else {
                    g2.setColor(new Color(128, 128, 128, 90));
                    g2.fillRoundRect(x, y50 - 2, bw, 4, 3, 3);
                }
                g2.setFont(base.deriveFont(10.5f));
                g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 190 : 215));
                String et = etiquetas[i];
                g2.drawString(et, x + bw / 2 - g2.getFontMetrics().stringWidth(et) / 2, h - mb + 13);
                String pk = pick.length > i && pick[i] > 0 ? String.format(Locale.ROOT, "%.1f", pick[i]).replace('.', "en".equals(IDIOMA) ? '.' : ',') + "%" : "";
                g2.setColor(Color.GRAY);
                g2.drawString(pk, x + bw / 2 - g2.getFontMetrics().stringWidth(pk) / 2, h - mb + 26);
            }
            g2.dispose();
        }
    }
    final Map<String, CivAgg> ttWrPorCiv = new HashMap<>();   // clave del tech tree («Aztecs») → agregado con los filtros
    boolean ttRellenandoFiltros;
    static final Map<String, ImageIcon> ICONOS_CIV = new java.util.concurrent.ConcurrentHashMap<>();

    /** Icono de civ (techtree/img/Civs/<clave>.png) escalado a px; null si aún no está en disco. La clave del companion y la del tech tree coinciden en minúsculas. */
    static ImageIcon iconoCiv(String clave, int px) {
        if (clave == null || clave.isBlank()) return null;
        String k = clave.toLowerCase(Locale.ROOT);
        String kk = k + "@" + px;
        ImageIcon ic = ICONOS_CIV.get(kk);
        if (ic != null) return ic;
        Path p = TT_DIR.resolve("img/Civs/" + k + ".png");
        if (!Files.exists(p)) return null;
        try {
            BufferedImage img = javax.imageio.ImageIO.read(p.toFile());
            if (img == null) return null;
            ic = new ImageIcon(img.getScaledInstance(px, px, Image.SCALE_SMOOTH));
            ICONOS_CIV.put(kk, ic);
            return ic;
        } catch (Exception ex) { return null; }
    }
    static final Map<String, ImageIcon> ICONOS_MAPA = new java.util.concurrent.ConcurrentHashMap<>();
    static final Set<String> MAPAS_PIDIENDO = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Icono de mapa si conocemos su imagen (la API la da con cada partida); se descarga una vez a sfrdata/mapas. null si no hay. */
    static ImageIcon iconoMapa(String nombre, int px) { return iconoMapa(nombre, null, px); }
    /** Igual, probando primero la clave del volcado («rm_arabia») y luego el nombre («Arabia»). */
    static ImageIcon iconoMapa(String nombre, String clave, int px) {
        if ((nombre == null || nombre.isBlank()) && (clave == null || clave.isBlank())) return null;
        String kc = clave == null ? null : clave.toLowerCase(Locale.ROOT).trim();
        String kn = nombre == null ? null : nombre.toLowerCase(Locale.ROOT).trim();
        String k = kc != null && MAPA_IMG_URL.containsKey(kc) ? kc : kn != null && MAPA_IMG_URL.containsKey(kn) ? kn : kc != null ? kc : kn;
        String kk = k + "@" + px;
        ImageIcon ic = ICONOS_MAPA.get(kk);
        if (ic != null) return ic;
        Path p = LADDER_DIR.resolve("mapas").resolve(k.replaceAll("[^a-z0-9_-]", "_") + ".png");
        if (Files.exists(p)) {
            try { BufferedImage img = javax.imageio.ImageIO.read(p.toFile()); if (img != null) { ic = new ImageIcon(img.getScaledInstance(px, px, Image.SCALE_SMOOTH)); ICONOS_MAPA.put(kk, ic); return ic; } } catch (Exception ignored) { }
            return null;
        }
        String url = MAPA_IMG_URL.get(k);
        if (url == null && kc != null && MAPA_IMG_PATRON != null) url = MAPA_IMG_PATRON.replace("{clave}", kc);   // mapa no visto todavía: por el patrón
        if (url == null || !MAPAS_PIDIENDO.add(k)) return null;
        String urlMini = url.contains("cdn.aoe2companion.com") && !url.contains("?") ? url + "?width=200" : url;   // miniatura de 200 px: el tamaño que el companion quiere servir
        new Thread(() -> {
            try {
                Files.createDirectories(p.getParent());
                HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(urlMini)).timeout(Duration.ofSeconds(20)).header("User-Agent", UA).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() / 100 == 2 && r.body().length > 0) Files.write(p, r.body());
            } catch (Exception ex) { log("mapa " + k + ": " + causa(ex)); }
            finally { MAPAS_PIDIENDO.remove(k); }
        }, "mapa-icono").start();
        return null;
    }
    /** Clave de civ a partir del nombre que da la API en el idioma de la app («Aztecas», «Aztecs»). */
    String claveCivDeNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return null;
        String k = ttCivPorNombre.get(nombre);
        if (k != null) return k.toLowerCase(Locale.ROOT);
        String n = nombre.trim().toLowerCase(Locale.ROOT);
        if (Files.exists(TT_DIR.resolve("img/Civs/" + n + ".png"))) return n;
        // variantes de idioma y plural («Tupís», «Muiscas», «Tupi», «Aztecas»…): raíz sin acentos ni -s/-es, comparada por prefijo con la clave y los nombres conocidos
        String raiz = raizCiv(n);
        if (raiz.length() >= 4) {
            for (Map.Entry<String, String> en : ttCivPorNombre.entrySet()) {
                String kk = en.getValue().toLowerCase(Locale.ROOT), rn = raizCiv(en.getKey()), rk = raizCiv(kk);
                if (rn.startsWith(raiz) || raiz.startsWith(rn) || rk.startsWith(raiz) || raiz.startsWith(rk)) return kk;
            }
            if (ttData != null) for (String kk : obj(ttData.get("civs")).keySet()) { String rk = raizCiv(kk.toLowerCase(Locale.ROOT)); if (rk.startsWith(raiz) || raiz.startsWith(rk)) return kk.toLowerCase(Locale.ROOT); }
        }
        return null;
    }
    static String raizCiv(String s) {
        String x = java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim();
        if (x.endsWith("es") && x.length() > 5) x = x.substring(0, x.length() - 2); else if (x.endsWith("s") && x.length() > 4) x = x.substring(0, x.length() - 1);
        return x;
    }

    JPanel construirFiltrosTechTree() {
        ttBanda = new JPanel(new BorderLayout(8, 4));
        ttBanda.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(128, 128, 128, 70)), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JPanel filtros = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 1));
        ttModoCombo = new JComboBox<>();
        for (String m : MODOS_STATS) ttModoCombo.addItem(modoNombre(m));
        ttModoCombo.addActionListener(e -> { if (ttRellenandoFiltros) return; statsModo = MODOS_STATS[Math.max(0, ttModoCombo.getSelectedIndex())]; guardarConfig("stats_modo", statsModo); stCivSeleccionada = null; statsFiltrosCambiados(true); });
        ttMapaCombo = new JComboBox<>();
        ttMapaCombo.setPrototypeDisplayValue("African Clearing (99.999)   ");
        ttMapaCombo.addActionListener(e -> { if (ttRellenandoFiltros) return; Object cl = ttMapaCombo.getClientProperty("claves"); if (cl instanceof List<?> l && ttMapaCombo.getSelectedIndex() >= 0 && ttMapaCombo.getSelectedIndex() < l.size()) { statsMapa = String.valueOf(l.get(ttMapaCombo.getSelectedIndex())); guardarConfig("stats_mapa", statsMapa); statsFiltrosCambiados(true); } });
        ttRango = new SelectorRangoElo();
        ttRango.alCambiar = r -> { if (ttRellenandoFiltros) return; statsTramo = r; guardarConfig("stats_tramo", statsTramo); statsFiltrosCambiados(true); };
        ttVentanaCombo = new JComboBox<>();
        for (String vv : VENTANAS_STATS_KEYS) ttVentanaCombo.addItem(ventanaNombre(vv));
        ttVentanaCombo.addActionListener(e -> { if (ttRellenandoFiltros) return; statsVentana = VENTANAS_STATS_KEYS[Math.max(0, ttVentanaCombo.getSelectedIndex())]; guardarConfig("stats_ventana", statsVentana); if (stVentanaCombo != null) { stRellenandoMapas = true; try { stVentanaCombo.setSelectedIndex(Math.max(0, Arrays.asList(VENTANAS_STATS_KEYS).indexOf(statsVentana))); } finally { stRellenandoMapas = false; } } ttCargarStats(); });
        ttWrEstado = new JLabel();
        ttWrEstado.setFont(ttWrEstado.getFont().deriveFont(Font.PLAIN, 11f));
        ttWrEstado.setForeground(Color.GRAY);
        JLabel cab = new JLabel(t("Estadísticas con:", "Statistics with:"));
        cab.setFont(cab.getFont().deriveFont(Font.BOLD));
        filtros.add(cab);
        filtros.add(ttModoCombo); filtros.add(ttMapaCombo); filtros.add(ttRango); filtros.add(ttVentanaCombo); filtros.add(ttWrEstado);
        filtros.setToolTipText(t("Los mismos filtros que el panel Civ Stats: cambiarlos aquí los cambia allí.", "Same filters as the Civ Stats panel: changing them here changes them there."));
        ttBanda.add(filtros, BorderLayout.NORTH);
        JPanel cols = new JPanel(new GridLayout(1, 3, 16, 0));   // tres columnas iguales
        JPanel izq = new JPanel(new BorderLayout(10, 2));
        ttEmblema = new JLabel();
        ttEmblema.setVerticalAlignment(SwingConstants.TOP);
        ttEmblema.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        izq.add(ttEmblema, BorderLayout.WEST);
        ttWrLabel = new JLabel();
        ttWrLabel.setVerticalAlignment(SwingConstants.TOP);
        izq.add(ttWrLabel, BorderLayout.CENTER);
        ttPuestoBtn = new JButton();
        ttPuestoBtn.setFocusable(false); ttPuestoBtn.setMargin(new Insets(1, 8, 1, 8)); ttPuestoBtn.putClientProperty("JButton.buttonType", "roundRect");
        ttPuestoBtn.setToolTipText(t("Ranking de todas las civs con estos filtros; clic en una civ para verla", "Ranking of every civ with these filters; click a civ to open it"));
        ttPuestoBtn.addActionListener(e -> ttMostrarRanking());
        ttPuestoBtn.setVisible(false);
        JPanel pb = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pb.add(ttPuestoBtn);
        izq.add(pb, BorderLayout.SOUTH);
        cols.add(izq);
        ttTramosPanel = new BarrasWrTramo();
        cols.add(ttTramosPanel);
        ttMapasPanel = listaVertical();
        cols.add(ttMapasPanel);
        cols.setPreferredSize(new Dimension(10, 184));
        ttBanda.add(cols, BorderLayout.CENTER);
        return ttBanda;
    }

    /** Carga (en segundo plano) el resumen de la ventana actual y pinta el winrate en el tech tree. */
    void ttCargarStats() {
        if (VENTANAS_STATS.containsKey(statsVentana)) { ttActualizarWr(); return; }
        ttWrEstado.setText(t("cargando…", "loading…"));
        String ventana = statsVentana;
        new Thread(() -> {
            String err = statsAsegurar(ventana, false);
            SwingUtilities.invokeLater(() -> {
                if (err != null) { ttWrEstado.setText(t("sin datos (", "no data (") + err + ")"); return; }
                ttWrEstado.setText("");
                if (civStatsPanel != null) statsFiltrosCambiados(true); else ttActualizarWr();
            });
        }, "techtree-stats").start();
    }

    /** Recalcula el winrate por civ con los filtros compartidos, rellena los combos de la banda y repinta la línea. */
    void ttActualizarWr() {
        VentanaStats v = VENTANAS_STATS.get(statsVentana);
        if (v == null || ttBanda == null) return;
        ttRellenandoFiltros = true;
        try {
            ttModoCombo.setSelectedIndex(Math.max(0, Arrays.asList(MODOS_STATS).indexOf(statsModo)));
            ttVentanaCombo.setSelectedIndex(Math.max(0, Arrays.asList(VENTANAS_STATS_KEYS).indexOf(statsVentana)));
            Map<String, Integer> mapas = partidasPorMapa(v, statsModo, statsTramo);
            List<Map.Entry<String, Integer>> lm = new ArrayList<>(mapas.entrySet());
            lm.sort((a, b) -> b.getValue() - a.getValue());
            List<String> claves = new ArrayList<>(); claves.add("*");
            ttMapaCombo.removeAllItems();
            ttMapaCombo.addItem(t("Todos los mapas", "All maps"));
            for (Map.Entry<String, Integer> en : lm) { if (en.getValue() < MIN_PARTIDAS_CIV) continue; claves.add(en.getKey()); ttMapaCombo.addItem(nombreMapaStats(v, en.getKey()) + " (" + miles(en.getValue()) + ")"); }
            ttMapaCombo.putClientProperty("claves", claves);
            ttMapaCombo.setSelectedIndex(Math.max(0, claves.indexOf(statsMapa)));
            ttRango.tramos(v.tramos(), statsTramo);
        } finally { ttRellenandoFiltros = false; }
        ttWrPorCiv.clear();
        for (CivAgg a : agregarCivs(v, statsModo, statsMapa, statsTramo).values()) {
            String k = claveTechTree(a.civ());
            if (k != null) ttWrPorCiv.put(k, a);
        }
        ttActualizarBanda();
    }

    /** Civs con datos suficientes, de mayor a menor winrate. */
    List<String> ttRankingCivs() {
        List<String> l = new ArrayList<>();
        for (Map.Entry<String, CivAgg> en : ttWrPorCiv.entrySet()) if (en.getValue().n() >= MIN_PARTIDAS_CIV) l.add(en.getKey());
        l.sort((a, b) -> Double.compare(ttWrPorCiv.get(b).wr(), ttWrPorCiv.get(a).wr()));
        return l;
    }

    /** La banda de la civ elegida: winrate con intervalo, pick, partidas y puesto; barras por tramo de ELO; mejores y peores mapas. */
    void ttActualizarBanda() {
        if (ttWrLabel == null) return;
        String civ = ttCivPedida;
        VentanaStats v = VENTANAS_STATS.get(statsVentana);
        ttMapasPanel.removeAll();
        ttEmblema.setIcon(civ == null ? null : iconoCiv(civ, 40));
        if (civ == null || v == null) {
            ttWrLabel.setText(v == null ? "<html><span style='color:gray'>" + t("Winrate: cargando…", "Win rate: loading…") + "</span></html>" : "");
            ttPuestoBtn.setVisible(false); ttTramosPanel.datos(new String[0], new int[0], new int[0], new double[0]);
            ttMapasPanel.revalidate(); ttMapasPanel.repaint(); return;
        }
        CivAgg a = ttWrPorCiv.get(civ);
        long totalN = 0; for (CivAgg x : ttWrPorCiv.values()) totalN += x.n();
        if (a == null || a.n() < MIN_PARTIDAS_CIV) {
            ttWrLabel.setText("<html><b>" + escapeHtml(ttNombreCiv(civ)) + "</b><br><span style='color:gray'>" + t("sin datos suficientes con estos filtros", "not enough data with these filters") + "</span></html>");
            ttPuestoBtn.setVisible(false); ttTramosPanel.datos(new String[0], new int[0], new int[0], new double[0]);
            ttMapasPanel.revalidate(); ttMapasPanel.repaint(); return;
        }
        Color c = colorWr(a.w(), a.n());
        double[] iv = wilson(a.w(), a.n());
        List<String> ranking = ttRankingCivs();
        int puesto = ranking.indexOf(civ) + 1;
        long partidasFiltro = totalN / (statsModo.endsWith("_1v1") ? 2 : statsModo.endsWith("_2v2") ? 4 : statsModo.endsWith("_3v3") ? 6 : statsModo.endsWith("_4v4") ? 8 : 6);
        ttWrEstado.setText(t("datos hasta el ", "data up to ") + v.hasta() + (partidasFiltro < POCAS_PARTIDAS ? "  ·  " + t("pocas partidas con estos filtros: prueba 90 o 365 días", "few games with these filters: try 90 or 365 days") : ""));
        ttWrLabel.setText("<html><div style='font-size:13px'><b style='font-size:17px'>" + escapeHtml(ttNombreCiv(civ)) + "</b><br><span style='color:gray;font-size:12px'>" + escapeHtml(modoNombre(statsModo)) + " · " + escapeHtml(nombreMapaStats(v, statsMapa)) + " · " + escapeHtml(tramoNombre(statsTramo)) + " · " + escapeHtml(ventanaNombre(statsVentana)) + "</span><br>"
                + "<span style='font-size:26px;color:" + colorHex(c) + "'><b>" + escapeHtml(pct1(a.wr())) + "</b></span> &nbsp;" + t("de victorias", "win rate") + " (" + escapeHtml(pct1(iv[0])) + " – " + escapeHtml(pct1(iv[1])) + ")<br>"
                + t("Pick rate ", "Pick rate ") + "<b>" + escapeHtml(pct1(totalN == 0 ? 0 : 100.0 * a.n() / totalN)) + "</b> &nbsp;·&nbsp; <b>" + miles(a.n()) + "</b> " + t("partidas", "games") + " &nbsp;·&nbsp; " + t("duración media ", "average length ") + "<b>" + duracionMedia(a.d(), a.n()) + "</b></div></html>");
        ttPuestoBtn.setText(puesto > 0 ? t("puesto ", "rank ") + puesto + t(" de ", " of ") + ranking.size() + " \u25BE" : t("ranking \u25BE", "ranking \u25BE"));
        ttPuestoBtn.setVisible(true);
        // por tramo de ELO (con el filtro de mapa)
        String[] etq = new String[v.tramos().size()]; int[] n = new int[etq.length], w = new int[etq.length]; double[] pk = new double[etq.length];
        Map<String, int[]> porTramo = new HashMap<>();
        Map<String, Long> totalTramo = new HashMap<>();   // todas las civs, para el pick rate
        for (CivFila f : v.civs()) {
            if (!f.modo().equals(statsModo)) continue;
            if (!"*".equals(statsMapa) && !f.mapa().equals(statsMapa)) continue;
            totalTramo.merge(f.tramo(), (long) f.n(), Long::sum);
            if (!f.civ().equals(a.civ())) continue;
            int[] x = porTramo.computeIfAbsent(f.tramo(), k -> new int[2]); x[0] += f.n(); x[1] += f.w();
        }
        for (int i = 0; i < etq.length; i++) {
            String tr = v.tramos().get(i);
            etq[i] = tr.endsWith("+") ? tr : tr.startsWith("0-") ? "<" + tr.substring(2) : tr.substring(0, tr.indexOf('-'));
            int[] x = porTramo.get(tr); n[i] = x == null ? 0 : x[0]; w[i] = x == null ? 0 : x[1];
            long tot = totalTramo.getOrDefault(tr, 0L); pk[i] = tot == 0 ? 0 : 100.0 * n[i] / tot;
        }
        ttTramosPanel.datos(etq, n, w, pk);
        // mejores y peores mapas (solo con «todos los mapas»)
        ttMapasPanel.add(tituloSeccion(t("Mejores y peores mapas", "Best and worst maps"), t("Winrate de la civ en cada mapa del pool con el modo y el tramo elegidos (sea cual sea el mapa del filtro); mínimo 20 partidas por mapa (menos de 100: orientativo). El mapa elegido va en negrita.", "The civ's win rate on each pool map with the chosen mode and bracket (whatever the filter map); at least 20 games per map (under 100: indicative only). The chosen map is in bold.")));
        {
            List<CivAgg> pm = new ArrayList<>(civPorMapa(v, statsModo, statsTramo, a.civ()).values());
            pm.removeIf(x -> x.n() < MIN_PARTIDAS_CIV);
            pm.sort((x, y) -> Double.compare(y.wr(), x.wr()));
            double maxN = 1; for (CivAgg x : pm) maxN = Math.max(maxN, x.n());
            List<CivAgg> mostrar = new ArrayList<>();
            if (pm.size() <= 6) mostrar.addAll(pm);
            else { mostrar.addAll(pm.subList(0, 3)); mostrar.addAll(pm.subList(pm.size() - 3, pm.size())); }
            if (!"*".equals(statsMapa)) for (CivAgg x : pm) if (x.civ().equals(statsMapa) && !mostrar.contains(x)) { mostrar.add(3 < mostrar.size() ? 3 : mostrar.size(), x); break; }   // el mapa del filtro, siempre presente
            for (CivAgg x : mostrar) {
                boolean elegido = x.civ().equals(statsMapa);
                ttMapasPanel.add(filaBarra(iconoMapa(nombreMapaStats(v, x.civ()), x.civ(), 16), (elegido ? "\u25B8 " : "") + nombreMapaStats(v, x.civ()), x.n() / maxN, pct1(x.wr()), colorWr(x.w(), x.n()), miles(x.n()) + t(" partidas", " games") + (elegido ? t(" · el mapa elegido", " · the chosen map") : ""), null));
            }
            if (pm.isEmpty()) { JLabel vac = new JLabel(t("Sin mapas con 20+ partidas.", "No maps with 20+ games.")); vac.setForeground(Color.GRAY); vac.setFont(vac.getFont().deriveFont(Font.PLAIN, 11f)); ttMapasPanel.add(vac); }
        }
        ttMapasPanel.revalidate(); ttMapasPanel.repaint();
    }

    /** Lista desplegable de todas las civs ordenadas por winrate con los filtros actuales; clic = ver esa civ. */
    void ttMostrarRanking() {
        List<String> ranking = ttRankingCivs();
        if (ranking.isEmpty()) return;
        JPopupMenu pm = new JPopupMenu();
        DefaultListModel<String> modelo = new DefaultListModel<>();
        for (String k : ranking) modelo.addElement(k);
        JList<String> lista = new JList<>(modelo);
        lista.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean foc) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, value, index, sel, foc);
                String k = String.valueOf(value);
                CivAgg a = ttWrPorCiv.get(k);
                Color col = sel ? lab.getForeground() : colorWr(a.w(), a.n());
                lab.setText("<html><span style='color:gray'>" + (index + 1) + ".</span> &nbsp;" + escapeHtml(ttNombreCiv(k)) + " &nbsp;<font color='" + colorHex(col) + "'><b>" + escapeHtml(pct1(a.wr())) + "</b></font> <span style='color:gray;font-size:9px'>" + miles(a.n()) + "</span></html>");
                lab.setIcon(iconoCiv(k, 18));
                lab.setIconTextGap(6);
                lab.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 8));
                return lab;
            }
        });
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (ttCivPedida != null) lista.setSelectedValue(ttCivPedida, true);
        lista.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int i = lista.locationToIndex(e.getPoint());
                if (i < 0) return;
                String k = modelo.get(i);
                pm.setVisible(false);
                ttCivCombo.setSelectedItem(ttNombreCiv(k));
            }
        });
        JScrollPane sp = new JScrollPane(lista);
        sp.setPreferredSize(new Dimension(300, Math.min(420, 24 * ranking.size() + 6)));
        pm.add(sp);
        pm.show(ttPuestoBtn, ttPuestoBtn.getWidth() - sp.getPreferredSize().width - 4, -sp.getPreferredSize().height - 8);
    }

    // =====================================================================================
    // PERFIL — la página de un jugador: cabecera (ELO, rango y Top % por ladder, máximo, totales,
    // forma y gráfica del año) y su actividad del último año (calendario, horas, meses, civs,
    // mapas, rivales, aliados y tramos). Solo agregados con un mínimo de partidas: nunca el
    // resultado de una partida suelta.
    // =====================================================================================
    static final int ACT_MAX_PAGINAS = 20, ACT_PAGINAS_RAPIDAS = 2, ACT_MAS_PAGINAS = 4, ACT_MIN = 3;   // por API, lo mínimo: 100 partidas al abrir; el resto solo si lo pides

    /**
     * Descarga historial página a página y avisa tras cada página con el estado parcial.
     * base == null: partidas nuevas desde la página 1 hasta maxPaginas (o el año). base con partidas: actualización —
     * baja páginas nuevas hasta encontrar una partida ya conocida y las funde con la base. mas == true: continúa
     * desde la última página de la base (botón «cargar más»).
     */
    static Actividad descargarActividad(long pid, String nombre, Actividad base, boolean mas, int maxPaginas,
                                        java.util.function.Consumer<Actividad> parcial) throws Exception { return descargarActividad(pid, nombre, base, mas, maxPaginas, parcial, () -> false); }

    static Actividad descargarActividad(long pid, String nombre, Actividad base, boolean mas, int maxPaginas,
                                        java.util.function.Consumer<Actividad> parcial, java.util.function.BooleanSupplier cancelar) throws Exception {
        Instant limite = Instant.now().minus(Duration.ofDays(ACT_DIAS));
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
            Object root = COMPANION.matches(pid, pag, PER_PAGE);
            List<Object> ms = arr(val(obj(root), "matches"));
            ultimaPagina = pag;
            if (ms.isEmpty()) { completo = true; break; }
            boolean parar = false;
            for (Object o : ms) {
                Match m = parseMatch(obj(o));
                if (m == null || m.started == null) continue;
                if (m.started.isBefore(limite)) { parar = true; completo = true; continue; }
                if (conocidos.contains(m.id)) { if (actualizar) parar = true; continue; }
                nuevas.add(m);
                conocidos.add(m.id);
            }
            if (ms.size() < PER_PAGE) { completo = true; parar = true; }
            List<Match> fusion = new ArrayList<>(mas ? previas : nuevas);
            if (mas) fusion.addAll(nuevas); else fusion.addAll(previas);
            Actividad parcialA = new Actividad(pid, nombre, fusion, completo && !(actualizar && !parar), actualizar ? Math.max(base.paginas(), pag) : ultimaPagina, System.currentTimeMillis());
            parcial.accept(parcialA);
            if (parar) break;
            dormir(PAUSA_MS);
        }
        List<Match> fusion = new ArrayList<>(mas ? previas : nuevas);
        if (mas) fusion.addAll(nuevas); else fusion.addAll(previas);
        boolean fin = !cancelado && (completo || (actualizar && base.completo()));
        int paginas = actualizar ? Math.max(base.paginas(), ultimaPagina) : ultimaPagina;
        Actividad a = new Actividad(pid, nombre, fusion, fin, paginas, System.currentTimeMillis());
        ACTIVIDAD_CACHE.put(pid, a);
        guardarActividad(a);
        return a;
    }

    /** Calienta en segundo plano los perfiles ya guardados (solo páginas nuevas, despacio): así los tuyos abren al instante. */
    void precalentarPerfiles() {
        Thread h = new Thread(() -> {
            try {
                Thread.sleep(90_000);
                if (!Files.isDirectory(PERFILES_DIR)) return;
                List<Path> ficheros;
                try (var st = Files.list(PERFILES_DIR)) { ficheros = st.filter(p -> p.toString().endsWith(".json")).toList(); } catch (IOException ex) { return; }
                ficheros = new ArrayList<>(ficheros);
                ficheros.removeIf(p -> { try { return System.currentTimeMillis() - Files.getLastModifiedTime(p).toMillis() > 7L * 24 * 3_600_000L; } catch (IOException ex) { return true; } });   // solo los abiertos en la última semana
                ficheros.sort((x, y) -> { try { return Files.getLastModifiedTime(y).compareTo(Files.getLastModifiedTime(x)); } catch (IOException ex) { return 0; } });
                if (ficheros.size() > 10) ficheros = ficheros.subList(0, 10);   // y como mucho diez: la precarga es una comodidad, no una obligación
                for (Path p : ficheros) {
                    long pid;
                    try { pid = Long.parseLong(p.getFileName().toString().replace(".json", "")); } catch (NumberFormatException ex) { continue; }
                    while (actCargando) Thread.sleep(5000);   // nunca competir con una carga pedida por el usuario
                    Actividad base = ACTIVIDAD_CACHE.get(pid);
                    if (base == null) base = cargarActividad(pid);
                    if (base == null || System.currentTimeMillis() - base.ms() < 6 * 3_600_000L) continue;
                    try { descargarActividad(pid, base.nombre(), base, false, 3, a -> { }); } catch (Exception ex) { log("precarga perfil " + pid + ": " + causa(ex)); }
                    Thread.sleep(4000);
                }
            } catch (InterruptedException ignored) { }
        }, "perfiles-precarga");
        h.setDaemon(true);
        h.setPriority(Thread.MIN_PRIORITY);
        h.start();
    }
    JToggleButton perfilBtn;
    JPanel actividadPanel, actCabecera, actChips, actBuscaPanel; JLabel actTitulo, actEstado, actSubtitulo, actPista; JComboBox<String> actModoCombo; PanelScrollable actCuerpo;
    JTextField perfilBusca; JPopupMenu perfilPopup; javax.swing.Timer perfilDebounce; JProgressBar actProgreso; JButton actMasBtn;
    boolean actividadAbierta, actCargando, actRellenandoModos; long actPid; String actNombre = "", actModo = "*";
    CalendarioPanel actCalendario; BarrasActividad actSemana, actHoras, actMeses; JPanel actSpark, actPosicion, actUltimos30Civs, actUltimos30Mapas, actDuracionFila; GraficaElo actGrafica;
    // ----- filtros del perfil: periodo y cara a cara -----
    JComboBox<String> actPeriodoCombo, actH2hSet; JTextField actH2hBusca; JPopupMenu actH2hPopup; javax.swing.Timer actH2hDebounce;
    LocalDate actDesde, actHasta; int actPeriodoIdx;
    Set<Long> h2hIds; String h2hNombre;   // null = sin cara a cara

    boolean pedirRangoFechas() {
        JTextField d1 = new JTextField(actDesde == null ? LocalDate.now().minusDays(29).toString() : actDesde.toString(), 10), d2 = new JTextField(actHasta == null ? LocalDate.now().toString() : actHasta.toString(), 10);
        JPanel pnl = new JPanel(new GridLayout(2, 2, 6, 6));
        pnl.add(new JLabel(t("Desde (AAAA-MM-DD):", "From (YYYY-MM-DD):"))); pnl.add(d1); pnl.add(new JLabel(t("Hasta:", "To:"))); pnl.add(d2);
        int r = JOptionPane.showConfirmDialog(this, pnl, t("Rango de fechas", "Date range"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return false;
        try { actDesde = LocalDate.parse(d1.getText().trim()); actHasta = LocalDate.parse(d2.getText().trim()); if (actHasta.isBefore(actDesde)) { LocalDate x = actDesde; actDesde = actHasta; actHasta = x; } return true; }
        catch (Exception ex) { status.setText(t("Fecha no válida: usa AAAA-MM-DD.", "Invalid date: use YYYY-MM-DD.")); return false; }
    }
    boolean enPeriodo(Match m) {
        if (m.started == null) return actDesde == null;
        LocalDate d = m.started.atZone(ZoneId.systemDefault()).toLocalDate();
        if (actDesde != null && d.isBefore(actDesde)) return false;
        return actHasta == null || !d.isAfter(actHasta);
    }
    /** ¿La partida es contra el conjunto del cara a cara (algún rival de otro equipo está en el conjunto)? */
    boolean contraH2h(Match m, MatchPlayer yo) {
        if (h2hIds == null) return true;
        for (MatchPlayer p : m.players) if (p.id != yo.id && p.team != yo.team && h2hIds.contains(p.id)) return true;
        return false;
    }
    void h2hSugerir() {
        String q = actH2hBusca.getText().trim();
        actH2hPopup.setVisible(false); actH2hPopup.removeAll();
        if (q.length() < 2) return;
        // primero, rivales del propio historial que coincidan (sin llamada): los más frecuentes
        Actividad a = ACTIVIDAD_CACHE.get(actPid);
        Map<Long, String> locales = new LinkedHashMap<>();
        if (a != null) for (Match m : a.partidas()) for (MatchPlayer p : m.players) if (p.id != actPid && p.name != null && p.name.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) locales.putIfAbsent(p.id, p.name);
        int n = 0;
        for (Map.Entry<Long, String> en : locales.entrySet()) { long pid = en.getKey(); String nombre = en.getValue(); JMenuItem it = new JMenuItem(nombre + t("  (rival en tu historial)", "  (opponent in the history)")); it.addActionListener(x -> fijarH2h(Set.of(pid), nombre)); actH2hPopup.add(it); if (++n >= 6) break; }
        if (n > 0 && actH2hBusca.isShowing()) actH2hPopup.show(actH2hBusca, 0, actH2hBusca.getHeight());
        new Thread(() -> {
            List<String[]> res = sugerirPerfiles(q);
            SwingUtilities.invokeLater(() -> {
                if (!q.equals(actH2hBusca.getText().trim())) return;
                int k = 0;
                for (String[] r : res) { long pid = Long.parseLong(r[0]); if (locales.containsKey(pid)) continue; JMenuItem it = new JMenuItem(r[2]); String nombre = r[1]; it.addActionListener(x -> fijarH2h(Set.of(pid), nombre)); actH2hPopup.add(it); if (++k >= 6) break; }
                if (actH2hPopup.getComponentCount() > 0 && actH2hBusca.isShowing()) actH2hPopup.show(actH2hBusca, 0, actH2hBusca.getHeight());
            });
        }, "h2h-sugerir").start();
    }
    void fijarH2h(Set<Long> ids, String nombre) {
        actH2hPopup.setVisible(false);
        h2hIds = ids; h2hNombre = nombre;
        actRellenandoModos = true; try { actH2hBusca.setText(nombre); actH2hSet.setSelectedIndex(0); } finally { actRellenandoModos = false; }
        actPintar();
    }
    void rellenarH2hConjuntos() {
        if (actH2hSet == null) return;
        actRellenandoModos = true;
        try {
            List<String[]> claves = new ArrayList<>();
            actH2hSet.removeAllItems(); actH2hSet.addItem(t("Contra un conjunto…", "Against a set…")); claves.add(new String[]{ "", "" });
            Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); for (Player x : todosJugadores) gs.add(x.grupo());
            for (String g : gs) { actH2hSet.addItem(t("Grupo ", "Group ") + g); claves.add(new String[]{ "grupo", g }); }
            for (String tag : clanesGuardados()) { actH2hSet.addItem(t("Clan ", "Clan ") + tag); claves.add(new String[]{ "clan", tag }); }
            if (!ahoraTop.isEmpty()) { actH2hSet.addItem(t("Top 250 mundial", "World top 250")); claves.add(new String[]{ "top", "" }); }
            actH2hSet.putClientProperty("claves", claves);
        } finally { actRellenandoModos = false; }
    }
    void aplicarH2hConjunto() {
        Object cl = actH2hSet.getClientProperty("claves");
        int i = actH2hSet.getSelectedIndex();
        if (!(cl instanceof List<?> l) || i <= 0 || i >= l.size()) { if (i == 0 && h2hIds != null && (h2hNombre == null || !h2hNombre.equals(actH2hBusca.getText().trim()))) { h2hIds = null; h2hNombre = null; actPintar(); } return; }
        String[] f = (String[]) l.get(i);
        Set<Long> ids = new HashSet<>();
        String nombre = String.valueOf(actH2hSet.getSelectedItem());
        switch (f[0]) {
            case "grupo" -> { for (Player x : todosJugadores) if (x.grupo().equalsIgnoreCase(f[1])) ids.add(x.id()); }
            case "clan" -> { new Thread(() -> { if (ladderAsegurar(false) == null) for (LadderRow r : miembrosClan(f[1])) ids.add(r.pid()); SwingUtilities.invokeLater(() -> { h2hIds = ids; h2hNombre = nombre; actRellenandoModos = true; actH2hBusca.setText(""); actRellenandoModos = false; actPintar(); }); }, "h2h-clan").start(); return; }
            case "top" -> { synchronized (ahoraTop) { for (Object[] x : ahoraTop) ids.add((Long) x[0]); } }
            default -> { }
        }
        h2hIds = ids; h2hNombre = nombre;
        actRellenandoModos = true; try { actH2hBusca.setText(""); } finally { actRellenandoModos = false; }
        actPintar();
    }

    /** ELO tras cada partida de UN ladder (las últimas 100): línea con escala, máximo, mínimo y actual; tooltip con fecha y ±diff. */
    class GraficaElo extends JPanel {
        List<long[]> puntos = List.of();   // {epochMs, rating, diff} de más antigua a más nueva
        String etiqueta = "";
        int ml = 40, mr = 44, mt = 18, mb = 16;
        GraficaElo() { setOpaque(false); ToolTipManager.sharedInstance().registerComponent(this); }
        void datos(List<long[]> p, String et) { puntos = p; etiqueta = et; repaint(); }
        int indiceEn(int x) {
            if (puntos.size() < 2) return -1;
            double sx = (double) (getWidth() - ml - mr) / (puntos.size() - 1);
            int i = (int) Math.round((x - ml) / sx);
            return i < 0 || i >= puntos.size() ? -1 : i;
        }
        @Override public String getToolTipText(MouseEvent e) {
            int i = indiceEn(e.getX());
            if (i < 0) return null;
            long[] p = puntos.get(i);
            String fecha = Instant.ofEpochMilli(p[0]).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES")));
            return "<html>" + fecha + "<br><b>" + p[1] + "</b>" + (p[2] != 0 ? " <span style='color:" + (p[2] > 0 ? "#3a9d5d" : "#c0392b") + "'>" + (p[2] > 0 ? "+" : "") + p[2] + "</span>" : "") + "<br><span style='color:gray'>" + t("partida ", "game ") + (i + 1) + t(" de ", " of ") + puntos.size() + "</span></html>";
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            Color gris = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 120 : 190);
            int w = getWidth(), h = getHeight();
            Font base = g2.getFont();
            g2.setFont(base.deriveFont(11f)); g2.setColor(gris);
            g2.drawString(etiqueta, ml, 12);
            if (puntos.size() < 2) { g2.drawString(t("Sin partidas suficientes en este ladder", "Not enough games on this ladder"), ml, h / 2); g2.dispose(); return; }
            long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            for (long[] p : puntos) { min = Math.min(min, p[1]); max = Math.max(max, p[1]); }
            if (max - min < 40) { long c = (min + max) / 2; min = c - 20; max = c + 20; }
            long paso = (long) pasoBonito((max - min) / 3.0);
            double sy = (double) (h - mt - mb) / (max - min), sx = (double) (w - ml - mr) / (puntos.size() - 1);
            g2.setFont(base.deriveFont(9f));
            for (long v = (long) Math.ceil(min / (double) paso) * paso; v <= max; v += paso) {
                int y = (int) (h - mb - (v - min) * sy);
                g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 35)); g2.drawLine(ml, y, w - mr, y);
                g2.setColor(gris); String sv = String.valueOf(v); g2.drawString(sv, ml - 4 - g2.getFontMetrics().stringWidth(sv), y + 4);
            }
            Color linea = temaOscuroActivo ? new Color(0xff, 0xc9, 0x4d) : new Color(0xb0, 0x78, 0x00);
            g2.setColor(linea); g2.setStroke(new BasicStroke(1.8f));
            int px = -1, py = -1;
            for (int i = 0; i < puntos.size(); i++) {
                int x = ml + (int) Math.round(i * sx), y = (int) (h - mb - (puntos.get(i)[1] - min) * sy);
                if (px >= 0) g2.drawLine(px, py, x, y);
                px = x; py = y;
            }
            long ultimo = puntos.get(puntos.size() - 1)[1];
            g2.fillOval(px - 3, py - 3, 6, 6);
            g2.setFont(base.deriveFont(Font.BOLD, 11f));
            g2.drawString(String.valueOf(ultimo), Math.min(px + 6, w - mr + 2), py + 4);
            g2.setFont(base.deriveFont(9f)); g2.setColor(gris);
            String etMax = t("máx ", "max ") + max, etMin = t("mín ", "min ") + min;
            g2.drawString(etMax, w - mr + 2, mt + 4); g2.drawString(etMin, w - mr + 2, h - mb);
            String fIni = Instant.ofEpochMilli(puntos.get(0)[0]).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES")));
            String fFin = Instant.ofEpochMilli(puntos.get(puntos.size() - 1)[0]).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES")));
            g2.drawString(fIni, ml, h - 3); g2.drawString(fFin, w - mr - g2.getFontMetrics().stringWidth(fFin), h - 3);
            g2.dispose();
        }
    }
    JPanel actTarjetas, actCivs, actCivsRival, actMapas, actRivales, actAliados, actTramos;

    JPanel construirPanelPerfil() {
        actividadPanel = new JPanel(new BorderLayout(8, 6));
        actividadPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel norte = new JPanel(new BorderLayout(8, 0));
        JPanel izq = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        actTitulo = new JLabel();
        actTitulo.setFont(actTitulo.getFont().deriveFont(Font.BOLD, 14f));
        actTitulo.setToolTipText(t("Clic derecho: nota, alias, nicks anteriores, cuentas vinculadas, watchlist…", "Right-click: note, alias, previous names, linked accounts, watchlist…"));
        actTitulo.addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) actMenuNombre(e, actTitulo); } @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) actMenuNombre(e, actTitulo); } });
        actModoCombo = new JComboBox<>();
        actModoCombo.addActionListener(e -> { if (actRellenandoModos) return; Object cl = actModoCombo.getClientProperty("claves"); if (cl instanceof List<?> l && actModoCombo.getSelectedIndex() >= 0 && actModoCombo.getSelectedIndex() < l.size()) { actModo = String.valueOf(l.get(actModoCombo.getSelectedIndex())); actPintar(); } });
        izq.add(actModoCombo);
        actPeriodoCombo = new JComboBox<>(new String[]{ t("Todo el historial", "Whole history"), t("7 días", "7 days"), t("30 días", "30 days"), t("90 días", "90 days"), t("365 días", "365 days"), t("Rango de fechas…", "Date range…") });
        actPeriodoCombo.setToolTipText(t("Periodo: todas las tarjetas y listas del perfil se calculan solo con las partidas de ese periodo", "Period: every card and list of the profile is computed with the games in that period only"));
        actPeriodoCombo.addActionListener(e -> {
            if (actRellenandoModos) return;
            int i = actPeriodoCombo.getSelectedIndex();
            if (i == 5) { if (!pedirRangoFechas()) { actRellenandoModos = true; actPeriodoCombo.setSelectedIndex(actPeriodoIdx); actRellenandoModos = false; return; } }
            else { actDesde = i == 0 ? null : LocalDate.now().minusDays(new int[]{ 0, 6, 29, 89, 364 }[i]); actHasta = null; }
            actPeriodoIdx = i; actPintar();
        });
        izq.add(actPeriodoCombo);
        actHastaLabel = new JLabel(); actHastaLabel.setFont(actHastaLabel.getFont().deriveFont(Font.PLAIN, 11f)); actHastaLabel.setForeground(colorSecundario()); actHastaLabel.setVisible(false);
        actHoyBtn = new JButton(t("Actualizar hoy", "Update today"));
        actHoyBtn.setFocusable(false); actHoyBtn.setMargin(new Insets(1, 8, 1, 8)); actHoyBtn.putClientProperty("JButton.buttonType", "roundRect"); actHoyBtn.setVisible(false);
        actHoyBtn.addActionListener(e -> perfilActualizarHoy());
        izq.add(actHastaLabel); izq.add(actHoyBtn);
        JButton histBtn = new JButton(t("Todas las partidas…", "All games…"));
        histBtn.setFocusable(false); histBtn.setMargin(new Insets(1, 8, 1, 8)); histBtn.putClientProperty("JButton.buttonType", "roundRect");
        histBtn.setToolTipText(t("El histórico del perfil en páginas, con filtro de modo y rec a un clic", "The profile's history in pages, with a mode filter and recs one click away"));
        histBtn.addActionListener(e -> { if (actPid > 0) verHistorialEnTabla(actPid, actNombre); });
        izq.add(histBtn);
        JButton h2hBtn = new JButton(t("Cara a cara…", "Head-to-head…"));
        h2hBtn.setFocusable(false); h2hBtn.setMargin(new Insets(1, 8, 1, 8)); h2hBtn.putClientProperty("JButton.buttonType", "roundRect");
        h2hBtn.setToolTipText(t("El cruce con un rival: balance, mapas, civ contra civ y últimas partidas entre ambos", "The pairing with an opponent: record, maps, civ vs civ and latest games between them"));
        h2hBtn.addActionListener(e -> mostrarCaraACara());
        izq.add(h2hBtn);
        JButton masBtn = new JButton("\u22EF");
        masBtn.setFocusable(false); masBtn.setMargin(new Insets(1, 6, 1, 6)); masBtn.putClientProperty("JButton.buttonType", "roundRect");
        masBtn.setToolTipText(t("Nota, alias, nicks anteriores, cuentas vinculadas, watchlist…", "Note, alias, previous names, linked accounts, watchlist…"));
        masBtn.addActionListener(e -> actMenuNombre(new MouseEvent(masBtn, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 0, masBtn.getHeight(), 1, true), masBtn));
        izq.add(masBtn);
        actH2hBusca = new JTextField(14);
        actH2hBusca.putClientProperty("JTextField.placeholderText", t("Cara a cara con… (nick)", "Head-to-head vs… (nick)"));
        actH2hBusca.putClientProperty("JTextField.showClearButton", true);
        actH2hBusca.setToolTipText(t("Escribe un nick: el perfil pasa a mostrar solo las partidas contra ese jugador (o elige un grupo, clan o top en el desplegable)", "Type a nick: the profile shows only the games against that player (or pick a group, clan or top in the dropdown)"));
        actH2hPopup = new JPopupMenu(); actH2hPopup.setFocusable(false);
        actH2hDebounce = new javax.swing.Timer(450, e -> h2hSugerir()); actH2hDebounce.setRepeats(false);
        actH2hBusca.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() { if (actH2hBusca.getText().trim().isEmpty()) { if (h2hIds != null) { h2hIds = null; h2hNombre = null; actPintar(); } actH2hPopup.setVisible(false); } else actH2hDebounce.restart(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        actH2hSet = new JComboBox<>();
        actH2hSet.setToolTipText(t("Cara a cara contra un conjunto: tu grupo, un clan guardado o el top 250", "Head-to-head against a set: your group, a saved clan or the top 250"));
        actH2hSet.addActionListener(e -> { if (actRellenandoModos) return; aplicarH2hConjunto(); });
        perfilBusca = new JTextField(18);
        perfilBusca.putClientProperty("JTextField.placeholderText", t("Buscar jugador… o selecciona en la watchlist", "Search a player… or select in the watchlist"));
        perfilBusca.putClientProperty("JTextField.showClearButton", true);
        perfilPopup = new JPopupMenu(); perfilPopup.setFocusable(false);
        perfilDebounce = new javax.swing.Timer(450, e -> perfilSugerir());
        perfilDebounce.setRepeats(false);
        perfilBusca.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() { perfilDebounce.restart(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        perfilBusca.addActionListener(e -> { if (perfilPopup.isVisible() && perfilPopup.getComponentCount() > 0) ((JMenuItem) perfilPopup.getComponent(0)).doClick(); else perfilSugerir(); });
        perfilBusca.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && perfilPopup.isVisible() && perfilPopup.getComponentCount() > 0) { ((JMenuItem) perfilPopup.getComponent(0)).doClick(); e.consume(); }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) perfilPopup.setVisible(false);
            }
        });
        izq.add(perfilBusca, 0);   // el buscador, siempre lo primero de la barra (igual en Ratings)
        izq.add(actTitulo, 1);
        actEstado = new JLabel();
        actEstado.setFont(actEstado.getFont().deriveFont(Font.PLAIN, 11f));
        izq.add(actEstado);
        norte.add(izq, BorderLayout.CENTER);
        JButton cerrar = new JButton("\u00D7");
        cerrar.setFocusable(false); cerrar.setMargin(new Insets(0, 7, 0, 7)); cerrar.putClientProperty("JButton.buttonType", "roundRect");
        cerrar.setToolTipText(t("Cerrar", "Close"));
        cerrar.addActionListener(e -> { actividadAbierta = false; mostrarDirectos(false); });
        norte.add(cerrar, BorderLayout.EAST);
        actProgreso = new JProgressBar(0, ACT_MAX_PAGINAS);
        actProgreso.setStringPainted(true);
        actProgreso.setVisible(false);
        actProgreso.setPreferredSize(new Dimension(10, 16));
        norte.add(actProgreso, BorderLayout.SOUTH);
        actMasBtn = new JButton(t("Cargar más partidas", "Load more games"));
        actMasBtn.setFocusable(false); actMasBtn.setMargin(new Insets(1, 8, 1, 8)); actMasBtn.putClientProperty("JButton.buttonType", "roundRect");
        actMasBtn.setToolTipText(t("Este jugador tiene más de 1.000 partidas en el último año: baja las 500 siguientes", "This player has over 1,000 games in the last year: fetch the next 500"));
        actMasBtn.setVisible(false);
        actMasBtn.addActionListener(e -> perfilCargarMas());
        izq.add(actMasBtn);
        perfilTira = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 0));
        perfilTira.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(128, 128, 128, 70)));
        perfilTira.setVisible(false);
        JPanel norteTodo = new JPanel(new BorderLayout(0, 4));
        norteTodo.add(perfilTira, BorderLayout.NORTH);
        norteTodo.add(norte, BorderLayout.CENTER);
        actividadPanel.add(norteTodo, BorderLayout.NORTH);

        actCuerpo = new PanelScrollable();
        actCuerpo.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 4));
        actPista = new JLabel(t("Selecciona un jugador en la watchlist, clic derecho → «Perfil completo…», o escribe un nick arriba.", "Select a player in the watchlist, right-click → \u201CFull profile…\u201D, or type a nick above."));
        actPista.setFont(actPista.getFont().deriveFont(Font.ITALIC, 12f)); actPista.setForeground(Color.GRAY); actPista.setAlignmentX(0f);
        actCuerpo.add(actPista);
        // cabecera: identidad + chips por ladder + gráfica del año
        actCabecera = new JPanel(new BorderLayout(10, 2));
        actCabecera.setAlignmentX(0f); actCabecera.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170)); actCabecera.setPreferredSize(new Dimension(10, 170));
        actCabecera.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(128, 128, 128, 70), 1, true), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JPanel identidad = new JPanel(new BorderLayout(0, 2));
        identidad.setOpaque(false);
        actSubtitulo = new JLabel();
        actSubtitulo.setFont(actSubtitulo.getFont().deriveFont(Font.PLAIN, 11.5f)); actSubtitulo.setForeground(colorSecundario());
        actChips = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 2));
        actChips.setOpaque(false);
        JPanel lineas = new JPanel(); lineas.setLayout(new BoxLayout(lineas, BoxLayout.Y_AXIS)); lineas.setOpaque(false);
        actSubtitulo.setAlignmentX(0f); lineas.add(actSubtitulo);
        actVinculadasPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 0)); actVinculadasPanel.setOpaque(false); actVinculadasPanel.setAlignmentX(0f);
        lineas.add(actVinculadasPanel);
        actNotaLinea = new JLabel(); actNotaLinea.setFont(actNotaLinea.getFont().deriveFont(Font.PLAIN, 11.5f)); actNotaLinea.setForeground(new Color(0xb0, 0x8d, 0x57)); actNotaLinea.setAlignmentX(0f); actNotaLinea.setVisible(false);
        lineas.add(actNotaLinea);
        identidad.add(lineas, BorderLayout.NORTH);
        identidad.add(actChips, BorderLayout.CENTER);
        actCabecera.add(identidad, BorderLayout.CENTER);
        actSpark = new JPanel(new BorderLayout());
        actSpark.setOpaque(false); actSpark.setPreferredSize(new Dimension(480, 150));
        actGrafica = new GraficaElo();
        actSpark.add(actGrafica, BorderLayout.CENTER);
        JButton ampliar = new JButton("\u26F6");
        ampliar.setFocusable(false); ampliar.setMargin(new Insets(0, 4, 0, 4)); ampliar.putClientProperty("JButton.buttonType", "roundRect");
        ampliar.setToolTipText(t("Ampliar la gráfica de ELO", "Enlarge the ELO chart"));
        ampliar.addActionListener(e -> {
            JDialog d = new JDialog(this, t("ELO de ", "ELO of ") + actNombre + " · " + actGrafica.etiqueta, false);
            GraficaElo g = new GraficaElo(); g.datos(actGrafica.puntos, actGrafica.etiqueta);
            d.add(g);
            d.getRootPane().registerKeyboardAction(ev -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            d.setSize(1000, 480); d.setLocationRelativeTo(this); d.setVisible(true);
        });
        JPanel esq = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); esq.setOpaque(false); esq.add(ampliar);
        actSpark.add(esq, BorderLayout.NORTH);
        actCabecera.add(actSpark, BorderLayout.EAST);
        actCuerpo.add(actCabecera);
        actCuerpo.add(Box.createVerticalStrut(8));
        actTarjetas = new JPanel(new GridLayout(1, 5, 8, 0));
        actTarjetas.setAlignmentX(0f); actTarjetas.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        actCuerpo.add(actTarjetas);
        actCuerpo.add(Box.createVerticalStrut(8));
        actCalendario = new CalendarioPanel();
        actCalendario.setAlignmentX(0f);
        actCalendario.setPreferredSize(new Dimension(10, 150)); actCalendario.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        actCuerpo.add(actCalendario);
        JPanel fila2 = new JPanel(new GridLayout(1, 3, 8, 0));
        fila2.setAlignmentX(0f); fila2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170)); fila2.setPreferredSize(new Dimension(10, 170));
        actSemana = new BarrasActividad(t("Por día de la semana", "By day of week"));
        actHoras = new BarrasActividad(t("Por hora (hora local)", "By hour (local time)"));
        actMeses = new BarrasActividad(t("Por mes", "By month"));
        fila2.add(actSemana); fila2.add(actHoras); fila2.add(actMeses);
        actCuerpo.add(fila2);
        actCuerpo.add(Box.createVerticalStrut(8));
        JLabel tDur = tituloSeccion(t("Winrate por duración de la partida", "Win rate by match duration"), t("Tramos como en aoe2insights; solo partidas con resultado", "Buckets as in aoe2insights; games with a result only")); tDur.setAlignmentX(0f); actCuerpo.add(tDur);
        actDuracionFila = new JPanel(new GridLayout(1, 5, 8, 0)); actDuracionFila.setAlignmentX(0f); actDuracionFila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        actCuerpo.add(actDuracionFila);
        actCuerpo.add(Box.createVerticalStrut(8));
        JPanel fila3 = new JPanel(new GridLayout(1, 3, 8, 0));
        fila3.setAlignmentX(0f);
        actCivs = listaVertical(); actCivsRival = listaVertical(); actMapas = listaVertical();
        fila3.add(actCivs); fila3.add(actCivsRival); fila3.add(actMapas);
        actCuerpo.add(fila3);
        actCuerpo.add(Box.createVerticalStrut(8));
        JPanel fila4 = new JPanel(new GridLayout(1, 3, 8, 0));
        fila4.setAlignmentX(0f);
        actRivales = listaVertical(); actAliados = listaVertical(); actTramos = listaVertical();
        fila4.add(actRivales); fila4.add(actAliados); fila4.add(actTramos);
        actCuerpo.add(fila4);
        actCuerpo.add(Box.createVerticalStrut(8));
        JPanel fila5 = new JPanel(new GridLayout(1, 3, 8, 0));
        fila5.setAlignmentX(0f);
        actPosicion = listaVertical(); actUltimos30Civs = listaVertical(); actUltimos30Mapas = listaVertical();
        fila5.add(actPosicion); fila5.add(actUltimos30Civs); fila5.add(actUltimos30Mapas);
        actCuerpo.add(fila5);
        JScrollPane scroll = new JScrollPane(actCuerpo, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        actividadPanel.add(scroll, BorderLayout.CENTER);
        JLabel pie = new JLabel(t("Perfil e historial de aoe2companion.com (hasta 1 año o 1.500 partidas). Solo agregados: cada línea exige al menos 3 partidas; el calendario cuenta partidas, no resultados. Clic en un rival o aliado abre su perfil.",
                "Profile and history from aoe2companion.com (up to 1 year or 1,500 games). Aggregates only: every line needs at least 3 games; the calendar counts games, not results. Click a rival or ally to open their profile."));
        pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f));
        actividadPanel.add(pie, BorderLayout.SOUTH);
        actMostrarCuerpo(false);
        return actividadPanel;
    }

    static JPanel listaVertical() { JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setAlignmentX(0f); return p; }

    /** Enlace «ver los N…» al pie de una lista recortada. */
    JButton enlaceVerTodo(int total, Runnable abrir) {
        JButton b = new JButton(t("ver los ", "see all ") + total + "\u2026");
        b.setFocusable(false); b.setMargin(new Insets(0, 4, 0, 4)); b.putClientProperty("JButton.buttonType", "borderless");
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 11f));
        b.setForeground(temaOscuroActivo ? new Color(0x7f, 0xb3, 0xe0) : new Color(0x2f, 0x5f, 0x8f));
        b.setAlignmentX(0f);
        b.addActionListener(e -> abrir.run());
        return b;
    }

    /** Ventana aparte con una lista entera (misma pintura que la recortada), con scroll. */
    /** Celda con icono y texto (tablas ordenables). */
    record Celda(Icon icono, String texto, Runnable alClicar) implements Comparable<Celda> { @Override public String toString() { return texto; } @Override public int compareTo(Celda o) { return texto.compareToIgnoreCase(o.texto); } }
    /** Porcentaje con color (tablas ordenables): se ordena por valor y se pinta con el color del winrate. */
    record Pct(double valor, int w, int n, boolean colorear) implements Comparable<Pct> { @Override public String toString() { return pct1(valor); } @Override public int compareTo(Pct o) { return Double.compare(valor, o.valor); } }
    /** Ventana con una tabla ordenable por cualquier columna: iconos, porcentajes coloreados, números a la derecha; doble clic en una fila ejecuta su acción si la tiene. */
    void mostrarTablaCompleta(String titulo, String[] columnas, List<Object[]> filas, int ordenInicial) {
        JDialog d = new JDialog(this, titulo, false);
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { for (Object[] f : filas) if (f[c] != null) return f[c].getClass(); return Object.class; }
        };
        for (Object[] f : filas) modelo.addRow(f);
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(tabla.getRowHeight() + 8);
        tabla.setAutoCreateRowSorter(true);
        tabla.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer render = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel lab = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, col);
                lab.setIcon(null); lab.setForeground(sel ? tb.getSelectionForeground() : tb.getForeground()); lab.setFont(tb.getFont());
                if (value instanceof Celda c) { lab.setText(c.texto()); lab.setIcon(c.icono()); lab.setIconTextGap(6); lab.setHorizontalAlignment(SwingConstants.LEFT); if (c.alClicar() != null) lab.setFont(tb.getFont().deriveFont(Font.BOLD)); }
                else if (value instanceof Pct pc) { lab.setText(pct1(pc.valor())); lab.setHorizontalAlignment(SwingConstants.RIGHT); if (pc.colorear() && !sel) lab.setForeground(colorWr(pc.w(), pc.n())); lab.setFont(tb.getFont().deriveFont(Font.BOLD)); }
                else if (value instanceof Number nn) { lab.setText(nn instanceof Double db ? pct1(db) : miles(nn.longValue())); lab.setHorizontalAlignment(SwingConstants.RIGHT); }
                else { lab.setText(value == null ? "" : String.valueOf(value)); lab.setHorizontalAlignment(SwingConstants.LEFT); }
                return lab;
            }
        };
        for (int c = 0; c < columnas.length; c++) tabla.getColumnModel().getColumn(c).setCellRenderer(render);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(220);
        for (int c = 1; c < columnas.length; c++) tabla.getColumnModel().getColumn(c).setPreferredWidth(90);
        if (ordenInicial >= 0 && ordenInicial < columnas.length) tabla.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(ordenInicial, SortOrder.DESCENDING)));
        tabla.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                int vr = tabla.rowAtPoint(e.getPoint()); if (vr < 0) return;
                Object v0 = modelo.getValueAt(tabla.convertRowIndexToModel(vr), 0);
                if (v0 instanceof Celda c && c.alClicar() != null) { c.alClicar().run(); }
            }
        });
        JScrollPane sp = new JScrollPane(tabla);
        sp.setBorder(null);
        JLabel pie = new JLabel(t("Clic en una cabecera: ordenar · doble clic en una fila con negrita: abrir", "Click a header: sort · double-click a bold row: open"));
        pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f)); pie.setForeground(colorSecundario()); pie.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        d.add(sp, BorderLayout.CENTER); d.add(pie, BorderLayout.SOUTH);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setSize(Math.min(760, 260 + 100 * columnas.length), Math.min(720, 90 + (tabla.getRowHeight() + 1) * (filas.size() + 1)));
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }
    void mostrarListaCompleta(String titulo, java.util.function.Consumer<JPanel> rellenar) {
        JDialog d = new JDialog(this, titulo, false);
        PanelScrollable cuerpo = new PanelScrollable();
        cuerpo.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        cuerpo.add(tituloSeccion(titulo));
        rellenar.accept(cuerpo);
        JScrollPane sp = new JScrollPane(cuerpo, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        d.add(sp);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setSize(460, Math.min(720, 60 + 24 * cuerpo.getComponentCount()));
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    void actMostrarCuerpo(boolean hay) {
        if (hay) actPista.setText(t("Selecciona un jugador en la watchlist, clic derecho → «Perfil completo…», o escribe un nick arriba.", "Select a player in the watchlist, right-click → \u201CFull profile…\u201D, or type a nick above."));
        actPista.setVisible(!hay);
        for (Component c : actCuerpo.getComponents()) if (c != actPista) c.setVisible(hay);
        actModoCombo.setVisible(hay);
        actCuerpo.revalidate(); actCuerpo.repaint();
    }

    /** Sugerencias de nick para el buscador del perfil (búsqueda del companion, con retardo). */
    void perfilSugerir() {
        String q = perfilBusca.getText().trim();
        perfilPopup.setVisible(false); perfilPopup.removeAll();
        if (q.length() < 2) return;
        new Thread(() -> {
            List<String[]> res = sugerirPerfiles(q);
            SwingUtilities.invokeLater(() -> {
                if (!q.equals(perfilBusca.getText().trim())) return;
                perfilPopup.removeAll();
                int n = 0;
                for (String[] r : res) {
                    JMenuItem it = new JMenuItem(r[2]);
                    long pid = Long.parseLong(r[0]); String nombre = r[1];
                    it.addActionListener(a -> { perfilBusca.setText(""); perfilPopup.setVisible(false); abrirPerfil(pid, nombre); });
                    perfilPopup.add(it);
                    if (++n >= 8) break;
                }
                if (n > 0 && perfilBusca.isShowing()) perfilPopup.show(perfilBusca, 0, perfilBusca.getHeight());
            });
        }, "perfil-sugerir").start();
    }

    /** Con Perfil abierto, seleccionar a alguien en la watchlist abre su perfil. */
    javax.swing.Timer perfilSeleccionTimer;
    void perfilSincronizarSeleccion() {
        if (!actividadAbierta || actividadPanel == null || !actividadPanel.isShowing()) return;
        if (perfilSeleccionTimer == null) {
            perfilSeleccionTimer = new javax.swing.Timer(500, ev -> {   // medio segundo de respiro: pasar por diez filas con las flechas no dispara diez cargas
                if (!actividadAbierta) return;
                List<Player> sel = playersList.getSelectedValuesList();
                if (sel.isEmpty()) return;
                Player p = sel.get(0);
                if (p.id() != actPid) abrirPerfil(p.id(), nombreVisible(p.id(), p.name()));
            });
            perfilSeleccionTimer.setRepeats(false);
        }
        perfilSeleccionTimer.restart();
    }

    /** El botón «Perfil»: el jugador seleccionado en la watchlist o, si no hay, la página con el buscador. */
    void perfilDesdeBoton() {
        List<Player> sel = playersList.getSelectedValuesList();
        if (!sel.isEmpty()) abrirPerfil(sel.get(0).id(), nombreVisible(sel.get(0).id(), sel.get(0).name()));
        else if (ultimosSujetos.size() == 1 && recsCards != null && recsCards.isShowing()) abrirPerfil(ultimosSujetos.get(0).id(), nombreVisible(ultimosSujetos.get(0).id(), ultimosSujetos.get(0).name()));   // «Partidas de: X» → su perfil
        else if (actPid > 0 && ACTIVIDAD_CACHE.containsKey(actPid)) abrirPerfil(actPid, actNombre);
        else abrirPerfil(0, "");
    }

    /** Partida de la muestra → Match (jugadores con nombre, civ, rating y resultado; sin revelar nada en pantalla hasta que se pida). */
    Match matchDeMuestra(List<Object> f) {
        Match m = new Match();
        m.id = lng(f.get(0)); m.started = Instant.ofEpochSecond(lng(f.get(1))); m.finished = Instant.ofEpochSecond(lng(f.get(2)));
        m.mode = "1v1 Random Map"; m.mapaClave = String.valueOf(f.get(3)); m.map = nombreMapaClave(m.mapaClave);
        int team = 1;
        for (Object jo : arr(f.get(4))) { List<Object> j = arr(jo); MatchPlayer mp = new MatchPlayer(); mp.id = lng(j.get(0)); mp.name = String.valueOf(j.get(1)); String civK = String.valueOf(j.get(2)); mp.civ = civK.isBlank() ? null : nombreCivStats(civK); mp.rating = (int) lng(j.get(3)); mp.won = lng(j.get(4)) == 1; mp.team = team++; m.players.add(mp); }
        if (!m.players.isEmpty()) m.refId = m.players.get(0).id;
        return m;
    }
    /** Guess the ELO desde la muestra de ayer: 5 partidas 1v1 de tramos distintos, nunca una ya vista (los ids vistos se guardan en config). Vacío si no hay muestra. */
    List<Match> gteDesdeMuestra(Random rnd) {
        Map<String, List<List<Object>>> muestra = muestraAyer();
        if (muestra == null || muestra.isEmpty()) return List.of();
        Set<String> vistas = new HashSet<>(Arrays.asList(leerConfig("gte_vistas", "").split(",")));
        List<String> tramos = new ArrayList<>(muestra.keySet()); Collections.shuffle(tramos, rnd);
        List<Match> res = new ArrayList<>();
        for (String tr : tramos) {
            List<List<Object>> l = new ArrayList<>(muestra.get(tr)); Collections.shuffle(l, rnd);
            for (List<Object> f : l) { if (vistas.contains(String.valueOf(lng(f.get(0))))) continue; res.add(matchDeMuestra(f)); break; }
            if (res.size() >= 5) break;
        }
        if (res.isEmpty()) return res;
        int base = maxGteEnDisco();
        for (int k = 0; k < res.size(); k++) { res.get(k).gte = base + k + 1; vistas.add(String.valueOf(res.get(k).id)); }
        List<String> lista = new ArrayList<>(vistas); lista.removeIf(String::isBlank); while (lista.size() > 3000) lista.remove(0);
        guardarConfig("gte_vistas", String.join(",", lista));
        return res;
    }
    /** Al azar por ELO desde la muestra de ayer: partidas cuyo ELO medio cae en [lo, hi], con filtros de mapa y civ, sin repetir las ya enseñadas. */
    List<Match> azarDesdeMuestra(int lo, int hi, String mapaSel, String civSel, Random rnd) {
        Map<String, List<List<Object>>> muestra = muestraAyer();
        if (muestra == null) return List.of();
        List<Match> cand = new ArrayList<>();
        for (List<List<Object>> l : muestra.values()) for (List<Object> f : l) {
            Match m = matchDeMuestra(f);
            if (m.players.size() != 2) continue;
            double media = (m.players.get(0).rating + m.players.get(1).rating) / 2.0;
            if (media < lo || media > hi) continue;
            if (mapaSel != null && !esCualquiera(mapaSel) && !mapaSel.equalsIgnoreCase(m.map) && !mapaSel.equalsIgnoreCase(m.mapaClave)) continue;
            if (civSel != null && m.players.stream().noneMatch(pl -> civSel.equalsIgnoreCase(pl.civ) || civSel.equalsIgnoreCase(claveCivDeNombre(pl.civ)))) continue;
            if (azarEnsenadas.contains(m.id)) continue;
            cand.add(m);
        }
        Collections.shuffle(cand, rnd);
        List<Match> out = new ArrayList<>(cand.subList(0, Math.min(10, cand.size())));
        for (Match m : out) azarEnsenadas.add(m.id);
        return out;
    }
    final Set<Long> azarEnsenadas = new HashSet<>();
    /** Forma por resta con los snapshots: ELO de ahora menos el de anoche (24 h) y menos el de hace 7 días; partidas = diferencia de partidas jugadas. null si no hay snapshot o no sabemos el ELO actual. */
    Forma[] formaPorResta(long pid) {
        int[] ayer = ELO_AYER.get(pid);
        if (ayer == null) return null;
        Integer ahora = null; Integer partidasAhora = null;
        Integer e = eloWatch.get(pid); if (e != null && e > 0) { ahora = e; partidasAhora = gamesWatch.get(pid); }   // el ELO de la lista (top: del leaderboard, fresco; grupos: del vigilante)
        if (ahora == null) return null;
        Forma f24 = new Forma(ahora - ayer[0], 0, 0, 0, false, partidasAhora == null || ayer[1] <= 0 ? (ahora != ayer[0] ? 1 : 0) : Math.max(0, partidasAhora - ayer[1]));
        int[] h7 = ELO_HACE7.get(pid);
        Forma f7 = h7 == null ? null : new Forma(ahora - h7[0], 0, 0, 0, false, partidasAhora == null || h7[1] <= 0 ? (ahora != h7[0] ? 1 : 0) : Math.max(0, partidasAhora - h7[1]));
        return new Forma[]{ f24, f7 };
    }
    final Map<Long, Integer> gamesWatch = new java.util.concurrent.ConcurrentHashMap<>();
    boolean actOrigenSfr; String actHastaSfr; JButton actHoyBtn; JLabel actHastaLabel;

    String nombreMapaClave(String clave) {
        for (VentanaStats v : VENTANAS_STATS.values()) { String n = v.nombresMapas().get(clave); if (n != null) return n; }
        String n = nombreMapaStats(null, clave);
        return n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
    static String posicionNombre(String p) { return "pocket".equals(p) ? "Pocket" : t("Flanco", "Flank"); }
    /** Construye la Actividad (año completo) de un jugador a partir de su entrada en el paquete. null si no está en el alcance. */
    Actividad actividadDesdeShard(long pid, Map<String, Object> shard) {
        boolean v2 = shard.get("v") instanceof Number vn && vn.intValue() >= 2;
        List<Object> partidasRaw; String nombre = "", pais = "";
        List<Object> civsDic = v2 ? arr(shard.get("civs")) : List.of(), mapasDic = v2 ? arr(shard.get("mapas")) : List.of();
        if (v2) {
            Object j = shard.get("j");
            if (!(j instanceof Map<?, ?> jm) || jm.get(String.valueOf(pid)) == null) return null;
            partidasRaw = arr(jm.get(String.valueOf(pid)));
            String[] nn = NOMBRES_AYER.get(pid); if (nn != null) { nombre = nn[0]; pais = nn[1]; }
        } else {
            Object jugadores = shard.get("jugadores");
            if (!(jugadores instanceof Map<?, ?> jm)) return null;
            Object entrada = jm.get(String.valueOf(pid));
            if (!(entrada instanceof Map<?, ?> em)) return null;
            nombre = em.get("n") == null ? "" : String.valueOf(em.get("n")); pais = em.get("c") == null ? "" : String.valueOf(em.get("c"));
            partidasRaw = arr(em.get("m"));
        }
        if (!pais.isBlank()) PAIS_DE.put(pid, pais);
        List<Match> partidas = new ArrayList<>();
        for (Object o : partidasRaw) {
            List<Object> f = arr(o);
            Match m = new Match();
            m.id = lng(f.get(0));
            long ini = lng(f.get(1)), fin = lng(f.get(2));
            m.started = ini > 0 ? Instant.ofEpochSecond(ini) : null;
            m.finished = fin > 0 ? Instant.ofEpochSecond(fin) : null;
            String lb = v2 ? (f.get(3) instanceof Number ln && ln.intValue() >= 0 && ln.intValue() < LADDERS_IDX.length ? LADDERS_IDX[ln.intValue()] : "?") : String.valueOf(f.get(3));
            m.mode = modoDeLadder(lb);
            String mapaClave = v2 ? (f.get(4) instanceof Number mn && mn.intValue() >= 0 && mn.intValue() < mapasDic.size() ? String.valueOf(mapasDic.get(mn.intValue())) : "") : String.valueOf(f.get(4));
            m.map = mapaClave.isBlank() || "unknown".equalsIgnoreCase(mapaClave) ? t("Mapa desconocido", "Unknown map") : nombreMapaClave(mapaClave);
            m.mapaClave = mapaClave;
            for (Object jo : arr(f.get(5))) {
                List<Object> j = arr(jo);
                MatchPlayer mp = new MatchPlayer();
                mp.id = lng(j.get(0));
                String civK;
                if (v2) {   // [pid, civIdx, equipo, rating, diff, won]: nombres del índice nocturno
                    String[] nj = NOMBRES_AYER.get(mp.id); mp.name = nj != null ? nj[0] : "#" + mp.id;
                    civK = j.get(1) instanceof Number cn && cn.intValue() >= 0 && cn.intValue() < civsDic.size() ? String.valueOf(civsDic.get(cn.intValue())) : "";
                    mp.team = (int) lng(j.get(2)); mp.rating = j.get(3) instanceof Number n && n.intValue() > 0 ? n.intValue() : null; mp.ratingDiff = j.get(4) instanceof Number n2 ? n2.intValue() : null;
                    long won = lng(j.get(5)); mp.won = won < 0 ? null : won == 1;
                    if (j.size() > 6 && j.get(6) instanceof Number sl && sl.intValue() > 0) mp.slot = sl.intValue();
                } else {   // [pid, nombre, civ, equipo, rating, diff, won]
                    mp.name = String.valueOf(j.get(1)); civK = String.valueOf(j.get(2));
                    mp.team = (int) lng(j.get(3)); mp.rating = j.get(4) instanceof Number n ? n.intValue() : null; mp.ratingDiff = j.get(5) instanceof Number n2 ? n2.intValue() : null;
                    long won = lng(j.get(6)); mp.won = won < 0 ? null : won == 1;
                }
                mp.civ = civK.isBlank() ? null : nombreCivStats(civK);
                if (mp.id == pid && (mp.name.isBlank() || mp.name.startsWith("#")) && !nombre.isBlank()) mp.name = nombre;
                m.players.add(mp);
            }
            m.refId = pid;
            partidas.add(m);
        }
        partidas.sort((a, b) -> { Instant x = a.started == null ? Instant.EPOCH : a.started, y = b.started == null ? Instant.EPOCH : b.started; return y.compareTo(x); });
        return new Actividad(pid, nombre.isBlank() ? actNombre : nombre, partidas, true, ACT_MAX_PAGINAS, System.currentTimeMillis());
    }
    /** Ficha de cabecera sin llamada: el ELO más reciente que conocemos (watchlist, o el de su última partida por ladder). */
    Object[] perfilSintetico(long pid, Actividad a, String pais) {
        Map<String, int[]> m = new HashMap<>();
        for (Match x : a.partidas()) {
            String lb = x.mode == null ? null : x.mode.contains("Empire") ? (x.mode.contains("1v1") ? "ew_1v1" : "ew_team") : x.mode.contains("Death") ? (x.mode.contains("1v1") ? "dm_1v1" : "dm_team") : x.mode.contains("1v1") ? "rm_1v1" : "rm_team";
            if (lb == null || m.containsKey(lb)) continue;
            for (MatchPlayer mp : x.players) if (mp.id == pid && mp.rating != null) { m.put(lb, new int[]{ mp.rating + (mp.ratingDiff == null ? 0 : mp.ratingDiff), 0, 0, 0, 0 }); }
        }
        Integer eloW = eloWatch.get(pid);
        if (eloW != null && eloW > 0) { int[] v = m.computeIfAbsent("rm_1v1", k -> new int[5]); v[0] = eloW; }
        return new Object[]{ System.currentTimeMillis(), m, pais == null ? "" : pais, "", (long) a.partidas().size() };
    }
    /** «Actualizar hoy»: dos llamadas (ficha del jugador y sus partidas recientes) y se funden con el año del paquete. */
    void perfilActualizarHoy() {
        long pid = actPid;
        if (pid <= 0 || actHoyBtn == null) return;
        actHoyBtn.setEnabled(false); actHoyBtn.setText(t("Actualizando…", "Updating…"));
        new Thread(() -> {
            int nuevas = 0; String error = null;
            try {
                Object[] perfil = perfilLadders(pid);
                vinculadasPedidas.add(pid);
                Actividad base = ACTIVIDAD_CACHE.get(pid);
                Set<Long> vistos = new HashSet<>(); if (base != null) for (Match x : base.partidas()) vistos.add(x.id);
                List<Match> extra = new ArrayList<>();
                Iterable<Match> leidas = COMPANION.partidas(pid, 1, 50);
                for (Match x : leidas) { if (x != null && x.finished != null && !vistos.contains(x.id)) { x.refId = pid; extra.add(x); } }
                nuevas = extra.size();
                if (base != null && !extra.isEmpty()) {
                    List<Match> todas = new ArrayList<>(extra); todas.addAll(base.partidas());
                    todas.sort((a, b) -> { Instant x = a.started == null ? Instant.EPOCH : a.started, y = b.started == null ? Instant.EPOCH : b.started; return y.compareTo(x); });
                    ACTIVIDAD_CACHE.put(pid, new Actividad(pid, base.nombre(), todas, true, base.paginas(), System.currentTimeMillis()));
                }
                final int n = nuevas;
                SwingUtilities.invokeLater(() -> {
                    if (actPid != pid) return;
                    actPintarCabecera(perfil);
                    Actividad a = ACTIVIDAD_CACHE.get(pid);
                    if (a != null) { actRellenarModos(a); actPintar(); }
                    actHoyBtn.setText(n == 0 ? t("Al día · sin partidas nuevas", "Up to date · no new games") : t("Actualizado · +", "Updated · +") + n + t(" partidas", " games"));
                    actHoyBtn.setForeground(UIManager.getColor("Button.foreground"));
                    actHoyBtn.setEnabled(false);
                    if (actHastaLabel != null) actHastaLabel.setText(t("Datos hasta hoy", "Data up to today"));
                });
            } catch (Exception ex) {
                error = causa(ex);
                final String err = error;
                SwingUtilities.invokeLater(() -> { if (actPid != pid) return; actHoyBtn.setEnabled(true); actHoyBtn.setText(t("Actualizar hoy", "Update today")); status.setText(t("No se pudo actualizar: ", "Couldn't update: ") + err); });
            }
        }, "perfil-hoy").start();
    }
    /** «Todas las partidas del perfil»: el histórico en páginas de 25, con filtro de modo; del paquete de sfr-data si está (sin llamadas) o de la API a 50 por llamada, solo al pedir más. Cada fila: descargar la rec o enviarla al juego. */
    JDialog histDialogo; int histPagina; String histModo = "*";
    void mostrarHistorialPerfil(long pid, String nombre) {
        if (histDialogo != null) { histDialogo.dispose(); histDialogo = null; }
        histDialogo = new JDialog(this, t("Todas las partidas de ", "All games of ") + nombre, false);
        JPanel norte = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JComboBox<String> modo = new JComboBox<>(new String[]{ t("Todos los modos", "All modes"), "1v1 Random Map", "Team Random Map", "1v1 Empire Wars", "Team Empire Wars", "1v1 Death Match", "Team Death Match" });
        norte.add(new JLabel(t("Modo:", "Mode:"))); norte.add(modo);
        JLabel info = new JLabel(); info.setForeground(colorSecundario()); norte.add(info);
        JButton ant = new JButton("\u2190"), sig = new JButton("\u2192"); for (JButton b : new JButton[]{ ant, sig }) { b.setFocusable(false); b.setMargin(new Insets(1, 8, 1, 8)); b.putClientProperty("JButton.buttonType", "roundRect"); }
        JButton mas = new JButton(t("Cargar 50 más (1 llamada)", "Load 50 more (1 request)")); mas.setFocusable(false); mas.setMargin(new Insets(1, 8, 1, 8)); mas.putClientProperty("JButton.buttonType", "roundRect"); mas.setVisible(false);
        JLabel pag = new JLabel();
        norte.add(ant); norte.add(pag); norte.add(sig); norte.add(mas);
        String[] cols = { t("Fecha", "Date"), t("Mapa", "Map"), t("Modo", "Mode"), t("Partida", "Game"), t("Rec", "Rec") };
        DefaultTableModel modelo = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        JTable tabla = new JTable(modelo); tabla.setRowHeight(tabla.getRowHeight() + 6); tabla.getTableHeader().setReorderingAllowed(false);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(120); tabla.getColumnModel().getColumn(1).setPreferredWidth(120); tabla.getColumnModel().getColumn(2).setPreferredWidth(120); tabla.getColumnModel().getColumn(3).setPreferredWidth(360); tabla.getColumnModel().getColumn(4).setPreferredWidth(60);
        List<Match> visibles = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES"));
        histPagina = 0; histModo = "*";
        Runnable pintar = () -> {
            Actividad a = ACTIVIDAD_CACHE.get(pid);
            List<Match> todas = new ArrayList<>();
            if (a != null) for (Match m : a.partidas()) if ("*".equals(histModo) || histModo.equals(m.mode)) todas.add(m);
            int porPag = 25, paginas = Math.max(1, (todas.size() + porPag - 1) / porPag);
            histPagina = Math.max(0, Math.min(histPagina, paginas - 1));
            modelo.setRowCount(0); visibles.clear();
            for (int i = histPagina * porPag; i < Math.min(todas.size(), (histPagina + 1) * porPag); i++) {
                Match m = todas.get(i); visibles.add(m);
                StringBuilder j = new StringBuilder(); int team = -1;
                for (MatchPlayer mp : m.players) { if (team != -1 && mp.team != team) j.append("  vs  "); else if (j.length() > 0) j.append(", "); j.append(nombreVisible(mp.id, mp.name)).append(mp.civ == null ? "" : " (" + mp.civ + ")"); team = mp.team; }
                modelo.addRow(new Object[]{ m.started == null ? "" : m.started.atZone(ZoneId.systemDefault()).format(fmt), m.map, m.mode, j.toString(), m.enDisco ? "\u2713" : "" });
            }
            pag.setText((histPagina + 1) + " / " + paginas);
            ant.setEnabled(histPagina > 0); sig.setEnabled(histPagina < paginas - 1);
            boolean deSfr = a != null && a.completo() && actOrigenSfr && pid == actPid;
            info.setText(a == null ? t("sin datos", "no data") : miles(todas.size()) + t(" partidas", " games") + (deSfr ? t(" · último año · de sfr-data", " · last year · from sfr-data") : t(" · cargadas hasta ahora", " · loaded so far")));
            mas.setVisible(a != null && !deSfr);
        };
        modo.addActionListener(e -> { histModo = modo.getSelectedIndex() == 0 ? "*" : String.valueOf(modo.getSelectedItem()); histPagina = 0; pintar.run(); });
        ant.addActionListener(e -> { histPagina--; pintar.run(); });
        sig.addActionListener(e -> { histPagina++; pintar.run(); });
        mas.addActionListener(e -> { mas.setEnabled(false); new Thread(() -> { try { Actividad base = ACTIVIDAD_CACHE.get(pid); Actividad a2 = descargarActividad(pid, nombre, base, false, (base == null ? 0 : base.paginas()) + 1, null, () -> false); ACTIVIDAD_CACHE.put(pid, a2); } catch (Exception ex) { log("historial: " + causa(ex)); } SwingUtilities.invokeLater(() -> { mas.setEnabled(true); pintar.run(); }); }, "historial-mas").start(); });
        JPanel sur = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton desc = new JButton(t("Descargar rec", "Download rec")), env = new JButton(t("Enviar al juego", "Send to game"));
        for (JButton b : new JButton[]{ desc, env }) { b.setFocusable(false); b.setMargin(new Insets(2, 10, 2, 10)); b.putClientProperty("JButton.buttonType", "roundRect"); }
        java.util.function.Supplier<List<Match>> sel = () -> { List<Match> l = new ArrayList<>(); for (int r : tabla.getSelectedRows()) l.add(visibles.get(r)); return l; };
        desc.addActionListener(e -> { List<Match> l = sel.get(); if (l.isEmpty()) return; descargaSinCambiarVista = true; alTerminarDescarga = pintar; download(l); });
        env.addActionListener(e -> { List<Match> l = sel.get(); if (l.isEmpty()) return; descargaSinCambiarVista = true; alTerminarDescarga = pintar; download(l, true); });
        JLabel nota = new JLabel(t("Sin resultado: como en Live now. Selecciona filas y descarga o envía al juego.", "No result shown, like Live now. Select rows and download or send to game.")); nota.setForeground(colorSecundario()); nota.setFont(nota.getFont().deriveFont(Font.PLAIN, 11f));
        sur.add(desc); sur.add(env); sur.add(nota);
        histDialogo.add(norte, BorderLayout.NORTH); histDialogo.add(new JScrollPane(tabla), BorderLayout.CENTER); histDialogo.add(sur, BorderLayout.SOUTH);
        histDialogo.getRootPane().registerKeyboardAction(e -> histDialogo.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pintar.run();
        histDialogo.setSize(900, 640); histDialogo.setLocationRelativeTo(this); histDialogo.setVisible(true);
    }
    /** Estado del botón «Actualizar hoy» según lo que sabemos: ámbar si lo hemos visto en partida después del volcado; gris si no consta nada nuevo. */
    void perfilEstadoHoy() {
        if (actHoyBtn == null) return;
        boolean visible = actOrigenSfr && actPid > 0;
        actHoyBtn.setVisible(visible); if (actHastaLabel != null) actHastaLabel.setVisible(visible);
        if (!visible) return;
        long hastaMs = 0;
        try { if (actHastaSfr != null && actHastaSfr.length() >= 10) hastaMs = LocalDate.parse(actHastaSfr.substring(0, 10)).plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(); } catch (Exception ignored) { }
        Long visto = VISTO_VIVO_MS.get(actPid);
        boolean hayNuevas = (visto != null && visto > hastaMs) || VIVO_PARTIDA.containsKey(actPid);
        actHoyBtn.setEnabled(true);
        actHoyBtn.setText(t("Actualizar hoy", "Update today"));
        actHoyBtn.setForeground(hayNuevas ? (temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00)) : UIManager.getColor("Button.foreground"));
        actHoyBtn.setToolTipText(hayNuevas ? t("Le hemos visto en partida después del volcado: hay partidas de hoy que traer (2 llamadas)", "Seen in a game after the dump: there are today's games to fetch (2 requests)")
                : t("No consta ninguna partida nueva desde el volcado; actualizar solo si crees que ha jugado hoy (2 llamadas)", "No new game is known since the dump; update only if you think they played today (2 requests)"));
        if (actHastaLabel != null) actHastaLabel.setText(t("Datos hasta el ", "Data up to ") + (actHastaSfr == null ? "?" : actHastaSfr) + (hayNuevas ? "" : t(" · sin partidas nuevas conocidas", " · no new games known")));
    }
    /** Abre el perfil de un jugador; pid 0 = página vacía con el buscador. Pinta al instante lo guardado y va completando página a página. */
    void abrirPerfil(long pid, String nombre) {
        if (perfilBtn != null && !perfilBtn.isSelected()) perfilBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        if (techTreeBtn != null && techTreeBtn.isSelected()) cerrarTechTree();
        ahoraAbierta = false; if (ahoraBtn != null) ahoraBtn.setSelected(false);
        actividadAbierta = true;
        ((CardLayout) centroCards.getLayout()).show(centroCards, "perfil");
        subirArriba(actividadPanel);
        taparResultados(); apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        if (pid <= 0) { actTitulo.setText(t("Perfil", "Profile")); actEstado.setText(""); actProgreso.setVisible(false); actMostrarCuerpo(false); perfilBusca.requestFocusInWindow(); refrescarTiraPerfil(); return; }
        perfilContabilizarPestana(pid, nombre);
        registrarDestino(new Destino("perfil", pid, nombre, null));
        if (actCargando && pid == actPid) return;
        actPid = pid; actNombre = nombre;
        actTitulo.setText(t("Perfil de ", "Profile of ") + nombre);
        actNombreReal = nombre;
        actualizarTextoBuscar();
        actMasBtn.setVisible(false);
        Actividad base = ACTIVIDAD_CACHE.get(pid);
        if (base == null) { base = cargarActividad(pid); if (base != null) ACTIVIDAD_CACHE.put(pid, base); }
        boolean fresco = base != null && System.currentTimeMillis() - base.ms() < 30 * 60_000L;
        if (base != null) { actMostrarCuerpo(true); actRellenarModos(base); actPintar(); }
        else { actMostrarCuerpo(false); actPista.setText(t("Descargando el historial… la página se irá rellenando sola.", "Downloading the history… the page will fill itself in.")); }
        Object[] perfilCache = PERFIL_CACHE.get(pid);
        if (perfilCache != null && base != null) actPintarCabecera(perfilCache);
        if (fresco && base.completo() && perfilCache != null) { actEstado.setText(miles(base.partidas().size()) + t(" partidas", " games")); actProgreso.setVisible(false); return; }
        actCargando = true;
        actEstado.setText(t("Consultando el perfil…", "Fetching the profile…"));
        actProgreso.setVisible(true); actProgreso.setIndeterminate(true); actProgreso.setString(t("Perfil…", "Profile…"));
        Actividad baseF = base;
        boolean actualizar = base != null && !base.partidas().isEmpty();
        actOrigenSfr = false; actHastaSfr = null; perfilEstadoHoy();
        new Thread(() -> {
            try {
                ladderAsegurar(false);   // para el Top % (si ya está cargado, no cuesta nada)
                // 1) sfr-data: el año completo del paquete, sin tocar la API
                Actividad desdeSfr = null; String hastaSfr = null; String paisSfr = null;
                try {
                    cargarEloAyer();   // nombres y países (formato v2 los toma del índice nocturno)
                    Map<String, Object> shard = perfilesShard(pid);
                    if (shard != null) { desdeSfr = actividadDesdeShard(pid, shard); hastaSfr = String.valueOf(shard.getOrDefault("hasta", perfilesIndex() == null ? "" : perfilesIndex().get("hasta"))); paisSfr = PAIS_DE.get(pid); }
                } catch (Exception ex) { log("perfiles: " + causa(ex)); }
                if (desdeSfr != null) {
                    Actividad a = desdeSfr; String hastaF = hastaSfr; String paisF = paisSfr;
                    ACTIVIDAD_CACHE.put(pid, a);
                    Object[] perfilConocido = PERFIL_CACHE.get(pid);
                    Object[] perfil = perfilConocido != null ? perfilConocido : perfilSintetico(pid, a, paisF);
                    SwingUtilities.invokeLater(() -> {
                        actCargando = false;
                        if (actPid != pid) return;
                        actOrigenSfr = true; actHastaSfr = hastaF;
                        actPintarCabecera(perfil);
                        actProgreso.setVisible(false); actMostrarCuerpo(true); actRellenarModos(a); actPintar();
                        actEstado.setText(miles(a.partidas().size()) + t(" partidas · último año · de sfr-data", " games · last year · from sfr-data"));
                        actMasBtn.setVisible(false);
                        perfilEstadoHoy();
                    });
                    return;
                }
                // 2) fuera del alcance de sfr-data: la API, con lo mínimo
                Object[] perfil = perfilLadders(pid);
                SwingUtilities.invokeLater(() -> { if (actPid == pid) actPintarCabecera(perfil); });
                int max = actualizar ? ACT_MAX_PAGINAS : ACT_PAGINAS_RAPIDAS;   // nuevo: primero 250 partidas; el resto solo si te quedas
                Actividad a = (fresco && baseF.completo()) ? baseF : descargarActividad(pid, nombre, baseF, false, max, parcialA -> SwingUtilities.invokeLater(() -> {
                    if (actPid != pid) return;
                    ACTIVIDAD_CACHE.put(pid, parcialA);
                    actProgreso.setIndeterminate(false); actProgreso.setMaximum(ACT_MAX_PAGINAS); actProgreso.setValue(Math.min(ACT_MAX_PAGINAS, parcialA.paginas()));
                    actProgreso.setString(t("Historial: página ", "History: page ") + parcialA.paginas() + t(" de ", " of ") + ACT_MAX_PAGINAS + " · " + miles(parcialA.partidas().size()) + t(" partidas", " games"));
                    actEstado.setText(t("datos parciales…", "partial data…"));
                    actMostrarCuerpo(true); actRellenarModos(parcialA); actPintar();
                }), () -> actPid != pid);
                SwingUtilities.invokeLater(() -> {
                    actCargando = false;
                    if (actPid != pid) return;
                    actProgreso.setVisible(false); actMostrarCuerpo(true); actRellenarModos(a); actPintar();
                    // nada se descarga solo: «Cargar más» es una decisión tuya (cada página son 50 partidas y una llamada)
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> { actCargando = false; if (actPid == pid) { actProgreso.setVisible(false); actEstado.setText(t("No se pudo cargar el perfil: ", "Couldn't load the profile: ") + causa(ex)); } });
            }
        }, "perfil-" + pid).start();
    }

    /** «Cargar más»: más páginas del historial (a mano, o solas cuando te quedas en el perfil). */
    void perfilCargarMas() { perfilCargarMas(ACT_MAS_PAGINAS); }
    void perfilCargarMas(int paginas) {
        Actividad base = ACTIVIDAD_CACHE.get(actPid);
        if (base == null || actCargando || paginas <= 0) return;
        long pid = actPid; String nombre = actNombre;
        actCargando = true; actMasBtn.setVisible(false);
        actProgreso.setVisible(true); actProgreso.setIndeterminate(true); actProgreso.setString(t("Cargando más…", "Loading more…"));
        new Thread(() -> {
            try {
                Actividad a = descargarActividad(pid, nombre, base, true, paginas, parcialA -> SwingUtilities.invokeLater(() -> {
                    if (actPid != pid) return;
                    ACTIVIDAD_CACHE.put(pid, parcialA);
                    actProgreso.setIndeterminate(false); actProgreso.setMaximum(base.paginas() + paginas); actProgreso.setValue(parcialA.paginas());
                    actProgreso.setString(t("Historial: página ", "History: page ") + parcialA.paginas() + " · " + miles(parcialA.partidas().size()) + t(" partidas", " games"));
                    actRellenarModos(parcialA); actPintar();
                }), () -> actPid != pid);
                SwingUtilities.invokeLater(() -> { actCargando = false; if (actPid == pid) { actProgreso.setVisible(false); actRellenarModos(a); actPintar(); } });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> { actCargando = false; if (actPid == pid) { actProgreso.setVisible(false); actEstado.setText(t("No se pudo cargar más: ", "Couldn't load more: ") + causa(ex)); } });
            }
        }, "perfil-mas-" + pid).start();
    }

    void actRellenarModos(Actividad a) {
        Map<String, Integer> cuenta = new LinkedHashMap<>();
        for (Match m : a.partidas()) cuenta.merge(m.mode == null ? "?" : m.mode, 1, Integer::sum);
        List<Map.Entry<String, Integer>> l = new ArrayList<>(cuenta.entrySet());
        l.sort((x, y) -> y.getValue() - x.getValue());
        List<String> claves = new ArrayList<>(); claves.add("*");
        actRellenandoModos = true;
        try {
            actModoCombo.removeAllItems();
            actModoCombo.addItem(t("Todos los modos", "All modes") + " (" + a.partidas().size() + ")");
            for (Map.Entry<String, Integer> en : l) { claves.add(en.getKey()); actModoCombo.addItem(en.getKey() + " (" + en.getValue() + ")"); }
            actModoCombo.putClientProperty("claves", claves);
            if (!claves.contains(actModo)) actModo = "*";
            actModoCombo.setSelectedIndex(claves.indexOf(actModo));
        } finally { actRellenandoModos = false; }
    }

    JLabel chipPerfil(String titulo, String valor, String tooltip) {
        JLabel l = new JLabel("<html><span style='color:" + colorSecundarioHex() + ";font-size:9.5px;font-weight:bold'>" + escapeHtml(titulo).toUpperCase(Locale.ROOT) + "</span><br><b>" + valor + "</b></html>");
        l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(128, 128, 128, 60), 1, true), BorderFactory.createEmptyBorder(1, 6, 1, 6)));
        if (tooltip != null) l.setToolTipText(tooltip);
        return l;
    }

    // ----- Cara a cara: ventana con buscador de rival y ficha del cruce (partidas del historial cargado) -----
    JDialog h2hDialogo; JTextField h2hBusca; JPopupMenu h2hPopup; javax.swing.Timer h2hDebounce; PanelScrollable h2hCuerpo; JComboBox<String> h2hModo; long h2hPidActual; String h2hNombreActual, h2hMapaFiltro;
    final List<Object[]> h2hHistorial = new ArrayList<>(); boolean h2hNavegando; JButton h2hAtrasBtn;   // {pid, nombre, mapa, modoIdx}
    void h2hRegistrar() { if (h2hNavegando) return; Object[] est = { h2hPidActual, h2hNombreActual, h2hMapaFiltro, h2hModo == null ? 0 : h2hModo.getSelectedIndex() }; if (!h2hHistorial.isEmpty()) { Object[] u = h2hHistorial.get(h2hHistorial.size() - 1); if (Objects.equals(u[0], est[0]) && Objects.equals(u[2], est[2]) && Objects.equals(u[3], est[3])) return; } h2hHistorial.add(est); if (h2hAtrasBtn != null) h2hAtrasBtn.setEnabled(h2hHistorial.size() > 1); }
    void h2hAtras() {
        if (h2hHistorial.size() < 2) return;
        h2hHistorial.remove(h2hHistorial.size() - 1);
        Object[] est = h2hHistorial.get(h2hHistorial.size() - 1);
        h2hNavegando = true;
        try {
            h2hMapaFiltro = (String) est[2];
            if (h2hModo != null && (Integer) est[3] != h2hModo.getSelectedIndex()) h2hModo.setSelectedIndex((Integer) est[3]);
            if ((Long) est[0] > 0) h2hFijar((Long) est[0], (String) est[1]); else { h2hPidActual = 0; h2hNombreActual = null; h2hBusca.setText(""); h2hPintarSugerenciasIniciales(); }
        } finally { h2hNavegando = false; }
        if (h2hAtrasBtn != null) h2hAtrasBtn.setEnabled(h2hHistorial.size() > 1);
    }
    static final String[] H2H_MODOS = { "*", "1v1", "tg", "ew", "dm", "unranked" };

    void mostrarCaraACara() {
        if (actPid <= 0 || ACTIVIDAD_CACHE.get(actPid) == null) { status.setText(t("Abre primero un perfil con historial cargado.", "Open a profile with its history loaded first.")); return; }
        if (h2hDialogo != null) { h2hDialogo.dispose(); h2hDialogo = null; }
        h2hDialogo = new JDialog(this, t("Cara a cara · ", "Head-to-head · ") + actNombre, false);
        JPanel norte = new JPanel(new BorderLayout(8, 4));
        norte.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        h2hBusca = new JTextField(22);
        h2hBusca.putClientProperty("JTextField.placeholderText", t("Rival: escribe un nick (primero salen los de su historial)", "Opponent: type a nick (their history's opponents come first)"));
        h2hBusca.putClientProperty("JTextField.showClearButton", true);
        h2hPopup = new JPopupMenu(); h2hPopup.setFocusable(false);
        h2hDebounce = new javax.swing.Timer(400, e -> h2hSugerirDialogo()); h2hDebounce.setRepeats(false);
        h2hBusca.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { h2hDebounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { h2hDebounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { h2hDebounce.restart(); }
        });
        h2hBusca.addActionListener(e -> { if (h2hPopup.getComponentCount() > 0) ((JMenuItem) h2hPopup.getComponent(0)).doClick(); });
        JPanel izq = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        h2hAtrasBtn = new JButton("\u2190"); h2hAtrasBtn.setFocusable(false); h2hAtrasBtn.setMargin(new Insets(1, 7, 1, 7)); h2hAtrasBtn.putClientProperty("JButton.buttonType", "roundRect"); h2hAtrasBtn.setEnabled(false);
        h2hAtrasBtn.setToolTipText(t("Atrás dentro de esta ventana (también el botón lateral del ratón)", "Back within this window (also the mouse's back button)"));
        h2hAtrasBtn.addActionListener(e -> h2hAtras());
        izq.add(h2hAtrasBtn);
        izq.add(new JLabel(t("Cara a cara de ", "Head-to-head of ") + actNombre + t(" contra:", " against:")));
        norte.add(izq, BorderLayout.WEST);
        norte.add(h2hBusca, BorderLayout.CENTER);
        h2hModo = new JComboBox<>(new String[]{ t("Todos los modos", "All modes"), "1v1 RM", t("Equipos RM", "Team RM"), "Empire Wars", "Deathmatch", "Unranked / Custom" });
        h2hModo.setToolTipText(t("Modo de las partidas del cruce (y de la lista de rivales)", "Mode of the pairing's games (and of the opponent list)"));
        h2hModo.addActionListener(e -> { if (h2hNavegando) return; h2hMapaFiltro = null; if (h2hPidActual > 0) h2hFijar(h2hPidActual, h2hNombreActual); else { h2hPintarSugerenciasIniciales(); h2hRegistrar(); } });
        JPanel der = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); der.add(new JLabel(t("Modo:", "Mode:"))); der.add(h2hModo);
        norte.add(der, BorderLayout.EAST);
        h2hDialogo.add(norte, BorderLayout.NORTH);
        h2hCuerpo = new PanelScrollable();
        h2hCuerpo.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));
        JScrollPane sp = new JScrollPane(h2hCuerpo, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        h2hDialogo.add(sp, BorderLayout.CENTER);
        h2hPidActual = 0; h2hMapaFiltro = null; h2hHistorial.clear();
        h2hPintarSugerenciasIniciales();
        h2hRegistrar();
        h2hDialogo.getRootPane().registerKeyboardAction(e -> h2hDialogo.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        h2hDialogo.setSize(1040, 760); h2hDialogo.setLocationRelativeTo(this); h2hDialogo.setVisible(true);
        SwingUtilities.invokeLater(h2hBusca::requestFocusInWindow);
    }

    boolean h2hModoOk(Match m) {
        String sel = H2H_MODOS[Math.max(0, h2hModo == null ? 0 : h2hModo.getSelectedIndex())];
        String modo = m.mode == null ? "" : m.mode.toLowerCase(Locale.ROOT);
        boolean unranked = modo.contains("unranked") || modo.contains("custom");
        return switch (sel) {
            case "1v1" -> m.players.size() == 2 && !modo.contains("empire") && !modo.contains("death") && !unranked;
            case "tg" -> m.players.size() > 2 && !modo.contains("empire") && !modo.contains("death") && !unranked;
            case "ew" -> modo.contains("empire");
            case "dm" -> modo.contains("death");
            case "unranked" -> unranked;
            default -> true;
        };
    }

    /** Rivales del historial (los más frecuentes) con partidas y winrate del cruce, para no tener que escribir. */
    void h2hPintarSugerenciasIniciales() {
        h2hCuerpo.removeAll();
        Actividad a = ACTIVIDAD_CACHE.get(actPid);
        Map<Long, Object[]> riv = new HashMap<>();   // pid → {nombre, n, w, conRes}
        if (a != null) for (Match m : a.partidas()) {
            if (!h2hModoOk(m)) continue;
            MatchPlayer yo = null; for (MatchPlayer p : m.players) if (p.id == actPid) yo = p;
            if (yo == null) continue;
            for (MatchPlayer p : m.players) if (p.id != actPid && p.team != yo.team) { Object[] r = riv.computeIfAbsent(p.id, k -> new Object[]{ p.name, 0, 0, 0 }); r[1] = (Integer) r[1] + 1; if (yo.won != null) { r[3] = (Integer) r[3] + 1; if (yo.won) r[2] = (Integer) r[2] + 1; } }
        }
        List<Map.Entry<Long, Object[]>> l = new ArrayList<>(riv.entrySet());
        l.sort((x, y) -> (Integer) y.getValue()[1] - (Integer) x.getValue()[1]);
        h2hCuerpo.add(tituloSeccion(t("Rivales más frecuentes en su historial", "Most frequent opponents in their history") + " · " + t("clic para ver el cruce", "click to see the pairing")));
        JPanel cab = new JPanel(new BorderLayout()); cab.setOpaque(false); cab.setAlignmentX(0f);
        JLabel c1 = new JLabel(t("Partidas", "Games"), SwingConstants.RIGHT), c2 = new JLabel(t("Winrate de ", "Win rate of ") + actNombre, SwingConstants.RIGHT);
        c1.setPreferredSize(new Dimension(70, 16)); c2.setPreferredSize(new Dimension(150, 16)); estiloCab(c1); estiloCab(c2);
        JPanel cabDer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); cabDer.setOpaque(false); cabDer.add(c1); cabDer.add(c2);
        cab.add(cabDer, BorderLayout.EAST);
        h2hCuerpo.add(cab);
        double max = l.isEmpty() ? 1 : (Integer) l.get(0).getValue()[1];
        int n = 0;
        for (Map.Entry<Long, Object[]> en : l) {
            long pid = en.getKey(); String nombre = nombreVisible(pid, (String) en.getValue()[0]); int veces = (Integer) en.getValue()[1], w = (Integer) en.getValue()[2], conRes = (Integer) en.getValue()[3];
            h2hCuerpo.add(filaRival(pid, nombre, veces / max, veces, w, conRes));
            if (++n >= 25) break;
        }
        if (l.isEmpty()) { JLabel vac = new JLabel(t("Sin rivales en el historial cargado con este modo.", "No opponents in the loaded history with this mode.")); vac.setForeground(Color.GRAY); vac.setAlignmentX(0f); h2hCuerpo.add(vac); }
        h2hCuerpo.revalidate(); h2hCuerpo.repaint();
    }

    /** Fila de rival: bandera · nombre · barra · partidas · winrate (color). Clic: el cruce; botón central: su perfil en pestaña nueva. */
    JPanel filaRival(long pid, String nombre, double fraccion, int n, int w, int conRes) {
        JPanel f = new JPanel(new BorderLayout(8, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int x0 = 300, ancho = Math.max(40, getWidth() - x0 - 250);
                g.setColor(new Color(128, 128, 128, 40)); g.fillRoundRect(x0, getHeight() / 2 - 4, ancho, 8, 4, 4);
                g.setColor(new Color(90, 140, 220, 200)); g.fillRoundRect(x0, getHeight() / 2 - 4, (int) (ancho * fraccion), 8, 4, 4);
            }
        };
        f.setOpaque(false); f.setAlignmentX(0f); f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        f.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JLabel nom = new JLabel(nombre, iconoBandera(paisDe(pid)), SwingConstants.LEFT); nom.setIconTextGap(6); nom.setPreferredSize(new Dimension(280, 20)); nom.setFont(nom.getFont().deriveFont(Font.BOLD, 12.5f));
        f.add(nom, BorderLayout.WEST);
        JPanel der = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); der.setOpaque(false);
        JLabel ln = new JLabel(miles(n), SwingConstants.RIGHT); ln.setPreferredSize(new Dimension(70, 20));
        JLabel lw = new JLabel(conRes > 0 ? w + "-" + (conRes - w) + "  ·  " + pct1(100.0 * w / conRes) : "-", SwingConstants.RIGHT); lw.setPreferredSize(new Dimension(150, 20)); lw.setFont(lw.getFont().deriveFont(Font.BOLD)); lw.setForeground(conRes > 0 ? colorWr(w, conRes) : Color.GRAY);
        der.add(ln); der.add(lw);
        f.add(der, BorderLayout.EAST);
        f.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        f.setToolTipText(t("Clic: ver el cruce · botón central: su perfil en pestaña nueva · clic derecho: más", "Click: see the pairing · middle button: their profile in a new tab · right-click: more"));
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isMiddleMouseButton(e)) abrirPerfilEnPestana(pid, nombre); else if (SwingUtilities.isLeftMouseButton(e)) h2hFijar(pid, nombre); }
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) menuContextualJugador(pid, nombre, e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) menuContextualJugador(pid, nombre, e); }
        };
        f.addMouseListener(ma); nom.addMouseListener(ma);
        return f;
    }

    void h2hSugerirDialogo() {
        String q = h2hBusca.getText().trim();
        h2hPopup.setVisible(false); h2hPopup.removeAll();
        if (q.length() < 2) { if (q.isEmpty() && h2hPidActual == 0) h2hPintarSugerenciasIniciales(); return; }
        Actividad a = ACTIVIDAD_CACHE.get(actPid);
        Map<Long, String> locales = new LinkedHashMap<>();
        if (a != null) for (Match m : a.partidas()) for (MatchPlayer p : m.players) if (p.id != actPid && p.name != null && p.name.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) locales.putIfAbsent(p.id, p.name);
        int n = 0;
        for (Map.Entry<Long, String> en : locales.entrySet()) { long pid = en.getKey(); String nombre = nombreVisible(pid, en.getValue()); JMenuItem it = new JMenuItem(nombre + t("  (en su historial)", "  (in their history)"), iconoBandera(paisDe(pid))); it.addActionListener(x -> h2hFijar(pid, nombre)); h2hPopup.add(it); if (++n >= 6) break; }
        if (n > 0 && h2hBusca.isShowing()) h2hPopup.show(h2hBusca, 0, h2hBusca.getHeight());
        new Thread(() -> {
            List<String[]> res = sugerirPerfiles(q);
            SwingUtilities.invokeLater(() -> {
                if (h2hBusca == null || !q.equals(h2hBusca.getText().trim())) return;
                int k = 0;
                for (String[] r : res) { long pid = Long.parseLong(r[0]); if (locales.containsKey(pid) || pid == actPid) continue; JMenuItem it = new JMenuItem(r[2]); String nombre = r[1]; it.addActionListener(x -> h2hFijar(pid, nombre)); h2hPopup.add(it); if (++k >= 6) break; }
                if (h2hPopup.getComponentCount() > 0 && h2hBusca.isShowing()) h2hPopup.show(h2hBusca, 0, h2hBusca.getHeight());
            });
        }, "h2h-sugerir").start();
    }

    String formaHtml(List<Boolean> ultimos) {
        StringBuilder b = new StringBuilder("<html>");
        for (Boolean g : ultimos) b.append(b.length() > 6 ? " " : "").append("<font color='").append(colorHex(colorWr(g ? 100 : 0, 100))).append("'>").append(g ? t("V", "W") : t("D", "L")).append("</font>");
        return b.append("</html>").toString();
    }
    /** «Todas las partidas»: el año completo del perfil (del paquete de sfr-data o de lo cargado por API) en la pestaña Partidas, con todo lo de la tabla: clic derecho, rec, enviar, resultado. */
    void verHistorialEnTabla(long pid, String nombre) {
        Actividad a = ACTIVIDAD_CACHE.get(pid);
        if (a == null || a.partidas().isEmpty()) { status.setText(t("Sin historial cargado para este perfil.", "No history loaded for this profile.")); return; }
        List<Match> todas = new ArrayList<>(a.partidas());
        for (Match m : todas) if (m.refId == 0) m.refId = pid;
        SUJETOS.clear(); SUJETOS.add(pid);
        vistaDeSujetos = vistaActualId();
        refrescarSujetos(List.of(new Player(pid, nombre, "", 0)), false);
        all.clear(); all.addAll(todas);
        playersList.clearSelection();
        refreshModeCombo();
        applyFilters();
        mostrarGuiaVacia(false);
        mostrarDirectos(false);
        historialEnTabla = pid;
        status.setText(miles(todas.size()) + t(" partidas · histórico de ", " games · history of ") + nombre + (a.completo() ? t(" · último año · de sfr-data", " · last year · from sfr-data") : t(" · cargadas hasta ahora", " · loaded so far")) + t(" · sin resultado hasta que lo pidas", " · no result until you ask"));
    }
    long historialEnTabla;   // pid cuyo histórico está en la tabla (para que la pestaña Partidas no vuelva a buscar)
    /** Carga en la pestaña Partidas los enfrentamientos del cruce (del historial ya descargado): la tabla los enseña sin resultado. */
    void verPartidasEntre(List<Match> cruce, long rivalPid, String rivalNombre) {
        if (cruce.isEmpty()) return;
        for (Match m : cruce) if (m.refId == 0) m.refId = actPid;
        SUJETOS.clear(); SUJETOS.add(actPid);
        vistaDeSujetos = vistaActualId();
        refrescarSujetos(List.of(new Player(actPid, actNombre, "", 0)), false);
        all.clear(); all.addAll(cruce);
        playersList.clearSelection();
        refreshModeCombo();
        applyFilters();
        mostrarGuiaVacia(false);
        mostrarDirectos(false);
        if (h2hDialogo != null) { h2hDialogo.setVisible(false); }   // el diálogo se aparta; la app queda delante, en la pestaña Partidas
        toFront(); requestFocus();
        status.setText(cruce.size() + t(" partidas entre ", " games between ") + actNombre + t(" y ", " and ") + rivalNombre + t(" · cargadas en la pestaña Partidas (sin resultado hasta que lo pidas).", " · loaded in the Games tab (no result until you ask for it)."));
    }

    /** La ficha del cruce con un rival concreto. */
    void h2hFijar(long rivalPid, String rivalNombre) {
        h2hPopup.setVisible(false);
        h2hPidActual = rivalPid; h2hNombreActual = rivalNombre;
        if (!rivalNombre.equals(h2hBusca.getText().trim())) h2hBusca.setText(rivalNombre);
        h2hRegistrar();
        Actividad a = ACTIVIDAD_CACHE.get(actPid);
        h2hCuerpo.removeAll();
        if (a == null) return;
        List<Match> cruce = new ArrayList<>(), juntos = new ArrayList<>();
        int w = 0, l = 0, sinRes = 0, wJ = 0, lJ = 0; Instant primero = null, ultimo = null;
        List<Boolean> ultimos = new ArrayList<>(); int rachaActual = 0; Boolean rachaGana = null; boolean rachaViva = true;
        Map<String, int[]> porMapa = new HashMap<>(), porCivs = new HashMap<>(), civYo = new HashMap<>(), civEl = new HashMap<>(), porModo = new LinkedHashMap<>();
        long eloYoSum = 0, eloElSum = 0; int eloN = 0; long durSum = 0; int durN = 0; long durMax = 0, durMin = Long.MAX_VALUE;
        List<Match> ordenCrono = new ArrayList<>();
        for (Match m : a.partidas()) {   // llegan de más nueva a más vieja
            if (!h2hModoOk(m)) continue;
            MatchPlayer yo = null, el = null;
            for (MatchPlayer p : m.players) { if (p.id == actPid) yo = p; else if (p.id == rivalPid) el = p; }
            if (yo == null || el == null) continue;
            if (yo.team == el.team) { juntos.add(m); if (yo.won != null) { if (yo.won) wJ++; else lJ++; } continue; }
            if (h2hMapaFiltro != null && !h2hMapaFiltro.equals(m.map)) continue;
            cruce.add(m); ordenCrono.add(m);
            if (m.started != null) { if (ultimo == null) ultimo = m.started; primero = m.started; }
            String modoL = m.mode == null ? "" : m.mode.toLowerCase(Locale.ROOT);
            boolean unoRanked = m.players.size() == 2 && !modoL.contains("empire") && !modoL.contains("death") && !modoL.contains("unranked") && !modoL.contains("custom");
            if (unoRanked && yo.rating != null && el.rating != null) { eloYoSum += yo.rating; eloElSum += el.rating; eloN++; }   // el ELO del cruce, solo con 1v1 RM ranked
            if (m.started != null && m.finished != null) { long d = Duration.between(m.started, m.finished).getSeconds(); if (d > 120) { durSum += d; durN++; durMax = Math.max(durMax, d); durMin = Math.min(durMin, d); } }
            Boolean gana = yo.won;
            if (gana == null) { sinRes++; continue; }
            if (gana) w++; else l++;
            if (ultimos.size() < 5) ultimos.add(gana);
            if (rachaViva) { if (rachaGana == null) { rachaGana = gana; rachaActual = 1; } else if (rachaGana == gana) rachaActual++; else rachaViva = false; }
            String mapa = m.map == null ? "?" : m.map;
            int[] pm = porMapa.computeIfAbsent(mapa, k -> new int[2]); pm[0]++; if (gana) pm[1]++;
            if (yo.civ != null && el.civ != null) { int[] pc = porCivs.computeIfAbsent(yo.civ + " vs " + el.civ, k -> new int[2]); pc[0]++; if (gana) pc[1]++; }
            if (yo.civ != null) { int[] c = civYo.computeIfAbsent(yo.civ, k -> new int[2]); c[0]++; if (gana) c[1]++; }
            if (el.civ != null) { int[] c = civEl.computeIfAbsent(el.civ, k -> new int[2]); c[0]++; if (!gana) c[1]++; }
        }
        int conRes = w + l;
        // cabecera: título + botones
        JPanel cab = new JPanel(new BorderLayout(10, 0)); cab.setOpaque(false); cab.setAlignmentX(0f);
        JPanel tituloFila = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); tituloFila.setOpaque(false);
        JLabel tYo = new JLabel("<html><span style='font-size:16px'><b>" + escapeHtml(actNombre) + "</b> vs</span></html>");
        JLabel tEl = new JLabel("<html><span style='font-size:16px'><b><u>" + escapeHtml(rivalNombre) + "</u></b></span></html>", iconoBandera(paisDe(rivalPid), 18), SwingConstants.LEFT);
        tEl.setIconTextGap(6); tEl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tEl.setToolTipText(t("Clic o botón central: su perfil en pestaña nueva · clic derecho: más", "Click or middle button: their profile in a new tab · right-click: more"));
        tEl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) abrirPerfilEnPestana(rivalPid, rivalNombre); }
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) menuContextualJugador(rivalPid, rivalNombre, e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) menuContextualJugador(rivalPid, rivalNombre, e); }
        });
        tituloFila.add(tYo); tituloFila.add(tEl);
        if (h2hMapaFiltro != null) {
            JLabel tMapa = new JLabel("<html><span style='color:gray;font-size:13px'>· " + escapeHtml(h2hMapaFiltro) + " <u>×</u></span></html>");
            tMapa.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); tMapa.setToolTipText(t("Clic: quitar el filtro de mapa", "Click: remove the map filter"));
            tMapa.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { h2hMapaFiltro = null; h2hFijar(rivalPid, rivalNombre); } });
            tituloFila.add(tMapa);
        }
        cab.add(tituloFila, BorderLayout.CENTER);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); botones.setOpaque(false);
        JButton perfN = new JButton(t("Su perfil en pestaña nueva", "Their profile in a new tab")); perfN.setFocusable(false); perfN.setMargin(new Insets(1, 8, 1, 8)); perfN.putClientProperty("JButton.buttonType", "roundRect");
        perfN.addActionListener(e -> abrirPerfilEnPestana(rivalPid, rivalNombre));
        botones.add(perfN);
        JButton verP = new JButton(t("Ver sus partidas", "View their games")); verP.setFocusable(false); verP.setMargin(new Insets(1, 8, 1, 8)); verP.putClientProperty("JButton.buttonType", "roundRect");
        verP.addActionListener(e -> { Player p = new Player(rivalPid, rivalNombre, grupoDestino()); objetivoForzado = p; invitado = p; vistaDelInvitado = vistaActualId(); playersList.clearSelection(); aplicarFiltroGrupo(); mostrarDirectos(false); fetchMatches(fetchBtn); });
        botones.add(verP);
        Match viva = VIVO_PARTIDA.get(rivalPid);
        if (viva != null && enCursoReal(viva) && viva.id > 0) { JButton esp = new JButton(t("Espectar", "Spectate")); esp.setFocusable(false); esp.setMargin(new Insets(1, 8, 1, 8)); esp.putClientProperty("JButton.buttonType", "roundRect"); esp.addActionListener(e -> { if (confirmarEspectar(rivalNombre)) espectarPartida(viva.id); }); botones.add(esp); }
        cab.add(botones, BorderLayout.EAST);
        h2hCuerpo.add(cab);
        if (conRes > 0) {
            String mejorMapa = null; int[] mm = null; for (Map.Entry<String, int[]> en : porMapa.entrySet()) if (en.getValue()[0] >= 2 && (mm == null || (double) en.getValue()[1] / en.getValue()[0] > (double) mm[1] / mm[0])) { mejorMapa = en.getKey(); mm = en.getValue(); }
            String civFuerteEl = null; int[] ce = null; for (Map.Entry<String, int[]> en : civEl.entrySet()) if (en.getValue()[0] >= 2 && (ce == null || (double) en.getValue()[1] / en.getValue()[0] > (double) ce[1] / ce[0])) { civFuerteEl = en.getKey(); ce = en.getValue(); }
            String quien = w > l ? actNombre : l > w ? rivalNombre : null;
            StringBuilder fr = new StringBuilder();
            fr.append(quien == null ? t("Cruce igualado: ", "Even pairing: ") + w + "-" + l : quien + t(" domina el cruce: ", " leads the pairing: ") + (w > l ? w + "-" + l : l + "-" + w));
            if (rachaActual >= 2) fr.append(", ").append(rachaActual).append(rachaGana ? t(" victorias seguidas de ", " wins in a row for ") + actNombre : t(" seguidas para ", " in a row for ") + rivalNombre);
            if (mejorMapa != null && mm[0] >= 2 && mm[1] > mm[0] - mm[1]) fr.append(t("; mejor en ", "; best on ")).append(mejorMapa).append(" (").append(mm[1]).append("-").append(mm[0] - mm[1]).append(")");
            if (civFuerteEl != null && ce[1] > ce[0] - ce[1]) fr.append("; ").append(rivalNombre).append(t(" le gana con ", " beats them with ")).append(civFuerteEl).append(" (").append(ce[1]).append("-").append(ce[0] - ce[1]).append(")");
            fr.append(".");
            JLabel resumen = new JLabel(fr.toString()); resumen.setFont(resumen.getFont().deriveFont(Font.PLAIN, 12.5f)); resumen.setAlignmentX(0f); resumen.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 0));
            h2hCuerpo.add(resumen);
        }
        h2hCuerpo.add(Box.createVerticalStrut(6));
        // fichas de ambos, lado a lado (ELO y puesto actuales)
        JPanel fichas = new JPanel(new GridLayout(1, 2, 12, 0)); fichas.setOpaque(false); fichas.setAlignmentX(0f); fichas.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        fichas.add(fichaJugadorH2h(actPid, actNombre)); fichas.add(fichaJugadorH2h(rivalPid, rivalNombre));
        h2hCuerpo.add(fichas);
        h2hCuerpo.add(Box.createVerticalStrut(8));
        if (cruce.isEmpty()) {
            JLabel vac = new JLabel(t("No se han enfrentado en el historial cargado (", "No games between them in the loaded history (") + (a.completo() ? t("último año", "last year") : miles(a.partidas().size()) + t(" partidas", " games")) + (h2hMapaFiltro != null ? ", " + h2hMapaFiltro : "") + ")."); vac.setForeground(Color.GRAY); vac.setAlignmentX(0f); h2hCuerpo.add(vac);
            if (!juntos.isEmpty()) { h2hCuerpo.add(Box.createVerticalStrut(6)); JLabel jl = new JLabel(t("Como aliados: ", "As allies: ") + juntos.size() + t(" partidas · ", " games · ") + wJ + "-" + lJ); jl.setAlignmentX(0f); h2hCuerpo.add(jl); }
            h2hCuerpo.revalidate(); h2hCuerpo.repaint(); return;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy", "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES"));
        long diasUltimo = ultimo == null ? -1 : Duration.between(ultimo, Instant.now()).toDays();
        double meses = primero == null || ultimo == null ? 1 : Math.max(1, Duration.between(primero, ultimo).toDays() / 30.0);
        JPanel fila1 = new JPanel(new GridLayout(1, 0, 8, 0)); fila1.setOpaque(false); fila1.setAlignmentX(0f); fila1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        fila1.add(tarjeta(t("Partidas", "Games"), miles(cruce.size()), (sinRes > 0 ? sinRes + t(" sin resultado · ", " without result · ") : "") + String.format(Locale.ROOT, "%.1f", cruce.size() / meses).replace('.', ',') + t(" al mes", " per month")));
        fila1.add(tarjeta(t("Balance", "Record"), w + "-" + l, conRes > 0 ? pct1(100.0 * w / conRes) + t(" para ", " for ") + actNombre : t("sin resultados", "no results")));
        fila1.add(tarjeta(t("Forma", "Form"), ultimos.isEmpty() ? "-" : formaHtml(ultimos), t("últimas ", "last ") + ultimos.size() + t(", la más reciente a la izquierda", ", most recent on the left")));
        fila1.add(tarjeta(t("Racha actual", "Current streak"), rachaActual == 0 ? "-" : String.valueOf(rachaActual), rachaActual == 0 ? "" : (rachaGana ? t("seguidas de ", "in a row for ") + actNombre : t("seguidas de ", "in a row for ") + rivalNombre)));
        fila1.add(tarjeta(t("Último cruce", "Last game"), diasUltimo < 0 ? "-" : diasUltimo == 0 ? t("hoy", "today") : t("hace ", "") + diasUltimo + t(" días", " days ago"), (primero == null ? "" : t("primero: ", "first: ") + primero.atZone(ZoneId.systemDefault()).format(fmt)) + (ultimo == null ? "" : " · " + ultimo.atZone(ZoneId.systemDefault()).format(fmt))));
        h2hCuerpo.add(fila1);
        h2hCuerpo.add(Box.createVerticalStrut(6));
        JPanel fila2 = new JPanel(new GridLayout(1, 0, 8, 0)); fila2.setOpaque(false); fila2.setAlignmentX(0f); fila2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        if (!juntos.isEmpty()) fila2.add(tarjeta(t("Como aliados", "As allies"), miles(juntos.size()), wJ + "-" + lJ + t(" juntos", " together")));
        if (eloN > 0) fila2.add(tarjeta(t("ELO del cruce (1v1 RM)", "Pairing ELO (1v1 RM)"), Math.round(eloYoSum / (double) eloN) + " / " + Math.round(eloElSum / (double) eloN), t("media de cada uno · diferencia ", "average of each · gap ") + (Math.round(eloYoSum / (double) eloN) - Math.round(eloElSum / (double) eloN) >= 0 ? "+" : "") + (Math.round(eloYoSum / (double) eloN) - Math.round(eloElSum / (double) eloN))));
        if (durN > 0) fila2.add(tarjeta(t("Duración media", "Average duration"), duracionMedia(durSum, durN), t("de las partidas entre ambos", "of the games between them")));
        if (fila2.getComponentCount() > 0) h2hCuerpo.add(fila2);
        h2hCuerpo.add(Box.createVerticalStrut(10));
        JPanel columnas = new JPanel(new GridLayout(1, 2, 16, 0)); columnas.setOpaque(false); columnas.setAlignmentX(0f);
        JPanel c1 = new JPanel(); c1.setLayout(new BoxLayout(c1, BoxLayout.Y_AXIS)); c1.setOpaque(false);
        JPanel c2 = new JPanel(); c2.setLayout(new BoxLayout(c2, BoxLayout.Y_AXIS)); c2.setOpaque(false);
        Map<String, Runnable> clicMapa = new HashMap<>(); for (String k : porMapa.keySet()) clicMapa.put(k, () -> { h2hMapaFiltro = k; h2hFijar(rivalPid, rivalNombre); });
        pintarListaAgg(c1, t("Winrate por mapa · ", "Win rate by map · ") + actNombre + t(" · clic: filtrar", " · click: filter"), porMapa, 10, k -> k, h2hMapaFiltro == null ? clicMapa : null, k -> iconoMapa(k, 18));
        pintarListaAgg(c2, t("Civ contra civ", "Civ vs civ"), porCivs, 10, k -> k, null, k -> iconoCiv(claveCivDeNombre(k.substring(0, k.indexOf(" vs "))), 18));
        columnas.add(c1); columnas.add(c2);
        h2hCuerpo.add(columnas);
        h2hCuerpo.add(Box.createVerticalStrut(8));
        JPanel columnas2 = new JPanel(new GridLayout(1, 2, 16, 0)); columnas2.setOpaque(false); columnas2.setAlignmentX(0f);
        JPanel c3 = new JPanel(); c3.setLayout(new BoxLayout(c3, BoxLayout.Y_AXIS)); c3.setOpaque(false);
        JPanel c4 = new JPanel(); c4.setLayout(new BoxLayout(c4, BoxLayout.Y_AXIS)); c4.setOpaque(false);
        pintarListaAgg(c3, t("Mejores civs · ", "Best civs · ") + actNombre, civYo, 5, k -> k, null, k -> iconoCiv(claveCivDeNombre(k), 18));
        pintarListaAgg(c4, t("Mejores civs · ", "Best civs · ") + rivalNombre, civEl, 5, k -> k, null, k -> iconoCiv(claveCivDeNombre(k), 18));
        columnas2.add(c3); columnas2.add(c4);
        h2hCuerpo.add(columnas2);
        h2hCuerpo.add(Box.createVerticalStrut(10));
        // cada uno por su lado: dos columnas alineadas, el rival desde sfr-data (sin llamadas)
        JPanel comparacion = new JPanel(); comparacion.setLayout(new BoxLayout(comparacion, BoxLayout.Y_AXIS)); comparacion.setOpaque(false); comparacion.setAlignmentX(0f);
        comparacion.add(tituloSeccion(t("Cada uno por su lado", "Each on their own") + " \u00B7 " + t("último año", "last year")));
        JLabel cargando = new JLabel(t("cargando el perfil del rival…", "loading the opponent's profile…")); cargando.setForeground(colorSecundario()); cargando.setAlignmentX(0f); comparacion.add(cargando);
        h2hCuerpo.add(comparacion);
        h2hCuerpo.add(Box.createVerticalStrut(10));
        new Thread(() -> {
            Actividad ar = ACTIVIDAD_CACHE.get(rivalPid);
            if (ar == null) { try { cargarEloAyer(); Map<String, Object> sh = perfilesShard(rivalPid); if (sh != null) { ar = actividadDesdeShard(rivalPid, sh); if (ar != null) ACTIVIDAD_CACHE.put(rivalPid, ar); } } catch (Exception ex) { log("h2h rival: " + causa(ex)); } }
            final Actividad arF = ar;
            SwingUtilities.invokeLater(() -> {
                if (h2hPidActual != rivalPid) return;
                comparacion.remove(cargando);
                if (arF == null) { JLabel no = new JLabel(t("El rival no está en sfr-data; abre su perfil para cargarlo por la API.", "The opponent isn't in sfr-data; open their profile to load it from the API.")); no.setForeground(colorSecundario()); no.setAlignmentX(0f); comparacion.add(no); }
                else {
                    Map<String, Map<String, int[]>> mio = resumenLado(a, actPid), suyo = resumenLado(arF, rivalPid);
                    for (String[] sec : new String[][]{ { "mapas", t("Winrate por mapa", "Win rate by map") }, { "civs", t("Winrate por civ", "Win rate by civ") }, { "duracion", t("Por duración (min)", "By duration (min)") }, { "posicion", t("Pocket o flanco", "Pocket or flank") } }) {
                        JPanel par = new JPanel(new GridLayout(1, 2, 16, 0)); par.setOpaque(false); par.setAlignmentX(0f);
                        JPanel l1 = new JPanel(); l1.setLayout(new BoxLayout(l1, BoxLayout.Y_AXIS)); l1.setOpaque(false);
                        JPanel l2 = new JPanel(); l2.setLayout(new BoxLayout(l2, BoxLayout.Y_AXIS)); l2.setOpaque(false);
                        java.util.function.Function<String, Icon> ic = "mapas".equals(sec[0]) ? k -> iconoMapa(k, 18) : "civs".equals(sec[0]) ? k -> iconoCiv(claveCivDeNombre(k), 18) : null;
                        pintarListaAgg(l1, sec[1] + " \u00B7 " + actNombre, mio.get(sec[0]), 5, k -> k, null, ic);
                        pintarListaAgg(l2, sec[1] + " \u00B7 " + rivalNombre, suyo.get(sec[0]), 5, k -> k, null, ic);
                        par.add(l1); par.add(l2);
                        comparacion.add(par); comparacion.add(Box.createVerticalStrut(6));
                    }
                }
                comparacion.revalidate(); comparacion.repaint();
            });
        }, "h2h-comparar").start();
        JButton verEntre = new JButton(t("Ver las partidas entre ambos en Partidas (sin spoilers)", "See the games between them in Games (spoiler-free)"));
        verEntre.setFocusable(false); verEntre.setMargin(new Insets(2, 10, 2, 10)); verEntre.putClientProperty("JButton.buttonType", "roundRect"); verEntre.setAlignmentX(0f);
        verEntre.setToolTipText(t("Carga en la pestaña Partidas los enfrentamientos del historial (con el modo y el mapa elegidos aquí): sin resultado y con la rec a un clic", "Loads the pairing's games from the history into the Games tab (with the mode and map chosen here): no result, rec one click away"));
        final List<Match> cruceF = new ArrayList<>(cruce);
        verEntre.addActionListener(e -> verPartidasEntre(cruceF, rivalPid, rivalNombre));
        h2hCuerpo.add(verEntre);
        h2hCuerpo.revalidate(); h2hCuerpo.repaint();
        subirArriba(h2hCuerpo);
    }

    /** Resumen de un jugador para la comparación: winrate por mapa, por civ, por duración y pocket/flanco, respetando el modo elegido en la ventana. */
    Map<String, Map<String, int[]>> resumenLado(Actividad a, long pid) {
        Map<String, int[]> mapas = new HashMap<>(), civs = new HashMap<>(), dur = new LinkedHashMap<>(), pos = new LinkedHashMap<>();
        for (String d : DURACION_TRAMOS) dur.put(d, new int[2]);
        for (Match m : a.partidas()) {
            if (!h2hModoOk(m)) continue;
            MatchPlayer yo = null; for (MatchPlayer p : m.players) if (p.id == pid) yo = p;
            if (yo == null || yo.won == null) continue;
            boolean g = yo.won;
            if (m.map != null) { int[] x = mapas.computeIfAbsent(m.map, k -> new int[2]); x[0]++; if (g) x[1]++; }
            if (yo.civ != null) { int[] x = civs.computeIfAbsent(yo.civ, k -> new int[2]); x[0]++; if (g) x[1]++; }
            int td = tramoDuracion(m); if (td >= 0) { int[] x = dur.get(DURACION_TRAMOS[td]); x[0]++; if (g) x[1]++; }
            String p = posicionEnEquipo(m, yo); if (p != null) { int[] x = pos.computeIfAbsent(posicionNombre(p), k -> new int[2]); x[0]++; if (g) x[1]++; }
        }
        dur.values().removeIf(v -> v[0] == 0);
        Map<String, Map<String, int[]>> out = new HashMap<>(); out.put("mapas", mapas); out.put("civs", civs); out.put("duracion", dur); out.put("posicion", pos);
        return out;
    }
    /** Ficha compacta de un jugador para el cara a cara: bandera, nick, ELO y puesto en 1v1 y equipos (se pide en segundo plano si no está). */
    JPanel fichaJugadorH2h(long pid, String nombre) {
        JPanel f = new JPanel(new BorderLayout(8, 0)); f.setOpaque(false);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(128, 128, 128, 70), 1, true), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JLabel l = new JLabel(nombre, iconoBandera(paisDe(pid), 18), SwingConstants.LEFT); l.setIconTextGap(6); l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        f.add(l, BorderLayout.WEST);
        JLabel datos = new JLabel();
        datos.setFont(datos.getFont().deriveFont(Font.PLAIN, 12f));
        Object[] perfil = PERFIL_CACHE.get(pid);
        if (perfil == null && ELO_AYER.get(pid) != null) {   // del snapshot nocturno: sin llamada
            int[] sn = ELO_AYER.get(pid); Map<String, int[]> mm = new HashMap<>();
            if (sn[0] > 0) mm.put("rm_1v1", new int[]{ sn[0], 0, 0, 0, 0 }); if (sn[2] > 0) mm.put("rm_team", new int[]{ sn[2], 0, 0, 0, 0 });
            perfil = new Object[]{ System.currentTimeMillis(), mm, NOMBRES_AYER.getOrDefault(pid, new String[]{ "", "" })[1], "", 0L };
        }
        if (perfil == null) {
            datos.setText(t("cargando…", "loading…")); datos.setForeground(Color.GRAY);
            new Thread(() -> { Object[] p2 = perfilLadders(pid); SwingUtilities.invokeLater(() -> pintarDatosFicha(datos, p2)); }, "h2h-ficha").start();
        } else pintarDatosFicha(datos, perfil);
        f.add(datos, BorderLayout.CENTER);
        return f;
    }
    void pintarDatosFicha(JLabel datos, Object[] perfil) {
        if (perfil == null) { datos.setText(""); return; }
        @SuppressWarnings("unchecked") Map<String, int[]> m = (Map<String, int[]>) perfil[1];
        StringBuilder h = new StringBuilder("<html>");
        for (String lb : new String[]{ "rm_1v1", "rm_team" }) { int[] v = m.get(lb); if (v == null || v[0] <= 0) continue; if (h.length() > 6) h.append("&nbsp;&nbsp;·&nbsp;&nbsp;"); h.append("<span style='color:gray'>").append(escapeHtml(ladderNombre(lb))).append("</span> <b>").append(v[0]).append("</b>").append(v[1] > 0 ? " <span style='color:gray'>#" + miles(v[1]) + "</span>" : ""); }
        datos.setText(h.append("</html>").toString()); datos.setForeground(UIManager.getColor("Label.foreground"));
    }

    // ----- Cabecera del perfil: cuentas vinculadas visibles (con ELO) y menú contextual en el nombre -----
    void actPintarVinculadas() {
        if (actVinculadasPanel == null) return;
        long pid = actPid;
        List<Object[]> v = VINCULADAS_CACHE.get(pid);
        actVinculadasPanel.removeAll();
        JLabel etq = new JLabel(t("Cuentas vinculadas:", "Linked accounts:")); etq.setFont(etq.getFont().deriveFont(Font.BOLD, 13f));
        actVinculadasPanel.add(etq);
        if (v == null && !vinculadasPedidas.contains(pid)) {   // no se consulta sola: un clic, y se recuerda para la sesión
            JLabel b = new JLabel("<html><u>" + t("comprobar", "check") + "</u></html>"); b.setForeground(colorSecundario()); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setToolTipText(t("Busca cuentas vinculadas (una llamada, más una por cuenta para su ELO)", "Looks up linked accounts (one request, plus one per account for its ELO)"));
            b.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { vinculadasPedidas.add(pid); actPintarVinculadas(); } });
            actVinculadasPanel.add(b);
        } else if (v == null) {
            JLabel b = new JLabel(t("buscando…", "looking up…")); b.setForeground(Color.GRAY); actVinculadasPanel.add(b);
            new Thread(() -> {
                List<Object[]> res;
                try { res = cuentasVinculadas(pid); } catch (Exception ex) { res = List.of(); }
                VINCULADAS_CACHE.put(pid, res);
                for (Object[] x : res) { long vid = (Long) x[0]; if (!ELO_VINC.containsKey(vid)) { Integer e = eloDeLadder(vid); ELO_VINC.put(vid, e == null ? 0 : e); } }
                SwingUtilities.invokeLater(() -> { if (actPid == pid) actPintarVinculadas(); });
            }, "perfil-vinculadas").start();
        } else if (v.isEmpty()) {
            JLabel b = new JLabel(t("ninguna conocida", "none known")); b.setForeground(Color.GRAY); actVinculadasPanel.add(b);
        } else {
            for (Object[] x : v) {
                long vid = (Long) x[0]; String nombre = nombreVisible(vid, String.valueOf(x[1]));
                Integer elo = ELO_VINC.get(vid);
                JLabel l = new JLabel("<html><u>" + escapeHtml(nombre) + "</u>" + (elo != null && elo > 0 ? " <span style='color:gray'>(" + elo + ")</span>" : "") + (containsPlayerId(vid) ? " <span style='color:gray'>\u2605</span>" : "") + "</html>", iconoBandera(x.length > 2 && x[2] != null ? String.valueOf(x[2]) : paisDe(vid)), SwingConstants.LEFT);
                l.setIconTextGap(4); l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
                l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                l.setToolTipText(t("Clic: abrir su perfil · botón central o clic derecho: en pestaña nueva", "Click: open their profile · middle or right button: in a new tab"));
                l.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) abrirPerfil(vid, nombre); else abrirPerfilEnPestana(vid, nombre); } });
                actVinculadasPanel.add(l);
            }
        }
        actVinculadasPanel.revalidate(); actVinculadasPanel.repaint();
    }
    JPanel actVinculadasPanel; JLabel actNotaLinea; String actNombreReal = ""; final Set<Long> vinculadasPedidas = new HashSet<>();

    void actMenuNombre(MouseEvent e, Component sobre) {
        long pid = actPid; String nombre = actNombre;
        if (pid <= 0) return;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem perfN = new JMenuItem(t("Perfil en pestaña nueva", "Profile in a new tab")); perfN.addActionListener(a -> abrirPerfilEnPestana(pid, nombre)); menu.add(perfN);
        menu.add(menuPerfilNavegador(pid));
        if (twitchLive.containsKey(pid)) { JMenuItem tw = new JMenuItem(t("Ver directo en Twitch", "Watch live on Twitch")); tw.addActionListener(a -> abrirUrl("https://twitch.tv/" + twitchLive.get(pid)[0])); menu.add(tw); }
        JMenu enPartida = menuEnPartida(pid); if (enPartida != null) menu.add(enPartida);
        menu.addSeparator();
        if (!containsPlayerId(pid)) {
            JMenu anadir = new JMenu(t("Añadir a mi watchlist", "Add to my watchlist"));
            Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); gs.add(GRUPO_GENERAL); for (Player x : todosJugadores) gs.add(x.grupo()); gs.addAll(gruposConfig());
            for (String g : gs) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> ficharDesdeTop(new Player(pid, nombre, g), g)); anadir.add(it); }
            menu.add(anadir);
        } else { JMenuItem ya = new JMenuItem(t("(ya está en tu watchlist)", "(already in your watchlist)")); ya.setEnabled(false); menu.add(ya); }
        JMenuItem vinc = new JMenuItem(t("Cuentas vinculadas…", "Linked accounts…")); vinc.addActionListener(a -> mostrarVinculadas(pid, nombre)); menu.add(vinc);
        menu.addSeparator();
        JMenuItem alias = new JMenuItem(t("Mostrar como…", "Show as…")); alias.addActionListener(a -> { pedirAlias(pid, nombre); actPintarCabecera(PERFIL_CACHE.get(pid)); }); menu.add(alias);
        JMenuItem nota = new JMenuItem(t("Nota…", "Note…")); nota.addActionListener(a -> { pedirNota(pid, nombre); actPintarCabecera(PERFIL_CACHE.get(pid)); }); menu.add(nota);
        if (notaDe(pid) != null) { JMenuItem bn = new JMenuItem(t("Borrar nota", "Delete note")); bn.addActionListener(a -> { borrarNota(pid, nombre); actPintarCabecera(PERFIL_CACHE.get(pid)); }); menu.add(bn); }
        JMenuItem nicks = new JMenuItem(t("Nicks anteriores…", "Previous names…")); nicks.addActionListener(a -> nicksAnteriores(pid, nombre)); menu.add(nicks);
        menu.show(sobre, e.getX(), e.getY());
    }

    /** Cabecera: país y clan, un chip por ladder con ELO · rango · Top %, máximo y totales, y la forma reciente. */
    @SuppressWarnings("unchecked")
    void actPintarCabecera(Object[] perfil) {
        actChips.removeAll();
        actPintarVinculadas();
        actNotaLinea.setText(notaDe(actPid) != null ? "\u270E " + notaDe(actPid) : (ALIASES.get(actPid) != null && !ALIASES.get(actPid).isBlank() ? t("Mostrado como ", "Shown as ") + ALIASES.get(actPid) + t(" · nick real ", " · real nick ") + actNombreReal : ""));
        actNotaLinea.setVisible(!actNotaLinea.getText().isEmpty());
        String pais = perfil == null ? "" : String.valueOf(perfil[2]), clan = perfil == null || perfil.length < 4 ? "" : String.valueOf(perfil[3]);
        long games = perfil == null || perfil.length < 5 ? 0 : (long) perfil[4];
        StringBuilder sub = new StringBuilder();
        if (!pais.isBlank()) sub.append(pais.toUpperCase(Locale.ROOT));
        if (!clan.isBlank()) sub.append(sub.length() > 0 ? "  ·  " : "").append(t("clan ", "clan ")).append(clan);
        if (games > 0) sub.append(sub.length() > 0 ? "  ·  " : "").append(miles(games)).append(t(" partidas en total", " games in total"));
        String canal = CANAL_DE.get(actPid);
        if (canal != null && !canal.isBlank()) sub.append(sub.length() > 0 ? "  ·  " : "").append("twitch.tv/").append(canal);
        actSubtitulo.setText(sub.length() == 0 ? t("Sin datos de perfil", "No profile data") : sub.toString());
        actSubtitulo.setIcon(iconoBandera(pais)); actSubtitulo.setIconTextGap(6);
        if (perfil != null) {
            Map<String, int[]> m = (Map<String, int[]>) perfil[1];
            for (String lb : LADDER_IDS) {
                int[] v = m.get(lb);
                if (v == null || v[0] <= 0) continue;
                String top = percentilRating(lb, true, v[0]);   // Top % entre los jugadores ACTIVOS (≥10 partidas en 28 días), que es contra quien juegas; si no hay campana de activos, contra todos
                if (top == null && v[1] > 0) top = percentilRango(lb, v[1]);
                StringBuilder val = new StringBuilder("<span style='font-size:13px'>" + v[0] + "</span>");
                if (v[1] > 0) val.append(" <span style='font-weight:normal'>#").append(miles(v[1])).append("</span>");
                if (top != null) val.append(" <span style='color:gray;font-weight:normal'>").append(escapeHtml(top)).append("</span>");
                if (v.length > 2 && v[2] > 0) val.append("<br><span style='font-weight:normal;font-size:10px;color:gray'>").append(t("máx ", "peak ")).append("</span><span style='font-weight:normal;font-size:10px'>").append(v[2]).append("</span>");
                String tip = (v.length > 2 && v[2] > 0 ? t("Máximo ", "Peak ") + v[2] : "") + (v.length > 4 && v[3] + v[4] > 0 ? (v.length > 2 && v[2] > 0 ? " · " : "") + v[3] + "-" + v[4] + t(" en total (", " in total (") + pct1(100.0 * v[3] / (v[3] + v[4])) + ")" : "");
                if (top != null) tip = (tip.isBlank() ? "" : tip + " · ") + t("Top % entre los jugadores activos (al menos una partida en los últimos 28 días); el # es el puesto en el ladder completo", "Top % among active players (at least one game in the last 28 days); # is the rank in the full ladder");
                actChips.add(chipPerfil(ladderNombre(lb), val.toString(), tip.isBlank() ? null : tip));
            }
            for (String lbTot : new String[]{ "rm_1v1", "rm_team" }) {
                int[] tot = m.get(lbTot);
                if (tot == null || tot.length < 5 || tot[3] + tot[4] == 0) continue;
                actChips.add(chipPerfil(ladderNombre(lbTot) + t(" en total", " overall"), "<b>" + miles(tot[3]) + "</b> " + t("victorias", "wins") + " - <b>" + miles(tot[4]) + "</b> " + t("derrotas", "losses") + " · <b>" + escapeHtml(pct1(100.0 * tot[3] / (tot[3] + tot[4]))) + "</b> · " + miles(tot[3] + tot[4]) + t(" partidas", " games"), null));
            }
        }
        actChips.revalidate(); actChips.repaint();
    }

    void actPintar() {
        Actividad a = ACTIVIDAD_CACHE.get(actPid);
        if (a == null) return;
        ZoneId zona = ZoneId.systemDefault();
        LocalDate hoy = LocalDate.now(zona), inicio = hoy.minusDays(ACT_DIAS - 1);
        Map<LocalDate, Integer> porDia = new HashMap<>();
        int[] semana = new int[7], semanaW = new int[7], semanaN = new int[7];
        int[] horas = new int[24], horasW = new int[24], horasN = new int[24];
        Map<String, int[]> meses = new TreeMap<>();
        Map<String, int[]> civs = new HashMap<>(), civsRival = new HashMap<>(), mapas = new HashMap<>(), tramos = new HashMap<>();
        int[] durN = new int[5], durW = new int[5];                                     // winrate por duración (tramos de aoe2insights)
        Map<String, int[]> posicion = new LinkedHashMap<>(), posicionMapa = new LinkedHashMap<>();   // pocket/flanco (3v3 y 4v4) y por mapa
        Map<String, int[]> civs30 = new HashMap<>(), mapas30 = new HashMap<>();          // últimos 30 días
        Instant hace30 = Instant.now().minus(Duration.ofDays(30));
        int eloActualJugador = 0;
        { Object[] pf = PERFIL_CACHE.get(actPid); if (pf != null && pf[1] instanceof Map<?, ?> pm && pm.get("rm_1v1") instanceof int[] v && v[0] > 0) eloActualJugador = v[0]; if (eloActualJugador == 0) { Integer e = eloWatch.get(actPid); if (e != null && e > 0) eloActualJugador = e; } }
        String[] franjas = null;
        Map<Long, Object[]> rivales = new HashMap<>(), aliados = new HashMap<>();   // pid → {nombre, n, w}
        String modoGrafica = "*".equals(actModo) ? modoPrincipal(a) : actModo;   // la gráfica de ELO va siempre por ladder
        List<long[]> serie = new ArrayList<>();   // {epochMs, rating tras la partida, diff}, de más nueva a más vieja (como llega)
        int partidas = 0, conResultado = 0, victorias = 0; long duracion = 0; int conDuracion = 0;
        int f10 = 0, fW = 0, fL = 0, fDiff = 0, fRacha = 0; Boolean fRachaGana = null; boolean fRachaViva = true;
        for (Match m : a.partidas()) {
            if (!"*".equals(actModo) && !actModo.equals(m.mode)) continue;
            if (!enPeriodo(m)) continue;
            MatchPlayer yo = null;
            for (MatchPlayer p : m.players) if (p.id == actPid) { yo = p; break; }
            if (yo == null) continue;
            partidas++;
            ZonedDateTime z = m.started.atZone(zona);
            porDia.merge(z.toLocalDate(), 1, Integer::sum);
            int dow = z.getDayOfWeek().getValue() - 1;
            int h = z.getHour();
            String mes = z.getYear() + "-" + String.format("%02d", z.getMonthValue());
            int[] mm = meses.computeIfAbsent(mes, k -> new int[2]);
            semana[dow]++; horas[h]++; mm[0]++;
            if (yo.ratingDiff != null && m.finished != null && modoGrafica != null && modoGrafica.equals(m.mode) && serie.size() < 100) serie.add(new long[]{ m.started.toEpochMilli(), yo.rating == null ? 0 : yo.rating, yo.ratingDiff });   // el rating de la API no es fiable en algunas partidas: la curva se reconstruye después desde el ELO actual
            Boolean gano = yo.won;
            if (gano != null && m.finished != null) {
                conResultado++; if (gano) victorias++;
                if (f10 < 10) {   // forma de las 10 más recientes
                    f10++;
                    if (gano) fW++; else fL++;
                    if (yo.ratingDiff != null) fDiff += yo.ratingDiff;
                    if (fRachaViva) {
                        if (fRachaGana == null) { fRachaGana = gano; fRacha = 1; }
                        else if (fRachaGana == gano) fRacha++;
                        else fRachaViva = false;
                    }
                }
                semanaN[dow]++; horasN[h]++; if (gano) { semanaW[dow]++; horasW[h]++; mm[1]++; }
                if (m.finished.isAfter(m.started)) { duracion += Duration.between(m.started, m.finished).getSeconds(); conDuracion++; }
                String civ = yo.civ == null || yo.civ.isBlank() ? "?" : yo.civ;
                int[] c = civs.computeIfAbsent(civ, k -> new int[2]); c[0]++; if (gano) c[1]++;
                if (m.map != null && !m.map.isBlank()) { int[] x = mapas.computeIfAbsent(m.map, k -> new int[2]); x[0]++; if (gano) x[1]++; }
                if (m.started != null && m.started.isAfter(hace30)) { int[] c30 = civs30.computeIfAbsent(civ, k -> new int[2]); c30[0]++; if (gano) c30[1]++; if (m.map != null && !m.map.isBlank()) { int[] x30 = mapas30.computeIfAbsent(m.map, k -> new int[2]); x30[0]++; if (gano) x30[1]++; } }
                { int td = tramoDuracion(m); if (td >= 0) { durN[td]++; if (gano) durW[td]++; } }
                if ("Team Random Map".equals(m.mode)) { String pos = posicionEnEquipo(m, yo); if (pos != null) { int[] px = posicion.computeIfAbsent(pos, k -> new int[2]); px[0]++; if (gano) px[1]++; if (m.map != null) { int[] pm = posicionMapa.computeIfAbsent(m.map + "\u0000" + pos, k -> new int[2]); pm[0]++; if (gano) pm[1]++; } } }   // solo ranked de equipos
                if (eloActualJugador == 0 && yo.rating != null && yo.rating > 0 && m.mode != null && m.mode.contains("1v1")) eloActualJugador = yo.rating + (yo.ratingDiff == null ? 0 : yo.ratingDiff);   // el ELO más reciente conocido
                int rivalRating = 0, nRivales = 0;
                for (MatchPlayer p : m.players) {
                    if (p.id == actPid) continue;
                    if (p.team != yo.team) {
                        Object[] r = rivales.computeIfAbsent(p.id, k -> new Object[]{ p.name, 0, 0 });
                        r[1] = (int) r[1] + 1; if (gano) r[2] = (int) r[2] + 1;
                        if (p.rating != null) { rivalRating += p.rating; nRivales++; }
                        if (m.players.size() == 2 && p.civ != null && !p.civ.isBlank()) { int[] x = civsRival.computeIfAbsent(p.civ, k -> new int[2]); x[0]++; if (gano) x[1]++; }
                    } else {
                        Object[] r = aliados.computeIfAbsent(p.id, k -> new Object[]{ p.name, 0, 0 });
                        r[1] = (int) r[1] + 1; if (gano) r[2] = (int) r[2] + 1;
                    }
                }
                if (nRivales > 0) {
                    if (franjas == null) franjas = franjasCentradas(eloActualJugador > 0 ? eloActualJugador : (int) Math.round(rivalRating / (double) nRivales));
                    String tr = franjaDe(rivalRating / (double) nRivales, franjas, eloActualJugador > 0 ? eloActualJugador : (int) Math.round(rivalRating / (double) nRivales));
                    int[] x = tramos.computeIfAbsent(tr, k -> new int[2]); x[0]++; if (gano) x[1]++;
                }
            }
        }
        int diasActivos = porDia.size();
        // forma reciente y gráfica del año, en la cabecera
        for (Component c : actChips.getComponents()) if (c instanceof JLabel l && "forma".equals(l.getName())) actChips.remove(l);
        if (f10 > 0) {
            String forma = fW + "-" + fL + " · " + (fDiff >= 0 ? "+" : "") + fDiff + (fDiff > 0 ? " \u25B2" : fDiff < 0 ? " \u25BC" : "")
                    + (fRacha >= 2 ? " · " + t("racha ", "streak ") + fRacha + (Boolean.TRUE.equals(fRachaGana) ? "V" : "D") : "");
            JLabel lf = chipPerfil(t("Últimas ", "Last ") + f10 + ("*".equals(actModo) ? "" : " · " + actModo), escapeHtml(forma), t("Las partidas más recientes con resultado, en el modo elegido", "The most recent games with a result, in the chosen mode"));
            lf.setName("forma");
            actChips.add(lf);
        }
        actChips.revalidate(); actChips.repaint();
        if (!serie.isEmpty()) {   // el punto más reciente = ELO actual del ladder; hacia atrás se resta la diferencia de cada partida (las diferencias sí son fiables)
            long acum = eloActualLadder(modoGrafica, serie.get(0));
            for (long[] pt : serie) { pt[1] = acum; acum -= pt[2]; }
        }
        Collections.reverse(serie);
        actGrafica.datos(serie, (modoGrafica == null ? "" : modoGrafica + " · ") + t("últimas ", "last ") + serie.size() + t(" partidas", " games"));
        actTarjetas.removeAll();
        actTarjetas.add(tarjeta(t("Partidas", "Games"), miles(partidas), (a.completo() ? t("último año", "last year") : t("últimas ", "last ") + miles(a.partidas().size()) + t(" (tope)", " (cap)")) + " · " + ("*".equals(actModo) ? t("todos los modos", "all modes") : actModo) + (actDesde != null ? " · " + (actHasta == null ? t("desde ", "since ") + actDesde : actDesde + " → " + actHasta) : "")));
        actTarjetas.add(tarjeta(t("Winrate", "Win rate"), conResultado == 0 ? "-" : pct1(100.0 * victorias / conResultado), victorias + "-" + (conResultado - victorias) + t(" con resultado", " with a result")));
        actTarjetas.add(tarjeta(t("Duración media", "Avg. length"), duracionMedia(duracion, conDuracion), t("de reloj, inicio a fin", "wall clock, start to end")));
        actTarjetas.add(tarjeta(t("Días con partidas", "Days with games"), String.valueOf(diasActivos), t("de los últimos ", "of the last ") + ACT_DIAS));
        actTarjetas.add(tarjeta(t("Por día activo", "Per active day"), diasActivos == 0 ? "-" : String.format(Locale.ROOT, "%.1f", partidas / (double) diasActivos).replace('.', "en".equals(IDIOMA) ? '.' : ','), t("partidas de media", "games on average")));
        actTarjetas.revalidate(); actTarjetas.repaint();
        actCalendario.datos(porDia, inicio, hoy);
        String[] dias = "en".equals(IDIOMA) ? new String[]{ "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" } : new String[]{ "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom" };
        actSemana.datos(dias, semana, semanaW, semanaN);
        String[] hl = new String[24], ht = new String[24]; int[] hv = new int[24], hw = new int[24], hn = new int[24];
        for (int i = 0; i < 24; i++) { hl[i] = i % 3 == 0 ? String.valueOf(i) : ""; ht[i] = String.format("%02d:00–%02d:59", i, i); hv[i] = horas[i]; hw[i] = horasW[i]; hn[i] = horasN[i]; }
        actHoras.datos(hl, hv, hw, hn, ht);
        List<String> mk = new ArrayList<>(meses.keySet());
        String[] ml = new String[mk.size()], mt2 = new String[mk.size()]; int[] mv = new int[mk.size()], mw = new int[mk.size()], mn = new int[mk.size()];
        for (int i = 0; i < mk.size(); i++) { ml[i] = String.valueOf(Integer.parseInt(mk.get(i).substring(5))); mt2[i] = mk.get(i); mv[i] = meses.get(mk.get(i))[0]; mw[i] = meses.get(mk.get(i))[1]; mn[i] = mv[i]; }
        actMeses.datos(ml, mv, mw, mn, mt2);
        Map<String, Runnable> irTechTree = new HashMap<>();
        for (String k : civs.keySet()) { String cl = claveCivDeNombre(k); if (cl != null) irTechTree.put(k, () -> abrirTechTree(cl)); }
        for (String k : civsRival.keySet()) { String cl = claveCivDeNombre(k); if (cl != null) irTechTree.put(k, () -> abrirTechTree(cl)); }
        pintarListaAgg(actCivs, t("Winrate por civ propia", "Win rate by own civ"), civs, 5, k -> k, irTechTree, k -> iconoCiv(claveCivDeNombre(k), 18));
        pintarListaAgg(actCivsRival, t("Winrate contra civ rival (1v1)", "Win rate vs opponent civ (1v1)"), civsRival, 5, k -> k, irTechTree, k -> iconoCiv(claveCivDeNombre(k), 18));
        pintarListaAgg(actMapas, t("Winrate por mapa", "Win rate by map"), mapas, 5, k -> k, null, k -> iconoMapa(k, 18));
        pintarListaJugadores(actRivales, t("Rivales más frecuentes", "Most frequent opponents"), rivales);
        pintarListaJugadores(actAliados, t("Aliados más frecuentes", "Most frequent allies"), aliados);
        Map<String, int[]> trOrd = new LinkedHashMap<>();
        if (franjas != null) for (String tr : franjas) if (tramos.containsKey(tr)) trOrd.put(tr, tramos.get(tr));
        pintarListaAgg(actTramos, t("Winrate por ELO del rival", "Win rate by opponent ELO") + (eloActualJugador > 0 ? " \u00B7 " + t("franjas centradas en ", "brackets centred on ") + eloActualJugador : ""), trOrd, 9, SpoilerFreeRecs::tramoNombre, null);
        // duración
        actDuracionFila.removeAll();
        for (int i = 0; i < 5; i++) actDuracionFila.add(tarjeta(DURACION_TRAMOS[i], durN[i] == 0 ? "\u2013" : pct1(100.0 * durW[i] / durN[i]), durN[i] == 0 ? t("sin partidas", "no games") : miles(durN[i]) + t(" partidas, ", " games, ") + miles(durW[i]) + t(" victorias", " wins")));
        actDuracionFila.revalidate(); actDuracionFila.repaint();
        // pocket / flanco
        Map<String, int[]> posOrd = new LinkedHashMap<>(); if (posicion.containsKey("pocket")) posOrd.put("pocket", posicion.get("pocket")); if (posicion.containsKey("flanco")) posOrd.put("flanco", posicion.get("flanco"));
        pintarListaAgg(actPosicion, t("Pocket o flanco (Team RM, 3v3 y 4v4)", "Pocket or flank (Team RM, 3v3 & 4v4)"), posOrd, 2, k -> posicionNombre(k), null);
        if (!posicionMapa.isEmpty()) {   // por mapa: una tabla ordenable (mapa · pocket · flanco)
            Map<String, int[][]> porMapa = new TreeMap<>();
            for (Map.Entry<String, int[]> en : posicionMapa.entrySet()) { String[] kv = en.getKey().split("\u0000"); int[][] f = porMapa.computeIfAbsent(kv[0], k -> new int[][]{ new int[2], new int[2] }); f["pocket".equals(kv[1]) ? 0 : 1] = en.getValue(); }
            List<Object[]> filasPos = new ArrayList<>();
            for (Map.Entry<String, int[][]> en : porMapa.entrySet()) { int[] pk = en.getValue()[0], fl = en.getValue()[1]; filasPos.add(new Object[]{ new Celda(iconoMapa(en.getKey(), 18), en.getKey(), null), (long) pk[0], pk[0] == 0 ? null : new Pct(100.0 * pk[1] / pk[0], pk[1], pk[0], true), (long) fl[0], fl[0] == 0 ? null : new Pct(100.0 * fl[1] / fl[0], fl[1], fl[0], true) }); }
            actPosicion.add(enlaceVerTodo(porMapa.size(), () -> mostrarTablaCompleta(t("Pocket o flanco por mapa", "Pocket or flank by map") + " \u00B7 " + actNombre, new String[]{ t("Mapa", "Map"), t("Partidas pocket", "Pocket games"), t("WR pocket", "Pocket WR"), t("Partidas flanco", "Flank games"), t("WR flanco", "Flank WR") }, filasPos, 1)));
        }
        // últimos 30 días
        pintarListaAgg(actUltimos30Civs, t("Últimos 30 días · civs", "Last 30 days · civs"), civs30, 5, k -> k, null, k -> iconoCiv(claveCivDeNombre(k), 18));
        pintarListaAgg(actUltimos30Mapas, t("Últimos 30 días · mapas", "Last 30 days · maps"), mapas30, 5, k -> k, null, k -> iconoMapa(k, 18));
        if (!actCargando) actEstado.setText(miles(a.partidas().size()) + t(" partidas", " games") + (a.completo() ? t(" · último año completo", " · full last year") : t(" · las más recientes", " · the most recent")));
        actMasBtn.setVisible(!a.completo() && !actCargando);
        actCuerpo.revalidate(); actCuerpo.repaint();
    }

    /** ELO actual del ladder que corresponde a un modo (de la ficha del perfil); si no hay ficha, el de la partida más reciente más su diferencia. */
    long eloActualLadder(String modo, long[] masReciente) {
        String lb = modo == null ? null : (modo.toLowerCase(Locale.ROOT).contains("empire") || modo.toLowerCase(Locale.ROOT).startsWith("ew")) ? (modo.contains("1v1") ? "ew_1v1" : "ew_team") : modo.contains("1v1") ? "rm_1v1" : "rm_team";
        Object[] perfil = PERFIL_CACHE.get(actPid);
        if (perfil != null && lb != null && perfil[1] instanceof Map<?, ?> mp && mp.get(lb) instanceof int[] v && v[0] > 0) return v[0];
        return masReciente[1] + masReciente[2];
    }

    /** Lista «nombre · barra por partidas · WR» ordenada por partidas; solo entradas con ACT_MIN partidas. */
    void pintarListaAgg(JPanel panel, String titulo, Map<String, int[]> datos, int tope, java.util.function.Function<String, String> nombre, Map<String, Runnable> alClicar) { pintarListaAgg(panel, titulo, datos, tope, nombre, alClicar, null); }

    final Map<String, Integer> ordenListas = new HashMap<>();   // título → 0 partidas, 1 winrate, 2 A-Z
    void pintarListaAgg(JPanel panel, String titulo, Map<String, int[]> datos, int tope, java.util.function.Function<String, String> nombre, Map<String, Runnable> alClicar, java.util.function.Function<String, Icon> icono) {
        panel.removeAll();
        boolean ordenado = datos instanceof LinkedHashMap;
        int modo = ordenado ? -1 : ordenListas.getOrDefault(titulo, 0);
        JLabel cab = tituloSeccion(titulo + (modo == 1 ? t("  · por winrate", "  · by win rate") : modo == 2 ? "  · A-Z" : ""),
                ordenado ? null : t("Clic para cambiar el orden: partidas → winrate → A-Z", "Click to change the order: games → win rate → A-Z"));
        if (!ordenado) {
            cab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cab.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { ordenListas.put(titulo, (ordenListas.getOrDefault(titulo, 0) + 1) % 3); actPintar(); } });
        }
        panel.add(cab);
        List<Map.Entry<String, int[]>> l = new ArrayList<>();
        for (Map.Entry<String, int[]> en : datos.entrySet()) if (en.getValue()[0] >= ACT_MIN) l.add(en);
        if (!ordenado) {
            if (modo == 1) l.sort((x, y) -> { double a = 100.0 * x.getValue()[1] / x.getValue()[0], b = 100.0 * y.getValue()[1] / y.getValue()[0]; int c = Double.compare(b, a); return c != 0 ? c : y.getValue()[0] - x.getValue()[0]; });
            else if (modo == 2) l.sort((x, y) -> nombre.apply(x.getKey()).compareToIgnoreCase(nombre.apply(y.getKey())));
            else l.sort((x, y) -> y.getValue()[0] - x.getValue()[0]);
        }
        double max = l.isEmpty() ? 1 : l.stream().mapToInt(e -> e.getValue()[0]).max().orElse(1);
        if (l.isEmpty()) { JLabel v = new JLabel(t("Nada con 3+ partidas.", "Nothing with 3+ games.")); v.setForeground(Color.GRAY); v.setFont(v.getFont().deriveFont(Font.PLAIN, 11f)); panel.add(v); }
        for (Map.Entry<String, int[]> en : l.subList(0, Math.min(tope, l.size()))) {
            int n = en.getValue()[0], w = en.getValue()[1];
            Runnable r = alClicar == null ? null : alClicar.get(en.getKey());
            panel.add(filaBarra(icono == null ? null : icono.apply(en.getKey()), nombre.apply(en.getKey()), n / max, pct1(100.0 * w / n), colorWr(w, n), w + "-" + (n - w) + " · " + n + t(" partidas", " games") + (r != null ? (icono != null ? t(" · clic: tech tree", " · click: tech tree") : t(" · clic: abrir su perfil", " · click: open their profile")) : ""), r));
        }
        if (l.size() > tope) {
            double maxF = max;
            panel.add(enlaceVerTodo(l.size(), () -> {
                List<Object[]> filas = new ArrayList<>();
                for (Map.Entry<String, int[]> en : l) {
                    int n = en.getValue()[0], w = en.getValue()[1];
                    Runnable r = alClicar == null ? null : alClicar.get(en.getKey());
                    filas.add(new Object[]{ new Celda(icono == null ? null : icono.apply(en.getKey()), nombre.apply(en.getKey()), r), (long) n, (long) w, (long) (n - w), new Pct(100.0 * w / n, w, n, true) });
                }
                mostrarTablaCompleta(titulo, new String[]{ t("Nombre", "Name"), t("Partidas", "Games"), t("V", "W"), t("D", "L"), "WR" }, filas, 1);
            }));
        }
        panel.revalidate(); panel.repaint();
    }

    /** Rivales o aliados: como la lista de agregados, con clic para abrir el perfil de cada uno. */
    void pintarListaJugadores(JPanel panel, String titulo, Map<Long, Object[]> jugadores) {
        esFilaJugador = true;
        try { pintarListaJugadoresImpl(panel, titulo, jugadores); } finally { esFilaJugador = false; }
    }
    void pintarListaJugadoresImpl(JPanel panel, String titulo, Map<Long, Object[]> jugadores) {
        Map<String, int[]> datos = new HashMap<>();
        Map<String, Runnable> clics = new HashMap<>();
        for (Map.Entry<Long, Object[]> en : jugadores.entrySet()) {
            long pid = en.getKey(); String nombre = String.valueOf(en.getValue()[0]);
            String clave = nombre + "\u0000" + pid;   // dos rivales con el mismo nick no se mezclan
            datos.put(clave, new int[]{ (int) en.getValue()[1], (int) en.getValue()[2] });
            clics.put(clave, () -> { if (ultimoClicCtrl) abrirPerfilEnPestana(pid, nombreVisible(pid, nombre)); else abrirPerfil(pid, nombreVisible(pid, nombre)); });
        }
        pintarListaAgg(panel, titulo, datos, esFilaJugador && h2hDialogo != null && h2hDialogo.isShowing() ? 10 : 5, k -> nombreVisible(Long.parseLong(k.substring(k.indexOf('\u0000') + 1)), k.substring(0, k.indexOf('\u0000'))), clics);
    }

    /** Calendario de un año: una celda por día, más oscura cuantas más partidas. */
    class CalendarioPanel extends JPanel {
        Map<LocalDate, Integer> porDia = Map.of(); LocalDate inicio, fin; int max = 1;
        int celda = 11, hueco = 2, ml = 30, mt = 34;
        CalendarioPanel() { setOpaque(false); ToolTipManager.sharedInstance().registerComponent(this); }
        void datos(Map<LocalDate, Integer> d, LocalDate ini, LocalDate f) {
            porDia = d; fin = f;
            inicio = ini.minusDays(ini.getDayOfWeek().getValue() - 1);   // arranca en lunes
            max = 1; for (int v : d.values()) max = Math.max(max, v);
            repaint();
        }
        LocalDate diaEn(Point p) {
            if (inicio == null || p.x < ml || p.y < mt) return null;
            int col = (p.x - ml) / (celda + hueco), fila = (p.y - mt) / (celda + hueco);
            if (fila < 0 || fila > 6) return null;
            LocalDate d = inicio.plusDays(col * 7L + fila);
            return d.isAfter(fin) ? null : d;
        }
        @Override public String getToolTipText(MouseEvent e) {
            LocalDate d = diaEn(e.getPoint());
            if (d == null) return null;
            int n = porDia.getOrDefault(d, 0);
            return d.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES"))) + ": " + n + t(" partidas", " games");
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (inicio == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            int w = getWidth();
            long dias = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1;
            int columnas = (int) ((dias + 6) / 7);
            celda = Math.max(8, Math.min(13, (w - ml - 8) / columnas - hueco));
            Font base = g2.getFont();
            g2.setFont(base.deriveFont(Font.BOLD, 13f)); g2.setColor(fg);
            g2.drawString(t("Actividad: partidas por día, último año", "Activity: games per day, last year") + "  ·  " + t("máximo ", "max ") + max + t(" en un día", " in a day"), 4, 14);
            g2.setFont(base.deriveFont(9f));
            String[] etq = "en".equals(IDIOMA) ? new String[]{ "Mon", "", "Wed", "", "Fri", "", "Sun" } : new String[]{ "Lun", "", "Mié", "", "Vie", "", "Dom" };
            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 160 : 205));
            for (int f = 0; f < 7; f++) if (!etq[f].isEmpty()) g2.drawString(etq[f], 4, mt + f * (celda + hueco) + celda - 2);
            Color verde = temaOscuroActivo ? new Color(0x4c, 0xaf, 0x50) : new Color(0x2e, 0x7d, 0x32);
            int mesPrevio = -1;
            for (int c = 0; c < columnas; c++) {
                for (int f = 0; f < 7; f++) {
                    LocalDate d = inicio.plusDays(c * 7L + f);
                    if (d.isAfter(fin)) break;
                    int x = ml + c * (celda + hueco), y = mt + f * (celda + hueco);
                    if (f == 0 || c == 0) {
                        if (d.getMonthValue() != mesPrevio && d.getDayOfMonth() <= 7) {
                            mesPrevio = d.getMonthValue();
                            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 160 : 205));
                            g2.drawString(d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES")), x, mt - 4);
                        }
                    }
                    int n = porDia.getOrDefault(d, 0);
                    if (n == 0) g2.setColor(new Color(128, 128, 128, temaOscuroActivo ? 45 : 30));
                    else { double k = 0.25 + 0.75 * Math.min(1, Math.sqrt(n / (double) max)); g2.setColor(new Color(verde.getRed(), verde.getGreen(), verde.getBlue(), (int) (255 * k))); }
                    g2.fillRoundRect(x, y, celda, celda, 3, 3);
                }
            }
            g2.dispose();
        }
    }

    /** Barras verticales con etiqueta y, si hay partidas suficientes, el winrate encima. */
    class BarrasActividad extends JPanel {
        final String titulo; String[] etiquetas = new String[0], tips = null; int[] valores = new int[0], ganadas = new int[0], conRes = new int[0];
        BarrasActividad(String titulo) { this.titulo = titulo; setOpaque(false); ToolTipManager.sharedInstance().registerComponent(this); }
        void datos(String[] e, int[] v, int[] w, int[] n) { datos(e, v, w, n, null); }
        void datos(String[] e, int[] v, int[] w, int[] n, String[] t) { etiquetas = e; valores = v; ganadas = w; conRes = n; tips = t; repaint(); }
        int barraEn(Point p) {
            if (valores.length == 0) return -1;
            int ml = 8, mr = 8, w = getWidth();
            double sx = (double) (w - ml - mr) / valores.length;
            int i = (int) ((p.x - ml) / sx);
            return i < 0 || i >= valores.length ? -1 : i;
        }
        @Override public String getToolTipText(MouseEvent e) {
            int i = barraEn(e.getPoint());
            if (i < 0) return null;
            String base = tips != null ? tips[i] : etiquetas[i].isEmpty() ? String.valueOf(i) : etiquetas[i];
            return base + ": " + valores[i] + t(" partidas", " games") + (conRes[i] >= ACT_MIN ? " · WR " + pct1(100.0 * ganadas[i] / conRes[i]) : "");
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            int w = getWidth(), h = getHeight(), ml = 8, mr = 8, mt = 42, mb = 18;
            Font base = g2.getFont();
            g2.setFont(base.deriveFont(Font.BOLD, 12f)); g2.setColor(fg);
            g2.drawString(titulo, 4, 14);
            g2.setFont(base.deriveFont(10.5f)); g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 160 : 205));
            g2.drawString(t("barras: partidas · número: % de victorias", "bars: games · number: win rate %"), 4, 27);
            if (valores.length == 0) { g2.dispose(); return; }
            int max = 1; for (int v : valores) max = Math.max(max, v);
            double sx = (double) (w - ml - mr) / valores.length;
            Color barra = temaOscuroActivo ? new Color(0x5a, 0x8f, 0xc7) : new Color(0x3b, 0x6e, 0xa8);
            g2.setFont(base.deriveFont(9f));
            for (int i = 0; i < valores.length; i++) {
                int x = ml + (int) (i * sx), bw = Math.max(2, (int) sx - 2);
                int bh = (int) ((h - mt - mb) * valores[i] / (double) max);
                g2.setColor(barra);
                g2.fillRoundRect(x, h - mb - bh, bw, bh, 3, 3);
                if (conRes[i] >= ACT_MIN && sx >= 22) {
                    double wr = 100.0 * ganadas[i] / conRes[i];
                    g2.setColor(colorWr(ganadas[i], conRes[i]));
                    String s = String.format(Locale.ROOT, "%.0f", wr) + "%";
                    g2.drawString(s, x + bw / 2 - g2.getFontMetrics().stringWidth(s) / 2, h - mb - bh - 3);
                }
                if (!etiquetas[i].isEmpty()) { g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 170 : 210)); g2.drawString(etiquetas[i], x + bw / 2 - g2.getFontMetrics().stringWidth(etiquetas[i]) / 2, h - 5); }
            }
            g2.dispose();
        }
    }

    // =====================================================================================
    // LIVE NOW — partidas en curso de los 250 mejores del ladder 1v1 (tarjetas por partida) y las
    // terminadas en las últimas 2 horas (sin resultado, con la rec a un clic). Datos: un barrido por
    // lotes al abrir y cada 10 min; entre medias, el socket del companion suscrito a esos 250.
    // =====================================================================================
    static final int AHORA_TOP = 250;
    JToggleButton ahoraBtn; JPanel ahoraPanel; JLabel ahoraEstado, liveSub; PanelScrollable ahoraCuerpo; JComboBox<String> liveTipo, liveMapa, liveOrden, liveFuente; JCheckBox liveTwitch, liveTopTop;
    final List<String[]> liveFuentes = new ArrayList<>();   // {tipo, valor, etiqueta}: top · pais · clan · grupo
    String liveFuenteTipo = "top", liveFuenteValor = ""; boolean liveRellenandoFuente;
    JComboBox<String> liveValor; JPanel liveValorCaja; JLabel liveTitulo; JPopupMenu liveClanPopup; JScrollPane liveScroll; int liveAnchoPintado;
    final List<Object[]> ahoraTop = new ArrayList<>();      // {pid, nombre, rating, rango, país}
    final Map<Long, Match> ahoraEnCurso = new HashMap<>();  // pid → partida en curso (vista)
    final Map<Long, Object[]> liveTerminadas = new LinkedHashMap<>();   // matchId → {Match, fin ms}
    long ahoraTopMs, ahoraUltimaMs; boolean ahoraCargando, ahoraAbierta, liveRellenando; javax.swing.Timer ahoraTimer, liveReloj; String liveMapaSel = "*";
    final List<JLabel> liveRelojes = new ArrayList<>();
    final Set<Long> socketExtra = java.util.concurrent.ConcurrentHashMap.newKeySet();   // ids extra para el socket (Live now abierto, vistas con campana)

    JPanel construirPanelAhora() {
        ahoraPanel = new JPanel(new BorderLayout(8, 6));
        ahoraPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel norte = new JPanel(new BorderLayout(8, 2));
        JPanel fila1 = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        liveTitulo = new JLabel("Live now");
        liveTitulo.setFont(liveTitulo.getFont().deriveFont(Font.BOLD, 15f));
        fila1.add(liveTitulo);
        ahoraEstado = new JLabel();
        ahoraEstado.setFont(ahoraEstado.getFont().deriveFont(Font.PLAIN, 11f));
        fila1.add(ahoraEstado);

        JButton refrescar = new JButton(t("Actualizar", "Refresh"));
        refrescar.setFocusable(false); refrescar.setMargin(new Insets(1, 8, 1, 8)); refrescar.putClientProperty("JButton.buttonType", "roundRect");
        refrescar.setToolTipText(t("Barrido completo ahora (entre barridos, las novedades llegan por el socket)", "Full sweep now (between sweeps, updates arrive through the socket)"));
        refrescar.addActionListener(e -> ahoraRefrescar(true));
        fila1.add(refrescar);
        liveSub = new JLabel();
        liveSub.setFont(liveSub.getFont().deriveFont(Font.PLAIN, 13f));
        JPanel cabecera = new JPanel(new BorderLayout(0, 2)); cabecera.add(fila1, BorderLayout.NORTH); cabecera.add(liveSub, BorderLayout.SOUTH);
        norte.add(cabecera, BorderLayout.NORTH);
        JPanel filtrosFila = new JPanel(new BorderLayout(8, 0));
        JPanel filtros = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 2));
        liveFuente = new JComboBox<>(new String[]{ t("Top 250 mundial", "World top 250"), t("Top 100 país", "Country top 100"), t("Clan", "Clan"), t("Grupo", "Group") });
        liveFuente.setToolTipText(t("De quién quieres ver las partidas: los 250 mejores del mundo, el top 100 de un país, un clan o uno de tus grupos", "Whose games to show: the world's top 250, a country's top 100, a clan or one of your groups"));
        liveFuente.addActionListener(e -> { if (liveRellenandoFuente) return; String tipo = new String[]{ "top", "pais", "clan", "grupo" }[Math.max(0, liveFuente.getSelectedIndex())]; guardarConfig("live_fuente_tipo", tipo); rellenarValorLive(tipo); aplicarFuenteLive(); });
        filtros.add(new JLabel(t("Fuente:", "Source:"))); filtros.add(liveFuente);
        liveValor = new JComboBox<>(); liveValor.setMaximumRowCount(14);
        liveValor.addActionListener(e -> { if (liveRellenandoFuente) return; aplicarFuenteLive(); });
        liveValorCaja = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0)); liveValorCaja.setOpaque(false); liveValorCaja.add(liveValor);
        JButton irFuente = new JButton("\u2192"); irFuente.setFocusable(false); irFuente.setMargin(new Insets(1, 6, 1, 6)); irFuente.putClientProperty("JButton.buttonType", "roundRect");
        irFuente.setToolTipText(t("Cargar esta fuente (también con Enter)", "Load this source (Enter works too)"));
        irFuente.addActionListener(e -> aplicarFuenteLive());
        liveValorCaja.add(irFuente);
        ((JTextField) liveValor.getEditor().getEditorComponent()).addActionListener(e -> aplicarFuenteLive());   // Enter en el desplegable editable aplica
        filtros.add(liveValorCaja);
        liveTipo = new JComboBox<>(new String[]{ t("Todas", "All"), "1v1", t("Equipos", "Team"), "Empire Wars" });
        liveTipo.addActionListener(e -> ahoraPintar());
        filtros.add(new JLabel(t("Tipo:", "Type:"))); filtros.add(liveTipo);
        liveMapa = new JComboBox<>();
        liveMapa.addActionListener(e -> { if (liveRellenando) return; Object cl = liveMapa.getClientProperty("claves"); if (cl instanceof List<?> l && liveMapa.getSelectedIndex() >= 0 && liveMapa.getSelectedIndex() < l.size()) { liveMapaSel = String.valueOf(l.get(liveMapa.getSelectedIndex())); ahoraPintar(); } });
        filtros.add(new JLabel(t("Mapa:", "Map:"))); filtros.add(liveMapa);
        liveOrden = new JComboBox<>(new String[]{ t("Ordenar: ELO", "Sort: ELO"), t("Ordenar: tiempo de partida", "Sort: game time") });
        liveOrden.addActionListener(e -> ahoraPintar());
        filtros.add(liveOrden);
        liveTwitch = new JCheckBox(t("Solo con Twitch", "Twitch only")); liveTwitch.setFocusable(false); liveTwitch.addActionListener(e -> ahoraPintar());
        liveTopTop = new JCheckBox(t("Solo top contra top", "Top vs top only")); liveTopTop.setFocusable(false); liveTopTop.setToolTipText(t("Partidas en las que ambos bandos tienen a alguien del top 50", "Games where both sides have someone from the top 50")); liveTopTop.addActionListener(e -> ahoraPintar());
        filtros.add(liveTwitch); filtros.add(liveTopTop);
        filtrosFila.add(filtros, BorderLayout.CENTER);
        JButton verTop = new JButton(t("Ver la lista", "See the list"));
        verTop.setFocusable(false); verTop.setMargin(new Insets(1, 8, 1, 8)); verTop.putClientProperty("JButton.buttonType", "roundRect");
        verTop.setToolTipText(t("La lista completa de la fuente elegida, con filtros por país y nick, y espectar a quien esté jugando", "The full list of the chosen source, with country and nick filters, and spectate whoever is playing"));
        verTop.addActionListener(e -> mostrarLista250());
        JPanel der = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2)); der.add(verTop);
        filtrosFila.add(der, BorderLayout.EAST);
        norte.add(filtrosFila, BorderLayout.SOUTH);
        ahoraPanel.addComponentListener(new java.awt.event.ComponentAdapter() { @Override public void componentResized(java.awt.event.ComponentEvent e) { filtros.revalidate(); norte.revalidate(); } });   // la fila de filtros se reajusta al ancho real
        rellenarFuentesLive();
        ahoraPanel.add(norte, BorderLayout.NORTH);
        ahoraCuerpo = new PanelScrollable();
        ahoraCuerpo.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 6));
        JScrollPane sp = new JScrollPane(ahoraCuerpo, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {   // al cambiar el ancho, se recalcula el reparto (sin scroll horizontal nunca)
            javax.swing.Timer t;
            @Override public void componentResized(java.awt.event.ComponentEvent e) { if (t != null) t.stop(); t = new javax.swing.Timer(150, ev -> { if (ahoraAbierta && ahoraPanel.isShowing() && liveAnchoPintado != sp.getViewport().getWidth()) ahoraPintar(); }); t.setRepeats(false); t.start(); }
        });
        liveScroll = sp;
        sp.setBorder(null);
        ahoraPanel.add(sp, BorderLayout.CENTER);
        JLabel pie = new JLabel(t("Barrido al abrir y cada 10 min; el socket del companion trae las novedades al instante. Doble clic en un nick: perfil · botón central: pestaña nueva · clic derecho: más. «Buscando partida» no lo publica ninguna API: solo se ven partidas ya empezadas.",
                "Sweep on open and every 10 min; the companion's socket brings updates instantly. Double-click a nick: profile · middle button: new tab · right-click: more. \u201CIn queue\u201D is not published by any API: only started games are shown."));
        pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f));
        ahoraPanel.add(pie, BorderLayout.SOUTH);
        liveReloj = new javax.swing.Timer(1000, e -> { if (!ahoraAbierta) { liveReloj.stop(); return; } for (JLabel l : liveRelojes) { Object m = l.getClientProperty("match"); if (m instanceof Match mm && mm.started != null) l.setText(reloj(Duration.between(mm.started, Instant.now()))); } });
        return ahoraPanel;
    }

    /** Título de sección que se pliega y despliega con un clic (estado recordado en config). */
    JLabel tituloPlegable(String texto, String claveConfig) {
        JLabel l = tituloSeccion(texto);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 15f));
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.setToolTipText(t("Clic para plegar o desplegar", "Click to collapse or expand"));
        l.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { guardarConfig(claveConfig, String.valueOf(!Boolean.parseBoolean(leerConfig(claveConfig, "false")))); ahoraPintar(); } });
        return l;
    }
    /** Ventana con la lista completa de la fuente: puesto, bandera, nick, ELO, país, en partida; filtros por país (desplegable con escritura) y por nick; espectar desde dentro; doble clic = perfil. */
    void mostrarLista250() {
        List<Object[]> top; synchronized (ahoraTop) { top = new ArrayList<>(ahoraTop); }
        if (top.isEmpty()) { status.setText(t("Todavía no se ha cargado la lista: abre Live now un momento.", "The list isn't loaded yet: open Live now for a moment.")); return; }
        JDialog d = new JDialog(this, "Live now · " + etiquetaFuenteLive(), false);
        DefaultTableModel modelo = new DefaultTableModel(new Object[]{ "#", t("Jugador", "Player"), "ELO", t("País", "Country"), t("Modo", "Mode"), t("Ahora", "Now"), "" }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 || c == 2 ? Integer.class : String.class; }
        };
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(tabla.getRowHeight() + 8);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(50); tabla.getColumnModel().getColumn(1).setPreferredWidth(220); tabla.getColumnModel().getColumn(2).setPreferredWidth(60); tabla.getColumnModel().getColumn(3).setPreferredWidth(60); tabla.getColumnModel().getColumn(4).setPreferredWidth(90); tabla.getColumnModel().getColumn(5).setPreferredWidth(200); tabla.getColumnModel().getColumn(6).setPreferredWidth(96);
        tabla.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel lab = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, col);
                lab.setIcon(value == null ? null : iconoBandera(String.valueOf(value))); lab.setIconTextGap(5); return lab;
            }
        });
        tabla.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {   // «Espectar» como botón pintado
            @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel lab = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, col);
                boolean hay = value != null && !String.valueOf(value).isEmpty();
                lab.setHorizontalAlignment(SwingConstants.CENTER);
                lab.setText(hay ? "\u25B6 " + t("Espectar", "Spectate") : "");
                lab.setForeground(hay ? (temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00)) : lab.getForeground());
                lab.setFont(lab.getFont().deriveFont(Font.BOLD));
                return lab;
            }
        });
        Map<Long, Match> vivos; synchronized (ahoraEnCurso) { vivos = new HashMap<>(ahoraEnCurso); }
        List<Object[]> filas = new ArrayList<>();   // {pid, nombre, matchId}
        for (Object[] f : top) {
            Match m = vivos.get((Long) f[0]);
            String ahora = m == null ? "" : ("\u25CF " + (m.map == null ? "" : m.map) + (m.started != null ? " · " + reloj(Duration.between(m.started, Instant.now())) : ""));
            modelo.addRow(new Object[]{ f[3], nombreVisible((Long) f[0], (String) f[1]), f[2], f[4] == null ? "" : String.valueOf(f[4]).toUpperCase(Locale.ROOT), m == null ? "" : modoCorto(m), ahora, m == null || m.id <= 0 ? "" : String.valueOf(m.id) });
            filas.add(new Object[]{ f[0], nombreVisible((Long) f[0], (String) f[1]), m == null ? 0L : m.id });
        }
        javax.swing.table.TableRowSorter<DefaultTableModel> sorter = new javax.swing.table.TableRowSorter<>(modelo);
        tabla.setRowSorter(sorter);
        // filtros: país (desplegable editable con scroll) y nick
        Set<String> paisesSet = new TreeSet<>(); for (Object[] f : top) if (f[4] != null && !String.valueOf(f[4]).isBlank()) paisesSet.add(String.valueOf(f[4]).toUpperCase(Locale.ROOT));
        JComboBox<String> paisCb = new JComboBox<>();
        paisCb.addItem(t("Todos los países", "All countries"));
        for (String cc : paisesSet) { String nombreP = cc; for (PaisItem pi : PAISES) if (pi.code().equalsIgnoreCase(cc)) nombreP = pi.nombre() + " (" + cc + ")"; paisCb.addItem(nombreP); }
        paisCb.setEditable(true); paisCb.setMaximumRowCount(16);
        paisCb.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean foc) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, value, index, sel, foc);
                String v = String.valueOf(value); int i = v.lastIndexOf('('); String cc = i > 0 ? v.substring(i + 1, v.length() - 1) : null;
                lab.setIcon(cc == null ? null : iconoBandera(cc)); lab.setIconTextGap(5); return lab;
            }
        });
        JTextField busca = new JTextField(16);
        busca.putClientProperty("JTextField.placeholderText", t("Filtrar por nick", "Filter by nick"));
        JCheckBox soloVivos = new JCheckBox(t("Solo en partida", "In a game only")); soloVivos.setFocusable(false);
        Runnable filtrar = () -> {
            List<RowFilter<Object, Object>> fs = new ArrayList<>();
            String q = busca.getText().trim();
            if (!q.isEmpty()) fs.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(q), 1));
            Object pv = paisCb.getEditor().getItem(); String ptxt = pv == null ? "" : String.valueOf(pv).trim();
            if (paisCb.getSelectedIndex() > 0 || (!ptxt.isEmpty() && !ptxt.equals(paisCb.getItemAt(0)))) {
                int i = ptxt.lastIndexOf('('); String cc = i > 0 && ptxt.endsWith(")") ? ptxt.substring(i + 1, ptxt.length() - 1) : ptxt;
                final String ccF = cc.toUpperCase(Locale.ROOT);
                fs.add(RowFilter.regexFilter("(?i)^" + java.util.regex.Pattern.quote(ccF), 3));
            }
            if (soloVivos.isSelected()) fs.add(RowFilter.regexFilter("\\u25CF", 5));
            sorter.setRowFilter(fs.isEmpty() ? null : RowFilter.andFilter(fs));
        };
        busca.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { filtrar.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { filtrar.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrar.run(); }
        });
        paisCb.addActionListener(e -> filtrar.run());
        ((JTextField) paisCb.getEditor().getEditorComponent()).getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {   // al escribir, filtra el desplegable y la tabla
            void cambio() { String q = String.valueOf(paisCb.getEditor().getItem()).trim().toLowerCase(Locale.ROOT); if (q.length() >= 2 && !paisCb.isPopupVisible()) { for (int i = 1; i < paisCb.getItemCount(); i++) if (paisCb.getItemAt(i).toLowerCase(Locale.ROOT).startsWith(q)) { paisCb.setPopupVisible(true); break; } } filtrar.run(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { filtrar.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrar.run(); }
        });
        soloVivos.addActionListener(e -> filtrar.run());
        tabla.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int vr = tabla.rowAtPoint(e.getPoint()); if (vr < 0) return;
                int mr = tabla.convertRowIndexToModel(vr);
                Object[] f = filas.get(mr);
                int vc = tabla.columnAtPoint(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1 && tabla.convertColumnIndexToModel(vc) == 6 && (Long) f[2] > 0) {   // «Espectar» con el aviso de siempre
                    if (confirmarEspectar((String) f[1])) espectarPartida((Long) f[2]);
                    return;
                }
                if (e.getClickCount() != 2) return;
                if (SwingUtilities.isMiddleMouseButton(e)) abrirPerfilEnPestana((Long) f[0], (String) f[1]); else abrirPerfil((Long) f[0], (String) f[1]);
            }
        });
        JPanel arriba = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        arriba.add(new JLabel(t("País:", "Country:"))); arriba.add(paisCb); arriba.add(busca); arriba.add(soloVivos);
        JLabel pie = new JLabel(t("Doble clic: perfil · botón central: pestaña nueva · «▶ Espectar» en quien está jugando · ordena clicando las cabeceras", "Double-click: profile · middle button: new tab · \u201C\u25B6 Spectate\u201D on whoever is playing · click headers to sort")); pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f)); pie.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        d.add(arriba, BorderLayout.NORTH); d.add(new JScrollPane(tabla), BorderLayout.CENTER); d.add(pie, BorderLayout.SOUTH);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setSize(860, 660); d.setLocationRelativeTo(this); d.setVisible(true);
    }
    /** Modo corto de una partida: «1v1 RM», «TG 3v3», «1v1 EW», «DM 2v2», «Custom»… */
    static String modoCorto(Match m) {
        String modo = m.mode == null ? "" : m.mode.toLowerCase(Locale.ROOT);
        int n = m.players.size();
        String tam = n <= 2 ? "1v1" : (n / 2) + "v" + (n / 2);
        String tipo = modo.contains("empire") ? "EW" : modo.contains("death") || modo.contains(" dm") ? "DM" : modo.contains("unranked") || modo.contains("custom") ? t("Unranked", "Unranked") : "RM";
        return n > 2 && tipo.equals("RM") ? "TG " + tam : tam + " " + tipo;
    }
    static String reloj(Duration d) { long s = Math.max(0, d.getSeconds()); return String.format("%d:%02d", s / 60, s % 60); }
    String ahoraNombre(long pid) { for (Object[] f : ahoraTop) if ((Long) f[0] == pid) return nombreVisible(pid, (String) f[1]); return String.valueOf(pid); }
    Object[] liveFicha(long pid) { for (Object[] f : ahoraTop) if ((Long) f[0] == pid) return f; return null; }

    void abrirAhora() {
        registrarDestino(new Destino("ahora", 0, null, null));
        if (ahoraBtn != null && !ahoraBtn.isSelected()) ahoraBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        if (techTreeBtn != null && techTreeBtn.isSelected()) cerrarTechTree();
        actividadAbierta = false;
        if (perfilBtn != null) perfilBtn.setSelected(false);
        ahoraAbierta = true;
        ((CardLayout) centroCards.getLayout()).show(centroCards, "ahora");
        subirArriba(ahoraPanel);
        taparResultados(); apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        if (ahoraTimer == null) { ahoraTimer = new javax.swing.Timer(1_800_000, e -> { if (ahoraAbierta && ahoraPanel.isShowing() && !socketConectado) ahoraRefrescar(false); }); ahoraTimer.start(); }   // con el socket vivo no hay barridos periódicos: los eventos mantienen el estado
        if (!ctrlOn("live_now")) { ahoraEstado.setText(t("Live now está pausado temporalmente por mantenimiento de la fuente de datos.", "Live now is paused temporarily for data-source maintenance.")); ahoraCuerpo.removeAll(); ahoraCuerpo.revalidate(); ahoraCuerpo.repaint(); return; }
        liveReloj.start();
        rellenarFuentesLive();
        ahoraRefrescar(false);
    }

    void rellenarFuentesLive() {
        liveRellenandoFuente = true;
        try {
            String tipo = leerConfig("live_fuente_tipo", "top");
            int idx = switch (tipo) { case "pais" -> 1; case "clan" -> 2; case "grupo" -> 3; default -> 0; };
            liveFuente.setSelectedIndex(idx);
            rellenarValorLive(tipo);
        } finally { liveRellenandoFuente = false; }
        aplicarFuenteLive();
    }
    /** Segundo desplegable según el tipo: países (con banderas, se puede escribir), clanes (guardados + escribir), grupos. Recuerda la última elección de cada tipo. */
    void rellenarValorLive(String tipo) {
        boolean antes = liveRellenandoFuente; liveRellenandoFuente = true;
        try {
            liveValor.removeAllItems(); liveValor.setEditable(false); liveValor.setRenderer(new DefaultListCellRenderer());
            liveValorCaja.setVisible(!tipo.equals("top"));
            List<String> claves = new ArrayList<>();
            switch (tipo) {
                case "pais" -> {
                    for (PaisItem pi : PAISES) { claves.add(pi.code()); liveValor.addItem(pi.nombre() + " (" + pi.code().toUpperCase(Locale.ROOT) + ")"); }
                    liveValor.setRenderer(new DefaultListCellRenderer() {
                        @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean foc) {
                            JLabel lab = (JLabel) super.getListCellRendererComponent(l, value, index, sel, foc);
                            String v = String.valueOf(value); int i = v.lastIndexOf('('); lab.setIcon(i > 0 ? iconoBandera(v.substring(i + 1, v.length() - 1).toLowerCase(Locale.ROOT)) : null); lab.setIconTextGap(5); return lab;
                        }
                    });
                    liveValor.setEditable(true);
                    String cc = leerConfig("live_pais", paisSel() == null ? "es" : paisSel());
                    int i = claves.indexOf(cc); liveValor.setSelectedIndex(Math.max(0, i));
                }
                case "clan" -> {
                    for (String tag : clanesGuardados()) { claves.add(tag); liveValor.addItem(tag); }
                    liveValor.setEditable(true);
                    String tag = leerConfig("live_clan", clanField == null ? "" : clanField.getText().trim());
                    if (!tag.isEmpty() && !claves.contains(tag)) { claves.add(tag); liveValor.addItem(tag); }
                    liveValor.setSelectedItem(tag.isEmpty() && liveValor.getItemCount() > 0 ? liveValor.getItemAt(0) : tag);
                    instalarPrediccionClanes();   // al escribir, sugiere clanes del ladder (como en Top clan)
                }
                case "grupo" -> {
                    Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); for (Player x : todosJugadores) gs.add(x.grupo());
                    for (String g : gs) { claves.add(g); liveValor.addItem(g); }
                    String g = leerConfig("live_grupo", ""); int i = claves.indexOf(g); liveValor.setSelectedIndex(Math.max(0, i));
                }
                default -> { }
            }
            liveValor.putClientProperty("claves", claves);
            liveValor.setPreferredSize(new Dimension(tipo.equals("pais") ? 220 : 160, liveValor.getPreferredSize().height));
            liveValorCaja.revalidate();
        } finally { liveRellenandoFuente = antes; }
    }
    /** Sugerencias de clan al escribir en el desplegable de Live now (misma lista que Top clan); Enter o clic aplican. */
    void instalarPrediccionClanes() {
        JTextField ed = (JTextField) liveValor.getEditor().getEditorComponent();
        if (Boolean.TRUE.equals(ed.getClientProperty("prediccionClanes"))) return;
        ed.putClientProperty("prediccionClanes", true);
        liveClanPopup = new JPopupMenu(); liveClanPopup.setFocusable(false);
        ed.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() {
                if (!"clan".equals(new String[]{ "top", "pais", "clan", "grupo" }[Math.max(0, liveFuente.getSelectedIndex())])) return;
                String q = ed.getText().trim();
                liveClanPopup.setVisible(false); liveClanPopup.removeAll();
                if (q.length() < 1) return;
                if (clanes.isEmpty()) { Thread th = new Thread(() -> ladderAsegurar(false), "clanes"); th.setDaemon(true); th.start(); return; }
                for (Map.Entry<String, Integer> en : sugerirClanes(q)) {
                    JMenuItem it = new JMenuItem(en.getKey() + "  (" + en.getValue() + ")");
                    it.addActionListener(a -> { liveClanPopup.setVisible(false); liveValor.setSelectedItem(en.getKey()); aplicarFuenteLive(); });
                    liveClanPopup.add(it);
                }
                if (liveClanPopup.getComponentCount() > 0) liveClanPopup.show(ed, 0, ed.getHeight());
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        ed.addActionListener(e -> { liveClanPopup.setVisible(false); aplicarFuenteLive(); });
    }
    /** Lee tipo y valor de los desplegables, los recuerda y, si cambian, recarga la fuente. */
    void aplicarFuenteLive() {
        String tipo = new String[]{ "top", "pais", "clan", "grupo" }[Math.max(0, liveFuente.getSelectedIndex())];
        String valor = "";
        if (!tipo.equals("top")) {
            Object cl = liveValor.getClientProperty("claves");
            int i = liveValor.getSelectedIndex();
            if (cl instanceof List<?> l && i >= 0 && i < l.size()) valor = String.valueOf(l.get(i));
            else { Object ed = liveValor.isEditable() ? liveValor.getEditor().getItem() : liveValor.getSelectedItem(); valor = ed == null ? "" : String.valueOf(ed).trim(); if (tipo.equals("pais")) { int k = valor.lastIndexOf('('); if (k > 0 && valor.endsWith(")")) valor = valor.substring(k + 1, valor.length() - 1); valor = valor.toLowerCase(Locale.ROOT); } }
            if (valor.isEmpty()) return;
            guardarConfig(tipo.equals("pais") ? "live_pais" : tipo.equals("clan") ? "live_clan" : "live_grupo", valor);
        }
        if (tipo.equals(liveFuenteTipo) && valor.equals(liveFuenteValor)) { actualizarSubtituloLive(); return; }
        liveFuenteTipo = tipo; liveFuenteValor = valor;
        liveFuentes.clear();
        String etiqueta = switch (tipo) { case "pais" -> { String n = valor.toUpperCase(Locale.ROOT); for (PaisItem pi : PAISES) if (pi.code().equalsIgnoreCase(valor)) n = pi.nombre(); yield t("Top 100 · ", "Top 100 · ") + n; } case "clan" -> t("Clan ", "Clan ") + valor; case "grupo" -> t("Grupo ", "Group ") + valor; default -> t("Top 250 mundial", "World top 250"); };
        liveFuentes.add(new String[]{ tipo, valor, etiqueta });
        cambiarFuenteLive();
    }
    String etiquetaFuenteLive() { for (String[] f : liveFuentes) if (f[0].equals(liveFuenteTipo) && f[1].equals(liveFuenteValor)) return f[2]; return t("Top 250 mundial", "World top 250"); }
    void actualizarSubtituloLive() {
        if (liveSub == null) return;
        String quien = switch (liveFuenteTipo) {
            case "pais" -> t("los 100 mejores de ", "the top 100 of ") + etiquetaFuenteLive().replaceFirst("^Top 100 \u00B7 ", "");
            case "clan" -> t("los jugadores del clan ", "the players of clan ") + liveFuenteValor;
            case "grupo" -> t("los jugadores de tu grupo «", "the players of your group \u201C") + liveFuenteValor + (("es".equals(IDIOMA)) ? "»" : "\u201D");
            default -> t("los 250 mejores del mundo (ladder 1v1)", "the world's top 250 (1v1 ladder)");
        };
        liveSub.setText(t("Qué están jugando ahora mismo ", "What ") + quien + t(", en tiempo real; y sus partidas terminadas en las últimas dos horas, sin resultado.", " are playing right now, in real time; and their games finished in the last two hours, no result shown."));
        if (liveTitulo != null) liveTitulo.setText("Live now \u00B7 " + etiquetaFuenteLive());
    }
    void cambiarFuenteLive() {
        synchronized (ahoraTop) { ahoraTop.clear(); }
        synchronized (ahoraEnCurso) { ahoraEnCurso.clear(); }
        synchronized (liveTerminadas) { liveTerminadas.clear(); }
        ahoraTopMs = 0; ahoraUltimaMs = 0;
        actualizarSubtituloLive();
        ahoraRefrescar(true);
    }

    /** La lista de jugadores de la fuente elegida: {pid, nombre, rating, rango, país}. */
    List<Object[]> cargarFuenteLive() throws Exception {
        List<Object[]> top = new ArrayList<>();
        switch (liveFuenteTipo) {
            case "pais" -> {
                for (FilaClasificacion f : COMPANION.clasificacion("rm_1v1", 1, 100, liveFuenteValor).filas()) {
                    long pid = f.pid(); if (pid <= 0) continue;
                    int rating = f.rating() != null ? f.rating() : 0;
                    int rango = f.rango() != null ? f.rango() : 0;
                    aprenderPais(pid, f.pais()); aprenderCanal(pid, f.canal());
                    top.add(new Object[]{ pid, String.valueOf(f.nombre()), rating, rango, paisDe(pid) });
                }
            }
            case "clan" -> { if (ladderAsegurar(false) == null) for (LadderRow r : miembrosClan(liveFuenteValor)) { aprenderPais(r.pid(), r.country()); top.add(new Object[]{ r.pid(), r.name(), r.rating(), r.rank(), paisDe(r.pid()) }); } if (top.isEmpty()) log("live: clan «" + liveFuenteValor + "» sin miembros en el ladder 1v1"); }
            case "grupo" -> { for (Player pl : todosJugadores) if (pl.grupo().equalsIgnoreCase(liveFuenteValor)) { Integer elo = eloWatch.get(pl.id()); top.add(new Object[]{ pl.id(), pl.name(), elo == null ? 0 : elo, 0, paisDe(pl.id()) }); } }
            default -> {
                for (int pag = 1; pag <= 3 && top.size() < AHORA_TOP; pag++) {
                    for (FilaClasificacion f : COMPANION.clasificacion("rm_1v1", pag, 100, null).filas()) {
                        long pid = f.pid(); if (pid <= 0) continue;
                        int rating = f.rating() != null ? f.rating() : 0;
                        int rango = f.rango() != null ? f.rango() : top.size() + 1;
                        aprenderPais(pid, f.pais()); aprenderCanal(pid, f.canal());
                        top.add(new Object[]{ pid, String.valueOf(f.nombre()), rating, rango, paisDe(pid) });
                        if (top.size() >= AHORA_TOP) break;
                    }
                }
            }
        }
        return top;
    }

    /** Carga la fuente (si tiene más de 30 min), comprueba por lotes quién está en partida y suscribe esos ids al socket. */
    void ahoraRefrescar(boolean forzar) {
        if (ahoraCargando) return;
        if (!forzar && System.currentTimeMillis() - ahoraUltimaMs < 60_000 && !ahoraTop.isEmpty()) { ahoraPintar(); return; }
        ahoraCargando = true;
        ahoraEstado.setText(t("Consultando…", "Checking…"));
        new Thread(() -> {
            try {
                if (ahoraTop.isEmpty() || System.currentTimeMillis() - ahoraTopMs > 30 * 60_000L) {
                    List<Object[]> top = cargarFuenteLive();
                    synchronized (ahoraTop) { ahoraTop.clear(); ahoraTop.addAll(top); }
                    ahoraTopMs = System.currentTimeMillis();
                    Set<Long> ids = new HashSet<>(); for (Object[] f : top) ids.add((Long) f[0]);
                    socketExtra.removeIf(id -> !ids.contains(id) && !campanaContiene(id));
                    socketExtra.addAll(ids);
                    SwingUtilities.invokeLater(this::sincronizarSocket);   // el socket vigila también a los 250
                }
                Map<Long, Match> vivos = new HashMap<>();
                List<Object[]> top; synchronized (ahoraTop) { top = new ArrayList<>(ahoraTop); }
                final int LOTE = 15; int fallosSeguidos = 0;
                for (int d = 0; d < top.size(); d += LOTE) {
                    if (!ahoraAbierta || fallosSeguidos >= 3) { if (fallosSeguidos >= 3) log("live: tres lotes fallidos seguidos: barrido abortado hasta el próximo"); break; }
                    List<Object[]> lote = top.subList(d, Math.min(d + LOTE, top.size()));
                    StringBuilder csv = new StringBuilder(); Set<Long> ids = new HashSet<>();
                    for (Object[] f : lote) { if (csv.length() > 0) csv.append(','); csv.append((Long) f[0]); ids.add((Long) f[0]); }
                    try {
                        Iterable<Match> leidas = COMPANION.partidas(csv.toString(), 1, 100);
                        for (Match m : leidas) {
                            if (m == null) continue;
                            if (enCursoReal(m)) { for (MatchPlayer mp : m.players) if (ids.contains(mp.id) && !vivos.containsKey(mp.id)) vivos.put(mp.id, m); }
                            else if (m.finished != null && m.finished.isAfter(Instant.now().minus(Duration.ofHours(2)))) { synchronized (liveTerminadas) { liveTerminadas.putIfAbsent(m.id, new Object[]{ m, m.finished.toEpochMilli() }); } }
                        }
                        fallosSeguidos = 0;
                    } catch (Exception ex) { fallosSeguidos++; log("live: lote " + (d / LOTE + 1) + ": " + causa(ex)); }
                    final int hechos = Math.min(d + LOTE, top.size());
                    SwingUtilities.invokeLater(() -> ahoraEstado.setText(t("Consultando… ", "Checking… ") + hechos + " / " + top.size()));
                }
                synchronized (ahoraEnCurso) { ahoraEnCurso.clear(); ahoraEnCurso.putAll(vivos); }
                for (Map.Entry<Long, Match> en : vivos.entrySet()) VIVO_PARTIDA.put(en.getKey(), en.getValue());
                ahoraUltimaMs = System.currentTimeMillis();
                SwingUtilities.invokeLater(this::ahoraPintar);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> ahoraEstado.setText(t("No se pudo consultar: ", "Couldn't check: ") + causa(ex)));
            } finally { ahoraCargando = false; }
        }, "live-now").start();
    }

    /** Evento del socket sobre alguien del top 250: entra en partida o la termina. */
    void liveEvento(long pid, Match m, boolean terminada) {
        if (liveFicha(pid) == null) return;
        if (terminada) {
            Match viva = VIVO_PARTIDA.remove(pid);
            Match fin = m != null ? m : viva;
            if (fin != null && fin.id > 0) synchronized (liveTerminadas) { liveTerminadas.putIfAbsent(fin.id, new Object[]{ fin, System.currentTimeMillis() }); }
            synchronized (ahoraEnCurso) { ahoraEnCurso.remove(pid); }
        } else if (m != null) {
            VIVO_PARTIDA.put(pid, m);
            synchronized (ahoraEnCurso) { ahoraEnCurso.put(pid, m); }
        }
        if (ahoraAbierta && ahoraPanel != null && ahoraPanel.isShowing()) SwingUtilities.invokeLater(this::ahoraPintar);
    }

    boolean liveTipoOk(Match m) {
        int sel = liveTipo == null ? 0 : liveTipo.getSelectedIndex();
        String modo = m.mode == null ? "" : m.mode.toLowerCase(Locale.ROOT);
        return switch (sel) {
            case 1 -> m.players.size() == 2 && !modo.contains("empire") && !modo.contains("ew");
            case 2 -> m.players.size() > 2;
            case 3 -> modo.contains("empire") || modo.contains(" ew") || modo.startsWith("ew");
            default -> true;
        };
    }

    void ahoraPintar() {
        List<Object[]> top; synchronized (ahoraTop) { top = new ArrayList<>(ahoraTop); }
        Map<Long, Match> vivos; synchronized (ahoraEnCurso) { vivos = new HashMap<>(ahoraEnCurso); }
        Map<Long, Object[]> fichas = new HashMap<>(); for (Object[] f : top) fichas.put((Long) f[0], f);
        // partidas únicas en curso (una tarjeta por partida, aunque haya varios del top dentro)
        Map<Long, Match> partidas = new LinkedHashMap<>();
        for (Match m : vivos.values()) partidas.putIfAbsent(m.id, m);
        List<Match> lista = new ArrayList<>(partidas.values());
        // mapas para el filtro
        liveRellenando = true;
        try {
            Map<String, Integer> mapas = new TreeMap<>();
            for (Match m : lista) if (m.map != null && !m.map.isBlank()) mapas.merge(m.map, 1, Integer::sum);
            List<String> claves = new ArrayList<>(); claves.add("*");
            liveMapa.removeAllItems(); liveMapa.addItem(t("Todos", "All"));
            for (Map.Entry<String, Integer> en : mapas.entrySet()) { claves.add(en.getKey()); liveMapa.addItem(en.getKey() + " (" + en.getValue() + ")"); }
            liveMapa.putClientProperty("claves", claves);
            if (!claves.contains(liveMapaSel)) liveMapaSel = "*";
            liveMapa.setSelectedIndex(claves.indexOf(liveMapaSel));
        } finally { liveRellenando = false; }
        // filtros
        lista.removeIf(m -> !liveTipoOk(m));
        if (!"*".equals(liveMapaSel)) lista.removeIf(m -> !liveMapaSel.equals(m.map));
        if (liveTwitch.isSelected()) lista.removeIf(m -> m.players.stream().noneMatch(mp -> twitchLive.containsKey(mp.id)));
        if (liveTopTop.isSelected()) lista.removeIf(m -> { Set<Integer> equipos = new HashSet<>(); for (MatchPlayer mp : m.players) { Object[] f = fichas.get(mp.id); if (f != null && (Integer) f[3] <= 50) equipos.add(mp.team); } return equipos.size() < 2; });
        java.util.function.ToIntFunction<Match> eloMax = m -> { int mx = 0; for (MatchPlayer mp : m.players) if (mp.rating != null) mx = Math.max(mx, mp.rating); return mx; };
        if (liveOrden.getSelectedIndex() == 1) lista.sort((a, b) -> { Instant x = a.started == null ? Instant.EPOCH : a.started, y = b.started == null ? Instant.EPOCH : b.started; return x.compareTo(y); });
        else lista.sort((a, b) -> eloMax.applyAsInt(b) - eloMax.applyAsInt(a));
        ahoraCuerpo.removeAll(); liveRelojes.clear();
        medirColumnasLive(ahoraCuerpo);
        liveAnchoPintado = liveScroll == null ? 0 : liveScroll.getViewport().getWidth();
        ajustarAnchosLive(liveAnchoPintado - 12);   // el reparto se calcula para el ancho real: todo cabe siempre
        boolean plegCurso = Boolean.parseBoolean(leerConfig("live_plegado_curso", "false")), plegFin = Boolean.parseBoolean(leerConfig("live_plegado_fin", "false"));
        ahoraCuerpo.add(tituloPlegable((plegCurso ? "\u25B8 " : "\u25BE ") + t("En partida ahora", "In a game now") + "  ·  " + lista.size() + (lista.size() == 1 ? t(" partida", " game") : t(" partidas", " games")), "live_plegado_curso"));
        if (!plegCurso) {
            RejillaLive rej = new RejillaLive();
            cabeceraLive(rej);
            int i = 1;
            for (Match m : lista) tarjetaLive(rej, i++, m, fichas, false);
            ahoraCuerpo.add(rej);
            if (lista.isEmpty()) { JLabel vac = new JLabel(ahoraCargando ? t("Consultando…", "Checking…") : t("Nadie de esta fuente en partida con estos filtros.", "Nobody from this source in a game with these filters.")); vac.setForeground(Color.GRAY); vac.setAlignmentX(0f); ahoraCuerpo.add(vac); }
        }
        List<Object[]> terminadas; synchronized (liveTerminadas) { liveTerminadas.values().removeIf(x -> System.currentTimeMillis() - (Long) x[1] > 2 * 3_600_000L); terminadas = new ArrayList<>(liveTerminadas.values()); }
        terminadas.removeIf(x -> !liveTipoOk((Match) x[0]) || (!"*".equals(liveMapaSel) && !liveMapaSel.equals(((Match) x[0]).map)));
        if (liveOrden.getSelectedIndex() == 0) terminadas.sort((a, b) -> eloMax.applyAsInt((Match) b[0]) - eloMax.applyAsInt((Match) a[0]));   // por el jugador de mayor ELO de la partida
        else terminadas.sort((a, b) -> Long.compare((Long) b[1], (Long) a[1]));
        ahoraCuerpo.add(Box.createVerticalStrut(10));
        ahoraCuerpo.add(tituloPlegable((plegFin ? "\u25B8 " : "\u25BE ") + t("Terminadas en las últimas 2 horas", "Finished in the last 2 hours") + "  ·  " + terminadas.size() + "   " + t("(sin resultado: la rec, a un clic)", "(no result shown: the rec, one click away)"), "live_plegado_fin"));
        if (!plegFin) {
            RejillaLive rej = new RejillaLive();
            cabeceraLive(rej);
            int i = 1;
            for (Object[] x : terminadas) tarjetaLive(rej, i++, (Match) x[0], fichas, true);
            ahoraCuerpo.add(rej);
            if (terminadas.isEmpty()) { JLabel vac = new JLabel(t("Todavía ninguna (se van acumulando mientras la vista esté abierta).", "None yet (they accumulate while this view is open).")); vac.setForeground(Color.GRAY); vac.setAlignmentX(0f); ahoraCuerpo.add(vac); }
        }
        ahoraCuerpo.revalidate(); ahoraCuerpo.repaint();
        String hace = ahoraUltimaMs == 0 ? "" : t(" · barrido hace ", " · sweep ") + Math.max(0, (System.currentTimeMillis() - ahoraUltimaMs) / 60_000) + t(" min", " min ago");
        ahoraEstado.setText(vivos.size() + t(" de ", " of ") + top.size() + t(" en partida", " in a game") + hace + (socketConectado ? t(" · socket en vivo", " · live socket") : ""));
    }

    static void estiloCab(JLabel l) { l.setFont(l.getFont().deriveFont(Font.BOLD, 11f)); l.setForeground(colorSecundario()); }
    /** Cabecera de un bando, con las mismas celdas que filaJugadorLive. */
    JPanel cabeceraBando() {
        JPanel b = new JPanel(new GridBagLayout()); b.setOpaque(false);
        medirColumnasLive(b);
        GridBagConstraints gc = new GridBagConstraints(); gc.gridy = 0; gc.anchor = GridBagConstraints.WEST;
        String[] textos = { "#", "", t("Jugador", "Player"), "", "ELO", "1v1", t("Civ", "Civ") };
        for (int i = 0; i < textos.length; i++) {
            JLabel l = new JLabel(textos[i], i == 0 ? SwingConstants.RIGHT : SwingConstants.LEFT); estiloCab(l);
            l.setPreferredSize(new Dimension(LIVE_COLS[i], 16)); l.setMinimumSize(new Dimension(LIVE_COLS_MIN[i], 16)); l.setMaximumSize(new Dimension(LIVE_COLS[i], 16));
            gc.gridx = i; gc.insets = new Insets(0, 0, 0, i == 1 || i == 2 ? 4 : 6); b.add(l, gc);
        }
        JPanel relleno = new JPanel(); relleno.setOpaque(false); gc.gridx = textos.length; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(0, 0, 0, 0); b.add(relleno, gc);
        return b;
    }
    /** Cabecera de columnas de Live now: la fila 0 de la rejilla compartida de la sección. */
    void cabeceraLive(RejillaLive cab) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0; gc.anchor = GridBagConstraints.WEST; gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(2, 0, 4, 10);
        JLabel lMapa = new JLabel(t("Mapa · modo", "Map · mode")); estiloCab(lMapa); lMapa.setPreferredSize(new Dimension(liveMapaW, 16)); lMapa.setMinimumSize(new Dimension(liveMapaW, 16));
        gc.gridx = 0; gc.weightx = 0; gc.insets = new Insets(2, 8, 4, liveGapMapa); cab.add(lMapa, gc);
        cab.cabMapa = lMapa;
        gc.insets = new Insets(2, 0, 4, 10);
        gc.gridx = 1; gc.weightx = 1; cab.add(cabeceraBando(), gc);
        JLabel vs = new JLabel(""); vs.setPreferredSize(new Dimension(30, 16)); gc.gridx = 2; gc.weightx = 0; cab.add(vs, gc);
        gc.gridx = 3; gc.weightx = 1; cab.add(cabeceraBando(), gc);
        JLabel lT = new JLabel(t("Tiempo", "Time"), SwingConstants.RIGHT); estiloCab(lT); lT.setPreferredSize(new Dimension(liveTiempoW, 16)); lT.setMinimumSize(new Dimension(liveTiempoW, 16));
        gc.gridx = 4; gc.weightx = 0; cab.add(lT, gc);
        cab.cabTiempo = lT;
        JLabel lB = new JLabel(""); lB.setPreferredSize(new Dimension(liveBotonesW, 16)); lB.setMinimumSize(new Dimension(liveBotonesW, 16)); gc.gridx = 5; gc.insets = new Insets(2, 0, 4, 8); cab.add(lB, gc);
    }

    /** Una tarjeta de partida en rejilla fija: mapa grande · bando A · vs · bando B · reloj · botones. Las subcolumnas de cada jugador van alineadas en todas las tarjetas. */
    /** Sección de Live now: UNA rejilla para la cabecera y todas las tarjetas, así las columnas coinciden siempre, en cualquier anchura. Pinta filas alternas y separadores. */
    static class RejillaLive extends JPanel {
        final List<Component> filas = new ArrayList<>();            // el bloque de mapa de cada fila
        final List<List<Component>> celdasFila = new ArrayList<>();  // todas las celdas de cada fila: el fondo alterno cubre la altura real (en TG el bando es más alto que el mapa)
        Component cabMapa, cabTiempo;
        RejillaLive() { super(new GridBagLayout()); setOpaque(false); setAlignmentX(0f); }
        void celda(int fila, Component c) { while (celdasFila.size() < fila) celdasFila.add(new ArrayList<>()); celdasFila.get(fila - 1).add(c); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int i = 0, yPrimero = -1, yUltimo = -1;
            for (List<Component> cs : celdasFila) {
                if (cs.isEmpty()) continue;
                int y0 = Integer.MAX_VALUE, y1 = 0;
                for (Component c : cs) { y0 = Math.min(y0, c.getY()); y1 = Math.max(y1, c.getY() + c.getHeight()); }
                if (yPrimero < 0) yPrimero = y0 - 4;
                yUltimo = y1 + 4;
                if (i++ % 2 == 1) { g.setColor(new Color(128, 128, 128, temaOscuroActivo ? 22 : 16)); g.fillRoundRect(0, y0 - 4, getWidth(), y1 - y0 + 8, 8, 8); }
            }
            g.setColor(new Color(128, 128, 128, 50));   // separadores verticales tenues: tras el mapa y antes del reloj
            if (cabMapa != null && yPrimero >= 0) { int x = cabMapa.getX() + cabMapa.getWidth() + 20; g.drawLine(x, yPrimero, x, yUltimo); if (cabTiempo != null) { int x2 = cabTiempo.getX() - 6; g.drawLine(x2, yPrimero, x2, yUltimo); } }
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }   // nunca por debajo de lo preferido: si no cabe, se recorta por la derecha, pero no se aplasta
    }
    void tarjetaLive(RejillaLive card, int fila, Match m, Map<Long, Object[]> fichas, boolean terminada) {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = fila; gc.anchor = GridBagConstraints.WEST; gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(6, 0, 6, 10);
        // mapa: miniatura grande + nombre + modo
        JPanel mapa = new JPanel(new BorderLayout(8, 0)); mapa.setOpaque(false);
        JLabel mini = new JLabel(iconoMapa(m.map, 56));
        mini.setPreferredSize(new Dimension(56, 56));
        mapa.add(mini, BorderLayout.WEST);
        JPanel mapaTxt = new JPanel(); mapaTxt.setLayout(new BoxLayout(mapaTxt, BoxLayout.Y_AXIS)); mapaTxt.setOpaque(false);
        JLabel mapaNombre = new JLabel(m.map == null || m.map.isBlank() ? "?" : m.map);
        mapaNombre.setFont(mapaNombre.getFont().deriveFont(Font.BOLD, 14f));
        String modoTxt = m.mode == null ? "" : m.mode;
        boolean ranked = !modoTxt.toLowerCase(Locale.ROOT).contains("unranked") && !modoTxt.toLowerCase(Locale.ROOT).contains("custom") && !modoTxt.isBlank();
        JLabel modo = new JLabel(modoTxt);
        modo.setFont(modo.getFont().deriveFont(ranked ? Font.PLAIN : Font.ITALIC, 12f)); modo.setForeground(ranked ? (temaOscuroActivo ? new Color(0x9f, 0xc5, 0xe8) : new Color(0x2f, 0x5f, 0x8f)) : Color.GRAY);
        mapaTxt.add(mapaNombre); mapaTxt.add(modo);
        { Set<Integer> con50 = new HashSet<>(), con25 = new HashSet<>();
          for (MatchPlayer mp : m.players) { Object[] f = fichas.get(mp.id); if (f == null) continue; if ((Integer) f[3] <= 50) con50.add(mp.team); if ((Integer) f[3] <= 25) con25.add(mp.team); }
          String etq = m.players.size() == 2 && con25.size() >= 2 ? t("élite 25 vs 25", "elite 25 vs 25") : con50.size() >= 2 ? "top 50 vs top 50" : null;
          if (etq != null) { JLabel tt = new JLabel(etq); tt.setFont(tt.getFont().deriveFont(Font.BOLD, 10.5f)); tt.setForeground(temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00)); tt.setToolTipText(m.players.size() == 2 && con25.size() >= 2 ? t("Los dos jugadores están en el top 25 del ladder 1v1", "Both players are in the 1v1 ladder's top 25") : t("Los dos bandos tienen a alguien del top 50 del ladder 1v1", "Both sides have someone from the 1v1 ladder's top 50")); mapaTxt.add(tt); } }
        mapa.add(mapaTxt, BorderLayout.CENTER);
        mapa.setPreferredSize(new Dimension(liveMapaW, 58)); mapa.setMinimumSize(new Dimension(liveMapaW, 58));
        gc.gridx = 0; gc.weightx = 0; gc.insets = new Insets(6, 8, 6, liveGapMapa); card.add(mapa, gc);   // aire tras el mapa: se lee solo
        card.filas.add(mapa); card.celda(fila, mapa);
        gc.insets = new Insets(6, 0, 6, 10);
        // bandos
        Map<Integer, List<MatchPlayer>> equipos = new TreeMap<>();
        for (MatchPlayer mp : m.players) equipos.computeIfAbsent(mp.team, k -> new ArrayList<>()).add(mp);
        List<List<MatchPlayer>> bandos = new ArrayList<>(equipos.values());
        boolean ffa = bandos.size() > 2;
        if (ffa) {   // FFA / custom con más de dos equipos: dos columnas (mitad y mitad); si no, la rejilla necesitaba un ancho por equipo y lo aplastaba todo
            List<MatchPlayer> todos = new ArrayList<>(); for (List<MatchPlayer> b : bandos) todos.addAll(b);
            int mitad = (todos.size() + 1) / 2;
            bandos = new ArrayList<>(List.of(new ArrayList<>(todos.subList(0, mitad)), new ArrayList<>(todos.subList(mitad, todos.size()))));
        }
        int col = 1;
        for (int b = 0; b < 2; b++) {
            if (b > 0) {
                JLabel vs = new JLabel(ffa ? "ffa" : "vs", SwingConstants.CENTER); vs.setForeground(Color.GRAY); vs.setFont(vs.getFont().deriveFont(Font.BOLD, ffa ? 11f : 13f));
                vs.setPreferredSize(new Dimension(30, 20));
                gc.gridx = col++; gc.weightx = 0; card.add(vs, gc);
            }
            JPanel bando = new JPanel(); bando.setLayout(new BoxLayout(bando, BoxLayout.Y_AXIS)); bando.setOpaque(false);
            boolean unoContraUno = m.players.size() == 2;
            if (b < bandos.size()) for (MatchPlayer mp : bandos.get(b)) bando.add(filaJugadorLive(mp, fichas.get(mp.id), unoContraUno));
            bando.setMinimumSize(bando.getPreferredSize());   // el bando nunca encoge (ni en ancho ni en alto)
            gc.gridx = col++; gc.weightx = 1; card.add(bando, gc); card.celda(fila, bando);
        }
        // reloj / estado
        JLabel tiempo = new JLabel("", SwingConstants.RIGHT);
        tiempo.setPreferredSize(new Dimension(liveTiempoW, 24)); tiempo.setMinimumSize(new Dimension(liveTiempoW, 24));
        if (terminada) {
            long hace = m.finished == null ? 0 : Duration.between(m.finished, Instant.now()).toMinutes();
            tiempo.setText(t("hace ", "") + hace + t(" min", " min ago"));
            tiempo.setFont(tiempo.getFont().deriveFont(Font.PLAIN, 13f)); tiempo.setForeground(Color.GRAY);
            tiempo.setToolTipText(t("Terminó hace ", "Finished ") + hace + t(" minutos", " minutes ago"));
        } else {
            tiempo.putClientProperty("match", m);
            tiempo.setText(m.started == null ? "" : reloj(Duration.between(m.started, Instant.now())));
            tiempo.setFont(tiempo.getFont().deriveFont(Font.BOLD, 16f));
            tiempo.setForeground(temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00));
            tiempo.setToolTipText(t("Tiempo de partida (reloj de pared desde el inicio)", "Game time (wall clock since start)"));
            liveRelojes.add(tiempo);
        }
        gc.gridx = col++; gc.weightx = 0; card.add(tiempo, gc); card.celda(fila, tiempo);
        // botones
        JPanel botones = new JPanel(); botones.setLayout(new BoxLayout(botones, BoxLayout.Y_AXIS)); botones.setOpaque(false);
        botones.setPreferredSize(new Dimension(liveBotonesW, 44)); botones.setMinimumSize(new Dimension(liveBotonesW, 44));
        if (terminada) {
            if (m.enJuego) {
                JLabel ok = new JLabel("\u2713 " + t("Enviada al juego", "Sent to the game")); ok.setForeground(colorWr(60, 100)); ok.setFont(ok.getFont().deriveFont(Font.BOLD, 12.5f)); ok.setAlignmentX(1f);
                botones.add(Box.createVerticalGlue()); botones.add(ok); botones.add(Box.createVerticalGlue());
            } else if (m.enDisco) {
                JLabel ok = new JLabel("\u2713 " + t("Descargada", "Downloaded")); ok.setForeground(colorWr(60, 100)); ok.setFont(ok.getFont().deriveFont(Font.BOLD, 12.5f)); ok.setAlignmentX(1f);
                botones.add(ok);
                JButton env = new JButton(t("Enviar al juego", "Send to game"));
                env.setFocusable(false); env.setMargin(new Insets(1, 8, 1, 8)); env.putClientProperty("JButton.buttonType", "roundRect"); env.setAlignmentX(1f);
                env.addActionListener(e -> { descargaSinCambiarVista = true; alTerminarDescarga = this::ahoraPintar; download(List.of(m), true); });
                botones.add(env);
            } else {
                JButton rec = new JButton(t("Descargar rec", "Download rec"));
                rec.setFocusable(false); rec.setMargin(new Insets(1, 8, 1, 8)); rec.putClientProperty("JButton.buttonType", "roundRect");
                rec.setToolTipText(t("Descarga la grabación de esta partida (sin resultado); te quedas en esta pantalla", "Download this game's recording (no result shown); you stay on this screen"));
                rec.addActionListener(e -> { if (m.refId == 0 && !m.players.isEmpty()) m.refId = m.players.get(0).id; rec.setEnabled(false); rec.setText(t("Descargando…", "Downloading…")); descargaSinCambiarVista = true; alTerminarDescarga = this::ahoraPintar; download(List.of(m)); });
                rec.setAlignmentX(1f);
                JLabel res = new JLabel("<html><u>" + t("Resultado", "Result") + "</u></html>"); res.setFont(res.getFont().deriveFont(Font.PLAIN, 11f)); res.setForeground(colorSecundario()); res.setAlignmentX(1f);
                res.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); res.setToolTipText(t("Revela quién ganó esta partida (solo aquí)", "Reveals who won this game (only here)"));
                res.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) {
                    Runnable pintar = () -> {
                        List<String> ganadores = new ArrayList<>(); for (MatchPlayer mp : m.players) if (Boolean.TRUE.equals(mp.won)) ganadores.add(nombreVisible(mp.id, mp.name));
                        res.setText("<html><b>" + (ganadores.isEmpty() ? t("Aún sin resultado (unos minutos)", "No result yet (a few minutes)") : t("Ganó ", "Won: ") + escapeHtml(String.join(", ", ganadores))) + "</b></html>"); res.setForeground(ganadores.isEmpty() ? colorSecundario() : colorWr(100, 100)); res.setCursor(Cursor.getDefaultCursor());
                    };
                    boolean hay = false; for (MatchPlayer mp : m.players) if (mp.won != null) hay = true;
                    if (hay || m.players.isEmpty()) { pintar.run(); return; }
                    res.setText(t("consultando…", "checking…"));
                    long pid0 = m.players.get(0).id;
                    new Thread(() -> {   // el socket avisa del final antes de que la API tenga el resultado: una llamada, solo si lo pides
                        try { Iterable<Match> leidas = COMPANION.partidas(pid0, 1, 5); for (Match x : leidas) { if (x != null && x.id == m.id) { for (MatchPlayer mp : m.players) for (MatchPlayer xp : x.players) if (xp.id == mp.id) mp.won = xp.won; break; } } } catch (Exception ex) { log("resultado: " + causa(ex)); }
                        SwingUtilities.invokeLater(pintar);
                    }, "resultado").start();
                } });
                botones.add(Box.createVerticalGlue()); botones.add(rec); botones.add(Box.createVerticalStrut(2)); botones.add(res); botones.add(Box.createVerticalGlue());
            }
        } else if (m.id > 0) {
            JButton esp = new JButton(t("Espectar", "Spectate"));
            esp.setFocusable(false); esp.setMargin(new Insets(1, 8, 1, 8)); esp.putClientProperty("JButton.buttonType", "roundRect"); esp.setAlignmentX(1f);
            esp.addActionListener(e -> { if (confirmarEspectar(m.map == null ? "" : m.map)) espectarPartida(m.id); });   // el mismo aviso «va a abrir el juego» que en el resto de la app
            botones.add(Box.createVerticalGlue()); botones.add(esp); botones.add(Box.createVerticalGlue());
        }
        gc.gridx = col; gc.insets = new Insets(6, 0, 6, 8); card.add(botones, gc); card.celda(fila, botones);
    }

    static final int[] LIVE_COLS_BASE = { 44, 26, 180, 26, 56, 66, 150 };   // puesto · bandera · nick · TW · ELO · 1v1 · civ: anchos base
    static int[] LIVE_COLS = LIVE_COLS_BASE.clone(), LIVE_COLS_MIN = LIVE_COLS_BASE.clone();   // las celdas numéricas se miden con la fuente real (DPI, Segoe…); ninguna celda encoge nunca
    static boolean liveColsMedidas;
    /** Mide con la fuente de verdad lo que ocupan «#250», «(2999)» y «1v1 2999» y ajusta las celdas: así nunca se recortan, sea cual sea el escalado. */
    static void medirColumnasLive(JComponent c) {
        if (liveColsMedidas) return;
        FontMetrics fmPuesto = c.getFontMetrics(c.getFont().deriveFont(Font.BOLD, 11.5f)), fmElo = c.getFontMetrics(c.getFont().deriveFont(Font.PLAIN, 12.5f)), fmUno = c.getFontMetrics(c.getFont().deriveFont(Font.PLAIN, 11f));
        int puesto = Math.max(LIVE_COLS_BASE[0], fmPuesto.stringWidth("#250") + 8), elo = Math.max(LIVE_COLS_BASE[4], fmElo.stringWidth("(2999)") + 8), uno = Math.max(LIVE_COLS_BASE[5], fmUno.stringWidth("1v1 2999") + 10);
        LIVE_COLS_MEDIDAS = new int[]{ puesto, LIVE_COLS_BASE[1], LIVE_COLS_BASE[2], LIVE_COLS_BASE[3], elo, uno, LIVE_COLS_BASE[6] };
        LIVE_COLS = LIVE_COLS_MEDIDAS.clone();
        LIVE_COLS_MIN = LIVE_COLS.clone();
        liveColsMedidas = true;
    }
    static int[] LIVE_COLS_MEDIDAS = LIVE_COLS_BASE.clone();
    static int liveMapaW = 210, liveGapMapa = 40, liveTiempoW = 110, liveBotonesW = 170;
    /** Reparte el ancho disponible: si la rejilla completa no cabe, ceden por este orden el aire tras el mapa, el bloque de mapa, los botones, el reloj y por último nick y civ (con «…»). Nunca hay scroll horizontal. */
    static void ajustarAnchosLive(int disponible) {
        int[] c = LIVE_COLS_MEDIDAS.clone();
        int mapa = 210, gap = 40, tiempo = 110, botones = 170;
        java.util.function.IntSupplier total = () -> { int bando = 10; for (int i = 0; i < c.length; i++) bando += c[i] + (i == 1 || i == 2 ? 4 : 6); return 8 + mapaHolder[0] + gapHolder[0] + 2 * bando + 10 + 30 + 10 + tiempoHolder[0] + 10 + botonesHolder[0] + 8; };
        mapaHolder[0] = mapa; gapHolder[0] = gap; tiempoHolder[0] = tiempo; botonesHolder[0] = botones;
        if (disponible > 100) {
            if (total.getAsInt() > disponible) gapHolder[0] = 16;
            if (total.getAsInt() > disponible) mapaHolder[0] = 160;
            if (total.getAsInt() > disponible) botonesHolder[0] = 130;
            if (total.getAsInt() > disponible) tiempoHolder[0] = 80;
            if (total.getAsInt() > disponible) {   // nick y civ ceden a partes iguales, hasta un mínimo legible
                int sobra = total.getAsInt() - disponible;
                int quitaNick = Math.min(c[2] - 90, (sobra + 3) / 4), quitaCiv = Math.min(c[6] - 70, (sobra + 3) / 4);
                c[2] -= Math.max(0, quitaNick); c[6] -= Math.max(0, quitaCiv);
                sobra = total.getAsInt() - disponible;
                if (sobra > 0) { int q = Math.min(c[2] - 90, (sobra + 3) / 2); c[2] -= Math.max(0, q); sobra = total.getAsInt() - disponible; if (sobra > 0) c[6] -= Math.max(0, Math.min(c[6] - 70, (sobra + 3) / 2)); }
            }
        }
        LIVE_COLS = c; LIVE_COLS_MIN = c.clone();
        liveMapaW = mapaHolder[0]; liveGapMapa = gapHolder[0]; liveTiempoW = tiempoHolder[0]; liveBotonesW = botonesHolder[0];
    }
    static final int[] mapaHolder = { 210 }, gapHolder = { 40 }, tiempoHolder = { 110 }, botonesHolder = { 170 };
    /** Etiqueta que recorta su texto con «…» al ancho que tenga en cada momento (nunca se parte en dos líneas ni se sale). */
    static class EtiquetaRecorte extends JLabel {
        String completo = "";
        EtiquetaRecorte() { super(); }
        void texto(String s) { completo = s == null ? "" : s; super.setText(completo); }
        @Override protected void paintComponent(Graphics g) {
            int disponible = getWidth() - getInsets().left - getInsets().right - (getIcon() != null ? getIcon().getIconWidth() + getIconTextGap() : 0);
            String vis = truncarPx(completo, getFontMetrics(getFont()), Math.max(10, disponible));
            if (!vis.equals(getText())) super.setText(vis);
            super.paintComponent(g);
        }
    }
    /** Un jugador dentro de un bando, en texto plano y celdas fijas: nada se parte ni se desplaza, sea cual sea el nick. */
    javax.swing.Timer liveHoverTimer;
    JPanel filaJugadorLive(MatchPlayer mp, Object[] ficha, boolean grande) {
        JPanel fila = new JPanel(new GridBagLayout()); fila.setOpaque(false); fila.setAlignmentX(0f);
        medirColumnasLive(fila);
        GridBagConstraints gc = new GridBagConstraints(); gc.gridy = 0; gc.anchor = GridBagConstraints.WEST; gc.insets = new Insets(grande ? 3 : 1, 0, grande ? 3 : 1, 6);
        int altoFila = grande ? 26 : 22;
        Color ambar = temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00);
        JLabel puesto = new JLabel(ficha != null ? "#" + ficha[3] : "", SwingConstants.RIGHT);
        puesto.setFont(puesto.getFont().deriveFont(Font.BOLD, 11.5f));
        puesto.setForeground(ficha != null && (Integer) ficha[3] <= 50 ? ambar : Color.GRAY);
        if (ficha != null) puesto.setToolTipText(t("Puesto ", "Rank ") + ficha[3] + t(" del ladder 1v1 · ELO 1v1 ", " on the 1v1 ladder · 1v1 ELO ") + ficha[2]);
        String cc = ficha != null ? (String) ficha[4] : paisDe(mp.id);
        JLabel bandera = new JLabel(iconoBandera(cc, 18));
        String nick = nombreVisible(mp.id, mp.name);
        EtiquetaRecorte nombre = new EtiquetaRecorte();
        nombre.setFont(nombre.getFont().deriveFont(Font.BOLD, 15f));
        nombre.texto(nick);   // recorte con «…» al ancho real: nunca dos líneas
        nombre.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nombre.addMouseListener(new MouseAdapter() {   // sin tooltip de texto: solo la tarjeta de ELO al posar el ratón
            @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isMiddleMouseButton(e)) abrirPerfilEnPestana(mp.id, nick); else if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) { ocultarHoverCard(true); abrirPerfil(mp.id, nick); } }
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) menuContextualJugador(mp.id, nick, e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) menuContextualJugador(mp.id, nick, e); }

        });
        JLabel elo = new JLabel(mp.rating == null ? "" : "(" + mp.rating + ")");
        elo.setFont(elo.getFont().deriveFont(Font.PLAIN, 12.5f)); elo.setForeground(Color.GRAY);
        JLabel uno = new JLabel(!grande && ficha != null && (Integer) ficha[2] > 0 ? "1v1 " + ficha[2] : "");   // en equipos: el 1v1 de los del top, en ámbar
        uno.setFont(uno.getFont().deriveFont(Font.PLAIN, 11f)); uno.setForeground(ambar);
        String claveCiv = claveCivDeNombre(mp.civ);
        EtiquetaRecorte civ = new EtiquetaRecorte(); civ.setIcon(claveCiv == null ? null : iconoCiv(claveCiv, 20)); civ.texto(mp.civ == null ? "" : claveCiv != null ? nombreCivStats(claveCiv) : mp.civ);   // siempre el mismo nombre e icono, venga en el idioma que venga
        civ.setIconTextGap(5); civ.setFont(civ.getFont().deriveFont(Font.PLAIN, 13.5f)); civ.setForeground(UIManager.getColor("Label.foreground"));
        String[] tw = twitchLive.get(mp.id);
        JLabel twl = new JLabel(tw != null ? "TW" : "");
        twl.setFont(twl.getFont().deriveFont(Font.BOLD, 12f)); twl.setForeground(new Color(0x91, 0x46, 0xFF));
        if (tw != null) { twl.setToolTipText(t("En directo en twitch.tv/", "Live on twitch.tv/") + tw[0]); twl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); twl.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { abrirUrl("https://twitch.tv/" + tw[0]); } }); }
        JComponent[] celdas = { puesto, bandera, nombre, twl, elo, uno, civ };   // el TW, pegado al nick
        for (int i = 0; i < celdas.length; i++) {
            celdas[i].setPreferredSize(new Dimension(LIVE_COLS[i], altoFila)); celdas[i].setMinimumSize(new Dimension(LIVE_COLS_MIN[i], altoFila)); celdas[i].setMaximumSize(new Dimension(LIVE_COLS[i], altoFila));
            gc.gridx = i; gc.insets = new Insets(grande ? 3 : 1, 0, grande ? 3 : 1, i == 1 || i == 2 ? 4 : 6);   // bandera y TW, pegados al nick
            fila.add(celdas[i], gc);
        }
        JPanel relleno = new JPanel(); relleno.setOpaque(false);
        gc.gridx = celdas.length; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(0, 0, 0, 0); fila.add(relleno, gc);
        return fila;
    }

    /** Menú contextual de un jugador fuera de la watchlist (Live now, listas): perfil, pestaña nueva y, si está en partida, aliados y rivales. */
    void menuContextualJugador(long pid, String nombre, MouseEvent e) {
        JPopupMenu pm = new JPopupMenu();
        JMenuItem perf = new JMenuItem(t("Ver perfil", "View profile")); perf.addActionListener(a -> abrirPerfil(pid, nombre)); pm.add(perf);
        JMenuItem perfN = new JMenuItem(t("Abrir perfil en pestaña nueva", "Open profile in a new tab")); perfN.addActionListener(a -> abrirPerfilEnPestana(pid, nombre)); pm.add(perfN);
        JMenu enPartida = menuEnPartida(pid);
        if (enPartida != null) { pm.addSeparator(); pm.add(enPartida); }
        pm.show(e.getComponent(), e.getX(), e.getY());
    }

    /** Submenú «En partida ahora»: aliados y rivales de la partida en curso de pid, con bandera, ELO y civ; clic = su perfil. */
    JMenu menuEnPartida(long pid) {
        Match m = VIVO_PARTIDA.get(pid);
        if (m == null || !enCursoReal(m)) return null;
        MatchPlayer yo = null; for (MatchPlayer p : m.players) if (p.id == pid) yo = p;
        if (yo == null) return null;
        JMenu menu = new JMenu(t("En partida ahora", "In a game now") + (m.map != null ? " · " + m.map : "") + (m.started != null ? " · " + reloj(Duration.between(m.started, Instant.now())) : ""));
        List<MatchPlayer> aliados = new ArrayList<>(), rivales = new ArrayList<>();
        for (MatchPlayer p : m.players) { if (p.id == pid) continue; if (p.team == yo.team) aliados.add(p); else rivales.add(p); }
        if (!aliados.isEmpty()) {
            JMenuItem cab = new JMenuItem(t("Aliados", "Allies")); cab.setEnabled(false); menu.add(cab);
            for (MatchPlayer p : aliados) menu.add(itemJugadorPartida(p));
            menu.addSeparator();
        }
        JMenuItem cab2 = new JMenuItem(t("Rivales", "Opponents")); cab2.setEnabled(false); menu.add(cab2);
        for (MatchPlayer p : rivales) menu.add(itemJugadorPartida(p));
        if (m.id > 0) { menu.addSeparator(); JMenuItem esp = new JMenuItem(t("Espectar la partida", "Spectate the game")); esp.addActionListener(a -> espectarPartida(m.id)); menu.add(esp); }
        return menu;
    }
    static final Map<Long, Integer> ELO_1V1 = new java.util.concurrent.ConcurrentHashMap<>();   // pid → ELO 1v1 RM (sesión)
    Integer elo1v1Conocido(long pid) {
        Integer e = ELO_1V1.get(pid); if (e != null) return e > 0 ? e : null;
        Object[] f = liveFicha(pid); if (f != null && (Integer) f[2] > 0) return (Integer) f[2];
        e = ELO_VINC.get(pid); if (e != null && e > 0) return e;
        Object[] perfil = PERFIL_CACHE.get(pid); if (perfil != null && perfil[1] instanceof Map<?, ?> mp && mp.get("rm_1v1") instanceof int[] v && v[0] > 0) return v[0];
        return null;
    }
    JMenuItem itemJugadorPartida(MatchPlayer p) {
        String nombre = nombreVisible(p.id, p.name);
        Integer e1 = elo1v1Conocido(p.id);
        JMenuItem it = new JMenuItem(nombre + (e1 != null ? "  1v1 " + e1 : "") + (p.civ != null && !p.civ.isBlank() ? "  ·  " + p.civ : ""), iconoBandera(paisDe(p.id)));
        it.setIconTextGap(6);
        it.addActionListener(a -> abrirPerfil(p.id, nombre));
        if (e1 == null && !ELO_1V1.containsKey(p.id)) new Thread(() -> {   // el ELO 1v1 se pide una vez y se rellena en el propio ítem
            Integer e = eloDeLadder(p.id); ELO_1V1.put(p.id, e == null ? 0 : e);
            if (e != null && e > 0) SwingUtilities.invokeLater(() -> it.setText(nombre + "  1v1 " + e + (p.civ != null && !p.civ.isBlank() ? "  ·  " + p.civ : "")));
        }, "elo-1v1").start();
        return it;
    }

    // ----- Campanas: aviso cuando alguien de una vista marcada entra en partida -----
    JToggleButton campanaBtn;
    final Map<String, Set<Long>> campanaIds = new java.util.concurrent.ConcurrentHashMap<>();   // id de vista → jugadores vigilados
    final Set<Long> avisados = java.util.concurrent.ConcurrentHashMap.newKeySet();               // pid|matchId ya avisados (en un long compuesto no cabe: usamos texto)
    final Set<String> avisadosClave = java.util.concurrent.ConcurrentHashMap.newKeySet();
    javax.swing.Timer campanasTimer; JPanel toast; javax.swing.Timer toastTimer;

    String idVistaCampana() {
        if (modoClan()) return "\u2605clan|" + (clanField == null ? "" : clanField.getText().trim().toLowerCase(Locale.ROOT));
        if (modoPais()) return "\u2605pais|" + paisSel();
        if (modoTop()) return "\u2605ladder";
        return "grupo|" + String.valueOf(grupoCombo.getSelectedItem());
    }
    Set<String> campanas() { Set<String> s = new LinkedHashSet<>(); for (String x : leerConfig("campanas", "").split("\\u0001")) if (!x.isBlank()) s.add(x); return s; }
    void guardarCampanas(Set<String> s) { guardarConfig("campanas", String.join("\\u0001", s)); }
    boolean campanaContiene(long pid) { for (Set<Long> ids : campanaIds.values()) if (ids.contains(pid)) return true; return false; }

    void refrescarCampanaBtn() {
        if (campanaBtn == null) return;
        boolean on = campanas().contains(idVistaCampana());
        campanaBtn.setSelected(on);
        campanaBtn.setForeground(on ? (temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00)) : UIManager.getColor("Button.foreground"));
        campanaBtn.setToolTipText(on ? t("Avisos activados para esta lista: te avisa cuando alguien de aquí entre en partida (clic para apagar)", "Alerts on for this list: you get a notice when someone here starts a game (click to turn off)")
                : t("Activar para recibir un aviso cuando alguien de esta lista entre en partida", "Turn on to get a notice when someone in this list starts a game"));
    }

    void alternarCampana() {
        Set<String> s = campanas();
        String id = idVistaCampana();
        if (!s.remove(id)) s.add(id);
        guardarCampanas(s);
        refrescarCampanaBtn();
        refrescarCampanas();
        status.setText(s.contains(id) ? t("Avisos activados para «", "Alerts on for \u201C") + id.replace("\u2605", "\u2605 ").replace("|", " ") + t("»: te avisaré cuando alguien entre en partida.", "\u201D: you'll get a notice when someone starts a game.") : t("Avisos apagados para esta lista.", "Alerts off for this list."));
    }

    /** Recalcula (en segundo plano) los jugadores de cada vista con campana y los mete en el socket. Cada 15 min para tops, país y clan. */
    void refrescarCampanas() {
        Set<String> s = campanas();
        if (s.isEmpty()) { campanaIds.clear(); SwingUtilities.invokeLater(this::sincronizarSocket); return; }
        new Thread(() -> {
            Map<String, Set<Long>> nuevo = new HashMap<>();
            for (String id : s) {
                Set<Long> ids = new HashSet<>();
                try {
                    if (id.startsWith("grupo|")) { String g = id.substring(6); for (Player p : todosJugadores) if (g.equalsIgnoreCase(t("Todos", "All")) || p.grupo().equalsIgnoreCase(g)) ids.add(p.id()); }
                    else if (id.equals("\u2605ladder")) { for (long pid : idsLeaderboard(null, Integer.parseInt(leerConfig("top_n", "50")))) ids.add(pid); }
                    else if (id.startsWith("\u2605pais|")) { for (long pid : idsLeaderboard(id.substring(6), Integer.parseInt(leerConfig("top_n", "50")))) ids.add(pid); }
                    else if (id.startsWith("\u2605clan|")) { if (ladderAsegurar(false) == null) for (LadderRow r : miembrosClan(id.substring(6))) ids.add(r.pid()); }
                } catch (Exception ex) { log("campanas " + id + ": " + causa(ex)); }
                nuevo.put(id, ids);
            }
            campanaIds.clear(); campanaIds.putAll(nuevo);
            Set<Long> todos = new HashSet<>(); for (Set<Long> x : nuevo.values()) todos.addAll(x);
            synchronized (ahoraTop) { for (Object[] f : ahoraTop) todos.add((Long) f[0]); }
            socketExtra.retainAll(todos); socketExtra.addAll(todos);
            SwingUtilities.invokeLater(this::sincronizarSocket);
        }, "campanas").start();
    }

    /** Ids del top del ladder 1v1 (global o de un país), sin tocar la vista. */
    List<Long> idsLeaderboard(String pais, int n) throws Exception {
        List<Long> out = new ArrayList<>();
        for (FilaClasificacion f : COMPANION.clasificacion("rm_1v1", 1, Math.min(100, Math.max(25, n)), pais).filas()) { long pid = f.pid(); if (pid > 0) out.add(pid); if (out.size() >= n) break; }
        return out;
    }

    /** «Mi partida»: si el que entra en partida soy yo (mi_pid), aviso con el rival (bandera, ELO, civ) y accesos a su perfil y al cara a cara. */
    void avisarMiPartida(long pid, Match m) {
        String mi = leerConfig("mi_pid", "");
        if (mi.isBlank() || pid != Long.parseLong(mi) || m == null) return;
        if (!avisadosClave.add("mi|" + m.id)) return;
        log("mi partida: el socket dice que mi partida " + m.id + " ha empezado (" + m.map + ", " + m.mode + ")");
        MatchPlayer yo0 = null; for (MatchPlayer p : m.players) if (p.id == pid) yo0 = p;
        if (yo0 == null) return;
        final MatchPlayer yo = yo0;
        List<MatchPlayer> rivales = new ArrayList<>(); for (MatchPlayer p : m.players) if (p.team != yo.team) rivales.add(p);
        StringBuilder txt = new StringBuilder("\u25CF " + t("Tu partida ha empezado", "Your game has started") + " \u00B7 " + (m.map == null ? "" : m.map) + " \u00B7 " + modoCorto(m) + " \u00B7 " + t("vs ", "vs "));
        for (int i = 0; i < rivales.size(); i++) { MatchPlayer r = rivales.get(i); if (i > 0) txt.append(", "); txt.append(nombreVisible(r.id, r.name)); Integer e1 = elo1v1Conocido(r.id); if (e1 == null && r.rating != null) e1 = r.rating; if (e1 != null) txt.append(" (").append(e1).append(")"); if (r.civ != null) txt.append(" ").append(r.civ); }
        final MatchPlayer rival = rivales.isEmpty() ? null : rivales.get(0);
        List<Object[]> fichas = new ArrayList<>(); for (MatchPlayer r : rivales) { Integer e1 = elo1v1Conocido(r.id); if (e1 == null) e1 = r.rating; fichas.add(new Object[]{ r.id, nombreVisible(r.id, r.name) + (r.civ != null ? "  ·  " + r.civ : ""), e1 }); }
        final String txtSup = "\u25CF " + t("Tu partida empieza", "Your game starts") + " \u00B7 " + (m.map == null ? "" : m.map) + " \u00B7 " + modoCorto(m);
        SwingUtilities.invokeLater(() -> {
            mostrarSuperposicion(txtSup, fichas, 60_000);   // sobre el juego, mientras carga
            mostrarToast(txt.toString(), m.id);
            if (rival != null && rival.id > 0 && toast != null) {   // accesos rápidos en el propio aviso
                JButton perf = new JButton(t("Su perfil", "Their profile")); perf.setFocusable(false); perf.setMargin(new Insets(0, 6, 0, 6)); perf.addActionListener(a -> abrirPerfilEnPestana(rival.id, nombreVisible(rival.id, rival.name)));
                JButton cara = new JButton(t("Cara a cara", "Head-to-head")); cara.setFocusable(false); cara.setMargin(new Insets(0, 6, 0, 6)); cara.addActionListener(a -> { abrirPerfil(pid, leerConfig("mi_nombre", nombreVisible(pid, yo.name))); javax.swing.Timer tt = new javax.swing.Timer(1200, ev -> { if (actPid == pid) { mostrarCaraACara(); if (h2hDialogo != null) h2hFijar(rival.id, nombreVisible(rival.id, rival.name)); } }); tt.setRepeats(false); tt.start(); });
                JPanel acc = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); acc.setOpaque(false); acc.add(perf); acc.add(cara);
                toast.add(acc, BorderLayout.SOUTH); toast.revalidate();
            }
        });
    }
    /** Alguien vigilado entra en partida: si está en una lista con campana, aviso (toast dentro de la app; Windows si está minimizada). */
    void avisarSiCampana(long pid, Match m) {
        if (m == null || !campanaContiene(pid)) return;
        String clave = pid + "|" + m.id;
        if (!avisadosClave.add(clave)) return;
        String nombre = nombreVisible(pid, ahoraNombre(pid).equals(String.valueOf(pid)) ? nombreDe(pid) : ahoraNombre(pid));
        String resumen = resumenVivo(m, pid);
        String texto = "\u25CF " + nombre + t(" ha empezado una partida", " started a game") + (resumen != null ? " · " + resumen : "");
        SwingUtilities.invokeLater(() -> mostrarToast(texto, m.id));   // solo dentro de la app: nada de notificaciones de Windows
    }
    String nombreDe(long pid) { for (Player p : todosJugadores) if (p.id() == pid) return p.name(); for (Player p : topLadder) if (p.id() == pid) return p.name(); return String.valueOf(pid); }

    void mostrarToast(String texto, long matchId) {
        JLayeredPane capa = getLayeredPane();
        if (toast != null) capa.remove(toast);
        toast = new JPanel(new BorderLayout(8, 0));
        toast.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00), 1, true), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        JLabel l = new JLabel(texto);
        toast.add(l, BorderLayout.CENTER);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); botones.setOpaque(false);
        if (matchId > 0) { JButton esp = new JButton(t("Espectar", "Spectate")); esp.setFocusable(false); esp.setMargin(new Insets(1, 8, 1, 8)); esp.addActionListener(e -> { espectarPartida(matchId); ocultarToast(); }); botones.add(esp); }
        JButton x = new JButton("\u00D7"); x.setFocusable(false); x.setMargin(new Insets(1, 6, 1, 6)); x.addActionListener(e -> ocultarToast()); botones.add(x);
        toast.add(botones, BorderLayout.EAST);
        Dimension d = toast.getPreferredSize();
        toast.setBounds(getRootPane().getWidth() - d.width - 24, getRootPane().getHeight() - d.height - 56, d.width, d.height);
        capa.add(toast, JLayeredPane.POPUP_LAYER);
        capa.repaint();
        if (toastTimer != null) toastTimer.stop();
        toastTimer = new javax.swing.Timer(10_000, e -> ocultarToast()); toastTimer.setRepeats(false); toastTimer.start();
    }
    void ocultarToast() { if (toast != null) { getLayeredPane().remove(toast); getLayeredPane().repaint(); toast = null; } }

    // =====================================================================================
    // BANDERAS — banderas de 20×15 en banderas/<cc>.png (dominio público, Wikimedia vía
    // hampusborgos/country-flags; el bat las deja junto al exe). El país en sí vive en cache.Paises.
    // =====================================================================================
    static final Path BANDERAS_DIR = Path.of("banderas");
    static final Map<String, ImageIcon> ICONOS_BANDERA = new java.util.concurrent.ConcurrentHashMap<>();

    /** Bandera del país (código ISO de dos letras), o null si no hay archivo. */
    static ImageIcon iconoBandera(String cc) {
        if (cc == null) return null;
        String k = cc.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty() || "null".equals(k)) return null;
        ImageIcon ic = ICONOS_BANDERA.get(k);
        if (ic != null) return ic;
        Path p = BANDERAS_DIR.resolve(k + ".png");
        if (!Files.exists(p)) p = BANDERAS_DIR.resolve("banderas").resolve(k + ".png");   // «Extraer todo» de Windows anida la carpeta
        if (!Files.exists(p)) return null;
        ic = new ImageIcon(p.toString());
        if (ic.getIconWidth() <= 0) return null;
        ICONOS_BANDERA.put(k, ic);
        return ic;
    }
    /** Bandera escalada a una altura dada (para Live now en 1v1). */
    static ImageIcon iconoBandera(String cc, int alto) {
        ImageIcon base = iconoBandera(cc);
        if (base == null || alto <= 15) return base;
        String k = cc.trim().toLowerCase(Locale.ROOT) + "@" + alto;
        ImageIcon ic = ICONOS_BANDERA.get(k);
        if (ic != null) return ic;
        ic = new ImageIcon(base.getImage().getScaledInstance(alto * 4 / 3, alto, Image.SCALE_SMOOTH));
        ICONOS_BANDERA.put(k, ic);
        return ic;
    }
    /** La misma bandera dentro de un texto HTML (para el renderer de la watchlist). */
    static String banderaHtml(String cc) {
        if (cc == null) return "";
        String k = cc.trim().toLowerCase(Locale.ROOT);
        Path p = BANDERAS_DIR.resolve(k + ".png");
        if (!Files.exists(p)) p = BANDERAS_DIR.resolve("banderas").resolve(k + ".png");
        if (!Files.exists(p)) return "";
        return "<img src='" + p.toUri() + "' width='16' height='12'>&nbsp;";
    }

    // =====================================================================================
    // Pestañas de perfiles (una tira sobre el panel de perfil) e historial de navegación («←», botón lateral del ratón)
    // =====================================================================================
    static final int PERFIL_MAX_PESTANAS = 6;
    final List<Object[]> perfilPestanas = new ArrayList<>();   // {pid Long, nombre String}
    int perfilPestanaActiva = -1;
    JPanel perfilTira;
    boolean ultimoClicCtrl;
    record Destino(String vista, long pid, String nombre, String civ) { }
    final List<Destino> historial = new ArrayList<>();
    int historialPos = -1;
    boolean navegandoAtras;
    JButton atrasBtn, adelanteBtn;

    void registrarDestino(Destino d) {
        if (navegandoAtras) return;
        if (historialPos >= 0 && historialPos < historial.size()) {
            Destino u = historial.get(historialPos);
            if (u.vista().equals(d.vista()) && u.pid() == d.pid() && Objects.equals(u.civ(), d.civ())) return;
        }
        while (historial.size() > historialPos + 1) historial.remove(historial.size() - 1);   // una ruta nueva borra el «adelante»
        historial.add(d);
        while (historial.size() > 60) historial.remove(0);
        historialPos = historial.size() - 1;
        actualizarBotonesHistorial();
    }
    void actualizarBotonesHistorial() {
        if (atrasBtn != null) atrasBtn.setEnabled(historialPos > 0);
        if (adelanteBtn != null) adelanteBtn.setEnabled(historialPos >= 0 && historialPos < historial.size() - 1);
    }
    /** Vuelve a la vista anterior (perfil, civ del tech tree, Civ Stats…). */
    void volverAtras() {
        if (historialPos <= 0) return;
        historialPos--;
        navegandoAtras = true;
        try { irA(historial.get(historialPos)); } finally { navegandoAtras = false; }
        actualizarBotonesHistorial();
    }
    void irAdelante() {
        if (historialPos < 0 || historialPos >= historial.size() - 1) return;
        historialPos++;
        navegandoAtras = true;
        try { irA(historial.get(historialPos)); } finally { navegandoAtras = false; }
        actualizarBotonesHistorial();
    }
    void irA(Destino d) {
        switch (d.vista()) {
            case "directos" -> mostrarDirectos(true);
            case "ladder" -> abrirLadder();
            case "civstats" -> abrirCivStats();
            case "ahora" -> abrirAhora();
            case "techtree" -> abrirTechTree(d.civ());
            case "perfil" -> abrirPerfil(d.pid(), d.nombre());
            default -> mostrarDirectos(false);
        }
    }

    int indicePestana(long pid) { for (int i = 0; i < perfilPestanas.size(); i++) if ((Long) perfilPestanas.get(i)[0] == pid) return i; return -1; }

    /** Abre a un jugador en una pestaña NUEVA (Ctrl+clic, «+»); si ya está abierto, se activa la suya. */
    void abrirPerfilEnPestana(long pid, String nombre) {
        int i = indicePestana(pid);
        if (i >= 0) { perfilPestanaActiva = i; abrirPerfil(pid, nombre); return; }
        if (perfilPestanas.size() >= PERFIL_MAX_PESTANAS) {
            JOptionPane.showMessageDialog(this, t("Ya hay " + PERFIL_MAX_PESTANAS + " perfiles abiertos, el máximo. Cierra alguno con su × para abrir otro.\n(Sin Ctrl, el clic abre al jugador en la pestaña actual.)",
                    "There are already " + PERFIL_MAX_PESTANAS + " profiles open, the maximum. Close one with its × to open another.\n(Without Ctrl, a click opens the player in the current tab.)"),
                    t("Pestañas de perfil", "Profile tabs"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        perfilPestanaActiva = -1;   // la siguiente apertura crea pestaña
        abrirPerfil(pid, nombre);
    }

    /** Lleva la cuenta de pestañas al abrir un perfil: la existente, la actual (sustituyendo), o una nueva. */
    void perfilContabilizarPestana(long pid, String nombre) {
        int i = indicePestana(pid);
        if (i >= 0) perfilPestanaActiva = i;
        else if (perfilPestanaActiva >= 0 && perfilPestanaActiva < perfilPestanas.size()) perfilPestanas.set(perfilPestanaActiva, new Object[]{ pid, nombre });
        else { perfilPestanas.add(new Object[]{ pid, nombre }); perfilPestanaActiva = perfilPestanas.size() - 1; }
        refrescarTiraPerfil();
    }

    void cerrarPestana(int i) {
        if (i < 0 || i >= perfilPestanas.size()) return;
        perfilPestanas.remove(i);
        if (perfilPestanas.isEmpty()) { perfilPestanaActiva = -1; refrescarTiraPerfil(); abrirPerfil(0, ""); return; }
        if (perfilPestanaActiva >= perfilPestanas.size()) perfilPestanaActiva = perfilPestanas.size() - 1;
        else if (i < perfilPestanaActiva) perfilPestanaActiva--;
        Object[] p = perfilPestanas.get(Math.max(0, perfilPestanaActiva));
        perfilPestanaActiva = Math.max(0, perfilPestanaActiva);
        refrescarTiraPerfil();
        abrirPerfil((Long) p[0], (String) p[1]);
    }

    void refrescarTiraPerfil() {
        if (perfilTira == null) return;
        perfilTira.removeAll();
        for (int i = 0; i < perfilPestanas.size(); i++) {
            final int idx = i;
            Object[] p = perfilPestanas.get(i);
            JPanel caja = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); caja.setOpaque(false);
            JToggleButton tb = pestana((String) p[1], iconoBandera(paisDe((Long) p[0])));
            tb.setSelected(i == perfilPestanaActiva);
            tb.addActionListener(e -> { if (!tb.isSelected()) { tb.setSelected(true); return; } perfilPestanaActiva = idx; abrirPerfil((Long) p[0], (String) p[1]); });
            JButton x = new JButton("\u00D7");
            x.setFocusable(false); x.setMargin(new Insets(0, 4, 0, 4)); x.putClientProperty("JButton.buttonType", "borderless");
            x.setToolTipText(t("Cerrar esta pestaña", "Close this tab"));
            x.addActionListener(e -> cerrarPestana(idx));
            caja.add(tb); caja.add(x);
            perfilTira.add(caja);
        }
        JButton mas = new JButton("+");
        mas.setFocusable(false); mas.setMargin(new Insets(2, 8, 2, 8)); mas.putClientProperty("JButton.buttonType", "roundRect");
        mas.setToolTipText(t("Nueva pestaña: busca a otro jugador (también Ctrl+clic en un rival o aliado)", "New tab: search another player (also Ctrl+click on a rival or ally)"));
        mas.addActionListener(e -> {
            if (perfilPestanas.size() >= PERFIL_MAX_PESTANAS) { abrirPerfilEnPestana(-1, ""); return; }
            perfilPestanaActiva = -1; abrirPerfil(0, "");
        });
        perfilTira.add(mas);
        perfilTira.setVisible(!perfilPestanas.isEmpty());
        perfilTira.revalidate(); perfilTira.repaint();
    }

    // =====================================================================================
    // Desplazamiento con la rueda pulsada (como Chrome) y vuelta arriba al cambiar de vista
    // =====================================================================================
    Point autoAncla; JScrollPane autoPanel; javax.swing.Timer autoTimer; long autoInicioMs;

    void instalarAutoScroll() {
        autoTimer = new javax.swing.Timer(30, e -> {
            if (autoAncla == null || autoPanel == null) return;
            PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi == null) return;
            Point m = pi.getLocation();
            int dy = m.y - autoAncla.y, dx = m.x - autoAncla.x;
            JScrollBar v = autoPanel.getVerticalScrollBar(), h = autoPanel.getHorizontalScrollBar();
            if (Math.abs(dy) > 8 && v.isVisible()) v.setValue(v.getValue() + (int) (Math.signum(dy) * Math.pow(Math.abs(dy) - 8, 1.25) / 4));
            if (Math.abs(dx) > 8 && h.isVisible()) h.setValue(h.getValue() + (int) (Math.signum(dx) * Math.pow(Math.abs(dx) - 8, 1.25) / 4));
        });
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (!(ev instanceof MouseEvent me)) return;
            if (me.getID() == MouseEvent.MOUSE_PRESSED && me.getButton() == 4) { if (h2hDialogo != null && h2hDialogo.isShowing() && h2hDialogo.isFocused()) h2hAtras(); else volverAtras(); me.consume(); return; }   // botones laterales del ratón: atrás / adelante (dentro del cara a cara, su propio atrás)
            if (me.getID() == MouseEvent.MOUSE_PRESSED && me.getButton() == 5) { irAdelante(); me.consume(); return; }
            if (me.getID() == MouseEvent.MOUSE_PRESSED) {
                if (autoAncla != null) {   // cualquier clic termina el modo
                    boolean rapido = SwingUtilities.isMiddleMouseButton(me) && System.currentTimeMillis() - autoInicioMs < 250;
                    if (!rapido) { pararAutoScroll(); if (!SwingUtilities.isMiddleMouseButton(me)) return; else return; }
                }
                if (!SwingUtilities.isMiddleMouseButton(me)) return;
                Component c = me.getComponent();
                JScrollPane sp = c == null ? null : (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, c);
                if (sp == null && c instanceof JScrollPane s) sp = s;
                if (sp == null) return;
                autoAncla = me.getLocationOnScreen(); autoPanel = sp; autoInicioMs = System.currentTimeMillis();
                sp.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                autoTimer.start();
                me.consume();
            } else if (me.getID() == MouseEvent.MOUSE_RELEASED && autoAncla != null && SwingUtilities.isMiddleMouseButton(me)) {
                if (System.currentTimeMillis() - autoInicioMs >= 250) pararAutoScroll();   // arrastre: termina al soltar; clic corto: sigue hasta el próximo clic
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
    }
    void pararAutoScroll() {
        if (autoPanel != null) autoPanel.setCursor(Cursor.getDefaultCursor());
        autoAncla = null; autoPanel = null;
        if (autoTimer != null) autoTimer.stop();
    }

    /** Todos los scroll de un panel, arriba del todo (al abrir una vista). */
    static void subirArriba(Container c) {
        if (c == null) return;
        for (Component x : c.getComponents()) {
            if (x instanceof JScrollPane sp) { sp.getVerticalScrollBar().setValue(0); sp.getHorizontalScrollBar().setValue(0); }
            if (x instanceof Container cc) subirArriba(cc);
        }
    }

    // =====================================================================================
    // TECH TREE — datos de aoe2techtree (MIT, HSZemi): carpeta techtree junto al exe,
    // actualizada sola (ETag diario), iconos precargados en paralelo
    // =====================================================================================
    static final Map<String, ImageIcon> ttIconos = new java.util.concurrent.ConcurrentHashMap<>();
    static final Set<String> ttIconosPedidos = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static final java.util.concurrent.LinkedBlockingQueue<String> ttCola = new java.util.concurrent.LinkedBlockingQueue<>();
    static final java.util.concurrent.atomic.AtomicInteger ttHilos = new java.util.concurrent.atomic.AtomicInteger();
    JToggleButton techTreeBtn;
    JPanel techTreePanel, ttArbolPanel, ttFichaCards;
    JEditorPane ttFichaCiv, ttFichaDetalle;
    JComboBox<String> ttCivCombo;
    JLabel ttEstado;
    JScrollPane ttScroll;
    volatile boolean ttCargando, ttRellenandoCombo;
    final Map<String, String> ttCivPorNombre = new java.util.LinkedHashMap<>();   // nombre mostrado → clave (Aztecs)
    JTextField ttBuscaCiv;
    volatile String ttCivPedida;
    int ttDivisorPrevio = -1;

    /** Texto largo como HTML seguro: se escapa todo y se restauran solo <br> y <b>. */
    static String ttHtml(String s) {
        if (s == null) return "";
        String e = escapeHtml(s.replace("\n", "<br>"));
        e = e.replace("&lt;br&gt;", "<br>").replace("&lt;br/&gt;", "<br>").replace("&lt;b&gt;", "<b>").replace("&lt;/b&gt;", "</b>").replace("&lt;i&gt;", "<i>").replace("&lt;/i&gt;", "</i>")
                .replaceAll("(<br>\\s*){3,}", "<br><br>");
        return e;
    }
    /** Texto de ayuda sin etiquetas (para recortes): las cursivas y negritas se quitan, los saltos se conservan como espacios. */
    static String ttPlano(String s) { return s == null ? "" : s.replaceAll("(?i)</?[bi]>", "").replaceAll("(?i)<br\\s*/?>", " ").replace("\n", " ").replaceAll("\\s{2,}", " ").trim(); }

    static String ttImgHtml(Path p, int px) {
        return Files.exists(p) ? "<img src='" + p.toUri() + "' width='" + px + "' height='" + px + "'>" : "";
    }

    /** Icono (px) de img/<tipo>/<id>.png: memoria → disco → red (cola compartida, 4 hilos). */
    ImageIcon ttIcono(String tipo, long id, int px) {
        String clave = tipo + "/" + id + "@" + px;
        ImageIcon ic = ttIconos.get(clave);
        if (ic != null) return ic;
        Path p = ttRutaIcono(tipo, id);
        if (Files.exists(p)) {
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(p.toFile());
                if (img != null) { ic = new ImageIcon(img.getScaledInstance(px, px, Image.SCALE_SMOOTH)); ttIconos.put(clave, ic); return ic; }
            } catch (Exception ignored) { }
        }
        ttPedirIcono("img/" + tipo + "/" + id + ".png");
        return null;
    }

    void ttPedirIcono(String rel) {
        if (!ttIconosPedidos.add(rel)) return;
        ttCola.offer(rel);
        while (ttHilos.get() < 4 && !ttCola.isEmpty()) {
            ttHilos.incrementAndGet();
            Thread h = new Thread(() -> {
                try {
                    String job;
                    int bajados = 0;
                    while ((job = ttCola.poll(2, java.util.concurrent.TimeUnit.SECONDS)) != null) {
                        try { ttDescargar(job); bajados++; }
                        catch (Exception ex) { log("techtree " + job + ": " + causa(ex)); }
                        finally { ttIconosPedidos.remove(job); }
                        if (bajados % 8 == 0) SwingUtilities.invokeLater(this::ttPintarDeNuevo);
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    ttHilos.decrementAndGet();
                    SwingUtilities.invokeLater(this::ttPintarDeNuevo);
                }
            }, "techtree-iconos");
            h.setDaemon(true);
            h.start();
        }
    }

    /** Al arrancar, en segundo plano: lo que falte del árbol de cada civ (normalmente nada: el ZIP trae la base). */
    void ttPrecargar() {
        precalentarPerfiles();
        Thread h = new Thread(() -> {
            if (ttAsegurarDatos() != null) return;
            try {
                for (String civ : new ArrayList<>(obj(ttData.get("civs")).keySet())) {
                    if ("antiquity".equals(String.valueOf(obj(obj(ttData.get("civs")).get(civ)).get("era")))) continue;
                    Map<String, Object> arbol = ttArbol(civ);
                    for (Object o : arr(arbol.get("units_techs"))) {
                        Map<String, Object> n = obj(o);
                        Path p = ttRutaIcono(String.valueOf(n.get("use_type")), lng(n.get("picture_index")));
                        if (!Files.exists(p)) ttPedirIcono("img/" + n.get("use_type") + "/" + lng(n.get("picture_index")) + ".png");
                    }
                    for (Object o : arr(arbol.get("buildings"))) {
                        Map<String, Object> n = obj(o);
                        if (!Files.exists(ttRutaIcono("Building", lng(n.get("picture_index"))))) ttPedirIcono("img/Building/" + lng(n.get("picture_index")) + ".png");
                    }
                    if (!Files.exists(TT_DIR.resolve("img/Civs/" + civ.toLowerCase(Locale.ROOT) + ".png"))) ttPedirIcono("img/Civs/" + civ.toLowerCase(Locale.ROOT) + ".png");
                    Thread.sleep(50);
                }
                for (String rr : new String[]{ "food", "wood", "gold", "stone" })
                    if (!Files.exists(TT_DIR.resolve("img/" + rr + ".png"))) ttPedirIcono("img/" + rr + ".png");
            } catch (Exception ex) { log("techtree precarga: " + causa(ex)); }
        }, "techtree-precarga");
        h.setDaemon(true);
        h.start();
    }

    /** La descripción del juego (efecto de la tecnología, uso de la unidad…): cadena LanguageNameId + 21000,
     *  sin la primera línea «Investigar/Crear/Construir X (coste)» que ya cuentan el título y el coste. */
    static String ttDescripcion(Map<String, Object> d) {
        if (d == null) return null;
        String txt = ttStr(d.get("LanguageHelpId"));
        if (txt == null && d.get("LanguageNameId") instanceof Number n) txt = ttStr(n.longValue() + 21000);
        if (txt == null) return null;
        txt = txt.replaceAll("\\(?\u2039[^\u203a]*\u203a\\)?", "");   // fuera los marcadores del juego: ‹cost›, ‹hp›, ‹DEFAULT›…
        String[] partes = txt.split("<br>|\n", 2);
        if (partes.length == 2 && partes[0].matches("(?i)\\s*(Investigar|Crear|Construir|Research|Create|Build|Train)\\b.*")) txt = partes[1];
        return txt.replaceAll("(<br>\\s*|\\n\\s*)+$", "").replaceAll("^(<br>|\\s)+", "").trim();
    }

    static Map<String, Object> ttDatos(String tipo, long id) {
        return ttData == null ? Map.of() : obj(obj(obj(ttData.get("data")).get(tipo)).get(String.valueOf(id)));
    }

    /** «45 [oro] 25 [madera]» con los iconos de recursos. */
    static String ttCosteHtml(Map<String, Object> cost) {
        if (cost == null || cost.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String r : new String[]{ "Food", "Wood", "Gold", "Stone" }) {
            Object v = cost.get(r);
            if (v instanceof Number n && n.intValue() > 0) {
                String img = ttImgHtml(TT_DIR.resolve("img/" + r.toLowerCase(Locale.ROOT) + ".png"), 14);
                sb.append(sb.length() > 0 ? " &nbsp; " : "").append(n.intValue()).append(' ').append(img.isEmpty() ? r : img);
            }
        }
        return sb.toString();
    }

    static int ttArmadura(Map<String, Object> d, int clase) {
        for (Object a : arr(d.get("Armours"))) { Map<String, Object> m = obj(a); if (lng(m.get("Class")) == clase) return (int) lng(m.get("Amount")); }
        return 0;
    }

    static String ttNum(Object o) {
        if (!(o instanceof Number n)) return "";
        double v = n.doubleValue();
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    /** Tooltip: nombre, coste con iconos y lo esencial; el clic abre la ficha completa. */
    String ttTooltip(String tipo, long id, String nombre, boolean disponible) {
        Map<String, Object> d = ttDatos(tipo, id);
        StringBuilder h = new StringBuilder("<html><b>").append(escapeHtml(nombre)).append("</b>");
        if (!disponible) h.append(" <font color='#e57373'>").append(t("(no disponible)", "(not available)")).append("</font>");
        if (!d.isEmpty()) {
            String coste = ttCosteHtml(obj(d.get("Cost")));
            if (!coste.isEmpty()) h.append("<br>").append(coste);
            if ("Unit".equals(tipo)) {
                h.append("<br>").append(t("PV ", "HP ")).append(ttNum(d.get("HP"))).append(" · ").append(t("ataque ", "attack ")).append(ttNum(d.get("Attack")))
                 .append(" · ").append(t("armadura ", "armor ")).append(ttArmadura(d, 4)).append("/").append(ttArmadura(d, 3));
                if (d.get("Range") instanceof Number n && n.doubleValue() > 0) h.append(" · ").append(t("alcance ", "range ")).append(ttNum(n));
                if (d.get("Speed") instanceof Number) h.append(" · ").append(t("vel. ", "speed ")).append(ttNum(d.get("Speed")));
            } else if ("Tech".equals(tipo)) {
                if (d.get("ResearchTime") instanceof Number) h.append("<br>").append(t("investigación ", "research ")).append(ttNum(d.get("ResearchTime"))).append(" s");
                String desc = ttDescripcion(d);
                if (desc != null) { String plano = ttPlano(desc); h.append("<br><div style='width:320px'>").append(escapeHtml(plano.length() > 260 ? plano.substring(0, 258) + "\u2026" : plano)).append("</div>"); }
            } else if (d.get("HP") instanceof Number) h.append("<br>").append(t("PV ", "HP ")).append(ttNum(d.get("HP")));
        }
        h.append("<br><font color='#8a8a8a'>").append(t("Clic: ficha completa", "Click: full details")).append("</font></html>");
        return h.toString();
    }

    /** Ficha completa de un elemento (columna izquierda): como en la web, todo lo que hay. */
    JDialog ttDetalleDialog;
    JEditorPane ttDetallePane;

    void ttMostrarDetalle(String tipo, long id, long pictureIndex, String nombre, boolean disponible) { ttMostrarDetalle(tipo, id, pictureIndex, nombre, disponible, null); }

    /** Ficha completa en un panel flotante junto al icono: × o un clic en cualquier otra parte lo cierran. */
    void ttMostrarDetalle(String tipo, long id, long pictureIndex, String nombre, boolean disponible, Component ancla) {
        Map<String, Object> d = ttDatos(tipo, id);
        StringBuilder h = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px'>");
        h.append("<div>").append(ttImgHtml(ttRutaIcono(tipo, pictureIndex), 48)).append(" <span style='font-size:15px'><b>").append(escapeHtml(nombre)).append("</b></span></div>");
        if (!disponible) h.append("<p style='color:#e57373'>").append(t("No disponible para esta civilización.", "Not available for this civilization.")).append("</p>");
        if (!d.isEmpty()) {
            String coste = ttCosteHtml(obj(d.get("Cost")));
            if (!coste.isEmpty()) h.append("<p>").append(t("<b>Coste:</b> ", "<b>Cost:</b> ")).append(coste).append("</p>");
            h.append("<table cellpadding='2' cellspacing='0'>");
            java.util.function.BiConsumer<String, String> fila = (k, v) -> { if (v != null && !v.isEmpty()) h.append("<tr><td style='color:#8a8a8a'>").append(k).append("</td><td>").append(v).append("</td></tr>"); };
            if ("Unit".equals(tipo) || "Building".equals(tipo)) {
                fila.accept(t("PV", "HP"), ttNum(d.get("HP")));
                fila.accept(t("Ataque", "Attack"), ttNum(d.get("Attack")));
                StringBuilder bon = new StringBuilder();
                for (Object a : arr(d.get("Attacks"))) {
                    Map<String, Object> m = obj(a); int cl = (int) lng(m.get("Class")); long am = lng(m.get("Amount"));
                    if (cl == 3 || cl == 4 || am == 0) continue;
                    bon.append(bon.length() > 0 ? "<br>" : "").append("+").append(am).append(" ").append(t("vs ", "vs ")).append(escapeHtml(ttClase(cl)));
                }
                fila.accept(t("Bonus de ataque", "Attack bonuses"), bon.toString());
                fila.accept(t("Armadura", "Armor"), ttArmadura(d, 4) + " / " + ttArmadura(d, 3) + " <span style='color:#8a8a8a'>(" + t("cuerpo a cuerpo / perforante", "melee / pierce") + ")</span>");
                StringBuilder arm = new StringBuilder();
                for (Object a : arr(d.get("Armours"))) {
                    Map<String, Object> m = obj(a); int cl = (int) lng(m.get("Class")); long am = lng(m.get("Amount"));
                    if (cl == 3 || cl == 4) continue;
                    arm.append(arm.length() > 0 ? "<br>" : "").append(am >= 0 ? "+" : "").append(am).append(" ").append(escapeHtml(ttClase(cl)));
                }
                fila.accept(t("Clases de armadura", "Armor classes"), arm.toString());
                if (d.get("Range") instanceof Number n && n.doubleValue() > 0) fila.accept(t("Alcance", "Range"), ttNum(n) + (d.get("MinRange") instanceof Number mn && mn.doubleValue() > 0 ? " (" + t("mín. ", "min ") + ttNum(mn) + ")" : ""));
                fila.accept(t("Línea de visión", "Line of sight"), ttNum(d.get("LineOfSight")));
                if ("Unit".equals(tipo)) {
                    fila.accept(t("Velocidad", "Speed"), ttNum(d.get("Speed")));
                    fila.accept(t("Tiempo de entrenamiento", "Train time"), d.get("TrainTime") instanceof Number ? ttNum(d.get("TrainTime")) + " s" : "");
                    fila.accept(t("Cadencia", "Reload time"), d.get("ReloadTime") instanceof Number ? ttNum(d.get("ReloadTime")) + " s" : "");
                    if (d.get("AccuracyPercent") instanceof Number ap && ap.intValue() > 0 && ap.intValue() < 100) fila.accept(t("Precisión", "Accuracy"), ap.intValue() + " %");
                    if (d.get("GarrisonCapacity") instanceof Number g && g.intValue() > 0) fila.accept(t("Guarnición", "Garrison"), ttNum(g));
                } else {
                    if (d.get("GarrisonCapacity") instanceof Number g && g.intValue() > 0) fila.accept(t("Guarnición", "Garrison"), ttNum(g));
                    if (d.get("TrainTime") instanceof Number) fila.accept(t("Tiempo de construcción", "Build time"), ttNum(d.get("TrainTime")) + " s");
                }
            } else {
                if (d.get("ResearchTime") instanceof Number) fila.accept(t("Tiempo de investigación", "Research time"), ttNum(d.get("ResearchTime")) + " s");
            }
            h.append("</table>");
            String ayuda = ttDescripcion(d);
            if (ayuda != null) h.append("<p style='margin-top:8px'>").append(ttHtml(ayuda)).append("</p>");
        }
        h.append("</body></html>");
        if (ttDetalleDialog == null) {
            ttDetalleDialog = new JDialog(this, false);
            ttDetalleDialog.setUndecorated(true);
            JPanel cont = new JPanel(new BorderLayout(0, 4));
            cont.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xa3, 0xb8, 0x6c), 2, true), BorderFactory.createEmptyBorder(6, 8, 8, 8)));
            JPanel cab = new JPanel(new BorderLayout());
            cab.setOpaque(false);
            JLabel tit = new JLabel(t("Ficha", "Details"));
            tit.setFont(tit.getFont().deriveFont(Font.BOLD, 11f));
            cab.add(tit, BorderLayout.WEST);
            JButton x = new JButton("\u00D7");
            x.setFocusable(false); x.setMargin(new Insets(0, 6, 0, 6));
            x.putClientProperty("JButton.buttonType", "roundRect");
            x.addActionListener(e -> ttDetalleDialog.setVisible(false));
            cab.add(x, BorderLayout.EAST);
            cont.add(cab, BorderLayout.NORTH);
            ttDetallePane = new JEditorPane("text/html", "");
            ttDetallePane.setEditable(false);
            ttDetallePane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            JScrollPane sp = new JScrollPane(ttDetallePane);
            sp.setBorder(null);
            sp.getVerticalScrollBar().setUnitIncrement(16);
            cont.add(sp, BorderLayout.CENTER);
            ttDetalleDialog.setContentPane(cont);
            ttDetalleDialog.setSize(520, 600);
            ttDetalleDialog.getRootPane().registerKeyboardAction(e -> ttDetalleDialog.setVisible(false),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
        ttDetallePane.setText(h.toString());
        ttDetallePane.setCaretPosition(0);
        ttDetallePane.setSize(new Dimension(480, Integer.MAX_VALUE));
        int altoContenido = ttDetallePane.getPreferredSize().height + 60;
        ttDetalleDialog.setSize(520, Math.max(220, Math.min(600, altoContenido)));
        // junto al icono, sin salirse de la pantalla
        Point p;
        if (ancla != null && ancla.isShowing()) { p = ancla.getLocationOnScreen(); p.translate(ancla.getWidth() + 8, -40); }
        else { p = getLocationOnScreen(); p.translate(getWidth() / 2 - 260, getHeight() / 2 - 300); }
        Rectangle pantalla = getGraphicsConfiguration().getBounds();
        p.x = Math.max(pantalla.x, Math.min(p.x, pantalla.x + pantalla.width - 530));
        p.y = Math.max(pantalla.y, Math.min(p.y, pantalla.y + pantalla.height - 610));
        ttDetalleDialog.setLocation(p);
        ttDetalleDialog.setVisible(true);
        ttDetalleDialog.toFront();
    }

    Icon ttIconoBoton() {   // un árbol tecnológico dibujado: tres nodos y sus ramas, sin assets ajenos
        return new Icon() {
            public int getIconWidth() { return 14; }
            public int getIconHeight() { return 14; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.getForeground());
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawLine(x + 7, y + 3, x + 7, y + 7); g2.drawLine(x + 3, y + 11, x + 7, y + 7); g2.drawLine(x + 11, y + 11, x + 7, y + 7);
                g2.fillOval(x + 5, y + 0, 5, 5); g2.fillOval(x + 1, y + 9, 5, 5); g2.fillOval(x + 9, y + 9, 5, 5);
                g2.dispose();
            }
        };
    }

    JPanel construirPanelTechTree() {
        techTreePanel = new JPanel(new BorderLayout(8, 4));
        techTreePanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        // Barra: civ · estado · ×
        JPanel barra = new JPanel(new BorderLayout(8, 0));
        JPanel izq = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        izq.add(new JLabel(t("Civilización:", "Civilization:")));
        ttCivCombo = new JComboBox<>();
        ttCivCombo.setPrototypeDisplayValue("Achaemenids      ");
        ttCivCombo.setRenderer(new DefaultListCellRenderer() {   // con el emblema de cada civ
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean foc) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, value, index, sel, foc);
                String k = value == null ? null : ttCivPorNombre.get(String.valueOf(value));
                lab.setIcon(k == null ? null : iconoCiv(k, 18));
                lab.setIconTextGap(6);
                return lab;
            }
        });
        ttCivCombo.addActionListener(e -> { if (!ttRellenandoCombo && ttCivCombo.getSelectedItem() != null) ttMostrarCiv(ttCivPorNombre.getOrDefault((String) ttCivCombo.getSelectedItem(), (String) ttCivCombo.getSelectedItem())); });
        izq.add(ttCivCombo);
        ttBuscaCiv = new JTextField(12);   // escribe las primeras letras y Enter
        ttBuscaCiv.putClientProperty("JTextField.placeholderText", t("Escribe una civ…", "Type a civ…"));
        ttBuscaCiv.putClientProperty("JTextField.showClearButton", true);
        ttBuscaCiv.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() {
                String q = normalizarNick(ttBuscaCiv.getText());
                if (q.isEmpty()) return;
                for (int i = 0; i < ttCivCombo.getItemCount(); i++) {   // primero por prefijo, luego por contenido
                    String it = ttCivCombo.getItemAt(i);
                    if (normalizarNick(it).startsWith(q) || normalizarNick(ttCivPorNombre.getOrDefault(it, it)).startsWith(q)) { if (!it.equals(ttCivCombo.getSelectedItem())) ttCivCombo.setSelectedItem(it); return; }
                }
                for (int i = 0; i < ttCivCombo.getItemCount(); i++) {
                    String it = ttCivCombo.getItemAt(i);
                    if (normalizarNick(it).contains(q)) { if (!it.equals(ttCivCombo.getSelectedItem())) ttCivCombo.setSelectedItem(it); return; }
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        ttBuscaCiv.addActionListener(e -> ttBuscaCiv.selectAll());
        izq.add(ttBuscaCiv);
        ttEstado = new JLabel();
        ttEstado.setFont(ttEstado.getFont().deriveFont(Font.PLAIN, 11f));
        izq.add(ttEstado);
        barra.add(izq, BorderLayout.CENTER);
        JButton cerrar = new JButton("\u00D7");
        cerrar.setFocusable(false);
        cerrar.setMargin(new Insets(0, 7, 0, 7));
        cerrar.putClientProperty("JButton.buttonType", "roundRect");
        cerrar.setToolTipText(t("Cerrar el tech tree", "Close the tech tree"));
        cerrar.addActionListener(e -> cerrarTechTree());
        barra.add(cerrar, BorderLayout.EAST);
        techTreePanel.add(barra, BorderLayout.NORTH);
        // Columna izquierda: ficha de civ / ficha de detalle
        ttFichaCards = new JPanel(new CardLayout());
        ttFichaCards.setPreferredSize(new Dimension(330, 10));
        ttFichaCiv = new JEditorPane("text/html", "");
        ttFichaCiv.setEditable(false);
        ttFichaCiv.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        JScrollPane s1 = new JScrollPane(ttFichaCiv);
        s1.getVerticalScrollBar().setUnitIncrement(16);
        ttFichaCards.add(s1, "civ");
        techTreePanel.add(ttFichaCards, BorderLayout.WEST);
        // Árbol: horizontal con scroll; las edades, fijas al margen izquierdo
        ttArbolPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {   // degradado de fondo, columnas alternas, bandas de edad con línea dorada y líneas entre edificios
                super.paintComponent(g);
                int celda = ttCeldaActual, cab = TT_CAB, paso = celda + TT_VGAP;
                Graphics2D g2 = (Graphics2D) g.create();
                Color fondo = getBackground();
                g2.setPaint(new GradientPaint(0, 0, temaOscuroActivo ? new Color(0x26, 0x25, 0x23) : new Color(0xf7, 0xf3, 0xea), 0, getHeight(), temaOscuroActivo ? new Color(0x1c, 0x1c, 0x1b) : new Color(0xea, 0xe4, 0xd6)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                int k = 0;
                for (Component c : getComponents()) if (!(c instanceof JSeparator)) { if (k++ % 2 == 1) { g2.setColor(temaOscuroActivo ? new Color(255, 255, 255, 7) : new Color(0, 0, 0, 6)); g2.fillRect(c.getX(), 0, c.getWidth(), getHeight()); } }
                for (int a = 0; a < 4; a++) {
                    int y = cab + 2 + a * 2 * paso;
                    int tono = temaOscuroActivo ? 4 + a * 7 : 8 + a * 8;   // Alta clara → Imperial más marcada
                    g2.setColor(temaOscuroActivo ? new Color(255, 255, 255, tono) : new Color(0, 0, 0, tono));
                    g2.fillRect(0, y, getWidth(), 2 * paso);
                    g2.setColor(new Color(0xc9, 0x8a, 0x3b, temaOscuroActivo ? 70 : 90));   // línea dorada tenue entre edades
                    g2.drawLine(0, y, getWidth(), y);
                }
                g2.setColor(temaOscuroActivo ? new Color(255, 255, 255, 40) : new Color(0, 0, 0, 40));
                for (Component c : getComponents()) if (c instanceof JSeparator) g2.drawLine(c.getX(), 0, c.getX(), getHeight());
                g2.dispose();
            }
        };
        ttArbolPanel.setOpaque(true);
        ttArbolPanel.setLayout(new BoxLayout(ttArbolPanel, BoxLayout.X_AXIS));
        ttScroll = new JScrollPane(ttArbolPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        ttScroll.getHorizontalScrollBar().setUnitIncrement(32);
        ttScroll.getVerticalScrollBar().setUnitIncrement(24);
        ttScroll.addMouseWheelListener(e -> {   // la rueda desplaza en horizontal (el árbol crece a lo ancho)
            if (e.isShiftDown() || ttArbolPanel.getPreferredSize().height <= ttScroll.getViewport().getHeight()) {
                JScrollBar hb = ttScroll.getHorizontalScrollBar();
                hb.setValue(hb.getValue() + e.getWheelRotation() * 48);
            } else {
                JScrollBar vb = ttScroll.getVerticalScrollBar();
                vb.setValue(vb.getValue() + e.getWheelRotation() * 24);
            }
        });
        ttScroll.setWheelScrollingEnabled(false);
        JPanel centroTT = new JPanel(new BorderLayout());
        centroTT.add(ttScroll, BorderLayout.CENTER);
        centroTT.add(construirFiltrosTechTree(), BorderLayout.SOUTH);   // winrate y filtros, en la banda bajo el árbol
        techTreePanel.add(centroTT, BorderLayout.CENTER);
        // Arrastrar con el botón derecho (o central) mueve el árbol; llega a cualquier celda gracias al oyente global
        final Point[] arrastre = { null, null };   // {origen en pantalla, posición del visor}
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (!(ev instanceof MouseEvent me) || ttScroll == null || !ttScroll.isShowing()) return;
            Component c = me.getComponent();
            if (me.getID() == MouseEvent.MOUSE_PRESSED) {
                if (!(SwingUtilities.isRightMouseButton(me) || SwingUtilities.isMiddleMouseButton(me))) return;
                if (c == null || !SwingUtilities.isDescendingFrom(c, ttScroll)) return;
                arrastre[0] = me.getLocationOnScreen(); arrastre[1] = ttScroll.getViewport().getViewPosition();
                ttScroll.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            } else if (me.getID() == MouseEvent.MOUSE_DRAGGED && arrastre[0] != null) {
                JViewport vp = ttScroll.getViewport();
                Point p = me.getLocationOnScreen();
                int nx = Math.max(0, Math.min(Math.max(0, vp.getViewSize().width - vp.getWidth()), arrastre[1].x - (p.x - arrastre[0].x)));
                int ny = Math.max(0, Math.min(Math.max(0, vp.getViewSize().height - vp.getHeight()), arrastre[1].y - (p.y - arrastre[0].y)));
                vp.setViewPosition(new Point(nx, ny));
            } else if (me.getID() == MouseEvent.MOUSE_RELEASED && arrastre[0] != null) {
                arrastre[0] = null; ttScroll.setCursor(Cursor.getDefaultCursor());
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        JLabel pie = new JLabel(t("Datos e iconos: aoe2techtree.net (HSZemi, MIT) · Age of Empires II © Microsoft. En gris, lo que la civ no tiene. Pasa el ratón para ver coste y estadísticas; clic para la ficha completa.",
                "Data & icons: aoe2techtree.net (HSZemi, MIT) · Age of Empires II © Microsoft. Greyed out = not available for this civ. Hover for cost and stats; click for full details."));
        pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f));
        techTreePanel.add(pie, BorderLayout.SOUTH);
        return techTreePanel;
    }

    /** Abre el panel (plegando la watchlist) y, si se pide, en una civ concreta. */
    void abrirTechTree(String civ) {
        registrarDestino(new Destino("techtree", 0, null, civ != null ? civ : ttCivPedida));
        if (techTreeBtn != null && !techTreeBtn.isSelected()) techTreeBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        actividadAbierta = false;
        if (perfilBtn != null) perfilBtn.setSelected(false);
        ahoraAbierta = false; if (ahoraBtn != null) ahoraBtn.setSelected(false);
        ((CardLayout) centroCards.getLayout()).show(centroCards, "techtree");
        taparResultados(); apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        if (splitPrincipal != null && ttDivisorPrevio < 0) {   // la watchlist se pliega: el árbol necesita el ancho
            ttDivisorPrevio = splitPrincipal.getDividerLocation();
            splitPrincipal.setOneTouchExpandable(true);
            splitPrincipal.setDividerLocation(0);
        }
        if (ttCargando) return;
        ttCargando = true;
        ttEstado.setText(t("Cargando datos…", "Loading data…"));
        new Thread(() -> {
            ttComprobarActualizacion();
            String err = ttAsegurarDatos();
            SwingUtilities.invokeLater(() -> {
                ttCargando = false;
                if (err != null) {
                    ttEstado.setText(t("No se pudieron cargar los datos (", "Couldn't load the data (") + err + t("). Vuelve a intentarlo con conexión.", "). Try again with a connection."));
                    return;
                }
                ttEstado.setText("");
                List<String> civs = new ArrayList<>();
                for (Map.Entry<String, Object> en : obj(ttData.get("civs")).entrySet())
                    if (!"antiquity".equals(String.valueOf(obj(en.getValue()).get("era")))) civs.add(en.getKey());
                civs.sort(String.CASE_INSENSITIVE_ORDER);
                ttCivPorNombre.clear();
                List<String> nombres = new ArrayList<>();
                for (String c : civs) { String n = ttNombreCiv(c); ttCivPorNombre.put(n, c); nombres.add(n); }
                nombres.sort(String.CASE_INSENSITIVE_ORDER);
                String actualNombre = (String) ttCivCombo.getSelectedItem();
                if (ttCivCombo.getItemCount() != nombres.size()) {
                    ttRellenandoCombo = true;
                    try { ttCivCombo.removeAllItems(); for (String n : nombres) ttCivCombo.addItem(n); } finally { ttRellenandoCombo = false; }
                }
                String actual = actualNombre == null ? null : ttCivPorNombre.get(actualNombre);
                String pedida = civ == null ? null : civs.stream().filter(c -> c.equalsIgnoreCase(civ)).findFirst().orElse(null);
                String elegir = pedida != null ? pedida : actual != null ? actual : civs.isEmpty() ? null : civs.get(0);
                if (elegir != null) {
                    String nombreElegir = ttNombreCiv(elegir);
                    if (nombreElegir.equals(ttCivCombo.getSelectedItem())) ttMostrarCiv(elegir); else ttCivCombo.setSelectedItem(nombreElegir);
                }
                ttCargarStats();   // winrate junto a cada civ, con los filtros de Civ Stats
            });
        }, "techtree-datos").start();
    }

    void cerrarTechTree() {
        if (ttDetalleDialog != null) ttDetalleDialog.setVisible(false);
        if (techTreeBtn != null) techTreeBtn.setSelected(false);
        if (splitPrincipal != null && ttDivisorPrevio >= 0) {   // la watchlist vuelve a su ancho
            splitPrincipal.setDividerLocation(ttDivisorPrevio);
            SwingUtilities.invokeLater(() -> { if (norteWatchRef != null) { norteWatchRef.revalidate(); norteWatchRef.repaint(); } });   // las filas del norte se recalculan con el ancho ya restaurado
            splitPrincipal.setOneTouchExpandable(false);
            ttDivisorPrevio = -1;
        }
        mostrarDirectos(false);
    }

    void ttMostrarCiv(String civ) {
        if (ttDetalleDialog != null) ttDetalleDialog.setVisible(false);
        ttCivPedida = civ;
        ttArbolPanel.removeAll();
        ttEstado.setText(t("Cargando ", "Loading ") + civ + "…");
        new Thread(() -> {
            Map<String, Object> arbol;
            try { arbol = ttArbol(civ); }
            catch (Exception ex) { SwingUtilities.invokeLater(() -> ttEstado.setText(t("No se pudo cargar ", "Couldn't load ") + civ + ": " + causa(ex))); return; }
            int px = ttCeldaPx();   // los iconos de esta civ, a memoria ANTES de pintar: el árbol sale entero de golpe
            for (Object o : arr(arbol.get("units_techs"))) { Map<String, Object> n = obj(o); ttIcono(String.valueOf(n.get("use_type")), lng(n.get("picture_index")), px - 4); }
            for (Object o : arr(arbol.get("buildings"))) { Map<String, Object> n = obj(o); ttIcono("Building", lng(n.get("picture_index")), 26); }
            SwingUtilities.invokeLater(() -> {
                if (!civ.equals(ttCivPedida)) return;   // ya se pidió otra civ: esta llegó tarde
                ttEstado.setText("");
                ttPintarFichaCiv(civ);
                ((CardLayout) ttFichaCards.getLayout()).show(ttFichaCards, "civ");
                ttPintarArbol(civ, arbol);
                ttArbolPanel.revalidate(); ttArbolPanel.repaint();
                ttScroll.getHorizontalScrollBar().setValue(0);
            });
        }, "techtree-civ").start();
    }

    void ttPintarFichaCiv(String civ) {
        Map<String, Object> civInfo = obj(obj(ttData.get("civs")).get(civ));
        String ayuda = ttStr(civInfo.get("help_string_id"));
        Color fg = UIManager.getColor("Label.foreground");
        String col = fg == null ? "#cccccc" : String.format("#%02x%02x%02x", fg.getRed(), fg.getGreen(), fg.getBlue());
        StringBuilder h = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px;color:" + col + "'>");
        Path ic = TT_DIR.resolve("img/Civs/" + civ.toLowerCase(Locale.ROOT) + ".png");
        if (!Files.exists(ic)) ttPedirIcono("img/Civs/" + civ.toLowerCase(Locale.ROOT) + ".png");
        h.append("<div>").append(ttImgHtml(ic, 56)).append(" <span style='font-size:17px'><b>").append(escapeHtml(ttNombreCiv(civ))).append("</b></span></div>");
        ttActualizarBanda();
        if (ayuda != null) h.append("<p>").append(ttHtml(ayuda)).append("</p>");
        h.append("</body></html>");
        ttFichaCiv.setText(h.toString());
        ttFichaCiv.setCaretPosition(0);
    }

    void ttPintarDeNuevo() {
        String civ = ttCivCombo == null || ttCivCombo.getSelectedItem() == null ? null : ttCivPorNombre.getOrDefault((String) ttCivCombo.getSelectedItem(), (String) ttCivCombo.getSelectedItem());
        if (civ == null || !ttTrees.containsKey(civ) || !techTreePanel.isShowing()) return;
        int h = ttScroll.getHorizontalScrollBar().getValue();
        ttArbolPanel.removeAll();
        ttPintarArbol(civ, ttTrees.get(civ));
        ttArbolPanel.revalidate(); ttArbolPanel.repaint();
        ttScroll.getHorizontalScrollBar().setValue(h);
    }

    static final String[] TT_EDADES_ES = { "Alta Edad Media", "Edad Feudal", "Edad de los Castillos", "Edad Imperial" };
    static final String[] TT_EDADES_EN = { "Dark Age", "Feudal Age", "Castle Age", "Imperial Age" };
    static final int TT_VGAP = 12;  // hueco vertical entre filas: por él corren las líneas de mejora (milicia → hombre de armas…)
    static final int TT_CAB = 66;   // la tarjeta del edificio (icono grande y nombre); el edificio en sí va además en su fila de edad, como en la web
    int ttCeldaActual = 40;
    /** Placa de edad: degradado oscuro y borde dorado fino, como la tarjeta de cada edificio. */
    static class PlacaTT extends JPanel {
        final int edad;
        PlacaTT(int edad) { this.edad = edad; setOpaque(false); setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 4)); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 6, h = getHeight() - 6;
            g2.setPaint(new GradientPaint(0, 3, temaOscuroActivo ? new Color(0x33, 0x2f, 0x27) : new Color(0xf6, 0xef, 0xdd), 0, 3 + h, temaOscuroActivo ? new Color(0x24, 0x22, 0x1e) : new Color(0xe8, 0xdd, 0xc2)));
            g2.fillRoundRect(2, 3, w, h, 10, 10);
            g2.setColor(new Color(0xc9, 0x8a, 0x3b, temaOscuroActivo ? 120 : 150));
            g2.drawRoundRect(2, 3, w, h, 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }
    /** Borde-placa para la cabecera de edificio: degradado oscuro, borde dorado fino y esquinas redondeadas. */
    static class PlacaBorde extends javax.swing.border.AbstractBorder {
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {   // solo el contorno: el fondo lo pinta el propio componente, debajo de su contenido
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0xc9, 0x8a, 0x3b, temaOscuroActivo ? 150 : 170));
            g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 12, 12);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(4, 6, 4, 6); }
        @Override public boolean isBorderOpaque() { return false; }
    }
    /** La rejilla de un edificio: GridLayout con hueco vertical y, por debajo de los iconos, las líneas que unen cada unidad con su mejora (misma columna, fila siguiente con contenido, a como mucho una edad). */
    static class RejillaTT extends JPanel {
        final int filas, cols;
        RejillaTT(int filas, int cols) { super(new GridLayout(filas, cols, 2, TT_VGAP)); this.filas = filas; this.cols = cols; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Component[] cs = getComponents();
            if (cs.length < filas * cols) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(2f));
            // de la placa del edificio a su icono: una línea dorada por la columna 0 hasta la fila del edificio
            for (int r = 0; r < filas; r++) {
                Component b0 = cs[r * cols];
                if (b0 instanceof JLabel lb0 && lb0.getIcon() != null) {
                    g2.setColor(new Color(0xc9, 0x8a, 0x3b, temaOscuroActivo ? 110 : 130));
                    int x = b0.getX() + b0.getWidth() / 2;
                    if (b0.getY() > 2) g2.drawLine(x, 0, x, b0.getY() + 1);
                    break;
                }
            }
            for (int c = 1; c < cols; c++) {   // la columna 0 es el propio edificio
                int prev = -1;
                for (int r = 0; r < filas; r++) {
                    Component celda = cs[r * cols + c];
                    boolean lleno = celda instanceof JLabel l && (l.getIcon() != null || (l.getText() != null && !l.getText().isEmpty()));
                    if (!lleno) continue;
                    if (prev >= 0 && r - prev <= 2) {
                        Component arriba = cs[prev * cols + c];
                        boolean disp = celda.getForeground() == null || !(celda instanceof JLabel lb && "\u00D7".equals(lb.getText()));
                        Color marcoAbajo = celda instanceof JComponent jc && jc.getBorder() instanceof javax.swing.border.LineBorder lbrd ? lbrd.getLineColor() : Color.GRAY;
                        boolean noDisp = marcoAbajo.getAlpha() < 255;   // las no disponibles llevan marco rojo translúcido
                        g2.setColor(noDisp ? new Color(0xe5, 0x73, 0x73, 60) : (temaOscuroActivo ? new Color(0xc9, 0x8a, 0x3b, 120) : new Color(0x8a, 0x5e, 0x1e, 140)));
                        int x = arriba.getX() + arriba.getWidth() / 2;
                        g2.drawLine(x, arriba.getY() + arriba.getHeight() - 1, x, celda.getY() + 1);
                    }
                    prev = r;
                }
            }
            g2.dispose();
        }
    }
    /** Versión apagada de un icono (gris y translúcida) para lo que la civ no tiene. */
    static Image imagenApagada(Image img) {
        Image gris = GrayFilter.createDisabledImage(img);
        int w = img.getWidth(null), h = img.getHeight(null);
        if (w <= 0 || h <= 0) return gris;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g2.drawImage(gris, 0, 0, null);
        g2.dispose();
        return out;
    }

    int ttCeldaPx() {   // ocho filas + cabecera en el alto disponible: iconos grandes cuando hay sitio
        int alto = ttScroll == null || ttScroll.getViewport().getHeight() < 200 ? 560 : ttScroll.getViewport().getHeight();
        return Math.max(32, Math.min(58, (alto - TT_CAB - 24) / 8 - TT_VGAP));
    }

    /** El árbol como la web: una columna por edificio, ocho filas (dos por edad) alineadas en todas las columnas. */
    void ttPintarArbol(String civ, Map<String, Object> arbol) {
        final int TT_CELDA = ttCeldaPx();
        ttCeldaActual = TT_CELDA;
        Map<String, Map<String, Object>> nodos = new HashMap<>();
        for (Object o : arr(arbol.get("units_techs"))) { Map<String, Object> n = obj(o); nodos.put(String.valueOf(n.get("id")), n); }
        for (Object o : arr(arbol.get("buildings"))) { Map<String, Object> n = obj(o); nodos.put(String.valueOf(n.get("id")), n); }
        Color gris = temaOscuroActivo ? new Color(0x8a, 0x8a, 0x8a) : new Color(0x77, 0x77, 0x77);
        Color banda = temaOscuroActivo ? new Color(0xff, 0xff, 0xff, 14) : new Color(0, 0, 0, 12);
        // Margen fijo con las edades (rowHeader del scroll: no se mueve con el desplazamiento horizontal)
        JPanel edades = new JPanel();
        edades.setLayout(new BoxLayout(edades, BoxLayout.Y_AXIS));
        edades.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        JLabel vacio = new JLabel(); vacio.setPreferredSize(new Dimension(104, TT_CAB + 2)); vacio.setMaximumSize(new Dimension(104, TT_CAB + 2)); edades.add(vacio);
        edades.setOpaque(true); edades.setBackground(temaOscuroActivo ? new Color(0x22, 0x21, 0x1f) : new Color(0xf1, 0xec, 0xe1));
        String[] nombres = "es".equals(IDIOMA) ? TT_EDADES_ES : TT_EDADES_EN;
        String[] edadImg = { "base_dark_age", "base_feudal_age", "base_castle_age", "base_imperial_age" };
        List<Object> ageIds = arr(obj(ttData.get("age_names")).get("base"));
        for (int a = 0; a < 4; a++) {   // una etiqueta por edad, del alto de sus dos filas
            String nombreEdad = a < ageIds.size() && ttStr(ageIds.get(a)) != null ? ttNombre(ageIds.get(a)) : nombres[a];
            JLabel e = new JLabel("<html><div style='text-align:center'><b>" + escapeHtml(nombreEdad).replace(" de los ", "<br>de los ").replace("Alta Edad Media", "Alta<br>Edad Media") + "</b></div></html>");
            Path pe = TT_DIR.resolve("img/Ages/" + edadImg[a] + ".png");
            if (!Files.exists(pe)) ttPedirIcono("img/Ages/" + edadImg[a] + ".png");
            else try { int px = Math.min(64, 2 * (TT_CELDA + TT_VGAP) - 40); e.setIcon(new ImageIcon(javax.imageio.ImageIO.read(pe.toFile()).getScaledInstance(px, px, Image.SCALE_SMOOTH))); } catch (Exception ignored) { }
            e.setVerticalTextPosition(SwingConstants.BOTTOM); e.setHorizontalTextPosition(SwingConstants.CENTER);
            e.setHorizontalAlignment(SwingConstants.CENTER); e.setVerticalAlignment(SwingConstants.CENTER);
            e.setForeground(gris);
            e.setFont(e.getFont().deriveFont(Font.BOLD, 10.5f));
            int alto = 2 * (TT_CELDA + TT_VGAP);
            e.setPreferredSize(new Dimension(104, alto)); e.setMaximumSize(new Dimension(104, alto)); e.setMinimumSize(new Dimension(104, alto));
            e.setOpaque(false);
            e.setForeground(temaOscuroActivo ? new Color(0xd8, 0xc2, 0x8a) : new Color(0x6b, 0x4c, 0x16));   // nombre de edad en dorado apagado
            JPanel placa = new PlacaTT(a);
            placa.setLayout(new BorderLayout()); placa.add(e, BorderLayout.CENTER);
            placa.setPreferredSize(new Dimension(104, alto)); placa.setMaximumSize(new Dimension(104, alto)); placa.setMinimumSize(new Dimension(104, alto));
            edades.add(placa);
        }
        ttScroll.setRowHeaderView(edades);
        List<Map<String, Object>> edificios = new ArrayList<>();
        for (Object o : arr(arbol.get("buildings"))) edificios.add(obj(o));
        for (Map<String, Object> b : edificios) {
            List<Object> grid = arr(b.get("grid"));
            int cols = 0, conContenido = 0;
            for (Object f : grid) { cols = Math.max(cols, arr(f).size()); for (Object c : arr(f)) if (c != null && nodos.containsKey(String.valueOf(c))) conContenido++; }
            if (cols == 0 || conContenido == 0) continue;   // torres, muros, puertas, puestos…: sin nada que enseñar, fuera
            boolean bDisp = !"NotAvailable".equals(String.valueOf(b.get("node_status")));
            String bNombre = ttNombre(b.get("name_string_id"));
            long bPic = lng(b.get("picture_index")), bId = lng(b.get("building_id"));
            int colsTot = cols + 1;   // la primera columna es la del propio edificio, en su fila de edad
            int anchoCol = colsTot * (TT_CELDA + 2);
            JPanel columna = new JPanel(new BorderLayout(0, 2));
            columna.setOpaque(false);
            columna.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            columna.setMaximumSize(new Dimension(anchoCol + 12, TT_CAB + 2 + 8 * (TT_CELDA + TT_VGAP)));
            columna.setAlignmentY(Component.TOP_ALIGNMENT);   // todas las columnas arrancan arriba: filas alineadas con las edades
            // cabecera del edificio: icono + nombre a todo el ancho de la columna
            ImageIcon bic = ttIcono("Building", bPic, TT_CELDA - 6);
            ImageIcon bicCab = ttIcono("Building", bPic, 34);
            JLabel cab = new JLabel() {
                @Override protected void paintComponent(Graphics g) {   // fondo de placa debajo del icono y el nombre
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight();
                    g2.setPaint(new GradientPaint(0, 0, temaOscuroActivo ? new Color(0x36, 0x31, 0x28) : new Color(0xf8, 0xf1, 0xdf), 0, h, temaOscuroActivo ? new Color(0x25, 0x23, 0x1f) : new Color(0xe9, 0xde, 0xc3)));
                    g2.fillRoundRect(1, 1, w - 3, h - 3, 12, 12);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            cab.setPreferredSize(new Dimension(anchoCol, TT_CAB));
            cab.setMinimumSize(new Dimension(anchoCol, TT_CAB));
            cab.setMaximumSize(new Dimension(anchoCol, TT_CAB));
            if (bicCab != null) cab.setIcon(bDisp ? bicCab : new ImageIcon(GrayFilter.createDisabledImage(bicCab.getImage())));
            cab.setText("<html><div style='text-align:center'><b><span style='font-size:10.5px'>" + escapeHtml(bNombre).toUpperCase(Locale.ROOT).replace(" ", "&nbsp;") + "</span></b></div></html>");
            cab.setIconTextGap(2);
            cab.setHorizontalAlignment(SwingConstants.CENTER);   // icono arriba, nombre en versalitas debajo: una placa por edificio
            cab.setHorizontalTextPosition(SwingConstants.CENTER);
            cab.setVerticalTextPosition(SwingConstants.BOTTOM);
            cab.setVerticalAlignment(SwingConstants.CENTER);
            cab.setOpaque(false);
            cab.setBorder(new PlacaBorde());
            cab.setForeground(bDisp ? (temaOscuroActivo ? new Color(0xe6, 0xd3, 0xa3) : new Color(0x5a, 0x40, 0x10)) : gris);
            int filaEdificio = (int) Math.max(0, Math.min(3, lng(b.get("age_id")) - 1)) * 2;   // su edad: primera fila de la banda
            cab.setToolTipText(ttTooltip("Building", bId, bNombre, bDisp));
            cab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final boolean fDisp = bDisp;
            cab.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { ttMostrarDetalle("Building", bId, bPic, bNombre, fDisp, cab); } });
            columna.add(cab, BorderLayout.NORTH);
            JPanel rejilla = new RejillaTT(8, colsTot);
            rejilla.setPreferredSize(new Dimension(anchoCol, 8 * (TT_CELDA + TT_VGAP)));
            columna.add(rejilla, BorderLayout.CENTER);
            for (int r = 0; r < 8; r++) {
                List<Object> filaG = r < grid.size() ? arr(grid.get(r)) : List.of();
                for (int c = -1; c < cols; c++) {
                    if (c == -1) {   // columna del edificio
                        JLabel be = new JLabel();
                        be.setPreferredSize(new Dimension(TT_CELDA, TT_CELDA));
                        be.setHorizontalAlignment(SwingConstants.CENTER);
                        if (r == filaEdificio) {
                            if (bic != null) be.setIcon(bDisp ? bic : new ImageIcon(GrayFilter.createDisabledImage(bic.getImage())));
                            be.setToolTipText(ttTooltip("Building", bId, bNombre, bDisp));
                            be.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            be.setBorder(BorderFactory.createLineBorder(bDisp ? new Color(0xc9, 0x8a, 0x3b) : new Color(0xe5, 0x73, 0x73, 110), 2, true));
                            be.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { ttMostrarDetalle("Building", bId, bPic, bNombre, fDisp, be); } });
                        }
                        rejilla.add(be);
                        continue;
                    }
                    Object celda = c < filaG.size() ? filaG.get(c) : null;
                    JLabel l = new JLabel();
                    l.setPreferredSize(new Dimension(TT_CELDA, TT_CELDA));
                    l.setHorizontalAlignment(SwingConstants.CENTER);
                    if (celda != null && nodos.containsKey(String.valueOf(celda))) {
                        Map<String, Object> n = nodos.get(String.valueOf(celda));
                        String tipo = String.valueOf(n.get("use_type"));
                        long nid = lng(n.get("node_id")), pic = lng(n.get("picture_index"));
                        boolean disp = !"NotAvailable".equals(String.valueOf(n.get("node_status")));
                        String nombre = ttNombre(n.get("name_string_id"));
                        ImageIcon ic = ttIcono(tipo, pic, TT_CELDA - 6);
                        if (ic != null) l.setIcon(disp ? ic : new ImageIcon(imagenApagada(ic.getImage())));
                        else if (!disp) { l.setText("\u00D7"); l.setForeground(gris); }
                        l.setToolTipText(ttTooltip(tipo, nid, nombre, disp));
                        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        Color marco = !disp ? new Color(0xe5, 0x73, 0x73, 70)
                                : "Unit".equals(tipo) ? new Color(0x3b, 0x82, 0xc4) : "Tech".equals(tipo) ? new Color(0x5c, 0xa8, 0x4a) : new Color(0xc9, 0x8a, 0x3b);
                        l.setBorder(BorderFactory.createLineBorder(marco, 2, true));   // marco por tipo, como la web
                        l.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { ttMostrarDetalle(tipo, nid, pic, nombre, disp, l); } });
                    }
                    rejilla.add(l);
                }
            }
            ttArbolPanel.add(columna);
            JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
            sep.setMaximumSize(new Dimension(1, TT_CAB + 2 + 8 * (TT_CELDA + TT_VGAP)));
            sep.setAlignmentY(Component.TOP_ALIGNMENT);
            ttArbolPanel.add(sep);
        }
    }

    // ----- Partidas en curso en tiempo real: websocket «ongoing-matches» del companion -----------------
    // Se abre una conexión con los ids de la vista actual y el servidor empuja matchAdded /
    // matchUpdated / matchRemoved. El barrido por lotes sigue de respaldo (y no quita puntos
    // mientras el socket esté sano).
    static final String SOCKET_URL = "wss://socket.aoe2companion.com/listen?handler=ongoing-matches";
    volatile java.net.http.WebSocket socket;
    volatile Set<Long> socketIds = Set.of(); volatile boolean socketHuboCaida;
    long ultimoResyncMs;
    volatile long socketUltimoMsgMs;       // último mensaje recibido (salud)
    volatile boolean socketConectado;
    volatile int socketReintentos;
    final StringBuilder socketBuffer = new StringBuilder();   // los mensajes pueden llegar troceados
    javax.swing.Timer socketPing, socketReconexion;

    boolean socketSano() { return socketConectado && System.currentTimeMillis() - socketUltimoMsgMs < 10 * 60_000; }

    /** Conecta (o reconecta) con los ids visibles; si no cambian y el socket está sano, no hace nada. */
    void sincronizarSocket() {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        for (int i = 0; i < playersModel.size(); i++) ids.add(playersModel.get(i).id());
        for (Player p : todosJugadores) ids.add(p.id());      // todos los grupos, no solo la vista actual: así cambiar de pestaña no reconecta el socket (y no se pierden eventos en el hueco)
        synchronized (topLadder) { for (Player p : topLadder) ids.add(p.id()); }
        ids.addAll(socketExtra);   // Live now abierto y vistas con campana: también se vigilan aunque no estén a la vista
        try { String mi = leerConfig("mi_pid", ""); if (!mi.isBlank()) ids.add(Long.parseLong(mi)); } catch (Exception ignored) { }   // «Mi partida»: mi propio id siempre vigilado
        if (ids.isEmpty()) { cerrarSocket(); return; }
        if (ids.equals(socketIds) && socketConectado) return;
        socketIds = ids;
        cerrarSocket();
        abrirSocket(ids);
    }

    void cerrarSocket() {
        java.net.http.WebSocket s = socket;
        socket = null;
        socketConectado = false;
        if (s != null) { try { s.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "bye"); } catch (Exception ignored) { } }
    }

    void abrirSocket(Set<Long> ids) {
        StringBuilder csv = new StringBuilder();
        for (long id : ids) { if (csv.length() > 0) csv.append(','); csv.append(id); }
        String url = SOCKET_URL + "&language=" + ("es".equals(IDIOMA) ? "es" : "en") + "&profile_ids=" + csv;
        final Set<Long> idsEsta = ids;
        HTTP.newWebSocketBuilder().header("User-Agent", UA).connectTimeout(Duration.ofSeconds(15))
            .buildAsync(URI.create(url), new java.net.http.WebSocket.Listener() {
                @Override public void onOpen(java.net.http.WebSocket ws) {
                    socketConectado = true;
                    if (socketHuboCaida && ahoraAbierta) SwingUtilities.invokeLater(() -> ahoraRefrescar(true));   // tras una caída, un barrido para reparar el estado
                    socketHuboCaida = false;
                    socketReintentos = 0;
                    socketUltimoMsgMs = System.currentTimeMillis();
                    log("socket: conectado, vigilando " + idsEsta.size() + " jugadores en tiempo real");
                    ws.request(1);
                }
                @Override public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket ws, CharSequence data, boolean last) {
                    synchronized (socketBuffer) {
                        socketBuffer.append(data);
                        if (last) {
                            String msg = socketBuffer.toString();
                            socketBuffer.setLength(0);
                            socketUltimoMsgMs = System.currentTimeMillis();
                            try { procesarEventosSocket(msg, idsEsta); }
                            catch (Exception ex) { log("socket: mensaje no entendido: " + causa(ex)); }
                        }
                    }
                    ws.request(1);
                    return null;
                }
                @Override public java.util.concurrent.CompletionStage<?> onClose(java.net.http.WebSocket ws, int code, String reason) {
                    if (socket == ws || socket == null) { socketConectado = false; socketHuboCaida = true; log("socket: cerrado (" + code + " " + reason + ")"); programarReconexion(); }
                    return null;
                }
                @Override public void onError(java.net.http.WebSocket ws, Throwable error) {
                    socketConectado = false;
                    log("socket: error: " + causa(error instanceof Exception ex ? ex : new RuntimeException(error)));
                    programarReconexion();
                }
            })
            .whenComplete((ws, err) -> {
                if (err != null) { socketConectado = false; log("socket: no se pudo conectar: " + causa(new RuntimeException(err))); programarReconexion(); }
                else if (socketIds.equals(idsEsta)) socket = ws;
                else { try { ws.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "stale"); } catch (Exception ignored) { } }
            });
    }

    void programarReconexion() {
        SwingUtilities.invokeLater(() -> {
            if (socketReconexion != null && socketReconexion.isRunning()) return;
            int seg = Math.min(120, 5 * (1 << Math.min(socketReintentos++, 5)));   // 5, 10, 20, 40, 80, 120 s
            socketReconexion = new javax.swing.Timer(seg * 1000, e -> { socketConectado = false; Set<Long> ids = socketIds; if (!ids.isEmpty()) abrirSocket(ids); });
            socketReconexion.setRepeats(false);
            socketReconexion.start();
        });
    }

    /** Antes de marcar a alguien como jugando por un evento del socket, se comprueba en la API que la
     *  partida no esté ya terminada (el companion a veces anuncia partidas viejas como vivas). */
    void confirmarEventoSocket(Match m, List<Long> pids) {
        new Thread(() -> {
            boolean viva = true;
            try {
                Iterable<Match> leidas = COMPANION.partidas(pids.get(0), 1, 5);
                for (Match r : leidas) {
                    if (r != null && r.id == m.id) { viva = enCursoReal(r); break; }
                }
            } catch (Exception ex) { log("socket: no se pudo confirmar la partida " + m.id + ": " + causa(ex)); }
            if (!viva) { log("socket: partida " + m.id + " ya terminada según la API: fantasma ignorado"); return; }
            for (long pid : pids) { vivoWatch.put(pid, m.id); vivoInfo.put(pid, resumenVivo(m, pid)); VIVO_PARTIDA.put(pid, m); liveEvento(pid, m, false); avisarSiCampana(pid, m); avisarMiPartida(pid, m); }
            SwingUtilities.invokeLater(() -> { actualizarIndicadoresVivos(); refrescarAlturasWatch(); playersList.repaint(); table.repaint(); });
        }, "socket-confirmar").start();
    }

    /** Un mensaje del socket: {type:'pong'} o una lista de eventos [{type, data}]. */
    void procesarEventosSocket(String msg, Set<Long> ids) {
        Object root = Json.parse(msg);
        if (!(root instanceof List<?> eventos)) return;   // pong u otro objeto: nada que hacer
        boolean cambio = false;
        for (Object ev : eventos) {
            Map<String, Object> e = obj(ev);
            String tipo = String.valueOf(val(e, "type"));
            Map<String, Object> data = obj(val(e, "data"));
            if ("matchRemoved".equals(tipo)) {
                long mid = lng(val(data, "match_id", "matchId"));
                for (Long pid : new ArrayList<>(vivoWatch.keySet()))
                    if (Long.valueOf(mid).equals(vivoWatch.get(pid))) { vivoWatch.remove(pid); vivoInfo.remove(pid); VIVO_RIVAL.remove(pid); liveEvento(pid, null, true); cambio = true; }
                continue;
            }
            if (!"matchAdded".equals(tipo) && !"matchUpdated".equals(tipo)) continue;
            Match m = parseMatch(data);
            if (m == null) continue;
            int vigilados = 0; for (MatchPlayer mp : m.players) if (ids.contains(mp.id)) vigilados++;
            log("socket: " + tipo + " partida " + m.id + " started=" + m.started + " finished=" + m.finished + " · " + vigilados + " vigilados");
            if (m.id <= 0) continue;   // sin id de partida no hay nada que espectar
            List<Long> candidatos = new ArrayList<>();
            for (MatchPlayer mp : m.players) {
                if (!ids.contains(mp.id)) continue;
                if (m.finished != null) { vivoWatch.remove(mp.id); vivoInfo.remove(mp.id); VIVO_RIVAL.remove(mp.id); liveEvento(mp.id, m, true); cambio = true; }
                // en curso DE VERDAD: empezada (no un lobby), sin terminar y hace menos de 3 h
                else if (m.started != null && !m.started.isAfter(Instant.now().plusSeconds(60))
                        && m.started.isAfter(Instant.now().minus(Duration.ofHours(3)))
                        && !Long.valueOf(m.id).equals(vivoWatch.get(mp.id))) candidatos.add(mp.id);
            }
            if (!candidatos.isEmpty()) confirmarEventoSocket(m, candidatos);   // la API tiene la última palabra (fantasmas fuera)
        }
        if (cambio) SwingUtilities.invokeLater(() -> { actualizarIndicadoresVivos(); refrescarAlturasWatch(); playersList.repaint(); table.repaint(); });
    }

    /** Resumen compacto de la partida en curso, para la sublínea y el tooltip:
     *  1v1 → «vs Rival (CivP–CivR) · Mapa»; equipos → «TG 4v4 · Mapa». */
    static String resumenVivo(Match m, long pid) {
        if (enCursoReal(m)) { VIVO_PARTIDA.put(pid, m); VISTO_VIVO_MS.put(pid, System.currentTimeMillis()); }   // todos los caminos que detectan a alguien en partida pasan por aquí: así el menú «En partida ahora» siempre tiene la partida
        try {
            if (m.players.size() == 2) {
                MatchPlayer yo = null, riv = null;
                for (MatchPlayer p : m.players) { if (p.id == pid) yo = p; else riv = p; }
                if (riv == null) return null;
                VIVO_RIVAL.put(pid, new Object[]{ riv.id, riv.name });   // para el contextual «Rival: X»
                String civs = (yo != null && yo.civ != null && riv.civ != null)
                        ? " (" + yo.civ + "\u2013" + riv.civ + ")" : "";
                return "vs " + riv.name + (riv.rating != null ? " " + riv.rating : "") + civs
                        + (m.map == null || m.map.isBlank() ? "" : " \u00B7 " + m.map);
            }
            Map<Integer, Integer> porEquipo = new TreeMap<>();
            for (MatchPlayer p : m.players) porEquipo.merge(p.team, 1, Integer::sum);
            StringBuilder sb = new StringBuilder("TG ");
            boolean pr = true;
            for (int n : porEquipo.values()) { if (!pr) sb.append('v'); sb.append(n); pr = false; }
            return sb + (m.map == null || m.map.isBlank() ? "" : " \u00B7 " + m.map);
        } catch (Exception e) { return null; }
    }
    JButton detenerDescBtn, continuarBtn, addJugBtn;
    javax.swing.JTextField buscaNick;
    JPanel recsCards;   // «tabla» o «guia» (estado vacío que enseña el flujo)
    JButton guiaBtn;    // el «Buscar partidas» de la guía: dice lo mismo que el principal

    /** El botón principal dice lo que va a hacer. */
    Player objetivoEtiqueta;   // el jugador que nombra el botón «Buscar partidas (X)»: se busca a él, esté donde esté la vista
    void actualizarTextoBuscar() {
        if (fetchWorker != null) return;   // en marcha dice «Detener»
        String quien;
        objetivoEtiqueta = null;
        if (invitado != null) quien = nombreVisible(invitado.id(), invitado.name());
        else if (actPid > 0 && playersList.getSelectedIndices().length == 0 && (actividadAbierta || modoTop())) { quien = actNombre; objetivoEtiqueta = new Player(actPid, actNombre, ""); }   // el perfil abierto (o el último visto, si en el top no hay nadie seleccionado)
        else {
            int n = playersList.getSelectedIndices().length;
            if (n > 0) quien = n + t(" seleccionado" + (n > 1 ? "s" : ""), " selected");
            else if (modoTop()) quien = t("selecciona a alguien", "select someone");
            else quien = t("todo el grupo", "whole group");
        }
        fetchBtn.setText(t("Buscar partidas", "Search games") + " (" + quien + ")");
        if (guiaBtn != null) guiaBtn.setText(fetchBtn.getText());
        actualizarTextoForma();
    }

    void mostrarGuiaVacia(boolean guia) {
        if (recsCards != null) ((CardLayout) recsCards.getLayout()).show(recsCards, guia ? "guia" : "tabla");
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
    }
    boolean avisoTopMostrado;   // el popup del top caído: solo la primera vez por sesión
    JButton actualizarBtn;      // «Nueva versión X — Descargar», solo si existe una mayor

    /** Subtexto de quien está en partida, con el estilo de Live now: rival en color normal, civs en gris, mapa y reloj en ámbar. Se recorta a la anchura: primero cae el reloj, luego las civs, y al final se acorta el rival. */
    String subtextoVivo(long pid, FontMetrics fm, int px) {
        Match m = VIVO_PARTIDA.get(pid);
        String amb = temaOscuroActivo ? "#ffd56a" : "#b06a00";
        if (m == null || !enCursoReal(m)) {
            String info = vivoInfo.get(pid);
            return "<font color='#8a8a8a'>" + escapeHtml(truncarPx(info != null ? info : t("partida en curso \u2014 detalle en el próximo tick", "game in progress \u2014 details next tick"), fm, px)) + "</font>";
        }
        MatchPlayer yo = null; for (MatchPlayer mp : m.players) if (mp.id == pid) yo = mp;
        List<String> rivales = new ArrayList<>();
        MatchPlayer rivalUnico = null;
        for (MatchPlayer mp : m.players) { if (mp.id == pid) continue; if (yo != null && mp.team == yo.team) continue; rivales.add(nombreVisible(mp.id, mp.name) + (mp.rating != null ? " (" + mp.rating + ")" : "")); rivalUnico = mp; }
        boolean unoContraUno = m.players.size() == 2 && rivales.size() == 1;
        String prefijo = unoContraUno ? "vs " : "TG " + (m.players.size() / 2) + "v" + (m.players.size() / 2);
        String rival = unoContraUno ? String.join(", ", rivales) : "";   // en equipos, los nombres van en el clic derecho
        String civs = unoContraUno && yo != null && yo.civ != null && rivalUnico != null && rivalUnico.civ != null ? yo.civ + "\u2013" + rivalUnico.civ : "";
        String mapa = "";   // el mapa va ya en la línea del nick
        String reloj = "";  // sin reloj: molestaba
        // recorte por anchura, midiendo el texto plano
        java.util.function.Function<String[], String> plano = partes -> partes[0] + partes[1] + (partes[2].isEmpty() ? "" : " " + partes[2]) + (mapa.isEmpty() ? "" : " \u00B7 " + mapa) + (partes[3].isEmpty() ? "" : " \u00B7 " + partes[3]);
        String[] partes = { prefijo, rival, civs, reloj };
        if (fm.stringWidth(plano.apply(partes)) > px) partes[3] = "";
        if (fm.stringWidth(plano.apply(partes)) > px) partes[2] = "";
        while (fm.stringWidth(plano.apply(partes)) > px && partes[1].length() > 4) partes[1] = partes[1].substring(0, partes[1].length() - 2).trim() + "\u2026";
        StringBuilder h = new StringBuilder();
        h.append("<font color='#8a8a8a'>").append(escapeHtml(partes[0])).append("</font>").append(escapeHtml(partes[1]));
        if (!partes[2].isEmpty()) h.append(" <font color='#8a8a8a'>").append(escapeHtml(partes[2])).append("</font>");
        if (!mapa.isEmpty()) h.append(" <font color='#8a8a8a'>\u00B7</font> <font color='").append(amb).append("'>").append(escapeHtml(mapa)).append("</font>");
        if (!partes[3].isEmpty()) h.append(" <font color='#8a8a8a'>\u00B7</font> <font color='").append(amb).append("'>").append(partes[3]).append("</font>");
        return h.toString();
    }

    /** Recorta s hasta que quepa en px píxeles con la fuente dada (con «…»). */
    static String truncarPx(String s, FontMetrics fm, int px) {
        if (s == null) return "";
        if (fm == null || fm.stringWidth(s) <= px) return s;
        String puntos = "\u2026";
        int lo = 0, hi = s.length();
        while (lo < hi) {   // bisección sobre la longitud
            int mid = (lo + hi + 1) / 2;
            if (fm.stringWidth(s.substring(0, mid) + puntos) <= px) lo = mid; else hi = mid - 1;
        }
        return lo <= 0 ? puntos : s.substring(0, lo) + puntos;
    }

    /** Consulta GitHub Releases (una llamada, sin claves) y enseña el botón si hay versión nueva. */
    void comprobarActualizacion(boolean manual) {
        new Thread(() -> {
            String tag = null;
            try {
                Object root = Json.parse(httpText(RELEASES_API));
                Object t = val(obj(root), "tag_name");
                if (t != null) tag = String.valueOf(t).trim();
            } catch (Exception ex) {
                log("actualizaciones: " + causa(ex));
            }
            final String tagF = tag;
            SwingUtilities.invokeLater(() -> {
                if (tagF != null && versionMayor(tagF, VERSION)) {
                    String limpia = tagF.replaceFirst("^[vV]", "");
                    if (actualizarBtn != null) {
                        actualizarBtn.setText(t("Nueva versión ", "New version ") + limpia + t(" \u2014 Descargar", " \u2014 Download"));
                        actualizarBtn.setVisible(true);
                    }
                    if (manual) JOptionPane.showMessageDialog(this,
                            t("Hay una versión nueva: ", "There is a new version: ") + limpia
                                    + t("\nSe abrirá la página de descarga.", "\nThe download page will open."),
                            t("Actualización", "Update"), JOptionPane.INFORMATION_MESSAGE);
                    if (manual) abrirUrl(RELEASES_URL);
                } else if (manual) {
                    JOptionPane.showMessageDialog(this,
                            tagF == null ? t("No se pudo comprobar (¿sin red?).", "Couldn't check (no network?).")
                                         : t("Tienes la última versión (", "You have the latest version (") + VERSION + ").",
                            t("Actualización", "Update"), JOptionPane.INFORMATION_MESSAGE);
                }
            });
        }, "actualizaciones").start();
    }

    static int tickMs() {
        try { return Math.max(1, Integer.parseInt(leerConfig("tick_min", "1"))) * 60_000; }
        catch (Exception e) { return 60_000; }
    }

    void borrarNota(long pid, String nombre) {
        NOTAS.remove(pid);
        guardarConfig("nota_" + pid, "");
        playersList.repaint();
        refrescarAlturasWatch();
        tableModel.fireTableDataChanged();
        ajustarColumnas();
        actualizarControlesTabla();
        status.setText(t("Nota quitada a ", "Note removed from ") + nombre + ".");
    }

    void pedirNota(long pid, String nombre) {
        JTextArea area = new JTextArea(NOTAS.getOrDefault(pid, ""), 4, 34);
        area.setLineWrap(true); area.setWrapStyleWord(true);
        String guardarO = t("Guardar", "Save"), borrarO = t("Borrar", "Delete"), cancelarO = t("Cancelar", "Cancel");
        boolean tenia = NOTAS.containsKey(pid);
        Object[] ops = tenia ? new Object[]{ guardarO, borrarO, cancelarO } : new Object[]{ guardarO, cancelarO };
        int r = JOptionPane.showOptionDialog(this, new Object[]{
                t("Nota sobre ", "Note about ") + nombre + t(" (solo la ves tú):", " (only you see it):"),
                new JScrollPane(area) }, t("Nota", "Note"), JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, ops, guardarO);
        if (r < 0 || ops[r].equals(cancelarO)) return;
        String nota = ops[r].equals(borrarO) ? "" : area.getText().trim().replace("\n", " ");
        if (nota.isEmpty()) NOTAS.remove(pid); else NOTAS.put(pid, nota);
        guardarConfig("nota_" + pid, nota);
        playersList.repaint();
        refrescarAlturasWatch();   // la sublínea de la nota nace o muere: re-medir
        tableModel.fireTableDataChanged();
        status.setText(nota.isEmpty() ? t("Nota quitada.", "Note removed.") : t("Nota guardada para ", "Note saved for ") + nombre + ".");
    }

    void pedirAlias(long pid, String original) {
        String actual = ALIASES.getOrDefault(pid, "");
        String nuevo = (String) JOptionPane.showInputDialog(this,
                t("Nombre con el que quieres ver a ", "Name you want to see for ") + original
                        + t(" en toda la app (vacío = quitar el alias):", " across the app (empty = remove alias):"),
                t("Mostrar como\u2026", "Show as\u2026"), JOptionPane.PLAIN_MESSAGE, null, null, actual);
        if (nuevo == null) return;
        nuevo = nuevo.trim();
        if (nuevo.isEmpty() || nuevo.equals(original)) ALIASES.remove(pid); else ALIASES.put(pid, nuevo);
        guardarConfig("alias_" + pid, nuevo);
        playersList.repaint();
        tableModel.fireTableDataChanged();
        refrescarSujetos(ultimosSujetos, invitado != null);
        status.setText(nuevo.isEmpty() ? t("Alias quitado.", "Alias removed.")
                : original + " \u2192 " + nuevo);
    }
    JButton pararDescargasBtn;
    static final Set<Long> SUJETOS = java.util.concurrent.ConcurrentHashMap.newKeySet();   // los buscados: negrita y cabecera

    void actualizarNotaSpoilers() {
        boolean oscuro = temaOscuroActivo;
        if (mostrarResultados) {
            nota.setText(t("\u2726 Mostrando resultados: Jugador en verde si ganó y rojo si perdió, con su ±ELO; enfrentamiento completo y duración, en el tooltip de la fila.",
                           "\u2726 Showing results: Player in green if they won, red if they lost, with their ±ELO; full matchup and duration in the row tooltip."));
            nota.setForeground(oscuro ? new Color(0xd9, 0xa5, 0x5b) : new Color(0x9a, 0x6b, 0x1f));
        } else {
            nota.setText(t("Sin spoilers: nunca se muestra ganador, ±ELO ni duración. Doble clic en una fila = descargar.",
                           "Spoiler-free: winner, ±ELO and duration are never shown. Double-click a row = download."));
            ajustarGrises(oscuro);   // devuelve a la nota su gris de siempre
        }
    }
    JSplitPane splitPrincipal;
    JPanel panelDirectos, centroCards;
    JLabel directosContador, directosHora;
    final List<String[]> filasDir = new ArrayList<>();   // { login, nick visible, título visible }
    static final int MINI_W = 96, MINI_H = 54, MINI_OFFSET = MINI_W + 10;
    final Map<String, ImageIcon> minis = new java.util.concurrent.ConcurrentHashMap<>();   // login → miniatura en vivo
    final Map<String, Long> minisTs = new java.util.concurrent.ConcurrentHashMap<>();
    JTable tablaDirectos;
    ImageIcon miniPlaceholder;

    ImageIcon miniPlaceholder() {
        if (miniPlaceholder == null) {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(MINI_W, MINI_H, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(temaOscuroActivo ? new Color(0x3a, 0x3a, 0x3a) : new Color(0xdd, 0xdd, 0xdd));
            g.fillRoundRect(0, 0, MINI_W, MINI_H, 8, 8);
            g.dispose();
            miniPlaceholder = new ImageIcon(img);
        }
        return miniPlaceholder;
    }

    /** Descarga en segundo plano las miniaturas en vivo de Twitch (URL pública, sin claves) de
     *  los canales listados; se renuevan cada 5 min. */
    void cargarMiniaturas() {
        List<String> logins = new ArrayList<>();
        long ahora = System.currentTimeMillis();
        for (String[] d : filasDir)
            if (!minis.containsKey(d[0]) || ahora - minisTs.getOrDefault(d[0], 0L) > 300_000) logins.add(d[0]);
        if (logins.isEmpty()) return;
        new Thread(() -> {
            for (String login : logins) {
                try {
                    HttpRequest rq = HttpRequest.newBuilder(URI.create(
                            "https://static-cdn.jtvnw.net/previews-ttv/live_user_" + login.toLowerCase() + "-" + MINI_W + "x" + MINI_H + ".jpg"))
                            .timeout(Duration.ofSeconds(10)).header("User-Agent", UA).GET().build();
                    HttpResponse<byte[]> r = HTTP.send(rq, HttpResponse.BodyHandlers.ofByteArray());
                    if (r.statusCode() / 100 == 2) {
                        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(r.body()));
                        if (img != null) {
                            Image esc = img.getScaledInstance(MINI_W, MINI_H, Image.SCALE_SMOOTH);
                            minis.put(login, new ImageIcon(esc));
                            minisTs.put(login, System.currentTimeMillis());
                            SwingUtilities.invokeLater(() -> { if (tablaDirectos != null) tablaDirectos.repaint(); });
                        }
                    }
                } catch (Exception ex) {
                    log("miniatura " + login + ": " + causa(ex));
                }
            }
        }, "miniaturas-twitch").start();
    }
    final List<String> codigosIdiomaDir = new ArrayList<>();

    String nombreIdioma(String code) {
        try {
            String n = java.util.Locale.forLanguageTag(code)
                    .getDisplayLanguage(java.util.Locale.forLanguageTag(IDIOMA));
            if (n == null || n.isBlank()) return code.toUpperCase();
            return n.substring(0, 1).toUpperCase() + n.substring(1);
        } catch (Exception e) { return code.toUpperCase(); }
    }
    javax.swing.table.DefaultTableModel modeloDirectos;
    JComboBox<String> idiomaDirCombo;
    volatile boolean rearmandoIdiomas;
    volatile boolean vigilandoTwitch;
    String twitchHost;                                        // host que respondió (api/data)
    final Set<Long> vinculosExpandidos = new HashSet<>();
    final Map<Long, Character> marcaFila = new HashMap<>();   // pid -> P(rincipal) / E(xpandida) / H(ija)
    final Map<Long, Boolean> vivoFamilia = new HashMap<>();   // principal -> alguna cuenta viva
    final List<Player> topLadder = new ArrayList<>();
    final Map<Long, Long> lastTop = new HashMap<>();   // pid -> última partida (ms), del leaderboard
    long topCargado;
    volatile boolean cargandoTop, vigilandoTop;
    static final Path TOP_CACHE = CONFIG_FILE.resolveSibling("top_cache.txt");

    static JPanel par(java.awt.Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        for (java.awt.Component c : cs) p.add(c);
        return p;
    }

    /** ¿Qué texto hay pintado bajo el punto p en la fila idx? Pregunta a la vista HTML
     *  real del renderer (exacto sea cual sea el ancho), o "" si no hay texto ahí. */
    String textoBajo(int idx, Point p) {
        try {
            Rectangle b = playersList.getCellBounds(idx, idx);
            if (b == null || !b.contains(p)) return "";
            Component c = playersList.getCellRenderer().getListCellRendererComponent(
                    playersList, playersModel.get(idx), idx, false, false);
            if (!(c instanceof JLabel l)) return "";
            l.setBounds(0, 0, b.width, b.height);
            Object vo = l.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
            if (!(vo instanceof javax.swing.text.View v)) return "";
            Insets in = l.getInsets();
            Rectangle alloc = new Rectangle(in.left, in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
            v.setSize(alloc.width, alloc.height);   // sin tamaño, la vista responde con coordenadas colapsadas
            int x = p.x - b.x, y = p.y - b.y;
            javax.swing.text.Position.Bias[] bias = new javax.swing.text.Position.Bias[1];
            int pos = v.viewToModel(x, y, alloc, bias);
            if (pos < 0) return "";
            Shape s = v.modelToView(Math.max(0, pos - 1), alloc, javax.swing.text.Position.Bias.Forward);
            Rectangle r = s.getBounds();
            r.grow(6, 2);   // tolerancia de unos píxeles alrededor del glifo
            if (!r.contains(x, y)) return "";
            javax.swing.text.Document d = v.getDocument();
            int ini = Math.max(0, pos - 3), fin = Math.min(d.getLength(), pos + 3);
            return d.getText(ini, fin - ini);
        } catch (Exception ex) {
            return "";
        }
    }

    /** ¿El punto p cae sobre la sublínea «\u270E nota» de la fila idx (toda la línea, no solo el lápiz)? */
    boolean sobreNota(int idx, Point p) {
        try {
            if (idx < 0 || notaDe(playersModel.get(idx).id()) == null) return false;
            Rectangle b = playersList.getCellBounds(idx, idx);
            if (b == null || !b.contains(p)) return false;
            Component c = playersList.getCellRenderer().getListCellRendererComponent(
                    playersList, playersModel.get(idx), idx, false, false);
            if (!(c instanceof JLabel l)) return false;
            l.setBounds(0, 0, b.width, b.height);
            Object vo = l.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
            if (!(vo instanceof javax.swing.text.View v)) return false;
            Insets in = l.getInsets();
            Rectangle alloc = new Rectangle(in.left, in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
            v.setSize(alloc.width, alloc.height);
            int x = p.x - b.x, y = p.y - b.y;
            javax.swing.text.Document d = v.getDocument();
            String todo = d.getText(0, d.getLength());
            int lapiz = todo.lastIndexOf('\u270E');   // el último \u270E es el de la sublínea
            if (lapiz < 0) return false;
            Rectangle rNota = v.modelToView(lapiz, alloc, javax.swing.text.Position.Bias.Forward).getBounds();
            return y >= rNota.y - 2 && y <= rNota.y + rNota.height + 2 && x >= rNota.x - 6;   // la línea entera, desde el lápiz
        } catch (Exception ex) {
            return false;
        }
    }

    String celdaForma(long pid) {
        Forma f = formaActiva().get(pid);
        if (f == null) return "<font color='#8a8a8a'>\u2014</font>";   // sin consultar
        String col = f.diff() > 0 ? (temaOscuroActivo ? "#6abf69" : "#2e7d32") : f.diff() < 0 ? (temaOscuroActivo ? "#e57373" : "#c62828") : "#8a8a8a";
        return "<font color='" + col + "'>" + escapeHtml(f.corta()) + "</font>";
    }

    String rachaTexto(long pid, Forma f) {
        int n = 0; boolean gana = true;
        if (f != null && f.racha() >= 2) { n = f.racha(); gana = f.rachaGana(); }
        else if (modoTop() && TOP_STREAK.containsKey(pid) && Math.abs(TOP_STREAK.get(pid)) >= 2) { n = Math.abs(TOP_STREAK.get(pid)); gana = TOP_STREAK.get(pid) > 0; }
        if (n == 0) return "";
        return " \u00B7 " + n + (gana ? t(" victorias seguidas", " wins in a row") : t(" derrotas seguidas", " losses in a row"));
    }

    String tipForma(long pid) {
        Forma f = formaActiva().get(pid);
        StringBuilder sb = new StringBuilder(ventanaForma <= 24 ? t("Últimas 24 h: ", "Last 24 h: ") : t("Últimos 7 días: ", "Last 7 days: "));
        if (f == null) sb.append(t("sin consultar (selecciónalo y pulsa Ver forma)", "not fetched (select them and press Recent form)"));
        else if (f.partidas() == 0) sb.append(t("sin partidas 1v1", "no 1v1 games"));
        else sb.append(f.w()).append("-").append(f.l()).append(" \u00B7 ").append(f.diff() >= 0 ? "+" : "").append(f.diff()).append(rachaTexto(pid, f));
        int[] l10 = TOP_LAST10.get(pid);
        if (modoTop() && l10 != null) sb.append(" \u00B7 ").append(t("últimas 10: ", "last 10: ")).append(l10[0]).append("-").append(l10[1]);
        return sb.toString();
    }

    /** Celda de ELO fija: siempre con sitio para «elo \u21A5altElo». */
    static int anchoCeldaElo(int wPanel) { return 86; }

    boolean modoTop() {
        String s = String.valueOf(grupoCombo.getSelectedItem());
        return TOP_LADDER.equals(s) || TOP_PAIS.equals(s) || TOP_CLAN.equals(s);
    }
    boolean modoClan() { return TOP_CLAN.equals(String.valueOf(grupoCombo.getSelectedItem())); }

    boolean modoPais() { return TOP_PAIS.equals(String.valueOf(grupoCombo.getSelectedItem())); }

    String paisSel() {
        return paisActual != null ? paisActual.code() : leerConfig("top_pais", "es");
    }

    JLabel nota, firma;                       // grises regulados según el tema
    JLabel watchPista1, watchPista2, watchPista3;   // explicación visible de la Watchlist
    boolean actualizandoCombos = false;
    static boolean flatLafDisponible;         // hay jar de FlatLaf en el classpath
    static boolean temaOscuroActivo;          // último tema aplicado

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--make-ico")) {
            System.exit(generarIco() ? 0 : 1);
        }
        cargarCatalogos();
        IDIOMA = leerConfig("idioma",
                Locale.getDefault().getLanguage().equalsIgnoreCase("es") ? "es" : "en");
        SwingUtilities.invokeLater(() -> {
            String tema = temaValido(leerConfig("tema", TEMA_SISTEMA));
            flatLafDisponible = aplicarTema(tema, null);
            if (!flatLafDisponible) {
                try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                catch (Exception ignored) {}
            }
            try {
                new SpoilerFreeRecs(tema).setVisible(true);
            } catch (Throwable ex) {   // un fallo de arranque nunca más muere en silencio: queda escrito y avisa
                StringBuilder sb = new StringBuilder("Fallo al arrancar " + NOMBRE + " " + VERSION + "\n" + ex + "\n");
                for (StackTraceElement st : ex.getStackTrace()) sb.append("    at ").append(st).append("\n");
                try { Files.writeString(Path.of("arranque_error.log"), sb.toString()); } catch (Exception ignored) { }
                JOptionPane.showMessageDialog(null,
                        NOMBRE + " no ha podido arrancar.\nDetalle guardado en arranque_error.log (junto al exe).\n\n" + ex,
                        NOMBRE, JOptionPane.ERROR_MESSAGE);
                System.exit(2);
            }
        });
    }

    SpoilerFreeRecs(String temaInicial) {
        super(NOMBRE + " " + VERSION + t(" — tu radar del AoE2 competitivo, sin spoilers · por ", " — your competitive AoE2 radar, spoiler-free · by ") + AUTOR);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        aplicarVentanaGuardada();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { guardarVentana(); cerrarSocket(); }
        });
        addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent e) { ocultarHoverCard(true); }
        });
        if (logo != null) {
            // Varias escalas: Windows elige la adecuada para ventana y barra de
            // tareas. El logo es vertical: se centra en un lienzo cuadrado
            // transparente en vez de estirarlo (getScaledInstance lo deformaba).
            List<Image> iconos = new ArrayList<>();
            for (int s : new int[]{ 16, 24, 32, 48, 64, 128, 256 })
                iconos.add(iconoCuadrado(logo, s));
            setIconImages(iconos);
        }

        // Panel izquierdo: jugadores seguidos (la selección filtra la tabla)
        playersList.setVisibleRowCount(12);
        playersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { applyFilters(); ladderSincronizarSeleccion(); perfilSincronizarSeleccion(); }   // con Ratings o Perfil abiertos, la selección se refleja allí
        });
        delBtn = new JButton(t("Quitar del grupo", "Remove from group"));

        playersList.addMouseListener(new MouseAdapter() {   // botón central sobre un jugador: su perfil en pestaña nueva
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isMiddleMouseButton(e)) return;
                int idx = playersList.locationToIndex(e.getPoint());
                if (idx < 0 || !playersList.getCellBounds(idx, idx).contains(e.getPoint())) return;
                Player p = playersModel.get(idx);
                abrirPerfilEnPestana(p.id(), nombreVisible(p.id(), p.name()));
                e.consume();
            }
        });
        playersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Player p) {
                    char marca = marcaFila.getOrDefault(p.id(), ' ');
                    boolean vivo = vivoWatch.containsKey(p.id())
                            || (marca != 'H' && vivoFamilia.getOrDefault(p.id(), false));
                    Integer elo = eloWatch.get(p.id());
                    Integer rank = modoTop() ? rankTop.get(p.id()) : null;
                    String punto = vivo ? "<font color='#" + colorVivoHex() + "'>\u25CF</font>" : "";
                    String col1 = rank != null ? "<font color='gray'>" + rank + ".</font>"
                            : marca == 'P' ? "\u25B8" : marca == 'E' ? "\u25BE" : "";
                    String eloTxt = (elo != null && mostrarEloWatch) ? String.valueOf(elo) : "";
                    int w0 = Math.max(150, list.getWidth() - 22);
                    FontMetrics fmSub = l.getFontMetrics(l.getFont());   // para truncar sublíneas en píxeles reales
                    int maxChars = Math.max(10, (w0 - 76 - anchoCeldaElo(w0) - (formaVisible ? 72 : 0)) / 7);
                    String nombreVis = nombreVisible(p.id(), p.name());
                    String tip = ALIASES.containsKey(p.id())
                            ? t("Nick real: ", "Real nick: ") + p.name() : null;
                    String notaP = notaDe(p.id());
                    if (notaP != null) tip = (tip == null ? "" : tip + " \u2014 ") + t("Nota: ", "Note: ") + notaP + t(" (clic en \u270E para editar)", " (click \u270E to edit)");


                    if (nombreVis.length() > maxChars) {
                        nombreVis = nombreVis.substring(0, maxChars - 1) + "\u2026";
                        tip = p.name();
                    }
                    String[] st = twitchLive.get(p.id());   // solo para pintar el badge: sin tooltip del título (tapaba la tarjeta)
                    l.setToolTipText(tip);   // ni «jugando ahora» ni el título del stream: el punto, la sublínea y Directos ya lo cuentan
                    String mapaVivo = vivo && VIVO_PARTIDA.get(p.id()) != null && VIVO_PARTIDA.get(p.id()).map != null ? VIVO_PARTIDA.get(p.id()).map : null;
                    if (mapaVivo != null) {   // el mapa cabe en lo que sobra tras el nick; si no cabe, se recorta con «…» y, si ni siquiera hay sitio, no se pinta (el ELO nunca se tapa)
                        int sitio = maxChars - nombreVis.length() - 3;
                        if (sitio < 6) mapaVivo = null;
                        else if (mapaVivo.length() > sitio) mapaVivo = mapaVivo.substring(0, sitio - 1) + "\u2026";
                    }
                    String nick = (vivo ? "<font color='" + (temaOscuroActivo ? "#ffd56a" : "#b06a00") + "'>" + escapeHtml(nombreVis) + "</font>" + (mapaVivo != null ? " <font color='#8a8a8a'>\u00B7</font> <font color='" + (temaOscuroActivo ? "#ffd56a" : "#b06a00") + "'>" + escapeHtml(mapaVivo) + "</font>" : "") : escapeHtml(nombreVis))   // en partida: nick en ámbar y el mapa al lado
                            + (notaDe(p.id()) != null ? " <font color='#8a8a8a'>\u270E</font>" : "")
                            + (modoTop() && containsPlayerId(p.id()) ? " <font color='#8a8a8a'>\u2605</font>" : "");   // ya fichado
                    if (marca == 'H')   // hija: sangría fija y un punto menos, legible
                        nick = "&nbsp;&nbsp;&nbsp;<span style='font-size:0.92em'>" + nick + "</span>";
                    l.setBorder(null);   // renderer compartido: se fija SIEMPRE
                    String[] alt = marca == 'H' ? null : mejorAlt(p.id());
                    if (alt != null) {
                        eloTxt = eloTxt + " <font color='#8a8a8a'>\u21A5" + alt[1] + "</font>";
                        tip = (tip == null ? "" : tip + " \u2014 ")
                                + t("Su cuenta ", "Their account ") + alt[0]
                                + t(" está a ", " sits at ") + alt[1]
                                + t(" (clic para ver su perfil)", " (click to view their profile)");
                    }
                    int w = Math.max(150, list.getWidth() - 22);   // el HTML no refluye: ancho explícito
                    l.setText("<html><table width='" + w + "' cellpadding='0' cellspacing='0'><tr>"
                            + "<td width='24' align='right'>" + col1 + "</td>"
                            + "<td width='14' align='center'>" + punto + "</td>"
                            + "<td width='20' align='center'>" + (modoPais() ? "" : banderaHtml(paisDe(p.id()))) + "</td>"   // en «Top país» todas serían la misma: solo en el selector
                            + "<td nowrap align='left'>" + nick + "</td>"
                            + "<td width='26' align='center'>" + (st != null
                                    ? "<font color='#9146FF'><b>TW</b></font>" : "") + "</td>"
                            + (formaVisible ? "<td width='72' align='center'>" + celdaForma(p.id()) + "</td>" : "")
                            + "<td width='" + anchoCeldaElo(w0) + "' align='right'>" + eloTxt + "</td></tr>"
                            + "</table>"
                            // Las sublíneas van FUERA de la tabla, como bloques propios: un colspan dentro
                            // cambia el reparto de anchos de las columnas y descoloca puesto/punto/nick
                            + (vivo
                                ? "<div style='margin-left:38px'>" + subtextoVivo(p.id(), fmSub, w0 - 44) + "</div>"
                                : "")
                            + (notaDe(p.id()) != null
                                ? "<div style='margin-left:38px'><font color='#b08d57'>\u270E "
                                  + escapeHtml(truncarPx(notaDe(p.id()), fmSub, w0 - 60)) + "</font></div>"
                                : "")
                            + "</html>");
                }
                return l;
            }
        });
        playersList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  {
                ocultarHoverCard(true);
                if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
                    int idxH = playersList.locationToIndex(e.getPoint());
                    if (idxH >= 0 && playersList.getCellBounds(idxH, idxH).contains(e.getPoint())) {
                        String bajo = textoBajo(idxH, e.getPoint());   // lo que hay pintado bajo el ratón, de verdad
                        Player ph = playersModel.get(idxH);
                        if (invitado != null) {   // tocar una fila = el sujeto es ahora este; el invitado se despide
                            invitado = null;
                            SUJETOS.clear();
                            refrescarSujetos(List.of(), false);
                            actualizarTextoBuscar();
                        }
                        if (bajo.contains("\u270E") || sobreNota(idxH, e.getPoint())) { pedirNota(ph.id(), ph.name()); return; }
                        int wL = playersList.getWidth() - 22, eloW = anchoCeldaElo(wL);
                        int formaW = formaVisible ? 72 : 0;
                        boolean zonaTwFormula = e.getX() >= wL - eloW - formaW - 40 && e.getX() <= wL - eloW - formaW + 8;   // respaldo (la columna de forma desplaza el badge)
                        if (bajo.contains("TW") || (bajo.isEmpty() && zonaTwFormula)) {
                            String[] stT = twitchLive.get(ph.id());
                            if (stT != null) { abrirUrl("https://twitch.tv/" + stT[0]); return; }
                        }
                        if (bajo.contains("\u21A5") && marcaFila.getOrDefault(ph.id(), ' ') != 'H') {
                            String[] altA = mejorAlt(ph.id());
                            if (altA != null && altA.length > 2) {
                                try { abrirPerfil(Long.parseLong(altA[2]), altA[0]); return; }
                                catch (NumberFormatException ignored) { }
                            }
                        }
                        if (twitchLive.containsKey(ph.id()) && e.getX() > wL / 2)   // rastro: si un TW no reacciona, el log dice qué vio
                            log("clic sin acción en zona derecha (x=" + e.getX() + " de " + wL + ", bajo='" + bajo.trim() + "')");
                    }
                }
                if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1 && e.getX() < 26) {
                    int idx = playersList.locationToIndex(e.getPoint());
                    if (idx >= 0 && playersList.getCellBounds(idx, idx).contains(e.getPoint())) {
                        Player p = playersModel.get(idx);
                        char m = marcaFila.getOrDefault(p.id(), ' ');
                        if (m == 'P' || m == 'E') {
                            if (!vinculosExpandidos.remove(p.vinculo())) vinculosExpandidos.add(p.vinculo());
                            aplicarFiltroGrupo();
                            return;
                        }
                    }
                }
                maybePopup(e);
            }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int idx = playersList.locationToIndex(e.getPoint());
                if (idx < 0) return;
                Rectangle celda = playersList.getCellBounds(idx, idx);
                if (celda == null || !celda.contains(e.getPoint())) return;   // clic fuera de los nicks
                if (!playersList.isSelectedIndex(idx)) playersList.setSelectedIndex(idx);   // si ya está en la selección, se conserva la múltiple
                Player p = playersModel.get(idx);
                menuContextualWatchlist(p, e);
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown()) {
                    int idx1 = playersList.locationToIndex(e.getPoint());
                    boolean sobreFila = idx1 >= 0 && playersList.getCellBounds(idx1, idx1).contains(e.getPoint());
                    if (!sobreFila) { playersList.clearSelection(); actualizarTextoBuscar(); }   // como el Explorador: clic en el vacío = sin selección
                    return;
                }
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown()) {
                    int idx = playersList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        Player p = playersModel.get(idx);
                        String bajo = textoBajo(idx, e.getPoint());
                        if (bajo.contains("TW") || bajo.contains("\u21A5") || bajo.contains("\u270E") || sobreNota(idx, e.getPoint())) return;   // el clic simple ya actuó
                        playersList.setSelectedIndex(idx);   // doble clic = sus partidas, SIEMPRE (espectar vive en el clic derecho)
                        fetchMatches(fetchBtn);
                    }
                }
            }
        });
        delBtn.addActionListener(e -> {
            List<Player> sel = playersList.getSelectedValuesList();
            if (sel.isEmpty()) return;
            StringBuilder nombres = new StringBuilder();
            for (Player p : sel) nombres.append(nombres.isEmpty() ? "" : ", ").append(p.name());
            int r = JOptionPane.showConfirmDialog(this,
                    t("Se quitará de la Watchlist a: ", "This will remove from the Watchlist: ") + nombres
                            + t(". ¿Continuar?", ". Continue?"),
                    t("Quitar jugador", "Remove player"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return;
            for (Player p : sel) {
                playersModel.removeElement(p);
                todosJugadores.removeIf(x -> x.id() == p.id());
            }
            savePlayers();
            rebuildGrupos();
            applyFilters();
        });
        JPanel row2 = new JPanel(new GridLayout(1, 1, 4, 0));
        row2.add(delBtn);   // «Quitar filtro» sobra: la selección se suelta clicando en el fondo
        JPanel leftButtons = new JPanel(new GridLayout(1, 1, 0, 4));
        leftButtons.add(row2);   // «Buscar/Añadir jugador…» viven ahora en el buscador de arriba
        watchPanel = new JPanel(new BorderLayout(0, 6));
        JPanel left = watchPanel;
        tituloWatch = BorderFactory.createTitledBorder("Watchlist");
        tituloWatch.setTitleFont(tituloWatch.getTitleFont().deriveFont(Font.BOLD, 13f));
        left.setBorder(tituloWatch);
        watchPista1 = new JLabel(t("Amigos, pros o gente del clan.", "Friends, pros or clan mates."));
        watchPista2 = new JLabel(t("Selecciona para filtrar; sin selección, todos.", "Select to filter; none selected = everyone."));
        watchPista3 = new JLabel("\u21A5 " + t("= cuenta vinculada con más ELO", "= linked account with higher ELO"));
        watchPista1.setFont(watchPista1.getFont().deriveFont(Font.PLAIN, 11f));
        watchPista2.setFont(watchPista2.getFont().deriveFont(Font.PLAIN, 11f));
        watchPista3.setFont(watchPista3.getFont().deriveFont(Font.PLAIN, 11f));
        JPanel watchPistas = new JPanel();
        watchPistas.setLayout(new BoxLayout(watchPistas, BoxLayout.Y_AXIS));
        watchPistas.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
        watchPista1.setVisible(false);   // la explicación larga vive en el «?»
        watchPista3.setVisible(false);
        watchPista2.setText(t("Selecciona para filtrar \u00B7 \u21A5 = cuenta más fuerte", "Select to filter \u00B7 \u21A5 = stronger account"));
        JButton ayudaBtn = new JButton("?");
        ayudaBtn.setFocusable(false);
        ayudaBtn.setMargin(new Insets(0, 5, 0, 5));
        ayudaBtn.putClientProperty("JButton.buttonType", "roundRect");
        ayudaBtn.setToolTipText("<html>" + t("<b>Tu watchlist</b>: amigos, pros o gente del clan.<br>Selecciona jugadores para buscar solo sus partidas; sin selección, todo el grupo.<br>\u25CF rojo = jugando ahora (clic derecho \u2192 Espectar) \u00B7 TW = en Twitch (clic = su canal)<br>\u21A5 = cuenta vinculada con más ELO (clic = su perfil) \u00B7 \u270E = tiene nota (clic = editar)<br>Escribe un nick arriba y pulsa Enter para ver las partidas de cualquiera.<br>Clic en un jugador = seleccionarlo; clic en el hueco de la lista = quitar la selección; Ctrl+clic = varios.",
                "<b>Your watchlist</b>: friends, pros or clan mates.<br>Select players to search only their games; with none selected, the whole group.<br>Red \u25CF = playing now (right-click \u2192 Spectate) \u00B7 TW = on Twitch (click = channel)<br>\u21A5 = linked account with higher ELO (click = profile) \u00B7 \u270E = has a note (click = edit)<br>Type a nick above and press Enter to see anyone's games.<br>Click a player to select; click the empty space to deselect; Ctrl+click for several.") + "</html>");
        ayudaBtn.addActionListener(e -> {
            JToolTip tt = ayudaBtn.createToolTip();
            tt.setTipText(ayudaBtn.getToolTipText());
            JPopupMenu pm = new JPopupMenu();
            pm.add(tt);
            pm.show(ayudaBtn, 0, ayudaBtn.getHeight());
        });
        resumenWatch = new JLabel();   // se crea aquí: la línea del «?» lo necesita ya
        resumenWatch.setFont(resumenWatch.getFont().deriveFont(Font.PLAIN, 11f));
        JPanel pistaFila = new JPanel(new BorderLayout(6, 0));
        pistaFila.setOpaque(false);
        watchPista2.setVisible(false);   // su texto vive en el tooltip del «?»
        pistaFila.add(resumenWatch, BorderLayout.CENTER);   // «50 jugadores · 4 en directo»
        pistaFila.add(ayudaBtn, BorderLayout.EAST);
        pistaFila.setAlignmentX(Component.LEFT_ALIGNMENT);
        watchPistas.add(pistaFila);
        JPanel grupoFila = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        grupoFila.setAlignmentX(Component.LEFT_ALIGNMENT);
        grupoCombo.setToolTipText(t("Agrupa tu Watchlist (Amigos, Pros, Clan…). Se busca y se muestra el grupo activo.",
                "Group your Watchlist (Friends, Pros, Clan…). The active group is what gets searched and shown."));
        grupoCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String s = String.valueOf(value);
                boolean esNuevo = s.equals(t("+ Nuevo grupo…", "+ New group…"));
                boolean esTodos = s.equals(t("Todos", "All"));
                boolean esGestion = s.equals(t("Gestionar grupos…", "Manage groups…"));
                if (!esNuevo && !esGestion && grupoTieneVivo(esTodos ? null : s)) {
                    l.setText("<html><font color='#" + colorVivoHex() + "'>\u25CF</font> "
                            + escapeHtml(s) + "</html>");
                }
                return l;
            }
        });
        grupoCombo.addActionListener(e -> onGrupoElegido());
        grupoCombo.setFont(grupoCombo.getFont().deriveFont(Font.BOLD));
        grupoFila.add(grupoCombo);
        soloVivosBtn = new JToggleButton("\u25CF " + t("Jugando", "Playing"));
        soloVivosBtn.setToolTipText(t(
                "Detecta partidas EN CURSO de cualquier modo (1v1, TG, lo que sea). Límites: partidas de más de 3 h se consideran colgadas, y las salas personalizadas a veces no aparecen hasta terminar.",
                "Detects games IN PROGRESS of any mode (1v1, TG, anything). Limits: games over 3 h are treated as hung, and custom lobbies sometimes only show up once finished."));
        soloVivosBtn.setToolTipText(t("Muestra solo a los que están jugando ahora, dentro del grupo elegido (con «Todos», de toda la lista)",
                "Show only who is playing right now, within the selected group (with “All”, across your whole list)"));
        soloVivosBtn.setFocusable(false);
        soloVivosBtn.addActionListener(e -> {
            playersList.setFixedCellHeight(0);
            playersList.setFixedCellHeight(-1);   // invalida el caché de ALTURAS (las sublíneas de vivos)
            aplicarFiltroGrupo();
        });
        String paisGuardado = leerConfig("top_pais", "es");
        List<PaisItem> ordenPais = new ArrayList<>();
        for (PaisItem pi : PAISES) if (pi.code().equals(paisGuardado)) ordenPais.add(pi);
        List<PaisItem> resto = new ArrayList<>();
        for (PaisItem pi : PAISES) if (!pi.code().equals(paisGuardado)) resto.add(pi);
        resto.sort(Comparator.comparing(PaisItem::nombre, String.CASE_INSENSITIVE_ORDER));
        ordenPais.addAll(resto);
        catalogoOrdenado = ordenPais;
        paisActual = ordenPais.get(0);   // el favorito guardado
        buscaPais = new javax.swing.JTextField(6);
        buscaPais.putClientProperty("JTextField.placeholderText", t("Buscar\u2026", "Search\u2026"));
        buscaPais.setToolTipText(t("Filtra países al teclear: «bul» → Bulgaria; Enter elige el primero",
                "Filters countries as you type: \u201Cbul\u201D \u2192 Bulgaria; Enter picks the first"));
        paisCombo = new JComboBox<>(ordenPais.toArray(PaisItem[]::new));
        paisCombo.setRenderer(new DefaultListCellRenderer() {   // con su bandera
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean foc) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, value, index, sel, foc);
                if (value instanceof PaisItem pi) { lab.setIcon(iconoBandera(pi.code())); lab.setIconTextGap(6); }
                return lab;
            }
        });
        paisCombo.setSelectedIndex(0);
        paisCombo.setMaximumRowCount(14);
        paisCombo.setToolTipText(t("País del top a mostrar (se recuerda como predeterminado)",
                "Country whose top to show (remembered as default)"));
        paisCombo.addActionListener(e -> {
            if (!rearmandoPais && paisCombo.getSelectedItem() instanceof PaisItem p) fijarPais(p);
        });
        instalarFiltroPais();
        paisCombo.setPrototypeDisplayValue(null);
        paisCombo.setPreferredSize(new Dimension(150, paisCombo.getPreferredSize().height));   // más compacto: el nombre se ve, no manda
        buscaPais.setColumns(8);
        parPais = new JPanel(new BorderLayout(6, 0));
        parPais.setOpaque(false);
        parPais.add(new JLabel(t("País:", "Country:")), BorderLayout.WEST);
        parPais.add(buscaPais, BorderLayout.CENTER);
        parPais.add(paisCombo, BorderLayout.EAST);
        parPais.setAlignmentX(Component.LEFT_ALIGNMENT);
        parPais.setVisible(false);
        JButton nuevoGrupoBtn = new JButton(t("Crear Grupo", "Create Group"));
        nuevoGrupoBtn.setToolTipText(t("Crear un grupo nuevo", "Create a new group"));
        nuevoGrupoBtn.addActionListener(e -> crearGrupoDialog());
        JButton gestGruposBtn = new JButton(t("Gestionar…", "Manage…"));
        gestGruposBtn.setToolTipText(t("Renombrar o borrar grupos", "Rename or delete groups"));
        gestGruposBtn.addActionListener(e -> gestionarGrupos());
        JButton gruposBtn = new JButton("\u2699");
        gruposBtn.setFocusable(false);
        gruposBtn.setMargin(new Insets(1, 7, 1, 7));
        gruposBtn.putClientProperty("JButton.buttonType", "roundRect");
        gruposBtn.setToolTipText(t("Grupos: crear, renombrar o borrar", "Groups: create, rename or delete"));
        gruposBtn.addActionListener(e -> {
            JPopupMenu pm = new JPopupMenu();
            JMenuItem crear = new JMenuItem(t("Crear grupo\u2026", "Create group\u2026"));
            crear.addActionListener(a -> crearGrupoDialog());
            JMenuItem gest = new JMenuItem(t("Gestionar grupos\u2026", "Manage groups\u2026"));
            gest.addActionListener(a -> gestionarGrupos());
            pm.add(crear); pm.add(gest);
            pm.show(gruposBtn, 0, gruposBtn.getHeight());
        });
        grupoFila.add(gruposBtn);
        campanaBtn = new JToggleButton(iconoVista("campana"));
        campanaBtn.setFocusable(false); campanaBtn.setMargin(new Insets(2, 5, 2, 5)); campanaBtn.putClientProperty("JButton.buttonType", "roundRect");
        campanaBtn.addActionListener(e -> alternarCampana());
        grupoFila.add(campanaBtn);
        topNCombo = new JComboBox<>(new String[]{ "Top 25", "Top 50", "Top 100" });
        topNCombo.setToolTipText(t("Cuántos jugadores enseñan Top ladder y Top país", "How many players Top ladder and Top country show"));
        topNCombo.setSelectedIndex(Math.max(0, Arrays.asList("25", "50", "100").indexOf(leerConfig("top_n", "50"))));
        topNCombo.addActionListener(e -> { if (rellenandoTopN) return; String n = new String[]{ "25", "50", "100" }[Math.max(0, topNCombo.getSelectedIndex())]; if (n.equals(leerConfig("top_n", "50"))) return; guardarConfig("top_n", n); if (modoTop() && !modoClan()) { topCargado = 0; cargarTopLadder(true); } });
        topNCombo.setVisible(false);
        grupoFila.add(topNCombo);
        addJugBtn = new JButton(t("+ Añadir jugador", "+ Add player"));
        addJugBtn.setFocusable(false);
        addJugBtn.setMargin(new Insets(1, 7, 1, 7));
        addJugBtn.putClientProperty("JButton.buttonType", "roundRect");
        addJugBtn.setToolTipText(t("Busca un jugador por nick y añádelo a este grupo", "Find a player by nick and add them to this group"));
        addJugBtn.addActionListener(e -> addPlayerDialog(false, buscaNick != null ? buscaNick.getText().trim() : null));
        grupoFila.add(addJugBtn);
        // Fila 3: el chip «● En directo» y el resumen en gris
        soloVivosBtn.putClientProperty("JButton.buttonType", "roundRect");
        soloVivosBtn.setFocusable(false);
        JPanel filtroFila = new JPanel(new BorderLayout(8, 0));
        filtroFila.setOpaque(false);
        filtroFila.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        formaBtn = new JButton(t("Ver forma", "Recent form"));
        formaBtn.putClientProperty("JButton.buttonType", "roundRect");
        formaBtn.setFocusable(false);
        formaBtn.setToolTipText(tipVerForma());
        formaBtn.addActionListener(e -> cargarForma(objetivoForma(), ventanaForma, () -> {   // siempre consulta (y suma a lo ya consultado)
            formaVisible = true; actualizarTextoForma();
            if (ocultarFormaBtn != null) ocultarFormaBtn.setVisible(true);
            refrescarCabeceraOrden(); aplicarFiltroGrupo(); playersList.repaint();
        }));
        ocultarFormaBtn = new JButton(t("Ocultar forma", "Hide recent form"));
        ocultarFormaBtn.setFocusable(false);
        ocultarFormaBtn.setMargin(new Insets(1, 6, 1, 6));
        ocultarFormaBtn.putClientProperty("JButton.buttonType", "roundRect");
        ocultarFormaBtn.setToolTipText(tipOcultarForma());
        ocultarFormaBtn.setVisible(false);
        ocultarFormaBtn.addActionListener(e -> apagarForma());
        JComboBox<String> ventanaCb = new JComboBox<>(new String[]{ t("últimas 24 h", "last 24 h"), t("últimos 7 días", "last 7 days") });
        ventanaCb.setFocusable(false);
        ventanaCb.setToolTipText(t("Ventana de la forma que consulta «Ver forma»", "Window used by \u201CRecent form\u201D"));
        ventanaCb.addActionListener(e -> {
            ventanaForma = ventanaCb.getSelectedIndex() == 1 ? 24 * 7 : 24;
            if (formaVisible) { refrescarCabeceraOrden(); aplicarFiltroGrupo(); playersList.repaint(); }   // la columna cambia de ventana al instante
        });
        JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));   // envuelve a otra línea, nunca se trunca
        chips.setOpaque(false);
        chips.add(soloVivosBtn); chips.add(formaBtn); chips.add(ventanaCb); chips.add(ocultarFormaBtn);
        filtroFila.add(chips, BorderLayout.CENTER);
        filtroFila.setAlignmentX(Component.LEFT_ALIGNMENT);
        buscaNick = new javax.swing.JTextField();
        buscaNick.putClientProperty("JTextField.placeholderText",
                t("\uD83D\uDD0D Buscar jugador por nick  (Enter = ver sus partidas)", "\uD83D\uDD0D Find a player by nick  (Enter = see their games)"));
        buscaNick.setToolTipText(t("Escribe un nick y pulsa Enter: verás sus partidas sin añadirlo; desde su nombre podrás ficharlo a un grupo.",
                "Type a nick and press Enter: you'll see their games without adding them; from their name you can add them to a group."));
        JPopupMenu nickPopup = new JPopupMenu(); nickPopup.setFocusable(false);
        javax.swing.Timer nickDebounce = new javax.swing.Timer(450, ev -> {
            String q = buscaNick.getText().trim();
            nickPopup.setVisible(false); nickPopup.removeAll();
            if (q.length() < 2) return;
            new Thread(() -> {
                List<String[]> res = sugerirPerfiles(q);
                SwingUtilities.invokeLater(() -> {
                    if (!q.equals(buscaNick.getText().trim())) return;
                    nickPopup.removeAll();
                    int n = 0;
                    for (String[] r : res) {
                        JMenuItem it = new JMenuItem(r[2]);
                        String nombre = r[1];
                        long pidSug = Long.parseLong(r[0]);
                        it.addActionListener(a -> { nickPopup.setVisible(false); buscaNick.setText(nombre); jugadorElegido(pidSug, nombre); });   // el elegido es ESTE jugador (por id): nada de volver a buscar por nombre
                        nickPopup.add(it);
                        if (++n >= 8) break;
                    }
                    if (n > 0 && buscaNick.isShowing() && buscaNick.hasFocus()) nickPopup.show(buscaNick, 0, buscaNick.getHeight());
                });
            }, "nick-sugerir").start();
        });
        nickDebounce.setRepeats(false);
        buscaNick.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { nickDebounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { nickDebounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { nickDebounce.restart(); }
        });
        buscaNick.addKeyListener(new KeyAdapter() { @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ESCAPE) nickPopup.setVisible(false); } });
        buscaNick.addActionListener(e -> {
            nickPopup.setVisible(false);
            String q = buscaNick.getText().trim();
            if (!q.isEmpty()) { addPlayerDialog(true, q); buscaNick.setText(""); }
        });
        JPanel buscaFila = new JPanel(new BorderLayout());
        buscaFila.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        buscaFila.add(buscaNick, BorderLayout.CENTER);
        JPanel norteWatch = new JPanel();
        norteWatchRef = norteWatch;
        norteWatch.setLayout(new BoxLayout(norteWatch, BoxLayout.Y_AXIS));
        norteWatch.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
        buscaFila.setAlignmentX(Component.LEFT_ALIGNMENT);
        norteWatch.add(buscaFila);      // 1. el buscador
        norteWatch.add(grupoFila);      // 2. la vista (grupo · ⚙ · + Añadir jugador)
        norteWatch.add(parPais);        //    el país, en su propia línea, solo en Top país
        clanField = new JTextField(10);
        clanField.putClientProperty("JTextField.placeholderText", t("Tag del clan (R1, DK, TdB…)", "Clan tag (R1, DK, TdB…)"));
        clanField.setText(leerConfig("clan_tag", ""));
        clanPopup = new JPopupMenu(); clanPopup.setFocusable(false);
        clanField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() {
                clanPopup.setVisible(false); clanPopup.removeAll();
                if (clanField.getText().trim().length() < 1) return;
                if (clanes.isEmpty()) { Thread th = new Thread(() -> ladderAsegurar(false), "clanes"); th.setDaemon(true); th.start(); return; }
                for (Map.Entry<String, Integer> en : sugerirClanes(clanField.getText())) {
                    JMenuItem it = new JMenuItem(en.getKey() + "  (" + en.getValue() + ")");
                    it.addActionListener(a -> { clanField.setText(en.getKey()); clanPopup.setVisible(false); cargarTopClan(); });
                    clanPopup.add(it);
                }
                if (clanPopup.getComponentCount() > 0) clanPopup.show(clanField, 0, clanField.getHeight());
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        clanField.addActionListener(e -> { clanPopup.setVisible(false); cargarTopClan(); });
        parClan = new JPanel(new BorderLayout(6, 0));
        parClan.setOpaque(false);
        parClan.add(new JLabel(t("Clan:", "Clan:")), BorderLayout.WEST);
        parClan.add(clanField, BorderLayout.CENTER);
        clanesGuardadosCombo = new JComboBox<>();
        clanesGuardadosCombo.setToolTipText(t("Tus clanes guardados: elige uno para cargar su top", "Your saved clans: pick one to load its top"));
        clanesGuardadosCombo.addActionListener(e -> { if (rellenandoClanes) return; Object v = clanesGuardadosCombo.getSelectedItem(); if (v instanceof String tag && !tag.isBlank() && !tag.startsWith("(")) { clanField.setText(tag); clanPopup.setVisible(false); cargarTopClan(); } });
        clanEstrella = new JButton(t("Guardar clan", "Save clan"));
        clanEstrella.setFocusable(false); clanEstrella.setMargin(new Insets(1, 8, 1, 8));
        clanEstrella.addActionListener(e -> {
            String tag = clanField.getText().trim();
            if (tag.isEmpty()) return;
            List<String> l = clanesGuardados();
            if (l.removeIf(x -> x.equalsIgnoreCase(tag))) status.setText(t("Clan quitado de guardados: ", "Clan removed from saved: ") + tag); else { l.add(tag); status.setText(t("Clan guardado: ", "Clan saved: ") + tag); }
            guardarConfig("clanes_guardados", String.join(",", l));
            refrescarClanesGuardados();
        });
        parClan.add(clanEstrella, BorderLayout.EAST);
        parClanGuardados = new JPanel(new BorderLayout(6, 0));
        parClanGuardados.setOpaque(false);
        parClanGuardados.add(new JLabel(t("Clanes guardados:", "Saved clans:")), BorderLayout.WEST);
        parClanGuardados.add(clanesGuardadosCombo, BorderLayout.CENTER);
        parClanGuardados.setAlignmentX(Component.LEFT_ALIGNMENT);
        parClanGuardados.setVisible(false);
        clanField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() { refrescarClanesGuardados(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        refrescarClanesGuardados();
        parClan.setAlignmentX(Component.LEFT_ALIGNMENT);
        parClan.setVisible(false);
        norteWatch.add(parClan);        //    el clan, en su propia línea, solo en Top clan
        norteWatch.add(parClanGuardados);   //    y debajo, los guardados
        norteWatch.add(filtroFila);     // 3. chip En directo + resumen
        norteWatch.add(watchPistas);    //    una línea de pista + «?»
        left.add(norteWatch, BorderLayout.NORTH);
        JLabel cabLabel = new JLabel();
        cabLabel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Component.borderColor") != null
                        ? UIManager.getColor("Component.borderColor") : Color.GRAY));
        cabLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cabLabel.setToolTipText(t("Clic en Nick o en ELO para ordenar por esa columna",
                "Click Nick or ELO to sort by that column"));
        this.cabLabel = cabLabel;
        cabLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int wC = cabLabel.getWidth();
                String actual = leerConfig("orden_watch", "elo");
                String nuevo = e.getX() >= wC - 60 ? "elo"
                        : (formaVisible && e.getX() >= wC - 60 - 72 - 30) ? ("forma".equals(actual) ? "forma_asc" : "forma")   // 2.º clic: invierte
                        : "alfa";
                guardarConfig("orden_watch", nuevo);
                aplicarFiltroGrupo();
                refrescarCabeceraOrden();
            }
        });
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.add(cabLabel, BorderLayout.CENTER);
        hoverTimer = new javax.swing.Timer(600, ev -> {
            if (hoverPid != 0 && hoverPantalla != null && hoverProcede()) {
                int idx = playersList.locationToIndex(
                        new Point(playersList.getLocationOnScreen().x + 5,
                                  hoverPantalla.y - playersList.getLocationOnScreen().y >= 0
                                          ? hoverPantalla.y - playersList.getLocationOnScreen().y : 0));
                // el pid ya quedó fijado al mover: mostramos directamente
                for (int i = 0; i < playersModel.size(); i++)
                    if (playersModel.get(i).id() == hoverPid) {
                        mostrarPerfilCard(hoverPid, playersModel.get(i).name(), hoverPantalla);
                        return;
                    }
            }
        });
        hoverTimer.setRepeats(false);
        playersList.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int idx = playersList.locationToIndex(e.getPoint());
                long pid = 0;
                if (idx >= 0 && playersList.getCellBounds(idx, idx).contains(e.getPoint()))
                    pid = playersModel.get(idx).id();
                if (pid != 0 && enZonaForma(e.getPoint())) pid = 0;   // sobre la celda Forma no sale la tarjeta: sale su tooltip
                String bajoM = pid != 0 ? textoBajo(idx, e.getPoint()) : "";   // mano sobre TW, \u21A5 y \u270E
                boolean clicable = bajoM.contains("TW") || bajoM.contains("\u21A5") || bajoM.contains("\u270E")
                        || (pid != 0 && sobreNota(idx, e.getPoint()));
                playersList.setCursor(Cursor.getPredefinedCursor(clicable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                if (pid != hoverPid) {
                    if (hoverCard != null) ocultarHoverCard();
                    hoverTimer.stop();
                    hoverPid = pid;
                    if (false && pid != 0) {   // tarjeta flotante desactivada: el ELO ya está en la lista y el perfil a un doble clic (y cada tarjeta era una llamada a la API)
                        hoverPantalla = e.getLocationOnScreen();
                        hoverTimer.restart();
                    }
                }
            }
        });
        playersList.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { ocultarHoverCard(); }
        });
        ToolTipManager.sharedInstance().registerComponent(playersList);
        playersList.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { refrescarCabeceraOrden(); }
        });
        JPanel listWrap = new JPanel(new BorderLayout());

        JScrollPane listScroll = new JScrollPane(playersList);
        listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.getViewport().addMouseListener(new MouseAdapter() {   // el hueco bajo la última fila es del visor
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown()) {
                    playersList.clearSelection();
                    actualizarTextoBuscar();
                }
            }
        });
        listScroll.getViewport().addChangeListener(e -> {
            ocultarHoverCard();
            int vw = listScroll.getViewport().getWidth();
            if (vw > 0 && playersList.getFixedCellWidth() != vw)
                playersList.setFixedCellWidth(vw);   // orden, no sugerencia: invalida el caché de medidas
            refrescarCabeceraOrden();
        });
        sujetosPanel = new JPanel();
        sujetosPanel.setLayout(new BoxLayout(sujetosPanel, BoxLayout.Y_AXIS));
        sujetosPanel.setVisible(false);
        JPanel norteLista = new JPanel(new BorderLayout());
        norteLista.add(sujetosPanel, BorderLayout.NORTH);
        norteLista.add(cabecera, BorderLayout.SOUTH);   // la cabecera, pegada a su lista
        listWrap.add(norteLista, BorderLayout.NORTH);
        listWrap.add(listScroll, BorderLayout.CENTER);
        left.add(listWrap, BorderLayout.CENTER);
        refrescarCabeceraOrden();
        left.add(leftButtons, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(280, 0));

        // Barra superior: ventana de horas + buscar + filtro de modo + acerca de
        fetchBtn.addActionListener(e -> fetchMatches(fetchBtn));
        modeCombo.setPrototypeDisplayValue("RM Team MegaRandom XL");
        modeCombo.addActionListener(e -> { if (!actualizandoCombos) applyFilters(); });
        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));   // fija: nada salta de sitio
        JPanel filaVistas = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 0));
        unidadCombo.setSelectedIndex(Math.max(0, Math.min(2, Integer.parseInt(leerConfig("ventana_unidad", "0")))));
        unidadCombo.setFocusable(false);
        unidadCombo.setToolTipText(t("Unidad de la ventana de búsqueda (hasta 1 año)", "Unit of the search window (up to 1 year)"));
        hoursSpinner.setToolTipText(t("Ventana de búsqueda: hasta 1 año. Las ventanas largas piden más páginas por jugador (tope: 300 partidas/jugador, se avisa).",
                "Search window: up to 1 year. Long windows fetch more pages per player (cap: 300 games/player, you get a warning)."));
        unidadCombo.addActionListener(e -> {
            guardarConfig("ventana_unidad", String.valueOf(unidadCombo.getSelectedIndex()));
            int u = unidadCombo.getSelectedIndex(), max = u == 0 ? 24 : u == 1 ? 7 : 1;   // el techo, en la unidad elegida: 24 h, 7 días o 1 semana
            SpinnerNumberModel sm = (SpinnerNumberModel) hoursSpinner.getModel();
            sm.setMaximum(max);
            if ((int) hoursSpinner.getValue() > max) hoursSpinner.setValue(max);
        });
        { int u0 = unidadCombo.getSelectedIndex(); int max0 = u0 == 0 ? 24 : u0 == 1 ? 7 : 1; ((SpinnerNumberModel) hoursSpinner.getModel()).setMaximum(max0); if ((int) hoursSpinner.getValue() > max0) hoursSpinner.setValue(max0); }
        fila1.add(par(new JLabel(t("Últimas", "Last")), hoursSpinner, unidadCombo));
        parModo = par(new JLabel(t("Modo:", "Mode:")), modeCombo);   // los dos parámetros, pegados al botón que lanzan
        fila1.add(parModo);
        rivalField = new JTextField(11);
        rivalField.putClientProperty("JTextField.placeholderText", t("Rival…", "Opponent…"));
        rivalField.putClientProperty("JTextField.showClearButton", true);
        rivalField.setToolTipText(t("Filtra la tabla por el rival (en equipos, cualquiera del equipo contrario). Escribe para ver sugerencias.",
                "Filters the table by opponent (in team games, anyone on the other team). Type to see suggestions."));
        rivalPopup = new JPopupMenu();
        rivalPopup.setFocusable(false);
        rivalField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() { filtroRival = normalizarNick(rivalField.getText()); applyFilters(); sugerirRivales(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        rivalField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (!rivalPopup.isVisible() || rivalPopup.getComponentCount() == 0) return;
                if (e.getKeyCode() == KeyEvent.VK_DOWN) { ((JMenuItem) rivalPopup.getComponent(0)).doClick(); e.consume(); }
                else if (e.getKeyCode() == KeyEvent.VK_ENTER) { ((JMenuItem) rivalPopup.getComponent(0)).doClick(); e.consume(); }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) rivalPopup.setVisible(false);
            }
        });
        rivalField.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { rivalPopup.setVisible(false); } });
        parRival = par(new JLabel(t("Rival:", "Opponent:")), rivalField);
        fila1.add(parRival);
        mapaCombo.setToolTipText(t("Filtra la tabla por mapa (sobre las partidas cargadas, sin llamadas)", "Filters the table by map (over the loaded games, no requests)"));
        periodoCombo.setToolTipText(t("Filtra la tabla por fecha de la partida (sobre las partidas cargadas)", "Filters the table by game date (over the loaded games)"));
        mapaCombo.addActionListener(e -> { if (!actualizandoCombos) applyFilters(); });
        periodoCombo.addActionListener(e -> { if (!actualizandoCombos) applyFilters(); });
        fila1.add(par(new JLabel(t("Mapa:", "Map:")), mapaCombo));
        fila1.add(par(new JLabel(t("Periodo:", "Period:")), periodoCombo));
        fila1.add(fetchBtn);
        JSeparator sepModos = new JSeparator(SwingConstants.VERTICAL);
        sepModos.setPreferredSize(new Dimension(1, 24));

        azarBtn.setToolTipText(t("Hasta 10 partidas 1v1 recientes del ladder con ambos jugadores en el rango de ELO elegido", "Up to 10 recent 1v1s from the ladder with both players inside your ELO range"));

        azarBtn.addActionListener(e -> buscarAleatorias());
        fila1.add(azarBtn);
        gteBtn.setToolTipText(t("5 partidas 1v1 recientes de cualquier ELO, anónimas: adivina el ELO y compruébalo con «Revelar resultado…»", "5 recent anonymous 1v1s from any ELO: guess the ELO, then check with “Reveal result…”"));

        gteBtn.addActionListener(e -> buscarGte());
        fila1.add(gteBtn);
        atrasBtn = new JButton("\u2190");
        atrasBtn.setFocusable(false); atrasBtn.setMargin(new Insets(2, 8, 2, 8)); atrasBtn.putClientProperty("JButton.buttonType", "roundRect");
        atrasBtn.setToolTipText(t("Atrás: vuelve a la vista anterior (también el botón lateral del ratón)", "Back: return to the previous view (also the mouse's back button)"));
        atrasBtn.setEnabled(false);
        atrasBtn.addActionListener(e -> volverAtras());
        filaVistas.add(atrasBtn);
        adelanteBtn = new JButton("\u2192");
        adelanteBtn.setFocusable(false); adelanteBtn.setMargin(new Insets(2, 8, 2, 8)); adelanteBtn.putClientProperty("JButton.buttonType", "roundRect");
        adelanteBtn.setToolTipText(t("Adelante (también el botón lateral del ratón)", "Forward (also the mouse's forward button)"));
        adelanteBtn.setEnabled(false);
        adelanteBtn.addActionListener(e -> irAdelante());
        filaVistas.add(adelanteBtn);
        recsBtn = pestana(t("Partidas", "Games"), iconoVista("partidas"));
        recsBtn.addActionListener(e -> {   // desde un perfil: la tabla pasa a ser de ese jugador (búsqueda de sus partidas recientes), salvo que ya lo sea
            if (actPid > 0 && objetivoEtiqueta != null && objetivoEtiqueta.id() == actPid && historialEnTabla != actPid && (ultimosSujetos.size() != 1 || ultimosSujetos.get(0).id() != actPid) && fetchWorker == null)
                SwingUtilities.invokeLater(() -> fetchMatches(fetchBtn));
        });
        recsBtn.setSelected(true);
        recsBtn.setToolTipText(t("La tabla de partidas de tu watchlist (recs sin spoilers)", "Your watchlist's games table (spoiler-free recs)"));
        recsBtn.addActionListener(e -> { if (!recsBtn.isSelected()) { recsBtn.setSelected(true); return; } mostrarDirectos(false); });
        filaVistas.add(recsBtn);
        directosBtn = pestana("Twitch", iconoVista("directos"));
        directosBtn.setToolTipText(t("Todos los canales dando AoE2 en Twitch ahora mismo",
                "Every channel streaming AoE2 on Twitch right now"));
        directosBtn.addActionListener(e -> { if (!directosBtn.isSelected()) { directosBtn.setSelected(true); return; } mostrarDirectos(true); });
        ahoraBtn = pestana("Live now", iconoVista("ahora"));
        ahoraBtn.setToolTipText(t("Partidas en curso de los 250 mejores del ladder 1v1, en vivo: bandos, mapa, reloj, Twitch, espectar; y las terminadas en las últimas 2 h con su rec", "Ongoing games of the top 250 of the 1v1 ladder, live: sides, map, clock, Twitch, spectate; and those finished in the last 2 h with their rec"));
        ahoraBtn.addActionListener(e -> { if (!ahoraBtn.isSelected()) { ahoraBtn.setSelected(true); return; } abrirAhora(); });
        filaVistas.add(ahoraBtn);
        filaVistas.add(directosBtn);
        perfilBtn = pestana(t("Perfil", "Profile"), iconoVista("perfil"));
        perfilBtn.setToolTipText(t("Perfil y actividad del último año del jugador seleccionado en la watchlist (o de cualquier nick): ELO, forma, calendario, civs, mapas y rivales",
                "Profile and last year's activity of the player selected in the watchlist (or any nick): ELO, form, calendar, civs, maps and rivals"));
        perfilBtn.addActionListener(e -> { if (!perfilBtn.isSelected()) { perfilBtn.setSelected(true); return; } perfilDesdeBoton(); });
        filaVistas.add(perfilBtn);
        ladderBtn = pestana("Ratings", iconoVista("ratings"));
        ladderBtn.setToolTipText(t("Distribución de ELO por ladder, percentiles, dispersión 1v1 × equipos y comparador de jugadores (volcado diario de aoe2companion)",
                "ELO distribution per ladder, percentiles, 1v1 × team scatter and player comparison (aoe2companion daily dump)"));
        ladderBtn.addActionListener(e -> { if (!ladderBtn.isSelected()) { ladderBtn.setSelected(true); return; } abrirLadder(); });
        filaVistas.add(ladderBtn);
        civStatsBtn = pestana("Civ Stats", iconoVista("civstats"));
        civStatsBtn.setToolTipText(t("Winrate y pick rate por civilización, mapa y tramo de ELO; matchups y tendencias (volcados diarios de aoe2companion)",
                "Win rate and pick rate by civilization, map and ELO bracket; matchups and trends (aoe2companion daily dumps)"));
        civStatsBtn.addActionListener(e -> { if (!civStatsBtn.isSelected()) { civStatsBtn.setSelected(true); return; } abrirCivStats(); });
        filaVistas.add(civStatsBtn);
        techTreeBtn = pestana("Tech tree", ttIconoBoton());
        techTreeBtn.setIconTextGap(6);
        techTreeBtn.setToolTipText(t("Árbol tecnológico de cada civilización (datos de aoe2techtree, se actualizan solos)",
                "Every civilization's tech tree (data from aoe2techtree, updates itself)"));
        techTreeBtn.addActionListener(e -> { if (!techTreeBtn.isSelected()) { techTreeBtn.setSelected(true); return; } abrirTechTree(null); });
        filaVistas.add(techTreeBtn);
        filaVistas.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(128, 128, 128, 70)));

        JButton configBtn = new JButton(t("Configuración ▾", "Settings ▾"));
        configMenu = new JPopupMenu();

        JMenu idiomaMenu = new JMenu(t("Idioma", "Language"));
        ButtonGroup gIdioma = new ButtonGroup();
        for (String[] par : new String[][]{ { "Español", "es" }, { "English", "en" } }) {
            String valor = par[1];
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(par[0], valor.equals(IDIOMA));
            it.addActionListener(e -> {
                guardarConfig("idioma", valor);
                JOptionPane.showMessageDialog(this,
                        valor.equals("es") ? "El idioma se aplicará la próxima vez que abras la aplicación."
                                           : "The language will apply the next time you open the app.",
                        valor.equals("es") ? "Idioma" : "Language", JOptionPane.INFORMATION_MESSAGE);
            });
            gIdioma.add(it);
            idiomaMenu.add(it);
        }

        JMenu temaMenu = new JMenu(t("Tema", "Theme"));
        ButtonGroup gTema = new ButtonGroup();
        for (String[] par : new String[][]{ { t("Sistema", "System"), TEMA_SISTEMA }, { t("Claro", "Light"), TEMA_CLARO }, { t("Oscuro", "Dark"), TEMA_OSCURO } }) {
            String valor = par[1];
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(par[0], valor.equals(temaInicial));
            it.addActionListener(e -> { guardarConfig("tema", valor); aplicarTema(valor, this); });
            gTema.add(it);
            temaMenu.add(it);
        }
        temaMenu.setEnabled(flatLafDisponible);

        JMenu letraMenu = new JMenu(t("Letra", "Font size"));
        ButtonGroup gLetra = new ButtonGroup();
        String letraSel = leerConfig("letra", "grande");
        for (String[] par : new String[][]{ { t("Pequeño", "Small"), "normal" }, { t("Normal", "Normal"), "grande" }, { t("Grande", "Large"), "muygrande" } }) {
            String valor = par[1];
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(par[0], valor.equals(letraSel));
            it.addActionListener(e -> {
                guardarConfig("letra", valor);
                aplicarTema(temaValido(leerConfig("tema", TEMA_SISTEMA)), this);
            });
            gLetra.add(it);
            letraMenu.add(it);
        }
        letraMenu.setEnabled(flatLafDisponible);

        JCheckBoxMenuItem buscarAbrirItem = new JCheckBoxMenuItem(t("Buscar al abrir", "Search on startup"),
                Boolean.parseBoolean(leerConfig("buscar_al_abrir", "true")));
        buscarAbrirItem.setToolTipText(t("Al abrir la app, busca partidas de tus seguidos automáticamente", "Search your watchlist automatically when the app opens"));
        buscarAbrirItem.addActionListener(e ->
                guardarConfig("buscar_al_abrir", String.valueOf(buscarAbrirItem.isSelected())));

        JCheckBoxMenuItem autoWinItem = new JCheckBoxMenuItem(t("Ejecutar al iniciar Windows", "Run at Windows startup"),
                Boolean.parseBoolean(leerConfig("autoarranque", "false")));
        if (rutaExePropia() == null) {
            autoWinItem.setEnabled(false);
            autoWinItem.setSelected(false);
            autoWinItem.setToolTipText(t("Disponible solo ejecutando el " + NOMBRE + ".exe empaquetado",
                    "Only available when running the packaged " + NOMBRE + ".exe"));
        } else {
            autoWinItem.setToolTipText(t("Añade la app al arranque de tu usuario de Windows (sin permisos especiales)",
                    "Adds the app to your Windows user startup (no special permissions)"));
            autoWinItem.addActionListener(e -> {
                boolean ok = fijarAutoArranque(autoWinItem.isSelected());
                if (!ok) {
                    autoWinItem.setSelected(false);
                    status.setText(t("No se pudo cambiar el autoarranque (¿antivirus?).",
                            "Could not change startup entry (antivirus?)."));
                }
                guardarConfig("autoarranque", String.valueOf(autoWinItem.isSelected()));
            });
        }

        JCheckBoxMenuItem iniMinItem = new JCheckBoxMenuItem(t("Iniciar minimizada", "Start minimized"),
                Boolean.parseBoolean(leerConfig("inicio_min", "false")));
        iniMinItem.setToolTipText(t("La ventana arranca minimizada en la barra de tareas",
                "The window starts minimized to the taskbar"));
        iniMinItem.addActionListener(e ->
                guardarConfig("inicio_min", String.valueOf(iniMinItem.isSelected())));

        autoSgItem.setToolTipText(t("Tras cada descarga, copia también la rec a la carpeta savegame del juego", "After each download, also copy the rec to the game savegame folder"));
        autoSgItem.addActionListener(e -> {
            if (autoSgItem.isSelected() && obtenerSavegame(true) == null) {
                autoSgItem.setSelected(false);
                return;
            }
            guardarConfig("autosavegame", String.valueOf(autoSgItem.isSelected()));
        });

        JMenuItem carpetaItem = new JMenuItem(t("Cambiar carpeta savegame…", "Change savegame folder…"));
        carpetaItem.addActionListener(e -> {
            Path p = elegirSavegameManual();
            if (p != null) { status.setText(t("Carpeta savegame: ", "Savegame folder: ") + p); applyFilters(); }
        });

        JMenuItem aboutItem = new JMenuItem(t("Acerca de…", "About…"));
        aboutItem.addActionListener(e -> showAbout());

        configMenu.add(idiomaMenu);
        configMenu.add(temaMenu);
        configMenu.add(letraMenu);
        configMenu.addSeparator();
        JCheckBoxMenuItem eloWatchItem = new JCheckBoxMenuItem(
                t("Mostrar ELO en la Watchlist", "Show ELO in the Watchlist"), mostrarEloWatch);
        eloWatchItem.setToolTipText(t("El ELO se actualiza solo al abrir la app, nunca al buscar, para no chivar resultados",
                "ELO refreshes only when the app opens, never on search, so results are never given away"));
        eloWatchItem.addActionListener(e -> {
            mostrarEloWatch = eloWatchItem.isSelected();
            guardarConfig("elo_watchlist", String.valueOf(mostrarEloWatch));
            playersList.repaint();
        });
        configMenu.add(buscarAbrirItem);
        configMenu.add(autoWinItem);
        configMenu.add(iniMinItem);
        configMenu.add(eloWatchItem);
        configMenu.add(autoSgItem);
        JCheckBoxMenuItem usarCaItem = new JCheckBoxMenuItem(t("Usar CaptureAge", "Use CaptureAge"),
                Boolean.parseBoolean(leerConfig("usar_ca", "false")));
        usarCaItem.setToolTipText(t("Al espectar un directo, lanza también CaptureAge (se engancha solo al juego).",
                "When spectating, also launches CaptureAge (it hooks onto the game by itself)."));
        usarCaItem.addActionListener(e -> {
            guardarConfig("usar_ca", String.valueOf(usarCaItem.isSelected()));
            if (usarCaItem.isSelected() && rutaCaptureAge() == null)
                status.setText(t("CaptureAge no aparece en la ruta estándar: usa «Cambiar ruta de CaptureAge…».",
                        "CaptureAge isn't at the standard path: use \u201CChange CaptureAge path\u2026\u201D."));
        });
        configMenu.add(usarCaItem);
        JMenuItem rutaCaItem = new JMenuItem(t("Cambiar ruta de CaptureAge…", "Change CaptureAge path…"));
        rutaCaItem.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            Path actual = rutaCaptureAge();
            if (actual != null) fc.setSelectedFile(actual.toFile());
            fc.setDialogTitle(t("Elegir CaptureAge.exe", "Pick CaptureAge.exe"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                guardarConfig("ca_ruta", fc.getSelectedFile().getAbsolutePath());
                status.setText(t("Ruta de CaptureAge guardada: ", "CaptureAge path saved: ")
                        + fc.getSelectedFile().getAbsolutePath());
            }
        });
        configMenu.add(rutaCaItem);
        JMenu vigMenu = new JMenu(t("Vigilancia de vivos", "Live watch interval"));
        ButtonGroup vg = new ButtonGroup();
        String tickActual = leerConfig("tick_min", "1");
        for (String mins : new String[]{"1", "2", "5"}) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(t("Cada ", "Every ") + mins + " min", mins.equals(tickActual));
            it.addActionListener(e -> {
                guardarConfig("tick_min", mins);
                if (vigilante != null) { vigilante.setDelay(tickMs()); vigilante.setInitialDelay(tickMs()); vigilante.restart(); }
            });
            vg.add(it);
            vigMenu.add(it);
        }
        vigMenu.setToolTipText(t("Cada cuánto se refrescan los puntos rojos (1 min por defecto; sube si el servicio anda flojo).",
                "How often live dots refresh (1 min by default; raise it if the service is struggling)."));
        configMenu.add(vigMenu);
        cargarAliases();
        cargarNotas();
        JMenu topMenu = new JMenu(t("Top ladder", "Top ladder"));
        ButtonGroup topGrp = new ButtonGroup();
        for (int n : new int[]{ 25, 50, 100 }) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem("Top " + n,
                    String.valueOf(n).equals(leerConfig("top_n", "50")));
            it.addActionListener(e -> {
                guardarConfig("top_n", String.valueOf(n));
                if (modoTop()) { topCargado = 0; cargarTopLadder(true); }
            });
            topGrp.add(it);
            topMenu.add(it);
        }
        configMenu.add(topMenu);
        configMenu.add(carpetaItem);
        configMenu.addSeparator();
        JMenuItem updItem = new JMenuItem(t("Buscar actualizaciones…", "Check for updates…"));
        updItem.addActionListener(e -> comprobarActualizacion(true));
        configMenu.add(updItem);
        configMenu.add(aboutItem);
        configBtn.addActionListener(e -> configMenu.show(configBtn, 0, configBtn.getHeight()));
        JPanel esquina = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));   // Configuración: arriba a la derecha, siempre
        JButton miPerfilBtn = new JButton(t("Mi perfil", "My profile"));
        miPerfilBtn.setFocusable(false); miPerfilBtn.putClientProperty("JButton.buttonType", "roundRect");
        miPerfilBtn.setToolTipText(t("Tu propio perfil (la primera vez te pide tu nick y lo recuerda; clic derecho para cambiar de cuenta). Con tu nick guardado, la app te avisa de tus partidas.", "Your own profile (asks your nick the first time and remembers it; right-click to change account). With your nick saved, the app notifies you about your games."));
        miPerfilBtn.addActionListener(e -> abrirMiPerfil());
        miPerfilBtn.addMouseListener(new MouseAdapter() {   // clic derecho: cambiar de cuenta
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) menu(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) menu(e); }
            void menu(MouseEvent e) {
                JPopupMenu pm = new JPopupMenu();
                JMenuItem otra = new JMenuItem(t("Cambiar de cuenta…", "Change account…")); otra.addActionListener(a -> { guardarConfig("mi_pid", ""); preguntarMiNick(); }); pm.add(otra);
                pm.show(miPerfilBtn, e.getX(), e.getY());
            }
        });
        esquina.add(miPerfilBtn);
        esquina.add(configBtn);

        JPanel fila2 = new JPanel(new BorderLayout(8, 0));
        fila2.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        nota = new JLabel(t("Sin spoilers: nunca se muestra ganador, ±ELO ni duración. Doble clic en una fila = descargar.", "Spoiler-free: winner, ±ELO and duration are never shown. Double-click a row = download."));
        resultadosBtn = new JToggleButton(t("Mostrar resultados", "Show results"));
        resultadosBtn.setFocusable(false);
        resultadosBtn.setToolTipText(t("Modo consulta: el ganador de cada partida en dorado (±ELO y duración en el tooltip). Siempre arranca apagado.",
                "Lookup mode: each game's winner in gold (±ELO and duration in the tooltip). Always starts off."));
        resultadosBtn.addActionListener(e -> {
            mostrarResultados = resultadosBtn.isSelected();
            actualizarNotaSpoilers();
            tableModel.fireTableDataChanged();
        });
        fila2.add(nota, BorderLayout.CENTER);
        fila2.add(resultadosBtn, BorderLayout.EAST);   // sobre la columna Rec, donde vive su efecto
        filaNota = fila2;

        JPanel filasIzq = new JPanel();
        filasIzq.setLayout(new BoxLayout(filasIzq, BoxLayout.Y_AXIS));
        fila1.setAlignmentX(Component.LEFT_ALIGNMENT); filaVistas.setAlignmentX(Component.LEFT_ALIGNMENT);
        filasIzq.add(fila1);
        filasIzq.add(filaVistas);
        JPanel barraSuperior = new JPanel(new BorderLayout());
        barraSuperior.add(filasIzq, BorderLayout.CENTER);
        JPanel esquinaArriba = new JPanel(new BorderLayout());
        esquinaArriba.add(esquina, BorderLayout.NORTH);
        barraSuperior.add(esquinaArriba, BorderLayout.EAST);
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        barraSuperior.setAlignmentX(Component.LEFT_ALIGNMENT); fila2.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(barraSuperior);
        top.add(fila2);
        setMinimumSize(new Dimension(1100, 640));

        // Tabla de partidas
        table = new JTable(tableModel) {
            @Override public String getToolTipText(MouseEvent ev) {
                int r = rowAtPoint(ev.getPoint());
                if (r < 0) return null;
                int mr = convertRowIndexToModel(r);
                if (mr < 0 || mr >= view.size()) return null;
                Match m = view.get(mr);
                int mc = convertColumnIndexToModel(columnAtPoint(ev.getPoint()));
                if (mc == 1 && notaDe(m.refId) != null) return t("Nota: ", "Note: ") + notaDe(m.refId);
                if (mc == 5 && m.players.size() == 2)
                    for (MatchPlayer mp : m.players)
                        if (mp.id != m.refId && notaDe(mp.id) != null) return t("Nota: ", "Note: ") + notaDe(mp.id);
                if (enCursoReal(m))
                    return t("EN DIRECTO — doble clic para espectar", "LIVE — double-click to spectate");
                String enf = enfrentamiento(m, revelada(m));
                if (revelada(m)) {   // revelada: el enfrentamiento con resultados, y la duración
                    String tr = tipResultado(m);
                    if (tr != null) return "<html>" + (enf.startsWith("<html>") ? enf.substring(6, enf.length() - 7) : escapeHtml(enf))
                            + "<br>" + escapeHtml(tr) + "</html>";
                }
                return enf;
            }
            @Override public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                int mr = convertRowIndexToModel(row);
                boolean viva = mr >= 0 && mr < view.size() && enCursoReal(view.get(mr));
                // Los renderers son compartidos: color y fuente se fijan SIEMPRE,
                // en ambas ramas, para que la fila viva no contamine al resto.
                c.setFont(viva ? getFont().deriveFont(Font.BOLD) : getFont());
                if (!isRowSelected(row))
                    c.setForeground(viva ? colorVivoTabla() : getForeground());
                if (c instanceof JLabel jl && convertColumnIndexToModel(column) == 1
                        && mr >= 0 && mr < view.size())   // cuenta hermana: quién es su matriz
                    jl.setToolTipText(tipCuentaVinculada(view.get(mr)));
                return c;
            }
        };
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);   // clic en cabecera para ordenar
        table.getColumnModel().getColumn(0).setPreferredWidth(90);    // Fecha
        table.getColumnModel().getColumn(1).setPreferredWidth(130);   // Jugador
        table.getColumnModel().getColumn(2).setPreferredWidth(95);    // Civ
        table.getColumnModel().getColumn(3).setPreferredWidth(110);   // Modo
        table.getColumnModel().getColumn(4).setPreferredWidth(110);   // Mapa
        table.getColumnModel().getColumn(5).setPreferredWidth(230);   // Rival
        table.getColumnModel().getColumn(6).setPreferredWidth(95);    // Civ rival
        table.getColumnModel().getColumn(7).setPreferredWidth(100);   // Rec
        table.getColumnModel().getColumn(8).setPreferredWidth(80);    // Resultado (ojo por fila)
        {   // anchos guardados (en orden del modelo)
            String[] anchos = leerConfig("tabla_anchos", "").split(",");
            if (anchos.length == table.getColumnCount()) for (int i = 0; i < anchos.length; i++) { try { int a = Integer.parseInt(anchos[i].trim()); if (a >= 20) table.getColumnModel().getColumn(i).setPreferredWidth(a); } catch (NumberFormatException ignored) { } }
        }
        DefaultTableCellRenderer ojoR = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foc, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, sel, foc, row, col);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                boolean abierto = "\u25C9".equals(String.valueOf(v));
                l.setFont(l.getFont().deriveFont(abierto ? Font.BOLD : Font.PLAIN, 14f));
                if (!sel) l.setForeground(abierto ? (temaOscuroActivo ? new Color(0xd9, 0xa5, 0x5b) : new Color(0x9a, 0x6b, 0x1f))
                                                  : new Color(0x8a, 0x8a, 0x8a));
                l.setToolTipText(v == null || String.valueOf(v).isBlank() ? null
                        : (abierto ? t("Clic: volver a tapar el resultado", "Click: hide the result again")
                                   : t("Clic: revelar el resultado de esta partida", "Click: reveal this game's result")));
                return l;
            }
        };
        table.getColumnModel().getColumn(8).setCellRenderer(ojoR);
        aplicarOrdenColumnas(leerConfig("tabla_orden", ORDEN_COLUMNAS_DEFECTO));
        javax.swing.Timer guardaCols = new javax.swing.Timer(800, ev -> guardarColumnas());
        guardaCols.setRepeats(false);
        table.getColumnModel().addColumnModelListener(new javax.swing.event.TableColumnModelListener() {   // orden y anchos se recuerdan
            @Override public void columnMoved(javax.swing.event.TableColumnModelEvent e) { if (e.getFromIndex() != e.getToIndex()) guardaCols.restart(); }
            @Override public void columnMarginChanged(javax.swing.event.ChangeEvent e) { guardaCols.restart(); }
            @Override public void columnAdded(javax.swing.event.TableColumnModelEvent e) { }
            @Override public void columnRemoved(javax.swing.event.TableColumnModelEvent e) { }
            @Override public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) { }
        });
        DefaultTableCellRenderer centrado = new DefaultTableCellRenderer();
        centrado.setHorizontalAlignment(SwingConstants.CENTER);
        for (int ci : new int[]{ 0, 2, 3, 4, 6 }) table.getColumnModel().getColumn(ci).setCellRenderer(centrado);   // Fecha, Civ, Modo, Mapa, Civ rival
        table.getTableHeader().addMouseListener(new MouseAdapter() {   // doble clic en el borde de una columna = ajustar al contenido
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                int x = 0;
                for (int c = 0; c < table.getColumnCount(); c++) {
                    x += table.getColumnModel().getColumn(c).getWidth();
                    if (Math.abs(e.getX() - x) <= 4) { ajustarColumna(c); return; }
                }
            }
        });
        // Atajos: Enter en la tabla descarga la selección; Ctrl+F va al buscador; F5 refresca los directos
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sfrDescargar");
        table.getActionMap().put("sfrDescargar", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (dlSel.isEnabled() && !selectedRows().isEmpty()) download(selectedRows());
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK), "sfrBuscar");
        getRootPane().getActionMap().put("sfrBuscar", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (buscaNick != null) { buscaNick.requestFocusInWindow(); buscaNick.selectAll(); }
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "sfrRefrescar");
        getRootPane().getActionMap().put("sfrRefrescar", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                ultimoTwitchMs = 0;
                vigilarTwitch();
                if (directosBtn != null && directosBtn.isSelected()) status.setText(t("Refrescando directos…", "Refreshing streams…"));
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybePopupTabla(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopupTabla(e); }
            void maybePopupTabla(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int r = table.rowAtPoint(e.getPoint());
                if (r < 0) return;
                if (!table.isRowSelected(r)) table.setRowSelectionInterval(r, r);
                int mr = table.convertRowIndexToModel(r);
                if (mr < 0 || mr >= view.size()) return;
                Match m = view.get(mr);
                JPopupMenu menu = new JPopupMenu();
                if (m.gte == 0) {   // en Guess the ELO no: el perfil chivaría el ELO
                    List<MatchPlayer> conId = new ArrayList<>();
                    for (MatchPlayer mp : m.players) if (mp.id > 0) conId.add(mp);
                    if (!conId.isEmpty()) {
                        MatchPlayer titular = null; for (MatchPlayer mp : conId) if (mp.id == m.refId) titular = mp;
                        if (conId.size() <= 2)
                            for (MatchPlayer mp : conId) { Integer e1 = elo1v1Conocido(mp.id); if (e1 == null) e1 = mp.rating; JMenu mj = menuDeJugador(mp.id, mp.name + (e1 != null ? "  " + e1 : "")); mj.setIcon(iconoBandera(paisDe(mp.id))); menu.add(mj); }
                        else if (titular != null) {   // equipos: aliados y rivales del titular, como en la watchlist
                            for (MatchPlayer mp : conId) if (mp.id == titular.id) menu.add(menuDeJugador(mp.id, mp.name));
                            JMenu al = new JMenu(t("Aliados", "Allies")), ri = new JMenu(t("Rivales", "Opponents"));
                            for (MatchPlayer mp : conId) { if (mp.id == titular.id) continue; String pos = posicionEnEquipo(m, mp); JMenu dst = mp.team == titular.team ? al : ri; Integer e1 = elo1v1Conocido(mp.id); JMenu sub = menuDeJugador(mp.id, mp.name + (e1 != null ? "  " + e1 : "") + (pos == null ? "" : "  \u00B7 " + posicionNombre(pos))); sub.setIcon(iconoBandera(paisDe(mp.id))); dst.add(sub); }
                            if (al.getItemCount() > 0) menu.add(al); if (ri.getItemCount() > 0) menu.add(ri);
                        } else {
                            JMenu js = new JMenu(t("Jugadores de la partida", "Match players"));
                            for (MatchPlayer mp : conId) js.add(menuDeJugador(mp.id, mp.name));
                            menu.add(js);
                        }
                        menu.addSeparator();
                    }
                }
                if (enCursoReal(m)) {
                    JMenuItem esp = new JMenuItem(t("Espectar en directo", "Spectate live"));
                    esp.addActionListener(a -> espectarVerificando(m.players.isEmpty() ? 0 : m.players.get(0).id, m.id));
                    menu.add(esp);
                    if (rutaCaptureAge() != null) {
                        JMenuItem espCa = new JMenuItem(t("Espectar con CaptureAge", "Spectate with CaptureAge"));
                        espCa.addActionListener(a -> {
                            lanzarCaptureAge(null);   // acción explícita: no depende de la casilla
                            espectarVerificando(m.players.isEmpty() ? 0 : m.players.get(0).id, m.id);
                        });
                        menu.add(espCa);
                    }
                } else {
                    if (m.enDisco) {
                        JMenuItem env = new JMenuItem(t("Enviar al juego", "Send to game"));
                        env.addActionListener(a -> enviarASavegame(List.of(m)));
                        menu.add(env);
                    } else {
                        JMenuItem dl = new JMenuItem(t("Descargar", "Download"));
                        dl.addActionListener(a -> download(List.of(m)));
                        menu.add(dl);
                    }
                    menu.addSeparator();
                    JMenuItem rev = new JMenuItem(t("Revelar resultado\u2026", "Reveal result\u2026"));
                    rev.addActionListener(a -> revelarResultado());
                    menu.add(rev);
                    if (m.gte == 0) {   // el tech tree de cada civ de la partida, a un clic
                        java.util.LinkedHashSet<String> civsP = new java.util.LinkedHashSet<>();
                        for (MatchPlayer mp : m.players) if (mp.civ != null && !mp.civ.isBlank()) civsP.add(mp.civ);
                        if (civsP.size() <= 2) {
                            for (String cv : civsP) { JMenuItem it = new JMenuItem("Tech tree: " + cv); it.addActionListener(a -> abrirTechTree(cv)); menu.add(it); }
                        } else {
                            JMenu sub = new JMenu("Tech tree");
                            for (String cv : civsP) { JMenuItem it = new JMenuItem(cv); it.addActionListener(a -> abrirTechTree(cv)); sub.add(it); }
                            menu.add(sub);
                        }
                    }
                    if (m.gte == 0) {
                        JMenuItem ana = new JMenuItem(t("Análisis de la partida (¡spoilers!)\u2026",
                                "Match analysis (spoilers!)\u2026"));
                        ana.addActionListener(a -> {
                            int ok = JOptionPane.showConfirmDialog(SpoilerFreeRecs.this,
                                    t("Se abrirá el análisis completo en aoe2insights: resultado, estrategias y minimapa.\n¿Seguro?",
                                      "This opens the full analysis on aoe2insights: result, strategies and minimap.\nSure?"),
                                    t("Análisis con spoilers", "Analysis with spoilers"),
                                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                            if (ok == JOptionPane.YES_OPTION)
                                abrirUrl("https://www.aoe2insights.com/match/" + m.id + "/");
                        });
                        menu.add(ana);
                    }
                }
                menu.show(table, e.getX(), e.getY());
            }
            @Override public void mouseClicked(MouseEvent e) {
                int r0 = table.rowAtPoint(e.getPoint()), c0 = table.columnAtPoint(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e) && r0 >= 0 && c0 >= 0 && table.convertColumnIndexToModel(c0) == 8) {
                    if (e.getClickCount() != 1) return;   // el doble clic sobre el ojo no descarga
                    int mr0 = table.convertRowIndexToModel(r0);
                    if (mr0 < 0 || mr0 >= view.size()) return;
                    Match m0 = view.get(mr0);
                    if (m0.finished == null) return;
                    if (!reveladas.remove(m0.id)) reveladas.add(m0.id);
                    tableModel.fireTableRowsUpdated(mr0, mr0);
                    return;
                }
                if (e.getClickCount() != 2 || !dlSel.isEnabled() || e.isControlDown() || e.isShiftDown()) return;
                int r = table.rowAtPoint(e.getPoint());
                if (r < 0) return;
                int mr = table.convertRowIndexToModel(r);
                if (mr < 0 || mr >= view.size()) return;
                Match m = view.get(mr);
                if (enCursoReal(m)) {
                    if (confirmarEspectar(refNombre(m))) espectarVerificando(m.players.isEmpty() ? 0 : m.players.get(0).id, m.id);
                    return;
                }
                else if (m.finished == null)
                    status.setText(t("Esa partida quedó colgada en el servidor (crash): no hay rec que bajar.",
                            "That game hung on the server (crash): there's no rec to download."));
                else download(List.of(m));
            }
        });

        // Botones inferiores + savegame + firma + donación
        JButton abrir  = new JButton(t("Abrir carpeta recs descargadas", "Open downloaded recs folder"));
        JButton vaciar = new JButton(t("Vaciar recs", "Empty recs folder"));
        dlSel.addActionListener(e -> download(selectedRows()));
        dlAll.addActionListener(e -> download(allRows()));
        abrir.addActionListener(e -> abrirCarpeta());
        vaciar.addActionListener(e -> vaciarRecs());
        JPanel btns1 = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        JButton carpetasBtn = new JButton(t("Carpetas \u25BE", "Folders \u25BE"));
        carpetasBtn.setFocusable(false);
        carpetasBtn.addActionListener(e -> {
            JPopupMenu pm = new JPopupMenu();
            JMenuItem i1 = new JMenuItem(abrir.getText());  i1.addActionListener(a -> abrir.doClick());
            JMenuItem i2 = new JMenuItem(abrirSgTxt());     i2.addActionListener(a -> abrirSavegame());
            JMenuItem i3 = new JMenuItem(vaciar.getText()); i3.addActionListener(a -> vaciar.doClick());
            pm.add(i1); pm.add(i2); pm.addSeparator(); pm.add(i3);
            pm.show(carpetasBtn, 0, carpetasBtn.getHeight());
        });
        btns1.add(dlSel); btns1.add(dlAll);

        JButton enviarSg = new JButton(t("Enviar al juego", "Send to game"));
        enviarSg.setToolTipText(t("Envía las recs seleccionadas al juego: las ya descargadas se copian; las que falten se descargan y se envían en la misma acción", "Sends the selected recs to the game: downloaded ones are copied; missing ones are downloaded and sent in one go"));
        enviarSg.addActionListener(e -> enviarInteligente(selectedRows()));
        JPanel btns2 = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        btns1.add(enviarSg);       // acciones sobre partidas, en la primera fila
        todasPerfilBtn = new JButton(t("Todas las partidas del perfil", "All games of the profile"));
        todasPerfilBtn.setFocusable(false); todasPerfilBtn.putClientProperty("JButton.buttonType", "roundRect");
        todasPerfilBtn.setToolTipText(t("Abre el perfil del jugador buscado con su histórico completo en páginas (del último año, sin llamadas si está en sfr-data)", "Opens the searched player's profile with their full history in pages (last year, no requests when in sfr-data)"));
        todasPerfilBtn.addActionListener(e -> { if (ultimosSujetos.size() == 1) { Player p = ultimosSujetos.get(0); abrirPerfil(p.id(), nombreVisible(p.id(), p.name())); javax.swing.Timer tt = new javax.swing.Timer(900, ev -> { if (actPid == p.id()) mostrarHistorialPerfil(p.id(), nombreVisible(p.id(), p.name())); }); tt.setRepeats(false); tt.start(); } });
        todasPerfilBtn.setVisible(false);
        btns1.add(todasPerfilBtn);
        btns2.add(carpetasBtn);    // carpetas, en la segunda, bajo un solo botón

        JPanel filasBtns = new JPanel();
        filasBtns.setLayout(new BoxLayout(filasBtns, BoxLayout.Y_AXIS));
        filasBtns.add(btns1);
        filasBtns.add(btns2);
        filaBotonesInferiores = filasBtns;

        firma = new JLabel("<html>" + AUTOR + " · <u>" + TWITCH + "</u></html>");
        firma.setFont(firma.getFont().deriveFont(Font.PLAIN, 11f));
        firma.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        firma.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        firma.setToolTipText("Abrir https://" + TWITCH);
        firma.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { abrirTwitch(); }
        });
        cafeBtn.setToolTipText(DONAR_URL);
        cafeBtn.addActionListener(e -> abrirDonacion());
        JPanel este = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actualizarBtn = new JButton();
        actualizarBtn.setVisible(false);
        actualizarBtn.setFocusable(false);
        actualizarBtn.setToolTipText(t("Abre la página de descarga de la versión nueva", "Opens the new version's download page"));
        actualizarBtn.addActionListener(e -> abrirUrl(RELEASES_URL));
        este.add(actualizarBtn); este.add(cafeBtn); este.add(firma);

        progreso.setIndeterminate(true);
        progreso.setVisible(false);
        progreso.setPreferredSize(new Dimension(120, 14));
        JPanel oeste = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        oeste.add(progreso);
        detenerDescBtn = new JButton(t("Detener", "Stop"));
        detenerDescBtn.setVisible(false);
        detenerDescBtn.setToolTipText(t("Detiene la operación en curso: descargas, azar o Guess the ELO (cada petición muere sola a los 25 s).",
                "Stops the running operation: downloads, random or Guess the ELO (each request self-terminates at 25 s)."));
        detenerDescBtn.addActionListener(e -> {
            log("detener pulsado (op #" + opSerial + ")");
            stopOperacion = true;
            detenerDescBtn.setEnabled(false);
            status.setText(t("Deteniendo… (como mucho 15 s si había una petición en vuelo)",
                    "Stopping… (at most 15 s if a request was in flight)"));
            HTTP = nuevoHttp();   // las peticiones en vuelo caducan solas (≤15 s); las siguientes salen limpias
            final long serialDetenido = opSerial;
            javax.swing.Timer vig = new javax.swing.Timer(5000, ev -> {
                if (progreso.isVisible() && opSerial == serialDetenido) {   // solo si es LA MISMA operación
                    trabajando(false);
                    status.setText(t("Detenido.", "Stopped."));
                }
            });
            vig.setRepeats(false);
            vig.start();
        });
        oeste.add(detenerDescBtn);
        continuarBtn = new JButton(t("Continuar buscando", "Keep searching"));
        continuarBtn.setVisible(false);
        continuarBtn.setToolTipText(t("Reanuda el azar con los mismos filtros, sin re-diálogo: el muestreo recuerda lo ya leído.",
                "Resumes the random search with the same filters, no dialog: sampling remembers what it already read."));
        continuarBtn.addActionListener(e -> buscarAleatorias(true));
        oeste.add(continuarBtn);
        JPanel filaEstado = new JPanel(new BorderLayout());
        filaEstado.add(oeste, BorderLayout.WEST);
        filaEstado.add(status, BorderLayout.CENTER);
        filaEstado.add(este, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(filasBtns, BorderLayout.NORTH);
        bottom.add(filaEstado, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(top, BorderLayout.NORTH);
        centroCards = new JPanel(new CardLayout());
        recsCards = new JPanel(new CardLayout());
        recsCards.add(new JScrollPane(table), "tabla");
        JPanel guia = new JPanel(new GridBagLayout());
        JLabel guiaTxt = new JLabel("<html><div style='text-align:center'>"
                + "<b>" + t("Así funciona", "How it works") + "</b><br><br>"
                + t("1. Elige jugadores en la lista de la izquierda, o escribe un nick en el buscador.", "1. Pick players in the list on the left, or type a nick in the search box.") + "<br>"
                + t("2. Pulsa <b>Buscar partidas</b>.", "2. Press <b>Search games</b>.") + "<br>"
                + t("3. Doble clic en una partida para descargarla — sin spoilers.", "3. Double-click a game to download it — spoiler-free.")
                + "</div></html>");
        guiaTxt.setHorizontalAlignment(SwingConstants.CENTER);
        guiaBtn = new JButton(t("Buscar partidas", "Search games"));
        guiaBtn.addActionListener(e -> fetchBtn.doClick());
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0; gc.insets = new Insets(0, 0, 14, 0);
        guia.add(guiaTxt, gc);
        gc.gridy = 1; gc.insets = new Insets(0, 0, 0, 0);
        guia.add(guiaBtn, gc);
        recsCards.add(guia, "guia");
        centroCards.add(recsCards, "recs");
        mostrarGuiaVacia(true);   // sin partidas todavía: la guía (la vista inicial sigue siendo Directos)
        centroCards.add(construirPanelDirectos(), "directos");
        centroCards.add(construirPanelAhora(), "ahora");
        centroCards.add(construirPanelTechTree(), "techtree");
        ToolTipManager.sharedInstance().setInitialDelay(350);
        ToolTipManager.sharedInstance().setDismissDelay(90_000);   // el tooltip aguanta mientras el ratón esté quieto
        centroCards.add(construirPanelLadder(), "ladder");
        centroCards.add(construirPanelCivStats(), "civstats");
        centroCards.add(construirPanelPerfil(), "perfil");
        center.add(centroCards, BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, center);
        split.setContinuousLayout(true);
        split.setDividerSize(7);
        split.setResizeWeight(0);   // al agrandar la ventana crece la tabla
        left.setMinimumSize(new Dimension(230, 100));
        center.setMinimumSize(new Dimension(420, 100));
        try { split.setDividerLocation(Integer.parseInt(leerConfig("divisor", "355"))); }
        catch (Exception ignored) { split.setDividerLocation(355); }
        if (split.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI bui)
            bui.getDivider().addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) split.setDividerLocation(355);   // vuelta al ancho por defecto
                }
            });
        splitPrincipal = split;
        // Como el Explorador, en toda la ventana: clic en cualquier fondo que no sea un control = sin selección.
        // Excepciones: la cabecera de columnas (ordena) y la cabecera «Partidas de:» (sus nombres son clicables).
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (!(ev instanceof MouseEvent me) || me.getID() != MouseEvent.MOUSE_PRESSED) return;
            if (!SwingUtilities.isLeftMouseButton(me) || me.isControlDown() || me.isShiftDown()) return;
            Component c = me.getComponent();
            if (c == null) return;
            Window w = c instanceof Window win ? win : SwingUtilities.getWindowAncestor(c);
            if (w != SpoilerFreeRecs.this) return;   // solo la ventana principal (la hover-card y los diálogos, no)
            if (ttDetalleDialog != null && ttDetalleDialog.isVisible()) ttDetalleDialog.setVisible(false);   // la ficha flotante se cierra al clicar fuera
            // Un fondo sin listeners no recibe el clic (sube hasta la ventana): miramos qué hay REALMENTE bajo el ratón
            Point p = SwingUtilities.convertPoint(c, me.getPoint(), getLayeredPane());
            Component bajo = SwingUtilities.getDeepestComponentAt(getLayeredPane(), p.x, p.y);
            if (bajo == null || !esFondoDeseleccionable(bajo)) return;
            if (!playersList.isSelectionEmpty()) { playersList.clearSelection(); actualizarTextoBuscar(); }
        }, AWTEvent.MOUSE_EVENT_MASK);
        add(split, BorderLayout.CENTER);

        cargarCanales();
        loadPlayers();
        sanearVinculosHuerfanos();
        getRootPane().setDefaultButton(fetchBtn);   // acción primaria: acento y Enter
        ajustarGrises(flatLafDisponible && temaOscuroActivo);
        ajustarBotonesEspeciales(flatLafDisponible && temaOscuroActivo);
        ajustarFuentesSecundarias();
        table.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke("ENTER"), "descargarSeleccion");
        table.getActionMap().put("descargarSeleccion", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { download(selectedRows()); }
        });
        refrescarWatchlist();
        SwingUtilities.invokeLater(() -> {
            grupoCombo.setSelectedItem(TOP_LADDER);   // la app abre en ★
            mostrarDirectos(true);                    // …con los Directos a la vista, no una tabla vacía
            if (Boolean.parseBoolean(leerConfig("inicio_min", "false")))
                setExtendedState(JFrame.ICONIFIED);
        });
        final boolean autoOn = Boolean.parseBoolean(leerConfig("autoarranque", "false"));
        new Thread(() -> fijarAutoArranque(autoOn)).start();   // reconcilia SIEMPRE: escribe si sí, borra si no
        socketPing = new javax.swing.Timer(30_000, e -> {
            java.net.http.WebSocket s = socket;
            if (s != null && socketConectado) { try { s.sendPing(java.nio.ByteBuffer.wrap(new byte[]{ 1 })); } catch (Exception ignored) { } }
        });
        socketPing.start();
        javax.swing.Timer tUpd = new javax.swing.Timer(8000, e -> {   // una vez, tras arrancar
            comprobarActualizacion(false); ttPrecargar();
            cargarPaises(); instalarAutoScroll();
            new javax.swing.Timer(60_000, ev -> guardarPaises()).start();
            refrescarCampanas();
            campanasTimer = new javax.swing.Timer(15 * 60_000, ev -> refrescarCampanas()); campanasTimer.start();
            new javax.swing.Timer(1000, ev -> { if (!vivoWatch.isEmpty() && playersList.isShowing()) playersList.repaint(); }).start();   // el reloj del subtexto «en partida» corre   // las listas de las campanas (tops, país, clan) se repasan cada 15 min
            Thread lh = new Thread(() -> ladderAsegurar(false), "ladder-precarga"); lh.setDaemon(true); lh.start();   // el volcado del ladder, en silencio
        });
        tUpd.setRepeats(false);
        tUpd.start();
        vigilante = new javax.swing.Timer(tickMs(), e -> {
            // Con el socket conectado, quién está en partida llega al instante: los sondeos pasan a ser una resincronización
            // cada 10 min (× el multiplicador del mando a distancia). Si el socket cae, vuelven al ritmo del tick.
            long ahora = System.currentTimeMillis();
            long resync = (long) (10 * 60_000L * ctrlMult("tick_mult"));
            boolean tocaSondear = ctrlOn("sondeo") && (!socketConectado || ahora - ultimoResyncMs >= resync);
            if (tocaSondear) ultimoResyncMs = ahora;
            if (tocaSondear) vigilarVivos();
            if (!modoTop()) vigilarTwitch();   // en ★ lo dispara el propio río al terminar (vigilarTwitch tiene su propio ritmo)
            if (modoTop() && !modoClan()) {
                // recuperación automática: si el top no pudo cargarse (o solo hay caché), reintenta
                if (topLadder.isEmpty() || ahora - topCargado > 15 * 60_000L)
                    cargarTopLadder(true);
                else if (tocaSondear) vigilarTop();
            } else if (modoClan() && tocaSondear) vigilarTop();   // el clan se vigila, pero nunca se sustituye por el top del ladder
        });
        new Thread(() -> { cargarControl(); cargarEloAyer(); }, "control").start();
        if (!leerConfig("mi_pid", "").isBlank()) iniciarVigilanciaLogJuego();   // «Mi partida»: aviso temprano al encontrar partida

        new javax.swing.Timer(3_600_000, e -> new Thread(SpoilerFreeRecs::cargarControl, "control").start()).start();
        vigilante.setInitialDelay(tickMs());
        vigilante.start();
        if (Boolean.parseBoolean(leerConfig("buscar_al_abrir", "true")))
            SwingUtilities.invokeLater(() -> { if (!modoTop() && playersModel.size() > 0) fetchMatches(fetchBtn); });
    }

    // ----- Carpeta de recs ---------------------------------------------------
    void abrirCarpeta() {
        try {
            Files.createDirectories(RECS_DIR);
            Desktop.getDesktop().open(RECS_DIR.toFile());
        } catch (Exception ex) {
            status.setText(t("No se pudo abrir la carpeta: ", "Couldn't open the folder: ") + causa(ex));
        }
    }

    void vaciarRecs() {
        List<Path> files = new ArrayList<>();
        try {
            if (Files.isDirectory(RECS_DIR))
                try (var st = Files.list(RECS_DIR)) {
                    st.filter(f -> f.getFileName().toString().endsWith(".aoe2record")).forEach(files::add);
                }
        } catch (IOException ex) {
            status.setText(t("Error leyendo la carpeta: ", "Error reading the folder: ") + causa(ex));
            return;
        }
        if (files.isEmpty()) { status.setText(t("La carpeta recs ya está vacía.", "The recs folder is already empty.")); return; }
        int r = JOptionPane.showConfirmDialog(this,
                t("Se borrarán ", "This will delete ") + files.size()
                        + t(" recs de la carpeta recs.\n¿Continuar?", " recs from the recs folder.\nContinue?"),
                t("Vaciar recs", "Empty recs"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        int ok = 0;
        for (Path f : files) { try { Files.delete(f); ok++; } catch (IOException ignored) {} }
        for (Match m : all) { m.enDisco = false; if (m.estado.startsWith("✓")) m.estado = ""; }
        tableModel.fireTableDataChanged();
        status.setText(ok + " recs borradas.");
    }

    // ----- Savegame del juego y donación -------------------------------------
    /** Carpeta savegame activa: la de config si sigue existiendo; si no, la
     *  detectada (única = se guarda sola). En modo interactivo pregunta cuando
     *  hay varias o ninguna; sin interactivo devuelve null sin molestar. */
    Path obtenerSavegame(boolean interactivo) {
        String cfg = leerConfig("savegame", null);
        if (cfg != null && Files.isDirectory(Path.of(cfg))) return Path.of(cfg);
        List<Path> dets = detectarSavegames();
        if (dets.size() == 1) {
            guardarConfig("savegame", dets.get(0).toString());
            return dets.get(0);
        }
        if (!interactivo) return null;
        if (dets.size() > 1) {
            Object sel = JOptionPane.showInputDialog(this,
                    t("Hay varios perfiles del juego. Elige tu carpeta savegame:",
                      "There are several game profiles. Pick your savegame folder:"),
                    t("Carpeta savegame", "Savegame folder"), JOptionPane.PLAIN_MESSAGE, null, dets.toArray(), dets.get(0));
            if (sel == null) return null;
            guardarConfig("savegame", sel.toString());
            return (Path) sel;
        }
        JOptionPane.showMessageDialog(this,
                t("No encuentro la carpeta savegame del juego.\nElígela a mano:\n…\\Games\\Age of Empires 2 DE\\<perfil>\\savegame",
                  "Couldn't find the game's savegame folder.\nPick it manually:\n…\\Games\\Age of Empires 2 DE\\<profile>\\savegame"));
        return elegirSavegameManual();
    }

    Path elegirSavegameManual() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(t("Elige la carpeta savegame de AoE2 DE", "Pick the AoE2 DE savegame folder"));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String actual = leerConfig("savegame", null);
        Path base = actual != null ? Path.of(actual)
                : Path.of(System.getProperty("user.home", "."), "Games", "Age of Empires 2 DE");
        if (Files.isDirectory(base)) fc.setCurrentDirectory(base.toFile());
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        Path p = fc.getSelectedFile().toPath();
        guardarConfig("savegame", p.toString());
        return p;
    }

    /** Enviar al juego: copia lo descargado y descarga+envía lo que falte. */
    void enviarInteligente(List<Match> objetivo) {
        if (objetivo.isEmpty()) { status.setText(t("No hay partidas seleccionadas.", "No games selected.")); return; }
        List<Match> enDisco = new ArrayList<>(), faltan = new ArrayList<>();
        for (Match m : objetivo) (Files.exists(destino(m)) ? enDisco : faltan).add(m);
        if (!enDisco.isEmpty()) enviarASavegame(enDisco);
        if (!faltan.isEmpty()) download(faltan, true);
    }

    void enviarASavegame(List<Match> objetivo) {
        if (objetivo.isEmpty()) { status.setText(t("No hay partidas seleccionadas.", "No games selected.")); return; }
        Path sg = obtenerSavegame(true);
        if (sg == null) { status.setText(t("Sin carpeta savegame configurada.", "No savegame folder configured.")); return; }
        int ok = 0, sinRec = 0, yaEstaban = 0;
        for (Match m : objetivo) {
            if (!Files.exists(destino(m))) { sinRec++; continue; }
            boolean ya = Files.exists(sg.resolve(destino(m).getFileName().toString()));
            if (copiarASavegame(m, sg)) {
                ok++;
                if (ya) yaEstaban++;
                try {   // primera de la lista del juego: fecha renovada siempre
                    Files.setLastModifiedTime(sg.resolve(destino(m).getFileName().toString()),
                            java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
                } catch (Exception ignored) {}
                m.enJuego = true;
                setEstado(m, t("✓✓ en juego", "✓✓ in game"));
            }
        }
        status.setText(ok + t(" recs enviadas al juego", " recs sent to the game") +
                (yaEstaban > 0 ? " (" + yaEstaban + t(" ya estaban, actualizadas)", " were already there, refreshed)") : "") +
                (sinRec > 0 ? " · " + sinRec + t(" sin descargar aún", " not downloaded yet") : "") + ".");
    }

    String abrirSgTxt() { return t("Abrir carpeta savegame del juego", "Open game savegame folder"); }

    void abrirSavegame() {
        Path sg = obtenerSavegame(true);
        if (sg == null) { status.setText(t("Sin carpeta savegame configurada.", "No savegame folder configured.")); return; }
        try { Desktop.getDesktop().open(sg.toFile()); }
        catch (Exception ex) { status.setText(t("No se pudo abrir la carpeta: ", "Couldn't open the folder: ") + causa(ex)); }
    }

    // ----- Revelar resultado (único punto que enseña spoilers, bajo demanda) --
    void revelarResultado() {
        List<Match> sel = selectedRows();
        if (sel.size() != 1) { status.setText(t("Selecciona una sola partida para revelar.", "Select a single game to reveal.")); return; }
        Match m = sel.get(0);
        if (m.finished == null) {
            status.setText(enCursoReal(m)
                    ? t("Esa partida sigue EN DIRECTO: no hay resultado que revelar todavía.",
                        "That game is still LIVE: no result to reveal yet.")
                    : t("Esa partida quedó colgada en el servidor (crash): no tiene resultado.",
                        "That game hung on the server (crash): it has no result."));
            return;
        }
        int r = JOptionPane.showConfirmDialog(this,
                t("Vas a ver el resultado de esta partida (ganador, ±ELO y duración).\n¿Seguro?",
                  "You are about to see this game's result (winner, ±ELO and duration).\nSure?"),
                t("Revelar resultado", "Reveal result"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        if (m.gte > 0) {   // en GTE el análisis externo chivaría más de la cuenta
            JOptionPane.showMessageDialog(this, textoResultado(m), t("Resultado", "Result"), JOptionPane.PLAIN_MESSAGE);
            return;
        }
        String verAn = t("Ver análisis en aoe2insights\u2026", "View analysis on aoe2insights\u2026");
        String cerrar = t("Cerrar", "Close");
        int ra = JOptionPane.showOptionDialog(this, textoResultado(m), t("Resultado", "Result"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new Object[]{ cerrar, verAn }, cerrar);
        if (ra == 1) abrirUrl("https://www.aoe2insights.com/match/" + m.id + "/");
    }

    /** Tooltip del modo consulta: la duración (el ±ELO ya va inline). */
    static String tipResultado(Match m) {
        if (m.finished == null || m.started == null) return null;
        return t("Duraci\u00f3n: ", "Duration: ")
                + Duration.between(m.started, m.finished).toMinutes() + " min";
    }

    static String textoResultado(Match m) {
        StringBuilder sb = new StringBuilder();
        Map<Integer, List<MatchPlayer>> porEquipo = new TreeMap<>();
        for (MatchPlayer p : m.players)
            porEquipo.computeIfAbsent(p.team, k -> new ArrayList<>()).add(p);
        boolean hayGanador = false;
        for (var e : porEquipo.entrySet()) {
            boolean gana = false;
            for (MatchPlayer p : e.getValue()) if (Boolean.TRUE.equals(p.won)) gana = true;
            if (gana) hayGanador = true;
            sb.append("Equipo ").append(e.getKey()).append(gana ? "  —  GANA" : "").append('\n');
            for (MatchPlayer p : e.getValue()) {
                sb.append("    ").append(p.name);
                if (p.civ != null && !p.civ.isBlank()) sb.append(" (").append(p.civ).append(')');
                if (p.rating != null) sb.append("   ").append(p.rating);
                if (p.ratingDiff != null)
                    sb.append(" (").append(p.ratingDiff >= 0 ? "+" : "").append(p.ratingDiff).append(')');
                sb.append('\n');
            }
        }
        if (!hayGanador) sb.append(t("\nResultado no disponible en la API para esta partida.\n",
                "\nResult not available in the API for this game.\n"));
        if (m.started != null && m.finished != null) {
            long min = Duration.between(m.started, m.finished).toMinutes();
            sb.append(t("\nDuración: ", "\nDuration: "))
              .append(min / 60 > 0 ? (min / 60) + " h " : "").append(min % 60).append(" min");
        }
        return sb.toString();
    }

    // ----- Partidas 1v1 al azar por rango de ELO (para castear) --------------
    void buscarAleatorias() { buscarAleatorias(false); }

    void buscarAleatorias(boolean continuar) {
        if (!continuar) {
        JSpinner minSp = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(leerConfig("elo_min", "2100")), 0, 4000, 50));
        JSpinner maxSp = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(leerConfig("elo_max", "2300")), 0, 4000, 50));
        JSpinner horasSp = new JSpinner(new SpinnerNumberModel(
                Integer.parseInt(leerConfig("elo_horas", "48")), 1, 336, 6));
        String cualquiera = t("(cualquiera)", "(any)");
        JComboBox<String> mapaCb = new JComboBox<>();
        mapaCb.addItem(cualquiera);
        for (String m : MAPAS_CAT) mapaCb.addItem(m);
        mapaCb.setSelectedIndex(0);
        mapaCb.setSelectedItem(leerConfig("elo_mapa", cualquiera));
        JComboBox<String> civCb = new JComboBox<>();
        civCb.addItem(cualquiera);
        for (String c : CIVS_CAT) civCb.addItem(c);
        civCb.setSelectedIndex(0);
        civCb.setSelectedItem(leerConfig("elo_civ", cualquiera));
        JComboBox<String> intCb = new JComboBox<>(new String[]{
                t("Rápida", "Quick"), t("Amplia", "Broad"), t("Exhaustiva", "Exhaustive") });
        try { intCb.setSelectedIndex(Integer.parseInt(leerConfig("elo_intensidad", "0"))); } catch (Exception ignored) { }
        JPanel form = new JPanel(new GridLayout(6, 2, 8, 4));
        form.add(new JLabel(t("ELO mínimo:", "Min ELO:"))); form.add(minSp);
        form.add(new JLabel(t("ELO máximo:", "Max ELO:"))); form.add(maxSp);
        form.add(new JLabel(t("Últimas horas:", "Last hours:"))); form.add(horasSp);
        form.add(new JLabel(t("Mapa:", "Map:"))); form.add(mapaCb);
        form.add(new JLabel(t("Civilización (uno de los dos):", "Civilization (either player):"))); form.add(civCb);
        form.add(new JLabel(t("Intensidad:", "Intensity:"))); form.add(intCb);
        JLabel avisoCiv = new JLabel(t("Con civ o mapa, la primera tirada tarda ~1 min; repetirla continúa donde quedó y reutiliza lo ya leído.",
                "With civ or map filters the first run takes ~1 min; running it again picks up where it left off."));
        avisoCiv.setFont(avisoCiv.getFont().deriveFont(Font.PLAIN, 11f));
        JPanel formWrap = new JPanel(new BorderLayout(0, 8));
        formWrap.add(form, BorderLayout.CENTER);
        formWrap.add(avisoCiv, BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(this, formWrap, t("Partidas 1v1 al azar por ELO", "Random 1v1s by ELO"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        int a = (int) minSp.getValue(), b = (int) maxSp.getValue();
        guardarConfig("elo_min", String.valueOf(Math.min(a, b)));
        guardarConfig("elo_max", String.valueOf(Math.max(a, b)));
        guardarConfig("elo_horas", String.valueOf((int) horasSp.getValue()));
        guardarConfig("elo_mapa", String.valueOf(mapaCb.getSelectedItem()));
        guardarConfig("elo_civ", String.valueOf(civCb.getSelectedItem()));
        guardarConfig("elo_intensidad", String.valueOf(intCb.getSelectedIndex()));
        }   // continuar: sin diálogo — mismos parámetros, el muestreo recuerda lo leído
        final int lo = Integer.parseInt(leerConfig("elo_min", "2100"));
        final int hi = Integer.parseInt(leerConfig("elo_max", "2300"));
        String mSel = leerConfig("elo_mapa", "");
        String cSel = leerConfig("elo_civ", "");
        final String mapaSel = esCualquiera(mSel) ? null : mSel;   // «(cualquiera)» en cualquier idioma = sin filtro
        final String civSel  = esCualquiera(cSel) ? null : cSel;
        final int multAzar = switch (Integer.parseInt(leerConfig("elo_intensidad", "0"))) {
            case 1 -> 3; case 2 -> 10; default -> 1; };

        mostrarDirectos(false);
        fetchBtn.setEnabled(false);
        azarBtn.setEnabled(false);
        gteBtn.setEnabled(false);
        SUJETOS.clear();
        refrescarSujetos(List.of(), false);   // la cabecera pertenece a la tabla que viene
        taparResultados();
        trabajando(true);
        final long miSerial = opSerial;
        final int hours = Integer.parseInt(leerConfig("elo_horas", "48"));
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
        status.setText(t("Buscando partidas al azar ", "Searching random games ") + lo + "–" + hi + "…");
        log("azar #" + miSerial + ": inicio " + lo + "-" + hi + " h=" + hours + " mapa=" + mapaSel + " civ=" + civSel
                + " x" + multAzar + " continuar=" + continuar + " stop=" + stopOperacion);
        new SwingWorker<List<Match>, String>() {
            @Override protected List<Match> doInBackground() throws Exception {
                hiloOperacion = Thread.currentThread();   // Detener corta la espera del freno de ESTA operación, no la de todos
                Random rnd = new Random();
                if (hours >= 24) {   // la muestra nocturna cubre «ayer»: si da para una tanda, cero llamadas
                    List<Match> deMuestra = azarDesdeMuestra(lo, hi, mapaSel, civSel, rnd);
                    if (deMuestra.size() >= 5) { for (Match m : deMuestra) { m.azar = true; ajustarRefAzar(m, civSel); } return deMuestra; }
                }
                long ahora = System.currentTimeMillis();
                String firmaRango = lo + "|" + hi;
                String firmaTotal = firmaRango + "|" + hours + "|" + mapaSel + "|" + civSel;
                azarTramoAgotado = false;

                // Caché de sesión: contexto del ladder reutilizable y TTL de perfiles.
                if (ctxAzar == null || !firmaRango.equals(firmaRangoAzar)
                        || ahora - ctxAzarNacido > 10 * 60_000L) {
                    ctxAzar = new LbCtx();
                    ctxAzarNacido = ahora;
                    firmaRangoAzar = firmaRango;
                    pagsAzar = null;
                }
                perfilVistoAzar.values().removeIf(ts -> ahora - ts > 10 * 60_000L);
                boolean continua = firmaTotal.equals(firmaTotalAzar) && !perfilVistoAzar.isEmpty();
                firmaTotalAzar = firmaTotal;

                Set<Long> idsRes = new HashSet<>();
                List<Match> encontradas = new ArrayList<>();
                Map<Long, Integer> ratingsLb = new HashMap<>();
                for (PaginaLb pg : ctxAzar.cache.values())
                    for (long[] p : pg.jugadores()) ratingsLb.put(p[0], (int) p[1]);

                // Pre-siembra: lo ya leído en esta sesión que cumpla los filtros de hoy.
                for (Match m : filtrarAleatorias(cacheAzar.values(), ratingsLb, lo, hi, cutoff, mapaSel, civSel, 10, rnd))
                    if (idsRes.add(m.id)) encontradas.add(m);
                if (!encontradas.isEmpty())
                    publish(t("De lo ya leído en esta sesión: ", "From this session's cache: ")
                            + encontradas.size() + "/10\u2026");

                try {
                    // Tramo del rango en el ladder (bisección sobre páginas cacheadas).
                    ProveedorPaginas prov = p -> lbPagina(ctxAzar, p);
                    int ult = ultimaPaginaLadder(ctxAzar);
                    int pIni = primeraPaginaRango(prov, hi, ult);
                    int pFin = ultimaPaginaRango(prov, lo, ult);
                    log("azar #" + miSerial + ": tramo páginas " + pIni + "-" + pFin + " de " + ult + " stop=" + stopOperacion);
                    if (pIni > pFin) return ordenaYRecorta(encontradas);
                    double fraccion = (pFin - pIni + 1) / (double) Math.max(1, ult);

                    // ---- Río global: solo si el rango es una porción rentable del
                    // ladder y no hay filtro de civ (la dilución lo vuelve un pozo).
                    if (fraccion >= 0.12 && civSel == null && encontradas.size() < 10) {
                        String[] variantesLb = { "leaderboard_ids=rm_1v1&", "leaderboard_ids=3&", "" };
                        int varLb = 0;
                        int maxPagRio = mapaSel != null ? 25 : 15;
                        int pagLeidas = 0;
                        for (int pag = 1; pag <= maxPagRio && encontradas.size() < 10 && !stopOperacion; pag++) {
                            publish(t("Leyendo partidas recientes del ladder\u2026 (p\u00e1g. ", "Reading recent ladder games\u2026 (page ")
                                    + pag + ", " + encontradas.size() + "/10)");
                            String url = API + "/matches?" + variantesLb[varLb] + "page=" + pag + "&per_page=50";
                            List<Object> ms;
                            try {
                                ms = arr(val(obj(Json.parse(httpText429(url))), "matches"));
                            } catch (Exception ex) {
                                log("al azar (r\u00edo): fallo en p\u00e1gina " + pag + " (variante " + varLb + "): " + causa(ex));
                                if (varLb < variantesLb.length - 1 && pagLeidas == 0) { varLb++; pag = 0; continue; }
                                break;
                            }
                            if (ms.isEmpty() && pagLeidas == 0 && varLb < variantesLb.length - 1) {
                                varLb++;
                                pag = 0;
                                continue;
                            }
                            if (ms.isEmpty()) break;
                            pagLeidas++;
                            boolean algunaEnVentana = false;
                            for (Object o : ms) {
                                Match m = parseMatch(obj(o));
                                if (m == null || m.finished == null) continue;
                                cacheAzar.putIfAbsent(m.id, m);
                                if (!m.finished.isBefore(cutoff)) algunaEnVentana = true;
                                if (cumpleAzar(m, lo, hi, cutoff, mapaSel, civSel) && idsRes.add(m.id))
                                    encontradas.add(m);
                            }
                            if (!algunaEnVentana) break;      // el r\u00edo ya qued\u00f3 m\u00e1s viejo que la ventana
                            dormir(PAUSA_MS / 2);
                        }
                        log("al azar (r\u00edo): " + pagLeidas + " p\u00e1ginas le\u00eddas (variante " + varLb + "), "
                                + encontradas.size() + " v\u00e1lidas acumuladas");
                    }
                    if (encontradas.size() >= 10) return ordenaYRecorta(encontradas);

                    // ---- Perfiles activos del tramo, sin repetir los ya consultados.
                    if (pagsAzar == null) {
                        pagsAzar = new ArrayList<>();
                        for (int p = pIni; p <= pFin; p++) pagsAzar.add(p);
                        Collections.shuffle(pagsAzar, rnd);
                        cursorPagsAzar = 0;
                    }
                    int pasadas = ((mapaSel != null || civSel != null) ? 2 : 3) * multAzar;
                    for (int intento = 1; intento <= pasadas && encontradas.size() < 10 && !stopOperacion; intento++) {
                        int nPerfiles = civSel != null ? 40 : (intento == 1 ? 12 : 24);
                        int nPags = Math.min(pagsAzar.size(), civSel != null ? 12 : (intento == 1 ? 6 : 10));
                        if (intento > 1 || continua)
                            publish(t("A\u00fan ", "Still ") + encontradas.size()
                                    + t(" de 10; muestreando jugadores nuevos\u2026 (pasada ", " of 10; sampling new players\u2026 (pass ")
                                    + intento + "/" + pasadas + ")");
                        Map<Long, Match> unicos = new LinkedHashMap<>();
                        List<long[]> lb = new ArrayList<>();
                        for (int i = 0; i < nPags; i++) {
                            int pag = pagsAzar.get((cursorPagsAzar + i) % pagsAzar.size());
                            publish(t("Muestreando el ladder\u2026 (", "Sampling the ladder\u2026 (") + (i + 1) + "/" + nPags + ")");
                            lb.addAll(lbPagina(ctxAzar, pag).jugadores());
                        }
                        cursorPagsAzar += nPags;
                        ratingsLb.clear();
                        for (PaginaLb pg : ctxAzar.cache.values())
                            for (long[] p : pg.jugadores()) ratingsLb.put(p[0], (int) p[1]);
                        log("al azar por ELO " + lo + "-" + hi
                                + (mapaSel != null ? ", mapa=" + mapaSel : "")
                                + (civSel != null ? ", civ=" + civSel : "")
                                + ": pasada " + intento + "/" + pasadas
                                + ", tramo en p\u00e1ginas " + pIni + "\u2013" + pFin
                                + " (" + String.format("%.1f", fraccion * 100) + "% del ladder), "
                                + lb.size() + " jugadores a la vista, "
                                + perfilVistoAzar.size() + " consultados en la sesi\u00f3n");
                        List<Long> perfiles = new ArrayList<>();
                        for (long pid : elegirPerfiles(lb, lo, hi, cutoff.toEpochMilli(), nPerfiles * 4, rnd)) {
                            if (perfiles.size() >= nPerfiles) break;
                            if (!perfilVistoAzar.containsKey(pid)) perfiles.add(pid);
                        }
                        if (perfiles.isEmpty()) { azarTramoAgotado = true; break; }
                        int i = 0;
                        for (long pid : perfiles) {
                            if (stopOperacion) break;
                            publish(t("Perfil ", "Profile ") + (++i) + "/" + perfiles.size()
                                    + " \u00b7 " + encontradas.size() + "/10\u2026");
                            try {
                                Iterable<Match> leidas = COMPANION.partidas(pid, 1, PER_PAGE);
                                perfilVistoAzar.put(pid, System.currentTimeMillis());
                                for (Match m : leidas) {
                                    if (m != null && m.finished != null) {
                                        unicos.putIfAbsent(m.id, m);
                                        cacheAzar.putIfAbsent(m.id, m);
                                    }
                                }
                            } catch (Exception ex) {
                                log("al azar: fallo con perfil " + pid + ": " + causa(ex));
                            }
                            dormir(PAUSA_MS);
                        }
                        for (Match m : filtrarAleatorias(unicos.values(), ratingsLb, lo, hi, cutoff, mapaSel, civSel, 10, rnd))
                            if (idsRes.add(m.id)) encontradas.add(m);
                    }
                    log("al azar: total acumulado " + encontradas.size() + " partidas"
                            + (azarTramoAgotado ? " (tramo activo agotado en esta sesi\u00f3n)" : ""));
                    return ordenaYRecorta(encontradas);
                } catch (InterruptedException ex) {   // Detener durante la bisección o el muestreo: se aplica lo encontrado, como en la 1.1
                    if (stopOperacion) return ordenaYRecorta(encontradas);
                    throw ex;
                }
            }
            @Override protected void process(List<String> msgs) { status.setText(msgs.get(msgs.size() - 1)); }
            @Override protected void done() {
                if (miSerial != opSerial) {   // una operación posterior ya manda: no pisar su UI
                    log("azar #" + miSerial + ": terminó superada por la op #" + opSerial + "; resultado ignorado");
                    return;
                }
                fetchBtn.setEnabled(true);
                azarBtn.setEnabled(true);
                gteBtn.setEnabled(true);
                trabajando(false);
                if (continuarBtn != null) continuarBtn.setVisible(true);   // reanudar sin re-diálogo
                try {
                    List<Match> res = get();
                    log("azar #" + miSerial + ": done, " + res.size() + " partidas, stop=" + stopOperacion);
                    aprenderCatalogos(res);
                    if (stopOperacion) {
                        status.setText(t("Búsqueda detenida.", "Search stopped.")
                                + (res.isEmpty() ? "" : "  " + res.size()
                                   + t(" encontradas hasta el corte, aplicadas.", " found before the cut, applied.")));
                        if (res.isEmpty()) return;
                    } else if (res.isEmpty()) {
                        status.setText(t("Nada en ", "Nothing in ") + lo + "–" + hi
                                + t(" en las últimas ", " in the last ") + hours
                                + t(" h. Detalle del muestreo en descargas.log.", " h. Sampling details in descargas.log."));
                        return;
                    }
                    List<Player> refs = new ArrayList<>();
                    Set<Long> refIds = new HashSet<>();
                    for (Match m : res) {
                        m.azar = true;
                        ajustarRefAzar(m, civSel);   // el titular: quien jugó la civ filtrada
                        if (refIds.add(m.refId)) {
                            String nom = refNombre(m);
                            refs.add(new Player(m.refId, nom, "", 0));
                        }
                    }
                    SUJETOS.clear();
                    SUJETOS.addAll(refIds);
                    vistaDeSujetos = vistaActualId();
                    refrescarSujetos(refs, false);   // la cabecera cuenta la verdad del azar (≤4 visibles)
                    all.clear();
                    all.addAll(res);
                    playersList.clearSelection();
                    refreshModeCombo();
                    applyFilters();
                    String extra = "";
                    if (res.size() < 10 && azarTramoAgotado)
                        extra = t(" No hay más con esos filtros: tramo entero revisado (amplía horas o rango).",
                                  " Nothing else with those filters: whole bracket checked (widen hours or range).");
                    else if (res.size() < 10)
                        extra = t(" Repite la búsqueda: continúa donde lo dejó.",
                                  " Run it again: it picks up where it left off.");
                    status.setText(res.size() + t(" partidas 1v1 al azar, ELO ", " random 1v1s, ELO ") + lo + "–" + hi
                            + t(", últimas ", ", last ") + hours + " h." + extra);
                } catch (Exception ex) {
                    status.setText(stopOperacion ? t("Detenido.", "Stopped.") : "Error: " + causa(ex));
                    log("al azar por ELO: ERROR " + causa(ex));
                }
            }
        }.execute();
    }

    /** Estado compartido de una consulta al leaderboard: id que responde
     *  (rm_1v1 o 3), total de páginas y caché por página para que la
     *  búsqueda binaria no repita peticiones. */
    static final class LbCtx {
        String id;
        int totalPaginas = -1;
        final Map<Integer, PaginaLb> cache = new HashMap<>();
    }

    /** Baja y parsea una página del leaderboard, con caché. */
    static PaginaLb lbPagina(LbCtx ctx, int p) throws IOException, InterruptedException {
        PaginaLb enCache = ctx.cache.get(p);
        if (enCache != null) return enCache;
        Clasificacion root = null;
        List<FilaClasificacion> players = List.of();
        if (ctx.id == null) {
            for (String id : new String[]{ "rm_1v1", "3" }) {
                try {
                    root = COMPANION.clasificacion(id, p, 100, null);   // con reintento ante 429 (Detener corta la espera)
                    players = root.filas();
                    if (!players.isEmpty()) { ctx.id = id; break; }
                } catch (InterruptedException ie) { throw ie; }   // Detener: no probar el otro id ni decir «no responde»
                catch (IOException io) { if (String.valueOf(io.getMessage()).contains("429")) throw io; }   // un 429 es «espera», no «prueba otro id» (con reintentos, probar el «3» alargaba ~12 min y subía la pausa al tope)
                catch (Exception ignored) {}
            }
            if (ctx.id == null) throw new IOException("el leaderboard no responde (ni rm_1v1 ni 3)");
        } else {
            root = COMPANION.clasificacion(ctx.id, p, 100, null);   // con reintento ante 429 (Detener corta la espera)
            players = root.filas();
        }
        if (ctx.totalPaginas < 0 && root != null) {
            long total = root.total();
            long porPag = root.porPagina();
            if (porPag <= 0) porPag = 100;
            if (total > 0) ctx.totalPaginas = (int) ((total + porPag - 1) / porPag);
        }
        List<long[]> js = new ArrayList<>();
        int max = -1, min = -1;
        for (FilaClasificacion f : players) {
            long pid = f.pid();
            int rating = f.rating() != null ? f.rating() : -1;
            if (pid > 0 && rating > 0) {
                Instant lm = f.ultimaPartida();
                js.add(new long[]{ pid, rating, lm == null ? 0L : lm.toEpochMilli() });
                max = max < 0 ? rating : Math.max(max, rating);
                min = min < 0 ? rating : Math.min(min, rating);
            }
        }
        PaginaLb pag = new PaginaLb(js, max, min);
        ctx.cache.put(p, pag);
        log("leaderboard " + ctx.id + " página " + p + ": " + js.size() + " jugadores"
                + (js.isEmpty() ? "" : ", rating " + max + "–" + min));
        dormir(PAUSA_MS);
        return pag;
    }

    /** Número de páginas del ladder: del campo total si viene; si no, sondeo
     *  exponencial hasta encontrar una página vacía. */
    static int ultimaPaginaLadder(LbCtx ctx) throws IOException, InterruptedException {
        lbPagina(ctx, 1);
        if (ctx.totalPaginas > 0) return ctx.totalPaginas;
        int p = 1;
        while (p < 4096 && !lbPagina(ctx, p * 2).jugadores().isEmpty()) p *= 2;
        return p * 2;
    }

    /** httpText con hasta dos reintentos tras espera si el servidor limita (HTTP 429). Delega en ApiClient. */
    static String httpText429(String url) throws IOException, InterruptedException { return API_CLIENTE.textoCon429(url); }

    /** Río global: partidas RM 1v1 recientes de todo el ladder, sin perfiles
     *  (leaderboard_ids). Si el parámetro no estuviera soportado, devuelve
     *  vacío y los perfiles sostienen la búsqueda. */
    static List<Match> rioGlobalMuerto(int desde, int paginas) {
        List<Match> out = new ArrayList<>();
        for (int p = desde; p < desde + paginas; p++) {
            try {
                Object root = Json.parse(httpText(API + "/matches?leaderboard_ids=rm_1v1&page=" + p
                        + "&per_page=" + PER_PAGE));
                int antes = out.size();
                for (Object o : arr(val(obj(root), "matches"))) {
                    Match m = parseMatch(obj(o));
                    if (m != null && m.finished != null) out.add(m);
                }
                if (out.size() == antes) break;
                dormir(PAUSA_MS / 2);
            } catch (Exception ex) {
                log("río global: fallo en página " + p + ": " + causa(ex));
                break;
            }
        }
        return out;
    }

    /** «Guess the ELO!»: 5 partidas 1v1 recientes muestreadas por tramos de
     *  todo el ladder, anónimas en la app. La ventana temporal es la misma
     *  configurada para «Al azar por ELO…». */
    void buscarGte() {
        mostrarDirectos(false);
        fetchBtn.setEnabled(false);
        azarBtn.setEnabled(false);
        gteBtn.setEnabled(false);
        SUJETOS.clear();
        refrescarSujetos(List.of(), false);   // en GTE, nada que revelar en la cabecera
        taparResultados();                    // y sin modo consulta: sería hacer trampas
        trabajando(true);
        final long miSerial = opSerial;
        int hours = Integer.parseInt(leerConfig("elo_horas", "48"));
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
        status.setText(t("Preparando Guess the ELO…", "Preparing Guess the ELO…"));
        new SwingWorker<List<Match>, String>() {
            @Override protected List<Match> doInBackground() throws Exception {
                hiloOperacion = Thread.currentThread();   // Detener corta la espera del freno de ESTA operación, no la de todos
                Random rnd = new Random();
                List<Match> deMuestra = gteDesdeMuestra(rnd);   // la muestra nocturna de sfr-data: cero llamadas y nunca una partida repetida
                if (!deMuestra.isEmpty()) return deMuestra;
                LbCtx ctx = new LbCtx();
                int ult = ultimaPaginaLadder(ctx);
                int[][] tramos = tramosGte(ult);
                int[] franjas = franjasAleatorias(5, tramos.length, rnd);   // dado independiente por partida
                List<List<Long>> perfilesPorSlot = new ArrayList<>();
                Set<Long> usados = new HashSet<>();
                int nPerfiles = 0;
                for (int s = 0; s < franjas.length; s++) {
                    int[] tr = tramos[franjas[s]];
                    int pag = tr[0] + rnd.nextInt(Math.max(1, tr[1] - tr[0] + 1));
                    publish("Muestreando el ladder… (" + (s + 1) + "/" + franjas.length + ")");
                    List<long[]> js = new ArrayList<>(lbPagina(ctx, Math.min(pag, ult)).jugadores());
                    long cutMs = cutoff.toEpochMilli();
                    List<long[]> activos = new ArrayList<>(), resto = new ArrayList<>();
                    for (long[] j : js) ((j.length > 2 && j[2] >= cutMs) ? activos : resto).add(j);
                    Collections.shuffle(activos, rnd);
                    Collections.shuffle(resto, rnd);
                    List<long[]> orden = new ArrayList<>(activos);
                    orden.addAll(resto);
                    List<Long> ids = new ArrayList<>();
                    for (long[] j : orden) {
                        if (ids.size() >= 2) break;
                        if (usados.add(j[0])) ids.add(j[0]);
                    }
                    perfilesPorSlot.add(ids);
                    nPerfiles += ids.size();
                }
                Map<Long, Integer> ratings = new HashMap<>();
                for (PaginaLb pg : ctx.cache.values())
                    for (long[] p : pg.jugadores()) ratings.put(p[0], (int) p[1]);
                log("Guess the ELO: ladder de " + ult + " páginas, " + nPerfiles
                        + " perfiles en 5 slots con franja sorteada al azar");
                if (nPerfiles == 0) return List.of();

                List<List<Match>> validasPorSlot = new ArrayList<>();
                int i = 0;
                for (List<Long> ids : perfilesPorSlot) {
                    Map<Long, Match> unicos = new LinkedHashMap<>();
                    for (long pid : ids) {
                        if (stopOperacion) break;
                        publish(t("Perfil ", "Profile ") + (++i) + "/" + nPerfiles + "…");
                        try {
                            Iterable<Match> leidas = COMPANION.partidas(pid, 1, PER_PAGE);
                            for (Match m : leidas) {
                                if (m != null && m.finished != null) unicos.putIfAbsent(m.id, m);
                            }
                        } catch (Exception ex) {
                            log("Guess the ELO: fallo con perfil " + pid + ": " + causa(ex));
                        }
                        dormir(PAUSA_MS);
                    }
                    validasPorSlot.add(filtrarGte(unicos.values(), cutoff, 3, rnd));
                }
                List<Match> res = componerTanda(validasPorSlot, 5, rnd);
                int base = maxGteEnDisco();
                for (int k = 0; k < res.size(); k++) {
                    Match m = res.get(k);
                    m.gte = base + k + 1;
                    for (MatchPlayer p : m.players)
                        if (p.rating == null) p.rating = ratings.get(p.id);   // para el revelado
                }
                return res;
            }
            @Override protected void process(List<String> msgs) { status.setText(msgs.get(msgs.size() - 1)); }
            @Override protected void done() {
                if (miSerial != opSerial) { log("gte #" + miSerial + ": terminó superada por la op #" + opSerial); return; }
                fetchBtn.setEnabled(true);
                azarBtn.setEnabled(true);
                gteBtn.setEnabled(true);
                trabajando(false);
                try {
                    List<Match> res = get();
                    aprenderCatalogos(res);
                    if (res.isEmpty()) {
                        status.setText(t("Sin partidas para Guess the ELO en las últimas ", "No games for Guess the ELO in the last ") + hours
                                + t(" h. Detalle en descargas.log.", " h. Details in descargas.log."));
                        return;
                    }
                    all.clear();
                    all.addAll(res);
                    playersList.clearSelection();
                    refreshModeCombo();
                    applyFilters();
                    status.setText(res.size() + t(" partidas Guess the ELO (archivos: «Guess the ELO ", " Guess the ELO games (files: “Guess the ELO ")
                            + res.get(0).gte + t("»–«", "”–“") + res.get(res.size() - 1).gte
                            + t("»). Adivina y comprueba con «Revelar resultado…».", "”). Guess, then check with “Reveal result…”."));
                } catch (Exception ex) {
                    status.setText(stopOperacion ? t("Detenido.", "Stopped.") : "Error: " + causa(ex));
                    log("Guess the ELO: ERROR " + causa(ex));
                }
            }
        }.execute();
    }

    String grupoActivo() {   // null = «Todos»
        Object sel = grupoCombo.getSelectedItem();
        if (sel == null) return null;
        String s = String.valueOf(sel);
        return s.equals(t("Todos", "All")) || s.equals(TOP_LADDER) || s.equals(TOP_PAIS)
                || s.equals(t("+ Nuevo grupo…", "+ New group…"))
                || s.equals(t("Gestionar grupos…", "Manage groups…")) ? null : s;
    }

    static String limpiarGrupo(String nombre) {
        return nombre.trim().replace(";", " ").replace(",", " ");
    }

    /** Grupos creados por el usuario (existen aunque estén vacíos). */
    Set<String> gruposConfig() {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String g : leerConfig("grupos", "").split(",")) if (!g.isBlank()) out.add(g.trim());
        return out;
    }

    void registrarGrupo(String g) {
        Set<String> gs = gruposConfig();
        gs.add(g);
        guardarConfig("grupos", String.join(",", gs));
        rebuildGrupos();
    }

    void moverJugador(Player p, String grupo) {
        for (int i = 0; i < todosJugadores.size(); i++)
            if (todosJugadores.get(i).id() == p.id())
                todosJugadores.set(i, new Player(p.id(), p.name(), grupo));
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
        actualizarIndicadoresVivos();
        status.setText(p.name() + t(" movido al grupo «", " moved to group “") + grupo + t("».", "”."));
    }

    void renombrarGrupo(String viejo, String nuevo) {
        for (int i = 0; i < todosJugadores.size(); i++) {
            Player p = todosJugadores.get(i);
            if (p.grupo().equalsIgnoreCase(viejo))
                todosJugadores.set(i, new Player(p.id(), p.name(), nuevo));
        }
        Set<String> gs = gruposConfig();
        gs.removeIf(g -> g.equalsIgnoreCase(viejo));
        gs.add(nuevo);
        guardarConfig("grupos", String.join(",", gs));
        if (leerConfig("grupo_activo", "").equalsIgnoreCase(viejo)) guardarConfig("grupo_activo", nuevo);
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
    }

    void borrarGrupo(String g) {
        for (int i = 0; i < todosJugadores.size(); i++) {
            Player p = todosJugadores.get(i);
            if (p.grupo().equalsIgnoreCase(g))
                todosJugadores.set(i, new Player(p.id(), p.name(), GRUPO_GENERAL));
        }
        Set<String> gs = gruposConfig();
        gs.removeIf(x -> x.equalsIgnoreCase(g));
        guardarConfig("grupos", String.join(",", gs));
        if (leerConfig("grupo_activo", "").equalsIgnoreCase(g)) guardarConfig("grupo_activo", t("Todos", "All"));
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
    }

    /** Diálogo de gestión: crear, renombrar y borrar grupos sin tocar jugadores. */
    void gestionarGrupos() {
        DefaultListModel<String> modelo = new DefaultListModel<>();
        Runnable recargar = () -> {
            modelo.clear();
            Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            gs.add(GRUPO_GENERAL);
            gs.addAll(gruposConfig());
            for (Player p : todosJugadores) gs.add(p.grupo());
            for (String g : gs) modelo.addElement(g);
        };
        recargar.run();
        JList<String> lista = new JList<>(modelo);
        lista.setVisibleRowCount(8);
        JButton nuevo = new JButton(t("Nuevo…", "New…"));
        JButton renombrar = new JButton(t("Renombrar…", "Rename…"));
        JButton borrar = new JButton(t("Borrar", "Delete"));
        nuevo.addActionListener(a -> {
            String n = JOptionPane.showInputDialog(this, t("Nombre del grupo nuevo:", "New group name:"),
                    t("Nuevo grupo", "New group"), JOptionPane.PLAIN_MESSAGE);
            if (n != null && !n.isBlank()) { registrarGrupo(limpiarGrupo(n)); recargar.run(); }
        });
        renombrar.addActionListener(a -> {
            String sel = lista.getSelectedValue();
            if (sel == null || sel.equalsIgnoreCase(GRUPO_GENERAL)) return;
            String n = JOptionPane.showInputDialog(this, t("Nuevo nombre para «", "New name for “") + sel + "»:",
                    t("Renombrar grupo", "Rename group"), JOptionPane.PLAIN_MESSAGE);
            if (n != null && !n.isBlank()) { renombrarGrupo(sel, limpiarGrupo(n)); recargar.run(); }
        });
        borrar.addActionListener(a -> {
            String sel = lista.getSelectedValue();
            if (sel == null || sel.equalsIgnoreCase(GRUPO_GENERAL)) return;
            int r = JOptionPane.showConfirmDialog(this,
                    t("Se borrará el grupo «", "Group “") + sel
                            + t("». Sus jugadores pasarán a General. ¿Continuar?",
                                "” will be deleted. Its players move to General. Continue?"),
                    t("Borrar grupo", "Delete group"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r == JOptionPane.YES_OPTION) { borrarGrupo(sel); recargar.run(); }
        });
        lista.addListSelectionListener(a -> {
            String sel = lista.getSelectedValue();
            boolean editable = sel != null && !sel.equalsIgnoreCase(GRUPO_GENERAL);
            renombrar.setEnabled(editable);
            borrar.setEnabled(editable);
        });
        renombrar.setEnabled(false);
        borrar.setEnabled(false);
        JPanel botones = new JPanel(new GridLayout(3, 1, 0, 6));
        botones.add(nuevo); botones.add(renombrar); botones.add(borrar);
        JPanel cont = new JPanel(new BorderLayout(8, 0));
        cont.add(new JScrollPane(lista), BorderLayout.CENTER);
        cont.add(botones, BorderLayout.EAST);
        JOptionPane.showMessageDialog(this, cont, t("Gestionar grupos", "Manage groups"), JOptionPane.PLAIN_MESSAGE);
    }

    String grupoDestino() {
        String g = grupoActivo();
        return g != null ? g : GRUPO_GENERAL;
    }

    /** Antes de lanzar el juego desde un doble clic: confirmación con «no volver a preguntar». */
    boolean confirmarEspectar(String quien) {
        if ("1".equals(leerConfig("espectar_sin_preguntar", "0"))) return true;
        JCheckBox noMas = new JCheckBox(t("No volver a preguntar", "Don't ask again"));
        int r = JOptionPane.showConfirmDialog(this, new Object[]{
                t("¿Espectar la partida de ", "Spectate ") + quien + t(" en el juego?\nSe abrirá Age of Empires II.", "'s game in-game?\nAge of Empires II will open."),
                noMas }, t("Espectar", "Spectate"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return false;
        if (noMas.isSelected()) guardarConfig("espectar_sin_preguntar", "1");
        return true;
    }

    /** Cierra la búsqueda actual: tabla vacía (vuelve la guía), cabecera fuera, sin spoilers pendientes. */
    void cerrarBusqueda() {
        if (fetchWorker != null) { stopOperacion = true; }
        all.clear();
        view.clear();
        tableModel.fireTableDataChanged();
        SUJETOS.clear();
        filtroSujetos.clear();
        invitado = null;
        refrescarSujetos(List.of(), false);
        taparResultados();
        mostrarGuiaVacia(true);
        actualizarTextoBuscar();
        status.setText(t("Búsqueda cerrada.", "Search closed."));
    }

    /** Ficha a uno desde un top y, como el buscador, ofrece sus cuentas vinculadas. */
    void ficharDesdeTop(Player p, String g) {
        if (containsPlayerId(p.id())) return;
        todosJugadores.add(new Player(p.id(), p.name(), g));
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
        status.setText(p.name() + t(" añadido a «", " added to \u201C") + g + t("» de tu watchlist.", "\u201D in your watchlist."));
        ofrecerVinculadasTrasAlta(p.id(), p.name(), g);
    }

    /** Ficha a varios de golpe; pregunta UNA vez si buscar sus cuentas vinculadas y lo hace en segundo plano. */
    void ficharVarios(List<Player> lista, String g) {
        List<Player> nuevos = new ArrayList<>();
        for (Player p : lista) if (!containsPlayerId(p.id())) { todosJugadores.add(new Player(p.id(), p.name(), g)); nuevos.add(p); }
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
        refrescarWatchlist();
        status.setText(nuevos.size() + t(" jugadores añadidos a «", " players added to \u201C") + g + "\u00bb.");
        if (nuevos.isEmpty()) return;
        int r = JOptionPane.showConfirmDialog(this,
                t("¿Buscar las cuentas vinculadas de los ", "Look up the linked accounts of the ") + nuevos.size()
                        + t(" jugadores y añadirlas al grupo? (una consulta por jugador, en segundo plano)", " players and add them to the group? (one lookup per player, in the background)"),
                t("Cuentas vinculadas", "Linked accounts"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        trabajando(true);
        final long miSerial = opSerial;
        new SwingWorker<Integer, String>() {
            @Override protected Integer doInBackground() {
                hiloOperacion = Thread.currentThread();   // Detener corta la espera del freno de ESTA operación, no la de todos
                int anadidas = 0;
                for (Player p : nuevos) {
                    if (stopOperacion) break;
                    publish(t("Vinculadas de ", "Linked accounts of ") + p.name() + "\u2026");
                    try {
                        List<Object[]> vinc = cuentasVinculadas(p.id());
                        Set<Long> familia = new HashSet<>(); familia.add(p.id());
                        for (Object[] v : vinc) {
                            long vid = (Long) v[0];
                            if (!containsPlayerId(vid)) { todosJugadores.add(new Player(vid, (String) v[1], g)); anadidas++; }
                            familia.add(vid);
                        }
                        if (familia.size() > 1) marcarVinculo(familia);
                    } catch (Exception ex) { log("vinculadas " + p.name() + ": " + causa(ex)); }
                    dormir(PAUSA_MS / 2);
                }
                return anadidas;
            }
            @Override protected void process(List<String> ch) { status.setText(ch.get(ch.size() - 1)); }
            @Override protected void done() {
                if (miSerial != opSerial) return;
                trabajando(false);
                savePlayers(); rebuildGrupos(); aplicarFiltroGrupo(); refrescarWatchlist();
                int n = 0; try { n = get(); } catch (Exception ignored) { }
                status.setText(n + t(" cuentas vinculadas añadidas a «", " linked accounts added to \u201C") + g + "\u00bb.");
            }
        }.execute();
    }

    Set<String> gruposExistentes() {
        Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        gs.add(GRUPO_GENERAL);
        for (Player x : todosJugadores) gs.add(x.grupo());
        gs.addAll(gruposConfig());
        return gs;
    }

    void moverVarios(List<Player> lista, String g) {
        Set<Long> ids = new HashSet<>(); for (Player x : lista) ids.add(x.id());
        todosJugadores.replaceAll(x -> ids.contains(x.id()) ? new Player(x.id(), x.name(), g, x.vinculo()) : x);
        savePlayers(); rebuildGrupos(); aplicarFiltroGrupo(); refrescarWatchlist();
        status.setText(ids.size() + t(" jugadores movidos a «", " players moved to \u201C") + g + "\u00bb.");
    }

    /** Pregunta a qué grupo fichar (preseleccionado el activo), con «Nuevo grupo…». null = cancelado. */
    String elegirGrupoDialog(String nombreJugador) {
        Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        gs.add(GRUPO_GENERAL);
        for (Player x : todosJugadores) gs.add(x.grupo());
        gs.addAll(gruposConfig());
        String nuevoO = t("+ Nuevo grupo\u2026", "+ New group\u2026");
        List<String> ops = new ArrayList<>(gs);
        ops.add(nuevoO);
        JComboBox<String> cb = new JComboBox<>(ops.toArray(String[]::new));
        String pre = grupoActivo();
        if (pre != null && gs.contains(pre)) cb.setSelectedItem(pre);
        JPanel pnl = new JPanel(new BorderLayout(0, 6));
        pnl.add(new JLabel(t("¿A qué grupo añadir a ", "Which group should ") + nombreJugador + (t("?", " join?"))), BorderLayout.NORTH);
        pnl.add(cb, BorderLayout.CENTER);
        int r = JOptionPane.showConfirmDialog(this, pnl, t("Añadir al grupo", "Add to group"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return null;
        String sel = String.valueOf(cb.getSelectedItem());
        if (sel.equals(nuevoO)) {
            String nombre = JOptionPane.showInputDialog(this, t("Nombre del nuevo grupo:", "New group name:"), "");
            if (nombre == null || nombre.trim().isEmpty()) return null;
            nombre = nombre.trim();
            Set<String> cfg = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            cfg.addAll(gruposConfig()); cfg.add(nombre);
            guardarConfig("grupos", String.join(";", cfg));
            return nombre;
        }
        return sel;
    }

    /** Rellena el combo con «Todos», los grupos existentes y «+ Nuevo grupo…»,
     *  conservando la selección guardada. */
    void rebuildGrupos() {
        var listener = grupoCombo.getActionListeners();
        for (var l : listener) grupoCombo.removeActionListener(l);
        String guardado = leerConfig("grupo_activo", t("Todos", "All"));
        Set<String> grupos = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Player p : todosJugadores) grupos.add(p.grupo());
        grupos.addAll(gruposConfig());
        Set<String> persistir = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String g : grupos) if (!g.equalsIgnoreCase(GRUPO_GENERAL)) persistir.add(g);
        guardarConfig("grupos", String.join(",", persistir));   // un grupo ya nunca se esfuma al vaciarse
        grupoCombo.removeAllItems();
        grupoCombo.addItem(t("Todos", "All"));
        grupoCombo.addItem(TOP_LADDER);
        grupoCombo.addItem(TOP_PAIS);
        grupoCombo.addItem(TOP_CLAN);
        for (String g : grupos) grupoCombo.addItem(g);
        grupoCombo.setSelectedItem(t("Todos", "All"));
        for (int i = 0; i < grupoCombo.getItemCount(); i++)
            if (grupoCombo.getItemAt(i).equalsIgnoreCase(guardado)) { grupoCombo.setSelectedIndex(i); break; }
        for (var l : listener) grupoCombo.addActionListener(l);
    }

    void onGrupoElegido() {
        String sel = String.valueOf(grupoCombo.getSelectedItem());
        if (sel.equals(TOP_CLAN)) {
            guardarConfig("grupo_activo", sel);
            actualizarBotonesModo();
            if (clanField != null && clanField.getText().isBlank()) clanField.setText(leerConfig("clan_tag", ""));
            cargarTopClan();
            return;
        }
        if (sel.equals(TOP_LADDER) || sel.equals(TOP_PAIS)) {
            guardarConfig("grupo_activo", sel);
            actualizarBotonesModo();
            cargarTopLadder(false);
            return;
        }
        guardarConfig("grupo_activo", sel);
        actualizarBotonesModo();
        aplicarFiltroGrupo();
        refrescarWatchlist();
        actualizarIndicadoresVivos();
    }

    void crearGrupoDialog() {
        String nombre = JOptionPane.showInputDialog(this,
                t("Nombre del grupo nuevo:", "New group name:"),
                t("Nuevo grupo", "New group"), JOptionPane.PLAIN_MESSAGE);
        if (nombre == null || nombre.isBlank()) return;
        String limpio = limpiarGrupo(nombre);
        guardarConfig("grupo_activo", limpio);
        registrarGrupo(limpio);
        grupoCombo.setSelectedItem(limpio);
        aplicarFiltroGrupo();
        actualizarIndicadoresVivos();
        status.setText(t("Grupo «", "Group “") + limpio
                + t("» activo: los próximos jugadores que añadas caerán ahí.",
                    "” active: players you add next will go there."));
    }

    void refrescarCabeceraOrden() {
        if (cabLabel == null) return;
        String ordenCfg = leerConfig("orden_watch", "elo");
        boolean porForma = formaVisible && ordenCfg.startsWith("forma");
        boolean formaAsc = "forma_asc".equals(ordenCfg);
        boolean porElo = !porForma && mostrarEloWatch && "elo".equals(ordenCfg);
        String ordenable = " <font color='#8a8a8a'>\u21C5</font>";   // «⇅»: aquí también se puede ordenar
        cabLabel.setToolTipText(t("Clic en una cabecera para ordenar por Nick, ELO o Forma", "Click a header to sort by Nick, ELO or Recent form"));
        cabLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        int w = Math.max(150, playersList.getWidth() > 0 ? playersList.getWidth() - 22 : 250);
        cabLabel.setText("<html><table width='" + w + "' cellpadding='0' cellspacing='0'><tr>"
                + "<td width='24'></td><td width='14'></td>"
                + "<td><b>Nick" + (porElo || porForma ? ordenable : " \u2193") + "</b></td>"
                + (formaVisible ? "<td width='72' align='center'><b>" + t("Forma ", "Form ") + (ventanaForma <= 24 ? "24h" : "7d")
                        + (porForma ? (formaAsc ? " \u2191" : " \u2193") : ordenable) + "</b></td>" : "")
                + "<td width='" + anchoCeldaElo(Math.max(150, playersList.getWidth() - 22)) + "' align='right'><b>" + (mostrarEloWatch ? "ELO" + (porElo ? " \u2193" : ordenable) : "") + "</b></td>"
                + "</tr></table></html>");
    }

    /** En los modos ★ la lista es de solo lectura; el país solo se ve en ★ país. */
    void actualizarBotonesModo() {
        boolean editable = !modoTop();

        if (delBtn != null) delBtn.setEnabled(editable);
        if (parPais != null) parPais.setVisible(modoPais());
        if (parClan != null) parClan.setVisible(modoClan());
        if (parClanGuardados != null) parClanGuardados.setVisible(modoClan() && !clanesGuardados().isEmpty());
        if (topNCombo != null) { topNCombo.setVisible(modoTop() && !modoClan()); rellenandoTopN = true; try { topNCombo.setSelectedIndex(Math.max(0, Arrays.asList("25", "50", "100").indexOf(leerConfig("top_n", "50")))); } finally { rellenandoTopN = false; } }
        refrescarCampanaBtn();
        if (addJugBtn != null) addJugBtn.setVisible(!modoTop());   // en los tops se ficha desde la fila
        if (delBtn != null) delBtn.setVisible(!modoTop() && grupoActivo() != null);   // solo en grupos personalizados
        if (buscaNick != null) buscaNick.putClientProperty("JTextField.placeholderText", modoTop()
                ? t("\uD83D\uDD0D Buscar jugador por nick  (Enter = ver sus partidas)", "\uD83D\uDD0D Find a player by nick  (Enter = view their games)")
                : t("\uD83D\uDD0D Buscar o añadir jugador por nick  (Enter = ver sus partidas)", "\uD83D\uDD0D Find or add a player by nick  (Enter = view their games)"));
    }

    /** Reconstruye la lista visible con el grupo activo («Todos» = todos). */
    void aplicarFiltroGrupo() {
        String g = grupoActivo();
        List<Player> vis = new ArrayList<>();
        if (modoTop()) vis.addAll(topLadder);
        else for (Player p : todosJugadores)
            if (g == null || p.grupo().equalsIgnoreCase(g)) vis.add(p);
        if (soloVivosBtn != null && soloVivosBtn.isSelected()) {
            final List<Player> ambito = new ArrayList<>(vis);
            vis.removeIf(p -> !vivoWatch.containsKey(p.id())
                    && !(p.vinculo() != 0 && familiaViva(ambito, p.vinculo())));
        }
        boolean porElo = mostrarEloWatch && "elo".equals(leerConfig("orden_watch", "elo"));
        String ordenCfg = leerConfig("orden_watch", "elo");
        boolean porForma = formaVisible && ordenCfg.startsWith("forma");
        boolean formaAsc = "forma_asc".equals(ordenCfg);   // ascendente = los que más bajan, arriba
        final Map<Long, Forma> fa = formaActiva();
        Comparator<Player> orden = porForma
                ? Comparator.<Player, Integer>comparing(p -> fa.containsKey(p.id()) && fa.get(p.id()).partidas() > 0 ? fa.get(p.id()).diff() : null,
                        Comparator.nullsLast(formaAsc ? Comparator.<Integer>naturalOrder() : Comparator.<Integer>reverseOrder()))
                        .thenComparing(p -> p.name().toLowerCase())
                : porElo
                ? Comparator.<Player, Integer>comparing(p -> eloWatch.get(p.id()),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(p -> p.name().toLowerCase())
                : Comparator.comparing(p -> p.name().toLowerCase());
        // Familias: la cuenta con más ELO encabeza; las demás cuelgan si está expandida
        marcaFila.clear();
        vivoFamilia.clear();
        Map<Long, List<Player>> familias = new LinkedHashMap<>();
        List<Player> cabezas = new ArrayList<>();
        for (Player p : vis) {
            if (!modoTop() && p.vinculo() != 0) familias.computeIfAbsent(p.vinculo(), k -> new ArrayList<>()).add(p);
            else cabezas.add(p);
        }
        for (Map.Entry<Long, List<Player>> f : familias.entrySet()) {
            List<Player> fam = f.getValue();
            if (fam.size() == 1) { cabezas.add(fam.get(0)); continue; }
            fam.sort(Comparator.comparing((Player p) -> eloWatch.get(p.id()),
                            Comparator.nullsLast(Comparator.<Integer>reverseOrder()))
                    .thenComparing(p -> p.name().toLowerCase()));
            Player ppal = fam.get(0);
            cabezas.add(ppal);
            boolean algunaViva = false;
            for (Player x : fam) if (vivoWatch.containsKey(x.id())) algunaViva = true;
            vivoFamilia.put(ppal.id(), algunaViva);
            boolean exp = vinculosExpandidos.contains(f.getKey());
            marcaFila.put(ppal.id(), exp ? 'E' : 'P');
            if (exp) for (int i = 1; i < fam.size(); i++) marcaFila.put(fam.get(i).id(), 'H');
        }
        cabezas.sort(orden);
        vis = new ArrayList<>();
        for (Player c : cabezas) {
            vis.add(c);
            if (marcaFila.getOrDefault(c.id(), ' ') == 'E') {
                List<Player> fam = familias.get(c.vinculo());
                for (int i = 1; i < fam.size(); i++) vis.add(fam.get(i));
            }
        }
        if (invitado != null && containsPlayerId(invitado.id()))
            invitado = null;   // fichado por cualquier vía: deja de flotar
        if (invitado != null && !vistaActualId().equals(vistaDelInvitado))
            invitado = null;   // cambiar de vista (grupo o país) despide al invitado
        if (sujetosPanel != null && sujetosPanel.isVisible() && !vistaActualId().equals(vistaDeSujetos)) {
            SUJETOS.clear();
            refrescarSujetos(List.of(), false);   // la cabecera refleja la tabla; otra vista, otra historia
            taparResultados();
            apagarForma();
        }
        if (invitado != null) {
            final long invId = invitado.id();
            vis.removeIf(px -> px.id() == invId);   // el invitado vive en la cabecera fija, no como fila
        }
        boolean igual = vis.size() == playersModel.size();
        if (igual)
            for (int i = 0; i < vis.size(); i++)
                if (vis.get(i).id() != playersModel.get(i).id()) { igual = false; break; }
        if (igual) return;   // nada que redibujar: la selección del usuario se conserva
        Set<Long> selPrevia = new HashSet<>();
        for (Player p : playersList.getSelectedValuesList()) selPrevia.add(p.id());
        playersModel.clear();
        for (Player p : vis) playersModel.addElement(p);
        actualizarTextoBuscar();
        if (!selPrevia.isEmpty()) {   // la selección sobrevive al reordenado (ELOs frescos, vivos…)
            List<Integer> idxs = new ArrayList<>();
            for (int i = 0; i < playersModel.size(); i++)
                if (selPrevia.contains(playersModel.get(i).id())) idxs.add(i);
            int[] arr = idxs.stream().mapToInt(Integer::intValue).toArray();
            if (arr.length > 0) playersList.setSelectedIndices(arr);
        }
        sincronizarSocket();   // la vista manda: el socket vigila exactamente lo que se ve
    }

    /** Barrido de la Watchlist al abrir: para cada seguido, una consulta ligera
     *  que detecta partida en curso (finished vacío) y su ELO actual (rating de
     *  su último 1v1 terminado). Solo toca la lista, nunca la tabla: los
     *  resultados de las partidas siguen sin verse. */
    /** Carga el top N del leaderboard (nick, ELO, última partida) sin tocar
     *  players.txt, con caché de 10 min, y dispara un barrido de vivos. */
    void cargarTopLadder(boolean forzar) {
        if (cargandoTop) return;
        long ahora = System.currentTimeMillis();
        String pais = modoPais() ? paisSel() : null;
        String firma = pais == null ? "global" : pais;
        if (!forzar && firma.equals(topFirma) && !topLadder.isEmpty() && ahora - topCargado < 10 * 60_000L) {
            aplicarFiltroGrupo();
            actualizarIndicadoresVivos();
            vigilarTop();
            return;
        }
        cargandoTop = true;
        int topN = Integer.parseInt(leerConfig("top_n", "50"));
        String nombrePais = null;
        if (pais != null) for (PaisItem pi : PAISES) if (pi.code().equals(pais)) { nombrePais = pi.nombre(); break; }
        final String nombrePaisF = nombrePais;
        status.setText(t("Cargando el top ", "Loading the top ") + topN
                + (nombrePais != null ? t(" de ", " of ") + nombrePais : t(" del ladder…", " of the ladder…")));
        new SwingWorker<List<Object[]>, Void>() {
            @Override protected List<Object[]> doInBackground() throws Exception {
                List<Object[]> out = new ArrayList<>();
                for (String id : new String[]{ "rm_1v1", "3" }) {
                    try {
                        for (FilaClasificacion f : COMPANION.clasificacion(id, 1, 100, pais).filas()) {
                            long pid = f.pid();
                            int rating = f.rating() != null ? f.rating() : -1;
                            String name = String.valueOf(f.nombre());
                            aprenderCanal(pid, f.canal());
                            aprenderPais(pid, f.pais());
                            Instant lm = f.ultimaPartida();
                            if (f.racha() != null) TOP_STREAK.put(pid, f.racha());
                            if (f.jugadas10() > 0) TOP_LAST10.put(pid, new int[]{ f.ganadas10(), f.jugadas10() - f.ganadas10() });
                            if (pid > 0 && rating > 0 && !"null".equals(name)) {
                                out.add(new Object[]{ pid, name, rating, lm == null ? 0L : lm.toEpochMilli() });
                                if (f.partidas() != null) gamesWatch.put(pid, f.partidas());
                            }
                            if (out.size() >= topN) break;
                        }
                        if (!out.isEmpty()) break;
                    } catch (Exception ex) {
                        log("top ladder: fallo con id " + id + ": " + causa(ex));
                    }
                }
                return out;
            }
            @Override protected void done() {
                cargandoTop = false;
                if (modoClan() || !modoTop()) return;   // mientras cargaba, el usuario cambió de vista: no pintar encima
                try {
                    List<Object[]> res = get();
                    if (res.isEmpty()) {
                        if (cargarTopCache(firma)) {
                            topFirma = firma;
                            aplicarFiltroGrupo();
                            actualizarIndicadoresVivos();
                            long horasCache = Math.max(1, (System.currentTimeMillis() - topCargado) / 3600_000L);
                            status.setText(t("El servicio de datos no responde (¿bloqueo de red? p. ej. LaLiga/Cloudflare). Mostrando el top de hace ~",
                                    "The data service isn't responding (network block? e.g. LaLiga/Cloudflare). Showing the top from ~")
                                    + horasCache + t(" h. Reintento automático cada 2 min.", " h ago. Auto-retrying every 2 min."));
                        } else {
                            if (!avisoTopMostrado) {
                                avisoTopMostrado = true;
                                JOptionPane.showMessageDialog(SpoilerFreeRecs.this,
                                        t("No se pudo cargar el top del ladder.\n\nCausa probable: el servicio de datos está caído o bloqueado\n(p. ej. LaLiga/Cloudflare en días de fútbol en España).\n\nLa app reintenta sola cada 2 minutos — no hace falta hacer nada.",
                                          "Couldn't load the ladder top.\n\nLikely cause: the data service is down or blocked\n(e.g. LaLiga/Cloudflare on football days in Spain).\n\nThe app retries every 2 minutes on its own — nothing to do."),
                                        t("Servicio no disponible", "Service unavailable"),
                                        JOptionPane.WARNING_MESSAGE);
                            }
                            status.setText(t("No se pudo cargar el top (¿servicio caído o bloqueado? p. ej. LaLiga/Cloudflare en días de fútbol). Reintento automático cada 2 min.",
                                    "Couldn't load the top (service down or blocked? e.g. LaLiga/Cloudflare on match days). Auto-retrying every 2 min."));
                        }
                        return;
                    }
                    topLadder.clear();
        ultimoTopMs = 0; ultimoTwitchMs = 0;   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
                    lastTop.clear();
                    rankTop.clear();
                    for (Object[] j : res) {
                        long pid = (Long) j[0];
                        topLadder.add(new Player(pid, (String) j[1], TOP_LADDER));
                        eloWatch.put(pid, (Integer) j[2]);
                        lastTop.put(pid, (Long) j[3]);
                        rankTop.put(pid, topLadder.size());
                    }
                    topCargado = System.currentTimeMillis();
                    topFirma = firma;
                    guardarTopCache(firma);
                    aplicarFiltroGrupo();
                    actualizarIndicadoresVivos();
                    status.setText(t("Top ", "Top ") + topLadder.size()
                            + (nombrePaisF != null ? t(" de ", " of ") + nombrePaisF : t(" del ladder", " of the ladder"))
                            + t(" cargado. Los puntos rojos llegan en segundos…",
                                " loaded. Red dots arriving in seconds…"));
                    vigilarTop();
                } catch (Exception ex) {
                    status.setText(t("Error cargando el top: ", "Error loading the top: ") + causa(ex));
                }
            }
        }.execute();
    }

    void guardarTopCache(String firma) {
        try {
            List<String> lines = new ArrayList<>();
            lines.add(firma + "|" + topCargado);
            for (Player p : topLadder)
                lines.add(p.id() + ";" + p.name() + ";" + eloWatch.getOrDefault(p.id(), 0)
                        + ";" + lastTop.getOrDefault(p.id(), 0L));
            Files.write(TOP_CACHE, lines, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log("top cache: no se pudo guardar: " + causa(ex));
        }
    }

    /** Restaura el último top guardado si es de la misma vista. Devuelve éxito. */
    boolean cargarTopCache(String firma) {
        try {
            if (!Files.exists(TOP_CACHE)) return false;
            List<String> lines = Files.readAllLines(TOP_CACHE, StandardCharsets.UTF_8);
            if (lines.size() < 2) return false;
            String[] cab = lines.get(0).split("\\|", 2);
            if (!cab[0].equals(firma)) return false;
            topLadder.clear();
        ultimoTopMs = 0; ultimoTwitchMs = 0;   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
            lastTop.clear();
            rankTop.clear();
            for (int i = 1; i < lines.size(); i++) {
                String[] c = lines.get(i).split(";", 4);
                if (c.length < 4) continue;
                long pid = Long.parseLong(c[0]);
                topLadder.add(new Player(pid, c[1], TOP_LADDER));
                int elo = Integer.parseInt(c[2]);
                if (elo > 0) eloWatch.put(pid, elo);
                lastTop.put(pid, Long.parseLong(c[3]));
                rankTop.put(pid, topLadder.size());
            }
            topCargado = Long.parseLong(cab[1]);
            return !topLadder.isEmpty();
        } catch (Exception ex) {
            log("top cache: no se pudo leer: " + causa(ex));
            return false;
        }
    }

    /** Vivos del top: el río global de partidas en curso como motor (1-2
     *  llamadas), consulta a los «calientes» si el río no trae en-curso, y
     *  verificación individual presupuestada de los que se apagan. */
    void vigilarTop() {
        if (vigilandoTop || topLadder.isEmpty()) return;
        if (System.currentTimeMillis() - ultimoTopMs < 45_000) return;   // anti-solape: un barrido por tick
        ultimoTopMs = System.currentTimeMillis();
        vigilandoTop = true;
        List<Player> top = new ArrayList<>(topLadder);
        new SwingWorker<Map<Long, Long>, Void>() {
            final List<Match> terminadasRio = new ArrayList<>();
            @Override protected Map<Long, Long> doInBackground() {
                Map<Long, Long> resultado = new HashMap<>();
                topVerificados.clear();
                // Lotes pequeños y ventana ancha: en un top país los más activos encadenan TG cortas y
                // llenan la ventana con partidas terminadas, dejando fuera las que están en curso
                final int LOTE = 15;
                for (int d = 0; d < top.size(); d += LOTE) {
                    List<Player> lote = top.subList(d, Math.min(d + LOTE, top.size()));
                    Set<Long> idsLote = new HashSet<>();
                    StringBuilder csv = new StringBuilder();
                    for (Player p : lote) {
                        idsLote.add(p.id());
                        if (csv.length() > 0) csv.append(',');
                        csv.append(p.id());
                    }
                    try {
                        Iterable<Match> leidas = COMPANION.partidas(csv.toString(), 1, 100);
                        int nPart = 0, nCurso = 0; Instant masAntigua = null;
                        for (Match m : leidas) {
                            if (m == null) continue;
                            nPart++;
                            if (m.started != null && (masAntigua == null || m.started.isBefore(masAntigua))) masAntigua = m.started;
                            if (!enCursoReal(m)) continue;
                            nCurso++;
                            for (MatchPlayer mp : m.players)
                                if (idsLote.contains(mp.id) && !resultado.containsKey(mp.id)) {
                                    resultado.put(mp.id, m.id);
                                    vivoInfo.put(mp.id, resumenVivo(m, mp.id));
                                    if (!vivoWatch.containsKey(mp.id)) avisarSiCampana(mp.id, m);   // nuevo en partida desde el último barrido
                                    VIVO_PARTIDA.put(mp.id, m);
                                }
                        }
                        long minAnt = masAntigua == null ? -1 : Duration.between(masAntigua, Instant.now()).toMinutes();
                        log("top vivos: lote " + (d / LOTE + 1) + " → " + nPart + " partidas, " + nCurso + " en curso, la más antigua hace " + minAnt + " min");
                        topVerificados.addAll(idsLote);   // solo lo verificado se actualiza
                    } catch (Exception ex) {
                        log("top vivos: fallo con el lote " + (d / LOTE + 1) + " (se conserva el estado anterior): " + causa(ex));
                    }
                    dormir(PAUSA_MS / 2);
                }
                // Confirmación individual: quien estaba en partida y ya no aparece en el lote, se consulta solo
                for (Player p : top) {
                    if (stopOperacion) break;
                    if (!vivoWatch.containsKey(p.id()) || resultado.containsKey(p.id()) || !topVerificados.contains(p.id())) continue;
                    try {
                        Iterable<Match> leidas = COMPANION.partidas(p.id(), 1, 3);
                        Match ultima = null;
                        for (Match m : leidas) { if (m != null) { ultima = m; break; } }
                        if (ultima != null && enCursoReal(ultima)) {
                            resultado.put(p.id(), ultima.id);
                            vivoInfo.put(p.id(), resumenVivo(ultima, p.id()));
                            log("top vivos: " + p.name() + " seguía en partida (" + ultima.id + ") aunque el lote no la traía");
                        } else {
                            log("top vivos: " + p.name() + " terminó de verdad (última " + (ultima == null ? "?" : ultima.id + ", finished=" + ultima.finished) + ")");
                        }
                    } catch (Exception ex) {
                        resultado.put(p.id(), vivoWatch.get(p.id()));   // sin respuesta: se conserva el punto
                        log("top vivos: confirmación de " + p.name() + " falló, punto conservado: " + causa(ex));
                    }
                    dormir(PAUSA_MS / 3);
                }
                int sinVerificar = top.size() - topVerificados.size();
                log("top vivos (lote): " + resultado.size() + " en partida de " + topVerificados.size() + " verificados"
                        + (sinVerificar > 0 ? " · " + sinVerificar + " sin verificar (lote fallido), estado conservado" : ""));
                return resultado;
            }
            @Override protected void done() {
                vigilandoTop = false;
                vigilarTwitch();   // el río acaba de enseñar canales: ahora sí, el cruce
                try {
                    Map<Long, Long> vivos = get();
                    for (Player p : top) {
                        if (!topVerificados.contains(p.id())) continue;   // lote fallido: ni quitar ni poner
                        Long v = vivos.get(p.id());
                        if (v != null) vivoWatch.put(p.id(), v);
                        else { vivoWatch.remove(p.id()); vivoInfo.remove(p.id()); VIVO_RIVAL.remove(p.id()); }   // el REST manda al quitar
                    }
                    boolean tablaTocada = false;
                    for (Match fresco : terminadasRio)
                        for (Match m : all)
                            if (m.id == fresco.id && m.finished == null) {
                                m.finished = fresco.finished;   // la EN DIRECTO de la tabla ya acabó
                                m.players = fresco.players;
                                tablaTocada = true;
                            }
                    if (tablaTocada) applyFilters();
                    else table.repaint();
                    actualizarIndicadoresVivos();
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    /** La cuenta hermana con MÁS ELO conocido que la propia, o null.
     *  Bebe de los vínculos guardados y de las familias consultadas. */
    String[] mejorAlt(long pid) {
        Integer propio = eloWatch.get(pid);
        Map<Long, String> cand = new HashMap<>();
        for (Player x : todosJugadores)
            if (x.id() == pid && x.vinculo() != 0)
                for (Player y : todosJugadores)
                    if (y.vinculo() == x.vinculo() && y.id() != pid) cand.put(y.id(), y.name());
        Map<Long, String> cc = FAMILIA_CACHE.get(pid);
        if (cc != null) cand.putAll(cc);
        long mejorId = 0; Integer mejorElo = null; String mejorNombre = null;
        for (Map.Entry<Long, String> e : cand.entrySet()) {
            Integer el = eloWatch.get(e.getKey());
            if (el == null) continue;
            if (mejorElo == null || el > mejorElo) { mejorElo = el; mejorId = e.getKey(); mejorNombre = e.getValue(); }
        }
        if (mejorId == 0 || mejorElo == null) return null;
        if (propio != null && mejorElo <= propio) return null;
        return new String[]{ mejorNombre, String.valueOf(mejorElo), String.valueOf(mejorId) };
    }

    /** Cuentas vinculadas de un perfil según el companion (linked_profiles):
     *  Object[]{ id (Long), nombre, país, partidas (Long) }. */
    static List<Object[]> cuentasVinculadas(long profileId) {
        List<Object[]> out = new ArrayList<>();
        try {
            Perfil pf = COMPANION.perfil(profileId);
            aprenderCanal(profileId, pf.canal());
            Set<Long> vistos = new HashSet<>();
            for (Perfil.Vinculada lp : pf.vinculadas()) {
                long id = lp.pid();
                if (id <= 0 || id == profileId || !vistos.add(id)) continue;   // el propio no es su vinculada
                String name = String.valueOf(lp.nombre());
                String c = lp.pais();
                String pais = c == null ? "" : c.toUpperCase();
                long games = lp.partidas();
                if (id > 0 && !"null".equals(name)) out.add(new Object[]{ id, name, pais, games });
            }
            if (!out.isEmpty()) {   // la familia consultada alimenta la señal \u21A5 de la sesión
                Map<Long, String> deEste = FAMILIA_CACHE.computeIfAbsent(profileId, k -> new HashMap<>());
                for (Object[] v : out) {
                    long vid = (Long) v[0]; String vn = (String) v[1];
                    deEste.put(vid, vn);
                    FAMILIA_CACHE.computeIfAbsent(vid, k -> new HashMap<>()).put(profileId, "\u2014");
                }
            }
        } catch (Exception ex) {
            log("vinculadas: fallo con perfil " + profileId + ": " + causa(ex));
        }
        return out;
    }

    /** Consulta las vinculadas de un seguido y casa las que YA sigues. */
    void vincularExistentes(Player p) {
        status.setText(t("Buscando cuentas vinculadas de ", "Looking up linked accounts of ") + p.name() + "…");
        new SwingWorker<List<Object[]>, Void>() {
            @Override protected List<Object[]> doInBackground() { return cuentasVinculadas(p.id()); }
            @Override protected void done() {
                List<Object[]> vinc;
                try { vinc = get(); } catch (Exception e) { vinc = List.of(); }
                Set<Long> familia = new HashSet<>();
                familia.add(p.id());
                for (Object[] v : vinc) if (containsPlayerId((Long) v[0])) familia.add((Long) v[0]);
                if (familia.size() < 2) {
                    status.setText(t("No sigues ninguna otra cuenta vinculada de ", "You don't follow any other linked account of ")
                            + p.name() + t(". Usa «Cuentas vinculadas…» para añadirlas.", ". Use “Linked accounts…” to add them."));
                    return;
                }
                marcarVinculo(familia);
                savePlayers();
                aplicarFiltroGrupo();
                status.setText(t("Vinculadas ", "Linked ") + familia.size()
                        + t(" cuentas: ahora comparten fila en la lista.", " accounts: they now share a row in the list."));
            }
        }.execute();
    }

    /** Menú contextual de la watchlist y los tops, en cuatro bloques: en partida · perfil · watchlist · edición. */
    void menuContextualWatchlist(Player p, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        long pid = p.id(); String nombre = nombreVisible(pid, p.name());
        // ---- 1. en partida ahora
        if (vivoWatch.containsKey(pid)) {
            Match m = VIVO_PARTIDA.get(pid);
            JMenuItem cab = new JMenuItem(t("En partida ahora", "In a game now") + (m != null && m.map != null ? " · " + m.map : "") + (m != null && m.started != null ? " · " + reloj(Duration.between(m.started, Instant.now())) : ""));
            cab.setEnabled(false); cab.setFont(cab.getFont().deriveFont(Font.BOLD));
            menu.add(cab);
            JMenuItem esp = new JMenuItem(t("Espectar en directo", "Spectate live"));
            esp.addActionListener(a -> espectar(p));
            menu.add(esp);
            if (rutaCaptureAge() != null) {
                JMenuItem espCa = new JMenuItem(t("Espectar con CaptureAge", "Spectate with CaptureAge"));
                espCa.addActionListener(a -> { lanzarCaptureAge(null); espectar(p); });
                menu.add(espCa);
            }
            MatchPlayer yo = null; if (m != null) for (MatchPlayer mp : m.players) if (mp.id == pid) yo = mp;
            if (m != null && yo != null) {
                List<MatchPlayer> aliados = new ArrayList<>(), rivales = new ArrayList<>();
                for (MatchPlayer mp : m.players) { if (mp.id == pid) continue; if (mp.team == yo.team) aliados.add(mp); else rivales.add(mp); }
                if (m.players.size() == 2 && rivales.size() == 1) menu.add(submenuJugadorPartida(rivales.get(0), t("Rival: ", "Opponent: ")));
                else {
                    if (!aliados.isEmpty()) { JMenu al = new JMenu(t("Aliados", "Allies")); for (MatchPlayer mp : aliados) al.add(submenuJugadorPartida(mp, "")); menu.add(al); }
                    JMenu rv = new JMenu(t("Rivales", "Opponents")); for (MatchPlayer mp : rivales) rv.add(submenuJugadorPartida(mp, "")); menu.add(rv);
                }
            } else {
                Object[] riv = VIVO_RIVAL.get(pid);
                if (riv != null) { JMenu rivalMenu = menuDeJugador((Long) riv[0], (String) riv[1]); rivalMenu.setText(t("Rival: ", "Opponent: ") + riv[1]); menu.add(rivalMenu); }
            }
            menu.addSeparator();
        }
        // ---- 2. perfil
        JMenuItem perf = new JMenuItem(t("Perfil", "Profile"));
        perf.addActionListener(a -> abrirPerfil(pid, nombre));
        menu.add(perf);
        JMenuItem perfN = new JMenuItem(t("Perfil en pestaña nueva", "Profile in a new tab"));
        perfN.addActionListener(a -> abrirPerfilEnPestana(pid, nombre));
        menu.add(perfN);
        menu.add(menuPerfilNavegador(pid));
        if (twitchLive.containsKey(pid)) {
            JMenuItem tw = new JMenuItem(t("Ver directo en Twitch", "Watch live on Twitch"));
            tw.addActionListener(a -> abrirUrl("https://twitch.tv/" + twitchLive.get(pid)[0]));
            menu.add(tw);
        }
        menu.addSeparator();
        // ---- 3. watchlist
        if (modoTop()) {
            boolean yaSeguido = todosJugadores.stream().anyMatch(x -> x.id() == pid);
            if (yaSeguido) {
                JMenuItem quitarW = new JMenuItem(t("Quitar de mi watchlist", "Remove from my watchlist"));
                quitarW.addActionListener(a -> quitarDeWatchlist(pid));
                menu.add(quitarW);
            } else {
                JMenu anadir = new JMenu(t("Añadir a mi watchlist", "Add to my watchlist"));
                Set<String> gsTop = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                gsTop.add(GRUPO_GENERAL);
                for (Player x : todosJugadores) gsTop.add(x.grupo());
                gsTop.addAll(gruposConfig());
                for (String g : gsTop) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> ficharDesdeTop(p, g)); anadir.add(it); }
                anadir.addSeparator();
                JMenuItem nuevoGT = new JMenuItem(t("+ Nuevo grupo\u2026", "+ New group\u2026"));
                nuevoGT.addActionListener(a -> { String g = elegirGrupoDialog(p.name()); if (g != null) ficharDesdeTop(p, g); });
                anadir.add(nuevoGT);
                menu.add(anadir);
                List<Player> selTop = playersList.getSelectedValuesList();
                if (selTop.size() > 1 && selTop.contains(p)) {   // varios seleccionados: ficharlos todos de golpe
                    JMenu anadirVarios = new JMenu(t("Añadir los ", "Add the ") + selTop.size() + t(" seleccionados a", " selected to"));
                    for (String g : gsTop) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> ficharVarios(selTop, g)); anadirVarios.add(it); }
                    anadirVarios.addSeparator();
                    JMenuItem nuevoGV = new JMenuItem(t("+ Nuevo grupo\u2026", "+ New group\u2026"));
                    nuevoGV.addActionListener(a -> { String g = elegirGrupoDialog(selTop.size() + t(" jugadores", " players")); if (g != null) ficharVarios(selTop, g); });
                    anadirVarios.add(nuevoGV);
                    menu.add(anadirVarios);
                }
            }
        } else {
            JMenu mover = new JMenu(t("Mover a grupo", "Move to group"));
            Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            gs.add(GRUPO_GENERAL);
            gs.addAll(gruposConfig());
            for (Player x : todosJugadores) gs.add(x.grupo());
            for (String g : gs) { if (g.equalsIgnoreCase(p.grupo())) continue; JMenuItem it = new JMenuItem(g); it.addActionListener(a -> moverJugador(p, g)); mover.add(it); }
            JMenuItem nuevoG = new JMenuItem(t("Nuevo grupo…", "New group…"));
            nuevoG.addActionListener(a -> {
                String nombreG = JOptionPane.showInputDialog(SpoilerFreeRecs.this, t("Nombre del grupo nuevo:", "New group name:"), t("Nuevo grupo", "New group"), JOptionPane.PLAIN_MESSAGE);
                if (nombreG != null && !nombreG.isBlank()) { String limpio = limpiarGrupo(nombreG); registrarGrupo(limpio); moverJugador(p, limpio); }
            });
            if (mover.getItemCount() > 0) mover.addSeparator();
            mover.add(nuevoG);
            menu.add(mover);
            JMenuItem quitar = new JMenuItem(t("Quitar de la Watchlist", "Remove from Watchlist"));
            quitar.addActionListener(a -> { playersModel.removeElement(p); todosJugadores.removeIf(x -> x.id() == pid); savePlayers(); rebuildGrupos(); actualizarIndicadoresVivos(); });
            menu.add(quitar);
            List<Player> selW = playersList.getSelectedValuesList();
            if (selW.size() > 1 && selW.contains(p)) {   // varios seleccionados: mover o quitar de golpe
                JMenu moverVarios = new JMenu(t("Mover los ", "Move the ") + selW.size() + t(" seleccionados a", " selected to"));
                for (String g : gruposExistentes()) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> moverVarios(selW, g)); moverVarios.add(it); }
                moverVarios.addSeparator();
                JMenuItem nuevoGM = new JMenuItem(t("+ Nuevo grupo\u2026", "+ New group\u2026"));
                nuevoGM.addActionListener(a -> { String g = elegirGrupoDialog(selW.size() + t(" jugadores", " players")); if (g != null) moverVarios(selW, g); });
                moverVarios.add(nuevoGM);
                menu.add(moverVarios);
                JMenuItem quitarVarios = new JMenuItem(t("Quitar los ", "Remove the ") + selW.size() + t(" seleccionados del grupo", " selected from the group"));
                quitarVarios.addActionListener(a -> {
                    Set<Long> ids = new HashSet<>(); for (Player x : selW) ids.add(x.id());
                    todosJugadores.removeIf(x -> ids.contains(x.id()));
                    savePlayers(); rebuildGrupos(); aplicarFiltroGrupo();
                    status.setText(ids.size() + t(" jugadores quitados.", " players removed."));
                });
                menu.add(quitarVarios);
            }
        }
        JMenuItem vinc = new JMenuItem(t("Cuentas vinculadas…", "Linked accounts…"));
        vinc.addActionListener(a -> mostrarVinculadas(pid, p.name()));
        menu.add(vinc);
        menu.addSeparator();
        // ---- 4. edición
        JMenuItem alias = new JMenuItem(t("Mostrar como…", "Show as…"));
        alias.addActionListener(a -> pedirAlias(pid, p.name()));
        menu.add(alias);
        JMenuItem nota = new JMenuItem(t("Nota…", "Note…"));
        nota.addActionListener(a -> pedirNota(pid, p.name()));
        menu.add(nota);
        if (notaDe(pid) != null) { JMenuItem bn = new JMenuItem(t("Borrar nota", "Delete note")); bn.addActionListener(a -> borrarNota(pid, p.name())); menu.add(bn); }
        JMenuItem nicks = new JMenuItem(t("Nicks anteriores…", "Previous names…"));
        nicks.addActionListener(a -> nicksAnteriores(pid, p.name()));
        menu.add(nicks);
        menu.show(playersList, e.getX(), e.getY());
    }

    /** Un jugador de la partida en curso, como submenú: perfil, pestaña nueva, añadir a la watchlist. */
    JMenu submenuJugadorPartida(MatchPlayer mp, String prefijo) {
        String nombre = nombreVisible(mp.id, mp.name);
        Integer e1 = elo1v1Conocido(mp.id);
        JMenu sub = new JMenu(prefijo + nombre + (e1 != null ? "  1v1 " + e1 : "") + (mp.civ != null && !mp.civ.isBlank() ? "  ·  " + mp.civ : ""));
        sub.setIcon(iconoBandera(paisDe(mp.id)));
        if (e1 == null && !ELO_1V1.containsKey(mp.id)) new Thread(() -> { Integer e = eloDeLadder(mp.id); ELO_1V1.put(mp.id, e == null ? 0 : e); if (e != null && e > 0) SwingUtilities.invokeLater(() -> sub.setText(prefijo + nombre + "  1v1 " + e + (mp.civ != null && !mp.civ.isBlank() ? "  ·  " + mp.civ : ""))); }, "elo-1v1").start();
        JMenuItem perf = new JMenuItem(t("Perfil", "Profile")); perf.addActionListener(a -> abrirPerfil(mp.id, nombre)); sub.add(perf);
        JMenuItem perfN = new JMenuItem(t("Perfil en pestaña nueva", "Profile in a new tab")); perfN.addActionListener(a -> abrirPerfilEnPestana(mp.id, nombre)); sub.add(perfN);
        if (!containsPlayerId(mp.id)) {
            JMenu anadir = new JMenu(t("Añadir a mi watchlist", "Add to my watchlist"));
            Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); gs.add(GRUPO_GENERAL); for (Player x : todosJugadores) gs.add(x.grupo()); gs.addAll(gruposConfig());
            for (String g : gs) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> ficharDesdeTop(new Player(mp.id, mp.name, g), g)); anadir.add(it); }
            sub.add(anadir);
        }
        return sub;
    }

    JMenu menuPerfilNavegador(long id) {
        JMenu m = new JMenu(t("Ver perfil en el navegador", "View profile in browser"));
        JMenuItem comp = new JMenuItem("aoe2companion");
        comp.addActionListener(a -> abrirUrl("https://www.aoe2companion.com/players/" + id));
        JMenuItem ins = new JMenuItem("aoe2insights");
        ins.addActionListener(a -> abrirUrl("https://www.aoe2insights.com/user/" + id + "/"));
        m.add(comp);
        m.add(ins);
        return m;
    }

    void abrirUrl(String url) {
        try { Desktop.getDesktop().browse(URI.create(url)); }
        catch (Exception ex) { status.setText(t("No se pudo abrir el navegador: ", "Couldn't open the browser: ") + causa(ex)); }
    }

    /** Casa un conjunto de cuentas bajo la misma familia (clave = menor id). */
    /** Un vinculo que agrupa a UNA sola cuenta es un fantasma (p. ej. de
     *  cuando el companion devolvía al propio jugador como vinculada). */
    void sanearVinculosHuerfanos() {
        Map<Long, Integer> cuenta = new HashMap<>();
        for (Player p : todosJugadores)
            if (p.vinculo() != 0) cuenta.merge(p.vinculo(), 1, Integer::sum);
        boolean cambio = false;
        for (int i = 0; i < todosJugadores.size(); i++) {
            Player p = todosJugadores.get(i);
            if (p.vinculo() != 0 && cuenta.getOrDefault(p.vinculo(), 0) < 2) {
                todosJugadores.set(i, new Player(p.id(), p.name(), p.grupo(), 0));
                cambio = true;
            }
        }
        if (cambio) savePlayers();
    }

    void marcarVinculo(Set<Long> ids) {
        if (ids.size() < 2) return;
        long clave = ids.stream().mapToLong(Long::longValue).min().orElse(0L);
        boolean cambio = false;
        for (int i = 0; i < todosJugadores.size(); i++) {
            Player x = todosJugadores.get(i);
            if (ids.contains(x.id()) && x.vinculo() != clave) {
                todosJugadores.set(i, new Player(x.id(), x.name(), x.grupo(), clave));
                cambio = true;
            }
        }
        if (cambio) { savePlayers(); aplicarFiltroGrupo(); }   // el vínculo sobrevive al cierre
    }

    /** Amplía la lista con todas las cuentas de cada familia presente. */
    /** Suma a la lista todas las cuentas vinculadas de cada jugador — salvo
     *  las hijas seleccionadas explícitamente, que van solas (control fino). */
    List<Player> conFamilias(List<Player> base) {
        Map<Long, Player> out = new LinkedHashMap<>();
        for (Player p : base) out.putIfAbsent(p.id(), p);
        for (Player p : base)
            if (p.vinculo() != 0 && marcaFila.getOrDefault(p.id(), ' ') != 'H')
                for (Player x : todosJugadores)
                    if (x.vinculo() == p.vinculo()) out.putIfAbsent(x.id(), x);
        return new ArrayList<>(out.values());
    }

    /** Tooltip de la columna Jugador: si la cuenta es hermana de una familia,
     *  dice de quién. Null si no aplica. */
    String tipCuentaVinculada(Match m) {
        String ref = refNombre(m);
        for (Player x : todosJugadores)
            if (x.name().equals(ref) && x.vinculo() != 0) {
                Player matriz = null;
                Integer mejor = null;
                for (Player y : todosJugadores)
                    if (y.vinculo() == x.vinculo()) {
                        Integer e = eloWatch.get(y.id());
                        if (matriz == null || (e != null && (mejor == null || e > mejor))) { matriz = y; mejor = e; }
                    }
                if (matriz != null && matriz.id() != x.id())
                    return t("Cuenta vinculada de ", "Linked account of ") + matriz.name();
                return null;
            }
        return null;
    }

    boolean familiaViva(List<Player> vis, long vinculo) {
        for (Player x : vis)
            if (x.vinculo() == vinculo && vivoWatch.containsKey(x.id())) return true;
        return false;
    }

    void quitarDeWatchlist(long id) {
        todosJugadores.removeIf(x -> x.id() == id);
        savePlayers();
        aplicarFiltroGrupo();
        status.setText(t("Quitado de tu watchlist.", "Removed from your watchlist."));
    }

    String grupoDeJugador(long id) {
        for (Player x : todosJugadores) if (x.id() == id) return x.grupo();
        return null;
    }

    /** ¿Es un fondo (no un control) donde un clic debe soltar la selección de la lista? */
    boolean esFondoDeseleccionable(Component c) {
        if (c instanceof AbstractButton || c instanceof javax.swing.text.JTextComponent || c instanceof JComboBox
                || c instanceof JList || c instanceof JTable || c instanceof javax.swing.table.JTableHeader
                || c instanceof JScrollBar || c instanceof JSpinner || c instanceof JMenuBar || c instanceof JPopupMenu
                || c instanceof JSplitPane || c instanceof javax.swing.plaf.basic.BasicSplitPaneDivider || c instanceof JProgressBar)
            return false;
        if (c instanceof JLabel l && l.getMouseListeners().length > 0) return false;   // etiquetas clicables (firma, sujetos…)
        if (cabLabel != null && SwingUtilities.isDescendingFrom(c, cabLabel)) return false;          // ordenar no suelta
        if (sujetosPanel != null && SwingUtilities.isDescendingFrom(c, sujetosPanel)) return false;  // «Partidas de:» es clicable
        if (table != null && SwingUtilities.isDescendingFrom(c, table)) return false;
        if (tablaDirectos != null && SwingUtilities.isDescendingFrom(c, tablaDirectos)) return false;
        if (ladderPanel != null && SwingUtilities.isDescendingFrom(c, ladderPanel)) return false;   // mirar las campanas no suelta la selección (sus puntos desaparecerían)
        // los visores de las tablas (hueco bajo sus filas) tampoco: seleccionar partidas no debe cambiar el filtro de la lista
        for (Component p = c; p != null; p = p.getParent())
            if (p instanceof JScrollPane sp && sp.getViewport() != null
                    && (sp.getViewport().getView() == table || sp.getViewport().getView() == tablaDirectos)) return false;
        return c instanceof JPanel || c instanceof JViewport || c instanceof JLabel || c instanceof JRootPane
                || c instanceof JLayeredPane || c instanceof JFrame;
    }

    boolean containsPlayerId(long id) {
        for (Player x : todosJugadores) if (x.id() == id) return true;
        return false;
    }

    /** ¿El punto cae sobre el TEXTO del nick o del título (no el blanco)?
     *  El ancho se mide con la fuente real de cada línea. */
    boolean sobreNombreCanal(JTable tabla, Point p) {
        int fila = tabla.rowAtPoint(p), col = tabla.columnAtPoint(p);
        if (fila < 0 || col < 0 || tabla.convertColumnIndexToModel(col) != 0) return false;
        int i = tabla.convertRowIndexToModel(fila);
        if (i >= filasDir.size()) return false;
        String[] d = filasDir.get(i);
        Rectangle celda = tabla.getCellRect(fila, col, true);
        boolean lineaNick = p.y - celda.y <= tabla.getRowHeight() * 0.55;
        Font base = tabla.getFont();
        Font f = lineaNick ? base.deriveFont(Font.BOLD, 13f) : base;
        int ancho = tabla.getFontMetrics(f).stringWidth(lineaNick ? d[1] : d[2]) + 10;
        int dx = p.x - celda.x;
        return dx <= MINI_OFFSET + ancho;   // la miniatura y el texto son clicables; el hueco a la derecha, no
    }

    void abrirCanalEn(JTable tabla, Point p) {
        int fila = tabla.rowAtPoint(p);
        if (fila < 0) return;
        int i = tabla.convertRowIndexToModel(fila);
        if (i < filasDir.size()) abrirUrl("https://twitch.tv/" + filasDir.get(i)[0]);
    }

    /** Historial de alias que guarda Steam para la cuenta (endpoint público
     *  de la comunidad, vía el steamId del companion). Solo bajo demanda. */
    void nicksAnteriores(long pid, String nombre) {
        status.setText(t("Consultando nicks anteriores de ", "Looking up previous names of ") + nombre + "\u2026");
        new SwingWorker<List<String[]>, Void>() {
            String motivo;
            @Override protected List<String[]> doInBackground() {
                try {
                    String sid = COMPANION.perfil(pid).steamId();
                    String steamId = sid == null ? "" : sid.trim();
                    if (steamId.isBlank() || "null".equals(steamId)) {
                        motivo = t("Esta cuenta no tiene Steam vinculado en el companion: sin historial disponible.",
                                   "This account has no Steam link on the companion: no history available.");
                        return null;
                    }
                    Object aliases = Json.parse(httpText429(
                            "https://steamcommunity.com/profiles/" + steamId + "/ajaxaliases"));
                    List<String[]> out = new ArrayList<>();
                    if (aliases instanceof List<?> l)
                        for (Object o : l) {
                            Map<String, Object> a = obj(o);
                            String n = String.valueOf(firstNonNull(val(a, "newname"), ""));
                            String cuando = String.valueOf(firstNonNull(val(a, "timechanged"), ""));
                            if (!n.isBlank()) out.add(new String[]{ n, cuando });
                        }
                    return out;
                } catch (Exception ex) {
                    motivo = t("No se pudo consultar el historial (perfil de Steam privado o servicio caído).",
                               "Could not fetch the history (private Steam profile or service down).");
                    return null;
                }
            }
            @Override protected void done() {
                status.setText(t("Listo.", "Ready."));
                List<String[]> alias;
                try { alias = get(); } catch (Exception e) { alias = null; }
                if (alias == null) {
                    JOptionPane.showMessageDialog(SpoilerFreeRecs.this, motivo,
                            t("Nicks anteriores", "Previous names"), JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                if (alias.isEmpty()) {
                    JOptionPane.showMessageDialog(SpoilerFreeRecs.this,
                            nombre + t(" no tiene cambios de nombre registrados en Steam.",
                                       " has no name changes recorded on Steam."),
                            t("Nicks anteriores", "Previous names"), JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                StringBuilder sb = new StringBuilder("<html><b>")
                        .append(t("Nicks anteriores de ", "Previous names of "))
                        .append(escapeHtml(nombre)).append("</b><br><br>");
                for (String[] a : alias) {
                    sb.append(escapeHtml(a[0]));
                    if (!a[1].isBlank()) sb.append("&nbsp;&nbsp;<font color='#8a8a8a'>").append(escapeHtml(a[1])).append("</font>");
                    sb.append("<br>");
                }
                sb.append("<br><font color='#8a8a8a'>")
                  .append(t("Fuente: historial público de Steam (últimos cambios).",
                            "Source: Steam public history (latest changes)."))
                  .append("</font></html>");
                JOptionPane.showMessageDialog(SpoilerFreeRecs.this, sb.toString(),
                        t("Nicks anteriores", "Previous names"), JOptionPane.PLAIN_MESSAGE);
            }
        }.execute();
    }

    /** Submenú de acciones sobre un jugador concreto (contextual de la tabla). */
    JMenu menuDeJugador(long pid, String nombre) {
        String vis = nombre.length() > 28 ? nombre.substring(0, 27) + "\u2026" : nombre;
        JMenu mj = new JMenu(vis);
        JMenuItem perfNueva = new JMenuItem(t("Abrir perfil en pestaña nueva", "Open profile in a new tab"));
        perfNueva.addActionListener(a -> abrirPerfilEnPestana(pid, nombre));
        mj.add(perfNueva);
        JMenu enPartida = menuEnPartida(pid);
        if (enPartida != null) mj.add(enPartida);
        JMenuItem alias = new JMenuItem(t("Mostrar como\u2026", "Show as\u2026"));
        alias.addActionListener(a -> pedirAlias(pid, nombre));
        mj.add(alias);
        JMenuItem nota = new JMenuItem(t("Nota\u2026", "Note\u2026"));
        nota.addActionListener(a -> pedirNota(pid, nombre));
        mj.add(nota);
        if (notaDe(pid) != null) {
            JMenuItem bn = new JMenuItem(t("Borrar nota", "Delete note"));
            bn.addActionListener(a -> borrarNota(pid, nombre));
            mj.add(bn);
        }
        JMenuItem perf = new JMenuItem(t("Perfil completo\u2026", "Full profile\u2026"));
        perf.addActionListener(a -> abrirPerfil(pid, nombre));
        mj.add(perf);
        boolean ya = containsPlayerId(pid);
        JMenu anadir = new JMenu(t("Añadir a mi watchlist", "Add to my watchlist"));
        if (ya) {
            anadir.setEnabled(false);
            anadir.setToolTipText(t("Ya está en tu watchlist", "Already in your watchlist"));
        } else {
            Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            gs.add(GRUPO_GENERAL);
            for (Player x : todosJugadores) gs.add(x.grupo());
            gs.addAll(gruposConfig());
            for (String g : gs) {
                JMenuItem it = new JMenuItem(g);
                it.addActionListener(a -> {
                    todosJugadores.add(new Player(pid, nombre, g));
                    savePlayers();
                    rebuildGrupos();
                    aplicarFiltroGrupo();
                    refrescarWatchlist();
                    status.setText(nombre + t(" añadido a «", " added to \u201C") + g + "\u00bb.");
                    ofrecerVinculadasTrasAlta(pid, nombre, g);   // siempre que alguien entra en un grupo, se revisan sus cuentas vinculadas
                });
                anadir.add(it);
            }
            anadir.addSeparator();
            JMenuItem nuevoG = new JMenuItem(t("+ Nuevo grupo\u2026", "+ New group\u2026"));
            nuevoG.addActionListener(a -> {
                String g = elegirGrupoDialog(nombre);
                if (g == null) return;
                todosJugadores.add(new Player(pid, nombre, g));
                savePlayers(); rebuildGrupos(); aplicarFiltroGrupo(); refrescarWatchlist();
                status.setText(nombre + t(" añadido a «", " added to \u201C") + g + "\u00bb.");
                ofrecerVinculadasTrasAlta(pid, nombre, g);
            });
            anadir.add(nuevoG);
        }
        mj.add(anadir);
        JMenuItem vinc = new JMenuItem(t("Cuentas vinculadas\u2026", "Linked accounts\u2026"));
        vinc.addActionListener(a -> mostrarVinculadas(pid, nombre));
        mj.add(vinc);
        JMenuItem nicks = new JMenuItem(t("Nicks anteriores\u2026", "Previous names\u2026"));
        nicks.addActionListener(a -> nicksAnteriores(pid, nombre));
        mj.add(nicks);
        JMenu nav = new JMenu(t("Ver perfil en el navegador", "View profile in browser"));
        JMenuItem comp = new JMenuItem("aoe2companion");
        comp.addActionListener(a -> abrirUrl("https://www.aoe2companion.com/players/" + pid));
        JMenuItem ins = new JMenuItem("aoe2insights");
        ins.addActionListener(a -> abrirUrl("https://www.aoe2insights.com/user/" + pid + "/"));
        nav.add(comp);
        nav.add(ins);
        mj.add(nav);
        return mj;
    }

    /** La zona central alterna entre la tabla de recs y los directos. */
    void mostrarDirectos(boolean directos) {
        directosBtn.setSelected(directos);
        if (techTreeBtn != null && techTreeBtn.isSelected()) { techTreeBtn.setSelected(false); }
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        actividadAbierta = false;
        if (perfilBtn != null) perfilBtn.setSelected(false);
        ahoraAbierta = false; if (ahoraBtn != null) ahoraBtn.setSelected(false);
        if (splitPrincipal != null && ttDivisorPrevio >= 0) { SwingUtilities.invokeLater(() -> { if (norteWatchRef != null) { norteWatchRef.revalidate(); norteWatchRef.repaint(); } }); splitPrincipal.setDividerLocation(ttDivisorPrevio); splitPrincipal.setOneTouchExpandable(false); ttDivisorPrevio = -1; }
        if (directos) { taparResultados(); apagarForma(); }   // cambiar de pantalla apaga el modo consulta y la forma
        ((CardLayout) centroCards.getLayout()).show(centroCards, directos ? "directos" : "recs");
        if (!directos && all.isEmpty() && fetchWorker == null) mostrarGuiaVacia(true);   // sin partidas: la guía con su botón, no una tabla vacía
        registrarDestino(new Destino(directos ? "directos" : "recs", 0, null, null));
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        if (directos) {
            poblarDirectos();
            vigilarTwitch();   // refresco de cortesía al abrir
        }
    }

    JPanel construirPanelDirectos() {
        {
            JPanel cont = new JPanel(new BorderLayout(0, 8));
            cont.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            JPanel arriba = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
            directosContador = new JLabel(t("Buscando canales\u2026", "Finding channels\u2026"));
            directosContador.setFont(directosContador.getFont().deriveFont(Font.BOLD));
            arriba.add(directosContador);
            directosHora = new JLabel();
            directosHora.setEnabled(false);
            arriba.add(directosHora);
            arriba.add(new JLabel(t("Idioma:", "Language:")));
            idiomaDirCombo = new JComboBox<>();
            idiomaDirCombo.addActionListener(e -> {
                if (rearmandoIdiomas) return;
                int i = idiomaDirCombo.getSelectedIndex();
                guardarConfig("twitch_idioma",
                        i <= 0 || i - 1 >= codigosIdiomaDir.size() ? "" : codigosIdiomaDir.get(i - 1));
                poblarDirectos();
            });
            arriba.add(idiomaDirCombo);
            JButton refrescarDir = new JButton(t("Refrescar", "Refresh"));
            refrescarDir.addActionListener(e -> vigilarTwitch());
            arriba.add(refrescarDir);
            JLabel ayudaDir = new JLabel(t("Clic en el nombre de un canal = abrir su directo en Twitch.",
                    "Click a channel name to open its Twitch stream."));
            ayudaDir.setEnabled(false);
            JPanel norte = new JPanel(new BorderLayout());
            norte.add(arriba, BorderLayout.NORTH);
            norte.add(ayudaDir, BorderLayout.SOUTH);
            cont.add(norte, BorderLayout.NORTH);
            modeloDirectos = new javax.swing.table.DefaultTableModel(
                    new Object[]{ t("Canal", "Channel"), t("Idioma", "Language"),
                            t("Espectadores", "Viewers") }, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
                @Override public Class<?> getColumnClass(int c) {
                    return c == 2 ? Integer.class : String.class;
                }
            };
            JTable tabla = new JTable(modeloDirectos) {
                @Override public String getToolTipText(MouseEvent e) {
                    int fila = rowAtPoint(e.getPoint());
                    if (fila < 0) return null;
                    int i = convertRowIndexToModel(fila);
                    return i < filasDir.size() ? "twitch.tv/" + filasDir.get(i)[0] : null;
                }
            };
            tabla.setAutoCreateRowSorter(true);
            tablaDirectos = tabla;
            // Dos líneas (nick 13px + título) caben con cualquier tamaño de letra; la miniatura fija el mínimo
            tabla.setRowHeight(Math.max((int) (tabla.getFont().getSize2D() * 2f) + 22, MINI_H + 8));
            tabla.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
                @Override public Component getTableCellRendererComponent(JTable tb, Object value, boolean sel, boolean foc, int row, int column) {
                    JLabel l = (JLabel) super.getTableCellRendererComponent(tb, value, sel, foc, row, column);
                    int mr = tb.convertRowIndexToModel(row);
                    ImageIcon ic = mr >= 0 && mr < filasDir.size() ? minis.get(filasDir.get(mr)[0]) : null;
                    l.setIcon(ic != null ? ic : miniPlaceholder());
                    l.setIconTextGap(10);
                    return l;
                }
            });
            tabla.addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    tabla.setCursor(Cursor.getPredefinedCursor(sobreNombreCanal(tabla, e.getPoint())
                            ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            });
            tabla.getColumnModel().getColumn(0).setPreferredWidth(430);
            tabla.getColumnModel().getColumn(1).setPreferredWidth(120);
            tabla.getColumnModel().getColumn(2).setPreferredWidth(100);
            javax.swing.table.DefaultTableCellRenderer centro = new javax.swing.table.DefaultTableCellRenderer();
            centro.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            tabla.getColumnModel().getColumn(1).setCellRenderer(centro);
            javax.swing.table.DefaultTableCellRenderer miles = new javax.swing.table.DefaultTableCellRenderer() {
                @Override protected void setValue(Object v) {
                    setHorizontalAlignment(CENTER);
                    setText(v instanceof Integer n
                            ? String.format(java.util.Locale.forLanguageTag(IDIOMA), "%,d", n)
                            : String.valueOf(v));
                }
            };
            tabla.getColumnModel().getColumn(2).setCellRenderer(miles);
            tabla.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    // Un clic sobre el nombre del canal; doble clic, en cualquier parte
                    boolean sobre = sobreNombreCanal(tabla, e.getPoint());
                    if ((e.getClickCount() == 1 && sobre) || (e.getClickCount() == 2 && !sobre))
                        abrirCanalEn(tabla, e.getPoint());
                }
            });
            cont.add(new JScrollPane(tabla), BorderLayout.CENTER);
            panelDirectos = cont;
            return cont;
        }
    }

    void poblarDirectos() {
        if (modeloDirectos == null) return;
        String idiomaSel = leerConfig("twitch_idioma", "");
        Set<String> idiomas = new TreeSet<>();
        for (String[] d : directosAoE2) idiomas.add(d[3]);
        rearmandoIdiomas = true;
        idiomaDirCombo.removeAllItems();
        codigosIdiomaDir.clear();
        idiomaDirCombo.addItem(t("Todos", "All"));
        for (String c : idiomas) { codigosIdiomaDir.add(c); idiomaDirCombo.addItem(nombreIdioma(c)); }
        int sel = codigosIdiomaDir.indexOf(idiomaSel);
        if (sel >= 0) idiomaDirCombo.setSelectedIndex(sel + 1);
        rearmandoIdiomas = false;
        modeloDirectos.setRowCount(0);
        filasDir.clear();
        List<String[]> filas = new ArrayList<>(directosAoE2);
        filas.sort((a, b) -> Integer.parseInt(b[4]) - Integer.parseInt(a[4]));
        String grisTit = temaOscuroActivo ? "#9a9a9a" : "#666666";
        for (String[] d : filas) {
            if (!idiomaSel.isBlank() && !d[3].equalsIgnoreCase(idiomaSel)) continue;
            String titulo = d[2].length() > 90 ? d[2].substring(0, 89) + "\u2026" : d[2];
            String celda = "<html><b style='font-size:13px'>" + escapeHtml(d[1]) + "</b><br>"
                    + "<span style='color:" + grisTit + "'>" + escapeHtml(titulo) + "</span></html>";
            modeloDirectos.addRow(new Object[]{ celda, nombreIdioma(d[3]), Integer.parseInt(d[4]) });
            filasDir.add(new String[]{ d[0], d[1], titulo });
        }
        cargarMiniaturas();
        long audiencia = 0;
        for (int i = 0; i < modeloDirectos.getRowCount(); i++)
            audiencia += ((Integer) modeloDirectos.getValueAt(i, 2));
        directosContador.setText(t("Top ", "Top ") + modeloDirectos.getRowCount() + t(" canales", " channels")
                + " \u00B7 " + String.format(t("es", "en").equals("es") ? java.util.Locale.of("es") : java.util.Locale.US, "%,d", audiencia)
                + " " + t("espectadores", "viewers"));
        directosHora.setText(t("Actualizado: ", "Updated: ")
                + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    /** Cruza los canales conocidos con la lista de directos de AoE2 del
     *  companion (una llamada global). Para el top sin canal vinculado,
     *  respaldo por coincidencia exacta de nick. */
    long ultimoTwitchMs, ultimoTopMs;
    volatile boolean twitchFallo;   // el último barrido de Twitch no obtuvo respuesta

    void vigilarTwitch() {
        if (vigilandoTwitch) return;
        if (System.currentTimeMillis() - ultimoTwitchMs < (long) (170_000 * ctrlMult("twitch_mult"))) return;   // cada 3 min (× mando a distancia): con eso basta para un badge
        ultimoTwitchMs = System.currentTimeMillis();
        vigilandoTwitch = true;
        twitchFallo = false;
        List<Player> visibles = new ArrayList<>();
        for (int i = 0; i < playersModel.size(); i++) visibles.add(playersModel.get(i));
        new SwingWorker<Map<Long, String[]>, Void>() {
            final List<String[]> completos = new ArrayList<>();
            @Override protected Map<Long, String[]> doInBackground() {
                Map<String, String[]> envivo = new HashMap<>();   // clave lower -> {canal, título, viewers}
                String h = CompanionApi.TWITCH_LIVE + "?game=13389";   // solo para el log: la URL real la construye COMPANION.twitchDirectos()
                try {
                    Object root = COMPANION.twitchDirectos();
                    List<Object> lista = root instanceof List<?> l ? new ArrayList<>(l)
                            : arr(val(obj(root), "data"));
                    for (Object o : lista) {
                        Map<String, Object> s = obj(o);
                        String canal = String.valueOf(val(s, "user_login", "userLogin"));
                        if ("null".equals(canal) || canal.isBlank()) continue;
                        String titulo = String.valueOf(firstNonNull(val(s, "title"), ""));
                        long viewers = lng(val(s, "viewer_count", "viewerCount"));
                        String[] datos = { canal, titulo, String.valueOf(Math.max(0, viewers)) };
                        envivo.put(canal.toLowerCase(), datos);
                        String un = String.valueOf(val(s, "user_name", "userName"));
                        if (!"null".equals(un) && !un.isBlank()) envivo.putIfAbsent(un.toLowerCase(), datos);
                        String idioma = String.valueOf(firstNonNull(val(s, "language"), "?"));
                        completos.add(new String[]{ canal, "null".equals(un) || un.isBlank() ? canal : un,
                                titulo, idioma.toLowerCase(), String.valueOf(Math.max(0, viewers)) });
                    }
                } catch (Exception ex) {
                    log("twitch: fallo con " + h + ": " + causa(ex));
                    twitchFallo = true;
                }
                Map<Long, String[]> res = new HashMap<>();
                List<Player> sinCruce = new ArrayList<>();
                for (Player p : visibles) {
                    String canal = CANAL_DE.get(p.id());
                    String[] st = canal != null ? envivo.get(canal.toLowerCase()) : null;
                    if (st == null)
                        for (String v : variantesNick(p.name())) {
                            st = envivo.get(v);
                            if (st != null) { aprenderCanal(p.id(), st[0]); break; }
                        }
                    if (st != null) res.put(p.id(), st);
                    else if (canal != null && !twitchFallo) sinCruce.add(p);
                }
                // El proxy solo lista los 20 canales más vistos: los canales conocidos de tu lista que no
                // estén ahí se comprueban uno a uno (pocas llamadas, y el TW deja de depender de la audiencia)
                int consultas = 0;
                for (Player p : sinCruce) {
                    if (consultas++ >= 12) break;
                    String canal = CANAL_DE.get(p.id());
                    try {
                        Object rootC = COMPANION.twitchCanal(canal);
                        Object primero = rootC instanceof List<?> lc ? (lc.isEmpty() ? null : lc.get(0)) : rootC;
                        if (primero != null) {
                            Map<String, Object> s = obj(primero);
                            String login = String.valueOf(val(s, "user_login", "userLogin"));
                            if (!"null".equals(login) && !login.isBlank() && "live".equals(String.valueOf(val(s, "type")))) {
                                String titulo = String.valueOf(firstNonNull(val(s, "title"), ""));
                                long viewers = lng(val(s, "viewer_count", "viewerCount"));
                                res.put(p.id(), new String[]{ login, titulo, String.valueOf(Math.max(0, viewers)) });
                            }
                        }
                    } catch (Exception ex) {
                        log("twitch canal " + canal + ": " + causa(ex));
                    }
                    dormir(150);
                }
                log("twitch: " + envivo.size() / 2 + "+ directos AoE2; " + res.size()
                        + " de tu lista visible retransmitiendo");
                return res;
            }
            @Override protected void done() {
                vigilandoTwitch = false;
                try {
                    Map<Long, String[]> res = get();
                    if (!twitchFallo) { twitchLive.clear(); twitchLive.putAll(res); }   // con fallo, los TW se conservan
                    if (twitchFallo) {
                        // sin respuesta (502…): se conserva la última lista buena
                        status.setText(t("Twitch sin respuesta ahora mismo — mostrando la última lista buena.",
                                "Twitch not responding right now — showing the last good list."));
                    } else {
                        directosAoE2.clear();
                        directosAoE2.addAll(completos);
                    }
                    playersList.repaint();
                    if (directosBtn != null && directosBtn.isSelected()) poblarDirectos();
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    /** Tras fichar a alguien: si tiene cuentas vinculadas, ofrecer añadirlas
     *  todas al mismo grupo de una vez. */
    void ofrecerVinculadasTrasAlta(long profileId, String nombre, String grupo) {
        new SwingWorker<List<Object[]>, Void>() {
            final Map<Long, Integer> elosV = new HashMap<>();
            @Override protected List<Object[]> doInBackground() {
                List<Object[]> vinc = cuentasVinculadas(profileId);
                for (Object[] v : vinc) {
                    Integer e = eloDeLadder((Long) v[0]);
                    if (e != null) elosV.put((Long) v[0], e);
                    dormir(PAUSA_MS / 2);
                }
                return vinc;
            }
            @Override protected void done() {
                List<Object[]> vinc;
                try { vinc = get(); } catch (Exception e) { vinc = List.of(); }
                List<Object[]> nuevas = new ArrayList<>();
                for (Object[] v : vinc) if (!containsPlayerId((Long) v[0])) nuevas.add(v);
                if (nuevas.isEmpty()) return;
                StringBuilder sb = new StringBuilder();
                for (Object[] v : nuevas) sb.append(sb.isEmpty() ? "" : ", ").append(v[1]);
                int r = JOptionPane.showConfirmDialog(SpoilerFreeRecs.this,
                        nombre + t(" tiene ", " has ") + nuevas.size()
                                + t(" cuentas vinculadas: ", " linked accounts: ") + sb
                                + t(".\n¿Añadirlas también al grupo «", ".\nAdd them to group “") + grupo
                                + t("»?", "” too?"),
                        t("Cuentas vinculadas", "Linked accounts"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (r != JOptionPane.YES_OPTION) return;
                Set<Long> familia = new HashSet<>();
                familia.add(profileId);
                for (Object[] v : nuevas) {
                    todosJugadores.add(new Player((Long) v[0], (String) v[1], grupo));
                    familia.add((Long) v[0]);
                }
                for (Object[] v : vinc) if (containsPlayerId((Long) v[0])) familia.add((Long) v[0]);
                eloWatch.putAll(elosV);
                marcarVinculo(familia);
                savePlayers();
                rebuildGrupos();
                aplicarFiltroGrupo();
                refrescarWatchlist();
                status.setText(nuevas.size() + t(" cuentas vinculadas añadidas a «", " linked accounts added to “")
                        + grupo + "\u00bb.");
            }
        }.execute();
    }

    /** Diálogo de cuentas vinculadas: selección múltiple y grupo de destino. */
    void mostrarVinculadas(long profileId, String nombre) {
        final Integer[] eloPropio = new Integer[1];
        status.setText(t("Buscando cuentas vinculadas de ", "Looking up linked accounts of ") + nombre + "…");
        new SwingWorker<List<Object[]>, Void>() {
            @Override protected List<Object[]> doInBackground() {
                List<Object[]> vinc = cuentasVinculadas(profileId);
                for (Object[] v : vinc) {   // ELO 1v1 actual de cada cuenta, del ladder
                    v[3] = new Object[]{ v[3], eloDeLadder((Long) v[0]) };
                    dormir(PAUSA_MS / 2);
                }
                if (!containsPlayerId(profileId)) eloPropio[0] = eloDeLadder(profileId);
                return vinc;
            }
            @Override protected void done() {
                List<Object[]> vinc;
                try { vinc = get(); } catch (Exception e) { vinc = List.of(); }
                if (vinc.isEmpty()) {
                    status.setText(t("Listo.", "Ready."));
                    JOptionPane.showMessageDialog(SpoilerFreeRecs.this,
                            nombre + t(" no tiene cuentas vinculadas conocidas.",
                                       " has no known linked accounts."),
                            t("Cuentas vinculadas", "Linked accounts"), JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                status.setText(t("Listo.", "Ready."));
                if (eloPropio[0] != null) eloWatch.put(profileId, eloPropio[0]);
                for (Object[] v : vinc) {   // el ELO consultado siembra la watchlist al momento
                    Object[] ex = (Object[]) v[3];
                    if (ex[1] instanceof Integer e) eloWatch.put((Long) v[0], e);
                }
                DefaultListModel<String> modelo = new DefaultListModel<>();
                List<Object[]> anadibles = new ArrayList<>();
                for (Object[] v : vinc) {
                    boolean ya = containsPlayerId((Long) v[0]);
                    Object[] extra = (Object[]) v[3];
                    Integer eloV = (Integer) extra[1];
                    String fila = ((String) v[2]).isBlank() ? String.valueOf(v[1]) : v[1] + " \u00B7 " + v[2];
                    fila = fila + (eloV != null ? " \u00B7 " + eloV + " ELO" : t(" \u00B7 sin ELO", " \u00B7 no ELO"));
                    if (extra[0] instanceof Long g && g >= 0)
                        fila = fila + " \u00B7 " + g + t(" partidas", " games");
                    fila = fila + (ya ? t("  (ya en tu watchlist)", "  (already in your watchlist)") : "");
                    modelo.addElement(fila);
                    if (!ya) anadibles.add(v);
                }
                JList<String> lista = new JList<>(modelo);
                lista.setFont(lista.getFont().deriveFont(lista.getFont().getSize2D() + 1f));
                lista.setVisibleRowCount(Math.min(8, modelo.size()));
                Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                gs.add(GRUPO_GENERAL);
                for (Player x : todosJugadores) gs.add(x.grupo());
                gs.addAll(gruposConfig());
                JComboBox<String> grupoDest = new JComboBox<>(gs.toArray(String[]::new));
                String ga = grupoActivo();
                if (ga != null) grupoDest.setSelectedItem(ga);
                JPanel panel = new JPanel(new BorderLayout(0, 8));
                panel.add(new JLabel(t("Cuentas vinculadas de ", "Linked accounts of ") + nombre + ":"),
                        BorderLayout.NORTH);
                panel.add(new JScrollPane(lista), BorderLayout.CENTER);
                JPanel abajo = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                abajo.add(new JLabel(t("Grupo destino:", "Target group:")));
                abajo.add(grupoDest);
                panel.add(abajo, BorderLayout.SOUTH);
                // Visor primero: mirar no compromete. Solo el botón guarda.
                String btnGuardar = t("Guardar en el grupo", "Save to group");
                String btnCerrar  = t("Cerrar", "Close");
                int r = JOptionPane.showOptionDialog(SpoilerFreeRecs.this, panel,
                        t("Cuentas vinculadas", "Linked accounts"), JOptionPane.DEFAULT_OPTION,
                        JOptionPane.PLAIN_MESSAGE, null,
                        new Object[]{ btnGuardar, btnCerrar }, btnCerrar);
                if (r != 0) return;   // Cerrar o Esc: nada se añade, nada se vincula
                String g = String.valueOf(grupoDest.getSelectedItem());
                int nuevos = 0;
                Set<Long> familia = new HashSet<>();
                familia.add(profileId);
                if (!containsPlayerId(profileId)) {   // la MATRIZ entra también (caso ★/buscador)
                    todosJugadores.add(new Player(profileId, nombre, g));
                    nuevos++;
                }
                int[] selIdx = lista.getSelectedIndices();
                List<Object[]> elegidos = new ArrayList<>();
                if (selIdx.length == 0) elegidos.addAll(anadibles);   // sin selección: todas las nuevas
                else {
                    int i = 0;
                    for (Object[] v : vinc) {
                        boolean ya = containsPlayerId((Long) v[0]);
                        for (int s : selIdx) if (s == i && !ya) elegidos.add(v);
                        i++;
                    }
                }
                for (Object[] v : elegidos)
                    if (!containsPlayerId((Long) v[0])) {
                        todosJugadores.add(new Player((Long) v[0], (String) v[1], g));
                        nuevos++;
                    }
                for (Object[] v : vinc) if (containsPlayerId((Long) v[0])) familia.add((Long) v[0]);
                if (nuevos > 0) {
                    savePlayers();
                    rebuildGrupos();
                }
                marcarVinculo(familia);   // persiste y repinta por sí mismo
                aplicarFiltroGrupo();
                refrescarWatchlist();
                status.setText(t("Familia de ", "Family of ") + nombre
                        + t(" guardada en «", " saved to “") + g + "\u00bb ("
                        + nuevos + t(" nuevas).", " new)."));
            }
        }.execute();
    }

    /** Panel con la mini gráfica del rating (termina 10 partidas atrás). */
    static class SparkPanel extends JPanel {
        final int[] datos;
        SparkPanel(int[] datos) { this(datos, 200, 44); }
        SparkPanel(int[] datos, int w, int h) {
            this.datos = datos;
            setPreferredSize(new Dimension(w, h));
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (datos == null || datos.length < 2) return;
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight() - 14;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int v : datos) { min = Math.min(min, v); max = Math.max(max, v); }
            if (max == min) max = min + 1;
            g.setColor(temaOscuroActivo ? new Color(0xFF, 0xC9, 0x4D) : new Color(0xB0, 0x78, 0x00));
            int n = datos.length;
            int px = -1, py = -1;
            for (int i = 0; i < n; i++) {
                int x = (int) Math.round(i * (w - 4) / (double) (n - 1)) + 2;
                int y = 4 + (int) Math.round((max - datos[i]) * (h - 8) / (double) (max - min));
                if (px >= 0) g.drawLine(px, py, x, y);
                px = x; py = y;
            }
            g.setFont(getFont().deriveFont(Font.PLAIN, 10f));
            g.setColor(UIManager.getColor("Label.disabledForeground") != null
                    ? UIManager.getColor("Label.disabledForeground") : Color.GRAY);
            g.drawString(String.valueOf(min), 2, getHeight() - 2);
            String sMax = String.valueOf(max);
            g.drawString(sMax, w - g.getFontMetrics().stringWidth(sMax) - 2, getHeight() - 2);
        }
    }

    /** ¿Es buen momento para enseñar la hover-card? Nunca sobre diálogos,
     *  menús abiertos o con el ratón ya fuera de la lista. */
    Component hoverAncla;   // componente sobre el que vive la tarjeta flotante (la watchlist por defecto; en Live now, el nick)
    boolean hoverProcede() {
        Component c = hoverAncla != null ? hoverAncla : playersList;
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == this
                && MenuSelectionManager.defaultManager().getSelectedPath().length == 0
                && c.isShowing() && c.getMousePosition() != null;
    }

    void ocultarHoverCard() { ocultarHoverCard(false); }

    void ocultarHoverCard(boolean forzar) {
        if (!forzar && cardFijada) return;   // la card del clic derecho no la mata pasear el ratón
        if (hoverTimer != null) hoverTimer.stop();
        if (hoverCard != null) { hoverCard.dispose(); hoverCard = null; }
        cardFijada = false;
        hoverPid = 0;
    }

    /** Muestra la tarjeta de perfil de un jugador en la posición dada. */
    void mostrarPerfilCard(long pid, String nombre, Point enPantalla) {
        mostrarPerfilCard(pid, nombre, enPantalla, false);
    }

    void mostrarPerfilCard(long pid, String nombre, Point enPantalla, boolean fijar) {
        Object[] cache = perfilCardCache.get(pid);
        long ahora = System.currentTimeMillis();
        if (cache != null && ahora - (Long) cache[0] < 10 * 60_000L) {
            pintarCard((String) cache[1], (int[]) cache[2], enPantalla, pid, fijar);
            return;
        }
        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() {
                String pais = "", clan = "";
                long games = 0;
                Integer rating = null, maxRating = null, wins = null, losses = null;
                try {
                    Perfil pf = COMPANION.perfil(pid);
                    aprenderCanal(pid, pf.canal());
                    String c = pf.pais();
                    aprenderPais(pid, c);
                    if (c != null && !"null".equals(c)) pais = c.toUpperCase();
                    String cl = pf.clan();
                    if (cl != null && !"null".equals(cl)) clan = cl;
                    games = pf.partidas();
                    for (Perfil.Ladder lb : pf.ladders()) {
                        String lid = String.valueOf(lb.id());
                        if (!"rm_1v1".equals(lid) && !"3".equals(lid)) continue;
                        if (lb.rating() != null) rating = lb.rating();
                        if (lb.ratingMax() != null) maxRating = lb.ratingMax();
                        if (lb.ganadas() != null) wins = lb.ganadas();
                        if (lb.perdidas() != null) losses = lb.perdidas();
                        break;
                    }
                } catch (Exception ex) {
                    log("perfil card: fallo con " + pid + ": " + causa(ex));
                }
                int[] spark = null;
                boolean pocos1v1 = false;
                try {
                    List<Integer> serie = new ArrayList<>();
                    for (int pag = 1; pag <= 2 && serie.size() <= 15; pag++) {
                        dormir(PAUSA_MS / 2);
                        Object root = COMPANION.matches(pid, pag, PER_PAGE);
                        List<Object> ms = arr(val(obj(root), "matches"));
                        if (ms.isEmpty()) break;
                        for (Object o : ms) {
                            Match m = parseMatch(obj(o));
                            if (m == null || m.finished == null || m.players.size() != 2
                                    || m.mode == null || !m.mode.startsWith("1v1 Random")) continue;
                            for (MatchPlayer mp : m.players)
                                if (mp.id == pid && mp.rating != null) serie.add(mp.rating);
                        }
                    }
                    if (serie.size() > 10) serie = serie.subList(10, serie.size());   // sin la forma fresca
                    if (serie.size() >= 4) {
                        Collections.reverse(serie);               // cronológico
                        spark = serie.stream().mapToInt(Integer::intValue).toArray();
                    } else pocos1v1 = true;
                } catch (Exception ex) {
                    log("perfil card: sparkline falló con " + pid + ": " + causa(ex));
                }
                StringBuilder h = new StringBuilder("<html><b>").append(escapeHtml(nombre)).append("</b>");
                if (!pais.isBlank()) h.append("  \u00B7 ").append(pais);
                if (!clan.isBlank()) h.append("  \u00B7 ").append(escapeHtml(clan));
                h.append("<br>");
                if (rating != null) h.append(t("ELO 1v1: <b>", "1v1 ELO: <b>")).append(rating).append("</b>");
                if (maxRating != null) h.append(t("  \u00B7 máx ", "  \u00B7 peak ")).append(maxRating);
                h.append("<br>");
                if (wins != null && losses != null && wins + losses > 0)
                    h.append(t("Winrate 1v1: ", "1v1 winrate: "))
                     .append(Math.round(wins * 100.0 / (wins + losses))).append("% (")
                     .append(wins + losses).append(t(" partidas)", " games)"));
                else if (games > 0) h.append(games).append(t(" partidas jugadas", " games played"));
                if (spark != null)
                    h.append("<br><font size='2' color='gray'>")
                     .append(t("Rating (hasta hace ~10 partidas):", "Rating (up to ~10 games ago):"))
                     .append("</font>");
                else if (pocos1v1)
                    h.append("<br><font size='2' color='gray'>")
                     .append(t("(pocos 1v1 recientes para la gráfica)", "(too few recent 1v1s for the chart)"))
                     .append("</font>");
                return new Object[]{ h.append("</html>").toString(), spark };
            }
            @Override protected void done() {
                try {
                    Object[] r = get();
                    perfilCardCache.put(pid, new Object[]{ System.currentTimeMillis(), r[0], r[1] });
                    if (fijar || ((hoverPid == pid || hoverPid == 0) && hoverProcede()))
                        pintarCard((String) r[0], (int[]) r[1], enPantalla, pid, fijar);
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    void pintarCard(String html, int[] spark, Point enPantalla, long pid, boolean fijar) {
        ocultarHoverCard(true);
        cardFijada = fijar;
        hoverPid = pid;
        hoverCard = new JWindow(this);
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") != null
                        ? UIManager.getColor("Component.borderColor") : Color.GRAY),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        p.add(new JLabel(html), BorderLayout.CENTER);
        if (spark != null) p.add(new SparkPanel(spark), BorderLayout.SOUTH);
        hoverCard.add(p);
        hoverCard.pack();
        hoverCard.setLocation(enPantalla.x + 14, enPantalla.y + 10);
        hoverCard.setVisible(true);
    }

    /** ELO 1v1 actual de un perfil, preguntando su fila del leaderboard. */
    static Integer eloDeLadder(long profileId) {
        try {   // la vía del perfil: la misma que usa el hover, probada
            Perfil pf = COMPANION.perfil(profileId);
            aprenderCanal(profileId, pf.canal());
            for (Perfil.Ladder lb : pf.ladders()) {
                String lid = String.valueOf(lb.id());
                if (!"rm_1v1".equals(lid) && !"3".equals(lid)) continue;
                if (lb.rating() != null) return lb.rating();
            }
        } catch (Exception ignored) { }
        return null;
    }


    void refrescarWatchlist() {
        if (modoTop()) return;   // el top se alimenta del leaderboard y del río
        List<Player> objetivo = new ArrayList<>();
        for (int i = 0; i < playersModel.size(); i++) {
            Player p = playersModel.get(i);
            if (watchBarridos.add(p.id())) objetivo.add(p);
        }
        if (objetivo.isEmpty()) return;
        new SwingWorker<Void, Object[]>() {
            @Override protected Void doInBackground() {
                cargarEloAyer();
                for (Player p : objetivo) {
                    int[] snap = ELO_AYER.get(p.id());
                    if (snap != null && snap[0] > 0) { gamesWatch.put(p.id(), snap[1]); publish(new Object[]{ p.id(), null, snap[0], null }); continue; }   // del snapshot nocturno: sin llamada (el socket dirá si está en partida)
                    try {
                        Iterable<Match> leidas = COMPANION.partidas(p.id(), 1, 10);
                        Long vivo = null;
                        String resV = null;
                        Integer elo = null;
                        for (Match m : leidas) {
                            if (m == null) continue;
                            if (enCursoReal(m) && vivo == null) { vivo = m.id; resV = resumenVivo(m, p.id()); }
                            if (elo == null && m.finished != null && m.players.size() == 2
                                    && m.mode != null && m.mode.startsWith("1v1 Random"))
                                for (MatchPlayer mp : m.players)
                                    if (mp.id == p.id() && mp.rating != null) { elo = mp.rating; break; }
                            if (vivo != null && elo != null) break;
                        }
                        if (elo == null) {   // sin 1v1 ranked entre sus últimas 10: mirar más atrás
                            dormir(PAUSA_MS);
                            Iterable<Match> leidasApi = COMPANION.partidas(p.id(), 1, PER_PAGE);
                            for (Match m : leidasApi) {
                                if (m == null || m.finished == null || m.players.size() != 2
                                        || m.mode == null || !m.mode.startsWith("1v1 Random")) continue;
                                for (MatchPlayer mp : m.players)
                                    if (mp.id == p.id() && mp.rating != null) { elo = mp.rating; break; }
                                if (elo != null) break;
                            }
                        }
                        publish(new Object[]{ p.id(), vivo, elo, resV });
                    } catch (Exception ex) {
                        log("watchlist: fallo con " + p.name() + ": " + causa(ex));
                    }
                    dormir(PAUSA_MS / 2);
                }
                return null;
            }
            @Override protected void process(List<Object[]> chunks) {
                for (Object[] c : chunks) {
                    long id = (Long) c[0];
                    if (c[1] != null) {
                        vivoWatch.put(id, (Long) c[1]);
                        if (c.length > 3 && c[3] != null) vivoInfo.put(id, (String) c[3]);
                    } else { vivoWatch.remove(id); vivoInfo.remove(id); VIVO_RIVAL.remove(id); }
                    if (c[2] != null) eloWatch.put(id, (Integer) c[2]);
                }
                actualizarIndicadoresVivos();
            }
        }.execute();
    }

    /** Vigilancia periódica de TODA la watchlist (todos los grupos): solo
     *  detecta quién está jugando ahora; el ELO no se toca (solo al abrir).
     *  Se salta el tick si hay otra tarea en marcha. */
    void vigilarVivos() {
        if (vigilando || progreso.isVisible() || todosJugadores.isEmpty()) return;
        vigilando = true;
        List<Player> objetivo = new ArrayList<>(todosJugadores);
        new SwingWorker<Void, Object[]>() {
            @Override protected Void doInBackground() {
                final int LOTE = 25;   // 2 llamadas para un top 50, 4 para el top 100
                for (int d = 0; d < objetivo.size(); d += LOTE) {
                    List<Player> lote = objetivo.subList(d, Math.min(d + LOTE, objetivo.size()));
                    Set<Long> idsLote = new HashSet<>();
                    StringBuilder csv = new StringBuilder();
                    for (Player p : lote) {
                        idsLote.add(p.id());
                        if (csv.length() > 0) csv.append(',');
                        csv.append(p.id());
                    }
                    try {
                        Iterable<Match> leidas = COMPANION.partidas(csv.toString(), 1, 50);
                        Map<Long, Long> vivos = new HashMap<>();
                        Map<Long, String> infos = new HashMap<>();
                        List<Match> terminadas = new ArrayList<>();
                        for (Match m : leidas) {
                            if (m == null) continue;
                            if (enCursoReal(m)) {
                                for (MatchPlayer mp : m.players)
                                    if (idsLote.contains(mp.id) && !vivos.containsKey(mp.id)) {
                                        vivos.put(mp.id, m.id);
                                        infos.put(mp.id, resumenVivo(m, mp.id));
                                    }
                            } else if (m.finished != null) terminadas.add(m);
                        }
                        publish(new Object[]{ idsLote, vivos, infos, terminadas });
                    } catch (Exception ex) {
                        log("vigilante: fallo con el lote " + (d / LOTE + 1) + ": " + causa(ex));
                    }
                    dormir(PAUSA_MS / 2);
                }
                return null;
            }
            @Override protected void process(List<Object[]> chunks) {
                boolean tablaTocada = false;
                for (Object[] c : chunks) {
                    @SuppressWarnings("unchecked") Set<Long> idsLote = (Set<Long>) c[0];
                    @SuppressWarnings("unchecked") Map<Long, Long> vivos = (Map<Long, Long>) c[1];
                    @SuppressWarnings("unchecked") Map<Long, String> infos = (Map<Long, String>) c[2];
                    for (Long id : idsLote) {
                        Long vm = vivos.get(id);
                        if (vm != null) { vivoWatch.put(id, vm); vivoInfo.put(id, infos.get(id)); }
                        else { vivoWatch.remove(id); vivoInfo.remove(id); VIVO_RIVAL.remove(id); }
                    }
                    @SuppressWarnings("unchecked")
                    List<Match> terminadas = (List<Match>) c[3];
                    for (Match fresco : terminadas)
                        for (Match m : all)
                            if (m.id == fresco.id && m.finished == null) {
                                m.finished = fresco.finished;   // la EN DIRECTO de la tabla acabó:
                                m.players = fresco.players;     // fecha real y resultado disponibles
                                tablaTocada = true;
                            }
                }
                if (tablaTocada) applyFilters();
                else table.repaint();   // p. ej. una viva que cruza el umbral de fantasma
                actualizarIndicadoresVivos();
            }
            @Override protected void done() { vigilando = false; }
        }.execute();
    }

    /** ¿Hay alguien jugando ahora en el grupo? (null = en cualquiera). */
    boolean grupoTieneVivo(String grupo) {
        for (Player p : todosJugadores)
            if ((grupo == null || p.grupo().equalsIgnoreCase(grupo)) && vivoWatch.containsKey(p.id()))
                return true;
        return false;
    }

    /** Repinta lista, combo de grupos y el título («Watchlist — N en directo»). */
    void refrescarAlturasWatch() {
        playersList.setFixedCellHeight(0);
        playersList.setFixedCellHeight(-1);   // fuerza re-medir alturas (sublíneas que aparecen o mueren)
    }

    void actualizarIndicadoresVivos() {
        refrescarAlturasWatch();   // las sublíneas nacen y mueren con los vivos, en toda vista
        playersList.repaint();
        grupoCombo.repaint();
        int nVivos = 0;
        for (Player p : todosJugadores) if (vivoWatch.containsKey(p.id())) nVivos++;
        String g = grupoActivo();
        int nAmbito = 0;
        for (Player p : (modoTop() ? topLadder : todosJugadores))
            if ((modoTop() || g == null || p.grupo().equalsIgnoreCase(g)) && vivoWatch.containsKey(p.id())) nAmbito++;
        if (soloVivosBtn != null)
            soloVivosBtn.setText("\u25CF " + t("Jugando", "Playing") + (nAmbito > 0 ? " (" + nAmbito + ")" : ""));
            soloVivosBtn.setToolTipText(null);   // sin tooltip: el chip se explica solo
            if (resumenWatch != null) {
                int totalAmbito = 0;
                for (int i = 0; i < playersModel.size(); i++) totalAmbito++;
                resumenWatch.setText(totalAmbito + t(" jugadores", " players") + " \u00B7 " + nAmbito + t(" jugando", " playing"));
            }
        tituloWatch.setTitle(nVivos == 0 ? "Watchlist"
                : "Watchlist \u2014 " + nVivos + t(" jugando", " playing"));
        if (soloVivosBtn != null && soloVivosBtn.isSelected()) aplicarFiltroGrupo();
        if (watchPanel != null) watchPanel.repaint();
    }

    boolean usarCA() { return Boolean.parseBoolean(leerConfig("usar_ca", "false")); }

    /** Lanza CaptureAge — con una rec (la reproduce con su overlay) o sin
     *  argumentos (modo acompañante: se engancha al juego al espectar). */
    void lanzarCaptureAge(Path rec) {
        Path ca = rutaCaptureAge();
        if (ca == null) {
            status.setText(t("No encuentro CaptureAge: fija su ruta en Configuración → Cambiar ruta de CaptureAge…",
                    "Can't find CaptureAge: set its path in Settings → Change CaptureAge path…"));
            return;
        }
        try {
            // Vía explorer (doble clic real, sin heredar nuestro entorno); CA engancha el juego al espectar
            new ProcessBuilder("explorer.exe", ca.toString()).start();
            status.setText(t("Lanzando CaptureAge de acompañante…", "Launching CaptureAge alongside…"));
        } catch (Exception ex) {
            status.setText(t("No se pudo lanzar CaptureAge: ", "Couldn't launch CaptureAge: ") + causa(ex));
        }
    }

    /** Abre el juego espectando una partida en curso (protocolo
     *  aoe2de://1/matchId, el mismo que usa aoe2companion/aoe2recs). */
    void espectarPartida(long matchId) {
        log("espectar: lanzando aoe2de://1/" + matchId);
        try {
            Desktop.getDesktop().browse(URI.create("aoe2de://1/" + matchId));
            boolean caListo = rutaCaptureAge() != null;
            if (usarCA() && caListo) lanzarCaptureAge(null);
            String pista = (!usarCA() && caListo)
                    ? t(" (Configuración → «Usar CaptureAge» lo lanzaría también)",
                        " (Settings → \u201CUse CaptureAge\u201D would launch it too)")
                    : "";
            status.setText(t("Abriendo AoE2 para espectar\u2026 Si estaba cerrado tardará; si la partida termina antes de entrar, el juego dirá «Invalid match ID».",
                    "Opening AoE2 to spectate\u2026 If it was closed it takes a while; if the game ends before you join, AoE2 will show \"Invalid match ID\".") + pista);
        } catch (Exception ex) {
            status.setText(t("No se pudo abrir el juego: ", "Couldn't open the game: ") + causa(ex));
        }
    }

    /** Verifica que la partida sigue en curso justo antes de lanzar el juego:
     *  si acaba de terminar, avisa y re-sincroniza en vez de abrir AoE2 a un
     *  «Invalid match ID». */
    void espectarVerificando(long profileId, long matchId) {
        status.setText(t("Comprobando que la partida sigue en curso…", "Checking the game is still live…"));
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                try {
                    Iterable<Match> leidas = COMPANION.partidas(profileId, 1, PER_PAGE);
                    for (Match m : leidas) {
                        if (m != null && m.id == matchId) { log("espectar: verificación de " + matchId + " → started=" + m.started + " finished=" + m.finished); return enCursoReal(m); }
                    }
                    log("espectar: la partida " + matchId + " no aparece en las últimas del perfil " + profileId + "; se lanza igualmente");
                } catch (Exception ex) {
                    log("espectar: verificación no concluyente: " + causa(ex));
                }
                return null;   // sin datos claros: lanzar igualmente
            }
            @Override protected void done() {
                Boolean viva;
                try { viva = get(); } catch (Exception e) { viva = null; }
                if (Boolean.FALSE.equals(viva)) {
                    status.setText(t("Esa partida ya terminó (el companion la seguía dando por viva): el directo no existe. Dale a «Buscar partidas» para bajar la rec.",
                            "That game already ended (the companion still listed it as live): the live match is gone. Hit search to download the rec."));
                    for (Long pid : new ArrayList<>(vivoWatch.keySet()))   // el punto fantasma se va ya
                        if (Long.valueOf(matchId).equals(vivoWatch.get(pid))) { vivoWatch.remove(pid); vivoInfo.remove(pid); VIVO_RIVAL.remove(pid); }
                    actualizarIndicadoresVivos(); refrescarAlturasWatch(); playersList.repaint();
                    vigilarVivos();
                    return;
                }
                espectarPartida(matchId);
            }
        }.execute();
    }

    void espectar(Player p) {
        Long mid = vivoWatch.get(p.id());
        if (mid != null) espectarVerificando(p.id(), mid);
    }

    void abrirTwitch() {
        try { Desktop.getDesktop().browse(URI.create("https://" + TWITCH)); }
        catch (Exception ex) { status.setText("Abre en tu navegador: https://" + TWITCH); }
    }

    void abrirDonacion() {
        try { Desktop.getDesktop().browse(URI.create(DONAR_URL)); }
        catch (Exception ex) { status.setText("Abre en tu navegador: " + DONAR_URL); }
    }

    // ----- Acerca de (logo del clan + firma) ---------------------------------
    void showAbout() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        if (logo != null) {
            Image scaled = logo.getScaledInstance(96, -1, Image.SCALE_SMOOTH);
            JLabel l = new JLabel(new ImageIcon(scaled));
            l.setHorizontalAlignment(SwingConstants.CENTER);
            p.add(l, BorderLayout.NORTH);
        }
        JLabel texto = new JLabel("<html><div style='text-align:center'>"
                + "<b>" + NOMBRE + " " + VERSION + "</b><br>"
                + t("Tu radar del AoE2 competitivo, sin spoilers.<br><br>", "Your competitive AoE2 radar, spoiler-free.<br><br>")
                + t("Creado por <b>", "Created by <b>") + AUTOR_COMPLETO + "</b><br>"
                + "Clan " + CLAN + " · " + TWITCH + "<br><br>"
                + "<span style='color:gray'>© 2026 " + AUTOR_COMPLETO + " · MIT</span><br><br>"
                + "<span style='color:gray;font-size:90%'>"
                + t("Datos: <b>aoe2companion</b> (Dennis Keil) · Tech tree: <b>aoe2techtree</b> (HSZemi, MIT) · Banderas: hampusborgos/country-flags<br>UI: <b>FlatLaf</b> (Apache 2.0) · Runtime: OpenJDK (GPLv2+CPE) · Espectación: <b>CaptureAge</b> · Directos: <b>Twitch</b><br>Inspiración: aoe2insights, aoe2recs, aoe2scout<br><br>",
                    "Data: <b>aoe2companion</b> (Dennis Keil) · Tech tree: <b>aoe2techtree</b> (HSZemi, MIT) · Flags: hampusborgos/country-flags<br>UI: <b>FlatLaf</b> (Apache 2.0) · Runtime: OpenJDK (GPLv2+CPE) · Spectating: <b>CaptureAge</b> · Streams: <b>Twitch</b><br>Inspiration: aoe2insights, aoe2recs, aoe2scout<br><br>")
                + "Age of Empires II © Microsoft Corporation. aoe2radar was created under Microsoft's \u201CGame Content Usage Rules\u201D using assets from Age of Empires II, and it is not endorsed by or affiliated with Microsoft."
                + "</span></div></html>");
        texto.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(texto, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, p, "Acerca de", JOptionPane.PLAIN_MESSAGE);
    }

    // ----- Tema claro/oscuro (FlatLaf por reflexión, opcional) ---------------
    /** Aplica el tema con FlatLaf si su jar está en el classpath. Con ventana,
     *  refresca la UI en caliente. Devuelve false si FlatLaf no está. */
    static boolean aplicarTema(String tema, SpoilerFreeRecs ventana) {
        boolean oscuro = switch (tema) {
            case TEMA_OSCURO -> true;
            case TEMA_CLARO  -> false;
            default          -> sistemaEnOscuro();
        };
        try {
            try {   // acento en el azul del logo 12T (si esta versión de FlatLaf lo soporta)
                Class.forName("com.formdev.flatlaf.FlatLaf")
                        .getMethod("setGlobalExtraDefaults", Map.class)
                        .invoke(null, Map.of("@accentColor", "#1d428a"));
            } catch (Throwable ignored) {}
            String clase = oscuro ? "com.formdev.flatlaf.FlatDarkLaf" : "com.formdev.flatlaf.FlatLightLaf";
            UIManager.setLookAndFeel((LookAndFeel) Class.forName(clase).getDeclaredConstructor().newInstance());
            temaOscuroActivo = oscuro;
            Color fondo = UIManager.getColor("Table.background");
            Color letra = UIManager.getColor("Table.foreground");
            if (fondo != null && letra != null)
                UIManager.put("Table.alternateRowColor", mezcla(fondo, letra, 0.06f));
            Font base = UIManager.getFont("defaultFont");
            if (base != null)
                UIManager.put("defaultFont", base.deriveFont((float) tamLetra(leerConfig("letra", "grande"))));
            if (ventana != null) {
                SwingUtilities.updateComponentTreeUI(ventana);
                if (ventana.configMenu != null) SwingUtilities.updateComponentTreeUI(ventana.configMenu);
                ventana.ajustarGrises(oscuro);
                ventana.ajustarBotonesEspeciales(oscuro);
                ventana.ajustarFuentesSecundarias();
            }
            return true;
        } catch (Throwable t) {      // sin el jar: ClassNotFoundException
            return false;
        }
    }

    /** Windows: AppsUseLightTheme = 0 -> apps en modo oscuro.
     *  Fuera de Windows o si falla la consulta: claro. */
    static boolean sistemaEnOscuro() {
        try {
            Process p = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme").redirectErrorStream(true).start();
            String salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return salida.contains("0x0");
        } catch (Exception e) {
            return false;
        }
    }

    /** Restaura tamaño, posición y maximizado de la última sesión, con
     *  cordura: si la posición guardada cae fuera de la pantalla, se centra. */
    void aplicarVentanaGuardada() {
        setSize(1180, 680);
        setLocationRelativeTo(null);
        try {
            String v = leerConfig("ventana", null);
            if (v != null) {
                String[] p = v.split(",");
                int x = Integer.parseInt(p[0].trim()), y = Integer.parseInt(p[1].trim());
                int w = Math.max(700, Integer.parseInt(p[2].trim()));
                int h = Math.max(450, Integer.parseInt(p[3].trim()));
                Rectangle pant = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                w = Math.min(w, pant.width);
                h = Math.min(h, pant.height);
                if (x >= pant.x - 8 && y >= pant.y - 8
                        && x + 100 < pant.x + pant.width && y + 100 < pant.y + pant.height) {
                    setBounds(x, y, w, h);
                } else {
                    setSize(w, h);
                    setLocationRelativeTo(null);
                }
            }
            if (Boolean.parseBoolean(leerConfig("ventana_max", "false")))
                setExtendedState(JFrame.MAXIMIZED_BOTH);
        } catch (Exception ignored) {}
    }

    void guardarVentana() {
        if (splitPrincipal != null)
            guardarConfig("divisor", String.valueOf(splitPrincipal.getDividerLocation()));
        boolean max = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        guardarConfig("ventana_max", String.valueOf(max));
        if (!max) {
            Rectangle b = getBounds();
            guardarConfig("ventana", b.x + "," + b.y + "," + b.width + "," + b.height);
        }
    }

    static String temaValido(String t) {
        return switch (t) { case TEMA_CLARO, TEMA_OSCURO -> t; default -> TEMA_SISTEMA; };
    }

    /** Mezcla dos colores: base con una fracción f del otro (rayado de tabla). */
    static Color mezcla(Color a, Color b, float f) {
        return new Color(
                Math.round(a.getRed()   * (1 - f) + b.getRed()   * f),
                Math.round(a.getGreen() * (1 - f) + b.getGreen() * f),
                Math.round(a.getBlue()  * (1 - f) + b.getBlue()  * f));
    }

    /** Muestra u oculta la barra de progreso de la fila de estado. */
    long opSerial;   // cada operación tiene su número: el watchdog del Detener solo cierra la suya

    void trabajando(boolean on) {
        progreso.setVisible(on);
        if (on) { stopOperacion = false; hiloOperacion = null; }   // antes que opEnCurso: operación nueva = freno suelto (la anterior, si aún muere, ya no frena a esta), y hasta que anote su hilo Detener no alcanza a nadie
        opEnCurso = on;
        if (on) opSerial++;
        if (!on) {   // cualquier fin de operación deja la UI usable, pase por donde pase
            fetchBtn.setEnabled(true); azarBtn.setEnabled(true); gteBtn.setEnabled(true);
            if (dlSel != null) dlSel.setEnabled(true);
            if (dlAll != null) dlAll.setEnabled(true);
        }
        if (on) {
            if (continuarBtn != null) continuarBtn.setVisible(false);
        }
        if (detenerDescBtn != null) {
            detenerDescBtn.setVisible(on);
            if (!on) detenerDescBtn.setEnabled(true);
        }
    }

    static int tamLetra(String letra) {
        return switch (letra) { case "grande" -> 14; case "muygrande" -> 16; default -> 12; };
    }

    /** Reescala los textos secundarios (nota, firma, pistas de la Watchlist,
     *  título y alto de fila de la tabla) acorde a la fuente base actual. */
    void ajustarFuentesSecundarias() {
        Font base = UIManager.getFont("defaultFont");
        float b = base != null ? base.getSize2D() : 12f;
        float peq = Math.max(10f, b - 1f);
        if (nota != null) nota.setFont(nota.getFont().deriveFont(Font.PLAIN, peq));
        if (firma != null) firma.setFont(firma.getFont().deriveFont(Font.PLAIN, peq));
        if (watchPista1 != null) watchPista1.setFont(watchPista1.getFont().deriveFont(Font.PLAIN, peq));
        if (watchPista2 != null) watchPista2.setFont(watchPista2.getFont().deriveFont(Font.PLAIN, peq));
        if (watchPista3 != null) watchPista3.setFont(watchPista3.getFont().deriveFont(Font.PLAIN, peq));
        if (tituloWatch != null && base != null)
            tituloWatch.setTitleFont(base.deriveFont(Font.BOLD, b + 1f));
        table.setRowHeight(Math.round(b) + 12);
        repaint();
    }

    /** Modos especiales en contorno con los colores del logo, legibles en
     *  claro y en oscuro. «Buscar partidas» conserva el protagonismo como
     *  botón por defecto. */
    void ajustarBotonesEspeciales(boolean oscuro) {
        String azul = oscuro ? "#7da2e0" : "#1d428a";
        String rojo = oscuro ? "#e0707f" : "#c8102e";
        azarBtn.putClientProperty("FlatLaf.style",
                "foreground: " + azul + "; borderColor: " + azul + "; hoverBorderColor: " + azul + "; focusedBorderColor: " + azul);
        gteBtn.putClientProperty("FlatLaf.style",
                "foreground: " + rojo + "; borderColor: " + rojo + "; hoverBorderColor: " + rojo + "; focusedBorderColor: " + rojo);
        String ambar = oscuro ? "#d9a55b" : "#9a6b1f";
        if (resultadosBtn != null)
            resultadosBtn.putClientProperty("FlatLaf.style",
                    "foreground: " + ambar + "; borderColor: " + ambar + "; hoverBorderColor: " + ambar + "; focusedBorderColor: " + ambar);
        cafeBtn.putClientProperty("FlatLaf.style",
                "foreground: " + ambar + "; borderColor: " + ambar + "; hoverBorderColor: " + ambar + "; focusedBorderColor: " + ambar);
        azarBtn.updateUI();
        gteBtn.updateUI();
        cafeBtn.updateUI();
    }

    /** Grises de la nota y la firma legibles en ambos temas. */
    void ajustarGrises(boolean oscuro) {
        Color g1 = oscuro ? new Color(170, 170, 170) : new Color(90, 90, 90);
        Color g2 = oscuro ? new Color(150, 150, 150) : new Color(120, 120, 120);
        if (nota  != null) nota.setForeground(mostrarResultados
                ? (oscuro ? new Color(0xd9, 0xa5, 0x5b) : new Color(0x9a, 0x6b, 0x1f)) : g1);
        if (firma != null) firma.setForeground(g2);
        if (watchPista1 != null) watchPista1.setForeground(g1);
        if (watchPista2 != null) watchPista2.setForeground(g1);
        if (watchPista3 != null) watchPista3.setForeground(g1);
    }

    /** Genera logo.ico multi-tamaño desde el logo (embebido o logo.png).
     *  Lo usa crear_exe.bat: "java -cp build SpoilerFreeRecs --make-ico". */
    static boolean generarIco() {
        try {
            Image logo = cargarLogo();
            if (logo == null) {
                System.out.println("No hay logo: no se genera logo.ico.");
                return false;
            }
            Files.write(Path.of("logo.ico"), icoBytes(logo));
            System.out.println("logo.ico generado (multi-tamaño 16-256, logo centrado).");
            return true;
        } catch (Exception ex) {
            System.out.println("No se pudo generar logo.ico: " + causa(ex));
            return false;
        }
    }

    /** .ico multi-tamaño compatible con el Explorador de Windows en accesos
     *  directos y anclajes: 16/24/32/48/64 en BMP clásico de 32 bits con alfa
     *  y 256 en PNG. */
    static byte[] icoBytes(Image logo) throws IOException {
        int[] tams = { 16, 24, 32, 48, 64, 256 };
        List<byte[]> datos = new ArrayList<>();
        for (int s : tams) {
            BufferedImage img = (BufferedImage) iconoCuadrado(logo, s);
            if (s == 256) {
                var png = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, "png", png);
                datos.add(png.toByteArray());
            } else {
                datos.add(bmpIco(img));
            }
        }
        var out = new ByteArrayOutputStream();
        var d = new DataOutputStream(out);
        d.writeShort(Short.reverseBytes((short) 0));             // reservado
        d.writeShort(Short.reverseBytes((short) 1));             // tipo: icono
        d.writeShort(Short.reverseBytes((short) tams.length));   // nº de imágenes
        int offset = 6 + 16 * tams.length;
        for (int i = 0; i < tams.length; i++) {
            int s = tams[i];
            d.writeByte(s == 256 ? 0 : s);                       // ancho (0 = 256)
            d.writeByte(s == 256 ? 0 : s);                       // alto
            d.writeByte(0);                                      // paleta
            d.writeByte(0);                                      // reservado
            d.writeShort(Short.reverseBytes((short) 1));         // planos
            d.writeShort(Short.reverseBytes((short) 32));        // bits por píxel
            d.writeInt(Integer.reverseBytes(datos.get(i).length));
            d.writeInt(Integer.reverseBytes(offset));
            offset += datos.get(i).length;
        }
        for (byte[] b : datos) d.write(b);
        return out.toByteArray();
    }

    /** Entrada BMP de icono: BITMAPINFOHEADER + píxeles BGRA de abajo arriba
     *  + máscara AND a cero (la transparencia la decide el canal alfa). */
    static byte[] bmpIco(BufferedImage img) throws IOException {
        int s = img.getWidth();
        int filaAnd = ((s + 31) / 32) * 4;
        var out = new ByteArrayOutputStream();
        var d = new DataOutputStream(out);
        d.writeInt(Integer.reverseBytes(40));                    // tamaño de cabecera
        d.writeInt(Integer.reverseBytes(s));                     // ancho
        d.writeInt(Integer.reverseBytes(s * 2));                 // alto (XOR + AND)
        d.writeShort(Short.reverseBytes((short) 1));             // planos
        d.writeShort(Short.reverseBytes((short) 32));            // bits por píxel
        d.writeInt(0);                                           // sin compresión
        d.writeInt(Integer.reverseBytes(s * s * 4 + filaAnd * s));
        d.writeInt(0); d.writeInt(0); d.writeInt(0); d.writeInt(0);
        for (int y = s - 1; y >= 0; y--)
            for (int x = 0; x < s; x++) {
                int argb = img.getRGB(x, y);
                d.writeByte(argb & 0xFF);                        // B
                d.writeByte((argb >> 8) & 0xFF);                 // G
                d.writeByte((argb >> 16) & 0xFF);                // R
                d.writeByte((argb >>> 24) & 0xFF);               // A
            }
        d.write(new byte[filaAnd * s]);                          // máscara AND
        return out.toByteArray();
    }

    /** Logo del clan: primero como recurso empaquetado, si no como fichero al lado. */
    static Image cargarLogo() {
        try {
            var url = SpoilerFreeRecs.class.getResource("/logo.png");
            if (url != null) return new ImageIcon(url).getImage();
        } catch (Exception ignored) {}
        try {
            if (Files.exists(Path.of("logo.png")))
                return new ImageIcon("logo.png").getImage();
        } catch (Exception ignored) {}
        try {
            return new ImageIcon(Base64.getDecoder().decode(LOGO_B64)).getImage();
        } catch (Exception ignored) {}
        return null;
    }

    /** Logo 12T embebido (Base64). Último recurso de cargarLogo(): la app y
     *  el .ico salen con el logo aunque logo.png no esté al lado. */
    static final String LOGO_B64 = String.join("",
            "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");

    /** Icono cuadrado de lado {@code lado}: el logo entero, centrado y a escala
     *  proporcional sobre fondo transparente. Nunca se estira ni se recorta. */
    static Image iconoCuadrado(Image src, int lado) {
        int w = src.getWidth(null), h = src.getHeight(null);
        if (w <= 0 || h <= 0) return src;
        double f = Math.min(lado / (double) w, lado / (double) h);
        int nw = Math.max(1, (int) Math.round(w * f));
        int nh = Math.max(1, (int) Math.round(h * f));
        BufferedImage out = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, (lado - nw) / 2, (lado - nh) / 2, nw, nh, null);
        g.dispose();
        return out;
    }

    // ----- Filtros -----------------------------------------------------------
    void refreshModeCombo() {
        actualizandoCombos = true;
        Object sel = modeCombo.getSelectedItem();
        TreeSet<String> modos = new TreeSet<>();
        for (Match m : all) modos.add(m.mode);
        modeCombo.removeAllItems();
        modeCombo.addItem(todosModos());
        for (String s : modos) modeCombo.addItem(s);
        if (sel != null && modos.contains(String.valueOf(sel))) modeCombo.setSelectedItem(sel);
        Object selMapa = mapaCombo.getSelectedItem();
        TreeSet<String> mapas = new TreeSet<>();
        for (Match m : all) if (m.map != null && !m.map.isBlank()) mapas.add(m.map);
        mapaCombo.removeAllItems(); mapaCombo.addItem(t("Todos los mapas", "All maps"));
        for (String s : mapas) mapaCombo.addItem(s);
        if (selMapa != null && mapas.contains(String.valueOf(selMapa))) mapaCombo.setSelectedItem(selMapa);
        actualizandoCombos = false;
    }

    /** El titular del azar: con filtro de civ, quien LA JUGÓ (si ambos, el de
     *  más ELO); sin filtro, el de más ELO como siempre. */
    static void ajustarRefAzar(Match m, String civSel) {
        MatchPlayer mejor = null;
        for (MatchPlayer p : m.players) {
            boolean cumple = civSel == null || (p.civ != null && p.civ.equalsIgnoreCase(civSel));
            if (!cumple) continue;
            if (mejor == null || (p.rating != null && (mejor.rating == null || p.rating > mejor.rating))) mejor = p;
        }
        if (mejor == null && !m.players.isEmpty()) mejor = m.players.get(0);
        if (mejor != null) m.refId = mejor.id;
    }

    /** Fija el jugador seguido de referencia de la partida: primero uno de los
     *  seleccionados en la lista, si no cualquiera de los seguidos. */
    void asignarRef(Match m) {
        MatchPlayer suj = null;   // primero, los SUJETOS de la búsqueda (si varios, el de más ELO entre ellos)
        for (MatchPlayer mp : m.players)
            if (SUJETOS.contains(mp.id) && (suj == null || (mp.rating != null && (suj.rating == null || mp.rating > suj.rating)))) suj = mp;
        if (suj != null) { m.refId = suj.id; return; }
        for (Player p : playersList.getSelectedValuesList())
            if (m.tieneJugador(p.id())) { m.refId = p.id(); return; }
        for (int i = 0; i < playersModel.size(); i++) {
            Player p = playersModel.get(i);
            if (m.tieneJugador(p.id())) { m.refId = p.id(); return; }
        }
        if (!m.players.isEmpty()) {
            MatchPlayer mejor = m.players.get(0);
            for (MatchPlayer p : m.players)
                if (p.rating != null && (mejor.rating == null || p.rating > mejor.rating)) mejor = p;
            m.refId = mejor.id;
        }
    }

    /** Ancho óptimo de una columna: el mayor entre su cabecera y sus celdas visibles (con topes). */
    void ajustarColumna(int c) {
        javax.swing.table.TableColumn col = table.getColumnModel().getColumn(c);
        int mc = table.convertColumnIndexToModel(c);
        javax.swing.table.TableCellRenderer hr = col.getHeaderRenderer() != null ? col.getHeaderRenderer() : table.getTableHeader().getDefaultRenderer();
        int w = hr.getTableCellRendererComponent(table, col.getHeaderValue(), false, false, -1, c).getPreferredSize().width + 16;
        int filas = Math.min(table.getRowCount(), 300);
        for (int r = 0; r < filas; r++) {
            Component comp = table.prepareRenderer(table.getCellRenderer(r, c), r, c);
            w = Math.max(w, comp.getPreferredSize().width + 14);
        }
        int max = mc == 1 || mc == 5 ? 420 : 260;
        col.setPreferredWidth(Math.max(56, Math.min(max, w)));
    }

    /** Todas las columnas al contenido (tras cada tabla nueva): Rival absorbe lo que sobre. */
    void ajustarColumnas() {
        if (table == null || table.getColumnCount() == 0) return;
        for (int c = 0; c < table.getColumnCount(); c++) ajustarColumna(c);
    }

    void applyFilters() {
        marcarFantasmas(all);
        String modo = (String) modeCombo.getSelectedItem();
        Set<Long> selIds = new HashSet<>();
        List<Player> baseFiltro = new ArrayList<>();   // filtro rápido: los chips seleccionados en «Partidas de:»
        for (Player s : ultimosSujetos) if (filtroSujetos.contains(s.id())) baseFiltro.add(s);
        for (Player p : baseFiltro) {
            selIds.add(p.id());
            if (p.vinculo() != 0)   // seleccionar una cuenta = seleccionar su familia
                for (Player x : todosJugadores)
                    if (x.vinculo() == p.vinculo()) selIds.add(x.id());
        }

        String sgCfg = leerConfig("savegame", null);
        Path sgConocida = (sgCfg != null && Files.isDirectory(Path.of(sgCfg))) ? Path.of(sgCfg) : null;

        view.clear();
        for (Match m : all) {
            if (modo != null && !todosModos().equals(modo) && !modo.equals(m.mode)) continue;
            if (mapaCombo.getSelectedIndex() > 0 && !String.valueOf(mapaCombo.getSelectedItem()).equals(m.map)) continue;
            if (periodoCombo.getSelectedIndex() > 0) { int dias = new int[]{ 0, 7, 30, 90, 365 }[periodoCombo.getSelectedIndex()]; if (m.started == null || m.started.isBefore(Instant.now().minus(Duration.ofDays(dias)))) continue; }
            if (!filtroRival.isEmpty()) { asignarRef(m); if (!rivalCoincide(m, filtroRival)) continue; }
            if (!selIds.isEmpty()) {
                boolean alguno = false;
                for (long id : selIds) if (m.tieneJugador(id)) { alguno = true; break; }
                if (!alguno) continue;
            }
            asignarRef(m);
            if (m.finished == null) {
                m.estado = enCursoReal(m) ? "\u25B6" : "\u2014";
                m.enDisco = false;
                m.enJuego = false;
            } else {
                m.enDisco = Files.exists(destino(m));
                m.enJuego = sgConocida != null
                        && Files.exists(sgConocida.resolve(destino(m).getFileName().toString()));
            }
            view.add(m);
        }
        tableModel.fireTableDataChanged();
        mostrarGuiaVacia(all.isEmpty());
        actualizarTextoBuscar();
        if (!all.isEmpty())
            status.setText(view.size() + t(" de ", " of ") + all.size() + t(" partidas (según filtros).", " games (per filters)."));
    }

    // ----- Alta de jugadores (búsqueda por nick, sin visitar webs) -----------
    // ----- «Mi partida»: quién eres (registro de Windows), aviso al encontrar partida (log del juego) y panel sobre el juego -----
    javax.swing.Timer logJuegoTimer; Path logJuegoActual; long logJuegoPos; String logJuegoUltimaFase; long logJuegoUltimoAvisoMs;
    JWindow superposicion; javax.swing.Timer superposicionTimer;

    /** Vigila el MainLog.txt de la sesión más reciente del juego: al ver la fase de preparación (MS_Setup), aviso temprano y sondeo del lobby. Solo si hay «mi perfil». */
    void iniciarVigilanciaLogJuego() {
        if (logJuegoTimer != null) return;
        log("mi partida: vigilancia del log del juego activa (carpeta " + carpetaLogsJuego() + ", existe=" + Files.isDirectory(carpetaLogsJuego()) + "); socket con mi id " + leerConfig("mi_pid", ""));
        logJuegoTimer = new javax.swing.Timer(2000, e -> new Thread(this::leerLogJuego, "log-juego").start());
        logJuegoTimer.start();
    }
    long logJuegoUltimoDiagMs;
    void leerLogJuego() {
        try {
            if (leerConfig("mi_pid", "").isBlank()) return;
            Path carpeta = carpetaLogsJuego();
            if (!Files.isDirectory(carpeta)) return;
            Path sesion = null;
            try (var st = Files.list(carpeta)) { sesion = st.filter(Files::isDirectory).max(Comparator.comparing(p2 -> { try { return Files.getLastModifiedTime(p2).toMillis(); } catch (IOException ex) { return 0L; } })).orElse(null); }
            if (sesion == null) return;
            Path log = sesion.resolve("MainLog.txt");
            if (!Files.exists(log)) { if (System.currentTimeMillis() - logJuegoUltimoDiagMs > 600_000) { logJuegoUltimoDiagMs = System.currentTimeMillis(); log("mi partida: la sesión más reciente (" + sesion.getFileName() + ") no tiene MainLog.txt"); } return; }
            if (!log.equals(logJuegoActual)) { logJuegoActual = log; logJuegoPos = Math.max(0, Files.size(log) - 4000); logJuegoUltimaFase = null; log("mi partida: vigilando " + log + " (" + Files.size(log) + " bytes)"); }   // sesión nueva: empezar por el final
            long tam = Files.size(log);
            if (tam < logJuegoPos) logJuegoPos = 0;
            if (tam == logJuegoPos) return;
            String trozo;
            try (var ch = java.nio.channels.FileChannel.open(log, java.nio.file.StandardOpenOption.READ)) {
                java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate((int) Math.min(tam - logJuegoPos, 512_000));
                ch.position(logJuegoPos); ch.read(buf); buf.flip();
                trozo = StandardCharsets.UTF_8.decode(buf).toString();
                logJuegoPos = tam;
            }
            if (System.currentTimeMillis() - logJuegoUltimoDiagMs > 600_000) { logJuegoUltimoDiagMs = System.currentTimeMillis(); log("mi partida: el log del juego crece (" + tam + " bytes); último trozo " + trozo.length() + " caracteres"); }
            if (trozo.contains("PlayerReadyRequest") || trozo.contains("MS_Setup")) {
                if (System.currentTimeMillis() - logJuegoUltimoAvisoMs > 120_000) {   // una vez por partida
                    logJuegoUltimoAvisoMs = System.currentTimeMillis();
                    log("mi partida: fase de preparación detectada en el log del juego");
                    SwingUtilities.invokeLater(() -> mostrarSuperposicion("\u25CF " + t("Partida encontrada · preparando…", "Match found · getting ready…"), null, 25_000));
                    new Thread(this::sondearLobbyOficial, "lobby-oficial").start();
                }
            }
        } catch (Exception ex) { log("log del juego: " + causa(ex)); }
    }
    /** EXPERIMENTO: lista pública de lobbies del servidor oficial; si el mío aparece con sus jugadores, aviso con los rivales antes de que empiece. Se anota en el log lo que se encuentre. */
    void sondearLobbyOficial() {
        try {
            long mi = Long.parseLong(leerConfig("mi_pid", ""));
            String texto = new String(descargarBytes(OFICIAL_LOBBIES, 20), StandardCharsets.UTF_8);
            Object root = Json.parse(texto);
            List<Long> compañeros = new ArrayList<>();
            boolean encontrado = buscarLobbyConPid(root, mi, compañeros);
            log("lobby oficial: " + (encontrado ? "mi lobby encontrado con " + compañeros.size() + " jugadores más" : "mi lobby no aparece") + " (respuesta de " + texto.length() + " caracteres)");
            if (!encontrado || compañeros.isEmpty()) return;
            cargarEloAyer();
            StringBuilder sb = new StringBuilder("\u25CF " + t("Partida encontrada · con ", "Match found · with "));
            List<Object[]> fichas = new ArrayList<>();
            for (long pid : compañeros) { String[] nn = NOMBRES_AYER.get(pid); String nombre = nn != null ? nn[0] : "#" + pid; Integer e1 = elo1v1Conocido(pid); fichas.add(new Object[]{ pid, nombre, e1 }); if (sb.length() > 30) sb.append(", "); sb.append(nombre).append(e1 != null ? " (" + e1 + ")" : ""); }
            SwingUtilities.invokeLater(() -> mostrarSuperposicion(sb.toString(), fichas, 60_000));
        } catch (Exception ex) { log("lobby oficial: " + causa(ex)); }
    }
    /** Panel sobre el juego (siempre visible, sin bordes, arriba en el centro): el aviso y, si hay, las fichas de los rivales. Se cierra solo. */
    void mostrarSuperposicion(String texto, List<Object[]> fichas, int ms) {
        if (superposicion != null) { superposicion.dispose(); superposicion = null; }
        if (superposicionTimer != null) superposicionTimer.stop();
        JWindow w = new JWindow();
        w.setAlwaysOnTop(true);
        JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(0x1e, 0x1e, 0x1e, 235)); p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xff, 0xd5, 0x6a), 1), BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        JLabel l = new JLabel(texto); l.setForeground(Color.WHITE); l.setFont(l.getFont().deriveFont(Font.BOLD, 14f)); l.setAlignmentX(0f); p.add(l);
        if (fichas != null) for (Object[] f : fichas) {
            long pid = (Long) f[0]; String nombre = (String) f[1]; Integer e1 = (Integer) f[2];
            StringBuilder d = new StringBuilder(nombre + (e1 != null ? "  ·  ELO " + e1 : ""));
            Actividad a = ACTIVIDAD_CACHE.get(pid);
            if (a != null) {   // lo que ya sabemos del rival: sus civs de los últimos 30 días
                Map<String, Integer> civs = new HashMap<>(); Instant hace30 = Instant.now().minus(Duration.ofDays(30));
                for (Match m : a.partidas()) if (m.started != null && m.started.isAfter(hace30)) for (MatchPlayer mp : m.players) if (mp.id == pid && mp.civ != null) civs.merge(mp.civ, 1, Integer::sum);
                List<Map.Entry<String, Integer>> top = new ArrayList<>(civs.entrySet()); top.sort((x, y) -> y.getValue() - x.getValue());
                if (!top.isEmpty()) { d.append("  ·  "); for (int i = 0; i < Math.min(3, top.size()); i++) d.append(i > 0 ? ", " : "").append(top.get(i).getKey()); }
            }
            JLabel lf = new JLabel(d.toString(), iconoBandera(paisDe(pid)), SwingConstants.LEFT); lf.setForeground(new Color(0xdd, 0xdd, 0xdd)); lf.setIconTextGap(6); lf.setAlignmentX(0f); lf.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0)); p.add(lf);
        }
        w.setContentPane(p); w.pack();
        Rectangle pantalla = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        w.setLocation(pantalla.x + (pantalla.width - w.getWidth()) / 2, pantalla.y + 24);
        w.setVisible(true);
        superposicion = w;
        superposicionTimer = new javax.swing.Timer(ms, e -> { if (superposicion == w) { w.dispose(); superposicion = null; } }); superposicionTimer.setRepeats(false); superposicionTimer.start();
    }
    /** «Mi perfil»: el pid guardado en config (mi_pid/mi_nombre); si no, primero el SteamID del registro (sin preguntar) y, si no hay Steam, un buscador. */
    void abrirMiPerfil() {
        String pid = leerConfig("mi_pid", ""), nombre = leerConfig("mi_nombre", "");
        if (!pid.isBlank()) { abrirPerfil(Long.parseLong(pid), nombre); return; }
        preguntarMiNick();
    }
    void preguntarMiNick() {
        String q = JOptionPane.showInputDialog(this, t("Tu nick en el juego:", "Your in-game nick:"), t("Mi perfil", "My profile"), JOptionPane.PLAIN_MESSAGE);
        if (q == null || q.trim().isEmpty()) return;
        new Thread(() -> {
            List<String[]> res = buscarPerfiles(q.trim());
            SwingUtilities.invokeLater(() -> {
                if (res.isEmpty()) { status.setText(t("No encontré ese nick.", "Couldn't find that nick.")); return; }
                Object el = res.size() == 1 ? res.get(0)[2] : JOptionPane.showInputDialog(this, t("¿Cuál eres tú?", "Which one is you?"), t("Mi perfil", "My profile"), JOptionPane.PLAIN_MESSAGE, null, res.stream().map(r -> r[2]).toArray(), res.get(0)[2]);
                if (el == null) return;
                for (String[] r : res) if (r[2].equals(el)) { guardarConfig("mi_pid", r[0]); guardarConfig("mi_nombre", r[1]); abrirPerfil(Long.parseLong(r[0]), r[1]); iniciarVigilanciaLogJuego(); sincronizarSocket(); return; }
            });
        }, "mi-perfil").start();
    }
    /** Un jugador concreto elegido de una sugerencia: en Perfil se abre directamente; en el resto, las mismas tres opciones del buscador, sin repetir la búsqueda. */
    void jugadorElegido(long pid, String nombre) {
        if (actividadAbierta) { abrirPerfil(pid, nombre); return; }
        String verO = t("Ver sus partidas", "View their games"), perfO = t("Ver perfil", "View profile"), addO = t("Añadir al grupo\u2026", "Add to group\u2026"), canO = t("Cancelar", "Cancel");
        int r0 = JOptionPane.showOptionDialog(this, nombre + "  ·  " + pid, t("Resultados", "Results"), JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[]{ perfO, verO, addO, canO }, perfO);
        if (r0 == 0) abrirPerfil(pid, nombre);
        else if (r0 == 1) { Player pl = new Player(pid, nombre, grupoDestino()); objetivoForzado = pl; invitado = pl; vistaDelInvitado = vistaActualId(); playersList.clearSelection(); aplicarFiltroGrupo(); mostrarDirectos(false); fetchMatches(fetchBtn); }
        else if (r0 == 2) {
            Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); gs.add(GRUPO_GENERAL); for (Player x : todosJugadores) gs.add(x.grupo()); gs.addAll(gruposConfig());
            Object g = JOptionPane.showInputDialog(this, t("Grupo:", "Group:"), t("Añadir a la watchlist", "Add to the watchlist"), JOptionPane.PLAIN_MESSAGE, null, gs.toArray(), grupoDestino());
            if (g != null) ficharDesdeTop(new Player(pid, nombre, String.valueOf(g)), String.valueOf(g));
        }
    }
    void addPlayerDialog(boolean soloVer) { addPlayerDialog(soloVer, null); }

    void addPlayerDialog(boolean soloVer, String nickInicial) {
        String q = nickInicial != null && !nickInicial.isBlank() ? nickInicial
                : JOptionPane.showInputDialog(this, t("Nick del jugador:", "Player nick:"),
                        t("Buscar jugador", "Find player"), JOptionPane.PLAIN_MESSAGE);
        if (q == null || q.isBlank()) return;
        status.setText(t("Buscando \"", "Searching \"") + q.trim() + "\"…");
        new SwingWorker<List<String[]>, Void>() {
            @Override protected List<String[]> doInBackground() throws Exception {
                Object root = COMPANION.buscarPerfiles(q);   // sin reintento, como antes
                List<String[]> out = new ArrayList<>();
                for (Object o : arr(val(obj(root), "profiles"))) {
                    Map<String, Object> p = obj(o);
                    long id = lng(val(p, "profile_id", "profileId"));
                    if (id <= 0) continue;
                    String name = str(val(p, "name"));
                    String pais = str(val(p, "country"));
                    aprenderPais(id, pais);
                    long games  = lng(val(p, "games"));
                    out.add(new String[]{ String.valueOf(id), name,
                            name + (pais != null ? "  [" + pais + "]" : "") + "  ·  " + id
                                 + (games > 0 ? "  ·  " + games + " partidas" : "") });
                }
                Set<String> vistos = new HashSet<>(); for (String[] r : out) vistos.add(r[0]);   // la API primero (conoce el nick actual, aunque el volcado aún no); después el índice local, sin repetir
                for (String[] r : buscarLocal(q.trim())) if (vistos.add(r[0])) out.add(new String[]{ r[0], r[1], r[2] + "  ·  " + r[0] });
                return out;
            }
            @Override protected void done() {
                try {
                    List<String[]> res = get();
                    if (res.isEmpty()) { status.setText(t("Sin resultados para \"", "No results for \"") + q.trim() + "\"."); return; }
                    String[] opciones = res.stream().map(r -> r[2]).toArray(String[]::new);
                    JComboBox<String> cbSel = new JComboBox<>(opciones);
                    JPanel pnl = new JPanel(new BorderLayout(0, 6));
                    pnl.add(new JLabel(t("Elige el jugador:", "Pick the player:")), BorderLayout.NORTH);
                    pnl.add(cbSel, BorderLayout.CENTER);
                    String verO = t("Ver sus partidas", "View their games");
                    String perfO = t("Ver perfil", "View profile");
                    String addO = t("Añadir al grupo\u2026", "Add to group\u2026");
                    String canO = t("Cancelar", "Cancel");
                    int r0;
                    if (actividadAbierta && res.size() == 1) r0 = 0;   // en la pestaña Perfil, el buscador de arriba abre el perfil directamente
                    else r0 = JOptionPane.showOptionDialog(SpoilerFreeRecs.this, pnl, t("Resultados", "Results"),
                            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                            actividadAbierta ? new Object[]{ perfO, canO } : new Object[]{ perfO, verO, addO, canO }, perfO);
                    if (actividadAbierta && r0 != 0) { status.setText(t("Listo.", "Ready.")); return; }
                    if (r0 != 0 && r0 != 1 && r0 != 2) { status.setText(t("Listo.", "Ready.")); return; }
                    String sel = (String) cbSel.getSelectedItem();
                    final boolean verPerfil = r0 == 0, verAhora = r0 == 1;
                    for (String[] r : res) if (r[2].equals(sel)) {
                        Player p = new Player(Long.parseLong(r[0]), r[1], grupoDestino());
                        if (verPerfil) { status.setText(t("Listo.", "Ready.")); abrirPerfil(p.id(), p.name()); return; }
                        if (verAhora) {
                            objetivoForzado = p;   // aunque ya esté en un grupo (entonces no es invitado, pero sí el objetivo)
                            invitado = p;
                            vistaDelInvitado = vistaActualId();
                            playersList.clearSelection();   // la selección vieja no debe filtrar al invitado
                            aplicarFiltroGrupo();   // la fila flotante, visible EN EL ACTO (también en los tops)
                            new Thread(() -> {   // ELO del invitado para su fila flotante
                                Integer ei = eloDeLadder(p.id());
                                if (ei != null) SwingUtilities.invokeLater(() -> {
                                    eloWatch.put(p.id(), ei);
                                    playersList.repaint();
                                    refrescarSujetos(ultimosSujetos, invitado != null);   // el ELO recién llegado, a la cabecera
                                });
                            }).start();
                            fetchMatches(fetchBtn);
                            return;
                        }
                        String gElegido = elegirGrupoDialog(p.name());
                        if (gElegido == null) { status.setText(t("Listo.", "Ready.")); return; }
                        final Player pAdd = new Player(p.id(), p.name(), gElegido);
                        if (!containsPlayer(pAdd.id())) {
                            todosJugadores.add(pAdd);
                            savePlayers();
                            rebuildGrupos();
                            aplicarFiltroGrupo();
                            refrescarWatchlist();
                        }
                        ofrecerVinculadasTrasAlta(pAdd.id(), pAdd.name(), pAdd.grupo());
                    }
                    status.setText(t("Listo.", "Ready."));
                } catch (Exception ex) {
                    status.setText(t("Error buscando: ", "Search error: ") + causa(ex));
                }
            }
        }.execute();
    }

    boolean containsPlayer(long id) {
        for (Player p : todosJugadores)
            if (p.id() == id) return true;
        return false;
    }

    // ----- Consulta de partidas ----------------------------------------------
    void fetchMatches(JButton btn) {
        if (fetchWorker != null) {          // segunda pulsación = Detener
            fetchWorker.cancel(true);
            return;
        }
        if (objetivoForzado == null && invitado == null && actividadAbierta && actPid > 0 && playersList.getSelectedIndices().length == 0) {
            objetivoForzado = new Player(actPid, actNombre, grupoDestino());   // con un perfil abierto, «Buscar partidas» busca a ese jugador
            mostrarDirectos(false);
        }
        if (playersModel.isEmpty() && invitado == null && objetivoForzado == null) {
            JOptionPane.showMessageDialog(this, t("Añade antes algún jugador a la lista.",
                    "Add a player to the list first."));
            return;
        }
        List<Player> sel = new ArrayList<>();
        if (objetivoForzado != null) {
            sel.add(objetivoForzado);   // «Ver sus partidas» de alguien concreto: manda él, esté o no en un grupo
            objetivoForzado = null;
        } else if (invitado != null) {
            sel.add(invitado);   // persiste: sigue flotando tras la búsqueda
        } else if (objetivoEtiqueta != null && playersList.getSelectedIndices().length == 0) {
            sel.add(objetivoEtiqueta);   // lo que dice el botón es lo que se busca
        } else if (modoTop()) {
            List<Player> selTop = playersList.getSelectedValuesList();
            if (selTop.size() > 15) {
                selTop = selTop.subList(0, 15);
                status.setText(t("En ★ el máximo son 15 perfiles por tanda: busco los 15 primeros seleccionados.",
                        "In ★ the cap is 15 profiles per run: searching the first 15 selected."));
            }
            if (!selTop.isEmpty()) sel.addAll(selTop);
            else if (soloVivosBtn != null && soloVivosBtn.isSelected())
                for (int i = 0; i < playersModel.size(); i++) sel.add(playersModel.get(i));
            else {
                JOptionPane.showMessageDialog(this,
                        t("En ★ (Top ladder o Top país), selecciona jugadores concretos (clic o Ctrl+clic en la lista)\no activa el chip «● Jugando» antes de buscar.",
                          "In ★ (Top ladder or Country top), select specific players (click or Ctrl+click the list)\nor turn on the “● Live” checkbox before searching."),
                        t("Buscar en el top", "Search the top"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        } else {
            List<Player> selNorm = playersList.getSelectedValuesList();
            if (!selNorm.isEmpty()) sel.addAll(selNorm);   // seleccionar = consultar SOLO eso (y su familia)
            else for (int i = 0; i < playersModel.size(); i++) sel.add(playersModel.get(i));
        }
        final List<Player> tracked = (invitado == null && !modoTop()) ? conFamilias(sel) : sel;
        SUJETOS.clear();
        filtroSujetos.clear();
        if (rivalField != null && !rivalField.getText().isEmpty()) rivalField.setText("");   // búsqueda nueva, filtro limpio
        fallosFetch = 0;
        mostrarGuiaVacia(false);   // llegan filas: la tabla, no la guía
        taparResultados();         // tabla nueva = sin spoilers
        for (Player px : tracked) SUJETOS.add(px.id());
        vistaDeSujetos = vistaActualId();
        refrescarSujetos(tracked, invitado != null);

        mostrarDirectos(false);
        guardarConfig("ventana_n", String.valueOf((int) hoursSpinner.getValue()));
        guardarConfig("horas", String.valueOf(horasVentana()));   // compatibilidad con el resto (azar, GTE)
        btn.setText(t("Detener", "Stop"));
        btn.setToolTipText(t("Detiene la búsqueda en curso", "Stops the current search"));
        azarBtn.setEnabled(false);
        gteBtn.setEnabled(false);
        trabajando(true);
        final long miSerial = opSerial;
        int hours = horasVentana();
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
        SwingWorker<List<Match>, String> fw = new SwingWorker<>() {
            @Override protected List<Match> doInBackground() {
                hiloOperacion = Thread.currentThread();   // Detener corta la espera del freno de ESTA operación, no la de todos
                Map<Long, Match> unicos = new LinkedHashMap<>();
                topeAlcanzado = false;
                final int MAX_PAGINAS = 6, MAX_TOTAL = 600;   // tope de seguridad por búsqueda
                for (Player pl : tracked) {
                    if (isCancelled()) break;
                    // Páginas sucesivas hasta cubrir la ventana (para 24-48 h basta una)
                    for (int pagina = 1; pagina <= MAX_PAGINAS; pagina++) {
                        if (isCancelled()) break;
                        publish(t("Consultando ", "Checking ") + pl.name() + (pagina > 1 ? " (" + t("pág. ", "p. ") + pagina + ")" : "") + "…");
                        boolean seguir = false;
                        try {
                            Iterable<Match> leidas = COMPANION.partidas(pl.id(), pagina, PER_PAGE);   // con reintento ante 429 (la segunda pulsación corta la espera)
                            int n = 0; Instant masAntigua = null;
                            for (Match m : leidas) {
                                if (m == null) continue;
                                n++;
                                Instant ref = m.finished != null ? m.finished : m.started;
                                if (ref != null && (masAntigua == null || ref.isBefore(masAntigua))) masAntigua = ref;
                                if (m.finished == null && !enCursoReal(m)) continue;               // fantasma de crash: fuera
                                if (m.finished != null && m.finished.isBefore(cutoff)) continue;   // fuera de ventana (las EN CURSO entran)
                                unicos.putIfAbsent(m.id, m);
                            }
                            // hay más que pedir si la página vino llena y su partida más antigua sigue dentro de la ventana
                            seguir = n >= PER_PAGE && masAntigua != null && masAntigua.isAfter(cutoff);
                            if (seguir && pagina == MAX_PAGINAS) topeAlcanzado = true;
                            if (unicos.size() >= MAX_TOTAL) { topeAlcanzado = true; seguir = false; }
                            Thread.sleep(PAUSA_MS);
                        } catch (Exception ex) {
                            if (isCancelled() || ex instanceof InterruptedException) break;   // segunda pulsación: done() ya dijo «detenida»; no pisarlo ni contar un fallo en la búsqueda siguiente
                            fallosFetch++;
                            publish(t("Aviso: fallo con ", "Heads-up: failed with ") + pl.name() + " (" + causa(ex) + ")");
                        }
                        if (!seguir) break;
                    }
                    if (unicos.size() >= MAX_TOTAL) break;
                }
                List<Match> lista = new ArrayList<>(unicos.values());
                lista.sort(Comparator.comparing((Match m) -> m.finished == null ? Instant.MAX : m.finished).reversed());
                return lista;
            }
            @Override protected void process(List<String> msgs) { status.setText(msgs.get(msgs.size() - 1)); }
            @Override protected void done() {
                if (miSerial != opSerial) { log("buscar #" + miSerial + ": terminó superada por la op #" + opSerial); fetchWorker = null; return; }
                fetchWorker = null;
                actualizarTextoBuscar();
                btn.setToolTipText(null);
                btn.setEnabled(true);
                azarBtn.setEnabled(true);
                gteBtn.setEnabled(true);
                trabajando(false);
                if (isCancelled()) {
                    status.setText(t("Búsqueda detenida.", "Search stopped."));
                    return;
                }
                try {
                    List<Match> res = get();
                    aprenderCatalogos(res);
                    Map<Long, Long> vivosVistos = new HashMap<>();
                    Map<Long, String> infosVistos = new HashMap<>();
                    for (Match m : res)
                        if (enCursoReal(m))
                            for (MatchPlayer p : m.players) {
                                vivosVistos.put(p.id, m.id);
                                infosVistos.put(p.id, resumenVivo(m, p.id));
                            }
                    for (Player pl : tracked) {
                        Long v = vivosVistos.get(pl.id());
                        if (v != null) { vivoWatch.put(pl.id(), v); vivoInfo.put(pl.id(), infosVistos.get(pl.id())); }
                        else { vivoWatch.remove(pl.id()); vivoInfo.remove(pl.id()); VIVO_RIVAL.remove(pl.id()); }
                    }
                    actualizarIndicadoresVivos();
                    all.clear();
                    all.addAll(res);
                    refreshModeCombo();
                    applyFilters();
                    status.setText(view.size() + t(" de ", " of ") + all.size() + t(" partidas en las últimas ", " games in the last ") + textoVentana(hours) + "."
                            + (topeAlcanzado
                                ? "  \u26A0 " + t("Tope de la búsqueda alcanzado: puede faltar historial antiguo — acorta la ventana o filtra por modo.",
                                                  "Search cap reached: older history may be missing — shorten the window or filter by mode.")
                                : "")
                            + (fallosFetch > 0
                                ? "  \u26A0 " + fallosFetch + t(" jugador(es) SIN RESPUESTA del servicio (¿429/caído?): sus partidas faltan — reintenta en un minuto.",
                                                              " player(s) got NO RESPONSE from the service (429/down?): their games are missing — retry in a minute.")
                                : ""));
                } catch (Exception ex) {
                    status.setText("Error: " + causa(ex));
                }
            }
        };
        fetchWorker = fw;
        fw.execute();
    }

    // ----- Descarga de recs --------------------------------------------------
    List<Match> selectedRows() {
        List<Match> out = new ArrayList<>();
        for (int r : table.getSelectedRows()) out.add(view.get(table.convertRowIndexToModel(r)));
        return out;
    }

    List<Match> allRows() { return new ArrayList<>(view); }

    void download(List<Match> objetivoIn) { download(objetivoIn, false); }

    void download(List<Match> objetivoIn, boolean enviarSiempre) {
        List<Match> objetivo = new ArrayList<>();
        List<Match> vivas = new ArrayList<>();
        for (Match m : objetivoIn) (m.finished == null ? vivas : objetivo).add(m);
        if (!vivas.isEmpty() && objetivo.isEmpty()) {
            if (vivas.size() == 1) espectarPartida(vivas.get(0).id);
            else status.setText(t("Esas partidas están EN DIRECTO: doble clic en una para espectarla.",
                    "Those games are LIVE: double-click one to spectate."));
            return;
        }
        if (!vivas.isEmpty())
            status.setText(t("Las partidas EN DIRECTO no se descargan; se saltan.",
                    "LIVE games can't be downloaded; skipping them."));
        if (objetivo.isEmpty()) { status.setText(t("No hay partidas seleccionadas.", "No games selected.")); return; }
        if (!descargaSinCambiarVista) mostrarDirectos(false);
        descargaSinCambiarVista = false;
        dlSel.setEnabled(false); dlAll.setEnabled(false);
        trabajando(true);
        final long miSerial = opSerial;
        Set<Long> trackedIds = new HashSet<>();
        for (int i = 0; i < playersModel.size(); i++) trackedIds.add(playersModel.get(i).id());
        final boolean autoCopiar = autoSgItem.isSelected();
        final boolean autoCopiarFinal = autoCopiar || enviarSiempre;
        final Path sgAuto = autoCopiarFinal ? obtenerSavegame(enviarSiempre) : null;
        if (autoCopiarFinal && sgAuto == null)
            log("copia automática a savegame activada pero sin carpeta resuelta: no se copiará");

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                hiloOperacion = Thread.currentThread();   // Detener corta la espera del freno de ESTA operación, no la de todos
                try { Files.createDirectories(RECS_DIR); } catch (IOException ignored) {}
                int ok = 0, copiadas = 0;
                for (Match m : objetivo) {
                    if (stopOperacion) break;
                    setEstado(m, "descargando…");
                    boolean hecho = false;
                    for (long pid : candidatos(m, trackedIds)) {
                        if (stopOperacion) break;
                        byte[] datos = descargarRec(m.id, pid);
                        if (datos != null) {
                            try {
                                Files.write(destino(m), datos);
                                hecho = true;
                                break;
                            } catch (IOException ex) {
                                log("no se pudo escribir " + destino(m) + ": " + causa(ex));
                            }
                        }
                        dormir(PAUSA_MS);
                    }
                    boolean copiada = false;
                    if (hecho) {
                        m.enDisco = true;
                        ok++;
                        if (autoCopiarFinal && sgAuto != null && copiarASavegame(m, sgAuto)) {
                            copiadas++;
                            copiada = true;
                            m.enJuego = true;
                        }
                    }
                    setEstado(m, hecho ? (copiada ? t("✓✓ en juego", "✓✓ in game") : "✓ guardada") : "✗ no disponible");
                    dormir(PAUSA_MS);
                }
                final int n = ok, tot = objetivo.size(), cop = copiadas;
                final boolean parada = stopOperacion;
                SwingUtilities.invokeLater(() ->
                        status.setText((parada ? t("Detenido. ", "Stopped. ") : "")
                                + n + "/" + tot + t(" recs guardadas en ./", " recs saved to ./") + RECS_DIR
                                + (cop > 0 ? "  ·  " + cop + t(" al juego", " to the game") : "")
                                + (n < tot ? t("  ·  detalle en descargas.log", "  ·  details in descargas.log") : "")));
                return null;
            }
            @Override protected void done() {
                if (miSerial != opSerial) { log("descargas #" + miSerial + ": terminó superada por la op #" + opSerial); return; }
                dlSel.setEnabled(true);
                dlAll.setEnabled(true);
                trabajando(false);
                if (alTerminarDescarga != null) { Runnable r = alTerminarDescarga; alTerminarDescarga = null; r.run(); }
            }
        }.execute();
    }
    boolean descargaSinCambiarVista; Runnable alTerminarDescarga;

    /** POVs a intentar, por orden: el de referencia, con rec confirmada
     *  (seguidos primero), seguidos, resto. */
    static List<Long> candidatos(Match m, Set<Long> tracked) {
        LinkedHashSet<Long> c = new LinkedHashSet<>();
        for (MatchPlayer p : m.players) if (p.id == m.refId && !Boolean.FALSE.equals(p.replay)) c.add(p.id);
        for (MatchPlayer p : m.players) if (Boolean.TRUE.equals(p.replay) && tracked.contains(p.id)) c.add(p.id);
        for (MatchPlayer p : m.players) if (Boolean.TRUE.equals(p.replay)) c.add(p.id);
        for (MatchPlayer p : m.players) if (tracked.contains(p.id)) c.add(p.id);
        for (MatchPlayer p : m.players) c.add(p.id);
        return new ArrayList<>(c).subList(0, Math.min(c.size(), 8));   // TGs de 8: todas las perspectivas caben
    }

    void setEstado(Match m, String txt) {
        SwingUtilities.invokeLater(() -> {
            m.estado = txt;
            int idx = view.indexOf(m);
            if (idx >= 0) tableModel.fireTableRowsUpdated(idx, idx);
        });
    }

    // ----- Persistencia ------------------------------------------------------
    void loadPlayers() {
        if (Files.exists(PLAYERS_FILE)) {
            try {
                for (String line : Files.readAllLines(PLAYERS_FILE, StandardCharsets.UTF_8)) {
                    String[] parts = line.split(";", 4);
                    if (parts.length >= 2 && !parts[0].isBlank()) {
                        long vinc = 0L;
                        if (parts.length == 4 && !parts[3].isBlank())
                            try { vinc = Long.parseLong(parts[3].trim()); } catch (Exception ignored) {}
                        todosJugadores.add(new Player(Long.parseLong(parts[0].trim()), parts[1].trim(),
                                parts.length >= 3 && !parts[2].isBlank() ? parts[2].trim() : GRUPO_GENERAL, vinc));
                    }
                }
            } catch (Exception e) {
                status.setText(t("No se pudo leer players.txt: ", "Couldn't read players.txt: ") + causa(e));
            }
        } else if (leerConfig("grupos", "").isBlank()) {
            // Primer arranque: grupo semilla (el top ya cubre a los pros)
            guardarConfig("grupos", t("Amigos", "Friends"));
        }
        // Limpieza única: el «Pros» sembrado en versiones anteriores, si sigue vacío, sobra
        for (String pros : new String[]{ "Pros" }) {
            boolean conMiembros = false;
            for (Player p : todosJugadores) if (p.grupo().equalsIgnoreCase(pros)) { conMiembros = true; break; }
            if (!conMiembros) {
                List<String> gs = new ArrayList<>();
                for (String g : leerConfig("grupos", "").split(","))
                    if (!g.isBlank() && !g.trim().equalsIgnoreCase(pros)) gs.add(g.trim());
                guardarConfig("grupos", String.join(",", gs));
            }
        }
        rebuildGrupos();       // SIEMPRE: sin esto, el combo quedaba vacío en instalaciones nuevas
        aplicarFiltroGrupo();
    }

    void savePlayers() {
        try {
            List<String> lines = new ArrayList<>();
            for (Player p : todosJugadores)
                lines.add(p.id() + ";" + p.name() + ";" + p.grupo() + (p.vinculo() != 0 ? ";" + p.vinculo() : ""));
            Files.write(PLAYERS_FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            status.setText(t("No se pudo guardar players.txt: ", "Could not save players.txt: ") + causa(e));
        }
    }

    // ----- Tabla -------------------------------------------------------------
    /** Orden de columnas por defecto (índices del modelo): Fecha · Jugador · Civ · Rival · Civ rival · Mapa · Modo · Rec · Resultado. */
    static final String ORDEN_COLUMNAS_DEFECTO = "0,1,2,5,6,4,3,7,8";

    void aplicarOrdenColumnas(String orden) {
        try {
            String[] partes = orden.split(",");
            if (partes.length != table.getColumnCount()) return;
            for (int destino = 0; destino < partes.length; destino++) {
                int modelo = Integer.parseInt(partes[destino].trim());
                int actual = table.convertColumnIndexToView(modelo);
                if (actual >= 0 && actual != destino) table.getColumnModel().moveColumn(actual, destino);
            }
        } catch (RuntimeException ex) { log("columnas: orden ilegible: " + orden); }
    }

    void guardarColumnas() {
        StringBuilder orden = new StringBuilder();
        for (int v = 0; v < table.getColumnCount(); v++) { if (v > 0) orden.append(','); orden.append(table.convertColumnIndexToModel(v)); }
        StringBuilder anchos = new StringBuilder();
        for (int m = 0; m < table.getModel().getColumnCount(); m++) { if (m > 0) anchos.append(','); anchos.append(table.getColumnModel().getColumn(table.convertColumnIndexToView(m)).getWidth()); }
        guardarConfig("tabla_orden", orden.toString());
        guardarConfig("tabla_anchos", anchos.toString());
    }

    final class MatchesTableModel extends AbstractTableModel {
        final String[] cols = { t("Fecha", "Date"), t("Jugador", "Player"), "Civ", t("Modo", "Mode"), t("Mapa", "Map"),
                t("Rival", "Opponent"), t("Civ rival", "Opp. civ"), "Rec", t("Resultado", "Result") };
        @Override public int getRowCount() { return view.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) {
            return switch (c) { case 0 -> FechaCell.class; default -> String.class; };
        }
        @Override public Object getValueAt(int r, int c) {
            Match m = view.get(r);
            return switch (c) {
                case 0 -> m.finished == null && !enCursoReal(m)
                        ? new FechaCell(null, m.started)
                        : new FechaCell(m.finished);
                case 1 -> revelada(m) ? refConVeredicto(m) : refNombre(m);
                case 2 -> m.civDe(m.refId);
                case 3 -> m.mode;
                case 4 -> m.map;
                case 5 -> rivalTexto(m, revelada(m));
                case 6 -> m.civRival();
                case 8 -> m.finished == null ? "" : (revelada(m) ? "\u25C9" : "\u25CE");   // ojo abierto / cerrado (también en GTE)
                case 7 -> !m.estado.isBlank() ? m.estado
                          : m.enJuego ? t("✓✓ en juego", "✓✓ in game")
                          : m.enDisco ? t("✓ en disco", "✓ on disk")
                          : (m.povsConRec() > 0 ? m.povsConRec() + " POV" : "¿?");
                default -> "";
            };
        }
    }

    // ----- HTTP --------------------------------------------------------------
    /** La pausa por 429 del companion, contada en la barra de estado (ApiClient avisa; la red ya no toca Swing). */
    static void avisarPausa429(long seg) {
        SpoilerFreeRecs app = null; for (Frame f : Frame.getFrames()) if (f instanceof SpoilerFreeRecs sf) app = sf;
        final SpoilerFreeRecs appF = app;
        if (appF != null) SwingUtilities.invokeLater(() -> appF.status.setText(t("La API pide calma (429): la app pausa las consultas ", "The API asks for calm (429): the app pauses its requests for ") + seg + t(" s y sigue sola.", " s and carries on by itself.")));
    }
    /** Lee control.json (multiplicadores de intervalos, interruptores, mensaje) al arrancar y cada hora. */
    static void cargarControl() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(CONTROL_URL)).timeout(Duration.ofSeconds(20)).header("User-Agent", UA).GET().build();
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return;
            Object root = Json.parse(r.body());
            if (!(root instanceof Map<?, ?> m)) return;
            CONTROL.clear();
            for (Map.Entry<?, ?> en : m.entrySet()) if (en.getValue() != null) CONTROL.put(String.valueOf(en.getKey()), en.getValue());
            log("control.json: " + CONTROL);
            Object msg = CONTROL.get("mensaje");
            if (msg instanceof String txt && !txt.isBlank() && !txt.equals(leerConfig("control_msg_visto", ""))) {
                guardarConfig("control_msg_visto", txt);
                SpoilerFreeRecs app = null; for (Frame f : Frame.getFrames()) if (f instanceof SpoilerFreeRecs sf) app = sf;
                final SpoilerFreeRecs appF = app;
                if (appF != null) SwingUtilities.invokeLater(() -> appF.status.setText(txt));
            }
        } catch (Exception ex) { log("control.json: " + causa(ex)); }
    }
    /** Cliente único de la API: freno, 429, cancelación (api.ApiClient). Estas dos funciones quedan como fachada. */
    static final ApiClient API_CLIENTE = new ApiClient(THROTTLE, TRANSPORTE, SpoilerFreeRecs::avisarPausa429, () -> detieneEsteHilo());
    /** Endpoints del companion con su URL en un solo sitio (api.CompanionApi). Va DESPUÉS de API_CLIENTE: los static final se inicializan en orden de texto. */
    static final CompanionApi COMPANION = new CompanionApi(API_CLIENTE);

    static String httpText(String url) throws IOException, InterruptedException { return API_CLIENTE.texto(url); }

    static void dormir(long ms) {
        long fin = System.currentTimeMillis() + ms;
        while (true) {
            long resta = fin - System.currentTimeMillis();
            if (resta <= 0 || (stopOperacion && opEnCurso)) return;   // el freno solo corta esperas de operaciones cancelables
            try { Thread.sleep(Math.min(250, resta)); }
            catch (InterruptedException e) { return; }   // sin re-marcar: los hilos del pool se reutilizan
        }
    }

    /** Búsqueda de jugadores por nombre en el companion: {id, nombre, etiqueta legible}. */
    /** Sugerencias locales del índice nocturno de nombres (top 40.000): {pid, nombre, «nombre · país · ELO»}, hasta 8, las de más ELO primero. */
    static List<String[]> buscarLocal(String q) {
        List<String[]> out = new ArrayList<>();
        if (q == null || q.length() < 2 || NOMBRES_AYER.isEmpty()) return out;
        String ql = q.toLowerCase(Locale.ROOT);
        List<Map.Entry<Long, String[]>> l = new ArrayList<>();
        for (Map.Entry<Long, String[]> en : NOMBRES_AYER.entrySet()) if (en.getValue()[0].toLowerCase(Locale.ROOT).contains(ql)) l.add(en);
        l.sort((a, b) -> { int[] ea = ELO_AYER.get(a.getKey()), eb = ELO_AYER.get(b.getKey()); return Integer.compare(eb == null ? 0 : eb[0], ea == null ? 0 : ea[0]); });
        for (Map.Entry<Long, String[]> en : l) { int[] e = ELO_AYER.get(en.getKey()); out.add(new String[]{ String.valueOf(en.getKey()), en.getValue()[0], en.getValue()[0] + (en.getValue()[1].isBlank() ? "" : " · " + en.getValue()[1].toUpperCase(Locale.ROOT)) + (e != null && e[0] > 0 ? " · " + e[0] : "") }); if (out.size() >= 8) break; }
        return out;
    }
    /** Sugerencias al teclear: el índice local si tiene algo (sin llamada); si no, la API. */
    static List<String[]> sugerirPerfiles(String q) {
        List<String[]> local = buscarLocal(q);
        return local.isEmpty() ? buscarPerfiles(q) : local;
    }
    /** Búsqueda explícita (Enter): la API y el índice local, juntos y sin duplicados; primero lo local. Así un nick cambiado hace poco (que el volcado aún no conoce) también aparece. */
    static List<String[]> buscarPerfiles(String q) {
        List<String[]> local = buscarLocal(q), out = new ArrayList<>(local);
        Set<String> vistos = new HashSet<>(); for (String[] r : local) vistos.add(r[0]);
        for (String[] r : buscarPerfilesApi(q)) if (vistos.add(r[0])) out.add(r);
        return out;
    }
    static List<String[]> buscarPerfilesApi(String q) {
        List<String[]> out = new ArrayList<>();
        try {
            Object root = COMPANION.buscarPerfiles(q);   // sin reintento, como antes
            for (Object o : arr(val(obj(root), "profiles"))) {
                Map<String, Object> p = obj(o);
                long id = lng(val(p, "profile_id", "profileId"));
                if (id <= 0) continue;
                String name = str(val(p, "name"));
                String pais = str(val(p, "country"));
                aprenderPais(id, pais);
                long games = lng(val(p, "games"));
                out.add(new String[]{ String.valueOf(id), name, name + (pais != null ? "  [" + pais + "]" : "") + "  ·  " + id + (games > 0 ? "  ·  " + games + t(" partidas", " games") : "") });
            }
        } catch (Exception ex) { log("buscarPerfiles: " + causa(ex)); }
        return out;
    }
}
