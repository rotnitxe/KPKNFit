package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.sessionassistant.transformExercisesFlat

/** Resultado observable de una propuesta XRM, separado entre referencia guardada y series cambiadas. */
data class ApreXrmApplicationResult(
    val program: Program,
    val savedSourceIds: Set<String> = emptySet(),
    val changedSourceIds: Set<String> = emptySet(),
    val changedExerciseIds: Set<String> = emptySet(),
)

/** Aplica XRM de trabajo de APRE sin pasar por el perfil de TM ni reconstruir el plan. */
object ApreXrmProgression {
    fun apply(
        program: Program,
        targetWeekId: String?,
        proposals: List<AutoregulationProposal>,
        executedWeekIds: Set<String> = emptySet(),
        executedSessionIds: Set<String> = emptySet(),
    ): ApreXrmApplicationResult {
        val captures = proposals.asSequence()
            .filter { it.kind == AutoregulationProposalKind.ADJUST_XRM }
            .mapNotNull { it.apreXrm }
            .filter { validApreXrmCapture(it) && it.reference.sourceProgramId.let { source -> source == null || source == program.id } }
            .distinctBy { captureKey(it) }
            .toList()
        if (captures.isEmpty()) return ApreXrmApplicationResult(program)

        val targetWeek = targetWeekId?.let { findWeek(program, it) }
        var working = program
        val savedSourceIds = linkedSetOf<String>()
        captures.forEach { capture ->
            val compatibleIds = targetWeek?.sessions.orEmpty()
                .flatMap { it.allExercisesWithVariants() }
                .filter { isCompatibleTarget(it, capture) }
                .mapTo(linkedSetOf()) { it.id }
            val referenceIds = compatibleIds + capture.sourceExerciseId
            val hadCapture = hasSameCapture(working, capture)
            working = ExerciseLoadReferenceStore.appendCapture(working, referenceIds, capture.reference)
            if (!hadCapture && storedReference(working, capture.sourceExerciseId, capture) != null) {
                capture.reference.metadata?.sourceId?.let(savedSourceIds::add)
            }
        }

        val changedSourceIds = linkedSetOf<String>()
        val changedExerciseIds = linkedSetOf<String>()
        val protectedSessions = program.manualSessionOverrides.mapTo(linkedSetOf()) { it.sessionId } +
            executedSessionIds + program.runState?.completedSessionIds.orEmpty()
        if (targetWeekId != null && targetWeek != null && targetWeekId !in executedWeekIds) {
            working = updateWeek(working, targetWeekId) { week ->
                week.copy(sessions = week.sessions.map { session ->
                    session.transformExercisesWithVariants(protectedSessions) { original ->
                        captures.fold(original) { current, capture ->
                            val exactReference = storedReference(working, current.id, capture)
                            val exactCapture = exactReference?.let { capture.copy(reference = it) } ?: capture
                            val updated = applyApreXrmToExercise(current, exactCapture)
                            if (prescriptionSignature(updated) != prescriptionSignature(current)) {
                                capture.reference.metadata?.sourceId?.let(changedSourceIds::add)
                                changedExerciseIds += original.id
                            }
                            updated
                        }
                    }
                })
            }
        }
        return ApreXrmApplicationResult(working, savedSourceIds, changedSourceIds, changedExerciseIds)
    }

    /** Idempotencia de la misma captura; un XRM corregido del mismo evento sí crea revisión. */
    fun hasSameCapture(program: Program, capture: ApreXrmCapture): Boolean {
        val sourceId = capture.reference.metadata?.sourceId ?: return false
        val latestRevision = ExerciseLoadReferenceStore.history(program, capture.sourceExerciseId, capture.reference)
            .lastOrNull { stored -> stored.metadata?.sourceId == sourceId }
        return latestRevision != null && samePayload(latestRevision, capture.reference)
    }

    private fun storedReference(program: Program, exerciseId: String, capture: ApreXrmCapture) =
        ExerciseLoadReferenceStore.history(program, exerciseId, capture.reference)
            .lastOrNull { stored ->
                stored.metadata?.sourceId == capture.reference.metadata?.sourceId && samePayload(stored, capture.reference)
            }

    private fun isCompatibleTarget(exercise: Exercise, capture: ApreXrmCapture): Boolean {
        val sameOccurrence = exercise.id == capture.sourceExerciseId
        val sameRecipeSlot = capture.sourceRecipeDayId != null && capture.sourceRecipeSlotId != null &&
            exercise.recipeDayId == capture.sourceRecipeDayId && exercise.recipeSlotId == capture.sourceRecipeSlotId
        if (!sameOccurrence && !sameRecipeSlot) return false
        if (exercise.sets.size != 4) return false
        val block = resolveLiveApreBlock(exercise) ?: return false
        if (block.variant.xrmReps != capture.reference.repMin) return false
        val side = capture.context.side
        if ((side == null && exercise.isEffectivelyUnilateral()) ||
            (side != null && !exercise.isEffectivelyUnilateral())) return false
        return block.indices.all { index ->
            val set = exercise.sets[index]
            plannedApreContext(exercise, set, side) == capture.context &&
                set.manualLoadRequiredSides.let { required ->
                    if (side == null) required.isEmpty() else side !in required && "bilateral" !in required
                }
        }
    }

    private fun prescriptionSignature(exercise: Exercise): List<Any?> = listOf(
        exercise.trainingMode,
        exercise.sets.map { set -> listOf(set.id, set.weight, set.leftTarget?.weight, set.rightTarget?.weight) },
    )

    private fun samePayload(first: com.example.kpkn.data.protocols.PlanLoadReference,
        second: com.example.kpkn.data.protocols.PlanLoadReference): Boolean =
        first.copy(capturedAtMs = null, metadata = first.metadata?.copy(revision = 0)) ==
            second.copy(capturedAtMs = null, metadata = second.metadata?.copy(revision = 0))

    private fun captureKey(capture: ApreXrmCapture): List<Any?> = listOf(
        capture.reference.metadata?.sourceId,
        capture.sourceExerciseId,
        capture.sourcePlannedSetId,
        capture.context.key,
        capture.reference.capturedLoadKg,
        capture.incrementKg,
    )

    private fun Session.allExercisesWithVariants(): List<Exercise> = buildList {
        addAll(allExercises())
        sessionB?.let { addAll(it.allExercisesWithVariants()) }
        sessionC?.let { addAll(it.allExercisesWithVariants()) }
        sessionD?.let { addAll(it.allExercisesWithVariants()) }
    }

    private fun Session.transformExercisesWithVariants(
        protectedSessions: Set<String>,
        transform: (Exercise) -> Exercise,
    ): Session {
        if (id in protectedSessions) return this
        return transformExercisesFlat(transform).copy(
            sessionB = sessionB?.let { it.transformExercisesWithVariants(protectedSessions, transform) },
            sessionC = sessionC?.let { it.transformExercisesWithVariants(protectedSessions, transform) },
            sessionD = sessionD?.let { it.transformExercisesWithVariants(protectedSessions, transform) },
        )
    }

    private fun findWeek(program: Program, weekId: String): ProgramWeek? =
        program.macrocycles.asSequence().flatMap { it.blocks.asSequence() }
            .flatMap { it.mesocycles.asSequence() }.flatMap { it.weeks.asSequence() }
            .firstOrNull { it.id == weekId }

    private fun updateWeek(program: Program, weekId: String, transform: (ProgramWeek) -> ProgramWeek): Program =
        program.copy(macrocycles = program.macrocycles.map { macro ->
            macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.map { mesocycle ->
                    mesocycle.copy(weeks = mesocycle.weeks.map { week ->
                        if (week.id == weekId) transform(week) else week
                    })
                })
            })
        })
}
