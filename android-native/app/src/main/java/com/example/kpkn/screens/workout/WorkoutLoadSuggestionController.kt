package com.example.kpkn.screens.workout

import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.sharedLoadConfigurationId
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.percentLoadReferenceKg
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.workout.BaseLoadPolicy
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.SharedLoadReferenceKind
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.plannedRepAnchor
import com.example.kpkn.data.models.toUnitModeV2
import com.example.kpkn.data.repository.SharedExerciseLoadReferenceRepository
import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.domain.workout.WorkoutSetLoadAdaptation
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
    private val sharedLoadReferences: SharedExerciseLoadReferenceRepository? = null,
) {
    interface Ports {
        fun visibleExercises(state: WorkoutUiState): List<Exercise>
        fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean): Boolean
        fun effectiveLoadModeForExercise(exercise: Exercise, setIdx: Int? = null): LoadModeV2
        fun canonicalExerciseKey(exercise: Exercise): String
        fun getWeightSuggestion(exercise: Exercise, setIdx: Int, activeTag: String? = null): WeightSuggestion?
        fun getExerciseHistory(exerciseDbId: String, limit: Int = 10, preferredTag: String? = null): List<ExerciseHistoryEntry>
        fun activeContextProfile(exerciseId: String): WorkoutContextProfile?
        fun lastComparableSet(
            exercise: Exercise,
            setIdx: Int,
            side: String?,
            tagId: String?,
            loadMode: LoadModeV2,
            unitMode: UnitModeV2,
        ): CompletedSet? = null

        fun loadIncrementKg(exercise: Exercise, setIdx: Int): Double? = null
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
        val shared = if (authored == null && manual == null) sharedReferenceWeight(state, exercise, setIdx, set, mode) else null
        val homologated = if (authored == null && manual == null && shared == null) {
            homologatedHistoryWeight(state, exercise, set, mode)
        } else null
        val tagId = state.activeTagsByExercise[exercise.id]?.firstOrNull() ?: exercise.workoutTagId
        val unitMode = set.unitModeV2 ?: exercise.trainingMode.toUnitModeV2()
        val previousActual = if (manual == null && mode == LoadModeV2.LOAD && unitMode == UnitModeV2.REPS) {
            ports.lastComparableSet(exercise, setIdx, side, tagId, mode, unitMode)
        } else null
        val actualLoad = previousActual?.let { LoadSuggestionEngine.inputLoad(it, mode) }
        val reduction = previousActual?.let {
            WorkoutSetLoadAdaptation.reductionPercent(it, set, side, mode, unitMode)
        }
        val adapted = if (reduction != null && actualLoad != null) {
            WorkoutSetLoadAdaptation.reducedLoadKg(actualLoad, reduction, ports.loadIncrementKg(exercise, setIdx))
        } else null
        val historical = if (actualLoad != null && previousActual != null) {
            if (previousActual.reps == (set.targetReps ?: set.plannedRepAnchor()) && reduction == null) actualLoad
            else StrengthModel.estimate1RM(previousActual).takeIf { it > 0.0 }?.let { StrengthModel.weightFor(it, set) }
        } else null
        val value = manual ?: authored ?: shared ?: homologated?.suggestedWeightKg ?: historical
            ?: if (mode == LoadModeV2.BODYWEIGHT) 0.0 else return null
        if (!value.isFinite() || value < 0.0) return null
        return WorkoutLoadSuggestionUi(
            suggestedWeight = adapted ?: value,
            originalWeight = authored ?: homologated?.ownReferenceWeightKg ?: value,
            reason = when {
                adapted != null && reduction == 20 -> "Reducir 20 % · reps bajo el mínimo o ROM incompleto"
                adapted != null && reduction == 15 -> "Reducir 15 % · esfuerzo mayor al objetivo"
                manual != null -> "Carga manual confirmada"
                authored != null -> "Carga del plan"
                shared != null -> "Referencia inteligente de la etiqueta"
                homologated != null -> "Carga homologada · referencia propia ${homologated.ownReferenceWeightKg.toTrimmedNumberString()} kg"
                historical != null -> "Último antecedente comparable de la etiqueta"
                else -> "Carga del plan"
            },
            isRecalculated = adapted != null,
            source = when {
                adapted != null -> WorkoutLoadSuggestionSource.ADAPTATION
                manual != null -> WorkoutLoadSuggestionSource.MANUAL_BASE
                authored != null -> WorkoutLoadSuggestionSource.PROGRAM
                shared != null -> WorkoutLoadSuggestionSource.SHARED_REFERENCE
                homologated != null -> WorkoutLoadSuggestionSource.HOMOLOGATED
                historical != null -> WorkoutLoadSuggestionSource.HISTORY
                else -> WorkoutLoadSuggestionSource.PROGRAM
            },
            suggestedLoadMode = mode)
    }

    private fun sharedReferenceWeight(
        state: WorkoutUiState,
        exercise: Exercise,
        setIdx: Int,
        set: com.example.kpkn.data.models.ExerciseSet,
        mode: LoadModeV2,
    ): Double? {
        val store = sharedLoadReferences ?: return null
        val configurationId = sharedLoadConfigurationId(exercise) ?: return null
        val exerciseKey = ports.canonicalExerciseKey(exercise)
        val tagId = state.activeTagsByExercise[exercise.id]?.firstOrNull() ?: exercise.workoutTagId
        val unit = set.unitModeV2 ?: exercise.trainingMode.toUnitModeV2()
        val convention = set.loadQuantityConvention.takeUnless {
            it == com.example.kpkn.data.models.LoadQuantityConvention.UNSPECIFIED
        } ?: exercise.loadQuantityConvention
        val requestedKind = if (set.loadBasis == LoadBasis.PERCENT_TM || exercise.smartLoadBasis == com.example.kpkn.data.models.SmartLoadBasis.TRAINING_MAX) {
            SharedLoadReferenceKind.TRAINING_MAX
        } else null
        val identities = store.references.value.filter { ref ->
            ref.exerciseKey == exerciseKey && ref.configurationId == configurationId && ref.tagId == tagId &&
                ref.unitMode == unit && ref.loadMode == mode && ref.quantityConvention == convention
        }
        val reference = when (requestedKind) {
            SharedLoadReferenceKind.TRAINING_MAX -> identities.firstOrNull { it.kind == requestedKind }
            else -> identities.firstOrNull { it.kind == SharedLoadReferenceKind.ESTIMATED_PR }
                ?: identities.firstOrNull { it.kind == SharedLoadReferenceKind.ONE_RM }
        } ?: return null
        val capacity = reference.suggestedValueKg()
        val projection = when (reference.kind) {
            SharedLoadReferenceKind.TRAINING_MAX -> exercise.copy(referenceTrainingMax = capacity)
            else -> exercise.copy(reference1RM = capacity)
        }
        return calculateSuggestedLoad(projection, set)
            ?: if (requestedKind == SharedLoadReferenceKind.TRAINING_MAX && set.targetPercentageRM != null) {
                (capacity * set.targetPercentageRM / 100.0).takeIf { it.isFinite() && it > 0.0 }
            } else StrengthModel.weightFor(capacity, set)
    }

    private data class HomologatedLoadSuggestion(val suggestedWeightKg: Double, val ownReferenceWeightKg: Double)

    private fun homologatedHistoryWeight(
        state: WorkoutUiState,
        exercise: Exercise,
        set: com.example.kpkn.data.models.ExerciseSet,
        mode: LoadModeV2,
    ): HomologatedLoadSuggestion? {
        if (mode != LoadModeV2.LOAD || (set.unitModeV2 ?: exercise.trainingMode.toUnitModeV2()) != UnitModeV2.REPS) return null
        val store = sharedLoadReferences ?: return null
        val destinationTagId = state.activeTagsByExercise[exercise.id]?.firstOrNull() ?: exercise.workoutTagId ?: return null
        val configurationId = sharedLoadConfigurationId(exercise) ?: return null
        val exerciseKey = ports.canonicalExerciseKey(exercise)
        val convention = set.loadQuantityConvention.takeUnless {
            it == com.example.kpkn.data.models.LoadQuantityConvention.UNSPECIFIED
        } ?: exercise.loadQuantityConvention
        val rows = store.references.value.filter { row ->
            row.kind == SharedLoadReferenceKind.OBSERVED_SESSION && row.exerciseKey == exerciseKey &&
                row.configurationId == configurationId && row.unitMode == UnitModeV2.REPS &&
                row.loadMode == LoadModeV2.LOAD && row.quantityConvention == convention && row.tagId != null
        }
        val destinationRow = rows.firstOrNull { it.tagId == destinationTagId } ?: return null
        val destination = destinationRow.comparableObservations.map {
            com.example.kpkn.domain.workout.WorkoutTagCapacityObservation(it.sessionId, it.observedAtMs, it.capacityKg)
        }
        val ownCapacity = com.example.kpkn.domain.workout.WorkoutTagLoadHomologation.destinationCapacityKg(destination) ?: return null
        val source = rows.asSequence().filter { it.tagId != destinationTagId }.mapNotNull { row ->
            val observations = row.comparableObservations.map {
                com.example.kpkn.domain.workout.WorkoutTagCapacityObservation(it.sessionId, it.observedAtMs, it.capacityKg)
            }
            val adjusted = com.example.kpkn.domain.workout.WorkoutTagLoadHomologation.adjustedDestinationCapacityKg(
                destinationTagId, row.tagId.orEmpty(), destination, observations,
            ) ?: return@mapNotNull null
            val mostRecent = observations.maxOfOrNull { it.observedAtMs } ?: return@mapNotNull null
            Triple(adjusted, mostRecent, row.tagId.orEmpty())
        }.maxByOrNull { it.second } ?: return null
        val adjustedWeight = StrengthModel.weightFor(source.first, set)?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val ownWeight = StrengthModel.weightFor(ownCapacity, set)?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return HomologatedLoadSuggestion(adjustedWeight, ownWeight)
    }
}
