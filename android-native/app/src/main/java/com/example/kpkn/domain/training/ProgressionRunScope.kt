package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*

/** Keep old evidence readable while making the owning execution explicit. */
internal object ProgressionRunScope {
    /** Previews use the injected seed; restarting chains it to the preceding execution. */
    fun nextRunId(program: Program, seed: String): String = "run_" + java.util.UUID.nameUUIDFromBytes(
        "${program.id}|${program.runState?.runId.orEmpty()}|$seed".toByteArray(Charsets.UTF_8),
    ).toString()

    fun archiveAudit(program: Program): List<NativeProgressionResolution> {
        val previous = program.runState?.runId ?: "legacy_${program.id}"
        val history = program.nativeProgressionAudit.map { entry ->
            entry.copy(programRunId = entry.programRunId ?: entry.identity?.programRunId ?: previous,
                identity = entry.identity?.let { it.copy(programRunId = it.programRunId ?: previous) })
        }
        return history + program.nativeProgressionProposals.map { proposal ->
            NativeProgressionResolution(proposal.proposalId, NativeProgressionResolutionStatus.EXPIRED,
                proposal.kind, proposal.createdAtMs, "La ejecución del programa se reinició; la propuesta anterior queda en el historial.",
                identity = proposal.identity.copy(programRunId = proposal.identity.programRunId ?: previous),
                sourceLogIds = proposal.sourceLogIds, programRunId = proposal.identity.programRunId ?: previous)
        }
    }
    fun archiveRecipes(program: Program): List<EffectiveWeekRecipe> {
        val previous = program.runState?.runId ?: "legacy_${program.id}"
        return program.effectiveWeekRecipes.map { entry ->
            entry.copy(programRunId = entry.programRunId ?: previous,
                appliedProposals = entry.appliedProposals.map { it.copy(programRunId = it.programRunId ?: entry.programRunId ?: previous) })
        }
    }
    fun matches(recipe: EffectiveWeekRecipe, program: Program): Boolean =
        recipe.programRunId == null || recipe.programRunId == program.runState?.runId
}
