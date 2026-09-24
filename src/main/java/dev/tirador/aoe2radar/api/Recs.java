package dev.tirador.aoe2radar.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static dev.tirador.aoe2radar.api.Http.HTTP;
import static dev.tirador.aoe2radar.api.Http.req;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/** Descarga de recs de aoe.ms y normalización del cuerpo (gzip, zip, zlib o rec tal cual). */
public final class Recs {
    private Recs() {}

    public static final String REC_URL = "https://aoe.ms/replay/?gameId=%d&profileId=%d";

    /** Descarga la rec de una POV y devuelve los bytes listos para escribir
     *  (ya descomprimidos si el servidor los sirvió en gzip/zlib), o null si no
     *  hay rec válida. Cada intento queda registrado en descargas.log. */
    public static byte[] descargarRec(long gameId, long profileId) {
        String url = String.format(REC_URL, gameId, profileId);
        try {
            HttpResponse<byte[]> r = HTTP.send(req(url), HttpResponse.BodyHandlers.ofByteArray());
            byte[] b = r.body();
            String base = "match=" + gameId + " pov=" + profileId + " HTTP " + r.statusCode()
                    + " | CT=" + r.headers().firstValue("content-type").orElse("-")
                    + " CE=" + r.headers().firstValue("content-encoding").orElse("-")
                    + " CL=" + r.headers().firstValue("content-length").orElse("-")
                    + " | recibidos=" + (b == null ? 0 : b.length) + " B magia=" + magia(b)
                    + " | URL final: " + r.uri();
            if (r.statusCode() / 100 != 2) { log(base + " -> descartada (HTTP)"); return null; }
            RecNormalizada rn = normalizar(b);
            log(base + " -> " + rn.nota());
            return rn.datos();
        } catch (Exception ex) {
            log("match=" + gameId + " pov=" + profileId + " ERROR: " + causa(ex) + " | URL: " + url);
            return null;
        }
    }

    /** Resultado de normalizar un cuerpo descargado: datos == null si hay que
     *  descartar; nota explica siempre la decisión (va al log). */
    public record RecNormalizada(byte[] datos, String nota) {}

    public static RecNormalizada normalizar(byte[] b) {
        if (b == null || b.length == 0) return new RecNormalizada(null, "cuerpo vacío, descartada");
        if (esGzip(b)) {
            try {
                byte[] plano = gunzip(b);
                return esRecValida(plano)
                        ? new RecNormalizada(plano, "gzip descomprimida " + b.length + " -> " + plano.length + " B, guardada")
                        : new RecNormalizada(null, "gzip descomprimida pero el contenido no parece una rec, descartada");
            } catch (IOException ex) {
                return new RecNormalizada(null, "gzip corrupta o truncada (" + causa(ex) + "), descartada");
            }
        }
        if (esZip(b)) {
            try {
                byte[] plano = extraerZip(b);
                if (plano != null && esRecValida(plano))
                    return new RecNormalizada(plano, "zip extraída " + b.length + " -> " + plano.length + " B, guardada");
                if (plano != null)
                    return new RecNormalizada(null, "zip extraída pero el contenido no parece una rec, descartada");
                // Zip sin entradas de archivo: se sigue con el resto de comprobaciones.
            } catch (IOException ignored) {
                // «PK» casual en una rec normal: se sigue con los bytes tal cual.
            }
        }
        if (esZlib(b)) {
            try {
                byte[] plano = inflar(b);
                return esRecValida(plano)
                        ? new RecNormalizada(plano, "zlib descomprimida " + b.length + " -> " + plano.length + " B, guardada")
                        : new RecNormalizada(null, "zlib descomprimida pero el contenido no parece una rec, descartada");
            } catch (IOException ignored) {
                // Cabecera zlib casual en una rec normal: se sigue con los bytes tal cual.
            }
        }
        return esRecValida(b)
                ? new RecNormalizada(b, "guardada tal cual (" + b.length + " B)")
                : new RecNormalizada(null, "no parece una rec (HTML/JSON o < 5 KB), descartada");
    }

    public static boolean esGzip(byte[] b) {
        return b.length >= 2 && (b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B;
    }

    /** Cabecera ZIP: 50 4B («PK»). aoe.ms sirve las recs así. */
    public static boolean esZip(byte[] b) {
        return b.length >= 4 && (b[0] & 0xFF) == 0x50 && (b[1] & 0xFF) == 0x4B;
    }

    /** Extrae del ZIP la entrada que parece la rec: la que termina en
     *  .aoe2record o, si no hay ninguna, la de mayor tamaño.
     *  Devuelve null si el zip no tiene entradas de archivo. */
    public static byte[] extraerZip(byte[] b) throws IOException {
        byte[] mejor = null;
        boolean mejorEsRec = false;
        try (var zin = new ZipInputStream(new ByteArrayInputStream(b))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                byte[] datos = zin.readAllBytes();
                boolean esRec = e.getName().toLowerCase().endsWith(".aoe2record");
                if (mejor == null || (esRec && !mejorEsRec)
                        || (esRec == mejorEsRec && datos.length > mejor.length)) {
                    mejor = datos;
                    mejorEsRec = esRec;
                }
            }
        }
        return mejor;
    }

    /** Cabecera zlib válida según RFC 1950: CM = 8 y checksum de cabecera múltiplo de 31. */
    public static boolean esZlib(byte[] b) {
        if (b.length < 2) return false;
        int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF;
        return (b0 & 0x0F) == 8 && ((b0 << 8) + b1) % 31 == 0;
    }

    public static String magia(byte[] b) {
        if (b == null || b.length < 2) return "-";
        return String.format("%02X %02X", b[0] & 0xFF, b[1] & 0xFF);
    }

    public static byte[] gunzip(byte[] b) throws IOException {
        try (var in = new GZIPInputStream(new ByteArrayInputStream(b))) { return in.readAllBytes(); }
    }

    public static byte[] inflar(byte[] b) throws IOException {
        try (var in = new InflaterInputStream(new ByteArrayInputStream(b))) { return in.readAllBytes(); }
    }

    public static boolean esRecValida(byte[] datos) {
        if (datos == null || datos.length < 5000) return false;
        char c0 = (char) datos[0];
        return c0 != '<' && c0 != '{';   // páginas de error HTML/JSON
    }
}
