package dev.tirador.aoe2radar.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class Identidad {
    private Identidad() {}

    // ----- Versión: una sola fuente, el <version> de pom.xml ---------------------
    /** Recurso que Maven rellena al compilar (src/main/resources-filtradas, filtrado en el pom). */
    static final String RECURSO_VERSION = "version.properties";
    /** Si no hay recurso filtrado (clase compilada fuera de Maven): versión «desconocida». Con ella el comprobador
     *  de actualizaciones ve cualquier tag vX.Y como más nuevo; es inocuo y solo pasa fuera de un build de Maven. */
    static final String VERSION_DESCONOCIDA = "0.0";
    /** Misma expresión que la regex-property «version-app» del pom (la que usa jpackage): cambiar las dos a la vez;
     *  IdentidadTest comprueba que dan lo mismo con la versión real del pom. */
    static final String REGLA_CORTA = "^(?:(\\d+\\.\\d+)\\.0|([^-]+))(?:-.*)?$";
    /** Y el mismo reemplazo que su &lt;replacement&gt;. */
    static final String REEMPLAZO_CORTA = "$1$2";

    /** Versión del pom tal cual (p. ej. «1.3.0»), o null si el recurso no está filtrado. */
    public static final String VERSION_POM = leerVersionPom();
    /** La que enseña la app (título, Acerca de) y compara con los tags vX.Y: «1.3.0» -> «1.3». */
    public static final String VERSION     = VERSION_POM == null ? VERSION_DESCONOCIDA : versionCorta(VERSION_POM);

    /** Versión del pom -> versión de la app: sin sufijo «-algo» y sin un tercer tramo «.0».
     *  1.3.0 -> 1.3 · 1.3.1 -> 1.3.1 · 1.4.0-SNAPSHOT -> 1.4 · 2.0 -> 2.0. */
    public static String versionCorta(String versionPom) {
        return versionPom.trim().replaceAll(REGLA_CORTA, REEMPLAZO_CORTA);
    }

    /** Lee version.pom del recurso filtrado. Nunca lanza: un fallo en el inicializador estático tumbaría el arranque. */
    static String leerVersionPom() {
        try (InputStream in = Identidad.class.getResourceAsStream(RECURSO_VERSION)) {
            if (in == null) return null;
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            String v = p.getProperty("version.pom");
            return v == null || v.isBlank() || v.contains("${") ? null : v.trim();
        } catch (Exception e) {
            return null;
        }
    }

    // ----- Identidad / autoría ----------------------------------------------
    public static final String NOMBRE         = "aoe2radar";   // nombre del producto (la clase sigue llamándose SpoilerFreeRecs)
    public static final String AUTOR          = "12Tirador";
    public static final String CLAN           = "R1";
    public static final String AUTOR_COMPLETO = "Jorge «12Tirador» Guardiola";
    public static final String TWITCH         = "twitch.tv/12tirador";

    public static final String REPO_URL = "https://github.com/jguardiola-dev/aoe2radar";
    public static final String RELEASES_URL = REPO_URL + "/releases/latest";
    public static final String RELEASES_API = "https://api.github.com/repos/jguardiola-dev/aoe2radar/releases/latest";
}
