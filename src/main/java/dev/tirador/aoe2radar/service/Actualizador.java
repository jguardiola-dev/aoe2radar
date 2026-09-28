package dev.tirador.aoe2radar.service;

/**
 * El actualizador propio de la app instalada (1.4), visto desde la interfaz: comprobar, descargar y aplicar al
 * cerrar. Implementación real: {@link ActualizadorService}; la ui depende solo de esto (y los tests, de uno falso).
 * <p>Hilos: {@link #activo}, {@link #comprobar} y {@link #descargar} tocan el disco o la red: nunca en el EDT.
 * {@link #aplicarAlCerrar} toca el disco y lo llama el cierre de la ventana (en un hilo aparte, esperándolo).
 */
public interface Actualizador {

    /** Qué hay. AL_DIA: nada nuevo. SIN_DATOS: no se pudo consultar (red, update.json roto). DISPONIBLE: hay una
     *  versión que se puede poner cambiando solo el jar, sin descargar aún (ajuste automático apagado). LISTA:
     *  descargada y verificada, se aplicará al cerrar. COMPLETA: hay una versión que necesita el instalador (otro
     *  runtime, otras dependencias, o la actualización por jar ya falló una vez). FALLO_DESCARGA: la descarga no
     *  salió (se reintenta en la siguiente comprobación). EN_CURSO: ya hay una descarga en marcha. */
    enum Estado { AL_DIA, SIN_DATOS, DISPONIBLE, LISTA, COMPLETA, FALLO_DESCARGA, EN_CURSO }

    /** El estado y la versión de la que habla (null en AL_DIA/SIN_DATOS). */
    record Resultado(Estado estado, String version) { }

    /** ¿La app está instalada y puede cambiar su propio jar? (paquete con .cfg reconocible y carpeta escribible).
     *  Si no, la ui sigue con el aviso de siempre por tags de GitHub. Se calcula una vez. */
    boolean activo();

    /** Consulta update.json y decide. Con auto, si hay versión por jar la descarga ya (y devuelve LISTA si sale). */
    Resultado comprobar(boolean auto);

    /** Descarga la última versión por jar conocida (tras un comprobar que dio DISPONIBLE o FALLO_DESCARGA). */
    Resultado descargar();

    /** Al cerrar la app: aplica lo descargado (copia el jar a app/ y cambia el .cfg). Devuelve si quedó aplicado
     *  (o ya lo estaba) para la próxima vez que se abra. Nunca lanza. */
    boolean aplicarAlCerrar();

    /** {versión que falló, versión a la que se volvió} si una actualización no arrancó y aún no se ha avisado; y la
     *  marca como avisada. Null si no hay nada que contar. */
    String[] avisoFallida();
}
