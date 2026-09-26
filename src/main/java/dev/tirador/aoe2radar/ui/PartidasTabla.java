package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static dev.tirador.aoe2radar.ui.Componentes.colorVivoTabla;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * La tabla de Partidas: el JTable con sus renderers, anchos y orden de columnas (guardados en config), los atajos
 * de teclado, el menú contextual y los clics; y la selección y el estado de cada fila. Sale tal cual de
 * {@link PartidasView} (1.3): el estado (table, view, tableModel, reveladas…) sigue en la fachada y se lee y
 * escribe por {@code vista}, en el momento de usarlo (nunca en el constructor: la ventana aún se está montando).
 */
final class PartidasTabla {

    static final String ORDEN_COLUMNAS_DEFECTO = "0,1,2,5,6,4,3,7,8";

    private final PartidasView vista;

    PartidasTabla(PartidasView vista) { this.vista = vista; }

    /** El JTable completo: renderers, anchos, orden de columnas, atajos y menú contextual. La ventana la llama
     *  donde antes llamaba a construirTablaPartidas(). */
    public void construirTabla() {
        vista.table = new JTable(vista.tableModel) {
            @Override public String getToolTipText(MouseEvent ev) {
                int r = rowAtPoint(ev.getPoint());
                if (r < 0) return null;
                int mr = convertRowIndexToModel(r);
                if (mr < 0 || mr >= vista.view.size()) return null;
                Match m = vista.view.get(mr);
                int mc = convertColumnIndexToModel(columnAtPoint(ev.getPoint()));
                if (mc == 1 && vista.dialogos.notaDe(m.refId) != null) return t("Nota: ", "Note: ") + vista.dialogos.notaDe(m.refId);
                if (mc == 5 && m.players.size() == 2)
                    for (MatchPlayer mp : m.players)
                        if (mp.id != m.refId && vista.dialogos.notaDe(mp.id) != null) return t("Nota: ", "Note: ") + vista.dialogos.notaDe(mp.id);
                if (vista.anfitrion.enCursoReal(m))
                    return t("EN DIRECTO — doble clic para espectar", "LIVE — double-click to spectate");
                String enf = vista.texto.enfrentamiento(m, vista.revelada(m));
                if (vista.revelada(m)) {
                    String tr = PartidasTexto.tipResultado(m);
                    if (tr != null) return "<html>" + (enf.startsWith("<html>") ? enf.substring(6, enf.length() - 7) : escapeHtml(enf))
                            + "<br>" + escapeHtml(tr) + "</html>";
                }
                return enf;
            }
            @Override public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                int mr = convertRowIndexToModel(row);
                boolean viva = mr >= 0 && mr < vista.view.size() && vista.anfitrion.enCursoReal(vista.view.get(mr));
                c.setFont(viva ? getFont().deriveFont(Font.BOLD) : getFont());
                if (!isRowSelected(row))
                    c.setForeground(viva ? colorVivoTabla() : getForeground());
                if (c instanceof JLabel jl && convertColumnIndexToModel(column) == 1
                        && mr >= 0 && mr < vista.view.size())
                    jl.setToolTipText(vista.enlaceWatchlist.tipCuentaVinculada(vista.view.get(mr)));
                return c;
            }
        };
        vista.table.setRowHeight(24);
        vista.table.setAutoCreateRowSorter(true);
        vista.table.getColumnModel().getColumn(0).setPreferredWidth(90);
        vista.table.getColumnModel().getColumn(1).setPreferredWidth(130);
        vista.table.getColumnModel().getColumn(2).setPreferredWidth(95);
        vista.table.getColumnModel().getColumn(3).setPreferredWidth(110);
        vista.table.getColumnModel().getColumn(4).setPreferredWidth(110);
        vista.table.getColumnModel().getColumn(5).setPreferredWidth(230);
        vista.table.getColumnModel().getColumn(6).setPreferredWidth(95);
        vista.table.getColumnModel().getColumn(7).setPreferredWidth(100);
        vista.table.getColumnModel().getColumn(8).setPreferredWidth(80);
        {
            String[] anchos = leerConfig("tabla_anchos", "").split(",");
            if (anchos.length == vista.table.getColumnCount()) for (int i = 0; i < anchos.length; i++) { try { int a = Integer.parseInt(anchos[i].trim()); if (a >= 20) vista.table.getColumnModel().getColumn(i).setPreferredWidth(a); } catch (NumberFormatException ignored) { } }
        }
        DefaultTableCellRenderer ojoR = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foc, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, sel, foc, row, col);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                boolean abierto = "\u25C9".equals(String.valueOf(v));
                l.setFont(l.getFont().deriveFont(abierto ? Font.BOLD : Font.PLAIN, 14f));
                if (!sel) l.setForeground(abierto ? (temaOscuroActivo ? new Color(0xd9, 0xa5, 0x5b) : new Color(0x9a, 0x6b, 0x1f))
                                                  : new Color(0x8a, 0x8a, 0x8a));
                l.setToolTipText(v == null || String.valueOf(v).isBlank() ? null
                        : (abierto ? t("Clic: volver a tapar el resultado", "Click: hide the result again")
                                   : t("Clic: revelar el resultado de esta partida", "Click: reveal this game's result")));
                return l;
            }
        };
        vista.table.getColumnModel().getColumn(8).setCellRenderer(ojoR);
        aplicarOrdenColumnas(leerConfig("tabla_orden", ORDEN_COLUMNAS_DEFECTO));
        javax.swing.Timer guardaCols = new javax.swing.Timer(800, ev -> guardarColumnas());
        guardaCols.setRepeats(false);
        vista.table.getColumnModel().addColumnModelListener(new javax.swing.event.TableColumnModelListener() {
            @Override public void columnMoved(javax.swing.event.TableColumnModelEvent e) { if (e.getFromIndex() != e.getToIndex()) guardaCols.restart(); }
            @Override public void columnMarginChanged(javax.swing.event.ChangeEvent e) { guardaCols.restart(); }
            @Override public void columnAdded(javax.swing.event.TableColumnModelEvent e) { }
            @Override public void columnRemoved(javax.swing.event.TableColumnModelEvent e) { }
            @Override public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) { }
        });
        DefaultTableCellRenderer centrado = new DefaultTableCellRenderer();
        centrado.setHorizontalAlignment(SwingConstants.CENTER);
        for (int ci : new int[]{ 0, 2, 3, 4, 6 }) vista.table.getColumnModel().getColumn(ci).setCellRenderer(centrado);
        vista.table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                int x = 0;
                for (int c = 0; c < vista.table.getColumnCount(); c++) {
                    x += vista.table.getColumnModel().getColumn(c).getWidth();
                    if (Math.abs(e.getX() - x) <= 4) { ajustarColumna(c); return; }
                }
            }
        });
        vista.table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sfrDescargar");
        vista.table.getActionMap().put("sfrDescargar", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (vista.dlSel.isEnabled() && !selectedRows().isEmpty()) vista.download(selectedRows());
            }
        });
        vista.ventana.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK), "sfrBuscar");
        vista.ventana.getRootPane().getActionMap().put("sfrBuscar", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { vista.anfitrion.enfocarBuscador(); }
        });
        vista.ventana.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "sfrRefrescar");
        vista.ventana.getRootPane().getActionMap().put("sfrRefrescar", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { vista.anfitrion.refrescarDirectos(); }
        });
        vista.table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybePopupTabla(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopupTabla(e); }
            void maybePopupTabla(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int r = vista.table.rowAtPoint(e.getPoint());
                if (r < 0) return;
                if (!vista.table.isRowSelected(r)) vista.table.setRowSelectionInterval(r, r);
                int mr = vista.table.convertRowIndexToModel(r);
                if (mr < 0 || mr >= vista.view.size()) return;
                Match m = vista.view.get(mr);
                vista.menuPartida.mostrar(e, m);
            }
            @Override public void mouseClicked(MouseEvent e) {
                int r0 = vista.table.rowAtPoint(e.getPoint()), c0 = vista.table.columnAtPoint(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e) && r0 >= 0 && c0 >= 0 && vista.table.convertColumnIndexToModel(c0) == 8) {
                    if (e.getClickCount() != 1) return;
                    int mr0 = vista.table.convertRowIndexToModel(r0);
                    if (mr0 < 0 || mr0 >= vista.view.size()) return;
                    Match m0 = vista.view.get(mr0);
                    if (m0.finished == null) return;
                    if (!vista.reveladas.remove(m0.id)) vista.reveladas.add(m0.id);
                    vista.tableModel.fireTableRowsUpdated(mr0, mr0);
                    return;
                }
                if (e.getClickCount() != 2 || !vista.dlSel.isEnabled() || e.isControlDown() || e.isShiftDown()) return;
                int r = vista.table.rowAtPoint(e.getPoint());
                if (r < 0) return;
                int mr = vista.table.convertRowIndexToModel(r);
                if (mr < 0 || mr >= vista.view.size()) return;
                Match m = vista.view.get(mr);
                if (vista.anfitrion.enCursoReal(m)) {
                    if (vista.anfitrion.confirmarEspectar(vista.texto.refNombre(m))) vista.anfitrion.espectarVerificando(m.players.isEmpty() ? 0 : m.players.get(0).id, m.id);
                    return;
                }
                else if (m.finished == null)
                    vista.anfitrion.estado(t("Esa partida quedó colgada en el servidor (crash): no hay rec que bajar.",
                            "That game hung on the server (crash): there's no rec to download."));
                else vista.download(List.of(m));
            }
        });
    }

    void ajustarColumna(int c) {
        TableColumn col = vista.table.getColumnModel().getColumn(c);
        int mc = vista.table.convertColumnIndexToModel(c);
        TableCellRenderer hr = col.getHeaderRenderer() != null ? col.getHeaderRenderer() : vista.table.getTableHeader().getDefaultRenderer();
        int w = hr.getTableCellRendererComponent(vista.table, col.getHeaderValue(), false, false, -1, c).getPreferredSize().width + 16;
        int filas = Math.min(vista.table.getRowCount(), 300);
        for (int r = 0; r < filas; r++) {
            Component comp = vista.table.prepareRenderer(vista.table.getCellRenderer(r, c), r, c);
            w = Math.max(w, comp.getPreferredSize().width + 14);
        }
        int max = mc == 1 || mc == 5 ? 420 : 260;
        col.setPreferredWidth(Math.max(56, Math.min(max, w)));
    }

    public void ajustarColumnas() {
        if (vista.table == null || vista.table.getColumnCount() == 0) return;
        for (int c = 0; c < vista.table.getColumnCount(); c++) ajustarColumna(c);
    }

    public List<Match> selectedRows() {
        List<Match> out = new ArrayList<>();
        for (int r : vista.table.getSelectedRows()) out.add(vista.view.get(vista.table.convertRowIndexToModel(r)));
        return out;
    }

    public List<Match> allRows() { return new ArrayList<>(vista.view); }

    void setEstado(Match m, String txt) {
        SwingUtilities.invokeLater(() -> {
            m.estado = txt;
            int idx = vista.view.indexOf(m);
            if (idx >= 0) vista.tableModel.fireTableRowsUpdated(idx, idx);
        });
    }

    void aplicarOrdenColumnas(String orden) {
        try {
            String[] partes = orden.split(",");
            if (partes.length != vista.table.getColumnCount()) return;
            for (int destino = 0; destino < partes.length; destino++) {
                int modelo = Integer.parseInt(partes[destino].trim());
                int actual = vista.table.convertColumnIndexToView(modelo);
                if (actual >= 0 && actual != destino) vista.table.getColumnModel().moveColumn(actual, destino);
            }
        } catch (RuntimeException ex) { log("columnas: orden ilegible: " + orden); }
    }

    void guardarColumnas() {
        StringBuilder orden = new StringBuilder();
        for (int v = 0; v < vista.table.getColumnCount(); v++) { if (v > 0) orden.append(','); orden.append(vista.table.convertColumnIndexToModel(v)); }
        StringBuilder anchos = new StringBuilder();
        for (int m = 0; m < vista.table.getModel().getColumnCount(); m++) { if (m > 0) anchos.append(','); anchos.append(vista.table.getColumnModel().getColumn(vista.table.convertColumnIndexToView(m)).getWidth()); }
        guardarConfig("tabla_orden", orden.toString());
        guardarConfig("tabla_anchos", anchos.toString());
    }
}
