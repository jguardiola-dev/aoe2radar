package dev.tirador.aoe2radar.cache;

import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** La regla de caducidad y sus piezas, con un reloj falso: nada espera de verdad. */
class CacheServiceTest {

    final RelojFalso reloj = new RelojFalso();
    final CacheService cache = new CacheService(reloj);
    static final Duration TREINTA_MIN = Duration.ofMinutes(30);

    @Test void laFronteraEsEstricta() {
        long sello = reloj.ahoraMs();
        reloj.avanzar(TREINTA_MIN.toMillis() - 1);
        assertTrue(cache.fresco(sello, TREINTA_MIN), "un milisegundo antes, vale");
        reloj.avanzar(1);
        assertFalse(cache.fresco(sello, TREINTA_MIN), "a la edad justa ya no vale");
    }

    @Test void vigenteSoloMientrasNoCaduca_ultimoSiempre() {
        CacheMemoria<Long, String> m = cache.memoria(TREINTA_MIN);
        assertNull(m.vigente(1L));
        assertNull(m.ultimo(1L));
        m.poner(1L, "ELO 1500");
        assertEquals("ELO 1500", m.vigente(1L));
        reloj.avanzar(TREINTA_MIN.toMillis());
        assertNull(m.vigente(1L), "caducado: hay que volver a pedirlo");
        assertEquals("ELO 1500", m.ultimo(1L), "pero lo último que se supo sigue ahí para pintarlo");
        m.poner(1L, "ELO 1510");
        assertEquals("ELO 1510", m.vigente(1L), "poner renueva el sello");
    }

    @Test void noAdmiteNull() {
        CacheMemoria<Long, String> m = cache.memoria(TREINTA_MIN);
        assertThrows(NullPointerException.class, () -> m.poner(1L, null));
    }

    @Test void unSelloSinMarcarNoEsFresco() {
        Sello s = cache.sello(TREINTA_MIN);
        assertFalse(s.fresco(), "nunca cargado: hay que cargar (como el 0 de la 1.1)");
        s.marcar();
        assertTrue(s.fresco());
        reloj.avanzar(TREINTA_MIN.toMillis());
        assertFalse(s.fresco());
    }

    @Test void unSelloSinMarcarNoEsFrescoNiConElRelojEnCero() {
        reloj.ahora = 0;   // la guarda protege de volver al centinela 0 (ahora - 0 < ttl) y del desbordamiento de ahora - Long.MIN_VALUE
        assertFalse(cache.sello(TREINTA_MIN).fresco());
    }

    @Test void archivoFrescoPorSuFechaDeModificacion(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("ladder.json");
        assertFalse(cache.archivoFresco(f, TREINTA_MIN), "si no existe, no vale");
        Files.writeString(f, "{}");
        Files.setLastModifiedTime(f, FileTime.fromMillis(reloj.ahoraMs() - TREINTA_MIN.toMillis() + 1));
        assertTrue(cache.archivoFresco(f, TREINTA_MIN));
        reloj.avanzar(1);
        assertFalse(cache.archivoFresco(f, TREINTA_MIN), "la misma regla que en memoria");
    }

    @Test void lasCaducidadesDeLaUnoUno() {
        // Caracterización: las cifras que la 1.1 tenía repartidas por el código.
        assertEquals(Duration.ofMinutes(30), Caducidad.PERFIL);
        assertEquals(Duration.ofMinutes(10), Caducidad.TARJETA);
        assertEquals(Duration.ofHours(6), Caducidad.NOCTURNO);
        assertEquals(Duration.ofHours(12), Caducidad.DESCARGA_DIARIA);
        assertEquals(Duration.ofHours(24), Caducidad.TECHTREE);
    }
}
