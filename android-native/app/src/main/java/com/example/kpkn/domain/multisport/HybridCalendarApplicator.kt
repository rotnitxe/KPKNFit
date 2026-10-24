package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.ProgramStructureMutationResult
import com.example.kpkn.domain.training.ProgramStructureMutator
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class HybridCalendarApplicationScope {
    /** An explicit template edit before this program has ever been started. */
    BEFORE_FIRST_RUN_TEMPLATE,
    /** One persisted overlay keyed by the complete run/cycle/week-instance identity. */
    ACTIVE_WEEK_OCCURRENCE,
}

/** Accepted placement metadata: minutes are keyed by session identity, never by weekday. */
@Serializable
data class HybridAppliedWeekSchedule(
    val schemaVersion: Int = 1,
    val sourceProgramId: String,
    val sourceWeekId: String,
    val scope: HybridCalendarApplicationScope,
    val acceptedAtMs: Long,
    val proposalSnapshot: HybridWeekProposal,
    val assignments: List<HybridPlannedSession>,
    val omittedOptionalSessions: List<OptionalSessionOmission> = emptyList(),
    /** Hash of the accepted authored layout, excluding proposal/application metadata. */
    val acceptedWeekFingerprint: String = "",
)

data class HybridCalendarAcceptanceRequest(
    val programId: String,
    val weekId: String,
    val proposal: HybridWeekProposal,
    val scope: HybridCalendarApplicationScope,
    val expectedRunId: String? = null,
    val expectedCycleNumber: Int? = null,
    val expectedWeekInstanceId: String? = null,
    /** Required explicit anchor for a floating active occurrence; never inferred from today. */
    val occurrenceWeekStartDate: String? = null,
)

/** Context is read again at commit; completed logs are never edited by this feature. */
data class HybridCalendarSourceSnapshot(
    val program: Program,
    val activeProgram: ActiveProgramState? = null,
    val history: List<WorkoutLog> = emptyList(),
    val hasOngoingWorkout: Boolean = false,
)

sealed interface HybridCalendarRejection {
    data object WrongProgram : HybridCalendarRejection
    data object InvalidHierarchy : HybridCalendarRejection
    data object WeekNotFound : HybridCalendarRejection
    data object ProposalNotStored : HybridCalendarRejection
    data object StaleProposal : HybridCalendarRejection
    data class CannotRecompute(val result: HybridProgramPlanResult) : HybridCalendarRejection
    data object ProposalIntegrityMismatch : HybridCalendarRejection
    data object RunIdentityMismatch : HybridCalendarRejection
    data object AlreadyAppliedToOccurrence : HybridCalendarRejection
    data object ProgramAlreadyStarted : HybridCalendarRejection
    data object OngoingWorkout : HybridCalendarRejection
    data class CompletedSessions(val sessionIds: Set<String>) : HybridCalendarRejection
    data class MultipleOccurrencesOfSession(val sessionId: String) : HybridCalendarRejection
    data object InvalidCalendarDates : HybridCalendarRejection
    data class PastScheduledDate(val date: String) : HybridCalendarRejection
    data class PastScheduledTime(val sessionId: String) : HybridCalendarRejection
}

sealed interface HybridCalendarApplicationResult {
    data class Applied(val program: Program, val schedule: HybridAppliedWeekSchedule) : HybridCalendarApplicationResult
    data class Rejected(val reason: HybridCalendarRejection) : HybridCalendarApplicationResult
}

/**
 * Explicit acceptance of the stored, recomputed proposal. Template acceptance edits one
 * unstarted week; active acceptance appends one exact occurrence overlay. Neither operation
 * restarts a run, propagates to other weeks, rewrites prescriptions, or confirms optional sessions.
 */
object HybridCalendarApplicator {
    fun apply(
        source: HybridCalendarSourceSnapshot,
        request: HybridCalendarAcceptanceRequest,
        today: LocalDate,
        acceptedAtMs: Long,
        searchNodeBudget: Int = HybridWeekPlanRequest.DEFAULT_SEARCH_NODE_BUDGET,
        currentMinuteOfDay: Int? = null,
    ): HybridCalendarApplicationResult {
        fun reject(reason: HybridCalendarRejection) = HybridCalendarApplicationResult.Rejected(reason)
        val program = source.program
        if (request.programId.isBlank() || request.programId != program.id ||
            request.proposal.sourceProgramId != program.id
        ) return reject(HybridCalendarRejection.WrongProgram)
        val hierarchy = ProgramHierarchyIndex(program)
        if (!hierarchy.isValid || !hasUnambiguousSessionIdentities(program)) {
            return reject(HybridCalendarRejection.InvalidHierarchy)
        }
        val week = hierarchy.locateWeek(request.weekId)?.week
            ?: return reject(HybridCalendarRejection.WeekNotFound)
        val occurrence = if (request.scope == HybridCalendarApplicationScope.ACTIVE_WEEK_OCCURRENCE) {
            val run = program.runState
            if (run == null || request.expectedRunId.isNullOrBlank() ||
                request.expectedRunId != run.runId || request.expectedCycleNumber != run.cycleNumber ||
                request.expectedWeekInstanceId.isNullOrBlank() || request.expectedWeekInstanceId != run.weekInstanceId ||
                run.weekId != request.weekId || HybridOccurrenceResolver.currentIdentity(program) == null ||
                source.activeProgram?.let { active ->
                    active.programId != program.id || active.programRunId?.let { it != run.runId } == true ||
                        active.currentWeekId.takeIf(String::isNotBlank)?.let {
                            ProgramProgressEngine.templateWeekIdFromInstance(it) != request.weekId
                        } == true ||
                        active.currentCycleNumber?.let { it != run.cycleNumber } == true ||
                        active.currentWeekInstanceId?.let { it != run.weekInstanceId } == true ||
                        active.status == ProgramStatus.COMPLETED
                } == true
            ) return reject(HybridCalendarRejection.RunIdentityMismatch)
            HybridOccurrenceIdentity(run.runId, run.cycleNumber, run.weekInstanceId!!, request.weekId)
        } else null
        // An accidentally supplied occurrence must never silently fall back to a template edit.
        if (occurrence == null && (request.expectedRunId != null || request.expectedCycleNumber != null ||
                request.expectedWeekInstanceId != null || request.occurrenceWeekStartDate != null)) {
            return reject(HybridCalendarRejection.RunIdentityMismatch)
        }
        val sessionIds = week.sessions.mapTo(mutableSetOf()) { it.id }
        val applicableLogs = if (occurrence == null) source.history else ProgramProgressEngine.logsForInstance(
            source.history, program.id, occurrence.weekInstanceId, occurrence.cycleNumber, occurrence.runId,
        ) + source.history.filter { it.programId == program.id && it.programRunId == null &&
            it.weekId == null && it.weekInstanceId == null }
        val completed = applicableLogs.filter { it.programId == program.id && it.sessionId in sessionIds }
            .mapTo(mutableSetOf()) { it.sessionId } + program.runState?.completedSessionIds.orEmpty().intersect(sessionIds)
        if (completed.isNotEmpty()) return reject(HybridCalendarRejection.CompletedSessions(completed))
        if (source.hasOngoingWorkout) return reject(HybridCalendarRejection.OngoingWorkout)
        if (occurrence == null && (program.runState != null || source.activeProgram?.programId == program.id ||
            source.history.any { it.programId == program.id }
        )) return reject(HybridCalendarRejection.ProgramAlreadyStarted)
        if (occurrence != null && program.hybridOccurrenceSchedules.any { it.programId == program.id && it.identity == occurrence }) {
            return reject(HybridCalendarRejection.AlreadyAppliedToOccurrence)
        }
        if (week.hybridProposal != request.proposal) return reject(HybridCalendarRejection.ProposalNotStored)
        if (!HybridProgramPlanner.isCurrent(program, request.weekId, request.proposal)) {
            return reject(HybridCalendarRejection.StaleProposal)
        }
        val recomputed = HybridProgramPlanner.prepare(program, request.weekId, request.proposal.bindings, searchNodeBudget)
        if (recomputed !is HybridProgramPlanResult.Ready) {
            return reject(HybridCalendarRejection.CannotRecompute(recomputed))
        }
        // Freshness alone deliberately says nothing about the integrity of stored assignments.
        // Compare the complete canonical proposal, including bindings and omission reasons.
        if (recomputed.proposal != request.proposal) return reject(HybridCalendarRejection.ProposalIntegrityMismatch)
        val assignments = recomputed.proposal.assignments
        week.sessions.firstOrNull { session ->
            (session.assignedDays + listOfNotNull(session.dayOfWeek)).distinct().size > 1
        }?.let { return reject(HybridCalendarRejection.MultipleOccurrencesOfSession(it.id)) }

        val looksDated = program.calendarization != null || program.resolvedSchedulePlan().mode == ScheduleMode.DATED ||
            program.resolvedSchedulePlan().anchorDate != null || week.startDate != null || week.endDate != null ||
            week.trainingDayDates.isNotEmpty()
        val dates = if (looksDated || occurrence != null) {
            if (looksDated && !ProgramCalendarEngine.isCalendarized(program)) return reject(HybridCalendarRejection.InvalidCalendarDates)
            val declaredDateLimits = listOfNotNull(program.calendarization?.manualEndDate, program.resolvedSchedulePlan().targetEndDate)
            if (declaredDateLimits.any { ProgramCalendarEngine.parseIsoDate(it) == null } ||
                week.sessions.any { session ->
                    session.dayOfWeek?.let { it !in 1..7 } == true || session.assignedDays.any { it !in 1..7 }
                }
            ) return reject(HybridCalendarRejection.InvalidCalendarDates)
            val projection = ProgramCalendarEngine.project(program, includeOccurrenceOverlays = false)
            val projected = projection.weeks.singleOrNull { it.weekId == week.id }
            val anchor = if (looksDated) projected?.startDate else ProgramCalendarEngine.parseIsoDate(request.occurrenceWeekStartDate)
            if (anchor == null || (looksDated && request.occurrenceWeekStartDate != null && request.occurrenceWeekStartDate != anchor.toString())) {
                return reject(HybridCalendarRejection.InvalidCalendarDates)
            }
            val end = anchor.plusDays(6)
            if ((looksDated && week.startDate != null && week.startDate != anchor.toString()) ||
                (looksDated && week.endDate != null && week.endDate != end.toString()) ||
                week.trainingDayDates.any { (day, raw) ->
                    val date = ProgramCalendarEngine.parseIsoDate(raw)
                    date == null || day !in 1..7 || date.dayOfWeek.value != day || date.isBefore(anchor) || date.isAfter(end)
                }
            ) return reject(HybridCalendarRejection.InvalidCalendarDates)
            // Moving a previously scheduled day in the past would rewrite the historical calendar.
            val originalDates = week.sessions.mapNotNull { session ->
                session.dayOfWeek?.let { day -> HybridOccurrenceResolver.dateForWeekday(anchor, day) }
            } + week.trainingDayDates.values.mapNotNull(ProgramCalendarEngine::parseIsoDate)
            originalDates.firstOrNull { it.isBefore(today) }?.let {
                return reject(HybridCalendarRejection.PastScheduledDate(it.toString()))
            }
            val mapped = assignments.map { assignment ->
                assignment.weekday to anchor.plusDays(
                    ((assignment.weekday - anchor.dayOfWeek.value + 7) % 7).toLong(),
                )
            }.toMap()
            mapped.values.firstOrNull { it.isBefore(today) }?.let {
                return reject(HybridCalendarRejection.PastScheduledDate(it.toString()))
            }
            if (currentMinuteOfDay != null) assignments.firstOrNull {
                mapped[it.weekday] == today && it.startMinute < currentMinuteOfDay
            }?.let { return reject(HybridCalendarRejection.PastScheduledTime(it.sessionId)) }
            if (mapped.values.any { it.isBefore(anchor) || it.isAfter(end) || projection.effectiveEndDate?.let(it::isAfter) == true }) {
                return reject(HybridCalendarRejection.InvalidCalendarDates)
            }
            Triple(anchor.toString(), end.toString(), mapped.mapValues { it.value.toString() })
        } else null

        val bySession = assignments.associateBy { it.sessionId }
        val appliedTemplateWeek = if (occurrence != null) week else week.copy(
            sessions = week.sessions.map { session ->
                val assignment = bySession[session.id]
                if (assignment == null) session else session.copy(dayOfWeek = assignment.weekday, assignedDays = listOf(assignment.weekday))
            },
            startDate = dates?.first ?: week.startDate,
            endDate = dates?.second ?: week.endDate,
            trainingDayDates = dates?.third ?: week.trainingDayDates,
            hybridProposal = null,
        )
        val acceptedFingerprint = HybridProgramPlanner.fingerprintWeek(appliedTemplateWeek)
            ?: return reject(HybridCalendarRejection.StaleProposal)
        val schedule = HybridAppliedWeekSchedule(
            sourceProgramId = program.id,
            sourceWeekId = week.id,
            scope = request.scope,
            acceptedAtMs = acceptedAtMs,
            proposalSnapshot = recomputed.proposal,
            assignments = assignments.toList(),
            omittedOptionalSessions = recomputed.proposal.omittedOptionalSessions.toList(),
            acceptedWeekFingerprint = acceptedFingerprint,
        )
        if (occurrence != null) {
            val overlay = HybridOccurrenceWeekSchedule(
                programId = program.id, runId = occurrence.runId, cycleNumber = occurrence.cycleNumber,
                weekInstanceId = occurrence.weekInstanceId, weekId = occurrence.weekId,
                weekStartDate = dates!!.first, schedule = schedule,
            )
            return HybridCalendarApplicationResult.Applied(
                program.copy(hybridOccurrenceSchedules = program.hybridOccurrenceSchedules + overlay), schedule,
            )
        }
        val mutation = ProgramStructureMutator.updateWeek(program, week.id) {
            appliedTemplateWeek.copy(hybridAppliedSchedule = schedule)
        }
        return if (mutation is ProgramStructureMutationResult.Success) {
            HybridCalendarApplicationResult.Applied(mutation.program, schedule)
        } else reject(HybridCalendarRejection.InvalidHierarchy)
    }

    /** The normal hierarchy index only visits base sessions; alternatives are identities too. */
    private fun hasUnambiguousSessionIdentities(program: Program): Boolean {
        val seen = mutableSetOf<String>()
        fun visit(session: Session): Boolean {
            if (session.id.isBlank() || !seen.add(session.id)) return false
            return listOfNotNull(session.sessionB, session.sessionC, session.sessionD).all(::visit)
        }
        val base = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.flatMap { it.sessions }
        val additional = program.loops.flatMap { it.sessions } + program.events.flatMap { it.sessions } +
            program.calendarBreaks.flatMap { it.weeks }.flatMap { it.sessions }
        return (base + additional).all(::visit)
    }
}
