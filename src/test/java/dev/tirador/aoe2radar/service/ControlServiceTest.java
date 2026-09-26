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
        String msg = servicio.cargarControl("");
        assertNull(msg);
        assertEquals(2.0, ((Number) Freno.CONTROL.get("intervalo")).doubleValue());
        assertEquals(Boolean.TRUE, Freno.CONTROL.get("activo"));
        assertFalse(Freno.CONTROL.containsKey("otro"), "los valores null se ignoran");
    }

    @Test void estadoDistintoDe200NoTocaControl() {
        Freno.CONTROL.put("previo", "valor");
        control.estado = 500;
        control.cuerpo = "{\"intervalo\":9}";
        String msg = servicio.cargarControl("");
        assertNull(msg);
        assertEquals("valor", Freno.CONTROL.get("previo"), "un estado != 200 no toca CONTROL");
        assertFalse(Freno.CONTROL.containsKey("intervalo"));
    }

    @Test void estado201NoTocaControl() {
        Freno.CONTROL.put("previo", "valor");
        control.estado = 201;   // solo vale 200 exacto, ni siquiera otro 2xx
        control.cuerpo = "{\"intervalo\":9}";
        String msg = servicio.cargarControl("");
        assertNull(msg);
        assertEquals("valor", Freno.CONTROL.get("previo"), "solo vale 200 exacto");
        assertFalse(Freno.CONTROL.containsKey("intervalo"));
    }

    @Test void controlBuenoBorraLasClavesAnteriores() {
        Freno.CONTROL.put("viejo", "de una vuelta anterior");
        control.cuerpo = "{\"intervalo\":3}";
        servicio.cargarControl("");
        assertFalse(Freno.CONTROL.containsKey("viejo"), "control.json reemplaza el mapa entero (clear), no lo mezcla");
        assertEquals(3.0, ((Number) Freno.CONTROL.get("intervalo")).doubleValue());
    }

    @Test void jsonQueNoEsObjetoNoTocaControl() {
        Freno.CONTROL.put("previo", "valor");
        control.cuerpo = "[1,2,3]";
        String msg = servicio.cargarControl("");
        assertNull(msg);
        assertEquals("valor", Freno.CONTROL.get("previo"));
    }

    @Test void mensajeNuevoSeDevuelveSinMarcarloVisto() {
        // F10 (revisión 1.3): el servicio ya no lo marca como visto; lo hace la ventana cuando el usuario cierra la
        // franja (ui.FranjaAviso, su ×). Por eso cargarControl ya no recibe con qué marcarlo.
        control.cuerpo = "{\"mensaje\":\"hola\"}";
        assertEquals("hola", servicio.cargarControl(""));
        assertEquals("hola", servicio.cargarControl(""), "sin marcar, la recarga de cada hora lo vuelve a traer");
    }

    @Test void mensajeYaVistoDevuelveNull() {
        control.cuerpo = "{\"mensaje\":\"hola\"}";
        String msg = servicio.cargarControl("hola");
        assertNull(msg);
    }

    @Test void mensajeEnBlancoNoSeDevuelveNiSeMarcaVisto() {
        control.cuerpo = "{\"mensaje\":\"   \"}";
        String msg = servicio.cargarControl("");
        assertNull(msg);
    }

    @Test void falloDeRedNoLanzaYNoTocaControl() {
        Freno.CONTROL.put("previo", "valor");
        control.fallo = new RuntimeException("sin red");
        String msg = assertDoesNotThrow(() -> servicio.cargarControl(""));
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

    /** En la 1.1, httpText delegaba en ApiClient.texto, que ante un estado que no es 2xx lanzaba
     *  IOException("HTTP nnn"); comprobarActualizacion lo anotaba como «actualizaciones: HTTP nnn». Aquí no hay
     *  ApiClient de por medio, así que ultimaVersion lanza esa misma IOException para que su propio catch la
     *  anote igual. */
    @Test void ultimaVersionAnte404AnotaHttpEnElLogYNoDaTag() {
        version.estado = 404;
        version.cuerpo = "{\"tag_name\":\"v1.3\"}";
        java.io.ByteArrayOutputStream captura = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        System.setOut(new java.io.PrintStream(captura));
        String tag;
        try {
            tag = servicio.ultimaVersion("https://api.github.com/releases/latest");
        } finally {
            System.setOut(original);
        }
        assertNull(tag);
        assertTrue(captura.toString().contains("actualizaciones: HTTP 404"), captura.toString());
    }
}
