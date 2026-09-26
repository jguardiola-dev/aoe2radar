package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Los fantasmas de Live now: la API tiene la última palabra. Sin red (transporte falso) y con la hora fija. */
class LiveServiceTest {
    static final long HORA = 1_700_000_000_000L;   // nov. 2023, en ms

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

    final TransporteFalso red = new TransporteFalso();
    final RelojFalso reloj = new RelojFalso();
    { reloj.ahora = HORA; }
    final LiveService live = new LiveService(new CompanionApi(new ApiClient(new ThrottleSinFreno(), red, s -> { }, () -> false)), reloj);

    /** Una partida del JSON de /matches, empezada hace `hace` (y terminada si terminada). */
    static String partida(long id, Duration hace, boolean terminada) {
        long ini = HORA - hace.toMillis();
        return "{\"match_id\":" + id + ",\"started\":" + ini + (terminada ? ",\"finished\":" + (ini + 60_000) : "") + "}";
    }
    void responder(String... partidas) { red.cuerpo = "{\"matches\":[" + String.join(",", partidas) + "]}"; }

    @Test void siApareceEnCursoEsViva() {
        responder(partida(1, Duration.ofMinutes(20), true), partida(555, Duration.ofMinutes(10), false));
        LiveService.Comprobacion c = live.comprobar(7, 555, 5);
        assertEquals(LiveService.Veredicto.VIVA, c.veredicto());
        assertEquals(555, c.partida().id, "con la partida tal como la da la API");
        assertNull(c.error());
    }

    @Test void siApareceTerminadaEsFantasma() {
        responder(partida(555, Duration.ofMinutes(10), true));
        LiveService.Comprobacion c = live.comprobar(7, 555, 5);
        assertEquals(LiveService.Veredicto.TERMINADA, c.veredicto());
        assertEquals(555, c.partida().id, "espectar lo da por hecho: con la partida, anota «verificación de…» y NO abre el juego");
        assertNull(c.error());
    }

    @Test void siEmpezoHaceMasDeTresHorasSinTerminarEsFantasma() {
        responder(partida(555, Duration.ofHours(3).plusMinutes(1), false));
        assertEquals(LiveService.Veredicto.TERMINADA, live.comprobar(7, 555, 5).veredicto(), "una «en curso» de hace horas crasheó");
    }

    @Test void decideConSuRelojNoConLaHoraReal() {
        responder(partida(555, Duration.ofHours(1), false));   // hace 1 h para SU reloj (2023): con la hora real serían años
        assertEquals(LiveService.Veredicto.VIVA, live.comprobar(7, 555, 5).veredicto());
    }

    @Test void siNoApareceSinDatosYSinError() {
        responder(partida(1, Duration.ofMinutes(10), false));
        LiveService.Comprobacion c = live.comprobar(7, 555, 5);
        assertEquals(LiveService.Veredicto.SIN_DATOS, c.veredicto());
        assertNull(c.partida());
        assertNull(c.error(), "no es un fallo: la partida no está entre las últimas");
    }

    @Test void siLaApiFallaSinDatosConElError() {
        red.estado = 500;
        LiveService.Comprobacion c = live.comprobar(7, 555, 5);
        assertEquals(LiveService.Veredicto.SIN_DATOS, c.veredicto());
        assertNotNull(c.error(), "el llamador lo anota en el log");
    }

    @Test void pideLasUltimasDelJugadorEnUnaLlamada() {
        live.comprobar(7, 555, 50);
        assertEquals(1, red.pedidas.size());
        String url = red.pedidas.get(0);
        assertTrue(url.contains("profile_ids=7") && url.contains("page=1") && url.contains("per_page=50"), url);
    }
}
