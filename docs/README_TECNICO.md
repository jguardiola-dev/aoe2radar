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
   the repo or in the Git history; its parameters are deduced from the code — name from `util.Identidad`,
   main class from the manifest, `--make-ico` documented in `AcercaDe.generarIco()`).
4. Calls the JDK's `jpackage` with `--type app-image`: a `target/dist/aoe2radar/` folder with
   `aoe2radar.exe`, its own runtime (implicit jlink, nothing to install separately) and the jars in `app/`.
   WiX Toolset is not needed because no installer is generated, only the app folder.

## Version: one single place
The version lives only in `<version>` in `pom.xml` (today `1.3.0`). To release a new one, change that line
and nothing else. From it:
- `build-helper-maven-plugin` (`regex-property`, phase `initialize`) computes the property `version.app`:
  the `-qualifier` suffix and a third `.0` part are dropped (`1.3.0` → `1.3`, `1.3.1` → `1.3.1`,
  `1.4.0-SNAPSHOT` → `1.4`). That is the version the app shows (window title, About) and compares with the
  GitHub tags `vX.Y` when checking for updates, and also jpackage's `--app-version`.
- `src/main/resources-filtradas/dev/tirador/aoe2radar/util/version.properties` is the only filtered resource
  (`src/main/resources` is not filtered: flags and tech-tree data must go byte for byte). Maven writes
  `version.pom` and `version.app` into it, and `util.Identidad` reads it when the class loads:
  `VERSION_POM` (`1.3.0`) and `VERSION` (`1.3`, via `Identidad.versionCorta`, the same rule as the pom).
- If the class was compiled outside Maven (no filtered resource), `VERSION` is `0.0`: harmless, the update
  checker just sees any tag as newer.
- `util/IdentidadTest` checks, on every `mvn test`, that the resource arrived filtered, that it matches the
  `<version>` in `pom.xml`, and that the Java rule and the pom rule give the same result.

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
