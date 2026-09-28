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

import static dev.tirador.aoe2radar.sfrdata.EloNocturnoTest.gz;
import static org.junit.jupiter.api.Assertions.*;

/** Las chispas de sfr-data sin red: decodificación por diferencias, «completo», extra, 12 h desde la descarga y copias rotas. */
class ChispasNocturnasTest {

    static final String BASE = "https://release/";
    static final long HORA = 3_600_000L;
    /** Ejemplo con la forma que publica build_data.py 1.6 (chispa_codificar): 1500, 1512, 1498, 1510. */
    static final String JSON = "{\"v\":1,\"fecha\":\"2026-09-27\",\"n\":25,\"completo\":true,"
            + "\"j\":{\"7\":[1500,12,-14,12],\"8\":[900]},\"x\":{\"7\":[1650,120,100],\"8\":[-1,-1,-1]}}";

    @TempDir Path dir;
    final RelojFalso reloj = new RelojFalso();
    final EloNocturnoTest.Release red = new EloNocturnoTest.Release();
    SfrDataClient sfr;

    @BeforeEach void preparar() {
        reloj.ahora = System.currentTimeMillis();
        sfr = new SfrDataClient(dir, red, new SfrDataClient.Etags() {
            @Override public String leer(String n) { return ""; }
            @Override public void guardar(String n, String e) { }
        }, new CacheService(reloj), () -> BASE);
        red.archivos.put(ChispasNocturnas.ARCHIVO, gz(JSON));
    }

    ChispasNocturnas nuevas() { return new ChispasNocturnas(sfr, new CacheService(reloj)); }
    Path copia() { return dir.resolve("perfiles_shards").resolve(ChispasNocturnas.ARCHIVO); }

    @Test void decodificaLaSerieEnOrdenCronologico() {
        ChispasNocturnas.Chispa c = nuevas().chispa(7);
        assertArrayEquals(new int[]{ 1500, 1512, 1498, 1510 }, c.serie());
        assertArrayEquals(new int[]{ 1650, 120, 100 }, c.extra());
        assertTrue(c.completo());
    }

    @Test void unJugadorQueNoEstaTieneSerieVaciaYNoNull() {
        ChispasNocturnas.Chispa c = nuevas().chispa(99);
        assertNotNull(c, "el archivo está: la respuesta es «sin puntos», no «no sé»");
        assertEquals(0, c.serie().length);
        assertNull(c.extra());
    }

    @Test void parcialLoDice() {
        red.archivos.put(ChispasNocturnas.ARCHIVO, gz(JSON.replace("\"completo\":true", "\"completo\":false")));
        assertFalse(nuevas().chispa(7).completo());
    }

    @Test void sinArchivoEsNullYNoVuelveALaRedHastaCincoMinutos() {
        red.archivos.clear();
        ChispasNocturnas ch = nuevas();
        assertNull(ch.chispa(7), "aún no publicado: null (la tarjeta va a la API como antes)");
        red.archivos.put(ChispasNocturnas.ARCHIVO, gz(JSON));
        assertNull(ch.chispa(7), "lo dispara el ratón: tras un fallo, 5 min sin red");
        assertNull(ch.chispa(8));
        assertEquals(1, red.pedidos.size(), "una sola petición por muchos hovers");
        reloj.ahora += 5 * 60_000L;
        assertNotNull(ch.chispa(7), "a los 5 min vuelve a intentarlo");
        assertEquals(2, red.pedidos.size());
    }

    @Test void unFormatoDesconocidoTampocoSeBajaEnCadaHover() {
        red.archivos.put(ChispasNocturnas.ARCHIVO, gz(JSON.replace("\"v\":1", "\"v\":2")));
        ChispasNocturnas ch = nuevas();
        assertNull(ch.chispa(7));
        assertNull(ch.chispa(7));
        assertEquals(1, red.pedidos.size(), "2,5 MB que no se entienden no se vuelven a bajar a cada hover");
    }

    @Test void valeDoceHorasDesdeQueSeBajo() throws IOException {
        ChispasNocturnas ch = nuevas();
        ch.chispa(7);
        long t0 = Files.getLastModifiedTime(copia()).toMillis();
        reloj.ahora = t0 + 12 * HORA - 1;
        ch.chispa(8);
        assertEquals(1, red.pedidos.size(), "una sola descarga para todas las tarjetas");
        reloj.ahora = t0 + 12 * HORA;
        ch.chispa(7);
        assertEquals(2, red.pedidos.size());
    }

    @Test void unaCopiaRotaOUnFormatoDesconocidoSeBorra() throws IOException {
        Files.createDirectories(copia().getParent());
        Files.write(copia(), "roto".getBytes(StandardCharsets.UTF_8));
        assertNull(nuevas().chispa(7));
        assertFalse(Files.exists(copia()), "no se entiende: se borra");
        red.archivos.put(ChispasNocturnas.ARCHIVO, gz(JSON.replace("\"v\":1", "\"v\":2")));
        assertNull(nuevas().chispa(7), "una versión que esta app no conoce no se interpreta a ciegas");
        assertFalse(Files.exists(copia()));
    }
}
