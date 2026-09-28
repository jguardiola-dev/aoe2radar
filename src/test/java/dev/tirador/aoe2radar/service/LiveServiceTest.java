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

    // ===== comprobarVarias: varias partidas en una sola llamada (confirmación en lote de las quitadas) =====

    @Test void variasPartidasEnUnaSolaLlamadaConLosPidsEnCsv() {
        responder(partida(555, Duration.ofMinutes(30), true), partida(556, Duration.ofMinutes(10), false), partida(1, Duration.ofMinutes(5), false));
        LiveService.Lote lote = live.comprobarVarias(List.of(7L, 8L, 9L), List.of(555L, 556L, 557L), 100);
        java.util.Map<Long, LiveService.Comprobacion> l = lote.veredictos();
        assertEquals(1, red.pedidas.size(), "una llamada para las tres");
        String url = red.pedidas.get(0);
        assertTrue(url.contains("profile_ids=7,8,9") && url.contains("page=1") && url.contains("per_page=100"), url);
        assertEquals(LiveService.Veredicto.TERMINADA, l.get(555L).veredicto());
        assertEquals(555, l.get(555L).partida().id);
        assertEquals(LiveService.Veredicto.VIVA, l.get(556L).veredicto());
        assertEquals(LiveService.Veredicto.SIN_DATOS, l.get(557L).veredicto());
        assertNull(l.get(557L).error(), "no vino: sin datos, no un fallo");
        assertEquals(java.time.Instant.ofEpochMilli(HORA - Duration.ofMinutes(30).toMillis()), lote.masAntigua(), "el started más antiguo de la página");
    }

    @Test void conUnSoloPidPideLoMismoQueComprobar() {
        live.comprobarVarias(List.of(7L), List.of(555L), 5);
        live.comprobar(7, 555, 5);
        assertEquals(red.pedidas.get(1), red.pedidas.get(0));
    }

    @Test void ausenteSeguraSoloSiLaPaginaLlegaAntesDeSuInicio() {
        java.time.Instant hace1h = java.time.Instant.ofEpochMilli(HORA - 3_600_000L);
        LiveService.Lote lote = new LiveService.Lote(java.util.Map.of(), hace1h);
        assertTrue(LiveService.ausenteSegura(lote, java.time.Instant.ofEpochMilli(HORA)), "empezó hace nada y la página llega a hace 1 h: habría salido");
        assertFalse(LiveService.ausenteSegura(lote, hace1h.plusSeconds(60)), "dentro del margen: no se fía");
        assertFalse(LiveService.ausenteSegura(lote, java.time.Instant.ofEpochMilli(HORA - 7_200_000L)), "empezó antes que la más antigua: pudo quedar tapada");
        assertFalse(LiveService.ausenteSegura(lote, null));
        assertFalse(LiveService.ausenteSegura(new LiveService.Lote(java.util.Map.of(), null), java.time.Instant.ofEpochMilli(HORA)));
    }

    @Test void unStartedRotoNoCuentaComoLaMasAntigua() {
        responder("{\"match_id\":1,\"started\":0}", partida(2, Duration.ofMinutes(20), false));
        assertEquals(java.time.Instant.ofEpochMilli(HORA - Duration.ofMinutes(20).toMillis()), live.comprobarVarias(List.of(7L, 8L), List.of(555L), 100).masAntigua(),
                "started 0 (1970) haría «segura» cualquier ausencia");
    }

    /** El supuesto de ausenteSegura, fijado con una respuesta REAL de /matches con 4 pids (28/09/2026, recortada a
     *  id, started, finished y jugadores): una sola lista de más nueva a más vieja, mezclando a todos los jugadores. */
    @Test void laApiDaLasDeVariosPidsMezcladasDeMasNuevaAMasVieja() throws Exception {
        try (java.io.InputStream in = getClass().getResourceAsStream("/api/matches_varios_pids.json")) {
            red.cuerpo = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        List<dev.tirador.aoe2radar.model.Match> ms = new ArrayList<>();
        for (dev.tirador.aoe2radar.model.Match m : live.partidas("199325,8793414,2776293,251265", 1, 100)) ms.add(m);
        assertEquals(100, ms.size());
        for (int i = 1; i < ms.size(); i++) assertFalse(ms.get(i).started.isAfter(ms.get(i - 1).started), "orden por started descendente en " + i);
        java.util.Set<Long> primeros = new java.util.HashSet<>();
        for (int i = 0; i < 12; i++) for (dev.tirador.aoe2radar.model.MatchPlayer p : ms.get(i).players) primeros.add(p.id);
        assertTrue(primeros.contains(8793414L) && primeros.contains(251265L), "mezcladas, no una lista por jugador");
        // y entonces: toda partida de la página que empezó después de la más antigua (con margen) aparece
        LiveService.Lote lote = live.comprobarVarias(List.of(199325L, 8793414L, 2776293L, 251265L), List.of(ms.get(50).id), 100);
        assertEquals(ms.get(99).started, lote.masAntigua());
        assertNotEquals(LiveService.Veredicto.SIN_DATOS, lote.veredictos().get(ms.get(50).id).veredicto());
    }

    @Test void variasConLaApiCaidaTodasSinDatosConElError() {
        red.estado = 500;
        LiveService.Lote lote = live.comprobarVarias(List.of(7L, 8L), List.of(555L, 556L), 100);
        java.util.Map<Long, LiveService.Comprobacion> l = lote.veredictos();
        assertNull(lote.masAntigua());
        assertEquals(2, l.size());
        for (LiveService.Comprobacion c : l.values()) {
            assertEquals(LiveService.Veredicto.SIN_DATOS, c.veredicto());
            assertNotNull(c.error());
        }
    }
}
