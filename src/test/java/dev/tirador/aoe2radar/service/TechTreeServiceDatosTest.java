package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.techtree.TechTreeDatos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TechTreeServiceDatos contra TechTreeDatos con datos puestos a mano: sin red, comparando siempre con la
 * llamada directa a TechTreeDatos (misma salida). ttData/ttStrings/ttTrees son estáticos y compartidos con la
 * UI y el harness de capturas (ver TechTreeDatos), así que se guardan antes y se restauran después de cada
 * test para no contaminar el resto de la suite.
 * <p>No se prueban asegurarDatos(), comprobarActualizacion() ni descargar(rel): las tres van a la red.
 */
class TechTreeServiceDatosTest {

    final TechTreeService tt = TechTreeServiceDatos.SISTEMA;

    Map<String, Object> datosOriginal;
    Map<String, Object> stringsOriginal;
    Map<String, Map<String, Object>> arbolesOriginal;

    @BeforeEach void guardar() {
        datosOriginal = TechTreeDatos.ttData;
        stringsOriginal = TechTreeDatos.ttStrings;
        arbolesOriginal = new HashMap<>(TechTreeDatos.ttTrees);
    }

    @AfterEach void restaurar() {
        TechTreeDatos.ttData = datosOriginal;
        TechTreeDatos.ttStrings = stringsOriginal;
        TechTreeDatos.ttTrees.clear();
        TechTreeDatos.ttTrees.putAll(arbolesOriginal);
    }

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test void datosDevuelveElMismoMapaQueTtDataYNullSiNoHay() {
        TechTreeDatos.ttData = null;
        assertNull(tt.datos());

        Map<String, Object> datos = obj("civs", obj());
        TechTreeDatos.ttData = datos;
        assertSame(datos, tt.datos());
    }

    @Test void arbolEnCacheYArbolCacheadoReflejanTtTrees() {
        TechTreeDatos.ttTrees.clear();
        assertFalse(tt.arbolEnCache("aztecs"));
        assertNull(tt.arbolCacheado("aztecs"));

        Map<String, Object> arbol = obj("id", "aztecs");
        TechTreeDatos.ttTrees.put("aztecs", arbol);
        assertTrue(tt.arbolEnCache("aztecs"));
        assertSame(arbol, tt.arbolCacheado("aztecs"));
        assertFalse(tt.arbolEnCache("britons"));
    }

    @Test void arbolYaEnCacheNoVaARedYDevuelveElMismoObjeto() throws Exception {
        TechTreeDatos.ttTrees.clear();
        Map<String, Object> arbol = obj("id", "aztecs");
        TechTreeDatos.ttTrees.put("aztecs", arbol);
        assertSame(arbol, tt.arbol("aztecs"), "ya está en caché: no hace falta ir a la red a descargarlo");
    }

    @Test void dirDevuelveLaMismaCarpetaQueTtDir() {
        assertEquals(TechTreeDatos.TT_DIR, tt.dir());
    }

    @Test void rutaIconoCoincideConTtRutaIcono() {
        assertEquals(TechTreeDatos.ttRutaIcono("Building", 123), tt.rutaIcono("Building", 123));
        assertEquals(TechTreeDatos.ttRutaIcono("Civs", 0), tt.rutaIcono("Civs", 0));
    }

    @Test void claseCoincideConTtClaseParaIdConocidoYDesconocido() {
        assertEquals(TechTreeDatos.ttClase(1), tt.clase(1));      // conocido: "Infantry"/"Infantería"
        assertEquals(TechTreeDatos.ttClase(9999), tt.clase(9999)); // desconocido: "#9999"
    }

    @Test void strYNombreCoincidenConTtStrYTtNombre() {
        TechTreeDatos.ttStrings = obj("100", "Hola<br>Mundo", "200", "Sin saltos");
        assertNull(tt.str(null));
        assertEquals(TechTreeDatos.ttStr(100), tt.str(100));
        assertEquals(TechTreeDatos.ttStr("200"), tt.str("200"));
        assertEquals(TechTreeDatos.ttNombre(100), tt.nombre(100));
        assertEquals("Hola Mundo", tt.nombre(100), "ttNombre limpia <br> y saltos de linea");

        TechTreeDatos.ttStrings = null;
        assertNull(tt.str(100));
        assertEquals(TechTreeDatos.ttNombre(999), tt.nombre(999));
        assertEquals("?", tt.nombre(999), "sin cadena: \"?\"");
    }

    @Test void nombreCivCoincideConTtNombreCivConYSinTraduccion() {
        TechTreeDatos.ttStrings = obj("500", "Aztecas");
        TechTreeDatos.ttData = obj("civs", obj("aztecs", obj("name_string_id", 500)));
        assertEquals(TechTreeDatos.ttNombreCiv("aztecs"), tt.nombreCiv("aztecs"));
        assertEquals("Aztecas", tt.nombreCiv("aztecs"));

        // civ sin entrada en ttData.civs: se devuelve la clave tal cual.
        assertEquals(TechTreeDatos.ttNombreCiv("britons"), tt.nombreCiv("britons"));
        assertEquals("britons", tt.nombreCiv("britons"));

        TechTreeDatos.ttData = null;
        assertEquals(TechTreeDatos.ttNombreCiv("aztecs"), tt.nombreCiv("aztecs"));
        assertEquals("aztecs", tt.nombreCiv("aztecs"), "sin ttData: la clave tal cual");
    }
}
