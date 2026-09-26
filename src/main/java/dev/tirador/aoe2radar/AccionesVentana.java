// ============================================================================
// AccionesVentana: arranque de la ventana principal (carga inicial, timers de
// vigilancia) y las acciones de “abrir algo”: navegador, Twitch, donacion,
// espectar una partida y CaptureAge. Metodos static que reciben la ventana
// (SpoilerFreeRecs v): es composicion, no un servicio con contrato propio, y
// vive en el paquete raiz porque lee campos de paquete de la ventana (status,
// watchlist, partidas, vigilante...) sin volverlos public.
// Movido tal cual desde SpoilerFreeRecs (fase 3, tanda 4, oleada B, zona B4):
// mismos textos, mismos hilos (EDT/fondo), mismo orden; solo cambian las
// referencias sin cualificar a `v.` porque ahora es un metodo static.
// ============================================================================

package dev.tirador.aoe2radar;

import dev.tirador.aoe2radar.app.Servicios;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.Espectar;
import dev.tirador.aoe2radar.ui.TemaApp;
import dev.tirador.aoe2radar.ui.WatchlistView;

import javax.swing.*;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.net.URI;
import java.nio.file.Path;

import static dev.tirador.aoe2radar.api.Freno.ctrlMult;
import static dev.tirador.aoe2radar.api.Freno.ctrlOn;
import static dev.tirador.aoe2radar.app.Servicios.*;
import static dev.tirador.aoe2radar.cache.Canales.cargarCanales;
import static dev.tirador.aoe2radar.cache.Paises.cargarPaises;
import static dev.tirador.aoe2radar.cache.Paises.guardarPaises;
import static dev.tirador.aoe2radar.service.EnlaceVivo.tickMs;
import static dev.tirador.aoe2radar.service.Juego.rutaCaptureAge;
import static dev.tirador.aoe2radar.sfrdata.Ladder.ladderAsegurar;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.cargarEloAyer;
import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.ui.TemaApp.flatLafDisponible;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Identidad.TWITCH;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;
import static dev.tirador.aoe2radar.util.Sistema.fijarAutoArranque;

/** Arranque de la ventana y acciones de “abrir algo” (fase 3, tanda 4, oleada B, zona B4).
 *  Clase final sin estado propio: solo metodos static que reciben la ventana. Vive en el
 *  paquete raiz -- junto a SpoilerFreeRecs -- para leer sus campos de paquete (status,
 *  watchlist, partidas, vigilante...) sin volverlos public. */
final class AccionesVentana {

    private AccionesVentana() {}

    // Cargas iniciales (canales, jugadores, tema/fuentes) y el arranque de los
    // temporizadores: vigilante de vivos, ping del socket, comprobar actualizacion,
    // precarga del tech tree y del ladder. Es lo último que hace el constructor.
    static void arrancar(SpoilerFreeRecs v) {
        cargarCanales();
        v.watchlist.loadPlayers();
        v.watchlist.sanearVinculosHuerfanos();
        v.getRootPane().setDefaultButton(v.partidas.fetchBtn);   // acción primaria: acento y Enter
        TemaApp.ajustarGrises(v, flatLafDisponible && temaOscuroActivo);
        TemaApp.ajustarBotonesEspeciales(v, flatLafDisponible && temaOscuroActivo);
        TemaApp.ajustarFuentesSecundarias(v);
        v.partidas.table.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke("ENTER"), "descargarSeleccion");
        v.partidas.table.getActionMap().put("descargarSeleccion", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { v.partidas.download(v.partidas.selectedRows()); }
        });
        v.watchlist.refrescarWatchlist();
        SwingUtilities.invokeLater(() -> {
            v.watchlist.grupoCombo.setSelectedItem(WatchlistView.TOP_LADDER);   // la app abre en ★
            v.mostrarDirectos(true);                    // …con los Directos a la vista, no una tabla vacía
            if (Boolean.parseBoolean(leerConfig("inicio_min", "false")))
                v.setExtendedState(JFrame.ICONIFIED);
        });
        final boolean autoOn = Boolean.parseBoolean(leerConfig("autoarranque", "false"));
        new Thread(() -> fijarAutoArranque(autoOn)).start();   // reconcilia SIEMPRE: escribe si sí, borra si no
        v.enlaceVivo.iniciarPing();
        javax.swing.Timer tUpd = new javax.swing.Timer(8000, e -> {   // una vez, tras arrancar
            v.menuConfiguracion.comprobarActualizacion(false); v.techTree.precargar();
            cargarPaises(); v.autoScroll.instalar();
            new javax.swing.Timer(60_000, ev -> guardarPaises()).start();
            v.watchlist.refrescarCampanas();
            v.campanasTimer = new javax.swing.Timer(15 * 60_000, ev -> v.watchlist.refrescarCampanas()); v.campanasTimer.start();
            new javax.swing.Timer(1000, ev -> { if (!VIVO.nadieJugando() && v.playersList.isShowing()) v.playersList.repaint(); }).start();   // el reloj del subtexto «en partida» corre   // las listas de las campanas (tops, país, clan) se repasan cada 15 min
            Thread lh = new Thread(() -> ladderAsegurar(false), "ladder-precarga"); lh.setDaemon(true); lh.start();   // el volcado del ladder, en silencio
        });
        tUpd.setRepeats(false);
        tUpd.start();
        v.vigilante = new javax.swing.Timer(tickMs(), e -> {
            // Con el socket conectado, quién está en partida llega al instante: los sondeos pasan a ser una resincronización
            // cada 10 min (× el multiplicador del mando a distancia). Si el socket cae, vuelven al ritmo del tick.
            long ahoraMs = System.currentTimeMillis();
            long resync = (long) (10 * 60_000L * ctrlMult("tick_mult"));
            boolean tocaSondear = ctrlOn("sondeo") && (!v.enlaceVivo.conectado() || ahoraMs - v.ultimoResyncMs >= resync);
            if (tocaSondear) v.ultimoResyncMs = ahoraMs;
            if (tocaSondear) v.watchlist.vigilarVivos();
            if (!v.watchlist.modoTop()) v.directos.vigilarTwitch();   // en ★ lo dispara el propio río al terminar (vigilarTwitch tiene su propio ritmo)
            if (v.watchlist.modoTop() && !v.watchlist.modoClan()) {
                // recuperación automática: si el top no pudo cargarse (o solo hay caché), reintenta
                if (v.watchlist.topLadderVacio() || ahoraMs - v.watchlist.topCargadoMs() > 15 * 60_000L)
                    v.watchlist.cargarTopLadder(true);
                else if (tocaSondear) v.watchlist.vigilarTop();
            } else if (v.watchlist.modoClan() && tocaSondear) v.watchlist.vigilarTop();   // el clan se vigila, pero nunca se sustituye por el top del ladder
        });
        new Thread(() -> { cargarControl(); cargarEloAyer(); }, "control").start();
        if (!leerConfig("mi_pid", "").isBlank()) v.miPartida.iniciarVigilancia();   // «Mi partida»: aviso temprano al encontrar partida

        new javax.swing.Timer(3_600_000, e -> new Thread(Servicios::cargarControl, "control").start()).start();
        v.vigilante.setInitialDelay(tickMs());
        v.vigilante.start();
        if (Boolean.parseBoolean(leerConfig("buscar_al_abrir", "true")))
            SwingUtilities.invokeLater(() -> { if (!v.watchlist.modoTop() && v.playersModel.size() > 0) v.partidas.fetchMatches(v.partidas.fetchBtn); });
    }

    static void abrirUrl(SpoilerFreeRecs v, String url) {
        try { Desktop.getDesktop().browse(URI.create(url)); }
        catch (Exception ex) { v.status.setText(t("No se pudo abrir el navegador: ", "Couldn't open the browser: ") + causa(ex)); }
    }

    static boolean usarCA() { return Boolean.parseBoolean(leerConfig("usar_ca", "false")); }

    /** Lanza CaptureAge — con una rec (la reproduce con su overlay) o sin argumentos (modo acompañante: se
     *  engancha al juego al espectar). Buscar la ruta y arrancar el proceso viven en service.Espectar (fase 3,
     *  tanda 4, Z4); aquí solo queda traducir el resultado al texto de estado. */
    static void lanzarCaptureAge(SpoilerFreeRecs v, Path rec) {
        Espectar.ResultadoCaptureAge r = v.espectar.lanzarCaptureAge();
        switch (r.estado()) {
            case RUTA_AUSENTE -> v.status.setText(t("No encuentro CaptureAge: fija su ruta en Configuración → Cambiar ruta de CaptureAge…",
                    "Can't find CaptureAge: set its path in Settings → Change CaptureAge path…"));
            case LANZADO -> v.status.setText(t("Lanzando CaptureAge de acompañante…", "Launching CaptureAge alongside…"));
            case FALLO -> v.status.setText(t("No se pudo lanzar CaptureAge: ", "Couldn't launch CaptureAge: ") + causa(r.error()));
        }
    }

    /** Abre el juego espectando una partida en curso (protocolo
     *  aoe2de://1/matchId, el mismo que usa aoe2companion/aoe2recs). */
    static void espectarPartida(SpoilerFreeRecs v, long matchId) {
        log("espectar: lanzando aoe2de://1/" + matchId);
        try {
            v.espectar.espectarPartida(matchId);
            boolean caListo = rutaCaptureAge() != null;
            if (usarCA() && caListo) lanzarCaptureAge(v, null);
            String pista = (!usarCA() && caListo)
                    ? t(" (Configuración → «Usar CaptureAge» lo lanzaría también)",
                        " (Settings → \u201CUse CaptureAge\u201D would launch it too)")
                    : "";
            v.status.setText(t("Abriendo AoE2 para espectar\u2026 Si estaba cerrado tardará; si la partida termina antes de entrar, el juego dirá «Invalid match ID».",
                    "Opening AoE2 to spectate\u2026 If it was closed it takes a while; if the game ends before you join, AoE2 will show \"Invalid match ID\".") + pista);
        } catch (Exception ex) {
            v.status.setText(t("No se pudo abrir el juego: ", "Couldn't open the game: ") + causa(ex));
        }
    }

    /** Verifica que la partida sigue en curso justo antes de lanzar el juego:
     *  si acaba de terminar, avisa y re-sincroniza en vez de abrir AoE2 a un
     *  «Invalid match ID». La llamada de red y sus logs viven en service.Espectar.viva (fase 3, tanda 4, Z4). */
    static void espectarVerificando(SpoilerFreeRecs v, long profileId, long matchId) {
        v.status.setText(t("Comprobando que la partida sigue en curso…", "Checking the game is still live…"));
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() { return v.espectar.viva(profileId, matchId, PER_PAGE); }
            @Override protected void done() {
                Boolean viva;
                try { viva = get(); } catch (Exception e) { viva = null; }
                if (Boolean.FALSE.equals(viva)) {
                    v.status.setText(t("Esa partida ya terminó (el companion la seguía dando por viva): el directo no existe. Dale a «Buscar partidas» para bajar la rec.",
                            "That game already ended (the companion still listed it as live): the live match is gone. Hit search to download the rec."));
                    VIVO.quitarPartida(matchId);   // el punto fantasma se va ya
                    v.watchlist.actualizarIndicadoresVivos(); v.watchlist.refrescarAlturasWatch(); v.playersList.repaint();
                    v.watchlist.vigilarVivos();
                    return;
                }
                espectarPartida(v, matchId);
            }
        }.execute();
    }

    static void espectar(SpoilerFreeRecs v, Player p) {
        Long mid = VIVO.matchDe(p.id());
        if (mid != null) espectarVerificando(v, p.id(), mid);
    }

    static void abrirTwitch(SpoilerFreeRecs v) {
        try { Desktop.getDesktop().browse(URI.create("https://" + TWITCH)); }
        catch (Exception ex) { v.status.setText("Abre en tu navegador: https://" + TWITCH); }
    }

    static void abrirDonacion(SpoilerFreeRecs v) {
        try { Desktop.getDesktop().browse(URI.create(SpoilerFreeRecs.DONAR_URL)); }
        catch (Exception ex) { v.status.setText("Abre en tu navegador: " + SpoilerFreeRecs.DONAR_URL); }
    }
}
