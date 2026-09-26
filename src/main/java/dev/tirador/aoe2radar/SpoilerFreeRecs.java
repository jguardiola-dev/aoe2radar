// SpoilerFreeRecs — la ventana principal (JFrame): declara los campos que comparten las vistas
// (watchlist, partidas, techTree...) y llama al cableado en orden desde el constructor. La lógica
// vive fuera: servicios y arranque en app.Servicios/app.Main, la composición de la ventana en las
// clases Cableado*/AccionesVentana (mismo paquete, para leer estos campos sin volverlos public), y
// cada vista en ui/ apoyada en service/. Historial de desarrollo: CHANGELOG.md.
//
// Creado por Jorge «12Tirador» Guardiola · twitch.tv/12tirador · Licencia MIT. Créditos completos
// (aoe2companion, aoe2insights, Twitch, FlatLaf, aoe2techtree, CaptureAge) en el README.

package dev.tirador.aoe2radar;

import dev.tirador.aoe2radar.api.Recs;
import dev.tirador.aoe2radar.api.SteamApi;
import dev.tirador.aoe2radar.cache.CacheMemoria;
import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.RecsDisco;
import dev.tirador.aoe2radar.model.Forma;
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
import dev.tirador.aoe2radar.ui.MenuConfiguracion;
import dev.tirador.aoe2radar.util.Config;
import dev.tirador.aoe2radar.util.Reloj;

import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Identidad.AUTOR;
import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;
import static dev.tirador.aoe2radar.util.Identidad.VERSION;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
// Azar/Guess the ELO: la lógica vive en service.AzarService/AzarServiceCompanion.
import dev.tirador.aoe2radar.service.AzarService;
import dev.tirador.aoe2radar.service.AzarServiceCompanion;
// Barra de estado y semáforo de operación en curso: ver ui.BarraEstado.
import dev.tirador.aoe2radar.ui.BarraEstado;
// Raíz de composición: app.Servicios crea COMPANION/API_CLIENTE/... y PAUSA_MS/PER_PAGE, VIVO,
// ELO_1V1, ANOTACIONES, dormir, cargarControl, aprenderCatalogos y buscarPerfiles. Import static
// para que ninguna llamada existente cambie de texto; import normal para Servicios::dormir...
import dev.tirador.aoe2radar.app.Servicios;
import static dev.tirador.aoe2radar.app.Servicios.*;
// Socket de vivos y espectar: ver service.EnlaceVivo/service.Espectar/ui.ConfirmacionEspectar.
import dev.tirador.aoe2radar.service.EnlaceVivo;
import dev.tirador.aoe2radar.service.Espectar;
import dev.tirador.aoe2radar.service.ReglasPartida;

public class SpoilerFreeRecs extends JFrame implements dev.tirador.aoe2radar.ui.Navegacion, dev.tirador.aoe2radar.ui.ComponentesTema {

    static final Path   PLAYERS_FILE = Path.of("players.txt");
    static final String DONAR_URL    = "https://paypal.me/12Tirador/5EUR";
    // PER_PAGE/PAUSA_MS: en app.Servicios, llegan aquí por el import static de arriba.

    final Listas listas = CableadoJugador.listas(this);   // tarjeta/filaBarra/listas completas; RegresionCapturas la usa por su nombre

    /** Diálogos de un jugador (nota, alias, cuentas vinculadas, nicks): ver ui.DialogosJugador (cableado en CableadoJugador). */
    final dev.tirador.aoe2radar.ui.DialogosJugador dialogos = CableadoJugador.dialogos(this);

    /** Menús contextuales de jugador: ver ui.MenusJugadorSwing (cableado en CableadoJugador). */
    final dev.tirador.aoe2radar.ui.MenusJugador menus = CableadoJugador.menus(this);

    // «Al azar por ELO»/«Guess the ELO»: muestreo y caché de sesión en el servicio; techTree.claveCivDeNombre y dormir() como colaboradores.
    final AzarService azarService = new AzarServiceCompanion(COMPANION, civ -> SpoilerFreeRecs.this.techTree.claveCivDeNombre(civ),
            Snapshots::muestraAyer, ms -> dormir(ms), PER_PAGE, PAUSA_MS);

    final List<Player> todosJugadores = new ArrayList<>();          // fuente de verdad (todos los grupos)
    final DefaultListModel<Player> playersModel = new DefaultListModel<>();
    final JList<Player> playersList = CableadoWatchlist.playersList(this);
    // ELO actual por seguido: se lee también fuera del EDT (formaService.porResta desde el doInBackground de
    // WatchlistView.cargarForma, y Campanas), de ahí el mapa concurrente (fila 96 de DEUDA).
    final Map<Long, Integer> eloWatch  = new java.util.concurrent.ConcurrentHashMap<>();
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

    /** Barra de estado, semáforo de operación en curso y toast: ver ui.BarraEstado. */
    final BarraEstado barraEstado = new BarraEstado(this, CableadoCromo.barraEstadoAnfitrion(this), DONAR_URL);
    // status/progreso/cafeBtn: alias al mismo objeto de ui.BarraEstado (medio fichero los usa por su nombre).
    public final JLabel status = barraEstado.status;   // public: lo pinta app.Servicios (cargarControl) desde fuera del paquete; avisarPausa429 ya no lo toca (limpieza 1, ver Servicios.avisoPausa429)
    final JButton cafeBtn = barraEstado.cafeBtn;
    final JProgressBar progreso = barraEstado.progreso;
    final Image logo = AcercaDe.cargarLogo();
    final JCheckBoxMenuItem autoSgItem = new JCheckBoxMenuItem(t("Enviar al juego al descargar", "Send to game after download"),
            Boolean.parseBoolean(leerConfig("autosavegame", "false")));
    MenuConfiguracion menuConfiguracion;   // ver ui.MenuConfiguracion: botón "Configuración ▾" + esquina "Mi perfil"
    Player invitado;
    String vistaDelInvitado = "";
    final CacheMemoria<Long, Object[]> perfilCardCache = CacheService.SISTEMA.memoria(Caducidad.TARJETA);   // pid -> { htmlDatos, int[] spark }
    JPanel sujetosPanel;

    final Map<Long, String[]> twitchLive = new HashMap<>();   // pid -> { canal, título, viewers }; escribe ui.DirectosView, leen Live now/Perfil
    Player objetivoForzado;           // jugador concreto pedido con «Ver sus partidas» (una sola búsqueda)

    // pestana/iconoVista/sincronizarPestanas/recsBtn/actualizarControlesTabla: ver ui.Navegador.
    // Forma reciente (±ELO): lógica y estado en ui.WatchlistView; se inyecta porque se construye después de COMPANION y antes que la Watchlist.
    final FormService formaService = new FormaCompanion(COMPANION, Snapshots.ELO, Reloj.SISTEMA);
    /** La vista Ratings (card "ladder"): ver ui.RatingsView. */
    RatingsView ratings;

    /** El cromo (botones, historial, CardLayout) de Ratings vive en ui.Navegador. */
    @Override public void abrirLadder() { navegador.abrirLadder(); }

    // CIV STATS (winrate/pick rate/matchups/tendencias): datos y cálculos en sfrdata.CivStats/service.CalculoStats, tras el contrato service.StatsService.
    /** Asegura las ventanas de Civ Stats y hace sus cálculos (ver service.StatsService). */
    final StatsService stats = new StatsServiceSfr(SfrDataClient.SISTEMA);
    /** Filtros de Civ Stats (y de la banda del Tech tree, que los comparte): ver ui.FiltroStats. */
    final FiltroStats filtroStats = new FiltroStats(leerConfig("stats_modo", "rm_1v1"), leerConfig("stats_ventana", "30"), leerConfig("stats_mapa", "*"), leerConfig("stats_tramo", "*"));
    /** La pestaña Civ Stats: ver ui.CivStatsView. */
    CivStatsView civStats;
    @Override public void abrirCivStats() { navegador.abrirCivStats(); }

    // PERFIL (cabecera, actividad, calendario, cara a cara): ui.PerfilView/ui.PerfilPresenter y ui.CaraACaraDialogo/Presenter; cromo en ui.Navegador.
    /** La pestaña Perfil: ver ui.PerfilView. */
    PerfilView perfil;
    // El botón «Perfil»: el seleccionado en la watchlist o, si no hay, la página con el buscador (decide A QUIÉN, no CÓMO: se queda en la ventana).
    void perfilDesdeBoton() { CableadoJugador.perfilDesdeBoton(this); }   // lógica en CableadoJugador; this::perfilDesdeBoton se pasa al construir el Navegador, más abajo

    // matchDeMuestra/gteDesdeMuestra/azarDesdeMuestra/azarEnsenadas: en service.AzarServiceCompanion (lógica pura).
    final Map<Long, Integer> gamesWatch = new java.util.concurrent.ConcurrentHashMap<>();   // lo usa FormService, no el azar

    /** Abre el perfil de un jugador; pid 0 = página vacía con el buscador. El cromo vive en ui.Navegador. */
    @Override public void abrirPerfil(long pid, String nombre) { navegador.abrirPerfil(pid, nombre); }

    /** Abre a un jugador en una pestaña NUEVA (Ctrl+clic, «+»); la cuenta de pestañas es de ui.PerfilView. */
    @Override public void abrirPerfilEnPestana(long pid, String nombre) { navegador.abrirPerfilEnPestana(pid, nombre); }
    // LIVE NOW (top 250 y terminadas recientes): vista/presentador en ui.LiveNowView/Presenter, cromo en ui.Navegador; aquí lo compartido con la watchlist.
    /** La pestaña Live now: ver ui.LiveNowView. */
    LiveNowView liveNow;
    @Override public void abrirAhora() { navegador.abrirAhora(); }

    // Campanas (aviso al entrar en partida alguien de una vista marcada): red/config/deduplicación en service.Campanas, aquí solo Swing y estado compartido.
    // "watchlist" (más abajo) aún no existe al construir este campo: referencia de método, que solo se lee al llamarla (una lambda directa no compilaría).
    final Campanas campanas = new Campanas(COMPANION, Config::leerConfig, Config::guardarConfig, this::esGrupoDeUsuarioWatchlist);
    private boolean esGrupoDeUsuarioWatchlist(String nombre) { return CableadoWatchlist.esGrupoDeUsuarioWatchlist(this, nombre); }   // lógica en CableadoWatchlist
    javax.swing.Timer campanasTimer;   // barrido de campanas (Watchlist); el toast que dispara vive en ui.BarraEstado
    void mostrarToast(String texto, long matchId) { barraEstado.mostrarToast(texto, matchId); }   // el aviso flotante «X ha empezado una partida»
    void ocultarToast() { barraEstado.ocultarToast(); }
    // Banderas/icono de civ/icono de mapa: ver ui.Iconos (cachés y escalado) y service.ImagenesJuego.
    boolean ultimoClicCtrl;

    // Destino/historial/navegación: en ui.AppState + ui.Navegador (estado observable, docs/ARQUITECTURA.md). Destino sigue aquí como compat de una línea:
    // PerfilView.Anfitrion (en CableadoCentro.construirCentro) construye "new Destino(...)" y llama a este registrarDestino tal cual.
    record Destino(String vista, long pid, String nombre, String civ) { }

    void registrarDestino(Destino d) { navegador.estado.registrarDestino(new dev.tirador.aoe2radar.ui.AppState.Destino(d.vista(), d.pid(), d.nombre(), d.civ())); }

    // volverAtras/irAdelante: se quedan porque RegresionCapturas los usa como referencia de método (app::volverAtras).
    void volverAtras() { navegador.volverAtras(); }
    void irAdelante() { navegador.irAdelante(); }

    // Desplazamiento con la rueda pulsada (como Chrome) y vuelta arriba al cambiar de vista: ver ui.AutoScroll; el Anfitrion vive en CableadoCromo.autoScroll.
    final AutoScroll autoScroll = CableadoCromo.autoScroll(this);   // paquete, no private: AccionesVentana lo instala

    // TECH TREE (árbol, ficha, banda de winrate): ui.TechTreeView; cromo compartido en ui.Navegador; splitPrincipal se crea en montarVentana.
    TechTreeView techTree;

    /** Abre el panel (plegando la watchlist) y, si se pide, en una civ concreta. El cromo vive en ui.Navegador. */
    @Override public void abrirTechTree(String civ) { navegador.abrirTechTree(civ); }

    // Partidas en curso en tiempo real (websocket «ongoing-matches»; el barrido por lotes sigue de respaldo): protocolo/eventos en service.EnlaceVivo.
    final EnlaceVivo enlaceVivo = CableadoCromo.enlaceVivo(this);
    long ultimoResyncMs;

    JButton detenerDescBtn, continuarBtn;
    JButton actualizarBtn;      // «Nueva versión X — Descargar», solo si existe una mayor
    // notaDe/pedirAlias/pedirNota/borrarNota/mostrarVinculadas/nicksAnteriores: en ui.DialogosJugador (campo dialogos); Cableado* llama a dialogos.* directo.

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

    public static void main(String[] args) { dev.tirador.aoe2radar.app.Main.main(args); }   // arranque real en app.Main; el pom/harness llaman a este

    /** La Watchlist (panel izquierdo): ver ui.WatchlistView (construcción y Anfitrion en CableadoWatchlist). */
    final WatchlistView watchlist;

    // El cromo de navegación (pestañas, historial, esqueleto de los abrir*): ui.Navegador/ui.AppState; la ventana implementa Navegacion delegando en él.
    // Se crea al principio del constructor (necesita sus botones para construirBarraSuperior); conectarVistas lo completa con el resto y el split.
    final dev.tirador.aoe2radar.ui.Navegador navegador;

    public SpoilerFreeRecs(String temaInicial) {   // visible para app.Main (main() construye la ventana desde fuera del paquete)
        super(NOMBRE + " " + VERSION + t(" — tu radar del AoE2 competitivo, sin spoilers · por ", " — your competitive AoE2 radar, spoiler-free · by ") + AUTOR);
        CableadoCromo.configurarVentana(this);
        navegador = new dev.tirador.aoe2radar.ui.Navegador(status, partidas, this::perfilDesdeBoton);

        sujetosPanel = new JPanel();
        watchlist = CableadoWatchlist.construir(this);
        JPanel left = watchlist.panel();

        JPanel top = CableadoCromo.construirBarraSuperior(this, temaInicial);

        partidas.construirTabla();

        JPanel bottom = CableadoCromo.construirBarraInferior(this);

        JPanel center = CableadoCentro.construirCentro(this, top, bottom);

        CableadoCromo.montarVentana(this, left, center);
        navegador.conectarVistas(watchlist, perfil, liveNow, techTree, ratings, civStats, directos, centroCards, splitPrincipal);

        AccionesVentana.arrancar(this);
    }

    /** El endpoint de Steam, aparte del companion. No static: para cuando se crea, Servicios ya está inicializado. */
    final SteamApi steam = new SteamApi(API_CLIENTE);

    // La zona central alterna entre tabla de recs y directos (cromo en ui.Navegador); delegado porque medio fichero llama a «mostrarDirectos» por su nombre.
    void mostrarDirectos(boolean mostrar) { navegador.mostrarDirectos(mostrar); }

    // Espectar (protocolo aoe2de://) y CaptureAge de acompañante: la parte sin Swing vive en service.Espectar. Las acciones de "abrir algo" ya no tienen
    // delegado aquí: las clases Cableado y AccionesVentana llaman directo a AccionesVentana o a ui.ConfirmacionEspectar.
    final Espectar espectar = new Espectar(LIVE);

    void showAbout() { AcercaDe.showAbout(this, logo); }   // ver ui.AcercaDe (logo, showAbout)

    // ComponentesTema: componentes que ui.TemaApp necesita repintar (inversión de dependencias: TemaApp no conoce esta clase).
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

    // Tamaño/posición guardados: ui.VentanaGuardada (aplicar lo llama ui.VentanaPrincipalAjustes al abrir; guardar, CableadoCromo al cerrar).
    // trabajando (progreso, semáforo de operación): ui.BarraEstado (campo barraEstado, llamado directamente).

    // «Mi partida» (quién eres, aviso al encontrar partida, panel sobre el juego): ui.MiPartidaPanel + Presenter + service.MiPartidaServiceJuego; el
    // cableado (su Anfitrion) vive en CableadoCromo.miPartida. AccionesVentana/CableadoCromo llaman a miPartida.* directamente.
    final MiPartidaPanel miPartida = CableadoCromo.miPartida(this);

    // BarridoVivos: red y decisión de vigilarVivos/refrescarWatchlist/«Buscar partidas»; lo usan ui.PartidasView y ui.WatchlistView (por constructor).
    final BarridoVivos barridoVivos = new BarridoVivos(COMPANION, Reloj.SISTEMA, ReglasPartida::resumenVivo,
            Snapshots.ELO_AYER, Servicios::dormir, PAUSA_MS, PER_PAGE);

    // RecService: descarga, disco y savegame para UNA partida (ver service.RecService).
    final RecService recService = new DescargaRecs(Recs::descargarRec, RecsDisco::destino, Juego::copiarASavegame,
            Servicios::dormir, PAUSA_MS);

    // La pestaña «Partidas»: ui.PartidasView/Presenter y CableadoPartidas (el cableado). Se construye aquí, en el mismo punto de siempre: antes de
    // configurarVentana y de crear la Watchlist, para que sus botones existan cuando construirBarraSuperior los necesite.
    final dev.tirador.aoe2radar.ui.PartidasView partidas = CableadoPartidas.construir(this);

    // Persistencia/tabla (aplicarOrdenColumnas/guardarColumnas/MatchesTableModel): en ui.PartidasView. HTTP (avisarPausa429, cargarControl, la cadena
    // CONTROL_SERVICE → ... → TWITCH_SERVICE, dormir, buscarPerfiles): en app.Servicios, por el import static de arriba; ninguna llamada cambia.
}
