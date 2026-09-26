package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.model.Directo;
import dev.tirador.aoe2radar.model.Player;
import dev.tirador.aoe2radar.util.Reloj;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

import static dev.tirador.aoe2radar.api.Freno.ctrlMult;
import static dev.tirador.aoe2radar.api.Http.HTTP;
import static dev.tirador.aoe2radar.api.Http.UA;
import static dev.tirador.aoe2radar.cache.Canales.CANAL_DE;
import static dev.tirador.aoe2radar.cache.Canales.aprenderCanal;
import static dev.tirador.aoe2radar.util.Json.firstNonNull;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;
import static dev.tirador.aoe2radar.util.Texto.variantesNick;

/**
 * Implementación de TwitchService con el companion (api.CompanionApi) y la caché de canales conocidos
 * (cache.Canales), tal cual la 1.1 (SpoilerFreeRecs.vigilarTwitch/cargarMiniaturas).
 */
public final class TwitchServiceCompanion implements TwitchService {

    /** Cuánto se recuerda que un canal comprobado uno a uno no estaba en directo (plan API de la 1.3). */
    static final long SIN_DIRECTO_MS = 10 * 60_000L;

    private final CompanionApi api;
    private final LongConsumer pausa;
    private final LongPredicate jugando;   // ¿pid está en partida ahora? (EstadoVivo, en la app)
    private final Reloj reloj;
    /** canal (minúsculas) → hasta cuándo se da por «no emite» sin volver a preguntar. */
    private final Map<String, Long> sinDirecto = new ConcurrentHashMap<>();

    /** Sin saber quién juega: cualquiera con canal conocido se puede consultar uno a uno (como hasta la 1.3). */
    public TwitchServiceCompanion(CompanionApi api, LongConsumer pausa) {
        this(api, pausa, pid -> true, Reloj.SISTEMA);
    }

    /** Con quién está jugando ahora (solo a esos se les consulta el canal uno a uno) y el reloj de la caché «no emite». */
    public TwitchServiceCompanion(CompanionApi api, LongConsumer pausa, LongPredicate jugando, Reloj reloj) {
        this.api = api;
        this.pausa = pausa;
        this.jugando = jugando;
        this.reloj = reloj;
    }

    @Override
    public Resultado barrer(List<Player> visibles) { return barrer(visibles, List.of()); }

    @Override
    public Resultado barrer(List<Player> visibles, List<Player> otrosVigilados) {
        Map<String, String[]> envivo = new HashMap<>();   // clave lower -> {canal, título, viewers}
        List<String[]> completos = new ArrayList<>();
        boolean fallo = false;
        try {
            for (Directo s : api.twitchDirectos()) {
                String canal = String.valueOf(s.login());
                if ("null".equals(canal) || canal.isBlank()) continue;
                String titulo = String.valueOf(firstNonNull(s.titulo(), ""));
                long viewers = s.viewers();
                String[] datos = { canal, titulo, String.valueOf(Math.max(0, viewers)) };
                envivo.put(canal.toLowerCase(), datos);
                String un = String.valueOf(s.nombre());
                if (!"null".equals(un) && !un.isBlank()) envivo.putIfAbsent(un.toLowerCase(), datos);
                String idioma = String.valueOf(firstNonNull(s.idioma(), "?"));
                completos.add(new String[]{ canal, "null".equals(un) || un.isBlank() ? canal : un,
                        titulo, idioma.toLowerCase(), String.valueOf(Math.max(0, viewers)) });
            }
        } catch (Exception ex) {
            log("twitch: fallo con " + CompanionApi.TWITCH_LIVE + "?game=13389: " + causa(ex));
            fallo = true;
        }
        Map<Long, String[]> res = new HashMap<>();
        List<Player> sinCruce = new ArrayList<>();
        boolean falloFinal = fallo;
        for (Player p : visibles) {
            String canal = CANAL_DE.get(p.id());
            String[] st = canal != null ? envivo.get(canal.toLowerCase()) : null;
            if (st == null)
                for (String v : variantesNick(p.name())) {
                    st = envivo.get(v);
                    if (st != null) { aprenderCanal(p.id(), st[0]); break; }
                }
            if (st != null) res.put(p.id(), st);
            else if (canal != null && !falloFinal) sinCruce.add(p);
        }
        // El proxy solo lista los 20 canales más vistos: los canales conocidos de tu lista que no
        // estén ahí se comprueban uno a uno (pocas llamadas, y el TW deja de depender de la audiencia)
        // Plan API de la 1.3: solo para quien está jugando ahora (quien emite casi siempre está en partida) y sin
        // repetir durante SIN_DIRECTO_MS un canal que ya se comprobó y no emitía. El tope de 12 cuenta llamadas hechas.
        int consultas = 0;
        long ahora = reloj.ahoraMs();
        sinDirecto.values().removeIf(hasta -> hasta <= ahora);
        for (Player p : sinCruce) {
            if (!jugando.test(p.id())) continue;
            String canal = CANAL_DE.get(p.id());
            String clave = canal.toLowerCase(Locale.ROOT);
            if (sinDirecto.containsKey(clave)) continue;
            if (consultas++ >= 12) break;
            try {
                Directo s = api.twitchCanal(canal);
                boolean emite = false;
                if (s != null) {
                    String login = String.valueOf(s.login());
                    if (!"null".equals(login) && !login.isBlank() && "live".equals(String.valueOf(s.tipo()))) {
                        String titulo = String.valueOf(firstNonNull(s.titulo(), ""));
                        long viewers = s.viewers();
                        res.put(p.id(), new String[]{ login, titulo, String.valueOf(Math.max(0, viewers)) });
                        emite = true;
                    }
                }
                if (!emite) sinDirecto.put(clave, ahora + SIN_DIRECTO_MS);   // un fallo de red (catch) no es «no emite»
            } catch (Exception ex) {
                log("twitch canal " + canal + ": " + causa(ex));
            }
            pausa.accept(150L);
        }
        int deVisibles = res.size();
        // Los demás vigilados (otros grupos, fuente de Live now): solo con su canal ya conocido y contra el listado
        // global. Sin adivinar por el nick (250 nicks contra los canales darían falsos TW) ni llamadas una a una (el
        // tope de 12 es para la lista visible). Revisión 1.3, F10.
        for (Player p : otrosVigilados) {
            if (res.containsKey(p.id())) continue;
            String canal = CANAL_DE.get(p.id());
            String[] st = canal != null ? envivo.get(canal.toLowerCase()) : null;
            if (st != null) res.put(p.id(), st);
        }
        log("twitch: " + envivo.size() / 2 + "+ directos AoE2; " + deVisibles
                + " de tu lista visible retransmitiendo" + (res.size() > deVisibles ? " (+" + (res.size() - deVisibles) + " de otros vigilados)" : ""));
        return new Resultado(res, completos, fallo);
    }

    @Override
    public Miniatura miniatura(String login, int ancho, int alto) {
        try {
            HttpRequest rq = HttpRequest.newBuilder(URI.create(
                    "https://static-cdn.jtvnw.net/previews-ttv/live_user_" + login.toLowerCase() + "-" + ancho + "x" + alto + ".jpg"))
                    .timeout(Duration.ofSeconds(10)).header("User-Agent", UA).GET().build();
            HttpResponse<byte[]> r = HTTP.send(rq, HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() / 100 != 2) return null;
            return decodificarYEscalar(r.body(), ancho, alto);
        } catch (Exception ex) {
            log("miniatura " + login + ": " + causa(ex));
            return null;
        }
    }

    /**
     * Decodifica un JPEG y lo escala a (ancho × alto) con SCALE_SMOOTH (igual que la 1.1), leyendo los píxeles ya
     * escalados con PixelGrabber (solo java.awt.image, sin Component/MediaTracker/Graphics2D). null si el JPEG no
     * se pudo decodificar o el grab de píxeles falló. Separado de miniatura() para poder probarlo sin red.
     */
    static Miniatura decodificarYEscalar(byte[] jpeg, int ancho, int alto) {
        try {
            BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(jpeg));
            if (img == null) return null;
            Image esc = img.getScaledInstance(ancho, alto, Image.SCALE_SMOOTH);
            PixelGrabber pg = new PixelGrabber(esc, 0, 0, ancho, alto, true);
            if (!pg.grabPixels()) return null;
            return new Miniatura(ancho, alto, (int[]) pg.getPixels());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public double multiplicador() {
        return ctrlMult("twitch_mult");
    }
}
