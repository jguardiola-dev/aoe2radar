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
          socket + fantasmas), EnlaceVivo (protocolo del socket de partidas en vivo sobre api.SocketVivo: salud
          de la conexión —revisarSalud cierra y reconecta si pasan 10 min sin mensajes ni pong— y la regla del
          «fantasma»: desde la decisión de Jorge del 2026-09-26, un matchRemoved del companion ya NO saca a
          nadie; solo lo hace una comprobación confirmada por la API, finished, con reintento hasta 3 h),
          FormService (forma por resta + fallback), RecService (descargas/enviar al juego, con RecService.recSana
          decidiendo si la copia en disco vale o hay que volver a bajarla),
          WatchlistService (grupos, tops, clanes: repartido en ListaSeguidos, Familias, FiltroLista, BarridoVivos,
          TopLadderService, Campanas, AnotacionesService + api.SteamApi; su estado pasa a AppState en la fase 3),
          StatsService (civ stats), Throttle (freno + cortacircuitos),
          ControlService (control.json).
ui        Swing: una vista por pestaña (WatchlistView, MatchesView, LiveView, ProfileView, RatingsView,
          CivStatsView, TechTreeView) y su presentador (…Presenter). Las vistas no llaman a la red: piden al
          presentador y pintan lo que el presentador les da.
app       Main (arranque, tema, wiring) y Servicios (la raíz de composición: crea y conecta, en un orden fijo,
          todos los servicios que hablan con el companion). El estado compartido de la interfaz (AppState: qué
          vista, qué jugador, qué filtros) vive en ui, en piezas pequeñas (ui.FiltroStats es la primera), para
          que las vistas lo usen sin depender de app (decisión de la fase 3: si viviera en app, ui importaría
          hacia fuera).
(raíz)    SpoilerFreeRecs (el JFrame: declara los campos que comparten las vistas y llama al cableado en orden
          desde el constructor) y las clases Cableado*/AccionesVentana, que hacen ese cableado y las acciones
          de «abrir algo»; viven en el paquete raíz (no en app ni en ui) para leer los campos de la ventana sin
          volverlos public.
util      Json, t() (i18n), formatos, Log, Reloj (inyectable), Archivos (escritura atómica con respaldo: escribe
          a un temporal y mueve con ATOMIC_MOVE; si el move falla —p. ej. Windows con el destino abierto—
          escribe directo, como en la 1.1, en vez de perder el dato).
```
Regla de dependencia: cada capa solo conoce las de dentro. `ui` conoce `service` y `model`; `service` conoce
`api`, `sfrdata`, `techtree`, `cache`, `model`; `api`, `sfrdata` y `techtree` conocen `cache` (guardan en ella lo que
descargan), `model` y `util`, y `sfrdata`/`techtree` además `api` (transporte HTTP y freno); `cache` conoce `model` y `util`; `util` lo puede usar cualquiera y no conoce a nadie (salvo
`model` si hiciera falta); `model` no conoce a nadie. Si una flecha va hacia fuera, está mal. Además, ninguna
clase de `ui` importa `java.net` (la red va siempre por `service`/`api`). `tools/capas.py` audita las dos cosas
por import (cero excepciones a mano) y lo lanza `verificar.ps1`; el paquete raíz (`SpoilerFreeRecs`/`Cableado*`/
`AccionesVentana`) no lo audita el script, porque es la composición de la ventana, no una capa.

## Contratos clave (interfaces)
- `ApiClient`: `List<Match> matches(List<Long> pids, int page, int perPage)`, `Profile profile(long pid)`,
  `List<LadderRow> leaderboard(String lb, int page, String country)`, `List<PlayerHit> search(String q)`…
  Todo lo que devuelva pasa por `Throttle`. Nada de Swing.
- `SfrDataClient`: `Optional<Shard> shard(long pid)`, `EloSnapshot eloAyer()`, `EloSnapshot eloHace7()`,
  `Muestra muestraAyer()`, `CivStatsWindow civStats(String ventana)`, `Ladder ladder()`.
- `CacheService(Reloj)`: una sola regla (`fresco = edad < caducidad`, cifras en `Caducidad`) y dos piezas:
  `CacheMemoria<K,V>` por clave, con `vigente(k)` (¿hay que volver a pedirlo?) y `ultimo(k)` (¿qué pinto mientras?),
  y `Sello` para un dato suelto (`fresco()`/`marcar()`); más `archivoFresco(Path, caducidad)` para el disco. La carga
  la hace quien llama (no `get(clave, ttl, cargar)`): cada sitio tiene su política ante fallos. No son caché la
  actividad del perfil abierto, las vinculadas ni las familias (estado de sesión: ProfileService/AppState).
- `Throttle`: `void adquirir(cancelar)` (cubo de fichas: ráfaga de 5, luego 1/s; Detener corta la espera) y
  `long registrar429()` (pausa global 60→120→240→300 s por episodio; se olvida tras 10 min sin 429 desde que acabó la última pausa).
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
**Cerrada el 2026-09-24** (rama `fase-1-particion`): 51 ficheros en model, util, api, cache, sfrdata, techtree,
service y ui; lo que queda fuera está listado en `DEUDA.md` («cierre fase 1»).

**Fase 2 · Servicios con contrato.** Interfaces + implementaciones + tests unitarios con dobles (sin red).
Aquí se unifican las tres cachés y los tres sitios donde hoy se decide «¿llamo a la API?». Hecho cuando cada
servicio tiene tests y ningún `httpText` vive fuera de `api`.

**Fase 3 · Vistas y presentadores.** Pestaña a pestaña, como «estrangulador»: cada pestaña sale ENTERA de
`SpoilerFreeRecs.java` a su vista + presentador (patrón Presentador/Pantalla/Anfitrion/Tareas, detallado en
[docs/README_TECNICO.md](README_TECNICO.md#cómo-se-añade-una-vista-nueva-patrón-vista--presentador)) y su
código se borra del original. Hecho cuando ninguna clase de `ui` importa `java.net` ni conoce `ApiClient`, y
`SpoilerFreeRecs.java` queda como la ventana (`JFrame`) de menos de 300 líneas, con el cableado en
`Cableado*`/`AccionesVentana` y el arranque en `app.Main`/`app.Servicios`.
**Cerrada** (tanda 4, oleada B + pasada final, y su cierre de deuda en fase 4): `SpoilerFreeRecs.java` en 299
líneas; todas las vistas viven en `ui`, con `Cableado*`/`AccionesVentana` en el paquete raíz.

**Fase 4 · Cierre.** Deuda anotada resuelta o descartada con motivo, `README` técnico, jpackage desde Maven,
release 1.2. Resuelta o descartada casi toda la deuda de `DEUDA.md` (con motivo, fecha 2026-09-26); quedan
abiertas unas pocas filas de bajo riesgo (prioridad `baja`/`media`) y la fila 137 (partir `WatchlistView`/
`PartidasView`), aplazada a la 1.2.x por decisión de Jorge. El empaquetado con `jpackage` ya existe como perfil
Maven (`-Pempaquetar`, ver README_TECNICO.md); falta la release 1.2 en sí.

## Reglas de desarrollo

- **Hilo de la interfaz:** todo lo que toca Swing va en el EDT (`SwingUtilities.invokeLater` o `Tareas.enUi`); la red
  y el disco, nunca. Los servicios avisan en el log si se les llama desde el EDT (`util.Hilos.avisarSiUi`).
- **Cortesía con la API del companion:** freno global (1 llamada/s con ráfaga de 5), cortacircuitos ante 429 y
  «nocturno primero»: lo que den los resúmenes de sfr-data no se pide a la API. Estas reglas viven en un solo sitio
  (`api.Throttle`/`api.ApiClient`).
- **Capas:** `tools/capas.py` comprueba qué paquete puede importar a cuál (y que `ui` no importe `java.net`).
- **Harness de capturas:** `RegresionCapturas` compara 29 capturas con sus referencias. Si una cambia sin que el
  cambio fuera intencionado, es un bug: se investiga, no se regraba la referencia.
- **Commits:** cada commit compila y pasa `verificar.ps1 -Rapido` (sin pantalla); el harness completo
  (`verificar.ps1`) antes de integrar un grupo de cambios. Mensajes en español que empiezan por el paquete tocado.
- **Cambios delicados** (socket de vivos, freno, estado compartido): revisión antes del commit y test que falle sin
  el arreglo.

## Decisiones cerradas (no reabrir)
Civ Stats en una sola página; forma solo como dato de ELO (sin W-L); listas del perfil a 5; Live now sin scroll
horizontal; sin tarjetas flotantes; «nocturno primero» (sfr-data antes que API); perfil sin llamadas al abrir y
«Actualizar hoy» explícito; freno 1 req/s; cortacircuitos 429; aviso de Microsoft y créditos tal como están.

Decisiones de Jorge del 2026-09-26 (cierre de la deuda de fase 4):
1. Países y tops («★ Top país», «★ Top clan») en el idioma de la app, no siempre en español; con migración de
   `grupo_activo` y de las campanas guardadas para que sigan casando en el otro idioma.
2. El aviso de pausa por la API (429) muestra la cuenta atrás («Esperando a la API (N s): la API pide calma…»)
   en Buscar y Twitch.
3. El socket ya no se cree el aviso «partida quitada» (`matchRemoved`); solo cuenta «terminada» (`finished`),
   confirmada por la API.
4. «Enviar al juego» usa la rec ya descargada si parece sana (tamaño > 0 y abre como zip); si no, descarga.
5. Mover UN jugador de grupo conserva su vínculo de familia, igual que mover varios.
6. «Solo vivos» con una familia plegada la muestra si juega CUALQUIERA de sus miembros, no solo la cabeza.
7. El clic derecho en las listas del perfil siempre ofrece «Abrir perfil en pestaña nueva» en una fila de jugador.
8. «Añadir jugador» busca primero en local y luego en la API, con el mismo formato de fila que los demás buscadores.
9. Partir `WatchlistView`/`PartidasView` (fila 137 de DEUDA) se aplaza a la 1.2.x; la ventana (`SpoilerFreeRecs.java`)
   sí se parte a menos de 300 líneas ya en la fase 3/4.

## Deuda conocida al empezar
- ~~Mapas `vivoWatch`/`vivoInfo` escritos desde hilos de fondo y leídos en el EDT~~ (resuelto en la fase 2: service.EstadoVivo).
- Tres cachés distintas (perfilCardCache, ACTIVIDAD_CACHE, cachés en disco) con reglas distintas.
- `config.properties` como único almacén de estado; sin migraciones.
- `EtiquetaRecorte` y los anchos medidos de Live now: lógica de layout mezclada con datos.
