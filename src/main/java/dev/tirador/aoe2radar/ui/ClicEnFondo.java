package dev.tirador.aoe2radar.ui;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JLayeredPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.table.JTableHeader;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseEvent;

/**
 * Clic en cualquier fondo de la ventana (que no sea un control) = quitar la selección de la
 * Watchlist, como el Explorador de Windows. Sale de lo que era SpoilerFreeRecs.montarVentana (hoy
 * CableadoCromo.montarVentana; el listener AWT global, registrado una sola vez) y de esFondoDeseleccionable: qué cuenta como "fondo"
 * mezcla excepciones de varias vistas (tabla de partidas, directos, ratings, la cabecera de la
 * watchlist, "Partidas de:"), así que se piden a {@link Anfitrion} en vez de conocerlas por nombre.
 */
public final class ClicEnFondo {

    /** Lo que hace falta de las vistas para decidir si un componente es "fondo" y para actuar al clicar uno. */
    public interface Anfitrion {
        /** Cabecera de columnas de la Watchlist (ordenar no suelta la selección). */
        JLabel cabeceraWatchlist();
        /** «Partidas de:» (sus nombres son clicables, no sueltan la selección). */
        JPanel sujetosPanel();
        /** Tabla de Partidas. */
        JTable tablaPartidas();
        /** Tabla de Directos, o null si esa vista aún no existe. */
        JTable tablaDirectos();
        /** Panel de Ratings (mirar las campanas no suelta la selección), o null si aún no existe. */
        JComponent panelRatings();
        /** La ficha flotante del tech tree se cierra al clicar fuera. */
        void ocultarDetalleTechTree();
        /** ¿Hay selección en la Watchlist ahora mismo? */
        boolean haySeleccionWatchlist();
        /** Suelta la selección de la Watchlist y actualiza el texto de "buscar". */
        void limpiarSeleccionWatchlist();
    }

    private ClicEnFondo() { }

    /** Instala, una sola vez, el filtro global de clics en fondo de {@code ventana}. */
    public static void instalarGlobal(JFrame ventana, Anfitrion anfitrion) {
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (!(ev instanceof MouseEvent me) || me.getID() != MouseEvent.MOUSE_PRESSED) return;
            if (!SwingUtilities.isLeftMouseButton(me) || me.isControlDown() || me.isShiftDown()) return;
            Component c = me.getComponent();
            if (c == null) return;
            Window w = c instanceof Window win ? win : SwingUtilities.getWindowAncestor(c);
            if (w != ventana) return;   // solo la ventana principal (la hover-card y los diálogos, no)
            anfitrion.ocultarDetalleTechTree();   // la ficha flotante se cierra al clicar fuera
            // Un fondo sin listeners no recibe el clic (sube hasta la ventana): miramos qué hay REALMENTE bajo el ratón
            Point p = SwingUtilities.convertPoint(c, me.getPoint(), ventana.getLayeredPane());
            Component bajo = SwingUtilities.getDeepestComponentAt(ventana.getLayeredPane(), p.x, p.y);
            if (bajo == null || !esFondoDeseleccionable(bajo, anfitrion)) return;
            if (anfitrion.haySeleccionWatchlist()) anfitrion.limpiarSeleccionWatchlist();
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    /** ¿Es un fondo (no un control) donde un clic debe soltar la selección de la lista? */
    static boolean esFondoDeseleccionable(Component c, Anfitrion anfitrion) {
        if (c instanceof AbstractButton || c instanceof javax.swing.text.JTextComponent || c instanceof JComboBox
                || c instanceof JList || c instanceof JTable || c instanceof JTableHeader
                || c instanceof JScrollBar || c instanceof JSpinner || c instanceof JMenuBar || c instanceof JPopupMenu
                || c instanceof JSplitPane || c instanceof BasicSplitPaneDivider || c instanceof JProgressBar)
            return false;
        if (c instanceof JLabel l && l.getMouseListeners().length > 0) return false;   // etiquetas clicables (firma, sujetos…)
        JLabel cabLabel = anfitrion.cabeceraWatchlist();
        if (cabLabel != null && SwingUtilities.isDescendingFrom(c, cabLabel)) return false;          // ordenar no suelta
        JPanel sujetosPanel = anfitrion.sujetosPanel();
        if (sujetosPanel != null && SwingUtilities.isDescendingFrom(c, sujetosPanel)) return false;  // «Partidas de:» es clicable
        JTable table = anfitrion.tablaPartidas();
        if (table != null && SwingUtilities.isDescendingFrom(c, table)) return false;
        JTable tablaDirectos = anfitrion.tablaDirectos();
        if (tablaDirectos != null && SwingUtilities.isDescendingFrom(c, tablaDirectos)) return false;
        JComponent panelRatings = anfitrion.panelRatings();
        if (panelRatings != null && SwingUtilities.isDescendingFrom(c, panelRatings)) return false;   // mirar las campanas no suelta la selección (sus puntos desaparecerían)
        // los visores de las tablas (hueco bajo sus filas) tampoco: seleccionar partidas no debe cambiar el filtro de la lista
        for (Component p = c; p != null; p = p.getParent())
            if (p instanceof JScrollPane sp && sp.getViewport() != null
                    && (sp.getViewport().getView() == table || (tablaDirectos != null && sp.getViewport().getView() == tablaDirectos))) return false;
        return c instanceof JPanel || c instanceof JViewport || c instanceof JLabel || c instanceof JRootPane
                || c instanceof JLayeredPane || c instanceof JFrame;
    }
}
