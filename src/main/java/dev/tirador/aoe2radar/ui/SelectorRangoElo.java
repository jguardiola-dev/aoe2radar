package dev.tirador.aoe2radar.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * Selector de ELO: presets (Todos, &lt;800, 800–1000, …, 2000+) y «Personalizado…», que pregunta
 * el rango «de … a …» en un diálogo y lo deja como opción activa. El valor es «*», un tramo o
 * «desde|hasta». Lo comparten Civ Stats y la banda del Tech tree; recibe por constructor la
 * ventana que hace de padre del diálogo y el nombre legible de un tramo (hoy en SpoilerFreeRecs)
 * para no depender de la clase de la ventana.
 */
public class SelectorRangoElo extends JPanel {
    private final Window padre;
    private final Function<String, String> tramoNombre;
    final JComboBox<String> preset = new JComboBox<>();
    List<String> tramos = List.of(); String personalizado;   // rango «desde|hasta» activo, si lo hay
    boolean rellenando; public Consumer<String> alCambiar;

    public SelectorRangoElo(Window padre, Function<String, String> tramoNombre) {
        super(new FlowLayout(FlowLayout.LEFT, 3, 0)); setOpaque(false);
        this.padre = padre;
        this.tramoNombre = tramoNombre;
        add(preset);
        preset.setToolTipText(t("Tramo de ELO (media de ELO de la partida). «Personalizado…» pide un rango de tramo a tramo, con «sin límite» en cualquier extremo", "ELO bracket (match ELO average). “Custom…” asks for a range from bracket to bracket, with “no limit” at either end"));
        preset.addActionListener(e -> {
            if (rellenando) return;
            int i = preset.getSelectedIndex();
            if (i == preset.getItemCount() - 1) {   // «Personalizado…»: preguntar
                String r = pedirRango();
                if (r == null) { rango(personalizado != null ? personalizado : "*"); return; }
                personalizado = r; rellenar(); rango(r);
            }
            if (alCambiar != null) alCambiar.accept(rango());
        });
    }
    public void tramos(List<String> t, String rangoActual) {
        tramos = new ArrayList<>(t);
        if (rangoActual != null && rangoActual.contains("|") && !"*|*".equals(rangoActual)) personalizado = rangoActual;
        rellenar();
        rango(rangoActual);
    }
    void rellenar() {
        boolean antes = rellenando; rellenando = true;
        try {
            preset.removeAllItems();
            preset.addItem(t("Todos los ELO", "All ELO"));
            for (String tr : tramos) preset.addItem(tramoNombre.apply(tr));
            if (personalizado != null) preset.addItem(tramoNombre.apply(personalizado) + t(" (personalizado)", " (custom)"));
            preset.addItem(t("Personalizado…", "Custom…"));
        } finally { rellenando = antes; }
    }
    String pedirRango() {
        JComboBox<String> desde = new JComboBox<>(), hasta = new JComboBox<>();
        desde.addItem(t("sin límite", "no limit")); hasta.addItem(t("sin límite", "no limit"));
        for (String tr : tramos) { String lo = tr.contains("-") ? tr.substring(0, tr.indexOf('-')) : tr.replace("+", ""), hi = tr.endsWith("+") ? tr : tr.contains("-") ? tr.substring(tr.indexOf('-') + 1) : tr; desde.addItem(lo.equals("0") ? "<" + hi : lo); hasta.addItem(hi); }
        if (personalizado != null) { String[] p = personalizado.split("\\|", -1); desde.setSelectedIndex("*".equals(p[0]) ? 0 : tramos.indexOf(p[0]) + 1); hasta.setSelectedIndex("*".equals(p[1]) ? 0 : tramos.indexOf(p[1]) + 1); }
        JPanel pnl = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        pnl.add(new JLabel(t("ELO de", "ELO from"))); pnl.add(desde); pnl.add(new JLabel(t("a", "to"))); pnl.add(hasta);
        int r = JOptionPane.showConfirmDialog(padre, pnl, t("Rango de ELO personalizado", "Custom ELO range"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return null;
        int a = desde.getSelectedIndex() - 1, b = hasta.getSelectedIndex() - 1;
        if (a >= 0 && b >= 0 && a > b) { int x = a; a = b; b = x; }
        String lo = a < 0 ? "*" : tramos.get(a), hi = b < 0 ? "*" : tramos.get(b);
        return "*".equals(lo) && "*".equals(hi) ? "*" : lo.equals(hi) ? lo : lo + "|" + hi;
    }
    public String rango() {
        int i = preset.getSelectedIndex(), n = preset.getItemCount();
        if (i <= 0) return "*";
        if (i <= tramos.size()) return tramos.get(i - 1);
        if (personalizado != null && i == n - 2) return personalizado;
        return personalizado != null ? personalizado : "*";
    }
    public void rango(String r) {
        boolean antes = rellenando; rellenando = true;
        try {
            if (r == null || "*".equals(r) || "*|*".equals(r)) { preset.setSelectedIndex(0); return; }
            if (!r.contains("|")) { int i = tramos.indexOf(r); preset.setSelectedIndex(i >= 0 ? i + 1 : 0); return; }
            if (!r.equals(personalizado)) { personalizado = r; rellenar(); }
            preset.setSelectedIndex(preset.getItemCount() - 2);
        } finally { rellenando = antes; }
    }
}
