package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Player;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static dev.tirador.aoe2radar.util.I18n.t;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DirectosView con Tareas.EN_LINEA y el doble de TwitchService de DirectosPresenterTest: comprueba lo que hacen sus
 * controles (sin ventana: solo se construyen los componentes). Revisión 1.3, F12.
 */
class DirectosViewTest {

    final DirectosPresenterTest.ServicioFalso servicio = new DirectosPresenterTest.ServicioFalso();
    final DirectosView vista = new DirectosView(servicio, new HashMap<>(), Tareas.EN_LINEA, new DirectosView.Anfitrion() {
        @Override public List<Player> visibles() { return new ArrayList<>(); }
        @Override public void repintarLista() { }
        @Override public void estado(String texto) { }
        @Override public void abrirUrl(String url) { }
        @Override public boolean seleccionada() { return true; }
    });

    static <T extends Component> List<T> buscar(Container c, Class<T> tipo) {
        List<T> out = new ArrayList<>();
        for (Component x : c.getComponents()) {
            if (tipo.isInstance(x)) out.add(tipo.cast(x));
            if (x instanceof Container hijo) out.addAll(buscar(hijo, tipo));
        }
        return out;
    }

    /** F12: el botón «Refrescar» refresca de verdad, aunque el último barrido tenga menos de 170 s (como F5). */
    @Test void botonRefrescar_barreAunqueElUltimoBarridoSeaReciente() {
        vista.alAbrir();   // el refresco de cortesía al abrir: primer barrido
        assertEquals(1, servicio.barrerLlamadas);
        List<JButton> botones = buscar(vista.panel(), JButton.class);
        botones.removeIf(b -> !t("Refrescar", "Refresh").equals(b.getText()));   // fuera las flechas de combos y barras
        assertEquals(1, botones.size(), "Directos tiene un solo botón «Refrescar»");
        botones.get(0).doClick();   // segundos después: antes se ignoraba en silencio (ritmo de 170 s)
        assertEquals(2, servicio.barrerLlamadas);
    }
}
