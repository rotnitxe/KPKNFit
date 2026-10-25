package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.ProgramProgressEngine
import java.time.LocalDate

data class HybridResolvedWeekSchedule(
    val schedule: HybridAppliedWeekSchedule,
    val weekStartDate: LocalDate?,
    val identity: HybridOccurrenceIdentity? = null,
)

/** Resolves accepted placement only; templates and stored past/future occurrences stay immutable. */
object HybridOccurrenceResolver {
    fun currentIdentity(program: Program): HybridOccurrenceIdentity? {
        val run = program.runState ?: return null
        val weekId = run.weekId?.takeIf(String::isNotBlank) ?: return null
        val instance = run.weekInstanceId?.takeIf(String::isNotBlank) ?: return null
        if (run.runId.isBlank() || run.cycleNumber <= 0 || run.status == ProgramRunStatus.COMPLETED ||
            run.status == ProgramRunStatus.BREAK || ProgramProgressEngine.templateWeekIdFromInstance(instance) != weekId ||
            ProgramProgressEngine.cycleFromInstanceId(instance)?.let { it != run.cycleNumber } == true
        ) return null
        return HybridOccurrenceIdentity(run.runId, run.cycleNumber, instance, weekId)
    }

    fun scheduleFor(
        program: Program,
        week: ProgramWeek,
        identity: HybridOccurrenceIdentity? = currentIdentity(program),
    ): HybridResolvedWeekSchedule? {
        val templateId = ProgramProgressEngine.templateWeekIdFromInstance(week.id) ?: week.id
        if (identity != null && identity.weekId == templateId) {
            val matching = program.hybridOccurrenceSchedules.filter {
                it.programId == program.id && it.identity == identity
            }
            // Duplicate exact identities are ambiguous; do not select an arbitrary overlay.
            if (matching.size > 1) return null
            matching.singleOrNull()?.let { overlay ->
                val anchor = parseDate(overlay.weekStartDate) ?: return null
                if (overlay.schemaVersion != 1 || overlay.schedule.scope != HybridCalendarApplicationScope.ACTIVE_WEEK_OCCURRENCE ||
                    !isUsable(program, week, overlay.schedule, templateId)
                ) return null
                return HybridResolvedWeekSchedule(overlay.schedule, anchor, identity)
            }
        }
        val template = week.hybridAppliedSchedule ?: return null
        if (template.scope != HybridCalendarApplicationScope.BEFORE_FIRST_RUN_TEMPLATE ||
            !isUsable(program, week, template, templateId)
        ) return null
        return HybridResolvedWeekSchedule(template, parseDate(week.startDate))
    }

    /** Runtime copies may omit OPTIONAL sessions; original authored sessions always remain stored. */
    fun resolveWeek(
        program: Program,
        week: ProgramWeek,
        runId: String?,
        cycleNumber: Int?,
        weekInstanceId: String?,
    ): ProgramWeek {
        val templateId = ProgramProgressEngine.templateWeekIdFromInstance(week.id) ?: week.id
        val identity = if (runId != null && cycleNumber != null && weekInstanceId != null) {
            HybridOccurrenceIdentity(runId, cycleNumber, weekInstanceId, templateId)
        } else null
        val resolved = scheduleFor(program, week, identity) ?: return week
        val assignments = resolved.schedule.assignments.associateBy { it.sessionId }
        val omitted = resolved.schedule.omittedOptionalSessions.mapTo(mutableSetOf()) { it.sessionId }
        val dates = resolved.weekStartDate?.let { anchor ->
            assignments.values.associate { it.weekday to dateForWeekday(anchor, it.weekday).toString() }
        }
        return week.copy(
            sessions = week.sessions.filterNot { it.id in omitted }.map { session ->
                assignments[session.id]?.let { placement ->
                    session.copy(dayOfWeek = placement.weekday, assignedDays = listOf(placement.weekday))
                } ?: session
            },
            startDate = resolved.weekStartDate?.toString() ?: week.startDate,
            endDate = resolved.weekStartDate?.plusDays(6)?.toString() ?: week.endDate,
            trainingDayDates = dates ?: week.trainingDayDates,
            hybridAppliedSchedule = resolved.schedule,
        )
    }

    fun dateForWeekday(anchor: LocalDate, weekday: Int): LocalDate =
        anchor.plusDays(Math.floorMod(weekday - anchor.dayOfWeek.value, 7).toLong())

    /** Defensive structural gate for metadata loaded from JSON, including after manual edits. */
    private fun isUsable(program: Program, week: ProgramWeek, schedule: HybridAppliedWeekSchedule, templateId: String): Boolean {
        if (schedule.schemaVersion != 1 || schedule.sourceProgramId != program.id || schedule.sourceWeekId != templateId ||
            schedule.proposalSnapshot.sourceProgramId != program.id ||
            schedule.assignments != schedule.proposalSnapshot.assignments ||
            schedule.omittedOptionalSessions != schedule.proposalSnapshot.omittedOptionalSessions ||
            schedule.acceptedWeekFingerprint.isBlank() ||
            !HybridProgramPlanner.isCurrent(program, templateId,
                schedule.proposalSnapshot.copy(sourceWeekFingerprint = schedule.acceptedWeekFingerprint))
        ) return false
        val assignments = schedule.assignments
        val omissions = schedule.omittedOptionalSessions
        val assignmentIds = assignments.map { it.sessionId }
        val omissionIds = omissions.map { it.sessionId }
        val sessions = week.sessions.associateBy { it.id }
        if (sessions.size != week.sessions.size || assignmentIds.size != assignmentIds.distinct().size ||
            omissionIds.size != omissionIds.distinct().size || assignmentIds.intersect(omissionIds.toSet()).isNotEmpty() ||
            (assignmentIds + omissionIds).toSet() != sessions.keys ||
            omissions.any { sessions[it.sessionId]?.requirement != SessionRequirement.OPTIONAL }
        ) return false
        val proposal = schedule.proposalSnapshot
        val bindings = proposal.bindings.associateBy { it.sessionId }
        if (bindings.size != proposal.bindings.size || bindings.keys != sessions.keys) return false
        val candidates = week.sessions.map { session ->
            val binding = bindings[session.id] ?: return false
            if (binding.goalId !in proposal.intentSnapshot.goals.map { it.id } ||
                (session.placeId != null && binding.allowedPlaces.isNotEmpty() && session.placeId !in binding.allowedPlaces)
            ) return false
            val duration = HybridSessionDurationPolicy.resolve(session, binding.durationMinutes)
                as? HybridSessionDurationPolicyResult.Ready ?: return false
            CandidateSession(
                id = session.id, goalId = binding.goalId, durationMinutes = duration.resolution.placementMinutes,
                trainingMinutes = duration.resolution.trainingMinutes,
                allowedWeekdays = binding.allowedWeekdays, allowedPlaces = session.placeId?.let { setOf(it) } ?: binding.allowedPlaces,
                requiredEquipment = binding.requiredEquipment, required = session.requirement == SessionRequirement.REQUIRED,
            )
        }
        val configuration = proposal.configurationSnapshot
        val request = HybridWeekPlanRequest(proposal.intentSnapshot, configuration.availableWindows, configuration.fixedCommitments,
            candidates, separationRules = configuration.separationRules, weeklyGoalRequirements = configuration.weeklyGoalRequirements,
            requireIntentGoalCoverage = true)
        if (HybridWeekPlanner.validateRequest(request).isNotEmpty() ||
            proposal.trainingAssessment != HybridTrainingPolicy.assessment(configuration.separationRules, configuration.weeklyGoalRequirements)
        ) return false
        val candidateById = candidates.associateBy { it.id }
        val resolved = assignments.map { assignment ->
            val candidate = candidateById[assignment.sessionId] ?: return false
            val window = configuration.availableWindows.singleOrNull { it.id == assignment.windowId } ?: return false
            if (assignment.goalId != candidate.goalId || assignment.weekday != window.weekday ||
                assignment.weekday !in candidate.allowedWeekdays || assignment.placeId != window.placeId ||
                assignment.startMinute < window.startMinute || assignment.endMinute > window.endMinute ||
                assignment.endMinute.toLong() - assignment.startMinute != candidate.durationMinutes.toLong() ||
                (candidate.allowedPlaces.isNotEmpty() && assignment.placeId !in candidate.allowedPlaces) ||
                !window.equipment.containsAll(candidate.requiredEquipment) ||
                configuration.fixedCommitments.any { it.weekday == assignment.weekday &&
                    assignment.startMinute < it.endMinute && assignment.endMinute > it.startMinute }
            ) return false
            SessionAssignment(candidate, assignment.weekday, assignment.startMinute, assignment.endMinute, assignment.placeId, assignment.windowId)
        }
        if (resolved.indices.any { index -> resolved.drop(index + 1).any { other ->
                val current = resolved[index]
                current.weekday == other.weekday && current.startMinute < other.endMinute && current.endMinute > other.startMinute
            } }) return false
        if (omissions.any { it.goalId != candidateById[it.sessionId]?.goalId || it.reasons.isEmpty() }) return false
        return HybridTrainingPolicy.violations(resolved, emptyList(), configuration.separationRules, configuration.weeklyGoalRequirements,
            proposal.intentSnapshot.goals.mapTo(mutableSetOf()) { it.id }).isEmpty()
    }

    private fun parseDate(raw: String?): LocalDate? = try {
        raw?.let(LocalDate::parse)
    } catch (_: java.time.format.DateTimeParseException) { null }
}
