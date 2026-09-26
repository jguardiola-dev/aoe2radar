// Cableado del jugador: los diálogos (nota, alias, cuentas vinculadas, nicks: ui.DialogosJugador), los menús
// contextuales (ui.MenusJugadorSwing), la lista/tabla completa (ui.Listas) y el botón «Perfil». Antes eran
// inicializadores de campo y clases anónimas de SpoilerFreeRecs: se sacan aquí para separar QUÉ necesita la
// ventana de CÓMO se conecta. Métodos static que reciben la ventana (parámetro v): sin estado propio, pura
// composición (fase 3, tanda 4, B1).
package dev.tirador.aoe2radar;

import dev.tirador.aoe2radar.cache.Anotaciones;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.ui.DialogosJugador;
import dev.tirador.aoe2radar.ui.Listas;
import dev.tirador.aoe2radar.ui.MenusJugador;
import dev.tirador.aoe2radar.ui.MenusJugadorSwing;
import dev.tirador.aoe2radar.ui.Tareas;

import java.util.List;
import java.util.Set;

import static dev.tirador.aoe2radar.app.Servicios.ANOTACIONES;
import static dev.tirador.aoe2radar.app.Servicios.COMPANION;
import static dev.tirador.aoe2radar.app.Servicios.ELO_1V1;
import static dev.tirador.aoe2radar.app.Servicios.PAUSA_MS;
import static dev.tirador.aoe2radar.app.Servicios.SERVICIO_PERFIL;
import static dev.tirador.aoe2radar.app.Servicios.VIVO;
import static dev.tirador.aoe2radar.app.Servicios.dormir;
import static dev.tirador.aoe2radar.cache.Anotaciones.nombreVisible;
import static dev.tirador.aoe2radar.cache.HistorialDisco.ACTIVIDAD_CACHE;
import static dev.tirador.aoe2radar.util.I18n.t;

/** Cableado de todo lo relacionado con UN jugador: la lista/tabla completa, los diálogos y los menús
 *  contextuales, y el botón «Perfil». Vive en el paquete raíz porque lee campos de paquete de la ventana
 *  (playersList, watchlist, partidas...) sin volverlos public. Sin estado propio: solo construye colaboradores
 *  con lo que la ventana ya tiene (fase 3, tanda 4, B1). */
final class CableadoJugador {
    private CableadoJugador() {}

    /** ui.Listas (tarjeta, filaBarra, enlaceVerTodo y las ventanas de lista/tabla completa). */
    static Listas listas(SpoilerFreeRecs v) {
        return new Listas(v, ctrl -> v.ultimoClicCtrl = ctrl);
    }

    /** Diálogos de un jugador (nota, alias, cuentas vinculadas, nicks anteriores): ver ui.DialogosJugador. Lo que
     *  toca la tabla/lista de la ventana llega por su Anfitrion; la red de Steam, por RedSteam (ui no importa api). */
    static DialogosJugador dialogos(SpoilerFreeRecs v) {
        return new DialogosJugador(v, ANOTACIONES, SERVICIO_PERFIL,
                new DialogosJugador.RedSteam() {
                    @Override public String steamId(long pid) throws Exception { return COMPANION.perfil(pid).steamId(); }
                    @Override public List<String[]> alias(String steamId) throws Exception { return v.steam.alias(steamId); }
                },
                new DialogosJugador.Anfitrion() {
                    @Override public void repintarLista() { v.playersList.repaint(); }
                    @Override public void refrescarAlturas() { v.watchlist.refrescarAlturasWatch(); }
                    @Override public void refrescarTabla() { v.partidas.refrescarTabla(); }
                    @Override public void ajustarColumnasTabla() { v.partidas.ajustarColumnas(); }
                    @Override public void actualizarControles() { v.navegador.actualizarControlesTabla(); }
                    @Override public void refrescarSujetos() { v.partidas.refrescarSujetos(v.partidas.ultimosSujetos, v.invitado != null); }   // el ELO recién llegado, a la cabecera
                    @Override public void mostrarEstado(String texto) { v.status.setText(texto); }
                    @Override public boolean enWatchlist(long pid) { return v.watchlist.containsPlayerId(pid); }
                    @Override public void ponerEloWatch(long pid, int elo) { v.eloWatch.put(pid, elo); }
                    @Override public Set<String> gruposDisponibles() { return v.watchlist.gruposDisponibles(); }
                    @Override public String grupoActivo() { return v.watchlist.grupoActivo(); }
                    @Override public void agregarJugador(long pid, String nombre, String grupo) { v.todosJugadores.add(new Player(pid, nombre, grupo)); }
                    @Override public void guardarJugadores() { v.watchlist.savePlayers(); }
                    @Override public void reconstruirGrupos() { v.watchlist.rebuildGrupos(); }
                    @Override public void marcarFamiliaVinculada(Set<Long> familia) { v.watchlist.marcarVinculo(familia); }
                    @Override public void aplicarFiltro() { v.watchlist.aplicarFiltroGrupo(); }
                    @Override public void refrescarWatchlist() { v.watchlist.refrescarWatchlist(); }
                    @Override public void pausaCortesia() { dormir(PAUSA_MS / 2); }
                });
    }

    /** Menús de jugador para las vistas: ver ui.MenusJugadorSwing (implementación real). Lo que necesita de la
     *  ventana y no es navegación llega por Acciones. */
    static MenusJugador menus(SpoilerFreeRecs v) {
        return new MenusJugadorSwing(VIVO, ELO_1V1, SERVICIO_PERFIL, v,
                pid -> v.liveNow != null ? v.liveNow.liveFicha(pid) : null, Tareas.SWING,
                new MenusJugadorSwing.Acciones() {
                    @Override public void espectarPartida(long matchId) { AccionesVentana.espectarPartida(v, matchId); }
                    @Override public void abrirUrl(String url) { AccionesVentana.abrirUrl(v, url); }
                    @Override public String nombreVisible(long pid, String nombre) { return Anotaciones.nombreVisible(pid, nombre); }
                    @Override public String paisDe(long pid) { return dev.tirador.aoe2radar.cache.Paises.paisDe(pid); }
                    @Override public String notaDe(long pid) { return v.dialogos.notaDe(pid); }
                    @Override public void pedirAlias(long pid, String nombreOriginal) { v.dialogos.pedirAlias(pid, nombreOriginal); }
                    @Override public void pedirNota(long pid, String nombre) { v.dialogos.pedirNota(pid, nombre); }
                    @Override public void borrarNota(long pid, String nombre) { v.dialogos.borrarNota(pid, nombre); }
                    @Override public void mostrarVinculadas(long pid, String nombre) { v.dialogos.mostrarVinculadas(pid, nombre); }
                    @Override public void nicksAnteriores(long pid, String nombre) { v.dialogos.nicksAnteriores(pid, nombre); }
                    @Override public boolean enWatchlist(long pid) { return v.watchlist.containsPlayerId(pid); }
                    @Override public Set<String> gruposDisponibles() { return v.watchlist.gruposDisponibles(); }
                    @Override public void anadirAWatchlist(long pid, String nombre, String grupo) {
                        v.todosJugadores.add(new Player(pid, nombre, grupo));
                        v.watchlist.savePlayers();
                        v.watchlist.rebuildGrupos();
                        v.watchlist.aplicarFiltroGrupo();
                        v.watchlist.refrescarWatchlist();
                        v.status.setText(nombre + t(" añadido a «", " added to \u201C") + grupo + "\u00bb.");
                        v.watchlist.ofrecerVinculadasTrasAlta(pid, nombre, grupo);   // siempre que alguien entra en un grupo, se revisan sus cuentas vinculadas
                    }
                    @Override public String elegirGrupoDialog(String nombreSugerido) { return v.watchlist.elegirGrupoDialog(nombreSugerido); }
                });
    }

    /** El botón «Perfil»: el jugador seleccionado en la watchlist o, si no hay, la página con el buscador. No es
     *  navegación (no decide CÓMO se abre, decide A QUIÉN): la llama ui.Navegador como colaborador (Runnable)
     *  cuando se pulsa la pestaña directamente. La ventana deja un delegado de una línea con este nombre
     *  porque this::perfilDesdeBoton se pasa al construir Navegador. */
    static void perfilDesdeBoton(SpoilerFreeRecs v) {
        List<Player> sel = v.playersList.getSelectedValuesList();
        if (!sel.isEmpty()) v.abrirPerfil(sel.get(0).id(), nombreVisible(sel.get(0).id(), sel.get(0).name()));
        else if (v.partidas.ultimosSujetos.size() == 1 && v.partidas.recsCards != null && v.partidas.recsCards.isShowing()) v.abrirPerfil(v.partidas.ultimosSujetos.get(0).id(), nombreVisible(v.partidas.ultimosSujetos.get(0).id(), v.partidas.ultimosSujetos.get(0).name()));   // «Partidas de: X» → su perfil
        else if (v.perfil.pidAbierto() > 0 && ACTIVIDAD_CACHE.containsKey(v.perfil.pidAbierto())) v.abrirPerfil(v.perfil.pidAbierto(), v.perfil.nombreAbierto());
        else v.abrirPerfil(0, "");
    }
}
