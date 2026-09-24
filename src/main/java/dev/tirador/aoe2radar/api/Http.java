package dev.tirador.aoe2radar.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;
import static dev.tirador.aoe2radar.util.Identidad.REPO_URL;
import static dev.tirador.aoe2radar.util.Identidad.VERSION;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/** Transporte HTTP: cliente, User-Agent y descargas sin freno (GitHub). Las llamadas a la API del companion (httpText) pasan por Freno; imágenes del CDN y socket no. */
public final class Http {
    private Http() {}

    public static final String API     = "https://data.aoe2companion.com/api";
    public static final String UA      = NOMBRE + "/" + VERSION + " (+" + REPO_URL + "; twitch.tv/12tirador)";

    public static volatile HttpClient HTTP = nuevoHttp();

    /** Cliente HTTP/1.1 (una conexión rota no arrastra a las demás) con timeout de
     *  conexión; se RENUEVA tras cada Detener para descartar conexiones a medias. */
    public static HttpClient nuevoHttp() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public static HttpRequest req(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .timeout(Duration.ofSeconds(15))
                .GET().build();
    }

    public static byte[] descargarBytes(String url, int timeoutS) throws IOException, InterruptedException {
        IOException ultimo = null;
        for (int intento = 1; intento <= 3; intento++) {   // con VPN (días de fútbol en España) GitHub va lento: tres intentos con más margen cada vez
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutS * intento)).header("User-Agent", UA).GET().build();
                HttpResponse<byte[]> r = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() == 404) throw new IOException("HTTP 404 " + url);
                if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " " + url);
                return r.body();
            } catch (IOException ex) {
                if (String.valueOf(ex.getMessage()).contains("404")) throw ex;
                ultimo = ex; log("descarga (" + intento + "/3): " + causa(ex) + " · " + url.substring(url.lastIndexOf('/') + 1));
                Thread.sleep(1500L * intento);
            }
        }
        throw ultimo;
    }

    public static byte[] httpBytesTT(String url) throws Exception {
        HttpResponse<byte[]> r = HTTP.send(req(url), HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode());
        return r.body();
    }
}
