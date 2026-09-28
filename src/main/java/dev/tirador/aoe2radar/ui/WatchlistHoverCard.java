package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.service.TarjetaPerfil;

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
import java.util.ArrayList;
import java.util.List;

import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * La tarjeta de perfil flotante de la Watchlist (hoy inerte: el hover-timer nunca llega a dispararla, ver
 * el {@code if (false && ...)} del MouseMotionListener de la lista). Sale de WatchlistView tal cual en la 1.3:
 * mismo código, mismo SwingWorker; su estado (hoverCard, hoverTimer, hoverPid...) sigue en la fachada.
 * Hilos: todo en el EDT salvo el doInBackground de mostrarPerfilCard (red vía Anfitrion.tarjetaNocturna, que puede bajar
 * las chispas de sfr-data, y Anfitrion.perfilApi/paginaApi). El HTML y la regla de la gráfica viven en service.TarjetaPerfil.
 */
final class WatchlistHoverCard {

    private final WatchlistView wv;

    WatchlistHoverCard(WatchlistView wv) { this.wv = wv; }

    boolean hoverProcede() {
        Component c = wv.hoverAncla != null ? wv.hoverAncla : wv.playersList;
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == wv.ventana
                && MenuSelectionManager.defaultManager().getSelectedPath().length == 0
                && c.isShowing() && c.getMousePosition() != null;
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
        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() {
                // 1.4, nocturno primero: con las chispas de sfr-data, sin /profiles ni /matches (null → la API, como antes)
                TarjetaPerfil.Datos noct = wv.anfitrion.tarjetaNocturna(pid);
                if (noct != null) return new Object[]{ TarjetaPerfil.html(nombre, noct), noct.spark() };
                String pais = "", clan = "";
                long games = 0;
                Integer rating = null, maxRating = null, wins = null, losses = null;
                try {
                    Perfil pf = wv.anfitrion.perfilApi(pid);
                    wv.anfitrion.aprenderCanal(pid, pf.canal());
                    String c = pf.pais();
                    wv.anfitrion.aprenderPais(pid, c);
                    if (c != null && !"null".equals(c)) pais = c.toUpperCase();
                    String cl = pf.clan();
                    if (cl != null && !"null".equals(cl)) clan = cl;
                    games = pf.partidas();
                    for (Perfil.Ladder lb : pf.ladders()) {
                        String lid = String.valueOf(lb.id());
                        if (!"rm_1v1".equals(lid) && !"3".equals(lid)) continue;
                        if (lb.rating() != null) rating = lb.rating();
                        if (lb.ratingMax() != null) maxRating = lb.ratingMax();
                        if (lb.ganadas() != null) wins = lb.ganadas();
                        if (lb.perdidas() != null) losses = lb.perdidas();
                        break;
                    }
                } catch (Exception ex) {
                    log("perfil card: fallo con " + pid + ": " + causa(ex));
                }
                int[] spark = null;
                boolean pocos1v1 = false;
                try {
                    List<Integer> serie = new ArrayList<>();
                    for (int pag = 1; pag <= 2 && serie.size() <= 15; pag++) {
                        wv.anfitrion.dormir(wv.pausaMs / 2);
                        dev.tirador.aoe2radar.model.PaginaPartidas ms = wv.anfitrion.paginaApi(pid, pag, wv.perPage);
                        if (ms.brutas() == 0) break;
                        for (Match m : ms.partidas()) {
                            if (m.finished == null || m.players.size() != 2
                                    || m.mode == null || !m.mode.startsWith("1v1 Random")) continue;
                            for (MatchPlayer mp : m.players)
                                if (mp.id == pid && mp.rating != null) serie.add(mp.rating);
                        }
                    }
                    TarjetaPerfil.Chispa ch = TarjetaPerfil.chispa(serie);   // sin la forma fresca; cronológico
                    spark = ch.spark();
                    pocos1v1 = ch.pocos1v1();
                } catch (Exception ex) {
                    log("perfil card: sparkline falló con " + pid + ": " + causa(ex));
                }
                return new Object[]{ TarjetaPerfil.html(nombre, new TarjetaPerfil.Datos(pais, clan, games, rating, maxRating, wins, losses, spark, pocos1v1)), spark };
            }
            @Override protected void done() {
                try {
                    Object[] r = get();
                    wv.anfitrion.tarjetaPerfilGuardar(pid, new Object[]{ r[0], r[1] });
                    if (fijar || ((wv.hoverPid == pid || wv.hoverPid == 0) && hoverProcede()))
                        pintarCard((String) r[0], (int[]) r[1], enPantalla, pid, fijar);
                } catch (Exception ignored) { }
            }
        }.execute();
    }

    private void pintarCard(String html, int[] spark, Point enPantalla, long pid, boolean fijar) {
        ocultarHoverCard(true);
        wv.cardFijada = fijar;
        wv.hoverPid = pid;
        wv.hoverCard = new JWindow(wv.ventana);
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
