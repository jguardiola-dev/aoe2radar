package dev.tirador.aoe2radar.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lo de ui.Listas que no necesita abrir una ventana de verdad: filaBarra (JPanel suelto, nunca un JDialog) y
 * enlaceVerTodo. mostrarTablaCompleta/mostrarListaCompleta abren un JDialog visible, así que quedan fuera de
 * este test (los cubre RegresionCapturas, con pantalla).
 */
class ListasTest {

    final Listas listas = new Listas(null, v -> { });

    @Test void filaBarraSinAlClicarNoPoneCursorDeMano() {
        JPanel f = listas.filaBarra("nombre", 0.5, "50 %", Color.GRAY, null);
        assertNotEquals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), f.getCursor());
    }

    @Test void filaBarraConAlClicarPoneCursorDeMano() {
        JPanel f = listas.filaBarra("nombre", 0.5, "50 %", Color.GRAY, null, () -> { });
        assertEquals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), f.getCursor());
    }

    @Test void enlaceVerTodoEjecutaElRunnableAlHacerClic() {
        AtomicBoolean ejecutado = new AtomicBoolean(false);
        JButton b = listas.enlaceVerTodo(42, () -> ejecutado.set(true));
        b.doClick();
        assertTrue(ejecutado.get());
    }
}
