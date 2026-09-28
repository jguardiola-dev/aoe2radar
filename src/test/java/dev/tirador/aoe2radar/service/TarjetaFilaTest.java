package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.util.I18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** La línea de alias y nota que la tarjeta añade al pintarse (antes lo decía el tooltip de la fila). Sin pantalla. */
class TarjetaFilaTest {

    final String idiomaPrevio = I18n.IDIOMA;
    @AfterEach void restaurar() { I18n.IDIOMA = idiomaPrevio; }

    static final String BASE = "<html><b>12Tirador</b><br>ELO 1v1: <b>1905</b><br></html>";

    @Test void aliasYNotaEnUnaLineaMasAlFinal() {
        I18n.IDIOMA = "es";
        assertEquals("<html><b>12Tirador</b><br>ELO 1v1: <b>1905</b><br><br><font size='2' color='gray'>Alias: Jorge  · Nota: rushea &lt;siempre&gt;</font></html>",
                TarjetaPerfil.conFila(BASE, "Jorge", "rushea <siempre>"));
    }

    @Test void soloUnoDeLosDosYEnIngles() {
        I18n.IDIOMA = "en";
        assertEquals("<html><b>12Tirador</b><br>ELO 1v1: <b>1905</b><br><br><font size='2' color='gray'>Note: main</font></html>",
                TarjetaPerfil.conFila(BASE, null, "main"));
        assertEquals("<html><b>12Tirador</b><br>ELO 1v1: <b>1905</b><br><br><font size='2' color='gray'>Alias: Jorge</font></html>",
                TarjetaPerfil.conFila(BASE, "Jorge", "  "));
    }

    @Test void sinAliasNiNotaElHtmlTalCual() {
        assertSame(BASE, TarjetaPerfil.conFila(BASE, null, null));
        assertSame(BASE, TarjetaPerfil.conFila(BASE, "", " "));
    }
}
