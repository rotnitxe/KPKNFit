# UI-2 · El anverso de la tarjeta: el vídeo en un chip de la línea «REPS» (ley 1)

- **Rama:** `vt/ui2` en `C:\kw\vt-a`, que sale de `feat/vt-ui` con UI-1a y UI-1b integrados.
- **Logs:** `C:\kw\logs\vt-ui2-<n>.log`.
- **PNG:** `C:/kw/shots/vt/ui/2`.
- **Antes de empezar, lee:** `UI_COMUN.md` (y lo que cita), `docs/video-technique/UI_PLAN.md` (D35) y el prototipo `Main.dc.html` de la
  tarjeta.
- **Código:**
  - `screens/workout/components/SetExecutionCard.kt`:
    - la línea de cabecera de `ValueStepperBlock` (~L2042-2140): «REPS», el «+» y su `Popup`;
    - la inserción de `WorkoutVideoRepsSuggestion` tras `ValueStepperBlock` (~L2277-2299), con su `onUse`, `onCorrect` y
      `onMarkFailure`;
  - `screens/workout/design/WorkoutVideoRepsSuggestion.kt`;
  - `screens/workout/design/VisualModeAnalysis.kt` (UI-1a);
  - `screens/workout/WorkoutVideoAnalysisController.kt`.

## Por qué
Hoy, cuando llega un análisis, se inserta una fila de texto con acciones entre las reps y el esfuerzo. La tarjeta CRECE: eso rompe la
ley 1. Además el texto se parte («ROM ≈ 95» y «%» en otra línea) y no tiene jerarquía.

## Qué haces

### 1. Fuera la fila que crece
- Quita la inserción de `WorkoutVideoRepsSuggestion` del cuerpo de la tarjeta.
- La lógica de `onUse`, `onCorrect` y `onMarkFailure` se mantiene IGUAL, con el reinicio del selector de reps y las parciales: solo se
  muda al menú del punto 3.
- La tarjeta mide lo mismo con y sin análisis, en TODOS los estados.

### 2. Chip del vídeo en la línea de «REPS»
- **Dónde:** a la derecha de la cabecera «REPS +», en la misma fila, empujado con un `Spacer(weight)`. Solo en modo reps y si la serie
  tiene vídeo (`videoAnalysis != null`).
- **Pieza nueva** sin estado: `WorkoutVideoChip(value: SetVideoAnalysisUi, onClick)`, en `screens/workout/design/`.
  - Píldora de 22–24 dp de alto con relleno horizontal de 8 dp.
  - Fondo `Color(0xFF1F1F1F)` y borde de 1 dp `WizardColors.text` al 12 %.
  - A la izquierda, un glifo de vídeo de 12 dp, el triángulo de `KpknLineIcons.Play` o el que haya.
  - Objetivo táctil de 48 dp sin cambiar la altura de la fila: `minimumInteractiveComponentSize` o un relleno táctil que no mida en
    el layout.
- **Contenido por estado**, en `KpknType.chip` a 11 sp:

  | Estado | Chip |
  |---|---|
  | `Analyzing(p)` | anillo de 12 dp + «42 %» |
  | `Suggest` ALTA | «8» (+ «·1p» si hay parciales) |
  | `Suggest` o `Mismatch` MEDIA | «≈ 8» |
  | `Agrees` | «✓ 8» |
  | `Mismatch` ALTA con conteos distintos | «7», con el número en `sessionAccentColor`: avisa de la diferencia |
  | `Inconclusive` | «?» en `textMuted` |
  | `Failed` | «!» en `danger` |
  | `Unsupported` | sin chip |

- **TalkBack:** el chip lee la frase completa (la de `workoutVideoRepsText`) y «Toca para ver las opciones».

### 3. Menú anclado con la frase y las acciones
- **Apertura:** al tocar el chip se abre un `Popup` anclado bajo el chip, como el del «+».
  - Encima de todo: NO cambia la tarjeta.
  - Ancho de 264 dp; esquinas de 16 dp; fondo `Color(0xFF151515)`; borde de 1 dp `WizardColors.text` al 10 %; relleno de 14 dp.
- **Contenido:**
  - la frase (`workoutVideoRepsText`, sin cortes raros: «ROM ≈ 95 %» con espacio inseparable);
  - debajo, las acciones que ya existen hoy, con `KpknTextAction` en una fila que puede partirse:
    - «Usar» (Suggest ALTA con reps o parciales);
    - «Corregir» (Mismatch ALTA con conteos distintos);
    - «Marcar fallo» (fallos > 0 con ALTA);
    - «Ver análisis» (siempre);
    - «Reintentar» (`Failed`, con `onRetryAnalysis` de UI-1a);
  - con MEDIA o BAJA, solo «Ver análisis». Ninguna acción aplica nada sola.
- **Cierre:** al tocar fuera o tras ejecutar una acción.
- **Pieza:** `WorkoutVideoRepsSuggestion` pasa a ser el contenido de este menú (renómbralo o reutilízalo). Mantén `workoutVideoRepsText`
  y sus pruebas.

## Archivos permitidos
- `screens/workout/components/SetExecutionCard.kt`: la cabecera de reps, quitar la fila y el `Popup` nuevo.
- `screens/workout/design/WorkoutVideoRepsSuggestion.kt` y `screens/workout/design/WorkoutVideoChip.kt` (nuevo).
- Pruebas:
  - las de la tarjeta (`screens/workout/components/SetExecutionCard*Test`);
  - las de la pieza del vídeo (`*VideoReps*`);
  - la hoja `render/LiveSessionRefineRenderSheetTest` o la de la tarjeta que use hoy la escena `video_card`. Búscala con
    `grep -rl video_card src/test`.

## Pruebas
- **Robolectric:**
  - con y sin análisis, la tarjeta mide lo mismo (mismo `boundsInRoot` del contenedor de la tarjeta);
  - tocar el chip abre el menú;
  - «Usar» deja las reps y las parciales como hoy.
- **Pura:** el contenido del chip para cada estado de la tabla.
- **Hoja de render**, al 100 % y al 130 %:
  - la tarjeta sin vídeo;
  - el chip en cada estado de la tabla;
  - el menú abierto con ALTA (Usar), con Mismatch (Corregir) y con MEDIA;
  - el borde de la tarjeta, idéntico en todas.

## Corridas
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.components.SetExecutionCard*" --tests "com.example.kpkn.screens.workout.*VideoReps*" --tests "com.example.kpkn.screens.workout.*VideoChip*" --tests "com.example.kpkn.screens.workout.*VideoAnalysis*"`
3. La hoja, con `KPKN_RENDER_DIR=C:/kw/shots/vt/ui/2`.

## Commits
- `fix(workout): el vídeo ya no agranda la tarjeta: chip en la línea de reps y menú anclado (ley 1)`
- `test(workout): la tarjeta mide lo mismo con y sin análisis`
