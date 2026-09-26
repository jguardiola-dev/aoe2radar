package dev.tirador.aoe2radar.util;

import dev.tirador.aoe2radar.cache.Directorios;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carpetas de la app. Carpeta del exe (arreglo F1 de la revisión 1.3): se calcula desde la ruta del exe, venga de
 * donde venga el directorio de trabajo (el autoarranque de Windows lanza el exe desde System32). Carpeta de datos
 * (1.4): empaquetada (jpackage o Conveyor), %APPDATA%\aoe2radar, salvo modo portátil; recs en Documentos. Sin
 * paquete (mvn, tests, harness), todo sigue relativo, idéntico a antes. Nunca se toca el %APPDATA% ni el registro
 * reales: rutas, entorno y salida de reg.exe se inyectan.
 */
class SistemaTest {

    @TempDir Path tmp;

    private static final Path SIN_DOCS = null;

    // ------------------------------------------------------------ ¿empaquetada? carpeta de la app

    @Test void empaquetadaConJpackageLaCarpetaEsLaDelExe() {
        Path base = Sistema.carpetaExe("C:\\Apps\\aoe2radar\\aoe2radar.exe");
        assertEquals(Path.of("C:\\Apps\\aoe2radar"), base);
        assertEquals(Path.of("C:\\Apps\\aoe2radar"), Sistema.carpetaInstalacion("C:\\Apps\\aoe2radar\\aoe2radar.exe", null));
        assertEquals(Path.of("C:\\Apps\\aoe2radar"),
                Sistema.carpetaInstalacion("C:\\Apps\\aoe2radar\\aoe2radar.exe", "C:\\otra"), "jpackage manda");
    }

    @Test void empaquetadaConConveyorLaCarpetaEsAppDir() {
        assertEquals(Path.of("C:\\Program Files\\WindowsApps\\aoe2radar_1.4\\app"),
                Sistema.carpetaInstalacion(null, "C:\\Program Files\\WindowsApps\\aoe2radar_1.4\\app"));
        assertEquals(Path.of("C:\\x\\app"), Sistema.carpetaInstalacion("  ", "C:\\x\\app"));
    }

    @Test void sinNingunaSenalNoEstaEmpaquetada() {
        assertNull(Sistema.carpetaInstalacion(null, null));
        assertNull(Sistema.carpetaInstalacion("", " "));
        assertEquals(Path.of(""), Sistema.carpetaExe(null));
        assertEquals(Path.of("config.properties"), Sistema.carpetaExe(null).resolve("config.properties"));
    }

    @Test void enLosTestsNoHayPaqueteYLasConstantesNoCambian() {
        // Surefire no fija jpackage.app-path ni app.dir: las rutas deben ser exactamente las relativas de siempre.
        assertFalse(Sistema.empaquetada());
        assertEquals(Path.of("config.properties"), Config.CONFIG_FILE);
        assertEquals(Path.of("descargas.log"), Log.LOG_FILE);
        assertEquals(Path.of("sfrdata"), Directorios.LADDER_DIR);
        assertEquals(Path.of("banderas"), dev.tirador.aoe2radar.service.ImagenesJuego.BANDERAS_DIR);
        assertEquals(Path.of("recs"), dev.tirador.aoe2radar.cache.RecsDisco.RECS_DIR);
        assertEquals(Path.of(""), Sistema.carpetaApp());
        assertEquals(Path.of(""), Sistema.carpetaBase());
        assertFalse(Sistema.datosNuevos());
        assertNull(Sistema.avisoCarpetaDatos());
    }

    // ------------------------------------------------------------ carpeta de datos

    @Test void sinPaqueteLaCarpetaDeDatosEsLaDeTrabajoYNoSeCreaNada() {
        Sistema.Resolucion r = Sistema.resolver(null, tmp.toString(), tmp.toString(), () -> tmp.resolve("docs"));
        assertEquals(Path.of(""), r.carpeta());
        assertNull(r.documentos());
        assertFalse(r.datosNuevos());
        assertNull(r.aviso());
        assertFalse(Files.exists(tmp.resolve("aoe2radar")));
    }

    @Test void empaquetadaLaCarpetaDeDatosEsAppdataAoe2radarYSeCrea() throws IOException {
        Path app = Files.createDirectories(tmp.resolve("Program Files").resolve("aoe2radar"));
        Path roaming = tmp.resolve("Roaming");
        Path docs = tmp.resolve("OneDrive").resolve("Documentos");
        assertEquals(roaming.resolve("aoe2radar"), Sistema.carpetaDatos(app, roaming.toString(), "C:\\ignorado"));

        Sistema.Resolucion r = Sistema.resolver(app, roaming.toString(), "C:\\ignorado", () -> docs);
        assertEquals(roaming.resolve("aoe2radar"), r.carpeta());
        assertTrue(Files.isDirectory(r.carpeta()), "se crea si no existe");
        assertEquals(docs, r.documentos());
        assertTrue(r.datosNuevos(), "sin config ni players: usuario nuevo en esta carpeta");
        assertNull(r.aviso());
        assertFalse(Files.exists(app.resolve("config.properties")), "nada se escribe en la carpeta de la app");
    }

    @Test void conDatosYaEnAppdataNoSonNuevos() throws IOException {
        Path app = Files.createDirectories(tmp.resolve("app"));
        Path datos = Files.createDirectories(tmp.resolve("Roaming").resolve("aoe2radar"));
        Files.writeString(datos.resolve("players.txt"), "1;Ana\n");
        assertFalse(Sistema.resolver(app, tmp.resolve("Roaming").toString(), null, () -> SIN_DOCS).datosNuevos());
    }

    @Test void sinAppdataSeUsaUserHomeAppDataRoaming() {
        Path app = tmp.resolve("app");
        Path esperado = tmp.resolve("home").resolve("AppData").resolve("Roaming").resolve("aoe2radar");
        assertEquals(esperado, Sistema.carpetaDatos(app, null, tmp.resolve("home").toString()));
        assertEquals(esperado, Sistema.carpetaDatos(app, "  ", tmp.resolve("home").toString()));
    }

    @Test void conPortableLosDatosSiguenJuntoAlExe() throws IOException {
        for (String marca : new String[] { "portable", "portable.txt" }) {
            Path app = Files.createDirectories(tmp.resolve("app_" + marca));
            Files.writeString(app.resolve(marca), "");
            Path roaming = tmp.resolve("Roaming_" + marca);
            assertEquals(app, Sistema.carpetaDatos(app, roaming.toString(), null));
            Sistema.Resolucion r = Sistema.resolver(app, roaming.toString(), null, () -> tmp.resolve("docs"));
            assertEquals(app, r.carpeta());
            assertNull(r.documentos(), "portátil: las recs van junto al exe, no a Documentos");
            assertFalse(r.datosNuevos(), "portátil: no se ofrece importar");
            assertFalse(Files.exists(roaming), "en modo portátil no se toca APPDATA");
            assertEquals(app.resolve("recs"), Sistema.carpetaRecs(r.carpeta(), r.documentos(), null));
        }
    }

    @Test void siNoSePuedePrepararVuelveALaCarpetaDeLaApp() throws IOException {
        Path app = Files.createDirectories(tmp.resolve("app"));
        Path archivo = Files.writeString(tmp.resolve("soy_un_archivo"), "x");   // APPDATA apunta a un archivo
        Sistema.Resolucion r = Sistema.resolver(app, archivo.toString(), null, () -> SIN_DOCS);
        assertEquals(app, r.carpeta());
        assertTrue(r.aviso().startsWith("datos: no se pudo preparar"), r.aviso());
    }

    // ------------------------------------------------------------ recs y Documentos

    @Test void carpetaDeRecs() {
        Path datos = Path.of("C:\\datos"), docs = Path.of("D:\\OneDrive\\Documentos");
        assertEquals(Path.of("recs"), Sistema.carpetaRecs(Path.of(""), null, null), "fuera del paquete, como siempre");
        assertEquals(Path.of("D:\\OneDrive\\Documentos\\aoe2radar\\recs"), Sistema.carpetaRecs(datos, docs, null));
        assertEquals(Path.of("D:\\OneDrive\\Documentos\\aoe2radar\\recs"), Sistema.carpetaRecs(datos, docs, "  "));
        assertEquals(Path.of("E:\\mis recs"), Sistema.carpetaRecs(datos, docs, "E:\\mis recs"), "la del usuario se respeta");
        assertEquals(Path.of("C:\\datos\\recs"), Sistema.carpetaRecs(datos, null, null));
    }

    private static final String REG_USERPROFILE = "\r\nHKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders\r\n"
            + "    Personal    REG_EXPAND_SZ    %USERPROFILE%\\Documents\r\n\r\n";
    private static final String REG_ONEDRIVE = "\r\nHKEY_CURRENT_USER\\...\\User Shell Folders\r\n"
            + "    Personal    REG_EXPAND_SZ    C:\\Users\\Jorge Ñú\\OneDrive\\Documentos\r\n\r\n";

    @Test void valorPersonalDelRegistro() {
        Map<String, String> env = Map.of("USERPROFILE", "C:\\Users\\Jorge");
        assertEquals("C:\\Users\\Jorge\\Documents", Sistema.valorPersonal(REG_USERPROFILE, env::get));
        assertEquals("C:\\Users\\Jorge Ñú\\OneDrive\\Documentos", Sistema.valorPersonal(REG_ONEDRIVE, env::get));
        assertNull(Sistema.valorPersonal("ERROR: The system was unable to find the specified registry key or value.", env::get));
        assertNull(Sistema.valorPersonal(null, env::get));
        assertNull(Sistema.valorPersonal(REG_USERPROFILE, k -> null), "variable sin valor: no se inventa la ruta");
    }

    @Test void documentosRedirigidaORespaldo() {
        Map<String, String> env = Map.of("USERPROFILE", "C:\\Users\\Jorge");
        assertEquals(Path.of("C:\\Users\\Jorge Ñú\\OneDrive\\Documentos"),
                Sistema.documentos(REG_ONEDRIVE, env::get, "C:\\Users\\Jorge", p -> true));
        assertEquals(Path.of("C:\\Users\\Jorge\\Documents"),
                Sistema.documentos(REG_ONEDRIVE, env::get, "C:\\Users\\Jorge", p -> false), "si no existe, respaldo");
        assertEquals(Path.of("C:\\Users\\Jorge\\Documents"),
                Sistema.documentos(null, env::get, "C:\\Users\\Jorge", p -> true), "sin reg.exe, respaldo");
    }

    // ------------------------------------------------------------ relanzar

    @Test void soloSeRelanzaElExeDeLaApp() {
        assertTrue(Sistema.esLanzadorRelanzable("C:\\Program Files\\aoe2radar\\aoe2radar.exe"));
        assertFalse(Sistema.esLanzadorRelanzable("C:\\jdk\\bin\\java.exe"));
        assertFalse(Sistema.esLanzadorRelanzable("C:\\jdk\\bin\\JAVAW.EXE"));
        assertFalse(Sistema.esLanzadorRelanzable("/usr/bin/java"));
        assertFalse(Sistema.esLanzadorRelanzable(null));
        assertFalse(Sistema.relanzar(), "fuera del paquete nunca relanza");
    }
}
