package dev.tirador.aoe2radar.cache;

import dev.tirador.aoe2radar.util.Reloj;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Las cachés de la app y su única regla: un dato guardado vale mientras su edad sea MENOR que su caducidad (las
 * cifras, en Caducidad). El reloj entra por el constructor: en los tests, uno falso. Fabrica las dos piezas
 * (CacheMemoria por clave, Sello para un dato suelto) y responde por los archivos en disco.
 * <p>Qué NO es caché: la actividad del perfil abierto, las vinculadas y las familias (estado de la sesión, van con
 * ProfileService/AppState), y los «no más de una vez cada X» de Live o Twitch (frenos locales).
 */
public final class CacheService {
    /** El de la app, con el reloj del sistema. */
    public static final CacheService SISTEMA = new CacheService(Reloj.SISTEMA);

    private final Reloj reloj;

    public CacheService(Reloj reloj) { this.reloj = reloj; }

    public Reloj reloj() { return reloj; }

    /** La regla: ¿un dato sellado a selloMs sigue valiendo con esta caducidad? Frontera estricta: a la edad justa, no. */
    public boolean fresco(long selloMs, Duration caducidad) { return reloj.ahoraMs() - selloMs < caducidad.toMillis(); }

    /** ¿Existe f y su fecha de modificación es fresca? Si no se puede leer la fecha, IOException (decide el llamador). */
    public boolean archivoFresco(Path f, Duration caducidad) throws IOException {
        return Files.exists(f) && fresco(Files.getLastModifiedTime(f).toMillis(), caducidad);
    }

    public <K, V> CacheMemoria<K, V> memoria(Duration caducidad) { return new CacheMemoria<>(this, caducidad); }

    public Sello sello(Duration caducidad) { return new Sello(this, caducidad); }
}
