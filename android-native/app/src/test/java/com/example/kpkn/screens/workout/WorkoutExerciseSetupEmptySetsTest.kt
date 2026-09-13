package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.ExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutExerciseSetupEmptySetsTest {

    @Test
    fun emptySets_doNotThrow_andReturnNull() {
        assertNull(resolveSetupCurrentSet(currentSet = null, sets = emptyList()))
    }

    @Test
    fun prefersExplicitCurrentSet() {
        val current = ExerciseSet(id = "current")
        val first = ExerciseSet(id = "first")
        assertEquals(current, resolveSetupCurrentSet(current, listOf(first)))
    }

    @Test
    fun fallsBackToFirstSet() {
        val first = ExerciseSet(id = "first")
        assertEquals(first, resolveSetupCurrentSet(null, listOf(first, ExerciseSet(id = "second"))))
    }
}
