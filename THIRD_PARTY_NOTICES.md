# Third-party notices

aoe2radar is free to use; its own code is not open source (see [LICENSE](LICENSE)). The application package (the
installer and the portable zip) also contains, or is built from, the works listed below. Each one keeps its
own license. This file travels with the package, next to `aoe2radar.exe`.

## Age of Empires II game content (Microsoft)

Age of Empires II © Microsoft Corporation. aoe2radar was created under Microsoft's "Game Content Usage
Rules" using assets from Age of Empires II, and it is not endorsed by or affiliated with Microsoft.

Game Content Usage Rules: https://www.xbox.com/en-US/developers/rules

This covers the unit, building, technology and civilization icons and the game text in `techtree/` (inside
the application jar), the map images downloaded at runtime from `cdn.aoe2companion.com`, and the names of
civilizations, units, maps and technologies shown anywhere in the app. aoe2radar is free, has no ads and no
paid features.

## aoe2techtree — MIT License

https://github.com/SiegeEngineers/aoe2techtree

Bundled in the application jar under `techtree/`: `data/data.json`, `data/locales/`, `data/trees/` and the
images in `img/`; refreshed at runtime from the same repository. The MIT license below covers the
aoe2techtree code and data. The icons and game strings are Age of Empires II content © Microsoft
Corporation, used under Microsoft's Game Content Usage Rules (see above), not under the MIT license.

The look of the Tech tree tab (`ui/TechTreeRejilla.java`) follows aoe2techtree.net's layout: node boxes filled with
the node-type colours of its legend (`js/techtree.js`), the portrait at the top with the name below, the age bands
(`#4d3617` at 30 %), connectors that leave the building, turn in the gap above the node and enter from the top
(`js/main.js`), and a highlighted path on hover (`style.css`). No code was copied; the colours and proportions are
taken from that MIT-licensed project, under the notice below.

```
MIT License

Copyright (c) 2018 HSZemi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## FlatLaf — Apache License 2.0

https://github.com/JFormDesigner/FlatLaf

Copyright 2019 FormDev Software GmbH. Distributed unmodified as `app/flatlaf-<version>.jar`, which carries
its own copy of the license in `META-INF/LICENSE`. FlatLaf has no NOTICE file. The full text of the Apache
License 2.0 is reproduced at the end of this file.

## JNA (Java Native Access) — Apache License 2.0 (or LGPL 2.1)

https://github.com/java-native-access/jna (5.17.0)

Used for the global keyboard shortcuts of the in-game build window (`RegisterHotKey` in Windows' user32). Distributed
unmodified as `app/jna-5.17.0.jar`, which carries its license files in `META-INF/`. JNA is dual-licensed under the
Apache License 2.0 and the LGPL 2.1; aoe2radar uses it under the **Apache License 2.0**, whose full text is reproduced
at the end of this file.

## Interface icons

### Lucide — ISC License (and MIT for the icons derived from Feather)

https://lucide.dev · https://github.com/lucide-icons/lucide (package `lucide-static` 1.48.0, September 2026)

Used for: the interface icons only (buttons, tabs, menus, player controls, and the buttons and chips of the
recorded-game viewer). One version for the whole application. The SVG files are in
`src/main/resources/dev/tirador/aoe2radar/ui/iconos/` (inside the application jar), unmodified, each with
its `@license lucide-static v1.48.0 - ISC` header. The list is in `ui/tema/IconosUi.java`. Civilizations,
maps, units, buildings and technologies keep the game's own art (see the Microsoft section): no Lucide icon
replaces them.

```
ISC License

Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```

Some Lucide icons are derived from the Feather project. Of the ones aoe2radar ships, these are:
`arrow-left`, `arrow-right`, `check`, `chevron-down`, `chevron-right`, `chevron-up`, `circle-help` (Feather's
`help-circle`), `clock`, `crosshair`, `download`, `ellipsis` (`more-horizontal`), `external-link`, `house` (`home`), `info`, `maximize`,
`moon`, `plus`, `search`, `triangle-alert` (`alert-triangle`), `tv`, `x`, `zoom-in` and `zoom-out` (Lucide's
own list, in its `LICENSE`, uses Feather's names). For them:

```
The MIT License (MIT) (for the icons listed above)

Copyright (c) 2013-present Cole Bemis

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### FlatLaf Extras — Apache License 2.0

https://github.com/JFormDesigner/FlatLaf (module `flatlaf-extras`, same version as FlatLaf)

Copyright 2019 FormDev Software GmbH. Distributed unmodified as `app/flatlaf-extras-<version>.jar`, which
carries its own copy of the license in `META-INF/LICENSE`. It has no NOTICE file. Used for `FlatSVGIcon`,
which paints the interface icons. The full text of the Apache License 2.0 is reproduced once, at the end
of this file (the same one as for FlatLaf).

### JSVG — MIT License

https://github.com/weisJ/jsvg (2.1.0)

The SVG renderer used by FlatLaf Extras. Distributed unmodified as `app/jsvg-2.1.0.jar`, with its license in
`META-INF/LICENSE`.

```
MIT License

Copyright (c) 2021-2024 Jannis Weis

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Java runtime (OpenJDK) — GPL v2 with the Classpath Exception

The `runtime/` folder is a Java 21 runtime image generated with `jlink` (through `jpackage`). The official
release packages are built on GitHub Actions with **Eclipse Temurin**, an OpenJDK build by the Eclipse
Foundation (Adoptium); its exact version is in `runtime/release` (`JAVA_VERSION`).

OpenJDK is licensed under the GNU General Public License, version 2, with the Classpath Exception. The
license texts and the notices of every module and third-party component in the runtime are in
`runtime/legal/`, one folder per module (for example `runtime/legal/java.base/`). They must not be
removed from the package.

Source code of the runtime: https://github.com/adoptium/jdk21u (tag matching `JAVA_VERSION`) and
https://openjdk.org/projects/jdk-updates/.

## Scenario tool (aoe2radar-escenarios) — a separate program under GPL v3

The folder `herramientas\escenarios\` contains `escenarios.exe`, a **separate program**, aoe2radar-escenarios, licensed
under the GNU General Public License v3. It is not part of aoe2radar and is not under the MIT license: it is shipped in
the same package as an aggregate (GPL v3, section 5). aoe2radar never loads its code: for "Send to game" in Tools ›
Combat › Groups it writes a JSON file, runs `escenarios.exe` as an external process and reads back the `.aoe2scenario`
file it writes. The two programs communicate only through those files.

`escenarios.exe` is built with PyInstaller and contains:

- **AoE2ScenarioParser** 0.9.1 by KSneijders and contributors, GNU GPL v3 —
  https://github.com/KSneijders/AoE2ScenarioParser (the library that reads and writes the AoE2 DE scenario format).
  Note: its PyPI metadata says "MIT", but the LICENSE file shipped in the package is the GPL v3; it is treated as GPL v3.
- its dependencies deprecation (Apache-2.0), ordered-set (MIT), typing_extensions (PSF) and packaging
  (Apache-2.0 / BSD-2-Clause);
- the Python 3.11 runtime (Python Software Foundation License) and the PyInstaller bootloader (GPL v2 with the
  bootloader exception).

Its license is in `herramientas\escenarios\LICENSE`, and `herramientas\escenarios\FUENTES.txt` says where the complete
corresponding source is: https://github.com/jguardiola-dev/aoe2radar-escenarios (the tag of the version shipped).

## Country flags — public domain

The 20×15 flag images in `banderas/` (next to the executable, and inside the application jar) are small
PNG renderings of national flags, attributed to
[hampusborgos/country-flags](https://github.com/hampusborgos/country-flags), whose sources come from
Wikimedia Commons. As that project states: "The flags are not under copyright
protection since flags are in public domain (there may be other restrictions on how the flag can be used
though)."

## aoe2companion — match and profile data

Match, profile, ladder and live-match data come from **aoe2companion** (https://www.aoe2companion.com), by
Dennis Keil: its API, its live-matches socket, its CDN and its daily data dumps (the nightly summaries of
the sfr-data repository are built from those dumps). No aoe2companion data is bundled in the package: it is
downloaded at runtime.

## Liquipedia — tournament data (CC BY-SA 3.0) and logos (fair use)

The tournament calendar of the «Inicio» / «Home» tab comes from **Liquipedia**
(https://liquipedia.net/ageofempires/), licensed under CC BY-SA 3.0
(https://creativecommons.org/licenses/by-sa/3.0/). The sfr-data repository extracts it into `torneos.json`,
published under the same licence; the app downloads it at runtime and shows the attribution next to the list.
No Liquipedia data is bundled in the package.

Tournament logos shown in the tournament explorer and on «Inicio» / «Home» are trademarks of their owners. They are
taken from Liquipedia by sfr-data and used here under fair use, only to identify each tournament; each logo carries its
credit in its tooltip, and the explorer footer says «Datos y logos: Liquipedia, CC BY-SA / fair use». No logo is bundled
in the package.

## gamenex.gg — community tournaments

The «Torneos de la comunidad» / community tournaments list (small tournaments such as CastleRush or Age of Amateurs
Cup) comes from the public tournament list of **gamenex.gg** (https://gamenex.gg/). The sfr-data repository extracts
name, date, format, slots and link into `torneos_comunidad.json` and copies the organizers' logos (shown only to
identify each tournament; they are trademarks of their owners). The app downloads that file at runtime and links
each tournament to its gamenex.gg page. No gamenex.gg data or logo is bundled in the package, and community
tournaments are never mixed with Liquipedia's.

## Live streams: Twitch, YouTube and Kick

The «En directo» / Live view lists who is streaming Age of Empires II on **Twitch**, **YouTube** and **Kick**. The
list is built by sfr-data (`directos.json`); the app only downloads
it, shows thumbnails and avatars from each platform's CDN (`static-cdn.jtvnw.net`, `i.ytimg.com`, `yt3.ggpht.com`,
`yt3.googleusercontent.com`, `*.kick.com`) and opens the stream in your browser when you click. Twitch, YouTube and
Kick are trademarks of their owners; nothing from them is bundled.

## aoe2insights — links

Player and match menus offer a link to the player or match page on **aoe2insights** (https://www.aoe2insights.com/).
It opens in your browser only when clicked; no aoe2insights data is downloaded or bundled.

## Combat simulator: sources of the effect table

The combat simulator (`dev.tirador.aoe2radar.service.combate`) reads unit statistics at run time from the
aoe2techtree `data.json` (SiegeEngineers/aoe2techtree, MIT). What `data.json` does not carry in structured form
(numeric effects of technologies, civilization bonuses, a few unit mechanics) lives in aoe2radar's own table,
`src/main/resources/datosjuego/*.tsv`, written for aoe2radar. Every row names its public source: the in-game help
text as published by aoe2techtree (`data/locales/en/strings.json`, with the string id) and, where the text gives
no number, pages of the **Age of Empires Series Wiki** (https://ageofempires.fandom.com), whose text is available
under the **Creative Commons Attribution-ShareAlike 3.0** license (CC BY-SA 3.0). Only facts (numbers) are taken, each
cited per row; no text or table is copied. The validation tests cite the wiki pages and aoecoach.com unit pages
they check against. Nothing is extracted from the installed game's files.

## Villager calculator: data sources

The villager calculator ("How many villagers do I need?", `dev.tirador.aoe2radar.service.eco`) reads costs,
training and research times at run time from the same aoe2techtree `data.json`. What `data.json` does not carry
(gathering rates, carry capacities, and the effects of economic upgrades and civilization bonuses) lives in
aoe2radar's own table, `src/main/resources/dev/tirador/aoe2radar/service/eco/tabla_eco.json`, written for aoe2radar.
Every row cites its public source:

- the in-game help text as published by aoe2techtree (SiegeEngineers/aoe2techtree, MIT);
- pages of the **Age of Empires Series Wiki** (https://ageofempires.fandom.com), CC BY-SA 3.0: gathering rates, carry
  capacities and the Wheelbarrow carry table;
- **aoe2-de-tools**, by **Survivalist**: the "villagers-required" tool
  (https://aoe2-de-tools.herokuapp.com/villagers-required; code at https://github.com/PeterWhiteJavascript/aoe2-de-tools).
  The idea of the calculator comes from it, and so do the effective farm and pasture rates with each upgrade. Only
  those numbers were taken, each one cited in its row; no code, text or images were copied, and no other values
  are imported from it.

Only facts (numbers) are taken. Nothing is extracted from the installed game's files.

## Build orders: sources and credits

The build orders in `src/main/resources/buildorders/buildorders.json` belong to their creators. Each one names its
author and links the public video it comes from: **Survivalist**, **Hera**, **Daut**, **Sitaux**, **St4rk**,
**Dragonstar**, **Liereyy**, **Morley Games**, **ciskhan**, **Red_Phosphorus**, **Ganji**, **TaToH** and
**Sebastian**.

- Most of them were found thanks to **AoE Buddy** (https://aoebuddy.com/build-orders), which transcribes those
  videos. From that catalogue aoe2radar takes only the facts of each step (population, villagers per resource, age,
  what to build or research, and the video timestamp).
- Five were transcribed by aoe2radar itself, straight from the creators' public videos, with no AoE Buddy
  involvement: two by **TaToH** (YouTube channel https://www.youtube.com/@TaToHAoE: two-range archers from chapters
  3, 4 and 6 of his series "De 0 a 2000 en DE", and the tower rush from chapter 26 of his earlier series
  "De 0 a 2000") and three by
  **Sebastian** (Sebastian Gastelumendi, YouTube channel https://www.youtube.com/@Sebastian_AoE: Men-at-Arms with
  chickens, Goths Men-at-Arms and Varangians). Only the game facts each player states are taken, with the video
  timestamp.

Every note, tip and build name is written by aoe2radar in Spanish and English; no AoE Buddy or creator text,
translation or image is copied. The Build orders view credits each creator and links their video; the AoE Buddy credit is
shown in About and here. Step icons are the game's own, from aoe2techtree (see above).

## Inspirations (no code taken)

- **aoe2database**, by **Pau795** (https://aoe2database.com; web code at https://github.com/pau795/AoE2DatabaseWeb,
  Unlicense). The combat simulator's layout of upgrades (tech tree icons grouped by building) and its damage breakdown
  were inspired by it. No code, data or images were copied from it, so there is no license text to reproduce.
- **aoe2-de-tools**, by **Survivalist** (https://github.com/PeterWhiteJavascript/aoe2-de-tools, no license). The villager
  calculator was inspired by its «villagers required» tool. No code or structure was copied; a few measured gather
  rates are cited row by row in `tabla_eco.json` as facts, with their source.

## Recorded-game parser: ported code

The `.aoe2record` reader in `src/main/java/dev/tirador/aoe2radar/rec/` (package `dev.tirador.aoe2radar.rec`
and its subpackages) was written for aoe2radar in Java, but its knowledge of the file format is ported from
the open-source parsers below. Each ported or derived file says at the top which upstream files it comes from
and that it was ported to Java and modified. aoe2radar only reads recorded games; it does not read or extract
anything from the game's own files.

### aoc-mgz — MIT License

https://github.com/happyleavesaoc/aoc-mgz (read at commit `9c1e9cc`, March 2026)

Used for: the overall file structure, the header fields and their version gates, the scan for initial
objects, the body operations (actions, sync, view lock, chat) and the postgame block. Ported in
`rec/formato/LectorCabecera.java`, `LectorCuerpo.java`, `Acciones.java`, `Chat.java` and
`rec/modelo/Comando.java`, `Syncs.java`, `TipoAccion.java`.
Also used in `service/visor/analisis/ChatEdades.java` (the per-language age-up chat markers of
`mgz/common/chat.py`) and `service/visor/analisis/Revelado.java` (the winner rule of `mgz/model/__init__.py`:
the only team with no resignation, and only if someone resigned; reimplemented).

```
MIT License

Copyright (c) 2021 happyleavesaoc

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### aoe2rec — MIT License

https://github.com/aoe2ct/aoe2rec (crate `aoe2rec` 0.10.0 and the ImHex patterns in `patterns/`, read at
commit `e383a05`, September 2026)

Used for: the game-settings block with its separators, the replay block, the pregame operation, chapters,
the fields added in 67.2 and 68.9, the object-type table and the payload layout of most actions. Ported in
`rec/formato/LectorCabecera.java`, `LectorCuerpo.java`, `Acciones.java`, `TiposCabecera.java`,
`rec/modelo/Comando.java`, `TipoObjeto.java` and `rec/unidades/Categoria.java`. The `UnitClass` enum of
`patterns/aoe2header.hexpat` is also the source of the gaia and unit classes in `service/visor/CatalogoVisor.java`
(`gaia`) and `service/visor/TiposPartida.java` (`deUnitClass`).

The crate declares `license = "MIT"` in `crates/aoe2rec/Cargo.toml`, but the upstream `LICENSE` file leaves
the copyright line as the template placeholder (`Copyright (c) [year] [fullname]`). The copyright line below
is written in good faith from the author named in the project's `Cargo.toml` files. aoe2rec says it is
largely an adaptation of aoc-mgz, whose notice is above.

```
MIT License

Copyright (c) Stéphane Bisinger and aoe2rec contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### aoe2-record-viewer — MIT License

https://github.com/ozansahal/aoe2-record-viewer (`src/parser/parse_rec.ts`, August 2026)

Used for: a few format fixes for game versions 67.2 and 68 (scenario-name string, AI order and work actions,
lobby settings version). Its parser is itself a port of aoc-mgz (notice above). Nothing was taken from its
`src/parser/aocref-data/` folder. Cited in `rec/formato/LectorCabecera.java`, `Acciones.java` and
`rec/modelo/Comando.java`. Also cited in `service/visor/analisis/Calor.java`: the heat-map decay (180 s) and
log1p reference (32) constants of `src/lib/view.ts` and `src/components/Minimap.tsx` (the code is our own).

```
MIT License

Copyright (c) 2026 Ozan Şahal

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Logo

The aoe2radar logo, app icon and Visor eye (`src/main/resources/dev/tirador/aoe2radar/ui/marca/*.svg`, also
`site/img/logotipo-oscuro.svg`) are the project's own design, under its MIT license.

### Inter — SIL Open Font License 1.1

https://rsms.me/inter · https://github.com/rsms/inter

Copyright (c) 2016 The Inter Project Authors. The text of the wordmark ("aoe2radar" in `logotipo*.svg`) is set
in Inter SemiBold and converted to outlines: no font file is distributed. SIL Open Font License 1.1:
https://openfontlicense.org

## Cinzel — SIL Open Font License 1.1

https://github.com/NDISCOVER/Cinzel · https://github.com/google/fonts/tree/main/ofl/cinzel

Copyright 2020 The Cinzel Project Authors (https://github.com/NDISCOVER/Cinzel). Designed by Natanael Gama.
Used only for the title of the hero band (the nick in Profile, the build's name). aoe2radar ships
`src/main/resources/dev/tirador/aoe2radar/ui/tema/fuentes/Cinzel-SemiBold.ttf`: a static instance (`wght` = 600,
renamed "Cinzel SemiBold") generated with fontTools from the variable font `Cinzel[wght].ttf` of the Google Fonts
repository. The full license text, `OFL.txt`, travels next to it in the same folder (and inside the jar).
SIL Open Font License 1.1: https://openfontlicense.org

---

## Apache License 2.0 (full text, for FlatLaf, FlatLaf Extras and JNA)

```
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
