# Mapa · descanso, feedback de la última serie, molestias y RIR/RPE (2026-10-16, HEAD 41f8e6653)

Hecho por un explorador de solo lectura. `K = android-native/app/src/main/java/com/example/kpkn`. Líneas aproximadas: busca por
nombre.

## 1. Descanso
- **Pantalla completa:** `K/screens/workout/components/WorkoutRestOverlay.kt`, `RestTimerOverlay(state: WorkoutRestModalState,
  remainingSeconds, hazeState, pendingRestSuggestion, lastSetOutcome, lastCompletedSet, lastCompletedSets, isAdaptiveActive,
  sessionAccentColor, onDecrease, onIncrease, onSkip, skipExerciseLabel, onSkipExercise, onUseAdaptive, onWarmupEffort (sin uso),
  postExerciseFeedbackContent, feedbackExerciseCount, onMinimize, relatorText, relatorPhaseKey, relatorActions, onRelatorAction)`.
  Velo al 92 % con haze, zIndex 6. `AnimatedContent("feedback-layout-transition")` elige:
  - `NormalRestContent`: chevron «Minimizar», línea del relator, **anillo Canvas 204 dp `testTag("lr1g-aro")`**, `RestClockText`,
    tipo de descanso y ejercicio, −15 / «Ajustar tiempo» / +15 (`RestRoundButton`), `RestLastSetModule`, «PLAN DE SESIÓN» /
    «DESCANSO DINÁMICO» y «Descanso sugerido» (`RestFigureModule`), «Saltar descanso», «Saltar series restantes…».
  - `FeedbackContent` (tras la última serie): el mismo anillo encogido (labels `rest-timer-shrink/-stroke/-font`, solo si
    `remainingSeconds > 0`) y el panel de feedback debajo.
  - Piezas: `RestClockText` (también en la tarjeta), `formatRestClock`, `restKindText`.
- **Tarjeta (minimizado):** `K/screens/workout/components/RestLiveCard.kt`, `RestLiveCard(remainingSeconds, totalSeconds,
  sessionAccentColor, restState, pendingRestSuggestion, lastSetOutcome, lastCompletedSet, onDecrease, onIncrease, onSkip,
  onUseAdaptive, onExpand, isAdaptiveActive, skipExerciseLabel, onSkipExercise, modifier)`: barra `KpknProgressBar`, −15 s /
  Saltar / +15 s, expandir. Es la página `LivePageType.REST` del pager (`WorkoutV2Body.kt`, `restPageInsertIndex` en
  `WorkoutSetPager.kt`).
- `RestTimerPill` (`WorkoutRestOverlay.kt`) es inalcanzable hoy. Píldora del roadmap: `TimelineElement.RestPill`
  (`WorkoutSetPager.kt`), construida en `WorkoutV2Body.kt`.
- Calentamiento/movilidad (`RestTimerKind.WARMUP`): inline en `WarmupMobilityLiveCards.kt`, no usa overlay ni tarjeta.
- **Quién decide:** `WorkoutUiState.isRestMinimized` (no persistido); `WorkoutRestTimerOrchestrator.start` lo pone a false;
  `WorkoutViewModel.toggleRestMinimized()`/`minimizeRestOverlay()`; Atrás (`WorkoutBackNavigation.kt`). En `WorkoutScreen.kt`:
  `workingRestActive = isRestTimerRunning && restModalState != null && kind != WARMUP`; overlay si `workingRestActive &&
  !isRestMinimized`; host si eso o `isShowingFeedback` (`showPostExerciseSheet && postExerciseTarget != null`, fuerza pantalla
  completa con `forceShowForFeedback`).
- **Estado:** `WorkoutRestModalState` (`WorkoutSessionContracts.kt`, `@Serializable`, persistido: `exerciseId`, `exerciseName`,
  `kind: RestTimerKind {STANDARD, SUPERSET_INTRA, SUPERSET_ROUND, WARMUP, BETWEEN_SIDES}`, `plannedSeconds`, `suggestedSeconds`,
  `activeSeconds`, `endsAtMs`, `isManualOverride`, …, `skipCurrentExerciseOnFinish`). En `WorkoutUiState`: `restTimerTotal`,
  `isRestTimerRunning`, `pendingRestSuggestion` (`PendingRestSuggestion`: planned, adaptive, lastSet, advancedFeedback),
  `restModalState`, `lastSetOutcomeV2`. **Restante:** `RestTimerController.remaining` (`StateFlow<Int>`, tick 500 ms, calculado con
  `endsAtMs`), expuesto como `WorkoutViewModel.restTimerRemaining`; `restRecovery`/`WorkoutRestRecoveryModel` sin UI.
- **Acciones:** `startRestTimer`, `addRestTime(±15)` → `addTime` (preserveElapsed, isManualOverride, `scheduleRestEnd
  (isAdjustment)`), `stopRestTimer` («Saltar» → feedback o cierre según `pendingPostExerciseIdx`),
  `resolvePendingRestSuggestion`, `completeRestIfStuckAtZero` (solo desde `WorkoutScreen` vía `RestStuckAtZeroPolicy`),
  `isAdaptiveRestActive`, paridad `restLiveActions` (`RestLiveActionParity.kt`). **No hay pausa ni estado «excedido»**:
  `handleNaturalFinish` (`WorkoutRestTimerOrchestrator.kt`) cierra el descanso en 0 y encadena avance / feedback / cierre.
- Origen: `WorkoutSetRecorder.kt` (tipo y plan, `WorkoutAdaptiveRest.compute`, `PendingRestSuggestion` + `startRestTimer`).
  Persistencia `OngoingWorkoutState.restModalState`; restauración en `WorkoutSessionHydrator.kt` solo si `endsAtMs > now`.

## 2. Servicio y alertas (NO romper)
`K/services/workout/`: `WorkoutRestAlertManager` (alarma exacta de fin y prealerta 3 s, servicio en primer plano o notificación,
`onTimerFinishedInApp`, `cancelRestAlerts`, vibración y tonos), `WorkoutRestForegroundService` (cuenta atrás con acciones −15 / +15 /
Saltar / Completar serie), `WorkoutRestForegroundStartGate`, `TimerNotificationActionReceiver` → `WorkoutViewModel.
handleTimerAction`. TTS en `WorkoutVoiceController` (`onRestTimerFinished*`, `onRestTimerStarted*`, aviso de 10 s). Comandos de voz
SkipRest/UseAdaptiveRest/AdjustRestTime (`WorkoutVoiceCommandHandler.kt`). Pruebas: `RestTimerControllerTest`,
`RestLiveActionParityTest`, `WorkoutRestAlertRulesTest`, `WorkoutRestForegroundService*Test`, `RestClockTextTest`,
`LiveSessionRelatorRenderSheetTest` (usa `lr1g-aro`); instrumentada `WorkoutRestModalUiTest` (busca «DESCANSO», «ÚLTIMA SERIE»,
«DESCANSO SUGERIDO», «Ajustar tiempo», «Saltar descanso», «Feedback post ejercicio»).

## 3. Feedback de la última serie
- Disparo en `WorkoutStepNavigator.kt` (al pasar a otro ejercicio con el actual completo: `showPostExerciseSheet = true`,
  `pendingPostExerciseIdx`; −2 al final). El recorder llama a `nextSet` antes de `startRestTimer`: por eso el overlay abre en
  `FeedbackContent`. Objetivo `PostExerciseFeedbackTarget` Single/SupersetGroup.
- Panel: `PostExerciseFeedbackPanel` en `WorkoutPostExerciseFeedbackHost.kt` (contenedor `WorkoutPostExerciseFeedbackContent`):
  «Calidad técnica» slider 1–10 (8 por defecto, por ejercicio); «Qué tan intenso fue» 1–10 + «Fallo» (solo si
  `!exerciseHasPlannedIntensity(ex)`); «Molestias previas relacionadas» Sigue/Resuelta; «¿Sientes alguna molestia?» con buscador
  sobre `DISCOMFORT_CATALOG` y chips; «Registrar feedback».
- Modelo `PostExerciseFeedback` (`data/models/WorkoutLog.kt`): `technicalQuality` (1–10), `discomfortIds` (`"none"` = sin
  molestias), `notes`, `perceivedIntensityRpe`, `perceivedFailure`, `stillPresentDiscomfortIds`. Guardado
  `WorkoutViewModel.savePostExerciseFeedbacks` → `WorkoutFeedbackController` (rellena RPE de series sin intensidad con
  `backfillCompletedSetIntensityFromPostExerciseFeedback`, avanza con `advanceAfterPostExerciseFeedback`); descartar guarda 8 y
  `"none"`. Al cerrar (`WorkoutFinishController`): penalización por técnica, energía, `log.discomforts`, `postExerciseReports`,
  `stillPresentDiscomfortIds`, `PerformanceRangeStore`.
- Consumidores: `AugeRecoveryEngine.calculateMuscleDiscomfortPenaltyPct`, `AugeTtcEngine.calculateArticularBatteries`,
  `PerformanceTauLearner`, `TrainingEnergyEngine`, `ExerciseReadinessEngine.computeDiscomfortPenaltyFactor`,
  `DiscomfortAggregationEngine` (hoja de cierre), `WorkoutViewModel.persistentDiscomfortForExercise` (3 sesiones → «Aviso
  preventivo»), relator, `ProgramDetailHelpers.computeProgramDiscomforts`, `OvertrainingDetector`, la voz (escribe el mismo
  modelo).

## 4. Molestias
- Catálogo `data/models/DiscomfortCatalog.kt`: `DiscomfortSection` (hombro/brazos, columna/cuello, cadera, rodilla, tobillo/pie,
  general), `DiscomfortCatalogEntry` (`relatedMuscles`, `relatedArticular`), 18 entradas con `"none"`, `DISCOMFORT_CATALOG_BY_ID`,
  `discomfortLabel`. `ArticularBattery` SHOULDER, ELBOW, KNEE, HIP, ANKLE, CERVICAL, LUMBAR.
- No existe entidad de lesión, lado, gravedad ni historial propio. «Reportes anteriores»: `uiState.previousSessionDiscomforts`
  (del último log), el aviso persistente y las «Molestias previas relacionadas».
- Estado previo: `PreWorkoutDiscomfortSelector` + `DiscomfortSuggestionEngine` → `DailyWellbeingLog.preWorkoutDiscomforts`.
- Ningún motor relaciona molestias con volumen.

## 5. RIR/RPE
- `IntensityCarouselLogic.kt`: RPE 0…10 de 0,5 en 0,5 + «FALLO»; RIR de `max(10, target+3)` a 0 + «FALLO»; fallo planificado (RPE
  6…10, FALLO, RIR 0…4); SOLO_RM sin modo «—». `effectiveCarouselIntensityValue`.
- Tarjeta `SetInputCardV2` (`SetExecutionCard.kt`): `hasPlannedIntensityInput`, `resolveDefaultReportedIntensityMode`,
  `IntensityStepperBlock` con `WorkoutHorizontalWheelPicker`. Si no hay intensidad planificada, solo reps (se preguntaba después).
- Opcional: `intensity: Double?` en `WorkoutSetRecorder.record`. ✓ = `WorkoutRecordFab` (`canInvokeWorkoutRecordFab`), acción ligada
  con `RecordActionHolder.bind`. Validaciones: peso corporal en ASSISTED, carga ≤ 0, reps/tiempo ≤ 0; **no se exige RIR/RPE**.
- **Posible bug:** serie sin intensidad planificada → `effectiveCarouselIntensityValue` sin comprobar `hasPlannedIntensityInput`
  → RPE 0 fantasma. Confirmar con prueba.
- **Otro:** `TrainingEnergyEngine` usa `technicalQuality.coerceIn(1, 5)` sobre escala 1–10.

## 6. Anillos
`KpknTowerRings` (`ui/components/rings/`), estilos CLASSIC/LUMINOUS; ver `MAPA_anillos_estado_fichas.md`.
