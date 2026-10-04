package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BenchVariant
import com.example.kpkn.data.models.DeadliftVariant
import com.example.kpkn.data.models.PowerliftingModality
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.SquatVariant
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R-04 y R-19: la fusión del perfil de cargas conserva lo que el asistente de TM no edita (variantes,
 * modalidad, estimados) y los TM ajustados de los levantamientos cuyo 1RM no cambió.
 */
class TrainingMaxMergeTest {

    private val pct = 0.90

    /** Lo que devuelve el asistente: solo 1RM, con el TM ya hidratado y las variantes por defecto. */
    private fun fromWizard(
        squat: Double? = null,
        bench: Double? = null,
        deadlift: Double? = null,
        overhead: Double? = null,
        percent: Double = pct,
    ): PowerliftingProfile = TrainingMaxResolver.hydrateProfile(
        PowerliftingProfile(squat1RM = squat, bench1RM = bench, deadlift1RM = deadlift, overhead1RM = overhead),
        percent,
    )

    // Perfil del programa: la banca trae un TM ajustado por una propuesta (110 en vez de 108).
    private val stored = PowerliftingProfile(
        squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0,
        squatTM = 180.0, benchTM = 110.0, deadliftTM = 198.0,
    )

    @Test
    fun an_unchanged_one_rm_keeps_the_adjusted_tm_and_a_new_one_recomputes_it() {
        val merged = TrainingMaxMerge.merge(stored, fromWizard(squat = 210.0, bench = 120.0, deadlift = 220.0), pct)

        // Banca y peso muerto no cambiaron: la banca conserva su TM ajustado (110, no 108).
        assertEquals(120.0, merged.bench1RM!!, 0.0)
        assertEquals(110.0, merged.benchTM!!, 1e-9)
        assertEquals(198.0, merged.deadliftTM!!, 1e-9)
        // Sentadilla cambió de 200 a 210: el TM pasa a 1RM × porcentaje.
        assertEquals(210.0, merged.squat1RM!!, 0.0)
        assertEquals(189.0, merged.squatTM!!, 1e-9)
    }

    @Test
    fun the_new_tm_uses_the_percentage_it_receives_not_the_one_the_wizard_hydrated_with() {
        val merged = TrainingMaxMerge.merge(stored, fromWizard(squat = 210.0, percent = 0.99), 0.87)

        assertEquals(210.0 * 0.87, merged.squatTM!!, 1e-9)
    }

    @Test
    fun without_a_stored_profile_the_merge_is_the_usual_hydration() {
        val wizard = fromWizard(squat = 200.0, bench = 120.0, deadlift = 220.0, percent = 0.87)

        val merged = TrainingMaxMerge.merge(null, wizard, 0.87)

        assertEquals(200.0 * 0.87, merged.squatTM!!, 1e-9)
        assertEquals(120.0 * 0.87, merged.benchTM!!, 1e-9)
        assertEquals(220.0 * 0.87, merged.deadliftTM!!, 1e-9)
        assertNull("sin 1RM de press militar no hay TM", merged.overheadTM)
        assertEquals(SquatVariant.LOW_BAR, merged.squatVariant)
        assertEquals(PowerliftingModality.RAW_CLASSIC, merged.modality)
    }

    @Test
    fun without_a_stored_profile_a_profile_with_only_one_rms_gets_its_tms_from_the_percentage() {
        val tested = PowerliftingProfile(squat1RM = 210.0, bench1RM = 125.0, deadlift1RM = 230.0)

        val merged = TrainingMaxMerge.merge(null, tested, 0.87)

        assertEquals(210.0 * 0.87, merged.squatTM!!, 1e-9)
        assertEquals(125.0 * 0.87, merged.benchTM!!, 1e-9)
        assertEquals(230.0 * 0.87, merged.deadliftTM!!, 1e-9)
    }

    @Test
    fun the_variants_modality_and_estimates_of_the_stored_profile_are_kept() {
        val withChoices = stored.copy(
            squatVariant = SquatVariant.FRONT_SQUAT,
            benchVariant = BenchVariant.DUMBBELL,
            deadliftVariant = DeadliftVariant.SUMO,
            modality = PowerliftingModality.EQUIPPED,
            squatE1RM = 205.0,
            benchE1RM = 118.0,
        )

        val merged = TrainingMaxMerge.merge(withChoices, fromWizard(squat = 215.0, bench = 125.0, deadlift = 220.0), pct)

        assertEquals(SquatVariant.FRONT_SQUAT, merged.squatVariant)
        assertEquals(BenchVariant.DUMBBELL, merged.benchVariant)
        assertEquals(DeadliftVariant.SUMO, merged.deadliftVariant)
        assertEquals(PowerliftingModality.EQUIPPED, merged.modality)
        assertEquals(205.0, merged.squatE1RM!!, 0.0)
        assertEquals(118.0, merged.benchE1RM!!, 0.0)
        assertEquals(215.0 * pct, merged.squatTM!!, 1e-9)
        assertEquals(125.0 * pct, merged.benchTM!!, 1e-9)
    }

    @Test
    fun a_lift_left_blank_keeps_everything_the_program_had() {
        val withOverhead = stored.copy(overhead1RM = 80.0, overheadTM = 71.0)

        // El asistente deja en blanco el press militar y la sentadilla: no se editaron.
        val merged = TrainingMaxMerge.merge(withOverhead, fromWizard(bench = 125.0, deadlift = 220.0), pct)

        assertEquals(80.0, merged.overhead1RM!!, 0.0)
        assertEquals(71.0, merged.overheadTM!!, 1e-9)
        assertEquals(200.0, merged.squat1RM!!, 0.0)
        assertEquals(180.0, merged.squatTM!!, 1e-9)
        assertEquals(125.0 * pct, merged.benchTM!!, 1e-9)
    }

    @Test
    fun a_lift_the_program_never_had_gets_its_tm_from_the_new_one_rm() {
        val merged = TrainingMaxMerge.merge(stored, fromWizard(overhead = 80.0), pct)

        assertEquals(80.0, merged.overhead1RM!!, 0.0)
        assertEquals(72.0, merged.overheadTM!!, 1e-9)
        assertEquals("el resto queda como estaba", stored.squatTM, merged.squatTM)
        assertEquals(stored.benchTM, merged.benchTM)
    }

    @Test
    fun an_unchanged_one_rm_without_a_stored_tm_derives_it_from_the_one_rm() {
        val onlyOneRm = PowerliftingProfile(squat1RM = 200.0)

        val merged = TrainingMaxMerge.merge(onlyOneRm, fromWizard(squat = 200.0), 0.87)

        assertEquals(174.0, merged.squatTM!!, 1e-9)
    }

    @Test
    fun an_estimate_counts_as_the_previous_one_rm_when_there_is_no_tested_one() {
        val estimatedOnly = PowerliftingProfile(squatE1RM = 190.0, squatTM = 170.0)

        val same = TrainingMaxMerge.merge(estimatedOnly, fromWizard(squat = 190.0), pct)
        val other = TrainingMaxMerge.merge(estimatedOnly, fromWizard(squat = 195.0), pct)

        assertEquals("mismo valor que el estimado: el TM ajustado se conserva", 170.0, same.squatTM!!, 1e-9)
        assertEquals(190.0, same.squat1RM!!, 0.0)
        assertEquals("otro valor: el TM se recalcula", 195.0 * pct, other.squatTM!!, 1e-9)
    }

    @Test
    fun one_rms_that_differ_by_a_rounding_error_are_the_same_one_rm() {
        val merged = TrainingMaxMerge.merge(stored, fromWizard(bench = 120.0 + 1e-9), pct)

        assertEquals(110.0, merged.benchTM!!, 1e-9)
    }

    @Test
    fun the_tm_of_the_new_profile_is_ignored_when_the_one_rm_did_not_change() {
        val newProfile = fromWizard(bench = 120.0).copy(benchTM = 999.0)

        val merged = TrainingMaxMerge.merge(stored, newProfile, pct)

        assertEquals(110.0, merged.benchTM!!, 1e-9)
    }

    @Test
    fun a_stored_tm_without_a_useful_value_is_derived_again_from_the_unchanged_one_rm() {
        val broken = stored.copy(benchTM = 0.0)

        val merged = TrainingMaxMerge.merge(broken, fromWizard(bench = 120.0), pct)

        assertEquals(120.0 * pct, merged.benchTM!!, 1e-9)
    }

    @Test
    fun the_training_max_percentage_of_a_program_is_its_recipes_or_ninety_percent() {
        val withoutRecipe = Program(id = "p", name = "P")
        val withRecipe = withoutRecipe.copy(
            sourceRecipe = TrainingPlanRecipe(id = "r", weeks = emptyList(), trainingMaxPercent = 0.87),
        )

        assertEquals(0.90, TrainingMaxMerge.trainingMaxPercentOf(withoutRecipe), 0.0)
        assertEquals(0.87, TrainingMaxMerge.trainingMaxPercentOf(withRecipe), 0.0)
    }
}
