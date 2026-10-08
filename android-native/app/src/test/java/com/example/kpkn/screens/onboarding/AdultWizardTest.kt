package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AdultWizardTest {
    @Test fun numericAgeRejectsMinorInWizardAndWizChat() {
        val question = WizChatQuestion(WizChatQuestionId.P_AGE, WizChatStage.PROFILE, "Age", WizChatAnswerKind.NUMBER)
        listOf(13, 17).forEach { age ->
            assertNotNull(WizChatValidation.validate(question, number = age.toDouble()))
            assertTrue(SetupWizardValidation.validate(SetupWizardDraft(ageYears = age), SetupWizardChapter.PROFILE).containsKey("age"))
        }
        assertNull(WizChatValidation.validate(question, number = 18.0))
        assertFalse(SetupWizardValidation.validate(SetupWizardDraft(ageYears = 18), SetupWizardChapter.PROFILE).containsKey("age"))
    }
    @Test fun birthDateRequiresEighteenthBirthday() {
        val today = LocalDate.now()
        fun errors(date: LocalDate) = SetupWizardValidation.validate(SetupWizardDraft(birthDateIso = date.toString()), SetupWizardChapter.PROFILE)
        assertTrue(errors(today.minusYears(18).plusDays(1)).containsKey("birthDate"))
        assertFalse(errors(today.minusYears(18)).containsKey("birthDate"))
        assertTrue(errors(today.minusYears(13)).containsKey("birthDate"))
    }
}
