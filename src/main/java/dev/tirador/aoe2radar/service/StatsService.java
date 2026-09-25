package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.CivAgg;
import dev.tirador.aoe2radar.model.Tendencias;
import dev.tirador.aoe2radar.model.VentanaStats;

import java.util.List;
import java.util.Map;

/**
 * Civ Stats: las ventanas de winrate/pick rate de sfr-data (7/30/90/365 días y «parche») y sus cálculos. La
 * ventana y las tendencias ya cargadas quedan en sfrdata.CivStats (VENTANAS_STATS, tendenciasStats): son
 * estado estático compartido con la UI (que las lee directamente en varios sitios) y con el harness de
 * capturas, así que este servicio no las duplica ni las traslada; solo decide CUÁNDO se bajan (ver DEUDA).
 * <p>Hilos: asegurar() puede ir a la red y no se debe llamar en el EDT (ver util.Hilos); hoy la app ya la
 * llama siempre desde un hilo aparte (new Thread(...).start()), nunca en el EDT.
 */
public interface StatsService {

    /**
     * Carga (con caché en memoria, para toda la sesión) la ventana pedida ("7", "30", "90", "365", "parche").
     * null si va bien; si no, el motivo (para pintarlo en la barra de estado). Puede ir a la red.
     */
    String asegurar(String ventana, boolean conTendencias);

    /** La ventana ya cargada por asegurar(), o null si aún no se pidió o falló. Sin red. */
    VentanaStats ventana(String clave);

    /** ¿Ya está cargada la ventana «clave»? Para no repetir una descarga en curso o decidir si hace falta pedirla. Sin red. */
    boolean tieneVentana(String clave);

    /** Los modos de Civ Stats («rm_1v1», «rm_2v2»…), en el orden fijo de la app. Mismo array en cada llamada: no se copia. */
    String[] modos();

    /** Las claves de ventana disponibles («7», «30», «90», «365», «parche»), en el orden fijo de la app. Mismo array en cada llamada: no se copia. */
    String[] clavesVentanas();

    /** Las tendencias mensuales ya cargadas por asegurar(.., true), o null si aún no. Sin red. */
    Tendencias tendencias();

    /**
     * ¿La fila de tramo «tramo» entra en el rango elegido? Acepta «*», una clave simple o «desde|hasta».
     * Ver CalculoStats.tramoEnRango. Cálculo puro, sin red.
     */
    boolean tramoEnRango(String tramo, List<String> tramos, String rango);

    /** Intervalo de Wilson al 95 % en porcentaje: {inferior, superior}. Ver CalculoStats.wilson. Sin red. */
    double[] wilson(int w, int n);

    /** Suma por civ con los filtros («*» = todos). Ver CalculoStats.agregarCivs. Sin red. */
    Map<String, CivAgg> agregarCivs(VentanaStats v, String modo, String mapa, String tramo);

    /** Partidas por mapa en el modo elegido. Ver CalculoStats.partidasPorMapa. Sin red. */
    Map<String, Integer> partidasPorMapa(VentanaStats v, String modo, String tramo);

    /** Winrate de una civ por mapa (para la ficha del tech tree). Ver CalculoStats.civPorMapa. Sin red. */
    Map<String, CivAgg> civPorMapa(VentanaStats v, String modo, String tramo, String civ);

    /** Duración media legible ("m:ss min"), o "-" sin partidas. Ver CalculoStats.duracionMedia. Sin red. */
    String duracionMedia(long segundos, int n);
}
