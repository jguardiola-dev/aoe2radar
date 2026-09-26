package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.LadderHist;
import dev.tirador.aoe2radar.model.LadderRow;
import dev.tirador.aoe2radar.model.Rejilla;

import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * Lo que la vista Ratings necesita de los resúmenes del ladder (campanas, dispersión y clanes de sfr-data), sin
 * leer directamente los campos estáticos de sfrdata.Ladder. Hoy la vista los lee por import static; este contrato
 * existe para que, cuando salga a ui/RatingsView + RatingsPresenter, no tenga que importar sfrdata. Ver
 * RatingsServiceSfr para la implementación (delega en sfrdata.Ladder) y ConsultasLadder para los percentiles
 * (BIN_LADDER, percentilRango, percentilRating), que ya viven en service y no se envuelven aquí.
 * <p>Hilos: asegurar() puede ir a la red y no se debe llamar en el EDT (ver util.Hilos); hoy la app ya la
 * llama siempre desde un hilo aparte (new Thread(...).start()), nunca en el EDT.
 */
public interface RatingsService {

    /**
     * Carga (o refresca) campanas, dispersión y clanes desde sfr-data. null si va bien; si no, el motivo (para
     * pintarlo en la barra de estado). Ver sfrdata.Ladder.ladderAsegurar. Puede ir a la red.
     */
    String asegurar(boolean forzar);

    /** ¿Hay una carga en curso? El volátil ladderCargando de sfrdata.Ladder, leído tal cual (sin compareAndSet). */
    boolean cargando();

    /** Marca (o desmarca) que hay una carga en curso. Misma escritura simple que hoy hace la vista. */
    void cargando(boolean v);

    /** El texto de progreso de la carga en curso ("Descargando los resúmenes del ladder…", etc.). Sin red. */
    String progreso();

    /** La campana elegida: activos si se pide y existe; si no, todos. Ver sfrdata.Ladder.hist(lb, activos). Sin red. */
    LadderHist hist(String lb, boolean activos);

    /** ¿La campana «lb» tiene datos de activos publicados? (para decidir si el título dice «activos» o «jugadores»). Sin red. */
    boolean tieneActivos(String lb);

    /** La rejilla de dispersión de la familia («rm»/«ew»): activos si se pide y existe; si no, todos. Sin red. */
    Rejilla dispersion(String familia, boolean activos);

    /** ¿La familia «familia» tiene dispersión de activos publicada? (mismo papel que tieneActivos, para el título). Sin red. */
    boolean dispersionTieneActivos(String familia);

    /** Los clanes del ladder 1v1 (tag → miembros), en el orden que publica sfr-data (por rating), ya cargados por asegurar(). Sin red. */
    Map<String, List<LadderRow>> clanes();

    /** Mínimo de partidas para contar como «activo» (definido por sfr-data en ladder.json, activos_def; por defecto 10). Sin red. */
    int activosMinPartidas();

    /** Días para contar como «activo» (definido por sfr-data en ladder.json, activos_def; por defecto 28). Sin red. */
    int activosDias();

    /**
     * El criterio de «activo» dicho en una frase, para tooltips: con min_partidas 1 (lo que publica sfr-data hoy) no
     * tiene sentido «1 o más partidas y una en los últimos 28 días». Sin mayúscula ni punto final. Pura.
     */
    static String criterioActivos(int minPartidas, int dias) {
        return minPartidas <= 1
                ? t("al menos una partida en ese ladder en los últimos ", "at least one game on that ladder in the last ") + dias + t(" días", " days")
                : minPartidas + t(" o más partidas en ese ladder y una en los últimos ", " games or more on that ladder and one in the last ") + dias + t(" días", " days");
    }

    /** Fecha (ISO) de generación de los resúmenes ya cargados, o "" si aún no se cargaron. Sin red. */
    String generado();
}
