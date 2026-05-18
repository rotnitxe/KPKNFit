package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.isSimpleTemporalProgram

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
            listOf(ReplacementPersistenceScopeV2.SESSION_ONLY)
        }
    }
}
