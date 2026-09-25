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

    /** Grupos donde se puede fichar a alguien: «General», los grupos con gente ahora mismo y los guardados en
     *  config (idéntico al bloque que arma menuContextualWatchlist/menuDeJugador/mostrarVinculadas). */
    Set<String> gruposParaFichar() {
        Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        gs.add(GRUPO_GENERAL);
        for (Player x : todosJugadores) gs.add(x.grupo());
        gs.addAll(gruposConfig());
        return gs;
    }

    /** Diálogos de un jugador (nota, alias, cuentas vinculadas, nicks anteriores): ver ui.DialogosJugador. Lo que
     *  toca la tabla/lista de la ventana llega por su Anfitrion; la red de Steam, por RedSteam (ui no importa api). */
    final dev.tirador.aoe2radar.ui.DialogosJugador dialogos = new dev.tirador.aoe2radar.ui.DialogosJugador(this, ANOTACIONES, SERVICIO_PERFIL,
            new dev.tirador.aoe2radar.ui.DialogosJugador.RedSteam() {
                @Override public String steamId(long pid) throws Exception { return COMPANION.perfil(pid).steamId(); }
                @Override public List<String[]> alias(String steamId) throws Exception { return steam.alias(steamId); }
            },
            new dev.tirador.aoe2radar.ui.DialogosJugador.Anfitrion() {
                @Override public void repintarLista() { playersList.repaint(); }
                @Override public void refrescarAlturas() { refrescarAlturasWatch(); }
                @Override public void refrescarTabla() { partidas.refrescarTabla(); }
                @Override public void ajustarColumnasTabla() { partidas.ajustarColumnas(); }
                @Override public void actualizarControles() { actualizarControlesTabla(); }
                @Override public void refrescarSujetos() { partidas.refrescarSujetos(partidas.ultimosSujetos, invitado != null); }
                @Override public void mostrarEstado(String texto) { status.setText(texto); }
                @Override public boolean enWatchlist(long pid) { return containsPlayerId(pid); }
                @Override public void ponerEloWatch(long pid, int elo) { eloWatch.put(pid, elo); }
                @Override public Set<String> gruposDisponibles() { return gruposParaFichar(); }
                @Override public String grupoActivo() { return SpoilerFreeRecs.this.grupoActivo(); }
                @Override public void agregarJugador(long pid, String nombre, String grupo) { todosJugadores.add(new Player(pid, nombre, grupo)); }
                @Override public void guardarJugadores() { savePlayers(); }
                @Override public void reconstruirGrupos() { rebuildGrupos(); }
                @Override public void marcarFamiliaVinculada(Set<Long> familia) { marcarVinculo(familia); }
                @Override public void aplicarFiltro() { aplicarFiltroGrupo(); }
                @Override public void refrescarWatchlist() { SpoilerFreeRecs.this.refrescarWatchlist(); }
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
                @Override public boolean enWatchlist(long pid) { return containsPlayerId(pid); }
                @Override public Set<String> gruposDisponibles() { return gruposParaFichar(); }
                @Override public void anadirAWatchlist(long pid, String nombre, String grupo) {
                    todosJugadores.add(new Player(pid, nombre, grupo));
                    savePlayers();
                    rebuildGrupos();
                    aplicarFiltroGrupo();
                    refrescarWatchlist();
                    status.setText(nombre + t(" añadido a «", " added to \u201C") + grupo + "\u00bb.");
                    ofrecerVinculadasTrasAlta(pid, nombre, grupo);   // siempre que alguien entra en un grupo, se revisan sus cuentas vinculadas
                }
                @Override public String elegirGrupoDialog(String nombreSugerido) { return SpoilerFreeRecs.this.elegirGrupoDialog(nombreSugerido); }
            });

    // «Al azar por ELO» / «Guess the ELO»: muestreo, filtros y caché de sesión viven en el servicio (una sola
    // instancia por ventana, con techTree.claveCivDeNombre y dormir() de la propia ventana como colaboradores).
    final AzarService azarService = new AzarServiceCompanion(COMPANION, civ -> SpoilerFreeRecs.this.techTree.claveCivDeNombre(civ),
            Snapshots::muestraAyer, ms -> dormir(ms), PER_PAGE, PAUSA_MS);


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

    // Presentación de Match (enfrentamiento/refNombre/eloAntesDespues/rivalTexto/refConVeredicto/FechaCell):
    // movida a ui.PartidasTexto (fase 3, tanda 3, oleada B). SUJETOS: movido a ui.PartidasView.SUJETOS.

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


    /** La ventana de búsqueda en horas: número × unidad (recordados entre sesiones). */
    int horasVentana() {
        int n = (int) hoursSpinner.getValue();
        int u = unidadCombo.getSelectedIndex();
        return Math.min(24 * 7, n * (u == 1 ? 24 : u == 2 ? 24 * 7 : 1));   // techo: una semana; el histórico completo vive en el perfil («Todas las partidas del perfil»)
    }
    boolean actualizandoMapas;
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
    TitledBorder tituloWatch;
    JToggleButton soloVivosBtn;
    JLabel resumenWatch;   // «50 jugadores · 6 en directo»
    JButton delBtn;
    JLabel cabLabel;
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
    JToggleButton directosBtn;
    Player objetivoForzado;           // jugador concreto pedido con «Ver sus partidas» (una sola búsqueda)


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
        boolean tablaVisible = partidas.recsCards != null && partidas.recsCards.isShowing() && !(directosBtn != null && directosBtn.isSelected()) && !(techTreeBtn != null && techTreeBtn.isSelected()) && !(ladderBtn != null && ladderBtn.isSelected()) && !(civStatsBtn != null && civStatsBtn.isSelected()) && !(perfil != null && perfil.abierto()) && !(liveNow != null && liveNow.ahoraAbierta);
        boolean hay = tablaVisible && partidas.hayPartidas();
        if (partidas.filaNota != null) partidas.filaNota.setVisible(tablaVisible);
        if (partidas.filaBotonesInferiores != null) partidas.filaBotonesInferiores.setVisible(tablaVisible);
        status.setVisible(tablaVisible || (directosBtn != null && directosBtn.isSelected()));   // los mensajes de estado, solo donde se usan
        sincronizarPestanas(tablaVisible);
        if (partidas.resultadosBtn != null) partidas.resultadosBtn.setVisible(hay);
        if (partidas.parModo != null) partidas.parModo.setVisible(hay);
        if (partidas.parRival != null) partidas.parRival.setVisible(hay);
    }

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



    /** Cada tabla nueva nace tapada: se apaga el modo consulta y se cierran los ojos. */
    final Set<Long> topVerificados = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static final String TOP_CLAN = t("\u2605 Top clan", "\u2605 Clan top");
    JComboBox<String> topNCombo; boolean rellenandoTopN; JPanel norteWatchRef;
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
        partidas.taparResultados(); apagarForma();
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
        partidas.taparResultados(); apagarForma();
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
        else if (partidas.ultimosSujetos.size() == 1 && partidas.recsCards != null && partidas.recsCards.isShowing()) abrirPerfil(partidas.ultimosSujetos.get(0).id(), nombreVisible(partidas.ultimosSujetos.get(0).id(), partidas.ultimosSujetos.get(0).name()));   // «Partidas de: X» → su perfil
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
        partidas.taparResultados(); apagarForma();
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
        partidas.taparResultados(); apagarForma();
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
        partidas.taparResultados(); apagarForma();
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
            SwingUtilities.invokeLater(() -> { actualizarIndicadoresVivos(); refrescarAlturasWatch(); playersList.repaint(); partidas.table.repaint(); });
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
        if (cambio) SwingUtilities.invokeLater(() -> { actualizarIndicadoresVivos(); refrescarAlturasWatch(); playersList.repaint(); partidas.table.repaint(); });
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

    // notaDe, pedirAlias, pedirNota, borrarNota, mostrarVinculadas y nicksAnteriores se movieron a
    // ui.DialogosJugador en la tanda 3 (oleada A2, T3-A2): delegados de una línea con el mismo nombre.
    String notaDe(long pid) { return dialogos.notaDe(pid); }
    void borrarNota(long pid, String nombre) { dialogos.borrarNota(pid, nombre); }

    void pedirNota(long pid, String nombre) { dialogos.pedirNota(pid, nombre); }

    void pedirAlias(long pid, String original) { dialogos.pedirAlias(pid, original); }

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

    JLabel firma;                       // grises regulados según el tema
    JLabel watchPista1, watchPista2, watchPista3;   // explicación visible de la Watchlist
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

    SpoilerFreeRecs(String temaInicial) {
        super(NOMBRE + " " + VERSION + t(" — tu radar del AoE2 competitivo, sin spoilers · por ", " — your competitive AoE2 radar, spoiler-free · by ") + AUTOR);
        configurarVentana();

        JPanel left = construirWatchlist();

        JPanel top = construirBarraSuperior(temaInicial);

        partidas.construirTabla();

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
    }

    // El panel izquierdo: la Watchlist (buscador, grupos, pais/clan, la lista de
    // jugadores seguidos con su ELO y su «en vivo», y los botones para quitarlos).
    // Devuelve el panel para que el constructor lo pase al split principal.
    private JPanel construirWatchlist() {
        // Panel izquierdo: jugadores seguidos (la selección filtra la tabla)
        playersList.setVisibleRowCount(12);
        playersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { partidas.applyFilters(); if (ratings != null) ratings.sincronizarSeleccion(); if (perfil != null) perfil.sincronizarSeleccion(); }   // con Ratings o Perfil abiertos, la selección se refleja allí
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
                            dev.tirador.aoe2radar.ui.PartidasView.SUJETOS.clear();
                            partidas.refrescarSujetos(List.of(), false);
                            partidas.actualizarTextoBuscar();
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
                    if (!sobreFila) { playersList.clearSelection(); partidas.actualizarTextoBuscar(); }   // como el Explorador: clic en el vacío = sin selección
                    return;
                }
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown()) {
                    int idx = playersList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        Player p = playersModel.get(idx);
                        String bajo = textoBajo(idx, e.getPoint());
                        if (bajo.contains("TW") || bajo.contains("\u21A5") || bajo.contains("\u270E") || sobreNota(idx, e.getPoint())) return;   // el clic simple ya actuó
                        playersList.setSelectedIndex(idx);   // doble clic = sus partidas, SIEMPRE (espectar vive en el clic derecho)
                        partidas.fetchMatches(partidas.fetchBtn);
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
            partidas.applyFilters();
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
                    partidas.actualizarTextoBuscar();
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
        return left;
    }

    // La barra de arriba: ventana de horas/buscar, filtros, pestañas de vistas,
    // flechas de historial y el menú Configuración (idioma, tema, letra...). Recibe
    // temaInicial porque el menú de Tema marca la opción ya activa al abrir.
    private JPanel construirBarraSuperior(String temaInicial) {
        // Barra superior: ventana de horas + buscar + filtro de modo + acerca de
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
        partidas.agregarFilaConsulta(fila1);   // modo, rival (sugerencias), mapa, periodo, Buscar partidas, Al azar por ELO, Guess the ELO: ver ui.PartidasView
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
            if (perfil.pidAbierto() > 0 && partidas.objetivoEtiqueta != null && partidas.objetivoEtiqueta.id() == perfil.pidAbierto() && perfil.historialEnTabla() != perfil.pidAbierto() && (partidas.ultimosSujetos.size() != 1 || partidas.ultimosSujetos.get(0).id() != perfil.pidAbierto()) && partidas.fetchWorker == null)
                SwingUtilities.invokeLater(() -> partidas.fetchMatches(partidas.fetchBtn));
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
                    @Override public void actualizarTextoBuscar() { partidas.actualizarTextoBuscar(); }
                    @Override public JToggleButton crearBotonPestana(String texto, Icon icono) { return pestana(texto, icono); }
                    @Override public void traerAlFrente() { toFront(); requestFocus(); }
                    @Override public void mostrarEstadoGlobal(String texto) { status.setText(texto); }
                    @Override public void cerrarPerfil() { mostrarDirectos(false); }
                    @Override public boolean enCursoReal(Match m) { return dev.tirador.aoe2radar.cache.Vivos.enCursoReal(m); }
                    @Override public boolean confirmarEspectar(String nombre) { return SpoilerFreeRecs.this.confirmarEspectar(nombre); }
                    @Override public void espectarPartida(long matchId) { SpoilerFreeRecs.this.espectarPartida(matchId); }
                    @Override public void cargarPartidasEnTabla(List<Match> lista, Player sujeto) {
                        playersList.clearSelection();
                        partidas.cargarPartidasEnTabla(lista, sujeto, vistaActualId());
                        mostrarDirectos(false);
                    }
                    @Override public void buscarPartidasDe(long pid, String nombre) {
                        Player p = new Player(pid, nombre, grupoDestino());
                        objetivoForzado = p; invitado = p; vistaDelInvitado = vistaActualId();
                        playersList.clearSelection(); aplicarFiltroGrupo(); mostrarDirectos(false); partidas.fetchMatches(partidas.fetchBtn);
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

    // Cargas iniciales (canales, jugadores, tema/fuentes) y el arranque de los
    // temporizadores: vigilante de vivos, ping del socket, comprobar actualizacion,
    // precarga del tech tree y del ladder. Es lo último que hace el constructor.
    private void arrancar() {
        cargarCanales();
        loadPlayers();
        sanearVinculosHuerfanos();
        getRootPane().setDefaultButton(partidas.fetchBtn);   // acción primaria: acento y Enter
        ajustarGrises(flatLafDisponible && temaOscuroActivo);
        ajustarBotonesEspeciales(flatLafDisponible && temaOscuroActivo);
        ajustarFuentesSecundarias();
        partidas.table.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke("ENTER"), "descargarSeleccion");
        partidas.table.getActionMap().put("descargarSeleccion", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { partidas.download(partidas.selectedRows()); }
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
            SwingUtilities.invokeLater(() -> { if (!modoTop() && playersModel.size() > 0) partidas.fetchMatches(partidas.fetchBtn); });
    }





    /** Enviar al juego: copia lo descargado y descarga+envía lo que falte. */




    // ----- Revelar resultado (único punto que enseña spoilers, bajo demanda) --





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
        if (sujetosPanel != null && sujetosPanel.isVisible() && !vistaActualId().equals(partidas.vistaDeSujetos)) {
            dev.tirador.aoe2radar.ui.PartidasView.SUJETOS.clear();
            partidas.refrescarSujetos(List.of(), false);   // la cabecera refleja la tabla; otra vista, otra historia
            partidas.taparResultados();
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
        partidas.actualizarTextoBuscar();
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
                        for (Match m : partidas.all)
                            if (m.id == fresco.id && m.finished == null) {
                                m.finished = fresco.finished;   // la EN DIRECTO de la tabla ya acabó
                                m.players = fresco.players;
                                tablaTocada = true;
                            }
                    if (tablaTocada) partidas.applyFilters();
                    else partidas.table.repaint();
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

    JMenu menuPerfilNavegador(long id) { return menus.perfilNavegador(id); }

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
        String ref = partidas.refNombre(m);
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

    boolean containsPlayerId(long id) {
        return listaSeguidos.contiene(todosJugadores, id);
    }

    /** El endpoint de Steam (api.SteamApi), aparte del companion. Campo de instancia, no static: así no importa el
     *  orden de texto frente a API_CLIENTE (static, definido más abajo) — se crea cuando ya existe la ventana, y para
     *  entonces la clase entera (con sus static) ya está inicializada. */
    final SteamApi steam = new SteamApi(API_CLIENTE);

    /** Historial de alias que guarda Steam para la cuenta (endpoint público
     *  de la comunidad, vía el steamId del companion). Solo bajo demanda. */
    void nicksAnteriores(long pid, String nombre) { dialogos.nicksAnteriores(pid, nombre); }

    /** Submenú de acciones sobre un jugador concreto (contextual de la tabla). */
    JMenu menuDeJugador(long pid, String nombre) { return menus.deJugador(pid, nombre); }

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
        if (mostrar) { partidas.taparResultados(); apagarForma(); }   // cambiar de pantalla apaga el modo consulta y la forma
        ((CardLayout) centroCards.getLayout()).show(centroCards, mostrar ? "directos" : "recs");
        if (!mostrar && !partidas.hayPartidas() && partidas.fetchWorker == null) partidas.mostrarGuiaVacia(true);   // sin partidas: la guía con su botón, no una tabla vacía
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
    void mostrarVinculadas(long profileId, String nombre) { dialogos.mostrarVinculadas(profileId, nombre); }

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
                        for (Match m : partidas.all)
                            if (m.id == fresco.id && m.finished == null) {
                                m.finished = fresco.finished;   // la EN DIRECTO de la tabla acabó:
                                m.players = fresco.players;     // fecha real y resultado disponibles
                                tablaTocada = true;
                            }
                }
                if (tablaTocada) partidas.applyFilters();
                else partidas.table.repaint();   // p. ej. una viva que cruza el umbral de fantasma
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
    @Override public JLabel watchPista1() { return watchPista1; }
    @Override public JLabel watchPista2() { return watchPista2; }
    @Override public JLabel watchPista3() { return watchPista3; }
    @Override public TitledBorder tituloWatch() { return tituloWatch; }
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
    /** Delegado: ver ui.MiPartidaPanel.mostrarSuperposicion. Nombre conservado para avisarMiPartida. */
    void mostrarSuperposicion(String texto, List<Object[]> fichas, int ms) { miPartida.mostrarSuperposicion(texto, fichas, ms); }
    /** Delegado: ver ui.MiPartidaPanel.abrirMiPerfil. Nombre conservado para el botón "Mi perfil". */
    void abrirMiPerfil() { miPartida.abrirMiPerfil(); }
    /** Delegado: ver ui.MiPartidaPanel.preguntarMiNick. Nombre conservado para "Cambiar de cuenta". */
    void preguntarMiNick() { miPartida.preguntarMiNick(); }
    /** Un jugador concreto elegido de una sugerencia: en Perfil se abre directamente; en el resto, las mismas tres opciones del buscador, sin repetir la búsqueda. */
    void jugadorElegido(long pid, String nombre) {
        if (perfil.abierto()) { abrirPerfil(pid, nombre); return; }
        String verO = t("Ver sus partidas", "View their games"), perfO = t("Ver perfil", "View profile"), addO = t("Añadir al grupo\u2026", "Add to group\u2026"), canO = t("Cancelar", "Cancel");
        int r0 = JOptionPane.showOptionDialog(this, nombre + "  ·  " + pid, t("Resultados", "Results"), JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[]{ perfO, verO, addO, canO }, perfO);
        if (r0 == 0) abrirPerfil(pid, nombre);
        else if (r0 == 1) { Player pl = new Player(pid, nombre, grupoDestino()); objetivoForzado = pl; invitado = pl; vistaDelInvitado = vistaActualId(); playersList.clearSelection(); aplicarFiltroGrupo(); mostrarDirectos(false); partidas.fetchMatches(partidas.fetchBtn); }
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
                                    partidas.refrescarSujetos(partidas.ultimosSujetos, invitado != null);   // el ELO recién llegado, a la cabecera
                                });
                            }).start();
                            partidas.fetchMatches(partidas.fetchBtn);
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

    // ----- Descarga de recs --------------------------------------------------


    /** RecService: descarga, disco y savegame para UNA partida (ver service.RecService). Campo de instancia (no
     *  static): se cablea junto al código que lo usa, sin tocar el bloque static de COMPANION/LIVE/SERVICIO_PERFIL. */
    final RecService recService = new DescargaRecs(Recs::descargarRec, RecsDisco::destino, Juego::copiarASavegame,
            SpoilerFreeRecs::dormir, PAUSA_MS);

    /** La pestaña «Partidas»: ver ui.PartidasView/ui.PartidasPresenter (fase 3, tanda 3, oleada B). Se construye
     *  aquí (como field initializer: se ejecuta antes que configurarVentana/construirWatchlist/etc., igual que
     *  dialogos/menus/azarService/recService/barridoVivos) para que sus botones existan cuando
     *  construirBarraSuperior los necesite. Watchlist sigue en esta clase: se le pasa por EnlaceWatchlist con
     *  lambdas a los métodos/campos de aquí; el resto de la ventana, por Anfitrion. */
    final dev.tirador.aoe2radar.ui.PartidasView partidas = new dev.tirador.aoe2radar.ui.PartidasView(this, menus, dialogos, this,
            azarService, recService, barridoVivos, PER_PAGE, PAUSA_MS,
            new dev.tirador.aoe2radar.ui.PartidasView.EnlaceWatchlist() {
                @Override public List<Player> seleccion() { return playersList.getSelectedValuesList(); }
                @Override public int seleccionSize() { return playersList.getSelectedIndices().length; }
                @Override public boolean soloVivosMarcado() { return soloVivosBtn != null && soloVivosBtn.isSelected(); }
                @Override public boolean modoTop() { return SpoilerFreeRecs.this.modoTop(); }
                @Override public String grupoDestino() { return SpoilerFreeRecs.this.grupoDestino(); }
                @Override public List<Player> conFamilias(List<Player> base) { return SpoilerFreeRecs.this.conFamilias(base); }
                @Override public void limpiarSeleccion() { playersList.clearSelection(); }
                @Override public int totalJugadores() { return playersModel.size(); }
                @Override public Player jugador(int indice) { return playersModel.get(indice); }
                @Override public Integer eloDe(long pid) { return eloWatch.get(pid); }
                @Override public String grupoDeJugador(long pid) { return SpoilerFreeRecs.this.grupoDeJugador(pid); }
                @Override public List<Player> todosJugadores() { return todosJugadores; }
                @Override public void actualizarIndicadoresVivos() { SpoilerFreeRecs.this.actualizarIndicadoresVivos(); }
                @Override public void aplicarFiltroGrupo() { SpoilerFreeRecs.this.aplicarFiltroGrupo(); }
                @Override public String tipCuentaVinculada(Match m) { return SpoilerFreeRecs.this.tipCuentaVinculada(m); }
                @Override public Player objetivoForzado() { return objetivoForzado; }
                @Override public void fijarObjetivoForzado(Player p) { objetivoForzado = p; }
                @Override public void limpiarObjetivoForzado() { objetivoForzado = null; }
                @Override public Player invitado() { return invitado; }
                @Override public void limpiarInvitado() { invitado = null; }
                @Override public String vistaActualId() { return SpoilerFreeRecs.this.vistaActualId(); }
                @Override public int horasVentana() { return SpoilerFreeRecs.this.horasVentana(); }
                @Override public void guardarVentanaHoras() {
                    guardarConfig("ventana_n", String.valueOf((int) hoursSpinner.getValue()));
                    guardarConfig("horas", String.valueOf(SpoilerFreeRecs.this.horasVentana()));
                }
                @Override public void actualizarTextoForma() { SpoilerFreeRecs.this.actualizarTextoForma(); }
                @Override public JPanel sujetosPanel() { return sujetosPanel; }
            },
            new dev.tirador.aoe2radar.ui.PartidasView.Anfitrion() {
                @Override public void estado(String texto) { status.setText(texto); }
                @Override public void mostrarDirectos(boolean mostrar) { SpoilerFreeRecs.this.mostrarDirectos(mostrar); }
                @Override public void refrescarDirectos() {
                    directos.refrescarForzado();
                    if (directosBtn != null && directosBtn.isSelected()) status.setText(t("Refrescando directos…", "Refreshing streams…"));
                }
                @Override public void enfocarBuscador() { if (buscaNick != null) { buscaNick.requestFocusInWindow(); buscaNick.selectAll(); } }
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
     *  (BUSQUEDA): la lógica vive allí, aquí solo queda la fachada para no tocar los cinco buscadores de la interfaz
     *  (ni addPlayerDialog, que usa buscarLocal con su propio orden) ahora. */
    /** Sugerencias al teclear: el índice local si tiene algo (sin llamada); si no, la API. */
    static List<String[]> sugerirPerfiles(String q) { return BUSQUEDA.sugerir(q); }
    /** Búsqueda explícita (Enter): la API y el índice local, juntos y sin duplicados; primero lo local. Así un nick cambiado hace poco (que el volcado aún no conoce) también aparece. */
    static List<String[]> buscarPerfiles(String q) { return BUSQUEDA.buscar(q); }
    /** Sugerencias locales del índice nocturno de nombres (top 40.000): {pid, nombre, «nombre · país · ELO»}, hasta 8, las de más ELO primero. */
    static List<String[]> buscarLocal(String q) { return BUSQUEDA.local(q); }
}
