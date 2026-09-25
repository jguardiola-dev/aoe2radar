package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.CivFila;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.service.NombresStats;
import dev.tirador.aoe2radar.service.StatsService;
import dev.tirador.aoe2radar.service.TechTreeService;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.GrayFilter;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static dev.tirador.aoe2radar.service.CalculoStats.MIN_PARTIDAS_CIV;
import static dev.tirador.aoe2radar.service.CalculoStats.POCAS_PARTIDAS;
import static dev.tirador.aoe2radar.service.NombresStats.claveTechTree;
import static dev.tirador.aoe2radar.service.NombresStats.modoNombre;
import static dev.tirador.aoe2radar.service.NombresStats.nombreMapaStats;
import static dev.tirador.aoe2radar.service.NombresStats.raizCiv;
import static dev.tirador.aoe2radar.service.NombresStats.tramoNombre;
import static dev.tirador.aoe2radar.service.NombresStats.ventanaNombre;
import static dev.tirador.aoe2radar.ui.Componentes.colorHex;
import static dev.tirador.aoe2radar.ui.Componentes.colorWr;
import static dev.tirador.aoe2radar.ui.Componentes.listaVertical;
import static dev.tirador.aoe2radar.ui.Componentes.tituloSeccion;
import static dev.tirador.aoe2radar.ui.Iconos.iconoCiv;
import static dev.tirador.aoe2radar.ui.Iconos.iconoMapa;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.Formato.miles;
import static dev.tirador.aoe2radar.util.Formato.pct1;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Texto.normalizarNick;

/**
 * La pestaña Tech tree: el árbol tecnológico de cada civ (datos de aoe2techtree) y, debajo, la banda de
 * winrate compartida con Civ Stats. Movida tal cual desde SpoilerFreeRecs (construirPanelTechTree y todo lo
 * que pintaba): mismos textos, colores y nombres de hilo. Lo lento (bajar el catálogo, el árbol de una civ,
 * el resumen de Civ Stats, los iconos) vive en {@link TechTreePresenter}; aquí solo Swing y cálculos rápidos
 * ya en memoria.
 * <p>«claveCivDeNombre» es pública porque Actividad (perfil de un jugador) la usa para poner el icono de la
 * civ y para llevar al tech tree con un clic, sin conocer el mapa nombre→clave que solo vive aquí.
 */
public final class TechTreeView implements TechTreePresenter.Pantalla {

    /** Lo que el Tech tree necesita de la ventana y no es navegación: calentar los perfiles guardados al arrancar
     *  y el cierre de la pestaña (cromo compartido con la watchlist: divisor, botón, «Directos»). */
    public interface Anfitrion {
        void precalentarPerfiles();

        /** El botón «×» de la barra del Tech tree: hace lo mismo que cerrar la pestaña desde fuera. */
        void cerrar();
    }

    /** Lo que el Tech tree necesita de Civ Stats (comparten filtros y banda de winrate): la ventana lo cablea con
     *  lambdas al código de Civ Stats, que en esta fase sigue en SpoilerFreeRecs. */
    public interface EnlaceCivStats {
        /** ¿Ya se construyó el panel de Civ Stats? (equivalente a "civStatsPanel != null" de hoy). */
        boolean construida();

        /** Recalcula Civ Stats con los filtros actuales; si repintarTechTree, además repinta esta banda. */
        void filtrosCambiados(boolean repintarTechTree);

        /** Pone el combo de ventana de Civ Stats en "ventana" sin disparar su propio listener (stRellenandoMapas). */
        void sincronizarVentana(String ventana);
    }

    private final Window ventana;
    private final TechTreeService tt;
    private final StatsService stats;
    private final FiltroStats filtroStats;
    private final Listas listas;
    private final Anfitrion anfitrion;
    private final EnlaceCivStats enlaceCivStats;
    private final TechTreePresenter presenter;

    /** Icono (ImageIcon: tipo Swing) ya escalado, de memoria; vive aquí porque la caché de la 1.1 era estática pero
     *  con una única ventana por app el resultado es el mismo (ver docs/DEUDA.md, fase 3, cachés de iconos de estático a instancia). */
    private final Map<String, ImageIcon> ttIconos = new ConcurrentHashMap<>();

    JPanel techTreePanel, ttArbolPanel, ttFichaCards;
    JEditorPane ttFichaCiv;
    public JComboBox<String> ttCivCombo;   // visible para RegresionCapturas
    JLabel ttEstado;
    JScrollPane ttScroll;
    volatile boolean ttCargando, ttRellenandoCombo;
    final Map<String, String> ttCivPorNombre = new LinkedHashMap<>();   // nombre mostrado → clave (Aztecs)
    JTextField ttBuscaCiv;
    volatile String ttCivPedida;

    // ----- Tech tree × Civ Stats: banda inferior con los filtros compartidos, el winrate de la civ y su puesto -----
    JPanel ttBanda, ttMapasPanel;
    JComboBox<String> ttModoCombo, ttMapaCombo, ttVentanaCombo;
    SelectorRangoElo ttRango;
    JLabel ttWrEstado;
    public JLabel ttWrLabel;   // visible para RegresionCapturas
    JLabel ttEmblema;
    public JButton ttPuestoBtn;   // visible para RegresionCapturas
    BarrasWrTramo ttTramosPanel;
    public final Map<String, CivAgg> ttWrPorCiv = new HashMap<>();   // visible para RegresionCapturas: clave del tech tree → agregado con los filtros
    boolean ttRellenandoFiltros;

    /** Ficha completa de un elemento (columna izquierda de la ventana flotante): como en la web, todo lo que hay. */
    JDialog ttDetalleDialog;
    JEditorPane ttDetallePane;

    static final String[] TT_EDADES_ES = { "Alta Edad Media", "Edad Feudal", "Edad de los Castillos", "Edad Imperial" };
    static final String[] TT_EDADES_EN = { "Dark Age", "Feudal Age", "Castle Age", "Imperial Age" };
    static final int TT_VGAP = 12;  // hueco vertical entre filas: por él corren las líneas de mejora (milicia → hombre de armas…)
    static final int TT_CAB = 66;   // la tarjeta del edificio (icono grande y nombre); el edificio en sí va además en su fila de edad, como en la web
    int ttCeldaActual = 40;

    public TechTreeView(Window ventana, TechTreeService tt, StatsService stats, FiltroStats filtroStats, Listas listas,
                         Navegacion navegacion, Tareas tareas, Anfitrion anfitrion, EnlaceCivStats enlaceCivStats) {
        this.ventana = ventana;
        this.tt = tt;
        this.stats = stats;
        this.filtroStats = filtroStats;
        this.listas = listas;
        this.anfitrion = anfitrion;
        this.enlaceCivStats = enlaceCivStats;
        this.presenter = new TechTreePresenter(tt, stats, filtroStats, tareas, this, anfitrion, enlaceCivStats);
        construirPanelTechTree();
    }

    /** El panel de la pestaña, para el CardLayout de la ventana. */
    public JPanel panel() { return techTreePanel; }

    /** La civ pedida ahora mismo (o null): la ventana la usa para el historial de navegación. */
    public String civPedida() { return ttCivPedida; }

    /** Cierra (si está abierta) la ficha flotante de un elemento: la ventana la llama al clicar fuera o al cerrar la pestaña. */
    public void ocultarDetalle() { if (ttDetalleDialog != null && ttDetalleDialog.isVisible()) ttDetalleDialog.setVisible(false); }

    /** Al arrancar, en segundo plano: ver TechTreePresenter.precargar(). */
    public void precargar() { presenter.precargar(); }

    /** Recalcula y repinta la banda de winrate con los filtros actuales; la llama también Civ Stats cuando cambian. */
    public void actualizarWr() { ttActualizarWr(); }

    /** La parte de vista de "abrir Tech tree" (el cromo se queda en la ventana): carga el catálogo si hace falta. */
    public void alAbrir(String civ) {
        if (ttCargando) return;
        ttCargando = true;
        ttEstado.setText(t("Cargando datos…", "Loading data…"));
        presenter.cargarDatos(civ);
    }

    /** Clave de civ a partir del nombre que da la API en el idioma de la app («Aztecas», «Aztecs»). Pública: la usa Actividad. */
    public String claveCivDeNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return null;
        String k = ttCivPorNombre.get(nombre);
        if (k != null) return k.toLowerCase(Locale.ROOT);
        String n = nombre.trim().toLowerCase(Locale.ROOT);
        if (Files.exists(tt.dir().resolve("img/Civs/" + n + ".png"))) return n;
        // variantes de idioma y plural («Tupís», «Muiscas», «Tupi», «Aztecas»…): raíz sin acentos ni -s/-es, comparada por prefijo con la clave y los nombres conocidos
        String raiz = raizCiv(n);
        if (raiz.length() >= 4) {
            for (Map.Entry<String, String> en : ttCivPorNombre.entrySet()) {
                String kk = en.getValue().toLowerCase(Locale.ROOT), rn = raizCiv(en.getKey()), rk = raizCiv(kk);
                if (rn.startsWith(raiz) || raiz.startsWith(rn) || rk.startsWith(raiz) || raiz.startsWith(rk)) return kk;
            }
            if (tt.datos() != null) for (String kk : obj(tt.datos().get("civs")).keySet()) { String rk = raizCiv(kk.toLowerCase(Locale.ROOT)); if (rk.startsWith(raiz) || raiz.startsWith(rk)) return kk.toLowerCase(Locale.ROOT); }
        }
        return null;
    }

    /** Un árbol tecnológico dibujado (tres nodos y sus ramas, sin assets ajenos): estático porque hace falta antes
     *  de que exista la vista, para el icono del botón de la pestaña. */
    public static Icon iconoBoton() {
        return new Icon() {
            public int getIconWidth() { return 14; }
            public int getIconHeight() { return 14; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.getForeground());
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawLine(x + 7, y + 3, x + 7, y + 7); g2.drawLine(x + 3, y + 11, x + 7, y + 7); g2.drawLine(x + 11, y + 11, x + 7, y + 7);
                g2.fillOval(x + 5, y + 0, 5, 5); g2.fillOval(x + 1, y + 9, 5, 5); g2.fillOval(x + 9, y + 9, 5, 5);
                g2.dispose();
            }
        };
    }

    // ===================================================================================== Pantalla (ver TechTreePresenter)

    @Override public void datosListos(String err, String civPedidaOriginal) {
        ttCargando = false;
        if (err != null) {
            ttEstado.setText(t("No se pudieron cargar los datos (", "Couldn't load the data (") + err + t("). Vuelve a intentarlo con conexión.", "). Try again with a connection."));
            return;
        }
        ttEstado.setText("");
        List<String> civs = new ArrayList<>();
        for (Map.Entry<String, Object> en : obj(tt.datos().get("civs")).entrySet())
            if (!"antiquity".equals(String.valueOf(obj(en.getValue()).get("era")))) civs.add(en.getKey());
        civs.sort(String.CASE_INSENSITIVE_ORDER);
        ttCivPorNombre.clear();
        List<String> nombres = new ArrayList<>();
        for (String c : civs) { String n = tt.nombreCiv(c); ttCivPorNombre.put(n, c); nombres.add(n); }
        nombres.sort(String.CASE_INSENSITIVE_ORDER);
        String actualNombre = (String) ttCivCombo.getSelectedItem();
        if (ttCivCombo.getItemCount() != nombres.size()) {
            ttRellenandoCombo = true;
            try { ttCivCombo.removeAllItems(); for (String n : nombres) ttCivCombo.addItem(n); } finally { ttRellenandoCombo = false; }
        }
        String actual = actualNombre == null ? null : ttCivPorNombre.get(actualNombre);
        String pedida = civPedidaOriginal == null ? null : civs.stream().filter(c -> c.equalsIgnoreCase(civPedidaOriginal)).findFirst().orElse(null);
        String elegir = pedida != null ? pedida : actual != null ? actual : civs.isEmpty() ? null : civs.get(0);
        if (elegir != null) {
            String nombreElegir = tt.nombreCiv(elegir);
            if (nombreElegir.equals(ttCivCombo.getSelectedItem())) ttMostrarCiv(elegir); else ttCivCombo.setSelectedItem(nombreElegir);
        }
        presenter.cargarStats();   // winrate junto a cada civ, con los filtros de Civ Stats
    }

    @Override public void arbolListo(String civ, Map<String, Object> arbol) {
        ttEstado.setText("");
        ttPintarFichaCiv(civ);
        ((java.awt.CardLayout) ttFichaCards.getLayout()).show(ttFichaCards, "civ");
        ttPintarArbol(civ, arbol);
        ttArbolPanel.revalidate(); ttArbolPanel.repaint();
        ttScroll.getHorizontalScrollBar().setValue(0);
    }

    @Override public void errorArbol(String civ, String motivo) { ttEstado.setText(t("No se pudo cargar ", "Couldn't load ") + civ + ": " + motivo); }

    @Override public int celdaPx() { return ttCeldaPx(); }

    @Override public void precalentarIcono(String tipo, long id, int px) { ttIcono(tipo, id, px); }

    @Override public void iconosActualizados() { ttPintarDeNuevo(); }

    @Override public void estadoWr(String texto) { ttWrEstado.setText(texto); }

    // ===================================================================================== Ficha, tooltip e iconos

    /** Texto largo como HTML seguro: se escapa todo y se restauran solo <br> y <b>. */
    static String ttHtml(String s) {
        if (s == null) return "";
        String e = escapeHtml(s.replace("\n", "<br>"));
        e = e.replace("&lt;br&gt;", "<br>").replace("&lt;br/&gt;", "<br>").replace("&lt;b&gt;", "<b>").replace("&lt;/b&gt;", "</b>").replace("&lt;i&gt;", "<i>").replace("&lt;/i&gt;", "</i>")
                .replaceAll("(<br>\\s*){3,}", "<br><br>");
        return e;
    }
    /** Texto de ayuda sin etiquetas (para recortes): las cursivas y negritas se quitan, los saltos se conservan como espacios. */
    static String ttPlano(String s) { return s == null ? "" : s.replaceAll("(?i)</?[bi]>", "").replaceAll("(?i)<br\\s*/?>", " ").replace("\n", " ").replaceAll("\\s{2,}", " ").trim(); }

    static String ttImgHtml(Path p, int px) {
        return Files.exists(p) ? "<img src='" + p.toUri() + "' width='" + px + "' height='" + px + "'>" : "";
    }

    /** Icono (px) de img/<tipo>/<id>.png: memoria → disco → red (cola del presentador, 4 hilos). */
    ImageIcon ttIcono(String tipo, long id, int px) {
        String clave = tipo + "/" + id + "@" + px;
        ImageIcon ic = ttIconos.get(clave);
        if (ic != null) return ic;
        Path p = tt.rutaIcono(tipo, id);
        if (Files.exists(p)) {
            try {
                BufferedImage img = ImageIO.read(p.toFile());
                if (img != null) { ic = new ImageIcon(img.getScaledInstance(px, px, Image.SCALE_SMOOTH)); ttIconos.put(clave, ic); return ic; }
            } catch (Exception ignored) { }
        }
        presenter.pedirIcono("img/" + tipo + "/" + id + ".png");
        return null;
    }

    /** La descripción del juego (efecto de la tecnología, uso de la unidad…): cadena LanguageNameId + 21000,
     *  sin la primera línea «Investigar/Crear/Construir X (coste)» que ya cuentan el título y el coste. */
    String ttDescripcion(Map<String, Object> d) {
        if (d == null) return null;
        String txt = tt.str(d.get("LanguageHelpId"));
        if (txt == null && d.get("LanguageNameId") instanceof Number n) txt = tt.str(n.longValue() + 21000);
        if (txt == null) return null;
        txt = txt.replaceAll("\\(?\u2039[^\u203a]*\u203a\\)?", "");   // fuera los marcadores del juego: ‹cost›, ‹hp›, ‹DEFAULT›…
        String[] partes = txt.split("<br>|\n", 2);
        if (partes.length == 2 && partes[0].matches("(?i)\\s*(Investigar|Crear|Construir|Research|Create|Build|Train)\\b.*")) txt = partes[1];
        return txt.replaceAll("(<br>\\s*|\\n\\s*)+$", "").replaceAll("^(<br>|\\s)+", "").trim();
    }

    Map<String, Object> ttDatos(String tipo, long id) {
        return tt.datos() == null ? Map.of() : obj(obj(obj(tt.datos().get("data")).get(tipo)).get(String.valueOf(id)));
    }

    /** «45 [oro] 25 [madera]» con los iconos de recursos. */
    String ttCosteHtml(Map<String, Object> cost) {
        if (cost == null || cost.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String r : new String[]{ "Food", "Wood", "Gold", "Stone" }) {
            Object v = cost.get(r);
            if (v instanceof Number n && n.intValue() > 0) {
                String img = ttImgHtml(tt.dir().resolve("img/" + r.toLowerCase(Locale.ROOT) + ".png"), 14);
                sb.append(sb.length() > 0 ? " &nbsp; " : "").append(n.intValue()).append(' ').append(img.isEmpty() ? r : img);
            }
        }
        return sb.toString();
    }

    static int ttArmadura(Map<String, Object> d, int clase) {
        for (Object a : arr(d.get("Armours"))) { Map<String, Object> m = obj(a); if (lng(m.get("Class")) == clase) return (int) lng(m.get("Amount")); }
        return 0;
    }

    static String ttNum(Object o) {
        if (!(o instanceof Number n)) return "";
        double v = n.doubleValue();
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    /** Tooltip: nombre, coste con iconos y lo esencial; el clic abre la ficha completa. */
    String ttTooltip(String tipo, long id, String nombre, boolean disponible) {
        Map<String, Object> d = ttDatos(tipo, id);
        StringBuilder h = new StringBuilder("<html><b>").append(escapeHtml(nombre)).append("</b>");
        if (!disponible) h.append(" <font color='#e57373'>").append(t("(no disponible)", "(not available)")).append("</font>");
        if (!d.isEmpty()) {
            String coste = ttCosteHtml(obj(d.get("Cost")));
            if (!coste.isEmpty()) h.append("<br>").append(coste);
            if ("Unit".equals(tipo)) {
                h.append("<br>").append(t("PV ", "HP ")).append(ttNum(d.get("HP"))).append(" · ").append(t("ataque ", "attack ")).append(ttNum(d.get("Attack")))
                 .append(" · ").append(t("armadura ", "armor ")).append(ttArmadura(d, 4)).append("/").append(ttArmadura(d, 3));
                if (d.get("Range") instanceof Number n && n.doubleValue() > 0) h.append(" · ").append(t("alcance ", "range ")).append(ttNum(n));
                if (d.get("Speed") instanceof Number) h.append(" · ").append(t("vel. ", "speed ")).append(ttNum(d.get("Speed")));
            } else if ("Tech".equals(tipo)) {
                if (d.get("ResearchTime") instanceof Number) h.append("<br>").append(t("investigación ", "research ")).append(ttNum(d.get("ResearchTime"))).append(" s");
                String desc = ttDescripcion(d);
                if (desc != null) { String plano = ttPlano(desc); h.append("<br><div style='width:320px'>").append(escapeHtml(plano.length() > 260 ? plano.substring(0, 258) + "\u2026" : plano)).append("</div>"); }
            } else if (d.get("HP") instanceof Number) h.append("<br>").append(t("PV ", "HP ")).append(ttNum(d.get("HP")));
        }
        h.append("<br><font color='#8a8a8a'>").append(t("Clic: ficha completa", "Click: full details")).append("</font></html>");
        return h.toString();
    }

    void ttMostrarDetalle(String tipo, long id, long pictureIndex, String nombre, boolean disponible) { ttMostrarDetalle(tipo, id, pictureIndex, nombre, disponible, null); }

    /** Ficha completa en un panel flotante junto al icono: × o un clic en cualquier otra parte lo cierran. */
    void ttMostrarDetalle(String tipo, long id, long pictureIndex, String nombre, boolean disponible, Component ancla) {
        Map<String, Object> d = ttDatos(tipo, id);
        StringBuilder h = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px'>");
        h.append("<div>").append(ttImgHtml(tt.rutaIcono(tipo, pictureIndex), 48)).append(" <span style='font-size:15px'><b>").append(escapeHtml(nombre)).append("</b></span></div>");
        if (!disponible) h.append("<p style='color:#e57373'>").append(t("No disponible para esta civilización.", "Not available for this civilization.")).append("</p>");
        if (!d.isEmpty()) {
            String coste = ttCosteHtml(obj(d.get("Cost")));
            if (!coste.isEmpty()) h.append("<p>").append(t("<b>Coste:</b> ", "<b>Cost:</b> ")).append(coste).append("</p>");
            h.append("<table cellpadding='2' cellspacing='0'>");
            BiConsumer<String, String> fila = (k, v) -> { if (v != null && !v.isEmpty()) h.append("<tr><td style='color:#8a8a8a'>").append(k).append("</td><td>").append(v).append("</td></tr>"); };
            if ("Unit".equals(tipo) || "Building".equals(tipo)) {
                fila.accept(t("PV", "HP"), ttNum(d.get("HP")));
                fila.accept(t("Ataque", "Attack"), ttNum(d.get("Attack")));
                StringBuilder bon = new StringBuilder();
                for (Object a : arr(d.get("Attacks"))) {
                    Map<String, Object> m = obj(a); int cl = (int) lng(m.get("Class")); long am = lng(m.get("Amount"));
                    if (cl == 3 || cl == 4 || am == 0) continue;
                    bon.append(bon.length() > 0 ? "<br>" : "").append("+").append(am).append(" ").append(t("vs ", "vs ")).append(escapeHtml(tt.clase(cl)));
                }
                fila.accept(t("Bonus de ataque", "Attack bonuses"), bon.toString());
                fila.accept(t("Armadura", "Armor"), ttArmadura(d, 4) + " / " + ttArmadura(d, 3) + " <span style='color:#8a8a8a'>(" + t("cuerpo a cuerpo / perforante", "melee / pierce") + ")</span>");
                StringBuilder arm = new StringBuilder();
                for (Object a : arr(d.get("Armours"))) {
                    Map<String, Object> m = obj(a); int cl = (int) lng(m.get("Class")); long am = lng(m.get("Amount"));
                    if (cl == 3 || cl == 4) continue;
                    arm.append(arm.length() > 0 ? "<br>" : "").append(am >= 0 ? "+" : "").append(am).append(" ").append(escapeHtml(tt.clase(cl)));
                }
                fila.accept(t("Clases de armadura", "Armor classes"), arm.toString());
                if (d.get("Range") instanceof Number n && n.doubleValue() > 0) fila.accept(t("Alcance", "Range"), ttNum(n) + (d.get("MinRange") instanceof Number mn && mn.doubleValue() > 0 ? " (" + t("mín. ", "min ") + ttNum(mn) + ")" : ""));
                fila.accept(t("Línea de visión", "Line of sight"), ttNum(d.get("LineOfSight")));
                if ("Unit".equals(tipo)) {
                    fila.accept(t("Velocidad", "Speed"), ttNum(d.get("Speed")));
                    fila.accept(t("Tiempo de entrenamiento", "Train time"), d.get("TrainTime") instanceof Number ? ttNum(d.get("TrainTime")) + " s" : "");
                    fila.accept(t("Cadencia", "Reload time"), d.get("ReloadTime") instanceof Number ? ttNum(d.get("ReloadTime")) + " s" : "");
                    if (d.get("AccuracyPercent") instanceof Number ap && ap.intValue() > 0 && ap.intValue() < 100) fila.accept(t("Precisión", "Accuracy"), ap.intValue() + " %");
                    if (d.get("GarrisonCapacity") instanceof Number g && g.intValue() > 0) fila.accept(t("Guarnición", "Garrison"), ttNum(g));
                } else {
                    if (d.get("GarrisonCapacity") instanceof Number g && g.intValue() > 0) fila.accept(t("Guarnición", "Garrison"), ttNum(g));
                    if (d.get("TrainTime") instanceof Number) fila.accept(t("Tiempo de construcción", "Build time"), ttNum(d.get("TrainTime")) + " s");
                }
            } else {
                if (d.get("ResearchTime") instanceof Number) fila.accept(t("Tiempo de investigación", "Research time"), ttNum(d.get("ResearchTime")) + " s");
            }
            h.append("</table>");
            String ayuda = ttDescripcion(d);
            if (ayuda != null) h.append("<p style='margin-top:8px'>").append(ttHtml(ayuda)).append("</p>");
        }
        h.append("</body></html>");
        if (ttDetalleDialog == null) {
            ttDetalleDialog = new JDialog(ventana, Dialog.ModalityType.MODELESS);
            ttDetalleDialog.setUndecorated(true);
            JPanel cont = new JPanel(new BorderLayout(0, 4));
            cont.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xa3, 0xb8, 0x6c), 2, true), BorderFactory.createEmptyBorder(6, 8, 8, 8)));
            JPanel cab = new JPanel(new BorderLayout());
            cab.setOpaque(false);
            JLabel tit = new JLabel(t("Ficha", "Details"));
            tit.setFont(tit.getFont().deriveFont(Font.BOLD, 11f));
            cab.add(tit, BorderLayout.WEST);
            JButton x = new JButton("\u00D7");
            x.setFocusable(false); x.setMargin(new Insets(0, 6, 0, 6));
            x.putClientProperty("JButton.buttonType", "roundRect");
            x.addActionListener(e -> ttDetalleDialog.setVisible(false));
            cab.add(x, BorderLayout.EAST);
            cont.add(cab, BorderLayout.NORTH);
            ttDetallePane = new JEditorPane("text/html", "");
            ttDetallePane.setEditable(false);
            ttDetallePane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            JScrollPane sp = new JScrollPane(ttDetallePane);
            sp.setBorder(null);
            sp.getVerticalScrollBar().setUnitIncrement(16);
            cont.add(sp, BorderLayout.CENTER);
            ttDetalleDialog.setContentPane(cont);
            ttDetalleDialog.setSize(520, 600);
            ttDetalleDialog.getRootPane().registerKeyboardAction(e -> ttDetalleDialog.setVisible(false),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
        ttDetallePane.setText(h.toString());
        ttDetallePane.setCaretPosition(0);
        ttDetallePane.setSize(new Dimension(480, Integer.MAX_VALUE));
        int altoContenido = ttDetallePane.getPreferredSize().height + 60;
        ttDetalleDialog.setSize(520, Math.max(220, Math.min(600, altoContenido)));
        // junto al icono, sin salirse de la pantalla
        Point p;
        if (ancla != null && ancla.isShowing()) { p = ancla.getLocationOnScreen(); p.translate(ancla.getWidth() + 8, -40); }
        else { p = ventana.getLocationOnScreen(); p.translate(ventana.getWidth() / 2 - 260, ventana.getHeight() / 2 - 300); }
        Rectangle pantalla = ventana.getGraphicsConfiguration().getBounds();
        p.x = Math.max(pantalla.x, Math.min(p.x, pantalla.x + pantalla.width - 530));
        p.y = Math.max(pantalla.y, Math.min(p.y, pantalla.y + pantalla.height - 610));
        ttDetalleDialog.setLocation(p);
        ttDetalleDialog.setVisible(true);
        ttDetalleDialog.toFront();
    }

    // ===================================================================================== El panel y el árbol

    private JPanel construirPanelTechTree() {
        techTreePanel = new JPanel(new BorderLayout(8, 4));
        techTreePanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        // Barra: civ · estado · ×
        JPanel barra = new JPanel(new BorderLayout(8, 0));
        JPanel izq = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        izq.add(new JLabel(t("Civilización:", "Civilization:")));
        ttCivCombo = new JComboBox<>();
        ttCivCombo.setPrototypeDisplayValue("Achaemenids      ");
        ttCivCombo.setRenderer(new DefaultListCellRenderer() {   // con el emblema de cada civ
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean foc) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, value, index, sel, foc);
                String k = value == null ? null : ttCivPorNombre.get(String.valueOf(value));
                lab.setIcon(k == null ? null : iconoCiv(k, 18));
                lab.setIconTextGap(6);
                return lab;
            }
        });
        ttCivCombo.addActionListener(e -> { if (!ttRellenandoCombo && ttCivCombo.getSelectedItem() != null) ttMostrarCiv(ttCivPorNombre.getOrDefault((String) ttCivCombo.getSelectedItem(), (String) ttCivCombo.getSelectedItem())); });
        izq.add(ttCivCombo);
        ttBuscaCiv = new JTextField(12);   // escribe las primeras letras y Enter
        ttBuscaCiv.putClientProperty("JTextField.placeholderText", t("Escribe una civ…", "Type a civ…"));
        ttBuscaCiv.putClientProperty("JTextField.showClearButton", true);
        ttBuscaCiv.getDocument().addDocumentListener(new DocumentListener() {
            void cambio() {
                String q = normalizarNick(ttBuscaCiv.getText());
                if (q.isEmpty()) return;
                for (int i = 0; i < ttCivCombo.getItemCount(); i++) {   // primero por prefijo, luego por contenido
                    String it = ttCivCombo.getItemAt(i);
                    if (normalizarNick(it).startsWith(q) || normalizarNick(ttCivPorNombre.getOrDefault(it, it)).startsWith(q)) { if (!it.equals(ttCivCombo.getSelectedItem())) ttCivCombo.setSelectedItem(it); return; }
                }
                for (int i = 0; i < ttCivCombo.getItemCount(); i++) {
                    String it = ttCivCombo.getItemAt(i);
                    if (normalizarNick(it).contains(q)) { if (!it.equals(ttCivCombo.getSelectedItem())) ttCivCombo.setSelectedItem(it); return; }
                }
            }
            @Override public void insertUpdate(DocumentEvent e) { cambio(); }
            @Override public void removeUpdate(DocumentEvent e) { cambio(); }
            @Override public void changedUpdate(DocumentEvent e) { cambio(); }
        });
        ttBuscaCiv.addActionListener(e -> ttBuscaCiv.selectAll());
        izq.add(ttBuscaCiv);
        ttEstado = new JLabel();
        ttEstado.setFont(ttEstado.getFont().deriveFont(Font.PLAIN, 11f));
        izq.add(ttEstado);
        barra.add(izq, BorderLayout.CENTER);
        JButton cerrar = new JButton("\u00D7");
        cerrar.setFocusable(false);
        cerrar.setMargin(new Insets(0, 7, 0, 7));
        cerrar.putClientProperty("JButton.buttonType", "roundRect");
        cerrar.setToolTipText(t("Cerrar el tech tree", "Close the tech tree"));
        cerrar.addActionListener(e -> anfitrion.cerrar());
        barra.add(cerrar, BorderLayout.EAST);
        techTreePanel.add(barra, BorderLayout.NORTH);
        // Columna izquierda: ficha de civ / ficha de detalle
        ttFichaCards = new JPanel(new java.awt.CardLayout());
        ttFichaCards.setPreferredSize(new Dimension(330, 10));
        ttFichaCiv = new JEditorPane("text/html", "");
        ttFichaCiv.setEditable(false);
        ttFichaCiv.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        JScrollPane s1 = new JScrollPane(ttFichaCiv);
        s1.getVerticalScrollBar().setUnitIncrement(16);
        ttFichaCards.add(s1, "civ");
        techTreePanel.add(ttFichaCards, BorderLayout.WEST);
        // Árbol: horizontal con scroll; las edades, fijas al margen izquierdo
        ttArbolPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {   // degradado de fondo, columnas alternas, bandas de edad con línea dorada y líneas entre edificios
                super.paintComponent(g);
                int celda = ttCeldaActual, cab = TT_CAB, paso = celda + TT_VGAP;
                Graphics2D g2 = (Graphics2D) g.create();
                Color fondo = getBackground();
                g2.setPaint(new GradientPaint(0, 0, temaOscuroActivo ? new Color(0x26, 0x25, 0x23) : new Color(0xf7, 0xf3, 0xea), 0, getHeight(), temaOscuroActivo ? new Color(0x1c, 0x1c, 0x1b) : new Color(0xea, 0xe4, 0xd6)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                int k = 0;
                for (Component c : getComponents()) if (!(c instanceof JSeparator)) { if (k++ % 2 == 1) { g2.setColor(temaOscuroActivo ? new Color(255, 255, 255, 7) : new Color(0, 0, 0, 6)); g2.fillRect(c.getX(), 0, c.getWidth(), getHeight()); } }
                for (int a = 0; a < 4; a++) {
                    int y = cab + 2 + a * 2 * paso;
                    int tono = temaOscuroActivo ? 4 + a * 7 : 8 + a * 8;   // Alta clara → Imperial más marcada
                    g2.setColor(temaOscuroActivo ? new Color(255, 255, 255, tono) : new Color(0, 0, 0, tono));
                    g2.fillRect(0, y, getWidth(), 2 * paso);
                    g2.setColor(new Color(0xc9, 0x8a, 0x3b, temaOscuroActivo ? 70 : 90));   // línea dorada tenue entre edades
                    g2.drawLine(0, y, getWidth(), y);
                }
                g2.setColor(temaOscuroActivo ? new Color(255, 255, 255, 40) : new Color(0, 0, 0, 40));
                for (Component c : getComponents()) if (c instanceof JSeparator) g2.drawLine(c.getX(), 0, c.getX(), getHeight());
                g2.dispose();
            }
        };
        ttArbolPanel.setOpaque(true);
        ttArbolPanel.setLayout(new BoxLayout(ttArbolPanel, BoxLayout.X_AXIS));
        ttScroll = new JScrollPane(ttArbolPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        ttScroll.getHorizontalScrollBar().setUnitIncrement(32);
        ttScroll.getVerticalScrollBar().setUnitIncrement(24);
        ttScroll.addMouseWheelListener(e -> {   // la rueda desplaza en horizontal (el árbol crece a lo ancho)
            if (e.isShiftDown() || ttArbolPanel.getPreferredSize().height <= ttScroll.getViewport().getHeight()) {
                JScrollBar hb = ttScroll.getHorizontalScrollBar();
                hb.setValue(hb.getValue() + e.getWheelRotation() * 48);
            } else {
                JScrollBar vb = ttScroll.getVerticalScrollBar();
                vb.setValue(vb.getValue() + e.getWheelRotation() * 24);
            }
        });
        ttScroll.setWheelScrollingEnabled(false);
        JPanel centroTT = new JPanel(new BorderLayout());
        centroTT.add(ttScroll, BorderLayout.CENTER);
        centroTT.add(construirFiltrosTechTree(), BorderLayout.SOUTH);   // winrate y filtros, en la banda bajo el árbol
        techTreePanel.add(centroTT, BorderLayout.CENTER);
        // Arrastrar con el botón derecho (o central) mueve el árbol; llega a cualquier celda gracias al oyente global
        final Point[] arrastre = { null, null };   // {origen en pantalla, posición del visor}
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (!(ev instanceof MouseEvent me) || ttScroll == null || !ttScroll.isShowing()) return;
            Component c = me.getComponent();
            if (me.getID() == MouseEvent.MOUSE_PRESSED) {
                if (!(javax.swing.SwingUtilities.isRightMouseButton(me) || javax.swing.SwingUtilities.isMiddleMouseButton(me))) return;
                if (c == null || !javax.swing.SwingUtilities.isDescendingFrom(c, ttScroll)) return;
                arrastre[0] = me.getLocationOnScreen(); arrastre[1] = ttScroll.getViewport().getViewPosition();
                ttScroll.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            } else if (me.getID() == MouseEvent.MOUSE_DRAGGED && arrastre[0] != null) {
                JViewport vp = ttScroll.getViewport();
                Point p = me.getLocationOnScreen();
                int nx = Math.max(0, Math.min(Math.max(0, vp.getViewSize().width - vp.getWidth()), arrastre[1].x - (p.x - arrastre[0].x)));
                int ny = Math.max(0, Math.min(Math.max(0, vp.getViewSize().height - vp.getHeight()), arrastre[1].y - (p.y - arrastre[0].y)));
                vp.setViewPosition(new Point(nx, ny));
            } else if (me.getID() == MouseEvent.MOUSE_RELEASED && arrastre[0] != null) {
                arrastre[0] = null; ttScroll.setCursor(Cursor.getDefaultCursor());
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        JLabel pie = new JLabel(t("Datos e iconos: aoe2techtree.net (HSZemi, MIT) · Age of Empires II © Microsoft. En gris, lo que la civ no tiene. Pasa el ratón para ver coste y estadísticas; clic para la ficha completa.",
                "Data & icons: aoe2techtree.net (HSZemi, MIT) · Age of Empires II © Microsoft. Greyed out = not available for this civ. Hover for cost and stats; click for full details."));
        pie.setFont(pie.getFont().deriveFont(Font.PLAIN, 11f));
        techTreePanel.add(pie, BorderLayout.SOUTH);
        return techTreePanel;
    }

    void ttMostrarCiv(String civ) {
        ocultarDetalle();
        ttCivPedida = civ;
        ttArbolPanel.removeAll();
        ttEstado.setText(t("Cargando ", "Loading ") + civ + "…");
        presenter.pedirArbol(civ);
    }

    void ttPintarFichaCiv(String civ) {
        Map<String, Object> civInfo = obj(obj(tt.datos().get("civs")).get(civ));
        String ayuda = tt.str(civInfo.get("help_string_id"));
        Color fg = UIManager.getColor("Label.foreground");
        String col = fg == null ? "#cccccc" : String.format("#%02x%02x%02x", fg.getRed(), fg.getGreen(), fg.getBlue());
        StringBuilder h = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px;color:" + col + "'>");
        Path ic = tt.dir().resolve("img/Civs/" + civ.toLowerCase(Locale.ROOT) + ".png");
        if (!Files.exists(ic)) presenter.pedirIcono("img/Civs/" + civ.toLowerCase(Locale.ROOT) + ".png");
        h.append("<div>").append(ttImgHtml(ic, 56)).append(" <span style='font-size:17px'><b>").append(escapeHtml(tt.nombreCiv(civ))).append("</b></span></div>");
        ttActualizarBanda();
        if (ayuda != null) h.append("<p>").append(ttHtml(ayuda)).append("</p>");
        h.append("</body></html>");
        ttFichaCiv.setText(h.toString());
        ttFichaCiv.setCaretPosition(0);
    }

    void ttPintarDeNuevo() {
        String civ = ttCivCombo == null || ttCivCombo.getSelectedItem() == null ? null : ttCivPorNombre.getOrDefault((String) ttCivCombo.getSelectedItem(), (String) ttCivCombo.getSelectedItem());
        if (civ == null || !tt.arbolEnCache(civ) || !techTreePanel.isShowing()) return;
        int h = ttScroll.getHorizontalScrollBar().getValue();
        ttArbolPanel.removeAll();
        ttPintarArbol(civ, tt.arbolCacheado(civ));
        ttArbolPanel.revalidate(); ttArbolPanel.repaint();
        ttScroll.getHorizontalScrollBar().setValue(h);
    }

    int ttCeldaPx() {   // ocho filas + cabecera en el alto disponible: iconos grandes cuando hay sitio
        int alto = ttScroll == null || ttScroll.getViewport().getHeight() < 200 ? 560 : ttScroll.getViewport().getHeight();
        return Math.max(32, Math.min(58, (alto - TT_CAB - 24) / 8 - TT_VGAP));
    }

    /** El árbol como la web: una columna por edificio, ocho filas (dos por edad) alineadas en todas las columnas. */
    void ttPintarArbol(String civ, Map<String, Object> arbol) {
        final int TT_CELDA = ttCeldaPx();
        ttCeldaActual = TT_CELDA;
        Map<String, Map<String, Object>> nodos = new HashMap<>();
        for (Object o : arr(arbol.get("units_techs"))) { Map<String, Object> n = obj(o); nodos.put(String.valueOf(n.get("id")), n); }
        for (Object o : arr(arbol.get("buildings"))) { Map<String, Object> n = obj(o); nodos.put(String.valueOf(n.get("id")), n); }
        Color gris = temaOscuroActivo ? new Color(0x8a, 0x8a, 0x8a) : new Color(0x77, 0x77, 0x77);
        Color banda = temaOscuroActivo ? new Color(0xff, 0xff, 0xff, 14) : new Color(0, 0, 0, 12);
        // Margen fijo con las edades (rowHeader del scroll: no se mueve con el desplazamiento horizontal)
        JPanel edades = new JPanel();
        edades.setLayout(new BoxLayout(edades, BoxLayout.Y_AXIS));
        edades.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        JLabel vacio = new JLabel(); vacio.setPreferredSize(new Dimension(104, TT_CAB + 2)); vacio.setMaximumSize(new Dimension(104, TT_CAB + 2)); edades.add(vacio);
        edades.setOpaque(true); edades.setBackground(temaOscuroActivo ? new Color(0x22, 0x21, 0x1f) : new Color(0xf1, 0xec, 0xe1));
        String[] nombres = "es".equals(IDIOMA) ? TT_EDADES_ES : TT_EDADES_EN;
        String[] edadImg = { "base_dark_age", "base_feudal_age", "base_castle_age", "base_imperial_age" };
        List<Object> ageIds = arr(obj(tt.datos().get("age_names")).get("base"));
        for (int a = 0; a < 4; a++) {   // una etiqueta por edad, del alto de sus dos filas
            String nombreEdad = a < ageIds.size() && tt.str(ageIds.get(a)) != null ? tt.nombre(ageIds.get(a)) : nombres[a];
            JLabel e = new JLabel("<html><div style='text-align:center'><b>" + escapeHtml(nombreEdad).replace(" de los ", "<br>de los ").replace("Alta Edad Media", "Alta<br>Edad Media") + "</b></div></html>");
            Path pe = tt.dir().resolve("img/Ages/" + edadImg[a] + ".png");
            if (!Files.exists(pe)) presenter.pedirIcono("img/Ages/" + edadImg[a] + ".png");
            else try { int px = Math.min(64, 2 * (TT_CELDA + TT_VGAP) - 40); e.setIcon(new ImageIcon(ImageIO.read(pe.toFile()).getScaledInstance(px, px, Image.SCALE_SMOOTH))); } catch (Exception ignored) { }
            e.setVerticalTextPosition(SwingConstants.BOTTOM); e.setHorizontalTextPosition(SwingConstants.CENTER);
            e.setHorizontalAlignment(SwingConstants.CENTER); e.setVerticalAlignment(SwingConstants.CENTER);
            e.setForeground(gris);
            e.setFont(e.getFont().deriveFont(Font.BOLD, 10.5f));
            int alto = 2 * (TT_CELDA + TT_VGAP);
            e.setPreferredSize(new Dimension(104, alto)); e.setMaximumSize(new Dimension(104, alto)); e.setMinimumSize(new Dimension(104, alto));
            e.setOpaque(false);
            e.setForeground(temaOscuroActivo ? new Color(0xd8, 0xc2, 0x8a) : new Color(0x6b, 0x4c, 0x16));   // nombre de edad en dorado apagado
            JPanel placa = new TechTreeArbol.PlacaTT(a);
            placa.setLayout(new BorderLayout()); placa.add(e, BorderLayout.CENTER);
            placa.setPreferredSize(new Dimension(104, alto)); placa.setMaximumSize(new Dimension(104, alto)); placa.setMinimumSize(new Dimension(104, alto));
            edades.add(placa);
        }
        ttScroll.setRowHeaderView(edades);
        List<Map<String, Object>> edificios = new ArrayList<>();
        for (Object o : arr(arbol.get("buildings"))) edificios.add(obj(o));
        for (Map<String, Object> b : edificios) {
            List<Object> grid = arr(b.get("grid"));
            int cols = 0, conContenido = 0;
            for (Object f : grid) { cols = Math.max(cols, arr(f).size()); for (Object c : arr(f)) if (c != null && nodos.containsKey(String.valueOf(c))) conContenido++; }
            if (cols == 0 || conContenido == 0) continue;   // torres, muros, puertas, puestos…: sin nada que enseñar, fuera
            boolean bDisp = !"NotAvailable".equals(String.valueOf(b.get("node_status")));
            String bNombre = tt.nombre(b.get("name_string_id"));
            long bPic = lng(b.get("picture_index")), bId = lng(b.get("building_id"));
            int colsTot = cols + 1;   // la primera columna es la del propio edificio, en su fila de edad
            int anchoCol = colsTot * (TT_CELDA + 2);
            JPanel columna = new JPanel(new BorderLayout(0, 2));
            columna.setOpaque(false);
            columna.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            columna.setMaximumSize(new Dimension(anchoCol + 12, TT_CAB + 2 + 8 * (TT_CELDA + TT_VGAP)));
            columna.setAlignmentY(Component.TOP_ALIGNMENT);   // todas las columnas arrancan arriba: filas alineadas con las edades
            // cabecera del edificio: icono + nombre a todo el ancho de la columna
            ImageIcon bic = ttIcono("Building", bPic, TT_CELDA - 6);
            ImageIcon bicCab = ttIcono("Building", bPic, 34);
            JLabel cab = new JLabel() {
                @Override protected void paintComponent(Graphics g) {   // fondo de placa debajo del icono y el nombre
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight();
                    g2.setPaint(new GradientPaint(0, 0, temaOscuroActivo ? new Color(0x36, 0x31, 0x28) : new Color(0xf8, 0xf1, 0xdf), 0, h, temaOscuroActivo ? new Color(0x25, 0x23, 0x1f) : new Color(0xe9, 0xde, 0xc3)));
                    g2.fillRoundRect(1, 1, w - 3, h - 3, 12, 12);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            cab.setPreferredSize(new Dimension(anchoCol, TT_CAB));
            cab.setMinimumSize(new Dimension(anchoCol, TT_CAB));
            cab.setMaximumSize(new Dimension(anchoCol, TT_CAB));
            if (bicCab != null) cab.setIcon(bDisp ? bicCab : new ImageIcon(GrayFilter.createDisabledImage(bicCab.getImage())));
            cab.setText("<html><div style='text-align:center'><b><span style='font-size:10.5px'>" + escapeHtml(bNombre).toUpperCase(Locale.ROOT).replace(" ", "&nbsp;") + "</span></b></div></html>");
            cab.setIconTextGap(2);
            cab.setHorizontalAlignment(SwingConstants.CENTER);   // icono arriba, nombre en versalitas debajo: una placa por edificio
            cab.setHorizontalTextPosition(SwingConstants.CENTER);
            cab.setVerticalTextPosition(SwingConstants.BOTTOM);
            cab.setVerticalAlignment(SwingConstants.CENTER);
            cab.setOpaque(false);
            cab.setBorder(new TechTreeArbol.PlacaBorde());
            cab.setForeground(bDisp ? (temaOscuroActivo ? new Color(0xe6, 0xd3, 0xa3) : new Color(0x5a, 0x40, 0x10)) : gris);
            int filaEdificio = (int) Math.max(0, Math.min(3, lng(b.get("age_id")) - 1)) * 2;   // su edad: primera fila de la banda
            cab.setToolTipText(ttTooltip("Building", bId, bNombre, bDisp));
            cab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final boolean fDisp = bDisp;
            cab.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { ttMostrarDetalle("Building", bId, bPic, bNombre, fDisp, cab); } });
            columna.add(cab, BorderLayout.NORTH);
            JPanel rejilla = new TechTreeArbol.RejillaTT(8, colsTot);
            rejilla.setPreferredSize(new Dimension(anchoCol, 8 * (TT_CELDA + TT_VGAP)));
            columna.add(rejilla, BorderLayout.CENTER);
            for (int r = 0; r < 8; r++) {
                List<Object> filaG = r < grid.size() ? arr(grid.get(r)) : List.of();
                for (int c = -1; c < cols; c++) {
                    if (c == -1) {   // columna del edificio
                        JLabel be = new JLabel();
                        be.setPreferredSize(new Dimension(TT_CELDA, TT_CELDA));
                        be.setHorizontalAlignment(SwingConstants.CENTER);
                        if (r == filaEdificio) {
                            if (bic != null) be.setIcon(bDisp ? bic : new ImageIcon(GrayFilter.createDisabledImage(bic.getImage())));
                            be.setToolTipText(ttTooltip("Building", bId, bNombre, bDisp));
                            be.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            be.setBorder(BorderFactory.createLineBorder(bDisp ? new Color(0xc9, 0x8a, 0x3b) : new Color(0xe5, 0x73, 0x73, 110), 2, true));
                            be.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { ttMostrarDetalle("Building", bId, bPic, bNombre, fDisp, be); } });
                        }
                        rejilla.add(be);
                        continue;
                    }
                    Object celda = c < filaG.size() ? filaG.get(c) : null;
                    JLabel l = new JLabel();
                    l.setPreferredSize(new Dimension(TT_CELDA, TT_CELDA));
                    l.setHorizontalAlignment(SwingConstants.CENTER);
                    if (celda != null && nodos.containsKey(String.valueOf(celda))) {
                        Map<String, Object> n = nodos.get(String.valueOf(celda));
                        String tipo = String.valueOf(n.get("use_type"));
                        long nid = lng(n.get("node_id")), pic = lng(n.get("picture_index"));
                        boolean disp = !"NotAvailable".equals(String.valueOf(n.get("node_status")));
                        String nombre = tt.nombre(n.get("name_string_id"));
                        ImageIcon ic = ttIcono(tipo, pic, TT_CELDA - 6);
                        if (ic != null) l.setIcon(disp ? ic : new ImageIcon(TechTreeArbol.imagenApagada(ic.getImage())));
                        else if (!disp) { l.setText("\u00D7"); l.setForeground(gris); }
                        l.setToolTipText(ttTooltip(tipo, nid, nombre, disp));
                        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        Color marco = !disp ? new Color(0xe5, 0x73, 0x73, 70)
                                : "Unit".equals(tipo) ? new Color(0x3b, 0x82, 0xc4) : "Tech".equals(tipo) ? new Color(0x5c, 0xa8, 0x4a) : new Color(0xc9, 0x8a, 0x3b);
                        l.setBorder(BorderFactory.createLineBorder(marco, 2, true));   // marco por tipo, como la web
                        l.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { ttMostrarDetalle(tipo, nid, pic, nombre, disp, l); } });
                    }
                    rejilla.add(l);
                }
            }
            ttArbolPanel.add(columna);
            JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
            sep.setMaximumSize(new Dimension(1, TT_CAB + 2 + 8 * (TT_CELDA + TT_VGAP)));
            sep.setAlignmentY(Component.TOP_ALIGNMENT);
            ttArbolPanel.add(sep);
        }
    }

    // PlacaTT, PlacaBorde, RejillaTT e imagenApagada (pintura pura, sin estado de la vista): ver ui.TechTreeArbol.


    // ===================================================================================== Banda de winrate (compartida con Civ Stats)

    /** Winrate por tramo: barra desde la línea del 50 % (verde arriba si gana, roja abajo si pierde), el % encima y el pick rate del tramo en gris debajo. */
    class BarrasWrTramo extends JPanel {
        String[] etiquetas = new String[0]; int[] n = new int[0], w = new int[0]; double[] pick = new double[0];
        BarrasWrTramo() { setOpaque(false); ToolTipManager.sharedInstance().registerComponent(this); }
        void datos(String[] e, int[] nn, int[] ww, double[] p) { etiquetas = e; n = nn; w = ww; pick = p; repaint(); }
        int barraEn(Point pt) {
            if (n.length == 0) return -1;
            int ml = 8, mr = 8; double sx = (double) (getWidth() - ml - mr) / n.length;
            int i = (int) ((pt.x - ml) / sx);
            return i < 0 || i >= n.length ? -1 : i;
        }
        @Override public String getToolTipText(MouseEvent e) {
            int i = barraEn(e.getPoint());
            if (i < 0) return null;
            if (n[i] < MIN_PARTIDAS_CIV) return etiquetas[i] + ": " + n[i] + t(" partidas (pocas)", " games (few)");
            double[] iv = stats.wilson(w[i], n[i]);
            return etiquetas[i] + ": " + pct1(100.0 * w[i] / n[i]) + " (" + pct1(iv[0]) + "–" + pct1(iv[1]) + ") · " + miles(n[i]) + t(" partidas · pick ", " games · pick ") + pct1(pick[i]);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.foreground"); if (fg == null) fg = Color.GRAY;
            int wd = getWidth(), h = getHeight(), ml = 8, mr = 8, mt = 42, mb = 30;
            Font base = g2.getFont();
            g2.setFont(base.deriveFont(Font.BOLD, 13f)); g2.setColor(fg);
            g2.drawString(t("Winrate por tramo de ELO", "Win rate by ELO bracket"), 4, 15);
            g2.setFont(base.deriveFont(10.5f)); g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 160 : 205));
            g2.drawString(t("barra: % de victorias respecto al 50 · debajo: pick rate del tramo", "bar: win rate vs 50% · below: pick rate in that bracket"), 4, 28);
            if (n.length == 0) { g2.dispose(); return; }
            int y50 = mt + (h - mt - mb) / 2, medio = (h - mt - mb) / 2 - 12;
            g2.setColor(new Color(128, 128, 128, 120));
            g2.drawLine(ml, y50, wd - mr, y50);
            double sx = (double) (wd - ml - mr) / n.length;
            for (int i = 0; i < n.length; i++) {
                int x = ml + (int) (i * sx), bw = Math.max(2, (int) sx - 4);
                if (n[i] >= MIN_PARTIDAS_CIV) {
                    double wr = 100.0 * w[i] / n[i], d = Math.max(-8, Math.min(8, wr - 50));   // ±8 puntos = barra completa
                    int bh = (int) Math.round(Math.abs(d) / 8.0 * medio);
                    Color c = colorWr(w[i], n[i]);
                    g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 200));
                    if (d >= 0) g2.fillRoundRect(x, y50 - bh, bw, Math.max(2, bh), 3, 3); else g2.fillRoundRect(x, y50, bw, Math.max(2, bh), 3, 3);
                    g2.setColor(c);
                    g2.setFont(base.deriveFont(Font.BOLD, 12f));
                    String s = String.format(Locale.ROOT, "%.0f", wr) + "%";
                    int sw = g2.getFontMetrics().stringWidth(s);
                    g2.drawString(s, x + bw / 2 - sw / 2, d >= 0 ? y50 - bh - 4 : y50 + bh + 12);
                } else {
                    g2.setColor(new Color(128, 128, 128, 90));
                    g2.fillRoundRect(x, y50 - 2, bw, 4, 3, 3);
                }
                g2.setFont(base.deriveFont(10.5f));
                g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), temaOscuroActivo ? 190 : 215));
                String et = etiquetas[i];
                g2.drawString(et, x + bw / 2 - g2.getFontMetrics().stringWidth(et) / 2, h - mb + 13);
                String pk = pick.length > i && pick[i] > 0 ? String.format(Locale.ROOT, "%.1f", pick[i]).replace('.', "en".equals(IDIOMA) ? '.' : ',') + "%" : "";
                g2.setColor(Color.GRAY);
                g2.drawString(pk, x + bw / 2 - g2.getFontMetrics().stringWidth(pk) / 2, h - mb + 26);
            }
            g2.dispose();
        }
    }

    private JPanel construirFiltrosTechTree() {
        ttBanda = new JPanel(new BorderLayout(8, 4));
        ttBanda.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(128, 128, 128, 70)), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JPanel filtros = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 1));
        ttModoCombo = new JComboBox<>();
        for (String m : stats.modos()) ttModoCombo.addItem(modoNombre(m));
        ttModoCombo.addActionListener(e -> { if (ttRellenandoFiltros) return; filtroStats.modo(stats.modos()[Math.max(0, ttModoCombo.getSelectedIndex())]); guardarConfig("stats_modo", filtroStats.modo()); filtroStats.civSeleccionada(null); enlaceCivStats.filtrosCambiados(true); });
        ttMapaCombo = new JComboBox<>();
        ttMapaCombo.setPrototypeDisplayValue("African Clearing (99.999)   ");
        ttMapaCombo.addActionListener(e -> { if (ttRellenandoFiltros) return; Object cl = ttMapaCombo.getClientProperty("claves"); if (cl instanceof List<?> l && ttMapaCombo.getSelectedIndex() >= 0 && ttMapaCombo.getSelectedIndex() < l.size()) { filtroStats.mapa(String.valueOf(l.get(ttMapaCombo.getSelectedIndex()))); guardarConfig("stats_mapa", filtroStats.mapa()); enlaceCivStats.filtrosCambiados(true); } });
        ttRango = new SelectorRangoElo(ventana, NombresStats::tramoNombre);
        ttRango.alCambiar = r -> { if (ttRellenandoFiltros) return; filtroStats.tramo(r); guardarConfig("stats_tramo", filtroStats.tramo()); enlaceCivStats.filtrosCambiados(true); };
        ttVentanaCombo = new JComboBox<>();
        for (String vv : stats.clavesVentanas()) ttVentanaCombo.addItem(ventanaNombre(vv));
        ttVentanaCombo.addActionListener(e -> {
            if (ttRellenandoFiltros) return;
            filtroStats.ventana(stats.clavesVentanas()[Math.max(0, ttVentanaCombo.getSelectedIndex())]);
            guardarConfig("stats_ventana", filtroStats.ventana());
            enlaceCivStats.sincronizarVentana(filtroStats.ventana());
            presenter.cargarStats();
        });
        ttWrEstado = new JLabel();
        ttWrEstado.setFont(ttWrEstado.getFont().deriveFont(Font.PLAIN, 11f));
        ttWrEstado.setForeground(Color.GRAY);
        JLabel cab = new JLabel(t("Estadísticas con:", "Statistics with:"));
        cab.setFont(cab.getFont().deriveFont(Font.BOLD));
        filtros.add(cab);
        filtros.add(ttModoCombo); filtros.add(ttMapaCombo); filtros.add(ttRango); filtros.add(ttVentanaCombo); filtros.add(ttWrEstado);
        filtros.setToolTipText(t("Los mismos filtros que el panel Civ Stats: cambiarlos aquí los cambia allí.", "Same filters as the Civ Stats panel: changing them here changes them there."));
        ttBanda.add(filtros, BorderLayout.NORTH);
        JPanel cols = new JPanel(new GridLayout(1, 3, 16, 0));   // tres columnas iguales
        JPanel izq = new JPanel(new BorderLayout(10, 2));
        ttEmblema = new JLabel();
        ttEmblema.setVerticalAlignment(SwingConstants.TOP);
        ttEmblema.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        izq.add(ttEmblema, BorderLayout.WEST);
        ttWrLabel = new JLabel();
        ttWrLabel.setVerticalAlignment(SwingConstants.TOP);
        izq.add(ttWrLabel, BorderLayout.CENTER);
        ttPuestoBtn = new JButton();
        ttPuestoBtn.setFocusable(false); ttPuestoBtn.setMargin(new Insets(1, 8, 1, 8)); ttPuestoBtn.putClientProperty("JButton.buttonType", "roundRect");
        ttPuestoBtn.setToolTipText(t("Ranking de todas las civs con estos filtros; clic en una civ para verla", "Ranking of every civ with these filters; click a civ to open it"));
        ttPuestoBtn.addActionListener(e -> ttMostrarRanking());
        ttPuestoBtn.setVisible(false);
        JPanel pb = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pb.add(ttPuestoBtn);
        izq.add(pb, BorderLayout.SOUTH);
        cols.add(izq);
        ttTramosPanel = new BarrasWrTramo();
        cols.add(ttTramosPanel);
        ttMapasPanel = listaVertical();
        cols.add(ttMapasPanel);
        cols.setPreferredSize(new Dimension(10, 184));
        ttBanda.add(cols, BorderLayout.CENTER);
        return ttBanda;
    }

    /** Recalcula el winrate por civ con los filtros compartidos, rellena los combos de la banda y repinta la línea. */
    void ttActualizarWr() {
        VentanaStats v = stats.ventana(filtroStats.ventana());
        if (v == null || ttBanda == null) return;
        ttRellenandoFiltros = true;
        try {
            ttModoCombo.setSelectedIndex(Math.max(0, Arrays.asList(stats.modos()).indexOf(filtroStats.modo())));
            ttVentanaCombo.setSelectedIndex(Math.max(0, Arrays.asList(stats.clavesVentanas()).indexOf(filtroStats.ventana())));
            Map<String, Integer> mapas = stats.partidasPorMapa(v, filtroStats.modo(), filtroStats.tramo());
            List<Map.Entry<String, Integer>> lm = new ArrayList<>(mapas.entrySet());
            lm.sort((a, b) -> b.getValue() - a.getValue());
            List<String> claves = new ArrayList<>(); claves.add("*");
            ttMapaCombo.removeAllItems();
            ttMapaCombo.addItem(t("Todos los mapas", "All maps"));
            for (Map.Entry<String, Integer> en : lm) { if (en.getValue() < MIN_PARTIDAS_CIV) continue; claves.add(en.getKey()); ttMapaCombo.addItem(nombreMapaStats(v, en.getKey()) + " (" + miles(en.getValue()) + ")"); }
            ttMapaCombo.putClientProperty("claves", claves);
            ttMapaCombo.setSelectedIndex(Math.max(0, claves.indexOf(filtroStats.mapa())));
            ttRango.tramos(v.tramos(), filtroStats.tramo());
        } finally { ttRellenandoFiltros = false; }
        ttWrPorCiv.clear();
        for (CivAgg a : stats.agregarCivs(v, filtroStats.modo(), filtroStats.mapa(), filtroStats.tramo()).values()) {
            String k = claveTechTree(a.civ());
            if (k != null) ttWrPorCiv.put(k, a);
        }
        ttActualizarBanda();
    }

    /** Civs con datos suficientes, de mayor a menor winrate. */
    List<String> ttRankingCivs() {
        List<String> l = new ArrayList<>();
        for (Map.Entry<String, CivAgg> en : ttWrPorCiv.entrySet()) if (en.getValue().n() >= MIN_PARTIDAS_CIV) l.add(en.getKey());
        l.sort((a, b) -> Double.compare(ttWrPorCiv.get(b).wr(), ttWrPorCiv.get(a).wr()));
        return l;
    }

    /** La banda de la civ elegida: winrate con intervalo, pick, partidas y puesto; barras por tramo de ELO; mejores y peores mapas. */
    void ttActualizarBanda() {
        if (ttWrLabel == null) return;
        String civ = ttCivPedida;
        VentanaStats v = stats.ventana(filtroStats.ventana());
        ttMapasPanel.removeAll();
        ttEmblema.setIcon(civ == null ? null : iconoCiv(civ, 40));
        if (civ == null || v == null) {
            ttWrLabel.setText(v == null ? "<html><span style='color:gray'>" + t("Winrate: cargando…", "Win rate: loading…") + "</span></html>" : "");
            ttPuestoBtn.setVisible(false); ttTramosPanel.datos(new String[0], new int[0], new int[0], new double[0]);
            ttMapasPanel.revalidate(); ttMapasPanel.repaint(); return;
        }
        CivAgg a = ttWrPorCiv.get(civ);
        long totalN = 0; for (CivAgg x : ttWrPorCiv.values()) totalN += x.n();
        if (a == null || a.n() < MIN_PARTIDAS_CIV) {
            ttWrLabel.setText("<html><b>" + escapeHtml(tt.nombreCiv(civ)) + "</b><br><span style='color:gray'>" + t("sin datos suficientes con estos filtros", "not enough data with these filters") + "</span></html>");
            ttPuestoBtn.setVisible(false); ttTramosPanel.datos(new String[0], new int[0], new int[0], new double[0]);
            ttMapasPanel.revalidate(); ttMapasPanel.repaint(); return;
        }
        Color c = colorWr(a.w(), a.n());
        double[] iv = stats.wilson(a.w(), a.n());
        List<String> ranking = ttRankingCivs();
        int puesto = ranking.indexOf(civ) + 1;
        long partidasFiltro = totalN / (filtroStats.modo().endsWith("_1v1") ? 2 : filtroStats.modo().endsWith("_2v2") ? 4 : filtroStats.modo().endsWith("_3v3") ? 6 : filtroStats.modo().endsWith("_4v4") ? 8 : 6);
        ttWrEstado.setText(t("datos hasta el ", "data up to ") + v.hasta() + (partidasFiltro < POCAS_PARTIDAS ? "  ·  " + t("pocas partidas con estos filtros: prueba 90 o 365 días", "few games with these filters: try 90 or 365 days") : ""));
        ttWrLabel.setText("<html><div style='font-size:13px'><b style='font-size:17px'>" + escapeHtml(tt.nombreCiv(civ)) + "</b><br><span style='color:gray;font-size:12px'>" + escapeHtml(modoNombre(filtroStats.modo())) + " · " + escapeHtml(nombreMapaStats(v, filtroStats.mapa())) + " · " + escapeHtml(tramoNombre(filtroStats.tramo())) + " · " + escapeHtml(ventanaNombre(filtroStats.ventana())) + "</span><br>"
                + "<span style='font-size:26px;color:" + colorHex(c) + "'><b>" + escapeHtml(pct1(a.wr())) + "</b></span> &nbsp;" + t("de victorias", "win rate") + " (" + escapeHtml(pct1(iv[0])) + " – " + escapeHtml(pct1(iv[1])) + ")<br>"
                + t("Pick rate ", "Pick rate ") + "<b>" + escapeHtml(pct1(totalN == 0 ? 0 : 100.0 * a.n() / totalN)) + "</b> &nbsp;·&nbsp; <b>" + miles(a.n()) + "</b> " + t("partidas", "games") + " &nbsp;·&nbsp; " + t("duración media ", "average length ") + "<b>" + stats.duracionMedia(a.d(), a.n()) + "</b></div></html>");
        ttPuestoBtn.setText(puesto > 0 ? t("puesto ", "rank ") + puesto + t(" de ", " of ") + ranking.size() + " \u25BE" : t("ranking \u25BE", "ranking \u25BE"));
        ttPuestoBtn.setVisible(true);
        // por tramo de ELO (con el filtro de mapa)
        String[] etq = new String[v.tramos().size()]; int[] n = new int[etq.length], w = new int[etq.length]; double[] pk = new double[etq.length];
        Map<String, int[]> porTramo = new HashMap<>();
        Map<String, Long> totalTramo = new HashMap<>();   // todas las civs, para el pick rate
        for (CivFila f : v.civs()) {
            if (!f.modo().equals(filtroStats.modo())) continue;
            if (!"*".equals(filtroStats.mapa()) && !f.mapa().equals(filtroStats.mapa())) continue;
            totalTramo.merge(f.tramo(), (long) f.n(), Long::sum);
            if (!f.civ().equals(a.civ())) continue;
            int[] x = porTramo.computeIfAbsent(f.tramo(), k -> new int[2]); x[0] += f.n(); x[1] += f.w();
        }
        for (int i = 0; i < etq.length; i++) {
            String tr = v.tramos().get(i);
            etq[i] = tr.endsWith("+") ? tr : tr.startsWith("0-") ? "<" + tr.substring(2) : tr.substring(0, tr.indexOf('-'));
            int[] x = porTramo.get(tr); n[i] = x == null ? 0 : x[0]; w[i] = x == null ? 0 : x[1];
            long tot = totalTramo.getOrDefault(tr, 0L); pk[i] = tot == 0 ? 0 : 100.0 * n[i] / tot;
        }
        ttTramosPanel.datos(etq, n, w, pk);
        // mejores y peores mapas (solo con «todos los mapas»)
        ttMapasPanel.add(tituloSeccion(t("Mejores y peores mapas", "Best and worst maps"), t("Winrate de la civ en cada mapa del pool con el modo y el tramo elegidos (sea cual sea el mapa del filtro); mínimo 20 partidas por mapa (menos de 100: orientativo). El mapa elegido va en negrita.", "The civ's win rate on each pool map with the chosen mode and bracket (whatever the filter map); at least 20 games per map (under 100: indicative only). The chosen map is in bold.")));
        {
            List<CivAgg> pm = new ArrayList<>(stats.civPorMapa(v, filtroStats.modo(), filtroStats.tramo(), a.civ()).values());
            pm.removeIf(x -> x.n() < MIN_PARTIDAS_CIV);
            pm.sort((x, y) -> Double.compare(y.wr(), x.wr()));
            double maxN = 1; for (CivAgg x : pm) maxN = Math.max(maxN, x.n());
            List<CivAgg> mostrar = new ArrayList<>();
            if (pm.size() <= 6) mostrar.addAll(pm);
            else { mostrar.addAll(pm.subList(0, 3)); mostrar.addAll(pm.subList(pm.size() - 3, pm.size())); }
            if (!"*".equals(filtroStats.mapa())) for (CivAgg x : pm) if (x.civ().equals(filtroStats.mapa()) && !mostrar.contains(x)) { mostrar.add(3 < mostrar.size() ? 3 : mostrar.size(), x); break; }   // el mapa del filtro, siempre presente
            for (CivAgg x : mostrar) {
                boolean elegido = x.civ().equals(filtroStats.mapa());
                ttMapasPanel.add(listas.filaBarra(iconoMapa(nombreMapaStats(v, x.civ()), x.civ(), 16), (elegido ? "\u25B8 " : "") + nombreMapaStats(v, x.civ()), x.n() / maxN, pct1(x.wr()), colorWr(x.w(), x.n()), miles(x.n()) + t(" partidas", " games") + (elegido ? t(" · el mapa elegido", " · the chosen map") : ""), null));
            }
            if (pm.isEmpty()) { JLabel vac = new JLabel(t("Sin mapas con 20+ partidas.", "No maps with 20+ games.")); vac.setForeground(Color.GRAY); vac.setFont(vac.getFont().deriveFont(Font.PLAIN, 11f)); ttMapasPanel.add(vac); }
        }
        ttMapasPanel.revalidate(); ttMapasPanel.repaint();
    }

    /** Lista desplegable de todas las civs ordenadas por winrate con los filtros actuales; clic = ver esa civ. */
    void ttMostrarRanking() {
        List<String> ranking = ttRankingCivs();
        if (ranking.isEmpty()) return;
        JPopupMenu pm = new JPopupMenu();
        DefaultListModel<String> modelo = new DefaultListModel<>();
        for (String k : ranking) modelo.addElement(k);
        JList<String> lista = new JList<>(modelo);
        lista.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean foc) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, value, index, sel, foc);
                String k = String.valueOf(value);
                CivAgg a = ttWrPorCiv.get(k);
                Color col = sel ? lab.getForeground() : colorWr(a.w(), a.n());
                lab.setText("<html><span style='color:gray'>" + (index + 1) + ".</span> &nbsp;" + escapeHtml(tt.nombreCiv(k)) + " &nbsp;<font color='" + colorHex(col) + "'><b>" + escapeHtml(pct1(a.wr())) + "</b></font> <span style='color:gray;font-size:9px'>" + miles(a.n()) + "</span></html>");
                lab.setIcon(iconoCiv(k, 18));
                lab.setIconTextGap(6);
                lab.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 8));
                return lab;
            }
        });
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (ttCivPedida != null) lista.setSelectedValue(ttCivPedida, true);
        lista.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int i = lista.locationToIndex(e.getPoint());
                if (i < 0) return;
                String k = modelo.get(i);
                pm.setVisible(false);
                ttCivCombo.setSelectedItem(tt.nombreCiv(k));
            }
        });
        JScrollPane sp = new JScrollPane(lista);
        sp.setPreferredSize(new Dimension(300, Math.min(420, 24 * ranking.size() + 6)));
        pm.add(sp);
        pm.show(ttPuestoBtn, ttPuestoBtn.getWidth() - sp.getPreferredSize().width - 4, -sp.getPreferredSize().height - 8);
    }
}
