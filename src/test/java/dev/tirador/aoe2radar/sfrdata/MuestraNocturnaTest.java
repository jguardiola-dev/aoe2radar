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
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.sfrdata.EloNocturnoTest.gz;
import static org.junit.jupiter.api.Assertions.*;

/** La muestra de ayer sin red: un solo reloj (12 h desde la descarga), borrado de copias rotas y reintento tras fallo. */
class MuestraNocturnaTest {

    static final String BASE = "https://release/";
    static final long HORA = 3_600_000L;
    static final String JSON = "{\"fecha\":\"2026-09-24\",\"tramos\":{\"1000\":[[11,1],[12,2]],\"1500\":[[13,3]]}}";

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
        red.archivos.put(MuestraNocturna.ARCHIVO, gz(JSON));
    }

    MuestraNocturna nueva() { return new MuestraNocturna(sfr, new CacheService(reloj)); }
    Path copia() { return dir.resolve("perfiles_shards").resolve(MuestraNocturna.ARCHIVO); }
    long t0() throws IOException { return Files.getLastModifiedTime(copia()).toMillis(); }

    @Test void convierteLosTramos() {
        MuestraNocturna m = nueva();
        Map<String, List<List<Object>>> t = m.muestra();
        assertEquals(2, t.get("1000").size());
        assertEquals(List.of(13.0, 3.0), t.get("1500").get(0));
        assertEquals("2026-09-24", m.fecha());
        // los números llegan como Double: así los lee Json.parse, como en la 1.1
    }

    @Test void valeDoceHorasDesdeQueSeBajo() throws IOException {
        MuestraNocturna m = nueva();
        m.muestra();
        long t0 = t0();
        reloj.ahora = t0 + 12 * HORA - 1;
        m.muestra();
        assertEquals(1, red.pedidos.size());
        reloj.ahora = t0 + 12 * HORA;
        m.muestra();
        assertEquals(2, red.pedidos.size(), "memoria y copia caducan a la vez");
    }

    @Test void trasReiniciarCuentaDesdeLaCopia() throws IOException {
        nueva().muestra();
        long t0 = t0();
        reloj.ahora = t0 + 11 * HORA;
        MuestraNocturna reiniciada = nueva();
        reiniciada.muestra();
        assertEquals(1, red.pedidos.size(), "la reiniciada la lee del disco");
        reloj.ahora = t0 + 12 * HORA;
        reiniciada.muestra();
        assertEquals(2, red.pedidos.size(), "a las 12 h de la descarga, no de la lectura");
    }

    @Test void entreRecargasNoReleeElDisco() throws IOException {
        MuestraNocturna m = nueva();
        m.muestra();
        Files.write(copia(), gz(JSON.replace("[13,3]", "[99,9]")));
        reloj.ahora = t0() + 11 * HORA;
        assertEquals(List.of(13.0, 3.0), m.muestra().get("1500").get(0));
    }

    @Test void unaCopiaRotaSeBorraYSinRedEsNullYSeReintenta() throws IOException {
        Files.createDirectories(copia().getParent());
        Files.write(copia(), "roto".getBytes(StandardCharsets.UTF_8));
        MuestraNocturna m = nueva();
        assertNull(m.muestra());
        assertFalse(Files.exists(copia()), "no se entiende: se borra");
        assertNotNull(m.muestra(), "sin muestra no hay sello que esperar: la siguiente llamada vuelve a la red");
        assertEquals(1, red.pedidos.size());
    }

    @Test void siCaducaYLaRedFallaQuedaNull() throws IOException {
        MuestraNocturna m = nueva();
        m.muestra();
        reloj.ahora = t0() + 12 * HORA;
        red.archivos.clear();
        assertNull(m.muestra(), "caracterización de la 1.1: se pierde la vieja; «Al azar» y «Guess the ELO» van a la API");
    }

    @Test void unJsonNullCuentaComoCopiaRota() {
        red.archivos.put(MuestraNocturna.ARCHIVO, gz("null"));
        assertNull(nueva().muestra());
        assertFalse(Files.exists(copia()));
    }

    @Test void sinTramosEsUnaMuestraVacia() {
        red.archivos.put(MuestraNocturna.ARCHIVO, gz("{\"fecha\":\"2026-09-24\"}"));
        assertTrue(nueva().muestra().isEmpty(), "caracterización de la 1.1: vacía, no null");
    }
}
