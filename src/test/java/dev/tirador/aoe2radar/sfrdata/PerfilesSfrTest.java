package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.sfrdata.EloNocturnoTest.gz;
import static dev.tirador.aoe2radar.util.Json.arr;
import static dev.tirador.aoe2radar.util.Json.lng;
import static org.junit.jupiter.api.Assertions.*;

/** Índice y paquetes de perfil de la release «perfiles», sin red: por URL completa, bytes; si no está, 404. */
class PerfilesSfrTest {

    static final String BASE = "https://h/releases/download/perfiles/";
    static final long HORA = 3_600_000L;

    static final class Release implements DescargaSfr {
        final Map<String, byte[]> urls = new HashMap<>();
        final List<String> pedidas = new ArrayList<>();
        @Override public Respuesta condicional(String url, String etag, int timeoutS) { throw new AssertionError("no es de la rama data"); }
        @Override public byte[] bytes(String url, int timeoutS) throws IOException {
            pedidas.add(url);
            byte[] b = urls.get(url);
            if (b == null) throw new IOException("HTTP 404 " + url);
            return b;
        }
    }

    @TempDir Path dir;
    final RelojFalso reloj = new RelojFalso();
    final Release red = new Release();

    @BeforeEach void preparar() { reloj.ahora = System.currentTimeMillis(); }

    PerfilesSfr nuevo() {
        SfrDataClient sfr = new SfrDataClient(dir, red, new SfrDataClient.Etags() {
            @Override public String leer(String n) { return ""; }
            @Override public void guardar(String n, String e) { }
        }, new CacheService(reloj), () -> BASE);
        return new PerfilesSfr(sfr, new CacheService(reloj));
    }

    void indice(String json) { red.urls.put(BASE + "index.json", json.getBytes(StandardCharsets.UTF_8)); }

    /** La copia en disco lleva la hora real de cuando se escribió: T0 = esa hora exacta, para fronteras sin holgura. */
    long t0() throws IOException { return Files.getLastModifiedTime(dir.resolve("perfiles_shards/index.json")).toMillis(); }

    // ----- índice

    @Test void elIndiceValeSeisHorasDesdeQueSeBajo() throws IOException {
        indice("{\"hasta\":\"2026-09-24\"}");
        PerfilesSfr p = nuevo();
        assertEquals("2026-09-24", p.indice().get("hasta"));
        long t0 = t0();
        reloj.ahora = t0 + 6 * HORA - 1;
        assertEquals("2026-09-24", p.indice().get("hasta"));
        assertEquals(1, red.pedidas.size(), "a 6 h − 1 ms, ni memoria ni disco han caducado");
        reloj.ahora = t0 + 6 * HORA;
        indice("{\"hasta\":\"2026-09-25\"}");
        assertEquals("2026-09-25", p.indice().get("hasta"), "a las 6 h justas caducan los dos a la vez: cambia cada noche");
        assertEquals(2, red.pedidas.size());
    }

    @Test void trasReiniciarLaMemoriaCuentaDesdeLaCopiaNoDesdeLaLectura() throws IOException {
        indice("{\"hasta\":\"2026-09-24\"}");
        nuevo().indice();                                  // T0: se baja y se guarda
        long t0 = t0();
        reloj.ahora = t0 + 5 * HORA;
        PerfilesSfr reiniciada = nuevo();                  // la app reiniciada a las 5 h lo lee del disco
        assertEquals("2026-09-24", reiniciada.indice().get("hasta"));
        assertEquals(1, red.pedidas.size());
        reloj.ahora = t0 + 6 * HORA;
        indice("{\"hasta\":\"2026-09-25\"}");
        assertEquals("2026-09-25", reiniciada.indice().get("hasta"), "a las 6 h de la descarga, no a las 6 h de la lectura (serían 11 h)");
    }

    @Test void unFalloDeRedConIndiceAnteriorDejaNull() throws IOException {
        indice("{\"hasta\":\"2026-09-24\"}");
        PerfilesSfr p = nuevo();
        p.indice();
        reloj.ahora = t0() + 6 * HORA;
        red.urls.clear();
        assertNull(p.indice(), "caracterización de la 1.1: el fallo deja null, no el anterior");
    }

    @Test void elIndiceToleraUnNaNSuelto() {
        indice("{\"alcance\":[NaN,1],\"x\":[1,NaN]}");
        Map<String, Object> idx = nuevo().indice();
        assertEquals("", arr(idx.get("alcance")).get(0));
        assertEquals("", arr(idx.get("x")).get(1));
    }

    @Test void sinIndiceEsNullYSeReintentaEnLaSiguienteLlamada() {
        PerfilesSfr p = nuevo();
        assertNull(p.indice(), "sin release todavía");
        indice("{\"hasta\":\"2026-09-24\"}");
        assertNotNull(p.indice(), "sin índice no hay sello que esperar, como en la 1.1");
    }

    @Test void unIndiceGuardadoIlegibleSeBorra() throws IOException {
        Path f = dir.resolve("perfiles_shards/index.json");
        Files.createDirectories(f.getParent());
        Files.writeString(f, "{roto");
        assertNull(nuevo().indice());
        assertFalse(Files.exists(f), "así la siguiente llamada vuelve a la red");
    }

    @Test void unIndiceQueNoEsUnObjetoConservaElAnterior() throws IOException {
        indice("{\"hasta\":\"2026-09-24\"}");
        PerfilesSfr p = nuevo();
        p.indice();
        reloj.ahora = t0() + 6 * HORA;
        indice("[]");
        assertEquals("2026-09-24", p.indice().get("hasta"), "caracterización de la 1.1: solo se sustituye por un objeto");
        assertEquals(2, red.pedidas.size(), "y de verdad se leyó el «[]» nuevo");
    }

    // ----- paquetes

    @Test void sinIndiceNoHayPaquete() throws Exception {
        assertNull(nuevo().shard(1L));
    }

    @Test void formatoV1UnPaquetePorGrupoDeJugadores() throws Exception {
        indice("{\"v\":1,\"shards\":256,\"hasta\":\"2026-09-24\"}");
        red.urls.put(BASE + "shard-044.json.gz", gz("{\"j\":{\"300\":[[7]]}}"));   // 300 % 256 = 44
        Map<String, Object> m = nuevo().shard(300L);
        assertEquals(1L, m.get("v"));
        assertEquals("2026-09-24", Files.readString(dir.resolve("perfiles_shards/shard-044.json.gz.v")), "versión = «hasta» del índice");
    }

    @Test void formatoV2BaseEnSuReleaseMasDeltasSinRepetir() throws Exception {
        indice("{\"v\":2,\"shards\":1000,\"grupos\":16,\"base_hasta\":\"2026-09-20\",\"base_release\":\"perfiles-base-7\","
                + "\"deltas\":[\"2026-09-21\",\"2026-09-22\"],\"hasta\":\"2026-09-22\",\"civs\":[\"Aztecs\"],\"mapas\":[\"Arabia\"]}");
        // pid 1234 → paquete 234 → grupo 234 % 16 = 10; la base vive en la release «perfiles-base-7»
        red.urls.put("https://h/releases/download/perfiles-base-7/shard-0234.json.gz", gz("{\"j\":{\"1234\":[[1,\"a\"],[2,\"b\"]],\"5\":[[9]]}}"));
        red.urls.put(BASE + "delta-2026-09-21-g10.json.gz", gz("{\"j\":{\"1234\":[[2,\"b\"],[3,\"c\"]]}}"));
        // el delta del 22 no está (404): se anota en el log y se sigue
        Map<String, Object> m = nuevo().shard(1234L);
        assertEquals(2L, m.get("v"));
        assertEquals(List.of("Aztecs"), m.get("civs"));
        assertEquals("2026-09-22", m.get("hasta"));
        @SuppressWarnings("unchecked") Map<String, List<Object>> j = (Map<String, List<Object>>) m.get("j");
        assertEquals(List.of(1L, 2L, 3L), j.get("1234").stream().map(o -> lng(arr(o).get(0))).toList(), "la partida 2 no se repite");
        assertEquals(1, j.get("5").size(), "los demás jugadores del paquete quedan tal cual");
        assertEquals("perfiles-base-7", Files.readString(dir.resolve("perfiles_shards/shard-0234.json.gz.v")), "la versión de la base es su release");
    }

    @Test void valoresPorDefectoDelIndiceYVersionDelDelta() throws Exception {
        // sin «shards» (256) ni «grupos» (16); «base_release» en blanco cuenta como que no hay
        indice("{\"v\":2,\"base_hasta\":\"2026-09-20\",\"base_release\":\"  \",\"deltas\":[\"2026-09-21\"],\"mapas\":[\"Arabia\"]}");
        red.urls.put(BASE + "shard-0044.json.gz", gz("{\"j\":{}}"));                // 300 % 256 = 44
        red.urls.put(BASE + "delta-2026-09-21-g12.json.gz", gz("{\"j\":{}}"));      // 44 % 16 = 12
        Map<String, Object> m = nuevo().shard(300L);
        assertEquals(List.of("Arabia"), m.get("mapas"));
        assertEquals("2026-09-21", Files.readString(dir.resolve("perfiles_shards/delta-2026-09-21-g12.json.gz.v")), "la versión de un delta es su fecha");
        assertTrue(red.pedidas.contains(BASE + "shard-0044.json.gz"), "base en blanco: la release de siempre");
    }

    @Test void formatoV2SinReleaseDeBaseUsaLaDeSiempre() throws Exception {
        indice("{\"v\":2,\"shards\":1000,\"base_hasta\":\"2026-09-20\",\"deltas\":[]}");
        red.urls.put(BASE + "shard-0007.json.gz", gz("{\"j\":{}}"));
        nuevo().shard(7L);
        assertEquals("2026-09-20", Files.readString(dir.resolve("perfiles_shards/shard-0007.json.gz.v")), "versión = «base_hasta»");
    }
}
