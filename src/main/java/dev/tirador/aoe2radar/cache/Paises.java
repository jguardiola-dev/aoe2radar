package dev.tirador.aoe2radar.cache;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.tirador.aoe2radar.cache.Directorios.LADDER_DIR;
import static dev.tirador.aoe2radar.util.Archivos.escribirAtomico;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/** País de cada jugador, aprendido de la API y guardado en sfrdata/paises.txt. */
public final class Paises {
    private Paises() {}

    public static final Map<Long, String> PAIS_DE = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Path PAISES_FILE = LADDER_DIR.resolve("paises.txt");
    public static volatile boolean paisesSucios;
    /** Un solo guardado a la vez (hallazgo del revisor sobre la fila 25): con cargarPaises/guardarPaises corriendo
     *  en hilos aparte, dos disparos del Timer no deben escribir el archivo al mismo tiempo. Paquete, no privado:
     *  PaisesTest lo usa para forzar el camino de «ya hay uno en marcha». */
    static final AtomicBoolean GUARDANDO = new AtomicBoolean(false);
    /** ¿Terminó la carga inicial de paises.txt? Hasta entonces, guardarAlCerrar no escribe: guardaría un mapa a
     *  medias encima del archivo completo. Paquete: PaisesTest lo fija. */
    static volatile boolean paisesCargados;

    public static void aprenderPais(long pid, Object pais) {
        if (pid <= 0 || pais == null) return;
        String c = String.valueOf(pais).trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty() || "null".equals(c) || c.length() > 6) return;
        if (!c.equals(PAIS_DE.put(pid, c))) paisesSucios = true;
    }
    public static String paisDe(long pid) { return PAIS_DE.get(pid); }

    public static void cargarPaises() {
        try {
            if (!Files.exists(PAISES_FILE)) return;
            for (String linea : Files.readAllLines(PAISES_FILE, StandardCharsets.UTF_8)) {
                int i = linea.indexOf('=');
                if (i <= 0) continue;
                try { PAIS_DE.put(Long.parseLong(linea.substring(0, i).trim()), linea.substring(i + 1).trim()); } catch (NumberFormatException ignored) { }
            }
        } catch (Exception ex) { log("paises: " + causa(ex)); }
        finally { paisesCargados = true; }
    }

    /** Los países aprendidos desde el último guardado de 60 s (arreglo F13 de la revisión 1.3): la ventana lo llama
     *  al cerrarse. Guarda en un hilo aparte y espera como mucho maxMs, para no retener el cierre. Si el guardado
     *  del Timer está en marcha, espera a que acabe y guarda lo que falte. Sin la carga inicial terminada, no hace
     *  nada. */
    public static void guardarAlCerrar(long maxMs) {
        if (!paisesCargados || (!paisesSucios && !GUARDANDO.get())) return;   // si el Timer está escribiendo, se le espera: si no, System.exit lo cortaría
        long fin = System.currentTimeMillis() + maxMs;
        Thread t = new Thread(() -> {
            while (GUARDANDO.get() && System.currentTimeMillis() < fin) {
                try { Thread.sleep(20); } catch (InterruptedException e) { return; }
            }
            guardarPaises();
        }, "paises-cierre");
        t.setDaemon(true);
        t.start();
        try { t.join(Math.max(1, fin - System.currentTimeMillis())); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static void guardarPaises() {
        if (!paisesSucios) return;
        if (!GUARDANDO.compareAndSet(false, true)) return;   // ya hay un guardado en marcha: este disparo se salta
        try {
            paisesSucios = false;
            StringBuilder b = new StringBuilder();
            for (Map.Entry<Long, String> en : PAIS_DE.entrySet()) b.append(en.getKey()).append('=').append(en.getValue()).append('\n');
            // escribirAtomico (temporal + move ATOMIC_MOVE, con respaldo si el move falla): con guardarPaises en
            // un hilo demonio, un EXIT_ON_CLOSE puede cortar el proceso a mitad de escritura; Files.writeString
            // (trunca y escribe) dejaría paises.txt a medias. Ver hallazgo del revisor sobre la fila 25.
            escribirAtomico(PAISES_FILE, b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            paisesSucios = true;   // no se guardó: que el siguiente barrido lo vuelva a intentar (fila 26 de DEUDA)
            log("paises: no se pudo guardar: " + causa(ex));
        } finally {
            GUARDANDO.set(false);
        }
    }
}
