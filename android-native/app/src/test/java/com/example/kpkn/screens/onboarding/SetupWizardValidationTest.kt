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
}
