package dev.tirador.aoe2radar.ui;

import dev.tirador.aoe2radar.util.I18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 1.4: si «Al azar» completó la muestra de ayer con la de anteayer, el aviso lo dice; si no, el de siempre. */
class AvisoAnteayerTest {

    final String idiomaPrevio = I18n.IDIOMA;
    @AfterEach void restaurar() { I18n.IDIOMA = idiomaPrevio; }

    @Test void conAnteayerLoDiceEnLosDosIdiomas() {
        I18n.IDIOMA = "es";
        assertEquals("10 partidas 1v1 al azar, ELO 1800–1900, partidas de ayer y anteayer (muestra nocturna).",
                PartidasPresenter.mensajeAzar(10, 1800, 1900, 48, true, true, () -> true));
        I18n.IDIOMA = "en";
        assertEquals("10 random 1v1s, ELO 1800–1900, yesterday's and the day before's games (nightly sample).",
                PartidasPresenter.mensajeAzar(10, 1800, 1900, 48, true, true, () -> true));
    }

    @Test void todasDeAyerSigueComoHoy() {
        I18n.IDIOMA = "es";
        assertEquals("6 partidas 1v1 al azar, ELO 1800–1900, partidas de ayer (muestra nocturna).",
                PartidasPresenter.mensajeAzar(6, 1800, 1900, 48, true, false, () -> true));
        assertEquals(PartidasPresenter.mensajeAzar(6, 1800, 1900, 48, true, () -> true),
                PartidasPresenter.mensajeAzar(6, 1800, 1900, 48, true, false, () -> true), "la firma de antes = sin anteayer");
    }

    @Test void sinMuestraAnteayerNoCuenta() {
        I18n.IDIOMA = "es";
        assertEquals(PartidasPresenter.mensajeAzar(3, 1800, 1900, 48, false, () -> false),
                PartidasPresenter.mensajeAzar(3, 1800, 1900, 48, false, true, () -> false));
    }
}
