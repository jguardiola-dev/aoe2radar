package dev.tirador.aoe2radar.ui;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.AbstractBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * La franja fina de arriba de la ventana con el mensaje del mando a distancia (control.json, campo «mensaje»).
 * Decisión de Jorge para la 1.3: no intrusiva (sin ventana flotante, sin sonido, sin robar el foco), con borde
 * e icono ⓘ granates, el texto tal cual viene y una × a la derecha. Se queda hasta que el usuario pulsa la ×, y
 * solo entonces se marca como visto (lo que haga {@code alCerrar}); con la ventana minimizada se muestra igual y
 * la verá al volver. Un mensaje distinto sustituye al anterior (que no se marca: no se llegó a cerrar).
 * <p>
 * Nace oculta: sin mensaje no ocupa sitio (BorderLayout no reserva hueco para un hijo invisible). Todo en el EDT.
 */
public final class FranjaAviso extends JPanel {

    /** Líneas y caracteres por línea que caben en la franja; lo que sobra se corta con «…» y va entero al tooltip. */
    static final int MAX_LINEAS = 2, MAX_CARACTERES = 160;

    /** Paquete, no privados: FranjaAvisoTest los lee y pulsa la × sin pantalla. */
    final JLabel texto = new JLabel();
    final JButton cerrarBtn = new JButton("×");

    /** El mensaje a la vista (null si no hay), lo que marca que se ha visto y el último que el usuario cerró (para
     *  que la recarga de cada hora no lo resucite si llega antes de que se guarde control_msg_visto). */
    private String textoActual, ultimoCerrado;
    private Runnable alCerrarActual;

    public FranjaAviso() {
        super(new BorderLayout(8, 0));
        setBorder(BorderFactory.createCompoundBorder(new BordeGranate(), BorderFactory.createEmptyBorder(3, 8, 3, 4)));
        texto.setIcon(new IconoInfo());
        texto.setIconTextGap(8);
        add(texto, BorderLayout.CENTER);
        cerrarBtn.setFocusable(false);   // no roba el foco: ni al aparecer ni al cerrarla
        cerrarBtn.setMargin(new Insets(0, 6, 0, 6));
        cerrarBtn.setToolTipText(t("Cerrar el aviso: no volverá a salir", "Close the notice: it won't show again"));
        cerrarBtn.addActionListener(e -> cerrar());
        add(cerrarBtn, BorderLayout.EAST);
        setVisible(false);
    }

    /** Enseña el mensaje (EDT). El mismo que ya está a la vista o que el usuario acaba de cerrar no hace nada; otro
     *  distinto sustituye al anterior, cuyo alCerrar se descarta sin correr. alCerrar corre al pulsar la ×. */
    public void mostrar(String mensaje, Runnable alCerrar) {
        if (mensaje == null || mensaje.isBlank() || mensaje.equals(textoActual) || mensaje.equals(ultimoCerrado)) return;
        textoActual = mensaje;
        alCerrarActual = alCerrar;
        texto.setText(textoFranja(mensaje));
        texto.setToolTipText(tooltip(mensaje));
        setVisible(true);
        revalidar();
    }

    /** La × (EDT): oculta la franja y, solo ahora, marca el mensaje como visto (una vez). */
    void cerrar() {
        if (textoActual == null) return;
        Runnable marcar = alCerrarActual;
        ultimoCerrado = textoActual;
        textoActual = null;
        alCerrarActual = null;
        setVisible(false);
        revalidar();
        if (marcar != null) marcar.run();
    }

    /** El mensaje a la vista, o null. */
    public String textoActual() { return textoActual; }

    private void revalidar() {
        if (getParent() != null) { getParent().revalidate(); getParent().repaint(); }
    }

    /** Lo que se pinta: como mucho MAX_LINEAS líneas de MAX_CARACTERES; si sobra algo, «…». Una línea va como texto
     *  plano (el JLabel la recorta solo si no cabe); dos, como HTML escapado. */
    static String textoFranja(String mensaje) {
        String[] lineas = mensaje.strip().split("\\R");
        int n = Math.min(lineas.length, MAX_LINEAS);
        String[] ver = new String[n];
        for (int i = 0; i < n; i++) {
            String l = lineas[i].strip();
            ver[i] = l.length() > MAX_CARACTERES ? l.substring(0, MAX_CARACTERES - 1).stripTrailing() + "…" : l;
        }
        if (lineas.length > MAX_LINEAS && !ver[n - 1].endsWith("…")) ver[n - 1] += " …";
        if (n == 1) return ver[0];
        StringBuilder sb = new StringBuilder("<html>");
        for (int i = 0; i < n; i++) sb.append(i > 0 ? "<br>" : "").append(escapar(ver[i]));
        return sb.append("</html>").toString();
    }

    /** El mensaje entero, para el tooltip (con sus saltos de línea). */
    static String tooltip(String mensaje) {
        String[] lineas = mensaje.strip().split("\\R");
        if (lineas.length == 1) return lineas[0];
        StringBuilder sb = new StringBuilder("<html>");
        for (int i = 0; i < lineas.length; i++) sb.append(i > 0 ? "<br>" : "").append(escapar(lineas[i]));
        return sb.append("</html>").toString();
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Borde fino granate con una barra algo más gruesa a la izquierda; el color se lee al pintar (Tema.granate()),
     *  así sigue al cambio de tema sin que nadie tenga que avisarle. */
    private static final class BordeGranate extends AbstractBorder {
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            g.setColor(Tema.granate());
            g.drawRect(x, y, w - 1, h - 1);
            g.fillRect(x, y, 4, h);
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(1, 4, 1, 1); }
        @Override public Insets getBorderInsets(Component c, Insets i) { i.set(1, 4, 1, 1); return i; }
    }

    /** La ⓘ dibujada (círculo con una «i»), en granate: no depende de que la fuente tenga el carácter U+24D8. */
    private static final class IconoInfo implements Icon {
        private static final int LADO = 16;
        @Override public int getIconWidth() { return LADO; }
        @Override public int getIconHeight() { return LADO; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(Tema.granate());
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(x + 1, y + 1, LADO - 3, LADO - 3);
                g2.setFont(c.getFont().deriveFont(Font.BOLD, 11f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("i", x + (LADO - fm.stringWidth("i")) / 2f, y + (LADO + fm.getAscent() - fm.getDescent()) / 2f - 0.5f);
            } finally {
                g2.dispose();
            }
        }
    }
}
