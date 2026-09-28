package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.service.TopLadderService;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.util.ArrayList;
import java.util.List;

import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Los trabajos en segundo plano de la Watchlist: forma reciente, top ladder/país y los barridos de vivos
 * (refrescarWatchlist, vigilarVivos, vigilarTop). Sale de WatchlistView tal cual en la 1.3: cada SwingWorker con
 * su publish/process y su done(). Desde la fase 5 son el TRANSPORTE: el estado (el top, la forma, las marcas) y
 * las decisiones (qué pedir, cómo aplicarlo) viven en WatchlistPresenter y su pieza WatchlistTop; aquí quedan el
 * hilo, el publish/process, el get() y el done(). El top de un clan va por el hilo "top-clan" de WatchlistTop.
 * Hilos: doInBackground hace la red; process/done vuelven al EDT.
 */
final class WatchlistTrabajos {

    private final WatchlistView wv;

    WatchlistTrabajos(WatchlistView wv) { this.wv = wv; }

    /** Cambiar de vista apaga la columna Forma (vuelve solo si la pides). */
    public void apagarForma() {
        if (!wv.presenter.formaVisible) return;
        wv.presenter.formaVisible = false;
        if (wv.ocultarFormaBtn != null) wv.ocultarFormaBtn.setVisible(false);
        if (wv.formaBtn != null) actualizarTextoForma();
        wv.presenter.quitarOrdenPorForma();
        wv.refrescarCabeceraOrden();
    }

    /** Consulta la forma de los jugadores dados (con caché de 10 min), con progreso y Detener. El SwingWorker es el
     *  transporte (hilo, publish/process, done); qué consultar y cómo, lo decide WatchlistPresenter. */
    void cargarForma(List<Player> objetivo, int horas, Runnable alTerminar) {
        List<Player> pendientes = wv.presenter.pendientesForma(objetivo, horas);
        if (pendientes.isEmpty()) { if (alTerminar != null) alTerminar.run(); return; }
        if (wv.presenter.confirmarForma(pendientes.size())) {
            int ok = JOptionPane.showConfirmDialog(wv.ventana, WatchlistPresenter.textoConfirmarForma(pendientes.size(), horas),
                    t("Forma reciente", "Recent form"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
        }
        final long miSerial = wv.anfitrion.empezarOperacion();   // su propio freno (un Detener de otra no la para)
        new SwingWorker<Void, String>() {
            @Override protected Void doInBackground() {
                wv.anfitrion.marcarHiloOperacionActual(miSerial);   // Detener corta la espera del freno de ESTA operación, no la de todos
                try {
                    wv.anfitrion.cargarEloAyer();
                    wv.presenter.consultarForma(pendientes, horas, wv.presenter::eloParaResta,
                            this::publish, () -> wv.anfitrion.operacionDetenida(miSerial));
                } finally {
                    wv.anfitrion.soltarHiloOperacion();
                }
                return null;
            }
            @Override protected void process(List<String> ch) { wv.status.setText(ch.get(ch.size() - 1)); }
            @Override protected void done() {
                wv.anfitrion.terminarOperacion(miSerial);   // siempre: aunque otra la haya superado, deja de contar para «Detener»
                if (miSerial != wv.anfitrion.opSerial()) return;
                wv.status.setText(t("Forma consultada.", "Recent form fetched."));
                if (alTerminar != null) alTerminar.run();
            }
        }.execute();
    }

    /** Objetivo de «Ver forma»: los seleccionados si los hay; si no, todos los visibles. */
    List<Player> objetivoForma() {
        List<Player> sel = wv.playersList.getSelectedValuesList();
        return sel.isEmpty() ? jugadoresVisibles() : new ArrayList<>(sel);
    }

    /** El botón dice lo que va a consultar: «Ver forma (todos · 50)» / «(3 seleccionados)» / «Ocultar forma». */
    public void actualizarTextoForma() {
        if (wv.formaBtn == null) return;
        int n = wv.playersList.getSelectedIndices().length;
        String quien = n > 0 ? n + t(" seleccionado" + (n > 1 ? "s" : ""), " selected")
                             : t("todos \u00B7 ", "all \u00B7 ") + wv.playersModel.size();
        wv.formaBtn.setText(t("Ver forma", "Recent form") + " (" + quien + ")");
    }

    private List<Player> jugadoresVisibles() {
        List<Player> v = new ArrayList<>();
        for (int i = 0; i < wv.playersModel.size(); i++) v.add(wv.playersModel.get(i));
        return v;
    }

    // ----- ★ Top ladder / ★ Top país: el estado y las decisiones viven en WatchlistTop (pieza del presentador) -----

    /** Carga el top N del leaderboard (nick, ELO, última partida) sin tocar players.txt, con caché de 10 min, y
     *  dispara un barrido de vivos. El SwingWorker es el transporte: qué pedir y cómo aplicarlo, WatchlistTop. */
    public void cargarTopLadder(boolean forzar) {
        WatchlistTop top = wv.presenter.top;
        WatchlistTop.Carga carga = top.prepararCarga(forzar);
        if (carga == null) return;   // ya cargando, o fresco (ya pintado)
        new SwingWorker<TopLadderService.ResultadoTop, Void>() {
            @Override protected TopLadderService.ResultadoTop doInBackground() {
                return top.cargar(carga);   // siempre de ahora, sin la caché por URL (B1)
            }
            @Override protected void done() {
                top.cargaTerminada();
                try {
                    TopLadderService.ResultadoTop res = get();
                    top.aplicarCarga(res, carga);
                } catch (Exception ex) {
                    top.errorCarga(ex);
                }
            }
        }.execute();
    }

    /** Vivos del top: el río global de partidas en curso como motor (1-2
     *  llamadas), consulta a los «calientes» si el río no trae en-curso, y
     *  verificación individual presupuestada de los que se apagan. */
    public void vigilarTop() {
        WatchlistTop topW = wv.presenter.top;
        List<Player> top = topW.prepararVigilancia();
        if (top == null) return;   // ya vigilando, sin top o dentro de los 45 s
        new SwingWorker<TopLadderService.ResultadoVigilancia, Void>() {
            @Override protected TopLadderService.ResultadoVigilancia doInBackground() {
                return topW.vigilar(top);
            }
            @Override protected void done() {
                topW.vigilanciaTerminada();
                try {
                    TopLadderService.ResultadoVigilancia r = get();
                    topW.aplicarVigilancia(top, r);
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    /** Barrido de la Watchlist al abrir: para cada seguido, una consulta ligera
     *  que detecta partida en curso (finished vacío) y su ELO actual (rating de
     *  su último 1v1 terminado). Solo toca la lista, nunca la tabla: los
     *  resultados de las partidas siguen sin verse. */
    public void refrescarWatchlist() {
        List<Player> visibles = new ArrayList<>();
        for (int i = 0; i < wv.playersModel.size(); i++) visibles.add(wv.playersModel.get(i));
        List<Player> objetivo = wv.presenter.objetivoBarrido(visibles);   // en los tops, ninguno
        if (objetivo.isEmpty()) return;
        new SwingWorker<Void, BarridoVivos.Refresco>() {
            @Override protected Void doInBackground() {
                wv.anfitrion.cargarEloAyer();
                for (Player p : objetivo) {
                    try {
                        BarridoVivos.Refresco r = wv.barridoVivos.refrescar(p.id());
                        if (r.juegosNocturno() != null) {   // del snapshot nocturno: sin llamada (el socket dirá si está en partida)
                            wv.gamesWatch.put(p.id(), r.juegosNocturno());
                            publish(r);
                            continue;
                        }
                        publish(r);
                    } catch (Exception ex) {
                        log("watchlist: fallo con " + p.name() + ": " + causa(ex));
                    }
                    wv.anfitrion.dormir(wv.pausaMs / 2);
                }
                return null;
            }
            @Override protected void process(List<BarridoVivos.Refresco> chunks) {
                wv.presenter.procesarRefrescos(chunks);
            }
        }.execute();
    }

    /** Vigilancia periódica de TODA la watchlist: solo detecta quién está jugando ahora. Se salta el tick si
     *  hay otra tarea en marcha. */
    /** Vigilancia periódica de TODA la watchlist (todos los grupos): solo
     *  detecta quién está jugando ahora; el ELO no se toca (solo al abrir).
     *  Se salta el tick si hay otra tarea en marcha. */
    public void vigilarVivos() {
        if (wv.vigilando || wv.progreso.isVisible() || wv.todosJugadores.isEmpty()) return;
        wv.vigilando = true;
        List<Player> objetivo = new ArrayList<>(wv.todosJugadores);
        new SwingWorker<Void, BarridoVivos.Lote>() {
            @Override protected Void doInBackground() {
                final int LOTE = BarridoVivos.LOTE_VIVOS;   // 2 llamadas para un top 50, 4 para el top 100
                for (int d = 0; d < objetivo.size(); d += LOTE) {
                    List<Player> lote = objetivo.subList(d, Math.min(d + LOTE, objetivo.size()));
                    List<Long> idsLote = new ArrayList<>();
                    for (Player p : lote) idsLote.add(p.id());
                    try {
                        publish(wv.barridoVivos.lote(idsLote));
                    } catch (Exception ex) {
                        log("vigilante: fallo con el lote " + (d / LOTE + 1) + ": " + causa(ex));
                    }
                    wv.anfitrion.dormir(wv.pausaMs / 2);
                }
                return null;
            }
            @Override protected void process(List<BarridoVivos.Lote> chunks) {
                wv.presenter.procesarLotes(chunks, wv.all);
            }
            @Override protected void done() { wv.vigilando = false; }
        }.execute();
    }
}
