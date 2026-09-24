package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.onboarding.SetupDraftScope
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupPreviewKind
import com.example.kpkn.domain.onboarding.SetupProgressOrigin
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatAnswerSource
import com.example.kpkn.domain.onboarding.WizChatGraph
import com.example.kpkn.domain.onboarding.WizChatQuestionId

/**
 * Pure compatibility rules for persisted drafts: age, height and weight must be
 * explicitly declared in flows that collect profile vitals, so old drafts that
 * omitted them are rewound to ask only the pending data.
 */
object SetupDraftCompatibility {
    private val mandatoryOrder = listOf(WizChatQuestionId.P_AGE, WizChatQuestionId.P_HEIGHT, WizChatQuestionId.P_WEIGHT)

    fun collectsProfileVitals(scope: String): Boolean =
        SetupDraftResolver.scopeOf(scope) in setOf(SetupDraftScope.FULL, SetupDraftScope.TRAINING_ONLY)

    fun declaredVitals(draft: SetupWizardDraft): Set<WizChatQuestionId> = draft.wizChat.acceptedAnswers
        .filter { it.questionId in mandatoryOrder && it.source != WizChatAnswerSource.OMITTED && it.numberValue != null }
        .mapTo(mutableSetOf()) { it.questionId }

    fun pendingMandatoryVitals(draft: SetupWizardDraft): List<WizChatQuestionId> =
        if (!collectsProfileVitals(draft.draftScope)) emptyList()
        else mandatoryOrder.filterNot { it in declaredVitals(draft) }

    fun repair(draft: SetupWizardDraft): SetupWizardDraft =
        repairStepProgress(repairGoalStyleConflict(restoreMandatoryVitals(draft)))

    /**
     * Maps the legacy `currentQuestionId` onto the stable wizard step so old
     * WizChat drafts resume exactly where they stopped. Drafts that cannot be
     * placed inside the step route keep every legacy payload untouched and are
     * marked NOT_CONVERTIBLE: they survive until the user explicitly discards
     * them.
     */
    fun repairStepProgress(draft: SetupWizardDraft): SetupWizardDraft {
        if (draft.stepProgress.origin != SetupProgressOrigin.NOT_CONVERTIBLE) return draft
        val provenance = draft.wizChat.acceptedAnswers.mapNotNull { record ->
            SetupStepGraph.stepForQuestion(record.questionId)?.let { step ->
                step to SetupAnswerProvenance.fromLegacy(record.source)
            }
        }.toMap()
        val migrated = SetupStepGraph.migrateFromLegacy(
            draft.wizChat.currentQuestionId, draft.stepContext(), provenance)
        return if (migrated == draft.stepProgress) draft
        else draft.copy(stepProgress = migrated.copy(revision = draft.stepProgress.revision + 1))
    }

    /**
     * Resume rule when the plan catalog changed while the draft was stored:
     * every answer is kept, the selection that may no longer exist is cleared
     * only when its plan is really gone, and the step is marked for review with
     * the dependent previews stale. Returns the draft untouched when the
     * catalog did not change or the draft never selected a plan.
     */
    fun applyCatalogRevision(
        draft: SetupWizardDraft,
        persistedRevision: String?,
        currentRevision: String,
        planExists: (String) -> Boolean,
    ): SetupWizardDraft {
        if (persistedRevision == null || persistedRevision == currentRevision) return draft
        val selected = draft.selectedCatalogId ?: return draft
        return draft.copy(
            catalogRevision = currentRevision,
            selectedCatalogId = selected.takeIf(planExists),
            stepProgress = draft.stepProgress
                .withPendingReview(setOf(SetupStepId.PLAN))
                .withStalePreviews(setOf(
                    SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.EXERCISES,
                    SetupPreviewKind.LOADS, SetupPreviewKind.WARMUPS, SetupPreviewKind.SPLIT,
                    SetupPreviewKind.RECIPE, SetupPreviewKind.MARKS,
                )),
            wizChat = draft.wizChat.copy(terminal = false, revision = draft.wizChat.revision + 1),
        )
    }

    private fun repairGoalStyleConflict(draft: SetupWizardDraft): SetupWizardDraft {
        val goal = draft.goal ?: return draft
        val inferred = goal.inferredTrainingStyle ?: return draft
        val answers = draft.volumeAnswers
        if (answers.style == inferred) return draft
        // Old flows could keep a style that contradicts the goal; the goal wins
        // and the volume reference is recalibrated with the same engine.
        val fixed = answers.copy(style = inferred)
        val profile = rebuiltVolumeProfile(fixed)
        return draft.copy(
            volumeAnswers = fixed,
            volumeCalibrationProfile = profile,
            volumeRecommendations = profile?.recommendations.orEmpty(),
            athleteProfileScore = profile?.athleteProfileScore,
            wizChat = draft.wizChat.copy(
                acceptedAnswers = draft.wizChat.acceptedAnswers.filterNot { it.questionId == WizChatQuestionId.T_STYLE },
            ),
        )
    }

    private fun rebuiltVolumeProfile(answers: SetupVolumeAnswers): com.example.kpkn.data.models.VolumeCalibrationProfile? {
        val style = answers.style ?: return null
        val technique = answers.technique ?: return null
        val consistency = answers.consistency ?: return null
        val strength = answers.strength ?: return null
        val mobility = answers.mobility ?: return null
        val output = com.example.kpkn.domain.training.VolumeCalibrationEngine.calculate(style, technique, consistency, strength, mobility)
        return com.example.kpkn.data.models.VolumeCalibrationProfile(
            style,
            output.score,
            com.example.kpkn.data.models.VolumeCalibrationResponses(
                technique, consistency, strength, mobility,
                answers.responseState.takeIf { it != com.example.kpkn.data.models.CalibrationResponseState.UNKNOWN }
                    ?: com.example.kpkn.data.models.CalibrationResponseState.DECLARED,
            ),
            output.recommendations,
            System.currentTimeMillis(),
            com.example.kpkn.domain.training.VolumeCalibrationEngine.REVISION,
        )
    }

    private fun restoreMandatoryVitals(draft: SetupWizardDraft): SetupWizardDraft {
        if (!collectsProfileVitals(draft.draftScope)) return draft
        val progress = draft.wizChat
        val keptAnswers = progress.acceptedAnswers.filterNot { record ->
            record.questionId in mandatoryOrder &&
                (record.source == WizChatAnswerSource.OMITTED || record.numberValue == null)
        }
        val declared = keptAnswers
            .filter { it.questionId in mandatoryOrder && it.numberValue != null }
            .associate { it.questionId to it.numberValue!! }
        val cleaned = draft.copy(
            ageYears = declared[WizChatQuestionId.P_AGE]?.toInt() ?: draft.ageYears,
            heightCm = declared[WizChatQuestionId.P_HEIGHT] ?: draft.heightCm,
            weightKg = declared[WizChatQuestionId.P_WEIGHT] ?: draft.weightKg,
            wizChat = progress.copy(acceptedAnswers = keptAnswers),
        )
        val pending = mandatoryOrder.filterNot { it in declared.keys }
        if (pending.isEmpty()) return cleaned
        val firstPending = pending.first()
        val progressedPast = progress.terminal || progress.currentQuestionId.ordinal > firstPending.ordinal
        if (!progressedPast) return cleaned
        return cleaned.copy(
            wizChat = cleaned.wizChat.copy(
                currentQuestionId = firstPending,
                stage = WizChatGraph.stageFor(firstPending),
                terminal = false,
                revision = progress.revision + 1,
            ),
        )
    }
}
