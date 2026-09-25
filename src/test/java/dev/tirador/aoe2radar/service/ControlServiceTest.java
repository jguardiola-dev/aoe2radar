package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.Freno;
import dev.tirador.aoe2radar.api.Transporte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** El mando a distancia (control.json) y la comprobación de versión: sin red (transporte falso). */
class ControlServiceTest {

    static final class TransporteFalso implements Transporte {
        final List<String> pedidas = new ArrayList<>();
        String cuerpo = "{}";
        int estado = 200;
        RuntimeException fallo;
        @Override public Respuesta get(String url) {
            pedidas.add(url);
            if (fallo != null) throw fallo;
            return new Respuesta(estado, cuerpo);
        }
    }

    final TransporteFalso control = new TransporteFalso();
    final TransporteFalso version = new TransporteFalso();
    final ControlService servicio = new ControlService(control, version);

    @BeforeEach void limpiarControl() { Freno.CONTROL.clear(); }   // CONTROL es un mapa compartido de api.Freno

    // ----- cargarControl -------------------------------------------------

    @Test void controlBuenoRellenaYLosNullSeIgnoran() {
        control.cuerpo = "{\"intervalo\":2,\"activo\":true,\"otro\":null}";
        String msg = servicio.cargarControl("", txt -> fail("sin mensaje no debería marcar visto"));
        assertNull(msg);
        assertEquals(2.0, ((Number) Freno.CONTROL.get("intervalo")).doubleValue());
        assertEquals(Boolean.TRUE, Freno.CONTROL.get("activo"));
        assertFalse(Freno.CONTROL.containsKey("otro"), "los valores null se ignoran");
    }

    @Test void estadoDistintoDe200NoTocaControl() {
        Freno.CONTROL.put("previo", "valor");
        control.estado = 500;
        control.cuerpo = "{\"intervalo\":9}";
        String msg = servicio.cargarControl("", txt -> fail("estado != 200: no debería marcar visto"));
        assertNull(msg);
        assertEquals("valor", Freno.CONTROL.get("previo"), "un estado != 200 no toca CONTROL");
        assertFalse(Freno.CONTROL.containsKey("intervalo"));
    }

    @Test void jsonQueNoEsObjetoNoTocaControl() {
        Freno.CONTROL.put("previo", "valor");
        control.cuerpo = "[1,2,3]";
        String msg = servicio.cargarControl("", txt -> fail("JSON que no es objeto: no debería marcar visto"));
        assertNull(msg);
        assertEquals("valor", Freno.CONTROL.get("previo"));
    }

    @Test void mensajeNuevoSeDevuelveYSeMarcaVisto() {
        control.cuerpo = "{\"mensaje\":\"hola\"}";
        List<String> vistos = new ArrayList<>();
        String msg = servicio.cargarControl("", vistos::add);
        assertEquals("hola", msg);
        assertEquals(List.of("hola"), vistos);
    }

    @Test void mensajeYaVistoDevuelveNull() {
        control.cuerpo = "{\"mensaje\":\"hola\"}";
        String msg = servicio.cargarControl("hola", txt -> fail("ya visto: no debería marcar de nuevo"));
        assertNull(msg);
    }

    @Test void falloDeRedNoLanzaYNoTocaControl() {
        Freno.CONTROL.put("previo", "valor");
        control.fallo = new RuntimeException("sin red");
        String msg = assertDoesNotThrow(() -> servicio.cargarControl("", txt -> fail("fallo de red: no debería marcar visto")));
        assertNull(msg);
        assertEquals("valor", Freno.CONTROL.get("previo"));
    }

    // ----- ultimaVersion ---------------------------------------------------

    @Test void ultimaVersionConJsonBuenoDevuelveElTag() {
        version.cuerpo = "{\"tag_name\":\"v1.3\"}";
        assertEquals("v1.3", servicio.ultimaVersion("https://api.github.com/releases/latest"));
    }

    @Test void ultimaVersionConJsonRotoDevuelveNull() {
        version.cuerpo = "no es json";
        assertNull(servicio.ultimaVersion("https://api.github.com/releases/latest"));
    }

    @Test void ultimaVersionConEstadoDistintoDe2xxDevuelveNull() {
        version.estado = 404;
        version.cuerpo = "{\"tag_name\":\"v1.3\"}";
        assertNull(servicio.ultimaVersion("https://api.github.com/releases/latest"));
    }

    @Test void ultimaVersionSinRedNoLanza() {
        version.fallo = new RuntimeException("sin red");
        String tag = assertDoesNotThrow(() -> servicio.ultimaVersion("https://api.github.com/releases/latest"));
        assertNull(tag);
    }
}
