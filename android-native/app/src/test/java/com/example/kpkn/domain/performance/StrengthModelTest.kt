package com.example.kpkn.domain.performance

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RecordedSetPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthModelTest {
    @Test fun amrapPurposeUsesItsMinimumWithoutAddingPlannedReserve() {
        val set = com.example.kpkn.data.models.ExerciseSet("amrap", targetReps = 12,
            purpose = com.example.kpkn.data.models.SetPurpose.AMRAP, amrapMinimumReps = 5, targetRIR = 4)
        assertEquals(106.7, StrengthModel.weightFor(120.0, set)!!, 0.0)
        assertEquals(106.7, com.example.kpkn.domain.calculations.calculateWeightFrom1RMAndIntensity(120.0, set)!!, 0.0)
    }

    @Test
    fun `version y regiones mantienen la misma curva V1`() {
        assertEquals("strength-v1", StrengthModel.VERSION)
        StrengthRegion.entries.forEach { region ->
            assertEquals(120.0, StrengthModel.estimate1RM(100.0, 5, rir = 2, region = region), 0.0)
            assertEquals(100.0, StrengthModel.weightFor(120.0, 5, rir = 2, region = region), 0.0)
            assertEquals(7.0, StrengthModel.repsToFailureAt(100.0, 120.0, region), 1e-9)
        }
    }

    @Test
    fun `cien por cinco con dos en reserva prescribe cien`() {
        val estimated = StrengthModel.estimate1RM(100.0, 5, rir = 2)
        assertEquals(120.0, estimated, 0.0)
        assertEquals(100.0, StrengthModel.weightFor(estimated, 5, rir = 2), 0.0)
    }

    @Test
    fun `ida y vuelta en toda la malla incluido el tope`() {
        listOf(0.5, 20.0, 62.5, 100.0, 142.25, 240.0).forEach { weight ->
            (1..50).forEach { reps ->
                (0..4).forEach { rir ->
                    val estimated = StrengthModel.estimate1RM(weight, reps, rir)
                    assertEquals("$weight × $reps RIR $rir", weight, StrengthModel.weightFor(estimated, reps, rir), 0.100001)
                }
            }
        }
    }

    @Test
    fun `sin RIR equivale a cero y RPE ocho equivale a RIR dos`() {
        assertEquals(112.5, StrengthModel.estimate1RM(100.0, 5), 0.0)
        assertEquals(StrengthModel.estimate1RM(100.0, 5, rir = 0), StrengthModel.estimate1RM(100.0, 5), 0.0)
        assertEquals(StrengthModel.estimate1RM(100.0, 5, rir = 2), StrengthModel.estimate1RM(100.0, 5, rpe = 8.0), 0.0)
        assertEquals(6, StrengthModel.effectiveReps(5, rpe = 8.5))
        assertEquals(5, StrengthModel.effectiveReps(5, rpe = 12.0))
        assertEquals(6, StrengthModel.effectiveReps(5, rir = 1, rpe = 8.0))
    }

    @Test
    fun `fallo fuerza reserva cero pero AMRAP conserva el autoinforme`() {
        val expected = StrengthModel.estimate1RM(100.0, 5)
        assertEquals(expected, StrengthModel.estimate1RM(100.0, 5, rir = 4, rpe = 6.0, toFailure = true), 0.0)
        assertEquals(StrengthModel.estimate1RM(100.0, 5, rir = 4), StrengthModel.estimate1RM(100.0, 5, rir = 4, isAmrap = true), 0.0)
        val set = CompletedSet("serie", weight = 100.0, reps = 5, rir = 4)
        assertEquals(expected, StrengthModel.estimate1RM(set.copy(isFailure = true)), 0.0)
        assertEquals(expected, StrengthModel.estimate1RM(set.copy(isFailedSet = true)), 0.0)
        assertEquals(StrengthModel.estimate1RM(set), StrengthModel.estimate1RM(set.copy(amrapPerformed = true)), 0.0)
        assertEquals(expected, StrengthModel.estimate1RM(set.copy(actualIntensityMode = IntensityMode.SOLO_RM)), 0.0)
    }

    @Test
    fun `series saltadas e invalidas no estiman`() {
        val set = CompletedSet("serie", weight = 100.0, reps = 5, rir = 2)
        assertEquals(0.0, StrengthModel.estimate1RM(set.copy(skipped = true)), 0.0)
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { weight ->
            assertEquals(0.0, StrengthModel.estimate1RM(weight, 5), 0.0)
            assertEquals(0.0, StrengthModel.weightFor(weight, 5), 0.0)
            assertEquals(0.0, StrengthModel.repsToFailureAt(weight, 100.0), 0.0)
            assertEquals(0.0, StrengthModel.repsToFailureAt(100.0, weight), 0.0)
        }
        listOf(0, -1, Int.MIN_VALUE).forEach { reps ->
            assertEquals(0, StrengthModel.effectiveReps(reps, rir = 4))
            assertEquals(0.0, StrengthModel.estimate1RM(100.0, reps, rir = 4), 0.0)
            assertEquals(0.0, StrengthModel.weightFor(100.0, reps, rir = 4), 0.0)
        }
    }

    @Test
    fun `el esfuerzo del payload se respeta sin cambiar el registro`() {
        val payload = RecordedSetPayload(exerciseId = "press", actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 8.0)
        val set = CompletedSet("serie", weight = 100.0, reps = 5, recordedPayloadV3 = payload)
        assertEquals(120.0, StrengthModel.estimate1RM(set), 0.0)
        assertEquals(0.0, StrengthModel.estimate1RM(set.copy(recordedPayloadV3 = payload.copy(skipped = true))), 0.0)
        assertEquals(112.5, StrengthModel.estimate1RM(set.copy(recordedPayloadV3 = payload.copy(reachedFailure = true))), 0.0)
        assertEquals(8.0, payload.actualIntensityValue!!, 0.0)
        assertNull(set.rir)
    }

    @Test
    fun `la prescripcion respeta el rango y la politica de intensidad`() {
        val set = ExerciseSet("plan", targetReps = 5, targetRIR = 2)
        assertEquals(100.0, StrengthModel.weightFor(120.0, set)!!, 0.0)
        assertEquals(100.0, StrengthModel.weightFor(120.0, set.copy(targetRIR = null, targetRPE = 8.0))!!, 0.0)
        assertEquals(100.0, StrengthModel.weightFor(120.0, set.copy(targetRIR = null))!!, 0.0)
        listOf(IntensityMode.FAILURE, IntensityMode.AMRAP, IntensityMode.SOLO_RM).forEach { mode ->
            assertEquals(100.0, StrengthModel.weightFor(112.5, set.copy(intensityMode = mode))!!, 0.0)
        }
        assertEquals(100.0, StrengthModel.weightFor(120.0, set.copy(targetReps = null,
            targetRepsRange = com.example.kpkn.data.models.RepRange(3, 5)))!!, 0.0)
        assertNull(StrengthModel.weightFor(120.0, set.copy(targetReps = null)))
    }

    @Test
    fun `reserva negativa no resta reps y el tope no desborda enteros`() {
        assertEquals(5, StrengthModel.effectiveReps(5, rir = -2))
        assertEquals(50, StrengthModel.effectiveReps(48, rir = 4))
        assertEquals(50, StrengthModel.effectiveReps(Int.MAX_VALUE, rir = Int.MAX_VALUE))
        assertEquals(StrengthModel.estimate1RM(100.0, 50), StrengthModel.estimate1RM(100.0, 48, rir = 4), 0.0)
        assertEquals(StrengthModel.estimate1RM(100.0, 5), StrengthModel.estimate1RM(100.0, 5, rpe = Double.NaN), 0.0)
    }

    @Test
    fun `mas reserva y mas carga elevan la estimacion`() {
        (1..45).forEach { reps ->
            (0..3).forEach { rir ->
                assertTrue(StrengthModel.estimate1RM(100.0, reps, rir + 1) > StrengthModel.estimate1RM(100.0, reps, rir))
                assertTrue(StrengthModel.estimate1RM(105.0, reps, rir) > StrengthModel.estimate1RM(100.0, reps, rir))
            }
        }
    }

    @Test
    fun `la inversa continua recorre los tres tramos sin redondear reps`() {
        // Capacidad grande para aislar el redondeo de kilogramos de la inversión de la curva.
        (1..50).forEach { reps ->
            val estimated = StrengthModel.estimate1RM(10000.0, reps)
            assertEquals(reps.toDouble(), StrengthModel.repsToFailureAt(10000.0, estimated), 0.002)
        }
        assertEquals(7.5, StrengthModel.repsToFailureAt(100.0, 100.0 * 36.0 / 29.5), 1e-9)
        assertEquals(15.5, StrengthModel.repsToFailureAt(100.0, 100.0 * (1.0 + 15.5 / 30.0)), 1e-9)
        assertEquals(1.0, StrengthModel.repsToFailureAt(200.0, 100.0), 0.0)
        assertEquals(50.0, StrengthModel.repsToFailureAt(10.0, 100.0), 0.0)
    }
}
