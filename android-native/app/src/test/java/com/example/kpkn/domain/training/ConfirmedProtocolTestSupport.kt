package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import org.junit.Assert.*

/** Ports the protocol numeric oracles through an EXPLICIT athlete confirmation.
 * AUTO is legacy input only: every call proves no mutation before confirming.
 * PROPOSE/OFF calls remain unresolved so their existing gate assertions still run.
 */
internal fun proposeAndExplicitlyConfirmLegacyAutoForTest(
    program: Program, nextWeekId: String?, proposals: List<AutoregulationProposal>,
    recipe: TrainingPlanRecipe?, executedWeekIds: Set<String>,
    metadata: ExerciseCompositionMetadataProvider? = null,
    nowMs: Long = System.currentTimeMillis(), executedSessionIds: Set<String> = emptySet(),
): AutoregulationEvaluation {
    val pending = ProgramAutoregulationEngine.apply(program, nextWeekId, proposals, recipe,
        executedWeekIds, metadata, nowMs, executedSessionIds)
    if (program.autoregulationMode != AutoregulationMode.AUTO) return pending
    assertFalse("Legacy AUTO cannot apply without confirmation", pending.applied)
    assertEquals(program.macrocycles, pending.program.macrocycles)
    assertEquals(program.powerliftingProfile, pending.program.powerliftingProfile)
    assertEquals(program.exerciseLoadReferences, pending.program.exerciseLoadReferences)
    val confirmed = ProgramAutoregulationEngine.resolvePending(pending.program, true, metadata,
        executedWeekIds = executedWeekIds, executedSessionIds = executedSessionIds, nowMs = nowMs + 1)
    val added = confirmed.runState?.autoregulationAudit.orEmpty().drop(program.runState?.autoregulationAudit.orEmpty().size)
    return pending.copy(program = confirmed, applied = added.any { it.resolution == PendingActionResolutionStatus.APPLIED })
}
