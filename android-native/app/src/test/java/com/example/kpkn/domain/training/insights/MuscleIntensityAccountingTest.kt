package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleIntensityAccountingTest {

    private fun testExerciseIndex(): Map<String, ExerciseMuscleInfo> {
        val bench = ExerciseMuscleInfo(
            id = "bench",
            name = "Press de Banca",
            involvedMuscles = listOf(
                InvolvedMuscle(muscle = "pectoralis", role = MuscleRole.PRIMARY),
                InvolvedMuscle(muscle = "triceps", role = MuscleRole.SECONDARY),
                InvolvedMuscle(muscle = "deltoid", role = MuscleRole.STABILIZER),
                InvolvedMuscle(muscle = "biceps", role = MuscleRole.NEUTRALIZER),
            ),
        )
        val squat = ExerciseMuscleInfo(
            id = "squat",
            name = "Sentadilla",
            involvedMuscles = listOf(
                InvolvedMuscle(muscle = "quadriceps", role = MuscleRole.PRIMARY),
                InvolvedMuscle(muscle = "gluteus_maximus", role = MuscleRole.SECONDARY),
            ),
        )
        return mapOf(
            "bench" to bench,
            "press de banca" to bench,
            "squat" to squat,
            "sentadilla" to squat,
        )
    }

    @Test
    fun `global RIR and RPE convert uniformly with 1_0 primary 0_5 secondary 0_3 stabilizer and 0_0 neutralizer weights`() {
        val benchCompleted = CompletedExercise(
            exerciseId = "e1",
            exerciseDbId = "bench",
            exerciseName = "Press de Banca",
            sets = listOf(
                // Warmup should be excluded
                CompletedSet(id = "w1", weight = 40.0, reps = 8, rir = 5, isWarmup = true),
                // Working set 1 with Global RIR 2 -> RPE 8.0
                CompletedSet(id = "s1", weight = 80.0, reps = 8, rir = 2, actualIntensityMode = IntensityMode.RIR, actualIntensityValue = 2.0),
                // Working set 2 to failure -> RPE 10.0, RIR 0.0
                CompletedSet(id = "s2", weight = 80.0, reps = 7, isFailure = true),
            ),
        )

        val squatCompleted = CompletedExercise(
            exerciseId = "e2",
            exerciseDbId = "squat",
            exerciseName = "Sentadilla",
            sets = listOf(
                // Working set with explicit RPE 7.0 -> RIR 3.0
                CompletedSet(id = "s3", weight = 100.0, reps = 6, rpe = 7.0, actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 7.0),
                // Unrated working set must not fabricate effort
                CompletedSet(id = "s4", weight = 100.0, reps = 6),
            ),
        )

        val gripReport = LimitingFactorCatalog.resolveReport("grip", primaryMuscles = listOf("pectoralis"))
        val summary = MuscleIntensityAccounting.computeForSession(
            exercises = listOf(benchCompleted, squatCompleted),
            limitingFactorsByExerciseId = mapOf("e1" to listOf(gripReport)),
            exerciseIndex = testExerciseIndex(),
        )

        val byMuscle = summary.muscles.associateBy { it.muscleName }
        // Neutralizer (Bíceps) has 0.0 weight -> excluded from intensity exposure
        assertTrue("Neutralizer must not receive intensity", "Bíceps" !in byMuscle)

        // Bench worked Pectorales (1.0), Tríceps (0.5), Deltoides (0.3), Antebrazo via grip limiting factor (0.3) at avg RPE (8 + 10)/2 = 9.0
        assertEquals(9.0, byMuscle["Pectorales"]!!.weightedRpeMean, 0.05)
        assertEquals(1.0, byMuscle["Pectorales"]!!.weightedRirMean, 0.05)
        assertEquals(1, byMuscle["Pectorales"]!!.failureSetCount)
        assertEquals(2.0, byMuscle["Pectorales"]!!.weightedExposure, 0.05)

        assertEquals(9.0, byMuscle["Tríceps"]!!.weightedRpeMean, 0.05)
        assertEquals(1.0, byMuscle["Tríceps"]!!.weightedExposure, 0.05)

        assertEquals(9.0, byMuscle["Antebrazo"]!!.weightedRpeMean, 0.05)
        assertEquals(0.6, byMuscle["Antebrazo"]!!.weightedExposure, 0.05)

        // Squat only had RPE 7.0 -> RIR 3.0 on rated working set (s4 excluded)
        assertEquals(7.0, byMuscle["Cuádriceps"]!!.weightedRpeMean, 0.05)
        assertEquals(1, byMuscle["Cuádriceps"]!!.ratedSetCount)

        // Top 3 ordered by highest weighted RPE first (all 9.0 bench muscles above 7.0 squat muscles)
        assertEquals(3, summary.top3Muscles.size)
        assertTrue(summary.top3Muscles.all { it.weightedRpeMean >= 9.0 })
        assertTrue(summary.exercises.first { it.exerciseId == "e1" }.meanRpe!! == 9.0)
    }

    @Test
    fun `computeForLogs aggregates postExerciseReports limiting factors across workout logs`() {
        val log = WorkoutLog(
            id = "log1",
            programId = "p1",
            sessionId = "s1",
            sessionName = "Pierna",
            date = "2026-11-24T10:00:00Z",
            durationMinutes = 50,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "ex-squat",
                    exerciseDbId = "squat",
                    exerciseName = "Sentadilla",
                    sets = listOf(CompletedSet(id = "set1", weight = 120.0, reps = 5, rir = 1)),
                ),
            ),
            postExerciseReports = listOf(
                com.example.kpkn.data.models.ExerciseDiscomfortReport(
                    exerciseId = "ex-squat",
                    exerciseName = "Sentadilla",
                    technicalQuality = 7,
                    limitingFactors = listOf(
                        LimitingFactorCatalog.resolveReport("espalda", primaryMuscles = listOf("quadriceps")),
                    ),
                ),
            ),
        )

        val summary = MuscleIntensityAccounting.computeForLogs(listOf(log), testExerciseIndex())
        val byMuscle = summary.muscles.associateBy { it.muscleName }
        // Non-back primary Squat + "espalda" limiting factor auto-links Core at 0.3 exposure
        assertTrue("Core should receive weighted intensity from auto-linked lumbar bottleneck", "Core" in byMuscle)
        assertEquals(9.0, byMuscle["Core"]!!.weightedRpeMean, 0.05)
        assertEquals(1, summary.limitingFactorTallies.single().autoLinkedCoreCount)
    }
}
