package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.PaginaLb;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static dev.tirador.aoe2radar.util.Log.log;

/** Lógica pura de «Al azar por ELO» y «Guess the ELO»: rangos de páginas, filtros, franjas y composición de tandas. */
public final class Aleatorio {
    private Aleatorio() {}

    /** Modo ranked de mapa aleatorio (el mode del API llega como
     *  «1v1 Random Map» / «Team Random Map»). */
    public static boolean esRankedRM(String mode) {
        return mode != null && (mode.startsWith("1v1 Random") || mode.startsWith("Team Random"));
    }

    @FunctionalInterface
    public interface ProveedorPaginas {
        PaginaLb pagina(int p) throws IOException, InterruptedException;
    }

    /** Búsqueda binaria: primera página cuyo rating mínimo ya alcanza el techo
     *  del rango (el ladder va de mayor a menor rating). */
    public static int primeraPaginaRango(ProveedorPaginas prov, int hi, int ult) throws IOException, InterruptedException {
        int a = 1, b = ult, res = ult;
        while (a <= b) {
            int m = (a + b) / 2;
            PaginaLb pg = prov.pagina(m);
            if (pg.jugadores().isEmpty() || pg.ratingMin() <= hi) { res = m; b = m - 1; }
            else a = m + 1;
        }
        return res;
    }

    /** Búsqueda binaria: última página cuyo rating máximo sigue llegando al
     *  suelo del rango. */
    public static int ultimaPaginaRango(ProveedorPaginas prov, int lo, int ult) throws IOException, InterruptedException {
        int a = 1, b = ult, res = 1;
        while (a <= b) {
            int m = (a + b) / 2;
            PaginaLb pg = prov.pagina(m);
            if (!pg.jugadores().isEmpty() && pg.ratingMax() >= lo) { res = m; a = m + 1; }
            else b = m - 1;
        }
        return res;
    }

    /** Del leaderboard, los perfiles dentro del rango, barajados y limitados. */
    public static List<Match> ordenaYRecorta(List<Match> l) {
        l.sort(Comparator.comparing((Match m) -> m.finished).reversed());
        return l.size() > 10 ? new ArrayList<>(l.subList(0, 10)) : l;
    }

    /** Perfiles del rango priorizando a los ACTIVOS dentro de la ventana: los
     *  inactivos claros quedan fuera y los de actividad desconocida, al final. */
    public static List<Long> elegirPerfiles(List<long[]> lb, int lo, int hi, long cutoffMs, int max, Random rnd) {
        List<Long> activos = new ArrayList<>(), desconocidos = new ArrayList<>();
        for (long[] p : lb) {
            if (p[1] < lo || p[1] > hi) continue;
            long lm = p.length > 2 ? p[2] : 0L;
            if (lm >= cutoffMs) activos.add(p[0]);
            else if (lm == 0L) desconocidos.add(p[0]);
        }
        Collections.shuffle(activos, rnd);
        Collections.shuffle(desconocidos, rnd);
        List<Long> in = new ArrayList<>(activos);
        in.addAll(desconocidos);
        return in.subList(0, Math.min(in.size(), max));
    }

    /** ¿Cumple esta partida todos los filtros del azar? 1v1, en ventana, mapa,
     *  civ (basta uno de los dos) y AMBOS ratings del momento dentro del rango. */
    public static boolean cumpleAzar(Match m, int lo, int hi, Instant cutoff, String mapa, String civ) {
        if (m.players.size() != 2 || !esRankedRM(m.mode) || (m.mode != null && !m.mode.startsWith("1v1"))) return false;
        if (m.finished == null || m.finished.isBefore(cutoff)) return false;
        if (mapa != null && (m.map == null || !m.map.equalsIgnoreCase(mapa))) return false;
        if (civ != null) {
            boolean tiene = false;
            for (MatchPlayer p : m.players)
                if (p.civ != null && civ.equalsIgnoreCase(p.civ.trim())) { tiene = true; break; }
            if (!tiene) return false;
        }
        for (MatchPlayer p : m.players) {
            if (p.rating == null) return false;
            if (p.rating < lo || p.rating > hi) return false;
        }
        return true;
    }

    /** Filtro final: 1v1 dentro de la ventana y ambos jugadores en el rango de
     *  ELO (rating del leaderboard por profileId, con el del match como
     *  respaldo). El flag replay del API describe el sistema de replays de
     *  aoe2companion, no aoe.ms: NO descarta, solo prioriza (las partidas que
     *  lo declaran entran primero al cupo). Deja el desglose en el log. */
    public static List<Match> filtrarAleatorias(Collection<Match> candidatas, Map<Long, Integer> ratingsLb,
                                         int lo, int hi, Instant cutoff, String mapa, String civ,
                                         int max, Random rnd) {
        List<Match> conRec = new ArrayList<>(), resto = new ArrayList<>();
        int no1v1 = 0, viejas = 0, otroMapa = 0, sinCiv = 0, sinRating = 0, fueraRango = 0;
        for (Match m : candidatas) {
            if (m.players.size() != 2 || m.mode == null || !m.mode.startsWith("1v1 Random")) { no1v1++; continue; }
            if (m.finished == null || m.finished.isBefore(cutoff)) { viejas++; continue; }
            if (mapa != null && (m.map == null || !m.map.equalsIgnoreCase(mapa))) { otroMapa++; continue; }
            if (civ != null) {
                boolean tiene = false;
                for (MatchPlayer p : m.players)
                    if (p.civ != null && civ.equalsIgnoreCase(p.civ.trim())) { tiene = true; break; }
                if (!tiene) { sinCiv++; continue; }
            }
            boolean ambos = true, desconocido = false;
            for (MatchPlayer p : m.players) {
                Integer r = ratingsLb.get(p.id);
                if (r == null) r = p.rating;
                if (r == null) { desconocido = true; break; }
                if (r < lo || r > hi) { ambos = false; break; }
            }
            if (desconocido) { sinRating++; continue; }
            if (!ambos) { fueraRango++; continue; }
            (m.povsConRec() > 0 ? conRec : resto).add(m);
        }
        log("filtro al azar: " + candidatas.size() + " candidatas -> " + no1v1 + " no 1v1, "
                + viejas + " fuera de ventana, " + otroMapa + " otro mapa, " + sinCiv + " sin la civ, "
                + sinRating + " sin rating conocido, " + fueraRango + " fuera de rango -> "
                + (conRec.size() + resto.size()) + " válidas (" + conRec.size() + " declaran rec)");
        Collections.shuffle(conRec, rnd);
        Collections.shuffle(resto, rnd);
        List<Match> in = new ArrayList<>(conRec);
        in.addAll(resto);
        List<Match> out = new ArrayList<>(in.subList(0, Math.min(in.size(), max)));
        out.sort(Comparator.comparing((Match m) -> m.finished).reversed());
        return out;
    }

    /** Filtro GTE: 1v1 dentro de la ventana, sin mirar ELO (cualquiera vale).
     *  Prioriza las que declaran rec y baraja: el orden no sigue patrón. */
    public static List<Match> filtrarGte(Collection<Match> candidatas, Instant cutoff, int max, Random rnd) {
        List<Match> conRec = new ArrayList<>(), resto = new ArrayList<>();
        int no1v1 = 0, viejas = 0;
        for (Match m : candidatas) {
            if (m.players.size() != 2 || m.mode == null || !m.mode.startsWith("1v1 Random")) { no1v1++; continue; }
            if (m.finished == null || m.finished.isBefore(cutoff)) { viejas++; continue; }
            (m.povsConRec() > 0 ? conRec : resto).add(m);
        }
        log("filtro GTE: " + candidatas.size() + " candidatas -> " + no1v1 + " no 1v1, " + viejas
                + " fuera de ventana -> " + (conRec.size() + resto.size())
                + " válidas (" + conRec.size() + " declaran rec)");
        Collections.shuffle(conRec, rnd);
        Collections.shuffle(resto, rnd);
        List<Match> in = new ArrayList<>(conRec);
        in.addAll(resto);
        return new ArrayList<>(in.subList(0, Math.min(in.size(), max)));
    }

    /** Tramos de ranking para Guess the ELO, agudos hacia los extremos para
     *  que la élite y el fondo del ladder salgan de verdad: top 0,5%, 0,5-5%,
     *  5-25%, 25-60% y 60-100%. */
    public static int[][] tramosGte(int ult) {
        int c1 = Math.max(1, ult / 200);
        int c2 = Math.max(c1 + 1, ult / 20);
        int c3 = Math.max(c2 + 1, ult / 4);
        int c4 = Math.max(c3 + 1, ult * 6 / 10);
        int fin = Math.max(c4 + 1, ult);
        return new int[][]{ { 1, c1 }, { c1 + 1, c2 }, { c2 + 1, c3 }, { c3 + 1, c4 }, { c4 + 1, fin } };
    }

    /** Una franja al azar por partida, independiente: sin garantías por tanda
     *  y sin patrón que se pueda deducir por descarte. */
    public static int[] franjasAleatorias(int slots, int tramos, Random rnd) {
        int[] f = new int[slots];
        for (int i = 0; i < slots; i++) f[i] = rnd.nextInt(tramos);
        return f;
    }

    /** Ensambla la tanda: una partida por slot muestreado y relleno con
     *  sobrantes si algún slot no aporta. Sin duplicados y barajada para que
     *  la posición en la lista no delate nada. */
    public static List<Match> componerTanda(List<List<Match>> porTramo, int max, Random rnd) {
        List<Match> tanda = new ArrayList<>();
        List<Match> sobrantes = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (List<Match> tramo : porTramo) {
            boolean puesto = false;
            for (Match m : tramo) {
                if (!ids.add(m.id)) continue;
                if (!puesto) { tanda.add(m); puesto = true; }
                else sobrantes.add(m);
            }
        }
        Collections.shuffle(sobrantes, rnd);
        for (Match m : sobrantes) {
            if (tanda.size() >= max) break;
            tanda.add(m);
        }
        if (tanda.size() > max) tanda = new ArrayList<>(tanda.subList(0, max));
        Collections.shuffle(tanda, rnd);
        return tanda;
    }
}
