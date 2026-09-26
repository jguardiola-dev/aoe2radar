# aoe2radar — technical README

## What it is
A Windows desktop app for Age of Empires II DE: spoiler-free recs (recorded games), Live now, profiles,
ratings, civ stats and tech tree. Java 21 + Swing + FlatLaf. Data comes from the aoe2companion API
(REST + websocket) and from aoe2techtree; the `sfr-data` repo precomputes nightly summaries and profiles.

It was migrated from a single file (`SpoilerFreeRecs.java`, ~13,900 lines in 1.1) to packages that each have
their own responsibility; the migration is done, and `SpoilerFreeRecs.java` is now a window of about 300
lines that only does the wiring. The plan, with phases and closing criteria, lives in
[docs/ARQUITECTURA.md](ARQUITECTURA.md).

## Requirements
- JDK 21 (with `jpackage`; any full JDK 21 includes it, a JRE does not).
- Maven 3.9+ (check with `mvn -version`).
- Windows: the app and the screenshot harness are designed for Windows (Swing + registry paths).

## Build
```
mvn -q -DskipTests compile
```
Compiles without tests, as a quick check that everything fits together. The full jar:
```
mvn -q -DskipTests package
```

## Tests
Two ways to run the test suite, depending on how much time you have and whether you can give up the screen:

| Command | What it does | When to use it |
|---|---|---|
| `.\verificar.ps1 -Rapido` | Compiles, runs the layer rule (`tools\capas.py`) and all tests except the screenshot harness. No screen needed. | While iterating, before each small commit. |
| `.\verificar.ps1` | The same + the full harness (`RegresionCapturas`): opens the app and compares its 29 screenshots pixel by pixel. It beeps and shows a small window before starting and when it ends: don't touch the mouse on the main monitor while it runs (~1 min). | When closing a group of 2-3 commits, and always before moving to another package or phase. |

If the harness goes red, the screenshot is not adjusted: it is a bug and it gets investigated
(docs/ARQUITECTURA.md, "Development rules (Reglas de desarrollo)"). Before each screenshot,
`asegurarTamanoRaiz` measures the real size of the window (JRootPane) on this machine and uses it as the
reference (not a fixed constant): if it does not match (e.g. the Windows frame changes by a few pixels), it
resizes and retries once before really failing. If the first screenshot comes out black,
`AvisoHarness.logonUiActivo` checks whether the Windows lock screen (`LogonUI.exe`) is among the running
processes, only to make the error message clearer; it never decides on its own that the session is locked.

## Packaging (Maven profile `empaquetar`)
The normal build (`mvn test`, `mvn package`) does not produce the `.exe`: that lives in a separate profile
that is **not activated automatically**, so it does not affect or slow down day-to-day work.

```
mvn -Pempaquetar -DskipTests package
```

What it does, in order (everything inside `target/`, which is already in `.gitignore`, so `mvn clean` also
cleans the packaging):
1. Copies the runtime dependencies (FlatLaf) to `target/jpackage-input`.
2. Copies the app jar there, already built by `maven-jar-plugin`.
3. Generates `target/logo.ico` (multi-size) by calling `dev.tirador.aoe2radar.SpoilerFreeRecs --make-ico`,
   the same mechanism `crear_exe.bat` used in 1.1 (see the note in `pom.xml` itself: that script is not in
   the repo or in the Git history; its parameters are deduced from the code — name and version from
   `util.Identidad`, main class from the manifest, `--make-ico` documented in `AcercaDe.generarIco()`).
4. Calls the JDK's `jpackage` with `--type app-image`: a `target/dist/aoe2radar/` folder with
   `aoe2radar.exe`, its own runtime (implicit jlink, nothing to install separately) and the jars in `app/`.
   WiX Toolset is not needed because no installer is generated, only the app folder.

The version is set by hand in two places: `util.Identidad.VERSION` (what the app shows) and
`jpackage.appVersion` in the profile (the `.exe` version). Change both together.

## App, data and recs folders (`util.Sistema`)
**Packaged or not** is decided in one place, `Sistema.carpetaInstalacion`: the `jpackage.app-path` system
property (set by the jpackage launcher: the exe path) or the `app.dir` system property (set by the Conveyor
launcher: the install folder). Neither → not packaged (`mvn`, tests, capture harness).

- **App folder** (`carpetaApp()` / `enCarpetaApp`): the install folder. Read-only resources only: today the
  `banderas/` copy that the `empaquetar` profile leaves next to the exe (`ImagenesJuego.BANDERAS_DIR`). The
  tech tree and the flags inside the jar are read from the classpath.
- **Data folder** (`carpetaBase()` / `enCarpetaBase`): everything the app writes — `config.properties`,
  `players.txt`, `top_cache.txt`, `descargas.log`, `sfrdata/`, `techtree/` (the disk copy of the tech tree,
  filled from the jar and refreshed by ETag) and `arranque_error.log`.
- **Recs folder** (`carpetaRecs()`, used by `RecsDisco.RECS_DIR`).

| Situation | Data folder | Recs folder |
|---|---|---|
| Not packaged | `Path.of("")` (working directory, as always: the harness depends on it) | `recs` (relative) |
| Packaged, `portable` or `portable.txt` in the app folder | the app folder (as in 1.1–1.3) | `recs/` there |
| Packaged | `%APPDATA%\aoe2radar` (without `APPDATA`: `user.home\AppData\Roaming\aoe2radar`), created if missing | `Documents\aoe2radar\recs` |
| The data folder cannot be prepared | the app folder, and a line in the log | as above |

`config.properties` key `carpeta_recs`, if set, overrides the recs folder. Documents is the `Personal` value of
`HKCU\...\Explorer\User Shell Folders` (it follows a OneDrive redirection), read once with `reg.exe` (console
in UTF-8) and expanded; if missing or not a folder, `user.home\Documents`. Under Conveyor (MSIX), Windows
redirects `%APPDATA%` writes to the package's private copy transparently (removed on uninstall), and the portable
file cannot be created in the install folder.

The folder is resolved once, in a lazy holder (`Sistema.Datos`), the first time a data path is asked for (on
the main thread, in `Main.main`). `Sistema` cannot call `Log` or `Config` while resolving (their constants come
from it), so it keeps the message (`avisoCarpetaDatos()`) and `Log` writes it when it finishes initializing;
the recs folder, which does read `config.properties`, lives in a second holder.

### Importing data from a 1.x zip (`util.ImportacionDatos`, `ui.ImportarDatos`)
An installed app cannot know where the old zip was, so there is no automatic migration. Instead:

- `Sistema.datosNuevos()`: when packaged (not portable), the data folder had neither `config.properties` nor
  `players.txt` at startup (decided before anything writes `config.properties`).
- If so, and `importar_ofrecido` is not set, `AccionesVentana.arrancar` asks once, after the window is up:
  "Coming from aoe2radar 1.x (zip)?" with **Choose folder…** / **No, thanks**. Either answer is remembered.
  The same action is in Settings → **Import data from another version…** (only when packaged, so the
  harness menu capture does not change).
- The chosen folder must contain `config.properties` or `players.txt` and must not be (or contain, or be
  inside) the data folder. If the data folder already has user data, the app asks for confirmation first and
  says where the copy of the current data will go.
- **Step 1, prepare** (`ImportacionDatos.preparar`, background thread, the app keeps running): `sfrdata/`,
  `top_cache.txt`, `players.txt` and `config.properties` are copied to a `.importando` staging folder inside
  the data folder; `config.properties` is rewritten (absolute `carpeta_recs` inside the old folder → the new
  recs folder; a relative one is dropped; `importar_ofrecido=true`); the old recs are copied to the recs folder
  without overwriting, each through a `*.importando` temporary name (leftovers are swept at startup by
  `Archivos.limpiarTemporales`). Copies drop the read-only attribute. If this fails, the staging folder is
  deleted and the data folder has not changed (recs already copied stay, and the message says so). Not
  imported: `descargas.log`, `techtree/` (rebuilt from the jar; its ETag is forgotten when `data.json` comes
  from the jar), app resources.
- **Step 2, place** (`ImportacionDatos.colocar`), immediately followed by relaunch and exit, with nothing in
  between: inside `synchronized (Config.class)` (the monitor of `leerConfig`/`guardarConfig`) and with every
  `Archivos.escribirAtomico` paused (a read/write lock: in-flight writes finish, new ones fail with
  `IOException`; this covers the countries Timer, the top cache, sfr-data, `players.txt` and the config). Every
  file that will be replaced is first copied to `.antes_de_importar/<date>/` (always kept), then the staged
  files are moved in with `players.txt`, `config.properties` and the `importado_desde.txt` marker last. If a
  move fails, the replaced files are restored from that copy and the new ones removed (states `IMPORTADO`,
  `SIN_CAMBIOS`, `RESTAURADO`, `A_MEDIAS`; the message tells which).
- On success the writes stay paused, the result is written to `.aviso_importacion.txt` (shown by the new app at
  startup, including a warning if the imported config had autostart on), the app relaunches its own exe
  (`Sistema.relanzar`, from `ProcessHandle`) and exits with `System.exit`, skipping the normal close: the old
  in-memory player list and countries would otherwise be saved over the imported files.
- `reg.exe` (Documents lookup) runs with a 5 s limit and its output is read on another thread, so a hung
  process cannot block startup; an invalid `jpackage.app-path`/`app.dir` counts as "not packaged".

Autostart with Windows (`Sistema.fijarAutoArranque`, `HKCU\...\CurrentVersion\Run`) points at the exe
(`jpackage.app-path`), never at the data folder. Under Conveyor there is no `jpackage.app-path`, so the menu
item is disabled (an MSIX app needs a startup task declared in its package instead).

## Layered architecture
Summary table; the details of what each layer knows and the key contracts are in
[docs/ARQUITECTURA.md](ARQUITECTURA.md#layers-inside-out).

| Package | What lives there |
|---|---|
| `model` | Plain data (`Match`, `Player`, `Forma`…). No Swing, no network. |
| `util` | `Json`, `t()` (i18n), formats, `Log`, `Identidad`, `Config`, `Reloj`, `Archivos` (atomic write). |
| `api` | Companion client: `ApiClient`, `CompanionApi` (REST), `SocketVivo` (websocket), `Throttle`/`ThrottleCubo` (throttle + circuit breaker). Transport and parsing only. |
| `sfrdata` | `sfr-data` client (shards, elo, muestra, civstats, ladder), with a disk cache. |
| `techtree` | aoe2techtree data, with a disk cache and ETag. |
| `cache` | `CacheService`: in-memory and disk caches with TTL, one single implementation. |
| `service` | Business rules: `ProfileService`, `LiveService`, `FormService`, `RecService`, `RatingsService`, `ControlService`…, and the watchlist pieces: `ListaSeguidos`, `Familias`, `FiltroLista`, `BarridoVivos`, `TopLadderService`, `Campanas`, `AnotacionesService`. |
| `ui` | One view per tab (`…View`) and its presenter (`…Presenter`). Views do not call the network. Shared UI state: `AppState` (active view and navigation history, with listeners) and `Navegador` (tab chrome and back/forward; implements `Navegacion`). |
| `app` | `Main` (the real startup: `--make-ico`, catalogs, language, theme, window on the EDT) and `Servicios` (composition root: creates and connects, in a fixed order, the services that talk to the companion). |
| (root package) | `SpoilerFreeRecs` is the window (`JFrame`): it declares the fields the views share and calls the wiring in order from the constructor; today under 300 lines. The `Cableado*`/`AccionesVentana` classes (same package, so they can read those fields without making them `public`) do the composition (which view with which service) and the "open something" actions (URL, Twitch, spectate, CaptureAge). |

Dependency rule: each layer only knows the ones further in (`ui` knows `service` and `model`, never the
other way round). It is checked on every `verificar.ps1` run by `tools/capas.py`, which fails if an arrow
points outwards.

## How to add a new view (view + presenter pattern)
Each tab leaves `SpoilerFreeRecs.java` whole and becomes two classes in `ui`, with four contract pieces
between them (small interfaces, one per responsibility):

- **Presenter** (`…Presenter`): the tab's logic, without Swing. It receives the view's events
  (`onBuscar`, `onAbrirPerfil`…), calls the services and decides what to paint.
- **Screen** (interface `Presenter.Pantalla`, implemented by the view): what the presenter needs from the
  view — paint a result, show a status message, give the current state of a component. The view never
  decides logic, it only paints what it is asked to.
- **Host** (interface `View.Anfitrion`, implemented by the main window): what the view needs from outside
  itself (for example, the selection in another tab). This way the view knows neither the window nor the
  other views.
- **Navigation** (`ui.Navegacion`): how a view asks to open ANOTHER view ("open this player's profile")
  without knowing it directly.
- **Tasks** (`ui.Tareas`): the app's two threading rules, written down in one place. The presenter calls
  `tareas.enFondo(nombre, trabajo)` for slow work (network, disk) and `tareas.enUi(trabajo)` to get back to
  the EDT before touching Swing. Tests use `Tareas.EN_LINEA`, which runs everything immediately, with no
  threads.

Real example: `ui/RatingsPresenter.java` + `ui/RatingsView.java` (the Ratings tab).

```java
// RatingsPresenter.java — Pantalla is what the presenter asks of the view
public interface Pantalla {
    List<Comparado> comparados();
    void refrescarComparados();
    void estado(String texto);
    void cargaTerminada(String error);
    // …
}

public void cargar() {
    if (servicio.cargando()) return;
    servicio.cargando(true);
    pantalla.cargaIniciada();
    tareas.enFondo("ladder-datos", () -> {           // slow work, off the EDT
        String err = servicio.asegurar(false);
        tareas.enUi(() -> {                           // back on the EDT to paint
            pantalla.pararProgreso();
            servicio.cargando(false);
            pantalla.cargaTerminada(err);
        });
    });
}
```

```java
// RatingsView.java — implements Pantalla and asks the window for what it needs via Anfitrion
public final class RatingsView implements RatingsPresenter.Pantalla {
    public interface Anfitrion {
        List<Player> seleccion();
        boolean seleccionado(long pid);
        String nombreVisible(long pid, String nombre);
        // …
    }
    // the Pantalla methods paint Swing; they never call a service or decide logic
}
```

Steps for a new view:
1. Create `NuevaView` and `NuevaPresenter` in `ui`, with the `Pantalla` interface in the presenter and, if
   the view needs something from the window, `Anfitrion` in the view.
2. Move that tab's code from `SpoilerFreeRecs.java` as is (without changing logic) and delete it from the
   original: the tab leaves whole, it is never left half in each place.
3. The presenter only calls services (`service`) and models (`model`); never `javax.swing.*` or
   `java.net.*`. If it needs threads, it asks `Tareas` for them.
4. Characterization test of the presenter with `Tareas.EN_LINEA` and a test `Pantalla`/`Anfitrion`
   (test double), without starting Swing or the network.
5. `.\verificar.ps1 -Rapido` before each commit; the full `.\verificar.ps1` when closing the group.
