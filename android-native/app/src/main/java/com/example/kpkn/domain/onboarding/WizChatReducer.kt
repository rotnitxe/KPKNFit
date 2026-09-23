package com.example.kpkn.domain.onboarding

import java.security.MessageDigest

data class WizChatReducerResult(
    val progress: WizChatProgress,
    val accepted: WizChatAnswerRecord,
    val invalidated: Set<WizChatQuestionId>,
)

object WizChatReducer {
    fun accept(
        progress: WizChatProgress,
        questionId: WizChatQuestionId,
        answer: WizChatAnswerRecord,
        nextQuestionId: WizChatQuestionId,
    ): WizChatReducerResult? {
        if (progress.terminal || progress.currentQuestionId != questionId ||
            answer.expectedRevision != null && answer.expectedRevision != progress.revision) return null
        val revision = progress.revision + 1
        val accepted = answer.copy(
            questionId = questionId,
            revision = revision,
            expectedRevision = null,
            variantId = answer.variantId ?: stableVariantId(progress.draftScope, questionId, revision, progress.scriptVersion),
        )
        val answers = progress.acceptedAnswers.filterNot { it.questionId == questionId } + accepted
        val completedStage = WizChatGraph.stageFor(questionId).takeIf { it != WizChatStage.REVIEW }
        val nextProgress = progress.copy(
            currentQuestionId = nextQuestionId,
            stage = WizChatGraph.stageFor(nextQuestionId),
            acceptedAnswers = answers,
            revision = revision,
            completedStages = if (completedStage == null) progress.completedStages else progress.completedStages + completedStage,
        )
        return WizChatReducerResult(nextProgress, accepted, invalidatedAnswersFor(questionId))
    }

    fun invalidatedAnswersFor(questionId: WizChatQuestionId): Set<WizChatQuestionId> = when (questionId) {
        WizChatQuestionId.P_NAME -> emptySet()
        WizChatQuestionId.P_GENDER -> emptySet()
        WizChatQuestionId.P_AGE -> setOf(WizChatQuestionId.N_SEX, WizChatQuestionId.N_ELIGIBILITY, WizChatQuestionId.N_DIRECTION, WizChatQuestionId.N_ACTIVITY, WizChatQuestionId.N_RESULT)
        WizChatQuestionId.P_HEIGHT, WizChatQuestionId.P_WEIGHT -> setOf(WizChatQuestionId.N_RESULT)
        WizChatQuestionId.P_EXPERIENCE -> setOf(WizChatQuestionId.T_PLAN, WizChatQuestionId.T_REVIEW)
        WizChatQuestionId.T_GOAL -> setOf(WizChatQuestionId.T_STYLE, WizChatQuestionId.T_CARDIO_TYPE, WizChatQuestionId.T_CARDIO_TIME, WizChatQuestionId.T_PLAN, WizChatQuestionId.T_REVIEW)
        WizChatQuestionId.T_TIME -> setOf(WizChatQuestionId.T_CARDIO_TIME, WizChatQuestionId.T_PLAN, WizChatQuestionId.T_REVIEW)
        WizChatQuestionId.T_ROUTE, WizChatQuestionId.T_STYLE,
        WizChatQuestionId.T_VOLUME_TECHNIQUE, WizChatQuestionId.T_VOLUME_CONSISTENCY,
        WizChatQuestionId.T_VOLUME_STRENGTH, WizChatQuestionId.T_VOLUME_MOBILITY,
        WizChatQuestionId.T_EQUIPMENT -> setOf(WizChatQuestionId.T_HOME_EQUIPMENT, WizChatQuestionId.T_PLAN, WizChatQuestionId.T_REVIEW)
        WizChatQuestionId.T_DAYS -> setOf(WizChatQuestionId.T_WEEKDAYS, WizChatQuestionId.T_PLAN, WizChatQuestionId.T_REVIEW)
        WizChatQuestionId.T_HOME_EQUIPMENT, WizChatQuestionId.T_WEEKDAYS,
        WizChatQuestionId.T_CARDIO_TYPE, WizChatQuestionId.T_CARDIO_TIME,
        WizChatQuestionId.T_TRAINING_MAX -> setOf(WizChatQuestionId.T_MARKS, WizChatQuestionId.T_PLAN, WizChatQuestionId.T_REVIEW)
        WizChatQuestionId.T_MARKS -> setOf(WizChatQuestionId.T_PLAN, WizChatQuestionId.T_REVIEW)
        WizChatQuestionId.T_PLAN -> setOf(WizChatQuestionId.T_REVIEW)
        WizChatQuestionId.N_START, WizChatQuestionId.N_SEX, WizChatQuestionId.N_ELIGIBILITY,
        WizChatQuestionId.N_DIRECTION, WizChatQuestionId.N_ACTIVITY -> setOf(WizChatQuestionId.N_RESULT, WizChatQuestionId.R_RESULT, WizChatQuestionId.REVIEW)
        WizChatQuestionId.R_START, WizChatQuestionId.R_RECENT, WizChatQuestionId.R_SESSIONS,
        WizChatQuestionId.R_RECENCY, WizChatQuestionId.R_ACTIVITY, WizChatQuestionId.R_INTENSITY,
        WizChatQuestionId.R_AXIAL, WizChatQuestionId.R_FEELINGS_MUSCLE,
        WizChatQuestionId.R_FEELINGS_ENERGY, WizChatQuestionId.R_FEELINGS_STRUCTURE,
        WizChatQuestionId.R_DISCOMFORT -> setOf(WizChatQuestionId.R_RESULT, WizChatQuestionId.REVIEW)
        WizChatQuestionId.T_REVIEW, WizChatQuestionId.N_RESULT, WizChatQuestionId.R_RESULT,
        WizChatQuestionId.REVIEW -> emptySet()
    }

    fun stableVariantId(commitId: String, questionId: WizChatQuestionId, revision: Int, scriptVersion: Int): String {
        val input = "$commitId|${questionId.name}|$revision|$scriptVersion"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }
}
