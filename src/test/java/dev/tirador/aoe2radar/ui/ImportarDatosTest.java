package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.util.ImportacionDatos;
import org.junit.jupiter.api.Test;

import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fuera del paquete (tests, harness) la oferta de importar no aparece ni escribe nada: si llegara a abrir el
 *  diálogo, este test se quedaría esperando a un clic. */
class ImportarDatosTest {

    @Test void fueraDelPaqueteNoSeOfreceNiSeRecuerda() {
        String antes = leerConfig(ImportacionDatos.CLAVE_OFRECIDA, "ausente");
        ImportarDatos.ofrecerSiToca(null);
        ImportarDatos.alArrancar(null);   // ni aviso ni oferta: vuelve sin lanzar hilos ni diálogos
        assertEquals(antes, leerConfig(ImportacionDatos.CLAVE_OFRECIDA, "ausente"));
    }

    @Test void soloUnaImportacionALaVez() {
        assertTrue(ImportarDatos.reservar());
        try {
            assertFalse(ImportarDatos.reservar(), "con una en marcha no se lanza otra");
        } finally {
            ImportarDatos.liberar();
        }
        assertTrue(ImportarDatos.reservar(), "al terminar, se puede volver a importar");
        ImportarDatos.liberar();
    }
}
