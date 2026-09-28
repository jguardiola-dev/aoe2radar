package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.service.Actualizador;

import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;
import static dev.tirador.aoe2radar.util.Identidad.RELEASES_URL;
import static dev.tirador.aoe2radar.util.Identidad.VERSION;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * El presentador del actualizador propio (1.4): pide a {@link Actualizador} (red y disco, en segundo plano) y le
 * cuenta a {@link FranjaActualizacion} qué enseñar (en el EDT). Nada de diálogos (decisión de Jorge: no intrusivo):
 * <ul>
 * <li>LISTA: «aoe2radar 1.5 está lista: se aplicará al cerrar.» [Reiniciar ahora]</li>
 * <li>COMPLETA: «Hay una versión nueva que necesita reinstalar.» [Descargar instalador] (abre la release)</li>
 * <li>DISPONIBLE (con «Actualizar automáticamente» apagado): «aoe2radar 1.5 está disponible.» [Actualizar]</li>
 * <li>Si una actualización no arrancó y se volvió atrás: «La actualización a 1.5 no pudo arrancar: sigues en la 1.4.»</li>
 * </ul>
 * La comprobación manual (Configuración → Buscar actualizaciones…) cuenta además el resultado en la barra de estado.
 * Al cerrar la ventana, {@link #alCerrar} aplica lo descargado y, si se pidió «Reiniciar ahora», vuelve a abrir la app.
 */
public final class Actualizaciones {

    /** Clave de config.properties del ajuste «Actualizar automáticamente» (por defecto, sí). */
    public static final String CLAVE_AUTO = "actualizar_auto";
    /** Lo que se espera como mucho a que se aplique al cerrar, para no retener el cierre si el disco se atasca. */
    static final long ESPERA_CIERRE_MS = 20_000;

    /** Lo que necesita de la ventana. */
    public interface Anfitrion {
        /** Abre una URL en el navegador (EDT). */
        void abrirUrl(String url);
        /** Texto en la barra de estado (EDT). */
        void estado(String texto);
        /** Cierra la ventana por su cierre normal (EDT): el que guarda todo y llama a {@link #alCerrar}. */
        void cerrarVentana();
        /** Vuelve a abrir la app (el mismo exe). Devuelve si la lanzó. Fuera del EDT. */
        boolean relanzar();
    }

    private final Actualizador actualizador;
    private final FranjaActualizacion franja;
    private final Anfitrion anfitrion;
    private final Tareas tareas;
    private volatile boolean reiniciarAlCerrar;

    public Actualizaciones(Actualizador actualizador, FranjaActualizacion franja, Anfitrion anfitrion, Tareas tareas) {
        this.actualizador = actualizador;
        this.franja = franja;
        this.anfitrion = anfitrion;
        this.tareas = tareas;
    }

    /** ¿Se encarga el actualizador propio? (si no, sigue el aviso de siempre por tags). Toca el disco la primera vez:
     *  fuera del EDT. */
    public boolean activo() { return actualizador.activo(); }

    public static boolean automatico() { return Boolean.parseBoolean(leerConfig(CLAVE_AUTO, "true")); }

    /** Comprueba y lo pinta. Fuera del EDT (la llama el hilo de «actualizaciones» o el Timer de 12 h vía un hilo). */
    public void comprobar(boolean manual) {
        String[] fallida = actualizador.avisoFallida();
        Actualizador.Resultado r = actualizador.comprobar(automatico());
        if (fallida != null) {
            // Un solo aviso: tras volver atrás, update.json sigue anunciando esa versión y sale COMPLETA; dos mostrar
            // seguidos harían que el segundo tapara al primero sin que se llegara a ver (revisor, 2ª vuelta).
            String a = fallida[1] == null || fallida[1].isBlank() ? VERSION : fallida[1];
            String texto = t("La actualización a " + fallida[0] + " no pudo arrancar: sigues en la " + a + ".",
                    "The update to " + fallida[0] + " could not start: you are still on " + a + ".");
            boolean misma = r.estado() == Actualizador.Estado.COMPLETA && fallida[0].equals(r.version());
            tareas.enUi(() -> {
                if (misma) franja.mostrar(texto, t("Descargar instalador", "Download installer"), () -> anfitrion.abrirUrl(RELEASES_URL));
                else franja.mostrar(texto, null, null);   // lo demás (otra versión) sale en la siguiente comprobación
            });
            return;
        }
        tareas.enUi(() -> pintar(r, manual, false));
    }

    /** EDT. manual: el usuario lo pidió (se cuenta en la barra de estado). desdeBoton: viene de [Actualizar]; si la
     *  descarga falla, el botón vuelve a salir (con la comprobación automática, se reintenta sola más tarde). */
    void pintar(Actualizador.Resultado r, boolean manual, boolean desdeBoton) {
        switch (r.estado()) {
            case LISTA -> franja.mostrar(t(NOMBRE + " " + r.version() + " está lista: se aplicará al cerrar.",
                            NOMBRE + " " + r.version() + " is ready: it will be applied when you close the app."),
                    t("Reiniciar ahora", "Restart now"), this::reiniciarAhora);
            case COMPLETA -> franja.mostrar(t("Hay una versión nueva que necesita reinstalar.",
                            "There is a new version that needs reinstalling."),
                    t("Descargar instalador", "Download installer"), () -> anfitrion.abrirUrl(RELEASES_URL));
            case DISPONIBLE -> franja.mostrar(t(NOMBRE + " " + r.version() + " está disponible.",
                            NOMBRE + " " + r.version() + " is available."),
                    t("Actualizar", "Update"), this::descargarAhora);
            case FALLO_DESCARGA -> {
                if (desdeBoton) franja.mostrar(t(NOMBRE + " " + r.version() + " está disponible.",
                                NOMBRE + " " + r.version() + " is available."),
                        t("Actualizar", "Update"), this::descargarAhora);   // el botón vuelve a estar a mano
                if (manual) anfitrion.estado(t("No se pudo descargar la actualización; se reintentará más tarde.",
                        "Couldn't download the update; it will retry later."));
            }
            case EN_CURSO -> { if (manual) anfitrion.estado(t("Descargando la actualización…", "Downloading the update…")); }
            case AL_DIA -> { if (manual) anfitrion.estado(t("Tienes la última versión (", "You have the latest version (") + VERSION + ")."); }
            case SIN_DATOS -> { if (manual) anfitrion.estado(t("No se pudo comprobar (¿sin red?).", "Couldn't check (no network?).")); }
        }
    }

    /** [Actualizar] (EDT): descarga en segundo plano y vuelve a pintar. */
    void descargarAhora() {
        franja.ocupado(t("Descargando…", "Downloading…"));
        tareas.enFondo("actualizar-descarga", () -> {
            Actualizador.Resultado r = actualizador.descargar();
            tareas.enUi(() -> pintar(r, true, true));
        });
    }

    /** [Reiniciar ahora] (EDT): se cierra por el cierre normal (que guarda todo y aplica) y se vuelve a abrir. */
    void reiniciarAhora() {
        reiniciarAlCerrar = true;
        anfitrion.cerrarVentana();
    }

    /** Lo último del cierre de la ventana (EDT, justo antes de salir): aplica lo descargado en un hilo aparte,
     *  esperándolo como mucho {@link #ESPERA_CIERRE_MS}, y relanza la app si se pidió «Reiniciar ahora». Si no se
     *  llega a tiempo, el .cfg no queda a medias (se escribe de golpe o no se escribe) y se reintenta al siguiente
     *  cierre. Nunca lanza. */
    public void alCerrar() {
        Thread h = new Thread(() -> {
            boolean aplicada = actualizador.aplicarAlCerrar();
            if (reiniciarAlCerrar) {
                boolean ok = anfitrion.relanzar();
                log("actualizar: reiniciar ahora (" + (aplicada ? "aplicada" : "sin aplicar") + ", relanzada: " + ok + ")");
            }
        }, "actualizar-cierre");
        h.start();
        try { h.join(ESPERA_CIERRE_MS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (h.isAlive()) {
            log("actualizar: el cierre no esperó más a aplicar la actualización");
            // «Reiniciar ahora» se cumple igual: el .cfg se escribe de golpe o no se escribe, así que la app que se abre
            // arranca la versión vieja o la nueva, nunca una a medias.
            if (reiniciarAlCerrar) anfitrion.relanzar();
        }
    }
}
