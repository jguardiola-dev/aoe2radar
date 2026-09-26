package dev.tirador.aoe2radar;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Avisa a Jorge de que el harness toma la pantalla: pitido grave y cartel rojo al empezar; dos pitidos que suben y
 * cartel verde unos segundos al acabar. Vive DENTRO del harness para que avise lo lance quien lo lance (verificar.ps1,
 * mvn a mano, un subagente). El cartel es tools/aviso.ps1: fuera de la zona fotografiada y sin robar el foco. Si el
 * aviso falla (sin audio, sin pwsh), el harness sigue y lo deja escrito como AVISO.
 */
final class AvisoHarness {
    private AvisoHarness() {}

    private static Process cartelRojo;

    /**
     * Encargo de Opus, revisado: ¿está LogonUI.exe (la pantalla de bloqueo de Windows) entre los procesos? Solo
     * MIRA, con {@link ProcessHandle#allProcesses()} (sin lanzar nada externo); no falla ni decide nada por su
     * cuenta. Es una señal débil a propósito: en Windows este proceso corre como SYSTEM, así que si el harness no
     * tiene permiso para leer su línea de mandato puede no verse; y puede estar activo con la sesión de Jorge
     * DESBLOQUEADA (otra sesión bloqueada en la misma máquina, un RDP en paralelo). Por eso RegresionCapturas la
     * usa solo para enriquecer el mensaje si la primera foto de verdad sale negra, nunca para decidir ella sola
     * que la pantalla está bloqueada (eso lo hacía la versión anterior, y creaba un Robot en @BeforeAll, ANTES de
     * arrancar la app: eso inicializa Java2D y cambia el escalado de iconos, algo que el propio harness prohíbe
     * en su comentario de correr()).
     */
    static boolean logonUiActivo() {
        // ProcessHandle no ve el comando de los procesos de SYSTEM (LogonUI lo es) sin elevar: devuelve vacío.
        // tasklist sí ve el nombre de la imagen. ProcessHandle queda de respaldo (p. ej. si tasklist no existe).
        try {
            Process p = new ProcessBuilder("tasklist", "/NH", "/FI", "IMAGENAME eq LogonUI.exe").redirectErrorStream(true).start();
            String salida = new String(p.getInputStream().readAllBytes(), java.nio.charset.Charset.defaultCharset());
            if (p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && salidaTasklistTieneLogonUi(salida)) return true;
        } catch (Exception ignored) { }
        return ProcessHandle.allProcesses().anyMatch(ph -> esComandoLogonUi(ph.info().command().orElse("")));
    }

    /** ¿La salida de «tasklist /FI "IMAGENAME eq LogonUI.exe"» lista el proceso? (sin él, tasklist dice «INFO: …»). */
    static boolean salidaTasklistTieneLogonUi(String salida) {
        return salida != null && salida.toLowerCase(java.util.Locale.ROOT).contains("logonui.exe");
    }

    /** La parte sin ProcessHandle, para poder probarla sin procesos de verdad: ¿este camino de comando es el de LogonUI.exe? */
    static boolean esComandoLogonUi(String comando) {
        if (comando == null || comando.isEmpty()) return false;
        return Path.of(comando).getFileName().toString().equalsIgnoreCase("LogonUI.exe");
    }

    static void empezar(Path base) {
        tono(440, 180);
        cartelRojo = cartel(base, "NO TOQUES EL RATÓN · harness en marcha", "rojo", 0);
    }

    static void terminar(Path base) {
        if (cartelRojo != null) cartelRojo.destroy();
        tono(523, 120);
        tono(784, 160);
        cartel(base, "YA PUEDES USAR EL RATÓN", "verde", 6);
    }

    private static Process cartel(Path base, String texto, String color, int segundos) {
        List<String> cmd = new ArrayList<>(List.of("pwsh", "-NoProfile", "-File", base.resolve("tools/aviso.ps1").toString(),
                "-Texto", texto, "-Color", color));
        if (segundos > 0) { cmd.add("-Segundos"); cmd.add(String.valueOf(segundos)); }
        try {
            return new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            System.out.println("AVISO: no se pudo mostrar el cartel del harness: " + e.getMessage());
            return null;
        }
    }

    /** Un tono puro de hz durante ms (como [console]::Beep, pero desde Java: no depende de tener consola). */
    private static void tono(int hz, int ms) {
        try {
            AudioFormat f = new AudioFormat(44_100, 8, 1, true, false);
            try (SourceDataLine l = AudioSystem.getSourceDataLine(f)) {
                l.open(f);
                l.start();
                byte[] b = new byte[44_100 * ms / 1000];
                for (int i = 0; i < b.length; i++) b[i] = (byte) (Math.sin(2 * Math.PI * hz * i / 44_100.0) * 90);
                l.write(b, 0, b.length);
                l.drain();
            }
        } catch (Exception e) {
            java.awt.Toolkit.getDefaultToolkit().beep();   // sin tarjeta de sonido utilizable: al menos el pitido del sistema
            System.out.println("AVISO: pitido del harness sin tono (" + e.getMessage() + ")");
        }
    }
}
