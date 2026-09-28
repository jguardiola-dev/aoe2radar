package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * «Partidas 1v1 al azar por rango de ELO» y «Guess the ELO»: el muestreo (nocturno primero desde sfr-data, luego
 * el ladder y perfiles del companion), la caché de sesión (lo ya leído, para «continuar buscando» sin repetir) y
 * el filtrado de candidatas viven aquí. El diálogo de filtros, el SwingWorker que lanza cada búsqueda y la
 * escritura en la tabla de Partidas se quedan en la ventana: este servicio solo dice QUÉ partidas salen.
 * <p>Una sola instancia por ventana: la caché de sesión (páginas de leaderboard reutilizadas, partidas ya
 * descargadas, perfiles ya consultados con su TTL) vive en la instancia y se reutiliza entre tiradas — ver
 * AzarServiceCompanion.
 * <p>Hilos: ambos métodos de búsqueda van a la red (companion); nunca se deben llamar desde el EDT (avisado por
 * util.Hilos.avisarSiUi, no lo impide: hoy no pasa, y si algún día se llama mal la app sigue funcionando, más
 * lenta, y el log dice dónde).
 */
public interface AzarService {

    /**
     * Partidas 1v1 al azar cuyo ELO medio (o el de cada jugador, según el filtro) cae en [lo, hi], dentro de las
     * últimas «hours» horas (cutoff ya calculado por quien llama), con filtro opcional de mapa y civ. Orden de
     * fuentes, igual que hoy: (1) si hours >= 24, la muestra nocturna de sfr-data (si da 5 o más, cero llamadas al
     * companion); si no, (2) lo ya leído en esta sesión que cumpla los filtros de hoy; (3) el «río» de partidas
     * recientes del ladder, solo si el rango es una porción amplia y sin filtro de civ; (4) perfiles muestreados
     * del tramo del ladder, sin repetir los ya consultados en los últimos 10 minutos. Desde la 1.4, con los datos
     * nocturnos nuevos: si hours >= 48 la muestra de anteayer completa la de ayer, y el tramo (3-4) sale del ladder de
     * anoche (sin llamadas al leaderboard); con datos anteriores, como antes.
     * <p>multAzar multiplica el número de pasadas de muestreo de perfiles (intensidad del diálogo). progreso recibe
     * los mismos textos que antes mostraba directamente el estado de la ventana (vía publish/process del
     * SwingWorker). serial es solo para identificar la operación en los mensajes de log.
     * <p>Si se interrumpe (Detener) durante la búsqueda, devuelve lo encontrado hasta el corte, igual que hoy. El
     * freno es el de la operación de quien llama: debe apuntar su hilo (util.Operaciones.anotarHilo) antes, y este
     * servicio lo mira con api.Cancelacion.detieneEsteHilo (lo mismo en buscarGte).
     */
    List<Match> buscarAleatorias(int lo, int hi, String mapaSel, String civSel, int hours, int multAzar,
                                  Instant cutoff, long serial, Consumer<String> progreso) throws Exception;

    /**
     * «Guess the ELO»: hasta 5 partidas 1v1 recientes de tramos distintos del ladder (élite, medio-alto, medio,
     * medio-bajo y fondo), o de la muestra nocturna si esta ya da alguna sin repetir. cutoff, la misma ventana
     * temporal configurada para «Al azar por ELO».
     */
    List<Match> buscarGte(Instant cutoff, Consumer<String> progreso) throws Exception;

    /**
     * True si la última tirada de buscarAleatorias agotó el tramo de ELO activo en esta sesión (no quedan
     * perfiles nuevos que muestrear con esos filtros). Se consulta después de que buscarAleatorias termine.
     */
    boolean tramoAgotado();

    /**
     * True si la última tirada de buscarAleatorias salió entera de la muestra nocturna (partidas de ayer, sin mirar
     * la ventana de horas). La vista lo avisa en el mensaje de estado (decisión de Jorge, 1.3). Se consulta después
     * de que buscarAleatorias termine.
     */
    boolean deMuestra();

    /**
     * El titular de una partida del azar: con filtro de civ, quien LA JUGÓ (si ambos, el de más ELO entre los que
     * cumplen); sin filtro, el de más ELO de la partida como siempre. Pura: no toca red ni caché de sesión.
     */
    static void ajustarRefAzar(Match m, String civSel) {
        MatchPlayer mejor = null;
        for (MatchPlayer p : m.players) {
            boolean cumple = civSel == null || (p.civ != null && p.civ.equalsIgnoreCase(civSel));
            if (!cumple) continue;
            if (mejor == null || (p.rating != null && (mejor.rating == null || p.rating > mejor.rating))) mejor = p;
        }
        if (mejor == null && !m.players.isEmpty()) mejor = m.players.get(0);
        if (mejor != null) m.refId = mejor.id;
    }
}
