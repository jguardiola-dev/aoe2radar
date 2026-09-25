package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.service.MiPartidaService;

import java.util.List;

import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * Lo que «Mi partida» pide en segundo plano: el tick de 2s que vigila el log del juego, el sondeo del lobby
 * oficial, y buscar/fijar «quién eres». Sale de SpoilerFreeRecs (leerLogJuego, sondearLobbyOficial,
 * preguntarMiNick, abrirMiPerfil de la 1.1) tal cual, con los mismos nombres de hilo («log-juego»,
 * «lobby-oficial», «mi-perfil») y el mismo orden: abrir el perfil, luego arrancar la vigilancia, luego
 * sincronizar el socket.
 * <p>Sin Swing: la vista (MiPartidaPanel) implementa {@link Pantalla} y pinta la superposición y los diálogos.
 */
public final class MiPartidaPresenter {

    /** Lo que el presentador necesita pintar en la vista, sin conocer Swing. */
    public interface Pantalla {
        /** Panel sobre el juego (siempre visible, se cierra solo a los ms indicados). */
        void mostrarSuperposicion(String texto, List<Object[]> fichas, int ms);
        /** Varios resultados para el nick buscado: que el usuario elija cuál es (o null si cancela). Se llama ya
         *  en el hilo de la UI (un diálogo modal, como el JOptionPane de la 1.1). */
        String[] elegirNick(List<String[]> resultados);
        /** Sin identidad todavía: que la vista pida el nick (su propio diálogo de entrada). */
        void pedirNick();
        /** Arranca (si no estaba ya) la vigilancia del log del juego. Idempotente, como logJuegoTimer != null. */
        void iniciarVigilancia();
    }

    private final MiPartidaService servicio;
    private final Tareas tareas;
    private final Navegacion navegacion;
    private final MiPartidaPanel.Anfitrion anfitrion;
    private final Pantalla pantalla;

    public MiPartidaPresenter(MiPartidaService servicio, Tareas tareas, Navegacion navegacion,
                               MiPartidaPanel.Anfitrion anfitrion, Pantalla pantalla) {
        this.servicio = servicio; this.tareas = tareas; this.navegacion = navegacion;
        this.anfitrion = anfitrion; this.pantalla = pantalla;
    }

    /** El aviso de diagnóstico al arrancar la vigilancia (carpeta de logs, mi id), antes de que el Timer haga el
     *  primer tick. Sin red: se llama tal cual, sin hilo aparte (igual que en la 1.1). */
    public void alIniciarVigilancia() { servicio.registrarInicioVigilancia(); }

    /** El país de pid, para la bandera de la ficha (lectura de caché, sin red: se llama tal cual desde el EDT,
     *  igual que paisDe(pid) en mostrarSuperposicion de la 1.1). */
    public String paisDe(long pid) { return servicio.paisDe(pid); }

    /** Las civs más jugadas por pid en los últimos 30 días (lectura de caché, sin red). */
    public List<String> civsRecientes(long pid) { return servicio.civsRecientes(pid); }

    /**
     * Un tick del Timer de 2s: lee el log en un hilo "log-juego"; si detecta la fase de preparación, aviso
     * temprano (superposición) y sondeo del lobby oficial en un hilo "lobby-oficial" (con las fichas de los
     * compañeros, si aparece).
     */
    public void tick() {
        tareas.enFondo("log-juego", () -> {
            if (!servicio.leerLogJuego()) return;
            tareas.enUi(() -> pantalla.mostrarSuperposicion(
                    "\u25CF " + t("Partida encontrada · preparando…", "Match found · getting ready…"), null, 25_000));
            tareas.enFondo("lobby-oficial", () -> {
                MiPartidaService.ResultadoLobby r = servicio.sondearLobbyOficial();
                if (r.avisar()) tareas.enUi(() -> pantalla.mostrarSuperposicion(r.texto(), r.fichas(), 60_000));
            });
        });
    }

    /** «Mi perfil»: si ya sé quién eres, abro tu perfil (Navegacion); si no, que la vista pida el nick. */
    public void abrirMiPerfil() {
        MiPartidaService.Identidad id = servicio.identidad();
        if (id != null) navegacion.abrirPerfil(id.pid(), id.nombre());
        else pantalla.pedirNick();
    }

    /**
     * Busca el nick (hilo "mi-perfil"); sin resultados, aviso en el estado; con uno, se elige solo; con varios,
     * que la vista pregunte cuál es. Elegido: se fija la identidad, se abre el perfil, se arranca la vigilancia
     * y se sincroniza el socket (mismo orden que preguntarMiNick de la 1.1).
     */
    public void buscarMiNick(String q) {
        if (q == null || q.trim().isEmpty()) return;
        String query = q.trim();
        tareas.enFondo("mi-perfil", () -> {
            List<String[]> res = anfitrion.buscarPerfiles(query);
            tareas.enUi(() -> {
                if (res.isEmpty()) { anfitrion.mostrarEstado(t("No encontré ese nick.", "Couldn't find that nick.")); return; }
                String[] elegido = res.size() == 1 ? res.get(0) : pantalla.elegirNick(res);
                if (elegido == null) return;
                long pid = Long.parseLong(elegido[0]);
                String nombre = elegido[1];
                servicio.fijarIdentidad(pid, nombre);
                navegacion.abrirPerfil(pid, nombre);
                pantalla.iniciarVigilancia();
                anfitrion.sincronizarSocket();
            });
        });
    }
}
