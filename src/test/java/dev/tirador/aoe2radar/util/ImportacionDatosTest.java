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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        Files.writeString(z.resolve("config.properties"), "idioma=en\ntema=oscuro\n");
        Files.writeString(z.resolve("players.txt"), "123;Ana\n");
        Files.writeString(z.resolve("top_cache.txt"), "firma|1\n");
        Files.writeString(z.resolve("descargas.log"), "log viejo\n");
        Files.createDirectories(z.resolve("recs"));
        Files.writeString(z.resolve("recs").resolve("a.aoe2record"), "rec a vieja");
        Files.writeString(z.resolve("recs").resolve("b.aoe2record"), "rec b");
        Files.createDirectories(z.resolve("sfrdata").resolve("perfiles"));
        Files.writeString(z.resolve("sfrdata").resolve("perfiles").resolve("123.json"), "{}");
        Files.writeString(z.resolve("aoe2radar.exe"), "exe");
        Files.createDirectories(z.resolve("app"));
        Files.createDirectories(z.resolve("runtime"));
        Files.createDirectories(z.resolve("banderas"));
        Files.createDirectories(z.resolve("techtree").resolve("data"));
        Files.writeString(z.resolve("techtree").resolve("data").resolve("data.json"), "{}");
        return z;
    }

    private Path datos() throws IOException { return Files.createDirectories(tmp.resolve("Roaming").resolve("aoe2radar")); }
    private Path recs() { return tmp.resolve("Documentos").resolve("aoe2radar").resolve("recs"); }

    private static Properties props(Path f) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) { p.load(in); }
        return p;
    }

    @Test void seOfreceSoloConDatosNuevosYUnaVez() {
        assertTrue(ImportacionDatos.debeOfrecer(true, null));
        assertTrue(ImportacionDatos.debeOfrecer(true, "false"));
        assertFalse(ImportacionDatos.debeOfrecer(true, "true"), "ya se ofreció: no se vuelve a preguntar");
        assertFalse(ImportacionDatos.debeOfrecer(false, null), "con datos ya en la carpeta nueva no se ofrece");
    }

    @Test void validaLaCarpetaElegida() throws IOException {
        Path z = zipViejo(), d = datos();
        assertTrue(ImportacionDatos.valida(z, d));
        Path vacia = Files.createDirectories(tmp.resolve("vacia"));
        assertFalse(ImportacionDatos.valida(vacia, d), "sin config ni players");
        assertFalse(ImportacionDatos.valida(tmp.resolve("no_existe"), d));
        assertFalse(ImportacionDatos.valida(null, d));
        Files.writeString(d.resolve("players.txt"), "1;x\n");
        assertFalse(ImportacionDatos.valida(d, d), "la propia carpeta de datos");
        assertFalse(ImportacionDatos.valida(tmp.resolve("Roaming"), d), "una carpeta que contiene la de datos");
        Path soloPlayers = Files.createDirectories(tmp.resolve("solo_players"));
        Files.writeString(soloPlayers.resolve("players.txt"), "1;x\n");
        assertTrue(ImportacionDatos.valida(soloPlayers, d));
    }

    @Test void noGraciasNoTocaElDisco() throws IOException {
        Path d = tmp.resolve("Roaming").resolve("aoe2radar");
        assertNull(ImportacionDatos.responder(null, d, recs()));
        assertFalse(Files.exists(d));
        assertFalse(Files.exists(recs()));
    }

    @Test void importaDatosYRecsSinSobrescribirYDejaLaMarca() throws IOException {
        Path z = zipViejo(), d = datos();
        Files.writeString(d.resolve("config.properties"), "idioma=es\n");            // lo que escribió este arranque
        Files.createDirectories(d.resolve("sfrdata"));
        Files.writeString(d.resolve("sfrdata").resolve("ladder.json"), "nuevo");      // caché ya bajada por la nueva
        Files.createDirectories(recs());
        Files.writeString(recs().resolve("a.aoe2record"), "rec a nueva");             // ya está: no se pisa

        ImportacionDatos.Resultado r = ImportacionDatos.responder(z, d, recs());
        assertTrue(r.ok(), r.error());
        assertEquals(1, r.recsCopiadas());
        assertEquals(1, r.recsYaEstaban());

        Properties cfg = props(d.resolve("config.properties"));
        assertEquals("en", cfg.getProperty("idioma"), "los ajustes vienen del zip");
        assertEquals("oscuro", cfg.getProperty("tema"));
        assertEquals("true", cfg.getProperty(ImportacionDatos.CLAVE_OFRECIDA), "no se vuelve a ofrecer");
        assertEquals("123;Ana\n", Files.readString(d.resolve("players.txt")));
        assertEquals("firma|1\n", Files.readString(d.resolve("top_cache.txt")));
        assertEquals("{}", Files.readString(d.resolve("sfrdata").resolve("perfiles").resolve("123.json")));
        assertEquals("nuevo", Files.readString(d.resolve("sfrdata").resolve("ladder.json")));
        assertEquals("rec a nueva", Files.readString(recs().resolve("a.aoe2record")));
        assertEquals("rec b", Files.readString(recs().resolve("b.aoe2record")));
        assertEquals(z.toAbsolutePath().normalize().toString(), Files.readString(d.resolve("importado_desde.txt")).strip());
        for (String no : new String[] { "descargas.log", "techtree", "banderas", "app", "runtime", "aoe2radar.exe", "recs" })
            assertFalse(Files.exists(d.resolve(no)), "no se importa: " + no);
        assertFalse(Files.exists(d.resolve(".importando")));
        // copia, no mueve
        assertTrue(Files.exists(z.resolve("players.txt")));
        assertTrue(Files.exists(z.resolve("recs").resolve("b.aoe2record")));
    }

    @Test void unaCarpetaDeRecsDentroDelZipSeReescribeALaNueva() throws IOException {
        Path z = zipViejo(), d = datos();
        Path propias = Files.createDirectories(z.resolve("mis_recs"));
        Files.writeString(propias.resolve("c.aoe2record"), "rec c");
        Files.writeString(z.resolve("config.properties"),
                "carpeta_recs=" + propias.toString().replace("\\", "\\\\") + "\n");

        ImportacionDatos.Resultado r = ImportacionDatos.importar(z, d, recs());
        assertTrue(r.ok(), r.error());
        assertEquals(recs().toAbsolutePath().toString(), props(d.resolve("config.properties")).getProperty("carpeta_recs"));
        assertEquals("rec c", Files.readString(recs().resolve("c.aoe2record")));
        assertFalse(Files.exists(recs().resolve("a.aoe2record")), "las de recs/ no eran las que usaba");
    }

    @Test void unaCarpetaDeRecsFueraDelZipSeRespeta() throws IOException {
        Path z = zipViejo(), d = datos();
        Path fuera = Files.createDirectories(tmp.resolve("D_recs"));
        String valor = fuera.toString();
        Files.writeString(z.resolve("config.properties"), "carpeta_recs=" + valor.replace("\\", "\\\\") + "\n");

        assertTrue(ImportacionDatos.importar(z, d, recs()).ok());
        assertEquals(valor, props(d.resolve("config.properties")).getProperty("carpeta_recs"));
        assertFalse(Files.exists(recs()), "no se copia nada: el usuario las tiene donde quiere");
    }

    @Test void siFallaAMediasLaCarpetaDeDatosQuedaComoEstaba() throws IOException {
        Path z = zipViejo(), d = datos();
        Files.writeString(d.resolve("config.properties"), "idioma=es\n");
        ImportacionDatos.Copiador falla = (o, dst) -> {
            if (o.getFileName().toString().equals("players.txt")) throw new IOException("disco lleno");
            ImportacionDatos.COPIA_REAL.copiar(o, dst);
        };
        ImportacionDatos.Resultado r = ImportacionDatos.importar(z, d, recs(), falla);
        assertFalse(r.ok());
        assertTrue(r.error().contains("disco lleno"), r.error());
        assertEquals("idioma=es\n", Files.readString(d.resolve("config.properties")), "config intacta");
        assertFalse(Files.exists(d.resolve("players.txt")));
        assertFalse(Files.exists(d.resolve("sfrdata")), "sfrdata ya copiada a la preparación, pero no colocada");
        assertFalse(Files.exists(d.resolve("importado_desde.txt")));
        assertFalse(Files.exists(d.resolve(".importando")), "la preparación se borra");

        assertTrue(ImportacionDatos.importar(z, d, recs()).ok(), "reintentar funciona");
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
        assertFalse(ImportacionDatos.importar(z, d, recs(), falla).ok());
        assertFalse(Files.exists(recs().resolve("b.aoe2record")));
        assertFalse(Files.exists(recs().resolve("b.aoe2record.importando")));
        assertFalse(Files.exists(d.resolve("players.txt")), "con las recs a medias no se colocan los datos");

        ImportacionDatos.Resultado r = ImportacionDatos.importar(z, d, recs());
        assertTrue(r.ok(), r.error());
        assertEquals("rec b", Files.readString(recs().resolve("b.aoe2record")));
    }
}
