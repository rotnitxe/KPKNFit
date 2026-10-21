package com.example.kpkn.domain.training

import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.domain.training.Cal5TestSupport.recipeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * CAL5 · Juggernaut 2.0 (RES1 §5): TM nuevo = TM + (repeticiones del AMRAP de realización − estándar) × 2,5 kg (tren inferior) o
 * 1,25 kg (tren superior), con un tope de 10 repeticiones extra, al entrar en la ola siguiente. Sin superar el estándar el TM
 * queda igual y se avisa.
 */
class Cal5JuggernautTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val rule = ProgressionRule.RepsOverStandardTm()
    private val recipe = recipeOf("juggernaut-2")
    private val profile = PowerliftingProfile(
        squat1RM = 167.0, squatTM = 150.0,
        bench1RM = 111.0, benchTM = 100.0,
        deadlift1RM = 200.0, deadliftTM = 180.0,
        overhead1RM = 67.0, overheadTM = 60.0,
    )

    private fun hit(lift: LiftSlot, standard: Int, reps: Int, percent: Double = 85.0, weightRatio: Double? = null) =
        AmrapHit(lift, percent, prescribedReps = standard, actualReps = reps, weightRatio = weightRatio)

    private fun changeOf(lift: LiftSlot, hit: AmrapHit): AuthoredProgressionEngine.TmChange? = AuthoredProgressionEngine.tmChanges(
        profile, recipe, evidence = AuthoredProgressionEngine.CloseEvidence(amrapHits = listOf(hit)),
    ).firstOrNull { it.liftSlot == lift }

    @Test
    fun the_recipe_declares_the_formula_and_not_a_fixed_increment_per_wave() {
        assertEquals(ProgressionRule.RepsOverStandardTm(lowerKgPerRep = 2.5, upperKgPerRep = 1.25, maxExtraReps = 10), recipe.progression)
        assertFalse(ProgramAutoregulationEngine.amrapMovesTm(recipe))
        assertTrue(AuthoredProgressionEngine.appliesAtBlockClose(recipe.progression))
        assertFalse(AuthoredProgressionEngine.appliesAtCycleClose(recipe.progression))
    }

    @Test
    fun the_lower_body_gains_2_5_kg_per_rep_over_the_standard() {
        // RES1 §5.2: sentadilla, ola de 5s (estándar 5), 13 repeticiones al 85 % → (13 − 5) × 2,5 = +20 kg (el ejemplo de la fuente, en kg).
        val change = changeOf(LiftSlot.SQUAT, hit(LiftSlot.SQUAT, standard = 5, reps = 13))!!
        assertEquals(150.0, change.beforeKg, 1e-9)
        assertEquals(170.0, change.afterKg, 1e-9)
        assertTrue(change.note!!.contains("TM +20 kg"))
        // Peso muerto, ola de 3s (estándar 3): 8 repeticiones → +12,5 kg.
        assertEquals(192.5, changeOf(LiftSlot.DEADLIFT, hit(LiftSlot.DEADLIFT, 3, 8, percent = 90.0))!!.afterKg, 1e-9)
    }

    @Test
    fun the_upper_body_gains_1_25_kg_per_rep_over_the_standard() {
        // Banca, ola de 8s (estándar 8), 12 repeticiones → 4 × 1,25 = +5 kg; press militar, ola de 10s, 14 → +5 kg.
        assertEquals(105.0, changeOf(LiftSlot.BENCH, hit(LiftSlot.BENCH, 8, 12, percent = 80.0))!!.afterKg, 1e-9)
        assertEquals(65.0, changeOf(LiftSlot.OVERHEAD, hit(LiftSlot.OVERHEAD, 10, 14, percent = 75.0))!!.afterKg, 1e-9)
    }

    @Test
    fun at_most_ten_extra_reps_count() {
        // Ola de 10s: 25 repeticiones son 15 extra, pero cuentan 10: sentadilla +25 kg y banca +12,5 kg.
        assertEquals(175.0, changeOf(LiftSlot.SQUAT, hit(LiftSlot.SQUAT, 10, 25, percent = 75.0))!!.afterKg, 1e-9)
        assertEquals(112.5, changeOf(LiftSlot.BENCH, hit(LiftSlot.BENCH, 10, 25, percent = 75.0))!!.afterKg, 1e-9)
        val result = JuggernautProgression.evaluate(rule, hit(LiftSlot.SQUAT, 10, 25, percent = 75.0))!!
        assertEquals(10, result.countedExtraReps)
        assertEquals(25.0, result.deltaKg, 1e-9)
    }

    @Test
    fun without_beating_the_standard_the_tm_stays_and_the_athlete_is_told() {
        val atStandard = hit(LiftSlot.SQUAT, 5, 5)
        assertNull(changeOf(LiftSlot.SQUAT, atStandard))
        val below = hit(LiftSlot.SQUAT, 5, 3)
        assertNull(changeOf(LiftSlot.SQUAT, below))
        val holds = AuthoredProgressionEngine.tmHolds(
            profile, recipe, evidence = AuthoredProgressionEngine.CloseEvidence(amrapHits = listOf(below)),
        )
        val hold = holds.single { it.liftSlot == LiftSlot.SQUAT }
        assertEquals(150.0, hold.tmKg, 1e-9)
        assertTrue(hold.note!!, hold.note!!.contains("quizá esté alto"))
        assertEquals(JuggernautProgression.Outcome.AT_STANDARD, JuggernautProgression.evaluate(rule, atStandard)!!.outcome)
        assertEquals(JuggernautProgression.Outcome.BELOW_STANDARD, JuggernautProgression.evaluate(rule, below)!!.outcome)
        assertEquals(0.0, JuggernautProgression.evaluate(rule, below)!!.deltaKg, 0.0)
    }

    @Test
    fun a_lighter_load_than_prescribed_does_not_measure_the_tm() {
        val lighter = hit(LiftSlot.SQUAT, 5, 13, weightRatio = 0.9)
        assertEquals(JuggernautProgression.Outcome.LIGHTER_LOAD, JuggernautProgression.evaluate(rule, lighter)!!.outcome)
        assertNull(changeOf(LiftSlot.SQUAT, lighter))
    }

    @Test
    fun a_hit_without_a_standard_or_a_lift_is_ignored() {
        assertNull(JuggernautProgression.evaluate(rule, AmrapHit(null, 85.0, 5, 9)))
        assertNull(JuggernautProgression.evaluate(rule, AmrapHit(LiftSlot.SQUAT, 85.0, 0, 9)))
        assertTrue(AuthoredProgressionEngine.tmChanges(profile, recipe).isEmpty())
    }

    @Test
    fun the_summary_reads_in_plain_spanish() {
        assertEquals(
            "13 reps con estándar de 5: TM +20 kg",
            JuggernautProgression.summary(JuggernautProgression.evaluate(rule, hit(LiftSlot.SQUAT, 5, 13))!!),
        )
        assertEquals(
            "3 reps, por debajo del estándar de 5: el TM no cambia y quizá esté alto",
            JuggernautProgression.summary(JuggernautProgression.evaluate(rule, hit(LiftSlot.SQUAT, 5, 3))!!),
        )
    }

    @Test
    fun the_published_description_says_what_the_program_does() {
        val description = PROTOCOL_LIBRARY.first { it.id == "juggernaut-2" }.description
        assertTrue(description, description.contains("2,5 kg"))
        assertTrue(description, description.contains("1,25 kg"))
        assertTrue(description, description.contains("estándar"))
        assertFalse("ya no promete un ajuste genérico", description.contains("que ajusta TM"))
    }
}
