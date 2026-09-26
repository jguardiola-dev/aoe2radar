package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Perfil;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * El perfil de un jugador: de dónde sale cada dato y cuándo se va a la red. Crece por pasos en la fase 2 (A: ficha;
 * B: vinculadas; C: año de sfr-data; D: historial y «Actualizar hoy»).
 * <p>Hilos: los métodos que pueden ir a la red bloquean y no se llaman en el EDT (si se hace, queda aviso en el log,
 * ver util.Hilos). Los que dicen «sin red» se pueden llamar desde cualquier hilo.
 */
public interface ProfileService {

    /**
     * Historial por API (50 partidas por página): páginas como máximo, las del primer vistazo, las de «Cargar más» y el
     * mínimo. Por API, lo mínimo: 100 partidas al abrir; el resto solo si lo pides.
     */
    int ACT_MAX_PAGINAS = 20, ACT_PAGINAS_RAPIDAS = 2, ACT_MAS_PAGINAS = 4, ACT_MIN = 3;

    /**
     * La ficha del companion (/profiles/{pid}): si hay una de menos de 30 min (Caducidad.PERFIL) la devuelve sin red;
     * si no, la pide y la guarda. null si la llamada falla (queda en el log). Puede ir a la red.
     */
    FichaPerfil ficha(long pid);

    /** La última ficha conocida, aunque haya caducado; null si nunca se pidió. Sin red. */
    FichaPerfil fichaConocida(long pid);

    /**
     * ELO 1v1 (RM) ACTUAL del perfil: pide /profiles cada vez, sin caché (se quiere el de ahora; ver DEUDA, ELO_1V1).
     * null si no tiene o si falla (sin log, como la 1.1). Va a la red.
     */
    Integer elo1v1(long pid);

    /** Lo que devuelve {@link #elo1v1Leido}: el ELO 1v1 (null: el perfil no tiene) o, con fallo=true, que no se
     *  pudo saber (red, 429…). */
    record Elo1v1(Integer elo, boolean fallo) { }

    /**
     * Como elo1v1, pero distingue «no tiene» de «no se pudo saber»: lo que se recuerda para toda la sesión
     * (EloSesion) no puede ser un fallo de red (revisión 1.3, F11). Por defecto (dobles de prueba) no hay fallos:
     * null es «no tiene», como en elo1v1. Va a la red.
     */
    default Elo1v1 elo1v1Leido(long pid) { return new Elo1v1(elo1v1(pid), false); }

    /**
     * Las cuentas vinculadas según el companion (linked_profiles), SIEMPRE de la red: sin la propia ni repetidas, sin
     * nombre «null», país en mayúsculas ("" si falta). Lista vacía si falla (queda en el log). De paso apunta la familia
     * en la sesión (ver familia). Va a la red.
     */
    List<Perfil.Vinculada> vinculadas(long pid);

    /**
     * Las de la cabecera del perfil: las pide (vinculadas) y las recuerda para toda la sesión; después pide el ELO 1v1
     * (elo1v1) de cada una que aún no se sepa y también lo recuerda (0 si no tiene). Devuelve la lista. Va a la red.
     */
    List<Perfil.Vinculada> vinculadasConElo(long pid);

    /** Las vinculadas recordadas por vinculadasConElo; null si aún no se pidieron. Sin red. */
    List<Perfil.Vinculada> vinculadasConocidas(long pid);

    /** El ELO 1v1 recordado de una vinculada: null si no se preguntó, 0 si se preguntó y no tiene. Sin red. */
    Integer eloVinculada(long vid);

    /**
     * Las cuentas hermanas conocidas en la sesión por haber consultado vinculadas (de este perfil o de otro de su
     * familia): id → nombre («—» si se conoció desde la otra punta). null si ninguna. Sin red.
     * <p>El mapa devuelto se puede leer desde otro hilo mientras se escribe (es un ConcurrentHashMap): quien
     * llama no necesita copiarlo ni sincronizarlo por su cuenta (ver DEUDA fila 79, PerfilesCompanion).
     */
    Map<Long, String> familia(long pid);

    /**
     * «Nocturno primero»: el año del jugador desde el paquete de sfr-data, sin la API (ver AnioDesdeSfr). null si no
     * está en el alcance de sfr-data (entonces toca la API). nombreSiFalta: el nombre si el paquete no lo trae. Puede ir
     * a la red (sfr-data); un fallo sale como excepción y quien llama decide (lo anota y sigue con la API).
     */
    AnioSfr anioSfr(long pid, String nombreSiFalta) throws Exception;

    /** La actividad que se sabe: de memoria o, si no está, del disco (y la sube a memoria). null si ninguna. Sin red. */
    Actividad actividad(long pid);

    /**
     * El historial por la API, página a página (ver HistorialPerfil.descargar): base null, desde la página 1; base con
     * partidas y mas false, solo lo nuevo hasta una partida conocida; mas true, sigue tras la última página de base.
     * parcial (no null: a -> { } si no hace falta) recibe el estado tras cada página, en el hilo de la descarga;
     * cancelar se mira antes de cada llamada.
     * El resultado queda en memoria y en disco. Va a la red, con pausa entre páginas.
     */
    Actividad historial(long pid, String nombre, Actividad base, boolean mas, int maxPaginas,
                        Consumer<Actividad> parcial, BooleanSupplier cancelar) throws IOException, InterruptedException;

    /**
     * «Actualizar hoy» (sin la ficha, que se pide con ficha): las 50 partidas más recientes (una llamada) y las nuevas
     * terminadas, fundidas con la actividad en memoria (ver HistorialPerfil.traerHoy). Devuelve cuántas nuevas hubo.
     * Va a la red.
     */
    int traerHoy(long pid) throws IOException, InterruptedException;

    /**
     * Olvida la ficha guardada de pid (la de la sesión y, si la fuente guarda respuestas, la de la caché por URL), para
     * que la próxima ficha(pid) salga de la red: «Actualizar hoy» quiere la cabecera de ahora. fichaConocida sigue
     * dando la de antes. Sin red. Por defecto (dobles de prueba), nada.
     */
    default void olvidarFicha(long pid) { }
}
