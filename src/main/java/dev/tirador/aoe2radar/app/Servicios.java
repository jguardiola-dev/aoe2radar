package dev.tirador.aoe2radar.app;

import dev.tirador.aoe2radar.SpoilerFreeRecs;
import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.service.AnioDesdeSfr;
import dev.tirador.aoe2radar.service.AnotacionesService;
import dev.tirador.aoe2radar.service.BusquedaPerfiles;
import dev.tirador.aoe2radar.service.BusquedaPerfilesCompanion;
import dev.tirador.aoe2radar.service.ControlService;
import dev.tirador.aoe2radar.service.EloSesion;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.HistorialPerfil;
import dev.tirador.aoe2radar.service.LiveService;
import dev.tirador.aoe2radar.service.NombresJuego;
import dev.tirador.aoe2radar.service.PerfilesCompanion;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.service.TopLadderService;
import dev.tirador.aoe2radar.service.TwitchService;
import dev.tirador.aoe2radar.service.TwitchServiceCompanion;
import dev.tirador.aoe2radar.sfrdata.Snapshots;
import dev.tirador.aoe2radar.util.Reloj;

import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

import static dev.tirador.aoe2radar.api.Cancelacion.detieneEsteHilo;
import static dev.tirador.aoe2radar.api.Cancelacion.opEnCurso;
import static dev.tirador.aoe2radar.api.Cancelacion.stopOperacion;
import static dev.tirador.aoe2radar.api.Freno.THROTTLE;
import static dev.tirador.aoe2radar.api.Http.HTTP;
import static dev.tirador.aoe2radar.api.Http.TRANSPORTE;
import static dev.tirador.aoe2radar.api.Http.UA;
import static dev.tirador.aoe2radar.cache.Anotaciones.ALIASES;
import static dev.tirador.aoe2radar.cache.Anotaciones.NOTAS;
import static dev.tirador.aoe2radar.cache.CachePerfiles.PERFIL_CACHE;
import static dev.tirador.aoe2radar.cache.Canales.aprenderCanal;
import static dev.tirador.aoe2radar.cache.Catalogos.CIVS_CAT;
import static dev.tirador.aoe2radar.cache.Catalogos.MAPAS_CAT;
import static dev.tirador.aoe2radar.cache.HistorialDisco.ACTIVIDAD_CACHE;
import static dev.tirador.aoe2radar.cache.HistorialDisco.cargarActividad;
import static dev.tirador.aoe2radar.cache.HistorialDisco.guardarActividad;
import static dev.tirador.aoe2radar.cache.Paises.PAIS_DE;
import static dev.tirador.aoe2radar.cache.Paises.aprenderPais;
import static dev.tirador.aoe2radar.service.Aleatorio.esRankedRM;
import static dev.tirador.aoe2radar.service.NombresStats.nombreCivStats;
import static dev.tirador.aoe2radar.service.NombresStats.nombreMapaClave;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.ELO_AYER;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.NOMBRES_AYER;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * Raíz de composición de la app: aquí se crean, UNA sola vez y en un orden fijo, todos los servicios que
 * hablan con el companion (fase 3, tanda 4, Z6). Es la misma pieza que en la 1.1 era el bloque {@code static
 * final} al final de SpoilerFreeRecs.java: se mueve tal cual, conservando el orden de texto exacto, porque
 * varios campos se construyen a partir de otros ya declarados (comentado en cada uno). SpoilerFreeRecs sigue
 * llamando a estos nombres mediante {@code import static ...Servicios.*}, así que ninguna llamada cambia de
 * texto.
 * <p>Esta clase se inicializa en su primer uso, que es el campo {@code dialogos} de la ventana, en el EDT y
 * después de fijar IDIOMA; en la 1.1 era antes, en el hilo main. Ningún constructor de aquí lee el idioma, la
 * config ni hace E/S: un servicio nuevo que lo haga cambiaría de comportamiento según ese momento.
 */
public class Servicios {

    private Servicios() { }   // solo estáticos: nadie instancia la raíz de composición

    public static final int  PER_PAGE = 50;   // partidas consultadas por jugador
    public static final long PAUSA_MS = 300;  // cortesía entre llamadas

    /** Añade a los catálogos los mapas y civs de las partidas recibidas y
     *  persiste las novedades. */
    public static void aprenderCatalogos(Collection<Match> ms) {
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

    /** Quién está en partida ahora (ver service.EstadoVivo): un solo dueño para el socket, los barridos y la UI. */
    public static final EstadoVivo VIVO = EstadoVivo.SISTEMA;

    /** ÚNICA instancia del ELO 1v1 de sesión: la usan MenusJugadorSwing (inyectada) y submenuJugadorPartida (watchlist), aquí. */
    public static final EloSesion ELO_1V1 = new EloSesion(VIVO, Reloj.SISTEMA, EloSesion.ESPERA);   // pid → ELO 1v1 RM (sesión; caduca al terminar una partida)

    /** Poner/quitar/leer alias y notas, con la persistencia inyectada (service.AnotacionesService): un solo estado
     *  compartido con cache.Anotaciones, cuyos mapas ALIASES/NOTAS se le pasan tal cual. Los diálogos se quedan
     *  en la ventana. */
    public static final AnotacionesService ANOTACIONES = new AnotacionesService(ALIASES, NOTAS, (clave, valor) -> guardarConfig(clave, valor));

    // ----- HTTP --------------------------------------------------------------
    /** La pausa por 429 del companion, contada en la barra de estado (ApiClient avisa; la red ya no toca Swing). */
    static void avisarPausa429(long seg) {
        SpoilerFreeRecs app = null; for (Frame f : Frame.getFrames()) if (f instanceof SpoilerFreeRecs sf) app = sf;
        final SpoilerFreeRecs appF = app;
        if (appF != null) SwingUtilities.invokeLater(() -> appF.status.setText(t("La API pide calma (429): la app pausa las consultas ", "The API asks for calm (429): the app pauses its requests for ") + seg + t(" s y sigue sola.", " s and carries on by itself.")));
    }
    /** Lee control.json (multiplicadores de intervalos, interruptores, mensaje) al arrancar y cada hora. La red y las
     *  reglas de aplicación viven en service.ControlService; aquí solo queda leer/guardar config y pintar en Swing. */
    public static void cargarControl() {
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
    public static final ControlService CONTROL_SERVICE = new ControlService(TRANSPORTE_CONTROL, TRANSPORTE);
    /** Cliente único de la API: freno, 429, cancelación (api.ApiClient). Estas dos funciones quedan como fachada. */
    public static final ApiClient API_CLIENTE = new ApiClient(THROTTLE, TRANSPORTE, Servicios::avisarPausa429, () -> detieneEsteHilo());
    /** Endpoints del companion con su URL en un solo sitio (api.CompanionApi). Va DESPUÉS de API_CLIENTE: los static final se inicializan en orden de texto. */
    public static final CompanionApi COMPANION = new CompanionApi(API_CLIENTE);
    /** Las reglas del directo que necesitan la API (ver service.LiveService). */
    public static final LiveService LIVE = new LiveService(COMPANION, Reloj.SISTEMA);
    /** Los tops de la watchlist: red, decisión y disco de cargarTopLadder/cargarTopClan/vigilarTop (ver service.TopLadderService). */
    public static final TopLadderService TOP_LADDER_SERVICE = new TopLadderService(COMPANION, Reloj.SISTEMA, ms -> dormir(ms), PAUSA_MS);
    /** El perfil de un jugador (ver service.ProfileService); guarda sus fichas en PERFIL_CACHE */
    public static final ProfileService SERVICIO_PERFIL = new PerfilesCompanion(COMPANION, PERFIL_CACHE, (pid, c) -> aprenderCanal(pid, c), (pid, c) -> aprenderPais(pid, c),
            new AnioDesdeSfr(Snapshots.ELO, Snapshots.PERFILES, PAIS_DE, new NombresJuego() {
                @Override public String mapa(String clave) { return nombreMapaClave(clave); }
                @Override public String civ(String clave) { return nombreCivStats(clave); }
            }, Reloj.SISTEMA),
            new HistorialPerfil(COMPANION, ACTIVIDAD_CACHE, pid -> cargarActividad(pid), a -> guardarActividad(a), ms -> dormir(ms), PER_PAGE, PAUSA_MS, Reloj.SISTEMA),
            new EloSesion(VIVO, Reloj.SISTEMA, EloSesion.ESPERA));
    /** Búsqueda de perfiles por nick (service.BusquedaPerfiles), la usan los cinco buscadores de la interfaz. Va
     *  DESPUÉS de COMPANION: los static final se inicializan en orden de texto. */
    public static final BusquedaPerfiles BUSQUEDA = new BusquedaPerfilesCompanion(COMPANION, NOMBRES_AYER, ELO_AYER, (pid, pais) -> aprenderPais(pid, pais));
    /** El barrido de Twitch y sus miniaturas (ver service.TwitchService); usa dormir() entre las llamadas una a una. */
    public static final TwitchService TWITCH_SERVICE = new TwitchServiceCompanion(COMPANION, ms -> dormir(ms));

    public static void dormir(long ms) {
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
    public static List<String[]> buscarPerfiles(String q) { return BUSQUEDA.buscar(q); }
}
