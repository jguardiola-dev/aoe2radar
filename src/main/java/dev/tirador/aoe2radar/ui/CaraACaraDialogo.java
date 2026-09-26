package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static dev.tirador.aoe2radar.service.ConsultasLadder.ladderNombre;
import static dev.tirador.aoe2radar.service.NombresStats.posicionNombre;
import static dev.tirador.aoe2radar.service.ReglasPartida.DURACION_TRAMOS;
import static dev.tirador.aoe2radar.service.ReglasPartida.posicionEnEquipo;
import static dev.tirador.aoe2radar.service.ReglasPartida.tramoDuracion;
import static dev.tirador.aoe2radar.ui.Componentes.colorSecundario;
import static dev.tirador.aoe2radar.ui.Componentes.colorWr;
import static dev.tirador.aoe2radar.ui.Componentes.subirArriba;
import static dev.tirador.aoe2radar.ui.Componentes.tituloSeccion;
import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.ui.Iconos.iconoCiv;
import static dev.tirador.aoe2radar.ui.Iconos.iconoMapa;
import static dev.tirador.aoe2radar.ui.Componentes.colorHex;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.Formato.miles;
import static dev.tirador.aoe2radar.util.Formato.pct1;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * El diálogo «Cara a cara» de la pestaña Perfil: el cruce con un rival (balance, mapas, civ contra civ, últimas
 * partidas) sobre el historial ya cargado del perfil abierto, más «cada uno por su lado» (año del rival, de
 * sfr-data). Sale de SpoilerFreeRecs (h2hDialogo y sus métodos de la 1.1) tal cual; lo abre PerfilView con
 * {@link #mostrar()}. Está tan pegado a PerfilView (mismo perfil abierto, mismas listas, mismos servicios) que en
 * vez de repetir cada colaborador por su interfaz recibe la vista entera (mismo paquete: acceso de paquete, sin
 * getters públicos de más). Lee {@code view.actPid}/{@code view.actNombre} EN VIVO en cada repintado: si mientras
 * el diálogo está abierto se navega a otro perfil, el próximo repintado usa ya el nuevo, igual que en la 1.1 (no
 * había ningún cierre automático al cambiar de perfil).
 */
public final class CaraACaraDialogo {

    static final String[] H2H_MODOS = { "*", "1v1", "tg", "ew", "dm", "unranked" };

    private final Window ventana;
    private final PerfilView view;
    private final CaraACaraPresenter presenter;

    private JDialog dialogo;
    private JTextField busca;
    private JPopupMenu popup;
    private javax.swing.Timer debounce;
    private PanelScrollable cuerpo;
    private JComboBox<String> modo;
    private long pidActual;
    private String nombreActual;
    private String mapaFiltro;
    private final List<Object[]> historialNav = new ArrayList<>();   // {pid Long, nombre String, mapa String, modoIdx Integer}
    private boolean navegando;
    private JButton atrasBtn;

    public CaraACaraDialogo(Window ventana, PerfilView view) {
        this.ventana = ventana; this.view = view;
        this.presenter = new CaraACaraPresenter(view.perfiles, view.busqueda, view.tareas, view.actividadCache);
    }

    private void registrar() {
        if (navegando) return;
        Object[] est = { pidActual, nombreActual, mapaFiltro, modo == null ? 0 : modo.getSelectedIndex() };
        if (!historialNav.isEmpty()) {
            Object[] u = historialNav.get(historialNav.size() - 1);
            if (Objects.equals(u[0], est[0]) && Objects.equals(u[2], est[2]) && Objects.equals(u[3], est[3])) return;
        }
        historialNav.add(est);
        if (atrasBtn != null) atrasBtn.setEnabled(historialNav.size() > 1);
    }

    private void atras() {
        if (historialNav.size() < 2) return;
        historialNav.remove(historialNav.size() - 1);
        Object[] est = historialNav.get(historialNav.size() - 1);
        navegando = true;
        try {
            mapaFiltro = (String) est[2];
            if (modo != null && (Integer) est[3] != modo.getSelectedIndex()) modo.setSelectedIndex((Integer) est[3]);
            if ((Long) est[0] > 0) fijar((Long) est[0], (String) est[1]); else { pidActual = 0; nombreActual = null; busca.setText(""); pintarSugerenciasIniciales(); }
        } finally { navegando = false; }
        if (atrasBtn != null) atrasBtn.setEnabled(historialNav.size() > 1);
    }

    /** ¿El diálogo está mostrado y tiene el foco? (para que el botón lateral del ratón de la ventana decida si es «atrás» del diálogo o el general). */
    public boolean mostradoConFoco() { return dialogo != null && dialogo.isShowing() && dialogo.isFocused(); }

    /** ¿El diálogo está mostrado (sin exigir foco)? Lo usa PerfilView para el tope de filas de rivales/aliados (10 en vez de 5). */
    public boolean mostrado() { return dialogo != null && dialogo.isShowing(); }

    /** ¿Se ha llegado a crear el diálogo alguna vez? (para no tocar campos si mostrar() nunca se llamó). */
    public boolean creado() { return dialogo != null; }

    /** El botón «atrás» propio del diálogo (también el botón lateral del ratón, ver mostradoConFoco). */
    public void irAtras() { atras(); }

    /** Fija el cruce con este rival (lo usa el aviso «Mi partida», tras abrir el diálogo). */
    public void fijarRival(long rivalPid, String rivalNombre) { fijar(rivalPid, rivalNombre); }

    /** Abre (o reabre) el diálogo para el perfil actualmente abierto en Perfil. Sin historial cargado, avisa en el estado general y no abre nada. */
    public void mostrar() {
        long pid = view.actPid;
        if (pid <= 0 || view.actividadCache.get(pid) == null) { view.anfitrion.mostrarEstadoGlobal(t("Abre primero un perfil con historial cargado.", "Open a profile with its history loaded first.")); return; }
        if (dialogo != null) { dialogo.dispose(); dialogo = null; }
        dialogo = new JDialog(ventana, t("Cara a cara · ", "Head-to-head · ") + view.actNombre, Dialog.ModalityType.MODELESS);
        JPanel norte = new JPanel(new BorderLayout(8, 4));
        norte.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        busca = new JTextField(22);
        busca.putClientProperty("JTextField.placeholderText", t("Rival: escribe un nick (primero salen los de su historial)", "Opponent: type a nick (their history's opponents come first)"));
        busca.putClientProperty("JTextField.showClearButton", true);
        popup = new JPopupMenu(); popup.setFocusable(false);
        debounce = new javax.swing.Timer(400, e -> sugerir()); debounce.setRepeats(false);
        busca.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
        });
        busca.addActionListener(e -> { if (popup.getComponentCount() > 0) ((JMenuItem) popup.getComponent(0)).doClick(); });
        JPanel izq = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        atrasBtn = new JButton("\u2190"); atrasBtn.setFocusable(false); atrasBtn.setMargin(new Insets(1, 7, 1, 7)); atrasBtn.putClientProperty("JButton.buttonType", "roundRect"); atrasBtn.setEnabled(false);
        atrasBtn.setToolTipText(t("Atrás dentro de esta ventana (también el botón lateral del ratón)", "Back within this window (also the mouse's back button)"));
        atrasBtn.addActionListener(e -> atras());
        izq.add(atrasBtn);
        izq.add(new JLabel(t("Cara a cara de ", "Head-to-head of ") + view.actNombre + t(" contra:", " against:")));
        norte.add(izq, BorderLayout.WEST);
        norte.add(busca, BorderLayout.CENTER);
        modo = new JComboBox<>(new String[]{ t("Todos los modos", "All modes"), "1v1 RM", t("Equipos RM", "Team RM"), "Empire Wars", "Deathmatch", "Unranked / Custom" });
        modo.setToolTipText(t("Modo de las partidas del cruce (y de la lista de rivales)", "Mode of the pairing's games (and of the opponent list)"));
        modo.addActionListener(e -> { if (navegando) return; mapaFiltro = null; if (pidActual > 0) fijar(pidActual, nombreActual); else { pintarSugerenciasIniciales(); registrar(); } });
        JPanel der = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); der.add(new JLabel(t("Modo:", "Mode:"))); der.add(modo);
        norte.add(der, BorderLayout.EAST);
        dialogo.add(norte, BorderLayout.NORTH);
        cuerpo = new PanelScrollable();
        cuerpo.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));
        JScrollPane sp = new JScrollPane(cuerpo, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        dialogo.add(sp, BorderLayout.CENTER);
        pidActual = 0; mapaFiltro = null; historialNav.clear();
        pintarSugerenciasIniciales();
        registrar();
        dialogo.getRootPane().registerKeyboardAction(e -> dialogo.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialogo.setSize(1040, 760); dialogo.setLocationRelativeTo(ventana); dialogo.setVisible(true);
        SwingUtilities.invokeLater(busca::requestFocusInWindow);
    }

    private boolean modoOk(Match m) {
        String sel = H2H_MODOS[Math.max(0, modo == null ? 0 : modo.getSelectedIndex())];
        String modoTexto = m.mode == null ? "" : m.mode.toLowerCase(Locale.ROOT);
        boolean unranked = modoTexto.contains("unranked") || modoTexto.contains("custom");
        return switch (sel) {
            case "1v1" -> m.players.size() == 2 && !modoTexto.contains("empire") && !modoTexto.contains("death") && !unranked;
            case "tg" -> m.players.size() > 2 && !modoTexto.contains("empire") && !modoTexto.contains("death") && !unranked;
            case "ew" -> modoTexto.contains("empire");
            case "dm" -> modoTexto.contains("death");
            case "unranked" -> unranked;
            default -> true;
        };
    }

    /** Rivales del historial (los más frecuentes) con partidas y winrate del cruce, para no tener que escribir. */
    private void pintarSugerenciasIniciales() {
        cuerpo.removeAll();
        long pid = view.actPid;
        Actividad a = view.actividadCache.get(pid);
        Map<Long, Object[]> riv = new HashMap<>();   // pid → {nombre, n, w, conRes}
        if (a != null) for (Match m : a.partidas()) {
            if (!modoOk(m)) continue;
            MatchPlayer yo = null; for (MatchPlayer p : m.players) if (p.id == pid) yo = p;
            if (yo == null) continue;
            for (MatchPlayer p : m.players) if (p.id != pid && p.team != yo.team) { Object[] r = riv.computeIfAbsent(p.id, k -> new Object[]{ p.name, 0, 0, 0 }); r[1] = (Integer) r[1] + 1; if (yo.won != null) { r[3] = (Integer) r[3] + 1; if (yo.won) r[2] = (Integer) r[2] + 1; } }
        }
        List<Map.Entry<Long, Object[]>> l = new ArrayList<>(riv.entrySet());
        l.sort((x, y) -> (Integer) y.getValue()[1] - (Integer) x.getValue()[1]);
        cuerpo.add(tituloSeccion(t("Rivales más frecuentes en su historial", "Most frequent opponents in their history") + " · " + t("clic para ver el cruce", "click to see the pairing")));
        JPanel cab = new JPanel(new BorderLayout()); cab.setOpaque(false); cab.setAlignmentX(0f);
        JLabel c1 = new JLabel(t("Partidas", "Games"), SwingConstants.RIGHT), c2 = new JLabel(t("Winrate de ", "Win rate of ") + view.actNombre, SwingConstants.RIGHT);
        c1.setPreferredSize(new Dimension(70, 16)); c2.setPreferredSize(new Dimension(150, 16));
        c1.setFont(c1.getFont().deriveFont(Font.BOLD, 11f)); c1.setForeground(colorSecundario());
        c2.setFont(c2.getFont().deriveFont(Font.BOLD, 11f)); c2.setForeground(colorSecundario());
        JPanel cabDer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); cabDer.setOpaque(false); cabDer.add(c1); cabDer.add(c2);
        cab.add(cabDer, BorderLayout.EAST);
        cuerpo.add(cab);
        double max = l.isEmpty() ? 1 : (Integer) l.get(0).getValue()[1];
        int n = 0;
        for (Map.Entry<Long, Object[]> en : l) {
            long rpid = en.getKey(); String nombre = view.anfitrion.nombreVisible(rpid, (String) en.getValue()[0]); int veces = (Integer) en.getValue()[1], w = (Integer) en.getValue()[2], conRes = (Integer) en.getValue()[3];
            cuerpo.add(filaRival(rpid, nombre, veces / max, veces, w, conRes));
            if (++n >= 25) break;
        }
        if (l.isEmpty()) { JLabel vac = new JLabel(t("Sin rivales en el historial cargado con este modo.", "No opponents in the loaded history with this mode.")); vac.setForeground(Color.GRAY); vac.setAlignmentX(0f); cuerpo.add(vac); }
        cuerpo.revalidate(); cuerpo.repaint();
    }

    /** Fila de rival: bandera · nombre · barra · partidas · winrate (color). Clic: el cruce; botón central: su perfil en pestaña nueva. */
    private JPanel filaRival(long pid, String nombre, double fraccion, int n, int w, int conRes) {
        JPanel f = new JPanel(new BorderLayout(8, 0)) {
            @Override protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                int x0 = 300, ancho = Math.max(40, getWidth() - x0 - 250);
                g.setColor(new Color(128, 128, 128, 40)); g.fillRoundRect(x0, getHeight() / 2 - 4, ancho, 8, 4, 4);
                g.setColor(new Color(90, 140, 220, 200)); g.fillRoundRect(x0, getHeight() / 2 - 4, (int) (ancho * fraccion), 8, 4, 4);
            }
        };
        f.setOpaque(false); f.setAlignmentX(0f); f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        f.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        JLabel nom = new JLabel(nombre, iconoBandera(view.anfitrion.paisDe(pid)), SwingConstants.LEFT); nom.setIconTextGap(6); nom.setPreferredSize(new Dimension(280, 20)); nom.setFont(nom.getFont().deriveFont(Font.BOLD, 12.5f));
        f.add(nom, BorderLayout.WEST);
        JPanel der = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); der.setOpaque(false);
        JLabel ln = new JLabel(miles(n), SwingConstants.RIGHT); ln.setPreferredSize(new Dimension(70, 20));
        JLabel lw = new JLabel(conRes > 0 ? w + "-" + (conRes - w) + "  ·  " + pct1(100.0 * w / conRes) : "-", SwingConstants.RIGHT); lw.setPreferredSize(new Dimension(150, 20)); lw.setFont(lw.getFont().deriveFont(Font.BOLD)); lw.setForeground(conRes > 0 ? colorWr(w, conRes) : Color.GRAY);
        der.add(ln); der.add(lw);
        f.add(der, BorderLayout.EAST);
        f.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        f.setToolTipText(t("Clic: ver el cruce · botón central: su perfil en pestaña nueva · clic derecho: más", "Click: see the pairing · middle button: their profile in a new tab · right-click: more"));
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isMiddleMouseButton(e)) view.navegacion.abrirPerfilEnPestana(pid, nombre); else if (SwingUtilities.isLeftMouseButton(e)) fijar(pid, nombre); }
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) view.menus.menuContextual(pid, nombre, e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) view.menus.menuContextual(pid, nombre, e); }
        };
        f.addMouseListener(ma); nom.addMouseListener(ma);
        return f;
    }

    private void sugerir() {
        String q = busca.getText().trim();
        popup.setVisible(false); popup.removeAll();
        if (q.length() < 2) { if (q.isEmpty() && pidActual == 0) pintarSugerenciasIniciales(); return; }
        long pidAbierto = view.actPid;
        Actividad a = view.actividadCache.get(pidAbierto);
        Map<Long, String> locales = new LinkedHashMap<>();
        if (a != null) for (Match m : a.partidas()) for (MatchPlayer p : m.players) if (p.id != pidAbierto && p.name != null && p.name.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) locales.putIfAbsent(p.id, p.name);
        int n = 0;
        for (Map.Entry<Long, String> en : locales.entrySet()) { long pid = en.getKey(); String nombre = view.anfitrion.nombreVisible(pid, en.getValue()); JMenuItem it = new JMenuItem(nombre + t("  (en su historial)", "  (in their history)"), iconoBandera(view.anfitrion.paisDe(pid))); it.addActionListener(x -> fijar(pid, nombre)); popup.add(it); if (++n >= 6) break; }
        if (n > 0 && busca.isShowing()) popup.show(busca, 0, busca.getHeight());
        presenter.sugerir(q, () -> busca == null ? null : busca.getText().trim(), res -> {
            int k = 0;
            for (String[] r : res) { long pid = Long.parseLong(r[0]); if (locales.containsKey(pid) || pid == pidAbierto) continue; JMenuItem it = new JMenuItem(r[2]); String nombre = r[1]; it.addActionListener(x -> fijar(pid, nombre)); popup.add(it); if (++k >= 6) break; }
            if (popup.getComponentCount() > 0 && busca.isShowing()) popup.show(busca, 0, busca.getHeight());
        });
    }

    private String formaHtml(List<Boolean> ultimos) {
        StringBuilder b = new StringBuilder("<html>");
        for (Boolean g : ultimos) b.append(b.length() > 6 ? " " : "").append("<font color='").append(colorHex(colorWr(g ? 100 : 0, 100))).append("'>").append(g ? t("V", "W") : t("D", "L")).append("</font>");
        return b.append("</html>").toString();
    }

    /** Carga en la pestaña Partidas los enfrentamientos del cruce (del historial ya descargado): la tabla los enseña sin resultado. */
    private void verPartidasEntre(List<Match> cruce, long rivalPid, String rivalNombre) {
        if (cruce.isEmpty()) return;
        long pid = view.actPid; String nombre = view.actNombre;
        for (Match m : cruce) if (m.refId == 0) m.refId = pid;
        view.anfitrion.cargarPartidasEnTabla(cruce, new dev.tirador.aoe2radar.model.Player(pid, nombre, "", 0));
        if (dialogo != null) dialogo.setVisible(false);   // el diálogo se aparta; la app queda delante, en la pestaña Partidas
        view.anfitrion.traerAlFrente();
        view.anfitrion.mostrarEstadoGlobal(cruce.size() + t(" partidas entre ", " games between ") + nombre + t(" y ", " and ") + rivalNombre + t(" · cargadas en la pestaña Partidas (sin resultado hasta que lo pidas).", " · loaded in the Games tab (no result until you ask for it)."));
    }

    /** La ficha del cruce con un rival concreto. */
    private void fijar(long rivalPid, String rivalNombre) {
        popup.setVisible(false);
        pidActual = rivalPid; nombreActual = rivalNombre;
        if (!rivalNombre.equals(busca.getText().trim())) busca.setText(rivalNombre);
        registrar();
        long pidAbierto = view.actPid; String nombreAbierto = view.actNombre;
        Actividad a = view.actividadCache.get(pidAbierto);
        cuerpo.removeAll();
        if (a == null) return;
        List<Match> cruce = new ArrayList<>(), juntos = new ArrayList<>();
        int w = 0, l = 0, sinRes = 0, wJ = 0, lJ = 0; Instant primero = null, ultimo = null;
        List<Boolean> ultimos = new ArrayList<>(); int rachaActual = 0; Boolean rachaGana = null; boolean rachaViva = true;
        Map<String, int[]> porMapa = new HashMap<>(), porCivs = new HashMap<>(), civYo = new HashMap<>(), civEl = new HashMap<>();
        long eloYoSum = 0, eloElSum = 0; int eloN = 0; long durSum = 0; int durN = 0;
        for (Match m : a.partidas()) {   // llegan de más nueva a más vieja
            if (!modoOk(m)) continue;
            MatchPlayer yo = null, el = null;
            for (MatchPlayer p : m.players) { if (p.id == pidAbierto) yo = p; else if (p.id == rivalPid) el = p; }
            if (yo == null || el == null) continue;
            if (yo.team == el.team) { juntos.add(m); if (yo.won != null) { if (yo.won) wJ++; else lJ++; } continue; }
            if (mapaFiltro != null && !mapaFiltro.equals(m.map)) continue;
            cruce.add(m);
            if (m.started != null) { if (ultimo == null) ultimo = m.started; primero = m.started; }
            String modoL = m.mode == null ? "" : m.mode.toLowerCase(Locale.ROOT);
            boolean unoRanked = m.players.size() == 2 && !modoL.contains("empire") && !modoL.contains("death") && !modoL.contains("unranked") && !modoL.contains("custom");
            if (unoRanked && yo.rating != null && el.rating != null) { eloYoSum += yo.rating; eloElSum += el.rating; eloN++; }
            if (m.started != null && m.finished != null) { long d = Duration.between(m.started, m.finished).getSeconds(); if (d > 120) { durSum += d; durN++; } }
            Boolean gana = yo.won;
            if (gana == null) { sinRes++; continue; }
            if (gana) w++; else l++;
            if (ultimos.size() < 5) ultimos.add(gana);
            if (rachaViva) { if (rachaGana == null) { rachaGana = gana; rachaActual = 1; } else if (rachaGana == gana) rachaActual++; else rachaViva = false; }
            String mapa = m.map == null ? "?" : m.map;
            int[] pm = porMapa.computeIfAbsent(mapa, k -> new int[2]); pm[0]++; if (gana) pm[1]++;
            if (yo.civ != null && el.civ != null) { int[] pc = porCivs.computeIfAbsent(yo.civ + " vs " + el.civ, k -> new int[2]); pc[0]++; if (gana) pc[1]++; }
            if (yo.civ != null) { int[] c = civYo.computeIfAbsent(yo.civ, k -> new int[2]); c[0]++; if (gana) c[1]++; }
            if (el.civ != null) { int[] c = civEl.computeIfAbsent(el.civ, k -> new int[2]); c[0]++; if (!gana) c[1]++; }
        }
        int conRes = w + l;
        JPanel cab = new JPanel(new BorderLayout(10, 0)); cab.setOpaque(false); cab.setAlignmentX(0f);
        JPanel tituloFila = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); tituloFila.setOpaque(false);
        JLabel tYo = new JLabel("<html><span style='font-size:16px'><b>" + escapeHtml(nombreAbierto) + "</b> vs</span></html>");
        JLabel tEl = new JLabel("<html><span style='font-size:16px'><b><u>" + escapeHtml(rivalNombre) + "</u></b></span></html>", iconoBandera(view.anfitrion.paisDe(rivalPid), 18), SwingConstants.LEFT);
        tEl.setIconTextGap(6); tEl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tEl.setToolTipText(t("Clic o botón central: su perfil en pestaña nueva · clic derecho: más", "Click or middle button: their profile in a new tab · right-click: more"));
        tEl.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) view.navegacion.abrirPerfilEnPestana(rivalPid, rivalNombre); }
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) view.menus.menuContextual(rivalPid, rivalNombre, e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) view.menus.menuContextual(rivalPid, rivalNombre, e); }
        });
        tituloFila.add(tYo); tituloFila.add(tEl);
        if (mapaFiltro != null) {
            JLabel tMapa = new JLabel("<html><span style='color:gray;font-size:13px'>· " + escapeHtml(mapaFiltro) + " <u>×</u></span></html>");
            tMapa.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); tMapa.setToolTipText(t("Clic: quitar el filtro de mapa", "Click: remove the map filter"));
            tMapa.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { mapaFiltro = null; fijar(rivalPid, rivalNombre); } });
            tituloFila.add(tMapa);
        }
        cab.add(tituloFila, BorderLayout.CENTER);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); botones.setOpaque(false);
        JButton perfN = new JButton(t("Su perfil en pestaña nueva", "Their profile in a new tab")); perfN.setFocusable(false); perfN.setMargin(new Insets(1, 8, 1, 8)); perfN.putClientProperty("JButton.buttonType", "roundRect");
        perfN.addActionListener(e -> view.navegacion.abrirPerfilEnPestana(rivalPid, rivalNombre));
        botones.add(perfN);
        JButton verP = new JButton(t("Ver sus partidas", "View their games")); verP.setFocusable(false); verP.setMargin(new Insets(1, 8, 1, 8)); verP.putClientProperty("JButton.buttonType", "roundRect");
        verP.addActionListener(e -> view.anfitrion.buscarPartidasDe(rivalPid, rivalNombre));
        botones.add(verP);
        Match viva = view.vivo.partida(rivalPid);
        if (viva != null && view.anfitrion.enCursoReal(viva) && viva.id > 0) { JButton esp = new JButton(t("Espectar", "Spectate")); esp.setFocusable(false); esp.setMargin(new Insets(1, 8, 1, 8)); esp.putClientProperty("JButton.buttonType", "roundRect"); esp.addActionListener(e -> { if (view.anfitrion.confirmarEspectar(rivalNombre)) view.anfitrion.espectarPartida(viva.id); }); botones.add(esp); }
        cab.add(botones, BorderLayout.EAST);
        cuerpo.add(cab);
        if (conRes > 0) {
            String mejorMapa = null; int[] mm = null; for (Map.Entry<String, int[]> en : porMapa.entrySet()) if (en.getValue()[0] >= 2 && (mm == null || (double) en.getValue()[1] / en.getValue()[0] > (double) mm[1] / mm[0])) { mejorMapa = en.getKey(); mm = en.getValue(); }
            String civFuerteEl = null; int[] ce = null; for (Map.Entry<String, int[]> en : civEl.entrySet()) if (en.getValue()[0] >= 2 && (ce == null || (double) en.getValue()[1] / en.getValue()[0] > (double) ce[1] / ce[0])) { civFuerteEl = en.getKey(); ce = en.getValue(); }
            String quien = w > l ? nombreAbierto : l > w ? rivalNombre : null;
            StringBuilder fr = new StringBuilder();
            fr.append(quien == null ? t("Cruce igualado: ", "Even pairing: ") + w + "-" + l : quien + t(" domina el cruce: ", " leads the pairing: ") + (w > l ? w + "-" + l : l + "-" + w));
            if (rachaActual >= 2) fr.append(", ").append(rachaActual).append(rachaGana ? t(" victorias seguidas de ", " wins in a row for ") + nombreAbierto : t(" seguidas para ", " in a row for ") + rivalNombre);
            if (mejorMapa != null && mm[0] >= 2 && mm[1] > mm[0] - mm[1]) fr.append(t("; mejor en ", "; best on ")).append(mejorMapa).append(" (").append(mm[1]).append("-").append(mm[0] - mm[1]).append(")");
            if (civFuerteEl != null && ce[1] > ce[0] - ce[1]) fr.append("; ").append(rivalNombre).append(t(" le gana con ", " beats them with ")).append(civFuerteEl).append(" (").append(ce[1]).append("-").append(ce[0] - ce[1]).append(")");
            fr.append(".");
            JLabel resumen = new JLabel(fr.toString()); resumen.setFont(resumen.getFont().deriveFont(Font.PLAIN, 12.5f)); resumen.setAlignmentX(0f); resumen.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 0));
            cuerpo.add(resumen);
        }
        cuerpo.add(Box.createVerticalStrut(6));
        JPanel fichas = new JPanel(new GridLayout(1, 2, 12, 0)); fichas.setOpaque(false); fichas.setAlignmentX(0f); fichas.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        fichas.add(fichaJugador(pidAbierto, nombreAbierto)); fichas.add(fichaJugador(rivalPid, rivalNombre));
        cuerpo.add(fichas);
        cuerpo.add(Box.createVerticalStrut(8));
        if (cruce.isEmpty()) {
            JLabel vac = new JLabel(t("No se han enfrentado en el historial cargado (", "No games between them in the loaded history (") + (a.completo() ? t("último año", "last year") : miles(a.partidas().size()) + t(" partidas", " games")) + (mapaFiltro != null ? ", " + mapaFiltro : "") + ")."); vac.setForeground(Color.GRAY); vac.setAlignmentX(0f); cuerpo.add(vac);
            if (!juntos.isEmpty()) { cuerpo.add(Box.createVerticalStrut(6)); JLabel jl = new JLabel(t("Como aliados: ", "As allies: ") + juntos.size() + t(" partidas · ", " games · ") + wJ + "-" + lJ); jl.setAlignmentX(0f); cuerpo.add(jl); }
            cuerpo.revalidate(); cuerpo.repaint(); return;
        }
        long diasUltimo = ultimo == null ? -1 : Duration.between(ultimo, Instant.now()).toDays();
        double meses = primero == null || ultimo == null ? 1 : Math.max(1, Duration.between(primero, ultimo).toDays() / 30.0);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", "en".equals(IDIOMA) ? Locale.ENGLISH : Locale.forLanguageTag("es-ES"));
        JPanel fila1 = new JPanel(new GridLayout(1, 0, 8, 0)); fila1.setOpaque(false); fila1.setAlignmentX(0f); fila1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        fila1.add(view.listas.tarjeta(t("Partidas", "Games"), miles(cruce.size()), (sinRes > 0 ? sinRes + t(" sin resultado · ", " without result · ") : "") + String.format(Locale.ROOT, "%.1f", cruce.size() / meses).replace('.', ',') + t(" al mes", " per month")));
        fila1.add(view.listas.tarjeta(t("Balance", "Record"), w + "-" + l, conRes > 0 ? pct1(100.0 * w / conRes) + t(" para ", " for ") + nombreAbierto : t("sin resultados", "no results")));
        fila1.add(view.listas.tarjeta(t("Forma", "Form"), ultimos.isEmpty() ? "-" : formaHtml(ultimos), t("últimas ", "last ") + ultimos.size() + t(", la más reciente a la izquierda", ", most recent on the left")));
        fila1.add(view.listas.tarjeta(t("Racha actual", "Current streak"), rachaActual == 0 ? "-" : String.valueOf(rachaActual), rachaActual == 0 ? "" : (rachaGana ? t("seguidas de ", "in a row for ") + nombreAbierto : t("seguidas de ", "in a row for ") + rivalNombre)));
        fila1.add(view.listas.tarjeta(t("Último cruce", "Last game"), diasUltimo < 0 ? "-" : diasUltimo == 0 ? t("hoy", "today") : t("hace ", "") + diasUltimo + t(" días", " days ago"), (primero == null ? "" : t("primero: ", "first: ") + primero.atZone(java.time.ZoneId.systemDefault()).format(fmt)) + (ultimo == null ? "" : " · " + ultimo.atZone(java.time.ZoneId.systemDefault()).format(fmt))));
        cuerpo.add(fila1);
        cuerpo.add(Box.createVerticalStrut(6));
        JPanel fila2 = new JPanel(new GridLayout(1, 0, 8, 0)); fila2.setOpaque(false); fila2.setAlignmentX(0f); fila2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        if (!juntos.isEmpty()) fila2.add(view.listas.tarjeta(t("Como aliados", "As allies"), miles(juntos.size()), wJ + "-" + lJ + t(" juntos", " together")));
        if (eloN > 0) fila2.add(view.listas.tarjeta(t("ELO del cruce (1v1 RM)", "Pairing ELO (1v1 RM)"), Math.round(eloYoSum / (double) eloN) + " / " + Math.round(eloElSum / (double) eloN), t("media de cada uno · diferencia ", "average of each · gap ") + (Math.round(eloYoSum / (double) eloN) - Math.round(eloElSum / (double) eloN) >= 0 ? "+" : "") + (Math.round(eloYoSum / (double) eloN) - Math.round(eloElSum / (double) eloN))));
        if (durN > 0) fila2.add(view.listas.tarjeta(t("Duración media", "Average duration"), view.stats.duracionMedia(durSum, durN), t("de las partidas entre ambos", "of the games between them")));
        if (fila2.getComponentCount() > 0) cuerpo.add(fila2);
        cuerpo.add(Box.createVerticalStrut(10));
        JPanel columnas = new JPanel(new GridLayout(1, 2, 16, 0)); columnas.setOpaque(false); columnas.setAlignmentX(0f);
        JPanel c1 = new JPanel(); c1.setLayout(new BoxLayout(c1, BoxLayout.Y_AXIS)); c1.setOpaque(false);
        JPanel c2 = new JPanel(); c2.setLayout(new BoxLayout(c2, BoxLayout.Y_AXIS)); c2.setOpaque(false);
        Map<String, Runnable> clicMapa = new HashMap<>(); for (String k : porMapa.keySet()) clicMapa.put(k, () -> { mapaFiltro = k; fijar(rivalPid, rivalNombre); });
        view.pintarListaAgg(c1, t("Winrate por mapa · ", "Win rate by map · ") + nombreAbierto + t(" · clic: filtrar", " · click: filter"), porMapa, 10, k -> k, mapaFiltro == null ? clicMapa : null, k -> iconoMapa(k, 18));
        view.pintarListaAgg(c2, t("Civ contra civ", "Civ vs civ"), porCivs, 10, k -> k, null, k -> iconoCiv(view.techTree.claveCivDeNombre(k.substring(0, k.indexOf(" vs "))), 18));
        columnas.add(c1); columnas.add(c2);
        cuerpo.add(columnas);
        cuerpo.add(Box.createVerticalStrut(8));
        JPanel columnas2 = new JPanel(new GridLayout(1, 2, 16, 0)); columnas2.setOpaque(false); columnas2.setAlignmentX(0f);
        JPanel c3 = new JPanel(); c3.setLayout(new BoxLayout(c3, BoxLayout.Y_AXIS)); c3.setOpaque(false);
        JPanel c4 = new JPanel(); c4.setLayout(new BoxLayout(c4, BoxLayout.Y_AXIS)); c4.setOpaque(false);
        view.pintarListaAgg(c3, t("Mejores civs · ", "Best civs · ") + nombreAbierto, civYo, 5, k -> k, null, k -> iconoCiv(view.techTree.claveCivDeNombre(k), 18));
        view.pintarListaAgg(c4, t("Mejores civs · ", "Best civs · ") + rivalNombre, civEl, 5, k -> k, null, k -> iconoCiv(view.techTree.claveCivDeNombre(k), 18));
        columnas2.add(c3); columnas2.add(c4);
        cuerpo.add(columnas2);
        cuerpo.add(Box.createVerticalStrut(10));
        JPanel comparacion = new JPanel(); comparacion.setLayout(new BoxLayout(comparacion, BoxLayout.Y_AXIS)); comparacion.setOpaque(false); comparacion.setAlignmentX(0f);
        comparacion.add(tituloSeccion(t("Cada uno por su lado", "Each on their own") + " \u00B7 " + t("último año", "last year")));
        JLabel cargando = new JLabel(t("cargando el perfil del rival…", "loading the opponent's profile…")); cargando.setForeground(colorSecundario()); cargando.setAlignmentX(0f); comparacion.add(cargando);
        cuerpo.add(comparacion);
        cuerpo.add(Box.createVerticalStrut(10));
        // Fila 80: el nombre a pasar es el del RIVAL (antes se pasaba por error nombreAbierto, el del perfil ya
        // abierto); el "#pid" de respaldo lo aplica el propio presentador. Ver CaraACaraPresenter.pedirAnioRival.
        presenter.pedirAnioRival(rivalPid, rivalNombre, () -> pidActual == rivalPid, arF -> {
            comparacion.remove(cargando);
            if (arF == null) { JLabel no = new JLabel(t("El rival no está en sfr-data; abre su perfil para cargarlo por la API.", "The opponent isn't in sfr-data; open their profile to load it from the API.")); no.setForeground(colorSecundario()); no.setAlignmentX(0f); comparacion.add(no); }
            else {
                Map<String, Map<String, int[]>> mio = resumenLado(a, pidAbierto), suyo = resumenLado(arF, rivalPid);
                for (String[] sec : new String[][]{ { "mapas", t("Winrate por mapa", "Win rate by map") }, { "civs", t("Winrate por civ", "Win rate by civ") }, { "duracion", t("Por duración (min)", "By duration (min)") }, { "posicion", t("Pocket o flanco", "Pocket or flank") } }) {
                    JPanel par = new JPanel(new GridLayout(1, 2, 16, 0)); par.setOpaque(false); par.setAlignmentX(0f);
                    JPanel l1 = new JPanel(); l1.setLayout(new BoxLayout(l1, BoxLayout.Y_AXIS)); l1.setOpaque(false);
                    JPanel l2 = new JPanel(); l2.setLayout(new BoxLayout(l2, BoxLayout.Y_AXIS)); l2.setOpaque(false);
                    java.util.function.Function<String, Icon> ic = "mapas".equals(sec[0]) ? k -> iconoMapa(k, 18) : "civs".equals(sec[0]) ? k -> iconoCiv(view.techTree.claveCivDeNombre(k), 18) : null;
                    view.pintarListaAgg(l1, sec[1] + " \u00B7 " + nombreAbierto, mio.get(sec[0]), 5, k -> k, null, ic);
                    view.pintarListaAgg(l2, sec[1] + " \u00B7 " + rivalNombre, suyo.get(sec[0]), 5, k -> k, null, ic);
                    par.add(l1); par.add(l2);
                    comparacion.add(par); comparacion.add(Box.createVerticalStrut(6));
                }
            }
            comparacion.revalidate(); comparacion.repaint();
        });
        JButton verEntre = new JButton(t("Ver las partidas entre ambos en Partidas (sin spoilers)", "See the games between them in Games (spoiler-free)"));
        verEntre.setFocusable(false); verEntre.setMargin(new Insets(2, 10, 2, 10)); verEntre.putClientProperty("JButton.buttonType", "roundRect"); verEntre.setAlignmentX(0f);
        verEntre.setToolTipText(t("Carga en la pestaña Partidas los enfrentamientos del historial (con el modo y el mapa elegidos aquí): sin resultado y con la rec a un clic", "Loads the pairing's games from the history into the Games tab (with the mode and map chosen here): no result, rec one click away"));
        final List<Match> cruceF = new ArrayList<>(cruce);
        verEntre.addActionListener(e -> verPartidasEntre(cruceF, rivalPid, rivalNombre));
        cuerpo.add(verEntre);
        cuerpo.revalidate(); cuerpo.repaint();
        subirArriba(cuerpo);
    }

    /** Resumen de un jugador para la comparación: winrate por mapa, por civ, por duración y pocket/flanco, respetando el modo elegido en el diálogo. */
    private Map<String, Map<String, int[]>> resumenLado(Actividad a, long pid) {
        Map<String, int[]> mapas = new HashMap<>(), civs = new HashMap<>(), dur = new LinkedHashMap<>(), pos = new LinkedHashMap<>();
        for (String d : DURACION_TRAMOS) dur.put(d, new int[2]);
        for (Match m : a.partidas()) {
            if (!modoOk(m)) continue;
            MatchPlayer yo = null; for (MatchPlayer p : m.players) if (p.id == pid) yo = p;
            if (yo == null || yo.won == null) continue;
            boolean g = yo.won;
            if (m.map != null) { int[] x = mapas.computeIfAbsent(m.map, k -> new int[2]); x[0]++; if (g) x[1]++; }
            if (yo.civ != null) { int[] x = civs.computeIfAbsent(yo.civ, k -> new int[2]); x[0]++; if (g) x[1]++; }
            int td = tramoDuracion(m); if (td >= 0) { int[] x = dur.get(DURACION_TRAMOS[td]); x[0]++; if (g) x[1]++; }
            String p = posicionEnEquipo(m, yo); if (p != null) { int[] x = pos.computeIfAbsent(posicionNombre(p), k -> new int[2]); x[0]++; if (g) x[1]++; }
        }
        dur.values().removeIf(v -> v[0] == 0);
        Map<String, Map<String, int[]>> out = new HashMap<>(); out.put("mapas", mapas); out.put("civs", civs); out.put("duracion", dur); out.put("posicion", pos);
        return out;
    }

    /** Ficha compacta de un jugador para el cara a cara: bandera, nick, ELO y puesto en 1v1 y equipos (se pide en segundo plano si no está). */
    private JPanel fichaJugador(long pid, String nombre) {
        JPanel f = new JPanel(new BorderLayout(8, 0)); f.setOpaque(false);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(128, 128, 128, 70), 1, true), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JLabel l = new JLabel(nombre, iconoBandera(view.anfitrion.paisDe(pid), 18), SwingConstants.LEFT); l.setIconTextGap(6); l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        f.add(l, BorderLayout.WEST);
        JLabel datos = new JLabel();
        datos.setFont(datos.getFont().deriveFont(Font.PLAIN, 12f));
        FichaPerfil perfil = view.perfiles.fichaConocida(pid);
        if (perfil == null && view.eloAyer.get(pid) != null) {   // del snapshot nocturno: sin llamada
            int[] sn = view.eloAyer.get(pid); Map<String, int[]> mm = new HashMap<>();
            if (sn[0] > 0) mm.put("rm_1v1", new int[]{ sn[0], 0, 0, 0, 0 }); if (sn[2] > 0) mm.put("rm_team", new int[]{ sn[2], 0, 0, 0, 0 });
            perfil = new FichaPerfil(mm, view.nombresAyer.getOrDefault(pid, new String[]{ "", "" })[1], "", 0L);
        }
        if (perfil == null) {
            datos.setText(t("cargando…", "loading…")); datos.setForeground(Color.GRAY);
            presenter.pedirFicha(pid, p2 -> pintarDatosFicha(datos, p2));
        } else pintarDatosFicha(datos, perfil);
        f.add(datos, BorderLayout.CENTER);
        return f;
    }

    private void pintarDatosFicha(JLabel datos, FichaPerfil perfil) {
        if (perfil == null) { datos.setText(""); return; }
        Map<String, int[]> m = perfil.ladders();
        StringBuilder h = new StringBuilder("<html>");
        for (String lb : new String[]{ "rm_1v1", "rm_team" }) { int[] v = m.get(lb); if (v == null || v[0] <= 0) continue; if (h.length() > 6) h.append("&nbsp;&nbsp;·&nbsp;&nbsp;"); h.append("<span style='color:gray'>").append(escapeHtml(ladderNombre(lb))).append("</span> <b>").append(v[0]).append("</b>").append(v[1] > 0 ? " <span style='color:gray'>#" + miles(v[1]) + "</span>" : ""); }
        datos.setText(h.append("</html>").toString()); datos.setForeground(UIManager.getColor("Label.foreground"));
    }
}
