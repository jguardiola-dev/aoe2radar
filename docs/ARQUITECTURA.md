# Target architecture and migration plan

## Why migrate
A 13,900-line file works, but it cannot be maintained: any change means reading all of it, there are no
tests, and the data logic (throttle, caches, socket) is mixed with the Swing painting. The goal is not
"pretty code": it is being able to change one thing without breaking another, to test it without opening the
app, and for another person (or an agent) to understand the project in an afternoon.

## Layers (inside out)
```
model     Plain data: Match, MatchPlayer, Player, Actividad, Forma, LadderRow… No Swing, no network.
api       Companion client: ApiClient (the only text path to the network) + CompanionApi (REST endpoints),
          SocketVivo (websocket), Throttle/ThrottleCubo (throttle + circuit breaker). Transport + parsing only.
sfrdata   sfr-data client: SfrDataClient (index/shards/elo_ayer/muestra/civstats/ladder), with a disk cache.
techtree  aoe2techtree data (data.json, per-civ trees, per-language strings), with a disk cache and ETag.
          Same layer as sfrdata: an external source of precomputed data. The icons (ImageIcon) belong to ui.
cache     CacheService: in-memory and disk caches with TTL, one single implementation for all of them.
service   Business rules: ProfileService (profile = shard + "Actualizar hoy"), LiveService (Live now: sweep +
          socket + ghosts), EnlaceVivo (protocol of the live-matches socket on top of api.SocketVivo: connection
          health —revisarSalud closes and reconnects after 10 min with no message and no pong— and the
          "ghost" rule: since Jorge's decision of 2026-09-26, a matchRemoved from the companion NO longer
          removes anyone; only a check confirmed by the API does, finished, with retries for up to 3 h),
          FormService (form by subtraction + fallback), RecService (downloads/send to game, with
          RecService.recSana deciding whether the copy on disk is good or must be downloaded again),
          the watchlist (groups, tops, clans; there is no WatchlistService class: it is split into ListaSeguidos,
          Familias, FiltroLista, BarridoVivos, TopLadderService, Campanas, AnotacionesService + api.SteamApi),
          StatsService (civ stats), ControlService (control.json).
ui        Swing: one view per tab (WatchlistView, PartidasView, DirectosView, LiveNowView, PerfilView,
          RatingsView, CivStatsView, TechTreeView) and its presenter (…Presenter). Views do not call the network: they ask
          the presenter and paint what the presenter gives them.
app       Main (startup, theme, wiring) and Servicios (the composition root: creates and connects, in a fixed
          order, every service that talks to the companion). The shared UI state lives in ui, in small
          pieces: ui.AppState (the active view and the navigation history, with listeners), ui.Navegador
          (the shared tab chrome and back/forward buttons; it implements ui.Navegacion) and ui.FiltroStats.
          It lives in ui so that views can use it without depending on app (phase 3 decision: if it lived
          in app, ui would import outwards).
(root)    SpoilerFreeRecs (the JFrame: declares the fields the views share and calls the wiring in order
          from the constructor) and the Cableado*/AccionesVentana classes, which do that wiring and the
          "open something" actions; they live in the root package (not in app or ui) so they can read the
          window's fields without making them public.
util      Json, t() (i18n), formats, Log, Reloj (injectable clock), Archivos (atomic write with fallback: writes
          to a temp file and moves it with ATOMIC_MOVE; if the move fails —e.g. Windows with the target file
          open— it writes directly, as in 1.1, instead of losing the data).
```
Dependency rule: each layer only knows the inner ones. `ui` knows `service` and `model`; `service` knows
`api`, `sfrdata`, `techtree`, `cache`, `model`; `api`, `sfrdata` and `techtree` know `cache` (they store what
they download in it), `model` and `util`, and `sfrdata`/`techtree` also know `api` (HTTP transport and
throttle); `cache` knows `model` and `util`; `util` can be used by anyone and knows no one (except `model` if
needed); `model` knows no one. If an arrow points outwards, it is wrong. Also, no `ui` class imports
`java.net` (the network always goes through `service`/`api`). `tools/capas.py` checks both things from the
imports (zero hand-written exceptions) and `verificar.ps1` runs it; the root package (`SpoilerFreeRecs`/
`Cableado*`/`AccionesVentana`) is not checked by the script, because it is the composition of the window,
not a layer.

## Key contracts
Signatures as they are in the code today (simplified: `throws` clauses left out).
- `api.ApiClient` (class): `String texto(String url)` and `String textoCon429(String url)`: the only text path
  to the network. Every call goes through `Throttle`; a 429 is reported through a callback, never through Swing.
- `api.CompanionApi` (class, on top of `ApiClient`): `partidas(long pid, int pagina, int porPagina)`,
  `Perfil perfil(long pid)`, `Clasificacion clasificacion(String id, int pagina, int porPagina, String pais)`,
  `List<PerfilEncontrado> buscarPerfiles(String q)`, `twitchDirectos()`…
- `sfrdata.SfrDataClient` (class): three ways to download, by file: `byte[] datos(nombre)` (the «data» branch,
  with ETag), `byte[] diario(nombre, timeoutS[, caducidad])` (the «perfiles» release) and
  `byte[] versionado(base, nombre, version, timeoutS)` (profile packages and deltas). Parsing is done by
  `Ladder`, `Snapshots`, `CivStats`, `PerfilesSfr`…
- `CacheService(Reloj)`: a single rule (`fresh = age < expiry`, values in `Caducidad`) and two pieces:
  `CacheMemoria<K,V>` per key, with `vigente(k)` (must it be requested again?) and `ultimo(k)` (what do I paint
  meanwhile?), and `Sello` for a single value (`fresco()`/`marcar()`); plus `archivoFresco(Path, caducidad)` for
  disk. The caller does the loading (no `get(key, ttl, load)`): each call site has its own policy on failure.
  Not caches: the activity of the open profile, linked players and families (session state:
  ProfileService/AppState).
- `api.Throttle` (interface; implementation `api.ThrottleCubo`): `void adquirir(cancelar)` (token bucket: burst of 5, then 1/s; Stop cuts the wait) and
  `long registrar429()` (global pause 60→120→240→300 s per episode; it is forgotten after 10 min with no 429
  since the last pause ended).
- Presenters: they receive events from the view (`onBuscar(nick)`, `onAbrirPerfil(pid)`), call services on a
  worker thread (`Tareas.enFondo`) and, back on the EDT (`Tareas.enUi`), tell the view what to paint through
  its `Pantalla` interface (there are no `…ViewModel` classes).

## Phases with acceptance criteria
**Phase 0 · Safety net.** Git repo, Maven, `SpoilerFreeRecs.java` inside `src/main/java`, screenshot harness
as a test (`RegresionCapturas`: the 23 screenshots of that time, 29 today; compared pixel by pixel with a tolerance).
Done when `mvn test` reproduces the screenshots in green.

**Phase 1 · Mechanical split.** Extract classes and methods into the packages without changing a single line
of logic. Order: model → util → api → sfrdata → cache (and Live/socket state). Done when all the static code
that is not UI has left `SpoilerFreeRecs.java` and the harness is still green. The UI (inner panels, the
code of each tab) is NOT split here: it is split in phase 3, once there are services with tests
(decision of 2026-09-24: splitting it now created files that still depended on the whole window, and
phase 3 would have redone them).
**Closed on 2026-09-24** (branch `fase-1-particion`): 51 files in model, util, api, cache, sfrdata, techtree,
service and ui; what remains outside is listed in `DEUDA.md` ("cierre fase 1").

**Phase 2 · Services with contracts.** Interfaces + implementations + unit tests with test doubles (no
network). This is where the three caches and the three places that today decide "do I call the API?" are
unified. Done when every service has tests and no `httpText` lives outside `api`.

**Phase 3 · Views and presenters.** Tab by tab, as a "strangler": each tab leaves `SpoilerFreeRecs.java`
WHOLE for its view + presenter (Presenter/Screen/Host/Tasks pattern, detailed in
[docs/README_TECNICO.md](README_TECNICO.md#how-to-add-a-new-view-view--presenter-pattern)) and its code is
deleted from the original. Done when no `ui` class imports `java.net` or knows `ApiClient`, and
`SpoilerFreeRecs.java` is left as the window (`JFrame`) in fewer than 300 lines, with the wiring in
`Cableado*`/`AccionesVentana` and startup in `app.Main`/`app.Servicios`.
**Closed** (batch 4, wave B + final pass, and its debt closure in phase 4): `SpoilerFreeRecs.java` at 299
lines; all views live in `ui`, with `Cableado*`/`AccionesVentana` in the root package.

**Phase 4 · Wrap-up.** Recorded debt resolved or discarded with a reason, technical `README`, jpackage from
Maven, release 1.2. Almost all the debt in `DEUDA.md` was resolved or discarded (with a reason, dated
2026-09-26). Packaging with `jpackage` is a Maven profile (`-Pempaquetar`, see README_TECNICO.md).
**Closed:** 1.2 is released (tag `v1.2`, GitHub release).

**Version 1.3.** `WatchlistView` and `PartidasView` split into a facade plus focused pieces (DEUDA row 137); a
full functional review of the app with its fixes (startup, persistence, live data and socket, Watchlist, Partidas,
Profile/Civ Stats/Tech tree); «Open in» startup option; per-map matchups matrix (needs sfr-data 1.5.4); fewer live
API calls (URL cache, Live now releases its socket ids, lighter Twitch, per-endpoint call counter); the
`FuentePartidas`/`FuenteLadder` seam for a future fallback source; user README and public docs in English. What is
left, and the plan for 1.4, is in `docs/DEUDA.md`.

## Development rules (Reglas de desarrollo)

- **UI thread:** everything that touches Swing runs on the EDT (`SwingUtilities.invokeLater` or `Tareas.enUi`);
  network and disk access, never. Services log a warning if they are called from the EDT
  (`util.Hilos.avisarSiUi`).
- **Courtesy with the companion API:** global throttle (1 call/s with a burst of 5), circuit breaker on 429,
  and "nightly first": whatever the sfr-data summaries provide is not requested from the API. These rules
  live in one single place (`api.Throttle`/`api.ApiClient`).
- **Layers:** `tools/capas.py` checks which package may import which (and that `ui` does not import `java.net`).
- **Screenshot harness:** `RegresionCapturas` compares 29 screenshots with their references. If one changes
  and the change was not intended, it is a bug: it gets investigated, the reference is not re-recorded.
- **Commits:** every commit compiles and passes `verificar.ps1 -Rapido` (no screen needed); the full harness
  (`verificar.ps1`) runs before merging a group of changes. Commit messages are in Spanish and start with the
  package they touch.
- **Delicate changes** (live-matches socket, throttle, shared state): review before the commit, and a test
  that fails without the fix.

## Closed decisions (Decisiones cerradas): do not reopen
Civ Stats on a single page; form only as ELO data (no W-L); profile lists capped at 5; Live now without
horizontal scroll; no floating cards; "nightly first" (sfr-data before the API); profile with no calls on
opening and an explicit "Actualizar hoy"; throttle at 1 req/s; circuit breaker on 429; Microsoft notice and
credits as they are.

Jorge's decisions of 2026-09-26 (closing the phase 4 debt):
1. Countries and tops («★ Top país», «★ Top clan») in the app's language, not always in Spanish; with a
   migration of `grupo_activo` and of the saved alerts (`Campanas`) so they still match in the other language.
2. The API pause notice (429) shows the countdown («Esperando a la API (N s): la API pide calma…») in
   Search and Twitch.
3. The socket no longer trusts the "match removed" notice (`matchRemoved`); only "finished" (`finished`)
   counts, confirmed by the API.
4. «Enviar al juego» uses the already downloaded rec if it looks sound (size > 0 and it opens as a zip);
   otherwise, it downloads it.
5. Moving ONE player to another group keeps their family link, the same as moving several.
6. «Solo vivos» with a collapsed family shows it if ANY of its members is playing, not only the head.
7. Right-clicking a player row in the profile lists always offers «Abrir perfil en pestaña nueva».
8. «Añadir jugador» searches locally first and then the API, with the same row format as the other searches.
9. Splitting `WatchlistView`/`PartidasView` (DEUDA row 137) is postponed to after 1.2 (now part of 1.3); the window
   (`SpoilerFreeRecs.java`) is split to under 300 lines already in phase 3/4.

## Known debt at the start
- ~~`vivoWatch`/`vivoInfo` maps written from background threads and read on the EDT~~ (resolved in phase 2:
  service.EstadoVivo).
- Three different caches (perfilCardCache, ACTIVIDAD_CACHE, disk caches) with different rules.
- `config.properties` as the only state store; no migrations.
- `EtiquetaRecorte` and the measured widths of Live now: layout logic mixed with data.
