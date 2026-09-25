package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.sfrdata.CivStats;
import dev.tirador.aoe2radar.sfrdata.DescargaSfr;
import dev.tirador.aoe2radar.sfrdata.SfrDataClient;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StatsService sobre una release falsa de sfr-data: carpeta temporal, reloj falso, sin red. La ventana y las
 * tendencias quedan en CivStats (estático, compartido con la UI): se limpia antes y después de cada test para
 * no contaminar otros tests de la suite (ver la clase CivStats).
 */
class StatsServiceTest {

    static final String BASE = "https://release/";

    /** La release: por nombre, bytes; si no está, 404. Apunta cada descarga (rama «data»: condicional). */
    static final class Release implements DescargaSfr {
        final Map<String, byte[]> archivos = new HashMap<>();
        final List<String> pedidos = new ArrayList<>();
        @Override public Respuesta condicional(String url, String etag, int timeoutS) {
            String nombre = url.substring(SfrDataClient.SFR_DATA.length());
            pedidos.add(nombre);
            byte[] b = archivos.get(nombre);
            return b == null ? new Respuesta(404, new byte[0], null) : new Respuesta(200, b, "e-" + nombre);
        }
        @Override public byte[] bytes(String url, int timeoutS) { throw new AssertionError("no es de la rama diario"); }
    }

    static byte[] gz(String json) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPOutputStream g = new GZIPOutputStream(bo)) { g.write(json.getBytes(StandardCharsets.UTF_8)); }
            return bo.toByteArray();
        } catch (IOException e) { throw new AssertionError(e); }
    }

    static final String VENTANA_30 = "{\"ventana\":\"30\",\"desde\":\"2026-08-26\",\"hasta\":\"2026-09-25\",\"dias\":30,\"parche\":\"\","
            + "\"tramos\":[\"0-1000\",\"1000+\"],"
            + "\"modos\":{\"rm_1v1\":{\"partidas\":10,\"abandonos\":0,\"espejos\":0,\"sin_resultado\":0}},"
            + "\"nombres_mapas\":{\"arabia\":\"Arabia\"},"
            + "\"mapas\":[[\"rm_1v1\",\"arabia\",20]],"
            + "\"civs\":[[\"rm_1v1\",\"arabia\",\"0-1000\",\"aztecs\",10,6,6000],[\"rm_1v1\",\"arabia\",\"0-1000\",\"britons\",10,4,6000]],"
            + "\"matchups\":[[\"rm_1v1\",\"0-1000\",\"aztecs\",\"britons\",10,6]]}";

    static final String TENDENCIAS = "{\"meses\":[\"2026-08\",\"2026-09\"],"
            + "\"partidas\":[[\"rm_1v1\",\"2026-08\",100],[\"rm_1v1\",\"2026-09\",120]],"
            + "\"filas\":[[\"rm_1v1\",\"aztecs\",\"2026-08\",50,30]],"
            + "\"filas_mapa\":[],\"filas_tramo\":[],\"filas_mt\":[]}";

    @TempDir Path dir;
    final RelojFalso reloj = new RelojFalso();
    final Release red = new Release();
    SfrDataClient sfr;
    StatsService stats;

    @BeforeEach void preparar() {
        reloj.ahora = System.currentTimeMillis();
        sfr = new SfrDataClient(dir, red, new SfrDataClient.Etags() {
            @Override public String leer(String n) { return ""; }
            @Override public void guardar(String n, String e) { }
        }, new CacheService(reloj), () -> BASE);
        stats = new StatsServiceSfr(sfr);
        limpiarCivStats();
        red.archivos.put("civstats/ventanas/v30.json.gz", gz(VENTANA_30));
        red.archivos.put("civstats/tendencias.json.gz", gz(TENDENCIAS));
    }

    @AfterEach void limpiar() { limpiarCivStats(); }

    /** CivStats.VENTANAS_STATS y tendenciasStats son estáticos, compartidos con la UI: sin esto un test contamina el siguiente. */
    static void limpiarCivStats() { CivStats.VENTANAS_STATS.clear(); CivStats.tendenciasStats = null; }

    @Test void aseguraCargaLaVentanaYSoloLaPideUnaVez() {
        assertNull(stats.asegurar("30", false));
        assertEquals(List.of("civstats/ventanas/v30.json.gz"), red.pedidos);
        VentanaStats v = stats.ventana("30");
        assertNotNull(v);
        assertEquals("2026-09-25", v.hasta());
        assertEquals(2, v.civs().size());

        assertNull(stats.asegurar("30", false));
        assertEquals(1, red.pedidos.size(), "ya está en memoria para toda la sesión: no se vuelve a pedir");
    }

    @Test void aseguraConTendenciasLasCargaYQuedanEnCivStats() {
        assertNull(stats.asegurar("30", true));
        Tendencias tn = stats.tendencias();
        assertNotNull(tn);
        assertEquals(List.of("2026-08", "2026-09"), tn.meses());
        assertEquals(List.of("civstats/ventanas/v30.json.gz", "civstats/tendencias.json.gz"), red.pedidos);
    }

    @Test void sinAsegurarLaVentanaYLasTendenciasSonNull() {
        assertNull(stats.ventana("30"));
        assertNull(stats.tendencias());
    }

    @Test void unaVentanaQueNoExisteDevuelveElMotivoYNoQuedaEnCache() {
        String err = stats.asegurar("90", false);
        assertNotNull(err, "404: sin release, el motivo no es null");
        assertNull(stats.ventana("90"), "no se guarda nada a medias");
    }

    @Test void unFalloDeRedNoRompeUnaVentanaYaCargada() {
        assertNull(stats.asegurar("30", false));
        red.archivos.remove("civstats/ventanas/v30.json.gz");
        assertNull(stats.asegurar("30", false), "ya está en memoria: ni se mira si sigue en la release");
        assertNotNull(stats.ventana("30"));
    }

    @Test void losCalculosPasanPorElServicioYCoincidenConCalculoStats() {
        assertNull(stats.asegurar("30", false));
        VentanaStats v = stats.ventana("30");
        assertEquals(CalculoStats.agregarCivs(v, "rm_1v1", "arabia", "*"), stats.agregarCivs(v, "rm_1v1", "arabia", "*"));
        assertEquals(CalculoStats.duracionMedia(6000, 10), stats.duracionMedia(6000, 10));
        assertArrayEquals(CalculoStats.wilson(6, 10), stats.wilson(6, 10));
        assertTrue(stats.tramoEnRango("0-1000", v.tramos(), "*"));
    }

    @Test void tramoEnRangoConUnRangoRealDaFalsoOVerdaderoIgualQueCalculoStats() {
        assertNull(stats.asegurar("30", false));
        VentanaStats v = stats.ventana("30");
        // "1000+" como rango: solo el tramo "1000+" entra, "0-1000" se queda fuera.
        assertFalse(stats.tramoEnRango("0-1000", v.tramos(), "1000+"));
        assertEquals(CalculoStats.tramoEnRango("0-1000", v.tramos(), "1000+"), stats.tramoEnRango("0-1000", v.tramos(), "1000+"));
        assertTrue(stats.tramoEnRango("1000+", v.tramos(), "1000+"));
        assertEquals(CalculoStats.tramoEnRango("1000+", v.tramos(), "1000+"), stats.tramoEnRango("1000+", v.tramos(), "1000+"));
    }

    @Test void partidasPorMapaConModoYTramoDistintosCoincideConCalculoStats() {
        assertNull(stats.asegurar("30", false));
        VentanaStats v = stats.ventana("30");
        // modo y tramo con valores distintos entre sí: si el delegado los intercambiara, esto lo notaría.
        Map<String, Integer> esperado = CalculoStats.partidasPorMapa(v, "rm_1v1", "0-1000");
        assertEquals(esperado, stats.partidasPorMapa(v, "rm_1v1", "0-1000"));
        assertEquals(Integer.valueOf(10), esperado.get("arabia"), "20 filas de civ / 2 jugadores por partida en 1v1");
    }

    @Test void civPorMapaConModoTramoYCivDistintosCoincideConCalculoStats() {
        assertNull(stats.asegurar("30", false));
        VentanaStats v = stats.ventana("30");
        // modo, tramo y civ, los tres distintos entre sí: si el delegado desordenara los argumentos, esto lo notaría.
        Map<String, CivAgg> esperado = CalculoStats.civPorMapa(v, "rm_1v1", "0-1000", "aztecs");
        assertEquals(esperado, stats.civPorMapa(v, "rm_1v1", "0-1000", "aztecs"));
        assertEquals(10, esperado.get("arabia").n());
    }

    @Test void tieneVentanaDiceSiYaEstaCargadaSinPedirNadaALaRed() {
        assertFalse(stats.tieneVentana("30"), "aún no se ha pedido");
        assertNull(stats.asegurar("30", false));
        assertTrue(stats.tieneVentana("30"));
        assertFalse(stats.tieneVentana("90"), "esta no se ha pedido nunca");
    }

    @Test void modosYClavesVentanasSonLasDeCivStatsEnElMismoOrden() {
        assertArrayEquals(CivStats.MODOS_STATS, stats.modos());
        assertArrayEquals(CivStats.VENTANAS_STATS_KEYS, stats.clavesVentanas());
        assertSame(CivStats.MODOS_STATS, stats.modos(), "mismo array, no una copia");
        assertSame(CivStats.VENTANAS_STATS_KEYS, stats.clavesVentanas(), "mismo array, no una copia");
    }
}
