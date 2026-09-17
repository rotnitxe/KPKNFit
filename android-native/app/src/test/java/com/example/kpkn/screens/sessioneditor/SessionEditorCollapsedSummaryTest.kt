package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.TrainingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionEditorCollapsedSummaryTest {
    @Test
    fun timeExercise_collapsesToCountBySeconds() {
        val exercise = Exercise(
            id = "copenhague",
            name = "Plancha Copenhague",
            trainingMode = TrainingMode.TIME,
            sets = listOf(ExerciseSet(id = "s1", targetDuration = 30)),
        )
        assertEquals("1×30s", formatExerciseCollapsedSummary(exercise))
    }

    @Test
    fun repsExercise_collapsesToCountByReps() {
        val exercise = Exercise(
            id = "press",
            name = "Press",
            trainingMode = TrainingMode.REPS,
            sets = listOf(ExerciseSet(id = "s1", targetReps = 8)),
        )
        assertEquals("1×8", formatExerciseCollapsedSummary(exercise))
    }
}
