# Arquitectura objetivo y plan de migración

## Por qué migrar
Un archivo de 13.900 líneas funciona, pero no se puede sostener: cualquier cambio obliga a leerlo entero, no
hay tests, y la lógica de datos (freno, cachés, socket) está mezclada con la pintura Swing. El objetivo no es
«código bonito»: es poder cambiar una cosa sin romper otra, probarlo sin abrir la app, y que otra persona (o
un agente) entienda el proyecto en una tarde.

## Capas (de dentro hacia fuera)
```
model     Datos puros: Match, MatchPlayer, Player, Actividad, Forma, LadderRow… Sin Swing, sin red.
api       Cliente del companion: ApiClient (REST), OngoingSocket (websocket). Solo transporte + parseo.
sfrdata   Cliente de sfr-data: SfrDataClient (index/shards/elo_ayer/muestra/civstats/ladder), con caché en disco.
techtree  Datos de aoe2techtree (data.json, árboles por civ, cadenas por idioma), con caché en disco y ETag.
          Misma capa que sfrdata: fuente externa de datos precalculados. Los iconos (ImageIcon) son de ui.
cache     CacheService: cachés en memoria y disco con TTL, una sola implementación para todas.
service   Reglas de negocio: ProfileService (perfil = shard + «Actualizar hoy»), LiveService (Live now: barrido +
          socket + fantasmas), FormService (forma por resta + fallback), RecService (descargas/enviar al juego),
          WatchlistService (grupos, tops, clanes), StatsService (civ stats), Throttle (freno + cortacircuitos),
          ControlService (control.json).
ui        Swing: una vista por pestaña (WatchlistView, MatchesView, LiveView, ProfileView, RatingsView,
          CivStatsView, TechTreeView) y su presentador (…Presenter). Las vistas no llaman a la red: piden al
          presentador y pintan lo que el presentador les da.
app       AppState (estado observable: qué vista, qué jugador, qué filtros) y Main (arranque, tema, wiring).
util      Json, t() (i18n), formatos, Log.
```
Regla de dependencia: cada capa solo conoce las de dentro. `ui` conoce `service` y `model`; `service` conoce
`api`, `sfrdata`, `techtree`, `cache`, `model`; `api`, `sfrdata` y `techtree` conocen `cache` (guardan en ella lo que
descargan), `model` y `util`; `cache` conoce `model` y `util`; `util` lo puede usar cualquiera y no conoce a nadie (salvo
`model` si hiciera falta); `model` no conoce a nadie. Si una flecha va hacia fuera, está mal.

## Contratos clave (interfaces)
- `ApiClient`: `List<Match> matches(List<Long> pids, int page, int perPage)`, `Profile profile(long pid)`,
  `List<LadderRow> leaderboard(String lb, int page, String country)`, `List<PlayerHit> search(String q)`…
  Todo lo que devuelva pasa por `Throttle`. Nada de Swing.
- `SfrDataClient`: `Optional<Shard> shard(long pid)`, `EloSnapshot eloAyer()`, `EloSnapshot eloHace7()`,
  `Muestra muestraAyer()`, `CivStatsWindow civStats(String ventana)`, `Ladder ladder()`.
- `CacheService`: `<T> T get(String clave, Duration ttl, Supplier<T> cargar)` en memoria y variante en disco.
- `Throttle`: `void adquirir()` (cubo de fichas) y `void pausar(Duration)` (429).
- Presentadores: reciben eventos de la vista (`onBuscar(nick)`, `onAbrirPerfil(pid)`), llaman a servicios en
  un hilo de trabajo y devuelven al EDT un `…ViewModel` inmutable que la vista pinta.

## Fases con criterios de aceptación
**Fase 0 · Red de seguridad.** Repo Git, Maven, `SpoilerFreeRecs.java` dentro de `src/main/java`, harness de
capturas como test (`RegresionCapturas`: las 23 fotos actuales, comparadas píxel a píxel con tolerancia).
Hecho cuando `mvn test` reproduce las capturas en verde.

**Fase 1 · Partición mecánica.** Extraer clases y métodos a los paquetes sin cambiar una línea de lógica.
Orden: model → util → api → sfrdata → cache (y estado de Live/socket). Hecho cuando todo el código estático
que no es de interfaz ha salido de `SpoilerFreeRecs.java` y el harness sigue verde. La interfaz (paneles
internos, código de cada pestaña) NO se parte aquí: se reparte en la fase 3, cuando ya hay servicios con tests
(decisión del 2026-09-24: partirla ahora creaba ficheros que seguían dependiendo de la ventana entera y la
fase 3 los habría rehecho).

**Fase 2 · Servicios con contrato.** Interfaces + implementaciones + tests unitarios con dobles (sin red).
Aquí se unifican las tres cachés y los tres sitios donde hoy se decide «¿llamo a la API?». Hecho cuando cada
servicio tiene tests y ningún `httpText` vive fuera de `api`.

**Fase 3 · Vistas y presentadores.** Pestaña a pestaña, como «estrangulador»: cada pestaña sale ENTERA de
`SpoilerFreeRecs.java` a su vista + presentador y su código se borra del original. Hecho cuando ninguna clase
de `ui` importa `java.net` ni conoce `ApiClient`, y `SpoilerFreeRecs.java` queda como un `Main` de menos de
300 líneas.

**Fase 4 · Cierre.** Deuda anotada resuelta o descartada con motivo, `README` técnico, jpackage desde Maven,
release 1.2.

## Decisiones cerradas (no reabrir)
Civ Stats en una sola página; forma solo como dato de ELO (sin W-L); listas del perfil a 5; Live now sin scroll
horizontal; sin tarjetas flotantes; «nocturno primero» (sfr-data antes que API); perfil sin llamadas al abrir y
«Actualizar hoy» explícito; freno 1 req/s; cortacircuitos 429; aviso de Microsoft y créditos tal como están.

## Deuda conocida al empezar
- Mapas `vivoWatch`/`vivoInfo` escritos desde hilos de fondo y leídos en el EDT.
- Tres cachés distintas (perfilCardCache, ACTIVIDAD_CACHE, cachés en disco) con reglas distintas.
- `config.properties` como único almacén de estado; sin migraciones.
- `EtiquetaRecorte` y los anchos medidos de Live now: lógica de layout mezclada con datos.
