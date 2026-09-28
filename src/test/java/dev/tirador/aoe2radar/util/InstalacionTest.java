package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static dev.tirador.aoe2radar.util.CfgLanzadorTest.CFG_REAL;
import static dev.tirador.aoe2radar.util.VerificacionJarTest.jarBueno;
import static dev.tirador.aoe2radar.util.VerificacionJarTest.sha;
import static org.junit.jupiter.api.Assertions.*;

/**
 * El lado de disco del actualizador: aplicar al cerrar, primer arranque, vuelta atrás y limpieza. Todo en carpetas
 * temporales que imitan la instalación (app/ con el .cfg real de jpackage) y la carpeta de datos: nunca la
 * instalación ni los datos reales.
 */
class InstalacionTest {

    @TempDir Path tmp;
    Path app, cfg, dir;
    byte[] jarNuevo;

    @BeforeEach void instalacion() throws Exception {
        app = Files.createDirectories(tmp.resolve("aoe2radar").resolve("app"));
        cfg = app.resolve("aoe2radar.cfg");
        Files.writeString(cfg, CFG_REAL, StandardCharsets.ISO_8859_1);
        Files.write(app.resolve("aoe2radar-1.3.0.jar"), jarBueno("1.3"));
        Files.writeString(app.resolve("flatlaf-3.7.2.jar"), "flatlaf");
        dir = tmp.resolve("datos").resolve(Instalacion.CARPETA);
        jarNuevo = jarBueno("1.5");
    }

    Instalacion.Rutas rutas(String jarEnUso) { return new Instalacion.Rutas(app, cfg, dir, jarEnUso); }

    /** Deja la 1.5 descargada y apuntada como lista (lo que hace ActualizadorService.descargar). */
    void lista15() throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve("aoe2radar-1.5.jar"), jarNuevo);
        assertTrue(Instalacion.guardarLista(dir, lista("1.5", jarNuevo)));
    }

    static final String RUNTIME = "21.0.9";

    static Instalacion.Lista lista(String version, byte[] jar) throws Exception {
        return new Instalacion.Lista(version, Instalacion.nombreJar(version), sha(jar), jar.length, RUNTIME,
                List.of("flatlaf-3.7.2.jar"), List.of("-Dfile.encoding=UTF-8"), "dev.tirador.aoe2radar.SpoilerFreeRecs");
    }

    /** aplicar con el runtime de la instalación de prueba. */
    static Instalacion.Aplicacion aplicar(Instalacion.Rutas r, String version) {
        return Instalacion.aplicar(r, version, RUNTIME);
    }

    /** Arranque con un pid inventado; vivos: los pids que siguen abiertos. */
    static Instalacion.Arranque arrancar(Instalacion.Rutas r, long pid, Long... vivos) {
        List<Long> v = List.of(vivos);
        return Instalacion.alArrancar(r, pid, v::contains);
    }

    String cfgAhora() throws IOException { return Files.readString(cfg, StandardCharsets.ISO_8859_1); }

    static final String CFG_15 = CfgLanzador.reescribir(CFG_REAL, "aoe2radar-1.5.jar", "1.5");

    // ------------------------------------------------------------------ aplicar

    @Test void sinNadaDescargadoNoHaceNada() throws Exception {
        assertEquals(Instalacion.Aplicacion.NADA, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        assertEquals(CFG_REAL, cfgAhora());
        assertEquals(Instalacion.Aplicacion.NADA, aplicar(null, "1.3"));
    }

    @Test void aplicarCopiaElJarYApuntaElCfgSinTocarElJarEnUso() throws Exception {
        lista15();
        byte[] viejo = Files.readAllBytes(app.resolve("aoe2radar-1.3.0.jar"));
        assertEquals(Instalacion.Aplicacion.APLICADA, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        assertEquals(CFG_15, cfgAhora());
        assertArrayEquals(jarNuevo, Files.readAllBytes(app.resolve("aoe2radar-1.5.jar")));
        assertArrayEquals(viejo, Files.readAllBytes(app.resolve("aoe2radar-1.3.0.jar")), "el jar en uso ni se toca");
        assertEquals(CFG_REAL, Files.readString(dir.resolve(Instalacion.CFG_ANTERIOR), StandardCharsets.ISO_8859_1), "copia del .cfg de antes");
        var marca = Instalacion.leerProps(dir.resolve(Instalacion.MARCA));
        assertEquals("aoe2radar-1.3.0.jar", marca.getProperty("anterior"));
        assertEquals("aoe2radar-1.5.jar", marca.getProperty("nueva"));
        assertEquals("", marca.getProperty("pids"), "aún sin arranques");
        assertTrue(sinTemporales(app), "ni .parcial ni .tmp en app/");
    }

    @Test void aplicarDosVecesEsIdempotente() throws Exception {
        lista15();
        aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3");
        // otra instancia de la 1.3 que se cierra después: el .cfg ya apunta a la 1.5
        assertEquals(Instalacion.Aplicacion.YA_ESTABA, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        assertEquals(CFG_15, cfgAhora());
    }

    @Test void nuncaHaciaAtrasNiALaFallida() throws Exception {
        lista15();
        assertEquals(Instalacion.Aplicacion.NADA, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.5"), "ya estás en la 1.5");
        assertEquals(Instalacion.Aplicacion.NADA, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.6"));
        // el .cfg ya apunta a una 1.6 (otra instancia actualizó más): una 1.3 que cierra no la baja a la 1.5
        Files.write(app.resolve("aoe2radar-1.6.jar"), jarBueno("1.6"));
        Files.writeString(cfg, CfgLanzador.reescribir(CFG_REAL, "aoe2radar-1.6.jar", "1.6"), StandardCharsets.ISO_8859_1);
        assertEquals(Instalacion.Aplicacion.NADA, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        assertTrue(cfgAhora().contains("aoe2radar-1.6.jar"));
        // versión que ya falló una vez
        Files.writeString(cfg, CFG_REAL, StandardCharsets.ISO_8859_1);
        Files.writeString(dir.resolve(Instalacion.FALLIDA), "version=1.5\n");
        assertEquals(Instalacion.Aplicacion.NADA, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        assertEquals(CFG_REAL, cfgAhora());
    }

    @Test void unJarDescargadoEstropeadoNoLlegaAApp() throws Exception {
        lista15();
        Files.write(dir.resolve("aoe2radar-1.5.jar"), jarBueno("1.6"));   // mismo tamaño, otro sha
        assertEquals(Instalacion.Aplicacion.FALLO, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        assertEquals(CFG_REAL, cfgAhora());
        assertFalse(Files.exists(app.resolve("aoe2radar-1.5.jar")));
        assertNull(Instalacion.leerLista(dir), "se olvida: se bajará otra vez");
        assertFalse(Files.exists(dir.resolve(Instalacion.MARCA)));
    }

    @Test void siUnJarConElNombreBuenoYaEstaEnAppYEsElBuenoNoSeCopiaOtraVez() throws Exception {
        lista15();
        Path destino = Files.write(app.resolve("aoe2radar-1.5.jar"), jarNuevo);
        FileTime antes = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));
        Files.setLastModifiedTime(destino, antes);
        Files.setAttribute(destino, "creationTime", antes);
        assertEquals(Instalacion.Aplicacion.APLICADA, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        assertEquals(antes, Files.getAttribute(destino, "creationTime"), "es el mismo archivo: no se ha copiado otro encima");
        assertTrue(Files.getLastModifiedTime(destino).compareTo(antes) > 0, "pero con fecha de ahora, para que el barrido no lo tome por viejo");
        // y uno con el nombre bueno pero estropeado se sustituye
        Files.writeString(cfg, CFG_REAL, StandardCharsets.ISO_8859_1);
        Files.write(destino, jarBueno("roto"));
        assertEquals(Instalacion.Aplicacion.APLICADA, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        assertArrayEquals(jarNuevo, Files.readAllBytes(destino));
    }

    @EnabledOnOs(OS.WINDOWS)
    @Test void siElCfgNoSePuedeCambiarQuedaIntactoYSinMarca() throws Exception {
        lista15();
        try (RandomAccessFile bloqueo = new RandomAccessFile(cfg.toFile(), "r")) {
            assertEquals(Instalacion.Aplicacion.FALLO, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        }
        assertEquals(CFG_REAL, cfgAhora());
        assertFalse(Files.exists(dir.resolve(Instalacion.MARCA)), "sin marca: el próximo arranque es normal");
        assertNotNull(Instalacion.leerLista(dir), "se reintenta en el próximo cierre");
        assertEquals(Instalacion.Arranque.NORMAL, arrancar(rutas("aoe2radar-1.3.0.jar"), 1));
    }

    @Test void conUnCfgQueNoSeReconoceNoSeToca() throws Exception {
        lista15();
        Files.writeString(cfg, "[Application]\r\nalgo raro\r\n");
        assertEquals(Instalacion.Aplicacion.FALLO, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        assertEquals("[Application]\r\nalgo raro\r\n", cfgAhora());
    }

    // ------------------------------------------------------------------ arranque y vuelta atrás

    @Test void primerArranqueTrasActualizarYConfirmacion() throws Exception {
        lista15();
        aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3");
        Instalacion.Rutas nueva = rutas("aoe2radar-1.5.jar");
        assertEquals(Instalacion.Arranque.TRAS_ACTUALIZAR, arrancar(nueva, 101));
        assertEquals("101", Instalacion.leerProps(dir.resolve(Instalacion.MARCA)).getProperty("pids"));
        envejecer(app.resolve("aoe2radar-1.3.0.jar"));
        Instalacion.confirmar(nueva, "1.5");
        assertFalse(Files.exists(dir.resolve(Instalacion.MARCA)));
        assertFalse(Files.exists(dir.resolve(Instalacion.CFG_ANTERIOR)));
        assertFalse(Files.exists(app.resolve("aoe2radar-1.3.0.jar")), "el jar viejo se borra en el arranque siguiente");
        assertTrue(Files.exists(app.resolve("flatlaf-3.7.2.jar")), "las dependencias no se tocan");
        assertNull(Instalacion.leerLista(dir), "lo descargado ya no hace falta");
        assertFalse(Files.exists(dir.resolve("aoe2radar-1.5.jar")));
        assertEquals(Instalacion.Arranque.NORMAL, arrancar(nueva, 102));
    }

    @Test void unaMarcaDeOtroJarSeDescarta() throws Exception {
        lista15();
        aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3");
        // el instalador (u otra cosa) cambió el .cfg: arranca otro jar
        assertEquals(Instalacion.Arranque.NORMAL, arrancar(rutas("aoe2radar-1.6.0.jar"), 1));
        assertFalse(Files.exists(dir.resolve(Instalacion.MARCA)));
        assertFalse(Files.exists(dir.resolve(Instalacion.CFG_ANTERIOR)));
    }

    @Test void trasDosArranquesSinVentanaSeVuelveALaAnterior() throws Exception {
        lista15();
        aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3");
        Instalacion.Rutas nueva = rutas("aoe2radar-1.5.jar");
        assertEquals(Instalacion.Arranque.TRAS_ACTUALIZAR, arrancar(nueva, 101));
        assertEquals(Instalacion.Arranque.TRAS_ACTUALIZAR, arrancar(nueva, 102));
        assertEquals(Instalacion.Arranque.REVERTIDO, arrancar(nueva, 103), "101 y 102 murieron sin abrir la ventana");
        assertEquals(CFG_REAL, cfgAhora(), ".cfg de antes, byte a byte");
        assertEquals("1.5", Instalacion.versionFallida(dir));
        assertNull(Instalacion.leerLista(dir), "la 1.3 no la vuelve a aplicar al cerrar");
        assertFalse(Files.exists(dir.resolve(Instalacion.MARCA)));
        String[] aviso = Instalacion.fallidaSinAvisar(dir);
        assertArrayEquals(new String[]{ "1.5", "1.3" }, aviso);
        Instalacion.marcarFallidaAvisada(dir);
        assertNull(Instalacion.fallidaSinAvisar(dir), "se avisa una vez");
        assertEquals("1.5", Instalacion.versionFallida(dir), "pero sigue apuntada");
    }

    @Test void revertirDesdeElFalloDeArranque() throws Exception {
        lista15();
        aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3");
        Instalacion.Rutas nueva = rutas("aoe2radar-1.5.jar");
        arrancar(nueva, 101, 101L);
        assertTrue(Instalacion.revertir(nueva, "excepción al abrir la ventana"));
        assertEquals(CFG_REAL, cfgAhora());
        assertEquals("1.5", Instalacion.versionFallida(dir));
        assertFalse(Instalacion.revertir(nueva, "otra vez"), "sin marca ya no hay nada que revertir");
    }

    @Test void sinElJarAnteriorNoSeRevierte() throws Exception {
        lista15();
        aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3");
        Files.delete(app.resolve("aoe2radar-1.3.0.jar"));   // p. ej., el [InstallDelete] del instalador
        assertFalse(Instalacion.revertir(rutas("aoe2radar-1.5.jar"), "prueba"));
        assertEquals(CFG_15, cfgAhora(), "revertir aquí dejaría la app sin jar: no se toca");
    }

    @Test void siElCfgYaNoApuntaAlNuevoNoSeRevierte() throws Exception {
        lista15();
        aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3");
        String otro = CfgLanzador.reescribir(CFG_REAL, "aoe2radar-1.6.jar", "1.6");
        Files.writeString(cfg, otro, StandardCharsets.ISO_8859_1);
        assertFalse(Instalacion.revertir(rutas("aoe2radar-1.5.jar"), "prueba"));
        assertEquals(otro, cfgAhora());
    }

    @Test void unaFallidaSeOlvidaAlLlegarAEsaVersion() throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(Instalacion.FALLIDA), "version=1.5\navisado=true\n");
        Instalacion.confirmar(rutas("aoe2radar-1.3.0.jar"), "1.3");
        assertEquals("1.5", Instalacion.versionFallida(dir));
        Instalacion.confirmar(rutas("aoe2radar-1.3.0.jar"), "1.5");   // p. ej., instalada con el instalador
        assertNull(Instalacion.versionFallida(dir));
    }

    // ------------------------------------------------------------------ barrido de jars viejos

    @Test void elBarridoRespetaElJarEnUsoElDelCfgLosRecientesYLasDependencias() throws Exception {
        Path viejo = Files.write(app.resolve("aoe2radar-1.2.0-SNAPSHOT.jar"), jarBueno("1.2"));
        Path reciente = Files.write(app.resolve("aoe2radar-1.6.jar"), jarBueno("1.6"));   // otra instancia colocándolo
        envejecer(viejo);
        envejecer(app.resolve("aoe2radar-1.3.0.jar"));
        envejecer(app.resolve("flatlaf-3.7.2.jar"));
        assertEquals(List.of("aoe2radar-1.2.0-SNAPSHOT.jar"), Instalacion.barrerJarsViejos(rutas("aoe2radar-1.3.0.jar")));
        assertTrue(Files.exists(reciente));
        assertTrue(Files.exists(app.resolve("aoe2radar-1.3.0.jar")));
        assertTrue(Files.exists(app.resolve("flatlaf-3.7.2.jar")));
    }

    @Test void conUnCfgIlegibleNoSeBarreNada() throws Exception {
        Path viejo = Files.write(app.resolve("aoe2radar-1.2.jar"), jarBueno("1.2"));
        envejecer(viejo);
        Files.writeString(cfg, "roto");
        assertEquals(List.of(), Instalacion.barrerJarsViejos(rutas("aoe2radar-1.3.0.jar")));
        assertTrue(Files.exists(viejo));
    }

    @EnabledOnOs(OS.WINDOWS)
    @Test void unJarBloqueadoSeQuedaParaLaSiguiente() throws Exception {
        Path viejo = Files.write(app.resolve("aoe2radar-1.2.jar"), jarBueno("1.2"));
        envejecer(viejo);
        try (RandomAccessFile bloqueo = new RandomAccessFile(viejo.toFile(), "r")) {
            assertEquals(List.of(), Instalacion.barrerJarsViejos(rutas("aoe2radar-1.3.0.jar")));
        }
        assertEquals(List.of("aoe2radar-1.2.jar"), Instalacion.barrerJarsViejos(rutas("aoe2radar-1.3.0.jar")));
    }

    // ------------------------------------------------------------------ varios

    @Test void activaSoloConCfgReconocibleDelJarEnUso() throws Exception {
        assertTrue(Instalacion.activa(rutas("aoe2radar-1.3.0.jar")));
        assertFalse(Instalacion.activa(rutas("aoe2radar-1.2.jar")), "el .cfg arranca otro jar");
        assertFalse(Instalacion.activa(null));
        Files.writeString(cfg, "roto");
        assertFalse(Instalacion.activa(rutas("aoe2radar-1.3.0.jar")));
    }

    @Test void versionDeJar() {
        assertEquals("1.3.0", Instalacion.versionDeJar("aoe2radar-1.3.0.jar"));
        assertEquals("1.5", Instalacion.versionDeJar("aoe2radar-1.5.jar"));
        assertEquals("1.4.0-SNAPSHOT", Instalacion.versionDeJar("aoe2radar-1.4.0-SNAPSHOT.jar"));
        assertNull(Instalacion.versionDeJar("aoe2radar-x.jar"));
        assertNull(Instalacion.versionDeJar("flatlaf-3.7.2.jar"));
    }

    @Test void unaListaMalApuntadaNoVale() throws Exception {
        Files.createDirectories(dir);
        assertTrue(Instalacion.guardarLista(dir, lista("1.5", jarNuevo)));
        assertEquals(lista("1.5", jarNuevo), Instalacion.leerLista(dir), "ida y vuelta");
        String[][] malos = {
                { "jar", "..\\malo.jar" }, { "jar", "aoe2radar-9.9.jar" }, { "version", "1.5-rc1" }, { "sha256", "corto" },
                { "size", "diez" }, { "size", "0" }, { "runtime", null }, { "mainclass", null }, { "classpath.n", "3" },
                { "opciones.n", null } };
        for (String[] m : malos) {
            assertTrue(Instalacion.guardarLista(dir, lista("1.5", jarNuevo)));
            java.util.Properties p = Instalacion.leerProps(dir.resolve(Instalacion.LISTA));
            if (m[1] == null) p.remove(m[0]); else p.setProperty(m[0], m[1]);
            assertTrue(Instalacion.escribirProps(dir.resolve(Instalacion.LISTA), p));
            assertNull(Instalacion.leerLista(dir), m[0] + "=" + m[1]);
        }
    }

    // ------------------------------------------------------------------ hallazgos del revisor

    /** Files.copy en Windows conserva la fecha del original: el jar recién colocado parecería viejo y el barrido de
     *  otra instancia que arranca lo borraría, dejando el .cfg apuntando a nada. */
    @Test void elJarRecienColocadoNoLoBarreOtraInstancia() throws Exception {
        lista15();
        Files.setLastModifiedTime(dir.resolve("aoe2radar-1.5.jar"), FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
        assertEquals(Instalacion.Aplicacion.APLICADA, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        Files.writeString(cfg, CFG_REAL, StandardCharsets.ISO_8859_1);   // aunque el .cfg aún no lo nombrara…
        Files.delete(dir.resolve(Instalacion.MARCA));                     // …ni hubiera marca
        assertEquals(List.of(), Instalacion.barrerJarsViejos(rutas("aoe2radar-1.3.0.jar")));
        assertTrue(Files.exists(app.resolve("aoe2radar-1.5.jar")));
    }

    @Test void elBarridoNoTocaLosJarsDeUnaMarcaPendiente() throws Exception {
        lista15();
        assertEquals(Instalacion.Aplicacion.APLICADA, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        envejecer(app.resolve("aoe2radar-1.5.jar"));
        envejecer(app.resolve("aoe2radar-1.3.0.jar"));
        // una instancia de la 1.3.0 aún abierta confirma su arranque: ni el nuevo (lo nombra el .cfg) ni el anterior
        // (hace falta para volver atrás) se borran mientras la marca siga
        Instalacion.confirmar(rutas("aoe2radar-1.3.0.jar"), "1.3");
        assertTrue(Files.exists(app.resolve("aoe2radar-1.5.jar")));
        assertTrue(Files.exists(app.resolve("aoe2radar-1.3.0.jar")));
    }

    @Test void siElCfgCambiaMientrasSeAplicaNoSePisa() throws Exception {
        lista15();
        String delInstalador = CfgLanzador.reescribir(CFG_REAL, "aoe2radar-1.6.0.jar", "1.6");
        Instalacion.antesDeEscribirCfg = () -> {
            try { Files.writeString(cfg, delInstalador, StandardCharsets.ISO_8859_1); } catch (IOException e) { throw new RuntimeException(e); }
        };
        try {
            assertEquals(Instalacion.Aplicacion.FALLO, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        } finally {
            Instalacion.antesDeEscribirCfg = () -> { };
        }
        assertEquals(delInstalador, cfgAhora(), "gana el que escribió después de leer");
        assertFalse(Files.exists(dir.resolve(Instalacion.MARCA)));
    }

    @Test void loDescargadoParaOtroRuntimeODependenciasNoSeAplica() throws Exception {
        lista15();
        assertEquals(Instalacion.Aplicacion.NADA, Instalacion.aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3", "21.0.10"));
        assertEquals(CFG_REAL, cfgAhora());
        assertNull(Instalacion.leerLista(dir), "se olvida: la próxima comprobación decide otra vez");
        lista15();
        Files.writeString(cfg, CFG_REAL.replace("flatlaf-3.7.2.jar", "flatlaf-3.8.jar"), StandardCharsets.ISO_8859_1);   // un instalador nuevo
        assertEquals(Instalacion.Aplicacion.NADA, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        assertTrue(cfgAhora().contains("aoe2radar-1.3.0.jar"));
    }

    @Test void arranquesALaVezNoCuentanComoFallos() throws Exception {
        lista15();
        aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3");
        Instalacion.Rutas nueva = rutas("aoe2radar-1.5.jar");
        // triple clic con un primer arranque lento: los tres procesos siguen vivos
        assertEquals(Instalacion.Arranque.TRAS_ACTUALIZAR, arrancar(nueva, 101, 101L));
        assertEquals(Instalacion.Arranque.TRAS_ACTUALIZAR, arrancar(nueva, 102, 101L, 102L));
        assertEquals(Instalacion.Arranque.TRAS_ACTUALIZAR, arrancar(nueva, 103, 101L, 102L, 103L));
        assertEquals(CFG_15, cfgAhora());
        assertNull(Instalacion.versionFallida(dir));
    }

    @Test void pidsIlegiblesEnLaMarcaCuentanComoFallos() throws Exception {
        lista15();
        aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3");
        java.util.Properties m = Instalacion.leerProps(dir.resolve(Instalacion.MARCA));
        m.setProperty("pids", "x,y");
        Instalacion.escribirProps(dir.resolve(Instalacion.MARCA), m);
        assertEquals(Instalacion.Arranque.REVERTIDO, arrancar(rutas("aoe2radar-1.5.jar"), 5));
    }

    @Test void sinLaCopiaDelCfgNoSeRevierteYSeDejaDeVigilar() throws Exception {
        lista15();
        aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3");
        Files.delete(dir.resolve(Instalacion.CFG_ANTERIOR));
        Instalacion.Rutas nueva = rutas("aoe2radar-1.5.jar");
        assertFalse(Instalacion.revertir(nueva, "prueba"));
        assertEquals(CFG_15, cfgAhora());
        arrancar(nueva, 101);
        arrancar(nueva, 102);
        assertEquals(Instalacion.Arranque.NORMAL, arrancar(nueva, 103), "no se puede volver atrás: se sigue con la nueva");
        assertFalse(Files.exists(dir.resolve(Instalacion.MARCA)), "y sin repetir el intento en cada arranque");
        assertEquals(CFG_15, cfgAhora());
    }

    @Test void siElProcesoMuereEntreLaMarcaYElCfgElArranqueEsNormal() throws Exception {
        lista15();
        Instalacion.antesDeEscribirCfg = () -> { throw new IllegalStateException("se fue la luz"); };
        try {
            assertEquals(Instalacion.Aplicacion.FALLO, aplicar(rutas("aoe2radar-1.3.0.jar"), "1.3"));
        } finally {
            Instalacion.antesDeEscribirCfg = () -> { };
        }
        assertEquals(CFG_REAL, cfgAhora());
        assertEquals(Instalacion.Arranque.NORMAL, arrancar(rutas("aoe2radar-1.3.0.jar"), 1));
        assertFalse(Files.exists(dir.resolve(Instalacion.MARCA)));
    }

    static void envejecer(Path p) throws IOException {
        Files.setLastModifiedTime(p, FileTime.from(Instant.now().minus(Instalacion.EDAD_BARRIDO).minusSeconds(60)));
    }

    static boolean sinTemporales(Path d) throws IOException {
        try (Stream<Path> s = Files.list(d)) {
            return s.noneMatch(p -> p.getFileName().toString().endsWith(".parcial") || p.getFileName().toString().endsWith(".tmp"));
        }
    }
}
