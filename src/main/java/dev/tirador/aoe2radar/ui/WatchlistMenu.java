package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.EstadoVivo;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * El menú contextual de la Watchlist y de los tops (clic derecho sobre un jugador): en partida · perfil ·
 * watchlist · edición. Sale de WatchlistView tal cual en la 1.3; las altas, bajas y movimientos los sigue
 * haciendo la fachada (quitarDeWatchlist, ficharDesdeTop, moverJugador...). Hilos: todo en el EDT salvo
 * el hilo "elo-1v1" del submenú de cada jugador de la partida, que vuelve al EDT con invokeLater.
 */
final class WatchlistMenu {

    private final WatchlistView wv;

    WatchlistMenu(WatchlistView wv) { this.wv = wv; }

    /** Menú contextual de la watchlist y los tops, en cuatro bloques: en partida · perfil · watchlist · edición. */
    // visible para RegresionCapturas (abre el menú contextual para fotografiarlo)
    public void menuContextualWatchlist(Player p, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        long pid = p.id(); String nombre = wv.anfitrion.nombreVisible(pid, p.name());
        // ---- 1. en partida ahora
        if (WatchlistView.VIVO.jugando(pid)) {
            Match m = WatchlistView.VIVO.partida(pid);
            JMenuItem cab = new JMenuItem(t("En partida ahora", "In a game now") + (m != null && m.map != null ? " · " + m.map : "") + (m != null && m.started != null ? " · " + dev.tirador.aoe2radar.util.Formato.reloj(Duration.between(m.started, Instant.now())) : ""));
            cab.setEnabled(false); cab.setFont(cab.getFont().deriveFont(Font.BOLD));
            menu.add(cab);
            JMenuItem esp = new JMenuItem(t("Espectar en directo", "Spectate live"));
            esp.addActionListener(a -> wv.anfitrion.espectar(p));
            menu.add(esp);
            if (wv.anfitrion.rutaCaptureAge() != null) {
                JMenuItem espCa = new JMenuItem(t("Espectar con CaptureAge", "Spectate with CaptureAge"));
                espCa.addActionListener(a -> { wv.anfitrion.lanzarCaptureAge(null); wv.anfitrion.espectar(p); });
                menu.add(espCa);
            }
            MatchPlayer yo = null; if (m != null) for (MatchPlayer mp : m.players) if (mp.id == pid) yo = mp;
            if (m != null && yo != null) {
                List<MatchPlayer> aliados = new ArrayList<>(), rivales = new ArrayList<>();
                for (MatchPlayer mp : m.players) { if (mp.id == pid) continue; if (mp.team == yo.team) aliados.add(mp); else rivales.add(mp); }
                if (m.players.size() == 2 && rivales.size() == 1) menu.add(submenuJugadorPartida(rivales.get(0), t("Rival: ", "Opponent: ")));
                else {
                    if (!aliados.isEmpty()) { JMenu al = new JMenu(t("Aliados", "Allies")); for (MatchPlayer mp : aliados) al.add(submenuJugadorPartida(mp, "")); menu.add(al); }
                    JMenu rv = new JMenu(t("Rivales", "Opponents")); for (MatchPlayer mp : rivales) rv.add(submenuJugadorPartida(mp, "")); menu.add(rv);
                }
            } else {
                EstadoVivo.Rival riv = WatchlistView.VIVO.rival(pid);
                if (riv != null) { JMenu rivalMenu = wv.menus.deJugador(riv.pid(), riv.nombre()); rivalMenu.setText(t("Rival: ", "Opponent: ") + riv.nombre()); menu.add(rivalMenu); }
            }
            menu.addSeparator();
        }
        // ---- 2. perfil
        JMenuItem perf = new JMenuItem(t("Perfil", "Profile"));
        perf.addActionListener(a -> wv.navegacion.abrirPerfil(pid, nombre));
        menu.add(perf);
        JMenuItem perfN = new JMenuItem(t("Perfil en pestaña nueva", "Profile in a new tab"));
        perfN.addActionListener(a -> wv.navegacion.abrirPerfilEnPestana(pid, nombre));
        menu.add(perfN);
        menu.add(wv.menus.perfilNavegador(pid));
        if (wv.twitchLive.containsKey(pid)) {
            JMenuItem tw = new JMenuItem(t("Ver directo en Twitch", "Watch live on Twitch"));
            tw.addActionListener(a -> wv.anfitrion.abrirUrl("https://twitch.tv/" + wv.twitchLive.get(pid)[0]));
            menu.add(tw);
        }
        menu.addSeparator();
        // ---- 3. watchlist
        if (wv.modoTop()) {
            boolean yaSeguido = wv.todosJugadores.stream().anyMatch(x -> x.id() == pid);
            if (yaSeguido) {
                JMenuItem quitarW = new JMenuItem(t("Quitar de mi watchlist", "Remove from my watchlist"));
                quitarW.addActionListener(a -> wv.quitarDeWatchlist(pid));
                menu.add(quitarW);
            } else {
                JMenu anadir = new JMenu(t("Añadir a mi watchlist", "Add to my watchlist"));
                Set<String> gsTop = wv.gruposDisponibles();
                for (String g : gsTop) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> wv.ficharDesdeTop(p, g)); anadir.add(it); }
                anadir.addSeparator();
                JMenuItem nuevoGT = new JMenuItem(t("+ Nuevo grupo\u2026", "+ New group\u2026"));
                nuevoGT.addActionListener(a -> { String g = wv.elegirGrupoDialog(p.name()); if (g != null) wv.ficharDesdeTop(p, g); });
                anadir.add(nuevoGT);
                menu.add(anadir);
                List<Player> selTop = wv.playersList.getSelectedValuesList();
                if (selTop.size() > 1 && selTop.contains(p)) {   // varios seleccionados: ficharlos todos de golpe
                    JMenu anadirVarios = new JMenu(t("Añadir los ", "Add the ") + selTop.size() + t(" seleccionados a", " selected to"));
                    for (String g : gsTop) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> wv.ficharVarios(selTop, g)); anadirVarios.add(it); }
                    anadirVarios.addSeparator();
                    JMenuItem nuevoGV = new JMenuItem(t("+ Nuevo grupo\u2026", "+ New group\u2026"));
                    nuevoGV.addActionListener(a -> { String g = wv.elegirGrupoDialog(selTop.size() + t(" jugadores", " players")); if (g != null) wv.ficharVarios(selTop, g); });
                    anadirVarios.add(nuevoGV);
                    menu.add(anadirVarios);
                }
            }
        } else {
            JMenu mover = new JMenu(t("Mover a grupo", "Move to group"));
            Set<String> gs = wv.gruposDisponibles();
            for (String g : gs) { if (g.equalsIgnoreCase(p.grupo())) continue; JMenuItem it = new JMenuItem(g); it.addActionListener(a -> wv.moverJugador(p, g)); mover.add(it); }
            JMenuItem nuevoG = new JMenuItem(t("Nuevo grupo…", "New group…"));
            nuevoG.addActionListener(a -> {
                String nombreG = JOptionPane.showInputDialog(wv.ventana, t("Nombre del grupo nuevo:", "New group name:"), t("Nuevo grupo", "New group"), JOptionPane.PLAIN_MESSAGE);
                if (nombreG != null && !nombreG.isBlank()) { String limpio = WatchlistView.limpiarGrupo(nombreG); wv.registrarGrupo(limpio); wv.moverJugador(p, limpio); }
            });
            if (mover.getItemCount() > 0) mover.addSeparator();
            mover.add(nuevoG);
            menu.add(mover);
            JMenuItem quitar = new JMenuItem(t("Quitar de la Watchlist", "Remove from Watchlist"));
            quitar.addActionListener(a -> { wv.playersModel.removeElement(p); wv.todosJugadores.removeIf(x -> x.id() == pid); wv.savePlayers(); wv.rebuildGrupos(); wv.actualizarIndicadoresVivos(); });
            menu.add(quitar);
            List<Player> selW = wv.playersList.getSelectedValuesList();
            if (selW.size() > 1 && selW.contains(p)) {   // varios seleccionados: mover o quitar de golpe
                JMenu moverVarios = new JMenu(t("Mover los ", "Move the ") + selW.size() + t(" seleccionados a", " selected to"));
                for (String g : wv.gruposDisponibles()) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> wv.moverVarios(selW, g)); moverVarios.add(it); }
                moverVarios.addSeparator();
                JMenuItem nuevoGM = new JMenuItem(t("+ Nuevo grupo\u2026", "+ New group\u2026"));
                nuevoGM.addActionListener(a -> { String g = wv.elegirGrupoDialog(selW.size() + t(" jugadores", " players")); if (g != null) wv.moverVarios(selW, g); });
                moverVarios.add(nuevoGM);
                menu.add(moverVarios);
                JMenuItem quitarVarios = new JMenuItem(t("Quitar los ", "Remove the ") + selW.size() + t(" seleccionados de la Watchlist", " selected from the Watchlist"));
                quitarVarios.addActionListener(a -> {
                    Set<Long> ids = new HashSet<>(); for (Player x : selW) ids.add(x.id());
                    wv.todosJugadores.removeIf(x -> ids.contains(x.id()));
                    wv.savePlayers(); wv.rebuildGrupos(); wv.aplicarFiltroGrupo();
                    wv.status.setText(ids.size() + t(" jugadores quitados.", " players removed."));
                });
                menu.add(quitarVarios);
            }
        }
        JMenuItem vinc = new JMenuItem(t("Cuentas vinculadas…", "Linked accounts…"));
        vinc.addActionListener(a -> wv.dialogos.mostrarVinculadas(pid, p.name()));
        menu.add(vinc);
        menu.addSeparator();
        // ---- 4. edición
        JMenuItem alias = new JMenuItem(t("Mostrar como…", "Show as…"));
        alias.addActionListener(a -> wv.dialogos.pedirAlias(pid, p.name()));
        menu.add(alias);
        JMenuItem nota = new JMenuItem(t("Nota…", "Note…"));
        nota.addActionListener(a -> wv.dialogos.pedirNota(pid, p.name()));
        menu.add(nota);
        if (wv.dialogos.notaDe(pid) != null) { JMenuItem bn = new JMenuItem(t("Borrar nota", "Delete note")); bn.addActionListener(a -> wv.dialogos.borrarNota(pid, p.name())); menu.add(bn); }
        JMenuItem nicks = new JMenuItem(t("Nicks anteriores…", "Previous names…"));
        nicks.addActionListener(a -> wv.dialogos.nicksAnteriores(pid, p.name()));
        menu.add(nicks);
        menu.show(wv.playersList, e.getX(), e.getY());
    }

    /** Un jugador de la partida en curso, como submenú: perfil, pestaña nueva, añadir a la watchlist. */
    private JMenu submenuJugadorPartida(MatchPlayer mp, String prefijo) {
        String nombre = wv.anfitrion.nombreVisible(mp.id, mp.name);
        Integer e1 = wv.menus.elo1v1Conocido(mp.id);
        JMenu sub = new JMenu(prefijo + nombre + (e1 != null ? "  1v1 " + e1 : "") + (mp.civ != null && !mp.civ.isBlank() ? "  ·  " + mp.civ : ""));
        sub.setIcon(iconoBandera(wv.anfitrion.paisDe(mp.id)));
        if (((e1 == null && wv.eloSesion.conocido(mp.id) == null) || wv.eloSesion.caducado(mp.id)) && wv.eloSesion.reservar(mp.id)) new Thread(() -> { long pedido = wv.eloSesion.ahora(); Integer e = wv.perfiles.elo1v1(mp.id); wv.eloSesion.apuntar(mp.id, e, pedido); if (e != null && e > 0) SwingUtilities.invokeLater(() -> sub.setText(prefijo + nombre + "  1v1 " + e + (mp.civ != null && !mp.civ.isBlank() ? "  ·  " + mp.civ : ""))); }, "elo-1v1").start();
        JMenuItem perf = new JMenuItem(t("Perfil", "Profile")); perf.addActionListener(a -> wv.navegacion.abrirPerfil(mp.id, nombre)); sub.add(perf);
        JMenuItem perfN = new JMenuItem(t("Perfil en pestaña nueva", "Profile in a new tab")); perfN.addActionListener(a -> wv.navegacion.abrirPerfilEnPestana(mp.id, nombre)); sub.add(perfN);
        if (!wv.containsPlayerId(mp.id)) {
            JMenu anadir = new JMenu(t("Añadir a mi watchlist", "Add to my watchlist"));
            Set<String> gs = wv.gruposDisponibles();
            for (String g : gs) { JMenuItem it = new JMenuItem(g); it.addActionListener(a -> wv.ficharDesdeTop(new Player(mp.id, mp.name, g), g)); anadir.add(it); }
            sub.add(anadir);
        }
        return sub;
    }
}
