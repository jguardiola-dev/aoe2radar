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
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.CivFila;
import dev.tirador.aoe2radar.model.Directo;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Matchup;
import dev.tirador.aoe2radar.model.PaginaPartidas;
import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.PerfilEncontrado;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;
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
import dev.tirador.aoe2radar.service.MiPartidaServiceJuego;
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
import dev.tirador.aoe2radar.ui.AcercaDe;
import dev.tirador.aoe2radar.ui.CivStatsView;
import dev.tirador.aoe2radar.ui.DirectosView;
import dev.tirador.aoe2radar.ui.FiltroStats;
import dev.tirador.aoe2radar.ui.Listas;
import dev.tirador.aoe2radar.ui.LiveNowView;
import dev.tirador.aoe2radar.ui.MiPartidaPanel;
import dev.tirador.aoe2radar.ui.PanelScrollable;
import dev.tirador.aoe2radar.ui.PerfilView;
import dev.tirador.aoe2radar.ui.WatchlistView;
import dev.tirador.aoe2radar.ui.RatingsView;
import dev.tirador.aoe2radar.ui.SelectorRangoElo;
import dev.tirador.aoe2radar.ui.Tareas;
import dev.tirador.aoe2radar.ui.TechTreeView;
import dev.tirador.aoe2radar.ui.TemaApp;
import dev.tirador.aoe2radar.ui.WrapLayout;
import dev.tirador.aoe2radar.util.Config;
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
import static dev.tirador.aoe2radar.api.Http.nuevoHttp;
import static dev.tirador.aoe2radar.api.Http.req;
import static dev.tirador.aoe2radar.cache.Anotaciones.ALIASES;
import static dev.tirador.aoe2radar.cache.Anotaciones.NOTAS;
import static dev.tirador.aoe2radar.cache.Anotaciones.cargarAliases;
import static dev.tirador.aoe2radar.cache.Anotaciones.cargarNotas;
import static dev.tirador.aoe2radar.cache.Anotaciones.nombreVisible;
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
import static dev.tirador.aoe2radar.cache.Vivos.candidatoSocket;
import static dev.tirador.aoe2radar.cache.Vivos.enCursoReal;
import static dev.tirador.aoe2radar.service.Aleatorio.esRankedRM;
import static dev.tirador.aoe2radar.service.CalculoStats.MIN_PARTIDAS_CIV;
import static dev.tirador.aoe2radar.service.CalculoStats.POCAS_PARTIDAS;
import static dev.tirador.aoe2radar.service.CalculoStats.duracionMedia;
import static dev.tirador.aoe2radar.service.ConsultasLadder.ladderNombre;
import static dev.tirador.aoe2radar.service.ConsultasLadder.miembrosClan;
import static dev.tirador.aoe2radar.service.ConsultasLadder.percentilRango;
import static dev.tirador.aoe2radar.service.ConsultasLadder.percentilRating;
import static dev.tirador.aoe2radar.service.ConsultasLadder.sugerirClanes;
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
import static dev.tirador.aoe2radar.ui.TemaApp.TEMA_CLARO;
import static dev.tirador.aoe2radar.ui.TemaApp.TEMA_OSCURO;
import static dev.tirador.aoe2radar.ui.TemaApp.TEMA_SISTEMA;
import static dev.tirador.aoe2radar.ui.TemaApp.flatLafDisponible;

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
// Azar/Guess the ELO (T3-A3): la lógica vive en service.AzarService/AzarServiceCompanion.
import dev.tirador.aoe2radar.service.AzarService;
import dev.tirador.aoe2radar.service.AzarServiceCompanion;
import static dev.tirador.aoe2radar.service.AzarService.ajustarRefAzar;

public class SpoilerFreeRecs extends JFrame implements dev.tirador.aoe2radar.ui.Navegacion, dev.tirador.aoe2radar.ui.ComponentesTema {

    // ----- Configuración -----------------------------------------------------
    static final Path   PLAYERS_FILE = Path.of("players.txt");
    static final String FLATLAF_JAR  = "flatlaf-3.7.2.jar";
    static final String DONAR_URL    = "https://paypal.me/12Tirador/5EUR";
    static final int    PER_PAGE     = 50;   // partidas consultadas por jugador
    static final long   PAUSA_MS     = 300;  // cortesía entre llamadas

    // tarjeta, filaBarra, enlaceVerTodo y las ventanas de lista/tabla completa (ver ui.Listas); RegresionCapturas la usa por el nombre del campo.
    final Listas listas = new Listas(this, v -> ultimoClicCtrl = v);


    /** Diálogos de un jugador (nota, alias, cuentas vinculadas, nicks anteriores): ver ui.DialogosJugador. Lo que
     *  toca la tabla/lista de la ventana llega por su Anfitrion; la red de Steam, por RedSteam (ui no importa api). */
    final dev.tirador.aoe2radar.ui.DialogosJugador dialogos = new dev.tirador.aoe2radar.ui.DialogosJugador(this, ANOTACIONES, SERVICIO_PERFIL,
            new dev.tirador.aoe2radar.ui.DialogosJugador.RedSteam() {
                @Override public String steamId(long pid) throws Exception { return COMPANION.perfil(pid).steamId(); }
                @Override public List<String[]> alias(String steamId) throws Exception { return steam.alias(steamId); }
            },
            new dev.tirador.aoe2radar.ui.DialogosJugador.Anfitrion() {
                @Override public void repintarLista() { playersList.repaint(); }
                @Override public void refrescarAlturas() { watchlist.refrescarAlturasWatch(); }
                @Override public void refrescarTabla() { tableModel.fireTableDataChanged(); }
                @Override public void ajustarColumnasTabla() { ajustarColumnas(); }
                @Override public void actualizarControles() { actualizarControlesTabla(); }
                @Override public void refrescarSujetos() { SpoilerFreeRecs.this.refrescarSujetos(ultimosSujetos, invitado != null); }   // el ELO recién llegado, a la cabecera
                @Override public void mostrarEstado(String texto) { status.setText(texto); }
                @Override public boolean enWatchlist(long pid) { return watchlist.containsPlayerId(pid); }
                @Override public void ponerEloWatch(long pid, int elo) { eloWatch.put(pid, elo); }
                @Override public Set<String> gruposDisponibles() { return watchlist.gruposParaFichar(); }
                @Override public String grupoActivo() { return watchlist.grupoActivo(); }
                @Override public void agregarJugador(long pid, String nombre, String grupo) { todosJugadores.add(new Player(pid, nombre, grupo)); }
                @Override public void guardarJugadores() { watchlist.savePlayers(); }
                @Override public void reconstruirGrupos() { watchlist.rebuildGrupos(); }
                @Override public void marcarFamiliaVinculada(Set<Long> familia) { watchlist.marcarVinculo(familia); }
                @Override public void aplicarFiltro() { watchlist.aplicarFiltroGrupo(); }
                @Override public void refrescarWatchlist() { watchlist.refrescarWatchlist(); }
                @Override public void pausaCortesia() { dormir(PAUSA_MS / 2); }
            });

    /** Menús de jugador para las vistas: ver ui.MenusJugadorSwing (implementación real). Lo que necesita de la
     *  ventana y no es navegación llega por Acciones. */
    final dev.tirador.aoe2radar.ui.MenusJugador menus = new dev.tirador.aoe2radar.ui.MenusJugadorSwing(VIVO, ELO_1V1, SERVICIO_PERFIL, this,
            pid -> SpoilerFreeRecs.this.liveNow != null ? SpoilerFreeRecs.this.liveNow.liveFicha(pid) : null, dev.tirador.aoe2radar.ui.Tareas.SWING,
            new dev.tirador.aoe2radar.ui.MenusJugadorSwing.Acciones() {
                @Override public void espectarPartida(long matchId) { SpoilerFreeRecs.this.espectarPartida(matchId); }
                @Override public void abrirUrl(String url) { SpoilerFreeRecs.this.abrirUrl(url); }
                @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
                @Override public String paisDe(long pid) { return dev.tirador.aoe2radar.cache.Paises.paisDe(pid); }
                @Override public String notaDe(long pid) { return SpoilerFreeRecs.this.notaDe(pid); }
                @Override public void pedirAlias(long pid, String nombreOriginal) { SpoilerFreeRecs.this.pedirAlias(pid, nombreOriginal); }
                @Override public void pedirNota(long pid, String nombre) { SpoilerFreeRecs.this.pedirNota(pid, nombre); }
                @Override public void borrarNota(long pid, String nombre) { SpoilerFreeRecs.this.borrarNota(pid, nombre); }
                @Override public void mostrarVinculadas(long pid, String nombre) { SpoilerFreeRecs.this.mostrarVinculadas(pid, nombre); }
                @Override public void nicksAnteriores(long pid, String nombre) { SpoilerFreeRecs.this.nicksAnteriores(pid, nombre); }
                @Override public boolean enWatchlist(long pid) { return watchlist.containsPlayerId(pid); }
                @Override public Set<String> gruposDisponibles() { return watchlist.gruposParaFichar(); }
                @Override public void anadirAWatchlist(long pid, String nombre, String grupo) {
                    todosJugadores.add(new Player(pid, nombre, grupo));
                    watchlist.savePlayers();
                    watchlist.rebuildGrupos();
                    watchlist.aplicarFiltroGrupo();
                    watchlist.refrescarWatchlist();
                    status.setText(nombre + t(" añadido a «", " added to \u201C") + grupo + "\u00bb.");
                    watchlist.ofrecerVinculadasTrasAlta(pid, nombre, grupo);   // siempre que alguien entra en un grupo, se revisan sus cuentas vinculadas
                }
                @Override public String elegirGrupoDialog(String nombreSugerido) { return watchlist.elegirGrupoDialog(nombreSugerido); }
            });

    // «Al azar por ELO» / «Guess the ELO»: muestreo, filtros y caché de sesión viven en el servicio (una sola
    // instancia por ventana, con techTree.claveCivDeNombre y dormir() de la propia ventana como colaboradores).
    final AzarService azarService = new AzarServiceCompanion(COMPANION, civ -> SpoilerFreeRecs.this.techTree.claveCivDeNombre(civ),
            Snapshots::muestraAyer, ms -> dormir(ms), PER_PAGE, PAUSA_MS);

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
    final DefaultListModel<Player> playersModel = new DefaultListModel<>();
    final JList<Player> playersList = new JList<>(playersModel) {
        @Override public String getToolTipText(MouseEvent e) {   // sobre la celda Forma: su tooltip; en el resto, el de la fila
            if (watchlist.formaVisible() && watchlist.enZonaForma(e.getPoint())) {
                int i = locationToIndex(e.getPoint());
                if (i >= 0 && getCellBounds(i, i).contains(e.getPoint())) return watchlist.tipForma(playersModel.get(i).id());
            }
            return super.getToolTipText(e);
        }
    };
    final Map<Long, Integer> eloWatch  = new HashMap<>();   // ELO actual por seguido (escrito en el EDT)
    /** Quién está en partida ahora (ver service.EstadoVivo): un solo dueño para el socket, los barridos y la UI. */
    static final EstadoVivo VIVO = EstadoVivo.SISTEMA;
    /** Fuerza la carga de la clase ui.WatchlistView AQUÍ, en la inicialización estática de SpoilerFreeRecs — el
     *  mismo punto relativo donde TOP_LADDER/TOP_PAIS/TOP_CLAN/PAISES vivían en la 1.1 — para que esos static
     *  se evalúen ANTES de que main() fije IDIOMA (por eso salen siempre en español, aunque el sistema esté en
     *  inglés). Es una rareza de orden de carga ya existente en la 1.1; decisión de Opus: conservarla tal cual
     *  al sacar la Watchlist, no corregirla de paso. Ver docs/DEUDA.md. */
    static final PaisItem[] PAISES = dev.tirador.aoe2radar.ui.WatchlistView.PAISES;
    javax.swing.Timer vigilante;      // barrido periódico del «en directo» (nunca del ELO)
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
    JTable table;   // no final: se asigna en construirTablaPartidas(), no en el constructor (T3-A1)
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
    volatile SwingWorker<?, ?> fetchWorker;
    Player invitado;
    String vistaDelInvitado = "";
    final CacheMemoria<Long, Object[]> perfilCardCache = CacheService.SISTEMA.memoria(Caducidad.TARJETA);   // pid -> { htmlDatos, int[] spark }
    JPanel sujetosPanel;
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
            JLabel todos = new JLabel("<html><i>" + escapeHtml(watchlist.vistaActualId().split("\\|")[0]) + "</i> <font color='#8a8a8a'>(" + ultimosSujetos.size() + ")</font></html>");
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
                            watchlist.aplicarFiltroGrupo();
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
            String grupoYa = ultimosSujetos.size() == 1 ? watchlist.grupoDeJugador(pid) : null;
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

    /** Forma reciente (±ELO): la lógica y el estado viven en ui.WatchlistView; este campo se inyecta porque
     *  se construye después de COMPANION (static) y antes que la propia Watchlist. */
    /** Campo de instancia (no static): se inicializa después de COMPANION, que es static (SpoilerFreeRecs paso FormService). */
    final FormService formaService = new FormaCompanion(COMPANION, Snapshots.ELO, Reloj.SISTEMA);

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
    volatile int fallosFetch;   // jugadores sin respuesta en la última búsqueda
    JButton todasPerfilBtn;
    JToggleButton ladderBtn;
    /** La vista Ratings (card "ladder"): ver ui.RatingsView. */
    RatingsView ratings;

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
        taparResultados(); watchlist.apagarForma();
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
        taparResultados(); watchlist.apagarForma();
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

    // matchDeMuestra/gteDesdeMuestra/azarDesdeMuestra/azarEnsenadas: movidos a service.AzarServiceCompanion
    // (lógica pura del azar/GTE, ver T3-A3). gamesWatch se queda: lo usa FormService, no el azar.
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
        taparResultados(); watchlist.apagarForma();
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
        taparResultados(); watchlist.apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        liveNow.alAbrirDespues();
    }

    // Los seis métodos de MenusJugador (contextual, en partida, ítem de partida, ELO 1v1 conocido, submenú de
    // jugador, perfil en el navegador) se movieron a ui.MenusJugadorSwing en la tanda 3 (oleada A2, T3-A2): estos
    // son delegados de una línea con el mismo nombre para quien los llamaba (constructor, watchlist, tabla, Perfil).
    void menuContextualJugador(long pid, String nombre, MouseEvent e) { menus.menuContextual(pid, nombre, e); }
    JMenu menuEnPartida(long pid) { return menus.enPartida(pid); }
    /** ÚNICA instancia del ELO 1v1 de sesión: la usan MenusJugadorSwing (inyectada) y submenuJugadorPartida (watchlist), aquí. */
    static final EloSesion ELO_1V1 = new EloSesion(VIVO, Reloj.SISTEMA, EloSesion.ESPERA);   // pid → ELO 1v1 RM (sesión; caduca al terminar una partida)
    Integer elo1v1Conocido(long pid) { return menus.elo1v1Conocido(pid); }
    JMenuItem itemJugadorPartida(MatchPlayer p) { return menus.itemJugadorPartida(p); }

    // ----- Campanas: aviso cuando alguien de una vista marcada entra en partida -----
    // La red, la config y la deduplicacion de avisos viven en service.Campanas; aqui solo queda
    // Swing (boton, toast) y el estado compartido (campanaIds, socketExtra). Cableado junto al
    // propio Campanas, no al lado de COMPANION/LIVE/SERVICIO_PERFIL.
    final Campanas campanas = new Campanas(COMPANION, Config::leerConfig, Config::guardarConfig);
    javax.swing.Timer campanasTimer; JPanel toast; javax.swing.Timer toastTimer;

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
        taparResultados(); watchlist.apagarForma();
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
            SwingUtilities.invokeLater(() -> { if (watchlist.norteWatchRef != null) { watchlist.norteWatchRef.revalidate(); watchlist.norteWatchRef.repaint(); } });   // las filas del norte se recalculan con el ancho ya restaurado
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
        for (Player p : watchlist.topLadderSnapshot()) ids.add(p.id());
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
            for (long pid : pids) { if (VIVO.terminada(m.id)) continue; String resumen = resumenVivo(m, pid); if (!VIVO.marcarJugando(pid, m.id, resumen)) continue;   /* terminada entretanto: ni Live now ni avisos */ VIVO.guardarPartida(pid, m); if (liveNow != null) liveNow.liveEvento(pid, m, false); watchlist.avisarSiCampana(pid, m); watchlist.avisarMiPartida(pid, m); }
            SwingUtilities.invokeLater(() -> { watchlist.actualizarIndicadoresVivos(); watchlist.refrescarAlturasWatch(); playersList.repaint(); table.repaint(); });
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
        if (cambio) SwingUtilities.invokeLater(() -> { watchlist.actualizarIndicadoresVivos(); watchlist.refrescarAlturasWatch(); playersList.repaint(); table.repaint(); });
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
    JButton detenerDescBtn, continuarBtn;
    JPanel recsCards;   // «tabla» o «guia» (estado vacío que enseña el flujo)
    JButton guiaBtn;    // el «Buscar partidas» de la guía: dice lo mismo que el principal

    /** El botón principal dice lo que va a hacer. */
    Player objetivoEtiqueta;   // el jugador que nombra el botón «Buscar partidas (X)»: se busca a él, esté donde esté la vista
    void actualizarTextoBuscar() {
        if (fetchWorker != null) return;   // en marcha dice «Detener»
        String quien;
        objetivoEtiqueta = null;
        if (invitado != null) quien = nombreVisible(invitado.id(), invitado.name());
        else if (perfil.pidAbierto() > 0 && playersList.getSelectedIndices().length == 0 && (perfil.abierto() || watchlist.modoTop())) { quien = perfil.nombreAbierto(); objetivoEtiqueta = new Player(perfil.pidAbierto(), perfil.nombreAbierto(), ""); }   // el perfil abierto (o el último visto, si en el top no hay nadie seleccionado)
        else {
            int n = playersList.getSelectedIndices().length;
            if (n > 0) quien = n + t(" seleccionado" + (n > 1 ? "s" : ""), " selected");
            else if (watchlist.modoTop()) quien = t("selecciona a alguien", "select someone");
            else quien = t("todo el grupo", "whole group");
        }
        fetchBtn.setText(t("Buscar partidas", "Search games") + " (" + quien + ")");
        if (guiaBtn != null) guiaBtn.setText(fetchBtn.getText());
        watchlist.actualizarTextoForma();
    }

    void mostrarGuiaVacia(boolean guia) {
        if (recsCards != null) ((CardLayout) recsCards.getLayout()).show(recsCards, guia ? "guia" : "tabla");
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
    }
    JButton actualizarBtn;      // «Nueva versión X — Descargar», solo si existe una mayor


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

    // notaDe, pedirAlias, pedirNota, borrarNota, mostrarVinculadas y nicksAnteriores se movieron a
    // ui.DialogosJugador en la tanda 3 (oleada A2, T3-A2): delegados de una línea con el mismo nombre.
    String notaDe(long pid) { return dialogos.notaDe(pid); }
    void borrarNota(long pid, String nombre) { dialogos.borrarNota(pid, nombre); }

    void pedirNota(long pid, String nombre) { dialogos.pedirNota(pid, nombre); }

    void pedirAlias(long pid, String original) { dialogos.pedirAlias(pid, original); }
    /** Diálogo de cuentas vinculadas: selección múltiple y grupo de destino. */
    void mostrarVinculadas(long profileId, String nombre) { dialogos.mostrarVinculadas(profileId, nombre); }
    /** Historial de alias que guarda Steam para la cuenta (endpoint público
     *  de la comunidad, vía el steamId del companion). Solo bajo demanda. */
    void nicksAnteriores(long pid, String nombre) { dialogos.nicksAnteriores(pid, nombre); }
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

    static JPanel par(java.awt.Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        for (java.awt.Component c : cs) p.add(c);
        return p;
    }

    JLabel nota, firma;                       // grises regulados según el tema
    boolean actualizandoCombos = false;

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

    /** La Watchlist (panel izquierdo): ver ui.WatchlistView. */
    final WatchlistView watchlist;

    /** Lo que la Watchlist pide a Partidas (todavía dentro de esta clase en esta rama): ver
     *  ui.WatchlistView.EnlacePartidas. */
    private WatchlistView.EnlacePartidas watchlistEnlacePartidas() {
        return new WatchlistView.EnlacePartidas() {
            @Override public void fetchMatches() { SpoilerFreeRecs.this.fetchMatches(fetchBtn); }
            @Override public void mostrarDirectos(boolean mostrar) { SpoilerFreeRecs.this.mostrarDirectos(mostrar); }
            @Override public void refrescarSujetos(List<Player> tracked, boolean esInvitado) { SpoilerFreeRecs.this.refrescarSujetos(tracked, esInvitado); }
            @Override public List<Player> ultimosSujetos() { return ultimosSujetos; }
            @Override public void taparResultados() { SpoilerFreeRecs.this.taparResultados(); }
            @Override public void applyFilters() { SpoilerFreeRecs.this.applyFilters(); }
            @Override public void actualizarTextoBuscar() { SpoilerFreeRecs.this.actualizarTextoBuscar(); }
            @Override public void limpiarSujetos() { SUJETOS.clear(); }
            @Override public void sincronizarSocket() { SpoilerFreeRecs.this.sincronizarSocket(); }
            @Override public String resumenVivo(Match m, long pid) { return SpoilerFreeRecs.resumenVivo(m, pid); }
            @Override public String refNombre(Match m) { return SpoilerFreeRecs.refNombre(m); }
            @Override public void repintarTabla() { table.repaint(); }
            @Override public void fijarObjetivo(Player p, String vistaId) { objetivoForzado = p; invitado = p; vistaDelInvitado = vistaId; }
            @Override public Player invitado() { return invitado; }
            @Override public void limpiarInvitado() { invitado = null; }
            @Override public String vistaDelInvitado() { return vistaDelInvitado; }
            @Override public boolean sujetosPanelVisible() { return sujetosPanel != null && sujetosPanel.isVisible(); }
            @Override public String vistaDeSujetos() { return vistaDeSujetos; }
        };
    }

    /** Lo que la Watchlist pide al resto de la ventana (cromo, red que no es de servicio): ver
     *  ui.WatchlistView.Anfitrion. */
    private WatchlistView.Anfitrion watchlistAnfitrion() {
        return new WatchlistView.Anfitrion() {
            @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
            @Override public String paisDe(long pid) { return dev.tirador.aoe2radar.cache.Paises.paisDe(pid); }
            @Override public void aprenderPais(long pid, String pais) { dev.tirador.aoe2radar.cache.Paises.aprenderPais(pid, pais); }
            @Override public void aprenderCanal(long pid, String canal) { dev.tirador.aoe2radar.cache.Canales.aprenderCanal(pid, canal); }
            @Override public void cargarEloAyer() { Snapshots.cargarEloAyer(); }
            @Override public boolean clanesVacios() { return dev.tirador.aoe2radar.sfrdata.Ladder.clanes.isEmpty(); }
            @Override public void asegurarLadderEnFondo() { dev.tirador.aoe2radar.sfrdata.Ladder.ladderAsegurar(false); }
            @Override public List<Map.Entry<String, Integer>> sugerirClanes(String texto) { return dev.tirador.aoe2radar.service.ConsultasLadder.sugerirClanes(texto); }
            @Override public void trabajando(boolean on) { SpoilerFreeRecs.this.trabajando(on); }
            @Override public long opSerial() { return opSerial; }
            @Override public void marcarHiloOperacionActual() { hiloOperacion = Thread.currentThread(); }
            @Override public boolean detenerOperacion() { return stopOperacion; }
            @Override public void dormir(long ms) { SpoilerFreeRecs.dormir(ms); }
            @Override public void abrirUrl(String url) { SpoilerFreeRecs.this.abrirUrl(url); }
            @Override public void espectar(Player p) { SpoilerFreeRecs.this.espectar(p); }
            @Override public java.nio.file.Path rutaCaptureAge() { return dev.tirador.aoe2radar.service.Juego.rutaCaptureAge(); }
            @Override public void lanzarCaptureAge(java.nio.file.Path rec) { SpoilerFreeRecs.this.lanzarCaptureAge(rec); }
            @Override public void mostrarToast(String texto, long matchId) { SpoilerFreeRecs.this.mostrarToast(texto, matchId); }
            @Override public void agregarAccionesToast(Runnable accionPerfil, Runnable accionCaraACara) {
                if (toast == null) return;
                JButton perf = new JButton(t("Su perfil", "Their profile")); perf.setFocusable(false); perf.setMargin(new Insets(0, 6, 0, 6)); perf.addActionListener(a -> accionPerfil.run());
                JButton cara = new JButton(t("Cara a cara", "Head-to-head")); cara.setFocusable(false); cara.setMargin(new Insets(0, 6, 0, 6)); cara.addActionListener(a -> accionCaraACara.run());
                JPanel acc = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); acc.setOpaque(false); acc.add(perf); acc.add(cara);
                toast.add(acc, BorderLayout.SOUTH); toast.revalidate();
            }
            @Override public void abrirPerfilYCaraACara(long pid, String miNombre, long rivalId, String rivalNombre) {
                abrirPerfil(pid, miNombre);
                javax.swing.Timer tt = new javax.swing.Timer(1200, ev -> { if (perfil.pidAbierto() == pid) perfil.abrirCaraACaraCon(rivalId, rivalNombre); });
                tt.setRepeats(false); tt.start();
            }
            @Override public Object[] tarjetaPerfilCache(long pid) { return perfilCardCache.vigente(pid); }
            @Override public void tarjetaPerfilGuardar(long pid, Object[] valor) { perfilCardCache.poner(pid, valor); }
            @Override public boolean perfilAbierto() { return perfil.abierto(); }
            @Override public Icon iconoVista(String tipo) { return SpoilerFreeRecs.iconoVista(tipo); }
            @Override public void seleccionCambiada() { if (ratings != null) ratings.sincronizarSeleccion(); if (perfil != null) perfil.sincronizarSeleccion(); }
            @Override public boolean enCursoReal(Match m) { return dev.tirador.aoe2radar.cache.Vivos.enCursoReal(m); }
            @Override public List<dev.tirador.aoe2radar.model.PerfilEncontrado> buscarPerfilesApi(String q) throws Exception { return COMPANION.buscarPerfiles(q); }
            @Override public Perfil perfilApi(long pid) throws Exception { return COMPANION.perfil(pid); }
            @Override public dev.tirador.aoe2radar.model.PaginaPartidas paginaApi(long pid, int pagina, int porPagina) throws Exception { return COMPANION.pagina(pid, pagina, porPagina); }
            @Override public void reiniciarThrottleDirectos() { directos.reiniciarThrottle(); }
            @Override public void vigilarTwitchDirectos() { directos.vigilarTwitch(); }
            @Override public void mostrarSuperposicion(String texto, List<Object[]> fichas, int ms) { miPartida.mostrarSuperposicion(texto, fichas, ms); }
            @Override public void actualizarSocketExtra(Set<Long> ids) {
                if (liveNow != null) { for (Object[] f : liveNow.topSnapshot()) ids.add((Long) f[0]); liveNow.socketExtra.retainAll(ids); liveNow.socketExtra.addAll(ids); }
            }
            @Override public String ahoraNombre(long pid) { return liveNow.ahoraNombre(pid); }
        };
    }

    SpoilerFreeRecs(String temaInicial) {
        super(NOMBRE + " " + VERSION + t(" — tu radar del AoE2 competitivo, sin spoilers · por ", " — your competitive AoE2 radar, spoiler-free · by ") + AUTOR);
        configurarVentana();

        sujetosPanel = new JPanel();
        watchlist = new WatchlistView(this, SERVICIO_PERFIL, BUSQUEDA, TOP_LADDER_SERVICE, formaService, campanas,
                barridoVivos, ELO_1V1, menus, dialogos, this,
                Tareas.SWING, watchlistEnlacePartidas(), watchlistAnfitrion(),
                todosJugadores, playersModel, playersList, eloWatch, gamesWatch, twitchLive, ALIASES,
                status, progreso, all, sujetosPanel, PLAYERS_FILE, Config.CONFIG_FILE.resolveSibling("top_cache.txt"),
                PAUSA_MS, PER_PAGE);
        JPanel left = watchlist.panel();

        JPanel top = construirBarraSuperior(temaInicial);

        construirTablaPartidas();

        JPanel bottom = construirBarraInferior();

        JPanel center = construirCentro(top, bottom);

        montarVentana(left, center);

        arrancar();
    }

    // Cierre, ventana recordada, foco perdido e iconos: ajustes del JFrame antes
    // de construir ningun panel. Se separa del resto del constructor porque es
    // configuración de ventana, no construcción de paneles.
    private void configurarVentana() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        aplicarVentanaGuardada();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { guardarVentana(); socketVivo.cerrar(); }
        });
        addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent e) { watchlist.ocultarHoverCard(true); }
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
    }


    // La barra de arriba: ventana de horas/buscar, filtros, pestañas de vistas,
    // flechas de historial y el menú Configuración (idioma, tema, letra...). Recibe
    // temaInicial porque el menú de Tema marca la opción ya activa al abrir.
    private JPanel construirBarraSuperior(String temaInicial) {
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
                t("Mostrar ELO en la Watchlist", "Show ELO in the Watchlist"), watchlist.mostrarEloWatch);
        eloWatchItem.setToolTipText(t("El ELO se actualiza solo al abrir la app, nunca al buscar, para no chivar resultados",
                "ELO refreshes only when the app opens, never on search, so results are never given away"));
        eloWatchItem.addActionListener(e -> {
            watchlist.mostrarEloWatch = eloWatchItem.isSelected();
            guardarConfig("elo_watchlist", String.valueOf(watchlist.mostrarEloWatch));
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
                if (watchlist.modoTop()) watchlist.forzarRecargaTop();
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
        return top;
    }

    // La tabla de partidas: el JTable con sus renderers (fila en vivo en negrita,
    // el ojo de «revelar resultado»), el menú contextual por fila y los atajos de
    // teclado (Enter descarga, Ctrl+F busca, F5 refresca Directos).
    private void construirTablaPartidas() {
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
                    jl.setToolTipText(watchlist.tipCuentaVinculada(view.get(mr)));
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
                watchlist.enfocarBuscador();
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
    }

    // La franja inferior: los botones de descargar/enviar al juego, el menú de
    // carpetas, la firma y donacion, y la barra de estado (progreso, detener,
    // continuar buscando). Devuelve el panel para el centro de la ventana.
    private JPanel construirBarraInferior() {
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
        return bottom;
    }

    // El centro de la ventana: las cartas (CardLayout) de cada vista -- tabla de
    // partidas/guia, Directos, Live now, tech tree, Ratings, Civ Stats y Perfil --
    // encima de «top» y con «bottom» debajo. Aquí se crean las vistas y sus
    // anfitriones (las interfaces que les dan acceso a la ventana).
    private JPanel construirCentro(JPanel top, JPanel bottom) {
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
        liveNow = new LiveNowView(campanas, LIVE, List.of(WatchlistView.PAISES), todosJugadores, eloWatch, twitchLive, civ -> techTree.claveCivDeNombre(civ), Tareas.SWING, this, menus, this, new LiveNowView.Anfitrion() {
            @Override public List<String> clanesGuardados() { return watchlist.clanesGuardados(); }
            @Override public String paisSel() { return watchlist.paisSel(); }
            @Override public String clanBuscado() { return watchlist.clanBuscado(); }
            @Override public boolean campanaContiene(long pid) { return watchlist.campanaContiene(pid); }
            @Override public void sincronizarSocket() { SpoilerFreeRecs.this.sincronizarSocket(); }
            @Override public boolean socketConectado() { return socketVivo.conectado(); }
            @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
            @Override public String paisDe(long pid) { return dev.tirador.aoe2radar.cache.Paises.paisDe(pid); }
            @Override public void ocultarHoverCard(boolean forzar) { watchlist.ocultarHoverCard(forzar); }
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
                    @Override public boolean estaEnWatchlist(long pid) { return watchlist.containsPlayerId(pid); }
                    @Override public String paisDe(long pid) { return dev.tirador.aoe2radar.cache.Paises.paisDe(pid); }
                    @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
                    @Override public void ficharDesdeTop(long pid, String nombre, String grupo) { watchlist.ficharDesdeTop(new Player(pid, nombre, grupo), grupo); }
                    @Override public List<String> gruposDeJugadores() { Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); for (Player x : todosJugadores) gs.add(x.grupo()); return new ArrayList<>(gs); }
                    @Override public Set<Long> idsDeGrupo(String grupo) { Set<Long> ids = new HashSet<>(); for (Player x : todosJugadores) if (x.grupo().equalsIgnoreCase(grupo)) ids.add(x.id()); return ids; }
                    @Override public List<String> gruposGuardados() { return new ArrayList<>(watchlist.gruposConfig()); }
                    @Override public String grupoGeneral() { return WatchlistView.GRUPO_GENERAL; }
                    @Override public List<String> clanesGuardados() { return watchlist.clanesGuardados(); }
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
                        vistaDeSujetos = watchlist.vistaActualId();
                        refrescarSujetos(List.of(sujeto), false);
                        all.clear(); all.addAll(partidas);
                        playersList.clearSelection();
                        refreshModeCombo();
                        applyFilters();
                        mostrarGuiaVacia(false);
                        mostrarDirectos(false);
                    }
                    @Override public void buscarPartidasDe(long pid, String nombre) {
                        Player p = new Player(pid, nombre, watchlist.grupoDestino());
                        objetivoForzado = p; invitado = p; vistaDelInvitado = watchlist.vistaActualId();
                        playersList.clearSelection(); watchlist.aplicarFiltroGrupo(); mostrarDirectos(false); fetchMatches(fetchBtn);
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
        return center;
    }

    // El split principal (Watchlist | resto) y el filtro global de clics: en
    // cualquier fondo sin control, clic izquierdo = quitar la selección de la
    // Watchlist (como el Explorador de Windows).
    private void montarVentana(JPanel left, JPanel center) {
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
    }

    /** ¿Es un fondo (no un control) donde un clic debe soltar la selección de la lista? */
    boolean esFondoDeseleccionable(Component c) {
        if (c instanceof AbstractButton || c instanceof javax.swing.text.JTextComponent || c instanceof JComboBox
                || c instanceof JList || c instanceof JTable || c instanceof javax.swing.table.JTableHeader
                || c instanceof JScrollBar || c instanceof JSpinner || c instanceof JMenuBar || c instanceof JPopupMenu
                || c instanceof JSplitPane || c instanceof javax.swing.plaf.basic.BasicSplitPaneDivider || c instanceof JProgressBar)
            return false;
        if (c instanceof JLabel l && l.getMouseListeners().length > 0) return false;   // etiquetas clicables (firma, sujetos…)
        if (watchlist.cabLabel != null && SwingUtilities.isDescendingFrom(c, watchlist.cabLabel)) return false;          // ordenar no suelta
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

    // Cargas iniciales (canales, jugadores, tema/fuentes) y el arranque de los
    // temporizadores: vigilante de vivos, ping del socket, comprobar actualizacion,
    // precarga del tech tree y del ladder. Es lo último que hace el constructor.
    private void arrancar() {
        cargarCanales();
        watchlist.loadPlayers();
        watchlist.sanearVinculosHuerfanos();
        getRootPane().setDefaultButton(fetchBtn);   // acción primaria: acento y Enter
        ajustarGrises(flatLafDisponible && temaOscuroActivo);
        ajustarBotonesEspeciales(flatLafDisponible && temaOscuroActivo);
        ajustarFuentesSecundarias();
        table.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke("ENTER"), "descargarSeleccion");
        table.getActionMap().put("descargarSeleccion", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { download(selectedRows()); }
        });
        watchlist.refrescarWatchlist();
        SwingUtilities.invokeLater(() -> {
            watchlist.grupoCombo.setSelectedItem(WatchlistView.TOP_LADDER);   // la app abre en ★
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
            watchlist.refrescarCampanas();
            campanasTimer = new javax.swing.Timer(15 * 60_000, ev -> watchlist.refrescarCampanas()); campanasTimer.start();
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
            if (tocaSondear) watchlist.vigilarVivos();
            if (!watchlist.modoTop()) directos.vigilarTwitch();   // en ★ lo dispara el propio río al terminar (vigilarTwitch tiene su propio ritmo)
            if (watchlist.modoTop() && !watchlist.modoClan()) {
                // recuperación automática: si el top no pudo cargarse (o solo hay caché), reintenta
                if (watchlist.topLadderVacio() || ahora - watchlist.topCargadoMs() > 15 * 60_000L)
                    watchlist.cargarTopLadder(true);
                else if (tocaSondear) watchlist.vigilarTop();
            } else if (watchlist.modoClan() && tocaSondear) watchlist.vigilarTop();   // el clan se vigila, pero nunca se sustituye por el top del ladder
        });
        new Thread(() -> { cargarControl(); cargarEloAyer(); }, "control").start();
        if (!leerConfig("mi_pid", "").isBlank()) iniciarVigilanciaLogJuego();   // «Mi partida»: aviso temprano al encontrar partida

        new javax.swing.Timer(3_600_000, e -> new Thread(SpoilerFreeRecs::cargarControl, "control").start()).start();
        vigilante.setInitialDelay(tickMs());
        vigilante.start();
        if (Boolean.parseBoolean(leerConfig("buscar_al_abrir", "true")))
            SwingUtilities.invokeLater(() -> { if (!watchlist.modoTop() && playersModel.size() > 0) fetchMatches(fetchBtn); });
    }

    /** El endpoint de Steam (api.SteamApi), aparte del companion. Campo de instancia, no static: así no importa el
     *  orden de texto frente a API_CLIENTE (static, definido más abajo) — se crea cuando ya existe la ventana, y para
     *  entonces la clase entera (con sus static) ya está inicializada. */
    final SteamApi steam = new SteamApi(API_CLIENTE);

    /** Submenú de acciones sobre un jugador concreto (contextual de la tabla). */
    JMenu menuDeJugador(long pid, String nombre) { return menus.deJugador(pid, nombre); }
    JMenu menuPerfilNavegador(long id) { return menus.perfilNavegador(id); }

    void abrirUrl(String url) {
        try { Desktop.getDesktop().browse(URI.create(url)); }
        catch (Exception ex) { status.setText(t("No se pudo abrir el navegador: ", "Couldn't open the browser: ") + causa(ex)); }
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
        if (splitPrincipal != null && ttDivisorPrevio >= 0) { SwingUtilities.invokeLater(() -> { if (watchlist.norteWatchRef != null) { watchlist.norteWatchRef.revalidate(); watchlist.norteWatchRef.repaint(); } }); splitPrincipal.setDividerLocation(ttDivisorPrevio); splitPrincipal.setOneTouchExpandable(false); ttDivisorPrevio = -1; }
        if (mostrar) { taparResultados(); watchlist.apagarForma(); }   // cambiar de pantalla apaga el modo consulta y la forma
        ((CardLayout) centroCards.getLayout()).show(centroCards, mostrar ? "directos" : "recs");
        if (!mostrar && all.isEmpty() && fetchWorker == null) mostrarGuiaVacia(true);   // sin partidas: la guía con su botón, no una tabla vacía
        registrarDestino(new Destino(mostrar ? "directos" : "recs", 0, null, null));
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        if (mostrar) directos.alAbrir();
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
                return azarService.buscarAleatorias(lo, hi, mapaSel, civSel, hours, multAzar, cutoff, miSerial, this::publish);
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
                    vistaDeSujetos = watchlist.vistaActualId();
                    refrescarSujetos(refs, false);   // la cabecera cuenta la verdad del azar (≤4 visibles)
                    all.clear();
                    all.addAll(res);
                    playersList.clearSelection();
                    refreshModeCombo();
                    applyFilters();
                    String extra = "";
                    if (res.size() < 10 && azarService.tramoAgotado())
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

    // LbCtx/lbPagina/ultimaPaginaLadder: movidos a service.AzarServiceCompanion (detalle interno del muestreo,
    // no se usaban fuera de buscarAleatorias/buscarGte).

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
                return azarService.buscarGte(cutoff, this::publish);
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
                    watchlist.actualizarIndicadoresVivos(); watchlist.refrescarAlturasWatch(); playersList.repaint();
                    watchlist.vigilarVivos();
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

    // ----- Acerca de: movido a ui.AcercaDe (logo, showAbout, generarIco) -----
    void showAbout() { AcercaDe.showAbout(this, logo); }

    static Image cargarLogo() { return AcercaDe.cargarLogo(); }

    /** Genera logo.ico multi-tamaño desde el logo (embebido o logo.png).
     *  Lo usa crear_exe.bat: "java -cp build SpoilerFreeRecs --make-ico". */
    static boolean generarIco() { return AcercaDe.generarIco(); }

    // ----- Tema claro/oscuro: movido a ui.TemaApp (FlatLaf por reflexión) ---
    /** Aplica el tema con FlatLaf si su jar está en el classpath. Con ventana,
     *  refresca la UI en caliente. Devuelve false si FlatLaf no está. */
    static boolean aplicarTema(String tema, SpoilerFreeRecs ventana) { return TemaApp.aplicarTema(tema, ventana); }

    static String temaValido(String t) { return TemaApp.temaValido(t); }

    void ajustarFuentesSecundarias() { TemaApp.ajustarFuentesSecundarias(this); }

    void ajustarBotonesEspeciales(boolean oscuro) { TemaApp.ajustarBotonesEspeciales(this, oscuro); }

    void ajustarGrises(boolean oscuro) { TemaApp.ajustarGrises(this, oscuro); }

    // ----- ComponentesTema: los componentes que TemaApp necesita repintar ---
    // visible para ui.TemaApp (inversion de dependencias: TemaApp no conoce SpoilerFreeRecs)
    @Override public java.awt.Component raiz() { return this; }
    @Override public JPopupMenu configMenu() { return configMenu; }
    @Override public JLabel nota() { return nota; }
    @Override public JLabel firma() { return firma; }
    @Override public JLabel watchPista1() { return watchlist.watchPista1(); }
    @Override public JLabel watchPista2() { return watchlist.watchPista2(); }
    @Override public JLabel watchPista3() { return watchlist.watchPista3(); }
    @Override public TitledBorder tituloWatch() { return watchlist.tituloWatch; }
    @Override public JTable table() { return table; }
    @Override public JButton azarBtn() { return azarBtn; }
    @Override public JButton gteBtn() { return gteBtn; }
    @Override public JToggleButton resultadosBtn() { return resultadosBtn; }
    @Override public JButton cafeBtn() { return cafeBtn; }
    @Override public boolean mostrarResultados() { return mostrarResultados; }

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

    // ajustarRefAzar: movido a service.AzarService (método estático, lógica pura del azar).

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
    // ----- «Mi partida»: quién eres, aviso al encontrar partida (log del juego) y panel sobre el juego -----
    // Sale a ui.MiPartidaPanel + ui.MiPartidaPresenter + service.MiPartidaServiceJuego (fase 3, tanda 3): mismos
    // textos, mismo Timer de 2s, mismos nombres de hilo ("log-juego", "lobby-oficial", "mi-perfil").
    final MiPartidaPanel miPartida = new MiPartidaPanel(
            new MiPartidaServiceJuego(this::elo1v1Conocido, Config::leerConfig, Config::guardarConfig, Reloj.SISTEMA, Juego::carpetaLogsJuego),
            Tareas.SWING, this, this, new MiPartidaPanel.Anfitrion() {
                @Override public List<String[]> buscarPerfiles(String nick) { return SpoilerFreeRecs.buscarPerfiles(nick); }
                @Override public void mostrarEstado(String texto) { status.setText(texto); }
                @Override public void sincronizarSocket() { SpoilerFreeRecs.this.sincronizarSocket(); }
            });

    /** Delegado: ver ui.MiPartidaPanel.iniciarVigilancia. Nombre conservado para quien lo llama (constructor). */
    void iniciarVigilanciaLogJuego() { miPartida.iniciarVigilancia(); }
    /** Delegado: ver ui.MiPartidaPanel.mostrarSuperposicion. Nombre conservado para avisarMiPartida. */
    void mostrarSuperposicion(String texto, List<Object[]> fichas, int ms) { miPartida.mostrarSuperposicion(texto, fichas, ms); }
    /** Delegado: ver ui.MiPartidaPanel.abrirMiPerfil. Nombre conservado para el botón "Mi perfil". */
    void abrirMiPerfil() { miPartida.abrirMiPerfil(); }
    /** Delegado: ver ui.MiPartidaPanel.preguntarMiNick. Nombre conservado para "Cambiar de cuenta". */
    void preguntarMiNick() { miPartida.preguntarMiNick(); }
    // ----- Consulta de partidas ----------------------------------------------
    void fetchMatches(JButton btn) {
        if (fetchWorker != null) {          // segunda pulsación = Detener
            fetchWorker.cancel(true);
            return;
        }
        if (objetivoForzado == null && invitado == null && perfil.abierto() && perfil.pidAbierto() > 0 && playersList.getSelectedIndices().length == 0) {
            objetivoForzado = new Player(perfil.pidAbierto(), perfil.nombreAbierto(), watchlist.grupoDestino());   // con un perfil abierto, «Buscar partidas» busca a ese jugador
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
        } else if (invitado != null) {   // tocar una fila = el sujeto es ahora este; el invitado se despide
            sel.add(invitado);   // persiste: sigue flotando tras la búsqueda
        } else if (objetivoEtiqueta != null && playersList.getSelectedIndices().length == 0) {
            sel.add(objetivoEtiqueta);   // lo que dice el botón es lo que se busca
        } else if (watchlist.modoTop()) {
            List<Player> selTop = playersList.getSelectedValuesList();
            if (selTop.size() > 15) {
                selTop = selTop.subList(0, 15);
                status.setText(t("En ★ el máximo son 15 perfiles por tanda: busco los 15 primeros seleccionados.",
                        "In ★ the cap is 15 profiles per run: searching the first 15 selected."));
            }
            if (!selTop.isEmpty()) sel.addAll(selTop);
            else if (watchlist.soloVivosBtn != null && watchlist.soloVivosBtn.isSelected())
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
        final List<Player> tracked = (invitado == null && !watchlist.modoTop()) ? watchlist.conFamilias(sel) : sel;
        SUJETOS.clear();
        filtroSujetos.clear();
        if (rivalField != null && !rivalField.getText().isEmpty()) rivalField.setText("");   // búsqueda nueva, filtro limpio
        fallosFetch = 0;
        mostrarGuiaVacia(false);   // llegan filas: la tabla, no la guía
        taparResultados();         // tabla nueva = sin spoilers
        for (Player px : tracked) SUJETOS.add(px.id());
        vistaDeSujetos = watchlist.vistaActualId();
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
                    watchlist.actualizarIndicadoresVivos();
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

    /** BarridoVivos: la red y la decisión de vigilarVivos/refrescarWatchlist/«Buscar partidas» (ver
     *  service.BarridoVivos). Campo de instancia, junto al código que lo usa (como recService). Lo usan
     *  fetchMatches (aquí) y ui.WatchlistView (inyectado por constructor). */
    final BarridoVivos barridoVivos = new BarridoVivos(COMPANION, Reloj.SISTEMA, SpoilerFreeRecs::resumenVivo,
            Snapshots.ELO_AYER, SpoilerFreeRecs::dormir, PAUSA_MS, PER_PAGE);

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
