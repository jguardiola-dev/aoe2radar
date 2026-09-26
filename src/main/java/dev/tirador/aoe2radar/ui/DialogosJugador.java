package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.service.AnotacionesService;
import dev.tirador.aoe2radar.service.ProfileService;

import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * Los diálogos de un jugador que antes vivían en SpoilerFreeRecs: notaDe, pedirAlias, pedirNota, borrarNota,
 * mostrarVinculadas, nicksAnteriores. Se movieron tal cual en la tanda 3 (oleada A2) de la fase 3; la ventana deja
 * delegados de una línea con los nombres de siempre (los usan el constructor, la watchlist, la tabla y Perfil vía
 * su propio Anfitrion).
 * <p>El alias/nota en sí vive en {@link AnotacionesService} (fase 2, estado compartido con cache.Anotaciones). Lo
 * que toca la tabla o la lista de la watchlist de la ventana llega por {@link Anfitrion}. La red de «Nicks
 * anteriores» (companion + Steam) llega por {@link RedSteam}: ui no puede importar api, así que la ventana
 * implementa esta interfaz con sus propios campos COMPANION/steam. Todo en el EDT salvo los SwingWorker, igual que
 * en la 1.1.
 */
public final class DialogosJugador {

    /** Lo que estos diálogos tocan de la ventana y no es la persistencia del alias/nota en sí. */
    public interface Anfitrion {
        // --- notas/alias: repintar lo que ya está en pantalla ---
        void repintarLista();
        void refrescarAlturas();
        void refrescarTabla();
        void ajustarColumnasTabla();
        void actualizarControles();
        void refrescarSujetos();
        void mostrarEstado(String texto);

        // --- «cuentas vinculadas»: alta en la watchlist ---
        boolean enWatchlist(long pid);
        void ponerEloWatch(long pid, int elo);
        Set<String> gruposDisponibles();
        String grupoActivo();
        void agregarJugador(long pid, String nombre, String grupo);
        void guardarJugadores();
        void reconstruirGrupos();
        void marcarFamiliaVinculada(Set<Long> familia);
        void aplicarFiltro();
        void refrescarWatchlist();

        /** Pausa de cortesía entre llamadas (dormir de la ventana: respeta el freno de operaciones cancelables). */
        void pausaCortesia();
    }

    /** La red de «Nicks anteriores»: el steamId del companion y el historial de alias de Steam. ui no importa api. */
    public interface RedSteam {
        String steamId(long pid) throws Exception;
        List<String[]> alias(String steamId) throws Exception;
    }

    private final Component padre;
    private final AnotacionesService anotaciones;
    private final ProfileService servicioPerfil;
    private final RedSteam redSteam;
    private final Anfitrion anfitrion;

    public DialogosJugador(Component padre, AnotacionesService anotaciones, ProfileService servicioPerfil,
                            RedSteam redSteam, Anfitrion anfitrion) {
        this.padre = padre;
        this.anotaciones = anotaciones;
        this.servicioPerfil = servicioPerfil;
        this.redSteam = redSteam;
        this.anfitrion = anfitrion;
    }

    public String notaDe(long pid) { return anotaciones.notaDe(pid); }

    public void borrarNota(long pid, String nombre) {
        anotaciones.ponerNota(pid, "");
        anfitrion.repintarLista();
        anfitrion.refrescarAlturas();
        anfitrion.refrescarTabla();
        anfitrion.ajustarColumnasTabla();
        anfitrion.actualizarControles();
        anfitrion.mostrarEstado(t("Nota quitada a ", "Note removed from ") + nombre + ".");
    }

    public void pedirNota(long pid, String nombre) {
        String notaActual = anotaciones.notaDe(pid);
        JTextArea area = new JTextArea(notaActual != null ? notaActual : "", 4, 34);
        area.setLineWrap(true); area.setWrapStyleWord(true);
        String guardarO = t("Guardar", "Save"), borrarO = t("Borrar", "Delete"), cancelarO = t("Cancelar", "Cancel");
        boolean tenia = notaActual != null;
        Object[] ops = tenia ? new Object[]{ guardarO, borrarO, cancelarO } : new Object[]{ guardarO, cancelarO };
        int r = JOptionPane.showOptionDialog(padre, new Object[]{
                t("Nota sobre ", "Note about ") + nombre + t(" (solo la ves tú):", " (only you see it):"),
                new JScrollPane(area) }, t("Nota", "Note"), JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, ops, guardarO);
        if (r < 0 || ops[r].equals(cancelarO)) return;
        String nota = ops[r].equals(borrarO) ? "" : area.getText().trim().replace("\n", " ");
        anotaciones.ponerNota(pid, nota);
        anfitrion.repintarLista();
        anfitrion.refrescarAlturas();   // la sublínea de la nota nace o muere: re-medir
        anfitrion.refrescarTabla();
        anfitrion.mostrarEstado(nota.isEmpty() ? t("Nota quitada.", "Note removed.") : t("Nota guardada para ", "Note saved for ") + nombre + ".");
    }

    public void pedirAlias(long pid, String original) {
        String actual = anotaciones.aliasDe(pid);
        String nuevo = (String) JOptionPane.showInputDialog(padre,
                t("Nombre con el que quieres ver a ", "Name you want to see for ") + original
                        + t(" en toda la app (vacío = quitar el alias):", " across the app (empty = remove alias):"),
                t("Mostrar como\u2026", "Show as\u2026"), JOptionPane.PLAIN_MESSAGE, null, null, actual != null ? actual : "");
        if (nuevo == null) return;
        nuevo = nuevo.trim();
        anotaciones.ponerAlias(pid, original, nuevo);
        anfitrion.repintarLista();
        anfitrion.refrescarTabla();
        anfitrion.refrescarSujetos();
        anfitrion.mostrarEstado(nuevo.isEmpty() ? t("Alias quitado.", "Alias removed.")
                : original + " \u2192 " + nuevo);
    }

    /** Historial de alias que guarda Steam para la cuenta (endpoint público
     *  de la comunidad, vía el steamId del companion). Solo bajo demanda. */
    public void nicksAnteriores(long pid, String nombre) {
        anfitrion.mostrarEstado(t("Consultando nicks anteriores de ", "Looking up previous names of ") + nombre + "\u2026");
        new SwingWorker<List<String[]>, Void>() {
            String motivo;
            @Override protected List<String[]> doInBackground() {
                try {
                    String sid = redSteam.steamId(pid);
                    String steamId = sid == null ? "" : sid.trim();
                    if (steamId.isBlank() || "null".equals(steamId)) {
                        motivo = t("Esta cuenta no tiene Steam vinculado en el companion: sin historial disponible.",
                                   "This account has no Steam link on the companion: no history available.");
                        return null;
                    }
                    return redSteam.alias(steamId);
                } catch (Exception ex) {
                    motivo = t("No se pudo consultar el historial (perfil de Steam privado o servicio caído).",
                               "Could not fetch the history (private Steam profile or service down).");
                    return null;
                }
            }
            @Override protected void done() {
                anfitrion.mostrarEstado(t("Listo.", "Ready."));
                List<String[]> alias;
                try { alias = get(); } catch (Exception e) { alias = null; }
                if (alias == null) {
                    JOptionPane.showMessageDialog(padre, motivo,
                            t("Nicks anteriores", "Previous names"), JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                if (alias.isEmpty()) {
                    JOptionPane.showMessageDialog(padre,
                            nombre + t(" no tiene cambios de nombre registrados en Steam.",
                                       " has no name changes recorded on Steam."),
                            t("Nicks anteriores", "Previous names"), JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                StringBuilder sb = new StringBuilder("<html><b>")
                        .append(t("Nicks anteriores de ", "Previous names of "))
                        .append(escapeHtml(nombre)).append("</b><br><br>");
                for (String[] a : alias) {
                    sb.append(escapeHtml(a[0]));
                    if (!a[1].isBlank()) sb.append("&nbsp;&nbsp;<font color='#8a8a8a'>").append(escapeHtml(a[1])).append("</font>");
                    sb.append("<br>");
                }
                sb.append("<br><font color='#8a8a8a'>")
                  .append(t("Fuente: historial público de Steam (últimos cambios).",
                            "Source: Steam public history (latest changes)."))
                  .append("</font></html>");
                JOptionPane.showMessageDialog(padre, sb.toString(),
                        t("Nicks anteriores", "Previous names"), JOptionPane.PLAIN_MESSAGE);
            }
        }.execute();
    }

    /** Diálogo de cuentas vinculadas: selección múltiple y grupo de destino. */
    public void mostrarVinculadas(long profileId, String nombre) {
        final Integer[] eloPropio = new Integer[1];
        anfitrion.mostrarEstado(t("Buscando cuentas vinculadas de ", "Looking up linked accounts of ") + nombre + "…");
        new SwingWorker<List<Perfil.Vinculada>, Void>() {
            final Map<Long, Integer> elosV = new HashMap<>();   // vid → ELO 1v1 actual (null: sin ELO)
            @Override protected List<Perfil.Vinculada> doInBackground() {
                List<Perfil.Vinculada> vinc = servicioPerfil.vinculadas(profileId);
                for (Perfil.Vinculada v : vinc) {   // ELO 1v1 actual de cada cuenta, del ladder
                    elosV.put(v.pid(), servicioPerfil.elo1v1(v.pid()));
                    anfitrion.pausaCortesia();
                }
                if (!anfitrion.enWatchlist(profileId)) eloPropio[0] = servicioPerfil.elo1v1(profileId);
                return vinc;
            }
            @Override protected void done() {
                List<Perfil.Vinculada> vinc;
                try { vinc = get(); } catch (Exception e) { vinc = List.of(); }
                if (vinc.isEmpty()) {
                    anfitrion.mostrarEstado(t("Listo.", "Ready."));
                    JOptionPane.showMessageDialog(padre,
                            nombre + t(" no tiene cuentas vinculadas conocidas.",
                                       " has no known linked accounts."),
                            t("Cuentas vinculadas", "Linked accounts"), JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                anfitrion.mostrarEstado(t("Listo.", "Ready."));
                if (eloPropio[0] != null) anfitrion.ponerEloWatch(profileId, eloPropio[0]);
                for (Perfil.Vinculada v : vinc) {   // el ELO consultado siembra la watchlist al momento
                    if (elosV.get(v.pid()) instanceof Integer e) anfitrion.ponerEloWatch(v.pid(), e);
                }
                DefaultListModel<String> modelo = new DefaultListModel<>();
                List<Perfil.Vinculada> anadibles = new ArrayList<>();
                for (Perfil.Vinculada v : vinc) {
                    boolean ya = anfitrion.enWatchlist(v.pid());
                    Integer eloV = elosV.get(v.pid());
                    String fila = v.pais().isBlank() ? String.valueOf(v.nombre()) : v.nombre() + " \u00B7 " + v.pais();
                    fila = fila + (eloV != null ? " \u00B7 " + eloV + " ELO" : t(" \u00B7 sin ELO", " \u00B7 no ELO"));
                    if (v.partidas() >= 0)
                        fila = fila + " \u00B7 " + v.partidas() + t(" partidas", " games");
                    fila = fila + (ya ? t("  (ya en tu watchlist)", "  (already in your watchlist)") : "");
                    modelo.addElement(fila);
                    if (!ya) anadibles.add(v);
                }
                JList<String> lista = new JList<>(modelo);
                lista.setFont(lista.getFont().deriveFont(lista.getFont().getSize2D() + 1f));
                lista.setVisibleRowCount(Math.min(8, modelo.size()));
                Set<String> gs = anfitrion.gruposDisponibles();
                JComboBox<String> grupoDest = new JComboBox<>(gs.toArray(String[]::new));
                String ga = anfitrion.grupoActivo();
                if (ga != null) grupoDest.setSelectedItem(ga);
                JPanel panel = new JPanel(new BorderLayout(0, 8));
                panel.add(new JLabel(t("Cuentas vinculadas de ", "Linked accounts of ") + nombre + ":"),
                        BorderLayout.NORTH);
                panel.add(new JScrollPane(lista), BorderLayout.CENTER);
                JPanel abajo = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                abajo.add(new JLabel(t("Grupo destino:", "Target group:")));
                abajo.add(grupoDest);
                panel.add(abajo, BorderLayout.SOUTH);
                // Visor primero: mirar no compromete. Solo el botón guarda.
                String btnGuardar = t("Guardar en el grupo", "Save to group");
                String btnCerrar  = t("Cerrar", "Close");
                int r = JOptionPane.showOptionDialog(padre, panel,
                        t("Cuentas vinculadas", "Linked accounts"), JOptionPane.DEFAULT_OPTION,
                        JOptionPane.PLAIN_MESSAGE, null,
                        new Object[]{ btnGuardar, btnCerrar }, btnCerrar);
                if (r != 0) return;   // Cerrar o Esc: nada se añade, nada se vincula
                String g = String.valueOf(grupoDest.getSelectedItem());
                int nuevos = 0;
                Set<Long> familia = new HashSet<>();
                familia.add(profileId);
                if (!anfitrion.enWatchlist(profileId)) {   // la MATRIZ entra también (caso ★/buscador)
                    anfitrion.agregarJugador(profileId, nombre, g);
                    nuevos++;
                }
                int[] selIdx = lista.getSelectedIndices();
                List<Perfil.Vinculada> elegidos = new ArrayList<>();
                if (selIdx.length == 0) elegidos.addAll(anadibles);   // sin selección: todas las nuevas
                else {
                    int i = 0;
                    for (Perfil.Vinculada v : vinc) {
                        boolean ya = anfitrion.enWatchlist(v.pid());
                        for (int s : selIdx) if (s == i && !ya) elegidos.add(v);
                        i++;
                    }
                }
                for (Perfil.Vinculada v : elegidos)
                    if (!anfitrion.enWatchlist(v.pid())) {
                        anfitrion.agregarJugador(v.pid(), v.nombre(), g);
                        nuevos++;
                    }
                for (Perfil.Vinculada v : vinc) if (anfitrion.enWatchlist(v.pid())) familia.add(v.pid());
                if (nuevos > 0) {
                    anfitrion.guardarJugadores();
                    anfitrion.reconstruirGrupos();
                }
                anfitrion.marcarFamiliaVinculada(familia);   // persiste y repinta por sí mismo
                anfitrion.aplicarFiltro();
                anfitrion.refrescarWatchlist();
                anfitrion.mostrarEstado(t("Familia de ", "Family of ") + nombre
                        + t(" guardada en «", " saved to “") + g + "\u00bb ("
                        + nuevos + t(" nuevas).", " new)."));
            }
        }.execute();
    }
}
