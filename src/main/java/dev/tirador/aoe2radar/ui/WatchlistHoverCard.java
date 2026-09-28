package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.service.TarjetaPerfil;
import dev.tirador.aoe2radar.service.TarjetaService;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.RenderingHints;

import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;

/**
 * La tarjeta de perfil flotante de la Watchlist: sale tras 600 ms quieto sobre una fila (hoverTimer de WatchlistLista) y
 * se oculta al salir de la lista, al pulsar, al hacer scroll, al rehacerse la lista (otro grupo, ★ top, país o clan, o
 * un reordenado) y al perder el foco la ventana (un diálogo u otra aplicación). Apagada desde la 1.1 porque cada
 * tarjeta costaba dos o tres llamadas; encendida en la 1.4: con las chispas nocturnas no cuesta ninguna y, si no,
 * una (ver service.TarjetaService). Su estado (hoverCard, hoverTimer, hoverPid...) sigue en la fachada.
 * Hilos: todo en el EDT salvo el doInBackground de mostrarPerfilCard (TarjetaService.cargar: chispas de sfr-data o
 * /profiles). El HTML y la regla de la gráfica viven en service.TarjetaPerfil.
 */
final class WatchlistHoverCard {

    private final WatchlistView wv;
    /** Fuente de la tarjeta y una carga a la vez por pid (ver TarjetaService). */
    private final TarjetaService tarjetas;

    WatchlistHoverCard(WatchlistView wv) {
        this.wv = wv;
        this.tarjetas = new TarjetaService(new TarjetaService.Fuentes() {
            @Override public TarjetaPerfil.Datos nocturna(long pid) { return wv.anfitrion.tarjetaNocturna(pid); }
            @Override public Perfil perfil(long pid) throws Exception { return wv.anfitrion.perfilApi(pid); }
        });
    }

    boolean hoverProcede() {
        Component c = wv.hoverAncla != null ? wv.hoverAncla : wv.playersList;
        return TarjetaService.procede(KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == wv.ventana,
                MenuSelectionManager.defaultManager().getSelectedPath().length != 0,
                c.isShowing(), c.isShowing() && c.getMousePosition() != null);
    }

    public void ocultarHoverCard() { ocultarHoverCard(false); }

    public void ocultarHoverCard(boolean forzar) {
        if (!forzar && wv.cardFijada) return;   // la card del clic derecho no la mata pasear el ratón
        if (wv.hoverTimer != null) wv.hoverTimer.stop();
        if (wv.hoverCard != null) { wv.hoverCard.dispose(); wv.hoverCard = null; }
        wv.cardFijada = false;
        wv.hoverPid = 0;
    }

    /** Muestra la tarjeta de perfil de un jugador en la posición dada. */
    void mostrarPerfilCard(long pid, String nombre, Point enPantalla) { mostrarPerfilCard(pid, nombre, enPantalla, false); }

    void mostrarPerfilCard(long pid, String nombre, Point enPantalla, boolean fijar) {
        Object[] cache = wv.anfitrion.tarjetaPerfilCache(pid);
        if (cache != null) {
            pintarCard((String) cache[0], (int[]) cache[1], enPantalla, pid, fijar);
            return;
        }
        if (!tarjetas.reservar(pid)) return;   // ya se está cargando: su done() la pinta si el ratón sigue ahí
        new SwingWorker<TarjetaService.Resultado, Void>() {
            @Override protected TarjetaService.Resultado doInBackground() {
                // nocturno primero; si no, una llamada, y solo si el ratón sigue en esa fila (hoverPid es volatile)
                TarjetaService.Resultado r = tarjetas.cargar(pid, nombre, () -> fijar || wv.hoverPid == pid);
                if (r != null && r.perfil() != null) {   // lo que la API enseña de paso, como antes
                    wv.anfitrion.aprenderCanal(pid, r.perfil().canal());
                    wv.anfitrion.aprenderPais(pid, r.perfil().pais());
                }
                return r;
            }
            @Override protected void done() {
                try {
                    TarjetaService.Resultado r = get();
                    if (r == null) return;   // ya no interesaba: ni llamada ni tarjeta (el finally libera el pid)
                    if (r.guardar()) wv.anfitrion.tarjetaPerfilGuardar(pid, new Object[]{ r.html(), r.spark() });
                    if (TarjetaService.pintarAlLlegar(pid, wv.hoverPid, fijar, hoverProcede()))
                        pintarCard(r.html(), r.spark(), enPantalla, pid, fijar);
                } catch (Exception ignored) {
                } finally {
                    tarjetas.liberar(pid);
                }
            }
        }.execute();
    }

    private void pintarCard(String html, int[] spark, Point enPantalla, long pid, boolean fijar) {
        ocultarHoverCard(true);
        wv.cardFijada = fijar;
        wv.hoverPid = pid;
        wv.hoverCard = new JWindow(wv.ventana);
        wv.hoverCard.setFocusableWindowState(false);   // no roba el foco: si lo hiciera, alPerderFoco la ocultaría al momento
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") != null
                        ? UIManager.getColor("Component.borderColor") : Color.GRAY),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        p.add(new JLabel(html), BorderLayout.CENTER);
        if (spark != null) p.add(new SparkPanel(spark), BorderLayout.SOUTH);
        wv.hoverCard.add(p);
        wv.hoverCard.pack();
        wv.hoverCard.setLocation(enPantalla.x + 14, enPantalla.y + 10);
        wv.hoverCard.setVisible(true);
    }

    /** Panel con la mini gráfica del rating (termina 10 partidas atrás). */
    static class SparkPanel extends JPanel {
        final int[] datos;
        SparkPanel(int[] datos) { this(datos, 200, 44); }
        SparkPanel(int[] datos, int w, int h) {
            this.datos = datos;
            setPreferredSize(new Dimension(w, h));
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (datos == null || datos.length < 2) return;
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight() - 14;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int v : datos) { min = Math.min(min, v); max = Math.max(max, v); }
            if (max == min) max = min + 1;
            g.setColor(temaOscuroActivo ? new Color(0xFF, 0xC9, 0x4D) : new Color(0xB0, 0x78, 0x00));
            int n = datos.length;
            int px = -1, py = -1;
            for (int i = 0; i < n; i++) {
                int x = (int) Math.round(i * (w - 4) / (double) (n - 1)) + 2;
                int y = 4 + (int) Math.round((max - datos[i]) * (h - 8) / (double) (max - min));
                if (px >= 0) g.drawLine(px, py, x, y);
                px = x; py = y;
            }
            g.setFont(getFont().deriveFont(Font.PLAIN, 10f));
            g.setColor(UIManager.getColor("Label.disabledForeground") != null
                    ? UIManager.getColor("Label.disabledForeground") : Color.GRAY);
            g.drawString(String.valueOf(min), 2, getHeight() - 2);
            String sMax = String.valueOf(max);
            g.drawString(sMax, w - g.getFontMetrics().stringWidth(sMax) - 2, getHeight() - 2);
        }
    }
}
