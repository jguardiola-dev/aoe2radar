package dev.tirador.aoe2radar.ui;

import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static dev.tirador.aoe2radar.util.Config.leerConfig;

/**
 * Tema claro/oscuro con FlatLaf, cargado por reflexión (Class.forName) para no obligar a tener su jar en el
 * classpath en tiempo de compilación: si no está, aplicarTema devuelve false y la app sigue con el look&feel
 * por defecto. Además de fijar el look&feel, ajusta el color de fila alterna, el tamaño de letra y los
 * componentes propios de la ventana que el tema no resuelve solo (a través de ComponentesTema). El «qué tema
 * hay puesto ahora» (ui.Tema.temaOscuroActivo) vive aparte porque también lo leen las vistas al pintar.
 */
public final class TemaApp {
    private TemaApp() { }

    public static final String TEMA_CLARO = "claro", TEMA_OSCURO = "oscuro", TEMA_SISTEMA = "sistema";

    /** hay jar de FlatLaf en el classpath */
    public static boolean flatLafDisponible;

    /** Aplica el tema con FlatLaf si su jar está en el classpath. Con ventana,
     *  refresca la UI en caliente. Devuelve false si FlatLaf no está. */
    public static boolean aplicarTema(String tema, ComponentesTema ventana) {
        boolean oscuro = switch (tema) {
            case TEMA_OSCURO -> true;
            case TEMA_CLARO  -> false;
            default          -> sistemaEnOscuro();
        };
        try {
            try {   // acento en el azul del logo 12T (si esta versión de FlatLaf lo soporta)
                Class.forName("com.formdev.flatlaf.FlatLaf")
                        .getMethod("setGlobalExtraDefaults", Map.class)
                        .invoke(null, Map.of("@accentColor", "#1d428a"));
            } catch (Throwable ignored) {}
            String clase = oscuro ? "com.formdev.flatlaf.FlatDarkLaf" : "com.formdev.flatlaf.FlatLightLaf";
            UIManager.setLookAndFeel((LookAndFeel) Class.forName(clase).getDeclaredConstructor().newInstance());
            Tema.temaOscuroActivo = oscuro;
            Color fondo = UIManager.getColor("Table.background");
            Color letra = UIManager.getColor("Table.foreground");
            if (fondo != null && letra != null)
                UIManager.put("Table.alternateRowColor", mezcla(fondo, letra, 0.06f));
            Font base = UIManager.getFont("defaultFont");
            if (base != null)
                UIManager.put("defaultFont", base.deriveFont((float) tamLetra(leerConfig("letra", "grande"))));
            if (ventana != null) {
                SwingUtilities.updateComponentTreeUI(ventana.raiz());
                if (ventana.configMenu() != null) SwingUtilities.updateComponentTreeUI(ventana.configMenu());
                refrescarSueltos(ventana.raiz(), java.util.Arrays.asList(java.awt.Window.getWindows()), new java.util.ArrayList<>(POPUPS_SUELTOS));
                ajustarGrises(ventana, oscuro);
                ajustarBotonesEspeciales(ventana, oscuro);
                ajustarFuentesSecundarias(ventana);
            }
            return true;
        } catch (Throwable t) {      // sin el jar: ClassNotFoundException
            return false;
        }
    }

    /** Popups guardados en campos que el cambio de tema no alcanza recorriendo ventanas: mientras están ocultos no
     *  cuelgan de ninguna. Referencias débiles: registrar uno no lo retiene. Solo en el EDT. */
    private static final java.util.Set<javax.swing.JPopupMenu> POPUPS_SUELTOS =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** Apunta un popup que se guarda en un campo y se reutiliza (el de clan, el de nick…) para que un cambio de tema
     *  en caliente también le llegue (F12 de la revisión general). Los que se crean en cada clic no hace falta. EDT. */
    public static javax.swing.JPopupMenu registrarPopup(javax.swing.JPopupMenu popup) {
        if (popup != null) POPUPS_SUELTOS.add(popup);
        return popup;
    }

    /**
     * El cambio de tema en caliente, más allá del árbol de la ventana principal (F12 de la revisión general; ya en la
     * 1.1 se quedaban con el tema viejo): las demás ventanas vivas, visibles u ocultas (diálogos guardados en campos,
     * la tarjeta flotante de la Watchlist, «Mi partida»…) y los popups registrados. EDT.
     */
    static void refrescarSueltos(java.awt.Component raiz, java.util.Collection<? extends java.awt.Window> ventanas,
                                 java.util.Collection<javax.swing.JPopupMenu> popups) {
        for (java.awt.Window w : ventanas) if (w != raiz) SwingUtilities.updateComponentTreeUI(w);
        for (javax.swing.JPopupMenu pm : popups) SwingUtilities.updateComponentTreeUI(pm);
    }

    /** Windows: AppsUseLightTheme = 0 -> apps en modo oscuro.
     *  Fuera de Windows o si falla la consulta: claro. */
    static boolean sistemaEnOscuro() {
        try {
            Process p = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme").redirectErrorStream(true).start();
            String salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return salida.contains("0x0");
        } catch (Exception e) {
            return false;
        }
    }

    public static String temaValido(String t) {
        return switch (t) { case TEMA_CLARO, TEMA_OSCURO -> t; default -> TEMA_SISTEMA; };
    }

    /** Mezcla dos colores: base con una fracción f del otro (rayado de tabla). */
    static Color mezcla(Color a, Color b, float f) {
        return new Color(
                Math.round(a.getRed()   * (1 - f) + b.getRed()   * f),
                Math.round(a.getGreen() * (1 - f) + b.getGreen() * f),
                Math.round(a.getBlue()  * (1 - f) + b.getBlue()  * f));
    }

    static int tamLetra(String letra) {
        return switch (letra) { case "grande" -> 14; case "muygrande" -> 16; default -> 12; };
    }

    /** Reescala los textos secundarios (nota, firma, pistas de la Watchlist,
     *  título y alto de fila de la tabla) acorde a la fuente base actual. */
    public static void ajustarFuentesSecundarias(ComponentesTema v) {
        Font base = UIManager.getFont("defaultFont");
        float b = base != null ? base.getSize2D() : 12f;
        float peq = Math.max(10f, b - 1f);
        if (v.nota() != null) v.nota().setFont(v.nota().getFont().deriveFont(Font.PLAIN, peq));
        if (v.firma() != null) v.firma().setFont(v.firma().getFont().deriveFont(Font.PLAIN, peq));
        if (v.watchPista1() != null) v.watchPista1().setFont(v.watchPista1().getFont().deriveFont(Font.PLAIN, peq));
        if (v.watchPista2() != null) v.watchPista2().setFont(v.watchPista2().getFont().deriveFont(Font.PLAIN, peq));
        if (v.watchPista3() != null) v.watchPista3().setFont(v.watchPista3().getFont().deriveFont(Font.PLAIN, peq));
        if (v.tituloWatch() != null && base != null)
            v.tituloWatch().setTitleFont(base.deriveFont(Font.BOLD, b + 1f));
        v.table().setRowHeight(Math.round(b) + 12);
        v.raiz().repaint();
    }

    /** Modos especiales en contorno con los colores del logo, legibles en
     *  claro y en oscuro. «Buscar partidas» conserva el protagonismo como
     *  botón por defecto. */
    public static void ajustarBotonesEspeciales(ComponentesTema v, boolean oscuro) {
        String azul = oscuro ? "#7da2e0" : "#1d428a";
        String rojo = oscuro ? "#e0707f" : "#c8102e";
        v.azarBtn().putClientProperty("FlatLaf.style",
                "foreground: " + azul + "; borderColor: " + azul + "; hoverBorderColor: " + azul + "; focusedBorderColor: " + azul);
        v.gteBtn().putClientProperty("FlatLaf.style",
                "foreground: " + rojo + "; borderColor: " + rojo + "; hoverBorderColor: " + rojo + "; focusedBorderColor: " + rojo);
        String ambar = oscuro ? "#d9a55b" : "#9a6b1f";
        if (v.resultadosBtn() != null)
            v.resultadosBtn().putClientProperty("FlatLaf.style",
                    "foreground: " + ambar + "; borderColor: " + ambar + "; hoverBorderColor: " + ambar + "; focusedBorderColor: " + ambar);
        v.cafeBtn().putClientProperty("FlatLaf.style",
                "foreground: " + ambar + "; borderColor: " + ambar + "; hoverBorderColor: " + ambar + "; focusedBorderColor: " + ambar);
        v.azarBtn().updateUI();
        v.gteBtn().updateUI();
        v.cafeBtn().updateUI();
    }

    /** Grises de la nota y la firma legibles en ambos temas. */
    public static void ajustarGrises(ComponentesTema v, boolean oscuro) {
        Color g1 = oscuro ? new Color(170, 170, 170) : new Color(90, 90, 90);
        Color g2 = oscuro ? new Color(150, 150, 150) : new Color(120, 120, 120);
        if (v.nota()  != null) v.nota().setForeground(v.mostrarResultados()
                ? (oscuro ? new Color(0xd9, 0xa5, 0x5b) : new Color(0x9a, 0x6b, 0x1f)) : g1);
        if (v.firma() != null) v.firma().setForeground(g2);
        if (v.watchPista1() != null) v.watchPista1().setForeground(g1);
        if (v.watchPista2() != null) v.watchPista2().setForeground(g1);
        if (v.watchPista3() != null) v.watchPista3().setForeground(g1);
    }
}
