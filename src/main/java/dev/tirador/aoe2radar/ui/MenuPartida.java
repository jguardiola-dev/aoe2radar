package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static dev.tirador.aoe2radar.service.NombresStats.posicionNombre;
import static dev.tirador.aoe2radar.service.ReglasPartida.posicionEnEquipo;
import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * El menú contextual de la tabla de Partidas y «Revelar resultado…». Sale tal cual de {@link PartidasView} (1.3);
 * lee la tabla, la selección y los colaboradores por la fachada ({@code vista}).
 */
final class MenuPartida {

    private final PartidasView vista;

    MenuPartida(PartidasView vista) { this.vista = vista; }

    /** El menú contextual de una fila (clic derecho): jugadores, espectar, enviar al juego o descargar, revelar,
     *  tech tree y análisis. Lo llama el MouseAdapter de {@link PartidasTabla} (sigue registrado en la misma
     *  tabla) después de seleccionar la fila. */
    void mostrar(MouseEvent e, Match m) {
        JPopupMenu menu = new JPopupMenu();
        if (m.gte == 0) {
            List<MatchPlayer> conId = new ArrayList<>();
            for (MatchPlayer mp : m.players) if (mp.id > 0) conId.add(mp);
            if (!conId.isEmpty()) {
                MatchPlayer titular = null; for (MatchPlayer mp : conId) if (mp.id == m.refId) titular = mp;
                if (conId.size() <= 2)
                    for (MatchPlayer mp : conId) { Integer e1 = vista.menus.elo1v1Conocido(mp.id); if (e1 == null) e1 = mp.rating; JMenu mj = vista.menus.deJugador(mp.id, mp.name + (e1 != null ? "  " + e1 : "")); mj.setIcon(iconoBandera(vista.anfitrion.paisDe(mp.id))); menu.add(mj); }
                else if (titular != null) {
                    for (MatchPlayer mp : conId) if (mp.id == titular.id) menu.add(vista.menus.deJugador(mp.id, mp.name));
                    JMenu al = new JMenu(t("Aliados", "Allies")), ri = new JMenu(t("Rivales", "Opponents"));
                    for (MatchPlayer mp : conId) { if (mp.id == titular.id) continue; String pos = posicionEnEquipo(m, mp); JMenu dst = mp.team == titular.team ? al : ri; Integer e1 = vista.menus.elo1v1Conocido(mp.id); JMenu sub = vista.menus.deJugador(mp.id, mp.name + (e1 != null ? "  " + e1 : "") + (pos == null ? "" : "  \u00B7 " + posicionNombre(pos))); sub.setIcon(iconoBandera(vista.anfitrion.paisDe(mp.id))); dst.add(sub); }
                    if (al.getItemCount() > 0) menu.add(al); if (ri.getItemCount() > 0) menu.add(ri);
                } else {
                    JMenu js = new JMenu(t("Jugadores de la partida", "Match players"));
                    for (MatchPlayer mp : conId) js.add(vista.menus.deJugador(mp.id, mp.name));
                    menu.add(js);
                }
                menu.addSeparator();
            }
        }
        if (vista.anfitrion.enCursoReal(m)) {
            JMenuItem esp = new JMenuItem(t("Espectar en directo", "Spectate live"));
            esp.addActionListener(a -> vista.anfitrion.espectarVerificando(m.players.isEmpty() ? 0 : m.players.get(0).id, m.id));
            menu.add(esp);
            if (dev.tirador.aoe2radar.service.Juego.rutaCaptureAge() != null) {
                JMenuItem espCa = new JMenuItem(t("Espectar con CaptureAge", "Spectate with CaptureAge"));
                espCa.addActionListener(a -> espectarConCaptureAge(m));
                menu.add(espCa);
            }
        } else {
            if (m.enDisco) {
                JMenuItem env = new JMenuItem(t("Enviar al juego", "Send to game"));
                // enviarInteligente (no enviarASavegame a secas): decisión 94/95, "si no [parece sana],
                // descarga como hoy" — con un archivo sano se comporta igual que antes (enviarASavegame).
                env.addActionListener(a -> vista.enviarInteligente(List.of(m)));
                menu.add(env);
            } else {
                JMenuItem dl = new JMenuItem(t("Descargar", "Download"));
                dl.addActionListener(a -> vista.download(List.of(m)));
                menu.add(dl);
            }
            menu.addSeparator();
            JMenuItem rev = new JMenuItem(t("Revelar resultado\u2026", "Reveal result\u2026"));
            rev.addActionListener(a -> revelarResultado());
            menu.add(rev);
            if (m.gte == 0) {
                java.util.LinkedHashSet<String> civsP = new java.util.LinkedHashSet<>();
                for (MatchPlayer mp : m.players) if (mp.civ != null && !mp.civ.isBlank()) civsP.add(mp.civ);
                if (civsP.size() <= 2) {
                    for (String cv : civsP) { JMenuItem it = new JMenuItem("Tech tree: " + cv); it.addActionListener(a -> vista.navegacion.abrirTechTree(cv)); menu.add(it); }
                } else {
                    JMenu sub = new JMenu("Tech tree");
                    for (String cv : civsP) { JMenuItem it = new JMenuItem(cv); it.addActionListener(a -> vista.navegacion.abrirTechTree(cv)); sub.add(it); }
                    menu.add(sub);
                }
            }
            if (m.gte == 0) {
                JMenuItem ana = new JMenuItem(t("Análisis de la partida (¡spoilers!)\u2026",
                        "Match analysis (spoilers!)\u2026"));
                ana.addActionListener(a -> {
                    int ok = JOptionPane.showConfirmDialog(vista.ventana,
                            t("Se abrirá el análisis completo en aoe2insights: resultado, estrategias y minimapa.\n¿Seguro?",
                              "This opens the full analysis on aoe2insights: result, strategies and minimap.\nSure?"),
                            t("Análisis con spoilers", "Analysis with spoilers"),
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (ok == JOptionPane.YES_OPTION)
                        vista.anfitrion.abrirUrl("https://www.aoe2insights.com/match/" + m.id + "/");
                });
                menu.add(ana);
            }
        }
        menu.show(vista.table, e.getX(), e.getY());
    }

    /** «Espectar con CaptureAge»: lanza CA y especta. Con «Usar CaptureAge» activado, espectar ya lo lanza él
     *  mismo (AccionesVentana.espectarPartida, tras verificar la partida): aquí no se lanza otra vez (revisión
     *  1.3: salían dos CaptureAge). Sin la casilla, este es el único lanzamiento, como siempre. */
    void espectarConCaptureAge(Match m) {
        if (!Boolean.parseBoolean(leerConfig("usar_ca", "false"))) vista.anfitrion.lanzarCaptureAge(null);
        vista.anfitrion.espectarVerificando(m.players.isEmpty() ? 0 : m.players.get(0).id, m.id);
    }

    void revelarResultado() {
        List<Match> sel = vista.selectedRows();
        if (sel.size() != 1) { vista.anfitrion.estado(t("Selecciona una sola partida para revelar.", "Select a single game to reveal.")); return; }
        Match m = sel.get(0);
        if (m.finished == null) {
            vista.anfitrion.estado(vista.anfitrion.enCursoReal(m)
                    ? t("Esa partida sigue EN DIRECTO: no hay resultado que revelar todavía.",
                        "That game is still LIVE: no result to reveal yet.")
                    : t("Esa partida quedó colgada en el servidor (crash): no tiene resultado.",
                        "That game hung on the server (crash): it has no result."));
            return;
        }
        int r = JOptionPane.showConfirmDialog(vista.ventana,
                t("Vas a ver el resultado de esta partida (ganador, ±ELO y duración).\n¿Seguro?",
                  "You are about to see this game's result (winner, ±ELO and duration).\nSure?"),
                t("Revelar resultado", "Reveal result"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        if (m.gte > 0) {
            JOptionPane.showMessageDialog(vista.ventana, PartidasTexto.textoResultado(m), t("Resultado", "Result"), JOptionPane.PLAIN_MESSAGE);
            return;
        }
        String verAn = t("Ver análisis en aoe2insights\u2026", "View analysis on aoe2insights\u2026");
        String cerrar = t("Cerrar", "Close");
        int ra = JOptionPane.showOptionDialog(vista.ventana, PartidasTexto.textoResultado(m), t("Resultado", "Result"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new Object[]{ cerrar, verAn }, cerrar);
        if (ra == 1) vista.anfitrion.abrirUrl("https://www.aoe2insights.com/match/" + m.id + "/");
    }
}
