package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.util.ImportacionDatos;
import org.junit.jupiter.api.Test;

import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fuera del paquete (tests, harness) la oferta de importar no aparece ni escribe nada: si llegara a abrir el
 *  diálogo, este test se quedaría esperando a un clic. */
class ImportarDatosTest {

    @Test void fueraDelPaqueteNoSeOfreceNiSeRecuerda() {
        String antes = leerConfig(ImportacionDatos.CLAVE_OFRECIDA, "ausente");
        ImportarDatos.ofrecerSiToca(null);
        assertEquals(antes, leerConfig(ImportacionDatos.CLAVE_OFRECIDA, "ausente"));
    }
}
