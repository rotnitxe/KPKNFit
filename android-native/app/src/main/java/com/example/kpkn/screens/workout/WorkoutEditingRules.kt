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
import com.example.kpkn.data.models.toUnitModeV2
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.domain.workout.expectedSidesForSet
import com.example.kpkn.domain.workout.isSetDone
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
        if (!isSetDoneForExercise(completedSets, exercise, safeSetIdx)) return null

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

    private fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean): Boolean {
        // Fallback for callers without Exercise: keep bilateral vs paired logic.
        // Prefer overload with Exercise when available (see buildEditingState below).
        return completedSets.containsKey(buildCompletedSetKey(exerciseId, setIdx, null)) ||
            (isUnilateral &&
                completedSets.containsKey(buildCompletedSetKey(exerciseId, setIdx, "left")) &&
                completedSets.containsKey(buildCompletedSetKey(exerciseId, setIdx, "right")))
    }

    private fun isSetDoneForExercise(completedSets: Map<String, CompletedSet>, exercise: Exercise, setIdx: Int): Boolean =
        exercise.isSetDone(completedSets, setIdx)

    private fun buildCompletedSetKey(exerciseId: String, setIdx: Int, side: String?): String = when (side) {
        "left" -> "${exerciseId}_${setIdx}_L"
        "right" -> "${exerciseId}_${setIdx}_R"
        else -> "${exerciseId}_${setIdx}"
    }

    fun liveEditPersistenceScope(
        program: Program,
        sessionId: String? = null,
    ): WorkoutLiveEditPersistenceScope {
        val allSessions = program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { it.sessions }
        val sessionEditable = sessionId == null || allSessions.any { it.id == sessionId }
        return if (allSessions.isNotEmpty() && sessionEditable) {
            WorkoutLiveEditPersistenceScope.PERMANENT_ALLOWED
        } else {
            WorkoutLiveEditPersistenceScope.SESSION_ONLY
        }
    }

    fun canPersistLiveStructuralChanges(
        program: Program,
        sessionId: String? = null,
    ): Boolean = liveEditPersistenceScope(program, sessionId) == WorkoutLiveEditPersistenceScope.PERMANENT_ALLOWED

    fun replacementPersistenceOptions(
        program: Program,
        sessionId: String? = null,
    ): List<ReplacementPersistenceScopeV2> = if (canPersistLiveStructuralChanges(program, sessionId)) {
        listOf(
            ReplacementPersistenceScopeV2.SESSION_ONLY,
            ReplacementPersistenceScopeV2.PERMANENT,
        )
    } else listOf(ReplacementPersistenceScopeV2.SESSION_ONLY)

    fun unitModeForTrainingMode(mode: TrainingMode): UnitModeV2 = mode.toUnitModeV2()

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
                targetRepsRange = null,
                plannedTargetV2 = null,
                targetPercentageRM = null,
            )
            TrainingMode.DISTANCE,
            TrainingMode.CUSTOM,
            -> set.copy(
                unitModeV2 = unitMode,
                plannedTargetV2 = set.plannedTargetV2 ?: set.targetReps?.toDouble() ?: set.targetDuration?.toDouble(),
                targetReps = null,
                targetRepsRange = null,
                targetDuration = null,
                targetPercentageRM = null,
            )
            TrainingMode.RM -> set.copy(
                unitModeV2 = UnitModeV2.REPS,
                targetReps = set.targetReps ?: set.plannedTargetV2?.roundToInt(),
                targetRepsRange = null,
                targetDuration = null,
                plannedTargetV2 = null,
                targetPercentageRM = (set.targetPercentageRM ?: 75.0).coerceIn(40.0, 100.0)
                    .let { kotlin.math.round(it * 100.0) / 100.0 },
            )
            TrainingMode.SOLO_RPE -> set.copy(
                unitModeV2 = UnitModeV2.REPS,
                targetReps = null,
                targetRepsRange = null,
                targetDuration = null,
                plannedTargetV2 = null,
                targetPercentageRM = null,
            )
            TrainingMode.AMRAP -> set.copy(
                unitModeV2 = UnitModeV2.REPS,
                targetReps = set.effectiveRepRange()?.max ?: set.targetReps ?: set.plannedTargetV2?.roundToInt(),
                targetDuration = null,
                plannedTargetV2 = null,
                targetPercentageRM = null,
                purpose = set.purpose,
                intensityMode = set.intensityMode,
            )
            TrainingMode.REPS -> set.copy(
                unitModeV2 = UnitModeV2.REPS,
                targetReps = set.effectiveRepRange()?.max ?: set.targetReps ?: set.plannedTargetV2?.roundToInt(),
                targetDuration = null,
                plannedTargetV2 = null,
                targetPercentageRM = null,
                purpose = set.purpose,
            )
        }

        val intensityNormalized = if (metricNormalized.purpose.isAmrap) {
            metricNormalized.copy(
                intensityMode = IntensityMode.AMRAP,
                targetRPE = null,
                targetRIR = null,
                isFailure = false,
            )
        } else if (metricNormalized.isFailure || metricNormalized.intensityMode == IntensityMode.FAILURE) {
            metricNormalized.copy(
                intensityMode = IntensityMode.FAILURE,
                targetRPE = null,
                targetRIR = null,
                isFailure = true,
            )
        } else when (mode) {
            // RM prescrito por %RM: la intensidad de esfuerzo se reporta en vivo
            // (RPE/RIR/Fallo). No forzar LOAD ni borrar la elección del usuario.
            TrainingMode.RM -> metricNormalized.copy(
                intensityMode = when (metricNormalized.intensityMode) {
                    IntensityMode.LOAD -> null
                    IntensityMode.SOLO_RM -> null
                    else -> metricNormalized.intensityMode
                },
            )
            TrainingMode.SOLO_RPE -> metricNormalized.copy(
                intensityMode = IntensityMode.RPE,
                targetRPE = (metricNormalized.targetRPE ?: 8.0).coerceIn(1.0, 10.0),
                targetRIR = null,
                isFailure = false,
            )
            else -> metricNormalized.copy(
                intensityMode = when (metricNormalized.intensityMode) {
                    // Preserve an omitted intensity. The live set must not
                    // invent an RPE/RIR requirement during normalization.
                    null -> null
                    IntensityMode.SOLO_RM -> IntensityMode.SOLO_RM
                    else -> metricNormalized.intensityMode
                },
            )
        }

        return intensityNormalized.copy(loadModeV2 = intensityNormalized.loadModeV2 ?: LoadModeV2.LOAD)
    }
}
