package com.example.kpkn.screens.workout.components

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LiveSetConfigSheetTest {
    @Test
    fun prescriptionEditAllowsAnExerciseWithoutConfiguredRest() {
        val exercise = Exercise(id = "press", name = "Press")
        val set = ExerciseSet(id = "set-1", targetReps = 8, targetRIR = 2)
        val draft = liveSetConfigDraft(exercise, set).copy(max = "10")

        val updated = applyLiveSetConfig(draft, set, exercise.restTime)

        assertNotNull(updated)
        assertEquals(10, updated?.targetReps)
        assertEquals(8, updated?.targetRepsRange?.min)
        assertEquals(10, updated?.targetRepsRange?.max)
        assertEquals(null, updated?.restAfterSeconds)
    }
}
