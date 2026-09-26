package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.util.Reloj;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Cuántas peticiones salen por ApiClient, por host y «plantilla» de endpoint, y cuántas respondieron 429. Sirve para
 * medir (no para frenar: el freno es el Throttle). Una línea resumen al log cada hora y otra al cerrar (volcar()).
 *
 * La plantilla es la ruta sin ids ni valores: los tramos numéricos pasan a {id}, el tramo tras /leaderboards/ a {lb},
 * y de la consulta quedan solo los NOMBRES de los parámetros, ordenados. Así «/matches?page&per_page&profile_ids»
 * (partidas de jugadores) y «/matches?leaderboard_ids&page&per_page» (el río de «Al azar») salen por separado, igual
 * que «/twitch/live?game» (el top) y «/twitch/live?channel» (un canal).
 *
 * Seguro entre hilos sin bloquear: ConcurrentHashMap + LongAdder. El volcado horario lo hace, de paso, la primera
 * petición que llega pasada la hora (ningún hilo propio); lo que se cuente mientras se vuelca va a la hora siguiente.
 */
public final class ContadorLlamadas {
    static final long HORA_MS = 3_600_000L;

    /** Llamadas y 429 de una plantilla. */
    private record Par(LongAdder llamadas, LongAdder c429) {
        Par() { this(new LongAdder(), new LongAdder()); }
    }

    private final Reloj reloj;
    private final Consumer<String> salida;
    private final Map<String, Par> cuentas = new ConcurrentHashMap<>();
    private final AtomicLong desdeMs;        // inicio del tramo que se está contando
    private final AtomicLong proximoMs;      // cuándo toca el siguiente volcado horario

    public ContadorLlamadas(Reloj reloj, Consumer<String> salida) {
        this.reloj = reloj; this.salida = salida;
        long ahora = reloj.ahoraMs();
        this.desdeMs = new AtomicLong(ahora);
        this.proximoMs = new AtomicLong(ahora + HORA_MS);
    }

    /** Una petición que sale hacia url (se cuenta antes de enviarla: si la red falla, la llamada existió igual). */
    public void peticion(String url) {
        volcarSiToca();
        cuentas.computeIfAbsent(clave(url), k -> new Par()).llamadas().increment();
    }

    /** La respuesta de url llegó con este estado HTTP (solo cuenta los 429). */
    public void respuesta(String url, int estado) {
        if (estado == 429) cuentas.computeIfAbsent(clave(url), k -> new Par()).c429().increment();
    }

    /** Si ya pasó la hora, vuelca y abre el tramo siguiente. Solo un hilo gana el CAS: una línea por hora. */
    void volcarSiToca() {
        long ahora = reloj.ahoraMs();
        long toca = proximoMs.get();
        if (ahora >= toca && proximoMs.compareAndSet(toca, ahora + HORA_MS)) volcar();
    }

    /**
     * Escribe la línea resumen del tramo actual (desde el último volcado) y pone los contadores a cero. Para el cierre
     * de la app; si no hubo ninguna llamada, no escribe nada.
     */
    public void volcar() {
        long ahora = reloj.ahoraMs();
        long desde = desdeMs.getAndSet(ahora);
        long total = 0, total429 = 0;
        List<Object[]> filas = new ArrayList<>();
        for (Map.Entry<String, Par> e : cuentas.entrySet()) {
            long n = e.getValue().llamadas().sumThenReset();
            long c = e.getValue().c429().sumThenReset();
            if (n == 0 && c == 0) continue;
            total += n; total429 += c;
            filas.add(new Object[] { e.getKey(), n, c });
        }
        if (filas.isEmpty()) return;
        filas.sort((a, b) -> {
            int porCuenta = Long.compare((Long) b[1], (Long) a[1]);
            return porCuenta != 0 ? porCuenta : ((String) a[0]).compareTo((String) b[0]);
        });
        StringBuilder sb = new StringBuilder("API: llamadas en ").append(Math.max(0, (ahora - desde + 30_000) / 60_000))
                .append(" min: ").append(total).append(" (429: ").append(total429).append(")");
        for (Object[] f : filas) {
            sb.append(" · ").append(f[0]).append(' ').append(f[1]);
            if ((Long) f[2] > 0) sb.append(" (429: ").append(f[2]).append(')');
        }
        salida.accept(sb.toString());
    }

    /** «host plantilla» de una URL (ver la clase). Una URL ilegible cuenta aparte, sin romper la petición. */
    static String clave(String url) {
        try {
            URI u = URI.create(url);
            String host = u.getHost() == null ? "?" : u.getHost().toLowerCase(Locale.ROOT);
            return host + " " + plantilla(u.getRawPath(), u.getRawQuery());
        } catch (IllegalArgumentException ex) {
            return "? (url ilegible)";
        }
    }

    static String plantilla(String ruta, String consulta) {
        StringBuilder sb = new StringBuilder();
        String anterior = "";
        for (String tramo : (ruta == null ? "" : ruta).split("/")) {
            if (tramo.isEmpty()) continue;
            String t = anterior.equals("leaderboards") ? "{lb}" : tramo.chars().allMatch(Character::isDigit) ? "{id}" : tramo;
            sb.append('/').append(t);
            anterior = tramo;
        }
        if (sb.length() == 0) sb.append('/');
        if (consulta != null && !consulta.isEmpty()) {
            TreeSet<String> nombres = new TreeSet<>();
            for (String p : consulta.split("&")) {
                int igual = p.indexOf('=');
                String nombre = igual < 0 ? p : p.substring(0, igual);
                if (!nombre.isEmpty()) nombres.add(nombre);
            }
            if (!nombres.isEmpty()) sb.append('?').append(String.join("&", nombres));
        }
        return sb.toString();
    }
}
