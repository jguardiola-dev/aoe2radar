package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.util.Reloj;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ELO 1v1 recordados en la sesión (el de los menús de jugador y el de las vinculadas), con la regla de Jorge: «la
 * utilidad de ver el ELO es ver el actual». Un ELO guardado vale hasta que el jugador TERMINA una partida (lo sabe
 * EstadoVivo por el socket o los barridos); entonces hay que volver a pedirlo, pero no antes de `espera` desde el
 * final, que es lo que tarda el companion en recalcularlo (si no, se volvería a guardar el de antes). Mientras tanto se
 * sigue mostrando el que hay. Sin partidas terminadas, se comporta como la 1.1: se pide una vez y se recuerda.
 */
public final class EloSesion {
    /**
     * Lo que tarda el companion en tener el ELO nuevo tras una partida. Medido el 2026-09-25 (una partida del top, MbL
     * contra Blve): final 12:29:34; /matches la da por terminada ~2 min después; el ELO del perfil cambia ~4,5 min
     * después. La app puede enterarse del final casi al momento (socket), así que 4,5 min más margen. Una sola medición:
     * ver DEUDA.
     */
    public static final Duration ESPERA = Duration.ofMinutes(6);

    private record Dato(int elo, long ms) { }

    private final EstadoVivo estado;
    private final Reloj reloj;
    private final Duration espera;
    private final Map<Long, Dato> datos = new ConcurrentHashMap<>();
    private final Set<Long> enVuelo = ConcurrentHashMap.newKeySet();

    public EloSesion(EstadoVivo estado, Reloj reloj, Duration espera) {
        this.estado = estado; this.reloj = reloj; this.espera = espera;
    }

    /** El ELO recordado de pid (aunque haya quedado viejo): 0 si se preguntó y no tiene; null si nunca se preguntó. */
    public Integer conocido(long pid) {
        Dato d = datos.get(pid);
        return d == null ? null : d.elo();
    }

    /**
     * ¿El recordado quedó viejo y ya se puede pedir el nuevo? Sí si pid terminó una partida DESPUÉS de pedirlo (más la
     * espera) y la espera desde ese final ya ha pasado. Si nunca se preguntó, false: eso lo decide quien llama, como antes.
     */
    public boolean caducado(long pid) {
        Dato d = datos.get(pid);
        Long fin = estado.finMs(pid);
        if (d == null || fin == null) return false;
        long listo = fin + espera.toMillis();
        return d.ms() < listo && reloj.ahoraMs() >= listo;
    }

    /**
     * Reserva la petición del ELO de pid: false si ya hay una en vuelo (abrir el menú varias veces antes de la respuesta
     * lanzaba una llamada por apertura, ya en la 1.1). Quien reserva debe llamar a apuntar al acabar.
     */
    public boolean reservar(long pid) { return enVuelo.add(pid); }

    /** La hora de ahora, para sellar una petición ANTES de lanzarla (ver apuntar). */
    public long ahora() { return reloj.ahoraMs(); }

    /**
     * Apunta el ELO pedido en pedidoMs (null: no tiene, se guarda 0) y libera la reserva. La hora es la de la PETICIÓN,
     * no la de la respuesta: una petición lenta (freno, 429) que salió antes de que el companion lo recalculara no puede
     * pasar por nueva.
     */
    public void apuntar(long pid, Integer elo, long pedidoMs) {
        datos.put(pid, new Dato(elo == null ? 0 : elo, pedidoMs));
        enVuelo.remove(pid);
    }

    /**
     * La petición reservada no pudo saber el ELO (red, 429…): libera la reserva SIN apuntar nada. Un fallo no es «no
     * tiene»: si se guardara 0, nadie volvería a pedirlo en toda la sesión (revisión 1.3, F11). La próxima vez que
     * haga falta se vuelve a pedir; si ya había uno recordado, se sigue mostrando ese.
     */
    public void liberar(long pid) { enVuelo.remove(pid); }

    /** Como apuntar(pid, elo, pedidoMs) con una petición de ahora mismo (respuestas inmediatas y tests). */
    public void apuntar(long pid, Integer elo) { apuntar(pid, elo, reloj.ahoraMs()); }
}
