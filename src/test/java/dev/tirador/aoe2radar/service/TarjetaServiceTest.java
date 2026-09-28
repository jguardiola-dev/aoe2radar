package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.util.I18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** La tarjeta flotante sin pantalla ni red: cuándo se enseña, de qué fuente sale y una sola carga a la vez por pid. */
class TarjetaServiceTest {

    final String idiomaPrevio = I18n.IDIOMA;
    @AfterEach void restaurar() { I18n.IDIOMA = idiomaPrevio; }

    /** Fuentes de prueba: nocturna fija (o null) y un /profiles que cuenta llamadas. */
    static final class Fuentes implements TarjetaService.Fuentes {
        TarjetaPerfil.Datos nocturna;
        Perfil perfil;
        IOException fallo;
        final AtomicInteger llamadasApi = new AtomicInteger(), llamadasNocturna = new AtomicInteger();
        @Override public TarjetaPerfil.Datos nocturna(long pid) { llamadasNocturna.incrementAndGet(); return nocturna; }
        @Override public Perfil perfil(long pid) throws Exception {
            llamadasApi.incrementAndGet();
            if (fallo != null) throw fallo;
            return perfil;
        }
    }

    static Perfil perfil(int nPuntos) {
        List<Perfil.Punto> puntos = new ArrayList<>();
        for (int i = 0; i < nPuntos; i++) puntos.add(new Perfil.Punto(Instant.ofEpochSecond(1_790_000_000L + i * 3600L), 1000 + i, 5));
        return new Perfil("es", "TCC", 300, "canal", null,
                List.of(new Perfil.Ladder("rm_team", 1700, 9, 1800, 1, 1), new Perfil.Ladder("rm_1v1", 1905, 812, 2010, 120, 80)),
                List.of(new Perfil.Serie("rm_team", List.of(new Perfil.Punto(Instant.ofEpochSecond(1_800_000_000L), 9999, 1))),
                        new Perfil.Serie("rm_1v1", puntos)),
                List.of());
    }

    // ----- de qué fuente sale -----

    @Test void conChispasNoHaceNiUnaLlamada() {
        Fuentes f = new Fuentes();
        f.nocturna = new TarjetaPerfil.Datos("ES", "", 200, 1905, null, null, null, new int[]{ 1, 2, 3, 4 }, false);
        TarjetaService.Resultado r = new TarjetaService(f).cargar(7, "Uno");
        assertEquals(0, f.llamadasApi.get());
        assertArrayEquals(new int[]{ 1, 2, 3, 4 }, r.spark());
        assertNull(r.perfil());
        assertTrue(r.guardar());
    }

    @Test void sinChispasUnaSolaLlamadaYLaGraficaDeSuSerie1v1() {
        I18n.IDIOMA = "es";
        Fuentes f = new Fuentes();
        f.perfil = perfil(40);
        TarjetaService.Resultado r = new TarjetaService(f).cargar(7, "Uno");
        assertEquals(1, f.llamadasApi.get(), "una llamada: /profiles, sin páginas de /matches");
        // los 25 puntos más recientes (1039…1015), sin los 10 últimos (1039…1030) → 1015…1029, de viejo a nuevo
        int[] esperado = new int[15];
        for (int i = 0; i < 15; i++) esperado[i] = 1015 + i;
        assertArrayEquals(esperado, r.spark(), "la serie de equipos no se mezcla");
        assertTrue(r.html().contains("ELO 1v1: <b>1905</b>"));
        assertTrue(r.html().contains("Winrate 1v1: 60% (200 partidas)"));
        assertSame(f.perfil, r.perfil(), "para aprender canal y país, como antes");
        assertTrue(r.guardar());
    }

    @Test void siLaApiFallaSaleSinDatosYNoSeGuarda() {
        Fuentes f = new Fuentes();
        f.fallo = new IOException("HTTP 500");
        TarjetaService.Resultado r = new TarjetaService(f).cargar(7, "Uno");
        assertEquals("<html><b>Uno</b><br><br></html>", r.html());
        assertNull(r.spark());
        assertFalse(r.guardar(), "una tarjeta vacía no se guarda 10 min");
    }

    @Test void unFalloDelNocturnoCaeALaApi() {
        TarjetaService.Fuentes f = new TarjetaService.Fuentes() {
            @Override public TarjetaPerfil.Datos nocturna(long pid) { throw new IllegalStateException("roto"); }
            @Override public Perfil perfil(long pid) { return TarjetaServiceTest.perfil(20); }
        };
        assertNotNull(new TarjetaService(f).cargar(7, "Uno").perfil());
    }

    @Test void pocos1v1EnLaSerieDelPerfil() {
        I18n.IDIOMA = "es";
        TarjetaPerfil.Datos d = TarjetaService.desdePerfil(perfil(12));
        assertNull(d.spark());
        assertTrue(d.pocos1v1());
        assertEquals("ES", d.pais());
        assertEquals("TCC", d.clan());
    }

    @Test void siElRatonYaSeFueNoSaleALaApi() {
        Fuentes f = new Fuentes();
        f.perfil = perfil(20);
        assertNull(new TarjetaService(f).cargar(7, "Uno", () -> false));
        assertEquals(0, f.llamadasApi.get(), "pasear el ratón no gasta fichas del freno");
        f.nocturna = new TarjetaPerfil.Datos("", "", 0, 1500, null, null, null, null, true);
        assertNotNull(new TarjetaService(f).cargar(7, "Uno", () -> false), "la nocturna no cuesta nada: se prepara igual");
    }

    // ----- una carga a la vez por pid -----

    @Test void reservarNoDejaDosCargasDelMismoPid() {
        TarjetaService s = new TarjetaService(new Fuentes());
        assertTrue(s.reservar(7));
        assertFalse(s.reservar(7), "la segunda pasada del ratón mientras la primera sigue en vuelo no carga otra vez");
        assertTrue(s.reservar(8), "otro jugador sí");
        s.liberar(7);
        assertTrue(s.reservar(7), "al acabar, se puede volver a pedir");
    }

    @Test void entreHilosSoloUnoGanaLaReserva() throws Exception {
        TarjetaService s = new TarjetaService(new Fuentes());
        int n = 8;
        CountDownLatch salida = new CountDownLatch(1), fin = new CountDownLatch(n);
        AtomicInteger ganadores = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(() -> {
                try { salida.await(); if (s.reservar(42)) ganadores.incrementAndGet(); } catch (InterruptedException ignored) { }
                finally { fin.countDown(); }
            });
            t.setDaemon(true);
            t.start();
        }
        salida.countDown();
        assertTrue(fin.await(5, TimeUnit.SECONDS));
        assertEquals(1, ganadores.get());
    }

    // ----- cuándo se enseña -----

    @Test void procedeSoloConLaVentanaActivaSinMenusYConElRatonEncima() {
        assertTrue(TarjetaService.procede(true, false, true, true));
        assertFalse(TarjetaService.procede(false, false, true, true), "otra ventana o un diálogo delante");
        assertFalse(TarjetaService.procede(true, true, true, true), "un menú abierto");
        assertFalse(TarjetaService.procede(true, false, false, true), "la lista no se ve");
        assertFalse(TarjetaService.procede(true, false, true, false), "el ratón ya no está encima");
    }

    @Test void alLlegarSoloSePintaSiElRatonSigueEnEsaFila() {
        assertTrue(TarjetaService.pintarAlLlegar(7, 7, false, true));
        assertFalse(TarjetaService.pintarAlLlegar(7, 8, false, true), "ya está en otra fila: su propia espera la pedirá");
        assertFalse(TarjetaService.pintarAlLlegar(7, 0, false, true), "se ocultó mientras tanto (clic, scroll, lista rehecha)");
        assertFalse(TarjetaService.pintarAlLlegar(7, 7, false, false), "la ventana perdió el foco");
        assertTrue(TarjetaService.pintarAlLlegar(7, 0, true, false), "la fijada se pinta siempre");
    }
}
