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
