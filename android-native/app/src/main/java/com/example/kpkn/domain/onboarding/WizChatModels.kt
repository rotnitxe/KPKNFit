package com.example.kpkn.domain.onboarding

import kotlinx.serialization.Serializable

/** Pure, persisted identifiers for the single WIZCHAT state machine. */
@Serializable
enum class WizChatStage {
    PROFILE,
    TRAINING,
    NUTRITION,
    RINGS,
    REVIEW,
}

@Serializable
enum class WizChatQuestionId {
    P_NAME,
    P_GENDER,
    P_AGE,
    P_HEIGHT,
    P_WEIGHT,
    P_EXPERIENCE,
    T_ROUTE,
    T_GOAL,
    T_STYLE,
    T_VOLUME_TECHNIQUE,
    T_VOLUME_CONSISTENCY,
    T_VOLUME_STRENGTH,
    T_VOLUME_MOBILITY,
    T_EQUIPMENT,
    T_HOME_EQUIPMENT,
    T_DAYS,
    T_WEEKDAYS,
    T_TIME,
    T_CARDIO_TYPE,
    T_CARDIO_TIME,
    T_TRAINING_MAX,
    T_MARKS,
    T_PLAN,
    T_REVIEW,
    N_START,
    N_SEX,
    N_ELIGIBILITY,
    N_DIRECTION,
    N_ACTIVITY,
    N_RESULT,
    R_START,
    R_RECENT,
    R_SESSIONS,
    R_RECENCY,
    R_ACTIVITY,
    R_INTENSITY,
    R_AXIAL,
    R_FEELINGS_MUSCLE,
    R_FEELINGS_ENERGY,
    R_FEELINGS_STRUCTURE,
    R_DISCOMFORT,
    R_RESULT,
    REVIEW,
}

@Serializable
enum class WizChatAnswerKind {
    TEXT,
    NUMBER,
    CHOICE,
    MULTI_CHOICE,
    TOGGLE,
    ACTION,
}

@Serializable
enum class WizChatAnswerSource {
    DECLARED,
    IMPORTED,
    SUGGESTED_ACCEPTED,
    UNKNOWN,
    OMITTED,
}

/**
 * A typed answer envelope. Values remain data, never presentation copy. A
 * single envelope keeps old drafts forward-compatible while retaining the
 * kind needed to validate and invalidate dependencies.
 */
@Serializable
data class WizChatAnswerRecord(
    val questionId: WizChatQuestionId,
    val kind: WizChatAnswerKind,
    val textValue: String? = null,
    val numberValue: Double? = null,
    val values: List<String> = emptyList(),
    val source: WizChatAnswerSource = WizChatAnswerSource.DECLARED,
    val revision: Int = 1,
    val acceptedAtMs: Long? = null,
    val variantId: String? = null,
    /** Revision observed by the UI when this event was created, not a persisted answer revision. */
    val expectedRevision: Int? = null,
)

@Serializable
data class WizChatProgress(
    val schemaVersion: Int = 2,
    val scriptVersion: Int = 1,
    val draftScope: String = "",
    val stage: WizChatStage = WizChatStage.PROFILE,
    val currentQuestionId: WizChatQuestionId = WizChatQuestionId.P_NAME,
    val completedStages: Set<WizChatStage> = emptySet(),
    val acceptedAnswers: List<WizChatAnswerRecord> = emptyList(),
    val advancedBranches: Set<String> = emptySet(),
    val soundEnabled: Boolean = true,
    val revision: Int = 0,
    val terminal: Boolean = false,
)

enum class WizChatMachineState {
    Loading,
    AwaitingAnswer,
    PersistingAnswer,
    PreparingPreview,
    Reviewing,
    Committing,
    Committed,
    RecoverableError,
    UnsupportedDraft,
}

data class WizChatQuestion(
    val id: WizChatQuestionId,
    val stage: WizChatStage,
    val prompt: String,
    val kind: WizChatAnswerKind,
    val options: List<String> = emptyList(),
    val unit: String? = null,
    val allowSkip: Boolean = false,
    val suggestedValue: String? = null,
)

data class WizChatMessage(
    val id: String,
    val stage: WizChatStage,
    val fromUser: Boolean,
    val text: String,
    val questionId: WizChatQuestionId? = null,
    val variantId: String? = null,
)
