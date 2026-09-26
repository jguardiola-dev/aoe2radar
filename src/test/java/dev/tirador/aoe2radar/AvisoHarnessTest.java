package dev.tirador.aoe2radar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La parte de AvisoHarness.logonUiActivo que no necesita procesos de verdad: esComandoLogonUi mira solo el
 * nombre de archivo del camino de comando, sin importar mayúsculas ni la carpeta. logonUiActivo() en sí (con
 * ProcessHandle.allProcesses()) no se prueba aquí: en la máquina de test nunca hay un LogonUI.exe de verdad, así
 * que lo único comprobable sería "no está" (siempre true), que no dice nada.
 */
class AvisoHarnessTest {

    @Test void reconoceLogonUiPorElNombreDeArchivoSinImportarMayusculas() {
        assertTrue(AvisoHarness.esComandoLogonUi("C:\\Windows\\System32\\LogonUI.exe"));
        assertTrue(AvisoHarness.esComandoLogonUi("C:\\Windows\\System32\\logonui.EXE"));
    }

    @Test void noConfundeOtroProcesoConLogonUi() {
        assertFalse(AvisoHarness.esComandoLogonUi("C:\\Windows\\System32\\notepad.exe"));
        assertFalse(AvisoHarness.esComandoLogonUi("C:\\Windows\\System32\\explorer.exe"));
    }

    @Test void sinComandoNoEsLogonUi() {
        assertFalse(AvisoHarness.esComandoLogonUi(null));
        assertFalse(AvisoHarness.esComandoLogonUi(""));
    }
}
