package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnilateralTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCompletedExercisesParityTest {

    @Test
    fun skippedWithLoggedSetsStillAppearAndCardioDetailsCopy() {
        val cardio = Exercise(
            id = "cardio1",
            name = "Bici",
            cardioDetails = com.example.kpkn.data.models.CardioDetails(
                type = com.example.kpkn.data.models.CardioType.BIKE_STATIONARY,
                targetDurationSeconds = 600,
            ),
            sets = listOf(ExerciseSet(id = "c0", targetDuration = 600)),
        )
        val curl = Exercise(
            id = "curl1",
            name = "Curl",
            sets = listOf(ExerciseSet(id = "s0", targetReps = 10)),
        )
        val session = Session(id = "s", name = "A", exercises = listOf(cardio, curl))
        val completed = mapOf(
            "cardio1_0" to CompletedSet(id = "done-c", timeSeconds = 600, reps = 0, weight = 0.0),
            "curl1_0" to CompletedSet(id = "done-k", weight = 12.0, reps = 10),
        )
        val live = toCompletedExercises(
            session = session,
            completedSets = completed,
            skippedExerciseIds = setOf("curl1"),
        )
        assertEquals(2, live.size)
        assertTrue(live.any { it.exerciseId == "curl1" && it.sets.isNotEmpty() })
        assertEquals(cardio.cardioDetails, live.first { it.exerciseId == "cardio1" }.cardioDetails)
    }

    @Test
    fun logicalSetCountCountsUnilateralPairAsOne() {
        val unilateral = Exercise(
            id = "curl1",
            name = "Curl",
            isUnilateral = true,
            sets = listOf(
                ExerciseSet(id = "s0", targetReps = 10),
                ExerciseSet(id = "s1", targetReps = 10),
                ExerciseSet(id = "s2", targetReps = 10),
            ),
        )
        val session = Session(id = "s", name = "A", exercises = listOf(unilateral))
        val completed = mapOf(
            "curl1_0_L" to CompletedSet(id = "l0", weight = 12.0, reps = 10, side = "left"),
            "curl1_0_R" to CompletedSet(id = "r0", weight = 12.0, reps = 10, side = "right"),
            "curl1_1_L" to CompletedSet(id = "l1", weight = 12.0, reps = 10, side = "left"),
            "curl1_1_R" to CompletedSet(id = "r1", weight = 12.0, reps = 10, side = "right"),
            "curl1_2_L" to CompletedSet(id = "l2", weight = 12.0, reps = 10, side = "left"),
            "curl1_2_R" to CompletedSet(id = "r2", weight = 12.0, reps = 10, side = "right"),
        )
        assertEquals(3, logicalSetCount(completed, session))
        val built = toCompletedExercises(session, completed)
        assertEquals(3, logicalSetCountFromCompleted(built))
        assertEquals(3, logicalWorkingSetCount(unilateral, completed))
    }

    @Test
    fun stackedDropWithoutSubseriesStaysOpen() {
        val exercise = Exercise(
            id = "x",
            name = "Curl",
            sets = listOf(ExerciseSet(id = "s0", isDropSet = true, targetReps = 8)),
        )
        val open = mapOf("x_0" to CompletedSet(id = "c", weight = 20.0, reps = 8))
        assertTrue(isStackedTechniqueStillOpen(exercise, 0, open))
        val closed = mapOf(
            "x_0" to CompletedSet(
                id = "c",
                weight = 20.0,
                reps = 8,
                dropSets = listOf(com.example.kpkn.data.models.DropSetData(weight = 15.0, reps = 6)),
            ),
        )
        assertFalse(isStackedTechniqueStillOpen(exercise, 0, closed))
    }
}
