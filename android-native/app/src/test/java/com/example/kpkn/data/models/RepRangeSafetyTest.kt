package com.example.kpkn.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepRangeSafetyTest {

    @Test
    fun plannedTargetZeroDoesNotBuildRepRange() {
        val set = ExerciseSet(id = "s0", targetReps = 0, plannedTargetV2 = 0.0)
        assertNull(set.effectiveRepRange())
        val fromPlanned = set.plannedTargetV2?.toInt()?.takeIf { it > 0 }?.let { RepRange(it, it) }
        assertNull(fromPlanned)
    }

    @Test
    fun positiveTargetBuildsRange() {
        val set = ExerciseSet(id = "s0", targetReps = 8)
        assertEquals(8, set.effectiveRepRange()?.min)
        assertEquals(8, set.effectiveRepRange()?.max)
    }
}
