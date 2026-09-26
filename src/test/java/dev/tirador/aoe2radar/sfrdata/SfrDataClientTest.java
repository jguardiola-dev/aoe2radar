package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** La caché en disco de sfr-data, sin red: carpeta temporal, red falsa, ETags en memoria y reloj falso. */
class SfrDataClientTest {

    /** Apunta cada petición y responde lo que el test haya encolado (por defecto, 200 «nuevo» con ETag "e2"). */
    static final class RedFalsa implements DescargaSfr {
        record Pedida(String url, String etag, int timeoutS) { }
        final List<Pedida> pedidas = new ArrayList<>();
        final Deque<Respuesta> respuestas = new ArrayDeque<>();
        byte[] bytes = "release".getBytes(StandardCharsets.UTF_8);
        @Override public Respuesta condicional(String url, String etag, int timeoutS) {
            pedidas.add(new Pedida(url, etag, timeoutS));
            return respuestas.isEmpty() ? new Respuesta(200, "nuevo".getBytes(StandardCharsets.UTF_8), "e2") : respuestas.poll();
        }
        @Override public byte[] bytes(String url, int timeoutS) {
            pedidas.add(new Pedida(url, null, timeoutS));
            return bytes;
        }
    }

    static final class EtagsFalsos implements SfrDataClient.Etags {
        final Map<String, String> m = new HashMap<>();
        @Override public String leer(String nombre) { return m.getOrDefault(nombre, ""); }
        @Override public void guardar(String nombre, String etag) { m.put(nombre, etag); }
    }

    @TempDir Path dir;
    final RelojFalso reloj = new RelojFalso();
    final RedFalsa red = new RedFalsa();
    final EtagsFalsos etags = new EtagsFalsos();
    SfrDataClient sfr;

    static final long HORA = 3_600_000L;

    @BeforeEach void preparar() {
        reloj.ahora = System.currentTimeMillis();   // las fechas de los archivos son reales: el reloj falso parte de la misma hora
        sfr = new SfrDataClient(dir, red, etags, new CacheService(reloj), () -> "https://release/");
    }

    Path copia(String nombre, String contenido, long edadMs) throws IOException {
        Path f = dir.resolve(nombre);
        Files.createDirectories(f.getParent());
        Files.writeString(f, contenido);
        Files.setLastModifiedTime(f, FileTime.fromMillis(reloj.ahora - edadMs));
        return f;
    }

    static String txt(byte[] b) { return new String(b, StandardCharsets.UTF_8); }

    // ----- datos(): rama «data», 6 h + ETag

    @Test void datosSinCopiaLoBajaSinEtagYGuardaArchivoYEtag() throws Exception {
        assertEquals("nuevo", txt(sfr.datos("ladder.json")));
        assertEquals(List.of(new RedFalsa.Pedida(SfrDataClient.SFR_DATA + "ladder.json", null, 60)), red.pedidas);
        assertEquals("nuevo", Files.readString(dir.resolve("ladder.json")));
        assertEquals("e2", etags.leer("ladder.json"));
    }

    @Test void datosConCopiaFrescaNoPideNada() throws Exception {
        copia("ladder.json", "guardado", 6 * HORA - 1);
        assertEquals("guardado", txt(sfr.datos("ladder.json")));
        assertTrue(red.pedidas.isEmpty());
    }

    @Test void datosCaducadoPreguntaConEtagY304RenuevaSinReescribir() throws Exception {
        Path f = copia("ladder.json", "guardado", 6 * HORA);
        etags.guardar("ladder.json", "e1");
        red.respuestas.add(new DescargaSfr.Respuesta(304, new byte[0], null));
        assertEquals("guardado", txt(sfr.datos("ladder.json")));
        assertEquals("e1", red.pedidas.get(0).etag(), "If-None-Match con el ETag guardado");
        assertEquals(reloj.ahora, Files.getLastModifiedTime(f).toMillis(), "el 304 renueva la fecha: vale otras 6 h");
        sfr.datos("ladder.json");
        assertEquals(1, red.pedidas.size(), "y la siguiente ya no pregunta");
    }

    @Test void datosConEtagPeroSinCopiaNoLoManda() throws Exception {
        etags.guardar("ladder.json", "e1");
        sfr.datos("ladder.json");
        assertNull(red.pedidas.get(0).etag(), "sin copia, un 304 no serviría de nada");
    }

    @Test void datosCaducadoSinEtagGuardadoNoMandaUnoVacio() throws Exception {
        copia("ladder.json", "guardado", 7 * HORA);
        sfr.datos("ladder.json");
        assertNull(red.pedidas.get(0).etag(), "sin ETag guardado no se manda If-None-Match: \"\"");
    }

    @Test void datosUn200SinEtagNoTocaElGuardado() throws Exception {
        etags.guardar("ladder.json", "e1");
        red.respuestas.add(new DescargaSfr.Respuesta(200, "nuevo".getBytes(StandardCharsets.UTF_8), null));
        sfr.datos("ladder.json");
        assertEquals("e1", etags.leer("ladder.json"));
    }

    @Test void datosConErrorUsaLaCopiaViejaYSinCopiaFalla() throws Exception {
        copia("ladder.json", "viejo", 30 * HORA);
        red.respuestas.add(new DescargaSfr.Respuesta(500, new byte[0], null));
        assertEquals("viejo", txt(sfr.datos("ladder.json")), "mejor un resumen de ayer que nada");
        red.respuestas.add(new DescargaSfr.Respuesta(500, new byte[0], null));
        IOException e = assertThrows(IOException.class, () -> sfr.datos("clans.json.gz"));
        assertEquals("HTTP 500 (clans.json.gz)", e.getMessage());
    }

    @Test void datosGzSeDescomprimeAlLeerYEnSubcarpetas() throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bo)) { gz.write("{\"a\":1}".getBytes(StandardCharsets.UTF_8)); }
        red.respuestas.add(new DescargaSfr.Respuesta(200, bo.toByteArray(), null));
        assertEquals("{\"a\":1}", txt(sfr.datos("civstats/tendencias.json.gz")));
        assertTrue(Files.exists(dir.resolve("civstats/tendencias.json.gz")), "en disco se guarda comprimido");
    }

    // ----- diario(): release «perfiles», 12 h

    @Test void diarioVale12HorasYDespuesSeBajaDeLaRelease() throws Exception {
        copia("perfiles_shards/elo_ayer.json.gz", "de hoy", 12 * HORA - 1);
        assertEquals("de hoy", txt(sfr.diario("elo_ayer.json.gz", 45)));
        assertTrue(red.pedidas.isEmpty());
        reloj.avanzar(1);
        assertEquals("release", txt(sfr.diario("elo_ayer.json.gz", 45)));
        assertEquals(new RedFalsa.Pedida("https://release/elo_ayer.json.gz", null, 45), red.pedidas.get(0), "con el timeout que se le pasa");
        assertEquals("release", Files.readString(dir.resolve("perfiles_shards/elo_ayer.json.gz")));
    }

    // ----- versionado(): paquetes de perfil, por versión

    @Test void versionadoSoloSeBajaSiCambiaLaVersion() throws Exception {
        assertEquals("release", txt(sfr.versionado("shard-0001.json.gz", "2026-09-24", 120)));
        assertEquals(1, red.pedidas.size());
        reloj.avanzar(365 * 24 * HORA);
        sfr.versionado("shard-0001.json.gz", "2026-09-24", 120);
        assertEquals(1, red.pedidas.size(), "misma versión: vale aunque pase un año");
        sfr.versionado("shard-0001.json.gz", "2026-09-25", 120);
        assertEquals(2, red.pedidas.size());
        assertEquals("2026-09-25", Files.readString(dir.resolve("perfiles_shards/shard-0001.json.gz.v")));
    }

    @Test void laBaseDeLaReleaseSeLeeEnCadaLlamada() throws Exception {
        Deque<String> bases = new ArrayDeque<>(List.of("https://a/", "https://b/"));
        SfrDataClient c = new SfrDataClient(dir, red, etags, new CacheService(reloj), bases::poll);
        c.diario("muestra_ayer.json.gz", 60);
        c.versionado("shard-0003.json.gz", "v1", 120);
        assertEquals("https://a/muestra_ayer.json.gz", red.pedidas.get(0).url());
        assertEquals("https://b/shard-0003.json.gz", red.pedidas.get(1).url(), "el mando a distancia puede cambiarla entre llamadas");
    }

    @Test void versionadoIgnoraEspaciosEnLaMarca() throws Exception {
        copia("perfiles_shards/shard-0004.json.gz", "guardado", 0);
        copia("perfiles_shards/shard-0004.json.gz.v", "2026-09-24\n", 0);
        assertEquals("guardado", txt(sfr.versionado("shard-0004.json.gz", "2026-09-24", 120)));
        assertTrue(red.pedidas.isEmpty());
    }

    @Test void versionadoConBasePropia() throws Exception {
        sfr.versionado("https://otra/", "shard-0002.json.gz", "base-1", 120);
        assertEquals("https://otra/shard-0002.json.gz", red.pedidas.get(0).url());
    }

    // ----- escritura atómica (fila 70 de DEUDA): a un ".tmp" y luego Files.move -----

    @Test void diarioNoDejaUnTmpBasuraDeUnCorteAnterior() throws Exception {
        Path tmp = dir.resolve("perfiles_shards/elo_ayer.json.gz.tmp");
        Files.createDirectories(tmp.getParent());
        Files.writeString(tmp, "basura de un corte a medias");
        assertEquals("release", txt(sfr.diario("elo_ayer.json.gz", 45)));
        assertEquals("release", Files.readString(dir.resolve("perfiles_shards/elo_ayer.json.gz")));
        assertFalse(Files.exists(tmp), "la escritura atomica no deja basura de un corte anterior");
    }

    @Test void versionadoNoDejaUnTmpBasuraDeUnCorteAnterior() throws Exception {
        Path tmp = dir.resolve("perfiles_shards/shard-0009.json.gz.tmp");
        Files.createDirectories(tmp.getParent());
        Files.writeString(tmp, "basura");
        assertEquals("release", txt(sfr.versionado("shard-0009.json.gz", "2026-09-24", 120)));
        assertFalse(Files.exists(tmp), "la escritura atomica no deja basura de un corte anterior");
        assertEquals("2026-09-24", Files.readString(dir.resolve("perfiles_shards/shard-0009.json.gz.v")));
    }
}
