package dev.tirador.aoe2radar.api;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * El único camino de la app hacia la red en texto: freno para el companion, 429 y éxitos al Throttle, cancelación.
 * Todo lo que necesita entra por el constructor (Throttle, Transporte, aviso de pausa, «¿Detener en curso?»), así que
 * se prueba sin red ni pantalla. La red ya no toca Swing: la pausa se avisa por alPausar y la UI decide qué pinta.
 */
public final class ApiClient {
    private final Throttle throttle;
    private final Transporte transporte;
    private final LongConsumer alPausar;          // segundos de pausa tras un 429 del companion (la barra de estado)
    private final BooleanSupplier detenida;       // ¿hay un Detener real en curso? (botón Detener)

    public ApiClient(Throttle throttle, Transporte transporte, LongConsumer alPausar, BooleanSupplier detenida) {
        this.throttle = throttle; this.transporte = transporte; this.alPausar = alPausar; this.detenida = detenida;
    }

    /**
     * Antes httpText: GET con freno si es del companion; lanza IOException «HTTP nnn» si no es 2xx. Un 429 del companion
     * se cuenta al freno (pausa global y aviso) aunque aquí no se reintente: en la 1.1 este camino lo dejaba pasar.
     */
    public String texto(String url) throws IOException, InterruptedException {
        Transporte.Respuesta r = pedir(url);
        if (r.estado() / 100 != 2) {
            if (r.estado() == 429 && Freno.aplicaA(url)) registrar429();
            throw new IOException("HTTP " + r.estado());
        }
        return r.cuerpo();
    }

    /** Cancelación, freno y envío; no registra nada (cada método público decide qué cuenta al freno). */
    private Transporte.Respuesta pedir(String url) throws IOException, InterruptedException {
        // Una interrupción residual de un Detener anterior (los hilos del pool se reutilizan) se limpia;
        // solo cuenta si hay un Detener real en curso.
        if (Thread.interrupted() && detenida.getAsBoolean()) throw new InterruptedException("detenido");
        if (Freno.aplicaA(url)) throttle.adquirir(detenida);   // cualquier host del companion; Detener corta la espera del freno
        return transporte.get(url);
    }

    /** Como texto() pero sin contar nada al freno: lo usa textoCon429, que registra a su manera (sin contar dos veces). */
    private String textoSinRegistrar(String url) throws IOException, InterruptedException {
        Transporte.Respuesta r = pedir(url);
        if (r.estado() / 100 != 2) throw new IOException("HTTP " + r.estado());
        return r.cuerpo();
    }

    /** Antes httpText429: como texto(), con hasta dos reintentos si el servidor limita (HTTP 429). */
    public String textoCon429(String url) throws IOException, InterruptedException {
        boolean companion = Freno.aplicaA(url);   // solo el companion cuenta para el freno: un 429 o un éxito de otro host (Steam…) no lo toca
        try {
            String r = textoSinRegistrar(url);
            if (companion) throttle.registrarExito();   // si hacía más de 10 min del último éxito contado, se olvida la escalada
            return r;
        } catch (Exception ex) {
            if (String.valueOf(ex.getMessage()).contains("429")) {
                if (companion) registrar429();   // pausa global; el propio freno la respeta en todas las llamadas
                try { return textoSinRegistrar(url); }
                catch (Exception ex2) {
                    if (!String.valueOf(ex2.getMessage()).contains("429")) throw ex2;
                    if (companion) registrar429();
                    return textoSinRegistrar(url);
                }
            }
            if (ex instanceof IOException io) throw io;
            if (ex instanceof InterruptedException ie) throw ie;
            throw new IOException(causa(ex));
        }
    }

    /** Un 429 del companion: pausa global en el Throttle, línea en el log y aviso a quien escuche (la UI). */
    private void registrar429() {
        long pausa = throttle.registrar429();   // escalada y pausa global viven en el Throttle
        long seg = (pausa + 999) / 1000;   // hacia arriba: «0 s» con la pausa aún en curso confundiría
        log("API: 429 recibido: pausa global de " + seg + " s para no insistir");
        alPausar.accept(seg);
    }
}
