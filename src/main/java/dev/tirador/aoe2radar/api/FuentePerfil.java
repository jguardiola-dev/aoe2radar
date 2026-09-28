package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.model.Perfil;

import java.io.IOException;

/**
 * De dónde sale el perfil de un jugador (ficha, ELO por ladder, steamId): lo que ProfileService, la tarjeta del hover y
 * el menú del jugador piden a la red. La implementan {@link CompanionApi} (GET /profiles/{pid}, con caché) y
 * {@link WorldsEdgeApi} (getPersonalStat, sin serie ni vinculadas); ConRespaldo.perfil las junta (1.4). Va a la red:
 * nunca en el EDT.
 */
public interface FuentePerfil {

    /** El perfil de pid (la fuente puede servirlo de su caché). */
    Perfil perfil(long pid) throws IOException, InterruptedException;

    /** El perfil de ahora mismo, sin caché. Por defecto (una fuente sin caché), lo mismo que perfil. */
    default Perfil perfilFresco(long pid) throws IOException, InterruptedException { return perfil(pid); }

    /** Olvida lo guardado de pid (la siguiente lectura va a la red). Sin red. Por defecto, nada (sin caché). */
    default void invalidarPerfil(long pid) { }
}
