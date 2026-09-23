package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.onboarding.WizChatAnswerKind
import com.example.kpkn.domain.onboarding.WizChatAnswerRecord
import com.example.kpkn.domain.onboarding.WizChatAnswerSource
import com.example.kpkn.domain.onboarding.WizChatProgress
import com.example.kpkn.domain.onboarding.WizChatQuestionId
import com.example.kpkn.domain.onboarding.WizChatStage
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupPendingNutritionTest {
    private fun professionalDraft(includeNutrition: Boolean = false): SetupWizardDraft {
        val nutrition = NutritionWizardDraft(mode = "professional").copy(
            direction = PlanDirection.PROFESSIONAL,
            manualCalorieTargetText = "2100",
            manualProteinText = "160",
            equationSex = EerSex.MALE,
            activity = EerActivity.ACTIVE,
        )
        val nStart = WizChatAnswerRecord(
            questionId = WizChatQuestionId.N_START,
            kind = WizChatAnswerKind.CHOICE,
            textValue = "Tengo indicaciones de un profesional",
            source = WizChatAnswerSource.DECLARED,
            revision = 4,
        )
        return SetupWizardDraft(
            draftId = "setup-wizard:full",
            commitId = "commit-1",
            draftScope = "full",
            includeTraining = true,
            includeNutrition = includeNutrition,
            nutritionMode = "professional",
            nutritionDraft = nutrition,
            wizChat = WizChatProgress(
                draftScope = "full",
                stage = WizChatStage.NUTRITION,
                currentQuestionId = WizChatQuestionId.N_RESULT,
                acceptedAnswers = listOf(nStart),
                revision = 9,
            ),
        )
    }

    @Test
    fun postponingProfessionalKeepsDraftForResume() {
        assertTrue(SetupPendingNutrition.shouldPreserve(professionalDraft(includeNutrition = false)))
        assertFalse(SetupPendingNutrition.shouldPreserve(professionalDraft(includeNutrition = true)))
    }

    @Test
    fun completingOrSkippingNonProfessionalDoesNotCreatePending() {
        val createMode = professionalDraft(includeNutrition = false).copy(
            nutritionMode = "create",
            nutritionDraft = NutritionWizardDraft(mode = "create"),
        )
        assertFalse(SetupPendingNutrition.shouldPreserve(createMode))

        val noNutrition = professionalDraft(includeNutrition = false).copy(nutritionDraft = null)
        assertFalse(SetupPendingNutrition.shouldPreserve(noNutrition))
    }

    @Test
    fun pendingDraftLandsOnNResultWithNutritionOnlyScope() {
        val source = professionalDraft(includeNutrition = false)
        val pending = SetupPendingNutrition.build(source)

        assertTrue(SetupPendingNutrition.isPendingId(pending.draftId))
        assertEquals("setup-wizard:pending_nutrition:commit-1", pending.draftId)
        assertEquals("nutrition_only", pending.draftScope)
        assertEquals(WizChatQuestionId.N_RESULT, pending.wizChat.currentQuestionId)
        assertEquals(WizChatStage.NUTRITION, pending.wizChat.stage)
        assertEquals("nutrition_only", pending.wizChat.draftScope)
        assertEquals("professional", pending.nutritionMode)
        assertEquals("professional", pending.nutritionDraft?.mode)
        assertEquals("2100", pending.nutritionDraft?.manualCalorieTargetText)
        assertTrue(pending.includeNutrition)
        assertFalse(pending.includeTraining)
        assertFalse(pending.activateProgram)
        assertNull(pending.ringsAnswers)
        assertNull(pending.volumeCalibrationProfile)
        assertEquals(source.commitId.let { "setup-wizard:pending_nutrition:$it" }, pending.draftId)
        assertFalse(pending.commitId.isBlank())
        assertTrue(pending.commitId != source.commitId)
        assertEquals(1, pending.revision)
        assertEquals(1, pending.wizChat.revision)
        assertFalse(pending.wizChat.terminal)
        assertEquals(
            listOf(WizChatQuestionId.N_START),
            pending.wizChat.acceptedAnswers.map { it.questionId },
        )
        assertNull(pending.wizChat.acceptedAnswers.first().expectedRevision)
    }

    @Test
    fun pendingDraftIgnoresStaleExpectedRevisionOnCarriedAnswer() {
        val source = professionalDraft(includeNutrition = false)
        val pending = SetupPendingNutrition.build(source)
        assertNull(pending.wizChat.acceptedAnswers.single().expectedRevision)
        assertEquals(WizChatAnswerSource.DECLARED, pending.wizChat.acceptedAnswers.single().source)
    }

    @Test
    fun deferringProfessionalAfterSelectingItClearsPendingPreservation() {
        val professional = SetupPendingNutrition.applyNStart(
            professionalDraft(includeNutrition = true),
            "Tengo indicaciones de un profesional",
        )
        assertTrue(professional.includeNutrition)
        assertEquals("professional", professional.nutritionMode)
        assertFalse(SetupPendingNutrition.shouldPreserve(professional))
        assertTrue(SetupPendingNutrition.shouldPreserve(professional.copy(includeNutrition = false)))

        val deferred = SetupPendingNutrition.applyNStart(professional, "Lo haré después")
        assertFalse(deferred.includeNutrition)
        assertEquals("create", deferred.nutritionMode)
        assertNull(deferred.nutritionDraft)
        assertFalse(SetupPendingNutrition.shouldPreserve(deferred))
    }

    @Test
    fun choosingAutomaticNutritionClearsLeftoverProfessionalMode() {
        val fromProfessional = professionalDraft(includeNutrition = true)
        val automatic = SetupPendingNutrition.applyNStart(fromProfessional, "Sí, preparar mis referencias")
        assertTrue(automatic.includeNutrition)
        assertEquals("create", automatic.nutritionMode)
        assertEquals("create", automatic.nutritionDraft?.mode)
        assertFalse(SetupPendingNutrition.shouldPreserve(automatic.copy(includeNutrition = false)))
    }
}
