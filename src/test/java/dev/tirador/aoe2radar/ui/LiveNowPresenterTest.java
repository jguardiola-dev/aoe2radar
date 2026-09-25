package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LiveNowPresenter con Tareas.EN_LINEA (sin hilos) y dobles del buscador (LiveService.partidas) y de la Pantalla:
 * comprueba el barrido por lotes (caché de 60 s, circuito de tres fallos, marcado de en-curso/terminadas) y el
 * evento del socket (liveEvento), incluido que respeta puedeRepintar() antes de programar el repintado.
 * <p>Usa ids de partida grandes y propios de cada test porque EstadoVivo.SISTEMA es un singleton compartido por
 * toda la JVM de test (igual que en la 1.1: un solo VIVO para toda la app).
 */
class LiveNowPresenterTest {

    /** Un buscador de mentira: lo que LiveNowPresenter pide a LiveService.partidas por lote. */
    static final class BuscadorFalso implements LiveNowPresenter.Buscador {
        List<Match> resultado = new ArrayList<>();
        RuntimeException falla;
        int llamadas;
        final List<String> csvsPedidos = new ArrayList<>();
        @Override public Iterable<Match> partidas(String pidsCsv, int pagina, int porPagina) {
            llamadas++;
            csvsPedidos.add(pidsCsv);
            if (falla != null) throw falla;
            return resultado;
        }
    }

    /** La Pantalla de mentira: guarda lo que el presentador le pide, como haría LiveNowView. */
    static final class PantallaFalsa implements LiveNowPresenter.Pantalla {
        final List<String> estados = new ArrayList<>();
        int pintarVeces;
        List<Object[]> fuente = new ArrayList<>();
        Exception fuenteError;
        int cargarFuenteLiveLlamadas;
        final java.util.Set<Long> conCampana = new java.util.HashSet<>();
        int sincronizarSocketVeces;
        boolean abierta = true;
        boolean puedeRepintar = true;

        @Override public void estado(String texto) { estados.add(texto); }
        @Override public void pintar() { pintarVeces++; }
        @Override public List<Object[]> cargarFuenteLive() throws Exception {
            cargarFuenteLiveLlamadas++;
            if (fuenteError != null) throw fuenteError;
            return fuente;
        }
        @Override public boolean campanaContiene(long pid) { return conCampana.contains(pid); }
        @Override public void sincronizarSocket() { sincronizarSocketVeces++; }
        @Override public boolean abierta() { return abierta; }
        @Override public boolean puedeRepintar() { return puedeRepintar; }
    }

    /** Un Tareas que encola el trabajo de fondo en vez de ejecutarlo: para comprobar la guarda «ya cargando» antes
     *  de que el primer barrido termine, como pasaría de verdad con dos hilos. enUi sigue en el acto. */
    static final class TareasAplazadas implements Tareas {
        final List<Runnable> pendientesFondo = new ArrayList<>();
        @Override public void enFondo(String nombre, Runnable trabajo) { pendientesFondo.add(trabajo); }
        @Override public void enFondoDemonio(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enFondoDemonioMinima(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enUi(Runnable trabajo) { trabajo.run(); }
    }

    static Object[] ficha(long pid, String nombre, int rating, int rango, String pais) { return new Object[]{ pid, nombre, rating, rango, pais }; }

    static Match matchEnCurso(long id, long pid) {
        Match m = new Match();
        m.id = id; m.started = Instant.now(); m.map = "Arabia"; m.mode = "1v1 Random Map";
        MatchPlayer p = new MatchPlayer(); p.id = pid; p.name = "Uno"; p.team = 1;
        m.players.add(p);
        return m;
    }

    static Match matchTerminado(long id, long pid) {
        Match m = matchEnCurso(id, pid);
        m.finished = Instant.now().minusSeconds(600);
        return m;
    }

    final BuscadorFalso buscador = new BuscadorFalso();
    final PantallaFalsa pantalla = new PantallaFalsa();
    final LiveNowPresenter presenter = new LiveNowPresenter(buscador, java.util.concurrent.ConcurrentHashMap.newKeySet(), Tareas.EN_LINEA, pantalla);

    // ----- refrescar: fuente, caché y suscripción del socket -----------------------------

    @Test void refrescar_primeraVez_pideLaFuenteYSuscribeElSocket() {
        pantalla.fuente = List.<Object[]>of(ficha(101L, "Uno", 1500, 10, "es"));
        presenter.refrescar(false);
        assertEquals(1, pantalla.cargarFuenteLiveLlamadas);
        assertEquals(1, pantalla.sincronizarSocketVeces);
        assertEquals(1, pantalla.pintarVeces);
        assertEquals(1, presenter.topSnapshot().size());
    }

    @Test void refrescar_conCacheReciente_soloRepintaSinVolverAConsultar() {
        pantalla.fuente = List.<Object[]>of(ficha(101L, "Uno", 1500, 10, "es"));
        presenter.refrescar(true);
        int llamadasAntes = buscador.llamadas, fuenteAntes = pantalla.cargarFuenteLiveLlamadas;
        presenter.refrescar(false);   // menos de 60 s desde el barrido anterior: ni fuente ni lotes
        assertEquals(llamadasAntes, buscador.llamadas);
        assertEquals(fuenteAntes, pantalla.cargarFuenteLiveLlamadas);
        assertEquals(2, pantalla.pintarVeces);
    }

    @Test void refrescar_forzado_ignoraLaCacheYVuelveAConsultar() {
        pantalla.fuente = List.<Object[]>of(ficha(101L, "Uno", 1500, 10, "es"));
        presenter.refrescar(true);
        int llamadasAntes = buscador.llamadas;
        presenter.refrescar(true);   // forzar: aunque sea reciente, vuelve a pedir el lote
        assertTrue(buscador.llamadas > llamadasAntes);
    }

    @Test void refrescar_yaCargando_noRelanza() {
        TareasAplazadas tareas = new TareasAplazadas();
        LiveNowPresenter p = new LiveNowPresenter(buscador, java.util.concurrent.ConcurrentHashMap.newKeySet(), tareas, pantalla);
        pantalla.fuente = List.<Object[]>of(ficha(101L, "Uno", 1500, 10, "es"));
        p.refrescar(false);   // encola el barrido, no lo ejecuta: ahoraCargando ya queda a true
        assertEquals(1, tareas.pendientesFondo.size());
        p.refrescar(false);   // segunda apertura mientras el barrido «viaja»
        assertEquals(1, tareas.pendientesFondo.size());
        assertEquals(1, pantalla.estados.size());   // solo el «Consultando…» de la primera vez
    }

    // ----- barrido por lotes: en curso, terminadas y circuito de fallos -------------------

    @Test void refrescar_marcaEnCursoYTerminadasSegunLaRespuesta() {
        pantalla.fuente = List.<Object[]>of(ficha(201L, "Vivo", 1500, 10, "es"), ficha(202L, "Fin", 1400, 20, "es"));
        buscador.resultado = List.of(matchEnCurso(9001L, 201L), matchTerminado(9002L, 202L));
        presenter.refrescar(false);
        assertTrue(presenter.enCursoSnapshot().containsKey(201L));
        assertTrue(presenter.terminadasVigentes().stream().anyMatch(x -> ((Match) x[0]).id == 9002L));
        assertFalse(presenter.enCursoSnapshot().containsKey(202L));
    }

    @Test void refrescar_tresLotesFallidosSeguidos_abortaElRestante() {
        List<Object[]> fuente = new ArrayList<>();
        for (long i = 1; i <= 60; i++) fuente.add(ficha(300 + i, "J" + i, 1000, (int) i, "es"));   // 4 lotes de 15
        pantalla.fuente = fuente;
        buscador.falla = new RuntimeException("sin red");
        presenter.refrescar(false);
        // «Consultando…» inicial + un intento de progreso por cada uno de los tres primeros lotes; el cuarto ni se intenta
        assertEquals(4, pantalla.estados.size());
        assertEquals(3, buscador.llamadas);
        assertTrue(presenter.enCursoSnapshot().isEmpty());
    }

    @Test void refrescar_pestanaCerrada_noLlegaAPedirNingunLote() {
        pantalla.fuente = List.<Object[]>of(ficha(401L, "Uno", 1500, 10, "es"));
        pantalla.abierta = false;
        presenter.refrescar(false);
        assertEquals(1, pantalla.cargarFuenteLiveLlamadas);   // la fuente sí se carga (no depende de «abierta»)
        assertEquals(0, buscador.llamadas);                   // pero el barrido por lotes se corta antes del primero
    }

    @Test void refrescar_fallaLaFuente_avisaElErrorYNoRompe() {
        pantalla.fuenteError = new java.io.IOException("sin red");
        presenter.refrescar(false);
        assertTrue(pantalla.estados.get(pantalla.estados.size() - 1).contains("sin red"));
        assertTrue(presenter.topSnapshot().isEmpty());
    }

    @Test void reiniciarFuente_borraLoCacheado() {
        pantalla.fuente = List.<Object[]>of(ficha(101L, "Uno", 1500, 10, "es"));
        presenter.refrescar(true);
        assertFalse(presenter.topSnapshot().isEmpty());
        presenter.reiniciarFuente();
        assertTrue(presenter.topSnapshot().isEmpty());
        assertEquals(0, presenter.ultimaMs());
    }

    // ----- liveEvento: seguro desde cualquier hilo, respeta puedeRepintar() ---------------

    @Test void liveEvento_pidFueraDeLaFuente_noHaceNadaNiRepinta() {
        pantalla.fuente = List.<Object[]>of(ficha(101L, "Uno", 1500, 10, "es"));
        presenter.refrescar(false);
        int pintarAntes = pantalla.pintarVeces;
        presenter.liveEvento(999L, matchEnCurso(9101L, 999L), false);
        assertTrue(presenter.enCursoSnapshot().isEmpty());
        assertEquals(pintarAntes, pantalla.pintarVeces);
    }

    @Test void liveEvento_entraEnPartida_laApuntaYRepintaSiPuedeRepintar() {
        pantalla.fuente = List.<Object[]>of(ficha(501L, "Uno", 1500, 10, "es"));
        presenter.refrescar(false);   // dado de alta en la fuente actual
        int pintarAntes = pantalla.pintarVeces;
        pantalla.puedeRepintar = true;
        Match m = matchEnCurso(9201L, 501L);
        presenter.liveEvento(501L, m, false);
        assertEquals(m, presenter.enCursoSnapshot().get(501L));
        assertEquals(pintarAntes + 1, pantalla.pintarVeces);
    }

    @Test void liveEvento_terminaLaPartida_laMueveATerminadasYLaQuitaDeEnCurso() {
        pantalla.fuente = List.<Object[]>of(ficha(601L, "Uno", 1500, 10, "es"));
        presenter.refrescar(false);
        Match m = matchEnCurso(9301L, 601L);
        presenter.liveEvento(601L, m, false);
        assertTrue(presenter.enCursoSnapshot().containsKey(601L));
        presenter.liveEvento(601L, m, true);
        assertFalse(presenter.enCursoSnapshot().containsKey(601L));
        assertTrue(presenter.terminadasVigentes().stream().anyMatch(x -> ((Match) x[0]).id == 9301L));
    }

    @Test void liveEvento_noRepintaSiLaPantallaNoLoPermite() {
        pantalla.fuente = List.<Object[]>of(ficha(701L, "Uno", 1500, 10, "es"));
        presenter.refrescar(false);
        pantalla.puedeRepintar = false;
        int pintarAntes = pantalla.pintarVeces;
        presenter.liveEvento(701L, matchEnCurso(9401L, 701L), false);
        assertEquals(pintarAntes, pantalla.pintarVeces);
        assertTrue(presenter.enCursoSnapshot().containsKey(701L));   // el estado se actualiza igual; solo el repintado se salta
    }
}
