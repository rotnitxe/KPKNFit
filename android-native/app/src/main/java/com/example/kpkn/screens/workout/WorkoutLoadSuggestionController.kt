package com.example.kpkn.screens.workout

import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import kotlinx.coroutines.CoroutineScope
import kotlin.math.roundToInt

internal data class SessionExerciseSetSnapshot(
    val setIndex: Int,
    val completedSet: CompletedSet,
)

/**
 * Contextual load suggestions, refresh, and auto-regulation weight adapter.
 */
class WorkoutLoadSuggestionController(
    private val performanceRangeStore: PerformanceRangeStore,
    private val scope: CoroutineScope,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val ports: Ports,
) {
    interface Ports {
        fun visibleExercises(state: WorkoutUiState): List<Exercise>
        fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean): Boolean
        fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int? = null): LoadModeV2
        fun canonicalExerciseKey(exercise: Exercise): String
        fun getWeightSuggestion(exercise: Exercise, setIdx: Int, activeTag: String? = null): WeightSuggestion?
        fun getExerciseHistory(exerciseDbId: String, limit: Int = 10, preferredTag: String? = null): List<ExerciseHistoryEntry>
    }

    fun getContextualLoadSuggestion(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String? = null,
        side: String? = null,
    ): WorkoutLoadSuggestionUi? {
        val state = getState()
        val key = workoutSetKey(exercise.id, setIdx, side)
        return state.loadSuggestions[key]
            ?: if (side != null) state.loadSuggestions[workoutSetKey(exercise.id, setIdx)] else null
            ?: buildLoadSuggestionForSet(exercise, setIdx, activeTag, side)
    }

    fun refreshLoadSuggestions(
        state: WorkoutUiState = getState(),
        trackPulses: Boolean = true,
    ) {
        val exercises = ports.visibleExercises(state)
        val previousSuggestions = state.loadSuggestions
        val nowMs = System.currentTimeMillis()
        val suggestions = buildMap {
            exercises.forEach { exercise ->
                exercise.sets.indices.forEach { setIdx ->
                    if (ports.isSetDone(state.completedSets, exercise.id, setIdx, exercise.isEffectivelyUnilateral())) return@forEach
                    if (exercise.isEffectivelyUnilateral()) {
                        listOf("left", "right").forEach { side ->
                            buildLoadSuggestionForSet(exercise, setIdx, state.exerciseTags[exercise.id], side)?.let { suggestion ->
                                put(workoutSetKey(exercise.id, setIdx, side), suggestion)
                            }
                        }
                    } else {
                        buildLoadSuggestionForSet(exercise, setIdx, state.exerciseTags[exercise.id], null)?.let { suggestion ->
                            put(workoutSetKey(exercise.id, setIdx), suggestion)
                        }
                    }
                }
            }
        }
        val pulseTokens = if (!trackPulses) {
            emptyMap()
        } else {
            val preserved = state.loadSuggestionPulseTokens
                .filterKeys { it in suggestions.keys }
                .filterValues { token -> isWorkoutPulseActive(token, nowMs) }
            val changed = suggestions.mapNotNull { (key, suggestion) ->
                val previous = previousSuggestions[key]
                val hasChanged = previous != null && (
                    kotlin.math.abs(previous.suggestedWeight - suggestion.suggestedWeight) >= 0.25 ||
                        previous.reason != suggestion.reason ||
                        previous.isRecalculated != suggestion.isRecalculated
                    )
                if (hasChanged) key to nowMs else null
            }.toMap()
            preserved + changed
        }
        updateState {
            it.copy(
                loadSuggestions = suggestions,
                loadSuggestionPulseTokens = pulseTokens,
            )
        }
    }

    fun getWeightSuggestionWithAutoRegulation(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String? = null,
        side: String? = null,
    ): WeightSuggestion? {
        val currentLoadMode = ports.effectiveLoadModeForExercise(exercise, setIdx)
        if (currentLoadMode == LoadModeV2.BODYWEIGHT) {
            return WeightSuggestion(
                suggestedWeight = 0.0,
                reason = "Peso corporal · progresa por reps o tiempo",
                suggestedLoadMode = LoadModeV2.BODYWEIGHT,
            )
        }

        val contextualSuggestion = getContextualLoadSuggestion(exercise, setIdx, activeTag, side)
        if (contextualSuggestion != null) {
            return WeightSuggestion(
                suggestedWeight = contextualSuggestion.suggestedWeight,
                reason = contextualSuggestion.reason,
                suggestedLoadMode = contextualSuggestion.suggestedLoadMode,
            )
        }
        val state = getState()
        val baseSuggestion = ports.getWeightSuggestion(exercise, setIdx, activeTag)
        val autoRegulation = state.currentAutoRegulation

        val sessionSets = completedSessionSetsForExercise(exercise, activeTag)
        val lastSessionSet = sessionSets.lastOrNull { it.completedSet.weight > 0.0 }
        val lastLiftedLoad = lastSessionSet?.completedSet
            ?.let { inputLoadForSuggestion(it, currentLoadMode) }
            ?.takeIf { it > 0.0 }

        val sessionAnchoredWeight = lastSessionSet?.completedSet
            ?.let { inputLoadForSuggestion(it, currentLoadMode) }
            ?.takeIf { it > 0.0 }
            ?.let { lastLoad ->
                val lastPlannedLoad = plannedWorkingWeightForSet(exercise, lastSessionSet.setIndex)
                val currentPlannedLoad = plannedWorkingWeightForSet(exercise, setIdx)
                when {
                    lastPlannedLoad != null && currentPlannedLoad != null &&
                        lastPlannedLoad > 0.0 && currentPlannedLoad > 0.0 -> {
                        lastLoad * (currentPlannedLoad / lastPlannedLoad)
                    }
                    else -> lastLoad
                }
            }

        val historyBaselineRm = ports.canonicalExerciseKey(exercise)
            .takeIf { it.isNotBlank() }
            ?.let { exerciseDbId ->
                ports.getExerciseHistory(exerciseDbId, limit = 1, preferredTag = activeTag)
                    .firstOrNull()
                    ?.e1rm
            }
            ?: exercise.reference1RM

        val firstSessionEstimatedRm = sessionSets.firstOrNull { it.completedSet.weight > 0.0 }?.completedSet?.let(::estimatedSessionCapacity)
        val sessionBestEstimatedRm = sessionSets
            .mapNotNull { estimatedSessionCapacity(it.completedSet) }
            .maxOrNull()

        val sessionImprovementFactor = listOfNotNull(historyBaselineRm, firstSessionEstimatedRm)
            .maxOrNull()
            ?.takeIf { it > 0.0 }
            ?.let { baseline ->
                val sessionBest = sessionBestEstimatedRm ?: return@let 1.0
                if (sessionBest > baseline * 1.015) {
                    (sessionBest / baseline).coerceIn(1.0, 1.08)
                } else {
                    1.0
                }
            }
            ?: 1.0

        val exerciseDbId = ports.canonicalExerciseKey(exercise)
        val performanceRangeData = performanceRangeStore.getCached(exerciseDbId)
        performanceRangeStore.prefetchIfMissing(exerciseDbId, scope)

        val plannedFallback = plannedWorkingWeightForSet(exercise, setIdx)
        val baseWorkingWeight = listOfNotNull(
            sessionAnchoredWeight,
            baseSuggestion?.suggestedWeight,
            plannedFallback,
            lastLiftedLoad,
        ).firstOrNull()?.takeIf { it > 0.0 } ?: return null

        val baseReason = when {
            sessionAnchoredWeight != null && lastSessionSet?.setIndex != null && lastSessionSet.setIndex != setIdx ->
                "Referencia de la última serie"
            sessionAnchoredWeight != null -> "Carga base de la sesión"
            baseSuggestion != null -> baseSuggestion.reason
            else -> "Del programa"
        }

        val dbInfo = EXERCISE_DATABASE_BY_ID[exerciseDbId]
        val involvedMuscleIds = dbInfo?.involvedMuscles
            ?.filter { resolveMuscleVolumeContribution(it) > 0.0 }
            ?.mapNotNull { getAugeMuscleDisplayId(it.muscle, it.emphasis) }
            ?: emptyList()

        val readinessFactor = WorkoutAutoRegulation.computeReadinessAdjustmentFactor(
            readinessNeural = state.readinessNeuralOverride,
            readinessSpinal = state.readinessSpinalOverride,
            readinessMuscular = state.readinessMuscularOverride,
            readinessPerMuscle = state.readinessMuscleOverrides,
            involvedMuscleIds = involvedMuscleIds,
        )

        val readinessReason = WorkoutAutoRegulation.buildReadinessReason(
            factor = readinessFactor,
            readinessValue = listOfNotNull(
                state.readinessNeuralOverride,
                state.readinessSpinalOverride,
                state.readinessMuscularOverride,
            ).minOrNull(),
        )

        val combinedFactor = if (autoRegulation != null &&
            autoRegulation.exerciseId == exercise.id &&
            autoRegulation.nextSetIdx == setIdx
        ) {
            autoRegulation.adjustmentFactor * readinessFactor
        } else {
            readinessFactor
        }

        val rmsAdjustedFactor = if (performanceRangeData != null && performanceRangeData.ermRms > 0) {
            val currentSessionRm = sessionBestEstimatedRm ?: 0.0
            val baselineRm = historyBaselineRm ?: 0.0
            when {
                currentSessionRm > performanceRangeData.ermMax * 1.02 -> 1.03
                currentSessionRm < performanceRangeData.ermMin * 0.98 -> 0.97
                performanceRangeData.ermRms > baselineRm -> 1.02
                else -> 1.0
            }
        } else {
            1.0
        }

        val sleepModifier = when (state.sleepQuality) {
            5    -> 1.02
            4    -> 1.00
            3    -> 0.95
            2    -> 0.90
            1    -> 0.85
            else -> 1.00
        }
        val sleepReason = when (state.sleepQuality) {
            in 1..2 -> " · Sueño bajo ${((sleepModifier - 1.0) * 100).roundToInt()}%"
            3       -> " · Sueño regular -5%"
            5       -> " · Sueño óptimo +2%"
            else    -> ""
        }

        val positiveLoadFactor = sessionImprovementFactor * rmsAdjustedFactor * sleepModifier * combinedFactor
        val rawAdjustedWeight = if (currentLoadMode == LoadModeV2.ASSISTED) {
            LoadSuggestionEngine.applyAssistedAdjustment(baseWorkingWeight, positiveLoadFactor)
        } else {
            baseWorkingWeight * positiveLoadFactor
        }
        val upperBound = maxOf(baseWorkingWeight, lastLiftedLoad ?: 0.0).let { reference ->
            if (reference > 0.0) reference * 1.12 else Double.MAX_VALUE
        }
        val adjustedWeight = LoadSuggestionEngine.roundLoad(
            rawAdjustedWeight
                .coerceAtLeast(0.0)
                .let { if (currentLoadMode == LoadModeV2.ASSISTED) it.coerceAtLeast(baseWorkingWeight) else it }
                .coerceAtMost(upperBound),
        )

        val autoRegReason = autoRegulation?.reason.orEmpty()
        val rangeIndicator = if (performanceRangeData != null) {
            val currentRm = sessionBestEstimatedRm ?: 0.0
            when {
                currentRm > performanceRangeData.ermMax -> " · Sobre rango"
                currentRm < performanceRangeData.ermMin -> " · Bajo rango"
                else -> ""
            }
        } else ""
        val reason = buildString {
            append(baseReason)
            if (sessionImprovementFactor > 1.0) {
                append(" · eRM en alza +${((sessionImprovementFactor - 1.0) * 100).roundToInt()}%")
            }
            if (autoRegReason.isNotBlank()) append(" · $autoRegReason")
            if (readinessReason.isNotBlank()) append(" · $readinessReason")
            if (sleepReason.isNotBlank()) append(sleepReason)
            append(rangeIndicator)
            when (currentLoadMode) {
                LoadModeV2.ASSISTED -> append(" · Sugerido en kg de asistencia")
                LoadModeV2.LASTRE -> append(" · Sugerido en kg de lastre")
                else -> {}
            }
        }

        return WeightSuggestion(
            suggestedWeight = adjustedWeight,
            reason = reason,
            suggestedLoadMode = currentLoadMode,
        )
    }

    fun getWarmupWorkingWeightAnchor(
        exercise: Exercise,
        activeTag: String? = null,
    ): Double? {
        val suggested = getWeightSuggestionWithAutoRegulation(
            exercise = exercise,
            setIdx = 0,
            activeTag = activeTag,
            side = null,
        )?.suggestedWeight?.takeIf { it > 0.0 }
        if (suggested != null) return suggested

        plannedWorkingWeightForSet(exercise, 0)?.takeIf { it > 0.0 }?.let { return it }

        val referenceRm = exercise.reference1RM?.takeIf { it > 0.0 }
            ?: exercise.prFor1RM?.takeIf { it.weight > 0.0 && it.reps > 0 }?.let {
                calculateHybrid1RM(it.weight, it.reps)
            }
        val plannedPercent = exercise.sets.firstOrNull()?.targetPercentageRM?.takeIf { it > 0.0 }
        if (referenceRm != null && plannedPercent != null) {
            return LoadSuggestionEngine.roundLoad(referenceRm * plannedPercent / 100.0)
        }

        completedSessionSetsForExercise(exercise, activeTag)
            .mapNotNull { estimatedSessionCapacity(it.completedSet) }
            .maxOrNull()
            ?.takeIf { it > 0.0 }
            ?.let { estimatedRm ->
                return LoadSuggestionEngine.roundLoad(estimatedRm * ((plannedPercent ?: 75.0) / 100.0))
            }

        return completedSessionSetsForExercise(exercise, activeTag)
            .firstOrNull { it.completedSet.weight > 0.0 }
            ?.completedSet
            ?.weight
            ?: exercise.consolidatedWeight?.weightKg?.takeIf { it > 0.0 }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun buildLoadSuggestionForSet(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String?,
        side: String?,
    ): WorkoutLoadSuggestionUi? {
        val currentLoadMode = ports.effectiveLoadModeForExercise(exercise, setIdx)
        if (currentLoadMode == LoadModeV2.BODYWEIGHT) return null

        val manualOverride = manualOverrideForSet(exercise.id, setIdx, side)
        val resolvedBase = determineSessionBaseWeight(exercise, setIdx, activeTag, side)
        val historySuggestion = ports.getWeightSuggestion(exercise, setIdx, activeTag)
        val baseWeight = (manualOverride ?: resolvedBase.first)?.takeIf { it > 0.0 } ?: return null
        val originalWeight = plannedWorkingWeightForSet(exercise, setIdx)
            ?: historySuggestion?.suggestedWeight
            ?: baseWeight
        val shouldRespectPlan = shouldUsePlanAsDominantBase(exercise, setIdx)

        val improvementFactor = if (shouldRespectPlan) {
            1.0
        } else {
            computeSessionImprovementAdjustment(exercise, activeTag, side)
        }
        val fatigueFactor = determineFatigueFactor(exercise, setIdx, activeTag, side)

        val exerciseHistory = completedSessionSetsForExercise(exercise, activeTag)
            .filter { it.completedSet.side == side || side == null }
            .filter { !it.completedSet.skipped }

        val bestRatio = exerciseHistory.mapNotNull { performanceRatioForSet(it.completedSet) }.maxOrNull()
        val worstRatio = exerciseHistory.mapNotNull { performanceRatioForSet(it.completedSet) }.minOrNull()
        val latestCompletedSet = exerciseHistory.lastOrNull()?.completedSet
        val latestRatio = latestCompletedSet?.let(::performanceRatioForSet)
        val severePerformanceDrop = latestCompletedSet != null && (
            latestCompletedSet.failureReason == "execution_error" ||
                (latestCompletedSet.isPartial && (latestRatio ?: 1.0) <= 0.85) ||
                (latestRatio != null && latestRatio <= 0.70)
            )
        val baseReason = when (manualOverride) {
            null -> when (resolvedBase.second) {
                WorkoutLoadSuggestionSource.MANUAL_BASE -> "Base manual de la sesión"
                WorkoutLoadSuggestionSource.HISTORY -> "Historial del usuario"
                WorkoutLoadSuggestionSource.SESSION_ERM -> "eRM de la sesión"
                WorkoutLoadSuggestionSource.PROGRAM -> "Plan de la sesión"
            }
            else -> "Override manual"
        }

        val computed = LoadSuggestionEngine.computeContextualWorkingLoad(
            baseWeight = baseWeight,
            originalWeight = originalWeight,
            fatigueFactor = fatigueFactor,
            improvementFactor = improvementFactor,
            shouldRespectPlan = shouldRespectPlan,
            hasManualOverride = manualOverride != null,
            bestRatio = bestRatio,
            worstRatio = worstRatio,
            severePerformanceDrop = severePerformanceDrop,
            isAssisted = currentLoadMode == LoadModeV2.ASSISTED,
            baseReason = baseReason,
        )

        return WorkoutLoadSuggestionUi(
            suggestedWeight = computed.suggestedWeight,
            originalWeight = computed.originalWeightRounded,
            isRecalculated = computed.isRecalculated,
            reason = computed.reason,
            source = if (manualOverride != null) WorkoutLoadSuggestionSource.MANUAL_BASE else resolvedBase.second,
            suggestedLoadMode = if (currentLoadMode == LoadModeV2.ASSISTED) {
                LoadModeV2.ASSISTED
            } else {
                historySuggestion?.suggestedLoadMode
            },
        )
    }

    private fun determineSessionBaseWeight(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String?,
        side: String?,
    ): Pair<Double?, WorkoutLoadSuggestionSource> {
        val plannedWeight = plannedWorkingWeightForSet(exercise, setIdx)?.takeIf { it > 0.0 }
        val historySuggestion = ports.getWeightSuggestion(exercise, setIdx, activeTag)
        val historyWeight = historySuggestion?.suggestedWeight?.takeIf { it > 0.0 }
        val sessionSets = completedSessionSetsForExercise(exercise, activeTag)
            .filter { !it.completedSet.skipped }
        val sideFilteredSets = if (side != null) {
            sessionSets.filter { it.completedSet.side == side }
        } else {
            sessionSets.filter { it.completedSet.side == null }
        }.ifEmpty {
            if (side == null) sessionSets else emptyList()
        }

        val manualAnchor = sideFilteredSets
            .sortedByDescending { it.setIndex }
            .firstNotNullOfOrNull { snapshot ->
                manualOverrideForSet(exercise.id, snapshot.setIndex, snapshot.completedSet.side)
            }
        if (manualAnchor != null) return manualAnchor to WorkoutLoadSuggestionSource.MANUAL_BASE

        val firstActual = sideFilteredSets.firstOrNull()
            ?.completedSet
            ?.let { completed -> inputLoadForSuggestion(completed, ports.effectiveLoadModeForExercise(exercise, setIdx)) }
            ?.takeIf { it > 0.0 }
        if (firstActual != null && setIdx > 0) {
            return firstActual to WorkoutLoadSuggestionSource.SESSION_ERM
        }

        if (plannedWeight != null) return plannedWeight to WorkoutLoadSuggestionSource.PROGRAM
        if (historyWeight != null) return historyWeight to WorkoutLoadSuggestionSource.HISTORY
        return null to WorkoutLoadSuggestionSource.PROGRAM
    }

    private fun shouldUsePlanAsDominantBase(exercise: Exercise, setIdx: Int): Boolean {
        val set = exercise.sets.getOrNull(setIdx) ?: return false
        return set.weight != null || set.consolidatedWeight != null || calculateSuggestedLoad(exercise, set) != null
    }

    private fun determineFatigueFactor(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String?,
        side: String?,
    ): Double {
        val priorCompletedCount = completedSessionSetsForExercise(exercise, activeTag)
            .asSequence()
            .filter { snapshot -> snapshot.setIndex < setIdx }
            .filter { snapshot -> !snapshot.completedSet.skipped }
            .filter { snapshot ->
                when {
                    side != null -> snapshot.completedSet.side == side
                    else -> snapshot.completedSet.side == null
                }
            }
            .count()

        return LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(priorCompletedCount)
    }

    private fun computeSessionImprovementAdjustment(
        exercise: Exercise,
        activeTag: String?,
        side: String?,
    ): Double {
        val sessionSets = completedSessionSetsForExercise(exercise, activeTag)
            .filter { snapshot -> snapshot.completedSet.side == side || side == null }
            .filter { snapshot -> !snapshot.completedSet.skipped }
        if (sessionSets.isEmpty()) return 1.0

        val historyBaselineRm = ports.canonicalExerciseKey(exercise)
            .takeIf { it.isNotBlank() }
            ?.let { exerciseDbId ->
                ports.getExerciseHistory(exerciseDbId, limit = 1, preferredTag = activeTag)
                    .firstOrNull()
                    ?.e1rm
            }
            ?: exercise.reference1RM
            ?: exercise.goal1RM

        val sessionBestEstimatedRm = sessionSets
            .mapNotNull { estimatedSessionCapacity(it.completedSet) }
            .maxOrNull()
            ?: return 1.0

        if (historyBaselineRm != null && historyBaselineRm > 0.0) {
            val ratio = sessionBestEstimatedRm / historyBaselineRm
            return when {
                ratio >= 1.025 -> ratio.coerceAtMost(1.05)
                ratio <= 0.97 -> ratio.coerceAtLeast(0.92)
                else -> 1.0
            }
        }
        return 1.0
    }

    private fun performanceRatioForSet(set: CompletedSet): Double? {
        val estimatedRm = estimatedSessionCapacity(set)?.takeIf { it > 0.0 } ?: return null
        val payload = set.recordedPayloadV3
        val actualLoad = when {
            payload?.loadInputMode == LoadModeV2.ASSISTED -> payload.assistedLoad ?: set.weight
            else -> payload?.externalLoad ?: set.weight
        }.takeIf { it > 0.0 } ?: return null
        return actualLoad / estimatedRm
    }

    private fun manualOverrideForSet(exerciseId: String, setIdx: Int, side: String? = null): Double? {
        val state = getState()
        val exact = state.manualLoadOverrides[workoutSetKey(exerciseId, setIdx, side)]
        if (exact != null) return exact
        return if (side != null) state.manualLoadOverrides[workoutSetKey(exerciseId, setIdx)] else null
    }

    private fun completedSessionSetsForExercise(
        exercise: Exercise,
        activeTag: String?,
    ): List<SessionExerciseSetSnapshot> {
        val state = getState()
        val allSessionSets = exercise.sets.indices.flatMap { setIndex ->
            listOfNotNull(
                state.completedSets[buildCompletedSetKey(exercise.id, setIndex, null)],
                state.completedSets[buildCompletedSetKey(exercise.id, setIndex, "left")],
                state.completedSets[buildCompletedSetKey(exercise.id, setIndex, "right")],
            ).map { SessionExerciseSetSnapshot(setIndex = setIndex, completedSet = it) }
        }.filter { !it.completedSet.isWarmup }

        val taggedSets = if (activeTag.isNullOrBlank()) {
            allSessionSets
        } else {
            allSessionSets.filter { snapshot -> snapshot.completedSet.tagId == activeTag }
        }

        return taggedSets.ifEmpty { allSessionSets }
    }

    private fun plannedWorkingWeightForSet(
        exercise: Exercise,
        setIdx: Int,
    ): Double? {
        val set = exercise.sets.getOrNull(setIdx) ?: return null
        return calculateSuggestedLoad(exercise, set)
            ?: set.weight
            ?: set.consolidatedWeight
            ?: exercise.consolidatedWeight?.weightKg
    }

    private fun estimatedSessionCapacity(set: CompletedSet): Double? =
        LoadSuggestionEngine.estimatedCapacity(set)

    private fun inputLoadForSuggestion(set: CompletedSet, loadMode: LoadModeV2): Double =
        LoadSuggestionEngine.inputLoad(set, loadMode)

    private fun buildCompletedSetKey(exerciseId: String, setIdx: Int, side: String?): String = when (side) {
        "left" -> "${exerciseId}_${setIdx}_L"
        "right" -> "${exerciseId}_${setIdx}_R"
        else -> "${exerciseId}_${setIdx}"
    }
}
