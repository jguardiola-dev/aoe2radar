package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Versión con una sola fuente: el &lt;version&gt; de pom.xml. Maven filtra version.properties y Identidad lo lee;
 * aquí se comprueba que el recurso llega filtrado, que coincide con el pom y que la conversión a la versión
 * de la app (la del título, «aoe2radar 1.3 — …», y la de los tags vX.Y) es la esperada.
 */
class IdentidadTest {

    /** El &lt;version&gt; del proyecto en pom.xml (el que va justo tras el artifactId, no el de una dependencia). */
    static String versionDelPom() throws Exception {
        // surefire pasa basedir (systemPropertyVariables del pom): el directorio de trabajo es target/harness
        Path pom = Path.of(System.getProperty("basedir", "."), "pom.xml");
        Matcher m = Pattern.compile("<artifactId>aoe2radar</artifactId>\\s*<version>([^<]+)</version>")
                .matcher(Files.readString(pom, StandardCharsets.UTF_8));
        if (!m.find()) throw new AssertionError("no encuentro el <version> del proyecto en " + pom);
        return m.group(1).trim();
    }

    static Properties recurso() throws Exception {
        try (InputStream in = Identidad.class.getResourceAsStream(Identidad.RECURSO_VERSION)) {
            assertNotNull(in, "falta " + Identidad.RECURSO_VERSION + " en el classpath (¿se borró resources-filtradas?)");
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return p;
        }
    }

    @Test void elRecursoLlegaFiltradoPorMaven() throws Exception {
        Properties p = recurso();
        assertFalse(p.getProperty("version.pom", "${").contains("${"), "version.pom sin filtrar: " + p);
        assertFalse(p.getProperty("version.app", "${").contains("${"), "version.app sin filtrar: " + p);
    }

    @Test void laVersionLeidaEsLaDelPom() throws Exception {
        assertEquals(versionDelPom(), Identidad.VERSION_POM);
    }

    @Test void laVersionDeLaAppEsLaCortaDelPom() throws Exception {
        assertEquals(Identidad.versionCorta(versionDelPom()), Identidad.VERSION);
    }

    /** jpackage (--app-version) usa version.app, calculada por build-helper en el pom con la misma regla:
     *  si alguien cambia una de las dos expresiones y no la otra, esto sale rojo. */
    @Test void laDeJpackageEsLaMismaQueLaDeLaApp() throws Exception {
        assertEquals(Identidad.VERSION, recurso().getProperty("version.app"));
    }

    /** Hoy el pom dice 1.3.0 y el título del harness de capturas dice «aoe2radar 1.3 — …»: no debe cambiar. */
    @Test void conElPomActualLaAppDice13() throws Exception {
        if (versionDelPom().equals("1.3.0")) assertEquals("1.3", Identidad.VERSION);
    }

    @Test void reglaDeConversion() {
        assertEquals("1.3", Identidad.versionCorta("1.3.0"));
        assertEquals("1.3.1", Identidad.versionCorta("1.3.1"), "un parche no se pierde (si no, v1.3.1 sería siempre «nueva»)");
        assertEquals("1.3.10", Identidad.versionCorta("1.3.10"));
        assertEquals("1.30", Identidad.versionCorta("1.30.0"));
        assertEquals("1.4", Identidad.versionCorta("1.4.0-SNAPSHOT"));
        assertEquals("1.4.2", Identidad.versionCorta("1.4.2-SNAPSHOT"));
        assertEquals("2.0", Identidad.versionCorta("2.0"));
        assertEquals("1.3", Identidad.versionCorta(" 1.3.0 "));
    }

    /** La versión corta se compara con los tags de GitHub (vX.Y) en MenuConfiguracion: comprobarlo con la real. */
    @Test void elComprobadorDeActualizacionesLaEntiende() {
        assertFalse(Texto.versionMayor("v" + Identidad.VERSION, Identidad.VERSION), "el tag de esta misma versión no es «nuevo»");
        assertFalse(Texto.versionMayor("v1.2", "1.3"));
        assertEquals(true, Texto.versionMayor("v1.3.1", "1.3"));
    }
}
