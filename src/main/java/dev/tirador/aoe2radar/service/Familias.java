package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Los vínculos entre cuentas del mismo jugador: el campo {@code vinculo} de Player es la clave común de una
 * familia, el id mínimo de sus miembros. Opera sobre la {@code List<Player>} que recibe (la app conserva la
 * lista como campo, la guarda con savePlayers y repinta con aplicarFiltroGrupo en ese mismo orden de hoy).
 * <p>Sin Swing ni red; NO es seguro entre hilos: la lista debe tocarse desde un solo hilo (desde la tanda 2, ficharVarios también la toca solo en el EDT).
 */
public final class Familias {

    /**
     * Casa un conjunto de cuentas bajo la misma familia (clave = menor id). Devuelve si algo cambió, para que
     * quien llama decida si guarda y repinta.
     */
    public boolean marcarVinculo(List<Player> jugadores, Set<Long> ids) {
        if (ids.size() < 2) return false;
        long clave = ids.stream().mapToLong(Long::longValue).min().orElse(0L);
        boolean cambio = false;
        for (int i = 0; i < jugadores.size(); i++) {
            Player x = jugadores.get(i);
            if (ids.contains(x.id()) && x.vinculo() != clave) {
                jugadores.set(i, new Player(x.id(), x.name(), x.grupo(), clave));
                cambio = true;
            }
        }
        return cambio;
    }

    /**
     * Un vinculo que agrupa a UNA sola cuenta es un fantasma (p. ej. de cuando el companion devolvía al propio
     * jugador como vinculada). Deshace esas familias de tamaño 1. Devuelve si algo cambió.
     */
    public boolean sanearVinculosHuerfanos(List<Player> jugadores) {
        Map<Long, Integer> cuenta = new HashMap<>();
        for (Player p : jugadores)
            if (p.vinculo() != 0) cuenta.merge(p.vinculo(), 1, Integer::sum);
        boolean cambio = false;
        for (int i = 0; i < jugadores.size(); i++) {
            Player p = jugadores.get(i);
            if (p.vinculo() != 0 && cuenta.getOrDefault(p.vinculo(), 0) < 2) {
                jugadores.set(i, new Player(p.id(), p.name(), p.grupo(), 0));
                cambio = true;
            }
        }
        return cambio;
    }

    /**
     * La cuenta hermana con MÁS ELO conocido que la propia, o null. Bebe del vínculo propio (jugadores) y de la
     * familia consultada (ver ProfileService.familia, inyectada aquí ya resuelta) y, para el ELO, de eloWatch.
     */
    public String[] mejorAlt(long pid, List<Player> jugadores, Map<Long, String> familiaConsultada, Map<Long, Integer> eloWatch) {
        Integer propio = eloWatch.get(pid);
        Map<Long, String> cand = new HashMap<>();
        for (Player x : jugadores)
            if (x.id() == pid && x.vinculo() != 0)
                for (Player y : jugadores)
                    if (y.vinculo() == x.vinculo() && y.id() != pid) cand.put(y.id(), y.name());
        if (familiaConsultada != null) cand.putAll(familiaConsultada);
        long mejorId = 0; Integer mejorElo = null; String mejorNombre = null;
        for (Map.Entry<Long, String> e : cand.entrySet()) {
            Integer el = eloWatch.get(e.getKey());
            if (el == null) continue;
            if (mejorElo == null || el > mejorElo) { mejorElo = el; mejorId = e.getKey(); mejorNombre = e.getValue(); }
        }
        if (mejorId == 0 || mejorElo == null) return null;
        if (propio != null && mejorElo <= propio) return null;
        return new String[]{ mejorNombre, String.valueOf(mejorElo), String.valueOf(mejorId) };
    }

    /**
     * El propio jugador más las vinculadas que YA se siguen (seguido.test(id)): la familia candidata a vincular.
     * La app decide qué hacer si el resultado tiene menos de dos cuentas (no hay nada que vincular).
     */
    public Set<Long> vincularExistentes(long pid, List<Perfil.Vinculada> vinculadas, LongPredicate seguido) {
        Set<Long> familia = new HashSet<>();
        familia.add(pid);
        for (Perfil.Vinculada v : vinculadas) if (seguido.test(v.pid())) familia.add(v.pid());
        return familia;
    }
}
