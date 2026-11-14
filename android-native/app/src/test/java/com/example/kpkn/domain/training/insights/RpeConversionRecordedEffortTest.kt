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
        isFailure: Boolean = false,
        isFailedSet: Boolean = false,
        failureReason: String? = null,
        executionError: Boolean = false,
    ) = CompletedSet(
        id = id,
        weight = 100.0,
        reps = 5,
        rpe = rpe,
        rir = rir,
        isWarmup = warmup,
        isFailure = isFailure,
        isFailedSet = isFailedSet,
        failureReason = failureReason,
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
            executionError = executionError,
        ),
    )

    private fun exercise(vararg sets: CompletedSet, occurrenceId: String? = null) = CompletedExercise(
        exerciseId = "exercise",
        exerciseName = "Ejercicio",
        sets = sets.toList(),
        occurrenceId = occurrenceId,
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
        assertEquals("Sin intensidad registrada · 0 de 1 series", prescribed.coverageLabel)
    }

    @Test
    fun reachedFailureLegacyCountsAsTenButFailedExecutionsAreExcluded() {
        val result = RpeConversion.recordedEffort(
            listOf(
                exercise(
                    set("execution-flag", 0, rpe = 9.0, mode = IntensityMode.RPE, value = 9.0, isFailedSet = true),
                    set("execution-error", 1, rpe = 8.0, mode = IntensityMode.RPE, value = 8.0, failureReason = "execution_error"),
                    set("legacy-failure", 2, isFailure = true),
                    set("missing", 3),
                ),
            ),
        )

        assertEquals(10.0, result.value ?: -1.0, 0.0)
        assertEquals(1, result.coveredSets)
        assertEquals(2, result.totalSets)
        assertEquals("10/10 · 1 de 2 series", result.coverageLabel)
    }

    @Test
    fun payloadExecutionErrorsAreExcludedFromCoverage() {
        val result = RpeConversion.recordedEffort(
            listOf(
                exercise(
                    set("payload-error", 0, rpe = 8.0, mode = IntensityMode.RPE, value = 8.0, executionError = true),
                    set("missing", 1),
                ),
            ),
        )

        assertNull(result.value)
        assertEquals(0, result.coveredSets)
        assertEquals(1, result.totalSets)
        assertEquals("Sin intensidad registrada · 0 de 1 series", result.coverageLabel)
    }

    @Test
    fun repeatedExerciseOccurrencesKeepSeparateLogicalSets() {
        val result = RpeConversion.recordedEffort(
            listOf(
                exercise(set("a", 0, rpe = 8.0, mode = IntensityMode.RPE, value = 8.0), occurrenceId = "slot-a"),
                exercise(set("b", 0, rpe = 10.0, mode = IntensityMode.RPE, value = 10.0), occurrenceId = "slot-b"),
            ),
        )

        assertEquals(9.0, result.value ?: -1.0, 0.0)
        assertEquals(2, result.coveredSets)
        assertEquals(2, result.totalSets)
    }

    @Test
    fun zeroCoverageStillShowsTheLogicalSetDenominator() {
        val result = RpeConversion.recordedEffort(
            listOf(exercise(set("a", 0), set("b", 1), set("c", 2))),
        )
        assertEquals("Sin intensidad registrada · 0 de 3 series", result.coverageLabel)
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
