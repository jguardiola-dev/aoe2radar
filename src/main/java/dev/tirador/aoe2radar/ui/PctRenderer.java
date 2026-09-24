package dev.tirador.aoe2radar.ui;

import javax.swing.table.DefaultTableCellRenderer;

import static dev.tirador.aoe2radar.util.Formato.pct1;

public class PctRenderer extends DefaultTableCellRenderer {
    public PctRenderer() { setHorizontalAlignment(RIGHT); }
    @Override protected void setValue(Object v) { setText(v instanceof Double d ? pct1(d) : ""); }
}
