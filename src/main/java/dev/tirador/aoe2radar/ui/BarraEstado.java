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

import dev.tirador.aoe2radar.util.Operaciones;

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
 * No conoce la red ({@code api.Http}; {@code ui} no puede importar {@code api}): lo que necesita de fuera se lo
 * pide a su {@link Anfitrion}. Los frenos de las operaciones (uno por operación, desde la 1.3) son un
 * {@link Operaciones}: la barra decide con él a quién para «Detener» (la más reciente viva) y cuándo se oculta.
 */
public final class BarraEstado {

    /** Lo que BarraEstado necesita del resto de la ventana: la red (api.Http, java.net) y las otras vistas
     *  (Partidas). Implementado por la ventana con lambdas. Los frenos de las operaciones ya no pasan por aquí:
     *  son de util.Operaciones, que esta barra usa directamente (decisión de Jorge, 1.3: un freno por operación). */
    public interface Anfitrion {
        /** Al terminar la ÚLTIMA operación viva, cada vista reactiva sus propios botones (fetchBtn/azarBtn/gteBtn/
         *  dlSel/dlAll: ui.PartidasView). */
        void operacionTerminada();
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
    /** Los frenos de las operaciones vivas (uno por operación). La barra los toca solo en el EDT. */
    private final Operaciones operaciones;

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
     *  propio número de operación detenida capturado por el lambda, y solo actúa sobre ESA operación. Tras un
     *  clic, {@code detenerDescBtn} queda deshabilitado mientras esa operación siga viva; si otra más reciente
     *  empieza, o si esa termina y queda otra anterior viva, se vuelve a habilitar para parar a la nueva
     *  destinataria: cada watchdog vigila solo la suya. */
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
        this(ventana, anfitrion, donarUrl, Operaciones.GLOBAL);
    }

    /** Con sus propios frenos: solo para tests (sin tocar Operaciones.GLOBAL). Ojo: el freno de la red
     *  (api.Cancelacion.detieneEsteHilo) lee siempre GLOBAL; la app usa el constructor de tres argumentos. */
    public BarraEstado(RootPaneContainer ventana, Anfitrion anfitrion, String donarUrl, Operaciones operaciones) {
        this.ventana = ventana;
        this.anfitrion = anfitrion;
        this.donarUrl = donarUrl;
        this.operaciones = operaciones;
        cafeBtn = new JButton("\u2615 " + t("Invítame a un café", "Buy me a coffee"));
        // El aviso de pausa por 429 (app.Servicios.avisarPausa429) llega por Servicios.avisoPausa429, fijado en
        // el EDT al construir la ventana (CableadoCromo.configurarVentana): ya no hace falta que esta barra se
        // "encuentre a sí misma" con un putClientProperty en `status` (limpieza 1, fase 4).
    }

    /** El número de la operación empezada más recientemente (siga viva o no): con él, cada operación sabe al
     *  acabar si otra la ha superado (PartidasPresenter.vigente). */
    public long opSerial() { return opSerial; }

    /** Los frenos de las operaciones: el cableado de las vistas pregunta aquí si SU operación debe parar. */
    public Operaciones operaciones() { return operaciones; }

    /** El toast actual (o null si no hay ninguno mostrado): lo usa la Watchlist para añadirle botones extra
     *  («Su perfil»/«Cara a cara») antes de que se auto-oculte. */
    public JPanel toast() { return toast; }

    /** Empieza una operación cancelable: número nuevo, freno propio (suelto) y barra de progreso con «Detener»,
     *  que desde ahora apunta a esta. Devuelve su número. EDT. */
    public long empezarOperacion() {
        opSerial++;
        operaciones.empezar(opSerial);
        if (continuarBtn != null) continuarBtn.setVisible(false);
        pintarOperaciones();
        return opSerial;
    }

    /** La operación {@code op} terminó (repetirlo no hace nada). Si quedan otras vivas, el progreso sigue y
     *  «Detener» pasa a la más reciente de ellas; si era la última, se ocultan y cada vista reactiva sus botones.
     *  EDT. */
    public void terminarOperacion(long op) {
        boolean seguiaViva = operaciones.terminar(op);
        pintarOperaciones();
        if (seguiaViva && !operaciones.hayVivas()) anfitrion.operacionTerminada();   // cualquier fin de la última deja la UI usable
    }

    /** Para la operación {@code op} desde un botón propio (la × de «Partidas de:», «Buscar partidas» → «Detener»):
     *  su freno, la interrupción de su hilo si es interrumpible, y el «Detener» de la barra repintado (si apuntaba a
     *  ella, queda deshabilitado hasta que termine). EDT. */
    public void detener(long op) {
        operaciones.detener(op);
        operaciones.interrumpir(op);
        pintarOperaciones();
    }

    /** Progreso y «Detener» según las operaciones vivas (util.Operaciones.estadoDetener decide; aquí se pinta). */
    private void pintarOperaciones() {
        Operaciones.EstadoDetener e = operaciones.estadoDetener();
        progreso.setVisible(e.visible());
        if (detenerDescBtn != null) {
            detenerDescBtn.setVisible(e.visible());
            detenerDescBtn.setEnabled(!e.visible() || e.habilitado());   // oculto, queda listo para la próxima
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
        este.add(respaldo); este.add(actualizarBtn); este.add(cafeBtn); este.add(firma);

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
            final long serialDetenido = operaciones.detenerUltima();   // SOLO la más reciente que sigue viva
            log("detener pulsado (op #" + serialDetenido + ", última empezada #" + opSerial + ")");
            if (serialDetenido < 0) return;
            operaciones.interrumpir(serialDetenido);   // corta ya su espera, si su trabajo es interrumpible
            pintarOperaciones();   // queda deshabilitado mientras esa siga viva
            status.setText(t("Deteniendo… (como mucho 15 s si había una petición en vuelo)",
                    "Stopping… (at most 15 s if a request was in flight)"));
            anfitrion.renovarHttp();   // las peticiones en vuelo caducan solas (≤15 s); las siguientes salen limpias
            watchdogDetener = new javax.swing.Timer(5000, ev -> {
                // Solo sobre LA MISMA operación, y solo si sigue viva: se da por terminada para la barra (su freno
                // sigue puesto y su hilo lo sigue viendo hasta que acabe de verdad).
                boolean seguiaViva = operaciones.terminar(serialDetenido);
                if (!seguiaViva) return;
                pintarOperaciones();
                if (operaciones.hayVivas()) return;   // no pisa el mensaje de la operación que sigue viva
                anfitrion.operacionTerminada();
                status.setText(t("Detenido.", "Stopped."));
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

    /** «Datos parciales (fuente de respaldo)»: visible solo mientras algo se sirve de World's Edge porque el companion
     *  falla (1.4, api.ConRespaldo). Oculta al empezar: sin respaldo, la barra es la de siempre. Paquete: BarraEstadoTest. */
    final JLabel respaldo = new JLabel(t("Datos parciales (fuente de respaldo)", "Partial data (fallback source)"));
    {
        respaldo.setVisible(false);
        respaldo.setFont(respaldo.getFont().deriveFont(Font.BOLD, 11f));
        respaldo.setToolTipText(t("<html>El companion no responde: algunos datos vienen de la API del juego (World's Edge).<br>"
                        + "Faltan la serie de ELO, las cuentas vinculadas, Twitch, el historial largo y las partidas en curso.<br>"
                        + "Se vuelve al companion solo, en cuanto responda.</html>",
                "<html>The companion is not responding: some data comes from the game's API (World's Edge).<br>"
                        + "Missing: ELO history, linked accounts, Twitch, long history and live games.<br>"
                        + "It switches back to the companion by itself as soon as it responds.</html>"));
    }

    /** true: se enseña «Datos parciales (fuente de respaldo)»; false: se quita. Sin diálogos. EDT. */
    public void mostrarRespaldo(boolean enRespaldo) {
        if (respaldo.isVisible() == enRespaldo) return;
        respaldo.setVisible(enRespaldo);
        java.awt.Container padre = respaldo.getParent();
        if (padre != null) { padre.revalidate(); padre.repaint(); }
    }

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
