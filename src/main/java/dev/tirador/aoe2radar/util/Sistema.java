package dev.tirador.aoe2radar.util;

import java.nio.file.Path;

import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;

/** Integración con Windows: ruta del propio exe y arranque con el sistema (registro Run). */
public final class Sistema {
    private Sistema() {}

    /** Ruta del exe real cuando corremos empaquetados con jpackage; null si no. */
    public static String rutaExePropia() {
        String p = System.getProperty("jpackage.app-path");
        return p == null || p.isBlank() ? null : p;
    }

    /** Carpeta de los datos de la app (config.properties, players.txt, recs/, sfrdata/, techtree/, descargas.log):
     *  la del exe cuando corremos empaquetados; si no, el directorio de trabajo, como siempre. Arreglo F1 de la
     *  revisión 1.3: el autoarranque de Windows (clave Run) lanza el exe con otro directorio de trabajo
     *  (normalmente C:\Windows\System32) y las rutas relativas no encontraban nada. */
    public static Path carpetaBase() { return carpetaBase(System.getProperty("jpackage.app-path")); }

    /** Pura, para el test: la carpeta del exe si hay ruta de exe; si no, {@code Path.of("")}. Con la ruta vacía,
     *  {@code carpetaBase().resolve("x")} es exactamente {@code Path.of("x")}: fuera del exe (mvn, tests, harness)
     *  todo sigue siendo relativo al directorio de trabajo, byte a byte como antes. */
    static Path carpetaBase(String rutaExe) {
        if (rutaExe == null || rutaExe.isBlank()) return Path.of("");
        Path padre = Path.of(rutaExe).toAbsolutePath().getParent();
        return padre != null ? padre : Path.of("");
    }

    /** Un archivo o carpeta de datos de la app, dentro de {@link #carpetaBase()}. */
    public static Path enCarpetaBase(String nombre) { return carpetaBase().resolve(nombre); }

    static final String CLAVE_RUN = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    /** Escribe o borra la entrada de autoarranque en el registro del usuario
     *  con reg.exe (sin permisos de administrador). Devuelve si fue bien. */
    public static boolean fijarAutoArranque(boolean activar) {
        String exe = rutaExePropia();
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return false;
        if (activar && exe == null) return false;
        try {
            ProcessBuilder pb = activar
                    ? new ProcessBuilder("reg", "add", CLAVE_RUN, "/v", NOMBRE,
                            "/t", "REG_SZ", "/d", "\"" + exe + "\"", "/f")
                    : new ProcessBuilder("reg", "delete", CLAVE_RUN, "/v", NOMBRE, "/f");
            Process pr = pb.redirectErrorStream(true).start();
            pr.getInputStream().readAllBytes();
            return pr.waitFor() == 0 || !activar;   // borrar lo inexistente también vale
        } catch (Exception e) { return false; }
    }
}
