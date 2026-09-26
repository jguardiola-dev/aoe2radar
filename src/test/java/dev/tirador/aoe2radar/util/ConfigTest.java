package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Config.CONFIG_FILE es una ruta fija en el directorio de trabajo (no es inyectable): en los tests ese directorio
 * es target/harness (lo fija el surefire del pom, no la raíz del repo), así que este test no respalda nada de
 * antes: solo borra lo que él mismo crea (config.properties y los ".tmp" que pudiera dejar un test roto).
 */
class ConfigTest {

    static final Path DIR = Config.CONFIG_FILE.toAbsolutePath().getParent();

    /** true si no queda ningún "config.properties*.tmp" en la carpeta: Files.createTempFile le pone un sufijo
     *  numérico único a cada llamada, así que no hay un nombre fijo que comprobar (fila 11 de DEUDA). */
    static boolean noHayNingunTmp() throws Exception {
        try (var listado = Files.list(DIR)) {
            return listado.noneMatch(p -> p.getFileName().toString().startsWith("config.properties") && p.getFileName().toString().endsWith(".tmp"));
        }
    }

    @AfterEach void limpiar() throws Exception {
        Files.deleteIfExists(Config.CONFIG_FILE);
        try (var listado = Files.list(DIR)) {
            listado.filter(p -> p.getFileName().toString().startsWith("config.properties") && p.getFileName().toString().endsWith(".tmp"))
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) { } });
        }
    }

    @Test void guardarYLeerUnaClave() {
        Config.guardarConfig("clave_test_f4", "valor1");
        assertEquals("valor1", Config.leerConfig("clave_test_f4", "def"));
    }

    @Test void leerSinArchivoDaElValorPorDefecto() throws Exception {
        Files.deleteIfExists(Config.CONFIG_FILE);
        assertEquals("def", Config.leerConfig("no_existe_f4", "def"));
    }

    @Test void guardarNoDejaNingunTmp() throws Exception {
        Config.guardarConfig("clave_test_f4", "valor2");
        assertTrue(noHayNingunTmp(), "la escritura atomica no deja su .tmp detrás");
        assertEquals("valor2", Config.leerConfig("clave_test_f4", "def"));
    }

    /**
     * En Windows, un lector con CONFIG_FILE abierto puede hacer fallar el move atómico (AccessDeniedException):
     * Archivos.escribirAtomico cae entonces a escribir directo sobre destino, como en la 1.1, así que el guardado
     * no se pierde. Aquí solo comprobamos el resultado (se guarda y no queda ".tmp"), sea cual sea la rama que
     * tomó: en este entorno el move puede o no fallar con el archivo abierto, y las dos ramas son correctas.
     */
    @Test void siHayUnLectorAbiertoElGuardadoNoSePierdeYNoQuedaTmp() throws Exception {
        Config.guardarConfig("clave_test_f4", "inicial");
        try (var lector = Files.newInputStream(Config.CONFIG_FILE)) {
            Config.guardarConfig("clave_test_f4", "valor_con_lector_abierto");
        }
        assertEquals("valor_con_lector_abierto", Config.leerConfig("clave_test_f4", "def"));
        assertTrue(noHayNingunTmp(), "no debe quedar ningún .tmp, se haya movido o escrito directo");
    }

    @Test void guardarDosClavesSeguidasConservaLasDos() {
        Config.guardarConfig("clave_test_f4", "uno");
        Config.guardarConfig("clave_test_f4_b", "dos");
        assertEquals("uno", Config.leerConfig("clave_test_f4", "def"));
        assertEquals("dos", Config.leerConfig("clave_test_f4_b", "def"));
    }
}
