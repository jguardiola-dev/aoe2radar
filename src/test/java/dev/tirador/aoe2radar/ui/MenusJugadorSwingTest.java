package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.service.EloSesion;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.service.ProfileService;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MenusJugadorSwing con Tareas.EN_LINEA (sin hilos reales) y dobles de EstadoVivo/EloSesion (instancias propias, no
 * el singleton SISTEMA), ProfileService, Navegacion y Acciones. Todo dentro de invokeAndWait: aunque construir un
 * JMenu no exige el EDT, es donde vive en la app real (ver CLAUDE.md, «hilo de la UI»).
 * <p>Cubre lo que se movió tal cual desde SpoilerFreeRecs en la tanda 3 (oleada A2): menú «en partida ahora» (o
 * null si no juega), el orden de fuentes de elo1v1Conocido y que itemJugadorPartida no lanza una segunda petición
 * mientras la primera sigue en vuelo.
 */
class MenusJugadorSwingTest {

    /** Lo mínimo de ProfileService que toca elo1v1Conocido/itemJugadorPartida. */
    static class ProfileServiceFalso implements ProfileService {
        FichaPerfil fichaConocida;
        Integer eloVinculada;
        Integer elo1v1Respuesta;
        int llamadasElo1v1;

        @Override public FichaPerfil ficha(long pid) { return null; }
        @Override public FichaPerfil fichaConocida(long pid) { return fichaConocida; }
        @Override public Integer elo1v1(long pid) { llamadasElo1v1++; return elo1v1Respuesta; }
        @Override public List<Perfil.Vinculada> vinculadas(long pid) { return List.of(); }
        @Override public List<Perfil.Vinculada> vinculadasConElo(long pid) { return List.of(); }
        @Override public List<Perfil.Vinculada> vinculadasConocidas(long pid) { return null; }
        @Override public Integer eloVinculada(long vid) { return eloVinculada; }
        @Override public Map<Long, String> familia(long pid) { return null; }
        @Override public AnioSfr anioSfr(long pid, String nombreSiFalta) { return null; }
        @Override public Actividad actividad(long pid) { return null; }
        @Override public Actividad historial(long pid, String nombre, Actividad base, boolean mas, int maxPaginas, Consumer<Actividad> parcial, BooleanSupplier cancelar) { return null; }
        @Override public int traerHoy(long pid) { return 0; }
    }

    /** Navegacion de mentira: solo apunta a dónde se pidió ir. */
    static class NavegacionFalsa implements Navegacion {
        final List<String> visitas = new ArrayList<>();
        @Override public void abrirTechTree(String civ) { }
        @Override public void abrirCivStats() { }
        @Override public void abrirLadder() { }
        @Override public void abrirPerfil(long pid, String nombre) { visitas.add("perfil:" + pid + ":" + nombre); }
        @Override public void abrirPerfilEnPestana(long pid, String nombre) { visitas.add("pestana:" + pid + ":" + nombre); }
        @Override public void abrirAhora() { }
    }

    /** Acciones de mentira: registra qué le pidió MenusJugadorSwing a la ventana. */
    static class AccionesFalsas implements MenusJugadorSwing.Acciones {
        final List<Long> espectadas = new ArrayList<>();
        final List<String> urls = new ArrayList<>();
        final Set<Long> watchlist = new HashSet<>();
        Set<String> grupos = Set.of("General");
        final List<Object[]> altas = new ArrayList<>();
        String grupoElegido = "Nuevo";
        Map<Long, String> notas = Map.of();

        @Override public void espectarPartida(long matchId) { espectadas.add(matchId); }
        @Override public void abrirUrl(String url) { urls.add(url); }
        @Override public String nombreVisible(long pid, String nombre) { return nombre; }
        @Override public String paisDe(long pid) { return ""; }
        @Override public String notaDe(long pid) { return notas.get(pid); }
        @Override public void pedirAlias(long pid, String nombreOriginal) { }
        @Override public void pedirNota(long pid, String nombre) { }
        @Override public void borrarNota(long pid, String nombre) { }
        @Override public void mostrarVinculadas(long pid, String nombre) { }
        @Override public void nicksAnteriores(long pid, String nombre) { }
        @Override public boolean enWatchlist(long pid) { return watchlist.contains(pid); }
        @Override public Set<String> gruposDisponibles() { return grupos; }
        @Override public void anadirAWatchlist(long pid, String nombre, String grupo) { altas.add(new Object[]{ pid, nombre, grupo }); }
        @Override public String elegirGrupoDialog(String nombreSugerido) { return grupoElegido; }
    }

    private MatchPlayer jugador(long id, String nombre, int team) {
        MatchPlayer p = new MatchPlayer();
        p.id = id; p.name = nombre; p.team = team;
        return p;
    }

    private void enEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r::run);
    }

    @Test
    void enPartida_nullSiNoJuega() throws Exception {
        EstadoVivo vivo = new EstadoVivo(new RelojFalso());
        EloSesion elo1v1 = new EloSesion(vivo, new RelojFalso(), EloSesion.ESPERA);
        MenusJugadorSwing menus = new MenusJugadorSwing(vivo, elo1v1, new ProfileServiceFalso(), new NavegacionFalsa(),
                pid -> null, Tareas.EN_LINEA, new AccionesFalsas());

        AtomicReference<JMenu> resultado = new AtomicReference<>();
        enEdt(() -> resultado.set(menus.enPartida(111L)));

        assertNull(resultado.get());
    }

    @Test
    void enPartida_aliadosYRivalesYTextoDelMenu() throws Exception {
        RelojFalso reloj = new RelojFalso(); reloj.ahora = 1_700_000_000_000L;
        EstadoVivo vivo = new EstadoVivo(reloj);
        EloSesion elo1v1 = new EloSesion(vivo, reloj, EloSesion.ESPERA);
        long yo = 10L, aliado = 20L, rival1 = 30L, rival2 = 40L;
        Match m = new Match();
        m.id = 999L; m.map = "Arabia"; m.started = Instant.now();
        m.players.add(jugador(yo, "Yo", 0));
        m.players.add(jugador(aliado, "Aliado", 0));
        m.players.add(jugador(rival1, "Rival Uno", 1));
        m.players.add(jugador(rival2, "Rival Dos", 1));
        vivo.guardarPartida(yo, m);

        MenusJugadorSwing menus = new MenusJugadorSwing(vivo, elo1v1, new ProfileServiceFalso(), new NavegacionFalsa(),
                pid -> null, Tareas.EN_LINEA, new AccionesFalsas());

        AtomicReference<JMenu> resultado = new AtomicReference<>();
        enEdt(() -> resultado.set(menus.enPartida(yo)));
        JMenu menu = resultado.get();

        assertNotNull(menu);
        assertTrue(menu.getText().contains("En partida ahora"));
        assertTrue(menu.getText().contains("Arabia"));
        // Cabecera «Aliados» (deshabilitada) + 1 aliado + separador + cabecera «Rivales» + 2 rivales + separador + «Espectar»
        List<String> textos = new ArrayList<>();
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem it = menu.getItem(i);
            textos.add(it == null ? "---" : it.getText());
        }
        assertTrue(textos.contains("Aliados"));
        assertTrue(textos.stream().anyMatch(t -> t.startsWith("Aliado")));
        assertTrue(textos.contains("Rivales"));
        assertTrue(textos.stream().anyMatch(t -> t.startsWith("Rival Uno")));
        assertTrue(textos.stream().anyMatch(t -> t.startsWith("Rival Dos")));
        assertTrue(textos.contains("Espectar la partida"));
    }

    @Test
    void elo1v1Conocido_ordenDeFuentes() {
        RelojFalso reloj = new RelojFalso(); reloj.ahora = 1_700_000_000_000L;
        EstadoVivo vivo = new EstadoVivo(reloj);
        EloSesion elo1v1 = new EloSesion(vivo, reloj, EloSesion.ESPERA);
        ProfileServiceFalso servicio = new ProfileServiceFalso();
        MenusJugadorSwing menusSinLive = new MenusJugadorSwing(vivo, elo1v1, servicio, new NavegacionFalsa(),
                pid -> null, Tareas.EN_LINEA, new AccionesFalsas());

        // 1) ELO_1V1 ya conocido y > 0: gana a todo lo demás.
        elo1v1.apuntar(1L, 1500);
        assertEquals(1500, menusSinLive.elo1v1Conocido(1L));

        // 2) ELO_1V1 conocido pero 0 (se preguntó y no tenía): null, sin mirar las demás fuentes.
        servicio.eloVinculada = 999;
        elo1v1.apuntar(2L, null);   // apuntar(null) guarda 0
        assertNull(menusSinLive.elo1v1Conocido(2L));

        // 3) Sin ELO_1V1: la ficha de Live now (liveFicha) manda si trae un rating > 0.
        LongFunction<Object[]> liveFicha = pid -> pid == 3L ? new Object[]{ 3L, "Nombre", 1234 } : null;
        MenusJugadorSwing conLive = new MenusJugadorSwing(vivo, elo1v1, servicio, new NavegacionFalsa(),
                liveFicha, Tareas.EN_LINEA, new AccionesFalsas());
        assertEquals(1234, conLive.elo1v1Conocido(3L));

        // 4) Sin ELO_1V1 ni liveFicha: el ELO de la vinculada (ProfileService.eloVinculada).
        servicio.eloVinculada = 1111;
        assertEquals(1111, menusSinLive.elo1v1Conocido(4L));

        // 5) Sin nada de lo anterior: la ficha conocida del perfil (ladder rm_1v1).
        servicio.eloVinculada = null;
        servicio.fichaConocida = new FichaPerfil(Map.of("rm_1v1", new int[]{ 1650, 0, 0, 0, 0 }), "", "", 0);
        assertEquals(1650, menusSinLive.elo1v1Conocido(5L));

        // 6) Ninguna fuente: null.
        servicio.fichaConocida = null;
        assertNull(menusSinLive.elo1v1Conocido(6L));
    }

    @Test
    void itemJugadorPartida_noRelanzaSiYaHayUnaPeticionEnVuelo() throws Exception {
        RelojFalso reloj = new RelojFalso(); reloj.ahora = 1_700_000_000_000L;
        EstadoVivo vivo = new EstadoVivo(reloj);
        EloSesion elo1v1 = new EloSesion(vivo, reloj, EloSesion.ESPERA);
        ProfileServiceFalso servicio = new ProfileServiceFalso();
        servicio.elo1v1Respuesta = 1800;
        MenusJugadorSwing menus = new MenusJugadorSwing(vivo, elo1v1, servicio, new NavegacionFalsa(),
                pid -> null, Tareas.EN_LINEA, new AccionesFalsas());
        MatchPlayer p = jugador(50L, "Fulano", 0);

        elo1v1.reservar(50L);   // simula una petición ya en camino (por ejemplo, del menú abierto un instante antes)
        enEdt(() -> menus.itemJugadorPartida(p));

        assertEquals(0, servicio.llamadasElo1v1, "no debe pedir el ELO otra vez mientras hay una petición reservada");
    }

    @Test
    void itemJugadorPartida_pideElEloQueFaltaYLoApunta() throws Exception {
        RelojFalso reloj = new RelojFalso(); reloj.ahora = 1_700_000_000_000L;
        EstadoVivo vivo = new EstadoVivo(reloj);
        EloSesion elo1v1 = new EloSesion(vivo, reloj, EloSesion.ESPERA);
        ProfileServiceFalso servicio = new ProfileServiceFalso();
        servicio.elo1v1Respuesta = 1750;
        MenusJugadorSwing menus = new MenusJugadorSwing(vivo, elo1v1, servicio, new NavegacionFalsa(),
                pid -> null, Tareas.EN_LINEA, new AccionesFalsas());
        MatchPlayer p = jugador(60L, "Mengano", 0);

        AtomicReference<JMenuItem> item = new AtomicReference<>();
        enEdt(() -> item.set(menus.itemJugadorPartida(p)));
        enEdt(() -> { });   // el propio ítem se actualiza con un invokeLater: deja que la cola del EDT lo procese

        assertEquals(1, servicio.llamadasElo1v1);
        assertEquals(1750, elo1v1.conocido(60L));
        assertTrue(item.get().getText().contains("1750"));
    }

    @Test
    void menuPerfilNavegador_abreLasDosUrls() throws Exception {
        EstadoVivo vivo = new EstadoVivo(new RelojFalso());
        EloSesion elo1v1 = new EloSesion(vivo, new RelojFalso(), EloSesion.ESPERA);
        AccionesFalsas acciones = new AccionesFalsas();
        MenusJugadorSwing menus = new MenusJugadorSwing(vivo, elo1v1, new ProfileServiceFalso(), new NavegacionFalsa(),
                pid -> null, Tareas.EN_LINEA, acciones);

        AtomicReference<JMenu> resultado = new AtomicReference<>();
        enEdt(() -> resultado.set(menus.perfilNavegador(70L)));
        JMenu menu = resultado.get();
        assertEquals(2, menu.getItemCount());
        enEdt(() -> { menu.getItem(0).doClick(); menu.getItem(1).doClick(); });

        assertEquals(List.of("https://www.aoe2companion.com/players/70", "https://www.aoe2insights.com/user/70/"), acciones.urls);
    }

    @Test
    void deJugador_noOfreceAnadirSiYaEstaEnLaWatchlist() throws Exception {
        EstadoVivo vivo = new EstadoVivo(new RelojFalso());
        EloSesion elo1v1 = new EloSesion(vivo, new RelojFalso(), EloSesion.ESPERA);
        AccionesFalsas acciones = new AccionesFalsas();
        acciones.watchlist.add(80L);
        MenusJugadorSwing menus = new MenusJugadorSwing(vivo, elo1v1, new ProfileServiceFalso(), new NavegacionFalsa(),
                pid -> null, Tareas.EN_LINEA, acciones);

        AtomicReference<JMenu> resultado = new AtomicReference<>();
        enEdt(() -> resultado.set(menus.deJugador(80L, "Zutano")));
        JMenu anadir = buscarSubmenu(resultado.get(), "Añadir a mi watchlist");

        assertNotNull(anadir);
        assertFalse(anadir.isEnabled());
    }

    @Test
    void deJugador_anadirAWatchlistUsaElGrupoElegido() throws Exception {
        EstadoVivo vivo = new EstadoVivo(new RelojFalso());
        EloSesion elo1v1 = new EloSesion(vivo, new RelojFalso(), EloSesion.ESPERA);
        AccionesFalsas acciones = new AccionesFalsas();
        acciones.grupos = Set.of("General");
        MenusJugadorSwing menus = new MenusJugadorSwing(vivo, elo1v1, new ProfileServiceFalso(), new NavegacionFalsa(),
                pid -> null, Tareas.EN_LINEA, acciones);

        AtomicReference<JMenu> resultado = new AtomicReference<>();
        enEdt(() -> resultado.set(menus.deJugador(90L, "Perengano")));
        JMenu anadir = buscarSubmenu(resultado.get(), "Añadir a mi watchlist");
        enEdt(() -> anadir.getItem(0).doClick());   // el único grupo disponible: «General»

        assertEquals(1, acciones.altas.size());
        assertArrayEquals(new Object[]{ 90L, "Perengano", "General" }, acciones.altas.get(0));
    }

    private JMenu buscarSubmenu(JMenu raiz, String texto) {
        for (int i = 0; i < raiz.getItemCount(); i++) {
            JMenuItem it = raiz.getItem(i);
            if (it instanceof JMenu jm && texto.equals(jm.getText())) return jm;
        }
        return null;
    }
}
