package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.model.Actividad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.MatchPlayer;
import dev.tirador.aoe2radar.sfrdata.DescargaSfr;
import dev.tirador.aoe2radar.sfrdata.EloNocturno;
import dev.tirador.aoe2radar.sfrdata.PerfilesSfr;
import dev.tirador.aoe2radar.sfrdata.SfrDataClient;
import dev.tirador.aoe2radar.util.RelojFalso;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnioDesdeSfr, antigua {@code actividadDesdeShard} de SpoilerFreeRecs: el año de un jugador a partir del paquete
 * nocturno de sfr-data, «nocturno primero» (sin tocar la API). Dobles:
 * <ul>
 * <li>{@code convertir(pid, shard, nombreSiFalta)} es package-private: se prueba directamente con mapas/listas de
 *     Map.of/List.of, tal como llegan de Json (números como Long).</li>
 * <li>EloNocturno: instancia real, pero sin llamar a cargar(); sus nombres se ponen a mano en su mapa público
 *     {@code nombres} (pid → {nombre, país}). Para leer() sí se ejerce cargar(), con una red falsa.</li>
 * <li>PerfilesSfr: null cuando el test es solo de convertir() (no lo usa); real con red falsa para leer().</li>
 * <li>NombresJuego: un doble que devuelve "Mapa:"+clave y "Civ:"+clave.</li>
 * <li>Reloj: RelojFalso.</li>
 * <li>Map&lt;Long,String&gt; paisDe: un HashMap normal.</li>
 * </ul>
 * Nada de esto toca la red ni el disco de verdad: EloNocturno/PerfilesSfr con red falsa escriben en un @TempDir.
 */
class AnioDesdeSfrTest {

    @TempDir Path dir;
    final RelojFalso reloj = new RelojFalso();
    final Map<Long, String> paisDe = new HashMap<>();
    final NombresJuego nombres = new NombresJuego() {
        @Override public String mapa(String clave) { return "Mapa:" + clave; }
        @Override public String civ(String clave) { return "Civ:" + clave; }
    };

    /** Nadie debería llamar a la red en los tests de convertir(): si se llama, el test falla con un mensaje claro. */
    static final DescargaSfr SIN_RED = new DescargaSfr() {
        @Override public Respuesta condicional(String url, String etag, int timeoutS) { throw new AssertionError("convertir() no va a la red"); }
        @Override public byte[] bytes(String url, int timeoutS) { throw new AssertionError("convertir() no va a la red"); }
    };
    static final SfrDataClient.Etags SIN_ETAGS = new SfrDataClient.Etags() {
        @Override public String leer(String n) { return ""; }
        @Override public void guardar(String n, String e) { }
    };

    /** Un EloNocturno real que nunca llama a cargar(): para convertir() basta con rellenar su mapa público de nombres. */
    EloNocturno eloSinRed() {
        SfrDataClient sfr = new SfrDataClient(dir, SIN_RED, SIN_ETAGS, new CacheService(reloj), () -> "https://elo/");
        return new EloNocturno(sfr, new CacheService(reloj), Runnable::run, () -> LocalDate.of(2026, 9, 25));
    }

    AnioDesdeSfr servicio(EloNocturno elo) { return new AnioDesdeSfr(elo, null, paisDe, nombres, reloj); }

    static List<Object> fila(Object... xs) { return List.of(xs); }

    // ===================== convertir(): formato v2 =====================

    @Test void v2_ladderPorIndiceYMapaPorIndiceUsandoNombresJuego() {
        Map<String, Object> shard = Map.of(
                "v", 2L,
                "civs", List.of("Aztecs", "Britons"),
                "mapas", List.of("arabia", "arena"),
                "j", Map.of("1", List.of(fila(100L, 1000L, 2000L, 0L, 1L, List.of(
                        fila(1L, 0L, 1L, 1500L, 20L, 1L),
                        fila(2L, 1L, 2L, 1400L, -20L, 0L)
                )))));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "nombreSiFalta");
        Match m = a.partidas().get(0);
        assertEquals(100L, m.id);
        assertEquals(Instant.ofEpochSecond(1000L), m.started);
        assertEquals(Instant.ofEpochSecond(2000L), m.finished);
        assertEquals("1v1 Random Map", m.mode, "ladderIdx 0 → rm_1v1 → modoDeLadder");
        assertEquals("Mapa:arena", m.map, "mapaIdx 1 → «arena» → nombres.mapa(clave)");
        assertEquals("arena", m.mapaClave);
        assertEquals("Civ:Aztecs", m.players.get(0).civ, "civIdx 0 → «Aztecs» → nombres.civ(clave)");
        assertEquals("Civ:Britons", m.players.get(1).civ, "civIdx 1 → «Britons»");
    }

    @Test void v2_ratingCeroONegativoEsNull() {
        Map<String, Object> shard = Map.of(
                "v", 2L, "civs", List.of(), "mapas", List.of(),
                "j", Map.of("1", List.of(fila(1L, 0L, 0L, 0L, 0L, List.of(
                        fila(1L, 0L, 1L, 0L, 0L, 1L),     // rating 0
                        fila(2L, 0L, 1L, -5L, 0L, 1L),    // rating negativo
                        fila(3L, 0L, 1L, 800L, 0L, 1L)    // rating positivo: se conserva
                )))));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "n");
        List<MatchPlayer> ps = a.partidas().get(0).players;
        assertNull(ps.get(0).rating, "rating ≤ 0 → null");
        assertNull(ps.get(1).rating, "rating ≤ 0 → null");
        assertEquals(800, ps.get(2).rating, "rating > 0 se conserva");
    }

    @Test void v2_wonMenosUnoNullUnoTrueCeroFalse() {
        Map<String, Object> shard = Map.of(
                "v", 2L, "civs", List.of(), "mapas", List.of(),
                "j", Map.of("1", List.of(fila(1L, 0L, 0L, 0L, 0L, List.of(
                        fila(1L, 0L, 1L, 100L, 0L, -1L),
                        fila(2L, 0L, 1L, 100L, 0L, 1L),
                        fila(3L, 0L, 1L, 100L, 0L, 0L)
                )))));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "n");
        List<MatchPlayer> ps = a.partidas().get(0).players;
        assertNull(ps.get(0).won, "won -1 → null");
        assertEquals(Boolean.TRUE, ps.get(1).won, "won 1 → true");
        assertEquals(Boolean.FALSE, ps.get(2).won, "won 0 → false");
    }

    @Test void v2_slotSoloSiEsMayorQueCero() {
        Map<String, Object> shard = Map.of(
                "v", 2L, "civs", List.of(), "mapas", List.of(),
                "j", Map.of("1", List.of(fila(1L, 0L, 0L, 0L, 0L, List.of(
                        fila(1L, 0L, 1L, 100L, 0L, 1L),         // sin slot
                        fila(2L, 0L, 1L, 100L, 0L, 1L, 0L),     // slot 0
                        fila(3L, 0L, 1L, 100L, 0L, 1L, 3L)      // slot 3
                )))));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "n");
        List<MatchPlayer> ps = a.partidas().get(0).players;
        assertNull(ps.get(0).slot, "sin séptimo elemento: sin slot");
        assertNull(ps.get(1).slot, "slot 0 no cuenta");
        assertEquals(3, ps.get(2).slot, "slot > 0 se conserva");
    }

    @Test void v2_teamYRatingDiffSeLeenDeLaPosicionCorrecta() {
        // valores todos distintos en cada posición: si el índice de team o de ratingDiff se desplazara, el
        // valor leído sería el de otra posición y la aserción fallaría.
        Map<String, Object> shard = Map.of(
                "v", 2L, "civs", List.of(), "mapas", List.of(),
                "j", Map.of("1", List.of(fila(1L, 0L, 0L, 0L, 0L, List.of(
                        fila(10L, 0L, 7L, 500L, 55L, 1L)   // pid=10, civIdx=0, team=7, rating=500, ratingDiff=55, won=1
                )))));
        MatchPlayer mp = servicio(eloSinRed()).convertir(1L, shard, "n").partidas().get(0).players.get(0);
        assertEquals(7, mp.team, "team en la posición 2 de la fila");
        assertEquals(55, mp.ratingDiff, "ratingDiff en la posición 4 de la fila");
    }

    @Test void v2_nombresDelRestoDesdeEloNombresConHashSiFalta() {
        EloNocturno elo = eloSinRed();
        elo.nombres.put(2L, new String[]{ "Rival", "fr" });
        Map<String, Object> shard = Map.of(
                "v", 2L, "civs", List.of(), "mapas", List.of(),
                "j", Map.of("1", List.of(fila(1L, 0L, 0L, 0L, 0L, List.of(
                        fila(2L, 0L, 1L, 100L, 0L, 1L),
                        fila(3L, 0L, 1L, 100L, 0L, 1L)
                )))));
        Actividad a = servicio(elo).convertir(1L, shard, "n");
        List<MatchPlayer> ps = a.partidas().get(0).players;
        assertEquals("Rival", ps.get(0).name, "está en elo.nombres");
        assertEquals("#3", ps.get(1).name, "no está: «#pid»");
    }

    @Test void v2_indicesFueraDeRangoDejanLadderInterrogacionYMapaCivVacios() {
        Map<String, Object> shard = Map.of(
                "v", 2L, "civs", List.of("Aztecs"), "mapas", List.of("arabia"),
                "j", Map.of("1", List.of(fila(1L, 100L, 200L, 99L, 99L, List.of(
                        fila(9L, 99L, 1L, 500L, 0L, 1L)
                )))));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "n");
        Match m = a.partidas().get(0);
        assertEquals("?", m.mode, "ladderIdx fuera de rango → «?»");
        assertEquals(dev.tirador.aoe2radar.util.I18n.t("Mapa desconocido", "Unknown map"), m.map, "mapaIdx fuera de rango → mapa desconocido");
        assertEquals("", m.mapaClave, "clave vacía cuando el índice no resuelve nada");
        assertNull(m.players.get(0).civ, "civIdx fuera de rango → civ null");
    }

    @Test void v2_sinElPidEnJDevuelveNull() {
        Map<String, Object> shard = Map.of("v", 2L, "civs", List.of(), "mapas", List.of(),
                "j", Map.of("2", List.of()));
        assertNull(servicio(eloSinRed()).convertir(1L, shard, "n"), "el pid pedido no está en «j»");
    }

    @Test void v2_elPropioJugadorSinNombreEnEloNombresQuedaComoHashPid() {
        // pid 1 (el propio) no está en elo.nombres: ni la fila ni el «nombre» de cabecera lo resuelven.
        Map<String, Object> shard = Map.of("v", 2L, "civs", List.of(), "mapas", List.of(),
                "j", Map.of("1", List.of(fila(1L, 0L, 0L, 0L, 0L, List.of(
                        fila(1L, 0L, 1L, 100L, 0L, 1L)
                )))));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "n");
        assertEquals("#1", a.partidas().get(0).players.get(0).name, "pid 1 no está en elo.nombres: se queda «#1»");
    }

    @Test void v2_elPropioJugadorConNombreEnEloNombresLoToma() {
        EloNocturno elo = eloSinRed();
        elo.nombres.put(1L, new String[]{ "Jorge", "es" });
        Map<String, Object> shard = Map.of("v", 2L, "civs", List.of(), "mapas", List.of(),
                "j", Map.of("1", List.of(fila(1L, 0L, 0L, 0L, 0L, List.of(
                        fila(1L, 0L, 1L, 100L, 0L, 1L)
                )))));
        Actividad a = servicio(elo).convertir(1L, shard, "n");
        assertEquals("Jorge", a.partidas().get(0).players.get(0).name, "pid 1 está en elo.nombres: toma su nombre de ahí");
    }

    /**
     * La línea «if (mp.id == pid && (mp.name.isBlank() || mp.name.startsWith("#")) && !nombre.isBlank()) mp.name =
     * nombre;» es necesaria en v1, donde «nombre» viene de em.get("n") (el nombre que declara la entrada) y puede ser
     * distinto de la fila del propio jugador (un placeholder «#1»). En v2, tanto «nombre» (de cabecera) como
     * mp.name (de la fila del propio jugador) salen del MISMO elo.nombres.get(pid): si no está, los dos quedan
     * vacíos/«#pid» y la condición !nombre.isBlank() no se cumple (ver el test de arriba); si está, los dos ya
     * coinciden antes de llegar a esa línea. No hay combinación de datos v2 en la que esta línea cambie el resultado:
     * en v2 es un no-op (se probó arriba que sin elo.nombres se queda en «#pid», tal como ya haría sin esta línea).
     */
    @Test void v1_elPropioJugadorConNombrePlaceholderTomaElNombreConocido() {
        // v1: el nombre de la fila del propio jugador es un placeholder («#1»), pero la entrada del paquete sí lo conoce.
        Map<String, Object> shard = Map.of("jugadores", Map.of("1", Map.of(
                "n", "Jorge", "c", "es",
                "m", List.of(fila(1L, 0L, 0L, "rm_1v1", "arabia", List.of(
                        fila(1L, "#1", "Aztecs", 1L, 100L, 0L, 1L)
                ))))));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "n");
        assertEquals("Jorge", a.partidas().get(0).players.get(0).name, "el «#1» de la fila se sustituye por el nombre de la entrada");
    }

    // ===================== convertir(): formato v1 =====================

    @Test void v1_jugadoresConNCMFilasCorrectas() {
        Map<String, Object> shard = Map.of("jugadores", Map.of("1", Map.of(
                "n", "Jorge", "c", "es",
                "m", List.of(fila(5L, 10L, 20L, "rm_1v1", "arabia", List.of(
                        fila(1L, "Jorge", "aztecs", 1L, 1500L, 10L, 1L),
                        fila(2L, "Rival", "britons", 2L, 1400L, -10L, 0L)
                ))))));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "n");
        Match m = a.partidas().get(0);
        assertEquals(5L, m.id);
        assertEquals("1v1 Random Map", m.mode);
        assertEquals("Mapa:arabia", m.map);
        MatchPlayer yo = m.players.get(0), rival = m.players.get(1);
        assertEquals("Jorge", yo.name); assertEquals("Civ:aztecs", yo.civ); assertEquals(1, yo.team); assertEquals(1500, yo.rating); assertEquals(10, yo.ratingDiff); assertEquals(Boolean.TRUE, yo.won);
        assertEquals("Rival", rival.name); assertEquals(Boolean.FALSE, rival.won);
    }

    @Test void v1_jugadorAusenteEnJugadoresDevuelveNull() {
        Map<String, Object> shard = Map.of("jugadores", Map.of("2", Map.of("n", "Otro", "m", List.of())));
        assertNull(servicio(eloSinRed()).convertir(1L, shard, "n"), "el pid pedido no tiene entrada");
    }

    @Test void v1_entradaQueNoEsUnMapaDevuelveNull() {
        Map<String, Object> shard = Map.of("jugadores", Map.of("1", "no soy una entrada"));
        assertNull(servicio(eloSinRed()).convertir(1L, shard, "n"));
    }

    // ===================== mapa: vacío/unknown → desconocido; clave conservada =====================

    @Test void mapaVacioDaMapaDesconocidoYMapaClaveVacia() {
        Match m = unaSolaPartidaV1ConMapa("");
        assertEquals(dev.tirador.aoe2radar.util.I18n.t("Mapa desconocido", "Unknown map"), m.map);
        assertEquals("", m.mapaClave, "la clave se conserva tal cual, aunque esté vacía");
    }

    @Test void mapaUnknownSinDistinguirMayusculasDaMapaDesconocidoPeroConservaLaClave() {
        Match m = unaSolaPartidaV1ConMapa("UNKNOWN");
        assertEquals(dev.tirador.aoe2radar.util.I18n.t("Mapa desconocido", "Unknown map"), m.map);
        assertEquals("UNKNOWN", m.mapaClave, "la clave no se toca (ni se pasa a minúsculas)");
    }

    @Test void mapaConNombreUsaNombresJuego() {
        Match m = unaSolaPartidaV1ConMapa("arabia");
        assertEquals("Mapa:arabia", m.map);
        assertEquals("arabia", m.mapaClave);
    }

    Match unaSolaPartidaV1ConMapa(String mapaClave) {
        Map<String, Object> shard = Map.of("jugadores", Map.of("1", Map.of(
                "n", "Jorge", "m", List.of(fila(1L, 0L, 0L, "rm_1v1", mapaClave, List.of())))));
        return servicio(eloSinRed()).convertir(1L, shard, "n").partidas().get(0);
    }

    // ===================== país: se apunta en paisDe si no está en blanco =====================

    @Test void v1_paisNoBlancoSeApuntaEnPaisDe() {
        Map<String, Object> shard = Map.of("jugadores", Map.of("1", Map.of("n", "Jorge", "c", "fr", "m", List.of())));
        servicio(eloSinRed()).convertir(1L, shard, "n");
        assertEquals("fr", paisDe.get(1L));
    }

    @Test void v1_paisEnBlancoNoSeApunta() {
        Map<String, Object> shard = Map.of("jugadores", Map.of("1", Map.of("n", "Jorge", "m", List.of())));
        servicio(eloSinRed()).convertir(1L, shard, "n");
        assertFalse(paisDe.containsKey(1L), "sin país en la entrada, no se toca paisDe");
    }

    @Test void v2_paisDelIndiceNocturnoSeApuntaEnPaisDe() {
        EloNocturno elo = eloSinRed();
        elo.nombres.put(1L, new String[]{ "Jorge", "de" });
        Map<String, Object> shard = Map.of("v", 2L, "civs", List.of(), "mapas", List.of(), "j", Map.of("1", List.of()));
        servicio(elo).convertir(1L, shard, "n");
        assertEquals("de", paisDe.get(1L));
    }

    // ===================== started/finished: 0 → null; > 0 → Instant =====================

    @Test void iniYFinEnCeroSonNullMayorQueCeroEsInstant() {
        Map<String, Object> shard = Map.of("jugadores", Map.of("1", Map.of("n", "Jorge", "m", List.of(
                fila(1L, 0L, 0L, "rm_1v1", "arabia", List.of()),
                fila(2L, 100L, 200L, "rm_1v1", "arabia", List.of())
        ))));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "n");
        Match sinFechas = a.partidas().stream().filter(m -> m.id == 1L).findFirst().orElseThrow();
        Match conFechas = a.partidas().stream().filter(m -> m.id == 2L).findFirst().orElseThrow();
        assertNull(sinFechas.started); assertNull(sinFechas.finished);
        assertEquals(Instant.ofEpochSecond(100L), conFechas.started);
        assertEquals(Instant.ofEpochSecond(200L), conFechas.finished);
    }

    // ===================== orden: de más reciente a más antigua; started null al final =====================

    @Test void ordenDeMasRecienteAMasAntiguaConNullAlFinal() {
        Map<String, Object> shard = Map.of("jugadores", Map.of("1", Map.of("n", "Jorge", "m", List.of(
                fila(1L, 500L, 0L, "rm_1v1", "arabia", List.of()),
                fila(2L, 0L, 0L, "rm_1v1", "arabia", List.of()),      // started null
                fila(3L, 1000L, 0L, "rm_1v1", "arabia", List.of())
        ))));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "n");
        assertEquals(List.of(3L, 1L, 2L), a.partidas().stream().map(m -> m.id).toList(),
                "1000 antes que 500, y la de started null (como EPOCH) al final");
    }

    // ===================== Actividad: nombre/completo/paginas/ms/refId =====================

    @Test void actividadTomaElNombreDelPaqueteYLosCamposFijos() {
        reloj.ahora = 987654321L;
        Map<String, Object> shard = Map.of("jugadores", Map.of("1", Map.of("n", "Jorge", "m", List.of(
                fila(1L, 0L, 0L, "rm_1v1", "arabia", List.of())))));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "nombreSiFalta");
        assertEquals("Jorge", a.nombre());
        assertTrue(a.completo());
        assertEquals(ProfileService.ACT_MAX_PAGINAS, a.paginas());
        assertEquals(987654321L, a.ms(), "ms = reloj.ahoraMs()");
        assertEquals(1L, a.partidas().get(0).refId, "refId = pid en cada partida");
    }

    @Test void actividadUsaNombreSiFaltaCuandoElPaqueteNoTraeNombre() {
        Map<String, Object> shard = Map.of("jugadores", Map.of("1", Map.of("n", "", "m", List.of())));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "nombreSiFalta");
        assertEquals("nombreSiFalta", a.nombre());
    }

    @Test void v2_actividadUsaNombreSiFaltaCuandoElPidNoEstaEnEloNombres() {
        // el caso del H2H de docs/DEUDA.md: si el rival no está en el índice nocturno, no hay nombre que sacar del
        // paquete v2 (no lo trae), y la Actividad debe caer al nombre que le pasa quien llama.
        Map<String, Object> shard = Map.of("v", 2L, "civs", List.of(), "mapas", List.of(),
                "j", Map.of("1", List.of(fila(1L, 0L, 0L, 0L, 0L, List.of()))));
        Actividad a = servicio(eloSinRed()).convertir(1L, shard, "nombreSiFalta");
        assertEquals("nombreSiFalta", a.nombre(), "pid 1 no está en elo.nombres: no hay nombre en el paquete v2");
    }

    // ===================== leer(): compone EloNocturno + PerfilesSfr =====================

    static byte[] gz(String json) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPOutputStream g = new GZIPOutputStream(bo)) { g.write(json.getBytes(StandardCharsets.UTF_8)); }
            return bo.toByteArray();
        } catch (IOException e) { throw new AssertionError(e); }
    }

    /** Red falsa compartida por EloNocturno («https://elo/…») y PerfilesSfr («https://perfiles/…»), por URL completa. */
    static final class RedCombinada implements DescargaSfr {
        final Map<String, byte[]> archivos = new HashMap<>();
        final List<String> pedidas = new ArrayList<>();
        @Override public Respuesta condicional(String url, String etag, int timeoutS) { throw new AssertionError("no es de la rama data"); }
        @Override public byte[] bytes(String url, int timeoutS) throws IOException {
            pedidas.add(url);
            byte[] b = archivos.get(url);
            if (b == null) throw new IOException("HTTP 404 " + url);
            return b;
        }
    }

    final RedCombinada red = new RedCombinada();

    EloNocturno eloConRed() {
        SfrDataClient sfr = new SfrDataClient(dir, red, SIN_ETAGS, new CacheService(reloj), () -> "https://elo/");
        return new EloNocturno(sfr, new CacheService(reloj), Runnable::run, () -> LocalDate.of(2026, 9, 25));
    }

    PerfilesSfr perfilesConRed() {
        SfrDataClient sfr = new SfrDataClient(dir, red, SIN_ETAGS, new CacheService(reloj), () -> "https://perfiles/");
        return new PerfilesSfr(sfr, new CacheService(reloj));
    }

    AnioDesdeSfr servicioConRed(EloNocturno elo, PerfilesSfr perfiles) { return new AnioDesdeSfr(elo, perfiles, paisDe, nombres, reloj); }

    void indice(String json) { red.archivos.put("https://perfiles/index.json", json.getBytes(StandardCharsets.UTF_8)); }

    @Test void leerCargaElEloAntesDeLeerAunqueNoHayaPaquete() throws Exception {
        red.archivos.put("https://elo/elo_ayer.json.gz", gz("{\"fecha\":\"2026-09-24\",\"j\":{\"9\":[1000,10,0,0,\"Ana\",\"es\"]}}"));
        // sin índice de perfiles: shard() devolverá null
        AnioSfr r = servicioConRed(eloConRed(), perfilesConRed()).leer(1L, "n");
        assertNull(r);
        assertTrue(red.pedidas.contains("https://elo/elo_ayer.json.gz"), "el ELO se carga aunque no haya paquete que leer");
    }

    @Test void leerTraeNombreYPaisConocidosSoloPorEloAyerSinPonerlosAMano() throws Exception {
        // nombre y país de pid 5 SOLO están en elo_ayer.json.gz (lo baja cargar()); no se meten a mano en elo.nombres.
        red.archivos.put("https://elo/elo_ayer.json.gz", gz("{\"fecha\":\"2026-09-24\",\"j\":{\"5\":[1000,10,0,0,\"Ana\",\"es\"]}}"));
        indice("{\"v\":2,\"shards\":1,\"base_hasta\":\"2026-09-24\",\"hasta\":\"2026-09-24\",\"civs\":[],\"mapas\":[],\"grupos\":16,\"deltas\":[]}");
        red.archivos.put("https://perfiles/shard-0000.json.gz", gz("{\"j\":{\"5\":[[100,1000,2000,0,0,[]]]}}"));
        AnioSfr r = servicioConRed(eloConRed(), perfilesConRed()).leer(5L, "n");
        assertNotNull(r);
        assertEquals("Ana", r.actividad().nombre(), "el nombre sale de elo_ayer, cargado por leer() antes de convertir");
        assertEquals("es", r.pais());
    }

    @Test void leerEsNullSiElJugadorNoEstaEnElPaquete() throws Exception {
        indice("{\"v\":1,\"shards\":256,\"hasta\":\"2026-09-22\"}");
        red.archivos.put("https://perfiles/shard-044.json.gz", gz("{\"jugadores\":{\"2\":{\"n\":\"Otro\",\"m\":[]}}}"));   // 300 % 256 = 44
        assertNull(servicioConRed(eloConRed(), perfilesConRed()).leer(300L, "n"));
    }

    @Test void leerTomaElHastaDelPaqueteSiLoTrae() throws Exception {
        indice("{\"v\":1,\"shards\":256,\"hasta\":\"2026-09-22\"}");
        red.archivos.put("https://perfiles/shard-044.json.gz",
                gz("{\"hasta\":\"2026-09-21\",\"jugadores\":{\"300\":{\"n\":\"Jorge\",\"m\":[]}}}"));
        AnioSfr r = servicioConRed(eloConRed(), perfilesConRed()).leer(300L, "n");
        assertEquals("2026-09-21", r.hasta(), "el paquete trae su propio «hasta»: se prefiere al del índice");
    }

    @Test void leerTomaElHastaDelIndiceSiElPaqueteNoLoTrae() throws Exception {
        indice("{\"v\":1,\"shards\":256,\"hasta\":\"2026-09-22\"}");
        red.archivos.put("https://perfiles/shard-044.json.gz", gz("{\"jugadores\":{\"300\":{\"n\":\"Jorge\",\"m\":[]}}}"));
        AnioSfr r = servicioConRed(eloConRed(), perfilesConRed()).leer(300L, "n");
        assertEquals("2026-09-22", r.hasta(), "sin «hasta» en el paquete, se usa el del índice");
    }

    @Test void leerTomaElPaisDePaisDeTrasConvertir() throws Exception {
        indice("{\"v\":1,\"shards\":256,\"hasta\":\"2026-09-22\"}");
        red.archivos.put("https://perfiles/shard-044.json.gz", gz("{\"jugadores\":{\"300\":{\"n\":\"Jorge\",\"c\":\"es\",\"m\":[]}}}"));
        AnioSfr r = servicioConRed(eloConRed(), perfilesConRed()).leer(300L, "n");
        assertEquals("es", r.pais());
        assertEquals("es", paisDe.get(300L));
    }
}
