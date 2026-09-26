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

    @Test void leerJsonSanoNoTraeNada() throws Exception {
        Path p = dir.resolve(ARBOL);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "{\"a\":1}");
        Map<String, Object> m = TechTreeDatos.leerJson(dir, ARBOL, rel -> fail("no hacía falta traer " + rel));
        assertEquals(1.0, m.get("a"));
    }
}
