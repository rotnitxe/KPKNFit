package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramExecutionReplacement
import com.example.kpkn.data.models.ProgramExecutionTarget
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutLog

data class ExecutionReplacementApplication(
    val session: Session,
    val replacementIds: List<String>,
)

/** Resolves one-run replacements without modifying a reusable program template. */
object ProgramExecutionReplacementEngine {
    fun applyPending(
        program: Program,
        session: Session,
        programRunId: String?,
        cycleNumber: Int?,
        weekTemplateId: String,
        weekInstanceId: String,
        variant: WeekVariant?,
    ): ExecutionReplacementApplication {
        if (programRunId.isNullOrBlank() || cycleNumber == null || session.id.isBlank()) {
            return ExecutionReplacementApplication(session, emptyList())
        }
        val candidates = program.pendingExecutionReplacements.filter { pending ->
            val target = pending.target
            target.programRunId == programRunId &&
                target.cycleNumber == cycleNumber &&
                target.weekTemplateId == weekTemplateId &&
                target.weekInstanceId == weekInstanceId &&
                target.sessionId == session.id &&
                (target.variant == null || target.variant == variant)
        }
        if (candidates.isEmpty()) return ExecutionReplacementApplication(session, emptyList())

        val appliedIds = mutableListOf<String>()
        fun replaceExercise(exercise: com.example.kpkn.data.models.Exercise): com.example.kpkn.data.models.Exercise {
            val pending = candidates.firstOrNull {
                it.exerciseId == exercise.id && it.sourceConfigurationId == exercise.catalogConfigurationId
            } ?: return exercise
            appliedIds += pending.id
            return pending.replacement.copy(id = exercise.id)
        }
        fun replaceSession(current: Session): Session = current.copy(
            exercises = current.exercises.map(::replaceExercise),
            parts = current.parts.map { part -> part.copy(exercises = part.exercises.map(::replaceExercise)) },
            sessionB = current.sessionB?.let(::replaceSession),
            sessionC = current.sessionC?.let(::replaceSession),
            sessionD = current.sessionD?.let(::replaceSession),
        )

        val updated = replaceSession(session)
        return ExecutionReplacementApplication(updated, appliedIds.distinct())
    }

    /** Remove only overlays tied to the execution that was durably logged. */
    fun consumeCompleted(program: Program, log: WorkoutLog): Program {
        val runId = log.programRunId ?: return program
        val cycle = log.cycleNumber ?: return program
        val instanceId = log.weekInstanceId ?: return program
        val remaining = program.pendingExecutionReplacements.filterNot { pending ->
            val target = pending.target
            target.programRunId == runId && target.cycleNumber == cycle &&
                target.weekInstanceId == instanceId && target.sessionId == log.sessionId
        }
        return if (remaining == program.pendingExecutionReplacements) program
        else program.copy(pendingExecutionReplacements = remaining)
    }

    fun sameOccurrence(first: ProgramExecutionTarget, second: ProgramExecutionTarget): Boolean =
        first.programRunId == second.programRunId && first.cycleNumber == second.cycleNumber &&
            first.weekTemplateId == second.weekTemplateId && first.weekInstanceId == second.weekInstanceId &&
            first.sessionId == second.sessionId && first.variant == second.variant
}
