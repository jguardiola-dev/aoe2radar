package dev.tirador.aoe2radar.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Formato.dec1: la coma o el punto según el idioma (v13_textos 18: Cara a cara forzaba la coma también en inglés). */
class FormatoTest {

    @Test void dec1UsaElSeparadorDelIdioma() {
        String antes = I18n.IDIOMA;
        try {
            I18n.IDIOMA = "es";
            assertEquals("3,5", Formato.dec1(3.5));
            assertEquals("0,3", Formato.dec1(1.0 / 3));
            I18n.IDIOMA = "en";
            assertEquals("3.5", Formato.dec1(3.5));
        } finally { I18n.IDIOMA = antes; }
    }
}
