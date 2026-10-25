package com.example.kpkn.domain.performance

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.plannedRepAnchor
import com.example.kpkn.domain.calculations.calculateWeightFrom1RMAndIntensity
import kotlin.math.pow
import kotlin.math.round
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("DEPRECATION")
class StrengthPrescriptionLegacyGoldenTest {
    @Test
    fun `la prescripcion conserva la tabla de oro de esfuerzo planificado`() {
        listOf(20.0, 100.0, 142.25, 240.0).forEach { e1rm ->
            (1..20).forEach { reps ->
                val plain = ExerciseSet("plan", targetReps = reps)
                val cases = listOf(plain) +
                    (0..4).map { plain.copy(targetRIR = it) } +
                    listOf(6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0).map { plain.copy(targetRPE = it) } +
                    listOf(IntensityMode.FAILURE, IntensityMode.AMRAP, IntensityMode.SOLO_RM).map {
                        plain.copy(intensityMode = it, targetRIR = 4, targetRPE = 6.0)
                    } +
                    listOf(IntensityMode.LOAD, IntensityMode.RIR, IntensityMode.RPE).map { plain.copy(intensityMode = it) } +
                    plain.copy(targetRIR = 1, targetRPE = 6.0) +
                    // Los indicadores sueltos no cambian la convención heredada (+2 sin esfuerzo declarado).
                    listOf(plain.copy(isFailure = true), plain.copy(isFailure = true, targetRIR = 1))
                cases.forEach { set ->
                    val expected = legacyPrescription(e1rm, set)
                    assertEquals("$e1rm × $set", expected, calculateWeightFrom1RMAndIntensity(e1rm, set))
                    assertEquals("$e1rm × $set", expected, StrengthModel.weightFor(e1rm, set))
                }
            }
        }
    }

    // Referencia congelada de Calculations.kt en 5d0abc72f; no usa el modelo actual.
    private fun legacyPrescription(reference1RM: Double, set: ExerciseSet): Double? {
        if (reference1RM <= 0) return null
        val reps = set.plannedRepAnchor() ?: return null
        if (reps <= 0) return null
        val effectiveReps = when {
            set.intensityMode == IntensityMode.FAILURE ||
                set.intensityMode == IntensityMode.AMRAP ||
                set.intensityMode == IntensityMode.SOLO_RM -> reps
            set.targetRIR != null -> reps + set.targetRIR!!
            set.targetRPE != null -> reps + (10 - set.targetRPE!!).toInt()
            else -> reps + 2
        }
        if (effectiveReps <= 0) return null
        val weight = legacyWeight(reference1RM, effectiveReps)
        return if (weight > 0) weight else null
    }

    private fun legacyWeight(e1rm: Double, reps: Int): Double {
        if (reps <= 0 || e1rm <= 0) return 0.0
        if (reps == 1) return e1rm
        val r = reps.coerceAtMost(50)
        val weight = when {
            r <= 10 -> e1rm * ((37 - r) / 36.0)
            r <= 20 -> e1rm / (1 + r / 30.0)
            else -> e1rm / ((1 + 20.0 / 30) * (1 + (r - 20) / 80.0).pow(0.9))
        }
        return maxOf(0.0, round(weight * 10.0) / 10.0)
    }
}
