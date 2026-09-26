package dev.tirador.aoe2radar.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.RootPaneContainer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Identidad.AUTOR;
import static dev.tirador.aoe2radar.util.Identidad.TWITCH;
import static dev.tirador.aoe2radar.util.Identidad.RELEASES_URL;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Barra de estado y semáforo de la operación en curso: el mensaje de la fila inferior ({@link #status}), la
 * barra de progreso, los botones «Detener»/«Continuar buscando» (con el vigilante de 5 s que evita quedarse
 * pegado en «progreso» si la cancelación no llega a tiempo), la firma del autor con el botón de café/donación,
 * y el toast flotante (avisos de «tu partida»/campanas). Vive en {@code ui} porque es cromo puro de la ventana,
 * no de una vista concreta: todas las vistas (Partidas, Watchlist, Azar…) comparten este único semáforo.
 * <p>
 * No conoce la red ni el freno de cancelación real (viven en {@code api.Cancelacion}/{@code api.Http}, y
 * {@code ui} no puede importar {@code api}): lo que necesita de fuera se lo pide a su {@link Anfitrion}.
 */
public final class BarraEstado {

    /** Lo que BarraEstado necesita del resto de la ventana: el freno global de cancelación (api.Cancelacion),
     *  la red (api.Http, java.net) y las otras vistas (Partidas). Implementado por la ventana con lambdas. */
    public interface Anfitrion {
        /** Al empezar una operación: limpia el freno de cancelación (api.Cancelacion.stopOperacion/hiloOperacion). */
        void iniciarOperacion();
        /** api.Cancelacion.opEnCurso: lo usa dormir() (pausa cooperativa) en medio proyecto. */
        void marcarOperacionEnCurso(boolean on);
        /** Al terminar, cada vista reactiva sus propios botones (fetchBtn/azarBtn/gteBtn/dlSel/dlAll: ui.PartidasView). */
        void operacionTerminada();
        /** Botón «Detener»: pide parar la operación en curso (api.Cancelacion.stopOperacion = true). */
        void pararOperacion();
        /** Las peticiones en vuelo mueren solas (≤15 s); las siguientes salen con un cliente nuevo (api.Http). */
        void renovarHttp();
        /** Botón «Continuar buscando»: reanuda el azar con los mismos filtros (ui.PartidasView.buscarAleatorias). */
        void continuarBuscando();
        void abrirTwitch();
        void abrirDonacion();
        void abrirUrl(String url);
        void espectarPartida(long matchId);
    }

    private final RootPaneContainer ventana;
    private final Anfitrion anfitrion;
    private final String donarUrl;

    /** visible para SpoilerFreeRecs (alias de campo: decenas de sitios de otras zonas llaman status.setText(...)) */
    public final JLabel status = new JLabel(t("Listo.", "Ready.")) {
        @Override public void setText(String texto) {   // si no cabe, el tooltip lo enseña entero
            super.setText(texto);
            setToolTipText(texto == null || texto.isBlank() ? null : texto);
        }
    };
    /** visible para SpoilerFreeRecs (alias de campo) */
    public final JProgressBar progreso = new JProgressBar();
    /** visible para SpoilerFreeRecs (alias de campo) */
    public final JButton cafeBtn;

    /** Construidos en {@link #construirFila()}, como en la 1.1 (no en el constructor): visible para SpoilerFreeRecs. */
    public JButton detenerDescBtn, continuarBtn, actualizarBtn;
    public JLabel firma;

    private JPanel toast;
    /** visible para RegresionCapturas */
    public javax.swing.Timer toastTimer;
    /** El watchdog del botón «Detener»: campo (no variable local, como en la 1.1) solo para que los tests puedan
     *  simularlo sin esperar los 5 s reales (disparando su ActionListener a mano); el comportamiento en
     *  producción es idéntico. Cada clic en «Detener» SUSTITUYE esta referencia por un Timer nuevo, pero eso no
     *  cambia nada: el Timer anterior (si lo hubiera) no se cancela, sigue vivo en la cola de Swing con su
     *  propio {@code serialDetenido} capturado por el lambda, y comparará ese número cuando le toque disparar,
     *  sea cual sea el valor de este campo en ese momento. Además, en la práctica nunca hay dos a la vez:
     *  {@code detenerDescBtn} queda deshabilitado justo tras el primer clic y no vuelve a habilitarse hasta que
     *  {@link #trabajando(boolean)} termina la operación, así que no puede haber un segundo clic (y por tanto un
     *  segundo watchdog) mientras el primero sigue pendiente. */
    javax.swing.Timer watchdogDetener;

    /** cada operación tiene su número: el watchdog del Detener solo cierra la suya */
    private long opSerial;

    /** Cuenta atrás del aviso de pausa por 429 (decisión de Jorge, DEUDA 45): un único Timer, reutilizado
     *  (restart(), nunca «new») aunque lleguen varios avisos seguidos. Paquete, no privado: BarraEstadoTest
     *  lo para al cerrar (como watchdogDetener/toastTimer) y comprueba que sigue siendo el mismo objeto. */
    javax.swing.Timer timerPausaApi;
    private long segRestantesPausaApi;
    /** El último texto de cuenta atrás que se pintó: al llegar a 0 solo se limpia `status` si sigue mostrando
     *  ESTE texto exacto (si alguien pintó otra cosa encima mientras tanto, no se toca). */
    private String ultimoTextoPausaApi;

    public BarraEstado(RootPaneContainer ventana, Anfitrion anfitrion, String donarUrl) {
        this.ventana = ventana;
        this.anfitrion = anfitrion;
        this.donarUrl = donarUrl;
        cafeBtn = new JButton("\u2615 " + t("Invítame a un café", "Buy me a coffee"));
        // El aviso de pausa por 429 (app.Servicios.avisarPausa429) llega por Servicios.avisoPausa429, fijado en
        // el EDT al construir la ventana (CableadoCromo.configurarVentana): ya no hace falta que esta barra se
        // "encuentre a sí misma" con un putClientProperty en `status` (limpieza 1, fase 4).
    }

    public long opSerial() { return opSerial; }

    /** El toast actual (o null si no hay ninguno mostrado): lo usa la Watchlist para añadirle botones extra
     *  («Su perfil»/«Cara a cara») antes de que se auto-oculte. */
    public JPanel toast() { return toast; }

    /** Muestra u oculta la barra de progreso y el semáforo de la operación en curso. */
    public void trabajando(boolean on) {
        progreso.setVisible(on);
        if (on) anfitrion.iniciarOperacion();   // antes que opEnCurso: operación nueva = freno suelto (la anterior, si aún muere, ya no frena a esta), y hasta que anote su hilo Detener no alcanza a nadie
        anfitrion.marcarOperacionEnCurso(on);
        if (on) opSerial++;
        if (!on) anfitrion.operacionTerminada();   // cualquier fin de operación deja la UI usable, pase por donde pase
        if (on) {
            if (continuarBtn != null) continuarBtn.setVisible(false);
        }
        if (detenerDescBtn != null) {
            detenerDescBtn.setVisible(on);
            if (!on) detenerDescBtn.setEnabled(true);
        }
    }

    /** La franja de estado: progreso/detener/continuar a la izquierda, el mensaje en el centro, firma/café/
     *  actualizar a la derecha. Layout idéntico al que construye CableadoCromo.construirBarraInferior (antes,
     *  SpoilerFreeRecs.construirBarraInferior). */
    public JPanel construirFila() {
        JLabel f = new JLabel("<html>" + AUTOR + " · <u>" + TWITCH + "</u></html>");
        f.setFont(f.getFont().deriveFont(Font.PLAIN, 11f));
        f.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        f.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        f.setToolTipText(t("Abrir https://", "Open https://") + TWITCH);
        f.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { anfitrion.abrirTwitch(); }
        });
        firma = f;
        cafeBtn.setToolTipText(donarUrl);
        cafeBtn.addActionListener(e -> anfitrion.abrirDonacion());
        JPanel este = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actualizarBtn = new JButton();
        actualizarBtn.setVisible(false);
        actualizarBtn.setFocusable(false);
        actualizarBtn.setToolTipText(t("Abre la página de descarga de la versión nueva", "Opens the new version's download page"));
        actualizarBtn.addActionListener(e -> anfitrion.abrirUrl(RELEASES_URL));
        este.add(actualizarBtn); este.add(cafeBtn); este.add(firma);

        progreso.setIndeterminate(true);
        progreso.setVisible(false);
        progreso.setPreferredSize(new Dimension(120, 14));
        JPanel oeste = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        oeste.add(progreso);
        detenerDescBtn = new JButton(t("Detener", "Stop"));
        detenerDescBtn.setVisible(false);
        detenerDescBtn.setToolTipText(t("Detiene la operación en curso: descargas, azar o Guess the ELO (cada petición muere sola a los 15 s).",
                "Stops the running operation: downloads, random or Guess the ELO (each request self-terminates at 15 s)."));
        detenerDescBtn.addActionListener(e -> {
            log("detener pulsado (op #" + opSerial + ")");
            anfitrion.pararOperacion();
            detenerDescBtn.setEnabled(false);
            status.setText(t("Deteniendo… (como mucho 15 s si había una petición en vuelo)",
                    "Stopping… (at most 15 s if a request was in flight)"));
            anfitrion.renovarHttp();   // las peticiones en vuelo caducan solas (≤15 s); las siguientes salen limpias
            final long serialDetenido = opSerial;
            watchdogDetener = new javax.swing.Timer(5000, ev -> {
                if (progreso.isVisible() && opSerial == serialDetenido) {   // solo si es LA MISMA operación
                    trabajando(false);
                    status.setText(t("Detenido.", "Stopped."));
                }
            });
            watchdogDetener.setRepeats(false);
            watchdogDetener.start();
        });
        oeste.add(detenerDescBtn);
        continuarBtn = new JButton(t("Continuar buscando", "Keep searching"));
        continuarBtn.setVisible(false);
        continuarBtn.setToolTipText(t("Reanuda el azar con los mismos filtros, sin re-diálogo: el muestreo recuerda lo ya leído.",
                "Resumes the random search with the same filters, no dialog: sampling remembers what it already read."));
        continuarBtn.addActionListener(e -> anfitrion.continuarBuscando());
        oeste.add(continuarBtn);
        JPanel filaEstado = new JPanel(new BorderLayout());
        filaEstado.add(oeste, BorderLayout.WEST);
        filaEstado.add(status, BorderLayout.CENTER);
        filaEstado.add(este, BorderLayout.EAST);
        return filaEstado;
    }

    /** El aviso flotante («X ha empezado una partida», campanas): un JPanel en la capa emergente de la
     *  ventana, con «Espectar» (si hay matchId) y «×», que se auto-oculta a los 10 s. */
    public void mostrarToast(String texto, long matchId) {
        JLayeredPane capa = ventana.getLayeredPane();
        if (toast != null) capa.remove(toast);
        toast = new JPanel(new BorderLayout(8, 0));
        toast.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(temaOscuroActivo ? new Color(0xff, 0xd5, 0x6a) : new Color(0xb0, 0x6a, 0x00), 1, true), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        JLabel l = new JLabel(texto);
        toast.add(l, BorderLayout.CENTER);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); botones.setOpaque(false);
        if (matchId > 0) { JButton esp = new JButton(t("Espectar", "Spectate")); esp.setFocusable(false); esp.setMargin(new Insets(1, 8, 1, 8)); esp.addActionListener(e -> { anfitrion.espectarPartida(matchId); ocultarToast(); }); botones.add(esp); }
        JButton x = new JButton("\u00D7"); x.setFocusable(false); x.setMargin(new Insets(1, 6, 1, 6)); x.addActionListener(e -> ocultarToast()); botones.add(x);
        toast.add(botones, BorderLayout.EAST);
        Dimension d = toast.getPreferredSize();
        toast.setBounds(ventana.getRootPane().getWidth() - d.width - 24, ventana.getRootPane().getHeight() - d.height - 56, d.width, d.height);
        capa.add(toast, JLayeredPane.POPUP_LAYER);
        capa.repaint();
        if (toastTimer != null) toastTimer.stop();
        toastTimer = new javax.swing.Timer(10_000, e -> ocultarToast()); toastTimer.setRepeats(false); toastTimer.start();
    }

    public void ocultarToast() { if (toast != null) { ventana.getLayeredPane().remove(toast); ventana.getLayeredPane().repaint(); toast = null; } }

    // ======================================================================
    // Aviso de pausa por 429 (decisión de Jorge, DEUDA 45): "Buscar" y Twitch se quedaban en "Consultando..."
    // hasta 5 min sin explicar por qué. app.Servicios.avisarPausa429 llama aquí, ya en el EDT, una vez por
    // episodio de pausa.
    // ======================================================================

    /** Pinta la cuenta atrás YA (para ganarle a un publish/process de "Consultando..." que llegue en el mismo
     *  instante) y arranca (o reinicia, si ya había una en curso) un único Timer de 1 s que la reescribe cada
     *  segundo. Un segundo aviso mientras la cuenta sigue corriendo la reinicia con el nuevo valor de seg: el
     *  Timer se reutiliza (restart()), nunca se crea uno nuevo. seg &lt;= 0 no hace nada (no hay pausa que
     *  contar). */
    public void mostrarPausaApi(long seg) {
        if (seg <= 0) return;
        segRestantesPausaApi = seg;
        ultimoTextoPausaApi = textoPausaApi(seg);
        status.setText(ultimoTextoPausaApi);
        if (timerPausaApi == null) {
            timerPausaApi = new javax.swing.Timer(1000, e -> tickPausa());
            timerPausaApi.setRepeats(true);
            timerPausaApi.start();
        } else {
            timerPausaApi.restart();
        }
    }

    /** Un segundo de la cuenta atrás: al llegar a 0 para el Timer y, SOLO si `status` sigue mostrando su
     *  propio texto de cuenta atrás (nadie ha pintado otra cosa encima), lo limpia (misma regla de siempre, sin
     *  cambios). En los ticks intermedios, solo repinta si `status` sigue siendo su propio texto o un
     *  «Consultando…»/«Checking…» (ver {@link #puedeRepintarPausa}): si otro mensaje lo tapó, se respeta y se
     *  deja de repintar (el Timer sigue contando solo; si más adelante vuelve a verse un «Consultando…», el
     *  siguiente tick lo tapa de nuevo). Paquete, no privado: lo dispara BarraEstadoTest a mano, sin esperar el
     *  segundo real. */
    void tickPausa() {
        segRestantesPausaApi--;
        if (segRestantesPausaApi <= 0) {
            timerPausaApi.stop();
            if (ultimoTextoPausaApi.equals(status.getText())) status.setText("");
            return;
        }
        if (puedeRepintarPausa()) {
            ultimoTextoPausaApi = textoPausaApi(segRestantesPausaApi);
            status.setText(ultimoTextoPausaApi);
        }
    }

    /** ¿Puede este tick reescribir `status`? Solo si sigue mostrando el texto que pintamos la última vez
     *  (nadie ha tocado nada) o un «Consultando…»/«Checking…» (PartidasView.download/fetchMatches,
     *  LiveNowPresenter): esos son justo los mensajes que la cuenta atrás debe tapar. Cualquier otro mensaje
     *  (una vista contando lo que hizo, un aviso distinto) se respeta sin tocarlo. */
    private boolean puedeRepintarPausa() {
        String actual = status.getText();
        return actual != null && (actual.equals(ultimoTextoPausaApi) || actual.startsWith(prefijoConsultando()));
    }

    private static String prefijoConsultando() { return t("Consultando", "Checking"); }

    private static String textoPausaApi(long seg) {
        return t("Esperando a la API (", "Waiting for the API (") + seg
                + t(" s): pide calma y la app sigue sola.", " s): it asks for calm and the app carries on by itself.");
    }
}
