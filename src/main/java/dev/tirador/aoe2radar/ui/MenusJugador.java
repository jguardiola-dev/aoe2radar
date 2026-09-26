package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.MatchPlayer;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.MouseEvent;

/**
 * Los menús de jugador que comparten varias vistas (Live now, Perfil, la watchlist, la tabla de partidas): «Ver
 * perfil», «En partida ahora» con aliados y rivales, «Ver perfil en el navegador»… Las vistas los piden por esta
 * interfaz y no saben quién los construye. Hoy los construye la ventana (SpoilerFreeRecs) con sus métodos de siempre;
 * cuando salga la watchlist (tanda 3) la implementación pasará a una clase de ui y las vistas no cambiarán.
 * Todo en el EDT.
 */
public interface MenusJugador {
    /** Menú emergente del jugador (ver perfil, perfil en pestaña nueva y, si juega, «En partida ahora») en el ratón. */
    void menuContextual(long pid, String nombre, MouseEvent e);

    /** Submenú «En partida ahora» de pid, o null si no está en una partida en curso. */
    JMenu enPartida(long pid);

    /** Submenú completo del jugador (pestaña nueva, en partida, alias, nota, perfil…) con su nombre como título. */
    JMenu deJugador(long pid, String nombre);

    /** Submenú «Ver perfil en el navegador» (aoe2companion, aoe2insights). */
    JMenu perfilNavegador(long pid);

    /** Ítem de un jugador de una partida (bandera, ELO 1v1, civ); clic = su perfil. Pide el ELO que falte en segundo plano. */
    JMenuItem itemJugadorPartida(MatchPlayer p);

    /** El ELO 1v1 que ya se conoce de pid sin ir a la red, o null. */
    Integer elo1v1Conocido(long pid);
}
