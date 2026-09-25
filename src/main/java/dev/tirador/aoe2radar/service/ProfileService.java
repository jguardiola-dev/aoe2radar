package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Perfil;

import java.util.List;
import java.util.Map;

/**
 * El perfil de un jugador: de dónde sale cada dato y cuándo se va a la red. Crece por pasos en la fase 2 (A: ficha;
 * B: vinculadas; C: año de sfr-data; D: historial y «Actualizar hoy»).
 * <p>Hilos: los métodos que pueden ir a la red bloquean y no se llaman en el EDT (si se hace, queda aviso en el log,
 * ver util.Hilos). Los que dicen «sin red» se pueden llamar desde cualquier hilo.
 */
public interface ProfileService {

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
     */
    Map<Long, String> familia(long pid);
}
