# LS2a · Sesión en vivo: el marco (fila superior, progreso, cabecera del ejercicio e isla) (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3` con LS0 y D2 integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/LIVE_SESSION_V1.md`, §0, §2 (puntos 1, 2, 3 y 7), §5 («Carga») y §6: TU especificación;
- el mapa `C:\kw\analysis\maps\workout.md`, §1, §2.1, §3, §4 y §6. Las líneas son de ANTES de LS0: búscalas por nombre;
- `screens/programdetail/design/` (la regla de semanas de P2, `ProgramWeekStrip`) y `KpknContextMenu` de `ui/design/`.

Este paquete hace el MARCO de la pantalla de una serie. La tarjeta de la serie, «Registrar serie» y la tira de series son de LS2b y
siguen como están: no los toques, aunque se vean viejos dentro del marco nuevo. El descanso (LS3), la ruta por dentro (LS4), la
preparación, el calentamiento y el cardio (LS5), el resumen y el cockpit (LS6) y las hojas (LS7) tampoco son tuyos.

## Qué haces
1. **Fila superior** (sobre el negro, sin isla), en `screens/workout/design/WorkoutTopRow.kt`, sin estado:
   - a la izquierda, `KpknIconButton(ChevronDown, "Minimizar")`, que hace lo que hoy hace salir y dejar la sesión en curso (la
     tarjeta flotante la recoge);
   - en el centro, el cronómetro de la sesión en `KpknType.metricM` tabular («24:31»); debajo, en `caption` `textMuted`, el ritmo si
     hay límite de tiempo («Vas 4 min por delante», con lo que ya calcula `pacingController`). Sin límite, no hay línea;
   - a la derecha:
     - un punto de 8 dp en `SymbolPalette.ok` y «Voz» en `caption` si la voz está activa;
     - `KpknIconButton(More)` con un `KpknContextMenu`: «Sesión» (el cockpit de hoy), «Notas», «Fotos», «Etiquetas», «Modo de voz»
       y «Terminar sesión». Cada una abre lo mismo que hoy.
2. **Barra de progreso de la sesión** (`WorkoutProgressBar.kt`):
   - una regla segmentada a todo el ancho, 6 dp de alto y 3 dp entre tramos: un tramo por ejercicio;
   - los hechos en tinta, el actual en `SymbolPalette.musculo` y los futuros en blanco al 12 %;
   - es la misma regla de semanas de Program Detail: reutiliza su dibujo si se puede extraer sin cambiarla. Si no, cópiala con un
     comentario que diga de dónde sale;
   - tocarla abre la ruta de la sesión que exista hoy (el roadmap expandido); LS4 la rehará;
   - TalkBack: «Ejercicio 3 de 6. Abrir la ruta de la sesión».
3. **Cabecera del ejercicio** (`WorkoutExerciseHeader.kt`):
   - el eyebrow «EJERCICIO 3 DE 6 · TORSO A», o «SUPERSERIE A · RONDA 2 DE 3» en una superserie;
   - el nombre en `KpknType.display`, con 2 líneas como máximo, que se encoge hasta 24 sp si no cabe;
   - una fila de datos en `caption` tabular:
     - «Objetivo 4 × 6–8 · RIR 2»;
     - «Última vez 80 kg × 8». Sin historial, esa parte NO se pinta (nunca «—» suelto);
   - a la derecha, tres `KpknIconButton`:
     - `Info` («Técnica»): lo que hoy abre la técnica, la foto o el vídeo;
     - `Repeat` («Cambiar ejercicio»): el reemplazo de hoy;
     - `More` («Opciones del ejercicio»): un `KpknContextMenu` con lo que hoy cuelga de la cabecera vieja: «Ver historial», «Apodo»,
       «Ultra rápido», «Adaptar» y «Superserie»;
   - el protocolo del catálogo: un `KpknChip` con un icono KPKN Line, nunca con emoji. Si no hay icono que encaje, sin icono;
   - debajo, la línea del relator (`WorkoutLiveRelatorLine`) en `caption`, como hoy. Se oculta en el descanso.
4. **Isla inferior** (vidrio v2 `KpknGlassVariant.ISLAND`), que sustituye al dock del roadmap (`WorkoutRoadmapBar` en modo compacto):
   - a la izquierda, `ChevronLeft` para el ejercicio anterior;
   - en el centro, «Sigue: Press inclinado» en `bodyStrong` (una línea con «…»): abre la ruta;
   - a la derecha, `ChevronRight` para el siguiente;
   - lleva el ancla `TourAnchors.WORKOUT_SIGUIENTE`, que hoy está en el dock;
   - el FAB de registrar y la tira de series siguen donde están hasta LS2b. La isla no los tapa: la tira queda encima y el FAB, a su
     derecha, como ahora.
5. **Lo que se va de la cabecera vieja** (`WorkoutHeaderBar`): todo lo de los puntos 1 y 3 tiene ya su sitio. Si al terminar
   `WorkoutHeaderBar` o partes de `WorkoutRoadmapBar` quedan sin uso, bórralas (Grep en todo `app/src`). Lo que siga en uso, se queda.
6. **Franja de sesión fuera de programa:** pasa a un `KpknModule(accent = WizardColors.danger)` compacto con su texto de hoy.
7. **Carga** (§5): fuera el fondo `0xFF0D1117` y el spinner. Pon esqueletos sobre negro puro (`KpknSkeleton`), y «Cargando la
   sesión…» en `caption` solo si tarda más de 600 ms. «Cancelar carga» se queda como `KpknTextAction`.
8. **Movimiento:** con `wizardReducedMotion()`, sin animaciones nuevas.

## Pruebas
- **Siguen en verde:** las JVM de `screens/workout/` y `domain/workout/`, y `*OngoingWorkout*`, `*FinalizeWorkout*` y
  `*StartWorkoutConflict*`. La compilación de `androidTest` también. Antes de cambiar un texto, Grep en `src/androidTest`: se buscan
  «Ver historial», «Siguiente punto», «Sigue con Press inclinado», «Press inclinado» y otros. Si cambias uno, actualiza su prueba en
  el mismo commit.
- **Robolectric** `WorkoutFrameTest`:
  - la fila superior llama a minimizar y abre el menú;
  - sin límite de tiempo no hay línea de ritmo;
  - la cabecera sin historial no pinta «Última vez»;
  - la isla llama a anterior, siguiente y ruta.
- **Hoja de render** `render/WorkoutFrameRenderSheetTest`, con las piezas montadas con datos de ejemplo (no la pantalla entera con
  el VM):
  - la fila superior con y sin ritmo, y con la voz activa;
  - la barra de progreso en el ejercicio 3 de 6;
  - la cabecera con un nombre de dos líneas y una superserie;
  - la isla;
  - la carga;
  - el marco entero con la tarjeta de hoy debajo, si se puede montar sin el VM (si no, anótalo);
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/ls2a`.
  - Mira cada PNG con Read y critica con el listón del usuario: esta pantalla se lee con el pulso alto. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/ls2a bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "*WorkoutFrame*" > /c/kw/logs/screens-ls2a-<n>.log 2>&1`

## Commit
- `feat(workout): marco de la sesión en vivo (fila superior, progreso, cabecera del ejercicio e isla) (LS2a)`.
- Si borras piezas viejas, aparte: `refactor(workout): quita la cabecera y el dock viejos (LS2a)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La tarjeta de la serie, «Registrar serie» y la tira de series (LS2b); el descanso, la ruta por dentro, la preparación, el cardio,
  el resumen, el cockpit por dentro y las hojas (LS3–LS7).
- Cambiar la lógica (`WorkoutViewModel` y sus controladores: registro, descanso, persistencia, voz, sugerencia, cierre). Lo que falte,
  AL FINAL del VM.
- Las piezas de `screens/workout/design/` que deja LS1 (`WorkoutIntensityRow`, `WorkoutLoadSuggestion`, `WorkoutSetStrip`) y
  `ui/design/KpknStepperMetric.kt`.
- `screens/sessioneditor/` y `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Textos cambiados y pruebas actualizadas.
- Pruebas: número y resultado.
- PNG y crítica.
