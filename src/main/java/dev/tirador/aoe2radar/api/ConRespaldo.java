package dev.tirador.aoe2radar.api;

import dev.tirador.aoe2radar.model.Clasificacion;
import dev.tirador.aoe2radar.model.Match;
import dev.tirador.aoe2radar.model.PaginaPartidas;
import dev.tirador.aoe2radar.model.Perfil;
import dev.tirador.aoe2radar.model.PerfilEncontrado;
import dev.tirador.aoe2radar.util.Reloj;

import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static dev.tirador.aoe2radar.util.Log.causa;
import static dev.tirador.aoe2radar.util.Log.log;

/**
 * Decorador genérico «primaria con respaldo»: cada petición va a la primaria (el companion); si falla por la red
 * (IOException), esa MISMA petición se repite, después y una sola vez, en la secundaria (World's Edge). Nunca las dos a
 * la vez: o la primaria sola, o la primaria y, tras su fallo, la secundaria, o (en modo respaldo) la secundaria sola.
 * <p>El estado de la primaria («¿está caída?») es UNO para todas las interfaces ({@link Estado}): si /matches cae, el
 * ladder tampoco la intenta durante la espera. Pasa a modo respaldo con N fallos seguidos o con el cortacircuitos del
 * companion abierto (pausa por 429: se pregunta sin esperar); tras la espera, la siguiente petición vuelve a probar la
 * primaria y, si responde, se sale del modo respaldo. La UI se entera solo en los cambios (alCambiar: true al servir
 * algo del respaldo, false cuando la primaria vuelve), para la barra de estado: «datos parciales (fuente de respaldo)».
 * <p>No es respaldo: Detener (InterruptedException, sale tal cual), un «no existe» de la primaria (HTTP 4xx salvo 408
 * y 429: repetirlo en otra fuente no lo arregla) y los errores de programa (RuntimeException). Va a la red: nunca en el
 * EDT, como las fuentes que envuelve.
 * <p>Java no puede implementar «T» a partir de un genérico: {@link #llamar} es el núcleo y las fábricas (partidas,
 * ladder, perfil, busqueda) son la capa fina que implementa cada interfaz delegando en él.
 *
 * @param <T> la interfaz de la fuente (FuentePartidas, FuenteLadder, FuentePerfil, FuenteBusqueda)
 */
public final class ConRespaldo<T> {

    /** Una petición a una fuente cualquiera de tipo T. */
    @FunctionalInterface
    public interface Llamada<T, R> { R en(T fuente) throws IOException, InterruptedException; }

    /**
     * El estado de la primaria, compartido por todos los decoradores que la envuelven (app.Servicios crea uno). Un solo
     * candado privado: los contadores y la hora de vuelta se leen y escriben juntos.
     */
    public static final class Estado {
        /** Fallos de red seguidos de la primaria que la dan por caída. */
        public static final int FALLOS_PARA_RESPALDO = 3;
        /** Cuánto se deja de intentar la primaria tras darla por caída (luego se vuelve a probar). */
        public static final long ESPERA_MS = 5 * 60_000L;

        private final Reloj reloj;
        private final int fallosParaRespaldo;
        private final long esperaMs;
        private final BooleanSupplier cortacircuitosAbierto;   // p. ej. () -> Freno.THROTTLE.pausaRestanteMs() > 0
        private final Consumer<Boolean> alCambiar;             // true: se sirve del respaldo; false: vuelve la primaria
        private final Object candado = new Object();
        private int fallosSeguidos;                            // bajo candado
        private long respaldoHastaMs;                          // bajo candado: hasta cuándo no se intenta la primaria
        private boolean avisadoRespaldo;                       // bajo candado: la UI ya sabe que hay datos parciales

        public Estado(Reloj reloj, BooleanSupplier cortacircuitosAbierto, Consumer<Boolean> alCambiar) {
            this(reloj, FALLOS_PARA_RESPALDO, ESPERA_MS, cortacircuitosAbierto, alCambiar);
        }

        public Estado(Reloj reloj, int fallosParaRespaldo, long esperaMs, BooleanSupplier cortacircuitosAbierto,
                      Consumer<Boolean> alCambiar) {
            this.reloj = reloj; this.fallosParaRespaldo = fallosParaRespaldo; this.esperaMs = esperaMs;
            this.cortacircuitosAbierto = cortacircuitosAbierto; this.alCambiar = alCambiar;
        }

        /** ¿Se salta la primaria en esta petición? Modo respaldo vigente o cortacircuitos del companion abierto. */
        boolean saltarPrimaria() {
            synchronized (candado) { if (reloj.ahoraMs() < respaldoHastaMs) return true; }
            return cortacircuitosAbierto.getAsBoolean();
        }

        /** La primaria respondió: se olvidan los fallos y, si la UI estaba avisada, se le dice que ya no hay respaldo. */
        void exitoPrimaria() {
            synchronized (candado) {
                fallosSeguidos = 0;
                respaldoHastaMs = 0;
                if (!avisadoRespaldo) return;
                avisadoRespaldo = false;
                alCambiar.accept(false);   // dentro del candado: un true y un false de dos hilos llegan en el orden de los cambios
            }
            log("respaldo: el companion responde otra vez; vuelve a ser la fuente");
        }

        /** Un fallo de red de la primaria: al llegar a N seguidos, modo respaldo durante esperaMs. */
        void falloPrimaria(Exception e) {
            boolean abre = false;
            synchronized (candado) {
                // respaldoHastaMs != 0: ya estuvo caída y esta era la prueba tras la espera; un fallo basta para volver.
                if (respaldoHastaMs != 0 || ++fallosSeguidos >= fallosParaRespaldo) {
                    fallosSeguidos = 0;
                    respaldoHastaMs = reloj.ahoraMs() + esperaMs;
                    abre = true;
                }
            }
            if (abre) log("respaldo: el companion falla (" + causa(e) + "); World's Edge durante " + esperaMs / 60_000 + " min");
        }

        /** La secundaria sirvió la petición: aviso a la UI la primera vez (luego, hasta que vuelva la primaria, nada). */
        void servidoPorRespaldo() {
            synchronized (candado) {
                if (avisadoRespaldo) return;
                avisadoRespaldo = true;
                alCambiar.accept(true);    // dentro del candado, ver exitoPrimaria (alCambiar no debe bloquear: en la app, invokeLater)
            }
        }

        /** ¿La próxima petición a la primaria es la prueba tras una espera? (ya estuvo caída y no ha vuelto a responder) */
        boolean probando() { synchronized (candado) { return respaldoHastaMs != 0; } }

        /** ¿Está la UI avisada de que hay datos del respaldo? (tests) */
        boolean enRespaldo() { synchronized (candado) { return avisadoRespaldo; } }
    }

    private final Estado estado;
    private final T primaria, secundaria;
    private final String nombre;   // para el log: «partidas», «ladder»…

    public ConRespaldo(Estado estado, T primaria, T secundaria, String nombre) {
        this.estado = estado; this.primaria = primaria; this.secundaria = secundaria; this.nombre = nombre;
    }

    /** El núcleo: la petición f en la primaria o, según las reglas de arriba, en la secundaria. */
    public <R> R llamar(Llamada<T, R> f) throws IOException, InterruptedException { return llamar(f, f); }

    /**
     * Como llamar(f), con la variante «fresca» de la petición (sin caché) para cuando la primaria está a prueba tras
     * una espera: un acierto de la caché del companion (CompanionApi.conCache) no prueba que el companion responda y
     * sacaría del modo respaldo sin razón. Fuera de la prueba, f tal cual (con su caché).
     */
    public <R> R llamar(Llamada<T, R> f, Llamada<T, R> fresca) throws IOException, InterruptedException {
        if (estado.saltarPrimaria()) return enSecundaria(f, null);
        R r;
        try {
            r = (estado.probando() ? fresca : f).en(primaria);
        } catch (IOException e) {
            if (!esCaida(e)) throw e;
            estado.falloPrimaria(e);
            return enSecundaria(f, e);
        }
        estado.exitoPrimaria();
        return r;
    }

    private <R> R enSecundaria(Llamada<T, R> f, IOException dePrimaria) throws IOException, InterruptedException {
        try {
            R r = f.en(secundaria);
            estado.servidoPorRespaldo();
            return r;
        } catch (IOException e) {
            if (dePrimaria != null) e.addSuppressed(dePrimaria);
            log("respaldo: " + nombre + " tampoco en World's Edge: " + causa(e));
            throw e;
        }
    }

    /**
     * ¿Este error de la primaria es una caída (vale la pena el respaldo)? Sí: red, timeout, 5xx, 429, 408. No: el resto
     * de 4xx (404 de un perfil que no existe, 400 de una petición mal formada): la otra fuente diría lo mismo.
     */
    static boolean esCaida(IOException e) {
        String m = String.valueOf(e.getMessage());
        if (!m.startsWith("HTTP 4")) return true;
        return m.startsWith("HTTP 429") || m.startsWith("HTTP 408");
    }

    // ----- Capa fina: una implementación de cada interfaz que delega en llamar -----

    /** FuentePartidas con respaldo (partidas recientes para recs, «¿ya terminó?»). */
    public static FuentePartidas partidas(Estado estado, FuentePartidas primaria, FuentePartidas secundaria) {
        ConRespaldo<FuentePartidas> r = new ConRespaldo<>(estado, primaria, secundaria, "partidas");
        return new FuentePartidas() {
            @Override public Iterable<Match> partidas(String ids, int pagina, int porPagina) throws IOException, InterruptedException {
                return r.llamar(f -> f.partidas(ids, pagina, porPagina));
            }
            @Override public Iterable<Match> partidas(long pid, int pagina, int porPagina) throws IOException, InterruptedException {
                return r.llamar(f -> f.partidas(pid, pagina, porPagina));
            }
            @Override public PaginaPartidas pagina(long pid, int pagina, int porPagina) throws IOException, InterruptedException {
                return r.llamar(f -> f.pagina(pid, pagina, porPagina));
            }
        };
    }

    /** FuenteLadder con respaldo (top y campanas; con filtro de país, World's Edge no sirve y sale el error). */
    public static FuenteLadder ladder(Estado estado, FuenteLadder primaria, FuenteLadder secundaria) {
        ConRespaldo<FuenteLadder> r = new ConRespaldo<>(estado, primaria, secundaria, "ladder");
        return new FuenteLadder() {
            @Override public Clasificacion clasificacion(String id, int pagina, int porPagina, String pais) throws IOException, InterruptedException {
                return r.llamar(f -> f.clasificacion(id, pagina, porPagina, pais), f -> f.clasificacionFresca(id, pagina, porPagina, pais));
            }
            @Override public Clasificacion clasificacionFresca(String id, int pagina, int porPagina, String pais) throws IOException, InterruptedException {
                return r.llamar(f -> f.clasificacionFresca(id, pagina, porPagina, pais));
            }
        };
    }

    /** FuentePerfil con respaldo (ficha, ELO 1v1, steamId). invalidarPerfil va a las dos (sin red). */
    public static FuentePerfil perfil(Estado estado, FuentePerfil primaria, FuentePerfil secundaria) {
        ConRespaldo<FuentePerfil> r = new ConRespaldo<>(estado, primaria, secundaria, "perfil");
        return new FuentePerfil() {
            @Override public Perfil perfil(long pid) throws IOException, InterruptedException { return r.llamar(f -> f.perfil(pid), f -> f.perfilFresco(pid)); }
            @Override public Perfil perfilFresco(long pid) throws IOException, InterruptedException { return r.llamar(f -> f.perfilFresco(pid)); }
            @Override public void invalidarPerfil(long pid) { primaria.invalidarPerfil(pid); secundaria.invalidarPerfil(pid); }
        };
    }

    /** FuenteBusqueda con respaldo (en World's Edge, solo el alias exacto). */
    public static FuenteBusqueda busqueda(Estado estado, FuenteBusqueda primaria, FuenteBusqueda secundaria) {
        ConRespaldo<FuenteBusqueda> r = new ConRespaldo<>(estado, primaria, secundaria, "búsqueda");
        return q -> r.llamar(f -> f.buscarPerfiles(q));
    }
}
