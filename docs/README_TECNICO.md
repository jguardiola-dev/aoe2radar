# aoe2radar — README técnico

## Qué es
App de escritorio Windows para Age of Empires II DE: recomendaciones sin spoilers, Live now, perfiles,
ratings, civ stats y tech tree. Java 21 + Swing + FlatLaf. Los datos vienen de la API de aoe2companion
(REST + websocket) y de aoe2techtree; el repo `sfr-data` precalcula resúmenes nocturnos y perfiles.

Está en migración de un único archivo (`SpoilerFreeRecs.java`, ~13.900 líneas) a paquetes con
responsabilidad propia. El plan completo, con fases y criterios de cierre, vive en
[docs/ARQUITECTURA.md](ARQUITECTURA.md).

## Requisitos
- JDK 21 (con `jpackage`; lo trae cualquier JDK 21 completo, no un JRE).
- Maven 3.9+ (`mvn -version` para comprobarlo).
- Windows: la app y el harness de capturas están pensados para Windows (Swing + rutas del registro).

## Compilar
```
mvn -q -DskipTests compile
```
Compila sin tests, para comprobar rápido que todo encaja. El jar completo:
```
mvn -q -DskipTests package
```

## Tests
Dos maneras de pasar la batería, según cuánto tiempo tengas y si puedes ceder la pantalla:

| Comando | Qué hace | Cuándo usarlo |
|---|---|---|
| `.\verificar.ps1 -Rapido` | Compila, la regla de capas (`tools\capas.py`) y todos los tests salvo el harness de capturas. Sin pantalla. | Mientras iteras, antes de cada commit pequeño. |
| `.\verificar.ps1` | Lo mismo + el harness completo (`RegresionCapturas`): abre la app y compara ~23 capturas píxel a píxel. Avisa con pitido y una ventanita antes de empezar y al acabar: no toques el ratón en el monitor principal mientras corre (~1 min). | Al cerrar un grupo de 2-3 commits, y siempre antes de cambiar de paquete o de fase. |

Si el harness sale rojo, no se ajusta la captura: es un bug y se investiga (regla de CLAUDE.md).

## Empaquetar (perfil Maven `empaquetar`)
El build normal (`mvn test`, `mvn package`) no genera el `.exe`: eso vive en un perfil aparte que **no se
activa solo**, para no tocar ni ralentizar el día a día.

```
mvn -Pempaquetar -DskipTests package
```

Qué hace, en orden (todo dentro de `target/`, que ya está en `.gitignore`, para que `mvn clean` también
limpie el empaquetado):
1. Copia las dependencias de runtime (FlatLaf) a `target/jpackage-input`.
2. Copia ahí el jar de la app, ya construido por `maven-jar-plugin`.
3. Genera `target/logo.ico` (multi-tamaño) llamando a `dev.tirador.aoe2radar.SpoilerFreeRecs --make-ico`,
   el mismo mecanismo que usaba `crear_exe.bat` en la 1.1 (ver la nota en el propio `pom.xml`: ese script no
   está en el repo ni en el histórico de Git; sus parámetros están deducidos del código — nombre y versión de
   `util.Identidad`, clase principal del manifest, `--make-ico` documentado en `AcercaDe.generarIco()`).
4. Llama a `jpackage` del JDK con `--type app-image`: una carpeta `target/dist/aoe2radar/` con
   `aoe2radar.exe`, su runtime propio (jlink implícito, sin instalar nada aparte) y los jars en `app/`. No
   hace falta WiX Toolset porque no se genera instalador, solo la carpeta de la app.

La versión del `.exe` (`jpackage.appVersion` en el perfil) se sincroniza a mano con
`util.Identidad.VERSION` hasta que la fase 4 lo automatice.

## Arquitectura en capas
Tabla resumida; el detalle de qué conoce cada capa y los contratos clave está en
[docs/ARQUITECTURA.md](ARQUITECTURA.md#capas-de-dentro-hacia-fuera).

| Paquete | Qué vive ahí |
|---|---|
| `model` | Datos puros (`Match`, `Player`, `Forma`…). Sin Swing, sin red. |
| `util` | `Json`, `t()` (i18n), formatos, `Log`, `Identidad`, `Config`. |
| `api` | Cliente del companion: REST + websocket. Solo transporte y parseo. |
| `sfrdata` | Cliente de `sfr-data` (shards, elo, muestra, civstats, ladder), con caché en disco. |
| `techtree` | Datos de aoe2techtree, con caché en disco y ETag. |
| `cache` | `CacheService`: cachés en memoria y disco con TTL, una sola implementación. |
| `service` | Reglas de negocio: `ProfileService`, `LiveService`, `FormService`, `RecService`, `WatchlistService`, `RatingsService`, `Throttle`, `ControlService`… |
| `ui` | Una vista por pestaña (`…View`) y su presentador (`…Presenter`). Las vistas no llaman a la red. |
| `app` | `Main` (arranque real: `--make-ico`, catálogos, idioma, tema, ventana en el EDT) y `Servicios` (raíz de composición: crea y conecta, en un orden fijo, los servicios que hablan con el companion). |
| (paquete raíz) | `SpoilerFreeRecs` es la ventana (`JFrame`): declara los campos que comparten las vistas y llama al cableado en orden desde el constructor; hoy menos de 300 líneas. Las clases `Cableado*`/`AccionesVentana` (mismo paquete, para leer esos campos sin volverlos `public`) hacen la composición (qué vista con qué servicio) y las acciones de «abrir algo» (URL, Twitch, espectar, CaptureAge). |

Regla de dependencia: cada capa solo conoce las de más adentro (`ui` conoce `service` y `model`, nunca al
revés). Se comprueba en cada `verificar.ps1` con `tools/capas.py`, que falla si aparece una flecha hacia
fuera.

## Cómo se añade una vista nueva (patrón vista + presentador)
Cada pestaña sale entera de `SpoilerFreeRecs.java` a dos clases en `ui`, con cuatro piezas de contrato entre
ellas (interfaces pequeñas, una por responsabilidad):

- **Presentador** (`…Presenter`): la lógica de la pestaña sin Swing. Recibe los eventos de la vista
  (`onBuscar`, `onAbrirPerfil`…), llama a los servicios y decide qué pintar.
- **Pantalla** (interfaz `Presenter.Pantalla`, la implementa la vista): lo que el presentador necesita de la
  vista — pintar un resultado, mostrar un mensaje de estado, dar el estado actual de un componente. La vista
  nunca decide lógica, solo pinta lo que le piden.
- **Anfitrión** (interfaz `View.Anfitrion`, la implementa la ventana principal): lo que la vista necesita de
  fuera de sí misma (por ejemplo, la selección de otra pestaña). Así la vista no conoce la ventana ni a las
  demás vistas.
- **Navegación** (`ui.Navegacion`): cómo una vista pide abrir OTRA vista (“abre el perfil de este jugador”),
  sin conocerla directamente.
- **Tareas** (`ui.Tareas`): las dos reglas de hilos de la app, ya explicadas una sola vez. El presentador
  pide `tareas.enFondo(nombre, trabajo)` para lo lento (red, disco) y `tareas.enUi(trabajo)` para volver al
  EDT antes de tocar Swing. En los tests se usa `Tareas.EN_LINEA`, que ejecuta todo en el acto, sin hilos.

Ejemplo real: `ui/RatingsPresenter.java` + `ui/RatingsView.java` (la pestaña Ratings).

```java
// RatingsPresenter.java — la Pantalla es lo que el presentador le pide a la vista
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
    tareas.enFondo("ladder-datos", () -> {           // trabajo lento, fuera del EDT
        String err = servicio.asegurar(false);
        tareas.enUi(() -> {                           // de vuelta al EDT para pintar
            pantalla.pararProgreso();
            servicio.cargando(false);
            pantalla.cargaTerminada(err);
        });
    });
}
```

```java
// RatingsView.java — implementa Pantalla y pide a la ventana lo que necesita vía Anfitrion
public final class RatingsView implements RatingsPresenter.Pantalla {
    public interface Anfitrion {
        List<Player> seleccion();
        boolean seleccionado(long pid);
        String nombreVisible(long pid, String nombre);
        // …
    }
    // los métodos de Pantalla pintan Swing; nunca llaman a un servicio ni deciden lógica
}
```

Pasos para una vista nueva:
1. Crear `NuevaView` y `NuevaPresenter` en `ui`, con la interfaz `Pantalla` en el presentador y, si la vista
   necesita algo de la ventana, `Anfitrion` en la vista.
2. Mover el código de esa pestaña desde `SpoilerFreeRecs.java` tal cual (sin cambiar lógica) y borrarlo del
   original: la pestaña sale entera, no queda a medias en los dos sitios.
3. El presentador solo llama a servicios (`service`) y modelos (`model`); nunca a `javax.swing.*` ni a
   `java.net.*`. Si necesita hilos, los pide a `Tareas`.
4. Test de caracterización del presentador con `Tareas.EN_LINEA` y una `Pantalla`/`Anfitrion` de prueba
   (doble), sin levantar Swing ni red.
5. `.\verificar.ps1 -Rapido` antes de cada commit; `.\verificar.ps1` completo al cerrar el grupo.
