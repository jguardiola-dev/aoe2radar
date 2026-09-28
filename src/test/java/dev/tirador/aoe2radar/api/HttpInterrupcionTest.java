package dev.tirador.aoe2radar.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Detener interrumpe el hilo de «Buscar partidas» (util.Operaciones.interrumpir) aunque esté dentro de
 * HttpClient.send: la petición se corta enseguida y el cliente compartido (Http.HTTP) sigue sirviendo para las
 * siguientes. Con un servidor local (sin red de verdad) y latches, sin esperas a ciegas.
 */
class HttpInterrupcionTest {

    @Test void sendInterrumpido_saleEnseguidaYElClienteSigueUsable() throws Exception {
        CountDownLatch dentro = new CountDownLatch(1), soltar = new CountDownLatch(1);
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.ExecutorService hilosServidor = java.util.concurrent.Executors.newCachedThreadPool();
        servidor.setExecutor(hilosServidor);
        servidor.createContext("/lenta", ex -> {
            dentro.countDown();
            try { soltar.await(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            responder(ex, "tarde");
        });
        servidor.createContext("/rapida", ex -> responder(ex, "ok"));
        servidor.start();
        try {
            String base = "http://127.0.0.1:" + servidor.getAddress().getPort();
            HttpClient cliente = Http.nuevoHttp();   // el mismo tipo de cliente que usa la app
            AtomicReference<Throwable> fallo = new AtomicReference<>();
            Thread hilo = new Thread(() -> {
                try {
                    cliente.send(HttpRequest.newBuilder(URI.create(base + "/lenta")).build(), HttpResponse.BodyHandlers.ofString());
                } catch (Throwable t) {
                    fallo.set(t);
                }
            });
            hilo.start();
            assertTrue(dentro.await(5, TimeUnit.SECONDS), "la petición llegó al servidor");
            hilo.interrupt();   // lo que hace Operaciones.interrumpir
            hilo.join(5000);
            assertFalse(hilo.isAlive(), "sale sin esperar la respuesta (el servidor tardaría 30 s)");
            assertTrue(fallo.get() instanceof InterruptedException, "sale con InterruptedException: " + fallo.get());
            HttpResponse<String> r = cliente.send(HttpRequest.newBuilder(URI.create(base + "/rapida")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r.statusCode(), "el mismo cliente sigue sirviendo");
            assertEquals("ok", r.body());
        } finally {
            soltar.countDown();
            servidor.stop(0);
            hilosServidor.shutdownNow();
        }
    }

    private static void responder(com.sun.net.httpserver.HttpExchange ex, String cuerpo) throws java.io.IOException {
        byte[] b = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }
}
