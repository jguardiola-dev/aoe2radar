package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.service.RecService;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Carpeta de recs, savegame, «Enviar al juego» y descargas de Partidas. Sale tal cual de {@link PartidasView}
 * (1.3): el SwingWorker de download no cambia (mismo publish/done, mismo setEstado por invokeLater); all, view,
 * tableModel y los botones siguen en la fachada y se usan por {@code vista}.
 */
final class DescargasPartidas {

    /** Perfil/Live now descargan sin cambiar de pestaña: descargarSinCambiarVista los arma y download() los toma y
     *  los desarma al empezar (siempre). Solo los usa esta clase (antes eran campos públicos de la fachada). */
    boolean descargaSinCambiarVista;
    Runnable alTerminarDescarga;

    private final PartidasView vista;

    DescargasPartidas(PartidasView vista) { this.vista = vista; }

    public void abrirCarpeta() {
        try {
            Files.createDirectories(vista.anfitrion.recsDir());
            java.awt.Desktop.getDesktop().open(vista.anfitrion.recsDir().toFile());
        } catch (Exception ex) {
            vista.anfitrion.estado(t("No se pudo abrir la carpeta: ", "Couldn't open the folder: ") + causa(ex));
        }
    }

    public void vaciarRecs() {
        List<Path> files = new ArrayList<>();
        try {
            if (Files.isDirectory(vista.anfitrion.recsDir()))
                try (var st = Files.list(vista.anfitrion.recsDir())) {
                    st.filter(f -> f.getFileName().toString().endsWith(".aoe2record")).forEach(files::add);
                }
        } catch (IOException ex) {
            vista.anfitrion.estado(t("Error leyendo la carpeta: ", "Error reading the folder: ") + causa(ex));
            return;
        }
        if (files.isEmpty()) { vista.anfitrion.estado(t("La carpeta recs ya está vacía.", "The recs folder is already empty.")); return; }
        int r = JOptionPane.showConfirmDialog(vista.ventana,
                t("Se borrarán ", "This will delete ") + files.size()
                        + t(" recs de la carpeta recs.\n¿Continuar?", " recs from the recs folder.\nContinue?"),
                t("Vaciar recs", "Empty recs"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        int ok = 0;
        for (Path f : files) { try { Files.delete(f); ok++; } catch (IOException ignored) {} }
        for (Match m : vista.all) { m.enDisco = false; if (m.estado.startsWith("✓")) m.estado = ""; }
        vista.tableModel.fireTableDataChanged();
        vista.anfitrion.estado(ok + " recs borradas.");
    }

    /** Carpeta savegame activa: la de config si sigue existiendo; si no, la detectada. */
    public Path obtenerSavegame(boolean interactivo) {
        String cfg = leerConfig("savegame", null);
        if (cfg != null && Files.isDirectory(Path.of(cfg))) return Path.of(cfg);
        List<Path> dets = dev.tirador.aoe2radar.service.Juego.detectarSavegames();
        if (dets.size() == 1) {
            guardarConfig("savegame", dets.get(0).toString());
            return dets.get(0);
        }
        if (!interactivo) return null;
        if (dets.size() > 1) {
            Object sel = JOptionPane.showInputDialog(vista.ventana,
                    t("Hay varios perfiles del juego. Elige tu carpeta savegame:",
                      "There are several game profiles. Pick your savegame folder:"),
                    t("Carpeta savegame", "Savegame folder"), JOptionPane.PLAIN_MESSAGE, null, dets.toArray(), dets.get(0));
            if (sel == null) return null;
            guardarConfig("savegame", sel.toString());
            return (Path) sel;
        }
        JOptionPane.showMessageDialog(vista.ventana,
                t("No encuentro la carpeta savegame del juego.\nElígela a mano:\n…\\Games\\Age of Empires 2 DE\\<perfil>\\savegame",
                  "Couldn't find the game's savegame folder.\nPick it manually:\n…\\Games\\Age of Empires 2 DE\\<profile>\\savegame"));
        return elegirSavegameManual();
    }

    public Path elegirSavegameManual() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(t("Elige la carpeta savegame de AoE2 DE", "Pick the AoE2 DE savegame folder"));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String actual = leerConfig("savegame", null);
        Path base = actual != null ? Path.of(actual)
                : Path.of(System.getProperty("user.home", "."), "Games", "Age of Empires 2 DE");
        if (Files.isDirectory(base)) fc.setCurrentDirectory(base.toFile());
        if (fc.showOpenDialog(vista.ventana) != JFileChooser.APPROVE_OPTION) return null;
        Path p = fc.getSelectedFile().toPath();
        guardarConfig("savegame", p.toString());
        return p;
    }

    /** Enviar al juego: copia lo que ya está sano en disco y descarga+envía lo que falte (decisión de Jorge,
     *  DEUDA 94/95: «sana» es la misma regla que usa RecService.procesar, no un Files.exists propio). */
    public void enviarInteligente(List<Match> objetivo) {
        if (objetivo.isEmpty()) { vista.anfitrion.estado(t("No hay partidas seleccionadas.", "No games selected.")); return; }
        // Leer la cabecera de cada rec es disco: va en un hilo de fondo (revisión 1.3). Lo que sigue (el diálogo del
        // savegame, la copia y la descarga de lo que falte) arranca desde done(), ya en el EDT, en el mismo orden.
        final List<Match> lista = new ArrayList<>(objetivo);
        final Function<Match, Path> destino = vista.anfitrion::destino;
        new SwingWorker<Void, Void>() {
            final List<Match> enDisco = new ArrayList<>(), faltan = new ArrayList<>();
            @Override protected Void doInBackground() { separarSanas(lista, destino, enDisco, faltan); return null; }
            @Override protected void done() {
                Runnable descargarFaltan = faltan.isEmpty() ? null : () -> download(faltan, true);
                if (!enDisco.isEmpty()) enviarASavegame(enDisco, descargarFaltan);   // copia primero; luego descarga
                else if (descargarFaltan != null) descargarFaltan.run();
            }
        }.execute();
    }

    /** Copia al savegame lo que le llegue en `objetivo`: NO vuelve a comprobar RecService.recSana (su único
     *  llamador, enviarInteligente, ya leyó la cabecera de cada archivo para armar esta lista); así es una sola
     *  lectura de cabecera por partida, no dos. */
    public void enviarASavegame(List<Match> objetivo) { enviarASavegame(objetivo, null); }

    /** Igual, y al acabar (copie o no) corre {@code despues} en el EDT: enviarInteligente descarga ahí lo que
     *  faltaba, para que siga yendo después de la copia, como antes. */
    void enviarASavegame(List<Match> objetivo, Runnable despues) {
        if (objetivo.isEmpty()) { vista.anfitrion.estado(t("No hay partidas seleccionadas.", "No games selected.")); if (despues != null) despues.run(); return; }
        Path sg = obtenerSavegame(true);   // puede abrir diálogos: en el EDT
        if (sg == null) { vista.anfitrion.estado(t("Sin carpeta savegame configurada.", "No savegame folder configured.")); if (despues != null) despues.run(); return; }
        final List<Match> lista = new ArrayList<>(objetivo);
        final Function<Match, Path> destino = vista.anfitrion::destino;
        final String enJuego = t("✓✓ en juego", "✓✓ in game");
        new SwingWorker<Copia, Void>() {
            @Override protected Copia doInBackground() {   // copiar es disco: fuera del EDT (setEstado ya va por invokeLater)
                return copiarAlSavegame(lista, sg, destino, m -> vista.setEstado(m, enJuego));
            }
            @Override protected void done() {
                try {
                    Copia c = get();
                    vista.anfitrion.estado(c.ok() + t(" recs enviadas al juego", " recs sent to the game") +
                            (c.yaEstaban() > 0 ? " (" + c.yaEstaban() + t(" ya estaban, actualizadas)", " were already there, refreshed)") : "") + ".");
                } catch (Exception ex) {
                    log("enviar al juego: ERROR " + causa(ex));
                    vista.anfitrion.estado("Error: " + causa(ex));
                }
                if (despues != null) despues.run();
            }
        }.execute();
    }

    // Núcleo sin Swing de «Enviar al juego»: el prólogo (lista vacía, obtenerSavegame con sus diálogos) y el
    // epílogo (anfitrion.estado) se quedan en el EDT; esto solo lee y copia archivos, y desde la 1.3 corre en el
    // hilo de fondo de un SwingWorker (separarSanas en enviarInteligente, copiarAlSavegame en enviarASavegame).

    /** Reparte `objetivo` en lo que ya está sano en disco (RecService.recSana: lee la cabecera) y lo que falta. */
    static void separarSanas(List<Match> objetivo, Function<Match, Path> destino, List<Match> enDisco, List<Match> faltan) {
        for (Match m : objetivo) (RecService.recSana(destino.apply(m)) ? enDisco : faltan).add(m);
    }

    /** Lo que devuelve copiarAlSavegame: cuántas se copiaron y cuántas de esas ya estaban en el savegame. */
    record Copia(int ok, int yaEstaban) { }

    /** Copia cada partida al savegame `sg` y marca m.enJuego; `alCopiar` se llama justo donde antes iba setEstado
     *  (que ya es seguro desde cualquier hilo: va por invokeLater). */
    static Copia copiarAlSavegame(List<Match> objetivo, Path sg, Function<Match, Path> destino, Consumer<Match> alCopiar) {
        int ok = 0, yaEstaban = 0;
        for (Match m : objetivo) {
            boolean ya = Files.exists(sg.resolve(destino.apply(m).getFileName().toString()));
            if (dev.tirador.aoe2radar.service.Juego.copiarASavegame(m, sg)) {
                ok++;
                if (ya) yaEstaban++;
                try {
                    Files.setLastModifiedTime(sg.resolve(destino.apply(m).getFileName().toString()),
                            java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
                } catch (Exception ignored) {}
                m.enJuego = true;
                alCopiar.accept(m);
            }
        }
        return new Copia(ok, yaEstaban);
    }

    String abrirSgTxt() { return t("Abrir carpeta savegame del juego", "Open game savegame folder"); }

    public void abrirSavegame() {
        Path sg = obtenerSavegame(true);
        if (sg == null) { vista.anfitrion.estado(t("Sin carpeta savegame configurada.", "No savegame folder configured.")); return; }
        try { java.awt.Desktop.getDesktop().open(sg.toFile()); }
        catch (Exception ex) { vista.anfitrion.estado(t("No se pudo abrir la carpeta: ", "Couldn't open the folder: ") + causa(ex)); }
    }

    public void download(List<Match> objetivoIn) { download(objetivoIn, false); }

    public void download(List<Match> objetivoIn, boolean enviarSiempre) {
        // Los dos encargos de descargarSinCambiarVista son de ESTA llamada: se toman y se desarman ya, salga por
        // donde salga (antes, una salida temprana los dejaba armados para la descarga siguiente, y alTerminar solo
        // se consumía si la descarga seguía vigente al acabar).
        final boolean sinCambiarVista = descargaSinCambiarVista;
        final Runnable alTerminar = alTerminarDescarga;
        descargaSinCambiarVista = false;
        alTerminarDescarga = null;
        List<Match> objetivo = new ArrayList<>();
        List<Match> vivas = new ArrayList<>();
        for (Match m : objetivoIn) (m.finished == null ? vivas : objetivo).add(m);
        if (!vivas.isEmpty() && objetivo.isEmpty()) {
            if (vivas.size() == 1) vista.anfitrion.espectarPartida(vivas.get(0).id);
            else vista.anfitrion.estado(t("Esas partidas están EN DIRECTO: doble clic en una para espectarla.",
                    "Those games are LIVE: double-click one to spectate."));
            return;
        }
        if (!vivas.isEmpty())
            vista.anfitrion.estado(t("Las partidas EN DIRECTO no se descargan; se saltan.",
                    "LIVE games can't be downloaded; skipping them."));
        if (objetivo.isEmpty()) { vista.anfitrion.estado(t("No hay partidas seleccionadas.", "No games selected.")); return; }
        if (!sinCambiarVista) vista.anfitrion.mostrarDirectos(false);
        vista.dlSel.setEnabled(false); vista.dlAll.setEnabled(false);
        vista.anfitrion.trabajando(true);
        final long miSerial = vista.anfitrion.operacionActual();
        Set<Long> trackedIds = new HashSet<>();
        for (int i = 0; i < vista.enlaceWatchlist.totalJugadores(); i++) trackedIds.add(vista.enlaceWatchlist.jugador(i).id());
        final boolean autoCopiar = vista.anfitrion.autoCopiarAlDescargar();
        final boolean autoCopiarFinal = autoCopiar || enviarSiempre;
        final Path sgAuto = autoCopiarFinal ? obtenerSavegame(enviarSiempre) : null;
        if (autoCopiarFinal && sgAuto == null)
            log("copia automática a savegame activada pero sin carpeta resuelta: no se copiará");

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                vista.anfitrion.anotarHiloOperacion();
                try { Files.createDirectories(vista.anfitrion.recsDir()); } catch (IOException ignored) {}
                int ok = 0, copiadas = 0;
                for (Match m : objetivo) {
                    if (vista.anfitrion.detenido()) break;
                    vista.setEstado(m, "descargando…");
                    RecService.Resultado r = vista.recService.procesar(m, trackedIds, autoCopiarFinal, sgAuto, vista.anfitrion::detenido);
                    boolean hecho = r.estado() != RecService.Estado.FALLO;
                    if (hecho) {
                        m.enDisco = true;
                        ok++;
                        if (r.enJuego()) {
                            copiadas++;
                            m.enJuego = true;
                        }
                    }
                    vista.setEstado(m, hecho ? (r.enJuego() ? t("✓✓ en juego", "✓✓ in game") : "✓ guardada") : "✗ no disponible");
                    // La pausa de cortesía es para espaciar peticiones a la API: si la rec se reutilizó del
                    // disco (RecService.Resultado.reutilizada), no hubo ninguna que espaciar.
                    if (!r.reutilizada()) vista.anfitrion.dormir(vista.pausaMs);
                }
                final int n = ok, tot = objetivo.size(), cop = copiadas;
                final boolean parada = vista.anfitrion.detenido();
                SwingUtilities.invokeLater(() ->
                        vista.anfitrion.estado((parada ? t("Detenido. ", "Stopped. ") : "")
                                + n + "/" + tot + t(" recs guardadas en ./", " recs saved to ./") + vista.anfitrion.recsDir()
                                + (cop > 0 ? "  ·  " + cop + t(" al juego", " to the game") : "")
                                + (n < tot ? t("  ·  detalle en descargas.log", "  ·  details in descargas.log") : "")));
                return null;
            }
            @Override protected void done() {
                if (!PartidasPresenter.vigente(miSerial, vista.anfitrion.operacionActual())) {
                    log("descargas #" + miSerial + ": terminó superada por la op #" + vista.anfitrion.operacionActual());
                } else {
                    vista.dlSel.setEnabled(true);
                    vista.dlAll.setEnabled(true);
                    vista.anfitrion.trabajando(false);
                }
                // El aviso de quien la pidió (Perfil/Live now: repintar su tabla con lo ya en disco) corre siempre al
                // acabar ESTA descarga, aunque otra operación se llevara el semáforo mientras tanto.
                if (alTerminar != null) alTerminar.run();
            }
        }.execute();
    }

    /** Descarga sin cambiar de pestaña (Perfil/Live now la piden desde su propia vista). */
    public void descargarSinCambiarVista(List<Match> partidas, boolean enviarAlJuego, Runnable alTerminar) {
        descargaSinCambiarVista = true;
        alTerminarDescarga = alTerminar;
        download(partidas, enviarAlJuego);
    }
}
