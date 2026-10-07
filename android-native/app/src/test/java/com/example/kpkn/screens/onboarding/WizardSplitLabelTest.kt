package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.training.NativeProfileSplitWitness
import com.example.kpkn.domain.training.SplitApplicationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A.E2 (D6) · Un reparto de powerlifting solo se ofrece en Fuerza ([isSplitOfferedForGoal]): es la regla con la que el
 * reductor de GOAL retira un reparto que el objetivo nuevo ya no ofrece. JVM puro.
 *
 * Entreno v2: la lista de repartos del tablero de la semana y sus nombres en español viven en el dominio
 * (`SplitCatalogRules`, con sus propias pruebas); la lista y el nombre que tenía el paso SPLIT se retiraron con él.
 */
class WizardSplitLabelTest {

    private val visiblePowerlifting = SPLIT_TEMPLATES.filter { it.isVisibleForApplication && SplitTag.POWERLIFTING in it.tags }

    private fun trainingDaysOf(split: SplitTemplate): Int =
        SplitApplicationEngine.patternToTrainingDays(split.pattern, startDay = 1).size

    /** Repartos visibles con [days] días de entreno (todos si es null) que se ofrecen a [goal]. */
    private fun offered(days: Int?, goal: SetupGoal?): List<SplitTemplate> = SPLIT_TEMPLATES.filter { split ->
        split.isVisibleForApplication && isSplitOfferedForGoal(split, goal) && (days == null || trainingDaysOf(split) == days)
    }

    private fun offeredIds(days: Int?, goal: SetupGoal?): List<String> = offered(days, goal).map { it.id }

    private fun goalOf(profile: NativeProfileKind): SetupGoal = when (profile) {
        NativeProfileKind.STRENGTH -> SetupGoal.STRENGTH
        NativeProfileKind.MUSCLE -> SetupGoal.MUSCLE
        NativeProfileKind.POWERBUILDING -> SetupGoal.STRENGTH_MUSCLE
        NativeProfileKind.COMPLETE_ATHLETE -> SetupGoal.COMPLETE_ATHLETE
    }

    @Test
    fun powerliftingSplitsAreOfferedOnlyInStrength() {
        assertTrue("el catálogo publica repartos de powerlifting", visiblePowerlifting.size >= 2)
        (1..7).forEach { days ->
            SetupGoal.entries.forEach { goal ->
                val powerliftingOffered = offered(days, goal).filter { SplitTag.POWERLIFTING in it.tags }.map { it.id }
                if (goal == SetupGoal.STRENGTH) {
                    assertEquals(
                        "Fuerza con $days días los ofrece todos",
                        visiblePowerlifting.filter { trainingDaysOf(it) == days }.map { it.id },
                        powerliftingOffered,
                    )
                } else {
                    assertTrue("$goal con $days días ofrece $powerliftingOffered", powerliftingOffered.isEmpty())
                }
            }
        }
    }

    @Test
    fun theGoalOnlyTakesAwayPowerliftingSplitsAndNothingElse() {
        (listOf<Int?>(null) + (1..7)).forEach { days ->
            val unfiltered = offered(days, goal = null)
            SetupGoal.entries.forEach { goal ->
                val expected = if (goal == SetupGoal.STRENGTH) unfiltered else unfiltered.filter { SplitTag.POWERLIFTING !in it.tags }
                assertEquals("$goal con $days días", expected.map { it.id }, offeredIds(days, goal))
            }
        }
    }

    @Test
    fun aMuscleListWithThreeDaysKeepsTheGeneralSplitsAndDropsEveryPowerliftingOne() {
        val muscle = offeredIds(3, SetupGoal.MUSCLE)
        listOf("fullbody_x3", "heavy_light", "ppl_x3", "ul_fb_x3").forEach { assertTrue("Músculo ofrece $it", it in muscle) }
        listOf("pl_sbd_x3", "texas_method", "madcow_5x5", "sheiko_3day", "korte_3x3").forEach {
            assertFalse("Músculo no debe ofrecer $it", it in muscle)
        }
        val strength = offeredIds(3, SetupGoal.STRENGTH)
        listOf("fullbody_x3", "pl_sbd_x3", "texas_method", "madcow_5x5", "sheiko_3day", "korte_3x3").forEach {
            assertTrue("Fuerza ofrece $it", it in strength)
        }
    }

    @Test
    fun theGoalFilterNeverHidesTheSplitTheOwnPlanOfThatGoalAccepts() {
        NativeProfileSplitWitness.allWitnesses().forEach { witness ->
            val goal = goalOf(witness.profile)
            assertTrue(
                "$goal con ${witness.days} días debe ofrecer ${witness.splitId}",
                witness.splitId in offeredIds(witness.days, goal),
            )
        }
    }
}
