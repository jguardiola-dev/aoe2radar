package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Espectar sin Swing (fase 3, tanda 4, Z4): buscar la ruta de CaptureAge, lanzar el proceso y verificar la
 * partida por red, con dobles (ruta ausente, un lanzador de proceso falso que no toca el SO, y un transporte
 * falso para LiveService). Antes vivía en SpoilerFreeRecs (lanzarCaptureAge/espectarVerificando).
 */
class EspectarTest {
    static final long HORA = 1_700_000_000_000L;

    static final class TransporteFalso implements Transporte {
        final List<String> pedidas = new ArrayList<>();
        String cuerpo = "{\"matches\":[]}";
        int estado = 200;
        @Override public Respuesta get(String url) { pedidas.add(url); return new Respuesta(estado, cuerpo); }
    }
    static final class ThrottleSinFreno implements Throttle {
        @Override public void adquirir(BooleanSupplier cancelar) { }
        @Override public long registrar429() { return 0; }
    }
    /** Guarda el comando pedido en vez de lanzar un proceso de verdad. */
    static final class LanzadorFalso implements Espectar.LanzadorProceso {
        List<String> comando;
        RuntimeException fallar;
        @Override public void lanzar(String... comando) throws Exception {
            if (fallar != null) throw fallar;
            this.comando = List.of(comando);
        }
    }

    static String partidaJson(long id, Duration hace, boolean terminada) {
        long ini = HORA - hace.toMillis();
        return "{\"match_id\":" + id + ",\"started\":" + ini + (terminada ? ",\"finished\":" + (ini + 60_000) : "") + "}";
    }

    final TransporteFalso red = new TransporteFalso();
    final RelojFalso reloj = new RelojFalso();
    { reloj.ahora = HORA; }
    final LiveService live = new LiveService(new CompanionApi(new ApiClient(new ThrottleSinFreno(), red, s -> { }, () -> false)), reloj);
    final LanzadorFalso lanzador = new LanzadorFalso();

    Espectar espectarCon(Supplier<Path> ruta) { return new Espectar(live, ruta, lanzador); }

    // ===== lanzarCaptureAge: ruta ausente / proceso =====

    @Test void sinRutaDeCaptureAgeElResultadoEsRutaAusenteYNoLanzaNada() {
        Espectar e = espectarCon(() -> null);
        Espectar.ResultadoCaptureAge r = e.lanzarCaptureAge();
        assertEquals(Espectar.ResultadoCaptureAge.Estado.RUTA_AUSENTE, r.estado());
        assertNull(r.error());
        assertNull(lanzador.comando, "sin ruta, el lanzador no se llama");
    }

    @Test void conRutaLanzaExplorerConLaRutaDeCaptureAge() {
        Path ca = Path.of("C:", "Programas", "CaptureAge", "CaptureAge.exe");
        Espectar e = espectarCon(() -> ca);
        Espectar.ResultadoCaptureAge r = e.lanzarCaptureAge();
        assertEquals(Espectar.ResultadoCaptureAge.Estado.LANZADO, r.estado());
        assertEquals(List.of("explorer.exe", ca.toString()), lanzador.comando, "vía explorer, sin heredar nuestro entorno");
    }

    @Test void siElProcesoNoArrancaElResultadoEsFalloConElError() {
        lanzador.fallar = new RuntimeException("boom");
        Espectar e = espectarCon(() -> Path.of("CaptureAge.exe"));
        Espectar.ResultadoCaptureAge r = e.lanzarCaptureAge();
        assertEquals(Espectar.ResultadoCaptureAge.Estado.FALLO, r.estado());
        assertSame(lanzador.fallar, r.error());
    }

    // ===== viva(): verificación por red =====

    @Test void vivaEsVerdaderaSiLaApiDiceQueSigueEnCurso() {
        red.cuerpo = "{\"matches\":[" + partidaJson(555, Duration.ofMinutes(10), false) + "]}";
        Espectar e = espectarCon(() -> null);
        assertEquals(Boolean.TRUE, e.viva(7, 555, 5));
    }

    @Test void vivaEsFalsaSiLaApiDiceQueYaTermino() {
        red.cuerpo = "{\"matches\":[" + partidaJson(555, Duration.ofMinutes(10), true) + "]}";
        Espectar e = espectarCon(() -> null);
        assertEquals(Boolean.FALSE, e.viva(7, 555, 5));
    }

    @Test void vivaEsNuloSiLaPartidaNoApareceEntreLasUltimas() {
        red.cuerpo = "{\"matches\":[" + partidaJson(1, Duration.ofMinutes(10), false) + "]}";
        Espectar e = espectarCon(() -> null);
        assertNull(e.viva(7, 555, 5), "sin datos claros: la 1.1 lanza el juego igualmente");
    }

    @Test void vivaEsNuloSiLaApiFalla() {
        red.estado = 500;
        Espectar e = espectarCon(() -> null);
        assertNull(e.viva(7, 555, 5));
    }
}
