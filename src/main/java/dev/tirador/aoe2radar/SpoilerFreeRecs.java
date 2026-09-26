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
import dev.tirador.aoe2radar.ui.AutoScroll;
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
import dev.tirador.aoe2radar.ui.ClicEnFondo;
import dev.tirador.aoe2radar.ui.VentanaGuardada;
import dev.tirador.aoe2radar.ui.VentanaPrincipalAjustes;
import dev.tirador.aoe2radar.ui.MenuConfiguracion;
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
import static dev.tirador.aoe2radar.util.Texto.esCualquiera;
import static dev.tirador.aoe2radar.util.Texto.normalizarNick;
import static dev.tirador.aoe2radar.util.Texto.recorta;
import static dev.tirador.aoe2radar.util.Texto.sinTildes;
import static dev.tirador.aoe2radar.util.Texto.variantesNick;
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
// Barra de estado y semáforo de operación en curso (T4-Z3): ver ui.BarraEstado.
import dev.tirador.aoe2radar.ui.BarraEstado;
// Raíz de composición (fase 3, tanda 4, Z6): app.Servicios crea COMPANION/API_CLIENTE/... y PAUSA_MS/PER_PAGE,
// VIVO, ELO_1V1, ANOTACIONES, dormir, cargarControl, aprenderCatalogos y buscarPerfiles. Import static para que
// ninguna llamada existente cambie de texto; import normal para las referencias cualificadas (Servicios::dormir...).
import dev.tirador.aoe2radar.app.Servicios;
import static dev.tirador.aoe2radar.app.Servicios.*;
// Socket de vivos y espectar (fase 3, tanda 4, Z4): ver service.EnlaceVivo/service.Espectar/ui.ConfirmacionEspectar.
import dev.tirador.aoe2radar.service.EnlaceVivo;
import dev.tirador.aoe2radar.service.Espectar;
import dev.tirador.aoe2radar.service.ReglasPartida;
import static dev.tirador.aoe2radar.service.EnlaceVivo.tickMs;

public class SpoilerFreeRecs extends JFrame implements dev.tirador.aoe2radar.ui.Navegacion, dev.tirador.aoe2radar.ui.ComponentesTema {

    // ----- Configuración -----------------------------------------------------
    static final Path   PLAYERS_FILE = Path.of("players.txt");
    static final String DONAR_URL    = "https://paypal.me/12Tirador/5EUR";
    // PER_PAGE y PAUSA_MS: movidos a app.Servicios (fase 3, tanda 4, Z6, raíz de composición); llegan aquí por
    // el import static de más arriba, con el mismo nombre y valor.

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

    // aprenderCatalogos: movido a app.Servicios (fase 3, tanda 4, Z6, raíz de composición); llega aquí por el
    // import static de más arriba.

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
    /** Barra de estado, semáforo de la operación en curso y toast: ver ui.BarraEstado. Se crea en el mismo
     *  punto donde antes se creaban status/progreso/cafeBtn (eran campos inicializados, antes del constructor). */
    final BarraEstado barraEstado = new BarraEstado(this, barraEstadoAnfitrion(), DONAR_URL);
    // status/progreso/cafeBtn: alias al mismo objeto de ui.BarraEstado. Decenas de sitios de otras zonas de
    // esta ventana los usan por su nombre de campo (status.setText(...) sobre todo): mover el campo sin
    // tener que tocar cada uno de esos sitios.
    public final JLabel status = barraEstado.status;   // public: lo pinta app.Servicios (avisarPausa429/cargarControl) desde fuera del paquete
    final JButton cafeBtn = barraEstado.cafeBtn;
    final JProgressBar progreso = barraEstado.progreso;
    final Image logo = cargarLogo();
    final JCheckBoxMenuItem autoSgItem = new JCheckBoxMenuItem(t("Enviar al juego al descargar", "Send to game after download"),
            Boolean.parseBoolean(leerConfig("autosavegame", "false")));
    MenuConfiguracion menuConfiguracion;   // ver ui.MenuConfiguracion: botón "Configuración ▾" + esquina "Mi perfil"
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
     *  se construye después de COMPANION (vive en app.Servicios, inicializado en su primer uso: el campo
     *  {@code dialogos}, más arriba) y antes que la propia Watchlist. */
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

    // ELO_1V1: movido a app.Servicios (fase 3, tanda 4, Z6, raíz de composición); llega aquí por el import
    // static de más arriba. Va después de VIVO en Servicios, igual que aquí.
    Integer elo1v1Conocido(long pid) { return menus.elo1v1Conocido(pid); }
    JMenuItem itemJugadorPartida(MatchPlayer p) { return menus.itemJugadorPartida(p); }

    // ----- Campanas: aviso cuando alguien de una vista marcada entra en partida -----
    // La red, la config y la deduplicacion de avisos viven en service.Campanas; aqui solo queda
    // Swing (boton, toast) y el estado compartido (campanaIds, socketExtra). Cableado junto al
    // propio Campanas, no al lado de COMPANION/LIVE/SERVICIO_PERFIL.
    // "watchlist" (más abajo) todavía no existe cuando se construye este campo: el método de abajo lo lee al
    // llamarlo (no al crearse la referencia), y para entonces la ventana ya está montada del todo. Referencia
    // de método en vez de lambda directa: leer "watchlist" (blank final) desde la propia expresión del
    // inicializador no compila («might not have been initialized»), aunque solo se fuera a usar más tarde.
    final Campanas campanas = new Campanas(COMPANION, Config::leerConfig, Config::guardarConfig, this::esGrupoDeUsuarioWatchlist);
    /** Ver el comentario de "campanas": separado en un método para poder pasarlo como Predicate ahí mismo. */
    private boolean esGrupoDeUsuarioWatchlist(String nombre) { return watchlist != null && watchlist.esGrupoDeUsuario(nombre); }
    javax.swing.Timer campanasTimer;   // barrido de campanas (Watchlist): el toast que dispara se sacó a ui.BarraEstado (T4-Z3)

    /** El aviso flotante («X ha empezado una partida»): ver ui.BarraEstado. */
    void mostrarToast(String texto, long matchId) { barraEstado.mostrarToast(texto, matchId); }
    void ocultarToast() { barraEstado.ocultarToast(); }

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
    // Infraestructura de toda la ventana (no de una vista): ver ui.AutoScroll.
    private final AutoScroll autoScroll = new AutoScroll(new AutoScroll.Anfitrion() {
        @Override public void atras() { if (!perfil.h2hAtrasSiProcede()) volverAtras(); }
        @Override public void adelante() { irAdelante(); }
    });

    void instalarAutoScroll() { autoScroll.instalar(); }
    void pararAutoScroll() { autoScroll.parar(); }

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
    // mientras el socket esté sano). El protocolo y qué significa cada evento viven en service.EnlaceVivo
    // (fase 3, tanda 4, Z4): aquí solo queda avisar a las vistas por Vistas (con la vuelta al EDT, que
    // service no puede tocar) y los delegados de una línea que usa el resto de la ventana.
    /** El cableado del socket (ver service.EnlaceVivo); aquí solo se traducen sus avisos a las vistas concretas. */
    final EnlaceVivo enlaceVivo = new EnlaceVivo(VIVO, LIVE, new EnlaceVivo.Vistas() {
        @Override public List<Long> idsWatchlist() {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < playersModel.size(); i++) ids.add(playersModel.get(i).id());
            return ids;
        }
        @Override public List<Long> idsTodosJugadores() {
            List<Long> ids = new ArrayList<>();
            for (Player p : todosJugadores) ids.add(p.id());
            return ids;
        }
        @Override public List<Long> idsTopLadder() {
            List<Long> ids = new ArrayList<>();
            for (Player p : watchlist.topLadderSnapshot()) ids.add(p.id());
            return ids;
        }
        @Override public Set<Long> idsSocketExtra() { return liveNow != null ? liveNow.socketExtra : Set.of(); }
        @Override public void liveEvento(long pid, Match m, boolean terminada) { if (liveNow != null) liveNow.liveEvento(pid, m, terminada); }
        @Override public void avisarSiCampana(long pid, Match m) { watchlist.avisarSiCampana(pid, m); }
        @Override public void avisarMiPartida(long pid, Match m) { watchlist.avisarMiPartida(pid, m); }
        @Override public void avisarTrasCambio() {
            SwingUtilities.invokeLater(() -> { watchlist.actualizarIndicadoresVivos(); watchlist.refrescarAlturasWatch(); playersList.repaint(); partidas.table.repaint(); });
        }
        @Override public void refrescarLiveNowSiAbierta() {
            if (liveNow != null && liveNow.ahoraAbierta) SwingUtilities.invokeLater(() -> liveNow.refrescar(true));
        }
    });
    long ultimoResyncMs;

    /** Conecta (o reconecta) con los ids visibles; si no cambian y el socket está sano, no hace nada. Delegado
     *  de una línea: ver service.EnlaceVivo.sincronizarSocket. Nombre conservado para quien lo llama (Watchlist,
     *  Live now, Mi partida, por su Anfitrion). */
    void sincronizarSocket() { enlaceVivo.sincronizarSocket(); }

    JButton detenerDescBtn, continuarBtn;


    JButton actualizarBtn;      // «Nueva versión X — Descargar», solo si existe una mayor


    // truncarPx: ver util.Formato (recorte con puntos suspensivos, compartido por Live now y la watchlist).

    /** Delegado: la construcción de la UI y el hilo «actualizaciones» viven en ui.MenuConfiguracion (comparten
     *  el botón «Configuración ▾» con el ítem «Buscar actualizaciones…»). Nombre conservado: arrancar() la llama al arranque. */
    void comprobarActualizacion(boolean manual) { menuConfiguracion.comprobarActualizacion(manual); }


    // ANOTACIONES: movido a app.Servicios (fase 3, tanda 4, Z6, raíz de composición); llega aquí por el import
    // static de más arriba. Los diálogos se quedan aquí, en la app.

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

    // main(): movido a app.Main (fase 3, tanda 4, Z6, raíz de composición) con el mismo orden (--make-ico,
    // catálogos, idioma, tema, ventana en el EDT). El pom y el harness llaman a SpoilerFreeRecs.main, así que
    // se queda este delegado de una línea.
    public static void main(String[] args) { dev.tirador.aoe2radar.app.Main.main(args); }

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
            @Override public String resumenVivo(Match m, long pid) { return ReglasPartida.resumenVivo(m, pid); }
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

    /** Lo que ui.BarraEstado necesita del resto de la ventana: el freno de cancelación real (api.Cancelacion)
     *  y la red (api.Http, java.net) viven aquí porque ui no puede importarlos; «operacionTerminada» reactiva
     *  los botones de ui.PartidasView, que tampoco son suyos. */
    private BarraEstado.Anfitrion barraEstadoAnfitrion() {
        return new BarraEstado.Anfitrion() {
            @Override public void iniciarOperacion() { stopOperacion = false; hiloOperacion = null; }
            @Override public void marcarOperacionEnCurso(boolean on) { opEnCurso = on; }
            @Override public void operacionTerminada() {
                partidas.fetchBtn.setEnabled(true); partidas.azarBtn.setEnabled(true); partidas.gteBtn.setEnabled(true);
                if (partidas.dlSel != null) partidas.dlSel.setEnabled(true);
                if (partidas.dlAll != null) partidas.dlAll.setEnabled(true);
            }
            @Override public void pararOperacion() { stopOperacion = true; }
            @Override public void renovarHttp() { HTTP = nuevoHttp(); }
            @Override public void continuarBuscando() { partidas.buscarAleatorias(true); }
            @Override public void abrirTwitch() { SpoilerFreeRecs.this.abrirTwitch(); }
            @Override public void abrirDonacion() { SpoilerFreeRecs.this.abrirDonacion(); }
            @Override public void abrirUrl(String url) { SpoilerFreeRecs.this.abrirUrl(url); }
            @Override public void espectarPartida(long matchId) { SpoilerFreeRecs.this.espectarPartida(matchId); }
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
            @Override public long opSerial() { return barraEstado.opSerial(); }
            @Override public void marcarHiloOperacionActual() { hiloOperacion = Thread.currentThread(); }
            @Override public boolean detenerOperacion() { return stopOperacion; }
            @Override public void dormir(long ms) { Servicios.dormir(ms); }
            @Override public void abrirUrl(String url) { SpoilerFreeRecs.this.abrirUrl(url); }
            @Override public void espectar(Player p) { SpoilerFreeRecs.this.espectar(p); }
            @Override public java.nio.file.Path rutaCaptureAge() { return dev.tirador.aoe2radar.service.Juego.rutaCaptureAge(); }
            @Override public void lanzarCaptureAge(java.nio.file.Path rec) { SpoilerFreeRecs.this.lanzarCaptureAge(rec); }
            @Override public void mostrarToast(String texto, long matchId) { SpoilerFreeRecs.this.mostrarToast(texto, matchId); }
            @Override public void agregarAccionesToast(Runnable accionPerfil, Runnable accionCaraACara) {
                JPanel toastActual = barraEstado.toast();
                if (toastActual == null) return;
                JButton perf = new JButton(t("Su perfil", "Their profile")); perf.setFocusable(false); perf.setMargin(new Insets(0, 6, 0, 6)); perf.addActionListener(a -> accionPerfil.run());
                JButton cara = new JButton(t("Cara a cara", "Head-to-head")); cara.setFocusable(false); cara.setMargin(new Insets(0, 6, 0, 6)); cara.addActionListener(a -> accionCaraACara.run());
                JPanel acc = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); acc.setOpaque(false); acc.add(perf); acc.add(cara);
                toastActual.add(acc, BorderLayout.SOUTH); toastActual.revalidate();
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
     *  constructor porque construirBarraSuperior necesita sus botones; recibe partidas directamente (es un
     *  inicializador de campo, ya construido antes de este punto, como status); conectarVistas lo completa con
     *  el resto de vistas y el split, que no existen hasta construirCentro/montarVentana (mismo truco de
     *  referencia adelantada que ya usa el resto de la ventana; ver el javadoc de ui.Navegador). */
    final dev.tirador.aoe2radar.ui.Navegador navegador;

    public SpoilerFreeRecs(String temaInicial) {   // visible para app.Main (main() construye la ventana desde fuera del paquete)
        super(NOMBRE + " " + VERSION + t(" — tu radar del AoE2 competitivo, sin spoilers · por ", " — your competitive AoE2 radar, spoiler-free · by ") + AUTOR);
        configurarVentana();
        navegador = new dev.tirador.aoe2radar.ui.Navegador(status, partidas, this::perfilDesdeBoton);

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
        navegador.conectarVistas(watchlist, perfil, liveNow, techTree, ratings, civStats, directos, centroCards, splitPrincipal);

        arrancar();
    }

    // Cierre, ventana recordada, foco perdido e iconos: ajustes del JFrame antes
    // de construir ningun panel. Se separa del resto del constructor porque es
    // configuración de ventana, no construcción de paneles.
    private void configurarVentana() {
        VentanaPrincipalAjustes.configurar(this, logo, new VentanaPrincipalAjustes.Anfitrion() {
            @Override public void alCerrar() { guardarVentana(); enlaceVivo.cerrar(); }
            @Override public void alPerderFoco() { watchlist.ocultarHoverCard(true); }
        });
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

        // El botón «Configuración ▾», todos sus ítems y la esquina «Mi perfil»: ver ui.MenuConfiguracion. Lo
        // que sus ítems necesitan de otras zonas (tema, watchlist, vigilancia, CaptureAge, mi perfil, acerca
        // de, carpeta savegame…) llega por MenuConfiguracion.Anfitrion, implementado aquí con lambdas.
        menuConfiguracion = new MenuConfiguracion(temaInicial, autoSgItem, CONTROL_SERVICE, new MenuConfiguracion.Anfitrion() {
            @Override public Component padre() { return SpoilerFreeRecs.this; }
            @Override public void estado(String texto) { status.setText(texto); }
            @Override public void aplicarTema(String tema) { SpoilerFreeRecs.aplicarTema(tema, SpoilerFreeRecs.this); }
            @Override public void abrirUrl(String url) { SpoilerFreeRecs.this.abrirUrl(url); }
            @Override public void mostrarNuevaVersion(String etiqueta) {
                if (actualizarBtn != null) { actualizarBtn.setText(etiqueta); actualizarBtn.setVisible(true); }
            }
            @Override public boolean mostrarEloWatch() { return watchlist.mostrarEloWatch; }
            @Override public void fijarMostrarEloWatch(boolean mostrar) { watchlist.mostrarEloWatch = mostrar; }
            @Override public void repintarListaJugadores() { playersList.repaint(); }
            @Override public void reiniciarVigilancia() {
                if (vigilante != null) { vigilante.setDelay(tickMs()); vigilante.setInitialDelay(tickMs()); vigilante.restart(); }
            }
            @Override public void cargarAliasesYNotas() { cargarAliases(); cargarNotas(); }
            @Override public boolean modoTop() { return watchlist.modoTop(); }
            @Override public void forzarRecargaTop() { watchlist.forzarRecargaTop(); }
            @Override public Path elegirCarpetaSavegame() { return partidas.elegirSavegameManual(); }
            @Override public void refiltrarPartidas() { partidas.applyFilters(); }
            @Override public boolean hayCarpetaSavegame() { return partidas.obtenerSavegame(true) != null; }
            @Override public void mostrarMiPerfil() { abrirMiPerfil(); }
            @Override public void cambiarCuentaPropia() { preguntarMiNick(); }
            @Override public void mostrarAcercaDe() { showAbout(); }
        });
        JPanel esquina = menuConfiguracion.esquina();

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
        JPanel filaEstado = barraEstado.construirFila();   // progreso/detener/continuar, status, firma/café/actualizar: ver ui.BarraEstado
        firma = barraEstado.firma;
        actualizarBtn = barraEstado.actualizarBtn;
        detenerDescBtn = barraEstado.detenerDescBtn;
        continuarBtn = barraEstado.continuarBtn;

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
        liveNow = new LiveNowView(campanas, LIVE, List.of(watchlist.PAISES), todosJugadores, eloWatch, twitchLive, civ -> techTree.claveCivDeNombre(civ), Tareas.SWING, this, menus, this, new LiveNowView.Anfitrion() {
            @Override public List<String> clanesGuardados() { return watchlist.clanesGuardados(); }
            @Override public String paisSel() { return watchlist.paisSel(); }
            @Override public String clanBuscado() { return watchlist.clanBuscado(); }
            @Override public boolean campanaContiene(long pid) { return watchlist.campanaContiene(pid); }
            @Override public void sincronizarSocket() { SpoilerFreeRecs.this.sincronizarSocket(); }
            @Override public boolean socketConectado() { return enlaceVivo.conectado(); }
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
        // Ver ui.ClicEnFondo: mezcla excepciones de varias vistas, así que se las pedimos por interfaz.
        ClicEnFondo.instalarGlobal(this, new ClicEnFondo.Anfitrion() {
            @Override public JLabel cabeceraWatchlist() { return watchlist.cabLabel; }
            @Override public JPanel sujetosPanel() { return sujetosPanel; }
            @Override public JTable tablaPartidas() { return partidas.table; }
            @Override public JTable tablaDirectos() { return directos == null ? null : directos.tablaDirectos; }
            @Override public JComponent panelRatings() { return ratings == null ? null : ratings.panel(); }
            @Override public void ocultarDetalleTechTree() { techTree.ocultarDetalle(); }
            @Override public boolean haySeleccionWatchlist() { return !playersList.isSelectionEmpty(); }
            @Override public void limpiarSeleccionWatchlist() { playersList.clearSelection(); partidas.actualizarTextoBuscar(); }
        });
        add(split, BorderLayout.CENTER);
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
        enlaceVivo.iniciarPing();
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
            boolean tocaSondear = ctrlOn("sondeo") && (!enlaceVivo.conectado() || ahora - ultimoResyncMs >= resync);
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

        new javax.swing.Timer(3_600_000, e -> new Thread(Servicios::cargarControl, "control").start()).start();
        vigilante.setInitialDelay(tickMs());
        vigilante.start();
        if (Boolean.parseBoolean(leerConfig("buscar_al_abrir", "true")))
            SwingUtilities.invokeLater(() -> { if (!watchlist.modoTop() && playersModel.size() > 0) partidas.fetchMatches(partidas.fetchBtn); });
    }

    /** El endpoint de Steam (api.SteamApi), aparte del companion. Campo de instancia, no static: así no importa el
     *  orden de texto frente a API_CLIENTE (vive en app.Servicios, inicializado en su primer uso: el campo
     *  {@code dialogos}, mucho más arriba) — para cuando se crea {@code steam}, Servicios ya está inicializado. */
    final SteamApi steam = new SteamApi(API_CLIENTE);

    void abrirUrl(String url) {
        try { Desktop.getDesktop().browse(URI.create(url)); }
        catch (Exception ex) { status.setText(t("No se pudo abrir el navegador: ", "Couldn't open the browser: ") + causa(ex)); }
    }

    /** La zona central alterna entre la tabla de recs y los directos. El cromo vive en ui.Navegador; se queda
     *  este delegado porque medio fichero llama a «mostrarDirectos» por su nombre (Watchlist, Partidas, Perfil…). */
    void mostrarDirectos(boolean mostrar) { navegador.mostrarDirectos(mostrar); }


    boolean usarCA() { return Boolean.parseBoolean(leerConfig("usar_ca", "false")); }

    /** Espectar en el juego (protocolo aoe2de://), CaptureAge de acompañante y la verificación por red antes
     *  de lanzar: la parte sin Swing vive en service.Espectar (fase 3, tanda 4, Z4). */
    final Espectar espectar = new Espectar(LIVE);

    /** Lanza CaptureAge — con una rec (la reproduce con su overlay) o sin argumentos (modo acompañante: se
     *  engancha al juego al espectar). Buscar la ruta y arrancar el proceso viven en service.Espectar (fase 3,
     *  tanda 4, Z4); aquí solo queda traducir el resultado al texto de estado. */
    void lanzarCaptureAge(Path rec) {
        Espectar.ResultadoCaptureAge r = espectar.lanzarCaptureAge();
        switch (r.estado()) {
            case RUTA_AUSENTE -> status.setText(t("No encuentro CaptureAge: fija su ruta en Configuración → Cambiar ruta de CaptureAge…",
                    "Can't find CaptureAge: set its path in Settings → Change CaptureAge path…"));
            case LANZADO -> status.setText(t("Lanzando CaptureAge de acompañante…", "Launching CaptureAge alongside…"));
            case FALLO -> status.setText(t("No se pudo lanzar CaptureAge: ", "Couldn't launch CaptureAge: ") + causa(r.error()));
        }
    }

    /** Abre el juego espectando una partida en curso (protocolo
     *  aoe2de://1/matchId, el mismo que usa aoe2companion/aoe2recs). */
    /** Antes de lanzar el juego desde un doble clic: confirmación con «no volver a preguntar». Delegado: ver
     *  ui.ConfirmacionEspectar. Nombre conservado para las Anfitrion que lo llaman (Live now, Perfil, Partidas). */
    boolean confirmarEspectar(String quien) { return dev.tirador.aoe2radar.ui.ConfirmacionEspectar.confirmar(this, quien); }

    void espectarPartida(long matchId) {
        log("espectar: lanzando aoe2de://1/" + matchId);
        try {
            espectar.espectarPartida(matchId);
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
     *  «Invalid match ID». La llamada de red y sus logs viven en service.Espectar.viva (fase 3, tanda 4, Z4). */
    void espectarVerificando(long profileId, long matchId) {
        status.setText(t("Comprobando que la partida sigue en curso…", "Checking the game is still live…"));
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() { return espectar.viva(profileId, matchId, PER_PAGE); }
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
    @Override public JPopupMenu configMenu() { return menuConfiguracion == null ? null : menuConfiguracion.menu(); }
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
    void aplicarVentanaGuardada() { VentanaGuardada.aplicar(this); }

    void guardarVentana() { VentanaGuardada.guardar(this, splitPrincipal); }

    /** Muestra u oculta la barra de progreso de la fila de estado y el semáforo de la operación en curso:
     *  ver ui.BarraEstado. */
    void trabajando(boolean on) { barraEstado.trabajando(on); }

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
                @Override public List<String[]> buscarPerfiles(String nick) { return Servicios.buscarPerfiles(nick); }
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
    final BarridoVivos barridoVivos = new BarridoVivos(COMPANION, Reloj.SISTEMA, ReglasPartida::resumenVivo,
            Snapshots.ELO_AYER, Servicios::dormir, PAUSA_MS, PER_PAGE);

    /** RecService: descarga, disco y savegame para UNA partida (ver service.RecService). Campo de instancia (no
     *  static): se cablea junto al código que lo usa, sin tocar la raíz de composición (app.Servicios: COMPANION,
     *  LIVE, SERVICIO_PERFIL...). */
    final RecService recService = new DescargaRecs(Recs::descargarRec, RecsDisco::destino, Juego::copiarASavegame,
            Servicios::dormir, PAUSA_MS);

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
                @Override public long operacionActual() { return barraEstado.opSerial(); }
                @Override public boolean detenido() { return stopOperacion; }
                @Override public void pararOperacion() { stopOperacion = true; }
                @Override public void anotarHiloOperacion() { hiloOperacion = Thread.currentThread(); }
                @Override public void aprenderCatalogos(List<Match> res) { Servicios.aprenderCatalogos(res); }
                @Override public List<String> mapasConocidos() { return new ArrayList<>(MAPAS_CAT); }
                @Override public List<String> civsConocidas() { return new ArrayList<>(CIVS_CAT); }
                @Override public void dormir(long ms) { Servicios.dormir(ms); }
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
    // Todo este bloque (avisarPausa429, cargarControl, TRANSPORTE_CONTROL, la cadena CONTROL_SERVICE →
    // API_CLIENTE → COMPANION → LIVE → TOP_LADDER_SERVICE → SERVICIO_PERFIL → BUSQUEDA → TWITCH_SERVICE, dormir
    // y buscarPerfiles) se movió a app.Servicios (fase 3, tanda 4, Z6, raíz de composición), con el mismo orden
    // de texto. Llega aquí por el import static de más arriba, así que ninguna llamada cambia.
}
