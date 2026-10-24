package com.example.kpkn.data.repository

import com.example.kpkn.data.models.HybridPlanningConfiguration
import com.example.kpkn.data.models.HybridSessionGoalBinding
import com.example.kpkn.data.models.HybridWeekProposal
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.multisport.HybridProgramPlanResult
import com.example.kpkn.domain.multisport.HybridProgramPlanner
import com.example.kpkn.domain.multisport.HybridWeekPlanRequest
import com.example.kpkn.domain.multisport.WorkoutActivityAccounting
import com.example.kpkn.domain.multisport.WorkoutActivitySummary
import com.example.kpkn.domain.training.ProgramStructureMutationResult
import com.example.kpkn.domain.training.ProgramStructureMutator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The replacement must become durable before true is returned; false never means saved. */
interface HybridPlanningProgramStore {
    fun snapshot(programId: String): Program?
    suspend fun replaceIfUnchanged(programId: String, expected: Program, replacement: Program): Boolean
}

private class RepositoryHybridPlanningProgramStore(
    private val repository: ProgramRepository,
) : HybridPlanningProgramStore {
    override fun snapshot(programId: String): Program? = repository.getProgramById(programId)

    override suspend fun replaceIfUnchanged(
        programId: String,
        expected: Program,
        replacement: Program,
    ): Boolean = repository.mutateProgramNow(programId) { current ->
        // Planning, hashing and serialization have already finished outside this monitor.
        if (current == expected) replacement else null
    }
}

sealed interface HybridProposalSaveResult {
    data class Saved(val proposal: HybridWeekProposal) : HybridProposalSaveResult
    data object ProgramNotFound : HybridProposalSaveResult
    data class Rejected(val planning: HybridProgramPlanResult) : HybridProposalSaveResult
    /** The source changed or the durable writer was superseded; recompute against fresh data. */
    data object Conflict : HybridProposalSaveResult
    data class StorageFailure(val cause: Exception) : HybridProposalSaveResult
}

/**
 * Non-visual integration with the existing Program JSON and durable repository writer.
 * Saves an explicitly requested proposal only: no calendar, session, log or energy mutation.
 * The repository's existing normalization still applies. Cancellation can arrive after a durable
 * commit; callers must reread the stored proposal before retrying an interrupted request.
 */
class HybridPlanningService(
    private val store: HybridPlanningProgramStore,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    constructor(repository: ProgramRepository) : this(RepositoryHybridPlanningProgramStore(repository))

    suspend fun prepareAndSave(
        programId: String,
        weekId: String,
        configuration: HybridPlanningConfiguration,
        bindings: List<HybridSessionGoalBinding>,
        searchNodeBudget: Int = HybridWeekPlanRequest.DEFAULT_SEARCH_NODE_BUDGET,
    ): HybridProposalSaveResult {
        // Detach caller-owned collections before changing dispatchers or suspending.
        val frozenConfiguration = HybridProgramPlanner.snapshotConfiguration(configuration)
        val frozenBindings = bindings.map { binding ->
            binding.copy(
                allowedWeekdays = binding.allowedWeekdays.toSet(),
                allowedPlaces = binding.allowedPlaces.toSet(),
                requiredEquipment = binding.requiredEquipment.toSet(),
            )
        }
        val prepared = withContext(calculationDispatcher) {
            val source = store.snapshot(programId) ?: return@withContext null
            val defaults = source.copy(hybridPlanning = source.hybridPlanning ?: frozenConfiguration)
            val configuredWeek = ProgramStructureMutator.updateWeek(defaults, weekId) {
                it.copy(hybridPlanningConfiguration = frozenConfiguration)
            }
            val configured = (configuredWeek as? ProgramStructureMutationResult.Success)?.program ?: defaults
            val planning = HybridProgramPlanner.prepare(configured, weekId, frozenBindings, searchNodeBudget)
            if (planning !is HybridProgramPlanResult.Ready) {
                return@withContext Prepared(source, planning, null)
            }
            val mutation = ProgramStructureMutator.updateWeek(configured, weekId) { week ->
                week.copy(hybridProposal = planning.proposal)
            }
            Prepared(source, planning, (mutation as? ProgramStructureMutationResult.Success)?.program)
        } ?: return HybridProposalSaveResult.ProgramNotFound

        val planning = prepared.planning
        if (planning !is HybridProgramPlanResult.Ready) return HybridProposalSaveResult.Rejected(planning)
        val replacement = prepared.replacement ?: return HybridProposalSaveResult.Conflict
        return try {
            val committed = withContext(Dispatchers.IO) {
                store.replaceIfUnchanged(programId, prepared.source, replacement)
            }
            if (committed) HybridProposalSaveResult.Saved(planning.proposal)
            else HybridProposalSaveResult.Conflict
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            HybridProposalSaveResult.StorageFailure(failure)
        }
    }

    private data class Prepared(
        val source: Program,
        val planning: HybridProgramPlanResult,
        val replacement: Program?,
    )
}

/** One activity per completed log identity; proposals never enter this accounting path. */
suspend fun ProgramRepository.summarizeCompletedTraining(): WorkoutActivitySummary =
    withContext(Dispatchers.Default) { WorkoutActivityAccounting.summarize(history.value) }
