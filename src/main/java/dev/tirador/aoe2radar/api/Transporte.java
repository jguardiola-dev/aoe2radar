package dev.tirador.aoe2radar.api;

import java.io.IOException;

/** Una petición GET y su respuesta en texto. En producción, Http.TRANSPORTE; en los tests, uno falso sin red. */
@FunctionalInterface
public interface Transporte {
    Respuesta get(String url) throws IOException, InterruptedException;

    /** Estado HTTP y cuerpo (UTF-8). */
    record Respuesta(int estado, String cuerpo) { }
}
