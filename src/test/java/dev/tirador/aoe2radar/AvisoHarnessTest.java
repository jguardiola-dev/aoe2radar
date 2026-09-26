package dev.tirador.aoe2radar;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

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
    @Test void laSalidaDeTasklistConLogonUiLoDetectaYSinElNo() {
        assertTrue(AvisoHarness.salidaTasklistTieneLogonUi("LogonUI.exe                  12345 Console                    1     52.140 KB"));
        assertFalse(AvisoHarness.salidaTasklistTieneLogonUi("INFO: No tasks are running which match the specified criteria."));
        assertFalse(AvisoHarness.salidaTasklistTieneLogonUi(null));
    }

    @AfterEach void restaurarRespaldoDePitido() {
        AvisoHarness.forzarFalloAudioParaTest = false;
        AvisoHarness.respaldoBeepParaTest = () -> java.awt.Toolkit.getDefaultToolkit().beep();
    }

    /** Fila 144 de DEUDA: empezar() puede llegar desde un @BeforeAll, ANTES de arrancar la app; el respaldo
     *  Toolkit.beep() inicializaría AWT ahí, algo que el harness prohíbe. Se fuerza el fallo de audio (sin tocar
     *  hardware de verdad) y se sustituye el respaldo por un espía: sin conRespaldo (como empezar()) no debe
     *  llamarlo nunca; con conRespaldo (como terminar()) sí. */
    @Test void sinRespaldoNuncaLlamaAlPitidoDelSistemaConRespaldoSiempre() throws Exception {
        AtomicBoolean llamado = new AtomicBoolean(false);
        AvisoHarness.respaldoBeepParaTest = () -> llamado.set(true);
        AvisoHarness.forzarFalloAudioParaTest = true;

        AvisoHarness.tono(440, 10, false);
        assertFalse(llamado.get(), "empezar() no debe llamar a Toolkit.beep(): inicializaría AWT antes de la app");

        AvisoHarness.tono(440, 10, true);
        assertTrue(llamado.get(), "terminar() sí puede usar el respaldo: la app ya arrancó");
    }
}
