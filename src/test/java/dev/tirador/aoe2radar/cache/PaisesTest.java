package dev.tirador.aoe2radar.cache;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paises.PAISES_FILE es una ruta fija en disco (no inyectable): el test respalda y restaura el archivo y el mapa
 * en memoria para no interferir con la caché real del proyecto ni con otros tests.
 */
class PaisesTest {

    @Test void siFallaElGuardadoVuelveAQuedarSucioParaReintentarlo() throws Exception {
        Map<Long, String> paisesPrevios = new HashMap<>(Paises.PAIS_DE);
        boolean suciosPrevio = Paises.paisesSucios;
        byte[] contenidoPrevio = Files.exists(Paises.PAISES_FILE) ? Files.readAllBytes(Paises.PAISES_FILE) : null;
        try {
            Files.deleteIfExists(Paises.PAISES_FILE);
            Files.createDirectories(Paises.PAISES_FILE);   // paises.txt como carpeta: escribirlo como archivo falla
            Paises.PAIS_DE.clear();
            Paises.PAIS_DE.put(999L, "es");
            Paises.paisesSucios = true;

            Paises.guardarPaises();

            assertTrue(Paises.paisesSucios, "el guardado falló: el lote se queda sucio para que el siguiente barrido lo reintente");
        } finally {
            if (Files.isDirectory(Paises.PAISES_FILE))
                Files.walk(Paises.PAISES_FILE).sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) { } });
            else Files.deleteIfExists(Paises.PAISES_FILE);
            if (contenidoPrevio != null) Files.write(Paises.PAISES_FILE, contenidoPrevio);
            Paises.PAIS_DE.clear();
            Paises.PAIS_DE.putAll(paisesPrevios);
            Paises.paisesSucios = suciosPrevio;
        }
    }

    /** Respalda archivo, mapa y banderas, corre el caso y lo deja todo como estaba (F13, revisión 1.3). */
    private interface Caso { void correr() throws Exception; }
    private static void conPaisesAislados(Caso caso) throws Exception {
        Map<Long, String> paisesPrevios = new HashMap<>(Paises.PAIS_DE);
        boolean suciosPrevio = Paises.paisesSucios, cargadosPrevio = Paises.paisesCargados;
        byte[] contenidoPrevio = Files.exists(Paises.PAISES_FILE) && !Files.isDirectory(Paises.PAISES_FILE)
                ? Files.readAllBytes(Paises.PAISES_FILE) : null;
        try {
            Files.deleteIfExists(Paises.PAISES_FILE);
            Paises.PAIS_DE.clear();
            Paises.PAIS_DE.put(999L, "es");
            Paises.paisesSucios = true;
            caso.correr();
        } finally {
            Paises.GUARDANDO.set(false);
            if (contenidoPrevio != null) Files.write(Paises.PAISES_FILE, contenidoPrevio);
            else Files.deleteIfExists(Paises.PAISES_FILE);
            Paises.PAIS_DE.clear();
            Paises.PAIS_DE.putAll(paisesPrevios);
            Paises.paisesSucios = suciosPrevio;
            Paises.paisesCargados = cargadosPrevio;
        }
    }

    @Test void alCerrarSeGuardanLosPaisesPendientes() throws Exception {
        conPaisesAislados(() -> {
            Paises.paisesCargados = true;
            Paises.guardarAlCerrar(2000);
            assertTrue(Files.exists(Paises.PAISES_FILE), "los países del último minuto se guardan al cerrar");
            assertEquals("999=es", Files.readString(Paises.PAISES_FILE).trim());
            assertFalse(Paises.paisesSucios);
        });
    }

    @Test void alCerrarSinLaCargaInicialTerminadaNoSeGuardaNada() throws Exception {
        conPaisesAislados(() -> {
            Paises.paisesCargados = false;   // cerrar en los primeros segundos: el mapa aún está a medio leer
            Paises.guardarAlCerrar(2000);
            assertFalse(Files.exists(Paises.PAISES_FILE), "un mapa a medias no debe pisar el archivo completo");
            assertTrue(Paises.paisesSucios);
        });
    }

    @Test void alCerrarConElGuardadoDelTimerEnMarchaEsperaYGuarda() throws Exception {
        conPaisesAislados(() -> {
            Paises.paisesCargados = true;
            Paises.GUARDANDO.set(true);   // el Timer de 60 s está escribiendo en otro hilo…
            Thread libera = new Thread(() -> {
                try { Thread.sleep(150); } catch (InterruptedException ignored) { }
                Paises.GUARDANDO.set(false);   // …y termina al poco
            });
            libera.start();
            Paises.guardarAlCerrar(2000);
            libera.join();
            assertTrue(Files.exists(Paises.PAISES_FILE), "espera a que acabe el otro guardado y guarda lo pendiente");
            assertFalse(Paises.paisesSucios);
        });
    }

    @Test void siYaHayUnGuardadoEnMarchaElSiguienteDisparoNoHaceNada() throws Exception {
        // Hallazgo del revisor sobre la fila 25: con cargarPaises/guardarPaises corriendo en hilos aparte, dos
        // disparos del Timer no deben escribir el archivo a la vez. GUARDANDO (paquete, no privado) simula "ya
        // hay uno en marcha"; si guardarPaises() no respetara el flag, escribiría de verdad y dejaría
        // paisesSucios en false (falla la aserción de abajo).
        Map<Long, String> paisesPrevios = new HashMap<>(Paises.PAIS_DE);
        boolean suciosPrevio = Paises.paisesSucios;
        byte[] contenidoPrevio = Files.exists(Paises.PAISES_FILE) && !Files.isDirectory(Paises.PAISES_FILE)
                ? Files.readAllBytes(Paises.PAISES_FILE) : null;
        try {
            Paises.PAIS_DE.clear();
            Paises.PAIS_DE.put(999L, "es");
            Paises.paisesSucios = true;
            Paises.GUARDANDO.set(true);   // simula un guardado ya en marcha en otro hilo

            Paises.guardarPaises();

            assertTrue(Paises.paisesSucios, "con un guardado ya en marcha, este disparo debe saltarse sin tocar nada");
        } finally {
            Paises.GUARDANDO.set(false);
            if (contenidoPrevio != null) Files.write(Paises.PAISES_FILE, contenidoPrevio);
            else Files.deleteIfExists(Paises.PAISES_FILE);
            Paises.PAIS_DE.clear();
            Paises.PAIS_DE.putAll(paisesPrevios);
            Paises.paisesSucios = suciosPrevio;
        }
    }
}
