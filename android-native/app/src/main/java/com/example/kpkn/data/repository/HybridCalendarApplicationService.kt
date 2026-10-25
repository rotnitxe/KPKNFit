package com.example.kpkn.data.repository

import com.example.kpkn.data.models.HybridWeekProposal
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.multisport.*
import com.example.kpkn.domain.training.AppClock
import com.example.kpkn.domain.training.SystemAppClock
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** True only after the replacement is durable. A superseded write must return false. */
interface HybridCalendarProgramStore {
    fun snapshot(programId: String): HybridCalendarSourceSnapshot?
    suspend fun replaceIfUnchanged(expected: HybridCalendarSourceSnapshot, replacement: Program): Boolean
}

private class RepositoryHybridCalendarProgramStore(
    private val repository: ProgramRepository,
) : HybridCalendarProgramStore {
    override fun snapshot(programId: String): HybridCalendarSourceSnapshot? {
        val program = repository.getProgramById(programId) ?: return null
        return HybridCalendarSourceSnapshot(
            program = program,
            activeProgram = repository.activeProgramState.value?.takeIf { it.programId == programId },
            history = repository.history.value.filter { it.programId == programId },
            hasOngoingWorkout = repository.ongoingWorkout.value?.programId == programId,
        )
    }

    override suspend fun replaceIfUnchanged(expected: HybridCalendarSourceSnapshot, replacement: Program): Boolean =
        repository.applyHybridCalendarIfUnchanged(expected, replacement)
}

sealed interface HybridCalendarSaveResult {
    data class Applied(val schedule: HybridAppliedWeekSchedule) : HybridCalendarSaveResult
    data object ProgramNotFound : HybridCalendarSaveResult
    data class Rejected(val reason: HybridCalendarRejection) : HybridCalendarSaveResult
    data object Conflict : HybridCalendarSaveResult
    data class StorageFailure(val cause: Exception) : HybridCalendarSaveResult
}

/**
 * Manual non-UI acceptance; saving a proposal never calls this service implicitly.
 * The repository adapter uses the guarded durable program writer, which rechecks program +
 * lifecycle context before replacement and coordinates workout/lifecycle publication.
 * Cancellation may arrive after commit; reread persisted metadata before retrying.
 */
class HybridCalendarApplicationService(
    private val store: HybridCalendarProgramStore,
    private val clock: AppClock = SystemAppClock,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    constructor(repository: ProgramRepository) : this(RepositoryHybridCalendarProgramStore(repository))

    suspend fun accept(
        request: HybridCalendarAcceptanceRequest,
        searchNodeBudget: Int = HybridWeekPlanRequest.DEFAULT_SEARCH_NODE_BUDGET,
    ): HybridCalendarSaveResult {
        return try {
            // Detach caller-owned proposal collections before the first suspension.
            val frozenRequest = request.copy(proposal = detach(request.proposal))
            val prepared = withContext(calculationDispatcher) {
                val source = store.snapshot(frozenRequest.programId) ?: return@withContext null
                // A detached source detects mutations of caller-owned collections during CAS.
                val frozenSource = source.copy(program = snapshotJson.decodeFromString<Program>(snapshotJson.encodeToString(source.program)))
                val now = clock.now()
                val localNow = now.atZone(zoneId)
                val result = HybridCalendarApplicator.apply(
                    frozenSource, frozenRequest, clock.today(zoneId), now.toEpochMilli(), searchNodeBudget,
                    currentMinuteOfDay = localNow.hour * 60 + localNow.minute,
                )
                frozenSource to result
            } ?: return HybridCalendarSaveResult.ProgramNotFound
            when (val result = prepared.second) {
                is HybridCalendarApplicationResult.Rejected -> HybridCalendarSaveResult.Rejected(result.reason)
                is HybridCalendarApplicationResult.Applied -> {
                    val committed = withContext(Dispatchers.IO) { store.replaceIfUnchanged(prepared.first, result.program) }
                    if (committed) HybridCalendarSaveResult.Applied(result.schedule) else HybridCalendarSaveResult.Conflict
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            HybridCalendarSaveResult.StorageFailure(failure)
        }
    }

    private companion object {
        val snapshotJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        fun detach(proposal: HybridWeekProposal): HybridWeekProposal =
            snapshotJson.decodeFromString(snapshotJson.encodeToString(proposal))
    }
}
