# Fase 0.2 · Cimientos de profundidad en Compose (`ui/design/depth`)

Lee antes, en este orden: `briefs/impl-COMUN-v2.md` (reglas firmes: **Gradle SOLO por la cola**, sin commit, informe), `PROTOCOLO_1A1.md`, `DECISIONES_APROBADAS.md` (incluida la sección «Decisiones de implementación»),
`maps/cimientos.md` y `paridad/CIMIENTOS_GRUPOS.md` (el catálogo YA está cerrado: 65 sombras de caída/anillo/interior/resplandor, 3 sombras de texto, 7 vidrios, 5 filtros y 43 degradados; la versión para máquina es `paridad/cimientos.json`).
Trabajas en el worktree **`C:\kw\pu-dp`** (rama `pulido/dp`, creada desde `feat/pulido-v1`); las herramientas del lienzo (`build.cjs`, `tools/shots.cjs`, `tools/compare.cjs`) se ejecutan en `C:\kw\pulido\docs\design\pulido-v1`
con salida propia (`node build.cjs --out=shots/dp`), y los PNG no se versionan. Otro agente construye a la vez el movimiento (`ui/design/motion`) en `C:\kw\pu-mo`: no dependas de su código ni lo toques.

## Qué hay que construir
Los presets de **sombras, vidrio, resplandores, anillos, realces y degradados** del catálogo, una sola vez, en `android-native/app/src/main/java/com/example/kpkn/ui/design/depth/`, para que las pantallas los **usen por nombre** y nadie vuelva a improvisar una sombra:
- `KpknShadows.kt`: `data class KpknShadowSpec` (lista de capas: desplazamiento x/y, desenfoque, extensión, color con alfa, `inset`) y `enum class KpknShadow` con un caso por preset del catálogo (nombre PascalCase del nombre del catálogo, p. ej. `IslaVidrio`, y una propiedad
  `catalogName` con el nombre EXACTO del catálogo) que expone su `spec` canónico. `fun Modifier.kpknShadow(preset: KpknShadow, shape: Shape)` y la sobrecarga `fun Modifier.kpknShadow(spec: KpknShadowSpec, shape: Shape)`:
  dibujan las capas con los mismos valores que el CSS (capas `inset` incluidas, en el mismo orden de pintado). **Valor exacto, no redondeado**: la matriz de cada paquete trae el «Valor exacto» de cada fila; los presets agrupan hasta ±2 dp / ±4 dp de desenfoque / ±0,067 de alfa solo
  para nombrar y calibrar, así que la API tiene que poder dibujar también el valor exacto de una fila (`preset.spec.copy(...)` o un constructor corto) sin escribir una sombra a mano en la pantalla.
- Los anillos (`0 0 0 1px …`, `outline`/aureolas) son bordes, no sombras: `Modifier.kpknRing(...)` con sus presets. Los realces interiores (`inset 0 1px #ffffff14`, subrayados `inset 0 -2px`) igual (`kpknInset...`). Los de color dinámico (`--c`, `--zc`, `--wc`) reciben el color por parámetro.
- `KpknTextShadows.kt`: las tres sombras de texto (`texto-*`) como `Shadow`/`TextStyle`.
- `KpknGradients.kt`: los degradados canónicos (`velo-*`, `relleno-*`, `resplandor-*`, `vidrio-relleno-*`, `brillo-esqueleto-*`, `rayas-*`, `rejilla`, `fondo-compuesto-*`, `vineta`…) como `Brush`/funciones que reciben tamaño y color donde el CSS usa `var()`.
- `KpknGlassPresets.kt`: los 7 vidrios y 5 filtros apoyados en lo que ya existe (`ui/components/KpknGlass.kt`, Haze 1.7.2): `kpknGlass`/`kpknGlassOrFallback` aceptan el preset (parámetros nuevos con valor por defecto: **no rompas a los llamadores actuales**).
- `KpknDepthSheet.kt`: `internal @Composable fun KpknDepthSheet(sheet: String)` que dibuja **exactamente** las hojas de profundidad `cm-som-*`, `cm-vid-1`, `cm-vid-filtros-1` y `cm-deg-1…5` del lienzo (misma geometría, mismos fondos, mismas posiciones y valores: la geometría de cada una está en `maps/cimientos.md` §5 y en `src/scenes-cm.js` / `src/cm.css`).

Cómo traducir (tú calibras, no adivines): el desenfoque de CSS `blur(B)` es una gaussiana de σ = B/2. En Android usa lo más fiel que haya en la versión de Compose del proyecto (mira `gradle/libs.versions.toml`: `Modifier.dropShadow`/`innerShadow` si existen;
si no, `drawBehind` con `Paint.asFrameworkPaint().setShadowLayer(...)` o `BlurMaskFilter`/`RenderEffect`, con el factor de equivalencia que midas). Donde la capa no se pueda dibujar (API < 28, p. ej.) cae a `Modifier.shadow` con la elevación más cercana, sin romper. `minSdk` 24.
El `box-shadow` de CSS se recorta fuera de la forma en las sombras de caída y se pinta dentro en las `inset`: respétalo (forma con esquinas redondeadas incluida).

## Calibración (la parte que importa)
1. Test de render `android-native/app/src/test/java/com/example/kpkn/render/DepthSheetRenderTest.kt` (modelo: las `*RenderSheetTest` existentes) que dibuje `KpknDepthSheet` por hoja a 360 × 880 y guarde los PNG en `KPKN_RENDER_DIR`.
2. Captura las mismas hojas del lienzo (`node build.cjs --out=shots/dp` y `node tools/shots.cjs shot <id-cm> --build=shots/dp`) y compara con `node tools/compare.cjs <lienzo.png> <app.png>` (lee su cabecera para las opciones; produce el error medio y una imagen lado a lado).
3. Itera sobre el factor/estrategia de cada familia hasta bajar el error medio por muestra (objetivo ≤ 2,5/255 en la zona de cada sombra; el porcentaje de píxeles > 28/255 cerca de 0 salvo bordes de suavizado). Escribe en `paridad/CALIBRACION_DEPTH.md`: por preset, el error medio y el máximo, el método usado y las constantes
   de equivalencia CSS → Android que salieron. Lo que no baje del objetivo se registra como desviación en `paridad/DESVIACIONES.md` (`DESV-nn`) con la imagen lado a lado.
4. Tope: 5 ciclos de calibración por familia; si no cierra, documenta el mejor resultado y la causa.
5. Deja en el KDoc de cada archivo un ejemplo de 3 líneas por familia (cómo se usa un preset y cómo se dibuja un valor exacto).

## Gradle (autorizado por el usuario, solo por la cola)
`KPKN_RENDER_DIR=<carpeta> bash /c/kw/tools/gradle_slot.sh /c/kw/pu-dp/android-native :app:testBaseDebugUnitTest --tests "com.example.kpkn.render.DepthSheetRenderTest"`, en SEGUNDO PLANO (`run_in_background`) y esperando la notificación. La primera compilación de un worktree nuevo es en frío (unos 7 minutos) y una
corrida de pantallas tarda 15–21 min. **No mates procesos java ni abras otro Gradle**; sin `./gradlew` directo; guía completa en `/c/kw/tools/GRADLE_GUIA.md`. Si la cola está ocupada, espera: no la saltes. Entre corridas, comprueba lo que puedas con el carril JVM o leyendo; agrupa los cambios para gastar pocas corridas. No hagas commit.

## Entrega
Archivos nuevos y tocados (rutas relativas), las constantes calibradas, la tabla de errores por preset (`paridad/CALIBRACION_DEPTH.md`), desviaciones abiertas y cómo se usa cada preset. Aparte, guarda tres comparaciones lado a lado (p. ej. `hoja`, `isla-vidrio`, `modal`) y dime sus rutas, para enseñárselas al usuario.
Informe final ≤ 250 palabras, con franqueza sobre lo que no pudiste igualar.
