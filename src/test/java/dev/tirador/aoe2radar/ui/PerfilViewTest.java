package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.util.Reloj;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.tirador.aoe2radar.util.Config.CONFIG_FILE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PerfilView con componentes Swing reales dentro de invokeAndWait, nunca visibles (como RatingsViewTest y
 * CivStatsViewTest): no hace falta pantalla. Sin red: los dobles de servicios de RatingsViewTest y
 * CivStatsViewTest, y Tareas.EN_LINEA. menus y techTree van a null: construirPanelPerfil no los toca y los
 * escenarios de aquí no pintan listas de civs.
 */
class PerfilViewTest {

    /** El anfitrión de mentira: solo apunta los mensajes que la vista manda a la barra general. */
    static class AnfitrionFalso implements PerfilView.Anfitrion {
        final List<String> estadosGlobales = new ArrayList<>();
        @Override public List<Player> seleccionWatchlist() { return List.of(); }
        @Override public boolean estaEnWatchlist(long pid) { return false; }
        @Override public String paisDe(long pid) { return ""; }
        @Override public String nombreVisible(long pid, String nombre) { return nombre; }
        @Override public void ficharDesdeTop(long pid, String nombre, String grupo) { }
        @Override public List<String> gruposDeJugadores() { return List.of(); }
        @Override public List<String> gruposGuardados() { return List.of(); }
        @Override public String grupoGeneral() { return "General"; }
        @Override public boolean ultimoClicFueCtrl() { return false; }
        @Override public void pedirAlias(long pid, String nombreOriginal) { }
        @Override public void pedirNota(long pid, String nombre) { }
        @Override public void borrarNota(long pid, String nombre) { }
        @Override public void mostrarVinculadas(long pid, String nombre) { }
        @Override public void nicksAnteriores(long pid, String nombre) { }
        @Override public void abrirUrl(String url) { }
        @Override public void registrarDestino(long pid, String nombre) { }
        @Override public void actualizarTextoBuscar() { }
        @Override public JToggleButton crearBotonPestana(String texto, Icon icono) { return new JToggleButton(texto, icono); }
        @Override public void traerAlFrente() { }
        @Override public void mostrarEstadoGlobal(String texto) { estadosGlobales.add(texto); }
        @Override public void cerrarPerfil() { }
        @Override public boolean enCursoReal(Match m) { return false; }
        @Override public boolean confirmarEspectar(String nombre) { return false; }
        @Override public void espectarPartida(long matchId) { }
        @Override public void cargarPartidasEnTabla(List<Match> partidas, Player sujeto) { }
        @Override public void buscarPartidasDe(long pid, String nombre) { }
        @Override public void descargarSinCambiarVista(List<Match> partidas, boolean enviar, Runnable alTerminar) { }
    }

    /** ProfileService de mentira: el de PerfilPresenterTest (ficha y ficha conocida configurables). */
    final PerfilPresenterTest.PerfilesFalso perfiles = new PerfilPresenterTest.PerfilesFalso();
    final AnfitrionFalso anfitrion = new AnfitrionFalso();
    final Map<Long, Actividad> actividadCache = new ConcurrentHashMap<>();

    @BeforeEach @AfterEach
    void limpiarConfig() throws Exception { Files.deleteIfExists(CONFIG_FILE); }

    /** Construye la vista en el EDT (hay que llamarlo dentro de invokeAndWait). */
    PerfilView vista() {
        return new PerfilView(perfiles, new RatingsViewTest.RatingsServiceFalso(), new RatingsViewTest.BusquedaFalsa(), new CivStatsViewTest.StatsFalso(),
                new EstadoVivo(Reloj.SISTEMA), null, new CivStatsViewTest.NavegacionFalsa(), null, new Listas(null, b -> { }),
                Tareas.EN_LINEA, anfitrion, actividadCache, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), pid -> null, Path.of("perfiles-test-inexistente"), pid -> null, 365);
    }

    static FichaPerfil ficha(String pais, int elo) { return new FichaPerfil(Map.of("rm_1v1", new int[]{ elo, 10, elo, 5, 5 }), pais, "", 10); }

    /** F1 (1.3): la barra de estado de la ventana está oculta con Perfil abierto; el fallo de «Actualizar hoy» tiene
     *  que verse en la etiqueta propia del perfil (antes el botón volvía a «Actualizar hoy» sin decir nada). */
    @Test void errorDeActualizarHoySeVeEnElEstadoDelPerfil() throws Exception {
        String[] estado = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.actPid = 5L;
            v.hoyError("HTTP 429");
            estado[0] = v.actEstado.getText();
        });
        assertEquals("No se pudo actualizar: HTTP 429", estado[0]);
        assertEquals(List.of("No se pudo actualizar: HTTP 429"), anfitrion.estadosGlobales, "y también en la barra general, como antes");
    }

    /** F1: «Cara a cara…» sin historial cargado avisaba solo en la barra oculta: no pasaba nada a la vista. */
    @Test void caraACaraSinHistorialAvisaEnElEstadoDelPerfil() throws Exception {
        String[] estado = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.actPid = 5L;   // sin actividad en caché
            new CaraACaraDialogo(null, v).mostrar();
            estado[0] = v.actEstado.getText();
        });
        assertEquals("Abre primero un perfil con historial cargado.", estado[0]);
    }

    /** F9 (1.3): «Actualizar hoy» termina sin ficha (la API de la ficha falló y no había ninguna conocida): la
     *  cabecera que ya estaba pintada no se sustituye por «Sin datos de perfil». */
    @Test void actualizarHoySinFichaNoBorraLaCabecera() throws Exception {
        String[] sub = new String[2];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.actPid = 5L; v.actNombre = "Fulano";
            v.cabecera(ficha("es", 1500));
            sub[0] = v.actSubtitulo.getText();
            v.hoyTerminado(null, 0);
            sub[1] = v.actSubtitulo.getText();
        });
        assertEquals("ES  ·  10 partidas en total", sub[0]);
        assertEquals(sub[0], sub[1], "la cabecera sigue siendo la de antes");
    }
}
