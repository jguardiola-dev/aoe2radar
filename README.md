# aoe2radar

Your radar for competitive Age of Empires II, without spoilers.

🇪🇸 Español: [ver más abajo](#español)

---

## What it is

aoe2radar is a Windows desktop app for Age of Empires II: Definitive Edition.
It lets you follow players and their matches without learning the result before you want to.

It includes:
- Spoiler-free recs (recorded games): download them without seeing the result first.
- Live now: who is playing at this moment.
- Player profiles: activity, recent form and match history.
- Ratings: compare the ELO of several players.
- Civ Stats: win rate and play rate per civilization.
- Tech tree: the technology tree of each civilization.

Data comes from [aoe2companion](https://www.aoe2companion.com) (API and live matches) and
[aoe2techtree](https://www.aoe2techtree.net). Every night, the `sfr-data` repository builds summaries and
precomputed profiles, so the app does not depend only on the API when you open it.

## Screenshots

| Watchlist and matches | Live now |
|---|---|
| ![Watchlist and matches](src/test/resources/capturas/shot_partidas.png) | ![Live now](src/test/resources/capturas/shot_ahora.png) |

| Player profile | Civ Stats |
|---|---|
| ![Player profile](src/test/resources/capturas/shot_perfil_pestanas.png) | ![Civ Stats](src/test/resources/capturas/shot_civstats_matriz.png) |

## Installation

1. Go to [Releases](https://github.com/jguardiola-dev/aoe2radar/releases/latest) and download
   `aoe2radar-X.Y-setup.exe` of the latest version.
2. Run it. It installs for your Windows user only, **without administrator rights**, in
   `%LOCALAPPDATA%\Programs\aoe2radar`, with an entry in the Start menu (and a desktop shortcut if you tick the
   box). The installer speaks English or Spanish, following Windows.
3. Windows SmartScreen may warn that the program is not signed ("Windows protected your PC"). This is expected:
   the installer has no code-signing certificate yet. Click **More info** → **Run anyway**.
4. To uninstall: Windows Settings → Apps → aoe2radar → Uninstall. The program is removed, but **your data is
   not** (the uninstaller tells you where it is, in case you want to delete it by hand).

Running a newer installer over an installed version updates it: it closes the app if it is open, replaces the
program and keeps your data.

### Automatic updates

The installed app looks for a new version a few seconds after it starts and every 12 hours, and downloads it in
the background. When it is ready, a thin bar at the top of the window says *"aoe2radar X.Y is ready: it will be
applied when you close the app"*, with a **Restart now** button; nothing else interrupts you. The update is
applied when you close the app and used the next time you open it. If that first start fails, the app goes back
to the previous version by itself and tells you.

Some versions need more than the program itself (a new bundled Java, for instance). Then the bar says *"There
is a new version that needs reinstalling"* and **Download installer** opens the release page: download and run
the new installer.

To decide yourself, turn off **Configuración** (Settings) → **Update automatically**: the bar then only tells
you that a version is available, with an **Update** button.

### Coming from a 1.x zip

Install with the installer as above. The first time it starts, the app looks for old copies of aoe2radar 1.x
(or SpoilerFreeRecs) on your Desktop, in Downloads and in Documents, up to four folders deep. If it finds any, it
proposes the newest version ("Found 28 copies of aoe2radar 1.x. The newest is 1.3 in …") with **Import**,
**Choose another folder…** and **No, thanks**. If it finds none, click **Choose folder…** and pick the old
folder: the one you unzipped is enough, the app finds the data inside it. Your groups, settings, caches and
recs are copied (the old folder is left untouched, and you can delete it afterwards). You can also do
it later in **Configuración** → **Import data from another version…**; if you already have data in the new
version, it asks first and keeps a copy of it in `%APPDATA%\aoe2radar\.antes_de_importar`. The app then
restarts. If the old version started with Windows, turn that off in it (or delete it) so you don't get both.

### Zip, without installer

Each release also has `aoe2radar-X.Y-windows.zip`, for those who prefer no installer (or a USB stick): unzip it
into a folder of its own and open `aoe2radar.exe` (same SmartScreen warning as above). In a folder you can write
to, it updates itself like the installed app; otherwise it just tells you when there is a new version.

### Where your data lives

- Settings, player list, log and caches: `%APPDATA%\aoe2radar` (usually
  `C:\Users\<you>\AppData\Roaming\aoe2radar`), not the program folder. This way the program can live in a
  folder you cannot write to and an update never touches your data.
- Downloaded recs: `Documents\aoe2radar\recs` (your real Documents folder, also when OneDrive moves it).
- **Portable mode**: to keep everything next to the `.exe` (for example, on a USB stick), create an empty file
  named `portable` (or `portable.txt`) in the same folder as `aoe2radar.exe`. The app then reads and writes
  its data and recs in that folder, as versions 1.1 to 1.3 did.

The app includes its own Java runtime, so there is nothing else to install. It is available in Spanish and
English; you can switch the language in **Configuración** (Settings).

## Basic usage

- Add players to the Watchlist by searching for their nickname.
- Put them in groups (for example, by clan or by tournament) to filter the list.
- Look up their recent matches: the result stays hidden until you ask for it.
- Download the rec and send it to the game with one click.
- Open Live now to see who from your list, or from the world top, is playing right now.
- Open a player's profile to see their activity, recent form and match history.
- Compare the ELO of several players in Ratings.
- Use Civ Stats and Tech tree to prepare your games.

## Privacy

What the app stores on disk, in `%APPDATA%\aoe2radar` (recs in `Documents\aoe2radar\recs`; everything next
to the `.exe` in portable mode, see [Where your data lives](#where-your-data-lives)):
- `config.properties`: your settings.
- `players.txt`: the list of players you follow.
- `recs/`: the recorded games you download (plus `descargas.log`, the download history).
- `sfrdata/`: local caches (profiles, ladder, tech tree, civ stats), so the same data is not downloaded twice.
- If you use "Send to game", the rec is copied into the `savegame` folder of your Age of Empires II DE profile.

Services the app connects to on its own:
- **aoe2companion** (`data.aoe2companion.com`, the `socket.aoe2companion.com` socket,
  `api.aoe2companion.com/twitch/live` and `cdn.aoe2companion.com`): matches, Live now, profiles, the list
  of Twitch channels and map images.
- **aoe.ms**, Age of Empires II's own service, to download recs.
- **aoe-api.worldsedgelink.com** (World's Edge/Microsoft), to check whether you have a match of your own in
  progress.
- **steamcommunity.com**, for a player's previous names (it only uses their Steam ID, which is already
  public).
- **Twitch** (`static-cdn.jtvnw.net`), for the thumbnails of live channels.
- **GitHub** (`raw.githubusercontent.com`, `api.github.com`, `github.com` and its download server): the
  nightly `sfr-data` summaries, the `aoe2techtree` data, and the new-version check and download.

Some menus open links in your browser, but only when you click them (a player's page on aoe2companion or
aoe2insights, their Twitch channel, the "buy me a coffee" link, Microsoft's Game Content Usage Rules in
About and in the tech tree). The app never opens them on its own.

aoe2radar needs no user account, has no analytics or telemetry, and sends no personal data to anyone.

## Credits

- Match and profile data: **aoe2companion**, by Dennis Keil.
- Technology tree: **aoe2techtree**, by HSZemi (MIT license for its code and data; the icons and game
  text are Microsoft's, used under the Game Content Usage Rules below).
- Flags: [hampusborgos/country-flags](https://github.com/hampusborgos/country-flags) (public domain).
- UI: **FlatLaf** (Apache 2.0 license).
- Bundled Java runtime: **OpenJDK** (GPLv2 with Classpath Exception).
- Match spectating: **CaptureAge**.
- Streams: **Twitch**.

Full license texts and notices for everything bundled: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Age of Empires II © Microsoft Corporation. aoe2radar was created under Microsoft's "Game Content Usage
Rules" using assets from Age of Empires II, and it is not endorsed by or affiliated with Microsoft.
See Microsoft's [Game Content Usage Rules](https://www.xbox.com/en-US/developers/rules).

## License

The aoe2radar source code is free software under the MIT license: see [LICENSE](LICENSE).

## For developers

To build the project, understand its architecture or contribute, start with
[docs/README_TECNICO.md](docs/README_TECNICO.md) (how to build and test) and
[docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) (layers and migration plan). In short:

```
mvn -q -DskipTests compile      # build
.\verificar.ps1 -Rapido         # tests, no screen needed
.\verificar.ps1                 # tests + screenshot harness (don't touch the mouse while it runs)
mvn -Pempaquetar -DskipTests package   # builds the .exe (jpackage)
mvn "-Pempaquetar,instalador" -DskipTests verify   # ...and the installer (Inno Setup 6)
```

## Author

Jorge «12Tirador» Guardiola — [twitch.tv/12tirador](https://twitch.tv/12tirador)

---

## Español

aoe2radar es una app de escritorio para Windows para Age of Empires II: Definitive Edition. Sirve para
seguir a jugadores y sus partidas sin enterarte del resultado antes de tiempo: recs sin spoilers, Live now,
perfiles, ratings, Civ Stats y Tech tree. La app está en español y en inglés (se cambia en **Configuración**).

Instalación:
1. Descarga `aoe2radar-X.Y-setup.exe` de la última versión en
   [Releases](https://github.com/jguardiola-dev/aoe2radar/releases/latest).
2. Ejecútalo: instala solo para tu usuario, sin permisos de administrador, en
   `%LOCALAPPDATA%\Programs\aoe2radar` (lleva su propio Java). Si Windows avisa de que no está firmado, pulsa
   **Más información** → **Ejecutar de todas formas**.
3. Para desinstalar: Configuración de Windows → Aplicaciones. Tus datos no se borran.

Actualizaciones: la app instalada busca versiones nuevas al arrancar y cada 12 horas, y las descarga sola. Cuando
hay una lista, una franja arriba lo dice («se aplicará al cerrar», con **Reiniciar ahora**); si el primer
arranque con la nueva falla, vuelve sola a la anterior. Si una versión necesita reinstalar, la franja ofrece
**Descargar instalador**. Con **Configuración** → **Actualizar automáticamente** apagado, solo avisa. También
se publica el zip, para quien no quiera instalador.

Tus datos (configuración, jugadores, log y cachés) se guardan en `%APPDATA%\aoe2radar`, y las recs en
`Documentos\aoe2radar\recs`. Si vienes de una 1.x en zip, el primer arranque busca copias viejas en el
Escritorio, Descargas y Documentos y te propone la versión más nueva; si no encuentra ninguna, elige la
carpeta vieja (basta la que descomprimiste). También en **Configuración** → **Importar datos de otra
versión…**. Modo portátil: crea un archivo vacío `portable` (o `portable.txt`) junto a `aoe2radar.exe` y
todo se queda en esa carpeta, como antes. Privacidad, créditos y licencia: ver las secciones en inglés de
arriba.
