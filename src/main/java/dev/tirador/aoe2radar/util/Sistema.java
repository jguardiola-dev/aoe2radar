package dev.tirador.aoe2radar.util;

import static dev.tirador.aoe2radar.util.Identidad.NOMBRE;

/** Integración con Windows: ruta del propio exe y arranque con el sistema (registro Run). */
public final class Sistema {
    private Sistema() {}

    /** Ruta del exe real cuando corremos empaquetados con jpackage; null si no. */
    public static String rutaExePropia() {
        String p = System.getProperty("jpackage.app-path");
        return p == null || p.isBlank() ? null : p;
    }

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
