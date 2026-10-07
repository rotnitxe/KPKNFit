package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.domain.onboarding.PlanGoalProfile
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Traducciones del perfil de objetivo a lo que ya entiende el resto de la app (objetivo, estilo y tipo de atleta). */
class GoalProfileMappingTest {

    @Test
    fun everyProfileHasTheSetupGoalTheCurrentEngineReads() {
        assertEquals(
            mapOf(
                TrainingGoalProfile.STRENGTH_MUSCLE to SetupGoal.STRENGTH_MUSCLE,
                TrainingGoalProfile.STRENGTH_CARDIO to SetupGoal.COMPLETE_ATHLETE,
                TrainingGoalProfile.FUNCTIONAL_HEALTH to SetupGoal.FUNCTIONAL,
                TrainingGoalProfile.POWERBUILDING to SetupGoal.STRENGTH_MUSCLE,
                TrainingGoalProfile.CALISTHENICS to SetupGoal.CALISTHENICS,
                TrainingGoalProfile.BODYBUILDING to SetupGoal.MUSCLE,
                TrainingGoalProfile.WEIGHTLIFTING to SetupGoal.WEIGHTLIFTING,
                TrainingGoalProfile.ARMWRESTLING to SetupGoal.ARMWRESTLING,
                TrainingGoalProfile.STRONGMAN to SetupGoal.STRONGMAN,
                TrainingGoalProfile.POWERLIFTING to SetupGoal.STRENGTH,
            ),
            TrainingGoalProfile.entries.associateWith(GoalProfileMapping::setupGoalOf),
        )
        // Ningún perfil nuevo cae en los objetivos que solo sirven para leer borradores antiguos.
        TrainingGoalProfile.entries.forEach { profile ->
            assertTrue("$profile", !GoalProfileMapping.setupGoalOf(profile).isLegacyOnly)
        }
    }

    @Test
    fun trainingStyleIsPowerlifterForLiftingDisciplinesBodybuilderForBodyDisciplinesAndPowerbuilderForTheRest() {
        assertEquals(TrainingStyle.POWERLIFTER, GoalProfileMapping.trainingStyleOf(TrainingGoalProfile.POWERLIFTING))
        assertEquals(TrainingStyle.POWERLIFTER, GoalProfileMapping.trainingStyleOf(TrainingGoalProfile.WEIGHTLIFTING))
        assertEquals(TrainingStyle.BODYBUILDER, GoalProfileMapping.trainingStyleOf(TrainingGoalProfile.BODYBUILDING))
        assertEquals(TrainingStyle.BODYBUILDER, GoalProfileMapping.trainingStyleOf(TrainingGoalProfile.CALISTHENICS))
        assertEquals(TrainingStyle.BODYBUILDER, GoalProfileMapping.trainingStyleOf(TrainingGoalProfile.ARMWRESTLING))
        listOf(
            TrainingGoalProfile.STRENGTH_MUSCLE, TrainingGoalProfile.STRENGTH_CARDIO, TrainingGoalProfile.FUNCTIONAL_HEALTH,
            TrainingGoalProfile.POWERBUILDING, TrainingGoalProfile.STRONGMAN,
        ).forEach { profile ->
            assertEquals("$profile", TrainingStyle.POWERBUILDER, GoalProfileMapping.trainingStyleOf(profile))
        }
    }

    @Test
    fun athleteTypeFollowsTheTableOfTheBrief() {
        assertEquals(
            mapOf(
                TrainingGoalProfile.STRENGTH_MUSCLE to AthleteType.ENTHUSIAST,
                TrainingGoalProfile.STRENGTH_CARDIO to AthleteType.HYBRID,
                TrainingGoalProfile.FUNCTIONAL_HEALTH to AthleteType.ENTHUSIAST,
                TrainingGoalProfile.POWERBUILDING to AthleteType.POWERBUILDER,
                TrainingGoalProfile.CALISTHENICS to AthleteType.CALISTHENICS,
                TrainingGoalProfile.BODYBUILDING to AthleteType.BODYBUILDER,
                TrainingGoalProfile.WEIGHTLIFTING to AthleteType.WEIGHTLIFTER,
                TrainingGoalProfile.ARMWRESTLING to AthleteType.ENTHUSIAST,
                TrainingGoalProfile.STRONGMAN to AthleteType.POWERLIFTER,
                TrainingGoalProfile.POWERLIFTING to AthleteType.POWERLIFTER,
            ),
            TrainingGoalProfile.entries.associateWith(GoalProfileMapping::athleteTypeOf),
        )
    }

    @Test
    fun anOldGoalReadsAsTheProfileOfToday() {
        assertEquals(TrainingGoalProfile.POWERLIFTING, GoalProfileMapping.profileOfLegacy(SetupGoal.STRENGTH))
        assertEquals(TrainingGoalProfile.BODYBUILDING, GoalProfileMapping.profileOfLegacy(SetupGoal.MUSCLE))
        assertEquals(TrainingGoalProfile.STRENGTH_MUSCLE, GoalProfileMapping.profileOfLegacy(SetupGoal.STRENGTH_MUSCLE))
        assertEquals(TrainingGoalProfile.STRENGTH_CARDIO, GoalProfileMapping.profileOfLegacy(SetupGoal.COMPLETE_ATHLETE))
        assertEquals(TrainingGoalProfile.STRENGTH_CARDIO, GoalProfileMapping.profileOfLegacy(SetupGoal.MIXED))
        assertEquals(TrainingGoalProfile.FUNCTIONAL_HEALTH, GoalProfileMapping.profileOfLegacy(SetupGoal.HEALTH))
        assertEquals(TrainingGoalProfile.FUNCTIONAL_HEALTH, GoalProfileMapping.profileOfLegacy(SetupGoal.FUNCTIONAL))
        // Todos los objetivos actuales vuelven a ser el mismo al pasar por su perfil (los antiguos, a su sucesor).
        SetupGoal.entries.filterNot { it.isLegacyOnly }.forEach { goal ->
            val back = GoalProfileMapping.setupGoalOf(GoalProfileMapping.profileOfLegacy(goal))
            assertEquals("$goal", goal, back)
        }
        assertEquals(SetupGoal.COMPLETE_ATHLETE, GoalProfileMapping.setupGoalOf(GoalProfileMapping.profileOfLegacy(SetupGoal.MIXED)))
        assertEquals(SetupGoal.FUNCTIONAL, GoalProfileMapping.setupGoalOf(GoalProfileMapping.profileOfLegacy(SetupGoal.HEALTH)))
    }

    @Test
    fun everyGoalHasAPlanGoalProfileAndTheNewDisciplinesDoNotInventAPlanOfTheirOwn() {
        SetupGoal.entries.forEach { goal -> assertNotNull("$goal", planGoalProfileOf(goal)) }
        assertEquals(PlanGoalProfile.STRENGTH, planGoalProfileOf(SetupGoal.STRENGTH))
        assertEquals(PlanGoalProfile.MUSCLE, planGoalProfileOf(SetupGoal.MUSCLE))
        assertEquals(PlanGoalProfile.STRENGTH_MUSCLE, planGoalProfileOf(SetupGoal.STRENGTH_MUSCLE))
        assertEquals(PlanGoalProfile.COMPLETE_ATHLETE, planGoalProfileOf(SetupGoal.COMPLETE_ATHLETE))
        // Funcional se sirve, de momento, como Atleta completo; las cuatro disciplinas nuevas no tienen plan propio.
        assertEquals(PlanGoalProfile.COMPLETE_ATHLETE, planGoalProfileOf(SetupGoal.FUNCTIONAL))
        listOf(SetupGoal.CALISTHENICS, SetupGoal.WEIGHTLIFTING, SetupGoal.ARMWRESTLING, SetupGoal.STRONGMAN).forEach { goal ->
            assertEquals("$goal", PlanGoalProfile.LEGACY_HEALTH, planGoalProfileOf(goal))
        }
        assertEquals(PlanGoalProfile.LEGACY_HEALTH, planGoalProfileOf(null))
        assertNull("las reparaciones nunca ofrecen una disciplina nueva", PlanGoalProfile.LEGACY_HEALTH.toSetupGoal())
    }

    @Test
    fun theAthleteTypeIsPersistedOnlyWhenTheGoalStepWasAnsweredInThisSignUp() {
        val withProfile = SetupWizardDraft().withGoalProfile(TrainingGoalProfile.POWERLIFTING)
        // La sola elección del perfil (precargada o derivada) no basta: hace falta haber respondido el paso.
        assertNull(withProfile.athleteTypeToPersist())

        // Tocado por la persona.
        assertEquals(
            AthleteType.POWERLIFTER,
            withProfile.copy(declaredSteps = withProfile.declaredSteps + SetupStepId.GOAL).athleteTypeToPersist(),
        )
        // Confirmado (declarado) o aceptado como sugerido.
        listOf(SetupAnswerProvenance.USER_DECLARED, SetupAnswerProvenance.SUGGESTED).forEach { provenance ->
            val answered = withProfile.recordStepAnswer(SetupStepId.GOAL, provenance, SetupValueState.DECLARED)
            assertEquals("$provenance", AthleteType.POWERLIFTER, answered.athleteTypeToPersist())
        }
        // Un registro derivado o de un resultado de motor nunca cuenta como respuesta de la persona.
        listOf(SetupAnswerProvenance.DERIVED, SetupAnswerProvenance.ENGINE_RESULT).forEach { provenance ->
            val derived = withProfile.recordStepAnswer(SetupStepId.GOAL, provenance, SetupValueState.ESTIMATED)
            assertNull("$provenance", derived.athleteTypeToPersist())
        }
        // Sin perfil no hay nada que escribir, aunque el paso conste como respondido.
        val noProfile = SetupWizardDraft().recordStepAnswer(SetupStepId.GOAL, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)
        assertNull(noProfile.athleteTypeToPersist())
        // Cada perfil responde con su tipo de atleta.
        TrainingGoalProfile.entries.forEach { profile ->
            val answered = SetupWizardDraft().withGoalProfile(profile)
                .recordStepAnswer(SetupStepId.GOAL, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)
            assertEquals("$profile", GoalProfileMapping.athleteTypeOf(profile), answered.athleteTypeToPersist())
        }
    }
}
