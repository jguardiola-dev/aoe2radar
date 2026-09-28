package dev.tirador.aoe2radar.ui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.tirador.aoe2radar.service.ImagenesJuego.claveMapa;
import static dev.tirador.aoe2radar.service.ImagenesJuego.pedirMapa;
import static dev.tirador.aoe2radar.service.ImagenesJuego.bytesBandera;
import static dev.tirador.aoe2radar.service.ImagenesJuego.urlBandera;
import static dev.tirador.aoe2radar.service.ImagenesJuego.rutaIconoCiv;
import static dev.tirador.aoe2radar.service.ImagenesJuego.leerIcono;
import static dev.tirador.aoe2radar.service.ImagenesJuego.rutaMapa;

/**
 * Iconos de bandera, civ y mapa ya escalados a ImageIcon, con su caché en memoria. Las vistas los pintan sin
 * saber dónde vive cada png ni cómo se descarga una miniatura de mapa: eso lo resuelve service.ImagenesJuego.
 */
public final class Iconos {
    private Iconos() { }

    private static final Map<String, ImageIcon> ICONOS_BANDERA = new ConcurrentHashMap<>();
    private static final Map<String, ImageIcon> ICONOS_CIV = new ConcurrentHashMap<>();
    private static final Map<String, ImageIcon> ICONOS_MAPA = new ConcurrentHashMap<>();

    /** Bandera del país (código ISO de dos letras), o null si no hay archivo. */
    public static ImageIcon iconoBandera(String cc) {
        if (cc == null) return null;
        String k = cc.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty() || "null".equals(k)) return null;
        ImageIcon ic = ICONOS_BANDERA.get(k);
        if (ic != null) return ic;
        byte[] png = bytesBandera(k);
        if (png == null) return null;
        ic = new ImageIcon(png);
        if (ic.getIconWidth() <= 0) return null;
        ICONOS_BANDERA.put(k, ic);
        return ic;
    }

    /** Bandera escalada a una altura dada (para Live now en 1v1). */
    public static ImageIcon iconoBandera(String cc, int alto) {
        ImageIcon base = iconoBandera(cc);
        if (base == null || alto <= 15) return base;
        String k = cc.trim().toLowerCase(Locale.ROOT) + "@" + alto;
        ImageIcon ic = ICONOS_BANDERA.get(k);
        if (ic != null) return ic;
        ic = new ImageIcon(base.getImage().getScaledInstance(alto * 4 / 3, alto, Image.SCALE_SMOOTH));
        ICONOS_BANDERA.put(k, ic);
        return ic;
    }

    /** La misma bandera dentro de un texto HTML (para el renderer de la watchlist). */
    public static String banderaHtml(String cc) {
        if (cc == null) return "";
        String k = cc.trim().toLowerCase(Locale.ROOT);
        String url = urlBandera(k);
        if (url == null) return "";
        return "<img src='" + url + "' width='16' height='12'>&nbsp;";
    }

    /** Icono de civ (techtree/img/Civs/<clave>.png) escalado a px; null si aún no está en disco. La clave del companion y la del tech tree coinciden en minúsculas. */
    public static ImageIcon iconoCiv(String clave, int px) {
        if (clave == null || clave.isBlank()) return null;
        String k = clave.toLowerCase(Locale.ROOT);
        String kk = k + "@" + px;
        ImageIcon ic = ICONOS_CIV.get(kk);
        if (ic != null) return ic;
        Path p = rutaIconoCiv(k);
        if (!Files.exists(p)) return null;
        try {
            BufferedImage img = leerIcono(p);   // vacío o truncado: lo borra y la precarga del tech tree lo vuelve a traer (1.4)
            if (img == null) return null;
            ic = new ImageIcon(img.getScaledInstance(px, px, Image.SCALE_SMOOTH));
            ICONOS_CIV.put(kk, ic);
            return ic;
        } catch (Exception ex) { return null; }
    }

    /** Icono de mapa si conocemos su imagen (la API la da con cada partida); se descarga una vez a sfrdata/mapas. null si no hay. */
    public static ImageIcon iconoMapa(String nombre, int px) { return iconoMapa(nombre, null, px); }

    /** Igual, probando primero la clave del volcado («rm_arabia») y luego el nombre («Arabia»). */
    public static ImageIcon iconoMapa(String nombre, String clave, int px) {
        if ((nombre == null || nombre.isBlank()) && (clave == null || clave.isBlank())) return null;
        String kc = clave == null ? null : clave.toLowerCase(Locale.ROOT).trim();
        String kn = nombre == null ? null : nombre.toLowerCase(Locale.ROOT).trim();
        String k = claveMapa(kc, kn);
        String kk = k + "@" + px;
        ImageIcon ic = ICONOS_MAPA.get(kk);
        if (ic != null) return ic;
        Path p = rutaMapa(k);
        if (Files.exists(p)) {
            try { BufferedImage img = ImageIO.read(p.toFile()); if (img != null) { ic = new ImageIcon(img.getScaledInstance(px, px, Image.SCALE_SMOOTH)); ICONOS_MAPA.put(kk, ic); return ic; } } catch (Exception ignored) { }
            return null;
        }
        pedirMapa(k, kc, p);
        return null;
    }
}
