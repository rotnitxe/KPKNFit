package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RpeConversionRecordedEffortTest {
    private fun set(
        id: String,
        setIndex: Int,
        rpe: Double? = null,
        rir: Int? = null,
        mode: IntensityMode? = null,
        value: Double? = null,
        side: String? = null,
        warmup: Boolean = false,
        reachedFailure: Boolean = false,
    ) = CompletedSet(
        id = id,
        weight = 100.0,
        reps = 5,
        rpe = rpe,
        rir = rir,
        isWarmup = warmup,
        side = side,
        actualIntensityMode = mode,
        actualIntensityValue = value,
        setIndex = setIndex,
        recordedPayloadV3 = RecordedSetPayload(
            exerciseId = "exercise",
            unitMode = UnitModeV2.REPS,
            actualIntensityMode = mode,
            actualIntensityValue = value,
            reachedFailure = reachedFailure,
        ),
    )

    private fun exercise(vararg sets: CompletedSet) = CompletedExercise(
        exerciseId = "exercise",
        exerciseName = "Ejercicio",
        sets = sets.toList(),
    )

    @Test
    fun derivesAverageFromRpeAndRirWithCoverage() {
        val result = RpeConversion.recordedEffort(
            listOf(
                exercise(
                    set("a", 0, rpe = 8.0, mode = IntensityMode.RPE, value = 8.0),
                    set("b", 1, rir = 1, mode = IntensityMode.RIR, value = 1.0),
                    set("c", 2),
                ),
            ),
        )
        assertEquals(8.5, result.value ?: -1.0, 0.0)
        assertEquals(2, result.coveredSets)
        assertEquals(3, result.totalSets)
        assertEquals("8,5/10 · 2 de 3 series", result.coverageLabel)
    }

    @Test
    fun explicitFailureIsTenButPrescribedFailureWithoutIntensityIsNotAValue() {
        val explicit = RpeConversion.recordedEffort(
            listOf(exercise(set("a", 0, mode = IntensityMode.FAILURE))),
        )
        assertEquals(10.0, explicit.value ?: -1.0, 0.0)

        val prescribed = RpeConversion.recordedEffort(
            listOf(exercise(set("b", 0))),
        )
        assertNull(prescribed.value)
        assertEquals("Sin intensidad registrada", prescribed.coverageLabel)
    }

    @Test
    fun averagesSidesBeforeLogicalSetsAndExcludesWarmups() {
        val result = RpeConversion.recordedEffort(
            listOf(
                exercise(
                    set("l", 0, rpe = 8.0, mode = IntensityMode.RPE, value = 8.0, side = "left"),
                    set("r", 0, rpe = 9.0, mode = IntensityMode.RPE, value = 9.0, side = "right"),
                    set("warm", 1, rpe = 5.0, mode = IntensityMode.RPE, value = 5.0, warmup = true),
                ),
            ),
        )
        assertEquals(8.5, result.value ?: -1.0, 0.0)
        assertEquals(1, result.coveredSets)
        assertEquals(1, result.totalSets)
    }
}
