package dev.tirador.aoe2radar;

import dev.tirador.aoe2radar.app.Servicios;
import dev.tirador.aoe2radar.cache.Anotaciones;
import dev.tirador.aoe2radar.cache.RecsDisco;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.ui.PartidasView;
import dev.tirador.aoe2radar.ui.TemaApp;

import javax.swing.JPanel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.tirador.aoe2radar.app.Servicios.COMPANION;
import static dev.tirador.aoe2radar.app.Servicios.PAUSA_MS;
import static dev.tirador.aoe2radar.cache.Catalogos.CIVS_CAT;
import static dev.tirador.aoe2radar.cache.Catalogos.MAPAS_CAT;
import static dev.tirador.aoe2radar.cache.RecsDisco.RECS_DIR;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.I18n.t;

/** Cableado de la pestaña «Partidas»: ver ui.PartidasView/ui.PartidasPresenter (fase 3, tanda 3, oleada B). Se
 *  construye en el mismo punto de siempre (field initializer de la ventana, antes de CableadoCromo.configurarVentana
 *  y de crear la Watchlist, igual que dialogos/menus/azarService/recService/barridoVivos) para que sus botones
 *  existan cuando CableadoCromo.construirBarraSuperior los necesite. Las dos interfaces que necesita del resto de
 *  la ventana (EnlaceWatchlist: la Watchlist y sus jugadores; Anfitrion: todo lo demás) son cableado puro, sin
 *  estado propio: por eso salen de la ventana a esta clase (pasada final, fase 3, tanda 4).
 *  <p>La Watchlist llega a este cableado envuelta en EnlaceWatchlist, y sus métodos solo se EVALÚAN cuando algo
 *  los llama (nunca al construir el objeto): por eso partidas puede construirse antes que watchlist (que no es
 *  un field initializer, se asigna en el cuerpo del constructor de la ventana) sin NullPointerException. */
final class CableadoPartidas {
    private CableadoPartidas() { }

    static PartidasView construir(SpoilerFreeRecs v) {
        return new PartidasView(v, v.menus, v.dialogos, v,
                v.azarService, v.recService, v.barridoVivos, PAUSA_MS,
                new PartidasView.EnlaceWatchlist() {
                    @Override public List<Player> seleccion() { return v.playersList.getSelectedValuesList(); }
                    @Override public int seleccionSize() { return v.playersList.getSelectedIndices().length; }
                    @Override public boolean soloVivosMarcado() { return v.watchlist.soloVivosBtn != null && v.watchlist.soloVivosBtn.isSelected(); }
                    @Override public boolean modoTop() { return v.watchlist.modoTop(); }
                    @Override public String grupoDestino() { return v.watchlist.grupoDestino(); }
                    @Override public List<Player> conFamilias(List<Player> base) { return v.watchlist.conFamilias(base); }
                    @Override public void limpiarSeleccion() { v.playersList.clearSelection(); }
                    @Override public int totalJugadores() { return v.playersModel.size(); }
                    @Override public Player jugador(int indice) { return v.playersModel.get(indice); }
                    @Override public Integer eloDe(long pid) { return v.eloWatch.get(pid); }
                    @Override public String grupoDeJugador(long pid) { return v.watchlist.grupoDeJugador(pid); }
                    @Override public List<Player> todosJugadores() { return v.todosJugadores; }
                    @Override public void actualizarIndicadoresVivos() { v.watchlist.actualizarIndicadoresVivos(); }
                    @Override public void aplicarFiltroGrupo() { v.watchlist.aplicarFiltroGrupo(); }
                    @Override public String tipCuentaVinculada(Match m) { return v.watchlist.tipCuentaVinculada(m); }
                    @Override public Player objetivoForzado() { return v.objetivoForzado; }
                    @Override public void fijarObjetivoForzado(Player p) { v.objetivoForzado = p; }
                    @Override public void limpiarObjetivoForzado() { v.objetivoForzado = null; }
                    @Override public Player invitado() { return v.invitado; }
                    @Override public void limpiarInvitado() { v.invitado = null; }
                    @Override public String vistaActualId() { return v.watchlist.vistaActualId(); }
                    @Override public int horasVentana() { return v.horasVentana(); }
                    @Override public void guardarVentanaHoras() {
                        guardarConfig("ventana_n", String.valueOf((int) v.hoursSpinner.getValue()));
                        guardarConfig("horas", String.valueOf(v.horasVentana()));
                    }
                    @Override public void actualizarTextoForma() { v.watchlist.actualizarTextoForma(); }
                    @Override public JPanel sujetosPanel() { return v.sujetosPanel; }
                },
                new PartidasView.Anfitrion() {
                    @Override public void estado(String texto) { v.status.setText(texto); }
                    @Override public void mostrarDirectos(boolean mostrar) { v.mostrarDirectos(mostrar); }
                    @Override public void refrescarDirectos() {
                        v.directos.refrescarForzado();
                        if (v.navegador.directosBtn != null && v.navegador.directosBtn.isSelected()) v.status.setText(t("Refrescando directos…", "Refreshing streams…"));
                    }
                    @Override public void enfocarBuscador() { v.watchlist.enfocarBuscador(); }   // misma guarda de null, ahora dentro de WatchlistView
                    @Override public boolean confirmarEspectar(String nombre) { return dev.tirador.aoe2radar.ui.ConfirmacionEspectar.confirmar(v, nombre); }
                    @Override public void espectarVerificando(long profileId, long matchId) { AccionesVentana.espectarVerificando(v, profileId, matchId); }
                    @Override public void espectarPartida(long matchId) { AccionesVentana.espectarPartida(v, matchId); }
                    @Override public void lanzarCaptureAge(Path rec) { AccionesVentana.lanzarCaptureAge(v, rec); }
                    @Override public void abrirUrl(String url) { AccionesVentana.abrirUrl(v, url); }
                    @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
                    @Override public String paisDe(long pid) { return dev.tirador.aoe2radar.cache.Paises.paisDe(pid); }
                    @Override public boolean enCursoReal(Match m) { return dev.tirador.aoe2radar.cache.Vivos.enCursoReal(m); }
                    @Override public Path destino(Match m) { return RecsDisco.destino(m); }
                    @Override public Path recsDir() { return RECS_DIR; }
                    @Override public long empezarOperacion() { return v.barraEstado.empezarOperacion(); }
                    @Override public void terminarOperacion(long op) { v.barraEstado.terminarOperacion(op); }
                    @Override public long operacionActual() { return v.barraEstado.opSerial(); }
                    @Override public boolean detenido(long op) { return v.barraEstado.operaciones().detenido(op); }
                    @Override public void pararOperacion(long op) { v.barraEstado.detener(op); }
                    @Override public void anotarHiloOperacion(long op, boolean interrumpible) { v.barraEstado.operaciones().anotarHilo(op, interrumpible); }
                    @Override public void soltarHiloOperacion() { v.barraEstado.operaciones().soltarHilo(); }
                    @Override public void aprenderCatalogos(List<Match> res) { Servicios.aprenderCatalogos(res); }
                    @Override public List<String> mapasConocidos() { return new ArrayList<>(MAPAS_CAT); }
                    @Override public List<String> civsConocidas() { return new ArrayList<>(CIVS_CAT); }
                    @Override public void dormir(long ms) { Servicios.dormir(ms); }
                    @Override public long perfilAbiertoPid() { return v.perfil.pidAbierto(); }
                    @Override public boolean perfilAbierto() { return v.perfil.abierto(); }
                    @Override public String perfilNombreAbierto() { return v.perfil.nombreAbierto(); }
                    @Override public void mostrarHistorialSiSigueAbierto(long pid, String nombre) {
                        javax.swing.Timer tt = new javax.swing.Timer(900, ev -> { if (v.perfil.pidAbierto() == pid) v.perfil.mostrarHistorialPerfil(pid, nombre); });
                        tt.setRepeats(false); tt.start();
                    }
                    @Override public Iterable<Match> paginaDePartidas(List<Long> pids, int pagina, int porPagina) throws IOException, InterruptedException {
                        StringBuilder csv = new StringBuilder();
                        for (Long pid : pids) { if (csv.length() > 0) csv.append(','); csv.append(pid); }
                        return COMPANION.partidas(csv.toString(), pagina, porPagina);
                    }
                    @Override public boolean autoCopiarAlDescargar() { return v.autoSgItem.isSelected(); }
                    @Override public void continuarDisponible(boolean visible) { if (v.continuarBtn != null) v.continuarBtn.setVisible(visible); }
                    @Override public void ajustarGrisesNota(boolean oscuro) { TemaApp.ajustarGrises(v, oscuro); }
                    @Override public void actualizarControlesTabla() { v.navegador.actualizarControlesTabla(); }
                });
    }
}
