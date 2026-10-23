package com.example.kpkn.data.repository

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.domain.training.ExerciseTrainingMax
import com.example.kpkn.domain.training.PlanMaterializer

/** Invoke inside the caller's existing mutateProgramNow transaction, after a draft is confirmed. */
fun ProgramRepository.withConfirmedExerciseReferences(
    previous: Program,
    updated: Program,
    exercises: List<Exercise>,
    nowMs: Long,
    sourceId: String,
    reason: String,
): Program {
    val oldExercises = ExerciseTrainingMax.exercises(previous).associateBy { it.id }
    var captured = updated
    var changed = false
    exercises.forEach { exercise ->
        val old = oldExercises[exercise.id]
        listOf(PlanLoadReferenceKind.EXERCISE_TM to (old?.referenceTrainingMax to exercise.referenceTrainingMax),
            PlanLoadReferenceKind.EXERCISE_1RM to (old?.reference1RM to exercise.reference1RM)).forEach { (kind, values) ->
            val (before, after) = values
            if (after == null && before != null) {
                captured = ExerciseTrainingMax.clear(captured, exercise, kind, nowMs, "$sourceId:${exercise.id}:$kind")
                changed = true
            } else if (after != null && after.isFinite() && after > 0.0 && after != before &&
                ExerciseTrainingMax.capturedKg(captured, exercise, kind) != after) {
                captured = ExerciseTrainingMax.capture(captured, exercise, kind, after, nowMs,
                    "$sourceId:${exercise.id}:$kind", reason)
                changed = true
            }
        }
    }
    if (!changed) return updated
    return rematerializeExerciseReferences(captured)
}

/** A failed rebuild throws before the enclosing mutation persists: references and prescription remain atomic. */
fun ProgramRepository.rematerializeExerciseReferences(program: Program): Program {
    val evidence = executedTrainingEvidence(program)
    val recipe = program.sourceRecipe
    if (recipe == null) return ExerciseTrainingMax.refreshPending(program, evidence.weekIds, evidence.sessionIds)
    var result = program
    val weeks = program.macrocycles.flatMap { it.blocks }.filter { it.sourceDefinitionId == recipe.id }
        .flatMap { it.mesocycles }.flatMap { it.weeks }.filterNot { it.isLoopWeek }
    weeks.forEach { week -> result = PlanMaterializer.rematerializeWeek(result, week.id, recipe,
        executedWeekIds = evidence.weekIds, executedSessionIds = evidence.sessionIds) }
    return result
}
