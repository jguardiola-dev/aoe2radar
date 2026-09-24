package dev.tirador.aoe2radar.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.tirador.aoe2radar.model.Match;

import static dev.tirador.aoe2radar.cache.RecsDisco.destino;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/** Integración con AoE2 DE en este PC: carpeta de savegames, CaptureAge, logs del juego, cuenta de Steam activa y lobby. */
public final class Juego {
    private Juego() {}

    /** Carpetas savegame de AoE2 DE del usuario:
     *  %USERPROFILE%\Games\Age of Empires 2 DE\<perfil>\savegame
     *  (misma ruta en Steam y Microsoft Store). */
    public static List<Path> detectarSavegames() {
        List<Path> out = new ArrayList<>();
        String home = System.getProperty("user.home");
        if (home == null) return out;
        Path base = Path.of(home, "Games", "Age of Empires 2 DE");
        if (!Files.isDirectory(base)) return out;
        try (var st = Files.list(base)) {
            for (Path perfil : st.toList()) {
                Path sg = perfil.resolve("savegame");
                if (Files.isDirectory(sg)) out.add(sg);
            }
        } catch (IOException ignored) {}
        return out;
    }

    /** Ruta de CaptureAge: la fijada en Configuración o la instalación
     *  estándar; null si no aparece. */
    public static Path rutaCaptureAge() {
        String cfg = leerConfig("ca_ruta", "");
        if (!cfg.isBlank()) {
            Path p = Path.of(cfg);
            if (Files.exists(p)) return p;
        }
        String lad = System.getenv("LOCALAPPDATA");
        if (lad != null) {
            Path p = Path.of(lad, "Programs", "CaptureAge", "CaptureAge.exe");
            if (Files.exists(p)) return p;
        }
        return null;
    }

    public static final String OFICIAL_LOBBIES = "https://aoe-api.worldsedgelink.com/community/advertisement/findAdvertisements?title=age2&start=0&count=200";

    /** SteamID activo según el registro de Windows (HKCU\\Software\\Valve\\Steam\\ActiveProcess\\ActiveUser, id de 32 bits) → id64. null si no hay Steam. */
    public static Long steamIdActivo() {
        try {
            Process p = new ProcessBuilder("reg", "query", "HKCU\\Software\\Valve\\Steam\\ActiveProcess", "/v", "ActiveUser").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("ActiveUser\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)").matcher(out);
            if (!m.find()) return null;
            long id32 = Long.parseLong(m.group(1), 16);
            return id32 > 0 ? 76561197960265728L + id32 : null;
        } catch (Exception ex) { return null; }
    }
    /** Carpeta de logs del juego: %USERPROFILE%\\Games\\Age of Empires 2 DE\\logs (o la indicada en config «logs_juego»). */
    public static Path carpetaLogsJuego() {
        String cfg = leerConfig("logs_juego", "");
        if (!cfg.isBlank()) return Path.of(cfg);
        return Path.of(System.getProperty("user.home"), "Games", "Age of Empires 2 DE", "logs");
    }

    /** Copia la rec ya descargada de la partida a savegame. Solo copia y solo
     *  sobrescribe su propio nombre (misma partida): nunca borra nada del juego. */
    public static boolean copiarASavegame(Match m, Path sg) {
        try {
            Path origen = destino(m);
            Files.copy(origen, sg.resolve(origen.getFileName().toString()),
                    StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ex) {
            log("no se pudo copiar a savegame: " + causa(ex));
            return false;
        }
    }

    /** Busca en cualquier estructura JSON un lobby cuyos miembros incluyan mi pid; devuelve los demás ids. */
    public static boolean buscarLobbyConPid(Object nodo, long mi, List<Long> otros) {
        if (nodo instanceof Map<?, ?> m) {
            for (Object k : List.of("matchmembers", "members", "players")) {
                if (m.get(k) instanceof List<?> l) {
                    List<Long> ids = new ArrayList<>();
                    for (Object o : l) { if (o instanceof Map<?, ?> mm) { Object pid = mm.get("profile_id"); if (pid == null) pid = mm.get("profileId"); if (pid == null) pid = mm.get("profileid"); if (pid instanceof Number n) ids.add(n.longValue()); } else if (o instanceof Number n) ids.add(n.longValue()); }
                    if (ids.contains(mi)) { for (long id : ids) if (id != mi) otros.add(id); return true; }
                }
            }
            for (Object v : m.values()) if (buscarLobbyConPid(v, mi, otros)) return true;
        } else if (nodo instanceof List<?> l) {
            for (Object v : l) if (buscarLobbyConPid(v, mi, otros)) return true;
        }
        return false;
    }
}
