package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.Player;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Los diálogos de la Watchlist: gestión y creación de grupos, «¿a qué grupo?», fichar a varios, cuentas
 * vinculadas tras un alta y el buscador «Añadir jugador». Sale de WatchlistView tal cual en la 1.3 (mismos
 * JOptionPane, mismos SwingWorker). vincularExistentes es código muerto de la 1.1 (sin llamadas): se mueve,
 * no se borra. Hilos: los JOptionPane en el EDT; la red, en doInBackground o en el hilo del ELO del invitado.
 */
final class WatchlistDialogos {

    private final WatchlistView wv;

    WatchlistDialogos(WatchlistView wv) { this.wv = wv; }

    /** Consulta las vinculadas de un seguido y casa las que YA sigues. */
    private void vincularExistentes(Player p) {
        wv.status.setText(t("Buscando cuentas vinculadas de ", "Looking up linked accounts of ") + p.name() + "…");
        new SwingWorker<List<Perfil.Vinculada>, Void>() {
            @Override protected List<Perfil.Vinculada> doInBackground() { return wv.perfiles.vinculadas(p.id()); }
            @Override protected void done() {
                List<Perfil.Vinculada> vinc;
                try { vinc = get(); } catch (Exception e) { vinc = List.of(); }
                Set<Long> familia = wv.familiaSvc.vincularExistentes(p.id(), vinc, wv::containsPlayerId);
                if (familia.size() < 2) {
                    wv.status.setText(t("No sigues ninguna otra cuenta vinculada de ", "You don't follow any other linked account of ")
                            + p.name() + t(". Usa «Cuentas vinculadas…» para añadirlas.", ". Use “Linked accounts…” to add them."));
                    return;
                }
                wv.marcarVinculo(familia);
                wv.savePlayers();
                wv.aplicarFiltroGrupo();
                wv.status.setText(t("Vinculadas ", "Linked ") + familia.size()
                        + t(" cuentas: ahora comparten fila en la lista.", " accounts: they now share a row in the list."));
            }
        }.execute();
    }

    /** Diálogo de gestión: crear, renombrar y borrar grupos sin tocar jugadores. */
    void gestionarGrupos() {
        DefaultListModel<String> modelo = new DefaultListModel<>();
        Runnable recargar = () -> {
            modelo.clear();
            for (String g : wv.gruposDisponibles()) modelo.addElement(g);
        };
        recargar.run();
        JList<String> lista = new JList<>(modelo);
        lista.setVisibleRowCount(8);
        JButton nuevo = new JButton(t("Nuevo…", "New…"));
        JButton renombrar = new JButton(t("Renombrar…", "Rename…"));
        JButton borrar = new JButton(t("Borrar", "Delete"));
        nuevo.addActionListener(a -> {
            String n = JOptionPane.showInputDialog(wv.ventana, t("Nombre del grupo nuevo:", "New group name:"),
                    t("Nuevo grupo", "New group"), JOptionPane.PLAIN_MESSAGE);
            if (n != null && !n.isBlank()) { wv.registrarGrupo(WatchlistPresenter.limpiarGrupo(n)); recargar.run(); }
        });
        renombrar.addActionListener(a -> {
            String sel = lista.getSelectedValue();
            if (sel == null || sel.equalsIgnoreCase(WatchlistView.GRUPO_GENERAL)) return;
            String n = JOptionPane.showInputDialog(wv.ventana, t("Nuevo nombre para «", "New name for “") + sel + t("»:", "”:"),
                    t("Renombrar grupo", "Rename group"), JOptionPane.PLAIN_MESSAGE);
            if (n != null && !n.isBlank()) { wv.renombrarGrupo(sel, WatchlistPresenter.limpiarGrupo(n)); recargar.run(); }
        });
        borrar.addActionListener(a -> {
            String sel = lista.getSelectedValue();
            if (sel == null || sel.equalsIgnoreCase(WatchlistView.GRUPO_GENERAL)) return;
            int r = JOptionPane.showConfirmDialog(wv.ventana,
                    t("Se borrará el grupo «", "Group “") + sel
                            + t("». Sus jugadores pasarán a General. ¿Continuar?",
                                "” will be deleted. Its players move to General. Continue?"),
                    t("Borrar grupo", "Delete group"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r == JOptionPane.YES_OPTION) { wv.borrarGrupo(sel); recargar.run(); }
        });
        lista.addListSelectionListener(a -> {
            String sel = lista.getSelectedValue();
            boolean editable = sel != null && !sel.equalsIgnoreCase(WatchlistView.GRUPO_GENERAL);
            renombrar.setEnabled(editable);
            borrar.setEnabled(editable);
        });
        renombrar.setEnabled(false);
        borrar.setEnabled(false);
        JPanel botones = new JPanel(new GridLayout(3, 1, 0, 6));
        botones.add(nuevo); botones.add(renombrar); botones.add(borrar);
        JPanel cont = new JPanel(new BorderLayout(8, 0));
        cont.add(new JScrollPane(lista), BorderLayout.CENTER);
        cont.add(botones, BorderLayout.EAST);
        JOptionPane.showMessageDialog(wv.ventana, cont, t("Gestionar grupos", "Manage groups"), JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Ficha a varios de golpe; pregunta UNA vez si buscar sus cuentas vinculadas y lo hace en segundo plano.
     * BUG corregido (fase 2, service.ListaSeguidos): la 1.1 mutaba todosJugadores y llamaba a marcarVinculo
     * desde doInBackground (hilo de fondo) mientras el EDT recorre esa misma lista. Ahora, en el mismo punto
     * donde la 1.1 mutaba, se aplica con SwingUtilities.invokeAndWait: la mutación ocurre en el EDT y
     * doInBackground espera a que termine antes de seguir con el siguiente jugador; así, al acabar el
     * bucle, ya está todo aplicado (done() no puede adelantarse al último hallazgo). Los textos de
     * estado siguen yendo por publish/process, como antes.
     */
    public void ficharVarios(List<Player> lista, String g) {
        List<Player> nuevos = wv.presenter.listaSeguidos.ficharVarios(wv.todosJugadores, lista, g);
        wv.savePlayers();
        wv.rebuildGrupos();
        wv.aplicarFiltroGrupo();
        wv.refrescarWatchlist();
        wv.status.setText(nuevos.size() + t(" jugadores añadidos a «", " players added to \u201C") + g + t("\u00bb.", "\u201D."));
        if (nuevos.isEmpty()) return;
        int r = JOptionPane.showConfirmDialog(wv.ventana,
                t("¿Buscar las cuentas vinculadas de los ", "Look up the linked accounts of the ") + nuevos.size()
                        + t(" jugadores y añadirlas al grupo? (una consulta por jugador, en segundo plano)", " players and add them to the group? (one lookup per player, in the background)"),
                t("Cuentas vinculadas", "Linked accounts"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        wv.anfitrion.trabajando(true);
        final long miSerial = wv.anfitrion.opSerial();
        final int[] anadidas = { 0 };
        new SwingWorker<Void, String>() {
            @Override protected Void doInBackground() {
                wv.anfitrion.marcarHiloOperacionActual();   // Detener corta la espera del freno de ESTA operación, no la de todos
                for (Player p : nuevos) {
                    if (wv.anfitrion.detenerOperacion()) break;
                    publish(t("Vinculadas de ", "Linked accounts of ") + p.name() + "\u2026");
                    try {
                        List<Perfil.Vinculada> vinc = wv.perfiles.vinculadas(p.id());
                        SwingUtilities.invokeAndWait(() -> {
                            try {
                                Set<Long> familia = new HashSet<>(); familia.add(p.id());
                                for (Perfil.Vinculada v : vinc) {
                                    long vid = v.pid();
                                    if (wv.presenter.listaSeguidos.ficharSiNuevo(wv.todosJugadores, vid, v.nombre(), g)) anadidas[0]++;
                                    familia.add(vid);
                                }
                                if (familia.size() > 1) wv.marcarVinculo(familia);
                            } catch (Exception ex) { log("vinculadas " + p.name() + ": " + causa(ex)); }
                        });
                    } catch (Exception ex) { log("vinculadas " + p.name() + ": " + causa(ex)); }
                    wv.anfitrion.dormir(wv.pausaMs / 2);
                }
                return null;
            }
            @Override protected void process(List<String> ch) { wv.status.setText(ch.get(ch.size() - 1)); }
            @Override protected void done() {
                if (miSerial != wv.anfitrion.opSerial()) return;
                wv.anfitrion.trabajando(false);
                wv.savePlayers(); wv.rebuildGrupos(); wv.aplicarFiltroGrupo(); wv.refrescarWatchlist();
                wv.status.setText(anadidas[0] + t(" cuentas vinculadas añadidas a «", " linked accounts added to \u201C") + g + t("\u00bb.", "\u201D."));
            }
        }.execute();
    }

    /** Pregunta a qué grupo fichar (preseleccionado el activo), con «Nuevo grupo…». null = cancelado. */
    public String elegirGrupoDialog(String nombreJugador) {
        Set<String> gs = wv.gruposDisponibles();
        String nuevoO = t("+ Nuevo grupo\u2026", "+ New group\u2026");
        List<String> ops = new ArrayList<>(gs);
        ops.add(nuevoO);
        JComboBox<String> cb = new JComboBox<>(ops.toArray(String[]::new));
        String pre = wv.grupoActivo();
        if (pre != null && gs.contains(pre)) cb.setSelectedItem(pre);
        JPanel pnl = new JPanel(new BorderLayout(0, 6));
        pnl.add(new JLabel(t("¿A qué grupo añadir a ", "Which group should ") + nombreJugador + (t("?", " join?"))), BorderLayout.NORTH);
        pnl.add(cb, BorderLayout.CENTER);
        int r = JOptionPane.showConfirmDialog(wv.ventana, pnl, t("Añadir al grupo", "Add to group"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return null;
        String sel = String.valueOf(cb.getSelectedItem());
        if (sel.equals(nuevoO)) {
            String nombre = JOptionPane.showInputDialog(wv.ventana, t("Nombre del nuevo grupo:", "New group name:"), "");
            if (nombre == null || nombre.trim().isEmpty()) return null;
            // fila 102 de DEUDA: mismo camino que el resto de altas de grupo (limpiarGrupo + registrarGrupo, que
            // llama a listaSeguidos.registrarGrupo y escribe con ","), en vez de repetir aquí esa lógica a mano.
            String limpio = WatchlistPresenter.limpiarGrupo(nombre);
            wv.registrarGrupo(limpio);
            return limpio;
        }
        return sel;
    }

    void crearGrupoDialog() {
        String nombre = JOptionPane.showInputDialog(wv.ventana,
                t("Nombre del grupo nuevo:", "New group name:"),
                t("Nuevo grupo", "New group"), JOptionPane.PLAIN_MESSAGE);
        if (nombre == null || nombre.isBlank()) return;
        String limpio = WatchlistPresenter.limpiarGrupo(nombre);
        wv.guardarCfg.accept("grupo_activo", limpio);
        wv.registrarGrupo(limpio);
        wv.grupoCombo.setSelectedItem(limpio);
        wv.aplicarFiltroGrupo();
        wv.actualizarIndicadoresVivos();
        wv.status.setText(t("Grupo «", "Group “") + limpio
                + t("» activo: los próximos jugadores que añadas caerán ahí.",
                    "” active: players you add next will go there."));
    }

    /** Tras fichar a alguien: si tiene cuentas vinculadas, ofrecer añadirlas
     *  todas al mismo grupo de una vez. */
    public void ofrecerVinculadasTrasAlta(long profileId, String nombre, String grupo) {
        new SwingWorker<List<Perfil.Vinculada>, Void>() {
            final Map<Long, Integer> elosV = new HashMap<>();
            @Override protected List<Perfil.Vinculada> doInBackground() {
                List<Perfil.Vinculada> vinc = wv.perfiles.vinculadas(profileId);
                for (Perfil.Vinculada v : vinc) {
                    Integer e = wv.perfiles.elo1v1(v.pid());
                    if (e != null) elosV.put(v.pid(), e);
                    wv.anfitrion.dormir(wv.pausaMs / 2);
                }
                return vinc;
            }
            @Override protected void done() {
                List<Perfil.Vinculada> vinc;
                try { vinc = get(); } catch (Exception e) { vinc = List.of(); }
                List<Perfil.Vinculada> nuevas = new ArrayList<>();
                for (Perfil.Vinculada v : vinc) if (!wv.containsPlayerId(v.pid())) nuevas.add(v);
                if (nuevas.isEmpty()) return;
                StringBuilder sb = new StringBuilder();
                for (Perfil.Vinculada v : nuevas) sb.append(sb.isEmpty() ? "" : ", ").append(v.nombre());
                int r = JOptionPane.showConfirmDialog(wv.ventana,
                        nombre + t(" tiene ", " has ") + nuevas.size()
                                + t(" cuentas vinculadas: ", " linked accounts: ") + sb
                                + t(".\n¿Añadirlas también al grupo «", ".\nAdd them to group “") + grupo
                                + t("»?", "” too?"),
                        t("Cuentas vinculadas", "Linked accounts"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (r != JOptionPane.YES_OPTION) return;
                Set<Long> familia = new HashSet<>();
                familia.add(profileId);
                for (Perfil.Vinculada v : nuevas) {
                    wv.todosJugadores.add(new Player(v.pid(), v.nombre(), grupo));
                    familia.add(v.pid());
                }
                for (Perfil.Vinculada v : vinc) if (wv.containsPlayerId(v.pid())) familia.add(v.pid());
                elosV.forEach(wv::ponerEloFresco);   // ELO de la API: fresco (F5)
                wv.marcarVinculo(familia);
                wv.savePlayers();
                wv.rebuildGrupos();
                wv.aplicarFiltroGrupo();
                wv.refrescarWatchlist();
                wv.status.setText(nuevas.size() + t(" cuentas vinculadas añadidas a «", " linked accounts added to “")
                        + grupo + t("\u00bb.", "\u201D."));
            }
        }.execute();
    }

    /** Un jugador concreto elegido de una sugerencia: en Perfil se abre directamente; en el resto, las mismas tres opciones del buscador, sin repetir la búsqueda. */
    public void jugadorElegido(long pid, String nombre) {
        if (wv.anfitrion.perfilAbierto()) { wv.navegacion.abrirPerfil(pid, nombre); return; }
        String verO = t("Ver sus partidas", "View their games"), perfO = t("Ver perfil", "View profile"), addO = t("Añadir al grupo\u2026", "Add to group\u2026"), canO = t("Cancelar", "Cancel");
        int r0 = JOptionPane.showOptionDialog(wv.ventana, nombre + "  ·  " + pid, t("Resultados", "Results"), JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[]{ perfO, verO, addO, canO }, perfO);
        if (r0 == 0) wv.navegacion.abrirPerfil(pid, nombre);
        else if (r0 == 1) {
            Player pl = new Player(pid, nombre, wv.grupoDestino());
            wv.enlacePartidas.fijarObjetivo(pl, wv.vistaActualId());
            wv.playersList.clearSelection(); wv.aplicarFiltroGrupo(); wv.enlacePartidas.mostrarDirectos(false); wv.enlacePartidas.fetchMatches();
        } else if (r0 == 2) {
            Set<String> gs = wv.gruposDisponibles();
            Object g = JOptionPane.showInputDialog(wv.ventana, t("Grupo:", "Group:"), t("Añadir a la watchlist", "Add to the watchlist"), JOptionPane.PLAIN_MESSAGE, null, gs.toArray(), wv.grupoDestino());
            if (g != null) wv.ficharDesdeTop(new Player(pid, nombre, String.valueOf(g)), String.valueOf(g));
        }
    }

    public void addPlayerDialog(boolean soloVer, String nickInicial) {
        String q = nickInicial != null && !nickInicial.isBlank() ? nickInicial
                : JOptionPane.showInputDialog(wv.ventana, t("Nick del jugador:", "Player nick:"),
                        t("Buscar jugador", "Find player"), JOptionPane.PLAIN_MESSAGE);
        if (q == null || q.isBlank()) return;
        wv.status.setText(t("Buscando \"", "Searching \"") + q.trim() + "\"…");
        new SwingWorker<List<String[]>, Void>() {
            // decisión 8 (DEUDA fila 112): mismo buscador que Ratings/Perfil/Live/Cara a cara (service.BusquedaPerfiles):
            // local primero, luego la API, sin duplicados y con el mismo formato de fila. Antes esta vista llamaba a
            // la API a mano (con su propio formato, sin t() en "partidas") y solo después completaba con lo local.
            @Override protected List<String[]> doInBackground() {
                return wv.busqueda.buscar(q.trim());
            }
            @Override protected void done() {
                try {
                    List<String[]> res = get();
                    if (res.isEmpty()) { wv.status.setText(t("Sin resultados para \"", "No results for \"") + q.trim() + "\"."); return; }
                    String[] opciones = res.stream().map(r -> r[2]).toArray(String[]::new);
                    JComboBox<String> cbSel = new JComboBox<>(opciones);
                    JPanel pnl = new JPanel(new BorderLayout(0, 6));
                    pnl.add(new JLabel(t("Elige el jugador:", "Pick the player:")), BorderLayout.NORTH);
                    pnl.add(cbSel, BorderLayout.CENTER);
                    String verO = t("Ver sus partidas", "View their games");
                    String perfO = t("Ver perfil", "View profile");
                    String addO = t("Añadir al grupo\u2026", "Add to group\u2026");
                    String canO = t("Cancelar", "Cancel");
                    boolean perfilAbierto = wv.anfitrion.perfilAbierto();
                    int r0;
                    if (perfilAbierto && res.size() == 1) r0 = 0;   // en la pestaña Perfil, el buscador de arriba abre el perfil directamente
                    else r0 = JOptionPane.showOptionDialog(wv.ventana, pnl, t("Resultados", "Results"),
                            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                            perfilAbierto ? new Object[]{ perfO, canO } : new Object[]{ perfO, verO, addO, canO }, perfO);
                    if (perfilAbierto && r0 != 0) { wv.status.setText(t("Listo.", "Ready.")); return; }
                    if (r0 != 0 && r0 != 1 && r0 != 2) { wv.status.setText(t("Listo.", "Ready.")); return; }
                    final boolean verPerfil = r0 == 0, verAhora = r0 == 1;
                    // Por ÍNDICE, no por texto (ver jugadorDeFila): dos filas locales pueden compartir texto.
                    Player p = WatchlistView.jugadorDeFila(res, cbSel.getSelectedIndex(), wv.grupoDestino());
                    if (p == null) { wv.status.setText(t("Listo.", "Ready.")); return; }
                    if (verPerfil) { wv.status.setText(t("Listo.", "Ready.")); wv.navegacion.abrirPerfil(p.id(), p.name()); return; }
                    if (verAhora) {
                        wv.enlacePartidas.fijarObjetivo(p, wv.vistaActualId());
                        wv.playersList.clearSelection();   // la selección vieja no debe filtrar al invitado
                        wv.aplicarFiltroGrupo();   // la fila flotante, visible EN EL ACTO (también en los tops)
                        new Thread(() -> {   // ELO del invitado para su fila flotante
                            Integer ei = wv.perfiles.elo1v1(p.id());
                            if (ei != null) SwingUtilities.invokeLater(() -> {
                                wv.ponerEloFresco(p.id(), ei);   // ELO de la API: fresco (F5)
                                wv.playersList.repaint();
                                wv.enlacePartidas.refrescarSujetos(wv.enlacePartidas.ultimosSujetos(), wv.enlacePartidas.invitado() != null);   // el ELO recién llegado, a la cabecera
                            });
                        }).start();
                        wv.enlacePartidas.fetchMatches();
                        return;
                    }
                    String gElegido = elegirGrupoDialog(p.name());
                    if (gElegido == null) { wv.status.setText(t("Listo.", "Ready.")); return; }
                    final Player pAdd = new Player(p.id(), p.name(), gElegido);
                    if (!wv.containsPlayerId(pAdd.id())) {
                        wv.todosJugadores.add(pAdd);
                        wv.savePlayers();
                        wv.rebuildGrupos();
                        wv.aplicarFiltroGrupo();
                        wv.refrescarWatchlist();
                    }
                    ofrecerVinculadasTrasAlta(pAdd.id(), pAdd.name(), pAdd.grupo());
                    wv.status.setText(t("Listo.", "Ready."));
                } catch (Exception ex) {
                    wv.status.setText(t("Error buscando: ", "Search error: ") + causa(ex));
                }
            }
        }.execute();
    }
}
