package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.util.CfgLanzador;
import dev.tirador.aoe2radar.util.Instalacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El actualizador propio: update.json, la decisión jar/completa y la descarga. Sin red (transporte y descarga
 * falsos) y en carpetas temporales que imitan la instalación: nunca GitHub ni la instalación real.
 */
class ActualizadorServiceTest {

    static final String MAIN = "dev.tirador.aoe2radar.SpoilerFreeRecs";
    static final String CFG = "[Application]\r\n"
            + "app.classpath=$APPDIR\\aoe2radar-1.4.0.jar\r\n"
            + "app.mainclass=" + MAIN + "\r\n"
            + "app.classpath=$APPDIR\\flatlaf-3.7.2.jar\r\n"
            + "\n"
            + "[JavaOptions]\r\n"
            + "java-options=-Djpackage.app-version=1.4\r\n"
            + "java-options=-Dfile.encoding=UTF-8\r\n";

    @TempDir Path tmp;
    Path app, dir;
    Instalacion.Rutas rutas;
    byte[] jar15;
    final List<String> pedidas = new ArrayList<>();
    String cuerpoUpdate;
    int estadoUpdate = 200;
    Descargas descargas = new Descargas();

    /** Descarga falsa: sirve bytes, o lanza, o se queda esperando a que el test la suelte. */
    static final class Descargas implements ActualizadorService.Descarga {
        byte[] bytes;
        InputStream flujo;
        IOException fallo;
        CountDownLatch dentro, soltar;
        final List<String> urls = new ArrayList<>();
        @Override public InputStream abrir(String url) throws IOException, InterruptedException {
            synchronized (urls) { urls.add(url); }
            if (dentro != null) { dentro.countDown(); soltar.await(5, TimeUnit.SECONDS); }
            if (fallo != null) throw fallo;
            return flujo != null ? flujo : new ByteArrayInputStream(bytes);
        }
    }

    static byte[] jar(String semilla, String... entradas) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            for (String e : entradas) { z.putNextEntry(new ZipEntry(e)); z.write((semilla + e).getBytes()); z.closeEntry(); }
        }
        return b.toByteArray();
    }

    static byte[] jarBueno(String semilla) throws IOException {
        return jar(semilla, "dev/tirador/aoe2radar/app/Main.class", MAIN.replace('.', '/') + ".class");
    }

    static String sha(byte[] b) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }

    String updateJson(String version, byte[] jar, String runtime) throws Exception {
        return "{\"version\":\"" + version + "\",\"jar\":\"aoe2radar-" + version + ".jar\",\"sha256\":\"" + sha(jar) + "\","
                + "\"size\":" + jar.length + ",\"runtime\":\"" + runtime + "\",\"instalador\":\"aoe2radar-" + version + "-setup.exe\","
                + "\"classpath\":[\"flatlaf-3.7.2.jar\"],\"opciones\":[\"-Dfile.encoding=UTF-8\"],\"mainclass\":\"" + MAIN + "\"}";
    }

    @BeforeEach void instalacion() throws Exception {
        app = Files.createDirectories(tmp.resolve("aoe2radar").resolve("app"));
        Files.writeString(app.resolve("aoe2radar.cfg"), CFG, StandardCharsets.ISO_8859_1);
        Files.write(app.resolve("aoe2radar-1.4.0.jar"), jarBueno("1.4"));
        dir = tmp.resolve("datos").resolve(Instalacion.CARPETA);
        rutas = new Instalacion.Rutas(app, app.resolve("aoe2radar.cfg"), dir, "aoe2radar-1.4.0.jar");
        jar15 = jarBueno("1.5");
        cuerpoUpdate = updateJson("1.5", jar15, "21.0.9");
        descargas.bytes = jar15;
    }

    ActualizadorService servicio() {
        Transporte t = url -> { pedidas.add(url); return new Transporte.Respuesta(estadoUpdate, cuerpoUpdate); };
        return new ActualizadorService(t, descargas, () -> rutas, "1.4", "21.0.9");
    }

    // ------------------------------------------------------------------ update.json

    @Test void leeUnUpdateJsonCompleto() throws Exception {
        ActualizadorService.Info i = ActualizadorService.leer(cuerpoUpdate);
        assertEquals("1.5", i.version());
        assertEquals("aoe2radar-1.5.jar", i.jar());
        assertEquals(jar15.length, i.size());
        assertEquals("21.0.9", i.runtime());
        assertEquals(List.of("flatlaf-3.7.2.jar"), i.classpath());
        assertEquals(MAIN, i.mainclass());
    }

    @Test void updateJsonConObligatoriosQueFaltanOBasuraNoVale() throws Exception {
        String sha = sha(jar15);
        assertNull(ActualizadorService.leer("basura"));
        assertNull(ActualizadorService.leer("[1,2]"));
        assertNull(ActualizadorService.leer(""));
        assertNull(ActualizadorService.leer("{\"jar\":\"aoe2radar-1.5.jar\",\"sha256\":\"" + sha + "\",\"size\":10}"), "sin version");
        assertNull(ActualizadorService.leer("{\"version\":\"1.5\",\"sha256\":\"" + sha + "\",\"size\":10}"), "sin jar");
        assertNull(ActualizadorService.leer("{\"version\":\"1.5\",\"jar\":\"aoe2radar-1.5.jar\",\"size\":10}"), "sin sha");
        assertNull(ActualizadorService.leer("{\"version\":\"1.5\",\"jar\":\"aoe2radar-1.5.jar\",\"sha256\":\"" + sha + "\"}"), "sin size");
        assertNull(ActualizadorService.leer("{\"version\":\"1.5\",\"jar\":\"aoe2radar-1.5.jar\",\"sha256\":\"" + sha + "\",\"size\":-3}"));
        assertNull(ActualizadorService.leer("{\"version\":\"1.5\",\"jar\":\"aoe2radar-1.5.jar\",\"sha256\":\"" + sha + "\",\"size\":1.5}"));
        assertNull(ActualizadorService.leer("{\"version\":\"1.5\",\"jar\":\"aoe2radar-1.5.jar\",\"sha256\":\"" + sha + "\",\"size\":\"10\"}"));
        assertNull(ActualizadorService.leer("{\"version\":\"1.5\",\"jar\":\"..\\\\..\\\\x.jar\",\"sha256\":\"" + sha + "\",\"size\":10}"), "nada de rutas");
        assertNull(ActualizadorService.leer("{\"version\":\"v1.5\",\"jar\":\"aoe2radar-1.5.jar\",\"sha256\":\"" + sha + "\",\"size\":10}"));
        assertNull(ActualizadorService.leer("{\"version\":\"1.99999999999\",\"jar\":\"aoe2radar-1.99999999999.jar\",\"sha256\":\"" + sha + "\",\"size\":10}"),
                "tramos de más de cuatro cifras: versionMayor no podría compararlos");
        assertNull(ActualizadorService.leer("{\"version\":\"1.5-rc1\",\"jar\":\"aoe2radar-1.5-rc1.jar\",\"sha256\":\"" + sha + "\",\"size\":10}"),
                "sin sufijos: 1.5-rc1 se compararía como 1.51");
        assertNull(ActualizadorService.leer("{\"version\":\"1.5\",\"jar\":\"aoe2radar-9.9.jar\",\"sha256\":\"" + sha + "\",\"size\":10}"),
                "el jar tiene que ser el de esa versión");
        ActualizadorService.Info i = ActualizadorService.leer("{\"version\":\"1.5\",\"jar\":\"aoe2radar-1.5.jar\",\"sha256\":\"" + sha + "\",\"size\":10}");
        assertNotNull(i, "los opcionales pueden faltar");
        assertNull(i.runtime());
        assertNull(i.classpath());
        assertNull(ActualizadorService.leer("{\"version\":\"1.5\",\"jar\":\"aoe2radar-1.5.jar\",\"sha256\":\"" + sha + "\",\"size\":10,\"classpath\":[1]}").classpath(),
                "un classpath que no es de textos cuenta como que falta");
    }

    // ------------------------------------------------------------------ decisión jar / completa

    @Test void decision() throws Exception {
        CfgLanzador.Datos cfg = CfgLanzador.leer(CFG);
        ActualizadorService.Info i = ActualizadorService.leer(cuerpoUpdate);
        assertEquals(ActualizadorService.Tipo.JAR, ActualizadorService.decidir(i, "21.0.9", cfg, null));
        assertEquals(ActualizadorService.Tipo.COMPLETA, ActualizadorService.decidir(i, "21.0.10", cfg, null), "otro runtime");
        assertEquals(ActualizadorService.Tipo.COMPLETA, ActualizadorService.decidir(i, "21.0.9", cfg, "1.5"), "esa versión ya falló como jar");
        assertEquals(ActualizadorService.Tipo.COMPLETA, ActualizadorService.decidir(i, "21.0.9", null, null), "sin .cfg legible");
        String otraDep = cuerpoUpdate.replace("flatlaf-3.7.2.jar", "flatlaf-3.8.jar");
        assertEquals(ActualizadorService.Tipo.COMPLETA, ActualizadorService.decidir(ActualizadorService.leer(otraDep), "21.0.9", cfg, null), "dependencia nueva");
        String otraOpcion = cuerpoUpdate.replace("[\"-Dfile.encoding=UTF-8\"]", "[\"-Dfile.encoding=UTF-8\",\"-Xmx1g\"]");
        assertEquals(ActualizadorService.Tipo.COMPLETA, ActualizadorService.decidir(ActualizadorService.leer(otraOpcion), "21.0.9", cfg, null), "otra opción de Java");
        String otraMain = cuerpoUpdate.replace(MAIN, "dev.tirador.aoe2radar.app.Main");
        assertEquals(ActualizadorService.Tipo.COMPLETA, ActualizadorService.decidir(ActualizadorService.leer(otraMain), "21.0.9", cfg, null), "otra clase principal");
        String sinRuntime = "{\"version\":\"1.5\",\"jar\":\"aoe2radar-1.5.jar\",\"sha256\":\"" + sha(jar15) + "\",\"size\":10}";
        assertEquals(ActualizadorService.Tipo.COMPLETA, ActualizadorService.decidir(ActualizadorService.leer(sinRuntime), "21.0.9", cfg, null), "sin datos del paquete: instalador");
    }

    // ------------------------------------------------------------------ comprobar

    @Test void alDiaYSinDatos() throws Exception {
        cuerpoUpdate = updateJson("1.4", jar15, "21.0.9");
        assertEquals(Actualizador.Estado.AL_DIA, servicio().comprobar(true).estado());
        cuerpoUpdate = updateJson("1.3", jar15, "21.0.9");
        assertEquals(Actualizador.Estado.AL_DIA, servicio().comprobar(true).estado(), "nunca hacia atrás");
        estadoUpdate = 404;
        assertEquals(Actualizador.Estado.SIN_DATOS, servicio().comprobar(true).estado());
        estadoUpdate = 200;
        cuerpoUpdate = "<html>";
        assertEquals(Actualizador.Estado.SIN_DATOS, servicio().comprobar(true).estado());
        assertEquals(ActualizadorService.URL_UPDATE, pedidas.get(0));
        assertTrue(descargas.urls.isEmpty(), "nada que bajar");
    }

    @Test void completaNoDescargaNada() throws Exception {
        cuerpoUpdate = updateJson("1.5", jar15, "21.0.11");
        Actualizador.Resultado r = servicio().comprobar(true);
        assertEquals(new Actualizador.Resultado(Actualizador.Estado.COMPLETA, "1.5"), r);
        assertTrue(descargas.urls.isEmpty());
    }

    @Test void conAutoApagadoSoloAvisaYDescargaAlPedirlo() throws Exception {
        ActualizadorService s = servicio();
        assertEquals(new Actualizador.Resultado(Actualizador.Estado.DISPONIBLE, "1.5"), s.comprobar(false));
        assertTrue(descargas.urls.isEmpty());
        assertEquals(Actualizador.Estado.LISTA, s.descargar().estado());
        assertEquals(List.of("https://github.com/jguardiola-dev/aoe2radar/releases/download/v1.5/aoe2radar-1.5.jar"), descargas.urls,
                "por el tag de la versión, no por «latest»");
    }

    @Test void conAutoDescargaVerificaYLaDejaLista() throws Exception {
        Actualizador.Resultado r = servicio().comprobar(true);
        assertEquals(new Actualizador.Resultado(Actualizador.Estado.LISTA, "1.5"), r);
        assertArrayEquals(jar15, Files.readAllBytes(dir.resolve("aoe2radar-1.5.jar")));
        Instalacion.Lista l = Instalacion.leerLista(dir);
        assertEquals("1.5", l.version());
        assertEquals(sha(jar15), l.sha256());
        assertTrue(sinTemporales(), "sin .descargando");
        // la siguiente comprobación no la baja otra vez
        assertEquals(Actualizador.Estado.LISTA, servicio().comprobar(true).estado());
        assertEquals(1, descargas.urls.size());
    }

    /** 1.4.1, el primer parche de tres tramos que publica el actualizador: la 1.4 instalada lo ve como nuevo y, con
     *  el mismo runtime y classpath, lo baja como jar suelto del tag v1.4.1; la 1.4.1 ya no ve la 1.4 como nueva. */
    @Test void unaInstalacion14VeLa141ComoNuevaYLaBajaComoJar() throws Exception {
        byte[] jar141 = jarBueno("1.4.1");
        cuerpoUpdate = updateJson("1.4.1", jar141, "21.0.9");
        descargas.bytes = jar141;
        ActualizadorService.Info info = ActualizadorService.leer(cuerpoUpdate);
        assertNotNull(info, "update.json con version 1.4.1 debe valer");
        assertEquals("aoe2radar-1.4.1.jar", info.jar());
        assertEquals(ActualizadorService.Tipo.JAR, ActualizadorService.decidir(info, "21.0.9", CfgLanzador.leer(CFG), null), "mismo paquete: solo el jar");

        assertEquals(new Actualizador.Resultado(Actualizador.Estado.LISTA, "1.4.1"), servicio().comprobar(true));
        assertEquals(List.of("https://github.com/jguardiola-dev/aoe2radar/releases/download/v1.4.1/aoe2radar-1.4.1.jar"), descargas.urls);
        assertEquals("1.4.1", Instalacion.leerLista(dir).version());

        String json141 = updateJson("1.4.1", jar141, "21.0.9"), json14 = updateJson("1.4", jar141, "21.0.9");
        ActualizadorService ya141 = new ActualizadorService(
                url -> new Transporte.Respuesta(200, json141), descargas, () -> rutas, "1.4.1", "21.0.9");
        assertEquals(Actualizador.Estado.AL_DIA, ya141.comprobar(false).estado(), "la misma versión no es nueva");
        ActualizadorService vieja = new ActualizadorService(
                url -> new Transporte.Respuesta(200, json14), descargas, () -> rutas, "1.4.1", "21.0.9");
        assertEquals(Actualizador.Estado.AL_DIA, vieja.comprobar(false).estado(), "la 1.4 no es nueva para la 1.4.1");
    }

    @Test void unaDescargaMalaNuncaQuedaConElNombreBueno() throws Exception {
        descargas.bytes = jarBueno("1.6");   // mismo tamaño, otro sha
        assertEquals(Actualizador.Estado.FALLO_DESCARGA, servicio().comprobar(true).estado());
        assertFalse(Files.exists(dir.resolve("aoe2radar-1.5.jar")));
        assertNull(Instalacion.leerLista(dir));
        assertTrue(sinTemporales());

        descargas.bytes = java.util.Arrays.copyOf(jar15, jar15.length + 10);   // más grande de lo anunciado
        assertEquals(Actualizador.Estado.FALLO_DESCARGA, servicio().comprobar(true).estado());
        descargas.bytes = java.util.Arrays.copyOf(jar15, jar15.length - 10);   // cortada
        assertEquals(Actualizador.Estado.FALLO_DESCARGA, servicio().comprobar(true).estado());
        descargas.bytes = null;
        descargas.fallo = new IOException("sin red");
        assertEquals(Actualizador.Estado.FALLO_DESCARGA, servicio().comprobar(true).estado());
        assertFalse(Files.exists(dir.resolve("aoe2radar-1.5.jar")));
        assertTrue(sinTemporales());

        // sha y tamaño buenos pero no es la app (sin app/Main.class): tampoco
        byte[] otro = jar("x", MAIN.replace('.', '/') + ".class");
        cuerpoUpdate = updateJson("1.5", otro, "21.0.9");
        descargas.fallo = null;
        descargas.bytes = otro;
        assertEquals(Actualizador.Estado.FALLO_DESCARGA, servicio().comprobar(true).estado());
        assertFalse(Files.exists(dir.resolve("aoe2radar-1.5.jar")));

        // y en la siguiente comprobación se reintenta y sale
        cuerpoUpdate = updateJson("1.5", jar15, "21.0.9");
        descargas.bytes = jar15;
        assertEquals(Actualizador.Estado.LISTA, servicio().comprobar(true).estado());
    }

    @Test void unaDescargaQueNoAcabaSeCortaAlPasarDelTamanoAnunciado() throws Exception {
        long[] leidos = new long[1];
        descargas.flujo = new InputStream() {   // 50 MB de ceros (un servidor que no para): no se leen enteros
            @Override public int read() { return leidos[0]++ < 50_000_000 ? 0 : -1; }
            @Override public int read(byte[] b, int off, int len) {
                if (leidos[0] >= 50_000_000) return -1;
                int n = (int) Math.min(len, 50_000_000 - leidos[0]);
                leidos[0] += n;
                return n;
            }
        };
        assertEquals(Actualizador.Estado.FALLO_DESCARGA, servicio().comprobar(true).estado());
        assertTrue(leidos[0] < jar15.length + 256 * 1024, "se corta en cuanto pasa del tamaño: " + leidos[0]);
        assertTrue(sinTemporales());
    }

    @Test void unaSolaDescargaALaVez() throws Exception {
        ActualizadorService s = servicio();
        assertEquals(Actualizador.Estado.DISPONIBLE, s.comprobar(false).estado());
        descargas.dentro = new CountDownLatch(1);
        descargas.soltar = new CountDownLatch(1);
        Actualizador.Resultado[] primera = new Actualizador.Resultado[1];
        Thread t = new Thread(() -> primera[0] = s.descargar());
        t.start();
        assertTrue(descargas.dentro.await(5, TimeUnit.SECONDS));
        assertEquals(Actualizador.Estado.EN_CURSO, s.descargar().estado(), "la segunda no arranca otra");
        descargas.soltar.countDown();
        t.join(5000);
        assertEquals(Actualizador.Estado.LISTA, primera[0].estado());
        assertEquals(1, descargas.urls.size());
    }

    @Test void loQuePideElInstaladorNuncaSeDescarga() throws Exception {
        cuerpoUpdate = updateJson("1.5", jar15, "21.0.11");
        ActualizadorService s = servicio();
        assertEquals(Actualizador.Estado.COMPLETA, s.comprobar(false).estado());
        assertEquals(Actualizador.Estado.SIN_DATOS, s.descargar().estado(), "descargar() tras COMPLETA no baja nada");
        assertTrue(descargas.urls.isEmpty());
        assertNull(Instalacion.leerLista(dir));
        // y una comprobación que pasa de JAR a COMPLETA también olvida la anterior
        cuerpoUpdate = updateJson("1.5", jar15, "21.0.9");
        assertEquals(Actualizador.Estado.DISPONIBLE, s.comprobar(false).estado());
        cuerpoUpdate = updateJson("1.6", jar15, "21.0.11");
        assertEquals(Actualizador.Estado.COMPLETA, s.comprobar(false).estado());
        assertEquals(Actualizador.Estado.SIN_DATOS, s.descargar().estado());
        assertTrue(descargas.urls.isEmpty());
    }

    @Test void laVersionQueYaFallóSeOfreceConInstalador() throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("fallida.properties"), "version=1.5\n");
        assertEquals(Actualizador.Estado.COMPLETA, servicio().comprobar(true).estado());
        assertTrue(descargas.urls.isEmpty());
    }

    @Test void sinInstalacionActivaNoHaceNada() {
        rutas = null;
        ActualizadorService s = servicio();
        assertFalse(s.activo());
        assertEquals(Actualizador.Estado.SIN_DATOS, s.comprobar(true).estado());
        assertFalse(s.aplicarAlCerrar());
        assertNull(s.avisoFallida());
        assertTrue(pedidas.isEmpty(), "ni se consulta: queda el aviso por tags");
    }

    @Test void deComprobarAAplicarAlCerrar() throws Exception {
        ActualizadorService s = servicio();
        assertEquals(Actualizador.Estado.LISTA, s.comprobar(true).estado());
        assertTrue(s.aplicarAlCerrar());
        assertEquals("aoe2radar-1.5.jar", CfgLanzador.leer(app.resolve("aoe2radar.cfg")).jarApp());
        assertTrue(Files.exists(app.resolve("aoe2radar-1.5.jar")));
    }

    private boolean sinTemporales() throws IOException {
        if (!Files.isDirectory(dir)) return true;
        try (Stream<Path> st = Files.list(dir)) {
            return st.noneMatch(p -> p.getFileName().toString().endsWith(Instalacion.SUFIJO_DESCARGA));
        }
    }
}
