package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.util.I18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** La tarjeta del hover sin Swing ni red: la regla de la gráfica (movida tal cual), el HTML y la versión nocturna (1.4). */
class TarjetaPerfilTest {

    String idiomaPrevio;
    @BeforeEach void es() { idiomaPrevio = I18n.IDIOMA; I18n.IDIOMA = "es"; }
    @AfterEach void restaurar() { I18n.IDIOMA = idiomaPrevio; }

    static List<Integer> deN(int n, int primero) {   // de la más reciente a la más antigua: primero, primero-1, ...
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(primero - i);
        return l;
    }

    // ----- la regla de la gráfica (caracterización de la de WatchlistHoverCard) -----

    @Test void conMasDeDiezQuitaLasDiezMasRecientesYDaLaCronologica() {
        TarjetaPerfil.Chispa c = TarjetaPerfil.chispa(deN(15, 1500));   // 1500 (la más reciente) … 1486
        assertArrayEquals(new int[]{ 1486, 1487, 1488, 1489, 1490 }, c.spark(), "sin las 10 últimas (1500-1491) y de vieja a nueva");
        assertFalse(c.pocos1v1());
    }

    @Test void conDiezOMenosNoQuitaNada() {
        assertArrayEquals(new int[]{ 1493, 1494, 1495, 1496, 1497, 1498, 1499, 1500 }, TarjetaPerfil.chispa(deN(8, 1500)).spark(),
                "así era en la 1.3: con 10 o menos se pinta todo");
    }

    @Test void conMenosDeCuatroQueQuedenSonPocos() {
        assertTrue(TarjetaPerfil.chispa(deN(13, 1500)).pocos1v1(), "13 − 10 = 3");
        assertTrue(TarjetaPerfil.chispa(deN(3, 1500)).pocos1v1());
        assertNull(TarjetaPerfil.chispa(deN(3, 1500)).spark());
    }

    // ----- HTML (caracterización del de WatchlistHoverCard) -----

    @Test void elHtmlCompleto() {
        TarjetaPerfil.Datos d = new TarjetaPerfil.Datos("ES", "TCC", 900, 1905, 2010, 120, 80, new int[]{ 1, 2, 3, 4 }, false);
        assertEquals("<html><b>12Tirador</b>  · ES  · TCC<br>ELO 1v1: <b>1905</b>  · máx 2010<br>"
                + "Winrate 1v1: 60% (200 partidas)<br><font size='2' color='gray'>Rating (hasta hace ~10 partidas):</font></html>",
                TarjetaPerfil.html("12Tirador", d));
    }

    @Test void elHtmlSinDatosDeLadderYConPocos1v1() {
        TarjetaPerfil.Datos d = new TarjetaPerfil.Datos("", "", 42, null, null, null, null, null, true);
        assertEquals("<html><b>a&lt;b</b><br><br>42 partidas jugadas<br><font size='2' color='gray'>(pocos 1v1 recientes para la gráfica)</font></html>",
                TarjetaPerfil.html("a<b", d));
    }

    // ----- nocturno (1.4) -----

    static int[] cron(int n) { int[] s = new int[n]; for (int i = 0; i < n; i++) s[i] = 1000 + i; return s; }

    @Test void sinArchivoDeChispasVaALaApi() {
        assertNull(TarjetaPerfil.desdeNocturno(null, true, null, 1500, null, "es"));
    }

    @Test void unaParcialCortaVaALaApiYUnaLargaNo() {
        assertNull(TarjetaPerfil.desdeNocturno(cron(13), false, null, 1500, new int[]{ 1500, 50, 0, 0 }, "es"), "parcial: 13 puntos no dicen «pocos 1v1»");
        TarjetaPerfil.Datos d = TarjetaPerfil.desdeNocturno(cron(14), false, null, 1500, new int[]{ 1500, 50, 0, 0 }, "es");
        assertNotNull(d);
        assertArrayEquals(new int[]{ 1000, 1001, 1002, 1003 }, d.spark(), "sin las 10 más recientes (1004-1013)");
    }

    @Test void unaCompletaCortaEsPocos1v1SinLlamadas() {
        TarjetaPerfil.Datos d = TarjetaPerfil.desdeNocturno(cron(12), true, null, null, new int[]{ 1200, 30, 0, 0 }, "fr");
        assertNotNull(d);
        assertTrue(d.pocos1v1());
        assertNull(d.spark());
        TarjetaPerfil.Datos vacia = TarjetaPerfil.desdeNocturno(new int[0], true, null, null, new int[]{ 0, 0, 1500, 40 }, null);
        assertNotNull(vacia, "activo (en elo_ayer), completa y sin puntos: tampoco hace falta la API");
        assertTrue(vacia.pocos1v1());
        assertNull(TarjetaPerfil.desdeNocturno(new int[0], true, null, 1500, null, null),
                "fuera de elo_ayer = fuera del alcance de sfr-data: su perfil real sale de la API, como antes");
    }

    @Test void losDatosDeCabecera() {
        TarjetaPerfil.Datos d = TarjetaPerfil.desdeNocturno(cron(25), true, new int[]{ 2010, 120, 80 }, 1905, new int[]{ 1890, 200, 0, 0 }, "es");
        assertEquals("ES", d.pais());
        assertEquals(1905, d.rating(), "el ELO que la app ya conoce manda sobre el de anoche");
        assertEquals(2010, d.maxRating());
        assertEquals(120, d.wins());
        assertEquals(80, d.losses());
        assertEquals(200, d.games());
        assertEquals("", d.clan(), "el nocturno no trae clan");
        assertEquals(15, d.spark().length);
        TarjetaPerfil.Datos sinEloActual = TarjetaPerfil.desdeNocturno(cron(25), true, new int[]{ -1, -1, -1 }, null, new int[]{ 1890, 200, 0, 0 }, null);
        assertEquals(1890, sinEloActual.rating(), "sin ELO actual, el de anoche");
        assertNull(sinEloActual.maxRating(), "-1 = el volcado no lo trae");
        assertNull(sinEloActual.wins());
        assertEquals("", sinEloActual.pais());
    }
}
