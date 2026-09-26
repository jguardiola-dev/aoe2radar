package dev.tirador.aoe2radar.app;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.service.EloSesion;
import dev.tirador.aoe2radar.service.EstadoVivo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static dev.tirador.aoe2radar.api.Cancelacion.hiloOperacion;
import static dev.tirador.aoe2radar.api.Cancelacion.opEnCurso;
import static dev.tirador.aoe2radar.api.Cancelacion.stopOperacion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caracteriza app.Servicios, la raíz de composición (fase 3, tanda 4, Z6): que los servicios existen, y que el
 * orden de inicialización estático (COMPANION construido sobre API_CLIENTE, y el resto sobre COMPANION) deja a
 * TODOS los servicios compartiendo el MISMO objeto — exactamente lo que la ventana usa, porque SpoilerFreeRecs
 * llega a estos campos con {@code import static Servicios.*}, sin copiarlos. Sin red: solo se comprueba el
 * cableado, no se llama a ningún método que hable con el companion.
 */
class ServiciosTest {

    /** Lee un campo privado por reflexión: para comprobar CON QUÉ se construyó un servicio sin exponer getters
     *  nuevos (eso sí sería tocar el contrato, no solo el test). */
    private static Object campo(Object o, String nombre) throws Exception {
        Field f = o.getClass().getDeclaredField(nombre);
        f.setAccessible(true);
        return f.get(o);
    }

    @Test
    void losServiciosDeLaAppExisten() {
        assertNotNull(Servicios.CONTROL_SERVICE);
        assertNotNull(Servicios.API_CLIENTE);
        assertNotNull(Servicios.COMPANION);
        assertNotNull(Servicios.LIVE);
        assertNotNull(Servicios.TOP_LADDER_SERVICE);
        assertNotNull(Servicios.SERVICIO_PERFIL);
        assertNotNull(Servicios.BUSQUEDA);
        assertNotNull(Servicios.TWITCH_SERVICE);
        assertNotNull(Servicios.VIVO);
        assertNotNull(Servicios.ELO_1V1);
        assertNotNull(Servicios.ANOTACIONES);
    }

    @Test
    void vivoEsElUnicoDeLaApp_yLoUsaElo1v1() throws Exception {
        // EstadoVivo.SISTEMA es el singleton de la app (service.EstadoVivo): Servicios.VIVO debe ser ÉSE, no una
        // copia, para que el socket, los barridos y la UI (que llegan a VIVO por el mismo import static) vean
        // siempre el mismo estado.
        assertSame(EstadoVivo.SISTEMA, Servicios.VIVO);
        // ELO_1V1 se construyó como "new EloSesion(VIVO, ...)": el campo privado "estado" tiene que ser ese
        // mismo VIVO, para que el ELO de sesión caduque con las partidas que de verdad ha visto la app.
        assertSame(Servicios.VIVO, campo(Servicios.ELO_1V1, "estado"));
    }

    @Test
    void laCadenaDeLaApiComparteUnaSolaInstancia() throws Exception {
        // API_CLIENTE -> COMPANION: CompanionApi guarda el ApiClient con el que se construyó.
        assertSame(Servicios.API_CLIENTE, campo(Servicios.COMPANION, "api"));
        // Todo lo que se construye "DESPUÉS de COMPANION" (comentado en Servicios) debe envolver ese MISMO
        // CompanionApi, no uno nuevo: si no, el freno y la caché de sesión se partirían en dos.
        assertSame(Servicios.COMPANION, campo(Servicios.LIVE, "api"));
        assertSame(Servicios.COMPANION, campo(Servicios.TOP_LADDER_SERVICE, "api"));
        assertSame(Servicios.COMPANION, campo(Servicios.SERVICIO_PERFIL, "api"));
        assertSame(Servicios.COMPANION, campo(Servicios.BUSQUEDA, "api"));
        assertSame(Servicios.COMPANION, campo(Servicios.TWITCH_SERVICE, "api"));
    }

    @Test
    void constantesDeCortesiaConLaApi() {
        // PER_PAGE/PAUSA_MS: mismos valores que en la 1.1 (SpoilerFreeRecs), ahora en la raíz de composición.
        assertEquals(50, Servicios.PER_PAGE);
        assertEquals(300L, Servicios.PAUSA_MS);
    }

    @Test
    void dormirNoBloqueaConCero() {
        // dormir(ms) es una pausa cooperativa (respeta stopOperacion/opEnCurso), no un Thread.sleep ciego: con
        // 0 ms debe volver enseguida. Sin red: no se toca BUSQUEDA.buscar (esa sí llamaría al companion).
        long antes = System.currentTimeMillis();
        Servicios.dormir(0);
        assertTrue(System.currentTimeMillis() - antes < 200);
    }

    @Test
    void dormirSoloAcortaLaPausaParaElHiloDeLaOperacionCancelada() {
        // Fila 56 de DEUDA: antes, dormir miraba "stopOperacion && opEnCurso" sin comprobar el hilo, así que
        // pulsar Detener acortaba también las pausas de un barrido de fondo ajeno a la operación cancelada.
        // Ahora usa Cancelacion.detieneEsteHilo(), que exige además que sea el hilo de esa operación.
        stopOperacion = true;
        opEnCurso = true;
        try {
            hiloOperacion = new Thread();   // un hilo distinto al de este test: su pausa NO debe acortarse
            long antes = System.currentTimeMillis();
            Servicios.dormir(400);
            assertTrue(System.currentTimeMillis() - antes >= 350,
                    "un hilo ajeno a la operación cancelada no debe acortar su pausa");

            hiloOperacion = Thread.currentThread();   // el hilo de la operación cancelada: sí debe acortarse
            antes = System.currentTimeMillis();
            Servicios.dormir(3000);
            assertTrue(System.currentTimeMillis() - antes < 300,
                    "el hilo de la operación cancelada sí debe acortar su pausa");
        } finally {
            stopOperacion = false; opEnCurso = false; hiloOperacion = null;
        }
    }
}
