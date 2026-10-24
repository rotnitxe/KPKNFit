package com.example.kpkn.data.repository

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.multisport.tracking.*
import com.example.kpkn.domain.training.AppClock
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.SystemAppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId

class ExternalSportCompletionConflictException : IllegalStateException("El programa cambió antes de registrar la actividad.")

@Serializable
enum class ExternalSportDurationSource { ATHLETE_REPORTED, TIMER, IMPORTED }

@Serializable
data class ExternalSportCompletionRequest(
    val logId: String,
    val programId: String,
    val sessionId: String,
    val weekId: String,
    val runId: String,
    val cycleNumber: Int,
    val weekInstanceId: String,
    val completedAtIso: String,
    /** Athlete/timer-reported actual whole activity duration, including rests. */
    val actualDurationSeconds: Long,
    val durationSource: ExternalSportDurationSource,
    val documents: List<SportTrackingDocument>,
    val notes: String? = null,
)

sealed interface ExternalSportCompletionResult {
    data class Completed(val log: WorkoutLog, val alreadyRecorded: Boolean) : ExternalSportCompletionResult
    data class Invalid(val reason: String) : ExternalSportCompletionResult
    data class InvalidDocuments(val issues: List<SportDocumentCollectionIssue>) : ExternalSportCompletionResult
    data object NotFound : ExternalSportCompletionResult
    data object Conflict : ExternalSportCompletionResult
    data class StorageFailure(val cause: Exception) : ExternalSportCompletionResult
}

/**
 * Manual finish of a real external class/field session through the existing atomic finalizer.
 * It creates one native WorkoutLog. It does not start another live session, estimate calories,
 * translate sRPE to AUGE, or replay progress when a completion is retried.
 */
class ExternalSportCompletionService(
    private val repository: ProgramRepository,
    private val registry: SportTrackingRegistry = NativeSportTrackingEngines.registry,
    private val clock: AppClock = SystemAppClock,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val json = Json { encodeDefaults = true; allowSpecialFloatingPointValues = true }

    suspend fun complete(request: ExternalSportCompletionRequest): ExternalSportCompletionResult {
        // Snapshot JSON before the first suspension so a mutable caller cannot change the write.
        val captured = json.encodeToString(request)
        return try {
            val prepared = withContext(Dispatchers.Default) {
                val input = json.decodeFromString<ExternalSportCompletionRequest>(captured)
                if (listOf(input.logId, input.programId, input.sessionId, input.weekId, input.runId, input.weekInstanceId).any(String::isBlank) ||
                    input.cycleNumber <= 0 || input.actualDurationSeconds !in 1L..Int.MAX_VALUE.toLong() * 60L
                ) return@withContext Preparation.Rejected(ExternalSportCompletionResult.Invalid("IDENTITY_OR_DURATION"))
                val instant = runCatching { Instant.parse(input.completedAtIso) }.getOrNull()
                    ?: return@withContext Preparation.Rejected(ExternalSportCompletionResult.Invalid("COMPLETION_INSTANT"))
                if (instant.isAfter(clock.now())) return@withContext Preparation.Rejected(ExternalSportCompletionResult.Invalid("FUTURE_COMPLETION"))
                val issues = registry.validateAll(input.documents, setOf(SportDocumentPurpose.RESULT, SportDocumentPurpose.OBSERVATION))
                if (issues.isNotEmpty()) return@withContext Preparation.Rejected(ExternalSportCompletionResult.InvalidDocuments(issues))
                if (input.documents.isEmpty()) return@withContext Preparation.Rejected(ExternalSportCompletionResult.Invalid("RESULT_REQUIRED"))
                val existing = repository.history.value.firstOrNull { it.id == input.logId }
                if (existing != null) {
                    val same = existing.programId == input.programId && existing.sessionId == input.sessionId &&
                        existing.programRunId == input.runId && existing.cycleNumber == input.cycleNumber &&
                        existing.weekInstanceId == input.weekInstanceId && existing.date == input.completedAtIso &&
                        existing.weekId == input.weekId && existing.actualDurationSeconds == input.actualDurationSeconds &&
                        existing.actualDurationSource == input.durationSource.name && existing.notes == input.notes &&
                        existing.sportDocuments == input.documents
                    return@withContext Preparation.Rejected(if (same) ExternalSportCompletionResult.Completed(existing, true)
                        else ExternalSportCompletionResult.Conflict)
                }
                val program = repository.getProgramById(input.programId)
                    ?: return@withContext Preparation.Rejected(ExternalSportCompletionResult.NotFound)
                val source = json.decodeFromString<Program>(json.encodeToString(program))
                val run = source.runState
                if (repository.activeProgramState.value?.programId != source.id || run == null ||
                    run.runId != input.runId || run.cycleNumber != input.cycleNumber || run.weekId != input.weekId ||
                    run.weekInstanceId != input.weekInstanceId || run.status != ProgramRunStatus.ACTIVE
                ) return@withContext Preparation.Rejected(ExternalSportCompletionResult.Conflict)
                val index = ProgramHierarchyIndex(source)
                if (!index.isValid) return@withContext Preparation.Rejected(ExternalSportCompletionResult.Invalid("HIERARCHY"))
                val location = index.locateSession(input.sessionId)
                    ?: return@withContext Preparation.Rejected(ExternalSportCompletionResult.NotFound)
                val session = location.session
                if (location.hierarchy.weekId != input.weekId || !ExternalSportSessionPolicy.isRecordable(session, registry)) {
                    return@withContext Preparation.Rejected(ExternalSportCompletionResult.Invalid("EXTERNAL_PRESCRIPTION_REQUIRED"))
                }
                val plannedSports = session.sportDocuments.mapTo(mutableSetOf()) { it.sportId }
                if (input.documents.any { it.sportId !in plannedSports }) {
                    return@withContext Preparation.Rejected(ExternalSportCompletionResult.Invalid("RESULT_SPORT_MISMATCH"))
                }
                val date = instant.atZone(zoneId).toLocalDate()
                val scheduled = ProgramCalendarEngine.project(source).scheduledDateFor(session, input.weekId)
                val log = WorkoutLog(
                    id = input.logId, programId = input.programId, sessionId = input.sessionId, sessionName = session.name,
                    date = input.completedAtIso, durationMinutes = ((input.actualDurationSeconds + 59L) / 60L).toInt(),
                    programRunId = input.runId, cycleNumber = input.cycleNumber, weekId = input.weekId,
                    weekInstanceId = input.weekInstanceId, actualDate = date.toString(), scheduledDate = scheduled?.toString(),
                    macroIndex = location.hierarchy.macroIndex, mesoIndex = location.hierarchy.globalMesoIndex,
                    notes = input.notes, sportDocuments = input.documents,
                    actualDurationSeconds = input.actualDurationSeconds, actualDurationSource = input.durationSource.name,
                )
                Preparation.Ready(source, log)
            }
            when (prepared) {
                is Preparation.Rejected -> prepared.result
                is Preparation.Ready -> {
                    repository.finalizeWorkout(prepared.log, clearOngoing = false,
                        expectedProgramForExternalCompletion = prepared.program, requireNoOngoingWorkout = true)
                    val saved = repository.history.value.single { it.id == prepared.log.id }
                    ExternalSportCompletionResult.Completed(saved, false)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: ExternalSportCompletionConflictException) { ExternalSportCompletionResult.Conflict }
        catch (failure: Exception) { ExternalSportCompletionResult.StorageFailure(failure) }
    }

    private sealed interface Preparation {
        data class Ready(val program: Program, val log: WorkoutLog) : Preparation
        data class Rejected(val result: ExternalSportCompletionResult) : Preparation
    }
}
