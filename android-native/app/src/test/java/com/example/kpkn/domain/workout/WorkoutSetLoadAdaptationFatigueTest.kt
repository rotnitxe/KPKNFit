package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedTechniqueStatus
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSetLoadAdaptationFatigueTest {
    @Test
    fun `series pesadas seguidas reducen la sugerencia por fatiga acumulada sin repetir exacto`() {
        val target = ExerciseSet("s", targetReps = 5, targetRIR = 2)
        val afterSet1 = WorkoutSetLoadAdaptation.suggestNextSetLoad(
            priorSetsInSession = listOf(CompletedSet("s0", weight = 100.0, reps = 5, rir = 2)),
            nextSet = target,
            stepKg = 2.5,
        )
        assertNotNull(afterSet1)
        assertEquals(97.5, afterSet1!!.weightKg, 0.0)
        assertTrue(afterSet1.isAdjusted)

        val afterSet2 = WorkoutSetLoadAdaptation.suggestNextSetLoad(
            priorSetsInSession = listOf(
                CompletedSet("s0", weight = 100.0, reps = 5, rir = 2),
                CompletedSet("s1", weight = 97.5, reps = 5, rir = 1),
            ),
            nextSet = target,
            stepKg = 2.5,
        )
        assertNotNull(afterSet2)
        assertTrue(afterSet2!!.weightKg < 97.5)
        assertTrue(afterSet2.weightKg % 2.5 == 0.0)
    }

    @Test
    fun `serie moderada sin exceso de esfuerzo mantiene carga redondeada sin decimales raros`() {
        val suggestion = WorkoutSetLoadAdaptation.suggestNextSetLoad(
            priorSetsInSession = listOf(CompletedSet("s0", weight = 72.75, reps = 8, rir = 4)),
            nextSet = ExerciseSet("s1", targetReps = 8, targetRIR = 3),
            stepKg = 2.5,
        )
        assertNotNull(suggestion)
        assertEquals(72.5, suggestion!!.weightKg, 0.0)
    }

    @Test
    fun `series normales ya completadas con techniqueStatus COMPLETED siguen siendo elegibles`() {
        val suggestion = WorkoutSetLoadAdaptation.suggestNextSetLoad(
            priorSetsInSession = listOf(
                CompletedSet(
                    id = "s0",
                    weight = 80.0,
                    reps = 5,
                    rir = 1,
                    techniqueStatus = RecordedTechniqueStatus.COMPLETED,
                ),
            ),
            nextSet = ExerciseSet("s1", targetReps = 5, targetRIR = 2),
            stepKg = 2.5,
        )
        assertNotNull(suggestion)
        assertTrue(suggestion!!.weightKg < 80.0)
        assertTrue(suggestion.isAdjusted)
    }

    @Test
    fun `la desigualdad RIR vs targetRPE detecta exceso real de esfuerzo`() {
        val actualHarder = CompletedSet("s0", weight = 100.0, reps = 5, rir = 0)
        val targetModerate = ExerciseSet("s1", targetReps = 5, targetRPE = 8.0)
        assertEquals(
            15,
            WorkoutSetLoadAdaptation.reductionPercent(
                actual = actualHarder,
                target = targetModerate,
                expectedSide = null,
                expectedLoadMode = LoadModeV2.LOAD,
                expectedUnitMode = UnitModeV2.REPS,
            ),
        )
    }
}
