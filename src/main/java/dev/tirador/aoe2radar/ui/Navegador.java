package dev.tirador.aoe2radar.ui;

import javax.swing.*;
import java.awt.*;

import static dev.tirador.aoe2radar.ui.Componentes.subirArriba;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * El cromo compartido por las siete pestañas de la ventana (Partidas, Twitch, Live now, Perfil, Ratings, Civ
 * Stats, Tech tree): los botones de la barra de vistas, las flechas de atrás/adelante y el esqueleto común de
 * cada {@code abrir*} (deseleccionar las otras pestañas, cambiar de card, tapar resultados/forma, refrescar los
 * controles de la tabla). El historial en sí (qué vista está activa y por dónde se ha pasado) vive en
 * {@link AppState}; este objeto decide QUÉ pasa al navegar y pinta los botones. Implementa {@link Navegacion}:
 * las vistas piden «abre tal cosa» sin conocer ni la ventana ni al resto de vistas.
 *
 * <p>Se construye en dos tiempos. {@code partidas} llega ya en el constructor: es un inicializador de campo de
 * la ventana (se crea antes de que corra el cuerpo del constructor, como {@code status}), así que está listo
 * antes incluso de {@code CableadoCromo.configurarVentana()}. El resto de vistas ({@code watchlist}, {@code perfil},
 * {@code liveNow}, {@code techTree}, {@code ratings}, {@code civStats}, {@code directos}) y {@code centroCards}/
 * {@code splitPrincipal} llegan tarde de verdad: {@code watchlist} se asigna en el cuerpo del constructor
 * (después de {@code configurarVentana}), y las demás las crea {@code CableadoCentro.construirCentro}/
 * {@code CableadoCromo.montarVentana}, que corren después de que {@code CableadoCromo.construirBarraSuperior} ya
 * haya pedido los botones de pestaña. Por eso
 * {@link #conectarVistas} las enchufa aparte, justo después de {@code montarVentana}. Nada síncrono las usa
 * antes: {@code abrirLadder}/{@code abrirCivStats}/etc. solo se disparan por un clic de botón o por
 * {@code irA}, siempre después de que el constructor de la ventana haya terminado del todo — el mismo truco de
 * «referencia adelantada» que ya usa el resto de la app (p. ej. {@code liveNow} capturando
 * {@code techTree::claveCivDeNombre} antes de que el campo exista).
 */
public final class Navegador implements Navegacion {

    /** Vista activa e historial (ver AppState). */
    public final AppState estado = new AppState();

    // Botones de pestaña y flechas de historial: públicos porque RegresionCapturas los usa por nombre
    // (app.navegador.xxxBtn.doClick()).
    public JToggleButton recsBtn;                    // pestaña «Partidas»
    public JToggleButton directosBtn, ahoraBtn, perfilBtn, ladderBtn, civStatsBtn, techTreeBtn;
    public JButton atrasBtn, adelanteBtn;

    private final JLabel status;
    private final PartidasView partidas;
    private final Runnable perfilDesdeBoton;

    private WatchlistView watchlist;
    private PerfilView perfil;
    private LiveNowView liveNow;
    private TechTreeView techTree;
    private RatingsView ratings;
    private CivStatsView civStats;
    private DirectosView directos;
    private JPanel centroCards;
    private JSplitPane splitPrincipal;
    private int ttDivisorPrevio = -1;

    /** @param status la barra de estado (la pinta actualizarControlesTabla, no la construye este objeto).
     *  @param partidas la pestaña Partidas: llega ya construida porque, como {@code status}, es un
     *  inicializador de campo de la ventana (se crea antes de que corra el cuerpo del constructor).
     *  @param perfilDesdeBoton qué hacer cuando se pulsa la pestaña «Perfil» directamente (decide qué jugador
     *  abrir; sigue viviendo en la ventana porque no es navegación, es elegir a quién navegar). */
    public Navegador(JLabel status, PartidasView partidas, Runnable perfilDesdeBoton) {
        this.status = status;
        this.partidas = partidas;
        this.perfilDesdeBoton = perfilDesdeBoton;
        estado.agregarOyente(this::actualizarBotonesHistorial);
    }

    /** Enchufa el resto de vistas y el contenedor de cards, que la ventana crea tarde (CableadoCentro.construirCentro/
     *  CableadoCromo.montarVentana, después de CableadoCromo.construirBarraSuperior): ver el javadoc de la clase para el porqué. */
    public void conectarVistas(WatchlistView watchlist, PerfilView perfil, LiveNowView liveNow,
            TechTreeView techTree, RatingsView ratings, CivStatsView civStats, DirectosView directos,
            JPanel centroCards, JSplitPane splitPrincipal) {
        this.watchlist = watchlist;
        this.perfil = perfil;
        this.liveNow = liveNow;
        this.techTree = techTree;
        this.ratings = ratings;
        this.civStats = civStats;
        this.directos = directos;
        this.centroCards = centroCards;
        this.splitPrincipal = splitPrincipal;
    }

    /** «Partidas» está marcada cuando ninguna otra vista lo está. */
    void sincronizarPestanas(boolean tablaVisible) {
        if (recsBtn == null) return;
        boolean otra = (directosBtn != null && directosBtn.isSelected()) || (techTreeBtn != null && techTreeBtn.isSelected())
                || (ladderBtn != null && ladderBtn.isSelected()) || (civStatsBtn != null && civStatsBtn.isSelected()) || (perfilBtn != null && perfilBtn.isSelected()) || (ahoraBtn != null && ahoraBtn.isSelected());
        if (recsBtn.isSelected() == otra) recsBtn.setSelected(!otra);
    }

    /** Visibilidad de la fila de nota, el botón «Mostrar resultados» y el resto de controles de la tabla de
     *  Partidas, según qué pestaña esté activa. */
    public void actualizarControlesTabla() {
        boolean tablaVisible = partidas.recsCards != null && partidas.recsCards.isShowing() && !(directosBtn != null && directosBtn.isSelected()) && !(techTreeBtn != null && techTreeBtn.isSelected()) && !(ladderBtn != null && ladderBtn.isSelected()) && !(civStatsBtn != null && civStatsBtn.isSelected()) && !(perfil != null && perfil.abierto()) && !(liveNow != null && liveNow.ahoraAbierta);
        boolean hay = tablaVisible && partidas.hayPartidas();
        if (partidas.filaNota != null) partidas.filaNota.setVisible(tablaVisible);
        if (partidas.filaBotonesInferiores != null) partidas.filaBotonesInferiores.setVisible(tablaVisible);
        status.setVisible(tablaVisible || (directosBtn != null && directosBtn.isSelected()));   // los mensajes de estado, solo donde se usan
        sincronizarPestanas(tablaVisible);
        if (partidas.resultadosBtn != null) partidas.resultadosBtn.setVisible(hay);
        if (partidas.parModo != null) partidas.parModo.setVisible(hay);
        if (partidas.parRival != null) partidas.parRival.setVisible(hay);
    }

    /** Ratings (card "ladder"): la vista y el presentador viven en ui.RatingsView/ui.RatingsPresenter;
     *  aqui solo queda el cromo (botones, historial, CardLayout), igual que el resto de vistas de la fase 3. */
    @Override public void abrirLadder() {
        estado.registrarDestino(new AppState.Destino("ladder", 0, null, null));
        if (ladderBtn != null && !ladderBtn.isSelected()) ladderBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        if (perfil != null) perfil.cerrar();
        if (perfilBtn != null) perfilBtn.setSelected(false);
        if (liveNow != null) liveNow.marcarCerrada(); if (ahoraBtn != null) ahoraBtn.setSelected(false);
        if (techTreeBtn != null && techTreeBtn.isSelected()) cerrarTechTree();
        ((CardLayout) centroCards.getLayout()).show(centroCards, "ladder");
        ratings.alAbrirAntes();
        partidas.taparResultados(); watchlist.apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        ratings.alAbrirDespues();
    }

    @Override public void abrirCivStats() {
        estado.registrarDestino(new AppState.Destino("civstats", 0, null, null));
        if (civStatsBtn != null && !civStatsBtn.isSelected()) civStatsBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (perfil != null) perfil.cerrar();
        if (perfilBtn != null) perfilBtn.setSelected(false);
        if (liveNow != null) liveNow.marcarCerrada(); if (ahoraBtn != null) ahoraBtn.setSelected(false);
        if (techTreeBtn != null && techTreeBtn.isSelected()) cerrarTechTree();
        ((CardLayout) centroCards.getLayout()).show(centroCards, "civstats");
        civStats.subirArriba();
        partidas.taparResultados(); watchlist.apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        civStats.alAbrir();
    }

    /** Abre el perfil de un jugador; pid 0 = página vacía con el buscador. El cromo (botones, CardLayout,
     *  historial) vive aquí; la carga y la pintura son de ui.PerfilView (perfil.alAbrir). Nota: a diferencia
     *  del resto de abrir*, esta no llama a registrarDestino: lo hace PerfilView.alAbrir, al final de este
     *  método, y solo si pid > 0. */
    @Override public void abrirPerfil(long pid, String nombre) {
        if (perfilBtn != null && !perfilBtn.isSelected()) perfilBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        if (techTreeBtn != null && techTreeBtn.isSelected()) cerrarTechTree();
        if (liveNow != null) liveNow.marcarCerrada(); if (ahoraBtn != null) ahoraBtn.setSelected(false);
        perfil.marcarAbierta();
        ((CardLayout) centroCards.getLayout()).show(centroCards, "perfil");
        subirArriba(perfil.panel());
        partidas.taparResultados(); watchlist.apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        perfil.alAbrir(pid, nombre);
    }

    /** Abre a un jugador en una pestaña NUEVA (Ctrl+clic, «+»); la cuenta de pestañas es de ui.PerfilView. */
    @Override public void abrirPerfilEnPestana(long pid, String nombre) { perfil.abrirEnPestanaNueva(pid, nombre); }

    @Override public void abrirAhora() {
        estado.registrarDestino(new AppState.Destino("ahora", 0, null, null));
        if (ahoraBtn != null && !ahoraBtn.isSelected()) ahoraBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        if (techTreeBtn != null && techTreeBtn.isSelected()) cerrarTechTree();
        if (perfil != null) perfil.cerrar();
        if (perfilBtn != null) perfilBtn.setSelected(false);
        liveNow.marcarAbierta();
        ((CardLayout) centroCards.getLayout()).show(centroCards, "ahora");
        liveNow.alAbrirAntes();
        partidas.taparResultados(); watchlist.apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        liveNow.alAbrirDespues();
    }

    /** Abre el panel (plegando la watchlist) y, si se pide, en una civ concreta. */
    @Override public void abrirTechTree(String civ) {
        estado.registrarDestino(new AppState.Destino("techtree", 0, null, civ != null ? civ : techTree.civPedida()));
        if (techTreeBtn != null && !techTreeBtn.isSelected()) techTreeBtn.setSelected(true);
        directosBtn.setSelected(false);
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        if (perfil != null) perfil.cerrar();
        if (perfilBtn != null) perfilBtn.setSelected(false);
        if (liveNow != null) liveNow.marcarCerrada(); if (ahoraBtn != null) ahoraBtn.setSelected(false);
        ((CardLayout) centroCards.getLayout()).show(centroCards, "techtree");
        partidas.taparResultados(); watchlist.apagarForma();
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        if (splitPrincipal != null && ttDivisorPrevio < 0) {   // la watchlist se pliega: el árbol necesita el ancho
            ttDivisorPrevio = splitPrincipal.getDividerLocation();
            splitPrincipal.setOneTouchExpandable(true);
            splitPrincipal.setDividerLocation(0);
        }
        techTree.alAbrir(civ);
    }

    public void cerrarTechTree() {
        techTree.ocultarDetalle();
        if (techTreeBtn != null) techTreeBtn.setSelected(false);
        if (splitPrincipal != null && ttDivisorPrevio >= 0) {   // la watchlist vuelve a su ancho
            splitPrincipal.setDividerLocation(ttDivisorPrevio);
            SwingUtilities.invokeLater(() -> { if (watchlist.norteWatchRef != null) { watchlist.norteWatchRef.revalidate(); watchlist.norteWatchRef.repaint(); } });   // las filas del norte se recalculan con el ancho ya restaurado
            splitPrincipal.setOneTouchExpandable(false);
            ttDivisorPrevio = -1;
        }
        mostrarDirectos(false);
    }

    /** La zona central alterna entre la tabla de recs y los directos. */
    public void mostrarDirectos(boolean mostrar) {
        directosBtn.setSelected(mostrar);
        if (techTreeBtn != null && techTreeBtn.isSelected()) { techTreeBtn.setSelected(false); }
        if (ladderBtn != null) ladderBtn.setSelected(false);
        if (civStatsBtn != null) civStatsBtn.setSelected(false);
        if (perfil != null) perfil.cerrar();
        if (perfilBtn != null) perfilBtn.setSelected(false);
        if (liveNow != null) liveNow.marcarCerrada(); if (ahoraBtn != null) ahoraBtn.setSelected(false);
        if (splitPrincipal != null && ttDivisorPrevio >= 0) { SwingUtilities.invokeLater(() -> { if (watchlist.norteWatchRef != null) { watchlist.norteWatchRef.revalidate(); watchlist.norteWatchRef.repaint(); } }); splitPrincipal.setDividerLocation(ttDivisorPrevio); splitPrincipal.setOneTouchExpandable(false); ttDivisorPrevio = -1; }
        if (mostrar) { partidas.taparResultados(); watchlist.apagarForma(); }   // cambiar de pantalla apaga el modo consulta y la forma
        ((CardLayout) centroCards.getLayout()).show(centroCards, mostrar ? "directos" : "recs");
        if (!mostrar && !partidas.hayPartidas() && partidas.fetchWorker == null) partidas.mostrarGuiaVacia(true);   // sin partidas: la guía con su botón, no una tabla vacía
        estado.registrarDestino(new AppState.Destino(mostrar ? "directos" : "recs", 0, null, null));
        SwingUtilities.invokeLater(this::actualizarControlesTabla);
        if (mostrar) directos.alAbrir();
    }

    private void irA(AppState.Destino d) {
        switch (d.vista()) {
            case "directos" -> mostrarDirectos(true);
            case "ladder" -> abrirLadder();
            case "civstats" -> abrirCivStats();
            case "ahora" -> abrirAhora();
            case "techtree" -> abrirTechTree(d.civ());
            case "perfil" -> { perfil.antesDeVolverPorHistorial(d.pid()); abrirPerfil(d.pid(), d.nombre()); }   // F10: pestaña cerrada → pestaña nueva
            default -> mostrarDirectos(false);
        }
    }

    /** Vuelve a la vista anterior (perfil, civ del tech tree, Civ Stats…). */
    public void volverAtras() {
        AppState.Destino d = estado.prepararAtras();
        if (d == null) return;
        try { irA(d); } finally { estado.dejarDeNavegar(); }
        actualizarBotonesHistorial();
    }

    public void irAdelante() {
        AppState.Destino d = estado.prepararAdelante();
        if (d == null) return;
        try { irA(d); } finally { estado.dejarDeNavegar(); }
        actualizarBotonesHistorial();
    }

    void actualizarBotonesHistorial() {
        if (atrasBtn != null) atrasBtn.setEnabled(estado.puedeVolver());
        if (adelanteBtn != null) adelanteBtn.setEnabled(estado.puedeAvanzar());
    }

    /** Una pestaña de la barra de vistas: estilo «tab» de FlatLaf (subrayado en la activa), icono y sin foco. */
    public static JToggleButton pestana(String texto, Icon icono) {
        JToggleButton b = new JToggleButton(texto, icono);
        b.setFocusable(false);
        b.setIconTextGap(6);
        b.putClientProperty("JButton.buttonType", "tab");
        b.setMargin(new Insets(4, 10, 4, 10));
        return b;
    }

    /** Iconos vectoriales de las pestañas (16 px, en el color del texto): lista, punto en directo, persona, campana, barras. */
    public static Icon iconoVista(String tipo) {
        return new Icon() {
            @Override public int getIconWidth() { return 16; }
            @Override public int getIconHeight() { return 16; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color col = c.getForeground() != null ? c.getForeground() : Color.GRAY;
                g2.setColor(col);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (tipo) {
                    case "partidas" -> { for (int i = 0; i < 3; i++) g2.drawLine(x + 2, y + 3 + i * 5, x + 14, y + 3 + i * 5); }
                    case "directos" -> { g2.fillOval(x + 5, y + 5, 6, 6); g2.drawOval(x + 1, y + 1, 14, 14); }
                    case "ahora" -> { g2.drawOval(x + 1, y + 1, 14, 14); g2.drawLine(x + 8, y + 4, x + 8, y + 8); g2.drawLine(x + 8, y + 8, x + 11, y + 10); }
                    case "campana" -> { g2.drawArc(x + 3, y + 2, 10, 12, 0, 180); g2.drawLine(x + 3, y + 8, x + 3, y + 12); g2.drawLine(x + 13, y + 8, x + 13, y + 12); g2.drawLine(x + 1, y + 12, x + 15, y + 12); g2.fillOval(x + 6, y + 13, 4, 3); }
                    case "perfil" -> { g2.drawOval(x + 5, y + 1, 6, 6); g2.drawArc(x + 2, y + 8, 12, 12, 0, 180); }
                    case "ratings" -> { java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double(); p.moveTo(x + 1, y + 14); p.curveTo(x + 6, y + 14, x + 6, y + 2, x + 8, y + 2); p.curveTo(x + 10, y + 2, x + 10, y + 14, x + 15, y + 14); g2.draw(p); }
                    case "civstats" -> { g2.fillRect(x + 2, y + 9, 3, 6); g2.fillRect(x + 7, y + 4, 3, 11); g2.fillRect(x + 12, y + 7, 3, 8); }
                    default -> g2.drawRect(x + 2, y + 2, 12, 12);
                }
                g2.dispose();
            }
        };
    }

    /** La barra de pestañas (Partidas/Twitch/Live now/Perfil/Ratings/Civ Stats/Tech tree) y las flechas de
     *  atrás/adelante: mismo panel, mismo orden y mismos listeners que en la 1.1; CableadoCromo.construirBarraSuperior
     *  la llama justo donde antes se construía el bloque inline. */
    public JPanel construirFilaVistas() {
        JPanel filaVistas = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 0));
        atrasBtn = new JButton("\u2190");
        atrasBtn.setFocusable(false); atrasBtn.setMargin(new Insets(2, 8, 2, 8)); atrasBtn.putClientProperty("JButton.buttonType", "roundRect");
        atrasBtn.setToolTipText(t("Atrás: vuelve a la vista anterior (también el botón lateral del ratón)", "Back: return to the previous view (also the mouse's back button)"));
        atrasBtn.setEnabled(false);
        atrasBtn.addActionListener(e -> volverAtras());
        filaVistas.add(atrasBtn);
        adelanteBtn = new JButton("\u2192");
        adelanteBtn.setFocusable(false); adelanteBtn.setMargin(new Insets(2, 8, 2, 8)); adelanteBtn.putClientProperty("JButton.buttonType", "roundRect");
        adelanteBtn.setToolTipText(t("Adelante (también el botón lateral del ratón)", "Forward (also the mouse's forward button)"));
        adelanteBtn.setEnabled(false);
        adelanteBtn.addActionListener(e -> irAdelante());
        filaVistas.add(adelanteBtn);
        recsBtn = pestana(t("Partidas", "Games"), iconoVista("partidas"));
        recsBtn.addActionListener(e -> {   // desde un perfil: la tabla pasa a ser de ese jugador (búsqueda de sus partidas recientes), salvo que ya lo sea
            if (perfil.pidAbierto() > 0 && partidas.objetivoEtiqueta != null && partidas.objetivoEtiqueta.id() == perfil.pidAbierto() && perfil.historialEnTabla() != perfil.pidAbierto() && (partidas.ultimosSujetos.size() != 1 || partidas.ultimosSujetos.get(0).id() != perfil.pidAbierto()) && partidas.fetchWorker == null)
                SwingUtilities.invokeLater(() -> partidas.fetchMatches(partidas.fetchBtn));
        });
        recsBtn.setSelected(true);
        recsBtn.setToolTipText(t("La tabla de partidas de tu watchlist (recs sin spoilers)", "Your watchlist's games table (spoiler-free recs)"));
        recsBtn.addActionListener(e -> { if (!recsBtn.isSelected()) { recsBtn.setSelected(true); return; } mostrarDirectos(false); });
        filaVistas.add(recsBtn);
        directosBtn = pestana("Twitch", iconoVista("directos"));
        directosBtn.setToolTipText(t("Todos los canales dando AoE2 en Twitch ahora mismo",
                "Every channel streaming AoE2 on Twitch right now"));
        directosBtn.addActionListener(e -> { if (!directosBtn.isSelected()) { directosBtn.setSelected(true); return; } mostrarDirectos(true); });
        ahoraBtn = pestana("Live now", iconoVista("ahora"));
        ahoraBtn.setToolTipText(t("Partidas en curso de los 250 mejores del ladder 1v1, en vivo: bandos, mapa, reloj, Twitch, espectar; y las terminadas en las últimas 2 h con su rec", "Ongoing games of the top 250 of the 1v1 ladder, live: sides, map, clock, Twitch, spectate; and those finished in the last 2 h with their rec"));
        ahoraBtn.addActionListener(e -> { if (!ahoraBtn.isSelected()) { ahoraBtn.setSelected(true); return; } abrirAhora(); });
        filaVistas.add(ahoraBtn);
        filaVistas.add(directosBtn);
        perfilBtn = pestana(t("Perfil", "Profile"), iconoVista("perfil"));
        perfilBtn.setToolTipText(t("Perfil y actividad del último año del jugador seleccionado en la watchlist (o de cualquier nick): ELO, forma, calendario, civs, mapas y rivales",
                "Profile and last year's activity of the player selected in the watchlist (or any nick): ELO, form, calendar, civs, maps and rivals"));
        perfilBtn.addActionListener(e -> { if (!perfilBtn.isSelected()) { perfilBtn.setSelected(true); return; } perfilDesdeBoton.run(); });
        filaVistas.add(perfilBtn);
        ladderBtn = pestana("Ratings", iconoVista("ratings"));
        ladderBtn.setToolTipText(t("Distribución de ELO por ladder, percentiles, dispersión 1v1 × equipos y comparador de jugadores (volcado diario de aoe2companion)",
                "ELO distribution per ladder, percentiles, 1v1 × team scatter and player comparison (aoe2companion daily dump)"));
        ladderBtn.addActionListener(e -> { if (!ladderBtn.isSelected()) { ladderBtn.setSelected(true); return; } abrirLadder(); });
        filaVistas.add(ladderBtn);
        civStatsBtn = pestana("Civ Stats", iconoVista("civstats"));
        civStatsBtn.setToolTipText(t("Winrate y pick rate por civilización, mapa y tramo de ELO; matchups y tendencias (volcados diarios de aoe2companion)",
                "Win rate and pick rate by civilization, map and ELO bracket; matchups and trends (aoe2companion daily dumps)"));
        civStatsBtn.addActionListener(e -> { if (!civStatsBtn.isSelected()) { civStatsBtn.setSelected(true); return; } abrirCivStats(); });
        filaVistas.add(civStatsBtn);
        techTreeBtn = pestana("Tech tree", TechTreeView.iconoBoton());
        techTreeBtn.setIconTextGap(6);
        techTreeBtn.setToolTipText(t("Árbol tecnológico de cada civilización (datos de aoe2techtree, se actualizan solos)",
                "Every civilization's tech tree (data from aoe2techtree, updates itself)"));
        techTreeBtn.addActionListener(e -> { if (!techTreeBtn.isSelected()) { techTreeBtn.setSelected(true); return; } abrirTechTree(null); });
        filaVistas.add(techTreeBtn);
        filaVistas.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(128, 128, 128, 70)));
        return filaVistas;
    }
}
