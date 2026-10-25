package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

data class HybridRemainingPlanningRequest(val programId: String, val identity: HybridOccurrenceIdentity, val weekStartDate: String)

sealed interface HybridRemainingPlanningError {
    data object WrongOccurrence : HybridRemainingPlanningError
    data object OngoingWorkout : HybridRemainingPlanningError
    data object InvalidDates : HybridRemainingPlanningError
    data object InvalidBindings : HybridRemainingPlanningError
    data object InvalidExistingOverlay : HybridRemainingPlanningError
    data object NoRemainingSessions : HybridRemainingPlanningError
    data class DuplicateHistoryId(val logId: String) : HybridRemainingPlanningError
    data class AmbiguousHistory(val logIds: List<String>) : HybridRemainingPlanningError
    data class ProtectedTimeUnavailable(val ruleId: String, val sessionIds: List<String>) : HybridRemainingPlanningError
    data class PerformedDoseExceedsMaximum(val goalId: String) : HybridRemainingPlanningError
    data object NumericOverflow : HybridRemainingPlanningError
}

sealed interface HybridRemainingPlanResult {
    data class Ready(val proposal: HybridWeekProposal) : HybridRemainingPlanResult
    data class Invalid(val reasons: List<HybridRemainingPlanningError>) : HybridRemainingPlanResult
    data class PlanningFailed(val planning: HybridProgramPlanResult) : HybridRemainingPlanResult
}

internal data class HybridPlanningAdaptation(
    val context: HybridOccurrencePlanningContext,
    val adjustedRequirements: List<HybridWeeklyGoalRequirement>,
    val minimumStarts: Map<Int, Int>,
)

/** Explicit occurrence planning. Protected records are evidence, never moving candidates. */
object HybridRemainingWeekPlanner {
    private val json = Json { encodeDefaults = true }

    fun prepare(
        source: HybridCalendarSourceSnapshot,
        request: HybridRemainingPlanningRequest,
        bindings: List<HybridSessionGoalBinding>,
        today: LocalDate,
        currentMinute: Int,
        zoneId: ZoneId,
        searchNodeBudget: Int = HybridWeekPlanRequest.DEFAULT_SEARCH_NODE_BUDGET,
    ): HybridRemainingPlanResult {
        fun invalid(error: HybridRemainingPlanningError) = HybridRemainingPlanResult.Invalid(listOf(error))
        val program = source.program
        val identity = HybridOccurrenceResolver.currentIdentity(program)
        if (program.id != request.programId || identity != request.identity || currentMinute !in 0..1439) {
            return invalid(HybridRemainingPlanningError.WrongOccurrence)
        }
        if (source.hasOngoingWorkout) return invalid(HybridRemainingPlanningError.OngoingWorkout)
        val index = ProgramHierarchyIndex(program)
        val week = index.locateWeek(request.identity.weekId)?.week
            ?: return invalid(HybridRemainingPlanningError.WrongOccurrence)
        if (!index.isValid) return invalid(HybridRemainingPlanningError.WrongOccurrence)
        val anchor = ProgramCalendarEngine.parseIsoDate(request.weekStartDate)
            ?: return invalid(HybridRemainingPlanningError.InvalidDates)
        if (ProgramCalendarEngine.isCalendarized(program)) {
            val actual = ProgramCalendarEngine.project(program, false).weeks.singleOrNull { it.weekId == week.id }?.startDate
            if (actual != anchor) return invalid(HybridRemainingPlanningError.InvalidDates)
        } else if (program.calendarization != null || program.resolvedSchedulePlan().mode == ScheduleMode.DATED) {
            return invalid(HybridRemainingPlanningError.InvalidDates)
        }
        val configuration = week.hybridPlanningConfiguration ?: program.hybridPlanning
            ?: return HybridRemainingPlanResult.PlanningFailed(HybridProgramPlanResult.Invalid(listOf(HybridProgramPlanningError.MissingConfiguration)))
        val byBinding = bindings.associateBy { it.sessionId }
        if (byBinding.size != bindings.size || byBinding.keys != week.sessions.map { it.id }.toSet()) {
            return invalid(HybridRemainingPlanningError.InvalidBindings)
        }
        // Check IDs before program/run filtering: a conflicting duplicate must not disappear.
        source.history.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys.firstOrNull()?.let {
            return invalid(HybridRemainingPlanningError.DuplicateHistoryId(it))
        }
        val sessionIds = week.sessions.mapTo(mutableSetOf()) { it.id }
        val matching = mutableListOf<WorkoutLog>()
        val ambiguous = mutableListOf<String>()
        source.history.filter { it.programId == program.id && it.sessionId in sessionIds }.forEach { log ->
            if (log.programRunId != null && log.programRunId != identity.runId) return@forEach
            if (log.cycleNumber != null && log.cycleNumber != identity.cycleNumber) return@forEach
            if (log.weekInstanceId != null && log.weekInstanceId != identity.weekInstanceId) return@forEach
            if (!log.calendarBreakId.isNullOrBlank()) return@forEach
            if (log.programRunId == null || log.cycleNumber == null || log.weekInstanceId == null) ambiguous += log.id
            else matching += log
        }
        if (ambiguous.isNotEmpty()) return invalid(HybridRemainingPlanningError.AmbiguousHistory(ambiguous.sorted()))
        val overlays = program.hybridOccurrenceSchedules.filter { it.programId == program.id && it.identity == identity }
        if (overlays.size > 1) return invalid(HybridRemainingPlanningError.InvalidExistingOverlay)
        val prior = HybridOccurrenceResolver.scheduleFor(program, week, identity)
        if (overlays.isNotEmpty() && prior?.identity != identity) return invalid(HybridRemainingPlanningError.InvalidExistingOverlay)
        val previousAssignments = prior?.schedule?.assignments.orEmpty().associateBy { it.sessionId }
        val previousProtected = prior?.schedule?.proposalSnapshot?.occurrenceContext?.protectedSessions.orEmpty().associateBy { it.sessionId }
        val previouslyOmitted = prior?.schedule?.omittedOptionalSessions.orEmpty().mapTo(mutableSetOf()) { it.sessionId }
        val groupedLogs = matching.groupBy { it.sessionId }
        val completedIds = groupedLogs.keys + program.runState!!.completedSessionIds.intersect(sessionIds)
        val protected = week.sessions.mapNotNull { session ->
            val previous = previousAssignments[session.id] ?: previousProtected[session.id]?.previousAssignment
            val explicitDate = previousProtected[session.id]?.calendarDate
                ?: session.dayOfWeek?.let { week.trainingDayDates[it] }
            val scheduled = ProgramCalendarEngine.parseIsoDate(explicitDate)
                ?: if (session.id !in previouslyOmitted) (previous?.weekday ?: session.dayOfWeek)?.let {
                    HybridOccurrenceResolver.dateForWeekday(anchor, it)
                } else null
            val completed = session.id in completedIds
            val inPast = scheduled?.let { it.isBefore(today) || (it == today && previous != null && previous.startMinute < currentMinute) } == true
            if (!completed && !inPast) return@mapNotNull null
            val activities = groupedLogs[session.id].orEmpty().sortedBy { it.id }.map { log ->
                val exact = log.actualDurationSeconds?.takeIf { it > 0L && !log.actualDurationSource.isNullOrBlank() }
                val legacy = if (exact == null) log.durationMinutes.takeIf { it > 0 } else null
                HybridCompletedActivityEvidence(log.id, actualDate(log, zoneId)?.toString(),
                    ProgramCalendarEngine.parseIsoDate(log.scheduledDate)?.toString(), exact,
                    if (exact != null) log.actualDurationSource else null, legacy,
                    when { exact != null -> HybridRecordedDurationSource.EXACT_RECORDED_SECONDS
                        legacy != null -> HybridRecordedDurationSource.LEGACY_ROUNDED_MINUTES
                        else -> HybridRecordedDurationSource.UNAVAILABLE })
            }
            HybridProtectedSessionEvidence(session.id, byBinding.getValue(session.id).goalId,
                if (completed) HybridProtectedSessionKind.COMPLETED else HybridProtectedSessionKind.PAST_UNCOMPLETED,
                activities.mapNotNull { it.scheduledDate }.distinct().singleOrNull()
                    ?: scheduled?.toString() ?: activities.mapNotNull { it.actualDate }.distinct().singleOrNull(),
                previous, activities)
        }.sortedBy { it.sessionId }
        if (protected.size == week.sessions.size) return invalid(HybridRemainingPlanningError.NoRemainingSessions)
        val protectedById = protected.associateBy { it.sessionId }
        configuration.separationRules.forEach { rule ->
            val unavailable = listOf(rule.firstSessionId, rule.secondSessionId).filter {
                protectedById[it]?.let { row -> row.previousAssignment == null || row.calendarDate == null } == true
            }
            if (unavailable.isNotEmpty()) return invalid(HybridRemainingPlanningError.ProtectedTimeUnavailable(rule.id, unavailable))
        }
        val baseContext = HybridOccurrencePlanningContext(identity = identity, weekStartDate = anchor.toString(),
            asOfDate = today.toString(), asOfMinute = currentMinute, zoneId = zoneId.id,
            sourceHistoryFingerprint = historyFingerprint(source.history),
            sourceOverlayFingerprint = overlays.singleOrNull()?.let { hash(json.encodeToString(it)) }, protectedSessions = protected)
        val dose = try { summarizeDose(baseContext, program.trainingIntent!!.goals.map { it.id }, emptyList(), configuration.weeklyGoalRequirements) }
            catch (_: ArithmeticException) { return invalid(HybridRemainingPlanningError.NumericOverflow) }
        val context = baseContext.copy(weeklyDose = dose)
        val adjusted = adjustedRequirements(context, configuration.weeklyGoalRequirements)
            ?: return invalid(HybridRemainingPlanningError.PerformedDoseExceedsMaximum(
                configuration.weeklyGoalRequirements.firstOrNull { requirement ->
                    val recorded = dose.first { it.goalId == requirement.goalId }
                    requirement.maximumSessions?.let { recorded.recordedActivitiesInWeek > it } == true ||
                        requirement.maximumMinutes?.let { recorded.recordedExactSecondsInWeek > it.toLong() * 60 } == true
                }?.goalId.orEmpty()))
        val starts = (1..7).associateWith { day ->
            val date = HybridOccurrenceResolver.dateForWeekday(anchor, day)
            when { date.isBefore(today) -> 1440; date == today -> currentMinute; else -> 0 }
        }
        val adapted = try {
            HybridProgramPlanner.prepareAdapted(program, week.id, bindings,
                HybridPlanningAdaptation(context, adjusted, starts), searchNodeBudget)
        } catch (_: ArithmeticException) {
            return invalid(HybridRemainingPlanningError.NumericOverflow)
        }
        return when (val plan = adapted) {
            is HybridProgramPlanResult.Ready -> HybridRemainingPlanResult.Ready(plan.proposal)
            else -> HybridRemainingPlanResult.PlanningFailed(plan)
        }
    }

    internal fun summarizeDose(
        context: HybridOccurrencePlanningContext,
        goalIds: List<String>,
        assignments: List<SessionAssignment>,
        requirements: List<HybridWeeklyGoalRequirement>,
    ): List<HybridOccurrenceGoalDose> {
        val anchor = LocalDate.parse(context.weekStartDate)
        return goalIds.sorted().map { goalId ->
            val completed = context.protectedSessions.filter { it.goalId == goalId && it.kind == HybridProtectedSessionKind.COMPLETED }
            val activities = completed.flatMap { it.activities }.filter { activity ->
                activity.actualDate?.let(LocalDate::parse)?.let { !it.isBefore(anchor) && !it.isAfter(anchor.plusDays(6)) } == true
            }
            val unknownActivities = completed.filter { it.activities.isEmpty() || it.activities.any { row -> row.actualDate == null } }.map { it.sessionId }
            val exact = activities.fold(0L) { sum, row -> Math.addExact(sum, row.actualDurationSeconds ?: 0L) }
            val legacy = activities.fold(0L) { sum, row -> Math.addExact(sum, row.legacyRoundedMinutes?.toLong() ?: 0L) }
            val unknownDurations = activities.filter { it.actualDurationSeconds == null }.map { it.logId }
            val planned = assignments.filter { it.session.goalId == goalId }
            val requirement = requirements.singleOrNull { it.goalId == goalId }
            val unknown = requirement != null && ((unknownActivities.isNotEmpty() &&
                listOf(requirement.minimumSessions, requirement.maximumSessions, requirement.minimumMinutes, requirement.maximumMinutes).any { it != null }) ||
                (unknownDurations.isNotEmpty() && (requirement.minimumMinutes != null || requirement.maximumMinutes != null)))
            HybridOccurrenceGoalDose(goalId, activities.size, exact, legacy, unknownActivities.sorted(), unknownDurations.sorted(),
                planned.size, planned.sumOf { it.session.trainingMinutes.toLong() },
                when { requirement == null -> HybridOccurrenceDoseStatus.NOT_DECLARED; unknown -> HybridOccurrenceDoseStatus.UNKNOWN
                    else -> HybridOccurrenceDoseStatus.SATISFIED })
        }
    }

    internal fun adjustedRequirements(context: HybridOccurrencePlanningContext, requirements: List<HybridWeeklyGoalRequirement>): List<HybridWeeklyGoalRequirement>? {
        return requirements.mapNotNull { requirement ->
            val recorded = context.weeklyDose.singleOrNull { it.goalId == requirement.goalId } ?: return null
            val exact = recorded.recordedExactSecondsInWeek
            if (requirement.maximumSessions?.let { recorded.recordedActivitiesInWeek > it } == true ||
                requirement.maximumMinutes?.let { exact > it.toLong() * 60 } == true) return null
            val unknownCount = recorded.unknownActivitySessionIds.isNotEmpty()
            val unknownMinutes = unknownCount || recorded.unknownDurationLogIds.isNotEmpty()
            val updated = requirement.copy(
                minimumSessions = if (unknownCount) null else requirement.minimumSessions?.let { maxOf(0, it - recorded.recordedActivitiesInWeek) },
                maximumSessions = requirement.maximumSessions?.let { it - recorded.recordedActivitiesInWeek },
                minimumMinutes = if (unknownMinutes) null else requirement.minimumMinutes?.let { maxOf(0, it - (exact / 60).toInt()) },
                maximumMinutes = requirement.maximumMinutes?.let { it - ((exact + 59) / 60).toInt() },
            )
            updated.takeIf { listOf(it.minimumSessions, it.maximumSessions, it.minimumMinutes, it.maximumMinutes).any { value -> value != null } }
        }
    }

    internal fun assessment(context: HybridOccurrencePlanningContext, configuration: HybridPlanningConfiguration): HybridTrainingAssessment {
        val base = HybridTrainingPolicy.assessment(configuration.separationRules, configuration.weeklyGoalRequirements)
        return if (context.weeklyDose.any { it.requirementStatus == HybridOccurrenceDoseStatus.UNKNOWN })
            base.copy(declaredConstraints = HybridDeclaredConstraintsStatus.UNASSESSED) else base
    }

    internal fun historyFingerprint(history: List<WorkoutLog>): String = hash(JsonArray(history.sortedBy { it.id }.map { log -> buildJsonObject {
        put("id", log.id); put("program", log.programId); put("session", log.sessionId); put("run", log.programRunId?.let(::JsonPrimitive) ?: JsonNull)
        put("cycle", log.cycleNumber?.let(::JsonPrimitive) ?: JsonNull); put("instance", log.weekInstanceId?.let(::JsonPrimitive) ?: JsonNull)
        put("week", log.weekId?.let(::JsonPrimitive) ?: JsonNull); put("date", log.date); put("actualDate", log.actualDate?.let(::JsonPrimitive) ?: JsonNull)
        put("scheduledDate", log.scheduledDate?.let(::JsonPrimitive) ?: JsonNull); put("minutes", log.durationMinutes)
        put("seconds", log.actualDurationSeconds?.let(::JsonPrimitive) ?: JsonNull); put("source", log.actualDurationSource?.let(::JsonPrimitive) ?: JsonNull)
    } }).toString())

    internal fun overlayFingerprint(overlay: HybridOccurrenceWeekSchedule?): String? = overlay?.let { hash(json.encodeToString(it)) }
    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun actualDate(log: WorkoutLog, zone: ZoneId): LocalDate? = ProgramCalendarEngine.parseIsoDate(log.actualDate)
        ?: ProgramCalendarEngine.parseIsoDate(log.date) ?: runCatching { Instant.parse(log.date).atZone(zone).toLocalDate() }.getOrNull()
}
