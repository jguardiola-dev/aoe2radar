package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.AzarService;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.service.RecService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static dev.tirador.aoe2radar.service.ReglasPartida.marcarFantasmas;
import static dev.tirador.aoe2radar.service.ReglasPartida.rivalCoincide;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Texto.normalizarNick;

/**
 * La pestaña «Partidas»: la tabla de recs sin spoilers, la cabecera «Partidas de:», los filtros (modo, mapa,
 * periodo, rival), la descarga a disco/savegame, y «Al azar por ELO…»/«Guess the ELO!» (cuyo muestreo ya vive
 * en {@link AzarService}: aquí solo queda escribir el resultado en la tabla, igual que fetchMatches/download).
 * Movida tal cual desde SpoilerFreeRecs (fase 3, tanda 3, oleada B).
 * <p>
 * Desde la 1.3 esta clase es la FACHADA: conserva la API, las interfaces y el estado que leen la ventana y los tests,
 * y delega el trabajo en {@link PartidasTabla} (+ {@link MatchesTableModel}), {@link MenuPartida},
 * {@link CabeceraSujetos}, {@link DescargasPartidas} y {@link BusquedasPartidas}, que la reciben y leen su estado.
 * <p>
 * fetchMatches/download/buscarAleatorias/buscarGte SIGUEN siendo {@code SwingWorker} (ahora en BusquedasPartidas y
 * DescargasPartidas; la tanda lo permite
 * explícitamente: reescribirlos con {@code Tareas} cambiaría cuándo se pinta cada trozo de publish/process).
 * Lo que sí se aisló en {@link PartidasPresenter}, sin Swing, es {@code vigente()} (la comprobación de caducidad
 * por opSerial que cada uno hace al terminar, antes de decidir qué pintar) y los tres filtros de la tabla.
 * <p>
 * La Watchlist (ui.WatchlistView) se pide por {@link EnlaceWatchlist}, que cablea la ventana. El resto de la ventana
 * (navegación, semáforo de operación en curso, perfil abierto, red) llega por {@link Anfitrion}.
 */
public final class PartidasView {

    /** Lo que Partidas necesita de la Watchlist (ui.WatchlistView; la ventana lo cablea). Nombres de negocio: la
     *  vista no conoce playersList/playersModel/eloWatch, solo lo que puede hacer con ellos. */
    public interface EnlaceWatchlist {
        List<Player> seleccion();
        int seleccionSize();
        boolean soloVivosMarcado();
        boolean modoTop();
        String grupoDestino();
        List<Player> conFamilias(List<Player> base);
        void limpiarSeleccion();
        int totalJugadores();
        Player jugador(int indice);
        Integer eloDe(long pid);
        String grupoDeJugador(long pid);
        List<Player> todosJugadores();
        void actualizarIndicadoresVivos();
        void aplicarFiltroGrupo();
        /** Tooltip de «cuenta hermana» de la columna Jugador (delegado en service.Familias, vía Watchlist). */
        String tipCuentaVinculada(Match m);
        Player objetivoForzado();
        void fijarObjetivoForzado(Player p);
        void limpiarObjetivoForzado();
        Player invitado();
        void limpiarInvitado();
        /** Identidad de la vista actual de la watchlist (grupo|país): para saber si la cabecera «Partidas de:»
         *  sigue hablando de la misma vista. */
        String vistaActualId();
        int horasVentana();
        void guardarVentanaHoras();
        /** El chip «Forma» (±ELO reciente) actualiza su texto cuando cambia a quién se busca. */
        void actualizarTextoForma();
        /** El panel fijo sobre la watchlist donde vive la cabecera «Partidas de:» (se construye dentro de
         *  construirWatchlist, antes de que esta vista exista; se pide por aquí para no duplicarlo). */
        JPanel sujetosPanel();
    }

    /** Lo que Partidas necesita del resto de la ventana (cromo, perfil, red) que no es la Watchlist. */
    public interface Anfitrion {
        void estado(String texto);
        void mostrarDirectos(boolean mostrar);
        void refrescarDirectos();
        void enfocarBuscador();
        boolean confirmarEspectar(String nombre);
        void espectarVerificando(long profileId, long matchId);
        void espectarPartida(long matchId);
        void lanzarCaptureAge(Path rec);
        void abrirUrl(String url);
        /** cache.Anotaciones.nombreVisible: ui no puede importar cache, así que llega envuelto (mismo patrón
         *  que ya usan PerfilView/LiveNowView/RatingsView). */
        String nombreVisible(long pid, String nombre);
        String paisDe(long pid);
        boolean enCursoReal(Match m);
        Path destino(Match m);
        Path recsDir();
        void trabajando(boolean on);
        long operacionActual();
        boolean detenido();
        void pararOperacion();
        void anotarHiloOperacion();
        void aprenderCatalogos(List<Match> res);
        List<String> mapasConocidos();
        List<String> civsConocidas();
        void dormir(long ms);
        long perfilAbiertoPid();
        boolean perfilAbierto();
        String perfilNombreAbierto();
        void mostrarHistorialSiSigueAbierto(long pid, String nombre);
        /** La única llamada de red que queda aquí (CompanionApi.partidas): ui no puede importar api. */
        Iterable<Match> paginaDePartidas(long pid, int pagina, int porPagina) throws IOException, InterruptedException;
        boolean autoCopiarAlDescargar();
        void continuarDisponible(boolean visible);
        /** La nota sin-spoilers vuelve a su gris de siempre (delegado en ui.TemaApp, que conoce toda la ventana). */
        void ajustarGrisesNota(boolean oscuro);
        /** Visibilidad de nota/botones/parModo/parRival según la pestaña activa (cromo, toca varias vistas). */
        void actualizarControlesTabla();
    }

    final JFrame ventana;
    final MenusJugador menus;
    final DialogosJugador dialogos;
    final Navegacion navegacion;
    final AzarService azarService;
    final RecService recService;
    final BarridoVivos barridoVivos;
    final int perPage;
    final long pausaMs;
    final EnlaceWatchlist enlaceWatchlist;
    final Anfitrion anfitrion;
    final PartidasTexto texto;
    /** Las piezas en que se parte la vista (1.3): reciben esta fachada y leen su estado por ella. */
    final PartidasTabla tabla;
    final MenuPartida menuPartida;
    final CabeceraSujetos cabecera;
    final DescargasPartidas descargas;
    final BusquedasPartidas busquedas;

    /** Los buscados actuales: negrita en la tabla y cabecera «Partidas de:». Estático porque azar/GTE y
     *  {@code enfrentamiento()} lo comparten, igual que en la 1.1 (antes vivía en SpoilerFreeRecs). */
    public static final Set<Long> SUJETOS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ----- Campos de la tabla y su estado -----
    public final List<Match> all = new ArrayList<>();    // todo lo consultado
    public final List<Match> view = new ArrayList<>();   // lo que pasa los filtros (lo que ve la tabla)
    public final MatchesTableModel tableModel = new MatchesTableModel(this);   // visible para RegresionCapturas
    public JTable table;   // se asigna en construirTabla()

    public final JButton dlSel = new JButton(t("Descargar seleccionadas", "Download selected"));
    public final JButton dlAll = new JButton(t("Descargar todas", "Download all"));
    public final JButton fetchBtn = new JButton(t("Buscar partidas", "Search games"));
    public final JButton azarBtn = new JButton(t("Al azar por ELO…", "Random by ELO…"));
    public final JButton gteBtn = new JButton("Guess the ELO!");
    final JComboBox<String> modeCombo = new JComboBox<>(new String[]{ todosModos() });
    final JComboBox<String> mapaCombo = new JComboBox<>(new String[]{ t("Todos los mapas", "All maps") });
    final JComboBox<String> periodoCombo = new JComboBox<>(new String[]{ t("Todo", "All"), t("7 días", "7 days"), t("30 días", "30 days"), t("90 días", "90 days"), t("365 días", "365 days") });
    boolean actualizandoCombos = false;
    javax.swing.JTextField rivalField;
    public JPopupMenu rivalPopup;
    public String filtroRival = "";
    public JPanel parModo, parRival;

    public JPanel recsCards;   // «tabla» o «guia»
    public JButton guiaBtn;

    public JPanel filaNota, filaBotonesInferiores;
    public JLabel nota;
    public JToggleButton resultadosBtn;
    public boolean mostrarResultados;   // modo consulta: siempre renace apagado
    public final Set<Long> reveladas = new HashSet<>();   // ojos abiertos fila a fila

    public JButton todasPerfilBtn;
    public List<Player> ultimosSujetos = List.of();
    public final Set<Long> filtroSujetos = new HashSet<>();
    public String vistaDeSujetos = "";   // visible para WatchlistView.aplicarFiltroGrupo, vía su EnlacePartidas
    public Player objetivoEtiqueta;   // el jugador que nombra el botón «Buscar partidas (X)»

    public volatile SwingWorker<?, ?> fetchWorker;

    public PartidasView(JFrame ventana, MenusJugador menus, DialogosJugador dialogos, Navegacion navegacion,
                         AzarService azarService, RecService recService, BarridoVivos barridoVivos,
                         int perPage, long pausaMs, EnlaceWatchlist enlaceWatchlist, Anfitrion anfitrion) {
        this.ventana = ventana;
        this.menus = menus;
        this.dialogos = dialogos;
        this.navegacion = navegacion;
        this.azarService = azarService;
        this.recService = recService;
        this.barridoVivos = barridoVivos;
        this.perPage = perPage;
        this.pausaMs = pausaMs;
        this.enlaceWatchlist = enlaceWatchlist;
        this.anfitrion = anfitrion;
        this.texto = new PartidasTexto(anfitrion::nombreVisible);
        this.tabla = new PartidasTabla(this);
        this.menuPartida = new MenuPartida(this);
        this.cabecera = new CabeceraSujetos(this);
        this.descargas = new DescargasPartidas(this);
        this.busquedas = new BusquedasPartidas(this);
    }

    static String todosModos() { return t("Todos los modos", "All modes"); }

    /** El panel de la pestaña, para el CardLayout de la ventana (card "recs": tabla + guía). */
    public JPanel panel() { return recsCards; }

    // ======================================================================
    // Construcción (llamada por la ventana en el mismo orden que hoy)
    // ======================================================================

    /** La parte de consulta de la fila 1 de la barra superior: modo, rival (con sugerencias), mapa, periodo,
     *  el botón «Buscar partidas» y, a continuación, «Al azar por ELO…»/«Guess the ELO!». La ventana la llama
     *  justo donde antes seguía construyendo fila1 a mano (después de «Últimas N horas»). */
    public void agregarFilaConsulta(JPanel fila1) {
        fetchBtn.addActionListener(e -> busquedas.alternar(fetchBtn));   // el botón (y Enter) alterna Buscar/Detener
        modeCombo.setPrototypeDisplayValue("RM Team MegaRandom XL");
        modeCombo.addActionListener(e -> { if (!actualizandoCombos) applyFilters(); });
        parModo = par(new JLabel(t("Modo:", "Mode:")), modeCombo);
        fila1.add(parModo);
        rivalField = new javax.swing.JTextField(11);
        rivalField.putClientProperty("JTextField.placeholderText", t("Rival…", "Opponent…"));
        rivalField.putClientProperty("JTextField.showClearButton", true);
        rivalField.setToolTipText(t("Filtra la tabla por el rival (en equipos, cualquiera del equipo contrario). Escribe para ver sugerencias.",
                "Filters the table by opponent (in team games, anyone on the other team). Type to see suggestions."));
        rivalPopup = new JPopupMenu();
        rivalPopup.setFocusable(false);
        rivalField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void cambio() { filtroRival = normalizarNick(rivalField.getText()); applyFilters(); sugerirRivales(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { cambio(); }
        });
        rivalField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (!rivalPopup.isVisible() || rivalPopup.getComponentCount() == 0) return;
                if (e.getKeyCode() == KeyEvent.VK_DOWN) { ((JMenuItem) rivalPopup.getComponent(0)).doClick(); e.consume(); }
                else if (e.getKeyCode() == KeyEvent.VK_ENTER) { ((JMenuItem) rivalPopup.getComponent(0)).doClick(); e.consume(); }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) rivalPopup.setVisible(false);
            }
        });
        rivalField.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { rivalPopup.setVisible(false); } });
        parRival = par(new JLabel(t("Rival:", "Opponent:")), rivalField);
        fila1.add(parRival);
        mapaCombo.setToolTipText(t("Filtra la tabla por mapa (sobre las partidas cargadas, sin llamadas)", "Filters the table by map (over the loaded games, no requests)"));
        periodoCombo.setToolTipText(t("Filtra la tabla por fecha de la partida (sobre las partidas cargadas)", "Filters the table by game date (over the loaded games)"));
        mapaCombo.addActionListener(e -> { if (!actualizandoCombos) applyFilters(); });
        periodoCombo.addActionListener(e -> { if (!actualizandoCombos) applyFilters(); });
        fila1.add(par(new JLabel(t("Mapa:", "Map:")), mapaCombo));
        fila1.add(par(new JLabel(t("Periodo:", "Period:")), periodoCombo));
        fila1.add(fetchBtn);

        azarBtn.setToolTipText(t("Hasta 10 partidas 1v1 recientes del ladder con ambos jugadores en el rango de ELO elegido", "Up to 10 recent 1v1s from the ladder with both players inside your ELO range"));
        azarBtn.addActionListener(e -> buscarAleatorias());
        fila1.add(azarBtn);
        gteBtn.setToolTipText(t("5 partidas 1v1 recientes de cualquier ELO, anónimas: adivina el ELO y compruébalo con «Revelar resultado…»", "5 recent anonymous 1v1s from any ELO: guess the ELO, then check with “Reveal result…”"));
        gteBtn.addActionListener(e -> buscarGte());
        fila1.add(gteBtn);
    }

    /** Panel de una fila con las etiquetas y componentes dados (copia local del helper de la ventana: es una
     *  pieza trivial de layout usada por varias áreas y esta oleada no reordena utilidades compartidas). */
    private static JPanel par(Component... cs) {
        JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        for (Component c : cs) p.add(c);
        return p;
    }

    /** «Mostrar resultados», con la nota sin-spoilers: la fila 2 de la barra superior. La ventana la añade al
     *  panel «top» justo donde antes construía fila2 a mano. */
    public JPanel construirFilaNota() {
        JPanel fila2 = new JPanel(new BorderLayout(8, 0));
        fila2.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        nota = new JLabel(t("Sin spoilers: nunca se muestra ganador, ±ELO ni duración. Doble clic en una fila = descargar.", "Spoiler-free: winner, ±ELO and duration are never shown. Double-click a row = download."));
        resultadosBtn = new JToggleButton(t("Mostrar resultados", "Show results"));
        resultadosBtn.setFocusable(false);
        resultadosBtn.setToolTipText(t("Modo consulta: el ganador de cada partida en dorado (±ELO y duración en el tooltip). Siempre arranca apagado.",
                "Lookup mode: each game's winner in gold (±ELO and duration in the tooltip). Always starts off."));
        resultadosBtn.addActionListener(e -> {
            mostrarResultados = resultadosBtn.isSelected();
            actualizarNotaSpoilers();
            tableModel.fireTableDataChanged();
        });
        fila2.add(nota, BorderLayout.CENTER);
        fila2.add(resultadosBtn, BorderLayout.EAST);
        filaNota = fila2;
        return fila2;
    }

    /** El JTable completo (renderers, anchos, orden de columnas, atajos y menú contextual): vive en
     *  {@link PartidasTabla}. La ventana la llama donde antes llamaba a construirTablaPartidas(). */
    public void construirTabla() { tabla.construirTabla(); }

    /** Las cards «tabla»/«guia» de la zona central de Partidas. CableadoCentro la llama donde antes montaba
     *  recsCards dentro de construirCentro, justo antes de crear Directos. */
    public void construirCards() {
        recsCards = new JPanel(new java.awt.CardLayout());
        recsCards.add(new JScrollPane(table), "tabla");
        JPanel guia = new JPanel(new GridBagLayout());
        JLabel guiaTxt = new JLabel("<html><div style='text-align:center'>"
                + "<b>" + t("Así funciona", "How it works") + "</b><br><br>"
                + t("1. Elige jugadores en la lista de la izquierda, o escribe un nick en el buscador.", "1. Pick players in the list on the left, or type a nick in the search box.") + "<br>"
                + t("2. Pulsa <b>Buscar partidas</b>.", "2. Press <b>Search games</b>.") + "<br>"
                + t("3. Doble clic en una partida para descargarla — sin spoilers.", "3. Double-click a game to download it — spoiler-free.")
                + "</div></html>");
        guiaTxt.setHorizontalAlignment(SwingConstants.CENTER);
        guiaBtn = new JButton(t("Buscar partidas", "Search games"));
        guiaBtn.addActionListener(e -> fetchBtn.doClick());
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0; gc.insets = new Insets(0, 0, 14, 0);
        guia.add(guiaTxt, gc);
        gc.gridy = 1; gc.insets = new Insets(0, 0, 0, 0);
        guia.add(guiaBtn, gc);
        recsCards.add(guia, "guia");
        mostrarGuiaVacia(true);
    }

    /** Los botones de descarga/carpeta/perfil de la franja inferior. CableadoCromo.construirBarraInferior la
     *  llama donde antes montaba btns1/btns2, y coloca lo que devuelve en el NORTE de «bottom». */
    public JPanel construirBotonesInferiores() {
        JButton abrir  = new JButton(t("Abrir carpeta recs descargadas", "Open downloaded recs folder"));
        JButton vaciar = new JButton(t("Vaciar recs", "Empty recs folder"));
        dlSel.addActionListener(e -> download(selectedRows()));
        dlAll.addActionListener(e -> download(allRows()));
        abrir.addActionListener(e -> abrirCarpeta());
        vaciar.addActionListener(e -> vaciarRecs());
        JPanel btns1 = new JPanel(new WrapLayout(java.awt.FlowLayout.LEFT, 8, 2));
        JButton carpetasBtn = new JButton(t("Carpetas \u25BE", "Folders \u25BE"));
        carpetasBtn.setFocusable(false);
        carpetasBtn.addActionListener(e -> {
            JPopupMenu pm = new JPopupMenu();
            JMenuItem i1 = new JMenuItem(abrir.getText());  i1.addActionListener(a -> abrir.doClick());
            JMenuItem i2 = new JMenuItem(abrirSgTxt());     i2.addActionListener(a -> abrirSavegame());
            JMenuItem i3 = new JMenuItem(vaciar.getText()); i3.addActionListener(a -> vaciar.doClick());
            pm.add(i1); pm.add(i2); pm.addSeparator(); pm.add(i3);
            pm.show(carpetasBtn, 0, carpetasBtn.getHeight());
        });
        btns1.add(dlSel); btns1.add(dlAll);

        JButton enviarSg = new JButton(t("Enviar al juego", "Send to game"));
        enviarSg.setToolTipText(t("Envía las recs seleccionadas al juego: las ya descargadas se copian; las que falten se descargan y se envían en la misma acción", "Sends the selected recs to the game: downloaded ones are copied; missing ones are downloaded and sent in one go"));
        enviarSg.addActionListener(e -> enviarInteligente(selectedRows()));
        JPanel btns2 = new JPanel(new WrapLayout(java.awt.FlowLayout.LEFT, 8, 2));
        btns1.add(enviarSg);
        todasPerfilBtn = new JButton(t("Todas las partidas del perfil", "All games of the profile"));
        todasPerfilBtn.setFocusable(false); todasPerfilBtn.putClientProperty("JButton.buttonType", "roundRect");
        todasPerfilBtn.setToolTipText(t("Abre el perfil del jugador buscado con su histórico completo en páginas (del último año, sin llamadas si está en sfr-data)", "Opens the searched player's profile with their full history in pages (last year, no requests when in sfr-data)"));
        todasPerfilBtn.addActionListener(e -> { if (ultimosSujetos.size() == 1) { Player p = ultimosSujetos.get(0); navegacion.abrirPerfil(p.id(), anfitrion.nombreVisible(p.id(), p.name())); anfitrion.mostrarHistorialSiSigueAbierto(p.id(), anfitrion.nombreVisible(p.id(), p.name())); } });
        todasPerfilBtn.setVisible(false);
        btns1.add(todasPerfilBtn);
        btns2.add(carpetasBtn);

        JPanel filasBtns = new JPanel();
        filasBtns.setLayout(new javax.swing.BoxLayout(filasBtns, javax.swing.BoxLayout.Y_AXIS));
        filasBtns.add(btns1);
        filasBtns.add(btns2);
        filaBotonesInferiores = filasBtns;
        return filasBtns;
    }

    // ======================================================================
    // Cabecera «Partidas de:» y sugerencias de rival
    // ======================================================================

    /** La cabecera «Partidas de:» (vive en {@link CabeceraSujetos}). */
    public void refrescarSujetos(List<Player> tracked, boolean esInvitadoIn) { cabecera.refrescarSujetos(tracked, esInvitadoIn); }

    /** Sugerencias: rivales de la búsqueda actual que contienen lo tecleado, con su número de partidas. */
    void sugerirRivales() {
        rivalPopup.setVisible(false);
        rivalPopup.removeAll();
        if (filtroRival.length() < 1 || all.isEmpty()) return;
        Map<String, Integer> cuenta = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Match m : all) {
            if (m.gte > 0) continue;
            asignarRef(m);
            MatchPlayer yo = null;
            for (MatchPlayer p : m.players) if (p.id == m.refId) yo = p;
            for (MatchPlayer p : m.players) {
                if (p.id == m.refId) continue;
                if (yo != null && m.players.size() > 2 && p.team == yo.team) continue;
                String vis = anfitrion.nombreVisible(p.id, p.name);
                if (normalizarNick(vis).contains(filtroRival) || normalizarNick(p.name).contains(filtroRival)) cuenta.merge(vis, 1, Integer::sum);
            }
        }
        if (cuenta.isEmpty()) return;
        List<Map.Entry<String, Integer>> lista = new ArrayList<>(cuenta.entrySet());
        lista.sort((a, b) -> b.getValue() - a.getValue());
        int n = 0;
        for (Map.Entry<String, Integer> en : lista) {
            if (n++ >= 8) break;
            if (normalizarNick(en.getKey()).equals(filtroRival) && lista.size() == 1) return;
            JMenuItem it = new JMenuItem(en.getKey() + "  (" + en.getValue() + ")");
            it.addActionListener(a -> { rivalField.setText(en.getKey()); rivalPopup.setVisible(false); });
            rivalPopup.add(it);
        }
        rivalPopup.show(rivalField, 0, rivalField.getHeight());
    }

    // ======================================================================
    // Revelar / tapar resultados
    // ======================================================================

    boolean revelada(Match m) { return PartidasPresenter.revelada(mostrarResultados, m.gte, reveladas.contains(m.id)); }

    /** Cada tabla nueva nace tapada: se apaga el modo consulta y se cierran los ojos. */
    public void taparResultados() {
        boolean cambia = mostrarResultados || !reveladas.isEmpty();
        mostrarResultados = false;
        reveladas.clear();
        if (resultadosBtn != null && resultadosBtn.isSelected()) resultadosBtn.setSelected(false);
        if (cambia) { actualizarNotaSpoilers(); if (tableModel != null) tableModel.fireTableDataChanged(); }
    }

    void actualizarNotaSpoilers() {
        boolean oscuro = temaOscuroActivo;
        if (mostrarResultados) {
            nota.setText(t("\u2726 Mostrando resultados: Jugador en verde si ganó y rojo si perdió, con su ±ELO; enfrentamiento completo y duración, en el tooltip de la fila.",
                           "\u2726 Showing results: Player in green if they won, red if they lost, with their ±ELO; full matchup and duration in the row tooltip."));
            nota.setForeground(oscuro ? new Color(0xd9, 0xa5, 0x5b) : new Color(0x9a, 0x6b, 0x1f));
        } else {
            nota.setText(t("Sin spoilers: nunca se muestra ganador, ±ELO ni duración. Doble clic en una fila = descargar.",
                           "Spoiler-free: winner, ±ELO and duration are never shown. Double-click a row = download."));
            anfitrion.ajustarGrisesNota(oscuro);
        }
    }

    // ======================================================================
    // Carpeta de recs, savegame, descargas y enviar al juego
    // ======================================================================

    // Viven en {@link DescargasPartidas}; la fachada conserva la API que usan la ventana y las piezas.
    public void abrirCarpeta() { descargas.abrirCarpeta(); }

    public void vaciarRecs() { descargas.vaciarRecs(); }

    public Path obtenerSavegame(boolean interactivo) { return descargas.obtenerSavegame(interactivo); }

    public Path elegirSavegameManual() { return descargas.elegirSavegameManual(); }

    public void enviarInteligente(List<Match> objetivo) { descargas.enviarInteligente(objetivo); }

    public void enviarASavegame(List<Match> objetivo) { descargas.enviarASavegame(objetivo); }

    String abrirSgTxt() { return descargas.abrirSgTxt(); }

    public void abrirSavegame() { descargas.abrirSavegame(); }

    public void download(List<Match> objetivoIn) { descargas.download(objetivoIn); }

    public void download(List<Match> objetivoIn, boolean enviarSiempre) { descargas.download(objetivoIn, enviarSiempre); }

    public void descargarSinCambiarVista(List<Match> partidas, boolean enviarAlJuego, Runnable alTerminar) {
        descargas.descargarSinCambiarVista(partidas, enviarAlJuego, alTerminar);
    }

    // ======================================================================
    // Cierre de búsqueda, filtros
    // ======================================================================

    public void cerrarBusqueda() {
        // La × con una búsqueda en marcha la cancela de verdad: antes solo cortaba la espera del freno y la tabla
        // volvía a llenarse al terminar (revisión 1.3, watchlist F6). Su done() (que llega después, en el EDT)
        // apaga el progreso y repone los botones, pero deja el estado en «Búsqueda cerrada.».
        SwingWorker<?, ?> enCurso = fetchWorker;
        if (enCurso != null) { anfitrion.pararOperacion(); busquedas.cerradaPorLaCruz = enCurso; enCurso.cancel(true); }
        all.clear();
        view.clear();
        tableModel.fireTableDataChanged();
        SUJETOS.clear();
        filtroSujetos.clear();
        enlaceWatchlist.limpiarInvitado();
        refrescarSujetos(List.of(), false);
        taparResultados();
        mostrarGuiaVacia(true);
        actualizarTextoBuscar();
        anfitrion.estado(t("Búsqueda cerrada.", "Search closed."));
    }

    public void refreshModeCombo() {
        actualizandoCombos = true;
        Object sel = modeCombo.getSelectedItem();
        TreeSet<String> modos = new TreeSet<>();
        for (Match m : all) modos.add(m.mode);
        modeCombo.removeAllItems();
        modeCombo.addItem(todosModos());
        for (String s : modos) modeCombo.addItem(s);
        if (sel != null && modos.contains(String.valueOf(sel))) modeCombo.setSelectedItem(sel);
        Object selMapa = mapaCombo.getSelectedItem();
        TreeSet<String> mapas = new TreeSet<>();
        for (Match m : all) if (m.map != null && !m.map.isBlank()) mapas.add(m.map);
        mapaCombo.removeAllItems(); mapaCombo.addItem(t("Todos los mapas", "All maps"));
        for (String s : mapas) mapaCombo.addItem(s);
        if (selMapa != null && mapas.contains(String.valueOf(selMapa))) mapaCombo.setSelectedItem(selMapa);
        actualizandoCombos = false;
    }

    /** Fija el jugador seguido de referencia de la partida. */
    void asignarRef(Match m) {
        MatchPlayer suj = null;
        for (MatchPlayer mp : m.players)
            if (SUJETOS.contains(mp.id) && (suj == null || (mp.rating != null && (suj.rating == null || mp.rating > suj.rating)))) suj = mp;
        if (suj != null) { m.refId = suj.id; return; }
        for (Player p : enlaceWatchlist.seleccion())
            if (m.tieneJugador(p.id())) { m.refId = p.id(); return; }
        for (int i = 0; i < enlaceWatchlist.totalJugadores(); i++) {
            Player p = enlaceWatchlist.jugador(i);
            if (m.tieneJugador(p.id())) { m.refId = p.id(); return; }
        }
        if (!m.players.isEmpty()) {
            MatchPlayer mejor = m.players.get(0);
            for (MatchPlayer p : m.players)
                if (p.rating != null && (mejor.rating == null || p.rating > mejor.rating)) mejor = p;
            m.refId = mejor.id;
        }
    }

    public void ajustarColumnas() { tabla.ajustarColumnas(); }

    public void applyFilters() {
        marcarFantasmas(all);
        String modo = (String) modeCombo.getSelectedItem();
        Set<Long> selIds = new HashSet<>();
        List<Player> baseFiltro = new ArrayList<>();
        for (Player s : ultimosSujetos) if (filtroSujetos.contains(s.id())) baseFiltro.add(s);
        for (Player p : baseFiltro) {
            selIds.add(p.id());
            if (p.vinculo() != 0)
                for (Player x : enlaceWatchlist.todosJugadores())
                    if (x.vinculo() == p.vinculo()) selIds.add(x.id());
        }

        String sgCfg = leerConfig("savegame", null);
        Path sgConocida = (sgCfg != null && Files.isDirectory(Path.of(sgCfg))) ? Path.of(sgCfg) : null;
        Instant ahora = Instant.now();

        view.clear();
        for (Match m : all) {
            if (!PartidasPresenter.pasaFiltroModo(modo, todosModos(), m.mode)) continue;
            if (!PartidasPresenter.pasaFiltroMapa(mapaCombo.getSelectedIndex(), mapaCombo.getSelectedItem(), m.map)) continue;
            if (!PartidasPresenter.pasaFiltroPeriodo(periodoCombo.getSelectedIndex(), m.started, ahora)) continue;
            if (!filtroRival.isEmpty()) { asignarRef(m); if (!rivalCoincide(m, filtroRival)) continue; }
            if (!selIds.isEmpty()) {
                boolean alguno = false;
                for (long id : selIds) if (m.tieneJugador(id)) { alguno = true; break; }
                if (!alguno) continue;
            }
            asignarRef(m);
            if (m.finished == null) {
                m.estado = anfitrion.enCursoReal(m) ? "\u25B6" : "\u2014";
                m.enDisco = false;
                m.enJuego = false;
            } else {
                m.enDisco = Files.exists(anfitrion.destino(m));
                m.enJuego = sgConocida != null
                        && Files.exists(sgConocida.resolve(anfitrion.destino(m).getFileName().toString()));
            }
            view.add(m);
        }
        tableModel.fireTableDataChanged();
        mostrarGuiaVacia(all.isEmpty());
        actualizarTextoBuscar();
        if (!all.isEmpty())
            anfitrion.estado(view.size() + t(" de ", " of ") + all.size() + t(" partidas (según filtros).", " games (per filters)."));
    }

    // ======================================================================
    // Cards / controles / texto del botón
    // ======================================================================

    public void mostrarGuiaVacia(boolean guia) {
        if (recsCards != null) ((java.awt.CardLayout) recsCards.getLayout()).show(recsCards, guia ? "guia" : "tabla");
        SwingUtilities.invokeLater(anfitrion::actualizarControlesTabla);
    }

    /** El botón principal dice a quién va a buscar. */
    public void actualizarTextoBuscar() {
        if (fetchWorker != null) return;
        String quien;
        objetivoEtiqueta = null;
        if (enlaceWatchlist.invitado() != null) quien = anfitrion.nombreVisible(enlaceWatchlist.invitado().id(), enlaceWatchlist.invitado().name());
        else if (anfitrion.perfilAbiertoPid() > 0 && enlaceWatchlist.seleccionSize() == 0 && (anfitrion.perfilAbierto() || enlaceWatchlist.modoTop())) { quien = anfitrion.perfilNombreAbierto(); objetivoEtiqueta = new Player(anfitrion.perfilAbiertoPid(), anfitrion.perfilNombreAbierto(), ""); }
        else {
            int n = enlaceWatchlist.seleccionSize();
            if (n > 0) quien = n + t(" seleccionado" + (n > 1 ? "s" : ""), " selected");
            else if (enlaceWatchlist.modoTop()) quien = t("selecciona a alguien", "select someone");
            else quien = t("todo el grupo", "whole group");
        }
        fetchBtn.setText(t("Buscar partidas", "Search games") + " (" + quien + ")");
        if (guiaBtn != null) guiaBtn.setText(fetchBtn.getText());
        enlaceWatchlist.actualizarTextoForma();
    }

    public boolean hayPartidas() { return !all.isEmpty(); }

    /** Repinta la tabla sin recalcular filtros (usado por DialogosJugador tras editar nota/alias). */
    public void refrescarTabla() { tableModel.fireTableDataChanged(); }

    // ======================================================================
    // Azar por ELO / Guess the ELO
    // ======================================================================

    // Viven en {@link BusquedasPartidas} (sus tres SwingWorker, tal cual).
    public void buscarAleatorias() { busquedas.buscarAleatorias(); }

    public void buscarAleatorias(boolean continuar) { busquedas.buscarAleatorias(continuar); }

    public void buscarGte() { busquedas.buscarGte(); }

    // ======================================================================
    // Consulta de partidas y descarga
    // ======================================================================

    /** Vive en {@link BusquedasPartidas} (su SwingWorker, tal cual). */
    public void fetchMatches(JButton btn) { busquedas.fetchMatches(btn); }

    public List<Match> selectedRows() { return tabla.selectedRows(); }

    public List<Match> allRows() { return tabla.allRows(); }

    /** Nombre del jugador de referencia de la partida (columna «Jugador»); lo usa Watchlist para
     *  «tipCuentaVinculada» (tooltip de cuenta hermana). */
    public String refNombre(Match m) { return texto.refNombre(m); }

    void setEstado(Match m, String txt) { tabla.setEstado(m, txt); }

    /** Llamado por Perfil (vía el Anfitrion de la ventana) cuando trae partidas ya cargadas a la tabla: mismo
     *  orden que hoy (limpiar la selección de la watchlist va justo después de volcar en `all`, antes de
     *  refrescar el combo de modos); la ventana sigue ocultando Directos alrededor de esta llamada. */
    public void cargarPartidasEnTabla(List<Match> partidas, Player sujeto, String vistaId) {
        SUJETOS.clear(); SUJETOS.add(sujeto.id());
        vistaDeSujetos = vistaId;
        refrescarSujetos(List.of(sujeto), false);
        all.clear(); all.addAll(partidas);
        enlaceWatchlist.limpiarSeleccion();
        refreshModeCombo();
        applyFilters();
        mostrarGuiaVacia(false);
    }
}
