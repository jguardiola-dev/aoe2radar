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
