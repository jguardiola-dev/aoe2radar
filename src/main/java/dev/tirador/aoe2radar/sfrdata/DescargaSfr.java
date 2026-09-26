package dev.tirador.aoe2radar.sfrdata;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static dev.tirador.aoe2radar.api.Http.HTTP;
import static dev.tirador.aoe2radar.api.Http.UA;
import static dev.tirador.aoe2radar.api.Http.descargarBytes;

/**
 * La red de sfr-data (GitHub), separada para poder probar SfrDataClient sin red. sfr-data no es el companion: no
 * pasa por el freno ni por ApiClient.
 */
public interface DescargaSfr {
    /** Respuesta de un GET condicional: estado, cuerpo y ETag (null si no viene). */
    record Respuesta(int estado, byte[] cuerpo, String etag) { }

    /** Un GET, un solo intento; con If-None-Match si etag no es null (304 = sin cambios). */
    Respuesta condicional(String url, String etag, int timeoutS) throws IOException, InterruptedException;

    /** Descarga con reintentos (tres, con más margen cada vez; un 404 no se reintenta). */
    byte[] bytes(String url, int timeoutS) throws IOException, InterruptedException;

    /** La de la app: el HttpClient compartido y Http.descargarBytes. */
    static DescargaSfr http() {
        return new DescargaSfr() {
            @Override public Respuesta condicional(String url, String etag, int timeoutS) throws IOException, InterruptedException {
                HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutS)).header("User-Agent", UA).GET();
                if (etag != null) rb.header("If-None-Match", etag);
                HttpResponse<byte[]> r = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
                return new Respuesta(r.statusCode(), r.body(), r.headers().firstValue("etag").orElse(null));
            }
            @Override public byte[] bytes(String url, int timeoutS) throws IOException, InterruptedException {
                return descargarBytes(url, timeoutS);
            }
        };
    }
}
