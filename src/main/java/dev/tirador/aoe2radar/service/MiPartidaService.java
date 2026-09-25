package dev.tirador.aoe2radar.service;

import java.util.List;

/**
 * «Mi partida»: quién eres en el juego (mi_pid/mi_nombre en config) y la vigilancia del log del juego
 * (MainLog.txt) para avisar en cuanto el propio juego encuentra partida, antes incluso de que el socket del
 * companion se entere. Nada de Swing aquí: la vista ({@code ui.MiPartidaPanel}) pinta la superposición y los
 * diálogos; este servicio solo decide, lee disco y hace la única llamada de red (la lista pública de lobbies).
 */
public interface MiPartidaService {

    /** Mi identidad guardada en config (mi_pid/mi_nombre de la 1.1). */
    record Identidad(long pid, String nombre) { }

    /** La identidad ya guardada, o null si todavía no se ha configurado «mi perfil» (mi_pid en blanco). */
    Identidad identidad();

    /** Guarda la identidad elegida (tras buscar el nick): mi_pid y mi_nombre en config. */
    void fijarIdentidad(long pid, String nombre);

    /** Aviso de diagnóstico en el log de la app al empezar a vigilar: carpeta de logs, si existe, mi id. */
    void registrarInicioVigilancia();

    /**
     * Una lectura del log del juego: lee lo nuevo desde la última posición (o los últimos 4000 bytes, si es una
     * sesión nueva) y dice si ESTA vez toca avisar de la fase de preparación (frases «PlayerReadyRequest» o
     * «MS_Setup» en el trozo leído, y más de 120s desde el último aviso de esta sesión: el mismo throttle que
     * logJuegoUltimoAvisoMs en la 1.1). Mantiene la posición y el throttle como estado interno.
     */
    boolean leerLogJuego();

    /** El resultado de sondear la lista pública de lobbies oficiales del companion. */
    record ResultadoLobby(boolean avisar, String texto, List<Object[]> fichas) { }

    /**
     * Busca mi lobby en la lista pública de lobbies oficiales (va a la red); si aparece con compañeros, el texto
     * y las fichas (pid, nombre, ELO 1v1 conocido o null) listos para la superposición.
     */
    ResultadoLobby sondearLobbyOficial();

    /** El país de pid (cache.Paises), para la bandera de la ficha de la superposición. */
    String paisDe(long pid);

    /** Las civs más jugadas por pid en los últimos 30 días (hasta 3, de más a menos), según lo que ya se sabe en
     *  caché (ACTIVIDAD_CACHE); vacío si no se sabe nada de pid todavía. */
    List<String> civsRecientes(long pid);
}
