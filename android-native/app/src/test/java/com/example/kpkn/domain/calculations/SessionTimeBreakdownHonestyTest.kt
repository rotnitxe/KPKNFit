package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SupersetGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTimeBreakdownHonestyTest {

    @Test
    fun emptySetsDoNotInventPlaceholderWork() {
        val session = Session(
            id = "s",
            name = "Empty",
            exercises = listOf(
                Exercise(id = "e1", name = "Press", restTime = 90, sets = emptyList()),
            ),
        )
        val breakdown = calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
        )
        assertEquals(0, breakdown.totalSetCount)
        assertEquals(0, breakdown.executionSeconds)
        assertEquals(0, breakdown.restSeconds)
    }

    @Test
    fun lastSetDoesNotChargeTrailingRest() {
        val session = Session(
            id = "s",
            name = "Rests",
            exercises = listOf(
                Exercise(
                    id = "e1",
                    name = "Press",
                    restTime = 90,
                    sets = List(3) { ExerciseSet(id = "s$it", targetReps = 8) },
                ),
            ),
        )
        val breakdown = calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
            averageSetupSeconds = 0,
            averageWorkSeconds = 45,
        )
        assertEquals(135, breakdown.executionSeconds)
        assertEquals(180, breakdown.restSeconds)
    }

    @Test
    fun supersetRoundRestSkipsTheLastRound() {
        val group = SupersetGroup(
            id = "g1",
            exerciseOrder = listOf("a", "b"),
            restBetweenExercises = 30,
            restAfterSuperset = 90,
        )
        val exercises = listOf(
            Exercise(
                id = "a",
                name = "A",
                supersetGroupRef = "g1",
                sets = List(2) { ExerciseSet(id = "a$it", targetReps = 8) },
            ),
            Exercise(
                id = "b",
                name = "B",
                supersetGroupRef = "g1",
                sets = List(2) { ExerciseSet(id = "b$it", targetReps = 8) },
            ),
        )
        val breakdown = calculateSessionTimeBreakdown(
            exercises = exercises,
            supersetGroups = listOf(group),
            averageSetupSeconds = 0,
            averageWorkSeconds = 0,
        )
        // 2 rounds * 30s intra between 2 members = 60, plus 1 * 90s after first round.
        assertEquals(150, breakdown.restSeconds)
    }
}
