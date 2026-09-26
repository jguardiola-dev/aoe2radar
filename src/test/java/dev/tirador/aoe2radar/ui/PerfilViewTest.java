package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.util.Reloj;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.tirador.aoe2radar.util.Config.CONFIG_FILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PerfilView con componentes Swing reales dentro de invokeAndWait, nunca visibles (como RatingsViewTest y
 * CivStatsViewTest): no hace falta pantalla. Sin red: los dobles de servicios de RatingsViewTest y
 * CivStatsViewTest, y Tareas.EN_LINEA. menus y techTree van a null: construirPanelPerfil no los toca y los
 * escenarios de aquí no pintan listas de civs.
 */
class PerfilViewTest {

    /** El anfitrión de mentira: solo apunta los mensajes que la vista manda a la barra general. */
    static class AnfitrionFalso implements PerfilView.Anfitrion {
        final List<String> estadosGlobales = new ArrayList<>();
        @Override public List<Player> seleccionWatchlist() { return List.of(); }
        @Override public boolean estaEnWatchlist(long pid) { return false; }
        @Override public String paisDe(long pid) { return ""; }
        @Override public String nombreVisible(long pid, String nombre) { return nombre; }
        @Override public void ficharDesdeTop(long pid, String nombre, String grupo) { }
        @Override public List<String> gruposDeJugadores() { return List.of(); }
        @Override public List<String> gruposGuardados() { return List.of(); }
        @Override public String grupoGeneral() { return "General"; }
        @Override public boolean ultimoClicFueCtrl() { return false; }
        @Override public void pedirAlias(long pid, String nombreOriginal) { }
        @Override public void pedirNota(long pid, String nombre) { }
        @Override public void borrarNota(long pid, String nombre) { }
        @Override public void mostrarVinculadas(long pid, String nombre) { }
        @Override public void nicksAnteriores(long pid, String nombre) { }
        @Override public void abrirUrl(String url) { }
        @Override public void registrarDestino(long pid, String nombre) { }
        @Override public void actualizarTextoBuscar() { }
        @Override public JToggleButton crearBotonPestana(String texto, Icon icono) { return new JToggleButton(texto, icono); }
        @Override public void traerAlFrente() { }
        @Override public void mostrarEstadoGlobal(String texto) { estadosGlobales.add(texto); }
        @Override public void cerrarPerfil() { }
        @Override public boolean enCursoReal(Match m) { return false; }
        @Override public boolean confirmarEspectar(String nombre) { return false; }
        @Override public void espectarPartida(long matchId) { }
        @Override public void cargarPartidasEnTabla(List<Match> partidas, Player sujeto) { }
        @Override public void buscarPartidasDe(long pid, String nombre) { }
        @Override public void descargarSinCambiarVista(List<Match> partidas, boolean enviar, Runnable alTerminar) { }
    }

    /** ProfileService de mentira: el de PerfilPresenterTest (ficha y ficha conocida configurables). */
    final PerfilesContados perfiles = new PerfilesContados();

    static class PerfilesContados extends PerfilPresenterTest.PerfilesFalso {
        // como el servicio real: tras vinculadasConElo, las vinculadas ya se conocen (el doble base devuelve null
        // siempre y, con Tareas.EN_LINEA, la cabecera volvería a pedirlas sin fin)
        boolean pedidas;
        int anioSfrLlamadas, fichaLlamadas;
        @Override public dev.tirador.aoe2radar.model.AnioSfr anioSfr(long pid, String nombreSiFalta) throws Exception { anioSfrLlamadas++; return super.anioSfr(pid, nombreSiFalta); }
        @Override public FichaPerfil ficha(long pid) { fichaLlamadas++; return super.ficha(pid); }
        @Override public List<dev.tirador.aoe2radar.model.Perfil.Vinculada> vinculadasConElo(long pid) { pedidas = true; return List.of(); }
        @Override public List<dev.tirador.aoe2radar.model.Perfil.Vinculada> vinculadasConocidas(long pid) { return pedidas ? List.of() : null; }
    }
    final AnfitrionFalso anfitrion = new AnfitrionFalso();
    final Map<Long, Actividad> actividadCache = new ConcurrentHashMap<>();

    @BeforeEach @AfterEach
    void limpiarConfig() throws Exception { Files.deleteIfExists(CONFIG_FILE); }

    /** Construye la vista en el EDT (hay que llamarlo dentro de invokeAndWait). */
    PerfilView vista() { return vista(Tareas.EN_LINEA); }

    PerfilView vista(Tareas tareas) {
        return new PerfilView(perfiles, new RatingsViewTest.RatingsServiceFalso(), new RatingsViewTest.BusquedaFalsa(), new CivStatsViewTest.StatsFalso(),
                new EstadoVivo(Reloj.SISTEMA), null, new CivStatsViewTest.NavegacionFalsa(), null, new Listas(null, b -> { }),
                tareas, anfitrion, actividadCache, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), pid -> null, Path.of("perfiles-test-inexistente"), pid -> null, 365);
    }

    static FichaPerfil ficha(String pais, int elo) { return new FichaPerfil(Map.of("rm_1v1", new int[]{ elo, 10, elo, 5, 5 }), pais, "", 10); }

    /** F1 (1.3): la barra de estado de la ventana está oculta con Perfil abierto; el fallo de «Actualizar hoy» tiene
     *  que verse en la etiqueta propia del perfil (antes el botón volvía a «Actualizar hoy» sin decir nada). */
    @Test void errorDeActualizarHoySeVeEnElEstadoDelPerfil() throws Exception {
        String[] estado = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.actPid = 5L;
            v.hoyError("HTTP 429");
            estado[0] = v.actEstado.getText();
        });
        assertEquals("No se pudo actualizar: HTTP 429", estado[0]);
        assertEquals(List.of("No se pudo actualizar: HTTP 429"), anfitrion.estadosGlobales, "y también en la barra general, como antes");
    }

    /** F1: «Cara a cara…» sin historial cargado avisaba solo en la barra oculta: no pasaba nada a la vista. */
    @Test void caraACaraSinHistorialAvisaEnElEstadoDelPerfil() throws Exception {
        String[] estado = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.actPid = 5L;   // sin actividad en caché
            new CaraACaraDialogo(null, v).mostrar();
            estado[0] = v.actEstado.getText();
        });
        assertEquals("Abre primero un perfil con historial cargado.", estado[0]);
    }

    /** D3 (1.3): una partida sin hora de inicio (AnioDesdeSfr pone started=null si ini<=0; traerHoy tampoco la
     *  filtra) hacía saltar una NullPointerException en actPintar, en el EDT, y el perfil quedaba a medias. */
    @Test void partidaSinHoraDeInicioNoRevientaElPintado() throws Exception {
        Match sinHora = new Match();
        sinHora.id = 1; sinHora.mode = "1v1 Random Map"; sinHora.map = "Arabia"; sinHora.started = null;
        dev.tirador.aoe2radar.model.MatchPlayer yo = new dev.tirador.aoe2radar.model.MatchPlayer();
        yo.id = 5L; yo.name = "Fulano"; yo.team = 1;
        sinHora.players.add(yo);
        Match conHora = new Match();
        conHora.id = 2; conHora.mode = "1v1 Random Map"; conHora.map = "Arabia"; conHora.started = java.time.Instant.now().minusSeconds(3600);
        conHora.players.add(yo);
        Actividad a = new Actividad(5L, "Fulano", List.of(conHora, sinHora), true, 1, System.currentTimeMillis());
        actividadCache.put(5L, a);
        String[] estado = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.actPid = 5L; v.actNombre = "Fulano";
            v.desdeSfr(a, "2026-09-24");   // pinta el cuerpo entero (actPintar)
            estado[0] = v.actEstado.getText();
        });
        assertEquals("2 partidas · último año · de sfr-data", estado[0], "el pintado llegó al final");
    }

    static Actividad fresca(long pid, String nombre) { return new Actividad(pid, nombre, List.of(), true, 1, System.currentTimeMillis()); }

    /** F4 (1.3): A se abre desde sfr-data (con «Actualizar hoy» y «Datos hasta…»), luego B por la API, y se vuelve a
     *  A, que está fresco en la caché: la salida temprana de baseLista debe enseñar el estado de A, no el de B. */
    @Test void reabrirDesdeLaCacheEnsenaElEstadoDeEsePerfil() throws Exception {
        Actividad a = fresca(5L, "Fulano"), b = fresca(6L, "Mengano");
        boolean[] visible = new boolean[3]; String[] hasta = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.actPid = 5L; v.actNombre = "Fulano";
            actividadCache.put(5L, a);
            v.desdeSfr(a, "2026-09-24");
            visible[0] = v.actHoyBtn.isVisible();
            v.actPid = 6L; v.actNombre = "Mengano";   // B, por la API
            actividadCache.put(6L, b);
            v.cargaIniciada();
            visible[1] = v.actHoyBtn.isVisible();
            v.actPid = 5L; v.actNombre = "Fulano";   // vuelta a A, fresco y completo en la caché
            v.baseLista(a, ficha("es", 1500));
            visible[2] = v.actHoyBtn.isVisible();
            hasta[0] = v.actHastaLabel.getText();
        });
        assertTrue(visible[0]);
        assertFalse(visible[1], "B viene de la API: sin «Actualizar hoy»");
        assertTrue(visible[2], "de vuelta en A, su «Actualizar hoy» vuelve a estar");
        assertTrue(hasta[0].startsWith("Datos hasta el 2026-09-24"), hasta[0]);
    }

    /** F4: al revés, un perfil de la API reabierto desde la caché no hereda el botón del perfil de sfr-data anterior. */
    @Test void reabrirDesdeLaCacheUnPerfilDeLaApiNoHeredaElBoton() throws Exception {
        Actividad a = fresca(5L, "Fulano"), b = fresca(6L, "Mengano");
        boolean[] visible = new boolean[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.actPid = 5L; actividadCache.put(5L, a);
            v.desdeSfr(a, "2026-09-24");
            v.actPid = 6L; actividadCache.put(6L, b);   // B ya estaba en la caché (p. ej. se cargó antes por la API)
            v.baseLista(b, ficha("es", 1400));
            visible[0] = v.actHoyBtn.isVisible();
        });
        assertFalse(visible[0]);
    }

    /** F4: se deja A con su «Actualizar hoy» en marcha y se vuelve a él (desde la caché) antes de que termine: el
     *  botón sigue en «Actualizando…», deshabilitado, y no se puede lanzar otro. */
    @Test void volverAUnPerfilConActualizarHoyEnMarcha() throws Exception {
        PerfilPresenterTest.TareasAplazadas tareas = new PerfilPresenterTest.TareasAplazadas();
        Actividad a = fresca(5L, "Fulano"), b = fresca(6L, "Mengano");
        String[] texto = new String[2]; boolean[] habilitado = new boolean[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista(tareas);
            v.actPid = 5L; actividadCache.put(5L, a);
            v.desdeSfr(a, "2026-09-24");
            v.actHoyBtn.doClick();   // en marcha (el hilo perfil-hoy queda aplazado)
            v.actPid = 6L; actividadCache.put(6L, b);
            v.baseLista(b, ficha("es", 1400));
            v.actPid = 5L;
            v.baseLista(a, ficha("es", 1500));
            texto[0] = v.actHoyBtn.getText(); habilitado[0] = v.actHoyBtn.isEnabled();
            tareas.pendientesFondo.get(0).run();   // termina
            texto[1] = v.actHoyBtn.getText();
        });
        assertEquals("Actualizando…", texto[0]);
        assertFalse(habilitado[0]);
        assertEquals("Al día · sin partidas nuevas", texto[1]);
    }

    /** F6 (1.3): la gráfica de «1v1 Death Match» no se ancla en el ELO de RM 1v1 de la ficha (no hay ladder de DM):
     *  termina en el ELO de su partida más reciente. */
    @Test void graficaDeDeathMatchSeAnclaEnSuUltimaPartida() {
        java.time.LocalDate hoy = java.time.LocalDate.of(2026, 9, 26);
        long[] ultima = { 0L, 1300L, 12L };
        assertEquals(1312L, PerfilView.anclaGraficaElo("1v1 Death Match", ultima, ficha("es", 1900), null, hoy));
        assertEquals(1900L, PerfilView.anclaGraficaElo("1v1 Random Map", ultima, ficha("es", 1900), null, hoy), "RM sigue anclado en la ficha");
        assertEquals(1312L, PerfilView.anclaGraficaElo("1v1 Random Map", ultima, null, null, hoy), "sin ficha, la partida más reciente");
    }

    /** F6: con un «Rango de fechas…» que termina antes de hoy, la curva acaba en el ELO de entonces, no en el de hoy. */
    @Test void graficaDeUnPeriodoPasadoSeAnclaEnSuUltimaPartida() {
        java.time.LocalDate hoy = java.time.LocalDate.of(2026, 9, 26);
        long[] ultima = { 0L, 1700L, -15L };
        assertEquals(1685L, PerfilView.anclaGraficaElo("1v1 Random Map", ultima, ficha("es", 1900), hoy.minusDays(60), hoy));
        assertEquals(1900L, PerfilView.anclaGraficaElo("1v1 Random Map", ultima, ficha("es", 1900), hoy, hoy), "un rango que acaba hoy sí usa la ficha");
    }

    @Test void ladderDeModo() {
        assertEquals("rm_1v1", PerfilView.ladderDeModo("1v1 Random Map"));
        assertEquals("rm_team", PerfilView.ladderDeModo("Team Random Map"));
        assertEquals("ew_1v1", PerfilView.ladderDeModo("1v1 Empire Wars"));
        assertEquals("ew_team", PerfilView.ladderDeModo("Team Empire Wars"));
        assertEquals(null, PerfilView.ladderDeModo("1v1 Death Match"));
        assertEquals(null, PerfilView.ladderDeModo("Team Death Match"));
        assertEquals(null, PerfilView.ladderDeModo(null));
    }

    /** F7 (1.3): pulsar el título de una lista para cambiar el orden repinta a quien la pintó (el diálogo Cara a
     *  cara pasa su propio repintado); antes llamaba siempre a actPintar, el perfil de detrás. */
    @Test void cambiarElOrdenDeUnaListaRepintaAQuienLaPinto() throws Exception {
        int[] repintados = new int[1]; String[] orden = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            javax.swing.JPanel panel = new javax.swing.JPanel();
            Map<String, int[]> datos = new HashMap<>(Map.of("Arabia", new int[]{ 5, 3 }, "Arena", new int[]{ 4, 1 }));
            v.pintarListaAgg(panel, "Winrate por mapa · prueba", datos, 5, k -> k, null, null, () -> repintados[0]++);
            javax.swing.JLabel cab = (javax.swing.JLabel) panel.getComponent(0);
            java.awt.event.MouseEvent clic = new java.awt.event.MouseEvent(cab, java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 5, 5, 1, false);
            for (java.awt.event.MouseListener ml : cab.getMouseListeners()) ml.mouseClicked(clic);
            orden[0] = String.valueOf(v.ordenListas.get("Winrate por mapa · prueba"));
        });
        assertEquals(1, repintados[0], "se repinta la lista de quien la pintó");
        assertEquals("1", orden[0], "y el orden pasa a «por winrate»");
    }

    /** F10 (1.3): A y B en dos pestañas; se cierra la de A; «atrás» hasta A la abre en una pestaña nueva, sin
     *  sustituir la de B (antes B desaparecía). Si la pestaña no se cerró sino que se reutilizó, sigue como siempre. */
    @Test void volverAtrasAUnPerfilDePestanaCerradaAbrePestanaNueva() throws Exception {
        actividadCache.put(1L, fresca(1L, "A")); actividadCache.put(2L, fresca(2L, "B")); actividadCache.put(3L, fresca(3L, "C"));
        perfiles.ficha = ficha("es", 1500);
        List<String> tras = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.alAbrir(1L, "A");
            v.perfilPestanaActiva = -1; v.alAbrir(2L, "B");   // Ctrl+clic: B en pestaña nueva
            v.cerrarPestana(0);                                 // × en la pestaña de A
            v.alAbrir(2L, "B");                                 // lo que haría la ventana al cerrar (navegacion.abrirPerfil)
            v.antesDeVolverPorHistorial(1L); v.alAbrir(1L, "A"); // «atrás» hasta A
            for (Object[] p : v.perfilPestanas) tras.add((String) p[1]);
            tras.add("activa=" + v.perfilPestanaActiva);
            v.alAbrir(3L, "C");                                 // clic sin Ctrl: C sustituye a A en su pestaña
            v.antesDeVolverPorHistorial(1L); v.alAbrir(1L, "A"); // «atrás» hasta A: A no se cerró, se reutilizó
            for (Object[] p : v.perfilPestanas) tras.add((String) p[1]);
        });
        assertEquals(List.of("B", "A", "activa=1", "B", "A"), tras);
    }

    /** F4 (2): tras «Actualizar hoy», reabrir el mismo perfil desde la caché (p. ej. pulsar otra vez la pestaña
     *  Perfil) no vuelve a ofrecer «Actualizar hoy» ni a decir «Datos hasta el <volcado>»: los datos ya son de hoy. */
    @Test void reabrirTrasActualizarHoyConservaElEstado() throws Exception {
        Actividad a = fresca(5L, "Fulano");
        perfiles.traerHoyResultado = 0;
        String[] texto = new String[2]; boolean[] habilitado = new boolean[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.actPid = 5L; actividadCache.put(5L, a);
            v.desdeSfr(a, "2026-09-24");
            v.actHoyBtn.doClick();   // EN_LINEA: termina en el acto
            v.baseLista(a, ficha("es", 1500));   // reabrir desde la caché
            texto[0] = v.actHoyBtn.getText(); habilitado[0] = v.actHoyBtn.isEnabled(); texto[1] = v.actHastaLabel.getText();
        });
        assertEquals("Al día · sin partidas nuevas", texto[0]);
        assertFalse(habilitado[0]);
        assertEquals("Datos hasta hoy", texto[1]);
    }

    /** F4 (3): se deja A durante «Actualizando…», termina con B abierto y se vuelve a A desde la caché: el botón
     *  queda como «Al día…», deshabilitado (antes salía habilitado, porque hoyTerminado no llegó a A). */
    @Test void actualizarHoyQueTerminaConOtroPerfilAbiertoSeConserva() throws Exception {
        PerfilPresenterTest.TareasAplazadas tareas = new PerfilPresenterTest.TareasAplazadas();
        Actividad a = fresca(5L, "Fulano"), b = fresca(6L, "Mengano");
        String[] texto = new String[1]; boolean[] habilitado = new boolean[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista(tareas);
            v.actPid = 5L; actividadCache.put(5L, a);
            v.desdeSfr(a, "2026-09-24");
            v.actHoyBtn.doClick();
            v.actPid = 6L; actividadCache.put(6L, b);
            v.baseLista(b, ficha("es", 1400));
            tareas.pendientesFondo.get(0).run();   // termina con B abierto
            v.actPid = 5L;
            v.baseLista(a, ficha("es", 1500));
            texto[0] = v.actHoyBtn.getText(); habilitado[0] = v.actHoyBtn.isEnabled();
        });
        assertEquals("Al día · sin partidas nuevas", texto[0]);
        assertFalse(habilitado[0]);
    }

    /** F4 (3): un perfil que venía de sfr-data y se vuelve a cargar por la API deja de tener «Actualizar hoy» al
     *  reabrirlo desde la caché (antes conservaba la fecha «hasta» vieja). */
    @Test void recargarPorLaApiOlvidaElOrigenSfr() throws Exception {
        Actividad a = fresca(5L, "Fulano");
        boolean[] visible = new boolean[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.actPid = 5L; actividadCache.put(5L, a);
            v.desdeSfr(a, "2026-09-24");
            v.cargaIniciada();            // se vuelve a cargar A...
            v.cargaCompletada(a);         // ...y llega por la API
            v.baseLista(a, ficha("es", 1500));
            visible[0] = v.actHoyBtn.isVisible();
        });
        assertFalse(visible[0]);
    }

    /** F4 (3): el año de un rival traído de sfr-data por el Cara a cara (recordarOrigenSfr) da «Actualizar hoy» a
     *  su perfil al abrirlo desde la caché. */
    @Test void rivalTraidoDeSfrDataPorElCaraACaraTieneActualizarHoy() throws Exception {
        Actividad r = fresca(9L, "Rival");
        boolean[] visible = new boolean[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            actividadCache.put(9L, r);
            v.recordarOrigenSfr(9L, "2026-09-24");
            v.actPid = 9L;
            v.baseLista(r, ficha("es", 1500));
            visible[0] = v.actHoyBtn.isVisible();
        });
        assertTrue(visible[0]);
    }

    /** Busca en el árbol de componentes una etiqueta cuyo texto empiece así. */
    static javax.swing.JLabel etiqueta(java.awt.Container c, String prefijo) {
        for (java.awt.Component x : c.getComponents()) {
            if (x instanceof javax.swing.JLabel l && l.getText() != null && l.getText().startsWith(prefijo)) return l;
            if (x instanceof java.awt.Container k) { javax.swing.JLabel r = etiqueta(k, prefijo); if (r != null) return r; }
        }
        return null;
    }

    /** B2 (revisión de F7): en el diálogo Cara a cara, pulsar el título de una lista para cambiar el orden repinta
     *  las listas sin volver a pedir el año del rival a sfr-data ni su ficha a la API. */
    @Test void reordenarEnCaraACaraNoHaceLecturasNiLlamadas() throws Exception {
        Match m = new Match();
        m.id = 1; m.mode = "1v1 Random Map"; m.map = "Arabia"; m.started = java.time.Instant.now().minusSeconds(7200); m.finished = m.started.plusSeconds(1800);
        dev.tirador.aoe2radar.model.MatchPlayer yo = new dev.tirador.aoe2radar.model.MatchPlayer(); yo.id = 5L; yo.name = "Fulano"; yo.team = 1; yo.won = true; yo.civ = "Francos";
        dev.tirador.aoe2radar.model.MatchPlayer el = new dev.tirador.aoe2radar.model.MatchPlayer(); el.id = 9L; el.name = "Rival"; el.team = 2; el.won = false; el.civ = "Mayas";
        m.players.add(yo); m.players.add(el);
        actividadCache.put(5L, new Actividad(5L, "Fulano", List.of(m), true, 1, System.currentTimeMillis()));
        perfiles.anioSfr = null;   // el rival no está en sfr-data
        perfiles.ficha = null;     // y su ficha no se conoce
        int[] antes = new int[2], despues = new int[2]; String[] titulo = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.actPid = 5L; v.actNombre = "Fulano";
            CaraACaraDialogo d = new CaraACaraDialogo(null, v);
            d.construir();
            d.fijarParaTest(9L, "Rival");
            antes[0] = perfiles.anioSfrLlamadas; antes[1] = perfiles.fichaLlamadas;
            javax.swing.JLabel cab = etiqueta(d.cuerpoParaTest(), "Winrate por mapa · Fulano");
            java.awt.event.MouseEvent clic = new java.awt.event.MouseEvent(cab, java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 5, 5, 1, false);
            for (java.awt.event.MouseListener ml : cab.getMouseListeners()) ml.mouseClicked(clic);
            despues[0] = perfiles.anioSfrLlamadas; despues[1] = perfiles.fichaLlamadas;
            titulo[0] = etiqueta(d.cuerpoParaTest(), "Winrate por mapa · Fulano").getText();
        });
        assertTrue(antes[0] >= 1 && antes[1] >= 1, "al fijar el cruce sí se pidieron");
        assertEquals(antes[0], despues[0], "reordenar no vuelve a pedir el año del rival");
        assertEquals(antes[1], despues[1], "reordenar no vuelve a pedir la ficha");
        assertTrue(titulo[0].contains("por winrate"), "la lista sí se repintó con el orden nuevo: " + titulo[0]);
    }

    /** F9 (1.3): «Actualizar hoy» termina sin ficha (la API de la ficha falló y no había ninguna conocida): la
     *  cabecera que ya estaba pintada no se sustituye por «Sin datos de perfil». */
    @Test void actualizarHoySinFichaNoBorraLaCabecera() throws Exception {
        String[] sub = new String[2];
        SwingUtilities.invokeAndWait(() -> {
            PerfilView v = vista();
            v.actPid = 5L; v.actNombre = "Fulano";
            v.cabecera(ficha("es", 1500));
            sub[0] = v.actSubtitulo.getText();
            v.hoyTerminado(null, 0);
            sub[1] = v.actSubtitulo.getText();
        });
        assertEquals("ES  ·  10 partidas en total", sub[0]);
        assertEquals(sub[0], sub[1], "la cabecera sigue siendo la de antes");
    }

    /** v13_textos 14: la letra de la racha del chip de forma iba fija en español («racha 3V» también en inglés). */
    @Test void laLetraDeLaRachaSigueElIdioma() {
        String antes = dev.tirador.aoe2radar.util.I18n.IDIOMA;
        try {
            dev.tirador.aoe2radar.util.I18n.IDIOMA = "es";
            assertEquals("V", PerfilView.letraRacha(true)); assertEquals("D", PerfilView.letraRacha(false));
            dev.tirador.aoe2radar.util.I18n.IDIOMA = "en";
            assertEquals("W", PerfilView.letraRacha(true)); assertEquals("L", PerfilView.letraRacha(false));
        } finally { dev.tirador.aoe2radar.util.I18n.IDIOMA = antes; }
    }
}
