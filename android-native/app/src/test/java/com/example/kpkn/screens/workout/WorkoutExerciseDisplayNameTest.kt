package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutExerciseDisplayNameTest {

    @Test
    fun displayNameIncludesLegacyVariantChip() {
        val exercise = Exercise(
            id = "display-name-test",
            name = "Press de Banca",
            variantName = "Smith",
        )

        assertEquals("Press de Banca · Smith", displayWorkoutExerciseName(exercise))
    }
}
