package dev.tirador.aoe2radar.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Insets;

import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * La franja de las actualizaciones (1.4), hermana de {@link FranjaAviso} y con su mismo estilo (borde e icono
 * granates, sin robar el foco, sin diálogos): una línea de texto, un botón de acción opcional («Reiniciar ahora»,
 * «Descargar instalador», «Actualizar») y la ×. Solo pinta: qué decir y qué hace el botón lo decide
 * {@link Actualizaciones}. La × la oculta hasta el siguiente aviso DISTINTO (la comprobación de cada 12 h no
 * resucita el mismo texto en esta sesión).
 * <p>Nace oculta: sin nada que decir no ocupa sitio. Todo en el EDT.
 */
public final class FranjaActualizacion extends JPanel {

    /** Paquete, no privados: ActualizacionesTest los lee y los pulsa sin pantalla. */
    final JLabel texto = new JLabel();
    final JButton accionBtn = new JButton();
    final JButton cerrarBtn = new JButton("×");

    private String textoActual, ultimoCerrado;
    private Runnable accion;

    public FranjaActualizacion() {
        super(new BorderLayout(8, 0));
        setBorder(BorderFactory.createCompoundBorder(new BordeGranate(), BorderFactory.createEmptyBorder(3, 8, 3, 4)));
        texto.setIcon(new IconoInfo());
        texto.setIconTextGap(8);
        add(texto, BorderLayout.CENTER);
        accionBtn.setFocusable(false);
        accionBtn.addActionListener(e -> { if (accion != null) accion.run(); });
        cerrarBtn.setFocusable(false);
        cerrarBtn.setMargin(new Insets(0, 6, 0, 6));
        cerrarBtn.setToolTipText(t("Ocultar el aviso", "Hide the notice"));
        cerrarBtn.addActionListener(e -> cerrar());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        botones.setOpaque(false);
        botones.add(accionBtn);
        botones.add(cerrarBtn);
        add(botones, BorderLayout.EAST);
        setVisible(false);
    }

    /** Enseña el aviso (EDT). boton: el texto del botón de acción, o null para no enseñarlo. El mismo texto que el
     *  usuario acaba de cerrar con la × no vuelve a salir; uno distinto sustituye al que hubiera. */
    public void mostrar(String mensaje, String boton, Runnable alPulsar) {
        if (mensaje == null || mensaje.isBlank() || mensaje.equals(ultimoCerrado)) return;
        textoActual = mensaje;
        accion = alPulsar;
        texto.setText(mensaje);
        texto.setToolTipText(null);
        accionBtn.setVisible(boton != null);
        accionBtn.setText(boton == null ? "" : boton);
        accionBtn.setEnabled(true);
        setVisible(true);
        revalidar();
    }

    /** El botón en marcha (p. ej., «Descargando…»): deshabilitado con otro texto, hasta el siguiente mostrar. */
    public void ocupado(String textoBoton) {
        accionBtn.setText(textoBoton);
        accionBtn.setEnabled(false);
    }

    /** La ×: oculta la franja; ese mismo texto no vuelve en esta sesión. */
    void cerrar() {
        if (textoActual == null) return;
        ultimoCerrado = textoActual;
        textoActual = null;
        accion = null;
        setVisible(false);
        revalidar();
    }

    /** El aviso a la vista, o null. */
    public String textoActual() { return textoActual; }

    private void revalidar() {
        if (getParent() != null) { getParent().revalidate(); getParent().repaint(); }
    }
}
