package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.isSimpleTemporalProgram
import kotlin.math.roundToInt

enum class WorkoutLiveEditPersistenceScope {
    SESSION_ONLY,
    PERMANENT_ALLOWED,
}

object WorkoutEditingRules {
    fun buildEditingState(
        completedSets: Map<String, CompletedSet>,
        exercise: Exercise?,
        setIdx: Int,
        preferredSide: String? = null,
    ): WorkoutEditingState? {
        exercise ?: return null
        val safeSetIdx = setIdx.coerceIn(0, exercise.sets.lastIndex.coerceAtLeast(0))
        val isUnilateral = exercise.isEffectivelyUnilateral()
        if (!isSetDone(completedSets, exercise.id, safeSetIdx, isUnilateral)) return null

        val resolvedSide = when {
            !isUnilateral -> null
            preferredSide != null && completedSets.containsKey(buildCompletedSetKey(exercise.id, safeSetIdx, preferredSide)) -> preferredSide
            completedSets.containsKey(buildCompletedSetKey(exercise.id, safeSetIdx, "left")) -> "left"
            completedSets.containsKey(buildCompletedSetKey(exercise.id, safeSetIdx, "right")) -> "right"
            else -> null
        }

        return WorkoutEditingState(
            setKey = workoutSetKey(exercise.id, safeSetIdx, resolvedSide),
            exerciseId = exercise.id,
            setIdx = safeSetIdx,
            side = resolvedSide,
        )
    }

    private fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean): Boolean =
        completedSets.containsKey(buildCompletedSetKey(exerciseId, setIdx, null)) ||
            (isUnilateral &&
                completedSets.containsKey(buildCompletedSetKey(exerciseId, setIdx, "left")) &&
                completedSets.containsKey(buildCompletedSetKey(exerciseId, setIdx, "right")))

    private fun buildCompletedSetKey(exerciseId: String, setIdx: Int, side: String?): String = when (side) {
        "left" -> "${exerciseId}_${setIdx}_L"
        "right" -> "${exerciseId}_${setIdx}_R"
        else -> "${exerciseId}_${setIdx}"
    }

    fun liveEditPersistenceScope(program: Program): WorkoutLiveEditPersistenceScope {
        val isSimpleCyclic = program.isSimpleTemporalProgram &&
            program.simpleProgramKind == SimpleProgramKind.CYCLIC &&
            program.calendarization?.mode != ProgramCalendarizationMode.SIMPLE_DATED
        return if (isSimpleCyclic) {
            WorkoutLiveEditPersistenceScope.PERMANENT_ALLOWED
        } else {
            WorkoutLiveEditPersistenceScope.SESSION_ONLY
        }
    }

    fun canPersistLiveStructuralChanges(program: Program): Boolean =
        liveEditPersistenceScope(program) == WorkoutLiveEditPersistenceScope.PERMANENT_ALLOWED

    fun replacementPersistenceOptions(program: Program): List<ReplacementPersistenceScopeV2> {
        return if (canPersistLiveStructuralChanges(program)) {
            listOf(
                ReplacementPersistenceScopeV2.SESSION_ONLY,
                ReplacementPersistenceScopeV2.PERMANENT,
            )
        } else {
            listOf(
                ReplacementPersistenceScopeV2.SESSION_ONLY,
                ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING,
            )
        }
    }

    fun unitModeForTrainingMode(mode: TrainingMode): UnitModeV2 = when (mode) {
        TrainingMode.TIME -> UnitModeV2.TIME
        TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
        TrainingMode.CUSTOM -> UnitModeV2.CUSTOM
        TrainingMode.REPS,
        TrainingMode.RM,
        TrainingMode.SOLO_RPE,
        TrainingMode.AMRAP,
        -> UnitModeV2.REPS
    }

    fun normalizeLiveEditedExercise(exercise: Exercise): Exercise = exercise.copy(
        sets = exercise.sets.map { set -> normalizeLiveEditedSet(exercise.trainingMode, set) },
    )

    fun normalizeLiveEditedSet(mode: TrainingMode, set: ExerciseSet): ExerciseSet {
        val unitMode = unitModeForTrainingMode(mode)
        val metricNormalized = when (mode) {
            TrainingMode.TIME -> set.copy(
                unitModeV2 = UnitModeV2.TIME,
                targetDuration = set.targetDuration ?: set.plannedTargetV2?.roundToInt(),
                targetReps = null,
                plannedTargetV2 = null,
                targetPercentageRM = null,
                isAmrap = false,
            )
            TrainingMode.DISTANCE,
            TrainingMode.CUSTOM,
            -> set.copy(
                unitModeV2 = unitMode,
                plannedTargetV2 = set.plannedTargetV2 ?: set.targetReps?.toDouble() ?: set.targetDuration?.toDouble(),
                targetReps = null,
                targetDuration = null,
                targetPercentageRM = null,
                isAmrap = false,
            )
            TrainingMode.RM -> set.copy(
                unitModeV2 = UnitModeV2.REPS,
                targetReps = set.targetReps ?: set.plannedTargetV2?.roundToInt(),
                targetDuration = null,
                plannedTargetV2 = null,
                targetPercentageRM = (set.targetPercentageRM ?: 75.0).coerceIn(40.0, 100.0),
                isAmrap = false,
            )
            TrainingMode.SOLO_RPE -> set.copy(
                unitModeV2 = UnitModeV2.REPS,
                targetReps = null,
                targetDuration = null,
                plannedTargetV2 = null,
                targetPercentageRM = null,
                isAmrap = false,
            )
            TrainingMode.AMRAP -> set.copy(
                unitModeV2 = UnitModeV2.REPS,
                targetReps = set.targetReps ?: set.plannedTargetV2?.roundToInt(),
                targetDuration = null,
                plannedTargetV2 = null,
                targetPercentageRM = null,
                isAmrap = true,
            )
            TrainingMode.REPS -> set.copy(
                unitModeV2 = UnitModeV2.REPS,
                targetReps = set.targetReps ?: set.plannedTargetV2?.roundToInt(),
                targetDuration = null,
                plannedTargetV2 = null,
                targetPercentageRM = null,
                isAmrap = false,
            )
        }

        val intensityNormalized = if (metricNormalized.isFailure || metricNormalized.intensityMode == IntensityMode.FAILURE) {
            metricNormalized.copy(
                intensityMode = IntensityMode.FAILURE,
                targetRPE = null,
                targetRIR = null,
                isFailure = true,
            )
        } else when (mode) {
            TrainingMode.RM -> metricNormalized.copy(
                intensityMode = IntensityMode.LOAD,
                targetRPE = null,
                targetRIR = null,
                isFailure = false,
            )
            TrainingMode.SOLO_RPE -> metricNormalized.copy(
                intensityMode = IntensityMode.RPE,
                targetRPE = (metricNormalized.targetRPE ?: 8.0).coerceIn(1.0, 10.0),
                targetRIR = null,
                isFailure = false,
            )
            else -> metricNormalized.copy(
                intensityMode = when (metricNormalized.intensityMode) {
                    null,
                    IntensityMode.SOLO_RM,
                    -> IntensityMode.RPE
                    else -> metricNormalized.intensityMode
                },
            )
        }

        return intensityNormalized.copy(loadModeV2 = intensityNormalized.loadModeV2 ?: LoadModeV2.LOAD)
    }
}
