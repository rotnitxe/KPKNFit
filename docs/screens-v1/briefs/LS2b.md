# LS2b · Sesión en vivo: la serie (cifras, intensidad, sugerencia, «Registrar serie» y tira) (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con LS0, LS1 y LS2a integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/LIVE_SESSION_V1.md`, §0, §2 (puntos 4, 5 y 6) y §6: TU especificación;
- el mapa `C:\kw\analysis\maps\workout.md`, §1.1, §1.2, §2.1, §2.5 y §6. Las líneas son de ANTES de LS0 y LS2a: búscalas por
  nombre;
- las piezas de LS1 (`ui/design/KpknStepperMetric.kt`, `screens/workout/design/WorkoutIntensityRow.kt`, `WorkoutLoadSuggestion.kt`,
  `WorkoutSetStrip.kt`) y el marco de LS2a (`WorkoutLiveFrameHeader.kt`, `design/WorkoutExerciseHeader.kt`,
  `design/WorkoutBottomIsland.kt`).

Este paquete hace LA SERIE: lo que se toca en cada serie. La parte de arriba (fila superior, progreso y cabecera) ya es de LS2a y la
isla inferior también. La lógica del registro NO cambia: mismos borradores (`setDrafts`), mismas llamadas, mismo
`recordActionHolder` y misma sugerencia de carga.

## Qué haces
1. **Panel de la serie** (`screens/workout/design/WorkoutSetPanel.kt`, sin estado). Sustituye la CARA de `SetInputCardV2`
   (`components/SetExecutionCard.kt`) en la página NORMAL, sobre el negro y sin caja:
   - el eyebrow «Serie 2 de 4» (mayúsculas por estilo), o «Aproximación» en las series de aproximación;
   - **las cifras:** dos `KpknStepperMetric` lado a lado, «KG» y «REPS»:
     - el paso de kg es el que ya usa la tarjeta (`quickLoadIncrementFor`); el de repeticiones, 1, con `KeyboardType.Number`;
     - en series por tiempo, «TIEMPO» con «0:45» y ±5 s, en lugar de `WorkoutStepperField`;
     - en peso corporal, lastre o asistencia, el modo como `KpknSegmentedControl` compacto encima de la cifra de kg, en lugar del menú
       de modo de `IntegratedLoadInput`;
   - **la intensidad:** `WorkoutIntensityRow` con la escala del ejercicio (RIR o RPE, como hoy) y «Fallo». Sustituye a la rueda
     `intensityCarouselLabels` y a los chips «Llegar al fallo» y «Reservar reps»;
   - **la sugerencia:** `WorkoutLoadSuggestion` con los datos y la regla de hoy: no se aplica sola si la persona ya escribió otra
     carga;
   - **«Opciones avanzadas»** como `KpknTextAction` con `ChevronDown` (gira al abrir; sin giro con `wizardReducedMotion()`):
     despliega DEBAJO del panel el contenido avanzado de hoy (drop-sets, AMRAP, repeticiones asistidas o parciales, ROM, notas,
     «Reportar serie»…) tal cual, plegado por defecto. LS7 lo pasará a una hoja con piel KPKN: tú no lo reescribes;
   - TalkBack lee el panel como «Serie 2 de 4, 80 kilos, 8 repeticiones, RIR 2».
2. **«Registrar serie»:** `KpknPrimaryPill("Registrar serie")` a todo el ancho (56 dp), con la descripción «Registrar serie».
   Sustituye al FAB (`WorkoutRecordFab` y `WorkoutRecordFabHost`):
   - lee lo mismo que el FAB: `recordFabHolder` (visible, `isUpdateMode`, `activePageKey`), la misma condición de `enabled`
     (`canStartWorkoutRecording` y `canInvokeWorkoutRecordFab`) y la misma acción (`recordActionHolder.actionForPage`);
   - en modo de corrección dice «Actualizar serie» (lo busca `SetExecutionCardUiTest`);
   - al tocar: háptico y la píldora pasa a un `Check` durante 300 ms; con `wizardReducedMotion()`, sin animación;
   - con el teclado abierto, sube sobre el teclado como hoy el FAB (D2.2, `WindowInsets.ime`);
   - sirve también en calentamiento y movilidad, que hoy usan el mismo FAB.
3. **El pie de la pantalla, de arriba abajo:** la tira de series, «Registrar serie» y la isla de LS2a.
   - Por qué: la acción principal queda junto al pulgar; la tira, como contexto, pegada a la serie; la navegación, en el borde.
4. **Tira de series:** `WorkoutSetStrip` en lugar de `WorkoutSetPager` en el `setStripSlot` de `WorkoutScreen.kt`:
   - una función pura `toWorkoutSetDots(…)` (con su prueba) que traduce `liveSetStepperHolder.snapshot` (elementos, índice activo,
     hechas) a `WorkoutSetDot`: hechas con «80 × 8», la actual y las futuras;
   - tocar un punto llama a `onSelectPage` como hoy (abrir una hecha para corregirla); «+» llama a `onAddSet` si existe;
   - la pulsación larga, si hoy hace algo (`onLongPressPage`), se conserva;
   - en superseries, un punto por ronda con lo que hoy muestra la tira.
   - Si `WorkoutSetPager` queda sin uso, NO lo borres en este paquete: anótalo para el cierre.
5. **La cabecera del ejercicio** (`design/WorkoutExerciseHeader.kt`): el nombre pasa de `display` a `KpknType.title`, 2 líneas como
   máximo con «…» (decisión 98: la serie es la protagonista).
6. **Técnica:** conecta el `Info` de la cabecera (`onTechnique`, hoy sin pasar en `WorkoutV2Body.kt`) con lo que hoy abre el reverso
   de la tarjeta (`SetCardTechniqueBack` o `SetCardExerciseMediaBack`). Ábrelo en una `KpknSheet` «Técnica» con ese contenido tal
   cual; si no se puede sacar de la tarjeta sin tocar su lógica, deja el giro de hoy disparado desde el `Info` y anótalo.
7. **Sugerencia, ajuste de LS1** (`design/WorkoutLoadSuggestion.kt`, sin cambiar su firma): el motivo («Por tu última sesión») va
   debajo de «Sugerida 82,5 kg» en la misma columna, a 2 dp, y «Usar» queda centrado a la derecha. Hoy el motivo cae 32 dp más
   abajo porque la fila mide 48 dp por el toque de «Usar».
8. **Lo que queda sin uso** tras el cambio (`WorkoutRecordFab`, `WorkoutRecordFabHost`, `resolveRecordFabBottomOffsetDp` y su prueba,
   `IntegratedLoadInput`, `WorkoutHorizontalWheelPicker`, la rueda de intensidad…): bórralo con Grep en todo `app/src`. Lo que siga en
   uso, se queda.

## Textos y pruebas instrumentadas
Antes de cambiar un texto, Grep en `src/androidTest`. Se buscan, entre otros:
- «Registrar serie» y «Actualizar serie» (por descripción);
- «Opciones avanzadas», «Añadir drop-sets», «Drop-sets», «Saltar técnica y registrar solo la serie»;
- «Serie 1», «Serie 1/2», «Remo sentado · Serie 2», «Último set», «Reportar serie», «Tiempo objetivo», «Sugerida», «Solo esta
  vez», «Sí, solo la serie» y «SERIES EFECTIVAS».

Si cambias uno, actualiza su prueba en el mismo commit.

## Pruebas
- **Siguen en verde:**
  - las JVM de `screens/workout/` y `domain/workout/`;
  - `*OngoingWorkout*`, `*FinalizeWorkout*`, `*StartWorkoutConflict*`, `*WorkoutFrame*`, `*WorkoutSetPieces*` y
    `*KpknStepperMetric*`;
  - `RecordActionHolderTest`;
  - la compilación de `androidTest`.
- **Unitarias** `WorkoutSetDotsTest`: hechas con su cifra, la actual y las futuras; superserie por rondas.
- **Robolectric** `WorkoutSetPanelTest`:
  - ± de kg llama con el paso del material;
  - «Usar» aplica la sugerencia;
  - en tiempo se ve «0:45» y ± mueve 5 s;
  - «Opciones avanzadas» despliega y pliega;
  - la píldora llama a la acción una sola vez por toque y dice «Actualizar serie» en modo corrección.
- **Hoja de render** `render/WorkoutSetPanelRenderSheetTest`. Monta la pantalla de una serie con las piezas y datos de ejemplo, sin el
  VM: fila superior, progreso, cabecera, panel, tira, píldora e isla. Casos:
  - una serie normal;
  - una de aproximación;
  - una por tiempo;
  - peso corporal con lastre;
  - la sugerencia aplicada;
  - la píldora en check;
  - «Opciones avanzadas» abierto, si se puede montar sin el VM (si no, anótalo);
  - la hoja «Técnica» por dentro, montada suelta;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/ls2b`.
  - Mira cada PNG con Read y critica con el listón del usuario: es LA pantalla de la app, se usa con el pulso alto y una mano. Tiene
    que ganar a MacroFactor Workouts en foco y a Hevy en claridad. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/ls2b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "*WorkoutFrame*" --tests "*WorkoutSetPieces*" --tests "*KpknStepperMetric*" --tests "*WorkoutSetPanel*" --tests "*WorkoutSetDots*" > /c/kw/logs/screens-ls2b-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(workout): la serie en vivo con cifras grandes, intensidad, sugerencia, «Registrar serie» y tira de series (LS2b)`.
- Si borras piezas viejas, aparte: `refactor(workout): quita el FAB de registrar y los controles viejos de la tarjeta de serie (LS2b)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- El descanso (LS3), la ruta por dentro (LS4), la preparación, el calentamiento y el cardio por dentro (LS5), el resumen y el cockpit
  (LS6), y las hojas por dentro (LS7): el contenido avanzado y el de la técnica se muestran tal cual.
- La lógica: `WorkoutViewModel` y sus controladores (registro, descanso, persistencia, voz, sugerencia, cierre). Lo que falte, AL
  FINAL del VM.
- Las firmas de las piezas de LS1 y LS2a: solo puedes añadir parámetros opcionales al final.
- `screens/sessioneditor/` y `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Textos cambiados y pruebas actualizadas.
- Pruebas: número y resultado.
- PNG y crítica.
