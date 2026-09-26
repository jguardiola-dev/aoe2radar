package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.service.VistaInicial.Eleccion;
import dev.tirador.aoe2radar.service.VistaInicial.Tipo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** «Abrir en»: qué vista se elige al arrancar según la config (decisión de Jorge, 2026-09-26). Sin Swing ni disco. */
class VistaInicialTest {

    static final Set<String> PAISES = Set.of("es", "fr", "de");
    static final List<String> CLANES = List.of("R1", "TdB");
    static final List<String> GRUPOS = List.of("Amigos", "General", "Pros");

    static Eleccion decidir(String guardado) {
        return VistaInicial.resolver(VistaInicial.leer(guardado), PAISES, CLANES, GRUPOS);
    }

    @Test void porDefecto_topLadder() {
        assertEquals(Eleccion.TOP_LADDER, decidir(null), "sin la clave (instalaciones de antes): como siempre, ★ Top ladder");
        assertEquals(Eleccion.TOP_LADDER, decidir(""));
        assertEquals(Eleccion.TOP_LADDER, decidir("top"));
    }

    @Test void cadaTipoGuardadoAbreEnSuVista() {
        assertEquals(new Eleccion(Tipo.PAIS, "es"), decidir("pais:es"));
        assertEquals(new Eleccion(Tipo.CLAN, "R1"), decidir("clan:R1"));
        assertEquals(new Eleccion(Tipo.GRUPO, "Amigos"), decidir("grupo:Amigos"));
        assertEquals(Eleccion.TODOS_LOS_GRUPOS, decidir("todos"));
    }

    @Test void siLoGuardadoYaNoExiste_caeATopLadder() {
        assertEquals(Eleccion.TOP_LADDER, decidir("grupo:Borrado"), "grupo borrado");
        assertEquals(Eleccion.TOP_LADDER, decidir("clan:DK"), "clan quitado de los guardados");
        assertEquals(Eleccion.TOP_LADDER, decidir("pais:zz"), "código de país desconocido");
    }

    @Test void valoresRarosOIncompletos_caenATopLadder() {
        assertEquals(Eleccion.TOP_LADDER, decidir("grupo:"));
        assertEquals(Eleccion.TOP_LADDER, decidir("pais:  "));
        assertEquals(Eleccion.TOP_LADDER, decidir("otra:cosa"));
        assertEquals(Eleccion.TOP_LADDER, decidir("Amigos"), "sin tipo no se adivina");
    }

    @Test void mayusculas_seComparanSinDistinguirYSeDevuelvenLasDeHoy() {
        assertEquals(new Eleccion(Tipo.GRUPO, "Amigos"), decidir("grupo:amigos"));
        assertEquals(new Eleccion(Tipo.CLAN, "TdB"), decidir("clan:tdb"));
        assertEquals(new Eleccion(Tipo.PAIS, "es"), decidir("pais:ES"), "el ejemplo de Jorge, con el código en mayúsculas");
    }

    @Test void soloElPrimerDosPuntosSepara() {
        Eleccion e = VistaInicial.resolver(VistaInicial.leer("grupo:Liga: 2026"), PAISES, CLANES, List.of("Liga: 2026"));
        assertEquals(new Eleccion(Tipo.GRUPO, "Liga: 2026"), e);
    }

    @Test void idaYVueltaPorConfig() {
        for (Eleccion e : List.of(Eleccion.TOP_LADDER, Eleccion.TODOS_LOS_GRUPOS, new Eleccion(Tipo.PAIS, "fr"),
                new Eleccion(Tipo.CLAN, "R1"), new Eleccion(Tipo.GRUPO, "Pros")))
            assertEquals(e, decidir(e.aConfig()), e.aConfig());
    }

    @Test void buscarAlAbrir_soloEnUnGrupoOTodos() {
        assertTrue(decidir("grupo:Amigos").buscaAlAbrir());
        assertTrue(decidir("todos").buscaAlAbrir());
        assertFalse(decidir("top").buscaAlAbrir());
        assertFalse(decidir("pais:es").buscaAlAbrir());
        assertFalse(decidir("clan:R1").buscaAlAbrir());
        assertFalse(decidir("grupo:Borrado").buscaAlAbrir(), "si el grupo ya no existe se abre en ★: tampoco se busca");
    }
}
