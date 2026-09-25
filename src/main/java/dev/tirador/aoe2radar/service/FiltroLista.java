package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.model.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Qué fila de la Watchlist se ve y en qué orden: subconjunto por grupo activo (o los tops),
 * filtro «solo vivos», orden por ELO/forma/nombre y agrupación por familia (vinculo): quién es
 * la cabeza, quién la hija y quién va expandida, con sus marcas P/E/H.
 * <p>Aquí no hay Swing ni red: la ventana (aplicarFiltroGrupo) decide qué hacer con el resultado
 * (reconstruir playersModel, conservar la selección y avisar al socket).
 */
public final class FiltroLista {

    private final EstadoVivo vivo;

    public FiltroLista(EstadoVivo vivo) { this.vivo = vivo; }

    /** filas: el orden final a pintar (cabezas y, si están expandidas, sus hijas justo detrás).
     *  marcaFila: pid -> P(rincipal, colapsada) / E(xpandida) / H(ija); solo familias con >1 cuenta.
     *  vivoFamilia: pid de la cabeza -> si alguna cuenta de la familia está jugando ahora. */
    public record Resultado(List<Player> filas, Map<Long, Character> marcaFila, Map<Long, Boolean> vivoFamilia) { }

    public Resultado filtrar(List<Player> todosJugadores, List<Player> topLadder, boolean modoTop,
                              String grupoActivo, boolean soloVivos,
                              boolean porForma, boolean formaAsc, boolean porElo,
                              Map<Long, Forma> forma, Map<Long, Integer> eloWatch,
                              Set<Long> vinculosExpandidos) {
        List<Player> vis = new ArrayList<>();
        if (modoTop) vis.addAll(topLadder);
        else for (Player p : todosJugadores)
            if (grupoActivo == null || p.grupo().equalsIgnoreCase(grupoActivo)) vis.add(p);
        if (soloVivos) {
            final List<Player> ambito = new ArrayList<>(vis);
            vis.removeIf(p -> !vivo.jugando(p.id())
                    && !(p.vinculo() != 0 && familiaViva(ambito, p.vinculo())));
        }
        Comparator<Player> orden = porForma
                ? Comparator.<Player, Integer>comparing(p -> forma.containsKey(p.id()) && forma.get(p.id()).partidas() > 0 ? forma.get(p.id()).diff() : null,
                        Comparator.nullsLast(formaAsc ? Comparator.<Integer>naturalOrder() : Comparator.<Integer>reverseOrder()))
                        .thenComparing(p -> p.name().toLowerCase())
                : porElo
                ? Comparator.<Player, Integer>comparing(p -> eloWatch.get(p.id()),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(p -> p.name().toLowerCase())
                : Comparator.comparing(p -> p.name().toLowerCase());
        // Familias: la cuenta con más ELO encabeza; las demás cuelgan si está expandida
        Map<Long, Character> marcaFila = new HashMap<>();
        Map<Long, Boolean> vivoFamilia = new HashMap<>();
        Map<Long, List<Player>> familias = new LinkedHashMap<>();
        List<Player> cabezas = new ArrayList<>();
        for (Player p : vis) {
            if (!modoTop && p.vinculo() != 0) familias.computeIfAbsent(p.vinculo(), k -> new ArrayList<>()).add(p);
            else cabezas.add(p);
        }
        for (Map.Entry<Long, List<Player>> f : familias.entrySet()) {
            List<Player> fam = f.getValue();
            if (fam.size() == 1) { cabezas.add(fam.get(0)); continue; }
            fam.sort(Comparator.comparing((Player p) -> eloWatch.get(p.id()),
                            Comparator.nullsLast(Comparator.<Integer>reverseOrder()))
                    .thenComparing(p -> p.name().toLowerCase()));
            Player ppal = fam.get(0);
            cabezas.add(ppal);
            boolean algunaViva = false;
            for (Player x : fam) if (vivo.jugando(x.id())) algunaViva = true;
            vivoFamilia.put(ppal.id(), algunaViva);
            boolean exp = vinculosExpandidos.contains(f.getKey());
            marcaFila.put(ppal.id(), exp ? 'E' : 'P');
            if (exp) for (int i = 1; i < fam.size(); i++) marcaFila.put(fam.get(i).id(), 'H');
        }
        cabezas.sort(orden);
        List<Player> filas = new ArrayList<>();
        for (Player c : cabezas) {
            filas.add(c);
            if (marcaFila.getOrDefault(c.id(), ' ') == 'E') {
                List<Player> fam = familias.get(c.vinculo());
                for (int i = 1; i < fam.size(); i++) filas.add(fam.get(i));
            }
        }
        return new Resultado(filas, marcaFila, vivoFamilia);
    }

    private boolean familiaViva(List<Player> vis, long vinculo) {
        for (Player x : vis)
            if (x.vinculo() == vinculo && vivo.jugando(x.id())) return true;
        return false;
    }
}
