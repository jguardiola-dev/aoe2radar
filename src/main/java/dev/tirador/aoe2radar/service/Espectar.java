package dev.tirador.aoe2radar.service;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Supplier;

import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Espectar una partida en curso: la parte sin Swing de SpoilerFreeRecs.espectarPartida/espectarVerificando/
 * lanzarCaptureAge (fase 3, tanda 4, Z4; esos tres métodos ya no existen ahí, ver AccionesVentana). El protocolo
 * aoe2de://, buscar la ruta de CaptureAge y lanzar el
 * proceso, y la verificación por red (service.LiveService) viven aquí; los textos de estado, el toast y el
 * diálogo de confirmación (ui.ConfirmacionEspectar) se quedan en la ventana, que solo traduce el resultado a
 * un texto.
 */
public final class Espectar {

    /** El resultado de intentar lanzar CaptureAge de acompañante. */
    public record ResultadoCaptureAge(Estado estado, Exception error) {
        public enum Estado { RUTA_AUSENTE, LANZADO, FALLO }
    }

    /** Arranca un proceso del sistema operativo: en la app, ProcessBuilder de verdad; en los tests, un doble
     *  que no toca el SO (no hace falta lanzar explorer.exe de verdad para probar la decisión). */
    public interface LanzadorProceso { void lanzar(String... comando) throws Exception; }

    private final LiveService live;
    private final Supplier<Path> rutaCaptureAge;
    private final LanzadorProceso lanzador;

    public Espectar(LiveService live) {
        this(live, Juego::rutaCaptureAge, comando -> new ProcessBuilder(comando).start());
    }

    /** Visible para tests: una ruta y un lanzador de proceso falsos, sin tocar disco ni el sistema operativo. */
    Espectar(LiveService live, Supplier<Path> rutaCaptureAge, LanzadorProceso lanzador) {
        this.live = live;
        this.rutaCaptureAge = rutaCaptureAge;
        this.lanzador = lanzador;
    }

    /** Abre el juego espectando matchId (protocolo aoe2de://1/matchId, el mismo que aoe2companion/aoe2recs). */
    public void espectarPartida(long matchId) throws Exception {
        Desktop.getDesktop().browse(URI.create("aoe2de://1/" + matchId));
    }

    /** Busca la ruta de CaptureAge (service.Juego.rutaCaptureAge) y, si está, lo arranca de acompañante (vía
     *  explorer.exe, sin heredar nuestro entorno). El llamador decide el texto según el estado. */
    public ResultadoCaptureAge lanzarCaptureAge() {
        Path ca = rutaCaptureAge.get();
        if (ca == null) return new ResultadoCaptureAge(ResultadoCaptureAge.Estado.RUTA_AUSENTE, null);
        try {
            lanzador.lanzar("explorer.exe", ca.toString());
            return new ResultadoCaptureAge(ResultadoCaptureAge.Estado.LANZADO, null);
        } catch (Exception ex) {
            return new ResultadoCaptureAge(ResultadoCaptureAge.Estado.FALLO, ex);
        }
    }

    /** ¿Sigue en curso la partida matchId? true/false si la API tiene datos claros; null si no los tiene (la
     *  1.1 lanza el juego igualmente en ese caso: el beneficio de la duda). */
    public Boolean viva(long profileId, long matchId, int ultimas) {
        LiveService.Comprobacion c = live.comprobar(profileId, matchId, ultimas);
        if (c.partida() != null) {
            var m = c.partida();
            log("espectar: verificación de " + matchId + " → started=" + m.started + " finished=" + m.finished);
            return c.veredicto() == LiveService.Veredicto.VIVA;
        }
        if (c.error() != null) log("espectar: verificación no concluyente: " + causa(c.error()));
        else log("espectar: la partida " + matchId + " no aparece en las últimas del perfil " + profileId + "; se lanza igualmente");
        return null;
    }
}
