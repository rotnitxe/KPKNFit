package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.onboarding.PendingNutritionDraft
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.domain.onboarding.WizChatProgress
import com.example.kpkn.domain.onboarding.WizChatQuestionId
import com.example.kpkn.domain.onboarding.WizChatStage
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import java.util.UUID

/** Pure helpers to preserve a deferred professional (kcal-only) nutrition draft across the main setup commit. */
object SetupPendingNutrition {
    fun isPendingId(draftId: String): Boolean = SetupDraftResolver.isPendingNutritionDraft(draftId)

    fun shouldPreserve(draft: SetupWizardDraft): Boolean =
        !draft.includeNutrition &&
            draft.nutritionMode == "professional" &&
            draft.nutritionDraft != null

    /** Applies the N_START choice: defer clears professional mode so no pending draft is created. */
    fun applyNStart(draft: SetupWizardDraft, text: String?): SetupWizardDraft = when (text) {
        "Lo haré después", "Conservar plan actual" -> draft.copy(
            includeNutrition = false,
            nutritionMode = "create",
            nutritionDraft = null,
        )
        "Tengo indicaciones de un profesional" -> draft.copy(
            includeNutrition = true,
            nutritionMode = "professional",
            nutritionDraft = (draft.nutritionDraft ?: NutritionWizardDraft(mode = "professional"))
                .copy(mode = "professional", direction = PlanDirection.PROFESSIONAL),
        )
        else -> {
            val base = draft.nutritionDraft ?: NutritionWizardDraft(mode = "create", planId = draft.nutritionPlanId)
            draft.copy(
                includeNutrition = true,
                nutritionMode = "create",
                nutritionDraft = base.copy(
                    mode = "create",
                    direction = base.direction.takeUnless { it == PlanDirection.PROFESSIONAL },
                ),
            )
        }
    }

    fun build(source: SetupWizardDraft): SetupWizardDraft {
        val nStartAnswer = source.wizChat.acceptedAnswers
            .lastOrNull { it.questionId == WizChatQuestionId.N_START }
            ?.copy(expectedRevision = null)
        val accepted = listOfNotNull(nStartAnswer)
        return source.copy(
            draftId = SetupDraftResolver.pendingNutritionDraftId(source.commitId),
            commitId = UUID.randomUUID().toString(),
            revision = 1,
            draftScope = "nutrition_only",
            includeTraining = false,
            includeNutrition = true,
            programRoute = SetupProgramRoute.LATER,
            trainingPath = null,
            selectedCatalogId = null,
            sessions = emptyList(),
            activateProgram = false,
            ringsAnswers = null,
            volumeCalibrationProfile = null,
            manualMuscleOverrides = emptyMap(),
            manualEnergyOverride = null,
            manualStructureOverride = null,
            wizChat = WizChatProgress(
                schemaVersion = 2,
                scriptVersion = source.wizChat.scriptVersion,
                draftScope = "nutrition_only",
                stage = WizChatStage.NUTRITION,
                currentQuestionId = WizChatQuestionId.N_RESULT,
                completedStages = emptySet(),
                acceptedAnswers = accepted,
                advancedBranches = emptySet(),
                soundEnabled = source.wizChat.soundEnabled,
                revision = 1,
                terminal = false,
            ),
        )
    }

    fun toCommitField(draft: SetupWizardDraft, payloadJson: String): PendingNutritionDraft =
        PendingNutritionDraft(
            draftId = draft.draftId,
            payloadJson = payloadJson,
            revision = draft.revision.toLong(),
            catalogRevision = draft.catalogRevision,
        )
}
