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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.tirador.aoe2radar.service.AzarService.ajustarRefAzar;
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
 * y los botones siguen en la fachada y se usan por {@code vista}.
 */
final class BusquedasPartidas {

    /** El opSerial de la última «Buscar partidas» lanzada: la × lo mira para saber si el progreso sigue siendo
     *  de la búsqueda (y apagarlo) o ya es de otra operación (y dejarlo). Solo en el EDT. */
    long serialBusqueda;

    private final PartidasView vista;

    BusquedasPartidas(PartidasView vista) { this.vista = vista; }

    /** «36 h», «3 días», «2 semanas»: la ventana en la unidad que se lee mejor. */
    static String textoVentana(int horas) {
        if (horas % (24 * 7) == 0 && horas >= 24 * 7) { int w = horas / (24 * 7); return w + (w == 1 ? t(" semana", " week") : t(" semanas", " weeks")); }
        if (horas % 24 == 0 && horas >= 48) return (horas / 24) + t(" días", " days");
        return horas + " h";
    }

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
        vista.anfitrion.trabajando(true);
        final long miSerial = vista.anfitrion.operacionActual();
        final int hours = Integer.parseInt(leerConfig("elo_horas", "48"));
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
        vista.anfitrion.estado(t("Buscando partidas al azar ", "Searching random games ") + lo + "–" + hi + "…");
        log("azar #" + miSerial + ": inicio " + lo + "-" + hi + " h=" + hours + " mapa=" + mapaSel + " civ=" + civSel
                + " x" + multAzar + " continuar=" + continuar + " stop=" + vista.anfitrion.detenido());
        new SwingWorker<List<Match>, String>() {
            @Override protected List<Match> doInBackground() throws Exception {
                return vista.azarService.buscarAleatorias(lo, hi, mapaSel, civSel, hours, multAzar, cutoff, miSerial, this::publish);
            }
            @Override protected void process(List<String> msgs) { vista.anfitrion.estado(msgs.get(msgs.size() - 1)); }
            @Override protected void done() {
                if (!PartidasPresenter.vigente(miSerial, vista.anfitrion.operacionActual())) {
                    log("azar #" + miSerial + ": terminó superada por la op #" + vista.anfitrion.operacionActual() + "; resultado ignorado");
                    return;
                }
                vista.fetchBtn.setEnabled(true);
                vista.azarBtn.setEnabled(true);
                vista.gteBtn.setEnabled(true);
                vista.anfitrion.trabajando(false);
                vista.anfitrion.continuarDisponible(true);
                try {
                    List<Match> res = get();
                    log("azar #" + miSerial + ": done, " + res.size() + " partidas, stop=" + vista.anfitrion.detenido());
                    vista.anfitrion.aprenderCatalogos(res);
                    if (vista.anfitrion.detenido()) {
                        vista.anfitrion.estado(t("Búsqueda detenida.", "Search stopped.")
                                + (res.isEmpty() ? "" : "  " + res.size()
                                   + t(" encontradas hasta el corte, aplicadas.", " found before the cut, applied.")));
                        if (res.isEmpty()) return;
                    } else if (res.isEmpty()) {
                        vista.anfitrion.estado(t("Nada en ", "Nothing in ") + lo + "–" + hi
                                + t(" en las últimas ", " in the last ") + hours
                                + t(" h. Detalle del muestreo en descargas.log.", " h. Sampling details in descargas.log."));
                        return;
                    }
                    List<Player> refs = new ArrayList<>();
                    Set<Long> refIds = new HashSet<>();
                    for (Match m : res) {
                        m.azar = true;
                        ajustarRefAzar(m, civSel);
                        if (refIds.add(m.refId)) {
                            String nom = vista.texto.refNombre(m);
                            refs.add(new Player(m.refId, nom, "", 0));
                        }
                    }
                    PartidasView.SUJETOS.clear();
                    PartidasView.SUJETOS.addAll(refIds);
                    vista.vistaDeSujetos = vista.enlaceWatchlist.vistaActualId();
                    vista.refrescarSujetos(refs, false);
                    vista.all.clear();
                    vista.all.addAll(res);
                    vista.enlaceWatchlist.limpiarSeleccion();
                    vista.refreshModeCombo();
                    vista.applyFilters();
                    String extra = "";
                    if (res.size() < 10 && vista.azarService.tramoAgotado())
                        extra = t(" No hay más con esos filtros: tramo entero revisado (amplía horas o rango).",
                                  " Nothing else with those filters: whole bracket checked (widen hours or range).");
                    else if (res.size() < 10)
                        extra = t(" Repite la búsqueda: continúa donde lo dejó.",
                                  " Run it again: it picks up where it left off.");
                    vista.anfitrion.estado(res.size() + t(" partidas 1v1 al azar, ELO ", " random 1v1s, ELO ") + lo + "–" + hi
                            + t(", últimas ", ", last ") + hours + " h." + extra);
                } catch (Exception ex) {
                    vista.anfitrion.estado(vista.anfitrion.detenido() ? t("Detenido.", "Stopped.") : "Error: " + causa(ex));
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
        vista.anfitrion.trabajando(true);
        final long miSerial = vista.anfitrion.operacionActual();
        int hours = Integer.parseInt(leerConfig("elo_horas", "48"));
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
        vista.anfitrion.estado(t("Preparando Guess the ELO…", "Preparing Guess the ELO…"));
        new SwingWorker<List<Match>, String>() {
            @Override protected List<Match> doInBackground() throws Exception {
                return vista.azarService.buscarGte(cutoff, this::publish);
            }
            @Override protected void process(List<String> msgs) { vista.anfitrion.estado(msgs.get(msgs.size() - 1)); }
            @Override protected void done() {
                if (!PartidasPresenter.vigente(miSerial, vista.anfitrion.operacionActual())) { log("gte #" + miSerial + ": terminó superada por la op #" + vista.anfitrion.operacionActual()); return; }
                vista.fetchBtn.setEnabled(true);
                vista.azarBtn.setEnabled(true);
                vista.gteBtn.setEnabled(true);
                vista.anfitrion.trabajando(false);
                try {
                    List<Match> res = get();
                    vista.anfitrion.aprenderCatalogos(res);
                    if (res.isEmpty()) {
                        vista.anfitrion.estado(t("Sin partidas para Guess the ELO en las últimas ", "No games for Guess the ELO in the last ") + hours
                                + t(" h. Detalle en descargas.log.", " h. Details in descargas.log."));
                        return;
                    }
                    vista.all.clear();
                    vista.all.addAll(res);
                    vista.enlaceWatchlist.limpiarSeleccion();
                    vista.refreshModeCombo();
                    vista.applyFilters();
                    vista.anfitrion.estado(res.size() + t(" partidas Guess the ELO (archivos: «Guess the ELO ", " Guess the ELO games (files: “Guess the ELO ")
                            + res.get(0).gte + t("»–«", "”–“") + res.get(res.size() - 1).gte
                            + t("»). Adivina y comprueba con «Revelar resultado…».", "”). Guess, then check with “Reveal result…”."));
                } catch (Exception ex) {
                    vista.anfitrion.estado(vista.anfitrion.detenido() ? t("Detenido.", "Stopped.") : "Error: " + causa(ex));
                    log("Guess the ELO: ERROR " + causa(ex));
                }
            }
        }.execute();
    }

    /** El botón «Buscar partidas» (y la guía, que lo pulsa): con una búsqueda en marcha dice «Detener» y la
     *  cancela; si no, busca. */
    public void alternar(JButton btn) {
        if (vista.fetchWorker != null) {
            vista.fetchWorker.cancel(true);
            return;
        }
        fetchMatches(btn);
    }

    /** Busca las partidas de quien toque (invitado, perfil abierto, selección o grupo). Si ya había una búsqueda
     *  en marcha, la cancela y lanza esta (revisión 1.3, watchlist F8: un doble clic en otro jugador, «Ver sus
     *  partidas» o «Buscar partidas de…» durante una búsqueda antes la cancelaban sin buscar al nuevo). */
    public void fetchMatches(JButton btn) {
        if (vista.enlaceWatchlist.objetivoForzado() == null && vista.enlaceWatchlist.invitado() == null && vista.anfitrion.perfilAbierto() && vista.anfitrion.perfilAbiertoPid() > 0 && vista.enlaceWatchlist.seleccionSize() == 0) {
            vista.enlaceWatchlist.fijarObjetivoForzado(new Player(vista.anfitrion.perfilAbiertoPid(), vista.anfitrion.perfilNombreAbierto(), vista.enlaceWatchlist.grupoDestino()));
            vista.anfitrion.mostrarDirectos(false);
        }
        if (vista.enlaceWatchlist.totalJugadores() == 0 && vista.enlaceWatchlist.invitado() == null && vista.enlaceWatchlist.objetivoForzado() == null) {
            JOptionPane.showMessageDialog(vista.ventana, t("Añade antes algún jugador a la lista.",
                    "Add a player to the list first."));
            return;
        }
        List<Player> sel = new ArrayList<>();
        if (vista.enlaceWatchlist.objetivoForzado() != null) {
            sel.add(vista.enlaceWatchlist.objetivoForzado());
            vista.enlaceWatchlist.limpiarObjetivoForzado();
        } else if (vista.enlaceWatchlist.invitado() != null) {
            sel.add(vista.enlaceWatchlist.invitado());
        } else if (vista.objetivoEtiqueta != null && vista.enlaceWatchlist.seleccionSize() == 0) {
            sel.add(vista.objetivoEtiqueta);
        } else if (vista.enlaceWatchlist.modoTop()) {
            List<Player> selTop = vista.enlaceWatchlist.seleccion();
            if (selTop.size() > 15) {
                selTop = selTop.subList(0, 15);
                vista.anfitrion.estado(t("En ★ el máximo son 15 perfiles por tanda: busco los 15 primeros seleccionados.",
                        "In ★ the cap is 15 profiles per run: searching the first 15 selected."));
            }
            if (!selTop.isEmpty()) sel.addAll(selTop);
            else if (vista.enlaceWatchlist.soloVivosMarcado())
                for (int i = 0; i < vista.enlaceWatchlist.totalJugadores(); i++) sel.add(vista.enlaceWatchlist.jugador(i));
            else {
                JOptionPane.showMessageDialog(vista.ventana,
                        t("En ★ (Top ladder o Top país), selecciona jugadores concretos (clic o Ctrl+clic en la lista)\no activa el chip «● Jugando» antes de buscar.",
                          "In ★ (Top ladder or Country top), select specific players (click or Ctrl+click the list)\nor turn on the “● Playing” chip before searching."),
                        t("Buscar en el top", "Search the top"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        } else {
            List<Player> selNorm = vista.enlaceWatchlist.seleccion();
            if (!selNorm.isEmpty()) sel.addAll(selNorm);
            else for (int i = 0; i < vista.enlaceWatchlist.totalJugadores(); i++) sel.add(vista.enlaceWatchlist.jugador(i));
        }
        final List<Player> tracked = (vista.enlaceWatchlist.invitado() == null && !vista.enlaceWatchlist.modoTop()) ? vista.enlaceWatchlist.conFamilias(sel) : sel;
        SwingWorker<?, ?> anterior = vista.fetchWorker;
        if (anterior != null) {   // la nueva sustituye a la que estaba en marcha (su done() ya no toca nada: ver abajo)
            log("buscar: nueva búsqueda con otra en marcha; se cancela la anterior");
            vista.fetchWorker = null;
            anterior.cancel(true);
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
        vista.anfitrion.trabajando(true);
        final long miSerial = vista.anfitrion.operacionActual();
        serialBusqueda = miSerial;
        int hours = vista.enlaceWatchlist.horasVentana();
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
        SwingWorker<Recorrido, String> fw = new SwingWorker<>() {
            @Override protected Recorrido doInBackground() {
                vista.anfitrion.anotarHiloOperacion();
                // Detener (el del botón, el de la barra de estado o la × de «Partidas de:») corta el recorrido
                // ENTERO, no solo la página en curso (revisión 1.3, general F4 / watchlist F6).
                return recorrer(tracked, cutoff, vista.perPage, vista.pausaMs, vista.anfitrion::paginaDePartidas,
                        vista.anfitrion::enCursoReal, () -> isCancelled() || vista.anfitrion.detenido(), this::publish);
            }
            @Override protected void process(List<String> msgs) {
                // Un «Consultando…» tardío de una búsqueda cancelada, cerrada con la × o sustituida no tapa nada.
                if (isCancelled() || vista.fetchWorker != this) return;
                vista.anfitrion.estado(msgs.get(msgs.size() - 1));
            }
            @Override protected void done() {
                if (vista.fetchWorker != this) {
                    // Otra búsqueda la sustituyó (fetchMatches con una en marcha; fetchWorker es de la nueva, o ya
                    // null si la nueva acabó antes), o la cerró la × (soltarPorLaCruz, que ya repuso botones y
                    // progreso): esta no toca nada.
                    log("buscar #" + miSerial + ": sustituida o cerrada; resultado ignorado");
                    return;
                }
                // Otra operación de otro tipo (una descarga, «Ver forma»…) pudo empezar mientras tanto y llevarse el
                // semáforo: la búsqueda NO se descarta por eso (revisión 1.3, watchlist F7); solo deja el progreso
                // y el «Detener» de la barra a esa operación, que es la suya ahora.
                final boolean semaforoPropio = PartidasPresenter.vigente(miSerial, vista.anfitrion.operacionActual());
                if (!semaforoPropio) log("buscar #" + miSerial + ": la op #" + vista.anfitrion.operacionActual() + " empezó durante la búsqueda; el resultado se aplica igual");
                vista.fetchWorker = null;
                vista.actualizarTextoBuscar();
                btn.setToolTipText(null);
                btn.setEnabled(true);
                vista.azarBtn.setEnabled(true);
                vista.gteBtn.setEnabled(true);
                if (semaforoPropio) vista.anfitrion.trabajando(false);
                if (isCancelled()) {
                    vista.anfitrion.estado(t("Búsqueda detenida.", "Search stopped."));   // el botón «Detener»
                    return;
                }
                try {
                    Recorrido r = get();
                    if (r.detenida()) {
                        // Lo leído hasta el corte está incompleto: no se presenta como si fuera la búsqueda entera
                        // (misma salida que el botón «Detener»: la tabla anterior se queda como estaba).
                        log("buscar #" + miSerial + ": detenida; " + r.lista().size() + " partidas leídas hasta el corte, descartadas");
                        vista.anfitrion.estado(t("Búsqueda detenida.", "Search stopped."));
                        return;
                    }
                    List<Match> res = r.lista();
                    final boolean topeAlcanzado = r.topeAlcanzado();
                    final int fallosFetch = r.fallos();
                    final Set<Long> exitosos = r.exitosos();
                    vista.anfitrion.aprenderCatalogos(res);
                    List<Long> idsTracked = new ArrayList<>();
                    for (Player pl : tracked) idsTracked.add(pl.id());
                    BarridoVivos.DecisionBuscar dec = vista.barridoVivos.decidirVivos(res, idsTracked, exitosos);
                    for (Player pl : tracked) {
                        Long v = dec.vivos().get(pl.id());
                        if (v != null) { EstadoVivo.SISTEMA.marcarJugando(pl.id(), v, dec.infos().get(pl.id())); }
                        else if (dec.fuera().contains(pl.id())) { EstadoVivo.SISTEMA.marcarFuera(pl.id()); }
                    }
                    vista.enlaceWatchlist.actualizarIndicadoresVivos();
                    vista.all.clear();
                    vista.all.addAll(res);
                    vista.refreshModeCombo();
                    vista.applyFilters();
                    vista.anfitrion.estado(vista.view.size() + t(" de ", " of ") + vista.all.size() + t(" partidas en las últimas ", " games in the last ") + textoVentana(hours) + "."
                            + (topeAlcanzado
                                ? "  \u26A0 " + t("Tope de la búsqueda alcanzado: puede faltar historial antiguo — acorta la ventana o filtra por modo.",
                                                  "Search cap reached: older history may be missing — shorten the window or filter by mode.")
                                : "")
                            + (fallosFetch > 0
                                ? "  \u26A0 " + fallosFetch + t(" jugador(es) SIN RESPUESTA del servicio (¿429/caído?): sus partidas faltan — reintenta en un minuto.",
                                                              " player(s) got NO RESPONSE from the service (429/down?): their games are missing — retry in a minute.")
                                : ""));
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
        boolean semaforoPropio = PartidasPresenter.vigente(serialBusqueda, vista.anfitrion.operacionActual());
        if (semaforoPropio) vista.anfitrion.pararOperacion();   // corta la espera del freno; si el semáforo ya es de otra operación, no se la para
        enCurso.cancel(true);
        vista.fetchBtn.setToolTipText(null);
        vista.fetchBtn.setEnabled(true);
        vista.azarBtn.setEnabled(true);
        vista.gteBtn.setEnabled(true);
        if (semaforoPropio) vista.anfitrion.trabajando(false);
        log("buscar #" + serialBusqueda + ": cerrada con la ×");
    }

    /** Una página de partidas de un jugador (en la app, Anfitrion.paginaDePartidas: la red). */
    interface Paginador { Iterable<Match> pagina(long pid, int pagina, int porPagina) throws Exception; }

    /** Lo que deja el recorrido de fetchMatches: las partidas (la más reciente primero), si se tocó el tope, cuántos
     *  jugadores fallaron, quiénes respondieron entero y si se detuvo a medias (entonces la lista está incompleta). */
    record Recorrido(List<Match> lista, boolean topeAlcanzado, int fallos, Set<Long> exitosos, boolean detenida) { }

    /** El doInBackground de fetchMatches, sin Swing (para poder probarlo): páginas por jugador hasta la ventana,
     *  tope 6 páginas / 600 partidas. {@code parar} se mira antes de cada jugador, antes de cada página y al fallar
     *  una: si dice que sí, el recorrido acaba entero y vuelve con {@code detenida}. */
    static Recorrido recorrer(List<Player> tracked, Instant cutoff, int perPage, long pausaMs, Paginador paginas,
                              java.util.function.Predicate<Match> enCursoReal, java.util.function.BooleanSupplier parar,
                              java.util.function.Consumer<String> progreso) {
        Map<Long, Match> unicos = new LinkedHashMap<>();
        Set<Long> exitosos = new HashSet<>();
        boolean topeAlcanzado = false, detenida = false;
        int fallos = 0;
        final int MAX_PAGINAS = 6, MAX_TOTAL = 600;
        for (Player pl : tracked) {
            if (parar.getAsBoolean()) { detenida = true; break; }
            boolean fallo = false;
            for (int pagina = 1; pagina <= MAX_PAGINAS; pagina++) {
                if (parar.getAsBoolean()) { detenida = true; break; }
                progreso.accept(t("Consultando ", "Checking ") + pl.name() + (pagina > 1 ? " (" + t("pág. ", "p. ") + pagina + ")" : "") + "…");
                boolean seguir = false;
                try {
                    Iterable<Match> leidas = paginas.pagina(pl.id(), pagina, perPage);
                    int n = 0; Instant masAntigua = null;
                    for (Match m : leidas) {
                        if (m == null) continue;
                        n++;
                        Instant ref = m.finished != null ? m.finished : m.started;
                        if (ref != null && (masAntigua == null || ref.isBefore(masAntigua))) masAntigua = ref;
                        if (m.finished == null && !enCursoReal.test(m)) continue;
                        if (m.finished != null && m.finished.isBefore(cutoff)) continue;
                        unicos.putIfAbsent(m.id, m);
                    }
                    seguir = n >= perPage && masAntigua != null && masAntigua.isAfter(cutoff);
                    if (seguir && pagina == MAX_PAGINAS) topeAlcanzado = true;
                    if (unicos.size() >= MAX_TOTAL) { topeAlcanzado = true; seguir = false; }
                    Thread.sleep(pausaMs);
                } catch (Exception ex) {
                    fallo = true;
                    if (parar.getAsBoolean()) { detenida = true; break; }   // el freno cortó la espera: se para todo
                    if (ex instanceof InterruptedException) break;
                    fallos++;
                    progreso.accept(t("Aviso: fallo con ", "Heads-up: failed with ") + pl.name() + " (" + causa(ex) + ")");
                }
                if (!seguir) break;
            }
            if (detenida) break;
            if (!fallo) exitosos.add(pl.id());
            if (unicos.size() >= MAX_TOTAL) break;
        }
        List<Match> lista = new ArrayList<>(unicos.values());
        lista.sort(Comparator.comparing((Match m) -> m.finished == null ? Instant.MAX : m.finished).reversed());
        return new Recorrido(lista, topeAlcanzado, fallos, exitosos, detenida);
    }
}
