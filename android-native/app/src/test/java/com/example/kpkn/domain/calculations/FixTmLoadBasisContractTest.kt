package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX-TM: cada serie con porcentaje lee la referencia de SU base.
 * - `PERCENT_1RM` (y `PERCENT_DESIRED_MAX`): `reference1RM`, que siempre significa 1RM.
 * - `PERCENT_TM`: `referenceTrainingMax`; sin TM la carga queda pendiente (no se usa el 1RM como TM).
 * Todos los consumidores de %RM (sugerencia de carga, energía, ayudas del editor) comparten esa regla.
 */
class FixTmLoadBasisContractTest {
    private fun set(basis: LoadBasis?, percent: Double = 80.0) =
        ExerciseSet(id = "s-$basis", targetReps = 5, targetPercentageRM = percent, loadBasis = basis)

    private fun exercise(oneRm: Double?, tm: Double?, vararg sets: ExerciseSet) = Exercise(
        id = "e",
        name = "Press",
        trainingMode = TrainingMode.RM,
        reference1RM = oneRm,
        referenceTrainingMax = tm,
        sets = sets.toList(),
    )

    @Test
    fun percent_1rm_reads_the_one_rm_even_when_a_training_max_exists() {
        val s = set(LoadBasis.PERCENT_1RM)
        val e = exercise(oneRm = 200.0, tm = 180.0, s)
        assertEquals(160.0, calculateSuggestedLoad(e, s)!!, 0.0)
        assertEquals(160.0, calculateSuggestedLoad(e, s, emptyList())!!, 0.0)
        assertEquals(160.0, percentLoadKg(e, s)!!, 1e-9)
        assertEquals(200.0, percentLoadReferenceKg(e, s)!!, 0.0)
        assertEquals(80.0, percentOfOneRm(e, s)!!, 1e-9)
    }

    @Test
    fun percent_1rm_without_one_rm_stays_pending_and_never_borrows_the_training_max() {
        val s = set(LoadBasis.PERCENT_1RM)
        val e = exercise(oneRm = null, tm = 180.0, s)
        assertNull(calculateSuggestedLoad(e, s))
        assertNull(calculateSuggestedLoad(e, s, emptyList()))
        assertNull(percentLoadKg(e, s))
        assertNull(percentLoadReferenceKg(e, s))
    }

    @Test
    fun percent_tm_reads_the_training_max_not_the_one_rm() {
        val s = set(LoadBasis.PERCENT_TM)
        val e = exercise(oneRm = 200.0, tm = 180.0, s)
        assertEquals(144.0, calculateSuggestedLoad(e, s)!!, 0.0)
        assertEquals(144.0, calculateSuggestedLoad(e, s, emptyList())!!, 0.0)
        assertEquals(144.0, percentLoadKg(e, s)!!, 1e-9)
        assertEquals(180.0, percentLoadReferenceKg(e, s)!!, 0.0)
        // 80 % del TM sobre un TM de 180 y un 1RM de 200 son 72 % del 1RM.
        assertEquals(72.0, percentOfOneRm(e, s)!!, 1e-9)
    }

    @Test
    fun percent_tm_without_training_max_stays_pending_even_with_a_one_rm() {
        val s = set(LoadBasis.PERCENT_TM)
        val e = exercise(oneRm = 200.0, tm = null, s)
        assertNull(calculateSuggestedLoad(e, s))
        assertNull(calculateSuggestedLoad(e, s, emptyList()))
        assertNull(percentLoadKg(e, s))
        assertNull(percentLoadReferenceKg(e, s))
        assertNull("sin TM no se puede expresar sobre el 1RM", percentOfOneRm(e, s))
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid ->
            assertNull(percentLoadKg(e.copy(referenceTrainingMax = invalid), s))
        }
    }

    @Test
    fun a_mixed_exercise_resolves_each_set_on_its_own_base() {
        val onTm = set(LoadBasis.PERCENT_TM)
        val onOneRm = set(LoadBasis.PERCENT_1RM)
        val e = exercise(oneRm = 200.0, tm = 180.0, onTm, onOneRm)
        assertEquals(144.0, calculateSuggestedLoad(e, onTm)!!, 0.0)
        assertEquals(160.0, calculateSuggestedLoad(e, onOneRm)!!, 0.0)
    }

    @Test
    fun missing_references_are_reported_per_base() {
        val onTm = set(LoadBasis.PERCENT_TM)
        val onOneRm = set(LoadBasis.PERCENT_1RM)

        val noTm = missingPercentReferences(exercise(oneRm = 200.0, tm = null, onTm), resolved1RM = 200.0)
        assertTrue(noTm.trainingMax)
        assertFalse(noTm.oneRm)

        val noOneRm = missingPercentReferences(exercise(oneRm = null, tm = 180.0, onOneRm), resolved1RM = null)
        assertTrue(noOneRm.oneRm)
        assertFalse(noOneRm.trainingMax)

        // Un TM no cubre la falta de 1RM ni al revés, aunque el ejercicio mezcle las dos bases.
        val mixedMissingBoth = missingPercentReferences(exercise(oneRm = null, tm = null, onTm, onOneRm), resolved1RM = null)
        assertTrue(mixedMissingBoth.oneRm && mixedMissingBoth.trainingMax)

        val complete = missingPercentReferences(exercise(oneRm = 200.0, tm = 180.0, onTm, onOneRm), resolved1RM = 200.0)
        assertFalse(complete.any)
    }

    @Test
    fun the_energy_engine_resolves_planned_load_on_the_base_of_each_set() {
        val onTm = set(LoadBasis.PERCENT_TM)
        val onOneRm = set(LoadBasis.PERCENT_1RM)
        val e = exercise(oneRm = 200.0, tm = 180.0, onTm, onOneRm)
        val tmLoad = TrainingEnergyEngine.resolvePlannedExternalLoadKg(e, onTm, WeightUnit.KG)
        val oneRmLoad = TrainingEnergyEngine.resolvePlannedExternalLoadKg(e, onOneRm, WeightUnit.KG)
        assertEquals(144.0, tmLoad.externalLoadKg!!, 1e-9)
        assertEquals(160.0, oneRmLoad.externalLoadKg!!, 1e-9)

        // Con %TM y sin TM, jamás 1RM × % (160 kg): el 80 % del TM no es el 80 % del 1RM.
        val withoutTm = TrainingEnergyEngine.resolvePlannedExternalLoadKg(e.copy(referenceTrainingMax = null), onTm, WeightUnit.KG)
        assertFalse(withoutTm.externalLoadKg == 160.0)
    }
}
