package dev.tirador.aoe2radar.ui;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.border.TitledBorder;
import java.awt.Component;

/**
 * Los componentes concretos de la ventana que el cambio de tema tiene que repintar: la propia ventana y su
 * menú de configuración (para refrescar el look&feel con updateComponentTreeUI), y los textos/botones cuyo
 * color o tamaño depende de si el tema es claro u oscuro (nota, firma, pistas de la Watchlist, botones
 * especiales). La ventana implementa esta interfaz para que TemaApp (en ui) no necesite conocer la clase
 * SpoilerFreeRecs (paquete raíz): la misma inversión de dependencias que ya usan Navegacion y MenusJugador.
 */
public interface ComponentesTema {
    /** La ventana en sí, para SwingUtilities.updateComponentTreeUI. */
    Component raiz();

    JPopupMenu configMenu();
    JLabel nota();
    JLabel firma();
    JLabel watchPista1();
    JLabel watchPista2();
    JLabel watchPista3();
    TitledBorder tituloWatch();
    JTable table();
    JButton azarBtn();
    JButton gteBtn();
    JToggleButton resultadosBtn();
    JButton cafeBtn();

    /** true si el modo «Mostrar resultados» está activo (cambia el color de la nota). */
    boolean mostrarResultados();
}
