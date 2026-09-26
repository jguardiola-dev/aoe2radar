package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.model.Matchup;
import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.util.Json;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * statsAsegurar sin red: SfrDataClient con una descarga falsa, una de ellas bloqueada a propósito para reproducir
 * la carrera de dos llamadores que ven el mismo hueco vacío a la vez (fila 38 de DEUDA).
 */
class CivStatsTest {

    static byte[] gz(String json) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(bo)) { g.write(json.getBytes(StandardCharsets.UTF_8)); }
        return bo.toByteArray();
    }

    /** Responde el cuerpo fijado; si trae "entrar"/"salir", avisa al llegar y espera antes de devolver la respuesta. */
    static final class RedBloqueable implements DescargaSfr {
        final byte[] cuerpo;
        final CountDownLatch entrar, salir;
        RedBloqueable(byte[] cuerpo, CountDownLatch entrar, CountDownLatch salir) { this.cuerpo = cuerpo; this.entrar = entrar; this.salir = salir; }
        @Override public Respuesta condicional(String url, String etag, int timeoutS) throws InterruptedException {
            if (entrar != null) entrar.countDown();
            if (salir != null) salir.await();
            return new Respuesta(200, cuerpo, "e");
        }
        @Override public byte[] bytes(String url, int timeoutS) { throw new AssertionError("no es diario"); }
    }

    static SfrDataClient sfr(Path dir, DescargaSfr red) {
        return new SfrDataClient(dir, red, new SfrDataClient.Etags() {
            @Override public String leer(String n) { return ""; }
            @Override public void guardar(String n, String e) { }
        }, new CacheService(new RelojFalso()), () -> "https://release/");
    }

    static final String VENTANA = "f4test";

    @AfterEach void limpiar() { CivStats.VENTANAS_STATS.remove(VENTANA); }

    static final String MATCHUPS = "\"matchups\":[[\"rm_1v1\",\"0-1000\",\"aztecs\",\"britons\",10,6],[\"rm_1v1\",\"0-1000\",\"unknown\",\"britons\",3,1]]";
    static final String MATCHUPS_MAPA = "\"matchups_mapa\":[[\"rm_1v1\",\"arabia\",\"0-1000\",\"aztecs\",\"britons\",7,5],"
            + "[\"rm_1v1\",\"arena\",\"0-1000\",\"aztecs\",\"britons\",3,1],[\"rm_1v1\",\"arena\",\"0-1000\",\"aztecs\",\"unknown\",2,1]]";

    /** D2 (1.3): datos viejos (sin «matchups_mapa»): solo el agregado, con mapa "*". */
    @Test void sinMatchupsMapaSoloElAgregado() {
        VentanaStats v = CivStats.parsearVentana(Json.obj(Json.parse("{\"ventana\":\"30\"," + MATCHUPS + "}")));
        assertEquals(List.of(new Matchup("rm_1v1", "*", "0-1000", "aztecs", "britons", 10, 6)), v.matchups());
    }

    /** D2 (1.3): con «matchups_mapa» ([modo, mapa, tramo, civA, civB, n, winsA]) se añaden las filas por mapa;
     *  el agregado sigue con "*". Los textos repetidos se comparten (cientos de miles de filas en v365). */
    @Test void conMatchupsMapaLeeLasFilasPorMapa() {
        VentanaStats v = CivStats.parsearVentana(Json.obj(Json.parse("{\"ventana\":\"30\"," + MATCHUPS + "," + MATCHUPS_MAPA + "}")));
        assertEquals(List.of(new Matchup("rm_1v1", "*", "0-1000", "aztecs", "britons", 10, 6),
                new Matchup("rm_1v1", "arabia", "0-1000", "aztecs", "britons", 7, 5),
                new Matchup("rm_1v1", "arena", "0-1000", "aztecs", "britons", 3, 1)), v.matchups());
        assertSame(v.matchups().get(1).ca(), v.matchups().get(2).ca());
    }

    @Test void dosLlamadoresALaVezNoSePisan(@TempDir Path dirA, @TempDir Path dirB) throws Exception {
        CountDownLatch aEntroALaRed = new CountDownLatch(1), dejarSalirA = new CountDownLatch(1);
        SfrDataClient sfrA = sfr(dirA, new RedBloqueable(gz("{\"ventana\":\"A\"}"), aEntroALaRed, dejarSalirA));
        SfrDataClient sfrB = sfr(dirB, new RedBloqueable(gz("{\"ventana\":\"B\"}"), null, null));

        Thread hiloA = new Thread(() -> CivStats.statsAsegurar(sfrA, VENTANA, false));
        hiloA.setDaemon(true);   // si algo falla antes de soltar el latch, no se cuelga la JVM al final del test
        hiloA.start();
        try {
            assertTrue(aEntroALaRed.await(5, TimeUnit.SECONDS), "A ya vio el hueco vacío y está \"descargando\"");
            CivStats.statsAsegurar(sfrB, VENTANA, false);   // B ve el mismo hueco vacío y termina antes
            assertEquals("B", CivStats.VENTANAS_STATS.get(VENTANA).etiqueta());
        } finally {
            dejarSalirA.countDown();   // suelta a A pase lo que pase arriba, para no dejarlo esperando para siempre
            hiloA.join(5000);
        }

        assertEquals("B", CivStats.VENTANAS_STATS.get(VENTANA).etiqueta(),
                "A vio el hueco vacío antes de que B terminara: no debe pisar el valor que B ya dejó puesto");
    }
}
