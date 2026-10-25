package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.HybridPlannedSession
import com.example.kpkn.data.models.HybridProtectedSessionKind
import com.example.kpkn.data.models.HybridPlanningConfiguration
import com.example.kpkn.data.models.HybridProposalScope
import com.example.kpkn.data.models.HybridSessionGoalBinding
import com.example.kpkn.data.models.HybridWeekProposal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.domain.training.DuplicateProgramHierarchyId
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

/** Typed failures while resolving a real program week and its explicit bindings. */
sealed interface HybridProgramPlanningError {
    data object EmptyProgramId : HybridProgramPlanningError
    data object MissingTrainingIntent : HybridProgramPlanningError
    data class InvalidTrainingIntent(val errors: List<TrainingIntentValidationError>) : HybridProgramPlanningError
    data object MissingConfiguration : HybridProgramPlanningError
    data class UnsupportedConfigurationSchemaVersion(val actual: Int) : HybridProgramPlanningError
    data class AmbiguousHierarchy(val duplicateIds: List<DuplicateProgramHierarchyId>) : HybridProgramPlanningError
    data object EmptyWeekId : HybridProgramPlanningError
    data class WeekNotFound(val weekId: String) : HybridProgramPlanningError
    data class UnsupportedWeek(val weekId: String, val reason: WeekRejectionReason) : HybridProgramPlanningError
    data class EmptySessionId(val sessionIndex: Int) : HybridProgramPlanningError
    data class UnsupportedCompetitionSession(val sessionId: String) : HybridProgramPlanningError
    data class EmptyBindingSessionId(val bindingIndex: Int) : HybridProgramPlanningError
    data class DuplicateBindingSessionId(val sessionId: String) : HybridProgramPlanningError
    data class UnknownBindingSessionId(val sessionId: String) : HybridProgramPlanningError
    data class MissingSessionBinding(val sessionId: String) : HybridProgramPlanningError
    data class EmptyBindingGoalId(val sessionId: String) : HybridProgramPlanningError
    data class UnknownBindingGoalId(val sessionId: String, val goalId: String) : HybridProgramPlanningError
    data class IntentGoalUnboundBeforePlacement(val goalId: String) : HybridProgramPlanningError
    data class IntentGoalUncoveredAfterPlacement(
        val goalId: String,
        val omittedOptionalSessionIds: List<String>,
    ) : HybridProgramPlanningError
    data class MissingSessionDuration(val sessionId: String) : HybridProgramPlanningError
    data class InvalidSessionDuration(val sessionId: String, val durationMinutes: Int) : HybridProgramPlanningError
    data class SessionDurationWithoutContent(
        val sessionId: String,
        val budgetMinutes: Int?,
        val budgetSource: HybridSessionDurationBudgetSource?,
    ) : HybridProgramPlanningError
    data class SessionDurationEstimateUnavailable(val sessionId: String) : HybridProgramPlanningError
    data class SessionDurationPolicyFailure(val sessionId: String, val error: HybridSessionDurationPolicyError) : HybridProgramPlanningError
    data class InsufficientSessionDurationBudget(
        val sessionId: String,
        val estimatedMinutes: Int,
        val budgetMinutes: Int,
        val budgetSource: HybridSessionDurationBudgetSource,
    ) : HybridProgramPlanningError
    data class EmptyAllowedWeekdays(val sessionId: String) : HybridProgramPlanningError
    data class InvalidAllowedWeekday(val sessionId: String, val weekday: Int) : HybridProgramPlanningError
    data class EmptyAllowedPlaceId(val sessionId: String) : HybridProgramPlanningError
    data class EmptyRequiredEquipmentId(val sessionId: String) : HybridProgramPlanningError
    data class InvalidSessionPlaceId(val sessionId: String) : HybridProgramPlanningError
    data class BindingExcludesSessionPlace(
        val sessionId: String,
        val fixedPlaceId: String,
        val allowedPlaces: Set<String>,
    ) : HybridProgramPlanningError
    data object WeekFingerprintUnavailable : HybridProgramPlanningError
}

enum class WeekRejectionReason { LOOP, REST, EMPTY }

sealed interface HybridProgramPlanResult {
    data class Ready(
        val proposal: HybridWeekProposal,
        /** Ephemeral provenance for callers; the persisted proposal schema remains unchanged. */
        val durationPlans: List<HybridSessionDurationPlan> = emptyList(),
    ) : HybridProgramPlanResult
    data class Invalid(val reasons: List<HybridProgramPlanningError>) : HybridProgramPlanResult
    data class PlacementFailed(val result: HybridWeekPlanResult) : HybridProgramPlanResult
}

/**
 * Prepares a persisted, reviewable proposal from already-authored top-level sessions.
 * This never edits the program, sessions, dates, run cursor, or calendar.
 */
object HybridProgramPlanner {
    private const val CURRENT_CONFIGURATION_SCHEMA_VERSION = 1
    private const val CURRENT_PROPOSAL_SCHEMA_VERSION = 1

    private val fingerprintJson = Json { encodeDefaults = true }
    private val hexDigits = "0123456789abcdef"

    fun prepare(
        program: Program,
        weekId: String,
        bindings: List<HybridSessionGoalBinding>,
        searchNodeBudget: Int = HybridWeekPlanRequest.DEFAULT_SEARCH_NODE_BUDGET,
    ): HybridProgramPlanResult = prepareAdapted(program, weekId, bindings, null, searchNodeBudget)

    internal fun prepareAdapted(
        program: Program,
        weekId: String,
        bindings: List<HybridSessionGoalBinding>,
        adaptation: HybridPlanningAdaptation?,
        searchNodeBudget: Int = HybridWeekPlanRequest.DEFAULT_SEARCH_NODE_BUDGET,
    ): HybridProgramPlanResult {
        if (program.id.isBlank()) {
            return HybridProgramPlanResult.Invalid(listOf(HybridProgramPlanningError.EmptyProgramId))
        }
        val intent = program.trainingIntent
            ?: return HybridProgramPlanResult.Invalid(listOf(HybridProgramPlanningError.MissingTrainingIntent))
        val intentErrors = TrainingIntentPolicy.validate(intent)
        if (intentErrors.isNotEmpty()) {
            return HybridProgramPlanResult.Invalid(listOf(HybridProgramPlanningError.InvalidTrainingIntent(intentErrors)))
        }

        if (weekId.isBlank()) {
            return HybridProgramPlanResult.Invalid(listOf(HybridProgramPlanningError.EmptyWeekId))
        }
        val hierarchy = try {
            ProgramHierarchyIndex(program)
        } catch (_: Exception) {
            return HybridProgramPlanResult.Invalid(
                listOf(HybridProgramPlanningError.AmbiguousHierarchy(emptyList())),
            )
        }
        if (!hierarchy.isValid) {
            return HybridProgramPlanResult.Invalid(
                listOf(HybridProgramPlanningError.AmbiguousHierarchy(hierarchy.duplicateIds.toList())),
            )
        }
        val location = hierarchy.locateWeek(weekId)
            ?: return HybridProgramPlanResult.Invalid(listOf(HybridProgramPlanningError.WeekNotFound(weekId)))
        val week = location.week
        val configuration = week.hybridPlanningConfiguration ?: program.hybridPlanning
            ?: return HybridProgramPlanResult.Invalid(listOf(HybridProgramPlanningError.MissingConfiguration))
        if (configuration.schemaVersion != CURRENT_CONFIGURATION_SCHEMA_VERSION) {
            return HybridProgramPlanResult.Invalid(
                listOf(HybridProgramPlanningError.UnsupportedConfigurationSchemaVersion(configuration.schemaVersion)),
            )
        }
        val weekReason = when {
            week.isLoopWeek || week.loopId != null -> WeekRejectionReason.LOOP
            week.executionKind == WeekExecutionKind.REST -> WeekRejectionReason.REST
            week.sessions.isEmpty() -> WeekRejectionReason.EMPTY
            else -> null
        }
        if (weekReason != null) {
            return HybridProgramPlanResult.Invalid(
                listOf(HybridProgramPlanningError.UnsupportedWeek(weekId, weekReason)),
            )
        }

        // Copy every caller-owned collection before validation or planning.
        val bindingSnapshots = bindings.map { binding ->
            binding.copy(
                allowedWeekdays = binding.allowedWeekdays.toSortedSet(),
                allowedPlaces = binding.allowedPlaces.toSortedSet(),
                requiredEquipment = binding.requiredEquipment.toSortedSet(),
            )
        }
        val configurationSnapshot = snapshotConfiguration(configuration)
        val weekSessions = week.sessions.toList()
        val sessionIds = weekSessions.mapTo(mutableSetOf()) { it.id }
        val goalIds = intent.goals.mapTo(mutableSetOf()) { it.id }
        val errors = mutableListOf<HybridProgramPlanningError>()

        weekSessions.forEachIndexed { index, session ->
            if (session.id.isBlank()) errors += HybridProgramPlanningError.EmptySessionId(index)
            if (session.isMeetDay || session.isCompetitionSession || session.competitionDetails != null ||
                session.competitionRecordId != null || session.competitionKeyDateId != null
            ) {
                errors += HybridProgramPlanningError.UnsupportedCompetitionSession(session.id)
            }
        }
        bindingSnapshots.forEachIndexed { index, binding ->
            if (binding.sessionId.isBlank()) {
                errors += HybridProgramPlanningError.EmptyBindingSessionId(index)
            } else if (binding.sessionId !in sessionIds) {
                errors += HybridProgramPlanningError.UnknownBindingSessionId(binding.sessionId)
            }
        }
        bindingSnapshots.groupBy(HybridSessionGoalBinding::sessionId)
            .filter { (id, rows) -> id.isNotBlank() && rows.size > 1 }
            .keys.sorted()
            .forEach { errors += HybridProgramPlanningError.DuplicateBindingSessionId(it) }

        val bindingsBySessionId = bindingSnapshots.groupBy(HybridSessionGoalBinding::sessionId)
            .filter { (id, rows) -> id.isNotBlank() && rows.size == 1 }
            .mapValues { (_, rows) -> rows.single() }
        weekSessions.forEach { session ->
            if (session.id.isNotBlank() && session.id !in bindingsBySessionId) {
                errors += HybridProgramPlanningError.MissingSessionBinding(session.id)
            }
        }
        val boundGoalIds = bindingsBySessionId.values
            .mapNotNull { binding -> binding.goalId.takeIf { it.isNotBlank() && it in goalIds } }
            .toSet()
        intent.goals.filter { it.id !in boundGoalIds }.forEach { goal ->
            errors += HybridProgramPlanningError.IntentGoalUnboundBeforePlacement(goal.id)
        }

        val candidates = mutableListOf<CandidateSession>()
        val durationPlans = mutableListOf<HybridSessionDurationPlan>()
        weekSessions.forEach { session ->
            val binding = bindingsBySessionId[session.id] ?: return@forEach
            if (binding.goalId.isBlank()) {
                errors += HybridProgramPlanningError.EmptyBindingGoalId(session.id)
            } else if (binding.goalId !in goalIds) {
                errors += HybridProgramPlanningError.UnknownBindingGoalId(session.id, binding.goalId)
            }

            val duration = when (val result = HybridSessionDurationPolicy.resolve(session, binding.durationMinutes)) {
                is HybridSessionDurationPolicyResult.Ready -> {
                    durationPlans += HybridSessionDurationPlan(session.id, result.resolution)
                    result.resolution.placementMinutes
                }
                is HybridSessionDurationPolicyResult.Invalid -> {
                    when (val reason = result.error) {
                        is HybridSessionDurationPolicyError.NoExecutableContent ->
                            errors += HybridProgramPlanningError.SessionDurationWithoutContent(
                                sessionId = session.id,
                                budgetMinutes = reason.budgetMinutes,
                                budgetSource = reason.budgetSource,
                            )
                        is HybridSessionDurationPolicyError.InvalidBudget ->
                            errors += HybridProgramPlanningError.InvalidSessionDuration(session.id, reason.minutes)
                        is HybridSessionDurationPolicyError.BudgetTooShort ->
                            errors += HybridProgramPlanningError.InsufficientSessionDurationBudget(
                                sessionId = session.id,
                                estimatedMinutes = reason.estimatedMinutes,
                                budgetMinutes = reason.budgetMinutes,
                                budgetSource = reason.source,
                            )
                        HybridSessionDurationPolicyError.EstimateUnavailable ->
                            errors += HybridProgramPlanningError.SessionDurationEstimateUnavailable(session.id)
                        else -> errors += HybridProgramPlanningError.SessionDurationPolicyFailure(session.id, reason)
                    }
                    null
                }
            }

            if (binding.allowedWeekdays.isEmpty()) {
                errors += HybridProgramPlanningError.EmptyAllowedWeekdays(session.id)
            }
            binding.allowedWeekdays.filter { it !in 1..7 }.sorted().forEach {
                errors += HybridProgramPlanningError.InvalidAllowedWeekday(session.id, it)
            }
            binding.allowedPlaces.filter(String::isBlank).sorted().forEach {
                errors += HybridProgramPlanningError.EmptyAllowedPlaceId(session.id)
            }
            binding.requiredEquipment.filter(String::isBlank).sorted().forEach {
                errors += HybridProgramPlanningError.EmptyRequiredEquipmentId(session.id)
            }

            val fixedPlace = session.placeId
            if (fixedPlace != null && fixedPlace.isBlank()) {
                errors += HybridProgramPlanningError.InvalidSessionPlaceId(session.id)
            } else if (fixedPlace != null && binding.allowedPlaces.isNotEmpty() && fixedPlace !in binding.allowedPlaces) {
                errors += HybridProgramPlanningError.BindingExcludesSessionPlace(
                    sessionId = session.id,
                    fixedPlaceId = fixedPlace,
                    allowedPlaces = binding.allowedPlaces.toSortedSet(),
                )
            }

            if (duration != null && duration > 0 && binding.goalId.isNotBlank() && binding.goalId in goalIds &&
                binding.allowedWeekdays.isNotEmpty() && binding.allowedWeekdays.all { it in 1..7 } &&
                binding.allowedPlaces.none(String::isBlank) && binding.requiredEquipment.none(String::isBlank) &&
                (fixedPlace == null || fixedPlace.isNotBlank()) &&
                (fixedPlace == null || binding.allowedPlaces.isEmpty() || fixedPlace in binding.allowedPlaces)
            ) {
                candidates += CandidateSession(
                    id = session.id,
                    goalId = binding.goalId,
                    durationMinutes = duration,
                    allowedWeekdays = binding.allowedWeekdays.toSortedSet(),
                    allowedPlaces = if (fixedPlace != null) setOf(fixedPlace) else binding.allowedPlaces.toSortedSet(),
                    requiredEquipment = binding.requiredEquipment.toSortedSet(),
                    required = session.requirement == SessionRequirement.REQUIRED,
                    trainingMinutes = durationPlans.last { it.sessionId == session.id }.resolution.trainingMinutes,
                )
            }
        }

        if (errors.isNotEmpty()) return HybridProgramPlanResult.Invalid(errors.distinct())

        val sourceWeekFingerprint = fingerprintWeek(week)
            ?: return HybridProgramPlanResult.Invalid(
                listOf(HybridProgramPlanningError.WeekFingerprintUnavailable),
            )
        val protected = adaptation?.context?.protectedSessions.orEmpty()
        val protectedIds = protected.mapTo(mutableSetOf()) { it.sessionId }
        val coveredGoalIds = protected.filter { it.kind == HybridProtectedSessionKind.COMPLETED }
            .mapTo(mutableSetOf()) { it.goalId }
        val anchor = adaptation?.context?.weekStartDate?.let(java.time.LocalDate::parse)
        val fixedAssignments = protected.mapNotNull { evidence ->
            val previous = evidence.previousAssignment ?: return@mapNotNull null
            val date = evidence.calendarDate?.let(java.time.LocalDate::parse) ?: return@mapNotNull null
            if (anchor == null || date.isBefore(anchor) || !date.isBefore(anchor.plusDays(7))) return@mapNotNull null
            val candidate = candidates.single { it.id == evidence.sessionId }
            SessionAssignment(candidate.copy(durationMinutes = previous.endMinute - previous.startMinute),
                date.dayOfWeek.value, previous.startMinute, previous.endMinute,
                previous.placeId, previous.windowId)
        }
        val plannerResult = HybridWeekPlanner.plan(
            HybridWeekPlanRequest(
                intent = intent,
                availableWindows = configurationSnapshot.availableWindows,
                fixedCommitments = configurationSnapshot.fixedCommitments,
                candidateSessions = candidates.filter { it.id !in protectedIds },
                searchNodeBudget = searchNodeBudget,
                separationRules = configurationSnapshot.separationRules,
                weeklyGoalRequirements = adaptation?.adjustedRequirements ?: configurationSnapshot.weeklyGoalRequirements,
                requireIntentGoalCoverage = true,
                fixedSessionAssignments = fixedAssignments,
                coveredGoalIds = coveredGoalIds,
                minimumStartMinuteByWeekday = adaptation?.minimumStarts.orEmpty(),
                weekStartDay = anchor?.dayOfWeek?.value ?: 1,
            ),
        )
        if (plannerResult !is HybridWeekPlanResult.Ready) {
            // Preserve the established adapter failure for legacy unconfigured goal omissions,
            // while the search itself now backtracks before accepting a goal-uncovered week.
            if (plannerResult is HybridWeekPlanResult.NotFeasible && configurationSnapshot.separationRules.isEmpty() &&
                configurationSnapshot.weeklyGoalRequirements.isEmpty()
            ) {
                val uncovered = plannerResult.policyViolations.filterIsInstance<HybridTrainingViolation.GoalUncovered>()
                    .map { it.goalId }.distinct()
                if (uncovered.isNotEmpty()) return HybridProgramPlanResult.Invalid(uncovered.map { goalId ->
                    HybridProgramPlanningError.IntentGoalUncoveredAfterPlacement(goalId,
                        candidates.filter { !it.required && it.goalId == goalId }.map { it.id }.sorted())
                })
            }
            return HybridProgramPlanResult.PlacementFailed(plannerResult)
        }

        val assignedGoalIds = plannerResult.assignments.mapTo(mutableSetOf()) { it.session.goalId } + coveredGoalIds
        val uncoveredGoals = intent.goals.filter { it.id !in assignedGoalIds }
        if (uncoveredGoals.isNotEmpty()) {
            val omittedByGoal = plannerResult.omittedOptionalSessions.groupBy(
                keySelector = { it.goalId },
                valueTransform = { it.sessionId },
            )
            return HybridProgramPlanResult.Invalid(
                uncoveredGoals.map { goal ->
                    HybridProgramPlanningError.IntentGoalUncoveredAfterPlacement(
                        goalId = goal.id,
                        omittedOptionalSessionIds = omittedByGoal[goal.id].orEmpty().sorted(),
                    )
                },
            )
        }

        val occurrenceContext = adaptation?.context?.let { context ->
            context.copy(weeklyDose = HybridRemainingWeekPlanner.summarizeDose(context,
                intent.goals.map { it.id }, plannerResult.assignments, configurationSnapshot.weeklyGoalRequirements))
        }
        val proposal = HybridWeekProposal(
            schemaVersion = CURRENT_PROPOSAL_SCHEMA_VERSION,
            scope = if (adaptation == null) HybridProposalScope.TEMPLATE_BASE_SESSIONS
                else HybridProposalScope.ACTIVE_OCCURRENCE_REMAINING_SESSIONS,
            sourceWeekFingerprint = sourceWeekFingerprint,
            intentSnapshot = intent,
            configurationSnapshot = configurationSnapshot,
            bindings = bindingSnapshots.sortedBy(HybridSessionGoalBinding::sessionId),
            assignments = plannerResult.assignments.map { assignment ->
                HybridPlannedSession(
                    sessionId = assignment.session.id,
                    goalId = assignment.session.goalId,
                    weekday = assignment.weekday,
                    startMinute = assignment.startMinute,
                    endMinute = assignment.endMinute,
                    placeId = assignment.placeId,
                    windowId = assignment.windowId,
                )
            },
            omittedOptionalSessions = plannerResult.omittedOptionalSessions.map { omission ->
                omission.copy(reasons = omission.reasons.toList())
            },
            sourceProgramId = program.id,
            trainingAssessment = occurrenceContext?.let { HybridRemainingWeekPlanner.assessment(it, configurationSnapshot) }
                ?: plannerResult.trainingAssessment,
            occurrenceContext = occurrenceContext,
        )
        return HybridProgramPlanResult.Ready(
            proposal = proposal,
            durationPlans = durationPlans.sortedBy(HybridSessionDurationPlan::sessionId),
        )
    }

    /**
     * Checks source identity and freshness only. It does not authenticate stored assignments or
     * authorize their application; a future accept action must revalidate/recompute the proposal.
     */
    fun isCurrent(program: Program, weekId: String, proposal: HybridWeekProposal): Boolean {
        return try {
            if (program.id.isBlank() || proposal.sourceProgramId != program.id) return false
            if (proposal.schemaVersion != CURRENT_PROPOSAL_SCHEMA_VERSION ||
                proposal.scope != HybridProposalScope.TEMPLATE_BASE_SESSIONS
            ) return false

            val currentIntent = program.trainingIntent ?: return false
            if (!isSupportedIntent(currentIntent) || !isSupportedIntent(proposal.intentSnapshot)) return false
            if (currentIntent != proposal.intentSnapshot) return false

            if (weekId.isBlank()) return false
            val hierarchy = ProgramHierarchyIndex(program)
            if (!hierarchy.isValid) return false
            val week = hierarchy.locateWeek(weekId)?.week ?: return false
            val currentConfiguration = week.hybridPlanningConfiguration ?: program.hybridPlanning ?: return false
            if (currentConfiguration.schemaVersion != CURRENT_CONFIGURATION_SCHEMA_VERSION ||
                proposal.configurationSnapshot.schemaVersion != CURRENT_CONFIGURATION_SCHEMA_VERSION
            ) return false
            if (snapshotConfiguration(currentConfiguration) != snapshotConfiguration(proposal.configurationSnapshot)) return false

            if (week.isLoopWeek || week.loopId != null || week.executionKind == WeekExecutionKind.REST || week.sessions.isEmpty()) {
                return false
            }
            fingerprintWeek(week) == proposal.sourceWeekFingerprint
        } catch (_: Exception) {
            false
        }
    }

    private fun isSupportedIntent(intent: TrainingIntent): Boolean =
        !intent.isOpaque && TrainingIntentPolicy.validate(intent).isEmpty()

    internal fun snapshotConfiguration(configuration: HybridPlanningConfiguration): HybridPlanningConfiguration =
        configuration.copy(
            availableWindows = configuration.availableWindows.map { window ->
                window.copy(equipment = window.equipment.toSortedSet())
            }.sortedWith(
                compareBy<AvailableTrainingWindow> { it.weekday }
                    .thenBy { it.startMinute }
                    .thenBy { it.endMinute }
                    .thenBy { it.placeId }
                    .thenBy { it.id },
            ),
            fixedCommitments = configuration.fixedCommitments.sortedWith(
                compareBy<FixedTrainingCommitment> { it.weekday }
                    .thenBy { it.startMinute }
                    .thenBy { it.endMinute }
                    .thenBy { it.id },
            ),
            separationRules = configuration.separationRules.toList().sortedBy { it.id },
            weeklyGoalRequirements = configuration.weeklyGoalRequirements.toList().sortedBy { it.goalId },
        )

    internal fun fingerprintWeek(week: ProgramWeek): String? = try {
        val payload = fingerprintJson
            .encodeToJsonElement(ProgramWeek.serializer(), week.copy(hybridProposal = null, hybridAppliedSchedule = null))
            .stableWeekJson()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        buildString(digest.size * 2) {
            digest.forEach { value ->
                val byte = value.toInt() and 0xff
                append(hexDigits[byte ushr 4])
                append(hexDigits[byte and 0x0f])
            }
        }
    } catch (_: Exception) {
        null
    }

    /** Sort unordered JSON objects and the one serialized Set inside a base week. */
    private fun JsonElement.stableWeekJson(fieldName: String? = null): String = when (this) {
        is JsonObject -> entries
            .sortedBy { it.key }
            .joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
                "${JsonPrimitive(key)}:${value.stableWeekJson(key)}"
            }
        is JsonArray -> {
            val values: List<JsonElement> =
                if (fieldName == "manualLoadRequiredSides") sortedBy { it.toString() } else this
            values.joinToString(separator = ",", prefix = "[", postfix = "]") { it.stableWeekJson() }
        }
        else -> toString()
    }
}
