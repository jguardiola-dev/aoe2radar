package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.ListaSeguidos;
import dev.tirador.aoe2radar.service.VistaInicial;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.t;

/**
 * La lógica de la Watchlist sin Swing (fase 5): qué vista está elegida (grupo, ★ ladder, ★ país, ★ clan), los
 * grupos y la lista de seguidos. Sale de WatchlistView tal cual: mismas comparaciones, mismos textos, mismo orden
 * de llamadas. La vista (WatchlistView, la fachada) implementa {@link Pantalla}: le dice al presentador qué hay
 * elegido en sus controles y hace lo que toca Swing (combo, lista, barra de estado).
 * <p>El modo de vista NO se guarda aquí: cada consulta vuelve a leer {@code pantalla.grupoSeleccionado()}, como
 * hacía la vista con el combo (así nunca hay dos verdades). Hilos: todo en el hilo que llama (el EDT en la app).
 */
public final class WatchlistPresenter {

    /** Lo que el presentador necesita de la vista: qué hay elegido en sus controles y los repintados. */
    public interface Pantalla {
        /** El item elegido en el combo de vistas (null si no hay ninguno). Se lee en cada consulta: nunca se guarda. */
        String grupoSeleccionado();
        /** Los items del combo de vistas, en su orden. */
        List<String> itemsCombo();
        /** El texto del campo de clan tal cual (sin recortar); null si el campo aún no existe. */
        String clanEscrito();
        /** Enseña/oculta los controles de cada modo (país, clan, Top N, campana, «Añadir», «Quitar»). */
        void actualizarBotonesModo();
        /** ★ Top clan con el campo vacío: lo rellena con el último clan cargado (config clan_tag). */
        void rellenarClanSiVacio();
        void cargarTopClan();
        void cargarTopLadder(boolean forzar);
        /** Reconstruye la lista visible (aplicarFiltroGrupo). */
        void refrescarFiltro();
        /** Barrido de vivos/ELO de los visibles que aún no se han mirado (refrescarWatchlist). */
        void refrescarWatchlist();
        /** Conteos, título y chip «Jugando» (actualizarIndicadoresVivos). */
        void indicadoresVivos();
        /** Mensaje en la barra de estado. */
        void estado(String texto);
        /** Rehace el combo de vistas con los grupos de ahora (rebuildGrupos). */
        void reconstruirGrupos();
        /** Tras fichar a alguien: ofrece añadir sus cuentas vinculadas (consulta y diálogo, en la vista). */
        void ofrecerVinculadasTrasAlta(long pid, String nombre, String grupo);
    }

    private final Pantalla pantalla;
    private final String topPais, topClan;
    private final PaisItem[] paises;
    private final BiFunction<String, String, String> leerCfg;
    private final BiConsumer<String, String> guardarCfg;
    /** La lista de seguidos: la MISMA instancia que la ventana (y Ratings, Live now, Perfil...). */
    final List<Player> todosJugadores;
    /** Persistencia, grupos y altas/bajas/movimientos de la watchlist: ver service.ListaSeguidos. */
    final ListaSeguidos listaSeguidos;

    PaisItem paisActual;
    List<PaisItem> catalogoOrdenado = List.of();

    /** topPais/topClan/paises: los de la vista (calculados al construirla, con el idioma ya fijado), no otros. */
    WatchlistPresenter(Pantalla pantalla, List<Player> todosJugadores, Path playersFile,
                       String topPais, String topClan, PaisItem[] paises,
                       BiFunction<String, String, String> leerCfg, BiConsumer<String, String> guardarCfg) {
        this.pantalla = pantalla;
        this.todosJugadores = todosJugadores;
        this.topPais = topPais; this.topClan = topClan; this.paises = paises;
        this.leerCfg = leerCfg; this.guardarCfg = guardarCfg;
        this.listaSeguidos = new ListaSeguidos(playersFile, WatchlistView.GRUPO_GENERAL, leerCfg, guardarCfg);
    }

    // ===== Modo de vista (se lee SIEMPRE del combo, vía la Pantalla) ======================================

    public boolean modoTop() {
        String s = String.valueOf(pantalla.grupoSeleccionado());
        return WatchlistView.TOP_LADDER.equals(s) || topPais.equals(s) || topClan.equals(s);
    }
    public boolean modoClan() { return topClan.equals(String.valueOf(pantalla.grupoSeleccionado())); }
    public boolean modoPais() { return topPais.equals(String.valueOf(pantalla.grupoSeleccionado())); }
    public String paisSel() { return paisActual != null ? paisActual.code() : leerConfig("top_pais", "es"); }

    public String vistaActualId() {
        String g = pantalla.grupoSeleccionado();
        String pais = modoPais() ? paisSel() : "";
        return g + "|" + pais;
    }

    /** El grupo de usuario elegido; null = «Todos» o una vista ★ (ladder, país o clan: ninguna es un grupo donde
     *  fichar). Antes de la 1.3 se colaba «★ Top clan»: grupoDestino() lo daba como grupo y el invitado de
     *  «Ver sus partidas» nacía con ese grupo. */
    public String grupoActivo() {
        String s = pantalla.grupoSeleccionado();
        if (s == null) return null;
        return s.equals(t("Todos", "All")) || s.equals(WatchlistView.TOP_LADDER) || s.equals(topPais) || s.equals(topClan)
                || s.equals(t("+ Nuevo grupo…", "+ New group…"))
                || s.equals(t("Gestionar grupos…", "Manage groups…")) ? null : s;
    }

    public String grupoDestino() {
        String g = grupoActivo();
        return g != null ? g : WatchlistView.GRUPO_GENERAL;
    }

    /** El tag de clan escrito ahora mismo en el campo (Top clan); "" si no hay campo o está vacío. */
    public String clanBuscado() { String c = pantalla.clanEscrito(); return c == null ? "" : c.trim(); }

    public List<String> clanesGuardados() { List<String> l = new ArrayList<>(); for (String x : leerCfg.apply("clanes_guardados", "").split(",")) if (!x.isBlank()) l.add(x.trim()); return l; }

    /** El id interno de la vista para las campanas (service.Campanas): «★clan|r1», «★pais|es», «★ladder», «grupo|Amigos». */
    String idVistaCampana() {
        if (modoClan()) { String c = pantalla.clanEscrito(); return "\u2605clan|" + (c == null ? "" : c.trim().toLowerCase(Locale.ROOT)); }
        if (modoPais()) return "\u2605pais|" + paisSel();
        if (modoTop()) return "\u2605ladder";
        return "grupo|" + String.valueOf(pantalla.grupoSeleccionado());
    }

    /** El nombre de la vista actual tal como lo ve el usuario, para el texto de la campana: «★ Top país · España»,
     *  «★ Top clan · R1», «★ Top ladder», «Todos» o el nombre del grupo. Antes se enseñaba el id interno de
     *  idVistaCampana («★pais es», «grupo Amigos»: «grupo» en español también con la app en inglés). */
    String nombreVistaCampana() {
        if (modoClan()) return topClan + " \u00B7 " + clanBuscado();
        if (modoPais()) return topPais + " \u00B7 " + (paisActual != null ? paisActual.nombre() : paisSel().toUpperCase(Locale.ROOT));
        return String.valueOf(pantalla.grupoSeleccionado());
    }

    /** La firma de la lista cargada cuando es la de un clan: nunca coincide con la de ★ Top ladder («global») ni
     *  con la de ★ Top país (el código ISO), así que volver a esas vistas recarga (F1 de la revisión 1.3). */
    static String firmaClan(String tag) { return "clan|" + tag.toLowerCase(Locale.ROOT); }

    /** El combo de vistas cambió de item (listener de la vista): guarda la elección y carga lo que toca. */
    void grupoElegido() {
        String sel = String.valueOf(pantalla.grupoSeleccionado());
        if (sel.equals(topClan)) {
            guardarCfg.accept("grupo_activo", sel);
            pantalla.actualizarBotonesModo();
            pantalla.rellenarClanSiVacio();
            pantalla.cargarTopClan();
            return;
        }
        if (sel.equals(WatchlistView.TOP_LADDER) || sel.equals(topPais)) {
            guardarCfg.accept("grupo_activo", sel);
            pantalla.actualizarBotonesModo();
            pantalla.cargarTopLadder(false);
            return;
        }
        guardarCfg.accept("grupo_activo", sel);
        pantalla.actualizarBotonesModo();
        pantalla.refrescarFiltro();
        pantalla.refrescarWatchlist();
        pantalla.indicadoresVivos();
    }

    // ===== País (★ Top país) ==============================================================================

    /** El catálogo en el orden del selector: el país guardado primero y el resto por nombre. Deja ese orden en
     *  catalogoOrdenado y el primero (el favorito) como paisActual. */
    List<PaisItem> ordenarPaises(String paisGuardado) {
        List<PaisItem> ordenPais = new ArrayList<>();
        for (PaisItem pi : paises) if (pi.code().equals(paisGuardado)) ordenPais.add(pi);
        List<PaisItem> resto = new ArrayList<>();
        for (PaisItem pi : paises) if (!pi.code().equals(paisGuardado)) resto.add(pi);
        resto.sort(Comparator.comparing(PaisItem::nombre, String.CASE_INSENSITIVE_ORDER));
        ordenPais.addAll(resto);
        catalogoOrdenado = ordenPais;
        paisActual = ordenPais.get(0);   // el favorito guardado
        return ordenPais;
    }

    /** Los países del catálogo que casan con lo tecleado en el buscador (sin tildes; por nombre o por código). */
    List<PaisItem> paisesQueCoinciden(String texto) {
        String q = dev.tirador.aoe2radar.util.Texto.sinTildes(texto.trim());
        List<PaisItem> out = new ArrayList<>();
        for (PaisItem pi : catalogoOrdenado)
            if (q.isEmpty() || dev.tirador.aoe2radar.util.Texto.sinTildes(pi.nombre()).contains(q) || pi.code().startsWith(q))
                out.add(pi);
        return out;
    }

    void fijarPais(PaisItem pi) {
        paisActual = pi;
        dev.tirador.aoe2radar.util.Config.guardarConfig("top_pais", pi.code());
        if (modoPais()) pantalla.cargarTopLadder(true);
    }

    // ===== Grupos (lectura) ================================================================================

    /** Grupos donde se puede fichar o mover a alguien: «General», los grupos con gente ahora mismo y los guardados
     *  en config (service.ListaSeguidos.gruposDisponibles). Es el único sitio que los calcula: menú contextual,
     *  diálogos, esGrupoDeUsuario y, vía la ventana, DialogosJugador y MenusJugadorSwing (DEUDA fila 134). */
    public Set<String> gruposDisponibles() { return listaSeguidos.gruposDisponibles(todosJugadores); }

    /** Grupos creados por el usuario (existen aunque estén vacíos). */
    public Set<String> gruposConfig() { return listaSeguidos.gruposConfig(); }

    /** ¿"nombre" es un grupo de verdad (General, uno con jugadores dentro o uno registrado en config)? */
    public boolean esGrupoDeUsuario(String nombre) { return gruposDisponibles().contains(nombre); }

    public String grupoDeJugador(long id) { return listaSeguidos.grupoDeJugador(todosJugadores, id); }

    public boolean containsPlayerId(long id) { return listaSeguidos.contiene(todosJugadores, id); }

    static String limpiarGrupo(String nombre) { return nombre.trim().replace(";", " ").replace(",", " "); }

    // ===== Operaciones de la lista (mismo orden que la vista: guardar, combo, filtro, indicadores, estado) =====

    // Delegado a service.ListaSeguidos (cargar/guardar players.txt); la vista muestra el error, si lo hay, en el status.
    // OJO (deuda ya existente en la 1.1, no se cambia aquí): esta E/S de disco es síncrona y se llama desde el EDT
    // (arranque, botones, menús): el disco se toca en el EDT.
    void cargarJugadores() {
        String error = listaSeguidos.cargar(todosJugadores);
        if (error != null) pantalla.estado(error);
        pantalla.reconstruirGrupos();       // SIEMPRE: sin esto, el combo quedaba vacío en instalaciones nuevas
        pantalla.refrescarFiltro();
    }

    void guardarJugadores() {
        String error = listaSeguidos.guardar(todosJugadores);
        if (error != null) pantalla.estado(error);
    }

    void registrarGrupo(String g) { listaSeguidos.registrarGrupo(g); pantalla.reconstruirGrupos(); }

    void moverJugador(Player p, String grupo) {
        listaSeguidos.moverJugador(todosJugadores, p, grupo);
        guardarJugadores();
        pantalla.reconstruirGrupos();
        pantalla.refrescarFiltro();
        pantalla.indicadoresVivos();
        pantalla.estado(p.name() + t(" movido al grupo «", " moved to group “") + grupo + t("».", "”."));
    }

    void renombrarGrupo(String viejo, String nuevo) {
        listaSeguidos.renombrarGrupo(todosJugadores, viejo, nuevo);
        guardarJugadores();
        pantalla.reconstruirGrupos();
        pantalla.refrescarFiltro();
    }

    void borrarGrupo(String g) {
        listaSeguidos.borrarGrupo(todosJugadores, g);
        guardarJugadores();
        pantalla.reconstruirGrupos();
        pantalla.refrescarFiltro();
    }

    /** Ficha a uno desde un top y, como el buscador, ofrece sus cuentas vinculadas. */
    void ficharDesdeTop(Player p, String g) {
        if (!listaSeguidos.ficharDesdeTop(todosJugadores, p, g)) return;
        guardarJugadores();
        pantalla.reconstruirGrupos();
        pantalla.refrescarFiltro();
        pantalla.estado(p.name() + t(" añadido a «", " added to \u201C") + g + t("» de tu watchlist.", "\u201D in your watchlist."));
        pantalla.ofrecerVinculadasTrasAlta(p.id(), p.name(), g);
    }

    void moverVarios(List<Player> lista, String g) {
        int n = listaSeguidos.moverVarios(todosJugadores, lista, g);
        guardarJugadores(); pantalla.reconstruirGrupos(); pantalla.refrescarFiltro(); pantalla.refrescarWatchlist();
        pantalla.estado(n + t(" jugadores movidos a «", " players moved to \u201C") + g + t("\u00bb.", "\u201D."));
    }

    void quitarDeWatchlist(long id) {
        listaSeguidos.quitar(todosJugadores, id);
        guardarJugadores();
        pantalla.refrescarFiltro();
        pantalla.estado(t("Quitado de tu watchlist.", "Removed from your watchlist."));
    }

    /** «Quitar los N seleccionados de la Watchlist» (menú contextual con varios elegidos). */
    void quitarVarios(List<Player> sel) {
        Set<Long> ids = new HashSet<>(); for (Player x : sel) ids.add(x.id());
        todosJugadores.removeIf(x -> ids.contains(x.id()));
        guardarJugadores(); pantalla.reconstruirGrupos(); pantalla.refrescarFiltro();
        pantalla.estado(ids.size() + t(" jugadores quitados.", " players removed."));
    }

    /** Los items fijos del combo, delante de los grupos: «Todos» y las tres vistas ★. */
    List<String> itemsFijos() { return List.of(t("Todos", "All"), WatchlistView.TOP_LADDER, topPais, topClan); }

    /** El grupo_activo guardado (el que rebuildGrupos intenta volver a elegir). */
    String grupoGuardado() { return leerCfg.apply("grupo_activo", t("Todos", "All")); }

    /** Los grupos que van en el combo tras los fijos: un grupo ya nunca se esfuma al vaciarse. */
    Set<String> calcularGrupos() { return listaSeguidos.calcularGrupos(todosJugadores); }

    /**
     * Qué item del combo corresponde al grupo guardado, o -1. Dos pasadas, EN ESTE ORDEN: 1) coincidencia exacta,
     * como hacía la 1.1 (equalsIgnoreCase a secas). 2) solo si no hubo, la equivalencia bilingüe
     * (coincideGrupoGuardado). Si se hiciera al revés, un grupo de usuario que se llame igual que un texto
     * bilingüe del combo (p.ej. un grupo llamado "All") nunca se podría seleccionar: siempre ganaría la
     * traducción a "Todos" antes de llegar a él en la lista.
     */
    int indiceGuardado(List<String> items, String guardado) {
        for (int i = 0; i < items.size(); i++)
            if (items.get(i).equalsIgnoreCase(guardado)) return i;
        for (int i = 0; i < items.size(); i++)
            if (coincideGrupoGuardado(items.get(i), guardado)) return i;
        return -1;
    }

    /**
     * ¿Es "itemActual" (un item del combo, en el idioma activo ahora mismo) la variante bilingüe de "guardado"
     * (el valor de config grupo_activo, escrito quizá en una sesión con otro idioma)? Solo se mira cuando no hay
     * ninguna coincidencia EXACTA con ningún item (ver indiceGuardado): así, un grupo de usuario con ese mismo
     * nombre por casualidad siempre gana a la traducción, que es solo una red de seguridad. Los grupos del usuario
     * son texto libre, no bilingüe: aquí solo se traducen "Todos"/"All", TOP_PAIS y TOP_CLAN, los tres textos fijos
     * del combo que sí cambian con el idioma. Sin esto, cambiar el idioma de la app "perdía" el grupo activo
     * guardado (bug resuelto en fase 4, ver docs/DEUDA.md).
     */
    private boolean coincideGrupoGuardado(String itemActual, String guardado) {
        if (itemActual.equals(t("Todos", "All"))) return guardado.equalsIgnoreCase("Todos") || guardado.equalsIgnoreCase("All");
        if (itemActual.equals(topPais)) return guardado.equalsIgnoreCase("\u2605 Top pa\u00eds") || guardado.equalsIgnoreCase("\u2605 Country top");
        if (itemActual.equals(topClan)) return guardado.equalsIgnoreCase("\u2605 Top clan") || guardado.equalsIgnoreCase("\u2605 Clan top");
        return false;
    }

    // ===== «Abrir en» (Configuración): la vista con la que abre la app =================================

    /** Los grupos de usuario del combo (lo que hay detrás de «Todos» y las tres vistas ★), en su orden. */
    public List<String> gruposDelCombo() {
        List<String> gs = new ArrayList<>();
        for (String s : pantalla.itemsCombo())
            if (!s.equals(t("Todos", "All")) && !s.equals(WatchlistView.TOP_LADDER) && !s.equals(topPais) && !s.equals(topClan)) gs.add(s);
        return gs;
    }

    /** La elección de «Abrir en» guardada, ya comprobada contra lo que existe hoy (service.VistaInicial): si el país,
     *  el clan (entre los guardados) o el grupo ya no existen, ★ Top ladder. No toca nada. */
    public VistaInicial.Eleccion eleccionInicial() {
        List<String> codigos = new ArrayList<>();
        for (PaisItem pi : paises) codigos.add(pi.code());
        return VistaInicial.resolver(VistaInicial.leer(leerCfg.apply(VistaInicial.CLAVE, "top")), codigos, clanesGuardados(), gruposDelCombo());
    }
}
