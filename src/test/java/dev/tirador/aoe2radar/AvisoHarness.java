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
