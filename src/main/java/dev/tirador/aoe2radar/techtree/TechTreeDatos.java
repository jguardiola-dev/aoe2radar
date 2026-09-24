package dev.tirador.aoe2radar.techtree;

import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import static dev.tirador.aoe2radar.api.Http.HTTP;
import static dev.tirador.aoe2radar.api.Http.UA;
import static dev.tirador.aoe2radar.api.Http.httpBytesTT;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.I18n.IDIOMA;
import static dev.tirador.aoe2radar.util.Json.obj;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/** Datos de aoe2techtree: data.json, árboles por civ y cadenas por idioma, con caché en disco y comprobación diaria por ETag. */
public final class TechTreeDatos {
    private TechTreeDatos() {}

    public static final String TT_RAW = "https://raw.githubusercontent.com/SiegeEngineers/aoe2techtree/master/";
    public static final Path TT_DIR = ttDirBase();

    /** «Extraer todo» de Windows deja techtree\techtree: se acepta la carpeta anidada. */
    public static Path ttDirBase() {
        Path base = Path.of("techtree");
        if (!Files.exists(base.resolve("data/data.json")) && Files.exists(base.resolve("techtree/data/data.json"))) return base.resolve("techtree");
        return base;
    }
    public static volatile Map<String, Object> ttData;            // data.json
    public static volatile Map<String, Object> ttStrings;         // locales/<lang>/strings.json
    public static final Map<String, Map<String, Object>> ttTrees = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<Integer, String> TT_CLASES = Map.ofEntries(   // clases de bonus, como las nombra aoe2techtree
            Map.entry(0, "Wonders"), Map.entry(1, "Infantry"), Map.entry(2, "Heavy Warships"), Map.entry(3, "Base Pierce"),
            Map.entry(4, "Base Melee"), Map.entry(5, "Elephants"), Map.entry(8, "Mounted Units"), Map.entry(11, "All Buildings"),
            Map.entry(13, "Stone Defense & Harbors"), Map.entry(14, "Predator Animals"), Map.entry(15, "All Archers"), Map.entry(16, "Ships"),
            Map.entry(17, "High Pierce Armor Siege Units"), Map.entry(18, "Trees"), Map.entry(19, "Unique Units"), Map.entry(20, "Siege Units"),
            Map.entry(21, "Standard Buildings"), Map.entry(22, "Walls & Gates"), Map.entry(23, "Gunpowder Units"), Map.entry(24, "Aggressive Huntable Animals"),
            Map.entry(25, "Monastery Units"), Map.entry(26, "Castles & Kreposts"), Map.entry(27, "Spearmen"), Map.entry(28, "Mounted Archers"),
            Map.entry(29, "Shock Infantry"), Map.entry(30, "Camels"), Map.entry(31, "Unblockable Melee"), Map.entry(32, "Condottieri"),
            Map.entry(34, "Fishing Ships"), Map.entry(35, "Mamelukes"), Map.entry(36, "Heroes & Kings"), Map.entry(37, "Heavy Siege"),
            Map.entry(38, "Skirmishers"), Map.entry(39, "Cavalry Resistance"), Map.entry(40, "Houses"), Map.entry(41, "Fire Ships"), Map.entry(60, "Long-Range Warships"));
    public static final Map<String, String> TT_CLASES_ES = Map.ofEntries(
            Map.entry("Wonders", "Maravillas"), Map.entry("Infantry", "Infantería"), Map.entry("Heavy Warships", "Barcos de guerra pesados"),
            Map.entry("Base Pierce", "Perforante base"), Map.entry("Base Melee", "Cuerpo a cuerpo base"), Map.entry("Elephants", "Elefantes"),
            Map.entry("Mounted Units", "Unidades montadas"), Map.entry("All Buildings", "Todos los edificios"), Map.entry("Stone Defense & Harbors", "Defensas de piedra y puertos"),
            Map.entry("Predator Animals", "Depredadores"), Map.entry("All Archers", "Arqueros"), Map.entry("Ships", "Barcos"),
            Map.entry("High Pierce Armor Siege Units", "Asedio con armadura perforante alta"), Map.entry("Trees", "Árboles"), Map.entry("Unique Units", "Unidades únicas"),
            Map.entry("Siege Units", "Asedio"), Map.entry("Standard Buildings", "Edificios estándar"), Map.entry("Walls & Gates", "Muros y puertas"),
            Map.entry("Gunpowder Units", "Pólvora"), Map.entry("Aggressive Huntable Animals", "Animales de caza agresivos"), Map.entry("Monastery Units", "Monjes"),
            Map.entry("Castles & Kreposts", "Castillos y kreposts"), Map.entry("Spearmen", "Lanceros"), Map.entry("Mounted Archers", "Arqueros a caballo"),
            Map.entry("Shock Infantry", "Infantería de choque"), Map.entry("Camels", "Camellos"), Map.entry("Unblockable Melee", "Cuerpo a cuerpo imbloqueable"),
            Map.entry("Condottieri", "Condotieros"), Map.entry("Fishing Ships", "Pesqueros"), Map.entry("Mamelukes", "Mamelucos"), Map.entry("Heroes & Kings", "Héroes y reyes"),
            Map.entry("Heavy Siege", "Asedio pesado"), Map.entry("Skirmishers", "Guerrilleros"), Map.entry("Cavalry Resistance", "Resistencia a caballería"),
            Map.entry("Houses", "Casas"), Map.entry("Fire Ships", "Barcos incendiarios"), Map.entry("Long-Range Warships", "Barcos de largo alcance"));

    /** Nombre de la civ en el idioma de la app (name_string_id), o la clave si no hay traducción. */
    public static String ttNombreCiv(String civ) {
        Map<String, Object> ci = ttData == null ? null : obj(obj(ttData.get("civs")).get(civ));
        String n = ci == null ? null : ttStr(ci.get("name_string_id"));
        return n == null ? civ : n;
    }

    public static String ttClase(int id) {
        String en = TT_CLASES.getOrDefault(id, "#" + id);
        return "es".equals(IDIOMA) ? TT_CLASES_ES.getOrDefault(en, en) : en;
    }

    public static void ttDescargar(String rel) throws Exception {
        Path destino = TT_DIR.resolve(rel);
        Files.createDirectories(destino.getParent());
        Files.write(destino, httpBytesTT(TT_RAW + rel));
    }

    public static String ttLang() { return "es".equals(IDIOMA) ? "es" : "en"; }

    /** Carga (o descarga si falta) el catálogo y las cadenas. null si todo va bien; si no, el motivo. */
    public static String ttAsegurarDatos() {
        try {
            Path dj = TT_DIR.resolve("data/data.json");
            Path st = TT_DIR.resolve("data/locales/" + ttLang() + "/strings.json");
            if (!Files.exists(dj)) ttDescargar("data/data.json");
            if (!Files.exists(st)) ttDescargar("data/locales/" + ttLang() + "/strings.json");
            if (ttData == null) ttData = obj(Json.parse(Files.readString(dj)));
            if (ttStrings == null) ttStrings = obj(Json.parse(Files.readString(st)));
            return null;
        } catch (Exception ex) {
            log("techtree: " + causa(ex));
            return causa(ex);
        }
    }

    /** Una vez al día: pide data.json con el ETag guardado; 304 = sin cambios; si llega contenido distinto
     *  al local, se sustituye y se renuevan árboles y cadenas (los iconos se conservan y se completan solos). */
    public static void ttComprobarActualizacion() {
        try {
            long ultima = Long.parseLong(leerConfig("techtree_check", "0"));
            Path dj = TT_DIR.resolve("data/data.json");
            if (Files.exists(dj) && CacheService.SISTEMA.fresco(ultima, Caducidad.TECHTREE)) return;
            String etag = leerConfig("techtree_etag", "");
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(TT_RAW + "data/data.json")).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", UA).GET();
            if (!etag.isEmpty() && Files.exists(dj)) rb.header("If-None-Match", etag);
            HttpResponse<byte[]> r = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
            guardarConfig("techtree_check", String.valueOf(System.currentTimeMillis()));
            if (r.statusCode() == 304) return;
            if (r.statusCode() / 100 != 2) { log("techtree: comprobación HTTP " + r.statusCode()); return; }
            r.headers().firstValue("etag").ifPresent(e -> guardarConfig("techtree_etag", e));
            if (Files.exists(dj) && java.util.Arrays.equals(Files.readAllBytes(dj), r.body())) return;   // el snapshot ya es el actual
            boolean habia = Files.exists(dj);
            Path data = TT_DIR.resolve("data");
            if (habia) try (var s = Files.walk(data)) { s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } }); }
            Files.createDirectories(dj.getParent());
            Files.write(dj, r.body());
            ttTrees.clear(); ttData = null; ttStrings = null;
            if (habia) log("techtree: datos renovados desde aoe2techtree");
        } catch (Exception ex) {
            log("techtree: comprobación de actualización: " + causa(ex));
        }
    }

    public static Map<String, Object> ttArbol(String civ) throws Exception {
        Map<String, Object> t = ttTrees.get(civ);
        if (t != null) return t;
        String rel = "data/trees/" + civ.toUpperCase(Locale.ROOT) + ".json";
        Path p = TT_DIR.resolve(rel);
        if (!Files.exists(p)) ttDescargar(rel);
        t = obj(Json.parse(Files.readString(p)));
        ttTrees.put(civ, t);
        return t;
    }

    public static String ttStr(Object id) {
        if (ttStrings == null || id == null) return null;
        String clave = id instanceof Number n ? String.valueOf(n.longValue()) : String.valueOf(id);
        Object s = ttStrings.get(clave);
        return s == null ? null : String.valueOf(s);
    }

    public static String ttNombre(Object id) {
        String s = ttStr(id);
        return s == null ? "?" : s.replace("<br>", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
    }

    public static Path ttRutaIcono(String tipo, long id) { return TT_DIR.resolve("img/" + tipo + "/" + id + ".png"); }
}
