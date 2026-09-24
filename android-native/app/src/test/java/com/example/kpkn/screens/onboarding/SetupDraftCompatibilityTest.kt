package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupPreviewKind
import com.example.kpkn.domain.onboarding.SetupProgressOrigin
import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.WizChatAnswerKind
import com.example.kpkn.domain.onboarding.WizChatAnswerRecord
import com.example.kpkn.domain.onboarding.WizChatAnswerSource
import com.example.kpkn.domain.onboarding.WizChatProgress
import com.example.kpkn.domain.onboarding.WizChatQuestionId
import com.example.kpkn.domain.onboarding.WizChatStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val repaired = SetupDraftCompatibility.repair(rings)
        // La reparación solo migra el progreso de pasos: el payload legacy se
        // conserva completo y no se piden vitales.
        assertEquals(rings.wizChat, repaired.wizChat)
        assertNull(repaired.ageYears)
        assertNull(repaired.heightCm)
        assertNull(repaired.weightKg)
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

    @Test
    fun legacyWizChatDraftMigratesToTheMatchingStepAndKeepsEveryAnswer() {
        val old = draft(
            current = WizChatQuestionId.T_DAYS,
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
                WizChatAnswerRecord(WizChatQuestionId.P_EXPERIENCE, WizChatAnswerKind.CHOICE,
                    textValue = "Tengo experiencia", revision = 4),
            ),
        )
        val repaired = SetupDraftCompatibility.repair(old)

        assertEquals(SetupStepId.DAYS, repaired.stepProgress.currentStepId)
        assertEquals(SetupWizardBlock.TRAINING, repaired.stepProgress.block)
        assertEquals(setOf(SetupWizardBlock.BASICS), repaired.stepProgress.completedBlocks)
        assertEquals(SetupProgressOrigin.MIGRATED_FROM_WIZCHAT, repaired.stepProgress.origin)
        // La migración conserva todas las respuestas del flujo antiguo.
        assertEquals(old.wizChat.acceptedAnswers, repaired.wizChat.acceptedAnswers)
        assertEquals(old.ageYears, repaired.ageYears)
        assertEquals(old.weightKg, repaired.weightKg)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, repaired.stepProgress.answers[SetupStepId.AGE])
    }

    @Test
    fun nonConvertibleDraftsArePreservedUntilTheUserDiscardsThem() {
        val broken = draft(
            scope = "nutrition_only",
            current = WizChatQuestionId.T_DAYS,
            answers = listOf(answer(WizChatQuestionId.P_WEIGHT, 72.0)),
            age = null,
            height = null,
        ).copy(includeTraining = false, selectedCatalogId = "plan-legacy")
        val repaired = SetupDraftCompatibility.repair(broken)

        assertEquals(SetupProgressOrigin.NOT_CONVERTIBLE, repaired.stepProgress.origin)
        // Ningún dato se borra: el borrador se conserva tal cual.
        assertEquals(broken.wizChat, repaired.wizChat)
        assertEquals(broken.weightKg, repaired.weightKg)
        assertEquals("plan-legacy", repaired.selectedCatalogId)
        assertEquals(SetupStepId.DAYS, repaired.stepProgress.currentStepId)
    }

    @Test
    fun stepMigrationIsIdempotentAndNativeProgressIsLeftUntouched() {
        val old = draft(
            current = WizChatQuestionId.T_DAYS,
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
            ),
        )
        val migrated = SetupDraftCompatibility.repair(old)
        assertEquals(migrated.stepProgress, SetupDraftCompatibility.repair(migrated).stepProgress)

        val rings = draft(
            scope = "rings_only",
            current = WizChatQuestionId.R_RECENT,
            answers = emptyList(),
            age = null,
            height = null,
            weight = null,
        )
        val native = rings.copy(
            stepProgress = SetupStepProgress.initial(rings.stepContext())
                .at(SetupStepId.RINGS_MUSCLE_FEELING, rings.stepContext()),
        )
        assertEquals(native, SetupDraftCompatibility.repair(native))
    }

    @Test
    fun catalogRevisionChangeKeepsEveryAnswerAndOnlyFlagsTheSelectionForReview() {
        val base = draft(
            answers = listOf(
                answer(WizChatQuestionId.P_AGE, 30.0),
                answer(WizChatQuestionId.P_HEIGHT, 175.0),
                answer(WizChatQuestionId.P_WEIGHT, 72.0),
            ),
        ).copy(
            selectedCatalogId = "plan-v1",
            stepProgress = SetupStepProgress.initial(SetupStepContext(nutritionStarted = true))
                .at(SetupStepId.PLAN, SetupStepContext(nutritionStarted = true)),
        )

        // Sin cambio de catálogo no se toca nada.
        assertEquals(base, SetupDraftCompatibility.applyCatalogRevision(base, "rev-1", "rev-1") { true })

        // El plan desapareció del catálogo nuevo: se conserva todo el borrador
        // y solo se marca la selección como pendiente de revisión.
        val missing = SetupDraftCompatibility.applyCatalogRevision(base, "rev-1", "rev-2") { false }
        assertEquals("rev-2", missing.catalogRevision)
        assertNull(missing.selectedCatalogId)
        assertEquals(base.wizChat.acceptedAnswers, missing.wizChat.acceptedAnswers)
        assertEquals(base.ageYears, missing.ageYears)
        assertEquals(base.weightKg, missing.weightKg)
        assertEquals(base.stepProgress.answers, missing.stepProgress.answers)
        assertEquals(SetupStepId.PLAN, missing.stepProgress.currentStepId)
        assertTrue(SetupStepId.PLAN in missing.stepProgress.pendingReview)
        assertTrue(SetupPreviewKind.PLAN_CANDIDATES in missing.stepProgress.stalePreviews)
        assertTrue(SetupPreviewKind.EXERCISES in missing.stepProgress.stalePreviews)
        assertFalse(missing.wizChat.terminal)

        // El plan sigue existiendo: se conserva la selección y se pide revisar.
        val kept = SetupDraftCompatibility.applyCatalogRevision(base, "rev-1", "rev-2") { it == "plan-v1" }
        assertEquals("plan-v1", kept.selectedCatalogId)
        assertTrue(SetupStepId.PLAN in kept.stepProgress.pendingReview)
        assertEquals(base.wizChat.acceptedAnswers, kept.wizChat.acceptedAnswers)
    }
}
