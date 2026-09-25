package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.service.MiPartidaService;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.List;

import static dev.tirador.aoe2radar.ui.Iconos.iconoBandera;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * «Mi partida»: el panel que aparece SOBRE el juego al detectar que empieza una partida (antes de que el
 * socket del companion avise) y el flujo de «quién eres» (buscar tu nick, fijarlo, abrir tu perfil). Sale de
 * SpoilerFreeRecs (iniciarVigilanciaLogJuego, mostrarSuperposicion, abrirMiPerfil, preguntarMiNick de la 1.1)
 * tal cual: mismos textos, mismo Timer de 2s (javax.swing.Timer, del EDT), misma superposición (JWindow sin
 * bordes, siempre visible, se cierra sola).
 * <p>Nada de red aquí: eso lo hace {@link MiPartidaPresenter} con {@link MiPartidaService}. Este panel solo
 * pinta: el Timer, la superposición y los diálogos de «mi nick».
 */
public final class MiPartidaPanel implements MiPartidaPresenter.Pantalla {

    /** Lo que el panel necesita de la ventana y no es navegación (Navegacion ya cubre abrir el perfil). */
    public interface Anfitrion {
        /** Busca perfiles por nick (el mismo buscador de siempre, sugerirPerfiles/buscarPerfiles); va a la red. */
        List<String[]> buscarPerfiles(String nick);
        /** Aviso corto en la barra de estado de la ventana. */
        void mostrarEstado(String texto);
        /** Tras fijar «quién soy»: refresca qué jugadores vigila el socket de vivos con la nueva identidad. */
        void sincronizarSocket();
    }

    private final MiPartidaPresenter presenter;
    private final Component ventana;

    // javax.swing.Timer del EDT, tal cual logJuegoTimer/superposicion/superposicionTimer de la 1.1.
    javax.swing.Timer logJuegoTimer;
    JWindow superposicion;
    javax.swing.Timer superposicionTimer;

    public MiPartidaPanel(MiPartidaService servicio, Tareas tareas, Navegacion navegacion, Component ventana, Anfitrion anfitrion) {
        this.ventana = ventana;
        this.presenter = new MiPartidaPresenter(servicio, tareas, navegacion, anfitrion, this);
    }

    /** Vigila el MainLog.txt de la sesión más reciente del juego: al ver la fase de preparación, aviso temprano
     *  y sondeo del lobby. Idempotente (si ya está vigilando, no hace nada), como en la 1.1. */
    @Override
    public void iniciarVigilancia() {
        if (logJuegoTimer != null) return;
        presenter.alIniciarVigilancia();
        logJuegoTimer = new javax.swing.Timer(2000, e -> presenter.tick());
        logJuegoTimer.start();
    }

    /** «Mi perfil»: el pid guardado en config; si no, se pide el nick. */
    public void abrirMiPerfil() { presenter.abrirMiPerfil(); }

    /** Pide el nick por diálogo y lanza la búsqueda (hilo "mi-perfil", en el presentador). */
    public void preguntarMiNick() {
        String q = JOptionPane.showInputDialog(ventana, t("Tu nick en el juego:", "Your in-game nick:"),
                t("Mi perfil", "My profile"), JOptionPane.PLAIN_MESSAGE);
        presenter.buscarMiNick(q);
    }

    @Override
    public void pedirNick() { preguntarMiNick(); }

    @Override
    public String[] elegirNick(List<String[]> resultados) {
        Object el = JOptionPane.showInputDialog(ventana, t("¿Cuál eres tú?", "Which one is you?"),
                t("Mi perfil", "My profile"), JOptionPane.PLAIN_MESSAGE, null,
                resultados.stream().map(r -> r[2]).toArray(), resultados.get(0)[2]);
        if (el == null) return null;
        for (String[] r : resultados) if (r[2].equals(el)) return r;
        return null;
    }

    /** Panel sobre el juego (siempre visible, sin bordes, arriba en el centro): el aviso y, si hay, las fichas
     *  de los rivales/compañeros. Se cierra sola a los ms indicados. */
    @Override
    public void mostrarSuperposicion(String texto, List<Object[]> fichas, int ms) {
        if (superposicion != null) { superposicion.dispose(); superposicion = null; }
        if (superposicionTimer != null) superposicionTimer.stop();
        JWindow w = new JWindow();
        w.setAlwaysOnTop(true);
        JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(0x1e, 0x1e, 0x1e, 235));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xff, 0xd5, 0x6a), 1), BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        JLabel l = new JLabel(texto); l.setForeground(Color.WHITE); l.setFont(l.getFont().deriveFont(Font.BOLD, 14f)); l.setAlignmentX(0f); p.add(l);
        if (fichas != null) for (Object[] f : fichas) {
            long pid = (Long) f[0]; String nombre = (String) f[1]; Integer e1 = (Integer) f[2];
            StringBuilder d = new StringBuilder(nombre + (e1 != null ? "  ·  ELO " + e1 : ""));
            List<String> top = presenter.civsRecientes(pid);
            if (!top.isEmpty()) { d.append("  ·  "); for (int i = 0; i < top.size(); i++) d.append(i > 0 ? ", " : "").append(top.get(i)); }
            JLabel lf = new JLabel(d.toString(), iconoBandera(presenter.paisDe(pid)), SwingConstants.LEFT);
            lf.setForeground(new Color(0xdd, 0xdd, 0xdd)); lf.setIconTextGap(6); lf.setAlignmentX(0f); lf.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            p.add(lf);
        }
        w.setContentPane(p); w.pack();
        Rectangle pantalla = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        w.setLocation(pantalla.x + (pantalla.width - w.getWidth()) / 2, pantalla.y + 24);
        w.setVisible(true);
        superposicion = w;
        superposicionTimer = new javax.swing.Timer(ms, e -> { if (superposicion == w) { w.dispose(); superposicion = null; } });
        superposicionTimer.setRepeats(false); superposicionTimer.start();
    }
}
