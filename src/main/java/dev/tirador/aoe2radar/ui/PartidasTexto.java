package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;

import static dev.tirador.aoe2radar.ui.Tema.temaOscuroActivo;
import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * Cómo se ve una partida en texto/HTML: quién jugó contra quién (columna «Partida»), el nombre del buscado
 * con su referencia, el rival con su ±ELO, y el veredicto con corona y color al revelar. Movido tal cual desde
 * SpoilerFreeRecs (son métodos estáticos, sin estado Swing, que ya no dependían de la ventana). La única
 * diferencia mecánica: antes llamaban a {@code cache.Anotaciones.nombreVisible} por import estático; como
 * {@code ui} no puede importar {@code cache} (regla de capas), reciben el mismo dato de un resolutor inyectado
 * por {@link PartidasView} — mismo comportamiento, la dependencia ahora es explícita en vez de implícita.
 */
final class PartidasTexto {

    private final BiFunction<Long, String, String> resolverNombre;

    PartidasTexto(BiFunction<Long, String, String> resolverNombre) {
        this.resolverNombre = resolverNombre;
    }

    private String nombreVisible(long pid, String nombre) { return resolverNombre.apply(pid, nombre); }

    String enfrentamiento(Match m) { return enfrentamiento(m, false); }

    String enfrentamiento(Match m, boolean conResultados) {
        boolean revelar = conResultados && m.finished != null;   // en GTE solo con su ojo: nombres, ELO y ganador
        Map<Integer, List<String>> porEquipo = new TreeMap<>();
        Map<Integer, Boolean> equipoGano = new TreeMap<>();
        int anon = 0;
        for (MatchPlayer p : m.players) {
            String nombre = m.gte > 0 && !conResultados ? t("Jugador ", "Player ") + (++anon) : nombreVisible(p.id, p.name);
            if (m.gte > 0 && conResultados && p.rating != null) nombre += " " + p.rating;   // la solución del GTE
            String extra = p.civ != null && !p.civ.isBlank() ? p.civ : "";
            if (m.azar && m.gte == 0 && p.rating != null)
                extra = extra.isEmpty() ? String.valueOf(p.rating) : extra + ", " + p.rating;
            String pieza = nombre + (extra.isEmpty() ? "" : " (" + extra + ")");
            if (revelar) {
                pieza = escapeHtml(pieza);
                if (PartidasView.SUJETOS.contains(p.id)) pieza = "<b>" + pieza + "</b>";   // la negrita dice la autoría
                if (p.ratingDiff != null) {
                    String col = p.ratingDiff >= 0
                            ? (temaOscuroActivo ? "#6abf69" : "#2e7d32")
                            : (temaOscuroActivo ? "#e57373" : "#c62828");
                    pieza += " <font color='" + col + "'>" + (p.ratingDiff >= 0 ? "+" : "")
                            + p.ratingDiff + "</font>";
                }
            }
            porEquipo.computeIfAbsent(p.team, k -> new ArrayList<>()).add(pieza);
            if (Boolean.TRUE.equals(p.won)) equipoGano.put(p.team, true);
        }
        List<String> lados = new ArrayList<>();
        boolean algunGanador = equipoGano.containsValue(true);
        for (Map.Entry<Integer, List<String>> e : porEquipo.entrySet()) {
            String lado = String.join(", ", e.getValue());
            if (revelar && algunGanador && Boolean.TRUE.equals(equipoGano.get(e.getKey())))
                lado = "\u2726 " + lado;   // corona discreta: el color vive en la columna Jugador
            lados.add(lado);
        }
        String s = String.join("  vs  ", lados);
        return revelar ? "<html>" + s + "</html>" : s;
    }

    /** Nombre del jugador seguido de referencia (columna «Jugador»). */
    String refNombre(Match m) { return refNombre(m, false); }

    String refNombre(Match m, boolean revelado) {
        if (m.gte > 0 && !revelado) return "GTE " + m.gte;
        for (MatchPlayer p : m.players) if (p.id == m.refId) return nombreVisible(p.id, p.name);
        return "?";
    }

    /** «2364 \u2192 2378 (+14)»: el rating con el que entró y con el que salió. */
    static String eloAntesDespues(MatchPlayer p) {   // devuelve HTML (sin envoltorio): gris + diff en verde/rojo
        if (p == null || p.rating == null) return "";
        if (p.ratingDiff == null) return " <font color='#8a8a8a'>" + p.rating + "</font>";
        String colD = p.ratingDiff > 0 ? (temaOscuroActivo ? "#6abf69" : "#2e7d32") : p.ratingDiff < 0 ? (temaOscuroActivo ? "#e57373" : "#c62828") : "#8a8a8a";
        return " <font color='" + colD + "'>(" + (p.ratingDiff >= 0 ? "+" : "") + p.ratingDiff + ")</font> <font color='#8a8a8a'>"
                + p.rating + " \u2192 " + (p.rating + p.ratingDiff) + "</font>";
    }

    String rivalTexto(Match m) { return rivalTexto(m, false); }

    /** Columna Rival: en 1v1 el otro (con su ELO antes → después si la fila está revelada); en equipos «2v2 \u00B7 vs A, B». */
    String rivalTexto(Match m, boolean revelado) {
        if (m.gte > 0 && !revelado) return "";
        MatchPlayer yo = null;
        for (MatchPlayer p : m.players) if (p.id == m.refId) yo = p;
        if (yo == null) return "";
        if (m.players.size() == 2) {
            for (MatchPlayer p : m.players) if (p.id != m.refId) {
                if (!revelado) return nombreVisible(p.id, p.name);
                String colR = Boolean.TRUE.equals(p.won) ? (temaOscuroActivo ? "#6abf69" : "#2e7d32") : (temaOscuroActivo ? "#e57373" : "#c62828");
                return "<html><font color='" + colR + "'>" + (Boolean.TRUE.equals(p.won) ? "\u2726 " : "") + escapeHtml(nombreVisible(p.id, p.name))
                        + eloAntesDespues(p) + "</font></html>";
            }
            return "";
        }
        Map<Integer, Integer> porEquipo = new TreeMap<>();
        for (MatchPlayer p : m.players) porEquipo.merge(p.team, 1, Integer::sum);
        StringBuilder tam = new StringBuilder();
        for (int n : porEquipo.values()) { if (tam.length() > 0) tam.append('v'); tam.append(n); }
        List<String> riv = new ArrayList<>();
        for (MatchPlayer p : m.players) if (p.team != yo.team) riv.add(nombreVisible(p.id, p.name));
        return tam + " \u00B7 vs " + String.join(", ", riv);
    }

    /** El veredicto del buscado, en su columna: verde con \u2726 si ganó,
     *  rojo si perdió; a pelo en vivas, colgadas y GTE. */
    String refConVeredicto(Match m) {
        String nombre = refNombre(m, true);
        if (m.finished == null) return nombre;
        Boolean gano = null;
        for (MatchPlayer p : m.players) if (p.id == m.refId) { gano = p.won; break; }
        if (gano == null) return nombre;
        String col = gano ? (temaOscuroActivo ? "#6abf69" : "#2e7d32")
                          : (temaOscuroActivo ? "#e57373" : "#c62828");
        MatchPlayer yo = null;
        for (MatchPlayer p : m.players) if (p.id == m.refId) yo = p;
        return "<html><font color='" + col + "'>" + (gano ? "\u2726 " : "")
                + escapeHtml(nombre)
                + eloAntesDespues(yo)
                + "</font></html>";
    }

    /** Tooltip del modo consulta: la duración (el ±ELO ya va inline). */
    static String tipResultado(Match m) {
        if (m.finished == null || m.started == null) return null;
        return t("Duraci\u00f3n: ", "Duration: ")
                + Duration.between(m.started, m.finished).toMinutes() + " min";
    }

    static String textoResultado(Match m) {
        StringBuilder sb = new StringBuilder();
        Map<Integer, List<MatchPlayer>> porEquipo = new TreeMap<>();
        for (MatchPlayer p : m.players)
            porEquipo.computeIfAbsent(p.team, k -> new ArrayList<>()).add(p);
        boolean hayGanador = false;
        for (var e : porEquipo.entrySet()) {
            boolean gana = false;
            for (MatchPlayer p : e.getValue()) if (Boolean.TRUE.equals(p.won)) gana = true;
            if (gana) hayGanador = true;
            sb.append(t("Equipo ", "Team ")).append(e.getKey()).append(gana ? t("  —  GANA", "  —  WINS") : "").append('\n');
            for (MatchPlayer p : e.getValue()) {
                sb.append("    ").append(p.name);
                if (p.civ != null && !p.civ.isBlank()) sb.append(" (").append(p.civ).append(')');
                if (p.rating != null) sb.append("   ").append(p.rating);
                if (p.ratingDiff != null)
                    sb.append(" (").append(p.ratingDiff >= 0 ? "+" : "").append(p.ratingDiff).append(')');
                sb.append('\n');
            }
        }
        if (!hayGanador) sb.append(t("\nResultado no disponible en la API para esta partida.\n",
                "\nResult not available in the API for this game.\n"));
        if (m.started != null && m.finished != null) {
            long min = Duration.between(m.started, m.finished).toMinutes();
            sb.append(t("\nDuración: ", "\nDuration: "))
              .append(min / 60 > 0 ? (min / 60) + " h " : "").append(min % 60).append(" min");
        }
        return sb.toString();
    }

    /** Celdas ordenables que se pintan como texto. En curso real = «EN DIRECTO»
     *  arriba del todo; fantasmas de crash = «—» en su sitio cronológico. */
    record FechaCell(Instant t, Instant orden) implements Comparable<FechaCell> {
        static final DateTimeFormatter F =
                DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
        FechaCell(Instant t) { this(t, null); }
        Instant clave() { return t != null ? t : (orden != null ? orden : Instant.MAX); }
        @Override public int compareTo(FechaCell o) { return clave().compareTo(o.clave()); }
        @Override public String toString() {
            if (t != null) return F.format(t);
            return orden != null ? "\u2014" : dev.tirador.aoe2radar.util.I18n.t("EN DIRECTO", "LIVE");
        }
    }
}
