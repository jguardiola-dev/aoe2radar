package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;

import static dev.tirador.aoe2radar.cache.Anotaciones.nombreVisible;
import static dev.tirador.aoe2radar.util.Texto.normalizarNick;

/** Reglas del dominio sobre partidas y jugadores: coincidencia de rival, modo de ladder, posiciones, tramos y franjas. */
public final class ReglasPartida {
    private ReglasPartida() {}

    /** ¿Alguien del bando contrario al sujeto coincide con el filtro? (contiene, sin acentos; respeta alias) */
    public static boolean rivalCoincide(Match m, String filtro) {
        if (filtro.isEmpty()) return true;
        MatchPlayer yo = null;
        for (MatchPlayer p : m.players) if (p.id == m.refId) yo = p;
        for (MatchPlayer p : m.players) {
            if (p.id == m.refId) continue;
            if (yo != null && m.players.size() > 2 && p.team == yo.team) continue;   // aliado: no cuenta
            if (normalizarNick(nombreVisible(p.id, p.name)).contains(filtro) || normalizarNick(p.name).contains(filtro)) return true;
        }
        return false;
    }
}
