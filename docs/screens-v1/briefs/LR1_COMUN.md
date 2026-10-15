# LR1 · Común a los paquetes del refinado de la sesión en vivo ORIGINAL (2026-10-14)

El usuario rechazó el rediseño de la sesión en vivo y pidió: «Devuélveme la original. Y a esa misma original le refinas el diseño para
que sea más ad hoc a esta estética nueva». LR0 devolvió la original. LR1a refinó la serie y el descanso y, al verlo, el usuario pidió
seguir con el resto («hazlo ahora»). Estos paquetes aplican el MISMO criterio al resto de pantallas de la sesión.

Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/briefs/LR1a.md` y sus commits (`3b3e34093` la serie, `a097e532c` el descanso): son el patrón a seguir;
- `C:\kw\analysis\maps\live_session_original_map.md`: el mapa de cada pantalla, con archivos y líneas (las letras A1, B4… son de ahí).

Capturas del teléfono (360 × 880 dp):
- la original, en `C:\kw\shots\screens\phone-lr0\`;
- la serie y el descanso ya refinados, en `C:\kw\shots\screens\phone-lr1a\` (`s5_serie.png` y `s6_descanso.png`).
- `s4_a.png`, en las dos carpetas, es la hoja de estado de antes de la primera serie, aún sin refinar.

## La regla de oro
La persona debe reconocer al instante SU pantalla de siempre. Cambia la piel, no el esqueleto:
- misma disposición;
- mismos elementos en el mismo orden y sitio;
- mismos controles y gestos;
- mismos textos, salvo las erratas;
- misma conducta.

Si dudas entre «más bonito» y «más parecido al original», elige parecido.

## Qué es «la piel» (lo que hizo LR1a)
- **Superficies:**
  - `KpknSurface.canvas` (#000), `module` (#111111, radio 24 dp, filete blanco al 7 %) e `inset` (#1A1A1A);
  - una tarjeta es `module`, sus cajas interiores son `inset` y sus cifras van en círculos `inset`;
  - un chip o una píldora encima de algo `inset` no lleva relleno nuevo: lleva un filete de 1 dp en blanco al 12 %;
  - dentro de un módulo no hay otra caja de adorno: se separa con aire o con `divider`.
- **Tipografía (`KpknType`):**
  - Syne para las cifras grandes y los títulos, Inter para lo demás;
  - eyebrow en mayúsculas, salvo los símbolos de unidad («CARGA (kg)»);
  - espacio inseparable antes de unidades y % («82 %», «45 s», «100,5 kg»), coma decimal y «×» con espacios.
- **Color:**
  - la selección y la acción principal, en tinta (#F2EEE6, texto negro): un ✓ flotante, un «Saltar», un «Aplicar»;
  - el acento de la sesión (#F49A6E), solo para el progreso y lo activo, nunca como relleno de un botón;
  - los colores de los RINGS se quedan: Músculos #F49A6E, Energía #F7CF73 y Columna #8FB2FF;
  - `ok` (#43D18C) para lo hecho y `danger` para borrar;
  - fuera el ámbar y el amarillo (#FFB300, #FFCA28, #FFD740, #FFC107, #FACC15, #FFFF00) y los hex sueltos: cada uno pasa a su
    token.
- **Controles de Material sin vestir → su pieza de `ui/design`:**

  | Material | Pieza de `ui/design` |
  |---|---|
  | `Button` | `KpknPrimaryPill` |
  | `OutlinedButton` | `KpknSecondaryPill` |
  | `TextButton` | `KpknTextAction` |
  | `IconButton` | `KpknIconButton` |
  | botón de borrar | `KpknDangerPill` |
  | `FilterChip` | `KpknChip` |
  | `Switch` | `KpknSwitch` |
  | `OutlinedTextField` / `TextField` | `KpknTextField` |
  | `Checkbox` | `KpknCheckRow` |
  | `SegmentedButton` / pestañas | `KpknSegmentedControl` |

  - Firma y conducta, iguales: mismo `onClick`, mismo estado y mismo texto.
  - Si la pieza de `ui/design` no cubre un caso (por ejemplo, un `Slider`), viste el de Material con los tokens. No inventes una
    pieza nueva compartida.
- **Envoltorios de toda la app** (`KpknSheet`, `KpknAlertDialog`, `KpknGlassDialog`, `WorkoutDrawer`, `ProtectedWorkoutBottomSheet`,
  `KpknSnackbar`): NO se tocan. Cambiarlos cambia toda la app. Solo se refina lo que va dentro.
- **Lo que ya refinó LR1a:** la cabecera (`WorkoutHeaderBar.kt`), la tira inferior (`WorkoutSetPager.kt`, `WorkoutRoadmapBar.kt`),
  la tarjeta de la serie, el descanso y el ✓. Es común a todas las fases de la sesión: no lo toques.

## Lecciones de LR1a (no las repitas)
- **Probé en el teléfono lo que la hoja de render no enseñaba:**
  - una cabecera que crecía 32 dp y empujaba la tarjeta debajo del cajón de abajo;
  - el ✓ flotante tapando el texto de un botón;
  - un chip cortado a media palabra por un borde recto.
  En cada pantalla completa, calcula el presupuesto vertical a 360 × 880 dp: nada puede quedar bajo el cajón plegado ni bajo el ✓.
- **Una fila que se desplaza** no se corta con un borde recto: funde su orilla con una máscara de alfa (DstIn).
- **Dos píldoras hermanas** miden lo mismo (`height(IntrinsicSize.Min)` + `fillMaxHeight()`).
- **Un rótulo que no cabe:** en dos líneas, antes que con elipsis, si no rompe el presupuesto vertical.
- **No apiles una superficie del mismo tono** sobre otra: se vuelve invisible. Mide los colores de tu PNG si dudas.
- **Las pruebas de medida de texto con Syne:** el sangrado de sus glifos hace que `right − left`, `size.width` y `didOverflowWidth`
  no coincidan. Mide con el mismo criterio que el componente y no aflojes tolerancias sin decírmelo.

## Lo que NO cambias
- **La lógica:**
  - `WorkoutViewModel`, los controladores y los motores;
  - la conducta: qué abre qué, en qué orden y con qué gesto.
  - Excepción: los arreglos de coma decimal que lista tu brief, uno por commit y con una prueba que falle sin él. El patrón de la
    casa es `.replace(',', '.').toDoubleOrNull()`.
- **La ola VT** (técnica por vídeo; sigue trabajando en paralelo):
  - sus archivos: `SetCardExerciseMediaBack.kt`, `WorkoutMediaCaptureController.kt`, `WorkoutSessionAlbumSheet.kt` y
    `data/media/WorkoutMediaCaptureJournal.kt`;
  - las piezas que comparte: `WorkoutMediaThumb.kt` y `WorkoutVideoPlayer.kt`;
  - el giro de la tarjeta: `cardFace` y el `Box` con `rotationY`;
  - `WorkoutScreen.kt`, `WorkoutV2Body.kt` y `WorkoutOverlayHost.kt`: VT los está tocando.
- **Las piezas compartidas con el editor** (`ExercisePickerSheet`, `CardioEditorCard`, `ExerciseSetsCarousel`,
  `ExerciseSmartLoadDialog`, `CardioIntervalChart`, `CardioCatalogSheet` y `DropSetPlanDefaults`): el editor ya las refinó.
- **El vidrio:** una pieza que se compone DENTRO de la página no lee `LocalHazeState`. Fallo conocido: la app muere en RenderThread.
  Usa el velo opaco (`kpknGlassOrFallback(null, …)`).
- **Los archivos de los otros paquetes LR1:** cada brief dice los suyos. Si necesitas tocar uno ajeno, para y avísame.

## Pruebas y hojas de render
- **Siguen en verde:**
  - `com.example.kpkn.screens.workout.*` y `com.example.kpkn.domain.workout.*`;
  - `*OngoingWorkout*`, `*FinalizeWorkout*` y `*StartWorkoutConflict*`;
  - la compilación de `androidTest`. Las instrumentadas buscan textos: Grep `src/androidTest` antes de cambiar uno.
- **Hoja de render de tu paquete** (nombre y carpeta en tu brief), con las piezas REALES y datos de ejemplo:
  - a 360 dp, al 100 % y al 130 %;
  - mira cada PNG con Read: nada cortado, nada solapado, «…» de un carácter, coma decimal y espacio inseparable;
  - máximo 3 rondas.
- **Gradle:** siempre por `gradle_slot.sh`, en segundo plano y esperando la notificación. Nunca `./gradlew` directo ni `--stop`.
  - Si paras una corrida con `TaskStop`, el cliente de Gradle y su daemon quedan vivos: páralos con `Stop-Process` y suelta tu ranura.
  - Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin`; la final, con el comando entero.
- **El `.dbg` final**, para que yo lo pruebe en el teléfono: cópialo a `C:/kw/shots/<paquete>/app-<paquete>-dbg.apk` y avísame
  enseguida con SendMessage a "main". Después, la corrida final y los commits. No uses ni el teléfono ni el emulador.
