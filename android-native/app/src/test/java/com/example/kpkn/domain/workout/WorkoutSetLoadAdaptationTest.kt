package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSetLoadAdaptationTest {
    private fun actual(
        reps: Int = 8,
        rpe: Double? = null,
        rir: Int? = null,
        rom: Int? = 100,
        side: String? = null,
        warmup: Boolean = false,
        skipped: Boolean = false,
        mode: LoadModeV2? = LoadModeV2.LOAD,
        unit: UnitModeV2 = UnitModeV2.REPS,
        failure: Boolean = false,
    ) = CompletedSet(
        id = "actual",
        weight = 100.0,
        reps = reps,
        rpe = rpe,
        rir = rir,
        rom = rom,
        side = side,
        isWarmup = warmup,
        skipped = skipped,
        loadMode = mode,
        isFailure = failure,
        actualIntensityMode = if (rpe != null) IntensityMode.RPE else if (rir != null) IntensityMode.RIR else null,
        actualIntensityValue = rpe ?: rir?.toDouble(),
    )

    @Test fun `20 percent rule wins when reps or rom are incomplete`() {
        assertEquals(20, WorkoutSetLoadAdaptation.reductionPercent(
            actual(reps = 7), ExerciseSet(id = "s", targetReps = 8), null, LoadModeV2.LOAD, UnitModeV2.REPS,
        ))
        assertEquals(20, WorkoutSetLoadAdaptation.reductionPercent(
            actual(rpe = 10.0, rom = 95), ExerciseSet(id = "s", targetReps = 8, targetRPE = 8.0), null,
            LoadModeV2.LOAD, UnitModeV2.REPS,
        ))
    }

    @Test fun `15 percent requires greater reported effort than the explicit target`() {
        assertEquals(15, WorkoutSetLoadAdaptation.reductionPercent(
            actual(rpe = 9.0), ExerciseSet(id = "s", targetReps = 8, targetRPE = 8.0), null,
            LoadModeV2.LOAD, UnitModeV2.REPS,
        ))
        assertNull(WorkoutSetLoadAdaptation.reductionPercent(
            actual(rir = 0), ExerciseSet(id = "s", targetReps = 8, targetRIR = 0), null,
            LoadModeV2.LOAD, UnitModeV2.REPS,
        ))
    }

    @Test fun `unknown evidence and incompatible set types do not reduce`() {
        val target = ExerciseSet(id = "s", targetReps = 8, targetRPE = 8.0)
        assertNull(WorkoutSetLoadAdaptation.reductionPercent(actual(), target, null, LoadModeV2.LOAD, UnitModeV2.REPS))
        assertNull(WorkoutSetLoadAdaptation.reductionPercent(actual(warmup = true, rpe = 10.0), target, null, LoadModeV2.LOAD, UnitModeV2.REPS))
        assertNull(WorkoutSetLoadAdaptation.reductionPercent(actual(skipped = true, reps = 2), target, null, LoadModeV2.LOAD, UnitModeV2.REPS))
        assertNull(WorkoutSetLoadAdaptation.reductionPercent(actual(side = "right", rpe = 10.0), target, "left", LoadModeV2.LOAD, UnitModeV2.REPS))
        assertNull(WorkoutSetLoadAdaptation.reductionPercent(actual(mode = LoadModeV2.ASSISTED, rpe = 10.0), target, null, LoadModeV2.ASSISTED, UnitModeV2.REPS))
        assertNull(WorkoutSetLoadAdaptation.reductionPercent(actual(unit = UnitModeV2.TIME, rpe = 10.0), target, null, LoadModeV2.LOAD, UnitModeV2.TIME))
    }

    @Test fun `reductions floor to equipment step or quarter kilo grid`() {
        assertEquals(82.0, WorkoutSetLoadAdaptation.reducedLoadKg(103.0, 20, 0.5)!!, 0.0)
        assertEquals(85.75, WorkoutSetLoadAdaptation.reducedLoadKg(101.0, 15)!!, 0.0)
    }
}
