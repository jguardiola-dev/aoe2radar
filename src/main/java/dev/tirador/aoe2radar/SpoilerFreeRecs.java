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
                @Override public void refrescarTabla() { partidas.refrescarTabla(); }
                @Override public void ajustarColumnasTabla() { partidas.ajustarColumnas(); }
                @Override public void actualizarControles() { actualizarControlesTabla(); }
                @Override public void refrescarSujetos() { partidas.refrescarSujetos(partidas.ultimosSujetos, invitado != null); }   // el ELO recién llegado, a la cabecera
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


    // ----- Modelo ------------------------------------------------------------

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

    // Presentación de Match (enfrentamiento/refNombre/eloAntesDespues/rivalTexto/refConVeredicto/FechaCell):
    // movida a ui.PartidasTexto (fase 3, tanda 3, oleada B). SUJETOS: movido a ui.PartidasView.SUJETOS.

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


    /** La ventana de búsqueda en horas: número × unidad (recordados entre sesiones). */
    int horasVentana() {
        int n = (int) hoursSpinner.getValue();
        int u = unidadCombo.getSelectedIndex();
        return Math.min(24 * 7, n * (u == 1 ? 24 : u == 2 ? 24 * 7 : 1));   // techo: una semana; el histórico completo vive en el perfil («Todas las partidas del perfil»)
    }
    final JLabel status = new JLabel(t("Listo.", "Ready.")) {
        @Override public void setText(String texto) {   // si no cabe, el tooltip lo enseña entero
            super.setText(texto);
            setToolTipText(texto == null || texto.isBlank() ? null : texto);
        }
    };
    final JButton cafeBtn  = new JButton("\u2615 " + t("Invítame a un café", "Buy me a coffee"));
    final JProgressBar progreso = new JProgressBar();
    final Image logo = cargarLogo();
    final JCheckBoxMenuItem autoSgItem = new JCheckBoxMenuItem(t("Enviar al juego al descargar", "Send to game after download"),
            Boolean.parseBoolean(leerConfig("autosavegame", "false")));
    JPopupMenu configMenu;
    Player invitado;
    String vistaDelInvitado = "";
    final CacheMemoria<Long, Object[]> perfilCardCache = CacheService.SISTEMA.memoria(Caducidad.TARJETA);   // pid -> { htmlDatos, int[] spark }
    JPanel sujetosPanel;

    final Map<Long, String[]> twitchLive = new HashMap<>();   // pid -> { canal, título, viewers }; escribe ui.DirectosView, leen Live now/Perfil
    Player objetivoForzado;           // jugador concreto pedido con «Ver sus partidas» (una sola búsqueda)

    // pestana/iconoVista/sincronizarPestanas/recsBtn/actualizarControlesTabla: movidos a ui.Navegador (fase 3,
    // tanda 4, T4-Z1). Delegados de una línea más abajo para quien los llamaba desde otra zona de este fichero.
    static JToggleButton pestana(String texto, Icon icono) { return dev.tirador.aoe2radar.ui.Navegador.pestana(texto, icono); }

    static Icon iconoVista(String tipo) { return dev.tirador.aoe2radar.ui.Navegador.iconoVista(tipo); }

    void actualizarControlesTabla() { navegador.actualizarControlesTabla(); }

    /** Forma reciente (±ELO): la lógica y el estado viven en ui.WatchlistView; este campo se inyecta porque
     *  se construye después de COMPANION (static) y antes que la propia Watchlist. */
    /** Campo de instancia (no static): se inicializa después de COMPANION, que es static (SpoilerFreeRecs paso FormService). */
    final FormService formaService = new FormaCompanion(COMPANION, Snapshots.ELO, Reloj.SISTEMA);

    /** La vista Ratings (card "ladder"): ver ui.RatingsView. */
    RatingsView ratings;

    /** Ratings (card "ladder"): la vista y el presentador viven en ui.RatingsView/ui.RatingsPresenter; el cromo
     *  (botones, historial, CardLayout) vive ahora en ui.Navegador (fase 3, tanda 4, T4-Z1). */
    @Override public void abrirLadder() { navegador.abrirLadder(); }

    // =====================================================================================
    // CIV STATS — resúmenes de sfr-data (civstats/ventanas/vN.json.gz y tendencias.json.gz):
    // winrate y pick rate por civ, modo, mapa y tramo de ELO; matchups 1v1; tendencias por mes
    // datos y cálculos: sfrdata.CivStats y service.CalculoStats, tras el contrato de service.StatsService
    // =====================================================================================
    /** Asegura las ventanas de Civ Stats y hace sus cálculos (ver service.StatsService). */
    final StatsService stats = new StatsServiceSfr(SfrDataClient.SISTEMA);
    /** Filtros de Civ Stats (y de la banda del Tech tree, que los comparte): ver ui.FiltroStats. */
    final FiltroStats filtroStats = new FiltroStats(leerConfig("stats_modo", "rm_1v1"), leerConfig("stats_ventana", "30"), leerConfig("stats_mapa", "*"), leerConfig("stats_tramo", "*"));
    /** La pestaña Civ Stats (winrate, pick rate, tendencias, matchups): ver ui.CivStatsView. */
    CivStatsView civStats;

    @Override public void abrirCivStats() { navegador.abrirCivStats(); }

    // =====================================================================================
    // PERFIL — la página de un jugador: ver ui.PerfilView/ui.PerfilPresenter y, para el diálogo de cara a
    // cara, ui.CaraACaraDialogo/ui.CaraACaraPresenter. El cromo (botón, CardLayout, historial) vive en
    // ui.Navegador (fase 3, tanda 4, T4-Z1).
    // =====================================================================================
    /** La pestaña Perfil (cabecera, actividad, calendario, cara a cara): ver ui.PerfilView. */
    PerfilView perfil;

    /** Delegado fino en ui.PerfilView/PerfilPresenter (calienta en segundo plano los perfiles ya guardados en disco).
     *  Sigue haciendo falta como método de la ventana porque TechTreePresenter.precargar() lo pide por el
     *  Anfitrion de TechTreeView; lo lanza el temporizador de arranque (8 s), no la apertura del tech tree. */
    void precalentarPerfiles() { perfil.precalentar(); }

    /** El botón «Perfil»: el jugador seleccionado en la watchlist o, si no hay, la página con el buscador. No es
     *  navegación (no decide CÓMO se abre, decide A QUIÉN): se queda en la ventana y ui.Navegador lo invoca como
     *  colaborador (Runnable) cuando se pulsa la pestaña directamente, ver conectarVistas/construirFilaVistas. */
    void perfilDesdeBoton() {
        List<Player> sel = playersList.getSelectedValuesList();
        if (!sel.isEmpty()) abrirPerfil(sel.get(0).id(), nombreVisible(sel.get(0).id(), sel.get(0).name()));
        else if (partidas.ultimosSujetos.size() == 1 && partidas.recsCards != null && partidas.recsCards.isShowing()) abrirPerfil(partidas.ultimosSujetos.get(0).id(), nombreVisible(partidas.ultimosSujetos.get(0).id(), partidas.ultimosSujetos.get(0).name()));   // «Partidas de: X» → su perfil
        else if (perfil.pidAbierto() > 0 && ACTIVIDAD_CACHE.containsKey(perfil.pidAbierto())) abrirPerfil(perfil.pidAbierto(), perfil.nombreAbierto());
        else abrirPerfil(0, "");
    }

    // matchDeMuestra/gteDesdeMuestra/azarDesdeMuestra/azarEnsenadas: movidos a service.AzarServiceCompanion
    // (lógica pura del azar/GTE, ver T3-A3). gamesWatch se queda: lo usa FormService, no el azar.
    final Map<Long, Integer> gamesWatch = new java.util.concurrent.ConcurrentHashMap<>();

    /** Abre el perfil de un jugador; pid 0 = página vacía con el buscador. El cromo vive en ui.Navegador. */
    @Override public void abrirPerfil(long pid, String nombre) { navegador.abrirPerfil(pid, nombre); }

    /** Abre a un jugador en una pestaña NUEVA (Ctrl+clic, «+»); la cuenta de pestañas es de ui.PerfilView. */
    @Override public void abrirPerfilEnPestana(long pid, String nombre) { navegador.abrirPerfilEnPestana(pid, nombre); }

    // =====================================================================================
    // LIVE NOW — partidas en curso de los 250 mejores del ladder 1v1 (tarjetas por partida) y las
    // terminadas en las últimas 2 horas (sin resultado, con la rec a un clic). La vista y el presentador
    // viven en ui.LiveNowView/ui.LiveNowPresenter; el cromo (botón de pestaña, CardLayout, historial) vive en
    // ui.Navegador. Aquí se queda lo que comparte con la watchlist (los seis métodos de MenusJugador, ELO_1V1,
    // el toast y la campanita: ver más abajo).
    // =====================================================================================
    /** La pestaña Live now: ver ui.LiveNowView. */
    LiveNowView liveNow;

    @Override public void abrirAhora() { navegador.abrirAhora(); }

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

    // Destino/historial/historialPos/navegandoAtras/atrasBtn/adelanteBtn/actualizarBotonesHistorial/irA: movidos
    // a ui.AppState + ui.Navegador (fase 3, tanda 4, T4-Z1: estado observable de la app, ver docs/ARQUITECTURA.md).
    // Destino sigue existiendo aquí como un compat de una línea: PerfilView.Anfitrion (construida en
    // construirCentro, fuera de mi zona) construye "new Destino(...)" y llama a este registrarDestino tal cual.
    record Destino(String vista, long pid, String nombre, String civ) { }

    void registrarDestino(Destino d) { navegador.estado.registrarDestino(new dev.tirador.aoe2radar.ui.AppState.Destino(d.vista(), d.pid(), d.nombre(), d.civ())); }

    /** Delegado: instalarAutoScroll (botones laterales del ratón) lo llama por su nombre. */
    void volverAtras() { navegador.volverAtras(); }
    /** Delegado: instalarAutoScroll lo llama por su nombre. */
    void irAdelante() { navegador.irAdelante(); }

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
    // TECH TREE — la vista (árbol, ficha, banda de winrate) vive en ui.TechTreeView; el cromo compartido con
    // el resto de pestañas (botón, CardLayout, historial y el pliegue de la watchlist) vive en ui.Navegador
    // (fase 3, tanda 4, T4-Z1): splitPrincipal se crea en montarVentana (Z5) y se le pasa por conectarVistas.
    // =====================================================================================
    TechTreeView techTree;

    /** Abre el panel (plegando la watchlist) y, si se pide, en una civ concreta. El cromo vive en ui.Navegador. */
    @Override public void abrirTechTree(String civ) { navegador.abrirTechTree(civ); }

    /** Delegado: TechTreeView.Anfitrion.cerrar() (construida en construirCentro) lo llama por su nombre. */
    void cerrarTechTree() { navegador.cerrarTechTree(); }

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
            SwingUtilities.invokeLater(() -> { watchlist.actualizarIndicadoresVivos(); watchlist.refrescarAlturasWatch(); playersList.repaint(); partidas.table.repaint(); });
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
        if (cambio) SwingUtilities.invokeLater(() -> { watchlist.actualizarIndicadoresVivos(); watchlist.refrescarAlturasWatch(); playersList.repaint(); partidas.table.repaint(); });
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

    JLabel firma;                       // grises regulados según el tema

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

    /** Lo que la Watchlist pide a Partidas (ui.PartidasView, campo {@code partidas}): ver
     *  ui.WatchlistView.EnlacePartidas. objetivoForzado/invitado/vistaDelInvitado siguen en la ventana. */
    private WatchlistView.EnlacePartidas watchlistEnlacePartidas() {
        return new WatchlistView.EnlacePartidas() {
            @Override public void fetchMatches() { partidas.fetchMatches(partidas.fetchBtn); }
            @Override public void mostrarDirectos(boolean mostrar) { SpoilerFreeRecs.this.mostrarDirectos(mostrar); }
            @Override public void refrescarSujetos(List<Player> tracked, boolean esInvitado) { partidas.refrescarSujetos(tracked, esInvitado); }
            @Override public List<Player> ultimosSujetos() { return partidas.ultimosSujetos; }
            @Override public void taparResultados() { partidas.taparResultados(); }
            @Override public void applyFilters() { partidas.applyFilters(); }
            @Override public void actualizarTextoBuscar() { partidas.actualizarTextoBuscar(); }
            @Override public void limpiarSujetos() { dev.tirador.aoe2radar.ui.PartidasView.SUJETOS.clear(); }
            @Override public void sincronizarSocket() { SpoilerFreeRecs.this.sincronizarSocket(); }
            @Override public String resumenVivo(Match m, long pid) { return SpoilerFreeRecs.resumenVivo(m, pid); }
            @Override public String refNombre(Match m) { return partidas.refNombre(m); }
            @Override public void repintarTabla() { partidas.table.repaint(); }
            @Override public void fijarObjetivo(Player p, String vistaId) { objetivoForzado = p;   // aunque ya esté en un grupo (entonces no es invitado, pero sí el objetivo)
                invitado = p; vistaDelInvitado = vistaId; }
            @Override public Player invitado() { return invitado; }
            @Override public void limpiarInvitado() { invitado = null; }
            @Override public String vistaDelInvitado() { return vistaDelInvitado; }
            @Override public boolean sujetosPanelVisible() { return sujetosPanel != null && sujetosPanel.isVisible(); }
            @Override public String vistaDeSujetos() { return partidas.vistaDeSujetos; }
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

    /** El cromo de navegación (pestañas, historial, esqueleto de los abrir*): ver ui.Navegador/ui.AppState.
     *  La ventana implementa ui.Navegacion delegando en él (fase 3, tanda 4, T4-Z1). Se crea al principio del
     *  constructor porque construirBarraSuperior necesita sus botones; conectarVistas lo completa con las
     *  vistas y el split, que no existen hasta construirCentro/montarVentana (mismo truco de referencia
     *  adelantada que ya usa el resto de la ventana). */
    final dev.tirador.aoe2radar.ui.Navegador navegador;

    SpoilerFreeRecs(String temaInicial) {
        super(NOMBRE + " " + VERSION + t(" — tu radar del AoE2 competitivo, sin spoilers · por ", " — your competitive AoE2 radar, spoiler-free · by ") + AUTOR);
        configurarVentana();
        navegador = new dev.tirador.aoe2radar.ui.Navegador(status, this::perfilDesdeBoton);

        sujetosPanel = new JPanel();
        watchlist = new WatchlistView(this, SERVICIO_PERFIL, BUSQUEDA, TOP_LADDER_SERVICE, formaService, campanas,
                barridoVivos, ELO_1V1, menus, dialogos, this,
                Tareas.SWING, watchlistEnlacePartidas(), watchlistAnfitrion(),
                todosJugadores, playersModel, playersList, eloWatch, gamesWatch, twitchLive, ALIASES,
                status, progreso, partidas.all, sujetosPanel, PLAYERS_FILE, Config.CONFIG_FILE.resolveSibling("top_cache.txt"),
                PAUSA_MS, PER_PAGE);
        JPanel left = watchlist.panel();

        JPanel top = construirBarraSuperior(temaInicial);

        partidas.construirTabla();

        JPanel bottom = construirBarraInferior();

        JPanel center = construirCentro(top, bottom);

        montarVentana(left, center);
        navegador.conectarVistas(partidas, watchlist, perfil, liveNow, techTree, ratings, civStats, directos, centroCards, splitPrincipal);

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
        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));   // fija: nada salta de sitio
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
        partidas.agregarFilaConsulta(fila1);   // modo, rival (sugerencias), mapa, periodo, Buscar partidas, Al azar por ELO, Guess the ELO: ver ui.PartidasView
        // Pestañas de vistas y flechas de atrás/adelante: ver ui.Navegador.construirFilaVistas (fase 3, tanda 4, T4-Z1).
        JPanel filaVistas = navegador.construirFilaVistas();

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
            if (autoSgItem.isSelected() && partidas.obtenerSavegame(true) == null) {
                autoSgItem.setSelected(false);
                return;
            }
            guardarConfig("autosavegame", String.valueOf(autoSgItem.isSelected()));
        });

        JMenuItem carpetaItem = new JMenuItem(t("Cambiar carpeta savegame…", "Change savegame folder…"));
        carpetaItem.addActionListener(e -> {
            Path p = partidas.elegirSavegameManual();
            if (p != null) { status.setText(t("Carpeta savegame: ", "Savegame folder: ") + p); partidas.applyFilters(); }
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

        JPanel fila2 = partidas.construirFilaNota();   // nota sin-spoilers + «Mostrar resultados»: ver ui.PartidasView

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

    // La tabla de partidas: construirTablaPartidas() movida a ui.PartidasView.construirTabla().
    // La franja inferior: los botones de descargar/enviar al juego, el menú de
    // carpetas, la firma y donacion, y la barra de estado (progreso, detener,
    // continuar buscando). Devuelve el panel para el centro de la ventana.
    private JPanel construirBarraInferior() {
        JPanel filasBtns = partidas.construirBotonesInferiores();   // dlSel/dlAll/carpetas/enviarSg/todasPerfilBtn: ver ui.PartidasView
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
        continuarBtn.addActionListener(e -> partidas.buscarAleatorias(true));
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
        partidas.construirCards();   // recsCards «tabla»/«guia», guiaBtn: ver ui.PartidasView
        centroCards.add(partidas.panel(), "recs");
        directos = new DirectosView(TWITCH_SERVICE, twitchLive, Tareas.SWING, new DirectosView.Anfitrion() {
            @Override public List<Player> visibles() {
                List<Player> out = new ArrayList<>();
                for (int i = 0; i < playersModel.size(); i++) out.add(playersModel.get(i));
                return out;
            }
            @Override public void repintarLista() { playersList.repaint(); }
            @Override public void estado(String texto) { status.setText(texto); }
            @Override public void abrirUrl(String url) { SpoilerFreeRecs.this.abrirUrl(url); }
            @Override public boolean seleccionada() { return navegador.directosBtn != null && navegador.directosBtn.isSelected(); }
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
            @Override public void descargar(List<Match> lista, boolean enviarAlJuego, Runnable alTerminar) { partidas.descargarSinCambiarVista(lista, enviarAlJuego, alTerminar); }
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
                    @Override public void actualizarTextoBuscar() { partidas.actualizarTextoBuscar(); }
                    @Override public JToggleButton crearBotonPestana(String texto, Icon icono) { return pestana(texto, icono); }
                    @Override public void traerAlFrente() { toFront(); requestFocus(); }
                    @Override public void mostrarEstadoGlobal(String texto) { status.setText(texto); }
                    @Override public void cerrarPerfil() { mostrarDirectos(false); }
                    @Override public boolean enCursoReal(Match m) { return dev.tirador.aoe2radar.cache.Vivos.enCursoReal(m); }
                    @Override public boolean confirmarEspectar(String nombre) { return SpoilerFreeRecs.this.confirmarEspectar(nombre); }
                    @Override public void espectarPartida(long matchId) { SpoilerFreeRecs.this.espectarPartida(matchId); }
                    @Override public void cargarPartidasEnTabla(List<Match> lista, Player sujeto) {
                        partidas.cargarPartidasEnTabla(lista, sujeto, watchlist.vistaActualId());
                        mostrarDirectos(false);
                    }
                    @Override public void buscarPartidasDe(long pid, String nombre) {
                        Player p = new Player(pid, nombre, watchlist.grupoDestino());
                        objetivoForzado = p; invitado = p; vistaDelInvitado = watchlist.vistaActualId();
                        playersList.clearSelection(); watchlist.aplicarFiltroGrupo(); mostrarDirectos(false); partidas.fetchMatches(partidas.fetchBtn);
                    }
                    @Override public void descargarSinCambiarVista(List<Match> lista, boolean enviar, Runnable alTerminar) {
                        partidas.descargarSinCambiarVista(lista, enviar, alTerminar);
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
            if (!playersList.isSelectionEmpty()) { playersList.clearSelection(); partidas.actualizarTextoBuscar(); }
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
        if (partidas.table != null && SwingUtilities.isDescendingFrom(c, partidas.table)) return false;
        if (directos != null && directos.tablaDirectos != null && SwingUtilities.isDescendingFrom(c, directos.tablaDirectos)) return false;
        if (ratings != null && SwingUtilities.isDescendingFrom(c, ratings.panel())) return false;   // mirar las campanas no suelta la selección (sus puntos desaparecerían)
        // los visores de las tablas (hueco bajo sus filas) tampoco: seleccionar partidas no debe cambiar el filtro de la lista
        for (Component p = c; p != null; p = p.getParent())
            if (p instanceof JScrollPane sp && sp.getViewport() != null
                    && (sp.getViewport().getView() == partidas.table || (directos != null && sp.getViewport().getView() == directos.tablaDirectos))) return false;
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
        getRootPane().setDefaultButton(partidas.fetchBtn);   // acción primaria: acento y Enter
        ajustarGrises(flatLafDisponible && temaOscuroActivo);
        ajustarBotonesEspeciales(flatLafDisponible && temaOscuroActivo);
        ajustarFuentesSecundarias();
        partidas.table.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke("ENTER"), "descargarSeleccion");
        partidas.table.getActionMap().put("descargarSeleccion", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { partidas.download(partidas.selectedRows()); }
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
            SwingUtilities.invokeLater(() -> { if (!watchlist.modoTop() && playersModel.size() > 0) partidas.fetchMatches(partidas.fetchBtn); });
    }

    /** El endpoint de Steam (api.SteamApi), aparte del companion. Campo de instancia, no static: así no importa el
     *  orden de texto frente a API_CLIENTE (static, definido más abajo) — se crea cuando ya existe la ventana, y para
     *  entonces la clase entera (con sus static) ya está inicializada. */
    final SteamApi steam = new SteamApi(API_CLIENTE);

    void abrirUrl(String url) {
        try { Desktop.getDesktop().browse(URI.create(url)); }
        catch (Exception ex) { status.setText(t("No se pudo abrir el navegador: ", "Couldn't open the browser: ") + causa(ex)); }
    }

    /** La zona central alterna entre la tabla de recs y los directos. El cromo vive en ui.Navegador; se queda
     *  este delegado porque medio fichero llama a «mostrarDirectos» por su nombre (Watchlist, Partidas, Perfil…). */
    void mostrarDirectos(boolean mostrar) { navegador.mostrarDirectos(mostrar); }


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
    @Override public JLabel nota() { return partidas == null ? null : partidas.nota; }
    @Override public JLabel firma() { return firma; }
    @Override public JLabel watchPista1() { return watchlist.watchPista1(); }
    @Override public JLabel watchPista2() { return watchlist.watchPista2(); }
    @Override public JLabel watchPista3() { return watchlist.watchPista3(); }
    @Override public TitledBorder tituloWatch() { return watchlist.tituloWatch; }
    @Override public JTable table() { return partidas == null ? null : partidas.table; }
    @Override public JButton azarBtn() { return partidas == null ? null : partidas.azarBtn; }
    @Override public JButton gteBtn() { return partidas == null ? null : partidas.gteBtn; }
    @Override public JToggleButton resultadosBtn() { return partidas == null ? null : partidas.resultadosBtn; }
    @Override public JButton cafeBtn() { return cafeBtn; }
    @Override public boolean mostrarResultados() { return partidas != null && partidas.mostrarResultados; }

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
            partidas.fetchBtn.setEnabled(true); partidas.azarBtn.setEnabled(true); partidas.gteBtn.setEnabled(true);
            if (partidas.dlSel != null) partidas.dlSel.setEnabled(true);
            if (partidas.dlAll != null) partidas.dlAll.setEnabled(true);
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

    // ajustarRefAzar: movido a service.AzarService (método estático, lógica pura del azar).

    /** Fija el jugador seguido de referencia de la partida: primero uno de los
     *  seleccionados en la lista, si no cualquiera de los seguidos. */

    /** Ancho óptimo de una columna: el mayor entre su cabecera y sus celdas visibles (con topes). */

    /** Todas las columnas al contenido (tras cada tabla nueva): Rival absorbe lo que sobre. */


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
    /** Delegado: ver ui.MiPartidaPanel.abrirMiPerfil. Nombre conservado para el botón "Mi perfil". */
    void abrirMiPerfil() { miPartida.abrirMiPerfil(); }
    /** Delegado: ver ui.MiPartidaPanel.preguntarMiNick. Nombre conservado para "Cambiar de cuenta". */
    void preguntarMiNick() { miPartida.preguntarMiNick(); }
    // ----- Consulta de partidas ----------------------------------------------

    // ----- Descarga de recs --------------------------------------------------


    /** BarridoVivos: la red y la decisión de vigilarVivos/refrescarWatchlist/«Buscar partidas» (ver
     *  service.BarridoVivos). Campo de instancia, junto al código que lo usa (como recService). Lo usan
     *  ui.PartidasView (fetchMatches) y ui.WatchlistView (los dos, inyectado por constructor). */
    final BarridoVivos barridoVivos = new BarridoVivos(COMPANION, Reloj.SISTEMA, SpoilerFreeRecs::resumenVivo,
            Snapshots.ELO_AYER, SpoilerFreeRecs::dormir, PAUSA_MS, PER_PAGE);

    /** RecService: descarga, disco y savegame para UNA partida (ver service.RecService). Campo de instancia (no
     *  static): se cablea junto al código que lo usa, sin tocar el bloque static de COMPANION/LIVE/SERVICIO_PERFIL. */
    final RecService recService = new DescargaRecs(Recs::descargarRec, RecsDisco::destino, Juego::copiarASavegame,
            SpoilerFreeRecs::dormir, PAUSA_MS);

    /** La pestaña «Partidas»: ver ui.PartidasView/ui.PartidasPresenter (fase 3, tanda 3, oleada B). Se construye
     *  aquí (como field initializer: se ejecuta antes que configurarVentana y que crear la Watchlist, igual que
     *  dialogos/menus/azarService/recService/barridoVivos) para que sus botones existan cuando
     *  construirBarraSuperior los necesite. La Watchlist (ui.WatchlistView, campo {@code watchlist}) llega por
     *  EnlaceWatchlist: sus lambdas solo se evalúan después del constructor, cuando {@code watchlist} ya existe;
     *  playersList/playersModel/eloWatch/todosJugadores/sujetosPanel/objetivoForzado/invitado siguen aquí.
     *  El resto de la ventana, por Anfitrion. */
    final dev.tirador.aoe2radar.ui.PartidasView partidas = new dev.tirador.aoe2radar.ui.PartidasView(this, menus, dialogos, this,
            azarService, recService, barridoVivos, PER_PAGE, PAUSA_MS,
            new dev.tirador.aoe2radar.ui.PartidasView.EnlaceWatchlist() {
                @Override public List<Player> seleccion() { return playersList.getSelectedValuesList(); }
                @Override public int seleccionSize() { return playersList.getSelectedIndices().length; }
                @Override public boolean soloVivosMarcado() { return watchlist.soloVivosBtn != null && watchlist.soloVivosBtn.isSelected(); }
                @Override public boolean modoTop() { return watchlist.modoTop(); }
                @Override public String grupoDestino() { return watchlist.grupoDestino(); }
                @Override public List<Player> conFamilias(List<Player> base) { return watchlist.conFamilias(base); }
                @Override public void limpiarSeleccion() { playersList.clearSelection(); }
                @Override public int totalJugadores() { return playersModel.size(); }
                @Override public Player jugador(int indice) { return playersModel.get(indice); }
                @Override public Integer eloDe(long pid) { return eloWatch.get(pid); }
                @Override public String grupoDeJugador(long pid) { return watchlist.grupoDeJugador(pid); }
                @Override public List<Player> todosJugadores() { return todosJugadores; }
                @Override public void actualizarIndicadoresVivos() { watchlist.actualizarIndicadoresVivos(); }
                @Override public void aplicarFiltroGrupo() { watchlist.aplicarFiltroGrupo(); }
                @Override public String tipCuentaVinculada(Match m) { return watchlist.tipCuentaVinculada(m); }
                @Override public Player objetivoForzado() { return objetivoForzado; }
                @Override public void fijarObjetivoForzado(Player p) { objetivoForzado = p; }
                @Override public void limpiarObjetivoForzado() { objetivoForzado = null; }
                @Override public Player invitado() { return invitado; }
                @Override public void limpiarInvitado() { invitado = null; }
                @Override public String vistaActualId() { return watchlist.vistaActualId(); }
                @Override public int horasVentana() { return SpoilerFreeRecs.this.horasVentana(); }
                @Override public void guardarVentanaHoras() {
                    guardarConfig("ventana_n", String.valueOf((int) hoursSpinner.getValue()));
                    guardarConfig("horas", String.valueOf(SpoilerFreeRecs.this.horasVentana()));
                }
                @Override public void actualizarTextoForma() { watchlist.actualizarTextoForma(); }
                @Override public JPanel sujetosPanel() { return sujetosPanel; }
            },
            new dev.tirador.aoe2radar.ui.PartidasView.Anfitrion() {
                @Override public void estado(String texto) { status.setText(texto); }
                @Override public void mostrarDirectos(boolean mostrar) { SpoilerFreeRecs.this.mostrarDirectos(mostrar); }
                @Override public void refrescarDirectos() {
                    directos.refrescarForzado();
                    if (navegador.directosBtn != null && navegador.directosBtn.isSelected()) status.setText(t("Refrescando directos…", "Refreshing streams…"));
                }
                @Override public void enfocarBuscador() { watchlist.enfocarBuscador(); }   // misma guarda de null, ahora dentro de WatchlistView
                @Override public boolean confirmarEspectar(String nombre) { return SpoilerFreeRecs.this.confirmarEspectar(nombre); }
                @Override public void espectarVerificando(long profileId, long matchId) { SpoilerFreeRecs.this.espectarVerificando(profileId, matchId); }
                @Override public void espectarPartida(long matchId) { SpoilerFreeRecs.this.espectarPartida(matchId); }
                @Override public void lanzarCaptureAge(Path rec) { SpoilerFreeRecs.this.lanzarCaptureAge(rec); }
                @Override public void abrirUrl(String url) { SpoilerFreeRecs.this.abrirUrl(url); }
                @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
                @Override public String paisDe(long pid) { return dev.tirador.aoe2radar.cache.Paises.paisDe(pid); }
                @Override public boolean enCursoReal(Match m) { return dev.tirador.aoe2radar.cache.Vivos.enCursoReal(m); }
                @Override public Path destino(Match m) { return RecsDisco.destino(m); }
                @Override public Path recsDir() { return RECS_DIR; }
                @Override public void trabajando(boolean on) { SpoilerFreeRecs.this.trabajando(on); }
                @Override public long operacionActual() { return opSerial; }
                @Override public boolean detenido() { return stopOperacion; }
                @Override public void pararOperacion() { stopOperacion = true; }
                @Override public void anotarHiloOperacion() { hiloOperacion = Thread.currentThread(); }
                @Override public void aprenderCatalogos(List<Match> res) { SpoilerFreeRecs.aprenderCatalogos(res); }
                @Override public List<String> mapasConocidos() { return new ArrayList<>(MAPAS_CAT); }
                @Override public List<String> civsConocidas() { return new ArrayList<>(CIVS_CAT); }
                @Override public void dormir(long ms) { SpoilerFreeRecs.dormir(ms); }
                @Override public long perfilAbiertoPid() { return perfil.pidAbierto(); }
                @Override public boolean perfilAbierto() { return perfil.abierto(); }
                @Override public String perfilNombreAbierto() { return perfil.nombreAbierto(); }
                @Override public void mostrarHistorialSiSigueAbierto(long pid, String nombre) {
                    javax.swing.Timer tt = new javax.swing.Timer(900, ev -> { if (perfil.pidAbierto() == pid) perfil.mostrarHistorialPerfil(pid, nombre); });
                    tt.setRepeats(false); tt.start();
                }
                @Override public Iterable<Match> paginaDePartidas(long pid, int pagina, int porPagina) throws IOException, InterruptedException {
                    return COMPANION.partidas(pid, pagina, porPagina);
                }
                @Override public boolean autoCopiarAlDescargar() { return autoSgItem.isSelected(); }
                @Override public void continuarDisponible(boolean visible) { if (continuarBtn != null) continuarBtn.setVisible(visible); }
                @Override public void ajustarGrisesNota(boolean oscuro) { SpoilerFreeRecs.this.ajustarGrises(oscuro); }
                @Override public void actualizarControlesTabla() { SpoilerFreeRecs.this.actualizarControlesTabla(); }
            });

    // ----- Persistencia ------------------------------------------------------

    // ----- Tabla: aplicarOrdenColumnas/guardarColumnas/MatchesTableModel movidos a ui.PartidasView -----

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
     *  (BUSQUEDA): la lógica vive allí; aquí solo queda la fachada que usa el Anfitrion de Mi partida. */
    /** Búsqueda explícita (Enter): la API y el índice local, juntos y sin duplicados; primero lo local. Así un nick cambiado hace poco (que el volcado aún no conoce) también aparece. */
    static List<String[]> buscarPerfiles(String q) { return BUSQUEDA.buscar(q); }
}
