package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaceExerciseArchiveTest {

    @Test
    fun finishIncludesArchivedOriginalAndSuccessorStartsEmpty() {
        val original = Exercise(
            id = "ex1",
            name = "Press banca",
            sets = listOf(ExerciseSet(id = "s0", targetReps = 8)),
        )
        val successor = Exercise(
            id = "ex1",
            name = "Press mancuernas",
            sets = listOf(ExerciseSet(id = "s0", targetReps = 8)),
        )
        val completed = mapOf(
            workoutSetKey("ex1", 0) to CompletedSet(id = "done", weight = 80.0, reps = 8),
        )
        val archived = toCompletedExercises(
            session = Session(id = "sess", name = original.name, exercises = listOf(original)),
            completedSets = completed,
        )
        val cleaned = completed.filterKeys { !it.startsWith("ex1_") }
        val live = toCompletedExercises(
            session = Session(id = "sess", name = "Push", exercises = listOf(successor)),
            completedSets = cleaned,
        )
        val finish = archived + live
        assertEquals(1, archived.size)
        assertEquals("Press banca", finish.first().exerciseName)
        assertTrue(cleaned.isEmpty())
        assertTrue(live.isEmpty())
    }
}
