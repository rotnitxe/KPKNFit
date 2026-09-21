package com.example.kpkn.screens.onboarding

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupWizardValidationTest {
    @Test
    fun acceptsLeapDayWhenItRepresentsAnEligibleAge() {
        assertTrue(SetupWizardValidation.validateDate("2000-02-29", LocalDate.of(2025, 2, 28)))
    }

    @Test
    fun rejectsFutureAndUnderageBirthDates() {
        assertFalse(SetupWizardValidation.validateDate("2026-01-01", LocalDate.of(2025, 1, 1)))
        assertFalse(SetupWizardValidation.validateDate("2015-02-28", LocalDate.of(2025, 2, 28)))
    }

    @Test
    fun profileDoesNotAcceptAnUnconfirmedDefaultAge() {
        val draft = SetupWizardDraft(name = "Ana", experience = SetupExperience.NEW)
        assertTrue(SetupWizardValidation.validate(draft, SetupWizardChapter.PROFILE).containsKey("age"))
    }

    @Test
    fun nameIsOptionalWhenProfileDataIsComplete() {
        val draft = SetupWizardDraft(ageYears = 30, experience = SetupExperience.NEW)
        assertFalse(SetupWizardValidation.validate(draft, SetupWizardChapter.PROFILE).containsKey("name"))
    }

    @Test
    fun volumeChapterRequiresEveryCalibrationAnswer() {
        val incomplete = SetupWizardDraft(
            ageYears = 30,
            experience = SetupExperience.NEW,
            volumeAnswers = SetupVolumeAnswers(style = com.example.kpkn.data.models.TrainingStyle.BODYBUILDER),
        )
        val complete = incomplete.copy(
            volumeAnswers = SetupVolumeAnswers(
                style = com.example.kpkn.data.models.TrainingStyle.BODYBUILDER,
                technique = 2,
                consistency = 2,
                strength = 2,
                mobility = 2,
            ),
        )

        assertTrue(SetupWizardValidation.validate(incomplete, SetupWizardChapter.VOLUME).isNotEmpty())
        assertTrue(SetupWizardValidation.validate(complete, SetupWizardChapter.VOLUME).isEmpty())
    }

    @Test
    fun laterProgramRouteSkipsTrainingAndWeekRequirements() {
        val draft = SetupWizardDraft(
            ageYears = 30,
            experience = SetupExperience.NEW,
            includeTraining = false,
            programRoute = SetupProgramRoute.LATER,
        )

        assertTrue(SetupWizardValidation.validate(draft, SetupWizardChapter.TRAINING).isEmpty())
        assertTrue(SetupWizardValidation.validate(draft, SetupWizardChapter.WEEK).isEmpty())
    }
}
