package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.UnilateralTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineSetRowStateTest {

    @Test
    fun rulesIntensityActivatesRegularSetSelector() {
        val set = ExerciseSet(
            id = "set-rpe",
            targetRPE = 7.5,
            intensityMode = IntensityMode.RPE,
        )

        assertTrue(shouldShowPlannedIntensity(set, isUnilateral = false, activeSideTarget = null))
    }

    @Test
    fun unilateralSelectorUsesOnlyTheRenderedSide() {
        val set = ExerciseSet(
            id = "set-unilateral",
            leftTarget = UnilateralTarget(targetRPE = 7.0, intensityMode = IntensityMode.RPE),
            rightTarget = null,
        )

        assertTrue(shouldShowPlannedIntensity(set, isUnilateral = true, activeSideTarget = set.leftTarget))
        assertFalse(shouldShowPlannedIntensity(set, isUnilateral = true, activeSideTarget = set.rightTarget))
    }

    @Test
    fun emptySetKeepsIntensitySelectorOptional() {
        val set = ExerciseSet(id = "set-empty")

        assertFalse(shouldShowPlannedIntensity(set, isUnilateral = false, activeSideTarget = null))
    }
}
