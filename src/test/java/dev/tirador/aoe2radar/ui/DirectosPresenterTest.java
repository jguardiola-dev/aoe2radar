package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.TwitchService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.util.I18n.t;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DirectosPresenter con Tareas.EN_LINEA (sin hilos) y un doble de TwitchService y de la Pantalla: comprueba el
 * throttle y el anti-solape del barrido de Twitch (vigilarTwitch), qué pasa con éxito y con fallo, y la descarga
 * de miniaturas (cargarMiniaturas). No usa Swing.
 */
class DirectosPresenterTest {

    /** Un TwitchService de mentira: cuenta llamadas y devuelve lo que el test prepare. */
    static class ServicioFalso implements TwitchService {
        int barrerLlamadas;
        List<Player> ultimosVisibles;
        Resultado resultado = new Resultado(Map.of(), List.of(), false);
        double multiplicador = 1.0;
        final Map<String, Miniatura> miniaturas = new HashMap<>();
        List<String> loginsPedidos = new ArrayList<>();
        Runnable alBarrer;   // para simular reentrada desde dentro de barrer()

        @Override public Resultado barrer(List<Player> visibles) {
            barrerLlamadas++;
            ultimosVisibles = visibles;
            if (alBarrer != null) alBarrer.run();
            return resultado;
        }
        @Override public Miniatura miniatura(String login, int ancho, int alto) {
            loginsPedidos.add(login);
            return miniaturas.get(login);
        }
        @Override public double multiplicador() { return multiplicador; }
    }

    /** La Pantalla de mentira: guarda lo que el presentador le pide, como haría DirectosView. */
    static class PantallaFalsa implements DirectosPresenter.Pantalla {
        List<String[]> directosAoE2;
        int poblarVeces;
        String estado = "";
        int repintarVeces;
        boolean seleccionada = true;
        final Map<String, TwitchService.Miniatura> miniaturasListas = new HashMap<>();
        final List<String> ordenMiniaturas = new ArrayList<>();

        @Override public void actualizarDirectosAoE2(List<String[]> filas) { directosAoE2 = filas; }
        @Override public void poblarDirectos() { poblarVeces++; }
        @Override public void estado(String texto) { estado = texto; }
        @Override public void repintarLista() { repintarVeces++; }
        @Override public boolean seleccionada() { return seleccionada; }
        @Override public void miniaturaLista(String login, TwitchService.Miniatura miniatura, long enMs) {
            miniaturasListas.put(login, miniatura);
            ordenMiniaturas.add(login);
        }
    }

    final ServicioFalso servicio = new ServicioFalso();
    final Map<Long, String[]> twitchLive = new HashMap<>();
    final PantallaFalsa pantalla = new PantallaFalsa();
    final DirectosPresenter presenter = new DirectosPresenter(servicio, twitchLive, Tareas.EN_LINEA, pantalla);

    // ----- vigilarTwitch: throttle y anti-solape --------------------------------------

    @Test void primera_llamada_siempre_barre() {
        presenter.vigilarTwitch(List.of());
        assertEquals(1, servicio.barrerLlamadas);
    }

    @Test void segunda_llamada_inmediata_no_barre_por_el_throttle() {
        presenter.vigilarTwitch(List.of());
        presenter.vigilarTwitch(List.of());   // dentro de los 170s * multiplicador: se descarta
        assertEquals(1, servicio.barrerLlamadas);
    }

    @Test void reiniciarThrottle_permite_barrer_de_nuevo_en_el_acto() {
        presenter.vigilarTwitch(List.of());
        presenter.reiniciarThrottle();
        presenter.vigilarTwitch(List.of());
        assertEquals(2, servicio.barrerLlamadas);
    }

    @Test void una_llamada_reentrante_mientras_barre_no_relanza_el_barrido() {
        // simula F5 pulsado mientras el barrido anterior sigue en curso (vigilandoTwitch == true)
        servicio.alBarrer = () -> presenter.vigilarTwitch(List.of());
        presenter.vigilarTwitch(List.of());
        assertEquals(1, servicio.barrerLlamadas);
    }

    @Test void pasa_la_lista_de_visibles_tal_cual_al_servicio() {
        List<Player> visibles = List.of(new Player(1L, "Uno", ""), new Player(2L, "Dos", ""));
        presenter.vigilarTwitch(visibles);
        assertSame(visibles, servicio.ultimosVisibles);
    }

    // ----- vigilarTwitch: resultado ----------------------------------------------------

    @Test void barrido_ok_actualiza_twitchLive_y_directosAoE2_y_repinta() {
        Map<Long, String[]> enVivo = Map.of(1L, new String[]{ "canal1", "titulo", "10" });
        List<String[]> filas = List.<String[]>of(new String[]{ "canal1", "Canal1", "titulo", "es", "10" });
        servicio.resultado = new TwitchService.Resultado(enVivo, filas, false);
        twitchLive.put(99L, new String[]{ "viejo", "x", "0" });   // debe desaparecer: se sustituye, no se acumula
        presenter.vigilarTwitch(List.of());
        assertEquals(enVivo.keySet(), twitchLive.keySet());
        assertArrayEquals(enVivo.get(1L), twitchLive.get(1L));
        assertEquals(filas, pantalla.directosAoE2);
        assertEquals(1, pantalla.repintarVeces);
    }

    @Test void barrido_ok_repuebla_si_la_pestana_esta_seleccionada() {
        pantalla.seleccionada = true;
        presenter.vigilarTwitch(List.of());
        assertEquals(1, pantalla.poblarVeces);
    }

    @Test void barrido_ok_no_repuebla_si_la_pestana_no_esta_seleccionada() {
        pantalla.seleccionada = false;
        presenter.vigilarTwitch(List.of());
        assertEquals(0, pantalla.poblarVeces);
    }

    @Test void barrido_con_fallo_conserva_twitchLive_y_avisa_con_el_texto_exacto() {
        twitchLive.put(1L, new String[]{ "canal-bueno", "t", "5" });
        servicio.resultado = new TwitchService.Resultado(Map.of(), List.of(), true);
        presenter.vigilarTwitch(List.of());
        assertArrayEquals(new String[]{ "canal-bueno", "t", "5" }, twitchLive.get(1L));   // no se vacía
        assertNull(pantalla.directosAoE2);   // no se toca la lista de canales
        assertEquals(t("Twitch sin respuesta ahora mismo — mostrando la última lista buena.",
                "Twitch not responding right now — showing the last good list."), pantalla.estado);
        assertEquals(1, pantalla.repintarVeces);   // el repintado sí ocurre igual
    }

    // ----- cargarMiniaturas -------------------------------------------------------------

    @Test void cargarMiniaturas_con_lista_vacia_no_pide_nada() {
        presenter.cargarMiniaturas(List.of());
        assertTrue(servicio.loginsPedidos.isEmpty());
    }

    @Test void cargarMiniaturas_pide_cada_login_con_el_tamano_de_la_1_1() {
        servicio.miniaturas.put("uno", new TwitchService.Miniatura(DirectosPresenter.MINI_W, DirectosPresenter.MINI_H, new int[0]));
        presenter.cargarMiniaturas(List.of("uno"));
        assertEquals(List.of("uno"), servicio.loginsPedidos);
    }

    @Test void cargarMiniaturas_avisa_a_la_pantalla_solo_de_las_que_llegaron() {
        TwitchService.Miniatura m1 = new TwitchService.Miniatura(96, 54, new int[]{ 1, 2, 3 });
        servicio.miniaturas.put("con-foto", m1);
        // "sin-foto" no está en el mapa: miniatura() devuelve null (canal caído)
        presenter.cargarMiniaturas(List.of("con-foto", "sin-foto"));
        assertEquals(List.of("con-foto"), pantalla.ordenMiniaturas);
        assertSame(m1, pantalla.miniaturasListas.get("con-foto"));
    }
}
