package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La ruta de Entreno para cada uno de los diez perfiles, con experiencia intermedia y con principiante (los pasos del punto de
 * partida salen SOLO de datos del borrador: perfil, experiencia y material).
 */
class SetupStartingPointRouteTest {

    private fun route(profile: TrainingGoalProfile, experience: SetupExperience): List<SetupStepId> {
        val draft = SetupWizardDraft()
            .withPlaces(setOf(TrainingPlace.GYM))
            .withGoalProfile(profile)
            .copy(experience = experience)
        return SetupStepGraph.stepIds(draft.stepContext())
    }

    @Test
    fun theVariantsStepAppearsOnlyForPowerliftingAndPowerbuildingFromIntermediate() {
        for (profile in TrainingGoalProfile.entries) {
            val expected = profile == TrainingGoalProfile.POWERLIFTING || profile == TrainingGoalProfile.POWERBUILDING
            assertEquals("$profile intermedio", expected, SetupStepId.TRAINING_VARIANTS in route(profile, SetupExperience.INTERMEDIATE))
            assertFalse("$profile principiante", SetupStepId.TRAINING_VARIANTS in route(profile, SetupExperience.NEW))
        }
    }

    @Test
    fun theMarksStepFollowsTheProfileAndNeverAppearsForABeginner() {
        val asksMarks = setOf(
            TrainingGoalProfile.POWERLIFTING, TrainingGoalProfile.POWERBUILDING, TrainingGoalProfile.STRENGTH_MUSCLE,
            TrainingGoalProfile.WEIGHTLIFTING, TrainingGoalProfile.STRONGMAN,
        )
        for (profile in TrainingGoalProfile.entries) {
            assertEquals(
                "$profile intermedio",
                profile in asksMarks,
                SetupStepId.TRAINING_MAX in route(profile, SetupExperience.INTERMEDIATE),
            )
            assertFalse("$profile principiante", SetupStepId.TRAINING_MAX in route(profile, SetupExperience.NEW))
            assertFalse("$profile volviendo", SetupStepId.TRAINING_MAX in route(profile, SetupExperience.RETURNING))
        }
    }

    @Test
    fun theAvoidStepBelongsToBodybuildingAndTheArmStepToArmwrestling() {
        for (profile in TrainingGoalProfile.entries) {
            for (experience in listOf(SetupExperience.INTERMEDIATE, SetupExperience.NEW)) {
                val steps = route(profile, experience)
                assertEquals("$profile $experience", profile == TrainingGoalProfile.BODYBUILDING, SetupStepId.AVOIDED_EXERCISES in steps)
                assertEquals("$profile $experience", profile == TrainingGoalProfile.ARMWRESTLING, SetupStepId.ARM_STYLE in steps)
            }
        }
    }

    @Test
    fun theCardioBaseFollowsTheCardioGoalsRightAfterTheCardioTime() {
        for (profile in TrainingGoalProfile.entries) {
            val steps = route(profile, SetupExperience.INTERMEDIATE)
            val withCardio = profile == TrainingGoalProfile.STRENGTH_CARDIO || profile == TrainingGoalProfile.FUNCTIONAL_HEALTH
            assertEquals("$profile", withCardio, SetupStepId.CARDIO_BASE in steps)
            if (withCardio) {
                assertEquals("$profile", steps.indexOf(SetupStepId.CARDIO_TIME) + 1, steps.indexOf(SetupStepId.CARDIO_BASE))
            }
        }
    }

    @Test
    fun theNewStepsKeepTheirPlaceBetweenPrioritiesAndThePlan() {
        val powerbuilding = route(TrainingGoalProfile.POWERBUILDING, SetupExperience.INTERMEDIATE)
        assertTrue(powerbuilding.indexOf(SetupStepId.PRIORITIES) < powerbuilding.indexOf(SetupStepId.TRAINING_VARIANTS))
        assertTrue(powerbuilding.indexOf(SetupStepId.TRAINING_VARIANTS) < powerbuilding.indexOf(SetupStepId.TRAINING_MAX))
        assertTrue(powerbuilding.indexOf(SetupStepId.TRAINING_MAX) < powerbuilding.indexOf(SetupStepId.PLAN))

        val bodybuilding = route(TrainingGoalProfile.BODYBUILDING, SetupExperience.INTERMEDIATE)
        assertTrue(bodybuilding.indexOf(SetupStepId.PRIORITIES) < bodybuilding.indexOf(SetupStepId.AVOIDED_EXERCISES))
        assertTrue(bodybuilding.indexOf(SetupStepId.AVOIDED_EXERCISES) < bodybuilding.indexOf(SetupStepId.PLAN))

        val armwrestling = route(TrainingGoalProfile.ARMWRESTLING, SetupExperience.INTERMEDIATE)
        assertTrue(armwrestling.indexOf(SetupStepId.PRIORITIES) < armwrestling.indexOf(SetupStepId.ARM_STYLE))
        assertTrue(armwrestling.indexOf(SetupStepId.ARM_STYLE) < armwrestling.indexOf(SetupStepId.PLAN))
    }

    @Test
    fun theDefaultRouteDoesNotGainAnyStartingPointStep() {
        val steps = SetupStepGraph.stepIds(SetupStepContext())
        for (step in listOf(
            SetupStepId.TRAINING_VARIANTS, SetupStepId.AVOIDED_EXERCISES, SetupStepId.ARM_STYLE, SetupStepId.CARDIO_BASE,
        )) {
            assertFalse("$step", step in steps)
        }
    }
}
