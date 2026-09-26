package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;

/**
 * Lógica de la watchlist: persistencia de players.txt, grupos (creados por el usuario, aunque estén vacíos) y
 * altas/bajas/movimientos. Opera sobre la {@code List<Player>} que recibe cada método: la lista «de verdad»
 * (todosJugadores) sigue viviendo como campo de la ventana. Sin Swing: quien llama decide cuándo repintar el
 * combo de grupos (rebuildGrupos), reaplicar el filtro (aplicarFiltroGrupo) o poner el texto de estado.
 * <p>
 * La config ("grupos", "grupo_activo"...) se lee y escribe con las funciones inyectadas (hoy
 * util.Config.leerConfig/guardarConfig): así el servicio se puede probar sin disco real de configuración.
 */
public final class ListaSeguidos {

    private final Path playersFile;
    private final String grupoGeneral;
    private final BiFunction<String, String, String> leerConfig;
    private final BiConsumer<String, String> guardarConfig;

    public ListaSeguidos(Path playersFile, String grupoGeneral,
                          BiFunction<String, String, String> leerConfig, BiConsumer<String, String> guardarConfig) {
        this.playersFile = playersFile;
        this.grupoGeneral = grupoGeneral;
        this.leerConfig = leerConfig;
        this.guardarConfig = guardarConfig;
    }

    // ----- Persistencia (players.txt) -----------------------------------------
    // OJO (deuda ya existente, no se cambia aquí): tanto cargar como guardar hacen E/S de disco síncrona; si
    // quien llama está en el EDT (como hoy loadPlayers/savePlayers de la ventana), el disco se toca en el EDT.

    /**
     * Añade a {@code destino} lo leído de players.txt (formato {@code id;nombre;grupo[;vinculo]}), siembra el
     * grupo semilla «Amigos» en el primer arranque y depura el «Pros» sembrado en versiones antiguas si sigue
     * vacío. Devuelve el mensaje de error (ya traducido) si no se pudo leer, o null si fue bien.
     */
    public String cargar(List<Player> destino) {
        String error = null;
        if (Files.exists(playersFile)) {
            try {
                for (String line : Files.readAllLines(playersFile, StandardCharsets.UTF_8)) {
                    String[] parts = line.split(";", 4);
                    if (parts.length >= 2 && !parts[0].isBlank()) {
                        long vinc = 0L;
                        if (parts.length == 4 && !parts[3].isBlank())
                            try { vinc = Long.parseLong(parts[3].trim()); } catch (Exception ignored) {}
                        destino.add(new Player(Long.parseLong(parts[0].trim()), parts[1].trim(),
                                parts.length >= 3 && !parts[2].isBlank() ? parts[2].trim() : grupoGeneral, vinc));
                    }
                }
            } catch (Exception e) {
                error = t("No se pudo leer players.txt: ", "Couldn't read players.txt: ") + causa(e);
            }
        } else if (leerConfig.apply("grupos", "").isBlank()) {
            // Primer arranque: grupo semilla (el top ya cubre a los pros)
            guardarConfig.accept("grupos", t("Amigos", "Friends"));
        }
        // Limpieza única: el «Pros» sembrado en versiones anteriores, si sigue vacío, sobra
        for (String pros : new String[]{ "Pros" }) {
            boolean conMiembros = false;
            for (Player p : destino) if (p.grupo().equalsIgnoreCase(pros)) { conMiembros = true; break; }
            if (!conMiembros) {
                List<String> gs = new ArrayList<>();
                // "[,;]": lectura tolerante (fila 102 de DEUDA) - por si «grupos» quedó grabado con «;» desde el
                // diálogo de fichaje (ver gruposConfig). La escritura sigue siendo siempre con «,».
                for (String g : leerConfig.apply("grupos", "").split("[,;]"))
                    if (!g.isBlank() && !g.trim().equalsIgnoreCase(pros)) gs.add(g.trim());
                guardarConfig.accept("grupos", String.join(",", gs));
            }
        }
        return error;
    }

    /** Escribe {@code jugadores} en players.txt. Devuelve el mensaje de error (ya traducido), o null si fue bien. */
    public String guardar(List<Player> jugadores) {
        try {
            List<String> lines = new ArrayList<>();
            for (Player p : jugadores)
                lines.add(p.id() + ";" + p.name() + ";" + p.grupo() + (p.vinculo() != 0 ? ";" + p.vinculo() : ""));
            Files.write(playersFile, lines, StandardCharsets.UTF_8);
            return null;
        } catch (IOException e) {
            return t("No se pudo guardar players.txt: ", "Could not save players.txt: ") + causa(e);
        }
    }

    // ----- Grupos --------------------------------------------------------------

    /**
     * Grupos creados por el usuario (existen aunque estén vacíos). Lectura tolerante (fila 102 de DEUDA):
     * elegirGrupoDialog («+ Nuevo grupo…») podía haber grabado «grupos» con «;» en vez de «,»; se acepta también
     * ese separador al leer, aunque la escritura de este método siga siendo siempre con «,».
     */
    public Set<String> gruposConfig() {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String g : leerConfig.apply("grupos", "").split("[,;]")) if (!g.isBlank()) out.add(g.trim());
        return out;
    }

    public void registrarGrupo(String g) {
        Set<String> gs = gruposConfig();
        gs.add(g);
        guardarConfig.accept("grupos", String.join(",", gs));
    }

    /** Renombra el grupo en los jugadores y en la config, conservando el vínculo de familia de cada uno (misma
     *  regla que moverJugador, decisión 5; antes de la 1.3 renombrar deshacía las familias del grupo). No toca
     *  disco (players.txt): eso lo hace quien llama. */
    public void renombrarGrupo(List<Player> jugadores, String viejo, String nuevo) {
        for (int i = 0; i < jugadores.size(); i++) {
            Player p = jugadores.get(i);
            if (p.grupo().equalsIgnoreCase(viejo))
                jugadores.set(i, new Player(p.id(), p.name(), nuevo, p.vinculo()));
        }
        Set<String> gs = gruposConfig();
        gs.removeIf(g -> g.equalsIgnoreCase(viejo));
        gs.add(nuevo);
        guardarConfig.accept("grupos", String.join(",", gs));
        if (leerConfig.apply("grupo_activo", "").equalsIgnoreCase(viejo)) guardarConfig.accept("grupo_activo", nuevo);
    }

    /** Pasa los jugadores del grupo borrado a General. No toca disco (players.txt): eso lo hace quien llama. */
    public void borrarGrupo(List<Player> jugadores, String g) {
        for (int i = 0; i < jugadores.size(); i++) {
            Player p = jugadores.get(i);
            if (p.grupo().equalsIgnoreCase(g))
                jugadores.set(i, new Player(p.id(), p.name(), grupoGeneral, p.vinculo()));   // conserva el vínculo (F2 de la 1.3)
        }
        Set<String> gs = gruposConfig();
        gs.removeIf(x -> x.equalsIgnoreCase(g));
        guardarConfig.accept("grupos", String.join(",", gs));
        if (leerConfig.apply("grupo_activo", "").equalsIgnoreCase(g)) guardarConfig.accept("grupo_activo", t("Todos", "All"));
    }

    /**
     * El conjunto ordenado de grupos a mostrar (los de los jugadores + los de la config), y de paso persiste
     * la config sin los que ya no tienen jugadores Y no son «General» (un grupo creado no se esfuma al vaciarse).
     * El JComboBox lo rellena quien llama (rebuildGrupos): aquí solo el cálculo y la config.
     */
    public Set<String> calcularGrupos(List<Player> jugadores) {
        Set<String> grupos = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Player p : jugadores) grupos.add(p.grupo());
        grupos.addAll(gruposConfig());
        Set<String> persistir = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String g : grupos) if (!g.equalsIgnoreCase(grupoGeneral)) persistir.add(g);
        guardarConfig.accept("grupos", String.join(",", persistir));
        return grupos;
    }

    // ----- Consulta --------------------------------------------------------------

    public boolean contiene(List<Player> jugadores, long id) {
        for (Player x : jugadores) if (x.id() == id) return true;
        return false;
    }

    public String grupoDeJugador(List<Player> jugadores, long id) {
        for (Player x : jugadores) if (x.id() == id) return x.grupo();
        return null;
    }

    // ----- Altas, bajas y movimientos --------------------------------------------------------------

    public void quitar(List<Player> jugadores, long id) {
        jugadores.removeIf(x -> x.id() == id);
    }

    /** Mueve al jugador de id p.id() al grupo indicado, conservando su vínculo de familia (decisión 5 de Jorge,
     *  DEUDA fila 103): antes de la fase 4 se perdía al mover uno solo, a diferencia de moverVarios, que ya lo
     *  conservaba. Ahora los dos caminos se comportan igual. */
    public void moverJugador(List<Player> jugadores, Player p, String grupo) {
        for (int i = 0; i < jugadores.size(); i++) {
            Player x = jugadores.get(i);
            if (x.id() == p.id()) jugadores.set(i, new Player(x.id(), x.name(), grupo, x.vinculo()));
        }
    }

    /** Mueve a todos los de {@code lista} (por id) al grupo indicado; conserva su vínculo. Devuelve cuántos ids
     *  distintos se movieron (para el mensaje de estado). */
    public int moverVarios(List<Player> jugadores, List<Player> lista, String g) {
        Set<Long> ids = new HashSet<>();
        for (Player x : lista) ids.add(x.id());
        jugadores.replaceAll(x -> ids.contains(x.id()) ? new Player(x.id(), x.name(), g, x.vinculo()) : x);
        return ids.size();
    }

    /** Da de alta a (id, nombre) en el grupo g si no estaba ya. Devuelve si se añadió. */
    public boolean ficharSiNuevo(List<Player> jugadores, long id, String nombre, String g) {
        if (contiene(jugadores, id)) return false;
        jugadores.add(new Player(id, nombre, g));
        return true;
    }

    /** Ficha a p en el grupo g si no estaba ya (como ficharDesdeTop de la ventana). Devuelve si se añadió. */
    public boolean ficharDesdeTop(List<Player> jugadores, Player p, String g) {
        return ficharSiNuevo(jugadores, p.id(), p.name(), g);
    }

    /** Ficha a todos los de {@code lista} que aún no estén, en el grupo g. Devuelve los que se añadieron
     *  (mismo orden que {@code lista}), para que quien llama pueda ofrecer sus cuentas vinculadas. */
    public List<Player> ficharVarios(List<Player> jugadores, List<Player> lista, String g) {
        List<Player> nuevos = new ArrayList<>();
        for (Player p : lista) if (ficharSiNuevo(jugadores, p.id(), p.name(), g)) nuevos.add(p);
        return nuevos;
    }
}
