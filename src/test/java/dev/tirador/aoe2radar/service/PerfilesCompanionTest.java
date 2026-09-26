package dev.tirador.aoe2radar.service;

import dev.tirador.aoe2radar.api.ApiClient;
import dev.tirador.aoe2radar.api.CompanionApi;
import dev.tirador.aoe2radar.api.Throttle;
import dev.tirador.aoe2radar.api.Transporte;
import dev.tirador.aoe2radar.cache.CacheMemoria;
import dev.tirador.aoe2radar.cache.CacheService;
import dev.tirador.aoe2radar.cache.Caducidad;
import dev.tirador.aoe2radar.model.AnioSfr;
import dev.tirador.aoe2radar.model.FichaPerfil;
import dev.tirador.aoe2radar.model.Perfil;
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
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PerfilesCompanion (ProfileService, paso A: ficha y fichaConocida). Sin red: un Transporte falso responde el JSON de
 * /profiles/{pid} y cuenta las peticiones; la caché usa CacheService con un reloj falso (nada espera de verdad).
 */
class PerfilesCompanionTest {

    /**
     * Responde lo mismo a cualquier URL (cuerpo/estado configurables) y apunta cada URL pedida. Para las pruebas del
     * paso B, que piden /profiles/{pid} de varios perfiles en la misma sesión, cuerpoPorUrl permite dar un cuerpo
     * distinto según el SUFIJO de la URL (p. ej. "/profiles/7"); si no hay entrada para esa URL, responde `cuerpo`,
     * así los tests del paso A (que solo usan `cuerpo`) siguen igual.
     */
    static final class TransporteFalso implements Transporte {
        final List<String> pedidas = new ArrayList<>();
        String cuerpo = "{}";
        int estado = 200;
        final Map<String, String> cuerpoPorUrl = new HashMap<>();
        java.util.function.Consumer<String> alPedir = u -> { };   // para mirar el estado del servicio a mitad de una carga
        @Override public Respuesta get(String url) {
            pedidas.add(url);
            alPedir.accept(url);
            String c = cuerpoPorUrl.entrySet().stream().filter(e -> url.endsWith(e.getKey())).map(Map.Entry::getValue)
                    .findFirst().orElse(cuerpo);
            return new Respuesta(estado, c);
        }
    }

    /** Sin freno de verdad: deja pasar todo, para no meter esperas en el test. */
    static final class ThrottleSinFreno implements Throttle {
        @Override public void adquirir(java.util.function.BooleanSupplier cancelar) { }
        @Override public long registrar429() { return 0; }
    }

    final TransporteFalso red = new TransporteFalso();
    final CompanionApi api = new CompanionApi(new ApiClient(new ThrottleSinFreno(), red, s -> { }, () -> false));
    final RelojFalso reloj = new RelojFalso();
    final EstadoVivo estadoVivo = new EstadoVivo(reloj);   // para la regla del ELO de las vinculadas
    final CacheMemoria<Long, FichaPerfil> fichas = new CacheService(reloj).memoria(Caducidad.PERFIL);
    final Map<Long, Object> canalAprendido = new HashMap<>();
    final Map<Long, Object> paisAprendido = new HashMap<>();
    final PerfilesCompanion servicio = new PerfilesCompanion(api, fichas,
            (pid, v) -> canalAprendido.put(pid, v), (pid, v) -> paisAprendido.put(pid, v), null, null, new EloSesion(estadoVivo, reloj, EloSesion.ESPERA));   // el año y el historial se prueban en AnioDesdeSfrTest e HistorialPerfilTest

    /** Cuántas veces se pidió /profiles/{pid} a la red. */
    long peticiones(long pid) {
        String sufijo = "/profiles/" + pid;
        return red.pedidas.stream().filter(u -> u.endsWith(sufijo)).count();
    }

    // ----- 1. conversión de ladders, país, clan y partidas

    @Test void fichaMapeaLosIdsNumericosDeLosLaddersConocidos() {
        red.cuerpo = "{\"country\":\" es \",\"clan\":\" R1 \",\"games\":321,\"leaderboards\":["
                + "{\"leaderboard_id\":\"3\",\"rating\":1500.5,\"rank\":88,\"maxRating\":1600.6,\"wins\":10,\"losses\":2},"
                + "{\"leaderboard_id\":\"4\",\"rating\":1000},"
                + "{\"leaderboard_id\":\"13\",\"rating\":900},"
                + "{\"leaderboard_id\":\"14\",\"rating\":800}]}";
        FichaPerfil f = servicio.ficha(1L);
        assertNotNull(f);
        assertArrayEquals(new int[]{ 1501, 88, 1601, 10, 2 }, f.ladders().get("rm_1v1"), "id numérico 3 → rm_1v1 (el redondeo de 1500.5 es de CompanionApi, no de esta regla)");
        assertEquals(1000, f.ladders().get("rm_team")[0], "id numérico 4 → rm_team");
        assertEquals(900, f.ladders().get("ew_1v1")[0], "id numérico 13 → ew_1v1");
        assertEquals(800, f.ladders().get("ew_team")[0], "id numérico 14 → ew_team");
        assertEquals("es", f.pais(), "país con espacios: trim");
        assertEquals("R1", f.clan(), "clan con espacios: trim");
        assertEquals(321L, f.partidas(), "partidas viene de games");
    }

    @Test void unLadderConIdYaLiteralSeConservaTalCual() {
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"rm_1v1\",\"rating\":700}]}";
        FichaPerfil f = servicio.ficha(6L);
        assertEquals(700, f.ladders().get("rm_1v1")[0], "un id que ya llega como \"rm_1v1\" no pasa por el switch, y entra igual");
    }

    @Test void unLadderFueraDeLosConocidosNoEntraEnLaFicha() {
        red.cuerpo = "{\"leaderboards\":["
                + "{\"leaderboard_id\":\"rm_1v1_console\",\"rating\":999},"
                + "{\"leaderboard_id\":\"dm_1v1\",\"rating\":999}]}";
        FichaPerfil f = servicio.ficha(2L);
        assertTrue(f.ladders().isEmpty(), "ni rm_1v1_console ni dm_1v1 están en LADDER_IDS");
    }

    @Test void losCamposAusentesDeUnLadderQuedanEnCero() {
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"3\"}]}";
        FichaPerfil f = servicio.ficha(3L);
        assertArrayEquals(new int[]{ 0, 0, 0, 0, 0 }, f.ladders().get("rm_1v1"),
                "rating, rango, máximo, victorias y derrotas null → 0");
    }

    @Test void paisYClanAusentesQuedanVacios() {
        red.cuerpo = "{\"games\":5}";
        FichaPerfil f = servicio.ficha(4L);
        assertEquals("", f.pais());
        assertEquals("", f.clan());
    }

    @Test void laCadenaNullComoPaisOClanQuedaVacia() {
        red.cuerpo = "{\"country\":\"null\",\"clan\":\"null\"}";
        FichaPerfil f = servicio.ficha(5L);
        assertEquals("", f.pais(), "la 1.1 trataba el texto \"null\" como ausente");
        assertEquals("", f.clan());
    }

    // ----- 3. caché: fresco < 30 min, a los 30 min justos ya no

    @Test void unaSegundaFichaAntesDeLosTreintaMinutosNoVaALaRed() {
        red.cuerpo = "{\"games\":1}";
        servicio.ficha(10L);
        assertEquals(1, peticiones(10L));
        reloj.avanzar(Duration.ofMinutes(30).toMillis() - 1);
        servicio.ficha(10L);
        assertEquals(1, peticiones(10L), "a 30 min menos 1 ms sigue fresca: no se vuelve a pedir");
    }

    @Test void conLaFichaFrescaNoSeAprendeNadaDeNuevo() {
        red.cuerpo = "{\"country\":\"es\",\"socialTwitchChannel\":\"canal\"}";
        servicio.ficha(12L);
        canalAprendido.clear(); paisAprendido.clear();
        servicio.ficha(12L);
        assertTrue(canalAprendido.isEmpty() && paisAprendido.isEmpty(), "sin llamada no hay nada que aprender: la caché va antes");
    }

    @Test void alosTreintaMinutosJustosVaALaRed() {
        red.cuerpo = "{\"games\":1}";
        servicio.ficha(11L);
        reloj.avanzar(Duration.ofMinutes(30).toMillis());
        servicio.ficha(11L);
        assertEquals(2, peticiones(11L), "frontera estricta: a la edad justa, ya no vale");
    }

    // ----- 4. fallo: null y no guarda nada; lo último conocido sobrevive

    @Test void siLaLlamadaFallaFichaDevuelveNullYNoGuardaNada() {
        red.estado = 500;
        assertNull(servicio.ficha(20L));
        assertNull(servicio.fichaConocida(20L), "nunca hubo una ficha válida que guardar");
    }

    @Test void unJsonRotoTambienDevuelveNullSinGuardarNada() {
        red.cuerpo = "{esto no es json";
        assertNull(servicio.ficha(21L));
        assertNull(servicio.fichaConocida(21L));
    }

    @Test void unFalloTrasUnaFichaBuenaDejaSobrevivirLaUltimaConocida() {
        red.cuerpo = "{\"games\":7}";
        FichaPerfil buena = servicio.ficha(22L);
        assertNotNull(buena);
        reloj.avanzar(Duration.ofMinutes(30).toMillis());   // caduca, para que el siguiente ficha() intente ir a la red
        red.estado = 500;
        assertNull(servicio.ficha(22L), "la llamada que falla devuelve null");
        assertSame(buena, servicio.fichaConocida(22L), "pero lo último conocido no se pierde con un fallo posterior");
    }

    // ----- 5. fichaConocida: la caducada sin red, null si nunca se pidió

    @Test void fichaConocidaDevuelveLaCaducadaSinIrALaRed() {
        red.cuerpo = "{\"games\":9}";
        FichaPerfil f = servicio.ficha(30L);
        reloj.avanzar(Duration.ofMinutes(30).toMillis());
        assertEquals(1, peticiones(30L));
        assertSame(f, servicio.fichaConocida(30L), "caducada, pero es lo último que se supo");
        assertEquals(1, peticiones(30L), "fichaConocida no toca la red");
    }

    @Test void fichaConocidaEsNullSiNuncaSePidio() {
        assertNull(servicio.fichaConocida(999L));
    }

    // ----- 6. aprenderCanal y aprenderPais reciben lo crudo, como la 1.1

    @Test void aprenderCanalYAprenderPaisRecibenLosDatosCrudosSinRecortar() {
        red.cuerpo = "{\"country\":\" es \",\"socialTwitchChannel\":\"tira\"}";
        servicio.ficha(40L);
        assertEquals("tira", canalAprendido.get(40L), "el canal tal cual llegó (social_twitch_channel)");
        assertEquals(" es ", paisAprendido.get(40L), "el país CRUDO, sin trim: el trim es solo para la ficha");
    }

    // ===================== Paso B: elo1v1, vinculadas, vinculadasConElo, vinculadasConocidas, eloVinculada, familia =====================

    // ----- 7. elo1v1: ladder 3/rm_1v1, sin caché, null en los demás casos

    @Test void elo1v1DevuelveElRatingDelLadder3IgnorandoOtrosAunqueVenganAntes() {
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"4\",\"rating\":999},{\"leaderboard_id\":\"3\",\"rating\":1234}]}";
        assertEquals(1234, servicio.elo1v1(50L), "el ladder 4 viene primero, pero no cuenta");
    }

    @Test void elo1v1AceptaTambienElIdYaLiteralRm1v1() {
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"rm_1v1\",\"rating\":700}]}";
        assertEquals(700, servicio.elo1v1(51L));
    }

    @Test void elo1v1EsNullSiNoHayLadder1v1() {
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"4\",\"rating\":999}]}";
        assertNull(servicio.elo1v1(52L));
    }

    @Test void elo1v1EsNullSiElLadder1v1NoTieneRating() {
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"3\"}]}";
        assertNull(servicio.elo1v1(53L));
    }

    @Test void elo1v1EsNullSiLaRedFalla() {
        red.estado = 500;
        assertNull(servicio.elo1v1(54L));
    }

    // revisión 1.3, F11: elo1v1Leido distingue «no tiene» de «no se pudo saber»; elo1v1 sigue dando null en ambos
    @Test void elo1v1LeidoDistingueNoTieneDeFallo() {
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"3\",\"rating\":1234}]}";
        assertEquals(new ProfileService.Elo1v1(1234, false), servicio.elo1v1Leido(57L));
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"4\",\"rating\":999}]}";
        assertEquals(new ProfileService.Elo1v1(null, false), servicio.elo1v1Leido(58L), "no tiene 1v1: no es un fallo");
        red.estado = 500;
        assertEquals(new ProfileService.Elo1v1(null, true), servicio.elo1v1Leido(59L), "la red falló: fallo");
    }

    @Test void vinculadasConEloNoRecuerdaUnFalloComoSinElo() {
        red.cuerpoPorUrl.put("/profiles/90", "{\"linked_profiles\":[{\"profile_id\":91,\"name\":\"Ana\",\"games\":10}]}");
        red.cuerpoPorUrl.put("/profiles/91", "{esto no es json");   // la petición del ELO de 91 falla
        servicio.vinculadasConElo(90L);
        assertNull(servicio.eloVinculada(91L), "no se sabe: null, no 0 («no tiene»)");
        red.cuerpoPorUrl.put("/profiles/91", "{\"leaderboards\":[{\"leaderboard_id\":\"3\",\"rating\":1500}]}");
        servicio.vinculadasConElo(90L);
        assertEquals(2, peticiones(91L), "la segunda vez se vuelve a pedir");
        assertEquals(1500, servicio.eloVinculada(91L));
    }

    @Test void elo1v1NoTieneCacheDosLlamadasSeguidasHacenDosPeticiones() {
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"3\",\"rating\":1000}]}";
        servicio.elo1v1(55L);
        servicio.elo1v1(55L);
        assertEquals(2, peticiones(55L), "elo1v1 pide /profiles cada vez: no hay caché, a diferencia de ficha()");
    }

    @Test void elo1v1AprendeElCanal() {
        red.cuerpo = "{\"socialTwitchChannel\":\"canaltira\",\"leaderboards\":[{\"leaderboard_id\":\"3\",\"rating\":1000}]}";
        servicio.elo1v1(56L);
        assertEquals("canaltira", canalAprendido.get(56L), "elo1v1 usa la misma vía que ficha() y aprende el canal de paso");
    }

    // ----- 8. vinculadas: filtro, normalización de país, sin caché, y limpio ante fallo

    @Test void vinculadasFiltraLaPropiaRepetidasSinIdYSinNombreYNormalizaPais() {
        red.cuerpo = "{\"linked_profiles\":["
                + "{\"profile_id\":1,\"name\":\"Yo\",\"country\":\"es\",\"games\":1},"          // la propia
                + "{\"profile_id\":7,\"name\":\"Ana\",\"country\":\"es\",\"games\":10},"
                + "{\"profile_id\":7,\"name\":\"AnaRepetida\",\"country\":\"es\",\"games\":99}," // repetida (mismo id)
                + "{\"profile_id\":0,\"name\":\"SinId\",\"country\":\"es\",\"games\":1},"        // id <= 0
                + "{\"name\":\"SinCampoId\",\"country\":\"es\",\"games\":1},"                    // falta profile_id
                + "{\"profile_id\":8,\"country\":\"fr\",\"games\":2},"                           // sin nombre
                + "{\"profile_id\":9,\"name\":\"null\",\"country\":\"fr\",\"games\":3},"         // nombre literal \"null\"
                + "{\"profile_id\":10,\"name\":\"Bob\",\"games\":5}]}";                          // sin país
        List<Perfil.Vinculada> v = servicio.vinculadas(1L);
        assertEquals(2, v.size(), "solo quedan 7 (Ana) y 10 (Bob); el resto cae por alguna regla de filtro");
        Perfil.Vinculada ana = v.stream().filter(x -> x.pid() == 7).findFirst().orElseThrow();
        assertEquals("Ana", ana.nombre());
        assertEquals("ES", ana.pais(), "país en mayúsculas");
        assertEquals(10, ana.partidas(), "conserva las partidas");
        Perfil.Vinculada bob = v.stream().filter(x -> x.pid() == 10).findFirst().orElseThrow();
        assertEquals("", bob.pais(), "sin país: cadena vacía, no null");
    }

    @Test void vinculadasNoTieneCacheDosLlamadasSeguidasHacenDosPeticiones() {
        red.cuerpo = "{\"linked_profiles\":[{\"profile_id\":7,\"name\":\"Ana\",\"country\":\"es\",\"games\":10}]}";
        servicio.vinculadas(1L);
        servicio.vinculadas(1L);
        assertEquals(2, peticiones(1L), "vinculadas() pide la red siempre, como elo1v1");
    }

    @Test void vinculadasSiLaRedFallaDevuelveListaVaciaYNoApuntaFamilia() {
        red.estado = 500;
        List<Perfil.Vinculada> v = servicio.vinculadas(1L);
        assertTrue(v.isEmpty());
        assertNull(servicio.familia(1L), "un fallo no llega a apuntar familia (out queda vacío)");
    }

    // ----- 9. familia: se apunta en los dos sentidos al consultar vinculadas, null si no hay nada que apuntar

    @Test void familiaSeApuntaEnAmbosSentidosTrasConsultarVinculadas() {
        red.cuerpo = "{\"linked_profiles\":["
                + "{\"profile_id\":7,\"name\":\"Ana\",\"country\":\"es\",\"games\":10},"
                + "{\"profile_id\":8,\"name\":\"Bea\",\"country\":\"es\",\"games\":5}]}";
        servicio.vinculadas(1L);
        assertEquals(Map.of(7L, "Ana", 8L, "Bea"), servicio.familia(1L), "familia(1) conoce a sus hijas por su nombre");
        assertEquals(Map.of(1L, "—"), servicio.familia(7L), "desde la otra punta el nombre no se conoce: queda «—»");
        assertEquals(Map.of(1L, "—"), servicio.familia(8L));
    }

    @Test void familiaQuedaNullSiVinculadasNoDevuelveNinguna() {
        red.cuerpo = "{\"linked_profiles\":[]}";
        servicio.vinculadas(60L);
        assertNull(servicio.familia(60L), "lista vacía: no hay nada que apuntar");
    }

    @Test void familiaEsNullSiNuncaSeConsultaronVinculadas() {
        assertNull(servicio.familia(999L));
    }

    /**
     * DEUDA fila 79: familia() entrega el mapa interno de la familia (sin copia) y mejorAlt lo puede leer en el
     * EDT mientras vinculadas() lo escribe desde un hilo de fondo. Arreglo: el mapa interno es un
     * ConcurrentHashMap, no un HashMap, para que esa lectura/escritura concurrente sea segura. Antes del arreglo
     * era un HashMap corriente y este assertInstanceOf fallaba.
     */
    @Test void familiaDevuelveUnMapaSeguroParaLeerloDesdeOtroHilo() {
        red.cuerpo = "{\"linked_profiles\":[{\"profile_id\":7,\"name\":\"Ana\",\"country\":\"es\",\"games\":10}]}";
        servicio.vinculadas(1L);
        assertInstanceOf(Map.class, servicio.familia(1L));
        assertInstanceOf(java.util.concurrent.ConcurrentMap.class, servicio.familia(1L),
                "el mapa por id debe ser concurrente: vinculadas() lo escribe desde un worker mientras mejorAlt lo lee en el EDT");
    }

    // ----- 10. vinculadasConElo / vinculadasConocidas / eloVinculada: se recuerdan en la sesión

    @Test void vinculadasConEloRecuerdaLasVinculadasYElEloDeCadaUna() {
        red.cuerpoPorUrl.put("/profiles/70", "{\"linked_profiles\":["
                + "{\"profile_id\":71,\"name\":\"Ana\",\"country\":\"es\",\"games\":10},"
                + "{\"profile_id\":72,\"name\":\"Bea\",\"country\":\"es\",\"games\":5}]}");
        red.cuerpoPorUrl.put("/profiles/71", "{\"leaderboards\":[{\"leaderboard_id\":\"3\",\"rating\":1500}]}");
        red.cuerpoPorUrl.put("/profiles/72", "{\"leaderboards\":[{\"leaderboard_id\":\"4\",\"rating\":900}]}");

        assertNull(servicio.vinculadasConocidas(70L), "antes de pedirlas, ninguna conocida");

        List<Perfil.Vinculada> v = servicio.vinculadasConElo(70L);
        assertEquals(2, v.size());
        assertSame(v, servicio.vinculadasConocidas(70L), "las recuerda tal cual las devolvió");
        assertEquals(1500, servicio.eloVinculada(71L), "71 tiene 1v1");
        assertEquals(0, servicio.eloVinculada(72L), "72 no tiene 1v1: 0, no null");
        assertNull(servicio.eloVinculada(999L), "nunca preguntada: null");
    }

    @Test void vinculadasConEloRecuerdaLaListaAntesDePedirLosElo() {
        red.cuerpoPorUrl.put("/profiles/75", "{\"linked_profiles\":[{\"profile_id\":76,\"name\":\"Ana\",\"games\":1}]}");
        List<Object> vistaAlPedirElElo = new ArrayList<>();
        red.alPedir = u -> { if (u.endsWith("/profiles/76")) vistaAlPedirElElo.add(servicio.vinculadasConocidas(75L)); };
        servicio.vinculadasConElo(75L);
        assertEquals(1, vistaAlPedirElElo.size());
        assertNotNull(vistaAlPedirElElo.get(0), "como la 1.1: la lista se ve ya mientras llegan los ELO (un repintado la pinta sin ELO)");
    }

    @Test void vinculadasConEloPideCadaEloUnaSolaVezEnLaSesion() {
        red.cuerpoPorUrl.put("/profiles/80", "{\"linked_profiles\":["
                + "{\"profile_id\":81,\"name\":\"Ana\",\"country\":\"es\",\"games\":10}]}");
        red.cuerpoPorUrl.put("/profiles/81", "{\"leaderboards\":[{\"leaderboard_id\":\"3\",\"rating\":1500}]}");

        servicio.vinculadasConElo(80L);
        servicio.vinculadasConElo(80L);
        assertEquals(2, peticiones(80L), "vinculadas(80) no tiene caché: se repite en la segunda llamada");
        assertEquals(1, peticiones(81L), "pero el ELO de 81 ya se sabía de la primera vez: no se vuelve a pedir");
    }

    @Test void vinculadasConEloVuelveAPedirElEloCuandoElJugadorTerminaUnaPartida() {
        red.cuerpoPorUrl.put("/profiles/85", "{\"linked_profiles\":[{\"profile_id\":86,\"name\":\"Ana\",\"games\":10}]}");
        red.cuerpoPorUrl.put("/profiles/86", "{\"leaderboards\":[{\"leaderboard_id\":\"3\",\"rating\":1500}]}");
        servicio.vinculadasConElo(85L);
        estadoVivo.marcarJugando(86L, 555L);
        estadoVivo.marcarFuera(86L);                            // 86 termina una partida
        reloj.avanzar(EloSesion.ESPERA.toMillis() - 1);
        servicio.vinculadasConElo(85L);
        assertEquals(1, peticiones(86L), "aún dentro de la espera: el companion no lo habría recalculado");
        red.cuerpoPorUrl.put("/profiles/86", "{\"leaderboards\":[{\"leaderboard_id\":\"3\",\"rating\":1512}]}");
        reloj.avanzar(1);
        servicio.vinculadasConElo(85L);
        assertEquals(2, peticiones(86L), "pasada la espera: se pide el nuevo");
        assertEquals(1512, servicio.eloVinculada(86L));
    }

    @Test void vinculadasConEloConLaRedCaidaRecuerdaListaVaciaNoNull() {
        red.estado = 500;
        List<Perfil.Vinculada> v = servicio.vinculadasConElo(90L);
        assertTrue(v.isEmpty());
        assertNotNull(servicio.vinculadasConocidas(90L), "recuerda una lista vacía, no deja el estado en null");
        assertTrue(servicio.vinculadasConocidas(90L).isEmpty());
    }

    // ===================== 11. anioSfr: compone AnioDesdeSfr, como en AnioDesdeSfrTest =====================

    @TempDir Path dirSfr;

    static byte[] gz(String json) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPOutputStream g = new GZIPOutputStream(bo)) { g.write(json.getBytes(StandardCharsets.UTF_8)); }
            return bo.toByteArray();
        } catch (IOException e) { throw new AssertionError(e); }
    }

    /** Red falsa para sfr-data (elo y perfiles), por URL completa; sin disco ni red de verdad. */
    static final class RedSfrFalsa implements DescargaSfr {
        final Map<String, byte[]> archivos = new HashMap<>();
        @Override public Respuesta condicional(String url, String etag, int timeoutS) { throw new AssertionError("no usado por anioSfr"); }
        @Override public byte[] bytes(String url, int timeoutS) throws IOException {
            byte[] b = archivos.get(url);
            if (b == null) throw new IOException("HTTP 404 " + url);
            return b;
        }
    }

    static final SfrDataClient.Etags SIN_ETAGS_SFR = new SfrDataClient.Etags() {
        @Override public String leer(String n) { return ""; }
        @Override public void guardar(String n, String e) { }
    };

    /** Un PerfilesCompanion con un AnioDesdeSfr real montado sobre red falsa (mismo montaje que AnioDesdeSfrTest). */
    PerfilesCompanion servicioConAnioSfr(RedSfrFalsa redSfr) {
        SfrDataClient sfrElo = new SfrDataClient(dirSfr, redSfr, SIN_ETAGS_SFR, new CacheService(reloj), () -> "https://elo/");
        EloNocturno elo = new EloNocturno(sfrElo, new CacheService(reloj), Runnable::run, () -> LocalDate.of(2026, 9, 25));
        SfrDataClient sfrPerfiles = new SfrDataClient(dirSfr, redSfr, SIN_ETAGS_SFR, new CacheService(reloj), () -> "https://perfiles/");
        PerfilesSfr perfiles = new PerfilesSfr(sfrPerfiles, new CacheService(reloj));
        NombresJuego nombres = new NombresJuego() {
            @Override public String mapa(String clave) { return "Mapa:" + clave; }
            @Override public String civ(String clave) { return "Civ:" + clave; }
        };
        AnioDesdeSfr anio = new AnioDesdeSfr(elo, perfiles, new HashMap<>(), nombres, reloj);
        return new PerfilesCompanion(api, fichas, (pid, v) -> canalAprendido.put(pid, v), (pid, v) -> paisAprendido.put(pid, v), anio, null, new EloSesion(estadoVivo, reloj, EloSesion.ESPERA));
    }

    @Test void anioSfrDevuelveLoQueDevuelveLeerYNullSiElJugadorNoEsta() throws Exception {
        RedSfrFalsa redSfr = new RedSfrFalsa();
        redSfr.archivos.put("https://elo/elo_ayer.json.gz", gz("{\"fecha\":\"2026-09-24\",\"j\":{}}"));
        redSfr.archivos.put("https://perfiles/index.json", "{\"v\":1,\"shards\":256,\"hasta\":\"2026-09-22\"}".getBytes(StandardCharsets.UTF_8));
        redSfr.archivos.put("https://perfiles/shard-044.json.gz", gz("{\"jugadores\":{\"300\":{\"n\":\"Jorge\",\"m\":[]}}}"));
        PerfilesCompanion conAnio = servicioConAnioSfr(redSfr);

        AnioSfr r = conAnio.anioSfr(300L, "n");
        assertNotNull(r, "300 está en el paquete: anioSfr trae lo mismo que AnioDesdeSfr.leer");
        assertEquals("Jorge", r.actividad().nombre(), "la actividad del paquete viaja tal cual");

        assertNull(conAnio.anioSfr(44L, "n"), "44 cae en el mismo shard (44 % 256 = 44) pero no está en «jugadores»: null");
    }

    // ----- con la caché por URL (plan API de la 1.3): el ELO 1v1 nunca sale de ella -----

    @Test void elo1v1_conCachePorUrl_siempreVaALaRed() {
        CompanionApi conCache = CompanionApi.conCache(new ApiClient(new ThrottleSinFreno(), red, s -> { }, () -> false), reloj);
        PerfilesCompanion s = new PerfilesCompanion(conCache, fichas, (pid, v) -> { }, (pid, v) -> { }, null, null,
                new EloSesion(estadoVivo, reloj, EloSesion.ESPERA));
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"rm_1v1\",\"rating\":1500}]}";
        s.vinculadas(9_001L);   // vinculadas pasa por la caché: deja /profiles/9001 guardado
        assertEquals(1, peticiones(9_001L));
        s.vinculadas(9_001L);
        assertEquals(1, peticiones(9_001L), "vinculadas sí aprovecha lo guardado");
        red.cuerpo = "{\"leaderboards\":[{\"leaderboard_id\":\"rm_1v1\",\"rating\":1532}]}";   // terminó una partida: ELO nuevo
        assertEquals(1532, s.elo1v1(9_001L), "el ELO es el de ahora, no el guardado hace un momento");
        assertEquals(2, peticiones(9_001L));
        s.elo1v1Leido(9_001L);
        assertEquals(3, peticiones(9_001L), "cada petición de ELO sale a la red, como siempre");
    }
}
