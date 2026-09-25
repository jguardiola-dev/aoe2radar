package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.service.EloSesion;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.ProfileService;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

import static dev.tirador.aoe2radar.service.ReglasPartida.enCursoReal;
import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.util.Formato.reloj;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * Implementación REAL de {@link MenusJugador} (antes, seis métodos de SpoilerFreeRecs: menuContextualJugador,
 * menuEnPartida, itemJugadorPartida, elo1v1Conocido, menuDeJugador, menuPerfilNavegador). Se movió tal cual en la
 * tanda 3 (oleada A2) de la fase 3; la ventana deja delegados de una línea con los nombres de siempre.
 * <p>Lo que antes leía directamente de la ventana (watchlist, diálogos, «espectar», abrir una URL) llega ahora por
 * la interfaz {@link Acciones}, implementada por la ventana con lambdas: así esta clase no conoce la watchlist ni
 * el resto de la ventana. El ELO 1v1 de sesión ({@code ELO_1V1}) sigue siendo el mismo campo de siempre, inyectado
 * por constructor (una sola instancia, compartida con el resto de menús de jugador que quedan en la ventana).
 */
public final class MenusJugadorSwing implements MenusJugador {

    /** Lo que estos menús necesitan de la ventana y no es navegación: espectar, abrir una URL, los diálogos de
     *  jugador (nota, alias, vinculadas, nicks) y el trocito de «añadir a mi watchlist» de menuDeJugador. */
    public interface Acciones {
        void espectarPartida(long matchId);
        void abrirUrl(String url);
        String nombreVisible(long pid, String nombre);
        String paisDe(long pid);

        String notaDe(long pid);
        void pedirAlias(long pid, String nombreOriginal);
        void pedirNota(long pid, String nombre);
        void borrarNota(long pid, String nombre);
        void mostrarVinculadas(long pid, String nombre);
        void nicksAnteriores(long pid, String nombre);

        /** ¿Ya está pid en la watchlist? */
        boolean enWatchlist(long pid);
        /** Grupos donde se puede fichar a alguien: «General», los grupos con gente y los guardados en config. */
        java.util.Set<String> gruposDisponibles();
        /** Ficha a pid en grupo (alta, guardar, reconstruir combos, refiltrar, refrescar watchlist y avisar). */
        void anadirAWatchlist(long pid, String nombre, String grupo);
        /** Diálogo «Nuevo grupo…»; null si se cancela. */
        String elegirGrupoDialog(String nombreSugerido);
    }

    private final EstadoVivo vivo;
    private final EloSesion elo1v1;
    private final ProfileService servicioPerfil;
    private final Navegacion navegacion;
    private final LongFunction<Object[]> liveFicha;
    private final Tareas tareas;
    private final Acciones acciones;

    public MenusJugadorSwing(EstadoVivo vivo, EloSesion elo1v1, ProfileService servicioPerfil, Navegacion navegacion,
                              LongFunction<Object[]> liveFicha, Tareas tareas, Acciones acciones) {
        this.vivo = vivo;
        this.elo1v1 = elo1v1;
        this.servicioPerfil = servicioPerfil;
        this.navegacion = navegacion;
        this.liveFicha = liveFicha;
        this.tareas = tareas;
        this.acciones = acciones;
    }

    @Override public void menuContextual(long pid, String nombre, MouseEvent e) { menuContextualJugador(pid, nombre, e); }
    @Override public JMenu enPartida(long pid) { return menuEnPartida(pid); }
    @Override public JMenu deJugador(long pid, String nombre) { return menuDeJugador(pid, nombre); }
    @Override public JMenu perfilNavegador(long pid) { return menuPerfilNavegador(pid); }

    /** El ELO 1v1 que ya se conoce de pid sin ir a la red, o null. */
    @Override public Integer elo1v1Conocido(long pid) {
        Integer e = elo1v1.conocido(pid); if (e != null) return e > 0 ? e : null;
        Object[] f = liveFicha.apply(pid); if (f != null && (Integer) f[2] > 0) return (Integer) f[2];
        e = servicioPerfil.eloVinculada(pid); if (e != null && e > 0) return e;
        FichaPerfil perfil = servicioPerfil.fichaConocida(pid); if (perfil != null && perfil.ladders().get("rm_1v1") instanceof int[] v && v[0] > 0) return v[0];
        return null;
    }

    /** Ítem de un jugador de una partida (bandera, ELO 1v1, civ); clic = su perfil. Pide el ELO que falte en segundo plano. */
    @Override public JMenuItem itemJugadorPartida(MatchPlayer p) {
        String nombre = acciones.nombreVisible(p.id, p.name);
        Integer e1 = elo1v1Conocido(p.id);
        JMenuItem it = new JMenuItem(nombre + (e1 != null ? "  1v1 " + e1 : "") + (p.civ != null && !p.civ.isBlank() ? "  ·  " + p.civ : ""), iconoBandera(acciones.paisDe(p.id)));
        it.setIconTextGap(6);
        it.addActionListener(a -> navegacion.abrirPerfil(p.id, nombre));
        if (((e1 == null && elo1v1.conocido(p.id) == null) || elo1v1.caducado(p.id)) && elo1v1.reservar(p.id)) tareas.enFondo("elo-1v1", () -> {   // el ELO 1v1 se pide una vez y se rellena en el propio ítem
            long pedido = elo1v1.ahora(); Integer e = servicioPerfil.elo1v1(p.id); elo1v1.apuntar(p.id, e, pedido);
            if (e != null && e > 0) SwingUtilities.invokeLater(() -> it.setText(nombre + "  1v1 " + e + (p.civ != null && !p.civ.isBlank() ? "  ·  " + p.civ : "")));
        });
        return it;
    }

    /** Menú contextual de un jugador fuera de la watchlist (Live now, listas): perfil, pestaña nueva y, si está en partida, aliados y rivales. */
    private void menuContextualJugador(long pid, String nombre, MouseEvent e) {
        JPopupMenu pm = new JPopupMenu();
        JMenuItem perf = new JMenuItem(t("Ver perfil", "View profile")); perf.addActionListener(a -> navegacion.abrirPerfil(pid, nombre)); pm.add(perf);
        JMenuItem perfN = new JMenuItem(t("Abrir perfil en pestaña nueva", "Open profile in a new tab")); perfN.addActionListener(a -> navegacion.abrirPerfilEnPestana(pid, nombre)); pm.add(perfN);
        JMenu enPartida = menuEnPartida(pid);
        if (enPartida != null) { pm.addSeparator(); pm.add(enPartida); }
        pm.show(e.getComponent(), e.getX(), e.getY());
    }

    /** Submenú «En partida ahora»: aliados y rivales de la partida en curso de pid, con bandera, ELO y civ; clic = su perfil. */
    private JMenu menuEnPartida(long pid) {
        Match m = vivo.partida(pid);
        if (m == null || !enCursoReal(m)) return null;
        MatchPlayer yo = null; for (MatchPlayer p : m.players) if (p.id == pid) yo = p;
        if (yo == null) return null;
        JMenu menu = new JMenu(t("En partida ahora", "In a game now") + (m.map != null ? " · " + m.map : "") + (m.started != null ? " · " + reloj(Duration.between(m.started, Instant.now())) : ""));
        List<MatchPlayer> aliados = new ArrayList<>(), rivales = new ArrayList<>();
        for (MatchPlayer p : m.players) { if (p.id == pid) continue; if (p.team == yo.team) aliados.add(p); else rivales.add(p); }
        if (!aliados.isEmpty()) {
            JMenuItem cab = new JMenuItem(t("Aliados", "Allies")); cab.setEnabled(false); menu.add(cab);
            for (MatchPlayer p : aliados) menu.add(itemJugadorPartida(p));
            menu.addSeparator();
        }
        JMenuItem cab2 = new JMenuItem(t("Rivales", "Opponents")); cab2.setEnabled(false); menu.add(cab2);
        for (MatchPlayer p : rivales) menu.add(itemJugadorPartida(p));
        if (m.id > 0) { menu.addSeparator(); JMenuItem esp = new JMenuItem(t("Espectar la partida", "Spectate the game")); esp.addActionListener(a -> acciones.espectarPartida(m.id)); menu.add(esp); }
        return menu;
    }

    private JMenu menuPerfilNavegador(long id) {
        JMenu m = new JMenu(t("Ver perfil en el navegador", "View profile in browser"));
        JMenuItem comp = new JMenuItem("aoe2companion");
        comp.addActionListener(a -> acciones.abrirUrl("https://www.aoe2companion.com/players/" + id));
        JMenuItem ins = new JMenuItem("aoe2insights");
        ins.addActionListener(a -> acciones.abrirUrl("https://www.aoe2insights.com/user/" + id + "/"));
        m.add(comp);
        m.add(ins);
        return m;
    }

    /** Submenú de acciones sobre un jugador concreto (contextual de la tabla). */
    private JMenu menuDeJugador(long pid, String nombre) {
        String vis = nombre.length() > 28 ? nombre.substring(0, 27) + "…" : nombre;
        JMenu mj = new JMenu(vis);
        JMenuItem perfNueva = new JMenuItem(t("Abrir perfil en pestaña nueva", "Open profile in a new tab"));
        perfNueva.addActionListener(a -> navegacion.abrirPerfilEnPestana(pid, nombre));
        mj.add(perfNueva);
        JMenu enPartida = menuEnPartida(pid);
        if (enPartida != null) mj.add(enPartida);
        JMenuItem alias = new JMenuItem(t("Mostrar como…", "Show as…"));
        alias.addActionListener(a -> acciones.pedirAlias(pid, nombre));
        mj.add(alias);
        JMenuItem nota = new JMenuItem(t("Nota…", "Note…"));
        nota.addActionListener(a -> acciones.pedirNota(pid, nombre));
        mj.add(nota);
        if (acciones.notaDe(pid) != null) {
            JMenuItem bn = new JMenuItem(t("Borrar nota", "Delete note"));
            bn.addActionListener(a -> acciones.borrarNota(pid, nombre));
            mj.add(bn);
        }
        JMenuItem perf = new JMenuItem(t("Perfil completo…", "Full profile…"));
        perf.addActionListener(a -> navegacion.abrirPerfil(pid, nombre));
        mj.add(perf);
        boolean ya = acciones.enWatchlist(pid);
        JMenu anadir = new JMenu(t("Añadir a mi watchlist", "Add to my watchlist"));
        if (ya) {
            anadir.setEnabled(false);
            anadir.setToolTipText(t("Ya está en tu watchlist", "Already in your watchlist"));
        } else {
            java.util.Set<String> gs = acciones.gruposDisponibles();
            for (String g : gs) {
                JMenuItem it = new JMenuItem(g);
                it.addActionListener(a -> acciones.anadirAWatchlist(pid, nombre, g));
                anadir.add(it);
            }
            anadir.addSeparator();
            JMenuItem nuevoG = new JMenuItem(t("+ Nuevo grupo…", "+ New group…"));
            nuevoG.addActionListener(a -> {
                String g = acciones.elegirGrupoDialog(nombre);
                if (g == null) return;
                acciones.anadirAWatchlist(pid, nombre, g);
            });
            anadir.add(nuevoG);
        }
        mj.add(anadir);
        JMenuItem vinc = new JMenuItem(t("Cuentas vinculadas…", "Linked accounts…"));
        vinc.addActionListener(a -> acciones.mostrarVinculadas(pid, nombre));
        mj.add(vinc);
        JMenuItem nicks = new JMenuItem(t("Nicks anteriores…", "Previous names…"));
        nicks.addActionListener(a -> acciones.nicksAnteriores(pid, nombre));
        mj.add(nicks);
        JMenu nav = new JMenu(t("Ver perfil en el navegador", "View profile in browser"));
        JMenuItem comp = new JMenuItem("aoe2companion");
        comp.addActionListener(a -> acciones.abrirUrl("https://www.aoe2companion.com/players/" + pid));
        JMenuItem ins = new JMenuItem("aoe2insights");
        ins.addActionListener(a -> acciones.abrirUrl("https://www.aoe2insights.com/user/" + pid + "/"));
        nav.add(comp);
        nav.add(ins);
        mj.add(nav);
        return mj;
    }
}
