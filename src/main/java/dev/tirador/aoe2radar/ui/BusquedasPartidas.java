package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.service.EstadoVivo;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;
import static dev.tirador.aoe2radar.util.Texto.esCualquiera;

/**
 * Las búsquedas de Partidas: «Buscar partidas» (fetchMatches), «Al azar por ELO…» y «Guess the ELO!». Sale tal
 * cual de {@link PartidasView} (1.3): los tres SwingWorker no cambian (mismo doInBackground, publish/process y
 * done con PartidasPresenter.vigente); all, SUJETOS, filtroSujetos, fetchWorker, vistaDeSujetos, objetivoEtiqueta
 * y los botones siguen en la fachada y se usan por {@code vista}. Desde la fase 5, lo que se decide (a quién se
 * busca, el recorrido paginado, qué hacer con el resultado, los mensajes) está en {@link PartidasPresenter}; aquí
 * quedan los diálogos, los botones y los SwingWorker como transporte.
 */
final class BusquedasPartidas {

    /** El número de operación de la última «Buscar partidas» lanzada: la × (y una búsqueda nueva que la sustituye)
     *  paran y terminan ESA operación, sin tocar el freno de las demás. Solo en el EDT. */
    long serialBusqueda;

    private final PartidasView vista;

    BusquedasPartidas(PartidasView vista) { this.vista = vista; }

    public void buscarAleatorias() { buscarAleatorias(false); }

    public void buscarAleatorias(boolean continuar) {
        if (!continuar) {
            JSpinner minSp = new JSpinner(new SpinnerNumberModel(Integer.parseInt(leerConfig("elo_min", "2100")), 0, 4000, 50));
            JSpinner maxSp = new JSpinner(new SpinnerNumberModel(Integer.parseInt(leerConfig("elo_max", "2300")), 0, 4000, 50));
            JSpinner horasSp = new JSpinner(new SpinnerNumberModel(Integer.parseInt(leerConfig("elo_horas", "48")), 1, 336, 6));
            String cualquiera = t("(cualquiera)", "(any)");
            JComboBox<String> mapaCb = new JComboBox<>();
            mapaCb.addItem(cualquiera);
            for (String m : vista.anfitrion.mapasConocidos()) mapaCb.addItem(m);
            mapaCb.setSelectedIndex(0);
            mapaCb.setSelectedItem(leerConfig("elo_mapa", cualquiera));
            JComboBox<String> civCb = new JComboBox<>();
            civCb.addItem(cualquiera);
            for (String c : vista.anfitrion.civsConocidas()) civCb.addItem(c);
            civCb.setSelectedIndex(0);
            civCb.setSelectedItem(leerConfig("elo_civ", cualquiera));
            JComboBox<String> intCb = new JComboBox<>(new String[]{ t("Rápida", "Quick"), t("Amplia", "Broad"), t("Exhaustiva", "Exhaustive") });
            try { intCb.setSelectedIndex(Integer.parseInt(leerConfig("elo_intensidad", "0"))); } catch (Exception ignored) { }
            JPanel form = new JPanel(new GridLayout(6, 2, 8, 4));
            form.add(new JLabel(t("ELO mínimo:", "Min ELO:"))); form.add(minSp);
            form.add(new JLabel(t("ELO máximo:", "Max ELO:"))); form.add(maxSp);
            form.add(new JLabel(t("Últimas horas:", "Last hours:"))); form.add(horasSp);
            form.add(new JLabel(t("Mapa:", "Map:"))); form.add(mapaCb);
            form.add(new JLabel(t("Civilización (uno de los dos):", "Civilization (either player):"))); form.add(civCb);
            form.add(new JLabel(t("Intensidad:", "Intensity:"))); form.add(intCb);
            JLabel avisoCiv = new JLabel(t("Con civ o mapa, la primera tirada tarda ~1 min; repetirla continúa donde quedó y reutiliza lo ya leído.",
                    "With civ or map filters the first run takes ~1 min; running it again picks up where it left off."));
            avisoCiv.setFont(avisoCiv.getFont().deriveFont(Font.PLAIN, 11f));
            JPanel formWrap = new JPanel(new BorderLayout(0, 8));
            formWrap.add(form, BorderLayout.CENTER);
            formWrap.add(avisoCiv, BorderLayout.SOUTH);
            if (JOptionPane.showConfirmDialog(vista.ventana, formWrap, t("Partidas 1v1 al azar por ELO", "Random 1v1s by ELO"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            int a = (int) minSp.getValue(), b = (int) maxSp.getValue();
            guardarConfig("elo_min", String.valueOf(Math.min(a, b)));
            guardarConfig("elo_max", String.valueOf(Math.max(a, b)));
            guardarConfig("elo_horas", String.valueOf((int) horasSp.getValue()));
            guardarConfig("elo_mapa", String.valueOf(mapaCb.getSelectedItem()));
            guardarConfig("elo_civ", String.valueOf(civCb.getSelectedItem()));
            guardarConfig("elo_intensidad", String.valueOf(intCb.getSelectedIndex()));
        }
        final int lo = Integer.parseInt(leerConfig("elo_min", "2100"));
        final int hi = Integer.parseInt(leerConfig("elo_max", "2300"));
        String mSel = leerConfig("elo_mapa", "");
        String cSel = leerConfig("elo_civ", "");
        final String mapaSel = esCualquiera(mSel) ? null : mSel;
        final String civSel  = esCualquiera(cSel) ? null : cSel;
        final int multAzar = switch (Integer.parseInt(leerConfig("elo_intensidad", "0"))) {
            case 1 -> 3; case 2 -> 10; default -> 1; };

        vista.anfitrion.mostrarDirectos(false);
        vista.fetchBtn.setEnabled(false);
        vista.azarBtn.setEnabled(false);
        vista.gteBtn.setEnabled(false);
        PartidasView.SUJETOS.clear();
        vista.refrescarSujetos(List.of(), false);
        vista.taparResultados();
        final long miSerial = vista.anfitrion.empezarOperacion();   // su propio freno (un Detener de otra no la para)
        final int hours = Integer.parseInt(leerConfig("elo_horas", "48"));
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
        vista.anfitrion.estado(t("Buscando partidas al azar ", "Searching random games ") + lo + "–" + hi + "…");
        log("azar #" + miSerial + ": inicio " + lo + "-" + hi + " h=" + hours + " mapa=" + mapaSel + " civ=" + civSel
                + " x" + multAzar + " continuar=" + continuar + " stop=" + vista.anfitrion.detenido(miSerial));
        new SwingWorker<List<Match>, String>() {
            @Override protected List<Match> doInBackground() throws Exception {
                vista.anfitrion.anotarHiloOperacion(miSerial, false);   // Detener corta la espera del freno de ESTA operación, no la de todos
                try {
                    return vista.azarService.buscarAleatorias(lo, hi, mapaSel, civSel, hours, multAzar, cutoff, miSerial, this::publish);
                } finally {
                    vista.anfitrion.soltarHiloOperacion();
                }
            }
            @Override protected void process(List<String> msgs) { vista.anfitrion.estado(msgs.get(msgs.size() - 1)); }
            @Override protected void done() {
                final boolean parada = vista.anfitrion.detenido(miSerial);
                vista.anfitrion.terminarOperacion(miSerial);   // siempre: aunque otra la haya superado, deja de contar para «Detener»
                if (!PartidasPresenter.vigente(miSerial, vista.anfitrion.operacionActual())) {
                    log("azar #" + miSerial + ": terminó superada por la op #" + vista.anfitrion.operacionActual() + "; resultado ignorado");
                    return;
                }
                vista.fetchBtn.setEnabled(true);
                vista.azarBtn.setEnabled(true);
                vista.gteBtn.setEnabled(true);
                vista.anfitrion.continuarDisponible(true);
                try {
                    List<Match> res = get();
                    log("azar #" + miSerial + ": done, " + res.size() + " partidas, stop=" + parada);
                    vista.anfitrion.aprenderCatalogos(res);
                    if (parada) {
                        vista.anfitrion.estado(PartidasPresenter.mensajeAzarDetenido(res.size()));
                        if (res.isEmpty()) return;
                    } else if (res.isEmpty()) {
                        vista.anfitrion.estado(PartidasPresenter.mensajeAzarNada(lo, hi, hours));
                        return;
                    }
                    PartidasPresenter.SujetosAzar sujetos = PartidasPresenter.sujetosAzar(res, civSel, vista.texto::refNombre);
                    PartidasView.SUJETOS.clear();
                    PartidasView.SUJETOS.addAll(sujetos.refIds());
                    vista.vistaDeSujetos = vista.enlaceWatchlist.vistaActualId();
                    vista.refrescarSujetos(sujetos.refs(), false);
                    vista.all.clear();
                    vista.all.addAll(res);
                    vista.enlaceWatchlist.limpiarSeleccion();
                    vista.refreshModeCombo();
                    vista.applyFilters();
                    vista.anfitrion.estado(PartidasPresenter.mensajeAzar(res.size(), lo, hi, hours, vista.azarService.deMuestra(), vista.azarService::tramoAgotado));
                } catch (Exception ex) {
                    vista.anfitrion.estado(parada ? t("Detenido.", "Stopped.") : "Error: " + causa(ex));
                    log("al azar por ELO: ERROR " + causa(ex));
                }
            }
        }.execute();
    }

    public void buscarGte() {
        vista.anfitrion.mostrarDirectos(false);
        vista.fetchBtn.setEnabled(false);
        vista.azarBtn.setEnabled(false);
        vista.gteBtn.setEnabled(false);
        PartidasView.SUJETOS.clear();
        vista.refrescarSujetos(List.of(), false);
        vista.taparResultados();
        final long miSerial = vista.anfitrion.empezarOperacion();   // su propio freno (un Detener de otra no la para)
        int hours = Integer.parseInt(leerConfig("elo_horas", "48"));
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
        vista.anfitrion.estado(t("Preparando Guess the ELO…", "Preparing Guess the ELO…"));
        new SwingWorker<List<Match>, String>() {
            @Override protected List<Match> doInBackground() throws Exception {
                vista.anfitrion.anotarHiloOperacion(miSerial, false);   // Detener corta la espera del freno de ESTA operación, no la de todos
                try {
                    return vista.azarService.buscarGte(cutoff, this::publish);
                } finally {
                    vista.anfitrion.soltarHiloOperacion();
                }
            }
            @Override protected void process(List<String> msgs) { vista.anfitrion.estado(msgs.get(msgs.size() - 1)); }
            @Override protected void done() {
                final boolean parada = vista.anfitrion.detenido(miSerial);
                vista.anfitrion.terminarOperacion(miSerial);   // siempre: aunque otra la haya superado, deja de contar para «Detener»
                if (!PartidasPresenter.vigente(miSerial, vista.anfitrion.operacionActual())) { log("gte #" + miSerial + ": terminó superada por la op #" + vista.anfitrion.operacionActual()); return; }
                vista.fetchBtn.setEnabled(true);
                vista.azarBtn.setEnabled(true);
                vista.gteBtn.setEnabled(true);
                try {
                    List<Match> res = get();
                    vista.anfitrion.aprenderCatalogos(res);
                    if (res.isEmpty()) {
                        vista.anfitrion.estado(PartidasPresenter.mensajeGteNada(hours));
                        return;
                    }
                    vista.all.clear();
                    vista.all.addAll(res);
                    vista.enlaceWatchlist.limpiarSeleccion();
                    vista.refreshModeCombo();
                    vista.applyFilters();
                    vista.anfitrion.estado(PartidasPresenter.mensajeGte(res));
                } catch (Exception ex) {
                    vista.anfitrion.estado(parada ? t("Detenido.", "Stopped.") : "Error: " + causa(ex));
                    log("Guess the ELO: ERROR " + causa(ex));
                }
            }
        }.execute();
    }

    /** El botón «Buscar partidas» (y la guía, que lo pulsa): con una búsqueda en marcha dice «Detener» y la
     *  para; si no, busca. Parar = poner SU freno e interrumpir su hilo (no cancelar el SwingWorker): la página en
     *  vuelo o la pausa se cortan al momento (esa página se descarta) y su done() muestra lo leído hasta ahí como
     *  resultados parciales (decisión de Jorge, 1.3). */
    public void alternar(JButton btn) {
        if (vista.fetchWorker != null) {
            vista.anfitrion.pararOperacion(serialBusqueda);
            vista.anfitrion.estado(t("Deteniendo la búsqueda…", "Stopping the search…"));
            return;
        }
        fetchMatches(btn);
    }

    /** Busca las partidas de quien toque (invitado, perfil abierto, selección o grupo). Si ya había una búsqueda
     *  en marcha, la cancela y lanza esta (revisión 1.3, watchlist F8: un doble clic en otro jugador, «Ver sus
     *  partidas» o «Buscar partidas de…» durante una búsqueda antes la cancelaban sin buscar al nuevo). */
    public void fetchMatches(JButton btn) {
        if (PartidasPresenter.buscaAlDelPerfil(vista.enlaceWatchlist, vista.anfitrion)) {
            vista.enlaceWatchlist.fijarObjetivoForzado(new Player(vista.anfitrion.perfilAbiertoPid(), vista.anfitrion.perfilNombreAbierto(), vista.enlaceWatchlist.grupoDestino()));
            vista.anfitrion.mostrarDirectos(false);
        }
        if (PartidasPresenter.nadieABuscar(vista.enlaceWatchlist)) {
            JOptionPane.showMessageDialog(vista.ventana, t("Añade antes algún jugador a la lista.",
                    "Add a player to the list first."));
            return;
        }
        // A quién se busca lo decide PartidasPresenter.elegirBuscados; aquí quedan sus efectos, en el mismo orden.
        PartidasPresenter.Eleccion eleccion = PartidasPresenter.elegirBuscados(vista.enlaceWatchlist, vista.objetivoEtiqueta);
        if (eleccion.deForzado()) vista.enlaceWatchlist.limpiarObjetivoForzado();
        if (eleccion.recortadaA15())
            vista.anfitrion.estado(t("En ★ el máximo son 15 perfiles por tanda: busco los 15 primeros seleccionados.",
                    "In ★ the cap is 15 profiles per run: searching the first 15 selected."));
        if (eleccion.faltaSeleccionTop()) {
            JOptionPane.showMessageDialog(vista.ventana,
                    t("En ★ (Top ladder o Top país), selecciona jugadores concretos (clic o Ctrl+clic en la lista)\no activa el chip «● Jugando» antes de buscar.",
                      "In ★ (Top ladder or Country top), select specific players (click or Ctrl+click the list)\nor turn on the “● Playing” chip before searching."),
                    t("Buscar en el top", "Search the top"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<Player> sel = eleccion.buscados();
        final List<Player> tracked = (vista.enlaceWatchlist.invitado() == null && !vista.enlaceWatchlist.modoTop()) ? vista.enlaceWatchlist.conFamilias(sel) : sel;
        SwingWorker<?, ?> anterior = vista.fetchWorker;
        if (anterior != null) {   // la nueva sustituye a la que estaba en marcha (su done() ya no toca nada: ver abajo)
            log("buscar: nueva búsqueda con otra en marcha; se cancela la anterior");
            vista.fetchWorker = null;
            anterior.cancel(true);
            vista.anfitrion.terminarOperacion(serialBusqueda);   // su operación acaba aquí (su done() puede llegar tarde)
        }
        PartidasView.SUJETOS.clear();
        vista.filtroSujetos.clear();
        if (vista.rivalField != null && !vista.rivalField.getText().isEmpty()) vista.rivalField.setText("");
        vista.mostrarGuiaVacia(false);
        vista.taparResultados();
        for (Player px : tracked) PartidasView.SUJETOS.add(px.id());
        vista.vistaDeSujetos = vista.enlaceWatchlist.vistaActualId();
        vista.refrescarSujetos(tracked, vista.enlaceWatchlist.invitado() != null);

        vista.anfitrion.mostrarDirectos(false);
        vista.enlaceWatchlist.guardarVentanaHoras();
        btn.setText(t("Detener", "Stop"));
        btn.setToolTipText(t("Detiene la búsqueda en curso", "Stops the current search"));
        vista.azarBtn.setEnabled(false);
        vista.gteBtn.setEnabled(false);
        final long miSerial = vista.anfitrion.empezarOperacion();   // su propio freno (un Detener de otra no la para)
        serialBusqueda = miSerial;
        int hours = vista.enlaceWatchlist.horasVentana();
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
        SwingWorker<PartidasPresenter.Recorrido, String> fw = new SwingWorker<>() {
            @Override protected PartidasPresenter.Recorrido doInBackground() {
                vista.anfitrion.anotarHiloOperacion(miSerial, true);   // interrumpible: Detener corta al momento la página en vuelo o la pausa
                try {
                    // Detener (el del botón, el de la barra de estado si es la última operación viva, o la × de
                    // «Partidas de:») corta el recorrido ENTERO, no solo la página en curso (revisión 1.3, general
                    // F4 / watchlist F6).
                    return PartidasPresenter.recorrer(tracked, cutoff, vista.perPage, vista.pausaMs, vista.anfitrion::paginaDePartidas,
                            vista.anfitrion::enCursoReal, () -> isCancelled() || vista.anfitrion.detenido(miSerial), this::publish);
                } finally {
                    vista.anfitrion.soltarHiloOperacion();
                }
            }
            @Override protected void process(List<String> msgs) {
                // Un «Consultando…» tardío de una búsqueda cancelada, cerrada con la ×, sustituida o ya detenida no tapa
                // nada (tampoco el «Deteniendo…» del botón).
                if (isCancelled() || vista.fetchWorker != this || vista.anfitrion.detenido(miSerial)) return;
                vista.anfitrion.estado(msgs.get(msgs.size() - 1));
            }
            @Override protected void done() {
                vista.anfitrion.terminarOperacion(miSerial);   // siempre (repetirlo no hace nada): deja de contar para «Detener»
                if (vista.fetchWorker != this) {
                    // Otra búsqueda la sustituyó (fetchMatches con una en marcha; fetchWorker es de la nueva, o ya
                    // null si la nueva acabó antes), o la cerró la × (soltarPorLaCruz, que ya repuso botones y
                    // progreso): esta no toca nada.
                    log("buscar #" + miSerial + ": sustituida o cerrada; resultado ignorado");
                    return;
                }
                // Otra operación de otro tipo (una descarga, «Ver forma»…) pudo empezar mientras tanto: la búsqueda
                // NO se descarta por eso (revisión 1.3, watchlist F7). El progreso y el «Detener» de la barra siguen
                // mientras esa siga viva (terminarOperacion solo quita esta).
                if (!PartidasPresenter.vigente(miSerial, vista.anfitrion.operacionActual()))
                    log("buscar #" + miSerial + ": la op #" + vista.anfitrion.operacionActual() + " empezó durante la búsqueda; el resultado se aplica igual");
                vista.fetchWorker = null;
                vista.actualizarTextoBuscar();
                btn.setToolTipText(null);
                btn.setEnabled(true);
                vista.azarBtn.setEnabled(true);
                vista.gteBtn.setEnabled(true);
                // (Ya no hay rama «isCancelled()» aquí: solo cancelan la × y una búsqueda nueva, y las dos ponen
                // fetchWorker = null antes, así que salen por arriba; el botón «Detener» pone el freno, no cancela.)
                try {
                    PartidasPresenter.Recorrido r = get();
                    // Detenida (el botón, la barra o el freno): lo leído hasta el corte se muestra igual, con un aviso
                    // de que es parcial (decisión de Jorge, 1.3; antes se descartaba). Los no consultados no
                    // están en exitosos, así que no se marcan «fuera».
                    if (r.detenida()) log("buscar #" + miSerial + ": detenida; " + r.lista().size() + " partidas de "
                            + r.recorridos() + "/" + tracked.size() + " jugadores, se muestran como parciales");
                    if (PartidasPresenter.conservaTablaAnterior(r)) {   // nada leído: la tabla anterior se queda
                        vista.anfitrion.estado(PartidasPresenter.mensajeBusquedaDetenida(r.recorridos(), tracked.size(), r.fallos()));
                        return;
                    }
                    List<Match> res = r.lista();
                    final boolean topeAlcanzado = r.topeAlcanzado();
                    final int fallosFetch = r.fallos();
                    final Set<Long> exitosos = r.exitosos();
                    vista.anfitrion.aprenderCatalogos(res);
                    BarridoVivos.DecisionBuscar dec = vista.barridoVivos.decidirVivos(res, PartidasPresenter.ids(tracked), exitosos);
                    PartidasPresenter.aplicarVivos(dec, tracked, EstadoVivo.SISTEMA);
                    vista.enlaceWatchlist.actualizarIndicadoresVivos();
                    // Una descarga pudo seguir mientras se buscaba (F7): la misma partida vuelve como OTRO Match, y
                    // su estado («descargando…», «✓ guardada»…) se conserva por id de partida. enDisco/enJuego los
                    // vuelve a mirar applyFilters (y otra vez la descarga al acabar).
                    PartidasPresenter.conservarEstados(vista.all, res);
                    vista.all.clear();
                    vista.all.addAll(res);
                    vista.refreshModeCombo();
                    vista.applyFilters();
                    vista.anfitrion.estado(r.detenida()
                            ? PartidasPresenter.mensajeBusquedaDetenida(r.recorridos(), tracked.size(), r.fallos())
                            : PartidasPresenter.mensajeBusqueda(vista.view.size(), vista.all.size(), hours, topeAlcanzado, fallosFetch));
                } catch (Exception ex) {
                    vista.anfitrion.estado("Error: " + causa(ex));
                }
            }
        };
        vista.fetchWorker = fw;
        fw.execute();
    }

    /** La × de «Partidas de:» con una búsqueda en marcha (PartidasView.cerrarBusqueda). fetchWorker se pone a null
     *  ANTES de cancelar, como en fetchMatches: así su done() —que corre dentro de cancel() si aún no había
     *  terminado, o más tarde si doInBackground ya acabó y el done() estaba en cola— ve que ya no es la búsqueda
     *  y no toca nada (en ese segundo caso, cancel no puede nada y antes volvía a llenar la tabla). Los botones y
     *  el progreso los repone esta misma llamada. EDT. */
    void soltarPorLaCruz() {
        SwingWorker<?, ?> enCurso = vista.fetchWorker;
        if (enCurso == null) return;
        vista.fetchWorker = null;
        vista.anfitrion.pararOperacion(serialBusqueda);   // SU freno (corta su espera en el freno de la red), sea o no la última operación
        enCurso.cancel(true);
        vista.fetchBtn.setToolTipText(null);
        vista.fetchBtn.setEnabled(true);
        vista.azarBtn.setEnabled(true);
        vista.gteBtn.setEnabled(true);
        vista.anfitrion.terminarOperacion(serialBusqueda);   // si queda otra viva, el progreso y «Detener» pasan a ella
        log("buscar #" + serialBusqueda + ": cerrada con la ×");
    }
}
