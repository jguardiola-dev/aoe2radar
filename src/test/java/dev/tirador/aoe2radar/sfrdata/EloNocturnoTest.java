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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** El ELO nocturno sin red: release falsa, carpeta temporal, reloj falso y un segundo plano que el test ejecuta a mano. */
class EloNocturnoTest {

    static final String BASE = "https://release/";
    static final String AYER = "elo_ayer.json.gz", HACE7 = "elo-2026-09-18.json.gz";   // hoy (UTC) = 2026-09-25
    static final Supplier<LocalDate> HOY = () -> LocalDate.of(2026, 9, 25);
    static final long MIN = 60_000L, HORA = 60 * MIN;

    /** La release: por nombre, bytes; si no está, 404. Apunta cada descarga. */
    static final class Release implements DescargaSfr {
        final Map<String, byte[]> archivos = new HashMap<>();
        final List<String> pedidos = new ArrayList<>();
        Runnable alDescargar = () -> { };
        @Override public Respuesta condicional(String url, String etag, int timeoutS) { throw new AssertionError("no es de la rama data"); }
        @Override public byte[] bytes(String url, int timeoutS) throws IOException {
            String nombre = url.substring(BASE.length());
            pedidos.add(nombre);
            alDescargar.run();
            byte[] b = archivos.get(nombre);
            if (b == null) throw new IOException("HTTP 404 " + url);
            return b;
        }
    }

    static byte[] gz(String json) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPOutputStream g = new GZIPOutputStream(bo)) { g.write(json.getBytes(StandardCharsets.UTF_8)); }
            return bo.toByteArray();
        } catch (IOException e) { throw new AssertionError(e); }
    }
    static final String JSON_AYER = "{\"fecha\":\"2026-09-24\",\"j\":{\"1\":[1905,300,2110,800,\"12Tirador\",\"es\"],\"2\":[1610,90,0,0,\"Turpiacho\",\"es\"]}}";
    static final String JSON_HACE7 = "{\"j\":{\"1\":[1880,290,2100,790]}}";

    @TempDir Path dir;
    final RelojFalso reloj = new RelojFalso();
    final Release red = new Release();
    final List<Runnable> enSegundoPlano = new ArrayList<>();
    SfrDataClient sfr;
    EloNocturno elo;

    @BeforeEach void preparar() {
        reloj.ahora = System.currentTimeMillis();   // las copias del disco llevan fecha real
        sfr = new SfrDataClient(dir, red, new SfrDataClient.Etags() {
            @Override public String leer(String n) { return ""; }
            @Override public void guardar(String n, String e) { }
        }, new CacheService(reloj), () -> BASE);
        elo = new EloNocturno(sfr, new CacheService(reloj), enSegundoPlano::add, HOY);
        red.archivos.put(AYER, gz(JSON_AYER));
        red.archivos.put(HACE7, gz(JSON_HACE7));
    }

    Path copia(String nombre) { return dir.resolve("perfiles_shards").resolve(nombre); }

    @Test void laPrimeraCargaVaEnLineaYRellenaLosTresMapas() {
        elo.cargar();
        assertTrue(enSegundoPlano.isEmpty(), "la primera carga no va a segundo plano");
        assertEquals(List.of(AYER, HACE7), red.pedidos, "el de hace 7 días sale de «hoy» en UTC");
        assertArrayEquals(new int[]{ 1905, 300, 2110, 800 }, elo.ayer.get(1L));
        assertArrayEquals(new String[]{ "Turpiacho", "es" }, elo.nombres.get(2L));
        assertArrayEquals(new int[]{ 1880, 290, 2100, 790 }, elo.hace7.get(1L));
        assertEquals("2026-09-24", elo.fechaAyer());
        assertEquals("2026-09-18", elo.fechaHace7());
    }

    @Test void siLlegaValeSeisHorasYElRefrescoVaEnLinea() {
        elo.cargar();
        reloj.avanzar(6 * HORA - 1);
        elo.cargar();
        assertEquals(2, red.pedidos.size(), "dentro de las 6 h no se vuelve a cargar");
        reloj.avanzar(13 * HORA);   // más allá de las 12 h de la copia en disco, para verlo en la red
        elo.cargar();
        assertEquals(4, red.pedidos.size());
        assertTrue(enSegundoPlano.isEmpty(), "el refresco tras un éxito va en línea, como en la 1.1");
    }

    @Test void laFronteraDeLasSeisHoras() throws IOException {
        elo.cargar();
        Files.write(copia(AYER), gz(JSON_AYER.replace("1905", "1999")));   // la copia del disco sigue fresca (12 h): se ve si se relee
        reloj.avanzar(6 * HORA - 1);
        elo.cargar();
        assertEquals(1905, elo.ayer.get(1L)[0], "a 6 h − 1 ms no se relee");
        reloj.avanzar(1);
        elo.cargar();
        assertEquals(1999, elo.ayer.get(1L)[0], "a las 6 h justas, sí");
    }

    @Test void unHaceSieteRotoSeBorraPeroNoEsFallo() throws IOException {
        Files.createDirectories(copia(HACE7).getParent());
        Files.write(copia(HACE7), "roto".getBytes(StandardCharsets.UTF_8));
        elo.cargar();
        assertFalse(Files.exists(copia(HACE7)));
        assertEquals(1905, elo.ayer.get(1L)[0]);
    }

    @Test void unFalloDeRedNoBorraLaCopia() throws IOException {
        elo.cargar();
        reloj.avanzar(13 * HORA);           // la copia caduca…
        red.archivos.remove(AYER);          // …y la red falla
        elo.cargar();
        assertTrue(Files.exists(copia(AYER)), "no había nada roto: la copia se queda");
    }

    @Test void unaFilaRaraNoBorraUnArchivoLegible() {
        red.archivos.put(AYER, gz("{\"fecha\":\"x\",\"j\":{\"no-es-un-pid\":[1,2,3,4,\"a\",\"b\"]}}"));
        elo.cargar();
        assertTrue(Files.exists(copia(AYER)), "la release no cambia por volver a bajarla: sin borrar, como en la 1.1");
    }

    @Test void siFallaReintentaALosCincoMinutosEnSegundoPlano() {
        red.archivos.remove(AYER);   // 404: release aún sin publicar
        elo.cargar();
        assertEquals(List.of(AYER), red.pedidos, "sin el de ayer no se pide el de hace 7");
        assertTrue(elo.ayer.isEmpty());
        reloj.avanzar(5 * MIN - 1);
        elo.cargar();
        assertEquals(1, red.pedidos.size(), "a los 4:59, nada");
        reloj.avanzar(1);
        elo.cargar();
        assertEquals(1, red.pedidos.size(), "a los 5:00 no espera quien llama…");
        assertEquals(1, enSegundoPlano.size(), "…la descarga va a segundo plano");
        elo.cargar();
        assertEquals(1, enSegundoPlano.size(), "mientras carga, nadie lanza otra");
        red.archivos.put(AYER, gz(JSON_AYER));
        enSegundoPlano.get(0).run();
        assertEquals(1905, elo.ayer.get(1L)[0]);
        reloj.avanzar(5 * MIN);
        elo.cargar();
        assertEquals(1, enSegundoPlano.size(), "tras el éxito, 6 h de calma");
    }

    @Test void unArchivoRotoSeBorraYElSiguienteVuelveALaRed() throws IOException {
        Files.createDirectories(copia(AYER).getParent());
        Files.write(copia(AYER), "no es gzip".getBytes(StandardCharsets.UTF_8));   // una copia fresca pero ilegible
        elo.cargar();
        assertTrue(red.pedidos.isEmpty(), "la copia era fresca: no se fue a la red");
        assertFalse(Files.exists(copia(AYER)), "no se entiende: se borra");
        reloj.avanzar(5 * MIN);
        elo.cargar();
        enSegundoPlano.forEach(Runnable::run);
        assertEquals(List.of(AYER, HACE7), red.pedidos, "el reintento baja una copia nueva");
        assertEquals(1905, elo.ayer.get(1L)[0]);
    }

    @Test void sinElMapaJCuentaComoFalloYSeBorra() {
        red.archivos.put(AYER, gz("{\"fecha\":\"2026-09-24\"}"));
        elo.cargar();
        assertFalse(Files.exists(copia(AYER)));
        assertEquals(List.of(AYER, HACE7), red.pedidos, "sin «j» no es una excepción: se sigue con el de hace 7, como en la 1.1");
        reloj.avanzar(5 * MIN);
        elo.cargar();
        assertEquals(1, enSegundoPlano.size(), "y es un fallo: reintento a los 5 min");
    }

    @Test void siFallaElDeHaceSieteNoEsUnFallo() {
        red.archivos.remove(HACE7);
        elo.cargar();
        assertEquals(1905, elo.ayer.get(1L)[0]);
        assertTrue(elo.hace7.isEmpty());
        reloj.avanzar(5 * MIN);
        elo.cargar();
        assertEquals(2, red.pedidos.size(), "el de ayer llegó: 6 h, no 5 min");
    }

    @Test void dosLlamadoresALaVezNoDescarganDosVeces() {
        red.alDescargar = () -> elo.cargar();   // otro llamador entra mientras se descarga
        elo.cargar();
        assertEquals(List.of(AYER, HACE7), red.pedidos, "el segundo vuelve sin hacer nada");
    }

    @Test void siElSegundoPlanoNoArrancaNoSeQuedaCargando() {
        EloNocturno rechaza = new EloNocturno(sfr, new CacheService(reloj), t -> { throw new RejectedExecutionException("sin hilos"); }, HOY);
        red.archivos.remove(AYER);
        rechaza.cargar();                      // la primera va en línea y falla (404)
        reloj.avanzar(5 * MIN);
        assertThrows(RejectedExecutionException.class, rechaza::cargar);
        assertThrows(RejectedExecutionException.class, rechaza::cargar, "no quedó «cargando»: lo vuelve a intentar");
    }
}
