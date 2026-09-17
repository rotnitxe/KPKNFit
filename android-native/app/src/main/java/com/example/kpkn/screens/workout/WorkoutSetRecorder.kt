package com.example.kpkn.screens.workout

import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.AugeMetrics
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DifficultySignalV2
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SetEntryV2
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.data.models.SetTechniqueV2
import com.example.kpkn.data.models.SubTagCategory
import com.example.kpkn.data.models.TimeProgressionStrategyV3
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.buildWorkoutContextKey
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.workout.WorkoutPerformanceHomologationEngine
import com.example.kpkn.domain.workout.WorkoutTagResolver
import com.example.kpkn.domain.workout.isStackedIntensityTechnique
import com.example.kpkn.domain.workout.isVolumeReplacedTechnique
import com.example.kpkn.domain.workout.scheduledTechniquePlan
import com.example.kpkn.domain.workout.resolveScheduledTechniqueExecution
import com.example.kpkn.domain.workout.ScheduledTechniqueKind
import com.example.kpkn.domain.workout.isInlineEditorScheduledTechnique
import com.example.kpkn.domain.workout.techniqueScope
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.data.models.SessionEnergySummary
import com.example.kpkn.data.models.SetDrain
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.effectiveSupersetGroupFor
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Owns set-recording flow (V2/V3 homologation, state merge, rest/adaptive side-effects).
 * VM-specific navigation and persistence are injected via [Ports].
 */
class WorkoutSetRecorder(
    private val tryStartRecording: (String) -> Boolean,
    private val finishRecording: (String) -> Unit,
    private val evaluatedContextKeys: MutableSet<String>,
    private val repository: ProgramRepository,
    private val scope: CoroutineScope,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val ports: Ports,
) {
    interface Ports {
        fun visibleExercises(state: WorkoutUiState): List<Exercise>
        fun activeContextProfile(exerciseId: String): WorkoutContextProfile?
        fun inferUnitMode(exercise: Exercise, set: com.example.kpkn.data.models.ExerciseSet): UnitModeV2
        fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int): LoadModeV2
        fun currentBodyWeight(): Double?
        fun canonicalExerciseKey(exercise: Exercise): String
        fun inferPlannedTarget(set: com.example.kpkn.data.models.ExerciseSet, unitMode: UnitModeV2): Double?
        fun inferPlannedIntensity(set: com.example.kpkn.data.models.ExerciseSet): Double?
        suspend fun evaluateSetEntryV3(entry: SetEntryV2): WorkoutPerformanceHomologationEngine.EvaluationResult
        fun workoutStepPositions(state: WorkoutUiState): List<WorkoutStep>
        fun recomputeLiveEnergy(
            completedSets: Map<String, CompletedSet>,
            allExercises: List<Exercise>,
            settings: Settings,
        ): SessionEnergySummary
        fun computeImbalanceNotice(
            exercise: Exercise,
            setIdx: Int,
            completedSets: Map<String, CompletedSet>,
        ): String?
        fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?)
        fun persistLoadModeToProfile(exerciseId: String, loadMode: LoadModeV2)
        fun registerManualLoadOverride(exerciseId: String, setIdx: Int, side: String?, load: Double)
        /** Applies a scheduled technique's next-load prescription to the live card. */
        fun applyScheduledLoadOverride(exerciseId: String, setIdx: Int, side: String?, load: Double)
        fun refreshLoadSuggestions(state: WorkoutUiState, onlyExerciseId: String? = null)
        suspend fun persistOngoingStateAndAwait()
        fun nextSet(stopRest: Boolean = true)
        fun nextIncompleteStepAfter(state: WorkoutUiState): WorkoutStep?
        fun sessionForActiveMode(base: Session, mode: WeekVariant): Session
        fun adjustRestTimeForPace(baseSeconds: Int): Int
        fun startRestTimer(
            seconds: Int,
            advanceOnFinish: Boolean = false,
            lastSet: CompletedSet? = null,
            advancedFeedback: SetAdvancedFeedback? = null,
            kind: RestTimerKind = RestTimerKind.STANDARD,
        )
        fun computeAndStoreAutoRegulation(
            completedSet: CompletedSet,
            advanced: SetAdvancedFeedback,
            setDrain: SetDrain,
            effectiveRpe: Double,
            sessionProgress: Double,
        )
        fun updateCoachMessage(
            setDrain: SetDrain,
            sessionProgress: Double,
        )
        fun checkPaceCoachAlert()
        fun onSetRecordedMilestone(exercise: Exercise, weight: Double, reps: Int)
        fun onRecordingRejected(message: String)
    }

    suspend fun record(
        weight: Double,
        value: Double,
        intensity: Double?,
        advanced: SetAdvancedFeedback = SetAdvancedFeedback(),
        loadMode: LoadModeV2? = null,
        unitMode: UnitModeV2? = null,
        bodyWeight: Double? = null,
        side: String? = null,
        tagId: String? = null,
        setupId: String? = null,
        machineBrand: String? = null,
        amrapOverride: Boolean = false,
        setIdxOverride: Int? = null,
        expectedExerciseId: String? = null,
        expectedSetIdx: Int? = null,
        expectedSide: String? = null,
    ) {
        val state = getState()
        val allExercises = ports.visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx)
        if (exercise == null) {
            ports.onRecordingRejected("No hay ejercicio activo para registrar.")
            return
        }
        val targetSetIdx = setIdxOverride ?: state.currentSetIdx
        if (expectedExerciseId != null && expectedExerciseId != exercise.id) {
            ports.onRecordingRejected("El ejercicio cambió. Vuelve a la serie activa.")
            return
        }
        if (expectedSetIdx != null && expectedSetIdx != targetSetIdx) {
            ports.onRecordingRejected("La serie cambió. Vuelve a la serie activa.")
            return
        }
        val initialSide = if (exercise.isEffectivelyUnilateral()) side ?: expectedSide ?: "left" else null
        if (exercise.isEffectivelyUnilateral() && expectedSide != null && expectedSide != initialSide) {
            ports.onRecordingRejected("Cambia al lado activo antes de registrar.")
            return
        }
        val recordingKey = buildCompletedSetKey(exercise.id, targetSetIdx, initialSide)
        if (!tryStartRecording(recordingKey)) {
            ports.onRecordingRejected("Espera a que termine el registro anterior.")
            return
        }
        updateState { it.copy(recordingSetKey = recordingKey) }
        try {
            val plannedSet = exercise.sets.getOrNull(targetSetIdx)
            val scheduledPlan = plannedSet?.scheduledTechniquePlan()
            val amrapActive = resolveAmrapActive(
                plannedSet = plannedSet,
                requestedOverride = amrapOverride,
                explicitOverride = advanced.amrapOverride,
            )
            val activeProfile = ports.activeContextProfile(exercise.id)

            val resolvedUnitMode = unitMode ?: plannedSet?.let { ports.inferUnitMode(exercise, it) } ?: UnitModeV2.REPS
            var resolvedLoadMode = loadMode ?: ports.effectiveLoadModeForExercise(exercise, targetSetIdx)
            if (resolvedLoadMode == LoadModeV2.LASTRE && weight <= 0.0) {
                resolvedLoadMode = LoadModeV2.BODYWEIGHT
            }
            val resolvedBodyWeight = bodyWeight ?: ports.currentBodyWeight()
            val activeTagIds = state.activeTagsByExercise[exercise.id].orEmpty()
            val activeTagObj = state.userCreatedTags.values.flatten().firstOrNull { it.id in activeTagIds }
                ?: state.userCreatedTags.values.flatten().firstOrNull {
                    WorkoutTagResolver.namesMatch(it.name, state.exerciseTags[exercise.id])
                }
            val resolvedTagId = when {
                !tagId.isNullOrBlank() && WorkoutTagResolver.isInternalId(tagId) -> tagId
                activeTagObj != null -> activeTagObj.id
                !activeProfile?.tagId.isNullOrBlank() && WorkoutTagResolver.isInternalId(activeProfile?.tagId) ->
                    activeProfile?.tagId
                else -> tagId ?: activeProfile?.tagId ?: state.exerciseTags[exercise.id]
            }
            val resolvedTagName = activeTagObj?.name
                ?: state.exerciseTags[exercise.id]
                ?: tagId?.takeUnless { WorkoutTagResolver.isInternalId(it) }
            val resolvedSubTagIds = state.activeSubTagsByExercise[exercise.id].orEmpty()
            val resolvedSetupId = setupId ?: activeProfile?.setupProfileId ?: plannedSet?.defaultSetupProfileIdV3 ?: plannedSet?.setupId
            val resolvedMachineBrand = machineBrand ?: activeProfile?.machineBrand ?: plannedSet?.machineBrand
            val isUnilateralExercise = exercise.isEffectivelyUnilateral()
            val resolvedSide = if (isUnilateralExercise) initialSide else null
            val isFailedEntry = advanced.isFailedSet || advanced.executionError
            if (resolvedLoadMode != LoadModeV2.BODYWEIGHT && weight <= 0.0 && !isFailedEntry) {
                ports.onRecordingRejected("Carga inválida. Revisa el peso antes de registrar.")
                return
            }
            if (resolvedUnitMode != UnitModeV2.TIME && value <= 0.0 && !isFailedEntry) {
                ports.onRecordingRejected("Reps/tiempo inválidos. Revisa el valor antes de registrar.")
                return
            }
            val actualValue = when (resolvedUnitMode) {
                UnitModeV2.TIME -> value.coerceAtLeast(0.0)
                UnitModeV2.REPS, UnitModeV2.DISTANCE, UnitModeV2.CUSTOM -> value.coerceAtLeast(0.0)
            }
            val logicalActualValue = when (resolvedUnitMode) {
                UnitModeV2.REPS -> actualValue + ((advanced.partialReps ?: 0).coerceAtLeast(0) * 0.5)
                else -> actualValue
            }
            val plannedTarget = plannedSet?.let { ports.inferPlannedTarget(it, resolvedUnitMode) }
            val sideRepRange = when (resolvedSide) {
                "left" -> plannedSet?.leftTarget?.targetRepsRange
                    ?: plannedSet?.leftTarget?.targetReps?.takeIf { it > 0 }?.let { RepRange(it, it) }
                "right" -> plannedSet?.rightTarget?.targetRepsRange
                    ?: plannedSet?.rightTarget?.targetReps?.takeIf { it > 0 }?.let { RepRange(it, it) }
                else -> null
            }
            val plannedRepRange = sideRepRange ?: plannedSet?.effectiveRepRange()
                ?: plannedTarget?.toInt()?.takeIf { resolvedUnitMode == UnitModeV2.REPS && it > 0 }?.let { RepRange(it, it) }
            val debt = if (resolvedUnitMode == UnitModeV2.REPS && logicalActualValue >= 0) {
                evaluateRepRange(
                    actual = logicalActualValue,
                    range = plannedRepRange,
                    amrapActive = amrapActive,
                    amrapMinimum = advanced.amrapMinimumReps ?: plannedRepRange?.min,
                )?.debt ?: 0.0
            } else if (plannedTarget != null && logicalActualValue >= 0) {
                (plannedTarget - logicalActualValue).coerceAtLeast(0.0)
            } else {
                0.0
            }

            val techSubTags = state.userCreatedTags.values.flatten()
                .flatMap { tag -> tag.subTags.filter { it.id in resolvedSubTagIds && it.category in setOf(SubTagCategory.TECNICA, SubTagCategory.MARCA) } }
                .map { it.name.lowercase().replace(" ", "_") }
                .sorted()
                .joinToString("+")
                .ifBlank { null }
            val contextKey = buildWorkoutContextKey(
                exerciseId = ports.canonicalExerciseKey(exercise),
                machineBrand = resolvedMachineBrand,
                tagId = resolvedTagId,
                loadMode = resolvedLoadMode,
                unitMode = resolvedUnitMode,
                techSubTags = techSubTags,
            )

            val isFirstEvaluationInSession = contextKey !in evaluatedContextKeys.also {
                it.add(contextKey)
            }

            val actualIntensityMode = advanced.actualIntensityMode ?: when {
                advanced.reachedFailure -> IntensityMode.FAILURE
                advanced.rir != null -> IntensityMode.RIR
                amrapActive -> IntensityMode.AMRAP
                intensity != null -> IntensityMode.RPE
                plannedSet?.isFailure == true || plannedSet?.intensityMode == IntensityMode.FAILURE -> IntensityMode.FAILURE
                // LOAD is the legacy marker used by %RM prescriptions, not
                // an effort report.  Keep RM unlabelled until the athlete
                // explicitly selects RPE/RIR/FALLO.
                else -> plannedSet?.intensityMode?.takeUnless {
                    it == IntensityMode.LOAD ||
                        (exercise.trainingMode == TrainingMode.RM && plannedSet.targetPercentageRM != null)
                }
            }
            val actualIntensityValue = advanced.actualIntensityValue ?: when (actualIntensityMode) {
                IntensityMode.RIR -> advanced.rir?.toDouble()
                IntensityMode.FAILURE -> null
                IntensityMode.AMRAP -> intensity
                else -> intensity
            }
            val actualReps = if (resolvedUnitMode == UnitModeV2.TIME) 0 else actualValue.toInt().coerceAtLeast(0)
            val durationSeconds = if (resolvedUnitMode == UnitModeV2.TIME) {
                (advanced.timerElapsedSeconds ?: actualValue.toInt()).coerceAtLeast(0)
            } else {
                null
            }
            val techniques = buildList {
                if (advanced.dropSets.isNotEmpty()) add(SetTechniqueV2.DROP_SET)
                if (advanced.restPauses.isNotEmpty()) add(SetTechniqueV2.REST_PAUSE)
                if (scheduledPlan?.kind == ScheduledTechniqueKind.DROP_SET && advanced.dropSets.isEmpty()) add(SetTechniqueV2.DROP_SET)
                if (scheduledPlan?.kind == ScheduledTechniqueKind.REST_PAUSE && advanced.restPauses.isEmpty()) add(SetTechniqueV2.REST_PAUSE)
                if (advanced.isPartial) add(SetTechniqueV2.PARTIALS)
                if (advanced.reachedFailure) add(SetTechniqueV2.FAILURE)
                if (amrapActive) add(SetTechniqueV2.AMRAP)
            }
            val recordedPayload = RecordedSetPayload(
                contextProfileId = activeProfile?.id,
                exerciseId = exercise.id,
                exerciseDbId = ports.canonicalExerciseKey(exercise),
                side = resolvedSide,
                loadInputMode = resolvedLoadMode,
                unitMode = resolvedUnitMode,
                externalLoad = when (resolvedLoadMode) {
                    LoadModeV2.LOAD,
                    LoadModeV2.BODYWEIGHT,
                    LoadModeV2.LASTRE,
                    -> weight.takeIf { it > 0.0 }

                    LoadModeV2.ASSISTED -> null
                },
                assistedLoad = if (resolvedLoadMode == LoadModeV2.ASSISTED) weight.takeIf { it > 0.0 } else null,
                bodyWeightSnapshot = resolvedBodyWeight,
                completedReps = actualReps.takeIf { it > 0 },
                partialReps = advanced.partialReps,
                durationSeconds = durationSeconds,
                actualIntensityMode = actualIntensityMode,
                actualIntensityValue = actualIntensityValue,
                techniques = techniques,
                failedSet = advanced.isFailedSet || advanced.executionError,
                reachedFailure = advanced.reachedFailure,
                amrapPerformed = amrapActive,
                timerTargetSeconds = advanced.timerTargetSeconds ?: plannedSet?.targetDuration,
                timerElapsedSeconds = advanced.timerElapsedSeconds,
                failureReason = advanced.failureReason,
                executionError = advanced.executionError,
                skipped = advanced.skipped,
                superSetWithExerciseId = advanced.superSetWithExerciseId,
            )

            val resolvedBarWeightKg = if (!resolvedTagId.isNullOrBlank()) {
                com.example.kpkn.domain.workout.BaseLoadPolicy.resolvedFromProfile(activeProfile)
            } else {
                null
            }

            val entry = SetEntryV2(
                exerciseId = exercise.id,
                exerciseDbId = ports.canonicalExerciseKey(exercise),
                canonicalExerciseId = ports.canonicalExerciseKey(exercise),
                setIndex = targetSetIdx,
                loadMode = resolvedLoadMode,
                unitMode = resolvedUnitMode,
                plannedTarget = plannedTarget,
                actualValue = logicalActualValue,
                loggedLoad = weight.takeIf { it > 0.0 },
                bodyWeight = resolvedBodyWeight,
                plannedIntensity = plannedSet?.let { ports.inferPlannedIntensity(it) },
                actualIntensity = actualIntensityValue,
                debt = debt,
                failedSet = advanced.isFailedSet || advanced.executionError,
                reachedFailure = advanced.reachedFailure,
                amrapOverride = amrapActive,
                techniques = techniques,
                tagId = resolvedTagId,
                setupId = resolvedSetupId,
                machineBrand = resolvedMachineBrand,
                contextKey = contextKey,
                timeProgressionStrategy = plannedSet?.timeProgressionStrategyV3 ?: TimeProgressionStrategyV3.LOAD_THEN_TIME,
                barWeightKg = resolvedBarWeightKg,
                rom = advanced.rom,
                assistedReps = advanced.assistedReps,
                isFirstEvaluationInSession = isFirstEvaluationInSession,
            )

            val evaluation = if (
                state.featureFlags.workoutV2Homologation ||
                state.featureFlags.workoutV2LoadModes ||
                state.featureFlags.workoutV3UnifiedFlow
            ) {
                scope.async(Dispatchers.Default) { ports.evaluateSetEntryV3(entry) }.await()
            } else {
                null
            }
            val outcome = evaluation?.outcome ?: SetOutcomeV2(
                contextKey = contextKey,
                loadMode = resolvedLoadMode,
                unitMode = resolvedUnitMode,
                plannedTarget = plannedTarget,
                actualValue = logicalActualValue,
                actualIntensity = actualIntensityValue,
                debt = debt,
                failedSet = advanced.isFailedSet || advanced.executionError,
                reachedFailure = advanced.reachedFailure,
                amrapOverride = amrapActive,
                techniques = techniques,
                metricType = if (resolvedUnitMode == UnitModeV2.TIME) "TRM" else "ERM",
                metricValue = 0.0,
                estimatedRm = null,
                trm = null,
                globalPerformanceIndex = 50.0,
                contextPercentile = 50.0,
                contextEwma = 0.0,
                contextStdDev = 0.0,
                isContextPr = false,
                historyColor = HistoryColorV2.NEUTRAL,
                difficultySignal = DifficultySignalV2.MATCHED,
                suggestedNextLoad = null,
                suggestedTargetSeconds = null,
                suggestionReason = null,
                augeEquivalentLoad = WorkoutPerformanceHomologationEngine.computeNormalizedLoad(entry),
                augeEquivalentReps = logicalActualValue.roundToInt().coerceAtLeast(0),
            )

            val currentWorkoutStep = ports.workoutStepPositions(state).firstOrNull { step ->
                step.type == WorkoutStepType.WORKING_SET &&
                    step.exerciseId == exercise.id &&
                    step.setIndex == targetSetIdx &&
                    (resolvedSide == null || step.side == resolvedSide)
            }
            val amrapMinimumReps = advanced.amrapMinimumReps ?: plannedSet?.effectiveRepRange()?.min
            val amrapBelowMinimum = amrapActive &&
                amrapMinimumReps != null &&
                actualReps < amrapMinimumReps
            val recordedAfterAdvanced = applyAdvancedFeedback(
                base = CompletedSet(
                    id = UUID.randomUUID().toString(),
                    weight = outcome.augeEquivalentLoad,
                    reps = actualReps,
                    timeSeconds = durationSeconds,
                    side = resolvedSide,
                    // RIR is stored in `rir`; never mirror it into the RPE
                    // field.  RM without explicit effort remains null.
                    rpe = if (actualIntensityMode == IntensityMode.RPE) {
                        actualIntensityValue ?: intensity
                    } else {
                        null
                    },
                    actualIntensityMode = actualIntensityMode,
                    actualIntensityValue = actualIntensityValue,
                    debt = outcome.debt,
                    contextProfileId = activeProfile?.id,
                    tagId = resolvedTagId,
                    tagName = resolvedTagName,
                    subTagIds = resolvedSubTagIds,
                    setupProfileId = resolvedSetupId,
                    machineBrand = resolvedMachineBrand,
                    supersetId = exercise.supersetGroupRefOrLegacyId(),
                    supersetRoundIndex = currentWorkoutStep?.supersetRoundIndex,
                    restAfterKind = currentWorkoutStep?.restAfterKind?.name,
                    recordedPayloadV3 = recordedPayload,
                    homologatedResultV3 = evaluation?.homologated,
                    setOutcomeV2 = outcome,
                    amrapPerformed = amrapActive,
                    amrapMinimumReps = amrapMinimumReps,
                    amrapBelowMinimum = amrapBelowMinimum,
                ),
                advanced = advanced,
            )
            val scheduledDropRows = if (plannedSet?.isInlineEditorScheduledTechnique() == true &&
                scheduledPlan?.kind == ScheduledTechniqueKind.DROP_SET &&
                advanced.dropSets.isEmpty() && weight > 0.0
            ) {
                List(scheduledPlan.followUpCount) { index ->
                    com.example.kpkn.data.models.DropSetData(
                        weight = (weight - scheduledPlan.dropKg * (index + 1)).coerceAtLeast(0.0),
                        reps = scheduledPlan.followUpReps,
                    )
                }
            } else {
                emptyList()
            }
            val scheduledRestRows = if (plannedSet?.isInlineEditorScheduledTechnique() == true &&
                scheduledPlan?.kind == ScheduledTechniqueKind.REST_PAUSE &&
                advanced.restPauses.isEmpty()
            ) {
                List(scheduledPlan.followUpCount) {
                    com.example.kpkn.data.models.RestPauseData(
                        restTime = scheduledPlan.pauseSeconds,
                        reps = scheduledPlan.followUpReps,
                    )
                }
            } else {
                emptyList()
            }
            val completedSet = if (plannedSet?.isVolumeReplacedTechnique() == true) {
                recordedAfterAdvanced.copy(
                    dropSets = recordedAfterAdvanced.dropSets
                        .ifEmpty { plannedSet.dropSets }
                        .ifEmpty { scheduledDropRows },
                    restPauses = recordedAfterAdvanced.restPauses
                        .ifEmpty { plannedSet.restPauses }
                        .ifEmpty { scheduledRestRows },
                )
            } else {
                recordedAfterAdvanced
            }

            val key = buildCompletedSetKey(exercise.id, targetSetIdx, resolvedSide)
            val wasExistingSet = state.completedSets.containsKey(key)
            val existingSet = state.completedSets[key]
            if (existingSet != null && completedSetContentMatches(
                    existing = existingSet,
                    next = completedSet,
                    requestedNotes = advanced.notes,
                )
            ) {
                ports.onRecordingRejected("No hay cambios para actualizar.")
                return
            }
            val newDeviations = plannedSet?.let {
                WorkoutPlanDeviationSupport.detect(
                    exerciseId = exercise.id,
                    exerciseName = displayWorkoutExerciseName(exercise),
                    setIdx = targetSetIdx,
                    plannedSet = it,
                    actualWeight = weight.coerceAtLeast(0.0),
                    actualReps = if (resolvedUnitMode == UnitModeV2.TIME) {
                        logicalActualValue.toInt().coerceAtLeast(0)
                    } else {
                        logicalActualValue.roundToInt().coerceAtLeast(0)
                    },
                    advanced = advanced,
                    amrapMinimumReps = amrapMinimumReps,
                    suggestedWeight = outcome.suggestedNextLoad,
                )
            } ?: emptyList()

            updateState { current ->
                val mergedCompleted = current.completedSets + (key to completedSet)
                val recordedExerciseIdx = allExercises.indexOfFirst { it.id == exercise.id }
                val newEnergy = ports.recomputeLiveEnergy(
                    completedSets = mergedCompleted,
                    allExercises = allExercises,
                    settings = repository.settings.value,
                )
                current.copy(
                    completedSets = mergedCompleted,
                    // Make the recorded step the canonical cursor before
                    // resolving the next incomplete step. This matters when
                    // a pager/action closure carries an explicit set index:
                    // nextSet() must never advance from a stale cursor.
                    currentExerciseIdx = recordedExerciseIdx.takeIf { it >= 0 } ?: current.currentExerciseIdx,
                    currentSetIdx = targetSetIdx,
                    activeStepKey = currentWorkoutStep?.stepKey
                        ?: WorkoutStepRules.workingStepKey(exercise.id, targetSetIdx, resolvedSide),
                    setAdvancedFeedback = current.setAdvancedFeedback + (key to advanced),
                    planDeviations = current.planDeviations + newDeviations,
                    setJustLoggedKey = key,
                    lastSetOutcomeV2 = outcome,
                    lastHomologatedResultV3 = evaluation?.homologated,
                    imbalanceNotice = if (isUnilateralExercise) {
                        ports.computeImbalanceNotice(exercise, targetSetIdx, mergedCompleted)
                    } else {
                        null
                    },
                    liveEnergySummary = newEnergy,
                    continuityFeedbackExerciseId = pendingWorkoutFeedbackHandoffExercise(
                        visibleExercises = allExercises,
                        completedSets = mergedCompleted,
                        postExerciseFeedbackByExerciseId = current.postExerciseFeedbackByExerciseId,
                        loggedSetKey = key,
                        currentExerciseId = allExercises.getOrNull(current.currentExerciseIdx)?.id,
                    )?.id,
                )
            }
            ports.clearDraftForSet(exercise.id, targetSetIdx, resolvedSide)
            updateState { current ->
                current.copy(
                    persistedLoadModeBySet = current.persistedLoadModeBySet + (workoutSetKey(exercise.id, targetSetIdx) to resolvedLoadMode),
                    persistedLoadModeByExercise = current.persistedLoadModeByExercise + (exercise.id to resolvedLoadMode),
                )
            }
            ports.persistLoadModeToProfile(exercise.id, resolvedLoadMode)
            if (weight > 0.0) {
                ports.registerManualLoadOverride(exercise.id, targetSetIdx, resolvedSide, weight)
            }
            ports.refreshLoadSuggestions(getState(), onlyExerciseId = exercise.id)
            ports.persistOngoingStateAndAwait()

            KpknDiagnosticLogger.event(
                namespace = "workout",
                name = "set_recorded",
                fields = mapOf(
                    "sessionId" to state.session?.id,
                    "exerciseId" to exercise.id,
                    "exerciseName" to exercise.name,
                    "setIndex" to targetSetIdx,
                    "weight" to weight,
                    "value" to value,
                    "unitMode" to resolvedUnitMode.name,
                    "loadMode" to resolvedLoadMode.name,
                    "side" to resolvedSide,
                    "rpe" to completedSet.rpe,
                    "rir" to completedSet.rir,
                    "isFailure" to (completedSet.isFailure || outcome.reachedFailure),
                    "metricType" to outcome.metricType,
                    "metricValue" to outcome.metricValue,
                    "wasExistingSet" to wasExistingSet,
                    "setKey" to key,
                    "operation" to if (wasExistingSet) "replace" else "insert",
                    "actualIntensityMode" to completedSet.actualIntensityMode?.name,
                    "actualIntensityValue" to completedSet.actualIntensityValue,
                    "plannedIntensityMode" to plannedSet?.intensityMode?.name,
                    "plannedIntensityValue" to plannedSet?.let { set ->
                        set.targetPercentageRM
                            ?: set.targetRPE
                            ?: set.targetRIR?.toDouble()
                            ?: set.targetReps?.takeIf { set.isAmrap }?.toDouble()
                    },
                    "techniqueScope" to plannedSet?.techniqueScope()?.name,
                    "scheduledTechnique" to scheduledPlan?.kind?.name,
                    "scheduledPauseSeconds" to scheduledPlan?.pauseSeconds,
                    "scheduledDropKg" to scheduledPlan?.dropKg,
                    "scheduledFollowUps" to scheduledPlan?.followUpCount,
                    "effectiveRpe" to AugeFatigueEngine.getEffectiveRPE(completedSet),
                    "isFailedSet" to completedSet.isFailedSet,
                ),
            )

            val isExecutionError = advanced.isFailedSet || advanced.executionError
            val wasLastSet = targetSetIdx == exercise.sets.lastIndex

            val postUpdateCompleted = getState().completedSets
            val unilateralPendingOtherSide = isUnilateralExercise && resolvedSide != null &&
                postUpdateCompleted[buildCompletedSetKey(exercise.id, targetSetIdx, counterpartSide(resolvedSide))] == null
            val stateAfterLoggedSet = getState()
            val nextStepForRest = ports.nextIncompleteStepAfter(stateAfterLoggedSet)
            // An execution error is a recoverable state, not a completed route:
            // keep the cursor on the red card so Revertir can remove the
            // placeholder without racing the pager/rest timer.
            val techniqueStillOpen = plannedSet?.isStackedIntensityTechnique() == true && (
                (plannedSet.isDropSet && advanced.dropSets.isEmpty()) ||
                    (plannedSet.isRestPause && advanced.restPauses.isEmpty())
                )
            if (!isExecutionError && !unilateralPendingOtherSide && !wasExistingSet && !techniqueStillOpen) {
                // A legacy/one-phase scheduled set still gets its deterministic
                // next-load prescription even when the UI did not need to
                // collect explicit follow-up rows.
                val scheduledChain = scheduledPlan?.let { plan ->
                    if (plannedSet?.isInlineEditorScheduledTechnique() == true) {
                        // The inline card submits the main phase together with
                        // all follow-up rows.  At that point the cursor is on
                        // the final phase, so there is no technique load to
                        // leak into the next independent set.
                        val followUpsRecorded = when (plan.kind) {
                            ScheduledTechniqueKind.DROP_SET -> completedSet.dropSets.size
                            ScheduledTechniqueKind.REST_PAUSE -> completedSet.restPauses.size
                        }
                        (if (followUpsRecorded >= plan.followUpCount) plan.phaseCount - 1 else 0) to plan.phaseCount
                    } else {
                        fun sameKind(candidate: com.example.kpkn.data.models.ExerciseSet): Boolean =
                            candidate.isVolumeReplacedTechnique() &&
                                !candidate.isInlineEditorScheduledTechnique() &&
                                candidate.scheduledTechniquePlan()?.kind == plan.kind
                        var first = targetSetIdx
                        while (first > 0 && sameKind(exercise.sets[first - 1])) first -= 1
                        var last = targetSetIdx
                        while (last < exercise.sets.lastIndex && sameKind(exercise.sets[last + 1])) last += 1
                        (targetSetIdx - first) to (last - first + 1)
                    }
                }
                val scheduledPhase = scheduledChain?.first ?: 0
                val scheduledPhaseCount = scheduledChain?.second
                val scheduledNext = scheduledPlan?.let {
                    plannedSet?.resolveScheduledTechniqueExecution(
                        phaseIndex = scheduledPhase,
                        currentLoad = weight,
                        normalRestSeconds = exercise.restTime?.takeIf { seconds -> seconds > 0 }
                            ?: repository.settings.value.restTimerDefaultSeconds,
                        unitMode = resolvedUnitMode,
                        phaseCountOverride = scheduledPhaseCount,
                        actualIntensityMode = completedSet.actualIntensityMode,
                        actualIntensityValue = completedSet.actualIntensityValue,
                    )
                }
                val nextCanonicalIndex = targetSetIdx + 1
                if (scheduledNext?.nextLoad != null && nextCanonicalIndex < exercise.sets.size) {
                    ports.applyScheduledLoadOverride(
                        exerciseId = exercise.id,
                        setIdx = nextCanonicalIndex,
                        side = resolvedSide,
                        load = scheduledNext.nextLoad,
                    )
                }
                ports.nextSet(stopRest = false)
            }

            val baseRest = exercise.restTime?.takeIf { it > 0 } ?: repository.settings.value.restTimerDefaultSeconds
            val scheduledPhaseForRest = if (scheduledPlan != null &&
                plannedSet?.isInlineEditorScheduledTechnique() == true &&
                (completedSet.dropSets.isNotEmpty() || completedSet.restPauses.isNotEmpty())
            ) {
                scheduledPlan.phaseCount - 1
            } else {
                val plan = scheduledPlan
                if (plan == null || plannedSet?.isInlineEditorScheduledTechnique() == true) {
                    0
                } else {
                    var first = targetSetIdx
                    while (first > 0) {
                        val previous = exercise.sets[first - 1]
                        if (!previous.isVolumeReplacedTechnique() || previous.isInlineEditorScheduledTechnique() ||
                            previous.scheduledTechniquePlan()?.kind != plan.kind
                        ) break
                        first -= 1
                    }
                    targetSetIdx - first
                }
            }
            val scheduledPhaseCountForRest = if (scheduledPlan != null &&
                plannedSet?.isInlineEditorScheduledTechnique() != true
            ) {
                var first = targetSetIdx
                var last = targetSetIdx
                while (first > 0) {
                    val previous = exercise.sets[first - 1]
                    if (!previous.isVolumeReplacedTechnique() || previous.isInlineEditorScheduledTechnique() ||
                        previous.scheduledTechniquePlan()?.kind != scheduledPlan.kind
                    ) break
                    first -= 1
                }
                while (last < exercise.sets.lastIndex) {
                    val next = exercise.sets[last + 1]
                    if (!next.isVolumeReplacedTechnique() || next.isInlineEditorScheduledTechnique() ||
                        next.scheduledTechniquePlan()?.kind != scheduledPlan.kind
                    ) break
                    last += 1
                }
                last - first + 1
            } else {
                null
            }
            val scheduledRestForCurrent = scheduledPlan?.let {
                plannedSet?.resolveScheduledTechniqueExecution(
                    phaseIndex = scheduledPhaseForRest,
                    currentLoad = weight,
                    normalRestSeconds = baseRest,
                    unitMode = resolvedUnitMode,
                    phaseCountOverride = scheduledPhaseCountForRest,
                    actualIntensityMode = completedSet.actualIntensityMode,
                    actualIntensityValue = completedSet.actualIntensityValue,
                )?.restAfterSeconds
            }
            val sessionForRest = state.session?.let { ports.sessionForActiveMode(it, state.activeMode) }
            val supersetGroup = sessionForRest?.effectiveSupersetGroupFor(exercise)
            val sameSupersetRound = nextStepForRest?.supersetGroupId != null &&
                nextStepForRest.supersetGroupId == exercise.supersetGroupRefOrLegacyId() &&
                nextStepForRest.exerciseId != exercise.id &&
                nextStepForRest.supersetRoundIndex == targetSetIdx
            val restKind = when {
                unilateralPendingOtherSide -> RestTimerKind.BETWEEN_SIDES
                sameSupersetRound -> RestTimerKind.SUPERSET_INTRA
                supersetGroup != null -> RestTimerKind.SUPERSET_ROUND
                else -> RestTimerKind.STANDARD
            }
            val plannedRestForKind = when (restKind) {
                RestTimerKind.BETWEEN_SIDES -> exercise.restBetweenSidesSeconds ?: 0
                RestTimerKind.SUPERSET_INTRA -> supersetGroup?.roundRestBetweenExercises?.get(targetSetIdx)
                    ?: supersetGroup?.restBetweenExercises
                    ?: exercise.supersetRestBetween
                    ?: 0
                RestTimerKind.SUPERSET_ROUND -> supersetGroup?.roundRestAfterSuperset?.get(targetSetIdx)
                    ?: supersetGroup?.restAfterSuperset
                    ?: exercise.supersetRestAfter
                    ?: baseRest
                RestTimerKind.WARMUP -> exercise.warmupSets.getOrNull(targetSetIdx)?.restBetween ?: baseRest
                RestTimerKind.STANDARD -> {
                    val nextPlannedSet = exercise.sets.getOrNull(targetSetIdx + 1)
                    when {
                        scheduledRestForCurrent != null -> scheduledRestForCurrent
                        wasLastSet -> baseRest
                        nextPlannedSet?.isDropSet == true -> 0
                        nextPlannedSet?.isRestPause == true -> nextPlannedSet.restAfterSeconds?.takeIf { it >= 0 } ?: 15
                        else -> exercise.sets.getOrNull(targetSetIdx)?.restAfterSeconds?.takeIf { it >= 0 } ?: baseRest
                    }
                }
            }
            val dbInfo = resolveCatalogExerciseInfo(
                catalogConfigurationId = exercise.catalogConfigurationId,
                exerciseDbId = exercise.exerciseDbId,
                exerciseId = exercise.exerciseId,
                exerciseName = exercise.name,
            )
            val settings = repository.settings.value
            val augeMetrics = AugeFatigueEngine.getDynamicAugeMetrics(exercise.name, dbInfo?.equipment, dbInfo) ?: AugeMetrics()
            val augeTanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
            val densityMult = AugeFatigueEngine.getDensityMultiplierForExercise(
                supersetId = exercise.supersetGroupRefOrLegacyId(),
                restTime = plannedRestForKind,
                supersetExerciseCount = supersetGroup?.exerciseOrder?.size ?: 1,
                supersetRounds = supersetGroup?.rounds,
                supersetRestAfter = supersetGroup?.roundRestAfterSuperset?.get(targetSetIdx) ?: supersetGroup?.restAfterSuperset,
            )
            val completedWorkingUnits = getState().completedSets.keys.mapNotNull { k ->
                parseCompletedSetKey(k)?.let { "${it.exerciseId}_${it.setIdx}" }
            }.distinct().size
            val setDrain = AugeFatigueEngine.calculateSetBatteryDrain(
                set = completedSet,
                metrics = augeMetrics,
                tanks = augeTanks,
                accumulatedSets = completedWorkingUnits,
                restTime = exercise.restTime ?: settings.restTimerDefaultSeconds,
                densityMultiplier = densityMult,
            )
            val effectiveRpe = AugeFatigueEngine.getEffectiveRPE(completedSet)
            val totalSetsInSession = allExercises.sumOf { it.sets.size }
            val sessionProgress = if (totalSetsInSession > 0) {
                (completedWorkingUnits.toDouble() / totalSetsInSession).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            val exerciseType = when {
                dbInfo?.type.equals("Aislamiento", ignoreCase = true) -> WorkoutAdaptiveRest.ExerciseType.ISOLATION
                dbInfo?.type.equals("Básico", ignoreCase = true) -> WorkoutAdaptiveRest.ExerciseType.COMPOUND
                dbInfo?.involvedMuscles != null && dbInfo.involvedMuscles.count {
                    resolveMuscleVolumeContribution(it) > 0.0
                } >= 2 -> WorkoutAdaptiveRest.ExerciseType.COMPOUND
                dbInfo?.involvedMuscles != null && dbInfo.involvedMuscles.size <= 1 -> WorkoutAdaptiveRest.ExerciseType.ISOLATION
                else -> WorkoutAdaptiveRest.ExerciseType.UNKNOWN
            }
            val isSuperset = !exercise.supersetGroupRefOrLegacyId().isNullOrBlank()
            val adaptiveRest = WorkoutAdaptiveRest.compute(
                baseRestSeconds = baseRest,
                context = WorkoutAdaptiveRest.AdaptiveRestContext(
                    advanced = advanced,
                    setDrain = setDrain,
                    effectiveRpe = effectiveRpe,
                    sessionProgress = sessionProgress,
                    exerciseType = exerciseType,
                    isSuperset = isSuperset,
                ),
            )
            if (!wasExistingSet) {
                val nextPlannedSet = exercise.sets.getOrNull(targetSetIdx + 1)
                val plannedRest = when (restKind) {
                    RestTimerKind.SUPERSET_INTRA,
                    RestTimerKind.SUPERSET_ROUND,
                    RestTimerKind.BETWEEN_SIDES,
                    RestTimerKind.WARMUP,
                    -> plannedRestForKind.coerceAtLeast(0)
                    RestTimerKind.STANDARD -> when {
                        wasLastSet && nextStepForRest == null -> plannedRestForKind.coerceAtLeast(0)
                        nextPlannedSet?.isDropSet == true -> 0
                        nextPlannedSet?.isRestPause == true -> plannedRestForKind.coerceAtLeast(0)
                        else -> plannedRestForKind.coerceAtLeast(0)
                    }
                }
                val adjustedPlanned = ports.adjustRestTimeForPace(plannedRest)
                val adjustedAdaptive = if (restKind == RestTimerKind.SUPERSET_INTRA) {
                    ports.adjustRestTimeForPace(plannedRestForKind.coerceAtLeast(0))
                } else {
                    ports.adjustRestTimeForPace(adaptiveRest)
                }

                // Drop/rest-pause pauses run inside the card. Do not start the
                // originally planned between-set rest until those techniques
                // have actually been recorded.
                val techniqueStillOpen = plannedSet?.isStackedIntensityTechnique() == true && (
                    (plannedSet.isDropSet && advanced.dropSets.isEmpty()) ||
                        (plannedSet.isRestPause && advanced.restPauses.isEmpty())
                    )
                val effectivePlanned = if (techniqueStillOpen) 0 else adjustedPlanned

                val pendingSuggestion = PendingRestSuggestion(
                    plannedSeconds = effectivePlanned,
                    adaptiveSeconds = if (restKind == RestTimerKind.SUPERSET_INTRA || plannedRestForKind <= 0) {
                        adjustedAdaptive.coerceAtLeast(0)
                    } else {
                        adjustedAdaptive.coerceAtLeast(0)
                    },
                    exerciseName = displayWorkoutExerciseName(exercise),
                    exerciseId = exercise.id,
                    lastSet = completedSet,
                    advancedFeedback = advanced,
                )
                updateState { it.copy(pendingRestSuggestion = pendingSuggestion) }
                if (effectivePlanned > 0 && !(isExecutionError && !wasLastSet)) {
                    ports.startRestTimer(
                        seconds = effectivePlanned,
                        advanceOnFinish = false,
                        lastSet = completedSet,
                        advancedFeedback = advanced,
                        kind = restKind,
                    )
                }
            }

            if (isExecutionError && !wasLastSet && !getState().showPostExerciseSheet) {
                updateState {
                    it.copy(
                        showExecutionErrorDiscomfortSheet = true,
                        isRestTimerRunning = false,
                        restModalState = null,
                        pendingRestSuggestion = null,
                    )
                }
            }

            ports.computeAndStoreAutoRegulation(
                completedSet = completedSet,
                advanced = advanced,
                setDrain = setDrain,
                effectiveRpe = effectiveRpe,
                sessionProgress = sessionProgress,
            )
            val drainOverlay = buildExerciseDrainOverlayState(
                exerciseName = displayWorkoutExerciseName(exercise),
                drain = com.example.kpkn.data.models.PredictedDrain(
                    cns = setDrain.cnsDrainPct.roundToInt(),
                    muscular = setDrain.muscularDrainPct.roundToInt(),
                    spinal = setDrain.spinalDrainPct.roundToInt(),
                ),
                involvedMuscles = dbInfo?.involvedMuscles.orEmpty().ifEmpty {
                    exercise.effectiveMuscles.orEmpty()
                },
            )
            updateState { it.copy(lastDrainOverlay = drainOverlay) }
            if (amrapActive) {
                val contextPerformance = state.contextualPerformanceCache[contextKey]
                val ewma = contextPerformance?.ewma ?: 0.0
                val amrapVolume = weight * actualValue
                val calibratorMsg = if (ewma > 0 && amrapVolume > 0) {
                    val ratio = amrapVolume / ewma
                    val pct = (kotlin.math.abs(ratio - 1.0) * 100).toInt().coerceAtLeast(1)
                    when {
                        ratio > 1.10 -> "AMRAP: +$pct% vs histórico. Sugerencia: aumentar carga."
                        ratio < 0.90 -> "AMRAP: -$pct% vs histórico. Sugerencia: reducir carga."
                        else -> null
                    }
                } else {
                    null
                }
                updateState { it.copy(amrapCalibrationMessage = calibratorMsg) }
            }
            ports.updateCoachMessage(
                setDrain = setDrain,
                sessionProgress = sessionProgress,
            )
            ports.checkPaceCoachAlert()
            ports.onSetRecordedMilestone(exercise, weight, actualReps)
        } finally {
            finishRecording(recordingKey)
            updateState { current ->
                if (current.recordingSetKey == recordingKey) {
                    current.copy(recordingSetKey = null)
                } else {
                    current
                }
            }
        }
    }

    private fun buildCompletedSetKey(exerciseId: String, setIdx: Int, side: String?): String = when (side) {
        "left" -> "${exerciseId}_${setIdx}_L"
        "right" -> "${exerciseId}_${setIdx}_R"
        else -> "${exerciseId}_$setIdx"
    }

    private fun counterpartSide(side: String): String = if (side == "left") "right" else "left"

    private fun completedSetContentMatches(
        existing: CompletedSet,
        next: CompletedSet,
        requestedNotes: String?,
    ): Boolean {
        val oldPayload = existing.recordedPayloadV3
        val newPayload = next.recordedPayloadV3
        if (oldPayload != null && newPayload != null) {
            return oldPayload == newPayload &&
                existing.dropSets == next.dropSets &&
                existing.restPauses == next.restPauses &&
                existing.isPartial == next.isPartial &&
                existing.partialReps == next.partialReps &&
                existing.skipped == next.skipped &&
                existing.notes == (requestedNotes ?: existing.notes)
        }
        // Legacy logs may not carry recordedPayloadV3.  Compare every user
        // visible field that can be edited so an old set cannot accept a
        // no-op replacement merely because its compact payload is absent.
        return existing.weight == next.weight &&
            existing.side == next.side &&
            existing.reps == next.reps &&
            existing.timeSeconds == next.timeSeconds &&
            existing.rpe == next.rpe &&
            existing.rir == next.rir &&
            existing.isFailure == next.isFailure &&
            existing.isFailedSet == next.isFailedSet &&
            existing.failureReason == next.failureReason &&
            existing.actualIntensityMode == next.actualIntensityMode &&
            existing.actualIntensityValue == next.actualIntensityValue &&
            existing.amrapPerformed == next.amrapPerformed &&
            existing.amrapMinimumReps == next.amrapMinimumReps &&
            existing.amrapBelowMinimum == next.amrapBelowMinimum &&
            existing.dropSets == next.dropSets &&
            existing.restPauses == next.restPauses &&
            existing.isPartial == next.isPartial &&
            existing.partialReps == next.partialReps &&
            existing.skipped == next.skipped &&
            existing.notes == (requestedNotes ?: existing.notes)
    }
}
