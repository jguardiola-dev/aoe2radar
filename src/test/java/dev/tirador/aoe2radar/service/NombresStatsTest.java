package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.VentanaStats;
import dev.tirador.aoe2radar.techtree.TechTreeDatos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.service.NombresStats.*;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static org.junit.jupiter.api.Assertions.*;

/**
 * NombresStats: los nombres legibles de Civ Stats y Tech tree, movidos tal cual desde SpoilerFreeRecs. Como
 * dependen del idioma (I18n.IDIOMA) y, en dos casos, del catálogo del tech tree (TechTreeDatos.ttData/ttStrings,
 * estáticos y compartidos con la UI), cada test fija lo que necesita y lo restaura al acabar para no contaminar
 * al resto de la suite.
 */
class NombresStatsTest {

    String idiomaPrevio;
    @BeforeEach void fijarIdioma() { idiomaPrevio = IDIOMA; IDIOMA = "es"; }
    @AfterEach void restaurarIdioma() { IDIOMA = idiomaPrevio; }

    Map<String, Object> ttDataAntes, ttStringsAntes;
    @BeforeEach void guardarTechTree() { ttDataAntes = TechTreeDatos.ttData; ttStringsAntes = TechTreeDatos.ttStrings; }
    @AfterEach void restaurarTechTree() { TechTreeDatos.ttData = ttDataAntes; TechTreeDatos.ttStrings = ttStringsAntes; }

    @Test void modoNombreTraduceLosModosConocidosYDejaLosDesconocidosTalCual() {
        assertEquals("1v1 Random Map", modoNombre("rm_1v1"));
        assertEquals("4v4 Random Map", modoNombre("rm_4v4"));
        assertEquals("1v1 Empire Wars", modoNombre("ew_1v1"));
        assertEquals("1v1 Deathmatch", modoNombre("dm_1v1"));
        assertEquals("modo_raro", modoNombre("modo_raro"), "un modo desconocido se devuelve tal cual");
        IDIOMA = "es";
        assertEquals("Equipos Empire Wars", modoNombre("ew_team"));
        assertEquals("Equipos Deathmatch", modoNombre("dm_team"));
        IDIOMA = "en";
        assertEquals("Team Empire Wars", modoNombre("ew_team"));
        assertEquals("Team Deathmatch", modoNombre("dm_team"));
    }

    @Test void ventanaNombreTraduceLasVentanasConocidasYDejaLasDesconocidasTalCual() {
        IDIOMA = "es";
        assertEquals("7 días", ventanaNombre("7"));
        assertEquals("30 días", ventanaNombre("30"));
        assertEquals("365 días", ventanaNombre("365"));
        assertEquals("Parche actual", ventanaNombre("parche"));
        assertEquals("2026", ventanaNombre("2026"), "una ventana desconocida se devuelve tal cual");
        IDIOMA = "en";
        assertEquals("7 days", ventanaNombre("7"));
        assertEquals("Current patch", ventanaNombre("parche"));
    }

    @Test void tramoNombreTodosLosEloYSinRating() {
        assertEquals("Todos los ELO", tramoNombre(null));
        assertEquals("Todos los ELO", tramoNombre("*"));
        assertEquals("Todos los ELO", tramoNombre("*|*"));
        assertEquals("Sin rating", tramoNombre("?"));
    }

    @Test void tramoNombreTramoSimpleCambiaElGuionPorLaRayita() {
        assertEquals("0–1000", tramoNombre("0-1000"));
        assertEquals("2000+", tramoNombre("2000+"), "el tramo abierto por arriba no lleva rayita");
    }

    @Test void tramoNombreRangoDesdeHastaConAmbosExtremosOSoloUno() {
        assertEquals("1000–1800", tramoNombre("1000-1600|1600-1800"), "rango cerrado: extremo bajo del primero, alto del segundo");
        assertEquals("<1000", tramoNombre("*|1000"), "sin límite por abajo");
        assertEquals("1600+", tramoNombre("1600-1800|*"), "sin límite por arriba: el caso del enunciado");
        assertEquals("Todos los ELO", tramoNombre("0-1000|2000+"), "de 0 a «2000+» es, en la práctica, todos los ELO");
    }

    @Test void raizCivQuitaAcentosYPluralParaComparaCivsEnDistintoIdioma() {
        assertEquals("azteca", raizCiv("Aztecas"));
        assertEquals("tupi", raizCiv("Tupís"));
        assertEquals("muisca", raizCiv("Muiscas"));
        assertEquals("frances", raizCiv("Franceses"), "plural en «-es»: se quitan las dos letras, no solo la s");
        assertEquals("ares", raizCiv("Ares"), "4 letras acabadas en s: no se recorta (guarda longitud > 4)");
    }

    @Test void nombreMapaStatsTodosLosMapasYClaveSinVentana() {
        IDIOMA = "es";
        assertEquals("Todos los mapas", nombreMapaStats(null, "*"));
        assertEquals("arabia", nombreMapaStats(null, "rm_arabia"), "sin ventana: se le quita el prefijo de modo y se cambia _ y - por espacios");
        assertEquals("black forest", nombreMapaStats(null, "cm_black_forest"));
    }

    @Test void nombreMapaStatsUsaElNombreDeLaVentanaSiLoTiene() {
        VentanaStats v = ventanaDePrueba(Map.of("arabia", "Arabia", "cm_arena", "Cm Arena"));
        assertEquals("Arabia", nombreMapaStats(v, "arabia"), "si la ventana trae el nombre, se usa tal cual");
        assertEquals("Arena", nombreMapaStats(v, "cm_arena"), "salvo el prefijo «Cm » de los mapas cerrados, que se recorta");
        assertEquals("desert", nombreMapaStats(v, "rm_desert"), "una clave que la ventana no conoce cae al formateo de la clave");
    }

    @Test void nombreCivStatsSinTechTreeCargadoCapitalizaLaClave() {
        TechTreeDatos.ttData = null;
        assertEquals("Aztecs", nombreCivStats("aztecs"));
        assertEquals("", nombreCivStats(""), "una clave vacía se queda vacía");
    }

    @Test void nombreCivStatsConTechTreeCargadoUsaElNombreTraducido() {
        TechTreeDatos.ttData = Map.of("civs", Map.of("Britons", Map.of("name_string_id", 100)));
        TechTreeDatos.ttStrings = Map.of("100", "Britones");
        assertEquals("Britones", nombreCivStats("britons"), "la clave del companion es minúscula; la del tech tree, no");
        assertEquals("Franks", nombreCivStats("franks"), "sin traducción en ttStrings: capitalizado, como si no hubiera tech tree");
    }

    @Test void claveTechTreeSinDatosDevuelveNull() {
        TechTreeDatos.ttData = null;
        assertNull(claveTechTree("britons"));
    }

    @Test void claveTechTreeConDatosDevuelveLaClaveDelTechTree() {
        TechTreeDatos.ttData = Map.of("civs", Map.of("Britons", Map.of("name_string_id", 100)));
        assertEquals("Britons", claveTechTree("britons"));
        assertNull(claveTechTree("no_existe"));
    }

    static VentanaStats ventanaDePrueba(Map<String, String> nombresMapas) {
        return new VentanaStats("30 días", "2026-08-01", "2026-08-30", 30, "", List.of(), Map.of(), nombresMapas, Map.of(), List.of(), List.of());
    }
}
