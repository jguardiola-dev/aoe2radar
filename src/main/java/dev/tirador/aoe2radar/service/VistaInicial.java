package dev.tirador.aoe2radar.service;

import java.util.Collection;
import java.util.Locale;

/**
 * «Abrir en» (Configuración): con qué vista de la Watchlist abre la app. Decisión de Jorge (2026-09-26, 1.3): por
 * defecto ★ Top ladder, como hasta ahora; se puede elegir ★ Top país (cuál), ★ Top clan (uno de los guardados), un
 * grupo (cuál) o «Todos». Se guarda en la clave {@link #CLAVE} con valores que no dependen del idioma: «top»,
 * «pais:es», «clan:R1», «grupo:Amigos», «todos» (el texto visible «Todos»/«All» cambia con el idioma: guardarlo
 * traería de vuelta el fallo bilingüe que ya tuvo grupo_activo).
 * <p>Sin Swing y sin config: quien llama lee y escribe la clave y aplica la vista (WatchlistView.abrirVistaInicial,
 * MenuConfiguracion). Aquí solo se decide.
 */
public final class VistaInicial {
    private VistaInicial() {}

    /** La clave de config.properties. */
    public static final String CLAVE = "abrir_en";

    public enum Tipo { TOP, PAIS, CLAN, GRUPO, TODOS }

    /** Una vista de arranque: el tipo y, para país/clan/grupo, cuál (código ISO en minúsculas, tag o nombre). */
    public record Eleccion(Tipo tipo, String valor) {
        public static final Eleccion TOP_LADDER = new Eleccion(Tipo.TOP, "");
        public static final Eleccion TODOS_LOS_GRUPOS = new Eleccion(Tipo.TODOS, "");

        /** El valor que se guarda en config (ver la clase). */
        public String aConfig() {
            return switch (tipo) {
                case TOP -> "top";
                case TODOS -> "todos";
                case PAIS -> "pais:" + valor;
                case CLAN -> "clan:" + valor;
                case GRUPO -> "grupo:" + valor;
            };
        }

        /** ¿Aplica «Buscar al abrir»? Solo si la app abre en jugadores seguidos (un grupo o «Todos»): en los tops ★
         *  no hay búsqueda al abrir (lo que se ve es el leaderboard, no tu Watchlist). */
        public boolean buscaAlAbrir() { return tipo == Tipo.GRUPO || tipo == Tipo.TODOS; }
    }

    /** Lee el valor guardado. Vacío, desconocido o sin el «cuál» → ★ Top ladder (el de siempre). Solo el primer «:»
     *  separa: un grupo o un clan pueden llevar «:» en el nombre. */
    public static Eleccion leer(String guardado) {
        if (guardado == null) return Eleccion.TOP_LADDER;
        String s = guardado.trim();
        if (s.equalsIgnoreCase("todos")) return Eleccion.TODOS_LOS_GRUPOS;
        int dos = s.indexOf(':');
        if (dos < 0) return Eleccion.TOP_LADDER;
        String tipo = s.substring(0, dos).trim().toLowerCase(Locale.ROOT), valor = s.substring(dos + 1).trim();
        if (valor.isEmpty()) return Eleccion.TOP_LADDER;
        return switch (tipo) {
            case "pais" -> new Eleccion(Tipo.PAIS, valor.toLowerCase(Locale.ROOT));
            case "clan" -> new Eleccion(Tipo.CLAN, valor);
            case "grupo" -> new Eleccion(Tipo.GRUPO, valor);
            default -> Eleccion.TOP_LADDER;
        };
    }

    /**
     * La elección, comprobada contra lo que existe hoy: si el país no es un código conocido, el clan ya no está entre
     * los guardados o el grupo ya no existe, ★ Top ladder. El clan y el grupo se devuelven con las mayúsculas de hoy
     * (se comparan sin distinguirlas), para que el combo los encuentre tal cual.
     */
    public static Eleccion resolver(Eleccion e, Collection<String> codigosPais, Collection<String> clanesGuardados,
                                    Collection<String> grupos) {
        return switch (e.tipo()) {
            case TOP, TODOS -> e;
            case PAIS -> codigosPais.contains(e.valor()) ? e : Eleccion.TOP_LADDER;
            case CLAN -> buscar(clanesGuardados, e.valor()) instanceof String c ? new Eleccion(Tipo.CLAN, c) : Eleccion.TOP_LADDER;
            case GRUPO -> buscar(grupos, e.valor()) instanceof String g ? new Eleccion(Tipo.GRUPO, g) : Eleccion.TOP_LADDER;
        };
    }

    private static String buscar(Collection<String> nombres, String nombre) {
        for (String x : nombres) if (x.equalsIgnoreCase(nombre)) return x;
        return null;
    }
}
