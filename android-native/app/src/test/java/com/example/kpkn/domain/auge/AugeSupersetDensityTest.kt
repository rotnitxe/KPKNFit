package com.example.kpkn.domain.auge

import org.junit.Assert.assertTrue
import org.junit.Test

class AugeSupersetDensityTest {
    @Test
    fun supersetDensityScalesWithRestExerciseCountAndRounds() {
        val baseline = AugeFatigueEngine.getDensityMultiplierForExercise(
            supersetId = "ss",
            restTime = 90,
            supersetExerciseCount = 2,
            supersetRounds = 2,
            supersetRestAfter = 180,
        )
        val dense = AugeFatigueEngine.getDensityMultiplierForExercise(
            supersetId = "ss",
            restTime = 30,
            supersetExerciseCount = 4,
            supersetRounds = 5,
            supersetRestAfter = 45,
        )
        val nonSuperset = AugeFatigueEngine.getDensityMultiplierForExercise(
            supersetId = null,
            restTime = 30,
            supersetExerciseCount = 4,
            supersetRounds = 5,
            supersetRestAfter = 45,
        )

        assertTrue(baseline > 1.0)
        assertTrue(dense > baseline)
        assertTrue(dense <= 1.45)
        assertTrue(nonSuperset == 1.0)
    }
}
