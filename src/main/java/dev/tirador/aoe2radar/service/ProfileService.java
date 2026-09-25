package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.FichaPerfil;

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
}
