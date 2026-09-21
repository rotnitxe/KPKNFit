package com.example.kpkn.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WizChatStateMachineTest {
    @Test
    fun acceptsOnlyTheCurrentQuestionAndAdvancesDeterministically() {
        val start = WizChatProgress(draftScope = "full")
        val answer = WizChatAnswerRecord(WizChatQuestionId.P_NAME, WizChatAnswerKind.TEXT, textValue = "Ana")
        val result = WizChatReducer.accept(start, WizChatQuestionId.P_NAME, answer, WizChatQuestionId.P_AGE)

        assertEquals(WizChatQuestionId.P_AGE, result?.progress?.currentQuestionId)
        assertEquals("Ana", result?.accepted?.textValue)
        assertTrue(result?.accepted?.variantId?.isNotBlank() == true)
        assertNull(WizChatReducer.accept(start, WizChatQuestionId.P_AGE, answer, WizChatQuestionId.P_HEIGHT))
    }

    @Test
    fun copyVariantIsStableAndRevisionScoped() {
        val first = WizChatReducer.stableVariantId("commit", WizChatQuestionId.P_NAME, 1, 1)
        val repeated = WizChatReducer.stableVariantId("commit", WizChatQuestionId.P_NAME, 1, 1)
        val nextRevision = WizChatReducer.stableVariantId("commit", WizChatQuestionId.P_NAME, 2, 1)
        assertEquals(first, repeated)
        assertNotEquals(first, nextRevision)
    }

    @Test
    fun branchDoesNotInventTrainingWhenUserDefersIt() {
        val next = WizChatGraph.next(
            WizChatQuestionId.T_ROUTE,
            WizChatGraphContext(includeTraining = true, includeNutrition = true, programRouteLater = true),
        )
        assertEquals(WizChatQuestionId.T_REVIEW, next)
    }

    @Test
    fun professionalNutritionSkipsTheAutomaticEerQuestions() {
        val next = WizChatGraph.next(
            WizChatQuestionId.N_START,
            WizChatGraphContext(includeNutrition = true, nutritionProfessional = true),
        )
        assertEquals(WizChatQuestionId.N_RESULT, next)
    }

    @Test
    fun multiChoiceExclusivityIsEnforced() {
        assertEquals("Sin material es una opción exclusiva", WizChatValidation.exclusiveMultiChoice(listOf("Sin material", "Gimnasio completo"), "Sin material", "El equipo"))
        assertNull(WizChatValidation.exclusiveMultiChoice(listOf("Gimnasio completo"), "Sin material", "El equipo"))
    }
}
