package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemapCompletedSetKeysTest {

    @Test
    fun deletingMiddleSet_rewritesKeysBySetId() {
        val setA = ExerciseSet(id = "set-a", targetReps = 8)
        val setB = ExerciseSet(id = "set-b", targetReps = 8)
        val setC = ExerciseSet(id = "set-c", targetReps = 8)
        val previous = session(
            Exercise(id = "ex1", name = "Press", sets = listOf(setA, setB, setC)),
        )
        val next = session(
            Exercise(id = "ex1", name = "Press", sets = listOf(setA, setC)),
        )
        val completed = mapOf(
            workoutSetKey("ex1", 0) to CompletedSet(id = "c0", weight = 80.0, reps = 8),
            workoutSetKey("ex1", 1) to CompletedSet(id = "c1", weight = 82.0, reps = 7),
            workoutSetKey("ex1", 2) to CompletedSet(id = "c2", weight = 85.0, reps = 6),
        )

        val remapped = remapIndexKeyedMap(previous, next, completed)

        assertEquals(2, remapped.size)
        assertEquals("c0", remapped[workoutSetKey("ex1", 0)]?.id)
        assertEquals("c2", remapped[workoutSetKey("ex1", 1)]?.id)
        assertNull(remapped[workoutSetKey("ex1", 2)])
    }

    @Test
    fun deletingExercise_dropsItsKeys_andKeepsNeighborDrafts() {
        val previous = session(
            Exercise(id = "exA", name = "A", sets = listOf(ExerciseSet(id = "a0"))),
            Exercise(id = "exB", name = "B", sets = listOf(ExerciseSet(id = "b0"), ExerciseSet(id = "b1"))),
        )
        val next = session(
            Exercise(id = "exB", name = "B", sets = listOf(ExerciseSet(id = "b0"), ExerciseSet(id = "b1"))),
        )
        val completed = mapOf(
            workoutSetKey("exA", 0) to CompletedSet(id = "ca", reps = 5),
            workoutSetKey("exB", 1) to CompletedSet(id = "cb", reps = 8),
        )
        val drafts = mapOf(
            workoutSetKey("exA", 0) to WorkoutSetDraft(weightText = "100"),
            workoutSetKey("exB", 1) to WorkoutSetDraft(weightText = "60"),
        )

        val remappedCompleted = remapIndexKeyedMap(previous, next, completed)
        val remappedDrafts = remapIndexKeyedMap(previous, next, drafts)

        assertFalse(remappedCompleted.keys.any { it.startsWith("exA_") })
        assertEquals("cb", remappedCompleted[workoutSetKey("exB", 1)]?.id)
        assertNull(remappedDrafts[workoutSetKey("exA", 0)])
        assertEquals("60", remappedDrafts[workoutSetKey("exB", 1)]?.weightText)
        assertEquals(setOf("exB"), pruneExerciseIdSet(setOf("exA", "exB"), setOf("exB")))
    }

    @Test
    fun omittedKeysAndDrafts_shiftWithDeletedSet() {
        val setA = ExerciseSet(id = "set-a")
        val setB = ExerciseSet(id = "set-b")
        val setC = ExerciseSet(id = "set-c")
        val previous = session(Exercise(id = "ex1", name = "Press", sets = listOf(setA, setB, setC)))
        val next = session(Exercise(id = "ex1", name = "Press", sets = listOf(setA, setC)))
        val omitted = setOf(workoutSetKey("ex1", 1), workoutSetKey("ex1", 2))
        val drafts = mapOf(
            workoutSetKey("ex1", 1) to WorkoutSetDraft(valueText = "8"),
            workoutSetKey("ex1", 2) to WorkoutSetDraft(valueText = "6"),
        )

        val remappedOmitted = remapIndexKeyedSet(previous, next, omitted)
        val remappedDrafts = remapIndexKeyedMap(previous, next, drafts)

        assertEquals(setOf(workoutSetKey("ex1", 1)), remappedOmitted)
        assertTrue(workoutSetKey("ex1", 2) !in remappedOmitted)
        assertEquals("6", remappedDrafts[workoutSetKey("ex1", 1)]?.valueText)
        assertNull(remappedDrafts[workoutSetKey("ex1", 2)])
    }

    private fun session(vararg exercises: Exercise): Session =
        Session(id = "s1", name = "Push", exercises = exercises.toList())
}
