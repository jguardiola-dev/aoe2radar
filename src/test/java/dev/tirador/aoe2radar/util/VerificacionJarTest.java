package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** La comprobación del jar descargado: tamaño, sha256, zip legible y las clases de arranque. */
class VerificacionJarTest {

    static final String MAIN = "dev.tirador.aoe2radar.SpoilerFreeRecs";

    @TempDir Path tmp;

    /** Un jar de mentira con las entradas dadas (contenido distinto según «semilla», para que cambie el sha). */
    static byte[] jar(String semilla, String... entradas) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            for (String e : entradas) {
                z.putNextEntry(new ZipEntry(e));
                z.write((semilla + e).getBytes());
                z.closeEntry();
            }
        }
        return b.toByteArray();
    }

    /** El jar de una versión buena de la app. */
    static byte[] jarBueno(String semilla) throws IOException {
        return jar(semilla, VerificacionJar.CLASE_ARRANQUE, MAIN.replace('.', '/') + ".class");
    }

    static String sha(byte[] b) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));
    }

    @Test void unJarBuenoPasa() throws Exception {
        byte[] b = jarBueno("1.5");
        Path p = Files.write(tmp.resolve("aoe2radar-1.5.jar"), b);
        assertTrue(VerificacionJar.verificar(p, b.length, sha(b), MAIN).ok());
        assertTrue(VerificacionJar.verificar(p, b.length, sha(b).toUpperCase(Locale.ROOT), MAIN).ok(), "sha en mayúsculas también");
        assertEquals(sha(b), VerificacionJar.sha256(p));
    }

    @Test void tamanoShaYContenidoMalosNoPasan() throws Exception {
        byte[] b = jarBueno("1.5");
        Path p = Files.write(tmp.resolve("a.jar"), b);
        assertFalse(VerificacionJar.verificar(p, b.length + 1, sha(b), MAIN).ok(), "tamaño");
        assertFalse(VerificacionJar.verificar(p, b.length, sha(jarBueno("otro")), MAIN).ok(), "sha de otro");
        assertFalse(VerificacionJar.verificar(p, b.length, "zz", MAIN).ok(), "sha mal formado");
        assertFalse(VerificacionJar.verificar(tmp.resolve("no-existe.jar"), 1, sha(b), MAIN).ok());

        byte[] sinMain = jar("x", MAIN.replace('.', '/') + ".class");
        Path p2 = Files.write(tmp.resolve("b.jar"), sinMain);
        assertFalse(VerificacionJar.verificar(p2, sinMain.length, sha(sinMain), MAIN).ok(), "sin app/Main.class");

        byte[] sinPrincipal = jar("x", VerificacionJar.CLASE_ARRANQUE);
        Path p3 = Files.write(tmp.resolve("c.jar"), sinPrincipal);
        assertFalse(VerificacionJar.verificar(p3, sinPrincipal.length, sha(sinPrincipal), MAIN).ok(), "sin la clase del .cfg");
        assertTrue(VerificacionJar.verificar(p3, sinPrincipal.length, sha(sinPrincipal), null).ok(), "sin mirar la del .cfg");

        byte[] noZip = "esto no es un jar".getBytes();
        Path p4 = Files.write(tmp.resolve("d.jar"), noZip);
        VerificacionJar.Resultado r = VerificacionJar.verificar(p4, noZip.length, sha(noZip), MAIN);
        assertFalse(r.ok());
        assertTrue(r.motivo().contains("jar"), r.motivo());
    }
}
