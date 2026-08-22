package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TypedBodyGoal
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileProgressTest {
    @Test
    fun `starred exercises merge by canonical identity and calculate eRM`() {
        val program = starredProgram()
        val log = WorkoutLog(
            id = "log",
            programId = "program",
            sessionId = "session",
            sessionName = "Día A",
            date = "2026-08-22T10:00:00Z",
            durationMinutes = 60,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "bench-variant",
                    exerciseName = "Press banca con pausa",
                    canonicalExerciseId = "bench",
                    sets = listOf(
                        CompletedSet(id = "set", weight = 80.0, reps = 5),
                        // Lower weight x more reps must not hide the best eRM.
                        CompletedSet(id = "set-best", weight = 100.0, reps = 3),
                    ),
                ),
            ),
        )

        val result = buildStarredExerciseProgress(listOf(program, program.copy(id = "program-2")), listOf(log))

        assertEquals(1, result.size)
        assertEquals("Press banca", result.single().name)
        assertEquals(100.0, result.single().goal1RM!!, 0.001)
        assertEquals(110.0, result.single().bestEstimated1RM!!, 0.01)
        assertEquals(1, result.single().sessions)
        assertEquals(1.0f, result.single().progressFraction!!, 0.01f)
    }

    @Test
    fun `starred exercise without log or goal exposes honest missing progress`() {
        val result = buildStarredExerciseProgress(listOf(starredProgram(goal1RM = null)), emptyList())

        assertTrue(result.single().bestEstimated1RM == null)
        assertNull(result.single().goal1RM)
        assertNull(result.single().progressFraction)
    }

    @Test
    fun `nutrition progress is null until current and target exist`() {
        val plan = NutritionPlan(
            id = "plan",
            goalType = GoalMetric.WEIGHT,
            startValue = 90.0,
            typedBodyGoal = TypedBodyGoal(GoalMetric.WEIGHT, targetValueSi = 80.0),
        )
        val missing = buildNutritionGoalProgress(plan, emptyList())
        assertEquals(GoalMetric.WEIGHT, missing?.metric)
        assertNull(missing?.currentValue)
        assertNull(missing?.percent)

        val current = buildNutritionGoalProgress(
            plan,
            listOf(BodyObservation("weight", BodyMetric.WEIGHT, 85.0, "kg", timestampEpochMs = 1L)),
        )
        assertEquals(50, current?.percent)
    }

    @Test
    fun `nutrition goal equal to start is complete only at the target`() {
        val plan = NutritionPlan(
            id = "maintenance",
            goalType = GoalMetric.WEIGHT,
            startValue = 80.0,
            typedBodyGoal = TypedBodyGoal(GoalMetric.WEIGHT, targetValueSi = 80.0),
        )
        val progress = buildNutritionGoalProgress(
            plan,
            listOf(BodyObservation("weight", BodyMetric.WEIGHT, 80.0, "kg", timestampEpochMs = 2L)),
        )

        assertEquals(100, progress?.percent)
    }

    private fun starredProgram(goal1RM: Double? = 100.0): Program = Program(
        id = "program",
        name = "Rutina",
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Block",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso",
                                name = "Meso",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "week",
                                        name = "Week",
                                        sessions = listOf(
                                            Session(
                                                id = "session",
                                                name = "Día A",
                                                exercises = listOf(
                                                    Exercise(
                                                        id = "bench",
                                                        name = "Press banca",
                                                        canonicalExerciseId = "bench",
                                                        isStarTarget = true,
                                                        goal1RM = goal1RM,
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
