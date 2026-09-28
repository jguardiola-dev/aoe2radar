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

## Continuous integration (GitHub Actions)
`.github/workflows/build.yml` runs on every push and pull request to `main` and to the `fase-*` branches, on a
`windows-latest` runner: Java 21 (Temurin) with the Maven cache, `python tools/capas.py`, and
`mvn -B test "-Dtest=!RegresionCapturas"`. It is the same check as `.\verificar.ps1 -Rapido`.
- The screenshot harness never runs in CI: it takes the screen, and its reference images depend on the
  resolution, scaling and fonts of the development PC. It only runs with `-Dharness=si` (what the full
  `verificar.ps1` passes), and CI excludes it by name as well.
- Windows runner on purpose: several tests assume Windows paths (`SistemaTest`, `EspectarTest`,
  `AvisoHarnessTest`, the invalid-path case of "Enviar al juego").
- If a run fails, the Surefire reports are attached to it as the `surefire-reports` artifact.
- The result is on the repo's **Actions** tab and next to each commit/PR. A green CI does not replace the full
  harness before closing a group of commits: that one still runs on the development PC.

### Publishing a version (`release.yml`)
`.github/workflows/release.yml` builds the Windows package on GitHub, so a release does not depend on the
development PC. On `windows-latest`, with the JDK pinned to a fixed Temurin version (`21.0.9`, see below), it
installs Inno Setup 6 if the runner image lacks it (`choco install innosetup`), runs
`mvn -B -Pempaquetar,instalador -DskipTests verify` and attaches four files to the tag's release:

| File | What for |
|---|---|
| `aoe2radar-X.Y-setup.exe` | The per-user installer (Inno Setup). What the README tells users to download. |
| `aoe2radar-X.Y-windows.zip` | `target/dist/aoe2radar` zipped, same name and layout as the 1.2 and 1.3 zips (an `aoe2radar/` folder inside). |
| `aoe2radar-X.Y.jar` | Exactly the app jar inside the package, renamed: what the self-updater downloads. |
| `update.json` | What the self-updater reads (see [Self-updater](#self-updater-utilinstalacion-serviceactualizadorservice)). |

`update.json` is written by the workflow: `version` (`version.app`), `jar`, `sha256` and `size` of that jar
(computed in CI), `runtime` (`JAVA_VERSION` of the packaged `runtime/release`), `instalador` (the setup file
name), and, read from `app/aoe2radar.cfg` with the same rules as `util.CfgLanzador.leer`: `classpath` (the other
jars), `opciones` (`java-options` except `-Djpackage.app-version`) and `mainclass`. It is uploaded last, so an
app that reads it already finds the jar it names.

**The JDK version is pinned on purpose.** The runtime inside the package comes from it, and the self-updater
compares it with each installation's `java.version`: a release built with a different JDK is offered as "needs
reinstalling" (installer) instead of a jar swap. Changing the pinned version (for a Java security update, say)
is fine and is exactly how the runtime gets renewed; just expect that release to go out as a full update.

Steps to publish X.Y:
1. Change `<version>` in `pom.xml` (e.g. `1.4.0`), merge to `main`, and wait for the `build` workflow to be green.
2. On GitHub: **Releases → Draft a new release**, tag `vX.Y` (e.g. `v1.4`) on `main`, write the notes,
   **Publish**. The `release` workflow starts on its own and, a few minutes later, the four files appear in
   the release.
3. To rebuild the files of an existing tag (or if the automatic run failed): **Actions → release → Run
   workflow**, with the tag. It replaces them (`--clobber`): a file with the same name uploaded by hand to that
   release is overwritten without asking. If the release does not exist yet, the workflow creates it as a
   draft, to be completed and published by hand.

Safety checks: the workflow fails before uploading anything if the tag does not match the pom version
(`v` + `version.app`, e.g. `v1.4` for `1.4.0`), if `version.app` is not plain digits and dots (the self-updater
would reject it), or if the installer was not produced. While the files are being built (several minutes) the
published release has none yet: `releases/latest/download/update.json` answers 404 and installed apps simply
try again later; the 1.3 zip's tag-based checker may already show the new version. Re-running the workflow
on a published release rebuilds the jar (its SHA-256 may change) and uploads it before `update.json`: for a few
seconds a client may get the new jar with the old hash, reject it (download failed) and retry later.

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
   WiX Toolset is not needed: jpackage only makes the app folder; the installer is Inno Setup's job (below).

### Installer (Maven profile `instalador`, `packaging/aoe2radar.iss`)
```
mvn -Pempaquetar,instalador -DskipTests verify
```
Always together with `empaquetar`, and bound to `verify` so it runs after everything `empaquetar` does in
`package` (jpackage and the flags copy). It calls Inno Setup 6's `ISCC.exe` (default: a per-user install,
`%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe`; elsewhere, `-Discc="C:\...\ISCC.exe"`) on
`packaging/aoe2radar.iss`, passing the version (`version.app`), the app-image folder and the icon, and leaves
`target/instalador/aoe2radar-X.Y-setup.exe`. ISCC's output lists every file it packs ("Compressing: …"); the
count must match the files in `target/dist/aoe2radar`.

What the installer does:
- Per user, no administrator rights (`PrivilegesRequired=lowest`): installs into
  `%LOCALAPPDATA%\Programs\aoe2radar` (`{userpf}`), Start menu entry for the user, optional desktop shortcut
  (unchecked box). Wizard in English or Spanish, following the Windows UI language (English otherwise).
- **Fixed `AppId`**: installing a newer version over an older one replaces it in place. Never change it.
- If the app is open, the wizard closes it first (Restart Manager, `CloseApplications`, also watching `*.jar`).
  Not verified yet whether Java takes that as its normal close (`windowClosing`, which may apply a pending
  self-update just before the installer overwrites `app\`) or as a shutdown without it (nothing applied, window
  geometry not saved). Either way the result is the installer's: its `[InstallDelete]` removes every
  `app\aoe2radar-*.jar` so only the new `.cfg`'s jar remains, and the app's startup discards a stale update
  marker. (Check once: install over an open app and look at `descargas.log`.)
- The uninstaller removes the program (including jars the self-updater added, and the autostart registry
  value) but **not** the data folder or the recs, and says so in a final message with both paths.
- Needs Inno Setup **6.3 or later** (`ArchitecturesAllowed=x64compatible`); the CI installs the latest.
- The `.iss` file is UTF-8 **with BOM** (without it ISCC reads it as ANSI); in its `[Code]` section no line may
  start with `[`.

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
- Outside Maven (e.g. an IDE that compiles on its own without processing resources, or plain `javac`), the
  filtered resource does not exist and the version comes out as `0.0` (title `aoe2radar 0.0 — …`, and the
  update checker sees any tag as newer). Build through Maven (`mvn compile`/`test`/`package`, or the IDE
  delegating to Maven) to get the real version.
- `util/IdentidadTest` checks, on every `mvn test`, that the resource arrived filtered and matches the
  `<version>` in `pom.xml`, and that the `<regex>` and `<replacement>` in the pom are literally
  `Identidad.REGLA_CORTA` and `Identidad.REEMPLAZO_CORTA`, applying both to several versions (`1.3.0`,
  `1.3.1`, `1.4.0-SNAPSHOT`, `2.0`…). If you change the rule, change both places or the test goes red.

## App, data and recs folders (`util.Sistema`)
**Packaged or not** is decided in one place, `Sistema.carpetaInstalacion`: the `jpackage.app-path` system
property, set by the jpackage launcher (the exe path). Without it → not packaged (`mvn`, tests, capture
harness).

- **App folder** (`carpetaApp()` / `enCarpetaApp`): the install folder. Read-only resources only (the one
  exception is the self-updater, which adds jars to `app/` and rewrites its `.cfg`): today the
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
in UTF-8) and expanded; if missing or not a folder, `user.home\Documents`.

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
  process cannot block startup; an invalid `jpackage.app-path` counts as "not packaged".

Autostart with Windows (`Sistema.fijarAutoArranque`, `HKCU\...\CurrentVersion\Run`) points at the exe
(`jpackage.app-path`), never at the data folder. Without `jpackage.app-path` (not packaged) the menu item is
disabled.

## Self-updater (`util.Instalacion`, `service.ActualizadorService`)
Only the app jar changes; the runtime and the rest of `app/aoe2radar.cfg` change rarely and go through the
installer. Pieces, from the inside out:

| Piece | Package | What it does |
|---|---|---|
| `CfgLanzador` | util | Reads and rewrites jpackage's `.cfg` (real format: `[Application]` with repeated `app.classpath=$APPDIR\…`, `app.mainclass`, `[JavaOptions]`). Only the app jar line and `-Djpackage.app-version` change; everything else stays byte for byte (line endings included). Atomic write **without** fallback: temp file in the same folder, `force` to disk, `ATOMIC_MOVE`; if the move fails, the old file is intact (unlike `Archivos.escribirAtomico`, which writes in place as a fallback). |
| `VerificacionJar` | util | Exact size, SHA-256, a readable zip, `app/Main.class` and the `.cfg`'s main class inside. |
| `Instalacion` | util | Disk side, no network: detection, the pending download (`lista.properties`), apply on close, startup marker, revert, confirm, sweep of old jars. Everything that changes files takes an inter-process lock (`actualizacion/.cerrojo`, `FileChannel.tryLock`, up to 5 s; otherwise it is left for next time). |
| `ActualizadorService` (`Actualizador`) | service | `update.json`, the jar-or-installer decision, the download. |
| `Actualizaciones` + `FranjaActualizacion` | ui | Presenter and the thin bar (same style as `FranjaAviso`); no dialogs. |
| `app.Main`, `CableadoCromo`, `AccionesVentana` | app/root | Startup marker and recovery; the bar, the close hook, the 12 h timer. |

**When it is active.** Only when the app runs packaged, from a jar inside `app/` that the `.cfg` names (exactly one
`aoe2radar-*.jar` there), and `app/` is writable (tested by creating and deleting a file:
`Files.isWritable` lies on Windows). That is the installer's folder, or a zip unpacked where the user can write.
Otherwise (zip in a read-only folder, development, harness) Settings keeps the old check against the GitHub
tags `vX.Y` (`ControlService.ultimaVersion`), which the 1.3 zip also uses.

**Check.** 8 s after startup and every 12 h, off the EDT: `releases/latest/download/update.json` (GitHub's
download CDN, no API rate limit; the HTTP client follows the redirect). Required: `version` (1–4 numeric parts,
no suffixes), `jar` (exactly `aoe2radar-<version>.jar`: no paths), `sha256`, `size`; otherwise the file is
ignored. Not newer than `Identidad.VERSION` → nothing. **Full update** (installer) if the version already failed
as a jar, or if `runtime`, `classpath`, `opciones` or `mainclass` are missing or differ from this installation's
`java.version` and `.cfg`. Otherwise **jar update**.

**Download.** With Settings → **Update automatically** on (default), right away; off, only when the user clicks
**Update** in the bar. One at a time (`AtomicBoolean`). From the tag's URL (`releases/download/vX.Y/…`, not
`latest`), with 15 s to connect and 30 s max between bytes (`HttpURLConnection`: `HttpClient` only times out up
to the headers). Into `<data>/actualizacion/<jar>.descargando` with the SHA-256 computed on the fly, cut off as
soon as it exceeds the announced size; then size, SHA-256 and contents are checked, and only then, under the
lock, the file is forced to disk, moved (`ATOMIC_MOVE`) to its real name and recorded in `lista.properties`
together with the package it is meant for (runtime, classpath, options, main class). A failed download leaves
nothing under the real name and is retried on the next check.

**Apply (on close).** Last step of the window's normal close (`alCerrar`, after saving everything), in a thread
the close waits for up to 20 s. Nothing if there is no pending jar, it is not newer than this version or than the
jar the `.cfg` already names (another window may have updated further), it is the failed version, or the
runtime/`.cfg` are no longer the ones it was published for (an installer ran: the pending jar is forgotten). Then:
the jar is re-verified; copied to `app/<jar>` through a `.parcial` temp file (with today's date — `Files.copy`
keeps the download's date on Windows, which would make the sweep think it is old —, forced to disk, verified,
`ATOMIC_MOVE`); a copy of the current `.cfg` (`cfg.anterior`) and the **marker** (`aplicada.properties`:
previous jar, new jar, versions) are written; the `.cfg` is read again and must be byte-identical to the one read
at the start (else someone, e.g. the installer, changed it: nothing is written); and the new `.cfg` is written
atomically. The jar in use is never touched (Windows locks it). If anything fails, the `.cfg` stays as it was and
the app stays on its version; it is logged and retried on the next close.

**Restart now** sets a flag and dispatches `WINDOW_CLOSING`, so the app leaves through the normal close
(`alCerrar` → apply → `Sistema.relanzar()` → `EXIT_ON_CLOSE`), never a bare `System.exit`.

**Startup and recovery.** First thing in `Main.main` (`Instalacion.alArrancar`): if there is a marker and the jar
in use is the marker's new jar, this is a start after updating: its pid is added to the marker. If the marker
names another jar (the installer or another window changed things), it is discarded. If **two earlier starts**
of the new jar died without opening the window (their pids are no longer alive: a double click on a slow first
start does not count), the `.cfg` is restored from `cfg.anterior` and the app relaunches itself with the
previous version. And if `Main` catches a startup exception while the marker is on (the whole startup is now
inside the try), it reverts right there, writes `arranque_error.log`, says so in the error dialog and relaunches.
Revert only happens if the previous jar is still in `app/`, the copy names it and the current `.cfg` still names
the new jar; it records the version as failed (`fallida.properties`: it will be offered as a full update) and the
old version shows once in the bar "The update to X could not start: you are still on Y". When the window has
opened, `Instalacion.confirmar` (background thread) deletes the marker and the copy, and sweeps `app/`: every
`aoe2radar-*.jar` that is not the one running, not the `.cfg`'s, not the marker's, and older than 10 minutes
(one still locked by another open window stays for the next start); plus leftovers in `actualizacion/`.

**Limits of the recovery.** The code that counts starts and reverts is the **new** jar's. If that jar cannot even
reach `Main` (the launcher cannot load it, the JVM crashes first), nothing reverts automatically: the fix is to
run the installer (data is untouched). That is why the jar is verified completely before it reaches `app/`, and
only applied onto the exact runtime and `.cfg` it was built for. A hang without an exception is not detected in
that start; it counts as a failed start in the next one. A failure after the window has opened (the update is
already confirmed) does not revert.

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
