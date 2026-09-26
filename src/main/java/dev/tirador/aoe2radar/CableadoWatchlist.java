// Cableado de la Watchlist: lo que pide a Partidas (EnlacePartidas), lo que pide al resto de la ventana
// (Anfitrion), la lista de jugadores con el tooltip de forma reciente y la propia construcción de
// ui.WatchlistView. Antes eran métodos privados e inicializadores de campo de SpoilerFreeRecs: se sacan aquí
// para separar QUÉ necesita la ventana de CÓMO se conecta. Métodos static que reciben la ventana
// (parámetro v): sin estado propio, pura composición (fase 3, tanda 4, B1).
package dev.tirador.aoe2radar;

import dev.tirador.aoe2radar.app.Servicios;
import dev.tirador.aoe2radar.cache.Anotaciones;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.ReglasPartida;
import dev.tirador.aoe2radar.sfrdata.Snapshots;
import dev.tirador.aoe2radar.ui.Tareas;
import dev.tirador.aoe2radar.ui.WatchlistView;
import dev.tirador.aoe2radar.util.Config;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.tirador.aoe2radar.api.Cancelacion.hiloOperacion;
import static dev.tirador.aoe2radar.api.Cancelacion.stopOperacion;
import static dev.tirador.aoe2radar.app.Servicios.BUSQUEDA;
import static dev.tirador.aoe2radar.app.Servicios.COMPANION;
import static dev.tirador.aoe2radar.app.Servicios.ELO_1V1;
import static dev.tirador.aoe2radar.app.Servicios.PAUSA_MS;
import static dev.tirador.aoe2radar.app.Servicios.PER_PAGE;
import static dev.tirador.aoe2radar.app.Servicios.SERVICIO_PERFIL;
import static dev.tirador.aoe2radar.app.Servicios.TOP_LADDER_SERVICE;
import static dev.tirador.aoe2radar.cache.Anotaciones.ALIASES;
import static dev.tirador.aoe2radar.util.I18n.t;

/** Cableado de la Watchlist (panel izquierdo): su construcción y las dos interfaces que necesita del resto de
 *  la ventana (EnlacePartidas, Anfitrion), más la lista de jugadores (tooltip de forma reciente) y el
 *  predicado de grupo que usa Campanas. Vive en el paquete raíz porque lee campos de paquete de la ventana
 *  sin volverlos public. Sin estado propio (fase 3, tanda 4, B1). */
final class CableadoWatchlist {
    private CableadoWatchlist() {}

    /** Ver el comentario de "campanas" en SpoilerFreeRecs: separado en un método para poder pasarlo como
     *  Predicate. La ventana deja un delegado privado de una línea porque this::esGrupoDeUsuarioWatchlist se
     *  pasa al construir Campanas. */
    static boolean esGrupoDeUsuarioWatchlist(SpoilerFreeRecs v, String nombre) {
        return v.watchlist != null && v.watchlist.esGrupoDeUsuario(nombre);
    }

    /** playersList: sobre la celda Forma, su tooltip; en el resto de la fila, el de siempre. */
    static JList<Player> playersList(SpoilerFreeRecs v) {
        return new JList<>(v.playersModel) {
            @Override public String getToolTipText(MouseEvent e) {   // sobre la celda Forma: su tooltip; en el resto, el de la fila
                if (v.watchlist.formaVisible() && v.watchlist.enZonaForma(e.getPoint())) {
                    int i = locationToIndex(e.getPoint());
                    if (i >= 0 && getCellBounds(i, i).contains(e.getPoint())) return v.watchlist.tipForma(v.playersModel.get(i).id());
                }
                return super.getToolTipText(e);
            }
        };
    }

    /** Lo que la Watchlist pide a Partidas (ui.PartidasView, campo {@code partidas}): ver
     *  ui.WatchlistView.EnlacePartidas. objetivoForzado/invitado/vistaDelInvitado siguen en la ventana. */
    private static WatchlistView.EnlacePartidas watchlistEnlacePartidas(SpoilerFreeRecs v) {
        return new WatchlistView.EnlacePartidas() {
            @Override public void fetchMatches() { v.partidas.fetchMatches(v.partidas.fetchBtn); }
            @Override public void mostrarDirectos(boolean mostrar) { v.mostrarDirectos(mostrar); }
            @Override public void refrescarSujetos(List<Player> tracked, boolean esInvitado) { v.partidas.refrescarSujetos(tracked, esInvitado); }
            @Override public List<Player> ultimosSujetos() { return v.partidas.ultimosSujetos; }
            @Override public void taparResultados() { v.partidas.taparResultados(); }
            @Override public void applyFilters() { v.partidas.applyFilters(); }
            @Override public void actualizarTextoBuscar() { v.partidas.actualizarTextoBuscar(); }
            @Override public void limpiarSujetos() { dev.tirador.aoe2radar.ui.PartidasView.SUJETOS.clear(); }
            @Override public void sincronizarSocket() { v.sincronizarSocket(); }
            @Override public String resumenVivo(Match m, long pid) { return ReglasPartida.resumenVivo(m, pid); }
            @Override public String refNombre(Match m) { return v.partidas.refNombre(m); }
            @Override public void repintarTabla() { v.partidas.table.repaint(); }
            @Override public void fijarObjetivo(Player p, String vistaId) { v.objetivoForzado = p;   // aunque ya esté en un grupo (entonces no es invitado, pero sí el objetivo)
                v.invitado = p; v.vistaDelInvitado = vistaId; }
            @Override public Player invitado() { return v.invitado; }
            @Override public void limpiarInvitado() { v.invitado = null; }
            @Override public String vistaDelInvitado() { return v.vistaDelInvitado; }
            @Override public boolean sujetosPanelVisible() { return v.sujetosPanel != null && v.sujetosPanel.isVisible(); }
            @Override public String vistaDeSujetos() { return v.partidas.vistaDeSujetos; }
        };
    }

    /** Lo que la Watchlist pide al resto de la ventana (cromo, red que no es de servicio): ver
     *  ui.WatchlistView.Anfitrion. */
    private static WatchlistView.Anfitrion watchlistAnfitrion(SpoilerFreeRecs v) {
        return new WatchlistView.Anfitrion() {
            @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
            @Override public String paisDe(long pid) { return dev.tirador.aoe2radar.cache.Paises.paisDe(pid); }
            @Override public void aprenderPais(long pid, String pais) { dev.tirador.aoe2radar.cache.Paises.aprenderPais(pid, pais); }
            @Override public void aprenderCanal(long pid, String canal) { dev.tirador.aoe2radar.cache.Canales.aprenderCanal(pid, canal); }
            @Override public void cargarEloAyer() { Snapshots.cargarEloAyer(); }
            @Override public boolean clanesVacios() { return dev.tirador.aoe2radar.sfrdata.Ladder.clanes.isEmpty(); }
            @Override public void asegurarLadderEnFondo() { dev.tirador.aoe2radar.sfrdata.Ladder.ladderAsegurar(false); }
            @Override public List<Map.Entry<String, Integer>> sugerirClanes(String texto) { return dev.tirador.aoe2radar.service.ConsultasLadder.sugerirClanes(texto); }
            @Override public void trabajando(boolean on) { v.trabajando(on); }
            @Override public long opSerial() { return v.barraEstado.opSerial(); }
            @Override public void marcarHiloOperacionActual() { hiloOperacion = Thread.currentThread(); }
            @Override public boolean detenerOperacion() { return stopOperacion; }
            @Override public void dormir(long ms) { Servicios.dormir(ms); }
            @Override public void abrirUrl(String url) { v.abrirUrl(url); }
            @Override public void espectar(Player p) { v.espectar(p); }
            @Override public java.nio.file.Path rutaCaptureAge() { return dev.tirador.aoe2radar.service.Juego.rutaCaptureAge(); }
            @Override public void lanzarCaptureAge(java.nio.file.Path rec) { v.lanzarCaptureAge(rec); }
            @Override public void mostrarToast(String texto, long matchId) { v.mostrarToast(texto, matchId); }
            @Override public void agregarAccionesToast(Runnable accionPerfil, Runnable accionCaraACara) {
                JPanel toastActual = v.barraEstado.toast();
                if (toastActual == null) return;
                JButton perf = new JButton(t("Su perfil", "Their profile")); perf.setFocusable(false); perf.setMargin(new Insets(0, 6, 0, 6)); perf.addActionListener(a -> accionPerfil.run());
                JButton cara = new JButton(t("Cara a cara", "Head-to-head")); cara.setFocusable(false); cara.setMargin(new Insets(0, 6, 0, 6)); cara.addActionListener(a -> accionCaraACara.run());
                JPanel acc = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); acc.setOpaque(false); acc.add(perf); acc.add(cara);
                toastActual.add(acc, BorderLayout.SOUTH); toastActual.revalidate();
            }
            @Override public void abrirPerfilYCaraACara(long pid, String miNombre, long rivalId, String rivalNombre) {
                v.abrirPerfil(pid, miNombre);
                javax.swing.Timer tt = new javax.swing.Timer(1200, ev -> { if (v.perfil.pidAbierto() == pid) v.perfil.abrirCaraACaraCon(rivalId, rivalNombre); });
                tt.setRepeats(false); tt.start();
            }
            @Override public Object[] tarjetaPerfilCache(long pid) { return v.perfilCardCache.vigente(pid); }
            @Override public void tarjetaPerfilGuardar(long pid, Object[] valor) { v.perfilCardCache.poner(pid, valor); }
            @Override public boolean perfilAbierto() { return v.perfil.abierto(); }
            @Override public Icon iconoVista(String tipo) { return SpoilerFreeRecs.iconoVista(tipo); }
            @Override public void seleccionCambiada() { if (v.ratings != null) v.ratings.sincronizarSeleccion(); if (v.perfil != null) v.perfil.sincronizarSeleccion(); }
            @Override public boolean enCursoReal(Match m) { return dev.tirador.aoe2radar.cache.Vivos.enCursoReal(m); }
            @Override public List<dev.tirador.aoe2radar.model.PerfilEncontrado> buscarPerfilesApi(String q) throws Exception { return COMPANION.buscarPerfiles(q); }
            @Override public Perfil perfilApi(long pid) throws Exception { return COMPANION.perfil(pid); }
            @Override public dev.tirador.aoe2radar.model.PaginaPartidas paginaApi(long pid, int pagina, int porPagina) throws Exception { return COMPANION.pagina(pid, pagina, porPagina); }
            @Override public void reiniciarThrottleDirectos() { v.directos.reiniciarThrottle(); }
            @Override public void vigilarTwitchDirectos() { v.directos.vigilarTwitch(); }
            @Override public void mostrarSuperposicion(String texto, List<Object[]> fichas, int ms) { v.miPartida.mostrarSuperposicion(texto, fichas, ms); }
            @Override public void actualizarSocketExtra(Set<Long> ids) {
                if (v.liveNow != null) { for (Object[] f : v.liveNow.topSnapshot()) ids.add((Long) f[0]); v.liveNow.socketExtra.retainAll(ids); v.liveNow.socketExtra.addAll(ids); }
            }
            @Override public String ahoraNombre(long pid) { return v.liveNow.ahoraNombre(pid); }
        };
    }

    /** La Watchlist (panel izquierdo): ver ui.WatchlistView. Mismo orden de argumentos que la 1.1;
     *  sujetosPanel se crea justo antes, en el constructor de la ventana. */
    static WatchlistView construir(SpoilerFreeRecs v) {
        return new WatchlistView(v, SERVICIO_PERFIL, BUSQUEDA, TOP_LADDER_SERVICE, v.formaService, v.campanas,
                v.barridoVivos, ELO_1V1, v.menus, v.dialogos, v,
                Tareas.SWING, watchlistEnlacePartidas(v), watchlistAnfitrion(v),
                v.todosJugadores, v.playersModel, v.playersList, v.eloWatch, v.gamesWatch, v.twitchLive, ALIASES,
                v.status, v.progreso, v.partidas.all, v.sujetosPanel, SpoilerFreeRecs.PLAYERS_FILE, Config.CONFIG_FILE.resolveSibling("top_cache.txt"),
                PAUSA_MS, PER_PAGE);
    }
}
