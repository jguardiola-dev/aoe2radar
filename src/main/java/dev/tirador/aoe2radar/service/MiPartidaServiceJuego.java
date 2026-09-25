package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.cache.Paises;
import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.util.Json;
import dev.tirador.aoe2radar.util.Reloj;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import static dev.tirador.aoe2radar.api.Http.descargarBytes;
import static dev.tirador.aoe2radar.cache.HistorialDisco.ACTIVIDAD_CACHE;
import static dev.tirador.aoe2radar.service.Juego.OFICIAL_LOBBIES;
import static dev.tirador.aoe2radar.service.Juego.buscarLobbyConPid;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.NOMBRES_AYER;
import static dev.tirador.aoe2radar.sfrdata.Snapshots.cargarEloAyer;
import static dev.tirador.aoe2radar.util.I18n.t;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * La MiPartidaService de la app: MainLog.txt de verdad (Files/FileChannel), config real (inyectada, como el
 * resto de servicios: BiFunction/BiConsumer, ver service.Campanas) y la llamada de red a la lista pública de
 * lobbies oficiales. Tal cual iniciarVigilanciaLogJuego/leerLogJuego/sondearLobbyOficial de la 1.1: mismos
 * textos de log, mismo throttle (120s para el aviso, 600s para el diagnóstico), misma ventana de lectura
 * (últimos 4000 bytes al empezar una sesión nueva, hasta 512.000 bytes por lectura).
 * <p>elo1v1Conocido llega por constructor (LongFunction) porque hoy vive en la ventana (ELO_1V1, EloSesion de
 * sesión): igual que eloDe en service.Campanas, no se duplica esa caché aquí.
 */
public final class MiPartidaServiceJuego implements MiPartidaService {

    private final LongFunction<Integer> elo1v1Conocido;
    private final BiFunction<String, String, String> leerConfig;
    private final BiConsumer<String, String> guardarConfig;
    private final Reloj reloj;
    private final Supplier<Path> carpetaLogsJuego;

    // Estado de la sesión de vigilancia (logJuegoActual/logJuegoPos/logJuegoUltimaFase/
    // logJuegoUltimoAvisoMs/logJuegoUltimoDiagMs de la 1.1).
    private Path logActual;
    private long logPos;
    private String logUltimaFase;
    private long ultimoAvisoMs;
    private long ultimoDiagMs;

    public MiPartidaServiceJuego(LongFunction<Integer> elo1v1Conocido, BiFunction<String, String, String> leerConfig,
                                  BiConsumer<String, String> guardarConfig, Reloj reloj, Supplier<Path> carpetaLogsJuego) {
        this.elo1v1Conocido = elo1v1Conocido;
        this.leerConfig = leerConfig;
        this.guardarConfig = guardarConfig;
        this.reloj = reloj;
        this.carpetaLogsJuego = carpetaLogsJuego;
    }

    @Override
    public Identidad identidad() {
        String pid = leerConfig.apply("mi_pid", "");
        if (pid.isBlank()) return null;
        return new Identidad(Long.parseLong(pid), leerConfig.apply("mi_nombre", ""));
    }

    @Override
    public void fijarIdentidad(long pid, String nombre) {
        guardarConfig.accept("mi_pid", String.valueOf(pid));
        guardarConfig.accept("mi_nombre", nombre);
    }

    @Override
    public void registrarInicioVigilancia() {
        Path carpeta = carpetaLogsJuego.get();
        log("mi partida: vigilancia del log del juego activa (carpeta " + carpeta + ", existe=" + Files.isDirectory(carpeta) + "); socket con mi id " + leerConfig.apply("mi_pid", ""));
    }

    @Override
    public boolean leerLogJuego() {
        try {
            if (leerConfig.apply("mi_pid", "").isBlank()) return false;
            Path carpeta = carpetaLogsJuego.get();
            if (!Files.isDirectory(carpeta)) return false;
            Path sesion;
            try (var st = Files.list(carpeta)) {
                sesion = st.filter(Files::isDirectory)
                        .max(Comparator.comparing(p2 -> { try { return Files.getLastModifiedTime(p2).toMillis(); } catch (IOException ex) { return 0L; } }))
                        .orElse(null);
            }
            if (sesion == null) return false;
            Path miLog = sesion.resolve("MainLog.txt");
            if (!Files.exists(miLog)) {
                if (reloj.ahoraMs() - ultimoDiagMs > 600_000) {
                    ultimoDiagMs = reloj.ahoraMs();
                    log("mi partida: la sesión más reciente (" + sesion.getFileName() + ") no tiene MainLog.txt");
                }
                return false;
            }
            if (!miLog.equals(logActual)) {   // sesión nueva: empezar por el final
                logActual = miLog;
                logPos = Math.max(0, Files.size(miLog) - 4000);
                logUltimaFase = null;
                log("mi partida: vigilando " + miLog + " (" + Files.size(miLog) + " bytes)");
            }
            long tam = Files.size(miLog);
            if (tam < logPos) logPos = 0;
            if (tam == logPos) return false;
            String trozo;
            try (var ch = FileChannel.open(miLog, StandardOpenOption.READ)) {
                ByteBuffer buf = ByteBuffer.allocate((int) Math.min(tam - logPos, 512_000));
                ch.position(logPos); ch.read(buf); buf.flip();
                trozo = StandardCharsets.UTF_8.decode(buf).toString();
                logPos = tam;
            }
            if (reloj.ahoraMs() - ultimoDiagMs > 600_000) {
                ultimoDiagMs = reloj.ahoraMs();
                log("mi partida: el log del juego crece (" + tam + " bytes); último trozo " + trozo.length() + " caracteres");
            }
            if (trozo.contains("PlayerReadyRequest") || trozo.contains("MS_Setup")) {
                if (reloj.ahoraMs() - ultimoAvisoMs > 120_000) {   // una vez por partida
                    ultimoAvisoMs = reloj.ahoraMs();
                    log("mi partida: fase de preparación detectada en el log del juego");
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log("log del juego: " + causa(ex));
            return false;
        }
    }

    @Override
    public ResultadoLobby sondearLobbyOficial() {
        try {
            long mi = Long.parseLong(leerConfig.apply("mi_pid", ""));
            String texto = new String(descargarBytes(OFICIAL_LOBBIES, 20), StandardCharsets.UTF_8);
            Object root = Json.parse(texto);
            List<Long> companeros = new ArrayList<>();
            boolean encontrado = buscarLobbyConPid(root, mi, companeros);
            log("lobby oficial: " + (encontrado ? "mi lobby encontrado con " + companeros.size() + " jugadores más" : "mi lobby no aparece") + " (respuesta de " + texto.length() + " caracteres)");
            if (!encontrado || companeros.isEmpty()) return new ResultadoLobby(false, "", List.of());
            cargarEloAyer();
            StringBuilder sb = new StringBuilder("\u25CF " + t("Partida encontrada · con ", "Match found · with "));
            List<Object[]> fichas = new ArrayList<>();
            for (long pid : companeros) {
                String[] nn = NOMBRES_AYER.get(pid);
                String nombre = nn != null ? nn[0] : "#" + pid;
                Integer e1 = elo1v1Conocido.apply(pid);
                fichas.add(new Object[]{ pid, nombre, e1 });
                if (sb.length() > 30) sb.append(", ");
                sb.append(nombre).append(e1 != null ? " (" + e1 + ")" : "");
            }
            return new ResultadoLobby(true, sb.toString(), fichas);
        } catch (Exception ex) {
            log("lobby oficial: " + causa(ex));
            return new ResultadoLobby(false, "", List.of());
        }
    }

    @Override
    public String paisDe(long pid) { return Paises.paisDe(pid); }

    @Override
    public List<String> civsRecientes(long pid) {
        Actividad a = ACTIVIDAD_CACHE.get(pid);
        if (a == null) return List.of();
        Map<String, Integer> civs = new HashMap<>();
        Instant hace30 = Instant.ofEpochMilli(reloj.ahoraMs()).minus(Duration.ofDays(30));
        for (Match m : a.partidas())
            if (m.started != null && m.started.isAfter(hace30))
                for (MatchPlayer mp : m.players)
                    if (mp.id == pid && mp.civ != null) civs.merge(mp.civ, 1, Integer::sum);
        List<Map.Entry<String, Integer>> top = new ArrayList<>(civs.entrySet());
        top.sort((x, y) -> y.getValue() - x.getValue());
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(3, top.size()); i++) out.add(top.get(i).getKey());
        return out;
    }
}
