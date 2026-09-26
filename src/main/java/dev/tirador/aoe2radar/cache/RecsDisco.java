package dev.tirador.aoe2radar.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;

import static dev.tirador.aoe2radar.util.Sistema.enCarpetaBase;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Texto.limpiaNombre;
import static dev.tirador.aoe2radar.util.Texto.recorta;
import static dev.tirador.aoe2radar.util.Texto.sanea;

/** Recs guardadas en disco: carpeta recs/, ruta de cada partida y numeración de «Guess the ELO». */
public final class RecsDisco {
    private RecsDisco() {}

    public static final Path RECS_DIR = enCarpetaBase("recs");

    /** Nombre de archivo: empieza SIEMPRE por el jugador seguido de referencia
     *  (y su equipo), luego los rivales, mapa y hora:
     *  Ref+Aliado-vs-Rival1+Rival2_Mapa_dd-MM_HH.mm.aoe2record
     *  La misma partida siempre genera el mismo nombre (re-descargar sobrescribe). */
    public static Path destino(Match m) {
        // Guess the ELO: el archivo lleva el NÚMERO de la tanda (el que anuncia la barra de estado y lee
        // maxGteEnDisco), nunca el id de la partida (dejaría buscar el resultado y hacía saltar la numeración).
        if (m.gte > 0) return RECS_DIR.resolve("Guess the ELO " + m.gte + ".aoe2record");
        String hora = DateTimeFormatter.ofPattern("dd-MM_HH.mm")
                .withZone(ZoneId.systemDefault()).format(m.finished);

        Map<Integer, List<MatchPlayer>> porEquipo = new TreeMap<>();
        for (MatchPlayer p : m.players)
            porEquipo.computeIfAbsent(p.team, k -> new ArrayList<>()).add(p);

        Integer equipoRef = null;
        for (MatchPlayer p : m.players) if (p.id == m.refId) equipoRef = p.team;

        List<Integer> orden = new ArrayList<>(porEquipo.keySet());
        if (equipoRef != null) { orden.remove(equipoRef); orden.add(0, equipoRef); }

        List<String> lados = new ArrayList<>();
        for (Integer t : orden) {
            List<MatchPlayer> ps = new ArrayList<>(porEquipo.get(t));
            if (t.equals(equipoRef))
                ps.sort((a, b) -> Boolean.compare(b.id == m.refId, a.id == m.refId)); // ref primero
            List<String> ns = new ArrayList<>();
            for (MatchPlayer p : ps) ns.add(recorta(limpiaNombre(p.name), 14));
            lados.add(String.join("+", ns));
        }
        String vs = String.join("-vs-", lados);
        if (vs.length() > 110) vs = vs.substring(0, 110);   // TGs con nicks kilométricos
        String nombre = sanea(vs + "_" + m.map + "_" + hora) + ".aoe2record";
        return RECS_DIR.resolve(nombre);
    }

    /** Techo de un número de tanda creíble: hasta la 1.2 el archivo llevaba el id de la partida (cientos de
     *  millones), y esos archivos viejos no deben seguir marcando la numeración. */
    static final int MAX_NUMERO_GTE = 100_000;

    /** Mayor número «Guess the ELO N» ya usado en ./recs y en la carpeta savegame del juego (config «savegame»),
     *  para continuar la numeración sin repetir el nombre de una tanda vieja: «Vaciar recs» deja limpia ./recs,
     *  pero las copias enviadas al juego siguen allí y la nueva «Guess the ELO 1» las sobrescribiría. Disco: no
     *  llamar desde el EDT (lo llama AzarService en su hilo). */
    public static int maxGteEnDisco() {
        Path sg = null;
        try {
            String cfg = leerConfig("savegame", null);
            if (cfg != null && !cfg.isBlank()) sg = Path.of(cfg);
        } catch (RuntimeException ignored) { }   // ruta ilegible en config: solo cuenta ./recs
        return maxGteEnDisco(RECS_DIR, sg);
    }

    static int maxGteEnDisco(Path recs, Path savegame) {
        int max = maxGteEnDisco(recs);
        if (savegame != null && Files.isDirectory(savegame)) max = Math.max(max, maxGteEnDisco(savegame));
        return max;
    }

    static int maxGteEnDisco(Path dir) {
        int max = 0;
        try (var st = Files.list(dir)) {
            for (Path p : st.toList()) {
                String n = p.getFileName().toString();
                if (n.startsWith("Guess the ELO ") && n.endsWith(".aoe2record")) {
                    try {
                        int k = Integer.parseInt(n.substring(14, n.length() - 11).trim());
                        if (k <= MAX_NUMERO_GTE) max = Math.max(max, k);   // un id de partida (archivo de la 1.2) no cuenta
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException ignored) {}
        return max;
    }
}
