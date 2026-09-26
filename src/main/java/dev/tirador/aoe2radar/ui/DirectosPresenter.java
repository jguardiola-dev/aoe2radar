package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.TwitchService;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * La lógica de Directos sin Swing: el throttle y el anti-solape del barrido de Twitch (vigilarTwitch de la 1.1) y
 * la descarga de miniaturas (cargarMiniaturas). Sale de SpoilerFreeRecs tal cual, salvo que la parte de red y de
 * cache.Canales ahora vive en {@link TwitchService} (obligado por las capas: este presentador no puede importar
 * cache ni java.net) y que ya no toca los campos de Swing directamente: se los pide a {@link Pantalla}, que
 * implementa ui.DirectosView.
 * <p>Hilos: el barrido de Twitch de la 1.1 era un {@code SwingWorker} (hilo del pool, demonio): se relanza con
 * {@code tareas.enFondoDemonio("twitch-vigilar", …)}. Las miniaturas eran un {@code new Thread(...).start()}
 * explícito (no demonio): siguen con {@code tareas.enFondo("miniaturas-twitch", …)}. La vuelta a Swing, siempre con
 * {@code tareas.enUi(…)}, en el mismo orden que la 1.1 (primero el estado, luego el repintado de la lista, luego
 * poblarDirectos()).
 */
public final class DirectosPresenter {

    /** Tamaño de la miniatura en vivo (96×54, igual que la 1.1); MINI_OFFSET es el hueco que deja en la fila. */
    public static final int MINI_W = 96, MINI_H = 54, MINI_OFFSET = MINI_W + 10;

    /** Lo que el presentador necesita de la vista. */
    public interface Pantalla {
        /** Sustituye la lista de canales AoE2 en directo (para poblar la tabla); solo se llama sin fallo. */
        void actualizarDirectosAoE2(List<String[]> filas);
        /** Repuebla la tabla de Directos a partir de directosAoE2/filtro de idioma actuales. */
        void poblarDirectos();
        /** Mensaje de la barra de estado general de la ventana. */
        void estado(String texto);
        /** Repinta la lista de la watchlist (para que se vea el badge de Twitch tras un barrido). */
        void repintarLista();
        /** ¿Está la pestaña de Directos seleccionada ahora mismo? (directosBtn.isSelected() de la 1.1). */
        boolean seleccionada();
        /** Una miniatura ya lista: la vista la monta en un ImageIcon y repinta la tabla. */
        void miniaturaLista(String login, TwitchService.Miniatura miniatura, long enMs);
    }

    private final TwitchService twitchService;
    private final Map<Long, String[]> twitchLive;
    private final Tareas tareas;
    private final Pantalla pantalla;

    private volatile boolean vigilandoTwitch;
    private long ultimoTwitchMs;

    public DirectosPresenter(TwitchService twitchService, Map<Long, String[]> twitchLive, Tareas tareas, Pantalla pantalla) {
        this.twitchService = twitchService;
        this.twitchLive = twitchLive;
        this.tareas = tareas;
        this.pantalla = pantalla;
    }

    /** Fuerza el próximo vigilarTwitch() a saltarse el throttle (F5, o un conjunto de jugadores nuevo). */
    public void reiniciarThrottle() { ultimoTwitchMs = 0; }

    /**
     * Cruza el listado global de Twitch con los jugadores visibles, con el mismo throttle y anti-solape que la 1.1
     * (170 s × el «mando a distancia», y no relanzar si ya hay un barrido en curso). {@code visibles} se evalúa
     * DESPUÉS de pasar las dos guardas, como hacía la 1.1 (leía playersModel ya dentro de vigilarTwitch, tras los
     * "return" de anti-solape y throttle): así una llamada que se descarta no paga ni el coste de mirar la lista.
     */
    public void vigilarTwitch(Supplier<List<Player>> visibles) { vigilarTwitch(visibles, List::of); }

    /**
     * Como vigilarTwitch(visibles), y además cruza a otrosVigilados (demás grupos, fuente de Live now) con el listado
     * global, sin llamadas extra (ver TwitchService.barrer(visibles, otros)): así «Solo con Twitch» y el TW de Live now
     * no dependen del grupo abierto en la watchlist (revisión 1.3, F10). Las dos listas se leen tras las guardas.
     */
    public void vigilarTwitch(Supplier<List<Player>> visibles, Supplier<List<Player>> otrosVigilados) {
        if (vigilandoTwitch) return;
        if (System.currentTimeMillis() - ultimoTwitchMs < (long) (170_000 * twitchService.multiplicador())) return;
        ultimoTwitchMs = System.currentTimeMillis();
        vigilandoTwitch = true;
        List<Player> lista = visibles.get();
        List<Player> otros = otrosVigilados.get();
        tareas.enFondoDemonio("twitch-vigilar", () -> {
            TwitchService.Resultado[] resultado = new TwitchService.Resultado[1];
            try { resultado[0] = twitchService.barrer(lista, otros); } catch (Exception ignored) { }
            tareas.enUi(() -> {
                vigilandoTwitch = false;
                TwitchService.Resultado r = resultado[0];
                if (r == null) return;
                if (!r.fallo()) {
                    twitchLive.clear();
                    twitchLive.putAll(r.enVivo());
                    pantalla.actualizarDirectosAoE2(r.directosAoE2());
                } else {
                    // sin respuesta (502…): se conserva la última lista buena
                    pantalla.estado(t("Twitch sin respuesta ahora mismo — mostrando la última lista buena.",
                            "Twitch not responding right now — showing the last good list."));
                }
                pantalla.repintarLista();
                if (pantalla.seleccionada()) pantalla.poblarDirectos();
            });
        });
    }

    /** Descarga en segundo plano ("miniaturas-twitch", igual que la 1.1) las miniaturas de esos canales. */
    public void cargarMiniaturas(List<String> logins) {
        if (logins.isEmpty()) return;
        tareas.enFondo("miniaturas-twitch", () -> {
            for (String login : logins) {
                TwitchService.Miniatura m = twitchService.miniatura(login, MINI_W, MINI_H);
                if (m != null) {
                    long ahora = System.currentTimeMillis();
                    tareas.enUi(() -> pantalla.miniaturaLista(login, m, ahora));
                }
            }
        });
    }
}
