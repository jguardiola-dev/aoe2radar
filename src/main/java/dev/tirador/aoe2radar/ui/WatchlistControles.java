package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.PaisItem;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.ui.Componentes.colorVivoHex;
import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * La parte de arriba de la Watchlist: pistas y «?», combo de grupos con ⚙ y campana, Top N, «+ Añadir
 * jugador», el chip «Jugando» y la forma reciente, el buscador de nick con sus sugerencias, y los selectores de
 * país y de clan. Sale de construirPanel() tal cual en la 1.3: la fachada llama a construir(left) en el mismo
 * punto, así que los componentes se crean y los listeners se registran en el mismo orden que antes. Los
 * componentes que usa el resto de la vista siguen siendo campos de la fachada. Hilos: todo en el EDT salvo
 * "nick-sugerir" y "clanes", que vuelven al EDT con invokeLater (o no tocan Swing).
 */
final class WatchlistControles {

    private final WatchlistView wv;

    WatchlistControles(WatchlistView wv) { this.wv = wv; }

    /** Lo que construirPanel() montaba entre el borde con título y la lista (el norte del panel), en el
     *  mismo orden; left es el panel de la Watchlist (la fachada lo acaba de crear). */
    void construir(JPanel left) {
        wv.watchPista1 = new JLabel(t("Amigos, pros o gente del clan.", "Friends, pros or clan mates."));
        wv.watchPista2 = new JLabel(t("Selecciona para filtrar; sin selección, todos.", "Select to filter; none selected = everyone."));
        wv.watchPista3 = new JLabel("\u21A5 " + t("= cuenta vinculada con más ELO", "= linked account with higher ELO"));
        wv.watchPista1.setFont(wv.watchPista1.getFont().deriveFont(Font.PLAIN, 11f));
        wv.watchPista2.setFont(wv.watchPista2.getFont().deriveFont(Font.PLAIN, 11f));
        wv.watchPista3.setFont(wv.watchPista3.getFont().deriveFont(Font.PLAIN, 11f));
        JPanel watchPistas = new JPanel();
        watchPistas.setLayout(new BoxLayout(watchPistas, BoxLayout.Y_AXIS));
        watchPistas.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
        wv.watchPista1.setVisible(false);   // la explicación larga vive en el «?»
        wv.watchPista3.setVisible(false);
        wv.watchPista2.setText(t("Selecciona para filtrar \u00B7 \u21A5 = cuenta más fuerte", "Select to filter \u00B7 \u21A5 = stronger account"));
        JButton ayudaBtn = new JButton("?");
        ayudaBtn.setFocusable(false);
        ayudaBtn.setMargin(new Insets(0, 5, 0, 5));
        ayudaBtn.putClientProperty("JButton.buttonType", "roundRect");
        ayudaBtn.setToolTipText("<html>" + t("<b>Tu watchlist</b>: amigos, pros o gente del clan.<br>Selecciona jugadores para buscar solo sus partidas; sin selección, todo el grupo.<br>\u25CF rojo = jugando ahora (clic derecho \u2192 Espectar) \u00B7 TW = en Twitch (clic = su canal)<br>\u21A5 = cuenta vinculada con más ELO (clic = su perfil) \u00B7 \u270E = tiene nota (clic = editar)<br>Escribe un nick arriba y pulsa Enter para ver las partidas de cualquiera.<br>Clic en un jugador = seleccionarlo; clic en el hueco de la lista = quitar la selección; Ctrl+clic = varios.",
                "<b>Your watchlist</b>: friends, pros or clan mates.<br>Select players to search only their games; with none selected, the whole group.<br>Red \u25CF = playing now (right-click \u2192 Spectate) \u00B7 TW = on Twitch (click = channel)<br>\u21A5 = linked account with higher ELO (click = profile) \u00B7 \u270E = has a note (click = edit)<br>Type a nick above and press Enter to see anyone's games.<br>Click a player to select; click the empty space to deselect; Ctrl+click for several.") + "</html>");
        ayudaBtn.addActionListener(e -> {
            JToolTip tt = ayudaBtn.createToolTip();
            tt.setTipText(ayudaBtn.getToolTipText());
            JPopupMenu pm = new JPopupMenu();
            pm.add(tt);
            pm.show(ayudaBtn, 0, ayudaBtn.getHeight());
        });
        wv.resumenWatch = new JLabel();   // se crea aquí: la línea del «?» lo necesita ya
        wv.resumenWatch.setFont(wv.resumenWatch.getFont().deriveFont(Font.PLAIN, 11f));
        JPanel pistaFila = new JPanel(new BorderLayout(6, 0));
        pistaFila.setOpaque(false);
        wv.watchPista2.setVisible(false);   // su texto vive en el tooltip del «?»
        pistaFila.add(wv.resumenWatch, BorderLayout.CENTER);   // «50 jugadores · 4 en directo»
        pistaFila.add(ayudaBtn, BorderLayout.EAST);
        pistaFila.setAlignmentX(Component.LEFT_ALIGNMENT);
        watchPistas.add(pistaFila);
        JPanel grupoFila = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        grupoFila.setAlignmentX(Component.LEFT_ALIGNMENT);
        wv.grupoCombo.setToolTipText(t("Agrupa tu Watchlist (Amigos, Pros, Clan…). Se busca y se muestra el grupo activo.",
                "Group your Watchlist (Friends, Pros, Clan…). The active group is what gets searched and shown."));
        wv.grupoCombo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String s = String.valueOf(value);
                boolean esNuevo = s.equals(t("+ Nuevo grupo…", "+ New group…"));
                boolean esTodos = s.equals(t("Todos", "All"));
                boolean esGestion = s.equals(t("Gestionar grupos…", "Manage groups…"));
                if (!esNuevo && !esGestion && wv.grupoTieneVivo(esTodos ? null : s)) {
                    l.setText("<html><font color='#" + colorVivoHex() + "'>\u25CF</font> "
                            + escapeHtml(s) + "</html>");
                }
                return l;
            }
        });
        wv.grupoCombo.addActionListener(e -> wv.onGrupoElegido());
        wv.grupoCombo.setFont(wv.grupoCombo.getFont().deriveFont(Font.BOLD));
        grupoFila.add(wv.grupoCombo);
        wv.soloVivosBtn = new JToggleButton("\u25CF " + t("Jugando", "Playing"));
        wv.soloVivosBtn.setToolTipText(t(
                "Detecta partidas EN CURSO de cualquier modo (1v1, TG, lo que sea). Límites: partidas de más de 3 h se consideran colgadas, y las salas personalizadas a veces no aparecen hasta terminar.",
                "Detects games IN PROGRESS of any mode (1v1, TG, anything). Limits: games over 3 h are treated as hung, and custom lobbies sometimes only show up once finished."));
        wv.soloVivosBtn.setToolTipText(t("Muestra solo a los que están jugando ahora, dentro del grupo elegido (con «Todos», de toda la lista)",
                "Show only who is playing right now, within the selected group (with “All”, across your whole list)"));
        wv.soloVivosBtn.setFocusable(false);
        wv.soloVivosBtn.addActionListener(e -> {
            wv.playersList.setFixedCellHeight(0);
            wv.playersList.setFixedCellHeight(-1);   // invalida el caché de ALTURAS (las sublíneas de vivos)
            wv.aplicarFiltroGrupo();
        });
        String paisGuardado = leerConfig("top_pais", "es");
        List<PaisItem> ordenPais = wv.presenter.ordenarPaises(paisGuardado);   // deja paisActual = el favorito guardado
        wv.buscaPais = new JTextField(6);
        wv.buscaPais.putClientProperty("JTextField.placeholderText", t("Buscar\u2026", "Search\u2026"));
        wv.buscaPais.setToolTipText(t("Filtra países al teclear: «bul» → Bulgaria; Enter elige el primero",
                "Filters countries as you type: \u201Cbul\u201D \u2192 Bulgaria; Enter picks the first"));
        wv.paisCombo = new JComboBox<>(ordenPais.toArray(PaisItem[]::new));
        wv.paisCombo.setRenderer(new DefaultListCellRenderer() {   // con su bandera
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean foc) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, value, index, sel, foc);
                if (value instanceof PaisItem pi) { lab.setIcon(iconoBandera(pi.code())); lab.setIconTextGap(6); }
                return lab;
            }
        });
        wv.paisCombo.setSelectedIndex(0);
        wv.paisCombo.setMaximumRowCount(14);
        wv.paisCombo.setToolTipText(t("País del top a mostrar (se recuerda como predeterminado)",
                "Country whose top to show (remembered as default)"));
        wv.paisCombo.addActionListener(e -> {
            if (!wv.rearmandoPais && wv.paisCombo.getSelectedItem() instanceof PaisItem p) wv.presenter.fijarPais(p);
        });
        instalarFiltroPais();
        wv.paisCombo.setPrototypeDisplayValue(null);
        wv.paisCombo.setPreferredSize(new Dimension(150, wv.paisCombo.getPreferredSize().height));   // más compacto: el nombre se ve, no manda
        wv.buscaPais.setColumns(8);
        wv.parPais = new JPanel(new BorderLayout(6, 0));
        wv.parPais.setOpaque(false);
        wv.parPais.add(new JLabel(t("País:", "Country:")), BorderLayout.WEST);
        wv.parPais.add(wv.buscaPais, BorderLayout.CENTER);
        wv.parPais.add(wv.paisCombo, BorderLayout.EAST);
        wv.parPais.setAlignmentX(Component.LEFT_ALIGNMENT);
        wv.parPais.setVisible(false);
        JButton nuevoGrupoBtn = new JButton(t("Crear Grupo", "Create Group"));
        nuevoGrupoBtn.setToolTipText(t("Crear un grupo nuevo", "Create a new group"));
        nuevoGrupoBtn.addActionListener(e -> wv.crearGrupoDialog());
        JButton gestGruposBtn = new JButton(t("Gestionar…", "Manage…"));
        gestGruposBtn.setToolTipText(t("Renombrar o borrar grupos", "Rename or delete groups"));
        gestGruposBtn.addActionListener(e -> wv.gestionarGrupos());
        JButton gruposBtn = new JButton("\u2699");
        gruposBtn.setFocusable(false);
        gruposBtn.setMargin(new Insets(1, 7, 1, 7));
        gruposBtn.putClientProperty("JButton.buttonType", "roundRect");
        gruposBtn.setToolTipText(t("Grupos: crear, renombrar o borrar", "Groups: create, rename or delete"));
        gruposBtn.addActionListener(e -> {
            JPopupMenu pm = new JPopupMenu();
            JMenuItem crear = new JMenuItem(t("Crear grupo\u2026", "Create group\u2026"));
            crear.addActionListener(a -> wv.crearGrupoDialog());
            JMenuItem gest = new JMenuItem(t("Gestionar grupos\u2026", "Manage groups\u2026"));
            gest.addActionListener(a -> wv.gestionarGrupos());
            pm.add(crear); pm.add(gest);
            pm.show(gruposBtn, 0, gruposBtn.getHeight());
        });
        grupoFila.add(gruposBtn);
        wv.campanaBtn = new JToggleButton(wv.anfitrion.iconoVista("campana"));
        wv.campanaBtn.setFocusable(false); wv.campanaBtn.setMargin(new Insets(2, 5, 2, 5)); wv.campanaBtn.putClientProperty("JButton.buttonType", "roundRect");
        wv.campanaBtn.addActionListener(e -> alternarCampana());
        grupoFila.add(wv.campanaBtn);
        wv.topNCombo = new JComboBox<>(new String[]{ "Top 25", "Top 50", "Top 100" });
        wv.topNCombo.setToolTipText(t("Cuántos jugadores enseñan Top ladder y Top país", "How many players Top ladder and Top country show"));
        wv.topNCombo.setSelectedIndex(Math.max(0, Arrays.asList("25", "50", "100").indexOf(leerConfig("top_n", "50"))));
        wv.topNCombo.addActionListener(e -> { if (wv.rellenandoTopN) return; String n = new String[]{ "25", "50", "100" }[Math.max(0, wv.topNCombo.getSelectedIndex())]; if (n.equals(leerConfig("top_n", "50"))) return; guardarConfig("top_n", n); if (wv.modoTop() && !wv.modoClan()) { wv.topCargado = 0; wv.cargarTopLadder(true); } });
        wv.topNCombo.setVisible(false);
        grupoFila.add(wv.topNCombo);
        wv.addJugBtn = new JButton(t("+ Añadir jugador", "+ Add player"));
        wv.addJugBtn.setFocusable(false);
        wv.addJugBtn.setMargin(new Insets(1, 7, 1, 7));
        wv.addJugBtn.putClientProperty("JButton.buttonType", "roundRect");
        wv.addJugBtn.setToolTipText(t("Busca un jugador por nick y añádelo a este grupo", "Find a player by nick and add them to this group"));
        wv.addJugBtn.addActionListener(e -> wv.addPlayerDialog(false, wv.buscaNick != null ? wv.buscaNick.getText().trim() : null));
        grupoFila.add(wv.addJugBtn);
        // Fila 3: el chip «● En directo» y el resumen en gris
        wv.soloVivosBtn.putClientProperty("JButton.buttonType", "roundRect");
        wv.soloVivosBtn.setFocusable(false);
        JPanel filtroFila = new JPanel(new BorderLayout(8, 0));
        filtroFila.setOpaque(false);
        filtroFila.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        wv.formaBtn = new JButton(t("Ver forma", "Recent form"));
        wv.formaBtn.putClientProperty("JButton.buttonType", "roundRect");
        wv.formaBtn.setFocusable(false);
        wv.formaBtn.setToolTipText(WatchlistPresenter.tipVerForma());
        wv.formaBtn.addActionListener(e -> wv.cargarForma(wv.objetivoForma(), wv.presenter.ventanaForma, () -> {   // siempre consulta (y suma a lo ya consultado)
            wv.presenter.formaVisible = true; wv.actualizarTextoForma();
            if (wv.ocultarFormaBtn != null) wv.ocultarFormaBtn.setVisible(true);
            wv.refrescarCabeceraOrden(); wv.aplicarFiltroGrupo(); wv.playersList.repaint();
        }));
        wv.ocultarFormaBtn = new JButton(t("Ocultar forma", "Hide recent form"));
        wv.ocultarFormaBtn.setFocusable(false);
        wv.ocultarFormaBtn.setMargin(new Insets(1, 6, 1, 6));
        wv.ocultarFormaBtn.putClientProperty("JButton.buttonType", "roundRect");
        wv.ocultarFormaBtn.setToolTipText(WatchlistPresenter.tipOcultarForma());
        wv.ocultarFormaBtn.setVisible(false);
        wv.ocultarFormaBtn.addActionListener(e -> wv.apagarForma());
        JComboBox<String> ventanaCb = new JComboBox<>(new String[]{ t("últimas 24 h", "last 24 h"), t("últimos 7 días", "last 7 days") });
        ventanaCb.setFocusable(false);
        ventanaCb.setToolTipText(t("Ventana de la forma que consulta «Ver forma»", "Window used by \u201CRecent form\u201D"));
        ventanaCb.addActionListener(e -> {
            wv.presenter.ventanaForma = ventanaCb.getSelectedIndex() == 1 ? 24 * 7 : 24;
            if (wv.presenter.formaVisible) { wv.refrescarCabeceraOrden(); wv.aplicarFiltroGrupo(); wv.playersList.repaint(); }   // la columna cambia de ventana al instante
        });
        JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));   // envuelve a otra línea, nunca se trunca
        chips.setOpaque(false);
        chips.add(wv.soloVivosBtn); chips.add(wv.formaBtn); chips.add(ventanaCb); chips.add(wv.ocultarFormaBtn);
        filtroFila.add(chips, BorderLayout.CENTER);
        filtroFila.setAlignmentX(Component.LEFT_ALIGNMENT);
        wv.buscaNick = new JTextField();
        wv.buscaNick.putClientProperty("JTextField.placeholderText",
                t("\uD83D\uDD0D Buscar jugador por nick  (Enter = ver sus partidas)", "\uD83D\uDD0D Find a player by nick  (Enter = see their games)"));
        wv.buscaNick.setToolTipText(t("Escribe un nick y pulsa Enter: verás sus partidas sin añadirlo; desde su nombre podrás ficharlo a un grupo.",
                "Type a nick and press Enter: you'll see their games without adding them; from their name you can add them to a group."));
        JPopupMenu nickPopup = new JPopupMenu(); nickPopup.setFocusable(false);
        javax.swing.Timer nickDebounce = new javax.swing.Timer(450, ev -> {
            String q = wv.buscaNick.getText().trim();
            nickPopup.setVisible(false); nickPopup.removeAll();
            if (q.length() < 2) return;
            new Thread(() -> {
                List<String[]> res = wv.busqueda.sugerir(q);
                SwingUtilities.invokeLater(() -> {
                    if (WatchlistView.sugerenciaCaducada(q, wv.buscaNick.getText())) return;
                    nickPopup.removeAll();
                    int n = 0;
                    for (String[] r : res) {
                        JMenuItem it = new JMenuItem(r[2]);
                        String nombre = r[1];
                        long pidSug = Long.parseLong(r[0]);
                        it.addActionListener(a -> { nickPopup.setVisible(false); wv.buscaNick.setText(nombre); wv.jugadorElegido(pidSug, nombre); });   // el elegido es ESTE jugador (por id): nada de volver a buscar por nombre
                        nickPopup.add(it);
                        if (++n >= 8) break;
                    }
                    if (n > 0 && wv.buscaNick.isShowing() && wv.buscaNick.hasFocus()) nickPopup.show(wv.buscaNick, 0, wv.buscaNick.getHeight());
                });
            }, "nick-sugerir").start();
        });
        nickDebounce.setRepeats(false);
        wv.buscaNick.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { nickDebounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { nickDebounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { nickDebounce.restart(); }
        });
        wv.buscaNick.addKeyListener(new KeyAdapter() { @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ESCAPE) nickPopup.setVisible(false); } });
        wv.buscaNick.addActionListener(e -> {
            nickPopup.setVisible(false);
            String q = wv.buscaNick.getText().trim();
            if (!q.isEmpty()) { wv.addPlayerDialog(true, q); wv.buscaNick.setText(""); }
        });
        JPanel buscaFila = new JPanel(new BorderLayout());
        buscaFila.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        buscaFila.add(wv.buscaNick, BorderLayout.CENTER);
        JPanel norteWatch = new JPanel();
        wv.norteWatchRef = norteWatch;
        norteWatch.setLayout(new BoxLayout(norteWatch, BoxLayout.Y_AXIS));
        norteWatch.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
        buscaFila.setAlignmentX(Component.LEFT_ALIGNMENT);
        norteWatch.add(buscaFila);   // 1. el buscador
        norteWatch.add(grupoFila);   // 2. la vista (grupo · ⚙ · + Añadir jugador)
        norteWatch.add(wv.parPais);   //    el país, en su propia línea, solo en Top país
        wv.clanField = new JTextField(10);
        wv.clanField.putClientProperty("JTextField.placeholderText", t("Tag del clan (R1, DK, TdB…)", "Clan tag (R1, DK, TdB…)"));
        wv.clanField.setText(leerConfig("clan_tag", ""));
        wv.clanPopup = new JPopupMenu(); wv.clanPopup.setFocusable(false);
        wv.clanField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() {
                wv.clanPopup.setVisible(false); wv.clanPopup.removeAll();
                if (wv.clanField.getText().trim().length() < 1) return;
                if (wv.anfitrion.clanesVacios()) { Thread th = new Thread(wv.anfitrion::asegurarLadderEnFondo, "clanes"); th.setDaemon(true); th.start(); return; }
                for (Map.Entry<String, Integer> en : wv.anfitrion.sugerirClanes(wv.clanField.getText())) {
                    JMenuItem it = new JMenuItem(en.getKey() + "  (" + en.getValue() + ")");
                    it.addActionListener(a -> { wv.clanField.setText(en.getKey()); wv.clanPopup.setVisible(false); wv.cargarTopClan(); });
                    wv.clanPopup.add(it);
                }
                if (wv.clanPopup.getComponentCount() > 0 && wv.clanField.isShowing()) wv.clanPopup.show(wv.clanField, 0, wv.clanField.getHeight());   // oculto (p. ej. «Abrir en» un clan, antes de pasar a ★ Top clan): show() lanzaría IllegalComponentStateException
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        wv.clanField.addActionListener(e -> { wv.clanPopup.setVisible(false); wv.cargarTopClan(); });
        wv.parClan = new JPanel(new BorderLayout(6, 0));
        wv.parClan.setOpaque(false);
        wv.parClan.add(new JLabel(t("Clan:", "Clan:")), BorderLayout.WEST);
        wv.parClan.add(wv.clanField, BorderLayout.CENTER);
        wv.clanesGuardadosCombo = new JComboBox<>();
        wv.clanesGuardadosCombo.setToolTipText(t("Tus clanes guardados: elige uno para cargar su top", "Your saved clans: pick one to load its top"));
        wv.clanesGuardadosCombo.addActionListener(e -> { if (wv.rellenandoClanes) return; Object v = wv.clanesGuardadosCombo.getSelectedItem(); if (v instanceof String tag && !tag.isBlank() && !tag.startsWith("(")) { wv.clanField.setText(tag); wv.clanPopup.setVisible(false); wv.cargarTopClan(); } });
        wv.clanEstrella = new JButton(t("Guardar clan", "Save clan"));
        wv.clanEstrella.setFocusable(false); wv.clanEstrella.setMargin(new Insets(1, 8, 1, 8));
        wv.clanEstrella.addActionListener(e -> {
            String tag = wv.clanField.getText().trim();
            if (tag.isEmpty()) return;
            List<String> l = wv.presenter.clanesGuardados();
            if (l.removeIf(x -> x.equalsIgnoreCase(tag))) wv.status.setText(t("Clan quitado de guardados: ", "Clan removed from saved: ") + tag); else { l.add(tag); wv.status.setText(t("Clan guardado: ", "Clan saved: ") + tag); }
            wv.guardarCfg.accept("clanes_guardados", String.join(",", l));   // la misma config que lee wv.clanesGuardados()
            refrescarClanesGuardados();
        });
        wv.parClan.add(wv.clanEstrella, BorderLayout.EAST);
        wv.parClanGuardados = new JPanel(new BorderLayout(6, 0));
        wv.parClanGuardados.setOpaque(false);
        wv.parClanGuardados.add(new JLabel(t("Clanes guardados:", "Saved clans:")), BorderLayout.WEST);
        wv.parClanGuardados.add(wv.clanesGuardadosCombo, BorderLayout.CENTER);
        wv.parClanGuardados.setAlignmentX(Component.LEFT_ALIGNMENT);
        wv.parClanGuardados.setVisible(false);
        wv.clanField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() { refrescarClanesGuardados(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        refrescarClanesGuardados();
        wv.parClan.setAlignmentX(Component.LEFT_ALIGNMENT);
        wv.parClan.setVisible(false);
        norteWatch.add(wv.parClan);   //    el clan, en su propia línea, solo en Top clan
        norteWatch.add(wv.parClanGuardados);   //    y debajo, los guardados
        norteWatch.add(filtroFila);   // 3. chip En directo + resumen
        norteWatch.add(watchPistas);   //    una línea de pista + «?»
        left.add(norteWatch, BorderLayout.NORTH);
    }

    /** El campo filtra el combo de al lado; nadie más escribe en él, así que
     *  no puede realimentarse (la lección del cuelgue de la 4.41). */
    private void instalarFiltroPais() {
        wv.buscaPais.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void filtrar() {
                List<PaisItem> coinciden = wv.presenter.paisesQueCoinciden(wv.buscaPais.getText());
                PaisItem prev = wv.presenter.paisActual;
                wv.rearmandoPais = true;
                try {
                    wv.paisCombo.removeAllItems();
                    for (PaisItem pi : coinciden) wv.paisCombo.addItem(pi);
                    if (prev != null)
                        for (int i = 0; i < wv.paisCombo.getItemCount(); i++)
                            if (wv.paisCombo.getItemAt(i).code().equals(prev.code())) { wv.paisCombo.setSelectedIndex(i); break; }
                } finally { wv.rearmandoPais = false; }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { filtrar(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { filtrar(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        wv.buscaPais.addActionListener(e -> {   // Enter: el primero filtrado (y carga)
            if (wv.paisCombo.getItemCount() > 0) wv.paisCombo.setSelectedIndex(0);
        });
    }

    private void refrescarCampanaBtn() {
        if (wv.campanaBtn == null) return;
        boolean on = wv.campanas.campanas().contains(wv.presenter.idVistaCampana());
        wv.campanaBtn.setSelected(on);
        wv.campanaBtn.setForeground(on ? (temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00)) : UIManager.getColor("Button.foreground"));
        wv.campanaBtn.setToolTipText(on ? t("Avisos activados para esta lista: te avisa cuando alguien de aquí entre en partida (clic para apagar)", "Alerts on for this list: you get a notice when someone here starts a game (click to turn off)")
                : t("Activar para recibir un aviso cuando alguien de esta lista entre en partida", "Turn on to get a notice when someone in this list starts a game"));
    }

    private void alternarCampana() {
        String id = wv.presenter.idVistaCampana();
        boolean activo = wv.campanas.alternar(id);
        refrescarCampanaBtn();
        wv.refrescarCampanas();
        wv.status.setText(activo ? t("Avisos activados para «", "Alerts on for \u201C") + wv.presenter.nombreVistaCampana() + t("»: te avisaré cuando alguien entre en partida.", "\u201D: you'll get a notice when someone starts a game.") : t("Avisos apagados para esta lista.", "Alerts off for this list."));
    }

    private void refrescarClanesGuardados() {
        if (wv.clanesGuardadosCombo == null) return;
        wv.rellenandoClanes = true;
        try {
            List<String> l = wv.presenter.clanesGuardados();
            wv.clanesGuardadosCombo.removeAllItems();
            wv.clanesGuardadosCombo.addItem(l.isEmpty() ? t("(sin clanes guardados)", "(no saved clans)") : t("Guardados…", "Saved…"));
            for (String x : l) wv.clanesGuardadosCombo.addItem(x);
            String actual = wv.clanField == null ? "" : wv.clanField.getText().trim();
            boolean guardado = l.stream().anyMatch(x -> x.equalsIgnoreCase(actual));
            wv.clanEstrella.setText(guardado ? t("Quitar de guardados", "Remove from saved") : t("Guardar clan", "Save clan"));
            wv.clanEstrella.setEnabled(!actual.isEmpty());
            if (wv.parClanGuardados != null) wv.parClanGuardados.setVisible(wv.parClan != null && wv.parClan.isVisible() && !l.isEmpty());
        } finally { wv.rellenandoClanes = false; }
    }

    /** En los modos ★ la lista es de solo lectura; el país solo se ve en ★ país. */
    void actualizarBotonesModo() {
        boolean editable = !wv.modoTop();
        if (wv.delBtn != null) wv.delBtn.setEnabled(editable);
        if (wv.parPais != null) wv.parPais.setVisible(wv.modoPais());
        if (wv.parClan != null) wv.parClan.setVisible(wv.modoClan());
        if (wv.parClanGuardados != null) wv.parClanGuardados.setVisible(wv.modoClan() && !wv.presenter.clanesGuardados().isEmpty());
        if (wv.topNCombo != null) { wv.topNCombo.setVisible(wv.modoTop() && !wv.modoClan()); wv.rellenandoTopN = true; try { wv.topNCombo.setSelectedIndex(Math.max(0, Arrays.asList("25", "50", "100").indexOf(leerConfig("top_n", "50")))); } finally { wv.rellenandoTopN = false; } }
        refrescarCampanaBtn();
        if (wv.addJugBtn != null) wv.addJugBtn.setVisible(!wv.modoTop());   // en los tops se ficha desde la fila
        if (wv.delBtn != null) wv.delBtn.setVisible(!wv.modoTop() && wv.grupoActivo() != null);   // solo en grupos personalizados
        if (wv.buscaNick != null) wv.buscaNick.putClientProperty("JTextField.placeholderText", wv.modoTop()
                ? t("\uD83D\uDD0D Buscar jugador por nick  (Enter = ver sus partidas)", "\uD83D\uDD0D Find a player by nick  (Enter = view their games)")
                : t("\uD83D\uDD0D Buscar o añadir jugador por nick  (Enter = ver sus partidas)", "\uD83D\uDD0D Find or add a player by nick  (Enter = view their games)"));
    }
}
