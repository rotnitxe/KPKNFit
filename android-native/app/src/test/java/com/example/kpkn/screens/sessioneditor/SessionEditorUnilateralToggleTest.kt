package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.models.UnilateralTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorUnilateralToggleTest {

    @Test
    fun toggledBilateralUnilateral_enablesSingleVisibleUnilateralMode() {
        val exercise = Exercise(id = "curl", name = "Curl")

        val updated = exercise.toggledBilateralUnilateral()

        assertTrue(updated.isUnilateral)
        assertEquals(UnilateralMode.UNILATERAL_PAIRED, updated.unilateralMode)
        assertEquals(15, updated.restBetweenSidesSeconds)
    }

    @Test
    fun toggledBilateralUnilateral_disablesAndClearsSideSpecificTargets() {
        val exercise = Exercise(
            id = "curl",
            name = "Curl",
            isUnilateral = true,
            unilateralMode = UnilateralMode.UNILATERAL_DIFFERENTIAL,
            restBetweenSidesSeconds = 30,
            sets = listOf(
                ExerciseSet(
                    id = "s1",
                    leftTarget = UnilateralTarget(targetReps = 10),
                    rightTarget = UnilateralTarget(targetReps = 8),
                    restBetweenSides = 20,
                ),
            ),
        )

        val updated = exercise.toggledBilateralUnilateral()

        assertFalse(updated.isUnilateral)
        assertEquals(UnilateralMode.BILATERAL, updated.unilateralMode)
        assertNull(updated.restBetweenSidesSeconds)
        assertNull(updated.sets.single().leftTarget)
        assertNull(updated.sets.single().rightTarget)
        assertNull(updated.sets.single().restBetweenSides)
    }
}
