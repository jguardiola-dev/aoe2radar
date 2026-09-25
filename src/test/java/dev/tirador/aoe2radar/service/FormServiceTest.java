package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.model.Forma;
import dev.tirador.aoe2radar.sfrdata.DescargaSfr;
import dev.tirador.aoe2radar.sfrdata.EloNocturno;
import dev.tirador.aoe2radar.sfrdata.SfrDataClient;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FormService (FormaCompanion): la forma por resta (sin red, con el ELO nocturno) y por serie (una llamada, con el
 * reloj inyectado, nunca la hora real). Transporte falso para /profiles/{pid}, como en LiveServiceTest y
 * PerfilesCompanionTest; un EloNocturno real con sus mapas rellenados a mano, como en EloNocturnoTest.
 */
class FormServiceTest {
    static final long HORA = 1_700_000_000_000L;   // nov. 2023, en ms (RelojFalso)
    static final long HORA_S = HORA / 1000;        // en segundos: util.Json.when lee como segundos los números < 1e11

    static final class TransporteFalso implements Transporte {
        final List<String> pedidas = new ArrayList<>();
        String cuerpo = "{}";
        int estado = 200;
        @Override public Respuesta get(String url) { pedidas.add(url); return new Respuesta(estado, cuerpo); }
    }
    static final class ThrottleSinFreno implements Throttle {
        @Override public void adquirir(java.util.function.BooleanSupplier cancelar) { }
        @Override public long registrar429() { return 0; }
    }
    /** Nunca se llama: este test no ejecuta EloNocturno.cargar(), solo rellena sus mapas a mano. */
    static final class SinRed implements DescargaSfr {
        @Override public Respuesta condicional(String url, String etag, int timeoutS) { throw new AssertionError("no usado"); }
        @Override public byte[] bytes(String url, int timeoutS) { throw new AssertionError("no usado"); }
    }
    /** Cuenta cuántas veces se invocó (para probar que porResta lee eloActual/partidasActual de forma perezosa). */
    static final class ContadorLong implements LongFunction<Integer> {
        int llamadas; Integer valor;
        ContadorLong(Integer valor) { this.valor = valor; }
        @Override public Integer apply(long pid) { llamadas++; return valor; }
    }

    /** Una lectura fija de eloWatch/gamesWatch, ignorando el pid: para los tests que no necesitan contar llamadas. */
    static LongFunction<Integer> fijo(Integer v) { return pid -> v; }

    final TransporteFalso red = new TransporteFalso();
    final RelojFalso reloj = new RelojFalso();
    { reloj.ahora = HORA; }
    final CompanionApi api = new CompanionApi(new ApiClient(new ThrottleSinFreno(), red, s -> { }, () -> false));

    /** No se toca el disco (SinRed lanza si algo lo intenta): una carpeta cualquiera, sin necesitar @TempDir. */
    final Path dir = Path.of(System.getProperty("java.io.tmpdir"), "form-service-test");
    final EloNocturno eloNocturno = new EloNocturno(new SfrDataClient(dir, new SinRed(), new SfrDataClient.Etags() {
        @Override public String leer(String n) { return ""; }
        @Override public void guardar(String n, String e) { }
    }, new CacheService(reloj), () -> "https://elo/"), new CacheService(reloj), Runnable::run, () -> LocalDate.of(2026, 9, 25));

    final FormService form = new FormaCompanion(api, eloNocturno, reloj);

    // ===================== porResta =====================

    @Test void porRestaConSnapshotDeAyerYDeHace7CalculaLasDosVentanas() {
        eloNocturno.ayer.put(1L, new int[]{ 1500, 100, 0, 0 });      // {elo1v1, partidas1v1, eloEq, partidasEq}
        eloNocturno.hace7.put(1L, new int[]{ 1450, 90, 0, 0 });
        Forma[] f = form.porResta(1L, fijo(1550), fijo(110));
        assertNotNull(f);
        assertEquals(50, f[0].diff(), "1550 - 1500 (ayer)");
        assertEquals(10, f[0].partidas(), "110 - 100");
        assertNotNull(f[1]);
        assertEquals(100, f[1].diff(), "1550 - 1450 (hace 7)");
        assertEquals(20, f[1].partidas(), "110 - 90");
    }

    @Test void porRestaSinSnapshotDeHace7DejaEsaFormaEnNull() {
        eloNocturno.ayer.put(2L, new int[]{ 1000, 5, 0, 0 });
        Forma[] f = form.porResta(2L, fijo(1010), fijo(6));
        assertNotNull(f);
        assertEquals(10, f[0].diff());
        assertNull(f[1], "sin snapshot de hace 7 días, esa forma no se calcula");
    }

    @Test void porRestaSinSnapshotDeAyerEsNullEntero() {
        Forma[] f = form.porResta(999L, fijo(1500), fijo(10));
        assertNull(f, "sin ELO_AYER no hay resta posible, aunque se conozca el ELO actual");
    }

    @Test void porRestaSinEloActualConocidoEsNullAunqueHayaSnapshot() {
        eloNocturno.ayer.put(3L, new int[]{ 1000, 5, 0, 0 });
        assertNull(form.porResta(3L, fijo(null), fijo(null)), "sin el ELO que ya sabe la app, no hay con qué restar");
        assertNull(form.porResta(3L, fijo(0), fijo(null)), "0 no es un ELO válido, como en la 1.1 (e > 0)");
    }

    @Test void porRestaConPartidasActualNullUsaLaReglaDeUnaOCeroPartidas() {
        eloNocturno.ayer.put(4L, new int[]{ 1500, 100, 0, 0 });   // ayer[1] = 100 > 0
        Forma sube = form.porResta(4L, fijo(1550), fijo(null))[0];
        assertEquals(1, sube.partidas(), "sin partidasActual pero el ELO cambió: se asume 1 partida");
        Forma igual = form.porResta(4L, fijo(1500), fijo(null))[0];
        assertEquals(0, igual.partidas(), "sin partidasActual y el ELO no cambió: se asume 0 partidas");
    }

    @Test void porRestaConPartidasDeAyerNoPositivoUsaTambienLaReglaDeUnaOCero() {
        eloNocturno.ayer.put(5L, new int[]{ 1500, 0, 0, 0 });     // ayer[1] = 0: no fiable aunque SÍ tengamos partidasActual
        Forma sube = form.porResta(5L, fijo(1550), fijo(999))[0];
        assertEquals(1, sube.partidas(), "ayer[1] <= 0: no se resta partidasActual - ayer[1], se usa 1/0 igual que sin partidasActual");
        Forma igual = form.porResta(5L, fijo(1500), fijo(999))[0];
        assertEquals(0, igual.partidas());
    }

    @Test void porRestaNoLeeEloActualSiNoHaySnapshotDeAyer() {
        ContadorLong elo = new ContadorLong(1500);
        ContadorLong partidas = new ContadorLong(10);
        assertNull(form.porResta(777L, elo, partidas));
        assertEquals(0, elo.llamadas, "sin ELO_AYER, la 1.1 nunca llegaba a leer eloWatch (lectura perezosa)");
        assertEquals(0, partidas.llamadas);
    }

    // ===================== porSerie =====================

    /** Un punto de la serie rm_1v1 de /profiles: hace `haceHoras` (en el reloj falso), con su rating y ratingDiff. */
    static String punto(long haceHoras, int rating, int diff) {
        long fechaS = HORA_S - haceHoras * 3600L;
        return "{\"date\":" + fechaS + ",\"rating\":" + rating + ",\"rating_diff\":" + diff + "}";
    }
    static String puntoSinDiff(long haceHoras, int rating) {
        long fechaS = HORA_S - haceHoras * 3600L;
        return "{\"date\":" + fechaS + ",\"rating\":" + rating + ",\"rating_diff\":null}";
    }

    @Test void porSerieCalculaForma24hY7dConPuntosDentroYFueraDeCadaVentana() throws Exception {
        // de la más vieja a la más nueva: base 1400, +100 (hace 192h/8d) -> 1500, -15 (144h/6d) -> 1485,
        // +5 (30h) -> 1490, +10 (20h) -> 1500, +20 (10h) -> 1520 (rating "ahora")
        red.cuerpo = "{\"ratings\":[{\"leaderboard_id\":\"3\",\"ratings\":["
                + punto(10, 1520, 20) + ","
                + punto(20, 1500, 10) + ","
                + punto(30, 1490, 5) + ","
                + punto(144, 1485, -15) + ","
                + punto(192, 1500, 100)
                + "]}]}";
        Forma[] f = form.porSerie(10L);
        assertNotNull(f);
        Forma f24 = f[0], f7 = f[1];

        assertEquals(2, f24.partidas(), "solo las de hace 10h y 20h caen dentro de 24h");
        assertEquals(30, f24.diff(), "20+10: la de 30h queda fuera y marca el corte (1520-1490)");
        assertEquals(2, f24.w()); assertEquals(0, f24.l());
        assertEquals(2, f24.racha()); assertTrue(f24.rachaGana());

        assertEquals(4, f7.partidas(), "10h, 20h, 30h y 144h caen dentro de 7 días (168h); la de 192h queda fuera");
        assertEquals(20, f7.diff(), "20+10+5-15 (o 1520-1500, el corte en la de 192h)");
        assertEquals(3, f7.w()); assertEquals(1, f7.l());
        assertEquals(3, f7.racha(), "la racha se corta en la derrota de hace 144h"); assertTrue(f7.rachaGana());
    }

    @Test void porSerieConTodaLaSerieDentroDeLaVentanaSumaLosDiffsEnVezDeRestarElMasViejo() throws Exception {
        // 2 puntos, ambos dentro de 24h (y de 7d): la resta ingenua (1050 - 1000) daría 50, pero el diff real es 80.
        red.cuerpo = "{\"ratings\":[{\"leaderboard_id\":\"3\",\"ratings\":["
                + punto(2, 1050, 50) + ","
                + punto(5, 1000, 30)
                + "]}]}";
        Forma[] f = form.porSerie(20L);
        assertNotNull(f);
        assertEquals(80, f[0].diff(), "toda la serie cae en la ventana: suma de diffs (50+30), no 1050-1000");
        assertEquals(2, f[0].partidas());
        assertEquals(80, f[1].diff(), "también en la ventana de 7 días: mismos dos puntos");
        assertEquals(2, f[1].partidas());
    }

    @Test void porSerieSinPuntosDeRm1v1EsNull() throws Exception {
        red.cuerpo = "{\"ratings\":[{\"leaderboard_id\":\"4\",\"ratings\":[" + punto(1, 1000, 10) + "]}]}";
        assertNull(form.porSerie(30L), "solo hay serie del ladder 4 (rm_team): ninguna es rm_1v1/3");
    }

    @Test void porSerieAceptaTambienElIdLiteralRm1v1() throws Exception {
        red.cuerpo = "{\"ratings\":[{\"leaderboard_id\":\"rm_1v1\",\"ratings\":[" + punto(1, 1000, 10) + "]}]}";
        Forma[] f = form.porSerie(31L);
        assertNotNull(f, "el id ya viene como \"rm_1v1\" (no solo el numérico \"3\")");
        assertEquals(1, f[0].partidas());
        assertEquals(10, f[0].diff());
    }

    @Test void porSerieSiLaApiFallaLanzaExcepcion() {
        red.estado = 500;
        assertThrows(Exception.class, () -> form.porSerie(40L));
    }

    /** El punto justo en la frontera `desde` (exactamente a 24h) cuenta DENTRO de la ventana: la condición del corte
     * es «< desde», no «<= desde». Si alguien cambiara el operador, esta prueba se pondría en rojo (partidas 1, no 2). */
    @Test void porSerieUnPuntoJustoEnLaFronteraDeLos24hCuentaDentroDeLaVentana() throws Exception {
        red.cuerpo = "{\"ratings\":[{\"leaderboard_id\":\"3\",\"ratings\":["
                + punto(1, 1521, 1) + ","
                + punto(24, 1520, 20)   // exactamente a 24h: pt[0] == desde
                + "]}]}";
        Forma[] f = form.porSerie(50L);
        assertNotNull(f);
        assertEquals(2, f[0].partidas(), "el punto de hace EXACTAMENTE 24h todavía cae dentro de la ventana de 24h");
    }

    /** Un punto con rating_diff null (no cuenta como victoria ni derrota) no rompe la racha de los de alrededor: solo
     * los puntos con diff no nulo la mueven, ni la cortan al saltárselos. */
    @Test void unPuntoConDiffNuloEnMedioDeLaRachaNoLaRompe() throws Exception {
        red.cuerpo = "{\"ratings\":[{\"leaderboard_id\":\"3\",\"ratings\":["
                + punto(1, 1010, 10) + ","          // victoria: racha 1
                + puntoSinDiff(2, 1010) + ","        // sin resultado: no cuenta, no rompe la racha
                + punto(3, 1000, 5)                  // victoria: si no se saltara, seguiría siendo racha 2
                + "]}]}";
        Forma[] f = form.porSerie(60L);
        assertNotNull(f);
        assertEquals(3, f[0].partidas(), "las tres cuentan como partidas, aunque una no tenga resultado");
        assertEquals(2, f[0].w()); assertEquals(0, f[0].l());
        assertEquals(2, f[0].racha(), "la racha sigue en 2 victorias: el punto sin diff se salta, no la corta");
        assertTrue(f[0].rachaGana());
    }

    // ===================== pendiente: la frontera de los 10 minutos =====================

    @Test void pendienteEnSuFronteraExacta() {
        long ultima = HORA - Duration.ofMinutes(10).toMillis();
        assertFalse(form.pendiente(ultima, HORA), "a los 10 minutos justos, todavía NO hace falta recalcular (>, no >=)");
        assertTrue(form.pendiente(ultima - 1, HORA), "un milisegundo más viejo: ya hace falta");
    }

    @Test void pendienteConUnaConsultaReciente() {
        assertFalse(form.pendiente(HORA - 1, HORA), "hace 1 ms: nada pendiente");
    }

    @Test void pendienteSinConsultaPrevia() {
        assertTrue(form.pendiente(0L, HORA), "nunca consultado (0): pendiente");
    }
}
