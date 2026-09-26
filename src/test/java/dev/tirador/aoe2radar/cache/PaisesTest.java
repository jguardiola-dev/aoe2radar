package dev.tirador.aoe2radar.cache;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paises.PAISES_FILE es una ruta fija en disco (no inyectable): el test respalda y restaura el archivo y el mapa
 * en memoria para no interferir con la caché real del proyecto ni con otros tests.
 */
class PaisesTest {

    @Test void siFallaElGuardadoVuelveAQuedarSucioParaReintentarlo() throws Exception {
        Map<Long, String> paisesPrevios = new HashMap<>(Paises.PAIS_DE);
        boolean suciosPrevio = Paises.paisesSucios;
        byte[] contenidoPrevio = Files.exists(Paises.PAISES_FILE) ? Files.readAllBytes(Paises.PAISES_FILE) : null;
        try {
            Files.deleteIfExists(Paises.PAISES_FILE);
            Files.createDirectories(Paises.PAISES_FILE);   // paises.txt como carpeta: escribirlo como archivo falla
            Paises.PAIS_DE.clear();
            Paises.PAIS_DE.put(999L, "es");
            Paises.paisesSucios = true;

            Paises.guardarPaises();

            assertTrue(Paises.paisesSucios, "el guardado falló: el lote se queda sucio para que el siguiente barrido lo reintente");
        } finally {
            if (Files.isDirectory(Paises.PAISES_FILE))
                Files.walk(Paises.PAISES_FILE).sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) { } });
            else Files.deleteIfExists(Paises.PAISES_FILE);
            if (contenidoPrevio != null) Files.write(Paises.PAISES_FILE, contenidoPrevio);
            Paises.PAIS_DE.clear();
            Paises.PAIS_DE.putAll(paisesPrevios);
            Paises.paisesSucios = suciosPrevio;
        }
    }
}
