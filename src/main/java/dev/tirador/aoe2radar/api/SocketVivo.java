package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.util.Reloj;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static dev.tirador.aoe2radar.api.Parseo.parseMatch;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.Json.lng;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Json.val;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * El websocket «ongoing-matches» del companion: se abre con los ids vigilados y el servidor empuja matchAdded /
 * matchUpdated / matchRemoved. Aquí vive solo el PROTOCOLO: conectar, recomponer los mensajes troceados, traducirlos
 * a eventos con tipo, el ping (cada 30 s), la salud y la reconexión (5, 10, 20, 40, 80, 120 s). Qué significa cada
 * evento (quién está en partida, fantasmas, avisos) lo decide el Oyente, fuera. Nada de Swing: el Oyente se llama en
 * el hilo del socket y el ping y la reconexión en el del Planificador.
 */
public final class SocketVivo {
    public static final String URL = "wss://socket.aoe2companion.com/listen?handler=ongoing-matches";

    // ----- eventos -----
    /** Un evento del socket ya traducido. */
    public sealed interface Evento permits Partida, Quitada { }
    /** matchAdded o matchUpdated (tipo tal cual, para el log) con la partida leída. */
    public record Partida(String tipo, Match partida) implements Evento { }
    /** matchRemoved: la partida con ese id ya no está en curso. */
    public record Quitada(long matchId) implements Evento { }

    /** Quien da significado a los eventos (en la app, la ventana). Se llama en el hilo del socket. */
    public interface Oyente {
        /** Recién conectado; trasCaida: la conexión anterior se cayó (conviene un barrido para reparar el estado). */
        void conectado(boolean trasCaida);
        /** Un mensaje completo, ya traducido (vacío si era un pong u otro objeto), con los ids de esa conexión. */
        void eventos(List<Evento> eventos, Set<Long> ids);
    }

    // ----- transporte (en la app, java.net.http.WebSocket; en los tests, un doble) -----
    /** Una conexión abierta. */
    public interface Canal {
        void ping();
        void cerrar(String motivo);
    }
    /** Lo que el transporte avisa. Tras abierto y tras cada texto, el transporte pide el siguiente mensaje. */
    public interface Receptor {
        void abierto(Canal c);
        void texto(CharSequence trozo, boolean ultimo);
        void cerrado(Canal c, int codigo, String motivo);
        void error(Throwable t);
    }
    public interface Conector {
        CompletableFuture<Canal> conectar(String url, Receptor r);
    }
    /** Tareas diferidas (reconexión) y periódicas (ping). */
    public interface Planificador {
        /** true mientras la tarea no se haya ejecutado. */
        interface Tarea { boolean pendiente(); }
        Tarea despues(long ms, Runnable r);
        void cada(long ms, Runnable r);
    }

    /** El transporte de verdad: el cliente HTTP de la app, con su User-Agent y 15 s para conectar. */
    public static final Conector HTTP = (url, r) -> {
        EscuchaHttp escucha = new EscuchaHttp(r);
        return Http.HTTP.newWebSocketBuilder().header("User-Agent", Http.UA).connectTimeout(Duration.ofSeconds(15))
                .buildAsync(URI.create(url), escucha).thenApply(escucha::canal);
    };

    /** Un hilo de fondo para el ping y la reconexión (sustituye a los javax.swing.Timer de la 1.1). */
    public static Planificador planificadorSistema() {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> { Thread h = new Thread(r, "socket-vivo"); h.setDaemon(true); return h; });
        return new Planificador() {
            @Override public Tarea despues(long ms, Runnable r) { ScheduledFuture<?> f = ex.schedule(r, ms, TimeUnit.MILLISECONDS); return () -> !f.isDone(); }
            @Override public void cada(long ms, Runnable r) { ex.scheduleAtFixedRate(r, ms, ms, TimeUnit.MILLISECONDS); }
        };
    }

    private final Conector conector;
    private final Oyente oyente;
    private final Reloj reloj;
    private final Planificador planificador;

    // Estado de la conexión. Las decisiones («¿este intento es el vigente?» y lo que se escribe según la respuesta) van
    // juntas bajo `candado`, para que ningún hilo se cuele entre comprobar y actuar. Bajo el candado NUNCA se llama al
    // Oyente, a la red ni a programarReconexion (que usa el monitor de this): así no hay interbloqueos.
    private final Object candado = new Object();
    private long intento;                    // número del intento vigente; cerrar o abrir otro deja viejos a los anteriores
    private volatile Canal canal;
    private volatile Set<Long> ids = Set.of();
    private boolean huboCaida;
    private volatile long ultimoMsgMs;       // último mensaje recibido (salud)
    private volatile boolean conectado;
    private volatile int reintentos;
    private final StringBuilder buffer = new StringBuilder();   // los mensajes pueden llegar troceados
    private Planificador.Tarea reconexion;   // bajo el monitor de this
    private long aReconectar;                // bajo el monitor de this: el último intento que falló (al que sirve la reconexión)

    public SocketVivo(Conector conector, Oyente oyente, Reloj reloj, Planificador planificador) {
        this.conector = conector; this.oyente = oyente; this.reloj = reloj; this.planificador = planificador;
    }

    public boolean conectado() { return conectado; }

    /** Conectado y con algún mensaje hace menos de 10 min. */
    public boolean sano() { return conectado && reloj.ahoraMs() - ultimoMsgMs < 10 * 60_000; }

    /** Arranca el ping cada 30 s (mantiene viva la conexión mientras no llegan eventos). */
    public void iniciarPing() {
        planificador.cada(30_000, () -> {
            Canal c = canal;
            if (c != null && conectado) { try { c.ping(); } catch (Exception ignored) { } }
        });
    }

    /** Conecta (o reconecta) con estos ids; vacío: cierra (y ninguna reconexión vuelve a abrir); si no cambian y está conectado, no hace nada. */
    public void sincronizar(Set<Long> nuevos) {
        synchronized (candado) {
            if (!nuevos.isEmpty() && nuevos.equals(ids) && conectado) return;
            ids = nuevos.isEmpty() ? Set.of() : nuevos;
        }
        cerrar();
        if (!nuevos.isEmpty()) abrir(nuevos);
    }

    /** Cierra a propósito: el intento vigente pasa a viejo, así que su cierre no cuenta como caída ni reconecta. */
    public void cerrar() {
        Canal c;
        synchronized (candado) {
            intento++;
            c = canal;
            canal = null;
            conectado = false;
        }
        if (c != null) { try { c.cerrar("bye"); } catch (Exception ignored) { } }
    }

    /** Abre ya con estos ids (el intento nuevo pasa a ser el vigente). */
    void abrir(Set<Long> idsEsta) { abrir(idsEsta, -1); }

    /**
     * Abre un intento nuevo. idsEsta null: los ids actuales. soloSiIntento >= 0: solo si el vigente sigue siendo ese (la
     * reconexión: si entretanto alguien abrió o cerró, ya no le toca). La reserva del número y la lectura de los ids
     * van juntas bajo el candado.
     */
    private void abrir(Set<Long> idsEsta, long soloSiIntento) {
        long mio;
        Set<Long> idsAbrir;
        synchronized (candado) {
            if (soloSiIntento >= 0 && intento != soloSiIntento) return;
            idsAbrir = idsEsta != null ? idsEsta : ids;
            if (idsAbrir.isEmpty()) return;
            mio = ++intento;
        }
        StringBuilder csv = new StringBuilder();
        for (long id : idsAbrir) { if (csv.length() > 0) csv.append(','); csv.append(id); }
        String url = URL + "&language=" + ("es".equals(IDIOMA) ? "es" : "en") + "&profile_ids=" + csv;
        conector.conectar(url, new Receptor() {
            @Override public void abierto(Canal c) {
                boolean trasCaida;
                synchronized (candado) {
                    if (mio != intento) return;   // intento viejo: su whenComplete lo cierra
                    conectado = true;
                    trasCaida = huboCaida;
                    huboCaida = false;
                    reintentos = 0;
                    ultimoMsgMs = reloj.ahoraMs();
                }
                synchronized (buffer) { buffer.setLength(0); }   // un trozo de la conexión anterior no se pega al primer mensaje de esta
                oyente.conectado(trasCaida);   // tras una caída, la app hace un barrido para reparar el estado
                log("socket: conectado, vigilando " + idsAbrir.size() + " jugadores en tiempo real");
            }
            @Override public void texto(CharSequence trozo, boolean ultimo) {
                synchronized (buffer) {
                    // sin trozos de una conexión que ya no cuenta: la comprobación va DENTRO del buffer (orden buffer →
                    // candado, el mismo que cuando el Oyente llama a sincronizar), así ningún trozo viejo se añade
                    // después del vaciado que hace el abierto de la conexión nueva
                    synchronized (candado) { if (mio != intento) return; }
                    buffer.append(trozo);
                    if (ultimo) {
                        String msg = buffer.toString();
                        buffer.setLength(0);
                        ultimoMsgMs = reloj.ahoraMs();
                        try { oyente.eventos(leer(msg), idsAbrir); }
                        catch (Exception ex) { log("socket: mensaje no entendido: " + causa(ex)); }
                    }
                }
            }
            @Override public void cerrado(Canal c, int codigo, String motivo) {
                if (!caida()) return;   // un cierre viejo o pedido por nosotros (cerrar) no es una caída
                log("socket: cerrado (" + codigo + " " + motivo + ")");
                programarReconexion(mio);
            }
            @Override public void error(Throwable error) {
                if (!caida()) return;   // un error también es caída (en la 1.1, solo el cierre): al reconectar, la app repara
                log("socket: error: " + causa(error instanceof Exception ex ? ex : new RuntimeException(error)));
                programarReconexion(mio);
            }
            /** Si este es el intento vigente, lo apunta como caído y devuelve true. */
            private boolean caida() {
                synchronized (candado) {
                    if (mio != intento) return false;
                    conectado = false;
                    huboCaida = true;
                    return true;
                }
            }
        }).whenComplete((c, err) -> {
            boolean reprogramar = false;
            Canal fuera = null;
            synchronized (candado) {
                boolean vigente = mio == intento;
                if (err != null) { if (vigente) { conectado = false; reprogramar = true; } }
                else if (vigente) canal = c;
                else fuera = c;   // otro intento más nuevo manda
            }
            if (err != null) log("socket: no se pudo conectar: " + causa(new RuntimeException(err)));
            if (reprogramar) programarReconexion(mio);
            if (fuera != null) { try { fuera.cerrar("stale"); } catch (Exception ignored) { } }
        });
    }

    /**
     * Una sola reconexión pendiente a la vez; la espera se dobla con cada intento: 5, 10, 20, 40, 80 y 120 s. Sirve
     * siempre al intento fallido MÁS NUEVO (si ya hay una pendiente, se le cambia el intento; si no, una tarea vieja
     * ocuparía el sitio y al dispararse no haría nada: socket muerto). Al dispararse, solo abre si el vigente sigue
     * siendo ese: si entretanto alguien reconectó, abrió otro o cerró a propósito, no hace nada.
     */
    private synchronized void programarReconexion(long intentoFallido) {
        aReconectar = Math.max(aReconectar, intentoFallido);   // el número solo crece: un fallo viejo que llega tarde no pisa al nuevo
        if (reconexion != null && reconexion.pendiente()) return;
        int seg = Math.min(120, 5 * (1 << Math.min(reintentos++, 5)));
        reconexion = planificador.despues(seg * 1000L, () -> {
            // al empezar deja de estar pendiente (como el javax.swing.Timer de la 1.1, que al disparar ya no «corría»): si
            // abrir falla en este mismo hilo, su whenComplete puede programar la siguiente; si no, el socket moriría aquí
            long n;
            synchronized (this) { reconexion = null; n = aReconectar; }
            try { abrir(null, n); }
            catch (RuntimeException ex) { log("socket: no se pudo reconectar: " + causa(ex)); }
        });
    }

    /** Un mensaje: {type:'pong'} (u otro objeto) → ninguno; o una lista [{type, data}]. Las partidas ilegibles se saltan. */
    static List<Evento> leer(String msg) {
        Object root = dev.tirador.aoe2radar.util.Json.parse(msg);
        if (!(root instanceof List<?> lista)) return List.of();   // pong u otro objeto: nada que hacer
        List<Evento> out = new ArrayList<>();
        for (Object ev : lista) {
            Map<String, Object> e = obj(ev);
            String tipo = String.valueOf(val(e, "type"));
            Map<String, Object> data = obj(val(e, "data"));
            if ("matchRemoved".equals(tipo)) { out.add(new Quitada(lng(val(data, "match_id", "matchId")))); continue; }
            if (!"matchAdded".equals(tipo) && !"matchUpdated".equals(tipo)) continue;
            Match m = parseMatch(data);
            if (m != null) out.add(new Partida(tipo, m));
        }
        return out;
    }

    /** El Listener de java.net.http: traduce al Receptor y pide el siguiente mensaje tras cada uno. */
    private static final class EscuchaHttp implements WebSocket.Listener {
        private final Receptor r;
        private Canal canal;
        EscuchaHttp(Receptor r) { this.r = r; }

        synchronized Canal canal(WebSocket ws) {
            if (canal == null) canal = new Canal() {
                @Override public void ping() { ws.sendPing(ByteBuffer.wrap(new byte[]{ 1 })); }
                @Override public void cerrar(String motivo) { ws.sendClose(WebSocket.NORMAL_CLOSURE, motivo); }
            };
            return canal;
        }
        @Override public void onOpen(WebSocket ws) { r.abierto(canal(ws)); ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) { r.texto(data, last); ws.request(1); return null; }
        @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) { r.cerrado(canal(ws), code, reason); return null; }
        @Override public void onError(WebSocket ws, Throwable error) { r.error(error); }
    }
}
