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
        @Override public void mostrarDirectos(boolean mostrar) { }
        @Override public void refrescarDirectos() { }
        @Override public void enfocarBuscador() { }
        @Override public boolean confirmarEspectar(String nombre) { return false; }
        @Override public void espectarVerificando(long profileId, long matchId) { }
        @Override public void espectarPartida(long matchId) { }
        @Override public void lanzarCaptureAge(Path rec) { }
        @Override public void abrirUrl(String url) { }
        @Override public String nombreVisible(long pid, String nombre) { return nombre; }
        @Override public String paisDe(long pid) { return null; }
        @Override public boolean enCursoReal(Match m) { return false; }
        @Override public Path destino(Match m) { return recs.resolve(m.id + ".aoe2record"); }
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

    static final Player A = new Player(7001L, "Ana", "General", 0L);
    static final Player B = new Player(7002L, "Beto", "General", 0L);
    static final Player C = new Player(7003L, "Cris", "General", 0L);

    @TempDir Path recs;
    String idiomaPrevio;
    JFrame ventana;
    EnlaceFalso enlace;
    AnfitrionFalso anfitrion;
    PartidasView vista;

    @BeforeEach void crear() throws Exception {
        idiomaPrevio = IDIOMA;
        IDIOMA = "es";
        enlace = new EnlaceFalso();
        anfitrion = new AnfitrionFalso(recs);
        CompanionApi companion = new CompanionApi(new ApiClient(new WatchlistViewTest.ThrottleSinFreno(),
                new WatchlistViewTest.TransporteNuncaLlamado(), s -> { }, () -> false));
        BarridoVivos barrido = new BarridoVivos(companion, new RelojFalso(), (m, pid) -> "r", new HashMap<>(), ms -> { }, 0, 50);
        SwingUtilities.invokeAndWait(() -> {
            ventana = new JFrame();
            vista = new PartidasView(ventana, new WatchlistViewTest.MenusFalso(), null, new WatchlistViewTest.NavegacionFalsa(),
                    null, null, barrido, 50, 0, enlace, anfitrion);
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
}
