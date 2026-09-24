package dev.tirador.aoe2radar;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
 * Arranca la app, recorre las pestañas y compara 23 capturas con las referencias de src/test/resources/capturas.
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
class RegresionCapturas {
    static final Path BASE = Path.of(System.getProperty("basedir", "."));
    static final Path HARNESS = Path.of(System.getProperty("user.dir"));
    static final Path RECURSOS = BASE.resolve("src/main/resources");
    static final Path FIXTURE = BASE.resolve("src/test/resources/fixture");
    static final List<String> FALLOS = new ArrayList<>();
    static final Map<String, Double> UMBRALES = Map.of();
    static ComparadorCapturas comparador;
    static boolean grabarFixture;
    static int fotos;

    @BeforeAll static void prepararDirectorio() throws IOException {
        if (!HARNESS.endsWith(Path.of("target", "harness")))
            throw new IllegalStateException("el harness debe correr en target/harness (workingDirectory de surefire), no en " + HARNESS);
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
        assertTrue(fotos == 23, "se esperaban 23 capturas y se hicieron " + fotos);
        assertTrue(FALLOS.isEmpty(), FALLOS.size() + " capturas distintas de su referencia:\n  " + String.join("\n  ", FALLOS));
    }

    @AfterAll static void cerrar() throws Exception {
        SwingUtilities.invokeAndWait(() -> { for (Window w : Window.getWindows()) w.dispose(); });   // dispose, no System.exit: mataría el JVM de surefire
        // los hilos de la app (socket, descargas) siguen vivos: si uno escribe en sfrdata/ justo ahora, la fixture
        // podría salir a medias. Solo afecta a la grabación; si una captura falla tras grabar, se borra y se regraba.
        if (grabarFixture) grabarFixture();
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
        Rectangle[] r = new Rectangle[1];
        List<Rectangle> zonas = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
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
            for (JComponent c : new JComponent[]{ app.actCalendario, app.actSemana, app.actMeses })
                zonaVisible(c, raiz, zonas);
            for (JComponent c : ignorar.get()) zonaVisible(c, raiz, zonas);   // se resuelven aquí, en el EDT
        });
        BufferedImage img = new Robot().createScreenCapture(r[0]);
        fotos++;
        ComparadorCapturas.Resultado res = comparador.comparar(nombre.replaceFirst("\\.png$", ""), img, zonas);
        if (!res.ok()) FALLOS.add(res.nombre() + ": " + res.detalle());
        System.out.println("foto " + nombre + " " + r[0] + " · " + (res.ok() ? "ok" : "FALLA") + " · " + res.detalle());
    }
    static void correr() throws Exception {
        SpoilerFreeRecs.main(new String[0]);
        for (int i = 0; i < 60 && app() == null; i++) Thread.sleep(250);
        Thread.sleep(3000);
        SpoilerFreeRecs app = app();
        SwingUtilities.invokeAndWait(() -> { app.setSize(1500, 950); app.setLocation(0, 0); app.validate(); });
        Thread.sleep(500);
        SpoilerFreeRecs.PAIS_DE.put(1L, "es"); SpoilerFreeRecs.PAIS_DE.put(2L, "es"); SpoilerFreeRecs.PAIS_DE.put(3L, "ar"); SpoilerFreeRecs.PAIS_DE.put(4L, "de");
        SwingUtilities.invokeAndWait(() -> { app.grupoCombo.setSelectedItem("Todos"); app.playersModel.addElement(new SpoilerFreeRecs.Player(1L, "12Tirador", "", 0L)); app.playersModel.addElement(new SpoilerFreeRecs.Player(2L, "Turpiacho", "", 0L)); app.playersModel.addElement(new SpoilerFreeRecs.Player(3L, "pume", "", 0L)); app.eloWatch.put(1L, 1905); app.eloWatch.put(2L, 1610); app.eloWatch.put(3L, 1980); app.playersList.repaint(); });
        Thread.sleep(400);
        // la vista de arranque es Twitch: canales, miniaturas y espectadores en directo, imposibles de congelar
        foto("shot_watchlist.png", () -> new JComponent[]{ (JComponent) SwingUtilities.getAncestorOfClass(JScrollPane.class, app.tablaDirectos), app.directosContador, app.directosHora });
        SwingUtilities.invokeAndWait(() -> app.ladderBtn.doClick());
        for (int i = 0; i < 80 && (SpoilerFreeRecs.ladderHists.isEmpty()); i++) Thread.sleep(250);
        Thread.sleep(1500);
        foto("shot_ladder_vacio.png");
        SwingUtilities.invokeAndWait(() -> {
            app.ladderComparados.add(new SpoilerFreeRecs.Comparado(1L, "12Tirador", Map.of("rm_1v1", new int[]{ 1905, 260 }, "rm_team", new int[]{ 2110, 800 }, "ew_1v1", new int[]{ 1400, 300 }), "es", true));
            app.ladderComparados.add(new SpoilerFreeRecs.Comparado(2L, "Turpiacho", Map.of("rm_1v1", new int[]{ 1610, 2800 }, "rm_team", new int[]{ 1750, 9000 }), "es", true));
            app.ladderComparados.add(new SpoilerFreeRecs.Comparado(3L, "pume", Map.of("rm_1v1", new int[]{ 1980, 120 }, "rm_team", new int[]{ 1900, 4000 }), "es", false));
            app.ladderComparados.add(new SpoilerFreeRecs.Comparado(4L, "novato", Map.of("rm_1v1", new int[]{ 760, 200000 }, "rm_team", new int[]{ 900, 250000 }), "de", false));
            app.ladderRefrescar();
        });
        Thread.sleep(1200);
        cerrarDialogos();
        SwingUtilities.invokeAndWait(app::repaint);
        Thread.sleep(800);
        foto("shot_ladder_activos.png");
        SwingUtilities.invokeAndWait(() -> { app.activosCheck.setSelected(false); app.soloActivos = false; app.ladderRefrescar(); });
        Thread.sleep(800);
        foto("shot_ladder_todos.png");
        SwingUtilities.invokeAndWait(() -> { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, app.dispersion); sc.getVerticalScrollBar().setValue(sc.getVerticalScrollBar().getMaximum()); });
        Thread.sleep(600);
        foto("shot_ladder_dispersion.png");
        SwingUtilities.invokeAndWait(() -> { app.escalarRatings(0.5); });
        Thread.sleep(800);
        foto("shot_ratings_zoom.png");
        SwingUtilities.invokeAndWait(() -> { app.escalarRatingsReset(); app.familiaCombo.setSelectedIndex(1); });
        Thread.sleep(800);
        foto("shot_ladder_ew.png");
        // ----- Civ Stats
        SwingUtilities.invokeAndWait(() -> app.civStatsBtn.doClick());
        for (int i = 0; i < 120 && !SpoilerFreeRecs.VENTANAS_STATS.containsKey(app.statsVentana); i++) Thread.sleep(250);
        Thread.sleep(2500);
        cerrarDialogos();
        foto("shot_civstats_arriba.png");
        SwingUtilities.invokeAndWait(() -> { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, app.stTendencias); sc.getVerticalScrollBar().setValue(560); });
        Thread.sleep(700);
        foto("shot_civstats_tendencias.png");
        SwingUtilities.invokeAndWait(() -> { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, app.stTendencias); sc.getVerticalScrollBar().setValue(sc.getVerticalScrollBar().getMaximum()); });
        Thread.sleep(700);
        foto("shot_civstats_matriz.png");
        SwingUtilities.invokeAndWait(() -> { app.stRango.rango("*"); app.stRango.alCambiar.accept("*"); app.stMapaCombo.setSelectedIndex(0); });
        Thread.sleep(1200);
        System.out.println("stats filas tabla: " + app.stModelo.getRowCount() + " | fila0: " + java.util.Arrays.toString(app.stModelo.getDataVector().get(0).toArray()) + " | tramo=" + app.statsTramo + " mapa=" + app.statsMapa);
        // ----- Tech tree con WR
        SwingUtilities.invokeAndWait(() -> app.techTreeBtn.doClick());
        for (int i = 0; i < 160 && (SpoilerFreeRecs.ttData == null || app.ttCivCombo.getItemCount() == 0); i++) Thread.sleep(250);
        Thread.sleep(4000);
        SwingUtilities.invokeAndWait(() -> app.ttCivCombo.setSelectedItem(app.ttNombreCiv("Aztecs")));
        Thread.sleep(5000);
        cerrarDialogos();
        foto("shot_techtree_wr.png");
        SwingUtilities.invokeAndWait(() -> { app.ttPuestoBtn.doClick(); });
        Thread.sleep(900);
        foto("shot_techtree_ranking.png");
        SwingUtilities.invokeAndWait(() -> { for (Window w : Window.getWindows()) if (w instanceof JWindow jw) jw.setVisible(false); javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath(); });
        System.out.println("tt wr civs: " + app.ttWrPorCiv.size() + " | puesto: " + app.ttPuestoBtn.getText() + " | banda: " + app.ttWrLabel.getText().replaceAll("<[^>]+>", "").substring(0, 60));
        // ----- Actividad (historial sintético en caché: la API está bloqueada aquí)
        java.util.Random rnd = new java.util.Random(3);
        java.util.List<SpoilerFreeRecs.Match> ms = new java.util.ArrayList<>();
        String[] civsS = { "Aztecas", "Francos", "Mayas", "Hunos", "Britanos", "Mongoles", "Vikingos", "Romanos" };
        String[] mapsS = { "Arabia", "Arena", "Bosque Negro", "Cuatro Lagos", "Nómada", "Acrópolis" };
        String[] rivS = { "pume", "Turpiacho", "Viper", "Hera", "Liereyy", "DauT", "Vinchester", "Yo", "Nicov", "Mr_Yo", "Tatoh", "JorDan", "Capoch" };
        for (int i = 0; i < 420; i++) {
            SpoilerFreeRecs.Match m = new SpoilerFreeRecs.Match();
            m.id = 1000 + i;
            int dias = (int) Math.min(364, Math.abs(rnd.nextGaussian()) * 120);
            java.time.ZonedDateTime z = java.time.ZonedDateTime.now().minusDays(dias).withHour(rnd.nextInt(24) < 8 ? 22 : 19 + rnd.nextInt(5)).withMinute(rnd.nextInt(60));
            m.started = z.toInstant(); m.finished = z.plusMinutes(15 + rnd.nextInt(40)).toInstant();
            boolean equipo = rnd.nextInt(10) < 3;
            m.mode = equipo ? "Team Random Map" : "1v1 Random Map"; m.map = mapsS[rnd.nextInt(mapsS.length)];
            boolean gano = rnd.nextInt(100) < 53;
            SpoilerFreeRecs.MatchPlayer yo = new SpoilerFreeRecs.MatchPlayer(); yo.id = 1L; yo.name = "12Tirador"; yo.civ = civsS[rnd.nextInt(civsS.length)]; yo.team = 1; yo.won = gano; yo.rating = 1850 + rnd.nextInt(120);
            m.players.add(yo);
            int nRiv = equipo ? 3 : 1;
            for (int k = 0; k < (equipo ? 2 : 0); k++) { SpoilerFreeRecs.MatchPlayer al = new SpoilerFreeRecs.MatchPlayer(); al.id = 500 + rnd.nextInt(6); al.name = "aliado" + al.id; al.civ = civsS[rnd.nextInt(civsS.length)]; al.team = 1; al.won = gano; al.rating = 1700 + rnd.nextInt(300); m.players.add(al); }
            for (int k = 0; k < nRiv; k++) { SpoilerFreeRecs.MatchPlayer r = new SpoilerFreeRecs.MatchPlayer(); int ri = rnd.nextInt(rivS.length); r.id = 100 + ri; r.name = rivS[ri]; r.civ = civsS[rnd.nextInt(civsS.length)]; r.team = 2; r.won = !gano; r.rating = 1500 + rnd.nextInt(700); m.players.add(r); }
            ms.add(m);
        }
        SpoilerFreeRecs.ACTIVIDAD_CACHE.put(1L, new SpoilerFreeRecs.Actividad(1L, "12Tirador", ms, true, 9, System.currentTimeMillis()));
        SpoilerFreeRecs.PERFIL_CACHE.put(1L, new Object[]{ System.currentTimeMillis(), Map.of("rm_1v1", new int[]{ 1905, 260, 1960, 1240, 1100 }, "rm_team", new int[]{ 2110, 800, 2150, 800, 600 }, "ew_1v1", new int[]{ 1400, 300, 1450, 40, 30 }), "es", "TSK", 3810L });
        SwingUtilities.invokeAndWait(() -> app.perfilBtn.doClick());
        Thread.sleep(800);
        foto("shot_perfil_vacio.png");
        SwingUtilities.invokeAndWait(() -> app.abrirPerfil(1L, "12Tirador"));
        Thread.sleep(2500);
        cerrarDialogos();
        foto("shot_actividad.png");
        SpoilerFreeRecs.ACTIVIDAD_CACHE.put(2L, new SpoilerFreeRecs.Actividad(2L, "Turpiacho", ms, true, 9, System.currentTimeMillis()));
        SpoilerFreeRecs.PERFIL_CACHE.put(2L, new Object[]{ System.currentTimeMillis(), Map.of("rm_1v1", new int[]{ 1610, 2800, 1700, 900, 800 }), "es", "", 1700L });
        SwingUtilities.invokeAndWait(() -> app.abrirPerfilEnPestana(2L, "Turpiacho"));
        Thread.sleep(1200);
        foto("shot_perfil_pestanas.png");
        SwingUtilities.invokeAndWait(() -> app.abrirTechTree("aztecs"));
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(app::volverAtras);
        Thread.sleep(800);
        System.out.println("tras volver: perfil abierto=" + app.actividadAbierta + " pid=" + app.actPid + " pestañas=" + app.perfilPestanas.size() + " activa=" + app.perfilPestanaActiva + " historial=" + app.historial.size());
        foto("shot_perfil_atras.png");
        SwingUtilities.invokeAndWait(app::irAdelante);
        Thread.sleep(600);
        System.out.println("tras adelante: techtree=" + app.techTreeBtn.isSelected() + " pos=" + app.historialPos + "/" + app.historial.size());
        // «Ahora»: inyectar top y partidas en curso para ver la tabla
        SpoilerFreeRecs.Match mv = new SpoilerFreeRecs.Match(); mv.id = 555; mv.started = java.time.Instant.now().minusSeconds(900); mv.map = "Arabia"; mv.mode = "1v1 Random Map";
        SpoilerFreeRecs.MatchPlayer a1 = new SpoilerFreeRecs.MatchPlayer(); a1.id = 1; a1.name = "12Tirador"; a1.civ = "Aztecas"; a1.team = 1; a1.rating = 1905;
        SpoilerFreeRecs.MatchPlayer a2 = new SpoilerFreeRecs.MatchPlayer(); a2.id = 3; a2.name = "pume"; a2.civ = "Francos"; a2.team = 2; a2.rating = 1980;
        mv.players.add(a1); mv.players.add(a2);
        synchronized (app.ahoraTop) { app.ahoraTop.add(new Object[]{ 1L, "12Tirador", 1905, 260, "es" }); app.ahoraTop.add(new Object[]{ 3L, "pume", 1980, 120, "ar" }); app.ahoraTop.add(new Object[]{ 2L, "Turpiacho", 1610, 2800, "es" }); }
        app.ahoraTopMs = System.currentTimeMillis();
        synchronized (app.ahoraEnCurso) { app.ahoraEnCurso.put(1L, mv); app.ahoraEnCurso.put(3L, mv); }
        app.ahoraUltimaMs = System.currentTimeMillis();
        synchronized (app.liveTerminadas) { SpoilerFreeRecs.Match mt = new SpoilerFreeRecs.Match(); mt.id = 556; mt.started = java.time.Instant.now().minusSeconds(3000); mt.finished = java.time.Instant.now().minusSeconds(600); mt.map = "Arena"; mt.mode = "1v1 Random Map"; mt.players.add(a1); mt.players.add(a2); app.liveTerminadas.put(556L, new Object[]{ mt, System.currentTimeMillis() - 600_000 }); }
        SwingUtilities.invokeAndWait(() -> { app.ahoraBtn.doClick(); });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> app.mostrarToast("\u25CF Hera ha empezado una partida \u00B7 vs Viper 2732 (Mongoles\u2013Francos) \u00B7 Arabia", 555));
        Thread.sleep(700);
        foto("shot_ahora.png");
        System.out.println("live tarjetas: " + app.ahoraCuerpo.getComponentCount() + " | estado: " + app.ahoraEstado.getText());
        SwingUtilities.invokeAndWait(app::mostrarLista250);
        Thread.sleep(800);
        foto("shot_lista250.png");
        cerrarDialogos();
        // menú contextual de la watchlist sobre un jugador en partida
        // el vigilante barre la API cada poco y quitaría la partida inventada 555 antes de la foto: se para su
        // temporizador y se espera a que acabe un barrido en curso (vigilando se lee en el EDT, donde se escribe)
        SwingUtilities.invokeAndWait(() -> { if (app.vigilante != null) app.vigilante.stop(); });
        boolean[] barriendo = { true };
        for (int i = 0; i < 120 && barriendo[0]; i++) { SwingUtilities.invokeAndWait(() -> barriendo[0] = app.vigilando); if (barriendo[0]) Thread.sleep(250); }
        app.vivoWatch.put(1L, 555L);
        SwingUtilities.invokeAndWait(() -> { app.todosJugadores.add(new SpoilerFreeRecs.Player(1L, "12Tirador", "General")); app.todosJugadores.add(new SpoilerFreeRecs.Player(3L, "pume", "General")); app.rebuildGrupos(); app.grupoCombo.setSelectedItem("Todos"); app.aplicarFiltroGrupo(); app.playersList.setSelectedIndex(0);
            app.actualizarIndicadoresVivos(); });   // lo que hace el vigilante tras detectar la partida; sin esto la foto dependía de cuándo saltara su temporizador.
        // Carrera reducida, no eliminada: si el vigilante salta antes de la foto puede quitar vivoWatch(1L) (la partida 555 no existe).
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> { Rectangle r = app.playersList.getCellBounds(0, 0); app.menuContextualWatchlist(app.playersModel.get(0), new java.awt.event.MouseEvent(app.playersList, java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, r.x + 40, r.y + 8, 1, true)); });
        Thread.sleep(700);
        foto("shot_menu.png");
        SwingUtilities.invokeAndWait(() -> javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath());
        SwingUtilities.invokeAndWait(() -> { JScrollPane sc = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, app.actCalendario); sc.getVerticalScrollBar().setValue(sc.getVerticalScrollBar().getMaximum()); });
        Thread.sleep(700);
        foto("shot_actividad_abajo.png");
        System.out.println("actividad estado: " + app.actEstado.getText() + " | título: " + app.getTitle());
        System.out.println("tabla filas: " + app.ladderModelo.getRowCount() + " | fila0: " + java.util.Arrays.toString(app.ladderModelo.getDataVector().get(0).toArray()));
        System.out.println("pct rango 12Tirador todos rm_1v1: " + SpoilerFreeRecs.percentilRango("rm_1v1", 260) + " | por rating activos: " + SpoilerFreeRecs.percentilRating("rm_1v1", true, 1905) + " | novato: " + SpoilerFreeRecs.percentilRating("rm_1v1", true, 760));
        SwingUtilities.invokeAndWait(() -> { app.stRango.rango("1600-1800|*"); app.stRango.alCambiar.accept("1600-1800|*"); app.stMapaCombo.setSelectedIndex(3); });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> app.civStatsBtn.doClick());
        Thread.sleep(1500);
        foto("shot_civstats_2000_mapa.png");
        SwingUtilities.invokeAndWait(() -> { app.stRango.rango("*"); app.stRango.alCambiar.accept("*"); app.stMapaCombo.setSelectedIndex(0); });
        Thread.sleep(1200);
        SwingUtilities.invokeAndWait(app::mostrarMatrizGrande);
        Thread.sleep(1500);
        foto("shot_matriz_grande.png");
        cerrarDialogos();
        SwingUtilities.invokeAndWait(() -> app.mostrarListaCompleta("prueba", cuerpo -> { for (int i = 0; i < 30; i++) cuerpo.add(app.filaBarra("fila " + i, i / 30.0, "50 %", Color.GRAY, null)); }));
        Thread.sleep(800);
        foto("shot_lista_completa.png");
        cerrarDialogos();
        System.out.println("civstats 2000+ mapa: filas " + app.stModelo.getRowCount() + " | estado: " + app.stEstado.getText() + " | mapa=" + app.statsMapa + " tramo=" + app.statsTramo);
    }
}
