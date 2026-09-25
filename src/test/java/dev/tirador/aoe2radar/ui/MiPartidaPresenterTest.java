package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.service.MiPartidaService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MiPartidaPresenter con Tareas.EN_LINEA: el tick del log (aviso temprano + sondeo del lobby, solo si el
 * servicio dice que toca), «Mi perfil» (identidad conocida abre el perfil directamente; si no, pide el nick) y
 * la búsqueda del nick (sin resultados, uno solo, varios, cancelado), tal cual preguntarMiNick/abrirMiPerfil
 * de la 1.1.
 */
class MiPartidaPresenterTest {

    static final class ServicioFalso implements MiPartidaService {
        Identidad identidad;
        boolean leerLogJuego;
        ResultadoLobby lobby = new ResultadoLobby(false, "", List.of());
        long fijadaPid = -1; String fijadaNombre;

        @Override public Identidad identidad() { return identidad; }
        @Override public void fijarIdentidad(long pid, String nombre) { fijadaPid = pid; fijadaNombre = nombre; }
        @Override public void registrarInicioVigilancia() { }
        @Override public boolean leerLogJuego() { return leerLogJuego; }
        @Override public ResultadoLobby sondearLobbyOficial() { return lobby; }
        @Override public String paisDe(long pid) { return null; }
        @Override public List<String> civsRecientes(long pid) { return List.of(); }
    }

    static final class NavegacionFalsa implements Navegacion {
        long pidAbierto = -1; String nombreAbierto;
        @Override public void abrirTechTree(String civ) { }
        @Override public void abrirCivStats() { }
        @Override public void abrirLadder() { }
        @Override public void abrirPerfil(long pid, String nombre) { pidAbierto = pid; nombreAbierto = nombre; }
        @Override public void abrirPerfilEnPestana(long pid, String nombre) { }
        @Override public void abrirAhora() { }
    }

    static final class AnfitrionFalso implements MiPartidaPanel.Anfitrion {
        List<String[]> resultados = List.of();
        String ultimoEstado;
        boolean socketSincronizado;
        boolean buscarPerfilesLlamado;
        @Override public List<String[]> buscarPerfiles(String nick) { buscarPerfilesLlamado = true; return resultados; }
        @Override public void mostrarEstado(String texto) { ultimoEstado = texto; }
        @Override public void sincronizarSocket() { socketSincronizado = true; }
    }

    static final class PantallaFalsa implements MiPartidaPresenter.Pantalla {
        List<Object[]> superposiciones = new ArrayList<>();   // {texto, fichas, ms}
        String[] aElegir;   // lo que devuelve elegirNick
        boolean elegirNickLlamado;
        boolean nickPedido;
        boolean vigilanciaIniciada;
        @Override public void mostrarSuperposicion(String texto, List<Object[]> fichas, int ms) { superposiciones.add(new Object[]{ texto, fichas, ms }); }
        @Override public String[] elegirNick(List<String[]> resultados) { elegirNickLlamado = true; return aElegir; }
        @Override public void pedirNick() { nickPedido = true; }
        @Override public void iniciarVigilancia() { vigilanciaIniciada = true; }
    }

    final ServicioFalso servicio = new ServicioFalso();
    final NavegacionFalsa navegacion = new NavegacionFalsa();
    final AnfitrionFalso anfitrion = new AnfitrionFalso();
    final PantallaFalsa pantalla = new PantallaFalsa();
    final MiPartidaPresenter presenter = new MiPartidaPresenter(servicio, Tareas.EN_LINEA, navegacion, anfitrion, pantalla);

    // ----- tick: log del juego -----

    @Test void tick_sinFaseDetectadaNoMuestraNada() {
        servicio.leerLogJuego = false;
        presenter.tick();
        assertTrue(pantalla.superposiciones.isEmpty());
    }

    @Test void tick_conFaseDetectadaMuestraElAvisoDePreparando() {
        servicio.leerLogJuego = true;
        servicio.lobby = new MiPartidaService.ResultadoLobby(false, "", List.of());   // no aparece en el lobby oficial
        presenter.tick();
        assertEquals(1, pantalla.superposiciones.size(), "solo el aviso de preparando, el lobby no avisa");
        Object[] p = pantalla.superposiciones.get(0);
        assertTrue(((String) p[0]).contains("preparando"));
        assertEquals(25_000, p[2]);
    }

    @Test void tick_conLobbyEncontradoMuestraTambienLasFichas() {
        servicio.leerLogJuego = true;
        List<Object[]> fichas = List.<Object[]>of(new Object[]{ 1L, "Rival", 1500 });
        servicio.lobby = new MiPartidaService.ResultadoLobby(true, "Partida encontrada · con Rival", fichas);
        presenter.tick();
        assertEquals(2, pantalla.superposiciones.size());
        Object[] segundo = pantalla.superposiciones.get(1);
        assertEquals("Partida encontrada · con Rival", segundo[0]);
        assertSame(fichas, segundo[1]);
        assertEquals(60_000, segundo[2]);
    }

    // ----- abrirMiPerfil -----

    @Test void abrirMiPerfil_conIdentidadConocidaAbreElPerfilSinPedirNick() {
        servicio.identidad = new MiPartidaService.Identidad(123, "12Tirador");
        presenter.abrirMiPerfil();
        assertEquals(123L, navegacion.pidAbierto);
        assertEquals("12Tirador", navegacion.nombreAbierto);
        assertFalse(pantalla.nickPedido);
    }

    @Test void abrirMiPerfil_sinIdentidadPideElNick() {
        servicio.identidad = null;
        presenter.abrirMiPerfil();
        assertTrue(pantalla.nickPedido);
        assertEquals(-1, navegacion.pidAbierto);
    }

    // ----- buscarMiNick -----

    @Test void buscarMiNick_conQueryVaciaNoBusca() {
        presenter.buscarMiNick("   ");
        assertFalse(anfitrion.buscarPerfilesLlamado, "en blanco: ni se busca");
        assertEquals(-1, servicio.fijadaPid);
        assertFalse(pantalla.elegirNickLlamado);
    }

    @Test void buscarMiNick_sinResultadosAvisaEnElEstado() {
        anfitrion.resultados = List.of();
        presenter.buscarMiNick("NickQueNoExiste");
        assertNotNull(anfitrion.ultimoEstado);
        assertFalse(pantalla.elegirNickLlamado, "sin resultados no hay nada que elegir");
        assertEquals(-1, servicio.fijadaPid);
    }

    @Test void buscarMiNick_unSoloResultadoSeEligeSoloSinPreguntar() {
        anfitrion.resultados = List.<String[]>of(new String[]{ "77", "12Tirador", "12Tirador [es]" });
        presenter.buscarMiNick("12Tira");
        assertFalse(pantalla.elegirNickLlamado, "un único resultado no pregunta");
        assertEquals(77L, servicio.fijadaPid);
        assertEquals("12Tirador", servicio.fijadaNombre);
        assertEquals(77L, navegacion.pidAbierto);
        assertTrue(pantalla.vigilanciaIniciada);
        assertTrue(anfitrion.socketSincronizado);
    }

    @Test void buscarMiNick_variosResultadosPreguntaCualEsYSigueConElElegido() {
        String[] uno = { "1", "Ana", "Ana [es]" };
        String[] dos = { "2", "Ana2", "Ana2 [fr]" };
        anfitrion.resultados = List.of(uno, dos);
        pantalla.aElegir = dos;
        presenter.buscarMiNick("Ana");
        assertTrue(pantalla.elegirNickLlamado);
        assertEquals(2L, servicio.fijadaPid);
        assertEquals("Ana2", servicio.fijadaNombre);
        assertEquals(2L, navegacion.pidAbierto);
    }

    @Test void buscarMiNick_variosResultadosCanceladoNoFijaNada() {
        String[] uno = { "1", "Ana", "Ana [es]" };
        String[] dos = { "2", "Ana2", "Ana2 [fr]" };
        anfitrion.resultados = List.of(uno, dos);
        pantalla.aElegir = null;   // cancela el diálogo
        presenter.buscarMiNick("Ana");
        assertTrue(pantalla.elegirNickLlamado);
        assertEquals(-1, servicio.fijadaPid);
        assertEquals(-1, navegacion.pidAbierto);
        assertFalse(pantalla.vigilanciaIniciada);
        assertFalse(anfitrion.socketSincronizado);
    }
}
