package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Player;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static dev.tirador.aoe2radar.ui.Componentes.colorVivoHex;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * La lista de jugadores de la Watchlist: su renderer (punto de vivo, bandera, nick, TW, forma, ELO, sublíneas),
 * los listeners de ratón (clic, doble clic, botón central, menú contextual, cursor de mano), la cabecera
 * ordenable y el scroll. Sale de construirPanel() tal cual en la 1.3, en dos trozos que la fachada llama en el
 * mismo punto que antes: construirLista() (selección, renderer y ratón) y construirCentro(left) (cabecera,
 * hover-timer, scroll). El ORDEN de registro de los listeners de playersList es el de la 1.1 y importa (AWT
 * los avisa en ese orden). Hilos: todo en el EDT.
 */
final class WatchlistLista {

    private final WatchlistView wv;

    WatchlistLista(WatchlistView wv) { this.wv = wv; }

    /** El principio de construirPanel(): selección, botón «Quitar», ratón y renderer de playersList. */
    void construirLista() {
        // Panel izquierdo: jugadores seguidos (la selección filtra la tabla)
        wv.playersList.setVisibleRowCount(12);
        wv.playersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { wv.enlacePartidas.applyFilters(); wv.anfitrion.seleccionCambiada(); }   // con Ratings o Perfil abiertos, la selección se refleja allí
        });
        wv.delBtn = new JButton(t("Quitar de la Watchlist", "Remove from Watchlist"));   // quita de la Watchlist, no solo del grupo (F12 1.3)

        wv.playersList.addMouseListener(new MouseAdapter() {   // botón central sobre un jugador: su perfil en pestaña nueva
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isMiddleMouseButton(e)) return;
                int idx = wv.playersList.locationToIndex(e.getPoint());
                if (idx < 0 || !wv.playersList.getCellBounds(idx, idx).contains(e.getPoint())) return;
                Player p = wv.playersModel.get(idx);
                wv.navegacion.abrirPerfilEnPestana(p.id(), wv.anfitrion.nombreVisible(p.id(), p.name()));
                e.consume();
            }
        });
        wv.playersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Player p) {
                    char marca = wv.marcaFila.getOrDefault(p.id(), ' ');
                    boolean vivo = WatchlistView.VIVO.jugando(p.id())
                            || (marca != 'H' && wv.vivoFamilia.getOrDefault(p.id(), false));
                    Integer elo = wv.eloWatch.get(p.id());
                    Integer rank = wv.modoTop() ? wv.rankTop.get(p.id()) : null;
                    String punto = vivo ? "<font color='#" + colorVivoHex() + "'>\u25CF</font>" : "";
                    String col1 = rank != null ? "<font color='gray'>" + rank + ".</font>"
                            : marca == 'P' ? "\u25B8" : marca == 'E' ? "\u25BE" : "";
                    String eloTxt = (elo != null && wv.mostrarEloWatch) ? String.valueOf(elo) : "";
                    int w0 = Math.max(150, list.getWidth() - 22);
                    FontMetrics fmSub = l.getFontMetrics(l.getFont());   // para truncar sublíneas en píxeles reales
                    int maxChars = Math.max(10, (w0 - 76 - WatchlistView.anchoCeldaElo(w0) - (wv.formaVisible ? 72 : 0)) / 7);
                    String nombreVis = wv.anfitrion.nombreVisible(p.id(), p.name());
                    String tip = wv.aliases.containsKey(p.id())
                            ? t("Nick real: ", "Real nick: ") + p.name() : null;
                    String notaP = wv.dialogos.notaDe(p.id());
                    if (notaP != null) tip = (tip == null ? "" : tip + " \u2014 ") + t("Nota: ", "Note: ") + notaP + t(" (clic en \u270E para editar)", " (click \u270E to edit)");

                    if (nombreVis.length() > maxChars) {
                        nombreVis = nombreVis.substring(0, maxChars - 1) + "\u2026";
                        tip = p.name();
                    }
                    String[] st = wv.twitchLive.get(p.id());   // solo para pintar el badge: sin tooltip del título (tapaba la tarjeta)
                    l.setToolTipText(tip);   // ni «jugando ahora» ni el título del stream: el punto, la sublínea y Directos ya lo cuentan
                    Match enCurso = vivo ? WatchlistView.VIVO.partida(p.id()) : null;   // una sola lectura: el socket puede soltarla entre dos
                    String mapaVivo = enCurso != null && enCurso.map != null ? enCurso.map : null;
                    if (mapaVivo != null) {   // el mapa cabe en lo que sobra tras el nick; si no cabe, se recorta con «…» y, si ni siquiera hay sitio, no se pinta (el ELO nunca se tapa)
                        int sitio = maxChars - nombreVis.length() - 3;
                        if (sitio < 6) mapaVivo = null;
                        else if (mapaVivo.length() > sitio) mapaVivo = mapaVivo.substring(0, sitio - 1) + "\u2026";
                    }
                    String nick = (vivo ? "<font color='" + (temaOscuroActivo ? "#ffd56a" : "#b06a00") + "'>" + escapeHtml(nombreVis) + "</font>" + (mapaVivo != null ? " <font color='#8a8a8a'>\u00B7</font> <font color='" + (temaOscuroActivo ? "#ffd56a" : "#b06a00") + "'>" + escapeHtml(mapaVivo) + "</font>" : "") : escapeHtml(nombreVis))   // en partida: nick en ámbar y el mapa al lado
                            + (wv.dialogos.notaDe(p.id()) != null ? " <font color='#8a8a8a'>\u270E</font>" : "")
                            + (wv.modoTop() && wv.containsPlayerId(p.id()) ? " <font color='#8a8a8a'>\u2605</font>" : "");   // ya fichado
                    if (marca == 'H')   // hija: sangría fija y un punto menos, legible
                        nick = "&nbsp;&nbsp;&nbsp;<span style='font-size:0.92em'>" + nick + "</span>";
                    l.setBorder(null);   // renderer compartido: se fija SIEMPRE
                    String[] alt = marca == 'H' ? null : wv.mejorAlt(p.id());
                    if (alt != null) {
                        eloTxt = eloTxt + " <font color='#8a8a8a'>\u21A5" + alt[1] + "</font>";
                        tip = (tip == null ? "" : tip + " \u2014 ")
                                + t("Su cuenta ", "Their account ") + alt[0]
                                + t(" está a ", " sits at ") + alt[1]
                                + t(" (clic para ver su perfil)", " (click to view their profile)");
                    }
                    int w = Math.max(150, list.getWidth() - 22);   // el HTML no refluye: ancho explícito
                    l.setText("<html><table width='" + w + "' cellpadding='0' cellspacing='0'><tr>"
                            + "<td width='24' align='right'>" + col1 + "</td>"
                            + "<td width='14' align='center'>" + punto + "</td>"
                            + "<td width='20' align='center'>" + (wv.modoPais() ? "" : Iconos.banderaHtml(wv.anfitrion.paisDe(p.id()))) + "</td>"   // en «Top país» todas serían la misma: solo en el selector
                            + "<td nowrap align='left'>" + nick + "</td>"
                            + "<td width='26' align='center'>" + (st != null
                                    ? "<font color='#9146FF'><b>TW</b></font>" : "") + "</td>"
                            + (wv.formaVisible ? "<td width='72' align='center'>" + celdaForma(p.id()) + "</td>" : "")
                            + "<td width='" + WatchlistView.anchoCeldaElo(w0) + "' align='right'>" + eloTxt + "</td></tr>"
                            + "</table>"
                            // Las sublíneas van FUERA de la tabla, como bloques propios: un colspan dentro
                            // cambia el reparto de anchos de las columnas y descoloca puesto/punto/nick
                            + (vivo
                                ? "<div style='margin-left:38px'>" + subtextoVivo(p.id(), fmSub, w0 - 44) + "</div>"
                                : "")
                            + (wv.dialogos.notaDe(p.id()) != null
                                ? "<div style='margin-left:38px'><font color='#b08d57'>\u270E "
                                  + escapeHtml(dev.tirador.aoe2radar.util.Formato.truncarPx(wv.dialogos.notaDe(p.id()), fmSub, w0 - 60)) + "</font></div>"
                                : "")
                            + "</html>");
                }
                return l;
            }
        });
        wv.playersList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  {
                wv.ocultarHoverCard(true);
                if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1) {
                    int idxH = wv.playersList.locationToIndex(e.getPoint());
                    if (idxH >= 0 && wv.playersList.getCellBounds(idxH, idxH).contains(e.getPoint())) {
                        String bajo = textoBajo(idxH, e.getPoint());   // lo que hay pintado bajo el ratón, de verdad
                        Player ph = wv.playersModel.get(idxH);
                        if (wv.enlacePartidas.invitado() != null) {
                            wv.enlacePartidas.limpiarInvitado();
                            wv.enlacePartidas.limpiarSujetos();
                            wv.enlacePartidas.refrescarSujetos(List.of(), false);
                            wv.enlacePartidas.actualizarTextoBuscar();
                        }
                        if (bajo.contains("\u270E") || sobreNota(idxH, e.getPoint())) { wv.dialogos.pedirNota(ph.id(), ph.name()); return; }
                        int wL = wv.playersList.getWidth() - 22, eloW = WatchlistView.anchoCeldaElo(wL);
                        int formaW = wv.formaVisible ? 72 : 0;
                        boolean zonaTwFormula = e.getX() >= wL - eloW - formaW - 40 && e.getX() <= wL - eloW - formaW + 8;   // respaldo (la columna de forma desplaza el badge)
                        if (bajo.contains("TW") || (bajo.isEmpty() && zonaTwFormula)) {
                            String[] stT = wv.twitchLive.get(ph.id());
                            if (stT != null) { wv.anfitrion.abrirUrl("https://twitch.tv/" + stT[0]); return; }
                        }
                        if (bajo.contains("\u21A5") && wv.marcaFila.getOrDefault(ph.id(), ' ') != 'H') {
                            String[] altA = wv.mejorAlt(ph.id());
                            if (altA != null && altA.length > 2) {
                                try { wv.navegacion.abrirPerfil(Long.parseLong(altA[2]), altA[0]); return; }
                                catch (NumberFormatException ignored) { }
                            }
                        }
                        if (wv.twitchLive.containsKey(ph.id()) && e.getX() > wL / 2)   // rastro: si un TW no reacciona, el log dice qué vio
                            log("clic sin acción en zona derecha (x=" + e.getX() + " de " + wL + ", bajo='" + bajo.trim() + "')");
                    }
                }
                if (!e.isPopupTrigger() && e.getButton() == MouseEvent.BUTTON1 && e.getX() < 26) {
                    int idx = wv.playersList.locationToIndex(e.getPoint());
                    if (idx >= 0 && wv.playersList.getCellBounds(idx, idx).contains(e.getPoint())) {
                        Player p = wv.playersModel.get(idx);
                        char m = wv.marcaFila.getOrDefault(p.id(), ' ');
                        if (m == 'P' || m == 'E') {
                            if (!wv.vinculosExpandidos.remove(p.vinculo())) wv.vinculosExpandidos.add(p.vinculo());
                            wv.aplicarFiltroGrupo();
                            return;
                        }
                    }
                }
                maybePopup(e);
            }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int idx = wv.playersList.locationToIndex(e.getPoint());
                if (idx < 0) return;
                Rectangle celda = wv.playersList.getCellBounds(idx, idx);
                if (celda == null || !celda.contains(e.getPoint())) return;   // clic fuera de los nicks
                if (!wv.playersList.isSelectedIndex(idx)) wv.playersList.setSelectedIndex(idx);   // si ya está en la selección, se conserva la múltiple
                Player p = wv.playersModel.get(idx);
                wv.menuContextualWatchlist(p, e);
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown()) {
                    int idx1 = wv.playersList.locationToIndex(e.getPoint());
                    boolean sobreFila = idx1 >= 0 && wv.playersList.getCellBounds(idx1, idx1).contains(e.getPoint());
                    if (!sobreFila) { wv.playersList.clearSelection(); wv.enlacePartidas.actualizarTextoBuscar(); }   // como el Explorador: clic en el vacío = sin selección
                    return;
                }
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown()) {
                    int idx = wv.playersList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        Player p = wv.playersModel.get(idx);
                        String bajo = textoBajo(idx, e.getPoint());
                        if (bajo.contains("TW") || bajo.contains("\u21A5") || bajo.contains("\u270E") || sobreNota(idx, e.getPoint())) return;   // el clic simple ya actuó
                        wv.playersList.setSelectedIndex(idx);   // doble clic = sus partidas, SIEMPRE (espectar vive en el clic derecho)
                        wv.enlacePartidas.fetchMatches();
                    }
                }
            }
        });
        wv.delBtn.addActionListener(e -> {
            List<Player> sel = wv.playersList.getSelectedValuesList();
            if (sel.isEmpty()) return;
            StringBuilder nombres = new StringBuilder();
            for (Player p : sel) nombres.append(nombres.isEmpty() ? "" : ", ").append(p.name());
            int r = JOptionPane.showConfirmDialog(wv.ventana,
                    t("Se quitará de la Watchlist a: ", "This will remove from the Watchlist: ") + nombres
                            + t(". ¿Continuar?", ". Continue?"),
                    t("Quitar jugador", "Remove player"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return;
            for (Player p : sel) {
                wv.playersModel.removeElement(p);
                wv.todosJugadores.removeIf(x -> x.id() == p.id());
            }
            wv.savePlayers();
            wv.rebuildGrupos();
            wv.enlacePartidas.applyFilters();
        });
    }

    /** El final de construirPanel(): cabecera ordenable, hover-timer, cursor, scroll y sujetos; left es el
     *  panel de la Watchlist. */
    void construirCentro(JPanel left) {
        JLabel cabLabelLocal = new JLabel();
        cabLabelLocal.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Component.borderColor") != null
                        ? UIManager.getColor("Component.borderColor") : Color.GRAY));
        cabLabelLocal.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cabLabelLocal.setToolTipText(t("Clic en Nick o en ELO para ordenar por esa columna",
                "Click Nick or ELO to sort by that column"));
        wv.cabLabel = cabLabelLocal;
        cabLabelLocal.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int wC = wv.cabLabel.getWidth();
                String actual = leerConfig("orden_watch", "elo");
                String nuevo = e.getX() >= wC - 60 ? "elo"
                        : (wv.formaVisible && e.getX() >= wC - 60 - 72 - 30) ? ("forma".equals(actual) ? "forma_asc" : "forma")   // 2.º clic: invierte
                        : "alfa";
                guardarConfig("orden_watch", nuevo);
                wv.aplicarFiltroGrupo();
                refrescarCabeceraOrden();
            }
        });
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.add(wv.cabLabel, BorderLayout.CENTER);
        wv.hoverTimer = new javax.swing.Timer(600, ev -> {
            if (wv.hoverPid != 0 && wv.hoverPantalla != null && wv.hoverProcede()) {
                for (int i = 0; i < wv.playersModel.size(); i++)   // el pid ya quedó fijado al mover: mostramos directamente
                    if (wv.playersModel.get(i).id() == wv.hoverPid) {
                        wv.mostrarPerfilCard(wv.hoverPid, wv.playersModel.get(i).name(), wv.hoverPantalla);
                        return;
                    }
            }
        });
        wv.hoverTimer.setRepeats(false);
        wv.playersList.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int idx = wv.playersList.locationToIndex(e.getPoint());
                long pid = 0;
                if (idx >= 0 && wv.playersList.getCellBounds(idx, idx).contains(e.getPoint()))
                    pid = wv.playersModel.get(idx).id();
                if (pid != 0 && wv.enZonaForma(e.getPoint())) pid = 0;   // sobre la celda Forma no sale la tarjeta: sale su tooltip
                String bajoM = pid != 0 ? textoBajo(idx, e.getPoint()) : "";   // mano sobre TW, \u21A5 y \u270E
                boolean clicable = bajoM.contains("TW") || bajoM.contains("\u21A5") || bajoM.contains("\u270E")
                        || (pid != 0 && sobreNota(idx, e.getPoint()));
                wv.playersList.setCursor(Cursor.getPredefinedCursor(clicable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                if (pid != wv.hoverPid) {
                    if (wv.hoverCard != null) wv.ocultarHoverCard();
                    wv.hoverTimer.stop();
                    wv.hoverPid = pid;
                    if (false && pid != 0) {   // tarjeta flotante desactivada: el ELO ya está en la lista y el perfil a un doble clic (y cada tarjeta era una llamada a la API)
                        wv.hoverPantalla = e.getLocationOnScreen();
                        wv.hoverTimer.restart();
                    }
                }
            }
        });
        wv.playersList.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { wv.ocultarHoverCard(); }
        });
        ToolTipManager.sharedInstance().registerComponent(wv.playersList);
        wv.playersList.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { refrescarCabeceraOrden(); }
        });
        JPanel listWrap = new JPanel(new BorderLayout());

        JScrollPane listScroll = new JScrollPane(wv.playersList);
        listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.getViewport().addMouseListener(new MouseAdapter() {   // el hueco bajo la última fila es del visor
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown()) {
                    wv.playersList.clearSelection();
                    wv.enlacePartidas.actualizarTextoBuscar();
                }
            }
        });
        listScroll.getViewport().addChangeListener(e -> {
            wv.ocultarHoverCard();
            int vw = listScroll.getViewport().getWidth();
            if (vw > 0 && wv.playersList.getFixedCellWidth() != vw)
                wv.playersList.setFixedCellWidth(vw);   // orden, no sugerencia: invalida el caché de medidas
            refrescarCabeceraOrden();
        });
        wv.sujetosPanel.setLayout(new BoxLayout(wv.sujetosPanel, BoxLayout.Y_AXIS));
        wv.sujetosPanel.setVisible(false);
        JPanel norteLista = new JPanel(new BorderLayout());
        norteLista.add(wv.sujetosPanel, BorderLayout.NORTH);
        norteLista.add(cabecera, BorderLayout.SOUTH);   // la cabecera, pegada a su lista
        listWrap.add(norteLista, BorderLayout.NORTH);
        listWrap.add(listScroll, BorderLayout.CENTER);
        left.add(listWrap, BorderLayout.CENTER);
        refrescarCabeceraOrden();
    }

    /** Subtexto de quien está en partida, con el estilo de Live now: rival en color normal, civs en gris, mapa y reloj en ámbar. Se recorta a la anchura: primero cae el reloj, luego las civs, y al final se acorta el rival. */
    private String subtextoVivo(long pid, FontMetrics fm, int px) {
        Match m = WatchlistView.VIVO.partida(pid);
        String amb = temaOscuroActivo ? "#ffd56a" : "#b06a00";
        if (m == null || !wv.anfitrion.enCursoReal(m)) {
            String info = WatchlistView.VIVO.info(pid);
            return "<font color='#8a8a8a'>" + escapeHtml(dev.tirador.aoe2radar.util.Formato.truncarPx(info != null ? info : t("partida en curso \u2014 detalle en el próximo tick", "game in progress \u2014 details next tick"), fm, px)) + "</font>";
        }
        MatchPlayer yo = null; for (MatchPlayer mp : m.players) if (mp.id == pid) yo = mp;
        List<String> rivales = new ArrayList<>();
        MatchPlayer rivalUnico = null;
        for (MatchPlayer mp : m.players) { if (mp.id == pid) continue; if (yo != null && mp.team == yo.team) continue; rivales.add(wv.anfitrion.nombreVisible(mp.id, mp.name) + (mp.rating != null ? " (" + mp.rating + ")" : "")); rivalUnico = mp; }
        boolean unoContraUno = m.players.size() == 2 && rivales.size() == 1;
        String prefijo = unoContraUno ? "vs " : "TG " + (m.players.size() / 2) + "v" + (m.players.size() / 2);
        String rival = unoContraUno ? String.join(", ", rivales) : "";   // en equipos, los nombres van en el clic derecho
        String civs = unoContraUno && yo != null && yo.civ != null && rivalUnico != null && rivalUnico.civ != null ? yo.civ + "\u2013" + rivalUnico.civ : "";
        String mapa = "";   // el mapa va ya en la línea del nick
        String reloj = "";  // sin reloj: molestaba
        // recorte por anchura, midiendo el texto plano
        java.util.function.Function<String[], String> plano = partes -> partes[0] + partes[1] + (partes[2].isEmpty() ? "" : " " + partes[2]) + (mapa.isEmpty() ? "" : " \u00B7 " + mapa) + (partes[3].isEmpty() ? "" : " \u00B7 " + partes[3]);
        String[] partes = { prefijo, rival, civs, reloj };
        if (fm.stringWidth(plano.apply(partes)) > px) partes[3] = "";
        if (fm.stringWidth(plano.apply(partes)) > px) partes[2] = "";
        while (fm.stringWidth(plano.apply(partes)) > px && partes[1].length() > 4) partes[1] = partes[1].substring(0, partes[1].length() - 2).trim() + "\u2026";
        StringBuilder h = new StringBuilder();
        h.append("<font color='#8a8a8a'>").append(escapeHtml(partes[0])).append("</font>").append(escapeHtml(partes[1]));
        if (!partes[2].isEmpty()) h.append(" <font color='#8a8a8a'>").append(escapeHtml(partes[2])).append("</font>");
        if (!mapa.isEmpty()) h.append(" <font color='#8a8a8a'>\u00B7</font> <font color='").append(amb).append("'>").append(escapeHtml(mapa)).append("</font>");
        if (!partes[3].isEmpty()) h.append(" <font color='#8a8a8a'>\u00B7</font> <font color='").append(amb).append("'>").append(partes[3]).append("</font>");
        return h.toString();
    }

    /** ¿Qué texto hay pintado bajo el punto p en la fila idx? Pregunta a la vista HTML
     *  real del renderer (exacto sea cual sea el ancho), o "" si no hay texto ahí. */
    private String textoBajo(int idx, Point p) {
        try {
            Rectangle b = wv.playersList.getCellBounds(idx, idx);
            if (b == null || !b.contains(p)) return "";
            Component c = wv.playersList.getCellRenderer().getListCellRendererComponent(
                    wv.playersList, wv.playersModel.get(idx), idx, false, false);
            if (!(c instanceof JLabel l)) return "";
            l.setBounds(0, 0, b.width, b.height);
            Object vo = l.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
            if (!(vo instanceof javax.swing.text.View v)) return "";
            Insets in = l.getInsets();
            Rectangle alloc = new Rectangle(in.left, in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
            v.setSize(alloc.width, alloc.height);   // sin tamaño, la vista responde con coordenadas colapsadas
            int x = p.x - b.x, y = p.y - b.y;
            javax.swing.text.Position.Bias[] bias = new javax.swing.text.Position.Bias[1];
            int pos = v.viewToModel(x, y, alloc, bias);
            if (pos < 0) return "";
            java.awt.Shape s = v.modelToView(Math.max(0, pos - 1), alloc, javax.swing.text.Position.Bias.Forward);
            Rectangle r = s.getBounds();
            r.grow(6, 2);   // tolerancia de unos píxeles alrededor del glifo
            if (!r.contains(x, y)) return "";
            javax.swing.text.Document d = v.getDocument();
            int ini = Math.max(0, pos - 3), fin = Math.min(d.getLength(), pos + 3);
            return d.getText(ini, fin - ini);
        } catch (Exception ex) {
            return "";
        }
    }

    /** ¿El punto p cae sobre la sublínea «\u270E nota» de la fila idx (toda la línea, no solo el lápiz)? */
    private boolean sobreNota(int idx, Point p) {
        try {
            if (idx < 0 || wv.dialogos.notaDe(wv.playersModel.get(idx).id()) == null) return false;
            Rectangle b = wv.playersList.getCellBounds(idx, idx);
            if (b == null || !b.contains(p)) return false;
            Component c = wv.playersList.getCellRenderer().getListCellRendererComponent(
                    wv.playersList, wv.playersModel.get(idx), idx, false, false);
            if (!(c instanceof JLabel l)) return false;
            l.setBounds(0, 0, b.width, b.height);
            Object vo = l.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
            if (!(vo instanceof javax.swing.text.View v)) return false;
            Insets in = l.getInsets();
            Rectangle alloc = new Rectangle(in.left, in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
            v.setSize(alloc.width, alloc.height);
            int x = p.x - b.x, y = p.y - b.y;
            javax.swing.text.Document d = v.getDocument();
            String todo = d.getText(0, d.getLength());
            int lapiz = todo.lastIndexOf('\u270E');   // el último \u270E es el de la sublínea
            if (lapiz < 0) return false;
            Rectangle rNota = v.modelToView(lapiz, alloc, javax.swing.text.Position.Bias.Forward).getBounds();
            return y >= rNota.y - 2 && y <= rNota.y + rNota.height + 2 && x >= rNota.x - 6;   // la línea entera, desde el lápiz
        } catch (Exception ex) {
            return false;
        }
    }

    private String celdaForma(long pid) {
        Forma f = wv.formaActiva().get(pid);
        if (f == null) return "<font color='#8a8a8a'>\u2014</font>";   // sin consultar
        String col = f.diff() > 0 ? (temaOscuroActivo ? "#6abf69" : "#2e7d32") : f.diff() < 0 ? (temaOscuroActivo ? "#e57373" : "#c62828") : "#8a8a8a";
        return "<font color='" + col + "'>" + escapeHtml(f.corta()) + "</font>";
    }

    void refrescarCabeceraOrden() {
        if (wv.cabLabel == null) return;
        String ordenCfg = leerConfig("orden_watch", "elo");
        boolean porForma = wv.formaVisible && ordenCfg.startsWith("forma");
        boolean formaAsc = "forma_asc".equals(ordenCfg);
        boolean porElo = !porForma && wv.mostrarEloWatch && "elo".equals(ordenCfg);
        String ordenable = " <font color='#8a8a8a'>\u21C5</font>";   // «⇅»: aquí también se puede ordenar
        wv.cabLabel.setToolTipText(t("Clic en una cabecera para ordenar por Nick, ELO o Forma", "Click a header to sort by Nick, ELO or Recent form"));
        wv.cabLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        int w = Math.max(150, wv.playersList.getWidth() > 0 ? wv.playersList.getWidth() - 22 : 250);
        wv.cabLabel.setText("<html><table width='" + w + "' cellpadding='0' cellspacing='0'><tr>"
                + "<td width='24'></td><td width='14'></td>"
                + "<td><b>Nick" + (porElo || porForma ? ordenable : " \u2193") + "</b></td>"
                + (wv.formaVisible ? "<td width='72' align='center'><b>" + t("Forma ", "Form ") + (wv.ventanaForma <= 24 ? "24h" : "7d")
                        + (porForma ? (formaAsc ? " \u2191" : " \u2193") : ordenable) + "</b></td>" : "")
                + "<td width='" + WatchlistView.anchoCeldaElo(Math.max(150, wv.playersList.getWidth() - 22)) + "' align='right'><b>" + (wv.mostrarEloWatch ? "ELO" + (porElo ? " \u2193" : ordenable) : "") + "</b></td>"
                + "</tr></table></html>");
    }
}
