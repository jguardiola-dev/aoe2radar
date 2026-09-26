package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.BarridoVivos;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PartidasView (fachada + piezas) con dobles: sin red (la página de partidas la da cada test) y sin pantalla
 * visible (la ventana no se muestra). Cubre la revisión 1.3 de Partidas: Detener y la × paran «Buscar partidas»
 * (general F4 / watchlist F6), el recorrido sin Swing que hay debajo, y lo que se añada en los arreglos siguientes.
 * Los SwingWorker son los de verdad: cada test espera a que el estado llegue, con un tope de tiempo.
 */
class PartidasViewTest {

    static final class EnlaceFalso implements PartidasView.EnlaceWatchlist {
        final List<Player> jugadores = new ArrayList<>();
        final List<Player> seleccion = new ArrayList<>();
        Player invitado, objetivoForzado;
        final JPanel sujetosPanel = new JPanel();
        @Override public List<Player> seleccion() { return new ArrayList<>(seleccion); }
        @Override public int seleccionSize() { return seleccion.size(); }
        @Override public boolean soloVivosMarcado() { return false; }
        @Override public boolean modoTop() { return false; }
        @Override public String grupoDestino() { return "General"; }
        @Override public List<Player> conFamilias(List<Player> base) { return base; }
        @Override public void limpiarSeleccion() { seleccion.clear(); }
        @Override public int totalJugadores() { return jugadores.size(); }
        @Override public Player jugador(int indice) { return jugadores.get(indice); }
        @Override public Integer eloDe(long pid) { return null; }
        @Override public String grupoDeJugador(long pid) { return null; }
        @Override public List<Player> todosJugadores() { return jugadores; }
        @Override public void actualizarIndicadoresVivos() { }
        @Override public void aplicarFiltroGrupo() { }
        @Override public String tipCuentaVinculada(Match m) { return null; }
        @Override public Player objetivoForzado() { return objetivoForzado; }
        @Override public void fijarObjetivoForzado(Player p) { objetivoForzado = p; }
        @Override public void limpiarObjetivoForzado() { objetivoForzado = null; }
        @Override public Player invitado() { return invitado; }
        @Override public void limpiarInvitado() { invitado = null; }
        @Override public String vistaActualId() { return "grupo|General"; }
        @Override public int horasVentana() { return 24; }
        @Override public void guardarVentanaHoras() { }
        @Override public void actualizarTextoForma() { }
        @Override public JPanel sujetosPanel() { return sujetosPanel; }
    }

    /** Lo que la ventana pone de verdad (CableadoPartidas + BarraEstado), en pequeño: el semáforo de operación
     *  (opSerial sube con cada trabajando(true), que también suelta el freno) y la última línea de estado. */
    static final class AnfitrionFalso implements PartidasView.Anfitrion {
        volatile String estado = "";
        final List<String> estados = Collections.synchronizedList(new ArrayList<>());
        volatile long opSerial;
        volatile boolean progreso;
        volatile boolean stop;
        volatile BusquedasPartidas.Paginador paginador = (pid, pag, pp) -> List.of();
        final List<Long> pedidas = Collections.synchronizedList(new ArrayList<>());
        final Path recs;
        AnfitrionFalso(Path recs) { this.recs = recs; }
        @Override public void estado(String texto) { estado = texto; estados.add(texto); }
        final List<Boolean> mostrarDirectos = Collections.synchronizedList(new ArrayList<>());
        @Override public void mostrarDirectos(boolean mostrar) { mostrarDirectos.add(mostrar); }
        @Override public void refrescarDirectos() { }
        @Override public void enfocarBuscador() { }
        @Override public boolean confirmarEspectar(String nombre) { return false; }
        final List<Long> espectadas = Collections.synchronizedList(new ArrayList<>());
        volatile int capturesLanzados;
        @Override public void espectarVerificando(long profileId, long matchId) { espectadas.add(matchId); }
        @Override public void espectarPartida(long matchId) { }
        @Override public void lanzarCaptureAge(Path rec) { capturesLanzados++; }
        @Override public void abrirUrl(String url) { }
        @Override public String nombreVisible(long pid, String nombre) { return nombre; }
        @Override public String paisDe(long pid) { return null; }
        @Override public boolean enCursoReal(Match m) { return false; }
        /** En qué hilo se pidió cada ruta de rec (true = EDT): quien la pide va a mirar el disco justo después. */
        final List<Boolean> destinoEnEdt = Collections.synchronizedList(new ArrayList<>());
        /** Si están puestos, destino (fuera del EDT) espera / falla: un «Enviar al juego» que sigue mirando recs, o que se rompe. */
        volatile CountDownLatch soltarDestino;
        volatile RuntimeException fallarDestino;
        @Override public Path destino(Match m) {
            boolean edt = SwingUtilities.isEventDispatchThread();
            destinoEnEdt.add(edt);
            if (!edt && fallarDestino != null) throw fallarDestino;
            if (!edt && soltarDestino != null) try { soltarDestino.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            return recs.resolve(m.id + ".aoe2record");
        }
        @Override public Path recsDir() { return recs; }
        @Override public void trabajando(boolean on) { progreso = on; if (on) { stop = false; opSerial++; } }
        @Override public long operacionActual() { return opSerial; }
        @Override public boolean detenido() { return stop; }
        @Override public void pararOperacion() { stop = true; }
        @Override public void anotarHiloOperacion() { }
        @Override public void aprenderCatalogos(List<Match> res) { }
        @Override public List<String> mapasConocidos() { return List.of(); }
        @Override public List<String> civsConocidas() { return List.of(); }
        @Override public void dormir(long ms) { }
        @Override public long perfilAbiertoPid() { return 0; }
        @Override public boolean perfilAbierto() { return false; }
        @Override public String perfilNombreAbierto() { return ""; }
        @Override public void mostrarHistorialSiSigueAbierto(long pid, String nombre) { }
        @Override public Iterable<Match> paginaDePartidas(long pid, int pagina, int porPagina) throws java.io.IOException, InterruptedException {
            pedidas.add(pid);
            try { return paginador.pagina(pid, pagina, porPagina); }
            catch (java.io.IOException | InterruptedException | RuntimeException ex) { throw ex; }
            catch (Exception ex) { throw new java.io.IOException(ex); }
        }
        @Override public boolean autoCopiarAlDescargar() { return false; }
        @Override public void continuarDisponible(boolean visible) { }
        @Override public void ajustarGrisesNota(boolean oscuro) { }
        @Override public void actualizarControlesTabla() { }
    }

    /** RecService sin red: cuenta las partidas que le piden y las da por descargadas. */
    static final class RecFalso implements dev.tirador.aoe2radar.service.RecService {
        final List<Long> procesadas = Collections.synchronizedList(new ArrayList<>());
        volatile CountDownLatch dentro, soltar;   // si están puestos, procesar avisa y espera (una descarga «en curso»)
        volatile Path escribirEn;                 // si está puesto, deja la rec en esa carpeta (como la descarga real)
        @Override public Resultado procesar(Match m, java.util.Set<Long> trackedIds, boolean enviarAlJuego, Path savegame, BooleanSupplier cancelado) {
            procesadas.add(m.id);
            if (dentro != null) dentro.countDown();
            if (soltar != null) try { soltar.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            if (escribirEn != null) try { java.nio.file.Files.write(escribirEn.resolve(m.id + ".aoe2record"), new byte[6000]); } catch (java.io.IOException ignored) { }
            return new Resultado(Estado.DESCARGADA, false, null, true);
        }
    }

    static final Player A = new Player(7001L, "Ana", "General", 0L);
    static final Player B = new Player(7002L, "Beto", "General", 0L);
    static final Player C = new Player(7003L, "Cris", "General", 0L);

    @TempDir Path recs;
    String idiomaPrevio;
    JFrame ventana;
    EnlaceFalso enlace;
    AnfitrionFalso anfitrion;
    RecFalso rec;
    PartidasView vista;

    @BeforeEach void crear() throws Exception {
        idiomaPrevio = IDIOMA;
        IDIOMA = "es";
        enlace = new EnlaceFalso();
        anfitrion = new AnfitrionFalso(recs);
        rec = new RecFalso();
        CompanionApi companion = new CompanionApi(new ApiClient(new WatchlistViewTest.ThrottleSinFreno(),
                new WatchlistViewTest.TransporteNuncaLlamado(), s -> { }, () -> false));
        BarridoVivos barrido = new BarridoVivos(companion, new RelojFalso(), (m, pid) -> "r", new HashMap<>(), ms -> { }, 0, 50);
        SwingUtilities.invokeAndWait(() -> {
            ventana = new JFrame();
            vista = new PartidasView(ventana, new WatchlistViewTest.MenusFalso(), null, new WatchlistViewTest.NavegacionFalsa(),
                    null, rec, barrido, 50, 0, enlace, anfitrion);
            vista.agregarFilaConsulta(new JPanel());
            vista.construirFilaNota();
            vista.construirTabla();
            vista.construirCards();
            vista.construirBotonesInferiores();
        });
    }

    @AfterEach void cerrar() throws Exception {
        SwingUtilities.invokeAndWait(() -> { PartidasView.SUJETOS.clear(); ventana.dispose(); });
        IDIOMA = idiomaPrevio;
    }

    // ----- utilidades -----

    static Match partida(long id, Player p, Instant fin) {
        Match m = new Match();
        m.id = id; m.started = fin.minusSeconds(1800); m.finished = fin; m.mode = "1v1 Random Map"; m.map = "Arabia";
        MatchPlayer mp = new MatchPlayer(); mp.id = p.id(); mp.name = p.name(); mp.team = 1;
        MatchPlayer rv = new MatchPlayer(); rv.id = 9000 + id; rv.name = "rival" + id; rv.team = 2;
        m.players.add(mp); m.players.add(rv);
        return m;
    }

    static void enEdt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }

    /** Espera (hasta 10 s) a que se cumpla la condición, mirándola en el EDT. */
    static void esperar(BooleanSupplier cond, String que) throws Exception {
        long fin = System.currentTimeMillis() + 10_000;
        boolean[] ok = { false };
        while (System.currentTimeMillis() < fin) {
            SwingUtilities.invokeAndWait(() -> ok[0] = cond.getAsBoolean());
            if (ok[0]) return;
            Thread.sleep(20);
        }
        fail("tiempo agotado esperando: " + que);
    }

    /** Deja que el hilo de fondo acabe lo que tenga pendiente y que el EDT pinte lo que llegue. */
    static void asentar() throws Exception {
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    // ----- recorrer(): el doInBackground de «Buscar partidas», sin Swing -----

    @Test void recorrer_pararTrasElPrimerJugador_noPideAlSiguienteYVuelveDetenida() {
        boolean[] parar = { false };
        List<Long> pedidas = new ArrayList<>();
        Instant fin = Instant.now().minusSeconds(600);
        BusquedasPartidas.Recorrido r = BusquedasPartidas.recorrer(List.of(A, B, C), fin.minusSeconds(86_400), 50, 0,
                (pid, pag, pp) -> { pedidas.add(pid); parar[0] = true; return List.of(partida(pid, pid == A.id() ? A : B, fin)); },
                m -> false, () -> parar[0], s -> { });
        assertEquals(List.of(A.id()), pedidas, "Detener pulsado durante la página de A: ni B ni C se consultan");
        assertTrue(r.detenida());
    }

    @Test void recorrer_elFrenoCortaLaEspera_noSigueConElSiguienteJugador() {
        boolean[] parar = { false };
        List<Long> pedidas = new ArrayList<>();
        Instant fin = Instant.now().minusSeconds(600);
        BusquedasPartidas.Recorrido r = BusquedasPartidas.recorrer(List.of(A, B, C), fin.minusSeconds(86_400), 50, 0,
                (pid, pag, pp) -> {
                    pedidas.add(pid);
                    if (pid == B.id()) { parar[0] = true; throw new InterruptedException("detenido"); }   // lo que lanza el freno
                    return List.of(partida(pid, A, fin));
                },
                m -> false, () -> parar[0], s -> { });
        assertEquals(List.of(A.id(), B.id()), pedidas, "el corte del freno en B no deja pasar a C");
        assertTrue(r.detenida());
        assertEquals(0, r.fallos(), "un corte pedido no es un fallo del servicio");
    }

    @Test void recorrer_sinParar_recorreATodosYNoVuelveDetenida() {
        Instant fin = Instant.now().minusSeconds(600);
        BusquedasPartidas.Recorrido r = BusquedasPartidas.recorrer(List.of(A, B), fin.minusSeconds(86_400), 50, 0,
                (pid, pag, pp) -> List.of(partida(pid, pid == A.id() ? A : B, fin)),
                m -> false, () -> false, s -> { });
        assertFalse(r.detenida());
        assertEquals(2, r.lista().size());
        assertEquals(java.util.Set.of(A.id(), B.id()), r.exitosos());
    }

    // ----- general F4 / watchlist F6: Detener de la barra y la × paran la búsqueda -----

    @Test void detenerDeLaBarra_paraLaBusquedaYNoPresentaLoParcialComoCompleto() throws Exception {
        enlace.jugadores.addAll(List.of(A, B, C));
        CountDownLatch enB = new CountDownLatch(1), soltarB = new CountDownLatch(1);
        Instant fin = Instant.now().minusSeconds(600);
        anfitrion.paginador = (pid, pag, pp) -> {
            if (pid == B.id()) { enB.countDown(); soltarB.await(5, TimeUnit.SECONDS); }
            return List.of(partida(pid, pid == A.id() ? A : pid == B.id() ? B : C, fin));
        };
        enEdt(() -> vista.fetchMatches(vista.fetchBtn));
        assertTrue(enB.await(5, TimeUnit.SECONDS));
        anfitrion.pararOperacion();   // el «Detener» de la barra de estado: solo pone el freno (stopOperacion)
        soltarB.countDown();          // la petición en vuelo de B acaba igual
        esperar(() -> vista.fetchWorker == null, "que la búsqueda termine");
        asentar();
        assertFalse(anfitrion.pedidas.contains(C.id()), "tras Detener no se consulta a nadie más");
        assertTrue(vista.all.isEmpty(), "lo leído hasta el corte no se presenta como la búsqueda entera");
        assertEquals("Búsqueda detenida.", anfitrion.estado);
    }

    @Test void cruzDePartidasDe_conBusquedaEnCurso_laTablaNoVuelveALlenarse() throws Exception {
        enlace.jugadores.addAll(List.of(A, B));
        CountDownLatch enA = new CountDownLatch(1), soltarA = new CountDownLatch(1);
        Instant fin = Instant.now().minusSeconds(600);
        anfitrion.paginador = (pid, pag, pp) -> {
            if (pid == A.id()) { enA.countDown(); soltarA.await(5, TimeUnit.SECONDS); }
            return List.of(partida(pid, pid == A.id() ? A : B, fin));
        };
        enEdt(() -> vista.fetchMatches(vista.fetchBtn));
        assertTrue(enA.await(5, TimeUnit.SECONDS));
        enEdt(vista::cerrarBusqueda);
        soltarA.countDown();
        asentar();
        enEdt(() -> {
            assertNull(vista.fetchWorker);
            assertTrue(vista.all.isEmpty(), "la × vacía la tabla y la búsqueda cortada no la vuelve a llenar");
            assertFalse(anfitrion.progreso, "la barra de progreso no se queda encendida");
            assertEquals("Búsqueda cerrada.", anfitrion.estado, "estados: " + anfitrion.estados);
        });
        assertFalse(anfitrion.pedidas.contains(B.id()), "tras la × no se consulta a nadie más");
    }

    @Test void cruzDePartidasDe_conElDoneEnCola_laTablaNoVuelveALlenarse() throws Exception {
        // La carrera: doInBackground ya terminó y su done() espera en la cola del EDT cuando se pulsa la ×.
        // cancel(true) ya no puede nada; si fetchWorker siguiera apuntando a ella, su done() volvería a llenar la tabla.
        enlace.jugadores.add(A);
        Instant fin = Instant.now().minusSeconds(600);
        anfitrion.paginador = (pid, pag, pp) -> List.of(partida(pid, A, fin));
        enEdt(() -> {
            vista.fetchMatches(vista.fetchBtn);
            javax.swing.SwingWorker<?, ?> w = vista.fetchWorker;
            long tope = System.currentTimeMillis() + 5000;
            while (!w.isDone() && System.currentTimeMillis() < tope) Thread.onSpinWait();   // el EDT está ocupado: done() queda en cola
            assertTrue(w.isDone(), "doInBackground terminó");
            vista.cerrarBusqueda();
            assertNull(vista.fetchWorker);
            assertTrue(vista.fetchBtn.isEnabled() && vista.azarBtn.isEnabled() && vista.gteBtn.isEnabled(), "la × repone los botones");
            assertFalse(anfitrion.progreso, "y apaga el progreso de la búsqueda");
        });
        asentar();   // ahora corre el done() que estaba en cola
        enEdt(() -> {
            assertTrue(vista.all.isEmpty(), "el done() en cola no vuelve a llenar la tabla tras la ×");
            assertEquals("Búsqueda cerrada.", anfitrion.estado, "estados: " + anfitrion.estados);
        });
    }

    // ----- watchlist F8: pedir las partidas de otro jugador con una búsqueda en marcha -----

    @Test void otraBusquedaConUnaEnMarcha_cancelaLaAnteriorYBuscaAlNuevo() throws Exception {
        CountDownLatch enA = new CountDownLatch(1), soltarA = new CountDownLatch(1);
        CountDownLatch enB = new CountDownLatch(1), soltarB = new CountDownLatch(1);
        Instant fin = Instant.now().minusSeconds(600);
        anfitrion.paginador = (pid, pag, pp) -> {
            if (pid == A.id()) { enA.countDown(); soltarA.await(5, TimeUnit.SECONDS); }
            if (pid == B.id()) { enB.countDown(); soltarB.await(5, TimeUnit.SECONDS); }
            return List.of(partida(pid, pid == A.id() ? A : B, fin));
        };
        enlace.invitado = A;
        enEdt(() -> vista.fetchMatches(vista.fetchBtn));   // doble clic en A (EnlacePartidas.fetchMatches)
        assertTrue(enA.await(5, TimeUnit.SECONDS));
        enlace.invitado = B;
        enEdt(() -> vista.fetchMatches(vista.fetchBtn));   // doble clic en B mientras A sigue
        assertTrue(enB.await(5, TimeUnit.SECONDS), "se busca a B");
        asentar();   // el done() de la búsqueda de A (cancelada) ya pasó: no debe soltar la de B
        enEdt(() -> {
            assertNotNull(vista.fetchWorker, "la búsqueda de B sigue siendo la búsqueda en marcha");
            assertTrue(anfitrion.progreso, "y su barra de progreso sigue encendida");
        });
        soltarB.countDown();
        esperar(() -> vista.fetchWorker == null, "que la búsqueda de B termine");
        soltarA.countDown();   // la petición en vuelo de A acaba después: no debe pisar nada
        asentar();
        enEdt(() -> {
            assertTrue(anfitrion.pedidas.contains(B.id()), "se busca a B");
            assertEquals(1, vista.all.size());
            assertEquals(B.id(), vista.all.get(0).players.get(0).id, "la tabla es la de B");
            assertNull(vista.fetchWorker);
            assertFalse(anfitrion.progreso);
            assertTrue(anfitrion.estado.startsWith("1 de 1 partidas"), "estado: " + anfitrion.estado);
        });
    }

    // ----- watchlist F7: otra operación durante una búsqueda -----

    @Test void otraOperacionDuranteLaBusqueda_noDescartaSusResultados() throws Exception {
        enlace.jugadores.add(A);
        CountDownLatch enA = new CountDownLatch(1), soltarA = new CountDownLatch(1);
        Instant fin = Instant.now().minusSeconds(600);
        anfitrion.paginador = (pid, pag, pp) -> {
            enA.countDown(); soltarA.await(5, TimeUnit.SECONDS);
            return List.of(partida(pid, A, fin));
        };
        enEdt(() -> vista.fetchMatches(vista.fetchBtn));
        assertTrue(enA.await(5, TimeUnit.SECONDS));
        enEdt(() -> anfitrion.trabajando(true));   // empieza otra operación (una descarga, «Ver forma»…): sube el opSerial
        soltarA.countDown();
        esperar(() -> vista.fetchWorker == null, "que la búsqueda termine");
        asentar();
        enEdt(() -> {
            assertEquals(1, vista.all.size(), "la búsqueda se aplica aunque otra operación empezara mientras tanto");
            assertTrue(anfitrion.estado.startsWith("1 de 1 partidas"), "estado: " + anfitrion.estado);
            assertTrue(anfitrion.progreso, "el progreso es de la otra operación: la búsqueda no lo apaga");
            assertTrue(vista.fetchBtn.isEnabled() && vista.azarBtn.isEnabled() && vista.gteBtn.isEnabled());
        });
    }

    // ----- general F6 / watchlist F10: Enter en la tabla -----

    /** Lo que hace Swing con Enter y la tabla enfocada: primero su mapa WHEN_FOCUSED; si no hay nada, el de
     *  WHEN_ANCESTOR_OF_FOCUSED_COMPONENT. */
    Object accionDeEnter() {
        javax.swing.KeyStroke enter = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0);
        Object k = vista.table.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).get(enter);
        return k != null ? k : vista.table.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(enter);
    }

    void pulsarEnter() {
        vista.table.getActionMap().get(accionDeEnter()).actionPerformed(new java.awt.event.ActionEvent(vista.table, 0, "enter"));
    }

    @Test void enter_vaAlAtajoConGuardaYConLaTablaEnfocadaGana() throws Exception {
        enEdt(() -> assertEquals("sfrDescargar", vista.table.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0)),
                "el atajo con guarda está en WHEN_FOCUSED (el mapa que gana con la tabla enfocada)"));
    }

    @Test void enter_conUnaDescargaEnCurso_noLanzaOtra() throws Exception {
        Match m = partida(8101, A, Instant.now().minusSeconds(600));
        enEdt(() -> {
            vista.cargarPartidasEnTabla(List.of(m), A, "grupo|General");
            vista.table.setRowSelectionInterval(0, 0);
            vista.dlSel.setEnabled(false);   // lo que deja download() mientras descarga
        });
        long antes = anfitrion.opSerial;
        enEdt(this::pulsarEnter);
        assertEquals(antes, anfitrion.opSerial, "con una descarga en curso, Enter no empieza otra");
        enEdt(() -> { vista.dlSel.setEnabled(true); pulsarEnter(); });
        assertEquals(antes + 1, anfitrion.opSerial, "sin descarga en curso, Enter descarga la selección");
        esperar(() -> !anfitrion.progreso, "que la descarga termine");
        assertEquals(List.of(8101L), rec.procesadas);
    }

    // ----- DEUDA de la partición: los encargos de descargarSinCambiarVista siempre se desarman -----

    static Match enDirecto(long id) {
        Match m = partida(id, A, Instant.now());
        m.finished = null;   // en curso: download no la descarga (propone espectarla) y sale pronto
        return m;
    }

    @Test void descargarSinCambiarVista_queSalePronto_noDejaArmadaLaSiguiente() throws Exception {
        int[] avisos = { 0 };
        enEdt(() -> vista.descargarSinCambiarVista(List.of(enDirecto(8201)), false, () -> avisos[0]++));
        enEdt(() -> {
            assertFalse(vista.descargas.descargaSinCambiarVista, "desarmado aunque download saliera pronto");
            assertNull(vista.descargas.alTerminarDescarga, "desarmado aunque download saliera pronto");
        });
        anfitrion.mostrarDirectos.clear();
        enEdt(() -> vista.download(List.of(partida(8202, A, Instant.now().minusSeconds(600)))));   // una descarga normal
        esperar(() -> !anfitrion.progreso, "que la descarga termine");
        asentar();
        assertEquals(List.of(false), anfitrion.mostrarDirectos, "la descarga normal vuelve a la tabla, como siempre");
        assertEquals(0, avisos[0], "el aviso de la llamada anterior no lo dispara otra descarga");
    }

    @Test void descargarSinCambiarVista_superadaPorOtraOperacion_avisaIgualAlAcabar() throws Exception {
        rec.dentro = new CountDownLatch(1);
        rec.soltar = new CountDownLatch(1);
        int[] avisos = { 0 };
        enEdt(() -> vista.descargarSinCambiarVista(List.of(partida(8203, A, Instant.now().minusSeconds(600))), false, () -> avisos[0]++));
        assertTrue(rec.dentro.await(5, TimeUnit.SECONDS));
        enEdt(() -> anfitrion.trabajando(true));   // otra operación se lleva el semáforo
        rec.soltar.countDown();
        asentar();
        enEdt(() -> {
            assertEquals(1, avisos[0], "quien pidió la descarga se entera de que acabó (repinta su tabla)");
            assertNull(vista.descargas.alTerminarDescarga);
        });
        assertTrue(anfitrion.mostrarDirectos.isEmpty(), "sin cambiar de vista");
    }

    // ----- «Enviar al juego» fuera del EDT -----

    /** Corre {@code cuerpo} con la carpeta savegame de config apuntando a {@code sg}, y deja config.properties
     *  (el del directorio de trabajo del test, target/harness) como estaba. */
    static void conSavegame(Path sg, ThrowingRunnable cuerpo) throws Exception { conConfig("savegame", sg.toString(), cuerpo); }

    static void conConfig(String clave, String valor, ThrowingRunnable cuerpo) throws Exception {
        Path cfg = dev.tirador.aoe2radar.util.Config.CONFIG_FILE;
        byte[] previo = java.nio.file.Files.exists(cfg) ? java.nio.file.Files.readAllBytes(cfg) : null;
        try {
            dev.tirador.aoe2radar.util.Config.guardarConfig(clave, valor);
            cuerpo.run();
        } finally {
            if (previo != null) java.nio.file.Files.write(cfg, previo); else java.nio.file.Files.deleteIfExists(cfg);
        }
    }
    interface ThrowingRunnable { void run() throws Exception; }

    @Test void enviarAlJuego_leeYCopiaFueraDelEdt() throws Exception {
        Path sg = java.nio.file.Files.createDirectories(recs.resolve("savegame"));
        Match m = partida(8301, A, Instant.now().minusSeconds(600));
        java.nio.file.Files.write(recs.resolve("8301.aoe2record"), new byte[6000]);   // una rec «sana» (≥ 5000 B)
        conSavegame(sg, () -> {
            anfitrion.destinoEnEdt.clear();
            enEdt(() -> vista.enviarInteligente(List.of(m)));
            esperar(() -> anfitrion.estado.contains("recs enviadas al juego"), "el resumen del envío");
            assertFalse(anfitrion.destinoEnEdt.isEmpty(), "se miró la rec");
            assertFalse(anfitrion.destinoEnEdt.contains(true), "leer la cabecera y copiar no van en el EDT");
            assertTrue(rec.procesadas.isEmpty(), "una rec sana no se vuelve a descargar");
        });
    }

    /** Con una ruta imposible en config (InvalidPathException al mirarla), «Enviar al juego» dice el error y suelta
     *  la guarda: antes la excepción salía del done() y «enviando» se quedaba puesto (los clics siguientes, ignorados). */
    @Test void enviarAlJuego_conUnaRutaSavegameRara_avisaYSueltaLaGuarda() throws Exception {
        Match m = partida(8304, A, Instant.now().minusSeconds(600));
        java.nio.file.Files.write(recs.resolve("8304.aoe2record"), new byte[6000]);
        conConfig("savegame", "C:\\no<valida>|ruta", () -> {
            enEdt(() -> vista.enviarInteligente(List.of(m)));
            esperar(() -> anfitrion.estado.startsWith("Error: "), "el error de la carpeta savegame");
            esperar(() -> !vista.descargas.enviando, "la guarda suelta");
        });
    }

    /** «Descargar y enviar» con una ruta savegame rara en config: se descarga igual (sin copiar) y el semáforo
     *  de la operación se apaga; antes la excepción salía tras deshabilitar los botones y los dejaba así. */
    @Test void descargarYEnviar_conUnaRutaSavegameRara_descargaYNoSeQuedaTrabajando() throws Exception {
        Match m = partida(8305, A, Instant.now().minusSeconds(600));
        conConfig("savegame", "C:\\no<valida>|ruta", () -> {
            enEdt(() -> vista.download(List.of(m), true));
            esperar(() -> rec.procesadas.contains(8305L) && !anfitrion.progreso, "la descarga sin copia");
            esperar(() -> vista.dlSel.isEnabled(), "los botones de descarga vuelven");
        });
    }

    @Test void enviarAlJuego_loQueFaltaSeDescargaDespuesDeCopiar() throws Exception {
        Path sg = java.nio.file.Files.createDirectories(recs.resolve("savegame"));
        Match sana = partida(8302, A, Instant.now().minusSeconds(600));
        Match falta = partida(8303, A, Instant.now().minusSeconds(900));
        java.nio.file.Files.write(recs.resolve("8302.aoe2record"), new byte[6000]);
        conSavegame(sg, () -> {
            enEdt(() -> vista.enviarInteligente(List.of(sana, falta)));
            esperar(() -> rec.procesadas.contains(8303L) && !anfitrion.progreso, "la descarga de la que faltaba");
            int iCopia = -1, iDescarga = -1;
            synchronized (anfitrion.estados) {
                for (int i = 0; i < anfitrion.estados.size(); i++) {
                    String s = anfitrion.estados.get(i);
                    if (iCopia < 0 && s.contains("recs enviadas al juego")) iCopia = i;
                    if (iDescarga < 0 && s.contains("recs guardadas en")) iDescarga = i;
                }
            }
            assertTrue(iCopia >= 0, "hubo resumen de la copia: " + anfitrion.estados);
            assertTrue(iDescarga > iCopia, "primero se copia lo sano y después se descarga lo que falta: " + anfitrion.estados);
            assertEquals(List.of(8303L), rec.procesadas, "solo se descarga la que faltaba");
        });
    }

    // ----- applyFilters: «en disco» fuera del EDT -----

    @Test void applyFilters_miraElDiscoFueraDelEdtYAcabaPintandoLoMismo() throws Exception {
        Match enDisco = partida(8401, A, Instant.now().minusSeconds(600));
        Match sinRec = partida(8402, A, Instant.now().minusSeconds(900));
        java.nio.file.Files.write(recs.resolve("8401.aoe2record"), new byte[10]);
        anfitrion.destinoEnEdt.clear();
        enEdt(() -> vista.cargarPartidasEnTabla(List.of(enDisco, sinRec), A, "grupo|General"));   // llama a applyFilters
        esperar(() -> enDisco.enDisco, "que llegue la marca «en disco»");
        asentar();
        assertFalse(sinRec.enDisco);
        assertFalse(anfitrion.destinoEnEdt.isEmpty(), "se miró el disco");
        assertFalse(anfitrion.destinoEnEdt.contains(true), "los Files.exists de cada partida no van en el EDT");
        enEdt(() -> {
            assertEquals("✓ en disco", vista.tableModel.getValueAt(vista.view.indexOf(enDisco), 7));
            assertNotEquals("✓ en disco", vista.tableModel.getValueAt(vista.view.indexOf(sinRec), 7));
        });
    }

    // ----- textos (revisión 1.3, v13_textos.md): en inglés, nada en español fijo -----

    @Test void textos_enIngles_resultadoColumnaRecYEstadosDeDescarga() throws Exception {
        Match m = partida(8501, A, Instant.now().minusSeconds(600));
        m.players.get(0).won = true;
        enEdt(() -> vista.cargarPartidasEnTabla(List.of(m), A, "grupo|General"));
        asentar();
        IDIOMA = "en";   // se restaura en cerrar()
        String res = PartidasTexto.textoResultado(m);
        assertTrue(res.contains("Team 1  —  WINS"), res);
        assertFalse(res.contains("Equipo") || res.contains("GANA"), res);
        enEdt(() -> assertEquals("?", vista.tableModel.getValueAt(vista.view.indexOf(m), 7), "la columna Rec sin POV conocido"));
        enEdt(() -> vista.download(List.of(m)));
        esperar(() -> !anfitrion.progreso, "que la descarga termine");
        asentar();
        assertEquals("✓ saved", m.estado);
        assertTrue(anfitrion.estados.contains("1/1 recs saved to " + recs), "sin «./» delante de la carpeta: " + anfitrion.estados);
    }

    // ----- dudoso confirmado: «Espectar con CaptureAge» lanzaba CA dos veces con «Usar CaptureAge» -----

    @Test void espectarConCaptureAge_conUsarCaptureAge_noLoLanzaDosVeces() throws Exception {
        Match m = partida(8601, A, Instant.now());
        conConfig("usar_ca", "true", () -> enEdt(() -> vista.menuPartida.espectarConCaptureAge(m)));
        assertEquals(0, anfitrion.capturesLanzados, "con la casilla, lo lanza espectarPartida al abrir el juego (una vez)");
        assertEquals(List.of(8601L), anfitrion.espectadas);
    }

    @Test void espectarConCaptureAge_sinUsarCaptureAge_loLanzaUnaVez() throws Exception {
        Match m = partida(8602, A, Instant.now());
        conConfig("usar_ca", "false", () -> enEdt(() -> vista.menuPartida.espectarConCaptureAge(m)));
        assertEquals(1, anfitrion.capturesLanzados);
        assertEquals(List.of(8602L), anfitrion.espectadas);
    }

    @Test void busquedaQueAcabaDuranteUnaDescarga_laFilaSigueEnsenandoLaDescarga() throws Exception {
        // F7 (2.ª vuelta): la búsqueda trae la misma partida como OTRO Match; setEstado la buscaba por identidad y
        // «descargando… / ✓ guardada» dejaba de verse.
        Instant fin = Instant.now().minusSeconds(600);
        Match vieja = partida(8701, A, fin);
        rec.dentro = new CountDownLatch(1);
        rec.soltar = new CountDownLatch(1);
        rec.escribirEn = recs;
        enEdt(() -> vista.cargarPartidasEnTabla(List.of(vieja), A, "grupo|General"));
        enEdt(() -> vista.download(List.of(vieja)));
        assertTrue(rec.dentro.await(5, TimeUnit.SECONDS), "la descarga está en marcha");
        enlace.invitado = A;
        anfitrion.paginador = (pid, pag, pp) -> List.of(partida(8701, A, fin));   // la misma partida, otro objeto
        enEdt(() -> vista.fetchMatches(vista.fetchBtn));
        esperar(() -> vista.fetchWorker == null, "que la búsqueda termine");
        asentar();
        Match nueva = vista.view.get(0);
        assertNotSame(vieja, nueva);
        assertEquals("descargando…", nueva.estado, "la fila de la búsqueda nueva conserva el estado de la descarga");
        rec.soltar.countDown();
        esperar(() -> "✓ guardada".equals(nueva.estado), "el «✓ guardada» llega a la fila nueva");
        esperar(() -> nueva.enDisco, "y su marca «en disco»");
        asentar();
        enEdt(() -> assertEquals("✓ guardada", vista.tableModel.getValueAt(vista.view.indexOf(nueva), 7)));
        assertTrue(nueva.enDisco, "remarcar al acabar la descarga no la desmarca");
    }

    @Test void enviarAlJuego_segundoClicMientrasSigueElPrimero_noLanzaOtro() throws Exception {
        Path sg = java.nio.file.Files.createDirectories(recs.resolve("savegame"));
        Match m = partida(8801, A, Instant.now().minusSeconds(600));
        java.nio.file.Files.write(recs.resolve("8801.aoe2record"), new byte[6000]);
        conSavegame(sg, () -> {
            anfitrion.soltarDestino = new CountDownLatch(1);
            enEdt(() -> vista.enviarInteligente(List.of(m)));
            enEdt(() -> vista.enviarInteligente(List.of(m)));   // el segundo clic, con el primero mirando recs
            assertEquals("Ya se está enviando al juego: espera a que acabe.", anfitrion.estado);
            anfitrion.soltarDestino.countDown();
            esperar(() -> anfitrion.estado.contains("recs enviadas al juego"), "que acabe el primero");
            anfitrion.soltarDestino = null;
            anfitrion.estados.clear();
            enEdt(() -> vista.enviarInteligente(List.of(m)));   // acabado el primero, se puede volver a enviar
            esperar(() -> anfitrion.estado.contains("recs enviadas al juego"), "el tercero");
            assertFalse(anfitrion.estados.stream().anyMatch(s -> s.startsWith("Ya se está enviando")));
        });
    }

    @Test void enviarAlJuego_siFallaAlMirarLasRecs_loDiceYSePuedeReintentar() throws Exception {
        Match m = partida(8802, A, Instant.now().minusSeconds(600));
        anfitrion.fallarDestino = new IllegalStateException("disco roto");
        enEdt(() -> vista.enviarInteligente(List.of(m)));
        esperar(() -> anfitrion.estado.startsWith("Error: "), "el error en la barra");
        assertTrue(anfitrion.estado.contains("disco roto"), anfitrion.estado);
        assertFalse(vista.descargas.enviando, "tras el error se puede volver a enviar");
        assertTrue(rec.procesadas.isEmpty(), "no se descarga nada a ciegas");
    }

    @Test void botonBuscar_conUnaBusquedaEnMarcha_laDetiene() throws Exception {
        enlace.jugadores.addAll(List.of(A, B));
        CountDownLatch enA = new CountDownLatch(1), soltarA = new CountDownLatch(1);
        Instant fin = Instant.now().minusSeconds(600);
        anfitrion.paginador = (pid, pag, pp) -> {
            if (pid == A.id()) { enA.countDown(); soltarA.await(5, TimeUnit.SECONDS); }
            return List.of(partida(pid, pid == A.id() ? A : B, fin));
        };
        enEdt(() -> vista.fetchBtn.doClick());
        assertTrue(enA.await(5, TimeUnit.SECONDS));
        enEdt(() -> vista.fetchBtn.doClick());   // el mismo botón, ahora «Detener»
        soltarA.countDown();
        asentar();
        enEdt(() -> {
            assertNull(vista.fetchWorker);
            assertTrue(vista.all.isEmpty());
            assertEquals("Búsqueda detenida.", anfitrion.estado);
        });
        assertFalse(anfitrion.pedidas.contains(B.id()));
    }
}
