package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.WizChatAnswerKind
import com.example.kpkn.domain.onboarding.WizChatAnswerRecord
import com.example.kpkn.domain.onboarding.WizChatAnswerSource
import com.example.kpkn.domain.onboarding.WizChatProgress
import com.example.kpkn.domain.onboarding.WizChatQuestionId
import com.example.kpkn.domain.onboarding.WizChatStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupDraftCompatibilityTest {
    private fun answer(
        id: WizChatQuestionId,
        number: Double?,
        source: WizChatAnswerSource = WizChatAnswerSource.DECLARED,
        revision: Int = 1,
    ) = WizChatAnswerRecord(id, WizChatAnswerKind.NUMBER, numberValue = number, source = source, revision = revision)

    private fun draft(
        scope: String = "full",
        current: WizChatQuestionId = WizChatQuestionId.T_DAYS,
        terminal: Boolean = false,
        answers: List<WizChatAnswerRecord>,
        age: Int? = 30,
        height: Double? = 175.0,
        weight: Double? = 72.0,
    ) = SetupWizardDraft(
        draftScope = scope,
        ageYears = age,
        heightCm = height,
        weightKg = weight,
        wizChat = WizChatProgress(
            draftScope = scope,
            currentQuestionId = current,
            stage = com.example.kpkn.domain.onboarding.WizChatGraph.stageFor(current),
            acceptedAnswers = answers,
            terminal = terminal,
        ),
    )

    @Test
    fun oldDraftWithOmittedVitalsRewindsToTheFirstPendingOne() {
        val old = draft(
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, null, WizChatAnswerSource.OMITTED),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, null, WizChatAnswerSource.OMITTED),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)

        assertEquals(WizChatQuestionId.P_AGE, repaired.wizChat.currentQuestionId)
        assertEquals(WizChatStage.PROFILE, repaired.wizChat.stage)
        assertEquals(
            listOf(WizChatQuestionId.P_HEIGHT),
            repaired.wizChat.acceptedAnswers.map { it.questionId },
        )
        assertEquals(
            listOf(WizChatQuestionId.P_AGE, WizChatQuestionId.P_WEIGHT),
            SetupDraftCompatibility.pendingMandatoryVitals(repaired),
        )
    }

    @Test
    fun onlyPendingVitalsAreAskedAgainAfterRepair() {
        val old = draft(
            current = WizChatQuestionId.REVIEW,
            terminal = true,
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 31.0),
                answer(WizChatQuestionId.P_HEIGHT, null, WizChatAnswerSource.OMITTED),
                answer(WizChatQuestionId.P_WEIGHT, 70.4),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)

        assertEquals(WizChatQuestionId.P_HEIGHT, repaired.wizChat.currentQuestionId)
        assertEquals(31, repaired.ageYears)
        assertEquals(70.4, repaired.weightKg!!, 0.0001)
        assertEquals(listOf(WizChatQuestionId.P_HEIGHT), SetupDraftCompatibility.pendingMandatoryVitals(repaired))
    }

    @Test
    fun suggestedProfileValuesDoNotCountAsDeclaredAnswers() {
        val fresh = draft(
            current = WizChatQuestionId.P_NAME,
            answers = emptyList(),
        )
        assertEquals(
            listOf(WizChatQuestionId.P_AGE, WizChatQuestionId.P_HEIGHT, WizChatQuestionId.P_WEIGHT),
            SetupDraftCompatibility.pendingMandatoryVitals(fresh),
        )
        assertFalse(SetupDraftCompatibility.declaredVitals(fresh).contains(WizChatQuestionId.P_AGE))
    }

    @Test
    fun ringsOnlyDraftsNeverAskForProfileVitals() {
        val rings = draft(
            scope = "rings_only",
            current = WizChatQuestionId.R_RECENT,
            answers = emptyList(),
            age = null,
            height = null,
            weight = null,
        )
        assertEquals(emptyList<WizChatQuestionId>(), SetupDraftCompatibility.pendingMandatoryVitals(rings))
        assertEquals(rings, SetupDraftCompatibility.repair(rings))
    }

    @Test
    fun completeDraftsAreLeftUntouched() {
        val complete = draft(
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
            ),
        )
        assertEquals(complete.wizChat.currentQuestionId, SetupDraftCompatibility.repair(complete).wizChat.currentQuestionId)
        assertTrue(SetupDraftCompatibility.pendingMandatoryVitals(complete).isEmpty())
    }

    @Test
    fun contradictoryGoalAndStyleFromOldFlowsResolveToTheGoal() {
        val old = draft(
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
                WizChatAnswerRecord(WizChatQuestionId.T_STYLE, WizChatAnswerKind.CHOICE,
                    textValue = "Hipertrofia", revision = 4),
            ),
        ).copy(
            goal = SetupGoal.STRENGTH,
            volumeAnswers = SetupVolumeAnswers(
                style = com.example.kpkn.data.models.TrainingStyle.BODYBUILDER,
                technique = 2, consistency = 2, strength = 2, mobility = 2,
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)

        assertEquals(com.example.kpkn.data.models.TrainingStyle.POWERLIFTER, repaired.volumeAnswers.style)
        assertTrue(repaired.wizChat.acceptedAnswers.none { it.questionId == WizChatQuestionId.T_STYLE })
        assertEquals(com.example.kpkn.data.models.TrainingStyle.POWERLIFTER, repaired.volumeCalibrationProfile?.trainingStyle)
        assertTrue(repaired.volumeRecommendations.isNotEmpty())
    }

    @Test
    fun broadGoalsKeepTheirExplicitlyChosenFocus() {
        val old = draft(
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
            ),
        ).copy(
            goal = SetupGoal.HEALTH,
            volumeAnswers = SetupVolumeAnswers(
                style = com.example.kpkn.data.models.TrainingStyle.POWERBUILDER,
                technique = 2, consistency = 2, strength = 2, mobility = 2,
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)
        assertEquals(com.example.kpkn.data.models.TrainingStyle.POWERBUILDER, repaired.volumeAnswers.style)
    }
}
