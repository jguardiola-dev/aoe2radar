package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Player;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * La cabecera «Partidas de:» sobre la watchlist: título con «×» (cerrar búsqueda), chips de los sujetos (filtro,
 * Ctrl+clic, doble clic al perfil, menú del jugador), «Filtrar ▾» con más de 10 y «★ en grupo»/«+ Añadir al
 * grupo» con uno. Sale tal cual de {@link PartidasView} (1.3): ultimosSujetos y filtroSujetos siguen en la
 * fachada (los leen la ventana y RegresionCapturas) y se escriben por {@code vista}.
 */
final class CabeceraSujetos {

    private final PartidasView vista;

    CabeceraSujetos(PartidasView vista) { this.vista = vista; }

    /** Cabecera fija sobre la watchlist: los sujetos de la búsqueda, fuera del scroll. Solo con ≤4 sujetos
     *  (más de 10, un resumen con «Filtrar ▾»). */
    public void refrescarSujetos(List<Player> tracked, boolean esInvitadoIn) {
        JPanel sujetosPanel = vista.enlaceWatchlist.sujetosPanel();
        vista.ultimosSujetos = tracked == null ? List.of() : List.copyOf(tracked);
        if (vista.todasPerfilBtn != null) vista.todasPerfilBtn.setVisible(vista.ultimosSujetos.size() == 1 && vista.ultimosSujetos.get(0).id() > 0);
        final boolean esInvitado = esInvitadoIn
                || (vista.enlaceWatchlist.invitado() != null && vista.ultimosSujetos.size() == 1 && vista.ultimosSujetos.get(0).id() == vista.enlaceWatchlist.invitado().id());
        sujetosPanel.removeAll();
        vista.filtroSujetos.retainAll(vista.ultimosSujetos.stream().map(Player::id).collect(java.util.stream.Collectors.toSet()));
        if (vista.ultimosSujetos.isEmpty()) {
            sujetosPanel.setVisible(false);
            sujetosPanel.revalidate(); sujetosPanel.repaint();
            return;
        }
        final boolean muchos = vista.ultimosSujetos.size() > 10;
        Color sep = temaOscuroActivo ? new Color(0x5a, 0x5a, 0x5a) : new Color(0xc0, 0xc0, 0xc0);
        Color gris = temaOscuroActivo ? new Color(0x9a, 0x9a, 0x9a) : new Color(0x66, 0x66, 0x66);
        log("cabecera sujetos: " + vista.ultimosSujetos.size() + " · invitado=" + esInvitado);
        JLabel tit = new JLabel(t("Partidas de:", "Games of:"));
        tit.setFont(tit.getFont().deriveFont(Font.PLAIN, 11f));
        tit.setForeground(gris);
        tit.setBorder(BorderFactory.createEmptyBorder(3, 6, 1, 0));
        JButton cerrarBusq = new JButton("\u00D7");
        cerrarBusq.setFocusable(false);
        cerrarBusq.setMargin(new Insets(0, 5, 0, 5));
        cerrarBusq.putClientProperty("JButton.buttonType", "roundRect");
        cerrarBusq.setToolTipText(t("Cerrar esta búsqueda: vacía la tabla", "Close this search: clears the table"));
        cerrarBusq.addActionListener(e -> vista.cerrarBusqueda());
        JPanel filaTit = new JPanel(new BorderLayout(6, 0));
        filaTit.setOpaque(false);
        filaTit.add(tit, BorderLayout.WEST);
        filaTit.add(cerrarBusq, BorderLayout.EAST);
        filaTit.setAlignmentX(Component.LEFT_ALIGNMENT);
        sujetosPanel.add(filaTit);
        JPanel chipsSujetos = new JPanel(new WrapLayout(java.awt.FlowLayout.LEFT, 4, 2));
        chipsSujetos.setOpaque(false);
        chipsSujetos.setAlignmentX(Component.LEFT_ALIGNMENT);
        chipsSujetos.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && !vista.filtroSujetos.isEmpty()) { vista.filtroSujetos.clear(); refrescarSujetos(vista.ultimosSujetos, esInvitadoIn); vista.applyFilters(); }
            }
        });
        if (muchos) {
            JLabel todos = new JLabel("<html><i>" + escapeHtml(vista.enlaceWatchlist.vistaActualId().split("\\|")[0]) + "</i> <font color='#8a8a8a'>(" + vista.ultimosSujetos.size() + ")</font></html>");
            todos.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            JButton filtrarBtn = new JButton(t("Filtrar \u25BE", "Filter \u25BE"));
            filtrarBtn.setFocusable(false); filtrarBtn.setMargin(new Insets(1, 6, 1, 6));
            filtrarBtn.putClientProperty("JButton.buttonType", "roundRect");
            filtrarBtn.addActionListener(e -> {
                JPopupMenu pm = new JPopupMenu();
                JMenuItem todosIt = new JMenuItem(t("Todas las partidas", "All games"));
                todosIt.addActionListener(a -> { vista.filtroSujetos.clear(); refrescarSujetos(vista.ultimosSujetos, esInvitadoIn); vista.applyFilters(); });
                pm.add(todosIt); pm.addSeparator();
                for (Player s : vista.ultimosSujetos) {
                    javax.swing.JCheckBoxMenuItem it = new javax.swing.JCheckBoxMenuItem(vista.anfitrion.nombreVisible(s.id(), s.name()), vista.filtroSujetos.contains(s.id()));
                    it.addActionListener(a -> { if (it.isSelected()) vista.filtroSujetos.add(s.id()); else vista.filtroSujetos.remove(s.id()); refrescarSujetos(vista.ultimosSujetos, esInvitadoIn); vista.applyFilters(); });
                    pm.add(it);
                }
                pm.show(filtrarBtn, 0, filtrarBtn.getHeight());
            });
            chipsSujetos.add(todos); chipsSujetos.add(filtrarBtn);
            if (!vista.filtroSujetos.isEmpty()) {
                JLabel act = new JLabel("<font color='#8a8a8a'>" + vista.filtroSujetos.size() + t(" filtrados", " filtered") + "</font>");
                act.setText("<html>" + act.getText() + "</html>");
                chipsSujetos.add(act);
            }
        }
        for (Player s : muchos ? List.<Player>of() : vista.ultimosSujetos) {
            Integer elo = vista.enlaceWatchlist.eloDe(s.id());
            JLabel l = new JLabel("<html><i>" + escapeHtml(vista.anfitrion.nombreVisible(s.id(), s.name())) + "</i>"
                    + (elo != null ? " <font color='#8a8a8a'>\u00B7 " + elo + "</font>" : "") + "</html>");
            final boolean activo = vista.filtroSujetos.contains(s.id());
            l.setOpaque(activo);
            if (activo) l.setBackground(temaOscuroActivo ? new Color(0x4a, 0x3a, 0x1e) : new Color(0xf3, 0xe3, 0xc0));
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(activo ? (temaOscuroActivo ? new Color(0xd9, 0xa5, 0x5b) : new Color(0x9a, 0x6b, 0x1f)) : new Color(0, 0, 0, 0), 1, true),
                    BorderFactory.createEmptyBorder(1, 6, 1, 6)));
            l.setToolTipText(t("Clic: ver solo sus partidas (Ctrl+clic: varios; otro clic: todas) — clic derecho: perfil, nicks, grupos\u2026",
                    "Click: only their games (Ctrl+click: several; click again: all) — right-click: profile, names, groups\u2026"));
            l.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            final long pid = s.id(); final String nom = s.name();
            l.addMouseListener(new MouseAdapter() {
                void popup(MouseEvent e) {
                    JPopupMenu pm = new JPopupMenu();
                    JMenu jm = vista.menus.deJugador(pid, nom);
                    for (Component c : jm.getMenuComponents()) pm.add(c);
                    if (esInvitado) {
                        pm.addSeparator();
                        JMenuItem quitar = new JMenuItem(t("Quitar filtro (dejar de ver sus partidas)",
                                "Remove filter (stop viewing their games)"));
                        quitar.addActionListener(a -> {
                            vista.enlaceWatchlist.limpiarInvitado();
                            refrescarSujetos(List.of(), false);
                            vista.enlaceWatchlist.aplicarFiltroGrupo();
                        });
                        pm.add(quitar);
                    }
                    pm.show(l, e.getX(), e.getY());
                }
                @Override public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) popup(e); }
                @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) popup(e); }
                @Override public void mouseClicked(MouseEvent e)  {
                    if (e.getButton() != MouseEvent.BUTTON1) return;
                    if (e.getClickCount() == 2) { vista.navegacion.abrirPerfil(pid, nom); return; }
                    if (e.getClickCount() != 1) return;
                    PartidasPresenter.alternarFiltroSujeto(vista.filtroSujetos, pid, e.isControlDown());
                    refrescarSujetos(vista.ultimosSujetos, esInvitadoIn);
                    vista.applyFilters();
                }
            });
            String grupoYa = vista.ultimosSujetos.size() == 1 ? vista.enlaceWatchlist.grupoDeJugador(pid) : null;
            if (vista.ultimosSujetos.size() == 1 && grupoYa != null && !esInvitado) {
                JLabel ya = new JLabel("\u2605 " + t("en ", "in ") + (grupoYa.isBlank() ? t("tu watchlist", "your watchlist") : grupoYa));
                ya.setFont(ya.getFont().deriveFont(Font.PLAIN, 11f));
                ya.setForeground(gris);
                JPanel filaYa = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
                filaYa.setOpaque(false);
                filaYa.add(l);
                filaYa.add(ya);
                chipsSujetos.add(filaYa);
            } else if (vista.ultimosSujetos.size() == 1 && grupoYa == null) {
                JButton addInv = new JButton(t("+ Añadir al grupo", "+ Add to group"));
                addInv.setFont(addInv.getFont().deriveFont(11f));
                addInv.setMargin(new Insets(1, 6, 1, 6));
                addInv.setFocusable(false);
                addInv.setToolTipText(t("Ficha a este jugador en uno de tus grupos", "Add this player to one of your groups"));
                addInv.addActionListener(e -> {
                    JPopupMenu pm = new JPopupMenu();
                    JMenu jm = vista.menus.deJugador(pid, nom);
                    for (Component c : jm.getMenuComponents())
                        if (c instanceof JMenu sub && sub.getText() != null && sub.getText().toLowerCase().startsWith(t("añadir", "add")))
                            for (Component cc : sub.getMenuComponents()) pm.add(cc);
                    if (pm.getComponentCount() == 0) for (Component c : jm.getMenuComponents()) pm.add(c);
                    pm.show(addInv, 0, addInv.getHeight());
                });
                JPanel fila = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
                fila.setOpaque(false);
                fila.add(l);
                fila.add(addInv);
                chipsSujetos.add(fila);
            } else chipsSujetos.add(l);
        }
        sujetosPanel.add(chipsSujetos);
        JPanel raya = new JPanel();
        raya.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 5));
        raya.setPreferredSize(new java.awt.Dimension(10, 5));
        raya.setOpaque(false);
        raya.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, sep));
        sujetosPanel.add(raya);
        sujetosPanel.setVisible(true);
        sujetosPanel.revalidate(); sujetosPanel.repaint();
    }
}
