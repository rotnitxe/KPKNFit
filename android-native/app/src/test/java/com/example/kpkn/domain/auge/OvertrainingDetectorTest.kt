package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleFeedbackEntry
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OvertrainingDetectorTest {

    private val exerciseDb = mapOf(
        "squat" to ExerciseMuscleInfo(
            id = "squat",
            name = "Sentadilla",
            equipment = "barbell",
            efc = 8.0,
            cnc = 7.0,
            ssc = 6.0,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY)),
        ),
    )

    @Test
    fun `returns empty when no history or no volume recommendations`() {
        val program = Program(
            id = "p1",
            name = "Plan",
            volumeRecommendations = listOf(
                VolumeRecommendation(
                    muscleGroup = "Cuádriceps",
                    minEffectiveVolume = 5,
                    maxAdaptiveVolume = 8,
                    maxRecoverableVolume = 10,
                ),
            ),
        )
        assertTrue(
            OvertrainingDetector.detectOvertrainedMuscles(program, emptyList(), emptyList(), exerciseDb).isEmpty(),
        )

        val withHistory = program.copy(volumeRecommendations = emptyList())
        assertTrue(
            OvertrainingDetector.detectOvertrainedMuscles(
                withHistory,
                listOf(
                    WorkoutLog(
                        id = "1",
                        date = "2026-07-01",
                        sessionId = "s",
                        sessionName = "S",
                        programId = "p1",
                        durationMinutes = 60,
                        fatigueLevel = 9,
                    ),
                ),
                emptyList(),
                exerciseDb,
            ).isEmpty(),
        )
    }

    @Test
    fun `detects overtraining when enough factors align`() {
        val program = Program(
            id = "p1",
            name = "Plan",
            volumeRecommendations = listOf(
                VolumeRecommendation(
                    muscleGroup = "Cuádriceps",
                    minEffectiveVolume = 1,
                    maxAdaptiveVolume = 1,
                    maxRecoverableVolume = 1,
                ),
            ),
        )
        val logs = listOf(
            WorkoutLog(
                id = "1",
                date = "2026-07-20",
                sessionId = "s1",
                sessionName = "Lower",
                programId = "p1",
                durationMinutes = 70,
                fatigueLevel = 9,
                discomforts = listOf("dolor de cuádriceps"),
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "e1",
                        exerciseDbId = "squat",
                        exerciseName = "Sentadilla",
                        sets = listOf(
                            CompletedSet(id = "s1", reps = 5, weight = 100.0),
                            CompletedSet(id = "s2", reps = 5, weight = 100.0),
                            CompletedSet(id = "s3", reps = 5, weight = 100.0),
                        ),
                    ),
                ),
            ),
            WorkoutLog(
                id = "2",
                date = "2026-07-13",
                sessionId = "s1",
                sessionName = "Lower",
                programId = "p1",
                durationMinutes = 70,
                fatigueLevel = 8,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "e1",
                        exerciseDbId = "squat",
                        exerciseName = "Sentadilla",
                        sets = listOf(
                            CompletedSet(id = "s1", reps = 5, weight = 120.0),
                        ),
                    ),
                ),
            ),
        )
        val feedbacks = listOf(
            PostSessionFeedback(
                logId = "1",
                date = "2026-07-20",
                muscleFeedback = mapOf(
                    "Cuádriceps" to MuscleFeedbackEntry(doms = 4, strengthCapacity = 4),
                ),
            ),
        )

        val result = OvertrainingDetector.detectOvertrainedMuscles(program, logs, feedbacks, exerciseDb)
        assertTrue(result.isNotEmpty())
        assertEquals("Cuádriceps", result.first())
    }
}
