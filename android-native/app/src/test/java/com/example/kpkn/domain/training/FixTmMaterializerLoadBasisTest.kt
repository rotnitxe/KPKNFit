package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test

/**
 * FIX-TM: `PlanMaterializer` elige la base de CADA serie por su `loadBasis` y no por la de la primera serie del slot.
 * - `PERCENT_1RM` lee el 1RM; `PERCENT_TM` lee el TM.
 * - Sin TM declarado pero con un 1RM capturado, el TM es 1RM × la fracción de TM de la receta (0,90).
 * - Jamás se deduce un 1RM desde un TM.
 * El perfil usado tiene 1RM de banca 120 (TM = 108 con la receta al 90 %).
 */
class FixTmMaterializerLoadBasisTest {
    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private fun recipe(vararg sets: SetRecipe): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "fix-tm-recipe",
        weeks = listOf(
            weekRecipe(
                1,
                0,
                "Bloque",
                BlockGoal.ACCUMULATION,
                listOf(
                    DayRecipe(
                        label = "Banca",
                        slots = listOf(
                            slot(
                                "s_bench",
                                SlotRole.T1_MAIN,
                                CatalogIds.BP,
                                sets.toList(),
                                150,
                                LiftSlot.BENCH,
                                isCompetitionLift = true,
                            ),
                        ),
                        weekday = 1,
                        id = "day_fix_tm",
                    ),
                ),
            ),
        ),
        liftSlots = mapOf(LiftSlot.BENCH to CatalogIds.BP),
        claimedDaysPerWeek = 1,
    )

    private fun materialize(recipe: TrainingPlanRecipe, profile: PowerliftingProfile?): Exercise =
        PlanMaterializer.materialize(
            Program(id = "p", name = "T"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = profile,
            strict = false,
        ).macrocycles.first().blocks.first().mesocycles.first().weeks.first()
            .sessions.first().allExercises().first()

    private val profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0)

    private fun tm(percent: Double = 80.0, reference: PlanLoadReference? = null) =
        SetRecipe(reps = 5, percent = percent, loadBasis = LoadBasis.PERCENT_TM, reference = reference)

    private fun oneRm(percent: Double = 80.0, reference: PlanLoadReference? = null) =
        SetRecipe(reps = 5, percent = percent, loadBasis = LoadBasis.PERCENT_1RM, reference = reference)

    private fun captured(kind: PlanLoadReferenceKind, kg: Double) = PlanLoadReference(
        kind = kind,
        configurationId = CatalogIds.BP,
        state = PlanLoadReferenceState.CAPTURED,
        capturedLoadKg = kg,
    )

    @Test
    fun profile_percent_1rm_set_reads_the_one_rm_and_percent_tm_set_reads_the_training_max() {
        val onOneRm = materialize(recipe(oneRm()), profile)
        assertEquals(96.0, onOneRm.sets.single().weight ?: -1.0, 0.001)
        assertEquals(LoadBasis.PERCENT_1RM, onOneRm.sets.single().loadBasis)
        assertEquals(120.0, onOneRm.reference1RM ?: -1.0, 0.001)

        val onTm = materialize(recipe(tm()), profile)
        assertEquals(86.4, onTm.sets.single().weight ?: -1.0, 0.001)
        assertEquals(120.0, onTm.reference1RM ?: -1.0, 0.001)
        assertEquals(108.0, onTm.referenceTrainingMax ?: -1.0, 0.001)
    }

    @Test
    fun a_slot_mixing_bases_resolves_every_set_on_its_own_base_whatever_the_order() {
        // Antes la base de TODAS las series salía de la primera: la serie de la otra base leía la referencia equivocada.
        val tmFirst = materialize(recipe(tm(), oneRm()), profile)
        assertEquals(86.4, tmFirst.sets[0].weight ?: -1.0, 0.001)
        assertEquals(96.0, tmFirst.sets[1].weight ?: -1.0, 0.001)

        val oneRmFirst = materialize(recipe(oneRm(), tm()), profile)
        assertEquals(96.0, oneRmFirst.sets[0].weight ?: -1.0, 0.001)
        assertEquals(86.4, oneRmFirst.sets[1].weight ?: -1.0, 0.001)

        // Y el ejercicio materializado resuelve lo mismo que sus kg publicados.
        listOf(tmFirst, oneRmFirst).forEach { exercise ->
            exercise.sets.forEach { set ->
                assertEquals(set.weight ?: -1.0, calculateSuggestedLoad(exercise, set) ?: -2.0, 0.26)
            }
        }
    }

    @Test
    fun captured_one_rm_without_training_max_derives_the_tm_from_the_recipe_percentage() {
        val reference = captured(PlanLoadReferenceKind.EXERCISE_1RM, 100.0)

        val onTm = materialize(recipe(tm(reference = reference)), profile = null)
        assertEquals(100.0, onTm.reference1RM ?: -1.0, 0.001)
        assertEquals("TM = 1RM × 0,90", 90.0, onTm.referenceTrainingMax ?: -1.0, 0.001)
        assertEquals(72.0, onTm.sets.single().weight ?: -1.0, 0.001)
        assertEquals(72.0, calculateSuggestedLoad(onTm, onTm.sets.single()) ?: -1.0, 0.001)

        val onOneRm = materialize(recipe(oneRm(reference = reference)), profile = null)
        assertEquals(100.0, onOneRm.reference1RM ?: -1.0, 0.001)
        assertNull("Sin series %TM no se inventa un TM", onOneRm.referenceTrainingMax)
        assertEquals(80.0, onOneRm.sets.single().weight ?: -1.0, 0.001)

        val mixed = materialize(recipe(oneRm(reference = reference), tm(reference = reference)), profile = null)
        assertEquals(80.0, mixed.sets[0].weight ?: -1.0, 0.001)
        assertEquals(72.0, mixed.sets[1].weight ?: -1.0, 0.001)
    }

    @Test
    fun captured_training_max_feeds_percent_tm_and_never_becomes_a_one_rm() {
        val reference = captured(PlanLoadReferenceKind.EXERCISE_TM, 90.0)

        val onTm = materialize(recipe(tm(reference = reference)), profile = null)
        assertNull("Un TM capturado no es un 1RM", onTm.reference1RM)
        assertEquals(90.0, onTm.referenceTrainingMax ?: -1.0, 0.001)
        assertEquals(72.0, onTm.sets.single().weight ?: -1.0, 0.001)

        // Una serie %1RM con solo un TM declarado queda pendiente: no se deduce un 1RM desde el TM.
        val onOneRm = materialize(recipe(oneRm(reference = reference)), profile = null)
        assertNull(onOneRm.reference1RM)
        assertNull("Sin 1RM la serie %1RM queda pendiente, nunca 0 kg ni el TM", onOneRm.sets.single().weight)
    }

    @Test
    fun without_profile_or_reference_every_percent_set_stays_pending() {
        val exercise = materialize(recipe(tm(), oneRm()), profile = null)
        assertNull(exercise.reference1RM)
        assertNull(exercise.referenceTrainingMax)
        exercise.sets.forEach { assertNull(it.weight) }
    }
}
