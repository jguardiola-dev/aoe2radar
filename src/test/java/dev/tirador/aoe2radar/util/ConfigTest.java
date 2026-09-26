package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Config.CONFIG_FILE es una ruta fija en el directorio de trabajo (no es inyectable, como el resto de "cachés" de
 * un solo archivo de la app): el test respalda y borra lo que toca para no dejar basura en el repo.
 */
class ConfigTest {

    static final Path TMP = Config.CONFIG_FILE.resolveSibling(Config.CONFIG_FILE.getFileName() + ".tmp");

    @AfterEach void limpiar() throws Exception {
        Files.deleteIfExists(Config.CONFIG_FILE);
        Files.deleteIfExists(TMP);
    }

    @Test void guardarYLeerUnaClave() {
        Config.guardarConfig("clave_test_f4", "valor1");
        assertEquals("valor1", Config.leerConfig("clave_test_f4", "def"));
    }

    @Test void leerSinArchivoDaElValorPorDefecto() throws Exception {
        Files.deleteIfExists(Config.CONFIG_FILE);
        assertEquals("def", Config.leerConfig("no_existe_f4", "def"));
    }

    @Test void guardarNoDejaUnTmpBasuraDeUnCorteAnterior() throws Exception {
        Files.writeString(TMP, "basura de un corte a medias");
        Config.guardarConfig("clave_test_f4", "valor2");
        assertFalse(Files.exists(TMP), "la escritura atomica no deja basura de un corte anterior");
        assertEquals("valor2", Config.leerConfig("clave_test_f4", "def"));
    }
}
