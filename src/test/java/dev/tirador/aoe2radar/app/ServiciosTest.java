package dev.tirador.aoe2radar.app;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.service.EloSesion;
import dev.tirador.aoe2radar.service.EstadoVivo;
import dev.tirador.aoe2radar.util.Operaciones;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

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
        // Desde la 1.3, con la caché por URL activa (CompanionApi.conCache): sin ella, el campo «cache» es null.
        assertNotNull(campo(Servicios.COMPANION, "cache"), "COMPANION se construye con la caché por URL");
        // Todo lo que se construye "DESPUÉS de COMPANION" (comentado en Servicios) debe envolver ese MISMO
        // CompanionApi, no uno nuevo: si no, el freno y la caché de sesión se partirían en dos.
        // 1.4: lo que tiene respaldo de World's Edge recibe el decorador (PARTIDAS, LADDER, PERFIL, BUSCAR, que envuelven
        // COMPANION y WORLDS_EDGE); lo que no (partidas en curso, Twitch, vinculadas), el COMPANION de siempre.
        assertSame(Servicios.API_CLIENTE, campo(Servicios.WORLDS_EDGE, "api"), "World's Edge por el mismo cliente (su cubo del freno)");
        assertSame(Servicios.PARTIDAS, campo(Servicios.LIVE, "api"), "«¿ya terminó?» con respaldo");
        assertSame(Servicios.COMPANION, campo(Servicios.LIVE, "barrido"), "el barrido de Live now, sin respaldo");
        assertSame(Servicios.COMPANION, campo(Servicios.TOP_LADDER_SERVICE, "fuente"), "vigilarTop (en curso), sin respaldo");
        assertSame(Servicios.LADDER, campo(Servicios.TOP_LADDER_SERVICE, "ladder"));
        assertSame(Servicios.PERFIL, campo(Servicios.SERVICIO_PERFIL, "api"));
        assertSame(Servicios.COMPANION, campo(Servicios.SERVICIO_PERFIL, "apiVinculadas"), "las vinculadas, solo del companion");
        assertSame(Servicios.BUSCAR, campo(Servicios.BUSQUEDA, "api"));
        assertSame(Servicios.COMPANION, campo(Servicios.TWITCH_SERVICE, "api"));
    }

    @Test
    void losCatalogosNoAprendenDeLoQueSirvioElRespaldo() {
        // 1.4: los nombres de mapa y civ de World's Edge son reconstruidos; no se guardan en config (mapas_ranked, civs_vistas).
        dev.tirador.aoe2radar.model.Match m = new dev.tirador.aoe2radar.model.Match();
        m.deRespaldo = true; m.mode = "1v1 Random Map"; m.map = "Mapa Solo Del Respaldo";
        dev.tirador.aoe2radar.model.MatchPlayer p = new dev.tirador.aoe2radar.model.MatchPlayer(); p.civ = "Civ Solo Del Respaldo"; m.players.add(p);
        Servicios.aprenderCatalogos(java.util.List.of(m));
        org.junit.jupiter.api.Assertions.assertFalse(dev.tirador.aoe2radar.cache.Catalogos.MAPAS_CAT.contains("Mapa Solo Del Respaldo"));
        org.junit.jupiter.api.Assertions.assertFalse(dev.tirador.aoe2radar.cache.Catalogos.CIVS_CAT.contains("Civ Solo Del Respaldo"));
    }

    @Test
    void constantesDeCortesiaConLaApi() {
        // PER_PAGE/PAUSA_MS: mismos valores que en la 1.1 (SpoilerFreeRecs), ahora en la raíz de composición.
        assertEquals(50, Servicios.PER_PAGE);
        assertEquals(300L, Servicios.PAUSA_MS);
    }

    @Test
    void dormirNoBloqueaConCero() {
        // dormir(ms) es una pausa cooperativa (respeta el freno de su operación), no un Thread.sleep ciego: con
        // 0 ms debe volver enseguida. Sin red: no se toca BUSQUEDA.buscar (esa sí llamaría al companion).
        long antes = System.currentTimeMillis();
        Servicios.dormir(0);
        assertTrue(System.currentTimeMillis() - antes < 200);
    }

    @Test
    void dormirSoloAcortaLaPausaParaElHiloDeLaOperacionCancelada() {
        // Fila 56 de DEUDA: antes, dormir miraba "stopOperacion && opEnCurso" sin comprobar el hilo, así que
        // pulsar Detener acortaba también las pausas de un barrido de fondo ajeno a la operación cancelada.
        // Ahora usa Cancelacion.detieneEsteHilo(), que exige además que sea el hilo de esa operación (desde la
        // 1.3, con un freno por operación: util.Operaciones).
        Operaciones ops = Operaciones.GLOBAL;
        final long op = 930_001;   // lejos de los números de la barra
        ops.empezar(op);
        ops.detener(op);
        try {
            // este hilo no es el de la operación (no la ha anotado): su pausa NO debe acortarse
            long antes = System.currentTimeMillis();
            Servicios.dormir(400);
            assertTrue(System.currentTimeMillis() - antes >= 350,
                    "un hilo ajeno a la operación cancelada no debe acortar su pausa");

            ops.anotarHilo(op);   // el hilo de la operación cancelada: sí debe acortarse
            antes = System.currentTimeMillis();
            Servicios.dormir(3000);
            assertTrue(System.currentTimeMillis() - antes < 300,
                    "el hilo de la operación cancelada sí debe acortar su pausa");
        } finally {
            ops.soltarHilo();
            ops.terminar(op);
        }
    }

    @Test
    void avisarPausa429LlamaAlConsumidorFijadoPorLaVentanaEnElEdt() throws Exception {
        // Limpieza 1 (fase 4): antes, avisarPausa429 buscaba la ventana viva con Frame.getFrames() y pintaba
        // `status` a través de un putClientProperty que BarraEstado dejaba en su constructor. Ahora Servicios
        // solo conoce un LongConsumer explícito (avisoPausa429), que la ventana fija al construir BarraEstado.
        LongConsumer anterior = Servicios.avisoPausa429;
        try {
            AtomicLong segRecibidos = new AtomicLong(-1);
            AtomicBoolean enEdt = new AtomicBoolean(false);
            Servicios.avisoPausa429 = seg -> { segRecibidos.set(seg); enEdt.set(SwingUtilities.isEventDispatchThread()); };
            Servicios.avisarPausa429(7);
            SwingUtilities.invokeAndWait(() -> { });   // vacía el EDT: el invokeLater de avisarPausa429 ya corrió
            assertEquals(7, segRecibidos.get());
            assertTrue(enEdt.get(), "el aviso debe llegar al consumidor en el EDT, nunca desde la red");
        } finally {
            Servicios.avisoPausa429 = anterior;
        }
    }
}
