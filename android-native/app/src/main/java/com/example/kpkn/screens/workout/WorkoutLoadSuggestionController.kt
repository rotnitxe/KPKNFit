package com.example.kpkn.screens.workout

import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.percentLoadReferenceKg
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.workout.BaseLoadPolicy
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.data.models.WorkoutContextProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal fun mergeIncrementalLoadSuggestions(
    previous: Map<String, WorkoutLoadSuggestionUi>,
    rebuilt: Map<String, WorkoutLoadSuggestionUi>,
    onlyExerciseId: String?,
): Map<String, WorkoutLoadSuggestionUi> {
    if (onlyExerciseId.isNullOrBlank()) return rebuilt
    val prefix = "${onlyExerciseId}_"
    return previous.filterKeys { !it.startsWith(prefix) } + rebuilt
}

internal data class SessionExerciseSetSnapshot(
    val setIndex: Int,
    val completedSet: CompletedSet,
)

/**
 * Contextual load suggestions, refresh, and auto-regulation weight adapter.
 */
class WorkoutLoadSuggestionController(
    private val performanceRangeStore: PerformanceRangeStore? = null,
    private val scope: CoroutineScope,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val ports: Ports,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val equipmentInventory: () -> com.example.kpkn.data.models.EquipmentInventory? = { null },
) {
    interface Ports {
        fun visibleExercises(state: WorkoutUiState): List<Exercise>
        fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean): Boolean
        fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int? = null): LoadModeV2
        fun canonicalExerciseKey(exercise: Exercise): String
        fun getWeightSuggestion(exercise: Exercise, setIdx: Int, activeTag: String? = null): WeightSuggestion?
        fun getExerciseHistory(exerciseDbId: String, limit: Int = 10, preferredTag: String? = null): List<ExerciseHistoryEntry>
        fun activeContextProfile(exerciseId: String): WorkoutContextProfile?
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
        onlyExerciseId: String? = null,
    ) {
        if (onlyExerciseId != null) {
            applyLoadSuggestionRefresh(state, trackPulses, onlyExerciseId)
            return
        }
        scope.launch(computeDispatcher) {
            applyLoadSuggestionRefresh(getState(), trackPulses, onlyExerciseId = null)
        }
    }

    private fun applyLoadSuggestionRefresh(
        state: WorkoutUiState,
        trackPulses: Boolean,
        onlyExerciseId: String?,
    ) {
        val exercises = ports.visibleExercises(state).let { list ->
            if (onlyExerciseId != null) list.filter { it.id == onlyExerciseId } else list
        }
        val previousSuggestions = state.loadSuggestions
        val nowMs = System.currentTimeMillis()
        val rebuilt = buildMap {
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
        val suggestions = mergeIncrementalLoadSuggestions(previousSuggestions, rebuilt, onlyExerciseId)
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
                        previous.isRecalculated != suggestion.isRecalculated ||
                        previous.suggestedLoadMode != suggestion.suggestedLoadMode
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

    /** Only authored plan values and explicitly accepted protocol/calibration proposals. */
    fun getWeightSuggestionWithAutoRegulation(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String? = null,
        side: String? = null,
    ): WeightSuggestion? = buildLoadSuggestionForSet(exercise, setIdx, activeTag, side)?.let {
        WeightSuggestion(it.suggestedWeight, it.reason, suggestedLoadMode = it.suggestedLoadMode,
            isCalibrationAssignment = it.isCalibrationAssignment)
    }

    fun getWarmupWorkingWeightAnchor(exercise: Exercise, activeTag: String? = null): Double? =
        getWeightSuggestionWithAutoRegulation(exercise, 0, activeTag)?.suggestedWeight?.takeIf { it > 0.0 }

    private fun buildLoadSuggestionForSet(
        exercise: Exercise,
        setIdx: Int,
        activeTag: String?,
        side: String?,
    ): WorkoutLoadSuggestionUi? {
        val state = getState()
        val mode = ports.effectiveLoadModeForExercise(exercise, setIdx)
        val live = calibrationLoadForSet(state, exercise, setIdx, side)
        val accepted = if (live == null) acceptedCalibrationLoad(
            state, exercise, setIdx, side, System.currentTimeMillis(), equipmentInventory()) else null
        if (live != null || accepted != null) return WorkoutLoadSuggestionUi(
            suggestedWeight = live?.weight ?: checkNotNull(accepted),
            originalWeight = live?.originalWeight ?: checkNotNull(accepted),
            reason = if (live?.calibrated == true) "Calibración del protocolo activo"
                else if (accepted != null) "Referencia de calibración aceptada" else "Carga del plan conservada",
            isRecalculated = live?.calibrated == true || accepted != null,
            suggestedLoadMode = mode,
            isCalibrationAssignment = true)
        // APRE/progression owns its native proposal and confirmation workflow.
        if (LoadSuggestionEngine.shouldDeferToNativeProgression(exercise, setIdx, side)) return null
        val set = exercise.sets.getOrNull(setIdx) ?: return null
        val manual = state.manualLoadOverrides[workoutSetKey(exercise.id, setIdx, side)]
            ?: if (side != null) state.manualLoadOverrides[workoutSetKey(exercise.id, setIdx)] else null
        val authored = calculateSuggestedLoad(exercise, set) ?: set.weight ?: set.consolidatedWeight
            ?: exercise.consolidatedWeight?.weightKg
        val value = manual ?: authored ?: if (mode == LoadModeV2.BODYWEIGHT) 0.0 else return null
        if (!value.isFinite() || value < 0.0) return null
        return WorkoutLoadSuggestionUi(
            suggestedWeight = value,
            originalWeight = authored ?: value,
            reason = if (manual != null) "Carga manual confirmada" else "Carga del plan",
            isRecalculated = false,
            source = if (manual != null) WorkoutLoadSuggestionSource.MANUAL_BASE else WorkoutLoadSuggestionSource.PROGRAM,
            suggestedLoadMode = mode)
    }
}
