# VT8b · El resultado del vídeo en la sesión en vivo: «Usar», «Corregir», fallo y «Ver análisis»

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt8b`, desde `feat/video-technique` con VT9b integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`, `S_COMUN.md` y `KPKN_UI_V2.md`;
  - `VIDEO_TECHNIQUE_V1.md` §1 punto 3 y §9 punto 7;
  - del código de hoy:
    - `screens/workout/design/WorkoutSetPanel.kt` y `WorkoutLoadSuggestion.kt` (la pieza gemela);
    - `WorkoutRestPanel.kt`, con `videoSlot` como ÚLTIMO parámetro;
    - `WorkoutV2Body.kt`: la página REST, ~L1914-1942, y la llamada a `SetInputCardV2`, ~L2086;
    - `components/SetExecutionCard.kt`: la llamada a `WorkoutSetPanel`, ~L1578, y el patrón `rmSuggestedWeight` en ~L310 y ~L1199-1204;
    - `WorkoutVoiceSetMutationController.kt` (`patchLast`) y `WorkoutVoiceSetMutations.kt` (`applyVoiceEditPatch`, `resolveVoiceEditTarget`);
    - `WorkoutSetRecorder.kt`, ~L236 (reps lógicas = reps + 0,5·parciales);
    - `screens/workout/WorkoutScreen.kt` (parámetros de navegación);
    - `components/WorkoutSessionAlbumSheet.kt` (`WorkoutMediaPreviewDialog`).
- **Logs:** `C:\kw\logs\vt-8b-<n>.log`.
- **PNG:** `C:/kw/shots/vt/8b`.

## Archivos permitidos
- **Nuevos:**
  - `screens/workout/WorkoutVideoAnalysisController.kt`;
  - `screens/workout/design/WorkoutVideoRepsSuggestion.kt`;
  - `screens/workout/CompletedSetPatch.kt`: `CompletedSetPatch`, `applyCompletedSetPatch` (puro) y `logicalReps()`;
  - pruebas y hoja de render.
- **Modificados** (ViewModels: solo AL FINAL; composables: parámetros nuevos al final y con valor por defecto):
  - `WorkoutViewModel.kt`: el controlador, `videoAnalysisBySetKey`, `patchCompletedSet`;
  - `WorkoutVoiceSetMutationController.kt`: `patchAt(key, patch)`, que generaliza `patchLast` con la misma puerta, Room primero y publicar;
  - `WorkoutSetRecorder.kt`: solo extraer `logicalReps()` sin cambiar el resultado;
  - `design/WorkoutSetPanel.kt`: `videoSuggestion: (@Composable () -> Unit)? = null`;
  - `components/SetExecutionCard.kt`: recibe `videoAnalysis: SetVideoAnalysisUi? = null` y las acciones; pinta la pieza;
  - `WorkoutV2Body.kt`: pasa el mapa y rellena `videoSlot` en REST;
  - `WorkoutScreen.kt`: `onOpenTechniqueAnalysis: (String) -> Unit = {}`;
  - `WorkoutSessionAlbumSheet.kt`: «Ver análisis» en los vídeos con análisis;
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
2. **`WorkoutVideoRepsSuggestion`** (sin estado, gemela de `WorkoutLoadSuggestion`, con caption y `KpknTextAction`):

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
   - en `WorkoutSetPanel`, debajo de la sugerencia de carga, para la serie activa;
   - en `WorkoutRestPanel.videoSlot`, para la serie que se acaba de registrar: el caso más común, porque el análisis termina durante el descanso.
6. **«Ver análisis»:** `onOpenTechniqueAnalysis(mediaId)` → `MainActivity` → `TechniqueAnalysis.create(mediaId)`. Igual en `WorkoutMediaPreviewDialog`.

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
  - `*OngoingWorkout*`, `*FinalizeWorkout*`, `*WorkoutSetPieces*`, `*Voice*`;
  - la compilación de `androidTest`.
- **Hoja de render:** cada estado de la pieza, dentro del panel de la serie y dentro del panel de descanso, a 360 dp, al 100 % y al 130 %.
- **Corridas:**
  1. `:app:compileBaseDebugKotlin`
  2. `:app:testBaseDebugUnitTest` con los filtros de arriba y los nuevos
  3. `:app:compileBaseDebugAndroidTestKotlin`
  4. la hoja de render

## Commits
- `feat(workout): resultado del vídeo en la serie y en el descanso (usar, corregir, fallo y ver análisis)`
- `feat(workout): corregir una serie registrada desde el análisis del vídeo (patchAt)`
- `test(workout): controlador del análisis en la sesión y parche de la serie`
