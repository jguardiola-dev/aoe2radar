package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.model.PaisItem;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.service.VistaInicial;
import dev.tirador.aoe2radar.util.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * WatchlistPresenter sin Swing: una Pantalla de mentira que dice qué hay elegido en los controles y apunta, en orden,
 * lo que el presentador le pide (repintados, cargas, barridos). Config en un mapa en memoria. Tareas.EN_LINEA donde
 * hay hilos (todo en el acto y en orden).
 */
class WatchlistPresenterTest {

    static final String TOP_PAIS = "★ Top país", TOP_CLAN = "★ Top clan";

    /** La vista de mentira: el combo es un String, los controles son campos; cada petición deja su rastro en «llamadas». */
    static class PantallaFalsa implements WatchlistPresenter.Pantalla {
        String grupo;
        List<String> items = new ArrayList<>();
        String clan;
        final List<String> llamadas = new ArrayList<>();
        @Override public String grupoSeleccionado() { return grupo; }
        @Override public List<String> itemsCombo() { return items; }
        @Override public String clanEscrito() { return clan; }
        @Override public void actualizarBotonesModo() { llamadas.add("botones"); }
        @Override public void rellenarClanSiVacio() { llamadas.add("rellenarClan"); }
        @Override public void cargarTopClan() { llamadas.add("topClan"); }
        @Override public void cargarTopLadder(boolean forzar) { llamadas.add("topLadder:" + forzar); }
        @Override public void refrescarFiltro() { llamadas.add("filtro"); }
        @Override public void refrescarWatchlist() { llamadas.add("barrido"); }
        @Override public void indicadoresVivos() { llamadas.add("indicadores"); }
    }

    PantallaFalsa pantalla;
    List<Player> jugadores;
    Map<String, String> cfg;
    WatchlistPresenter p;
    String idiomaPrevio;
    final PaisItem[] paises = { new PaisItem("Alemania", "de"), new PaisItem("España", "es"), new PaisItem("Francia", "fr"), new PaisItem("Bélgica", "be") };

    @BeforeEach void crear() {
        idiomaPrevio = IDIOMA;
        IDIOMA = "es";
        pantalla = new PantallaFalsa();
        jugadores = new ArrayList<>();
        cfg = new HashMap<>();
        p = new WatchlistPresenter(pantalla, jugadores, Path.of("players_presenter_test.txt"), TOP_PAIS, TOP_CLAN, paises,
                (k, def) -> cfg.getOrDefault(k, def), cfg::put);
    }

    @AfterEach void restaurar() {
        IDIOMA = idiomaPrevio;
        try { Files.deleteIfExists(Config.CONFIG_FILE); } catch (Exception ignored) { }   // fijarPais guarda top_pais con util.Config
    }

    // ===== modo de vista: se lee SIEMPRE de la Pantalla ===============================================================

    @Test void modos_seLeenDelComboEnCadaConsulta() {
        pantalla.grupo = WatchlistView.TOP_LADDER;
        assertTrue(p.modoTop()); assertFalse(p.modoClan()); assertFalse(p.modoPais());
        pantalla.grupo = TOP_PAIS;   // sin avisar al presentador: no guarda el modo
        assertTrue(p.modoTop()); assertTrue(p.modoPais());
        pantalla.grupo = TOP_CLAN;
        assertTrue(p.modoTop()); assertTrue(p.modoClan());
        pantalla.grupo = "Amigos";
        assertFalse(p.modoTop());
        pantalla.grupo = null;
        assertFalse(p.modoTop());
    }

    @Test void grupoActivo_soloGruposDeUsuario() {
        for (String especial : List.of("Todos", WatchlistView.TOP_LADDER, TOP_PAIS, TOP_CLAN, "+ Nuevo grupo…", "Gestionar grupos…")) {
            pantalla.grupo = especial;
            assertNull(p.grupoActivo(), especial);
            assertEquals(WatchlistView.GRUPO_GENERAL, p.grupoDestino(), especial);
        }
        pantalla.grupo = null;
        assertNull(p.grupoActivo());
        pantalla.grupo = "Pros";
        assertEquals("Pros", p.grupoActivo());
        assertEquals("Pros", p.grupoDestino());
    }

    @Test void vistaActualId_grupoYPaisSoloEnTopPais() {
        pantalla.grupo = "Amigos";
        assertEquals("Amigos|", p.vistaActualId());
        pantalla.grupo = null;
        assertEquals("null|", p.vistaActualId(), "como la vista: el item null se concatena tal cual");
        p.paisActual = new PaisItem("Francia", "fr");
        pantalla.grupo = TOP_PAIS;
        assertEquals(TOP_PAIS + "|fr", p.vistaActualId());
    }

    @Test void paisSel_elElegidoOElDeConfig() {
        assertEquals("es", p.paisSel(), "sin país elegido: top_pais de util.Config, por defecto es");
        p.paisActual = new PaisItem("Alemania", "de");
        assertEquals("de", p.paisSel());
    }

    @Test void idVistaCampana_yNombreVistaCampana() {
        pantalla.grupo = "Todos";
        assertEquals("grupo|Todos", p.idVistaCampana());
        assertEquals("Todos", p.nombreVistaCampana());
        pantalla.grupo = WatchlistView.TOP_LADDER;
        assertEquals("★ladder", p.idVistaCampana());
        pantalla.grupo = TOP_PAIS;
        assertEquals("★pais|es", p.idVistaCampana());
        assertEquals(TOP_PAIS + " · ES", p.nombreVistaCampana(), "sin país elegido: el código en mayúsculas");
        p.paisActual = new PaisItem("Francia", "fr");
        assertEquals("★pais|fr", p.idVistaCampana());
        assertEquals(TOP_PAIS + " · Francia", p.nombreVistaCampana());
        pantalla.grupo = TOP_CLAN;
        assertEquals("★clan|", p.idVistaCampana(), "sin campo de clan todavía");
        assertEquals("", p.clanBuscado());
        pantalla.clan = "  TdB ";
        assertEquals("★clan|tdb", p.idVistaCampana());
        assertEquals("TdB", p.clanBuscado());
        assertEquals(TOP_CLAN + " · TdB", p.nombreVistaCampana());
    }

    @Test void clanesGuardados_deLaConfigInyectada() {
        cfg.put("clanes_guardados", " R1 ,, TdB,");
        assertEquals(List.of("R1", "TdB"), p.clanesGuardados());
    }

    @Test void firmaClan_nuncaEsLaDeLadderNiPais() {
        assertEquals("clan|r1", WatchlistPresenter.firmaClan("R1"));
    }

    @Test void limpiarGrupo_sinSeparadoresDeConfig() {
        assertEquals("a b c", WatchlistPresenter.limpiarGrupo("  a;b,c "));
    }

    // ===== el combo cambia de item =====================================================================================

    @Test void grupoElegido_topClan() {
        pantalla.grupo = TOP_CLAN;
        p.grupoElegido();
        assertEquals(TOP_CLAN, cfg.get("grupo_activo"));
        assertEquals(List.of("botones", "rellenarClan", "topClan"), pantalla.llamadas);
    }

    @Test void grupoElegido_topLadderYPais() {
        pantalla.grupo = WatchlistView.TOP_LADDER;
        p.grupoElegido();
        assertEquals(List.of("botones", "topLadder:false"), pantalla.llamadas);
        pantalla.llamadas.clear();
        pantalla.grupo = TOP_PAIS;
        p.grupoElegido();
        assertEquals(TOP_PAIS, cfg.get("grupo_activo"));
        assertEquals(List.of("botones", "topLadder:false"), pantalla.llamadas);
    }

    @Test void grupoElegido_grupoDeUsuario() {
        pantalla.grupo = "Amigos";
        p.grupoElegido();
        assertEquals("Amigos", cfg.get("grupo_activo"));
        assertEquals(List.of("botones", "filtro", "barrido", "indicadores"), pantalla.llamadas);
    }

    // ===== país =========================================================================================================

    @Test void ordenarPaises_elGuardadoPrimeroYElRestoPorNombre() {
        List<PaisItem> orden = p.ordenarPaises("fr");
        assertEquals(List.of("fr", "de", "be", "es"), orden.stream().map(PaisItem::code).toList());
        assertEquals("fr", p.paisActual.code());
        assertSame(orden, p.catalogoOrdenado);
    }

    @Test void paisesQueCoinciden_sinTildesPorNombreOCodigo() {
        p.ordenarPaises("es");
        assertEquals(List.of("be"), p.paisesQueCoinciden(" belg ").stream().map(PaisItem::code).toList(), "«belg» casa con «Bélgica»");
        assertEquals(List.of("de"), p.paisesQueCoinciden("de").stream().map(PaisItem::code).toList(), "por código");
        assertEquals(4, p.paisesQueCoinciden("").size(), "vacío: todos");
    }

    @Test void fijarPais_soloRecargaEnTopPais() {
        pantalla.grupo = "Amigos";
        p.fijarPais(new PaisItem("Alemania", "de"));
        assertEquals("de", p.paisSel());
        assertEquals(List.of(), pantalla.llamadas);
        pantalla.grupo = TOP_PAIS;
        p.fijarPais(new PaisItem("Francia", "fr"));
        assertEquals(List.of("topLadder:true"), pantalla.llamadas);
    }

    // ===== grupos =======================================================================================================

    @Test void indiceGuardado_exactoAntesQueBilingue() {
        List<String> items = List.of("Todos", WatchlistView.TOP_LADDER, TOP_PAIS, TOP_CLAN, "All");
        assertEquals(4, p.indiceGuardado(items, "all"), "hay un grupo de usuario «All»: gana a la traducción de «Todos»");
        assertEquals(0, p.indiceGuardado(List.of("Todos", "Pros"), "All"), "sin grupo «All»: «All» guardado es «Todos»");
        assertEquals(2, p.indiceGuardado(items, "★ Country top"));
        assertEquals(3, p.indiceGuardado(items, "★ Clan top"));
        assertEquals(-1, p.indiceGuardado(items, "Borrado"));
    }

    @Test void itemsFijosYGruposDelCombo() {
        assertEquals(List.of("Todos", WatchlistView.TOP_LADDER, TOP_PAIS, TOP_CLAN), p.itemsFijos());
        pantalla.items = List.of("Todos", WatchlistView.TOP_LADDER, TOP_PAIS, TOP_CLAN, "Amigos", "Pros");
        assertEquals(List.of("Amigos", "Pros"), p.gruposDelCombo());
    }

    @Test void grupoGuardado_porDefectoTodos() {
        assertEquals("Todos", p.grupoGuardado());
        cfg.put("grupo_activo", "Pros");
        assertEquals("Pros", p.grupoGuardado());
    }

    @Test void gruposYPertenencia() {
        jugadores.add(new Player(1L, "Uno", "Amigos"));
        cfg.put("grupos", "Torneo");
        assertTrue(p.containsPlayerId(1L));
        assertFalse(p.containsPlayerId(2L));
        assertEquals("Amigos", p.grupoDeJugador(1L));
        assertEquals(List.of("Amigos", "General", "Torneo"), new ArrayList<>(p.gruposDisponibles()));
        assertTrue(p.esGrupoDeUsuario("Torneo"));
        assertFalse(p.esGrupoDeUsuario("Todos"));
        assertEquals(List.of("Amigos", "Torneo"), new ArrayList<>(p.calcularGrupos()));
    }

    @Test void eleccionInicial_compruebaLoQueExiste() {
        cfg.put("abrir_en", "pais:fr");
        assertEquals(VistaInicial.Tipo.PAIS, p.eleccionInicial().tipo());
        cfg.put("abrir_en", "pais:zz");
        assertEquals(VistaInicial.Tipo.TOP, p.eleccionInicial().tipo(), "un país que no está en el catálogo: ★ Top ladder");
        pantalla.items = List.of("Todos", "Amigos");
        cfg.put("abrir_en", "grupo:amigos");
        assertEquals("Amigos", p.eleccionInicial().valor());
    }
}
