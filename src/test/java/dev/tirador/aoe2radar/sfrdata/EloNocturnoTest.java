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
        String cortarEn;   // este archivo sale como InterruptedException (Detener a mitad de descarga)
        @Override public Respuesta condicional(String url, String etag, int timeoutS) { throw new AssertionError("no es de la rama data"); }
        @Override public byte[] bytes(String url, int timeoutS) throws IOException, InterruptedException {
            String nombre = url.substring(BASE.length());
            pedidos.add(nombre);
            alDescargar.run();
            if (nombre.equals(cortarEn)) throw new InterruptedException("detenido");
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

    /** Hora real de la copia del ELO de ayer en disco: T0 exacto para fronteras sin holgura. */
    long t0() throws IOException { return Files.getLastModifiedTime(copia(AYER)).toMillis(); }

    @Test void siLlegaValeDoceHorasDesdeQueSeBajoYElRefrescoVaEnLinea() throws IOException {
        elo.cargar();
        long t0 = t0();
        reloj.ahora = t0 + 12 * HORA - 1;
        elo.cargar();
        assertEquals(2, red.pedidos.size(), "a 12 h − 1 ms no se recarga: ni la memoria ni la copia han caducado");
        reloj.ahora = t0 + 12 * HORA;
        elo.cargar();
        assertEquals(2, red.pedidos.stream().filter(AYER::equals).count(), "a las 12 h justas caducan las dos a la vez y se va a la red");
        assertTrue(enSegundoPlano.isEmpty(), "el refresco tras un éxito va en línea, como en la 1.1");
    }

    @Test void trasReiniciarCuentaDesdeLaCopiaNoDesdeLaLectura() throws IOException {
        elo.cargar();                                      // T0: se baja y se guarda
        long t0 = t0();
        reloj.ahora = t0 + 11 * HORA;
        EloNocturno reiniciada = new EloNocturno(sfr, new CacheService(reloj), enSegundoPlano::add, HOY);
        reiniciada.cargar();                               // la app reiniciada a las 11 h lo lee del disco
        assertEquals(2, red.pedidos.size());
        reloj.ahora = t0 + 12 * HORA;
        reiniciada.cargar();
        assertEquals(2, red.pedidos.stream().filter(AYER::equals).count(), "a las 12 h de la descarga, no a las 6 h de la lectura (serían 17 h)");
    }

    @Test void alCambiarElDiaUtcSePideElNuevoDeHaceSiete() {
        LocalDate[] dia = { LocalDate.of(2026, 9, 25) };
        EloNocturno e = new EloNocturno(sfr, new CacheService(reloj), enSegundoPlano::add, () -> dia[0]);
        e.cargar();
        reloj.avanzar(HORA);
        e.cargar();
        assertEquals(List.of(AYER, HACE7), red.pedidos, "mismo día y copia fresca: nada");
        dia[0] = LocalDate.of(2026, 9, 26);                 // medianoche UTC
        red.archivos.put("elo-2026-09-19.json.gz", gz(JSON_HACE7));
        e.cargar();
        assertEquals(List.of(AYER, HACE7, "elo-2026-09-19.json.gz"), red.pedidos, "el de ayer, del disco; el nuevo de hace 7, de la red");
        assertEquals("2026-09-19", e.fechaHace7());
    }

    @Test void unJsonNullCuentaComoCopiaRota() throws IOException {
        red.archivos.put(AYER, gz("null"));
        elo.cargar();
        assertFalse(Files.exists(copia(AYER)), "«null» no es un volcado: se borra");
    }

    @Test void entreRecargasNoReleeElDisco() throws IOException {
        elo.cargar();
        Files.write(copia(AYER), gz(JSON_AYER.replace("1905", "1999")));   // si se releyera el disco, se vería
        reloj.ahora = t0() + 11 * HORA;
        elo.cargar();
        assertEquals(1905, elo.ayer.get(1L)[0], "sin bucle de relecturas: la memoria vale lo mismo que la copia");
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
        assertEquals(1, enSegundoPlano.size(), "tras el éxito, calma hasta que caduque la copia");
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
        assertEquals(2, red.pedidos.size(), "el de ayer llegó: sin reintento a los 5 min");
    }

    @Test void dosLlamadoresALaVezNoDescarganDosVeces() {
        red.alDescargar = () -> elo.cargar();   // otro llamador entra mientras se descarga
        elo.cargar();
        assertEquals(List.of(AYER, HACE7), red.pedidos, "el segundo vuelve sin hacer nada");
    }

    @Test void siSeDetieneAntesDelDeAyerConservaLaMarcaYNoEsFallo() {
        red.cortarEn = AYER;
        try {
            elo.cargar();
            assertTrue(Thread.interrupted(), "la interrupción no se traga: quien llama se entera (y el test la limpia)");
        } finally { Thread.interrupted(); }
        assertEquals(List.of(AYER), red.pedidos, "tras el corte no se sigue con el de hace 7");
        red.cortarEn = null;
        elo.cargar();
        assertTrue(enSegundoPlano.isEmpty(), "no fue un fallo: ni 5 min de espera ni segundo plano…");
        assertEquals(List.of(AYER, AYER, HACE7), red.pedidos, "…se vuelve a pedir en línea al momento");
        assertEquals(1905, elo.ayer.get(1L)[0]);
    }

    @Test void siSeDetieneEnElDeHaceSieteElDeAyerValeYConservaLaMarca() throws IOException {
        red.cortarEn = HACE7;
        try {
            elo.cargar();
            assertTrue(Thread.interrupted(), "la interrupción no se queda en el catch del de hace 7");
        } finally { Thread.interrupted(); }
        assertEquals(1905, elo.ayer.get(1L)[0]);
        red.cortarEn = null;
        reloj.ahora = t0() + 12 * HORA;   // caduca la copia: ahora decide «fallo», no el sello de éxito
        elo.cargar();
        assertTrue(enSegundoPlano.isEmpty(), "el de ayer llegó: fue un éxito y el refresco va en línea, no como reintento");
        assertEquals(2, red.pedidos.stream().filter(AYER::equals).count());
    }

    @Test void sinJYCorteEnElDeHaceSieteSigueSiendoFallo() {
        red.archivos.put(AYER, gz("{\"fecha\":\"2026-09-24\"}"));
        red.cortarEn = HACE7;
        try { elo.cargar(); } finally { Thread.interrupted(); }
        red.cortarEn = null;
        elo.cargar();
        assertEquals(List.of(AYER, HACE7), red.pedidos, "el de ayer ya se leyó (mal): se apunta intento, nada en línea…");
        reloj.avanzar(5 * MIN);
        elo.cargar();
        assertEquals(1, enSegundoPlano.size(), "…y es un fallo: reintento a los 5 min en segundo plano");
    }

    // ----- fila 73 de DEUDA: al refrescar, ayer/hace7 nunca se ven vacíos a medias -----

    @Test void elMapaDeAyerNuncaSeVeVacioMientrasSeRefresca() throws Exception {
        int n = 20_000;
        StringBuilder j1 = new StringBuilder("{\"fecha\":\"2026-09-24\",\"j\":{");
        for (int i = 1; i <= n; i++) { if (i > 1) j1.append(','); j1.append('"').append(i).append("\":[1,1,1,1,\"J").append(i).append("\",\"es\"]"); }
        j1.append("}}");
        red.archivos.put(AYER, gz(j1.toString()));
        elo.cargar();                       // primera carga: fija los pids que se repetirán en el refresco
        assertEquals(n, elo.ayer.size());

        StringBuilder j2 = new StringBuilder("{\"fecha\":\"2026-09-25\",\"j\":{");
        for (int i = 1; i <= n; i++) { if (i > 1) j2.append(','); j2.append('"').append(i).append("\":[2,2,2,2,\"J").append(i).append("\",\"es\"]"); }
        j2.append("}}");
        red.archivos.put(AYER, gz(j2.toString()));   // segunda noche: mismos pids, valores nuevos
        reloj.avanzar(13 * HORA);           // caduca la copia de ayer: toca refrescar

        java.util.concurrent.atomic.AtomicBoolean vistoVacio = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean sigueLeyendo = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread lector = new Thread(() -> {
            while (sigueLeyendo.get()) if (elo.ayer.isEmpty()) { vistoVacio.set(true); break; }
        });
        lector.setDaemon(true);   // si algo falla abajo, no se cuelga la JVM al final del test
        lector.start();
        try {
            elo.cargar();                    // el refresco corre en este hilo mientras «lector» espía elo.ayer
        } finally {
            sigueLeyendo.set(false);
            lector.join(5000);
        }

        assertFalse(vistoVacio.get(), "quien lee ayer mientras se refresca (mismos jugadores) nunca lo ve vacío");
        assertEquals(2, elo.ayer.get(1L)[0], "y al final tiene los valores de la noche nueva");
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
