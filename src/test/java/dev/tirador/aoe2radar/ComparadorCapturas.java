package dev.tirador.aoe2radar;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Compara una captura con su referencia, con tolerancia.
 * - Sin referencia: la guarda y pasa (primera ejecución, o -Dcapturas.regrabar=true para un cambio intencionado).
 * - Un píxel es «distinto» si algún canal RGB difiere más de TOLERANCIA_CANAL (antialiasing, ClearType).
 * - La captura falla si los píxeles distintos superan el umbral (fracción del total).
 * - Si falla, deja en la carpeta de salida la captura nueva y una imagen de diferencias (referencia atenuada + rojo).
 */
final class ComparadorCapturas {
    static final int TOLERANCIA_CANAL = 24;
    // 0,01 % ≈ 140 px en 1486x943. Medido en esta máquina: el ruido entre ejecuciones llega a ~40 px (cursor
    // parpadeando), y un cambio de una palabra en un botón son ~800 px. Con 0,5 % ese cambio pasaba sin avisar.
    static final double UMBRAL_DEFECTO = 0.0001;

    record Resultado(String nombre, boolean ok, double fraccion, String detalle) { }

    private final Path referencias, salida;
    private final boolean regrabar;
    private final Map<String, Double> umbrales;   // umbral por captura cuando el defecto no basta (justificar cada entrada)
    private final Double umbralFijo;              // si no es null, manda sobre todo (diagnóstico)

    ComparadorCapturas(Path referencias, Path salida, boolean regrabar, Map<String, Double> umbrales) {
        this(referencias, salida, regrabar, umbrales, null);
    }

    ComparadorCapturas(Path referencias, Path salida, boolean regrabar, Map<String, Double> umbrales, Double umbralFijo) {
        this.referencias = referencias; this.salida = salida; this.regrabar = regrabar; this.umbrales = umbrales; this.umbralFijo = umbralFijo;
    }

    Resultado comparar(String nombre, BufferedImage actual) throws IOException { return comparar(nombre, actual, List.of()); }

    /** ignorar: zonas con datos en directo que no se pueden congelar (se pintan en azul en el diff y no cuentan). */
    Resultado comparar(String nombre, BufferedImage actual, List<Rectangle> ignorar) throws IOException {
        Path ref = referencias.resolve(nombre + ".png");
        if (regrabar || !Files.exists(ref)) {
            Files.createDirectories(referencias);
            ImageIO.write(actual, "png", ref.toFile());
            return new Resultado(nombre, true, 0, "referencia grabada");
        }
        BufferedImage esperada = ImageIO.read(ref.toFile());
        if (esperada.getWidth() != actual.getWidth() || esperada.getHeight() != actual.getHeight()) {
            guardar(nombre, actual, null);
            return new Resultado(nombre, false, 1, "tamaño distinto: referencia " + esperada.getWidth() + "x" + esperada.getHeight()
                    + ", ahora " + actual.getWidth() + "x" + actual.getHeight());
        }
        BufferedImage diff = new BufferedImage(actual.getWidth(), actual.getHeight(), BufferedImage.TYPE_INT_RGB);
        Conteo c = pixelesDistintos(esperada, actual, diff, ignorar);
        double fraccion = c.comparados() == 0 ? 0 : (double) c.distintos() / c.comparados();   // sobre lo comparado: lo ignorado no afloja el umbral
        double umbral = umbralFijo != null ? umbralFijo : umbrales.getOrDefault(nombre, UMBRAL_DEFECTO);
        String detalle = String.format("%.3f %% distinto (umbral %.3f %%)", fraccion * 100, umbral * 100);
        if (fraccion <= umbral) return new Resultado(nombre, true, fraccion, detalle);
        guardar(nombre, actual, diff);
        return new Resultado(nombre, false, fraccion, detalle + " → " + salida.resolve(nombre + ".diff.png"));
    }

    /** Cuenta los píxeles distintos; si diff no es null, pinta en él la referencia en gris atenuado, los distintos en rojo y lo ignorado en azul. */
    record Conteo(long distintos, long comparados) { }

    static Conteo pixelesDistintos(BufferedImage a, BufferedImage b, BufferedImage diff, List<Rectangle> ignorar) {
        long n = 0, comparados = 0;
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getRGB(x, y), q = b.getRGB(x, y);
                final int px = x, py = y;
                if (ignorar.stream().anyMatch(r -> r.contains(px, py))) {
                    if (diff != null) diff.setRGB(x, y, 0x3050a0);
                    continue;
                }
                comparados++;
                boolean distinto = Math.abs(((p >> 16) & 0xff) - ((q >> 16) & 0xff)) > TOLERANCIA_CANAL
                        || Math.abs(((p >> 8) & 0xff) - ((q >> 8) & 0xff)) > TOLERANCIA_CANAL
                        || Math.abs((p & 0xff) - (q & 0xff)) > TOLERANCIA_CANAL;
                if (distinto) n++;
                if (diff != null) {
                    int gris = 160 + (((p >> 16) & 0xff) + ((p >> 8) & 0xff) + (p & 0xff)) / 3 * 95 / 255;
                    diff.setRGB(x, y, distinto ? 0xff0000 : (gris << 16) | (gris << 8) | gris);
                }
            }
        return new Conteo(n, comparados);
    }

    private void guardar(String nombre, BufferedImage actual, BufferedImage diff) throws IOException {
        Files.createDirectories(salida);
        ImageIO.write(actual, "png", salida.resolve(nombre + ".png").toFile());
        if (diff != null) ImageIO.write(diff, "png", salida.resolve(nombre + ".diff.png").toFile());
    }
}
