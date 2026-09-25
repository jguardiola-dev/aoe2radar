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
import dev.tirador.aoe2radar.api.Recs;
import dev.tirador.aoe2radar.api.SocketVivo;
import dev.tirador.aoe2radar.api.SteamApi;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.cache.Anotaciones;
import dev.tirador.aoe2radar.cache.CacheMemoria;
import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.RecsDisco;
import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.CivFila;
import dev.tirador.aoe2radar.model.Clasificacion;
import dev.tirador.aoe2radar.model.Directo;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.FilaClasificacion;
import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Matchup;
import dev.tirador.aoe2radar.model.PaginaLb;
import dev.tirador.aoe2radar.model.PaginaPartidas;
import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.PerfilEncontrado;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.service.Aleatorio.ProveedorPaginas;
import dev.tirador.aoe2radar.service.AnioDesdeSfr;
import dev.tirador.aoe2radar.service.AnotacionesService;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.BusquedaPerfilesCompanion;
import dev.tirador.aoe2radar.service.Campanas;
import dev.tirador.aoe2radar.service.ControlService;
import dev.tirador.aoe2radar.service.DescargaRecs;
import dev.tirador.aoe2radar.service.EloSesion;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.Familias;
import dev.tirador.aoe2radar.service.FiltroLista;
import dev.tirador.aoe2radar.service.FormaCompanion;
import dev.tirador.aoe2radar.service.FormService;
import dev.tirador.aoe2radar.service.HistorialPerfil;
import dev.tirador.aoe2radar.service.Juego;
import dev.tirador.aoe2radar.service.ListaSeguidos;
import dev.tirador.aoe2radar.service.LiveService;
import dev.tirador.aoe2radar.service.NombresJuego;
import dev.tirador.aoe2radar.service.NombresStats;
import dev.tirador.aoe2radar.service.PerfilesCompanion;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.RatingsServiceSfr;
import dev.tirador.aoe2radar.service.RecService;
import dev.tirador.aoe2radar.service.StatsService;
import dev.tirador.aoe2radar.service.StatsServiceSfr;
import dev.tirador.aoe2radar.service.TechTreeServiceDatos;
import dev.tirador.aoe2radar.service.TopLadderService;
import dev.tirador.aoe2radar.service.TwitchService;
import dev.tirador.aoe2radar.service.TwitchServiceCompanion;
import dev.tirador.aoe2radar.sfrdata.SfrDataClient;
import dev.tirador.aoe2radar.sfrdata.Snapshots;
import dev.tirador.aoe2radar.ui.CivStatsView;
import dev.tirador.aoe2radar.ui.DirectosView;
import dev.tirador.aoe2radar.ui.FiltroStats;
import dev.tirador.aoe2radar.ui.Listas;
import dev.tirador.aoe2radar.ui.LiveNowView;
import dev.tirador.aoe2radar.ui.PanelScrollable;
import dev.tirador.aoe2radar.ui.PerfilView;
import dev.tirador.aoe2radar.ui.RatingsView;
import dev.tirador.aoe2radar.ui.SelectorRangoElo;
import dev.tirador.aoe2radar.ui.Tareas;
import dev.tirador.aoe2radar.ui.TechTreeView;
import dev.tirador.aoe2radar.ui.WrapLayout;
import dev.tirador.aoe2radar.util.Config;
import dev.tirador.aoe2radar.util.Json;
import dev.tirador.aoe2radar.util.Reloj;

import static dev.tirador.aoe2radar.api.Cancelacion.detieneEsteHilo;
import static dev.tirador.aoe2radar.api.Cancelacion.hiloOperacion;
import static dev.tirador.aoe2radar.api.Cancelacion.opEnCurso;
import static dev.tirador.aoe2radar.api.Cancelacion.stopOperacion;
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
import static dev.tirador.aoe2radar.cache.Anotaciones.ALIASES;
import static dev.tirador.aoe2radar.cache.Anotaciones.NOTAS;
import static dev.tirador.aoe2radar.cache.Anotaciones.cargarAliases;
import static dev.tirador.aoe2radar.cache.Anotaciones.cargarNotas;
import static dev.tirador.aoe2radar.cache.Anotaciones.nombreVisible;
import static dev.tirador.aoe2radar.cache.Anotaciones.notaDe;
import static dev.tirador.aoe2radar.cache.CachePerfiles.PERFIL_CACHE;
import static dev.tirador.aoe2radar.cache.Canales.CANAL_DE;
import static dev.tirador.aoe2radar.cache.Canales.aprenderCanal;
import static dev.tirador.aoe2radar.cache.Canales.cargarCanales;
import static dev.tirador.aoe2radar.cache.Catalogos.CIVS_CAT;
import static dev.tirador.aoe2radar.cache.Catalogos.MAPAS_CAT;
import static dev.tirador.aoe2radar.cache.Catalogos.cargarCatalogos;
import static dev.tirador.aoe2radar.cache.HistorialDisco.ACTIVIDAD_CACHE;
import static dev.tirador.aoe2radar.cache.HistorialDisco.ACT_DIAS;
import static dev.tirador.aoe2radar.cache.HistorialDisco.PERFILES_DIR;
import static dev.tirador.aoe2radar.cache.HistorialDisco.cargarActividad;
import static dev.tirador.aoe2radar.cache.HistorialDisco.guardarActividad;
import static dev.tirador.aoe2radar.cache.Paises.PAIS_DE;
import static dev.tirador.aoe2radar.cache.Paises.aprenderPais;
import static dev.tirador.aoe2radar.cache.Paises.cargarPaises;
import static dev.tirador.aoe2radar.cache.Paises.guardarPaises;
import static dev.tirador.aoe2radar.cache.Paises.paisDe;
import static dev.tirador.aoe2radar.cache.RecsDisco.RECS_DIR;
import static dev.tirador.aoe2radar.cache.RecsDisco.destino;
import static dev.tirador.aoe2radar.cache.RecsDisco.maxGteEnDisco;
import static dev.tirador.aoe2radar.cache.Vivos.candidatoSocket;
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
import static dev.tirador.aoe2radar.service.CalculoStats.POCAS_PARTIDAS;
import static dev.tirador.aoe2radar.service.CalculoStats.duracionMedia;
import static dev.tirador.aoe2radar.service.ConsultasLadder.ladderNombre;
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
import static dev.tirador.aoe2radar.service.NombresStats.claveTechTree;
import static dev.tirador.aoe2radar.service.NombresStats.modoNombre;
import static dev.tirador.aoe2radar.service.NombresStats.nombreCivStats;
import static dev.tirador.aoe2radar.service.NombresStats.nombreMapaStats;
import static dev.tirador.aoe2radar.service.NombresStats.raizCiv;
import static dev.tirador.aoe2radar.service.NombresStats.tramoNombre;
import static dev.tirador.aoe2radar.service.NombresStats.ventanaNombre;
import static dev.tirador.aoe2radar.service.ProfileService.ACT_MAS_PAGINAS;
import static dev.tirador.aoe2radar.service.ProfileService.ACT_MAX_PAGINAS;
import static dev.tirador.aoe2radar.service.ProfileService.ACT_MIN;
import static dev.tirador.aoe2radar.service.ProfileService.ACT_PAGINAS_RAPIDAS;
import static dev.tirador.aoe2radar.service.ReglasPartida.DURACION_TRAMOS;
import static dev.tirador.aoe2radar.service.ReglasPartida.LADDER_IDS;
import static dev.tirador.aoe2radar.service.ReglasPartida.franjaDe;
import static dev.tirador.aoe2radar.service.ReglasPartida.franjasCentradas;
import static dev.tirador.aoe2radar.service.ReglasPartida.marcarFantasmas;
import static dev.tirador.aoe2radar.service.ReglasPartida.modoPrincipal;
import static dev.tirador.aoe2radar.service.ReglasPartida.posicionEnEquipo;
import static dev.tirador.aoe2radar.service.ReglasPartida.rivalCoincide;
import static dev.tirador.aoe2radar.service.ReglasPartida.tramoDuracion;
import static dev.tirador.aoe2radar.sfrdata.CivStats.VENTANAS_STATS;
import static dev.tirador.aoe2radar.sfrdata.Ladder.clanes;
import static dev.tirador.aoe2radar.sfrdata.Ladder.ladderAsegurar;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.ELO_AYER;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.ELO_HACE7;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.NOMBRES_AYER;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.cargarEloAyer;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.muestraAyer;
import static dev.tirador.aoe2radar.util.Config.CONFIG_FILE;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.Formato.fmtTop;
import static dev.tirador.aoe2radar.util.Formato.miles;
import static dev.tirador.aoe2radar.util.Formato.pct1;
import static dev.tirador.aoe2radar.util.Formato.truncarPx;
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
import static dev.tirador.aoe2radar.ui.Componentes.PALETA_LADDER;
import static dev.tirador.aoe2radar.ui.Componentes.colorHex;
import static dev.tirador.aoe2radar.ui.Componentes.colorSecundario;
import static dev.tirador.aoe2radar.ui.Componentes.colorSecundarioHex;
import static dev.tirador.aoe2radar.ui.Componentes.colorWr;
import static dev.tirador.aoe2radar.ui.Componentes.estiloCab;
import static dev.tirador.aoe2radar.ui.Componentes.etiquetaK;
import static dev.tirador.aoe2radar.ui.Componentes.listaVertical;
import static dev.tirador.aoe2radar.ui.Componentes.pasoBonito;
import static dev.tirador.aoe2radar.ui.Componentes.subirArriba;
import static dev.tirador.aoe2radar.ui.Componentes.tituloSeccion;
import static dev.tirador.aoe2radar.ui.Iconos.banderaHtml;
import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.ui.Iconos.iconoCiv;
import static dev.tirador.aoe2radar.ui.Iconos.iconoMapa;
import static dev.tirador.aoe2radar.ui.Listas.Celda;
import static dev.tirador.aoe2radar.ui.Listas.Pct;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;

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
import static dev.tirador.aoe2radar.ui.Componentes.colorVivoHex;
import static dev.tirador.aoe2radar.ui.Componentes.colorVivoTabla;
import static dev.tirador.aoe2radar.ui.Componentes.iconoCuadrado;
import static dev.tirador.aoe2radar.util.Formato.reloj;
import static dev.tirador.aoe2radar.service.ReglasPartida.modoCorto;
import static dev.tirador.aoe2radar.service.NombresStats.nombreMapaClave;
import static dev.tirador.aoe2radar.service.NombresStats.posicionNombre;

public class SpoilerFreeRecs extends JFrame implements dev.tirador.aoe2radar.ui.Navegacion {

    // ----- Configuración -----------------------------------------------------
    static final Path   PLAYERS_FILE = Path.of("players.txt");
    static final String FLATLAF_JAR  = "flatlaf-3.7.2.jar";
    static final String DONAR_URL    = "https://paypal.me/12Tirador/5EUR";
    static final String TEMA_CLARO = "claro", TEMA_OSCURO = "oscuro", TEMA_SISTEMA = "sistema";
    static final int    PER_PAGE     = 50;   // partidas consultadas por jugador
    static final long   PAUSA_MS     = 300;  // cortesía entre llamadas

    // tarjeta, filaBarra, enlaceVerTodo y las ventanas de lista/tabla completa (ver ui.Listas); RegresionCapturas la usa por el nombre del campo.
    final Listas listas = new Listas(this, v -> ultimoClicCtrl = v);
    /** Menús de jugador para las vistas: hoy los construye la ventana con sus métodos (ver ui.MenusJugador). */
    final dev.tirador.aoe2radar.ui.MenusJugador menus = new dev.tirador.aoe2radar.ui.MenusJugador() {
        @Override public void menuContextual(long pid, String nombre, MouseEvent e) { menuContextualJugador(pid, nombre, e); }
        @Override public JMenu enPartida(long pid) { return menuEnPartida(pid); }
        @Override public JMenu deJugador(long pid, String nombre) { return menuDeJugador(pid, nombre); }
        @Override public JMenu perfilNavegador(long pid) { return menuPerfilNavegador(pid); }
        @Override public JMenuItem itemJugadorPartida(MatchPlayer p) { return SpoilerFreeRecs.this.itemJugadorPartida(p); }
        @Override public Integer elo1v1Conocido(long pid) { return SpoilerFreeRecs.this.elo1v1Conocido(pid); }
    };

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

    // ----- Estado UI ---------------------------------------------------------
    final List<Player> todosJugadores = new ArrayList<>();          // fuente de verdad (todos los grupos)
    /** Persistencia, grupos y altas/bajas/movimientos de la watchlist: ver service.ListaSeguidos. */
    final ListaSeguidos listaSeguidos = new ListaSeguidos(PLAYERS_FILE, GRUPO_GENERAL, Config::leerConfig, Config::guardarConfig);
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
    /** Quién está en partida ahora (ver service.EstadoVivo): un solo dueño para el socket, los barridos y la UI. */
    static final EstadoVivo VIVO = EstadoVivo.SISTEMA;
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
    final CacheMemoria<Long, Object[]> perfilCardCache = CacheService.SISTEMA.memoria(Caducidad.TARJETA);   // pid -> { htmlDatos, int[] spark }
    static final String TOP_LADDER = "\u2605 Top ladder";
    static final String TOP_PAIS = t("\u2605 Top pa\u00eds", "\u2605 Country top");
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
    final Map<Long, String[]> twitchLive = new HashMap<>();   // pid -> { canal, título, viewers }; escribe ui.DirectosView, leen Live now/Perfil
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
        boolean tablaVisible = recsCards != null && recsCards.isShowing() && !(directosBtn != null && directosBtn.isSelected()) && !(techTreeBtn != null && techTreeBtn.isSelected()) && !(ladderBtn != null && ladderBtn.isSelected()) && !(civStatsBtn != null && civStatsBtn.isSelected()) && !(perfil != null && perfil.abierto()) && !(liveNow != null && liveNow.ahoraAbierta);
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
    /** Campo de instancia (no static): se inicializa después de COMPANION, que es static (SpoilerFreeRecs paso FormService). */
    final FormService formaService = new FormaCompanion(COMPANION, Snapshots.ELO, Reloj.SISTEMA);
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
        long ahora = Reloj.SISTEMA.ahoraMs();   // una sola lectura del reloj para todo el lote, como la 1.1
        for (Player p : objetivo) if (formaService.pendiente(ts.getOrDefault(p.id(), 0L), ahora)) pendientes.add(p);
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
                    Forma[] f = formaService.porResta(p.id(), eloWatch::get, gamesWatch::get);
                    if (f == null) { porApi.add(p); continue; }
                    forma24.put(p.id(), f[0]); formaTs24.put(p.id(), ahoraTs);
                    if (f[1] != null) { forma7d.put(p.id(), f[1]); formaTs7d.put(p.id(), ahoraTs); } else if (horas > 24) porApi.add(p);
                }
                publish(t("Forma: ", "Recent form: ") + (pendientes.size() - porApi.size()) + t(" del snapshot nocturno", " from the nightly snapshot") + (porApi.isEmpty() ? "" : " · " + porApi.size() + t(" consultas", " requests")));
                for (Player p : porApi) {   // 2) quien no está en el snapshot: su serie de rating, exacta (una llamada por jugador)
                    if (stopOperacion) break;
                    try {
                        Forma[] ambas = formaService.porSerie(p.id());
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
    /** La vista Ratings (card "ladder"): ver ui.RatingsView. */
    RatingsView ratings;

    // ----- «★ Top clan»: una vista más de la watchlist -----
    void cargarTopClan() {
        String tag = clanField == null ? "" : clanField.getText().trim();
        if (tag.isEmpty()) { status.setText(t("Escribe el tag del clan (p. ej. R1).", "Type the clan tag (e.g. R1).")); return; }
        status.setText(clanes.isEmpty() ? t("Descargando la lista de clanes…", "Downloading the clan list…") : t("Cargando el clan…", "Loading the clan…"));
        new Thread(() -> {
            TopLadderService.ResultadoClan res = TOP_LADDER_SERVICE.topClan(tag);
            SwingUtilities.invokeLater(() -> {
                if (res.error() != null) { status.setText(t("No se pudo cargar la lista de clanes: ", "Couldn't load the clan list: ") + res.error()); return; }
                topLadder.clear();
                ultimoTopMs = 0; directos.reiniciarThrottle();   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
                lastTop.clear(); rankTop.clear();
                for (TopLadderService.FilaClan f : res.miembros()) {
                    topLadder.add(new Player(f.pid(), f.nombre(), TOP_CLAN));
                    eloWatch.put(f.pid(), f.rating());
                    rankTop.put(f.pid(), topLadder.size());
                }
                guardarConfig("clan_tag", tag);
                aplicarFiltroGrupo();
                actualizarIndicadoresVivos();
                status.setText(res.miembros().isEmpty() ? t("Ningún clan del ladder 1v1 se llama «", "No 1v1 ladder clan is called \u201C") + tag + t("» (elige uno de las sugerencias).", "\u201D (pick one from the suggestions).")
                        : t("Clan ", "Clan ") + tag + ": " + res.miembros().size() + t(" jugadores en el ladder 1v1 (resumen diario).", " players on the 1v1 ladder (daily summary)."));
            });
        }, "top-clan").start();
    }

    /** Ratings (card "ladder"): la vista y el presentador viven en ui.RatingsView/ui.RatingsPresenter;
     *  aqui solo queda el cromo (botones, historial, CardLayout), igual que el resto de vistas de la fase 3. */
    @Override public void abrirLadder() {
        registrarDestino(new Destino("ladder", 0, null, null));
        if (ladderBtn != null && !ladderBtn.isSelected()) ladderBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        if (perfil != null) perfil.cerrar();
        if (perfilBtn != null) perfilBtn.setSelected(false);
        if (liveNow != null) liveNow.ahoraAbierta = false; if (ahoraBtn != null) ahoraBtn.setSelected(false);
        if (techTreeBtn != null && techTreeBtn.isSelected()) cerrarTechTree();
        ((CardLayout) centroCards.getLayout()).show(centroCards, "ladder");
        ratings.alAbrirAntes();
        taparResultados(); apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        ratings.alAbrirDespues();
    }

    // =====================================================================================
    // CIV STATS — resúmenes de sfr-data (civstats/ventanas/vN.json.gz y tendencias.json.gz):
    // winrate y pick rate por civ, modo, mapa y tramo de ELO; matchups 1v1; tendencias por mes
    // datos y cálculos: sfrdata.CivStats y service.CalculoStats, tras el contrato de service.StatsService
    // =====================================================================================
    /** Asegura las ventanas de Civ Stats y hace sus cálculos (ver service.StatsService). */
    final StatsService stats = new StatsServiceSfr(SfrDataClient.SISTEMA);
    /** Filtros de Civ Stats (y de la banda del Tech tree, que los comparte): ver ui.FiltroStats. */
    final FiltroStats filtroStats = new FiltroStats(leerConfig("stats_modo", "rm_1v1"), leerConfig("stats_ventana", "30"), leerConfig("stats_mapa", "*"), leerConfig("stats_tramo", "*"));
    JToggleButton civStatsBtn;
    /** La pestaña Civ Stats (winrate, pick rate, tendencias, matchups): ver ui.CivStatsView. */
    CivStatsView civStats;

    @Override public void abrirCivStats() {
        registrarDestino(new Destino("civstats", 0, null, null));
        if (civStatsBtn != null && !civStatsBtn.isSelected()) civStatsBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (perfil != null) perfil.cerrar();
        if (perfilBtn != null) perfilBtn.setSelected(false);
        if (liveNow != null) liveNow.ahoraAbierta = false; if (ahoraBtn != null) ahoraBtn.setSelected(false);
        if (techTreeBtn != null && techTreeBtn.isSelected()) cerrarTechTree();
        ((CardLayout) centroCards.getLayout()).show(centroCards, "civstats");
        civStats.subirArriba();
        taparResultados(); apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        civStats.alAbrir();
    }

    // =====================================================================================
    // PERFIL — la página de un jugador: ver ui.PerfilView/ui.PerfilPresenter y, para el diálogo de cara a
    // cara, ui.CaraACaraDialogo/ui.CaraACaraPresenter. Aquí solo queda el cromo (botón, CardLayout, historial).
    // =====================================================================================
    JToggleButton perfilBtn;
    /** La pestaña Perfil (cabecera, actividad, calendario, cara a cara): ver ui.PerfilView. */
    PerfilView perfil;

    /** Delegado fino en ui.PerfilView/PerfilPresenter (calienta en segundo plano los perfiles ya guardados en disco).
     *  Sigue haciendo falta como método de la ventana porque TechTreePresenter.precargar() lo pide por el
     *  Anfitrion de TechTreeView; lo lanza el temporizador de arranque (8 s), no la apertura del tech tree. */
    void precalentarPerfiles() { perfil.precalentar(); }

    /** El botón «Perfil»: el jugador seleccionado en la watchlist o, si no hay, la página con el buscador. */
    void perfilDesdeBoton() {
        List<Player> sel = playersList.getSelectedValuesList();
        if (!sel.isEmpty()) abrirPerfil(sel.get(0).id(), nombreVisible(sel.get(0).id(), sel.get(0).name()));
        else if (ultimosSujetos.size() == 1 && recsCards != null && recsCards.isShowing()) abrirPerfil(ultimosSujetos.get(0).id(), nombreVisible(ultimosSujetos.get(0).id(), ultimosSujetos.get(0).name()));   // «Partidas de: X» → su perfil
        else if (perfil.pidAbierto() > 0 && ACTIVIDAD_CACHE.containsKey(perfil.pidAbierto())) abrirPerfil(perfil.pidAbierto(), perfil.nombreAbierto());
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
            if (civSel != null && m.players.stream().noneMatch(pl -> civSel.equalsIgnoreCase(pl.civ) || civSel.equalsIgnoreCase(techTree.claveCivDeNombre(pl.civ)))) continue;
            if (azarEnsenadas.contains(m.id)) continue;
            cand.add(m);
        }
        Collections.shuffle(cand, rnd);
        List<Match> out = new ArrayList<>(cand.subList(0, Math.min(10, cand.size())));
        for (Match m : out) azarEnsenadas.add(m.id);
        return out;
    }
    final Set<Long> azarEnsenadas = new HashSet<>();
    final Map<Long, Integer> gamesWatch = new java.util.concurrent.ConcurrentHashMap<>();

    /** Abre el perfil de un jugador; pid 0 = página vacía con el buscador. El cromo (botones, CardLayout, historial) vive
     *  aquí; la carga y la pintura son de ui.PerfilView (perfil.alAbrir). */
    @Override public void abrirPerfil(long pid, String nombre) {
        if (perfilBtn != null && !perfilBtn.isSelected()) perfilBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        if (techTreeBtn != null && techTreeBtn.isSelected()) cerrarTechTree();
        if (liveNow != null) liveNow.ahoraAbierta = false; if (ahoraBtn != null) ahoraBtn.setSelected(false);
        perfil.marcarAbierta();
        ((CardLayout) centroCards.getLayout()).show(centroCards, "perfil");
        subirArriba(perfil.panel());
        taparResultados(); apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        perfil.alAbrir(pid, nombre);
    }

    /** Abre a un jugador en una pestaña NUEVA (Ctrl+clic, «+»); la cuenta de pestañas es de ui.PerfilView. */
    @Override public void abrirPerfilEnPestana(long pid, String nombre) { perfil.abrirEnPestanaNueva(pid, nombre); }

    // =====================================================================================
    // LIVE NOW — partidas en curso de los 250 mejores del ladder 1v1 (tarjetas por partida) y las
    // terminadas en las últimas 2 horas (sin resultado, con la rec a un clic). La vista y el presentador
    // viven en ui.LiveNowView/ui.LiveNowPresenter; aquí solo queda el cromo (botón de pestaña, CardLayout,
    // historial) y lo que se queda con la watchlist (los seis métodos de MenusJugador, ELO_1V1, el toast y
    // la campanita: ver más abajo).
    // =====================================================================================
    JToggleButton ahoraBtn;
    /** La pestaña Live now: ver ui.LiveNowView. */
    LiveNowView liveNow;

    @Override public void abrirAhora() {
        registrarDestino(new Destino("ahora", 0, null, null));
        if (ahoraBtn != null && !ahoraBtn.isSelected()) ahoraBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        if (techTreeBtn != null && techTreeBtn.isSelected()) cerrarTechTree();
        if (perfil != null) perfil.cerrar();
        if (perfilBtn != null) perfilBtn.setSelected(false);
        liveNow.marcarAbierta();
        ((CardLayout) centroCards.getLayout()).show(centroCards, "ahora");
        liveNow.alAbrirAntes();
        taparResultados(); apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        liveNow.alAbrirDespues();
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
        Match m = VIVO.partida(pid);
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
    static final EloSesion ELO_1V1 = new EloSesion(VIVO, Reloj.SISTEMA, EloSesion.ESPERA);   // pid → ELO 1v1 RM (sesión; caduca al terminar una partida)
    Integer elo1v1Conocido(long pid) {
        Integer e = ELO_1V1.conocido(pid); if (e != null) return e > 0 ? e : null;
        Object[] f = liveNow != null ? liveNow.liveFicha(pid) : null; if (f != null && (Integer) f[2] > 0) return (Integer) f[2];
        e = SERVICIO_PERFIL.eloVinculada(pid); if (e != null && e > 0) return e;
        FichaPerfil perfil = SERVICIO_PERFIL.fichaConocida(pid); if (perfil != null && perfil.ladders().get("rm_1v1") instanceof int[] v && v[0] > 0) return v[0];
        return null;
    }
    JMenuItem itemJugadorPartida(MatchPlayer p) {
        String nombre = nombreVisible(p.id, p.name);
        Integer e1 = elo1v1Conocido(p.id);
        JMenuItem it = new JMenuItem(nombre + (e1 != null ? "  1v1 " + e1 : "") + (p.civ != null && !p.civ.isBlank() ? "  ·  " + p.civ : ""), iconoBandera(paisDe(p.id)));
        it.setIconTextGap(6);
        it.addActionListener(a -> abrirPerfil(p.id, nombre));
        if (((e1 == null && ELO_1V1.conocido(p.id) == null) || ELO_1V1.caducado(p.id)) && ELO_1V1.reservar(p.id)) new Thread(() -> {   // el ELO 1v1 se pide una vez y se rellena en el propio ítem
            long pedido = ELO_1V1.ahora(); Integer e = SERVICIO_PERFIL.elo1v1(p.id); ELO_1V1.apuntar(p.id, e, pedido);
            if (e != null && e > 0) SwingUtilities.invokeLater(() -> it.setText(nombre + "  1v1 " + e + (p.civ != null && !p.civ.isBlank() ? "  ·  " + p.civ : "")));
        }, "elo-1v1").start();
        return it;
    }

    // ----- Campanas: aviso cuando alguien de una vista marcada entra en partida -----
    // La red, la config y la deduplicacion de avisos viven en service.Campanas; aqui solo queda
    // Swing (boton, toast) y el estado compartido (campanaIds, socketExtra). Cableado junto al
    // propio Campanas, no al lado de COMPANION/LIVE/SERVICIO_PERFIL.
    final Campanas campanas = new Campanas(COMPANION, Config::leerConfig, Config::guardarConfig);
    JToggleButton campanaBtn;
    final Map<String, Set<Long>> campanaIds = new java.util.concurrent.ConcurrentHashMap<>();   // id de vista → jugadores vigilados
    javax.swing.Timer campanasTimer; JPanel toast; javax.swing.Timer toastTimer;

    String idVistaCampana() {
        if (modoClan()) return "\u2605clan|" + (clanField == null ? "" : clanField.getText().trim().toLowerCase(Locale.ROOT));
        if (modoPais()) return "\u2605pais|" + paisSel();
        if (modoTop()) return "\u2605ladder";
        return "grupo|" + String.valueOf(grupoCombo.getSelectedItem());
    }
    boolean campanaContiene(long pid) { return campanas.campanaContiene(pid, campanaIds); }

    void refrescarCampanaBtn() {
        if (campanaBtn == null) return;
        boolean on = campanas.campanas().contains(idVistaCampana());
        campanaBtn.setSelected(on);
        campanaBtn.setForeground(on ? (temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00)) : UIManager.getColor("Button.foreground"));
        campanaBtn.setToolTipText(on ? t("Avisos activados para esta lista: te avisa cuando alguien de aquí entre en partida (clic para apagar)", "Alerts on for this list: you get a notice when someone here starts a game (click to turn off)")
                : t("Activar para recibir un aviso cuando alguien de esta lista entre en partida", "Turn on to get a notice when someone in this list starts a game"));
    }

    void alternarCampana() {
        String id = idVistaCampana();
        boolean activo = campanas.alternar(id);
        refrescarCampanaBtn();
        refrescarCampanas();
        status.setText(activo ? t("Avisos activados para «", "Alerts on for \u201C") + id.replace("\u2605", "\u2605 ").replace("|", " ") + t("»: te avisaré cuando alguien entre en partida.", "\u201D: you'll get a notice when someone starts a game.") : t("Avisos apagados para esta lista.", "Alerts off for this list."));
    }

    /** Recalcula (en segundo plano, service.Campanas) los jugadores de cada vista con campana y los mete en el socket. Cada 15 min para tops, pais y clan. */
    void refrescarCampanas() {
        Set<String> s = campanas.campanas();
        if (s.isEmpty()) { campanaIds.clear(); SwingUtilities.invokeLater(this::sincronizarSocket); return; }
        new Thread(() -> {
            Map<String, Set<Long>> nuevo = campanas.calcularCampanaIds(s, todosJugadores);
            campanaIds.clear(); campanaIds.putAll(nuevo);   // no atomico entre el clear y el putAll: ver DEUDA
            Set<Long> todos = new HashSet<>(); for (Set<Long> x : nuevo.values()) todos.addAll(x);
            if (liveNow != null) { for (Object[] f : liveNow.topSnapshot()) todos.add((Long) f[0]); liveNow.socketExtra.retainAll(todos); liveNow.socketExtra.addAll(todos); }
            SwingUtilities.invokeLater(this::sincronizarSocket);
        }, "campanas").start();
    }

    /** «Mi partida»: si el que entra en partida soy yo (mi_pid), aviso con el rival (bandera, ELO, civ) y accesos a su perfil y al cara a cara. */
    void avisarMiPartida(long pid, Match m) {
        if (!campanas.tocaAvisarMiPartida(pid, m)) return;
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
                JButton cara = new JButton(t("Cara a cara", "Head-to-head")); cara.setFocusable(false); cara.setMargin(new Insets(0, 6, 0, 6)); cara.addActionListener(a -> { abrirPerfil(pid, leerConfig("mi_nombre", nombreVisible(pid, yo.name))); javax.swing.Timer tt = new javax.swing.Timer(1200, ev -> { if (perfil.pidAbierto() == pid) perfil.abrirCaraACaraCon(rival.id, nombreVisible(rival.id, rival.name)); }); tt.setRepeats(false); tt.start(); });
                JPanel acc = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); acc.setOpaque(false); acc.add(perf); acc.add(cara);
                toast.add(acc, BorderLayout.SOUTH); toast.revalidate();
            }
        });
    }
    /** Alguien vigilado entra en partida: si está en una lista con campana, aviso (toast dentro de la app; Windows si está minimizada). */
    void avisarSiCampana(long pid, Match m) {
        if (!campanas.tocaAvisar(pid, m, campanaIds)) return;
        String nombre = nombreVisible(pid, liveNow.ahoraNombre(pid).equals(String.valueOf(pid)) ? nombreDe(pid) : liveNow.ahoraNombre(pid));
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

    // Banderas, icono de civ e icono de mapa: ver ui.Iconos (cachés y escalado) y service.ImagenesJuego
    // (rutas fijas y descarga de miniaturas de mapa).
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
            if (me.getID() == MouseEvent.MOUSE_PRESSED && me.getButton() == 4) { if (!perfil.h2hAtrasSiProcede()) volverAtras(); me.consume(); return; }   // botones laterales del ratón: atrás / adelante (dentro del cara a cara, su propio atrás)
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

    // =====================================================================================
    // TECH TREE — la vista (árbol, ficha, banda de winrate) vive en ui.TechTreeView; aquí solo el
    // cromo compartido con el resto de pestañas: botón, CardLayout, historial y la watchlist plegada.
    // =====================================================================================
    TechTreeView techTree;
    int ttDivisorPrevio = -1;
    JToggleButton techTreeBtn;

    /** Abre el panel (plegando la watchlist) y, si se pide, en una civ concreta. */
    @Override public void abrirTechTree(String civ) {
        registrarDestino(new Destino("techtree", 0, null, civ != null ? civ : techTree.civPedida()));
        if (techTreeBtn != null && !techTreeBtn.isSelected()) techTreeBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        if (perfil != null) perfil.cerrar();
        if (perfilBtn != null) perfilBtn.setSelected(false);
        if (liveNow != null) liveNow.ahoraAbierta = false; if (ahoraBtn != null) ahoraBtn.setSelected(false);
        ((CardLayout) centroCards.getLayout()).show(centroCards, "techtree");
        taparResultados(); apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        if (splitPrincipal != null && ttDivisorPrevio < 0) {   // la watchlist se pliega: el árbol necesita el ancho
            ttDivisorPrevio = splitPrincipal.getDividerLocation();
            splitPrincipal.setOneTouchExpandable(true);
            splitPrincipal.setDividerLocation(0);
        }
        techTree.alAbrir(civ);
    }

    void cerrarTechTree() {
        techTree.ocultarDetalle();
        if (techTreeBtn != null) techTreeBtn.setSelected(false);
        if (splitPrincipal != null && ttDivisorPrevio >= 0) {   // la watchlist vuelve a su ancho
            splitPrincipal.setDividerLocation(ttDivisorPrevio);
            SwingUtilities.invokeLater(() -> { if (norteWatchRef != null) { norteWatchRef.revalidate(); norteWatchRef.repaint(); } });   // las filas del norte se recalculan con el ancho ya restaurado
            splitPrincipal.setOneTouchExpandable(false);
            ttDivisorPrevio = -1;
        }
        mostrarDirectos(false);
    }

    // ----- Partidas en curso en tiempo real: websocket «ongoing-matches» del companion -----------------
    // Se abre una conexión con los ids de la vista actual y el servidor empuja matchAdded /
    // matchUpdated / matchRemoved. El barrido por lotes sigue de respaldo (y no quita puntos
    // mientras el socket esté sano).
    /** El protocolo del socket (ver api.SocketVivo); aquí se decide qué significa cada evento. */
    final SocketVivo socketVivo = new SocketVivo(SocketVivo.HTTP, new SocketVivo.Oyente() {
        @Override public void conectado(boolean trasCaida) { if (trasCaida && liveNow != null && liveNow.ahoraAbierta) SwingUtilities.invokeLater(() -> liveNow.refrescar(true)); }   // tras una caída, un barrido para reparar el estado
        @Override public void eventos(List<SocketVivo.Evento> eventos, Set<Long> ids) { procesarEventosSocket(eventos, ids); }
    }, Reloj.SISTEMA, SocketVivo.planificadorSistema());
    long ultimoResyncMs;

    /** Conecta (o reconecta) con los ids visibles; si no cambian y el socket está sano, no hace nada. */
    void sincronizarSocket() {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        for (int i = 0; i < playersModel.size(); i++) ids.add(playersModel.get(i).id());
        for (Player p : todosJugadores) ids.add(p.id());      // todos los grupos, no solo la vista actual: así cambiar de pestaña no reconecta el socket (y no se pierden eventos en el hueco)
        synchronized (topLadder) { for (Player p : topLadder) ids.add(p.id()); }
        if (liveNow != null) ids.addAll(liveNow.socketExtra);   // Live now abierto y vistas con campana: también se vigilan aunque no estén a la vista
        try { String mi = leerConfig("mi_pid", ""); if (!mi.isBlank()) ids.add(Long.parseLong(mi)); } catch (Exception ignored) { }   // «Mi partida»: mi propio id siempre vigilado
        socketVivo.sincronizar(ids);
    }

    /** Antes de marcar a alguien como jugando por un evento del socket, se comprueba en la API que la
     *  partida no esté ya terminada (el companion a veces anuncia partidas viejas como vivas). */
    void confirmarEventoSocket(Match m, List<Long> pids) {
        new Thread(() -> {
            LiveService.Comprobacion c = LIVE.comprobar(pids.get(0), m.id, 5);
            if (c.error() != null) log("socket: no se pudo confirmar la partida " + m.id + ": " + causa(c.error()));
            boolean viva = c.veredicto() != LiveService.Veredicto.TERMINADA;   // sin datos: el beneficio de la duda
            if (!viva) { log("socket: partida " + m.id + " ya terminada según la API: fantasma ignorado"); return; }
            for (long pid : pids) { if (VIVO.terminada(m.id)) continue; String resumen = resumenVivo(m, pid); if (!VIVO.marcarJugando(pid, m.id, resumen)) continue;   /* terminada entretanto: ni Live now ni avisos */ VIVO.guardarPartida(pid, m); if (liveNow != null) liveNow.liveEvento(pid, m, false); avisarSiCampana(pid, m); avisarMiPartida(pid, m); }
            SwingUtilities.invokeLater(() -> { actualizarIndicadoresVivos(); refrescarAlturasWatch(); playersList.repaint(); table.repaint(); });
        }, "socket-confirmar").start();
    }

    /** Los eventos de un mensaje del socket (vacío si era un pong), ya traducidos por SocketVivo. */
    void procesarEventosSocket(List<SocketVivo.Evento> eventos, Set<Long> ids) {
        boolean cambio = false;
        for (SocketVivo.Evento ev : eventos) {
            if (ev instanceof SocketVivo.Quitada q) {
                long mid = q.matchId();
                for (long pid : VIVO.quitarPartida(mid)) { if (liveNow != null) liveNow.liveEvento(pid, null, true); cambio = true; }
                continue;
            }
            String tipo = ((SocketVivo.Partida) ev).tipo();
            Match m = ((SocketVivo.Partida) ev).partida();
            int vigilados = 0; for (MatchPlayer mp : m.players) if (ids.contains(mp.id)) vigilados++;
            log("socket: " + tipo + " partida " + m.id + " started=" + m.started + " finished=" + m.finished + " · " + vigilados + " vigilados");
            if (m.id <= 0) continue;   // sin id de partida no hay nada que espectar
            List<Long> candidatos = new ArrayList<>();
            for (MatchPlayer mp : m.players) {
                if (!ids.contains(mp.id)) continue;
                if (m.finished != null) { VIVO.apuntarTerminada(m.id); VIVO.marcarFuera(mp.id); if (liveNow != null) liveNow.liveEvento(mp.id, m, true); cambio = true; }
                // en curso DE VERDAD: empezada (no un lobby), sin terminar y hace menos de 3 h
                else if (candidatoSocket(m, Instant.now()) && !VIVO.terminada(m.id) && !Long.valueOf(m.id).equals(VIVO.matchDe(mp.id))) candidatos.add(mp.id);
            }
            if (!candidatos.isEmpty()) confirmarEventoSocket(m, candidatos);   // la API tiene la última palabra (fantasmas fuera)
        }
        if (cambio) SwingUtilities.invokeLater(() -> { actualizarIndicadoresVivos(); refrescarAlturasWatch(); playersList.repaint(); table.repaint(); });
    }

    /** Resumen compacto de la partida en curso, para la sublínea y el tooltip:
     *  1v1 → «vs Rival (CivP–CivR) · Mapa»; equipos → «TG 4v4 · Mapa». */
    static String resumenVivo(Match m, long pid) {
        VIVO.registrar(m, pid);   // de paso: partida, «visto» y rival (ver EstadoVivo.registrar); todos los caminos que detectan a alguien en partida pasan por aquí
        try {
            if (m.players.size() == 2) {
                MatchPlayer yo = null, riv = null;
                for (MatchPlayer p : m.players) { if (p.id == pid) yo = p; else riv = p; }
                if (riv == null) return null;
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
        else if (perfil.pidAbierto() > 0 && playersList.getSelectedIndices().length == 0 && (perfil.abierto() || modoTop())) { quien = perfil.nombreAbierto(); objetivoEtiqueta = new Player(perfil.pidAbierto(), perfil.nombreAbierto(), ""); }   // el perfil abierto (o el último visto, si en el top no hay nadie seleccionado)
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
        Match m = VIVO.partida(pid);
        String amb = temaOscuroActivo ? "#ffd56a" : "#b06a00";
        if (m == null || !enCursoReal(m)) {
            String info = VIVO.info(pid);
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

    // truncarPx: ver util.Formato (recorte con puntos suspensivos, compartido por Live now y la watchlist).

    /** Consulta GitHub Releases (una llamada, sin claves) y enseña el botón si hay versión nueva. La red y la lectura
     *  del JSON viven en service.ControlService#ultimaVersion; aquí solo quedan la UI y los diálogos. */
    void comprobarActualizacion(boolean manual) {
        new Thread(() -> {
            final String tagF = CONTROL_SERVICE.ultimaVersion(RELEASES_API);
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

    /** Poner/quitar/leer alias y notas, con la persistencia inyectada (service.AnotacionesService): un solo estado
     *  compartido con cache.Anotaciones, cuyos mapas ALIASES/NOTAS se le pasan tal cual. Los diálogos se quedan
     *  aquí, en la app. */
    static final AnotacionesService ANOTACIONES = new AnotacionesService(ALIASES, NOTAS, (clave, valor) -> guardarConfig(clave, valor));

    void borrarNota(long pid, String nombre) {
        ANOTACIONES.ponerNota(pid, "");
        playersList.repaint();
        refrescarAlturasWatch();
        tableModel.fireTableDataChanged();
        ajustarColumnas();
        actualizarControlesTabla();
        status.setText(t("Nota quitada a ", "Note removed from ") + nombre + ".");
    }

    void pedirNota(long pid, String nombre) {
        String notaActual = ANOTACIONES.notaDe(pid);
        JTextArea area = new JTextArea(notaActual != null ? notaActual : "", 4, 34);
        area.setLineWrap(true); area.setWrapStyleWord(true);
        String guardarO = t("Guardar", "Save"), borrarO = t("Borrar", "Delete"), cancelarO = t("Cancelar", "Cancel");
        boolean tenia = notaActual != null;
        Object[] ops = tenia ? new Object[]{ guardarO, borrarO, cancelarO } : new Object[]{ guardarO, cancelarO };
        int r = JOptionPane.showOptionDialog(this, new Object[]{
                t("Nota sobre ", "Note about ") + nombre + t(" (solo la ves tú):", " (only you see it):"),
                new JScrollPane(area) }, t("Nota", "Note"), JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, ops, guardarO);
        if (r < 0 || ops[r].equals(cancelarO)) return;
        String nota = ops[r].equals(borrarO) ? "" : area.getText().trim().replace("\n", " ");
        ANOTACIONES.ponerNota(pid, nota);
        playersList.repaint();
        refrescarAlturasWatch();   // la sublínea de la nota nace o muere: re-medir
        tableModel.fireTableDataChanged();
        status.setText(nota.isEmpty() ? t("Nota quitada.", "Note removed.") : t("Nota guardada para ", "Note saved for ") + nombre + ".");
    }

    void pedirAlias(long pid, String original) {
        String actual = ANOTACIONES.aliasDe(pid);
        String nuevo = (String) JOptionPane.showInputDialog(this,
                t("Nombre con el que quieres ver a ", "Name you want to see for ") + original
                        + t(" en toda la app (vacío = quitar el alias):", " across the app (empty = remove alias):"),
                t("Mostrar como\u2026", "Show as\u2026"), JOptionPane.PLAIN_MESSAGE, null, null, actual != null ? actual : "");
        if (nuevo == null) return;
        nuevo = nuevo.trim();
        ANOTACIONES.ponerAlias(pid, original, nuevo);
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
    JPanel centroCards;
    /** La pestaña Directos (card "directos", Twitch): ver ui.DirectosView. */
    DirectosView directos;
    final Set<Long> vinculosExpandidos = new HashSet<>();
    final Map<Long, Character> marcaFila = new HashMap<>();   // pid -> P(rincipal) / E(xpandida) / H(ija)
    final Map<Long, Boolean> vivoFamilia = new HashMap<>();   // principal -> alguna cuenta viva
    final FiltroLista filtroLista = new FiltroLista(VIVO);   // qué fila se ve y en qué orden (sin Swing)
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
            @Override public void windowClosing(WindowEvent e) { guardarVentana(); socketVivo.cerrar(); }
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
            if (!e.getValueIsAdjusting()) { applyFilters(); if (ratings != null) ratings.sincronizarSeleccion(); if (perfil != null) perfil.sincronizarSeleccion(); }   // con Ratings o Perfil abiertos, la selección se refleja allí
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
                    boolean vivo = VIVO.jugando(p.id())
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
                    Match enCurso = vivo ? VIVO.partida(p.id()) : null;   // una sola lectura: el socket puede soltarla entre dos
                    String mapaVivo = enCurso != null && enCurso.map != null ? enCurso.map : null;
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
            if (perfil.pidAbierto() > 0 && objetivoEtiqueta != null && objetivoEtiqueta.id() == perfil.pidAbierto() && perfil.historialEnTabla() != perfil.pidAbierto() && (ultimosSujetos.size() != 1 || ultimosSujetos.get(0).id() != perfil.pidAbierto()) && fetchWorker == null)
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
        techTreeBtn = pestana("Tech tree", TechTreeView.iconoBoton());
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
                directos.refrescarForzado();
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
        todasPerfilBtn.addActionListener(e -> { if (ultimosSujetos.size() == 1) { Player p = ultimosSujetos.get(0); abrirPerfil(p.id(), nombreVisible(p.id(), p.name())); javax.swing.Timer tt = new javax.swing.Timer(900, ev -> { if (perfil.pidAbierto() == p.id()) perfil.mostrarHistorialPerfil(p.id(), nombreVisible(p.id(), p.name())); }); tt.setRepeats(false); tt.start(); } });
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
        directos = new DirectosView(TWITCH_SERVICE, twitchLive, Tareas.SWING, new DirectosView.Anfitrion() {
            @Override public List<Player> visibles() {
                List<Player> out = new ArrayList<>();
                for (int i = 0; i < playersModel.size(); i++) out.add(playersModel.get(i));
                return out;
            }
            @Override public void repintarLista() { playersList.repaint(); }
            @Override public void estado(String texto) { status.setText(texto); }
            @Override public void abrirUrl(String url) { SpoilerFreeRecs.this.abrirUrl(url); }
            @Override public boolean seleccionada() { return directosBtn != null && directosBtn.isSelected(); }
        });
        centroCards.add(directos.panel(), "directos");
        liveNow = new LiveNowView(campanas, LIVE, List.of(PAISES), todosJugadores, eloWatch, twitchLive, civ -> techTree.claveCivDeNombre(civ), Tareas.SWING, this, menus, this, new LiveNowView.Anfitrion() {
            @Override public List<String> clanesGuardados() { return SpoilerFreeRecs.this.clanesGuardados(); }
            @Override public String paisSel() { return SpoilerFreeRecs.this.paisSel(); }
            @Override public String clanBuscado() { return clanField == null ? "" : clanField.getText().trim(); }
            @Override public boolean campanaContiene(long pid) { return SpoilerFreeRecs.this.campanaContiene(pid); }
            @Override public void sincronizarSocket() { SpoilerFreeRecs.this.sincronizarSocket(); }
            @Override public boolean socketConectado() { return socketVivo.conectado(); }
            @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
            @Override public String paisDe(long pid) { return dev.tirador.aoe2radar.cache.Paises.paisDe(pid); }
            @Override public void ocultarHoverCard(boolean forzar) { SpoilerFreeRecs.this.ocultarHoverCard(forzar); }
            @Override public boolean confirmarEspectar(String quien) { return SpoilerFreeRecs.this.confirmarEspectar(quien); }
            @Override public void espectarPartida(long matchId) { SpoilerFreeRecs.this.espectarPartida(matchId); }
            @Override public void abrirUrl(String url) { SpoilerFreeRecs.this.abrirUrl(url); }
            @Override public void descargar(List<Match> partidas, boolean enviarAlJuego, Runnable alTerminar) { descargaSinCambiarVista = true; alTerminarDescarga = alTerminar; download(partidas, enviarAlJuego); }
            @Override public void estadoGlobal(String texto) { status.setText(texto); }
        });
        centroCards.add(liveNow.panel(), "ahora");
        techTree = new TechTreeView(this, TechTreeServiceDatos.SISTEMA, stats, filtroStats, listas, this, Tareas.SWING,
                new TechTreeView.Anfitrion() {
                    @Override public void precalentarPerfiles() { SpoilerFreeRecs.this.precalentarPerfiles(); }
                    @Override public void cerrar() { cerrarTechTree(); }
                },
                new TechTreeView.EnlaceCivStats() {
                    // Civ Stats se crea después (civStats es null mientras se construye el tech tree, como civStatsPanel en la 1.1)
                    @Override public boolean construida() { return civStats != null; }
                    @Override public void filtrosCambiados(boolean repintarTechTree) { if (civStats != null) civStats.filtrosCambiados(repintarTechTree); }
                    @Override public void sincronizarVentana(String ventana) { if (civStats != null) civStats.ponerVentanaSinDisparar(ventana); }
                });
        centroCards.add(techTree.panel(), "techtree");
        ToolTipManager.sharedInstance().setInitialDelay(350);
        ToolTipManager.sharedInstance().setDismissDelay(90_000);   // el tooltip aguanta mientras el ratón esté quieto
        ratings = new RatingsView(RatingsServiceSfr.SISTEMA, BUSQUEDA, SERVICIO_PERFIL, Tareas.SWING, new RatingsView.Anfitrion() {
            @Override public List<Player> seleccion() { return playersList.getSelectedValuesList(); }
            @Override public boolean seleccionado(long pid) { for (Player p : playersList.getSelectedValuesList()) if (p.id() == pid) return true; return false; }
            @Override public void deseleccionar(long pid) { for (int i = 0; i < playersModel.getSize(); i++) if (playersModel.getElementAt(i).id() == pid) playersList.removeSelectionInterval(i, i); }
            @Override public void limpiarSeleccion() { playersList.clearSelection(); }
            @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
        });
        centroCards.add(ratings.panel(), "ladder");
        civStats = new CivStatsView(stats, filtroStats, listas, this, Tareas.SWING, this, () -> { if (techTree != null) techTree.actualizarWr(); });
        centroCards.add(civStats.panel(), "civstats");
        perfil = new PerfilView(SERVICIO_PERFIL, RatingsServiceSfr.SISTEMA, BUSQUEDA, stats, VIVO, menus, this, techTree, listas, Tareas.SWING,
                new PerfilView.Anfitrion() {
                    @Override public List<Player> seleccionWatchlist() { return playersList.getSelectedValuesList(); }
                    @Override public boolean estaEnWatchlist(long pid) { return containsPlayerId(pid); }
                    @Override public String paisDe(long pid) { return dev.tirador.aoe2radar.cache.Paises.paisDe(pid); }
                    @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
                    @Override public void ficharDesdeTop(long pid, String nombre, String grupo) { SpoilerFreeRecs.this.ficharDesdeTop(new Player(pid, nombre, grupo), grupo); }
                    @Override public List<String> gruposDeJugadores() { Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); for (Player x : todosJugadores) gs.add(x.grupo()); return new ArrayList<>(gs); }
                    @Override public Set<Long> idsDeGrupo(String grupo) { Set<Long> ids = new HashSet<>(); for (Player x : todosJugadores) if (x.grupo().equalsIgnoreCase(grupo)) ids.add(x.id()); return ids; }
                    @Override public List<String> gruposGuardados() { return new ArrayList<>(gruposConfig()); }
                    @Override public String grupoGeneral() { return GRUPO_GENERAL; }
                    @Override public List<String> clanesGuardados() { return SpoilerFreeRecs.this.clanesGuardados(); }
                    @Override public boolean hayTop250() { if (liveNow == null) return false; boolean[] hay = { false }; liveNow.conTop(l -> hay[0] = !l.isEmpty()); return hay[0]; }
                    @Override public Set<Long> idsTop250() { Set<Long> s = new HashSet<>(); if (liveNow != null) liveNow.conTop(l -> { for (Object[] x : l) s.add((Long) x[0]); }); return s; }
                    @Override public boolean ultimoClicFueCtrl() { return ultimoClicCtrl; }
                    @Override public void pedirAlias(long pid, String nombreOriginal) { SpoilerFreeRecs.this.pedirAlias(pid, nombreOriginal); }
                    @Override public void pedirNota(long pid, String nombre) { SpoilerFreeRecs.this.pedirNota(pid, nombre); }
                    @Override public void borrarNota(long pid, String nombre) { SpoilerFreeRecs.this.borrarNota(pid, nombre); }
                    @Override public void mostrarVinculadas(long pid, String nombre) { SpoilerFreeRecs.this.mostrarVinculadas(pid, nombre); }
                    @Override public void nicksAnteriores(long pid, String nombre) { SpoilerFreeRecs.this.nicksAnteriores(pid, nombre); }
                    @Override public void abrirUrl(String url) { SpoilerFreeRecs.this.abrirUrl(url); }
                    @Override public void registrarDestino(long pid, String nombre) { SpoilerFreeRecs.this.registrarDestino(new Destino("perfil", pid, nombre, null)); }
                    @Override public void actualizarTextoBuscar() { SpoilerFreeRecs.this.actualizarTextoBuscar(); }
                    @Override public JToggleButton crearBotonPestana(String texto, Icon icono) { return pestana(texto, icono); }
                    @Override public void traerAlFrente() { toFront(); requestFocus(); }
                    @Override public void mostrarEstadoGlobal(String texto) { status.setText(texto); }
                    @Override public void cerrarPerfil() { mostrarDirectos(false); }
                    @Override public boolean enCursoReal(Match m) { return dev.tirador.aoe2radar.cache.Vivos.enCursoReal(m); }
                    @Override public boolean confirmarEspectar(String nombre) { return SpoilerFreeRecs.this.confirmarEspectar(nombre); }
                    @Override public void espectarPartida(long matchId) { SpoilerFreeRecs.this.espectarPartida(matchId); }
                    @Override public void cargarPartidasEnTabla(List<Match> partidas, Player sujeto) {
                        SUJETOS.clear(); SUJETOS.add(sujeto.id());
                        vistaDeSujetos = vistaActualId();
                        refrescarSujetos(List.of(sujeto), false);
                        all.clear(); all.addAll(partidas);
                        playersList.clearSelection();
                        refreshModeCombo();
                        applyFilters();
                        mostrarGuiaVacia(false);
                        mostrarDirectos(false);
                    }
                    @Override public void buscarPartidasDe(long pid, String nombre) {
                        Player p = new Player(pid, nombre, grupoDestino());
                        objetivoForzado = p; invitado = p; vistaDelInvitado = vistaActualId();
                        playersList.clearSelection(); aplicarFiltroGrupo(); mostrarDirectos(false); fetchMatches(fetchBtn);
                    }
                    @Override public void descargarSinCambiarVista(List<Match> partidas, boolean enviar, Runnable alTerminar) {
                        descargaSinCambiarVista = true; alTerminarDescarga = alTerminar; download(partidas, enviar);
                    }
                },
                ACTIVIDAD_CACHE, eloWatch, ALIASES, CANAL_DE, ELO_AYER, NOMBRES_AYER, twitchLive, dev.tirador.aoe2radar.cache.Anotaciones::notaDe,
                PERFILES_DIR, dev.tirador.aoe2radar.cache.HistorialDisco::cargarActividad, ACT_DIAS);
        centroCards.add(perfil.panel(), "perfil");
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
            techTree.ocultarDetalle();   // la ficha flotante se cierra al clicar fuera
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
        socketVivo.iniciarPing();
        javax.swing.Timer tUpd = new javax.swing.Timer(8000, e -> {   // una vez, tras arrancar
            comprobarActualizacion(false); techTree.precargar();
            cargarPaises(); instalarAutoScroll();
            new javax.swing.Timer(60_000, ev -> guardarPaises()).start();
            refrescarCampanas();
            campanasTimer = new javax.swing.Timer(15 * 60_000, ev -> refrescarCampanas()); campanasTimer.start();
            new javax.swing.Timer(1000, ev -> { if (!VIVO.nadieJugando() && playersList.isShowing()) playersList.repaint(); }).start();   // el reloj del subtexto «en partida» corre   // las listas de las campanas (tops, país, clan) se repasan cada 15 min
            Thread lh = new Thread(() -> ladderAsegurar(false), "ladder-precarga"); lh.setDaemon(true); lh.start();   // el volcado del ladder, en silencio
        });
        tUpd.setRepeats(false);
        tUpd.start();
        vigilante = new javax.swing.Timer(tickMs(), e -> {
            // Con el socket conectado, quién está en partida llega al instante: los sondeos pasan a ser una resincronización
            // cada 10 min (× el multiplicador del mando a distancia). Si el socket cae, vuelven al ritmo del tick.
            long ahora = System.currentTimeMillis();
            long resync = (long) (10 * 60_000L * ctrlMult("tick_mult"));
            boolean tocaSondear = ctrlOn("sondeo") && (!socketVivo.conectado() || ahora - ultimoResyncMs >= resync);
            if (tocaSondear) ultimoResyncMs = ahora;
            if (tocaSondear) vigilarVivos();
            if (!modoTop()) directos.vigilarTwitch();   // en ★ lo dispara el propio río al terminar (vigilarTwitch tiene su propio ritmo)
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
                        String[] variantesLb = { "rm_1v1", "3", null };   // null: sin filtro de ladder
                        int varLb = 0;
                        int maxPagRio = mapaSel != null ? 25 : 15;
                        int pagLeidas = 0;
                        for (int pag = 1; pag <= maxPagRio && encontradas.size() < 10 && !stopOperacion; pag++) {
                            publish(t("Leyendo partidas recientes del ladder\u2026 (p\u00e1g. ", "Reading recent ladder games\u2026 (page ")
                                    + pag + ", " + encontradas.size() + "/10)");
                            PaginaPartidas ms;
                            try {
                                ms = COMPANION.recientes(variantesLb[varLb], pag, 50);   // con reintento ante 429, como antes
                            } catch (Exception ex) {
                                log("al azar (r\u00edo): fallo en p\u00e1gina " + pag + " (variante " + varLb + "): " + causa(ex));
                                if (varLb < variantesLb.length - 1 && pagLeidas == 0) { varLb++; pag = 0; continue; }
                                break;
                            }
                            if (ms.brutas() == 0 && pagLeidas == 0 && varLb < variantesLb.length - 1) {
                                varLb++;
                                pag = 0;
                                continue;
                            }
                            if (ms.brutas() == 0) break;
                            pagLeidas++;
                            boolean algunaEnVentana = false;
                            for (Match m : ms.partidas()) {
                                if (m.finished == null) continue;
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
        return listaSeguidos.gruposConfig();
    }

    void registrarGrupo(String g) {
        listaSeguidos.registrarGrupo(g);
        rebuildGrupos();
    }

    void moverJugador(Player p, String grupo) {
        listaSeguidos.moverJugador(todosJugadores, p, grupo);
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
        actualizarIndicadoresVivos();
        status.setText(p.name() + t(" movido al grupo «", " moved to group “") + grupo + t("».", "”."));
    }

    void renombrarGrupo(String viejo, String nuevo) {
        listaSeguidos.renombrarGrupo(todosJugadores, viejo, nuevo);
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
    }

    void borrarGrupo(String g) {
        listaSeguidos.borrarGrupo(todosJugadores, g);
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
        if (!listaSeguidos.ficharDesdeTop(todosJugadores, p, g)) return;
        savePlayers();
        rebuildGrupos();
        aplicarFiltroGrupo();
        status.setText(p.name() + t(" añadido a «", " added to \u201C") + g + t("» de tu watchlist.", "\u201D in your watchlist."));
        ofrecerVinculadasTrasAlta(p.id(), p.name(), g);
    }

    /**
     * Ficha a varios de golpe; pregunta UNA vez si buscar sus cuentas vinculadas y lo hace en segundo plano.
     * BUG corregido (fase 2, service.ListaSeguidos): la 1.1 mutaba todosJugadores y llamaba a marcarVinculo
     * desde doInBackground (hilo de fondo) mientras el EDT recorre esa misma lista. Ahora, en el mismo punto
     * donde la 1.1 mutaba, se aplica con SwingUtilities.invokeAndWait: la mutación ocurre en el EDT y
     * doInBackground espera a que termine antes de seguir con el siguiente jugador; así, al acabar el
     * bucle, ya está todo aplicado (done() no puede adelantarse al último hallazgo). Los textos de
     * estado siguen yendo por publish/process, como antes.
     */
    void ficharVarios(List<Player> lista, String g) {
        List<Player> nuevos = listaSeguidos.ficharVarios(todosJugadores, lista, g);
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
        final int[] anadidas = { 0 };
        new SwingWorker<Void, String>() {
            @Override protected Void doInBackground() {
                hiloOperacion = Thread.currentThread();   // Detener corta la espera del freno de ESTA operación, no la de todos
                for (Player p : nuevos) {
                    if (stopOperacion) break;
                    publish(t("Vinculadas de ", "Linked accounts of ") + p.name() + "\u2026");
                    try {
                        List<Perfil.Vinculada> vinc = SERVICIO_PERFIL.vinculadas(p.id());
                        SwingUtilities.invokeAndWait(() -> {
                            try {
                                Set<Long> familia = new HashSet<>(); familia.add(p.id());
                                for (Perfil.Vinculada v : vinc) {
                                    long vid = v.pid();
                                    if (listaSeguidos.ficharSiNuevo(todosJugadores, vid, v.nombre(), g)) anadidas[0]++;
                                    familia.add(vid);
                                }
                                if (familia.size() > 1) marcarVinculo(familia);
                            } catch (Exception ex) { log("vinculadas " + p.name() + ": " + causa(ex)); }
                        });
                    } catch (Exception ex) { log("vinculadas " + p.name() + ": " + causa(ex)); }
                    dormir(PAUSA_MS / 2);
                }
                return null;
            }
            @Override protected void process(List<String> ch) { status.setText(ch.get(ch.size() - 1)); }
            @Override protected void done() {
                if (miSerial != opSerial) return;
                trabajando(false);
                savePlayers(); rebuildGrupos(); aplicarFiltroGrupo(); refrescarWatchlist();
                status.setText(anadidas[0] + t(" cuentas vinculadas añadidas a «", " linked accounts added to \u201C") + g + "\u00bb.");
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
        int n = listaSeguidos.moverVarios(todosJugadores, lista, g);
        savePlayers(); rebuildGrupos(); aplicarFiltroGrupo(); refrescarWatchlist();
        status.setText(n + t(" jugadores movidos a «", " players moved to \u201C") + g + "\u00bb.");
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
        Set<String> grupos = listaSeguidos.calcularGrupos(todosJugadores);   // un grupo ya nunca se esfuma al vaciarse
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
        boolean soloVivos = soloVivosBtn != null && soloVivosBtn.isSelected();
        boolean porElo = mostrarEloWatch && "elo".equals(leerConfig("orden_watch", "elo"));
        String ordenCfg = leerConfig("orden_watch", "elo");
        boolean porForma = formaVisible && ordenCfg.startsWith("forma");
        boolean formaAsc = "forma_asc".equals(ordenCfg);   // ascendente = los que más bajan, arriba
        final Map<Long, Forma> fa = formaActiva();
        FiltroLista.Resultado res = filtroLista.filtrar(todosJugadores, topLadder, modoTop(), g, soloVivos,
                porForma, formaAsc, porElo, fa, eloWatch, vinculosExpandidos);
        marcaFila.clear();
        marcaFila.putAll(res.marcaFila());
        vivoFamilia.clear();
        vivoFamilia.putAll(res.vivoFamilia());
        List<Player> vis = res.filas();
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
        String pais = modoPais() ? paisSel() : null;
        String firma = pais == null ? "global" : pais;
        if (TOP_LADDER_SERVICE.topFresco(forzar, firma, topFirma, !topLadder.isEmpty(), topCargado)) {
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
        new SwingWorker<TopLadderService.ResultadoTop, Void>() {
            @Override protected TopLadderService.ResultadoTop doInBackground() {
                return TOP_LADDER_SERVICE.cargarTop(pais, topN);
            }
            @Override protected void done() {
                cargandoTop = false;
                try {
                    TopLadderService.ResultadoTop res = get();
                    TOP_STREAK.putAll(res.racha());
                    TOP_LAST10.putAll(res.ultimas10());
                    gamesWatch.putAll(res.partidas());   // como en la 1.1: se aprenden siempre, aunque el usuario haya cambiado de vista
                    if (modoClan() || !modoTop()) return;   // mientras cargaba, el usuario cambió de vista: no pintar encima
                    if (res.filas().isEmpty()) {
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
        ultimoTopMs = 0; directos.reiniciarThrottle();   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
                    lastTop.clear();
                    rankTop.clear();
                    for (TopLadderService.FilaTop f : res.filas()) {
                        topLadder.add(new Player(f.pid(), f.nombre(), TOP_LADDER));
                        eloWatch.put(f.pid(), f.rating());
                        lastTop.put(f.pid(), f.ultimaPartidaMs());
                        rankTop.put(f.pid(), topLadder.size());
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
        List<TopLadderService.FilaCache> filas = new ArrayList<>();
        for (Player p : topLadder)
            filas.add(new TopLadderService.FilaCache(p.id(), p.name(), eloWatch.getOrDefault(p.id(), 0), lastTop.getOrDefault(p.id(), 0L)));
        TOP_LADDER_SERVICE.guardarCache(TOP_CACHE, firma, topCargado, filas);
    }

    /** Restaura el último top guardado si es de la misma vista. Devuelve éxito. */
    boolean cargarTopCache(String firma) {
        TopLadderService.TopCache cache = TOP_LADDER_SERVICE.cargarCache(TOP_CACHE, firma);
        if (cache == null) return false;
        topLadder.clear();
        ultimoTopMs = 0; directos.reiniciarThrottle();   // conjunto nuevo: su barrido y su cruce Twitch, en el acto
        lastTop.clear();
        rankTop.clear();
        for (TopLadderService.FilaCache f : cache.filas()) {
            topLadder.add(new Player(f.pid(), f.nombre(), TOP_LADDER));
            if (f.elo() > 0) eloWatch.put(f.pid(), f.elo());
            lastTop.put(f.pid(), f.ultimaPartidaMs());
            rankTop.put(f.pid(), topLadder.size());
        }
        topCargado = cache.cargadoMs();
        return !topLadder.isEmpty();
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
        new SwingWorker<TopLadderService.ResultadoVigilancia, Void>() {
            final List<Match> terminadasRio = new ArrayList<>();
            @Override protected TopLadderService.ResultadoVigilancia doInBackground() {
                return TOP_LADDER_SERVICE.vigilarTop(top, VIVO::jugando, VIVO::matchDe,
                        (pid, m) -> {   // el lote: alguien aparece en curso (ver DEUDA: avisarSiCampana sigue en el hilo de fondo, como en la 1.1)
                            VIVO.ponerInfo(pid, resumenVivo(m, pid));
                            if (!VIVO.jugando(pid)) avisarSiCampana(pid, m);   // nuevo en partida desde el último barrido
                            VIVO.guardarPartida(pid, m);
                        },
                        (pid, m) -> VIVO.ponerInfo(pid, resumenVivo(m, pid)));   // la confirmación individual
            }
            @Override protected void done() {
                vigilandoTop = false;
                directos.vigilarTwitch();   // el río acaba de enseñar canales: ahora sí, el cruce
                try {
                    TopLadderService.ResultadoVigilancia r = get();
                    topVerificados.clear();
                    topVerificados.addAll(r.verificados());
                    Map<Long, Long> vivos = r.resultado();
                    for (Player p : top) {
                        if (!topVerificados.contains(p.id())) continue;   // lote fallido: ni quitar ni poner
                        Long v = vivos.get(p.id());
                        if (v != null) VIVO.marcarJugando(p.id(), v);
                        else { VIVO.marcarFuera(p.id()); }   // el REST manda al quitar
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

    /** Familias: vínculos entre cuentas del mismo jugador (ver service.Familias). Campo de instancia, junto al
     *  código que lo usa (no junto a COMPANION/LIVE/SERVICIO_PERFIL: es un servicio sin red). */
    final Familias familiaSvc = new Familias();

    /** La cuenta hermana con MÁS ELO conocido que la propia, o null.
     *  Bebe de los vínculos guardados y de las familias consultadas. */
    String[] mejorAlt(long pid) {
        return familiaSvc.mejorAlt(pid, todosJugadores, SERVICIO_PERFIL.familia(pid), eloWatch);
    }


    /** Consulta las vinculadas de un seguido y casa las que YA sigues. */
    void vincularExistentes(Player p) {
        status.setText(t("Buscando cuentas vinculadas de ", "Looking up linked accounts of ") + p.name() + "…");
        new SwingWorker<List<Perfil.Vinculada>, Void>() {
            @Override protected List<Perfil.Vinculada> doInBackground() { return SERVICIO_PERFIL.vinculadas(p.id()); }
            @Override protected void done() {
                List<Perfil.Vinculada> vinc;
                try { vinc = get(); } catch (Exception e) { vinc = List.of(); }
                Set<Long> familia = familiaSvc.vincularExistentes(p.id(), vinc, SpoilerFreeRecs.this::containsPlayerId);
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
        if (VIVO.jugando(pid)) {
            Match m = VIVO.partida(pid);
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
                EstadoVivo.Rival riv = VIVO.rival(pid);
                if (riv != null) { JMenu rivalMenu = menuDeJugador(riv.pid(), riv.nombre()); rivalMenu.setText(t("Rival: ", "Opponent: ") + riv.nombre()); menu.add(rivalMenu); }
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
        if (((e1 == null && ELO_1V1.conocido(mp.id) == null) || ELO_1V1.caducado(mp.id)) && ELO_1V1.reservar(mp.id)) new Thread(() -> { long pedido = ELO_1V1.ahora(); Integer e = SERVICIO_PERFIL.elo1v1(mp.id); ELO_1V1.apuntar(mp.id, e, pedido); if (e != null && e > 0) SwingUtilities.invokeLater(() -> sub.setText(prefijo + nombre + "  1v1 " + e + (mp.civ != null && !mp.civ.isBlank() ? "  ·  " + mp.civ : ""))); }, "elo-1v1").start();
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
        if (familiaSvc.sanearVinculosHuerfanos(todosJugadores)) savePlayers();
    }

    void marcarVinculo(Set<Long> ids) {
        if (familiaSvc.marcarVinculo(todosJugadores, ids)) { savePlayers(); aplicarFiltroGrupo(); }   // el vínculo sobrevive al cierre
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

    void quitarDeWatchlist(long id) {
        listaSeguidos.quitar(todosJugadores, id);
        savePlayers();
        aplicarFiltroGrupo();
        status.setText(t("Quitado de tu watchlist.", "Removed from your watchlist."));
    }

    String grupoDeJugador(long id) {
        return listaSeguidos.grupoDeJugador(todosJugadores, id);
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
        if (directos != null && directos.tablaDirectos != null && SwingUtilities.isDescendingFrom(c, directos.tablaDirectos)) return false;
        if (ratings != null && SwingUtilities.isDescendingFrom(c, ratings.panel())) return false;   // mirar las campanas no suelta la selección (sus puntos desaparecerían)
        // los visores de las tablas (hueco bajo sus filas) tampoco: seleccionar partidas no debe cambiar el filtro de la lista
        for (Component p = c; p != null; p = p.getParent())
            if (p instanceof JScrollPane sp && sp.getViewport() != null
                    && (sp.getViewport().getView() == table || (directos != null && sp.getViewport().getView() == directos.tablaDirectos))) return false;
        return c instanceof JPanel || c instanceof JViewport || c instanceof JLabel || c instanceof JRootPane
                || c instanceof JLayeredPane || c instanceof JFrame;
    }

    boolean containsPlayerId(long id) {
        return listaSeguidos.contiene(todosJugadores, id);
    }

    /** El endpoint de Steam (api.SteamApi), aparte del companion. Campo de instancia, no static: así no importa el
     *  orden de texto frente a API_CLIENTE (static, definido más abajo) — se crea cuando ya existe la ventana, y para
     *  entonces la clase entera (con sus static) ya está inicializada. */
    final SteamApi steam = new SteamApi(API_CLIENTE);

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
                    return steam.alias(steamId);
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
    void mostrarDirectos(boolean mostrar) {
        directosBtn.setSelected(mostrar);
        if (techTreeBtn != null && techTreeBtn.isSelected()) { techTreeBtn.setSelected(false); }
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        if (perfil != null) perfil.cerrar();
        if (perfilBtn != null) perfilBtn.setSelected(false);
        if (liveNow != null) liveNow.ahoraAbierta = false; if (ahoraBtn != null) ahoraBtn.setSelected(false);
        if (splitPrincipal != null && ttDivisorPrevio >= 0) { SwingUtilities.invokeLater(() -> { if (norteWatchRef != null) { norteWatchRef.revalidate(); norteWatchRef.repaint(); } }); splitPrincipal.setDividerLocation(ttDivisorPrevio); splitPrincipal.setOneTouchExpandable(false); ttDivisorPrevio = -1; }
        if (mostrar) { taparResultados(); apagarForma(); }   // cambiar de pantalla apaga el modo consulta y la forma
        ((CardLayout) centroCards.getLayout()).show(centroCards, mostrar ? "directos" : "recs");
        if (!mostrar && all.isEmpty() && fetchWorker == null) mostrarGuiaVacia(true);   // sin partidas: la guía con su botón, no una tabla vacía
        registrarDestino(new Destino(mostrar ? "directos" : "recs", 0, null, null));
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        if (mostrar) directos.alAbrir();
    }

    /** El «río» de Top ladder usa este throttle propio (independiente del de Twitch, ver ui.DirectosPresenter). */
    long ultimoTopMs;

    /** Tras fichar a alguien: si tiene cuentas vinculadas, ofrecer añadirlas
     *  todas al mismo grupo de una vez. */
    void ofrecerVinculadasTrasAlta(long profileId, String nombre, String grupo) {
        new SwingWorker<List<Perfil.Vinculada>, Void>() {
            final Map<Long, Integer> elosV = new HashMap<>();
            @Override protected List<Perfil.Vinculada> doInBackground() {
                List<Perfil.Vinculada> vinc = SERVICIO_PERFIL.vinculadas(profileId);
                for (Perfil.Vinculada v : vinc) {
                    Integer e = SERVICIO_PERFIL.elo1v1(v.pid());
                    if (e != null) elosV.put(v.pid(), e);
                    dormir(PAUSA_MS / 2);
                }
                return vinc;
            }
            @Override protected void done() {
                List<Perfil.Vinculada> vinc;
                try { vinc = get(); } catch (Exception e) { vinc = List.of(); }
                List<Perfil.Vinculada> nuevas = new ArrayList<>();
                for (Perfil.Vinculada v : vinc) if (!containsPlayerId(v.pid())) nuevas.add(v);
                if (nuevas.isEmpty()) return;
                StringBuilder sb = new StringBuilder();
                for (Perfil.Vinculada v : nuevas) sb.append(sb.isEmpty() ? "" : ", ").append(v.nombre());
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
                for (Perfil.Vinculada v : nuevas) {
                    todosJugadores.add(new Player(v.pid(), v.nombre(), grupo));
                    familia.add(v.pid());
                }
                for (Perfil.Vinculada v : vinc) if (containsPlayerId(v.pid())) familia.add(v.pid());
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
        new SwingWorker<List<Perfil.Vinculada>, Void>() {
            final Map<Long, Integer> elosV = new HashMap<>();   // vid → ELO 1v1 actual (null: sin ELO)
            @Override protected List<Perfil.Vinculada> doInBackground() {
                List<Perfil.Vinculada> vinc = SERVICIO_PERFIL.vinculadas(profileId);
                for (Perfil.Vinculada v : vinc) {   // ELO 1v1 actual de cada cuenta, del ladder
                    elosV.put(v.pid(), SERVICIO_PERFIL.elo1v1(v.pid()));
                    dormir(PAUSA_MS / 2);
                }
                if (!containsPlayerId(profileId)) eloPropio[0] = SERVICIO_PERFIL.elo1v1(profileId);
                return vinc;
            }
            @Override protected void done() {
                List<Perfil.Vinculada> vinc;
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
                for (Perfil.Vinculada v : vinc) {   // el ELO consultado siembra la watchlist al momento
                    if (elosV.get(v.pid()) instanceof Integer e) eloWatch.put(v.pid(), e);
                }
                DefaultListModel<String> modelo = new DefaultListModel<>();
                List<Perfil.Vinculada> anadibles = new ArrayList<>();
                for (Perfil.Vinculada v : vinc) {
                    boolean ya = containsPlayerId(v.pid());
                    Integer eloV = elosV.get(v.pid());
                    String fila = v.pais().isBlank() ? String.valueOf(v.nombre()) : v.nombre() + " \u00B7 " + v.pais();
                    fila = fila + (eloV != null ? " \u00B7 " + eloV + " ELO" : t(" \u00B7 sin ELO", " \u00B7 no ELO"));
                    if (v.partidas() >= 0)
                        fila = fila + " \u00B7 " + v.partidas() + t(" partidas", " games");
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
                List<Perfil.Vinculada> elegidos = new ArrayList<>();
                if (selIdx.length == 0) elegidos.addAll(anadibles);   // sin selección: todas las nuevas
                else {
                    int i = 0;
                    for (Perfil.Vinculada v : vinc) {
                        boolean ya = containsPlayerId(v.pid());
                        for (int s : selIdx) if (s == i && !ya) elegidos.add(v);
                        i++;
                    }
                }
                for (Perfil.Vinculada v : elegidos)
                    if (!containsPlayerId(v.pid())) {
                        todosJugadores.add(new Player(v.pid(), v.nombre(), g));
                        nuevos++;
                    }
                for (Perfil.Vinculada v : vinc) if (containsPlayerId(v.pid())) familia.add(v.pid());
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
        Object[] cache = perfilCardCache.vigente(pid);
        if (cache != null) {
            pintarCard((String) cache[0], (int[]) cache[1], enPantalla, pid, fijar);
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
                        PaginaPartidas ms = COMPANION.pagina(pid, pag, PER_PAGE);
                        if (ms.brutas() == 0) break;
                        for (Match m : ms.partidas()) {
                            if (m.finished == null || m.players.size() != 2
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
                    perfilCardCache.poner(pid, new Object[]{ r[0], r[1] });
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



    /** BarridoVivos: la red y la decisión de vigilarVivos/refrescarWatchlist/«Buscar partidas» (ver
     *  service.BarridoVivos). Campo de instancia, junto al código que lo usa (como recService). */
    final BarridoVivos barridoVivos = new BarridoVivos(COMPANION, Reloj.SISTEMA, SpoilerFreeRecs::resumenVivo,
            Snapshots.ELO_AYER, SpoilerFreeRecs::dormir, PAUSA_MS, PER_PAGE);

    void refrescarWatchlist() {
        if (modoTop()) return;   // el top se alimenta del leaderboard y del río
        List<Player> objetivo = new ArrayList<>();
        for (int i = 0; i < playersModel.size(); i++) {
            Player p = playersModel.get(i);
            if (watchBarridos.add(p.id())) objetivo.add(p);
        }
        if (objetivo.isEmpty()) return;
        new SwingWorker<Void, BarridoVivos.Refresco>() {
            @Override protected Void doInBackground() {
                cargarEloAyer();
                for (Player p : objetivo) {
                    try {
                        BarridoVivos.Refresco r = barridoVivos.refrescar(p.id());
                        if (r.juegosNocturno() != null) {   // del snapshot nocturno: sin llamada (el socket dirá si está en partida)
                            gamesWatch.put(p.id(), r.juegosNocturno());
                            publish(r);
                            continue;   // como la 1.1: sin llamada de red, tampoco la pausa de cortesía entre llamadas
                        }
                        publish(r);
                    } catch (Exception ex) {
                        log("watchlist: fallo con " + p.name() + ": " + causa(ex));
                    }
                    dormir(PAUSA_MS / 2);
                }
                return null;
            }
            @Override protected void process(List<BarridoVivos.Refresco> chunks) {
                for (BarridoVivos.Refresco r : chunks) {
                    if (r.vivo() != null) {
                        if (VIVO.marcarJugando(r.pid(), r.vivo()) && r.resumen() != null) VIVO.ponerInfo(r.pid(), r.resumen());
                    } else { VIVO.marcarFuera(r.pid()); }
                    if (r.elo() != null) eloWatch.put(r.pid(), r.elo());
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
        new SwingWorker<Void, BarridoVivos.Lote>() {
            @Override protected Void doInBackground() {
                final int LOTE = 25;   // 2 llamadas para un top 50, 4 para el top 100
                for (int d = 0; d < objetivo.size(); d += LOTE) {
                    List<Player> lote = objetivo.subList(d, Math.min(d + LOTE, objetivo.size()));
                    List<Long> idsLote = new ArrayList<>();
                    for (Player p : lote) idsLote.add(p.id());
                    try {
                        publish(barridoVivos.lote(idsLote));
                    } catch (Exception ex) {
                        log("vigilante: fallo con el lote " + (d / LOTE + 1) + ": " + causa(ex));
                    }
                    dormir(PAUSA_MS / 2);
                }
                return null;
            }
            @Override protected void process(List<BarridoVivos.Lote> chunks) {
                boolean tablaTocada = false;
                for (BarridoVivos.Lote c : chunks) {
                    for (Long id : c.idsLote()) {
                        Long vm = c.vivos().get(id);
                        if (vm != null) { VIVO.marcarJugando(id, vm, c.infos().get(id)); }
                        else { VIVO.marcarFuera(id); }
                    }
                    for (Match fresco : c.terminadas())
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
            if ((grupo == null || p.grupo().equalsIgnoreCase(grupo)) && VIVO.jugando(p.id()))
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
        for (Player p : todosJugadores) if (VIVO.jugando(p.id())) nVivos++;
        String g = grupoActivo();
        int nAmbito = 0;
        for (Player p : (modoTop() ? topLadder : todosJugadores))
            if ((modoTop() || g == null || p.grupo().equalsIgnoreCase(g)) && VIVO.jugando(p.id())) nAmbito++;
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
                LiveService.Comprobacion c = LIVE.comprobar(profileId, matchId, PER_PAGE);
                if (c.partida() != null) { Match m = c.partida(); log("espectar: verificación de " + matchId + " → started=" + m.started + " finished=" + m.finished); return c.veredicto() == LiveService.Veredicto.VIVA; }
                if (c.error() != null) log("espectar: verificación no concluyente: " + causa(c.error()));
                else log("espectar: la partida " + matchId + " no aparece en las últimas del perfil " + profileId + "; se lanza igualmente");
                return null;   // sin datos claros: lanzar igualmente
            }
            @Override protected void done() {
                Boolean viva;
                try { viva = get(); } catch (Exception e) { viva = null; }
                if (Boolean.FALSE.equals(viva)) {
                    status.setText(t("Esa partida ya terminó (el companion la seguía dando por viva): el directo no existe. Dale a «Buscar partidas» para bajar la rec.",
                            "That game already ended (the companion still listed it as live): the live match is gone. Hit search to download the rec."));
                    VIVO.quitarPartida(matchId);   // el punto fantasma se va ya
                    actualizarIndicadoresVivos(); refrescarAlturasWatch(); playersList.repaint();
                    vigilarVivos();
                    return;
                }
                espectarPartida(matchId);
            }
        }.execute();
    }

    void espectar(Player p) {
        Long mid = VIVO.matchDe(p.id());
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
        if (perfil.abierto()) { abrirPerfil(pid, nombre); return; }
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
                List<String[]> out = new ArrayList<>();
                for (PerfilEncontrado p : COMPANION.buscarPerfiles(q)) {   // sin reintento, como antes
                    long id = p.pid();
                    if (id <= 0) continue;
                    String name = p.nombre();
                    String pais = p.pais();
                    aprenderPais(id, pais);
                    long games  = p.partidas();
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
                    if (perfil.abierto() && res.size() == 1) r0 = 0;   // en la pestaña Perfil, el buscador de arriba abre el perfil directamente
                    else r0 = JOptionPane.showOptionDialog(SpoilerFreeRecs.this, pnl, t("Resultados", "Results"),
                            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                            perfil.abierto() ? new Object[]{ perfO, canO } : new Object[]{ perfO, verO, addO, canO }, perfO);
                    if (perfil.abierto() && r0 != 0) { status.setText(t("Listo.", "Ready.")); return; }
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
                                Integer ei = SERVICIO_PERFIL.elo1v1(p.id());
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
        return listaSeguidos.contiene(todosJugadores, id);
    }

    // ----- Consulta de partidas ----------------------------------------------
    void fetchMatches(JButton btn) {
        if (fetchWorker != null) {          // segunda pulsación = Detener
            fetchWorker.cancel(true);
            return;
        }
        if (objetivoForzado == null && invitado == null && perfil.abierto() && perfil.pidAbierto() > 0 && playersList.getSelectedIndices().length == 0) {
            objetivoForzado = new Player(perfil.pidAbierto(), perfil.nombreAbierto(), grupoDestino());   // con un perfil abierto, «Buscar partidas» busca a ese jugador
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
            /** Seguidos sin NINGUNA página fallida en esta búsqueda (BarridoVivos.decidirVivos: solo a ellos se les
             *  puede marcar «fuera» si no salen vivos; ver DEUDA del falso «fuera»). Campo DEL WORKER, no de la
             *  ventana: get() en done() ya garantiza ver los cambios de doInBackground, y así una búsqueda vieja que
             *  siga corriendo tras un Detener no pisa el conjunto de la siguiente búsqueda. */
            final Set<Long> exitosos = new HashSet<>();
            @Override protected List<Match> doInBackground() {
                hiloOperacion = Thread.currentThread();   // Detener corta la espera del freno de ESTA operación, no la de todos
                Map<Long, Match> unicos = new LinkedHashMap<>();
                topeAlcanzado = false;
                final int MAX_PAGINAS = 6, MAX_TOTAL = 600;   // tope de seguridad por búsqueda
                for (Player pl : tracked) {
                    if (isCancelled()) break;
                    boolean fallo = false;
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
                            fallo = true;   // también con InterruptedException sin Detener real: esta página quedó sin terminar
                            if (isCancelled() || ex instanceof InterruptedException) break;   // segunda pulsación: done() ya dijo «detenida»; no pisarlo ni contar un fallo en la búsqueda siguiente
                            fallosFetch++;
                            publish(t("Aviso: fallo con ", "Heads-up: failed with ") + pl.name() + " (" + causa(ex) + ")");
                        }
                        if (!seguir) break;
                    }
                    if (!isCancelled() && !fallo) exitosos.add(pl.id());   // ninguna de sus páginas falló: se le puede marcar «fuera»
                    if (unicos.size() >= MAX_TOTAL) break;   // los que quedan sin tocar no entran en exitosos: no se les toca el estado
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
                    List<Long> idsTracked = new ArrayList<>();
                    for (Player pl : tracked) idsTracked.add(pl.id());
                    BarridoVivos.DecisionBuscar dec = barridoVivos.decidirVivos(res, idsTracked, exitosos);
                    for (Player pl : tracked) {
                        Long v = dec.vivos().get(pl.id());
                        if (v != null) { VIVO.marcarJugando(pl.id(), v, dec.infos().get(pl.id())); }
                        else if (dec.fuera().contains(pl.id())) { VIVO.marcarFuera(pl.id()); }
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

    /** RecService: descarga, disco y savegame para UNA partida (ver service.RecService). Campo de instancia (no
     *  static): se cablea junto al código que lo usa, sin tocar el bloque static de COMPANION/LIVE/SERVICIO_PERFIL. */
    final RecService recService = new DescargaRecs(Recs::descargarRec, RecsDisco::destino, Juego::copiarASavegame,
            SpoilerFreeRecs::dormir, PAUSA_MS);

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
                    RecService.Resultado r = recService.procesar(m, trackedIds, autoCopiarFinal, sgAuto, () -> stopOperacion);
                    boolean hecho = r.estado() != RecService.Estado.FALLO;
                    if (hecho) {
                        m.enDisco = true;
                        ok++;
                        if (r.enJuego()) {
                            copiadas++;
                            m.enJuego = true;
                        }
                    }
                    setEstado(m, hecho ? (r.enJuego() ? t("✓✓ en juego", "✓✓ in game") : "✓ guardada") : "✗ no disponible");
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

    void setEstado(Match m, String txt) {
        SwingUtilities.invokeLater(() -> {
            m.estado = txt;
            int idx = view.indexOf(m);
            if (idx >= 0) tableModel.fireTableRowsUpdated(idx, idx);
        });
    }

    // ----- Persistencia ------------------------------------------------------
    // Delegado a service.ListaSeguidos (cargar/guardar players.txt); la ventana conserva el disparo de
    // rebuildGrupos()/aplicarFiltroGrupo() (Swing) y muestra el error, si lo hay, en el status.
    // OJO (deuda ya existente en la 1.1, no se cambia aquí): esta E/S de disco es síncrona y loadPlayers/savePlayers
    // se llaman directamente desde el EDT (arranque, botones, menús): el disco se toca en el EDT.
    void loadPlayers() {
        String error = listaSeguidos.cargar(todosJugadores);
        if (error != null) status.setText(error);
        rebuildGrupos();       // SIEMPRE: sin esto, el combo quedaba vacío en instalaciones nuevas
        aplicarFiltroGrupo();
    }

    void savePlayers() {
        String error = listaSeguidos.guardar(todosJugadores);
        if (error != null) status.setText(error);
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
    /** Lee control.json (multiplicadores de intervalos, interruptores, mensaje) al arrancar y cada hora. La red y las
     *  reglas de aplicación viven en service.ControlService; aquí solo queda leer/guardar config y pintar en Swing. */
    static void cargarControl() {
        String msg = CONTROL_SERVICE.cargarControl(leerConfig("control_msg_visto", ""), txt -> guardarConfig("control_msg_visto", txt));
        if (msg != null) {
            SpoilerFreeRecs app = null; for (Frame f : Frame.getFrames()) if (f instanceof SpoilerFreeRecs sf) app = sf;
            final SpoilerFreeRecs appF = app;
            if (appF != null) SwingUtilities.invokeLater(() -> appF.status.setText(msg));
        }
    }
    /** Transporte con el timeout de 20 s de control.json (distinto del normal, ya en la 1.1). El de la comprobación
     *  de versión reutiliza TRANSPORTE (mismo timeout que httpText). Cableado junto al propio ControlService, no al
     *  lado de COMPANION/LIVE/SERVICIO_PERFIL. */
    static final Transporte TRANSPORTE_CONTROL = url -> {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", UA).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return new Transporte.Respuesta(r.statusCode(), r.body());
    };
    /** El «mando a distancia» y la comprobación de versión (service.ControlService). */
    static final ControlService CONTROL_SERVICE = new ControlService(TRANSPORTE_CONTROL, TRANSPORTE);
    /** Cliente único de la API: freno, 429, cancelación (api.ApiClient). Estas dos funciones quedan como fachada. */
    static final ApiClient API_CLIENTE = new ApiClient(THROTTLE, TRANSPORTE, SpoilerFreeRecs::avisarPausa429, () -> detieneEsteHilo());
    /** Endpoints del companion con su URL en un solo sitio (api.CompanionApi). Va DESPUÉS de API_CLIENTE: los static final se inicializan en orden de texto. */
    static final CompanionApi COMPANION = new CompanionApi(API_CLIENTE);
    /** Las reglas del directo que necesitan la API (ver service.LiveService). */
    static final LiveService LIVE = new LiveService(COMPANION, Reloj.SISTEMA);
    /** Los tops de la watchlist: red, decisión y disco de cargarTopLadder/cargarTopClan/vigilarTop (ver service.TopLadderService). */
    static final TopLadderService TOP_LADDER_SERVICE = new TopLadderService(COMPANION, Reloj.SISTEMA, ms -> dormir(ms), PAUSA_MS);
    /** El perfil de un jugador (ver service.ProfileService); guarda sus fichas en PERFIL_CACHE */
    static final ProfileService SERVICIO_PERFIL = new PerfilesCompanion(COMPANION, PERFIL_CACHE, (pid, c) -> aprenderCanal(pid, c), (pid, c) -> aprenderPais(pid, c),
            new AnioDesdeSfr(Snapshots.ELO, Snapshots.PERFILES, PAIS_DE, new NombresJuego() {
                @Override public String mapa(String clave) { return nombreMapaClave(clave); }
                @Override public String civ(String clave) { return nombreCivStats(clave); }
            }, Reloj.SISTEMA),
            new HistorialPerfil(COMPANION, ACTIVIDAD_CACHE, pid -> cargarActividad(pid), a -> guardarActividad(a), ms -> dormir(ms), PER_PAGE, PAUSA_MS, Reloj.SISTEMA),
            new EloSesion(VIVO, Reloj.SISTEMA, EloSesion.ESPERA));
    /** Búsqueda de perfiles por nick (service.BusquedaPerfiles), la usan los cinco buscadores de la interfaz. Va
     *  DESPUÉS de COMPANION: los static final se inicializan en orden de texto. */
    static final BusquedaPerfiles BUSQUEDA = new BusquedaPerfilesCompanion(COMPANION, NOMBRES_AYER, ELO_AYER, (pid, pais) -> aprenderPais(pid, pais));
    /** El barrido de Twitch y sus miniaturas (ver service.TwitchService); usa dormir() entre las llamadas una a una. */
    static final TwitchService TWITCH_SERVICE = new TwitchServiceCompanion(COMPANION, ms -> dormir(ms));

    static void dormir(long ms) {
        long fin = System.currentTimeMillis() + ms;
        while (true) {
            long resta = fin - System.currentTimeMillis();
            if (resta <= 0 || (stopOperacion && opEnCurso)) return;   // el freno solo corta esperas de operaciones cancelables
            try { Thread.sleep(Math.min(250, resta)); }
            catch (InterruptedException e) { return; }   // sin re-marcar: los hilos del pool se reutilizan
        }
    }

    /** Búsqueda de jugadores por nombre en el companion: {id, nombre, etiqueta legible}. Delegados de service.BusquedaPerfiles
     *  (BUSQUEDA): la lógica vive allí, aquí solo queda la fachada para no tocar los cinco buscadores de la interfaz
     *  (ni addPlayerDialog, que usa buscarLocal con su propio orden) ahora. */
    /** Sugerencias al teclear: el índice local si tiene algo (sin llamada); si no, la API. */
    static List<String[]> sugerirPerfiles(String q) { return BUSQUEDA.sugerir(q); }
    /** Búsqueda explícita (Enter): la API y el índice local, juntos y sin duplicados; primero lo local. Así un nick cambiado hace poco (que el volcado aún no conoce) también aparece. */
    static List<String[]> buscarPerfiles(String q) { return BUSQUEDA.buscar(q); }
    /** Sugerencias locales del índice nocturno de nombres (top 40.000): {pid, nombre, «nombre · país · ELO»}, hasta 8, las de más ELO primero. */
    static List<String[]> buscarLocal(String q) { return BUSQUEDA.local(q); }
}
