# Deuda técnica (se anota aquí lo que se ve y no se arregla en la fase actual)

| Fecha | Dónde | Qué | Fase para resolverlo |
|---|---|---|---|
| 2026-09-13 | vigilante | mapas mutados fuera del EDT | 2 |
| 2026-09-24 | perfilesIndex() | el índice de perfiles solo se cachea en memoria: el harness no puede congelarlo y cada ejecución lo pide a la red | 2 |
| 2026-09-24 | socket ongoing-matches, Twitch | datos en directo sin forma de sustituirlos en tests; `shot_watchlist` ignora la zona de Twitch | 2 |
| 2026-09-24 | reloj | `System.currentTimeMillis()`/`LocalDate.now()` repartidos por todo el código: sin reloj inyectable, el calendario de Actividad y los cronómetros de Live now dependen del día y la hora del test; el harness ignora calendario, «por día de la semana» y «por mes» hasta tener reloj inyectable | 2 |
| 2026-09-24 | vista de arranque | con config limpia la app arranca en Twitch, no en la Watchlist (la captura `shot_watchlist` lo refleja tal cual) | 3 |
| 2026-09-24 | actualizarIndicadoresVivos() | `if (soloVivosBtn != null)` sin llaves: solo protege la primera línea; la sangría engaña y con el botón nulo habría NPE | 2 |
| 2026-09-24 | guardarConfig() | leer-modificar-escribir sin sincronizar: dos hilos a la vez pueden perder claves (sospecha: en la grabación del harness desaparecieron `idioma` y `tema`; sin confirmar) | 2 |
| 2026-09-24 | cargarEloAyer() | pide `elo-<hoy−7>.json.gz`: el nombre depende del reloj (el harness renombra la copia congelada) | 2 |
| 2026-09-24 | ladder (abrir) | carrera: el `invokeLater` que aplica «tabla de 230 px la primera vez» solo actúa si `ladderDivisor` ya tiene alto; según el orden, la tabla sale plegada o a 230 px. El harness fija el reparto por defecto | 3 |
| 2026-09-24 | harness shot_menu | el escenario inventa la partida 555 y un refresco de la app la quita en ~1 s: la captura muestra el estado final («0 jugando»), no un jugador en partida. Rehacer el escenario con servicios dobles | 2 |
| 2026-09-24 | Match, Forma | mezclan datos y presentación: `enfrentamiento()`, `rivalTexto()`, `refConVeredicto()`, `eloAntesDespues()` y `Forma.larga()` generan HTML/colores con `t()`, `temaOscuroActivo`, `escapeHtml`, `SUJETOS`. Separar datos (model) y formato (ui) antes de mover `Match` y `Actividad` a model | 1 (tras util) |
| 2026-09-24 | model (MatchPlayer, LadderHist, Rejilla, Comparado) | ya públicos: `MatchPlayer` con campos mutables; records con arrays (`int[] bins`, `int[][] celdas`, `Map<String,int[]>`) cuyo `equals`/`hashCode` compara referencias, no contenido | 2 |
| 2026-09-24 | TOP_PAIS, TOP_CLAN | `static final String … = t(...)` se evalúa al cargar la clase, antes de que `main` lea el idioma: con la app en inglés siguen en español | 3 |
| 2026-09-24 | api.Freno | el javadoc dice «nunca más de 3 llamadas por segundo» pero el código es un cubo de 5 fichas que se recarga a 1/s (lo que dice CLAUDE.md): comentario desfasado | 2 |
| 2026-09-24 | api.Freno | estado del freno y del cortacircuitos público y mutable (`frenoCreditos`, `PAUSA_HASTA`, `PAUSAS_SEGUIDAS`…): encapsular en `Throttle` (`adquirir()`, `pausar()`) | 2 |
| 2026-09-24 | httpText, httpText429, registrar429, cargarControl | siguen en la app: `httpText` mira el botón Detener (`stopOperacion`/`opEnCurso`); `registrar429` y `cargarControl` escriben en la barra de estado desde la red; la escalada del 429 se reinicia en `httpText429`. Las reglas del freno no viven aún en un solo sitio (CLAUDE.md): `ApiClient` con avisos por callback | 2 |
| 2026-09-24 | httpText (Twitch) | **prioritario**: `httpText` solo frena si la URL empieza por `API` (data.aoe2companion.com/api); las llamadas a `api.aoe2companion.com/twitch/live…` (líneas ~11853 y ~11896) son del companion y se saltan el freno global. El criterio debe ser el host del companion | 2 |
| 2026-09-24 | registrar429 | `PAUSAS_SEGUIDAS = Math.min(PAUSAS_SEGUIDAS + 1, 4)` no es atómico (volatile no basta): dos 429 a la vez pueden subir un escalón en vez de dos. Va con `Throttle` | 2 |
| 2026-09-24 | sfrdata.Ladder.MAPA_IMG_URL | caché de imágenes de mapa compartida: la rellena sfr-data (mapas.json) y también el código de API/Live; no es un dato de sfr-data. Llevar a CacheService | 2 |
| 2026-09-24 | sfrdata.Ladder.LADDER_DIR | «sfrdata/» hace de raíz de todas las cachés en disco (perfiles_shards, paises.txt, mapas/…): debería ser el directorio de caché de CacheService, no una constante del ladder | 2 |
| 2026-09-24 | cache.Paises, cache.Anotaciones | disco en el EDT: `cargarPaises` (Timer de 8 s), `guardarPaises` (Timer de 60 s), `cargarAliases`/`cargarNotas` (al construir la ventana) leen/escriben ficheros en el hilo de la UI | 2 |
| 2026-09-24 | cache.Paises.guardarPaises | pone `paisesSucios = false` antes de escribir: si la escritura falla, ese lote no se reintenta | 2 |
| 2026-09-24 | fase 1 (cierre) | ~~imports que quedan sin uso en SpoilerFreeRecs tras los movimientos~~ **resuelto** al cerrar la fase 1 (8 imports) | 1 |
| 2026-09-24 | cache.HistorialDisco.cargarActividad | disco en el EDT: al abrir un perfil se lee y convierte a Match hasta un año de partidas en el hilo de la UI | 2 |
| 2026-09-24 | api.Parseo.COLOR_SLOT_DISTINTOS | contador que nadie lee (ni logs ni diagnóstico): mostrarlo o quitarlo | 4 |
| 2026-09-24 | Live (VIVO_PARTIDA, ~7753) | **posible NPE en el EDT**: `VIVO_PARTIDA.get(p.id())` tres veces seguidas (comprobar `!= null` y leer `.map`); si el socket borra entre medias, NPE al pintar. Leer una vez en una variable local | 2 |
| 2026-09-24 | Live (barrido ~5446 vs liveEvento ~5459) | el barrido reescribe `VIVO_PARTIDA`/`ahoraEnCurso` (clear + putAll) con una foto previa: una partida que el socket acaba de dar por terminada puede volver hasta el siguiente evento | 2 |
| 2026-09-24 | Live (vivoWatch, vivoInfo, VIVO_RIVAL, VIVO_PARTIDA) | borrados y escrituras en varios mapas sin atomicidad (≈7225, 7237, 10728, 11971, 12032, 12172, 13037 frente a resumenVivo): estados a medias (rival sin partida…). LiveService como único escritor | 2 |
| 2026-09-24 | api.Cancelacion / trabajando() | `opEnCurso = on` y `stopOperacion = false` son dos volatile sin atomicidad: httpText puede ver el stop de la operación anterior (el comentario ya lo asume) | 2 |
| 2026-09-24 | cache.Vivos.VIVO_RIVAL | `Object[]` mutable y sin tipo: record | 2 |
| 2026-09-24 | service.CalculoStats.MIN_PARTIDAS_MAPA | constante que nadie usa (ya en la 1.1): usarla donde tocaba o quitarla | 4 |
| 2026-09-24 | service.Juego.steamIdActivo | método que nadie llama (ya en la 1.1): usarlo o quitarlo | 4 |
| 2026-09-24 | util.Sistema.fijarAutoArranque | **se llama en el EDT** desde el menú: lanza `reg.exe` y espera con `waitFor()`, puede congelar la UI (la llamada del arranque sí va en su hilo). También en el EDT, con poco coste: detectarSavegames, rutaCaptureAge, carpetaLogsJuego, guardarConfig en aprenderCatalogos | 2 |
| 2026-09-24 | sfrdata.CivStats.statsAsegurar | `containsKey` + `put` sin atomicidad: dos hilos pueden parsear la misma ventana (sin pérdida); `computeIfAbsent` | 2 |
| 2026-09-24 | colorWr (app) | dos javadocs seguidos; el primero («verde si el intervalo…») describe una lógica de Wilson que el método ya no tiene | 4 |
| 2026-09-24 | cabecera «Mi partida» (app) | anuncia «quién eres (registro de Windows)», código que ahora está en service.Juego y nadie llama (steamIdActivo) | 4 |
