package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.tirador.aoe2radar.cache.HistorialDisco.ACTIVIDAD_CACHE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MiPartidaServiceJuego: la vigilancia del log del juego (MainLog.txt real, con FileChannel/Files como en la
 * 1.1), la identidad (mi_pid/mi_nombre en config, inyectado con un mapa como en CampanasTest) y las civs
 * recientes de caché. La red (sondearLobbyOficial) no se prueba aquí: la 1.1 tampoco la probaba (sin
 * Transporte inyectable para esa llamada, ver informe del extractor).
 */
class MiPartidaServiceJuegoTest {

    final Map<String, String> config = new HashMap<>();
    final RelojFalso reloj = new RelojFalso();
    Path carpeta;

    MiPartidaServiceJuego servicio(Path carpetaLogs) {
        return new MiPartidaServiceJuego(pid -> null, config::getOrDefault, config::put, reloj, () -> carpetaLogs);
    }

    /** Crea <carpeta>/<sesion>/MainLog.txt con el contenido dado. */
    static Path sesionConLog(Path carpeta, String sesion, String contenido) throws IOException {
        Path dirSesion = Files.createDirectories(carpeta.resolve(sesion));
        Path log = dirSesion.resolve("MainLog.txt");
        Files.writeString(log, contenido, StandardCharsets.UTF_8);
        return log;
    }

    @AfterEach void limpiarActividadCache() { ACTIVIDAD_CACHE.clear(); }

    // ----- identidad -----

    @Test void identidadSinConfigDevuelveNull() {
        assertNull(servicio(Path.of("no-existe")).identidad());
    }

    @Test void identidadConConfigDevuelvePidYNombre() {
        config.put("mi_pid", "123");
        config.put("mi_nombre", "12Tirador");
        var id = servicio(Path.of("no-existe")).identidad();
        assertEquals(123L, id.pid());
        assertEquals("12Tirador", id.nombre());
    }

    @Test void fijarIdentidadGuardaAmbasClaves() {
        servicio(Path.of("no-existe")).fijarIdentidad(456, "Rival");
        assertEquals("456", config.get("mi_pid"));
        assertEquals("Rival", config.get("mi_nombre"));
    }

    // ----- leerLogJuego: registro (identidad) ausente -----

    @Test void leerLogJuegoSinIdentidadNoLeeNadaAunqueHayaLog(@TempDir Path tmp) throws IOException {
        sesionConLog(tmp, "1", "PlayerReadyRequest");   // hay partida, pero no sé quién soy
        assertFalse(servicio(tmp).leerLogJuego(), "sin mi_pid en config, ni se mira el log");
    }

    // ----- leerLogJuego: carpeta/sesión/MainLog.txt ausentes -----

    @Test void leerLogJuegoSinCarpetaDeLogsDevuelveFalse(@TempDir Path tmp) {
        config.put("mi_pid", "7");
        assertFalse(servicio(tmp.resolve("no-existe")).leerLogJuego());
    }

    @Test void leerLogJuegoSinSesionesDevuelveFalse(@TempDir Path tmp) {
        config.put("mi_pid", "7");
        assertFalse(servicio(tmp).leerLogJuego(), "carpeta de logs vacía, sin ninguna sesión dentro");
    }

    @Test void leerLogJuegoSinMainLogEnLaSesionDevuelveFalse(@TempDir Path tmp) throws IOException {
        config.put("mi_pid", "7");
        Files.createDirectories(tmp.resolve("sesion-1"));   // la carpeta de la sesión existe, pero sin MainLog.txt
        assertFalse(servicio(tmp).leerLogJuego());
    }

    // ----- leerLogJuego: parseo de líneas reales / detección de partida -----

    @Test void leerLogJuegoDetectaFaseDePreparacionConMsSetup(@TempDir Path tmp) throws IOException {
        config.put("mi_pid", "7");
        sesionConLog(tmp, "sesion-1", "algo de log\nMS_Setup iniciado\nmas log\n");
        assertTrue(servicio(tmp).leerLogJuego(), "MS_Setup en el trozo leído: toca avisar");
    }

    @Test void leerLogJuegoDetectaFaseDePreparacionConPlayerReadyRequest(@TempDir Path tmp) throws IOException {
        config.put("mi_pid", "7");
        sesionConLog(tmp, "sesion-1", "cabecera\nPlayerReadyRequest recibido\n");
        assertTrue(servicio(tmp).leerLogJuego());
    }

    @Test void leerLogJuegoSinLasFrasesNoAvisa(@TempDir Path tmp) throws IOException {
        config.put("mi_pid", "7");
        sesionConLog(tmp, "sesion-1", "log normal del juego, cargando recursos\n");
        assertFalse(servicio(tmp).leerLogJuego());
    }

    @Test void leerLogJuegoSoloAvisaConLoNuevoDesdeLaUltimaLectura(@TempDir Path tmp) throws IOException {
        config.put("mi_pid", "7");
        Path log = sesionConLog(tmp, "sesion-1", "arrancando\n");
        MiPartidaServiceJuego s = servicio(tmp);
        assertFalse(s.leerLogJuego(), "primera lectura: nada interesante todavía");
        Files.writeString(log, "MS_Setup ahora\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        assertTrue(s.leerLogJuego(), "lo nuevo añadido sí trae la fase de preparación");
    }

    // ----- leerLogJuego: reloj (throttle de 120s para no avisar dos veces seguidas) -----

    @Test void leerLogJuegoNoRepiteElAvisoAntesDe120s(@TempDir Path tmp) throws IOException {
        config.put("mi_pid", "7");
        Path log = sesionConLog(tmp, "sesion-1", "MS_Setup\n");
        MiPartidaServiceJuego s = servicio(tmp);
        assertTrue(s.leerLogJuego(), "primer aviso");
        Files.writeString(log, "MS_Setup otra vez\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        reloj.avanzar(60_000);   // menos de 120s: no toca avisar todavía
        assertFalse(s.leerLogJuego(), "menos de 120s desde el último aviso");
    }

    @Test void leerLogJuegoVuelveAAvisarPasados120s(@TempDir Path tmp) throws IOException {
        config.put("mi_pid", "7");
        Path log = sesionConLog(tmp, "sesion-1", "MS_Setup\n");
        MiPartidaServiceJuego s = servicio(tmp);
        assertTrue(s.leerLogJuego(), "primer aviso");
        Files.writeString(log, "MS_Setup otra vez\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        reloj.avanzar(120_001);   // más de 120s: toca avisar de nuevo
        assertTrue(s.leerLogJuego());
    }

    // ----- leerLogJuego: sin solape entre ticks (fila 132 de DEUDA) -----

    /**
     * El tick de 2s (MiPartidaPresenter) lanza un hilo "log-juego" nuevo en cada llamada, sin guarda anti-solape;
     * si dos llamadas coincidieran, tocarían a la vez logActual/logPos/ultimoAvisoMs sin ninguna protección.
     * Se comprueba aquí que el método es "synchronized" (una llamada espera a que termine la anterior) en vez de
     * levantar dos hilos de verdad, que sería un test lento y no determinista.
     */
    @Test void leerLogJuegoEsSynchronizedParaQueDosTicksNoSeSolapen() throws NoSuchMethodException {
        var metodo = MiPartidaServiceJuego.class.getMethod("leerLogJuego");
        assertTrue(java.lang.reflect.Modifier.isSynchronized(metodo.getModifiers()),
                "leerLogJuego debe ser synchronized: dos ticks seguidos no deben tocar el estado de la sesión a la vez");
    }

    // ----- civsRecientes / paisDe -----

    @Test void civsRecientesSinActividadEnCacheDevuelveVacio() {
        assertEquals(List.of(), servicio(Path.of("no-existe")).civsRecientes(999));
    }

    @Test void civsRecientesUsaLasDeLosUltimos30DiasDeMasAMenosJugada() {
        long pid = 42;
        List<Match> partidas = new ArrayList<>();
        partidas.add(partida(pid, "Franks", reloj.ahoraMs() - 1_000));         // reciente: 2 veces Franks
        partidas.add(partida(pid, "Franks", reloj.ahoraMs() - 2_000));
        partidas.add(partida(pid, "Aztecs", reloj.ahoraMs() - 3_000));         // reciente: 1 vez Aztecs
        partidas.add(partida(pid, "Mayans", Instant.ofEpochMilli(reloj.ahoraMs()).minus(java.time.Duration.ofDays(40)).toEpochMilli()));   // hace 40 días: no cuenta
        ACTIVIDAD_CACHE.put(pid, new Actividad(pid, "Jugador", partidas, true, 1, reloj.ahoraMs()));
        List<String> top = servicio(Path.of("no-existe")).civsRecientes(pid);
        assertEquals(List.of("Franks", "Aztecs"), top, "Franks (2) antes que Aztecs (1); Mayans queda fuera por antiguo");
    }

    static Match partida(long pid, String civ, long startedMs) {
        Match m = new Match();
        m.id = startedMs;
        m.started = Instant.ofEpochMilli(startedMs);
        MatchPlayer mp = new MatchPlayer();
        mp.id = pid; mp.civ = civ;
        m.players.add(mp);
        return m;
    }
}
