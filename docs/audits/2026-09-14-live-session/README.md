# Auditoría sesión en vivo — 2026-09-14

Informe **F0** (solo lectura de código). Verificado contra el árbol actual el 2026-09-14. No se copiaron números de línea del plan Cursor a ciegas: cada `file:line` se releyó en fuente.

Espejo de construcción: [`.opencode/plans/2026-09-14_live-session-relator-media.md`](../../../.opencode/plans/2026-09-14_live-session-relator-media.md).

**Fuera de este informe:** implementación F1–F5, cambios de producto, commits.

---

## Decisiones de producto (bloqueadas por el usuario)

Quedan escritas aquí como contrato. No reabrir en F1–F5 salvo anulación explícita.

1. **Relator inteligente sin LLM, solo texto.** Motor de reglas + memoria + variedad. **Sin TTS.** La voz (`services/workout/`) queda fuera de alcance.
2. **Medios:** almacenamiento en `filesDir` privado (local-first) + acciones por elemento **Compartir** / **Guardar en galería** + export en Ajustes > Datos.
3. **Trayectoria ML Kit Pose = F5** detrás de feature flag, solo tras álbumes y el bug de vídeo.
4. **Descanso: se conservan las dos vistas.** `RestTimerOverlay` (expandido, `WorkoutRestOverlayHost`) y `RestLiveCard` (página del pager al minimizar vía `isRestMinimized` / `toggleRestMinimized`) leen el mismo `restTimer.remaining`. **No es duplicidad.** No eliminar ni fusionar; F4 solo garantiza paridad de acciones y del relator.
5. **Modo Dios fuera de alcance.** Recortes intencionales: `GodModeSessionBoard` (sin callers de producción) y `godModeUndoStack`. **No son bugs.** No proponer borrarlos, recablearlos ni “completarlos”.

---

## Recortes intencionales (no hallazgo)

| Pieza | Estado actual | Tratamiento |
|-------|---------------|-------------|
| `GodModeSessionBoard` | Definido en `components/GodModeSessionBoard.kt:69`. **Cero callers** de producción (solo `GodModeSessionBoardTest`). | Recorte. No reportar como dead-code a borrar. |
| `godModeUndoStack` | Campo vivo en `WorkoutUiModels.kt:286`; se escribe en el VM (p. ej. 2424, 2611, 2632, 2675, 2782, 3650, 3672, 3687, 3728); se muestra en `WorkoutRoadmapBar.kt:131` / `:331`. El pager fuerza `godModeActive = false` (`WorkoutV2Body.kt:374`, `:379`, `:1551`). | Recorte de superficie. No “arreglar” el pager ni el board. |
| `RestTimerOverlay` + `RestLiveCard` | Overlay: `WorkoutRestOverlay.kt:38` montado en `WorkoutOverlayHost.kt:33` / `WorkoutScreen.kt:1261`. Card: `RestLiveCard.kt:54` montada en `WorkoutV2Body.kt:1751–1768`. Mismo `restTimerRemaining`. | Dos vistas intencionales (expandido vs minimizado). |

---

## Hallazgos P0 — rendimiento y pérdida de datos

### P0-1 — God-state: ticks 1 Hz de cardio/movilidad dentro de `WorkoutUiState`

`WorkoutUiState` tiene **130** propiedades (`WorkoutUiModels.kt:147–293`; el borrador del plan estimaba ~145). `WorkoutScreen.kt:261` hace `viewModel.uiState.collectAsStateWithLifecycle()` y pasa el objeto entero a `WorkoutV2Body`.

Los ticks de cardio y movilidad **escriben el god-state cada segundo**:

- Cardio: `WorkoutViewModel.kt:4419–4440` (`launchCardioTimerJob`, `delay(1_000L)` → `_uiState.update { copy(cardioTimerState = updated) }`).
- Movilidad: `WorkoutViewModel.kt:4128–4147` (`mobilityTotalTimerJob`, `delay(1_000L)` → `copy(mobilityTotalTimerState = …)`). Segundo arranque análogo en `:4219`.

El countdown de sesión **sí** está aislado: comentario y `StateFlow` en `WorkoutViewModel.kt:219–220`; colectado en `WorkoutV2Body.kt:241`. El campo `WorkoutUiState.sessionTimeRemainingSeconds` (`WorkoutUiModels.kt:262`) **no se escribe nunca** (campo muerto; el vivo es el `StateFlow` del pacing).

Descanso: `RestTimerController.kt:204` hace `delay(500L)` sobre `_remaining` (aislado). Pero ese flow se colecciona en padres grandes: `WorkoutScreen.kt:280` y otra vez `WorkoutV2Body.kt:139` → recomposición del cuerpo ~2 Hz mientras corre el descanso.

**Propuesta F1:** sacar `cardioTimerState` / `mobilityTotalTimerState` a `StateFlow`s dedicados (mismo patrón que `restTimer.remaining`) y colectarlos solo en las cards.

### P0-2 — Pager: hasta 5 `SetExecutionCard` vivas + `SideEffect` por frame

`WorkoutV2Body.kt:1514` — `HorizontalPager(beyondViewportPageCount = 2)` → página actual + 2 a cada lado = **hasta 5** composables de serie.

`SetExecutionCard.kt` mide **3036** líneas (el plan decía 2958). Costes por card viva:

- Flip: `animateFloatAsState` en `:1614–1618`.
- Timer local 1 Hz: `LaunchedEffect(timerRunning, timerRemainingSeconds)` en `:1592–1604` (`delay(1000)`, `timerElapsedSeconds`).
- Cámara: cada cara Media rebinde CameraX (`SetCardExerciseMediaBack.kt:401–421`, `unbindAll` + `bindToLifecycle`).

`SideEffect` del pager en `WorkoutV2Body.kt:886–898` corre **en cada recomposición/frame de scroll** (asigna `settledRelatorPhase` y visibilidad del FAB).

**Propuesta F1:** `beyondViewportPageCount = 1`; `isSettledPage` para pausar timers/flip/cámara; mover el `SideEffect` a `LaunchedEffect(pagerState.settledPage)`.

### P0-3 — Persistencia completa tras cada serie + serialización JSON

Tras registrar una serie, `WorkoutSetRecorder.kt:502–503` llama `refreshLoadSuggestions` **y** `persistOngoingStateAndAwait()`.

`refreshLoadSuggestions` (`WorkoutLoadSuggestionController.kt:58–81`) recorre **todos** los ejercicios × series × lados (O(E×S)), no solo el ejercicio afectado.

`OngoingWorkoutState.toEntity()` serializa el snapshot entero: `Entities.kt:120` (`dbJson.encodeToString(this)`).

`persist(immediate=true)` **no bloquea Main**: `WorkoutPersistenceController.kt:37–44` lanza en IO. El KDoc del VM (`WorkoutViewModel.kt:2142–2148`) dice lo contrario (“blocks until Room has the snapshot”) — comentario desalineado. El debounce existente (`DRAFT_DEBOUNCE_MS = 350L`, `:207`) solo aplica a `immediate=false`. Los `immediate=true` consecutivos **no se coalescen** (cancelan el job de draft y encolan otro write).

El write suspendido usa `updateOngoingWorkoutAndFlush` (`WorkoutViewModel.kt:228`), no el `runBlocking` de `ProgramRepository.updateOngoingWorkout` (`ProgramRepository.kt:821–824`).

**Propuesta F1:** coalescer inmediatos (~150 ms) manteniendo `persistAndAwait` tras serie; `refreshLoadSuggestions` incremental.

### P0-4 — Vídeo negro, archivo borrado, sin reproductor

`SetCardExerciseMediaBack.kt:325–334` — `AsyncImage(model = file)` sobre `.mp4`. Dependencia: solo `coil-compose` (`android-native/app/build.gradle.kts`); **no** hay `coil-video`, media3 ni `MediaMetadataRetriever`.

Grabación:

- `Finalize` con error **borra el archivo** (`:157–159`).
- `DisposableEffect(Unit)` (`:185–189`) hace `activeRecording?.stop()` al salir de composición.
- Cada preview hace `unbindAll()` al dispose (`:401–421`) mientras otra página del pager rebinde. La página anterior queda negra; una grabación en curso se finaliza con error y se pierde el fichero.

`RECORD_AUDIO`: se consulta en `:147–151` (`withAudioEnabled()` si ya está granted) pero el launcher (`:170–184`) **solo pide `CAMERA`**. El permiso de micrófono de `WorkoutScreen.kt:231–233` es de voz, no de esta captura.

Calidad actual: `Quality.SD` (`:97`). No hay HD.

Miniaturas de competición: el mismo patrón negro en `ProfileCompetitionsArchive.kt:285–297` (`AsyncImage` + overlay ▶ si `VIDEO`). **F4 opcional con aprobación**; no tocar `CompetitionMediaStore`.

**Propuesta F3:** `WorkoutMediaCaptureController` (Finalize robusto, conservar `length > 0`, thumb+duración, `RECORD_AUDIO` antes de grabar, bind solo en página asentada, media3 `PlayerView`).

---

## Hallazgos P1 — crashes, readiness, relator predecible

### P1-1 — `!!` en UI de sesión en vivo

Todos vigentes. Varios están **tras un if** (el `!!` es redundante, no un crash inmediato), pero siguen siendo bomba si el estado cambia en el mismo frame.

| Archivo:línea | Expresión | Guardia |
|---------------|-----------|---------|
| `RestLiveCard.kt:113` | `restState!!.exerciseName` | `!restState?.exerciseName.isNullOrBlank()` en `:111` |
| `WorkoutV2Body.kt:1723` | `pageExercise.cardioDetails!!` | página `CARDIO`; no hay `?: return` |
| `WorkoutV2Body.kt:1836` | `targetExercise.cardioDetails!!` | rama `isCardio` |
| `WorkoutV2Body.kt:2196` | `uiState.imbalanceNotice!!` | `!isNullOrBlank()` en `:2185` |
| `WorkoutStructureSheetsHost.kt:322` | `state.exerciseContextExerciseId!!` | `!= null` en `:321` |
| `WorkoutStructureSheetsHost.kt:965` | `draftExercise.cardioDetails!!` | `!= null` en `:963` |
| `WorkoutStructureSheetsHost.kt:1268` | `state.addCatalogToSupersetGroupId!!` | `!= null` en `:1267` |
| `WorkoutStructureSheetsHost.kt:1321` | `state.addExerciseAfterId!!` | `!= null` en `:1320` |
| `WorkoutStructureSheetsHost.kt:1393` | `state.replaceTargetExerciseId!!` | dentro de `onSelect`; el id puede haberse limpiado |
| `WorkoutStructureSheetsHost.kt:1542` | `state.replaceCardioTargetExerciseId!!` | `!= null` en `:1541` |
| `WorkoutSessionOverlaysHost.kt:139` | `uiState.historySheetExerciseDbId!!` | `!= null` en `:138` |
| `WorkoutSetPager.kt:1266` | `onAddSet!!` | `hasAddSet` en `:1256` |
| `WarmupMobilityLiveCards.kt:146` | `inlineRestRemainingSeconds!!` | `showInlineRest` exige `!= null` (`:106`) |
| `WarmupMobilityLiveCards.kt:234` | igual | `:197` |

`!!` **fuera del alcance F1** (no listados por el plan): `WorkoutVoiceCommandHandler.kt:1402`, `WorkoutTagsContextController.kt:464`, `WorkoutStepRules.kt:267`, `WorkoutSessionHydrator.kt:252`, `RestTimerController.kt:115`.

### P1-2 — Readiness AUGE sin `session` en keys

`WorkoutScreen.kt:336–346`: `LaunchedEffect(augeSnapshot.batteries, perMuscle, augeSnapshot.articular, unresolvedDiscomfortIds)`. Usa `uiState.session` por dentro (`:338–339`) **sin** incluirla en las keys. Si AUGE llega antes que la sesión hidratada, el mapa de readiness puede quedar vacío hasta otro cambio de baterías.

`getTodayWellbeing()` vive en `produceState` local (`WorkoutScreen.kt:326–328`), no en el VM. `calculateDailyReadiness` no se cablea al contexto del relator.

### P1-3 — Relator predecible (motor actual)

Cadena determinista `speechBucket()`: `WorkoutLiveRelator.kt:283–381`. Mismo estado → mismo bucket.

Variantes: típico **3** por familia (`WorkoutLiveRelatorCatalog.kt:71–96` `listOf(...)` de tres heads). Memoria anti-repetición: `RELATOR_SPEECH_MEMORY_SIZE = 12` (`RelatorSpeechVariety.kt:3`), **solo intra-sesión** (`RelatorSpeechSession` en `WorkoutLiveRelatorBar.kt:78–80`). Agotadas las variantes de situate, cae a `situateShort` = `"Serie $i de $n de {ex}."` (`WorkoutLiveRelatorCatalog.kt:65–68`, usado en `WorkoutLiveRelator.kt:233–234`).

Idle fijo 28 s: `RELATOR_IDLE_ROTATE_MS = 28_000L` (`WorkoutLiveRelator.kt:12`; loop en `WorkoutLiveRelatorBar.kt:71–76`).

Sin memoria entre sesiones: no existe `Settings.relatorMemoryJson`.

Sin contexto de plan en vivo. `WorkoutUiState` solo lleva `programId` / `weekId` / `macroIndex` / `mesoIndex` (`:150–153`). `Program.sourceProtocolId` / `sourceRecipe` / `mode` / `goals` / `trainingPhase` / `autoregulationMode` (`Program.kt:18–30`, `:58–60`) **no** se copian al hydrator (`WorkoutSessionHydrator.kt:306–324`). En `Exercise` / `ExerciseSet` **no** hay `slotRole`, `isTopSet`, `loadBasis`, `techniqueModifier`. Sí sobreviven `targetPercentageRM` / `isAmrap` / `targetRPE` / `targetRIR` (`Session.kt:573–581`), `isCompetitionLift` / `executionCues` (`:318–320`) y `SessionPart.name` (`:147`).

Acciones actuales (`RelatorAssistActionKind` en `WorkoutLiveRelatorAssist.kt:18–29`): huecos estructurales, tiempo, movilidad, ultrarrápido. `performRelatorAssist` (`WorkoutViewModel.kt:3420–3519`) no cubre carga, descanso, reemplazo, readiness, técnica, captura ni álbum.

Snapshot en Compose: `rememberLiveRelatorSnapshot` (`WorkoutLiveRelatorHost.kt:28`) **no** memoriza el perfil de catálogo. Recálculo en cada recomposición: `resolveRelatorExerciseContext` (`:134`) + scan lineal del catálogo (`WorkoutLiveRelatorContext.kt:52–72`); llamadas al VM sin `remember` en `:134–155` (`getWeightSuggestionWithAutoRegulation`, `getPreviousSessionFirstSetWeight`) y otra tanda en `:219–254`. `WorkoutV2Body.kt:242` reconstruye el snapshot en el padre grande.

**Propuesta F2:** motor puro `domain/relator/` (no existe aún), `LivePlanContext`, observadores, selector con cooldowns + semilla, compositor ≥6 variantes, memoria larga JSON, builder en el VM con `debounce(400)`.

### P1-4 — Ghost / sugerencia de carga sin memo en el padre

`WorkoutScreen.kt:535–539`: `getGhostForSet` / `getWeightSuggestionWithAutoRegulation` en cada recomposición del screen (el god-state o el rest tick las disparan). En el pager, el ghost de la página activa **sí** usa `remember` (`WorkoutV2Body.kt:1778–1793`); la sugerencia de esa página (`:1794–1797`) **no**.

`localBudgetTick` 1 Hz en `WorkoutV2Body.kt:1365–1372` fuerza `nowMs` y recomposición del stage aunque no haya presupuesto.

---

## Hallazgos P2 — higiene y consolidación

### P2-1 — `completeRestIfStuckAtZero` duplicado

Vivo en dos sitios sobre el mismo `fireNaturalFinishIfIdleAtZero` (idempotente, `RestTimerController.kt:162–168`):

- `WorkoutScreen.kt:754–757`
- `WorkoutOverlayHost.kt:55–57`

Consolidar en uno **sin** tocar las dos vistas de descanso.

### P2-2 — `.first()` de prep mobility/warmup

`WorkoutV2Body.kt:696` y `:711` usan `.first()`. **Están guardados** por `isNotEmpty()` en `:695` y `:710`. No es crash en el flujo actual; F1 puede pasar a `firstOrNull()` como higiene, no como P0.

### P2-3 — Comentario vs persistencia; `runBlocking`

- KDoc VM `persistOngoingState` (`WorkoutViewModel.kt:2142–2148`) vs implementación real (`WorkoutPersistenceController.kt:33–44`).
- `flushForBackgroundBlocking` (`WorkoutPersistenceController.kt:85–89`) se llama desde `onCleared` (`WorkoutViewModel.kt:6409`) — lifecycle, OK.
- `cancelWorkout()` (`WorkoutViewModel.kt:5238`) hace `runBlocking(Dispatchers.IO)` al limpiar ongoing. Está expuesto al handler de voz (`:537`). Verificar que no se dispare en el hilo de UI bajo presión.

### P2-4 — Código muerto verificado (F1 higiene; no Modo Dios)

| Símbolo | Evidencia |
|---------|-----------|
| `WorkoutMobilityOverlayHost` / `WorkoutWarmupOverlayHost` | Definidos `WorkoutOverlayHost.kt:108` y `:176`. Cero callers. `WorkoutRestOverlayHost` (`:33`) **sí** se usa (`WorkoutScreen.kt:1261`). |
| `addExercisePhoto` / `removeExercisePhoto` | VM `:4998` / `:5024`. Cero callers de UI. |
| `WorkoutUiState.sessionTimeRemainingSeconds` | `:262`; nunca asignado. El vivo es el `StateFlow`. |
| `WorkoutLiveGuidanceCard` | Solo androidTest (`WorkoutSupportCardsUiTest.kt:28`). `currentCoachMessage` se calcula (`WorkoutViewModel.kt:6320–6338`) y **no tiene composable de producción**. |

---

## Duplicidades (reales vs intencionales)

### Tres silos de fotos de entrenamiento (P1 de producto, se unifican en F3)

| Silo | Ruta | Vínculo sesión/serie | Room |
|------|------|----------------------|------|
| `ExerciseUserMediaStore` | `filesDir/exercise_user_media/<exerciseKey>` (`ExerciseUserMediaStore.kt:6–9`) | No | No |
| `addExercisePhoto` | `filesDir/workout_photos/<sessionId>/<exerciseId>` (`WorkoutViewModel.kt:5003`) | Sesión+ejercicio; **sin UI** | Mapa en `WorkoutUiState.exercisePhotos` → `WorkoutLog.exercisePhotos` (`WorkoutLog.kt:32`) |
| `addSessionPhoto` + `CockpitPhotosPage` | `…/workout_photos/<sessionId>/session` (`WorkoutViewModel.kt:5184`; UI `WorkoutSessionCockpit.kt:336`) | Sesión; máx. 8 | `WorkoutLog.sessionPhotos` (`:36`) |

`CompetitionMediaStore` (`filesDir/competition_media/<recordId>`, `CompetitionMediaStore.kt:18`) es un **cuarto patrón**. F3 no lo toca.

`provider_paths.xml` hoy: `exports/`, `shares/`, `workout_camera/`. **No** hay `<files-path>` de `workout_media/` (F3 lo añade).

### Cuatro canales de sugerencia de carga

Misma fuente `getWeightSuggestionWithAutoRegulation` (`WorkoutLoadSuggestionController.kt:109`):

1. Chips / card (`SetExecutionCard` + pager).
2. Relator (`WorkoutLiveRelatorHost.kt:135–141`).
3. Voz si `Settings.voiceAutoSuggestLoads` (`Settings.kt:44`; cable VM `:786`).
4. Ghost (`WorkoutScreen.kt:535–537`, `WorkoutV2Body.kt:1786`).

F4: la card muestra el número, el relator lo explica, voz solo con el flag.

### Densificación de tiempo

- `UltraFastEngine` — vivo (`WorkoutViewModel.kt:3005+`).
- `TimeCoachEngine` / `SessionAssistantEngine.evaluate` — editor (`SessionEditorViewModel.kt:723`, `:985`).

No fusionar en F0–F4.

### Relator vs `WorkoutCoachMessages`

El coach se calcula cada serie y no se muestra. F2 `CoachObserver` absorbe la matriz y permite borrar `WorkoutGuidanceComponents`.

---

## Riesgos

| Riesgo | Por qué | Mitigación prevista |
|--------|---------|---------------------|
| ANR / jank en vivo | God-state 1 Hz + 5 cards de 3036 líneas + persist JSON completo | F1 primero, antes de Relator 2.0 / Room 26 |
| Pérdida de vídeos | `unbindAll` + delete-on-error | F3 capture controller; no borrar si `length > 0` |
| Room 25 → 26 | `KpknDatabase.kt:62` `version = 25`. Nueva tabla `workout_media` | Migración + `26.json` + tests DAO; flag `[room]` |
| Import de legado | Tres carpetas + mapas del log | Flag `workoutMediaLegacyImportDone`; una pasada |
| Relator 2.0 rompe tests | `WorkoutLiveRelatorTest.kt` **1687** líneas / 81 `@Test` (el plan decía 1596) | Envolver resolver actual como `ReactionObserver` + `StructureAssistObserver` |
| `runBlocking` en cancel/clear | VM `:5238`; repo `:821` | F1: confirmar solo lifecycle; no ampliar |
| Paridad descanso | Overlay tiene `onSkipExercise` (`WorkoutRestOverlay.kt:51–52`, `:693`); `RestLiveCard` **no** (`RestLiveCard.kt:54–67`: ±15, saltar, adaptativo, expandir) | F4 paridad **sin** fusionar vistas |
| Relator tapado | `WorkoutLiveRelatorLine` está en `WorkoutV2Body.kt:2065–2077` (`zIndex 12`). El overlay se monta **después** en `WorkoutScreen.kt:1235–1261` y cubre el body al expandir | F2/F4: misma línea en overlay y card; el motor no depende de `isRestMinimized` |
| ML Kit en emulador | F5 offline a ~10 fps; emulador sin cámara real | Flag default false; QA con clip |

---

## Propuestas UX / flujos

### F4 por defecto (aprobadas en el plan; no ejecutar en F0)

- Paridad `RestTimerOverlay` ↔ `RestLiveCard`: ±15, saltar, adaptativo, **y** `onSkipExercise` donde aplique. Relator idéntico en ambas. Ninguna vista se elimina.
- Una sola política de “descanso atascado en 0”.
- Un canal de número de carga en la card; relator explica; voz solo con `voiceAutoSuggestLoads`.
- Atajo “PR: foto/vídeo” en Opciones avanzadas cuando la serie es PR.
- Modo Dios intacto.

### F4 opcional (exige aprobación explícita)

- Reutilizar visor media3 + thumbs de F3 en `ProfileCompetitionsArchive` **sin** tocar `CompetitionMediaStore` ni el JSON de `CompetitionRecord`.

### No proponer

- Borrar o fusionar las dos vistas de descanso.
- Borrar `GodModeSessionBoard` / `godModeUndoStack` o “activar” `godModeActive` en el pager.
- TTS del relator.
- UI tipo Wrapped (solo API `prHighlights` en F3).

### Flujos nuevos (F2–F3, no F0)

- Relator plan-aware (T1/AMRAP/top set/semana de bloque) + acciones `APPLY_SUGGESTED_LOAD`, `ADJUST_LOAD`, `START/EXTEND_REST`, `SKIP_REMAINING_WARMUPS`, `OPEN_*`, `CAPTURE_MEDIA`, `OPEN_ALBUM`.
- Álbum por sesión+fecha; rutas `profile/albums`, `profile/albums/{albumKey}`, `media/{mediaId}`; compartir FileProvider; guardar galería; export zip SAF.

---

## Matriz de tests existentes

Recuento **2026-09-14** (no se asume el “58+5” del plan: se volvió a listar).

### Unit — `android-native/app/src/test/.../screens/workout/`

**58 archivos**, **439** métodos `@Test`. El “58” del plan sigue vigente **como archivos**, no como métodos.

| Bloque | Archivos (n) | `@Test` | Cubre | Hueco F1–F5 |
|--------|--------------|---------|-------|-------------|
| Relator actual | `WorkoutLiveRelatorTest`, `WorkoutLiveRelatorMemoryTest`, `RelatorSpeechVarietyTest`, `RelatorLoadAnchorTest` (4) | 81+6+10+7 = **104** | Buckets, memoria 12, anclas de carga | Corpus 30 series, plan-aware, memoria larga, acciones nuevas, `< 1 ms` |
| Persistencia / ongoing | `WorkoutPersistenceDebounceTest`, `WorkoutStructuralPersistenceReplayTest`, hydrator missing template, remap keys, replace archive, conflict/parity (varios) | debounce **2** (solo `immediate=false` / 350 ms) | Draft debounce | Coalescing de `immediate=true`; persist incremental |
| Navegación / pager / steps | `WorkoutPagerSyncTest`, `WorkoutStepRulesTest`, `WorkoutStepNavigator*`, `WorkoutBackNavigationTest`, `WorkoutSessionRulesTest` | 7+17+4+17+29 | Cursor, reglas, back | `isSettledPage`, SideEffect→LaunchedEffect |
| Finish / volumen / calibración | finish*, volume, calibration, timeout, recording gate | varios | Cierre, gates | `attachToLog` + `markPrFlags` de medios |
| Carga / tags / intensidad | auto-regulation, tags*, carousel, planned intensity | varios | Sugerencia y tags | Refresh **incremental** |
| Timers | `RestTimerControllerTest` (3), `WorkoutPacingControllerTest` (13) | 16 | Rest aislado, pacing `StateFlow` | Cardio/movilidad fuera del god-state |
| Ultrarrápido | `UltraFastLiveTest` (5) | 5 | Motor vivo | — |
| Visual / cards | `WorkoutVisualModelsTest` (46) + tokens/prep/FAB | 46+ | Tokens, prep height | `!!` UI, video Finalize |
| Modo Dios | `GodModeSessionBoardTest` (2), `GodModeUndoRulesTest` (1) | 3 | Reglas del recorte | **No ampliar.** No es gap de producto |
| Voz (fuera de alcance F2 relator) | `WorkoutVoiceInputTest` (44) + labels + pounds | 51 | Parsers | No tocar `services/workout/` |

Relator de conceptos (fuera de `screens/workout`): `domain/concepts/RelatorConceptCueTest.kt` — vigente; F2 reutiliza `RelatorConceptCue`.

No hay `domain/relator/` ni tests de trayectoria.

### Instrumented — `androidTest/.../screens/workout/`

**5 archivos**, **26** `@Test`. El “5” del plan sigue vigente **como archivos**.

| Archivo | `@Test` | Notas |
|---------|---------|-------|
| `WorkoutV2UiTest.kt` | 7 | Shell V2 |
| `components/SetExecutionCardUiTest.kt` | 9 | Card de serie; **no** cámara/vídeo |
| `WorkoutRestModalUiTest.kt` | 3 | Overlay de descanso |
| `WorkoutMobilityCanonicalKnowledgeUiTest.kt` | 1 | Movilidad |
| `WorkoutSupportCardsUiTest.kt` | 6 | Incluye `WorkoutLiveGuidanceCard` (único montaje) |

---

## Gaps (tests y producto)

### Tests que F1–F5 deben añadir

- Coalescing de `persist(immediate=true)` (el debounce test actual no lo cubre).
- `refreshLoadSuggestions` incremental.
- Regresión por cada `!!` F1.
- Corpus Relator 2.0: 30 series sin fingerprint ni tema consecutivo; 5/3/1 AMRAP, Smolov, Westside ME, KPKN nativo; readiness baja → `ADJUST_LOAD`; memoria larga; `resolve` < 1 ms.
- Room 25→26, import legado, `markPrFlags`, política Finalize (lógica pura).
- JVM de landmarks sintéticos (F5).

### Producto que F0 no implementa (mapa)

| Fase | Qué falta hoy |
|------|----------------|
| F1 | StateFlows cardio/movilidad; pager 1; memo; persist coalescing; quitar `!!` listados; borrar hosts huérfanos y fotos muertas; campo muerto del countdown |
| F2 | `domain/relator/*`; `LivePlanContext`; campos opcionales en materializador; observadores; acciones nuevas; builder en VM |
| F3 | Room 26, store `filesDir/workout_media`, repo, import, capture controller, media3, álbumes, FileProvider, export |
| F4 | Paridad descanso + canales de carga + atajo PR |
| F5 | Flag `poseTrajectoryEnabled` (no existe en `WorkoutFeatureFlags`, `WorkoutV2Models.kt:6–14`); ML Kit; sidecar `pose.json` |

### Fuera de alcance (no gaps)

TTS / voz, paridad iOS/backend, UI Wrapped, Modo Dios, `CompetitionMediaStore` (salvo la propuesta F4 opcional).

---

## Correcciones respecto al borrador del plan Cursor

| Dato en el plan | Código actual |
|-----------------|---------------|
| `WorkoutUiState` ~145 campos | **130** `val` |
| `SetExecutionCard` 2958 líneas | **3036** |
| `WorkoutLiveRelatorTest` 1596 líneas | **1687** |
| `Entities.kt:119` encode | **`:120`** |
| Overlay `completeRestIfStuckAtZero` “:55” | **`:55–57`** (el call es `:57`) |
| `prepMobilityMembers.first()` crash | Guardado con `isNotEmpty()`; P2 |
| Cámara HD | Hoy **SD** (`Quality.SD`) |
| 58 unit + 5 instrumented | **58 archivos unit / 439 `@Test`**; **5 archivos instrumented / 26 `@Test`** |
| `persist(immediate=true)` bloquea Main | **No**; el KDoc del VM miente |
| `updateOngoingWorkout` runBlocking en el write vivo | El controller usa **`updateOngoingWorkoutAndFlush`** |

---

## Veredicto F0

Informe cerrado con evidencia `file:line` del árbol actual. Decisiones de producto escritas. Recortes de Modo Dios y las dos vistas de descanso **no** figuran como bugs. F1–F5 no implementados.
