package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.AzarService;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import static dev.tirador.aoe2radar.ui.PartidasViewTest.A;
import static dev.tirador.aoe2radar.ui.PartidasViewTest.B;
import static dev.tirador.aoe2radar.ui.PartidasViewTest.asentar;
import static dev.tirador.aoe2radar.ui.PartidasViewTest.conConfig;
import static dev.tirador.aoe2radar.ui.PartidasViewTest.enEdt;
import static dev.tirador.aoe2radar.ui.PartidasViewTest.esperar;
import static dev.tirador.aoe2radar.ui.PartidasViewTest.partida;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Caracterización de la lógica de Partidas a través de la vista entera (fachada + piezas, con los dobles de
 * {@link PartidasViewTest}): a quién se atribuye cada partida, los filtros de la tabla, el texto del botón «Buscar
 * partidas», las sugerencias de rival, los mensajes de estado de buscar/azar/GTE/descargar y la separación de las
 * partidas en directo. Se escribió ANTES de llevar esa lógica a {@link PartidasPresenter} y debe seguir verde
 * después: es la prueba de que el movimiento no cambió nada que se vea.
 */
class PartidasLogicaTest {

    /** AzarService sin red: devuelve lo que le diga cada test. */
    static final class AzarFalso implements AzarService {
        volatile List<Match> aleatorias = List.of(), gte = List.of();
        volatile boolean agotado;
        @Override public List<Match> buscarAleatorias(int lo, int hi, String mapaSel, String civSel, int hours, int multAzar,
                                                      Instant cutoff, long serial, Consumer<String> progreso) {
            return new ArrayList<>(aleatorias);
        }
        @Override public List<Match> buscarGte(Instant cutoff, Consumer<String> progreso) { return new ArrayList<>(gte); }
        @Override public boolean tramoAgotado() { return agotado; }
    }

    @TempDir Path recs;
    String idiomaPrevio;
    JFrame ventana;
    PartidasViewTest.EnlaceFalso enlace;
    PartidasViewTest.AnfitrionFalso anfitrion;
    PartidasViewTest.RecFalso rec;
    AzarFalso azar;
    PartidasView vista;

    byte[] configPrevio;

    @BeforeEach void crear() throws Exception {
        configPrevio = PartidasViewTest.savegameDePrueba(recs);   // sin diálogos reales: ver PartidasViewTest
        idiomaPrevio = IDIOMA;
        IDIOMA = "es";
        enlace = new PartidasViewTest.EnlaceFalso();
        anfitrion = new PartidasViewTest.AnfitrionFalso(recs);
        rec = new PartidasViewTest.RecFalso();
        azar = new AzarFalso();
        CompanionApi companion = new CompanionApi(new ApiClient(new WatchlistViewTest.ThrottleSinFreno(),
                new WatchlistViewTest.TransporteNuncaLlamado(), s -> { }, () -> false));
        BarridoVivos barrido = new BarridoVivos(companion, new RelojFalso(), (m, pid) -> "r", new HashMap<>(), ms -> { }, 0, 50);
        SwingUtilities.invokeAndWait(() -> {
            PartidasView.SUJETOS.clear();
            ventana = new JFrame();
            vista = new PartidasView(ventana, new WatchlistViewTest.MenusFalso(), null, new WatchlistViewTest.NavegacionFalsa(),
                    azar, rec, barrido, 50, 0, enlace, anfitrion);
            vista.agregarFilaConsulta(new JPanel());
            vista.construirFilaNota();
            vista.construirTabla();
            vista.construirCards();
            vista.construirBotonesInferiores();
        });
    }

    @AfterEach void cerrar() throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> { PartidasView.SUJETOS.clear(); ventana.dispose(); });
            IDIOMA = idiomaPrevio;
        } finally {
            PartidasViewTest.restaurarConfig(configPrevio);   // aunque la ventana sea null o dispose lance
        }
    }

    static MatchPlayer mp(long id, String nombre, int equipo, Integer rating) {
        MatchPlayer p = new MatchPlayer(); p.id = id; p.name = nombre; p.team = equipo; p.rating = rating;
        return p;
    }

    static Match partidaDe(long id, MatchPlayer... jugadores) {
        Match m = new Match();
        Instant fin = Instant.now().minusSeconds(600);
        m.id = id; m.started = fin.minusSeconds(1800); m.finished = fin; m.mode = "1v1 Random Map"; m.map = "Arabia";
        for (MatchPlayer p : jugadores) m.players.add(p);
        return m;
    }

    // ----- asignarRef: a quién se atribuye cada partida (columna «Jugador») -----

    @Test void referencia_sujetosLuegoSeleccionLuegoListaLuegoMejorRating() throws Exception {
        Match m1 = partidaDe(1, mp(10, "a", 1, 1500), mp(11, "b", 2, 1600));   // dos sujetos: el de más rating
        Match m2 = partidaDe(2, mp(10, "a", 1, null), mp(11, "b", 2, 1400));   // sujeto sin rating pierde ante uno con rating
        Match m3 = partidaDe(3, mp(10, "a", 1, 1500), mp(11, "b", 2, null));   // el primero con rating se queda
        Match m4 = partidaDe(4, mp(20, "c", 1, 2000), mp(21, "d", 2, 1000));   // nadie en SUJETOS: la selección
        Match m5 = partidaDe(5, mp(31, "f", 1, 2000), mp(30, "e", 2, 1000));   // ni sujeto ni selección: la lista
        Match m6 = partidaDe(6, mp(30, "e", 1, 2000), mp(21, "d", 2, 1000));   // selección antes que lista
        Match m7 = partidaDe(7, mp(40, "g", 1, 1200), mp(41, "h", 2, 1300), mp(42, "i", 1, null));   // mejor rating
        Match m8 = partidaDe(8, mp(50, "j", 1, 1300), mp(51, "k", 2, 1300));   // empate: el primero
        enlace.seleccion.add(new Player(21, "d", "General"));
        enlace.jugadores.add(new Player(30, "e", "General"));
        enlace.jugadores.add(new Player(21, "d", "General"));
        enEdt(() -> {
            PartidasView.SUJETOS.addAll(List.of(10L, 11L));
            vista.all.addAll(List.of(m1, m2, m3, m4, m5, m6, m7, m8));
            vista.applyFilters();
        });
        assertEquals(List.of(11L, 11L, 10L, 21L, 30L, 21L, 41L, 50L),
                List.of(m1.refId, m2.refId, m3.refId, m4.refId, m5.refId, m6.refId, m7.refId, m8.refId));
    }

    // ----- applyFilters -----

    @Test void filtroDeSujetos_incluyeLasCuentasVinculadas() throws Exception {
        Player p1 = new Player(100, "P1", "General", 5), p2 = new Player(101, "P2", "General", 5), p3 = new Player(102, "P3", "General", 0);
        enlace.jugadores.addAll(List.of(p1, p2, p3));
        Match a = partida(1, p1, Instant.now().minusSeconds(600)), b = partida(2, p2, Instant.now().minusSeconds(700)),
              c = partida(3, p3, Instant.now().minusSeconds(800));
        enEdt(() -> {
            vista.refrescarSujetos(List.of(p1, p3), false);
            vista.filtroSujetos.add(100L);
            vista.all.addAll(List.of(a, b, c));
            vista.applyFilters();
            assertEquals(List.of(a, b), vista.view, "P1 y su cuenta hermana P2 (mismo vínculo); P3 no");
            vista.filtroSujetos.clear(); vista.filtroSujetos.add(102L);
            vista.applyFilters();
            assertEquals(List.of(c), vista.view, "vínculo 0: solo él");
            vista.filtroSujetos.clear();
            vista.applyFilters();
            assertEquals(List.of(a, b, c), vista.view, "sin filtro: todas");
        });
    }

    @Test void filtroDeRivalYModo_yMensajeDeEstado() throws Exception {
        Match a = partida(1, A, Instant.now().minusSeconds(600)), b = partida(2, A, Instant.now().minusSeconds(700));
        Match c = partida(3, A, Instant.now().minusSeconds(800)); c.mode = "Team Random Map";
        enlace.jugadores.add(A);
        enEdt(() -> {
            vista.all.addAll(List.of(a, b, c));
            vista.refreshModeCombo();
            vista.applyFilters();
            assertEquals("3 de 3 partidas (según filtros).", anfitrion.estado);
            vista.rivalPopup = new JPopupMenu() { @Override public void show(Component invoker, int x, int y) { } };
            vista.rivalField.setText("RIVAL2");   // el DocumentListener normaliza y filtra
            assertEquals("rival2", vista.filtroRival);
            assertEquals(List.of(b), vista.view);
            assertEquals("1 de 3 partidas (según filtros).", anfitrion.estado);
            vista.rivalField.setText("");
            vista.modeCombo.setSelectedItem("Team Random Map");
            assertEquals(List.of(c), vista.view);
        });
    }

    @Test void partidasSinTerminar_estadoEnDirectoOColgada_yNoEnDisco() throws Exception {
        Match viva = partida(1, A, Instant.now()); viva.finished = null; viva.enDisco = true; viva.enJuego = true;
        Match colgada = partida(2, A, Instant.now().minusSeconds(3600)); colgada.finished = null; colgada.enDisco = true;
        anfitrion.enCurso = m -> m.id == 1;
        enEdt(() -> {
            vista.all.addAll(List.of(viva, colgada));
            vista.applyFilters();
        });
        assertEquals("▶", viva.estado);
        assertEquals("—", colgada.estado);
        assertFalse(viva.enDisco || viva.enJuego || colgada.enDisco);
    }

    @Test void combosDeModoYMapa_ordenadosYConservanLaEleccion() throws Exception {
        Match a = partida(1, A, Instant.now().minusSeconds(600)); a.mode = "Z mode"; a.map = "Nómada";
        Match b = partida(2, A, Instant.now().minusSeconds(700)); b.mode = "A mode"; b.map = " ";
        Match c = partida(3, A, Instant.now().minusSeconds(800)); c.mode = "A mode"; c.map = "Arabia";
        enEdt(() -> {
            vista.all.addAll(List.of(a, b, c));
            vista.refreshModeCombo();
            assertEquals(List.of("Todos los modos", "A mode", "Z mode"), items(vista.modeCombo));
            assertEquals(List.of("Todos los mapas", "Arabia", "Nómada"), items(vista.mapaCombo));
            vista.modeCombo.setSelectedItem("Z mode");
            vista.refreshModeCombo();
            assertEquals("Z mode", vista.modeCombo.getSelectedItem());
            vista.all.remove(a);
            vista.refreshModeCombo();
            assertEquals("Todos los modos", vista.modeCombo.getSelectedItem(), "el modo elegido ya no está: vuelve a todos");
        });
    }

    static List<String> items(javax.swing.JComboBox<String> cb) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < cb.getItemCount(); i++) out.add(cb.getItemAt(i));
        return out;
    }

    // ----- texto del botón «Buscar partidas» -----

    @Test void textoDelBoton_segunAQuienVaABuscar() throws Exception {
        enEdt(() -> {
            vista.actualizarTextoBuscar();
            assertEquals("Buscar partidas (todo el grupo)", vista.fetchBtn.getText());
            assertEquals(vista.fetchBtn.getText(), vista.guiaBtn.getText());
            assertNull(vista.objetivoEtiqueta);
        });
        enlace.seleccion.add(A);
        enEdt(() -> { vista.actualizarTextoBuscar(); assertEquals("Buscar partidas (1 seleccionado)", vista.fetchBtn.getText()); });
        enlace.seleccion.add(B);
        enEdt(() -> { vista.actualizarTextoBuscar(); assertEquals("Buscar partidas (2 seleccionados)", vista.fetchBtn.getText()); });
        enlace.seleccion.clear();
        enlace.modoTop = true;
        enEdt(() -> { vista.actualizarTextoBuscar(); assertEquals("Buscar partidas (selecciona a alguien)", vista.fetchBtn.getText()); });
        anfitrion.perfilPid = 55; anfitrion.perfilNombre = "Zed";
        enEdt(() -> {
            vista.actualizarTextoBuscar();   // perfil (no abierto) con la watchlist en ★ y sin selección: busca al del perfil
            assertEquals("Buscar partidas (Zed)", vista.fetchBtn.getText());
            assertEquals(new Player(55, "Zed", ""), vista.objetivoEtiqueta);
        });
        enlace.modoTop = false;
        enEdt(() -> { vista.actualizarTextoBuscar(); assertEquals("Buscar partidas (todo el grupo)", vista.fetchBtn.getText()); assertNull(vista.objetivoEtiqueta); });
        anfitrion.perfilAbierto = true;
        enEdt(() -> { vista.actualizarTextoBuscar(); assertEquals("Buscar partidas (Zed)", vista.fetchBtn.getText()); });
        enlace.seleccion.add(A);
        enEdt(() -> { vista.actualizarTextoBuscar(); assertEquals("Buscar partidas (1 seleccionado)", vista.fetchBtn.getText()); assertNull(vista.objetivoEtiqueta); });
        enlace.invitado = B;
        enEdt(() -> { vista.actualizarTextoBuscar(); assertEquals("Buscar partidas (Beto)", vista.fetchBtn.getText()); assertNull(vista.objetivoEtiqueta); });
        enEdt(() -> {
            vista.fetchWorker = new javax.swing.SwingWorker<Void, Void>() { @Override protected Void doInBackground() { return null; } };
            enlace.invitado = null;
            vista.actualizarTextoBuscar();
            assertEquals("Buscar partidas (Beto)", vista.fetchBtn.getText(), "con una búsqueda en marcha el botón dice lo suyo");
            vista.fetchWorker = null;
        });
    }

    // ----- sugerencias de rival -----

    /** El popup de sugerencias sin pantalla: se sustituye por uno que no se muestra, pero recuerda si se pidió. */
    boolean[] popupFalso() {
        boolean[] mostrado = { false };
        vista.rivalPopup = new JPopupMenu() { @Override public void show(Component invoker, int x, int y) { mostrado[0] = true; } };
        return mostrado;
    }

    static List<String> textos(JPopupMenu pm) {
        List<String> out = new ArrayList<>();
        for (Component c : pm.getComponents()) out.add(((JMenuItem) c).getText());
        return out;
    }

    @Test void sugerencias_cuentaPartidasPorRivalOrdenadasYHastaOcho() throws Exception {
        List<Match> ms = new ArrayList<>();
        long id = 1;
        for (int r = 1; r <= 9; r++)
            for (int k = 0; k < r; k++) ms.add(partidaDe(id++, mp(A.id(), "Ana", 1, 1500), mp(500 + r, "Rival" + r, 2, 1400)));
        Match equipo = partidaDe(id++, mp(A.id(), "Ana", 1, 1500), mp(600, "RivalAliado", 1, 1400), mp(601, "Otro", 2, 1400), mp(602, "Otro2", 2, 1400));
        Match gte = partidaDe(id++, mp(700, "RivalGte", 1, 1500), mp(701, "x", 2, 1400)); gte.gte = 3;
        ms.add(equipo); ms.add(gte);
        enlace.jugadores.add(A);
        boolean[] mostrado = popupFalso();
        enEdt(() -> {
            vista.all.addAll(ms);
            vista.rivalField.setText("rival");
            assertTrue(mostrado[0]);
            assertEquals(List.of("Rival9  (9)", "Rival8  (8)", "Rival7  (7)", "Rival6  (6)", "Rival5  (5)", "Rival4  (4)",
                    "Rival3  (3)", "Rival2  (2)"), textos(vista.rivalPopup), "top 8; ni el aliado ni la de Guess the ELO");
            ((JMenuItem) vista.rivalPopup.getComponent(1)).doClick();
            assertEquals("Rival8", vista.rivalField.getText(), "elegir una sugerencia la escribe");
        });
    }

    @Test void sugerencias_coincidenciaExactaYUnica_noSeMuestran() throws Exception {
        enlace.jugadores.add(A);
        boolean[] mostrado = popupFalso();
        enEdt(() -> {
            vista.all.add(partidaDe(1, mp(A.id(), "Ana", 1, 1500), mp(501, "Rival1", 2, 1400)));
            vista.rivalField.setText("rival1");
            assertFalse(mostrado[0]);
            assertEquals(0, vista.rivalPopup.getComponentCount());
            vista.rivalField.setText("rival");
            assertTrue(mostrado[0], "si no es exacta, sí");
            assertEquals(List.of("Rival1  (1)"), textos(vista.rivalPopup));
        });
    }

    // ----- mensajes de Buscar partidas y estados conservados -----

    @Test void buscar_mensajeFinal_yConservaElEstadoDeLasFilasQueVuelven() throws Exception {
        enlace.jugadores.add(A);
        Instant fin = Instant.now().minusSeconds(600);
        Match vieja = partida(8401, A, fin); vieja.estado = "✓ guardada";
        anfitrion.paginador = (pid, pag, pp) -> List.of(partida(8401, A, fin), partida(8402, A, fin.minusSeconds(60)));
        enEdt(() -> { vista.all.add(vieja); vista.fetchMatches(vista.fetchBtn); });
        esperar(() -> vista.fetchWorker == null, "que la búsqueda termine");
        asentar();
        enEdt(() -> {
            assertEquals("2 de 2 partidas en las últimas 24 h.", anfitrion.estado);
            assertNotSame(vieja, vista.all.get(0));
            assertEquals("✓ guardada", vista.all.get(0).estado, "la fila que vuelve conserva su estado");
            assertEquals("", vista.all.get(1).estado);
        });
    }

    // ----- Al azar por ELO y Guess the ELO -----

    void conEloConfig(PartidasViewTest.ThrowingRunnable cuerpo) throws Exception {
        conConfig("elo_min", "1800", () -> conConfig("elo_max", "1900", () -> conConfig("elo_horas", "36",
                () -> conConfig("elo_mapa", "(cualquiera)", () -> conConfig("elo_civ", "(cualquiera)",
                        () -> conConfig("elo_intensidad", "0", cuerpo))))));
    }

    @Test void azar_sujetosSonLosTitularesSinRepetir_yMensaje() throws Exception {
        Match m1 = partidaDe(1, mp(10, "Uno", 1, 1850), mp(11, "Dos", 2, 1880));
        Match m2 = partidaDe(2, mp(12, "Tres", 1, 1800), mp(11, "Dos", 2, 1890));
        Match m3 = partidaDe(3, mp(13, "Cuatro", 1, 1870), mp(14, "Cinco", 2, 1820));
        azar.aleatorias = List.of(m1, m2, m3);
        conEloConfig(() -> {
            enEdt(() -> vista.buscarAleatorias(true));
            esperar(() -> !anfitrion.progreso && anfitrion.estado.startsWith("3 "), "que el azar termine");
        });
        enEdt(() -> {
            assertTrue(m1.azar && m2.azar && m3.azar);
            assertEquals(java.util.Set.of(11L, 13L), PartidasView.SUJETOS);
            assertEquals(List.of(new Player(11, "Dos", "", 0), new Player(13, "Cuatro", "", 0)), vista.ultimosSujetos);
            assertEquals("grupo|General", vista.vistaDeSujetos);
            assertEquals(List.of(m1, m2, m3), vista.all);
            assertEquals("3 partidas 1v1 al azar, ELO 1800–1900, últimas 36 h. Repite la búsqueda: continúa donde lo dejó.", anfitrion.estado);
        });
    }

    @Test void azar_tramoAgotadoONada_mensajes() throws Exception {
        azar.aleatorias = List.of(partidaDe(1, mp(10, "Uno", 1, 1850), mp(11, "Dos", 2, 1880)));
        azar.agotado = true;
        conEloConfig(() -> {
            enEdt(() -> vista.buscarAleatorias(true));
            esperar(() -> !anfitrion.progreso && anfitrion.estado.startsWith("1 "), "que el azar termine");
            assertEquals("1 partidas 1v1 al azar, ELO 1800–1900, últimas 36 h. No hay más con esos filtros: tramo entero revisado (amplía horas o rango).", anfitrion.estado);
            azar.aleatorias = List.of();
            enEdt(() -> vista.buscarAleatorias(true));
            esperar(() -> !anfitrion.progreso && anfitrion.estado.startsWith("Nada"), "que el azar termine");
            assertEquals("Nada en 1800–1900 en las últimas 36 h. Detalle del muestreo en descargas.log.", anfitrion.estado);
        });
    }

    @Test void gte_mensajeConElPrimerYUltimoArchivo() throws Exception {
        Match g1 = partidaDe(1, mp(10, "Uno", 1, 1850), mp(11, "Dos", 2, 1880)); g1.gte = 3;
        Match g2 = partidaDe(2, mp(12, "Tres", 1, 1850), mp(13, "Cuatro", 2, 1880)); g2.gte = 7;
        azar.gte = List.of(g1, g2);
        conEloConfig(() -> {
            enEdt(() -> vista.buscarGte());
            esperar(() -> !anfitrion.progreso && anfitrion.estado.startsWith("2 "), "que GTE termine");
            assertEquals("2 partidas Guess the ELO (archivos: «Guess the ELO 3»–«7»). Adivina y comprueba con «Revelar resultado…».", anfitrion.estado);
            azar.gte = List.of();
            enEdt(() -> vista.buscarGte());
            esperar(() -> !anfitrion.progreso && anfitrion.estado.startsWith("Sin"), "que GTE termine");
            assertEquals("Sin partidas para Guess the ELO en las últimas 36 h. Detalle en descargas.log.", anfitrion.estado);
        });
    }

    // ----- descargar: las partidas en directo no se descargan -----

    @Test void descargar_separaLasPartidasEnDirecto() throws Exception {
        Match v1 = PartidasViewTest.enDirecto(8501), v2 = PartidasViewTest.enDirecto(8502);
        Match hecha = partida(8503, A, Instant.now().minusSeconds(600));
        enEdt(() -> vista.download(List.of(v1, v2)));
        assertEquals("Esas partidas están EN DIRECTO: doble clic en una para espectarla.", anfitrion.estado);
        enEdt(() -> vista.download(List.of()));
        assertEquals("No hay partidas seleccionadas.", anfitrion.estado);
        assertTrue(rec.procesadas.isEmpty());
        enEdt(() -> vista.download(List.of(v1, hecha)));
        esperar(() -> !anfitrion.progreso, "que la descarga termine");
        asentar();
        assertTrue(anfitrion.estados.contains("Las partidas EN DIRECTO no se descargan; se saltan."));
        assertEquals(List.of(8503L), rec.procesadas);
        assertEquals("1/1 recs guardadas en " + recs, anfitrion.estado);
    }

    // ----- a quién busca fetchMatches -----

    /** Lanza «Buscar partidas» (páginas vacías) y devuelve a quién se pidió, en orden. */
    List<Long> buscarYVerAQuien() throws Exception {
        anfitrion.pedidas.clear();
        anfitrion.paginador = (pid, pag, pp) -> List.of();
        enEdt(() -> vista.fetchMatches(vista.fetchBtn));
        esperar(() -> vista.fetchWorker == null, "que la búsqueda termine");
        return new ArrayList<>(anfitrion.pedidas);
    }

    @Test void buscar_seleccionOLaListaEntera() throws Exception {
        enlace.jugadores.addAll(List.of(A, B));
        assertEquals(List.of(A.id(), B.id()), buscarYVerAQuien(), "sin selección: toda la lista");
        enlace.seleccion.add(B);
        assertEquals(List.of(B.id()), buscarYVerAQuien());
    }

    @Test void buscar_elForzadoSeUsaYSeLimpia_yElInvitadoAntesQueLaSeleccion() throws Exception {
        enlace.jugadores.addAll(List.of(A, B));
        enlace.seleccion.add(A);
        Player forzado = new Player(7100, "Forzado", "General");
        enlace.objetivoForzado = forzado;
        enlace.invitado = B;
        assertEquals(List.of(7100L), buscarYVerAQuien());
        assertNull(enlace.objetivoForzado, "el objetivo forzado se consume");
        assertEquals(List.of(B.id()), buscarYVerAQuien(), "sin forzado: el invitado");
    }

    @Test void buscar_conElPerfilAbiertoYSinSeleccion_buscaAlDelPerfil() throws Exception {
        enlace.jugadores.addAll(List.of(A, B));
        anfitrion.perfilPid = 7200; anfitrion.perfilAbierto = true; anfitrion.perfilNombre = "Perfilado";
        assertEquals(List.of(7200L), buscarYVerAQuien());
        assertTrue(anfitrion.mostrarDirectos.contains(false));
        assertNull(enlace.objetivoForzado);
        enlace.seleccion.add(A);
        assertEquals(List.of(A.id()), buscarYVerAQuien(), "con selección, la selección");
    }

    @Test void buscar_elDelBotonSinSeleccion() throws Exception {
        enlace.jugadores.addAll(List.of(A, B));
        enEdt(() -> vista.objetivoEtiqueta = new Player(7300, "Etiqueta", ""));
        assertEquals(List.of(7300L), buscarYVerAQuien());
    }

    @Test void buscar_enTopConMasDeQuince_losQuincePrimerosYAviso() throws Exception {
        enlace.modoTop = true;
        for (int i = 0; i < 17; i++) { Player p = new Player(7400 + i, "T" + i, "General"); enlace.jugadores.add(p); enlace.seleccion.add(p); }
        List<Long> pedidas = buscarYVerAQuien();
        assertEquals(15, pedidas.size());
        assertEquals(7400L, pedidas.get(0));
        assertEquals(7414L, pedidas.get(14));
        assertTrue(anfitrion.estados.stream().anyMatch(s -> s.contains("15 perfiles por tanda")), "estados: " + anfitrion.estados);
    }

    // ----- cabecera «Partidas de:»: clic en un sujeto -----

    javax.swing.JLabel chip(int i) {
        JPanel chips = (JPanel) enlace.sujetosPanel.getComponent(1);
        return (javax.swing.JLabel) chips.getComponent(i);
    }

    void clicEnChip(int i, boolean ctrl) {
        javax.swing.JLabel l = chip(i);
        l.dispatchEvent(new java.awt.event.MouseEvent(l, java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                ctrl ? java.awt.event.InputEvent.CTRL_DOWN_MASK : 0, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    @Test void cabecera_clicFiltraCtrlSumaYOtroClicQuita() throws Exception {
        Match a = partida(1, A, Instant.now().minusSeconds(600)), b = partida(2, B, Instant.now().minusSeconds(700));
        enEdt(() -> {
            vista.refrescarSujetos(List.of(A, B), false);
            vista.all.addAll(List.of(a, b));
            vista.applyFilters();
            clicEnChip(1, false);
            assertEquals(java.util.Set.of(B.id()), vista.filtroSujetos);
            assertEquals(List.of(b), vista.view);
            clicEnChip(0, true);
            assertEquals(java.util.Set.of(A.id(), B.id()), vista.filtroSujetos, "Ctrl+clic suma");
            clicEnChip(1, true);
            assertEquals(java.util.Set.of(A.id()), vista.filtroSujetos, "Ctrl+clic en uno marcado lo quita");
            clicEnChip(0, false);
            assertEquals(java.util.Set.of(), vista.filtroSujetos, "clic en el único marcado: todas");
            assertEquals(List.of(a, b), vista.view);
            clicEnChip(0, false);
            clicEnChip(1, false);
            assertEquals(java.util.Set.of(B.id()), vista.filtroSujetos, "clic en otro: solo ese");
        });
    }
}
