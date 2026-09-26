package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.TwitchService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static dev.tirador.aoe2radar.util.I18n.t;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DirectosPresenter con Tareas.EN_LINEA (sin hilos) y un doble de TwitchService y de la Pantalla: comprueba el
 * throttle y el anti-solape del barrido de Twitch (vigilarTwitch), qué pasa con éxito, con fallo y si el servicio
 * lanza, y la descarga de miniaturas (cargarMiniaturas). No usa Swing.
 */
class DirectosPresenterTest {

    /** Un TwitchService de mentira: cuenta llamadas y devuelve lo que el test prepare. */
    static class ServicioFalso implements TwitchService {
        int barrerLlamadas;
        List<Player> ultimosVisibles;
        Resultado resultado = new Resultado(Map.of(), List.of(), false);
        boolean barrerLanza;
        double multiplicador = 1.0;
        final Map<String, Miniatura> miniaturas = new HashMap<>();
        List<String> loginsPedidos = new ArrayList<>();
        Runnable alBarrer;   // para simular reentrada desde dentro de barrer()

        List<Player> ultimosOtros;
        @Override public Resultado barrer(List<Player> visibles, List<Player> otrosVigilados) {
            ultimosOtros = otrosVigilados;
            return barrer(visibles);
        }
        @Override public Resultado barrer(List<Player> visibles) {
            barrerLlamadas++;
            ultimosVisibles = visibles;
            if (barrerLanza) throw new RuntimeException("fallo simulado de red");
            if (alBarrer != null) alBarrer.run();
            return resultado;
        }
        @Override public Miniatura miniatura(String login, int ancho, int alto) {
            loginsPedidos.add(login);
            return miniaturas.get(login);
        }
        @Override public double multiplicador() { return multiplicador; }
    }

    /** La Pantalla de mentira: guarda lo que el presentador le pide, como haría DirectosView, y en qué orden. */
    static class PantallaFalsa implements DirectosPresenter.Pantalla {
        List<String[]> directosAoE2;
        int poblarVeces;
        String estado = "";
        int repintarVeces;
        boolean seleccionada = true;
        final Map<String, TwitchService.Miniatura> miniaturasListas = new HashMap<>();
        final List<String> ordenMiniaturas = new ArrayList<>();
        final List<String> orden = new ArrayList<>();   // orden de llamada entre estado/repintarLista/poblarDirectos/actualizarDirectosAoE2

        @Override public void actualizarDirectosAoE2(List<String[]> filas) { orden.add("actualizarDirectosAoE2"); directosAoE2 = filas; }
        @Override public void poblarDirectos() { orden.add("poblarDirectos"); poblarVeces++; }
        @Override public void estado(String texto) { orden.add("estado"); estado = texto; }
        @Override public void repintarLista() { orden.add("repintarLista"); repintarVeces++; }
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

    /** Supplier de conveniencia: vigilarTwitch ya no acepta la lista directamente (se lee tras las guardas). */
    private static Supplier<List<Player>> visibles(List<Player> l) { return () -> l; }

    // ----- vigilarTwitch: throttle y anti-solape --------------------------------------

    @Test void primera_llamada_siempre_barre() {
        presenter.vigilarTwitch(visibles(List.of()));
        assertEquals(1, servicio.barrerLlamadas);
    }

    @Test void segunda_llamada_inmediata_no_barre_por_el_throttle() {
        presenter.vigilarTwitch(visibles(List.of()));
        presenter.vigilarTwitch(visibles(List.of()));   // dentro de los 170s * multiplicador: se descarta
        assertEquals(1, servicio.barrerLlamadas);
    }

    @Test void reiniciarThrottle_permite_barrer_de_nuevo_en_el_acto() {
        presenter.vigilarTwitch(visibles(List.of()));
        presenter.reiniciarThrottle();
        presenter.vigilarTwitch(visibles(List.of()));
        assertEquals(2, servicio.barrerLlamadas);
    }

    @Test void una_llamada_reentrante_mientras_barre_no_relanza_el_barrido() {
        // El throttle NO puede ser el que bloquee esta reentrada (por eso se reinicia antes): lo que se prueba es
        // el anti-solape (vigilandoTwitch). Si se quita el "if (vigilandoTwitch) return;", esta llamada reentrante
        // vuelve a pasar el throttle (recién reiniciado) y barre otra vez -> el test se pone en rojo.
        servicio.alBarrer = () -> { presenter.reiniciarThrottle(); presenter.vigilarTwitch(visibles(List.of())); };
        presenter.vigilarTwitch(visibles(List.of()));
        assertEquals(1, servicio.barrerLlamadas);
    }

    @Test void pasa_la_lista_de_visibles_tal_cual_al_servicio() {
        List<Player> lista = List.of(new Player(1L, "Uno", ""), new Player(2L, "Dos", ""));
        presenter.vigilarTwitch(visibles(lista));
        assertSame(lista, servicio.ultimosVisibles);
    }

    /** Revisión 1.3, F10: los demás vigilados (otros grupos, fuente de Live now) llegan también al servicio. */
    @Test void pasa_tambien_los_otros_vigilados_al_servicio() {
        List<Player> lista = List.of(new Player(1L, "Uno", ""));
        List<Player> otros = List.of(new Player(9L, "DelTop", ""));
        presenter.vigilarTwitch(visibles(lista), () -> otros);
        assertSame(lista, servicio.ultimosVisibles);
        assertSame(otros, servicio.ultimosOtros);
    }

    @Test void los_visibles_se_leen_solo_si_pasan_las_guardas() {
        // segunda llamada: el throttle la descarta antes de mirar la lista (como en la 1.1: playersModel se lee
        // dentro de vigilarTwitch, después de los "return" de anti-solape y throttle)
        boolean[] evaluado = { false };
        presenter.vigilarTwitch(visibles(List.of()));
        presenter.vigilarTwitch(() -> { evaluado[0] = true; return List.of(); });
        assertFalse(evaluado[0]);
    }

    // ----- vigilarTwitch: si el servicio lanza -----------------------------------------

    @Test void barrido_que_lanza_libera_el_anti_solape_pero_no_repinta_ni_repuebla() {
        servicio.barrerLanza = true;
        presenter.vigilarTwitch(visibles(List.of()));
        assertEquals(0, pantalla.repintarVeces);
        assertEquals(0, pantalla.poblarVeces);
        assertEquals("", pantalla.estado);
        assertNull(pantalla.directosAoE2);
        // vigilandoTwitch volvió a false: una segunda llamada (con el throttle reiniciado) sí barre
        servicio.barrerLanza = false;
        presenter.reiniciarThrottle();
        presenter.vigilarTwitch(visibles(List.of()));
        assertEquals(2, servicio.barrerLlamadas);
    }

    // ----- vigilarTwitch: resultado ----------------------------------------------------

    @Test void barrido_ok_actualiza_twitchLive_y_directosAoE2_y_repinta() {
        Map<Long, String[]> enVivo = Map.of(1L, new String[]{ "canal1", "titulo", "10" });
        List<String[]> filas = List.<String[]>of(new String[]{ "canal1", "Canal1", "titulo", "es", "10" });
        servicio.resultado = new TwitchService.Resultado(enVivo, filas, false);
        twitchLive.put(99L, new String[]{ "viejo", "x", "0" });   // debe desaparecer: se sustituye, no se acumula
        presenter.vigilarTwitch(visibles(List.of()));
        assertEquals(enVivo.keySet(), twitchLive.keySet());
        assertArrayEquals(enVivo.get(1L), twitchLive.get(1L));
        assertEquals(filas, pantalla.directosAoE2);
        assertEquals(1, pantalla.repintarVeces);
    }

    @Test void barrido_ok_repuebla_si_la_pestana_esta_seleccionada() {
        pantalla.seleccionada = true;
        presenter.vigilarTwitch(visibles(List.of()));
        assertEquals(1, pantalla.poblarVeces);
    }

    @Test void barrido_ok_no_repuebla_si_la_pestana_no_esta_seleccionada() {
        pantalla.seleccionada = false;
        presenter.vigilarTwitch(visibles(List.of()));
        assertEquals(0, pantalla.poblarVeces);
    }

    @Test void barrido_ok_respeta_el_orden_actualizar_repintar_repoblar() {
        servicio.resultado = new TwitchService.Resultado(Map.of(), List.of(), false);
        presenter.vigilarTwitch(visibles(List.of()));
        assertEquals(List.of("actualizarDirectosAoE2", "repintarLista", "poblarDirectos"), pantalla.orden);
    }

    @Test void barrido_con_fallo_conserva_twitchLive_y_avisa_con_el_texto_exacto() {
        twitchLive.put(1L, new String[]{ "canal-bueno", "t", "5" });
        servicio.resultado = new TwitchService.Resultado(Map.of(), List.of(), true);
        presenter.vigilarTwitch(visibles(List.of()));
        assertArrayEquals(new String[]{ "canal-bueno", "t", "5" }, twitchLive.get(1L));   // no se vacía
        assertNull(pantalla.directosAoE2);   // no se toca la lista de canales
        assertEquals(t("Twitch sin respuesta ahora mismo — mostrando la última lista buena.",
                "Twitch not responding right now — showing the last good list."), pantalla.estado);
        assertEquals(1, pantalla.repintarVeces);   // el repintado sí ocurre igual
    }

    @Test void barrido_con_fallo_respeta_el_orden_estado_repintar_repoblar() {
        servicio.resultado = new TwitchService.Resultado(Map.of(), List.of(), true);
        presenter.vigilarTwitch(visibles(List.of()));
        assertEquals(List.of("estado", "repintarLista", "poblarDirectos"), pantalla.orden);
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
