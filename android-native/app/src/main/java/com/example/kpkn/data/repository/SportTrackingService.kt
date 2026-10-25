package com.example.kpkn.data.repository

import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.multisport.tracking.*
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.ProgramStructureMutationResult
import com.example.kpkn.domain.training.ProgramStructureMutator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface SportTrackingSaveResult {
    data class Saved(val documents: List<SportTrackingDocument>) : SportTrackingSaveResult
    data class Invalid(val issues: List<SportDocumentCollectionIssue>) : SportTrackingSaveResult
    data object NotFound : SportTrackingSaveResult
    data object InvalidHierarchy : SportTrackingSaveResult
    data object Conflict : SportTrackingSaveResult
    data class StorageFailure(val cause: Exception) : SportTrackingSaveResult
}

/**
 * Nonvisual entry point for specialised authoring and manual results. Both retain the native
 * Session/WorkoutLog identity. Upserts preserve unrecognised documents; no second workout,
 * energy estimate or progress transition is emitted when a result is corrected.
 */
class SportTrackingService(
    private val repository: ProgramRepository,
    private val registry: SportTrackingRegistry = NativeSportTrackingEngines.registry,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val json = Json { encodeDefaults = true; allowSpecialFloatingPointValues = true }

    suspend fun upsertBaseSessionPlans(
        programId: String,
        sessionId: String,
        incoming: List<SportTrackingDocument>,
    ): SportTrackingSaveResult = protect {
        val detached = detach(incoming)
        val issues = registry.validateAll(detached, setOf(SportDocumentPurpose.PLAN))
        if (issues.isNotEmpty()) return@protect SportTrackingSaveResult.Invalid(issues)
        val prepared = withContext(calculationDispatcher) {
            val current = repository.getProgramById(programId) ?: return@withContext null
            val snapshot = json.decodeFromString<Program>(json.encodeToString(current))
            val index = ProgramHierarchyIndex(snapshot)
            if (!index.isValid) return@withContext Prepared.InvalidHierarchy
            val session = index.locateSession(sessionId)?.session ?: return@withContext null
            val merged = merge(session.sportDocuments, detached)
            val updated = ProgramStructureMutator.updateSession(snapshot, sessionId) { it.copy(sportDocuments = merged) }
            when (updated) {
                is ProgramStructureMutationResult.Success -> Prepared.Ready(snapshot, updated.program, merged)
                else -> Prepared.InvalidHierarchy
            }
        }
        when (prepared) {
            null -> SportTrackingSaveResult.NotFound
            Prepared.InvalidHierarchy -> SportTrackingSaveResult.InvalidHierarchy
            is Prepared.Ready -> {
                val saved = repository.mutateProgramNow(programId) { current ->
                    if (current == prepared.expected) prepared.replacement else null
                }
                if (saved) SportTrackingSaveResult.Saved(prepared.documents) else SportTrackingSaveResult.Conflict
            }
        }
    }

    suspend fun upsertCompletedResults(
        logId: String,
        incoming: List<SportTrackingDocument>,
    ): SportTrackingSaveResult = protect {
        val detached = detach(incoming)
        val issues = registry.validateAll(detached, setOf(SportDocumentPurpose.RESULT, SportDocumentPurpose.OBSERVATION))
        if (issues.isNotEmpty()) return@protect SportTrackingSaveResult.Invalid(issues)
        val prepared = withContext(calculationDispatcher) {
            val log = repository.history.value.singleOrNull { it.id == logId } ?: return@withContext null
            val expected = json.decodeFromString<com.example.kpkn.data.models.WorkoutLog>(json.encodeToString(log))
            expected to merge(expected.sportDocuments, detached)
        } ?: return@protect SportTrackingSaveResult.NotFound
        if (repository.replaceWorkoutSportDocumentsNow(prepared.first, prepared.second)) {
            SportTrackingSaveResult.Saved(prepared.second)
        } else SportTrackingSaveResult.Conflict
    }

    private suspend fun detach(documents: List<SportTrackingDocument>): List<SportTrackingDocument> {
        // Capture caller-owned lists/maps before the first suspension; decode on the compute lane.
        val captured = json.encodeToString(documents)
        return withContext(calculationDispatcher) { json.decodeFromString(captured) }
    }

    private fun merge(existing: List<SportTrackingDocument>, incoming: List<SportTrackingDocument>): List<SportTrackingDocument> {
        val replacements = incoming.associateBy { it.id }
        return existing.map { replacements[it.id] ?: it } + incoming.filter { row -> existing.none { it.id == row.id } }
    }

    private suspend fun protect(block: suspend () -> SportTrackingSaveResult): SportTrackingSaveResult = try {
        block()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { SportTrackingSaveResult.StorageFailure(failure) }

    private sealed interface Prepared {
        data object InvalidHierarchy : Prepared
        data class Ready(val expected: Program, val replacement: Program, val documents: List<SportTrackingDocument>) : Prepared
    }
}
