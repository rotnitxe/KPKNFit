package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVolumeDeltaTest {

    private fun pecInfo(): ExerciseMuscleInfo = ExerciseMuscleInfo(
        id = "bench",
        name = "Press banca",
        involvedMuscles = listOf(InvolvedMuscle(muscle = "Pectorales", role = MuscleRole.PRIMARY)),
    )

    private fun bench(id: String, setCount: Int): Exercise = Exercise(
        id = id,
        name = "Press banca",
        exerciseDbId = "bench",
        sets = (0 until setCount).map { ExerciseSet(id = "${id}_s$it", targetReps = 8, weight = 80.0) },
    )

    private fun completed(exerciseId: String, count: Int): Map<String, CompletedSet> =
        (0 until count).associate { idx ->
            "${exerciseId}_$idx" to CompletedSet(id = "c$idx", weight = 80.0, reps = 8)
        }

    @Test
    fun surplusVsBaselineCountsAddedAndCompletedSets() {
        val baseline = Session(id = "s1", name = "A", exercises = listOf(bench("ex1", 2)))
        val live = Session(id = "s1", name = "A", exercises = listOf(bench("ex1", 3)))
        val index = mapOf("bench" to pecInfo())
        val surplus = computeMuscleSetSurplus(
            plannedSession = baseline,
            liveSession = live,
            completedSets = completed("ex1", 3),
            exerciseIndex = index,
        )
        assertEquals(1.0, surplus["Pectorales"] ?: 0.0, 0.001)
    }

    @Test
    fun surplusVsMutatedLiveSessionIsZeroTheOldBug() {
        val live = Session(id = "s1", name = "A", exercises = listOf(bench("ex1", 3)))
        val index = mapOf("bench" to pecInfo())
        val surplus = computeMuscleSetSurplus(
            plannedSession = live,
            liveSession = live,
            completedSets = completed("ex1", 3),
            exerciseIndex = index,
        )
        assertTrue(surplus.isEmpty())
    }

    @Test
    fun noSurplusWhenCompletedMatchesBaseline() {
        val baseline = Session(id = "s1", name = "A", exercises = listOf(bench("ex1", 3)))
        val live = baseline.copy(exercises = listOf(bench("ex1", 3)))
        val index = mapOf("bench" to pecInfo())
        val surplus = computeMuscleSetSurplus(
            plannedSession = baseline,
            liveSession = live,
            completedSets = completed("ex1", 3),
            exerciseIndex = index,
        )
        assertTrue(surplus.isEmpty())
    }
}
