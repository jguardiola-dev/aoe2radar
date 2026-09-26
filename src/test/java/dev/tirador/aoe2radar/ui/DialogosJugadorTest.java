package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.service.AnotacionesService;
import dev.tirador.aoe2radar.service.ProfileService;
import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DialogosJugador: solo la lógica que NO abre un diálogo modal (notaDe, borrarNota), porque JOptionPane bloquea el
 * hilo y no se puede probar sin pantalla (pedirNota, pedirAlias, mostrarVinculadas y nicksAnteriores sí lo abren:
 * ver DEUDA). Comprueba que notaDe lee el mismo estado que AnotacionesService (cache.Anotaciones en la app real) y
 * que borrarNota avisa a la ventana (Anfitrion) en el mismo orden que hacía SpoilerFreeRecs.
 */
class DialogosJugadorTest {

    /** Lo mínimo de ProfileService: DialogosJugador lo usa en mostrarVinculadas, no en notaDe/borrarNota. */
    static class ProfileServiceFalso implements ProfileService {
        @Override public FichaPerfil ficha(long pid) { return null; }
        @Override public FichaPerfil fichaConocida(long pid) { return null; }
        @Override public Integer elo1v1(long pid) { return null; }
        @Override public List<Perfil.Vinculada> vinculadas(long pid) { return List.of(); }
        @Override public List<Perfil.Vinculada> vinculadasConElo(long pid) { return List.of(); }
        @Override public List<Perfil.Vinculada> vinculadasConocidas(long pid) { return null; }
        @Override public Integer eloVinculada(long vid) { return null; }
        @Override public Map<Long, String> familia(long pid) { return null; }
        @Override public AnioSfr anioSfr(long pid, String nombreSiFalta) { return null; }
        @Override public Actividad actividad(long pid) { return null; }
        @Override public Actividad historial(long pid, String nombre, Actividad base, boolean mas, int maxPaginas, Consumer<Actividad> parcial, BooleanSupplier cancelar) { return null; }
        @Override public int traerHoy(long pid) { return 0; }
    }

    static class RedSteamFalsa implements DialogosJugador.RedSteam {
        @Override public String steamId(long pid) { return ""; }
        @Override public List<String[]> alias(String steamId) { return List.of(); }
    }

    /** Anfitrion de mentira: apunta, en orden, qué le pidió DialogosJugador a la ventana. */
    static class AnfitrionFalso implements DialogosJugador.Anfitrion {
        final List<String> llamadas = new ArrayList<>();
        final List<String> estados = new ArrayList<>();
        final Set<Long> watchlist = new HashSet<>();

        @Override public void repintarLista() { llamadas.add("repintarLista"); }
        @Override public void refrescarAlturas() { llamadas.add("refrescarAlturas"); }
        @Override public void refrescarTabla() { llamadas.add("refrescarTabla"); }
        @Override public void ajustarColumnasTabla() { llamadas.add("ajustarColumnasTabla"); }
        @Override public void actualizarControles() { llamadas.add("actualizarControles"); }
        @Override public void refrescarSujetos() { llamadas.add("refrescarSujetos"); }
        @Override public void mostrarEstado(String texto) { llamadas.add("mostrarEstado"); estados.add(texto); }
        @Override public boolean enWatchlist(long pid) { return watchlist.contains(pid); }
        @Override public void ponerEloWatch(long pid, int elo) { }
        @Override public Set<String> gruposDisponibles() { return Set.of("General"); }
        @Override public String grupoActivo() { return null; }
        @Override public void agregarJugador(long pid, String nombre, String grupo) { }
        @Override public void guardarJugadores() { }
        @Override public void reconstruirGrupos() { }
        @Override public void marcarFamiliaVinculada(Set<Long> familia) { }
        @Override public void aplicarFiltro() { }
        @Override public void refrescarWatchlist() { }
        @Override public void pausaCortesia() { }
    }

    private DialogosJugador construir(AnotacionesService anotaciones, AnfitrionFalso anfitrion) {
        return new DialogosJugador((Component) null, anotaciones, new ProfileServiceFalso(), new RedSteamFalsa(), anfitrion);
    }

    @Test
    void notaDe_leeElMismoEstadoQueAnotacionesService() {
        Map<Long, String> alias = new HashMap<>();
        Map<Long, String> notas = new HashMap<>();
        notas.put(5L, "ojo con el fast castle");
        AnotacionesService anotaciones = new AnotacionesService(alias, notas, (clave, valor) -> { });
        DialogosJugador dialogos = construir(anotaciones, new AnfitrionFalso());

        assertEquals("ojo con el fast castle", dialogos.notaDe(5L));
        assertNull(dialogos.notaDe(6L), "sin nota guardada, notaDe debe devolver null");
    }

    @Test
    void notaDe_veLaNotaQueAcabaDePonerAnotacionesService() {
        Map<Long, String> alias = new HashMap<>();
        Map<Long, String> notas = new HashMap<>();
        AnotacionesService anotaciones = new AnotacionesService(alias, notas, (clave, valor) -> { });
        DialogosJugador dialogos = construir(anotaciones, new AnfitrionFalso());

        anotaciones.ponerNota(7L, "revancha pendiente");
        assertEquals("revancha pendiente", dialogos.notaDe(7L));
    }

    @Test
    void borrarNota_vaciaLaNotaYAvisaALaVentanaEnOrden() {
        Map<Long, String> alias = new HashMap<>();
        Map<Long, String> notas = new HashMap<>();
        notas.put(8L, "tramposo");
        AnotacionesService anotaciones = new AnotacionesService(alias, notas, (clave, valor) -> { });
        AnfitrionFalso anfitrion = new AnfitrionFalso();
        DialogosJugador dialogos = construir(anotaciones, anfitrion);

        dialogos.borrarNota(8L, "Fulano");

        assertNull(anotaciones.notaDe(8L));
        assertEquals(
                List.of("repintarLista", "refrescarAlturas", "refrescarTabla", "ajustarColumnasTabla", "actualizarControles", "mostrarEstado"),
                anfitrion.llamadas);
        assertEquals(List.of("Nota quitada a Fulano."), anfitrion.estados);
    }
}
