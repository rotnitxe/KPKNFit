package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.CompletedSet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AugeFatigueEngineEffectiveSetTest {

    @Test
    fun warmupWithDefaultRpeIsNotEffective() {
        val warmup = CompletedSet(
            id = "w1",
            weight = 60.0,
            reps = 10,
            isWarmup = true,
            // no explicit RPE → getEffectiveRPE defaults to 7.0 (>= 6)
        )
        assertFalse(AugeFatigueEngine.isSetEffective(warmup))
    }

    @Test
    fun workingSetWithDefaultRpeIsEffective() {
        val working = CompletedSet(
            id = "s1",
            weight = 100.0,
            reps = 8,
            isWarmup = false,
        )
        assertTrue(AugeFatigueEngine.isSetEffective(working))
    }

    @Test
    fun plankTimeOnlyIsEffectiveWithoutCardioDetails() {
        val plank = CompletedSet(id = "plank", reps = 0, weight = 0.0, timeSeconds = 45, rpe = 7.0)
        assertTrue(AugeFatigueEngine.isSetEffective(plank))
    }

    @Test
    fun failedEmptySetIsNotEffective() {
        val failed = CompletedSet(id = "f1", reps = 0, weight = 0.0, isFailedSet = true)
        assertFalse(AugeFatigueEngine.isSetEffective(failed))
    }
}
