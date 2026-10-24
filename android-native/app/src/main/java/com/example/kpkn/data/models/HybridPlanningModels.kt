package com.example.kpkn.data.models

import com.example.kpkn.domain.multisport.AvailableTrainingWindow
import com.example.kpkn.domain.multisport.FixedTrainingCommitment
import com.example.kpkn.domain.multisport.OptionalSessionOmission
import com.example.kpkn.domain.multisport.TrainingIntent
import com.example.kpkn.domain.multisport.HybridSessionSeparationRule
import com.example.kpkn.domain.multisport.HybridWeeklyGoalRequirement
import com.example.kpkn.domain.multisport.HybridTrainingAssessment
import kotlinx.serialization.Serializable

/** Explicit local planning constraints; absence preserves the existing scheduling behavior. */
@Serializable
data class HybridPlanningConfiguration(
    val schemaVersion: Int = 1,
    val availableWindows: List<AvailableTrainingWindow>,
    val fixedCommitments: List<FixedTrainingCommitment> = emptyList(),
    val separationRules: List<HybridSessionSeparationRule> = emptyList(),
    val weeklyGoalRequirements: List<HybridWeeklyGoalRequirement> = emptyList(),
)

/** User/adapter-declared identity; sport is never inferred from a session name or its contents. */
@Serializable
data class HybridSessionGoalBinding(
    val sessionId: String,
    val goalId: String,
    /** Explicit placement budget; null uses the existing session's positive target duration. */
    val durationMinutes: Int? = null,
    val allowedWeekdays: Set<Int> = (1..7).toSet(),
    val allowedPlaces: Set<String> = emptySet(),
    val requiredEquipment: Set<String> = emptySet(),
)

@Serializable
enum class HybridProposalScope {
    /** Top-level base sessions only; nested B/C/D variants are alternatives, not extra sessions. */
    TEMPLATE_BASE_SESSIONS,
    /** Explicit occurrence evidence protects completed/past sessions; assignments contain only remaining ones. */
    ACTIVE_OCCURRENCE_REMAINING_SESSIONS,
}

@Serializable
data class HybridPlannedSession(
    val sessionId: String,
    val goalId: String,
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
    val placeId: String,
    val windowId: String,
)

/**
 * Recoverable proposal, never an applied calendar or a claim of physiological compatibility.
 * The source fingerprint and snapshots permit detecting edits before a future acceptance action.
 */
@Serializable
data class HybridWeekProposal(
    val schemaVersion: Int = 1,
    val scope: HybridProposalScope = HybridProposalScope.TEMPLATE_BASE_SESSIONS,
    val sourceWeekFingerprint: String,
    val intentSnapshot: TrainingIntent,
    val configurationSnapshot: HybridPlanningConfiguration,
    val bindings: List<HybridSessionGoalBinding>,
    val assignments: List<HybridPlannedSession>,
    val omittedOptionalSessions: List<OptionalSessionOmission> = emptyList(),
    /** Missing in an earlier unbound proposal means stale, never the currently opened program. */
    val sourceProgramId: String = "",
    /** Only declared calendar/dose constraints are assessed; physiology remains UNASSESSED. */
    val trainingAssessment: HybridTrainingAssessment = HybridTrainingAssessment(),
    val occurrenceContext: HybridOccurrencePlanningContext? = null,
) {
    val omittedOptionalSessionIds: List<String>
        get() = omittedOptionalSessions.map { it.sessionId }
}
