package dev.tirador.aoe2radar.techtree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Archivos del tech tree en disco, sin red: escritura atómica y archivos cortados (general F5). */
class TechTreeDatosTest {

    @TempDir Path dir;

    static final String ARBOL = "data/trees/AZTECS.json";

    static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test void descargarSustituyeElArchivoEnteroNoEscribeEncima() throws Exception {
        Path p = dir.resolve(ARBOL);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "{\"viejo\":1}");
        Path espejo = dir.resolve("espejo.json");
        Files.createLink(espejo, p);   // comparte los datos con p: si se escribiera encima, cambiaría también

        TechTreeDatos.descargar(dir, ARBOL, rel -> utf8("{\"nuevo\":2}"));

        assertEquals("{\"nuevo\":2}", Files.readString(p));
        assertEquals("{\"viejo\":1}", Files.readString(espejo), "escritura atómica: se sustituye el archivo, no se escribe encima");
    }

    @Test void descargarCreaLasCarpetas() throws Exception {
        TechTreeDatos.descargar(dir, "img/Unit/7.png", rel -> new byte[]{ 1, 2, 3 });
        assertArrayEquals(new byte[]{ 1, 2, 3 }, Files.readAllBytes(dir.resolve("img/Unit/7.png")));
    }

    @Test void leerJsonSiFaltaLoTraeYLoLee() throws Exception {
        List<String> traidos = new ArrayList<>();
        Map<String, Object> m = TechTreeDatos.leerJson(dir, ARBOL, rel -> { traidos.add(rel); TechTreeDatos.descargar(dir, rel, r -> utf8("{\"a\":1}")); });
        assertEquals(1.0, m.get("a"));
        assertEquals(List.of(ARBOL), traidos);
    }

    @Test void leerJsonDeUnArchivoCortadoLoBorraYLoVuelveATraer() throws Exception {
        Path p = dir.resolve(ARBOL);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "{\"buildings\":[1,2,");   // cortado a mitad (una escritura de antes de la 1.3)
        List<String> traidos = new ArrayList<>();

        Map<String, Object> m = TechTreeDatos.leerJson(dir, ARBOL, rel -> { traidos.add(rel); TechTreeDatos.descargar(dir, rel, r -> utf8("{\"buildings\":[1,2,3]}")); });

        assertEquals(List.of(1.0, 2.0, 3.0), m.get("buildings"));
        assertEquals(List.of(ARBOL), traidos, "una sola vez");
        assertEquals("{\"buildings\":[1,2,3]}", Files.readString(p), "el de disco queda sano");
    }

    @Test void leerJsonSiElNuevoTambienEstaRotoFallaComoAntes() throws IOException {
        Path p = dir.resolve(ARBOL);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "{\"a\":");
        List<String> traidos = new ArrayList<>();
        assertThrows(Exception.class, () -> TechTreeDatos.leerJson(dir, ARBOL, rel -> { traidos.add(rel); Files.writeString(dir.resolve(rel), "{\"a\":"); }));
        assertEquals(1, traidos.size(), "no se reintenta en bucle");
    }

    // ----- General F7: el tech tree del jar, si falta en disco (sin ir a GitHub en un PC limpio)

    static final String DATA_JAR = "{\"civs\":{\"Aztecs\":{}}}";

    /** Un jar de mentira con data.json, un árbol y un icono; lo demás no lo trae (null). */
    static final TechTreeDatos.Fuente JAR = rel -> switch (rel) {
        case "data/data.json" -> utf8(DATA_JAR);
        case ARBOL -> utf8("{\"del\":\"jar\"}");
        case "img/Unit/7.png" -> new byte[]{ 7 };
        default -> null;
    };

    /** La red: apunta lo que se le pide y devuelve otra cosa, para distinguir de dónde salió cada archivo. */
    final List<String> aLaRed = new ArrayList<>();
    final TechTreeDatos.Fuente red = rel -> { aLaRed.add(rel); return utf8("{\"de\":\"red\"}"); };

    @Test void pcLimpioSacaDelJarDataJsonArbolesEIconosSinIrALaRed() throws Exception {
        assertTrue(TechTreeDatos.descargar(dir, "data/data.json", JAR, red));
        assertTrue(TechTreeDatos.descargar(dir, ARBOL, JAR, red));
        assertTrue(TechTreeDatos.descargar(dir, "img/Unit/7.png", JAR, red));
        assertEquals(List.of(), aLaRed, "lo que trae el jar no se baja de GitHub");
        assertEquals(DATA_JAR, Files.readString(dir.resolve("data/data.json")));
        assertEquals("{\"del\":\"jar\"}", Files.readString(dir.resolve(ARBOL)));
        assertArrayEquals(new byte[]{ 7 }, Files.readAllBytes(dir.resolve("img/Unit/7.png")));
    }

    @Test void loQueElJarNoTraeSeBajaDeLaRed() throws Exception {
        assertFalse(TechTreeDatos.descargar(dir, "data/trees/NUEVA.json", JAR, red));
        assertEquals(List.of("data/trees/NUEVA.json"), aLaRed);
    }

    @Test void conElMismoDataJsonQueElJarLosArbolesSalenDelJar() throws Exception {
        Files.createDirectories(dir.resolve("data"));
        Files.writeString(dir.resolve("data/data.json"), DATA_JAR);
        assertTrue(TechTreeDatos.descargar(dir, ARBOL, JAR, red));
        assertEquals(List.of(), aLaRed);
    }

    @Test void siLaActualizacionRenovoDataJsonLosArbolesVanALaRedYLosIconosNo() throws Exception {
        Files.createDirectories(dir.resolve("data"));
        Files.writeString(dir.resolve("data/data.json"), "{\"civs\":{\"Aztecs\":{},\"Nueva\":{}}}");   // el de aoe2techtree, más nuevo
        assertFalse(TechTreeDatos.descargar(dir, ARBOL, JAR, red), "el árbol del jar es de otra versión de data.json");
        assertEquals("{\"de\":\"red\"}", Files.readString(dir.resolve(ARBOL)));
        assertTrue(TechTreeDatos.descargar(dir, "img/Unit/7.png", JAR, red), "los iconos van por id: la actualización ya los conservaba");
        assertEquals(List.of(ARBOL), aLaRed);
    }

    @Test void laComparacionSeRehaceSiCambiaElDataJsonDeDisco() throws Exception {
        Files.createDirectories(dir.resolve("data"));
        Path dj = dir.resolve("data/data.json");
        Files.writeString(dj, DATA_JAR);
        assertTrue(TechTreeDatos.jarVale(dir, ARBOL, JAR));
        Files.writeString(dj, "{\"civs\":{\"Otra\":{}}}");   // lo que haría ttComprobarActualizacion
        Files.setLastModifiedTime(dj, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        assertFalse(TechTreeDatos.jarVale(dir, ARBOL, JAR));
    }

    @Test void elJarDeVerdadTraeElTechTreeDeSrcMainResources() throws Exception {
        byte[] dj = TechTreeDatos.JAR.bytes("data/data.json");
        assertNotNull(dj, "src/main/resources/techtree va en el classpath (y en el jar)");
        assertTrue(dj.length > 100_000);
        assertNotNull(TechTreeDatos.JAR.bytes("data/locales/es/strings.json"));
        assertNotNull(TechTreeDatos.JAR.bytes("data/locales/en/strings.json"));
        assertNotNull(TechTreeDatos.JAR.bytes("data/trees/AZTECS.json"));
        assertNotNull(TechTreeDatos.JAR.bytes("img/Civs/aztecs.png"));
        assertNull(TechTreeDatos.JAR.bytes("img/Unit/no-existe.png"));
    }

    @Test void leerJsonSanoNoTraeNada() throws Exception {
        Path p = dir.resolve(ARBOL);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "{\"a\":1}");
        Map<String, Object> m = TechTreeDatos.leerJson(dir, ARBOL, rel -> fail("no hacía falta traer " + rel));
        assertEquals(1.0, m.get("a"));
    }
}
