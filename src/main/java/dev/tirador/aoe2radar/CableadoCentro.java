package dev.tirador.aoe2radar;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.RatingsServiceSfr;
import dev.tirador.aoe2radar.service.TechTreeServiceDatos;
import dev.tirador.aoe2radar.ui.CivStatsView;
import dev.tirador.aoe2radar.ui.DirectosView;
import dev.tirador.aoe2radar.ui.LiveNowView;
import dev.tirador.aoe2radar.ui.PerfilView;
import dev.tirador.aoe2radar.ui.RatingsView;
import dev.tirador.aoe2radar.ui.Tareas;
import dev.tirador.aoe2radar.ui.TechTreeView;
import dev.tirador.aoe2radar.ui.WatchlistView;
import dev.tirador.aoe2radar.cache.Anotaciones;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static dev.tirador.aoe2radar.app.Servicios.*;
import static dev.tirador.aoe2radar.cache.Anotaciones.ALIASES;
import static dev.tirador.aoe2radar.cache.Canales.CANAL_DE;
import static dev.tirador.aoe2radar.cache.HistorialDisco.ACTIVIDAD_CACHE;
import static dev.tirador.aoe2radar.cache.HistorialDisco.ACT_DIAS;
import static dev.tirador.aoe2radar.cache.HistorialDisco.PERFILES_DIR;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.ELO_AYER;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.NOMBRES_AYER;

/** El centro de la ventana: las cartas (CardLayout) de cada vista -- tabla de
 *  partidas/guia, Directos, Live now, tech tree, Ratings, Civ Stats y Perfil --
 *  encima de «top» y con «bottom» debajo. Aquí se crean las vistas y sus
 *  anfitriones (las interfaces que les dan acceso a la ventana).
 *  Movido de SpoilerFreeRecs.construirCentro (fase 3, tanda 4, oleada B, zona B2):
 *  es cableado puro (sin estado propio), por eso vive en una clase final con un
 *  método static que recibe la ventana "v" para leer y escribir sus campos de vista. */
final class CableadoCentro {
    private CableadoCentro() { }

    static JPanel construirCentro(SpoilerFreeRecs v, JPanel top, JPanel bottom) {
        JPanel center = new JPanel(new BorderLayout());
        center.add(top, BorderLayout.NORTH);
        v.centroCards = new JPanel(new CardLayout());
        v.partidas.construirCards();   // recsCards «tabla»/«guia», guiaBtn: ver ui.PartidasView
        v.centroCards.add(v.partidas.panel(), "recs");
        v.directos = new DirectosView(TWITCH_SERVICE, v.twitchLive, Tareas.SWING, new DirectosView.Anfitrion() {
            @Override public List<Player> visibles() {
                List<Player> out = new ArrayList<>();
                for (int i = 0; i < v.playersModel.size(); i++) out.add(v.playersModel.get(i));
                return out;
            }
            @Override public List<Player> otrosVigilados() {   // copia en el EDT: el barrido va en otro hilo (F10)
                List<Player> out = new ArrayList<>(v.todosJugadores);
                if (v.liveNow != null) for (Object[] f : v.liveNow.topSnapshot()) out.add(new Player((Long) f[0], String.valueOf(f[1]), ""));
                return out;
            }
            @Override public void repintarLista() { v.playersList.repaint(); }
            @Override public void estado(String texto) { v.status.setText(texto); }
            @Override public void abrirUrl(String url) { AccionesVentana.abrirUrl(v, url); }
            @Override public boolean seleccionada() { return v.navegador.directosBtn != null && v.navegador.directosBtn.isSelected(); }
        });
        v.centroCards.add(v.directos.panel(), "directos");
        // techTree se crea AQUÍ, antes que Live now (fila 119 de DEUDA). La lambda de más abajo
        // ("civ -> v.techTree.claveCivDeNombre(civ)") ya funcionaba con el orden viejo, porque lee el campo
        // v.techTree al INVOCARSE, no al crearse; pero dependía de que nadie la llamara antes de tiempo. Con la
        // creación movida delante, deja de depender de ese orden de llamada. No toca v.liveNow ni nada creado
        // después, así que el cambio es seguro. El panel se añade al CardLayout más abajo, en su sitio de
        // siempre, para no mover el orden de las cartas.
        v.techTree = new TechTreeView(v, TechTreeServiceDatos.SISTEMA, v.stats, v.filtroStats, v.listas, v, Tareas.SWING,
                new TechTreeView.Anfitrion() {
                    @Override public void precalentarPerfiles() { v.perfil.precalentar(); }
                    @Override public void cerrar() { v.navegador.cerrarTechTree(); }
                },
                new TechTreeView.EnlaceCivStats() {
                    // Civ Stats se crea después (civStats es null mientras se construye el tech tree, como civStatsPanel en la 1.1)
                    @Override public boolean construida() { return v.civStats != null; }
                    @Override public void filtrosCambiados(boolean repintarTechTree) { if (v.civStats != null) v.civStats.filtrosCambiados(repintarTechTree); }
                    @Override public void sincronizarVentana(String ventana) { if (v.civStats != null) v.civStats.ponerVentanaSinDisparar(ventana); }
                });
        v.liveNow = new LiveNowView(v.campanas, LIVE, List.of(v.watchlist.PAISES), v.todosJugadores, v.eloWatch, v.twitchLive, civ -> v.techTree.claveCivDeNombre(civ), Tareas.SWING, v, v.menus, v, new LiveNowView.Anfitrion() {
            @Override public List<String> clanesGuardados() { return v.watchlist.clanesGuardados(); }
            @Override public String paisSel() { return v.watchlist.paisSel(); }
            @Override public String clanBuscado() { return v.watchlist.clanBuscado(); }
            @Override public boolean campanaContiene(long pid) { return v.watchlist.campanaContiene(pid); }
            @Override public void sincronizarSocket() { v.enlaceVivo.sincronizarSocket(); }
            @Override public boolean socketConectado() { return v.enlaceVivo.conectado(); }
            @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
            @Override public String paisDe(long pid) { return dev.tirador.aoe2radar.cache.Paises.paisDe(pid); }
            @Override public void ocultarHoverCard(boolean forzar) { v.watchlist.ocultarHoverCard(forzar); }
            @Override public boolean confirmarEspectar(String quien) { return dev.tirador.aoe2radar.ui.ConfirmacionEspectar.confirmar(v, quien); }
            @Override public void espectarPartida(long matchId) { AccionesVentana.espectarPartida(v, matchId); }
            @Override public void abrirUrl(String url) { AccionesVentana.abrirUrl(v, url); }
            @Override public void descargar(List<Match> lista, boolean enviarAlJuego, Runnable alTerminar) { v.partidas.descargarSinCambiarVista(lista, enviarAlJuego, alTerminar); }
            @Override public void estadoGlobal(String texto) { v.status.setText(texto); }
        });
        v.centroCards.add(v.liveNow.panel(), "ahora");
        v.centroCards.add(v.techTree.panel(), "techtree");
        ToolTipManager.sharedInstance().setInitialDelay(350);
        ToolTipManager.sharedInstance().setDismissDelay(90_000);   // el tooltip aguanta mientras el ratón esté quieto
        v.ratings = new RatingsView(RatingsServiceSfr.SISTEMA, BUSQUEDA, SERVICIO_PERFIL, Tareas.SWING, new RatingsView.Anfitrion() {
            @Override public List<Player> seleccion() { return v.playersList.getSelectedValuesList(); }
            @Override public boolean seleccionado(long pid) { for (Player p : v.playersList.getSelectedValuesList()) if (p.id() == pid) return true; return false; }
            @Override public void deseleccionar(long pid) { for (int i = 0; i < v.playersModel.getSize(); i++) if (v.playersModel.getElementAt(i).id() == pid) v.playersList.removeSelectionInterval(i, i); }
            @Override public void limpiarSeleccion() { v.playersList.clearSelection(); }
            @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
        });
        v.centroCards.add(v.ratings.panel(), "ladder");
        v.civStats = new CivStatsView(v.stats, v.filtroStats, v.listas, v, Tareas.SWING, v, () -> { if (v.techTree != null) v.techTree.actualizarWr(); });
        v.centroCards.add(v.civStats.panel(), "civstats");
        v.perfil = new PerfilView(SERVICIO_PERFIL, RatingsServiceSfr.SISTEMA, BUSQUEDA, v.stats, VIVO, v.menus, v, v.techTree, v.listas, Tareas.SWING,
                new PerfilView.Anfitrion() {
                    @Override public List<Player> seleccionWatchlist() { return v.playersList.getSelectedValuesList(); }
                    @Override public boolean estaEnWatchlist(long pid) { return v.watchlist.containsPlayerId(pid); }
                    @Override public String paisDe(long pid) { return dev.tirador.aoe2radar.cache.Paises.paisDe(pid); }
                    @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
                    @Override public void ficharDesdeTop(long pid, String nombre, String grupo) { v.watchlist.ficharDesdeTop(new Player(pid, nombre, grupo), grupo); }
                    @Override public List<String> gruposDeJugadores() { Set<String> gs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); for (Player x : v.todosJugadores) gs.add(x.grupo()); return new ArrayList<>(gs); }
                    @Override public Set<Long> idsDeGrupo(String grupo) { Set<Long> ids = new HashSet<>(); for (Player x : v.todosJugadores) if (x.grupo().equalsIgnoreCase(grupo)) ids.add(x.id()); return ids; }
                    @Override public List<String> gruposGuardados() { return new ArrayList<>(v.watchlist.gruposConfig()); }
                    @Override public String grupoGeneral() { return WatchlistView.GRUPO_GENERAL; }
                    @Override public List<String> clanesGuardados() { return v.watchlist.clanesGuardados(); }
                    @Override public boolean hayTop250() { if (v.liveNow == null) return false; boolean[] hay = { false }; v.liveNow.conTop(l -> hay[0] = !l.isEmpty()); return hay[0]; }
                    @Override public Set<Long> idsTop250() { Set<Long> s = new HashSet<>(); if (v.liveNow != null) v.liveNow.conTop(l -> { for (Object[] x : l) s.add((Long) x[0]); }); return s; }
                    @Override public boolean ultimoClicFueCtrl() { return v.ultimoClicCtrl; }
                    @Override public void pedirAlias(long pid, String nombreOriginal) { v.dialogos.pedirAlias(pid, nombreOriginal); }
                    @Override public void pedirNota(long pid, String nombre) { v.dialogos.pedirNota(pid, nombre); }
                    @Override public void borrarNota(long pid, String nombre) { v.dialogos.borrarNota(pid, nombre); }
                    @Override public void mostrarVinculadas(long pid, String nombre) { v.dialogos.mostrarVinculadas(pid, nombre); }
                    @Override public void nicksAnteriores(long pid, String nombre) { v.dialogos.nicksAnteriores(pid, nombre); }
                    @Override public void abrirUrl(String url) { AccionesVentana.abrirUrl(v, url); }
                    @Override public void registrarDestino(long pid, String nombre) { v.registrarDestino(new SpoilerFreeRecs.Destino("perfil", pid, nombre, null)); }
                    @Override public void actualizarTextoBuscar() { v.partidas.actualizarTextoBuscar(); }
                    @Override public JToggleButton crearBotonPestana(String texto, Icon icono) { return dev.tirador.aoe2radar.ui.Navegador.pestana(texto, icono); }
                    @Override public void traerAlFrente() { v.toFront(); v.requestFocus(); }
                    @Override public void mostrarEstadoGlobal(String texto) { v.status.setText(texto); }
                    @Override public void cerrarPerfil() { v.mostrarDirectos(false); }
                    @Override public boolean enCursoReal(Match m) { return dev.tirador.aoe2radar.cache.Vivos.enCursoReal(m); }
                    @Override public boolean confirmarEspectar(String nombre) { return dev.tirador.aoe2radar.ui.ConfirmacionEspectar.confirmar(v, nombre); }
                    @Override public void espectarPartida(long matchId) { AccionesVentana.espectarPartida(v, matchId); }
                    @Override public void cargarPartidasEnTabla(List<Match> lista, Player sujeto) {
                        v.partidas.cargarPartidasEnTabla(lista, sujeto, v.watchlist.vistaActualId());
                        v.mostrarDirectos(false);
                    }
                    @Override public void buscarPartidasDe(long pid, String nombre) {
                        Player p = new Player(pid, nombre, v.watchlist.grupoDestino());
                        v.objetivoForzado = p; v.invitado = p; v.vistaDelInvitado = v.watchlist.vistaActualId();
                        v.playersList.clearSelection(); v.watchlist.aplicarFiltroGrupo(); v.mostrarDirectos(false); v.partidas.fetchMatches(v.partidas.fetchBtn);
                    }
                    @Override public void descargarSinCambiarVista(List<Match> lista, boolean enviar, Runnable alTerminar) {
                        v.partidas.descargarSinCambiarVista(lista, enviar, alTerminar);
                    }
                },
                ACTIVIDAD_CACHE, v.eloWatch, ALIASES, CANAL_DE, ELO_AYER, NOMBRES_AYER, v.twitchLive, dev.tirador.aoe2radar.cache.Anotaciones::notaDe,
                PERFILES_DIR, dev.tirador.aoe2radar.cache.HistorialDisco::cargarActividad, ACT_DIAS);
        v.centroCards.add(v.perfil.panel(), "perfil");
        center.add(v.centroCards, BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH);
        return center;
    }
}
