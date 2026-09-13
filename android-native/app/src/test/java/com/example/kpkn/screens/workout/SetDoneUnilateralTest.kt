package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.domain.workout.isSetDone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetDoneUnilateralTest {

    @Test
    fun leftOnlyPlanIsDoneWithLeftKey() {
        val ex = Exercise(
            id = "exL",
            name = "Curl",
            isUnilateral = true,
            sets = listOf(
                ExerciseSet(
                    id = "s0",
                    targetReps = 10,
                    leftTarget = UnilateralTarget(targetReps = 10),
                ),
            ),
        )
        val completed = mapOf("exL_0_L" to CompletedSet(id = "l", side = "left"))
        assertTrue(ex.isSetDone(completed, 0))
        assertFalse(ex.isSetDone(emptyMap<String, CompletedSet>(), 0))
    }

    @Test
    fun pairedUnilateralNeedsBothSides() {
        val ex = Exercise(
            id = "exU",
            name = "Curl",
            isUnilateral = true,
            sets = listOf(ExerciseSet(id = "s0", targetReps = 10)),
        )
        val leftOnly = mapOf("exU_0_L" to CompletedSet(id = "l", side = "left"))
        val both = leftOnly + ("exU_0_R" to CompletedSet(id = "r", side = "right"))
        assertFalse(ex.isSetDone(leftOnly, 0))
        assertTrue(ex.isSetDone(both, 0))
    }

    @Test
    fun bilateralUsesPlainKey() {
        val ex = Exercise(
            id = "exB",
            name = "Press",
            sets = listOf(ExerciseSet(id = "s0", targetReps = 8)),
        )
        assertTrue(ex.isSetDone(mapOf("exB_0" to CompletedSet(id = "b")), 0))
        assertFalse(ex.isSetDone(mapOf("exB_0_L" to CompletedSet(id = "l")), 0))
    }
}
