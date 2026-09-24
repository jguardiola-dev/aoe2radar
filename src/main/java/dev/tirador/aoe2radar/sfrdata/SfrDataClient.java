package dev.tirador.aoe2radar.sfrdata;

import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Caducidad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.function.Supplier;

import static dev.tirador.aoe2radar.cache.Directorios.LADDER_DIR;
import static dev.tirador.aoe2radar.util.Config.guardarConfig;
import static dev.tirador.aoe2radar.util.Config.leerConfig;
import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Todo lo que la app baja de sfr-data, con su caché en disco. Tres maneras, según el archivo:
 * <ul>
 * <li>datos(nombre): rama «data» (ladder, dispersión, clanes, mapas, civ stats). Vale 6 h (Caducidad.NOCTURNO); al
 *     caducar se pregunta con ETag (304 = sigue valiendo otras 6 h). Si la red falla y hay copia, se usa la copia.</li>
 * <li>diario(nombre, timeout): release «perfiles» (ELO de ayer, de hace 7 días, muestra). Vale 12 h
 *     (Caducidad.DESCARGA_DIARIA); se baja con reintentos.</li>
 * <li>versionado(base, nombre, versión, timeout): paquetes de perfil y deltas. Vale mientras la versión (la fecha del
 *     índice) sea la misma; se apunta en «nombre.v».</li>
 * </ul>
 * Red, ETags, carpeta y reloj entran por el constructor: los tests no tocan ni la red ni la config del usuario.
 * Paso A de la migración: mismo comportamiento que las funciones estáticas de Ladder y Snapshots, que delegan aquí.
 */
public final class SfrDataClient {
    public static final String SFR_DATA = "https://raw.githubusercontent.com/jguardiola-dev/sfr-data/data/";

    /** Dónde se guardan los ETag de la rama «data» (en la app, en la config: «sfrdata_etag_<nombre>»). */
    public interface Etags {
        String leer(String nombre);   // "" si no hay
        void guardar(String nombre, String etag);

        static Etags config() {
            return new Etags() {
                @Override public String leer(String nombre) { return leerConfig("sfrdata_etag_" + nombre, ""); }
                @Override public void guardar(String nombre, String etag) { guardarConfig("sfrdata_etag_" + nombre, etag); }
            };
        }
    }

    /** El de la app. La base de la release se lee en cada llamada (el mando a distancia puede cambiarla). */
    public static final SfrDataClient SISTEMA = new SfrDataClient(LADDER_DIR, DescargaSfr.http(), Etags.config(),
            CacheService.SISTEMA, Snapshots::perfilesBase);

    private final Path dir, dirRelease;
    private final DescargaSfr red;
    private final Etags etags;
    private final CacheService cache;
    private final Supplier<String> baseRelease;

    public SfrDataClient(Path dir, DescargaSfr red, Etags etags, CacheService cache, Supplier<String> baseRelease) {
        this.dir = dir; this.dirRelease = dir.resolve("perfiles_shards");
        this.red = red; this.etags = etags; this.cache = cache; this.baseRelease = baseRelease;
    }

    /** Un archivo de la rama «data», con caché de 6 h y ETag; .gz se descomprime al leer. */
    public byte[] datos(String nombre) throws Exception {
        Path local = dir.resolve(nombre);
        Files.createDirectories(local.getParent());
        if (!cache.archivoFresco(local, Caducidad.NOCTURNO)) {
            String etag = etags.leer(nombre);
            DescargaSfr.Respuesta r = red.condicional(SFR_DATA + nombre, !etag.isEmpty() && Files.exists(local) ? etag : null, 60);
            if (r.estado() == 304) Files.setLastModifiedTime(local, FileTime.fromMillis(cache.reloj().ahoraMs()));
            else if (r.estado() / 100 == 2) { Files.write(local, r.cuerpo()); if (r.etag() != null) etags.guardar(nombre, r.etag()); }
            else if (!Files.exists(local)) throw new IOException("HTTP " + r.estado() + " (" + nombre + ")");
        }
        byte[] b = Files.readAllBytes(local);
        if (nombre.endsWith(".gz")) try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(b))) { b = in.readAllBytes(); }
        return b;
    }

    /** Un archivo de la release «perfiles», guardado en disco y reutilizado 12 h (sin descomprimir). */
    public byte[] diario(String nombre, int timeoutS) throws Exception {
        Path f = dirRelease.resolve(nombre);
        try { if (cache.archivoFresco(f, Caducidad.DESCARGA_DIARIA)) return Files.readAllBytes(f); } catch (IOException ignored) { }
        byte[] raw = red.bytes(baseRelease.get() + nombre, timeoutS);
        try { Files.createDirectories(dirRelease); Files.write(f, raw); } catch (IOException ignored) { }
        return raw;
    }

    /**
     * Borra la copia en disco de un archivo de diario(): quien lo leyó no pudo entenderlo (roto, truncado). Sin esto,
     * cada reintento releería la misma basura durante 12 h; así el siguiente vuelve a la red.
     */
    public void olvidarDiario(String nombre) {
        try { Files.deleteIfExists(dirRelease.resolve(nombre)); } catch (IOException ex) { log("sfr-data: no se pudo borrar " + nombre + ": " + causa(ex)); }
    }

    /** Como versionado(base, …) con la base de la release «perfiles». */
    public byte[] versionado(String nombre, String version, int timeoutS) throws Exception {
        return versionado(baseRelease.get(), nombre, version, timeoutS);
    }

    /** Un archivo con caché en disco por «versión» (la fecha que lo hace válido): si la marca coincide, no se baja. */
    public byte[] versionado(String base, String nombre, String version, int timeoutS) throws Exception {
        Path f = dirRelease.resolve(nombre), marca = dirRelease.resolve(nombre + ".v");
        try { if (Files.exists(f) && Files.exists(marca) && version.equals(Files.readString(marca).trim())) return Files.readAllBytes(f); } catch (IOException ignored) { }
        byte[] raw = red.bytes(base + nombre, timeoutS);
        try { Files.createDirectories(dirRelease); Files.write(f, raw); Files.writeString(marca, version); } catch (IOException ex) { log("perfiles: caché: " + causa(ex)); }
        return raw;
    }
}
