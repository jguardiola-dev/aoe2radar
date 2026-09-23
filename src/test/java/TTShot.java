import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.Map;

public class TTShot {
    static SpoilerFreeRecs app() {
        for (Frame f : Frame.getFrames()) if (f instanceof SpoilerFreeRecs s) return s;
        return null;
    }
    static void cerrarDialogos() throws Exception {
        SwingUtilities.invokeAndWait(() -> { for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isVisible()) d.dispose(); });
    }
    static void foto(String nombre) throws Exception {
        SpoilerFreeRecs app = app();
        Rectangle r = app.getBounds();
        BufferedImage img = new Robot().createScreenCapture(r);
        ImageIO.write(img, "png", new File(nombre));
        System.out.println("foto " + nombre + " " + r);
    }
    public static void main(String[] a) throws Exception {
        try { correr(); } catch (Throwable ex) { ex.printStackTrace(); } finally { System.exit(0); }
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
        foto("shot_watchlist.png");
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
        app.vivoWatch.put(1L, 555L);
        SwingUtilities.invokeAndWait(() -> { app.todosJugadores.add(new SpoilerFreeRecs.Player(1L, "12Tirador", "General")); app.todosJugadores.add(new SpoilerFreeRecs.Player(3L, "pume", "General")); app.rebuildGrupos(); app.grupoCombo.setSelectedItem("Todos"); app.aplicarFiltroGrupo(); app.playersList.setSelectedIndex(0); });
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
