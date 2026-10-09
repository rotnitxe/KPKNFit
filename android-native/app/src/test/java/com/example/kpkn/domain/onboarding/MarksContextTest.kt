package com.example.kpkn.domain.onboarding

import com.example.kpkn.domain.training.generator.RoutineLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarksContextTest {

    private val big3 = listOf(LiftMark.SQUAT, LiftMark.BENCH, LiftMark.DEADLIFT)

    private fun lifts(
        profile: TrainingGoalProfile?,
        novice: Boolean = false,
        hasBarbell: Boolean = true,
        olympic: Boolean = false,
        returning: Boolean = false,
    ) = MarksContext.liftsFor(profile, novice, hasBarbell, olympic, returning)

    @Test
    fun aNoviceIsNeverAskedForMarks() {
        for (profile in TrainingGoalProfile.entries) {
            assertTrue("$profile", lifts(profile, novice = true).isEmpty())
        }
    }

    @Test
    fun withoutAGoalNothingIsAsked() {
        assertTrue(lifts(null).isEmpty())
    }

    @Test
    fun strengthAndMuscleAsksTheBigThreeAndThePressOnlyWithABarbell() {
        assertEquals(big3 + LiftMark.OVERHEAD_PRESS, lifts(TrainingGoalProfile.STRENGTH_MUSCLE))
        assertTrue(lifts(TrainingGoalProfile.STRENGTH_MUSCLE, hasBarbell = false).isEmpty())
    }

    @Test
    fun powerliftingAndPowerbuildingAskTheBigThreeAndPowerbuildingAlsoThePress() {
        assertEquals(big3, lifts(TrainingGoalProfile.POWERLIFTING))
        assertEquals(big3 + LiftMark.OVERHEAD_PRESS, lifts(TrainingGoalProfile.POWERBUILDING))
        // Estas dos disciplinas piden el trío siempre: la marca describe lo que ya levantaba.
        assertEquals(big3 + LiftMark.OVERHEAD_PRESS, lifts(TrainingGoalProfile.POWERBUILDING, hasBarbell = false))
    }

    @Test
    fun returningLikeTheNoviceIsNeverAskedForMarks() {
        // Quien vuelve de una pausa: su fuerza ya no es la de antes, así que ninguna marca vale como punto de partida.
        for (profile in TrainingGoalProfile.entries) {
            assertTrue("$profile", lifts(profile, novice = false, returning = true).isEmpty())
        }
        // Sin la pausa, el mismo perfil sí las pide.
        assertTrue(lifts(TrainingGoalProfile.POWERLIFTING, novice = false, returning = false).isNotEmpty())
    }

    @Test
    fun theOverheadPressIsAskedWithTheBarbellProfilesAndStrongman() {
        assertTrue(LiftMark.OVERHEAD_PRESS in lifts(TrainingGoalProfile.STRENGTH_MUSCLE))
        assertTrue(LiftMark.OVERHEAD_PRESS in lifts(TrainingGoalProfile.POWERBUILDING))
        assertTrue(LiftMark.OVERHEAD_PRESS in lifts(TrainingGoalProfile.WEIGHTLIFTING))
        assertTrue(LiftMark.OVERHEAD_PRESS in lifts(TrainingGoalProfile.STRONGMAN))
        assertFalse(LiftMark.OVERHEAD_PRESS in lifts(TrainingGoalProfile.POWERLIFTING))
        assertFalse(LiftMark.OVERHEAD_PRESS in lifts(TrainingGoalProfile.WEIGHTLIFTING, hasBarbell = false))
    }

    @Test
    fun strongmanAsksDeadliftSquatAndOverheadPress() {
        assertEquals(
            listOf(LiftMark.DEADLIFT, LiftMark.SQUAT, LiftMark.OVERHEAD_PRESS),
            lifts(TrainingGoalProfile.STRONGMAN),
        )
    }

    @Test
    fun weightliftingAsksSquatAndThePressPlusTheOlympicLiftsOnlyWhenTheProgramReadsThem() {
        assertEquals(listOf(LiftMark.SQUAT, LiftMark.OVERHEAD_PRESS), lifts(TrainingGoalProfile.WEIGHTLIFTING))
        assertEquals(
            listOf(LiftMark.SQUAT, LiftMark.SNATCH, LiftMark.CLEAN_AND_JERK, LiftMark.OVERHEAD_PRESS),
            lifts(TrainingGoalProfile.WEIGHTLIFTING, olympic = true),
        )
        // Las reservas de halterofilia de D1b leen el arranque y los dos tiempos (`OlympicMarksTest` lo mide con el generador):
        // el valor por defecto las pregunta.
        assertEquals(true, MarksContext.CATALOG_HAS_OLYMPIC_LIFTS)
        assertEquals(
            listOf(LiftMark.SQUAT, LiftMark.SNATCH, LiftMark.CLEAN_AND_JERK, LiftMark.OVERHEAD_PRESS),
            MarksContext.liftsFor(TrainingGoalProfile.WEIGHTLIFTING, novice = false),
        )
    }

    @Test
    fun theOlympicMarksAreReadOnlyFromTheIntermediateLevelOnWhichTheGeneratorProgramsThoseLifts() {
        assertEquals(RoutineLevel.INTERMEDIATE, MarksContext.OLYMPIC_MIN_LEVEL)
        assertEquals(false, MarksContext.readsOlympicMarks(RoutineLevel.NOVICE))
        assertEquals(false, MarksContext.readsOlympicMarks(RoutineLevel.RETURNING))
        assertEquals(true, MarksContext.readsOlympicMarks(RoutineLevel.INTERMEDIATE))
        assertEquals(true, MarksContext.readsOlympicMarks(RoutineLevel.ADVANCED))
    }

    @Test
    fun theRestOfTheProfilesAskNothing() {
        for (profile in listOf(
            TrainingGoalProfile.STRENGTH_CARDIO, TrainingGoalProfile.FUNCTIONAL_HEALTH, TrainingGoalProfile.BODYBUILDING,
            TrainingGoalProfile.CALISTHENICS, TrainingGoalProfile.ARMWRESTLING,
        )) {
            assertTrue("$profile", lifts(profile).isEmpty())
        }
    }

    @Test
    fun theBigThreeAreTheOnlyMarksTheEngineReadsFromThePowerliftingProfile() {
        assertEquals(setOf(LiftMark.SQUAT, LiftMark.BENCH, LiftMark.DEADLIFT), MarksContext.BIG_THREE)
    }
}
