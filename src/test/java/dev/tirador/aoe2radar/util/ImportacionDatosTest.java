package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Importación de los datos de una 1.x en zip a la app instalada. Todo en carpetas temporales: ni el %APPDATA% ni
 * los Documentos reales. La elección del usuario se inyecta (una ruta o null): nunca se abre un diálogo.
 */
class ImportacionDatosTest {

    @TempDir Path tmp;

    /** Una carpeta de la 1.3 en zip: datos del usuario y recursos de la app mezclados. */
    private Path zipViejo() throws IOException {
        Path z = Files.createDirectories(tmp.resolve("aoe2radar-1.3"));
        Files.writeString(z.resolve("config.properties"), "idioma=en\ntema=oscuro\nautoarranque=true\n");
        Files.writeString(z.resolve("players.txt"), "123;Ana\n");
        Files.writeString(z.resolve("top_cache.txt"), "firma|1\n");
        Files.writeString(z.resolve("descargas.log"), "log viejo\n");
        Files.createDirectories(z.resolve("recs"));
        Files.writeString(z.resolve("recs").resolve("a.aoe2record"), "rec a vieja");
        Files.writeString(z.resolve("recs").resolve("b.aoe2record"), "rec b");
        Files.createDirectories(z.resolve("sfrdata").resolve("perfiles"));
        Files.writeString(z.resolve("sfrdata").resolve("perfiles").resolve("123.json"), "{}");
        Files.writeString(z.resolve("sfrdata").resolve("ladder.json"), "ladder viejo");
        Files.writeString(z.resolve("aoe2radar.exe"), "exe");
        Files.createDirectories(z.resolve("app"));
        Files.createDirectories(z.resolve("runtime"));
        Files.createDirectories(z.resolve("banderas"));
        Files.createDirectories(z.resolve("techtree").resolve("data"));
        Files.writeString(z.resolve("techtree").resolve("data").resolve("data.json"), "{}");
        return z;
    }

    /** La carpeta de datos de la app instalada, con lo que ya había escrito la versión nueva. */
    private Path datos() throws IOException {
        Path d = Files.createDirectories(tmp.resolve("Roaming").resolve("aoe2radar"));
        Files.writeString(d.resolve("config.properties"), "idioma=es\n");
        Files.writeString(d.resolve("players.txt"), "9;Nuevo\n");
        Files.createDirectories(d.resolve("sfrdata"));
        Files.writeString(d.resolve("sfrdata").resolve("ladder.json"), "ladder nuevo");
        Files.writeString(d.resolve("sfrdata").resolve("mapas.json"), "solo en la nueva");
        return d;
    }

    private Path recs() { return tmp.resolve("Documentos").resolve("aoe2radar").resolve("recs"); }

    private static Properties props(Path f) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) { p.load(in); }
        return p;
    }

    private ImportacionDatos.Resultado importar(Path z, Path d) {
        return ImportacionDatos.importar(z, d, recs(), ImportacionDatos.COPIA_REAL, ImportacionDatos.MOVER_REAL);
    }

    @Test void seOfreceSoloConDatosNuevosYUnaVez() {
        assertTrue(ImportacionDatos.debeOfrecer(true, null));
        assertTrue(ImportacionDatos.debeOfrecer(true, "false"));
        assertFalse(ImportacionDatos.debeOfrecer(true, "true"), "ya se ofreció: no se vuelve a preguntar");
        assertFalse(ImportacionDatos.debeOfrecer(false, null), "con datos ya en la carpeta nueva no se ofrece");
    }

    @Test void conDatosActualesSePideConfirmacion() throws IOException {
        Path vacia = Files.createDirectories(tmp.resolve("vacia"));
        assertFalse(ImportacionDatos.pideConfirmacion(true, vacia), "primer arranque, nada que perder");
        assertTrue(ImportacionDatos.pideConfirmacion(false, vacia), "ya tenía datos al arrancar");
        Files.writeString(vacia.resolve("config.properties"), "idioma=es\n");
        assertTrue(ImportacionDatos.pideConfirmacion(true, vacia), "hay ajustes que se sustituirían");
        Files.delete(vacia.resolve("config.properties"));
        Files.writeString(vacia.resolve("players.txt"), "1;x\n");
        assertTrue(ImportacionDatos.pideConfirmacion(true, vacia), "añadió jugadores en esta sesión");
    }

    @Test void validaLaCarpetaElegida() throws IOException {
        Path z = zipViejo(), d = datos();
        assertTrue(ImportacionDatos.valida(z, d));
        Path vacia = Files.createDirectories(tmp.resolve("vacia"));
        assertFalse(ImportacionDatos.valida(vacia, d), "sin config ni players");
        assertFalse(ImportacionDatos.valida(tmp.resolve("no_existe"), d));
        assertFalse(ImportacionDatos.valida(null, d));
        assertFalse(ImportacionDatos.valida(d, d), "la propia carpeta de datos");
        assertFalse(ImportacionDatos.valida(tmp.resolve("Roaming"), d), "una carpeta que contiene la de datos");
    }

    @Test void noGraciasNoTocaElDisco() {
        Path d = tmp.resolve("Roaming").resolve("aoe2radar");
        assertNull(ImportacionDatos.responder(null, d, recs()));
        assertFalse(Files.exists(d));
        assertFalse(Files.exists(recs()));
    }

    @Test void importaDatosYRecsSinSobrescribirYGuardaCopiaDeLoAnterior() throws IOException {
        Path z = zipViejo(), d = datos();
        Files.createDirectories(recs());
        Files.writeString(recs().resolve("a.aoe2record"), "rec a nueva");   // ya está: no se pisa

        ImportacionDatos.Preparado p = ImportacionDatos.preparar(z, d, recs());
        assertTrue(p.ok(), p.error());
        assertEquals(1, p.recsCopiadas());
        assertEquals(1, p.recsYaEstaban());
        assertTrue(p.autoarranque(), "la versión zip arrancaba con Windows: hay que avisar");
        assertEquals("9;Nuevo\n", Files.readString(d.resolve("players.txt")), "preparar no toca la carpeta de datos");

        ImportacionDatos.Resultado r = ImportacionDatos.colocar(p, d, false);
        assertTrue(r.ok(), r.error());
        Properties cfg = props(d.resolve("config.properties"));
        assertEquals("en", cfg.getProperty("idioma"), "los ajustes vienen del zip");
        assertEquals("true", cfg.getProperty(ImportacionDatos.CLAVE_OFRECIDA), "no se vuelve a ofrecer");
        assertEquals("123;Ana\n", Files.readString(d.resolve("players.txt")));
        assertEquals("firma|1\n", Files.readString(d.resolve("top_cache.txt")));
        assertEquals("{}", Files.readString(d.resolve("sfrdata").resolve("perfiles").resolve("123.json")));
        assertEquals("ladder viejo", Files.readString(d.resolve("sfrdata").resolve("ladder.json")));
        assertEquals("solo en la nueva", Files.readString(d.resolve("sfrdata").resolve("mapas.json")));
        assertEquals("rec a nueva", Files.readString(recs().resolve("a.aoe2record")));
        assertEquals("rec b", Files.readString(recs().resolve("b.aoe2record")));
        assertTrue(Files.readString(d.resolve("importado_desde.txt")).startsWith(z.toAbsolutePath().normalize().toString()));
        for (String no : new String[] { "descargas.log", "techtree", "banderas", "app", "runtime", "aoe2radar.exe", "recs", ".importando" })
            assertFalse(Files.exists(d.resolve(no)), "no se importa: " + no);

        // la copia de lo anterior se queda siempre
        Path copia = r.copiaAnterior();
        assertNotNull(copia);
        assertTrue(copia.startsWith(d.resolve(".antes_de_importar")));
        assertEquals("9;Nuevo\n", Files.readString(copia.resolve("players.txt")));
        assertEquals("idioma=es\n", Files.readString(copia.resolve("config.properties")));
        assertEquals("ladder nuevo", Files.readString(copia.resolve("sfrdata").resolve("ladder.json")));
        assertFalse(Files.exists(copia.resolve("sfrdata").resolve("mapas.json")), "solo lo que se reemplaza");
        // copia, no mueve
        assertTrue(Files.exists(z.resolve("players.txt")));
        assertTrue(Files.exists(z.resolve("recs").resolve("b.aoe2record")));
    }

    @Test void siFallaAlColocarSeRestauraLoAnterior() throws IOException {
        Path z = zipViejo(), d = datos();
        ImportacionDatos.Movedor fallaEnConfig = (o, dst) -> {
            if (dst.getFileName().toString().equals("config.properties")) throw new IOException("bloqueado");
            ImportacionDatos.MOVER_REAL.mover(o, dst);
        };
        ImportacionDatos.Resultado r = ImportacionDatos.importar(z, d, recs(), ImportacionDatos.COPIA_REAL, fallaEnConfig);
        assertEquals(ImportacionDatos.Estado.RESTAURADO, r.estado());
        assertTrue(r.error().contains("bloqueado"), r.error());
        assertEquals("9;Nuevo\n", Files.readString(d.resolve("players.txt")), "players vuelve a ser el de antes");
        assertEquals("idioma=es\n", Files.readString(d.resolve("config.properties")));
        assertEquals("ladder nuevo", Files.readString(d.resolve("sfrdata").resolve("ladder.json")));
        assertFalse(Files.exists(d.resolve("sfrdata").resolve("perfiles").resolve("123.json")), "lo que no existía se quita");
        assertFalse(Files.exists(d.resolve("top_cache.txt")));
        assertFalse(Files.exists(d.resolve("importado_desde.txt")));
        assertFalse(Files.exists(d.resolve(".importando")));
        assertTrue(Files.exists(r.copiaAnterior().resolve("players.txt")), "la copia se queda");
        assertEquals("rec b", Files.readString(recs().resolve("b.aoe2record")), "las recs copiadas se quedan (y se dice)");
    }

    @Test void siFallaAlPrepararNoCambiaNada() throws IOException {
        Path z = zipViejo(), d = datos();
        ImportacionDatos.Copiador falla = (o, dst) -> {
            if (o.getFileName().toString().equals("players.txt")) throw new IOException("disco lleno");
            ImportacionDatos.COPIA_REAL.copiar(o, dst);
        };
        ImportacionDatos.Resultado r = ImportacionDatos.importar(z, d, recs(), falla, ImportacionDatos.MOVER_REAL);
        assertEquals(ImportacionDatos.Estado.SIN_CAMBIOS, r.estado());
        assertTrue(r.error().contains("disco lleno"), r.error());
        assertEquals("9;Nuevo\n", Files.readString(d.resolve("players.txt")));
        assertEquals("idioma=es\n", Files.readString(d.resolve("config.properties")));
        assertFalse(Files.exists(d.resolve(".importando")));
        assertTrue(importar(z, d).ok(), "reintentar funciona");
        assertEquals("123;Ana\n", Files.readString(d.resolve("players.txt")));
    }

    @Test void siFallaUnaRecNoQuedaUnaRecAMediasConElNombreBueno() throws IOException {
        Path z = zipViejo(), d = datos();
        ImportacionDatos.Copiador falla = (o, dst) -> {
            if (o.getFileName().toString().equals("b.aoe2record")) {
                Files.writeString(dst, "a medi");   // copia cortada
                throw new IOException("cortado");
            }
            ImportacionDatos.COPIA_REAL.copiar(o, dst);
        };
        assertFalse(ImportacionDatos.importar(z, d, recs(), falla, ImportacionDatos.MOVER_REAL).ok());
        assertFalse(Files.exists(recs().resolve("b.aoe2record")));
        assertFalse(Files.exists(recs().resolve("b.aoe2record.importando")));
        assertEquals("9;Nuevo\n", Files.readString(d.resolve("players.txt")));
        assertTrue(importar(z, d).ok());
        assertEquals("rec b", Files.readString(recs().resolve("b.aoe2record")));
    }

    @Test void colocarPausaLasEscriturasYLasReanuda() throws IOException {
        Path z = zipViejo(), d = datos();
        Path otro = tmp.resolve("otro.txt");
        ImportacionDatos.Movedor intentaEscribir = (o, dst) -> {
            // en mitad de colocar, otro escritor de la app (Timer de países, caché del top…) no puede escribir
            Throwable t = assertThrows(IOException.class, () -> Archivos.escribirAtomico(otro, new byte[] { 1 }));
            assertTrue(t.getMessage().contains("pausa"));
            ImportacionDatos.MOVER_REAL.mover(o, dst);
        };
        assertTrue(ImportacionDatos.importar(z, d, recs(), ImportacionDatos.COPIA_REAL, intentaEscribir).ok());
        Archivos.escribirAtomico(otro, new byte[] { 2 });   // reanudadas al acabar
        assertEquals(1, Files.size(otro));
    }

    @Test void unaCarpetaDeRecsDentroDelZipSeReescribeALaNueva() throws IOException {
        Path z = zipViejo(), d = datos();
        Path propias = Files.createDirectories(z.resolve("mis_recs"));
        Files.writeString(propias.resolve("c.aoe2record"), "rec c");
        Files.writeString(z.resolve("config.properties"),
                "carpeta_recs=" + propias.toString().replace("\\", "\\\\") + "\n");

        assertTrue(importar(z, d).ok());
        assertEquals(recs().toAbsolutePath().toString(), props(d.resolve("config.properties")).getProperty("carpeta_recs"));
        assertEquals("rec c", Files.readString(recs().resolve("c.aoe2record")));
        assertFalse(Files.exists(recs().resolve("a.aoe2record")), "las de recs/ no eran las que usaba");
    }

    @Test void unaCarpetaDeRecsFueraDelZipSeRespetaYUnaRelativaSeQuita() throws IOException {
        Path z = zipViejo(), d = datos();
        Path fuera = Files.createDirectories(tmp.resolve("D_recs"));
        Files.writeString(z.resolve("config.properties"), "carpeta_recs=" + fuera.toString().replace("\\", "\\\\") + "\n");
        assertTrue(importar(z, d).ok());
        assertEquals(fuera.toString(), props(d.resolve("config.properties")).getProperty("carpeta_recs"));
        assertFalse(Files.exists(recs()), "no se copia nada: el usuario las tiene donde quiere");

        Files.writeString(z.resolve("config.properties"), "carpeta_recs=recs\n");
        assertTrue(importar(z, d).ok());
        assertNull(props(d.resolve("config.properties")).getProperty("carpeta_recs"), "relativa: no significa nada aquí");
        assertEquals("rec b", Files.readString(recs().resolve("b.aoe2record")), "y se copian las de recs/");
    }

    @Test void unArchivoDeSoloLecturaDelZipQuedaEscribible() throws IOException {
        Path z = zipViejo(), d = datos();
        Path players = z.resolve("players.txt");
        Files.setAttribute(players, "dos:readonly", true);
        try {
            assertTrue(importar(z, d).ok());
            assertTrue(Files.isWritable(d.resolve("players.txt")));
        } finally {
            Files.setAttribute(players, "dos:readonly", false);
        }
    }
}
