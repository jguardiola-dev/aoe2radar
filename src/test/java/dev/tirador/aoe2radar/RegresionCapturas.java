package dev.tirador.aoe2radar;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.Comparado;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.cache.CachePerfiles;
import dev.tirador.aoe2radar.cache.HistorialDisco;
import dev.tirador.aoe2radar.cache.Paises;
import dev.tirador.aoe2radar.service.ConsultasLadder;
import dev.tirador.aoe2radar.sfrdata.CivStats;
import dev.tirador.aoe2radar.sfrdata.Ladder;
import dev.tirador.aoe2radar.techtree.TechTreeDatos;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Harness de capturas (antes TTShot): test de caracterización de la UI.
 * Arranca la app, recorre las pestañas y compara 29 capturas con las referencias de src/test/resources/capturas.
 *
 * - Primera ejecución: graba las referencias y pasa. Con -Dcapturas.regrabar=true se regrabarán a propósito
 *   (solo tras un cambio visual intencionado; nunca para «hacer pasar» un rojo).
 * - Si una captura falla: target/capturas/<nombre>.png (nueva) y <nombre>.diff.png (diferencias en rojo).
 * - La app trabaja en target/harness (workingDirectory de surefire) con datos de red congelados en
 *   src/test/resources/fixture: se graban en la primera ejecución y después se reutilizan con fecha «ahora»,
 *   para que las cachés de disco parezcan frescas y la app no descargue nada.
 * - No toques ratón ni teclado mientras corre (~1,5 min): Robot fotografía la pantalla, no la ventana.
 *   Las referencias dependen de la resolución, el escalado de Windows y las fuentes de esta máquina.
 */
// Solo corre si se pide (-Dharness=si, lo pone verificar.ps1 completo): un `mvn test` a secas, lanzado por un
// subagente o a mano, no puede quitarle la pantalla a Jorge por sorpresa.
@EnabledIfSystemProperty(named = "harness", matches = "si")
class RegresionCapturas {
    static final Path BASE = Path.of(System.getProperty("basedir", "."));
    static final Path HARNESS = Path.of(System.getProperty("user.dir"));
    static final Path RECURSOS = BASE.resolve("src/main/resources");
    static final Path FIXTURE = BASE.resolve("src/test/resources/fixture");
    static final List<String> FALLOS = new ArrayList<>();
    static final List<String> REINTENTADAS = new ArrayList<>();
    static final int INTENTOS = 3;
    static final Map<String, Double> UMBRALES = Map.of();
    static ComparadorCapturas comparador;
    static boolean grabarFixture;
    static int fotos;

    @BeforeAll static void prepararDirectorio() throws IOException {
        if (!HARNESS.endsWith(Path.of("target", "harness")))
            throw new IllegalStateException("el harness debe correr en target/harness (workingDirectory de surefire), no en " + HARNESS);
        AvisoHarness.empezar(BASE);   // pitido y cartel rojo: a partir de aquí la pantalla es del harness
        try (Stream<Path> s = Files.list(HARNESS)) { for (Path p : s.toList()) borrar(p); }
        copiar(RECURSOS.resolve("banderas"), HARNESS.resolve("banderas"));
        copiar(RECURSOS.resolve("techtree"), HARNESS.resolve("techtree"));
        grabarFixture = !Files.isDirectory(FIXTURE);
        if (!grabarFixture) {
            copiar(FIXTURE, HARNESS);
            renombrarEloHace7();
            FileTime ahora = FileTime.fromMillis(System.currentTimeMillis());
            try (Stream<Path> s = Files.walk(HARNESS)) { for (Path p : s.toList()) Files.setLastModifiedTime(p, ahora); }
            guardarConfig(Map.of("techtree_check", String.valueOf(System.currentTimeMillis())));
        }
        guardarConfig(Map.of("idioma", "es", "tema", "oscuro"));   // siempre: con «sistema» las capturas dependerían del tema de Windows
        String umbralFijo = System.getProperty("capturas.umbral");   // diagnóstico: -Dcapturas.umbral=0 deja el diff de todas para ver el ruido
        comparador = new ComparadorCapturas(BASE.resolve("src/test/resources/capturas"), BASE.resolve("target/capturas"),
                Boolean.getBoolean("capturas.regrabar"), UMBRALES, umbralFijo == null ? null : Double.valueOf(umbralFijo));
    }

    @Test void capturas() throws Exception {
        correr();
        if (!REINTENTADAS.isEmpty()) System.out.println("AVISO: capturas que pasaron al reintentar: " + REINTENTADAS);
        assertTrue(fotos == 29, "se esperaban 29 capturas y se hicieron " + fotos);
        assertTrue(FALLOS.isEmpty(), FALLOS.size() + " capturas distintas de su referencia:\n  " + String.join("\n  ", FALLOS));
    }

    @AfterAll static void cerrar() throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> { for (Window w : Window.getWindows()) w.dispose(); });   // dispose, no System.exit: mataría el JVM de surefire
            // los hilos de la app (socket, descargas) siguen vivos: si uno escribe en sfrdata/ justo ahora, la fixture
            // podría salir a medias. Solo afecta a la grabación; si una captura falla tras grabar, se borra y se regraba.
            if (grabarFixture) grabarFixture();
        } finally {
            AvisoHarness.terminar(BASE);   // dos pitidos y cartel verde, también si algo falló
        }
    }

    /**
     * Congela los datos de red que la app dejó en disco: sfrdata/ y lo que cambió en techtree/ respecto a
     * src/main/resources. De la config, solo las claves de datos (etags) más idioma y tema: el estado de la UI
     * (familia del ladder, anchos de tabla, grupos…) es el del final de la ejecución y cambiaría el arranque.
     * top_cache.txt no: lleva su fecha dentro y la app lo refresca por red pasados 15 min (se crea durante la ejecución).
     */
    static void grabarFixture() throws IOException {
        for (String dir : List.of("sfrdata", "techtree")) {
            try (Stream<Path> s = Files.walk(HARNESS.resolve(dir))) {
                for (Path p : s.filter(Files::isRegularFile).toList()) {
                    Path rel = HARNESS.relativize(p);
                    Path original = RECURSOS.resolve(rel);
                    if (Files.exists(original) && Files.mismatch(original, p) == -1) continue;
                    Files.createDirectories(FIXTURE.resolve(rel).getParent());
                    Files.copy(p, FIXTURE.resolve(rel), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Properties todo = new Properties(), datos = new Properties();
        try (InputStream in = Files.newInputStream(HARNESS.resolve("config.properties"))) { todo.load(in); }
        for (String k : todo.stringPropertyNames())
            if (k.startsWith("sfrdata_etag_") || k.startsWith("techtree_") || k.equals("idioma") || k.equals("tema"))
                datos.setProperty(k, todo.getProperty(k));
        try (OutputStream out = Files.newOutputStream(FIXTURE.resolve("config.properties"))) { datos.store(out, "harness: datos congelados"); }
        System.out.println("fixture grabada en " + FIXTURE);
    }

    /**
     * cargarEloAyer() pide «elo-<hoy UTC − 7>.json.gz»: el nombre cambia cada día. La copia congelada se renombra
     * a la fecha que la app va a pedir hoy; si no, mañana iría a la red.
     */
    static void renombrarEloHace7() throws IOException {
        Path dir = HARNESS.resolve("sfrdata/perfiles_shards");
        if (!Files.isDirectory(dir)) return;
        Path destino = dir.resolve("elo-" + java.time.LocalDate.now(java.time.ZoneId.of("UTC")).minusDays(7) + ".json.gz");
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.filter(q -> q.getFileName().toString().matches("elo-\\d{4}-\\d{2}-\\d{2}\\.json\\.gz")).toList())
                if (!p.equals(destino)) Files.move(p, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void guardarConfig(Map<String, String> valores) throws IOException {
        Path f = HARNESS.resolve("config.properties");
        Properties p = new Properties();
        if (Files.exists(f)) try (InputStream in = Files.newInputStream(f)) { p.load(in); }
        p.putAll(valores);
        try (OutputStream out = Files.newOutputStream(f)) { p.store(out, "harness"); }
    }

    static void copiar(Path origen, Path destino) throws IOException {
        try (Stream<Path> s = Files.walk(origen)) {
            for (Path p : s.toList()) {
                Path d = destino.resolve(origen.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(d);
                else Files.copy(p, d, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static void borrar(Path p) throws IOException {
        try (Stream<Path> s = Files.walk(p)) { for (Path q : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(q); }
    }

    /** Zona a ignorar: solo la parte visible del componente (si está desplazado, su rectángulo entero taparía otras cosas). */
    static void zonaVisible(JComponent c, JRootPane raiz, List<Rectangle> zonas) {
        if (c == null || !c.isShowing()) return;
        Rectangle v = c.getVisibleRect();
        if (!v.isEmpty()) zonas.add(SwingUtilities.convertRectangle(c, v, raiz));
    }

    static SpoilerFreeRecs app() {
        for (Frame f : Frame.getFrames()) if (f instanceof SpoilerFreeRecs s) return s;
        return null;
    }
    static void cerrarDialogos() throws Exception {
        SwingUtilities.invokeAndWait(() -> { for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isVisible()) d.dispose(); });
    }
    /**
     * Fotografía el JRootPane, no getBounds() del marco: en Windows 10 el marco incluye un borde invisible
     * de 7 px (izquierda, derecha, abajo) y Robot captaría el escritorio que hay detrás.
     * ignorar: componentes con datos en directo (se excluyen de la comparación), más la última fila de píxeles.
     */
    static void foto(String nombre) throws Exception { foto(nombre, () -> new JComponent[0]); }
    static void foto(String nombre, java.util.function.Supplier<JComponent[]> ignorar) throws Exception {
        SpoilerFreeRecs app = app();
        // Hasta INTENTOS fotos separadas 1 s: un panel de Windows o un repintado tardío desaparecen en el siguiente
        // intento; un cambio real del código no, y sigue en rojo. Las que necesitan reintento se listan al final.
        ComparadorCapturas.Resultado res = null;
        String[] estado = new String[1];
        int intento = 0;
        while (intento < INTENTOS && (res == null || !res.ok())) {
            if (intento++ > 0) Thread.sleep(1000);
            Rectangle[] r = new Rectangle[1];
            List<Rectangle> zonas = new ArrayList<>();
            SwingUtilities.invokeAndWait(() -> {
                estado[0] = estadoWatch(app);
                JRootPane raiz = app.getRootPane();
                r[0] = new Rectangle(raiz.getLocationOnScreen(), raiz.getSize());
                zonas.add(new Rectangle(0, raiz.getHeight() - 1, raiz.getWidth(), 1));   // línea del marco de Windows: cambia de color si un diálogo quita el foco
                // lo mismo con el marco nativo de los diálogos abiertos y su sombra (8 px fuera): dependen de qué ventana tenga el foco
                Point origen = raiz.getLocationOnScreen();
                for (Window w : Window.getWindows())
                    if (w instanceof JDialog d && d.isShowing()) {
                        Rectangle fuera = d.getBounds(); fuera.grow(8, 8);
                        Rectangle dentro = new Rectangle(d.getRootPane().getLocationOnScreen(), d.getRootPane().getSize());
                        fuera.translate(-origen.x, -origen.y); dentro.translate(-origen.x, -origen.y);
                        dentro.height -= 1;   // su última fila es borde de Windows, como en el marco principal
                        zonas.add(new Rectangle(fuera.x, fuera.y, fuera.width, dentro.y - fuera.y));                                  // arriba
                        zonas.add(new Rectangle(fuera.x, dentro.y + dentro.height, fuera.width, fuera.y + fuera.height - dentro.y - dentro.height));   // abajo
                        zonas.add(new Rectangle(fuera.x, fuera.y, dentro.x - fuera.x, fuera.height));                                 // izquierda
                        zonas.add(new Rectangle(dentro.x + dentro.width, fuera.y, fuera.x + fuera.width - dentro.x - dentro.width, fuera.height));    // derecha
                    }
                // dependen de la fecha del día (LocalDate.now() en actPintar, sin reloj inyectable: DEUDA.md): mañana se
                // desplazarían aunque nada cambie. Se ignoran donde se vean; la gráfica por horas sí se compara.
                for (JComponent c : new JComponent[]{ app.perfil.actCalendario, app.perfil.actSemana, app.perfil.actMeses })
                    zonaVisible(c, raiz, zonas);
                for (JComponent c : ignorar.get()) zonaVisible(c, raiz, zonas);   // se resuelven aquí, en el EDT
            });
            BufferedImage img = new Robot().createScreenCapture(r[0]);
            res = comparador.comparar(nombre.replaceFirst("\\.png$", ""), img, zonas);
            System.out.println("foto " + nombre + " intento " + intento + " · " + (res.ok() ? "ok" : "FALLA") + " · " + res.detalle() + " · " + estado[0]);
        }
        fotos++;
        if (!res.ok()) FALLOS.add(res.nombre() + ": " + res.detalle());
        else if (intento > 1) REINTENTADAS.add(res.nombre() + " (intento " + intento + ")");
    }

    /** Lo que la app cree de la watchlist en el momento de la foto (diagnóstico de shot_menu y compañía). En el EDT. */
    static String estadoWatch(SpoilerFreeRecs app) {
        List<Long> ids = new ArrayList<>();
        for (var p : app.todosJugadores) ids.add(p.id());
        return "watch: vivo(1)=" + SpoilerFreeRecs.VIVO.matchDe(1L) + " todos=" + ids + " modelo=" + app.playersModel.size()
                + " grupo=" + app.grupoActivo() + " top=" + app.modoTop()
                + " titulo='" + (app.tituloWatch == null ? null : app.tituloWatch.getTitle()) + "'"
                + " resumen='" + (app.resumenWatch == null ? null : app.resumenWatch.getText()) + "'";
    }
    /** Un jugador de una partida inventada (T3-H, sin red): igual de plano que un MatchPlayer real de la API. */
    static MatchPlayer mp(long id, String nombre, String civ, int equipo, Boolean gano, Integer rating, Integer diff) {
        MatchPlayer p = new MatchPlayer();
        p.id = id; p.name = nombre; p.civ = civ; p.team = equipo; p.won = gano; p.rating = rating; p.ratingDiff = diff;
        return p;
    }
    /** Una partida inventada con fecha FIJA (no relativa a «ahora»): la columna Fecha pinta «dd/MM HH:mm»
     *  (FechaCell, sin reloj inyectable) y con instantes fijos la foto no cambia entre ejecuciones ni de un día
     *  para otro. */
    static Match partida(long id, java.time.Instant inicio, int minutos, String modo, String mapa, MatchPlayer... jugadores) {
        Match m = new Match();
        m.id = id; m.started = inicio; m.finished = inicio.plusSeconds(minutos * 60L);
        m.mode = modo; m.map = mapa;
        for (MatchPlayer p : jugadores) m.players.add(p);
        return m;
    }
    /** configBtn es una variable LOCAL de construirBarraSuperior (no un campo de la ventana): se busca por su
     *  texto en el árbol de componentes en vez de exponerlo como campo (la tarea prohíbe tocar src/main). */
    static JButton buscarBoton(Container raiz, String texto) {
        for (Component c : raiz.getComponents()) {
            if (c instanceof JButton b && texto.equals(b.getText())) return b;
            if (c instanceof Container hijo) { JButton r = buscarBoton(hijo, texto); if (r != null) return r; }
        }
        return null;
    }

    static void correr() throws Exception {
        SpoilerFreeRecs.main(new String[0]);
        for (int i = 0; i < 60 && app() == null; i++) Thread.sleep(250);
        Thread.sleep(3000);
        SpoilerFreeRecs app = app();
        SwingUtilities.invokeAndWait(() -> { app.setSize(1500, 950); app.setLocation(0, 0); app.validate(); });
        // el cursor encima de la app provoca hovers y tooltips (p. ej. la ficha de una celda de la matriz, que salió en
        // shot_matriz_grande): se aparca fuera de la zona fotografiada (la app ocupa 0..1500 x 0..950 del monitor principal).
        // DESPUÉS de arrancar la app: crear un Robot antes inicializa Java2D y cambia el escalado de iconos (lo mismo que obligó a reuseForks=false).
        Rectangle pantalla = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        new Robot().mouseMove(pantalla.x + pantalla.width - 5, pantalla.y + pantalla.height - 5);
        Thread.sleep(500);
        Paises.PAIS_DE.put(1L, "es"); Paises.PAIS_DE.put(2L, "es"); Paises.PAIS_DE.put(3L, "ar"); Paises.PAIS_DE.put(4L, "de");
        SwingUtilities.invokeAndWait(() -> { app.grupoCombo.setSelectedItem("Todos"); app.playersModel.addElement(new Player(1L, "12Tirador", "", 0L)); app.playersModel.addElement(new Player(2L, "Turpiacho", "", 0L)); app.playersModel.addElement(new Player(3L, "pume", "", 0L)); app.eloWatch.put(1L, 1905); app.eloWatch.put(2L, 1610); app.eloWatch.put(3L, 1980); app.playersList.repaint(); });
        Thread.sleep(400);
        // la vista de arranque es Twitch: canales, miniaturas y espectadores en directo, imposibles de congelar.
        // Se ignora la fila de cabecera entera (padre de directosContador), no solo sus etiquetas: el ancho de
        // «N espectadores · Actualizado hh:mm:ss» desplaza el desplegable Idioma y el botón Refrescar.
        foto("shot_watchlist.png", () -> new JComponent[]{ (JComponent) SwingUtilities.getAncestorOfClass(JScrollPane.class, app.directos.tablaDirectos), (JComponent) app.directos.directosContador.getParent() });
        SwingUtilities.invokeAndWait(() -> app.ladderBtn.doClick());
        for (int i = 0; i < 80 && (Ladder.ladderHists.isEmpty()); i++) Thread.sleep(250);
        Thread.sleep(1500);
        // Carrera de la app (DEUDA.md): al abrir el ladder programa «tabla de 230 px» solo si el divisor ya tiene alto.
        // Casi siempre aún no lo tiene y la tabla queda plegada (el estado de las referencias); a veces sí, y cambian
        // las 7 capturas del ladder. Se fija el reparto por defecto para que no dependa de ese orden.
        SwingUtilities.invokeAndWait(() -> app.ratings.ladderDivisor.resetToPreferredSizes());
        Thread.sleep(300);
        foto("shot_ladder_vacio.png");
        SwingUtilities.invokeAndWait(() -> {
            app.ratings.ladderComparados.add(new Comparado(1L, "12Tirador", Map.of("rm_1v1", new int[]{ 1905, 260 }, "rm_team", new int[]{ 2110, 800 }, "ew_1v1", new int[]{ 1400, 300 }), "es", true));
            app.ratings.ladderComparados.add(new Comparado(2L, "Turpiacho", Map.of("rm_1v1", new int[]{ 1610, 2800 }, "rm_team", new int[]{ 1750, 9000 }), "es", true));
            app.ratings.ladderComparados.add(new Comparado(3L, "pume", Map.of("rm_1v1", new int[]{ 1980, 120 }, "rm_team", new int[]{ 1900, 4000 }), "es", false));
            app.ratings.ladderComparados.add(new Comparado(4L, "novato", Map.of("rm_1v1", new int[]{ 760, 200000 }, "rm_team", new int[]{ 900, 250000 }), "de", false));
            app.ratings.ladderRefrescar();
        });
        Thread.sleep(1200);
        cerrarDialogos();
        SwingUtilities.invokeAndWait(app::repaint);
        Thread.sleep(800);
        foto("shot_ladder_activos.png");
        SwingUtilities.invokeAndWait(() -> { app.ratings.activosCheck.setSelected(false); app.ratings.soloActivos = false; app.ratings.ladderRefrescar(); });
        Thread.sleep(800);
        foto("shot_ladder_todos.png");
        SwingUtilities.invokeAndWait(() -> { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, app.ratings.dispersion); sc.getVerticalScrollBar().setValue(sc.getVerticalScrollBar().getMaximum()); });
        Thread.sleep(600);
        foto("shot_ladder_dispersion.png");
        SwingUtilities.invokeAndWait(() -> { app.ratings.escalarRatings(0.5); });
        Thread.sleep(800);
        foto("shot_ratings_zoom.png");
        SwingUtilities.invokeAndWait(() -> { app.ratings.escalarRatingsReset(); app.ratings.familiaCombo.setSelectedIndex(1); });
        Thread.sleep(800);
        foto("shot_ladder_ew.png");
        // ----- Civ Stats
        SwingUtilities.invokeAndWait(() -> app.civStatsBtn.doClick());
        for (int i = 0; i < 120 && !CivStats.VENTANAS_STATS.containsKey(app.filtroStats.ventana()); i++) Thread.sleep(250);
        Thread.sleep(2500);
        cerrarDialogos();
        foto("shot_civstats_arriba.png");
        SwingUtilities.invokeAndWait(() -> { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, app.civStats.stTendencias); sc.getVerticalScrollBar().setValue(560); });
        Thread.sleep(700);
        foto("shot_civstats_tendencias.png");
        SwingUtilities.invokeAndWait(() -> { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, app.civStats.stTendencias); sc.getVerticalScrollBar().setValue(sc.getVerticalScrollBar().getMaximum()); });
        Thread.sleep(700);
        foto("shot_civstats_matriz.png");
        SwingUtilities.invokeAndWait(() -> { app.civStats.stRango.rango("*"); app.civStats.stRango.alCambiar.accept("*"); app.civStats.stMapaCombo.setSelectedIndex(0); });
        Thread.sleep(1200);
        System.out.println("stats filas tabla: " + app.civStats.stModelo.getRowCount() + " | fila0: " + java.util.Arrays.toString(app.civStats.stModelo.getDataVector().get(0).toArray()) + " | tramo=" + app.filtroStats.tramo() + " mapa=" + app.filtroStats.mapa());
        // ----- Tech tree con WR
        SwingUtilities.invokeAndWait(() -> app.techTreeBtn.doClick());
        for (int i = 0; i < 160 && (TechTreeDatos.ttData == null || app.techTree.ttCivCombo.getItemCount() == 0); i++) Thread.sleep(250);
        Thread.sleep(4000);
        SwingUtilities.invokeAndWait(() -> app.techTree.ttCivCombo.setSelectedItem(TechTreeDatos.ttNombreCiv("Aztecs")));
        Thread.sleep(5000);
        cerrarDialogos();
        foto("shot_techtree_wr.png");
        SwingUtilities.invokeAndWait(() -> { app.techTree.ttPuestoBtn.doClick(); });
        Thread.sleep(900);
        foto("shot_techtree_ranking.png");
        SwingUtilities.invokeAndWait(() -> { for (Window w : Window.getWindows()) if (w instanceof JWindow jw) jw.setVisible(false); javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath(); });
        System.out.println("tt wr civs: " + app.techTree.ttWrPorCiv.size() + " | puesto: " + app.techTree.ttPuestoBtn.getText() + " | banda: " + app.techTree.ttWrLabel.getText().replaceAll("<[^>]+>", "").substring(0, 60));
        // ----- Actividad (historial sintético en caché: la API está bloqueada aquí)
        java.util.Random rnd = new java.util.Random(3);
        java.util.List<Match> ms = new java.util.ArrayList<>();
        String[] civsS = { "Aztecas", "Francos", "Mayas", "Hunos", "Britanos", "Mongoles", "Vikingos", "Romanos" };
        String[] mapsS = { "Arabia", "Arena", "Bosque Negro", "Cuatro Lagos", "Nómada", "Acrópolis" };
        String[] rivS = { "pume", "Turpiacho", "Viper", "Hera", "Liereyy", "DauT", "Vinchester", "Yo", "Nicov", "Mr_Yo", "Tatoh", "JorDan", "Capoch" };
        for (int i = 0; i < 420; i++) {
            Match m = new Match();
            m.id = 1000 + i;
            int dias = (int) Math.min(364, Math.abs(rnd.nextGaussian()) * 120);
            java.time.ZonedDateTime z = java.time.ZonedDateTime.now().minusDays(dias).withHour(rnd.nextInt(24) < 8 ? 22 : 19 + rnd.nextInt(5)).withMinute(rnd.nextInt(60));
            m.started = z.toInstant(); m.finished = z.plusMinutes(15 + rnd.nextInt(40)).toInstant();
            boolean equipo = rnd.nextInt(10) < 3;
            m.mode = equipo ? "Team Random Map" : "1v1 Random Map"; m.map = mapsS[rnd.nextInt(mapsS.length)];
            boolean gano = rnd.nextInt(100) < 53;
            MatchPlayer yo = new MatchPlayer(); yo.id = 1L; yo.name = "12Tirador"; yo.civ = civsS[rnd.nextInt(civsS.length)]; yo.team = 1; yo.won = gano; yo.rating = 1850 + rnd.nextInt(120);
            m.players.add(yo);
            int nRiv = equipo ? 3 : 1;
            for (int k = 0; k < (equipo ? 2 : 0); k++) { MatchPlayer al = new MatchPlayer(); al.id = 500 + rnd.nextInt(6); al.name = "aliado" + al.id; al.civ = civsS[rnd.nextInt(civsS.length)]; al.team = 1; al.won = gano; al.rating = 1700 + rnd.nextInt(300); m.players.add(al); }
            for (int k = 0; k < nRiv; k++) { MatchPlayer r = new MatchPlayer(); int ri = rnd.nextInt(rivS.length); r.id = 100 + ri; r.name = rivS[ri]; r.civ = civsS[rnd.nextInt(civsS.length)]; r.team = 2; r.won = !gano; r.rating = 1500 + rnd.nextInt(700); m.players.add(r); }
            ms.add(m);
        }
        HistorialDisco.ACTIVIDAD_CACHE.put(1L, new Actividad(1L, "12Tirador", ms, true, 9, System.currentTimeMillis()));
        CachePerfiles.PERFIL_CACHE.poner(1L, new FichaPerfil(Map.of("rm_1v1", new int[]{ 1905, 260, 1960, 1240, 1100 }, "rm_team", new int[]{ 2110, 800, 2150, 800, 600 }, "ew_1v1", new int[]{ 1400, 300, 1450, 40, 30 }), "es", "TSK", 3810L));
        SwingUtilities.invokeAndWait(() -> app.perfilBtn.doClick());
        Thread.sleep(800);
        foto("shot_perfil_vacio.png");
        SwingUtilities.invokeAndWait(() -> app.abrirPerfil(1L, "12Tirador"));
        Thread.sleep(2500);
        cerrarDialogos();
        foto("shot_actividad.png");
        HistorialDisco.ACTIVIDAD_CACHE.put(2L, new Actividad(2L, "Turpiacho", ms, true, 9, System.currentTimeMillis()));
        CachePerfiles.PERFIL_CACHE.poner(2L, new FichaPerfil(Map.of("rm_1v1", new int[]{ 1610, 2800, 1700, 900, 800 }), "es", "", 1700L));
        SwingUtilities.invokeAndWait(() -> app.abrirPerfilEnPestana(2L, "Turpiacho"));
        Thread.sleep(1200);
        foto("shot_perfil_pestanas.png");
        SwingUtilities.invokeAndWait(() -> app.abrirTechTree("aztecs"));
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(app::volverAtras);
        Thread.sleep(800);
        System.out.println("tras volver: perfil abierto=" + app.perfil.abierto() + " pid=" + app.perfil.pidAbierto() + " pestañas=" + app.perfil.perfilPestanas.size() + " activa=" + app.perfil.perfilPestanaActiva + " historial=" + app.historial.size());
        foto("shot_perfil_atras.png");
        SwingUtilities.invokeAndWait(app::irAdelante);
        Thread.sleep(600);
        System.out.println("tras adelante: techtree=" + app.techTreeBtn.isSelected() + " pos=" + app.historialPos + "/" + app.historial.size());
        // «Ahora»: inyectar top y partidas en curso para ver la tabla
        Match mv = new Match(); mv.id = 555; mv.started = java.time.Instant.now().minusSeconds(900); mv.map = "Arabia"; mv.mode = "1v1 Random Map";
        MatchPlayer a1 = new MatchPlayer(); a1.id = 1; a1.name = "12Tirador"; a1.civ = "Aztecas"; a1.team = 1; a1.rating = 1905;
        MatchPlayer a2 = new MatchPlayer(); a2.id = 3; a2.name = "pume"; a2.civ = "Francos"; a2.team = 2; a2.rating = 1980;
        mv.players.add(a1); mv.players.add(a2);
        app.liveNow.conTop(top -> { top.add(new Object[]{ 1L, "12Tirador", 1905, 260, "es" }); top.add(new Object[]{ 3L, "pume", 1980, 120, "ar" }); top.add(new Object[]{ 2L, "Turpiacho", 1610, 2800, "es" }); });
        app.liveNow.fijarTopMs(System.currentTimeMillis());
        app.liveNow.conEnCurso(enCurso -> { enCurso.put(1L, mv); enCurso.put(3L, mv); });
        app.liveNow.fijarUltimaMs(System.currentTimeMillis());
        app.liveNow.conTerminadas(terminadas -> { Match mt = new Match(); mt.id = 556; mt.started = java.time.Instant.now().minusSeconds(3000); mt.finished = java.time.Instant.now().minusSeconds(600); mt.map = "Arena"; mt.mode = "1v1 Random Map"; mt.players.add(a1); mt.players.add(a2); terminadas.put(556L, new Object[]{ mt, System.currentTimeMillis() - 600_000 }); });
        SwingUtilities.invokeAndWait(() -> { app.ahoraBtn.doClick(); });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> app.mostrarToast("\u25CF Hera ha empezado una partida \u00B7 vs Viper 2732 (Mongoles\u2013Francos) \u00B7 Arabia", 555));
        Thread.sleep(700);
        foto("shot_ahora.png");
        // el aviso existe para shot_ahora; su temporizador (10 s) lo cerraría en mitad de las capturas siguientes y cuáles
        // lo muestran dependería del tiempo transcurrido. Se cierra ya, con el método que usa el propio temporizador.
        SwingUtilities.invokeAndWait(() -> { if (app.toastTimer != null) app.toastTimer.stop(); app.ocultarToast(); });
        System.out.println("live tarjetas: " + app.liveNow.ahoraCuerpoPanel().getComponentCount() + " | estado: " + app.liveNow.ahoraEstadoLabel().getText());
        SwingUtilities.invokeAndWait(app.liveNow::mostrarLista250);
        Thread.sleep(800);
        foto("shot_lista250.png");
        cerrarDialogos();
        // Fin de Live now: la partida inventada 555 deja de estar en curso. Antes lo hacía, por casualidad, un barrido de
        // reparación contra la red real que disparaba un fallo del socket (el cierre pedido por la app contaba como caída y
        // reconectaba; arreglado en LiveService C2). Sin él, la 555 seguía «en partida» con un cronómetro en marcha. El
        // harness deja él mismo el estado que fotografía: sin depender de la red ni de aquel fallo.
        SwingUtilities.invokeAndWait(() -> { app.liveNow.conEnCurso(Map::clear); app.liveNow.pintar(); });
        // menú contextual de la watchlist sobre un jugador en partida
        SpoilerFreeRecs.VIVO.marcarJugando(1L, 555L);
        SwingUtilities.invokeAndWait(() -> { app.todosJugadores.add(new Player(1L, "12Tirador", "General")); app.todosJugadores.add(new Player(3L, "pume", "General")); app.rebuildGrupos(); app.grupoCombo.setSelectedItem("Todos"); app.aplicarFiltroGrupo(); app.playersList.setSelectedIndex(0); });
        // La partida 555 es inventada: un refresco de la propia app la quita en ~1 s y el panel pasa a «0 jugando»
        // (registrado: vivo(1)=null en la foto en 5 de 5 pasadas). Si el refresco tardaba, la foto pillaba el estado
        // intermedio («1 jugando»). Se espera a que termine para fotografiar siempre el estado final. DEUDA.md.
        boolean[] enPartida = { true };
        for (int i = 0; i < 40 && enPartida[0]; i++) { Thread.sleep(250); SwingUtilities.invokeAndWait(() -> enPartida[0] = SpoilerFreeRecs.VIVO.jugando(1L)); }
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> { Rectangle r = app.playersList.getCellBounds(0, 0); app.menuContextualWatchlist(app.playersModel.get(0), new java.awt.event.MouseEvent(app.playersList, java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, r.x + 40, r.y + 8, 1, true)); });
        Thread.sleep(700);
        foto("shot_menu.png");
        SwingUtilities.invokeAndWait(() -> javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath());
        SwingUtilities.invokeAndWait(() -> { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, app.perfil.actCalendario); sc.getVerticalScrollBar().setValue(sc.getVerticalScrollBar().getMaximum()); });
        Thread.sleep(700);
        foto("shot_actividad_abajo.png");
        System.out.println("actividad estado: " + app.perfil.actEstado.getText() + " | título: " + app.getTitle());
        System.out.println("tabla filas: " + app.ratings.ladderModelo.getRowCount() + " | fila0: " + java.util.Arrays.toString(app.ratings.ladderModelo.getDataVector().get(0).toArray()));
        System.out.println("pct rango 12Tirador todos rm_1v1: " + ConsultasLadder.percentilRango("rm_1v1", 260) + " | por rating activos: " + ConsultasLadder.percentilRating("rm_1v1", true, 1905) + " | novato: " + ConsultasLadder.percentilRating("rm_1v1", true, 760));
        SwingUtilities.invokeAndWait(() -> { app.civStats.stRango.rango("1600-1800|*"); app.civStats.stRango.alCambiar.accept("1600-1800|*"); app.civStats.stMapaCombo.setSelectedIndex(3); });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> app.civStatsBtn.doClick());
        Thread.sleep(1500);
        foto("shot_civstats_2000_mapa.png");
        SwingUtilities.invokeAndWait(() -> { app.civStats.stRango.rango("*"); app.civStats.stRango.alCambiar.accept("*"); app.civStats.stMapaCombo.setSelectedIndex(0); });
        Thread.sleep(1200);
        SwingUtilities.invokeAndWait(app.civStats::mostrarMatrizGrande);
        Thread.sleep(1500);
        foto("shot_matriz_grande.png");
        cerrarDialogos();
        SwingUtilities.invokeAndWait(() -> app.listas.mostrarListaCompleta("prueba", cuerpo -> { for (int i = 0; i < 30; i++) cuerpo.add(app.listas.filaBarra("fila " + i, i / 30.0, "50 %", Color.GRAY, null)); }));
        Thread.sleep(800);
        foto("shot_lista_completa.png");
        cerrarDialogos();
        System.out.println("civstats 2000+ mapa: filas " + app.civStats.stModelo.getRowCount() + " | estado: " + app.civStats.stEstado.getText() + " | mapa=" + app.filtroStats.mapa() + " tramo=" + app.filtroStats.tramo());

        // ===== T3-H: PARTIDAS y CROMO — hoy sin ninguna captura; se fotografía el comportamiento ACTUAL (base
        // 26b29c7) antes de extraer la vista. Se inyecta en `all`/`view` por el MISMO camino que usa
        // fetchMatches.done() (SpoilerFreeRecs ~5551-5566): limpiar SUJETOS, pintar la cabecera «Partidas de:»
        // con refrescarSujetos(...), volcar en `all` y dejar que refreshModeCombo()/applyFilters() hagan el
        // resto (asignan refId, marcan enDisco/enJuego, llenan `view`). Nada de red: no se toca fetchBtn.
        SwingUtilities.invokeAndWait(() -> app.mostrarDirectos(false));   // pestaña Partidas; con `all` vacío enseña la guía
        Thread.sleep(400);
        foto("shot_guia.png");
        java.time.Instant basePartidas = java.time.Instant.parse("2024-03-10T18:00:00Z");
        java.util.List<Match> partidasInventadas = new java.util.ArrayList<>(java.util.List.of(
                partida(8001, basePartidas, 28, "1v1 Random Map", "Arabia",
                        mp(1L, "12Tirador", "Aztecas", 1, true, 1905, 18),
                        mp(9101L, "Viper", "Britanos", 2, false, 2200, -14)),
                partida(8002, basePartidas.plusSeconds(86400), 27, "1v1 Random Map", "Arena",
                        mp(3L, "pume", "Francos", 1, false, 1980, -9),
                        mp(9102L, "Hera", "Mongoles", 2, true, 2100, 11)),
                partida(8003, basePartidas.plusSeconds(2 * 86400), 45, "Team Random Map", "Cuatro Lagos",
                        mp(2L, "Turpiacho", "Mayas", 1, true, 1610, 15),
                        mp(9103L, "DauT", "Hunos", 1, true, 1700, null),
                        mp(9104L, "Nicov", "Vikingos", 2, false, 1650, null),
                        mp(9105L, "Tatoh", "Romanos", 2, false, 1600, null)),
                partida(8004, basePartidas.plusSeconds(3 * 86400), 33, "1v1 Random Map", "Bosque Negro",
                        mp(1L, "12Tirador", "Mongoles", 1, false, 1890, -15),
                        mp(3L, "pume", "Francos", 2, true, 1995, 15)),
                partida(8005, basePartidas.plusSeconds(4 * 86400), 40, "1v1 Random Map", "Nómada",
                        mp(2L, "Turpiacho", "Vikingos", 1, true, 1625, 15),
                        mp(9106L, "Liereyy", "Britanos", 2, false, 1500, -10)),
                partida(8006, basePartidas.plusSeconds(5 * 86400), 52, "Team Random Map", "Acrópolis",
                        mp(1L, "12Tirador", "Hunos", 1, true, 1920, 12),
                        mp(9107L, "Vinchester", "Mayas", 1, true, 1750, null),
                        mp(9108L, "JorDan", "Aztecas", 1, true, 1680, null),
                        mp(9109L, "Capoch", "Francos", 2, false, 1700, null),
                        mp(9110L, "Mr_Yo", "Romanos", 2, false, 1650, null),
                        mp(9111L, "Yo", "Mongoles", 2, false, 1600, null)),
                partida(8007, basePartidas.plusSeconds(6 * 86400), 31, "1v1 Random Map", "Isla",
                        mp(3L, "pume", "Romanos", 1, true, 2000, 20),
                        mp(9112L, "Tatoh", "Hunos", 2, false, 1900, -18))
        ));
        // mismo orden que produce fetchMatches.doInBackground(): la más reciente primero
        partidasInventadas.sort(java.util.Comparator.comparing((Match m) -> m.finished).reversed());
        java.util.List<Player> sujetosPartidas = java.util.List.of(
                new Player(1L, "12Tirador", "", 0L), new Player(2L, "Turpiacho", "", 0L), new Player(3L, "pume", "", 0L));
        SwingUtilities.invokeAndWait(() -> {
            SpoilerFreeRecs.SUJETOS.clear();
            app.filtroSujetos.clear();
            for (Player p : sujetosPartidas) SpoilerFreeRecs.SUJETOS.add(p.id());
            app.refrescarSujetos(sujetosPartidas, false);   // cabecera «Partidas de:», antes de llenar la tabla (igual que fetchMatches)
            app.all.clear();
            app.all.addAll(partidasInventadas);
            app.refreshModeCombo();
            app.applyFilters();
        });
        System.out.println("partidas filas tabla: " + app.tableModel.getRowCount());
        Thread.sleep(500);
        foto("shot_partidas.png");
        // shot_partidas_sujetos.png: NO hace falta. refrescarSujetos() ya pintó la cabecera «Partidas de:» antes
        // de esta foto (mismo orden que fetchMatches.done()), así que shot_partidas.png ya la enseña.
        SwingUtilities.invokeAndWait(() -> app.resultadosBtn.doClick());   // «Mostrar resultados»: el único punto con spoilers
        Thread.sleep(500);
        foto("shot_partidas_resultados.png");
        SwingUtilities.invokeAndWait(() -> app.resultadosBtn.doClick());   // vuelve a tapar antes de la foto del menú
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> {
            Rectangle r = app.table.getCellRect(0, 0, true);   // fila 0: la más reciente tras el orden por fecha
            app.table.dispatchEvent(new java.awt.event.MouseEvent(app.table, java.awt.event.MouseEvent.MOUSE_RELEASED,
                    System.currentTimeMillis(), 0, r.x + r.width / 2, r.y + r.height / 2, 1, true));   // popupTrigger=true: dispara el mismo menú que un clic derecho real
        });
        Thread.sleep(700);
        foto("shot_partidas_menu.png");
        SwingUtilities.invokeAndWait(() -> javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath());
        SwingUtilities.invokeAndWait(app::cerrarBusqueda);   // deja `all`/`view`/SUJETOS/cabecera como los encontró
        Thread.sleep(300);

        // ----- Cromo: menú Configuración y Acerca de (T3-H) — sin ninguna captura hasta ahora en esta zona.
        // El idioma del harness siempre es "es" (BeforeAll): el texto del botón es literal.
        JButton[] configBtnRef = new JButton[1];
        SwingUtilities.invokeAndWait(() -> configBtnRef[0] = buscarBoton(app.getContentPane(), "Configuración ▾"));
        SwingUtilities.invokeAndWait(() -> configBtnRef[0].doClick());
        Thread.sleep(500);
        foto("shot_config_menu.png");
        SwingUtilities.invokeAndWait(() -> javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath());
        Thread.sleep(200);
        // showAbout() abre un JOptionPane MODAL: con invokeAndWait, la propia llamada no volvería hasta cerrar
        // el diálogo (bloquearía el test). Se lanza con invokeLater y se espera a que la ventana aparezca.
        SwingUtilities.invokeLater(app::showAbout);
        JDialog[] acercaD = new JDialog[1];
        for (int i = 0; i < 60 && acercaD[0] == null; i++) {
            Thread.sleep(100);
            SwingUtilities.invokeAndWait(() -> {
                for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isVisible() && "Acerca de".equals(d.getTitle())) acercaD[0] = d;
            });
        }
        Thread.sleep(300);
        foto("shot_acerca.png");
        cerrarDialogos();

        // Se vuelve a Civ Stats (la pestaña activa antes de este bloque): mostrarDirectos(false) la había
        // deseleccionado. abrirCivStats() no pide nada por red: sfr-data ya está en caché de esta ejecución.
        SwingUtilities.invokeAndWait(() -> app.civStatsBtn.doClick());
        Thread.sleep(400);
    }
}
