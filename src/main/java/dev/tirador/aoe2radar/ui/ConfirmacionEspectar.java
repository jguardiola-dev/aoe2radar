package dev.tirador.aoe2radar.ui;

import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import java.awt.Component;

import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * El diálogo «¿Espectar la partida de X?» con su «No volver a preguntar», antes de lanzar el juego desde un
 * doble clic (fase 3, tanda 4, Z4): antes vivía en SpoilerFreeRecs.confirmarEspectar. Sin red: solo lee/
 * escribe la preferencia en config.
 */
public final class ConfirmacionEspectar {
    private ConfirmacionEspectar() {}

    /** true si se puede espectar (el usuario aceptó, o ya había marcado «no preguntar más»). */
    public static boolean confirmar(Component padre, String quien) {
        if ("1".equals(leerConfig("espectar_sin_preguntar", "0"))) return true;
        JCheckBox noMas = new JCheckBox(t("No volver a preguntar", "Don't ask again"));
        int r = JOptionPane.showConfirmDialog(padre, new Object[]{
                t("¿Espectar la partida de ", "Spectate ") + quien + t(" en el juego?\nSe abrirá Age of Empires II.", "'s game in-game?\nAge of Empires II will open."),
                noMas }, t("Espectar", "Spectate"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return false;
        if (noMas.isSelected()) guardarConfig("espectar_sin_preguntar", "1");
        return true;
    }
}
