package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.model.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.tirador.aoe2radar.ui.PartidasViewTest.A;
import static dev.tirador.aoe2radar.ui.PartidasViewTest.B;
import static dev.tirador.aoe2radar.ui.PartidasViewTest.C;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PartidasPresenter es puro (sin Swing, sin hilos): estas pruebas llaman a sus métodos estáticos directamente,
 * sin Tareas.EN_LINEA ni dobles de servicio, porque no hay ninguna operación asíncrona que orquestar (fetchMatches/
 * download/buscarAleatorias/buscarGte siguen siendo SwingWorker en PartidasView, y son ellos —no este
 * presentador— quienes deciden qué hacer con el error o la cancelación). Cubre lo que el presentador SÍ hace:
 * vigente() (la comprobación de caducidad por opSerial que usan fetchMatches/download/azar/GTE al terminar),
 * los tres filtros de la tabla y el ojo de revelar.
 */
class PartidasPresenterTest {

    /** Hora fija (nunca Instant.now(): un test con la hora real es un test que a veces falla solo). */
    private static final Instant AHORA = Instant.ofEpochMilli(1_700_000_000_000L);

    // ----- vigente(): la comprobación de caducidad por opSerial que usan fetchMatches/download/azar/GTE -----

    @Test void download_vigente() {
        assertTrue(PartidasPresenter.vigente(3, 3));
    }

    @Test void download_caducada() {
        assertFalse(PartidasPresenter.vigente(3, 4));
    }

    // ----- revelar -----

    @Test void revelar_modoConsultaMuestraPartidaNormal() {
        assertTrue(PartidasPresenter.revelada(true, 0, false));
    }

    @Test void revelar_modoConsultaNoDestapaGte() {
        // el modo consulta global nunca revela Guess the ELO (haría trampa): gte > 0
        assertFalse(PartidasPresenter.revelada(true, 3, false));
    }

    @Test void revelar_manualEnReveladasSinModoConsulta() {
        assertTrue(PartidasPresenter.revelada(false, 0, true));
    }

    @Test void revelar_ninguna() {
        assertFalse(PartidasPresenter.revelada(false, 0, false));
    }

    // ----- filtros: modo -----

    @Test void filtroModo_todosPasaCualquierPartida() {
        assertTrue(PartidasPresenter.pasaFiltroModo("Todos los modos", "Todos los modos", "RM 1v1"));
    }

    @Test void filtroModo_sinSeleccionPasaCualquierPartida() {
        assertTrue(PartidasPresenter.pasaFiltroModo(null, "Todos los modos", "RM 1v1"));
    }

    @Test void filtroModo_coincideExacto() {
        assertTrue(PartidasPresenter.pasaFiltroModo("RM 1v1", "Todos los modos", "RM 1v1"));
    }

    @Test void filtroModo_noCoincide() {
        assertFalse(PartidasPresenter.pasaFiltroModo("RM Team", "Todos los modos", "RM 1v1"));
    }

    // ----- filtros: mapa -----

    @Test void filtroMapa_indiceCeroPasaCualquiera() {
        assertTrue(PartidasPresenter.pasaFiltroMapa(0, "Arabia", "Nómada"));
    }

    @Test void filtroMapa_coincide() {
        assertTrue(PartidasPresenter.pasaFiltroMapa(2, "Arabia", "Arabia"));
    }

    @Test void filtroMapa_noCoincide() {
        assertFalse(PartidasPresenter.pasaFiltroMapa(2, "Arabia", "Nómada"));
    }

    // ----- filtros: periodo -----

    @Test void filtroPeriodo_indiceCeroPasaAunSinFecha() {
        assertTrue(PartidasPresenter.pasaFiltroPeriodo(0, null, AHORA));
    }

    @Test void filtroPeriodo_dentroDeLaVentana() {
        Instant ahora = Instant.parse("2024-06-01T00:00:00Z");
        Instant inicio = ahora.minusSeconds(3600);   // hace 1 hora: dentro de "7 días" (índice 1)
        assertTrue(PartidasPresenter.pasaFiltroPeriodo(1, inicio, ahora));
    }

    @Test void filtroPeriodo_fueraDeLaVentana() {
        Instant ahora = Instant.parse("2024-06-01T00:00:00Z");
        Instant inicio = ahora.minusSeconds(30L * 24 * 3600);   // hace 30 días: fuera de "7 días" (índice 1)
        assertFalse(PartidasPresenter.pasaFiltroPeriodo(1, inicio, ahora));
    }

    @Test void filtroPeriodo_sinFechaDeInicioQuedaFuera() {
        assertFalse(PartidasPresenter.pasaFiltroPeriodo(1, null, AHORA));
    }

    // ======================================================================
    // Lo que salió de la vista en la 1.3 (fase 5): referencia, filtros, sugerencias
    // ======================================================================

    private String idiomaPrevio;
    @BeforeEach void espanol() { idiomaPrevio = IDIOMA; IDIOMA = "es"; }
    @AfterEach void idiomaDeAntes() { IDIOMA = idiomaPrevio; }

    /** La watchlist en pequeño: selección y lista; cuenta cuántas veces se le pide la lista entera. */
    static final class WatchlistFalsa implements PartidasPresenter.Watchlist {
        final List<Player> seleccion = new ArrayList<>(), jugadores = new ArrayList<>();
        boolean modoTop;
        Player invitado;
        int pedidasTodos;
        @Override public List<Player> seleccion() { return new ArrayList<>(seleccion); }
        @Override public int seleccionSize() { return seleccion.size(); }
        @Override public boolean modoTop() { return modoTop; }
        @Override public int totalJugadores() { return jugadores.size(); }
        @Override public Player jugador(int indice) { return jugadores.get(indice); }
        @Override public List<Player> todosJugadores() { pedidasTodos++; return jugadores; }
        @Override public Player invitado() { return invitado; }
        Player forzado;
        boolean soloVivos;
        @Override public Player objetivoForzado() { return forzado; }
        @Override public boolean soloVivosMarcado() { return soloVivos; }
    }

    static MatchPlayer mp(long id, String nombre, int equipo, Integer rating) {
        MatchPlayer p = new MatchPlayer(); p.id = id; p.name = nombre; p.team = equipo; p.rating = rating;
        return p;
    }

    static Match partida(long id, MatchPlayer... jugadores) {
        Match m = new Match();
        m.id = id; m.started = AHORA.minusSeconds(3600); m.finished = AHORA.minusSeconds(600); m.mode = "1v1 Random Map"; m.map = "Arabia";
        for (MatchPlayer p : jugadores) m.players.add(p);
        return m;
    }

    // ----- asignarRef -----

    @Test void asignarRef_elSujetoDeMasRating() {
        Match m = partida(1, mp(10, "a", 1, 1500), mp(11, "b", 2, 1600));
        PartidasPresenter.asignarRef(m, Set.of(10L, 11L), new WatchlistFalsa());
        assertEquals(11, m.refId);
    }

    @Test void asignarRef_sujetoSinRatingPierdeYElPrimeroConRatingSeQueda() {
        Match a = partida(1, mp(10, "a", 1, null), mp(11, "b", 2, 1400));
        Match b = partida(2, mp(10, "a", 1, 1500), mp(11, "b", 2, null));
        Match c = partida(3, mp(10, "a", 1, 1500), mp(11, "b", 2, 1500));
        for (Match m : List.of(a, b, c)) PartidasPresenter.asignarRef(m, Set.of(10L, 11L), new WatchlistFalsa());
        assertEquals(List.of(11L, 10L, 10L), List.of(a.refId, b.refId, c.refId), "en empate, el primero");
    }

    @Test void asignarRef_sinSujetos_seleccionAntesQueLista() {
        WatchlistFalsa w = new WatchlistFalsa();
        w.seleccion.add(new Player(21, "d", "G"));
        w.jugadores.add(new Player(30, "e", "G"));
        Match m = partida(1, mp(30, "e", 1, 2000), mp(21, "d", 2, 1000));
        PartidasPresenter.asignarRef(m, Set.of(), w);
        assertEquals(21, m.refId);
        Match n = partida(2, mp(31, "f", 1, 2000), mp(30, "e", 2, 1000));
        PartidasPresenter.asignarRef(n, Set.of(), w);
        assertEquals(30, n.refId, "sin selección que la jugara: el primero de la lista");
    }

    @Test void asignarRef_nadieConocido_elDeMasRatingYSinJugadoresNoToca() {
        Match m = partida(1, mp(40, "g", 1, 1200), mp(41, "h", 2, 1300), mp(42, "i", 1, null));
        PartidasPresenter.asignarRef(m, Set.of(), new WatchlistFalsa());
        assertEquals(41, m.refId);
        Match primeroSinRating = partida(2, mp(43, "j", 1, null), mp(44, "k", 2, 900));
        PartidasPresenter.asignarRef(primeroSinRating, Set.of(), new WatchlistFalsa());
        assertEquals(44, primeroSinRating.refId);
        Match vacia = partida(3); vacia.refId = 7;
        PartidasPresenter.asignarRef(vacia, Set.of(), new WatchlistFalsa());
        assertEquals(7, vacia.refId);
    }

    // ----- idsFiltroSujetos -----

    @Test void idsFiltroSujetos_marcadosYSusCuentasVinculadas() {
        WatchlistFalsa w = new WatchlistFalsa();
        Player p1 = new Player(100, "P1", "G", 5), p2 = new Player(101, "P2", "G", 5), p3 = new Player(102, "P3", "G", 0), p4 = new Player(103, "P4", "G", 6);
        w.jugadores.addAll(List.of(p1, p2, p3, p4));
        assertEquals(Set.of(100L, 101L), PartidasPresenter.idsFiltroSujetos(List.of(p1, p3), Set.of(100L), w));
        assertEquals(Set.of(102L), PartidasPresenter.idsFiltroSujetos(List.of(p1, p3), Set.of(102L), w));
        assertEquals(Set.of(), PartidasPresenter.idsFiltroSujetos(List.of(p1, p3), Set.of(), w), "sin marcar: sin filtro");
        assertEquals(Set.of(), PartidasPresenter.idsFiltroSujetos(List.of(p3), Set.of(100L), w), "marcado pero ya no es sujeto");
        w.pedidasTodos = 0;
        PartidasPresenter.idsFiltroSujetos(List.of(p3), Set.of(102L), w);
        assertEquals(0, w.pedidasTodos, "sin vínculo no se pide la lista entera");
    }

    // ----- filtrar -----

    static PartidasPresenter.Filtros sinFiltros() {
        return new PartidasPresenter.Filtros("Todos los modos", "Todos los modos", 0, "Todos los mapas", 0, "");
    }

    @Test void filtrar_rellenaLaMismaListaYDevuelveLasTerminadas() {
        Match a = partida(1, mp(10, "a", 1, 1500), mp(20, "rivalA", 2, 1400));
        Match viva = partida(2, mp(10, "a", 1, 1500), mp(21, "rivalB", 2, 1400)); viva.finished = null; viva.enDisco = true; viva.enJuego = true;
        Match colgada = partida(3, mp(10, "a", 1, 1500), mp(22, "rivalC", 2, 1400)); colgada.finished = null;
        List<Match> view = new ArrayList<>(List.of(partida(99)));
        List<Match> asignadas = new ArrayList<>();
        List<Match> terminadas = PartidasPresenter.filtrar(List.of(a, viva, colgada), sinFiltros(), Set.of(), asignadas::add,
                m -> m.id == 2, AHORA, view);
        assertEquals(List.of(a, viva, colgada), view, "la lista de la vista se vacía y se rellena (la misma instancia)");
        assertEquals(List.of(a), terminadas);
        assertEquals(List.of(a, viva, colgada), asignadas, "sin rival: una sola asignación por partida");
        assertEquals("▶", viva.estado);
        assertEquals("—", colgada.estado);
        assertFalse(viva.enDisco || viva.enJuego);
    }

    @Test void filtrar_modoMapaPeriodoRivalYSujetos() {
        Match a = partida(1, mp(10, "a", 1, 1500), mp(20, "rivalA", 2, 1400));
        Match b = partida(2, mp(11, "b", 1, 1500), mp(21, "rivalB", 2, 1400)); b.mode = "Team";
        Match c = partida(3, mp(10, "a", 1, 1500), mp(22, "rivalC", 2, 1400)); c.map = "Nómada";
        Match d = partida(4, mp(10, "a", 1, 1500), mp(23, "rivalD", 2, 1400)); d.started = AHORA.minusSeconds(10L * 24 * 3600);
        List<Match> all = List.of(a, b, c, d), view = new ArrayList<>();
        List<Match> asignadas = new ArrayList<>();
        java.util.function.Consumer<Match> ref = m -> { asignadas.add(m); m.refId = m.players.get(0).id; };
        PartidasPresenter.filtrar(all, new PartidasPresenter.Filtros("Team", "Todos los modos", 0, null, 0, ""), Set.of(), ref, m -> false, AHORA, view);
        assertEquals(List.of(b), view);
        PartidasPresenter.filtrar(all, new PartidasPresenter.Filtros(null, "Todos los modos", 2, "Nómada", 0, ""), Set.of(), ref, m -> false, AHORA, view);
        assertEquals(List.of(c), view);
        PartidasPresenter.filtrar(all, new PartidasPresenter.Filtros(null, "Todos los modos", 0, null, 1, ""), Set.of(), ref, m -> false, AHORA, view);
        assertEquals(List.of(a, b, c), view, "periodo 7 días: la de hace 10 fuera");
        asignadas.clear();
        PartidasPresenter.filtrar(all, new PartidasPresenter.Filtros(null, "Todos los modos", 0, null, 0, "rivalc"), Set.of(), ref, m -> false, AHORA, view);
        assertEquals(List.of(c), view);
        assertEquals(List.of(a, b, c, c, d), asignadas, "con rival: se asigna antes de mirar el rival y otra vez al entrar");
        PartidasPresenter.filtrar(all, sinFiltros(), Set.of(11L, 77L), ref, m -> false, AHORA, view);
        assertEquals(List.of(b), view, "sujetos: alguna de sus cuentas jugó");
    }

    @Test void mensajeFiltros() {
        assertEquals("2 de 5 partidas (según filtros).", PartidasPresenter.mensajeFiltros(2, 5));
        IDIOMA = "en";
        assertEquals("2 of 5 games (per filters).", PartidasPresenter.mensajeFiltros(2, 5));
    }

    @Test void modosYMapas_ordenadosSinRepetirYSinMapasVacios() {
        Match a = partida(1); a.mode = "Z"; a.map = "Nómada";
        Match b = partida(2); b.mode = "A"; b.map = " ";
        Match c = partida(3); c.mode = "A"; c.map = null;
        Match d = partida(4); d.mode = "M"; d.map = "Arabia";
        assertEquals(List.of("A", "M", "Z"), new ArrayList<>(PartidasPresenter.modosDe(List.of(a, b, c, d))));
        assertEquals(List.of("Arabia", "Nómada"), new ArrayList<>(PartidasPresenter.mapasDe(List.of(a, b, c, d))));
    }

    // ----- sugerenciasRival -----

    static List<String> textos(List<Map.Entry<String, Integer>> sug) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : sug) out.add(e.getKey() + "=" + e.getValue());
        return out;
    }

    @Test void sugerencias_topOchoPorPartidasSinAliadosNiGte() {
        List<Match> all = new ArrayList<>();
        long id = 1;
        for (int r = 1; r <= 9; r++)
            for (int k = 0; k < r; k++) all.add(partida(id++, mp(10, "Ana", 1, 1500), mp(500 + r, "Rival" + r, 2, 1400)));
        all.add(partida(id++, mp(10, "Ana", 1, 1500), mp(600, "RivalAliado", 1, 1400), mp(601, "x", 2, 1400), mp(602, "y", 2, 1400)));
        Match gte = partida(id++, mp(700, "RivalGte", 1, 1500), mp(701, "z", 2, 1400)); gte.gte = 3;
        all.add(gte);
        List<Match> asignadas = new ArrayList<>();
        List<Map.Entry<String, Integer>> sug = PartidasPresenter.sugerenciasRival(all, "rival",
                m -> { asignadas.add(m); m.refId = 10; }, (pid, nombre) -> nombre);
        assertEquals(List.of("Rival9=9", "Rival8=8", "Rival7=7", "Rival6=6", "Rival5=5", "Rival4=4", "Rival3=3", "Rival2=2"), textos(sug));
        assertEquals(all.size() - 1, asignadas.size(), "fija la referencia de cada partida, salvo las de Guess the ELO");
    }

    @Test void sugerencias_enEquipos_soloElEquipoContrario() {
        Match equipos = partida(1, mp(10, "Ana", 1, 1500), mp(600, "RivalAliado", 1, 1400), mp(601, "RivalContra", 2, 1400), mp(602, "z", 2, 1400));
        assertEquals(List.of("RivalContra=1"), textos(PartidasPresenter.sugerenciasRival(List.of(equipos), "rival", m -> m.refId = 10, (pid, n) -> n)));
    }

    @Test void sugerencias_porNombreVisibleOReal_yContandoMayusculasJuntas() {
        List<Match> all = List.of(
                partida(1, mp(10, "Ana", 1, 1500), mp(501, "Pepe", 2, 1400)),
                partida(2, mp(10, "Ana", 1, 1500), mp(502, "PEPE", 2, 1400)),
                partida(3, mp(10, "Ana", 1, 1500), mp(503, "Juan", 2, 1400)));
        List<Map.Entry<String, Integer>> sug = PartidasPresenter.sugerenciasRival(all, "terror",
                m -> m.refId = 10, (pid, nombre) -> pid == 503 ? "El Terror" : nombre);
        assertEquals(List.of("El Terror=1"), textos(sug), "por el alias");
        sug = PartidasPresenter.sugerenciasRival(all, "pep", m -> m.refId = 10, (pid, nombre) -> nombre);
        assertEquals(List.of("Pepe=2"), textos(sug), "el orden sin mayúsculas junta Pepe y PEPE");
        sug = PartidasPresenter.sugerenciasRival(all, "juan", m -> m.refId = 10, (pid, nombre) -> pid == 503 ? "El Terror" : nombre);
        assertEquals(List.of("El Terror=1"), textos(sug), "o por el nombre real");
    }

    @Test void sugerencias_nadaQueSugerirOCoincidenciaExactaYUnica_vacia() {
        List<Match> all = List.of(partida(1, mp(10, "Ana", 1, 1500), mp(501, "Rival1", 2, 1400)),
                                  partida(2, mp(10, "Ana", 1, 1500), mp(502, "Rival12", 2, 1400)));
        assertEquals(List.of(), PartidasPresenter.sugerenciasRival(all, "", m -> m.refId = 10, (pid, n) -> n));
        assertEquals(List.of(), PartidasPresenter.sugerenciasRival(List.of(), "r", m -> m.refId = 10, (pid, n) -> n));
        assertEquals(List.of(), PartidasPresenter.sugerenciasRival(all, "zz", m -> m.refId = 10, (pid, n) -> n));
        assertEquals(List.of(), PartidasPresenter.sugerenciasRival(all, "rival12", m -> m.refId = 10, (pid, n) -> n), "exacta y única: nada");
        assertEquals(List.of("Rival1=1", "Rival12=1"), textos(PartidasPresenter.sugerenciasRival(all, "rival1", m -> m.refId = 10, (pid, n) -> n)),
                "exacta pero no única: se sugieren las dos");
    }

    // ----- a quién busca «Buscar partidas» -----

    static final class EntornoFalso implements PartidasPresenter.Entorno {
        long pid; boolean abierto; String nombre = "";
        @Override public String nombreVisible(long p, String n) { return n + "*"; }
        @Override public long perfilAbiertoPid() { return pid; }
        @Override public boolean perfilAbierto() { return abierto; }
        @Override public String perfilNombreAbierto() { return nombre; }
    }

    @Test void quienBusca_todasLasRamas() {
        WatchlistFalsa w = new WatchlistFalsa();
        EntornoFalso e = new EntornoFalso();
        assertEquals(new PartidasPresenter.QuienBusca("Buscar partidas (todo el grupo)", null), PartidasPresenter.quienBusca(w, e));
        w.modoTop = true;
        assertEquals("Buscar partidas (selecciona a alguien)", PartidasPresenter.quienBusca(w, e).textoBoton());
        e.pid = 55; e.nombre = "Zed";
        assertEquals(new PartidasPresenter.QuienBusca("Buscar partidas (Zed)", new Player(55, "Zed", "")), PartidasPresenter.quienBusca(w, e),
                "perfil sin abrir pero watchlist en ★: el del perfil");
        w.modoTop = false;
        assertEquals("Buscar partidas (todo el grupo)", PartidasPresenter.quienBusca(w, e).textoBoton());
        e.abierto = true;
        assertEquals(new Player(55, "Zed", ""), PartidasPresenter.quienBusca(w, e).objetivo());
        w.seleccion.add(new Player(1, "a", "G"));
        assertEquals(new PartidasPresenter.QuienBusca("Buscar partidas (1 seleccionado)", null), PartidasPresenter.quienBusca(w, e));
        w.seleccion.add(new Player(2, "b", "G"));
        assertEquals("Buscar partidas (2 seleccionados)", PartidasPresenter.quienBusca(w, e).textoBoton());
        w.invitado = new Player(9, "Inv", "G");
        assertEquals(new PartidasPresenter.QuienBusca("Buscar partidas (Inv*)", null), PartidasPresenter.quienBusca(w, e), "el invitado, con su nombre visible");
        IDIOMA = "en";
        w.invitado = null;
        assertEquals("Search games (2 selected)", PartidasPresenter.quienBusca(w, e).textoBoton());
    }

    // ----- mensajes de estado -----

    @Test void textoVentana_enLaUnidadQueSeLeeMejor() {
        assertEquals("24 h", PartidasPresenter.textoVentana(24));
        assertEquals("36 h", PartidasPresenter.textoVentana(36));
        assertEquals("2 días", PartidasPresenter.textoVentana(48));
        assertEquals("1 semana", PartidasPresenter.textoVentana(168));
        assertEquals("2 semanas", PartidasPresenter.textoVentana(336));
        assertEquals("8 días", PartidasPresenter.textoVentana(192));
    }

    @Test void mensajeBusqueda_conTopeYFallos() {
        assertEquals("3 de 4 partidas en las últimas 2 días.", PartidasPresenter.mensajeBusqueda(3, 4, 48, false, 0));
        assertEquals("3 de 4 partidas en las últimas 24 h.  ⚠ Tope de la búsqueda alcanzado: puede faltar historial antiguo — acorta la ventana o filtra por modo.",
                PartidasPresenter.mensajeBusqueda(3, 4, 24, true, 0));
        assertEquals("3 de 4 partidas en las últimas 24 h.  ⚠ 2 jugador(es) SIN RESPUESTA del servicio (¿429/caído?): sus partidas faltan — reintenta en un minuto.",
                PartidasPresenter.mensajeBusqueda(3, 4, 24, false, 2));
        assertTrue(PartidasPresenter.mensajeBusqueda(3, 4, 24, true, 1).matches(".*Tope.*\u26A0 1 jugador.*"));
    }

    @Test void mensajesDelAzar() {
        assertEquals("Búsqueda detenida.", PartidasPresenter.mensajeAzarDetenido(0));
        assertEquals("Búsqueda detenida.  4 encontradas hasta el corte, aplicadas.", PartidasPresenter.mensajeAzarDetenido(4));
        assertEquals("Nada en 1800–1900 en las últimas 36 h. Detalle del muestreo en descargas.log.", PartidasPresenter.mensajeAzarNada(1800, 1900, 36));
        assertEquals("3 partidas 1v1 al azar, ELO 1800–1900, últimas 36 h. Repite la búsqueda: continúa donde lo dejó.",
                PartidasPresenter.mensajeAzar(3, 1800, 1900, 36, false, () -> false));
        assertEquals("3 partidas 1v1 al azar, ELO 1800–1900, últimas 36 h. No hay más con esos filtros: tramo entero revisado (amplía horas o rango).",
                PartidasPresenter.mensajeAzar(3, 1800, 1900, 36, false, () -> true));
        int[] consultas = { 0 };
        assertEquals("10 partidas 1v1 al azar, ELO 1800–1900, últimas 36 h.",
                PartidasPresenter.mensajeAzar(10, 1800, 1900, 36, false, () -> { consultas[0]++; return true; }));
        assertEquals(0, consultas[0], "con 10 no se pregunta si el tramo se agotó");
    }

    @Test void mensajeBusquedaDetenida_resultadosParcialesEnLosDosIdiomas() {
        assertEquals("Búsqueda detenida: resultados parciales (2 de 5 jugadores)", PartidasPresenter.mensajeBusquedaDetenida(2, 5, 0));
        assertEquals("Búsqueda detenida: resultados parciales (3 de 5 jugadores, 1 con error)", PartidasPresenter.mensajeBusquedaDetenida(3, 5, 1));
        IDIOMA = "en";
        assertEquals("Search stopped: partial results (2 of 5 players)", PartidasPresenter.mensajeBusquedaDetenida(2, 5, 0));
        assertEquals("Search stopped: partial results (3 of 5 players, 1 failed)", PartidasPresenter.mensajeBusquedaDetenida(3, 5, 1));
    }

    @Test void mensajeAzar_deLaMuestraNocturna_avisaDeQueSonPartidasDeAyer() {
        int[] consultas = { 0 };
        assertEquals("6 partidas 1v1 al azar, ELO 1800–1900, partidas de ayer (muestra nocturna).",
                PartidasPresenter.mensajeAzar(6, 1800, 1900, 36, true, () -> { consultas[0]++; return true; }));
        assertEquals(0, consultas[0], "de la muestra, ni «tramo agotado» ni «repite»: son consejos del muestreo por la API");
        IDIOMA = "en";
        assertEquals("6 random 1v1s, ELO 1800–1900, yesterday's games (nightly sample).",
                PartidasPresenter.mensajeAzar(6, 1800, 1900, 36, true, () -> false));
    }

    @Test void mensajesDeGuessTheElo() {
        Match a = partida(1), b = partida(2), c = partida(3);
        a.gte = 3; b.gte = 5; c.gte = 7;
        assertEquals("3 partidas Guess the ELO (archivos: «Guess the ELO 3»–«7»). Adivina y comprueba con «Revelar resultado…».",
                PartidasPresenter.mensajeGte(List.of(a, b, c)));
        assertEquals("Sin partidas para Guess the ELO en las últimas 48 h. Detalle en descargas.log.", PartidasPresenter.mensajeGteNada(48));
    }

    @Test void mensajesDeDescargaYEnvio() {
        java.nio.file.Path dir = java.nio.file.Path.of("recs");
        assertEquals("2/2 recs guardadas en recs", PartidasPresenter.mensajeDescarga(false, 2, 2, dir, 0));
        assertEquals("Detenido. 1/3 recs guardadas en recs  ·  1 al juego  ·  detalle en descargas.log", PartidasPresenter.mensajeDescarga(true, 1, 3, dir, 1));
        assertEquals("2 recs enviadas al juego.", PartidasPresenter.mensajeEnvio(2, 0));
        assertEquals("2 recs enviadas al juego (1 ya estaban, actualizadas).", PartidasPresenter.mensajeEnvio(2, 1));
        IDIOMA = "en";
        assertEquals("2 recs sent to the game (1 were already there, refreshed).", PartidasPresenter.mensajeEnvio(2, 1));
    }

    // ----- lo que se hace con los resultados -----

    @Test void sujetosAzar_titularesSinRepetirEnOrdenYConFiltroDeCiv() {
        Match a = partida(1, mp(10, "Uno", 1, 1850), mp(11, "Dos", 2, 1880));
        Match b = partida(2, mp(12, "Tres", 1, 1800), mp(11, "Dos", 2, 1890));
        Match c = partida(3, mp(13, "Cuatro", 1, 1870), mp(14, "Cinco", 2, 1820));
        c.players.get(1).civ = "Franks";
        PartidasPresenter.SujetosAzar s = PartidasPresenter.sujetosAzar(List.of(a, b, c), null, m -> "n" + m.refId);
        assertEquals(List.of(new Player(11, "n11", "", 0), new Player(13, "n13", "", 0)), s.refs());
        assertEquals(Set.of(11L, 13L), s.refIds());
        assertTrue(a.azar && b.azar && c.azar);
        s = PartidasPresenter.sujetosAzar(List.of(c), "franks", m -> "n" + m.refId);
        assertEquals(14, c.refId, "con civ: quien la jugó, aunque tenga menos ELO");
        assertEquals(List.of(new Player(14, "n14", "", 0)), s.refs());
    }

    @Test void ids_yIdsDeLaLista() {
        assertEquals(List.of(3L, 1L, 2L), PartidasPresenter.ids(List.of(new Player(3, "c", "G"), new Player(1, "a", "G"), new Player(2, "b", "G"))));
        WatchlistFalsa w = new WatchlistFalsa();
        w.jugadores.addAll(List.of(new Player(5, "e", "G"), new Player(6, "f", "G")));
        w.seleccion.add(new Player(7, "g", "G"));
        assertEquals(Set.of(5L, 6L), PartidasPresenter.idsDeLaLista(w), "la lista, no la selección");
    }

    @Test void aplicarVivos_jugandoFueraYElRestoSinTocar() {
        dev.tirador.aoe2radar.service.EstadoVivo e = new dev.tirador.aoe2radar.service.EstadoVivo(new dev.tirador.aoe2radar.util.RelojFalso());
        e.marcarJugando(2, 70);
        e.marcarJugando(3, 71);
        e.marcarJugando(4, 72);
        dev.tirador.aoe2radar.service.BarridoVivos.DecisionBuscar dec = new dev.tirador.aoe2radar.service.BarridoVivos.DecisionBuscar(
                Map.of(1L, 55L), Map.of(1L, "en Arabia"), Set.of(3L, 4L));
        PartidasPresenter.aplicarVivos(dec, List.of(new Player(1, "a", "G"), new Player(2, "b", "G"), new Player(3, "c", "G")), e);
        assertTrue(e.jugando(1), "el que da por jugando");
        assertEquals(55L, e.matchDe(1));
        assertEquals("en Arabia", e.info(1));
        assertTrue(e.jugando(2), "sin noticias: como estaba");
        assertFalse(e.jugando(3), "el que da por fuera");
        assertTrue(e.jugando(4), "fuera de los buscados: no se toca");
    }

    @Test void conservarEstados_porIdYSoloSiLaNuevaNoTiene() {
        Match v1 = partida(1), v2 = partida(2), v3 = partida(3);
        v1.estado = "descargando…"; v2.estado = " "; v3.estado = "✓ guardada";
        Match n1 = partida(1), n2 = partida(2), n3 = partida(3), n4 = partida(4);
        n3.estado = "✗ no disponible";
        PartidasPresenter.conservarEstados(List.of(v1, v2, v3), List.of(n1, n2, n3, n4));
        assertEquals(List.of("descargando…", "", "✗ no disponible", ""), List.of(n1.estado, n2.estado, n3.estado, n4.estado));
    }

    @Test void separarVivas_enOrden() {
        Match a = partida(1), b = partida(2), c = partida(3);
        b.finished = null;
        PartidasPresenter.Separadas s = PartidasPresenter.separarVivas(List.of(a, b, c));
        assertEquals(List.of(a, c), s.terminadas());
        assertEquals(List.of(b), s.vivas());
    }

    // ----- a quién pide las partidas fetchMatches -----

    static Player jugador(long id) { return new Player(id, "j" + id, "G"); }

    @Test void buscaAlDelPerfil_yNadieABuscar() {
        WatchlistFalsa w = new WatchlistFalsa();
        EntornoFalso e = new EntornoFalso();
        e.pid = 5; e.abierto = true;
        assertTrue(PartidasPresenter.buscaAlDelPerfil(w, e));
        e.abierto = false;
        assertFalse(PartidasPresenter.buscaAlDelPerfil(w, e), "perfil cerrado");
        e.abierto = true; e.pid = 0;
        assertFalse(PartidasPresenter.buscaAlDelPerfil(w, e), "sin pid");
        e.pid = 5; w.seleccion.add(jugador(1));
        assertFalse(PartidasPresenter.buscaAlDelPerfil(w, e), "con selección");
        w.seleccion.clear(); w.invitado = jugador(2);
        assertFalse(PartidasPresenter.buscaAlDelPerfil(w, e), "con invitado");
        w.invitado = null; w.forzado = jugador(3);
        assertFalse(PartidasPresenter.buscaAlDelPerfil(w, e), "con forzado");

        WatchlistFalsa vacia = new WatchlistFalsa();
        assertTrue(PartidasPresenter.nadieABuscar(vacia));
        vacia.invitado = jugador(1);
        assertFalse(PartidasPresenter.nadieABuscar(vacia));
        vacia.invitado = null; vacia.forzado = jugador(1);
        assertFalse(PartidasPresenter.nadieABuscar(vacia));
        vacia.forzado = null; vacia.jugadores.add(jugador(1));
        assertFalse(PartidasPresenter.nadieABuscar(vacia));
    }

    @Test void elegirBuscados_ordenDePrioridad() {
        WatchlistFalsa w = new WatchlistFalsa();
        w.jugadores.addAll(List.of(jugador(1), jugador(2)));
        assertEquals(new PartidasPresenter.Eleccion(List.of(jugador(1), jugador(2)), false, false, false), PartidasPresenter.elegirBuscados(w, null));
        w.seleccion.add(jugador(2));
        assertEquals(List.of(jugador(2)), PartidasPresenter.elegirBuscados(w, jugador(9)).buscados(), "con selección, el del botón no cuenta");
        w.seleccion.clear();
        assertEquals(List.of(jugador(9)), PartidasPresenter.elegirBuscados(w, jugador(9)).buscados(), "sin selección, el del botón");
        w.invitado = jugador(8);
        assertEquals(List.of(jugador(8)), PartidasPresenter.elegirBuscados(w, jugador(9)).buscados(), "el invitado antes");
        w.forzado = jugador(7);
        assertEquals(new PartidasPresenter.Eleccion(List.of(jugador(7)), true, false, false), PartidasPresenter.elegirBuscados(w, jugador(9)),
                "el forzado antes que nadie, y hay que limpiarlo");
    }

    @Test void elegirBuscados_enTop() {
        WatchlistFalsa w = new WatchlistFalsa();
        w.modoTop = true;
        for (int i = 1; i <= 16; i++) w.jugadores.add(jugador(i));
        assertEquals(new PartidasPresenter.Eleccion(List.of(), false, false, true), PartidasPresenter.elegirBuscados(w, null),
                "en ★ sin selección ni «● Jugando»: hay que pedir que seleccione");
        w.soloVivos = true;
        assertEquals(16, PartidasPresenter.elegirBuscados(w, null).buscados().size(), "con «● Jugando»: toda la lista, sin tope");
        w.seleccion.addAll(w.jugadores);
        PartidasPresenter.Eleccion e = PartidasPresenter.elegirBuscados(w, null);
        assertTrue(e.recortadaA15());
        assertEquals(w.jugadores.subList(0, 15), e.buscados());
        w.seleccion.remove(15);
        e = PartidasPresenter.elegirBuscados(w, null);
        assertFalse(e.recortadaA15(), "15 justos: sin aviso");
        assertEquals(15, e.buscados().size());
    }

    @Test void alternarFiltroSujeto_clicYCtrlClic() {
        Set<Long> f = new java.util.HashSet<>();
        PartidasPresenter.alternarFiltroSujeto(f, 1, false);
        assertEquals(Set.of(1L), f);
        PartidasPresenter.alternarFiltroSujeto(f, 2, true);
        assertEquals(Set.of(1L, 2L), f, "Ctrl suma");
        PartidasPresenter.alternarFiltroSujeto(f, 1, true);
        assertEquals(Set.of(2L), f, "Ctrl quita al marcado");
        PartidasPresenter.alternarFiltroSujeto(f, 2, false);
        assertEquals(Set.of(), f, "clic en el único marcado: sin filtro");
        f.addAll(Set.of(1L, 2L));
        PartidasPresenter.alternarFiltroSujeto(f, 2, false);
        assertEquals(Set.of(2L), f, "clic con varios marcados: solo ese");
    }

    // ----- recorrer(): el doInBackground de «Buscar partidas», sin Swing -----

    /** El recorrido jugador a jugador (lotes de 1, el de la 1.3): los tests de abajo lo fijan tal cual; los de lotes
     *  de 10 van después (recorrer_porLotes_*). */
    static PartidasPresenter.Recorrido recorrerUno(List<Player> tracked, Instant cutoff, int perPage, long pausaMs,
                                                   PartidasViewTest.PaginadorUno paginas, java.util.function.Predicate<Match> enCurso,
                                                   java.util.function.BooleanSupplier parar, java.util.function.Consumer<String> progreso) {
        return PartidasPresenter.recorrer(tracked, cutoff, 1, perPage, pausaMs, (pids, pag, pp) -> {
            assertEquals(1, pids.size(), "lote de 1");
            return paginas.pagina(pids.get(0), pag, pp);
        }, enCurso, parar, progreso);
    }

    // ----- recorrer() por lotes (1.4): los ids juntos en una llamada -----

    static List<Player> jugadores(int n) {
        List<Player> l = new ArrayList<>();
        for (int i = 1; i <= n; i++) l.add(jugador(i));
        return l;
    }

    @Test void recorrer_porLotes_unaLlamadaPorLoteDeDiez() {
        Instant ahora = Instant.now();
        List<List<Long>> lotes = new ArrayList<>();
        List<Integer> porPagina = new ArrayList<>();
        PartidasPresenter.Recorrido r = PartidasPresenter.recorrer(jugadores(25), ahora.minusSeconds(86_400),
                PartidasPresenter.JUGADORES_POR_LOTE, PartidasPresenter.PARTIDAS_POR_LOTE, 0,
                (pids, pag, pp) -> {
                    lotes.add(List.copyOf(pids)); porPagina.add(pp);
                    List<Match> l = new ArrayList<>();
                    for (long pid : pids) l.add(terminadaHace(pid, pid, 10, ahora));   // una partida por jugador
                    return l;
                },
                m -> false, () -> false, s -> { });
        assertEquals(3, lotes.size(), "25 jugadores: 3 llamadas (antes, 25)");
        assertEquals(List.of(10, 10, 5), lotes.stream().map(List::size).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), lotes.get(0), "en el orden de la lista");
        assertEquals(List.of(100, 100, 100), porPagina);
        assertEquals(25, r.lista().size());
        assertEquals(25, r.recorridos());
        assertEquals(25, r.exitosos().size());
        assertFalse(r.topeAlcanzado());
    }

    @Test void recorrer_porLotes_paginaMientrasLaMasAntiguaSeaPosteriorAlCorte() {
        Instant ahora = Instant.now();
        List<Integer> paginas = new ArrayList<>();
        long[] id = { 0 };
        // La «API» da 100 por página, cada vez más antiguas: página 1 = hace 1..100 min, página 2 = hace 101..200 min…
        PartidasPresenter.Recorrido r = PartidasPresenter.recorrer(jugadores(3), ahora.minusSeconds(150 * 60), 10, 100, 0,
                (pids, pag, pp) -> {
                    paginas.add(pag);
                    List<Match> l = new ArrayList<>();
                    for (int i = 1; i <= pp; i++) l.add(terminadaHace(++id[0], pids.get(i % pids.size()), (pag - 1) * 100L + i, ahora));
                    return l;
                },
                m -> false, () -> false, s -> { });
        assertEquals(List.of(1, 2), paginas, "la página 2 ya llega a antes del corte: no hay 3");
        assertEquals(150, r.lista().size(), "las de los últimos 150 min; las de antes, fuera");
        assertEquals(Set.of(1L, 2L, 3L), r.exitosos());
    }

    @Test void recorrer_porLotes_unLoteQueFallaCuentaASusJugadoresYSigue() {
        Instant ahora = Instant.now();
        List<String> progreso = new ArrayList<>();
        PartidasPresenter.Recorrido r = PartidasPresenter.recorrer(jugadores(12), ahora.minusSeconds(86_400), 10, 100, 0,
                (pids, pag, pp) -> {
                    if (pids.contains(1L)) throw new java.io.IOException("HTTP 429");
                    return List.of(terminadaHace(50, pids.get(0), 10, ahora));
                },
                m -> false, () -> false, progreso::add);
        assertEquals(10, r.fallos(), "el lote de 10 falló entero: sus 10 jugadores, sin respuesta");
        assertEquals(Set.of(11L, 12L), r.exitosos(), "ninguno del lote fallido cuenta como consultado (no se marcan «fuera»)");
        assertEquals(12, r.recorridos());
        assertEquals(1, r.lista().size());
        assertEquals(List.of("Consultando j1 y 9 más…", "Aviso: fallo con j1 y 9 más (HTTP 429)", "Consultando j11 y 1 más…"), progreso);
    }

    @Test void recorrer_porLotes_fallaLaPagina2_conservaLaPagina1YNingunoCuentaComoConsultado() {
        Instant ahora = Instant.now();
        long[] id = { 0 };
        PartidasPresenter.Recorrido r = PartidasPresenter.recorrer(jugadores(10), ahora.minusSeconds(86_400), 10, 100, 0,
                (pids, pag, pp) -> {
                    if (pag == 2) throw new java.io.IOException("HTTP 500");
                    List<Match> l = new ArrayList<>();
                    for (int i = 0; i < 100; i++) l.add(terminadaHace(++id[0], pids.get(i % 10), 1 + i, ahora));
                    return l;
                },
                m -> false, () -> false, s -> { });
        assertEquals(100, r.lista().size(), "lo de la página 1 se conserva");
        assertEquals(10, r.fallos(), "los 10 del lote, sin respuesta completa");
        assertTrue(r.exitosos().isEmpty(), "ninguno se da por consultado: no se marcan «fuera»");
        assertEquals(10, r.recorridos());
        assertFalse(r.detenida());
    }

    @Test void recorrer_porLotes_detenerAMitadDeUnLote_noCuentaSusJugadoresPeroConservaLoLeido() {
        Instant ahora = Instant.now();
        boolean[] parar = { false };
        List<List<Long>> lotes = new ArrayList<>();
        long[] id = { 0 };
        PartidasPresenter.Recorrido r = PartidasPresenter.recorrer(jugadores(25), ahora.minusSeconds(86_400), 10, 100, 0,
                (pids, pag, pp) -> {
                    lotes.add(List.copyOf(pids));
                    if (pids.contains(11L) && pag == 1) parar[0] = true;   // Detener mientras llega la página 1 del lote 2
                    List<Match> l = new ArrayList<>();
                    for (int i = 0; i < (pids.contains(11L) ? 100 : 10); i++) l.add(terminadaHace(++id[0], pids.get(0), 1 + i, ahora));
                    return l;
                },
                m -> false, () -> parar[0], s -> { });
        assertTrue(r.detenida());
        assertEquals(2, lotes.size(), "ni la página 2 del lote cortado ni el lote 3");
        assertEquals(10, r.recorridos(), "«10 de 25 jugadores»: el lote cortado no cuenta");
        assertEquals(110, r.lista().size(), "lo leído del lote cortado se conserva (se muestra como parcial)");
        assertEquals(PartidasPresenter.JUGADORES_POR_LOTE, lotes.get(0).size());
    }

    @Test void recorrer_pararTrasElPrimerJugador_noPideAlSiguienteYVuelveDetenida() {
        boolean[] parar = { false };
        List<Long> pedidas = new ArrayList<>();
        Instant fin = Instant.now().minusSeconds(600);
        PartidasPresenter.Recorrido r = recorrerUno(List.of(A, B, C), fin.minusSeconds(86_400), 50, 0,
                (pid, pag, pp) -> { pedidas.add(pid); parar[0] = true; return List.of(PartidasViewTest.partida(pid, pid == A.id() ? A : B, fin)); },
                m -> false, () -> parar[0], s -> { });
        assertEquals(List.of(A.id()), pedidas, "Detener pulsado durante la página de A: ni B ni C se consultan");
        assertTrue(r.detenida());
        assertEquals(1, r.recorridos(), "A se leyó entero antes del corte");
        assertEquals(1, r.lista().size(), "lo leído de A se conserva (se muestra como parcial)");
    }

    @Test void recorrer_elFrenoCortaLaEspera_noSigueConElSiguienteJugador() {
        boolean[] parar = { false };
        List<Long> pedidas = new ArrayList<>();
        Instant fin = Instant.now().minusSeconds(600);
        PartidasPresenter.Recorrido r = recorrerUno(List.of(A, B, C), fin.minusSeconds(86_400), 50, 0,
                (pid, pag, pp) -> {
                    pedidas.add(pid);
                    if (pid == B.id()) { parar[0] = true; throw new InterruptedException("detenido"); }   // lo que lanza el freno
                    return List.of(PartidasViewTest.partida(pid, A, fin));
                },
                m -> false, () -> parar[0], s -> { });
        assertEquals(List.of(A.id(), B.id()), pedidas, "el corte del freno en B no deja pasar a C");
        assertTrue(r.detenida());
        assertEquals(0, r.fallos(), "un corte pedido no es un fallo del servicio");
        assertEquals(1, r.recorridos(), "B, cortado a medias, no cuenta");
    }

    @Test void recorrer_loQueSirvioElRespaldoNoCuentaComoConsultado() {
        // 1.4: World's Edge no ve partidas en curso; si su página contara como «consultado con éxito», decidirVivos
        // sacaría de partida a quien está jugando (EstadoVivo.marcarFuera). Se trata como un fallo: su estado no se toca.
        Instant fin = Instant.now().minusSeconds(600);
        PartidasPresenter.Recorrido r = recorrerUno(List.of(A, B), fin.minusSeconds(86_400), 50, 0,
                (pid, pag, pp) -> {
                    Match m = PartidasViewTest.partida(pid, pid == A.id() ? A : B, fin);
                    m.deRespaldo = pid == B.id();
                    return List.of(m);
                },
                m -> false, () -> false, s -> { });
        assertEquals(java.util.Set.of(A.id()), r.exitosos(), "B vino del respaldo: fuera de exitosos");
        assertEquals(2, r.lista().size(), "pero sus partidas se muestran");
        assertEquals(0, r.fallos(), "y no es un fallo que avisar");
    }

    @Test void recorrer_porLotes_unLoteDelRespaldoNoCuentaNingunoDeSusJugadores() {
        // Lotes de 10 (1.4): una llamada por lote; si esa página la sirvió World's Edge, ninguno de sus 10 cuenta como
        // consultado (decidirVivos no los marca «fuera»), aunque solo algunos tengan partidas en ella.
        Instant ahora = Instant.now().minusSeconds(600);
        List<Player> js = jugadores(12);
        List<Integer> llamadas = new ArrayList<>();
        PartidasPresenter.Recorrido r = PartidasPresenter.recorrer(js, ahora.minusSeconds(86_400), 10, 100, 0,
                (pids, pag, pp) -> {
                    llamadas.add(pids.size());
                    Match m = PartidasViewTest.partida(pids.get(0), js.get(0), ahora);
                    m.id = 1000 + pids.get(0);
                    m.deRespaldo = pids.size() == 10;   // el primer lote, del respaldo; el segundo (2), del companion
                    return List.of(m);
                },
                m -> false, () -> false, s -> { });
        assertEquals(List.of(10, 2), llamadas, "una página por lote");
        assertEquals(java.util.Set.copyOf(PartidasPresenter.ids(js.subList(10, 12))), r.exitosos(), "solo el lote del companion");
        assertEquals(12, r.recorridos());
        assertEquals(0, r.fallos());
    }

    @Test void conservaTablaAnterior_soloSiSeDetuvoSinLeerNada() {
        Match m = PartidasViewTest.partida(1, A, Instant.now());
        assertTrue(PartidasPresenter.conservaTablaAnterior(new PartidasPresenter.Recorrido(List.of(), false, 0, java.util.Set.of(), true, 0)));
        assertFalse(PartidasPresenter.conservaTablaAnterior(new PartidasPresenter.Recorrido(List.of(m), false, 0, java.util.Set.of(A.id()), true, 1)),
                "detenida con algo leído: se muestra como parcial");
        assertFalse(PartidasPresenter.conservaTablaAnterior(new PartidasPresenter.Recorrido(List.of(), false, 0, java.util.Set.of(A.id()), false, 1)),
                "entera y sin partidas: la tabla se vacía como siempre");
    }

    @Test void recorrer_sinParar_recorreATodosYNoVuelveDetenida() {
        Instant fin = Instant.now().minusSeconds(600);
        PartidasPresenter.Recorrido r = recorrerUno(List.of(A, B), fin.minusSeconds(86_400), 50, 0,
                (pid, pag, pp) -> List.of(PartidasViewTest.partida(pid, pid == A.id() ? A : B, fin)),
                m -> false, () -> false, s -> { });
        assertFalse(r.detenida());
        assertEquals(2, r.lista().size());
        assertEquals(java.util.Set.of(A.id(), B.id()), r.exitosos());
        assertEquals(2, r.recorridos());
    }

    /** Partida terminada hace {@code minutos} de {@code pid} contra un rival fijo por partida. */
    static Match terminadaHace(long id, long pid, long minutos, Instant ahora) {
        Match m = partida(id, mp(pid, "p" + pid, 1, 1500), mp(90_000 + id, "r" + id, 2, 1400));
        m.finished = ahora.minusSeconds(minutos * 60); m.started = m.finished.minusSeconds(1800);
        return m;
    }

    @Test void recorrer_paginaHastaLaVentanaYAvisaDelTopeDeSeisPaginas() {
        Instant ahora = Instant.now();
        List<Integer> paginas = new ArrayList<>();
        PartidasPresenter.Recorrido r = recorrerUno(List.of(jugador(1)), ahora.minusSeconds(86_400), 2, 0,
                (pid, pag, pp) -> { paginas.add(pag); return List.of(terminadaHace(pag * 10L, pid, pag, ahora), terminadaHace(pag * 10L + 1, pid, pag, ahora)); },
                m -> false, () -> false, s -> { });
        assertEquals(List.of(1, 2, 3, 4, 5, 6), paginas, "páginas llenas y dentro de la ventana: hasta 6");
        assertTrue(r.topeAlcanzado());
        assertEquals(12, r.lista().size());
        assertEquals(Set.of(1L), r.exitosos());
    }

    @Test void recorrer_paraEnLaPaginaQueSaleDeLaVentanaODeLaQueVieneCorta() {
        Instant ahora = Instant.now();
        List<Integer> paginas = new ArrayList<>();
        PartidasPresenter.Recorrido r = recorrerUno(List.of(jugador(1)), ahora.minusSeconds(3600), 2, 0,
                (pid, pag, pp) -> { paginas.add(pag); return pag == 1
                        ? List.of(terminadaHace(1, pid, 10, ahora), terminadaHace(2, pid, 20, ahora))
                        : List.of(terminadaHace(3, pid, 50, ahora), terminadaHace(4, pid, 90, ahora)); },
                m -> false, () -> false, s -> { });
        assertEquals(List.of(1, 2), paginas, "la página 2 ya llega a antes de la ventana: no hay 3");
        assertFalse(r.topeAlcanzado());
        assertEquals(List.of(1L, 2L, 3L), r.lista().stream().map(m -> m.id).toList(), "la de hace 90 min, fuera; la más reciente primero");
        paginas.clear();
        recorrerUno(List.of(jugador(1)), ahora.minusSeconds(86_400), 2, 0,
                (pid, pag, pp) -> { paginas.add(pag); return List.of(terminadaHace(pag, pid, pag, ahora)); },
                m -> false, () -> false, s -> { });
        assertEquals(List.of(1), paginas, "página con menos de perPage: no hay más");
    }

    @Test void recorrer_enCursoSoloSiSonReales_sinRepetirYLasVivasPrimero() {
        Instant ahora = Instant.now();
        Match viva = terminadaHace(1, 1, 5, ahora); viva.finished = null;
        Match colgada = terminadaHace(2, 1, 500, ahora); colgada.finished = null;
        Match comun = terminadaHace(3, 1, 30, ahora);
        comun.players.add(mp(2, "p2", 2, 1400));
        Match otra = terminadaHace(4, 2, 10, ahora);
        PartidasPresenter.Recorrido r = recorrerUno(List.of(jugador(1), jugador(2)), ahora.minusSeconds(86_400), 50, 0,
                (pid, pag, pp) -> pid == 1 ? java.util.Arrays.asList(viva, colgada, null, comun) : List.of(comun, otra),
                m -> m.id == 1, () -> false, s -> { });
        assertEquals(List.of(1L, 4L, 3L), r.lista().stream().map(m -> m.id).toList(), "en directo primero; la colgada fuera; la común una vez");
    }

    @Test void recorrer_unFalloSeCuentaSigueConElSiguienteYLoDice() {
        Instant ahora = Instant.now();
        List<String> progreso = new ArrayList<>();
        PartidasPresenter.Recorrido r = recorrerUno(List.of(jugador(1), jugador(2)), ahora.minusSeconds(86_400), 50, 0,
                (pid, pag, pp) -> { if (pid == 1) throw new java.io.IOException("HTTP 429"); return List.of(terminadaHace(5, pid, 10, ahora)); },
                m -> false, () -> false, progreso::add);
        assertEquals(1, r.fallos());
        assertEquals(Set.of(2L), r.exitosos(), "el que falló no cuenta como consultado");
        assertFalse(r.detenida());
        assertEquals(1, r.lista().size());
        assertEquals(List.of("Consultando j1…", "Aviso: fallo con j1 (HTTP 429)", "Consultando j2…"), progreso);
    }

    @Test void recorrer_topeDe600Partidas() {
        Instant ahora = Instant.now();
        List<Long> pedidos = new ArrayList<>();
        long[] id = { 0 };
        PartidasPresenter.Recorrido r = recorrerUno(List.of(jugador(1), jugador(2), jugador(3)), ahora.minusSeconds(86_400), 200, 0,
                (pid, pag, pp) -> {
                    pedidos.add(pid);
                    List<Match> l = new ArrayList<>();
                    for (int i = 0; i < 200; i++) l.add(terminadaHace(++id[0], pid, 1, ahora));
                    return l;
                },
                m -> false, () -> false, s -> { });
        assertTrue(r.topeAlcanzado());
        assertEquals(600, r.lista().size(), "3 páginas de 200: justo el tope");
        assertEquals(List.of(1L, 1L, 1L), pedidos, "con 600 del primero ya no se pide al segundo");
    }

    // ----- Enter en la tabla (decisión de Jorge, 1.3): sin selección vuelve a avisar -----

    @Test void accionEnter_sinSeleccionAvisa_conSeleccionDescarga_conDescargaEnCursoNada() {
        assertEquals(PartidasPresenter.AccionEnter.AVISAR_SIN_SELECCION, PartidasPresenter.accionEnter(false, 0));
        assertEquals(PartidasPresenter.AccionEnter.DESCARGAR, PartidasPresenter.accionEnter(false, 2));
        assertEquals(PartidasPresenter.AccionEnter.NADA, PartidasPresenter.accionEnter(true, 2), "no lanza una segunda descarga");
        assertEquals(PartidasPresenter.AccionEnter.NADA, PartidasPresenter.accionEnter(true, 0));
    }

    @Test void mensajeSinSeleccion_enLosDosIdiomas() {
        IDIOMA = "es";
        assertEquals("No hay partidas seleccionadas.", PartidasPresenter.mensajeSinSeleccion());
        IDIOMA = "en";
        assertEquals("No games selected.", PartidasPresenter.mensajeSinSeleccion());
    }
}
