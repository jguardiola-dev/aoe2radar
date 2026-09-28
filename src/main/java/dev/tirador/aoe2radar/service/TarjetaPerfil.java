package dev.tirador.aoe2radar.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.tirador.aoe2radar.util.Formato.escapeHtml;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * La tarjeta de perfil del hover de la Watchlist, sin Swing y sin red: el HTML y la mini gráfica que antes se
 * montaban dentro del SwingWorker de ui.WatchlistHoverCard (movidos tal cual), y desde la 1.4 la versión nocturna:
 * con las chispas de sfr-data (últimos ratings 1v1 hasta anoche) la tarjeta sale sin /profiles ni /matches.
 * <p>La gráfica nunca enseña la forma fresca: de la serie (de la más reciente a la más antigua) se quitan las 10 más
 * recientes si hay más de 10, y se pinta si quedan 4 o más (regla de la tarjeta de siempre).
 */
public final class TarjetaPerfil {
    private TarjetaPerfil() {}

    /** Puntos que necesita una chispa parcial para no preguntar a la API: 10 que se quitan + 4 que se pintan. */
    public static final int MIN_PUNTOS_PARCIAL = 14;

    /** Lo que enseña la tarjeta. Cualquier campo puede faltar (null / vacío / 0), como con la respuesta de la API. */
    public record Datos(String pais, String clan, long games, Integer rating, Integer maxRating, Integer wins, Integer losses,
                        int[] spark, boolean pocos1v1) { }

    /** Resultado de la regla de la gráfica: la serie cronológica a pintar, o null con pocos1v1 = true. */
    public record Chispa(int[] spark, boolean pocos1v1) { }

    /** serie: ratings 1v1, de la partida más reciente a la más antigua (el orden de /matches). */
    public static Chispa chispa(List<Integer> serie) {
        if (serie.size() > 10) serie = serie.subList(10, serie.size());   // sin la forma fresca
        if (serie.size() >= 4) {
            List<Integer> cron = new ArrayList<>(serie);
            Collections.reverse(cron);   // cronológico
            return new Chispa(cron.stream().mapToInt(Integer::intValue).toArray(), false);
        }
        return new Chispa(null, true);
    }

    /**
     * La tarjeta desde el nocturno, o null si no alcanza y hay que preguntar a la API como antes: sin archivo de chispas
     * (serieCronologica null), jugador fuera de elo_ayer (ayer null: inactivo, sfr-data no lo cubre) o con una chispa
     * parcial más corta que MIN_PUNTOS_PARCIAL. Con una completa, una serie
     * corta es de verdad «pocos 1v1» y se dice sin llamadas.
     * @param serieCronologica chispa del jugador (vieja → nueva), vacía si no tiene; null si no hay archivo
     * @param extra            {pico, ganadas, perdidas} (-1 = no se sabe) o null
     * @param eloActual        el ELO que la app ya conoce (eloWatch) o null; si falta, el de anoche
     * @param ayer             {elo1v1, partidas1v1, eloEq, partidasEq} de anoche o null
     * @param pais             código de país de anoche (NOMBRES_AYER) o null
     */
    public static Datos desdeNocturno(int[] serieCronologica, boolean completo, int[] extra, Integer eloActual, int[] ayer, String pais) {
        if (serieCronologica == null) return null;
        if (ayer == null) return null;   // fuera del alcance de sfr-data (sin partidas en 28 días): la chispa no sabe nada de él
        if (!completo && serieCronologica.length < MIN_PUNTOS_PARCIAL) return null;
        List<Integer> reciente = new ArrayList<>(serieCronologica.length);
        for (int i = serieCronologica.length - 1; i >= 0; i--) reciente.add(serieCronologica[i]);
        Chispa ch = chispa(reciente);
        Integer rating = eloActual != null && eloActual > 0 ? eloActual : (ayer != null && ayer[0] > 0 ? Integer.valueOf(ayer[0]) : null);
        Integer max = extra != null && extra[0] > 0 ? Integer.valueOf(extra[0]) : null;
        Integer w = extra != null && extra[1] >= 0 ? Integer.valueOf(extra[1]) : null;
        Integer l = extra != null && extra[2] >= 0 ? Integer.valueOf(extra[2]) : null;
        long games = ayer != null ? Math.max(0, ayer[1]) : 0;
        String p = pais != null && !pais.isBlank() && !"null".equals(pais) ? pais.toUpperCase() : "";
        return new Datos(p, "", games, rating, max, w, l, ch.spark(), ch.pocos1v1());
    }

    /**
     * El HTML de la tarjeta con una línea más: el alias y la nota de esa fila de la Watchlist (lo que antes decía el
     * tooltip de la fila, que ya no sale mientras se ve la tarjeta). Se añade al pintar, no en la caché de 10 min: una nota
     * recién editada sale al momento. Sin alias ni nota (null o en blanco), el HTML tal cual.
     */
    public static String conFila(String html, String alias, String nota) {
        boolean hayAlias = alias != null && !alias.isBlank(), hayNota = nota != null && !nota.isBlank();
        if (!hayAlias && !hayNota || !html.endsWith("</html>")) return html;
        StringBuilder l = new StringBuilder("<br><font size='2' color='gray'>");
        if (hayAlias) l.append(t("Alias: ", "Alias: ")).append(escapeHtml(alias));
        if (hayAlias && hayNota) l.append("  \u00B7 ");
        if (hayNota) l.append(t("Nota: ", "Note: ")).append(escapeHtml(nota));
        l.append("</font>");
        return html.substring(0, html.length() - "</html>".length()) + l + "</html>";
    }

    /** El HTML de la tarjeta (el mismo que montaba WatchlistHoverCard). */
    public static String html(String nombre, Datos d) {
        String pais = d.pais() == null ? "" : d.pais(), clan = d.clan() == null ? "" : d.clan();
        long games = d.games();
        Integer rating = d.rating(), maxRating = d.maxRating(), wins = d.wins(), losses = d.losses();
        StringBuilder h = new StringBuilder("<html><b>").append(escapeHtml(nombre)).append("</b>");
        if (!pais.isBlank()) h.append("  \u00B7 ").append(pais);
        if (!clan.isBlank()) h.append("  \u00B7 ").append(escapeHtml(clan));
        h.append("<br>");
        if (rating != null) h.append(t("ELO 1v1: <b>", "1v1 ELO: <b>")).append(rating).append("</b>");
        if (maxRating != null) h.append(t("  \u00B7 máx ", "  \u00B7 peak ")).append(maxRating);
        h.append("<br>");
        if (wins != null && losses != null && wins + losses > 0)
            h.append(t("Winrate 1v1: ", "1v1 winrate: "))
             .append(Math.round(wins * 100.0 / (wins + losses))).append("% (")
             .append(wins + losses).append(t(" partidas)", " games)"));
        else if (games > 0) h.append(games).append(t(" partidas jugadas", " games played"));
        if (d.spark() != null)
            h.append("<br><font size='2' color='gray'>")
             .append(t("Rating (hasta hace ~10 partidas):", "Rating (up to ~10 games ago):"))
             .append("</font>");
        else if (d.pocos1v1())
            h.append("<br><font size='2' color='gray'>")
             .append(t("(pocos 1v1 recientes para la gráfica)", "(too few recent 1v1s for the chart)"))
             .append("</font>");
        return h.append("</html>").toString();
    }
}
