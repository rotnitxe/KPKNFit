# VT8b · El resultado del vídeo en la sesión en vivo: «Usar», «Corregir», fallo y «Ver análisis»


- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt8b`, desde `feat/video-technique` con VT9b integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`, `S_COMUN.md` y `KPKN_UI_V2.md`;
  - `VIDEO_TECHNIQUE_V1.md` §1 punto 3 y §9 punto 7;
  - del código de hoy:
    - `components/SetExecutionCard.kt`, en la sesión ORIGINAL (PR11):
      - `SetInputCardV2` (~L760);
      - el patrón `rmSuggestedWeight` para empujar un valor a la tarjeta (parámetro ~L794 y `LaunchedEffect` ~L1710);
      - `IntegratedLoadInput` (~L375) con `QuickSuggestionRow` (~L467, pintada en ~L679): la fila de chips «Sugerido» bajo el campo de kg es el modelo de estilo y densidad de tu pieza;
    - `WorkoutSessionContracts.kt` (~L446 y ~L477): contratos de la sugerencia de carga;
    - `components/WorkoutRestOverlay.kt`:
      - `RestTimerOverlay` (~L43), que ya recibe `lastCompletedSet` y `lastCompletedSets`;
      - `NormalRestContent` (~L324): el hueco va entre el nombre del ejercicio (~L446-457) y la fila −/+ (~L461);
      - `FeedbackContent` (~L149, con el reloj en ~L279);
    - `WorkoutOverlayHost.kt` (~L52): quien llama a `RestTimerOverlay`;
    - `WorkoutV2Body.kt`: la llamada a `SetInputCardV2` (~L2067);
    - `WorkoutVoiceSetMutationController.kt` (`patchLast`) y `WorkoutVoiceSetMutations.kt` (`applyVoiceEditPatch`, `resolveVoiceEditTarget`);
    - `WorkoutSetRecorder.kt`, ~L236 (reps lógicas = reps + 0,5·parciales);
    - `screens/workout/WorkoutScreen.kt` (parámetros de navegación);
    - `components/WorkoutSessionAlbumSheet.kt` (`WorkoutMediaPreviewDialog`, ~L106). Lo abren 3 sitios: el álbum de la sesión (~L93), `SetCardExerciseMediaBack` (~L346) y `CockpitPhotosPage` en `WorkoutSessionCockpit.kt` (~L507).
- **Logs:** `C:\kw\logs\vt-8b-<n>.log`.
- **PNG:** `C:/kw/shots/vt/8b`.

## Paso 1: lo pendiente del grabador (VT8a ya está integrado, `2b13a9b67`)
Hazlo antes de lo tuyo, en un commit aparte: `fix(technique): retoques del grabador (inclinación antes de grabar, focal sin suposición, título, guía y silueta)`.

**Archivos permitidos para esto:**
- `screens/workout/technique/CaptureMetadataRecorder.kt`, `TechniqueRecorderScreen.kt` y `FramingCheckController.kt`;
- `design/RecorderTopBar.kt`, `design/FramingGuideOverlay.kt` y `design/RecorderBadges.kt`;
- sus pruebas y `TechniqueRecorderRenderSheetTest.kt`.

**Qué haces:**
1. **«Endereza el móvil» antes de grabar.**
   - El sensor de gravedad solo se registra en `CaptureMetadataRecorder.start()`, es decir, al pulsar REC. Antes de grabar, `currentRollDeg` es `null` y el chip nunca aparece.
   - Registra el sensor desde que el grabador se abre (por ejemplo, `startPreview(targetRotation)`) y quítalo en `close()`/`onDispose`.
   - `start()` solo vacía las muestras de la mediana.
   - `currentRollDeg` usa la `targetRotation` del momento.
   - Prueba JVM de la lógica, con un sensor falso.
2. **La focal, sin suposición.** En `calculateFocalLengthPx`, si no se puede leer el tamaño del vídeo, devuelve `null`, no 1920: así `PlateScale` usa su FOV de 70°.
3. **El título se corta al 130 %** («Sentadill…»), porque las insignias le quitan el sitio.
   - Las insignias (60 FPS y disco) pasan a una segunda fila, bajo el título.
   - El nombre del ejercicio puede ocupar 2 líneas.
4. **La guía de texto se monta sobre la silueta** (`fullscreen_idle_130.png`).
   - Ponla en una franja propia sobre el botón REC, con un fondo `WizardColors.background` al 70 % y esquinas de 16 dp.
   - O bien acorta la silueta para que no se toquen. Comprueba en la hoja, al 130 %, que no se solapan.
5. **El disco de la silueta de lado está a la altura de la cadera.**
   - Colócalo según el perfil: a la altura de los hombros en SQUAT y OHP, junto a la espinilla en DEADLIFT, HINGE y ROW, y sobre el pecho en BENCH (silueta tumbada si la hay).
   - Si no hay una regla clara para el perfil, no lo dibujes: la insignia del disco ya avisa.
6. **KDoc:** quita la mención a «Otro» de `RecorderBadges.kt`.

**Corridas:** las de este paso entran en tus corridas 1 y 2, con `--tests "com.example.kpkn.screens.workout.technique.*"` añadido, y en la hoja de render del grabador.

**Sesión en vivo ORIGINAL (PR11; U7, D28).** El rediseño LS se retiró.
- La sesión «KPKN Wizard definitivo» va a refinar SOLO el aspecto de `SetExecutionCard` y `WorkoutRestOverlay`: misma disposición y mismos controles.
- Antes de lanzar este paquete, el orquestador confirma que no hay un paquete suyo en curso sobre esos archivos y fusiona lo último de `feat/sessions-v1`.
- Tus cambios ahí: mínimos, con los parámetros al final y con valor por defecto.

## Archivos permitidos
- **Nuevos:**
  - `screens/workout/WorkoutVideoAnalysisController.kt`;
  - `screens/workout/design/WorkoutVideoRepsSuggestion.kt`: pieza sin estado en tokens KPKN v2 (`KpknType`, `WizardColors`), compacta, con la densidad de `QuickSuggestionRow`;
  - `screens/workout/CompletedSetPatch.kt`: `CompletedSetPatch`, `applyCompletedSetPatch` (puro) y `logicalReps()`;
  - pruebas y hoja de render.
- **Modificados** (ViewModels: solo AL FINAL; composables: parámetros nuevos al final y con valor por defecto):
  - `WorkoutViewModel.kt`: el controlador, `videoAnalysisBySetKey`, `patchCompletedSet`;
  - `WorkoutVoiceSetMutationController.kt`: `patchAt(key, patch)`, que generaliza `patchLast` con la misma puerta, Room primero y publicar;
  - `WorkoutSetRecorder.kt`: solo extraer `logicalReps()` sin cambiar el resultado;
  - `components/SetExecutionCard.kt`: `SetInputCardV2` recibe AL FINAL `videoAnalysis: SetVideoAnalysisUi? = null` y sus acciones (lambdas con valor por defecto), y pinta la pieza bajo los campos de la serie activa;
  - `WorkoutV2Body.kt`: pasa a `SetInputCardV2` el análisis de su serie;
  - `components/WorkoutRestOverlay.kt`: `RestTimerOverlay` recibe como ÚLTIMO parámetro `videoSlot: (@Composable () -> Unit)? = null` y lo pasa a `NormalRestContent`, que lo pinta entre el nombre y la fila −/+, y a `FeedbackContent`, que lo pinta bajo el reloj;
  - `WorkoutOverlayHost.kt`: rellena `videoSlot` para la serie recién registrada;
  - `WorkoutScreen.kt`: `onOpenTechniqueAnalysis: (String) -> Unit = {}`;
  - `WorkoutSessionAlbumSheet.kt`: «Ver análisis» en los vídeos con análisis. `WorkoutMediaPreviewDialog` recibe AL FINAL `onOpenAnalysis: ((String) -> Unit)? = null`;
  - `SetCardExerciseMediaBack.kt` y `WorkoutSessionCockpit.kt`: solo pasar `onOpenAnalysis` al diálogo;
  - `MainActivity.kt`: SOLO conectar `onOpenTechniqueAnalysis` con la ruta `TechniqueAnalysis`.

## Qué haces
1. **`WorkoutVideoAnalysisController`** (lo crea el VM; vive en su ámbito):
   - combina `VideoAnalysisRepository.observeBySessionKey(sessionKey)` con las series registradas del VM;
   - las claves son las de `workoutSetKey` con el lado normalizado (`WorkoutMediaSides`);
   - si una serie tiene varios vídeos, manda el último DONE, o el último en curso si no hay ninguno DONE.
   - **Resultado:** `StateFlow<Map<String, SetVideoAnalysisUi>>`, con `stateIn(WhileSubscribed(5_000))`.
   - **`SetVideoAnalysisUi(mediaId, state)`**, con `state` uno de:
     - `Analyzing(progress)`;
     - `Suggest(reps, partials, failed, confidence, romPercent?)`, si la serie aún no está registrada;
     - `Mismatch(logged, counted, partials, failed, confidence)`, si está registrada con otra cifra;
     - `Agrees(counted)`, para pintar un «✓ El vídeo confirma 8» discreto;
     - `Inconclusive`;
     - `Unsupported(reasonText)`.
   - **ROM %:** solo si `exercise.trackRom` y hay una referencia personal (máximo de `romMeanDeg` en ≥ 2 sesiones analizadas del mismo `exerciseKey`): ROM % = media de la serie / referencia × 100, redondeado a 5.
2. **`WorkoutVideoRepsSuggestion`** (sin estado, con el estilo y la densidad de la fila de sugerencia de la sesión original, en tokens KPKN v2, con un texto breve y `KpknTextAction`):

   | Estado | Texto | Acción |
   |---|---|---|
   | Analyzing | «Analizando el vídeo… 42 %» | — |
   | Suggest, confianza ALTA | «El vídeo cuenta 8 · +1 parcial» | «Usar» |
   | Suggest, confianza MEDIA | «≈ 8 en el vídeo» | «Revisar» (abre el análisis) |
   | Mismatch, confianza ALTA | «Registraste 8; el vídeo cuenta 7» | «Corregir» |
   | Mismatch, confianza MEDIA | «≈ 7 en el vídeo» | «Revisar» |
   | Con fallidas | se añade «· 1 al fallo» | «Marcar fallo» |
   | Inconclusive | «Vídeo no concluyente» | «Ver análisis» |
   | Unsupported | el motivo, en `textFaint` | — |
   | Agrees | «✓ El vídeo confirma 8» | — |

   - TalkBack lee el texto completo.
   - **Nunca se aplica solo.**
3. **«Usar»** (serie sin registrar): la tarjeta lo resuelve EN LOCAL (`updateActiveValueText(reps)`, y las parciales en el borrador si el ejercicio las admite). No hace falta ir al VM.
4. **«Corregir» y «Marcar fallo»** (serie registrada):
   - `viewModel.patchCompletedSet(setKey, CompletedSetPatch(edit = VoiceSetEditPatch(metricValue = counted), partialReps = …, reachedFailure = …))`;
   - por debajo, `WorkoutVoiceSetMutationController.patchAt(key, patch)`, con la misma puerta que la voz: Room primero, publicar, y sus resultados (Committed, Busy…);
   - `applyCompletedSetPatch` reutiliza `applyVoiceEditPatch`, más las parciales y el fallo coherentes con `isPartial` y la técnica PARTIALS;
   - el 1RM estimado y los PR se quedan como se registraron, igual que con la voz (déjalo escrito en el KDoc);
   - después, `WorkoutMediaRepository.syncSetSnapshots`.
5. **Dónde se pinta:**
   - en `SetInputCardV2`, bajo los campos de la serie activa (debajo de `QuickSuggestionRow` cuando se ve), para la serie activa;
   - en el hueco de `RestTimerOverlay`, para la serie que se acaba de registrar. Es el caso más común, porque el análisis termina durante el descanso.
6. **«Ver análisis»:** `onOpenTechniqueAnalysis(mediaId)` → `MainActivity` → `TechniqueAnalysis.create(mediaId)`. Igual en `WorkoutMediaPreviewDialog`, en los tres sitios que lo abren.

## Pruebas
- **`CompletedSetPatchTest`** (JVM):
  - reps y parciales con las reps lógicas;
  - fallo;
  - sin cambios → `NoChange`.
- **`WorkoutVideoAnalysisControllerTest`** (Robolectric con BD en memoria):
  - Suggest, Mismatch y Agrees según la serie;
  - varios vídeos → el último DONE;
  - lado normalizado.
- **`patchAt`:** sobre una sesión de prueba, la serie cambia en Room y en el estado.
- **Siguen en verde:**
  - las JVM de `screens/workout/` y `domain/workout/`;
  - `*OngoingWorkout*`, `*FinalizeWorkout*` y `*Voice*`;
  - la compilación de `androidTest`.
- **Hoja de render:** cada estado de la pieza, suelta, dentro de un recorte de `SetInputCardV2` y dentro de `NormalRestContent`, a 360 dp, al 100 % y al 130 %.
- **Corridas:**
  1. `:app:compileBaseDebugKotlin`
  2. `:app:testBaseDebugUnitTest` con los filtros de arriba y los nuevos
  3. `:app:compileBaseDebugAndroidTestKotlin`
  4. la hoja de render

## Commits
- `feat(workout): resultado del vídeo en la serie y en el descanso (usar, corregir, fallo y ver análisis)`
- `feat(workout): corregir una serie registrada desde el análisis del vídeo (patchAt)`
- `test(workout): controlador del análisis en la sesión y parche de la serie`
