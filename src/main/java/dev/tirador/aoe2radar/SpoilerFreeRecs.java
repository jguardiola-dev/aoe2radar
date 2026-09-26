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

import dev.tirador.aoe2radar.api.Recs;
import dev.tirador.aoe2radar.api.SteamApi;
import dev.tirador.aoe2radar.cache.Anotaciones;
import dev.tirador.aoe2radar.cache.CacheMemoria;
import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.RecsDisco;
import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.service.Campanas;
import dev.tirador.aoe2radar.service.DescargaRecs;
import dev.tirador.aoe2radar.service.FormaCompanion;
import dev.tirador.aoe2radar.service.FormService;
import dev.tirador.aoe2radar.service.Juego;
import dev.tirador.aoe2radar.service.MiPartidaServiceJuego;
import dev.tirador.aoe2radar.service.RecService;
import dev.tirador.aoe2radar.service.StatsService;
import dev.tirador.aoe2radar.service.StatsServiceSfr;
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
import dev.tirador.aoe2radar.ui.PerfilView;
import dev.tirador.aoe2radar.ui.WatchlistView;
import dev.tirador.aoe2radar.ui.RatingsView;
import dev.tirador.aoe2radar.ui.TechTreeView;
import dev.tirador.aoe2radar.ui.TemaApp;
import dev.tirador.aoe2radar.ui.ClicEnFondo;
import dev.tirador.aoe2radar.ui.VentanaGuardada;
import dev.tirador.aoe2radar.ui.MenuConfiguracion;
import dev.tirador.aoe2radar.util.Config;
import dev.tirador.aoe2radar.util.Reloj;

import static dev.tirador.aoe2radar.api.Cancelacion.hiloOperacion;
import static dev.tirador.aoe2radar.api.Cancelacion.stopOperacion;
import static dev.tirador.aoe2radar.api.Http.HTTP;
import static dev.tirador.aoe2radar.cache.Anotaciones.nombreVisible;
import static dev.tirador.aoe2radar.cache.Catalogos.CIVS_CAT;
import static dev.tirador.aoe2radar.cache.Catalogos.MAPAS_CAT;
import static dev.tirador.aoe2radar.cache.Paises.paisDe;
import static dev.tirador.aoe2radar.cache.RecsDisco.RECS_DIR;
import static dev.tirador.aoe2radar.cache.RecsDisco.destino;
import static dev.tirador.aoe2radar.cache.Vivos.enCursoReal;
import static dev.tirador.aoe2radar.service.Juego.copiarASavegame;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.ELO_AYER;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.truncarPx;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Identidad.AUTOR;
import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;
import static dev.tirador.aoe2radar.util.Identidad.VERSION;
import static dev.tirador.aoe2radar.util.Log.log;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
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

public class SpoilerFreeRecs extends JFrame implements dev.tirador.aoe2radar.ui.Navegacion, dev.tirador.aoe2radar.ui.ComponentesTema {

    // ----- Configuración -----------------------------------------------------
    static final Path   PLAYERS_FILE = Path.of("players.txt");
    static final String DONAR_URL    = "https://paypal.me/12Tirador/5EUR";
    // PER_PAGE y PAUSA_MS: movidos a app.Servicios (fase 3, tanda 4, Z6, raíz de composición); llegan aquí por
    // el import static de más arriba, con el mismo nombre y valor.

    // tarjeta, filaBarra, enlaceVerTodo y las ventanas de lista/tabla completa (ver ui.Listas); RegresionCapturas la usa por el nombre del campo.
    final Listas listas = CableadoJugador.listas(this);   // cableado en CableadoJugador (fase 3, tanda 4, B1)


    /** Diálogos de un jugador (nota, alias, cuentas vinculadas, nicks anteriores): ver ui.DialogosJugador. Lo que
     *  toca la tabla/lista de la ventana llega por su Anfitrion; la red de Steam, por RedSteam (ui no importa api). */
    final dev.tirador.aoe2radar.ui.DialogosJugador dialogos = CableadoJugador.dialogos(this);   // cableado en CableadoJugador (fase 3, tanda 4, B1)

    /** Menús de jugador para las vistas: ver ui.MenusJugadorSwing (implementación real). Lo que necesita de la
     *  ventana y no es navegación llega por Acciones. */
    final dev.tirador.aoe2radar.ui.MenusJugador menus = CableadoJugador.menus(this);   // cableado en CableadoJugador (fase 3, tanda 4, B1)

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
    final JList<Player> playersList = CableadoWatchlist.playersList(this);   // cableado en CableadoWatchlist (fase 3, tanda 4, B1)
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
    final BarraEstado barraEstado = new BarraEstado(this, CableadoCromo.barraEstadoAnfitrion(this), DONAR_URL);
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
    void perfilDesdeBoton() { CableadoJugador.perfilDesdeBoton(this); }   // logica movida a CableadoJugador (fase 3, tanda 4, B1);
    // this::perfilDesdeBoton se pasa al construir el Navegador, mas abajo en este mismo constructor

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
    private boolean esGrupoDeUsuarioWatchlist(String nombre) { return CableadoWatchlist.esGrupoDeUsuarioWatchlist(this, nombre); }   // logica movida a CableadoWatchlist (fase 3, tanda 4, B1)
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
    // Infraestructura de toda la ventana (no de una vista): ver ui.AutoScroll. El cableado (el Anfitrion que
    // hace de atras/adelante) vive en CableadoCromo.autoScroll (fase 3, tanda 4, oleada B, T4-B3).
    private final AutoScroll autoScroll = CableadoCromo.autoScroll(this);

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
    /** El cableado del socket (ver service.EnlaceVivo); aquí solo se traducen sus avisos a las vistas concretas.
     *  El Anfitrion (EnlaceVivo.Vistas) vive en CableadoCromo.enlaceVivo (fase 3, tanda 4, oleada B, T4-B3). */
    final EnlaceVivo enlaceVivo = CableadoCromo.enlaceVivo(this);
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

    // watchlistEnlacePartidas(): movido a CableadoWatchlist (fase 3, tanda 4, B1); solo lo llamaba CableadoWatchlist.construir.

    // barraEstadoAnfitrion(): movido a CableadoCromo.barraEstadoAnfitrion (fase 3, tanda 4, oleada B, T4-B3);
    // el campo barraEstado (más arriba) sigue llamándolo en el mismo punto, ahora como CableadoCromo.barraEstadoAnfitrion(this).

    // watchlistAnfitrion(): movido a CableadoWatchlist (fase 3, tanda 4, B1); solo lo llamaba CableadoWatchlist.construir.

    /** El cromo de navegación (pestañas, historial, esqueleto de los abrir*): ver ui.Navegador/ui.AppState.
     *  La ventana implementa ui.Navegacion delegando en él (fase 3, tanda 4, T4-Z1). Se crea al principio del
     *  constructor porque construirBarraSuperior necesita sus botones; recibe partidas directamente (es un
     *  inicializador de campo, ya construido antes de este punto, como status); conectarVistas lo completa con
     *  el resto de vistas y el split, que no existen hasta construirCentro/montarVentana (mismo truco de
     *  referencia adelantada que ya usa el resto de la ventana; ver el javadoc de ui.Navegador). */
    final dev.tirador.aoe2radar.ui.Navegador navegador;

    public SpoilerFreeRecs(String temaInicial) {   // visible para app.Main (main() construye la ventana desde fuera del paquete)
        super(NOMBRE + " " + VERSION + t(" — tu radar del AoE2 competitivo, sin spoilers · por ", " — your competitive AoE2 radar, spoiler-free · by ") + AUTOR);
        CableadoCromo.configurarVentana(this);
        navegador = new dev.tirador.aoe2radar.ui.Navegador(status, partidas, this::perfilDesdeBoton);

        sujetosPanel = new JPanel();
        watchlist = CableadoWatchlist.construir(this);   // cableado en CableadoWatchlist (fase 3, tanda 4, B1)
        JPanel left = watchlist.panel();

        JPanel top = CableadoCromo.construirBarraSuperior(this, temaInicial);

        partidas.construirTabla();

        JPanel bottom = CableadoCromo.construirBarraInferior(this);

        JPanel center = CableadoCentro.construirCentro(this, top, bottom);

        CableadoCromo.montarVentana(this, left, center);
        navegador.conectarVistas(watchlist, perfil, liveNow, techTree, ratings, civStats, directos, centroCards, splitPrincipal);

        arrancar();
    }

    // configurarVentana/construirBarraSuperior/construirBarraInferior: movidos a CableadoCromo (fase 3, tanda 4,
    // oleada B, T4-B3), llamados desde el constructor en el mismo punto y orden.


    // montarVentana (split Watchlist/resto + ClicEnFondo): movido a CableadoCromo (fase 3, tanda 4, oleada B,
    // T4-B3), llamado desde el constructor en el mismo punto.

    // Cargas iniciales (canales, jugadores, tema/fuentes) y el arranque de los
    // temporizadores: vigilante de vivos, ping del socket, comprobar actualizacion,
    // precarga del tech tree y del ladder. Es lo último que hace el constructor.
    private void arrancar() { AccionesVentana.arrancar(this); }

    /** El endpoint de Steam (api.SteamApi), aparte del companion. Campo de instancia, no static: así no importa el
     *  orden de texto frente a API_CLIENTE (vive en app.Servicios, inicializado en su primer uso: el campo
     *  {@code dialogos}, mucho más arriba) — para cuando se crea {@code steam}, Servicios ya está inicializado. */
    final SteamApi steam = new SteamApi(API_CLIENTE);

    void abrirUrl(String url) { AccionesVentana.abrirUrl(this, url); }

    /** La zona central alterna entre la tabla de recs y los directos. El cromo vive en ui.Navegador; se queda
     *  este delegado porque medio fichero llama a «mostrarDirectos» por su nombre (Watchlist, Partidas, Perfil…). */
    void mostrarDirectos(boolean mostrar) { navegador.mostrarDirectos(mostrar); }



    /** Espectar en el juego (protocolo aoe2de://), CaptureAge de acompañante y la verificación por red antes
     *  de lanzar: la parte sin Swing vive en service.Espectar (fase 3, tanda 4, Z4). */
    final Espectar espectar = new Espectar(LIVE);

    /** Lanza CaptureAge — con una rec (la reproduce con su overlay) o sin argumentos (modo acompañante: se
     *  engancha al juego al espectar). Buscar la ruta y arrancar el proceso viven en service.Espectar (fase 3,
     *  tanda 4, Z4); aquí solo queda traducir el resultado al texto de estado. */
    void lanzarCaptureAge(Path rec) { AccionesVentana.lanzarCaptureAge(this, rec); }

    /** Abre el juego espectando una partida en curso (protocolo
     *  aoe2de://1/matchId, el mismo que usa aoe2companion/aoe2recs). */
    /** Antes de lanzar el juego desde un doble clic: confirmación con «no volver a preguntar». Delegado: ver
     *  ui.ConfirmacionEspectar. Nombre conservado para las Anfitrion que lo llaman (Live now, Perfil, Partidas). */
    boolean confirmarEspectar(String quien) { return dev.tirador.aoe2radar.ui.ConfirmacionEspectar.confirmar(this, quien); }

    void espectarPartida(long matchId) { AccionesVentana.espectarPartida(this, matchId); }

    /** Verifica que la partida sigue en curso justo antes de lanzar el juego:
     *  si acaba de terminar, avisa y re-sincroniza en vez de abrir AoE2 a un
     *  «Invalid match ID». La llamada de red y sus logs viven en service.Espectar.viva (fase 3, tanda 4, Z4). */
    void espectarVerificando(long profileId, long matchId) { AccionesVentana.espectarVerificando(this, profileId, matchId); }

    void espectar(Player p) { AccionesVentana.espectar(this, p); }

    void abrirTwitch() { AccionesVentana.abrirTwitch(this); }

    void abrirDonacion() { AccionesVentana.abrirDonacion(this); }

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
    // textos, mismo Timer de 2s, mismos nombres de hilo ("log-juego", "lobby-oficial", "mi-perfil"). El
    // cableado (su Anfitrion) vive en CableadoCromo.miPartida (fase 3, tanda 4, oleada B, T4-B3).
    final MiPartidaPanel miPartida = CableadoCromo.miPartida(this);

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
