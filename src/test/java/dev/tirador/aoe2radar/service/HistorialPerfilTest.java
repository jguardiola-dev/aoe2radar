package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.tirador.aoe2radar.cache.HistorialDisco.ACT_DIAS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HistorialPerfil.descargar es descargarActividad de la 1.1 copiada literal (ver
 * {@code git show HEAD:.../SpoilerFreeRecs.java}, líneas ~2938): estos son tests de caracterización, fijan lo que
 * el método HACE hoy (aunque una regla parezca rara) para poder tocarlo después con red de seguridad. Sin red ni
 * disco: un Transporte falso responde /matches por número de página (como en CompanionApiTest), la memoria es un
 * HashMap, y cargar/guardar/pausa son lambdas que apuntan lo que reciben. porPagina pequeño (3) para que las
 * páginas de prueba quepan en pocas partidas. El reloj es util.RelojFalso, fijo, y las partidas se fechan en
 * relativo a él con Duration.
 *
 * <p>Hallazgo al caracterizar (no es una regla del encargo, pero condiciona varios tests): la fusión que se manda a
 * `parcial` en CADA página es siempre nuevas+previas completo, no solo lo nuevo de esa página; las previas de la
 * base viajan en todos los parciales desde el primero, aunque la actualización aún no haya "parado". Ver el test
 * de la regla 6.
 */
class HistorialPerfilTest {

    /** Responde /matches?profile_ids=…&page=N&per_page=… según N; N no pedido → página vacía (matches: []). */
    static final class TransporteFalso implements Transporte {
        final Map<Integer, String> paginas = new HashMap<>();
        final List<Integer> pedidas = new ArrayList<>();
        final List<String> urls = new ArrayList<>();
        int estado = 200;
        @Override public Respuesta get(String url) {
            urls.add(url);
            Matcher m = Pattern.compile("page=(\\d+)").matcher(url);
            if (!m.find()) throw new AssertionError("URL sin page=: " + url);
            int pag = Integer.parseInt(m.group(1));
            pedidas.add(pag);
            return new Respuesta(estado, paginas.getOrDefault(pag, "{\"matches\":[]}"));
        }
    }

    /** Sin freno de verdad: deja pasar todo, para no meter esperas en el test. */
    static final class ThrottleSinFreno implements Throttle {
        @Override public void adquirir(BooleanSupplier cancelar) { }
        @Override public long registrar429() { return 0; }
    }

    final TransporteFalso red = new TransporteFalso();
    final CompanionApi api = new CompanionApi(new ApiClient(new ThrottleSinFreno(), red, s -> { }, () -> false));
    final RelojFalso reloj = new RelojFalso();
    {   // una hora realista (nov. 2023, en ms): Json.when lee como SEGUNDOS los números < 1e11, así que con el reloj en
        // 1e9 las partidas del JSON caerían en otra época que las de matchAt. Así las dos hablan en milisegundos.
        reloj.ahora = 1_700_000_000_000L;
    }
    final Map<Long, Actividad> memoria = new HashMap<>();
    final Map<Long, Actividad> disco = new HashMap<>();               // el "disco" falso: lo que guardar() escribió
    final List<Long> cargarPedido = new ArrayList<>();                 // qué pids se pidieron a cargar (disco)
    final LongFunction<Actividad> cargar = pid -> { cargarPedido.add(pid); return disco.get(pid); };
    final Consumer<Actividad> guardar = a -> disco.put(a.pid(), a);
    final List<Long> pausas = new ArrayList<>();
    final LongConsumer pausa = pausas::add;
    static final int POR_PAGINA = 3;
    static final long PAUSA_MS = 500;
    static final BooleanSupplier NUNCA_CANCELAR = () -> false;

    HistorialPerfil nuevo() { return new HistorialPerfil(api, memoria, cargar, guardar, pausa, POR_PAGINA, PAUSA_MS, reloj); }

    // ---------- helpers para construir partidas y páginas ----------

    /** Un Match con ese id, iniciado hace `haceMs` desde el reloj (para construir una base a mano). */
    Match matchAt(long id, long haceMs) {
        Match m = new Match();
        m.id = id;
        m.started = Instant.ofEpochMilli(reloj.ahoraMs() - haceMs);
        return m;
    }

    /** Una Actividad-base con una partida por cada id, todas de hace 1 día (dentro del año). */
    Actividad baseCon(long pid, String nombre, boolean completo, int paginas, Long... ids) {
        List<Match> ms = new ArrayList<>();
        for (long id : ids) ms.add(matchAt(id, diasMs(1)));
        return new Actividad(pid, nombre, ms, completo, paginas, reloj.ahoraMs());
    }

    /** El JSON de una partida iniciada hace `haceMs` desde el reloj (started en epoch-millis). */
    String partida(long id, long haceMs) {
        return "{\"match_id\":" + id + ",\"started\":" + (reloj.ahoraMs() - haceMs) + "}";
    }

    /** Una partida sin campo "started": la 1.1 la salta. */
    String partidaSinStarted(long id) {
        return "{\"match_id\":" + id + "}";
    }

    void pagina(int n, String... partidas) {
        red.paginas.put(n, "{\"matches\":[" + String.join(",", partidas) + "]}");
    }

    long diasMs(long dias) { return Duration.ofDays(dias).toMillis(); }

    List<Actividad> parciales(HistorialPerfil h, long pid, String nombre, Actividad base, boolean mas, int maxPaginas,
                              BooleanSupplier cancelar) throws Exception {
        List<Actividad> vistos = new ArrayList<>();
        h.descargar(pid, nombre, base, mas, maxPaginas, vistos::add, cancelar);
        return vistos;
    }

    List<Long> ids(Actividad a) { return a.partidas().stream().map(m -> m.id).toList(); }

    // ===================== 1. base null: primera descarga =====================

    @Test void baseNull_pideDesdeLaPagina1HastaMaxPaginas() throws Exception {
        pagina(1, partida(1, diasMs(1)), partida(2, diasMs(1)), partida(3, diasMs(1)));
        pagina(2, partida(4, diasMs(1)), partida(5, diasMs(1)), partida(6, diasMs(1)));
        Actividad a = nuevo().descargar(10L, "Jorge", null, false, 2, x -> { }, NUNCA_CANCELAR);
        assertEquals(List.of(1, 2), red.pedidas, "pide página 1 y 2, en ese orden");
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), ids(a));
        assertEquals(2, a.paginas());
        assertFalse(a.completo(), "se agotó maxPaginas sin ninguna señal de fin (ni página corta, ni límite de año)");
    }

    @Test void unaPaginaConMenosDePorPaginaPartidasBrutas_completoYPara() throws Exception {
        pagina(1, partida(1, diasMs(1)), partida(2, diasMs(1)));   // 2 brutas < porPagina (3)
        Actividad a = nuevo().descargar(10L, "Jorge", null, false, 5, x -> { }, NUNCA_CANCELAR);
        assertEquals(List.of(1), red.pedidas, "con menos de porPagina ya no hace falta pedir más páginas");
        assertTrue(a.completo());
    }

    @Test void unaPaginaVacia_completoYParaSinLlamarAParcialParaEsaPagina() throws Exception {
        // página 1 vacía (brutas 0): la 1.1 corta con "break" ANTES de construir el parcial de esa página.
        List<Actividad> vistos = new ArrayList<>();
        Actividad a = nuevo().descargar(10L, "Jorge", null, false, 5, vistos::add, NUNCA_CANCELAR);
        assertEquals(List.of(1), red.pedidas);
        assertTrue(a.completo());
        assertTrue(vistos.isEmpty(), "página vacía: ni un solo parcial");
    }

    @Test void partidasConStartedNullSeSaltan() throws Exception {
        pagina(1, partida(1, diasMs(1)), partidaSinStarted(2), partida(3, diasMs(1)));
        Actividad a = nuevo().descargar(10L, "Jorge", null, false, 5, x -> { }, NUNCA_CANCELAR);
        assertEquals(List.of(1L, 3L), ids(a), "la de started null (id 2) no entra");
    }

    @Test void unaPartidaDeHace300DiasEntraYNoPara() throws Exception {
        // guarda de unidades: con el reloj en 1e9 ms, Json.when leía estas fechas como segundos y esta partida caía en
        // la Edad Media (fuera del año). Con el reloj realista entra, como en la app.
        pagina(1, partida(1, diasMs(300)), partida(2, diasMs(300)), partida(3, diasMs(300)));
        pagina(2);
        Actividad a = nuevo().descargar(10L, "Jorge", null, false, 5, x -> { }, NUNCA_CANCELAR);
        assertEquals(List.of(1L, 2L, 3L), ids(a), "300 días está dentro del año");
        assertEquals(List.of(1, 2), red.pedidas, "no para por antigüedad en la página 1");
    }

    @Test void unaPartidaDeHaceMasDeUnAnio_completoYParaAlAcabarEsaPagina() throws Exception {
        pagina(1, partida(1, diasMs(1)), partida(2, ACT_DIAS * diasMs(1) + diasMs(1)), partida(3, diasMs(1)));
        Actividad a = nuevo().descargar(10L, "Jorge", null, false, 5, x -> { }, NUNCA_CANCELAR);
        assertEquals(List.of(1), red.pedidas, "se para al acabar la página donde apareció la vieja: no pide la 2");
        assertTrue(a.completo());
        assertEquals(List.of(1L, 3L), ids(a), "la vieja (id 2) no entra en el resultado, pero sí se sigue leyendo el resto de la página");
    }

    // ===================== 2. actualizar: base con partidas recientes, mas=false =====================

    @Test void actualizar_paraEnLaPaginaDondeApareceUnaPartidaYaConocidaYLasNuevasVanDelante() throws Exception {
        Actividad base = baseCon(10L, "Jorge", true, 3, 100L);
        pagina(1, partida(1, diasMs(1)), partida(2, diasMs(1)), partida(3, diasMs(1)));
        pagina(2, partida(100, diasMs(1)), partida(4, diasMs(1)), partida(5, diasMs(1)));   // 100 ya es conocida: para aquí
        Actividad a = nuevo().descargar(10L, "Jorge", base, false, 5, x -> { }, NUNCA_CANCELAR);
        assertEquals(List.of(1, 2), red.pedidas, "se para en la página 2, donde apareció la conocida: no pide la 3");
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 100L), ids(a), "las nuevas (1,2,3,4,5) van delante de las previas (100)");
    }

    @Test void actualizar_paginasEsElMaximoEntreLasDeLaBaseYLaUltimaPedida() throws Exception {
        Actividad base = baseCon(10L, "Jorge", true, 5, 100L);   // base ya tenía 5 páginas
        pagina(1, partida(100, diasMs(1)));   // la conocida ya sale en la página 1: para enseguida
        Actividad a = nuevo().descargar(10L, "Jorge", base, false, 2, x -> { }, NUNCA_CANCELAR);
        assertEquals(5, a.paginas(), "max(base.paginas()=5, última pedida=1) = 5");
    }

    @Test void actualizar_paginasCreceSiLaUltimaPedidaSuperaALaDeLaBase() throws Exception {
        Actividad base = baseCon(10L, "Jorge", true, 1, 100L);   // base con 1 página
        pagina(1, partida(1, diasMs(1)), partida(2, diasMs(1)), partida(3, diasMs(1)));   // llena, sin conocidas: sigue
        pagina(2, partida(100, diasMs(1)), partida(4, diasMs(1)), partida(5, diasMs(1)));   // la conocida: para en la 2
        Actividad a = nuevo().descargar(10L, "Jorge", base, false, 5, x -> { }, NUNCA_CANCELAR);
        assertEquals(2, a.paginas(), "max(base.paginas()=1, última pedida=2) = 2");
    }

    @Test void actualizar_conBaseCompletaYParadaEnUnaConocidaElResultadoEsCompleto() throws Exception {
        Actividad base = baseCon(10L, "Jorge", true, 1, 100L);
        pagina(1, partida(1, diasMs(1)), partida(2, diasMs(1)), partida(100, diasMs(1)));   // página llena: sin pista de fin por tamaño
        Actividad a = nuevo().descargar(10L, "Jorge", base, false, 5, x -> { }, NUNCA_CANCELAR);
        assertTrue(a.completo(), "lo nuevo ya enlaza con una base completa: el año sigue completo");
    }

    @Test void actualizar_completoHeredaDeLaBaseSiNoSeLlegoAlFinal() throws Exception {
        // base.completo()=false: si la actualización para al encontrar la conocida (página llena, sin señal de fin
        // por tamaño ni por año), el resultado hereda ese "no completo" de la base.
        Actividad base = baseCon(10L, "Jorge", false, 1, 100L);
        pagina(1, partida(1, diasMs(1)), partida(2, diasMs(1)), partida(100, diasMs(1)));   // 3 brutas = porPagina: ninguna pista de "última página" por tamaño
        Actividad a = nuevo().descargar(10L, "Jorge", base, false, 5, x -> { }, NUNCA_CANCELAR);
        assertFalse(a.completo(), "hereda el completo=false de la base");
    }

    // ===================== 3. previas de más de 365 días: se descartan de la fusión =====================

    @Test void lasPreviasDeMasDeUnAnioSeDescartanDeLaFusion() throws Exception {
        Match reciente = matchAt(200L, diasMs(1));                          // dentro del año: se conserva
        Match vieja = matchAt(100L, ACT_DIAS * diasMs(1) + diasMs(1));      // fuera del año: se descarta
        Actividad baseMixta = new Actividad(10L, "Jorge", List.of(reciente, vieja), true, 1, reloj.ahoraMs());
        pagina(1, partida(1, diasMs(1)), partida(2, diasMs(1)), partida(3, diasMs(1)));
        Actividad a = nuevo().descargar(10L, "Jorge", baseMixta, false, 1, x -> { }, NUNCA_CANCELAR);
        assertEquals(List.of(1L, 2L, 3L, 200L), ids(a), "la previa reciente (200) entra, la vieja (100) no");
    }

    // ===================== 4. mas=true: continúa desde base.paginas()+1 =====================

    @Test void masTrue_empiezaEnBasePaginasMasUnoYLasNuevasVanDetras() throws Exception {
        Actividad base = baseCon(10L, "Jorge", true, 2, 100L);
        pagina(3, partida(1, diasMs(1)), partida(2, diasMs(1)), partida(3, diasMs(1)));   // llena: sigue
        pagina(4, partida(4, diasMs(1)), partida(5, diasMs(1)));                          // corta: para
        Actividad a = nuevo().descargar(10L, "Jorge", base, true, 2, x -> { }, NUNCA_CANCELAR);
        assertEquals(List.of(3, 4), red.pedidas, "empieza en 2+1=3 y pide 2 páginas: 3 y 4");
        assertEquals(List.of(100L, 1L, 2L, 3L, 4L, 5L), ids(a), "las nuevas van DETRÁS de las previas");
    }

    @Test void masTrue_completoEsFalsoSalvoQueSeLlegueAlFinal() throws Exception {
        Actividad base = baseCon(10L, "Jorge", true, 2, 100L);
        pagina(3, partida(1, diasMs(1)), partida(2, diasMs(1)), partida(3, diasMs(1)));   // página llena: no hay pista de fin
        Actividad a = nuevo().descargar(10L, "Jorge", base, true, 1, x -> { }, NUNCA_CANCELAR);
        assertFalse(a.completo(), "no se llegó al final (ni página corta, ni límite de año): completo=false pese a que la base era completo=true");
    }

    @Test void masTrue_completoEsVerdaderoSiLaUltimaPaginaEsCorta() throws Exception {
        Actividad base = baseCon(10L, "Jorge", true, 2, 100L);
        pagina(3, partida(1, diasMs(1)));   // 1 bruta < porPagina
        Actividad a = nuevo().descargar(10L, "Jorge", base, true, 1, x -> { }, NUNCA_CANCELAR);
        assertTrue(a.completo(), "página corta: sí se llegó al final");
    }

    // ===================== 5. cancelar: se mira ANTES de cada llamada =====================

    @Test void cancelar_siDevuelveTrueNoPideEsaPaginaYElResultadoNoEsCompletoAunqueLaAnteriorLoFuera() throws Exception {
        // Actualizar con base ya completa (true): la página 1 no aporta ninguna pista de fin (llena, sin conocida
        // ni vieja), así que el flag interno "completo" sigue en true, heredado de la base, al llegar a la página 2.
        // Si esa llamada se cancela, el resultado final NO es completo, aunque ese flag interno lo fuera.
        Actividad base = baseCon(10L, "Jorge", true, 1, 100L);
        pagina(1, partida(1, diasMs(1)), partida(2, diasMs(1)), partida(3, diasMs(1)));
        List<Boolean> llamadas = new ArrayList<>();
        BooleanSupplier cancelarEnLaSegunda = () -> { llamadas.add(true); return llamadas.size() >= 2; };
        Actividad a = nuevo().descargar(10L, "Jorge", base, false, 5, x -> { }, cancelarEnLaSegunda);
        assertEquals(List.of(1), red.pedidas, "cancelar antes de la 2ª llamada: la página 2 no se pide");
        assertFalse(a.completo(), "el flag interno seguía en true (heredado de la base), pero cancelar deja el resultado no completo");
    }

    // ===================== 6. parcial: tras cada página, con la fusión y su "completo" =====================

    @Test void parcialSeLlamaTrasCadaPaginaConLaFusionHastaEseMomentoYSuCompleto() throws Exception {
        Actividad base = baseCon(10L, "Jorge", true, 1, 100L);
        pagina(1, partida(1, diasMs(1)), partida(2, diasMs(1)), partida(3, diasMs(1)));   // llena, sin conocidas: sigue
        pagina(2, partida(100, diasMs(1)), partida(4, diasMs(1)));                        // aparece la conocida (100) y una nueva (4): para
        List<Actividad> vistos = parciales(nuevo(), 10L, "Jorge", base, false, 5, NUNCA_CANCELAR);
        assertEquals(2, vistos.size(), "un parcial tras cada una de las 2 páginas pedidas");

        Actividad p1 = vistos.get(0);
        assertEquals(List.of(1L, 2L, 3L, 100L), ids(p1),
                "tras la página 1 la fusión YA incluye las previas de la base (100): no es solo lo nuevo de esa página");
        assertEquals(1, p1.paginas());
        assertFalse(p1.completo(), "a mitad de una actualización (parar aún false): no completo, aunque la base sí lo era");

        Actividad p2 = vistos.get(1);
        assertEquals(List.of(1L, 2L, 3L, 4L, 100L), ids(p2), "tras la página 2: la nueva de esa página (4) entra también");
        assertEquals(2, p2.paginas());
        assertTrue(p2.completo(), "al pararse por encontrar la conocida (parar=true): completo");
    }

    // ===================== 7. pausa: entre páginas, no tras la última que para =====================

    @Test void laPausaSeHaceEntrePaginasNoTrasLaUltimaQuePara() throws Exception {
        pagina(1, partida(1, diasMs(1)), partida(2, diasMs(1)), partida(3, diasMs(1)));
        pagina(2, partida(4, diasMs(1)));   // corta: para
        nuevo().descargar(10L, "Jorge", null, false, 5, x -> { }, NUNCA_CANCELAR);
        assertEquals(List.of(PAUSA_MS), pausas, "una sola pausa: entre la página 1 y la 2; tras la 2 (que para) no hay pausa");
    }

    // ===================== 8. resultado final: memoria, disco y ms del reloj =====================

    @Test void elResultadoFinalSeGuardaEnMemoriaYEnDiscoConMsDelReloj() throws Exception {
        pagina(1, partida(1, diasMs(1)));
        Actividad a = nuevo().descargar(10L, "Jorge", null, false, 5, x -> { }, NUNCA_CANCELAR);
        assertSame(a, memoria.get(10L), "queda en memoria");
        assertSame(a, disco.get(10L), "y en disco (guardar)");
        assertEquals(reloj.ahoraMs(), a.ms(), "ms = reloj.ahoraMs()");
    }

    // ===================== 9. bug conservado de la 1.1: parcial null y una página con partidas =====================

    /**
     * BUG CONSERVADO A PROPÓSITO (ver docs/DEUDA.md, «historial "Cargar 50 más"»): el botón «Cargar 50 más» de la 1.1
     * llama al historial con parcial = null; en cuanto una página trae alguna partida, parcial.accept(...) revienta con
     * NullPointerException, y como el guardado en memoria/disco vive DESPUÉS del bucle, la página ya descargada se
     * pierde entera: ni memoria ni disco. Cuando se arregle (pasar a -> {} en la llamada), este test debe cambiar
     * a propósito: dejará de lanzar y empezará a guardar.
     */
    @Test void bug_conParcialNullYUnaPaginaConPartidas_lanzaNpeYNoGuardaNadaNiEnMemoriaNiEnDisco() {
        pagina(1, partida(1, diasMs(1)));
        HistorialPerfil h = nuevo();
        assertThrows(NullPointerException.class, () -> h.descargar(10L, "Jorge", null, false, 5, null, NUNCA_CANCELAR));
        assertTrue(memoria.isEmpty(), "el bug pierde la página ya descargada: no llega a guardarse en memoria");
        assertTrue(disco.isEmpty(), "tampoco en disco");
    }

    // ===================== 10. actividad(pid): memoria, si no disco (y sube), null si ninguno =====================

    @Test void actividad_deMemoriaSiEsta() {
        Actividad a = baseCon(10L, "Jorge", true, 1, 1L);
        memoria.put(10L, a);
        assertSame(a, nuevo().actividad(10L));
        assertTrue(cargarPedido.isEmpty(), "estando en memoria, ni se pregunta al disco");
    }

    @Test void actividad_deDiscoSiNoEstaEnMemoriaYLaSubeAMemoria() {
        Actividad a = baseCon(10L, "Jorge", true, 1, 1L);
        disco.put(10L, a);
        Actividad r = nuevo().actividad(10L);
        assertSame(a, r);
        assertSame(a, memoria.get(10L), "se sube a memoria tras leerla del disco");
    }

    @Test void actividad_nullSiNiMemoriaNiDiscoYNoPoneNada() {
        assertNull(nuevo().actividad(999L));
        assertFalse(memoria.containsKey(999L), "un miss no escribe nada en memoria");
    }

    // ===================== 11. traerHoy («Actualizar hoy»): 50 recientes, solo las terminadas nuevas, en memoria =====================

    /** Una partida terminada: started hace `haceMs`, finished un minuto después. */
    String terminada(long id, long haceMs) {
        long ini = reloj.ahoraMs() - haceMs;
        return "{\"match_id\":" + id + ",\"started\":" + ini + ",\"finished\":" + (ini + 60_000) + "}";
    }

    @Test void traerHoy_fundeSoloLasTerminadasNuevasYOrdenaPorFecha() throws Exception {
        long hora = Duration.ofHours(1).toMillis();
        Actividad base = new Actividad(10L, "Jorge", List.of(matchAt(100L, hora), matchAt(101L, diasMs(3))), false, 4, 0L);
        memoria.put(10L, base);
        pagina(1, terminada(1, diasMs(2)), terminada(100, hora), partida(2, hora));   // 1 nueva; 100 ya conocida; 2 sin terminar
        reloj.ahora += 1_000;   // el ms del resultado es el de ahora, no el de la base
        int nuevas = nuevo().traerHoy(10L);
        assertEquals(1, nuevas, "solo cuenta la nueva terminada: ni la conocida ni la que sigue en juego");
        Actividad a = memoria.get(10L);
        assertEquals(List.of(100L, 1L, 101L), ids(a), "fundidas y ordenadas de más reciente a más antigua, no solo puestas delante");
        assertTrue(a.completo(), "tras «Actualizar hoy» el año queda completo, como en la 1.1");
        assertEquals(4, a.paginas(), "conserva las páginas de la base");
        assertEquals(reloj.ahora, a.ms(), "ms del reloj");
        assertEquals(10L, a.partidas().get(1).refId, "la nueva queda referida al jugador");
        assertTrue(disco.isEmpty(), "solo en memoria, no en disco (como la 1.1)");
        assertEquals(List.of(1), red.pedidas, "una sola llamada: la página 1");
        assertTrue(red.urls.get(0).contains("per_page=50"), "las 50 más recientes, no el tamaño de página del historial");
    }

    @Test void traerHoy_lasPartidasSinFechaQuedanAlFinal() throws Exception {
        Match sinFecha = new Match(); sinFecha.id = 102L;   // started null: el orden la trata como 1970
        memoria.put(10L, new Actividad(10L, "Jorge", List.of(sinFecha, matchAt(100L, diasMs(5))), false, 1, 0L));
        pagina(1, terminada(1, diasMs(1)));
        nuevo().traerHoy(10L);
        assertEquals(List.of(1L, 100L, 102L), ids(memoria.get(10L)), "sin fecha, al final (como EPOCH)");
    }

    @Test void traerHoy_sinActividadEnMemoriaCuentaLasNuevasPeroNoGuardaNada() throws Exception {
        pagina(1, terminada(1, diasMs(1)), terminada(2, diasMs(1)));
        assertEquals(2, nuevo().traerHoy(10L));
        assertTrue(memoria.isEmpty(), "sin base no hay con qué fundir: no se inventa una actividad");
    }

    @Test void traerHoy_sinNuevasNoTocaLaMemoria() throws Exception {
        Actividad base = baseCon(10L, "Jorge", false, 2, 100L);
        memoria.put(10L, base);
        pagina(1, terminada(100, diasMs(1)));
        assertEquals(0, nuevo().traerHoy(10L));
        assertSame(base, memoria.get(10L), "nada nuevo: la misma actividad, sin marcarla completa");
    }
}
