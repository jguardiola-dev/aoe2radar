package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.IllegalComponentStateException;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lo de ui.Listas que no necesita abrir una ventana de verdad: filaBarra (JPanel suelto, nunca un JDialog) y
 * enlaceVerTodo. mostrarTablaCompleta/mostrarListaCompleta abren un JDialog visible, así que quedan fuera de
 * este test (los cubre RegresionCapturas, con pantalla). Todo pasa por el EDT con invokeAndWait: filaBarra crea
 * componentes Swing y el clic simulado dispara un MouseListener, que también debe correr en el EDT.
 */
class ListasTest {

    final Listas listas = new Listas(null, v -> { });

    @Test void filaBarraSinAlClicarNoPoneCursorDeMano() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel f = listas.filaBarra("nombre", 0.5, "50 %", Color.GRAY, null);
            assertNotEquals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), f.getCursor());
        });
    }

    @Test void filaBarraConAlClicarPoneCursorDeMano() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel f = listas.filaBarra("nombre", 0.5, "50 %", Color.GRAY, null, () -> { });
            assertEquals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), f.getCursor());
        });
    }

    @Test void enlaceVerTodoEjecutaElRunnableAlHacerClic() throws Exception {
        AtomicBoolean ejecutado = new AtomicBoolean(false);
        SwingUtilities.invokeAndWait(() -> {
            JButton b = listas.enlaceVerTodo(42, () -> ejecutado.set(true));
            b.doClick();
        });
        assertTrue(ejecutado.get());
    }

    /** El clic con Ctrl marca «pestaña nueva» antes de ejecutar la acción y lo desmarca justo después (ver Listas.filaBarra). */
    @Test void filaBarraClicConCtrlMarcaYDesmarcaAlrededorDelRunnable() throws Exception {
        List<Boolean> recibidos = new ArrayList<>();
        AtomicReference<Boolean> ultimoDentroDelRunnable = new AtomicReference<>();
        Listas listasPropias = new Listas(null, recibidos::add);
        SwingUtilities.invokeAndWait(() -> {
            JPanel fila = listasPropias.filaBarra("nombre", 0.5, "50 %", Color.GRAY, null,
                    () -> ultimoDentroDelRunnable.set(recibidos.get(recibidos.size() - 1)));
            MouseEvent clicCtrl = new MouseEvent(fila, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                    InputEvent.CTRL_DOWN_MASK, 5, 5, 1, false, MouseEvent.BUTTON1);
            fila.dispatchEvent(clicCtrl);
        });
        assertEquals(List.of(true, false), recibidos);
        assertEquals(Boolean.TRUE, ultimoDentroDelRunnable.get());
    }

    /**
     * Fila 111 / decisión 7: esFilaJugador se debe fijar AL CONSTRUIR la fila, no leerse en el clic derecho (que
     * llega mucho después, cuando quien pinta varias listas seguidas ya volvió a poner esFilaJugador a false en
     * su finally). Aquí no hay ventana real (los tests de Listas no toman pantalla, ver la cabecera del archivo),
     * así que se comprueba que el menú SE INTENTA abrir: JPopupMenu.show() exige un componente visible en
     * pantalla y lanza IllegalComponentStateException si no lo está (comprobado aparte, sin pantalla, con un
     * JPanel suelto: siempre pasa por esa comprobación de AWT antes de dibujar nada). Que salte esa excepción
     * concreta prueba que el código pasó el guard de "!filaJugador" y llegó a pm.show(); si el guard leyera el
     * campo compartido esFilaJugador (el bug), habría vuelto antes y no lanzaría nada.
     */
    @Test void filaBarraClicDerechoUsaElEsFilaJugadorDeLaConstruccionNoElDelClic() throws Exception {
        Listas listasPropias = new Listas(null, v -> { });
        JPanel[] filaRef = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            listasPropias.esFilaJugador = true;
            filaRef[0] = listasPropias.filaBarra("nombre", 0.5, "50 %", Color.GRAY, null, () -> { });
            listasPropias.esFilaJugador = false;   // como el finally de quien pinta la lista, ya con la fila construida
        });
        MouseEvent clicDerecho = new MouseEvent(filaRef[0], MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                InputEvent.BUTTON3_DOWN_MASK, 5, 5, 1, true, MouseEvent.BUTTON3);
        InvocationTargetException lanzada = assertThrows(InvocationTargetException.class,
                () -> SwingUtilities.invokeAndWait(() -> filaRef[0].dispatchEvent(clicDerecho)));
        assertInstanceOf(IllegalComponentStateException.class, lanzada.getCause(),
                "el menú se intentó abrir (pm.show llegó a pedir la posición en pantalla)");
    }

    /** Contraparte: si la fila nunca fue de jugador, el clic derecho no debe intentar abrir nada (sin excepción). */
    @Test void filaBarraClicDerechoSinEsFilaJugadorNoAbreMenu() throws Exception {
        Listas listasPropias = new Listas(null, v -> { });
        JPanel[] filaRef = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            listasPropias.esFilaJugador = false;
            filaRef[0] = listasPropias.filaBarra("nombre", 0.5, "50 %", Color.GRAY, null, () -> { });
        });
        MouseEvent clicDerecho = new MouseEvent(filaRef[0], MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                InputEvent.BUTTON3_DOWN_MASK, 5, 5, 1, true, MouseEvent.BUTTON3);
        assertDoesNotThrow(() -> SwingUtilities.invokeAndWait(() -> filaRef[0].dispatchEvent(clicDerecho)));
    }
}
