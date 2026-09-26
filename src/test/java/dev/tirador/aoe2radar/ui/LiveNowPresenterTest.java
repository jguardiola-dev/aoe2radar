package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.util.Reloj;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LiveNowPresenter con Tareas.EN_LINEA (sin hilos) y dobles del buscador (LiveService.partidas) y de la Pantalla:
 * comprueba el barrido por lotes (caché de 60 s, circuito de tres fallos, marcado de en-curso/terminadas) y el
 * evento del socket (liveEvento), incluido que respeta puedeRepintar() antes de programar el repintado.
 * <p>Cada presentador se construye con su propio EstadoVivo (DEUDA, fila 125: se inyecta por constructor), así
 * que ningún test comparte el singleton EstadoVivo.SISTEMA de toda la app con otro.
 */
class LiveNowPresenterTest {

    /** Un buscador de mentira: lo que LiveNowPresenter pide a LiveService.partidas por lote. */
    static final class BuscadorFalso implements LiveNowPresenter.Buscador {
        List<Match> resultado = new ArrayList<>();
        RuntimeException falla;
        Runnable alPedir;   // lo que pasa «mientras» se consulta un lote (un evento del socket, un cambio de fuente…)
        int llamadas;
        final List<String> csvsPedidos = new ArrayList<>();
        @Override public Iterable<Match> partidas(String pidsCsv, int pagina, int porPagina) {
            llamadas++;
            csvsPedidos.add(pidsCsv);
            if (alPedir != null) alPedir.run();
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

    /** Guarda el nombre de hilo con el que se llamó enFondo/enFondoDemonio, sin ejecutar el trabajo demonio (así
     *  pedirClanes() no llega a tocar ConsultasLadder de verdad): para comprobar que un hilo movido a un
     *  presentador conserva su nombre (DEUDA, fila 124). */
    static final class TareasQueGuardaNombre implements Tareas {
        String ultimoNombreFondo, ultimoNombreDemonio;
        Runnable trabajoDemonioPendiente;
        @Override public void enFondo(String nombre, Runnable trabajo) { ultimoNombreFondo = nombre; trabajo.run(); }
        @Override public void enFondoDemonio(String nombre, Runnable trabajo) { ultimoNombreDemonio = nombre; trabajoDemonioPendiente = trabajo; }
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
    final LiveNowPresenter presenter = new LiveNowPresenter(buscador, java.util.concurrent.ConcurrentHashMap.newKeySet(), Tareas.EN_LINEA, pantalla, new EstadoVivo(Reloj.SISTEMA));

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
        LiveNowPresenter p = new LiveNowPresenter(buscador, java.util.concurrent.ConcurrentHashMap.newKeySet(), tareas, pantalla, new EstadoVivo(Reloj.SISTEMA));
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
        // «Consultando…» inicial + un intento de progreso por cada uno de los tres primeros lotes; el cuarto ni se intenta.
        // Desde la 1.3 (F7), un quinto: el aviso de que el barrido quedó incompleto (antes solo iba al log).
        assertEquals(5, pantalla.estados.size());
        assertTrue(pantalla.estados.get(4).startsWith(dev.tirador.aoe2radar.util.I18n.t("Barrido incompleto", "Incomplete sweep")), pantalla.estados.get(4));
        assertEquals(3, buscador.llamadas);
        assertTrue(presenter.enCursoSnapshot().isEmpty());
        assertEquals(0, presenter.ultimaMs(), "un barrido cortado no cuenta como hecho (F7)");
    }

    @Test void refrescar_pestanaCerrada_noLlegaAPedirNingunLote() {
        pantalla.fuente = List.<Object[]>of(ficha(401L, "Uno", 1500, 10, "es"));
        pantalla.abierta = false;
        presenter.refrescar(false);
        assertEquals(1, pantalla.cargarFuenteLiveLlamadas);   // la fuente sí se carga (no depende de «abierta»)
        assertEquals(0, buscador.llamadas);                   // pero el barrido por lotes se corta antes del primero
        assertEquals(0, presenter.ultimaMs(), "y no cuenta como hecho: al abrir la pestaña se barre de verdad (F7)");
    }

    // ----- barrido y cambios durante el barrido (revisión 1.3, F7) --------------------------

    /** El fondo, en el acto; lo del EDT, a una cola que el test vacía cuando quiere (como el EDT de verdad, que lo
     *  ejecuta «después»): así se ve el estado que queda entre el final de un barrido y lo que programó para el EDT. */
    static final class TareasUiAplazada implements Tareas {
        final List<Runnable> pendientesUi = new ArrayList<>();
        @Override public void enFondo(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enFondoDemonio(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enFondoDemonioMinima(String nombre, Runnable trabajo) { trabajo.run(); }
        @Override public void enUi(Runnable trabajo) { pendientesUi.add(trabajo); }
        void vaciarUi() { while (!pendientesUi.isEmpty()) pendientesUi.remove(0).run(); }
    }

    @Test void refrescar_cambioDeFuenteDuranteElBarrido_descartaLaViejaYBarreLaNueva() {
        TareasUiAplazada tareas = new TareasUiAplazada();
        LiveNowPresenter p = new LiveNowPresenter(buscador, java.util.concurrent.ConcurrentHashMap.newKeySet(), tareas, pantalla, new EstadoVivo(Reloj.SISTEMA));
        pantalla.fuente = List.<Object[]>of(ficha(901L, "Viejo", 2500, 1, "es"));
        buscador.resultado = List.of(matchEnCurso(9901L, 901L), matchTerminado(9903L, 901L));
        buscador.alPedir = () -> {   // en mitad del primer barrido, el usuario cambia de fuente (cambiarFuenteLive)
            buscador.alPedir = null;
            pantalla.fuente = List.<Object[]>of(ficha(902L, "Nuevo", 1500, 3, "es"));
            p.reiniciarFuente();
            p.refrescar(true);
        };
        p.refrescar(true);
        // el barrido viejo acabó: no ha escrito nada suyo bajo la fuente nueva (antes: sus tarjetas y «barrido hace 0 min»)
        assertFalse(p.enCursoSnapshot().containsKey(901L), "sin partidas de la fuente anterior bajo el título nuevo");
        assertTrue(p.terminadasVigentes().isEmpty(), "ni terminadas de la fuente anterior");
        assertEquals(0, p.ultimaMs());
        tareas.vaciarUi();   // el EDT: relanza el forzado que llegó durante el barrido
        assertEquals(List.of(902L), p.topSnapshot().stream().map(f -> (Long) f[0]).toList(), "la fuente que se ve es la nueva");
        assertEquals(2, pantalla.cargarFuenteLiveLlamadas, "la nueva se cargó al acabar el barrido viejo");
        assertTrue(p.ultimaMs() > 0, "y su barrido sí cuenta");
    }

    @Test void refrescar_forzadoDuranteUnBarrido_seRepiteAlAcabar() {
        TareasAplazadas tareas = new TareasAplazadas();
        LiveNowPresenter p = new LiveNowPresenter(buscador, java.util.concurrent.ConcurrentHashMap.newKeySet(), tareas, pantalla, new EstadoVivo(Reloj.SISTEMA));
        pantalla.fuente = List.<Object[]>of(ficha(911L, "Uno", 1500, 10, "es"));
        p.refrescar(false);
        p.refrescar(true);   // «Actualizar» (o un cambio de fuente) con el barrido en marcha
        assertEquals(1, tareas.pendientesFondo.size());
        tareas.pendientesFondo.get(0).run();   // acaba el primero…
        assertEquals(2, tareas.pendientesFondo.size(), "…y el forzado que llegó entretanto se lanza entonces");
    }

    @Test void refrescar_cortadoAlCerrarLaPestana_noCuentaComoHechoYConservaLoNoConsultado() {
        List<Object[]> fuente = new ArrayList<>();
        for (long i = 1; i <= 20; i++) fuente.add(ficha(920 + i, "J" + i, 1000, (int) i, "es"));   // 2 lotes de 15
        pantalla.fuente = fuente;
        Match delSegundoLote = matchEnCurso(9940L, 940L);
        buscador.resultado = List.of(delSegundoLote);
        presenter.refrescar(true);   // barrido completo: 940 en partida
        assertTrue(presenter.enCursoSnapshot().containsKey(940L));
        presenter.fijarUltimaMs(1234L);
        buscador.resultado = List.of();
        buscador.alPedir = () -> pantalla.abierta = false;   // se cierra la pestaña tras el primer lote
        presenter.refrescar(true);
        assertEquals(1234L, presenter.ultimaMs(), "cortado: no se apunta como barrido hecho");
        assertEquals(delSegundoLote, presenter.enCursoSnapshot().get(940L), "a 940 no se le consultó: se queda como estaba");
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

    // ----- ficha: mismo candado que topSnapshot/conTop (DEUDA, fila 123) ------------------

    /** Sin el candado, un escritor que hace conTop(t -> { t.clear(); t.addAll(...); }) mientras un lector recorre
     *  ahoraTop en ficha() dispara casi siempre una ConcurrentModificationException en unos pocos cientos de ms:
     *  esta es la mutación que demuestra el arreglo (quítalo y este test se pone en rojo). Es una prueba de
     *  concurrencia real (dos hilos, sin candado): el rojo es muy probable pero no está garantizado al 100%;
     *  lo comprobé a mano 3 de 3 veces al quitar el synchronized. */
    @Test void ficha_esSeguraFrenteAEscriturasConcurrentesDeAhoraTop() throws InterruptedException {
        List<Object[]> base = new ArrayList<>();
        for (long i = 0; i < 400; i++) base.add(ficha(i, "J" + i, 1000, (int) i, "es"));
        presenter.conTop(t -> t.addAll(base));
        AtomicBoolean parar = new AtomicBoolean(false);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread escritor = new Thread(() -> {
            try { while (!parar.get()) presenter.conTop(t -> { t.clear(); t.addAll(base); }); }
            catch (Throwable ex) { error.set(ex); }
        }, "test-escritor-ahoraTop");
        Thread lector = new Thread(() -> {
            try { long fin = System.currentTimeMillis() + 500; while (System.currentTimeMillis() < fin) presenter.ficha(200L); }
            catch (Throwable ex) { error.set(ex); }
        }, "test-lector-ficha");
        escritor.start(); lector.start();
        lector.join(3000);
        parar.set(true);
        escritor.join(1000);
        assertNull(error.get(), () -> "ficha() debe leer bajo el mismo candado que topSnapshot/conTop: " + error.get());
    }

    // ----- pedirResultado / pedirClanes: hilos movidos desde LiveNowView (DEUDA, fila 124) ------

    @Test void pedirResultado_consultaLaApiConElCsvDeUnSoloPidYActualizaWonAntesDeAvisar() {
        TareasQueGuardaNombre tareas = new TareasQueGuardaNombre();
        LiveNowPresenter p = new LiveNowPresenter(buscador, java.util.concurrent.ConcurrentHashMap.newKeySet(), tareas, pantalla, new EstadoVivo(Reloj.SISTEMA));
        Match m = matchTerminado(9501L, 501L);
        Match conResultado = matchTerminado(9501L, 501L);
        conResultado.players.get(0).won = true;
        buscador.resultado = List.of(conResultado);
        boolean[] avisado = { false };
        p.pedirResultado(m, () -> avisado[0] = true);
        assertEquals("resultado", tareas.ultimoNombreFondo, "el hilo conserva su nombre de antes (log y volcados de hilos)");
        assertEquals(List.of("501"), buscador.csvsPedidos, "mismo CSV que LiveService.partidas(pid0, 1, 5)");
        assertEquals(Boolean.TRUE, m.players.get(0).won, "el won de la partida original queda actualizado");
        assertTrue(avisado[0], "el callback se llama al terminar, en el EDT (tareas.enUi)");
    }

    @Test void pedirResultado_siLaApiFallaNoRompeYAunAsiAvisa() {
        buscador.falla = new RuntimeException("sin red");
        boolean[] avisado = { false };
        presenter.pedirResultado(matchTerminado(9502L, 502L), () -> avisado[0] = true);
        assertTrue(avisado[0]);
    }

    @Test void pedirClanes_lanzaUnHiloDemonioLlamadoClanes() {
        TareasQueGuardaNombre tareas = new TareasQueGuardaNombre();
        LiveNowPresenter p = new LiveNowPresenter(buscador, java.util.concurrent.ConcurrentHashMap.newKeySet(), tareas, pantalla, new EstadoVivo(Reloj.SISTEMA));
        p.pedirClanes();
        assertEquals("clanes", tareas.ultimoNombreDemonio);
        assertNotNull(tareas.trabajoDemonioPendiente);
    }

    // ----- el barrido fusiona con lo que llegó del socket entretanto (revisión 1.3, F2) --------

    @Test void refrescar_conservaLaPartidaQueElSocketConfirmoDuranteElBarrido() {
        EstadoVivo vivo = new EstadoVivo(Reloj.SISTEMA);
        LiveNowPresenter p = new LiveNowPresenter(buscador, java.util.concurrent.ConcurrentHashMap.newKeySet(), Tareas.EN_LINEA, pantalla, vivo);
        pantalla.fuente = List.<Object[]>of(ficha(801L, "Y", 1500, 10, "es"), ficha(802L, "Z", 1400, 20, "es"));
        Match nueva = matchEnCurso(9801L, 801L);
        buscador.resultado = List.of();   // la foto del lote: nadie en partida todavía
        buscador.alPedir = () -> {        // mientras tanto, el socket confirma la partida de Y (como hace EnlaceVivo)
            vivo.marcarJugando(801L, 9801L);
            p.liveEvento(801L, nueva, false);
        };
        p.refrescar(true);
        assertEquals(nueva, p.enCursoSnapshot().get(801L), "la partida confirmada por el socket no la borra la foto vieja del barrido");
    }

    @Test void refrescar_quitaLoQueSoloVioUnBarridoAnteriorSiElNuevoNoLoVe() {
        EstadoVivo vivo = new EstadoVivo(Reloj.SISTEMA);
        LiveNowPresenter p = new LiveNowPresenter(buscador, java.util.concurrent.ConcurrentHashMap.newKeySet(), Tareas.EN_LINEA, pantalla, vivo);
        pantalla.fuente = List.<Object[]>of(ficha(811L, "Y", 1500, 10, "es"));
        buscador.resultado = List.of(matchEnCurso(9811L, 811L));
        p.refrescar(true);
        assertTrue(p.enCursoSnapshot().containsKey(811L));
        buscador.resultado = List.of();   // el barrido siguiente ya no la ve, y el socket no la tiene: fuera, como antes
        p.refrescar(true);
        assertFalse(p.enCursoSnapshot().containsKey(811L));
    }

    @Test void refrescar_noConservaUnaPartidaDelSocketQueYaSeSabeTerminada() {
        EstadoVivo vivo = new EstadoVivo(Reloj.SISTEMA);
        LiveNowPresenter p = new LiveNowPresenter(buscador, java.util.concurrent.ConcurrentHashMap.newKeySet(), Tareas.EN_LINEA, pantalla, vivo);
        pantalla.fuente = List.<Object[]>of(ficha(821L, "Y", 1500, 10, "es"));
        Match m = matchEnCurso(9821L, 821L);
        buscador.resultado = List.of();
        buscador.alPedir = () -> { vivo.marcarJugando(821L, 9821L); p.liveEvento(821L, m, false); vivo.apuntarTerminada(9821L); };
        p.refrescar(true);
        assertFalse(p.enCursoSnapshot().containsKey(821L));
    }

    // ----- puesto de la ficha: la fuente «Grupo» no tiene puesto (revisión 1.3, F3) ----------

    @Test void fichaDeGrupoConRangoCero_notienePuestoNiCuentaComoTop() {
        Object[] deGrupo = ficha(1L, "Amigo", 1500, 0, "es");   // Campanas.cargarFuenteLive pone rango 0 en «grupo»
        assertFalse(LiveNowPresenter.conPuesto(deGrupo), "sin puesto: no se pinta «#0»");
        assertFalse(LiveNowPresenter.enTop(deGrupo, 50), "no cuenta para «top 50 vs top 50» ni para «Solo top contra top»");
        assertFalse(LiveNowPresenter.enTop(deGrupo, 25), "ni para «élite 25 vs 25»");
        assertFalse(LiveNowPresenter.enTop(null, 50), "fuera de la fuente: tampoco");
    }

    @Test void fichaConPuesto_cuentaSoloHastaElTope() {
        assertTrue(LiveNowPresenter.conPuesto(ficha(1L, "Uno", 2500, 1, "es")));
        assertTrue(LiveNowPresenter.enTop(ficha(1L, "Uno", 2500, 50, "es"), 50));
        assertFalse(LiveNowPresenter.enTop(ficha(1L, "Uno", 2500, 51, "es"), 50));
        assertFalse(LiveNowPresenter.enTop(ficha(1L, "Uno", 2500, 26, "es"), 25));
    }

    // ----- EstadoVivo inyectado, no el singleton global (DEUDA, fila 125) -----------------

    @Test void usaElEstadoVivoInyectadoNoElSingletonGlobal() {
        EstadoVivo propio = new EstadoVivo(Reloj.SISTEMA);
        LiveNowPresenter p = new LiveNowPresenter(buscador, java.util.concurrent.ConcurrentHashMap.newKeySet(), Tareas.EN_LINEA, pantalla, propio);
        pantalla.fuente = List.<Object[]>of(ficha(9901L, "Uno", 1500, 10, "es"));
        buscador.resultado = List.of(matchEnCurso(99001L, 9901L));
        p.refrescar(false);
        assertNotNull(propio.partida(9901L), "se guardó en el EstadoVivo inyectado");
        assertNull(EstadoVivo.SISTEMA.partida(9901L), "y no en el singleton global de la app");
    }
}
