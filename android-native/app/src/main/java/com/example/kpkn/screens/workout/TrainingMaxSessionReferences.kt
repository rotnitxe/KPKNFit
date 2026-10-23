package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.domain.calculations.percentLoadKg
import com.example.kpkn.domain.training.ExerciseLoadReferenceStore
import com.example.kpkn.domain.training.ExerciseTrainingMax
import com.example.kpkn.domain.training.observedApreXrmBlock

/** Keep the live occurrences coherent with the global identity captured in the program pool. */
internal fun sessionWithConfirmedTrainingMax(state: WorkoutUiState, program: Program?, source: Exercise, kg: Double): Session? {
    val query = ExerciseTrainingMax.request(source, PlanLoadReferenceKind.EXERCISE_TM)
    val manualSessions = program?.manualSessionOverrides.orEmpty().map { it.sessionId }.toSet()
    fun refresh(session: Session): Session {
        fun exercise(current: Exercise): Exercise {
            val request = ExerciseTrainingMax.request(current, PlanLoadReferenceKind.EXERCISE_TM)
            val compatible = if(program == null) current.id == source.id else query != null && request != null &&
                ExerciseLoadReferenceStore.sameIdentity(query,request)
            if(!compatible) return current
            val manual = session.id in manualSessions && current.id != source.id
            // Manual session references remain explicit. Missing runtime bases must still be hydrated.
            val referenced = if(manual && current.referenceTrainingMax != null) current else current.copy(referenceTrainingMax = kg)
            val apre = observedApreXrmBlock(current)?.setIds.orEmpty().toSet()
            return referenced.copy(sets = current.sets.mapIndexed { index,set ->
                if(manual || set.id in apre || set.loadBasis != LoadBasis.PERCENT_TM ||
                    set.completedReps != null || set.completedDuration != null || set.isEmptySlot) set
                else {
                    val weight = percentLoadKg(referenced,set) ?: return@mapIndexed set
                    fun recorded(side: String?) = state.completedSets.keys.any { key -> parseCompletedSetKey(key)?.let {
                        it.exerciseId == current.id && it.setIdx == index && (side == null || it.side == null || it.side == side)
                    } == true }
                    val leftFixed = recorded("left") || manuallyFixedCalibrationSet(state,current,index,"left")
                    val rightFixed = recorded("right") || manuallyFixedCalibrationSet(state,current,index,"right")
                    fun target(value: UnilateralTarget?, fixed: Boolean, otherFixed: Boolean): UnilateralTarget? = when {
                        fixed -> value
                        value != null -> value.copy(weight = weight)
                        current.isEffectivelyUnilateral() && otherFixed -> UnilateralTarget(weight = weight)
                        else -> null
                    }
                    set.copy(weight = if(recorded(null) || manuallyFixedCalibrationSet(state,current,index,null)) set.weight else weight,
                        leftTarget = target(set.leftTarget,leftFixed,rightFixed),
                        rightTarget = target(set.rightTarget,rightFixed,leftFixed))
                }
            })
        }
        return session.copy(exercises = session.exercises.map(::exercise),
            parts = session.parts.map { it.copy(exercises = it.exercises.map(::exercise)) },
            sessionB = session.sessionB?.let(::refresh),sessionC = session.sessionC?.let(::refresh),sessionD = session.sessionD?.let(::refresh))
    }
    return state.session?.let(::refresh)
}
