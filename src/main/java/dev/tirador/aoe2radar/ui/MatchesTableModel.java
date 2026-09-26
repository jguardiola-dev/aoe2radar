package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;

import javax.swing.table.AbstractTableModel;

import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * El modelo de la tabla de Partidas. Era clase interna de {@link PartidasView}; sale a primer nivel (1.3) y lee la
 * fachada en cada llamada, igual que antes leía {@code PartidasView.this}: la lista {@code view}, el anfitrión, el
 * texto y {@code revelada}. Público porque RegresionCapturas (otro paquete) llama a {@code tableModel.getRowCount()}.
 */
public final class MatchesTableModel extends AbstractTableModel {
    private final PartidasView vista;

    MatchesTableModel(PartidasView vista) { this.vista = vista; }

    final String[] cols = { t("Fecha", "Date"), t("Jugador", "Player"), "Civ", t("Modo", "Mode"), t("Mapa", "Map"),
            t("Rival", "Opponent"), t("Civ rival", "Opp. civ"), "Rec", t("Resultado", "Result") };
    @Override public int getRowCount() { return vista.view.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int c) { return cols[c]; }
    @Override public boolean isCellEditable(int r, int c) { return false; }
    @Override public Class<?> getColumnClass(int c) {
        return switch (c) { case 0 -> PartidasTexto.FechaCell.class; default -> String.class; };
    }
    @Override public Object getValueAt(int r, int c) {
        Match m = vista.view.get(r);
        return switch (c) {
            case 0 -> m.finished == null && !vista.anfitrion.enCursoReal(m)
                    ? new PartidasTexto.FechaCell(null, m.started)
                    : new PartidasTexto.FechaCell(m.finished);
            case 1 -> vista.revelada(m) ? vista.texto.refConVeredicto(m) : vista.texto.refNombre(m);
            case 2 -> m.civDe(m.refId);
            case 3 -> m.mode;
            case 4 -> m.map;
            case 5 -> vista.texto.rivalTexto(m, vista.revelada(m));
            case 6 -> m.civRival();
            case 8 -> m.finished == null ? "" : (vista.revelada(m) ? "\u25C9" : "\u25CE");
            case 7 -> !m.estado.isBlank() ? m.estado
                      : m.enJuego ? t("✓✓ en juego", "✓✓ in game")
                      : m.enDisco ? t("✓ en disco", "✓ on disk")
                      : (m.povsConRec() > 0 ? m.povsConRec() + " POV" : t("¿?", "?"));
            default -> "";
        };
    }
}
