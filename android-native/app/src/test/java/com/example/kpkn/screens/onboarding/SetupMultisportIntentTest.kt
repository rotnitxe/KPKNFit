package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.multisport.GoalPriority
import com.example.kpkn.domain.multisport.SportExperience
import com.example.kpkn.domain.multisport.SportGoal
import com.example.kpkn.domain.multisport.SportGoalTarget
import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.TrainingIntent
import com.example.kpkn.domain.multisport.TrainingIntentPolicy
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SetupMultisportIntentTest {
    private val today = LocalDate.of(2026, 10, 18)
    private val json = Json { ignoreUnknownKeys = true }

    private fun intent(primarySport: SportId = SportId.POWERLIFTING) = TrainingIntent(
        goals = listOf(
            SportGoal("run", SportId.RUNNING, "Terminar 10 km", GoalPriority.MAINTAIN,
                SportExperience.BEGINNER, SportGoalTarget("km", 10.0)),
            SportGoal("main", primarySport, "Mejorar mi rendimiento", GoalPriority.PROGRESS,
                SportExperience.ADVANCED),
        ),
        primaryGoalId = "main",
    )

    @Test fun legacyDraftDoesNotInventSportSelections() {
        val draft = json.decodeFromString<SetupWizardDraft>("""{"goal":"MIXED"}""")
        assertNull(draft.trainingIntent)
        assertNull(SetupDraftCompatibility.repair(draft).trainingIntent)
        assertNull(SetupMultisportIntent.primaryLegacyProfile(null))
    }

    @Test fun draftRoundTripAndLegacyEditPreserveEveryOrderedGoal() {
        val original = intent()
        val draft = SetupWizardDraft(trainingIntent = original)
        val reopened = json.decodeFromString<SetupWizardDraft>(json.encodeToString(draft))
        val edited = reopened.withGoalProfile(TrainingGoalProfile.BODYBUILDING)
            .withWeekdays(setOf(1, 4))
        assertEquals(original, edited.trainingIntent)
        assertEquals(original, SetupDraftCompatibility.repair(edited).trainingIntent)
        assertEquals(listOf("run", "main"), edited.trainingIntent!!.goals.map { it.id })
    }

    @Test fun activationStoresIntentWithoutChangingAuthoredProgramContent() {
        val program = Program("p", "Plan de autor", author = "Autor", sourceProtocolId = "authored")
        val original = intent()
        val activated = SetupActivationPayload.programForActivation(
            program, SetupWizardDraft(trainingIntent = original), today,
        )
        assertEquals(original, activated.trainingIntent)
        assertEquals(program.copy(trainingIntent = original), activated)
    }

    @Test fun legacyOnlyDraftDoesNotEraseExistingProgramIntent() {
        val program = Program("p", "Guardado", trainingIntent = intent())
        assertEquals(program, SetupActivationPayload.programForActivation(program, SetupWizardDraft(), today))
        assertNull(SetupActivationPayload.programForActivation(
            Program("legacy", "Antiguo"), SetupWizardDraft(), today,
        ).trainingIntent)
    }

    @Test fun competitionCalendarActivationAlsoRetainsOrderedIntent() {
        val original = intent()
        val draft = SetupWizardDraft(
            trainingIntent = original,
            goalProfile = TrainingGoalProfile.POWERLIFTING,
            experience = SetupExperience.ADVANCED,
            competition = CompetitionAnswer.WITH_DATE,
            meetDateIso = "2026-12-20",
            meetName = "Encuentro",
        )
        val activated = SetupActivationPayload.programForActivation(Program("p", "Plan"), draft, today)
        assertEquals(original, activated.trainingIntent)
        assertTrue(activated.keyDates.any { it.title == "Encuentro" })
    }

    @Test fun invalidExplicitIntentRejectsActivationWithOriginalDomainErrors() {
        val invalid = TrainingIntent(goals = intent().goals, primaryGoalId = "missing")
        val draft = SetupWizardDraft(trainingIntent = invalid)
        val error = assertThrows(InvalidSetupTrainingIntentException::class.java) {
            SetupActivationPayload.programForActivation(Program("p", "Plan"), draft, today)
        }
        assertEquals(TrainingIntentPolicy.validate(invalid), error.errors)
        assertSame(invalid, draft.trainingIntent)
        assertThrows(InvalidSetupTrainingIntentException::class.java) {
            SetupMultisportIntent.primaryLegacyProfile(invalid)
        }
    }

    @Test fun invalidSecondaryGoalCannotBeSilentlyReducedToValidPrimary() {
        val original = intent()
        val invalid = TrainingIntent(
            goals = original.goals.map { if (it.id == "run") it.copy(objective = "") else it },
            primaryGoalId = original.primaryGoalId,
        )
        val error = assertThrows(InvalidSetupTrainingIntentException::class.java) {
            SetupActivationPayload.programForActivation(
                Program("p", "Plan"), SetupWizardDraft(trainingIntent = invalid), today,
            )
        }
        assertEquals(TrainingIntentPolicy.validate(invalid), error.errors)
    }

    @Test fun projectionUsesPrimaryIdentityAndOnlyExactLegacySportProfiles() {
        val supported = mapOf(
            SportId.HYPERTROPHY to TrainingGoalProfile.BODYBUILDING,
            SportId.POWERLIFTING to TrainingGoalProfile.POWERLIFTING,
            SportId.POWERBUILDING to TrainingGoalProfile.POWERBUILDING,
            SportId.WEIGHTLIFTING to TrainingGoalProfile.WEIGHTLIFTING,
            SportId.CALISTHENICS to TrainingGoalProfile.CALISTHENICS,
            SportId.STRONGMAN to TrainingGoalProfile.STRONGMAN,
            SportId.ARMWRESTLING to TrainingGoalProfile.ARMWRESTLING,
        )
        for (sport in SportId.entries) {
            assertEquals(sport.name, supported[sport], SetupMultisportIntent.primaryLegacyProfile(intent(sport)))
        }
    }
}
